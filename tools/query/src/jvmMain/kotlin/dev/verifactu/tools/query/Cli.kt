package dev.verifactu.tools.query

import java.io.IOException
import java.nio.file.Path
import java.time.YearMonth
import java.time.ZoneId
import kotlin.system.exitProcess
import dev.verifactu.tools.sandbox.main as sandboxMain

/** Runs the user-facing CLI. Only sandbox invoices list explicitly accesses AEAT TEST and Keychain. */
public fun main(args: Array<String>) {
    val result = cliResult(args.toList())
    if (result.output.isNotEmpty()) println(result.output)
    if (result.diagnostic.isNotEmpty()) System.err.println(result.diagnostic)
    if (result.exitCode != 0) exitProcess(result.exitCode)
}

internal data class CliResult(
    val output: String = "",
    val diagnostic: String = "",
    val exitCode: Int = 0,
)

internal data class CliSettings(
    val taxpayerFile: Path = Path.of(System.getProperty("user.home"), ".config", "verifactu", "taxpayer.properties"),
    val month: String = YearMonth.now(ZoneId.of("Europe/Madrid")).toString(),
    val prompt: (String) -> String? = { System.console()?.readLine("%s", it) },
)

internal fun cliResult(
    args: List<String>,
    settings: CliSettings = CliSettings(),
    fetch: (Map<String, String>) -> QueryRunResult = { fetchQueries(it) },
    sandbox: (List<String>) -> Unit = { sandboxMain(it.toTypedArray()) },
): CliResult =
    try {
        runCli(args, settings, fetch, sandbox)
    } catch (error: QueryInputException) {
        CliResult(diagnostic = "Error: ${error.message}\nRun 'verifactu --help' for available commands.", exitCode = 2)
    } catch (_: java.nio.file.InvalidPathException) {
        CliResult(diagnostic = "Error: invalid local path.", exitCode = 2)
    } catch (_: IOException) {
        CliResult(diagnostic = "Error: cannot read or create a local file. Existing files are never overwritten.", exitCode = 2)
    } catch (_: SecurityException) {
        CliResult(diagnostic = "Error: local resource access was denied.", exitCode = 2)
    }

private fun runCli(
    args: List<String>,
    settings: CliSettings,
    fetch: (Map<String, String>) -> QueryRunResult,
    sandbox: (List<String>) -> Unit,
): CliResult {
    val words = if (args.firstOrNull() == "help") args.drop(1) + "--help" else args
    val command = words.takeWhile { !it.startsWith("-") }
    val help = commandHelp(command)
    if (words.drop(command.size) in listOf(emptyList(), listOf("--help"), listOf("-h"))) {
        if (command !in LEAF_COMMANDS || words.size > command.size) return CliResult(help)
    }
    val options = cliOptions(words.drop(command.size))
    return when (command) {
        listOf("config", "init") -> initializeTaxpayer(options, settings)
        listOf("sandbox", "invoices", "list") -> listInvoices(options, settings, fetch)
        listOf("sandbox", "demo") -> runDemo(options, sandbox)
        else -> throw QueryInputException("Choose an action.\n$help")
    }
}

internal fun cliOptions(args: List<String>): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val token = args[index++]
        queryRequire(token.startsWith("--") && token.length > 2, "Expected a --named-option.")
        val key = token.substring(2).substringBefore('=')
        queryRequire(key !in result, "Repeated --$key option.")
        val value = if ('=' in token) token.substringAfter('=') else args.getOrNull(index++)
        queryRequire(value != null && value.isNotBlank() && !value.startsWith("--"), "--$key requires a value.")
        result[key] = value!!
    }
    return result
}
