package dev.verifactu.core

internal fun registrationIdentifierIssues(draft: RegistroAltaDraft): List<ValidationIssue> =
    buildList {
        addAll(invoiceIdentifierIssues(draft.invoice, "invoice"))
        addAll(systemIdentifierIssues(draft.system))
        addAll(previousIdentifierIssues(draft.chainState))
        val conditional = draft.conditionalData
        val effectiveDate = conditional.operationDate ?: draft.invoice.issueDate
        conditional.recipients.forEachIndexed { index, party ->
            addAll(partyIdentifierIssues(party.identifier, effectiveDate, "conditionalData.recipients[$index]"))
        }
        conditional.thirdParty?.let {
            addAll(partyIdentifierIssues(it.identifier, effectiveDate, "conditionalData.thirdParty"))
        }
        conditional.replacedInvoices.forEachIndexed { index, invoice ->
            addAll(invoiceIdentifierIssues(invoice, "conditionalData.replacedInvoices[$index]"))
        }
        conditional.rectification?.rectifiedInvoices?.forEachIndexed { index, invoice ->
            addAll(invoiceIdentifierIssues(invoice, "conditionalData.rectification.rectifiedInvoices[$index]"))
        }
    }

internal fun cancellationIdentifierIssues(draft: RegistroAnulacionDraft): List<ValidationIssue> =
    invoiceIdentifierIssues(draft.cancelledInvoice, "cancelledInvoice") +
        systemIdentifierIssues(draft.system) + previousIdentifierIssues(draft.chainState)

private fun invoiceIdentifierIssues(
    invoice: InvoiceIdentifier,
    path: String,
): List<ValidationIssue> =
    FiscalIdentifierValidator.validateTaxIdentifier(invoice.issuer).issues.map { it.copy(fieldPath = "$path.issuer") } +
        FiscalIdentifierValidator.validateInvoiceNumber(invoice.number).issues.map { it.copy(fieldPath = "$path.number") }

private fun systemIdentifierIssues(system: SistemaInformatico): List<ValidationIssue> =
    FiscalIdentifierValidator.validateTaxIdentifier(system.producerTaxIdentifier).issues.map {
        it.copy(fieldPath = "system.producerTaxIdentifier")
    }

private fun previousIdentifierIssues(chainState: ChainState): List<ValidationIssue> =
    when (chainState) {
        ChainState.FirstRecord -> emptyList()
        is ChainState.PreviousRecord -> invoiceIdentifierIssues(chainState.invoice, "chainState.invoice")
    }

private fun partyIdentifierIssues(
    identifier: FiscalPartyIdentifier,
    effectiveDate: InvoiceIssueDate,
    path: String,
): List<ValidationIssue> =
    FiscalIdentifierValidator.validatePartyIdentifier(identifier, effectiveDate).issues.map {
        it.copy(fieldPath = "$path.${it.fieldPath}")
    }
