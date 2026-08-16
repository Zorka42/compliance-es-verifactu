package dev.verifactu.xml

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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegistroXmlSerializerTest {
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
                                    taxRate = "21",
                                    chargedTax = amount("21.00"),
                                ),
                            ),
                        ),
                    chainState = ChainState.FirstRecord,
                    system = SistemaInformatico("Producer", taxId(), "VeriFactu", "VF", "1.0", "install-1", true, false, false),
                    generatedAt = timestamp(),
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
