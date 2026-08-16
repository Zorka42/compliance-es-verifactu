package dev.verifactu.core

/** Severity assigned to a deterministic local validation issue. */
public enum class ValidationSeverity {
    ERROR,
    WARNING,
    ASSUMPTION,
}

/** Reference to a source used for a compliance-sensitive validation rule. */
public data class ComplianceSourceReference(
    public val document: String,
    public val section: String,
    public val version: String,
)

/** A stable, machine-readable validation outcome for a record field. */
public data class ValidationIssue(
    public val code: String,
    public val fieldPath: String,
    public val severity: ValidationSeverity,
    public val message: String,
    public val aeatCode: String? = null,
    public val source: ComplianceSourceReference? = null,
)

/** The complete side-effect-free result of validating a value. */
public data class ValidationReport(
    public val issues: List<ValidationIssue>,
) {
    /** Whether this report contains no error-severity issues. */
    public val isValid: Boolean = issues.none { issue -> issue.severity == ValidationSeverity.ERROR }
}

/** A deterministic validator that performs no I/O. */
public fun interface Validator<T> {
    /** Validates [value] and returns every issue discovered locally. */
    public fun validate(value: T): ValidationReport
}
