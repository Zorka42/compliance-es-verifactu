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
import dev.verifactu.core.RegistroAnulacion
import dev.verifactu.core.RegistroAnulacionDraft
import dev.verifactu.core.SistemaInformatico
import dev.verifactu.core.TaxBreakdown
import dev.verifactu.core.TaxBreakdownDetail
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.TaxOperation
import dev.verifactu.core.TaxType
import dev.verifactu.core.ValueResult
import dev.verifactu.xml.SubmissionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AeatResponseCorrelationTest {
    @Test
    fun matchesInvoiceAndOperationInSubmissionOrderWithoutChangingUnknownOrDuplicateStates() {
        val submitted = listOf(registration("S-1"), cancellation("S-1"), registration("S-2"))
        val unknown =
            line(
                "S-2",
            ).copy(status = AeatRecordStatus.UNKNOWN_STATE, declaredStatus = AeatRecordStatus.UNKNOWN_STATE, rawStatus = "Future")
        val duplicate =
            line("S-1", AeatOperationType.CANCELLATION).copy(
                status = AeatRecordStatus.DUPLICATE,
                declaredStatus = AeatRecordStatus.REJECTED,
                duplicate = AeatDuplicateRecord("earlier", AeatDuplicateStatus.CANCELLED, "Anulada"),
            )
        val accepted = line("S-1")
        val result =
            assertIs<AeatResponseCorrelationResult.Matched>(
                AeatResponseCorrelation.correlate(submitted, response(listOf(unknown, duplicate, accepted))),
            )

        assertEquals(listOf(accepted, duplicate, unknown), result.lines)
        assertEquals(AeatRecordStatus.UNKNOWN_STATE, result.lines[2].status)
        assertEquals(AeatDuplicateStatus.CANCELLED, result.lines[1].duplicate?.status)
    }

    @Test
    fun rejectsUnrelatedInvoiceTriplesAndMissingOrUnexpectedLines() {
        val original = line("JAVA-001")
        val identity = original.invoice!!
        val unrelated =
            listOf(
                identity.copy(number = "SAMPLE-001"),
                identity.copy(issuer = "OTHER-ISSUER"),
                identity.copy(issueDate = "02-01-2024"),
            )
        unrelated.forEach {
            val result = mismatch(listOf(registration("JAVA-001")), listOf(original.copy(invoice = it)))

            assertTrue(
                result.issues.any { issue ->
                    issue.code == AeatCorrelationIssueCode.MISSING_RESPONSE_LINE &&
                        issue.submittedIndex == 0
                },
            )
            assertTrue(
                result.issues.any { issue ->
                    issue.code == AeatCorrelationIssueCode.UNEXPECTED_RESPONSE_LINE &&
                        issue.responseIndex == 0
                },
            )
        }
        assertEquals(
            AeatCorrelationIssueCode.MISSING_RESPONSE_LINE,
            mismatch(listOf(registration("S-1")), emptyList()).issues.single().code,
        )
        assertTrue(mismatch(emptyList(), emptyList()).issues.any { it.code == AeatCorrelationIssueCode.EMPTY_SUBMISSION })
    }

    @Test
    fun rejectsRepeatedKeysAndIncompleteResponseIdentity() {
        val record = registration("S-1")
        assertTrue(
            mismatch(listOf(record, record), listOf(line("S-1"))).issues.any {
                it.code ==
                    AeatCorrelationIssueCode.AMBIGUOUS_SUBMISSION_KEY
            },
        )
        assertTrue(
            mismatch(listOf(record), listOf(line("S-1"), line("S-1"))).issues.any {
                it.code ==
                    AeatCorrelationIssueCode.AMBIGUOUS_RESPONSE_KEY
            },
        )
        assertTrue(
            mismatch(listOf(record), listOf(line("S-1").copy(invoice = null))).issues.any {
                it.code ==
                    AeatCorrelationIssueCode.MISSING_RESPONSE_IDENTITY
            },
        )
        assertTrue(
            mismatch(listOf(record), listOf(line("S-1").copy(operation = null))).issues.any {
                it.code ==
                    AeatCorrelationIssueCode.UNSUPPORTED_OPERATION
            },
        )
        assertTrue(
            mismatch(listOf(record), listOf(line("S-1", AeatOperationType.UNKNOWN_STATE))).issues.any {
                it.code == AeatCorrelationIssueCode.UNSUPPORTED_OPERATION
            },
        )
    }

    @Test
    fun rejectsAffirmativeAndUnknownFlagsThatSubmittedModelsCannotRepresent() {
        val record = registration("S-1")
        val original = line("S-1")
        val operation = original.operation!!
        val unsupported =
            listOf(
                operation.copy(subsanacion = "S"),
                operation.copy(rechazoPrevio = "S"),
                operation.copy(sinRegistroPrevio = "S"),
                operation.copy(subsanacion = "Future"),
            )
        unsupported.forEach {
            assertTrue(
                mismatch(listOf(record), listOf(original.copy(operation = it))).issues.any { issue ->
                    issue.code ==
                        AeatCorrelationIssueCode.UNSUPPORTED_OPERATION_FLAGS
                },
            )
        }
        assertIs<AeatResponseCorrelationResult.Matched>(
            AeatResponseCorrelation.correlate(
                listOf(record),
                response(
                    listOf(original.copy(operation = operation.copy(subsanacion = "N", rechazoPrevio = "N", sinRegistroPrevio = "N"))),
                ),
            ),
        )
        assertTrue(
            mismatch(listOf(record), listOf(original.copy(operation = operation.copy(rawType = "Anulacion")))).issues.any {
                it.code == AeatCorrelationIssueCode.UNSUPPORTED_OPERATION
            },
        )
    }

    @Test
    fun snapshotsResponseOrderAndKeepsDiagnosticSummariesFreeOfIdentifiers() {
        val privateNumber = "PRIVATE-MARKER"
        val lines = mutableListOf(line(privateNumber))
        val matched =
            assertIs<AeatResponseCorrelationResult.Matched>(
                AeatResponseCorrelation.correlate(listOf(registration(privateNumber)), response(lines)),
            )
        lines.clear()
        assertEquals(
            privateNumber,
            matched.lines
                .single()
                .invoice
                ?.number,
        )
        assertFalse(matched.toString().contains(privateNumber))
        assertFalse(mismatch(listOf(registration(privateNumber)), listOf(line("OTHER"))).toString().contains(privateNumber))
    }

    private fun mismatch(
        submitted: List<SubmissionRecord>,
        lines: List<AeatResponseLine>,
    ): AeatResponseCorrelationResult.Mismatch =
        assertIs<AeatResponseCorrelationResult.Mismatch>(AeatResponseCorrelation.correlate(submitted, response(lines)))

    private fun response(lines: List<AeatResponseLine>): AeatSubmissionResponse =
        AeatSubmissionResponse(AeatSubmissionStatus.ACCEPTED, "synthetic", 0, lines)

    private fun line(
        number: String,
        operation: AeatOperationType = AeatOperationType.REGISTRATION,
    ): AeatResponseLine =
        AeatResponseLine(
            status = AeatRecordStatus.ACCEPTED,
            invoice = AeatInvoiceReference("89890001K", number, "01-01-2024"),
            operation = AeatResponseOperation(operation, if (operation == AeatOperationType.REGISTRATION) "Alta" else "Anulacion"),
            rawStatus = "Correcto",
        )

    private fun registration(number: String): SubmissionRecord.Registration {
        val invoice = invoice(number)
        val draft =
            RegistroAltaDraft(
                RecordVersion.V1_0,
                invoice,
                "Example issuer",
                InvoiceType.F2,
                value(FiscalAmount.parse("21.00")),
                value(FiscalAmount.parse("121.00")),
                "Synthetic operation",
                TaxBreakdown(
                    listOf(
                        TaxBreakdownDetail(
                            TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                            value(FiscalAmount.parse("100.00")),
                            TaxType.IVA,
                            "01",
                            "21",
                            chargedTax = value(FiscalAmount.parse("21.00")),
                        ),
                    ),
                ),
                ChainState.FirstRecord,
                system(invoice.issuer),
                value(RecordGenerationTimestamp.parse("2024-01-01T12:00:00+01:00")),
            )
        return SubmissionRecord.Registration(
            assertIs<RecordCreationResult.Created<RegistroAlta>>(FiscalRecordFactory.createRegistration(draft)).record,
        )
    }

    private fun cancellation(number: String): SubmissionRecord.Cancellation {
        val invoice = invoice(number)
        val draft =
            RegistroAnulacionDraft(
                invoice,
                ChainState.FirstRecord,
                system(invoice.issuer),
                value(RecordGenerationTimestamp.parse("2024-01-01T12:00:00+01:00")),
            )
        return SubmissionRecord.Cancellation(
            assertIs<RecordCreationResult.Created<RegistroAnulacion>>(FiscalRecordFactory.createCancellation(draft)).record,
        )
    }

    private fun invoice(number: String): InvoiceIdentifier =
        InvoiceIdentifier(
            value(TaxIdentifier.parse("89890001K")),
            value(InvoiceNumber.parse(number)),
            value(InvoiceIssueDate.parse("01-01-2024")),
        )

    private fun system(issuer: TaxIdentifier): SistemaInformatico =
        SistemaInformatico("Example producer", issuer, "Correlation test", "VF", "1", "test-1", true, false, false)

    private fun <T> value(result: ValueResult<T>): T = assertIs<ValueResult.Valid<T>>(result).value
}
