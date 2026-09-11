package dev.verifactu.aeat

import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalAmount
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceIssueDate
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.InvoiceType
import dev.verifactu.core.Qualification
import dev.verifactu.core.RecordGenerationTimestamp
import dev.verifactu.core.RecordVersion
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.core.RegistroAnulacionDraft
import dev.verifactu.core.SistemaInformatico
import dev.verifactu.core.TaxBreakdown
import dev.verifactu.core.TaxBreakdownDetail
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.TaxOperation
import dev.verifactu.core.TaxType
import dev.verifactu.core.ValidationContext
import dev.verifactu.core.ValidationSeverity
import dev.verifactu.core.ValueResult
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.xml.SubmissionBatchBuildResult
import dev.verifactu.xml.SubmissionHeader
import dev.verifactu.xml.SubmissionRecord
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FiscalSubmissionPreparationTest {
    @Test
    fun receiptDateContextSurvivesFactoryFacadeAndBatchRevalidation() {
        val original = registrationDraft()
        val draft =
            original.copy(
                taxBreakdown = TaxBreakdown(original.taxBreakdown.details.map { it.copy(tax = TaxType.IPSI, regimeCode = null) }),
            )
        val before = ValidationContext(assertIs<ValueResult.Valid<InvoiceIssueDate>>(InvoiceIssueDate.parse("31-12-2026")).value)
        val after = ValidationContext(assertIs<ValueResult.Valid<InvoiceIssueDate>>(InvoiceIssueDate.parse("01-01-2027")).value)
        val prepared =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.TEST, before),
            )
        assertEquals(
            ValidationSeverity.WARNING,
            prepared.report.issues
                .single()
                .severity,
        )
        val header = SubmissionHeader(draft.issuerName, draft.invoice.issuer)
        val records = listOf(SubmissionRecord.Registration(prepared.record))
        val earlyBatch = assertIs<SubmissionBatchBuildResult.Created>(FiscalSubmissionPreparation.prepareBatch(header, records, before))
        assertEquals(
            prepared.report.issues
                .single()
                .aeatCode,
            earlyBatch.report.issues
                .single()
                .aeatCode,
        )
        assertEquals(
            "records[0].draft.taxBreakdown.details[0].regimeCode",
            earlyBatch.report.issues
                .single()
                .fieldPath,
        )
        val lateBatch = assertIs<SubmissionBatchBuildResult.Invalid>(FiscalSubmissionPreparation.prepareBatch(header, records, after))
        assertEquals(
            ValidationSeverity.ERROR,
            lateBatch.report.issues
                .single()
                .severity,
        )
        val latePreparation =
            assertIs<RegistrationPreparationResult.Invalid>(
                FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.TEST, after),
            )
        assertEquals(
            ValidationSeverity.ERROR,
            latePreparation.report.issues
                .single()
                .severity,
        )
    }

    @Test
    fun preparesConsistentRegistrationHashXmlQrAndRequestArtifacts() {
        val prepared =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(registrationDraft(), QrEnvironment.TEST),
            )

        assertEquals("0A64AC627B8C784028B15E9954B1ADA48BDA3CD4F5365C883E6B10111B669CD9", prepared.record.hash)
        assertEquals(prepared.record.hash, prepared.nextChainState.hash)
        assertEquals(prepared.record.draft.invoice, prepared.nextChainState.invoice)
        assertContains(prepared.recordXml, "<NumSerieFactura>PREPARE-001</NumSerieFactura>")
        assertContains(prepared.recordXml, "<Huella>${prepared.record.hash}</Huella>")
        assertContains(prepared.recordXml, "<PrimerRegistro>S</PrimerRegistro>")
        assertEquals(
            "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?nif=89890001K&numserie=PREPARE-001&fecha=01-01-2024&importe=121.00",
            prepared.qr.url,
        )
        assertContains(prepared.batchXml, "<NombreRazon>Synthetic issuer</NombreRazon>")
        assertContains(prepared.batchXml, "<RegistroFactura>${withoutDeclaration(prepared.recordXml)}</RegistroFactura>")
        assertContains(prepared.soapEnvelope, "<soap:Body>${withoutDeclaration(prepared.batchXml)}</soap:Body>")
        assertEquals("RegistrationPreparationResult.Prepared(<redacted>)", prepared.toString())
    }

    @Test
    fun keepsTheQrEnvironmentExplicitWithoutChangingFiscalRecords() {
        val draft = registrationDraft()
        val test =
            assertIs<RegistrationPreparationResult.Prepared>(FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.TEST))
        val production =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.PRODUCTION),
            )

        assertEquals(test.record, production.record)
        assertEquals(test.soapEnvelope, production.soapEnvelope)
        assertTrue(production.qr.url.startsWith("https://www2.agenciatributaria.gob.es/wlpl/TIKE-CONT/ValidarQR?"))
    }

    @Test
    fun preparesCancellationFromThePersistedRegistrationHead() {
        val registration =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(registrationDraft(), QrEnvironment.TEST),
            )
        val cancellation =
            assertIs<CancellationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareCancellation(
                    cancellationDraft(registration),
                    SubmissionHeader("Synthetic issuer", registration.record.draft.invoice.issuer),
                ),
            )

        assertEquals("4997404528016711F5BEDADDB0269009D3AF11230AE01AB21BDE6BDD0963B0B6", cancellation.record.hash)
        assertEquals(registration.nextChainState, cancellation.record.draft.chainState)
        assertEquals(cancellation.record.hash, cancellation.nextChainState.hash)
        assertEquals(registration.record.draft.invoice, cancellation.nextChainState.invoice)
        assertContains(cancellation.recordXml, "<RegistroAnterior>")
        assertContains(cancellation.recordXml, "<Huella>${registration.record.hash}</Huella>")
        assertTrue("<PrimerRegistro>" !in cancellation.recordXml)
        assertContains(cancellation.batchXml, "<RegistroFactura>${withoutDeclaration(cancellation.recordXml)}</RegistroFactura>")
        assertContains(cancellation.soapEnvelope, "<soap:Body>${withoutDeclaration(cancellation.batchXml)}</soap:Body>")
        assertEquals("CancellationPreparationResult.Prepared(<redacted>)", cancellation.toString())
    }

    @Test
    fun returnsRecordAndQrIssuesWithoutProducingPartialPreparedArtifacts() {
        val draft = registrationDraft()
        val invalidRecord =
            assertIs<RegistrationPreparationResult.Invalid>(
                FiscalSubmissionPreparation.prepareRegistration(draft.copy(operationDescription = ""), QrEnvironment.TEST),
            )
        assertEquals(
            "VF-RECORD-001",
            invalidRecord.report.issues
                .single()
                .code,
        )
        assertEquals(
            "operationDescription",
            invalidRecord.report.issues
                .single()
                .fieldPath,
        )

        val invalidInvoiceNumber =
            assertIs<RegistrationPreparationResult.Invalid>(
                FiscalSubmissionPreparation.prepareRegistration(
                    draft.copy(invoice = draft.invoice.copy(number = value(InvoiceNumber.parse("SERIE-ñ")))),
                    QrEnvironment.TEST,
                ),
            )
        assertEquals(
            "1130",
            invalidInvoiceNumber.report.issues
                .single()
                .aeatCode,
        )
        assertEquals(
            "invoice.number",
            invalidInvoiceNumber.report.issues
                .single()
                .fieldPath,
        )
    }

    @Test
    fun returnsCancellationRecordAndHeaderIssues() {
        val registration =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(registrationDraft(), QrEnvironment.TEST),
            )
        val draft = cancellationDraft(registration)
        val header = SubmissionHeader("Synthetic issuer", registration.record.draft.invoice.issuer)
        val invalidRecord =
            assertIs<CancellationPreparationResult.Invalid>(
                FiscalSubmissionPreparation.prepareCancellation(draft.copy(system = draft.system.copy(producerName = "")), header),
            )
        assertEquals(
            "VF-RECORD-001",
            invalidRecord.report.issues
                .single()
                .code,
        )

        val invalidHeader =
            assertIs<CancellationPreparationResult.Invalid>(
                FiscalSubmissionPreparation.prepareCancellation(
                    draft,
                    header.copy(issuerTaxIdentifier = value(TaxIdentifier.parse("12345678Z"))),
                ),
            )
        assertEquals(
            "VF-BATCH-003",
            invalidHeader.report.issues
                .single()
                .code,
        )
    }

    @Test
    fun preparesExistingRecordsAsAnOrderedBatchAndRejectsForgedRecords() {
        val registration =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(registrationDraft(), QrEnvironment.TEST),
            )
        val header = SubmissionHeader("Synthetic issuer", registration.record.draft.invoice.issuer)
        val cancellation =
            assertIs<CancellationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareCancellation(cancellationDraft(registration), header),
            )
        val batch =
            assertIs<SubmissionBatchBuildResult.Created>(
                FiscalSubmissionPreparation.prepareBatch(
                    header,
                    listOf(SubmissionRecord.Registration(registration.record), SubmissionRecord.Cancellation(cancellation.record)),
                ),
            )
        assertTrue(batch.xml.indexOf("<RegistroAlta ") < batch.xml.indexOf("<RegistroAnulacion "))
        assertContains(batch.xml, withoutDeclaration(registration.recordXml))
        assertContains(batch.xml, withoutDeclaration(cancellation.recordXml))

        val invalid =
            assertIs<SubmissionBatchBuildResult.Invalid>(
                FiscalSubmissionPreparation.prepareBatch(
                    header,
                    listOf(SubmissionRecord.Registration(registration.record.copy(hash = "0".repeat(64)))),
                ),
            )
        assertEquals(
            "VF-BATCH-004",
            invalid.report.issues
                .single()
                .code,
        )
    }

    @Test
    fun callerCanReuseThePreparedBytesAfterItsDraftCollectionsChange() {
        val draft = registrationDraft()
        val details = draft.taxBreakdown.details.toMutableList()
        val prepared =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(draft.copy(taxBreakdown = TaxBreakdown(details)), QrEnvironment.TEST),
            )
        val persistedBytes = prepared.soapEnvelope.encodeToByteArray()
        val persistedHead = prepared.nextChainState

        details.clear()

        assertEquals(draft.taxBreakdown.details, prepared.record.draft.taxBreakdown.details)
        assertEquals(persistedHead, prepared.nextChainState)
        assertContentEquals(persistedBytes, prepared.soapEnvelope.encodeToByteArray())
    }

    private fun registrationDraft(): RegistroAltaDraft {
        val issuer = value(TaxIdentifier.parse("89890001K"))
        return RegistroAltaDraft(
            version = RecordVersion.V1_0,
            invoice = InvoiceIdentifier(issuer, value(InvoiceNumber.parse("PREPARE-001")), value(InvoiceIssueDate.parse("01-01-2024"))),
            issuerName = "Synthetic issuer",
            invoiceType = InvoiceType.F2,
            totalTax = value(FiscalAmount.parse("21.00")),
            totalAmount = value(FiscalAmount.parse("121.00")),
            operationDescription = "Synthetic preparation example",
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
            system =
                SistemaInformatico(
                    "Synthetic producer",
                    issuer,
                    "Preparation example",
                    "VF",
                    "1.0",
                    "synthetic-1",
                    true,
                    false,
                    false,
                ),
            generatedAt = value(RecordGenerationTimestamp.parse("2024-01-01T12:00:00+01:00")),
        )
    }

    private fun cancellationDraft(registration: RegistrationPreparationResult.Prepared): RegistroAnulacionDraft =
        RegistroAnulacionDraft(
            registration.record.draft.invoice,
            registration.nextChainState,
            registration.record.draft.system,
            value(RecordGenerationTimestamp.parse("2024-01-01T12:01:00+01:00")),
        )

    private fun withoutDeclaration(xml: String): String = xml.removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")

    private fun <T> value(result: ValueResult<T>): T = assertIs<ValueResult.Valid<T>>(result).value
}
