package dev.verifactu.core

import kotlin.jvm.JvmStatic

/** A typed result for invalid primitive-domain construction without using exceptions. */
public sealed interface ValueResult<out T> {
    /** The parsed value. */
    public data class Valid<T>(
        public val value: T,
    ) : ValueResult<T>

    /** The failure explaining why the supplied input cannot form the target value. */
    public data class Invalid(
        public val error: ValueError,
    ) : ValueResult<Nothing>
}

/** Stable construction-error data for KMP consumers. */
public data class ValueError(
    public val code: String,
    public val fieldPath: String,
    public val message: String,
)

/**
 * Spanish issuer NIF with the schema's nine-character length constraint.
 * Parsing trims and uppercases XML-compatible input; it does not verify a checksum or taxpayer registration.
 */
@ConsistentCopyVisibility
public data class TaxIdentifier private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses the nine-character NIF representation accepted by the AEAT schema. */
        @JvmStatic
        public fun parse(input: String): ValueResult<TaxIdentifier> {
            val normalized = input.trim().uppercase()
            return if (normalized.xmlCharacterCountOrNull() == 9) {
                ValueResult.Valid(TaxIdentifier(normalized))
            } else {
                ValueResult.Invalid(ValueError("VF-VALUE-001", "taxIdentifier", "A NIF must contain exactly 9 XML-compatible characters."))
            }
        }
    }
}

/** XML-compatible invoice serial number in the AEAT `TextoIDFacturaType` length range; parsing trims its edges. */
@ConsistentCopyVisibility
public data class InvoiceNumber private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses a non-empty invoice serial number up to 60 characters. */
        @JvmStatic
        public fun parse(input: String): ValueResult<InvoiceNumber> {
            val normalized = input.trim()
            return if ((normalized.xmlCharacterCountOrNull() ?: 0) in 1..60) {
                ValueResult.Valid(InvoiceNumber(normalized))
            } else {
                ValueResult.Invalid(
                    ValueError("VF-VALUE-002", "invoiceNumber", "An invoice number must contain 1 to 60 XML-compatible characters."),
                )
            }
        }
    }
}

/** Invoice date formatted exactly as `DD-MM-YYYY` for AEAT record data. */
@ConsistentCopyVisibility
public data class InvoiceIssueDate private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses a calendar-valid date formatted as `DD-MM-YYYY`. */
        @JvmStatic
        public fun parse(input: String): ValueResult<InvoiceIssueDate> {
            val normalized = input.trim()
            val match =
                DATE_PATTERN.matchEntire(normalized)
                    ?: return ValueResult.Invalid(ValueError("VF-VALUE-003", "invoiceIssueDate", "A date must use DD-MM-YYYY format."))
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            if (!isCalendarDate(year, month, day)) {
                return ValueResult.Invalid(ValueError("VF-VALUE-004", "invoiceIssueDate", "The date is not a valid calendar day."))
            }
            return ValueResult.Valid(InvoiceIssueDate(normalized))
        }

        private val DATE_PATTERN: Regex = Regex("([0-9]{2})-([0-9]{2})-([0-9]{4})")
    }
}

/**
 * Signed monetary text in the AEAT `ImporteSgn12.2Type` format.
 * Parsing preserves sign, leading zeros, and scale. It performs no arithmetic or tax calculation.
 */
@ConsistentCopyVisibility
public data class FiscalAmount private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses an amount with at most twelve integral and two fractional digits. */
        @JvmStatic
        public fun parse(input: String): ValueResult<FiscalAmount> {
            val normalized = input.trim()
            return if (AMOUNT_PATTERN.matches(normalized)) {
                ValueResult.Valid(FiscalAmount(normalized))
            } else {
                ValueResult.Invalid(
                    ValueError("VF-VALUE-005", "fiscalAmount", "An amount must use a period and at most two decimal digits."),
                )
            }
        }

        private val AMOUNT_PATTERN: Regex = Regex("[+-]?\\d{1,12}(?:\\.\\d{0,2})?")
    }
}

/**
 * Generation timestamp with a four-digit positive year, whole seconds, and a numeric UTC offset.
 * Validates the calendar and XML Schema 1.0 `dateTime` ranges without reading a clock.
 * Preserves the supplied representation, including `24:00:00` and signed zero offsets.
 */
@ConsistentCopyVisibility
public data class RecordGenerationTimestamp private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses an AEAT-compatible generation timestamp with an explicit offset. */
        @JvmStatic
        public fun parse(input: String): ValueResult<RecordGenerationTimestamp> {
            val normalized = input.trim()
            val match = TIMESTAMP_PATTERN.matchEntire(normalized)
            if (match == null || !isValidTimestamp(match.groupValues)) {
                return ValueResult.Invalid(
                    ValueError(
                        "VF-VALUE-006",
                        "recordGenerationTimestamp",
                        "A timestamp must be a valid calendar date and time with an ISO 8601 numeric UTC offset.",
                    ),
                )
            }
            return ValueResult.Valid(RecordGenerationTimestamp(normalized))
        }

        private val TIMESTAMP_PATTERN: Regex =
            Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})[+-]([0-9]{2}):([0-9]{2})")

        private fun isValidTimestamp(parts: List<String>): Boolean {
            val year = parts[1].toInt()
            val month = parts[2].toInt()
            val day = parts[3].toInt()
            val hour = parts[4].toInt()
            val minute = parts[5].toInt()
            val second = parts[6].toInt()
            val offsetHour = parts[7].toInt()
            val offsetMinute = parts[8].toInt()
            // XML Schema Part 2 (2004), 3.2.7.1 and 3.2.7.3: midnight and offset boundaries.
            val midnight = hour == 24 && minute == 0 && second == 0
            val validTime = (hour in 0..23 || midnight) && minute in 0..59 && second in 0..59
            val validOffset = offsetHour in 0..14 && offsetMinute in 0..59 && (offsetHour < 14 || offsetMinute == 0)
            return year != 0 && isCalendarDate(year, month, day) && validTime && validOffset
        }
    }
}

private fun isCalendarDate(
    year: Int,
    month: Int,
    day: Int,
): Boolean {
    val daysInMonth =
        when (month) {
            2 -> if (year % 400 == 0 || year % 4 == 0 && year % 100 != 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    return month in 1..12 && day in 1..daysInMonth
}

// XML 1.0 Fifth Edition, 2.2 [2] Char; XSD string length counts characters, not UTF-16 code units.
internal fun String.xmlCharacterCountOrNull(): Int? {
    var count = 0
    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            character in '\uD800'..'\uDBFF' -> {
                if (index + 1 >= length || this[index + 1] !in '\uDC00'..'\uDFFF') return null
                index++
            }
            character == '\t' || character == '\n' || character == '\r' -> Unit
            character in '\u0020'..'\uD7FF' || character in '\uE000'..'\uFFFD' -> Unit
            else -> return null
        }
        count++
        index++
    }
    return count
}
