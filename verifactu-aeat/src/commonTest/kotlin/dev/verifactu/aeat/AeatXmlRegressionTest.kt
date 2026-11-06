package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AeatXmlRegressionTest {
    private val corpus = AeatXmlRegressionCorpus

    @Test
    fun rejectsMalformedCorpusWithoutReturningPartialDataOrPrivateText() {
        corpus.malformed.forEach { (name, xml) ->
            assertInvalid(name, xml)
            val fault = assertIs<SoapFaultParseResult.InvalidXml>(AeatResponseParser.parseSoapFault(xml), name)
            assertFalse(fault.reason.contains(corpus.PRIVATE_MARKER), name)
        }
    }

    @Test
    fun rejectsAmbiguousNamespaceAndEnvelopeCorpus() {
        corpus.ambiguous.forEach { (name, xml) -> assertInvalid(name, xml) }
    }

    @Test
    fun rejectsEveryTruncationOfSyntheticSubmissionAndFaultDocuments() {
        val submission = corpus.response()
        submission.indices.forEach { end -> assertInvalid("submission prefix $end", submission.take(end)) }
        val fault = corpus.fault()
        fault.indices.forEach { end ->
            assertIs<SoapFaultParseResult.InvalidXml>(AeatResponseParser.parseSoapFault(fault.take(end)), "fault prefix $end")
        }
    }

    @Test
    fun ignoresExtensionsWithoutPromotingNestedOrForeignStatuses() {
        val extension =
            "<future:Extra xmlns:future='urn:future'><EstadoEnvio>Incorrecto</EstadoEnvio>" +
                "<RespuestaLinea/><future:CSV>other</future:CSV></future:Extra>"
        listOf(corpus.response(extension), corpus.envelope(corpus.response(extension))).forEach {
            val response = parsed(it)
            assertEquals(AeatSubmissionStatus.ACCEPTED, response.status)
            assertEquals(corpus.PRIVATE_MARKER, response.csv)
            assertEquals(0, response.lines.size)
        }
        val unknown =
            parsed(
                corpus.response().replace("Correcto", "FutureState").replace(
                    "<sf:TipoOperacion>Alta</sf:TipoOperacion>",
                    "<sf:TipoOperacion>FutureOperation</sf:TipoOperacion>",
                ),
            )
        assertEquals(AeatSubmissionStatus.UNKNOWN_STATE, unknown.status)
        assertEquals(AeatRecordStatus.UNKNOWN_STATE, unknown.lines.single().status)
        assertEquals(
            AeatOperationType.UNKNOWN_STATE,
            unknown.lines
                .single()
                .operation
                ?.type,
        )
    }

    @Test
    fun preservesUnicodeEntitiesCdataAndXmlLineEndings() {
        val representations =
            listOf(
                "S-\uD83D\uDE00-e\u0301" to "S-\uD83D\uDE00-e\u0301",
                "S-&#x1F600;-&#101;&#769;" to "S-\uD83D\uDE00-e\u0301",
                "<![CDATA[S-\uD83D\uDE00-<tag>]]>" to "S-\uD83D\uDE00-<tag>",
                " S\r\n1\r2&#13;3 " to " S\n1\n2\r3 ",
                " S\u00A0\u20031 " to " S\u00A0\u20031 ",
            )
        representations.forEach { (xmlText, decoded) ->
            val xml = corpus.response(corpus.line(xmlText))
            assertEquals(
                decoded,
                parsed(xml)
                    .lines
                    .single()
                    .invoice
                    ?.number,
            )
            val fault = assertIs<SoapFaultParseResult.Parsed>(AeatResponseParser.parseSoapFault(corpus.fault(xmlText))).fault
            assertEquals(decoded, fault.message)
        }
        val escapedDeclaration = "<!-- <!DOCTYPE ignored> --><Future><![CDATA[<!ENTITY ignored>]]></Future>"
        assertEquals(AeatSubmissionStatus.ACCEPTED, parsed(corpus.response(escapedDeclaration)).status)
    }

    @Test
    fun acceptsOnlyXmlWhitespaceBetweenStructuralElements() {
        val xml = corpus.response(" \t\r\n" + corpus.line().replace("<IDFactura>", "<IDFactura> \t\r\n"))
        assertEquals(1, parsed(xml).lines.size)
        listOf("\u00A0", "\u2003", "&#xA0;", "&#x2003;").forEach {
            assertInvalid("non-XML whitespace $it", corpus.response(it))
            assertIs<SoapFaultParseResult.InvalidXml>(
                AeatResponseParser.parseSoapFault(corpus.fault().replace("<faultcode>", "$it<faultcode>")),
            )
        }
    }

    @Test
    fun checksCharacterLimitOnBothSidesOfTheExactBoundary() {
        val template = corpus.response("<!-- -->")
        val atLimit = template.replace("<!-- -->", "<!--" + "x".repeat(AEAT_XML_MAX_CHARACTERS - template.length + 1) + "-->")
        assertEquals(AEAT_XML_MAX_CHARACTERS, atLimit.length)
        assertEquals(AeatSubmissionStatus.ACCEPTED, parsed(atLimit).status)
        assertInvalid("character limit plus one", "$atLimit ")
        assertIs<SoapFaultParseResult.InvalidXml>(AeatResponseParser.parseSoapFault(" ".repeat(AEAT_XML_MAX_CHARACTERS + 1)))
    }

    @Test
    fun checksDepthLimitOnBothSidesOfTheExactBoundary() {
        fun nested(count: Int): String = corpus.response("<Future>".repeat(count) + "</Future>".repeat(count))
        assertEquals(AeatSubmissionStatus.ACCEPTED, parsed(nested(AEAT_XML_MAX_DEPTH - 1)).status)
        assertInvalid("depth limit plus one", nested(AEAT_XML_MAX_DEPTH))
    }

    @Test
    fun checksElementLimitOnBothSidesOfTheExactBoundary() {
        // response("") contains eight elements: root, CSV, header, issuer, name, NIF, wait and state.
        val atLimit = corpus.response("<Future/>".repeat(AEAT_XML_MAX_ELEMENTS - 8))
        assertEquals(AeatSubmissionStatus.ACCEPTED, parsed(atLimit).status)
        assertInvalid(
            "element limit plus one",
            atLimit.replace("</RespuestaRegFactuSistemaFacturacion>", "<Future/></RespuestaRegFactuSistemaFacturacion>"),
        )
    }

    @Test
    fun checksResponseLineLimitOnBothSidesOfTheExactBoundary() {
        val lines = (1..1000).joinToString("") { corpus.line("S-$it") }
        val response = parsed(corpus.response(lines))
        assertEquals(1000, response.lines.size)
        assertEquals(
            "S-1",
            response.lines
                .first()
                .invoice
                ?.number,
        )
        assertEquals(
            "S-1000",
            response.lines
                .last()
                .invoice
                ?.number,
        )
        assertInvalid("line limit plus one", corpus.response(lines + corpus.line("S-1001")))
    }

    private fun parsed(xml: String): AeatSubmissionResponse =
        assertIs<AeatResponseParseResult.Parsed>(AeatResponseParser.parseSubmission(xml)).response

    private fun assertInvalid(
        name: String,
        xml: String,
    ) {
        val result = assertIs<AeatResponseParseResult.InvalidXml>(AeatResponseParser.parseSubmission(xml), name)
        assertFalse(result.reason.contains(corpus.PRIVATE_MARKER), name)
    }
}
