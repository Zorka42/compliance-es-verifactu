package dev.verifactu.tools.query

import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

internal const val CONTRACT_NAMESPACE =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/"
internal const val COMMON_NAMESPACE = "${CONTRACT_NAMESPACE}SuministroInformacion.xsd"
internal const val QUERY_NAMESPACE = "${CONTRACT_NAMESPACE}ConsultaLR.xsd"
internal const val RESPONSE_NAMESPACE = "${CONTRACT_NAMESPACE}RespuestaConsultaLR.xsd"
internal const val SOAP_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/"
internal const val MAX_XML_BYTES = 64 * 1024 * 1024

/** The command's errors are fixed messages: parser diagnostics may contain fiscal data. */
internal class QueryInputException(
    message: String,
) : IllegalArgumentException(message)

internal class QueryXml {
    private val schema: Schema = loadSchema()

    fun parse(
        xml: String,
        namespace: String,
        name: String,
    ): Element {
        queryRequire(xml.length <= MAX_XML_BYTES, "XML exceeds the tool's 64 MiB limit.")
        return try {
            val document =
                documents(untrusted = true)
                    .newDocumentBuilder()
                    .apply {
                        setErrorHandler(StrictXmlErrors)
                    }.parse(InputSource(StringReader(xml)))
            val payload = payload(document.documentElement)
            queryRequire(
                payload.namespaceURI == namespace && payload.localName == name,
                "Expected a query document in the official AEAT namespace.",
            )
            schema
                .newValidator()
                .apply {
                    setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                    setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                    errorHandler = StrictXmlErrors
                }.validate(DOMSource(payload))
            payload
        } catch (error: QueryInputException) {
            throw error
        } catch (_: org.xml.sax.SAXException) {
            throw QueryInputException("XML is malformed, unsafe, or does not match the bundled AEAT schema.")
        }
    }

    private fun payload(root: Element): Element {
        if (root.namespaceURI != SOAP_NAMESPACE || root.localName != "Envelope") return root
        queryRequire(
            root.elements().all { it.namespaceURI == SOAP_NAMESPACE && it.localName in setOf("Header", "Body") },
            "Unsupported SOAP envelope structure.",
        )
        val bodies = root.elements(SOAP_NAMESPACE, "Body")
        queryRequire(
            bodies.size == 1 && root.elements(SOAP_NAMESPACE, "Header").size <= 1,
            "SOAP requires exactly one Body and at most one Header.",
        )
        return bodies.single().elements().singleOrNull()
            ?: throw QueryInputException("SOAP Body must contain exactly one response.")
    }

    private fun loadSchema(): Schema {
        val sources =
            listOf("xmldsig-core-schema.xsd", "SuministroInformacion.xsd", "ConsultaLR.xsd", "RespuestaConsultaLR.xsd")
                .map { name ->
                    checkNotNull(javaClass.classLoader.getResourceAsStream("$name")) { "Bundled AEAT schema is missing." }.use { input ->
                        val builder = documents(untrusted = false).newDocumentBuilder().apply { setErrorHandler(StrictXmlErrors) }
                        DOMSource(builder.parse(input))
                    }
                }
        return SchemaFactory
            .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                // The trusted query schema permits 10,000 records, above JAXP's default schema limit.
                setProperty("http://www.oracle.com/xml/jaxp/properties/maxOccurLimit", "1000000")
                errorHandler = StrictXmlErrors
            }.newSchema(sources.toTypedArray())
    }

    private fun documents(untrusted: Boolean): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", untrusted)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "64")
        }
}

private object StrictXmlErrors : ErrorHandler {
    override fun warning(exception: SAXParseException): Unit = throw exception

    override fun error(exception: SAXParseException): Unit = throw exception

    override fun fatalError(exception: SAXParseException): Unit = throw exception
}

internal fun Element.elements(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

internal fun Element.elements(
    namespace: String,
    name: String,
): List<Element> = elements().filter { it.namespaceURI == namespace && it.localName == name }

internal fun Element.required(
    namespace: String,
    name: String,
): Element = elements(namespace, name).singleOrNull() ?: throw QueryInputException("Missing or repeated required query field.")

internal fun Element.optionalText(
    namespace: String,
    name: String,
): String? = elements(namespace, name).singleOrNull()?.textContent

internal fun String.escapeXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("\r", "&#13;")

internal fun queryRequire(
    condition: Boolean,
    message: String,
) {
    if (!condition) throw QueryInputException(message)
}
