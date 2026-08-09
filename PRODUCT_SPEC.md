# VeriFactu KMP — Product Specification

**Status:** Draft  
**Target:** v1.0.0  
**Canonical language:** English  
**Compliance baseline reviewed:** 2026-08-09  
**License:** Apache-2.0

---

## 1. Product definition

**VeriFactu KMP** is an open-source Kotlin Multiplatform library that implements the Spanish **VERI*FACTU** fiscal-record protocol and AEAT submission integration.

It is a reusable compliance component for applications that issue invoices.

The library receives invoice/fiscal data plus the external chain state and provides:

- VERI*FACTU registration records;
- cancellation records;
- local validation;
- SHA-256 hash calculation;
- record chaining;
- deterministic XML serialization;
- QR payload generation;
- AEAT SOAP submission;
- typed AEAT response parsing;
- flow-control, retry and incidence semantics.

The library is deliberately **stateless and embeddable**.

It does not provide:

- a database;
- invoice storage;
- chain-state storage;
- a durable submission queue;
- background workers;
- certificate/private-key storage;
- UI;
- invoice PDF rendering;
- accounting;
- a hosted backend.

It is a protocol/compliance library, not an invoicing application.

---

## 2. Product goal

A Kotlin developer should be able to integrate VERI*FACTU without independently implementing the AEAT protocol.

The normal flow should be approximately:

```kotlin
val record = Verifactu.createRegistration(
    invoice = invoice,
    chain = previousChainState,
    system = systemInfo,
    generatedAt = timestamp
)

val validation = Verifactu.validate(record)
validation.requireValid()

val xml = VerifactuXml.serialize(record)

val qr = VerifactuQr.payload(
    invoice = invoice
)

val result = aeat.submit(
    taxpayer = taxpayer,
    system = systemInfo,
    records = listOf(record),
    credentials = credentials
)
```

After creating a record, the application can persist the compact state required to chain the next record:

```kotlin
val nextChainState = record.chainState
```

The library should make the correct path obvious and incorrect integrations difficult.

---

## 3. Target users

Primary users:

- Kotlin/JVM backend developers;
- Kotlin Multiplatform application developers;
- Android developers;
- iOS applications using a KMP shared module;
- macOS / Compose Desktop applications;
- ERP and POS developers;
- vertical SaaS vendors;
- invoicing and accounting software vendors.

The product is not tied to Spain-specific application architecture even though the protocol it implements is Spanish.

---

## 4. Compliance boundary

VeriFactu KMP is **not a complete Sistema Informático de Facturación (SIF)**.

Using the library does not by itself make an application compliant.

The integrating application remains responsible for application-level behavior including:

- deciding when an invoice is legally issued;
- durable persistence of its own business data;
- persisting the latest chain state;
- serializing concurrent record creation for a chain;
- maintaining a durable queue of unsent records;
- scheduling retries;
- displaying required incidence/pending-submission warnings;
- secure certificate and private-key storage;
- final invoice rendering and QR placement;
- operational security;
- integration of all SIF components;
- the compliance of the complete SIF.

The library MUST expose enough information and primitives for the integrating application to implement those responsibilities without reimplementing AEAT fiscal logic.

### 4.1. Component status

Spanish rules explicitly recognize invoicing **components** that implement requirements of a SIF.

Because VeriFactu KMP implements compliance-relevant functionality, production releases MUST be treated as a versioned SIF component for declaration/responsibility purposes.

The project MUST provide the information required for a component-level `declaración responsable` for released versions where the current regulations require it.

The declaration MUST describe only the scope implemented by VeriFactu KMP and MUST NOT claim compliance for the complete integrating SIF.

---

## 5. Scope of v1

Version 1 supports the **VERI*FACTU mode only**.

### 5.1. In scope

- `RegistroAlta`;
- `RegistroAnulacion`;
- current invoice types and enumerations defined by AEAT;
- rectifying invoice metadata;
- substitution metadata;
- `Subsanacion`;
- `RechazoPrevio`;
- SHA-256 hash generation;
- record chaining;
- local AEAT validation rules that can be evaluated without external state;
- deterministic XML;
- AEAT request generation;
- AEAT response parsing;
- SOAP faults;
- QR verification URL/payload generation;
- test and production AEAT environments;
- certificate-authenticated AEAT communication;
- batches;
- flow control;
- technical-incidence semantics;
- duplicate/retry semantics;
- typed submission results;
- test utilities for downstream integrators.

