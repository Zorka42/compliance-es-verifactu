package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatFlowControl
import dev.verifactu.aeat.AeatRecordStatus
import dev.verifactu.aeat.AeatResponseCorrelation
import dev.verifactu.aeat.AeatResponseCorrelationResult
import dev.verifactu.aeat.AeatResponseParseResult
import dev.verifactu.aeat.AeatResponseParser
import dev.verifactu.aeat.AeatSubmissionResponse
import dev.verifactu.aeat.AeatSubmissionStatus
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

internal enum class MatrixState { PENDING, PREPARED, PASS, NOT_SENT, UNKNOWN, REVIEW }

internal data class MatrixObservation(
    val state: MatrixState,
    val waitSeconds: Long? = null,
    val completedAt: OffsetDateTime? = null,
    val codes: List<String> = emptyList(),
    val startedAt: OffsetDateTime? = null,
)

internal fun matrixAttempts(
    session: MatrixSession,
    case: MatrixCase,
): List<Path> {
    val directory = session.directory(case)
    if (!Files.isDirectory(directory)) return emptyList()
    return Files.list(directory).use { paths ->
        paths.filter { it.fileName.toString().matches(Regex("attempt-[0-9]{3}")) }.sorted().collect(
            java.util.stream.Collectors
                .toList(),
        )
    }
}

internal fun observeMatrixCase(
    session: MatrixSession,
    case: MatrixCase,
): MatrixObservation =
    try {
        analyzeMatrixCase(session, case)
    } catch (
        _: java.io.IOException,
    ) {
        MatrixObservation(MatrixState.UNKNOWN)
    } catch (_: SandboxInputException) {
        MatrixObservation(MatrixState.REVIEW)
    }

private fun analyzeMatrixCase(
    session: MatrixSession,
    case: MatrixCase,
): MatrixObservation {
    val attempts = matrixAttempts(session, case)
    if (attempts.isEmpty()) {
        return MatrixObservation(
            if (Files.isDirectory(session.bundle(case))) MatrixState.PREPARED else MatrixState.PENDING,
        )
    }
    val attempt = attempts.last()
    if (!Files.isRegularFile(attempt.resolve("outcome.properties"))) return MatrixObservation(MatrixState.UNKNOWN)
    val outcome = readProperties(attempt.resolve("outcome.properties"))
    val bundle = loadBundle(session.bundle(case))
    verifyMatrixAttempt(bundle, case, attempt, outcome)
    if (outcome["delivery"] !in setOf("NOT_SENT", "RESPONSE_RECEIVED")) return MatrixObservation(MatrixState.UNKNOWN)
    val network = readProperties(attempt.resolve("network.log"))
    sandboxRequire(network["request.url"] == endpoint(bundle.values).url, "Unexpected intended matrix request route.")
    if (outcome["delivery"] == "NOT_SENT") {
        val safe = network["delivery"] == "NOT_SENT" && network["response.url"] == "unavailable"
        return MatrixObservation(if (safe) MatrixState.NOT_SENT else MatrixState.REVIEW)
    }
    sandboxRequire(
        outcome["transport.result"] == "XmlResponse" && outcome["http.status"]?.toIntOrNull() in 200..299,
        "Non-successful matrix response.",
    )
    sandboxRequire(
        network["response.url"] == endpoint(bundle.values).url,
        "Unexpected matrix response route.",
    )
    sandboxRequire(
        network["delivery"] == "RESPONSE_RECEIVED" && network["http.status"] == outcome["http.status"],
        "Incomplete native response receipt.",
    )
    return observeMatrixResponse(bundle, case, attempt)
}

private fun verifyMatrixAttempt(
    bundle: SandboxBundle,
    case: MatrixCase,
    attempt: Path,
    outcome: Map<String, String>,
) {
    val started = readProperties(attempt.resolve("started.properties"))
    sandboxRequire(
        started["request.sha256"] == bundle.sha256 && outcome["request.sha256"] == bundle.sha256,
        "Matrix request digest differs.",
    )
    sandboxRequire(readText(attempt.resolve("request.xml")) == bundle.plan.soap, "Matrix request bytes differ.")
    val marker = readProperties(attempt.resolve("matrix-case.properties"))
    sandboxRequire(
        marker["evidence.kind"] == "AEAT_TEST" && marker["case"] == case.id && marker["request.sha256"] == bundle.sha256,
        "Invalid matrix evidence marker.",
    )
}

private fun observeMatrixResponse(
    bundle: SandboxBundle,
    case: MatrixCase,
    attempt: Path,
): MatrixObservation {
    val parsed = AeatResponseParser.parseSubmission(readText(attempt.resolve("response.xml")))
    if (parsed !is AeatResponseParseResult.Parsed) return MatrixObservation(MatrixState.REVIEW)
    val response = parsed.response
    val matched = AeatResponseCorrelation.correlate(bundle.plan.records, response) is AeatResponseCorrelationResult.Matched
    val expected = expectedMatrixResponse(case, response)
    val codes =
        response.lines
            .mapNotNull { it.errorCode }
            .map { if (it.matches(Regex("[0-9]{4}"))) it else "UNKNOWN" }
            .distinct()
            .sorted()
            .take(20)
    val wait = (response.flowControl as? AeatFlowControl.Known)?.seconds?.toLong()
    val timing = readProperties(attempt.resolve("matrix-timing.properties"))
    val start = matrixTime(required(timing, "started.at"))
    val completed = matrixTime(required(timing, "completed.at"))
    val ordered = !completed.toInstant().isBefore(start.toInstant())
    val accepted = matched && expected
    val known = ordered && wait != null
    return MatrixObservation(if (accepted && known) MatrixState.PASS else MatrixState.REVIEW, wait, completed, codes, start)
}

private fun expectedMatrixResponse(
    case: MatrixCase,
    response: AeatSubmissionResponse,
): Boolean =
    when (case.id) {
        "warning" -> response.lines.all { it.status == AeatRecordStatus.ACCEPTED_WITH_ERRORS && it.errorCode == "2007" }
        "rejection" ->
            response.status == AeatSubmissionStatus.REJECTED &&
                response.lines.all { it.status == AeatRecordStatus.REJECTED && it.errorCode == "1152" }
        "duplicate" -> response.lines.all { it.status == AeatRecordStatus.DUPLICATE && it.errorCode in listOf(null, "3000") }
        else ->
            response.status == AeatSubmissionStatus.ACCEPTED &&
                response.lines.all { it.status == AeatRecordStatus.ACCEPTED && it.errorCode == null && it.errorDescription.isNullOrBlank() }
    }

internal fun requireMatrixDependency(
    session: MatrixSession,
    case: MatrixCase,
) {
    case.dependency?.let { id ->
        sandboxRequire(
            observeMatrixCase(session, session.case(id)).state == MatrixState.PASS,
            "Complete and verify the required prior case first: $id.",
        )
    }
}

internal fun matrixTime(text: String): OffsetDateTime =
    try {
        OffsetDateTime.parse(text)
    } catch (_: java.time.DateTimeException) {
        throw SandboxInputException("Invalid matrix evidence timestamp.")
    }
