package dev.verifactu.aeat

import kotlin.jvm.JvmStatic

/** Aggregate states from AEAT `RespuestaSuministro.xsd`; unknown values remain inspectable. */
public enum class AeatSubmissionStatus {
    ACCEPTED,
    PARTIALLY_ACCEPTED,
    REJECTED,
    UNKNOWN_STATE,
}

/** Per-record states; DUPLICATE retains the legacy summary while declaredStatus remains available. */
public enum class AeatRecordStatus {
    ACCEPTED,
    ACCEPTED_WITH_ERRORS,
    REJECTED,
    DUPLICATE,
    UNKNOWN_STATE,
}

/** State of the earlier stored record, from `SuministroInformacion.xsd/EstadoRegistroSFType`. */
public enum class AeatDuplicateStatus {
    ACCEPTED,
    ACCEPTED_WITH_ERRORS,
    CANCELLED,
    UNKNOWN_STATE,
}

/** Submitted operation reported by AEAT; this does not describe a retry decision. */
public enum class AeatOperationType {
    REGISTRATION,
    CANCELLATION,
    UNKNOWN_STATE,
}

/** Exact decoded invoice identity from the response, without applying fiscal input normalization. */
public data class AeatInvoiceReference(
    public val issuer: String,
    public val number: String,
    public val issueDate: String,
) {
    /** Omits fiscal identifiers. */
    override fun toString(): String = "AeatInvoiceReference(redacted)"
}

/** Exact operation and optional protocol flags; flag interpretation belongs to source-backed policies. */
public data class AeatResponseOperation(
    public val type: AeatOperationType,
    public val rawType: String,
    public val subsanacion: String? = null,
    public val rechazoPrevio: String? = null,
    public val sinRegistroPrevio: String? = null,
) {
    /** Omits remote field text. */
    override fun toString(): String = "AeatResponseOperation(type=$type, details=redacted)"
}

/** Details of an earlier duplicate; these do not alone establish that a retry is safe. */
public data class AeatDuplicateRecord(
    public val requestId: String,
    public val status: AeatDuplicateStatus,
    public val rawStatus: String?,
    public val errorCode: String? = null,
    public val errorDescription: String? = null,
) {
    /** Omits identifiers and diagnostics. */
    override fun toString(): String = "AeatDuplicateRecord(status=$status, details=redacted)"
}

/** Known wait seconds or the exact unrecognized/missing value; the library never sleeps. */
public sealed interface AeatFlowControl {
    /** A one-to-four-digit value from the pinned `Tipo6Type` representation. */
    public data class Known(
        public val seconds: Int,
    ) : AeatFlowControl

    /** Missing, empty, negative, oversized, or otherwise unrecognized wait text. */
    public data class Unknown(
        public val rawValue: String?,
    ) : AeatFlowControl {
        /** Omits untrusted remote text. */
        override fun toString(): String = "Unknown(rawValue=redacted)"
    }
}

/** A response line with preserved identity, declared status, duplicate diagnostics, and catalogue incidence. */
public data class AeatResponseLine(
    public val status: AeatRecordStatus,
    public val errorCode: String? = null,
    public val errorDescription: String? = null,
    public val duplicateRequestId: String? = null,
    public val invoice: AeatInvoiceReference? = null,
    public val operation: AeatResponseOperation? = null,
    public val declaredStatus: AeatRecordStatus = status,
    public val rawStatus: String? = null,
    public val duplicate: AeatDuplicateRecord? = null,
    public val externalReference: String? = null,
    public val incidence: AeatIncidence? = null,
) {
    /** Summarizes the state without printing remote diagnostics or identifiers. */
    override fun toString(): String = "AeatResponseLine(status=$status, declaredStatus=$declaredStatus, diagnostics=redacted)"
}

/** Typed AEAT response data. Parser-created lines always have an invoice and operation. */
public data class AeatSubmissionResponse(
    public val status: AeatSubmissionStatus,
    public val csv: String?,
    public val retryAfterSeconds: Int?,
    public val lines: List<AeatResponseLine>,
    public val rawStatus: String? = null,
    public val flowControl: AeatFlowControl = AeatFlowControl.Unknown(null),
) {
    /** Summarizes the response without printing CSV, identifiers, or remote diagnostics. */
    override fun toString(): String = "AeatSubmissionResponse(status=$status, lines=${lines.size}, details=redacted)"
}

