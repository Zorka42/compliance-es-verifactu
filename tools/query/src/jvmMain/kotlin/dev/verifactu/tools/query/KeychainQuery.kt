package dev.verifactu.tools.query

import dev.verifactu.aeat.AeatResponseParser
import dev.verifactu.aeat.SoapFaultParseResult
import java.io.IOException
import java.nio.file.Path

internal data class QueryRunResult(
    val output: String,
    val exitCode: Int = 0,
)

internal data class KeychainQueryInputs(
    val party: QueryParty,
    val period: QueryPeriod,
    val directions: List<QueryDirection>,
    val output: Path,
)

internal fun queryCommand(args: List<String>): QueryRunResult =
    if (args.firstOrNull() == "fetch") fetchQueries(parseOptions(args.drop(1))) else QueryRunResult(runCommand(args))

internal fun fetchQueries(
    options: Map<String, String>,
    helperProvider: () -> Path = ::installedHelper,
    transport: (Path, NativeQueryCall) -> NativeQueryReceipt = ::executeNativeQuery,
): QueryRunResult {
    options.allow("direction", "party-file", "year", "month", "out", "keychain")
    queryRequire(options["keychain"] == "true", "Fetching requires explicit --keychain selection; TEST only.")
    val party = queryProperties(Path.of(options.required("party-file")))
    queryRequire(party.keys == setOf("nif", "name"), "Party file must contain exactly nif and name.")
    val directions =
        when (options.required("direction")) {
            "issued" -> listOf(QueryDirection.ISSUED)
            "received" -> listOf(QueryDirection.RECEIVED)
            "both" -> QueryDirection.entries
            else -> throw QueryInputException("Direction must be issued, received, or both.")
        }
    val inputs =
        KeychainQueryInputs(
            QueryParty(party.getValue("nif"), party.getValue("name")),
            QueryPeriod(options.required("year"), options.required("month")),
            directions,
            Path.of(options.required("out")),
        )
    directions.forEach { InvoiceQuery().prepare(it, inputs.party, inputs.period) }
    val helper = helperProvider()
    privateDirectory(inputs.output)
    return QueryFetchSession(inputs, helper, transport).run()
}

