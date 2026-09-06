@file:Suppress("TooManyFunctions")

package dev.verifactu.core

/** Invoice category values used by AEAT registration records. */
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

/** Integrator-supplied metadata for the SIF that generated a record. */
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

/** Immutable registration record that has passed local validation and contains its hash. */
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

/** Immutable cancellation record that has passed local validation and contains its hash. */
public data class RegistroAnulacion(
    public val draft: RegistroAnulacionDraft,
    public val hash: String,
)

/** Result of record generation, including the caller-owned next chain state. */
public sealed interface RecordCreationResult<out T> {
    /** A valid immutable record and the state to persist atomically in the host application. */
    public data class Created<T>(
        public val record: T,
        public val nextChainState: ChainState.PreviousRecord,
    ) : RecordCreationResult<T>

    /** Deterministic local validation failures. */
    public data class Invalid(
        public val report: ValidationReport,
    ) : RecordCreationResult<Nothing>
}

/** Deterministic local record creator. It never reads or writes application state. */
public object FiscalRecordFactory {
    /** Validates and creates a registration record. */
    public fun createRegistration(draft: RegistroAltaDraft): RecordCreationResult<RegistroAlta> {
        val report = RegistroAltaValidator.validate(draft)
        if (!report.isValid) return RecordCreationResult.Invalid(report)
        val hash =
            RecordHashCalculator.sha256(
                RegistrationHashInput(
                    issuerId = draft.invoice.issuer.value,
                    invoiceNumber = draft.invoice.number.value,
                    issueDate = draft.invoice.issueDate.value,
                    invoiceType = draft.invoiceType.name,
                    totalTax = draft.totalTax.value,
                    totalAmount = draft.totalAmount.value,
                    previousHash = draft.chainState.hashOrNull(),
                    generatedAt = draft.generatedAt.value,
                ).canonicalString(),
            )
        val record = RegistroAlta(draft, hash)
        return RecordCreationResult.Created(record, ChainState.PreviousRecord(draft.invoice, hash))
    }

    /** Validates and creates a cancellation record. */
    public fun createCancellation(draft: RegistroAnulacionDraft): RecordCreationResult<RegistroAnulacion> {
        val report = RegistroAnulacionValidator.validate(draft)
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
        return RecordCreationResult.Created(record, ChainState.PreviousRecord(draft.cancelledInvoice, hash))
    }
}

/** Local structural validation for `RegistroAlta` drafts. */
public object RegistroAltaValidator : Validator<RegistroAltaDraft> {
    override fun validate(value: RegistroAltaDraft): ValidationReport =
        ValidationReport(
            systemIssues(value.system) + requiredTextIssue("issuerName", value.issuerName, 120) +
                requiredTextIssue("operationDescription", value.operationDescription, 500) +
                taxBreakdownIssues(value.taxBreakdown, value.invoiceType) + registrationConditionalIssues(value) +
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

private fun taxBreakdownIssues(
    breakdown: TaxBreakdown,
    invoiceType: InvoiceType,
): List<ValidationIssue> {
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
            detail.regimeCode?.takeUnless { Regex("\\d{2}").matches(it) }?.let {
                ValidationIssue(
                    "VF-RECORD-003",
                    "taxBreakdown.details[$index].regimeCode",
                    ValidationSeverity.ERROR,
                    "The regime code must contain two digits.",
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
        ) + taxOperationIssues(index, detail, invoiceType)
    }
}

private fun taxOperationIssues(
    index: Int,
    detail: TaxBreakdownDetail,
    invoiceType: InvoiceType,
): List<ValidationIssue> {
    val path = "taxBreakdown.details[$index]"
    return when (val operation = detail.operation) {
        is TaxOperation.Exempt -> noTaxFieldIssues(detail, path, "VF-RECORD-006", "1238", "An exempt operation")
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
        -> noTaxFieldIssues(detail, path, "VF-RECORD-009", "1237", "A non-subject operation")
    }

private fun subjectNotExemptIssues(
    detail: TaxBreakdownDetail,
    path: String,
): List<ValidationIssue> =
    if (detail.costBase == null && (detail.taxRate == null || detail.chargedTax == null)) {
        listOf(
            conditionalIssue(
                "VF-RECORD-007",
                path,
                "A subject non-exempt operation requires tax rate and charged tax when no cost base is supplied.",
                "1208",
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

private fun registrationConditionalIssues(draft: RegistroAltaDraft): List<ValidationIssue> {
    val data = draft.conditionalData
    return optionalConditionalTextIssues(data) + rectificationIssues(draft.invoiceType, data) +
        priorRejectionIssues(data) + recipientIssues(draft.invoiceType, data) + issuerDelegateIssues(data, draft.invoice.issuer) +
        indicatorIssues(draft.invoiceType, data) + macroDataIssues(draft.totalAmount, data.macroData)
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
            if (identifier.value.isBlank() || xmlSchemaCharacterCount(identifier.value) > 20) {
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
    val normalized = value.trim()
    return if (normalized.isEmpty() || xmlSchemaCharacterCount(normalized) > maxLength) {
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

private val HASH_SOURCE: ComplianceSourceReference = ComplianceSourceReference("AEAT hash specification", "Section 5", "0.1.2")
private val XSD_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference("AEAT SuministroInformacion.xsd", "tikeV1.0", "retrieved 2026-08-16")
private val CATALOGUE_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference("AEAT validation and error catalogue", "3.1.3", "1.2.2 (2026-04-08)")
