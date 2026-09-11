package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Source acceptance tables: archived AEAT validations v1.2.2, section 3.1.3.15 and error catalogue. */
class TaxConditionalValidationTest {
    @Test
    fun baselineIsLocallyValidForEveryTaxFamily() {
        (TaxType.entries + null).forEach { tax ->
            val detail = detail().copy(tax = tax, regimeCode = if (tax == TaxType.OTHER) null else "01")
            assertTrue(report(detail).isValid, "$tax: ${report(detail).issues}")
        }
    }

    @Test
    fun regimePresenceAndMembershipDependOnTaxFamily() {
        listOf(null, TaxType.IVA, TaxType.IGIC).forEach { tax ->
            assertAeatIssue("1245", detail().copy(tax = tax, regimeCode = null), "regimeCode")
        }
        assertAeatIssue("1260", detail().copy(tax = TaxType.OTHER), "regimeCode")
        assertAeatIssue("1246", detail().copy(regimeCode = "21"), "regimeCode")
        assertNoTaxIssues(detail().copy(tax = TaxType.IGIC, regimeCode = "21"))
        assertTrue(report(detail().copy(regimeCode = "99")).issues.any { it.code == "VF-RECORD-003" })
    }

    @Test
    fun ipsiTransitionRemainsAWarningWithoutAeatReceiptDateContext() {
        // Section 15.6 changes server severity on 2027-01-01. generatedAt is not AEAT's receipt date.
        listOf("01", "08", "11", "18", "19", "20").forEach { regime ->
            assertNoTaxIssues(detail().copy(tax = TaxType.IPSI, regimeCode = regime))
        }
        listOf(null, "02", "21").forEach { regime ->
            listOf("2026-12-31T23:59:59+01:00", "2027-01-01T00:00:00+01:00").forEach { generatedAt ->
                val draft = draft(detail().copy(tax = TaxType.IPSI, regimeCode = regime)).copy(generatedAt = timestamp(generatedAt))
                val issue = RegistroAltaValidator.validate(draft).issues.single()
                assertEquals("VF-TAX-IPSI-TRANSITION", issue.code)
                assertEquals(ValidationSeverity.WARNING, issue.severity)
                assertEquals(null, issue.aeatCode)
                assertTrue(issue.message.contains("2027-01-01"))
                assertTrue(issue.message.contains("does not establish acceptance"))
                assertTrue(assertNotNull(issue.source).section.contains("15.6"))
                val created = assertIs<RecordCreationResult.Created<RegistroAlta>>(FiscalRecordFactory.createRegistration(draft))
                assertEquals(listOf(issue), created.report.issues)
                assertFails { (created.report.issues as MutableList<ValidationIssue>).clear() }
                assertEquals(listOf(issue), created.report.issues)
            }
        }
    }

    @Test
    fun subjectNonExemptRequiresBothRateAndTaxWithOrWithoutCostBase() {
        listOf(detail(), detail().copy(regimeCode = "06", costBase = amount("80"))).forEach { original ->
            listOf(original.copy(taxRate = null), original.copy(chargedTax = null)).forEach { invalid ->
                val issue = report(invalid).issues.single { it.code == "VF-RECORD-007" }
                assertEquals(if (original.costBase == null) "1208" else "1209", issue.aeatCode)
                assertEquals("3.1.3.15.7", issue.source?.section)
            }
            assertNoTaxIssues(original)
        }
        val ipsi = detail().copy(tax = TaxType.IPSI, costBase = amount("80"), chargedTax = null)
        assertEquals(null, report(ipsi).issues.single { it.code == "VF-RECORD-007" }.aeatCode)
    }

    @Test
    fun costBaseRequiresRegime06OrIpsiOrOtherTax() {
        listOf(null, TaxType.IVA, TaxType.IGIC).forEach { tax ->
            assertAeatIssue("1257", detail().copy(tax = tax, costBase = amount("80")), "costBase")
        }
        assertNoTaxIssues(detail().copy(tax = TaxType.IPSI, costBase = amount("80")))
        assertNoTaxIssues(detail().copy(tax = TaxType.OTHER, regimeCode = null, costBase = amount("80")))
    }

