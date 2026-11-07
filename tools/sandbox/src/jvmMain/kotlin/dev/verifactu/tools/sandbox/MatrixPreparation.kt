package dev.verifactu.tools.sandbox

import java.nio.file.Files
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal fun prepareMatrixCase(
    session: MatrixSession,
    case: MatrixCase,
    now: OffsetDateTime,
): CommandResult {
    requireMatrixDependency(session, case)
    if (!Files.exists(session.directory(case))) createMatrixBundle(session, case, now, "aeat-test")
    val bundle = loadBundle(session.bundle(case))
    return CommandResult(
        bundleSummary(bundle) + "\n" +
            "Local preview: ${matrixCommand(session.root, "show", "--case", case.id)}\n" +
            "Separately authorized TEST write: ${matrixCommand(
                session.root,
                "send",
                "--case",
                case.id,
                "--confirm-sha256",
                bundle.sha256,
                "--confirm-records",
                case.records.toString(),
            )}\n" +
            "No request sent. Existing prepared bytes are immutable; this command never refreshes a prior fiscal record.",
    )
}

internal fun createMatrixBundle(
    session: MatrixSession,
    case: MatrixCase,
    now: OffsetDateTime,
    mode: String,
): SandboxBundle {
    val directory = session.directory(case)
    createPrivateDirectory(directory)
    if (case.scenario == "duplicate") {
        return prepareBundle(
            BundlePreparation(null, session.bundle(case), "duplicate", mode, "standard", session.bundle(session.case("registration"))),
        )
    }
    val values =
        session.inputs.toMutableMap().apply {
            put("invoice.number", session.properties.getValue("invoice.prefix") + "-" + case.invoiceSuffix)
            put("generated.at", now.withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            if (case.id == "rejection") put("invoice.date", "27-10-2024")
            if (case.scenario == "pagination") {
                put("batch.count", case.records.toString())
                put("batch.start", case.first.toString())
                put("system.installation", getValue("system.installation") + "-paging")
            }
        }
    val config = directory.resolve("inputs.properties")
    writePrivate(config, propertiesText(values))
    val previous = case.dependency?.let { session.bundle(session.case(it)) }
    return prepareBundle(BundlePreparation(config, session.bundle(case), case.scenario, mode, "standard", previous))
}

internal fun matrixOverview(session: MatrixSession): CommandResult {
    val lines =
        MATRIX_CASES.map { case ->
            "${case.id}\t${case.records}\t${observeMatrixCase(session, case).state}\t${scenarioExpectation(case.scenario)}"
        }
    val next =
        MATRIX_CASES.firstOrNull { case ->
            observeMatrixCase(session, case).state != MatrixState.PASS &&
                (case.dependency == null || observeMatrixCase(session, session.case(case.dependency)).state == MatrixState.PASS)
        }
    return CommandResult(
        "TEST matrix; no network or Keychain access.\ncase\trecords\tstate\texpectation\n" + lines.joinToString("\n") +
            "\n" +
            (
                next?.let { "Next local action: ${matrixCommand(session.root, "prepare", "--case", it.id)}" }
                    ?: "All write cases have matching evidence; query/report checks remain."
            ),
    )
}

internal fun showMatrixCase(
    session: MatrixSession,
    case: MatrixCase,
): CommandResult = runCommand(listOf("show", "--bundle", session.bundle(case).toString()))

internal fun matrixReport(session: MatrixSession): CommandResult {
    val observations = MATRIX_CASES.associate { it.id to observeMatrixCase(session, it) }
    val flow = matrixFlowControl(observations.values)
    val complete = observations.values.all { it.state == MatrixState.PASS } && flow == "PASS"
    val output =
        "environment=TEST\n" +
            observations.entries.joinToString("\n") { (id, result) ->
                "case.$id.state=${result.state}\ncase.$id.codes=${result.codes.joinToString(
                    ",",
                )}\ncase.$id.flow.seconds=${result.waitSeconds ?: "unavailable"}"
            }
    return CommandResult(
        output + "\nflow.control=$flow\nwrite.matrix=${if (complete) "PASS" else "INCOMPLETE"}\n" +
            "Query verification must cover both roles, expected states and actual pagination; a write-only report does not complete ZA-111.",
        if (complete) 0 else 3,
    )
}
