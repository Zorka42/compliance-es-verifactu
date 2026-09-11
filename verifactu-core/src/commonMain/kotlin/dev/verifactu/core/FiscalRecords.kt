@file:Suppress("TooManyFunctions")

package dev.verifactu.core

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Invoice category tokens from AEAT: F1 ordinary, F2 simplified, F3 replacement,
 * and R1–R5 rectifying invoices. Category-specific conditional fields are not yet modeled fully.
 */
public enum class InvoiceType {
    F1,
    F2,
    F3,
    R1,
    R2,
    R3,
    R4,
    R5,
}

/** AEAT's fixed schema version for VERI*FACTU records. */
public enum class RecordVersion(
    public val xmlValue: String,
) {
    V1_0("1.0"),
}

/** Indirect-tax identifiers accepted by the AEAT v1.0 schema. */
public enum class TaxType(
    public val xmlValue: String,
) {
    IVA("01"),
    IPSI("02"),
    IGIC("03"),
    OTHER("05"),
}

/** The required tax treatment choice in an AEAT breakdown detail. */
public sealed interface TaxOperation {
    /** A subject or non-subject operation. */
    public data class Qualified(
        public val value: Qualification,
    ) : TaxOperation

    /** An exempt operation. */
    public data class Exempt(
        public val value: Exemption,
    ) : TaxOperation
}

/** Values permitted by `CalificacionOperacion` in the AEAT v1.0 schema. */
public enum class Qualification(
    public val xmlValue: String,
) {
    SUBJECT_NOT_EXEMPT("S1"),
    SUBJECT_REVERSE_CHARGE("S2"),
    NOT_SUBJECT("N1"),
    NOT_SUBJECT_LOCATION_RULES("N2"),
}

/** Values permitted by `OperacionExenta` in the AEAT v1.0 schema. */
public enum class Exemption(
    public val xmlValue: String,
) {
    E1("E1"),
    E2("E2"),
    E3("E3"),
    E4("E4"),
    E5("E5"),
    E6("E6"),
    E7("E7"),
    E8("E8"),
}

/** One required `DetalleDesglose` entry in a registration record. */
public data class TaxBreakdownDetail(
    public val operation: TaxOperation,
    public val taxableBase: FiscalAmount,
    public val tax: TaxType? = null,
    public val regimeCode: String? = null,
    public val taxRate: String? = null,
    public val costBase: FiscalAmount? = null,
    public val chargedTax: FiscalAmount? = null,
    public val equivalenceSurchargeRate: String? = null,
    public val equivalenceSurcharge: FiscalAmount? = null,
)

/** The mandatory AEAT breakdown, containing from one to twelve detail entries. */
public data class TaxBreakdown(
    public val details: List<TaxBreakdownDetail>,
)

/** Immutable invoice identifier used in records and chain-state values. */
public data class InvoiceIdentifier(
    public val issuer: TaxIdentifier,
    public val number: InvoiceNumber,
    public val issueDate: InvoiceIssueDate,
)

/**
 * Integrator-supplied metadata for the SIF that generated a record (`SistemaInformatico`).
 * This identifies the host invoicing system and its producer, not automatically this library.
 */
public data class SistemaInformatico(
    public val producerName: String,
    public val producerTaxIdentifier: TaxIdentifier,
    public val systemName: String,
    public val systemIdentifier: String,
    public val version: String,
    public val installationNumber: String,
    public val veriFactuOnly: Boolean,
    public val supportsMultipleTaxpayers: Boolean,
    public val hasMultipleTaxpayers: Boolean,
)

/** Prior state required to append a record to a chain. */
public sealed interface ChainState {
    /** Indicates that the next record is the first one in its chain. */
    public data object FirstRecord : ChainState

    /** Identifies the preceding invoice record and its SHA-256 hash. */
    public data class PreviousRecord(
        public val invoice: InvoiceIdentifier,
        public val hash: String,
    ) : ChainState
}

/** Inputs supplied by an application to generate an immutable registration record. */
public data class RegistroAltaDraft(
    public val version: RecordVersion,
    public val invoice: InvoiceIdentifier,
    public val issuerName: String,
    public val invoiceType: InvoiceType,
    public val totalTax: FiscalAmount,
    public val totalAmount: FiscalAmount,
    public val operationDescription: String,
    public val taxBreakdown: TaxBreakdown,
    public val chainState: ChainState,
    public val system: SistemaInformatico,
    public val generatedAt: RecordGenerationTimestamp,
    public val conditionalData: RegistrationConditionalData = RegistrationConditionalData(),
)

/**
 * Registration record (`RegistroAlta`) and its hash. Use [FiscalRecordFactory] for validation.
 * The public constructor itself does not validate the draft or verify the supplied hash.
 */
public data class RegistroAlta(
    public val draft: RegistroAltaDraft,
    public val hash: String,
)

/** Inputs supplied by an application to generate an immutable cancellation record. */
public data class RegistroAnulacionDraft(
    public val cancelledInvoice: InvoiceIdentifier,
    public val chainState: ChainState,
    public val system: SistemaInformatico,
    public val generatedAt: RecordGenerationTimestamp,
)

/**
 * Cancellation record (`RegistroAnulacion`) and its hash. Cancellation does not delete an invoice.
 * Use [FiscalRecordFactory]; the public constructor does not validate the draft or hash.
 */
public data class RegistroAnulacion(
    public val draft: RegistroAnulacionDraft,
    public val hash: String,
)

/** Result of record generation, including the caller-owned next chain state. */
public sealed interface RecordCreationResult<out T> {
    /** A valid immutable record and the state to persist atomically in the host application. */
    public data class Created<T>
        @JvmOverloads
        constructor(
            public val record: T,
            public val nextChainState: ChainState.PreviousRecord,
            /** Validation evidence, including warnings that did not prevent creation. */
            public val report: ValidationReport = ValidationReport(emptyList()),
        ) : RecordCreationResult<T>

    /** Deterministic local validation failures. */
    public data class Invalid(
        public val report: ValidationReport,
    ) : RecordCreationResult<Nothing>
}

/**
 * Deterministic local record creator. It never reads or writes application state.
 *
 * Validation is structural and incomplete; creation does not guarantee AEAT acceptance.
 * The host must serialize creation per chain and atomically persist each record with its next
 * chain head. Registration creation snapshots all supplied lists, including conditional data.
 */
