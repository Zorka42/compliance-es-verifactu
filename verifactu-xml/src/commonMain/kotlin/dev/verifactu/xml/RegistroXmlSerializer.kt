package dev.verifactu.xml

import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalParty
import dev.verifactu.core.FiscalPartyIdentifier
import dev.verifactu.core.InvoiceRectification
import dev.verifactu.core.RegistroAlta
import dev.verifactu.core.RegistroAnulacion
import dev.verifactu.core.TaxBreakdownDetail
import dev.verifactu.core.TaxOperation

/** Deterministically serializes completed fiscal records in the AEAT v1.0 element order. */
public object RegistroXmlSerializer {
    /** Serializes an immutable registration record as a UTF-8 XML document. */
    public fun serialize(record: RegistroAlta): String =
        document("RegistroAlta") {
            val draft = record.draft
            element("IDVersion", draft.version.xmlValue)
            invoiceId("IDFactura", draft.invoice, false)
            val conditional = draft.conditionalData
            registrationExternalReference(conditional)
            element("NombreRazonEmisor", draft.issuerName)
            registrationConditionalPrefix(conditional)
            element("TipoFactura", draft.invoiceType.name)
            element("DescripcionOperacion", draft.operationDescription)
            registrationConditionalDetails(conditional)
            element("Desglose") {
                draft.taxBreakdown.details.forEach { taxDetail ->
                    element("DetalleDesglose") { breakdownDetail(taxDetail) }
                }
            }
            element("CuotaTotal", draft.totalTax.value)
            element("ImporteTotal", draft.totalAmount.value)
            chain(draft.chainState)
            system(draft.system)
            element("FechaHoraHusoGenRegistro", draft.generatedAt.value)
            registrationConditionalAgreements(conditional)
            element("TipoHuella", "01")
            element("Huella", record.hash)
        }

    /** Serializes an immutable cancellation record as a UTF-8 XML document. */
    public fun serialize(record: RegistroAnulacion): String =
        document("RegistroAnulacion") {
            val draft = record.draft
            element("IDVersion", "1.0")
            invoiceId("IDFactura", draft.cancelledInvoice, true)
            chain(draft.chainState)
            system(draft.system)
            element("FechaHoraHusoGenRegistro", draft.generatedAt.value)
            element("TipoHuella", "01")
            element("Huella", record.hash)
        }
}

private const val AEAT_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/" +
        "aeat/tike/cont/ws/SuministroInformacion.xsd"

@Suppress("TooManyFunctions")
private class XmlWriter {
    private val output: StringBuilder = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")

    fun document(
        rootName: String,
        content: XmlWriter.() -> Unit,
    ): String {
        output
            .append('<')
            .append(rootName)
            .append(" xmlns=\"")
            .append(AEAT_NAMESPACE)
            .append("\">")
        content()
        output.append("</").append(rootName).append('>')
        return output.toString()
    }

    fun element(
        name: String,
        value: String,
    ) {
        output
            .append('<')
            .append(name)
            .append('>')
            .append(value.escapeXml())
            .append("</")
            .append(name)
            .append('>')
    }

    fun element(
        name: String,
        content: XmlWriter.() -> Unit,
    ) {
        output.append('<').append(name).append('>')
        content()
        output.append("</").append(name).append('>')
    }

    fun invoiceId(
        name: String,
        invoice: dev.verifactu.core.InvoiceIdentifier,
        cancelled: Boolean,
    ) {
        element(name) {
            val suffix = if (cancelled) "Anulada" else ""
            element("IDEmisorFactura$suffix", invoice.issuer.value)
            element("NumSerieFactura$suffix", invoice.number.value)
            element("FechaExpedicionFactura$suffix", invoice.issueDate.value)
        }
    }

    fun rectification(rectification: InvoiceRectification) {
        element("TipoRectificativa", rectification.type.xmlValue)
        if (rectification.rectifiedInvoices.isNotEmpty()) {
            element("FacturasRectificadas") {
                rectification.rectifiedInvoices.forEach { invoiceId("IDFacturaRectificada", it, false) }
            }
        }
        rectification.replacedAmounts?.let { amounts ->
            element("ImporteRectificacion") {
                element("BaseRectificada", amounts.taxableBase.value)
                element("CuotaRectificada", amounts.chargedTax.value)
                amounts.equivalenceSurcharge?.let { element("CuotaRecargoRectificado", it.value) }
            }
        }
    }

