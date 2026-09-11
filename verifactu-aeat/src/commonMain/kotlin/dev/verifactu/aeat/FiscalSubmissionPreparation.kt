package dev.verifactu.aeat

import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalRecordFactory
import dev.verifactu.core.RecordCreationResult
import dev.verifactu.core.RegistroAlta
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.core.RegistroAnulacion
import dev.verifactu.core.RegistroAnulacionDraft
import dev.verifactu.core.ValidationReport
import dev.verifactu.qr.QrEnvironment
import dev.verifactu.qr.QrPayload
import dev.verifactu.qr.QrPayloadBuilder
import dev.verifactu.qr.QrPayloadInput
import dev.verifactu.qr.QrPayloadResult
import dev.verifactu.xml.RegistroXmlSerializer
import dev.verifactu.xml.SubmissionBatchBuildResult
import dev.verifactu.xml.SubmissionBatchBuilder
import dev.verifactu.xml.SubmissionHeader
import dev.verifactu.xml.SubmissionRecord
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/** Local outcome of preparing a registration record and its associated artifacts. */
public sealed interface RegistrationPreparationResult {
    /**
     * A record snapshot, its caller-owned next chain state, and exact XML/QR artifacts.
     * The XML strings and QR contain fiscal data; diagnostics redact this result.
     * Preparation alone does not persist a record or establish delivery to AEAT.
     */
    public data class Prepared
        @JvmOverloads
        constructor(
            public val record: RegistroAlta,
            public val nextChainState: ChainState.PreviousRecord,
            public val recordXml: String,
            public val qr: QrPayload,
            public val batchXml: String,
            public val soapEnvelope: String,
            /** Local record-validation evidence, with paths relative to the supplied draft. */
            public val report: ValidationReport = ValidationReport(emptyList()),
        ) : RegistrationPreparationResult {
            override fun toString(): String = "RegistrationPreparationResult.Prepared(<redacted>)"
        }

    /** Structured issues returned by record, QR, or batch validation. */
    public data class Invalid(
        public val report: ValidationReport,
    ) : RegistrationPreparationResult
}

/** Local outcome of preparing a cancellation record and its submission artifacts. */
public sealed interface CancellationPreparationResult {
    /**
     * A cancellation record snapshot, its caller-owned next chain state, and exact XML artifacts.
     * Cancellation does not remove the original invoice. Diagnostics redact this result.
     */
    public data class Prepared
        @JvmOverloads
        constructor(
            public val record: RegistroAnulacion,
            public val nextChainState: ChainState.PreviousRecord,
            public val recordXml: String,
            public val batchXml: String,
            public val soapEnvelope: String,
            /** Local record-validation evidence, with paths relative to the supplied draft. */
            public val report: ValidationReport = ValidationReport(emptyList()),
        ) : CancellationPreparationResult {
            override fun toString(): String = "CancellationPreparationResult.Prepared(<redacted>)"
        }

    /** Structured issues returned by record or batch validation. */
    public data class Invalid(
        public val report: ValidationReport,
    ) : CancellationPreparationResult
}

/**
 * Pure preparation entry points composing record creation, XML, QR, and validated SOAP artifacts.
 *
 * The host must serialize work per chain and atomically persist the prepared record with its
 * next chain state before sending. A retry uses the persisted request bytes and does not call
 * preparation again. Unknown delivery requires the host's reconciliation/retry decision.
 *
 * No method reads a clock, persists state, sends a request, accesses credentials, or schedules work.
 * Preparation applies only the currently implemented local rules; it does not guarantee AEAT acceptance.
 */
public object FiscalSubmissionPreparation {
    /** Prepares a registration, deriving its batch header from the validated issuer data. */
    @JvmStatic
    public fun prepareRegistration(
        draft: RegistroAltaDraft,
        qrEnvironment: QrEnvironment,
    ): RegistrationPreparationResult {
        val created =
            when (val result = FiscalRecordFactory.createRegistration(draft)) {
                is RecordCreationResult.Created -> result
                is RecordCreationResult.Invalid -> return RegistrationPreparationResult.Invalid(result.report)
            }
        val snapshot = created.record.draft
        val qr =
            when (val result = QrPayloadBuilder.build(QrPayloadInput(snapshot.invoice, snapshot.totalAmount, qrEnvironment))) {
                is QrPayloadResult.Created -> result.payload
                is QrPayloadResult.Invalid -> return RegistrationPreparationResult.Invalid(result.report)
            }
        val batch =
            when (
                val result =
                    SubmissionBatchBuilder.build(
                        SubmissionHeader(snapshot.issuerName, snapshot.invoice.issuer),
                        listOf(SubmissionRecord.Registration(created.record)),
                    )
            ) {
                is SubmissionBatchBuildResult.Created -> result
                is SubmissionBatchBuildResult.Invalid -> return RegistrationPreparationResult.Invalid(result.report)
            }
        return RegistrationPreparationResult.Prepared(
            created.record,
            created.nextChainState,
            RegistroXmlSerializer.serialize(created.record),
            qr,
            batch.xml,
            batch.soapEnvelope,
            created.report,
        )
    }

    /** Prepares a cancellation using the explicitly supplied issuer header. */
    @JvmStatic
    public fun prepareCancellation(
        draft: RegistroAnulacionDraft,
        header: SubmissionHeader,
    ): CancellationPreparationResult {
        val created =
            when (val result = FiscalRecordFactory.createCancellation(draft)) {
                is RecordCreationResult.Created -> result
                is RecordCreationResult.Invalid -> return CancellationPreparationResult.Invalid(result.report)
            }
        val batch =
            when (val result = SubmissionBatchBuilder.build(header, listOf(SubmissionRecord.Cancellation(created.record)))) {
                is SubmissionBatchBuildResult.Created -> result
                is SubmissionBatchBuildResult.Invalid -> return CancellationPreparationResult.Invalid(result.report)
            }
        return CancellationPreparationResult.Prepared(
            created.record,
            created.nextChainState,
            RegistroXmlSerializer.serialize(created.record),
            batch.xml,
            batch.soapEnvelope,
            created.report,
        )
    }

    /** Prepares an ordered batch of existing records without advancing or regenerating a chain. */
    @JvmStatic
    public fun prepareBatch(
        header: SubmissionHeader,
        records: List<SubmissionRecord>,
    ): SubmissionBatchBuildResult = SubmissionBatchBuilder.build(header, records)
}
