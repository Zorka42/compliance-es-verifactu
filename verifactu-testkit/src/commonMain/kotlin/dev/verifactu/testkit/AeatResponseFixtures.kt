package dev.verifactu.testkit

import dev.verifactu.aeat.AeatTransportResult
import kotlin.jvm.JvmStatic

/** Synthetic response branches supported by the current minimal response parser. */
public enum class AeatResponseScenario {
    ACCEPTED,
    ACCEPTED_WITH_ERRORS,
    REJECTED,
    DUPLICATE,
    FLOW_CONTROL,
}

/**
 * Minimal synthetic responses for consumer tests, not captured or schema-certified AEAT replies.
 *
 * TEST-prefixed diagnostics intentionally do not claim an official AEAT error-code mapping.
 */
public object AeatResponseFixtures {
    /** Returns one response line with fixed diagnostics and a 60-second wait for FLOW_CONTROL. */
    @JvmStatic
    public fun response(scenario: AeatResponseScenario): AeatTransportResult.XmlResponse {
        val rejected = scenario == AeatResponseScenario.REJECTED || scenario == AeatResponseScenario.DUPLICATE
        val status = if (rejected) "Incorrecto" else "Correcto"
        val lineStatus = if (scenario == AeatResponseScenario.ACCEPTED_WITH_ERRORS) "AceptadoConErrores" else status
        val diagnostics =
            when (scenario) {
                AeatResponseScenario.REJECTED ->
                    "<CodigoErrorRegistro>TEST-REJECTED</CodigoErrorRegistro><DescripcionErrorRegistro>Synthetic rejection</DescripcionErrorRegistro>"
                AeatResponseScenario.ACCEPTED_WITH_ERRORS ->
                    "<CodigoErrorRegistro>TEST-WARNING</CodigoErrorRegistro><DescripcionErrorRegistro>Synthetic warning</DescripcionErrorRegistro>"
                AeatResponseScenario.DUPLICATE ->
                    "<RegistroDuplicado><IdPeticionRegistroDuplicado>TEST-REQUEST</IdPeticionRegistroDuplicado>" +
                        "<EstadoRegistroDuplicado>Correcto</EstadoRegistroDuplicado></RegistroDuplicado>"
                else -> ""
            }
        val wait = if (scenario == AeatResponseScenario.FLOW_CONTROL) 60 else 0
        val invoice = VerifactuFixtures.invoice()
        return AeatTransportResult.XmlResponse(
            200,
            "text/xml; charset=UTF-8",
            "<RespuestaRegFactuSistemaFacturacion>" +
                "<CSV>TEST-CSV</CSV><TiempoEsperaEnvio>$wait</TiempoEsperaEnvio><EstadoEnvio>$status</EstadoEnvio>" +
                "<RespuestaLinea><IDFactura><IDEmisorFactura>${invoice.issuer.value}</IDEmisorFactura>" +
                "<NumSerieFactura>${invoice.number.value}</NumSerieFactura><FechaExpedicionFactura>${invoice.issueDate.value}" +
                "</FechaExpedicionFactura></IDFactura><EstadoRegistro>$lineStatus</EstadoRegistro>$diagnostics</RespuestaLinea>" +
                "</RespuestaRegFactuSistemaFacturacion>",
        )
    }

    /** Returns an HTTP 500 SOAP fault for exercising fault parsing rather than success parsing. */
    @JvmStatic
    public fun soapFault(): AeatTransportResult.XmlResponse =
        AeatTransportResult.XmlResponse(
            500,
            "text/xml",
            "<soap:Fault xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<faultcode>soap:Client</faultcode><faultstring>Synthetic fault</faultstring></soap:Fault>",
        )
}
