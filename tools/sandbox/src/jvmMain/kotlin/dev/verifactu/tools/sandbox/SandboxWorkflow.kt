package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportAdapter
import java.io.IOException
import java.nio.file.Path

/** Result of a host-tool command; exit codes retain the standalone sandbox semantics. */
public data class SandboxCommandResult(
    public val output: String,
    public val exitCode: Int,
) {
    override fun toString(): String = "SandboxCommandResult(exitCode=$exitCode, output=redacted)"
}

/** Host-tool bridge for the unified CLI. This is not part of a published library artifact. */
public object SandboxWorkflow {
    /** Runs a standalone command without terminating the calling process. */
    public fun execute(args: List<String>): SandboxCommandResult = commandLineResult(args).publicResult()

    /** Revalidates an explicit TEST bundle and digest before invoking the selected transport once. */
    public fun send(
        bundle: Path,
        output: Path,
        confirmedSha256: String,
        transport: AeatTransportAdapter,
    ): SandboxCommandResult =
        try {
            val reviewed = loadBundle(bundle)
            sandboxRequire(
                reviewed.values["mode"] == "aeat-test",
                "Offline bundles cannot be sent. Prepare explicit aeat-test inputs separately.",
            )
            sandboxRequire(confirmedSha256 == reviewed.sha256, "Confirm the SHA-256 printed by show for this exact request.")
            createPrivateDirectory(output)
            deliverSandboxBundle(reviewed, output, transport).publicResult()
        } catch (error: SandboxInputException) {
            SandboxCommandResult(error.message.orEmpty(), 2)
        } catch (_: IOException) {
            SandboxCommandResult("Cannot read or create a local file. Preserve existing evidence; no automatic retry.", 2)
        } catch (_: SecurityException) {
            SandboxCommandResult("Local access denied. Preserve existing evidence; no automatic retry.", 2)
        }
}

private fun CommandResult.publicResult(): SandboxCommandResult = SandboxCommandResult(output, exitCode)
