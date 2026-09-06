# Offline Consumer Examples

Run from the repository root:

```bash
./gradlew :samples:offline:runKotlinSample :samples:offline:runJavaSample
```

Both commands use synthetic fiscal data, a fixed timestamp, `example.invalid` endpoint metadata, and `FakeAeatTransport`. They never access the network, read certificates, or sleep. Gradle may resolve build dependencies unless run with `--offline` and a populated cache.

- [Kotlin shared flow](src/commonMain/kotlin/dev/verifactu/sample/OfflineExampleResult.kt): `FiscalSubmissionPreparation`, registration, hash, XML, QR, application-owned chain head, cancellation, prepared SOAP requests, correlated fake responses, and flow control.
- [Java flow](src/jvmMain/java/dev/verifactu/sample/JavaExample.java): explicit Java 11 construction, preparation, and response correlation through static entry points. Its synthetic response identifies `JAVA-001`, matching the submitted invoice.
- [Shared smoke test](src/commonTest/kotlin/dev/verifactu/sample/OfflineExampleTest.kt): public module integration on JVM and supported Apple test targets.
- [Attempt outcome tests](src/commonTest/kotlin/dev/verifactu/sample/OfflineAttemptOutcomeTest.kt): accepted records, warnings, rejection, duplicates, SOAP faults, malformed XML, mismatched identities/operations, unknown states, flow control, and transport failures.

The sample prepares a SOAP 1.1 envelope through the library facade and passes that exact string to an explicit fake. Both consumers require matched invoice identities and operations before inspecting acceptance. A duplicate, warning, rejection, or unknown state does not enter the sample's ordinary accepted branch. Synthetic responses provide local integration evidence; they do not establish remote AEAT acceptance.

`interpretExampleAttempt` is a pure, application-owned example. It distinguishes not-sent and unknown-delivery failures, HTTP responses it cannot use, SOAP faults, malformed responses, correlation failures, and known or unknown fiscal states. Parsed flow control remains inspectable. Interpreting a result performs no retry or chain-state mutation. A test explicitly repeats the same saved SOAP bytes after a synthetic timeout and verifies that the record hash and timestamp stay unchanged; a real host must first decide how to reconcile unknown delivery.

The sample prints only synthetic hashes/statuses/counts. Raw record XML and QR data remain in the returned example artifacts for tests. The local `persistedHead` value marks where a real host would atomically persist a record and its chain head before delivery. No durable store is implemented or tested here. A real host owns durable storage, per-chain concurrency, queues, credentials, and delivery recovery, and must address the library's documented validation gaps before production use.

To compile the same Java source against actual local published artifacts, see [the separate consumer build](../maven-consumer/build.gradle.kts) and [publication instructions](../../docs/publishing.md).
