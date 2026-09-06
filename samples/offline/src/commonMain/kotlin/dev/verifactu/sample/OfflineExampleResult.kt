package dev.verifactu.sample

import dev.verifactu.aeat.AeatAdvancedEndpointConfiguration
import dev.verifactu.aeat.AeatCertificateAccess
import dev.verifactu.aeat.AeatDuplicateStatus
import dev.verifactu.aeat.AeatEndpointConfiguration
import dev.verifactu.aeat.AeatEnvironment
import dev.verifactu.aeat.AeatOperationType
import dev.verifactu.aeat.AeatRecordStatus
import dev.verifactu.aeat.AeatResponseCorrelation
import dev.verifactu.aeat.AeatResponseCorrelationResult
import dev.verifactu.aeat.AeatResponseLine
import dev.verifactu.aeat.AeatResponseParseResult
import dev.verifactu.aeat.AeatResponseParser
import dev.verifactu.aeat.AeatSubmissionEndpoint
import dev.verifactu.aeat.AeatSubmissionResponse
import dev.verifactu.aeat.AeatSubmissionStatus
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.aeat.CancellationPreparationResult
import dev.verifactu.aeat.FiscalSubmissionPreparation
import dev.verifactu.aeat.RegistrationPreparationResult
import dev.verifactu.aeat.SoapFault
import dev.verifactu.aeat.SoapFaultParseResult
import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalAmount
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceIssueDate
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.InvoiceType
import dev.verifactu.core.Qualification
import dev.verifactu.core.RecordGenerationTimestamp
import dev.verifactu.core.RecordVersion
import dev.verifactu.core.RegistroAlta
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.core.RegistroAnulacion
import dev.verifactu.core.RegistroAnulacionDraft
import dev.verifactu.core.SistemaInformatico
import dev.verifactu.core.TaxBreakdown
import dev.verifactu.core.TaxBreakdownDetail
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.TaxOperation
import dev.verifactu.core.TaxType
import dev.verifactu.core.ValueResult
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario
import dev.verifactu.testkit.FakeAeatTransport
import dev.verifactu.xml.SubmissionHeader
import dev.verifactu.xml.SubmissionRecord

/** Application-owned artifacts composed through the production preparation API and a fake transport. */
internal data class OfflineExampleResult(
    val registration: RegistroAlta,
    val cancellation: RegistroAnulacion,
    val registrationXml: String,
    val cancellationXml: String,
    val qrUrl: String,
    val nextChainState: ChainState.PreviousRecord,
    val responses: List<AeatSubmissionResponse>,
    val capturedRequests: List<AeatTransportRequest>,
)

/** Runs shared production APIs with synthetic inputs and a transport that cannot perform I/O. */
internal fun runOfflineExample(): OfflineExampleResult {
    val draft = exampleRegistrationDraft()
    val registration =
        when (val result = FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.TEST)) {
            is RegistrationPreparationResult.Prepared -> result
            is RegistrationPreparationResult.Invalid -> error("Synthetic registration failed preparation.")
        }

    // A real application atomically persists the record and this head under its per-chain lock.
    val persistedHead = registration.nextChainState
    val cancellation =
        when (
            val result =
                FiscalSubmissionPreparation.prepareCancellation(
                    RegistroAnulacionDraft(
                        draft.invoice,
                        persistedHead,
                        draft.system,
                        value(RecordGenerationTimestamp.parse("2024-01-01T12:01:00+01:00")),
                    ),
                    SubmissionHeader(draft.issuerName, draft.invoice.issuer),
                )
        ) {
            is CancellationPreparationResult.Prepared -> result
            is CancellationPreparationResult.Invalid -> error("Synthetic cancellation failed preparation.")
        }
    val transport =
        FakeAeatTransport(
            listOf(
                AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, draft.invoice, AeatOperationType.REGISTRATION),
                AeatResponseFixtures.response(AeatResponseScenario.FLOW_CONTROL, draft.invoice, AeatOperationType.CANCELLATION),
            ),
        )
    val requests =
        listOf(
            SubmissionRecord.Registration(registration.record) to registration.soapEnvelope,
            SubmissionRecord.Cancellation(cancellation.record) to cancellation.soapEnvelope,
        )
    val responses =
        requests.map { (record, savedPayload) ->
            val result = transport.execute(AeatTransportRequest(offlineEndpoint(), savedPayload))
            when (val outcome = interpretExampleAttempt(listOf(record), result)) {
                is ExampleAttemptOutcome.KnownResponse -> {
                    check(outcome.isAccepted) { "The synthetic scenario expected matched acceptance." }
                    outcome.response
                }
                else -> error("Unexpected synthetic attempt outcome.")
            }
        }
    return OfflineExampleResult(
        registration.record,
        cancellation.record,
        registration.recordXml,
        cancellation.recordXml,
        registration.qr.url,
        cancellation.nextChainState,
        responses,
        transport.requests,
    )
}

/** Host-owned interpretation of one attempt, independent of persistence and retry scheduling. */
internal sealed interface ExampleAttemptOutcome {
    data class NotSent(
        val transport: AeatTransportResult,
    ) : ExampleAttemptOutcome

    data class UnknownDelivery(
        val transport: AeatTransportResult,
    ) : ExampleAttemptOutcome

    data class UnexpectedHttpResponse(
        val statusCode: Int,
    ) : ExampleAttemptOutcome

    data class SoapFailure(
        val fault: SoapFault,
    ) : ExampleAttemptOutcome

