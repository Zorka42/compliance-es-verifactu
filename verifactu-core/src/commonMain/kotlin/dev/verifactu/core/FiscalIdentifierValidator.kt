package dev.verifactu.core

import kotlin.jvm.JvmStatic

/**
 * Deterministic business checks for fiscal identifiers, separate from structural value parsing.
 * These checks do not establish census registration or ownership. DNI/NIE check characters are
 * verified; other Spanish NIF forms expose a warning when only their format can be checked.
 */
public object FiscalIdentifierValidator {
    /** Checks the invoice-number character repertoire in AEAT validation catalogue 1.2.2, section 3.1.3.1. */
    @JvmStatic
    public fun validateInvoiceNumber(value: InvoiceNumber): ValidationReport =
        identifierReport(
            if (value.value.any { it.code !in 32..126 || it in FORBIDDEN_INVOICE_CHARACTERS }) {
                listOf(identifierIssue("1130", "number", "The invoice number contains a character prohibited by AEAT.", "3.1.3.1"))
            } else {
                emptyList()
            },
        )

    /** Checks Spanish NIF format and the officially documented DNI/NIE check-character algorithm. */
    @JvmStatic
    public fun validateTaxIdentifier(value: TaxIdentifier): ValidationReport =
        identifierReport(spanishIdentifierIssues(value.value, "taxIdentifier", "1123"))

    /**
     * Checks the identifier's business format. [effectiveDate] is FechaOperacion when supplied,
     * otherwise FechaExpedicionFactura, and determines the GB/XI VAT-prefix transition.
     * Party-name, country-enumeration and third-party-role constraints are checked by the record validator.
     */
    @JvmStatic
    public fun validatePartyIdentifier(
        value: FiscalPartyIdentifier,
        effectiveDate: InvoiceIssueDate,
    ): ValidationReport =
        identifierReport(
            when (value) {
                is FiscalPartyIdentifier.SpanishNif -> spanishIdentifierIssues(value.value.value, "identifier.value", "1123")
                is FiscalPartyIdentifier.Other -> otherIdentifierIssues(value, effectiveDate)
            },
        )
}

private fun otherIdentifierIssues(
    identifier: FiscalPartyIdentifier.Other,
    effectiveDate: InvoiceIssueDate,
): List<ValidationIssue> =
    when (identifier.type) {
        OtherPartyIdentifierType.VAT_IDENTIFIER -> vatIdentifierIssues(identifier, effectiveDate)
        OtherPartyIdentifierType.NOT_REGISTERED -> notRegisteredIdentifierIssues(identifier.value)
        else -> emptyList()
    }

private fun notRegisteredIdentifierIssues(value: String): List<ValidationIssue> =
    if (nifKind(value) == NifKind.ENTITY) {
        listOf(identifierIssue("1131", "identifier.value", "Not-registered identification requires a natural-person NIF.", "3.1.3.13"))
    } else {
        spanishIdentifierIssues(value, "identifier.value", "1290")
    }

private fun spanishIdentifierIssues(
    value: String,
    path: String,
    errorCode: String,
): List<ValidationIssue> =
    when (nifKind(value)) {
        NifKind.UNKNOWN ->
            listOf(identifierIssue(errorCode, path, "The Spanish NIF format is invalid.", "errores.properties $errorCode"))
        NifKind.DNI, NifKind.NIE ->
            if (hasCorrectPersonalCheckCharacter(value)) {
                emptyList()
            } else {
                listOf(
                    ValidationIssue(
                        "VF-ID-$errorCode",
                        path,
                        ValidationSeverity.ERROR,
                        "The DNI/NIE check character does not match its numeric component.",
                        aeatCode = errorCode,
                        source = PERSONAL_CHECK_SOURCE,
                    ),
                )
            }
        NifKind.ASSIGNED_PERSON, NifKind.ENTITY ->
            listOf(
                ValidationIssue(
                    "VF-ID-CHECKSUM-UNVERIFIED",
                    path,
                    ValidationSeverity.WARNING,
                    "The NIF format was checked; its check character has not been verified locally.",
                    source = if (nifKind(value) == NifKind.ENTITY) ENTITY_NIF_SOURCE else ASSIGNED_NIF_SOURCE,
                ),
            )
    }

