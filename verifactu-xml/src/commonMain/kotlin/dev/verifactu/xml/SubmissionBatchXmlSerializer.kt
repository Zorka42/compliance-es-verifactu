package dev.verifactu.xml

import dev.verifactu.core.ComplianceSourceReference
import dev.verifactu.core.FiscalRecordFactory
import dev.verifactu.core.RecordCreationResult
import dev.verifactu.core.RegistroAlta
import dev.verifactu.core.RegistroAnulacion
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.ValidationContext
import dev.verifactu.core.ValidationIssue
import dev.verifactu.core.ValidationReport
import dev.verifactu.core.ValidationSeverity
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Header data required by the AEAT `RegFactuSistemaFacturacion` batch root. */
public data class SubmissionHeader(
    public val issuerName: String,
    public val issuerTaxIdentifier: TaxIdentifier,
)

/** A completed fiscal record that can appear in an AEAT batch. */
public sealed interface SubmissionRecord {
    /** A registration record. */
    public data class Registration(
        public val value: RegistroAlta,
    ) : SubmissionRecord

    /** A cancellation record. */
    public data class Cancellation(
        public val value: RegistroAnulacion,
    ) : SubmissionRecord
}

/** Result of deterministic batch preparation; this operation performs no submission. */
public sealed interface SubmissionBatchBuildResult {
    /**
     * Exact batch and SOAP 1.1 request payloads prepared from validated snapshots.
     * Payloads contain fiscal and personal data; their diagnostic string is redacted.
     */
    public data class Created
        @JvmOverloads
        constructor(
            public val xml: String,
            public val soapEnvelope: String,
            /** Local validation evidence, with record issue paths relative to the supplied list. */
            public val report: ValidationReport = ValidationReport(emptyList()),
        ) : SubmissionBatchBuildResult {
            override fun toString(): String = "SubmissionBatchBuildResult.Created(xml=<redacted>, soapEnvelope=<redacted>)"
        }

    /** Structured local issues; field paths are relative to the supplied header and record list. */
    public data class Invalid(
        public val report: ValidationReport,
    ) : SubmissionBatchBuildResult
}

/**
 * Prepares an ordered, single-issuer batch with verified record hashes and XML-compatible header data.
 * Validation covers the currently implemented record fields, not every AEAT acceptance rule.
 * The caller owns persistence, transport, and retry decisions. This builder never performs I/O.
 */
public object SubmissionBatchBuilder {
    /** Snapshots and validates [records], returning immutable validation evidence and prepared XML strings. */
    @JvmStatic
    @JvmOverloads
    public fun build(
        header: SubmissionHeader,
        records: List<SubmissionRecord>,
        context: ValidationContext = ValidationContext(),
    ): SubmissionBatchBuildResult {
        val input = records.toList()
        val issues = headerIssues(header).toMutableList()
        if (input.size !in 1..1000) {
            issues.add(batchIssue("VF-BATCH-001", "records", "A batch must contain from one to one thousand records.", BATCH_SOURCE))
            return SubmissionBatchBuildResult.Invalid(ValidationReport(BatchIssueSnapshot(issues)))
        }
        val snapshots =
            input.mapIndexedNotNull { index, record ->
                validateRecord(record, header.issuerTaxIdentifier, "records[$index]", issues, context)
            }
        val report = ValidationReport(BatchIssueSnapshot(issues))
        if (!report.isValid) return SubmissionBatchBuildResult.Invalid(report)
        val xml = SubmissionBatchXmlSerializer.serialize(header, snapshots)
        val body = xml.removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        val soap =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" +
                body + "</soap:Body></soap:Envelope>"
        return SubmissionBatchBuildResult.Created(xml, soap, report)
    }
}

/**
 * Deterministically serializes the AEAT batch document without transport behaviour.
 * This low-level API checks record count only. It does not enforce taxpayer consistency,
 * revalidate record hashes, or wrap the document in SOAP. Use [SubmissionBatchBuilder] for validated preparation.
 */
public object SubmissionBatchXmlSerializer {
    /**
     * Serializes one to one thousand completed records in caller-supplied order.
     * @throws IllegalArgumentException when the record count is outside that range.
     */
    @JvmStatic
    public fun serialize(
        header: SubmissionHeader,
        records: List<SubmissionRecord>,
    ): String {
        require(records.size in 1..1000) { "An AEAT batch must contain from one to one thousand records." }
        val recordXml =
            records.joinToString(separator = "") { record ->
                "<RegistroFactura>${record.documentXml()}</RegistroFactura>"
            }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<RegFactuSistemaFacturacion xmlns=\"$BATCH_NAMESPACE\">" +
            "<Cabecera><ObligadoEmision xmlns=\"$RECORD_NAMESPACE\"><NombreRazon>${header.issuerName.escapeBatchXml()}" +
            "</NombreRazon><NIF>${header.issuerTaxIdentifier.value.escapeBatchXml()}</NIF></ObligadoEmision></Cabecera>" +
            recordXml +
            "</RegFactuSistemaFacturacion>"
    }
}

