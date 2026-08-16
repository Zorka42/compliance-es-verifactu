package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AeatResponseParserTest {
    @Test
    fun parsesPartialResponsesAndDuplicateLineDiagnostics() {
        val result =
            AeatResponseParser.parseSubmission(
                """
                <RespuestaRegFactuSistemaFacturacion>
                  <CSV>CSV-1</CSV><TiempoEsperaEnvio>60</TiempoEsperaEnvio><EstadoEnvio>ParcialmenteCorrecto</EstadoEnvio>
                  <RespuestaLinea><EstadoRegistro>Correcto</EstadoRegistro></RespuestaLinea>
                  <RespuestaLinea><EstadoRegistro>Incorrecto</EstadoRegistro><CodigoErrorRegistro>1001</CodigoErrorRegistro><DescripcionErrorRegistro>Duplicada</DescripcionErrorRegistro><RegistroDuplicado><IdPeticionRegistroDuplicado>old-1</IdPeticionRegistroDuplicado><EstadoRegistroDuplicado>Correcta</EstadoRegistroDuplicado></RegistroDuplicado></RespuestaLinea>
                </RespuestaRegFactuSistemaFacturacion>
                """.trimIndent(),
            )

        val parsed = assertIs<AeatResponseParseResult.Parsed>(result).response
        assertEquals(AeatSubmissionStatus.PARTIALLY_ACCEPTED, parsed.status)
        assertEquals("CSV-1", parsed.csv)
        assertEquals(60, parsed.retryAfterSeconds)
        assertEquals(AeatRecordStatus.ACCEPTED, parsed.lines[0].status)
        assertEquals(AeatRecordStatus.DUPLICATE, parsed.lines[1].status)
        assertEquals("1001", parsed.lines[1].errorCode)
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
}