public object FiscalRecordFactory {
    /** Validates and creates a registration record. */
    @JvmStatic
    public fun createRegistration(draft: RegistroAltaDraft): RecordCreationResult<RegistroAlta> {
        val conditional = draft.conditionalData
        val snapshot =
            draft.copy(
                taxBreakdown = TaxBreakdown(RecordListSnapshot(draft.taxBreakdown.details)),
                conditionalData =
                    conditional.copy(
                        recipients = RecordListSnapshot(conditional.recipients),
                        replacedInvoices = RecordListSnapshot(conditional.replacedInvoices),
                        rectification =
                            conditional.rectification?.let {
                                it.copy(rectifiedInvoices = RecordListSnapshot(it.rectifiedInvoices))
                            },
                    ),
            )
        val validated = RegistroAltaValidator.validate(snapshot)
        val report = validated.copy(issues = RecordListSnapshot(validated.issues))
        if (!report.isValid) return RecordCreationResult.Invalid(report)
        val hash =
            RecordHashCalculator.sha256(
                RegistrationHashInput(
                    issuerId = snapshot.invoice.issuer.value,
                    invoiceNumber = snapshot.invoice.number.value,
                    issueDate = snapshot.invoice.issueDate.value,
                    invoiceType = snapshot.invoiceType.name,
                    totalTax = snapshot.totalTax.value,
                    totalAmount = snapshot.totalAmount.value,
                    previousHash = snapshot.chainState.hashOrNull(),
                    generatedAt = snapshot.generatedAt.value,
                ).canonicalString(),
            )
        val record = RegistroAlta(snapshot, hash)
        return RecordCreationResult.Created(record, ChainState.PreviousRecord(snapshot.invoice, hash), report)
    }

    /** Validates and creates a cancellation record. */
    @JvmStatic
    public fun createCancellation(draft: RegistroAnulacionDraft): RecordCreationResult<RegistroAnulacion> {
        val validated = RegistroAnulacionValidator.validate(draft)
        val report = validated.copy(issues = RecordListSnapshot(validated.issues))
        if (!report.isValid) return RecordCreationResult.Invalid(report)
        val hash =
            RecordHashCalculator.sha256(
                CancellationHashInput(
                    cancelledIssuerId = draft.cancelledInvoice.issuer.value,
                    cancelledInvoiceNumber = draft.cancelledInvoice.number.value,
                    cancelledIssueDate = draft.cancelledInvoice.issueDate.value,
                    previousHash = draft.chainState.hashOrNull(),
                    generatedAt = draft.generatedAt.value,
                ).canonicalString(),
            )
        val record = RegistroAnulacion(draft, hash)
        return RecordCreationResult.Created(record, ChainState.PreviousRecord(draft.cancelledInvoice, hash), report)
    }
}

private class RecordListSnapshot<T>(
    values: List<T>,
) : AbstractList<T>() {
    private val entries: List<T> = values.toList()

    override val size: Int
        get() = entries.size

    override fun get(index: Int): T = entries[index]
}

/** Local structural validation for `RegistroAlta` drafts. */
public object RegistroAltaValidator : Validator<RegistroAltaDraft> {
    override fun validate(value: RegistroAltaDraft): ValidationReport =
        ValidationReport(
            systemIssues(value.system) + requiredTextIssue("issuerName", value.issuerName, 120) +
                requiredTextIssue("operationDescription", value.operationDescription, 500) +
                taxBreakdownIssues(value) + registrationConditionalIssues(value) +
                chainIssues(value.chainState),
        )
}

/** Local structural validation for `RegistroAnulacion` drafts. */
public object RegistroAnulacionValidator : Validator<RegistroAnulacionDraft> {
    override fun validate(value: RegistroAnulacionDraft): ValidationReport =
        ValidationReport(
            systemIssues(value.system) + chainIssues(value.chainState),
        )
}

private fun ChainState.hashOrNull(): String? =
    when (this) {
        ChainState.FirstRecord -> null
        is ChainState.PreviousRecord -> hash
    }

private fun systemIssues(system: SistemaInformatico): List<ValidationIssue> =
    requiredTextIssue("system.producerName", system.producerName, 120) +
        requiredTextIssue("system.systemName", system.systemName, 30) +
        requiredTextIssue("system.systemIdentifier", system.systemIdentifier, 2) +
        requiredTextIssue("system.version", system.version, 50) +
        requiredTextIssue("system.installationNumber", system.installationNumber, 100)

private fun chainIssues(chainState: ChainState): List<ValidationIssue> =
    when (chainState) {
        ChainState.FirstRecord -> emptyList()
        is ChainState.PreviousRecord ->
            if (Regex("[A-F0-9]{64}").matches(chainState.hash)) {
                emptyList()
            } else {
                listOf(
                    ValidationIssue(
                        "VF-CHAIN-001",
                        "chainState.hash",
                        ValidationSeverity.ERROR,
                        "The previous hash must be 64 uppercase hexadecimal characters.",
                        source = HASH_SOURCE,
                    ),
                )
            }
    }

private fun taxBreakdownIssues(draft: RegistroAltaDraft): List<ValidationIssue> {
    val breakdown = draft.taxBreakdown
    if (breakdown.details.size !in 1..12) {
        return listOf(
            ValidationIssue(
                "VF-RECORD-002",
                "taxBreakdown.details",
                ValidationSeverity.ERROR,
                "The AEAT schema requires from one to twelve tax breakdown details.",
                source = XSD_SOURCE,
            ),
        )
    }
    return breakdown.details.flatMapIndexed { index, detail ->
        listOfNotNull(
            detail.regimeCode?.takeUnless { it in REGIME_CODES }?.let {
                ValidationIssue(
                    "VF-RECORD-003",
                    "taxBreakdown.details[$index].regimeCode",
                    ValidationSeverity.ERROR,
                    "The regime code must be one of the values in the AEAT schema.",
                    source = XSD_SOURCE,
                )
            },
            detail.taxRate?.takeUnless { Regex("\\d{1,3}(?:\\.\\d{0,2})?").matches(it) }?.let {
                ValidationIssue(
                    "VF-RECORD-004",
                    "taxBreakdown.details[$index].taxRate",
                    ValidationSeverity.ERROR,
                    "The tax rate must use the AEAT numeric format.",
                    source = XSD_SOURCE,
                )
            },
            detail.equivalenceSurchargeRate?.takeUnless { Regex("\\d{1,3}(?:\\.\\d{0,2})?").matches(it) }?.let {
                ValidationIssue(
                    "VF-RECORD-005",
                    "taxBreakdown.details[$index].equivalenceSurchargeRate",
                    ValidationSeverity.ERROR,
                    "The surcharge rate must use the AEAT numeric format.",
                    source = XSD_SOURCE,
                )
            },
        ) + taxOperationIssues(index, detail, draft.invoiceType) +
            taxConditionalIssues(detail, "taxBreakdown.details[$index]", draft)
    }
}

