# Integration Flow

The [shared executable example](../samples/offline/src/commonMain/kotlin/dev/verifactu/sample/OfflineExampleResult.kt) demonstrates the current local pipeline. It is sample application code, not the pending production workflow facade.

## Registration

1. The host finalizes invoice business input and acquires its per-chain lock.
2. It reads the current chain head and supplies an explicit generation timestamp and SIF metadata.
3. `FiscalRecordFactory.createRegistration` returns either typed structural issues or a record/hash and next head.
4. The host creates the XML and QR artifacts, then atomically persists the invoice, original fiscal record, and next head under its durability policy.
5. The host renders the invoice and separately queues delivery. It releases its concurrency guard only after committing the new head.

The sample uses an in-memory value to illustrate persistence ownership. A real integration needs durable storage and recovery. The library neither writes data nor starts a worker.

## Cancellation

Pass the invoice reference to `RegistroAnulacionDraft` with the current chain head, then call `createCancellation`. Persist that new cancellation and its returned head. Keep the original registration. A cancellation can follow unrelated later records in the chain; its predecessor is the current head, not necessarily the record being cancelled.

## Offline submission example

The sample serializes one record per batch, passes the batch document to `FakeAeatTransport`, and uses `AeatResponseParser.parseSubmission` on its synthetic XML response. It retains the response wait value for the host to inspect and never sleeps.

The Java sample demonstrates the same production creation/XML/QR APIs with static calls and explicit getters. The independent Java consumer verifies local published JARs and their transitive dependencies.

## Production submission remains pending

The low-level batch serializer is not the validated submission builder. A production integration still needs taxpayer/count validation with typed failures, SOAP request construction, complete response correlation, delivery-state distinctions, flow-control semantics, and source-backed retry/correction handling.

Transport errors and timeout results currently cannot prove whether delivery occurred. A duplicate response is not unconditional acceptance. Preserve the original record and raw response under an explicit diagnostics policy; do not automatically generate a new record or roll back the chain on delivery failure. See [error handling](error-handling.md).

The next workflow/orchestration APIs must preserve an explicit boundary between creating/persisting artifacts and sending them. No storage, queue, certificate vault, or automatic retry scheduler belongs inside the library.
