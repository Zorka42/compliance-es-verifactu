package dev.verifactu.xml

import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalAmount
import dev.verifactu.core.FiscalParty
import dev.verifactu.core.FiscalPartyIdentifier
import dev.verifactu.core.FiscalRecordFactory
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceIssueDate
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.InvoiceRectification
import dev.verifactu.core.InvoiceType
import dev.verifactu.core.Qualification
import dev.verifactu.core.RecordCreationResult
import dev.verifactu.core.RecordGenerationTimestamp
import dev.verifactu.core.RecordVersion
import dev.verifactu.core.RectificationAmounts
import dev.verifactu.core.RectificationType
import dev.verifactu.core.RegistrationConditionalData
import dev.verifactu.core.RegistrationPreviousRejection
import dev.verifactu.core.RegistroAlta
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.core.RegistroAnulacion
import dev.verifactu.core.RegistroAnulacionDraft
import dev.verifactu.core.SistemaInformatico
import dev.verifactu.core.Subsanation
import dev.verifactu.core.TaxBreakdown
import dev.verifactu.core.TaxBreakdownDetail
import dev.verifactu.core.TaxIdentifier
import dev.verifactu.core.TaxOperation
import dev.verifactu.core.TaxType
import dev.verifactu.core.ValueResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RegistroXmlSerializerTest {
    @Test
    fun serializesConditionalGroupsInSchemaOrderOnEveryCommonTarget() {
        val base = createdRegistration().draft
        val cases = InvoiceType.entries.flatMap { type -> RectificationType.entries.map { type to it } }
        cases.forEach { (type, correction) ->
            val isRectifying = type.name.startsWith("R")
            val replacedAmounts =
                if (correction == RectificationType.REPLACEMENT) {
                    RectificationAmounts(amount("100"), amount("21"))
                } else {
                    null
                }
            val rectification = if (isRectifying) InvoiceRectification(correction, listOf(base.invoice), replacedAmounts) else null
            val data =
                RegistrationConditionalData(
                    externalReference = "reference & 1",
                    subsanation = Subsanation.YES,
                    previousRejection = RegistrationPreviousRejection.YES,
                    rectification = rectification,
                    replacedInvoices = if (type == InvoiceType.F3) listOf(base.invoice) else emptyList(),
                    operationDate = base.invoice.issueDate,
                    recipients = if (type in listOf(InvoiceType.F2, InvoiceType.R5)) emptyList() else base.conditionalData.recipients,
                    taxationAgreementRegistrationNumber = "agreement",
                    systemAgreementIdentifier = "system-agreement",
                )
            val record =
                assertIs<RecordCreationResult.Created<RegistroAlta>>(
                    FiscalRecordFactory.createRegistration(base.copy(invoiceType = type, conditionalData = data)),
                ).record
            val xml = RegistroXmlSerializer.serialize(record)
            assertContains(xml, "<RefExterna>reference &amp; 1</RefExterna><NombreRazonEmisor>")
            assertContains(xml, "<Subsanacion>S</Subsanacion><RechazoPrevio>S</RechazoPrevio><TipoFactura>$type</TipoFactura>")
            assertTrue(xml.indexOf("<TipoFactura>") < xml.indexOf("<FechaOperacion>"))
            assertTrue(xml.indexOf("<FechaOperacion>") < xml.indexOf("<DescripcionOperacion>"))
            if (isRectifying) {
                assertContains(xml, "</TipoFactura><TipoRectificativa>${correction.xmlValue}</TipoRectificativa><FacturasRectificadas>")
                if (correction == RectificationType.REPLACEMENT) {
                    assertContains(xml, "</FacturasRectificadas><ImporteRectificacion><BaseRectificada>100</BaseRectificada>")
                }
            }
            if (type == InvoiceType.F3) assertContains(xml, "</TipoFactura><FacturasSustituidas><IDFacturaSustituida>")
            assertContains(xml, "</FechaHoraHusoGenRegistro><NumRegistroAcuerdoFacturacion>agreement</NumRegistroAcuerdoFacturacion>")
            assertContains(xml, "<IdAcuerdoSistemaInformatico>system-agreement</IdAcuerdoSistemaInformatico><TipoHuella>")
        }
    }

    @Test
    fun preservesNormativeSupplementaryBoundariesInRecordAndBatchText() {
        val original = createdRegistration().draft
        val supplementary = "\uD83D\uDE00"
        val draft =
            original.copy(
                issuerName = supplementary.repeat(120),
                operationDescription = supplementary.repeat(500),
            )
        val record = assertIs<RecordCreationResult.Created<RegistroAlta>>(FiscalRecordFactory.createRegistration(draft)).record
        val created =
            assertIs<SubmissionBatchBuildResult.Created>(
                SubmissionBatchBuilder.build(
                    SubmissionHeader(draft.issuerName, draft.invoice.issuer),
                    listOf(SubmissionRecord.Registration(record)),
                ),
            )
        assertContains(created.xml, "<NombreRazon>${supplementary.repeat(120)}</NombreRazon>")
        assertContains(created.xml, "<DescripcionOperacion>${supplementary.repeat(500)}</DescripcionOperacion>")
        // The low-level XML contract is wider than the AEAT service's invoice-number character profile.
        val structuralDraft = draft.copy(invoice = draft.invoice.copy(number = invoiceNumber(supplementary.repeat(60))))
        val invalid = assertIs<RecordCreationResult.Invalid>(FiscalRecordFactory.createRegistration(structuralDraft))
        assertTrue(invalid.report.issues.any { it.aeatCode == "1130" })
        val structuralXml = RegistroXmlSerializer.serialize(RegistroAlta(structuralDraft, record.hash))
        assertContains(structuralXml, "<NumSerieFactura>${supplementary.repeat(60)}</NumSerieFactura>")
    }

    @Test
    fun serializesARegistrationRecordInTheAeatSchemaOrder() {
        val xml = RegistroXmlSerializer.serialize(createdRegistration())

        assertEquals(REGISTRO_ALTA_V1_GOLDEN_XML, xml)
        assertContains(xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        assertContains(
            xml,
            "<RegistroAlta xmlns=\"https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd\">",
        )
        assertContains(xml, "<NombreRazonEmisor>Issuer &amp; Co</NombreRazonEmisor>")
        assertTrue(xml.indexOf("<IDVersion>") < xml.indexOf("<IDFactura>"))
        assertTrue(xml.indexOf("<Desglose>") < xml.indexOf("<CuotaTotal>"))
        assertTrue(xml.indexOf("<Encadenamiento>") < xml.indexOf("<SistemaInformatico>"))
        assertTrue(xml.indexOf("<TipoHuella>01</TipoHuella>") < xml.indexOf("<Huella>"))
    }

    @Test
    fun serializesFirstRecordChainStateWithoutAnInventedPreviousHash() {
        val xml = RegistroXmlSerializer.serialize(createdRegistration())

        assertContains(xml, "<PrimerRegistro>S</PrimerRegistro>")
        assertTrue("<RegistroAnterior>" !in xml)
    }

    @Test
    fun serializesTheCancellationGoldenFixture() {
        assertEquals(REGISTRO_ANULACION_V1_GOLDEN_XML, RegistroXmlSerializer.serialize(createdCancellation()))
    }

    @Test
    fun serializesARegistrationAndCancellationInOneBatch() {
        val xml =
            SubmissionBatchXmlSerializer.serialize(
                SubmissionHeader("Producer", taxId()),
                listOf(
                    SubmissionRecord.Registration(createdRegistration()),
                    SubmissionRecord.Cancellation(createdCancellation()),
                ),
            )

        assertContains(xml, "<RegFactuSistemaFacturacion")
        assertEquals(2, "<RegistroFactura>".toRegex().findAll(xml).count())
        assertContains(xml, "<RegistroAlta xmlns=")
        assertContains(xml, "<RegistroAnulacion xmlns=")
    }

    @Test
    fun preservesCarriageReturnsAndEscapesSpecialCharactersInRecordText() {
        val original = createdRegistration()
        val invoice = original.draft.invoice.copy(number = invoiceNumber("INV\r\n\t<&>\"'1"))
        // Exercise escaping through the unchecked serializer; these characters fail service validation.
        val record = RegistroAlta(original.draft.copy(invoice = invoice, issuerName = "Issuer\rName"), original.hash)
        val xml = RegistroXmlSerializer.serialize(record)

        assertContains(xml, "<NumSerieFactura>INV&#13;\n\t&lt;&amp;&gt;&quot;&apos;1</NumSerieFactura>")
        assertContains(xml, "<NombreRazonEmisor>Issuer&#13;Name</NombreRazonEmisor>")
        assertTrue('\r' !in xml)
    }

    @Test
    fun escapesHeaderTaxIdentifiersAndPreservesHeaderCarriageReturns() {
        val xml =
            SubmissionBatchXmlSerializer.serialize(
                SubmissionHeader("Issuer\rName", assertIs<ValueResult.Valid<TaxIdentifier>>(TaxIdentifier.parse("12&345678")).value),
                listOf(SubmissionRecord.Registration(createdRegistration())),
            )

        assertContains(xml, "<NombreRazon>Issuer&#13;Name</NombreRazon>")
        assertContains(xml, "<NIF>12&amp;345678</NIF>")
        assertTrue('\r' !in xml)
    }

    @Test
    fun serializesTheSameCreatedRecordAfterTheCallerChangesItsInputList() {
        val draft = createdRegistration().draft
        val details = draft.taxBreakdown.details.toMutableList()
        val created =
            assertIs<RecordCreationResult.Created<RegistroAlta>>(
                FiscalRecordFactory.createRegistration(draft.copy(taxBreakdown = TaxBreakdown(details))),
            ).record
        val before = RegistroXmlSerializer.serialize(created)

        details.clear()

        assertEquals(before, RegistroXmlSerializer.serialize(created))
    }

    @Test
    fun buildsAnOrderedBatchWithAnExactSoapBodyAndRedactedDiagnostics() {
        val header = SubmissionHeader("Issuer & Co", taxId())
        val records = listOf(SubmissionRecord.Registration(createdRegistration()), SubmissionRecord.Cancellation(createdCancellation()))
        val created = assertIs<SubmissionBatchBuildResult.Created>(SubmissionBatchBuilder.build(header, records))

        assertEquals(SubmissionBatchXmlSerializer.serialize(header, records), created.xml)
        assertTrue(created.xml.indexOf("<RegistroAlta ") < created.xml.indexOf("<RegistroAnulacion "))
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" +
                created.xml.removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>") +
                "</soap:Body></soap:Envelope>",
            created.soapEnvelope,
        )
        assertEquals("SubmissionBatchBuildResult.Created(xml=<redacted>, soapEnvelope=<redacted>)", created.toString())
    }

    @Test
    fun rejectsInvalidBatchSizesAndAcceptsTheMaximumSize() {
        val header = SubmissionHeader("Issuer", taxId())
        val record = SubmissionRecord.Registration(createdRegistration())
        listOf(emptyList(), List(1001) { record }).forEach { records ->
            val invalid = assertIs<SubmissionBatchBuildResult.Invalid>(SubmissionBatchBuilder.build(header, records))
            assertEquals(
                "VF-BATCH-001",
                invalid.report.issues
                    .single()
                    .code,
            )
            assertEquals(
                "records",
                invalid.report.issues
                    .single()
                    .fieldPath,
            )
        }

        val created = assertIs<SubmissionBatchBuildResult.Created>(SubmissionBatchBuilder.build(header, List(1000) { record }))
        assertEquals(1000, "<RegistroFactura>".toRegex().findAll(created.xml).count())
    }

    @Test
    fun rejectsInvalidBatchHeaderNames() {
        val records = listOf(SubmissionRecord.Registration(createdRegistration()))
        listOf("", " \t\r\n", " ${"A".repeat(120)} ", "Issuer\u0000", "Issuer\uD800", "Issuer\uDC00", "Issuer\uFFFE")
            .forEach { name ->
                val invalid =
                    assertIs<SubmissionBatchBuildResult.Invalid>(SubmissionBatchBuilder.build(SubmissionHeader(name, taxId()), records))
                assertEquals(
                    "VF-BATCH-002",
                    invalid.report.issues
                        .single()
                        .code,
                )
                assertEquals(
                    "header.issuerName",
                    invalid.report.issues
                        .single()
                        .fieldPath,
                )
            }
        assertIs<SubmissionBatchBuildResult.Created>(
            SubmissionBatchBuilder.build(SubmissionHeader("\uD83D\uDE00".repeat(120), taxId()), records),
        )
    }

    @Test
    fun rejectsBothRegistrationAndCancellationIssuerMismatches() {
        val otherId = assertIs<ValueResult.Valid<TaxIdentifier>>(TaxIdentifier.parse("12345678Z")).value
        val registration = createdRegistration()
        val otherRegistration =
            assertIs<RecordCreationResult.Created<RegistroAlta>>(
                FiscalRecordFactory.createRegistration(
                    registration.draft.copy(invoice = registration.draft.invoice.copy(issuer = otherId)),
                ),
            ).record
        val cancellation = createdCancellation()
        val otherCancellation =
            assertIs<RecordCreationResult.Created<RegistroAnulacion>>(
                FiscalRecordFactory.createCancellation(
                    cancellation.draft.copy(cancelledInvoice = cancellation.draft.cancelledInvoice.copy(issuer = otherId)),
                ),
            ).record
        val invalid =
            assertIs<SubmissionBatchBuildResult.Invalid>(
                SubmissionBatchBuilder.build(
                    SubmissionHeader("Issuer", taxId()),
                    listOf(SubmissionRecord.Registration(otherRegistration), SubmissionRecord.Cancellation(otherCancellation)),
                ),
            )

        assertEquals(
            listOf("records[0].draft.invoice.issuer", "records[1].draft.cancelledInvoice.issuer"),
            invalid.report.issues.map { it.fieldPath },
        )
        assertTrue(invalid.report.issues.all { it.code == "VF-BATCH-003" })
    }

    @Test
    fun rejectsForgedHashesForBothRecordTypes() {
        val invalid =
            assertIs<SubmissionBatchBuildResult.Invalid>(
                SubmissionBatchBuilder.build(
                    SubmissionHeader("Issuer", taxId()),
                    listOf(
                        SubmissionRecord.Registration(createdRegistration().copy(hash = "0".repeat(64))),
                        SubmissionRecord.Cancellation(createdCancellation().copy(hash = "F".repeat(64))),
                    ),
                ),
            )

        assertEquals(listOf("records[0].hash", "records[1].hash"), invalid.report.issues.map { it.fieldPath })
        assertTrue(invalid.report.issues.all { it.code == "VF-BATCH-004" })
    }

    @Test
    fun prefixesDraftValidationIssuesForUncheckedRecords() {
        val registration = createdRegistration()
        val cancellation = createdCancellation()
        val invalid =
            assertIs<SubmissionBatchBuildResult.Invalid>(
                SubmissionBatchBuilder.build(
                    SubmissionHeader("Issuer", taxId()),
                    listOf(
                        SubmissionRecord.Registration(registration.copy(draft = registration.draft.copy(issuerName = ""))),
                        SubmissionRecord.Cancellation(
                            cancellation.copy(draft = cancellation.draft.copy(system = cancellation.draft.system.copy(producerName = ""))),
                        ),
                    ),
                ),
            )

        assertEquals(
            listOf("records[0].draft.issuerName", "records[1].draft.system.producerName"),
            invalid.report.issues.map { it.fieldPath },
        )
    }

    @Test
    fun keepsPreparedPayloadsAfterCallerOwnedListsChange() {
        val original = createdRegistration()
        val details =
            original.draft.taxBreakdown.details
                .toMutableList()
        val records =
            mutableListOf<SubmissionRecord>(
                SubmissionRecord.Registration(original.copy(draft = original.draft.copy(taxBreakdown = TaxBreakdown(details)))),
            )
        val prepared =
            assertIs<SubmissionBatchBuildResult.Created>(
                SubmissionBatchBuilder.build(SubmissionHeader("Issuer", taxId()), records),
            )
        val xml = prepared.xml
        val soap = prepared.soapEnvelope

        details.clear()
        records.clear()

        assertEquals(xml, prepared.xml)
        assertEquals(soap, prepared.soapEnvelope)
        assertContains(prepared.xml, "<DetalleDesglose>")
    }

    private fun createdRegistration(): RegistroAlta {
        val result =
            FiscalRecordFactory.createRegistration(
                RegistroAltaDraft(
                    version = RecordVersion.V1_0,
                    invoice = InvoiceIdentifier(taxId(), invoiceNumber(), issueDate()),
                    issuerName = "Issuer & Co",
                    invoiceType = InvoiceType.F1,
                    totalTax = amount("21.00"),
                    totalAmount = amount("121.00"),
                    operationDescription = "Consulting",
                    taxBreakdown =
                        TaxBreakdown(
                            listOf(
                                TaxBreakdownDetail(
                                    operation = TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT),
                                    taxableBase = amount("100.00"),
                                    tax = TaxType.IVA,
                                    regimeCode = "01",
                                    taxRate = "21",
                                    chargedTax = amount("21.00"),
                                ),
                            ),
                        ),
                    chainState = ChainState.FirstRecord,
                    system = SistemaInformatico("Producer", taxId(), "VeriFactu", "VF", "1.0", "install-1", true, false, false),
                    generatedAt = timestamp(),
                    conditionalData =
                        RegistrationConditionalData(
                            recipients = listOf(FiscalParty("Recipient", FiscalPartyIdentifier.SpanishNif(taxId()))),
                        ),
                ),
            )
        return (result as RecordCreationResult.Created<RegistroAlta>).record
    }

    private fun createdCancellation(): RegistroAnulacion {
        val result =
            FiscalRecordFactory.createCancellation(
                RegistroAnulacionDraft(
                    cancelledInvoice = InvoiceIdentifier(taxId(), invoiceNumber("CANCEL-1"), issueDate()),
                    chainState = ChainState.FirstRecord,
                    system = SistemaInformatico("Producer", taxId(), "VeriFactu", "VF", "1.0", "install-1", true, false, false),
                    generatedAt = timestamp("2024-01-01T10:00:01+01:00"),
                ),
            )
        return (result as RecordCreationResult.Created<RegistroAnulacion>).record
    }

    private fun taxId(): TaxIdentifier = (TaxIdentifier.parse("89890001K") as ValueResult.Valid).value

    private fun invoiceNumber(value: String = "TEST-1"): InvoiceNumber = (InvoiceNumber.parse(value) as ValueResult.Valid).value

    private fun issueDate(): InvoiceIssueDate = (InvoiceIssueDate.parse("01-01-2024") as ValueResult.Valid).value

    private fun timestamp(value: String = "2024-01-01T10:00:00+01:00"): RecordGenerationTimestamp =
        (RecordGenerationTimestamp.parse(value) as ValueResult.Valid).value

    private fun amount(value: String): FiscalAmount = (FiscalAmount.parse(value) as ValueResult.Valid).value
}
