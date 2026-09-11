package dev.verifactu.core

internal fun applyValidationContext(
    draft: RegistroAltaDraft,
    baseReport: ValidationReport,
    context: ValidationContext,
): ValidationReport {
    val receiptDate = context.aeatReceiptDate ?: return baseReport
    val date = receiptDate.value
    val dateKey = date.substring(6, 10).toInt() * 10_000 + date.substring(3, 5).toInt() * 100 + date.substring(0, 2).toInt()
    val rejects = dateKey >= 20270101
    return ValidationReport(
        baseReport.issues.map { issue ->
            if (issue.code != "VF-TAX-IPSI-TRANSITION") return@map issue
            val index =
                draft.taxBreakdown.details.indices
                    .firstOrNull { issue.fieldPath == "taxBreakdown.details[$it].regimeCode" }
                    ?: return@map issue
            val missing = draft.taxBreakdown.details[index].regimeCode == null
            val aeatCode =
                when {
                    rejects && missing -> "1245"
                    rejects -> "1246"
                    missing -> "2009"
                    else -> null
                }
            val outcome =
                if (rejects) {
                    "requires rejection from 2027-01-01."
                } else {
                    "specifies a warning through 2026-12-31; this does not establish acceptance."
                }
            issue.copy(
                severity = if (rejects) ValidationSeverity.ERROR else ValidationSeverity.WARNING,
                message =
                    "The IPSI regime is missing or outside the permitted list. " +
                        "At caller-supplied AEAT receipt date ${receiptDate.value}, section 3.1.3.15.6 " +
                        outcome,
                aeatCode = aeatCode,
                source =
                    ComplianceSourceReference(
                        "AEAT validation and error catalogue",
                        "3.1.3.15.6; errores.properties 1245, 1246, 2009",
                        "1.2.2 (2026-04-08)",
                    ),
            )
        },
    )
}
