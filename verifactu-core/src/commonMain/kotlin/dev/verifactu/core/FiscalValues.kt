package dev.verifactu.core

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

/** Spanish issuer NIF as required by the AEAT v1.0 schemas. */
@ConsistentCopyVisibility
public data class TaxIdentifier private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses the nine-character NIF representation accepted by the AEAT schema. */
        public fun parse(input: String): ValueResult<TaxIdentifier> {
            val normalized = input.trim().uppercase()
            return if (normalized.length == 9) {
                ValueResult.Valid(TaxIdentifier(normalized))
            } else {
                ValueResult.Invalid(ValueError("VF-VALUE-001", "taxIdentifier", "A NIF must contain exactly 9 characters."))
            }
        }
    }
}

/** Invoice serial number in the AEAT `TextoIDFacturaType` range. */
@ConsistentCopyVisibility
public data class InvoiceNumber private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses a non-empty invoice serial number up to 60 characters. */
        public fun parse(input: String): ValueResult<InvoiceNumber> {
            val normalized = input.trim()
            return if (normalized.length in 1..60) {
                ValueResult.Valid(InvoiceNumber(normalized))
            } else {
                ValueResult.Invalid(ValueError("VF-VALUE-002", "invoiceNumber", "An invoice number must contain 1 to 60 characters."))
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
        public fun parse(input: String): ValueResult<InvoiceIssueDate> {
            val normalized = input.trim()
            val match =
                DATE_PATTERN.matchEntire(normalized)
                    ?: return ValueResult.Invalid(ValueError("VF-VALUE-003", "invoiceIssueDate", "A date must use DD-MM-YYYY format."))
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            if (month !in 1..12 || day !in 1..daysInMonth(month, year)) {
                return ValueResult.Invalid(ValueError("VF-VALUE-004", "invoiceIssueDate", "The date is not a valid calendar day."))
            }
            return ValueResult.Valid(InvoiceIssueDate(normalized))
        }

        private val DATE_PATTERN: Regex = Regex("(\\d{2})-(\\d{2})-(\\d{4})")

        private fun daysInMonth(
            month: Int,
            year: Int,
        ): Int =
            when (month) {
                2 -> if (year % 400 == 0 || year % 4 == 0 && year % 100 != 0) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
    }
}

/** Signed monetary amount conforming to AEAT `ImporteSgn12.2Type`. */
@ConsistentCopyVisibility
public data class FiscalAmount private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses an amount with at most twelve integral and two fractional digits. */
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

/** ISO 8601 date-time with a required numeric UTC offset. */
@ConsistentCopyVisibility
public data class RecordGenerationTimestamp private constructor(
    public val value: String,
) {
    public companion object {
        /** Parses an AEAT-compatible generation timestamp with an explicit offset. */
        public fun parse(input: String): ValueResult<RecordGenerationTimestamp> {
            val normalized = input.trim()
            return if (TIMESTAMP_PATTERN.matches(normalized)) {
                ValueResult.Valid(RecordGenerationTimestamp(normalized))
            } else {
                ValueResult.Invalid(
                    ValueError("VF-VALUE-006", "recordGenerationTimestamp", "A timestamp must include an ISO 8601 numeric UTC offset."),
                )
            }
        }

        private val TIMESTAMP_PATTERN: Regex = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:[+-]\\d{2}:\\d{2})")
    }
}
