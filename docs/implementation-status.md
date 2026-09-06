# Implementation Status

Local assessment and implementation batch: 2026-09-06. **Nine selected tasks are implemented and locally verified; Maven publishing is partially implemented.** The authoritative backlog remains [Zorka Accounting – Tasks](https://app.notion.com/p/3bd4f302230980d3bb72ffbd0068aa8b), project `Verifactu`.

## Starting point

The checkout contained five KMP modules with core records/hash/validation, XML, QR, minimal response extraction, and JVM/Android transport boundaries. README/getting-started still described a specification-only repository; testkit was empty. Notion contained 52 Verifactu tasks: 30 `To verify` (ZA-42–71), 22 `To do`, none externally verified as `Done`.

A `To verify` status was not treated as evidence that every acceptance criterion had passed. This run found and fixed a pre-existing common-metadata compilation failure in the response parser and an external W3C DTD dependency in JVM schema tests. It also identified production gaps listed below.

All changes are local. Notion was read but not modified. No commit, push, external publication, AEAT interaction, or personal certificate/key access was part of this work. Local Gradle caches and toolchains were used with `--offline`.

## Selected ten-task batch

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

The sample composes module APIs directly; it does not close ZA-89 (production workflow facade), ZA-90 (persistence orchestration), or ZA-91 (complete end-to-end runtime contract suite). The published preview likewise does not complete ZA-85's external criteria.

## Verification

- Initial JVM and Android tests passed before changes.
- `ktlintCheck`, `detekt`, `apiCheck`, JVM/Android tests and `koverVerifyJvm` passed after implementation.
- `compileKotlinMetadata`, `compileKotlinIosArm64`, `iosSimulatorArm64Test`, and `macosArm64Test` passed after replacing the platform-only regex option.
- Both Kotlin and Java sample commands passed. The independent Java build resolved local Maven POMs without project dependencies or Gradle metadata redirection and compiled with `--release 11`, `-Xlint:all`, and `-Werror`.
- Final `./gradlew --offline --no-daemon check publishJvmPreview` passed (671 tasks, 327 executed). The separate POM-based Java consumer also passed.
- Final test reports contain 39 JVM tests, 35 Android debug tests, 35 Android release tests, 36 iOS simulator tests, and 36 macOS tests: zero failures or skipped tests in those reports. Configured unsupported Intel-native tasks can be skipped by Gradle; they are not included in that runtime claim.
- All five JVM publications were inspected for POM license/SCM/developer metadata, Java 11 bytecode, source files, Dokka `index.html`, and `META-INF/LICENSE` in runtime/source/documentation JARs.
- Registration/cancellation testkit XML passes the vendored record XSD. Both schema suites preload the local schemas and disable external DTD/schema access. Vendored source bytes were not changed.
- `git diff --check` and all relative documentation links passed local inspection.
- Core JVM line coverage: 400/440 lines (90.9%), above the configured 90% gate. Coverage is not evidence of complete fiscal compliance.

Dokka 2.0 V1 tasks are marked incompatible with configuration cache. Builds may report cache-discard diagnostics for those tasks; this is not a successful cache reuse claim. The normal compilation/test configuration can still use the cache.

## Next work toward a release

1. **Pin missing official sources.** Only the record/common schema and its XML Signature dependency are vendored. Batch/response schemas, WSDL, submission specification, and error catalogue need an explicit reviewed offline baseline. No source refresh occurred during this run.
2. **ZA-73–77 runtime correctness.** Implement a typed validated batch builder, complete per-record response correlation, XML namespace/well-formedness handling, explicit not-sent versus unknown-delivery states, conservative duplicate handling, typed flow control, and source-backed correction/incidence rules.
3. **Complete fiscal models and validation.** NIF checks currently enforce length, timestamps enforce format only, and conditional F1/F3/R1–R5 fields/validation are incomplete. Public record constructors and caller-mutable lists can bypass the intended validated/immutable record boundary. QR restrictions and amount representations need review against the pinned sources.
4. **ZA-89–91 integration API.** Compose validated records and artifacts into a production workflow, with an explicit persistence-before-delivery contract and full failure-branch tests. No hidden storage, queueing, or sleeping.
5. **ZA-85/86/88 external release.** Confirm namespace ownership, configure dedicated signing and Central publishing in CI, protect/review release branches, recheck the legal/technical baseline, verify real transport separately, and execute release readiness. These actions require a separate authorized session.

Production transport, certificate handling, live acceptance, on-device behavior, and a Java 11 runtime (as opposed to Java 11 compilation) remain unverified. The current XML extractor can omit unsupported response lines and must not be used for production reconciliation.