    fun registrationConditionalPrefix(conditional: dev.verifactu.core.RegistrationConditionalData) {
        conditional.subsanation?.let { element("Subsanacion", it.xmlValue) }
        conditional.previousRejection?.let { element("RechazoPrevio", it.xmlValue) }
        conditional.rectification?.let { rectification(it) }
        if (conditional.replacedInvoices.isNotEmpty()) {
            element("FacturasSustituidas") {
                conditional.replacedInvoices.forEach { invoiceId("IDFacturaSustituida", it, false) }
            }
        }
        conditional.operationDate?.let { element("FechaOperacion", it.value) }
    }

    fun registrationExternalReference(conditional: dev.verifactu.core.RegistrationConditionalData) {
        conditional.externalReference?.let { element("RefExterna", it) }
    }

    fun registrationConditionalDetails(conditional: dev.verifactu.core.RegistrationConditionalData) {
        conditional.simplifiedInvoiceQualification?.let { element("FacturaSimplificadaArt7273", it.xmlValue) }
        conditional.recipientIdentificationExemption?.let { element("FacturaSinIdentifDestinatarioArt61d", it.xmlValue) }
        conditional.macroData?.let { element("Macrodato", it.xmlValue) }
        conditional.generatedBy?.let { element("EmitidaPorTerceroODestinatario", it.xmlValue) }
        conditional.thirdParty?.let { element("Tercero") { fiscalParty(it) } }
        if (conditional.recipients.isNotEmpty()) {
            element("Destinatarios") {
                conditional.recipients.forEach { recipient -> element("IDDestinatario") { fiscalParty(recipient) } }
            }
        }
        conditional.coupon?.let { element("Cupon", it.xmlValue) }
    }

    fun registrationConditionalAgreements(conditional: dev.verifactu.core.RegistrationConditionalData) {
        conditional.taxationAgreementRegistrationNumber?.let { element("NumRegistroAcuerdoFacturacion", it) }
        conditional.systemAgreementIdentifier?.let { element("IdAcuerdoSistemaInformatico", it) }
    }

    fun fiscalParty(party: FiscalParty) {
        element("NombreRazon", party.name)
        when (val identifier = party.identifier) {
            is FiscalPartyIdentifier.SpanishNif -> element("NIF", identifier.value.value)
            is FiscalPartyIdentifier.Other ->
                element("IDOtro") {
                    identifier.countryCode?.let { element("CodigoPais", it) }
                    element("IDType", identifier.type.xmlValue)
                    element("ID", identifier.value)
                }
        }
    }

    fun chain(state: ChainState) {
        element("Encadenamiento") {
            when (state) {
                ChainState.FirstRecord -> element("PrimerRegistro", "S")
                is ChainState.PreviousRecord ->
                    element("RegistroAnterior") {
                        element("IDEmisorFactura", state.invoice.issuer.value)
                        element("NumSerieFactura", state.invoice.number.value)
                        element("FechaExpedicionFactura", state.invoice.issueDate.value)
                        element("Huella", state.hash)
                    }
            }
        }
    }

    fun system(system: dev.verifactu.core.SistemaInformatico) {
        element("SistemaInformatico") {
            element("NombreRazon", system.producerName)
            element("NIF", system.producerTaxIdentifier.value)
            element("NombreSistemaInformatico", system.systemName)
            element("IdSistemaInformatico", system.systemIdentifier)
            element("Version", system.version)
            element("NumeroInstalacion", system.installationNumber)
            element("TipoUsoPosibleSoloVerifactu", if (system.veriFactuOnly) "S" else "N")
            element("TipoUsoPosibleMultiOT", if (system.supportsMultipleTaxpayers) "S" else "N")
            element("IndicadorMultiplesOT", if (system.hasMultipleTaxpayers) "S" else "N")
        }
    }

    fun breakdownDetail(detail: TaxBreakdownDetail) {
        detail.tax?.let { element("Impuesto", it.xmlValue) }
        detail.regimeCode?.let { element("ClaveRegimen", it) }
        when (val operation = detail.operation) {
            is TaxOperation.Qualified -> element("CalificacionOperacion", operation.value.xmlValue)
            is TaxOperation.Exempt -> element("OperacionExenta", operation.value.xmlValue)
        }
        detail.taxRate?.let { element("TipoImpositivo", it) }
        element("BaseImponibleOimporteNoSujeto", detail.taxableBase.value)
        detail.costBase?.let { element("BaseImponibleACoste", it.value) }
        detail.chargedTax?.let { element("CuotaRepercutida", it.value) }
        detail.equivalenceSurchargeRate?.let { element("TipoRecargoEquivalencia", it) }
        detail.equivalenceSurcharge?.let { element("CuotaRecargoEquivalencia", it.value) }
    }
}

private fun document(
    rootName: String,
    content: XmlWriter.() -> Unit,
): String = XmlWriter().document(rootName, content)

private fun String.escapeXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
