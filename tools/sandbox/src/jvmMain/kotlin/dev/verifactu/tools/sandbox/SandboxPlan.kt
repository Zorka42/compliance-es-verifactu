package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatCertificateAccess
import dev.verifactu.aeat.AeatEndpointConfiguration
import dev.verifactu.aeat.AeatEnvironment
import dev.verifactu.aeat.CancellationPreparationResult
import dev.verifactu.aeat.FiscalSubmissionPreparation
import dev.verifactu.aeat.RegistrationPreparationResult
import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalAmount
import dev.verifactu.core.FiscalParty
import dev.verifactu.core.FiscalPartyIdentifier
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceIssueDate
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.InvoiceType
import dev.verifactu.core.Qualification
import dev.verifactu.core.RecordGenerationTimestamp
import dev.verifactu.core.RecordVersion
import dev.verifactu.core.RegistrationConditionalData
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.core.RegistroAnulacionDraft
import dev.verifactu.core.SistemaInformatico
import dev.verifactu.core.Subsanation
import dev.verifactu.core.TaxBreakdown
import dev.verifactu.core.TaxBreakdownDetail
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.TaxOperation
import dev.verifactu.core.TaxType
import dev.verifactu.core.ValidationReport
import dev.verifactu.core.ValueResult
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.xml.SubmissionHeader
import dev.verifactu.xml.SubmissionRecord

internal val INPUT_KEYS: Set<String> =
    setOf(
        "issuer.nif",
        "issuer.name",
        "invoice.number",
        "invoice.date",
        "generated.at",
        "producer.nif",
        "producer.name",
        "system.name",
        "system.id",
        "system.version",
        "system.installation",
    )
internal val PREVIOUS_KEYS: Set<String> = setOf("previous.issuer", "previous.number", "previous.date", "previous.hash")
internal val OPTIONAL_INPUT_KEYS: Set<String> = setOf("recipient.nif", "recipient.name", "batch.count", "batch.start")
internal val PREPARATION_SCENARIOS: Set<String> =
    setOf("registration", "cancellation", "duplicate", "warning-first", "rejection-date", "subsanation", "pagination")
internal val METADATA_KEYS: Set<String> = setOf("format", "scenario", "mode", "certificate.route", "request.sha256")

internal data class SandboxPlan(
    val record: SubmissionRecord,
    val next: ChainState.PreviousRecord,
    val recordXml: String,
    val soap: String,
    val report: ValidationReport,
    val qr: String? = null,
    val records: List<SubmissionRecord> = listOf(record),
) {
    override fun toString(): String = "SandboxPlan(redacted)"
}

internal fun required(
    values: Map<String, String>,
    key: String,
): String = values[key]?.takeIf { it.isNotBlank() } ?: throw SandboxInputException("Missing required field: $key.")

internal fun <T> value(result: ValueResult<T>): T =
    when (result) {
        is ValueResult.Valid -> result.value
        is ValueResult.Invalid -> throw SandboxInputException("Invalid fiscal value: ${result.error.code}.")
    }

internal fun invoice(
    values: Map<String, String>,
    prefix: String = "invoice",
): InvoiceIdentifier =
    InvoiceIdentifier(
        value(TaxIdentifier.parse(required(values, if (prefix == "invoice") "issuer.nif" else "$prefix.issuer"))),
        value(InvoiceNumber.parse(required(values, "$prefix.number"))),
        value(InvoiceIssueDate.parse(required(values, if (prefix == "invoice") "invoice.date" else "$prefix.date"))),
    )

internal fun system(values: Map<String, String>): SistemaInformatico =
    SistemaInformatico(
        required(values, "producer.name"),
        value(TaxIdentifier.parse(required(values, "producer.nif"))),
        required(values, "system.name"),
        required(values, "system.id"),
        required(values, "system.version"),
        required(values, "system.installation"),
        true,
        false,
        false,
    )