    @Test
    fun nonSubjectIvaDisallowsTaxFieldsButOtherTaxesMayReportZeroTax() {
        listOf(Qualification.NOT_SUBJECT, Qualification.NOT_SUBJECT_LOCATION_RULES).forEach { qualification ->
            listOf(null, TaxType.IVA).forEach { tax ->
                val iva =
                    detail().copy(
                        tax = tax,
                        operation = TaxOperation.Qualified(qualification),
                        taxRate = "0",
                        chargedTax = amount("0"),
                    )
                assertTrue(report(iva).issues.any { it.aeatCode == "1237" })
                assertNoTaxIssues(iva.copy(taxRate = null, chargedTax = null))
            }
            listOf(TaxType.IPSI, TaxType.IGIC, TaxType.OTHER).forEach { tax ->
                val other =
                    detail().copy(
                        tax = tax,
                        regimeCode = if (tax == TaxType.OTHER) null else "01",
                        operation = TaxOperation.Qualified(qualification),
                        taxRate = "0",
                        chargedTax = amount("-0.00"),
                    )
                assertNoTaxIssues(other)
                assertAeatIssue("1207", other.copy(chargedTax = amount("-1")), "chargedTax")
                assertAeatIssue("1281", other.copy(equivalenceSurchargeRate = "0", equivalenceSurcharge = amount("0")))
            }
        }
    }

    @Test
    fun reverseChargeRequiresZeroValuesAndAllowedInvoiceTypes() {
        val reverse =
            detail().copy(
                operation = TaxOperation.Qualified(Qualification.SUBJECT_REVERSE_CHARGE),
                taxRate = "000.00",
                chargedTax = amount("+0.00"),
            )
        assertNoTaxIssues(reverse)
        assertTrue(report(reverse.copy(taxRate = null)).issues.any { it.aeatCode == "1198" })
        assertTrue(report(reverse.copy(chargedTax = amount("1"))).issues.any { it.aeatCode == "1198" })
        listOf(InvoiceType.F2, InvoiceType.R5).forEach { type ->
            assertTrue(RegistroAltaValidator.validate(draft(reverse).copy(invoiceType = type)).issues.any { it.aeatCode == "1197" })
        }
        assertAeatIssue("1281", reverse.copy(equivalenceSurchargeRate = "0", equivalenceSurcharge = amount("0")))
    }

    @Test
    fun surchargeRateAndAmountAreAnOptionalPairOnlyForS1() {
        assertAeatIssue("1284", detail().copy(equivalenceSurchargeRate = "5.2"))
        assertAeatIssue("1284", detail().copy(equivalenceSurcharge = amount("5.2")))
        assertNoTaxIssues(detail().copy(equivalenceSurchargeRate = "5.2", equivalenceSurcharge = amount("5.2")))
        val exempt = exempt(Exemption.E1).copy(equivalenceSurchargeRate = "0", equivalenceSurcharge = amount("0"))
        assertAeatIssue("1281", exempt)
        assertTrue(report(exempt).issues.any { it.aeatCode == "1238" })
    }

    @Test
    fun exemptionListsAndRegime01AreTaxSpecific() {
        listOf(Exemption.E7, Exemption.E8).forEach { exemption ->
            listOf(null, TaxType.IVA).forEach { tax ->
                assertAeatIssue("1182", exempt(exemption).copy(tax = tax), "operation")
            }
            assertNoTaxIssues(exempt(exemption).copy(tax = TaxType.IGIC))
        }
        listOf(Exemption.E2, Exemption.E3).forEach { exemption ->
            listOf(null, TaxType.IVA, TaxType.IGIC).forEach { tax ->
                assertAeatIssue("1199", exempt(exemption).copy(tax = tax), "operation")
                assertNoTaxIssues(exempt(exemption).copy(tax = tax, regimeCode = "02"))
            }
        }
        assertNoTaxIssues(exempt(Exemption.E2).copy(tax = TaxType.IPSI))
        assertNoTaxIssues(exempt(Exemption.E8).copy(tax = TaxType.OTHER, regimeCode = null))
    }

    @Test
    fun ivaE5RequiresOtherRecipientIdentification() {
        assertTrue(report(exempt(Exemption.E5)).issues.any { it.aeatCode == "1289" && it.fieldPath == "conditionalData.recipients" })
        val ivaDraft = draft(exempt(Exemption.E5)).withRecipients(otherRecipient())
        assertTrue(RegistroAltaValidator.validate(ivaDraft).isValid)
        assertNoTaxIssues(exempt(Exemption.E5).copy(tax = TaxType.IGIC))
    }

