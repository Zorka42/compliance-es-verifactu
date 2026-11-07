package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatResponseCorrelation
import dev.verifactu.aeat.AeatResponseCorrelationResult
import dev.verifactu.aeat.AeatResponseParseResult
import dev.verifactu.aeat.AeatResponseParser
import dev.verifactu.aeat.AeatTransportResult
import java.nio.file.Path
import java.time.OffsetDateTime

internal fun rehearseMatrix(
    session: MatrixSession,
    output: Path,
    now: OffsetDateTime,
): CommandResult {
    createPrivateDirectory(output)
    createPrivateDirectory(output.resolve("cases"))
    val rehearsal = session.copy(root = output)
    val lines =
        MATRIX_CASES.map { case ->
            val bundle = createMatrixBundle(rehearsal, case, now, "offline")
            val kind =
                when (case.id) {
                    "warning" -> "warning"
                    "rejection" -> "rejected"
                    "duplicate" -> "duplicate"
                    else -> "accepted"
                }
            val reply = simulatedResponse(bundle, kind) as AeatTransportResult.XmlResponse
            val parsed = AeatResponseParser.parseSubmission(reply.xml)
            sandboxRequire(parsed is AeatResponseParseResult.Parsed, "Offline rehearsal response cannot be parsed.")
            parsed as AeatResponseParseResult.Parsed
            sandboxRequire(
                AeatResponseCorrelation.correlate(bundle.plan.records, parsed.response) is AeatResponseCorrelationResult.Matched,
                "Offline rehearsal did not correlate every record.",
            )
            writePrivate(rehearsal.directory(case).resolve("synthetic-response.xml"), reply.xml)
            "${case.id}\t${bundle.plan.records.size}\tMATCHED\t${kind.uppercase(java.util.Locale.ROOT)}"
        }
    val report =
        "evidence.kind=OFFLINE_SIMULATION\nNetwork calls: 0; no credential access.\ncase\trecords\tcorrelation\tsynthetic_response\n" +
            lines.joinToString("\n") +
            "\nAll 10001 pagination records generated, schema-validated and correlated locally.\nThis establishes preparation only, not live AEAT outcomes."
    writePrivate(output.resolve("rehearsal.txt"), report + "\n")
    return CommandResult(report + "\nPrivate rehearsal: $output")
}
