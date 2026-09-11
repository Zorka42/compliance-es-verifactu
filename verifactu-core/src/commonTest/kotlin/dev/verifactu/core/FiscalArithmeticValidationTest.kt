package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Exact boundary fixtures for archived Validaciones_Errores v1.2.2 sections 15.7, 15.8, 16 and 17. */
class FiscalArithmeticValidationTest {
    @Test
    fun chargedTaxIncludesBothToleranceBoundariesWithoutRounding() {
        listOf("11", "11.01", "21", "30.99", "31").forEach { tax ->
            assertTrue(issues(detail(tax = tax)).none { it.aeatCode == "1142" }, tax)
        }
        listOf("10.99", "31.01").forEach { tax ->
            assertEquals("1142", issues(detail(tax = tax)).single { it.aeatCode == "1142" }.aeatCode)
        }
        // 100.01 * 21% = 21.0021: rounding to cents would incorrectly accept 11.00.
        assertTrue(issues(detail(base = "100.01", tax = "11")).any { it.aeatCode == "1142" })
        assertTrue(issues(detail(base = "100.01", tax = "11.01")).none { it.aeatCode == "1142" })
        assertTrue(issues(detail(base = "-100.01", tax = "-11")).any { it.aeatCode == "1142" })
        assertTrue(issues(detail(base = "-100.01", tax = "-11.01")).none { it.aeatCode == "1142" })
    }

    @Test
    fun chargedTaxChecksEffectiveBaseSignsAndTreatsZeroAsNeutral() {
        assertTrue(issues(detail(base = "100", tax = "-1")).any { it.aeatCode == "1143" })
        assertTrue(issues(detail(base = "-100", tax = "1")).any { it.aeatCode == "1143" })
        listOf("0", "-0.00", "+000.00").forEach { zero ->
            assertTrue(issues(detail(base = "100", rate = "0", tax = zero)).isEmpty())
            assertTrue(issues(detail(base = zero, rate = "21", tax = zero)).isEmpty())
        }
        val cost = detail(base = "999", tax = "21").copy(costBase = amount("100"), regimeCode = "06")
        assertTrue(issues(cost).isEmpty())
        assertTrue(issues(cost.copy(chargedTax = amount("-1"))).any { it.aeatCode == "1140" })
        assertTrue(issues(cost.copy(chargedTax = amount("31.01"))).any { it.aeatCode == "1144" })
    }

    @Test
    fun arithmeticDoesNotOverflowAtSchemaLimitsOrRejectMalformedRatesByThrowing() {
        val large = detail(base = "999999999999.99", rate = "999.99", tax = "999999999999.99")
        assertTrue(issues(large).any { it.aeatCode == "1142" })
        assertTrue(
            issues(large.copy(taxableBase = amount("-999999999999.99"), chargedTax = amount("-999999999999.99"))).any {
                it.aeatCode ==
                    "1142"
            },
        )
        val exact = detail(base = "999999999999.99", rate = "001.00", tax = "9999999999.99")
        assertTrue(issues(exact).none { it.aeatCode == "1142" })
        listOf(null, "", "NaN", "-1", "1000", "21.001").forEach { rate ->
            assertTrue(issues(detail().copy(taxRate = rate)).isEmpty())
        }
        assertTrue(issues(detail().copy(chargedTax = null)).none { it.aeatCode == "1142" })
        val twelve = draft(large).copy(taxBreakdown = TaxBreakdown(List(12) { large }))
        assertTrue(fiscalArithmeticIssues(twelve).any { it.aeatCode == "2005" })
        assertTrue(fiscalArithmeticIssues(twelve.copy(taxBreakdown = TaxBreakdown(List(13) { large }))).isEmpty())
    }

