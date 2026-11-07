package dev.verifactu.tools.sandbox

import dev.verifactu.core.ChainState
import dev.verifactu.core.RecordHashCalculator
import dev.verifactu.core.RegistrationHashInput
import dev.verifactu.core.RegistroAlta
import dev.verifactu.core.RegistroAltaDraft
import dev.verifactu.core.RegistroAltaValidator
import dev.verifactu.core.ValidationSeverity
import dev.verifactu.xml.RegistroXmlSerializer
import dev.verifactu.xml.SubmissionBatchXmlSerializer
import dev.verifactu.xml.SubmissionHeader
import dev.verifactu.xml.SubmissionRecord

// Only the explicit legacy negative fixture may represent this known-invalid registration.
// Normal preparation continues through FiscalSubmissionPreparation and rejects it.
internal fun buildPreCutoffFixture(draft: RegistroAltaDraft): SandboxPlan {
    sandboxRequire(draft.invoice.issueDate.value == "27-10-2024", "The negative fixture requires 27-10-2024.")
    val report = RegistroAltaValidator.validate(draft)
    val errors = report.issues.filter { it.severity == ValidationSeverity.ERROR }
    sandboxRequire(
        errors.size == 1 && errors.single().code == "VF-DATE-1152" && errors.single().fieldPath == "invoice.issueDate",
        "The negative fixture must violate only the documented issue-date cutoff.",
    )
    val hash =
        RecordHashCalculator.sha256(
            RegistrationHashInput(
                draft.invoice.issuer.value,
                draft.invoice.number.value,
                draft.invoice.issueDate.value,
                draft.invoiceType.name,
                draft.totalTax.value,
                draft.totalAmount.value,
                (draft.chainState as? ChainState.PreviousRecord)?.hash,
                draft.generatedAt.value,
            ).canonicalString(),
        )
    val record = RegistroAlta(draft, hash)
    val wrapped = SubmissionRecord.Registration(record)
    val xml = SubmissionBatchXmlSerializer.serialize(SubmissionHeader(draft.issuerName, draft.invoice.issuer), listOf(wrapped))
    validateBatchSchema(xml)
    val body = xml.removePrefix("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    val soap =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" +
            body + "</soap:Body></soap:Envelope>"
    return SandboxPlan(wrapped, ChainState.PreviousRecord(draft.invoice, hash), RegistroXmlSerializer.serialize(record), soap, report)
}
