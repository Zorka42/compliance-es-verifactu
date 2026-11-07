package dev.verifactu.tools.query

import dev.verifactu.aeat.AeatOperationType
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceIssueDate
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.ValueResult
import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal fun matrixReply(
    fields: Map<String, String>,
    case: String,
): String {
    val scenario =
        when (case) {
            "warning" -> AeatResponseScenario.ACCEPTED_WITH_ERRORS
            "rejection" -> AeatResponseScenario.REJECTED
            "duplicate" -> AeatResponseScenario.DUPLICATE
            else -> AeatResponseScenario.ACCEPTED
        }
    val count = fields["batch.count"]?.toInt() ?: 1
    val start = fields["batch.start"]?.toInt() ?: 1
    val replies =
        (start until start + count).map { index ->
            val number =
                fields.getValue("invoice.number") + if ("batch.count" in fields) "-${index.toString().padStart(5, '0')}" else ""
            val invoice =
                InvoiceIdentifier(
                    (TaxIdentifier.parse(fields.getValue("issuer.nif")) as ValueResult.Valid).value,
                    (InvoiceNumber.parse(number) as ValueResult.Valid).value,
                    (InvoiceIssueDate.parse(fields.getValue("invoice.date")) as ValueResult.Valid).value,
                )
            var reply =
                AeatResponseFixtures
                    .response(
                        scenario,
                        invoice,
                        if (case ==
                            "cancellation"
                        ) {
                            AeatOperationType.CANCELLATION
                        } else {
                            AeatOperationType.REGISTRATION
                        },
                    ).xml
            if (case == "warning") reply = reply.replace(">2000<", ">2007<")
            if (case == "rejection") reply = reply.replace(">3002<", ">1152<")
            if (case == "subsanation") reply = reply.replace("</Operacion>", "<sf:Subsanacion>S</sf:Subsanacion></Operacion>")
            reply
        }
    val lines =
        replies.joinToString("") {
            "<RespuestaLinea>" +
                it.substringAfter("<RespuestaLinea>").substringBefore("</RespuestaLinea>") +
                "</RespuestaLinea>"
        }
    return replies.first().substringBefore("<RespuestaLinea>") + lines + "</RespuestaRegFactuSistemaFacturacion>"
}

internal fun initialize(
    root: Path,
    settings: CliSettings,
): Path {
    val party = root.resolve("party.properties")
    privateFile(party, "nif=89890001K\nname=Synthetic matrix taxpayer\n")
    val producer = root.resolve("producer.properties")
    privateFile(producer, "nif=89890001K\nname=Synthetic matrix producer\n")
    val result =
        cliResult(
            listOf("sandbox", "matrix", "init", "--producer-file", producer.toString(), "--out", root.resolve("run").toString()),
            settings,
        )
    assertEquals(0, result.exitCode, result.diagnostic)
    assertFalse(result.output.contains("89890001K"))
    return root.resolve("run")
}

internal fun createQueryEvidence(
    inputs: MatrixQueryInputs,
    scope: String,
    missingReceived: Boolean = false,
    onePage: Boolean = false,
    mark: Boolean = true,
) {
    val output = inputs.root.resolve("queries/$scope-001")
    val calls = mutableMapOf<QueryDirection, Int>()
    val result =
        fetchQueries(
            mapOf(
                "direction" to "both",
                "keychain" to "true",
                "party-file" to inputs.root.resolve("party.properties").toString(),
                "year" to inputs.period.year,
                "month" to inputs.period.month,
                "out" to output.toString(),
            ),
            { inputs.root.resolve("helper") },
        ) { _, call ->
            val request = Files.readString(call.request)
            val direction = if (request.contains("<sf:ObligadoEmision>")) QueryDirection.ISSUED else QueryDirection.RECEIVED
            val page = calls.getOrDefault(direction, 0) + 1
            calls[direction] = page
            val records =
                when {
                    scope == "small" ->
                        listOf(
                            "R" to "Correcto",
                            "W" to if (missingReceived && direction == QueryDirection.RECEIVED) "Correcto" else "Anulado",
                        )
                    onePage -> (1..10000).map { "P-${it.toString().padStart(5, '0')}" to "Correcto" }
                    page == 1 -> (1..10000).map { "P-${it.toString().padStart(5, '0')}" to "Correcto" }
                    else -> listOf("P-10001" to "Correcto")
                }
            privateFile(
                call.directory.resolve("response.xml"),
                queryResponse(
                    inputs,
                    direction,
                    records,
                    scope == "pagination" && !onePage && page == 1,
                ),
            )
            NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, "a".repeat(64), TEST_QUERY_ENDPOINT)
        }
    assertEquals(0, result.exitCode, result.output)
    if (mark) {
        privateFile(
            output.resolve("matrix-query.properties"),
            "evidence.kind=AEAT_TEST\nscope=$scope\nbase.sha256=${inputs.digest}\nstarted.at=${OffsetDateTime.now()}\n",
        )
    }
}

internal fun queryResponse(
    inputs: MatrixQueryInputs,
    direction: QueryDirection,
    records: List<Pair<String, String>>,
    more: Boolean,
): String {
    val template = MatrixQueryTest::class.java.getResource("/query-fixtures/received-final.xml")!!.readText()
    val element = "RegistroRespuestaConsultaFactuSistemaFacturacion"
    val record = "<r:$element>" + template.substringAfter("<r:$element>").substringBefore("</r:$element>") + "</r:$element>"
    val date = queryProperties(inputs.root.resolve("base.properties")).getValue("invoice.date")
    val rows =
        records.joinToString("") { (suffix, state) ->
            record
                .replace("89890002Q", inputs.party.nif)
                .replace(
                    "SUP-4",
                    inputs.prefix + "-" + suffix,
                ).replace("01-09-2026", date)
                .replace(">Correcto<", ">$state<")
        }
    val cursor =
        if (more) {
            "<r:ClavePaginacion><sf:IDEmisorFactura>${inputs.party.nif}</sf:IDEmisorFactura>" +
                "<sf:NumSerieFactura>${inputs.prefix}-${records.last().first}</sf:NumSerieFactura>" +
                "<sf:FechaExpedicionFactura>$date</sf:FechaExpedicionFactura></r:ClavePaginacion>"
        } else {
            ""
        }
    return template
        .substringBefore("<r:$element>")
        .replace(
            "Destinatario",
            direction.xmlName,
        ).replace(
            "<r:Ejercicio>2026<",
            "<r:Ejercicio>${inputs.period.year}<",
        ).replace("<r:Periodo>09<", "<r:Periodo>${inputs.period.month}<")
        .replace("<r:IndicadorPaginacion>N<", "<r:IndicadorPaginacion>${if (more) "S" else "N"}<") + rows + cursor +
        "</r:RespuestaConsultaFactuSistemaFacturacion>"
}

internal fun matrixWorkspace(action: (Path) -> Unit) {
    val root = Files.createTempDirectory("verifactu-matrix-query-")
    try {
        action(root)
    } finally {
        root.toFile().deleteRecursively()
    }
}
