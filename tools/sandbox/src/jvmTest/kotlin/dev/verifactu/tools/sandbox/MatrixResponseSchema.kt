package dev.verifactu.tools.sandbox

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

internal fun matrixResponseSchema(): Schema {
    val documents =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
    val sources =
        listOf("xmldsig-core-schema.xsd", "SuministroInformacion.xsd", "SuministroLR.xsd", "RespuestaSuministro.xsd").map { name ->
            MatrixTest::class.java.getResourceAsStream("/$name")!!.use { DOMSource(documents.newDocumentBuilder().parse(it)) }
        }
    return SchemaFactory
        .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        .apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }.newSchema(sources.toTypedArray())
}
