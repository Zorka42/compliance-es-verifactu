# VeriFactu KMP — Product Requirements Document

**Status:** Draft
**Target:** v1.0.0
**Canonical language:** English
**License:** Apache-2.0
**Compliance baseline reviewed:** 2026-08-15
**Primary mode:** VERI*FACTU

---

## 1. Executive Summary

### Problem Statement

Spanish invoicing software vendors need to support VERI*FACTU before the 2027 mandatory rollout, but most Kotlin teams should not have to reimplement AEAT fiscal-record generation, hash chaining, XML/SOAP integration, QR payload construction, and response semantics from scratch.

Kotlin teams also need a library that is genuinely Kotlin Multiplatform, not a JVM-only SDK with a KMP label.

### Proposed Solution

Build **VeriFactu KMP**: an Apache-2.0 open-source Kotlin Multiplatform library that implements the reusable VERI*FACTU protocol layer for invoice/fiscal-record generation, local validation, deterministic serialization, QR payload generation, AEAT submission, and typed response handling.

The library is a compliance-focused embeddable component. It is not an invoicing application, database, queue, certificate vault, accounting system, hosted API, or complete Sistema Informatico de Facturacion (SIF).

### Success Criteria

- v1.0.0 publishes documented artifacts for `commonMain`, `jvmMain`, `androidMain`, `iosMain`, and `macosMain`.
- Every advertised KMP target compiles in CI and runs conformance tests or target-specific tests.
- 100% of compliance-relevant v1 behavior has a traceability entry linking official BOE/AEAT source, implementation, and tests.
- Hash, chain, XML, QR, validation, and response parsing fixtures pass deterministically on supported targets.
- First public release includes Apache-2.0 licensing, Maven Central publishing, CI, contribution docs, changelog, API docs, security policy, and release notes.

### Current Regulatory Timing

The current official baseline says:

- Corporate taxpayers covered by Article 3.1.a of RRSIF must have adapted systems before **2027-01-01**.
- Other covered taxpayers under Article 3.1 must have adapted systems before **2027-07-01**.
- Producers and commercializers of covered SIF products must offer products fully adapted within nine months from the entry into force of Orden HAC/1177/2024; AEAT FAQ identifies the resulting deadline as **2025-07-29**.

These dates must be rechecked before every release.

---

## 2. User Experience & Functionality

### User Personas

- Kotlin/JVM backend developers integrating ERP, SaaS, POS, or accounting systems.
- Kotlin Multiplatform developers sharing fiscal-record logic across server, Android, iOS, and desktop clients.
- Android, iOS, and macOS app developers embedding a KMP shared module.
- Open-source contributors updating AEAT rules, schemas, tests, docs, or platform adapters.
- Compliance reviewers auditing source-to-code-to-test traceability.

### User Stories

#### Generate a registration record

As a Kotlin developer, I want to create a typed `RegistroAlta` from invoice data and caller-owned chain state so that I can produce a compliant fiscal record without hand-building AEAT XML.

Acceptance criteria:

- The API accepts typed invoice data, system information, generation timestamp, and previous chain state.
- The function returns an immutable registration record with calculated hash and compact `chainState`.
- The function performs no hidden I/O, persistence, network calls, certificate lookup, global mutation, or background scheduling.
- The same deterministic inputs produce the same record, hash input, hash, XML representation, and QR payload.

#### Generate a cancellation record

As an integrator, I want to create `RegistroAnulacion` records so that invoice cancellation participates correctly in the same fiscal chain.

Acceptance criteria:

- Cancellation records are immutable fiscal records, not deletion commands.
- Registration and cancellation records share the same chronological chain.
- Cancellation creation exposes the next compact chain state.

#### Validate records locally

As a developer, I want deterministic local validation before submission so that I can catch input and protocol errors without making AEAT calls.

Acceptance criteria:

- Validation returns structured issues with stable library codes, field paths, severity, human-readable messages, optional AEAT code, and compliance-source reference.
- Validation distinguishes locally deterministic rules from rules that require AEAT or other external state.
- Validation is side-effect free and available from `commonMain`.

#### Serialize XML deterministically

As a developer, I want AEAT-compatible XML generation so that I do not manually manage namespaces, element ordering, escaping, date formats, or decimal formats.

