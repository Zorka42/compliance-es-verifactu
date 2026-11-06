package dev.verifactu.tools.sandbox

import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

internal fun validateBatchSchema(xml: String) {
    try {
        val errors =
            object : ErrorHandler {
                override fun warning(exception: SAXParseException): Unit = throw exception

                override fun error(exception: SAXParseException): Unit = throw exception

                override fun fatalError(exception: SAXParseException): Unit = throw exception
            }
        val documents =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        val sources =
            listOf("xmldsig-core-schema.xsd", "SuministroInformacion.xsd", "SuministroLR.xsd").map { name ->
                val input = checkNotNull(SandboxPlan::class.java.getResourceAsStream("/$name")) { "Bundled schema is missing." }
                input.use { DOMSource(documents.newDocumentBuilder().apply { setErrorHandler(errors) }.parse(it)) }
            }
        val schema =
            SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .apply {
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                    setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                    setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                    errorHandler = errors
                }.newSchema(sources.toTypedArray())
        schema
            .newValidator()
            .apply {
                setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                errorHandler = errors
            }.validate(StreamSource(StringReader(xml)))
    } catch (_: SAXException) {
        throw SandboxInputException("The prepared batch does not match the bundled AEAT schema.")
    }
}
