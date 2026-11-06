# Public-source gap review — ZA-120

Reviewed on 2026-11-06 using public static BOE and AEAT material only. No authenticated portal, taxpayer certificate, census/VIES request or operational endpoint was used. This review does **not** resolve the outstanding service-contract questions, and ZA-120 must not be marked `To verify` until its clarification criteria are met.

## Evidence retained in the repository

The validation catalogue, error catalogue and common schema were downloaded again and compared byte for byte with the archived files:

| File | Result | SHA-256 |
| --- | --- | --- |
| `Validaciones_Errores_Veri-Factu.pdf` | Unchanged; v1.2.2, 2026-04-08 | `426eb926fc098a36a163f66ca5f40d9e0847ca23300bbe5008979832d3513440` |
| `errores.properties` | Unchanged | `06519ceb23422bd6b0ad3bfb659e3007615050da4920781d12cff536481d5902` |
| `SuministroInformacion.xsd` | Unchanged | `ee4c1655175644de44c4c25055ffeb8e5f4bb4bc3834ce8254d4222ef18c8aa1` |

Additional original public files are archived under [`schemas-aeat`](../../schemas-aeat/README.md), with their exact URLs and digests in the manifest:

- `BOE-A-2024-12944.pdf`: original publication of Real Decreto-ley 4/2024, article 1.One/Two, BOE 27 June 2024.
- `aeat-entity-nif.html` and `aeat-personal-nif.html`: AEAT model-036 guide, NIF composition sections, retrieved on the review date.
- `FAQs-Desarrolladores.pdf`: developer FAQ linked by the current technical index (the index labels it updated 4 December 2025). It supplies no direct answer to the questions below.

The existing RD 1065/2007 and Order EHA/451/2008 snapshots remain the structural identifier sources. This focused review does not claim a complete refresh of legislation or every release contract. Searching public documentation without finding an algorithm is not proof that no authoritative algorithm exists.

## Zero-rate IVA and equivalence surcharge

The validation PDF §3.1.3.15.3 specifies zero IVA with zero surcharge for operation dates from 2023-01-01 through 2024-09-30. It contains no corresponding subsequent zero-IVA rule. Error 1170 nevertheless describes zero IVA with surcharge 0.26 from 2024-10-01. Error 1277 is a generic zero-IVA surcharge error and does not supply the missing date/rate rule.

The higher-priority BOE source, RDL 4/2024 article 1.One/Two, associates the temporary food-product zero IVA with zero surcharge through September 2024, and **2% IVA** with **0.26% surcharge** from October through December 2024. This explains a possible origin for the catalogue discrepancy; it does not establish that 1170 is a typo, nor what the VERI*FACTU service actually accepts for all zero-IVA operations after September 2024. Do not generalize these product-specific statutory rates into an invoice-wide validation algorithm.

**Decision:** keep `VF-TAX-ZERO-RATE-UNRESOLVED` as a library warning with no asserted AEAT code. Do not automatically substitute 2%, reject one interpretation, or claim service acceptance. Common tests retain both 0 and 0.26 surcharge candidates at and after the disputed boundary.

## Supplied VAT country codes

The PDF Note (1) lists Greek VAT prefix EL and Northern Ireland prefixes GB/XI. The schema `CountryType2` supplies GR and GB, but no EL or XI. Error 1122 literally requires the supplied country to agree with the first two identifier characters. None of the reviewed material explicitly reconciles these statements for supplied GR+EL or GB+XI values.

**Decision:** retain the existing mappings as explicitly labelled `VF-ID-COUNTRY-PREFIX-ASSUMPTION` issues, not verified service rules. For IDType 02, the PDF expressly permits omission of `CodigoPais`; an omitted country needs no alias inference. This is not evidence that every foreign-identification type permits omission. Common fixtures cover both alias pairs, omitted VAT country and mismatching ordinary prefixes.

## Entity and K/L/M check characters

The AEAT composition guide repeats the entity structure (prefix, seven random digits, control character) and explains special personal NIF allocation. It does not publish a complete entity or K/L/M checksum calculation. The retained BOE provisions establish structure, including the alphanumeric K/L/M allowance, rather than an implemented arithmetic algorithm.

**Decision:** preserve `VF-ID-CHECKSUM-UNVERIFIED` warnings. Do not import third-party CIF algorithms or infer them from a few public NIF examples. The existing common tests exercise every entity prefix and alphanumeric K/L/M values; follow-up fixtures assert that supplying receipt-date context and creating a record cannot turn these warnings into verified identity evidence.

## Questions prepared for AEAT

These questions are recorded for a separately authorized enquiry; no enquiry has been sent.

1. En la versión 1.2.2, apartado 3.1.3.15.3, no figura una regla posterior al 30/09/2024 para TipoImpositivo=0. El error 1170 exige TipoRecargoEquivalencia=0,26 desde el 01/10/2024, mientras el artículo 1.Dos del RDL 4/2024 vincula ese recargo al IVA del 2% para determinados productos. ¿Cuál es la regla de validación del servicio para IVA 0 desde el 01/10/2024, qué fechas/excepciones se aplican y se corregirá el catálogo?
2. Para IDType=02, ¿se aceptan explícitamente CodigoPais=GR con ID empezando por EL y CodigoPais=GB con ID empezando por XI? ¿Cómo se aplica el error 1122 a estos pares frente a CountryType2? ¿Hay ejemplos oficiales de petición y respuesta?
3. ¿Existe una especificación pública oficial del algoritmo del carácter de control de NIF de entidades y NIF K/L/M, incluidos los caracteres permitidos por prefijo y los casos alfanuméricos? Se solicita referencia/versionado, no datos censales ni credenciales.

The blocker is authoritative clarification, rather than missing certificate access. Work on the independent XML/API hardening task can continue without choosing a fiscal interpretation.
