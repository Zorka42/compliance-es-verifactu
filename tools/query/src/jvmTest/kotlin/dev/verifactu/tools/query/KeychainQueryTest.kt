package dev.verifactu.tools.query

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeychainQueryTest {
    @Test
    fun readsBothDirectionsAndFollowsOnlyCorrelatedPaginationWithOneSelectedIdentity() =
        workspace { root ->
            val responses = listOf(fixture("issued-empty"), fixture("received-page"), fixture("received-final"))
            val calls = mutableListOf<NativeQueryCall>()
            val result =
                fetchQueries(options(root, "both") + ("issuer" to "AC FNMT Usuarios"), { root.resolve("helper") }) { _, call ->
                    calls += call
                    assertTrue(Files.exists(call.directory.resolve("started.properties")))
                    val request = Files.readString(call.request)
                    assertEquals(queryDigest(request), call.sha256)
                    assertEquals(TEST_QUERY_ENDPOINT, call.endpoint)
                    assertEquals("AC FNMT Usuarios", call.issuer)
                    assertFalse(request.contains("<sfLR:RegFactu"))
                    assertEquals(if (calls.size == 1) null else IDENTITY, call.identity)
                    if (calls.size == 3) assertTrue(request.contains("ClavePaginacion"))
                    privateFile(call.directory.resolve("response.xml"), responses[calls.size - 1])
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, IDENTITY)
                }
            assertEquals(0, result.exitCode)
            assertEquals(3, calls.size)
            assertTrue(result.output.contains("ISSUED: COMPLETE, records=0"))
            assertTrue(result.output.contains("RECEIVED: COMPLETE, records=4"))
            assertFalse(result.output.contains("89890001K"))
            val rows = Files.readString(root.resolve("out/invoices.tsv"))
            assertTrue(rows.contains("SUP-A&B"))
            assertTrue(rows.contains("121.00"))
            assertEquals(5, rows.lines().filter { it.isNotEmpty() }.size)
            assertEquals("COMPLETE", queryProperties(root.resolve("out/outcome.properties"))["state"])
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(root.resolve("out/invoices.tsv")))
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root.resolve("out")))
            assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                fetchQueries(options(root, "both"), { root.resolve("helper") }) { _, _ -> error("No overwrite") }
            }
        }

    @Test
    fun rejectsInvalidOptionsIdentityAndPeriodBeforeHelperOrOutputCreation() =
        workspace { root ->
            val invalid =
                listOf(
                    options(root) - "keychain",
                    options(root) + ("endpoint" to "https://production.invalid"),
                    options(root) + ("direction" to "other"),
                    options(root) + ("month" to "13"),
                    options(root) + ("year" to "bad"),
                    options(root) - "out",
                    options(root) + ("issuer" to " "),
                    options(root) + ("issuer" to "CA\nName"),
                    options(root) + ("issuer" to "é".repeat(513)),
                )
            invalid.forEach { args -> assertFailsWith<QueryInputException> { fetchQueries(args, { error("No helper access") }) } }
            assertFalse(Files.exists(root.resolve("out")))
            Files.writeString(root.resolve("party.properties"), "nif=89890001K\nname=\n")
            assertFailsWith<QueryInputException> { fetchQueries(options(root), { error("No helper") }) }
            Files.writeString(root.resolve("party.properties"), "nif=89890001K\nname=Synthetic\npassword=PRIVATE\n")
            assertFailsWith<QueryInputException> { fetchQueries(options(root), { error("No helper") }) }
            assertFailsWith<QueryInputException> { parseOptions(listOf("--month", "--year")) }
            assertTrue(queryCommand(listOf("--help")).output.contains("fetch --keychain"))
        }

    @Test
    fun failedOrCancelledConsultationIsIncompleteRatherThanAnEmptyInvoiceList() =
        workspace { root ->
            listOf(
                "SELECTION_CANCELLED",
                "IDENTITY_UNAVAILABLE",
                "UNKNOWN_DELIVERY",
                "NON_XML_RESPONSE",
                "RESPONSE_TOO_LARGE",
            ).forEachIndexed {
                index,
                kind,
                ->
                var count = 0
                val out = "failed-$index"
                val result =
                    fetchQueries(options(root) + ("out" to root.resolve(out).toString()), { root.resolve("helper") }) { _, _ ->
                        count++
                        NativeQueryReceipt(kind, if (index < 2) "NOT_SENT" else "UNKNOWN", null, null)
                    }
                assertEquals(4, result.exitCode)
                assertEquals(1, count)
                assertFalse(result.output.contains("COMPLETE, records=0"))
                assertEquals("INCOMPLETE", queryProperties(root.resolve("$out/outcome.properties"))["state"])
            }
        }

    @Test
    fun mismatchedOrInvalidResponsesAndNonSuccessHttpNeverProduceACompleteRead() =
        workspace { root ->
            val xml = fixture("issued-empty")
            val responses =
                listOf(
                    xml.replace("89890001K", "89890002Q") to 200,
                    xml.replace("<r:Periodo>09</r:Periodo>", "<r:Periodo>10</r:Periodo>") to 200,
                    fixture("received-final") to 200,
                    "<invalid/>" to 200,
                    "<!DOCTYPE x SYSTEM 'file:///never-read'><x/>" to 200,
                    "<s:Fault xmlns:s='$SOAP_NAMESPACE'><faultcode>s:Server</faultcode><faultstring>PRIVATE</faultstring></s:Fault>" to 200,
                    xml to 500,
                )
            responses.forEachIndexed { index, (response, status) ->
                val result =
                    fetchQueries(
                        options(root, "issued") + ("out" to root.resolve("invalid-$index").toString()),
                        { root.resolve("helper") },
                    ) { _, call ->
                        privateFile(call.directory.resolve("response.xml"), response)
                        NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", status, IDENTITY)
                    }
                assertEquals(3, result.exitCode, "case $index")
                assertFalse(result.output.contains("PRIVATE"))
                assertFalse(result.output.contains("ISSUED: COMPLETE"))
            }
        }

    @Test
    fun stopsOnRepeatedCursorsChangedIdentityAndUnexpectedTransportFailure() =
        workspace { root ->
            var calls = 0
            val repeated =
                fetchQueries(options(root), { root.resolve("helper") }) { _, call ->
                    calls++
                    privateFile(call.directory.resolve("response.xml"), fixture("received-page"))
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, IDENTITY)
                }
            assertEquals(3, repeated.exitCode)
            assertEquals(2, calls)
            assertTrue(repeated.output.contains("repeated pagination"))
            calls = 0
            val changed =
                fetchQueries(options(root) + ("out" to root.resolve("changed").toString()), { root.resolve("helper") }) { _, call ->
                    calls++
                    privateFile(call.directory.resolve("response.xml"), fixture(if (calls == 1) "received-page" else "received-final"))
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, if (calls == 1) IDENTITY else "b".repeat(64))
                }
            assertEquals(4, changed.exitCode)
            assertEquals(2, calls)
            val failed =
                fetchQueries(
                    options(root) + ("out" to root.resolve("thrown").toString()),
                    { root.resolve("helper") },
                ) { _, _ -> error("PRIVATE") }
            assertEquals(4, failed.exitCode)
            assertFalse(failed.output.contains("PRIVATE"))
        }

    @Test
    fun responseSaveFailurePreservesStartedEvidenceAndDoesNotRetry() =
        workspace { root ->
            var calls = 0
            val result =
                fetchQueries(options(root), { root.resolve("helper") }) { _, _ ->
                    calls++
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, IDENTITY)
                }
            assertEquals(4, result.exitCode)
            assertEquals(1, calls)
            assertTrue(Files.exists(root.resolve("out/received-1/started.properties")))
            assertFalse(Files.exists(root.resolve("out/outcome.properties")))
            assertTrue(queryField("a\t\n\r\\\u202E\u2067\u3000").contains("\\u2067\u3000"))
        }

    private fun options(
        root: Path,
        direction: String = "received",
    ): Map<String, String> =
        mapOf(
            "keychain" to "true",
            "direction" to direction,
            "party-file" to root.resolve("party.properties").toString(),
            "year" to "2026",
            "month" to "09",
            "out" to root.resolve("out").toString(),
        )

    private fun fixture(name: String): String = javaClass.getResource("/query-fixtures/$name.xml")!!.readText()

    private fun workspace(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("verifactu-query-read-")
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
