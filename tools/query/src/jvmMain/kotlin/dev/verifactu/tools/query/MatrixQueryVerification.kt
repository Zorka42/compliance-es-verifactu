package dev.verifactu.tools.query

import java.io.IOException
import java.nio.file.Path
import java.time.DateTimeException
import java.time.OffsetDateTime

internal fun verifyMatrixQueries(root: Path): CliResult {
    val inputs = matrixQueryInputs(root)
    val lines =
        listOf("small", "pagination").map { scope ->
            val attempts = matrixQueries(root, scope)
            val state = if (attempts.isEmpty()) "PENDING" else queryEvidenceState(inputs, attempts.last(), scope)
            "query.$scope.state=$state"
        }
    val pass = lines.all { it.endsWith("=PASS") }
    return CliResult(
        lines.joinToString("\n") + "\nquery.matrix=${if (pass) "PASS" else "INCOMPLETE"}\n" +
            "Saved client evidence verifies TEST routing, response correlation and observed results; it cannot prove AEAT internal isolation.",
        exitCode = if (pass) 0 else 3,
    )
}

private fun queryEvidenceState(
    inputs: MatrixQueryInputs,
    directory: Path,
    scope: String,
): String =
    try {
        verifyQueryEvidence(inputs, directory, scope)
        "PASS"
    } catch (_: IOException) {
        "REVIEW"
    } catch (_: QueryInputException) {
        "REVIEW"
    } catch (_: DateTimeException) {
        "REVIEW"
    }

private fun verifyQueryEvidence(
    inputs: MatrixQueryInputs,
    directory: Path,
    scope: String,
) {
    verifyMatrixQueryMarker(inputs, directory, scope)
    val outcome = queryProperties(directory.resolve("outcome.properties"))
    val count = outcome["pages"]?.toIntOrNull()
    queryRequire(
        outcome["state"] == "COMPLETE" && outcome["exit.code"] == "0" && count != null && count in 2..100,
        "Incomplete query evidence.",
    )
    val network = queryProperties(directory.resolve("network.log"))
    queryRequire(
        network["environment"] == "TEST" &&
            network["request.url"] == TEST_QUERY_ENDPOINT &&
            network["operation"] == "ConsultaFactuSistemaFacturacion" &&
            network["http.method"] == "POST" &&
            network["redirect.policy"] == "REFUSE" &&
            network["tls.server.trust.policy"] == "SYSTEM_DEFAULT",
        "Unexpected query network route or policy.",
    )
    verifyMatrixPages(inputs, directory, scope, network, count!!)
}

private fun verifyMatrixPages(
    inputs: MatrixQueryInputs,
    directory: Path,
    scope: String,
    network: Map<String, String>,
    count: Int,
) {
    val query = InvoiceQuery()
    val records = QueryDirection.entries.associateWith { mutableListOf<QueryRecord>() }
    val previous = mutableMapOf<QueryDirection, QueryPage>()
    val pages = mutableMapOf<QueryDirection, Int>()
    val cursors = mutableSetOf<Pair<QueryDirection, QueryInvoice>>()
    val rows = mutableListOf("direction\tissuer\tinvoice\tissue_date\tstate\ttotal")
    for (index in 1..count) {
        val direction =
            QueryDirection.entries.singleOrNull { it.name == network["page.$index.direction"] }
                ?: throw QueryInputException("Unknown query direction.")
        queryRequire(index != 1 || direction == QueryDirection.ISSUED, "Issued query must precede received query.")
        queryRequire(direction != QueryDirection.ISSUED || QueryDirection.RECEIVED !in previous, "Query directions are out of order.")
        val pageDirectory = directory.resolve("${direction.name.lowercase(java.util.Locale.ROOT)}-$index")
        val page = verifyMatrixPage(inputs, pageDirectory, query, direction, previous[direction])
        queryRequire(
            network["page.$index.response.url"] == TEST_QUERY_ENDPOINT &&
                network["page.$index.http.status"]?.toIntOrNull() in 200..299 &&
                network["page.$index.kind"] == "XML_RESPONSE" &&
                network["page.$index.delivery"] == "RESPONSE_RECEIVED",
            "Missing successful TEST response receipt.",
        )
        page.next?.let { queryRequire(cursors.add(direction to it), "Repeated query pagination key.") }
        previous[direction] = page
        pages[direction] = pages.getOrDefault(direction, 0) + 1
        records.getValue(direction).addAll(page.records)
        page.records.forEach { record ->
            rows +=
                listOf(
                    direction.name.lowercase(java.util.Locale.ROOT),
                    record.invoice.issuer,
                    record.invoice.number,
                    record.invoice.date,
                    record.status,
                    record.total.orEmpty(),
                ).joinToString("\t", transform = ::queryField)
        }
    }
    queryRequire(QueryDirection.entries.all { previous[it]?.next == null && it in previous }, "Both directions must finish pagination.")
    queryRequire(
        readPrivateInput(directory.resolve("invoices.tsv"), MAX_XML_BYTES) == rows.joinToString("\n", postfix = "\n"),
        "Saved list differs from the validated XML pages.",
    )
    records.forEach { (direction, values) ->
        queryRequire(values.map { it.invoice }.distinct().size == values.size, "Repeated invoice across query pages.")
        verifyMatrixRecords(inputs, scope, values)
        if (scope == "pagination") queryRequire(pages.getValue(direction) >= 2, "Live pagination was not observed for both directions.")
    }
}

