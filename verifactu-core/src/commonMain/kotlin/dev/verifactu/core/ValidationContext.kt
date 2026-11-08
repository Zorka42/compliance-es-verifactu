package dev.verifactu.core

import kotlin.jvm.JvmOverloads

/**
 * External facts supplied explicitly by the host for local validation.
 *
 * Null dates are unknown. The library never substitutes one date for another, reads a clock,
 * or establishes receipt, AEAT's system date or taxpayer/history knowledge from this context.
 * Hypothetical dates yield conditional preflight results, not evidence of AEAT acceptance.
 */
public data class ValidationContext
    @JvmOverloads
    constructor(
        /** AEAT receipt calendar date supplied by the caller; null when not established. */
        public val aeatReceiptDate: InvoiceIssueDate? = null,
        /** AEAT system calendar date used for current-date rules; independent of receipt date. */
        public val aeatCurrentDate: InvoiceIssueDate? = null,
    )
