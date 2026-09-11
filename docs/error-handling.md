# Error Handling

## Local preparation

Value parsers return `ValueResult`; record factories return `RecordCreationResult`. `FiscalSubmissionPreparation` composes record, hash, XML, QR, batch and SOAP preparation. Its invalid results and `SubmissionBatchBuildResult.Invalid` carry `ValidationReport` with stable codes and field paths. Batch preparation checks the header, count, issuer consistency, drafts and supplied hashes before producing request strings.

Validation covers the implemented fields and structural rules. It cannot establish remote acceptance or validate currently unmodeled conditional fiscal fields. Public unchecked record constructors and the low-level serializer remain available; prefer the validated factory/facade. `FakeAeatTransport` exhaustion throws as a test setup error.

## Delivery and response states

| Result | Meaning and host action |
| --- | --- |
| `InvalidEndpoint`, `NotSent` | `deliveryState=NOT_SENT`; local preparation/preflight failed |
| `Timeout`, `NetworkFailure` | `deliveryState=UNKNOWN`; request delivery may have occurred |
| `XmlResponse`, `NonXmlResponse` | `deliveryState=RESPONSE_RECEIVED`; HTTP reception is not fiscal acceptance |
| `ResponseTooLarge` | `deliveryState=RESPONSE_RECEIVED`; the HTTP body exceeded the local cap and was not retained or parsed |
| `AeatResponseParseResult.InvalidXml` | Unsafe, malformed, oversized or structurally ambiguous XML; preserve the original only through explicit diagnostics |
| `AeatResponseCorrelationResult.Mismatch` | Response cannot be reconciled with the submitted identities and operations; do not mark the batch accepted |

The namespace-aware parser preserves each readable line's invoice identity, operation, declared state, raw unknown state and duplicate's earlier state. Unknown states become `UNKNOWN_STATE`. Missing identifiers or repeated critical fields invalidate the complete parse. A parsed response is not a full XSD/business-validation result. Match it to the submitted records with `AeatResponseCorrelation` before changing application state.

A duplicate's current declared rejection and earlier stored state are separate. `Correcta`, `AceptadaConErrores` and `Anulada` describe that earlier record; they do not justify automatically accepting a changed submission. A batch containing accepted-with-errors lines has aggregate `ParcialmenteCorrecto`; fully rejected submissions do not receive a new CSV. These semantics follow the archived service specification v1.0.3 §3 and response/common XSDs.

`AeatFlowControl.Known` preserves a recognized wait in seconds; `Unknown` preserves missing or unrecognized text. `retryAfterSeconds` remains a convenience for known values. The host schedules the next attempt; the library neither sleeps nor retries automatically.

## Persistence and retries

Persist the prepared fiscal record, next chain head and exact request before delivery, under the application's per-chain concurrency boundary. Keep attempts separate from records. After ambiguous delivery, retain the same hash, timestamp and XML; reconcile before deciding whether to resend. Do not generate a replacement record or roll back the fiscal chain merely because HTTP failed. Correction, incidence and automatic retry decisions still require the remaining source-backed policies; no exactly-once delivery is promised.

The offline sample demonstrates typed attempt handling and explicit reuse of saved request bytes, without a database, queue or scheduler. It is an application integration example, not a durable runtime implementation.

## AEAT Catalogue Semantics

Known AEAT error codes are exposed as a typed incidence with its catalogue source, disposition, location, and whether the catalogue expressly requires subsanation. The archived `errores.properties` baseline groups codes `2000`–`2009` as accepted records that must subsequently be subsanated; the library maps all of them to `ACCEPTED_WITH_ERRORS` and `REQUIRED`. The published technical/header group maps to `WHOLE_SUBMISSION_REJECTED`.

For `3000`–`3004` and the published record/header-dependent validation group, the catalogue states that the effect depends on where the error occurred. Pass `AeatErrorLocation.RECORD` for `RECORD_REJECTED` or `HEADER` for `WHOLE_SUBMISSION_REJECTED`. The default location is `UNKNOWN`, which retains an `UNKNOWN` disposition for these codes. The response parser supplies `RECORD` for line diagnostics. Catalogue classification describes published code semantics; it never overrides the response's declared status or authorizes a retry.

`AeatResponseOperation` preserves the exact `Subsanacion`, `RechazoPrevio`, and `SinRegistroPrevio` values returned in the response. Registration models represent caller-selected subsanation and prior-rejection flags, including `RechazoPrevio=X`. Correlation checks those flags against the submitted conditional data after uniquely matching invoice identity and operation. Missing/contradictory flags return `OPERATION_FLAGS_MISMATCH`; unknown flag text returns `UNSUPPORTED_OPERATION_FLAGS`. Absent flags and explicit `N` represent the same unmarked state. Cancellation models still cannot represent affirmative correction flags. Matching never establishes acceptance or selects a corrective operation; the application owns that decision and its source-backed workflow.

Unknown codes remain typed with an `UNKNOWN` disposition and no inferred correction or retry. A future catalogue version, an unknown flag value, and the relation between an application invoice and a correction are unresolved at library level; update the archived source and mapping deliberately before automating any such policy. An invoice identity and the operation returned in a response line are correlation data; neither is treated as acceptance.

## Unicode and XML Lengths

Common validation follows [W3C XML Schema Part 2, sections 4.3.1 and 4.3.3](../schemas-aeat/w3c-xmlschema-2-20041028.html): lengths count Unicode code points, so a supplementary character counts once. Combining marks count separately, and no normalization is applied. The same common fixtures run on JVM, Android host tests and Apple targets. XML serialization preserves those values; invalid XML scalars and unpaired surrogates are rejected separately.

The response-reader corpus also round-trips 60 supplementary characters and equivalent numeric character references through JVM/Android-host SAX and Apple Foundation. These are parser/structural boundaries, not proof of AEAT's narrower invoice-number business character profile (tracked separately as ZA-115).

The selected JVM XSD provider may instead measure supplementary characters as two UTF-16 units. A reproducible test first probes `xs:maxLength=1`, then checks that the archived AEAT schema exhibits the same profile at its 120-character boundary; DOM parsing must preserve the complete normative boundary under either profile. The test accepts either provider profile without enabling a JVM-global property. Android host tests exercise common semantics on the host JVM; they do not establish behaviour of an Android device XSD provider. Apple common tests check validation and serialization, and the Foundation adapter parses XML without XSD validation. The query tool deliberately applies its selected JAXP provider, so it may reject text that common validation accepts near a supplementary-character boundary. The intended validator and later AEAT interoperability require explicit release evidence.

## Diagnostics and XML boundaries

Transport request/result, response, duplicate, incidence, invoice reference, SOAP fault and prepared-request summaries redact fiscal data. Explicit fields remain unredacted; domain records also contain fiscal data. The JVM adapter uses fixed exception reasons. Do not log raw XML, identifiers, remote messages or arbitrary domain objects by default.

The submission String parser accepts UTF-8 declarations (or no encoding declaration), rejects DTD/entity declarations and malformed UTF-16, and limits documents to 8 Mi characters, depth 64 and 100,000 elements. JVM/Android SAX and Apple Foundation adapters feed the same common response interpretation. The standalone query tool has its own 64 MiB file limit and validates against the archived query XSD.

Never put passwords, keys, certificate material, actual fiscal records or production responses in fixtures or issue reports. Use the [synthetic testkit](testkit.md).
