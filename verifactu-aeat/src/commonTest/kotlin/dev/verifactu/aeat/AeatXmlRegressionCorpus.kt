package dev.verifactu.aeat

/** Synthetic, deterministic inputs shared by JAXP and Foundation tests; never live AEAT responses. */
internal object AeatXmlRegressionCorpus {
    const val PRIVATE_MARKER: String = "SYNTHETIC-PRIVATE-MARKER"

    val malformed: List<Pair<String, String>>
        get() =
            listOf(
                "empty" to "",
                "truncated-root" to response().dropLast(1),
                "second-root" to response() + response(),
                "text-before-root" to PRIVATE_MARKER + response(),
                "text-after-root" to response() + PRIVATE_MARKER,
                "mismatched-tag" to response().replace("</CSV>", "</Other>"),
                "undeclared-prefix" to response().replace("<CSV>", "<other:CSV>"),
                "duplicate-attribute" to response().replace("<CSV>", "<CSV a='1' a='2'>"),
                "unterminated-comment" to response("<!-- $PRIVATE_MARKER"),
                "undeclared-entity" to response().replace(PRIVATE_MARKER, "&notDeclared;"),
                "literal-control" to response().replace(PRIVATE_MARKER, "\u0001"),
                "reference-control" to response().replace(PRIVATE_MARKER, "&#1;"),
                "reference-null" to response().replace(PRIVATE_MARKER, "&#0;"),
                "reference-surrogate" to response().replace(PRIVATE_MARKER, "&#xD800;"),
                "reference-outside-unicode" to response().replace(PRIVATE_MARKER, "&#x110000;"),
                "unpaired-high-surrogate" to response().replace(PRIVATE_MARKER, "\uD800"),
                "unpaired-low-surrogate" to response().replace(PRIVATE_MARKER, "\uDC00"),
                "external-dtd" to "<!DOCTYPE r SYSTEM 'https://example.invalid/never-fetch.dtd'>${response()}",
                "internal-entity" to "<!DOCTYPE r [<!ENTITY x '$PRIVATE_MARKER'>]>${response()}",
                "parameter-entity" to "<!DOCTYPE r [<!ENTITY % x SYSTEM 'file:///never-read'>%x;]>${response()}",
                "unsupported-encoding" to "<?xml version='1.0' encoding='UTF-16'?>${response()}",
            )

    val ambiguous: List<Pair<String, String>>
        get() =
            listOf(
                "wrong-root-namespace" to response().replace(RESPONSE_NAMESPACE, "urn:other"),
                "namespace-reset-on-line" to response(line().replace("<RespuestaLinea>", "<RespuestaLinea xmlns=''>")),
                "rebound-identity-prefix" to response(line().replace("<IDFactura>", "<IDFactura xmlns:sf='urn:other'>")),
                "duplicate-identity-leaf" to
                    response(
                        line().replace(
                            "</sf:NumSerieFactura>",
                            "</sf:NumSerieFactura><sf:NumSerieFactura>other</sf:NumSerieFactura>",
                        ),
                    ),
                "nested-scalar" to response().replace(PRIVATE_MARKER, "<Future>$PRIVATE_MARKER</Future>"),
                "mixed-root-text" to response(PRIVATE_MARKER),
                "unicode-space-in-root" to response("\u00A0"),
                "unicode-space-in-identity" to response(line().replace("<IDFactura>", "<IDFactura>\u2003")),
                "unicode-space-in-soap-body" to envelope("\u00A0" + response()),
                "second-soap-body" to envelope(response()).replace("</s:Envelope>", "<s:Body/> </s:Envelope>"),
                "second-soap-payload" to envelope(response() + response()),
                "response-in-header" to "<s:Envelope xmlns:s='$SOAP_NAMESPACE'><s:Header>${response()}</s:Header><s:Body/></s:Envelope>",
                "soap12-submission" to envelope(response()).replace(SOAP_NAMESPACE, SOAP_12_NAMESPACE),
            )

    fun response(extension: String = line()): String =
        "<RespuestaRegFactuSistemaFacturacion xmlns='$RESPONSE_NAMESPACE' xmlns:sf='$INFORMATION_NAMESPACE'>" +
            "<CSV>$PRIVATE_MARKER</CSV><Cabecera><sf:ObligadoEmision><sf:NombreRazon>Synthetic issuer</sf:NombreRazon>" +
            "<sf:NIF>89890001K</sf:NIF></sf:ObligadoEmision></Cabecera>" +
            "<TiempoEsperaEnvio>60</TiempoEsperaEnvio><EstadoEnvio>Correcto</EstadoEnvio>" +
            extension + "</RespuestaRegFactuSistemaFacturacion>"

    fun line(number: String = "S-1"): String =
        "<RespuestaLinea><IDFactura><sf:IDEmisorFactura>89890001K</sf:IDEmisorFactura>" +
            "<sf:NumSerieFactura>$number</sf:NumSerieFactura><sf:FechaExpedicionFactura>01-01-2024</sf:FechaExpedicionFactura>" +
            "</IDFactura><Operacion><sf:TipoOperacion>Alta</sf:TipoOperacion></Operacion>" +
            "<EstadoRegistro>Correcto</EstadoRegistro></RespuestaLinea>"

    fun envelope(payload: String): String = "<s:Envelope xmlns:s='$SOAP_NAMESPACE'><s:Body>$payload</s:Body></s:Envelope>"

    fun fault(message: String = PRIVATE_MARKER): String =
        "<s:Fault xmlns:s='$SOAP_NAMESPACE'><faultcode>s:Client</faultcode><faultstring>$message</faultstring></s:Fault>"
}

private const val RESPONSE_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/RespuestaSuministro.xsd"
private const val INFORMATION_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd"
private const val SOAP_NAMESPACE: String = "http://schemas.xmlsoap.org/soap/envelope/"
private const val SOAP_12_NAMESPACE: String = "http://www.w3.org/2003/05/soap-envelope"
