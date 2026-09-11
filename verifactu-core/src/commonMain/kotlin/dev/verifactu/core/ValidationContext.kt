package dev.verifactu.core

import kotlin.jvm.JvmOverloads

/**
 * External facts supplied explicitly by the host for local validation.
 *
 * A null receipt date is unknown. The library never substitutes generation time, reads a clock,
 * or establishes receipt or taxpayer/history knowledge from this context.
 */
public data class ValidationContext
    @JvmOverloads
    constructor(
        /** AEAT receipt calendar date supplied by the caller; null when not established. */
        public val aeatReceiptDate: InvoiceIssueDate? = null,
    )
