package dev.verifactu.xml

import org.xml.sax.SAXException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AeatSchemaValidationTest {
    @Test
    fun validatesTheGoldenRegistrationDocumentAgainstThePublishedAeatSchema() {
        schema().newValidator().validate(StreamSource(StringReader(REGISTRO_ALTA_V1_GOLDEN_XML)))
    }

    @Test
    fun rejectsAnElementValueOutsideThePublishedAeatSchema() {
        val invalidXml = REGISTRO_ALTA_V1_GOLDEN_XML.replace("<TipoHuella>01</TipoHuella>", "<TipoHuella>02</TipoHuella>")

        assertFailsWith<SAXException> {
            schema().newValidator().validate(StreamSource(StringReader(invalidXml)))
        }
    }

    private fun schema(): javax.xml.validation.Schema {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        val documents =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
        val sources =
            listOf("xmldsig-core-schema.xsd", "SuministroInformacion.xsd").map { name ->
                checkNotNull(javaClass.classLoader.getResourceAsStream("aeat-xsd/$name")).use { input ->
                    DOMSource(documents.newDocumentBuilder().parse(input))
                }
            }
        return factory.newSchema(sources.toTypedArray())
    }
}
