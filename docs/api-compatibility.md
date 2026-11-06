# Public API compatibility policy

The project is pre-release. `0.1.0-rc.1` was a local candidate, not an externally published stable API. The first public release must state its supported platforms and known validation limits; passing API checks does not certify fiscal behavior.

## Baselines and review

Every library module retains JVM and Android declarations under `api/jvm` and `api/android`. It also retains a combined `<module>.klib.api` baseline for the configured Apple targets, using the existing binary-compatibility-validator 0.18.2 experimental KLib support. `apiCheck` compares current declarations with these files; this is an exact declaration-change gate, not proof of every source, binary, runtime or behavioral compatibility property.

Normal CI and the release workflow run JVM/Android checks on Linux and `apiCheck` on an Apple host. Linux cannot compile Apple libraries and must not be used as the sole Native ABI evidence. KLib checks are experimental and toolchain-sensitive. The configured Intel targets appear in the ABI dump but remain experimental: a declaration dump does not add runtime support. Swift/Objective-C framework interoperability and device networking are outside this gate.

When a deliberate public API change is required:

1. Explain the consumer impact, default arguments, Kotlin named arguments, Java constructors/overloads, and migration in the change and changelog. An added abstract member, changed data-class constructor, reordered parameter or removed overload can break consumers.
2. Generate baselines with `./gradlew apiDump` on an Apple host supporting all configured targets. Review every changed declaration. Never automatically regenerate baselines as part of `check`.
3. Run `./gradlew apiCheck` in a **separate invocation**, then relevant common/platform tests and the independent Java consumer. Separate invocations avoid dump/check tasks reading and writing the same baseline in one task graph.

Before the first public release, API changes require explicit review but no compatibility promise is inferred from an unpublished snapshot. During public 0.x releases, any deliberate incompatible change must be highlighted with migration guidance and a minor-version increment; patch releases should preserve public source/binary contracts. At 1.0 and later, incompatible public contract changes require a major-version release. Adding a fiscal rejection or changing an issue code/severity is also a consumer-visible behavior change even if ABI is unchanged.

## Java 11 artifact consumption

`samples/maven-consumer` resolves the five published `-jvm` artifacts through Maven POMs from the local preview repository, compiles with `--release 11`, and treats Java warnings as errors. Its executable distribution must run on Java 11 itself; running on a newer JDK only verifies compilation constraints. Both CI and the release workflow explicitly select a Java 11 runtime after building the distribution. Release verification passes the tag's version to the consumer so it tests that release's artifacts rather than a snapshot.

```bash
./gradlew publishJvmPreview
./gradlew -p samples/maven-consumer installDist
# Run with JAVA_HOME set to an explicitly selected Java 11 installation:
samples/maven-consumer/build/install/verifactu-java-consumer/bin/verifactu-java-consumer
```

For a release candidate, supply the same `-PreleaseVersion=<version>` to publication and `-PverifactuVersion=<version>` to the consumer. No Central upload, release tag, taxpayer certificate or AEAT access is needed. Compilation examples establish source usability; ABI snapshots and Java execution together still do not constitute a binary compatibility test against every previously released client.
