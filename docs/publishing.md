# Publishing

No Maven Central release exists. Local publication is unsigned and cannot contact a remote repository: the only configured publishing destination is `build/maven-repository` in this checkout. No signing plugin, credential lookup, or external release workflow is enabled.

## Coordinates and artifacts

All five libraries use group `io.github.zorka42` and development version `0.1.0-SNAPSHOT`. Packages stay `dev.verifactu`. Kotlin Multiplatform uses root artifact names such as `verifactu-core`; Java/Maven uses `verifactu-core-jvm` (and corresponding XML, QR, AEAT, testkit artifacts). Android release variants are configured for publication too.

Publications carry license, SCM, developer metadata, Kotlin sources, and a Dokka-generated `javadoc` JAR. JVM bytecode targets Java 11. The independent Java consumer builds with `--release 11` and warnings treated as errors.

## Local JVM preview

From the repository root:

```bash
./gradlew publishJvmPreview
./gradlew -p samples/maven-consumer run
```

Add `--offline` when dependencies are cached. The first command creates the five JVM publications; it does not create a complete KMP release. The second is a separate Gradle build with no project dependencies or composite-build substitution. It explicitly reads Maven POMs without Gradle metadata redirection, testing the dependency graph a Maven consumer receives.

For a full set of local platform publications on a configured Mac, use `publishAllPublicationsToLocalPreviewRepository`. That also requires the configured native/Android toolchains; a JVM preview alone does not verify every platform artifact.

Java consumers can use this dependency after installing a matching local preview repository, or replace the version after a real release:

```xml
<dependency>
  <groupId>io.github.zorka42</groupId>
  <artifactId>verifactu-core-jvm</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The local preview directory must be configured as a Maven repository; `mavenLocal()` is not used by these commands. See the [standalone consumer build](../samples/maven-consumer/build.gradle.kts) for repository and module declarations.

## External release work remains separate

ZA-85 is only partially implemented. Namespace verification, CI signing secrets, Central Portal upload/staging, tag-triggered publishing, and GitHub Release creation are still pending and have not been tested. No personal certificates or signing keys are required for the local preview.

Before enabling a release workflow, verify the publishing namespace and current Central requirements, configure dedicated signing/publishing secrets, pin/review the official compliance artifacts, and require all platform/API/consumer checks. Build official releases from reviewed tags in CI. Do not present a local unsigned snapshot as an official compliant release.
