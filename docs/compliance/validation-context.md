# Explicit Validation Context

`ValidationContext` carries facts supplied by the host application for rules that cannot be decided from the fiscal record alone. Its current field is `aeatReceiptDate`. A null date means unknown. The library does not obtain that date, establish delivery, read a clock, or substitute `generatedAt` for AEAT's receipt date.

```kotlin
val receiptDate = (InvoiceIssueDate.parse("01-01-2027") as ValueResult.Valid).value
val context = ValidationContext(aeatReceiptDate = receiptDate)
val report = RegistroAltaValidator.validate(draft, context)
val creation = FiscalRecordFactory.createRegistration(draft, context)
```

The date uses the existing `DD-MM-YYYY` calendar value type. Its name in the context gives it its receipt-date meaning. The caller must establish which date is appropriate for the actual AEAT receipt; a planned sending date or the caller's local date is not evidence of receipt. Supplying a hypothetical date is useful for deterministic preflight scenarios, but the result is conditional on that input. Context is validation input and does not change record fields, hashes or XML.

Existing calls without a context retain the explicit unknown-context warning where relevant. Callers must inspect issues even when `ValidationReport.isValid` is true: that property means there are no local error-severity issues, rather than proof that AEAT will accept the record. Factory creation preserves the report, including warnings; submission preparation carries those issues forward.

## IPSI regime transition

The archived [AEAT validation specification](../../schemas-aeat/Validaciones_Errores_Veri-Factu.pdf), version 1.2.2 dated 2026-04-08, §3.1.3.15.6, requires IPSI regime 01, 08, 11, 18, 19 or 20. It specifies warning treatment through 2026-12-31 and rejection from 2027-01-01 for a missing or incompatible IPSI regime. The implementation changes the existing `VF-TAX-IPSI-TRANSITION` issue using the caller-supplied receipt date:

| Context and IPSI regime | Local severity | Associated published AEAT code |
| --- | --- | --- |
| Receipt date unknown; regime missing or incompatible | Warning describing unavailable context | None |
| Receipt date through 2026-12-31; regime missing | Warning | 2009 |
| Receipt date through 2026-12-31; regime present but incompatible | Warning | None: no specific warning code is established by the archived sources |
| Receipt date from 2027-01-01; regime missing | Error | 1245 |
| Receipt date from 2027-01-01; regime present but incompatible | Error | 1246 |
| Permitted IPSI regime | No transition issue | None |

The code associations come from the archived [`errores.properties`](../../schemas-aeat/errores.properties). Error 2009 specifically describes a missing IPSI regime; it is not assigned to every incompatible value. Independent errors remain errors, including values outside the XSD enumeration. The transition does not waive schema checks or establish an AEAT response outcome.

`TaxConditionalValidationTest` checks the 2026-12-31 / 2027-01-01 boundary, absent and incompatible regimes, unrelated issues, and generation timestamps on both sides of the boundary. Factory, batch and facade tests verify propagation. These checks use synthetic records and supplied dates, with no network, certificates or clock access.

## Facts the context does not establish

The following checks require additional evidence or a published rule that this context does not provide:

| Check | Archived source | Remaining boundary |
| --- | --- | --- |
| Issue and operation dates relative to AEAT's current date | Validation specification §3.1.3.1 and §3.1.3.7 | The receipt-date field currently resolves the IPSI transition only. It does not silently enable other current-date validations. |
| Generation timestamp relative to AEAT's system time | §3.1.3.20; `errores.properties` 2004 | The PDF mentions a tolerance without specifying its numeric value; the archived message ends after introducing that tolerance. No tolerance or substitute clock is invented. |
| Taxpayer identification in the relevant census | §3.1.1; §3.1.3.4, .5, .12, .13, .15.6.9 | Locally valid identifier structure or a NIF prefix does not establish census status. |
| Prior registration, prior rejection, first-record status and permitted correction/cancellation history | §4.3 and Annex 6; errors including 2007 and 3000 | Caller-owned chain state is not proof of AEAT's stored history. No history store, remote lookup or boolean “verified” bypass is introduced. |
| Existence of a billing or system agreement | §3.1.3.21 and §3.1.3.22 | A supplied agreement identifier does not establish that AEAT has registered it. |

The host remains responsible for obtaining and retaining evidence where its workflow requires it. This library neither contacts an authenticated service nor certifies those facts. See [integration responsibilities](../integration-responsibilities.md) and [query access](query-access.md).

## Unresolved zero-rate IVA surcharge rule

The published sources do not establish one unambiguous local rule for a zero-rate IVA operation carrying equivalence-surcharge fields from 2024-10-01:

- Validation specification v1.2.2 §3.1.3.15.3 documents zero surcharge for zero-rate IVA within 2023-01-01 through 2024-09-30, without an explicit later-period rule in that subsection.
- Archived error 1165 describes that earlier interval. Error 1170 specifies a 0.26 surcharge from 2024-10-01 for a zero IVA rate. Error 1277 is a generic zero-rate surcharge error and does not resolve the difference.

Source priority gives the technical specification precedence over error-message wording. It does not supply a missing rule for the later period or establish that the message is a typographical error. The library therefore retains its own `VF-TAX-ZERO-RATE-UNRESOLVED` warning, without an asserted AEAT code. Supplying a receipt date does not resolve this discrepancy: the tax-rate rules refer to the operation date, falling back to the invoice issue date.

This remains a release limitation requiring an official clarification or corrected public source before claiming validation coverage for that combination. The warning is not evidence that either zero or 0.26 surcharge will be accepted. No live AEAT experiment was performed. The source versions and byte digests are recorded in the [archive manifest](../../schemas-aeat/manifest.tsv).

## Arithmetic interpretation boundaries

The aggregate checks in specification §3.1.3.16 and §3.1.3.17 exclude regimes 03, 05, 06, 08 and 09, without spelling out a separate mixed-detail algorithm. Local validation skips the aggregate comparison if any detail has an excluded regime. This is the library's documented interpretation: a partial sum of eligible details cannot establish the correctness of the whole invoice total. It is not a claim about AEAT's internal implementation. Independent detail checks remain applicable.

For the same-sign check in §3.1.3.15.7, zero is treated as neutral; opposite nonzero signs are rejected. This is consistent with §3.1.3.15.1 permitting a zero tax rate and with the §3.1.3.15.7 calculation yielding zero tax on a nonzero base at that rate. The local validator does not introduce an additional rejection of ordinary zero-tax results.
