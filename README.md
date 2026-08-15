# VeriFactu KMP

Apache-2.0 Kotlin Multiplatform library for building VERI*FACTU fiscal-record, XML, QR, and AEAT submission functionality into invoicing software.

## Status

Draft/pre-implementation. The repository currently defines the product and open-source documentation structure before the Kotlin modules are created.

Do not use this repository for production VERI*FACTU compliance yet.

## Installation

No Maven Central artifact has been released.

Planned coordinates:

```kotlin
implementation("io.github.zorka42:verifactu-core:<version>")
implementation("io.github.zorka42:verifactu-xml:<version>")
implementation("io.github.zorka42:verifactu-qr:<version>")
implementation("io.github.zorka42:verifactu-aeat:<version>")
```

## Quick Start

The target integration flow is:

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

This API is a product target, not a released implementation.

## Main Capabilities

- VERI*FACTU `RegistroAlta` and `RegistroAnulacion` modeling.
- Caller-owned fiscal-record chain state.
- SHA-256 hash input construction and hashing.
- Deterministic local validation.
- Deterministic AEAT XML serialization and response parsing.
- AEAT QR payload generation.
- Optional AEAT SOAP submission client.
- Typed flow-control, retry, incidence, and response semantics.
- KMP support target: `commonMain`, `jvmMain`, `androidMain`, `iosMain`, and `macosMain`.

## What This Library Does Not Do

VeriFactu KMP is not a complete Sistema Informatico de Facturacion.

It does not provide a database, invoice storage, durable queue, background worker, certificate store, user interface, invoice PDF renderer, accounting system, hosted API, or legal/tax advice.

## Documentation

- [Product requirements](PRODUCT_SPEC.md)
- [Open-source repository requirements](OPEN_SOURCE_REQUIREMENTS.md)
- [Developer documentation index](docs/README.md)
- [Getting started](docs/getting-started.md)
- [Core concepts](docs/core-concepts.md)
- [Integration flow](docs/integration-flow.md)
- [Protocol behavior](docs/protocol-behavior.md)
- [Error handling](docs/error-handling.md)
- [Integration responsibilities](docs/integration-responsibilities.md)
- [Compliance sources](docs/compliance/sources.md)
- [Compliance traceability](docs/compliance/README.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

Compliance-sensitive changes must cite the relevant official BOE or AEAT source.

## License

Apache-2.0. See [LICENSE](LICENSE).
