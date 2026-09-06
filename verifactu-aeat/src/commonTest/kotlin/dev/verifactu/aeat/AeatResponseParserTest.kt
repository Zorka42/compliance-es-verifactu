package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AeatResponseParserTest {
    @Test
    fun parsesPartialResponsesAndDuplicateLineDiagnostics() {
        val result =
            AeatResponseParser.parseSubmission(
                """
                <RespuestaRegFactuSistemaFacturacion>
                  <CSV>CSV-1</CSV><TiempoEsperaEnvio>60</TiempoEsperaEnvio><EstadoEnvio>ParcialmenteCorrecto</EstadoEnvio>
                  <RespuestaLinea><EstadoRegistro>Correcto</EstadoRegistro></RespuestaLinea>
                  <RespuestaLinea><IDFactura><IDEmisorFactura>89890001K</IDEmisorFactura><NumSerieFactura>TEST-1</NumSerieFactura><FechaExpedicionFactura>01-01-2024</FechaExpedicionFactura></IDFactura><Operacion><TipoOperacion>Alta</TipoOperacion></Operacion><EstadoRegistro>Incorrecto</EstadoRegistro><CodigoErrorRegistro>3000</CodigoErrorRegistro><DescripcionErrorRegistro>Duplicada</DescripcionErrorRegistro><RegistroDuplicado><IdPeticionRegistroDuplicado>old-1</IdPeticionRegistroDuplicado><EstadoRegistroDuplicado>Correcta</EstadoRegistroDuplicado></RegistroDuplicado></RespuestaLinea>
                </RespuestaRegFactuSistemaFacturacion>
                """.trimIndent(),
            )

        val parsed = assertIs<AeatResponseParseResult.Parsed>(result).response
        assertEquals(AeatSubmissionStatus.PARTIALLY_ACCEPTED, parsed.status)
        assertEquals("CSV-1", parsed.csv)
        assertEquals(60, parsed.retryAfterSeconds)
        assertEquals(AeatRecordStatus.ACCEPTED, parsed.lines[0].status)
        assertEquals(AeatRecordStatus.DUPLICATE, parsed.lines[1].status)
        assertEquals("3000", parsed.lines[1].errorCode)
        assertEquals(AeatResponseOperation.REGISTRATION, parsed.lines[1].correlation?.operation)
        assertEquals(
            "TEST-1",
            parsed.lines[1]
                .correlation
                ?.invoice
                ?.invoiceNumber,
        )
        assertEquals(AeatIncidenceDisposition.RECORD_REJECTED, parsed.lines[1].incidence?.disposition)
    }

    @Test
    fun ignoresFutureUnknownFieldsWhileRetainingKnownStates() {
        val result =
            AeatResponseParser.parseSubmission(
                "<RespuestaRegFactuSistemaFacturacion><CampoFuturo><Nada/></CampoFuturo><TiempoEsperaEnvio>0</TiempoEsperaEnvio><EstadoEnvio>Correcto</EstadoEnvio></RespuestaRegFactuSistemaFacturacion>",
            )

        assertEquals(AeatSubmissionStatus.ACCEPTED, assertIs<AeatResponseParseResult.Parsed>(result).response.status)
    }

    @Test
    fun parsesSoapFaultWithoutReturningTheRawEnvelope() {
        val result =
            AeatResponseParser.parseSoapFault(
                "<soap:Fault xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><faultcode>soap:Client</faultcode><faultstring>Invalid request</faultstring></soap:Fault>",
            )

        val fault = assertIs<SoapFaultParseResult.Parsed>(result).fault
        assertEquals("soap:Client", fault.code)
        assertEquals("Invalid request", fault.message)
    }

    @Test
    fun rejectsMalformedResponseXml() {
        val result = AeatResponseParser.parseSubmission("<RespuestaRegFactuSistemaFacturacion><EstadoEnvio>Correcto</EstadoEnvio>")

        assertIs<AeatResponseParseResult.InvalidXml>(result)
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
        val result =
            AeatResponseParser.parseSubmission(
                "<RespuestaRegFactuSistemaFacturacion><EstadoEnvio>Correcto</EstadoEnvio><RespuestaLinea><EstadoRegistro>AceptadoConErrores</EstadoRegistro><CodigoErrorRegistro>9999</CodigoErrorRegistro><DescripcionErrorRegistro>Future catalogue entry</DescripcionErrorRegistro></RespuestaLinea></RespuestaRegFactuSistemaFacturacion>",
            )

        val line = assertIs<AeatResponseParseResult.Parsed>(result).response.lines.single()
        assertEquals("9999", line.incidence?.code)
        assertEquals(AeatIncidenceDisposition.UNKNOWN, line.incidence?.disposition)
        assertEquals(AeatSubsanationRequirement.UNKNOWN, line.incidence?.subsanationRequirement)
        assertNull(line.incidence?.source)
    }
}