private fun taxOperationIssues(
    index: Int,
    detail: TaxBreakdownDetail,
    invoiceType: InvoiceType,
): List<ValidationIssue> {
    val path = "taxBreakdown.details[$index]"
    return when (val operation = detail.operation) {
        is TaxOperation.Exempt -> noTaxFieldIssues(detail, path, "VF-TAX-1238", "1238", "An exempt operation")
        is TaxOperation.Qualified -> qualificationIssues(detail, operation.value, path, invoiceType)
    }
}

private fun qualificationIssues(
    detail: TaxBreakdownDetail,
    qualification: Qualification,
    path: String,
    invoiceType: InvoiceType,
): List<ValidationIssue> =
    when (qualification) {
        Qualification.SUBJECT_NOT_EXEMPT -> subjectNotExemptIssues(detail, path)
        Qualification.SUBJECT_REVERSE_CHARGE -> reverseChargeIssues(detail, path, invoiceType)
        Qualification.NOT_SUBJECT,
        Qualification.NOT_SUBJECT_LOCATION_RULES,
        ->
            if (detail.isIva()) {
                noTaxFieldIssues(detail, path, "VF-RECORD-009", "1237", "A non-subject IVA operation")
            } else {
                emptyList()
            }
    }

private fun subjectNotExemptIssues(
    detail: TaxBreakdownDetail,
    path: String,
): List<ValidationIssue> =
    if (detail.taxRate == null || detail.chargedTax == null) {
        listOf(
            ValidationIssue(
                "VF-RECORD-007",
                path,
                ValidationSeverity.ERROR,
                "A subject non-exempt operation requires tax rate and charged tax, including when a cost base is supplied.",
                aeatCode =
                    when {
                        detail.costBase == null -> "1208"
                        detail.regimeCode == "06" -> "1209"
                        else -> null
                    },
                source = taxSource("3.1.3.15.7"),
            ),
        )
    } else {
        emptyList()
    }

private fun reverseChargeIssues(
    detail: TaxBreakdownDetail,
    path: String,
    invoiceType: InvoiceType,
): List<ValidationIssue> =
    buildList {
        if (invoiceType !in REVERSE_CHARGE_INVOICE_TYPES) {
            add(
                conditionalIssue(
                    "VF-RECORD-032",
                    path,
                    "A reverse-charge operation is only allowed for F1, F3, or R1 through R4.",
                    "1197",
                ),
            )
        }
        if (!detail.isZeroTaxRateAndChargedTax()) {
            add(
                conditionalIssue(
                    "VF-RECORD-008",
                    path,
                    "A reverse-charge operation requires zero tax rate and charged tax.",
                    "1198",
                ),
            )
        }
    }

private fun noTaxFieldIssues(
    detail: TaxBreakdownDetail,
    path: String,
    code: String,
    aeatCode: String,
    operation: String,
): List<ValidationIssue> =
    if (detail.hasTaxFields()) {
        listOf(
            conditionalIssue(
                code,
                path,
                "$operation cannot include tax, charged-tax, or equivalence-surcharge fields.",
                aeatCode,
            ),
        )
    } else {
        emptyList()
    }

private fun TaxBreakdownDetail.hasTaxFields(): Boolean =
    listOf(taxRate, chargedTax, equivalenceSurchargeRate, equivalenceSurcharge).any { it != null }

private fun TaxBreakdownDetail.isZeroTaxRateAndChargedTax(): Boolean =
    taxRate?.isZeroDecimal() == true && chargedTax?.value?.isZeroDecimal() == true

private fun String.isZeroDecimal(): Boolean = trimStart('+', '-').split('.').all { part -> part.all { it == '0' } }

private fun TaxBreakdownDetail.isIva(): Boolean = tax == null || tax == TaxType.IVA

private fun TaxBreakdownDetail.isIvaOrIgic(): Boolean = isIva() || tax == TaxType.IGIC

private fun TaxBreakdownDetail.qualification(): Qualification? = (operation as? TaxOperation.Qualified)?.value

private fun taxConditionalIssues(
    detail: TaxBreakdownDetail,
    path: String,
    draft: RegistroAltaDraft,
): List<ValidationIssue> =
    taxRegimePresenceIssues(detail, path) + taxFieldCombinationIssues(detail, path) +
        taxExemptionIssues(detail, path, draft) + ivaRateIssues(detail, path, draft) +
        regimeOperationIssues(detail, path) + contextualRegimeIssues(detail, path, draft) +
        taxOperationDateIssues(detail, path, draft)

private fun taxRegimePresenceIssues(
    detail: TaxBreakdownDetail,
    path: String,
): List<ValidationIssue> {
    val regime = detail.regimeCode
    val fieldPath = "$path.regimeCode"
    return when {
        detail.tax == TaxType.OTHER && regime != null ->
            listOf(taxIssue("1260", fieldPath, "Other taxes cannot include a regime code.", "3.1.3.15.6"))
        detail.tax == TaxType.IPSI && regime !in IPSI_REGIME_CODES ->
            listOf(
                ValidationIssue(
                    "VF-TAX-IPSI-TRANSITION",
                    fieldPath,
                    ValidationSeverity.WARNING,
                    "IPSI requires regime 01, 08, 11, 18, 19, or 20. The source specifies an AEAT warning through " +
                        "2026-12-31 and rejection from 2027-01-01. AEAT receipt-date context is unavailable locally; " +
                        "this warning does not establish acceptance.",
                    source = taxSource("3.1.3.15.6; IPSI transition at 2027-01-01"),
                ),
            )
        detail.isIvaOrIgic() && regime == null ->
            listOf(taxIssue("1245", fieldPath, "IVA and IGIC require an explicit regime code.", "3.1.3.15.6"))
        detail.isIva() && regime == "21" ->
            listOf(
                taxIssue(
                    "1246",
                    fieldPath,
                    "Regime 21 is an IGIC extension and is not in IVA list L8A.",
                    "3.1.3.15.6, 15.6.11; Order HAC/1177/2024 Annex 6 L8A/L8B",
                ),
            )
        else -> emptyList()
    }
}

private fun taxFieldCombinationIssues(
    detail: TaxBreakdownDetail,
    path: String,
): List<ValidationIssue> =
    buildList {
        if (detail.costBase != null && detail.regimeCode != "06" && detail.tax !in setOf(TaxType.IPSI, TaxType.OTHER)) {
            add(taxIssue("1257", "$path.costBase", "A cost base requires regime 06, IPSI, or other taxes.", "3.1.3.15.2"))
        }
        if (detail.qualification() != Qualification.SUBJECT_NOT_EXEMPT && detail.chargedTax?.value?.isZeroDecimal() == false) {
            add(taxIssue("1207", "$path.chargedTax", "Only an S1 operation may have non-zero charged tax.", "3.1.3.15.7"))
        }
        val hasSurcharge = detail.equivalenceSurchargeRate != null || detail.equivalenceSurcharge != null
        if (hasSurcharge && detail.qualification() != Qualification.SUBJECT_NOT_EXEMPT) {
            add(taxIssue("1281", path, "Equivalence-surcharge fields are only permitted for S1 operations.", "errores.properties 1281"))
        }
        if ((detail.equivalenceSurchargeRate == null) != (detail.equivalenceSurcharge == null)) {
            add(taxIssue("1284", path, "Equivalence-surcharge rate and amount must be supplied together.", "errores.properties 1284"))
        }
    }

