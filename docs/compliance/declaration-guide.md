# Component Declaration Preparation

These materials help a release producer and an integrating SIF producer assemble evidence for a declaración responsable. They are documentation scaffolding: neither this guide nor the [worksheet](declaration-template.md) is an issued declaration, an AEAT approval, or a certification of an application using the library.

## Responsibility Boundary

VeriFactu KMP implements reusable protocol components. Its [product scope](../../PRODUCT_SPEC.md) excludes the complete invoicing system. A passing hash vector, schema check, platform test or Maven publication demonstrates a specific technical property; it does not establish that a deployed SIF satisfies all applicable requirements.

[RD 1007/2023, regulation article 13.1–4](https://www.boe.es/buscar/act.php?id=BOE-A-2023-24840#a1-5) assigns the declaration to the system producer. It requires written, version-specific availability, preservation of earlier declarations, system identification, producer details, and date/place of subscription.

[Orden HAC/1177/2024, article 15.4](https://www.boe.es/buscar/act.php?id=BOE-A-2024-22138#a1-7) also addresses components and extensions from different producers. Calling this project a library does not establish an exemption. Before a production release, identify the responsible component producer and review the component declaration required for the delivered scope. The complete host's producer must separately account for the assembled product and its integration.

AEAT's [declaration FAQ](https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/preguntas-frecuentes/certificacion-sistemas-informaticos-declaracion-responsable.html), updated 2026-07-21, clarifies that an open-source integrator remains responsible for the incorporated code, and compliance-affecting components need consideration in a system built by multiple producers. Its answers on open-source software, multiple components and third-party extensions are relevant to this library. No completed upstream declaration should be inferred from the Apache-2.0 license.

## Release Evidence Package

The following package is a project review convention. Keep it reproducible for each component release:

- Exact artifact coordinates, released version, source commit, source archive and binary digests; record downstream patches separately. A changing `SNAPSHOT` alone does not identify the reviewed bytes.
- Included modules and supported target variants. Name the host's actual platform, runtime and deployment configuration rather than copying every planned KMP target.
- Resolved direct and transitive dependencies, versions, licenses, required notices and distribution scope. Separate runtime dependencies from test/build tooling; preserve their original license material. [Acknowledgements](../../ACKNOWLEDGEMENTS.md) are credits, not a complete license inventory.
- Official-source versions and digests, source-to-code-to-test traceability, reproducible test commands and reports. Identify synthetic fixtures and official examples distinctly.
- Documented supported operations, limitations and integration prerequisites. Retain failures and unverified cases; do not turn a release label into a compliance claim.
- The reviewed component declaration, when issued by its responsible producer, plus the host's own declaration and declarations for other applicable components. This repository's blank worksheet is not a substitute.

## Host Verification Before Issuing a Declaration

Use [integration responsibilities](../integration-responsibilities.md) to assign and verify the host-owned work. At minimum, the project review should cover:

| Area | Evidence to assemble |
| --- | --- |
| Scope and identity | Producer, product/version, relevant modules, supported operations and deployment boundaries |
| Invoice finalization | Invoice-to-record mapping, explicit timestamps and exact decimal handling |
| Durable chain | Serialized per-taxpayer chain updates, crash recovery and concurrent issuance tests |
| Delivery | Durable pending records, response reconciliation, unknown-delivery recovery and supported correction workflows |
| Credentials | Explicit caller-owned certificate selection, authorization, renewal handling and secret-safe diagnostics |
| Presentation | Rendered invoice and QR checks in the actual host application |
| Platform support | Tests of the assembled runtime and its transport, beyond common metadata compilation |
| Operations | Access controls, retention/recovery procedures and version/declaration availability |

Local deterministic tests, loopback transport checks and a Java/Kotlin artifact consumer can run before using a personal certificate. Actual authenticated AEAT acceptance and platform credential integration remain separate verification stages. Record their status explicitly; do not prefill them as passed.

## Preparing the Producer's Final Document

[Article 15.1–3 of Orden HAC/1177/2024](https://www.boe.es/buscar/act.php?id=BOE-A-2024-22138#a1-7) prescribes the final title, field wording/order, required information and availability. The worksheet maps these fields but deliberately omits any completed conformity statement. The producer must prepare the final Spanish document using the current official wording after reviewing the actual product. The [AEAT example, v0.5.1](https://sede.agenciatributaria.gob.es/static_files/Sede/Tema/IVA/Verifactu/EjemplosDeclaracionResponsable%28V0.5.1%29.pdf) illustrates a complete software product; its fictional identities and architectural claims must not be copied as facts about this library.

The AEAT FAQ answer on electronic signature states that an electronic signature is not required for the producer's declaration. This is distinct from certificate authentication for AEAT services and from Maven artifact signing. Preparing these documents needs neither the user's certificate nor access to an authenticated AEAT service.

Still required before issuing an actual declaration: an identified legal producer, a fixed release and composition, completed integration evidence, resolution of known gaps, and producer review of the applicable declaration obligations and final wording. This is the producer/legal review stage, not an action performed by filling in a template.

## Source Review

Reviewed on 2026-09-06: RD 1007/2023 consolidated through 2025-12-03; Orden HAC/1177/2024 as published 2024-10-28 with its 2024-11-08 correction; AEAT declaration FAQ updated 2026-07-21; AEAT example v0.5.1. BOE consolidated texts are navigational aids; review the official publications and amendments for the issued document. Current BOE article 15.1 identifies date/place as **l)**; use that text rather than the FAQ's occasional **m)** reference.
