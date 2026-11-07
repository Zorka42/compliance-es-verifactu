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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatrixQueryTest {
    @Test fun matrixHelpAndLocalCommandsNeverInvokeTransport() =
        matrixWorkspace { root ->
            val settings = CliSettings(taxpayerFile = root.resolve("party.properties"))
            for (action in MATRIX_ACTIONS) {
                val result = cliResult(listOf("sandbox", "matrix", action, "--help"), settings, { error("No network") })
                assertEquals(0, result.exitCode, result.diagnostic)
                assertTrue(result.output.contains("verifactu sandbox matrix $action"))
            }
            assertEquals(0, cliResult(listOf("sandbox", "matrix"), settings).exitCode)
            assertEquals(2, cliResult(listOf("sandbox", "matrix", "unknown"), settings).exitCode)
            val session = initialize(root, settings)
            assertEquals(
                0,
                cliResult(listOf("sandbox", "matrix", "prepare", "--run", session.toString(), "--case", "registration")).exitCode,
            )
            val show = cliResult(listOf("sandbox", "matrix", "show", "--run", session.toString(), "--case", "registration"))
            assertTrue(show.output.contains("RegFactuSistemaFacturacion"))
            val report = cliResult(listOf("sandbox", "matrix", "report", "--run", session.toString()))
            assertEquals(3, report.exitCode)
            assertTrue(report.output.contains("query.small.state=PENDING"))
            assertEquals(
                2,
                cliResult(listOf("sandbox", "matrix", "query", "--run", session.toString(), "--scope", "small"), fetch = {
                    error("Writes are not ready")
                }).exitCode,
            )
            assertEquals(
                2,
                cliResult(
                    listOf(
                        "sandbox",
                        "matrix",
                        "send",
                        "--run",
                        session.toString(),
                        "--case",
                        "registration",
                        "--confirm-sha256",
                        "wrong",
                        "--confirm-records",
                        "1001",
                    ),
                ).exitCode,
            )
        }

    @Test fun realQueryEngineAndReportVerifyBothRolesStatesAndAllPaginationRecords() =
        matrixWorkspace { root ->
            val run = initialize(root, CliSettings(taxpayerFile = root.resolve("party.properties")))
            val inputs = matrixQueryInputs(run)
            for (case in listOf("rejection", "pagination-11")) {
                privateDirectory(run.resolve("cases/$case"))
                privateDirectory(run.resolve("cases/$case/attempt-001"))
                privateFile(run.resolve("cases/$case/attempt-001/matrix-timing.properties"), "completed.at=2026-01-01T00:00:00Z\n")
            }
            createQueryEvidence(inputs, "small")
            assertTrue(verifyMatrixQueries(run).output.contains("query.small.state=PASS"))
            assertEquals(3, verifyMatrixQueries(run).exitCode)
            createQueryEvidence(inputs, "pagination")
            val report = verifyMatrixQueries(run)
            assertEquals(0, report.exitCode, report.output)
            assertFalse(report.output.contains("89890001K"))
            assertFalse(report.output.contains(inputs.prefix))
            val directory = run.resolve("queries/pagination-001")
            val networkFile = directory.resolve("network.log")
            val network = Files.readString(networkFile)
            Files.writeString(networkFile, network.replace("prewww1.aeat.es", "www1.agenciatributaria.gob.es"))
            assertTrue(verifyMatrixQueries(run).output.contains("query.pagination.state=REVIEW"))
            Files.writeString(networkFile, network)
            val list = directory.resolve("invoices.tsv")
            Files.writeString(list, Files.readString(list).replace("Correcto", "Anulado"))
            assertEquals(3, verifyMatrixQueries(run).exitCode)
            val marker = run.resolve("queries/small-001/matrix-query.properties")
            Files.writeString(marker, Files.readString(marker).replace("AEAT_TEST", "OFFLINE_SIMULATION"))
            assertTrue(verifyMatrixQueries(run).output.contains("query.small.state=REVIEW"))
        }

    @Test fun missingReceivedStatesAndSinglePageCannotEstablishCoverage() =
        matrixWorkspace { root ->
            val run = initialize(root, CliSettings(taxpayerFile = root.resolve("party.properties")))
            val inputs = matrixQueryInputs(run)
            for (case in listOf("rejection", "pagination-11")) {
                privateDirectory(run.resolve("cases/$case"))
                privateDirectory(run.resolve("cases/$case/attempt-001"))
                privateFile(run.resolve("cases/$case/attempt-001/matrix-timing.properties"), "completed.at=2026-01-01T00:00:00Z\n")
            }
            createQueryEvidence(inputs, "small", missingReceived = true)
            assertTrue(verifyMatrixQueries(run).output.contains("query.small.state=REVIEW"))
            createQueryEvidence(inputs, "pagination", onePage = true)
            assertTrue(verifyMatrixQueries(run).output.contains("query.pagination.state=REVIEW"))
        }

    @Test
    fun publicMatrixWorkflowReviewsAllBatchesAndQueriesWithoutRealKeychainOrAeat() =
        matrixWorkspace { root ->
            val run = initialize(root, CliSettings(taxpayerFile = root.resolve("party.properties")))
            val inputs = matrixQueryInputs(run)
            val cases =
                listOf("registration", "duplicate", "warning", "subsanation", "cancellation", "rejection") +
                    (1..11).map { "pagination-${it.toString().padStart(2, '0')}" }
            for (case in cases) {
                val prepared = cliResult(listOf("sandbox", "matrix", "prepare", "--run", run.toString(), "--case", case))
                assertEquals(0, prepared.exitCode, prepared.diagnostic)
                val bundle = run.resolve("cases/$case/bundle")
                val fields = queryProperties(bundle.resolve("bundle.properties"))
                val count = fields["batch.count"]?.toInt() ?: 1
                val options =
                    mapOf(
                        "run" to run.toString(),
                        "case" to case,
                        "confirm-sha256" to fields.getValue("request.sha256"),
                        "confirm-records" to count.toString(),
                        "issuer" to "Synthetic issuer",
                    )
                val wrong =
                    cliResult(
                        listOf(
                            "sandbox",
                            "matrix",
                            "send",
                            "--run",
                            run.toString(),
                            "--case",
                            case,
                            "--confirm-sha256",
                            "wrong",
                            "--confirm-records",
                            count.toString(),
                        ),
                    )
                assertEquals(2, wrong.exitCode)
                var calls = 0
                val result =
                    matrixSend(options, { root.resolve("helper") }) { _, call ->
                        calls++
                        assertEquals(count, call.submissionRecordCount)
                        assertEquals(fields.getValue("request.sha256"), call.submissionSha256)
                        privateFile(call.directory.resolve("response.xml"), matrixReply(fields, case))
                        NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, "a".repeat(64), TEST_QUERY_ENDPOINT)
                    }
                assertEquals(1, calls)
                assertEquals(0, result.exitCode, result.diagnostic)
            }
            val before = cliResult(listOf("sandbox", "matrix", "report", "--run", run.toString()))
            assertEquals(3, before.exitCode)
            assertTrue(before.output.contains("write.matrix=PASS"))
            assertTrue(before.output.contains("flow.control=PASS"))
            for (scope in listOf("small", "pagination")) {
                val result =
                    cliResult(
                        listOf(
                            "sandbox",
                            "matrix",
                            "query",
                            "--run",
                            run.toString(),
                            "--scope",
                            scope,
                            "--issuer",
                            "Synthetic issuer",
                        ),
                        fetch = { options ->
                            assertEquals("both", options["direction"])
                            assertEquals("Synthetic issuer", options["issuer"])
                            assertEquals(run.resolve("queries/$scope-001").toString(), options["out"])
                            createQueryEvidence(inputs, scope, mark = false)
                            QueryRunResult("Synthetic HTTP evidence from the actual local query engine.")
                        },
                    )
                assertEquals(0, result.exitCode, result.output + result.diagnostic)
            }
            val complete = cliResult(listOf("sandbox", "matrix", "report", "--run", run.toString()))
            assertEquals(0, complete.exitCode, complete.output + complete.diagnostic)
            assertTrue(complete.output.contains("query.matrix=PASS"))
        }

    private fun matrixReply(
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

    private fun initialize(
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

    private fun createQueryEvidence(
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

    private fun queryResponse(
        inputs: MatrixQueryInputs,
        direction: QueryDirection,
        records: List<Pair<String, String>>,
        more: Boolean,
    ): String {
        val template = javaClass.getResource("/query-fixtures/received-final.xml")!!.readText()
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

    private fun matrixWorkspace(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("verifactu-matrix-query-")
        try {
            action(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
