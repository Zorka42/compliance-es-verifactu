package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FiscalRecordFactoryTest {
    @Test
    fun createsRegistrationAndExposesTheNextCallerOwnedChainState() {
        val result = FiscalRecordFactory.createRegistration(registrationDraft())

        val created = assertIs<RecordCreationResult.Created<RegistroAlta>>(result)
        assertEquals("3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60", created.record.hash)
        assertEquals(created.record.hash, created.nextChainState.hash)
    }

    @Test
    fun returnsTypedIssuesInsteadOfThrowingForAnInvalidChainHash() {
        val result =
            FiscalRecordFactory.createRegistration(
                registrationDraft(chainState = ChainState.PreviousRecord(invoiceIdentifier(), "not-a-hash")),
            )

        val invalid = assertIs<RecordCreationResult.Invalid>(result)
        assertEquals(
            "VF-CHAIN-001",
            invalid.report.issues
                .single()
                .code,
        )
    }

    @Test
    fun identifiesMissingSystemMetadata() {
        val invalidSystem = SistemaInformatico("", validTaxId(), "VeriFactu", "VF", "1.0", "installation-1", true, false, false)

        val result = FiscalRecordFactory.createRegistration(registrationDraft().copy(system = invalidSystem))

        val invalid = assertIs<RecordCreationResult.Invalid>(result)
        assertEquals(
            "system.producerName",
            invalid.report.issues
                .single()
                .fieldPath,
        )
    }

    @Test
    fun returnsAValidationIssueWhenTheMandatoryTaxBreakdownIsEmpty() {
        val result =
            FiscalRecordFactory.createRegistration(
                registrationDraft().copy(taxBreakdown = TaxBreakdown(emptyList())),
            )

        val invalid = assertIs<RecordCreationResult.Invalid>(result)
        assertEquals(
            "VF-RECORD-002",
            invalid.report.issues
                .single()
                .code,
        )
    }

    @Test
    fun validatesTheConditionalInvoiceTypeMatrix() {
        val rectifyingTypes = listOf(InvoiceType.R1, InvoiceType.R2, InvoiceType.R3, InvoiceType.R4)

        assertValid(registrationDraft())
        assertValid(
            registrationDraft().copy(
                invoiceType = InvoiceType.F3,
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        replacedInvoices = listOf(invoiceIdentifier()),
                    ),
            ),
        )
        rectifyingTypes.forEach { invoiceType ->
            assertValid(
                registrationDraft().copy(
                    invoiceType = invoiceType,
                    conditionalData =
                        registrationDraft().conditionalData.copy(
                            rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE),
                        ),
                ),
            )
        }
        assertValid(
            registrationDraft().copy(
                invoiceType = InvoiceType.R5,
                conditionalData = RegistrationConditionalData(rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE)),
            ),
        )
    }

    @Test
    fun reportsConditionalRectificationAndRecipientViolations() {
        val missingRectification = registrationDraft().copy(invoiceType = InvoiceType.R1)
        val recipientsOnSimplifiedRectification =
            registrationDraft().copy(
                invoiceType = InvoiceType.R5,
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE),
                    ),
            )
        val replacementAmountsForDifference =
            registrationDraft().copy(
                invoiceType = InvoiceType.R2,
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        rectification =
                            InvoiceRectification(
                                type = RectificationType.BY_DIFFERENCE,
                                replacedAmounts = RectificationAmounts(validAmount("100.00"), validAmount("21.00")),
                            ),
                    ),
            )

        assertIssue("VF-RECORD-010", missingRectification)
        assertIssue("VF-RECORD-018", recipientsOnSimplifiedRectification)
        assertIssue("VF-RECORD-013", replacementAmountsForDifference)
    }

    @Test
    fun reportsSubsanationAndIssuerDelegateViolations() {
        val previousRejectionWithoutSubsanation =
            registrationDraft().copy(
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        previousRejection = RegistrationPreviousRejection.NOT_PRESENT_AT_AEAT,
                    ),
            )
        val thirdPartyWithoutParty =
            registrationDraft().copy(
                conditionalData = registrationDraft().conditionalData.copy(generatedBy = InvoiceGeneratedBy.THIRD_PARTY),
            )

        assertIssue("VF-RECORD-015", previousRejectionWithoutSubsanation)
        assertIssue("VF-RECORD-019", thirdPartyWithoutParty)
    }

    @Test
    fun reportsAdditionalConditionalInvoiceAndPartyIdentifierViolations() {
        val reverseChargeOnSimplifiedInvoice =
            registrationDraft().copy(
                invoiceType = InvoiceType.F2,
                conditionalData = RegistrationConditionalData(),
                taxBreakdown =
                    TaxBreakdown(
                        listOf(
                            TaxBreakdownDetail(
                                operation = TaxOperation.Qualified(Qualification.SUBJECT_REVERSE_CHARGE),
                                taxableBase = validAmount("111.10"),
                                tax = TaxType.IVA,
                                regimeCode = "01",
                                taxRate = "0",
                                chargedTax = validAmount("0"),
                            ),
                        ),
                    ),
            )
        val r3WithPassportRecipient =
            registrationDraft().copy(
                invoiceType = InvoiceType.R3,
                conditionalData =
                    RegistrationConditionalData(
                        rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE),
                        recipients =
                            listOf(
                                FiscalParty(
                                    "Foreign recipient",
                                    FiscalPartyIdentifier.Other("FR", OtherPartyIdentifierType.PASSPORT, "P-123"),
                                ),
                            ),
                    ),
            )
        val thirdPartyWithIssuerNif =
            registrationDraft().copy(
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        generatedBy = InvoiceGeneratedBy.THIRD_PARTY,
                        thirdParty = FiscalParty("Same NIF", FiscalPartyIdentifier.SpanishNif(validTaxId())),
                    ),
            )
        val nonRegisteredRecipientOutsideSpain =
            registrationDraft().copy(
                conditionalData =
                    RegistrationConditionalData(
                        recipients =
                            listOf(
                                FiscalParty(
                                    "Foreign recipient",
                                    FiscalPartyIdentifier.Other("FR", OtherPartyIdentifierType.NOT_REGISTERED, "N-123"),
                                ),
                            ),
                    ),
            )

        assertIssue("VF-RECORD-032", reverseChargeOnSimplifiedInvoice)
        assertIssue("VF-RECORD-033", r3WithPassportRecipient)
        assertIssue("VF-RECORD-035", thirdPartyWithIssuerNif)
        assertIssue("VF-RECORD-030", nonRegisteredRecipientOutsideSpain)
    }

    @Test
    fun reportsConditionalTaxTreatmentViolations() {
        val exemptWithTaxFields =
            registrationDraft().copy(
                taxBreakdown = taxBreakdown(TaxOperation.Exempt(Exemption.E1), taxRate = "0", chargedTax = validAmount("0")),
            )
        val nonSubjectWithTaxFields =
            registrationDraft().copy(
                taxBreakdown =
                    taxBreakdown(
                        TaxOperation.Qualified(Qualification.NOT_SUBJECT),
                        taxRate = "0",
                        chargedTax = validAmount("0"),
                    ),
            )
        val subjectWithoutRequiredTaxFields =
            registrationDraft().copy(
                taxBreakdown = taxBreakdown(TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT)),
            )
        val reverseChargeWithoutZeroFields =
            registrationDraft().copy(
                taxBreakdown = taxBreakdown(TaxOperation.Qualified(Qualification.SUBJECT_REVERSE_CHARGE)),
            )

        assertIssue("VF-TAX-1238", exemptWithTaxFields)
        assertIssue("VF-RECORD-009", nonSubjectWithTaxFields)
        assertIssue("VF-RECORD-007", subjectWithoutRequiredTaxFields)
        assertIssue("VF-RECORD-008", reverseChargeWithoutZeroFields)
    }

    @Test
    fun reportsRemainingInvoiceMatrixViolations() {
        val rectificationOnNonRectifyingInvoice =
            registrationDraft().copy(
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE),
                    ),
            )
        val replacementWithoutAmounts =
            registrationDraft().copy(
                invoiceType = InvoiceType.R1,
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        rectification = InvoiceRectification(RectificationType.REPLACEMENT),
                    ),
            )
        val replacedInvoicesOnF1 =
            registrationDraft().copy(
                conditionalData = registrationDraft().conditionalData.copy(replacedInvoices = listOf(invoiceIdentifier())),
            )
        val priorSubsanationRejectionWithoutSubsanation =
            registrationDraft().copy(
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        previousRejection = RegistrationPreviousRejection.YES,
                    ),
            )
        val missingRecipients = registrationDraft().copy(conditionalData = RegistrationConditionalData())
        val r2WithPassportRecipient =
            registrationDraft().copy(
                invoiceType = InvoiceType.R2,
                conditionalData =
                    RegistrationConditionalData(
                        rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE),
                        recipients = listOf(otherParty("FR", OtherPartyIdentifierType.PASSPORT)),
                    ),
            )

        assertIssue("VF-RECORD-011", rectificationOnNonRectifyingInvoice)
        assertIssue("VF-RECORD-012", replacementWithoutAmounts)
        assertIssue("VF-RECORD-014", replacedInvoicesOnF1)
        assertIssue("VF-RECORD-016", priorSubsanationRejectionWithoutSubsanation)
        assertIssue("VF-RECORD-017", missingRecipients)
        assertIssue("VF-RECORD-034", r2WithPassportRecipient)
    }

    @Test
    fun reportsRemainingDelegateIndicatorAndPartyViolations() {
        val recipientGeneratedWithoutRecipients =
            registrationDraft().copy(
                invoiceType = InvoiceType.F2,
                conditionalData = RegistrationConditionalData(generatedBy = InvoiceGeneratedBy.RECIPIENT),
            )
        val recipientGeneratedWithThirdParty =
            registrationDraft().copy(
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        generatedBy = InvoiceGeneratedBy.RECIPIENT,
                        thirdParty = otherParty("FR", OtherPartyIdentifierType.PASSPORT),
                    ),
            )
        val thirdPartyWithoutGenerator =
            registrationDraft().copy(
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        thirdParty = otherParty("FR", OtherPartyIdentifierType.PASSPORT),
                    ),
            )
        val unregisteredThirdParty =
            registrationDraft().copy(
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        generatedBy = InvoiceGeneratedBy.THIRD_PARTY,
                        thirdParty = otherParty("ES", OtherPartyIdentifierType.NOT_REGISTERED),
                    ),
            )
        val simplifiedQualificationOnF2 =
            registrationDraft().copy(
                invoiceType = InvoiceType.F2,
                conditionalData = RegistrationConditionalData(simplifiedInvoiceQualification = SimplifiedInvoiceQualification.YES),
            )
        val recipientExemptionOnF1 =
            registrationDraft().copy(
                conditionalData =
                    registrationDraft().conditionalData.copy(
                        recipientIdentificationExemption = RecipientIdentificationExemption.YES,
                    ),
            )
        val couponOnF1 =
            registrationDraft().copy(
                conditionalData = registrationDraft().conditionalData.copy(coupon = CouponIndicator.YES),
            )
        val macroDataMissingAtThreshold = registrationDraft().copy(totalAmount = validAmount("100000000.00"))
        val macroDataOutsideThreshold =
            registrationDraft().copy(
                conditionalData = registrationDraft().conditionalData.copy(macroData = MacroDataIndicator.YES),
            )
        val invalidOtherParty =
            registrationDraft().copy(
                conditionalData =
                    RegistrationConditionalData(
                        recipients = listOf(otherParty("ES", OtherPartyIdentifierType.VAT_IDENTIFIER, "")),
                    ),
            )
        val passportWithoutCountry =
            registrationDraft().copy(
                conditionalData = RegistrationConditionalData(recipients = listOf(otherParty(null, OtherPartyIdentifierType.PASSPORT))),
            )

        assertIssue("VF-RECORD-020", recipientGeneratedWithoutRecipients)
        assertIssue("VF-RECORD-021", recipientGeneratedWithThirdParty)
        assertIssue("VF-RECORD-022", thirdPartyWithoutGenerator)
        assertIssue("VF-RECORD-031", unregisteredThirdParty)
        assertIssue("VF-RECORD-023", simplifiedQualificationOnF2)
        assertIssue("VF-RECORD-024", recipientExemptionOnF1)
        assertIssue("VF-RECORD-025", couponOnF1)
        assertIssue("VF-RECORD-026", macroDataMissingAtThreshold)
        assertIssue("VF-RECORD-027", macroDataOutsideThreshold)
        assertIssue("VF-RECORD-028", invalidOtherParty)
        assertIssue("VF-RECORD-029", passportWithoutCountry)
    }

    @Test
    fun usesUnicodeCodePointsForXmlLengthValidation() {
        val atBoundary = "a".repeat(499) + "\uD83D\uDE00"
        val overBoundary = atBoundary + "\uD83D\uDE00"

        assertEquals(500, xmlSchemaCharacterCount(atBoundary))
        assertEquals(501, xmlSchemaCharacterCount(overBoundary))
        assertTrue(RegistroAltaValidator.validate(registrationDraft().copy(operationDescription = atBoundary)).isValid)
        assertFalse(RegistroAltaValidator.validate(registrationDraft().copy(operationDescription = overBoundary)).isValid)
    }

    @Test
    fun createsCancellationRecordsInTheSameChain() {
        val previousHash = "F7B94CFD8924EDFF273501B01EE5153E4CE8F259766F88CF6ACB8935802A2B97"
        val draft =
            RegistroAnulacionDraft(
                cancelledInvoice =
                    InvoiceIdentifier(
                        validTaxId(),
                        (InvoiceNumber.parse("12345679/G34") as ValueResult.Valid).value,
                        validIssueDate(),
                    ),
                chainState = ChainState.PreviousRecord(invoiceIdentifier(), previousHash),
                system =
                    SistemaInformatico(
                        "Example producer",
                        validTaxId(),
                        "VeriFactu",
                        "VF",
                        "1.0",
                        "installation-1",
                        true,
                        false,
                        false,
                    ),
                generatedAt = (RecordGenerationTimestamp.parse("2024-01-01T19:20:40+01:00") as ValueResult.Valid).value,
            )

        val result = FiscalRecordFactory.createCancellation(draft)

        val created = assertIs<RecordCreationResult.Created<RegistroAnulacion>>(result)
        assertEquals("177547C0D57AC74748561D054A9CEC14B4C4EA23D1BEFD6F2E69E3A388F90C68", created.record.hash)
    }

    @Test
    fun returnsTypedCancellationValidationIssuesForInvalidChainState() {
        val draft =
            RegistroAnulacionDraft(
                cancelledInvoice = invoiceIdentifier(),
                chainState = ChainState.PreviousRecord(invoiceIdentifier(), "lowercase-hash"),
                system =
                    SistemaInformatico(
                        "Example producer",
                        validTaxId(),
                        "VeriFactu",
                        "VF",
                        "1.0",
                        "installation-1",
                        true,
                        false,
                        false,
                    ),
                generatedAt = validTimestamp(),
            )

        val result = FiscalRecordFactory.createCancellation(draft)

        assertEquals(
            "VF-CHAIN-001",
            assertIs<RecordCreationResult.Invalid>(result)
                .report.issues
                .single()
                .code,
        )
    }

    @Test
    fun snapshotsTheTaxBreakdownBeforeReturningACreatedRecord() {
        val draft = registrationDraft()
        val details = draft.taxBreakdown.details.toMutableList()
        val created =
            assertIs<RecordCreationResult.Created<RegistroAlta>>(
                FiscalRecordFactory.createRegistration(draft.copy(taxBreakdown = TaxBreakdown(details))),
            )

        details.clear()

        assertEquals(draft.taxBreakdown.details, created.record.draft.taxBreakdown.details)
        assertTrue(RegistroAltaValidator.validate(created.record.draft).isValid)
    }

    @Test
    fun rejectsMutationThroughTheCreatedRecordList() {
        val draft = registrationDraft()
        val original = List(2) { draft.taxBreakdown.details.single() }
        val record =
            assertIs<RecordCreationResult.Created<RegistroAlta>>(
                FiscalRecordFactory.createRegistration(draft.copy(taxBreakdown = TaxBreakdown(original))),
            ).record
        val details = record.draft.taxBreakdown.details

        val clearFailure = assertFails { (details as MutableList<TaxBreakdownDetail>).clear() }
        val setFailure = assertFails { (details as MutableList<TaxBreakdownDetail>)[0] = original[0].copy(taxRate = "99") }

        assertTrue(clearFailure is ClassCastException || clearFailure is UnsupportedOperationException)
        assertTrue(setFailure is ClassCastException || setFailure is UnsupportedOperationException)
        assertEquals(original, details)
    }

    @Test
    fun validatesTheLengthOfTheUntrimmedTextThatWillBeSerialized() {
        val draft = registrationDraft()
        val invalid =
            assertIs<RecordCreationResult.Invalid>(
                FiscalRecordFactory.createRegistration(
                    draft.copy(
                        issuerName = " ${"A".repeat(120)} ",
                        operationDescription = " ${"A".repeat(500)} ",
                        system = draft.system.copy(systemIdentifier = " VF "),
                    ),
                ),
            )

        assertEquals(
            listOf("system.systemIdentifier", "issuerName", "operationDescription"),
            invalid.report.issues.map { it.fieldPath },
        )
        assertTrue(invalid.report.issues.all { it.code == "VF-RECORD-001" })
    }

    @Test
    fun acceptsTheW3cLengthBoundaryForSupplementaryUnicodeCharacters() {
        val draft = registrationDraft()
        assertIs<RecordCreationResult.Created<RegistroAlta>>(
            FiscalRecordFactory.createRegistration(
                draft.copy(issuerName = "\uD83D\uDE00".repeat(120)),
            ),
        )
        assertIs<RecordCreationResult.Invalid>(
            FiscalRecordFactory.createRegistration(
                draft.copy(issuerName = "\uD83D\uDE00".repeat(121)),
            ),
        )
    }

    @Test
    fun rejectsNonXmlCharactersInRecordAndSystemTextWithFieldPaths() {
        val draft = registrationDraft()
        val invalid =
            assertIs<RecordCreationResult.Invalid>(
                FiscalRecordFactory.createRegistration(
                    draft.copy(
                        issuerName = "Issuer\u0000",
                        operationDescription = "Operation\uD800",
                        system = draft.system.copy(producerName = "Producer\uDC00", version = "1\uFFFF"),
                    ),
                ),
            )

        assertEquals(
            listOf("system.producerName", "system.version", "issuerName", "operationDescription"),
            invalid.report.issues.map { it.fieldPath },
        )
        assertTrue(invalid.report.issues.all { it.code == "VF-RECORD-006" && it.source?.document == "W3C XML 1.0" })
    }

    @Test
    fun checksRegimeCodesAgainstThePinnedSchemaEnumeration() {
        val draft = registrationDraft()
        val detail = draft.taxBreakdown.details.single()
        listOf("00", "12", "13", "16", "22", "99", "1", "01 ").forEach { regime ->
            val invalid =
                assertIs<RecordCreationResult.Invalid>(
                    FiscalRecordFactory.createRegistration(
                        draft.copy(taxBreakdown = TaxBreakdown(listOf(detail.copy(regimeCode = regime)))),
                    ),
                    regime,
                )
            assertTrue(invalid.report.issues.any { it.code == "VF-RECORD-003" })
        }
        listOf("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "14", "15", "17", "18", "19", "20", "21")
            .forEach { regime ->
                val report =
                    RegistroAltaValidator.validate(
                        draft.copy(taxBreakdown = TaxBreakdown(listOf(detail.copy(regimeCode = regime)))),
                    )
                assertTrue(report.issues.none { it.code == "VF-RECORD-003" }, regime)
            }
    }

    private fun registrationDraft(chainState: ChainState = ChainState.FirstRecord): RegistroAltaDraft =
        RegistroAltaDraft(
            version = RecordVersion.V1_0,
            invoice = invoiceIdentifier(),
            issuerName = "Example issuer",
            invoiceType = InvoiceType.F1,
            totalTax = validAmount("12.35"),
            totalAmount = validAmount("123.45"),
            operationDescription = "Example operation",
            taxBreakdown =
                TaxBreakdown(
                    listOf(
                        TaxBreakdownDetail(
                            operation = TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                            taxableBase = validAmount("111.10"),
                            tax = TaxType.IVA,
                            regimeCode = "01",
                            taxRate = "10",
                            chargedTax = validAmount("12.35"),
                        ),
                    ),
                ),
            chainState = chainState,
            system = SistemaInformatico("Example producer", validTaxId(), "VeriFactu", "VF", "1.0", "installation-1", true, false, false),
            generatedAt = validTimestamp(),
            conditionalData =
                RegistrationConditionalData(
                    recipients = listOf(FiscalParty("Example recipient", FiscalPartyIdentifier.SpanishNif(validTaxId()))),
                ),
        )

    private fun invoiceIdentifier(): InvoiceIdentifier = InvoiceIdentifier(validTaxId(), validInvoiceNumber(), validIssueDate())

    private fun validTaxId(): TaxIdentifier = (TaxIdentifier.parse("89890001K") as ValueResult.Valid).value

    private fun validInvoiceNumber(): InvoiceNumber = (InvoiceNumber.parse("12345678/G33") as ValueResult.Valid).value

    private fun validIssueDate(): InvoiceIssueDate = (InvoiceIssueDate.parse("01-01-2024") as ValueResult.Valid).value

    private fun validAmount(value: String): FiscalAmount = (FiscalAmount.parse(value) as ValueResult.Valid).value

    private fun validTimestamp(): RecordGenerationTimestamp =
        (RecordGenerationTimestamp.parse("2024-01-01T19:20:30+01:00") as ValueResult.Valid).value

    private fun taxBreakdown(
        operation: TaxOperation,
        taxRate: String? = null,
        chargedTax: FiscalAmount? = null,
    ): TaxBreakdown =
        TaxBreakdown(
            listOf(
                TaxBreakdownDetail(
                    operation = operation,
                    taxableBase = validAmount("111.10"),
                    tax = TaxType.IVA,
                    regimeCode = "01",
                    taxRate = taxRate,
                    chargedTax = chargedTax,
                ),
            ),
        )

    private fun otherParty(
        countryCode: String?,
        type: OtherPartyIdentifierType,
        identifier: String = "P-123",
    ): FiscalParty =
        FiscalParty(
            "Foreign party",
            FiscalPartyIdentifier.Other(countryCode, type, identifier),
        )

    private fun assertValid(draft: RegistroAltaDraft) {
        assertTrue(RegistroAltaValidator.validate(draft).isValid)
    }

    private fun assertIssue(
        expectedCode: String,
        draft: RegistroAltaDraft,
    ) {
        assertTrue(RegistroAltaValidator.validate(draft).issues.any { issue -> issue.code == expectedCode })
    }
}
