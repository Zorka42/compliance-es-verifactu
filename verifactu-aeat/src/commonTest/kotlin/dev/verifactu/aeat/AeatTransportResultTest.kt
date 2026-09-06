package dev.verifactu.aeat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AeatTransportResultTest {
    @Test
    fun retainsAmbiguousDeliveryWithoutSchedulingAnAutomaticRetry() {
        val result: AeatTransportResult = AeatTransportResult.UnknownDelivery("Connection closed after request write")

        assertEquals(
            "Connection closed after request write",
            assertIs<AeatTransportResult.UnknownDelivery>(result).reason,
        )
    }
}
