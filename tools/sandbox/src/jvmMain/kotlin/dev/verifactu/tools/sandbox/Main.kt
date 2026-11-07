package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.testkit.FakeAeatTransport
import java.io.IOException
import java.nio.file.Path
import kotlin.system.exitProcess

/** Runs the standalone host utility; only the explicit send command can access credentials/network. */
public fun main(args: Array<String>) {
    val result = commandLineResult(args.toList())
    if (result.exitCode == 2) System.err.println(result.output) else println(result.output)
    if (result.exitCode != 0) exitProcess(result.exitCode)
}

internal fun commandLineResult(args: List<String>): CommandResult =
    try {
        runCommand(args)
    } catch (error: SandboxInputException) {
        CommandResult(error.message.orEmpty(), 2)
    } catch (_: java.nio.file.InvalidPathException) {
        CommandResult("Invalid local file path.", 2)
    } catch (_: IOException) {
        CommandResult("Cannot read or create a local file. Existing outputs are never overwritten.", 2)
    } catch (_: SecurityException) {
        CommandResult("Access to a selected local resource was denied.", 2)
    }

internal fun runCommand(
    args: List<String>,
    environment: (String) -> String? = System::getenv,
    transportFactory: (Path, String, CharArray) -> AeatTransportAdapter = ::loadSandboxTransport,
): CommandResult {
    if (args.isEmpty() || args == listOf("--help")) return CommandResult(USAGE)
    val options = parseOptions(args.drop(1))
    return when (args.first()) {
        "scenarios" -> {
            options.allow()
            CommandResult(
                "Preparation: registration, cancellation, duplicate\nOffline responses: ${OFFLINE_SCENARIOS.joinToString(
                    ", ",
                )}\nTEST only; no request sent.",
            )
        }
        "prepare" -> prepareCommand(options)
        "show" -> {
            options.allow("bundle")
            val bundle = loadBundle(options.path("bundle"))
            CommandResult(
                bundleSummary(bundle) + "\nXML preview (directional controls escaped; original bytes are in request.xml):\n" +
                    bundle.plan.soap
                        .map {
                            if (it in '\u202A'..'\u202E' ||
                                it in '\u2066'..'\u2069'
                            ) {
                                "\\u${it.code.toString(16).padStart(4, '0')}"
                            } else {
                                it.toString()
                            }
                        }.joinToString(""),
            )
        }
        "inspect" -> {
            options.allow("bundle", "input", "details")
            val bundle = loadBundle(options.path("bundle"))
            val result = inspectXml(bundle, readText(options.path("input")), "details" in options)
            result.copy(output = "Saved XML inspection only; not proof of network delivery.\n" + result.output)
        }
        "demo" -> demoCommand(options)
        "send" -> sendCommand(options, environment, transportFactory)
        else -> throw SandboxInputException(USAGE)
    }
}

private fun prepareCommand(options: Map<String, String>): CommandResult {
    val scenario = required(options, "scenario")
    if (scenario == "duplicate") {
        options.allow("scenario", "previous", "out")
    } else {
        options.allow("scenario", "config", "out", "previous", "mode", "certificate-route")
    }
    val bundle =
        prepareBundle(
            BundlePreparation(
                options["config"]?.let { Path.of(it) },
                options.path("out"),
                scenario,
                options["mode"] ?: "offline",
                options["certificate-route"] ?: "standard",
                options["previous"]?.let { Path.of(it) },
            ),
        )
    return CommandResult(bundleSummary(bundle) + "\nNo request was sent. Use show before a separately authorized send.")
}

private fun demoCommand(options: Map<String, String>): CommandResult {
    options.allow("bundle", "response")
    val bundle = loadBundle(options.path("bundle"))
    val scenarios = options["response"]?.let { listOf(it) } ?: OFFLINE_SCENARIOS
    val output =
        scenarios.joinToString("\n\n") { scenario ->
            val transport = FakeAeatTransport(listOf(simulatedResponse(bundle, scenario)))
            val result = transport.execute(AeatTransportRequest(endpoint(bundle.values), bundle.plan.soap))
            "OFFLINE SIMULATION: $scenario\n${summarizeTransport(bundle, result).output}"
        }
    return CommandResult(output + "\nNetwork calls: 0; no credential access. Demo completion is not fiscal acceptance.")
}

