package dev.verifactu.aeat

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXNotSupportedException
import org.xml.sax.SAXParseException
import org.xml.sax.XMLReader
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

internal actual fun parsePlatformAeatXml(xml: String): AeatXmlElement? {
    val builder = AeatXmlTreeBuilder()
    return try {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isValidating = false
        val reader = factory.newSAXParser().xmlReader
        // Common declaration rejection and the resolver remain mandatory on Android readers
        // that do not recognize the optional SAX entity feature switches.
        reader.disableFeatureIfSupported("http://xml.org/sax/features/external-general-entities")
        reader.disableFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities")
        reader.setEntityResolver { _, _ -> throw SAXException("External XML resolution is disabled.") }
        val handler =
            object : DefaultHandler() {
                override fun startElement(
                    uri: String,
                    localName: String,
                    qName: String,
                    attributes: Attributes,
                ) {
                    if (!builder.start(uri, localName)) throw SAXException("XML resource limit exceeded.")
                }

                override fun characters(
                    ch: CharArray,
                    start: Int,
                    length: Int,
                ) {
                    builder.characters(String(ch, start, length))
                }

                override fun endElement(
                    uri: String,
                    localName: String,
                    qName: String,
                ) {
                    builder.end()
                }

                override fun error(exception: SAXParseException): Unit = throw exception

                override fun fatalError(exception: SAXParseException): Unit = throw exception
            }
        reader.contentHandler = handler
        reader.errorHandler = handler
        reader.parse(InputSource(StringReader(xml)))
        builder.document()
    } catch (_: SAXException) {
        null
    } catch (_: ParserConfigurationException) {
        null
    } catch (_: java.io.IOException) {
        null
    }
}

private fun XMLReader.disableFeatureIfSupported(name: String) {
    try {
        setFeature(name, false)
    } catch (_: SAXNotRecognizedException) {
        // The common guard rejects every DTD and entity declaration before platform parsing.
    } catch (_: SAXNotSupportedException) {
        // External resolution is also blocked by the installed resolver.
    }
}
