package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppleAeatTransportTest {
    @Test
    fun delegatesExactRequestAndPreservesTransportOutcome() {
        val endpoint = AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.TEST)
        val response = AeatTransportResult.Timeout("Synthetic timeout")
        var received: AeatTransportRequest? = null
        val transport =
            AppleAeatTransport(
                object : AeatTransportAdapter {
                    override fun execute(request: AeatTransportRequest): AeatTransportResult {
                        received = request
                        return response
                    }
                },
            )

        assertSame(response, transport.submit(endpoint, "<prepared/>"))
        assertEquals(AeatTransportRequest(endpoint, "<prepared/>"), received)
    }

    @Test
    fun rejectsInsecureEndpointBeforeAdapterExecution() {
        var received: AeatTransportRequest? = null
        val transport =
            AppleAeatTransport(
                object : AeatTransportAdapter {
                    override fun execute(request: AeatTransportRequest): AeatTransportResult {
                        received = request
                        return AeatTransportResult.NetworkFailure("Unexpected call")
                    }
                },
            )
        val endpoint =
            AeatEndpointConfiguration
                .defaultSubmissionEndpoint(AeatEnvironment.TEST)
                .copy(url = "http://localhost/unused")

        assertIs<AeatTransportResult.InvalidEndpoint>(transport.submit(endpoint, "<prepared/>"))
        assertNull(received)
    }
}
