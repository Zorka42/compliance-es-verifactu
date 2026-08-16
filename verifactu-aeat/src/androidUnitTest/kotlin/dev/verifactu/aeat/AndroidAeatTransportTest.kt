package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AndroidAeatTransportTest {
    @Test
    fun createsPreparedRequestForInjectedAndroidAdapter() {
        val adapter = RecordingAdapter()
        val result = AndroidAeatTransport(adapter).submit(testEndpoint(), "<soap:Envelope/>")

        assertEquals(AeatTransportResult.XmlResponse(200, "text/xml", "<response/>"), result)
        assertEquals("<soap:Envelope/>", adapter.request?.xmlPayload)
        assertEquals(testEndpoint(), adapter.request?.endpoint)
    }

    @Test
    fun rejectsNonHttpsBeforeInvokingAdapter() {
        val result = AndroidAeatTransport(RecordingAdapter()).submit(testEndpoint("http://localhost"), "<xml/>")

        assertIs<AeatTransportResult.InvalidEndpoint>(result)
    }

    private class RecordingAdapter : AeatTransportAdapter {
        var request: AeatTransportRequest? = null

        override fun execute(request: AeatTransportRequest): AeatTransportResult {
            this.request = request
            return AeatTransportResult.XmlResponse(200, "text/xml", "<response/>")
        }
    }

    private fun testEndpoint(url: String = "https://example.test/aeat"): AeatSubmissionEndpoint =
        AeatSubmissionEndpoint(
            environment = AeatEnvironment.TEST,
            certificateAccess = AeatCertificateAccess.STANDARD,
            version = AeatServiceVersion.TIKE_V1_0,
            url = url,
            sourceUrl = "test",
            features = listOf(AeatSubmissionFeature.REGISTRATION_SUBMISSION),
        )
}