private enum class NifKind { DNI, NIE, ASSIGNED_PERSON, ENTITY, UNKNOWN }

private fun nifKind(value: String): NifKind =
    when {
        DNI_PATTERN.matches(value) -> NifKind.DNI
        NIE_PATTERN.matches(value) -> NifKind.NIE
        ASSIGNED_PERSON_PATTERN.matches(value) -> NifKind.ASSIGNED_PERSON
        ENTITY_PATTERN.matches(value) -> NifKind.ENTITY
        else -> NifKind.UNKNOWN
    }

private fun hasCorrectPersonalCheckCharacter(value: String): Boolean {
    val number =
        when (value.first()) {
            'X' -> "0" + value.substring(1, 8)
            'Y' -> "1" + value.substring(1, 8)
            'Z' -> "2" + value.substring(1, 8)
            else -> value.substring(0, 8)
        }.toInt()
    return value.last() == PERSONAL_CHECK_CHARACTERS[number % 23]
}

private fun vatIdentifierIssues(
    identifier: FiscalPartyIdentifier.Other,
    effectiveDate: InvoiceIssueDate,
): List<ValidationIssue> {
    val prefix = identifier.value.take(2)
    val number = identifier.value.drop(2)
    val pattern = VAT_NUMBER_PATTERNS[prefix]
    val issues = mutableListOf<ValidationIssue>()
    if (pattern == null || !pattern.matches(number)) {
        issues +=
            identifierIssue("1103", "identifier.value", "The VAT identifier does not match the published country format.", "Note (1), p.18")
    }
    if (identifier.countryCode != null && identifier.countryCode != vatCountryCode(prefix)) {
        issues +=
            identifierIssue(
                "1122",
                "identifier.countryCode",
                "The country code and VAT identifier prefix do not agree.",
                "Note (1), p.18; 1122",
            )
    }
    if (identifier.countryCode == vatCountryCode(prefix) && prefix in setOf("EL", "XI")) {
        issues +=
            ValidationIssue(
                "VF-ID-COUNTRY-PREFIX-ASSUMPTION",
                "identifier.countryCode",
                ValidationSeverity.ASSUMPTION,
                "The country-to-VAT-prefix mapping is inferred from the published lists and requires interoperability verification.",
                source =
                    ComplianceSourceReference(
                        "AEAT validation catalogue and SuministroInformacion.xsd",
                        "Note (1), p.18; CountryType2; errores.properties 1122",
                        "1.2.2; tikeV1.0",
                    ),
            )
    }
    britishVatPrefixIssue(prefix, effectiveDate)?.let { issues += it }
    return issues
}

private fun vatCountryCode(prefix: String): String =
    when (prefix) {
        "EL" -> "GR"
        "XI" -> "GB"
        else -> prefix
    }

private fun britishVatPrefixIssue(
    prefix: String,
    date: InvoiceIssueDate,
): ValidationIssue? {
    val sortableDate = date.value.substring(6, 10) + date.value.substring(3, 5) + date.value.substring(0, 2)
    val errorCode =
        when {
            prefix == "GB" && sortableDate >= "20210201" -> "1255"
            prefix == "XI" && sortableDate < "20210101" -> "1254"
            else -> return null
        }
    return identifierIssue(
        errorCode,
        "identifier.value",
        "The GB/XI VAT prefix is not permitted on the effective operation date.",
        "Note (1), BREXIT, p.18",
    )
}