    @Test
    fun onlyS1AndNonExemptRectificationKindsReceiveChargedTaxChecks() {
        val wrong = detail(tax = "-21")
        listOf(InvoiceType.R2, InvoiceType.R3).forEach { type ->
            assertTrue(fiscalArithmeticIssues(draft(wrong).copy(invoiceType = type)).none { it.aeatCode in setOf("1142", "1143") })
        }
        val difference =
            draft(
                wrong,
            ).copy(conditionalData = RegistrationConditionalData(rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE)))
        assertTrue(fiscalArithmeticIssues(difference).none { it.aeatCode in setOf("1142", "1143") })
        val replacement =
            difference.copy(
                conditionalData = RegistrationConditionalData(rectification = InvoiceRectification(RectificationType.REPLACEMENT)),
            )
        assertTrue(fiscalArithmeticIssues(replacement).any { it.aeatCode == "1143" })
        val operations =
            Qualification.entries.filter { it != Qualification.SUBJECT_NOT_EXEMPT }.map { TaxOperation.Qualified(it) } +
                TaxOperation.Exempt(Exemption.E1)
        operations.forEach { operation ->
            assertTrue(issues(wrong.copy(operation = operation)).none { it.aeatCode in setOf("1142", "1143") })
        }
    }

    @Test
    fun f2LimitUsesSignedSumExcludesSurchargeAndHonorsBothExceptions() {
        val lines = listOf(detail(base = "1500", tax = "5"), detail(base = "1500", tax = "5"))
        val boundary = draft(lines.first()).copy(invoiceType = InvoiceType.F2, taxBreakdown = TaxBreakdown(lines))
        assertFalse(fiscalArithmeticIssues(boundary).any { it.aeatCode == "1150" })
        val excess = boundary.copy(taxBreakdown = TaxBreakdown(lines + detail(base = "0.01", tax = "0")))
        assertTrue(fiscalArithmeticIssues(excess).any { it.aeatCode == "1150" })
        val surcharge = boundary.copy(taxBreakdown = TaxBreakdown(lines.map { it.copy(equivalenceSurcharge = amount("100")) }))
        assertFalse(fiscalArithmeticIssues(surcharge).any { it.aeatCode == "1150" })
        listOf(
            RegistrationConditionalData(taxationAgreementRegistrationNumber = "SYNTHETIC-AGREEMENT"),
            RegistrationConditionalData(recipientIdentificationExemption = RecipientIdentificationExemption.YES),
        ).forEach { conditional ->
            assertFalse(
                fiscalArithmeticIssues(excess.copy(conditionalData = conditional)).any {
                    it.aeatCode ==
                        "1150"
                },
            )
        }
        assertFalse(fiscalArithmeticIssues(excess.copy(invoiceType = InvoiceType.F1)).any { it.aeatCode == "1150" })
        val negative = draft(detail(base = "-9999", tax = "0")).copy(invoiceType = InvoiceType.F2)
        assertFalse(fiscalArithmeticIssues(negative).any { it.aeatCode == "1150" })
    }

    @Test
    fun totalsWarnBeyondTenEurosAndIncludeEveryDetailAndSurcharge() {
        val detail = detail().copy(equivalenceSurcharge = amount("5.20"))
        val value = draft(detail).copy(taxBreakdown = TaxBreakdown(listOf(detail, detail)))
        listOf("42.40" to "242.40", "62.40" to "262.40").forEach { (tax, total) ->
            assertTrue(fiscalArithmeticIssues(value.copy(totalTax = amount(tax), totalAmount = amount(total))).isEmpty())
        }
        listOf("42.39" to "242.39", "62.41" to "262.41").forEach { (tax, total) ->
            val report = ValidationReport(fiscalArithmeticIssues(value.copy(totalTax = amount(tax), totalAmount = amount(total))))
            assertEquals(setOf("2005", "2006"), report.issues.map { it.aeatCode }.toSet())
            assertTrue(report.isValid)
            assertTrue(report.issues.all { it.severity == ValidationSeverity.WARNING && it.source?.version?.startsWith("1.2.2") == true })
        }
        listOf("03", "05", "06", "08", "09").forEach { regime ->
            val mixed = value.copy(taxBreakdown = TaxBreakdown(listOf(detail, detail.copy(regimeCode = regime))))
            assertTrue(fiscalArithmeticIssues(mixed).none { it.aeatCode in setOf("2005", "2006") })
        }
        val created =
            assertIs<RecordCreationResult.Created<RegistroAlta>>(
                FiscalRecordFactory.createRegistration(draft(detail()).copy(totalTax = amount("100"))),
            )
        assertEquals(
            "2006",
            created.report.issues
                .single()
                .aeatCode,
        )
    }

    private fun issues(detail: TaxBreakdownDetail): List<ValidationIssue> =
        fiscalArithmeticIssues(draft(detail)).filter {
            it.aeatCode !in
                setOf("2005", "2006")
        }

    private fun detail(
        base: String = "100",
        rate: String = "21",
        tax: String = "21",
    ): TaxBreakdownDetail =
        TaxBreakdownDetail(
            TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
            amount(base),
            TaxType.IVA,
            "01",
            rate,
            chargedTax = amount(tax),
        )

    private fun draft(detail: TaxBreakdownDetail): RegistroAltaDraft =
        RegistroAltaDraft(
            RecordVersion.V1_0,
            InvoiceIdentifier(
                nif(),
                assertIs<ValueResult.Valid<InvoiceNumber>>(InvoiceNumber.parse("SYNTHETIC-1")).value,
                assertIs<ValueResult.Valid<InvoiceIssueDate>>(InvoiceIssueDate.parse("10-09-2026")).value,
            ),
            "Synthetic issuer",
            InvoiceType.F1,
            amount("21"),
            amount("121"),
            "Synthetic arithmetic fixture",
            TaxBreakdown(listOf(detail)),
            ChainState.FirstRecord,
            SistemaInformatico("Synthetic producer", nif(), "Example", "VF", "1", "fixture", true, false, false),
            assertIs<ValueResult.Valid<RecordGenerationTimestamp>>(RecordGenerationTimestamp.parse("2026-09-10T12:00:00+02:00")).value,
            RegistrationConditionalData(recipients = listOf(FiscalParty("Synthetic recipient", FiscalPartyIdentifier.SpanishNif(nif())))),
        )

    private fun nif(): TaxIdentifier = assertIs<ValueResult.Valid<TaxIdentifier>>(TaxIdentifier.parse("89890001K")).value

    private fun amount(value: String): FiscalAmount = assertIs<ValueResult.Valid<FiscalAmount>>(FiscalAmount.parse(value)).value
}
