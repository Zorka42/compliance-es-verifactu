# Protocol Behavior

## VERI*FACTU Mode

v1 targets VERI*FACTU mode only.

The library should support:

- registration records;
- cancellation records;
- SHA-256 hash generation;
- fiscal-record chaining;
- deterministic XML;
- QR payload generation;
- AEAT submission request/response handling;
- flow-control, retry, incidence, rejection, and subsanation semantics.

## NO VERI*FACTU Mode

NO VERI*FACTU mode is out of scope for v1.

v1 does not implement:

- event-record generation;
- event-record persistence;
- NO VERI*FACTU export/conservation machinery;
- XAdES signing required specifically for NO VERI*FACTU fiscal/event records.

## Hashing

Hashing must follow the official AEAT hash specification exactly.

Implementation requirements include:

- exact field selection;
- exact field ordering;
- deterministic canonical input construction;
- UTF-8;
- deterministic decimal and date formatting;
- no locale-sensitive formatting;
- no platform-default charset.

## XML

XML generation must be deterministic and compatible with the pinned AEAT schemas for the release.

JVM schema validation is expected to be part of CI once code exists. Cross-target tests should still verify deterministic serialization behavior where platform XML tooling differs.

## QR

The library should expose the AEAT QR payload or verification URL.

Invoice layout and QR rendering belong to the host application unless an optional renderer module is added later.

## Flow Control

AEAT may return a wait value for subsequent submissions.

The library should expose this value, but the host application owns scheduling and queueing.
