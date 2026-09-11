# Compliance Traceability

This directory stores compliance-source and traceability documentation.

Compliance behavior must be traceable to official BOE and AEAT sources.

## Source Priority

1. BOE legislation.
2. AEAT technical specifications.
3. AEAT XSD/WSDL and published validation/error definitions.
4. AEAT FAQ for clarification.

Third-party libraries, blog posts, and vendor documentation are not normative.

## Traceability Matrix

The project should maintain a lightweight matrix as implementation begins.

Recommended columns:

```text
requirement
official source
source section/version
implementation
tests
status
last reviewed date
```

## Initial Entries

| Requirement | Official source | Source section/version | Implementation | Tests | Status | Last reviewed date |
| --- | --- | --- | --- | --- | --- | --- |
| 2027 mandatory rollout dates | Real Decreto-ley 15/2025; AEAT FAQ | RDL 15/2025 Article 3; FAQ "Entrada en vigor y efectos" | `PRODUCT_SPEC.md`; `docs/compliance/sources.md` | Documentation review | Documented | 2026-08-15 |
| v1 supports VERI*FACTU mode only | Product scope derived from RRSIF/AEAT VERI*FACTU technical materials | TBD exact technical baseline | `PRODUCT_SPEC.md`; `docs/protocol-behavior.md` | TBD | Planned | 2026-08-15 |
| Chain state is caller-owned | RRSIF traceability and AEAT record chaining requirements | TBD exact technical baseline | `PRODUCT_SPEC.md`; `docs/core-concepts.md`; `docs/integration-responsibilities.md` | TBD | Planned | 2026-08-15 |
| AEAT/BOE sources are normative | Project open-source requirement | `OPEN_SOURCE_REQUIREMENTS.md` | `README.md`; `CONTRIBUTING.md`; `docs/compliance/sources.md` | Documentation review | Documented | 2026-08-15 |

## Implementation Traceability Matrix

