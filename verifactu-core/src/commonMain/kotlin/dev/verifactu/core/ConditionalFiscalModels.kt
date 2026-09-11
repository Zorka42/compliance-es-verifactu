package dev.verifactu.core

/** The two AEAT values for the `Subsanacion` field on a registration record. */
public enum class Subsanation(
    public val xmlValue: String,
) {
    /** The registration corrects eligible data from a prior registration. */
    YES("S"),

    /** The registration is not marked as a subsanation. */
    NO("N"),
}

/** The permitted prior-rejection states for a registration record. */
public enum class RegistrationPreviousRejection(
    public val xmlValue: String,
) {
    /** AEAT has not rejected a previous submission of this registration. */
    NO("N"),

    /** AEAT rejected a previous subsanation of this registration. */
    YES("S"),

    /** The prior registration does not exist at AEAT. */
    NOT_PRESENT_AT_AEAT("X"),
}

/** How a rectifying invoice reports the corrected values. */
public enum class RectificationType(
    public val xmlValue: String,
) {
    /** The registration replaces the prior values. */
    REPLACEMENT("S"),

    /** The registration reports the difference from the prior values. */
    BY_DIFFERENCE("I"),
}

/** Prior values reported by a replacement rectifying invoice. */
public data class RectificationAmounts(
    public val taxableBase: FiscalAmount,
    public val chargedTax: FiscalAmount,
    public val equivalenceSurcharge: FiscalAmount? = null,
)

/** Conditional data required only for a rectifying invoice type. */
public data class InvoiceRectification(
    public val type: RectificationType,
    public val rectifiedInvoices: List<InvoiceIdentifier> = emptyList(),
    public val replacedAmounts: RectificationAmounts? = null,
)

/** The source of a party identifier in AEAT record XML. */
public sealed interface FiscalPartyIdentifier {
    /** A Spanish NIF identifier. */
    public data class SpanishNif(
        public val value: TaxIdentifier,
    ) : FiscalPartyIdentifier

    /** An identifier other than a Spanish NIF. */
    public data class Other(
        public val countryCode: String?,
        public val type: OtherPartyIdentifierType,
        public val value: String,
    ) : FiscalPartyIdentifier
}

/** Values allowed for `IDOtro/IDType` in the AEAT v1.0 schema. */
public enum class OtherPartyIdentifierType(
    public val xmlValue: String,
) {
    VAT_IDENTIFIER("02"),
    PASSPORT("03"),
    RESIDENCE_COUNTRY_IDENTIFIER("04"),
    RESIDENCE_CERTIFICATE("05"),
    OTHER_PROOF("06"),
    NOT_REGISTERED("07"),
}

/** A recipient or third party identified for a fiscal registration. */
public data class FiscalParty(
    public val name: String,
    public val identifier: FiscalPartyIdentifier,
)

/** Whether a full invoice is a qualified simplified invoice under articles 7.2 and 7.3. */
public enum class SimplifiedInvoiceQualification(
    public val xmlValue: String,
) {
    YES("S"),
    NO("N"),
}

/** Whether a simplified invoice omits the recipient identifier under article 6.1.d. */
public enum class RecipientIdentificationExemption(
    public val xmlValue: String,
) {
    YES("S"),
    NO("N"),
}

/** Whether the record must carry AEAT's macrodato indicator. */
public enum class MacroDataIndicator(
    public val xmlValue: String,
) {
    YES("S"),
    NO("N"),
}

/** The party that generated the invoice or registration on behalf of the issuer. */
public enum class InvoiceGeneratedBy(
    public val xmlValue: String,
) {
    THIRD_PARTY("T"),
    RECIPIENT("D"),
}

/** Whether the record carries the AEAT coupon indicator. */
public enum class CouponIndicator(
    public val xmlValue: String,
) {
    YES("S"),
    NO("N"),
}

/**
 * Optional registration data whose legal applicability is determined by the invoice type or
 * another registration field. The validator reports the implemented structural and conditional rules.
 */
public data class RegistrationConditionalData(
    public val externalReference: String? = null,
    public val subsanation: Subsanation? = null,
    public val previousRejection: RegistrationPreviousRejection? = null,
    public val rectification: InvoiceRectification? = null,
    public val replacedInvoices: List<InvoiceIdentifier> = emptyList(),
    public val operationDate: InvoiceIssueDate? = null,
    public val simplifiedInvoiceQualification: SimplifiedInvoiceQualification? = null,
    public val recipientIdentificationExemption: RecipientIdentificationExemption? = null,
    public val macroData: MacroDataIndicator? = null,
    public val generatedBy: InvoiceGeneratedBy? = null,
    public val thirdParty: FiscalParty? = null,
    public val recipients: List<FiscalParty> = emptyList(),
    public val coupon: CouponIndicator? = null,
    public val taxationAgreementRegistrationNumber: String? = null,
    public val systemAgreementIdentifier: String? = null,
)
