package dev.verifactu.testkit

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import kotlin.test.Test

class FixtureSchemaTest {
    @Test
    fun validatesSyntheticRegistrationAndCancellationAgainstVendoredRecordSchema() {
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
        val schema = factory.newSchema(sources.toTypedArray())

        listOf(VerifactuFixtures.registrationXml(), VerifactuFixtures.cancellationXml()).forEach { xml ->
            val validator = schema.newValidator()
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            validator.validate(StreamSource(StringReader(xml)))
        }
    }
}
