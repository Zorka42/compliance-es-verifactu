package dev.verifactu.aeat

/** Aggregate AEAT submission states defined by `RespuestaSuministro.xsd`. */
public enum class AeatSubmissionStatus {
    ACCEPTED,
    PARTIALLY_ACCEPTED,
    REJECTED,
}

/** Per-record AEAT submission states defined by `RespuestaSuministro.xsd`. */
public enum class AeatRecordStatus {
    ACCEPTED,
    ACCEPTED_WITH_ERRORS,
    REJECTED,
    DUPLICATE,
}

/** A single typed response line, without retaining the raw response XML. */
public data class AeatResponseLine(
    public val status: AeatRecordStatus,
    public val errorCode: String? = null,
    public val errorDescription: String? = null,
    public val duplicateRequestId: String? = null,
)

/** Typed AEAT response data needed by submission-flow callers. */
public data class AeatSubmissionResponse(
    public val status: AeatSubmissionStatus,
    public val csv: String?,
    public val retryAfterSeconds: Int?,
    public val lines: List<AeatResponseLine>,
)

/** Result of parsing an AEAT submission response. */
public sealed interface AeatResponseParseResult {
    /** A response whose known fields were parsed; unsupported optional fields were ignored. */
    public data class Parsed(
        public val response: AeatSubmissionResponse,
    ) : AeatResponseParseResult

    /** XML was not a complete response document. */
    public data class InvalidXml(
        public val reason: String,
    ) : AeatResponseParseResult
}

/** Sanitized SOAP fault fields, intentionally excluding the raw envelope. */
public data class SoapFault(
    public val code: String?,
    public val message: String?,
)

/** Result of parsing a SOAP fault. */
public sealed interface SoapFaultParseResult {
    /** A SOAP 1.1 or SOAP 1.2 fault with readable diagnostics. */
    public data class Parsed(
        public val fault: SoapFault,
    ) : SoapFaultParseResult

    /** XML was incomplete or did not contain a SOAP fault. */
    public data class InvalidXml(
        public val reason: String,
    ) : SoapFaultParseResult
}

/**
 * Minimal, deterministic parser for the stable AEAT response fields.
 *
 * It deliberately ignores unknown elements so schema additions do not make known
 * submission states unreadable. It does not retain or log raw XML.
 */
public object AeatResponseParser {
    /** Parses a `RespuestaRegFactuSistemaFacturacion` XML document. */
    public fun parseSubmission(xml: String): AeatResponseParseResult {
        val root =
            xml.elementBlock("RespuestaRegFactuSistemaFacturacion")
                ?: return AeatResponseParseResult.InvalidXml("The AEAT response root element is missing or incomplete.")
        val status =
            root.elementText("EstadoEnvio").toSubmissionStatus()
                ?: return AeatResponseParseResult.InvalidXml("EstadoEnvio is missing or has an unsupported value.")
        val lines = root.elementBlocks("RespuestaLinea").mapNotNull { line -> line.parseLine() }
        return AeatResponseParseResult.Parsed(
            AeatSubmissionResponse(
                status = status,
                csv = root.elementText("CSV"),
                retryAfterSeconds = root.elementText("TiempoEsperaEnvio")?.toIntOrNull(),
                lines = lines,
            ),
        )
    }

    /** Parses a SOAP 1.1 or SOAP 1.2 fault without exposing its raw envelope. */
    public fun parseSoapFault(xml: String): SoapFaultParseResult {
        val fault =
            xml.elementBlock("Fault")
                ?: return SoapFaultParseResult.InvalidXml("The SOAP Fault element is missing or incomplete.")
        val code = fault.elementText("faultcode") ?: fault.elementText("Value")
        val message = fault.elementText("faultstring") ?: fault.elementText("Text")
        if (code == null && message == null) return SoapFaultParseResult.InvalidXml("The SOAP Fault has no readable code or message.")
        return SoapFaultParseResult.Parsed(SoapFault(code, message))
    }
}

private fun String.parseLine(): AeatResponseLine? {
    val declaredStatus = elementText("EstadoRegistro").toRecordStatus() ?: return null
    val duplicate = elementBlock("RegistroDuplicado")
    return AeatResponseLine(
        status = if (duplicate != null) AeatRecordStatus.DUPLICATE else declaredStatus,
        errorCode = elementText("CodigoErrorRegistro"),
        errorDescription = elementText("DescripcionErrorRegistro"),
        duplicateRequestId = duplicate?.elementText("IdPeticionRegistroDuplicado"),
    )
}

private fun String?.toSubmissionStatus(): AeatSubmissionStatus? =
    when (this) {
        "Correcto" -> AeatSubmissionStatus.ACCEPTED
        "ParcialmenteCorrecto" -> AeatSubmissionStatus.PARTIALLY_ACCEPTED
        "Incorrecto" -> AeatSubmissionStatus.REJECTED
        else -> null
    }

private fun String?.toRecordStatus(): AeatRecordStatus? =
    when (this) {
        "Correcto" -> AeatRecordStatus.ACCEPTED
        "AceptadoConErrores" -> AeatRecordStatus.ACCEPTED_WITH_ERRORS
        "Incorrecto" -> AeatRecordStatus.REJECTED
        else -> null
    }

private fun String.elementText(name: String): String? = elementBlock(name)?.trim()?.decodeXml()

private fun String.elementBlocks(name: String): List<String> = elementBlockRegex(name).findAll(this).map { it.groupValues[1] }.toList()

private fun String.elementBlock(name: String): String? = elementBlocks(name).firstOrNull()

private fun elementBlockRegex(name: String): Regex =
    Regex(
        "<(?:(?:[A-Za-z_][A-Za-z0-9_.-]*):)?$name\\b[^>]*>(.*?)</(?:(?:[A-Za-z_][A-Za-z0-9_.-]*):)?$name\\s*>",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

private fun String.decodeXml(): String =
    replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
