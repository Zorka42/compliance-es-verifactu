package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatFlowControl
import dev.verifactu.aeat.AeatRecordStatus
import dev.verifactu.aeat.AeatResponseCorrelation
import dev.verifactu.aeat.AeatResponseCorrelationResult
import dev.verifactu.aeat.AeatResponseParseResult
import dev.verifactu.aeat.AeatResponseParser
import dev.verifactu.aeat.AeatSubmissionStatus
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.aeat.SoapFaultParseResult
import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario

internal fun inspectXml(
    bundle: SandboxBundle,
    xml: String,
    details: Boolean,
    httpStatus: Int? = null,
): CommandResult {
    val fault = AeatResponseParser.parseSoapFault(xml)
    if (fault is SoapFaultParseResult.Parsed) {
        return CommandResult(
            "Result: SOAP_FAULT\n" +
                if (details) {
                    "Code: ${terminalText(fault.fault.code.orEmpty())}\nMessage: ${terminalText(fault.fault.message.orEmpty())}"
                } else {
                    "Remote diagnostics omitted; use inspect --details to view them."
                },
            3,
        )
    }
    val parsed = AeatResponseParser.parseSubmission(xml)
    if (parsed !is AeatResponseParseResult.Parsed) return CommandResult("Result: INVALID_XML\nNo acceptance established.", 3)
    val response = parsed.response
    val correlation = AeatResponseCorrelation.correlate(bundle.plan.records, response)
    val matched = correlation is AeatResponseCorrelationResult.Matched
    val result = submissionOutcome(response, matched, httpStatus)
    val states = response.lines.groupingBy { it.status }.eachCount()
    val codes =
        response.lines
            .mapNotNull { it.errorCode }
            .distinct()
            .take(20)
            .map { if (it.matches(Regex("[0-9]{4}"))) it else "UNKNOWN" }
    val wait =
        when (val flow = response.flowControl) {
            is AeatFlowControl.Known -> flow.seconds.toString()
            is AeatFlowControl.Unknown -> "unknown"
        }
    val issues =
        if (correlation is AeatResponseCorrelationResult.Mismatch) {
            correlation.issues.joinToString(
                ",",
            ) { it.code.name }
        } else {
            "none"
        }
    val summary =
        "Result: $result\nParsed aggregate: ${response.status}\nCorrelation: ${if (matched) "MATCHED" else "MISMATCH"}\n" +
            "Record states: $states\nAEAT codes: $codes\nSuggested wait seconds: $wait (no automatic retry or sleeping)\n" +
            "Correlation issues: $issues\n"
    val expanded =
        if (details) {
            response.lines
                .mapIndexed(
                    ::lineDetails,
                ).joinToString("\n")
        } else {
            "Invoice identifiers and remote diagnostics omitted."
        }
    return CommandResult(summary + expanded, if (result == "ACCEPTED") 0 else 3)
}

private fun submissionOutcome(
    response: dev.verifactu.aeat.AeatSubmissionResponse,
    matched: Boolean,
    httpStatus: Int?,
): String =
    when {
        !matched -> "CORRELATION_MISMATCH"
        httpStatus != null && httpStatus !in 200..299 -> "NON_SUCCESS_HTTP"
        response.lines.any { it.status == AeatRecordStatus.DUPLICATE } -> "DUPLICATE"
        response.status == AeatSubmissionStatus.REJECTED -> "REJECTED"
        response.status == AeatSubmissionStatus.UNKNOWN_STATE ||
            response.lines.any {
                it.status == AeatRecordStatus.UNKNOWN_STATE
            } -> "UNKNOWN_STATE"
        response.lines.any { !it.errorCode.isNullOrBlank() || !it.errorDescription.isNullOrBlank() } -> "REVIEW_REQUIRED"
        response.status == AeatSubmissionStatus.ACCEPTED && response.lines.all { it.status == AeatRecordStatus.ACCEPTED } -> "ACCEPTED"
        else -> "REVIEW_REQUIRED"
    }