private fun taxExemptionIssues(
    detail: TaxBreakdownDetail,
    path: String,
    draft: RegistroAltaDraft,
): List<ValidationIssue> {
    val exemption = (detail.operation as? TaxOperation.Exempt)?.value ?: return emptyList()
    return buildList {
        if (detail.isIva() && exemption in setOf(Exemption.E7, Exemption.E8)) {
            add(
                taxIssue(
                    "1182",
                    "$path.operation",
                    "IVA exemptions must belong to L10 (E1 through E6).",
                    "3.1.3.15.5; Order HAC/1177/2024 Annex 6 L10",
                ),
            )
        }
        if (detail.isIvaOrIgic() && detail.regimeCode == "01" && exemption in setOf(Exemption.E2, Exemption.E3)) {
            add(taxIssue("1199", "$path.operation", "Regime 01 cannot use exemption E2 or E3 for IVA or IGIC.", "3.1.3.15.5"))
        }
        if (detail.isIva() &&
            exemption == Exemption.E5 &&
            draft.conditionalData.recipients.any { it.identifier is FiscalPartyIdentifier.SpanishNif }
        ) {
            add(
                taxIssue(
                    "1289",
                    "conditionalData.recipients",
                    "IVA exemption E5 requires recipients to use IDOtro identification.",
                    "3.1.3.15.5.1",
                ),
            )
        }
    }
}

private fun ivaRateIssues(
    detail: TaxBreakdownDetail,
    path: String,
    draft: RegistroAltaDraft,
): List<ValidationIssue> {
    if (!detail.isIva() || detail.qualification() != Qualification.SUBJECT_NOT_EXEMPT) return emptyList()
    val rate = detail.taxRate?.rateHundredthsOrNull() ?: return emptyList()
    val date = (draft.conditionalData.operationDate ?: draft.invoice.issueDate).orderedDate()
    return ivaTaxRateIssues(rate, date, path) + ivaSurchargeRateIssues(detail, rate, date, path)
}

private fun ivaTaxRateIssues(
    rate: Int,
    date: Int,
    path: String,
): List<ValidationIssue> {
    if (rate !in setOf(0, 200, 400, 500, 750, 1000, 2100)) {
        return listOf(taxIssue("1124", "$path.taxRate", "The IVA S1 rate must be in the permitted list.", "3.1.3.15.1"))
    }
    val permitted =
        when (rate) {
            500 -> date in 20220701..20240930
            200, 750 -> date in 20241001..20241231
            else -> true
        }
    if (permitted) return emptyList()
    return listOf(
        ValidationIssue(
            "VF-TAX-RATE-DATE",
            "$path.taxRate",
            ValidationSeverity.ERROR,
            "The IVA S1 rate is outside its permitted operation-date (or issue-date) interval.",
            aeatCode = if (rate == 500) "1194" else null,
            source = taxSource("3.1.3.15.1; errores.properties 1194, 1235, 1236"),
        ),
    )
}

private fun ivaSurchargeRateIssues(
    detail: TaxBreakdownDetail,
    rate: Int,
    date: Int,
    path: String,
): List<ValidationIssue> {
    val surcharge = detail.equivalenceSurchargeRate?.rateHundredthsOrNull() ?: return emptyList()
    if (surcharge !in setOf(0, 26, 50, 62, 100, 140, 175, 520)) {
        return listOf(
            taxIssue("1127", "$path.equivalenceSurchargeRate", "The IVA S1 surcharge rate must be in the permitted list.", "3.1.3.15.3"),
        )
    }
    if (rate == 0) return zeroIvaSurchargeIssues(surcharge, date, path)
    val expected = expectedSurchargeRates(rate, date) ?: return emptyList()
    return if (surcharge in expected) {
        emptyList()
    } else {
        listOf(
            taxIssue(
                surchargeMismatchCode(rate, date),
                "$path.equivalenceSurchargeRate",
                "The surcharge rate does not match the IVA rate and operation-date (or issue-date) interval.",
                "3.1.3.15.3",
            ),
        )
    }
}

private fun expectedSurchargeRates(
    rate: Int,
    date: Int,
): Set<Int>? =
    when (rate) {
        2100 -> setOf(520, 175)
        1000 -> setOf(140)
        750 -> setOf(100)
        500 -> if (date <= 20221231) setOf(50) else setOf(62)
        400 -> setOf(50)
        200 -> setOf(26)
        else -> null
    }

private fun surchargeMismatchCode(
    rate: Int,
    date: Int,
): String =
    when (rate) {
        2100 -> "1162"
        1000 -> "1163"
        750 -> "1169"
        500 -> if (date <= 20221231) "1167" else "1168"
        400 -> "1164"
        200 -> "1166"
        else -> "1127"
    }

private fun zeroIvaSurchargeIssues(
    surcharge: Int,
    date: Int,
    path: String,
): List<ValidationIssue> =
    when {
        date in 20230101..20240930 && surcharge != 0 ->
            listOf(
                taxIssue(
                    "1165",
                    "$path.equivalenceSurchargeRate",
                    "Zero-rate IVA requires zero surcharge from 2023-01-01 through 2024-09-30.",
                    "3.1.3.15.3",
                ),
            )
        date >= 20241001 ->
            listOf(
                ValidationIssue(
                    "VF-TAX-ZERO-RATE-UNRESOLVED",
                    "$path.equivalenceSurchargeRate",
                    ValidationSeverity.WARNING,
                    "The zero-rate IVA surcharge rule from 2024-10-01 is unresolved locally: the validation PDF " +
                        "documents the earlier zero-surcharge interval, while error 1170 specifies 0.26 thereafter. " +
                        "This combination has not been verified for AEAT acceptance.",
                    source = taxSource("3.1.3.15.3; errores.properties 1165, 1170, 1277"),
                ),
            )
        else -> emptyList()
    }

