package dev.verifactu.xml

import dev.verifactu.core.ChainState
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
            element("NombreRazonEmisor", draft.issuerName)
            element("TipoFactura", draft.invoiceType.name)
            element("DescripcionOperacion", draft.operationDescription)
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