/** Result of parsing an AEAT response; unknown protocol values are retained in typed UNKNOWN_STATE values. */
public sealed interface AeatResponseParseResult {
    /** A structurally readable response; this is not a complete XSD/business-rule validation result. */
    public data class Parsed(
        public val response: AeatSubmissionResponse,
    ) : AeatResponseParseResult

    /** Malformed, unsafe, oversized, or structurally ambiguous XML. Reasons never contain source text. */
    public data class InvalidXml(
        public val reason: String,
    ) : AeatResponseParseResult
}

/** Remote SOAP fault text; explicit field access returns unredacted diagnostics. */
public data class SoapFault(
    public val code: String?,
    public val message: String?,
) {
    /** Omits remote code and message text from diagnostic output. */
    override fun toString(): String = "SoapFault(details=redacted)"
}

/** Result of parsing a SOAP fault. */
public sealed interface SoapFaultParseResult {
    /** A structurally valid SOAP 1.1 or SOAP 1.2 fault. */
    public data class Parsed(
        public val fault: SoapFault,
    ) : SoapFaultParseResult

    /** XML was unsafe, malformed, ambiguous, or did not contain the expected fault. */
    public data class InvalidXml(
        public val reason: String,
    ) : SoapFaultParseResult
}

/**
 * Namespace-aware response parsing backed by platform XML readers without I/O or external entities.
 *
 * Submission fields follow the pinned `RespuestaSuministro.xsd` and `SuministroInformacion.xsd`.
 * Accepts a bare namespaced response or a SOAP 1.1 envelope. Unknown states and wait values remain
 * inspectable; missing identities and duplicate critical fields invalidate the complete response.
 * Unknown extension fields are ignored. This is not full XSD validation or batch reconciliation.
 *
 * Defensive limits are 8 Mi characters, 64 nested elements, and 100,000 elements per document.
 * DTDs and entity declarations are rejected. Keep the original XML separately for explicit diagnostics.
 * String input must have valid Unicode and either no encoding declaration or a UTF-8 declaration.
 */
public object AeatResponseParser {
    /** Parses every response line in order without treating an unknown state as acceptance. */
    @JvmStatic
    public fun parseSubmission(xml: String): AeatResponseParseResult {
        val document = parseSafeAeatXml(xml) ?: return invalidSubmission()
        val reader = AeatXmlFields()
        val root =
            reader.payload(document, RESPONSE_NAMESPACE, "RespuestaRegFactuSistemaFacturacion", SOAP_11_NAMESPACE)
                ?: return invalidSubmission()
        val header = reader.child(root, RESPONSE_NAMESPACE, "Cabecera", required = true)
        val issuer = header?.let { reader.child(it, INFORMATION_NAMESPACE, "ObligadoEmision", required = true) }
        issuer?.let {
            reader.text(it, INFORMATION_NAMESPACE, "NombreRazon", required = true)
            reader.text(it, INFORMATION_NAMESPACE, "NIF", required = true)
        }
        val rawStatus = reader.text(root, RESPONSE_NAMESPACE, "EstadoEnvio")
        val wait = reader.text(root, RESPONSE_NAMESPACE, "TiempoEsperaEnvio")
        val flow = parseFlowControl(wait)
        val lines = reader.children(root, RESPONSE_NAMESPACE, "RespuestaLinea").map { reader.responseLine(it) }
        val csv = reader.text(root, RESPONSE_NAMESPACE, "CSV")
        reader.child(root, RESPONSE_NAMESPACE, "DatosPresentacion")
        if (!reader.valid || lines.any { it == null } || lines.size > 1000) return invalidSubmission()
        return AeatResponseParseResult.Parsed(
            AeatSubmissionResponse(
                status = rawStatus.toSubmissionStatus(),
                csv = csv,
                retryAfterSeconds = (flow as? AeatFlowControl.Known)?.seconds,
                lines = lines.filterNotNull(),
                rawStatus = rawStatus,
                flowControl = flow,
            ),
        )
    }

