package dev.verifactu.sample

import dev.verifactu.aeat.AeatRecordStatus
import dev.verifactu.core.RecordHashCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineExampleTest {
    @Test
    fun runsSharedConsumerPipelineAndRetainsChainAndArtifacts() {
        val result = runOfflineExample()

        assertEquals(result.registration.hash, (result.cancellation.draft.chainState as dev.verifactu.core.ChainState.PreviousRecord).hash)
        assertEquals(result.cancellation.hash, result.nextChainState.hash)
        assertTrue(result.registrationXml.contains("<Huella>${result.registration.hash}</Huella>"))
        assertTrue(result.cancellationXml.contains("<RegistroAnulacion"))
        assertTrue(result.qrUrl.endsWith("&importe=121.00"))
        assertEquals(listOf(AeatRecordStatus.ACCEPTED, AeatRecordStatus.ACCEPTED), result.responses.map { it.lines.single().status })
        assertEquals(60, result.responses[1].retryAfterSeconds)
        assertEquals(2, result.capturedRequests.size)
        assertTrue(result.capturedRequests.all { it.endpoint.url == "https://example.invalid/verifactu" })
        assertEquals(
            RecordHashCalculator.sha256(result.registrationXml),
            RecordHashCalculator.sha256(runOfflineExample().registrationXml),
        )
    }
}
