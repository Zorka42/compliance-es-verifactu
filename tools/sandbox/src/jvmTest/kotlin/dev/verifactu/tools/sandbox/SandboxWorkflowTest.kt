package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxWorkflowTest {
    @Test
    fun bridgeUsesExistingResponseSemanticsAndPreservesUnknownDelivery() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root, "aeat-test")
            for ((index, scenario) in OFFLINE_SCENARIOS.withIndex()) {
                var calls = 0
                val result =
                    SandboxWorkflow.send(
                        root.resolve("bundle"),
                        root.resolve("attempt-$index"),
                        bundle.sha256,
                        object : AeatTransportAdapter {
                            override fun execute(request: AeatTransportRequest): AeatTransportResult {
                                calls++
                                assertEquals(bundle.plan.soap, request.xmlPayload)
                                return simulatedResponse(bundle, scenario)
                            }
                        },
                    )
                assertEquals(1, calls)
                assertEquals(
                    if (scenario == "accepted" ||
                        scenario == "flow-control"
                    ) {
                        0
                    } else if (scenario in listOf("timeout", "not-sent")) {
                        4
                    } else {
                        3
                    },
                    result.exitCode,
                )
                assertFalse(result.toString().contains("Environment"))
            }
        }

    @Test
    fun bridgeRejectsBadLocalInputAndMissingOutputParentWithoutInvokingTransport() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root, "aeat-test")
            val never =
                object : AeatTransportAdapter {
                    override fun execute(request: AeatTransportRequest): AeatTransportResult = error("No transport")
                }
            assertEquals(2, SandboxWorkflow.execute(listOf("show", "--bundle", root.resolve("absent").toString())).exitCode)
            assertEquals(2, SandboxWorkflow.send(root.resolve("bundle"), root.resolve("attempt"), "0".repeat(64), never).exitCode)
            assertEquals(2, SandboxWorkflow.send(root.resolve("bundle"), root.resolve("absent/attempt"), bundle.sha256, never).exitCode)
            assertFalse(Files.exists(root.resolve("attempt")))
            assertTrue(SandboxWorkflow.execute(listOf("scenarios")).output.contains("registration"))
        }
}
