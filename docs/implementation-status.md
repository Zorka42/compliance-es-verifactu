# Implementation Status

Local assessment and implementation batches: 2026-09-06. **The offline foundation, review fixes, validated preparation, response handling and query utility are locally verified. External acceptance and release remain open.** The authoritative backlog remains [Zorka Accounting – Tasks](https://app.notion.com/p/3bd4f302230980d3bb72ffbd0068aa8b), project `Verifactu`.

## Starting point

The checkout contained five KMP modules with core records/hash/validation, XML, QR, minimal response extraction, and JVM/Android transport boundaries. README/getting-started still described a specification-only repository; testkit was empty. Notion contained 52 Verifactu tasks: 30 `To verify` (ZA-42–71), 22 `To do`, none externally verified as `Done`.

A `To verify` status was not treated as evidence that every acceptance criterion had passed. This run found and fixed a pre-existing common-metadata compilation failure in the response parser and an external W3C DTD dependency in JVM schema tests. It also identified production gaps listed below.

Notion was read but not modified; no task was marked Done. The first implementation package was committed as `29106f9`, merged into main as `1ddab07` and pushed after explicit authorization. Subsequent review/query work uses the same local-only operational boundary: no AEAT SOAP endpoint, authenticated portal or personal certificate/key access. Public static contracts were retrieved and archived. Gradle builds use the existing cache with `--offline`; no Maven Central release was made.

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
| [ZA-85 Maven publishing and signed release](https://app.notion.com/3bd4f302230981d7a36df58e66723e9b) | Partial; keep open | Unsigned JVM preview, all-module POM configuration, sources/Dokka/license packaging, independent POM-based Java consumer; signing/Central/tag release not configured |
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
| OSS release hardening | Java 11 runtime consumer configured in CI; 14 archived source files with hash verification | Signing/Central publishing, actual remote CI verification, branch settings and release readiness |

The code/architecture review retained the five-module structure. It fixed XML-invalid text, serialized-length checks, calendar/offset validation, regime membership, CR preservation, mutable factory input/output lists, unescaped header identifiers, unsafe diagnostic summaries, dropped response lines and sample invoice mismatches. The response parser uses platform readers behind a common interpretation boundary; core still performs no I/O.

## Latest Local Verification

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

See [the complete release and testing plan](release-plan.md) for the ordered work, required credentials and evidence at each stage. The immediate offline priorities are complete conditional fiscal models, catalogue-backed incidence/correction behavior, final workflow/API review and the app adapter. Existing constructors are intentionally low-level; not every supported XML field or fiscal rule is modeled.

After those local contracts are ready, exercise transports with disposable loopback test identities. Real AEAT test calls, actual issued/received history and personal certificates require the separate operational discussion requested by the owner. The query utility is ready for local preparation and saved-response inspection only; it has not retrieved this taxpayer's invoices.

Maven Central publication needs its own namespace/credentials/signing setup; producer/component declaration review and repository governance remain release prerequisites. None of these stages is reported as Done from local tests alone.