private fun regimeOperationIssues(
    detail: TaxBreakdownDetail,
    path: String,
): List<ValidationIssue> {
    if (!detail.isIvaOrIgic()) return emptyList()
    val qualification = detail.qualification()
    val exemption = (detail.operation as? TaxOperation.Exempt)?.value
    val code =
        when (detail.regimeCode) {
            "02", "03", "04" -> exemptRegimeIssueCode(detail)
            "07" -> cashRegimeIssueCode(qualification, exemption)
            "08" -> if (qualification != Qualification.NOT_SUBJECT_LOCATION_RULES) "1252" else null
            "20" -> if (detail.tax == TaxType.IGIC && qualification != Qualification.NOT_SUBJECT_LOCATION_RULES) "1293" else null
            else -> null
        }
    return code?.let {
        listOf(
            taxIssue(
                it,
                "$path.operation",
                "The operation qualification or exemption is incompatible with this tax regime.",
                "3.1.3.15.6.1–15.6.6, 15.6.10",
            ),
        )
    } ?: emptyList()
}

private fun exemptRegimeIssueCode(detail: TaxBreakdownDetail): String? {
    if (detail.operation is TaxOperation.Exempt) return null
    return when (detail.regimeCode) {
        "02" -> "1286"
        "03" -> if (detail.qualification() != Qualification.SUBJECT_NOT_EXEMPT) "1200" else null
        "04" -> if (detail.qualification() != Qualification.SUBJECT_REVERSE_CHARGE) "1201" else null
        else -> null
    }
}

private fun cashRegimeIssueCode(
    qualification: Qualification?,
    exemption: Exemption?,
): String? =
    if (qualification != null &&
        qualification != Qualification.SUBJECT_NOT_EXEMPT ||
        exemption in setOf(Exemption.E2, Exemption.E3, Exemption.E4, Exemption.E5)
    ) {
        "1203"
    } else {
        null
    }

private fun contextualRegimeIssues(
    detail: TaxBreakdownDetail,
    path: String,
    draft: RegistroAltaDraft,
): List<ValidationIssue> {
    if (!detail.isIvaOrIgic()) return emptyList()
    return when (detail.regimeCode) {
        "06" -> groupRegimeIssues(detail, path, draft.invoiceType)
        "10" -> collectionRegimeIssues(detail, path, draft)
        "11" -> rentalRegimeIssues(detail, path)
        "14" -> publicAuthorityRegimeIssues(draft)
        else -> emptyList()
    }
}

private fun groupRegimeIssues(
    detail: TaxBreakdownDetail,
    path: String,
    invoiceType: InvoiceType,
): List<ValidationIssue> =
    if (detail.costBase == null || invoiceType in setOf(InvoiceType.F2, InvoiceType.F3, InvoiceType.R5)) {
        listOf(taxIssue("1202", path, "Regime 06 requires a cost base and cannot use invoice type F2, F3, or R5.", "3.1.3.15.6.4"))
    } else {
        emptyList()
    }

private fun collectionRegimeIssues(
    detail: TaxBreakdownDetail,
    path: String,
    draft: RegistroAltaDraft,
): List<ValidationIssue> =
    if (detail.qualification() != Qualification.NOT_SUBJECT ||
        draft.invoiceType != InvoiceType.F1 ||
        draft.conditionalData.recipients.any { it.identifier !is FiscalPartyIdentifier.SpanishNif }
    ) {
        listOf(taxIssue("1205", path, "Regime 10 requires N1, invoice type F1, and recipients identified by NIF.", "3.1.3.15.6.7"))
    } else {
        emptyList()
    }

private fun rentalRegimeIssues(
    detail: TaxBreakdownDetail,
    path: String,
): List<ValidationIssue> =
    if (detail.isIva() && detail.taxRate?.rateHundredthsOrNull() != 2100) {
        listOf(taxIssue("1206", "$path.taxRate", "IVA regime 11 requires a 21 percent tax rate.", "3.1.3.15.6.8"))
    } else {
        emptyList()
    }

private fun publicAuthorityRegimeIssues(draft: RegistroAltaDraft): List<ValidationIssue> =
    buildList {
        val operationDate = draft.conditionalData.operationDate
        if (operationDate == null || operationDate.orderedDate() <= draft.invoice.issueDate.orderedDate()) {
            add(
                taxIssue(
                    "1147",
                    "conditionalData.operationDate",
                    "Regime 14 requires an operation date after the invoice issue date.",
                    "3.1.3.15.6.9",
                ),
            )
        }
        if (draft.invoiceType !in setOf(InvoiceType.F1, InvoiceType.R1, InvoiceType.R2, InvoiceType.R3, InvoiceType.R4)) {
            add(taxIssue("1148", "invoiceType", "Regime 14 requires invoice type F1 or R1 through R4.", "3.1.3.15.6.9"))
        }
        if (draft.conditionalData.recipients.any { !it.isPublicAuthorityNif() }) {
            add(
                taxIssue(
                    "1149",
                    "conditionalData.recipients",
                    "Regime 14 requires recipient NIFs beginning with P, Q, S, or V; census identification remains an AEAT check.",
                    "3.1.3.15.6.9",
                ),
            )
        }
    }

private fun FiscalParty.isPublicAuthorityNif(): Boolean {
    val nif = (identifier as? FiscalPartyIdentifier.SpanishNif)?.value ?: return false
    return nif.value.first() in setOf('P', 'Q', 'S', 'V')
}

private fun taxOperationDateIssues(
    detail: TaxBreakdownDetail,
    path: String,
    draft: RegistroAltaDraft,
): List<ValidationIssue> {
    val operationDate = draft.conditionalData.operationDate ?: return emptyList()
    if (!detail.isIvaOrIgic() || operationDate.orderedDate() <= draft.invoice.issueDate.orderedDate()) return emptyList()
    return if (detail.regimeCode !in setOf("14", "15")) {
        listOf(
            taxIssue(
                "1146",
                "$path.regimeCode",
                "Each IVA or IGIC detail requires regime 14 or 15 when the operation date is after the invoice issue date.",
                "3.1.3.1; errores.properties 1146",
            ),
        )
    } else {
        emptyList()
    }
}

private fun String.rateHundredthsOrNull(): Int? {
    if (!Regex("\\d{1,3}(?:\\.\\d{0,2})?").matches(this)) return null
    return substringBefore('.').toInt() * 100 + substringAfter('.', "").padEnd(2, '0').toInt()
}

private fun InvoiceIssueDate.orderedDate(): Int =
    value.substring(6, 10).toInt() * 10000 + value.substring(3, 5).toInt() * 100 + value.substring(0, 2).toInt()

private fun taxIssue(
    aeatCode: String,
    fieldPath: String,
    message: String,
    section: String,
): ValidationIssue = ValidationIssue("VF-TAX-$aeatCode", fieldPath, ValidationSeverity.ERROR, message, aeatCode, taxSource(section))

private fun taxSource(section: String): ComplianceSourceReference =
    ComplianceSourceReference("AEAT validation and error catalogue", section, "1.2.2 (2026-04-08); archived errores.properties")

