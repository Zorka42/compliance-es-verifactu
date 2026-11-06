package dev.verifactu.tools.query

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryBoundaryTest {
    @Test
    fun nativeInstallationRequiresMacJarAndExecutableSiblingHelper() =
        workspace { root ->
            val jar = root.resolve("lib/query.jar")
            Files.createDirectories(jar.parent)
            Files.createFile(jar)
            assertFailsWith<QueryInputException> { locateNativeHelper("Linux", jar) }
            assertFailsWith<QueryInputException> { locateNativeHelper("Mac OS X", root) }
            assertFailsWith<QueryInputException> { locateNativeHelper("Mac OS X", jar) }
            val helper = root.resolve("bin/verifactu-query-keychain")
            Files.createDirectories(helper.parent)
            Files.createFile(helper)
            Files.setPosixFilePermissions(helper, PosixFilePermissions.fromString("rw-------"))
            assertFailsWith<QueryInputException> { locateNativeHelper("Mac OS X", jar) }
            Files.setPosixFilePermissions(helper, PosixFilePermissions.fromString("rwx------"))
            assertEquals(helper, locateNativeHelper("Mac OS X", jar))
        }

    @Test
    fun renderedResultsKeepRowsAndDiagnosticsOnSeparateStreamsIncludingErrors() {
        val output = java.io.ByteArrayOutputStream()
        val error = java.io.ByteArrayOutputStream()
        val stdout = java.io.PrintStream(output)
        val stderr = java.io.PrintStream(error)
        listOf(CliResult(), CliResult("rows", "partial", 3), CliResult(diagnostic = "invalid", exitCode = 2)).forEach { result ->
            output.reset()
            error.reset()
            assertEquals(result.exitCode, printCliResult(result, stdout, stderr))
            assertEquals(result.output, output.toString().trimEnd())
            assertEquals(result.diagnostic, error.toString().trimEnd())
        }
        assertFailsWith<QueryInputException> { cliOptions(listOf("--")) }
        assertEquals(null, CliSettings().prompt("Ignored without an interactive console: "))
    }

    @Test
    fun malformedLegacyFetchFlagsNeverReachCertificateSelection() {
        assertFailsWith<QueryInputException> { queryCommand(listOf("fetch")) }
        listOf(listOf("unexpected", "x"), listOf("--"), listOf("--year", "2026", "--year", "2026"), listOf("--year")).forEach { args ->
            assertFailsWith<QueryInputException> { parseOptions(args) }
        }
    }

    @Test
    fun consultationStopsAtTheRealPageLimitAndRetainsPartialEvidence() =
        workspace { root ->
            var calls = 0
            val result =
                fetchQueries(options(root), { root.resolve("helper") }) { _, call ->
                    calls++
                    privateFile(call.directory.resolve("response.xml"), fixture().replace("SUP-3", "PAGE-$calls"))
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, IDENTITY)
                }
            assertEquals(100, calls)
            assertEquals(3, result.exitCode)
            assertTrue(result.output.contains("page limit"))
            assertEquals("INCOMPLETE", queryProperties(root.resolve("out/outcome.properties"))["state"])
            assertEquals(301, Files.readAllLines(root.resolve("out/invoices.tsv")).size)
        }

    @Test
    fun consultationStopsAtTheRealRecordLimitRatherThanReportingTruncatedSuccess() =
        workspace { root ->
            val original = fixture()
            val pattern =
                Regex(
                    "<r:RegistroRespuestaConsultaFactuSistemaFacturacion>.*?</r:RegistroRespuestaConsultaFactuSistemaFacturacion>",
                    RegexOption.DOT_MATCHES_ALL,
                )
            val record = pattern.find(original)!!.value
            val allRecords = pattern.replace(original, "")
            val page = allRecords.replace("<r:ClavePaginacion>", record.repeat(10_000) + "<r:ClavePaginacion>")
            var calls = 0
            val result =
                fetchQueries(options(root), { root.resolve("helper") }) { _, call ->
                    calls++
                    privateFile(call.directory.resolve("response.xml"), page.replace("SUP-3", "PAGE-$calls"))
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, IDENTITY)
                }
            assertEquals(11, calls)
            assertEquals(3, result.exitCode)
            assertTrue(result.output.contains("record limit"))
            assertEquals(100_001, Files.readAllLines(root.resolve("out/invoices.tsv")).size)
        }

    @Test
    fun invalidIdentityOrMissingHttpStatusCannotProduceACompleteConsultation() =
        workspace { root ->
            listOf(null, "invalid", IDENTITY).forEachIndexed { index, identity ->
                val result =
                    fetchQueries(
                        options(root) + ("out" to root.resolve("invalid-$index").toString()),
                        { root.resolve("helper") },
                    ) { _, call ->
                        privateFile(call.directory.resolve("response.xml"), fixture())
                        NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", null, identity)
                    }
                assertTrue(result.exitCode != 0)
                assertFalse(result.output.contains("COMPLETE, records="))
            }
        }

    @Test
    fun artifactWriteFailureAfterTransportIsAnUncertainFailureWithStartEvidence() =
        workspace { root ->
            val result =
                fetchQueries(options(root), { root.resolve("helper") }) { _, call ->
                    Files.createDirectory(call.directory.resolve("delivery.properties"))
                    NativeQueryReceipt("UNKNOWN_DELIVERY", "UNKNOWN", null, null)
                }
            assertEquals(4, result.exitCode)
            assertTrue(result.output.contains("Cannot persist"))
            assertTrue(Files.exists(root.resolve("out/received-1/started.properties")))
        }

    @Test
    fun unreadableInvoiceFileAfterFetchReturnsFailureAndKeepsEvidence() =
        workspace { root ->
            val settings = CliSettings(root.resolve("party.properties"), "2026-09") { null }
            val output = root.resolve("unreadable")
            val file = output.resolve("invoices.tsv")
            try {
                val result =
                    cliResult(listOf("sandbox", "invoices", "list", "--out", output.toString()), settings, {
                        privateDirectory(output)
                        privateFile(file, "private")
                        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"))
                        QueryRunResult("COMPLETE")
                    })
                assertEquals(4, result.exitCode)
                assertTrue(result.diagnostic.contains("Preserve evidence"))
            } finally {
                if (Files.exists(file)) Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
            }
        }

    private fun options(root: Path): Map<String, String> =
        mapOf(
            "keychain" to "true",
            "direction" to "received",
            "party-file" to root.resolve("party.properties").toString(),
            "year" to "2026",
            "month" to "09",
            "out" to root.resolve("out").toString(),
        )

    private fun fixture(): String = javaClass.getResource("/query-fixtures/received-page.xml")!!.readText()

    private fun workspace(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("verifactu-query-boundary-")
        try {
            Files.writeString(root.resolve("party.properties"), "nif=89890001K\nname=Synthetic taxpayer\n")
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val IDENTITY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