private class BatchIssueSnapshot(
    issues: List<ValidationIssue>,
) : AbstractList<ValidationIssue>() {
    private val entries: List<ValidationIssue> = issues.toList()

    override val size: Int
        get() = entries.size

    override fun get(index: Int): ValidationIssue = entries[index]
}

private fun validateRecord(
    record: SubmissionRecord,
    issuer: TaxIdentifier,
    path: String,
    issues: MutableList<ValidationIssue>,
    context: ValidationContext,
): SubmissionRecord? =
    when (record) {
        is SubmissionRecord.Registration -> {
            if (record.value.draft.invoice.issuer != issuer) {
                issues.add(batchIssue("VF-BATCH-003", "$path.draft.invoice.issuer", "The record issuer must match the batch header."))
            }
            when (val result = FiscalRecordFactory.createRegistration(record.value.draft, context)) {
                is RecordCreationResult.Invalid -> {
                    issues.addAll(result.report.issues.map { it.copy(fieldPath = "$path.draft.${it.fieldPath}") })
                    null
                }
                is RecordCreationResult.Created -> {
                    issues.addAll(result.report.issues.map { it.copy(fieldPath = "$path.draft.${it.fieldPath}") })
                    verifyHash(record.value.hash, result.record.hash, path, issues)
                    SubmissionRecord.Registration(result.record)
                }
            }
        }
        is SubmissionRecord.Cancellation -> {
            if (record.value.draft.cancelledInvoice.issuer != issuer) {
                issues.add(
                    batchIssue("VF-BATCH-003", "$path.draft.cancelledInvoice.issuer", "The record issuer must match the batch header."),
                )
            }
            when (val result = FiscalRecordFactory.createCancellation(record.value.draft)) {
                is RecordCreationResult.Invalid -> {
                    issues.addAll(result.report.issues.map { it.copy(fieldPath = "$path.draft.${it.fieldPath}") })
                    null
                }
                is RecordCreationResult.Created -> {
                    issues.addAll(result.report.issues.map { it.copy(fieldPath = "$path.draft.${it.fieldPath}") })
                    verifyHash(record.value.hash, result.record.hash, path, issues)
                    SubmissionRecord.Cancellation(result.record)
                }
            }
        }
    }

private fun verifyHash(
    supplied: String,
    calculated: String,
    path: String,
    issues: MutableList<ValidationIssue>,
) {
    if (supplied != calculated) {
        issues.add(batchIssue("VF-BATCH-004", "$path.hash", "The supplied hash does not match the record fields."))
    }
}

private fun headerIssues(header: SubmissionHeader): List<ValidationIssue> {
    val characterCount = header.issuerName.batchXmlCharacterCountOrNull()
    return if (header.issuerName.isBlank() || characterCount == null || characterCount !in 1..120) {
        listOf(
            batchIssue(
                "VF-BATCH-002",
                "header.issuerName",
                "The issuer name must contain 1 to 120 XML-compatible characters.",
                HEADER_SOURCE,
            ),
        )
    } else {
        emptyList()
    }
}

private fun batchIssue(
    code: String,
    path: String,
    message: String,
    source: ComplianceSourceReference? = null,
): ValidationIssue = ValidationIssue(code, path, ValidationSeverity.ERROR, message, source = source)

// XML 1.0 Fifth Edition, 2.2 [2] Char; count Unicode characters for XSD length constraints.
private fun String.batchXmlCharacterCountOrNull(): Int? {
    var count = 0
    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            character in '\uD800'..'\uDBFF' -> {
                if (index + 1 >= length || this[index + 1] !in '\uDC00'..'\uDFFF') return null
                index++
            }
            character == '\t' || character == '\n' || character == '\r' -> Unit
            character in '\u0020'..'\uD7FF' || character in '\uE000'..'\uFFFD' -> Unit
            else -> return null
        }
        count++
        index++
    }
    return count
}

private fun SubmissionRecord.documentXml(): String = serializedRecord().removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")

private fun SubmissionRecord.serializedRecord(): String =
    when (this) {
        is SubmissionRecord.Registration -> RegistroXmlSerializer.serialize(value)
        is SubmissionRecord.Cancellation -> RegistroXmlSerializer.serialize(value)
    }

private fun String.escapeBatchXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("\r", "&#13;")

private const val BATCH_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/" +
        "aeat/tike/cont/ws/SuministroLR.xsd"
private const val RECORD_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/" +
        "aeat/tike/cont/ws/SuministroInformacion.xsd"

private val BATCH_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference("AEAT SuministroLR.xsd", "RegFactuSistemaFacturacion/RegistroFactura", "tikeV1.0, retrieved 2026-09-06")
private val HEADER_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference(
        "AEAT SuministroInformacion.xsd",
        "CabeceraType/PersonaFisicaJuridicaESType",
        "tikeV1.0, retrieved 2026-08-16",
    )
