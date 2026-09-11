# Implementation Status

Local assessment updated 2026-09-11; earlier implementation batches are recorded below. **External acceptance and release remain open.** The authoritative backlog remains [Zorka Accounting – Tasks](https://app.notion.com/p/3bd4f302230980d3bb72ffbd0068aa8b), project `Verifactu`.

## Offline validation and candidate preparation — 2026-09-11

ZA-113 adds exact charged-tax/sign/tolerance checks, the F2 limit and warning-only total consistency. ZA-114 introduces explicit AEAT receipt-date context through factory, batch and facade APIs while retaining unknown history/census boundaries. ZA-115 adds source-backed fiscal identifier validation without narrowing the low-level XML value parsers. The known zero-rate surcharge discrepancy, entity/K/L/M checksum gap and special VAT-country mapping assumptions remain visible in reports and documentation.

The real app-accounting integration is developed separately under ZA-116, using its SQLite transaction and an explicitly enabled local VeriFactu source profile. It does not activate the existing desktop issue action without fiscal configuration. ZA-117 prepares a local candidate and independent Java consumption; it does not publish to Central. Current evidence and remaining work are in [the release plan](release-plan.md).

All fifteen previously archived public contracts were [refreshed without byte changes](compliance/source-refresh-2026-09-11.md). Additional BOE identifier sources and an explicitly marked Interior checksum fact snapshot are preserved with digests.

## Previous five-task acceptance review — 2026-09-11

The worktree was advanced from `8339528` to the existing `main` commit `51e2321` before reviewing ZA-106–110. Existing `To verify` labels were checked against code and acceptance criteria; they did not establish correctness. Review found invalid conditional XML ordering, mutable conditional lists, a UTF-16 length regression, incorrect correction-response matching, context-free error classification and a response-body timeout gap.

| Task | Implementation and regression evidence |
| --- | --- |
| ZA-106 conditional records | F1/F3/R1–R5 fields, source-backed conditional tax rules, immutable factory snapshots for recipients/replacements/rectifications, schema list limits and country enumeration; conditional records validated against the archived XSD |
| ZA-107 incidence/correction catalogue | Explicit record/header/unknown error location, inspectable unknown codes and redacted summaries; correlation compares submitted subsanation/prior-rejection flags independently from acceptance |
| ZA-109 Unicode compatibility | W3C character counts in common validation; supplementary/BMP boundaries, provider probes against official XSD and both query roles; no global provider property changes |
| ZA-108 loopback mTLS | Disposable local identities; client identity, SOAP headers, bounded responses and timeout covering the complete body, including a server that stalls after sending headers |
| ZA-110 application adapter contract | Finalized data mapping, stale-head comparison, unique finalization, validation failure without persistence, unknown delivery replay and restart after failed response persistence; the Java consumer also exercises an F1 recipient snapshot |

These are library and sample contracts. The real app-accounting database is not wired by this repository's sample. Its inspected `1d54f4f` issue transaction and remaining data requirements are documented in [the adapter contract](app-accounting-adapter-contract.md). Further arithmetic checks, identifier semantics, external validation context and actual app transaction integration are tracked as ZA-113–116 in [the release plan](release-plan.md). No task is `Done` solely from local verification.

### Verification for this batch

- `./gradlew --offline --no-daemon ktlintFormat check compileKotlinMetadata compileKotlinIosArm64 koverXmlReportJvm koverVerifyJvm publishJvmPreview --continue --console=plain` passed: **493 tasks, 341 executed**. This includes static analysis, versioned JVM API checks, compile-tested Kotlin/Java examples, archived contract digests, XML schemas and disposable loopback mTLS.
- Fresh reports contain **166 JVM, 126 Android host, 139 iOS simulator and 139 macOS tests**, with zero failures, errors or skipped tests in those reports. Intel Apple execution and Android device XSD/transport behavior are not covered by these counts.
- Core JVM line coverage is **1341/1349 (99.4%)**, above the unchanged 90% gate. Coverage is not a completeness or compliance claim.
- The independent `samples/maven-consumer` build and run passed against the newly published local artifacts, including F1 construction and Java mutation protection. Compilation targets Java 11; this local run used JDK 22. Java 11 runtime verification remains configured in CI.
- The selected JAXP provider reported `com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory`, profile `UTF16_UNITS`. The archived-schema probe and both query roles verified that profile, while common validation retained W3C character counts.
- Dokka completed successfully but caused configuration-cache discard warnings. No successful configuration-cache reuse or external Maven publication is claimed.

## Starting point

The checkout contained five KMP modules with core records/hash/validation, XML, QR, minimal response extraction, and JVM/Android transport boundaries. README/getting-started still described a specification-only repository; testkit was empty. Notion contained 52 Verifactu tasks: 30 `To verify` (ZA-42–71), 22 `To do`, none externally verified as `Done`.

A `To verify` status was not treated as evidence that every acceptance criterion had passed. This run found and fixed a pre-existing common-metadata compilation failure in the response parser and an external W3C DTD dependency in JVM schema tests. It also identified production gaps listed below.

The first implementation package was committed as `29106f9`, merged into main as `1ddab07` and pushed after explicit authorization. Later offline batches moved ZA-77, ZA-108, and ZA-110 to `To verify`; no task has been marked `Done` solely from local work. Subsequent review/query work uses the same local-only operational boundary: no AEAT SOAP endpoint, authenticated portal or personal certificate/key access. Public static contracts were retrieved and archived. Gradle builds use the existing cache with `--offline`; no Maven Central release was made.

## Release Readiness Update

`main` now requires the Linux/JVM/Android, Apple, CodeQL, and static-analysis GitHub checks through a pull request; force-push and deletion are disabled. A tag-only Central Portal release workflow and in-memory PGP signing configuration are present, and a local disposable-key signing test verifies the JVM publication path. Namespace ownership, the dedicated signing identity, CI secrets, Central staging/release, and producer approval remain external verification steps. They do not access AEAT and have not been attempted.

## First Ten-Task Batch

“Implemented locally” is a handoff recommendation for external verification, not `Done`. The final aggregate check is recorded below separately.

| Task | Local outcome | Evidence |
| --- | --- | --- |
| [ZA-72 Apple transport strategy](https://app.notion.com/3bd4f302230981a5839ad80f18fd1b68) | Implemented locally | `AppleAeatTransport`, `appleTest`, [Apple responsibilities](apple-aeat-transport.md); injection contract only |
| [ZA-78 Testkit](https://app.notion.com/3bd4f302230981909373f704da5f1b04) | Implemented locally | Synthetic record/chain/XML/QR/response fixtures, scripted fake, common tests, local record-XSD checks |
| [ZA-79 Supported source sets and smoke tests](https://app.notion.com/3bd4f3022309810ebfaeec0e8845ea35) | Implemented locally for the advertised matrix | Metadata/iOS-device compilation, JVM/Android/iOS-simulator/macOS tests, [matrix](platform-support.md), CI |
| [ZA-80 README](https://app.notion.com/3bd4f302230981dd9e40e46b4767f95e) | Implemented locally | Real API/example links, pending release coordinates, CI/coverage/license badges; no fabricated Maven version badge |
| [ZA-81 Integration docs](https://app.notion.com/3bd4f30223098188bec3f918201d1934) | Implemented locally | Getting started, core concepts, integration/error flow, platform/testkit/publication guidance, explicit production gaps |
| [ZA-83 OSS documents](https://app.notion.com/3bd4f3022309817caab9e5b717f6b85f) | Implemented locally | Toolchain/check instructions, meaningful changelog, security reporting guidance, existing Apache-2.0 license |
| [ZA-84 Compile-tested examples](https://app.notion.com/3bd4f30223098100ae00dfc9be6ecf6c) | Implemented locally | Shared Kotlin flow, Java 11 consumer, testkit-only fake submission, CI sample runs |
| [ZA-85 Maven publishing and signed release](https://app.notion.com/3bd4f302230981d7a36df58e66723e9b) | Prepared; keep open | Unsigned JVM preview, POM/source/Dokka/license packaging, guarded Central Portal tag workflow and in-memory signing configuration; namespace, credentials and first external release remain unverified |
| [ZA-87 English API/KDoc pass](https://app.notion.com/3bd4f302230981feac88ce0e57120874) | Implemented locally | Java static entry points, corrected validation/immutability/parser promises, explained official terms and host responsibilities, regenerated API baselines |
| [ZA-92 Runnable consumer](https://app.notion.com/3be4f302230981739dc9d776234e69b7) | Implemented locally | Registration → XML/QR → fake response, cancellation with continuous chain, deterministic result output, CI execution |

## Review and Query Batch

| Backlog area | Current local result | Still open |
| --- | --- | --- |
| ZA-73 validated batches | Typed header/count/issuer/draft/hash validation, snapshots, deterministic XML and SOAP; XSD checks | Additional fiscal fields remain part of model completeness |
| ZA-74 response model | Namespace-aware parsing, every readable identity/operation, declared and earlier duplicate states, explicit unknowns, pure matching | Full incidence/business outcome policy |
| ZA-75 flow control | Known wait seconds and preserved unknown values; no sleeping or hidden retries | Live interoperability evidence |
| ZA-76 delivery uncertainty | Known preflight not-sent versus ambiguous send failure; immutable replay and correlation tests | Application retry/recovery policy with durable evidence |
| ZA-82 declaration materials | Archived official sources, component/host guidance, unsigned worksheet, dependency acknowledgements | Final producer identity, release evidence and declaration review |
| ZA-89 preparation facade | Pure registration/cancellation/batch preparation with record, next head, XML/QR/SOAP | Final full-scope runtime API review and missing fiscal modes |
| ZA-90/91 integration contracts | Shared Kotlin and Java examples, typed attempt interpretation, matching before acceptance, fake failure branches and manual replay | Durable app integration, real adapters and full incidence branches |
| Query utility (issued and received) | JVM CLI prepares XSD-validated SOAP and inspects saved pages; no network/certificate code | Explicitly authorized live read, selected identity/period/credential |
| OSS release hardening | Java 11 runtime consumer configured in CI; archived source files with hash verification; protected `main` and guarded release workflow | First signed Central release, dependency/license audit, producer review and release readiness |

The code/architecture review retained the five-module structure. It fixed XML-invalid text, serialized-length checks, calendar/offset validation, regime membership, CR preservation, mutable factory input/output lists, unescaped header identifiers, unsafe diagnostic summaries, dropped response lines and sample invoice mismatches. The response parser uses platform readers behind a common interpretation boundary; core still performs no I/O.

## Historical Local Verification — 2026-09-06

- `./gradlew --offline --no-daemon ktlintFormat check compileKotlinMetadata compileKotlinIosArm64 koverXmlReportJvm koverVerifyJvm publishJvmPreview --continue` passed: **776 tasks, 408 executed**.
- `check` includes formatting/static analysis, API baselines, JVM/Android/Apple tests, Kotlin/Java samples and platform checks configured by the project. The separate metadata/device compilation and coverage gates also passed.
- Reports contain **100 JVM, 76 Android debug, 76 Android release, 83 iOS simulator and 83 macOS tests**, with zero failures, errors or skipped tests in those reports. Configured Intel-native tasks can be skipped and are not covered by this claim.
- Core JVM line coverage: **456/488 (93.4%)**, above the 90% gate. This is test coverage, not complete fiscal compliance.
- `./gradlew --offline --no-daemon -p samples/maven-consumer run` passed against the newly built POM-based JVM artifacts with Java 11 source/bytecode constraints, including Java getter-mutation protection and response matching. Local runtime was JDK 22; CI explicitly selects Temurin 11 for the independent consumer, whose remote run remains to be verified.
- Query tests cover both roles, XSD validation, stored states, sparse cancellation, paging, unsafe XML, local file behavior and all archived SHA-256 entries. The documented CLI inspection command runs against the committed received-page fixture.
- Full batch and synthetic submission-response XSD tests pass with external resolution disabled. The normative Unicode boundaries remain in core tests; the JAXP supplementary-character length limitation is explicit in the test and [release plan](release-plan.md).
- Maven preview includes the five JVM modules, sources, Dokka and POM metadata. API baselines reflect the pre-release additions. External publishing/signing did not run.

Dokka 2.0 V1 tasks remain incompatible with configuration cache. Their successful build reports cache-discard warnings; this is not a successful cache reuse claim.

## Remaining Plan

See [the complete release and testing plan](release-plan.md) for the ordered work, required credentials and evidence at each stage. The next offline priorities are application fiscal configuration/activation, durable receipt correlation, remaining source ambiguities, and broader parser/API regression coverage. Existing constructors are intentionally low-level; local validation is not a complete AEAT or legal compliance decision.

Loopback transport checks use fresh disposable test identities. Real AEAT test calls, actual issued/received history and personal certificates require the separate operational discussion requested by the owner. The query utility prepares requests and inspects saved responses locally; it has not retrieved this taxpayer's invoices.

Maven Central publication needs its own namespace/credentials/signing setup; producer/component declaration review and repository governance remain release prerequisites. None of these stages is reported as Done from local tests alone.
