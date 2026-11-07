package dev.verifactu.tools.query

import dev.verifactu.tools.sandbox.SandboxCommandResult
import dev.verifactu.tools.sandbox.SandboxWorkflow
import java.nio.file.Path

internal fun matrixHelp(words: List<String>): String {
    queryRequire(words.size <= 1 && (words.isEmpty() || words.single() in MATRIX_ACTIONS), "Unknown matrix action.")
    val usage =
        when (words.firstOrNull()) {
            "init" -> "init --producer-file FILE --out NEW_DIRECTORY [--party-file FILE]"
            "prepare" -> "prepare --run DIRECTORY --case NAME"
            "show" -> "show --run DIRECTORY [--case NAME]"
            "send" -> "send --run DIRECTORY --case NAME --confirm-sha256 DIGEST --confirm-records COUNT [--issuer NAME]"
            "query" -> "query --run DIRECTORY --scope small|pagination [--issuer NAME]"
            "report" -> "report --run DIRECTORY"
            "rehearse" -> "rehearse --run DIRECTORY --out NEW_DIRECTORY"
            else -> "<init|prepare|show|send|query|report|rehearse> [flags]"
        }
    return "USAGE\n  verifactu sandbox matrix $usage\n\n" +
        "init uses the locally configured taxpayer by default; producer file contains exactly nif and name.\n" +
        "Cases: registration, duplicate, warning, subsanation, cancellation, rejection, pagination-01..11.\n" +
        "prepare creates fresh fiscal timestamps only once; show reviews exact XML and digest.\n" +
        "send requires separately authorized TEST writes and a fresh native identity selection.\n" +
        "query is read-only, both directions, session month; report inspects saved evidence locally.\n" +
        "Pagination requires 10001 synthetic records in 11 individually confirmed batches.\n" +
        "rehearse builds the entire matrix offline; it cannot establish live acceptance.\n" +
        "No implicit commands, automatic retries, sleeping, production routes or private-key export."
}

internal fun matrixCli(
    action: String,
    options: Map<String, String>,
    settings: CliSettings,
    fetch: (Map<String, String>) -> QueryRunResult,
): CliResult {
    if (action == "send") return matrixSend(options)
    if (action == "query") return queryMatrix(options, fetch)
    if (action == "report") {
        options.allow("run")
        val result = SandboxWorkflow.execute(listOf("matrix", "report", "--run", options.required("run")))
        if (result.exitCode == 2) return matrixResult(result)
        val queries = verifyMatrixQueries(Path.of(options.required("run")))
        return CliResult(result.output + "\n" + queries.output, exitCode = if (result.exitCode == 0 && queries.exitCode == 0) 0 else 3)
    }
    val supplied =
        if (action == "init" &&
            "party-file" !in options
        ) {
            options + ("party-file" to settings.taxpayerFile.toString())
        } else {
            options
        }
    return matrixResult(SandboxWorkflow.execute(listOf("matrix", action) + supplied.flatMap { (key, value) -> listOf("--$key", value) }))
}

internal fun matrixResult(result: SandboxCommandResult): CliResult =
    if (result.exitCode == 0) CliResult(result.output) else CliResult(diagnostic = result.output, exitCode = result.exitCode)
