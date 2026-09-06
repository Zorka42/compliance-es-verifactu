# Core Concepts

## Deterministic values and validation

`TaxIdentifier`, `InvoiceNumber`, `InvoiceIssueDate`, `FiscalAmount`, and `RecordGenerationTimestamp` expose typed construction results. Fiscal amounts retain decimal text without floating-point conversion; parsing is not a tax calculation service. Invoice dates have calendar checks; generation timestamps currently have format checks only.

`ValidationIssue` contains a stable code, field path, severity, message, and optional AEAT/source references. `ValidationReport.isValid` means no local error-severity issues, not full compliance or AEAT acceptance.

## Records and system identity

`RegistroAlta` is a registration record; `RegistroAnulacion` is a cancellation record. Use `FiscalRecordFactory` to validate their drafts and calculate hashes. Their public constructors can also construct unvalidated records, so applications must not treat constructor use as proof of validity.

`SistemaInformatico` describes the host invoicing system and its producer. Do not automatically substitute this library's name/version for the host SIF metadata.

The API exposes official invoice category tokens F1–F3 and R1–R5; not all category-dependent fields are implemented. Samples use a synthetic F2 simplified invoice to avoid implying complete ordinary/rectifying invoice support.

## Chain state

`ChainState.FirstRecord` represents an empty chain. `ChainState.PreviousRecord` contains the preceding invoice reference and uppercase hash. Successful creation returns the next head alongside the record. Registration and cancellation participate in the same chronological sequence.

The application owns chain storage, per-chain locking, and atomic persistence. Delivery outcome does not change the generated record or reset the head. See [chain-state contract](chain-state-contract.md).

Values use read-only Kotlin properties, but lists passed by the caller are not guaranteed deep-immutable snapshots. Do not mutate a tax breakdown after hashing or serialization.

## XML, QR, and transport

`RegistroXmlSerializer` serializes completed records. `SubmissionBatchXmlSerializer` serializes a batch in supplied order and checks its count. Neither revalidates hashes or supplies the planned complete submission workflow.

`QrPayloadBuilder` returns a URL string for a selected environment. It never opens the URL or renders an image. The host owns QR rendering and invoice layout.

`AeatTransportAdapter` is an explicit execution boundary. Resolving endpoint metadata performs no I/O. JVM HTTP execution and Android/Apple injected adapters sit outside the deterministic core. The minimal response parser exposes known aggregate/per-line statuses, but is not ready for production reconciliation.

## Testkit

`verifactu-testkit` depends on production modules to provide synthetic record/payload fixtures and fake transport. Production modules do not depend on testkit. Add it to test dependencies only, except in an explicitly offline sample. See [testkit](testkit.md).