private val IPSI_REGIME_CODES: Set<String> = setOf("01", "08", "11", "18", "19", "20")

private fun registrationConditionalIssues(draft: RegistroAltaDraft): List<ValidationIssue> {
    val data = draft.conditionalData
    return optionalConditionalTextIssues(data) + conditionalListIssues(data) + rectificationIssues(draft.invoiceType, data) +
        priorRejectionIssues(data) + recipientIssues(draft.invoiceType, data) + issuerDelegateIssues(data, draft.invoice.issuer) +
        indicatorIssues(draft.invoiceType, data) + macroDataIssues(draft.totalAmount, data.macroData)
}

private fun conditionalListIssues(data: RegistrationConditionalData): List<ValidationIssue> =
    listOf(
        "conditionalData.recipients" to data.recipients.size,
        "conditionalData.replacedInvoices" to data.replacedInvoices.size,
        "conditionalData.rectification.rectifiedInvoices" to (data.rectification?.rectifiedInvoices?.size ?: 0),
    ).mapNotNull { (path, size) ->
        if (size > 1000) {
            ValidationIssue(
                "VF-RECORD-036",
                path,
                ValidationSeverity.ERROR,
                "The AEAT schema permits at most 1000 entries in this group.",
                source = ComplianceSourceReference("AEAT SuministroInformacion.xsd", "RegistroFacturacionAltaType", "tikeV1.0"),
            )
        } else {
            null
        }
    }

private fun optionalConditionalTextIssues(data: RegistrationConditionalData): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    optionalTextIssue("conditionalData.externalReference", data.externalReference, 60, issues)
    optionalTextIssue("conditionalData.taxationAgreementRegistrationNumber", data.taxationAgreementRegistrationNumber, 15, issues)
    optionalTextIssue("conditionalData.systemAgreementIdentifier", data.systemAgreementIdentifier, 16, issues)
    optionalTextIssue("conditionalData.operationDate", data.operationDate?.value, 10, issues)
    return issues
}

private fun rectificationIssues(
    invoiceType: InvoiceType,
    data: RegistrationConditionalData,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    if (invoiceType.isRectifying() && data.rectification == null) {
        issues +=
            conditionalIssue(
                "VF-RECORD-010",
                "conditionalData.rectification",
                "A rectifying invoice requires a rectification type.",
                "1114",
            )
    }
    if (!invoiceType.isRectifying() && data.rectification != null) {
        issues +=
            conditionalIssue(
                "VF-RECORD-011",
                "conditionalData.rectification",
                "A non-rectifying invoice cannot include rectification data.",
                "1115",
            )
    }
    data.rectification?.let { rectification ->
        if (rectification.type == RectificationType.REPLACEMENT && rectification.replacedAmounts == null) {
            issues +=
                conditionalIssue(
                    "VF-RECORD-012",
                    "conditionalData.rectification.replacedAmounts",
                    "A replacement rectification requires the replaced amounts.",
                    "1118",
                )
        }
        if (rectification.type != RectificationType.REPLACEMENT && rectification.replacedAmounts != null) {
            issues +=
                conditionalIssue(
                    "VF-RECORD-013",
                    "conditionalData.rectification.replacedAmounts",
                    "Replaced amounts can only be included for a replacement rectification.",
                    "1119",
                )
        }
    }
    if (data.replacedInvoices.isNotEmpty() && invoiceType != InvoiceType.F3) {
        issues +=
            conditionalIssue(
                "VF-RECORD-014",
                "conditionalData.replacedInvoices",
                "Replaced invoices can only be included for an F3 invoice.",
                "1116",
            )
    }
    return issues
}

private fun priorRejectionIssues(data: RegistrationConditionalData): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    if (data.previousRejection == RegistrationPreviousRejection.NOT_PRESENT_AT_AEAT && data.subsanation != Subsanation.YES) {
        issues +=
            conditionalIssue(
                "VF-RECORD-015",
                "conditionalData.previousRejection",
                "Previous rejection X requires subsanation S.",
                "1153",
            )
    }
    if (data.previousRejection == RegistrationPreviousRejection.YES && data.subsanation != Subsanation.YES) {
        issues +=
            conditionalIssue(
                "VF-RECORD-016",
                "conditionalData.previousRejection",
                "Previous rejection S requires subsanation S.",
                "1161",
            )
    }
    return issues
}

private fun recipientIssues(
    invoiceType: InvoiceType,
    data: RegistrationConditionalData,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    if (invoiceType.requiresRecipients() && data.recipients.isEmpty()) {
        issues +=
            conditionalIssue("VF-RECORD-017", "conditionalData.recipients", "This invoice type requires at least one recipient.", "1189")
    }
    if (invoiceType.disallowsRecipients() && data.recipients.isNotEmpty()) {
        issues += conditionalIssue("VF-RECORD-018", "conditionalData.recipients", "This invoice type cannot include recipients.", "1190")
    }
    data.recipients.forEachIndexed { index, recipient ->
        issues += fiscalPartyIssues("conditionalData.recipients[$index]", recipient, allowNotRegistered = true)
        issues += recipientIdentifierIssues(invoiceType, index, recipient)
    }
    return issues
}

private fun recipientIdentifierIssues(
    invoiceType: InvoiceType,
    index: Int,
    recipient: FiscalParty,
): List<ValidationIssue> =
    when {
        invoiceType == InvoiceType.R3 && !recipient.hasAllowedIdentifierForR3() ->
            listOf(
                conditionalIssue(
                    "VF-RECORD-033",
                    "conditionalData.recipients[$index].identifier",
                    "An R3 invoice recipient must use a Spanish NIF or not-registered identification.",
                    "1191",
                ),
            )
        invoiceType == InvoiceType.R2 && !recipient.hasAllowedIdentifierForR2() ->
            listOf(
                conditionalIssue(
                    "VF-RECORD-034",
                    "conditionalData.recipients[$index].identifier",
                    "An R2 invoice recipient must use a Spanish NIF, not-registered, or VAT identification.",
                    "1192",
                ),
            )
        else -> emptyList()
    }

private fun FiscalParty.hasAllowedIdentifierForR3(): Boolean =
    identifier is FiscalPartyIdentifier.SpanishNif ||
        (identifier as? FiscalPartyIdentifier.Other)?.type == OtherPartyIdentifierType.NOT_REGISTERED

private fun FiscalParty.hasAllowedIdentifierForR2(): Boolean =
    identifier is FiscalPartyIdentifier.SpanishNif ||
        (identifier as? FiscalPartyIdentifier.Other)?.type in
        setOf(
            OtherPartyIdentifierType.NOT_REGISTERED,
            OtherPartyIdentifierType.VAT_IDENTIFIER,
        )

