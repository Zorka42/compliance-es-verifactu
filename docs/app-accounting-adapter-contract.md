# App-Accounting Adapter Contract

The library does not include an accounting application, database, lock manager, queue, UI or credential store. The compile-tested offline sample contains an application-owned boundary at `samples/offline/src/commonMain/kotlin/dev/verifactu/sample/AppAccountingAdapterContract.kt`; it is an integration pattern, not a public library API or a prescribed storage design.

## Responsibility boundary

| Application owns | VeriFactu KMP owns |
| --- | --- |
| Invoice finalization, application invoice ID, database transaction, per-chain lock/compare-and-set, queue, UI, credential lifecycle and retry/reconciliation decision | Deterministic record creation, next chain-head calculation, XML, QR payload, SOAP request preparation, transport-result evidence and parsed response semantics |

For each taxpayer/SIF chain, the application must load its current head, create the record using that exact head, and atomically persist the immutable record, next head and exact SOAP bytes. A failed compare-and-set persists neither the record nor the new head; the application reloads its state before making another candidate. This contract is detailed further in [chain-state persistence](chain-state-contract.md).

That transaction must also enforce a unique finalized application invoice/event ID. Repeating the same finalization returns `AlreadyRecorded` and leaves the saved record and chain head unchanged. A correction is a separately identified event. The common contract suite covers an actual stale-head comparison, repeated finalization and invalid finalized data.

Attempt state is separate from the fiscal record. The sample records an attempt before invoking an injected transport and stores the typed outcome afterwards. A timeout or other `UNKNOWN` delivery result does not regenerate a record: a host may reconcile and, if appropriate, resend the saved SOAP bytes. It must not roll back the chain head merely because delivery is ambiguous.

If saving the response fails, the attempt remains unresolved even if an acceptance response was received in memory. A restart test reloads the saved registration through the store, preserves that unresolved attempt and demonstrates an explicit host-selected replay of exactly the saved bytes. It does not prove database durability or prescribe automatic replay after a crash.

## Mapping a finalized invoice

The sample maps an application-owned `AppAccountingFinalizedRegistration` to `RegistroAltaDraft`. The application supplies the finalized invoice identity, issuer name, type, decimal totals, operation description, tax breakdown, SIF identity and fixed generation timestamp; the adapter supplies only the current persisted chain state. The library returns validation issues rather than finalizing an invoice on the application's behalf.

Successful preparation retains its `report`, including local warnings, and the sample persists it as `validationReport`. The host must inspect warnings and unresolved context before choosing delivery. A prepared record is not a promise that every AEAT rule was checked or that the response will be accepted.

## Remaining application data gaps

The app-accounting checkout at commit `1d54f4f` was inspected locally on 2026-09-11. Its `SalesInvoice` has a final invoice number, seller/customer snapshots, decimal monetary totals and tax lines. `InvoiceIssueWorkflow.issueDraft` allocates the number and sets `Issued`/`PendingSubmission` inside a `RecordTransaction`. Neither file wires a fiscal chain or saved SOAP attempt into that transaction. The sample here verifies the boundary contract; it does not claim that the application's real database already implements it.

An integrating product must explicitly supply and validate:

- its durable invoice ID, finalization event and legal decision that an invoice is issued;
- taxpayer/SIF chain key, transaction boundary and lock or compare-and-set conflict handling;
- every fiscal field not currently present in its invoice model, including the conditional F1/F3/R1–R5 data that applies to its invoice types;
- issuer/system configuration, record-generation timestamp, QR environment and any corrected/cancelled-record source data;
- durable attempt, reconciliation and user-notification state, including an appropriate protected/raw-response diagnostic policy;
- certificate access through the platform adapter, never certificate bytes, passwords or private keys passed into the common library.

The sample uses synthetic invoices, a fake transport and an `example.invalid` endpoint. It creates no network connection and makes no claim of AEAT acceptance.
