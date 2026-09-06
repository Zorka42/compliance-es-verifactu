package dev.verifactu.testkit

import dev.verifactu.aeat.AeatEndpointConfiguration
import dev.verifactu.aeat.AeatEnvironment
import dev.verifactu.aeat.AeatRecordStatus
import dev.verifactu.aeat.AeatResponseParseResult
import dev.verifactu.aeat.AeatResponseParser
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.aeat.SoapFaultParseResult
import dev.verifactu.core.RegistroAltaValidator
import dev.verifactu.core.RegistroAnulacionValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VerifactuFixturesTest {
    @Test
    fun buildsDeterministicRecordsWithContinuousChainAndPayloads() {
        val first = VerifactuFixtures.registration()
        val second = VerifactuFixtures.registration(first.nextChainState, "SAMPLE-002")
        val cancellation = VerifactuFixtures.cancellation(second.nextChainState)

        assertTrue(RegistroAltaValidator.validate(first.record.draft).isValid)
        assertTrue(RegistroAnulacionValidator.validate(cancellation.record.draft).isValid)
        assertEquals(first.nextChainState, second.record.draft.chainState)
        assertEquals(second.nextChainState, cancellation.record.draft.chainState)
        assertEquals(cancellation.record.hash, cancellation.nextChainState.hash)
        assertEquals(first, VerifactuFixtures.registration())
        assertTrue(VerifactuFixtures.registrationXml().contains("<Huella>${first.record.hash}</Huella>"))
        assertTrue(VerifactuFixtures.cancellationXml().contains("<RegistroAnulacion"))
        assertEquals(
            "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?nif=89890001K&numserie=SAMPLE-001&fecha=01-01-2024&importe=121.00",
            VerifactuFixtures.qrPayload().url,
        )
    }

    @Test
    fun responseScenariosExerciseTheProductionParser() {
        val expected =
            listOf(
                AeatRecordStatus.ACCEPTED,
                AeatRecordStatus.ACCEPTED_WITH_ERRORS,
                AeatRecordStatus.REJECTED,
                AeatRecordStatus.DUPLICATE,
                AeatRecordStatus.ACCEPTED,
            )
        AeatResponseScenario.entries.forEachIndexed { index, scenario ->
            val response = AeatResponseFixtures.response(scenario)
            val parsed = assertIs<AeatResponseParseResult.Parsed>(AeatResponseParser.parseSubmission(response.xml)).response
            assertEquals(expected[index], parsed.lines.single().status)
            assertEquals(
                "SAMPLE-001",
                parsed.lines
                    .single()
                    .invoice
                    ?.number,
            )
            assertEquals(
                dev.verifactu.aeat.AeatOperationType.REGISTRATION,
                parsed.lines
                    .single()
                    .operation
                    ?.type,
            )
            assertEquals(if (scenario == AeatResponseScenario.FLOW_CONTROL) 60 else 0, parsed.retryAfterSeconds)
            if (scenario == AeatResponseScenario.DUPLICATE) assertEquals("TEST-REQUEST", parsed.lines.single().duplicateRequestId)
            if (scenario == AeatResponseScenario.REJECTED || scenario == AeatResponseScenario.DUPLICATE) {
                assertEquals(null, parsed.csv)
            }
            if (scenario == AeatResponseScenario.ACCEPTED_WITH_ERRORS) {
                assertEquals(dev.verifactu.aeat.AeatSubmissionStatus.PARTIALLY_ACCEPTED, parsed.status)
            }
        }
        assertIs<SoapFaultParseResult.Parsed>(AeatResponseParser.parseSoapFault(AeatResponseFixtures.soapFault().xml))
    }

    @Test
    fun scriptAndRequestHistoryAreSnapshotsAndNeverReachTheNetwork() {
        val failure = AeatTransportResult.NetworkFailure("Synthetic network failure")
        val timeout = AeatTransportResult.Timeout("Synthetic timeout")
        val script = mutableListOf<AeatTransportResult>(failure, timeout)
        val transport = FakeAeatTransport(script)
        script.clear()
        val request =
            AeatTransportRequest(
                AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.TEST),
                VerifactuFixtures.registrationXml(),
            )
        val emptyHistory = transport.requests

        assertSame(failure, transport.execute(request))
        val firstHistory = transport.requests
        assertSame(timeout, transport.execute(request))
        assertTrue(emptyHistory.isEmpty())
        assertEquals(listOf(request), firstHistory)
        assertEquals(listOf(request, request), transport.requests)
        assertEquals(0, transport.remainingResults)
        assertFailsWith<IllegalStateException> { transport.execute(request) }
        assertEquals(2, transport.requests.size)
        assertFalse(transport.toString().contains(request.xmlPayload))
    }
}
