package dev.verifactu.aeat

import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.xml.SubmissionRecord
import kotlin.jvm.JvmStatic

/** Structural correlation failures; none imply that a fiscal record was accepted or rejected. */
public enum class AeatCorrelationIssueCode {
    EMPTY_SUBMISSION,
    AMBIGUOUS_SUBMISSION_KEY,
    MISSING_RESPONSE_LINE,
    UNEXPECTED_RESPONSE_LINE,
    AMBIGUOUS_RESPONSE_KEY,
    MISSING_RESPONSE_IDENTITY,
    UNSUPPORTED_OPERATION,
    UNSUPPORTED_OPERATION_FLAGS,
    OPERATION_FLAGS_MISMATCH,
}

/** Stable issue code and zero-based positions without embedding fiscal identifiers or remote text. */
public data class AeatCorrelationIssue(
    public val code: AeatCorrelationIssueCode,
    public val submittedIndex: Int? = null,
    public val responseIndex: Int? = null,
)

/** Result of matching invoice identity and operation, independent of acceptance and retry policy. */
public sealed interface AeatResponseCorrelationResult {
    /**
     * Exactly one response line per input record, reordered to the original submission order.
     * Unknown statuses, duplicate details, and rejections remain unchanged and require inspection.
     */
    public data class Matched(
        public val lines: List<AeatResponseLine>,
    ) : AeatResponseCorrelationResult {
        /** Omits invoice identifiers and remote diagnostics. */
        override fun toString(): String = "Matched(lines=${lines.size}, details=redacted)"
    }

    /** The response cannot be uniquely correlated to the supplied batch. */
    public data class Mismatch(
        public val issues: List<AeatCorrelationIssue>,
    ) : AeatResponseCorrelationResult {
        /** Reports only the number of structural issues. */
        override fun toString(): String = "Mismatch(issues=${issues.size})"
    }
}

/**
 * Pure correlation using `IDFactura` and `Operacion` from the pinned AEAT response schemas.
 *
 * This does not infer acceptance, interpret error codes, validate record hashes, or decide retries.
 * Repeated invoice-plus-operation keys are ambiguous. An alta and anulacion for the same invoice
 * are distinct. Registration correction flags must match the submitted conditional data;
 * unknown flag text and cancellation flags not represented by the submitted model cannot match.
 */
public object AeatResponseCorrelation {
    /** Matches the complete response while retaining every original response state. */
    @JvmStatic
    public fun correlate(
        submitted: List<SubmissionRecord>,
        response: AeatSubmissionResponse,
    ): AeatResponseCorrelationResult {
        val records = submitted.toList()
        val expected = records.map { it.correlationKey() }
        val lines = response.lines.toList()
        val issues = mutableListOf<AeatCorrelationIssue>()
        if (expected.isEmpty()) issues.add(AeatCorrelationIssue(AeatCorrelationIssueCode.EMPTY_SUBMISSION))
        val actual = lines.mapIndexed { index, line -> responseKey(line, index, issues) }
        val expectedCounts = expected.groupingBy { it }.eachCount()
        val actualCounts = actual.filterNotNull().groupingBy { it }.eachCount()
        expected.forEachIndexed { index, key ->
            if (expectedCounts.getValue(key) > 1) {
                issues.add(AeatCorrelationIssue(AeatCorrelationIssueCode.AMBIGUOUS_SUBMISSION_KEY, submittedIndex = index))
            }
            if (actualCounts[key] == null) {
                issues.add(AeatCorrelationIssue(AeatCorrelationIssueCode.MISSING_RESPONSE_LINE, submittedIndex = index))
            }
        }
        actual.forEachIndexed { index, key ->
            if (key != null) {
                if (actualCounts.getValue(key) > 1) {
                    issues.add(AeatCorrelationIssue(AeatCorrelationIssueCode.AMBIGUOUS_RESPONSE_KEY, responseIndex = index))
                }
                if (expectedCounts[key] == null) {
                    issues.add(AeatCorrelationIssue(AeatCorrelationIssueCode.UNEXPECTED_RESPONSE_LINE, responseIndex = index))
                }
            }
        }
        if (issues.isNotEmpty()) return AeatResponseCorrelationResult.Mismatch(issues.toList())
        val indices = actual.withIndex().associate { it.value to it.index }
        expected.forEachIndexed { submittedIndex, key ->
            val responseIndex = indices.getValue(key)
            if (records[submittedIndex].operationFlags() != lines[responseIndex].operation?.operationFlags()) {
                issues.add(
                    AeatCorrelationIssue(
                        AeatCorrelationIssueCode.OPERATION_FLAGS_MISMATCH,
                        submittedIndex = submittedIndex,
                        responseIndex = responseIndex,
                    ),
                )
            }
        }
        if (issues.isNotEmpty()) return AeatResponseCorrelationResult.Mismatch(issues.toList())
        return AeatResponseCorrelationResult.Matched(expected.map { lines[indices.getValue(it)] })
    }
}

