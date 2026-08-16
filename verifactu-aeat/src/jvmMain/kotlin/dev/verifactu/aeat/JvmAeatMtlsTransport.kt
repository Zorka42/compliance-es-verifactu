package dev.verifactu.aeat

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext

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
public class JvmAeatMtlsTransport(
    private val credential: JvmAeatMtlsCredential,
    private val timeout: Duration,
) {
    /** Sends a prepared SOAP XML payload and classifies the transport-level outcome. */
    public fun submit(
        endpoint: AeatSubmissionEndpoint,
        xmlPayload: String,
    ): AeatTransportResult {
        if (!endpoint.url.startsWith("https://")) {
            return AeatTransportResult.InvalidEndpoint("The AEAT endpoint must use HTTPS.")
        }
        return try {
            val response =
                HttpClient
                    .newBuilder()
                    .sslContext(credential.sslContext)
                    .connectTimeout(timeout)
                    .build()
                    .send(
                        HttpRequest
                            .newBuilder()
                            .uri(java.net.URI.create(endpoint.url))
                            .timeout(timeout)
                            .header("Content-Type", "text/xml; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(xmlPayload))
                            .build(),
                        HttpResponse.BodyHandlers.ofString(),
                    )
            classifyJvmTransportResponse(
                statusCode = response.statusCode(),
                contentType = response.headers().firstValue("Content-Type").orElse(null),
                body = response.body(),
            )
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
        -> AeatTransportResult.Timeout(exception.message ?: "The AEAT request timed out.")
        is InterruptedException -> AeatTransportResult.NetworkFailure("The AEAT request was interrupted.")
        else -> AeatTransportResult.NetworkFailure(exception.message ?: "The AEAT connection failed.")
    }
