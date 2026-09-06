@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.verifactu.aeat

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSXMLParser
import platform.Foundation.NSXMLParserDelegateProtocol
import platform.Foundation.NSXMLParserResolveExternalEntitiesNever
import platform.Foundation.create
import platform.darwin.NSObject

internal actual fun parsePlatformAeatXml(xml: String): AeatXmlElement? {
    if (xml.isEmpty()) return null
    val bytes = xml.encodeToByteArray()
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    val builder = AeatXmlTreeBuilder()
    val delegate = AeatAppleXmlDelegate(builder)
    val parser = NSXMLParser(data)
    parser.shouldProcessNamespaces = true
    parser.shouldReportNamespacePrefixes = false
    parser.shouldResolveExternalEntities = false
    parser.externalEntityResolvingPolicy = NSXMLParserResolveExternalEntitiesNever
    parser.delegate = delegate
    return if (parser.parse() && parser.parserError == null && delegate.valid) builder.document() else null
}

private class AeatAppleXmlDelegate(
    private val builder: AeatXmlTreeBuilder,
) : NSObject(),
    NSXMLParserDelegateProtocol {
    var valid: Boolean = true
        private set

    override fun parser(
        parser: NSXMLParser,
        didStartElement: String,
        namespaceURI: String?,
        qualifiedName: String?,
        attributes: Map<Any?, *>,
    ) {
        if (!builder.start(namespaceURI.orEmpty(), didStartElement)) parser.abortParsing()
    }

    override fun parser(
        parser: NSXMLParser,
        didEndElement: String,
        namespaceURI: String?,
        qualifiedName: String?,
    ) {
        builder.end()
    }

    override fun parser(
        parser: NSXMLParser,
        foundCharacters: String,
    ) {
        builder.characters(foundCharacters)
    }

    override fun parser(
        parser: NSXMLParser,
        foundCDATA: NSData,
    ) {
        val text = NSString.create(data = foundCDATA, encoding = NSUTF8StringEncoding)
        if (text == null) {
            valid = false
            parser.abortParsing()
        } else {
            builder.characters(text.toString())
        }
    }

    override fun parser(
        parser: NSXMLParser,
        resolveExternalEntityName: String,
        systemID: String?,
    ): NSData? {
        valid = false
        parser.abortParsing()
        return null
    }
}
