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
| `AeatResponseParseResult.InvalidXml` | Unsafe, malformed, oversized or structurally ambiguous XML; preserve the original only through explicit diagnostics |
| `AeatResponseCorrelationResult.Mismatch` | Response cannot be reconciled with the submitted identities and operations; do not mark the batch accepted |

The namespace-aware parser preserves each readable line's invoice identity, operation, declared state, raw unknown state and duplicate's earlier state. Unknown states become `UNKNOWN_STATE`. Missing identifiers or repeated critical fields invalidate the complete parse. A parsed response is not a full XSD/business-validation result. Match it to the submitted records with `AeatResponseCorrelation` before changing application state.

A duplicate's current declared rejection and earlier stored state are separate. `Correcta`, `AceptadaConErrores` and `Anulada` describe that earlier record; they do not justify automatically accepting a changed submission. A batch containing accepted-with-errors lines has aggregate `ParcialmenteCorrecto`; fully rejected submissions do not receive a new CSV. These semantics follow the archived service specification v1.0.3 §3 and response/common XSDs.

`AeatFlowControl.Known` preserves a recognized wait in seconds; `Unknown` preserves missing or unrecognized text. `retryAfterSeconds` remains a convenience for known values. The host schedules the next attempt; the library neither sleeps nor retries automatically.

## Persistence and retries

Persist the prepared fiscal record, next chain head and exact request before delivery, under the application's per-chain concurrency boundary. Keep attempts separate from records. After ambiguous delivery, retain the same hash, timestamp and XML; reconcile before deciding whether to resend. Do not generate a replacement record or roll back the fiscal chain merely because HTTP failed. Correction, incidence and automatic retry decisions still require the remaining source-backed policies; no exactly-once delivery is promised.

The offline sample demonstrates typed attempt handling and explicit reuse of saved request bytes, without a database, queue or scheduler. It is an application integration example, not a durable runtime implementation.

## Diagnostics and XML boundaries

Transport request/result, response, duplicate, invoice reference, SOAP fault and prepared-request summaries redact fiscal data. Explicit fields remain unredacted; domain records also contain fiscal data. The JVM adapter uses fixed exception reasons. Do not log raw XML, identifiers, remote messages or arbitrary domain objects by default.

The submission String parser accepts UTF-8 declarations (or no encoding declaration), rejects DTD/entity declarations and malformed UTF-16, and limits documents to 8 Mi characters, depth 64 and 100,000 elements. JVM/Android SAX and Apple Foundation adapters feed the same common response interpretation. The standalone query tool has its own 64 MiB file limit and validates against the archived query XSD.

Never put passwords, keys, certificate material, actual fiscal records or production responses in fixtures or issue reports. Use the [synthetic testkit](testkit.md).
