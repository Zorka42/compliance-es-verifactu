package dev.verifactu.tools.sandbox

import java.time.OffsetDateTime
import java.time.ZoneId

internal fun matrixNow(): OffsetDateTime = OffsetDateTime.now(ZoneId.of("Europe/Madrid")).withNano(0)

internal fun runMatrixCommand(
    args: List<String>,
    clock: () -> OffsetDateTime = ::matrixNow,
): CommandResult {
    if (args.isEmpty() || args == listOf("--help")) return CommandResult(MATRIX_USAGE)
    val options = parseOptions(args.drop(1))
    if (args.first() == "init") return initializeMatrix(options, clock())
    options.allow("run", "case", "out")
    val session = loadMatrix(options.path("run"))
    return when (args.first()) {
        "prepare" -> {
            options.allow("run", "case")
            prepareMatrixCase(session, session.case(required(options, "case")), clock())
        }
        "show" -> {
            options.allow("run", "case")
            options["case"]?.let { showMatrixCase(session, session.case(it)) } ?: matrixOverview(session)
        }
        "report" -> {
            options.allow("run")
            matrixReport(session)
        }
        "rehearse" -> {
            options.allow("run", "out")
            rehearseMatrix(session, options.path("out"), clock())
        }
        else -> throw SandboxInputException(MATRIX_USAGE)
    }
}

internal const val MATRIX_USAGE: String =
    "TEST matrix: init --party-file FILE --producer-file FILE --out NEW_DIRECTORY; " +
        "prepare --run DIRECTORY --case NAME; show --run DIRECTORY [--case NAME]; report --run DIRECTORY; " +
        "rehearse --run DIRECTORY --out NEW_DIRECTORY. All these commands are local only. " +
        "Use the unified verifactu sandbox matrix send/query commands for separately authorized TEST operations."
