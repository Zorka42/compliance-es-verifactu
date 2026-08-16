package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
                            taxRate = "10",
                            chargedTax = validAmount("12.35"),
                        ),
                    ),
                ),
            chainState = chainState,
            system = SistemaInformatico("Example producer", validTaxId(), "VeriFactu", "VF", "1.0", "installation-1", true, false, false),
            generatedAt = validTimestamp(),
        )

    private fun invoiceIdentifier(): InvoiceIdentifier = InvoiceIdentifier(validTaxId(), validInvoiceNumber(), validIssueDate())

    private fun validTaxId(): TaxIdentifier = (TaxIdentifier.parse("89890001K") as ValueResult.Valid).value

    private fun validInvoiceNumber(): InvoiceNumber = (InvoiceNumber.parse("12345678/G33") as ValueResult.Valid).value

    private fun validIssueDate(): InvoiceIssueDate = (InvoiceIssueDate.parse("01-01-2024") as ValueResult.Valid).value

    private fun validAmount(value: String): FiscalAmount = (FiscalAmount.parse(value) as ValueResult.Valid).value

    private fun validTimestamp(): RecordGenerationTimestamp =
        (RecordGenerationTimestamp.parse("2024-01-01T19:20:30+01:00") as ValueResult.Valid).value
}
