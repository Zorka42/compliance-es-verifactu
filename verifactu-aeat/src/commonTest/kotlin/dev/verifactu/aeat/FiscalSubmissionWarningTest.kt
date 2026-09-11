package dev.verifactu.aeat

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
import dev.verifactu.core.RegistroAnulacionDraft
import dev.verifactu.core.SistemaInformatico
import dev.verifactu.core.TaxBreakdown
import dev.verifactu.core.TaxBreakdownDetail
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.TaxOperation
import dev.verifactu.core.TaxType
import dev.verifactu.core.ValidationIssue
import dev.verifactu.core.ValidationReport
import dev.verifactu.core.ValidationSeverity
import dev.verifactu.core.ValueResult
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.xml.SubmissionBatchBuildResult
import dev.verifactu.xml.SubmissionHeader
import dev.verifactu.xml.SubmissionRecord
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FiscalSubmissionWarningTest {
    @Test
    fun retainsRecordWarningsInPreparedArtifactsWithoutRejectingTheRecord() {
        val draft = registrationDraft()
        val created = assertIs<RecordCreationResult.Created<RegistroAlta>>(FiscalRecordFactory.createRegistration(draft))
        val prepared =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.TEST),
            )

        assertTrue(prepared.report.isValid)
        assertEquals(created.report, prepared.report)
        val warning = prepared.report.issues.single()
        assertEquals("VF-TAX-IPSI-TRANSITION", warning.code)
        assertEquals("taxBreakdown.details[0].regimeCode", warning.fieldPath)
        assertEquals(ValidationSeverity.WARNING, warning.severity)
        assertContains(prepared.soapEnvelope, "<Impuesto>02</Impuesto>")
        assertEquals("RegistrationPreparationResult.Prepared(<redacted>)", prepared.toString())
    }

    @Test
    fun preservesWarningsWithTheCorrectBatchRecordIndexAndStillPreparesCancellation() {
        val registration =
            assertIs<RegistrationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareRegistration(registrationDraft(), QrEnvironment.TEST),
            )
        val header = SubmissionHeader("Synthetic issuer", registration.record.draft.invoice.issuer)
        val cancellationDraft =
            RegistroAnulacionDraft(
                registration.record.draft.invoice,
                registration.nextChainState,
                registration.record.draft.system,
                value(RecordGenerationTimestamp.parse("2024-01-01T12:01:00+01:00")),
            )
        val createdCancellation =
            assertIs<RecordCreationResult.Created<*>>(FiscalRecordFactory.createCancellation(cancellationDraft))
        val cancellation =
            assertIs<CancellationPreparationResult.Prepared>(
                FiscalSubmissionPreparation.prepareCancellation(cancellationDraft, header),
            )
        val batch =
            assertIs<SubmissionBatchBuildResult.Created>(
                FiscalSubmissionPreparation.prepareBatch(
                    header,
                    listOf(SubmissionRecord.Cancellation(cancellation.record), SubmissionRecord.Registration(registration.record)),
                ),
            )

        assertTrue(cancellation.report.isValid)
        assertEquals(createdCancellation.report, cancellation.report)
        assertTrue(cancellation.report.issues.isEmpty())
        assertTrue(batch.report.isValid)
        assertEquals(
            registration.report.issues.map { it.copy(fieldPath = "records[1].draft.${it.fieldPath}") },
            batch.report.issues,
        )
        assertEquals("SubmissionBatchBuildResult.Created(xml=<redacted>, soapEnvelope=<redacted>)", batch.toString())
    }

    @Test
    fun keepsWarningsAlongsideErrorsWhenTheBatchOrDraftCannotBePrepared() {
        val draft = registrationDraft()
        val created = assertIs<RecordCreationResult.Created<RegistroAlta>>(FiscalRecordFactory.createRegistration(draft))
        val invalidBatch =
            assertIs<SubmissionBatchBuildResult.Invalid>(
                FiscalSubmissionPreparation.prepareBatch(
                    SubmissionHeader("", draft.invoice.issuer),
                    listOf(SubmissionRecord.Registration(created.record)),
                ),
            )
        val invalidDraft =
            assertIs<RegistrationPreparationResult.Invalid>(
                FiscalSubmissionPreparation.prepareRegistration(draft.copy(operationDescription = ""), QrEnvironment.TEST),
            )

        assertFalse(invalidBatch.report.isValid)
        assertEquals(
            setOf("VF-BATCH-002", "VF-TAX-IPSI-TRANSITION"),
            invalidBatch.report.issues
                .map { it.code }
                .toSet(),
        )
        assertFalse(invalidDraft.report.isValid)
        assertEquals(
            setOf("VF-RECORD-001", "VF-TAX-IPSI-TRANSITION"),
            invalidDraft.report.issues
                .map { it.code }
                .toSet(),
        )
        assertEquals(
            ValidationSeverity.WARNING,
            invalidBatch.report.issues
                .single { it.code == "VF-TAX-IPSI-TRANSITION" }
                .severity,
        )
    }

    @Test
    fun cannotMutateWarningsOrFailuresInReportsReturnedByTheBatchBuilder() {
        val draft = registrationDraft()
        val records =
            listOf("WARNING-001", "WARNING-002").map { number ->
                val recordDraft = draft.copy(invoice = draft.invoice.copy(number = value(InvoiceNumber.parse(number))))
                val record =
                    assertIs<RecordCreationResult.Created<RegistroAlta>>(
                        FiscalRecordFactory.createRegistration(recordDraft),
                    ).record
                SubmissionRecord.Registration(record)
            }
        val header = SubmissionHeader("Synthetic issuer", draft.invoice.issuer)
        val created = assertIs<SubmissionBatchBuildResult.Created>(FiscalSubmissionPreparation.prepareBatch(header, records))
        val invalid =
            assertIs<SubmissionBatchBuildResult.Invalid>(
                FiscalSubmissionPreparation.prepareBatch(header.copy(issuerName = ""), records),
            )
        val invalidCount =
            assertIs<SubmissionBatchBuildResult.Invalid>(
                FiscalSubmissionPreparation.prepareBatch(header.copy(issuerName = ""), emptyList()),
            )

        assertEquals(2, created.report.issues.size)
        listOf(created.report, invalid.report, invalidCount.report).forEach(::assertImmutableIssues)
    }

    private fun assertImmutableIssues(report: ValidationReport) {
        val original = report.issues.toList()
        val clearFailure = assertFails { (report.issues as MutableList<ValidationIssue>).clear() }
        val setFailure =
            assertFails { (report.issues as MutableList<ValidationIssue>)[0] = original[0].copy(severity = ValidationSeverity.ERROR) }

        assertTrue(clearFailure is ClassCastException || clearFailure is UnsupportedOperationException)
        assertTrue(setFailure is ClassCastException || setFailure is UnsupportedOperationException)
        assertEquals(original, report.issues)
    }

    private fun registrationDraft(): RegistroAltaDraft {
        val issuer = value(TaxIdentifier.parse("89890001K"))
        return RegistroAltaDraft(
            RecordVersion.V1_0,
            InvoiceIdentifier(issuer, value(InvoiceNumber.parse("WARNING-001")), value(InvoiceIssueDate.parse("01-01-2024"))),
            "Synthetic issuer",
            InvoiceType.F2,
            value(FiscalAmount.parse("21.00")),
            value(FiscalAmount.parse("121.00")),
            "Synthetic warning propagation fixture",
            TaxBreakdown(
                listOf(
                    TaxBreakdownDetail(
                        TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                        value(FiscalAmount.parse("100.00")),
                        tax = TaxType.IPSI,
                        taxRate = "21",
                        chargedTax = value(FiscalAmount.parse("21.00")),
                    ),
                ),
            ),
            ChainState.FirstRecord,
            SistemaInformatico("Synthetic producer", issuer, "Warning example", "VF", "1.0", "synthetic-1", true, false, false),
            value(RecordGenerationTimestamp.parse("2024-01-01T12:00:00+01:00")),
        )
    }

    private fun <T> value(result: ValueResult<T>): T = assertIs<ValueResult.Valid<T>>(result).value
}
