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
