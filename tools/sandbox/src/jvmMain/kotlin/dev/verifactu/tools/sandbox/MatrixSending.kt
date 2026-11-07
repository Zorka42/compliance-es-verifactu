package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal fun sendMatrixCase(
    options: Map<String, String>,
    transportFactory: (Path, Int) -> AeatTransportAdapter,
    clock: () -> OffsetDateTime,
): CommandResult {
    options.allow("run", "case", "confirm-sha256", "confirm-records", "issuer")
    val session = loadMatrix(options.path("run"))
    val case = session.case(required(options, "case"))
    requireMatrixDependency(session, case)
    val observations = MATRIX_CASES.map { observeMatrixCase(session, it) }
    sandboxRequire(
        observations.none {
            it.state in setOf(MatrixState.UNKNOWN, MatrixState.REVIEW)
        },
        "Uncertain or unexpected evidence exists. Reconcile it before any further TEST write.",
    )
    sandboxRequire(matrixFlowControl(observations) != "REVIEW", "Saved flow-control timing requires reconciliation before another write.")
    val current = observeMatrixCase(session, case)
    sandboxRequire(
        current.state in setOf(MatrixState.PREPARED, MatrixState.NOT_SENT),
        "Prepare this case first; completed cases are not sent again implicitly.",
    )
    val bundle = loadBundle(session.bundle(case))
    sandboxRequire(bundle.values["mode"] == "aeat-test", "Offline rehearsal bundles cannot be sent.")
    sandboxRequire(required(options, "confirm-sha256") == bundle.sha256, "Confirm the SHA-256 from the reviewed matrix show output.")
    sandboxRequire(
        required(options, "confirm-records").toIntOrNull() == case.records && bundle.plan.records.size == case.records,
        "Confirm the exact planned record count.",
    )
    val previous = observations.filter { it.completedAt != null }.maxByOrNull { it.completedAt!!.toInstant() }
    val now = clock()
    if (previous != null) {
        val deadline = previous.completedAt!!.plusSeconds(previous.waitSeconds!!)
        sandboxRequire(
            !now.isBefore(deadline),
            "WAIT: next write is allowed after $deadline. No sleep, Keychain access or request occurred.",
        )
    }
    val attempts = matrixAttempts(session, case)
    sandboxRequire(attempts.size < 99, "Matrix attempt limit reached; preserve and review this session.")
    val output = session.directory(case).resolve("attempt-${(attempts.size + 1).toString().padStart(3, '0')}")
    val selected = transportFactory(output, case.records)
    createPrivateDirectory(output)
    writePrivate(
        output.resolve("matrix-case.properties"),
        propertiesText(
            mapOf(
                "case" to case.id,
                "evidence.kind" to "AEAT_TEST",
                "request.sha256" to bundle.sha256,
            ),
        ),
    )
    val result =
        deliverSandboxBundle(
            bundle,
            output,
            object : AeatTransportAdapter {
                override fun execute(request: AeatTransportRequest): AeatTransportResult {
                    val reply =
                        try {
                            selected.execute(request)
                        } catch (
                            _: Exception,
                        ) {
                            AeatTransportResult.UnknownDelivery("Native attempt did not provide reliable delivery evidence.")
                        }
                    writePrivate(
                        output.resolve("matrix-timing.properties"),
                        propertiesText(
                            mapOf(
                                "started.at" to now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                "completed.at" to clock().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                            ),
                        ),
                    )
                    return reply
                }
            },
        )
    val observation = observeMatrixCase(session, case)
    return result.copy(
        exitCode =
            if (observation.state ==
                MatrixState.PASS
            ) {
                0
            } else if (result.exitCode ==
                0
            ) {
                3
            } else {
                result.exitCode
            },
        output =
            result.output + "\nExpected case verification: ${observation.state}" +
                "\nEvidence directory: $output\nNext local check: ${matrixCommand(session.root, "report")}",
    )
}