private fun lineDetails(
    index: Int,
    line: dev.verifactu.aeat.AeatResponseLine,
): String {
    val fields =
        listOf(
            "issuer" to line.invoice?.issuer.orEmpty(),
            "invoice" to line.invoice?.number.orEmpty(),
            "date" to line.invoice?.issueDate.orEmpty(),
            "error" to line.errorCode.orEmpty(),
            "description" to line.errorDescription.orEmpty(),
            "duplicate" to line.duplicateRequestId.orEmpty(),
        )
    return "Line ${index + 1}: " + fields.joinToString("; ") { (key, text) -> "$key=${terminalText(text)}" }
}

internal fun summarizeTransport(
    bundle: SandboxBundle,
    result: AeatTransportResult,
): CommandResult {
    val prefix = "Environment: TEST\nDelivery evidence: ${result.deliveryState}\n"
    if (result is AeatTransportResult.XmlResponse) {
        val parsed = inspectXml(bundle, result.xml, false, result.statusCode)
        return parsed.copy(output = prefix + "HTTP status: ${result.statusCode}\n" + parsed.output)
    }
    val kind =
        when (result) {
            is AeatTransportResult.InvalidEndpoint, is AeatTransportResult.NotSent -> "NOT_SENT"
            is AeatTransportResult.Timeout -> "TIMEOUT"
            is AeatTransportResult.NetworkFailure, is AeatTransportResult.UnknownDelivery -> "UNKNOWN_DELIVERY"
            is AeatTransportResult.NonXmlResponse -> "NON_XML_RESPONSE"
            is AeatTransportResult.ResponseTooLarge -> "RESPONSE_TOO_LARGE"
            is AeatTransportResult.XmlResponse -> error("XML handled above")
        }
    val status = transportHttpStatus(result)?.let { "HTTP status: $it\n" }.orEmpty()
    return CommandResult(
        prefix + status + "Result: $kind\nNo acceptance established. Reconcile uncertain delivery before another attempt.",
        4,
    )
}

internal fun transportHttpStatus(result: AeatTransportResult): Int? =
    when (result) {
        is AeatTransportResult.XmlResponse -> result.statusCode
        is AeatTransportResult.NonXmlResponse -> result.statusCode
        is AeatTransportResult.ResponseTooLarge -> result.statusCode
        else -> null
    }

internal val OFFLINE_SCENARIOS: List<String> =
    listOf(
        "accepted",
        "warning",
        "rejected",
        "duplicate",
        "flow-control",
        "soap-fault",
        "timeout",
        "not-sent",
    )

internal fun simulatedResponse(
    bundle: SandboxBundle,
    scenario: String,
): AeatTransportResult {
    val fixture =
        when (scenario) {
            "accepted" -> AeatResponseScenario.ACCEPTED
            "warning" -> AeatResponseScenario.ACCEPTED_WITH_ERRORS
            "rejected" -> AeatResponseScenario.REJECTED
            "duplicate" -> AeatResponseScenario.DUPLICATE
            "flow-control" -> AeatResponseScenario.FLOW_CONTROL
            "soap-fault" -> return AeatResponseFixtures.soapFault()
            "timeout" -> return AeatTransportResult.Timeout("Synthetic timeout")
            "not-sent" -> return AeatTransportResult.NotSent("Synthetic preflight failure")
            else -> throw SandboxInputException("Unsupported offline response scenario.")
        }
    val responses = bundle.plan.records.map { syntheticRecordResponse(it, bundle.values["scenario"], fixture) }
    val lines =
        responses.joinToString("") {
            "<RespuestaLinea>" + it.substringAfter("<RespuestaLinea>").substringBefore("</RespuestaLinea>") +
                "</RespuestaLinea>"
        }
    return AeatTransportResult.XmlResponse(
        200,
        "text/xml",
        responses.first().substringBefore("<RespuestaLinea>") + lines + "</RespuestaRegFactuSistemaFacturacion>",
    )
}