    @Test
    fun ivaRatesUseExactDecimalsAndOperationDateIntervals() {
        listOf("0", "4", "10", "21", "021.00", "4.").forEach { rate ->
            assertNoTaxIssues(detail().copy(taxRate = rate))
        }
        listOf("1", "7", "7.51", "22", "999.99").forEach { rate ->
            assertAeatIssue("1124", detail().copy(taxRate = rate), "taxRate")
        }
        val windows =
            listOf(
                Triple("5", "01-07-2022", "30-09-2024"),
                Triple("2", "01-10-2024", "31-12-2024"),
                Triple("7.50", "01-10-2024", "31-12-2024"),
            )
        windows.forEach { (rate, start, end) ->
            listOf(start, end).forEach { date -> assertNoTaxIssues(draft(detail().copy(taxRate = rate)).withOperationDate(date)) }
        }
        listOf(
            "5" to "30-06-2022",
            "5" to "01-10-2024",
            "2" to "30-09-2024",
            "2" to "01-01-2025",
            "7.5" to "30-09-2024",
            "7.5" to "01-01-2025",
        ).forEach { (rate, date) ->
            val issues = RegistroAltaValidator.validate(draft(detail().copy(taxRate = rate)).withOperationDate(date)).issues
            assertTrue(issues.any { it.code == "VF-TAX-RATE-DATE" }, "$rate on $date")
        }
        assertNoTaxIssues(detail().copy(tax = TaxType.IGIC, taxRate = "7"))
    }

    @Test
    fun missingOperationDateFallsBackToIssueDateWithoutUsingGenerationTimestamp() {
        val historic = draft(detail().copy(taxRate = "5")).copy(invoice = invoice("30-09-2024"))
        assertNoTaxIssues(historic)
        assertNoTaxIssues(historic.copy(generatedAt = timestamp("2030-01-01T00:00:00+01:00")))
        assertTrue(RegistroAltaValidator.validate(historic.withOperationDate("01-10-2024")).issues.any { it.code == "VF-TAX-RATE-DATE" })
    }

    @Test
    fun invalidRateLexicalFormsKeepSchemaIssues() {
        listOf("", "abc", "-1", "21.000").forEach { value ->
            assertTrue(report(detail().copy(taxRate = value)).issues.any { it.code == "VF-RECORD-004" })
            assertTrue(
                report(detail().copy(equivalenceSurchargeRate = value, equivalenceSurcharge = amount("0"))).issues.any {
                    it.code ==
                        "VF-RECORD-005"
                },
            )
        }
    }

    @Test
    fun surchargeRatesMatchTheIvaRateAndHistoricalInterval() {
        val cases =
            listOf(
                SurchargeCase("21", "5.2", "10-09-2026", "1162"),
                SurchargeCase("21", "1.75", "10-09-2026", "1162"),
                SurchargeCase("10", "1.4", "10-09-2026", "1163"),
                SurchargeCase("4", "0.5", "10-09-2026", "1164"),
                SurchargeCase("5", "0.5", "01-07-2022", "1167"),
                SurchargeCase("5", "0.5", "31-12-2022", "1167"),
                SurchargeCase("5", "0.62", "01-01-2023", "1168"),
                SurchargeCase("5", "0.62", "30-09-2024", "1168"),
                SurchargeCase("2", "0.26", "01-10-2024", "1166"),
                SurchargeCase("2", "0.26", "31-12-2024", "1166"),
                SurchargeCase("7.5", "1", "01-10-2024", "1169"),
                SurchargeCase("7.5", "1", "31-12-2024", "1169"),
            )
        cases.forEach { case ->
            val original = detail().copy(taxRate = case.rate, equivalenceSurchargeRate = case.surcharge, equivalenceSurcharge = amount("0"))
            assertNoTaxIssues(draft(original).withOperationDate(case.date))
            val invalid = original.copy(equivalenceSurchargeRate = "0")
            assertTrue(
                RegistroAltaValidator.validate(draft(invalid).withOperationDate(case.date)).issues.any { it.aeatCode == case.error },
                "$case",
            )
        }
        assertAeatIssue(
            "1127",
            detail().copy(equivalenceSurchargeRate = "0.01", equivalenceSurcharge = amount("0")),
            "equivalenceSurchargeRate",
        )
        assertAeatIssue("1124", detail().copy(taxRate = "12", equivalenceSurchargeRate = "0", equivalenceSurcharge = amount("0")))
    }