| Requirement | Official source | Source section/version | Implementation | Tests | Status | Last reviewed date |
| --- | --- | --- | --- | --- | --- | --- |
| Registration and cancellation XML record structures | AEAT `SuministroInformacion.xsd` | tikeV1.0; `RegistroFacturacionAltaType`, `RegistroFacturacionAnulacionType` | `verifactu-core`; `verifactu-xml` | core and XML schema tests | Implemented | 2026-08-16 |
| First/previous record chaining fields | AEAT `SuministroInformacion.xsd` | tikeV1.0; `EncadenamientoFacturaAnteriorType` | `verifactu-core` | chain-state continuity tests | Implemented | 2026-08-16 |
| Registration hash canonical order and output | AEAT hash specification | v0.1.2, sections 2–6 | `verifactu-core/.../RecordHash.kt` | `RecordHashCalculatorTest` official cases 1 and 3 | Implemented | 2026-08-16 |
| Deterministic record XML | AEAT `SuministroLR.xsd`, `SuministroInformacion.xsd` | tikeV1.0 | `verifactu-xml` | golden and JVM XSD tests | Implemented | 2026-08-16 |
| QR verification URL | AEAT QR specification | v0.5.0, sections 4–8 | `verifactu-qr` | QR golden fixtures | Implemented | 2026-08-16 |
| Submission and flow control | AEAT submission specification; `RespuestaSuministro.xsd` | retrieved 2026-08-16 | `verifactu-aeat` | response parsing fixtures | Planned | 2026-08-16 |
| AEAT submission environment defaults | AEAT `SistemaFacturacion.wsdl` | tikeV1.0; production/test and standard/seal endpoint ports | `verifactu-aeat/.../AeatEndpointConfiguration.kt` | `AeatEnvironmentConfigurationTest` | Implemented | 2026-08-16 |
| Local validation issues | AEAT schemas and validation catalogue | tikeV1.0; validation catalogue retrieved 2026-08-16 | `verifactu-core` | deterministic validation tests | Implemented | 2026-08-16 |
| Conditional registration models and validation | Archived AEAT `SuministroInformacion.xsd`; validation catalogue | tikeV1.0 `RegistroFacturacionAltaType` sequence and three `maxOccurs=1000` groups; `CountryType2`; catalogue 1.2.2 §3.1.3 | `ConditionalFiscalModels.kt`; `FiscalRecords.kt`; `RegistroXmlSerializer.kt` | Common invoice-type/rectification XML order, cardinality, country and immutable-list tests; JVM canonical-XSD fixtures for F1/F2/F3/R1–R5 | Implemented structural and selected conditional rules; complete identity and external-context validation remain separate | 2026-09-11 |
| Tax-family regime and exemption lists | Archived validation catalogue; Order HAC/1177/2024 | Catalogue 1.2.2 (2026-04-08) §3.1.3.15.5–15.6, 15.6.11; Order Annex 6 L8A/L8B/L10; errors 1182, 1199, 1245, 1246, 1260, 1289 | `FiscalRecords.kt` tax-regime and exemption validation | `TaxConditionalValidationTest`: IVA/default/IGIC/IPSI/other families, missing regimes, IVA 21 rejection, IGIC 21, E5 recipient identification | Implemented locally; census identification remains external | 2026-09-11 |
| Tax-field presence, cost base and qualification | Archived validation catalogue and `errores.properties` | Catalogue 1.2.2 §3.1.3.15.2, 15.4, 15.5, 15.7; errors 1197, 1198, 1207–1209, 1237, 1238, 1257, 1281, 1284 | `FiscalRecords.kt` tax-operation and field-combination validation | `TaxConditionalValidationTest`: S1 requires rate and tax even with cost base; S2 zero values; IVA-only N1/N2 prohibition; nonzero charged tax and surcharge fields only S1; surcharge rate/amount pairing | Implemented presence and combination checks; tax arithmetic remains separate | 2026-09-11 |
| IVA rates and surcharge combinations use the operation date, falling back to the issue date | Archived validation catalogue and `errores.properties` | Catalogue 1.2.2 §3.1.3.15.1, 15.3; errors 1124, 1127, 1162–1169, 1194, 1235, 1236 | `FiscalRecords.kt` IVA rate validation; exact decimal rate comparison | `TaxConditionalValidationTest`: historic interval start/end and adjacent dates, surcharge pairs, numeric equivalents, malformed rates, date fallback independent of generation timestamp | Implemented documented combinations; later zero-rate surcharge rule unresolved as described below | 2026-09-11 |
| Regime-specific operation, recipient, invoice-type and date combinations | Archived validation catalogue and `errores.properties` | Catalogue 1.2.2 §3.1.3.15.6.1–15.6.11; errors 1147–1149, 1200–1206, 1252, 1286, 1293 | `FiscalRecords.kt` regime-operation and contextual validation | `TaxConditionalValidationTest`: regimes 02/03/04/06/07/08/10/11/14/20/21, qualified/exempt choices, recipient NIF type/prefix, later operation date | Implemented modeled-field conditions; no census or AEAT-clock claim | 2026-09-11 |
| IPSI transition requires AEAT receipt-date context | Archived validation catalogue | Catalogue 1.2.2 §3.1.3.15.6: warning through 2026-12-31, rejection from 2027-01-01 for missing/incompatible IPSI regime | `FiscalRecords.kt` `VF-TAX-IPSI-TRANSITION` local warning | `TaxConditionalValidationTest`: both sides of generation-date boundary remain warnings, factory preserves immutable report; facade/batch warning tests | Explicit local limitation; generation timestamp is not the AEAT receipt date | 2026-09-11 |
| Operation date after issue date requires regime 14/15 for each IVA/IGIC detail | Archived validation catalogue and `errores.properties` | Catalogue 1.2.2 §3.1.3.1 p.8; error 1146 | `FiscalRecords.kt` per-detail date/regime validation | `TaxConditionalValidationTest`: before/equal/after dates, default/IVA/IGIC scope, mixed 14+15 accepted locally and 14+01 rejected, IPSI/other outside the PDF condition | Implemented explicit date relation; no current-date inference | 2026-09-11 |
| Typed incidence and subsanation interpretation | AEAT validation catalogue and archived `errores.properties` | catalogue 1.2.2, sections 4.2–4.4; `errores.properties` accepted-record, record/header-dependent rejection, and whole-submission rejection groups | `AeatErrorCatalogue.kt`; `AeatResponseParser.kt`; synthetic `AeatResponseFixtures` | Catalogue location/unknown/redaction tests; response incidence fixtures | Implemented with explicit error location; no automatic correction workflow | 2026-09-11 |
| Supplementary-Unicode character counts and provider compatibility | [Archived W3C XML Schema Part 2, Second Edition](../../schemas-aeat/w3c-xmlschema-2-20041028.html); AEAT XSD | W3C §4.3.1 and §4.3.3; `SuministroInformacion.xsd` `TextMax*Type`, `TextoIDFacturaType`, `NIFType` | `XmlCharacterCount.kt`; `FiscalValues.kt`; record and batch validation | Common 60/120/500-code-point boundaries, combining marks and invalid surrogates; portable JVM provider probe plus official-XSD boundary/DOM roundtrip | Normative common semantics implemented; JAXP compatibility and platform coverage limits documented in error handling | 2026-09-11 |

