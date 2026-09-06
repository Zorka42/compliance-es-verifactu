package dev.verifactu.sample

import dev.verifactu.aeat.AeatSubmissionEndpoint
import dev.verifactu.aeat.AeatTransportAdapter
import dev.verifactu.aeat.AeatTransportRequest
import dev.verifactu.aeat.FiscalSubmissionPreparation
import dev.verifactu.aeat.RegistrationPreparationResult
import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalAmount
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceType
import dev.verifactu.core.RecordGenerationTimestamp
import dev.verifactu.core.RecordVersion
import dev.verifactu.core.RegistrationConditionalData
import dev.verifactu.core.RegistroAlta
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.core.SistemaInformatico
import dev.verifactu.core.TaxBreakdown
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.xml.SubmissionRecord

/**
 * An application-owned finalized invoice shape used only by the offline integration sample.
 *
 * It deliberately is not a library API or a database model. A real application maps its own
 * finalized invoice and configuration to this boundary, then owns its persistence and workflow.
 */
internal data class AppAccountingFinalizedRegistration(
    val applicationInvoiceId: String,
    val chainId: String,
    val version: RecordVersion,
    val invoice: InvoiceIdentifier,
    val issuerName: String,
    val invoiceType: InvoiceType,
    val totalTax: FiscalAmount,
    val totalAmount: FiscalAmount,
    val operationDescription: String,
    val taxBreakdown: TaxBreakdown,
    val system: SistemaInformatico,
    val generatedAt: RecordGenerationTimestamp,
    val conditionalData: RegistrationConditionalData,
)

/** Maps host-finalized values to the deterministic library DTO using the host's current chain head. */
internal fun AppAccountingFinalizedRegistration.toRegistrationDraft(chainState: ChainState): RegistroAltaDraft =
    RegistroAltaDraft(
        version = version,
        invoice = invoice,
        issuerName = issuerName,
        invoiceType = invoiceType,
        totalTax = totalTax,
        totalAmount = totalAmount,
        operationDescription = operationDescription,
        taxBreakdown = taxBreakdown,
        chainState = chainState,
        system = system,
        generatedAt = generatedAt,
        conditionalData = conditionalData,
    )

/** Immutable material that the host persists with its source invoice and next chain head. */
internal data class AppAccountingPersistedRegistration(
    val applicationInvoiceId: String,
    val chainId: String,
    val record: RegistroAlta,
    val nextChainState: ChainState.PreviousRecord,
    val soapEnvelope: String,
) {
    override fun toString(): String = "AppAccountingPersistedRegistration(redacted)"
}

/** Application persistence boundary; the implementation owns its database, locks and queue. */
internal interface AppAccountingFiscalStore {
    fun loadChainState(chainId: String): ChainState

    /** Persists the record and advances the head in one transaction or compare-and-set operation. */
    fun persistRegistrationAndAdvanceChain(
        expectedChainState: ChainState,
        registration: AppAccountingPersistedRegistration,
    ): Boolean

    /** Records an application-owned delivery attempt against the exact persisted request bytes. */
    fun beginAttempt(
        applicationInvoiceId: String,
        soapEnvelope: String,
    ): Long

    fun completeAttempt(
        attemptId: Long,
        outcome: ExampleAttemptOutcome,
    )
}

/** Result of preparing a finalized host invoice and atomically persisting its fiscal artifacts. */
internal sealed interface AppAccountingPreparationResult {
    data class Persisted(
        val registration: AppAccountingPersistedRegistration,
    ) : AppAccountingPreparationResult

    data class Invalid(
        val result: RegistrationPreparationResult.Invalid,
    ) : AppAccountingPreparationResult

    data object ChainConflict : AppAccountingPreparationResult
}

/**
 * Compilable host-side boundary for the offline sample.
 *
 * It neither locks nor stores data itself. The store rejects a stale head, and the host decides
 * whether to reload/finalize again. Delivery always sends the saved SOAP envelope, never a newly
 * generated fiscal record.
 */
internal class AppAccountingVerifactuAdapter(
    private val store: AppAccountingFiscalStore,
) {
    fun prepareAndPersist(
        finalized: AppAccountingFinalizedRegistration,
        qrEnvironment: QrEnvironment,
    ): AppAccountingPreparationResult {
        val expectedChainState = store.loadChainState(finalized.chainId)
        return when (
            val result =
                FiscalSubmissionPreparation.prepareRegistration(
                    finalized.toRegistrationDraft(expectedChainState),
                    qrEnvironment,
                )
        ) {
            is RegistrationPreparationResult.Invalid -> AppAccountingPreparationResult.Invalid(result)
            is RegistrationPreparationResult.Prepared -> {
                val registration =
                    AppAccountingPersistedRegistration(
                        applicationInvoiceId = finalized.applicationInvoiceId,
                        chainId = finalized.chainId,
                        record = result.record,
                        nextChainState = result.nextChainState,
                        soapEnvelope = result.soapEnvelope,
                    )
                if (store.persistRegistrationAndAdvanceChain(expectedChainState, registration)) {
                    AppAccountingPreparationResult.Persisted(registration)
                } else {
                    AppAccountingPreparationResult.ChainConflict
                }
            }
        }
    }

    fun submitPersisted(
        registration: AppAccountingPersistedRegistration,
        endpoint: AeatSubmissionEndpoint,
        transport: AeatTransportAdapter,
    ): ExampleAttemptOutcome {
        val attemptId = store.beginAttempt(registration.applicationInvoiceId, registration.soapEnvelope)
        val transportResult = transport.execute(AeatTransportRequest(endpoint, registration.soapEnvelope))
        val outcome = interpretExampleAttempt(listOf(SubmissionRecord.Registration(registration.record)), transportResult)
        store.completeAttempt(attemptId, outcome)
        return outcome
    }
}
