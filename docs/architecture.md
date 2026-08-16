# Module Architecture

`verifactu-core` owns pure, deterministic fiscal models, validation, record creation, hash inputs, hashes, chain-state values, and compliance metadata. It must perform no I/O and has no dependency on the other modules.

`verifactu-xml` depends on core and owns deterministic XML serialization and XML parsing.

`verifactu-qr` depends on core and returns QR payloads or verification URLs only. Rendering belongs to the integrating application.

`verifactu-aeat` depends on core and XML, and owns optional AEAT transport contracts, endpoint configuration, and response semantics. It owns neither credentials nor retry scheduling.

`verifactu-testkit` depends on core and provides deterministic fixtures and test helpers. It must never be a runtime dependency of the production modules.

Public APIs are explicitly declared and KDoc is required for externally consumable declarations. Experimental public APIs must use a dedicated opt-in marker. Platform adapters remain internal unless an integration boundary requires a public interface.
