# Protocol Behavior

## VERI*FACTU Mode

v1 targets VERI*FACTU mode only.

This page distinguishes the product target from the current pre-release implementation. See [implementation status](implementation-status.md) for gaps and [the local sample](../samples/offline/README.md) for executable behavior.

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

Shared record golden tests and JVM record/batch/response XSD checks use archived public contracts. `SubmissionBatchBuilder` validates currently modeled records and builds SOAP 1.1 document/literal requests. Runtime response parsing enforces XML well-formedness and namespace identity while preserving unknown protocol values; it does not claim full XSD or business-rule validation.

## QR

The library should expose the AEAT QR payload or verification URL.

Invoice layout and QR rendering belong to the host application unless an optional renderer module is added later.

## Flow Control

AEAT may return a wait value for subsequent submissions.

The parser exposes `AeatFlowControl.Known` or `Unknown`, preserving unrecognized text, alongside the `retryAfterSeconds` convenience getter. The host application owns scheduling and queueing; the library never sleeps or retries automatically.
