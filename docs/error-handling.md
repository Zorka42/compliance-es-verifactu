# Error Handling

## Local construction and validation

Handle `ValueResult.Valid`/`Invalid` for value parsing and `RecordCreationResult.Created`/`Invalid` for record creation. An invalid record result carries `ValidationReport`, with stable issue codes, field paths, severity, messages, and optional source references. The factory performs these checks before calculating the hash.

Local validation is incomplete and cannot establish remote acceptance. Do not use `Boolean success` as an application-wide fiscal state.

The low-level `SubmissionBatchXmlSerializer.serialize` currently throws `IllegalArgumentException` for an empty batch or more than 1,000 records. Typed batch validation is pending. `FakeAeatTransport` throws `IllegalStateException` on exhausted test scripts; that is a test setup error, not a simulated AEAT rejection.

## Current transport and parser results

| Result | Meaning and host action |
| --- | --- |
| `XmlResponse` | XML media type at any HTTP status, including SOAP faults; inspect status and parse the body |
| `InvalidEndpoint` | Local HTTPS precheck failed before delegation |
| `Timeout` / `NetworkFailure` | No usable response; these legacy types do not prove non-delivery |
| `NonXmlResponse` | HTTP response with an unrecognized media type; no fiscal acceptance can be inferred |
| `AeatResponseParseResult.InvalidXml` | Known fields could not be extracted; preserve the response for explicit diagnostics |

`AeatSubmissionResponse` exposes aggregate status, CSV, wait seconds, and parsed response lines. Known line states are accepted, accepted-with-errors, rejected, and duplicate. This is a minimal field extractor: it can drop unsupported lines, does not enforce XML namespaces/full well-formedness, and does not expose per-record identifiers or the original duplicate's status. It must not drive production batch reconciliation.

`SoapFault` contains decoded remote text, not sanitized text. The raw envelope is not retained by the parser, but remains available in `XmlResponse.xml` to a caller that explicitly chooses to store it.

## Delivery uncertainty and duplicate handling

Keep immutable records and delivery attempts as separate application state. A timeout can occur after a server received a request. Do not generate a replacement fiscal record, reset the chain, or infer rejection from transport failure alone.

Duplicate handling, safe retry decisions, and incident/subsanation code mappings require the pending source-backed runtime tasks. The current duplicate marker does not establish the previous record's acceptance state. No exactly-once behavior is promised.

The parser's nullable `retryAfterSeconds` exposes a known field but does not validate all flow-control variants. Unknown variants are not yet preserved structurally. The application owns scheduling; the library neither waits nor retries automatically.

## Diagnostics

No logging is enabled by the core. XML, remote messages, identifiers, and data-class string representations can contain personal/fiscal data. Keep diagnostics opt-in and redact them before logging. JVM transport exception reasons currently also need review before production logging.

Never put passwords, keys, certificate material, real fiscal records, or production responses in fixtures or issue reports. Use the [synthetic testkit](testkit.md) to demonstrate a problem.
