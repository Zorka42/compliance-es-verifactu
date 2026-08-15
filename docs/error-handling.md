# Error Handling

The library should expose structured outcomes. A generic `Boolean success` is not enough for VERI*FACTU integration.

## Local Validation

Local validation should return:

- stable library issue code;
- field path;
- severity;
- human-readable message;
- optional AEAT code;
- optional compliance source reference.

Validation can only cover deterministic local rules. AEAT remains authoritative for remote validation.

## Submission Outcomes

Submission results should distinguish:

- accepted records;
- accepted records with errors;
- rejected records;
- SOAP protocol faults;
- transport failures where the request was not sent;
- transport failures with unknown delivery;
- parsed AEAT flow-control information.

## Unknown Delivery

A lost HTTP/SOAP response can mean the request reached AEAT.

The host application should be able to retry the same immutable records and parse duplicate or already-known semantics.

The library must not generate a replacement fiscal record solely because delivery is unknown.

## Diagnostics

Core functionality should not log by default.

Raw XML logging must be opt-in because XML can contain personal and fiscal data.

Credentials, certificate bytes, private keys, and secrets must never appear in normal logs, exceptions, or string representations.
