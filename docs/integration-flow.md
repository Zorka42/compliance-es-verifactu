# Integration Flow

The [shared executable example](../samples/offline/src/commonMain/kotlin/dev/verifactu/sample/OfflineExampleResult.kt) demonstrates the current local pipeline. It composes the pure preparation facade with explicit, application-owned attempt handling.

## Registration

1. The host finalizes invoice business input and acquires its per-chain lock.
2. It reads the current chain head and supplies an explicit generation timestamp and SIF metadata.
3. `FiscalSubmissionPreparation.prepareRegistration` returns typed structural issues or the record/hash, next head and XML/QR/SOAP artifacts.
4. The host atomically persists the invoice, original fiscal record, exact prepared request and next head under its durability policy.
5. The host renders the invoice and separately queues delivery. It releases its concurrency guard only after committing the new head.

The compile-tested [app-accounting adapter contract](app-accounting-adapter-contract.md) uses a fake host store to verify this compare-and-set boundary and exact-request replay. A real integration needs durable storage and recovery. The library neither writes data nor starts a worker.

## Cancellation

Pass the invoice reference to `RegistroAnulacionDraft` with the current chain head, then call `createCancellation`. Persist that new cancellation and its returned head. Keep the original registration. A cancellation can follow unrelated later records in the chain; its predecessor is the current head, not necessarily the record being cancelled.

## Offline submission example

The sample passes prepared SOAP to `FakeAeatTransport`, parses its schema-checked synthetic response and correlates every returned invoice/operation before inspecting acceptance. The attempt interpreter distinguishes local preflight failure, ambiguous delivery, HTTP failures, SOAP faults, malformed/mismatched responses and unknown states. It preserves flow control without sleeping.

The Java sample demonstrates the same production creation/XML/QR APIs with static calls and explicit getters. The independent Java consumer verifies local published JARs and their transitive dependencies.

## Production submission remains pending

The validated builder, preparation facade, typed delivery states and response correlation are implemented. Production integration still requires complete conditional fiscal models, source-backed incidence/correction policies, host persistence/recovery, real transport verification and release hardening.

`NOT_SENT` only represents known local failures; timeouts and send I/O failures remain `UNKNOWN`. A duplicate is not unconditional acceptance. Preserve the original record and response under an explicit diagnostics policy; do not generate a new record or roll back the chain on delivery failure. The sample tests manually reuse exactly the same prepared request. See [error handling](error-handling.md) and the [remaining plan](release-plan.md).

No storage, queue, certificate vault or automatic retry scheduler belongs inside the library.
