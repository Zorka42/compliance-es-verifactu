package dev.verifactu.tools.query

import java.nio.file.Files
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
            assertEquals(
                2,
                cliResult(
                    listOf(
                        "sandbox",
                        "matrix",
                        "send",
                        "--run",
                        root.toString(),
                        "--case",
                        "registration",
                        "--confirm-sha256",
                        "bad",
                        "--confirm-records",
                        "bad",
                    ),
                ).exitCode,
            )
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
            val pageFiles =
                listOf(
                    directory.resolve("issued-1/response.xml"),
                    directory.resolve("issued-2/response.xml"),
                    directory.resolve("received-3/response.xml"),
                    directory.resolve("received-4/response.xml"),
                    directory.resolve("invoices.tsv"),
                )
            val originals = pageFiles.associateWith(Files::readString)
            val date = queryProperties(run.resolve("base.properties")).getValue("invoice.date")
            for ((old, replacement) in listOf(
                "Correcto" to "Anulado",
                date to "01-01-2025",
                inputs.prefix + "-P-10001" to inputs.prefix + "-P-10000",
            )) {
                try {
                    originals.forEach { (file, text) -> Files.writeString(file, text.replace(old, replacement)) }
                    assertTrue(verifyMatrixQueries(run).output.contains("query.pagination.state=REVIEW"))
                } finally {
                    originals.forEach { (file, text) -> Files.writeString(file, text) }
                }
            }
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
            val failed =
                cliResult(listOf("sandbox", "matrix", "query", "--run", run.toString(), "--scope", "small"), fetch = {
                    QueryRunResult("Synthetic not-started fetch", 4)
                })
            assertEquals(4, failed.exitCode)
            val missing =
                cliResult(listOf("sandbox", "matrix", "query", "--run", run.toString(), "--scope", "small"), fetch = {
                    QueryRunResult("Synthetic missing evidence")
                })
            assertEquals(3, missing.exitCode)
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
            for (index in 2..99) privateDirectory(run.resolve("queries/small-${index.toString().padStart(3, '0')}"))
            val limited =
                cliResult(listOf("sandbox", "matrix", "query", "--run", run.toString(), "--scope", "small"), fetch = {
                    error("No request at the evidence limit")
                })
            assertEquals(2, limited.exitCode)
        }
}
