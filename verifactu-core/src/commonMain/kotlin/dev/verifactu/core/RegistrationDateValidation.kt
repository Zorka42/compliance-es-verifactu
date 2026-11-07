package dev.verifactu.core

internal fun registrationDateIssues(draft: RegistroAltaDraft): List<ValidationIssue> {
    val date = draft.invoice.issueDate.value
    val ordered = date.substring(6, 10) + date.substring(3, 5) + date.substring(0, 2)
    if (ordered >= "20241028") return emptyList()
    return listOf(
        ValidationIssue(
            "VF-DATE-1152",
            "invoice.issueDate",
            ValidationSeverity.ERROR,
            "A registration issue date must not precede 28-10-2024.",
            "1152",
            ComplianceSourceReference(
                "AEAT validation and error catalogue",
                "3.1.3.1, p.8; errores.properties 1152",
                "1.2.2 (2026-04-08)",
            ),
        ),
    )
}