private fun responseKey(
    line: AeatResponseLine,
    index: Int,
    issues: MutableList<AeatCorrelationIssue>,
): CorrelationKey? {
    val invoice = line.invoice
    if (invoice == null) {
        issues.add(AeatCorrelationIssue(AeatCorrelationIssueCode.MISSING_RESPONSE_IDENTITY, responseIndex = index))
        return null
    }
    val operation = line.operation
    if (operation == null || operation.type == AeatOperationType.UNKNOWN_STATE || operation.rawType != operation.type.protocolValue()) {
        issues.add(AeatCorrelationIssue(AeatCorrelationIssueCode.UNSUPPORTED_OPERATION, responseIndex = index))
        return null
    }
    if (operation.subsanacion !in listOf(null, "N", "S") ||
        operation.rechazoPrevio !in listOf(null, "N", "S", "X") ||
        operation.sinRegistroPrevio !in listOf(null, "N", "S")
    ) {
        issues.add(AeatCorrelationIssue(AeatCorrelationIssueCode.UNSUPPORTED_OPERATION_FLAGS, responseIndex = index))
    }
    return CorrelationKey(invoice, operation.type)
}

private fun SubmissionRecord.correlationKey(): CorrelationKey =
    when (this) {
        is SubmissionRecord.Registration -> CorrelationKey(value.draft.invoice.responseReference(), AeatOperationType.REGISTRATION)
        is SubmissionRecord.Cancellation -> CorrelationKey(value.draft.cancelledInvoice.responseReference(), AeatOperationType.CANCELLATION)
    }

private fun InvoiceIdentifier.responseReference(): AeatInvoiceReference = AeatInvoiceReference(issuer.value, number.value, issueDate.value)

private fun SubmissionRecord.operationFlags(): OperationFlags =
    when (this) {
        is SubmissionRecord.Registration ->
            OperationFlags(
                value.draft.conditionalData.subsanation
                    ?.xmlValue ?: "N",
                value.draft.conditionalData.previousRejection
                    ?.xmlValue ?: "N",
                "N",
            )
        is SubmissionRecord.Cancellation -> OperationFlags("N", "N", "N")
    }

private fun AeatResponseOperation.operationFlags(): OperationFlags =
    OperationFlags(subsanacion ?: "N", rechazoPrevio ?: "N", sinRegistroPrevio ?: "N")

private fun AeatOperationType.protocolValue(): String? =
    when (this) {
        AeatOperationType.REGISTRATION -> "Alta"
        AeatOperationType.CANCELLATION -> "Anulacion"
        AeatOperationType.UNKNOWN_STATE -> null
    }

private data class CorrelationKey(
    val invoice: AeatInvoiceReference,
    val operation: AeatOperationType,
)

private data class OperationFlags(
    val subsanation: String,
    val previousRejection: String,
    val withoutPreviousRecord: String,
)