private fun issuerDelegateIssues(
    data: RegistrationConditionalData,
    issuerTaxIdentifier: TaxIdentifier,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    when (data.generatedBy) {
        InvoiceGeneratedBy.THIRD_PARTY -> {
            if (data.thirdParty == null) {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-019",
                        "conditionalData.thirdParty",
                        "A third-party-generated invoice requires third-party data.",
                        "1186",
                    )
            }
        }
        InvoiceGeneratedBy.RECIPIENT -> {
            if (data.recipients.isEmpty()) {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-020",
                        "conditionalData.recipients",
                        "A recipient-generated invoice requires recipient data.",
                        "1158",
                    )
            }
            if (data.thirdParty != null) {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-021",
                        "conditionalData.thirdParty",
                        "Third-party data cannot accompany recipient-generated data.",
                        "1159",
                    )
            }
        }
        null ->
            if (data.thirdParty != null) {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-022",
                        "conditionalData.thirdParty",
                        "Third-party data requires a third-party generator indicator.",
                        "1155",
                    )
            }
    }
    data.thirdParty?.let { thirdParty ->
        issues += fiscalPartyIssues("conditionalData.thirdParty", thirdParty, allowNotRegistered = false)
        if ((thirdParty.identifier as? FiscalPartyIdentifier.SpanishNif)?.value == issuerTaxIdentifier) {
            issues +=
                conditionalIssue(
                    "VF-RECORD-035",
                    "conditionalData.thirdParty.identifier",
                    "A third-party NIF must differ from the invoice issuer NIF.",
                    "1188",
                )
        }
    }
    return issues
}

private fun indicatorIssues(
    invoiceType: InvoiceType,
    data: RegistrationConditionalData,
): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    if (data.simplifiedInvoiceQualification == SimplifiedInvoiceQualification.YES &&
        invoiceType !in setOf(InvoiceType.F1, InvoiceType.F3, InvoiceType.R1, InvoiceType.R2, InvoiceType.R3, InvoiceType.R4)
    ) {
        issues +=
            conditionalIssue(
                "VF-RECORD-023",
                "conditionalData.simplifiedInvoiceQualification",
                "A qualified simplified-invoice indicator is only allowed for F1, F3, or R1 through R4.",
                "1183",
            )
    }
    if (data.recipientIdentificationExemption == RecipientIdentificationExemption.YES &&
        invoiceType !in setOf(InvoiceType.F2, InvoiceType.R5)
    ) {
        issues +=
            conditionalIssue(
                "VF-RECORD-024",
                "conditionalData.recipientIdentificationExemption",
                "A recipient-identification exemption is only allowed for F2 or R5.",
                "1185",
            )
    }
    if (data.coupon == CouponIndicator.YES && invoiceType !in setOf(InvoiceType.R1, InvoiceType.R5)) {
        issues += conditionalIssue("VF-RECORD-025", "conditionalData.coupon", "A coupon indicator is only allowed for R1 or R5.", "1157")
    }
    return issues
}

private fun macroDataIssues(
    totalAmount: FiscalAmount,
    macroData: MacroDataIndicator?,
): List<ValidationIssue> =
    if (totalAmount.isAtLeastOneHundredMillion() && macroData != MacroDataIndicator.YES) {
        listOf(
            conditionalIssue(
                "VF-RECORD-026",
                "conditionalData.macroData",
                "Macrodato S is required when the absolute total amount is at least 100,000,000.00.",
                "1139",
            ),
        )
    } else if (!totalAmount.isAtLeastOneHundredMillion() && macroData == MacroDataIndicator.YES) {
        listOf(
            conditionalIssue(
                "VF-RECORD-027",
                "conditionalData.macroData",
                "Macrodato S is only allowed when the absolute total amount is at least 100,000,000.00.",
                "1138",
            ),
        )
    } else {
        emptyList()
    }

private fun FiscalAmount.isAtLeastOneHundredMillion(): Boolean {
    val absolute = value.removePrefix("+").removePrefix("-")
    val whole = absolute.substringBefore('.').toLong()
    return whole >= 100_000_000L
}

private fun fiscalPartyIssues(
    fieldPath: String,
    party: FiscalParty,
    allowNotRegistered: Boolean,
): List<ValidationIssue> {
    val issues = requiredTextIssue("$fieldPath.name", party.name, 120).toMutableList()
    when (val identifier = party.identifier) {
        is FiscalPartyIdentifier.SpanishNif -> Unit
        is FiscalPartyIdentifier.Other -> {
            issues += countryCodeIssues("$fieldPath.identifier.countryCode", identifier.countryCode)
            if (identifier.value.isBlank() || (identifier.value.xmlCharacterCountOrNull() ?: Int.MAX_VALUE) > 20) {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-028",
                        "$fieldPath.identifier.value",
                        "An other-party identifier must contain 1 to 20 XML characters.",
                        "1103",
                    )
            }
            if (identifier.type != OtherPartyIdentifierType.VAT_IDENTIFIER && identifier.countryCode.isNullOrBlank()) {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-029",
                        "$fieldPath.identifier.countryCode",
                        "A country code is required for this other-party identifier type.",
                        "1111",
                    )
            }
            if (identifier.countryCode == "ES" &&
                identifier.type !in setOf(OtherPartyIdentifierType.PASSPORT, OtherPartyIdentifierType.NOT_REGISTERED)
            ) {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-030",
                        "$fieldPath.identifier",
                        "Country ES only permits passport or not-registered identification.",
                        "1126",
                    )
            }
            if (identifier.type == OtherPartyIdentifierType.NOT_REGISTERED && identifier.countryCode != "ES") {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-030",
                        "$fieldPath.identifier.countryCode",
                        "Not-registered identification requires country ES.",
                        "1126",
                    )
            }
            if (!allowNotRegistered && identifier.type == OtherPartyIdentifierType.NOT_REGISTERED) {
                issues +=
                    conditionalIssue(
                        "VF-RECORD-031",
                        "$fieldPath.identifier.type",
                        "A third party cannot use not-registered identification.",
                        "1211",
                    )
            }
        }
    }
    return issues
}

private fun countryCodeIssues(
    fieldPath: String,
    countryCode: String?,
): List<ValidationIssue> =
    if (countryCode != null && countryCode !in COUNTRY_CODES) {
        listOf(
            ValidationIssue(
                "VF-RECORD-037",
                fieldPath,
                ValidationSeverity.ERROR,
                "The country code must be one of the values in the AEAT schema.",
                aeatCode = "1101",
                source = ComplianceSourceReference("AEAT SuministroInformacion.xsd", "CountryType2", "tikeV1.0"),
            ),
        )
    } else {
        emptyList()
    }

