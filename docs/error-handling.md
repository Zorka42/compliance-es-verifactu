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

## AEAT Catalogue Semantics

Known AEAT error codes are exposed as a typed incidence with its catalogue source, disposition, and whether the catalogue expressly requires subsanation. Unknown codes remain typed with an `UNKNOWN` disposition and no inferred correction or retry. An invoice identity and the operation returned in a response line are correlation data; neither is treated as acceptance.

## Unicode and XML Lengths

The W3C XML Schema `length` facets are defined in characters. The JVM provider used to validate the published AEAT XSD measures a supplementary Unicode character as two UTF-16 units at these boundaries. Common validation deliberately uses the same UTF-16 unit count on JVM, Android, and Apple Kotlin targets, so it never accepts text that the supported JVM schema fixture rejects. JVM fixture tests record that behaviour; production code does not configure a global XML provider and Android/Apple do not rely on a platform XSD validator.

## Diagnostics

Core functionality should not log by default.

Raw XML logging must be opt-in because XML can contain personal and fiscal data.

Credentials, certificate bytes, private keys, and secrets must never appear in normal logs, exceptions, or string representations.