    data object MalformedResponse : ExampleAttemptOutcome

    data class ResponseMismatch(
        val response: AeatSubmissionResponse,
        val correlation: AeatResponseCorrelationResult.Mismatch,
    ) : ExampleAttemptOutcome

    data class UnknownResponseState(
        val response: AeatSubmissionResponse,
    ) : ExampleAttemptOutcome

    data class KnownResponse(
        val response: AeatSubmissionResponse,
        val matchedLines: List<AeatResponseLine>,
    ) : ExampleAttemptOutcome {
        val isAccepted: Boolean
            get() =
                response.status == AeatSubmissionStatus.ACCEPTED &&
                    matchedLines.isNotEmpty() &&
                    matchedLines.all { it.status == AeatRecordStatus.ACCEPTED && it.declaredStatus == AeatRecordStatus.ACCEPTED }
    }
}

/** Pure application policy example: no result causes an automatic resend or a chain-state change. */
internal fun interpretExampleAttempt(
    submitted: List<SubmissionRecord>,
    transportResult: AeatTransportResult,
): ExampleAttemptOutcome =
    when (transportResult) {
        is AeatTransportResult.InvalidEndpoint, is AeatTransportResult.NotSent -> ExampleAttemptOutcome.NotSent(transportResult)
        is AeatTransportResult.Timeout,
        is AeatTransportResult.NetworkFailure,
        is AeatTransportResult.UnknownDelivery,
        -> ExampleAttemptOutcome.UnknownDelivery(transportResult)
        is AeatTransportResult.NonXmlResponse -> ExampleAttemptOutcome.UnexpectedHttpResponse(transportResult.statusCode)
        is AeatTransportResult.XmlResponse -> interpretExampleXml(submitted, transportResult)
    }

private fun interpretExampleXml(
    submitted: List<SubmissionRecord>,
    transportResult: AeatTransportResult.XmlResponse,
): ExampleAttemptOutcome {
    val response =
        when (val parsed = AeatResponseParser.parseSubmission(transportResult.xml)) {
            is AeatResponseParseResult.Parsed -> parsed.response
            is AeatResponseParseResult.InvalidXml ->
                return when (val fault = AeatResponseParser.parseSoapFault(transportResult.xml)) {
                    is SoapFaultParseResult.Parsed -> ExampleAttemptOutcome.SoapFailure(fault.fault)
                    is SoapFaultParseResult.InvalidXml -> ExampleAttemptOutcome.MalformedResponse
                }
        }
    if (transportResult.statusCode !in 200..299) return ExampleAttemptOutcome.UnexpectedHttpResponse(transportResult.statusCode)
    val correlation = AeatResponseCorrelation.correlate(submitted, response)
    if (correlation is AeatResponseCorrelationResult.Mismatch) return ExampleAttemptOutcome.ResponseMismatch(response, correlation)
    val matched = (correlation as AeatResponseCorrelationResult.Matched).lines
    val unknownState =
        response.status == AeatSubmissionStatus.UNKNOWN_STATE ||
            matched.any {
                it.status == AeatRecordStatus.UNKNOWN_STATE ||
                    it.declaredStatus == AeatRecordStatus.UNKNOWN_STATE ||
                    it.duplicate?.status == AeatDuplicateStatus.UNKNOWN_STATE
            }
    return if (unknownState) {
        ExampleAttemptOutcome.UnknownResponseState(response)
    } else {
        ExampleAttemptOutcome.KnownResponse(response, matched)
    }
}

internal fun offlineEndpoint(): AeatSubmissionEndpoint =
    AeatEndpointConfiguration.submissionEndpointWithAdvancedOverride(
        AeatEnvironment.TEST,
        AeatCertificateAccess.STANDARD,
        AeatAdvancedEndpointConfiguration("https://example.invalid/verifactu"),
    )

/** Models a finalized synthetic simplified invoice; business finalization belongs to the host. */
internal fun exampleRegistrationDraft(): RegistroAltaDraft {
    val issuer = value(TaxIdentifier.parse("89890001K"))
    return RegistroAltaDraft(
        version = RecordVersion.V1_0,
        invoice = InvoiceIdentifier(issuer, value(InvoiceNumber.parse("SAMPLE-001")), value(InvoiceIssueDate.parse("01-01-2024"))),
        issuerName = "Example issuer",
        invoiceType = InvoiceType.F2,
        totalTax = value(FiscalAmount.parse("21.00")),
        totalAmount = value(FiscalAmount.parse("121.00")),
        operationDescription = "Synthetic sample operation",
        taxBreakdown =
            TaxBreakdown(
                listOf(
                    TaxBreakdownDetail(
                        operation = TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                        taxableBase = value(FiscalAmount.parse("100.00")),
                        tax = TaxType.IVA,
                        regimeCode = "01",
                        taxRate = "21",
                        chargedTax = value(FiscalAmount.parse("21.00")),
                    ),
                ),
            ),
        chainState = ChainState.FirstRecord,
        system = SistemaInformatico("Example producer", issuer, "VeriFactu sample", "VF", "0.1", "sample-1", true, false, false),
        generatedAt = value(RecordGenerationTimestamp.parse("2024-01-01T12:00:00+01:00")),
    )
}

private fun <T> value(result: ValueResult<T>): T =
    when (result) {
        is ValueResult.Valid -> result.value
        is ValueResult.Invalid -> error("Invalid synthetic input: ${result.error.code}")
    }
