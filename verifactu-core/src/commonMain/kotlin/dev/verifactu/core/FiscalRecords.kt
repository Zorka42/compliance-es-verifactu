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
                taxBreakdownIssues(value.taxBreakdown) + chainIssues(value.chainState),
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

private fun taxBreakdownIssues(breakdown: TaxBreakdown): List<ValidationIssue> {
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
        )
    }
}

private fun requiredTextIssue(
    fieldPath: String,
    value: String,
    maxLength: Int,
): List<ValidationIssue> {
    val normalized = value.trim()
    return if (normalized.isEmpty() || normalized.length > maxLength) {
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
