package dev.verifactu.sample

import dev.verifactu.aeat.AeatTransportResult
import dev.verifactu.core.ChainState
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.core.TaxBreakdown
import dev.verifactu.core.TaxType
import dev.verifactu.core.ValidationSeverity
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario
import dev.verifactu.testkit.FakeAeatTransport
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
        assertEquals(persisted.nextChainState, store.loadChainState(persisted.chainId))

        val staleReader =
            object : AppAccountingFiscalStore by store {
                override fun loadChainState(chainId: String): ChainState = ChainState.FirstRecord
            }
        assertIs<AppAccountingPreparationResult.ChainConflict>(
            AppAccountingVerifactuAdapter(staleReader).prepareAndPersist(
                finalized.copy(applicationInvoiceId = "app-invoice-2"),
                QrEnvironment.TEST,
            ),
        )
        assertEquals(listOf(persisted), store.persistedRegistrations)
        assertEquals(persisted.nextChainState, store.loadChainState(persisted.chainId))
    }

    @Test
    fun repeatedFinalizationCannotCreateAnotherRecordOrAdvanceTheHead() {
        val store = FakeAppAccountingStore()
        val finalized = finalizedRegistration()
        val adapter = AppAccountingVerifactuAdapter(store)
        val saved =
            assertIs<AppAccountingPreparationResult.Persisted>(
                adapter.prepareAndPersist(finalized, QrEnvironment.TEST),
            ).registration

        assertIs<AppAccountingPreparationResult.AlreadyRecorded>(
            AppAccountingVerifactuAdapter(store).prepareAndPersist(finalized, QrEnvironment.TEST),
        )
        assertEquals(listOf(saved), store.persistedRegistrations)
        assertEquals(saved.nextChainState, store.loadChainState(saved.chainId))
        assertTrue(store.attempts.isEmpty())
    }

    @Test
    fun invalidFinalizedDataDoesNotPersistOrAdvanceTheChain() {
        val store = FakeAppAccountingStore()
        assertIs<AppAccountingPreparationResult.Invalid>(
            AppAccountingVerifactuAdapter(store).prepareAndPersist(
                finalizedRegistration().copy(operationDescription = ""),
                QrEnvironment.TEST,
            ),
        )
        assertEquals(ChainState.FirstRecord, store.loadChainState(finalizedRegistration().chainId))
        assertTrue(store.persistedRegistrations.isEmpty())
        assertTrue(store.attempts.isEmpty())
    }

    @Test
    fun persistsContextWarningsForAnExplicitHostDecisionBeforeDelivery() {
        val store = FakeAppAccountingStore()
        val finalized = finalizedRegistration()
        val ipsi =
            finalized.copy(
                taxBreakdown = TaxBreakdown(finalized.taxBreakdown.details.map { it.copy(tax = TaxType.IPSI, regimeCode = null) }),
            )
        val saved =
            assertIs<AppAccountingPreparationResult.Persisted>(
                AppAccountingVerifactuAdapter(store).prepareAndPersist(ipsi, QrEnvironment.TEST),
            ).registration
        val warning = saved.validationReport.issues.single { it.code == "VF-TAX-IPSI-TRANSITION" }
        assertEquals(ValidationSeverity.WARNING, warning.severity)
        assertNotNull(warning.source)
        assertTrue(saved.validationReport.isValid)
        assertTrue(store.attempts.isEmpty())
    }

    @Test
    fun restartAfterResponsePersistenceFailureLeavesAnUnresolvedAttemptAndReplaysSavedBytes() {
        val store = FakeAppAccountingStore()
        val adapter = AppAccountingVerifactuAdapter(store)
        val saved =
            assertIs<AppAccountingPreparationResult.Persisted>(
                adapter.prepareAndPersist(finalizedRegistration(), QrEnvironment.TEST),
            ).registration
        val transport =
            FakeAeatTransport(
                List(2) { AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, saved.record.draft.invoice) },
            )
        store.failNextAttemptCompletion = true
        assertFailsWith<SimulatedStoreFailure> { adapter.submitPersisted(saved, offlineEndpoint(), transport) }
        assertEquals(1, store.attempts.size)
        assertTrue(store.completedAttemptOutcomes.isEmpty())

        // A restarted host sees an unresolved attempt, not durable evidence of acceptance.
        val reloaded = assertNotNull(store.loadRegistration(saved.applicationInvoiceId))
        val restartedAdapter = AppAccountingVerifactuAdapter(store)
        assertEquals(1, transport.requests.size)
        val replay =
            assertIs<ExampleAttemptOutcome.KnownResponse>(
                restartedAdapter.submitPersisted(reloaded, offlineEndpoint(), transport),
            )
        assertTrue(replay.isAccepted)
        assertEquals(listOf(saved), store.persistedRegistrations)
        assertEquals(saved.nextChainState, store.loadChainState(saved.chainId))
        assertEquals(listOf(2L), store.completedAttemptIds)
        assertEquals(listOf(saved.soapEnvelope, saved.soapEnvelope), transport.requests.map { it.xmlPayload })
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
        assertEquals(persisted.nextChainState, store.loadChainState(persisted.chainId))
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
    private val chainStates: MutableMap<String, ChainState> = mutableMapOf()
    val persistedRegistrations: MutableList<AppAccountingPersistedRegistration> = mutableListOf()
    val attempts: MutableList<FakeAttempt> = mutableListOf()
    val completedAttemptOutcomes: MutableList<ExampleAttemptOutcome> = mutableListOf()
    val completedAttemptIds: MutableList<Long> = mutableListOf()
    var failNextAttemptCompletion: Boolean = false

    override fun loadChainState(chainId: String): ChainState = chainStates[chainId] ?: ChainState.FirstRecord

    override fun loadRegistration(applicationInvoiceId: String): AppAccountingPersistedRegistration? =
        persistedRegistrations.singleOrNull { it.applicationInvoiceId == applicationInvoiceId }

    override fun persistRegistrationAndAdvanceChain(
        expectedChainState: ChainState,
        registration: AppAccountingPersistedRegistration,
    ): AppAccountingPersistenceResult {
        if (loadRegistration(registration.applicationInvoiceId) != null) return AppAccountingPersistenceResult.ALREADY_RECORDED
        if (expectedChainState != loadChainState(registration.chainId)) {
            return AppAccountingPersistenceResult.CHAIN_CONFLICT
        }
        persistedRegistrations.add(registration)
        chainStates[registration.chainId] = registration.nextChainState
        return AppAccountingPersistenceResult.PERSISTED
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
        if (failNextAttemptCompletion) {
            failNextAttemptCompletion = false
            throw SimulatedStoreFailure()
        }
        completedAttemptIds.add(attemptId)
        completedAttemptOutcomes.add(outcome)
    }
}

private class SimulatedStoreFailure : Exception("Synthetic persistence failure")

private data class FakeAttempt(
    val id: Long,
    val applicationInvoiceId: String,
    val soapEnvelope: String,
)
