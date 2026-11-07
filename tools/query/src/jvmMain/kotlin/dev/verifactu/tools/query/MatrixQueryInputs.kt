package dev.verifactu.tools.query

import dev.verifactu.tools.sandbox.SandboxWorkflow
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

internal data class MatrixQueryInputs(
    val root: Path,
    val party: QueryParty,
    val period: QueryPeriod,
    val prefix: String,
    val digest: String,
)

internal fun matrixQueryInputs(root: Path): MatrixQueryInputs {
    val base = readPrivateInput(root.resolve("base.properties"), 16_384)
    val metadata = queryProperties(root.resolve("matrix.properties"))
    val party = queryProperties(root.resolve("party.properties"))
    val period = metadata["period"].orEmpty()
    queryRequire(metadata["environment"] == "TEST" && period.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")), "Invalid TEST matrix period.")
    val prefix = metadata["invoice.prefix"].orEmpty()
    queryRequire(prefix.matches(Regex("VF111-[a-zA-Z0-9]{8,16}")), "Invalid TEST matrix prefix.")
    return MatrixQueryInputs(
        root,
        QueryParty(party.getValue("nif"), party.getValue("name")),
        QueryPeriod(period.take(4), period.takeLast(2)),
        prefix,
        queryDigest(base),
    )
}

internal fun matrixQueries(
    root: Path,
    scope: String,
): List<Path> =
    Files.list(root.resolve("queries")).use { paths ->
        paths.filter { it.fileName.toString().matches(Regex("$scope-[0-9]{3}")) }.sorted().collect(Collectors.toList())
    }

internal fun queryMatrix(
    options: Map<String, String>,
    fetch: (Map<String, String>) -> QueryRunResult,
): CliResult {
    options.allow("run", "scope", "issuer")
    validateIssuer(options["issuer"])
    val scope = options.required("scope")
    queryRequire(scope in listOf("small", "pagination"), "--scope must be small or pagination.")
    val root = Path.of(options.required("run")).toAbsolutePath().normalize()
    val write = SandboxWorkflow.execute(listOf("matrix", "report", "--run", root.toString()))
    queryRequire(write.exitCode != 2, "Invalid matrix session.")
    val last = if (scope == "small") "rejection" else "pagination-11"
    queryRequire(
        "case.$last.state=PASS" in write.output.lines(),
        "Finish and verify this scope's write cases before querying its expected records.",
    )
    val inputs = matrixQueryInputs(root)
    val count = matrixQueries(root, scope).size
    queryRequire(count < 99, "Query evidence limit reached; preserve and review the session.")
    val output = root.resolve("queries/$scope-${(count + 1).toString().padStart(3, '0')}")
    val started = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    val result =
        fetch(
            mapOf(
                "keychain" to "true",
                "direction" to "both",
                "party-file" to root.resolve("party.properties").toString(),
                "year" to inputs.period.year,
                "month" to inputs.period.month,
                "out" to output.toString(),
            ) + options.filterKeys { it == "issuer" },
        )
    if (Files.isDirectory(output)) {
        privateFile(
            output.resolve("matrix-query.properties"),
            "evidence.kind=AEAT_TEST\nscope=$scope\nbase.sha256=${inputs.digest}\nstarted.at=$started\n",
        )
    }
    val check = verifyMatrixQueries(root)
    return CliResult(
        result.output + "\n" + check.output,
        exitCode =
            if (result.exitCode ==
                0
            ) {
                if ("query.$scope.state=PASS" in check.output.lines()) 0 else 3
            } else {
                result.exitCode
            },
    )
}
