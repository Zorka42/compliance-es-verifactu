# Getting Started

VeriFactu KMP has a local implementation and offline examples. No version is published to Maven Central yet.

## Build prerequisites

Use JDK 21 to build, Android SDK platform 36 with accepted SDK licenses, and an `ANDROID_HOME` environment variable or a gitignored `local.properties` containing `sdk.dir=/your/android/sdk`. JVM artifacts target Java 11. Apple compilation/tests additionally require macOS, Xcode, and an installed arm64 iOS simulator runtime.

```bash
./gradlew jvmTest testDebugUnitTest
./gradlew :samples:offline:runKotlinSample :samples:offline:runJavaSample
```

The first dependency resolution may need network access to Gradle/plugin/dependency repositories. Once cached, add `--offline`. Tests and examples never need live AEAT access or personal certificates.

## Create the first record

Read the complete, compiled [Kotlin example](../samples/offline/src/commonMain/kotlin/dev/verifactu/sample/OfflineExampleResult.kt) or [Java example](../samples/offline/src/jvmMain/java/dev/verifactu/sample/JavaExample.java).

1. Parse identifiers, dates, amounts, and the caller-supplied generation timestamp using each type's `parse` method. Handle `ValueResult.Valid` and `ValueResult.Invalid` at the application boundary.
2. Construct `RegistroAltaDraft`, including the tax breakdown and the host SIF's `SistemaInformatico` metadata. Select the correct invoice category and supply complete business input.
3. Supply `ChainState.FirstRecord` only for an empty chain, otherwise the persisted `ChainState.PreviousRecord`.
4. Call `FiscalRecordFactory.createRegistration`. `RecordCreationResult.Invalid` contains structural validation issues. `Created` contains the record and next chain head.
5. Serialize with `RegistroXmlSerializer.serialize` and build the QR URL with `QrPayloadBuilder.build`.
6. Atomically persist the original record and next head in your application before arranging delivery. Do not change collections supplied to the record after creation.

The factory runs local validation before hashing. It does not establish full fiscal validity or remote acceptance. Current parsing/validation gaps are listed in [implementation status](implementation-status.md).

## Cancellation and delivery

The Kotlin example passes the persisted registration head into `RegistroAnulacionDraft`, then calls `createCancellation`. Both record kinds share the chronological chain. Cancellation neither deletes the registration nor rewinds the head.

`SubmissionBatchXmlSerializer` currently produces the batch document only. It checks count, but does not implement the planned validated batch builder or a wire-ready SOAP request. The example deliberately sends this document to `FakeAeatTransport`; it is not a template for connecting a production HTTP client.

## Java and local artifacts

`parse`, record factory, XML, QR, and response parser entry points have JVM static methods. Sealed results are ordinary Java interfaces/nested classes with getters; Kotlin default arguments do not automatically become Java overloads. The Java example passes constructor arguments explicitly and uses standard Java 11 syntax.

To test actual artifact consumption instead of project dependencies:

```bash
./gradlew publishJvmPreview
./gradlew -p samples/maven-consumer run
```

See [publishing](publishing.md) for the Maven dependency snippet and artifact limitations.
