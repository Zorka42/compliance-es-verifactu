package dev.verifactu.sample

import dev.verifactu.aeat.AeatAdvancedEndpointConfiguration
import dev.verifactu.aeat.AeatCertificateAccess
import dev.verifactu.aeat.AeatEndpointConfiguration
import dev.verifactu.aeat.AeatEnvironment
import dev.verifactu.aeat.AeatResponseParseResult
import dev.verifactu.aeat.AeatResponseParser
import dev.verifactu.aeat.AeatSubmissionResponse
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalAmount
import dev.verifactu.core.FiscalRecordFactory
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceIssueDate
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.InvoiceType
import dev.verifactu.core.Qualification
import dev.verifactu.core.RecordCreationResult
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
import dev.verifactu.qr.QrPayloadBuilder
import dev.verifactu.qr.QrPayloadInput
import dev.verifactu.qr.QrPayloadResult
import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario
import dev.verifactu.testkit.FakeAeatTransport
import dev.verifactu.xml.RegistroXmlSerializer
import dev.verifactu.xml.SubmissionBatchXmlSerializer
import dev.verifactu.xml.SubmissionHeader
import dev.verifactu.xml.SubmissionRecord

/** Application-owned artifacts demonstrated by the sample, not a production library facade. */
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
    val registration = created(FiscalRecordFactory.createRegistration(draft))
    val registrationXml = RegistroXmlSerializer.serialize(registration.record)
    val qr =
        when (val result = QrPayloadBuilder.build(QrPayloadInput(draft.invoice, draft.totalAmount, QrEnvironment.TEST))) {
            is QrPayloadResult.Created -> result.payload
            is QrPayloadResult.Invalid -> error("Sample QR input is invalid.")
        }

    // A real application atomically persists the record and this head under its per-chain lock.
    val persistedHead = registration.nextChainState
    val cancellation =
        created(
            FiscalRecordFactory.createCancellation(
                RegistroAnulacionDraft(
                    draft.invoice,
                    persistedHead,
                    draft.system,
                    value(RecordGenerationTimestamp.parse("2024-01-01T12:01:00+01:00")),
                ),
            ),
        )
    val cancellationXml = RegistroXmlSerializer.serialize(cancellation.record)
    val transport =
        FakeAeatTransport(
            listOf(
                AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED),
                AeatResponseFixtures.response(AeatResponseScenario.FLOW_CONTROL),
            ),
        )
    val endpoint =
        AeatEndpointConfiguration.submissionEndpointWithAdvancedOverride(
            AeatEnvironment.TEST,
            AeatCertificateAccess.STANDARD,
            AeatAdvancedEndpointConfiguration("https://example.invalid/verifactu"),
        )
    val records = listOf(SubmissionRecord.Registration(registration.record), SubmissionRecord.Cancellation(cancellation.record))
    val responses =
        records.map { record ->
            val batchXml = SubmissionBatchXmlSerializer.serialize(SubmissionHeader(draft.issuerName, draft.invoice.issuer), listOf(record))
            // This is batch XML sent to a fake, not a wire-ready SOAP integration example.
            when (val result = transport.execute(AeatTransportRequest(endpoint, batchXml))) {
                is AeatTransportResult.XmlResponse ->
                    when (val parsed = AeatResponseParser.parseSubmission(result.xml)) {
                        is AeatResponseParseResult.Parsed -> parsed.response
                        is AeatResponseParseResult.InvalidXml -> error("Synthetic response could not be parsed.")
                    }
                else -> error("Unexpected synthetic transport outcome.")
            }
        }
    return OfflineExampleResult(
        registration.record,
        cancellation.record,
        registrationXml,
        cancellationXml,
        qr.url,
        cancellation.nextChainState,
        responses,
        transport.requests,
    )
}

/** Models a finalized synthetic simplified invoice; business finalization belongs to the host. */
private fun exampleRegistrationDraft(): RegistroAltaDraft {
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

private fun <T> created(result: RecordCreationResult<T>): RecordCreationResult.Created<T> =
    when (result) {
        is RecordCreationResult.Created -> result
        is RecordCreationResult.Invalid -> error("Synthetic input failed local record validation.")
    }
