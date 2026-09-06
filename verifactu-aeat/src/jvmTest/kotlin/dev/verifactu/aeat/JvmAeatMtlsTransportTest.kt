package dev.verifactu.aeat

import java.time.Duration
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class JvmAeatMtlsTransportTest {
    @Test
    fun rejectsNonHttpsEndpointsBeforeAnyNetworkOperation() {
        val transport =
            JvmAeatMtlsTransport(
                credential =
                    JvmAeatMtlsCredential(
                        metadata =
                            AeatCredentialMetadata(
                                strategy = AeatCredentialStrategy.JVM_KEY_STORE,
                                reference = AeatCredentialReference.fromAlias("test-certificate"),
                            ),
                        sslContext = SSLContext.getInstance("TLS"),
                    ),
                timeout = Duration.ofSeconds(1),
            )

        val result =
            transport.submit(
                endpoint =
                    AeatSubmissionEndpoint(
                        environment = AeatEnvironment.TEST,
                        certificateAccess = AeatCertificateAccess.STANDARD,
                        version = AeatServiceVersion.TIKE_V1_0,
                        url = "http://localhost:8080/not-aeat",
                        sourceUrl = "test",
                        features = listOf(AeatSubmissionFeature.REGISTRATION_SUBMISSION),
                    ),
                xmlPayload = "<RegFactuSistemaFacturacion/>",
            )

        assertIs<AeatTransportResult.InvalidEndpoint>(result)
        assertEquals(AeatDeliveryState.NOT_SENT, result.deliveryState)
    }

    @Test
    fun rejectsMalformedOrAmbiguousDestinationsThroughTheSharedAdapterBeforeDelivery() {
        val adapter: AeatTransportAdapter = testTransport(Duration.ofSeconds(1))
        val invalidUrls =
            listOf(
                "https://",
                "https:///missing-host",
                "https://private-user:private-password@example.invalid/",
                "https://example.invalid/#private-fragment",
                "https://example.invalid:65536/",
                "https://example.invalid:0/",
                "https://example.invalid/private path",
                "https:example.invalid",
            )

        invalidUrls.forEach { url ->
            val endpoint = AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.TEST).copy(url = url)
            val result = assertIs<AeatTransportResult.InvalidEndpoint>(adapter.execute(AeatTransportRequest(endpoint, "<private/>")))

            assertEquals(AeatDeliveryState.NOT_SENT, result.deliveryState)
            assertFalse(result.reason.contains("private"))
            assertFalse(result.reason.contains(url))
        }
    }

    @Test
    fun reportsInvalidTimeoutAsNotSentBeforeBuildingTheHttpClient() {
        val endpoint = AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.TEST).copy(url = "https://example.invalid/")

        listOf(Duration.ZERO, Duration.ofMillis(-1)).forEach { timeout ->
            val result = assertIs<AeatTransportResult.NotSent>(testTransport(timeout).submit(endpoint, "<private/>"))

            assertEquals(AeatDeliveryState.NOT_SENT, result.deliveryState)
            assertEquals("The request timeout must be positive.", result.reason)
        }
    }

    @Test
    fun failureClassificationRetainsUncertaintyAndOmitsExceptionMessages() {
        val privateText = "PRIVATE-MARKER"
        val exceptions =
            listOf(
                java.io.IOException(privateText),
                InterruptedException(privateText),
                SecurityException(privateText),
                IllegalArgumentException(privateText),
            )

        exceptions.forEach { exception ->
            val result = assertIs<AeatTransportResult.NetworkFailure>(classifyJvmTransportFailure(exception))

            assertEquals(AeatDeliveryState.UNKNOWN, result.deliveryState)
            assertFalse(result.reason.contains(privateText))
        }
        val timeout =
            assertIs<AeatTransportResult.Timeout>(classifyJvmTransportFailure(java.net.http.HttpConnectTimeoutException(privateText)))
        assertEquals(AeatDeliveryState.UNKNOWN, timeout.deliveryState)
        assertEquals("The AEAT request timed out; delivery is unknown.", timeout.reason)
    }

    @Test
    fun classifiesTimeoutNetworkAndNonXmlResponsesWithoutNetworkDoubles() {
        assertIs<AeatTransportResult.Timeout>(classifyJvmTransportFailure(TimeoutException("timed out")))
        assertIs<AeatTransportResult.NetworkFailure>(classifyJvmTransportFailure(java.io.IOException("offline")))

        val result = classifyJvmTransportResponse(502, "text/html", "<html>proxy error</html>")

        val nonXml = assertIs<AeatTransportResult.NonXmlResponse>(result)
        assertEquals(502, nonXml.statusCode)
    }

    private fun testTransport(timeout: Duration): JvmAeatMtlsTransport =
        JvmAeatMtlsTransport(
            credential =
                JvmAeatMtlsCredential(
                    AeatCredentialMetadata(AeatCredentialStrategy.JVM_KEY_STORE, AeatCredentialReference.fromAlias("unused-test-alias")),
                    SSLContext.getInstance("TLS"),
                ),
            timeout = timeout,
        )
}
