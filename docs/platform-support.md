# Platform Support

Compilation and runtime verification are distinct. The repository is pre-release; the matrix below describes configured CI coverage rather than a released compatibility promise.

| Target | Compilation | Runtime checks | Transport boundary |
| --- | --- | --- | --- |
| common metadata | `compileKotlinMetadata` | Shared tests on concrete targets | `AeatTransportAdapter` |
| JVM | `jvmTest`, Java 11 bytecode | Core/XML/QR/AEAT/testkit tests; Kotlin and Java samples | JVM HTTP client with a supplied SSL context |
| Android | SDK 36, minimum API 26 | `testDebugUnitTest`; shared testkit tests | Injected Android adapter; no device mTLS verification |
| iOS arm64 | `compileKotlinIosArm64` | Shared behavior tested on arm64 simulator; no device execution | Injected Apple adapter |
| iOS simulator arm64 | Test compilation | `iosSimulatorArm64Test`, including `appleTest` and shared example | Injected Apple adapter |
| macOS arm64 | Test compilation | `macosArm64Test`, including `appleTest` and shared example | Injected Apple adapter |
| iOS/macOS x64 | Existing targets remain configured | Not in the required CI matrix | Experimental; not advertised as supported |

Each production module has common tests. Testkit tests traverse the public cross-module APIs on every supported test target, and `appleTest` covers the Apple-specific boundary. JVM additionally validates pinned record XML against local XSDs.

CI runs checks on Linux and Apple hosts. It also builds unsigned JVM artifacts into a local repository and compiles/runs an independent Java consumer. A future release job must depend on both CI jobs; no publishing job is enabled yet.

JDK 21 is the build baseline; published JVM bytecode targets Java 11. The Java consumer compiles with `--release 11`. Java runtime validation uses the installed build JDK unless a separate JDK is explicitly selected; a Java 11 runtime CI job is still needed before claiming that runtime as release-verified.

Native tests require the matching Xcode SDKs and an installed iOS simulator runtime. Android unit tests are host JVM tests, not device or emulator integration tests. Live AEAT TLS behavior is not verified by this matrix.
