package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.aeat.JvmAeatMtlsTransport
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxMtlsTest {
    @Test
    fun selectedDisposableClientIdentitySendsReviewedXmlToLocalHttpsAndPersistsResponse() =
        withWorkspace { root ->
            val bundle = fixtureBundle(root, "aeat-test")
            DisposableSandboxTls(root).use { tls ->
                val calls = AtomicInteger()
                tls.handler = { exchange ->
                    assertTrue(
                        exchange.sslSession.peerPrincipal.name
                            .contains("selected-client"),
                    )
                    assertEquals("POST", exchange.requestMethod)
                    assertEquals("text/xml; charset=UTF-8", exchange.requestHeaders.getFirst("Content-Type"))
                    assertEquals(bundle.plan.soap, exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) })
                    assertTrue(Files.exists(root.resolve("attempt/started.properties")))
                    calls.incrementAndGet()
                    val xml = (simulatedResponse(bundle, "accepted") as AeatTransportResult.XmlResponse).xml.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "text/xml; charset=UTF-8")
                    exchange.sendResponseHeaders(200, xml.size.toLong())
                    exchange.responseBody.use { it.write(xml) }
                }
                tls.start()
                val result =
                    runCommand(sendArguments(root, bundle.sha256), { DISPOSABLE_PASSWORD }) { path, alias, password ->
                        val credential = loadCredential(path, alias, password, tls.clientTrust)
                        val transport = JvmAeatMtlsTransport(credential, Duration.ofSeconds(5))
                        object : AeatTransportAdapter {
                            override fun execute(request: AeatTransportRequest): AeatTransportResult =
                                transport.execute(
                                    request.copy(
                                        endpoint = request.endpoint.copy(url = "https://localhost:${tls.server.address.port}/local-test"),
                                    ),
                                )
                        }
                    }
                assertEquals(0, result.exitCode)
                assertEquals(1, calls.get())
                assertTrue(result.output.contains("MATCHED"))
                assertTrue(Files.exists(root.resolve("attempt/response.xml")))
                assertFalse(result.output.contains(DISPOSABLE_PASSWORD))
                assertFalse(result.output.contains("89890001K"))
                assertFailsWith<SandboxInputException> { loadCredential(tls.clientFile, "missing", DISPOSABLE_PASSWORD.toCharArray()) }
                val wrongPassword =
                    assertFailsWith<SandboxInputException> {
                        loadCredential(
                            tls.clientFile,
                            "selected-client",
                            "WRONG-PRIVATE".toCharArray(),
                        )
                    }
                assertFalse(wrongPassword.message.orEmpty().contains("WRONG-PRIVATE"))
            }
            assertFailsWith<SandboxInputException> { loadSandboxTransport(root.resolve("missing.p12"), "none", charArrayOf()) }
            val damaged = root.resolve("invalid.p12")
            Files.writeString(damaged, "not-a-keystore")
            assertFailsWith<SandboxInputException> { loadSandboxTransport(damaged, "none", charArrayOf()) }
        }
}
