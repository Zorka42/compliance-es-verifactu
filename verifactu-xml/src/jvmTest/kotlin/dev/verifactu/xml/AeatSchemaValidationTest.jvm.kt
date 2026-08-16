package dev.verifactu.xml

import org.xml.sax.SAXException
import java.io.StringReader
import javax.xml.XMLConstants
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

    private fun schema() =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(
            javaClass.classLoader.getResource("aeat-xsd/SuministroInformacion.xsd"),
        )
}
