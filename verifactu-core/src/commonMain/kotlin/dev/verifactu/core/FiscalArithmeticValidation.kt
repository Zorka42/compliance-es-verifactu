package dev.verifactu.core

internal fun fiscalArithmeticIssues(draft: RegistroAltaDraft): List<ValidationIssue> {
    val details = draft.taxBreakdown.details
    // The schema bounds both the number of details and amount precision; invalid counts are reported separately.
    if (details.size !in 1..12) return emptyList()
    return buildList {
        if (draft.conditionalData.rectification?.type != RectificationType.BY_DIFFERENCE &&
            draft.invoiceType != InvoiceType.R2 &&
            draft.invoiceType != InvoiceType.R3
        ) {
            details.forEachIndexed { index, detail -> addAll(chargedTaxArithmeticIssues(detail, index)) }
        }
        val baseTotal = details.sumOf { it.taxableBase.cents() }
        val chargedTotal = details.sumOf { it.chargedTax?.cents() ?: 0L }
        val checkSimplifiedLimit =
            draft.invoiceType == InvoiceType.F2 &&
                draft.conditionalData.taxationAgreementRegistrationNumber == null &&
                draft.conditionalData.recipientIdentificationExemption != RecipientIdentificationExemption.YES
        if (checkSimplifiedLimit && baseTotal + chargedTotal > 301_000L) {
            add(
                arithmeticIssue(
                    "1150",
                    "taxBreakdown.details",
                    "The F2 base and charged-tax sum exceeds EUR 3,000 plus EUR 10 tolerance.",
                    "3.1.3.15.8",
                ),
            )
        }
        if (details.none { it.regimeCode in TOTAL_CHECK_EXEMPT_REGIMES }) {
            val taxTotal = chargedTotal + details.sumOf { it.equivalenceSurcharge?.cents() ?: 0L }
            if (outsideTotalTolerance(draft.totalTax.cents(), taxTotal)) {
                add(
                    arithmeticIssue(
                        "2006",
                        "totalTax",
                        "The total tax differs from the detail tax and surcharge sum by more than EUR 10.",
                        "3.1.3.16",
                        ValidationSeverity.WARNING,
                    ),
                )
            }
            if (outsideTotalTolerance(draft.totalAmount.cents(), baseTotal + taxTotal)) {
                add(
                    arithmeticIssue(
                        "2005",
                        "totalAmount",
                        "The total amount differs from the detail base, tax and surcharge sum by more than EUR 10.",
                        "3.1.3.17",
                        ValidationSeverity.WARNING,
                    ),
                )
            }
        }
    }
}

private fun chargedTaxArithmeticIssues(
    detail: TaxBreakdownDetail,
    index: Int,
): List<ValidationIssue> {
    if ((detail.operation as? TaxOperation.Qualified)?.value != Qualification.SUBJECT_NOT_EXEMPT) return emptyList()
    val tax = detail.chargedTax?.cents() ?: return emptyList()
    val base = (detail.costBase ?: detail.taxableBase).cents()
    val rate = detail.taxRate?.takeIf { RATE_FORMAT.matches(it) }?.decimalHundredths() ?: return emptyList()
    val path = "taxBreakdown.details[$index].chargedTax"
    val signCode = if (detail.costBase == null) "1143" else "1140"
    val amountCode = if (detail.costBase == null) "1142" else "1144"
    return buildList {
        if (base.compareTo(0L) * tax.compareTo(0L) < 0) {
            add(
                arithmeticIssue(
                    signCode,
                    path,
                    "Charged tax and its effective base have opposite signs.",
                    "3.1.3.15.7",
                ),
            )
        }
        if (outsideChargedTaxTolerance(base, rate, tax)) {
            add(
                arithmeticIssue(
                    amountCode,
                    path,
                    "Charged tax differs from effective base times rate divided by 100 by more than EUR 10.",
                    "3.1.3.15.7",
                ),
            )
        }
    }
}

private fun outsideChargedTaxTolerance(
    base: Long,
    rate: Long,
    tax: Long,
): Boolean {
    // Divide before multiplying: a 12.2 amount times a 3.2 rate can overflow Long before division.
    // The whole cents and signed remainder retain the exact rational value without rounding.
    val remainderProduct = (base % 10_000L) * rate
    val wholeCents = (base / 10_000L) * rate + remainderProduct / 10_000L
    val remainder = remainderProduct % 10_000L
    val lower = tax - 1_000L
    val upper = tax + 1_000L
    val below = wholeCents < lower || (wholeCents == lower && remainder < 0L)
    val above = wholeCents > upper || (wholeCents == upper && remainder > 0L)
    return below || above
}

private fun outsideTotalTolerance(
    actual: Long,
    expected: Long,
): Boolean = actual < expected - 1_000L || actual > expected + 1_000L

private fun FiscalAmount.cents(): Long = value.decimalHundredths()

private fun String.decimalHundredths(): Long {
    val unsigned = removePrefix("-").removePrefix("+")
    val whole = unsigned.substringBefore('.').toLong()
    val fraction = unsigned.substringAfter('.', "").padEnd(2, '0').toLong()
    val magnitude = whole * 100L + fraction
    return if (startsWith('-')) -magnitude else magnitude
}

private fun arithmeticIssue(
    code: String,
    path: String,
    message: String,
    section: String,
    severity: ValidationSeverity = ValidationSeverity.ERROR,
): ValidationIssue =
    ValidationIssue(
        "VF-AMOUNT-$code",
        path,
        severity,
        message,
        code,
        ComplianceSourceReference("AEAT validation and error catalogue", section, "1.2.2 (2026-04-08); archived errores.properties"),
    )

private val RATE_FORMAT: Regex = Regex("[0-9]{1,3}(?:\\.[0-9]{0,2})?")
private val TOTAL_CHECK_EXEMPT_REGIMES: Set<String> = setOf("03", "05", "06", "08", "09")
