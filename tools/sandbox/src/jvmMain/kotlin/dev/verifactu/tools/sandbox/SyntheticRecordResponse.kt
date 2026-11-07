package dev.verifactu.tools.sandbox

import dev.verifactu.aeat.AeatOperationType
import dev.verifactu.testkit.AeatResponseFixtures
import dev.verifactu.testkit.AeatResponseScenario
import dev.verifactu.xml.SubmissionRecord

internal fun syntheticRecordResponse(
    record: SubmissionRecord,
    scenario: String?,
    fixture: AeatResponseScenario,
): String {
    val operation = if (record is SubmissionRecord.Registration) AeatOperationType.REGISTRATION else AeatOperationType.CANCELLATION
    val identifier =
        when (record) {
            is SubmissionRecord.Registration -> record.value.draft.invoice
            is SubmissionRecord.Cancellation -> record.value.draft.cancelledInvoice
        }
    var xml = AeatResponseFixtures.response(fixture, identifier, operation).xml
    if (record is SubmissionRecord.Registration &&
        record.value.draft.conditionalData.subsanation == dev.verifactu.core.Subsanation.YES
    ) {
        xml = xml.replace("</Operacion>", "<sf:Subsanacion>S</sf:Subsanacion></Operacion>")
    }
    if (scenario == "warning-first") xml = xml.replace(">2000<", ">2007<")
    if (scenario == "rejection-date") xml = xml.replace(">3002<", ">1152<")
    return xml
}