    @Test
    fun zeroRateSurchargeDistinguishesTheDocumentedIntervalAndUnresolvedLaterRule() {
        val zero = detail().copy(taxRate = "0", equivalenceSurchargeRate = "0", equivalenceSurcharge = amount("0"))
        listOf("01-01-2023", "30-09-2024").forEach { date ->
            assertNoTaxIssues(draft(zero).withOperationDate(date))
            assertTrue(
                RegistroAltaValidator.validate(draft(zero.copy(equivalenceSurchargeRate = "0.26")).withOperationDate(date)).issues.any {
                    it.aeatCode ==
                        "1165"
                },
            )
        }
        // PDF 15.3 documents the earlier interval; properties 1170 specifies a subsequent 0.26 rule.
        listOf("0", "0.26").forEach { surcharge ->
            val report =
                RegistroAltaValidator.validate(
                    draft(zero.copy(equivalenceSurchargeRate = surcharge)).withOperationDate("01-10-2024"),
                )
            val issue = report.issues.single()
            assertTrue(report.isValid)
            assertEquals("VF-TAX-ZERO-RATE-UNRESOLVED", issue.code)
            assertEquals(ValidationSeverity.WARNING, issue.severity)
            assertEquals(null, issue.aeatCode)
            assertTrue(assertNotNull(issue.source).section.contains("1170"))
        }
    }

    @Test
    fun regimes02Through08EnforceTheirQualifiedOrExemptOperationChoices() {
        val rejected = listOf("02" to "1286", "04" to "1201", "08" to "1252")
        rejected.forEach { (regime, code) -> assertAeatIssue(code, detail().copy(regimeCode = regime), "operation") }
        listOf("02", "03", "04").forEach { regime -> assertNoTaxIssues(exempt(Exemption.E1).copy(regimeCode = regime)) }
        assertNoTaxIssues(detail().copy(regimeCode = "03"))
        assertAeatIssue("1200", nonSubject().copy(regimeCode = "03"), "operation")
        val reverse =
            detail().copy(
                operation = TaxOperation.Qualified(Qualification.SUBJECT_REVERSE_CHARGE),
                taxRate = "0",
                chargedTax = amount("0"),
            )
        assertNoTaxIssues(reverse.copy(regimeCode = "04"))
        assertNoTaxIssues(nonSubject(Qualification.NOT_SUBJECT_LOCATION_RULES).copy(regimeCode = "08"))
        assertNoTaxIssues(detail().copy(regimeCode = "07"))
        listOf(Exemption.E1, Exemption.E6).forEach { assertNoTaxIssues(exempt(it).copy(regimeCode = "07")) }
        listOf(Exemption.E2, Exemption.E3, Exemption.E4, Exemption.E5).forEach {
            assertAeatIssue("1203", exempt(it).copy(regimeCode = "07"), "operation")
        }
        listOf(reverse, nonSubject(), nonSubject(Qualification.NOT_SUBJECT_LOCATION_RULES)).forEach {
            assertAeatIssue("1203", it.copy(regimeCode = "07"), "operation")
        }
    }

    @Test
    fun regime06RequiresCostBaseAndExcludesSimplifiedAndReplacementInvoiceTypes() {
        assertAeatIssue("1202", detail().copy(regimeCode = "06"))
        val group = detail().copy(regimeCode = "06", costBase = amount("80"))
        assertNoTaxIssues(group)
        listOf(InvoiceType.F2, InvoiceType.F3, InvoiceType.R5).forEach { type ->
            assertTrue(RegistroAltaValidator.validate(draft(group).copy(invoiceType = type)).issues.any { it.aeatCode == "1202" })
        }
    }

    @Test
    fun regime10RequiresN1F1AndNifRecipients() {
        assertAeatIssue("1205", detail().copy(regimeCode = "10"))
        val collection = draft(nonSubject().copy(regimeCode = "10"))
        assertNoTaxIssues(collection)
        assertTrue(RegistroAltaValidator.validate(collection.copy(invoiceType = InvoiceType.F3)).issues.any { it.aeatCode == "1205" })
        assertTrue(RegistroAltaValidator.validate(collection.withRecipients(otherRecipient())).issues.any { it.aeatCode == "1205" })
    }