private fun sendCommand(
    options: Map<String, String>,
    environment: (String) -> String?,
    transportFactory: (Path, String, CharArray) -> AeatTransportAdapter,
): CommandResult {
    options.allow("bundle", "out", "pkcs12", "alias", "password-env", "confirm-sha256")
    val bundle = loadBundle(options.path("bundle"))
    sandboxRequire(bundle.values["mode"] == "aeat-test", "Offline bundles cannot be sent. Prepare explicit aeat-test inputs separately.")
    sandboxRequire(required(options, "confirm-sha256") == bundle.sha256, "Confirm the SHA-256 printed by show for this exact request.")
    val certificate = options.path("pkcs12")
    val alias = required(options, "alias")
    val passwordVariable = required(options, "password-env")
    sandboxRequire(passwordVariable.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")), "Select a valid password environment variable name.")
    val output = options.path("out")
    val password =
        environment(passwordVariable)?.toCharArray()
            ?: throw SandboxInputException("The selected password environment variable is unset.")
    val transport =
        try {
            createPrivateDirectory(output)
            transportFactory(certificate, alias, password)
        } finally {
            password.fill('\u0000')
        }
    return deliverSandboxBundle(bundle, output, transport)
}

internal fun deliverSandboxBundle(
    bundle: SandboxBundle,
    output: Path,
    transport: AeatTransportAdapter,
): CommandResult {
    val destination = endpoint(bundle.values)
    writePrivate(output.resolve("request.xml"), bundle.plan.soap)
    writePrivate(
        output.resolve("started.properties"),
        propertiesText(
            mapOf(
                "state" to "STARTED",
                "environment" to "TEST",
                "endpoint" to destination.url,
                "request.sha256" to bundle.sha256,
            ),
        ),
    )
    val result =
        try {
            transport.execute(AeatTransportRequest(destination, bundle.plan.soap))
        } catch (_: Exception) {
            AeatTransportResult.UnknownDelivery("The transport failed without a reliable delivery result.")
        }
    val summary = summarizeTransport(bundle, result)
    return try {
        if (result is AeatTransportResult.XmlResponse) writePrivate(output.resolve("response.xml"), result.xml)
        writePrivate(
            output.resolve("outcome.properties"),
            propertiesText(
                mapOf(
                    "delivery" to result.deliveryState.name,
                    "exit.code" to summary.exitCode.toString(),
                    "request.sha256" to bundle.sha256,
                    "transport.result" to result.javaClass.simpleName,
                    "http.status" to (transportHttpStatus(result)?.toString() ?: "unavailable"),
                ),
            ),
        )
        writePrivate(output.resolve("summary.txt"), summary.output + "\n")
        summary
    } catch (_: IOException) {
        CommandResult(
            "Cannot persist the complete result after starting delivery. Preserve started.properties and reconcile before another attempt.",
            4,
        )
    } catch (_: SecurityException) {
        CommandResult(
            "Cannot persist the result after starting delivery. Preserve started.properties and reconcile before another attempt.",
            4,
        )
    }
}

private fun parseOptions(args: List<String>): Map<String, String> {
    val options = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val token = args[index++]
        sandboxRequire(token.startsWith("--") && token.length > 2, "Expected a named command option.")
        val key = token.removePrefix("--")
        sandboxRequire(key !in options, "Repeated command option.")
        val value =
            if (key == "details") {
                "true"
            } else {
                val next = args.getOrNull(index++) ?: throw SandboxInputException("A command option needs a value.")
                sandboxRequire(!next.startsWith("--"), "A command option needs a value.")
                next
            }
        options[key] = value
    }
    return options
}

private fun Map<String, String>.allow(vararg names: String) {
    sandboxRequire(keys.all { it in names }, "Unsupported command option.")
}

private fun Map<String, String>.path(name: String): Path = Path.of(required(this, name))

private const val USAGE =
    "VeriFactu sandbox CLI (TEST only).\n" +
        "scenarios\n" +
        "prepare --scenario registration|cancellation --config inputs.properties --out new-directory " +
        "[--previous bundle] [--mode offline|aeat-test] [--certificate-route standard|seal]\n" +
        "prepare --scenario duplicate --previous registration-bundle --out new-directory\n" +
        "show --bundle directory\ninspect --bundle directory --input response.xml [--details]\n" +
        "demo --bundle directory [--response accepted|warning|rejected|duplicate|flow-control|soap-fault|timeout|not-sent]\n" +
        "send --bundle directory --out new-attempt-directory --pkcs12 selected.p12 --alias selected-alias " +
        "--password-env VARIABLE_NAME --confirm-sha256 REVIEWED_SHA256\n" +
        "Exit: 0 local command/clean matched response; 2 local input error; 3 response needs review; 4 delivery failure/uncertainty."