    /** Parses a namespaced bare fault or a fault in the matching SOAP 1.1/1.2 envelope. */
    @JvmStatic
    public fun parseSoapFault(xml: String): SoapFaultParseResult {
        val document = parseSafeAeatXml(xml) ?: return invalidFault()
        val namespace = document.namespace
        if (namespace != SOAP_11_NAMESPACE && namespace != SOAP_12_NAMESPACE) return invalidFault()
        val reader = AeatXmlFields()
        val fault = reader.payload(document, namespace, "Fault", namespace) ?: return invalidFault()
        val code: String?
        val message: String?
        if (namespace == SOAP_11_NAMESPACE) {
            code = reader.text(fault, "", "faultcode", required = true)
            message = reader.text(fault, "", "faultstring", required = true)
        } else {
            val codeElement = reader.child(fault, namespace, "Code", required = true)
            code = codeElement?.let { reader.text(it, namespace, "Value", required = true) }
            val reason = reader.child(fault, namespace, "Reason", required = true)
            val texts = reason?.let { reader.children(it, namespace, "Text") }.orEmpty()
            if (texts.isEmpty()) reader.valid = false
            message = texts.firstOrNull()?.let { reader.leafText(it) }
        }
        return if (reader.valid) SoapFaultParseResult.Parsed(SoapFault(code, message)) else invalidFault()
    }
}

private fun invalidSubmission(): AeatResponseParseResult.InvalidXml =
    AeatResponseParseResult.InvalidXml("The AEAT response XML is unsafe, malformed, or structurally ambiguous.")

private fun invalidFault(): SoapFaultParseResult.InvalidXml =
    SoapFaultParseResult.InvalidXml("The SOAP fault XML is unsafe, malformed, or structurally ambiguous.")

private fun AeatXmlFields.responseLine(line: AeatXmlElement): AeatResponseLine? {
    val identity = child(line, RESPONSE_NAMESPACE, "IDFactura", required = true) ?: return null
    val issuer = text(identity, INFORMATION_NAMESPACE, "IDEmisorFactura", required = true) ?: return null
    val number = text(identity, INFORMATION_NAMESPACE, "NumSerieFactura", required = true) ?: return null
    val date = text(identity, INFORMATION_NAMESPACE, "FechaExpedicionFactura", required = true) ?: return null
    val operationElement = child(line, RESPONSE_NAMESPACE, "Operacion", required = true) ?: return null
    val rawOperation = text(operationElement, INFORMATION_NAMESPACE, "TipoOperacion", required = true) ?: return null
    val operation =
        AeatResponseOperation(
            rawOperation.toOperationType(),
            rawOperation,
            text(operationElement, INFORMATION_NAMESPACE, "Subsanacion"),
            text(operationElement, INFORMATION_NAMESPACE, "RechazoPrevio"),
            text(operationElement, INFORMATION_NAMESPACE, "SinRegistroPrevio"),
        )
    val rawStatus = text(line, RESPONSE_NAMESPACE, "EstadoRegistro")
    val declared = rawStatus.toRecordStatus()
    val duplicate = child(line, RESPONSE_NAMESPACE, "RegistroDuplicado")?.let { duplicateRecord(it) }
    val errorCode = text(line, RESPONSE_NAMESPACE, "CodigoErrorRegistro")
    val errorDescription = text(line, RESPONSE_NAMESPACE, "DescripcionErrorRegistro")
    return AeatResponseLine(
        status = if (duplicate != null) AeatRecordStatus.DUPLICATE else declared,
        errorCode = errorCode,
        errorDescription = errorDescription,
        duplicateRequestId = duplicate?.requestId,
        invoice = AeatInvoiceReference(issuer, number, date),
        operation = operation,
        declaredStatus = declared,
        rawStatus = rawStatus,
        duplicate = duplicate,
        externalReference = text(line, RESPONSE_NAMESPACE, "RefExterna"),
        incidence = errorCode?.let { AeatErrorCatalogue.classify(it, errorDescription) },
    )
}

