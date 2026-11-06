package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatEnvironment
import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxSendTest {
    @Test
    fun persistsFailureEvidenceAndHttpStatusWithoutSavingNonXmlOrRetrying() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root, "aeat-test")
            val results =
                listOf(
                    AeatTransportResult.NotSent("PRIVATE") to "unavailable",
                    AeatTransportResult.Timeout("PRIVATE") to "unavailable",
                    AeatTransportResult.NonXmlResponse(403, "PRIVATE-MEDIA") to "403",
                    AeatTransportResult.ResponseTooLarge(200, "PRIVATE-MEDIA", 1024) to "200",
                    AeatTransportResult.XmlResponse(200, "text/xml", "") to "200",
                    simulatedResponse(bundle, "soap-fault") to "500",
                )
            results.forEachIndexed { index, (response, expectedHttpStatus) ->
                val output = "failure-$index"
                var calls = 0
                val result =
                    runCommand(sendArguments(root, bundle.sha256, output), { "DISPOSABLE" }) { _, _, _ ->
                        object : AeatTransportAdapter {
                            override fun execute(request: AeatTransportRequest): AeatTransportResult {
                                calls++
                                assertTrue(Files.exists(root.resolve("$output/started.properties")))
                                return response
                            }
                        }
                    }
                assertEquals(1, calls)
                assertEquals(if (response is AeatTransportResult.XmlResponse) 3 else 4, result.exitCode)
                assertFalse(result.output.contains("PRIVATE"))
                val receipt = readProperties(root.resolve("$output/outcome.properties"))
                assertEquals(response.deliveryState.name, receipt["delivery"])
                assertEquals(expectedHttpStatus, receipt["http.status"])
                assertEquals(response is AeatTransportResult.XmlResponse, Files.exists(root.resolve("$output/response.xml")))
            }
        }

    @Test
    fun preflightRejectsOfflineUnreviewedOrModifiedInputsBeforeCredentialAccess() =
        withWorkspace { root ->
            var reads = 0
            val environment: (String) -> String? = {
                reads++
                "PRIVATE"
            }
            val transport: (Path, String, CharArray) -> AeatTransportAdapter = { _, _, _ -> error("No credential access expected") }
            val offline = fixtureBundle(root)
            assertFailsWith<SandboxInputException> { runCommand(sendArguments(root, offline.sha256), environment, transport) }
            root.resolve("bundle").toFile().deleteRecursively()
            val live = fixtureBundle(root, "aeat-test")
            assertFailsWith<SandboxInputException> { runCommand(sendArguments(root, "0".repeat(64)), environment, transport) }
            Files.writeString(root.resolve("bundle/request.xml"), live.plan.soap + " ")
            assertFailsWith<SandboxInputException> { runCommand(sendArguments(root, live.sha256), environment, transport) }
            assertEquals(0, reads)
            assertFalse(Files.exists(root.resolve("attempt")))
        }

    @Test
    fun sendsExactlyOnceWithPersistedStartEvidenceAndClearedPassword() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root, "aeat-test")
            var calls = 0
            var heldPassword: CharArray? = null
            val factory: (Path, String, CharArray) -> AeatTransportAdapter = { path, alias, password ->
                assertEquals(root.resolve("selected.p12"), path)
                assertEquals("selected-client", alias)
                assertEquals("DISPOSABLE-PASSWORD", password.concatToString())
                heldPassword = password
                object : AeatTransportAdapter {
                    override fun execute(request: AeatTransportRequest): AeatTransportResult {
                        calls++
                        assertTrue(password.all { it == '\u0000' })
                        assertEquals(AeatEnvironment.TEST, request.endpoint.environment)
                        assertTrue(request.endpoint.url.startsWith("https://prewww1.aeat.es/"))
                        assertEquals(bundle.plan.soap, request.xmlPayload)
                        assertEquals("STARTED", readProperties(root.resolve("attempt/started.properties"))["state"])
                        assertEquals(bundle.sha256, requestDigest(Files.readString(root.resolve("attempt/request.xml"))))
                        return simulatedResponse(bundle, "accepted")
                    }
                }
            }
            val result = runCommand(sendArguments(root, bundle.sha256), { "DISPOSABLE-PASSWORD" }, factory)
            assertEquals(0, result.exitCode)
            assertEquals(1, calls)
            assertTrue(heldPassword!!.all { it == '\u0000' })
            assertTrue(Files.exists(root.resolve("attempt/response.xml")))
            assertEquals("RESPONSE_RECEIVED", readProperties(root.resolve("attempt/outcome.properties"))["delivery"])
            assertEquals("200", readProperties(root.resolve("attempt/outcome.properties"))["http.status"])
            assertEquals("XmlResponse", readProperties(root.resolve("attempt/outcome.properties"))["transport.result"])
            assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                runCommand(sendArguments(root, bundle.sha256), { "DISPOSABLE-PASSWORD" }, factory)
            }
            assertEquals(1, calls)
        }

    @Test
    fun preservesUnknownDeliveryWithoutAutomaticRetryOrSuccessfulReceipt() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root, "aeat-test")
            var calls = 0
            val result =
                runCommand(sendArguments(root, bundle.sha256), { "DISPOSABLE" }) { _, _, _ ->
                    object : AeatTransportAdapter {
                        override fun execute(request: AeatTransportRequest): AeatTransportResult {
                            calls++
                            error("PRIVATE-EXCEPTION")
                        }
                    }
                }
            assertEquals(4, result.exitCode)
            assertEquals(1, calls)
            assertTrue(result.output.contains("UNKNOWN_DELIVERY"))
            assertFalse(result.output.contains("PRIVATE-EXCEPTION"))
            assertEquals("UNKNOWN", readProperties(root.resolve("attempt/outcome.properties"))["delivery"])
            assertFalse(Files.exists(root.resolve("attempt/response.xml")))
        }

    @Test
    fun postSendPersistenceFailureKeepsStartedEvidenceAndNeverOverwritesExistingFiles() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root, "aeat-test")
            val result =
                runCommand(sendArguments(root, bundle.sha256), { "DISPOSABLE" }) { _, _, _ ->
                    object : AeatTransportAdapter {
                        override fun execute(request: AeatTransportRequest): AeatTransportResult {
                            Files.writeString(root.resolve("attempt/response.xml"), "keep-original")
                            return simulatedResponse(bundle, "accepted")
                        }
                    }
                }
            assertEquals(4, result.exitCode)
            assertTrue(result.output.contains("reconcile"))
            assertTrue(Files.exists(root.resolve("attempt/started.properties")))
            assertEquals("keep-original", Files.readString(root.resolve("attempt/response.xml")))
            assertFalse(Files.exists(root.resolve("attempt/outcome.properties")))
        }

    @Test
    fun rejectsInvalidPasswordSourcesAndClearsPasswordEvenWhenCredentialLoadingFails() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root, "aeat-test")
            assertFailsWith<SandboxInputException> { runCommand(sendArguments(root, bundle.sha256), { null }) }
            val invalid = sendArguments(root, bundle.sha256, "bad-var").map { if (it == "TEST_PASSWORD") "INVALID-NAME" else it }
            assertFailsWith<SandboxInputException> { runCommand(invalid, { error("Do not read invalid variable names") }) }
            var held: CharArray? = null
            assertFailsWith<SandboxInputException> {
                runCommand(sendArguments(root, bundle.sha256, "bad-identity"), { "PRIVATE" }) { _, _, password ->
                    held = password
                    throw SandboxInputException("Selected identity unavailable.")
                }
            }
            assertTrue(held!!.all { it == '\u0000' })
            assertFalse(Files.exists(root.resolve("bad-identity/started.properties")))
        }

    @Test
    fun hasNoProductionEndpointOverridePasswordArgumentOrImplicitSendCommand() =
        withWorkspace { root ->
            fixtureBundle(root)
            listOf(
                listOf("send", "--endpoint", "https://production.invalid"),
                listOf("send", "--password", "PRIVATE"),
                listOf("show", "--bundle", root.resolve("bundle").toString(), "--send"),
                listOf(
                    "prepare",
                    "--scenario",
                    "duplicate",
                    "--previous",
                    root.resolve("bundle").toString(),
                    "--out",
                    root.resolve("copy").toString(),
                    "--mode",
                    "aeat-test",
                ),
                listOf("show", "--bundle", "x", "--bundle", "y"),
                listOf("show", "--bundle"),
                listOf("show"),
                listOf("show", "bundle"),
                listOf("show", "--bundle", "--input"),
                listOf("show", "--"),
                listOf("other"),
            ).forEach { assertFailsWith<SandboxInputException> { runCommand(it) } }
            assertTrue(runCommand(emptyList()).output.contains("TEST only"))
            assertEquals(runCommand(emptyList()), runCommand(listOf("--help")))
            assertTrue(runCommand(listOf("scenarios")).output.contains("cancellation"))
            assertFailsWith<SandboxInputException> { runCommand(listOf("scenarios", "--details")) }
        }
}
