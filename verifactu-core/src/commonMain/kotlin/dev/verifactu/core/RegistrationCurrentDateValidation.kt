package dev.verifactu.core

internal fun registrationCurrentDateIssues(
    draft: RegistroAltaDraft,
    context: ValidationContext,
): List<ValidationIssue> {
    val current =
        context.aeatCurrentDate?.calendarParts()
            ?: return listOf(
                dateIssue(
                    "CONTEXT-UNKNOWN",
                    "validationContext.aeatCurrentDate",
                    "AEAT's current date is unknown; issue-date and operation-date current-date checks were not performed.",
                    "3.1.3.1, p.8; 3.1.3.7, p.9",
                ),
            )
    return buildList {
        if (draft.invoice.issueDate
                .calendarParts()
                .ordered > current.ordered
        ) {
            add(dateError("1112", "invoice.issueDate", "The issue date exceeds the caller-supplied AEAT current date.", "3.1.3.1, p.8"))
        }
        val operation = draft.conditionalData.operationDate?.calendarParts() ?: return@buildList
        addAll(operationLowerBoundIssues(operation, current))
        addAll(operationUpperBoundIssues(operation, current))
        if (operation.ordered > current.ordered) addAll(futureOperationRegimeIssues(draft))
    }
}

private fun operationLowerBoundIssues(
    operation: CalendarParts,
    current: CalendarParts,
): List<ValidationIssue> {
    val lowerYear = current.year - 20
    // AEAT does not define clamping versus rolling an invalid leap-day anniversary.
    val invalidAnniversary = current.monthDay == 229 && !isLeapYear(lowerYear)
    if (invalidAnniversary && operation.year == lowerYear && operation.monthDay == 228) {
        return listOf(
            dateIssue(
                "OPERATION-LEAP-BOUND-UNRESOLVED",
                "conditionalData.operationDate",
                "The twenty-year lower bound lands on a nonexistent leap day; AEAT's boundary convention is unresolved.",
                "3.1.3.7, p.9; errores.properties 1134",
            ),
        )
    }
    return if (operation.ordered < lowerYear * 10000 + current.monthDay) {
        listOf(
            dateError(
                "1134",
                "conditionalData.operationDate",
                "The operation date precedes the caller-supplied AEAT current date minus twenty years.",
                "3.1.3.7, p.9",
            ),
        )
    } else {
        emptyList()
    }
}

private fun operationUpperBoundIssues(
    operation: CalendarParts,
    current: CalendarParts,
): List<ValidationIssue> =
    when {
        operation.year > current.year + 1 ->
            listOf(
                dateError(
                    "1125",
                    "conditionalData.operationDate",
                    "The operation date is beyond the year following the caller-supplied AEAT current date.",
                    "3.1.3.7, p.9",
                ),
            )
        operation.year == current.year + 1 && operation.monthDay > current.monthDay ->
            listOf(
                dateIssue(
                    "OPERATION-UPPER-BOUND-UNRESOLVED",
                    "conditionalData.operationDate",
                    "The operation date is beyond the earliest possible one-year anniversary but within the following calendar year; " +
                        "AEAT's exact upper boundary is unresolved.",
                    "3.1.3.7, p.9; errores.properties 1125",
                ),
            )
        else -> emptyList()
    }

private fun futureOperationRegimeIssues(draft: RegistroAltaDraft): List<ValidationIssue> =
    draft.taxBreakdown.details.mapIndexedNotNull { index, detail ->
        val ivaOrIgic = detail.tax == null || detail.tax == TaxType.IVA || detail.tax == TaxType.IGIC
        if (ivaOrIgic && detail.regimeCode !in setOf("14", "15")) {
            dateError(
                "1173",
                "taxBreakdown.details[$index].regimeCode",
                "Each IVA or IGIC detail requires regime 14 or 15 when the operation date exceeds AEAT's current date.",
                "3.1.3.7, p.9",
            )
        } else {
            null
        }
    }

private fun dateError(
    aeatCode: String,
    field: String,
    message: String,
    section: String,
): ValidationIssue =
    dateIssue(aeatCode, field, message, "$section; errores.properties $aeatCode")
        .copy(severity = ValidationSeverity.ERROR, aeatCode = aeatCode)

private fun dateIssue(
    code: String,
    field: String,
    message: String,
    section: String,
): ValidationIssue =
    ValidationIssue(
        "VF-DATE-$code",
        field,
        ValidationSeverity.WARNING,
        message,
        source = ComplianceSourceReference("AEAT validation and error catalogue", section, "1.2.2 (2026-04-08)"),
    )

private data class CalendarParts(
    val year: Int,
    val monthDay: Int,
) {
    val ordered: Int get() = year * 10000 + monthDay
}

private fun InvoiceIssueDate.calendarParts(): CalendarParts =
    CalendarParts(value.substring(6, 10).toInt(), value.substring(3, 5).toInt() * 100 + value.substring(0, 2).toInt())

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
