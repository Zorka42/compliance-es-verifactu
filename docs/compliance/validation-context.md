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

## Date-rule audit after owner-run TEST verification, 2026-11-07

Source presence, implemented local validation, a synthetic response test and a live server result are separate evidence. `MatrixEvidenceTest.acceptedPreCutoffRecordCannotPassTheRejectionCase` tests the host harness: a matched clean acceptance cannot satisfy an expected rejection or permit subsequent writes. It does **not** implement or prove fiscal date validation.

| Requirement | Exact official source | Local coverage and required work |
| --- | --- | --- |
| A registration's `FechaExpedicionFactura` must be at least 28-10-2024 | Validation v1.2.2 section 3.1.3.1, p.8; `errores.properties` 1152 | **Implemented.** `registrationDateIssues` returns `VF-DATE-1152`, error severity, field `invoice.issueDate`, AEAT code 1152 and the exact source reference. No AEAT date or clock is needed. |
| Issue date must not exceed AEAT's current date | Same section; error 1112 | Requires an explicitly established AEAT date. The current receipt-date context does not enable this check. Never substitute generation time or silently read a clock. |
| Operation date must fall between AEAT's current date minus twenty years and the following year; future operations have additional IVA/IGIC regime restrictions | Validation v1.2.2 section 3.1.3.7, p.9 | Current-date bounds are not implemented; contextual inputs and exact inclusive boundaries need explicit requirements and tests. |
| An operation after issue requires regime 14/15 for IVA/IGIC details | Validation v1.2.2 section 3.1.3.1 | Implemented by `taxOperationDateIssues`; covered by `TaxConditionalValidationTest`. |
| Generation-time comparison with AEAT's system time | Validation v1.2.2 section 3.1.3.20; error 2004 | Remote-time knowledge and a published numeric tolerance are absent. The observed reply's 240 seconds must not become a hardcoded universal rule. The PDF describes an excessive future time; the observed older-time warning also needs clarification. |

The gap was originally reproduced offline: three normal preparations differing only in issue date 27-10-2024, 28-10-2024 and 29-10-2024 all succeeded. The correction now rejects the day before and accepts the boundary and day after. `RegistrationDateValidationTest.registrationDateCutoffIsInclusiveAndHasStructuredSourceEvidence` checks the structured issue and factory outcome; `FiscalSubmissionPreparationTest.cutoffErrorsPropagateThroughPreparationAndBatchRevalidation` checks preparation and batch revalidation. Valid synthetic fixtures and the Kotlin/Java samples now use on-or-after-cutoff dates.

The correction is specific to registration validation. `InvoiceIssueDate.parse` still accepts historical calendar dates for wire parsing, cancellation targets, referenced/previous invoices and official hash vectors. `historicalCalendarReferencesAndCancellationTargetsRemainUsable` verifies those uses. Original official hash vectors and archived contract bytes are unchanged.

Only the host tool's explicit legacy `rejection-date` scenario uses low-level record/hash/XML construction to retain inspection of old prepared bytes. It requires exactly 27-10-2024 and exactly the cutoff error, rejecting any additional local error. Normal factory and submission preparation still reject the date. `MissingCancellationTest.normalPreparationRejectsTheDateAndLegacyNegativeFixturesRemainInspectable` covers both paths. The old live result remains REVIEW; parsing still reports what AEAT actually accepted.

### Public-source recheck and live discrepancy

The public validation PDF, error catalogue, common XSD, service WSDL and developer FAQ were retrieved again with normal HTTPS verification. All five SHA-256 values match [the archived manifest](../../schemas-aeat/manifest.tsv); original bytes were not replaced. The current technical index still links validation v1.2.2. Its page 8 and error 1152 both retain the cutoff. The reviewed developer FAQ v1.3 supplies no cutoff exception explaining this run. This is a bounded source review, not proof that no clarification exists elsewhere.

Private evidence confirms that the exact prepared registration bytes were sent to the fixed TEST `VerifactuSOAP` service. The header has no `RemisionRequerimiento` or `RefRequerimiento`, and the request is an ordinary initial registration, not subsanation. The response accepted the same pre-cutoff date; subsequent consultations in both roles returned it as `Correcto`. The relaxed treatment of business errors documented for submission under an AEAT requirement therefore does not explain the request that was actually sent. Neither a reason for the discrepancy nor production behavior has been established. See the [owner-run observations](../aeat-test-matrix.md).

### Separate missing-cancellation negative scenario

Keep the original date scenario and its REVIEW evidence. A separate candidate is ordinary cancellation of a fresh synthetic invoice that does not exist in TEST, without `SinRegistroPrevio=S` or `RechazoPrevio=S`. The published operation table (validation v1.2.2 Annex 6.2, p.28, ERROR (6)) requires a prior registration for ordinary cancellation; error 3002 means no fiscal record exists. This avoids depending on the disputed date cutoff. It is a source-backed expectation awaiting owner verification, not a guaranteed server result.

