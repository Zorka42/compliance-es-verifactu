package dev.verifactu.tools.query

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MatrixQueryEvidenceTest {
    @Test fun modifiedHeadersTimingReceiptsAndPagesRequireReview() =
        matrixWorkspace { root ->
            val run = initialize(root, CliSettings(taxpayerFile = root.resolve("party.properties")))
            val inputs = matrixQueryInputs(run)
            privateDirectory(run.resolve("cases/rejection"))
            privateDirectory(run.resolve("cases/rejection/attempt-001"))
            privateFile(run.resolve("cases/rejection/attempt-001/matrix-timing.properties"), "completed.at=2026-01-01T00:00:00Z\n")
            createQueryEvidence(inputs, "small")
            val directory = run.resolve("queries/small-001")
            val mutants =
                listOf(
                    Triple("outcome.properties", "state=COMPLETE", "state=INCOMPLETE"),
                    Triple("outcome.properties", "exit.code=0", "exit.code=4"),
                    Triple("outcome.properties", "pages=2", "pages=unknown"),
                    Triple("outcome.properties", "pages=2", "pages=1"),
                    Triple("outcome.properties", "pages=2", "pages=101"),
                    Triple("outcome.properties", "pages=2\n", ""),
                    Triple("network.log", "environment=TEST", "environment=PRODUCTION"),
                    Triple("network.log", "operation=ConsultaFactuSistemaFacturacion", "operation=RegFactuSistemaFacturacion"),
                    Triple("network.log", "http.method=POST", "http.method=GET"),
                    Triple("network.log", "redirect.policy=REFUSE", "redirect.policy=FOLLOW"),
                    Triple("network.log", "tls.server.trust.policy=SYSTEM_DEFAULT", "tls.server.trust.policy=INSECURE"),
                    Triple("network.log", "page.1.direction=ISSUED", "page.1.direction=unknown"),
                    Triple("network.log", "page.1.direction=ISSUED", "page.1.direction=RECEIVED"),
                    Triple("network.log", "page.2.direction=RECEIVED", "page.2.direction=ISSUED"),
                    Triple("network.log", "page.1.http.status=200", "page.1.http.status=403"),
                    Triple("network.log", "page.1.http.status=200\n", ""),
                    Triple("network.log", "page.1.kind=XML_RESPONSE", "page.1.kind=NON_XML_RESPONSE"),
                    Triple("network.log", "page.1.delivery=RESPONSE_RECEIVED", "page.1.delivery=UNKNOWN"),
                    Triple("network.log", "page.1.response.url=https", "page.1.response.url=http"),
                    Triple("matrix-query.properties", "scope=small", "scope=pagination"),
                    Triple("matrix-query.properties", "base.sha256=", "base.sha256=wrong"),
                    Triple("matrix-query.properties", "started.at=", "started.at=bad"),
                    Triple("issued-1/request.xml", "ConsultaFactuSistemaFacturacion", "Changed"),
                    Triple("issued-1/started.properties", "request.sha256=", "request.sha256=wrong"),
                    Triple("issued-1/response.xml", "ObligadoEmision", "Destinatario"),
                    Triple("issued-1/response.xml", "<sf:NIF>89890001K", "<sf:NIF>89890002Q"),
                    Triple(
                        "issued-1/response.xml",
                        "<r:Periodo>${inputs.period.month}<",
                        "<r:Periodo>${if (inputs.period.month == "01") "02" else "01"}<",
                    ),
                )
            for ((name, before, after) in mutants) {
                val file = directory.resolve(name)
                val original = Files.readString(file)
                assertTrue(before in original, name)
                try {
                    Files.writeString(file, original.replace(before, after))
                    assertTrue(verifyMatrixQueries(run).output.contains("query.small.state=REVIEW"), name)
                } finally {
                    Files.writeString(file, original)
                }
            }
            val responseFile = directory.resolve("issued-1/response.xml")
            val permissions = Files.getPosixFilePermissions(responseFile)
            try {
                Files.setPosixFilePermissions(responseFile, emptySet())
                assertTrue(verifyMatrixQueries(run).output.contains("query.small.state=REVIEW"))
            } finally {
                Files.setPosixFilePermissions(responseFile, permissions)
            }
            val marker = directory.resolve("matrix-query.properties")
            Files.writeString(marker, Files.readString(marker).substringBefore("started.at=") + "started.at=2025-01-01T00:00:00Z\n")
            assertTrue(verifyMatrixQueries(run).output.contains("query.small.state=REVIEW"))
        }

    @Test fun missingAndUnexpectedFiscalRowsCannotSubstituteForExpectedInvoices() =
        matrixWorkspace { root ->
            val run = initialize(root, CliSettings(taxpayerFile = root.resolve("party.properties")))
            val inputs = matrixQueryInputs(run)
            privateDirectory(run.resolve("cases/rejection"))
            privateDirectory(run.resolve("cases/rejection/attempt-001"))
            privateFile(run.resolve("cases/rejection/attempt-001/matrix-timing.properties"), "completed.at=2026-01-01T00:00:00Z\n")
            createQueryEvidence(inputs, "small")
            val directory = run.resolve("queries/small-001")
            for ((old, replacement) in listOf(
                inputs.prefix + "-R" to "UNRELATED-R",
                inputs.prefix + "-W" to "UNRELATED-W",
                "Correcto" to "AceptadoConErrores",
                "Anulado" to "Correcto",
                queryProperties(run.resolve("base.properties")).getValue("invoice.date") to "01-01-2025",
            )) {
                val files =
                    listOf(
                        directory.resolve("issued-1/response.xml"),
                        directory.resolve("received-2/response.xml"),
                        directory.resolve("invoices.tsv"),
                    )
                val saved = files.associateWith(Files::readString)
                try {
                    saved.forEach { (file, text) -> Files.writeString(file, text.replace(old, replacement)) }
                    assertTrue(verifyMatrixQueries(run).output.contains("query.small.state=REVIEW"), old)
                } finally {
                    saved.forEach { (file, text) -> Files.writeString(file, text) }
                }
            }
            val files =
                listOf(
                    directory.resolve("issued-1/response.xml"),
                    directory.resolve("received-2/response.xml"),
                    directory.resolve("invoices.tsv"),
                )
            val saved = files.associateWith(Files::readString)
            saved.forEach { (file, text) ->
                Files.writeString(file, text.replace("<r:ImporteTotal>24.20</r:ImporteTotal>", "").replace("\t24.20\n", "\t\n"))
            }
            assertTrue(verifyMatrixQueries(run).output.contains("query.small.state=PASS"))
        }

    @Test fun malformedSessionAndIncompleteInputsFailLocally() =
        matrixWorkspace { root ->
            val settings = CliSettings(taxpayerFile = root.resolve("party.properties"))
            val run = initialize(root, settings)
            assertEquals(2, cliResult(listOf("sandbox", "matrix", "report", "--run", root.resolve("absent").toString())).exitCode)
            assertEquals(2, cliResult(listOf("sandbox", "matrix", "one", "two", "--help")).exitCode)
            val initialized =
                cliResult(
                    listOf(
                        "sandbox",
                        "matrix",
                        "init",
                        "--party-file",
                        settings.taxpayerFile.toString(),
                        "--producer-file",
                        root.resolve("producer.properties").toString(),
                        "--out",
                        root.resolve("second").toString(),
                    ),
                    settings,
                )
            assertEquals(0, initialized.exitCode)
            for ((file, old, replacement) in listOf(
                Triple("matrix.properties", "environment=TEST", "environment=PRODUCTION"),
                Triple("matrix.properties", "period=", "period=bad"),
                Triple("matrix.properties", "period=${matrixQueryInputs(run).period.year}-${matrixQueryInputs(run).period.month}\n", ""),
                Triple("matrix.properties", "invoice.prefix=${matrixQueryInputs(run).prefix}\n", ""),
                Triple("matrix.properties", "invoice.prefix=VF111-", "invoice.prefix=bad"),
            )) {
                val path = run.resolve(file)
                val original = Files.readString(path)
                try {
                    Files.writeString(path, original.replace(old, replacement))
                    assertFailsWith<QueryInputException> { matrixQueryInputs(run) }
                } finally {
                    Files.writeString(path, original)
                }
            }
            assertFailsWith<QueryInputException> {
                queryMatrix(mapOf("run" to root.resolve("missing").toString(), "scope" to "small")) { error("No network") }
            }
        }
}
