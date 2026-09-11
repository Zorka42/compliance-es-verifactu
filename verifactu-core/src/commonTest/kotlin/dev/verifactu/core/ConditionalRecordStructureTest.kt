package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConditionalRecordStructureTest {
    @Test
    fun snapshotsEveryConditionalListBeforeReturningACreatedRecord() {
        val source = registrationDraft()
        val recipients = (source.conditionalData.recipients + source.conditionalData.recipients).toMutableList()
        val invoices = mutableListOf(invoiceIdentifier(), invoiceIdentifier())
        val rectifying =
            assertIs<RecordCreationResult.Created<RegistroAlta>>(
                FiscalRecordFactory.createRegistration(
                    source.copy(
                        invoiceType = InvoiceType.R1,
                        conditionalData =
                            RegistrationConditionalData(
                                recipients = recipients,
                                rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE, invoices),
                            ),
                    ),
                ),
            ).record
        val replacing =
            assertIs<RecordCreationResult.Created<RegistroAlta>>(
                FiscalRecordFactory.createRegistration(
                    source.copy(
                        invoiceType = InvoiceType.F3,
                        conditionalData = source.conditionalData.copy(replacedInvoices = invoices),
                    ),
                ),
            ).record

        recipients.clear()
        invoices.clear()

        val saved = rectifying.draft.conditionalData
        assertEquals(2, saved.recipients.size)
        assertEquals(2, saved.rectification?.rectifiedInvoices?.size)
        assertEquals(2, replacing.draft.conditionalData.replacedInvoices.size)
        assertFails { (saved.recipients as MutableList<FiscalParty>).clear() }
        assertFails { (saved.rectification!!.rectifiedInvoices as MutableList<InvoiceIdentifier>)[0] = invoiceIdentifier() }
        assertFails { (replacing.draft.conditionalData.replacedInvoices as MutableList<InvoiceIdentifier>).clear() }
    }

    @Test
    fun enforcesTheSchemaCardinalityOfEachConditionalGroup() {
        val source = registrationDraft()
        val recipients = List(1000) { source.conditionalData.recipients.single() }
        val invoices = List(1000) { invoiceIdentifier() }
        val cases =
            listOf(
                "conditionalData.recipients" to source.copy(conditionalData = RegistrationConditionalData(recipients = recipients)),
                "conditionalData.replacedInvoices" to
                    source.copy(
                        invoiceType = InvoiceType.F3,
                        conditionalData = source.conditionalData.copy(replacedInvoices = invoices),
                    ),
                "conditionalData.rectification.rectifiedInvoices" to
                    source.copy(
                        invoiceType = InvoiceType.R1,
                        conditionalData =
                            source.conditionalData.copy(
                                rectification = InvoiceRectification(RectificationType.BY_DIFFERENCE, invoices),
                            ),
                    ),
            )
        cases.forEach { (path, draft) ->
            assertValid(draft)
            val data = draft.conditionalData
            val tooMany =
                draft.copy(
                    conditionalData =
                        when (path) {
                            "conditionalData.recipients" -> data.copy(recipients = recipients + recipients.first())
                            "conditionalData.replacedInvoices" -> data.copy(replacedInvoices = invoices + invoices.first())
                            else -> data.copy(rectification = data.rectification!!.copy(rectifiedInvoices = invoices + invoices.first()))
                        },
                )
            val issue = RegistroAltaValidator.validate(tooMany).issues.single { it.code == "VF-RECORD-036" }
            assertEquals(path, issue.fieldPath)
            assertEquals("RegistroFacturacionAltaType", issue.source?.section)
        }
    }

    @Test
    fun validatesCountryTokensForRecipientsAndThirdParties() {
        val source = registrationDraft()
        listOf("FR", "GR", "GB", "ES").forEach { country ->
            val party = otherParty(country, OtherPartyIdentifierType.PASSPORT)
            assertValid(source.copy(conditionalData = source.conditionalData.copy(recipients = listOf(party))))
        }
        listOf("", "fr", "XX", "FR\u0000", "FR ", "XYZ").forEach { country ->
            val party = otherParty(country, OtherPartyIdentifierType.PASSPORT)
            val draft =
                source.copy(
                    conditionalData =
                        source.conditionalData.copy(
                            recipients = listOf(party),
                            generatedBy = InvoiceGeneratedBy.THIRD_PARTY,
                            thirdParty = party,
                        ),
                )
            assertEquals(
                listOf("conditionalData.recipients[0].identifier.countryCode", "conditionalData.thirdParty.identifier.countryCode"),
                RegistroAltaValidator
                    .validate(draft)
                    .issues
                    .filter { it.code == "VF-RECORD-037" }
                    .map { it.fieldPath },
            )
        }
    }

    private fun registrationDraft(): RegistroAltaDraft {
        val invoice = invoiceIdentifier()
        return RegistroAltaDraft(
            version = RecordVersion.V1_0,
            invoice = invoice,
            issuerName = "Example issuer",
            invoiceType = InvoiceType.F1,
            totalTax = amount("21"),
            totalAmount = amount("121"),
            operationDescription = "Example operation",
            taxBreakdown =
                TaxBreakdown(
                    listOf(
                        TaxBreakdownDetail(
                            operation = TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                            taxableBase = amount("100"),
                            tax = TaxType.IVA,
                            regimeCode = "01",
                            taxRate = "21",
                            chargedTax = amount("21"),
                        ),
                    ),
                ),
            chainState = ChainState.FirstRecord,
            system = SistemaInformatico("Producer", invoice.issuer, "VeriFactu", "VF", "1.0", "install-1", true, false, false),
            generatedAt = (RecordGenerationTimestamp.parse("2024-01-01T19:20:30+01:00") as ValueResult.Valid).value,
            conditionalData =
                RegistrationConditionalData(
                    recipients = listOf(FiscalParty("Recipient", FiscalPartyIdentifier.SpanishNif(invoice.issuer))),
                ),
        )
    }

    private fun invoiceIdentifier(): InvoiceIdentifier =
        InvoiceIdentifier(
            (TaxIdentifier.parse("89890001K") as ValueResult.Valid).value,
            (InvoiceNumber.parse("INV-1") as ValueResult.Valid).value,
            (InvoiceIssueDate.parse("01-01-2024") as ValueResult.Valid).value,
        )

    private fun amount(value: String): FiscalAmount = (FiscalAmount.parse(value) as ValueResult.Valid).value

    private fun otherParty(
        countryCode: String?,
        type: OtherPartyIdentifierType,
    ): FiscalParty = FiscalParty("Foreign party", FiscalPartyIdentifier.Other(countryCode, type, "P-123"))

    private fun assertValid(draft: RegistroAltaDraft) {
        val report = RegistroAltaValidator.validate(draft)
        assertTrue(report.isValid, report.toString())
    }
}
