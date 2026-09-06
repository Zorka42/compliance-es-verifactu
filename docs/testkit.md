# Testkit

`verifactu-testkit` is a separate KMP artifact for downstream tests. It depends on core, XML, QR, and AEAT modules; production modules never depend on it.

Use `testImplementation` on JVM or a dependency in `commonTest`. Java consumers use `verifactu-testkit-jvm`. Only offline demo applications should put it on a runtime classpath.

## Fixtures

`VerifactuFixtures` builds synthetic F2 registration and cancellation drafts/records, the next chain state, record XML, and a QR URL. Default inputs are fixed, including time and amount. `registration(previousHead, number)` supports sequential test records; copy a draft to test invalid business inputs.

These fixtures pass current local structural validation; their registration/cancellation XML is also checked against the vendored record XSD on JVM with external resolution disabled. They are not real invoices, independently certified AEAT examples, or proof of remote acceptance. Do not send them to any live service.

## Scripted transport

Construct `FakeAeatTransport` with a list of `AeatTransportResult` values. Each `execute` call records its request and returns the next result. Input scripts and exposed request histories are snapshots. Exhaustion fails the test with `IllegalStateException`; it never invents a successful result or contacts an endpoint.

Use one instance per test. It has instance-local mutable state and is not thread-safe. `requests` intentionally exposes captured XML; use only synthetic data. Its `toString()` reports counts without XML.

`AeatResponseFixtures.response` supports accepted, accepted-with-errors, rejected, duplicate, and wait-value scenarios. `soapFault()` returns a synthetic HTTP 500 fault. Script `NetworkFailure`, `Timeout`, and `NonXmlResponse` directly for transport branches.

Response fixtures are checked against the archived response XSD. Supply the invoice identity and `AeatOperationType` to match the submitted record. Numeric error code `999999` is synthetic, with no official business-rule mapping. Warning-only responses are partially accepted; fully rejected/duplicate responses omit the new CSV. See the [fixture tests](../verifactu-testkit/src/commonTest/kotlin/dev/verifactu/testkit/VerifactuFixturesTest.kt) and [offline examples](../samples/offline/README.md).
