# Getting Started

VeriFactu KMP is currently a specification-first repository. No installable artifact exists yet.

This page documents the intended first-user path for the initial implementation.

## Intended Installation

The planned release target is Maven Central.

Expected module split:

```kotlin
implementation("io.github.zorka42:verifactu-core:<version>")
implementation("io.github.zorka42:verifactu-xml:<version>")
implementation("io.github.zorka42:verifactu-qr:<version>")
implementation("io.github.zorka42:verifactu-aeat:<version>")
```

## Intended Minimal Flow

1. Build or map invoice data into the library's typed fiscal input model.
2. Load the caller-owned previous chain state.
3. Create a registration or cancellation record.
4. Validate the record locally.
5. Serialize XML or build a submission request.
6. Generate the QR payload for invoice rendering.
7. Persist the returned next chain state in the host application.
8. Submit records to AEAT if the host application uses VERI*FACTU remittance through this library.

Target API shape:

```kotlin
val record = Verifactu.createRegistration(
    invoice = invoice,
    chain = previousChain,
    system = systemInfo,
    generatedAt = timestamp,
)

val report = Verifactu.validate(record)
report.requireValid()

val xml = VerifactuXml.serialize(record)
val qrPayload = VerifactuQr.payload(invoice)
val nextChain = record.chainState
```

## Current Limitations

- No Kotlin modules exist yet.
- No Maven artifact exists yet.
- No live AEAT client exists yet.
- API examples are product targets until implementation starts.
