package dev.verifactu.tools.query

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal data class NativeQueryCall(
    val request: Path,
    val directory: Path,
    val sha256: String,
    val endpoint: String,
    val identity: String?,
    val issuer: String? = null,
    val submissionSha256: String? = null,
    val submissionRecordCount: Int = 1,
) {
    override fun toString(): String = "NativeQueryCall(sha256=$sha256, details=redacted)"
}

internal data class NativeQueryReceipt(
    val kind: String,
    val delivery: String,
    val httpStatus: Int?,
    val identity: String?,
    val responseUrl: String? = null,
) {
    override fun toString(): String = "NativeQueryReceipt(kind=$kind, delivery=$delivery, details=redacted)"
}

internal fun installedHelper(): Path =
    locateNativeHelper(
        System.getProperty("os.name"),
        Path.of(
            InvoiceQuery::class.java.protectionDomain.codeSource.location
                .toURI(),
        ),
    )

internal fun locateNativeHelper(
    osName: String,
    jar: Path,
): Path {
    queryRequire(osName.startsWith("Mac"), "Interactive Keychain consultation requires macOS.")
    queryRequire(jar.toString().endsWith(".jar"), "Build :tools:query:installDist and use the installed verifactu launcher.")
    val helper = jar.parent.parent.resolve("bin/verifactu-query-keychain")
    queryRequire(Files.isRegularFile(helper) && Files.isExecutable(helper), "The installed native Keychain helper is unavailable.")
    return helper
}

internal fun executeNativeQuery(
    helper: Path,
    call: NativeQueryCall,
): NativeQueryReceipt {
    val arguments =
        mutableListOf(
            helper.toString(),
            "--endpoint",
            call.endpoint,
            "--request",
            call.request.toString(),
            "--sha256",
            call.sha256,
            "--response",
            call.directory.resolve("response.xml").toString(),
            "--result",
            call.directory.resolve("native.properties").toString(),
        )
    call.identity?.let { arguments += listOf("--identity-sha256", it) }
    call.issuer?.let { arguments += listOf("--issuer", it) }
    call.submissionSha256?.let { arguments += listOf("--confirm-submission-sha256", it) }
    if (call.submissionSha256 != null) arguments += listOf("--confirm-record-count", call.submissionRecordCount.toString())
    val process =
        try {
            ProcessBuilder(arguments)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (_: IOException) {
            return NativeQueryReceipt("HELPER_NOT_STARTED", "NOT_SENT", null, null)
        } catch (_: SecurityException) {
            return NativeQueryReceipt("HELPER_NOT_STARTED", "NOT_SENT", null, null)
        }
    val exit =
        try {
            process.waitFor()
        } catch (_: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            return NativeQueryReceipt("HELPER_INTERRUPTED", "UNKNOWN", null, null)
        }
    if (exit != 0) return NativeQueryReceipt("HELPER_FAILED", "UNKNOWN", null, null)
    return readNativeReceipt(call)
}

private fun readNativeReceipt(call: NativeQueryCall): NativeQueryReceipt =
    try {
        val values = queryProperties(call.directory.resolve("native.properties"))
        queryRequire(values["request.sha256"] == call.sha256, "Native helper receipt does not match the request.")
        queryRequire(values["request.url"] == call.endpoint, "Native helper used a different endpoint.")
        val responseUrl = values["response.url"]?.takeUnless { it == "unavailable" }
        queryRequire(responseUrl == null || responseUrl == call.endpoint, "Native helper response URL differs from the request.")
        val identity = values["identity.sha256"]?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
        queryRequire(identity == null || call.identity == null || call.identity == identity, "Native helper changed the selected identity.")
        val kind = values["kind"]
        val delivery = values["delivery"]
        queryRequire(NATIVE_DELIVERY[kind] == delivery && delivery != null, "Invalid native helper result.")
        val status = values["http.status"]?.toIntOrNull()
        queryRequire(delivery != "RESPONSE_RECEIVED" || status in 100..599, "Missing or invalid response HTTP status.")
        queryRequire(delivery != "RESPONSE_RECEIVED" || responseUrl != null, "Missing native response URL.")
        NativeQueryReceipt(kind!!, delivery!!, status, identity, responseUrl)
    } catch (_: IOException) {
        NativeQueryReceipt("INVALID_HELPER_RECEIPT", "UNKNOWN", null, null)
    } catch (_: QueryInputException) {
        NativeQueryReceipt("INVALID_HELPER_RECEIPT", "UNKNOWN", null, null)
    }

private val NATIVE_DELIVERY =
    mapOf(
        "XML_RESPONSE" to "RESPONSE_RECEIVED",
        "NON_XML_RESPONSE" to "RESPONSE_RECEIVED",
        "RESPONSE_TOO_LARGE" to "RESPONSE_RECEIVED",
        "UNKNOWN_DELIVERY" to "UNKNOWN",
        "SELECTION_CANCELLED" to "NOT_SENT",
        "IDENTITY_UNAVAILABLE" to "NOT_SENT",
    )