private fun identifierIssue(
    code: String,
    path: String,
    message: String,
    section: String,
): ValidationIssue =
    ValidationIssue(
        "VF-ID-$code",
        path,
        ValidationSeverity.ERROR,
        message,
        aeatCode = code,
        source = ComplianceSourceReference("AEAT validation and error catalogue", section, "1.2.2 (2026-04-08)"),
    )

private fun identifierReport(issues: List<ValidationIssue>): ValidationReport = ValidationReport(IdentifierIssueSnapshot(issues))

private class IdentifierIssueSnapshot(
    values: List<ValidationIssue>,
) : AbstractList<ValidationIssue>() {
    private val entries = values.toList()
    override val size: Int get() = entries.size

    override fun get(index: Int): ValidationIssue = entries[index]
}

private val FORBIDDEN_INVOICE_CHARACTERS: Set<Char> = setOf('"', '\'', '<', '=', '>')
private val DNI_PATTERN: Regex = Regex("[0-9]{8}[A-Z]")
private val NIE_PATTERN: Regex = Regex("[XYZ][0-9]{7}[A-Z]")
private val ASSIGNED_PERSON_PATTERN: Regex = Regex("[KLM][A-Z0-9]{7}[A-Z]")
private val ENTITY_PATTERN: Regex = Regex("[ABCDEFGHJNPQRSUVW][0-9]{7}[A-Z0-9]")
private const val PERSONAL_CHECK_CHARACTERS: String = "TRWAGMYFPDXBNJZSQVHLCKE"

private val PERSONAL_CHECK_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference(
        "Ministerio del Interior: Calculo del digito de control del NIF/NIE",
        "DNI modulo 23; NIE X/Y/Z",
        "retrieved 2026-09-11",
    )
private val ASSIGNED_NIF_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference("BOE Real Decreto 1065/2007", "Articles 19.2 and 20.2", "consolidated 2025-04-02")
private val ENTITY_NIF_SOURCE: ComplianceSourceReference =
    ComplianceSourceReference("BOE Orden EHA/451/2008", "Articles 2-5", "consolidated 2016-01-15")

// Exact length/character classes in validation catalogue 1.2.2, Note (1), page 18.
private val VAT_NUMBER_PATTERNS: Map<String, Regex> =
    mapOf(
        "DE" to Regex("[0-9]{9}"),
        "AT" to Regex("[A-Z0-9]{9}"),
        "BE" to Regex("[0-9]{10}"),
        "CY" to Regex("[A-Z0-9]{9}"),
        "CZ" to Regex("[0-9]{8,10}"),
        "HR" to Regex("[0-9]{11}"),
        "DK" to Regex("[0-9]{8}"),
        "SK" to Regex("[0-9]{10}"),
        "SI" to Regex("[0-9]{8}"),
        "EE" to Regex("[0-9]{9}"),
        "FI" to Regex("[0-9]{8}"),
        "FR" to Regex("[A-Z0-9]{11}"),
        "EL" to Regex("[0-9]{9}"),
        "GB" to Regex("(?:[A-Z0-9]{5}|[A-Z0-9]{9}|[A-Z0-9]{12})"),
        "XI" to Regex("(?:[A-Z0-9]{5}|[A-Z0-9]{9}|[A-Z0-9]{12})"),
        "NL" to Regex("[A-Z0-9]{12}"),
        "HU" to Regex("[0-9]{8}"),
        "IT" to Regex("[0-9]{11}"),
        "IE" to Regex("[A-Z0-9]{8,9}"),
        "LV" to Regex("[0-9]{11}"),
        "LT" to Regex("(?:[0-9]{9}|[0-9]{12})"),
        "LU" to Regex("[0-9]{8}"),
        "MT" to Regex("[0-9]{8}"),
        "PL" to Regex("[0-9]{10}"),
        "PT" to Regex("[0-9]{9}"),
        "SE" to Regex("[0-9]{12}"),
        "BG" to Regex("[0-9]{9,10}"),
        "RO" to Regex("[1-9][0-9]{1,9}"),
    )