Acceptance criteria:

- Serialization produces deterministic UTF-8 XML bytes for deterministic inputs.
- XML uses the pinned AEAT schemas/namespaces for the release compliance baseline.
- XML serialization tests include golden fixtures, negative cases, and XSD validation where platform tooling is available.

#### Generate QR payloads

As an invoice/POS developer, I want the QR verification URL/payload so that my application can render the required QR on invoices using its own UI/PDF stack.

Acceptance criteria:

- The library returns the AEAT QR payload or verification URL, not only a bitmap.
- QR image rendering, invoice layout, and QR placement remain host-application responsibilities.

#### Submit to AEAT

As a backend or app developer, I want a typed AEAT client so that I can submit immutable fiscal records, parse responses, and handle flow-control/retry semantics without dealing directly with SOAP internals.

Acceptance criteria:

- The AEAT client is an optional I/O layer; core record generation works without configuring networking or certificates.
- Certificate authentication is modeled through platform-specific abstractions without storing private keys in the library.
- Submission results distinguish global response, per-record response, SOAP fault, transport failure, delivery-unknown failure, and business rejection.
- The library exposes flow-control data and never sleeps, schedules, or owns a durable queue.

#### Consume the library from real KMP targets

As a Kotlin Multiplatform developer, I want each supported `*Main` source set to be real and tested so that the library does not break outside JVM.

Acceptance criteria:

- `commonMain` contains deterministic fiscal core behavior.
- `jvmMain`, `androidMain`, `iosMain`, and `macosMain` contain only platform-specific adapters where required.
- Every advertised target has compile and test coverage in CI.
- A target is not advertised as supported if only metadata compilation works.

### Non-Goals

v1 does not provide:

- a complete SIF;
- invoice/business database;
- chain-state database;
- durable submission queue;
- background worker or scheduler;
- certificate/private-key storage;
- user interface;
- invoice PDF rendering;
- accounting or tax advisory logic;
- hosted API/SaaS;
- NO VERI*FACTU mode;
- NO VERI*FACTU event-record, conservation, export, or XAdES machinery;
- blockchain or distributed-ledger functionality.

---

## 3. AI System Requirements

Not applicable. VeriFactu KMP is a deterministic compliance/protocol library and has no AI runtime component in v1.

---

## 4. Technical Specifications

### Architecture Overview

The product has three layers:

```text
deterministic core
  models
  validation
  hash input construction
  SHA-256 hashing
  fiscal record creation
  chain state
  QR payload
  compliance metadata

serialization
  deterministic XML
  request XML
  response XML
  SOAP fault parsing
  schema fixtures

AEAT transport
  environments
  SOAP client
  TLS/client certificate adapters
  typed submission results
  flow control
  retry/incidence support
```

Recommended Gradle modules:

- `verifactu-core`;
- `verifactu-xml`;
- `verifactu-qr`;
- `verifactu-aeat`;
- `verifactu-testkit`.

The deterministic core must remain usable without the serialization and transport layers.

### KMP Source-Set Contract

v1 support target:

```text
commonMain
commonTest
jvmMain
jvmTest
androidMain
androidUnitTest
iosMain
iosX64Test
iosArm64Test
iosSimulatorArm64Test
macosMain
macosX64Test
macosArm64Test
```

Release documentation must name the concrete targets and test tasks that define support.

Initial CI acceptance:

```text
./gradlew check
./gradlew compileKotlinMetadata
./gradlew jvmTest
./gradlew testDebugUnitTest
./gradlew iosSimulatorArm64Test
./gradlew macosX64Test or macosArm64Test
```

### Public API Principles

- English-first developer-facing API.
- Official Spanish names preserved where they map directly to AEAT protocol terms.
- Immutable public values by default.
- No `Double` or `Float` for fiscal amounts.
- Explicit date/time and decimal types.
- Typed official enumerations.
- No hidden state, hidden network calls, or hidden persistence.
- Raw XML and parsed AEAT response data remain inspectable for debugging.

### Conceptual API

