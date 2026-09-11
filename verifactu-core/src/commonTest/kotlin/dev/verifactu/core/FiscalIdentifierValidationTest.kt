package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FiscalIdentifierValidationTest {
    @Test
    fun verifiesTheInteriorDniExampleAndLeadingZeroCheckCharacters() {
        listOf("12345678Z", "00000000T", "00000001R", "00000014Z", "00000022E", "89890001K").forEach { value ->
            assertEquals(emptyList(), FiscalIdentifierValidator.validateTaxIdentifier(taxId(value)).issues, value)
        }
        listOf("12345678A", "00000000R", "00000022T").forEach { value ->
            val report = FiscalIdentifierValidator.validateTaxIdentifier(taxId(value))
            assertEquals("1123", report.issues.single().aeatCode)
            assertEquals("taxIdentifier", report.issues.single().fieldPath)
            assertTrue(
                report.issues
                    .single()
                    .source!!
                    .document
                    .startsWith("Ministerio del Interior"),
            )
        }
    }

    @Test
    fun appliesDifferentNumericWeightsToTheThreeNiePrefixes() {
        // The numeric components are 0, 10,000,000 and 20,000,000, with remainders 0, 14 and 5.
        listOf("X0000000T", "Y0000000Z", "Z0000000M", "X1234567L", "Y1234567X", "Z1234567R").forEach { value ->
            assertTrue(FiscalIdentifierValidator.validateTaxIdentifier(taxId(value)).isValid, value)
        }
        listOf("Y0000000T", "Z0000000T", "X1234567X").forEach { value ->
            assertFalse(FiscalIdentifierValidator.validateTaxIdentifier(taxId(value)).isValid, value)
        }
    }

    @Test
    fun rejectsUnsupportedNifShapesWithoutChangingStructuralParsing() {
        listOf("ABCDEFGHJ", "B12A45678", "T1234567A", "12345678?", "１２３４５６７８Z", "A\uD83D\uDE001234567").forEach { value ->
            val parsed = assertIs<ValueResult.Valid<TaxIdentifier>>(TaxIdentifier.parse(value)).value
            assertFalse(FiscalIdentifierValidator.validateTaxIdentifier(parsed).isValid, value)
        }
    }

    @Test
    fun preservesBoeAlphanumericAssignedNifsAndReportsTheUnverifiedCheckCharacter() {
        listOf("K1234567A", "LAB12CD3Z", "MABCDEFGZ").forEach { value ->
            val report = FiscalIdentifierValidator.validateTaxIdentifier(taxId(value))
            assertTrue(report.isValid)
            val warning = report.issues.single()
            assertEquals("VF-ID-CHECKSUM-UNVERIFIED", warning.code)
            assertEquals(ValidationSeverity.WARNING, warning.severity)
            assertEquals("Articles 19.2 and 20.2", warning.source?.section)
        }
    }

    @Test
    fun checksEveryPublishedEntityPrefixWithoutInventingItsChecksum() {
        "ABCDEFGHJNPQRSUVW".forEach { prefix ->
            listOf("${prefix}12345670", "${prefix}1234567A").forEach { value ->
                val report = FiscalIdentifierValidator.validateTaxIdentifier(taxId(value))
                assertTrue(report.isValid, value)
                assertEquals("VF-ID-CHECKSUM-UNVERIFIED", report.issues.single().code)
                assertEquals(
                    "Articles 2-5",
                    report.issues
                        .single()
                        .source
                        ?.section,
                )
            }
        }
        assertFalse(FiscalIdentifierValidator.validateTaxIdentifier(taxId("B1234567?")).isValid)
    }

    @Test
    fun requiresANaturalPersonNifForNotRegisteredIdentification() {
        listOf("12345678Z", "X0000000T", "Y0000000Z", "Z0000000M").forEach { value ->
            assertEquals(emptyList(), notRegistered(value).issues, value)
        }
        assertEquals("VF-ID-CHECKSUM-UNVERIFIED", notRegistered("KAB12CD3Z").issues.single().code)
        assertEquals("1131", notRegistered("B12345678").issues.single().aeatCode)
        listOf("abc", "12345678A", "12345678z", "K12345678", "123 5678Z").forEach { value ->
            assertEquals("1290", notRegistered(value).issues.single().aeatCode, value)
        }
    }

    @Test
    fun enforcesThePublishedInvoiceCharacterRepertoireExhaustively() {
        val prohibited = setOf(34, 39, 60, 61, 62)
        (32..126).forEach { code ->
            val number = invoiceNumber("A${code.toChar()}1")
            val report = FiscalIdentifierValidator.validateInvoiceNumber(number)
            assertEquals(code !in prohibited, report.isValid, "ASCII $code")
            if (code in prohibited) {
                assertEquals("1130", report.issues.single().aeatCode)
                assertEquals("number", report.issues.single().fieldPath)
            }
        }
    }

    @Test
    fun rejectsServiceInvalidUnicodeAndWhitespaceWhileStructuralValuesRemainAvailable() {
        listOf("INV-é", "INV-ñ", "INV-Ж", "INV-\uD83D\uDE00", "INV\r1", "INV\n1", "INV\t1", "INV\u007F1").forEach { value ->
            val number = invoiceNumber(value)
            assertEquals(value, number.value)
            assertEquals(
                "1130",
                FiscalIdentifierValidator
                    .validateInvoiceNumber(number)
                    .issues
                    .single()
                    .aeatCode,
            )
        }
        assertTrue(FiscalIdentifierValidator.validateInvoiceNumber(invoiceNumber("INV  & / 1")).isValid)
        assertTrue(FiscalIdentifierValidator.validateInvoiceNumber(invoiceNumber("A".repeat(60))).isValid)
    }

    @Test
    fun acceptsTheFullVatCountryLengthTableIncludingAlternativeLengths() {
        val cases =
            mapOf(
                "DE" to listOf(9),
                "AT" to listOf(9),
                "BE" to listOf(10),
                "CY" to listOf(9),
                "CZ" to listOf(8, 9, 10),
                "HR" to listOf(11),
                "DK" to listOf(8),
                "SK" to listOf(10),
                "SI" to listOf(8),
                "EE" to listOf(9),
                "FI" to listOf(8),
                "FR" to listOf(11),
                "EL" to listOf(9),
                "GB" to listOf(5, 9, 12),
                "XI" to listOf(5, 9, 12),
                "NL" to listOf(12),
                "HU" to listOf(8),
                "IT" to listOf(11),
                "IE" to listOf(8, 9),
                "LV" to listOf(11),
                "LT" to listOf(9, 12),
                "LU" to listOf(8),
                "MT" to listOf(8),
                "PL" to listOf(10),
                "PT" to listOf(9),
                "SE" to listOf(12),
                "BG" to listOf(9, 10),
                "RO" to (2..10).toList(),
            )
        cases.forEach { (prefix, lengths) ->
            val date = if (prefix == "GB") "31-12-2020" else "01-02-2021"
            lengths.forEach { length ->
                assertTrue(vat(prefix + "1".repeat(length), date = date).isValid, "$prefix/$length")
            }
            assertFalse(vat(prefix + "1".repeat(lengths.min() - 1), date = date).isValid, prefix)
            assertFalse(vat(prefix + "1".repeat(lengths.max() + 1), date = date).isValid, prefix)
        }
    }

    @Test
    fun rejectsUnsupportedVatPrefixesWrongCharacterClassesAndRomanianLeadingZeros() {
        listOf(
            "DE12345678A",
            "DE１２３４５６７８９",
            "de123456789",
            "FRabc12345678",
            "US123456789",
            "ES12345678Z",
            "GR123456789",
            "RO01",
            "RO0123456789",
            "RO1",
            "LT1234567890",
            "XI123456",
            "DE123 45678",
        ).forEach { value ->
            assertEquals("1103", vat(value).issues.first().aeatCode, value)
        }
        listOf("ATU12345678", "FRAB123456789", "NL123456789B01", "IE1234567AB", "CY12345678A", "XIAB123").forEach { value ->
            assertTrue(vat(value).isValid, value)
        }
    }

    @Test
    fun matchesVatPrefixesToSchemaCountryCodesIncludingGreekAndNorthernIrishAliases() {
        assertTrue(vat("FRAB123456789", "FR").isValid)
        assertTrue(vat("EL123456789", "GR").isValid)
        assertTrue(vat("XI123456789", "GB").isValid)
        assertEquals(ValidationSeverity.ASSUMPTION, vat("EL123456789", "GR").issues.single().severity)
        assertEquals("VF-ID-COUNTRY-PREFIX-ASSUMPTION", vat("XI123456789", "GB").issues.single().code)
        assertTrue(vat("GB123456789", "GB", "31-12-2020").isValid)
        assertTrue(vat("EL123456789").isValid)
        assertEquals(emptyList(), vat("EL123456789").issues)
        listOf("DE", "fr", "").forEach { country ->
            val issue = vat("FRAB123456789", country).issues.single()
            assertEquals("1122", issue.aeatCode)
            assertEquals("identifier.countryCode", issue.fieldPath)
        }
    }

    @Test
    fun respectsEveryInclusiveBritishVatTransitionBoundary() {
        val dates = listOf("31-12-2020", "01-01-2021", "31-01-2021", "01-02-2021")
        val expectedGb = listOf(true, true, true, false)
        val expectedXi = listOf(false, true, true, true)
        dates.forEachIndexed { index, date ->
            assertEquals(expectedGb[index], vat("GB123456789", date = date).isValid, date)
            assertEquals(expectedXi[index], vat("XI123456789", date = date).isValid, date)
        }
        assertEquals("1254", vat("XI123456789", date = "31-12-2020").issues.single().aeatCode)
        assertEquals("1255", vat("GB123456789", date = "01-02-2021").issues.single().aeatCode)
    }

    @Test
    fun keepsReportsImmutableAndDoesNotEchoIdentifiersIntoIssues() {
        val value = taxId("K1234567A")
        val report = FiscalIdentifierValidator.validateTaxIdentifier(value)
        assertFails { (report.issues as MutableList<ValidationIssue>).clear() }
        assertFalse(report.toString().contains(value.value))
        assertEquals(report, FiscalIdentifierValidator.validateTaxIdentifier(value))
    }

    @Test
    fun reportsEveryNestedInvoiceAndPartyPathForRecordWiring() {
        val source = draft()
        val invalidInvoice = source.invoice.copy(number = invoiceNumber("INVALID=1"))
        val invalidParty = FiscalParty("Recipient", FiscalPartyIdentifier.SpanishNif(taxId("12345678A")))
        val value =
            source.copy(
                invoice = invalidInvoice,
                chainState = ChainState.PreviousRecord(invalidInvoice, "A".repeat(64)),
                system = source.system.copy(producerTaxIdentifier = taxId("12345678A")),
                conditionalData =
                    RegistrationConditionalData(
                        recipients = listOf(invalidParty),
                        thirdParty = invalidParty,
                        replacedInvoices = listOf(invalidInvoice),
                        rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE, listOf(invalidInvoice)),
                    ),
            )
        assertEquals(
            listOf(
                "invoice.number",
                "system.producerTaxIdentifier",
                "chainState.invoice.number",
                "conditionalData.recipients[0].identifier.value",
                "conditionalData.thirdParty.identifier.value",
                "conditionalData.replacedInvoices[0].number",
                "conditionalData.rectification.rectifiedInvoices[0].number",
            ),
            registrationIdentifierIssues(value).map { it.fieldPath },
        )
        val cancellation = RegistroAnulacionDraft(invalidInvoice, value.chainState, value.system, value.generatedAt)
        assertEquals(
            listOf("cancelledInvoice.number", "system.producerTaxIdentifier", "chainState.invoice.number"),
            cancellationIdentifierIssues(cancellation).map { it.fieldPath },
        )
    }

    @Test
    fun usesTheExplicitOperationDateBeforeTheIssueDateForVatPrefixChecks() {
        val source = draft()
        val party = FiscalParty("Recipient", FiscalPartyIdentifier.Other("GB", OtherPartyIdentifierType.VAT_IDENTIFIER, "GB123456789"))
        val withoutDate = source.copy(conditionalData = RegistrationConditionalData(recipients = listOf(party)))
        assertEquals("1255", registrationIdentifierIssues(withoutDate).single().aeatCode)
        val withDate = withoutDate.copy(conditionalData = withoutDate.conditionalData.copy(operationDate = issueDate("31-01-2021")))
        assertEquals(emptyList(), registrationIdentifierIssues(withDate))
    }

    private fun notRegistered(value: String): ValidationReport =
        FiscalIdentifierValidator.validatePartyIdentifier(
            FiscalPartyIdentifier.Other("ES", OtherPartyIdentifierType.NOT_REGISTERED, value),
            issueDate("01-02-2021"),
        )

    private fun vat(
        value: String,
        country: String? = null,
        date: String = "01-02-2021",
    ): ValidationReport =
        FiscalIdentifierValidator.validatePartyIdentifier(
            FiscalPartyIdentifier.Other(country, OtherPartyIdentifierType.VAT_IDENTIFIER, value),
            issueDate(date),
        )

    private fun taxId(value: String): TaxIdentifier = assertIs<ValueResult.Valid<TaxIdentifier>>(TaxIdentifier.parse(value)).value

    private fun invoiceNumber(value: String): InvoiceNumber = assertIs<ValueResult.Valid<InvoiceNumber>>(InvoiceNumber.parse(value)).value

    private fun issueDate(value: String): InvoiceIssueDate =
        assertIs<ValueResult.Valid<InvoiceIssueDate>>(InvoiceIssueDate.parse(value)).value

    private fun amount(value: String): FiscalAmount = assertIs<ValueResult.Valid<FiscalAmount>>(FiscalAmount.parse(value)).value

    private fun draft(): RegistroAltaDraft {
        val nif = taxId("12345678Z")
        return RegistroAltaDraft(
            version = RecordVersion.V1_0,
            invoice = InvoiceIdentifier(nif, invoiceNumber("INV-1"), issueDate("01-01-2026")),
            issuerName = "Synthetic issuer",
            invoiceType = InvoiceType.F1,
            totalTax = amount("21"),
            totalAmount = amount("121"),
            operationDescription = "Synthetic operation",
            taxBreakdown =
                TaxBreakdown(
                    listOf(
                        TaxBreakdownDetail(
                            TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                            amount("100"),
                            regimeCode = "01",
                            taxRate = "21",
                            chargedTax = amount("21"),
                        ),
                    ),
                ),
            chainState = ChainState.FirstRecord,
            system = SistemaInformatico("Producer", nif, "System", "VF", "1", "1", true, false, false),
            generatedAt =
                assertIs<ValueResult.Valid<RecordGenerationTimestamp>>(
                    RecordGenerationTimestamp.parse("2026-01-01T12:00:00+01:00"),
                ).value,
            conditionalData =
                RegistrationConditionalData(
                    recipients = listOf(FiscalParty("Recipient", FiscalPartyIdentifier.SpanishNif(nif))),
                ),
        )
    }
}
