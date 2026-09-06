# Changelog

This project follows Semantic Versioning.

## Unreleased

### Added

- Product requirements document for an Apache-2.0 VERI*FACTU Kotlin Multiplatform library.
- Open-source repository requirements.
- Initial developer documentation structure under `docs/`.
- Contribution, acknowledgement, changelog, and security documents.
- Five Gradle Kotlin Multiplatform modules with explicit APIs, lint/static-analysis gates, API baselines, coverage, and Dokka.
- Structural fiscal value/record validation, registration/cancellation hashing and chain-state outputs, deterministic XML and QR payloads.
- Minimal AEAT response/fault extraction, endpoint metadata, JVM transport, and Android adapter boundary.
- Apple adapter injection boundary with shared iOS/macOS tests; no built-in credential access.
- Synthetic KMP testkit with record/XML/QR fixtures, response scenarios, and scripted transport.
- Executable Kotlin registration/cancellation flow and Java consumer, plus a separate build consuming local published JVM artifacts.
- Unsigned local Maven publications with POM metadata, source JARs, and Dokka JARs.

### Changed

- Publication group aligned with planned `io.github.zorka42` coordinates; packages remain `dev.verifactu`.
- JVM publications target Java 11, and common factory/parser/serializer entry points expose Java static methods. Public API baselines change before the first release.
- README, integration docs, and KDoc now describe implemented APIs and their current validation/parser limitations.

### Fixed

- Response extraction now uses a common-compatible multiline regex instead of a platform-specific option that broke KMP metadata compilation.
- Dokka tasks are marked incompatible with Gradle configuration cache so their documentation/publication builds can complete.
- JVM schema tests preload vendored schemas and disable external resolution, removing an implicit W3C DTD dependency without changing the pinned schema files.

### Notes

- No Maven Central release exists. Local previews are unsigned and not production-ready.
- Full batch validation, SOAP orchestration, delivery uncertainty, duplicate reconciliation, incident/correction semantics, and signed release automation remain pending.
- The repository is not production-ready for VERI*FACTU compliance.
