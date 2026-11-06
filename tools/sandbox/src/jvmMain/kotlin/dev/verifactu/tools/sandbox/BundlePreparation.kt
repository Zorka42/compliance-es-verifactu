package dev.verifactu.tools.sandbox

import java.nio.file.Path

internal data class BundlePreparation(
    val config: Path?,
    val output: Path,
    val scenario: String,
    val mode: String,
    val route: String,
    val previousPath: Path?,
) {
    override fun toString(): String = "BundlePreparation(redacted)"
}
