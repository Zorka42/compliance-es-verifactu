package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RegistrationDateValidationTest {
    @Test
    fun registrationDateCutoffIsInclusiveAndHasStructuredSourceEvidence() {
        for (date in listOf("27-10-2024", "28-10-2024", "29-10-2024")) {
            val base = registrationDraft()
            val draft = base.copy(invoice = base.invoice.copy(issueDate = (InvoiceIssueDate.parse(date) as ValueResult.Valid).value))
            val report = RegistroAltaValidator.validate(draft)
            val result = FiscalRecordFactory.createRegistration(draft)
            if (date == "27-10-2024") {
                val issue = report.issues.single { it.code == "VF-DATE-1152" }
                assertEquals("invoice.issueDate", issue.fieldPath)
                assertEquals(ValidationSeverity.ERROR, issue.severity)
                assertEquals("1152", issue.aeatCode)
                assertTrue(issue.source!!.section.contains("3.1.3.1"))
                assertIs<RecordCreationResult.Invalid>(result)
            } else {
                assertTrue(report.isValid)
                assertIs<RecordCreationResult.Created<RegistroAlta>>(result)
            }
        }
    }

    @Test
    fun historicalCalendarReferencesAndCancellationTargetsRemainUsable() {
        val historical = registrationDraft().invoice.copy(issueDate = (InvoiceIssueDate.parse("01-01-2024") as ValueResult.Valid).value)
        val base = registrationDraft()
        val chain = ChainState.PreviousRecord(historical, "A".repeat(64))
        assertTrue(
            RegistroAltaValidator
                .validate(
                    base.copy(
                        invoiceType = InvoiceType.F3,
                        chainState = chain,
                        conditionalData =
                            base.conditionalData.copy(
                                replacedInvoices = listOf(historical),
                                recipients =
                                    listOf(
                                        FiscalParty("Synthetic recipient", FiscalPartyIdentifier.SpanishNif(base.invoice.issuer)),
                                    ),
                            ),
                    ),
                ).isValid,
        )
        assertIs<RecordCreationResult.Created<RegistroAnulacion>>(
            FiscalRecordFactory.createCancellation(RegistroAnulacionDraft(historical, chain, base.system, base.generatedAt)),
        )
    }

    private fun registrationDraft(): RegistroAltaDraft =
        RegistroAltaDraft(
            version = RecordVersion.V1_0,
            invoice =
                InvoiceIdentifier(
                    value(TaxIdentifier.parse("89890001K")),
                    value(InvoiceNumber.parse("DATE-001")),
                    value(InvoiceIssueDate.parse("28-10-2024")),
                ),
            issuerName = "Synthetic issuer",
            invoiceType = InvoiceType.F2,
            totalTax = value(FiscalAmount.parse("21.00")),
            totalAmount = value(FiscalAmount.parse("121.00")),
            operationDescription = "Synthetic operation",
            taxBreakdown =
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
            chainState = ChainState.FirstRecord,
            system =
                SistemaInformatico(
                    "Synthetic producer",
                    value(TaxIdentifier.parse("89890001K")),
                    "Test",
                    "VT",
                    "1",
                    "date-test",
                    true,
                    false,
                    false,
                ),
            generatedAt = value(RecordGenerationTimestamp.parse("2024-10-28T12:00:00+01:00")),
        )

    private fun <T> value(result: ValueResult<T>): T = (result as ValueResult.Valid).value
}
