package dev.verifactu.tools.query

import java.net.URI

internal fun queryNetworkHeader(): String =
    "environment=TEST\n" +
        "operation=ConsultaFactuSistemaFacturacion\n" +
        "http.method=POST\n" +
        "request.url=$TEST_QUERY_ENDPOINT\n" +
        "request.domain=${URI(TEST_QUERY_ENDPOINT).host}\n" +
        "redirect.policy=REFUSE\n" +
        "tls.server.trust.policy=SYSTEM_DEFAULT"

internal fun queryNetworkPage(
    direction: QueryDirection,
    page: Int,
    reply: NativeQueryReceipt,
): String {
    val prefix = "page.$page"
    // Only the allowlisted URL may enter this shareable report, never remote diagnostics or certificate identifiers.
    val responseUrl = reply.responseUrl?.takeIf { it == TEST_QUERY_ENDPOINT } ?: "unavailable"
    return "$prefix.direction=${direction.name}\n" +
        "$prefix.response.url=$responseUrl\n" +
        "$prefix.http.status=${reply.httpStatus ?: "unavailable"}\n" +
        "$prefix.kind=${reply.kind}\n" +
        "$prefix.delivery=${reply.delivery}"
}
