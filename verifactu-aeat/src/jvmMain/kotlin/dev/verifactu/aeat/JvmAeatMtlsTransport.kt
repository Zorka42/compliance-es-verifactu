package dev.verifactu.aeat

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext
import kotlin.jvm.JvmOverloads

/** A JVM-provided client certificate context for mutual-TLS transport. */
public class JvmAeatMtlsCredential(
    override val metadata: AeatCredentialMetadata,
    internal val sslContext: SSLContext,
) : AeatClientCredential

/**
 * JVM HTTPS transport for prepared AEAT SOAP XML payloads.
 *
 * It performs no logging and has no certificate or key-store loading behaviour. The caller
 * provides a ready-to-use [SSLContext] through [JvmAeatMtlsCredential].
 */
public class JvmAeatMtlsTransport
    @JvmOverloads
    constructor(
        private val credential: JvmAeatMtlsCredential,
        private val timeout: Duration,
        private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    ) : AeatTransportAdapter {
        public companion object {
            /** Default upper bound for an HTTP response body retained by this transport. */
            public const val DEFAULT_MAX_RESPONSE_BYTES: Int = 1_048_576
        }

        /** Executes one prepared request using this instance's caller-supplied TLS context. */
        override fun execute(request: AeatTransportRequest): AeatTransportResult = submit(request.endpoint, request.xmlPayload)

        /** Sends a prepared SOAP XML payload and classifies the transport-level outcome. */
        public fun submit(
            endpoint: AeatSubmissionEndpoint,
            xmlPayload: String,
        ): AeatTransportResult {
            val uri =
                validatedJvmEndpoint(endpoint.url)
                    ?: return AeatTransportResult.InvalidEndpoint(
                        "The AEAT endpoint must be an absolute HTTPS URL with a host and no user information or fragment.",
                    )
            if (timeout.isZero || timeout.isNegative) {
                return AeatTransportResult.NotSent("The request timeout must be positive.")
            }
            if (maxResponseBytes <= 0) {
                return AeatTransportResult.NotSent("The maximum response size must be positive.")
            }
            val prepared =
                try {
                    val request =
                        HttpRequest
                            .newBuilder()
                            .uri(uri)
                            .timeout(timeout)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(xmlPayload))
                            .build()
                    val client =
                        HttpClient
                            .newBuilder()
                            .sslContext(credential.sslContext)
                            .connectTimeout(timeout)
                            .build()
                    client to request
                } catch (_: IllegalArgumentException) {
                    return AeatTransportResult.NotSent("The request configuration is invalid.")
                } catch (_: IllegalStateException) {
                    return AeatTransportResult.NotSent("The transport configuration is unavailable.")
                } catch (_: SecurityException) {
                    return AeatTransportResult.NotSent("Local security policy prevented request setup.")
                }
            return executePreparedTransport(prepared, maxResponseBytes)
        }
    }

private fun executePreparedTransport(
    prepared: Pair<HttpClient, HttpRequest>,
    maxResponseBytes: Int,
): AeatTransportResult =
    try {
        val response = prepared.first.send(prepared.second, HttpResponse.BodyHandlers.ofInputStream())
        val contentType = response.headers().firstValue("Content-Type").orElse(null)
        response.body().use { body ->
            when (val result = body.readUpTo(maxResponseBytes)) {
                is BoundedResponseBody.Complete ->
                    classifyJvmTransportResponse(
                        statusCode = response.statusCode(),
                        contentType = contentType,
                        body = result.value,
                    )
                BoundedResponseBody.TooLarge ->
                    AeatTransportResult.ResponseTooLarge(response.statusCode(), contentType, maxResponseBytes)
            }
        }
    } catch (exception: TimeoutException) {
        classifyJvmTransportFailure(exception)
    } catch (exception: java.net.http.HttpTimeoutException) {
        classifyJvmTransportFailure(exception)
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        classifyJvmTransportFailure(exception)
    } catch (exception: java.io.IOException) {
        classifyJvmTransportFailure(exception)
    } catch (exception: IllegalArgumentException) {
        classifyJvmTransportFailure(exception)
    } catch (exception: SecurityException) {
        classifyJvmTransportFailure(exception)
    }

private fun validatedJvmEndpoint(url: String): URI? {
    val uri =
        try {
            URI.create(url)
        } catch (_: IllegalArgumentException) {
            return null
        }
    return uri.takeIf {
        it.scheme.equals("https", ignoreCase = true) &&
            !it.host.isNullOrBlank() &&
            it.rawUserInfo == null &&
            it.rawFragment == null &&
            (it.port == -1 || it.port in 1..65535)
    }
}

internal fun classifyJvmTransportResponse(
    statusCode: Int,
    contentType: String?,
    body: String,
): AeatTransportResult =
    if (contentType?.lowercase()?.contains("xml") == true) {
        AeatTransportResult.XmlResponse(statusCode, contentType, body)
    } else {
        AeatTransportResult.NonXmlResponse(statusCode, contentType)
    }

internal fun classifyJvmTransportFailure(exception: Exception): AeatTransportResult =
    when (exception) {
        is TimeoutException,
        is java.net.http.HttpTimeoutException,
        -> AeatTransportResult.Timeout("The AEAT request timed out; delivery is unknown.")
        is InterruptedException -> AeatTransportResult.NetworkFailure("The AEAT request was interrupted.")
        else -> AeatTransportResult.NetworkFailure("The AEAT request failed; delivery is unknown.")
    }

private sealed interface BoundedResponseBody {
    public data class Complete(
        public val value: String,
    ) : BoundedResponseBody

    public data object TooLarge : BoundedResponseBody
}

private fun java.io.InputStream.readUpTo(maxBytes: Int): BoundedResponseBody {
    val output = java.io.ByteArrayOutputStream(minOf(maxBytes, RESPONSE_BUFFER_BYTES))
    val buffer = ByteArray(RESPONSE_BUFFER_BYTES)
    while (true) {
        val read = read(buffer)
        if (read == -1) return BoundedResponseBody.Complete(output.toString(Charsets.UTF_8.name()))
        if (output.size() > maxBytes - read) return BoundedResponseBody.TooLarge
        output.write(buffer, 0, read)
    }
}

private const val RESPONSE_BUFFER_BYTES: Int = 8_192
