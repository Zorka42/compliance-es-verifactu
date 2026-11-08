package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegistrationDateValidationTest {
    @Test
    fun issueDateUsesOnlyExplicitAeatCurrentDateAndPropagatesThroughFactory() {
        val context = ValidationContext(aeatCurrentDate = date("08-11-2026"))
        for (issueDate in listOf("07-11-2026", "08-11-2026", "09-11-2026")) {
            val draft = registrationDraft().let { it.copy(invoice = it.invoice.copy(issueDate = date(issueDate))) }
            val report = RegistroAltaValidator.validate(draft, context)
            if (issueDate == "09-11-2026") {
                val issue = report.issues.single()
                assertDateError(issue, "1112", "invoice.issueDate", "3.1.3.1")
                assertEquals(report, assertIs<RecordCreationResult.Invalid>(FiscalRecordFactory.createRegistration(draft, context)).report)
            } else {
                assertTrue(report.issues.isEmpty())
                assertIs<RecordCreationResult.Created<RegistroAlta>>(FiscalRecordFactory.createRegistration(draft, context))
            }
        }
    }

    @Test
    fun unknownCurrentDateIsInspectableAndReceiptAndGenerationDatesCannotReplaceIt() {
        val base = registrationDraft()
        for (generated in listOf("2024-10-28T12:00:00+01:00", "2030-01-01T00:00:00-12:00")) {
            val draft = base.copy(generatedAt = value(RecordGenerationTimestamp.parse(generated)))
            for (context in listOf(ValidationContext(), ValidationContext(aeatReceiptDate = date("01-01-2020")))) {
                val result = assertIs<RecordCreationResult.Created<RegistroAlta>>(FiscalRecordFactory.createRegistration(draft, context))
                val issue = result.report.issues.single()
                assertEquals("VF-DATE-CONTEXT-UNKNOWN", issue.code)
                assertEquals("validationContext.aeatCurrentDate", issue.fieldPath)
                assertEquals(ValidationSeverity.WARNING, issue.severity)
                assertNull(issue.aeatCode)
                assertTrue(issue.source!!.section.contains("3.1.3.7"))
            }
        }
        val unknown = assertIs<RecordCreationResult.Created<RegistroAlta>>(FiscalRecordFactory.createRegistration(base))
        val known =
            assertIs<RecordCreationResult.Created<RegistroAlta>>(
                FiscalRecordFactory.createRegistration(base, ValidationContext(aeatCurrentDate = date("08-11-2026"))),
            )
        assertEquals(unknown.record, known.record)
        assertEquals(unknown.nextChainState, known.nextChainState)
    }

    @Test
    fun operationLowerBoundIsInclusiveAcrossOrdinaryAndLeapYears() {
        val cases =
            listOf(
                listOf("08-11-2026", "07-11-2006", "08-11-2006", "09-11-2006"),
                listOf("29-02-2024", "28-02-2004", "29-02-2004", "01-03-2004"),
                listOf("01-01-2027", "31-12-2006", "01-01-2007", "02-01-2007"),
                listOf("01-03-2100", "29-02-2080", "01-03-2080", "02-03-2080"),
                listOf("29-02-2420", "28-02-2400", "29-02-2400", "01-03-2400"),
            )
        for (case in cases) {
            val current = case[0]
            val before = case[1]
            val equal = case[2]
            val after = case[3]
            for (operation in listOf(before, equal, after)) {
                val issues = operationIssues(current, operation)
                if (operation == before) {
                    assertDateError(issues.single(), "1134", "conditionalData.operationDate", "3.1.3.7")
                } else {
                    assertTrue(issues.isEmpty(), "$current / $operation: $issues")
                }
            }
        }
        assertTrue(operationIssues("01-01-0001", "01-01-0001").isEmpty())
    }

    @Test
    fun unresolvedLeapDayLowerBoundWarnsOnlyOnTheDisputedDay() {
        assertDateError(operationIssues("29-02-2120", "27-02-2100").single(), "1134", "conditionalData.operationDate", "3.1.3.7")
        val issue = operationIssues("29-02-2120", "28-02-2100").single()
        assertEquals("VF-DATE-OPERATION-LEAP-BOUND-UNRESOLVED", issue.code)
        assertEquals(ValidationSeverity.WARNING, issue.severity)
        assertNull(issue.aeatCode)
        assertTrue(operationIssues("29-02-2120", "01-03-2100").isEmpty())
    }

    @Test
    fun operationUpperBoundRetainsTheDisputedBandWithoutInventingAnAeatError() {
        for (operation in listOf("07-11-2027", "08-11-2027")) {
            assertTrue(operationIssues("08-11-2026", operation).isEmpty())
        }
        for (operation in listOf("09-11-2027", "31-12-2027")) {
            val issue = operationIssues("08-11-2026", operation).single()
            assertEquals("VF-DATE-OPERATION-UPPER-BOUND-UNRESOLVED", issue.code)
            assertEquals(ValidationSeverity.WARNING, issue.severity)
            assertNull(issue.aeatCode)
        }
        assertDateError(operationIssues("08-11-2026", "01-01-2028").single(), "1125", "conditionalData.operationDate", "3.1.3.7")
        assertTrue(operationIssues("31-12-2026", "31-12-2027").isEmpty())
        assertTrue(operationIssues("29-02-2024", "28-02-2025").isEmpty())
        assertEquals("VF-DATE-OPERATION-UPPER-BOUND-UNRESOLVED", operationIssues("29-02-2024", "01-03-2025").single().code)
        assertTrue(operationIssues("31-12-9999", "31-12-9999").isEmpty())
    }

    @Test
    fun futureOperationChecksEveryIvaAndIgicDetailIncludingImplicitIva() {
        val base = registrationDraft()
        for (tax in TaxType.entries + null) {
            for (regime in listOf(null, "01", "14", "15")) {
                for (operation in listOf("07-11-2026", "08-11-2026", "09-11-2026")) {
                    assertFutureOperation(tax, regime, operation)
                }
            }
        }
        val mixed =
            base.copy(
                conditionalData = base.conditionalData.copy(operationDate = date("09-11-2026")),
                taxBreakdown =
                    TaxBreakdown(
                        listOf(
                            base.taxBreakdown.details
                                .single()
                                .copy(regimeCode = "14"),
                            base.taxBreakdown.details.single(),
                        ),
                    ),
            )
        assertEquals(
            "taxBreakdown.details[1].regimeCode",
            RegistroAltaValidator
                .validate(mixed, ValidationContext(aeatCurrentDate = date("08-11-2026")))
                .issues
                .single {
                    it.aeatCode ==
                        "1173"
                }.fieldPath,
        )
    }

    private fun assertFutureOperation(
        tax: TaxType?,
        regime: String?,
        operation: String,
    ) {
        val base = registrationDraft()
        val draft =
            base.copy(
                conditionalData = base.conditionalData.copy(operationDate = date(operation)),
                taxBreakdown = TaxBreakdown(base.taxBreakdown.details.map { it.copy(tax = tax, regimeCode = regime) }),
            )
        val errors =
            RegistroAltaValidator
                .validate(draft, ValidationContext(aeatCurrentDate = date("08-11-2026")))
                .issues
                .filter { it.aeatCode == "1173" }
        val expected =
            operation == "09-11-2026" &&
                (tax == null || tax == TaxType.IVA || tax == TaxType.IGIC) &&
                regime !in listOf("14", "15")
        assertEquals(if (expected) 1 else 0, errors.size, "$tax / $regime / $operation")
        if (expected) assertDateError(errors.single(), "1173", "taxBreakdown.details[0].regimeCode", "3.1.3.7")
    }

    private fun operationIssues(
        current: String,
        operation: String,
    ): List<ValidationIssue> {
        val base = registrationDraft()
        val draft =
            base.copy(
                conditionalData = base.conditionalData.copy(operationDate = date(operation)),
                taxBreakdown = TaxBreakdown(base.taxBreakdown.details.map { it.copy(tax = TaxType.IPSI, regimeCode = "01") }),
            )
        return RegistroAltaValidator
            .validate(draft, ValidationContext(aeatCurrentDate = date(current)))
            .issues
            .filter { it.fieldPath == "conditionalData.operationDate" }
    }

    private fun assertDateError(
        issue: ValidationIssue,
        code: String,
        field: String,
        section: String,
    ) {
        assertEquals("VF-DATE-$code", issue.code)
        assertEquals(code, issue.aeatCode)
        assertEquals(field, issue.fieldPath)
        assertEquals(ValidationSeverity.ERROR, issue.severity)
        assertTrue(issue.source!!.section.contains(section))
        assertEquals("1.2.2 (2026-04-08)", issue.source.version)
    }

    private fun date(text: String): InvoiceIssueDate = value(InvoiceIssueDate.parse(text))

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
