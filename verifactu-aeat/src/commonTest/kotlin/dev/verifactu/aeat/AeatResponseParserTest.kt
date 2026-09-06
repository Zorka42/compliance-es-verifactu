package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class AeatResponseParserTest {
    @Test
    fun responseSummariesRedactDiagnosticsWhileExplicitFieldsRemainInspectable() {
        val privateText = "PRIVATE-MARKER"
        val duplicate = AeatDuplicateRecord(privateText, AeatDuplicateStatus.UNKNOWN_STATE, privateText, privateText, privateText)
        val identity = AeatInvoiceReference(privateText, privateText, privateText)
        val operation = AeatResponseOperation(AeatOperationType.UNKNOWN_STATE, privateText, privateText)
        val line =
            AeatResponseLine(AeatRecordStatus.REJECTED, privateText, privateText, privateText, identity, operation, duplicate = duplicate)
        val response = AeatSubmissionResponse(AeatSubmissionStatus.REJECTED, privateText, 60, listOf(line), privateText)
        val fault = SoapFault(privateText, privateText)

        listOf(
            identity,
            operation,
            duplicate,
            line,
            response,
            fault,
            AeatFlowControl.Unknown(privateText),
            AeatResponseParseResult.Parsed(response),
            SoapFaultParseResult.Parsed(fault),
        ).forEach { assertFalse(it.toString().contains(privateText)) }
        assertEquals(privateText, line.errorCode)
        assertEquals(privateText, line.errorDescription)
        assertEquals(privateText, line.duplicateRequestId)
        assertEquals(privateText, response.csv)
        assertEquals(privateText, fault.code)
        assertEquals(privateText, fault.message)
    }

    @Test
    fun retainsPartialResponsesInvoiceIdentityOperationsAndSeparateDuplicateStates() {
        val duplicate =
            "<CodigoErrorRegistro>1001</CodigoErrorRegistro><DescripcionErrorRegistro>Duplicate</DescripcionErrorRegistro>" +
                "<RegistroDuplicado><sf:IdPeticionRegistroDuplicado>old-1</sf:IdPeticionRegistroDuplicado>" +
                "<sf:EstadoRegistroDuplicado>Anulada</sf:EstadoRegistroDuplicado>" +
                "<sf:CodigoErrorRegistro>99999999999999999999</sf:CodigoErrorRegistro>" +
                "<sf:DescripcionErrorRegistro>Earlier error</sf:DescripcionErrorRegistro></RegistroDuplicado>"
        val parsed = parsed(response(line("S-1", "Correcto") + line("S-2", "Incorrecto", duplicate, "Anulacion"), "ParcialmenteCorrecto"))

        assertEquals(AeatSubmissionStatus.PARTIALLY_ACCEPTED, parsed.status)
        assertEquals("CSV-1", parsed.csv)
        assertEquals(60, parsed.retryAfterSeconds)
        assertEquals(listOf("S-1", "S-2"), parsed.lines.map { it.invoice?.number })
        assertEquals(AeatInvoiceReference("89890001K", "S-2", "01-01-2024"), parsed.lines[1].invoice)
        assertEquals(AeatOperationType.CANCELLATION, parsed.lines[1].operation?.type)
        assertEquals(AeatRecordStatus.DUPLICATE, parsed.lines[1].status)
        assertEquals(AeatRecordStatus.REJECTED, parsed.lines[1].declaredStatus)
        assertEquals("Incorrecto", parsed.lines[1].rawStatus)
        assertEquals("1001", parsed.lines[1].errorCode)
        assertEquals("old-1", parsed.lines[1].duplicateRequestId)
        assertEquals(AeatDuplicateStatus.CANCELLED, parsed.lines[1].duplicate?.status)
        assertEquals("99999999999999999999", parsed.lines[1].duplicate?.errorCode)
        assertEquals(AeatIncidenceDisposition.UNKNOWN, parsed.lines[1].incidence?.disposition)
    }

    @Test
    fun acceptsPrefixesCommentsCdataAndNumericEntitiesWithoutTrimmingIdentities() {
        val diagnostic = "<DescripcionErrorRegistro><![CDATA[Message <tag> <!DOCTYPE ignored>]]> &#x26; &#65;</DescripcionErrorRegistro>"
        val xml = response(line(" S&#x2D;1 ", "Correcto", diagnostic) + "<!-- <EstadoEnvio>Incorrecto</EstadoEnvio> -->")
        val prefixed =
            xml
                .replace("xmlns=\"$RESPONSE_NS\"", "xmlns:r=\"$RESPONSE_NS\"")
                .replace(
                    Regex(
                        "<(\\/?)(RespuestaRegFactuSistemaFacturacion|CSV|Cabecera|RespuestaLinea|IDFactura|Operacion|" +
                            "EstadoRegistro|DescripcionErrorRegistro|TiempoEsperaEnvio|EstadoEnvio)(?=[ >])",
                    ),
                    "<$1r:$2",
                )
        val parsed = parsed(soapEnvelope(prefixed))

        assertEquals(
            " S-1 ",
            parsed.lines
                .single()
                .invoice
                ?.number,
        )
        assertEquals("Message <tag> <!DOCTYPE ignored> & A", parsed.lines.single().errorDescription)
        assertEquals(AeatSubmissionStatus.ACCEPTED, parsed.status)
    }

    @Test
    fun retainsEveryUnknownOrMissingStatusAndUnknownOperationWithoutInventingAcceptance() {
        val parsed = parsed(response(line("S-1", "FutureState") + line("S-2", null, operation = "FutureOperation"), "FutureAggregate"))

        assertEquals(AeatSubmissionStatus.UNKNOWN_STATE, parsed.status)
        assertEquals("FutureAggregate", parsed.rawStatus)
        assertEquals(2, parsed.lines.size)
        assertEquals(listOf(AeatRecordStatus.UNKNOWN_STATE, AeatRecordStatus.UNKNOWN_STATE), parsed.lines.map { it.status })
        assertEquals("FutureState", parsed.lines[0].rawStatus)
        assertNull(parsed.lines[1].rawStatus)
        assertEquals(AeatOperationType.UNKNOWN_STATE, parsed.lines[1].operation?.type)
        assertEquals("FutureOperation", parsed.lines[1].operation?.rawType)
        assertEquals(AeatSubmissionStatus.UNKNOWN_STATE, parsed(response(state = null)).status)
    }

    @Test
    fun rejectsMalformedDocumentsNamespacesSpoofedRootsAndExternalEntities() {
        val xml = response(line())
        val invalid =
            listOf(
                xml.dropLast(1),
                xml + xml,
                "<wrapper>$xml</wrapper>",
                xml.replace(RESPONSE_NS, "urn:other"),
                xml.replace("xmlns=\"$RESPONSE_NS\"", ""),
                xml
                    .replace("<EstadoEnvio>", "<wrong:EstadoEnvio xmlns:wrong=\"urn:other\">")
                    .replace("</EstadoEnvio>", "</wrong:EstadoEnvio>"),
                "<!DOCTYPE response SYSTEM \"https://example.invalid/private.dtd\">$xml",
                "<!DOCTYPE response [<!ENTITY private SYSTEM \"file:///private/sensitive\">]>$xml",
                xml.replace("CSV-1", "&undeclared;"),
                xml.replace("CSV-1", "\uD800"),
                xml.replace("CSV-1", "\uDC00"),
                xml.replace("<Cabecera>", "<Cabecera private=\"one\" private=\"two\">"),
                soapEnvelope(xml).replace("</soap:Body>", "<soap:Fault/></soap:Body>"),
            )
        invalid.forEach { assertIs<AeatResponseParseResult.InvalidXml>(AeatResponseParser.parseSubmission(it)) }
    }

    @Test
    fun rejectsDuplicateCriticalFieldsAndMissingCorrelationInsteadOfReturningPartialLines() {
        val xml = response(line())
        val invalid =
            listOf(
                xml.replace("<EstadoEnvio>", "<EstadoEnvio>Incorrecto</EstadoEnvio><EstadoEnvio>"),
                xml.replace("<TiempoEsperaEnvio>", "<TiempoEsperaEnvio>0</TiempoEsperaEnvio><TiempoEsperaEnvio>"),
                xml.replace("<IDFactura>", "<IDFactura/><IDFactura>"),
                xml.replace("<sf:NumSerieFactura>", "<sf:NumSerieFactura>other</sf:NumSerieFactura><sf:NumSerieFactura>"),
                response(line() + "<RespuestaLinea><EstadoRegistro>Correcto</EstadoRegistro></RespuestaLinea>"),
                xml.replace("<Operacion><sf:TipoOperacion>Alta</sf:TipoOperacion></Operacion>", ""),
                xml.replace("<EstadoRegistro>", "<EstadoRegistro>Incorrecto</EstadoRegistro><EstadoRegistro>"),
            )
        invalid.forEach { assertIs<AeatResponseParseResult.InvalidXml>(AeatResponseParser.parseSubmission(it)) }
    }

    @Test
    fun exposesWaitSecondsOnlyForKnownValuesAndPreservesEveryUnknownValue() {
        listOf("0" to 0, "0001" to 1, "9999" to 9999).forEach { (raw, seconds) ->
            val parsed = parsed(response(wait = raw))
            assertEquals(AeatFlowControl.Known(seconds), parsed.flowControl)
            assertEquals(seconds, parsed.retryAfterSeconds)
        }
        listOf(null, "", "-1", "10000", "999999999999999999999", "later", " 60 ").forEach { raw ->
            val parsed = parsed(response(wait = raw))
            assertEquals(raw, assertIs<AeatFlowControl.Unknown>(parsed.flowControl).rawValue)
            assertNull(parsed.retryAfterSeconds)
        }
    }

    @Test
    fun classifiesKnownAcceptedErrorsWithoutInferringReplacementOrRetry() {
        val incidence = AeatErrorCatalogue.classify("2000", "Hash mismatch")

        assertEquals(AeatIncidenceDisposition.ACCEPTED_WITH_ERRORS, incidence.disposition)
        assertEquals(AeatSubsanationRequirement.REQUIRED, incidence.subsanationRequirement)
        assertEquals(AeatErrorCatalogue.VERSION, incidence.source?.version)
    }

    @Test
    fun retainsUnknownCodesAsTypedInspectableIncidences() {
        val line =
            parsed(
                response(
                    line(
                        diagnostics =
                            "<CodigoErrorRegistro>9999</CodigoErrorRegistro>" +
                                "<DescripcionErrorRegistro>Future catalogue entry</DescripcionErrorRegistro>",
                    ),
                ),
            ).lines.single()
        assertEquals("9999", line.incidence?.code)
        assertEquals(AeatIncidenceDisposition.UNKNOWN, line.incidence?.disposition)
        assertEquals(AeatSubsanationRequirement.UNKNOWN, line.incidence?.subsanationRequirement)
        assertNull(line.incidence?.source)
    }

    @Test
    fun parsesNamespacedSoapFaultsAndRejectsFaultsOutsideTheBody() {
        val fault =
            "<soap:Fault xmlns:soap=\"$SOAP_NS\"><faultcode>soap:Client</faultcode>" +
                "<faultstring><![CDATA[Invalid <request>]]></faultstring></soap:Fault>"
        listOf(fault, soapEnvelope(fault)).forEach {
            val parsed = assertIs<SoapFaultParseResult.Parsed>(AeatResponseParser.parseSoapFault(it)).fault
            assertEquals("soap:Client", parsed.code)
            assertEquals("Invalid <request>", parsed.message)
        }
        val soap12 =
            "<s:Fault xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\"><s:Code><s:Value>s:Receiver</s:Value></s:Code>" +
                "<s:Reason><s:Text xml:lang=\"en\">Failure</s:Text></s:Reason></s:Fault>"
        assertEquals("Failure", assertIs<SoapFaultParseResult.Parsed>(AeatResponseParser.parseSoapFault(soap12)).fault.message)
        listOf(
            "<wrapper>$fault</wrapper>",
            fault.replace("<faultcode>", "<faultcode>other</faultcode><faultcode>"),
            soapEnvelope("<ignored/>", fault),
        ).forEach {
            assertIs<SoapFaultParseResult.InvalidXml>(AeatResponseParser.parseSoapFault(it))
        }
    }

    @Test
    fun enforcesResourceLimitsWithoutEchoingDocumentText() {
        val privateText = "PRIVATE-MARKER"
        val invalid =
            listOf(
                response("<unknown>".repeat(AEAT_XML_MAX_DEPTH) + privateText + "</unknown>".repeat(AEAT_XML_MAX_DEPTH)),
                response("<unknown/>".repeat(AEAT_XML_MAX_ELEMENTS)),
                " ".repeat(AEAT_XML_MAX_CHARACTERS) + privateText,
                response(line().repeat(1001)),
            )
        invalid.forEach {
            val result = assertIs<AeatResponseParseResult.InvalidXml>(AeatResponseParser.parseSubmission(it))
            assertFalse(result.reason.contains(privateText))
        }
    }

    @Test
    fun treatsStringInputAsUnicodeWithAnOptionalUtf8Declaration() {
        val xml = response(line("S-\uD83D\uDE00-ñ"))
        listOf("", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "\uFEFF<?xml version=\"1.0\" encoding='utf-8'?>").forEach {
            assertEquals(
                "S-\uD83D\uDE00-ñ",
                parsed(it + xml)
                    .lines
                    .single()
                    .invoice
                    ?.number,
            )
        }
        listOf("UTF-16", "ISO-8859-1", "PRIVATE-MARKER").forEach { encoding ->
            val result = AeatResponseParser.parseSubmission("<?xml version=\"1.0\" encoding=\"$encoding\"?>$xml")
            assertIs<AeatResponseParseResult.InvalidXml>(result)
            assertFalse(result.reason.contains(encoding))
        }
    }

    private fun parsed(xml: String): AeatSubmissionResponse =
        assertIs<AeatResponseParseResult.Parsed>(AeatResponseParser.parseSubmission(xml)).response

    private fun response(
        lines: String = "",
        state: String? = "Correcto",
        wait: String? = "60",
    ): String =
        "<RespuestaRegFactuSistemaFacturacion xmlns=\"$RESPONSE_NS\" xmlns:sf=\"$INFORMATION_NS\">" +
            "<CSV>CSV-1</CSV><Cabecera><sf:ObligadoEmision><sf:NombreRazon>Example issuer</sf:NombreRazon>" +
            "<sf:NIF>89890001K</sf:NIF></sf:ObligadoEmision></Cabecera>" +
            (wait?.let { "<TiempoEsperaEnvio>$it</TiempoEsperaEnvio>" } ?: "") +
            (state?.let { "<EstadoEnvio>$it</EstadoEnvio>" } ?: "") + lines + "</RespuestaRegFactuSistemaFacturacion>"

    private fun line(
        number: String = "S-1",
        state: String? = "Correcto",
        diagnostics: String = "",
        operation: String = "Alta",
    ): String =
        "<RespuestaLinea><IDFactura><sf:IDEmisorFactura>89890001K</sf:IDEmisorFactura>" +
            "<sf:NumSerieFactura>$number</sf:NumSerieFactura>" +
            "<sf:FechaExpedicionFactura>01-01-2024</sf:FechaExpedicionFactura></IDFactura>" +
            "<Operacion><sf:TipoOperacion>$operation</sf:TipoOperacion></Operacion>" +
            (state?.let { "<EstadoRegistro>$it</EstadoRegistro>" } ?: "") + diagnostics + "</RespuestaLinea>"

    private fun soapEnvelope(
        body: String,
        header: String = "",
    ): String = "<soap:Envelope xmlns:soap=\"$SOAP_NS\"><soap:Header>$header</soap:Header><soap:Body>$body</soap:Body></soap:Envelope>"
}

private const val RESPONSE_NS: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/RespuestaSuministro.xsd"
private const val INFORMATION_NS: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd"
private const val SOAP_NS: String = "http://schemas.xmlsoap.org/soap/envelope/"