Future implementation pull requests must replace `TBD` cells with exact source versions, implementation paths, and test paths.

### Tax validation limits

The tax tables use the original public files archived in [`schemas-aeat`](../../schemas-aeat/README.md); its manifest records the download URLs and SHA-256 values. Local validation covers the listed rules and does not establish full AEAT acceptance. Required sign/amount arithmetic, invoice-total tolerances, census/VAT identification, invoice-number business restrictions and checks requiring AEAT's date remain separate work.

The zero-rate IVA surcharge rule from 2024-10-01 needs source resolution before release: validation catalogue v1.2.2 §3.1.3.15.3 documents the earlier zero-surcharge interval (2023-01-01 through 2024-09-30), while archived `errores.properties` error 1170 specifies 0.26 thereafter (also compare 1165 and 1277). The library emits `VF-TAX-ZERO-RATE-UNRESOLVED` as its own warning for the later period, with no asserted AEAT error code or acceptance outcome. A warning-only report remains locally valid and must remain visible through record creation and submission preparation.

Similarly, `VF-TAX-IPSI-TRANSITION` does not reproduce AEAT's date-dependent severity. The validator has no receipt-date context and does not substitute a caller's generation timestamp or read a clock. Integrators must resolve that context before treating the local report as sufficient for submission.

## Offline integration work, 2026-09-06

The Apple adapter boundary, synthetic testkit, Java entry points, offline consumer samples, and local publication configuration add no new fiscal rules or official error-code mappings. They compose the existing baseline above. Hash/XML/QR golden and local XSD checks remain the source-backed verification; new synthetic fixtures are consumer-testing aids, not official response samples. A platform-specific regex dot-all option in the existing response extractor was replaced with the equivalent common-compatible character class; multiline parser fixtures run on JVM, Android, and supported Apple test targets.

No AEAT endpoint, website, or certificate was used for this work. The listed remote source versions were not refreshed. The response/batch schemas, WSDL, and error catalogue still need to be vendored and reviewed before new submission/correction semantics or an external release. See [implementation status](../implementation-status.md).

JVM schema tests now preload both vendored schemas and disable external DTD/schema access, avoiding the W3C DTD reference in the XML Signature schema. The vendored bytes and existing digest are unchanged. `FixtureSchemaTest` also validates the new synthetic registration and cancellation XML locally; this is structural schema evidence only.

## Review, Query and Preparation Work, 2026-09-06

The following additions use the original public contract snapshot in [`schemas-aeat`](../../schemas-aeat/README.md), with exact source URLs and SHA-256 in its manifest. Static documentation downloads did not involve authenticated portals, SOAP calls or personal certificates.