### 5.2. Explicitly out of scope

v1 does NOT implement the NO VERI*FACTU operating mode.

Therefore v1 does not need to provide:

- XAdES signing of fiscal records as a VERI*FACTU requirement;
- event-record generation;
- event-record persistence;
- NO VERI*FACTU export/conservation machinery;
- local long-term integrity enforcement required specifically by NO VERI*FACTU;
- blockchain or distributed-ledger functionality.

VERI*FACTU requires correct SHA-256 hashing and chaining, not a blockchain.

---

## 6. Normative sources

Compliance behavior MUST be derived from official sources.

Order of authority:

1. BOE legislation;
2. AEAT technical specifications;
3. AEAT XSD/WSDL and published validation/error definitions;
4. AEAT FAQs for clarification.

Third-party libraries, blog posts and vendor documentation are never normative.

### 6.1. Legal baseline

- Real Decreto 1007/2023  
  https://www.boe.es/buscar/act.php?id=BOE-A-2023-24840

- Orden HAC/1177/2024  
  https://www.boe.es/buscar/act.php?id=BOE-A-2024-22138

- Real Decreto 1619/2012  
  https://www.boe.es/buscar/act.php?id=BOE-A-2012-14696

### 6.2. AEAT technical baseline

- Technical documentation hub  
  https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica.html

- Record designs  
  https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica/disenos-registro.html

- WSDL  
  https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica/wsdl-servicios-web.html

- Schemas  
  https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica/esquemas.html

Current technical versions observed during this review:

- validations/errors: **1.2.2**;
- voluntary submission web service: **1.0.3**;
- hash specification: **0.1.2**;
- QR specification: **0.5.0**.

Exact versions used by each release MUST be recorded in repository compliance documentation.

---

## 7. Compliance traceability

Every compliance-relevant behavior MUST be traceable to an official source.

The project SHOULD maintain a simple matrix under:

```text
docs/compliance/
```

with:

```text
requirement
official source
source section/version
implementation
tests
```

The purpose is practical: when AEAT changes a rule, maintainers should be able to identify the affected implementation and tests.

The matrix must remain lightweight and must not duplicate the product specification.

---

## 8. Core architecture

The product has two conceptual layers.

```text
                 deterministic core
                        │
      ┌─────────────────┼─────────────────┐
      │                 │                 │
   models           validation          hash
      │                 │                 │
      └──────── fiscal records ──────────┘
                        │
                    chain state
                        │
               deterministic XML
                        │
                   QR payload


                   AEAT transport
                        │
                  SOAP / mTLS
                        │
                request / response
                        │
                 typed outcomes
```

### 8.1. Deterministic core

The core MUST have no hidden I/O.

Given the same inputs, it MUST produce the same:

- fiscal record;
- hash input;
- hash;
- XML;
- QR payload.

### 8.2. AEAT transport

Network submission MUST be a separate concern from record generation.

A developer must be able to use:

```kotlin
Verifactu.createRegistration(...)
Verifactu.validate(...)
VerifactuXml.serialize(...)
```

without configuring networking or certificates.

---

## 9. Domain model

The public model represents the fiscal data required by VERI*FACTU.

It is not intended to replace the application's own invoice/accounting model.

### Requirements

- immutable by default;
- strongly typed;
- exact decimal monetary values;
- no `Double` or `Float` for fiscal amounts;
- explicit date/time types;
- official enumerations represented as types;
- absence and zero are distinct;
- invalid combinations prevented where practical;
- unknown future enum/schema values must fail explicitly rather than be silently reinterpreted.

Developer-facing names SHOULD be English.

Official Spanish terminology SHOULD be preserved where it improves protocol clarity or maps directly to wire fields.

---

## 10. Billing system information

The library MUST model the AEAT `SistemaInformatico` information required in fiscal records/submissions.

The model must include the current required properties such as:

- producer identity;
- system name;
- system identifier;
- version;
- installation number;
- VERI*FACTU-only capability flag;
- multi-taxpayer capability flags.

The library MUST validate this data locally where AEAT rules make validation deterministic.

