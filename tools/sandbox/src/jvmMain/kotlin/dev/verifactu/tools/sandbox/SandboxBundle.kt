package dev.verifactu.tools.sandbox

import dev.verifactu.core.ChainState
import dev.verifactu.xml.SubmissionRecord
import java.nio.file.Path

internal data class SandboxBundle(
    val values: Map<String, String>,
    val plan: SandboxPlan,
    val sha256: String,
) {
    override fun toString(): String = "SandboxBundle(sha256=$sha256, data=redacted)"
}

internal fun loadBundle(directory: Path): SandboxBundle {
    val fields = readProperties(directory.resolve("bundle.properties"))
    sandboxRequire(fields["format"] == "1", "Unsupported sandbox bundle version.")
    val xml = readText(directory.resolve("request.xml"))
    val digest = requestDigest(xml)
    sandboxRequire(fields["request.sha256"] == digest, "The request differs from its recorded SHA-256. Prepare a new bundle.")
    val plan = buildPlan(fields)
    sandboxRequire(plan.soap == xml, "The request and bundle metadata do not describe the same prepared record.")
    sandboxRequire(plan.recordXml == readText(directory.resolve("record.xml")), "The saved record differs from its prepared snapshot.")
    return SandboxBundle(fields, plan, digest)
}

internal fun prepareBundle(request: BundlePreparation): SandboxBundle {
    val previous = request.previousPath?.let { loadBundle(it) }
    val fields = if (request.scenario == "duplicate") duplicateFields(request, previous) else freshFields(request, previous)
    val plan = buildPlan(fields)
    val digest = requestDigest(plan.soap)
    if (request.scenario == "duplicate") sandboxRequire(digest == previous!!.sha256, "Duplicate must retain the original request bytes.")
    fields["request.sha256"] = digest
    val output = request.output
    createPrivateDirectory(output)
    writePrivate(output.resolve("request.xml"), plan.soap)
    writePrivate(output.resolve("record.xml"), plan.recordXml)
    writePrivate(output.resolve("bundle.properties"), propertiesText(fields))
    writePrivate(
        output.resolve("validation.txt"),
        plan.report.issues.joinToString("\n") { "${it.severity} ${it.code} ${it.fieldPath}" } + "\n",
    )
    plan.qr?.let { writePrivate(output.resolve("qr.txt"), it + "\n") }
    return SandboxBundle(fields, plan, digest)
}

private fun duplicateFields(
    request: BundlePreparation,
    previous: SandboxBundle?,
): MutableMap<String, String> {
    sandboxRequire(request.config == null && previous != null, "Duplicate requires --previous and no new configuration.")
    sandboxRequire(
        previous!!.plan.record is SubmissionRecord.Registration && previous.plan.records.size == 1,
        "This duplicate scenario requires one registration record.",
    )
    return previous.values.toMutableMap().apply { put("scenario", "duplicate") }
}

private fun freshFields(
    request: BundlePreparation,
    previous: SandboxBundle?,
): MutableMap<String, String> {
    sandboxRequire(request.config != null, "Registration and cancellation require --config.")
    val fields = readProperties(request.config!!).toMutableMap()
    sandboxRequire(
        fields.keys.containsAll(INPUT_KEYS) &&
            fields.keys.all {
                it in INPUT_KEYS + OPTIONAL_INPUT_KEYS
            },
        "Configuration must contain the documented fiscal input keys only.",
    )
    fields.putAll(mapOf("format" to "1", "scenario" to request.scenario, "mode" to request.mode, "certificate.route" to request.route))
    if (previous != null) appendPrevious(fields, previous)
    return fields
}

private fun appendPrevious(
    fields: MutableMap<String, String>,
    previous: SandboxBundle,
) {
    sandboxRequire(
        previous.values["mode"] == fields["mode"] && previous.values["certificate.route"] == fields["certificate.route"],
        "Previous bundle mode and certificate route must match.",
    )
    val matchingKeys = INPUT_KEYS - setOf("invoice.number", "invoice.date", "generated.at")
    sandboxRequire(
        matchingKeys.all { fields[it] == previous.values[it] },
        "The prior bundle belongs to different issuer or system metadata.",
    )
    if (fields["scenario"] in setOf("cancellation", "subsanation")) {
        sandboxRequire(
            previous.plan.record is SubmissionRecord.Registration &&
                previous.plan.records.size == 1 &&
                invoice(fields) == invoice(previous.values),
            "Cancellation/subsanation must follow the matching single registration bundle.",
        )
    }
    fields.putAll(chainFields(previous.plan.next))
}

private fun chainFields(previous: ChainState.PreviousRecord): Map<String, String> =
    mapOf(
        "previous.issuer" to previous.invoice.issuer.value,
        "previous.number" to previous.invoice.number.value,
        "previous.date" to previous.invoice.issueDate.value,
        "previous.hash" to previous.hash,
    )

internal fun bundleSummary(bundle: SandboxBundle): String =
    "Environment: TEST\nMode: ${bundle.values.getValue("mode")}\nScenario: ${bundle.values.getValue("scenario")}\n" +
        "Endpoint: ${endpoint(bundle.values).url}\nRequest SHA-256: ${bundle.sha256}\n" +
        "Records: ${bundle.plan.records.size}\nLocal validation issues: ${bundle.plan.report.issues.size}\n" +
        "Expected TEST observation: ${scenarioExpectation(bundle.values.getValue("scenario"))}\n" +
        "Preparation is local validation only; it does not establish AEAT acceptance."

internal fun scenarioExpectation(scenario: String): String =
    when (scenario) {
        "warning-first" -> "accepted with errors, code 2007 (intentional first-record misuse; prior acceptance required)"
        "rejection-date" -> "rejected, code 1152 (intentional invoice date before 28-10-2024)"
        "duplicate" -> "duplicate, code 3000 (prior acceptance required)"
        else -> "clean matched acceptance; actual AEAT result must be reviewed"
    }
