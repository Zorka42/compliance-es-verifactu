# Module Architecture

`verifactu-core` owns pure, deterministic fiscal models, validation, record creation, hash inputs, hashes, chain-state values, and compliance metadata. It must perform no I/O and has no dependency on the other modules.

`verifactu-xml` depends on core and currently owns deterministic record and batch XML serialization. The minimal AEAT response parser currently lives in `verifactu-aeat`; its final module placement remains part of the runtime work.

`verifactu-qr` depends on core and returns QR payloads or verification URLs only. Rendering belongs to the integrating application.

`verifactu-aeat` depends on core and XML, and owns optional AEAT transport contracts, endpoint configuration, and response semantics. It owns neither credentials nor retry scheduling.

`verifactu-testkit` depends on core, XML, QR, and AEAT and provides synthetic fixtures and a scripted fake transport. It must never be a dependency of the production modules. This test-only dependency direction lets fixtures exercise the real implementations.

`samples:offline` is an unpublished KMP consumer with shared Kotlin flow code and a Java entry point. `samples/maven-consumer` is an independent Java build that resolves local published JVM artifacts. Neither is a production workflow facade.

Public APIs are explicitly declared and KDoc is required for externally consumable declarations. Experimental public APIs must use a dedicated opt-in marker. Platform adapters remain internal unless an integration boundary requires a public interface.
