# Integration Responsibilities

VeriFactu KMP is not a complete Sistema Informatico de Facturacion.

The host application remains responsible for application-level compliance and operations.

## Host Application Responsibilities

The host application must own:

- deciding when an invoice is legally issued;
- durable invoice and fiscal-record storage;
- latest chain-state persistence;
- per-chain concurrency control;
- durable queueing of unsent records;
- retry scheduling;
- user warnings while records remain pending;
- certificate and private-key storage;
- providing opaque certificate handles to platform transport adapters; VeriFactu KMP neither reads certificate bytes nor owns key-store paths or passwords;
- final invoice rendering;
- QR placement;
- operational security;
- complete SIF compliance;
- legal/tax review of the complete product.

## Library Responsibilities

The library should provide:

- typed fiscal models;
- deterministic fiscal-record creation;
- hash generation;
- chain-state primitives;
- local validation;
- deterministic XML;
- QR payloads;
- AEAT request/response modeling;
- transport adapters where supported;
- test utilities for downstream integrations.

## Concurrency Contract

The host application must serialize fiscal-record creation for each independent SIF/taxpayer chain.

Two records must not be generated from the same previous chain head.

The library may provide helper abstractions later, but must not require a particular database, lock manager, queue, or hosting architecture.
