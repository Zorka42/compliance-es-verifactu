package dev.verifactu.tools.sandbox

import java.nio.file.Files
import java.nio.file.Path

internal val TEST_INPUTS: Map<String, String> =
    mapOf(
        "issuer.nif" to "89890001K",
        "issuer.name" to "Synthetic sandbox issuer",
        "invoice.number" to "SANDBOX-001",
        "invoice.date" to "01-01-2024",
        "generated.at" to "2024-01-01T12:00:00+01:00",
        "producer.nif" to "89890001K",
        "producer.name" to "Synthetic sandbox producer",
        "system.name" to "Local sandbox test harness",
        "system.id" to "VF",
        "system.version" to "0.1-test",
        "system.installation" to "local-test-1",
    )

internal fun configFile(
    root: Path,
    values: Map<String, String> = TEST_INPUTS,
    name: String = "inputs.properties",
): Path = root.resolve(name).also { Files.writeString(it, propertiesText(values)) }

internal fun fixtureBundle(
    root: Path,
    mode: String = "offline",
    name: String = "bundle",
): SandboxBundle =
    prepareBundle(
        BundlePreparation(configFile(root, name = "$name-inputs.properties"), root.resolve(name), "registration", mode, "standard", null),
    )

internal inline fun withWorkspace(block: (Path) -> Unit) {
    val root = Files.createTempDirectory("verifactu-sandbox-test-")
    try {
        block(root)
    } finally {
        root.toFile().deleteRecursively()
    }
}

internal fun sendArguments(
    root: Path,
    digest: String,
    output: String = "attempt",
): List<String> =
    listOf(
        "send",
        "--bundle",
        root.resolve("bundle").toString(),
        "--out",
        root.resolve(output).toString(),
        "--pkcs12",
        root.resolve("selected.p12").toString(),
        "--alias",
        "selected-client",
        "--password-env",
        "TEST_PASSWORD",
        "--confirm-sha256",
        digest,
    )