The system information is supplied by the integrating application and is not globally stored by the library.

---

## 11. Registration records

The library MUST create a complete `RegistroAlta` from a typed input model.

Conceptual API:

```kotlin
val record = Verifactu.createRegistration(
    invoice = invoice,
    chain = chain,
    system = systemInfo,
    generatedAt = timestamp
)
```

The function MUST:

1. map the input data into the current AEAT record model;
2. insert the previous-record chain reference when applicable;
3. insert system information;
4. calculate the hash input exactly as defined by AEAT;
5. calculate and store the SHA-256 hash;
6. return an immutable record;
7. expose the compact `chainState` required by the next record.

Creation MUST NOT:

- persist anything;
- submit anything;
- load previous state from disk;
- mutate hidden state;
- discover certificates;
- make network calls.

---

## 12. Cancellation records

The library MUST support `RegistroAnulacion`.

Conceptual API:

```kotlin
val cancellation = Verifactu.createCancellation(
    invoice = invoiceIdentity,
    chain = chain,
    system = systemInfo,
    generatedAt = timestamp
)
```

A cancellation record participates in the same fiscal-record chain as registration records.

The library MUST NOT treat cancellation as deletion.

Previously generated registration records remain immutable from the perspective of the library.

---

## 13. Chain state

Chain state is caller-owned.

The library SHOULD expose a compact immutable object containing only the information required to chain the next record.

Conceptually:

```kotlin
sealed interface Chain {
    data object First : Chain

    data class Previous(
        val record: PreviousRecordRef
    ) : Chain
}

data class PreviousRecordRef(
    val issuerId: String,
    val invoiceNumber: String,
    val issueDate: LocalDate,
    val hash: VerifactuHash
)
```

The exact API can evolve before 1.0.

### Requirements

- registration and cancellation records share the same chronological chain;
- chains are independent per SIF and taxpayer;
- a multi-taxpayer SIF must have an independent chain for each taxpayer;
- invoice series do not create separate chains inside one SIF;
- the library never stores the current chain head;
- the caller provides either `First` or the previous chain state;
- each generated record exposes the chain state for the next record.

### 13.1. Concurrency contract

The integrating application MUST serialize fiscal-record creation for each independent SIF + taxpayer chain.

Two records MUST NOT be concurrently created against the same previous chain head.

Example of an invalid integration:

```text
             previous X
              /      \
          record A  record B
```

The library documentation MUST make this requirement prominent.

The library MAY provide helper abstractions for chain coordination, but MUST NOT require a particular database or locking technology.

---

## 14. Hashing

Hashing MUST implement the AEAT hash specification exactly.

Current algorithm: SHA-256.

Requirements include:

- exact AEAT field selection;
- exact field ordering;
- exact canonical input construction;
- UTF-8;
- deterministic trimming/formatting;
- deterministic decimal formatting;
- uppercase hexadecimal representation where required by the current specification;
- no platform-default charset;
- no locale-sensitive formatting.

Conceptual API:

```kotlin
val hash = VerifactuHash.calculate(record)
```

The project MUST include official/reference hash vectors as golden tests.

### 14.1. Hash verification

A diagnostic helper MAY be provided:

```kotlin
val verification = VerifactuHash.verify(record)
```

For VERI*FACTU mode this is **not** required as part of the normal record-generation path.

The AEAT requires VERI*FACTU systems to calculate and chain hashes correctly, but does not require them to re-verify previously generated hashes before creating every new record.

---

## 15. Validation

Validation is a first-class product feature.

Conceptual API:

```kotlin
val report = Verifactu.validate(record)
```

Recommended model:

```kotlin
data class ValidationReport(
    val issues: List<ValidationIssue>
) {
    val isValid: Boolean
}

data class ValidationIssue(
    val code: String,
    val path: String,
    val severity: ValidationSeverity,
    val message: String,
    val aeatCode: String? = null,
    val source: ComplianceSourceRef? = null
)
```

### Requirements

Validation MUST:

- evaluate all current deterministic AEAT rules implemented by the library;
- identify the exact field/path;
- expose stable machine-readable library codes;
- preserve AEAT error codes separately when one exists;
- distinguish errors from warnings;
- distinguish locally verifiable rules from rules that depend on AEAT/external state;
- remain side-effect free;
- never require network access.

