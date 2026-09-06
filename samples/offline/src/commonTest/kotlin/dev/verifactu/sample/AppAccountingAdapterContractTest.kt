package dev.verifactu.sample

import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.core.ChainState
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario
import dev.verifactu.testkit.FakeAeatTransport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppAccountingAdapterContractTest {
    @Test
    fun mapsFinalizedHostDataAndAtomicallyPersistsRecordWithItsNextChainHead() {
        val store = FakeAppAccountingStore()
        val adapter = AppAccountingVerifactuAdapter(store)
        val finalized = finalizedRegistration()

        val persisted =
            assertIs<AppAccountingPreparationResult.Persisted>(
                adapter.prepareAndPersist(finalized, QrEnvironment.TEST),
            ).registration

        assertEquals(finalized.applicationInvoiceId, persisted.applicationInvoiceId)
        assertEquals(finalized.chainId, persisted.chainId)
        assertEquals(finalized.invoice, persisted.record.draft.invoice)
        assertEquals(finalized.issuerName, persisted.record.draft.issuerName)
        assertEquals(finalized.totalAmount, persisted.record.draft.totalAmount)
        assertEquals(finalized.generatedAt, persisted.record.draft.generatedAt)
        assertEquals(listOf(persisted), store.persistedRegistrations)
        assertEquals(persisted.nextChainState, store.chainState)

        store.rejectNextChainCompareAndSet = true
        assertIs<AppAccountingPreparationResult.ChainConflict>(adapter.prepareAndPersist(finalized, QrEnvironment.TEST))
        assertEquals(listOf(persisted), store.persistedRegistrations)
        assertEquals(persisted.nextChainState, store.chainState)
    }

    @Test
    fun recoversAmbiguousDeliveryByReplayingThePersistedRequestThroughAFakeTransport() {
        val store = FakeAppAccountingStore()
        val adapter = AppAccountingVerifactuAdapter(store)
        val persisted =
            assertIs<AppAccountingPreparationResult.Persisted>(
                adapter.prepareAndPersist(finalizedRegistration(), QrEnvironment.TEST),
            ).registration
        val transport =
            FakeAeatTransport(
                listOf(
                    AeatTransportResult.Timeout("Synthetic unknown delivery"),
                    AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, persisted.record.draft.invoice),
                ),
            )
        val savedPayload = persisted.soapEnvelope.encodeToByteArray()
        val savedHash = persisted.record.hash
        val savedTimestamp = persisted.record.draft.generatedAt

        assertIs<ExampleAttemptOutcome.UnknownDelivery>(adapter.submitPersisted(persisted, offlineEndpoint(), transport))
        val accepted = assertIs<ExampleAttemptOutcome.KnownResponse>(adapter.submitPersisted(persisted, offlineEndpoint(), transport))

        assertTrue(accepted.isAccepted)
        assertEquals(2, store.attempts.size)
        assertEquals(2, store.completedAttemptOutcomes.size)
        assertContentEquals(savedPayload, store.attempts[0].soapEnvelope.encodeToByteArray())
        assertContentEquals(savedPayload, store.attempts[1].soapEnvelope.encodeToByteArray())
        assertContentEquals(savedPayload, transport.requests[0].xmlPayload.encodeToByteArray())
        assertContentEquals(savedPayload, transport.requests[1].xmlPayload.encodeToByteArray())
        assertEquals(savedHash, persisted.record.hash)
        assertEquals(savedTimestamp, persisted.record.draft.generatedAt)
        assertEquals(persisted.nextChainState, store.chainState)
    }

    private fun finalizedRegistration(): AppAccountingFinalizedRegistration {
        val draft: RegistroAltaDraft = exampleRegistrationDraft()
        return AppAccountingFinalizedRegistration(
            applicationInvoiceId = "app-invoice-1",
            chainId = "taxpayer-89890001K-sif-sample-1",
            version = draft.version,
            invoice = draft.invoice,
            issuerName = draft.issuerName,
            invoiceType = draft.invoiceType,
            totalTax = draft.totalTax,
            totalAmount = draft.totalAmount,
            operationDescription = draft.operationDescription,
            taxBreakdown = draft.taxBreakdown,
            system = draft.system,
            generatedAt = draft.generatedAt,
            conditionalData = draft.conditionalData,
        )
    }
}

private class FakeAppAccountingStore : AppAccountingFiscalStore {
    var chainState: ChainState = ChainState.FirstRecord
    val persistedRegistrations: MutableList<AppAccountingPersistedRegistration> = mutableListOf()
    val attempts: MutableList<FakeAttempt> = mutableListOf()
    val completedAttemptOutcomes: MutableList<ExampleAttemptOutcome> = mutableListOf()
    var rejectNextChainCompareAndSet: Boolean = false

    override fun loadChainState(chainId: String): ChainState = chainState

    override fun persistRegistrationAndAdvanceChain(
        expectedChainState: ChainState,
        registration: AppAccountingPersistedRegistration,
    ): Boolean {
        if (rejectNextChainCompareAndSet || expectedChainState != chainState) {
            rejectNextChainCompareAndSet = false
            return false
        }
        persistedRegistrations.add(registration)
        chainState = registration.nextChainState
        return true
    }

    override fun beginAttempt(
        applicationInvoiceId: String,
        soapEnvelope: String,
    ): Long {
        val attempt = FakeAttempt(attempts.size.toLong() + 1, applicationInvoiceId, soapEnvelope)
        attempts.add(attempt)
        return attempt.id
    }

    override fun completeAttempt(
        attemptId: Long,
        outcome: ExampleAttemptOutcome,
    ) {
        check(attempts.any { it.id == attemptId })
        completedAttemptOutcomes.add(outcome)
    }
}

private data class FakeAttempt(
    val id: Long,
    val applicationInvoiceId: String,
    val soapEnvelope: String,
)
