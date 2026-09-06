# Contributing

Thanks for considering a contribution to VeriFactu KMP.

The Gradle Kotlin Multiplatform workspace is available. This project is still pre-release and must not be used for production fiscal compliance.

## Language

Use English for:

- public API names;
- KDoc and comments;
- documentation;
- commit messages;
- pull requests;
- release notes.

Official Spanish VERI*FACTU terms may remain where they map directly to AEAT protocol, XML, or legal terminology.

## Local Development

Use JDK 21, Android SDK platform 36, and the checked-in Gradle Wrapper. Point `ANDROID_HOME` or the gitignored `local.properties` `sdk.dir` to the SDK. Apple checks need macOS, Xcode, and an installed iOS arm64 simulator runtime. JVM publications target Java 11.

Run the standard local gate before opening a pull request:

```bash
./gradlew check
```

For a focused JVM/Android iteration, run `./gradlew jvmTest testAndroidHostTest ktlintCheck detekt apiCheck`. On a Mac, run `./gradlew compileKotlinIosArm64 iosSimulatorArm64Test macosArm64Test` for the advertised Apple targets. Configured Intel Apple targets are experimental and may require additional SDK/runtime support for the aggregate `check` task.

Use `./gradlew dokkaHtml` to generate API documentation. Dokka is configured in offline mode. Add `--offline` to Gradle commands when dependencies are cached; normal tests and examples never require AEAT or real certificates.

Run `./gradlew :samples:offline:runKotlinSample :samples:offline:runJavaSample` for the canonical compiled examples. Local artifact verification uses `./gradlew publishJvmPreview` followed by `./gradlew -p samples/maven-consumer run`. See [publishing](docs/publishing.md).

The CI workflow also runs:

- `ktlintCheck` for Kotlin formatting;
- `detekt` for static analysis without a baseline;
- `apiCheck` for reviewed public JVM/Android API signatures;
- `koverVerifyJvm` for the 90% line-coverage gate in `verifactu-core`;
- `dokkaHtml` for generated API documentation.

The remaining modules publish JVM Kover reports without pretending that target-specific coverage is equivalent to JVM coverage.

For an intentional public API change, inspect the generated diff from `./gradlew apiDump`, commit the reviewed baselines, and rerun `apiCheck`. Do not refresh baselines merely to hide an accidental incompatible change. API stability before the first release is still under development.

## Compliance-Sensitive Changes

Changes affecting VERI*FACTU behavior must cite official sources.

Use this priority order:

1. BOE legislation.
2. AEAT technical specifications.
3. AEAT XSD/WSDL and validation/error definitions.
4. AEAT FAQ for clarification.

For each compliance-sensitive change:

- link or name the official source in the pull request;
- update `docs/compliance/README.md` or a future traceability matrix entry;
- add or update deterministic tests where applicable;
- avoid treating third-party libraries or blog posts as normative sources.

## Commit Messages

Use Conventional Commits:

```text
feat(core): add cancellation records
fix(hash): normalize decimal representation
docs: explain AEAT error handling
test(xml): add golden registration fixture
```

## Pull Requests

Pull requests should:

- keep scope narrow;
- explain behavior changes;
- include tests for behavior changes where code exists;
- update docs when public behavior changes;
- avoid committing secrets, certificates, private keys, or production credentials.

## Release Changes

No external release workflow is enabled yet. Publishing credentials and signing are separate from local development. Official releases must be built by CI from reviewed tags after the platform checks and compliance baseline are verified.

Release-related changes must preserve:

- Apache-2.0 licensing;
- Maven Central requirements;
- semantic versioning;
- changelog entries;
- traceability to the official compliance baseline.
