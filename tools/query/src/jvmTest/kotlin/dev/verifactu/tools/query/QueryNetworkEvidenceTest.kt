package dev.verifactu.tools.query

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryNetworkEvidenceTest {
    @Test
    fun neverCopiesUnexpectedResponseUrlsOrIdentityIntoShareableReport() {
        for (url in listOf(null, "https://unexpected.invalid/?PRIVATE", TEST_QUERY_ENDPOINT)) {
            val receipt = NativeQueryReceipt("UNKNOWN_DELIVERY", "UNKNOWN", null, "PRIVATE_IDENTITY", url)
            val report = queryNetworkPage(QueryDirection.RECEIVED, 2, receipt)
            assertTrue(report.contains("page.2.response.url=${if (url == TEST_QUERY_ENDPOINT) url else "unavailable"}"))
            assertTrue(report.contains("page.2.http.status=unavailable"))
            assertFalse(report.contains("PRIVATE"))
        }
    }
}
