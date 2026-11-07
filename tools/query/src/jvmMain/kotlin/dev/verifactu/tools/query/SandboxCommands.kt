package dev.verifactu.tools.query

import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.tools.sandbox.SandboxCommandResult
import dev.verifactu.tools.sandbox.SandboxWorkflow
import java.net.URI
import java.nio.file.Path

internal fun sandboxLocal(
    command: String,
    options: Map<String, String>,
    workflow: (List<String>) -> SandboxCommandResult,
): CliResult {
    when (command) {
        "scenarios" -> options.allow()
        "prepare" -> options.allow("scenario", "config", "out", "previous", "mode", "certificate-route")
        "show" -> options.allow("bundle")
        "inspect" -> options.allow("bundle", "input", "details")
        "demo" -> options.allow("bundle", "response")
        else -> throw QueryInputException("Unknown sandbox action.")
    }
    queryRequire(options["details"] in listOf(null, "true"), "--details is a flag, without a value.")
    val args = listOf(command) + options.flatMap { (key, value) -> if (key == "details") listOf("--details") else listOf("--$key", value) }
    val result = workflow(args)
    return if (result.exitCode == 0) CliResult(result.output) else CliResult(diagnostic = result.output, exitCode = result.exitCode)
}

internal fun sendSandbox(
    options: Map<String, String>,
    helperProvider: () -> Path = ::installedHelper,
    transport: (Path, NativeQueryCall) -> NativeQueryReceipt = ::executeNativeQuery,
): CliResult {
    options.allow("bundle", "out", "confirm-sha256", "issuer", "confirm-records")
    validateIssuer(options["issuer"])
    val bundle = Path.of(options.required("bundle"))
    val output = Path.of(options.required("out"))
    val confirmation = options.required("confirm-sha256")
    val records = options["confirm-records"]?.toIntOrNull() ?: if ("confirm-records" !in options) 1 else 0
    queryRequire(records in 1..1000, "--confirm-records must be 1..1000.")
    queryRequire(confirmation.matches(Regex("[a-f0-9]{64}")), "--confirm-sha256 requires the exact digest printed by sandbox show.")
    val result =
        SandboxWorkflow.send(
            bundle,
            output,
            confirmation,
            object : AeatTransportAdapter {
                override fun execute(request: AeatTransportRequest): AeatTransportResult =
                    nativeSubmission(request, output, NativeSubmissionSelection(options["issuer"], records), helperProvider, transport)
            },
            records,
        )
    return CliResult(diagnostic = result.output + "\nEvidence directory: $output", exitCode = result.exitCode)
}

private fun nativeSubmission(
    request: AeatTransportRequest,
    output: Path,
    selection: NativeSubmissionSelection,
    helperProvider: () -> Path,
    transport: (Path, NativeQueryCall) -> NativeQueryReceipt,
): AeatTransportResult {
    val native = output.resolve("native")
    privateDirectory(native)
    val call =
        NativeQueryCall(
            output.resolve("request.xml"),
            native,
            queryDigest(request.xmlPayload),
            request.endpoint.url,
            null,
            selection.issuer,
            queryDigest(request.xmlPayload),
            selection.records,
        )
    val helper =
        try {
            helperProvider()
        } catch (_: Exception) {
            null
        }
    val receipt = if (helper == null) NativeQueryReceipt("HELPER_NOT_STARTED", "NOT_SENT", null, null) else transport(helper, call)
    privateFile(output.resolve("network.log"), submissionNetworkEvidence(call, receipt))
    return when (receipt.kind) {
        "XML_RESPONSE" ->
            if (receipt.identity?.matches(Regex("[a-f0-9]{64}")) == true) {
                AeatTransportResult.XmlResponse(
                    receipt.httpStatus!!,
                    "text/xml",
                    readPrivateInput(native.resolve("response.xml"), MAX_XML_BYTES),
                )
            } else {
                AeatTransportResult.UnknownDelivery("Selected identity could not be correlated.")
            }
        "NON_XML_RESPONSE" -> AeatTransportResult.NonXmlResponse(receipt.httpStatus!!, null)
        "RESPONSE_TOO_LARGE" -> AeatTransportResult.ResponseTooLarge(receipt.httpStatus!!, null, MAX_XML_BYTES)
        else ->
            if (receipt.delivery == "NOT_SENT") {
                AeatTransportResult.NotSent("Native selection or setup did not send the request.")
            } else {
                AeatTransportResult.UnknownDelivery("Native delivery is uncertain; reconcile before another attempt.")
            }
    }
}

private fun submissionNetworkEvidence(
    call: NativeQueryCall,
    receipt: NativeQueryReceipt,
): String =
    "environment=TEST\noperation=RegFactuSistemaFacturacion\nhttp.method=POST\n" +
        "request.url=${call.endpoint}\nrequest.domain=${URI(call.endpoint).host}\n" +
        "redirect.policy=REFUSE\ntls.server.trust.policy=SYSTEM_DEFAULT\n" +
        "response.url=${receipt.responseUrl?.takeIf { it == call.endpoint } ?: "unavailable"}\n" +
        "http.status=${receipt.httpStatus ?: "unavailable"}\nkind=${receipt.kind}\ndelivery=${receipt.delivery}\n"

private data class NativeSubmissionSelection(
    val issuer: String?,
    val records: Int,
)

internal fun matrixSend(
    options: Map<String, String>,
    helperProvider: () -> Path = ::installedHelper,
    transport: (Path, NativeQueryCall) -> NativeQueryReceipt = ::executeNativeQuery,
): CliResult {
    options.allow("run", "case", "confirm-sha256", "confirm-records", "issuer")
    validateIssuer(options["issuer"])
    val count = options.required("confirm-records").toIntOrNull()
    queryRequire(count != null && count in 1..1000, "--confirm-records must be 1..1000.")
    return matrixResult(
        SandboxWorkflow.sendMatrix(
            Path.of(options.required("run")),
            options.required("case"),
            options.required("confirm-sha256"),
            count!!,
        ) { output, records ->
            object : AeatTransportAdapter {
                override fun execute(request: AeatTransportRequest): AeatTransportResult =
                    nativeSubmission(
                        request,
                        output,
                        NativeSubmissionSelection(options["issuer"], records),
                        helperProvider,
                        transport,
                    )
            }
        },
    )
}
