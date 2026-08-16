package dev.verifactu.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {
    @Test
    fun reportIsInvalidWhenItContainsAnError() {
        val report =
            ValidationReport(
                listOf(
                    ValidationIssue(
                        code = "VF-TEST-001",
                        fieldPath = "record.invoice",
                        severity = ValidationSeverity.ERROR,
                        message = "Required for test.",
                    ),
                ),
            )

        assertFalse(report.isValid)
    }

    @Test
    fun reportAllowsWarningsAndAssumptions() {
        val report =
            ValidationReport(
                listOf(
                    ValidationIssue("VF-TEST-002", "record", ValidationSeverity.WARNING, "Warning."),
                    ValidationIssue("VF-TEST-003", "record", ValidationSeverity.ASSUMPTION, "Assumption."),
                ),
            )

        assertTrue(report.isValid)
    }
}
