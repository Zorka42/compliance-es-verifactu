package dev.verifactu.qr

import dev.verifactu.core.FiscalAmount
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.ValidationIssue
import dev.verifactu.core.ValidationReport
import dev.verifactu.core.ValidationSeverity

/** AEAT environments that define the QR verification endpoint. */
public enum class QrEnvironment {
    TEST,
    PRODUCTION,
}

/** Immutable input for an AEAT VERI*FACTU QR payload. */
public data class QrPayloadInput(
    public val invoice: InvoiceIdentifier,
    public val totalAmount: FiscalAmount,
    public val environment: QrEnvironment,
)

/** A QR payload URL. QR image rendering remains an application responsibility. */
public data class QrPayload(
    public val url: String,
)

/** Typed result of QR payload construction. */
public sealed interface QrPayloadResult {
    /** A valid URL payload. */
    public data class Created(
        public val payload: QrPayload,
    ) : QrPayloadResult

    /** Deterministic validation failure(s). */
    public data class Invalid(
        public val report: ValidationReport,
    ) : QrPayloadResult
}

/** Builds deterministic AEAT QR verification URLs using the v0.5.0 parameter order. */
public object QrPayloadBuilder {
    /** Builds a QR payload or returns local typed validation issues. */
    public fun build(input: QrPayloadInput): QrPayloadResult {
        val serial = input.invoice.number.value
        if (serial.any { character -> character.code !in 32..126 }) {
            return QrPayloadResult.Invalid(
                ValidationReport(
                    listOf(
                        ValidationIssue(
                            code = "VF-QR-001",
                            fieldPath = "invoice.number",
                            severity = ValidationSeverity.ERROR,
                            message = "The QR invoice number must contain only printable ASCII characters.",
                            source = QR_SOURCE,
                        ),
                    ),
                ),
            )
        }
        val baseUrl =
            when (input.environment) {
                QrEnvironment.TEST -> "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR"
                QrEnvironment.PRODUCTION -> "https://www2.agenciatributaria.gob.es/wlpl/TIKE-CONT/ValidarQR"
            }
        val query =
            listOf(
                "nif=${encodeQueryValue(input.invoice.issuer.value)}",
                "numserie=${encodeQueryValue(serial)}",
                "fecha=${encodeQueryValue(input.invoice.issueDate.value)}",
                "importe=${encodeQueryValue(input.totalAmount.value)}",
            ).joinToString(separator = "&")
        return QrPayloadResult.Created(QrPayload("$baseUrl?$query"))
    }
}

private val QR_SOURCE: dev.verifactu.core.ComplianceSourceReference =
    dev.verifactu.core.ComplianceSourceReference(
        document = "AEAT QR technical specification",
        section = "Sections 4–6",
        version = "0.5.0",
    )

private fun encodeQueryValue(value: String): String =
    buildString {
        value.encodeToByteArray().forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            if (isUnreservedAscii(unsigned)) {
                append(unsigned.toChar())
            } else {
                append('%')
                append(HEX[unsigned ushr 4])
                append(HEX[unsigned and 0x0f])
            }
        }
    }

private fun isUnreservedAscii(value: Int): Boolean =
    when (value) {
        in 65..90, in 97..122, in 48..57, 45, 46, 95, 126 -> true
        else -> false
    }

private const val HEX: String = "0123456789ABCDEF"
