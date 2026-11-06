package dev.verifactu.tools.sandbox

internal class SandboxInputException(
    message: String,
) : IllegalArgumentException(message)

internal fun sandboxRequire(
    condition: Boolean,
    message: String,
) {
    if (!condition) throw SandboxInputException(message)
}