    @Test
    fun regime11Requires21PercentOnlyForIva() {
        assertNoTaxIssues(detail().copy(regimeCode = "11"))
        assertAeatIssue("1206", detail().copy(regimeCode = "11", taxRate = "10"), "taxRate")
        assertAeatIssue("1206", exempt(Exemption.E1).copy(regimeCode = "11"), "taxRate")
        assertNoTaxIssues(detail().copy(tax = TaxType.IGIC, regimeCode = "11", taxRate = "7"))
    }

    @Test
    fun regime14RequiresLaterOperationDatePublicAuthorityNifsAndAllowedInvoiceTypes() {
        val pending = draft(detail().copy(regimeCode = "14")).withRecipients(nifRecipient("P0000000A"))
        assertTrue(RegistroAltaValidator.validate(pending).issues.any { it.aeatCode == "1147" })
        listOf("09-09-2026", "10-09-2026").forEach { date ->
            assertTrue(RegistroAltaValidator.validate(pending.withOperationDate(date)).issues.any { it.aeatCode == "1147" })
        }
        val valid = pending.withOperationDate("11-09-2026")
        assertNoTaxIssues(valid)
        listOf("P", "Q", "S", "V").forEach { prefix -> assertNoTaxIssues(valid.withRecipients(nifRecipient("${prefix}0000000A"))) }
        listOf(nifRecipient(), otherRecipient()).forEach { recipient ->
            assertTrue(RegistroAltaValidator.validate(valid.withRecipients(recipient)).issues.any { it.aeatCode == "1149" })
        }
        listOf(InvoiceType.F2, InvoiceType.F3, InvoiceType.R5).forEach { type ->
            assertTrue(RegistroAltaValidator.validate(valid.copy(invoiceType = type)).issues.any { it.aeatCode == "1148" })
        }
    }

    @Test
    fun igicRegime20RequiresN2ButIvaRegime20DoesNot() {
        assertNoTaxIssues(detail().copy(regimeCode = "20"))
        assertAeatIssue("1293", detail().copy(tax = TaxType.IGIC, regimeCode = "20"), "operation")
        assertNoTaxIssues(nonSubject(Qualification.NOT_SUBJECT_LOCATION_RULES).copy(tax = TaxType.IGIC, regimeCode = "20"))
    }

    @Test
    fun operationAfterIssueDateRequiresRegime14Or15ForEachIvaOrIgicDetail() {
        listOf(null, TaxType.IVA, TaxType.IGIC).forEach { tax ->
            val normal = draft(detail().copy(tax = tax))
            listOf("09-09-2026", "10-09-2026").forEach { date -> assertNoTaxIssues(normal.withOperationDate(date)) }
            val invalid = RegistroAltaValidator.validate(normal.withOperationDate("11-09-2026"))
            val issue = invalid.issues.single { it.aeatCode == "1146" }
            assertEquals("taxBreakdown.details[0].regimeCode", issue.fieldPath)
            assertEquals("3.1.3.1; errores.properties 1146", issue.source?.section)
            assertNoTaxIssues(draft(detail().copy(tax = tax, regimeCode = "15")).withOperationDate("11-09-2026"))
        }
        // The PDF condition is explicitly scoped to IVA/default/IGIC details, not IPSI/other taxes.
        listOf(TaxType.IPSI, TaxType.OTHER).forEach { tax ->
            val detail = detail().copy(tax = tax, regimeCode = if (tax == TaxType.OTHER) null else "01")
            assertNoTaxIssues(draft(detail).withOperationDate("11-09-2026"))
        }
        val mixed =
            draft(detail().copy(regimeCode = "14"))
                .withRecipients(nifRecipient("P0000000A"))
                .withOperationDate("11-09-2026")
        val eligible = mixed.copy(taxBreakdown = TaxBreakdown(listOf(detail().copy(regimeCode = "14"), detail().copy(regimeCode = "15"))))
        assertNoTaxIssues(eligible)
        val ineligible = mixed.copy(taxBreakdown = TaxBreakdown(listOf(detail().copy(regimeCode = "14"), detail())))
        val issue = RegistroAltaValidator.validate(ineligible).issues.single { it.aeatCode == "1146" }
        assertEquals("taxBreakdown.details[1].regimeCode", issue.fieldPath)
    }

