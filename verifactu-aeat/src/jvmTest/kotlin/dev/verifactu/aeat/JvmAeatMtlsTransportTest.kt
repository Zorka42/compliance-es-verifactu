package dev.verifactu.aeat

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsExchange
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmAeatMtlsTransportTest {
    @Test
    fun sendsToALocalMtlsServerWithDisposableClientIdentityAndSoapContentType() =
        withLoopbackMtlsServer { server ->
            val payload = "<soap:Envelope>local-only</soap:Envelope>"
            val receivedPayload = AtomicReference<String>()
            val receivedContentType = AtomicReference<String>()
            val peerPrincipal = AtomicReference<String>()
            server.handler = { exchange ->
                receivedPayload.set(exchange.requestBody.use { it.readBytes().toString(StandardCharsets.UTF_8) })
                receivedContentType.set(exchange.requestHeaders.getFirst("Content-Type"))
                peerPrincipal.set(exchange.sslSession.peerPrincipal.name)
                exchange.respond("text/xml; charset=UTF-8", "<Respuesta/>")
            }
            server.start()

            val result = server.transport(timeout = Duration.ofSeconds(2)).submit(server.endpoint(), payload)

            val response = assertIs<AeatTransportResult.XmlResponse>(result)
            assertEquals(200, response.statusCode)
            assertEquals("<Respuesta/>", response.xml)
            assertEquals(AeatDeliveryState.RESPONSE_RECEIVED, result.deliveryState)
            assertEquals(payload, receivedPayload.get())
            assertEquals("text/xml; charset=UTF-8", receivedContentType.get())
            assertEquals("CN=verifactu-loopback-client", assertNotNull(peerPrincipal.get()))
        }

    @Test
    fun boundsALocalResponseWithoutTreatingItsReceiptAsFiscalAcceptance() =
        withLoopbackMtlsServer { server ->
            val releaseBody = CountDownLatch(1)
            server.handler = { exchange ->
                exchange.requestBody.close()
                exchange.responseHeaders.set("Content-Type", "text/xml")
                exchange.sendResponseHeaders(200, 1_000)
                exchange.responseBody.write("x".repeat(33).toByteArray(StandardCharsets.UTF_8))
                exchange.responseBody.flush()
                try {
                    releaseBody.await(10, TimeUnit.SECONDS)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            try {
                val result = server.transport(timeout = Duration.ofSeconds(2), maxResponseBytes = 32).submit(server.endpoint(), "<local/>")

                val tooLarge = assertIs<AeatTransportResult.ResponseTooLarge>(result)
                assertEquals(200, tooLarge.statusCode)
                assertEquals("text/xml", tooLarge.contentType)
                assertEquals(32, tooLarge.maxBodyBytes)
                assertEquals(AeatDeliveryState.RESPONSE_RECEIVED, tooLarge.deliveryState)
                assertFalse(tooLarge.toString().contains("xxxxxxxx"))
            } finally {
                releaseBody.countDown()
            }
        }

    @Test
    fun classifiesLocalServerTimeoutAsUnknownDelivery() =
        withLoopbackMtlsServer { server ->
            server.handler = { exchange ->
                exchange.requestBody.close()
                Thread.sleep(1_000)
            }
            server.start()

            val result = server.transport(timeout = Duration.ofMillis(100)).submit(server.endpoint(), "<local/>")

            assertIs<AeatTransportResult.Timeout>(result)
            assertEquals(AeatDeliveryState.UNKNOWN, result.deliveryState)
        }

    @Test
    fun enforcesTheDeadlineAfterHeadersAndAPartialResponseBodyHaveArrived() =
        withLoopbackMtlsServer { server ->
            val bodyStarted = CountDownLatch(1)
            val releaseBody = CountDownLatch(1)
            server.handler = { exchange ->
                exchange.requestBody.use { it.readBytes() }
                exchange.responseHeaders.set("Content-Type", "text/xml; charset=UTF-8")
                exchange.sendResponseHeaders(200, 64)
                exchange.responseBody.write('<'.code)
                exchange.responseBody.flush()
                bodyStarted.countDown()
                try {
                    releaseBody.await(10, TimeUnit.SECONDS)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            try {
                val result = server.transport(timeout = Duration.ofSeconds(2)).submit(server.endpoint(), "<local/>")

                assertTrue(bodyStarted.await(1, TimeUnit.SECONDS), "The timeout must occur after response body delivery started.")
                assertIs<AeatTransportResult.Timeout>(result)
                assertEquals(AeatDeliveryState.UNKNOWN, result.deliveryState)
            } finally {
                releaseBody.countDown()
            }
        }

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
    fun reportsInvalidResponseLimitAsNotSentBeforeBuildingTheHttpClient() {
        val endpoint = AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.TEST).copy(url = "https://example.invalid/")

        listOf(0, -1).forEach { limit ->
            val result = assertIs<AeatTransportResult.NotSent>(testTransport(Duration.ofSeconds(1), limit).submit(endpoint, "<private/>"))

            assertEquals(AeatDeliveryState.NOT_SENT, result.deliveryState)
            assertEquals("The maximum response size must be positive.", result.reason)
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

    private fun testTransport(
        timeout: Duration,
        maxResponseBytes: Int = JvmAeatMtlsTransport.DEFAULT_MAX_RESPONSE_BYTES,
    ): JvmAeatMtlsTransport =
        JvmAeatMtlsTransport(
            credential =
                JvmAeatMtlsCredential(
                    AeatCredentialMetadata(AeatCredentialStrategy.JVM_KEY_STORE, AeatCredentialReference.fromAlias("unused-test-alias")),
                    SSLContext.getInstance("TLS"),
                ),
            timeout = timeout,
            maxResponseBytes = maxResponseBytes,
        )
}

private class LoopbackMtlsServer(
    private val server: HttpsServer,
    private val clientSslContext: SSLContext,
) : AutoCloseable {
    lateinit var handler: (HttpsExchange) -> Unit

    init {
        server.createContext("/") { exchange -> handler(exchange as HttpsExchange) }
    }

    fun start() {
        server.start()
    }

    fun endpoint(): AeatSubmissionEndpoint =
        AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.TEST).copy(
            url = "https://localhost:${server.address.port}/loopback",
        )

    fun transport(
        timeout: Duration,
        maxResponseBytes: Int = JvmAeatMtlsTransport.DEFAULT_MAX_RESPONSE_BYTES,
    ): JvmAeatMtlsTransport =
        JvmAeatMtlsTransport(
            credential =
                JvmAeatMtlsCredential(
                    metadata =
                        AeatCredentialMetadata(
                            strategy = AeatCredentialStrategy.JVM_KEY_STORE,
                            reference = AeatCredentialReference.fromAlias("disposable-loopback-client"),
                        ),
                    sslContext = clientSslContext,
                ),
            timeout = timeout,
            maxResponseBytes = maxResponseBytes,
        )

    override fun close() {
        server.stop(0)
    }
}

private fun HttpsExchange.respond(
    contentType: String,
    body: String,
) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.set("Content-Type", contentType)
    sendResponseHeaders(200, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private inline fun withLoopbackMtlsServer(block: (LoopbackMtlsServer) -> Unit) {
    val identities = DisposableMtlsIdentities.create()
    val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.httpsConfigurator =
        object : HttpsConfigurator(identities.serverSslContext) {
            override fun configure(parameters: HttpsParameters) {
                val sslParameters = identities.serverSslContext.defaultSSLParameters
                sslParameters.needClientAuth = true
                parameters.setSSLParameters(sslParameters)
            }
        }
    val loopback = LoopbackMtlsServer(server, identities.clientSslContext)
    try {
        block(loopback)
    } finally {
        loopback.close()
        identities.close()
    }
}

private class DisposableMtlsIdentities(
    private val directory: Path,
    val clientSslContext: SSLContext,
    val serverSslContext: SSLContext,
) : AutoCloseable {
    override fun close() {
        directory.toFile().deleteRecursively()
    }

    public companion object {
        fun create(): DisposableMtlsIdentities {
            val directory = Files.createTempDirectory("verifactu-loopback-mtls-")
            try {
                val serverStore = directory.resolve("server.p12")
                val clientStore = directory.resolve("client.p12")
                val serverTrust = directory.resolve("server-trust.p12")
                val clientTrust = directory.resolve("client-trust.p12")
                val serverCertificate = directory.resolve("server.cer")
                val clientCertificate = directory.resolve("client.cer")
                generateIdentity(serverStore, "server", "CN=localhost", "serverAuth", "SAN=dns:localhost,ip:127.0.0.1")
                generateIdentity(clientStore, "client", "CN=verifactu-loopback-client", "clientAuth")
                exportAndTrust(serverStore, "server", serverCertificate, clientTrust)
                exportAndTrust(clientStore, "client", clientCertificate, serverTrust)
                return DisposableMtlsIdentities(
                    directory = directory,
                    clientSslContext = sslContext(clientStore, clientTrust),
                    serverSslContext = sslContext(serverStore, serverTrust),
                )
            } catch (exception: Exception) {
                directory.toFile().deleteRecursively()
                throw exception
            }
        }
    }
}

private fun generateIdentity(
    keyStore: Path,
    alias: String,
    distinguishedName: String,
    extendedKeyUsage: String,
    vararg extensions: String,
) {
    keytool(
        "-genkeypair",
        "-alias",
        alias,
        "-keyalg",
        "RSA",
        "-keysize",
        "2048",
        "-validity",
        "1",
        "-dname",
        distinguishedName,
        "-ext",
        "EKU=$extendedKeyUsage",
        *extensions.flatMap { listOf("-ext", it) }.toTypedArray(),
        "-storetype",
        "PKCS12",
        "-keystore",
        keyStore.toString(),
        "-storepass",
        LOOPBACK_STORE_PASSWORD,
        "-keypass",
        LOOPBACK_STORE_PASSWORD,
        "-noprompt",
    )
}

private fun exportAndTrust(
    sourceStore: Path,
    alias: String,
    certificate: Path,
    trustStore: Path,
) {
    keytool(
        "-exportcert",
        "-rfc",
        "-alias",
        alias,
        "-keystore",
        sourceStore.toString(),
        "-storepass",
        LOOPBACK_STORE_PASSWORD,
        "-file",
        certificate.toString(),
    )
    keytool(
        "-importcert",
        "-alias",
        alias,
        "-file",
        certificate.toString(),
        "-storetype",
        "PKCS12",
        "-keystore",
        trustStore.toString(),
        "-storepass",
        LOOPBACK_STORE_PASSWORD,
        "-noprompt",
    )
}

private fun sslContext(
    keyStorePath: Path,
    trustStorePath: Path,
): SSLContext {
    val password = LOOPBACK_STORE_PASSWORD.toCharArray()
    val keyStore = loadKeyStore(keyStorePath, password)
    val trustStore = loadKeyStore(trustStorePath, password)
    val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, password) }.keyManagers
    val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }.trustManagers
    return SSLContext.getInstance("TLS").apply { init(keyManagers, trustManagers, SecureRandom()) }
}

private fun loadKeyStore(
    path: Path,
    password: CharArray,
): KeyStore =
    KeyStore.getInstance("PKCS12").apply {
        Files.newInputStream(path).use { load(it, password) }
    }

private fun keytool(vararg arguments: String) {
    val executable = Path.of(System.getProperty("java.home"), "bin", "keytool")
    val process = ProcessBuilder(listOf(executable.toString()) + arguments).redirectErrorStream(true).start()
    process.inputStream.use { it.readBytes() }
    check(process.waitFor() == 0) { "Disposable loopback TLS identity generation failed." }
}

private const val LOOPBACK_STORE_PASSWORD: String = "local-test-only"
