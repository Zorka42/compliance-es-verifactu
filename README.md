# VeriFactu KMP

[![CI](https://github.com/Zorka42/compliance-es-verifactu/actions/workflows/ci.yml/badge.svg)](https://github.com/Zorka42/compliance-es-verifactu/actions/workflows/ci.yml)
[![Coverage](https://codecov.io/gh/Zorka42/compliance-es-verifactu/graph/badge.svg)](https://codecov.io/gh/Zorka42/compliance-es-verifactu)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

Apache-2.0 Kotlin Multiplatform building blocks for VERI*FACTU fiscal records, hashes, XML, QR payloads, and optional AEAT integration, usable from Kotlin and Java.

## Status

**Pre-release implementation; not ready for production fiscal compliance.** Five modules are implemented and a runnable offline consumer exercises record creation, chaining, XML, QR, fake submission, and response parsing. No Maven Central version has been released.

Validated batch/SOAP preparation, a local workflow facade, namespace-aware response parsing and response correlation are implemented. Real AEAT verification and the first credentialed signed release remain pending. See [implementation status](docs/implementation-status.md).

## Installation

The publication group is `io.github.zorka42`; source packages remain `dev.verifactu`.

After the first release, Kotlin Multiplatform consumers will use the root module coordinates below. `<version>` is a placeholder, not an available release:

```kotlin
implementation("io.github.zorka42:verifactu-core:<version>")
implementation("io.github.zorka42:verifactu-xml:<version>")
implementation("io.github.zorka42:verifactu-qr:<version>")
implementation("io.github.zorka42:verifactu-aeat:<version>")
```

Java/Maven consumers use the `-jvm` artifacts and Java 11 or newer. Local unsigned JVM publications can already be built and consumed from `build/maven-repository`; see [publishing](docs/publishing.md) and the [independent Java consumer](samples/maven-consumer/build.gradle.kts).

## Quick start

With JDK 21 and Android SDK 36 configured, run from the checkout:

```bash
./gradlew :samples:offline:runKotlinSample
./gradlew :samples:offline:runJavaSample
```

Both examples use synthetic inputs and a fake transport. They require no certificates and make no network submissions. Add `--offline` when build dependencies are already cached.

The [shared Kotlin example](samples/offline/src/commonMain/kotlin/dev/verifactu/sample/OfflineExampleResult.kt) constructs typed inputs and demonstrates registration followed by cancellation. The [Java example](samples/offline/src/jvmMain/java/dev/verifactu/sample/JavaExample.java) uses static factory/parser methods and typed result getters. Both compile and run in CI; they are the canonical quickstart sources.

## Modules

| Module | Provides |
| --- | --- |
| `verifactu-core` | Fiscal values, structural validation, alta/anulación records, SHA-256, caller-owned chain state; no I/O |
| `verifactu-xml` | Deterministic record XML, validated batch/SOAP preparation; archived XSD tests |
| `verifactu-qr` | Test/production QR URL payloads as data; no image rendering |
| `verifactu-aeat` | Local preparation facade, typed response/fault parsing and correlation, optional transport adapters |
| `verifactu-testkit` | Synthetic fixtures and scripted fake transport for downstream tests |

JVM, Android local unit tests, iOS arm64 simulator, and macOS arm64 have CI test coverage. iOS device code is compiled; on-device execution is not verified. Intel Apple targets remain configured but are not advertised as supported. See the [platform matrix](docs/platform-support.md).

The [offline invoice query tool](tools/query/README.md) prepares issued/received consultations and inspects saved responses without network or certificates. [Public contracts](schemas-aeat/README.md) are archived with source URLs and digests.

## Host application boundary

The library does not provide a database, invoice store, durable queue, scheduler, certificate vault, UI, invoice PDF renderer, accounting system, hosted API, or legal/tax advice. The host owns invoice finalization, durable records, concurrency, credentials, and operating the complete SIF. See the compile-tested [app-accounting adapter contract](docs/app-accounting-adapter-contract.md) for the persistence and replay boundary.

## Documentation and contributing

Start with [getting started](docs/getting-started.md), [integration flow](docs/integration-flow.md), and [error handling](docs/error-handling.md). The [documentation index](docs/README.md) links to platform guidance, testkit, compliance sources, and publication instructions.

Contributions follow [CONTRIBUTING.md](CONTRIBUTING.md). Compliance changes require official BOE/AEAT sources. Licensed under [Apache-2.0](LICENSE); see [acknowledgements](ACKNOWLEDGEMENTS.md).
