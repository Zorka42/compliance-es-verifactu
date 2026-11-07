package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.FiscalSubmissionPreparation
import dev.verifactu.core.ChainState
import dev.verifactu.core.FiscalRecordFactory
import dev.verifactu.core.InvoiceIdentifier
import dev.verifactu.core.InvoiceNumber
import dev.verifactu.core.RecordCreationResult
import dev.verifactu.core.RecordGenerationTimestamp
import dev.verifactu.xml.SubmissionBatchBuildResult
import dev.verifactu.xml.SubmissionHeader
import dev.verifactu.xml.SubmissionRecord

internal fun buildPaginationPlan(values: Map<String, String>): SandboxPlan {
    val count = required(values, "batch.count").toIntOrNull()
    val start = required(values, "batch.start").toIntOrNull()
    sandboxRequire(count != null && count in 1..1000, "Pagination batch.count must be 1..1000.")
    sandboxRequire(start != null && start in 1..10001 && start.toLong() + count!! <= 10002, "Pagination range must stay within 1..10001.")
    val base = invoice(values)
    val metadata = system(values)
    val timestamp = value(RecordGenerationTimestamp.parse(required(values, "generated.at")))
    var chain: ChainState =
        if (values.keys.any { it in PREVIOUS_KEYS }) {
            ChainState.PreviousRecord(invoice(values, "previous"), required(values, "previous.hash"))
        } else {
            ChainState.FirstRecord
        }
    val records =
        (start!! until start + count!!).map { index ->
            val identifier =
                InvoiceIdentifier(
                    base.issuer,
                    value(InvoiceNumber.parse("${base.number.value}-${index.toString().padStart(5, '0')}")),
                    base.issueDate,
                )
            val created = FiscalRecordFactory.createRegistration(registrationDraft(values, identifier, chain, metadata, timestamp))
            if (created is RecordCreationResult.Invalid) invalidPlan(created.report)
            created as RecordCreationResult.Created
            chain = created.nextChainState
            SubmissionRecord.Registration(created.record)
        }
    val batch = FiscalSubmissionPreparation.prepareBatch(SubmissionHeader(required(values, "issuer.name"), base.issuer), records)
    if (batch is SubmissionBatchBuildResult.Invalid) invalidPlan(batch.report)
    batch as SubmissionBatchBuildResult.Created
    validateBatchSchema(batch.xml)
    return SandboxPlan(records.first(), chain as ChainState.PreviousRecord, batch.xml, batch.soapEnvelope, batch.report, records = records)
}
