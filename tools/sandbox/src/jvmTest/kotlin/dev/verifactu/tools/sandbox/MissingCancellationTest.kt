package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.core.ChainState
import dev.verifactu.core.ValidationSeverity
import dev.verifactu.xml.SubmissionRecord
import java.io.StringReader
import javax.xml.transform.stream.StreamSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MissingCancellationTest {
    @Test fun separatesAbsentTargetFromTheAcceptedChainTailAndUsesFreshPreparationTime() =
        withWorkspace { root ->
            val tail = fixtureBundle(root)
            val config = configFile(root, TEST_INPUTS + ("invoice.number" to "ABSENT-001"), "absent.properties")
            val request =
                BundlePreparation(config, root.resolve("missing"), "cancellation-missing", "offline", "standard", root.resolve("bundle"))
            val bundle = prepareBundle(request) { MATRIX_TIME }
            val record = assertIs<SubmissionRecord.Cancellation>(bundle.plan.record).value
            assertEquals("ABSENT-001", record.draft.cancelledInvoice.number.value)
            assertEquals(tail.plan.next, record.draft.chainState)
            assertEquals("2026-11-07T12:00:00+01:00", record.draft.generatedAt.value)
            assertTrue(bundle.plan.report.isValid)
            assertTrue("<SinRegistroPrevio>" !in bundle.plan.soap)
            assertTrue("<RechazoPrevio>" !in bundle.plan.soap)
            assertEquals(bundle.sha256, loadBundle(root.resolve("missing")).sha256)
            val rejected = simulatedResponse(bundle, "rejected") as AeatTransportResult.XmlResponse
            matrixResponseSchema().newValidator().validate(StreamSource(StringReader(rejected.xml)))
            val expected = summarizeTransport(bundle, rejected)
            assertEquals(0, expected.exitCode)
            assertTrue("Result: REJECTED" in expected.output)
            assertTrue("AEAT codes: [3002]" in expected.output)
            assertTrue("Expected scenario verification: PASS" in expected.output)
            val accepted = summarizeTransport(bundle, simulatedResponse(bundle, "accepted"))
            assertEquals(3, accepted.exitCode)
            assertTrue("Expected scenario verification: REVIEW" in accepted.output)
            val wrongCode = rejected.copy(xml = rejected.xml.replace(">3002<", ">1152<"))
            assertEquals(3, summarizeTransport(bundle, wrongCode).exitCode)
            for (status in listOf(199, 500)) {
                assertEquals(3, summarizeTransport(bundle, rejected.copy(statusCode = status)).exitCode)
            }
        }

    @Test fun incompleteAndContradictoryRejectionsCannotSatisfyTheNegativeScenario() =
        withWorkspace { root ->
            fixtureBundle(root)
            val config = configFile(root, TEST_INPUTS + ("invoice.number" to "ABSENT-001"), "absent.properties")
            val bundle =
                prepareBundle(
                    BundlePreparation(
                        config,
                        root.resolve("missing"),
                        "cancellation-missing",
                        "offline",
                        "standard",
                        root.resolve("bundle"),
                    ),
                )
            val rejected = simulatedResponse(bundle, "rejected") as AeatTransportResult.XmlResponse
            assertEquals(0, inspectXml(bundle, rejected.xml, false).exitCode)
            val line =
                "<RespuestaLinea>" + rejected.xml.substringAfter("<RespuestaLinea>").substringBefore("</RespuestaLinea>") +
                    "</RespuestaLinea>"
            val variants =
                mapOf(
                    "no line" to rejected.xml.replace(line, ""),
                    "repeated line" to rejected.xml.replace(line, line + line),
                    "other invoice" to rejected.xml.replace("ABSENT-001", "OTHER-001"),
                    "wrong operation" to rejected.xml.replace(">Anulacion<", ">Alta<"),
                    "contradictory state" to rejected.xml.replace("<EstadoRegistro>Incorrecto<", "<EstadoRegistro>Correcto<"),
                    "unknown wait" to rejected.xml.replace("<TiempoEsperaEnvio>0<", "<TiempoEsperaEnvio>unknown<"),
                )
            for ((name, xml) in variants) {
                val result = summarizeTransport(bundle, rejected.copy(xml = xml))
                assertEquals(3, result.exitCode, name)
                assertTrue("Expected scenario verification: PASS" !in result.output, name)
            }
        }

    @Test fun commandPrintsAReviewableConfirmationWithoutCreatingAnAttempt() =
        withWorkspace { root ->
            fixtureBundle(root, "aeat-test")
            val config = configFile(root, TEST_INPUTS + ("invoice.number" to "ABSENT-001"), "absent.properties")
            val output = root.resolve("missing 'quoted'")
            val prepared =
                SandboxWorkflow.execute(
                    listOf(
                        "prepare",
                        "--scenario",
                        "cancellation-missing",
                        "--config",
                        config.toString(),
                        "--previous",
                        root.resolve("bundle").toString(),
                        "--mode",
                        "aeat-test",
                        "--out",
                        output.toString(),
                    ),
                )
            assertEquals(0, prepared.exitCode)
            val bundle = loadBundle(output)
            assertTrue("code 3002" in prepared.output)
            assertTrue("'--confirm-sha256' '${bundle.sha256}'" in prepared.output)
            assertTrue("'--confirm-records' '1'" in prepared.output)
            assertTrue("'\"'\"'" in prepared.output)
            assertTrue("No request was sent" in prepared.output)
            assertTrue(
                !java.nio.file.Files
                    .exists(root.resolve("missing 'quoted'-attempt-001")),
            )
            val shown = SandboxWorkflow.execute(listOf("show", "--bundle", output.toString()))
            assertEquals(0, shown.exitCode)
            assertTrue(bundle.sha256 in shown.output)
            assertTrue(bundle.plan.soap in shown.output)
            fixtureBundle(root, "offline", "offline-tail")
            val offline =
                SandboxWorkflow.execute(
                    listOf(
                        "prepare",
                        "--scenario",
                        "cancellation-missing",
                        "--config",
                        config.toString(),
                        "--previous",
                        root.resolve("offline-tail").toString(),
                        "--out",
                        root.resolve("offline").toString(),
                    ),
                )
            assertEquals(0, offline.exitCode)
            assertTrue("Separately authorized TEST write:" !in offline.output)
            assertTrue("Mode: offline" in offline.output)
        }

    @Test fun requiresADistinctTargetAndMatchingPreviousMetadata() =
        withWorkspace { root ->
            fixtureBundle(root)
            val same = configFile(root, TEST_INPUTS, "same.properties")
            val request =
                BundlePreparation(same, root.resolve("missing"), "cancellation-missing", "offline", "standard", root.resolve("bundle"))
            assertFailsWith<SandboxInputException> { prepareBundle(request) }
            assertFailsWith<SandboxInputException> { prepareBundle(request.copy(previousPath = null)) }
            val differentIssuer =
                configFile(root, TEST_INPUTS + mapOf("invoice.number" to "ABSENT-001", "issuer.name" to "Other"), "other.properties")
            assertFailsWith<SandboxInputException> { prepareBundle(request.copy(config = differentIssuer)) }
        }

    @Test fun normalPreparationRejectsTheDateAndLegacyNegativeFixturesRemainInspectable() =
        withWorkspace { root ->
            val before = TEST_INPUTS + ("invoice.date" to "27-10-2024")
            val config = configFile(root, before)
            val request = BundlePreparation(config, root.resolve("negative"), "registration", "offline", "standard", null)
            assertFailsWith<SandboxInputException> { prepareBundle(request) }
            val bundle = prepareBundle(request.copy(scenario = "rejection-date"))
            assertEquals(
                listOf("VF-DATE-1152"),
                bundle.plan.report.issues
                    .filter { it.severity == ValidationSeverity.ERROR }
                    .map { it.code },
            )
            assertEquals(bundle.plan.soap, loadBundle(root.resolve("negative")).plan.soap)
            val draft = assertIs<SubmissionRecord.Registration>(bundle.plan.record).value.draft
            assertFailsWith<SandboxInputException> {
                buildPreCutoffFixture(
                    draft.copy(
                        invoice =
                            draft.invoice.copy(
                                issueDate =
                                    value(
                                        dev.verifactu.core.InvoiceIssueDate
                                            .parse("28-10-2024"),
                                    ),
                            ),
                    ),
                )
            }

            assertEquals(ChainState.FirstRecord, assertIs<SubmissionRecord.Registration>(bundle.plan.record).value.draft.chainState)
            val warned =
                buildPreCutoffFixture(
                    draft.copy(
                        totalAmount =
                            value(
                                dev.verifactu.core.FiscalAmount
                                    .parse("150.00"),
                            ),
                    ),
                )
            assertTrue(warned.report.issues.any { it.severity == ValidationSeverity.WARNING && it.aeatCode == "2005" })
            assertEquals(
                listOf("VF-DATE-1152"),
                warned.report.issues
                    .filter { it.severity == ValidationSeverity.ERROR }
                    .map { it.code },
            )
            val malformed = configFile(root, before + ("invoice.number" to "BAD'NUMBER"), "malformed.properties")
            assertFailsWith<SandboxInputException> { prepareBundle(request.copy(config = malformed, scenario = "rejection-date")) }
        }
}
