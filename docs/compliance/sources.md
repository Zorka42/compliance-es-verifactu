# Compliance Sources

Compliance behavior must be derived from official sources.

This page records the current source baseline used by the repository documentation. Release artifacts must record the exact technical document versions used by that release.

## Legal Baseline

- Real Decreto 1007/2023
  https://www.boe.es/buscar/act.php?id=BOE-A-2023-24840
- Real Decreto-ley 15/2025
  https://www.boe.es/buscar/doc.php?id=BOE-A-2025-24446
- Orden HAC/1177/2024
  https://www.boe.es/buscar/act.php?id=BOE-A-2024-22138
- Real Decreto 1619/2012
  https://www.boe.es/buscar/act.php?id=BOE-A-2012-14696

## AEAT Sources

- AEAT SIF/VERI*FACTU hub
  https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu.html
- AEAT technical documentation
  https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/informacion-tecnica.html
- AEAT FAQ
  https://sede.agenciatributaria.gob.es/Sede/iva/sistemas-informaticos-facturacion-verifactu/preguntas-frecuentes.html

## Pinned Technical Inputs

Retrieved on 2026-08-16. These sources are the input baseline for the initial implementation; a release must re-download and record a content digest for every XSD/WSDL it packages.

| Artifact | Pinned version/date | Official location | Use |
| --- | --- | --- | --- |
| Hash algorithm | v0.1.2, 2024-08-27 | https://www.agenciatributaria.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/Veri-Factu_especificaciones_huella_hash_registros.pdf | Canonical field order, UTF-8 SHA-256 input, uppercase hash output, official examples |
| QR technical specification | v0.5.0, 2025-12-10 | https://www.agenciatributaria.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/DetalleEspecificacTecnCodigoQRfactura.pdf | QR verification URL, parameter order, URL encoding, and test/production endpoints |
| Registration and cancellation schema | tikeV1.0 endpoint, retrieved 2026-08-16 | https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/SuministroLR.xsd | `RegFactuSistemaFacturacion` root and record choice |
| Common schema | tikeV1.0 endpoint, retrieved 2026-08-16; SHA-256 `34ef72b3f5ba2c6c5cd2d9a7c3b5b7b226b59d754e569c5c74a04d1c27762989` | https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/SuministroInformacion.xsd | common types, required fields, element ordering, and JVM XSD validation fixture at `verifactu-xml/src/jvmTest/resources/aeat-xsd/` |
| Response schema | tikeV1.0 endpoint, retrieved 2026-08-16 | https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/RespuestaSuministro.xsd | global and per-record submission result semantics |
| VeriFactu service WSDL | tikeV1.0 endpoint, retrieved 2026-08-16 | https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/SistemaFacturacion.wsdl | production and test submission endpoints, standard and seal-certificate routes, service operations |
| Submission service specification | retrieved 2026-08-16 | https://sede.agenciatributaria.gob.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/Veri-Factu_Descripcion_SWeb.pdf | SOAP submission and flow-control behaviour |
| Validation and error catalogue | retrieved 2026-08-16 | https://www.agenciatributaria.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/Validaciones_Errores_Veri-Factu.pdf | AEAT validation/error codes |

## Explicit Initial Assumptions and Open Questions

- The remote technical pages expose mutable `tikeV1.0` endpoints. `SuministroInformacion.xsd` and its W3C XML Signature dependency are vendored for JVM test validation; the release process must re-download and review their digests.
- The library validates only deterministic, locally knowable rules. AEAT acceptance state, duplicate state, and delivery state remain submission outcomes rather than local assertions.
- The QR module returns a URL payload only. QR image generation, placement, and invoice presentation remain outside this library.
- The library consumes caller-owned previous chain state; durable storage, locking, retries, and exactly-once submission are integrating-application responsibilities.

## Current Timing Baseline

As reviewed on 2026-08-15:

- Corporate taxpayers covered by Article 3.1.a of RRSIF must have adapted systems before 2027-01-01.
- Other covered taxpayers under Article 3.1 must have adapted systems before 2027-07-01.
- Producers and commercializers of covered SIF products must offer products fully adapted within nine months from the entry into force of Orden HAC/1177/2024; AEAT FAQ identifies the resulting deadline as 2025-07-29.

These dates must be rechecked before every release.

## Technical Baseline To Pin Per Release

Each release should record exact versions for:

- record designs;
- validations/errors;
- WSDL;
- XSD schemas;
- hash specification;
- QR specification;
- web-service submission specification;
- AEAT FAQ revision date used for clarifications.

## Non-Normative Sources

Third-party libraries, vendor documents, blog posts, and examples may be useful for engineering comparisons, but they must not define compliance behavior.
