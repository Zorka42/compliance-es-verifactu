package dev.verifactu.sample

import dev.verifactu.aeat.AeatFlowControl
import dev.verifactu.aeat.AeatOperationType
import dev.verifactu.aeat.AeatRecordStatus
import dev.verifactu.aeat.AeatSubmissionStatus
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.aeat.FiscalSubmissionPreparation
import dev.verifactu.aeat.RegistrationPreparationResult
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.ValueResult
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario
import dev.verifactu.testkit.FakeAeatTransport
import dev.verifactu.xml.SubmissionRecord
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OfflineAttemptOutcomeTest {
    @Test
    fun keepsAcceptedWarningRejectedAndDuplicateResponsesDistinct() {
        val prepared = preparedRegistration()
        val scenarios =
            listOf(
                AeatResponseScenario.ACCEPTED to AeatRecordStatus.ACCEPTED,
                AeatResponseScenario.ACCEPTED_WITH_ERRORS to AeatRecordStatus.ACCEPTED_WITH_ERRORS,
                AeatResponseScenario.REJECTED to AeatRecordStatus.REJECTED,
                AeatResponseScenario.DUPLICATE to AeatRecordStatus.DUPLICATE,
            )
        scenarios.forEach { (scenario, expected) ->
            val outcome =
                assertIs<ExampleAttemptOutcome.KnownResponse>(
                    fakeAttempt(prepared, AeatResponseFixtures.response(scenario, prepared.record.draft.invoice)),
                )
            assertEquals(expected, outcome.matchedLines.single().status)
            assertEquals(scenario == AeatResponseScenario.ACCEPTED, outcome.isAccepted)
            if (scenario == AeatResponseScenario.ACCEPTED_WITH_ERRORS) {
                assertEquals(AeatSubmissionStatus.PARTIALLY_ACCEPTED, outcome.response.status)
            }
            if (scenario == AeatResponseScenario.DUPLICATE) {
                assertEquals(AeatRecordStatus.REJECTED, outcome.matchedLines.single().declaredStatus)
                assertEquals(
                    "TEST-REQUEST",
                    outcome.matchedLines
                        .single()
                        .duplicate
                        ?.requestId,
                )
            }
        }
    }

    @Test
    fun separatesNotSentUnknownDeliveryAndUnrecognizedHttpResponses() {
        val prepared = preparedRegistration()
        listOf(AeatTransportResult.NotSent("Synthetic setup failure"), AeatTransportResult.InvalidEndpoint("Synthetic endpoint failure"))
            .forEach { assertIs<ExampleAttemptOutcome.NotSent>(fakeAttempt(prepared, it)) }
        listOf(AeatTransportResult.Timeout("Synthetic timeout"), AeatTransportResult.NetworkFailure("Synthetic network failure"))
            .forEach { assertIs<ExampleAttemptOutcome.UnknownDelivery>(fakeAttempt(prepared, it)) }
        assertEquals(
            503,
            assertIs<ExampleAttemptOutcome.UnexpectedHttpResponse>(
                fakeAttempt(prepared, AeatTransportResult.NonXmlResponse(503, "text/html")),
            ).statusCode,
        )
        val nonSuccess = AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, prepared.record.draft.invoice).copy(statusCode = 500)
        assertIs<ExampleAttemptOutcome.UnexpectedHttpResponse>(fakeAttempt(prepared, nonSuccess))
    }

    @Test
    fun separatesSoapFaultFromMalformedXml() {
        val prepared = preparedRegistration()
        val fault = assertIs<ExampleAttemptOutcome.SoapFailure>(fakeAttempt(prepared, AeatResponseFixtures.soapFault()))
        assertEquals("soap:Client", fault.fault.code)
        assertIs<ExampleAttemptOutcome.MalformedResponse>(
            fakeAttempt(prepared, AeatTransportResult.XmlResponse(200, "text/xml", "<broken")),
        )
    }

    @Test
    fun neverAcceptsAnotherInvoiceOrTheWrongOperation() {
        val prepared = preparedRegistration()
        val otherInvoice =
            prepared.record.draft.invoice
                .copy(number = assertIs<ValueResult.Valid<InvoiceNumber>>(InvoiceNumber.parse("OTHER-001")).value)
        assertIs<ExampleAttemptOutcome.ResponseMismatch>(
            fakeAttempt(prepared, AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, otherInvoice)),
        )
        assertIs<ExampleAttemptOutcome.ResponseMismatch>(
            fakeAttempt(
                prepared,
                AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, prepared.record.draft.invoice, AeatOperationType.CANCELLATION),
            ),
        )
    }

    @Test
    fun retainsUnknownStatesAndFlowControlWithoutInventingAcceptanceOrAWait() {
        val prepared = preparedRegistration()
        val accepted = AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, prepared.record.draft.invoice)
        val unknown = accepted.copy(xml = accepted.xml.replace("Correcto", "FutureState"))
        val outcome = assertIs<ExampleAttemptOutcome.UnknownResponseState>(fakeAttempt(prepared, unknown))
        assertEquals(AeatSubmissionStatus.UNKNOWN_STATE, outcome.response.status)
        assertEquals("FutureState", outcome.response.rawStatus)

        val flow =
            assertIs<ExampleAttemptOutcome.KnownResponse>(
                fakeAttempt(prepared, AeatResponseFixtures.response(AeatResponseScenario.FLOW_CONTROL, prepared.record.draft.invoice)),
            )
        assertEquals(AeatFlowControl.Known(60), flow.response.flowControl)
        assertTrue(flow.isAccepted)

        val unknownWait =
            accepted.copy(
                xml = accepted.xml.replace("<TiempoEsperaEnvio>0</TiempoEsperaEnvio>", "<TiempoEsperaEnvio>future</TiempoEsperaEnvio>"),
            )
        val unknownFlow = assertIs<ExampleAttemptOutcome.KnownResponse>(fakeAttempt(prepared, unknownWait))
        assertEquals(AeatFlowControl.Unknown("future"), unknownFlow.response.flowControl)
        assertEquals(null, unknownFlow.response.retryAfterSeconds)
    }

    @Test
    fun manualRepeatUsesTheSavedPayloadAndDoesNotRegenerateARecord() {
        val prepared = preparedRegistration()
        val transport =
            FakeAeatTransport(
                listOf(
                    AeatTransportResult.Timeout("Synthetic unknown delivery"),
                    AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, prepared.record.draft.invoice),
                ),
            )
        val request = AeatTransportRequest(offlineEndpoint(), prepared.soapEnvelope)
        val savedBytes = request.xmlPayload.encodeToByteArray()
        val savedHash = prepared.record.hash
        val savedTimestamp = prepared.record.draft.generatedAt
        val submitted = listOf(SubmissionRecord.Registration(prepared.record))

        assertIs<ExampleAttemptOutcome.UnknownDelivery>(interpretExampleAttempt(submitted, transport.execute(request)))
        assertEquals(1, transport.requests.size)
        assertEquals(1, transport.remainingResults)

        // The test host explicitly chooses a second attempt; the interpreter never schedules it.
        val repeated = assertIs<ExampleAttemptOutcome.KnownResponse>(interpretExampleAttempt(submitted, transport.execute(request)))
        assertTrue(repeated.isAccepted)
        assertContentEquals(savedBytes, transport.requests[0].xmlPayload.encodeToByteArray())
        assertContentEquals(savedBytes, transport.requests[1].xmlPayload.encodeToByteArray())
        assertEquals(savedHash, prepared.record.hash)
        assertEquals(savedTimestamp, prepared.record.draft.generatedAt)
    }

    private fun preparedRegistration(): RegistrationPreparationResult.Prepared =
        assertIs<RegistrationPreparationResult.Prepared>(
            FiscalSubmissionPreparation.prepareRegistration(exampleRegistrationDraft(), QrEnvironment.TEST),
        )

    private fun fakeAttempt(
        prepared: RegistrationPreparationResult.Prepared,
        response: AeatTransportResult,
    ): ExampleAttemptOutcome {
        val transport = FakeAeatTransport(listOf(response))
        val result = transport.execute(AeatTransportRequest(offlineEndpoint(), prepared.soapEnvelope))
        assertEquals(1, transport.requests.size)
        assertEquals(0, transport.remainingResults)
        return interpretExampleAttempt(listOf(SubmissionRecord.Registration(prepared.record)), result)
    }
}
