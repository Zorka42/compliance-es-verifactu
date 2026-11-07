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
            assertEquals(3, summarizeTransport(bundle, rejected.copy(statusCode = 500)).exitCode)
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
            assertEquals(ChainState.FirstRecord, assertIs<SubmissionRecord.Registration>(bundle.plan.record).value.draft.chainState)
            val malformed = configFile(root, before + ("invoice.number" to "BAD'NUMBER"), "malformed.properties")
            assertFailsWith<SandboxInputException> { prepareBundle(request.copy(config = malformed, scenario = "rejection-date")) }
        }
}
