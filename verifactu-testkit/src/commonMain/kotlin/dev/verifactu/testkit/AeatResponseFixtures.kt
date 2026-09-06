package dev.verifactu.testkit

import dev.verifactu.aeat.AeatOperationType
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.core.InvoiceIdentifier
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Synthetic response branches for deterministic consumer integration tests. */
public enum class AeatResponseScenario {
    ACCEPTED,
    ACCEPTED_WITH_ERRORS,
    REJECTED,
    DUPLICATE,
    FLOW_CONTROL,
}

/**
 * Schema-checked synthetic responses for consumer tests, not captured AEAT replies.
 *
 * The numeric 999999 diagnostic is synthetic and does not claim an official error-code mapping.
 */
public object AeatResponseFixtures {
    /** Returns one response line with fixed diagnostics and a 60-second wait for FLOW_CONTROL. */
    @JvmStatic
    @JvmOverloads
    public fun response(
        scenario: AeatResponseScenario,
        invoice: InvoiceIdentifier = VerifactuFixtures.invoice(),
        operation: AeatOperationType = AeatOperationType.REGISTRATION,
    ): AeatTransportResult.XmlResponse {
        require(operation != AeatOperationType.UNKNOWN_STATE) { "Synthetic fixtures need a known submission operation." }
        val operationValue = if (operation == AeatOperationType.REGISTRATION) "Alta" else "Anulacion"
        val rejected = scenario == AeatResponseScenario.REJECTED || scenario == AeatResponseScenario.DUPLICATE
        val status =
            when {
                rejected -> "Incorrecto"
                scenario == AeatResponseScenario.ACCEPTED_WITH_ERRORS -> "ParcialmenteCorrecto"
                else -> "Correcto"
            }
        val lineStatus = if (scenario == AeatResponseScenario.ACCEPTED_WITH_ERRORS) "AceptadoConErrores" else status
        val diagnostics =
            when (scenario) {
                AeatResponseScenario.REJECTED ->
                    "<CodigoErrorRegistro>999999</CodigoErrorRegistro><DescripcionErrorRegistro>Synthetic rejection</DescripcionErrorRegistro>"
                AeatResponseScenario.ACCEPTED_WITH_ERRORS ->
                    "<CodigoErrorRegistro>999999</CodigoErrorRegistro><DescripcionErrorRegistro>Synthetic warning</DescripcionErrorRegistro>"
                AeatResponseScenario.DUPLICATE ->
                    "<RegistroDuplicado><sf:IdPeticionRegistroDuplicado>TEST-REQUEST</sf:IdPeticionRegistroDuplicado>" +
                        "<sf:EstadoRegistroDuplicado>Correcta</sf:EstadoRegistroDuplicado></RegistroDuplicado>"
                else -> ""
            }
        val wait = if (scenario == AeatResponseScenario.FLOW_CONTROL) 60 else 0
        val receipt = if (rejected) "" else "<CSV>TEST-CSV</CSV>"
        return AeatTransportResult.XmlResponse(
            200,
            "text/xml; charset=UTF-8",
            "<RespuestaRegFactuSistemaFacturacion xmlns=\"${NAMESPACE}RespuestaSuministro.xsd\" " +
                "xmlns:sf=\"${NAMESPACE}SuministroInformacion.xsd\">" +
                "$receipt<Cabecera><sf:ObligadoEmision><sf:NombreRazon>Example issuer</sf:NombreRazon>" +
                "<sf:NIF>${invoice.issuer.value.escapeFixtureXml()}</sf:NIF></sf:ObligadoEmision></Cabecera>" +
                "<TiempoEsperaEnvio>$wait</TiempoEsperaEnvio><EstadoEnvio>$status</EstadoEnvio>" +
                "<RespuestaLinea><IDFactura><sf:IDEmisorFactura>${invoice.issuer.value.escapeFixtureXml()}</sf:IDEmisorFactura>" +
                "<sf:NumSerieFactura>${invoice.number.value.escapeFixtureXml()}</sf:NumSerieFactura>" +
                "<sf:FechaExpedicionFactura>${invoice.issueDate.value}</sf:FechaExpedicionFactura></IDFactura>" +
                "<Operacion><sf:TipoOperacion>$operationValue</sf:TipoOperacion></Operacion>" +
                "<EstadoRegistro>$lineStatus</EstadoRegistro>$diagnostics</RespuestaLinea>" +
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

private const val NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/"

private fun String.escapeFixtureXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("\r", "&#13;")