    private data class SurchargeCase(
        val rate: String,
        val surcharge: String,
        val date: String,
        val error: String,
    )

    private fun assertAeatIssue(
        code: String,
        detail: TaxBreakdownDetail,
        field: String? = null,
    ) {
        val issue = report(detail).issues.single { it.aeatCode == code }
        assertEquals(ValidationSeverity.ERROR, issue.severity)
        assertFalse(report(detail).isValid)
        assertNotNull(issue.source)
        if (field != null) assertEquals("taxBreakdown.details[0].$field", issue.fieldPath)
    }

    private fun assertNoTaxIssues(detail: TaxBreakdownDetail) = assertNoTaxIssues(draft(detail))

    private fun assertNoTaxIssues(draft: RegistroAltaDraft) {
        val issues = RegistroAltaValidator.validate(draft).issues
        assertTrue(issues.none { it.code.startsWith("VF-TAX-") || it.fieldPath.startsWith("taxBreakdown") }, "$issues")
    }

    private fun report(detail: TaxBreakdownDetail): ValidationReport = RegistroAltaValidator.validate(draft(detail))

    private fun detail(): TaxBreakdownDetail =
        TaxBreakdownDetail(
            TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
            amount("100"),
            TaxType.IVA,
            "01",
            "21",
            chargedTax = amount("21"),
        )

    private fun exempt(exemption: Exemption): TaxBreakdownDetail =
        detail().copy(operation = TaxOperation.Exempt(exemption), taxRate = null, chargedTax = null)

    private fun nonSubject(qualification: Qualification = Qualification.NOT_SUBJECT): TaxBreakdownDetail =
        detail().copy(operation = TaxOperation.Qualified(qualification), taxRate = null, chargedTax = null)

    private fun draft(detail: TaxBreakdownDetail): RegistroAltaDraft =
        RegistroAltaDraft(
            RecordVersion.V1_0,
            invoice(),
            "Synthetic issuer",
            InvoiceType.F1,
            amount("21"),
            amount("121"),
            "Synthetic conditional validation example",
            TaxBreakdown(listOf(detail)),
            ChainState.FirstRecord,
            SistemaInformatico("Synthetic producer", nif("89890001K"), "Example SIF", "VF", "1", "fixture", true, false, false),
            timestamp("2026-09-10T12:00:00+02:00"),
            RegistrationConditionalData(recipients = listOf(nifRecipient())),
        )

    private fun RegistroAltaDraft.withOperationDate(value: String): RegistroAltaDraft =
        copy(conditionalData = conditionalData.copy(operationDate = date(value)))

    private fun RegistroAltaDraft.withRecipients(vararg recipients: FiscalParty): RegistroAltaDraft =
        copy(conditionalData = conditionalData.copy(recipients = recipients.toList()))

    private fun nifRecipient(value: String = "89890001K"): FiscalParty =
        FiscalParty("Synthetic recipient", FiscalPartyIdentifier.SpanishNif(nif(value)))

    private fun otherRecipient(): FiscalParty =
        FiscalParty("Synthetic recipient", FiscalPartyIdentifier.Other("FR", OtherPartyIdentifierType.PASSPORT, "SYNTHETIC"))

    private fun invoice(issueDate: String = "10-09-2026"): InvoiceIdentifier =
        InvoiceIdentifier(
            nif("89890001K"),
            assertIs<ValueResult.Valid<InvoiceNumber>>(InvoiceNumber.parse("SYNTHETIC-1")).value,
            date(issueDate),
        )

    private fun nif(value: String): TaxIdentifier = assertIs<ValueResult.Valid<TaxIdentifier>>(TaxIdentifier.parse(value)).value

    private fun amount(value: String): FiscalAmount = assertIs<ValueResult.Valid<FiscalAmount>>(FiscalAmount.parse(value)).value

    private fun date(value: String): InvoiceIssueDate = assertIs<ValueResult.Valid<InvoiceIssueDate>>(InvoiceIssueDate.parse(value)).value

    private fun timestamp(value: String): RecordGenerationTimestamp =
        assertIs<ValueResult.Valid<RecordGenerationTimestamp>>(RecordGenerationTimestamp.parse(value)).value
}
