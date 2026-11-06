package dev.verifactu.tools.sandbox

internal data class CommandResult(
    val output: String,
    val exitCode: Int = 0,
)
