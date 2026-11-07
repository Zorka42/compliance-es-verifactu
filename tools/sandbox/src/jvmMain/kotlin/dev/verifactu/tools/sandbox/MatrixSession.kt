package dev.verifactu.tools.sandbox

import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class MatrixCase(
    val id: String,
    val scenario: String,
    val dependency: String?,
    val invoiceSuffix: String,
    val records: Int = 1,
    val first: Int = 1,
)

internal val MATRIX_CASES: List<MatrixCase> =
    listOf(
        MatrixCase("registration", "registration", null, "R"),
        MatrixCase("duplicate", "duplicate", "registration", "R"),
        MatrixCase("warning", "warning-first", "registration", "W"),
        MatrixCase("subsanation", "subsanation", "warning", "W"),
        MatrixCase("cancellation", "cancellation", "subsanation", "W"),
        MatrixCase("rejection", "rejection-date", "cancellation", "X"),
    ) +
        (1..11).map { index ->
            MatrixCase(
                "pagination-${index.toString().padStart(2, '0')}",
                "pagination",
                if (index == 1) null else "pagination-${(index - 1).toString().padStart(2, '0')}",
                "P",
                if (index == 11) 1 else 1000,
                (index - 1) * 1000 + 1,
            )
        }

internal data class MatrixSession(
    val root: Path,
    val inputs: Map<String, String>,
    val properties: Map<String, String>,
) {
    override fun toString(): String = "MatrixSession(redacted)"

    fun case(id: String): MatrixCase = MATRIX_CASES.singleOrNull { it.id == id } ?: throw SandboxInputException("Unknown matrix case.")

    fun directory(case: MatrixCase): Path = root.resolve("cases/${case.id}")

    fun bundle(case: MatrixCase): Path = directory(case).resolve("bundle")
}

internal fun loadMatrix(root: Path): MatrixSession {
    val directory = root.toAbsolutePath().normalize()
    val properties = readProperties(directory.resolve("matrix.properties"))
    sandboxRequire(properties.keys == setOf("format", "environment", "period", "created.at", "invoice.prefix"), "Invalid matrix metadata.")
    sandboxRequire(properties["format"] == "1" && properties["environment"] == "TEST", "Only TEST matrix format 1 is supported.")
    val inputs = readProperties(directory.resolve("base.properties"))
    sandboxRequire(
        inputs.keys.containsAll(INPUT_KEYS) &&
            inputs.keys.all {
                it in INPUT_KEYS + setOf("recipient.nif", "recipient.name")
            },
        "Invalid matrix fiscal inputs.",
    )
    sandboxRequire(
        inputs["issuer.nif"] == inputs["recipient.nif"] && inputs["issuer.name"] == inputs["recipient.name"],
        "The matrix uses an explicit self-recipient TEST dataset.",
    )
    value(
        dev.verifactu.core.InvoiceIssueDate
            .parse(required(inputs, "invoice.date")),
    )
    val party = readProperties(directory.resolve("party.properties"))
    sandboxRequire(
        party == mapOf("nif" to inputs.getValue("issuer.nif"), "name" to inputs.getValue("issuer.name")),
        "Matrix taxpayer differs from fiscal inputs.",
    )
    sandboxRequire(properties["invoice.prefix"]?.matches(Regex("VF111-[a-zA-Z0-9]{8,16}")) == true, "Invalid matrix invoice prefix.")
    sandboxRequire(
        properties["period"] == inputs.getValue("invoice.date").takeLast(4) + "-" + inputs.getValue("invoice.date").substring(3, 5),
        "Matrix period differs from invoice date.",
    )
    return MatrixSession(directory, inputs, properties)
}

internal fun initializeMatrix(
    options: Map<String, String>,
    now: OffsetDateTime,
    sessionId: String = UUID.randomUUID().toString(),
): CommandResult {
    options.allow("party-file", "producer-file", "out")
    val party = readProperties(options.path("party-file"))
    val producer = readProperties(options.path("producer-file"))
    sandboxRequire(
        party.keys == setOf("nif", "name") && producer.keys == setOf("nif", "name"),
        "Party/producer files require exactly nif and name.",
    )
    sandboxRequire(sessionId.matches(Regex("[a-zA-Z0-9-]{8,50}")), "Invalid test session identifier.")
    val prefix = "VF111-${sessionId.replace("-", "").take(16)}"
    val inputs =
        mapOf(
            "issuer.nif" to required(party, "nif"),
            "issuer.name" to required(party, "name"),
            "producer.nif" to required(producer, "nif"),
            "producer.name" to required(producer, "name"),
            "recipient.nif" to required(party, "nif"),
            "recipient.name" to required(party, "name"),
            "invoice.number" to "$prefix-R",
            "invoice.date" to now.format(DateTimeFormatter.ofPattern("dd-MM-uuuu", java.util.Locale.ROOT)),
            "generated.at" to now.withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "system.name" to "Verifactu TEST harness",
            "system.id" to "VT",
            "system.version" to "0.1-test",
            "system.installation" to "ZA111-$sessionId",
        )
    // Validate all supplied identities/metadata before creating private session files.
    buildPlan(inputs + mapOf("scenario" to "registration", "mode" to "aeat-test", "certificate.route" to "standard"))
    val root = options.path("out").toAbsolutePath().normalize()
    createPrivateDirectory(root)
    writePrivate(root.resolve("base.properties"), propertiesText(inputs))
    writePrivate(root.resolve("party.properties"), propertiesText(party))
    writePrivate(
        root.resolve("matrix.properties"),
        propertiesText(
            mapOf(
                "format" to "1",
                "environment" to "TEST",
                "period" to now.toString().take(7),
                "created.at" to now.toString(),
                "invoice.prefix" to prefix,
            ),
        ),
    )
    createPrivateDirectory(root.resolve("cases"))
    createPrivateDirectory(root.resolve("queries"))
    writePrivate(
        root.resolve("plan.tsv"),
        "case\tscenario\trecords\tdependency\n" +
            MATRIX_CASES.joinToString("\n", postfix = "\n") { "${it.id}\t${it.scenario}\t${it.records}\t${it.dependency ?: "none"}" },
    )
    return CommandResult(
        "TEST matrix initialized locally. No Keychain or network access.\nPrivate session: $root\n" +
            "Small suite: 6 explicitly reviewed sends. Pagination: 10001 records in 11 separately confirmed batches.\n" +
            "Next (local only): ${matrixCommand(root, "prepare", "--case", "registration")}",
    )
}

internal fun matrixCommand(
    root: Path,
    action: String,
    vararg flags: String,
): String =
    (listOf("./verifactu", "sandbox", "matrix", action, "--run", root.toString()) + flags).joinToString(" ", transform = ::shellArgument)

internal fun shellArgument(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
