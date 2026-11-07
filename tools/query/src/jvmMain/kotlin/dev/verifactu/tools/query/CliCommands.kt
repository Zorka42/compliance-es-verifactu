package dev.verifactu.tools.query

import java.io.IOException
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

internal fun initializeTaxpayer(
    options: Map<String, String>,
    settings: CliSettings,
): CliResult {
    options.allow("party-file")
    queryRequire(!Files.exists(settings.taxpayerFile), "Taxpayer configuration already exists; it was not replaced.")
    val party =
        if ("party-file" in options) {
            queryProperties(Path.of(options.getValue("party-file")))
        } else {
            mapOf(
                "nif" to promptRequired(settings, "Taxpayer NIF: "),
                "name" to promptRequired(settings, "Taxpayer legal name: "),
            )
        }
    validateParty(party)
    Files.createDirectories(
        settings.taxpayerFile.parent,
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")),
    )
    val content = StringWriter()
    Properties().apply { putAll(party) }.store(content, "Taxpayer identity only; no credentials")
    privateFile(settings.taxpayerFile, content.toString())
    return CliResult("Taxpayer configured locally. No Keychain or network access.\nNext: verifactu sandbox invoices list")
}

private fun promptRequired(
    settings: CliSettings,
    label: String,
): String = settings.prompt(label) ?: throw QueryInputException("No terminal input. Use config init --party-file FILE.")

private fun validateParty(party: Map<String, String>) {
    queryRequire(party.keys == setOf("nif", "name"), "Taxpayer file requires exactly nif and name.")
    InvoiceQuery().prepare(QueryDirection.ISSUED, QueryParty(party.getValue("nif"), party.getValue("name")), QueryPeriod("2026", "01"))
}

internal fun listInvoices(
    options: Map<String, String>,
    settings: CliSettings,
    fetch: (Map<String, String>) -> QueryRunResult,
): CliResult {
    options.allow("month", "direction", "party-file", "out", "format", "issuer")
    validateIssuer(options["issuer"])
    val month = options["month"] ?: settings.month
    queryRequire(month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")), "--month must be YYYY-MM, for example 2026-11.")
    val direction = options["direction"] ?: "both"
    queryRequire(direction in listOf("issued", "received", "both"), "--direction must be issued, received, or both.")
    val format = options["format"] ?: "table"
    queryRequire(format in listOf("table", "tsv"), "--format must be table or tsv.")
    val party = if ("party-file" in options) Path.of(options.getValue("party-file")) else settings.taxpayerFile
    queryRequire(Files.isRegularFile(party), "Configure your taxpayer first: verifactu config init (or use --party-file FILE).")
    validateParty(queryProperties(party))
    val output =
        if ("out" in
            options
        ) {
            Path.of(options.getValue("out"))
        } else {
            Files.createTempDirectory("verifactu-test-").resolve("consultation")
        }
    val result =
        fetch(
            mapOf(
                "keychain" to "true",
                "direction" to direction,
                "party-file" to party.toString(),
                "year" to month.substring(0, 4),
                "month" to month.substring(5),
                "out" to output.toString(),
            ) + options.filterKeys { it == "issuer" },
        )
    return renderConsultation(result, output, format)
}

private fun renderConsultation(
    result: QueryRunResult,
    output: Path,
    format: String,
): CliResult =
    try {
        val rowsFile = output.resolve("invoices.tsv")
        val rows = if (Files.isRegularFile(rowsFile)) readPrivateInput(rowsFile, MAX_XML_BYTES).removeSuffix("\n") else ""
        val rendered = if (format == "tsv") rows else invoiceTable(rows)
        CliResult(rendered, result.output + "\nEvidence directory: $output", result.exitCode)
    } catch (_: IOException) {
        unreadableConsultation(output)
    } catch (_: SecurityException) {
        unreadableConsultation(output)
    } catch (_: QueryInputException) {
        unreadableConsultation(output)
    }

private fun unreadableConsultation(output: Path): CliResult =
    CliResult(
        diagnostic = "Cannot display the saved consultation. Preserve evidence in $output; no automatic retry.",
        exitCode = 4,
    )

internal fun invoiceTable(tsv: String): String {
    if (tsv.isEmpty()) return ""
    val lines = tsv.lines().map { it.split('\t') }
    queryRequire(lines.all { it.size == 6 }, "Saved invoice list is inconsistent; inspect the consultation evidence.")
    val widths = (0..5).map { column -> lines.maxOf { it[column].length } }
    return lines.joinToString("\n") { fields ->
        fields
            .mapIndexed {
                index,
                value,
                ->
                value.padEnd(widths[index])
            }.joinToString("  ")
            .trimEnd()
    }
}

internal fun runDemo(
    options: Map<String, String>,
    sandbox: (List<String>) -> Unit,
): CliResult {
    options.allow("response")
    val response = options["response"]
    queryRequire(response == null || response in DEMO_RESPONSES, "Unknown --response. Choose one shown by 'verifactu sandbox demo --help'.")
    val root = Files.createTempDirectory("verifactu-demo-")
    try {
        val config = root.resolve("synthetic.properties")
        val text =
            InvoiceQuery::class.java.getResourceAsStream("/offline.properties")?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw QueryInputException("Synthetic demo inputs are missing; rebuild the CLI distribution.")
        privateFile(config, text)
        val bundle = root.resolve("registration").toString()
        sandbox(listOf("prepare", "--scenario", "registration", "--config", config.toString(), "--out", bundle))
        sandbox(listOf("demo", "--bundle", bundle) + (if (response != null) listOf("--response", response) else emptyList()))
    } finally {
        root.toFile().deleteRecursively()
    }
    return CliResult()
}