Validation MUST NOT pretend to predict every possible AEAT rejection.

The AEAT itself remains authoritative for remote validation.

---

## 16. XML

The library MUST support deterministic AEAT-compatible XML serialization.

Conceptual API:

```kotlin
val xml = VerifactuXml.serialize(record)
```

and for submission:

```kotlin
val requestXml = VerifactuXml.serializeSubmission(batch)
```

Requirements:

- UTF-8;
- correct namespaces;
- correct element ordering;
- correct optional/required field behavior;
- exact date/time formatting;
- exact decimal formatting;
- correct escaping;
- deterministic output;
- compatibility with pinned official schemas;
- parsing of supported response structures and SOAP faults.

The same logical input MUST produce the same XML bytes where the protocol representation is deterministic.

XML generation MUST be testable without network access.

---

## 17. QR

QR payload generation is part of the product.

Invoice rendering is not.

Conceptual API:

```kotlin
val payload = VerifactuQr.payload(invoice)
```

The core result SHOULD be the AEAT verification URL/payload, not an image.

An optional renderer MAY be provided later or as a convenience adapter.

The library MUST expose enough information for the integrating application to render the required QR correctly.

Current regulatory baseline includes:

- ISO/IEC 18004;
- error-correction level M;
- printed size between 30×30 mm and 40×40 mm;
- invoice issuer NIF;
- invoice number/series;
- invoice issue date;
- invoice total;
- appropriate AEAT verification/remittance URL.

The fiscal-record hash is not part of the QR payload.

The integrating application is responsible for QR placement and final invoice presentation, including the VERI*FACTU text required on invoices issued in that mode.

---

## 18. AEAT client

The AEAT client is an optional I/O layer on top of the deterministic core.

Conceptual API:

```kotlin
val result = aeat.submit(
    taxpayer = taxpayer,
    system = systemInfo,
    records = records,
    credentials = credentials
)
```

Requirements:

- SOAP/XML protocol supported by AEAT;
- explicit test vs production environment;
- qualified certificate authentication through a platform abstraction;
- no certificate persistence;
- no private-key persistence;
- no background worker;
- no durable retry queue;
- same-taxpayer validation for a batch;
- current maximum batch size enforcement;
- response parsing at both global and per-record level;
- CSV extraction when returned;
- `TiempoEsperaEnvio` extraction;
- SOAP/transport/business failures clearly separated.

Current protocol allows **1 to 1,000** registration/cancellation records in a submission file.

Registration and cancellation records may coexist in one batch.

---

## 19. Credential abstraction

Certificate handling is platform-specific and MUST be isolated from fiscal logic.

The public client MUST accept credentials through an abstraction rather than require one specific storage format.

The library SHOULD support integrations where the certificate comes from:

- PKCS#12/PFX data;
- JVM/OS key stores;
- Apple Keychain/native security APIs;
- other caller-controlled providers.

The library MUST NOT require that certificate bytes be persisted by VeriFactu KMP.

Sensitive material MUST never appear in normal string representations, exceptions, or logs.

---

## 20. Submission result model

There MUST NOT be a generic `Boolean success` response.

The caller needs enough structured information to decide what happens next.

Conceptually:

```kotlin
sealed interface SubmissionResult {

    data class Completed(
        val csv: String?,
        val records: List<RecordResult>,
        val flowControl: FlowControl
    ) : SubmissionResult

    data class TransportFailure(
        val delivery: DeliveryState,
        val error: TransportError
    ) : SubmissionResult

    data class ProtocolFailure(
        val fault: SoapFault
    ) : SubmissionResult
}
```

Per-record result:

```kotlin
sealed interface RecordResult {
    data class Accepted(...) : RecordResult
    data class AcceptedWithErrors(...) : RecordResult
    data class Rejected(...) : RecordResult
}
```

The model MUST preserve:

- AEAT error code;
- AEAT error description;
- record identity;
- submission state;
- any data required for subsequent correction/subsanation handling.

---

## 21. Unknown delivery and duplicate retries

Network failures are not equivalent to AEAT rejection.

A request may have reached AEAT even when the client did not receive the response.

The result model MUST allow the caller to distinguish:

```text
definitely not sent
delivery unknown
AEAT responded
```