The standalone `sandbox prepare --scenario cancellation-missing` now separates the cancellation target from the actual chain tail supplied by `--previous`. It requires a distinct target and matching issuer/system/mode/route metadata, uses normal cancellation validation and XSD checks, and retains the digest/count/native TEST gates. It snapshots generation time once at explicit preparation; later show/send never refresh the bytes. Preparation does not establish absence or remote acceptance of the previous bundle: the owner must reconcile these facts before sending.

`MissingCancellationTest` and the unified CLI `SandboxWorkflowTest` exercise a schema-valid synthetic 3002 rejection, unexpected acceptance, wrong code, non-success HTTP, chain metadata and local bundle replay. Only a matched single-record rejection with code 3002 and known flow control satisfies this scenario. The actual outcome stays `REJECTED`; expected-case PASS is separate. Offline demo/inspection is not live evidence. See [the command workflow](../../tools/sandbox/README.md#negative-test-cancellation-of-an-absent-invoice).

The candidate is separate from the existing matrix. It does not mutate old evidence, relabel the date case, or bypass REVIEW/unknown delivery. A known unexpectedly accepted chain tail may be used only after its acceptance is reconciled from the actual response and consultation. No additional live request was made during implementation.

### Draft technical clarification for AEAT (not sent)

Asunto: PRE VERI*FACTU: aceptación de FechaExpedicionFactura anterior al 28/10/2024

En el entorno de pruebas, en el servicio `https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP`, se remitió un alta inicial F1 sintética con `FechaExpedicionFactura=27-10-2024`. No se informó `RemisionRequerimiento`, `RefRequerimiento` ni `Subsanacion`. La respuesta fue `EstadoEnvio=Correcto` y `EstadoRegistro=Correcto`, sin código de error. Posteriormente, las consultas de emisor y destinatario para octubre de 2024 devolvieron el mismo registro como `Correcto`.

El documento de validaciones v1.2.2, apartado 3.1.3.1, página 8, y el código 1152 publicado indican que la fecha de expedición no puede ser anterior al 28/10/2024. ¿Esta validación está desactivada o tiene una excepción en PRE? ¿Existe alguna condición de aplicación o actualización oficial que debamos considerar? ¿Qué comportamiento documentado debe aplicar un productor al validar localmente nuevas altas? Podemos facilitar evidencias privadas mediante el canal adecuado, una vez autorizadas por su titular.

This draft contains no taxpayer, producer, invoice or certificate identifiers. Sending or attaching private evidence requires the owner's explicit authorization.

## Unresolved zero-rate IVA surcharge rule

The published sources do not establish one unambiguous local rule for a zero-rate IVA operation carrying equivalence-surcharge fields from 2024-10-01:

- Validation specification v1.2.2 §3.1.3.15.3 documents zero surcharge for zero-rate IVA within 2023-01-01 through 2024-09-30, without an explicit later-period rule in that subsection.
- Archived error 1165 describes that earlier interval. Error 1170 specifies a 0.26 surcharge from 2024-10-01 for a zero IVA rate. Error 1277 is a generic zero-rate surcharge error and does not resolve the difference.

Source priority gives the technical specification precedence over error-message wording. It does not supply a missing rule for the later period or establish that the message is a typographical error. The library therefore retains its own `VF-TAX-ZERO-RATE-UNRESOLVED` warning, without an asserted AEAT code. Supplying a receipt date does not resolve this discrepancy: the tax-rate rules refer to the operation date, falling back to the invoice issue date.

This remains a release limitation requiring an official clarification or corrected public source before claiming validation coverage for that combination. The warning is not evidence that either zero or 0.26 surcharge will be accepted. No live AEAT experiment was performed. The source versions and byte digests are recorded in the [archive manifest](../../schemas-aeat/manifest.tsv).

## Arithmetic interpretation boundaries

The aggregate checks in specification §3.1.3.16 and §3.1.3.17 exclude regimes 03, 05, 06, 08 and 09, without spelling out a separate mixed-detail algorithm. Local validation skips the aggregate comparison if any detail has an excluded regime. This is the library's documented interpretation: a partial sum of eligible details cannot establish the correctness of the whole invoice total. It is not a claim about AEAT's internal implementation. Independent detail checks remain applicable.

For the same-sign check in §3.1.3.15.7, zero is treated as neutral; opposite nonzero signs are rejected. This is consistent with §3.1.3.15.1 permitting a zero tax rate and with the §3.1.3.15.7 calculation yielding zero tax on a nonzero base at that rate. The local validator does not introduce an additional rejection of ordinary zero-tax results.
