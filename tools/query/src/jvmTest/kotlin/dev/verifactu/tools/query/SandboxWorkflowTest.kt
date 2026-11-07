package dev.verifactu.tools.query

import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario
import dev.verifactu.testkit.VerifactuFixtures
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxWorkflowTest {
    @Test
    fun localCommandsPrepareReviewSimulateAndInspectWithoutSelectingCredentials() =
        workspace { root ->
            prepare(root, "offline")
            assertTrue(command("scenarios").output.contains("cancellation"))
            val shown = command("show", "--bundle", root.resolve("bundle").toString())
            assertEquals(0, shown.exitCode)
            assertTrue(shown.output.contains("RegFactuSistemaFacturacion"))
            assertTrue(shown.output.contains("Mode: offline"))
            val demo = command("demo", "--bundle", root.resolve("bundle").toString(), "--response=timeout")
            assertEquals(0, demo.exitCode)
            assertTrue(demo.output.contains("Network calls: 0"))
            val response = root.resolve("response.xml")
            Files.writeString(response, accepted())
            val args = arrayOf("inspect", "--bundle", root.resolve("bundle").toString(), "--input", response.toString())
            assertTrue(command(*args).output.contains("Result: ACCEPTED"))
            assertFalse(command(*args).output.contains("SANDBOX-001"))
            assertTrue(command(*args, "--details").output.contains("SANDBOX-001"))
            assertEquals(2, command(*args, "--details=false").exitCode)
            assertEquals(2, command("show", "--endpoint=PRIVATE").exitCode)
            val missing = command("show", "--bundle", root.resolve("missing").toString())
            assertEquals(2, missing.exitCode)
            assertTrue(missing.output.isEmpty())
            assertTrue(missing.diagnostic.isNotEmpty())
        }

    @Test
    fun keychainSendPersistsOneReviewedAttemptAndSafeNetworkEvidence() =
        workspace { root ->
            prepare(root, "aeat-test")
            val options = sendOptions(root) + ("confirm-records" to "1")
            var calls = 0
            val result =
                sendSandbox(options, { root.resolve("helper") }) { _, call ->
                    calls++
                    assertTrue(Files.exists(root.resolve("attempt/started.properties")))
                    assertEquals(call.sha256, call.submissionSha256)
                    assertEquals(TEST_QUERY_ENDPOINT, call.endpoint)
                    assertEquals("AC FNMT Usuarios", call.issuer)
                    assertTrue(Files.readString(call.request).contains("RegFactuSistemaFacturacion"))
                    privateFile(call.directory.resolve("response.xml"), accepted())
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, IDENTITY, call.endpoint)
                }
            assertEquals(0, result.exitCode)
            assertEquals(1, calls)
            assertTrue(result.diagnostic.contains("Result: ACCEPTED"))
            assertEquals("RESPONSE_RECEIVED", queryProperties(root.resolve("attempt/outcome.properties"))["delivery"])
            val network = Files.readString(root.resolve("attempt/network.log"))
            assertTrue(network.contains("operation=RegFactuSistemaFacturacion"))
            assertTrue(network.contains("response.url=$TEST_QUERY_ENDPOINT"))
            for (privateValue in listOf(IDENTITY, "89890001K", "SANDBOX-001", "<")) assertFalse(network.contains(privateValue))
            assertEquals(2, sendSandbox(options, { error("No overwrite") }).exitCode)
        }

    @Test
    fun offlineWrongDigestAndModifiedBundlesFailBeforeNativeAccess() =
        workspace { root ->
            prepare(root, "offline")
            assertEquals(2, sendSandbox(sendOptions(root), { error("No helper") }).exitCode)
            root.resolve("bundle").toFile().deleteRecursively()
            prepare(root, "aeat-test")
            val options = sendOptions(root)
            for (count in listOf("bad", "0", "1001")) {
                val args =
                    listOf("sandbox", "send") + (options + ("confirm-records" to count)).flatMap { (key, value) -> listOf("--$key", value) }
                assertEquals(2, cliResult(args).exitCode)
            }
            assertEquals(2, sendSandbox(options + ("confirm-sha256" to "0".repeat(64)), { error("No helper") }).exitCode)
            Files.writeString(root.resolve("bundle/request.xml"), "modified")
            assertEquals(2, sendSandbox(options, { error("No helper") }).exitCode)
            assertFalse(Files.exists(root.resolve("attempt")))
            val base = listOf("sandbox", "send")
            for (flags in listOf(emptyList(), listOf("--confirm-sha256=bad"), listOf("--endpoint=PRIVATE"))) {
                assertEquals(2, cliResult(base + flags).exitCode)
            }
        }

    @Test
    fun nativeFailuresNeverRetryOrEstablishAcceptanceAndPreserveTheirEvidence() =
        workspace { root ->
            prepare(root, "aeat-test")
            val receipts =
                listOf(
                    NativeQueryReceipt("SELECTION_CANCELLED", "NOT_SENT", null, null),
                    NativeQueryReceipt("UNKNOWN_DELIVERY", "UNKNOWN", null, null),
                    NativeQueryReceipt("NON_XML_RESPONSE", "RESPONSE_RECEIVED", 403, IDENTITY, TEST_QUERY_ENDPOINT),
                    NativeQueryReceipt("RESPONSE_TOO_LARGE", "RESPONSE_RECEIVED", 200, IDENTITY, TEST_QUERY_ENDPOINT),
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, null, TEST_QUERY_ENDPOINT),
                    NativeQueryReceipt("XML_RESPONSE", "RESPONSE_RECEIVED", 200, "invalid", "https://unexpected.invalid/PRIVATE"),
                )
            receipts.forEachIndexed { index, receipt ->
                var calls = 0
                val options = sendOptions(root) + ("out" to root.resolve("failure-$index").toString())
                val result =
                    sendSandbox(options, { root.resolve("helper") }) { _, _ ->
                        calls++
                        receipt
                    }
                assertEquals(1, calls)
                assertEquals(4, result.exitCode)
                assertFalse(result.diagnostic.contains("Result: ACCEPTED"))
                assertTrue(Files.exists(root.resolve("failure-$index/network.log")))
            }
            val result = sendSandbox(sendOptions(root), { throw java.io.IOException("PRIVATE") }) { _, _ -> error("No retry") }
            assertEquals(4, result.exitCode)
            assertFalse(result.diagnostic.contains("PRIVATE"))
            assertEquals("NOT_SENT", queryProperties(root.resolve("attempt/outcome.properties"))["delivery"])
            assertTrue(Files.readString(root.resolve("attempt/network.log")).contains("kind=HELPER_NOT_STARTED"))
        }

    private fun prepare(
        root: Path,
        mode: String,
    ) {
        val config = root.resolve("inputs.properties")
        Files.writeString(config, javaClass.getResource("/offline.properties")!!.readText())
        val result = command("prepare", "--scenario=registration", "--config=$config", "--mode=$mode", "--out=${root.resolve("bundle")}")
        assertEquals(0, result.exitCode, result.diagnostic)
    }

    private fun sendOptions(root: Path): Map<String, String> =
        mapOf(
            "bundle" to root.resolve("bundle").toString(),
            "out" to root.resolve("attempt").toString(),
            "issuer" to "AC FNMT Usuarios",
            "confirm-sha256" to queryDigest(Files.readString(root.resolve("bundle/request.xml"))),
        )

    private fun accepted(): String =
        AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, VerifactuFixtures.invoice("SANDBOX-001")).xml

    private fun command(vararg args: String): CliResult = cliResult(listOf("sandbox") + args)

    private fun workspace(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("verifactu-workflow-")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val IDENTITY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