// Exact tokens from archived SuministroInformacion.xsd, CountryType2.
private val COUNTRY_CODES: Set<String> =
    setOf(
        "AF",
        "AL",
        "DE",
        "AD",
        "AO",
        "AI",
        "AQ",
        "AG",
        "SA",
        "DZ",
        "AR",
        "AM",
        "AW",
        "AU",
        "AT",
        "AZ",
        "BS",
        "BH",
        "BD",
        "BB",
        "BE",
        "BZ",
        "BJ",
        "BM",
        "BY",
        "BO",
        "BA",
        "BW",
        "BV",
        "BR",
        "BN",
        "BG",
        "BF",
        "BI",
        "BT",
        "CV",
        "KY",
        "KH",
        "CM",
        "CA",
        "CF",
        "CC",
        "CO",
        "KM",
        "CG",
        "CD",
        "CK",
        "KP",
        "KR",
        "CI",
        "CR",
        "HR",
        "CU",
        "TD",
        "CZ",
        "CL",
        "CN",
        "CY",
        "CW",
        "DK",
        "DM",
        "DO",
        "EC",
        "EG",
        "AE",
        "ER",
        "SK",
        "SI",
        "ES",
        "US",
        "EE",
        "ET",
        "FO",
        "PH",
        "FI",
        "FJ",
        "FR",
        "GA",
        "GM",
        "GE",
        "GS",
        "GH",
        "GI",
        "GD",
        "GR",
        "GL",
        "GU",
        "GT",
        "GG",
        "GN",
        "GQ",
        "GW",
        "GY",
        "HT",
        "HM",
        "HN",
        "HK",
        "HU",
        "IN",
        "ID",
        "IR",
        "IQ",
        "IE",
        "IM",
        "IS",
        "IL",
        "IT",
        "JM",
        "JP",
        "JE",
        "JO",
        "KZ",
        "KE",
        "KG",
        "KI",
        "KW",
        "LA",
        "LS",
        "LV",
        "LB",
        "LR",
        "LY",
        "LI",
        "LT",
        "LU",
        "XG",
        "MO",
        "MK",
        "MG",
        "MY",
        "MW",
        "MV",
        "ML",
        "MT",
        "FK",
        "MP",
        "MA",
        "MH",
        "MU",
        "MR",
        "YT",
        "UM",
        "MX",
        "FM",
        "MD",
        "MC",
        "MN",
        "ME",
        "MS",
        "MZ",
        "MM",
        "NA",
        "NR",
        "CX",
        "NP",
        "NI",
        "NE",
        "NG",
        "NU",
        "NF",
        "NO",
        "NC",
        "NZ",
        "IO",
        "OM",
        "NL",
        "BQ",
        "PK",
        "PW",
        "PA",
        "PG",
        "PY",
        "PE",
        "PN",
        "PF",
        "PL",
        "PT",
        "PR",
        "QA",
        "GB",
        "RW",
        "RO",
        "RU",
        "RE",
        "SB",
        "SV",
        "WS",
        "AS",
        "KN",
        "SM",
        "SX",
        "PM",
        "VC",
        "SH",
        "LC",
        "ST",
        "SN",
        "RS",
        "SC",
        "SL",
        "SG",
        "SY",
        "SO",
        "LK",
        "SZ",
        "ZA",
        "SD",
        "SS",
        "SE",
        "CH",
        "SR",
        "TH",
        "TW",
        "TZ",
        "TJ",
        "PS",
        "TF",
        "TL",
        "TG",
        "TK",
        "TO",
        "TT",
        "TN",
        "TC",
        "TM",
        "TR",
        "TV",
        "UA",
        "UG",
        "UY",
        "UZ",
        "VU",
        "VA",
        "VE",
        "VN",
        "VG",
        "VI",
        "WF",
        "YE",
        "DJ",
        "ZM",
        "ZW",
        "QU",
        "XB",
        "XU",
        "XN",
    )

private fun InvoiceType.isRectifying(): Boolean =
    this in setOf(InvoiceType.R1, InvoiceType.R2, InvoiceType.R3, InvoiceType.R4, InvoiceType.R5)

private fun InvoiceType.requiresRecipients(): Boolean =
    this in setOf(InvoiceType.F1, InvoiceType.F3, InvoiceType.R1, InvoiceType.R2, InvoiceType.R3, InvoiceType.R4)

private fun InvoiceType.disallowsRecipients(): Boolean = this in setOf(InvoiceType.F2, InvoiceType.R5)

private val REVERSE_CHARGE_INVOICE_TYPES: Set<InvoiceType> =
    setOf(InvoiceType.F1, InvoiceType.F3, InvoiceType.R1, InvoiceType.R2, InvoiceType.R3, InvoiceType.R4)

private fun optionalTextIssue(
    fieldPath: String,
    value: String?,
    maxLength: Int,
    issues: MutableList<ValidationIssue>,
) {
    value?.let { issues += requiredTextIssue(fieldPath, it, maxLength) }
}

private fun conditionalIssue(
    code: String,
    fieldPath: String,
    message: String,
    aeatCode: String,
): ValidationIssue =
    ValidationIssue(
        code = code,
        fieldPath = fieldPath,
        severity = ValidationSeverity.ERROR,
        message = message,
        aeatCode = aeatCode,
        source = CATALOGUE_SOURCE,
    )

private fun requiredTextIssue(
    fieldPath: String,
    value: String,
    maxLength: Int,
): List<ValidationIssue> {
    val characterCount = value.xmlCharacterCountOrNull()
    if (characterCount == null) {
        return listOf(
            ValidationIssue(
                "VF-RECORD-006",
                fieldPath,
                ValidationSeverity.ERROR,
                "The field must contain only XML 1.0 characters.",
                source = XML_CHARACTERS_SOURCE,
            ),
        )
    }
    return if (value.isBlank() || characterCount > maxLength) {
        listOf(
            ValidationIssue(
                "VF-RECORD-001",
                fieldPath,
                ValidationSeverity.ERROR,
                "The field must contain 1 to $maxLength characters.",
                source = XSD_SOURCE,
            ),
        )
    } else {
        emptyList()
    }
}

private val REGIME_CODES: Set<String> =
    setOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "14", "15", "17", "18", "19", "20", "21")

private val HASH_SOURCE: ComplianceSourceReference = ComplianceSourceReference("AEAT hash specification", "Section 5", "0.1.2")
private val XSD_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference("AEAT SuministroInformacion.xsd", "tikeV1.0", "retrieved 2026-08-16")
private val CATALOGUE_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference("AEAT validation and error catalogue", "3.1.3", "1.2.2 (2026-04-08)")
private val XML_CHARACTERS_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference("W3C XML 1.0", "Section 2.2, production [2] Char", "Fifth Edition, 2008-11-26")