private fun verifyMatrixPage(
    inputs: MatrixQueryInputs,
    directory: Path,
    query: InvoiceQuery,
    direction: QueryDirection,
    previous: QueryPage?,
): QueryPage {
    val request = readPrivateInput(directory.resolve("request.xml"), 65_536)
    queryRequire(
        request == query.prepare(direction, inputs.party, inputs.period, previous),
        "Query request is not the expected continuation.",
    )
    queryRequire(
        queryProperties(directory.resolve("started.properties"))["request.sha256"] == queryDigest(request),
        "Query request digest differs.",
    )
    val page = query.inspect(readPrivateInput(directory.resolve("response.xml"), MAX_XML_BYTES))
    queryRequire(
        page.direction == direction && page.party.nif == inputs.party.nif && page.period == inputs.period,
        "Query response correlation failed.",
    )
    return page
}

private fun verifyMatrixRecords(
    inputs: MatrixQueryInputs,
    scope: String,
    values: List<QueryRecord>,
) {
    val relevant = values.filter { it.invoice.number.startsWith(inputs.prefix + "-") && it.invoice.issuer == inputs.party.nif }
    val base = queryProperties(inputs.root.resolve("base.properties"))
    val expectedDate = base.getValue("invoice.date")
    if (scope == "small") {
        queryRequire(
            relevant.any {
                it.invoice.number == inputs.prefix + "-R" && it.invoice.date == expectedDate && it.status == "Correcto"
            },
            "Registration not visible in this query role.",
        )
        queryRequire(
            relevant.any {
                it.invoice.number == inputs.prefix + "-W" && it.invoice.date == expectedDate && it.status == "Anulado"
            },
            "Cancellation not visible in this query role.",
        )
    } else {
        val paging = relevant.filter { it.invoice.number.startsWith(inputs.prefix + "-P-") }
        val expected = (1..10001).map { inputs.prefix + "-P-" + it.toString().padStart(5, '0') }.toSet()
        queryRequire(
            paging.map { it.invoice.number }.toSet() == expected &&
                paging.all { it.invoice.date == expectedDate && it.status == "Correcto" },
            "Pagination dataset is incomplete or has unexpected states.",
        )
    }
}

private fun verifyMatrixQueryMarker(
    inputs: MatrixQueryInputs,
    directory: Path,
    scope: String,
) {
    val marker = queryProperties(directory.resolve("matrix-query.properties"))
    queryRequire(
        marker["evidence.kind"] == "AEAT_TEST" && marker["scope"] == scope && marker["base.sha256"] == inputs.digest,
        "Query evidence belongs to a different session or simulation.",
    )
    val started = OffsetDateTime.parse(marker["started.at"].orEmpty())
    val lastCase = if (scope == "small") "rejection" else "pagination-11"
    val caseDirectory = inputs.root.resolve("cases/$lastCase")
    val attempts =
        java.nio.file.Files.list(caseDirectory).use { paths ->
            paths.filter { it.fileName.toString().matches(Regex("attempt-[0-9]{3}")) }.sorted().collect(
                java.util.stream.Collectors
                    .toList(),
            )
        }
    queryRequire(attempts.isNotEmpty(), "Missing write timing evidence.")
    val completed = queryProperties(attempts.last().resolve("matrix-timing.properties"))["completed.at"].orEmpty()
    queryRequire(!started.toInstant().isBefore(OffsetDateTime.parse(completed).toInstant()), "Query predates the scope's write evidence.")
}