private fun AeatXmlFields.duplicateRecord(element: AeatXmlElement): AeatDuplicateRecord? {
    val request = text(element, INFORMATION_NAMESPACE, "IdPeticionRegistroDuplicado", required = true) ?: return null
    val state = text(element, INFORMATION_NAMESPACE, "EstadoRegistroDuplicado")
    val status =
        when (state) {
            "Correcta" -> AeatDuplicateStatus.ACCEPTED
            "AceptadaConErrores" -> AeatDuplicateStatus.ACCEPTED_WITH_ERRORS
            "Anulada" -> AeatDuplicateStatus.CANCELLED
            else -> AeatDuplicateStatus.UNKNOWN_STATE
        }
    return AeatDuplicateRecord(
        request,
        status,
        state,
        text(element, INFORMATION_NAMESPACE, "CodigoErrorRegistro"),
        text(element, INFORMATION_NAMESPACE, "DescripcionErrorRegistro"),
    )
}

private fun parseFlowControl(rawValue: String?): AeatFlowControl =
    if (rawValue != null && rawValue.length in 1..4 && rawValue.all { it in '0'..'9' }) {
        AeatFlowControl.Known(rawValue.toInt())
    } else {
        AeatFlowControl.Unknown(rawValue)
    }

private fun String?.toSubmissionStatus(): AeatSubmissionStatus =
    when (this) {
        "Correcto" -> AeatSubmissionStatus.ACCEPTED
        "ParcialmenteCorrecto" -> AeatSubmissionStatus.PARTIALLY_ACCEPTED
        "Incorrecto" -> AeatSubmissionStatus.REJECTED
        else -> AeatSubmissionStatus.UNKNOWN_STATE
    }

private fun String?.toRecordStatus(): AeatRecordStatus =
    when (this) {
        "Correcto" -> AeatRecordStatus.ACCEPTED
        "AceptadoConErrores" -> AeatRecordStatus.ACCEPTED_WITH_ERRORS
        "Incorrecto" -> AeatRecordStatus.REJECTED
        else -> AeatRecordStatus.UNKNOWN_STATE
    }

private fun String.toOperationType(): AeatOperationType =
    when (this) {
        "Alta" -> AeatOperationType.REGISTRATION
        "Anulacion" -> AeatOperationType.CANCELLATION
        else -> AeatOperationType.UNKNOWN_STATE
    }

private class AeatXmlFields {
    var valid: Boolean = true

    fun children(
        parent: AeatXmlElement,
        namespace: String,
        name: String,
    ): List<AeatXmlElement> {
        if (parent.text.any { !it.isWhitespace() }) valid = false
        val candidates = parent.children.filter { it.name == name }
        if (candidates.any { it.namespace != namespace }) valid = false
        return candidates.filter { it.namespace == namespace }
    }

    fun child(
        parent: AeatXmlElement,
        namespace: String,
        name: String,
        required: Boolean = false,
    ): AeatXmlElement? {
        val elements = children(parent, namespace, name)
        if (elements.size > 1 || (required && elements.isEmpty())) valid = false
        return elements.singleOrNull()
    }

    fun text(
        parent: AeatXmlElement,
        namespace: String,
        name: String,
        required: Boolean = false,
    ): String? {
        val element = child(parent, namespace, name, required) ?: return null
        val text = leafText(element)
        if (required && text.isEmpty()) valid = false
        return text
    }

    fun leafText(element: AeatXmlElement): String {
        if (element.children.isNotEmpty()) valid = false
        return element.text.toString()
    }

    fun payload(
        document: AeatXmlElement,
        namespace: String,
        name: String,
        soapNamespace: String,
    ): AeatXmlElement? {
        if (document.namespace == namespace && document.name == name) return document
        if (document.namespace != soapNamespace || document.name != "Envelope") return null
        val body = child(document, soapNamespace, "Body", required = true) ?: return null
        child(document, soapNamespace, "Header")
        if (body.children.size != 1 || body.text.any { !it.isWhitespace() }) valid = false
        if (document.children.any { it.namespace != soapNamespace || it.name !in listOf("Header", "Body") }) valid = false
        return child(body, namespace, name, required = true).takeIf { valid }
    }
}

