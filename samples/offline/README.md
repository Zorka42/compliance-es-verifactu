# Offline Consumer Examples

Run from the repository root:

```bash
./gradlew :samples:offline:runKotlinSample :samples:offline:runJavaSample
```

Both commands use synthetic fiscal data, a fixed timestamp, `example.invalid` endpoint metadata, and `FakeAeatTransport`. They never access the network, read certificates, or sleep. Gradle may resolve build dependencies unless run with `--offline` and a populated cache.

- [Kotlin shared flow](src/commonMain/kotlin/dev/verifactu/sample/OfflineExampleResult.kt): typed input, registration, hash, XML, QR, application-owned chain head, cancellation, batch documents, fake responses, wait value.
- [Java flow](src/jvmMain/java/dev/verifactu/sample/JavaExample.java): explicit Java 11 construction and result handling through static entry points.
- [Shared smoke test](src/commonTest/kotlin/dev/verifactu/sample/OfflineExampleTest.kt): public module integration on JVM and supported Apple test targets.

This code is a sample application, not the future production workflow facade. The batch document is sent to a fake and is not wrapped in SOAP. The minimal response parser cannot reconcile production batches yet. Fixture response contents are synthetic and are not remote validation evidence.

The sample prints only synthetic hashes/statuses/counts. Raw record XML and QR data remain in the returned example artifacts for tests. A real host must own durable storage, per-chain concurrency, queues, complete fiscal validation, credentials, and delivery recovery.

To compile the same Java source against actual local published artifacts, see [the separate consumer build](../maven-consumer/build.gradle.kts) and [publication instructions](../../docs/publishing.md).