| Requirement / engineering invariant | Source and exact section/type | Implementation | Evidence |
| --- | --- | --- | --- |
| Batch order, 1–1000 records and header | `SuministroLR.xsd/RegFactuSistemaFacturacion`; `SuministroInformacion.xsd/CabeceraType` | `SubmissionBatchBuilder` | Common boundary/mismatch/hash tests; `AeatSchemaValidationTest` batch XSD and SOAP Body checks |
| SOAP 1.1 document/literal | `SistemaFacturacion.wsdl`, `soap:binding`, operation/body definitions | Batch builder and query tool | Parsed SOAP Body matches the archived operation XSD |
| Timestamp and exact Unicode/XML text | Common XSD `FechaHoraHusoGenRegistro`, `TextMax*Type`, `TextoIDFacturaType`; W3C XML Schema 1.0 Part 2 §3.2.7.1/3.2.7.3 and §4.3.1/4.3.3; XML 1.0 Fifth Edition §2.2 | `FiscalValues`, record validators | Calendar/offset, surrogate, Unicode-length and record schema tests; documented JAXP length-facet limitation |
| Preserve CR through XML parsing | XML 1.0 Fifth Edition §2.11 | Record/batch escaping (`&#13;`) | XML → DOM → hash-input regression |
| Regime enum membership | Common XSD `IdOperacionesTrascendenciaTributariaType` | Tax-breakdown validation | Allowed and disallowed regime fixtures |
| Response identity, operation, current/duplicate states | `RespuestaSuministro.xsd/RespuestaExpedidaType`; common XSD `OperacionType`, `RegistroDuplicadoType`, `EstadoRegistroSFType` | `AeatResponseParser`, `AeatResponseCorrelation` | Shared parser/correlation fixtures, including matching caller-selected subsanation/prior-rejection flags and rejecting contradictory echoes; schema-checked testkit responses |
| Partial acceptance and no new receipt for rejected submissions | Service specification v1.0.3 §3, pp.10–11; response XSD `RespuestaBaseType` | Synthetic fixtures and sample interpretation | Warning-only aggregate and rejected/duplicate CSV assertions |
| Wait representation | Common XSD `Tipo6Type`; response XSD `TiempoEsperaEnvio` | `AeatFlowControl.Known/Unknown` | Zero, bounded numeric, missing, invalid and future wait tests; no scheduling inference |
| Query role, period, paging, stored states | `ConsultaLR.xsd`, `RespuestaConsultaLR.xsd`; service specification v1.0.3 §6.4/11 | `tools:query` | Issued/received XSD requests, saved response fixtures, continuation and inconsistent/unsafe XML checks |
| Query authorization boundaries | Archived `errores.properties` codes 4132, 4140, 4112, 1261, 1272 | [Access guidance](query-access.md) | Document/source review only; no authentication claim |
| Component declaration responsibilities | RD 1007/2023 art.13; Orden HAC/1177/2024 art.15.1–4; declaration FAQ/example v0.5.1 | [Guide](declaration-guide.md), [unsigned worksheet](declaration-template.md) | Unsigned documentation only; producer review remains required |
| Factory output snapshots, supplied-hash checks, delivery uncertainty and redaction | Library engineering invariants, not extra XSD or legal obligations | Record factory, batch/facade, transport results and diagnostics | Mutation/Java getter tests, deterministic replay, typed preflight/failure and redaction tests |
| Bounded JVM transport body and complete-response deadline | Library resource and timeout limits, not extra fiscal rules | `JvmAeatMtlsTransport` | Disposable loopback mTLS identity, size-cap, pre-header timeout and stalled partial-body timeout tests |

The runtime parser deliberately preserves unknown protocol values rather than claiming full schema validation. Response matching does not imply acceptance. Published incidence groups are typed with explicit source metadata and location; the JVM contract test compares all 247 archived codes and verifies that unlisted codes remain unknown. Automatic correction decisions belong to the host. Conditional fields and the rules listed above are implemented; remaining arithmetic, identity, context and live interoperability work is tracked in the [release plan](../release-plan.md).