If the response is lost, AEAT guidance requires the records to be sent again until a response is obtained.

Therefore the library MUST make it possible to retry the same immutable fiscal records safely and parse the resulting duplicate/already-known semantics.

The library MUST NOT create a new fiscal record merely because an HTTP response was lost.

---

## 22. Flow control

VERI*FACTU submissions are subject to AEAT flow control.

The current legal baseline starts with a wait value of **60 seconds**.

AEAT responses return an updated `TiempoEsperaEnvio`, which must be used for subsequent submissions.

The protocol also permits a new submission once the maximum batch size has accumulated, according to the current flow-control rules.

The library MUST expose:

```kotlin
data class FlowControl(
    val waitSeconds: Int
)
```

or an equivalent type.

The library MUST NOT sleep or schedule the next submission itself.

The host application owns scheduling.

---

## 23. Technical incidence

When a technical incident prevents immediate VERI*FACTU submission:

- pending records must remain in generation order;
- they must be submitted as soon as possible;
- the affected submission must carry the appropriate incidence indication;
- pending transmission must be retried periodically;
- the current rule requires retry **at least once per hour** while records remain pending;
- the complete SIF must warn the user while records remain unsent.

The library does not own the queue or UI.

It MUST provide typed information and request-building support so the host application can implement these requirements correctly.

The API SHOULD make a distinction between:

```text
record content
submission context
retry/incidence state
```

so that transport state is not confused with the immutable business identity of an invoice.

---

## 24. Correction, rejection and subsanation

The library MUST model AEAT correction semantics explicitly.

It MUST support the current protocol fields and behaviors around:

- rejected records;
- records accepted with errors;
- correction/subsanation;
- previous rejection;
- invoice cancellation;
- legally rectifying invoices.

These concepts MUST NOT be collapsed into a generic `retry = true/false`.

The library SHOULD expose a typed action/directive where the AEAT rules are deterministic, for example:

```kotlin
sealed interface NextAction {
    data object None : NextAction
    data object RetrySameRecord : NextAction
    data object CorrectionRequired : NextAction
    data object ReviewRequired : NextAction
}
```

The library MUST NOT invent corrective behavior when AEAT rules or business context do not make the correct action deterministic.

---

## 25. Logging and diagnostics

Core functionality MUST NOT log by default.

The AEAT client MAY expose an optional caller-provided diagnostics interface.

Requirements:

- no invoice/customer data logged by default;
- no credentials logged;
- no certificate/private-key data logged;
- raw XML logging is opt-in;
- raw XML logging must be documented as potentially containing personal and fiscal data.

---

## 26. Kotlin Multiplatform requirements

The compliance core SHOULD live in `commonMain` wherever practical.

v1 MUST support the targets required for the primary use cases:

- JVM;
- Android;
- iOS;
- macOS.

JVM support covers backend and Compose Desktop use cases.

Platform-specific code SHOULD be limited primarily to:

- TLS/client-certificate integration;
- cryptographic provider plumbing where unavoidable;
- optional native representations.

Protocol behavior MUST remain equivalent across platforms.

Additional KMP targets may be added later without changing the fiscal model.

---

## 27. Module boundaries

The project SHOULD remain understandable and avoid unnecessary module fragmentation.

A likely structure is:

```text
verifactu-core
verifactu-xml
verifactu-qr
verifactu-aeat
verifactu-testkit
```

Validation may live in `core` or in a separate module depending on implementation size.

### `verifactu-core`

- fiscal models;
- registration/cancellation creation;
- hashing;
- chaining;
- validation;
- chain state.

### `verifactu-xml`

- XML serialization;
- request/response XML;
- schema adapters.

### `verifactu-qr`

- AEAT QR URL/payload.

### `verifactu-aeat`

- SOAP;
- environments;
- certificate abstraction;
- submission;
- response/error parsing;
- flow-control metadata.

### `verifactu-testkit`

Optional but desirable:

- deterministic fixture builders;
- fake transport;
- AEAT response fixtures;
- chain assertions;
- XML assertions.

The exact Gradle module count may be reduced if separate modules provide little practical value.

---

## 28. Testability requirements

Every compliance algorithm MUST be testable without AEAT network access.

Required product-level test categories:

- official/reference hash vectors;
- first record;
- subsequent registration record;
- registration → cancellation chaining;
- invoice-type and tax/regime validation;
- rectifying/substitution cases;
- deterministic XML fixtures;
- XSD-valid request fixtures;
- QR payload fixtures;
- accepted AEAT response;
- accepted-with-errors response;
- rejected response;
- SOAP fault;
- lost-response/unknown-delivery scenario;
- duplicate retry;
- partial batch result;
- flow-control response;
- incidence submission.

Where AEAT publishes examples, they SHOULD be used as golden/reference fixtures.

Real AEAT test-environment tests are useful but MUST NOT be required for deterministic unit/conformance testing.

---

## 29. Compliance declaration package

For each production release, the project MUST review whether its component-level `declaración responsable` needs to change.

The repository SHOULD contain a versioned declaration or declaration template identifying:

- component name;
- component/version;
- producer;
- implemented scope;
- supported VERI*FACTU mode;
- relevant system/component characteristics;
- regulatory baseline;
- limitations and integration responsibilities.

The declaration MUST be reviewed against the current Article 15 of Orden HAC/1177/2024 and current AEAT guidance before release.

---

## 30. Developer experience requirements

The public API should optimize for the common integrator, not mirror raw XSD complexity everywhere.

Key rules:

- English-first public API;
- official Spanish wire fields hidden behind typed adapters where practical;
- few top-level concepts;
- immutable values;
- no hidden state;
- no hidden network calls;
- actionable validation messages;
- actionable submission results;
- raw generated records/XML remain inspectable;
- caller can replace storage, queue and credential architecture freely.

A developer should not need to understand SOAP namespaces or AEAT hash-string construction to issue a correct request.

At the same time, the library must not hide information required to debug compliance problems.

---

## 31. Non-functional product requirements

### Determinism

Identical deterministic inputs produce identical fiscal outputs.

### Portability

Compliance behavior is equivalent across supported KMP targets.

### Privacy

The library does not collect telemetry or send data anywhere except the explicitly configured AEAT endpoint.

### Minimal dependencies

Runtime dependencies should be kept small, particularly in `core`.

### Inspectability

Generated fiscal records, XML, hashes and parsed AEAT results must be inspectable by integrators.

### Version awareness

The library must expose the implemented AEAT/specification baseline in a machine-readable or programmatically accessible form.

Example:

```kotlin
VerifactuComplianceInfo.current
```

---

## 32. v1 acceptance criteria

v1.0.0 is product-ready when:

1. registration and cancellation records cover the current VERI*FACTU schema;
2. current deterministic AEAT validation rules are implemented;
3. official/reference hash vectors pass exactly;
4. chaining works across registration and cancellation records;
5. compact caller-owned chain state is exposed;
6. deterministic XML validates against the pinned AEAT schema;
7. QR payloads match the current AEAT specification;
8. test and production submission endpoints are supported;
9. certificate-authenticated SOAP submission works on supported primary platforms;
10. global and per-record AEAT results are typed;
11. accepted / accepted-with-errors / rejected states are represented;
12. lost-response retry semantics are represented;
13. flow control is exposed;
14. incidence semantics are supported without owning a scheduler;
15. subsanation/previous-rejection semantics are represented;
16. no DB, persistent queue, UI or certificate store is required by the library;
17. downstream applications can test integrations without live AEAT;
18. component declaration responsibilities have been reviewed for the released version;
19. official source baseline is documented;
20. public API and documentation clearly explain the integration boundary.

---

## 33. Design references

The following open-source projects have been studied as non-normative engineering references:

- `eloi24/verifactu-sdk`
- `josemmo/Verifactu-PHP`
- `invopop/gobl.verifactu`
- `mdiago/VeriFactu`

Useful patterns adopted conceptually include:

- English-first developer APIs around Spanish AEAT wire models;
- compact caller-owned chain state;
- separation of record generation from persistence;
- strict validation and golden/reference tests;
- typed AEAT responses;
- practical handling of flow control and retries.

Patterns intentionally avoided include:

- hidden persistent state;
- library-owned databases;
- library-owned background queues;
- treating hash chaining as a blockchain;
- combining record creation, storage and submission into one opaque `save()` operation.

Official AEAT and BOE sources remain the sole normative source for compliance behavior.