internal const val AEAT_XML_MAX_CHARACTERS: Int = 8 * 1024 * 1024
internal const val AEAT_XML_MAX_DEPTH: Int = 64
internal const val AEAT_XML_MAX_ELEMENTS: Int = 100_000

internal class AeatXmlElement(
    val namespace: String,
    val name: String,
) {
    val children: MutableList<AeatXmlElement> = mutableListOf()
    val text: StringBuilder = StringBuilder()
}

internal class AeatXmlTreeBuilder {
    private val stack: MutableList<AeatXmlElement> = mutableListOf()
    private var count: Int = 0
    var valid: Boolean = true
        private set
    var root: AeatXmlElement? = null
        private set

    fun start(
        namespace: String,
        name: String,
    ): Boolean {
        if (!valid) return false
        count++
        if (stack.size >= AEAT_XML_MAX_DEPTH || count > AEAT_XML_MAX_ELEMENTS) {
            valid = false
            return false
        }
        val element = AeatXmlElement(namespace, name)
        if (stack.isEmpty()) {
            if (root != null) valid = false else root = element
        } else {
            stack.last().children.add(element)
        }
        stack.add(element)
        return valid
    }

    fun characters(text: String) {
        if (valid && stack.isNotEmpty()) stack.last().text.append(text)
    }

    fun end() {
        if (stack.isEmpty()) valid = false else stack.removeAt(stack.lastIndex)
    }

    fun document(): AeatXmlElement? = root.takeIf { valid && stack.isEmpty() }
}

internal expect fun parsePlatformAeatXml(xml: String): AeatXmlElement?

private fun parseSafeAeatXml(xml: String): AeatXmlElement? = AeatXmlInputGuard.prepare(xml)?.let { parsePlatformAeatXml(it) }

private object AeatXmlInputGuard {
    fun prepare(xml: String): String? {
        if (xml.length > AEAT_XML_MAX_CHARACTERS || containsAeatXmlDeclaration(xml) || hasUnpairedSurrogate(xml)) return null
        val document = xml.removePrefix("\uFEFF")
        return document.takeIf { hasSupportedEncodingDeclaration(it) }
    }

    private fun hasSupportedEncodingDeclaration(xml: String): Boolean {
        if (!xml.startsWith("<?xml") || xml.getOrNull(5)?.isWhitespace() != true) return true
        val end = xml.indexOf("?>")
        if (end < 0) return false
        val declaration = xml.substring(0, end)
        val encoding = Regex("\\sencoding\\s*=\\s*(['\"])([^'\"]*)\\1").find(declaration)?.groupValues?.get(2)
        return encoding == null || encoding.equals("UTF-8", ignoreCase = true)
    }

    private fun hasUnpairedSurrogate(xml: String): Boolean {
        var index = 0
        while (index < xml.length) {
            val code = xml[index].code
            if (code in 0xD800..0xDBFF) {
                if (index + 1 >= xml.length || xml[index + 1].code !in 0xDC00..0xDFFF) return true
                index++
            } else if (code in 0xDC00..0xDFFF) {
                return true
            }
            index++
        }
        return false
    }

    private fun containsAeatXmlDeclaration(xml: String): Boolean {
        var offset = 0
        while (offset < xml.length) {
            val start = xml.indexOf('<', offset)
            if (start < 0) return false
            val closing =
                when {
                    xml.startsWith("<!--", start) -> "-->"
                    xml.startsWith("<![CDATA[", start) -> "]]>"
                    xml.startsWith("<!DOCTYPE", start) || xml.startsWith("<!ENTITY", start) -> return true
                    else -> null
                }
            if (closing == null) {
                offset = start + 1
            } else {
                val end = xml.indexOf(closing, start + 4)
                if (end < 0) return false
                offset = end + closing.length
            }
        }
        return false
    }
}

private const val RESPONSE_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/RespuestaSuministro.xsd"
private const val INFORMATION_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd"
private const val SOAP_11_NAMESPACE: String = "http://schemas.xmlsoap.org/soap/envelope/"
private const val SOAP_12_NAMESPACE: String = "http://www.w3.org/2003/05/soap-envelope"
