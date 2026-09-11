# Changelog

This project follows Semantic Versioning.

## Unreleased

### Added

- Exact fiscal arithmetic checks for charged tax, simplified-invoice limits and total-consistency warnings, with archived-source boundary fixtures.
- Explicit receipt-date context shared by record validation, creation and batch/submission preparation; existing Java overloads remain available.

- Product requirements document for an Apache-2.0 VERI*FACTU Kotlin Multiplatform library.
- Open-source repository requirements.
- Initial developer documentation structure under `docs/`.
- Contribution, acknowledgement, changelog, and security documents.
- Five Gradle Kotlin Multiplatform modules with explicit APIs, lint/static-analysis gates, API baselines, coverage, and Dokka.
- Structural fiscal value/record validation, registration/cancellation hashing and chain-state outputs, deterministic XML and QR payloads.
- Namespace-aware AEAT response/fault parsing, preserved identities/operations/duplicates/unknown states, typed flow control and pure response correlation.
- Validated batch/SOAP preparation and a pure record/XML/QR preparation facade.
- Local issued/received invoice query CLI, archived public contracts with digest checks, and unsigned component declaration guidance.
- Endpoint metadata, JVM transport and Android adapter boundary.
- Apple adapter injection boundary with shared iOS/macOS tests; no built-in credential access.
- Synthetic KMP testkit with record/XML/QR fixtures, response scenarios, and scripted transport.
- Executable Kotlin registration/cancellation flow and Java consumer, plus a separate build consuming local published JVM artifacts.
- Unsigned local Maven publications with POM metadata, source JARs, and Dokka JARs.
- Guarded Maven Central Portal release workflow with in-memory artifact signing and GitHub Release creation; it remains inactive until dedicated release credentials are configured.
- Protected `main` governance with required cross-platform CI checks.

### Changed

- Publication group aligned with planned `io.github.zorka42` coordinates; packages remain `dev.verifactu`.
- JVM publications target Java 11, and common factory/parser/serializer entry points expose Java static methods. Public API baselines change before the first release.
- README, integration docs, and KDoc now describe implemented APIs and their current validation/parser limitations.

### Fixed

- Replaced regex response extraction with bounded platform XML readers; reject unsafe XML and preserve every identifiable response line.
- Validate timestamp calendar/offsets, exact Unicode field lengths, XML characters and schema regime codes; snapshot factory inputs and preserve CR in XML.
- Redact diagnostic summaries, classify not-sent versus unknown delivery, and gate sample acceptance on matching response identities/operations.
- Dokka tasks are marked incompatible with Gradle configuration cache so their documentation/publication builds can complete.
- JVM schema tests preload vendored schemas and disable external resolution, removing an implicit W3C DTD dependency without changing the pinned schema files.

### Notes

- No Maven Central release exists. Local previews are unsigned and not production-ready.
- Real AEAT verification, credentialed Central publication, producer review, and an integrating application's durable deployment remain pending.
- The repository is not production-ready for VERI*FACTU compliance.
