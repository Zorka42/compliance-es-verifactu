package dev.verifactu.testkit

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
import dev.verifactu.qr.QrPayload
import dev.verifactu.qr.QrPayloadBuilder
import dev.verifactu.qr.QrPayloadInput
import dev.verifactu.qr.QrPayloadResult
import dev.verifactu.xml.RegistroXmlSerializer
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Synthetic, fixed inputs for downstream tests. Never submit these records to AEAT.
 *
 * Fixtures pass the library's current structural validation; they are not evidence of remote
 * acceptance. Returned drafts can be copied to exercise application-specific validation paths.
 */
public object VerifactuFixtures {
    /** Returns a fixed sample identifier; the serial can be changed to build a sequence. */
    @JvmStatic
    @JvmOverloads
    public fun invoice(number: String = "SAMPLE-001"): InvoiceIdentifier =
        InvoiceIdentifier(
            fixtureValue(TaxIdentifier.parse("89890001K")),
            fixtureValue(InvoiceNumber.parse(number)),
            fixtureValue(InvoiceIssueDate.parse("01-01-2024")),
        )

    /** Returns synthetic SIF metadata; no credential material is involved. */
    @JvmStatic
    public fun system(): SistemaInformatico =
        SistemaInformatico("Example producer", invoice().issuer, "VeriFactu sample", "VF", "0.1", "sample-1", true, false, false)

    /** Builds a simplified-invoice draft with a fixed 100.00 base, 21.00 tax, and 121.00 total. */
    @JvmStatic
    @JvmOverloads
    public fun registrationDraft(
        chainState: ChainState = ChainState.FirstRecord,
        number: String = "SAMPLE-001",
    ): RegistroAltaDraft =
        RegistroAltaDraft(
            version = RecordVersion.V1_0,
            invoice = invoice(number),
            issuerName = "Example issuer",
            invoiceType = InvoiceType.F2,
            totalTax = fixtureValue(FiscalAmount.parse("21.00")),
            totalAmount = fixtureValue(FiscalAmount.parse("121.00")),
            operationDescription = "Synthetic sample operation",
            taxBreakdown =
                TaxBreakdown(
                    listOf(
                        TaxBreakdownDetail(
                            operation = TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                            taxableBase = fixtureValue(FiscalAmount.parse("100.00")),
                            tax = TaxType.IVA,
                            regimeCode = "01",
                            taxRate = "21",
                            chargedTax = fixtureValue(FiscalAmount.parse("21.00")),
                        ),
                    ),
                ),
            chainState = chainState,
            system = system(),
            generatedAt = fixtureValue(RecordGenerationTimestamp.parse("2024-01-01T12:00:00+01:00")),
        )

    /** Creates a locally validated registration and the chain head to persist with it. */
    @JvmStatic
    @JvmOverloads
    public fun registration(
        chainState: ChainState = ChainState.FirstRecord,
        number: String = "SAMPLE-001",
    ): RecordCreationResult.Created<RegistroAlta> =
        fixtureRecord(FiscalRecordFactory.createRegistration(registrationDraft(chainState, number)))

    /** Returns the chain head of the default sample registration. */
    @JvmStatic
    public fun chainState(): ChainState.PreviousRecord = registration().nextChainState

    /** Builds a cancellation draft, normally following the default sample registration. */
    @JvmStatic
    @JvmOverloads
    public fun cancellationDraft(chainState: ChainState = chainState()): RegistroAnulacionDraft =
        RegistroAnulacionDraft(
            invoice(),
            chainState,
            system(),
            fixtureValue(RecordGenerationTimestamp.parse("2024-01-01T12:01:00+01:00")),
        )

    /** Creates a locally validated cancellation and its next chain head. */
    @JvmStatic
    @JvmOverloads
    public fun cancellation(chainState: ChainState = chainState()): RecordCreationResult.Created<RegistroAnulacion> =
        fixtureRecord(FiscalRecordFactory.createCancellation(cancellationDraft(chainState)))

    /** Serializes the default sample registration using the production serializer. */
    @JvmStatic
    public fun registrationXml(): String = RegistroXmlSerializer.serialize(registration().record)

    /** Serializes the default sample cancellation using the production serializer. */
    @JvmStatic
    public fun cancellationXml(): String = RegistroXmlSerializer.serialize(cancellation().record)

    /** Builds a test-environment URL as data only; this function never opens the URL. */
    @JvmStatic
    public fun qrPayload(): QrPayload {
        val draft = registrationDraft()
        return when (val result = QrPayloadBuilder.build(QrPayloadInput(draft.invoice, draft.totalAmount, QrEnvironment.TEST))) {
            is QrPayloadResult.Created -> result.payload
            is QrPayloadResult.Invalid -> error("The built-in QR fixture failed local validation.")
        }
    }
}

private fun <T> fixtureValue(result: ValueResult<T>): T =
    when (result) {
        is ValueResult.Valid -> result.value
        is ValueResult.Invalid -> error("Invalid fixture value: ${result.error.code}")
    }

private fun <T> fixtureRecord(result: RecordCreationResult<T>): RecordCreationResult.Created<T> =
    when (result) {
        is RecordCreationResult.Created -> result
        is RecordCreationResult.Invalid -> error("The fixture failed local structural validation.")
    }
