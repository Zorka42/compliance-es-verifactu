package dev.verifactu.xml

import dev.verifactu.core.RegistroAlta
import dev.verifactu.core.RegistroAnulacion
import dev.verifactu.core.TaxIdentifier
import kotlin.jvm.JvmStatic

/** Header data required by the AEAT `RegFactuSistemaFacturacion` batch root. */
public data class SubmissionHeader(
    public val issuerName: String,
    public val issuerTaxIdentifier: TaxIdentifier,
)

/** A completed fiscal record that can appear in an AEAT batch. */
public sealed interface SubmissionRecord {
    /** A registration record. */
    public data class Registration(
        public val value: RegistroAlta,
    ) : SubmissionRecord

    /** A cancellation record. */
    public data class Cancellation(
        public val value: RegistroAnulacion,
    ) : SubmissionRecord
}

/**
 * Deterministically serializes the AEAT batch document without transport behaviour.
 * This low-level API checks record count only. It does not enforce taxpayer consistency,
 * revalidate record hashes, or wrap the document in SOAP. See the pending validated batch builder.
 */
public object SubmissionBatchXmlSerializer {
    /**
     * Serializes one to one thousand completed records in caller-supplied order.
     * @throws IllegalArgumentException when the record count is outside that range.
     */
    @JvmStatic
    public fun serialize(
        header: SubmissionHeader,
        records: List<SubmissionRecord>,
    ): String {
        require(records.size in 1..1000) { "An AEAT batch must contain from one to one thousand records." }
        val recordXml =
            records.joinToString(separator = "") { record ->
                "<RegistroFactura>${record.documentXml()}</RegistroFactura>"
            }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<RegFactuSistemaFacturacion xmlns=\"$BATCH_NAMESPACE\">" +
            "<Cabecera><ObligadoEmision xmlns=\"$RECORD_NAMESPACE\"><NombreRazon>${header.issuerName.escapeBatchXml()}" +
            "</NombreRazon><NIF>${header.issuerTaxIdentifier.value}</NIF></ObligadoEmision></Cabecera>" +
            recordXml +
            "</RegFactuSistemaFacturacion>"
    }
}

private fun SubmissionRecord.documentXml(): String = serializedRecord().removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")

private fun SubmissionRecord.serializedRecord(): String =
    when (this) {
        is SubmissionRecord.Registration -> RegistroXmlSerializer.serialize(value)
        is SubmissionRecord.Cancellation -> RegistroXmlSerializer.serialize(value)
    }

private fun String.escapeBatchXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

private const val BATCH_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/" +
        "aeat/tike/cont/ws/SuministroLR.xsd"
private const val RECORD_NAMESPACE: String =
    "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/" +
        "aeat/tike/cont/ws/SuministroInformacion.xsd"
