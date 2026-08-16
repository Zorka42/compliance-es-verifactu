package dev.verifactu.core

/**
 * AEAT canonical hash input for a `RegistroAlta` record.
 *
 * Field order and names follow AEAT's “Detalle de las especificaciones técnicas para
 * generación de la huella o hash de los registros de facturación”, v0.1.2, section 3.
 */
public data class RegistrationHashInput(
    public val issuerId: String,
    public val invoiceNumber: String,
    public val issueDate: String,
    public val invoiceType: String,
    public val totalTax: String?,
    public val totalAmount: String,
    public val previousHash: String?,
    public val generatedAt: String,
) {
    /** Returns the exact UTF-8 source text used for the record hash. */
    public fun canonicalString(): String =
        listOf(
            "IDEmisorFactura=${issuerId.trim()}",
            "NumSerieFactura=${invoiceNumber.trim()}",
            "FechaExpedicionFactura=${issueDate.trim()}",
            "TipoFactura=${invoiceType.trim()}",
            "CuotaTotal=${totalTax.orEmpty().trim()}",
            "ImporteTotal=${totalAmount.trim()}",
            "Huella=${previousHash.orEmpty().trim()}",
            "FechaHoraHusoGenRegistro=${generatedAt.trim()}",
        ).joinToString(separator = "&")
}

/**
 * AEAT canonical hash input for a `RegistroAnulacion` record.
 *
 * Field order and names follow AEAT's hash specification v0.1.2, section 3.
 */
public data class CancellationHashInput(
    public val cancelledIssuerId: String,
    public val cancelledInvoiceNumber: String,
    public val cancelledIssueDate: String,
    public val previousHash: String?,
    public val generatedAt: String,
) {
    /** Returns the exact UTF-8 source text used for the record hash. */
    public fun canonicalString(): String =
        listOf(
            "IDEmisorFacturaAnulada=${cancelledIssuerId.trim()}",
            "NumSerieFacturaAnulada=${cancelledInvoiceNumber.trim()}",
            "FechaExpedicionFacturaAnulada=${cancelledIssueDate.trim()}",
            "Huella=${previousHash.orEmpty().trim()}",
            "FechaHoraHusoGenRegistro=${generatedAt.trim()}",
        ).joinToString(separator = "&")
}

/** SHA-256 utility with a KMP-safe, deterministic implementation. */
public object RecordHashCalculator {
    /** Calculates the uppercase hexadecimal SHA-256 hash of [value]'s UTF-8 bytes. */
    public fun sha256(value: String): String = Sha256.digest(value.encodeToByteArray()).toHex()
}

private object Sha256 {
    private val roundConstants: IntArray =
        intArrayOf(
            0x428a2f98,
            0x71374491,
            0xb5c0fbcf.toInt(),
            0xe9b5dba5.toInt(),
            0x3956c25b,
            0x59f111f1,
            0x923f82a4.toInt(),
            0xab1c5ed5.toInt(),
            0xd807aa98.toInt(),
            0x12835b01,
            0x243185be,
            0x550c7dc3,
            0x72be5d74,
            0x80deb1fe.toInt(),
            0x9bdc06a7.toInt(),
            0xc19bf174.toInt(),
            0xe49b69c1.toInt(),
            0xefbe4786.toInt(),
            0x0fc19dc6,
            0x240ca1cc,
            0x2de92c6f,
            0x4a7484aa,
            0x5cb0a9dc,
            0x76f988da,
            0x983e5152.toInt(),
            0xa831c66d.toInt(),
            0xb00327c8.toInt(),
            0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(),
            0xd5a79147.toInt(),
            0x06ca6351,
            0x14292967,
            0x27b70a85,
            0x2e1b2138,
            0x4d2c6dfc,
            0x53380d13,
            0x650a7354,
            0x766a0abb,
            0x81c2c92e.toInt(),
            0x92722c85.toInt(),
            0xa2bfe8a1.toInt(),
            0xa81a664b.toInt(),
            0xc24b8b70.toInt(),
            0xc76c51a3.toInt(),
            0xd192e819.toInt(),
            0xd6990624.toInt(),
            0xf40e3585.toInt(),
            0x106aa070,
            0x19a4c116,
            0x1e376c08,
            0x2748774c,
            0x34b0bcb5,
            0x391c0cb3,
            0x4ed8aa4a,
            0x5b9cca4f,
            0x682e6ff3,
            0x748f82ee,
            0x78a5636f,
            0x84c87814.toInt(),
            0x8cc70208.toInt(),
            0x90befffa.toInt(),
            0xa4506ceb.toInt(),
            0xbef9a3f7.toInt(),
            0xc67178f2.toInt(),
        )

    fun digest(input: ByteArray): ByteArray {
        val padded = pad(input)
        var h0 = 0x6a09e667
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        for (offset in padded.indices step 64) {
            val words = IntArray(64)
            for (index in 0 until 16) {
                val start = offset + index * 4
                words[index] = ((padded[start].toInt() and 0xff) shl 24) or
                    ((padded[start + 1].toInt() and 0xff) shl 16) or
                    ((padded[start + 2].toInt() and 0xff) shl 8) or
                    (padded[start + 3].toInt() and 0xff)
            }
            for (index in 16 until 64) {
                val s0 = words[index - 15].rotateRight(7) xor words[index - 15].rotateRight(18) xor (words[index - 15] ushr 3)
                val s1 = words[index - 2].rotateRight(17) xor words[index - 2].rotateRight(19) xor (words[index - 2] ushr 10)
                words[index] = words[index - 16] + s0 + words[index - 7] + s1
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            var f = h5
            var g = h6
            var h = h7
            for (index in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val choose = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + choose + roundConstants[index] + words[index]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + majority
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            h5 += f
            h6 += g
            h7 += h
        }

        return intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
            .flatMap { word ->
                listOf(
                    (word ushr 24).toByte(),
                    (word ushr 16).toByte(),
                    (word ushr 8).toByte(),
                    word.toByte(),
                )
            }.toByteArray()
    }

    private fun pad(input: ByteArray): ByteArray {
        val bitLength = input.size.toLong() * 8L
        val paddingLength = ((56 - ((input.size + 1) % 64) + 64) % 64) + 1
        val result = ByteArray(input.size + paddingLength + 8)
        input.copyInto(result)
        result[input.size] = 0x80.toByte()
        for (index in 0 until 8) {
            result[result.lastIndex - index] = (bitLength ushr (index * 8)).toByte()
        }
        return result
    }
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(radix = 16).padStart(length = 2, padChar = '0')
    }.uppercase()
