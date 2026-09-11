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
import dev.verifactu.core.RecordHashCalculator
import dev.verifactu.core.RecordVersion
import dev.verifactu.core.RectificationAmounts
import dev.verifactu.core.RectificationType
import dev.verifactu.core.RegistrationConditionalData
import dev.verifactu.core.RegistrationHashInput
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
import dev.verifactu.core.ValueResult
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class AeatSchemaValidationTest {
    @Test
    fun validatesTheGoldenRegistrationDocumentAgainstThePublishedAeatSchema() {
        schema().newValidator().validate(StreamSource(StringReader(REGISTRO_ALTA_V1_GOLDEN_XML)))
    }

    @Test
    fun rejectsAnElementValueOutsideThePublishedAeatSchema() {
        val invalidXml = REGISTRO_ALTA_V1_GOLDEN_XML.replace("<TipoHuella>01</TipoHuella>", "<TipoHuella>02</TipoHuella>")

        assertFailsWith<SAXException> {
            schema().newValidator().validate(StreamSource(StringReader(invalidXml)))
        }
    }

    @Test
    fun preservesTheHashedInvoiceIdentityAfterXmlParsing() {
        val record = registration("INV\r\n\t<&>\"'1", "2024-01-01T10:00:00+01:00", "Issuer\rName")
        val xml = RegistroXmlSerializer.serialize(record)
        schema().newValidator().validate(StreamSource(StringReader(xml)))
        val parsed = documents().newDocumentBuilder().parse(InputSource(StringReader(xml)))

        fun text(name: String): String = parsed.getElementsByTagName(name).item(0).textContent

        assertEquals(record.draft.invoice.number.value, text("NumSerieFactura"))
        assertEquals(record.draft.issuerName, text("NombreRazonEmisor"))
        val hashInput =
            RegistrationHashInput(
                issuerId = text("IDEmisorFactura"),
                invoiceNumber = text("NumSerieFactura"),
                issueDate = text("FechaExpedicionFactura"),
                invoiceType = text("TipoFactura"),
                totalTax = text("CuotaTotal"),
                totalAmount = text("ImporteTotal"),
                previousHash = null,
                generatedAt = text("FechaHoraHusoGenRegistro"),
            )
        assertEquals(text("Huella"), RecordHashCalculator.sha256(hashInput.canonicalString()))
    }

    @Test
    fun validatesTimestampAndSupplementaryCharacterBoundariesAgainstTheSchema() {
        val validator = schema().newValidator()
        listOf("2024-02-29T24:00:00+14:00", "2024-12-31T24:00:00-14:00", "2000-02-29T23:59:59+13:59").forEach { timestamp ->
            // JDK 22's default Xerces provider counts UTF-16 units (XERCESJ-1592), unlike W3C
            // XML Schema Part 2, 4.3.3. Keep this provider fixture valid under both counts;
            // common tests cover the normative 60/120-code-point supplementary boundaries.
            val xml = RegistroXmlSerializer.serialize(registration("\uD83D\uDE00".repeat(30), timestamp, "\uD83D\uDE00".repeat(60)))
            validator.validate(StreamSource(StringReader(xml)))
            val bmpXml = RegistroXmlSerializer.serialize(registration("A".repeat(60), timestamp, "A".repeat(120)))
            validator.validate(StreamSource(StringReader(bmpXml)))
        }
    }

    @Test
    fun recordsTheSelectedProviderLengthProfileWithoutChangingTheCommonContract() {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        val probe =
            factory.newSchema(
                StreamSource(
                    StringReader(
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                            "<xs:element name=\"value\"><xs:simpleType><xs:restriction base=\"xs:string\">" +
                            "<xs:maxLength value=\"1\"/></xs:restriction></xs:simpleType></xs:element></xs:schema>",
                    ),
                ),
            )
        val supplementary = "\uD83D\uDE00"
        val countsCodePoints = accepts(probe.newValidator(), "<value>$supplementary</value>")
        val profile = if (countsCodePoints) "CODE_POINTS" else "UTF16_UNITS"
        println("XSD provider=${factory.javaClass.name}; lengthProfile=$profile")
        val record = registration("INV-1", "2024-01-01T10:00:00+01:00", supplementary.repeat(120))
        val xml = RegistroXmlSerializer.serialize(record)
        val parsed = documents().newDocumentBuilder().parse(InputSource(StringReader(xml)))

        assertEquals(record.draft.issuerName, parsed.getElementsByTagName("NombreRazonEmisor").item(0).textContent)
        assertEquals(countsCodePoints, accepts(schema().newValidator(), xml), "Provider: ${factory.javaClass.name}")
        // JDK 22's default profile is UTF-16; an updated conforming provider may accept this boundary.
        // Both profiles must accept the intersection fixture and reject text over the W3C limit.
        assertEquals(true, accepts(schema().newValidator(), xml.replace(supplementary.repeat(120), supplementary.repeat(60))))
        assertEquals(false, accepts(schema().newValidator(), xml.replace(supplementary.repeat(120), supplementary.repeat(121))))
    }

    @Test
    fun validatesConditionalInvoiceTypesInTheCanonicalSchemaSequence() {
        val base = registration("INV-1", "2024-01-01T10:00:00+01:00", "Issuer").draft
        val amount = (FiscalAmount.parse("0.00") as ValueResult.Valid).value
        InvoiceType.entries.forEach { invoiceType ->
            val rectifying = invoiceType.name.startsWith("R")
            val conditional =
                RegistrationConditionalData(
                    externalReference = "external & reference",
                    subsanation = Subsanation.YES,
                    previousRejection = RegistrationPreviousRejection.YES,
                    rectification =
                        if (rectifying) {
                            InvoiceRectification(
                                RectificationType.REPLACEMENT,
                                listOf(base.invoice),
                                RectificationAmounts(amount, amount, amount),
                            )
                        } else {
                            null
                        },
                    replacedInvoices = if (invoiceType == InvoiceType.F3) listOf(base.invoice) else emptyList(),
                    operationDate = base.invoice.issueDate,
                    recipients =
                        if (invoiceType in listOf(InvoiceType.F2, InvoiceType.R5)) {
                            emptyList()
                        } else {
                            listOf(FiscalParty("Recipient", FiscalPartyIdentifier.SpanishNif(base.invoice.issuer)))
                        },
                    taxationAgreementRegistrationNumber = "agreement-1",
                    systemAgreementIdentifier = "system-agree-1",
                )
            val record =
                assertIs<RecordCreationResult.Created<RegistroAlta>>(
                    FiscalRecordFactory.createRegistration(base.copy(invoiceType = invoiceType, conditionalData = conditional)),
                ).record
            val xml = RegistroXmlSerializer.serialize(record)
            schema(includeBatch = true).newValidator().validate(StreamSource(StringReader(xml)))
        }
    }

    private fun accepts(
        validator: javax.xml.validation.Validator,
        xml: String,
    ): Boolean =
        try {
            validator.validate(StreamSource(StringReader(xml)))
            true
        } catch (_: SAXException) {
            false
        }

    @Test
    fun schemaAndLocalParserRejectImpossibleTimestampValues() {
        val validator = schema().newValidator()
        listOf("0000-01-01T00:00:00+00:00", "1900-02-29T00:00:00+00:00", "2024-02-29T24:00:01+00:00", "2024-02-29T00:00:00+14:01")
            .forEach { timestamp ->
                assertIs<ValueResult.Invalid>(RecordGenerationTimestamp.parse(timestamp))
                val xml = REGISTRO_ALTA_V1_GOLDEN_XML.replace("2024-01-01T10:00:00+01:00", timestamp)
                assertFailsWith<SAXException> { validator.validate(StreamSource(StringReader(xml))) }
            }
    }

    @Test
    fun validatesThePreparedBatchAndSoapBodyAgainstTheCanonicalBatchSchema() {
        val registration = registration("INV-1", "2024-01-01T10:00:00+01:00", "Issuer")
        val cancellation =
            assertIs<RecordCreationResult.Created<RegistroAnulacion>>(
                FiscalRecordFactory.createCancellation(
                    RegistroAnulacionDraft(
                        cancelledInvoice = registration.draft.invoice,
                        chainState = ChainState.PreviousRecord(registration.draft.invoice, registration.hash),
                        system = registration.draft.system,
                        generatedAt = (RecordGenerationTimestamp.parse("2024-01-01T10:00:01+01:00") as ValueResult.Valid).value,
                    ),
                ),
            ).record
        val prepared =
            assertIs<SubmissionBatchBuildResult.Created>(
                SubmissionBatchBuilder.build(
                    SubmissionHeader("Issuer\r & Co", registration.draft.invoice.issuer),
                    listOf(SubmissionRecord.Registration(registration), SubmissionRecord.Cancellation(cancellation)),
                ),
            )
        val validator = schema(includeBatch = true).newValidator()
        validator.validate(StreamSource(StringReader(prepared.xml)))

        val soap = documents().newDocumentBuilder().parse(InputSource(StringReader(prepared.soapEnvelope)))
        assertEquals("http://schemas.xmlsoap.org/soap/envelope/", soap.documentElement.namespaceURI)
        val body = soap.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Body").item(0)
        assertEquals(1, body.childNodes.length)
        validator.validate(DOMSource(body.firstChild))
        assertEquals("Issuer\r & Co", soap.getElementsByTagNameNS("*", "NombreRazon").item(0).textContent)
    }

    private fun registration(
        number: String,
        timestamp: String,
        issuerName: String,
    ): RegistroAlta {
        val taxId = (TaxIdentifier.parse("89890001K") as ValueResult.Valid).value
        val amount = (FiscalAmount.parse("0.00") as ValueResult.Valid).value
        val draft =
            RegistroAltaDraft(
                version = RecordVersion.V1_0,
                invoice =
                    InvoiceIdentifier(
                        taxId,
                        (InvoiceNumber.parse(number) as ValueResult.Valid).value,
                        (InvoiceIssueDate.parse("01-01-2024") as ValueResult.Valid).value,
                    ),
                issuerName = issuerName,
                invoiceType = InvoiceType.F2,
                totalTax = amount,
                totalAmount = amount,
                operationDescription = "Schema regression",
                taxBreakdown =
                    TaxBreakdown(
                        listOf(TaxBreakdownDetail(TaxOperation.Qualified(Qualification.NOT_SUBJECT), amount, regimeCode = "01")),
                    ),
                chainState = ChainState.FirstRecord,
                system = SistemaInformatico("Producer", taxId, "VeriFactu", "VF", "1.0", "install-1", true, false, false),
                generatedAt = (RecordGenerationTimestamp.parse(timestamp) as ValueResult.Valid).value,
            )
        // Raw XML/hash fixtures intentionally include schema-compatible identifiers outside the service profile.
        val hashInput =
            RegistrationHashInput(
                draft.invoice.issuer.value,
                draft.invoice.number.value,
                draft.invoice.issueDate.value,
                draft.invoiceType.name,
                draft.totalTax.value,
                draft.totalAmount.value,
                null,
                draft.generatedAt.value,
            )
        return RegistroAlta(draft, RecordHashCalculator.sha256(hashInput.canonicalString()))
    }

    private fun schema(includeBatch: Boolean = false): javax.xml.validation.Schema {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        val documents = documents()
        val names =
            if (includeBatch) {
                listOf("xmldsig-core-schema.xsd", "SuministroInformacion.xsd", "SuministroLR.xsd")
            } else {
                listOf("aeat-xsd/xmldsig-core-schema.xsd", "aeat-xsd/SuministroInformacion.xsd")
            }
        val sources =
            names.map { name ->
                checkNotNull(javaClass.classLoader.getResourceAsStream(name)).use { input ->
                    DOMSource(documents.newDocumentBuilder().parse(input))
                }
            }
        return factory.newSchema(sources.toTypedArray())
    }

    private fun documents(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
}
