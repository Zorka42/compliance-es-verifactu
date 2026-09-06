package dev.verifactu.tools.query

internal enum class QueryDirection(
    val xmlName: String,
) {
    ISSUED("ObligadoEmision"),
    RECEIVED("Destinatario"),
}

internal data class QueryParty(
    val nif: String,
    val name: String,
) {
    override fun toString(): String = "QueryParty(redacted)"
}

internal data class QueryPeriod(
    val year: String,
    val month: String,
)

internal data class QueryInvoice(
    val issuer: String,
    val number: String,
    val date: String,
) {
    override fun toString(): String = "QueryInvoice(redacted)"
}

internal data class QueryRecord(
    val invoice: QueryInvoice,
    val status: String,
    val total: String?,
) {
    override fun toString(): String = "QueryRecord(status=$status, details=redacted)"
}

internal data class QueryPage(
    val direction: QueryDirection,
    val party: QueryParty,
    val period: QueryPeriod,
    val records: List<QueryRecord>,
    val next: QueryInvoice?,
) {
    override fun toString(): String = "QueryPage(records=${records.size}, more=${next != null}, details=redacted)"
}

internal class InvoiceQuery(
    private val xml: QueryXml = QueryXml(),
) {
    fun prepare(
        direction: QueryDirection,
        party: QueryParty,
        period: QueryPeriod,
        previous: QueryPage? = null,
    ): String {
        queryRequire(party.name.isNotBlank(), "The party name must not be blank.")
        previous?.let { validateContinuation(it, direction, party, period) }
        val cursor =
            previous
                ?.next
                ?.let {
                    "<con:ClavePaginacion><sf:IDEmisorFactura>${it.issuer.escapeXml()}</sf:IDEmisorFactura>" +
                        "<sf:NumSerieFactura>${it.number.escapeXml()}</sf:NumSerieFactura>" +
                        "<sf:FechaExpedicionFactura>${it.date.escapeXml()}</sf:FechaExpedicionFactura></con:ClavePaginacion>"
                }.orEmpty()
        val payload =
            "<con:ConsultaFactuSistemaFacturacion xmlns:con=\"$QUERY_NAMESPACE\" xmlns:sf=\"$COMMON_NAMESPACE\">" +
                "<con:Cabecera><sf:IDVersion>1.0</sf:IDVersion><sf:${direction.xmlName}>" +
                "<sf:NombreRazon>${party.name.escapeXml()}</sf:NombreRazon><sf:NIF>${party.nif.escapeXml()}</sf:NIF>" +
                "</sf:${direction.xmlName}></con:Cabecera><con:FiltroConsulta><con:PeriodoImputacion>" +
                "<sf:Ejercicio>${period.year.escapeXml()}</sf:Ejercicio><sf:Periodo>${period.month.escapeXml()}</sf:Periodo>" +
                "</con:PeriodoImputacion>$cursor</con:FiltroConsulta></con:ConsultaFactuSistemaFacturacion>"
        xml.parse(payload, QUERY_NAMESPACE, "ConsultaFactuSistemaFacturacion")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope xmlns:soap=\"$SOAP_NAMESPACE\">" +
            "<soap:Body>$payload</soap:Body></soap:Envelope>"
    }

    fun inspect(response: String): QueryPage {
        val root = xml.parse(response, RESPONSE_NAMESPACE, "RespuestaConsultaFactuSistemaFacturacion")
        val header = root.required(RESPONSE_NAMESPACE, "Cabecera")
        val direction =
            QueryDirection.entries.singleOrNull { header.elements(COMMON_NAMESPACE, it.xmlName).size == 1 }
                ?: throw QueryInputException("The query response must identify one party and direction.")
        val party = header.required(COMMON_NAMESPACE, direction.xmlName)
        val period = root.required(RESPONSE_NAMESPACE, "PeriodoImputacion")
        val records =
            root.elements(RESPONSE_NAMESPACE, "RegistroRespuestaConsultaFactuSistemaFacturacion").map { element ->
                QueryRecord(
                    element.required(RESPONSE_NAMESPACE, "IDFactura").invoice(),
                    element.required(RESPONSE_NAMESPACE, "EstadoRegistro").required(RESPONSE_NAMESPACE, "EstadoRegistro").textContent,
                    element.required(RESPONSE_NAMESPACE, "DatosRegistroFacturacion").optionalText(RESPONSE_NAMESPACE, "ImporteTotal"),
                )
            }
        val more = root.required(RESPONSE_NAMESPACE, "IndicadorPaginacion").textContent == "S"
        val cursor = root.elements(RESPONSE_NAMESPACE, "ClavePaginacion").singleOrNull()?.invoice()
        queryRequire(more == (cursor != null), "Pagination indicator and continuation key disagree.")
        val hasData = root.required(RESPONSE_NAMESPACE, "ResultadoConsulta").textContent == "ConDatos"
        queryRequire(hasData == records.isNotEmpty(), "Query result and record count disagree.")
        queryRequire(!more || records.isNotEmpty(), "A continuation requires records on the current page.")
        return QueryPage(
            direction,
            QueryParty(party.required(COMMON_NAMESPACE, "NIF").textContent, party.required(COMMON_NAMESPACE, "NombreRazon").textContent),
            QueryPeriod(
                period.required(RESPONSE_NAMESPACE, "Ejercicio").textContent,
                period.required(RESPONSE_NAMESPACE, "Periodo").textContent,
            ),
            records,
            cursor,
        )
    }

    private fun validateContinuation(
        previous: QueryPage,
        direction: QueryDirection,
        party: QueryParty,
        period: QueryPeriod,
    ) {
        queryRequire(
            previous.direction == direction && previous.party.nif == party.nif && previous.period == period,
            "The previous page belongs to a different party, direction, or period.",
        )
        queryRequire(previous.next != null, "The previous response has no next page.")
    }

    private fun org.w3c.dom.Element.invoice(): QueryInvoice =
        QueryInvoice(
            required(COMMON_NAMESPACE, "IDEmisorFactura").textContent,
            required(COMMON_NAMESPACE, "NumSerieFactura").textContent,
            required(COMMON_NAMESPACE, "FechaExpedicionFactura").textContent,
        )
}
