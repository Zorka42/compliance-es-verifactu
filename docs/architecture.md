# Module Architecture

`verifactu-core` owns deterministic fiscal models, structural validation, record creation, hash inputs, hashes, chain-state values and compliance references. It performs no I/O and has no dependency on the other modules.

`verifactu-xml` depends on core. It owns deterministic record serialization and validated single-issuer batch/SOAP preparation. `SubmissionBatchBuilder` returns exact immutable request strings after validation and hash verification. Low-level serialization remains available separately.

`verifactu-qr` depends on core and returns QR payloads or verification URLs. Rendering belongs to the host.

`verifactu-aeat` depends on core, XML and QR. Its `FiscalSubmissionPreparation` facade composes local artifacts. It also owns optional transport contracts, endpoint metadata, response semantics and pure response correlation. Common code interprets an internal namespace-aware XML tree supplied by platform readers: JAXP SAX for JVM/Android, Foundation `NSXMLParser` for Apple. This keeps external dependencies out of core. It owns no storage, clocks, credentials, queue or retry scheduling.

`verifactu-testkit` depends on the four production modules and provides schema-checked synthetic fixtures and a scripted fake. Production modules never depend on testkit. Its tests execute real builders, serializers, parsers and correlation through fake delivery.

`samples:offline` is an unpublished KMP consumer with shared Kotlin preparation/attempt handling and a Java entry point. `samples/maven-consumer` is an independent Java build resolving local Maven publications. `tools:query` is an unpublished JVM command for preparing consultation XML and inspecting saved responses; it has no network or certificate adapter. It uses only the JDK and bundled XSDs.

Public APIs use explicit types and English KDoc. Input lists are snapshotted where they become prepared fiscal artifacts; returned XML strings can be stored and replayed unchanged. Caller-owned persistence and delivery remain distinct stages. Review findings and remaining release work are tracked in [implementation status](implementation-status.md).
