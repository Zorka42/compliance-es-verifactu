package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AeatTransportResultTest {
    @Test
    fun distinguishesLocalFailuresFromResponsesAndUnknownDelivery() {
        assertEquals(AeatDeliveryState.NOT_SENT, AeatTransportResult.InvalidEndpoint("invalid").deliveryState)
        assertEquals(AeatDeliveryState.NOT_SENT, AeatTransportResult.NotSent("setup failed").deliveryState)
        assertEquals(AeatDeliveryState.UNKNOWN, AeatTransportResult.Timeout("timeout").deliveryState)
        assertEquals(AeatDeliveryState.UNKNOWN, AeatTransportResult.NetworkFailure("interrupted").deliveryState)
        assertEquals(
            AeatDeliveryState.RESPONSE_RECEIVED,
            AeatTransportResult.XmlResponse(500, "text/xml", "<Fault/>").deliveryState,
        )
        assertEquals(AeatDeliveryState.RESPONSE_RECEIVED, AeatTransportResult.NonXmlResponse(502, "text/html").deliveryState)
        val unknown: AeatTransportResult = AeatTransportResult.UnknownDelivery("connection closed after request write")
        assertEquals(AeatDeliveryState.UNKNOWN, unknown.deliveryState)
        assertEquals("connection closed after request write", assertIs<AeatTransportResult.UnknownDelivery>(unknown).reason)
    }

    @Test
    fun transportSummariesDoNotPrintPayloadsEndpointsOrExternalDiagnosticText() {
        val privateText = "PRIVATE-MARKER"
        val endpoint = AeatEndpointConfiguration.defaultSubmissionEndpoint(AeatEnvironment.TEST).copy(url = "https://$privateText.invalid")
        val request = AeatTransportRequest(endpoint, "<invoice>$privateText</invoice>")
        val response = AeatTransportResult.XmlResponse(200, "application/$privateText+xml", request.xmlPayload)
        val diagnostics =
            listOf(
                request,
                response,
                AeatTransportResult.NonXmlResponse(502, privateText),
                AeatTransportResult.InvalidEndpoint(privateText),
                AeatTransportResult.NotSent(privateText),
                AeatTransportResult.Timeout(privateText),
                AeatTransportResult.NetworkFailure(privateText),
            )

        diagnostics.forEach { assertFalse(it.toString().contains(privateText)) }
        assertEquals("<invoice>$privateText</invoice>", request.xmlPayload)
        assertEquals(request.xmlPayload, response.xml)
        assertEquals("application/$privateText+xml", response.contentType)
    }
}
