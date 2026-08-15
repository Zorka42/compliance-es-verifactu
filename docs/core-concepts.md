# Core Concepts

## Library Boundary

VeriFactu KMP is a reusable VERI*FACTU protocol component.

It helps applications generate and submit fiscal records, but it does not make the complete host application compliant by itself.

## Deterministic Core

The deterministic core should produce the same output for the same inputs:

- fiscal record;
- hash input;
- SHA-256 hash;
- validation report;
- XML where serialization is deterministic;
- QR payload.

The core must not perform hidden I/O.

## Chain State

Fiscal-record chain state belongs to the host application.

The library should accept either:

- `First`, when the chain has no previous record; or
- a compact reference to the previous record.

After creating a record, the library should return the next chain state so the host application can persist it.

The host application must serialize record creation for each independent SIF/taxpayer chain.

## Records

v1 targets VERI*FACTU mode only:

- `RegistroAlta`;
- `RegistroAnulacion`.

Registration and cancellation records participate in the same chronological chain.

Cancellation is not deletion. Previously generated records remain immutable.

## KMP Source Sets

The target support matrix is:

```text
commonMain
jvmMain
androidMain
iosMain
macosMain
```

The deterministic fiscal core belongs in `commonMain` wherever practical.

Platform-specific source sets should be limited to:

- networking;
- TLS/client certificate integration;
- credential provider adapters;
- cryptographic provider plumbing where unavoidable;
- native interoperability helpers.
