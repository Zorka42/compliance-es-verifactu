# Chain-State Persistence and Concurrency Contract

`verifactu-core` calculates immutable records and returns the next `ChainState.PreviousRecord`. The integrating application owns every durable operation around that value.

For each taxpayer and SIF installation, the host application must atomically:

1. Load the current persisted chain state.
2. Generate the next record from that exact state.
3. Durably persist the immutable record and the returned next chain state in one transaction or equivalent compare-and-set operation.

The library neither stores the state nor locks records. Two concurrent calls made from the same previous state can each create valid hashes but only one may become the next persisted record. The host must detect the conflict, discard the losing candidate, reload chain state, and generate a new record.

Submission retries do not regenerate records. A retry resends the immutable record already persisted by the application. The host must separately track unknown delivery, acceptance, rejection, and any AEAT flow-control response. The library never schedules retries, sleeps, or claims exactly-once delivery.

The contract is derived from the chaining fields in AEAT `SuministroInformacion.xsd` (`EncadenamientoFacturaAnteriorType`, tikeV1.0 retrieved 2026-08-16) and the caller-owned persistence boundary in the product specification.