private class QueryFetchSession(
    private val inputs: KeychainQueryInputs,
    private val helper: Path,
    private val transport: (Path, NativeQueryCall) -> NativeQueryReceipt,
) {
    private val rows = mutableListOf("direction\tissuer\tinvoice\tissue_date\tstate\ttotal")
    private val summaries = mutableListOf<String>()
    private var identity: String? = null
    private var started = false
    private var pageCount = 0

    fun run(): QueryRunResult =
        try {
            privateFile(inputs.output.resolve("started.properties"), "environment=TEST\noperation=CONSULTATION_ONLY\n")
            var result: QueryRunResult? = null
            for ((index, direction) in inputs.directions.withIndex()) {
                result = fetchDirection(direction)
                if (result != null) {
                    recordIncomplete(index)
                    break
                }
            }
            val outcome = result ?: QueryRunResult("COMPLETE: all requested consultations finished.")
            privateFile(inputs.output.resolve("invoices.tsv"), rows.joinToString("\n", postfix = "\n"))
            val output =
                "Environment: TEST; operation: CONSULTATION_ONLY\n" + summaries.joinToString("\n") + "\n" + outcome.output +
                    "\nSaved list: ${inputs.output.resolve("invoices.tsv")}\nOnly VERI*FACTU TEST records are covered."
            privateFile(inputs.output.resolve("summary.txt"), output + "\n")
            privateFile(
                inputs.output.resolve("outcome.properties"),
                "state=${if (outcome.exitCode == 0) "COMPLETE" else "INCOMPLETE"}\nexit.code=${outcome.exitCode}\npages=$pageCount\n",
            )
            outcome.copy(output = output)
        } catch (_: IOException) {
            QueryRunResult(
                "Cannot persist the complete consultation. Preserve started.properties and page artifacts; no automatic retry.",
                if (started) 4 else 2,
            )
        } catch (_: SecurityException) {
            QueryRunResult("Local resource access was denied. Preserve existing query artifacts.", if (started) 4 else 2)
        } catch (_: QueryInputException) {
            QueryRunResult(
                "Saved consultation evidence is missing or inconsistent. Preserve started.properties and existing page artifacts.",
                if (started) 4 else 2,
            )
        }

    private fun recordIncomplete(index: Int) {
        summaries += "${inputs.directions[index].name}: INCOMPLETE"
        inputs.directions.drop(index + 1).forEach { summaries += "${it.name}: NOT_STARTED" }
    }

    private fun fetchDirection(direction: QueryDirection): QueryRunResult? {
        val query = InvoiceQuery()
        val seen = mutableSetOf<QueryInvoice>()
        var previous: QueryPage? = null
        var records = 0
        while (true) {
            if (pageCount >= 100) return QueryRunResult("INCOMPLETE: page limit reached. Existing rows are partial.", 3)
            val request = query.prepare(direction, inputs.party, inputs.period, previous)
            val directory = inputs.output.resolve("${direction.name.lowercase()}-${++pageCount}")
            privateDirectory(directory)
            privateFile(directory.resolve("request.xml"), request)
            privateFile(directory.resolve("started.properties"), "request.sha256=${queryDigest(request)}\nstate=STARTED\n")
            started = true
            val reply =
                try {
                    transport(
                        helper,
                        NativeQueryCall(directory.resolve("request.xml"), directory, queryDigest(request), TEST_QUERY_ENDPOINT, identity),
                    )
                } catch (_: Exception) {
                    NativeQueryReceipt("HELPER_FAILED", "UNKNOWN", null, null)
                }
            privateFile(
                directory.resolve("delivery.properties"),
                "kind=${reply.kind}\ndelivery=${reply.delivery}\nhttp.status=${reply.httpStatus ?: "unavailable"}\n",
            )
            val page =
                when (val reviewed = reviewPage(reply, directory, direction)) {
                    is PageReview.Failed -> return reviewed.result
                    is PageReview.Valid -> reviewed.page
                }
            if (page.next != null && !seen.add(page.next)) return QueryRunResult("INCOMPLETE: repeated pagination key.", 3)
            if (rows.size - 1 + page.records.size >
                100_000
            ) {
                return QueryRunResult("INCOMPLETE: record limit reached. Existing rows are partial.", 3)
            }
            page.records.forEach { record ->
                rows +=
                    listOf(
                        direction.name.lowercase(),
                        record.invoice.issuer,
                        record.invoice.number,
                        record.invoice.date,
                        record.status,
                        record.total.orEmpty(),
                    ).joinToString("\t", transform = ::queryField)
            }
            records += page.records.size
            if (page.next == null) {
                summaries += "${direction.name}: COMPLETE, records=$records"
                return null
            }
            previous = page
        }
    }

    private fun reviewPage(
        reply: NativeQueryReceipt,
        directory: Path,
        direction: QueryDirection,
    ): PageReview {
        if (reply.kind !=
            "XML_RESPONSE"
        ) {
            return PageReview.Failed(QueryRunResult("INCOMPLETE: ${reply.kind}; delivery=${reply.delivery}. No automatic retry.", 4))
        }
        if (reply.identity?.matches(Regex("[a-f0-9]{64}")) != true || identity != null && identity != reply.identity) {
            return PageReview.Failed(QueryRunResult("INCOMPLETE: selected identity could not be correlated with this attempt.", 4))
        }
        identity = reply.identity
        val response = readPrivateInput(directory.resolve("response.xml"), MAX_XML_BYTES)
        if (reply.httpStatus !in
            200..299
        ) {
            return PageReview.Failed(QueryRunResult("INCOMPLETE: non-success HTTP ${reply.httpStatus ?: "unknown"}.", 3))
        }
        if (AeatResponseParser.parseSoapFault(
                response,
            ) is SoapFaultParseResult.Parsed
        ) {
            return PageReview.Failed(QueryRunResult("INCOMPLETE: SOAP_FAULT. Inspect the protected response.xml.", 3))
        }
        val page =
            try {
                InvoiceQuery().inspect(response)
            } catch (_: QueryInputException) {
                return PageReview.Failed(QueryRunResult("INCOMPLETE: response is malformed, unsafe, or not a valid consultation page.", 3))
            }
        if (page.direction != direction || page.party.nif != inputs.party.nif || page.period != inputs.period) {
            return PageReview.Failed(QueryRunResult("INCOMPLETE: response party, direction, or period differs from the request.", 3))
        }
        return PageReview.Valid(page)
    }
}

internal const val TEST_QUERY_ENDPOINT = "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"

private sealed interface PageReview {
    data class Valid(
        val page: QueryPage,
    ) : PageReview

    data class Failed(
        val result: QueryRunResult,
    ) : PageReview
}
