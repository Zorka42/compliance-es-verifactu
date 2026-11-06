package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxInspectionTest {
    @Test
    fun contradictoryOrIncompleteStatusesNeverBecomeCleanAcceptance() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root)
            val xml = (simulatedResponse(bundle, "accepted") as AeatTransportResult.XmlResponse).xml
            listOf(
                xml.replace("<EstadoRegistro>Correcto</EstadoRegistro>", "<EstadoRegistro>Future</EstadoRegistro>"),
                xml.replace("<EstadoEnvio>Correcto</EstadoEnvio>", "<EstadoEnvio>Future</EstadoEnvio>"),
                xml.replace("<EstadoEnvio>Correcto</EstadoEnvio>", "<EstadoEnvio>ParcialmenteCorrecto</EstadoEnvio>"),
                xml.replace("<EstadoRegistro>Correcto</EstadoRegistro>", "<EstadoRegistro>AceptadoConErrores</EstadoRegistro>"),
                xml.replace(
                    "<EstadoRegistro>Correcto</EstadoRegistro>",
                    "<EstadoRegistro>Correcto</EstadoRegistro><DescripcionErrorRegistro>PRIVATE</DescripcionErrorRegistro>",
                ),
            ).forEach {
                val result = inspectXml(bundle, it, false)
                assertEquals(3, result.exitCode)
                assertFalse(result.output.contains("PRIVATE"))
            }
            assertEquals(3, inspectXml(bundle, xml, false, 199).exitCode)
            val duplicate = (simulatedResponse(bundle, "duplicate") as AeatTransportResult.XmlResponse).xml
            val details = inspectXml(bundle, duplicate, true)
            assertEquals(3, details.exitCode)
            assertTrue(details.output.contains("duplicate=TEST-REQUEST"))
        }

    @Test
    fun emptyFaultDiagnosticsAreRejectedWithoutInventingAcceptance() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root)
            val xml = "<soap:Fault xmlns:soap='http://schemas.xmlsoap.org/soap/envelope/'><faultcode/><faultstring/></soap:Fault>"
            val result = inspectXml(bundle, xml, true)
            assertEquals(3, result.exitCode)
            assertTrue(result.output.contains("INVALID_XML"))
        }

    @Test
    fun interpretsAllOfflineBranchesWithoutCredentialsOrNetwork() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root)
            val expected =
                mapOf(
                    "accepted" to ("ACCEPTED" to 0),
                    "warning" to ("REVIEW_REQUIRED" to 3),
                    "rejected" to ("REJECTED" to 3),
                    "duplicate" to ("DUPLICATE" to 3),
                    "flow-control" to ("ACCEPTED" to 0),
                    "soap-fault" to ("SOAP_FAULT" to 3),
                    "timeout" to ("TIMEOUT" to 4),
                    "not-sent" to ("NOT_SENT" to 4),
                )
            expected.forEach { (scenario, outcome) ->
                val result = summarizeTransport(bundle, simulatedResponse(bundle, scenario))
                assertTrue(result.output.contains("Result: ${outcome.first}"), scenario)
                assertEquals(outcome.second, result.exitCode, scenario)
                assertFalse(result.output.contains("89890001K"))
                assertFalse(result.output.contains("SANDBOX-001"))
            }
            assertTrue(summarizeTransport(bundle, simulatedResponse(bundle, "flow-control")).output.contains("seconds: 60"))
            val demo =
                runCommand(listOf("demo", "--bundle", root.resolve("bundle").toString()), environment = {
                    error("No environment access")
                }, transportFactory = {
                    _,
                    _,
                    _,
                    ->
                    error("No credential access")
                })
            assertEquals(0, demo.exitCode)
            OFFLINE_SCENARIOS.forEach { assertTrue(demo.output.contains("SIMULATION: $it")) }
            assertTrue(demo.output.contains("Network calls: 0"))
        }

    @Test
    fun rejectsUnsafeXmlAndMismatchedOrUnknownResponsesInsteadOfInventingAcceptance() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root)
            val xml = (simulatedResponse(bundle, "accepted") as AeatTransportResult.XmlResponse).xml
            val invalid = listOf(xml.dropLast(1), "<!DOCTYPE x SYSTEM 'file:///never-read'>$xml", "<wrong/>")
            invalid.forEach { assertTrue(inspectXml(bundle, it, false).output.contains("INVALID_XML")) }
            val mismatch = inspectXml(bundle, xml.replace("SANDBOX-001", "WRONG"), false)
            assertEquals(3, mismatch.exitCode)
            assertTrue(mismatch.output.contains("CORRELATION_MISMATCH"))
            assertTrue(mismatch.output.contains("MISSING_RESPONSE_LINE"))
            val unknown = inspectXml(bundle, xml.replace("Correcto", "FutureStatus"), false)
            assertTrue(unknown.output.contains("UNKNOWN_STATE"))
            val httpFailure = inspectXml(bundle, xml, false, 500)
            assertTrue(httpFailure.output.contains("NON_SUCCESS_HTTP"))
            assertEquals(3, httpFailure.exitCode)
            val unknownCode =
                xml.replace(
                    "<EstadoRegistro>Correcto</EstadoRegistro>",
                    "<EstadoRegistro>Correcto</EstadoRegistro><CodigoErrorRegistro>REMOTE-PRIVATE</CodigoErrorRegistro>",
                )
            assertTrue(inspectXml(bundle, unknownCode, false).output.contains("codes: [UNKNOWN]"))
            assertEquals(3, inspectXml(bundle, unknownCode, false).exitCode)
            assertTrue(inspectXml(bundle, unknownCode, false).output.contains("Result: REVIEW_REQUIRED"))
            assertFalse(inspectXml(bundle, unknownCode, false).output.contains("REMOTE-PRIVATE"))
            assertTrue(
                inspectXml(bundle, xml.replace("<TiempoEsperaEnvio>0</TiempoEsperaEnvio>", ""), false).output.contains("seconds: unknown"),
            )
        }

    @Test
    fun detailsAreExplicitAndEscapeRemoteTerminalAndDirectionalControlText() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root)
            val response = (simulatedResponse(bundle, "warning") as AeatTransportResult.XmlResponse).xml
            val private = "PRIVATE\t\n\u202EMARKER\u2067\u3000"
            val xml = response.replace("Synthetic warning", private)
            val summary = inspectXml(bundle, xml, false)
            assertFalse(summary.output.contains("PRIVATE"))
            val details = inspectXml(bundle, xml, true)
            assertTrue(details.output.contains("89890001K"))
            assertTrue(details.output.contains("PRIVATE\\u0009\\u000a\\u202eMARKER"))
            assertFalse(details.output.contains('\u202E'))
            assertTrue(details.output.contains("\\u2067"))
            assertTrue(details.output.contains('\u3000'))
            val fault = (simulatedResponse(bundle, "soap-fault") as AeatTransportResult.XmlResponse).xml.replace("Synthetic fault", private)
            assertFalse(inspectXml(bundle, fault, false).output.contains("PRIVATE"))
            assertTrue(inspectXml(bundle, fault, true).output.contains("PRIVATE\\u0009"))
            assertTrue(terminalText("x".repeat(2000)).endsWith("[see saved XML]"))
        }

    @Test
    fun reportsEveryTransportFailureWithoutEchoingExceptionOrRemoteMediaText() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root)
            val private = "SECRET-PASSWORD-MARKER"
            listOf(
                AeatTransportResult.InvalidEndpoint(private),
                AeatTransportResult.NotSent(private),
                AeatTransportResult.NetworkFailure(private),
                AeatTransportResult.UnknownDelivery(private),
                AeatTransportResult.NonXmlResponse(403, private),
                AeatTransportResult.ResponseTooLarge(200, private, 1024),
            ).forEach {
                val result = summarizeTransport(bundle, it)
                assertEquals(4, result.exitCode)
                assertFalse(result.output.contains(private))
                assertTrue(result.output.contains("No acceptance established"))
            }
        }

    @Test
    fun inspectsSavedXmlAndCancellationResponsesThroughTheSamePublicParsers() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root)
            val response = (simulatedResponse(bundle, "accepted") as AeatTransportResult.XmlResponse).xml
            val file = root.resolve("response.xml")
            Files.writeString(file, response)
            val result =
                runCommand(listOf("inspect", "--bundle", root.resolve("bundle").toString(), "--input", file.toString(), "--details"))
            assertEquals(0, result.exitCode)
            assertTrue(result.output.contains("Saved XML inspection only"))
            val cancelled =
                prepareBundle(
                    BundlePreparation(
                        root.resolve("bundle-inputs.properties"),
                        root.resolve("cancel"),
                        "cancellation",
                        "offline",
                        "standard",
                        root.resolve("bundle"),
                    ),
                )
            val cancelResponse = simulatedResponse(cancelled, "accepted")
            assertEquals(0, summarizeTransport(cancelled, cancelResponse).exitCode)
        }
}
