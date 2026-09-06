# Acknowledgements

This project is based on official Spanish BOE and AEAT VERI*FACTU/SIF materials.

Official sources are normative for compliance behavior. Third-party libraries and articles are engineering references only.

## Official Sources

- BOE legislation listed in [docs/compliance/sources.md](docs/compliance/sources.md).
- AEAT SIF/VERI*FACTU technical documentation listed in [docs/compliance/sources.md](docs/compliance/sources.md).
- AEAT FAQ used only for clarification where legislation and technical specifications leave practical integration questions open.

## Runtime Dependencies and Development Tools

- Kotlin and its standard library, maintained by JetBrains and contributors, provide the language and multiplatform runtime. The current version catalogue uses Kotlin `2.3.10`; the generated JVM publication POM declares `org.jetbrains.kotlin:kotlin-stdlib:2.3.10`.
- Kotlin Test supports the repository's tests.
- Gradle, the Android Gradle Plugin, ktlint, detekt, Kover, Dokka, and Kotlin Binary Compatibility Validator support builds, style checks, analysis, coverage, documentation, and API checks. Their configured versions are recorded in [the version catalogue](gradle/libs.versions.toml) and the Gradle wrapper configuration. These development tools are separate from published runtime dependencies.

These credits are not a complete dependency/license inventory. Each release must inspect its resolved direct and transitive dependencies, retain required upstream license and notice files, and distinguish runtime artifacts from build/test tooling. Acknowledgements do not replace those notices or establish fiscal compliance. See [component declaration preparation](docs/compliance/declaration-guide.md) for the separate release evidence responsibilities.

## Open-Source References

The following projects may be studied as non-normative references:

- `eloi24/verifactu-sdk`
- `josemmo/Verifactu-PHP`
- `invopop/gobl.verifactu`
- `mdiago/VeriFactu`

No implementation code from those projects is currently copied into this repository.

If code, test fixtures, documentation, or examples are copied, translated, or adapted later, the project must preserve all applicable license notices and update this file.