internal fun buildPlan(values: Map<String, String>): SandboxPlan {
    sandboxRequire(
        values.keys.all {
            it in INPUT_KEYS + OPTIONAL_INPUT_KEYS + PREVIOUS_KEYS + METADATA_KEYS
        },
        "Unsupported configuration field.",
    )
    INPUT_KEYS.forEach { required(values, it) }
    sandboxRequire(values["mode"] in listOf("offline", "aeat-test"), "Mode must be offline or aeat-test.")
    endpoint(values)
    val scenario = values["scenario"]
    sandboxRequire(scenario in PREPARATION_SCENARIOS, "Unsupported sandbox scenario.")
    if (scenario == "pagination") return buildPaginationPlan(values)
    sandboxRequire(values.keys.none { it.startsWith("batch.") }, "Batch inputs require the pagination scenario.")
    if (scenario == "subsanation") sandboxRequire(values.keys.containsAll(PREVIOUS_KEYS), "Subsanation requires a matching prior bundle.")
    val previous =
        if (scenario != "warning-first" && values.keys.any { it in PREVIOUS_KEYS }) {
            ChainState.PreviousRecord(invoice(values, "previous"), required(values, "previous.hash"))
        } else {
            ChainState.FirstRecord
        }
    val identity = invoice(values)
    val timestamp = value(RecordGenerationTimestamp.parse(required(values, "generated.at")))
    val metadata = system(values)
    if (scenario == "cancellation") {
        sandboxRequire(previous is ChainState.PreviousRecord, "Cancellation requires a prior registration bundle.")
        val prepared =
            FiscalSubmissionPreparation.prepareCancellation(
                RegistroAnulacionDraft(identity, previous, metadata, timestamp),
                SubmissionHeader(required(values, "issuer.name"), identity.issuer),
            )
        if (prepared is CancellationPreparationResult.Invalid) invalidPlan(prepared.report)
        prepared as CancellationPreparationResult.Prepared
        validateBatchSchema(prepared.batchXml)
        return SandboxPlan(
            SubmissionRecord.Cancellation(prepared.record),
            prepared.nextChainState,
            prepared.recordXml,
            prepared.soapEnvelope,
            prepared.report,
        )
    }
    if (scenario ==
        "warning-first"
    ) {
        sandboxRequire(values.keys.containsAll(PREVIOUS_KEYS), "Warning requires a prior bundle for the same SIF.")
    }
    if (scenario ==
        "rejection-date"
    ) {
        sandboxRequire(identity.issueDate.value == "27-10-2024", "Rejection scenario requires the explicit pre-cutoff date 27-10-2024.")
    }
    val draft = registrationDraft(values, identity, previous, metadata, timestamp)
    val prepared = FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.TEST)
    if (prepared is RegistrationPreparationResult.Invalid) invalidPlan(prepared.report)
    prepared as RegistrationPreparationResult.Prepared
    validateBatchSchema(prepared.batchXml)
    return SandboxPlan(
        SubmissionRecord.Registration(prepared.record),
        prepared.nextChainState,
        prepared.recordXml,
        prepared.soapEnvelope,
        prepared.report,
        prepared.qr.url,
    )
}

internal fun registrationDraft(
    values: Map<String, String>,
    identity: InvoiceIdentifier,
    previous: ChainState,
    metadata: SistemaInformatico,
    timestamp: RecordGenerationTimestamp,
): RegistroAltaDraft {
    val recipient = values.keys.any { it.startsWith("recipient.") }
    val conditional =
        RegistrationConditionalData(
            subsanation = if (values["scenario"] == "subsanation") Subsanation.YES else null,
            recipients =
                if (recipient) {
                    listOf(
                        FiscalParty(
                            required(values, "recipient.name"),
                            FiscalPartyIdentifier.SpanishNif(value(TaxIdentifier.parse(required(values, "recipient.nif")))),
                        ),
                    )
                } else {
                    emptyList()
                },
        )
    return RegistroAltaDraft(
        RecordVersion.V1_0,
        identity,
        required(values, "issuer.name"),
        if (recipient) InvoiceType.F1 else InvoiceType.F2,
        value(FiscalAmount.parse("21.00")),
        value(FiscalAmount.parse("121.00")),
        "Sandbox synthetic operation",
        TaxBreakdown(
            listOf(
                TaxBreakdownDetail(
                    TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                    value(FiscalAmount.parse("100.00")),
                    tax = TaxType.IVA,
                    regimeCode = "01",
                    taxRate = "21",
                    chargedTax = value(FiscalAmount.parse("21.00")),
                ),
            ),
        ),
        previous,
        metadata,
        timestamp,
        conditional,
    )
}

internal fun endpoint(values: Map<String, String>) =
    AeatEndpointConfiguration.defaultSubmissionEndpoint(
        AeatEnvironment.TEST,
        when (values["certificate.route"]) {
            "standard" -> AeatCertificateAccess.STANDARD
            "seal" -> AeatCertificateAccess.SEAL
            else -> throw SandboxInputException("Certificate route must be standard or seal.")
        },
    )

internal fun invalidPlan(report: ValidationReport): Nothing =
    throw SandboxInputException(
        "Local fiscal validation failed: " + report.issues.joinToString(", ") { "${it.code} (${it.fieldPath})" },
    )