```kotlin
val record = Verifactu.createRegistration(
    invoice = invoice,
    chain = previousChain,
    system = systemInfo,
    generatedAt = timestamp,
)

Verifactu.validate(record).requireValid()

val xml = VerifactuXml.serialize(record)
val qrPayload = VerifactuQr.payload(invoice)
val nextChain = record.chainState
```

### Compliance Behavior

The library must implement:

- caller-owned chain state;
- SHA-256 hashing exactly as defined by AEAT;
- deterministic local validation;
- deterministic AEAT-compatible XML;
- AEAT QR payload generation;
- typed AEAT response parsing;
- flow-control, retry, incidence, rejection, and subsanation semantics.

### Normative Sources

Compliance behavior must be derived from official sources in this priority order:

1. BOE legislation.
2. AEAT technical specifications.
3. AEAT XSD/WSDL and published validation/error definitions.
4. AEAT FAQ for clarification.

Current baseline sources are listed in [docs/compliance/sources.md](docs/compliance/sources.md).

Traceability requirements are listed in [docs/compliance/README.md](docs/compliance/README.md).

---

## 5. Risks & Roadmap

### Phased Rollout

#### MVP: deterministic core

- Gradle KMP project structure.
- `verifactu-core`.
- Typed fiscal models for `RegistroAlta`.
- Chain-state model.
- AEAT hash input construction and SHA-256 hashing.
- Local validation framework.
- QR payload generation.
- Common golden tests.
- JVM, Android, iOS simulator, and macOS compile/test CI.

#### v0.5: XML and schema conformance

- `verifactu-xml`.
- Deterministic XML serialization for registration and cancellation.
- Request XML and response XML parsing.
- SOAP fault parsing.
- Pinned schemas and fixtures.
- Compliance traceability matrix.

#### v0.8: AEAT transport and response semantics

- `verifactu-aeat`.
- Test/production environment selection.
- Certificate credential abstraction.
- JVM/Android/Apple transport adapters.
- Batch submission.
- Typed submission results.
- Flow-control, unknown-delivery, duplicate retry, incidence, and subsanation support.

#### v1.0: production-ready open-source release

- Complete v1 VERI*FACTU mode coverage for pinned baseline.
- Full docs and examples.
- Component declaration package/template.
- Maven Central release automation.
- Public API, security, and dependency/license review.
- Release notes with compliance baseline.

### Technical Risks

- Regulatory drift before v1.
- KMP XML tooling differences across targets.
- Certificate/TLS platform differences.
- Users assuming the library makes a complete SIF compliant.
- Weak open-source trust if tests, docs, release automation, or license hygiene are incomplete.
- Host applications generating concurrent records against the same chain head.
- Normal CI depending on live AEAT availability.

### v1 Acceptance Criteria

v1.0.0 is product-ready only when:

1. Apache-2.0 licensing and dependency license checks are in place.
2. Advertised KMP targets compile and run relevant tests in CI.
3. Registration and cancellation records cover the pinned VERI*FACTU schema baseline.
4. Deterministic AEAT validation rules selected for v1 are implemented and traced.
5. Official/reference hash vectors pass exactly on supported targets.
6. Chaining works across registration and cancellation records.
7. Deterministic XML validates against pinned AEAT schema in JVM CI.
8. QR payloads match the pinned AEAT QR specification.
9. Test and production AEAT submission environments are supported.
10. Certificate-authenticated SOAP submission works on advertised transport targets.
11. Global and per-record AEAT results are typed.
12. Lost-response retry and duplicate/already-known semantics are tested.
13. Flow control and technical-incidence support are represented without library-owned scheduling, queues, or UI.
14. Downstream applications can test integrations without live AEAT credentials.
15. Component declaration responsibilities are reviewed for the release.
16. Exact official source baseline and AEAT technical document versions are recorded.
17. README, docs, examples, KDoc, changelog, contribution guide, and security policy are release-ready.
18. Maven Central publishing and GitHub release are automated by CI.

### Open Questions

- Which organization/person is the legal producer/maintainer name for release artifacts and declaration templates?
- Which Apple targets are release-critical for v1?
- Should `verifactu-aeat` guarantee live submission support on iOS/macOS in v1?
- What minimum Kotlin, Gradle, Android Gradle Plugin, iOS deployment, and macOS deployment versions should be supported?
- Should examples include a minimal host-owned persistence/queue sample?
