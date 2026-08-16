package dev.verifactu.aeat

import java.time.Duration
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext
import kotlin.test.Test
import kotlin.test.assertEquals
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
                        sslContext = SSLContext.getDefault(),
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
    }

    @Test
    fun classifiesTimeoutNetworkAndNonXmlResponsesWithoutNetworkDoubles() {
        assertIs<AeatTransportResult.Timeout>(classifyJvmTransportFailure(TimeoutException("timed out")))
        assertIs<AeatTransportResult.NetworkFailure>(classifyJvmTransportFailure(java.io.IOException("offline")))

        val result = classifyJvmTransportResponse(502, "text/html", "<html>proxy error</html>")

        val nonXml = assertIs<AeatTransportResult.NonXmlResponse>(result)
        assertEquals(502, nonXml.statusCode)
    }
}
