package dev.verifactu.aeat

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
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
            return executePreparedTransport(prepared, maxResponseBytes, timeout)
        }
    }

private fun executePreparedTransport(
    prepared: Pair<HttpClient, HttpRequest>,
    maxResponseBytes: Int,
    timeout: Duration,
): AeatTransportResult {
    val pending =
        try {
            prepared.first.sendAsync(prepared.second) { BoundedResponseSubscriber(maxResponseBytes) }
        } catch (exception: IllegalArgumentException) {
            return classifyJvmTransportFailure(exception)
        } catch (exception: SecurityException) {
            return classifyJvmTransportFailure(exception)
        }
    return try {
        val response = pending.get(timeout.boundedNanoseconds(), TimeUnit.NANOSECONDS)
        val contentType = response.headers().firstValue("Content-Type").orElse(null)
        when (val result = response.body()) {
            is BoundedResponseBody.Complete ->
                classifyJvmTransportResponse(
                    statusCode = response.statusCode(),
                    contentType = contentType,
                    body = result.value,
                )
            BoundedResponseBody.TooLarge ->
                AeatTransportResult.ResponseTooLarge(response.statusCode(), contentType, maxResponseBytes)
        }
    } catch (exception: TimeoutException) {
        pending.cancel(true)
        classifyJvmTransportFailure(exception)
    } catch (exception: InterruptedException) {
        pending.cancel(true)
        Thread.currentThread().interrupt()
        classifyJvmTransportFailure(exception)
    } catch (exception: ExecutionException) {
        classifyJvmTransportFailure(exception.cause as? Exception ?: exception)
    } catch (exception: CancellationException) {
        classifyJvmTransportFailure(exception)
    }
}

private fun Duration.boundedNanoseconds(): Long =
    try {
        toNanos()
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
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

private class BoundedResponseSubscriber(
    private val maxBytes: Int,
) : HttpResponse.BodySubscriber<BoundedResponseBody> {
    private val output = java.io.ByteArrayOutputStream(minOf(maxBytes, RESPONSE_BUFFER_BYTES))
    private val completion = CompletableFuture<BoundedResponseBody>()
    private var subscription: Flow.Subscription? = null

    override fun getBody(): CompletionStage<BoundedResponseBody> = completion

    override fun onSubscribe(subscription: Flow.Subscription) {
        if (this.subscription != null) {
            subscription.cancel()
        } else {
            this.subscription = subscription
            subscription.request(1)
        }
    }

    override fun onNext(item: List<ByteBuffer>) {
        if (completion.isDone) return
        for (buffer in item) {
            val count = buffer.remaining()
            if (output.size() > maxBytes - count) {
                completion.complete(BoundedResponseBody.TooLarge)
                subscription?.cancel()
                return
            }
            val bytes = ByteArray(count)
            buffer.get(bytes)
            output.write(bytes)
        }
        subscription?.request(1)
    }

    override fun onError(throwable: Throwable) {
        completion.completeExceptionally(throwable)
    }

    override fun onComplete() {
        completion.complete(BoundedResponseBody.Complete(output.toString(Charsets.UTF_8.name())))
    }
}

private const val RESPONSE_BUFFER_BYTES: Int = 8_192
