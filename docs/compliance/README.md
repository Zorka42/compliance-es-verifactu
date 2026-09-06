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
| Conditional registration models and validation | AEAT `SuministroInformacion.xsd`; validation catalogue | tikeV1.0; catalogue 1.2.2, sections 3.1.3 and 4.4 | `ConditionalFiscalModels.kt`; `FiscalRecords.kt` | `FiscalRecordFactoryTest` on common/JVM/Android/Apple targets | Implemented | 2026-09-06 |
| Typed incidence and subsanation interpretation | AEAT validation catalogue and `errores.properties` | catalogue 1.2.2, sections 4.2–4.4 | `AeatErrorCatalogue.kt`; `AeatResponseParser.kt` | `AeatResponseParserTest` | Implemented | 2026-09-06 |
| Supplementary-Unicode XSD length compatibility | W3C XML Schema `length` facets; AEAT XSD; observed JVM provider behaviour | `SuministroInformacion.xsd` `TextMax*Type` facets | `XmlCharacterCount.kt` | common validator boundary test and JVM schema fixture | Implemented | 2026-09-06 |

Future implementation pull requests must replace `TBD` cells with exact source versions, implementation paths, and test paths.

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
| Response identity, operation, current/duplicate states | `RespuestaSuministro.xsd/RespuestaExpedidaType`; common XSD `OperacionType`, `RegistroDuplicadoType`, `EstadoRegistroSFType` | `AeatResponseParser`, `AeatResponseCorrelation` | Shared parser/correlation fixtures on supported targets; schema-checked testkit responses |
| Partial acceptance and no new receipt for rejected submissions | Service specification v1.0.3 §3, pp.10–11; response XSD `RespuestaBaseType` | Synthetic fixtures and sample interpretation | Warning-only aggregate and rejected/duplicate CSV assertions |
| Wait representation | Common XSD `Tipo6Type`; response XSD `TiempoEsperaEnvio` | `AeatFlowControl.Known/Unknown` | Zero, bounded numeric, missing, invalid and future wait tests; no scheduling inference |
| Query role, period, paging, stored states | `ConsultaLR.xsd`, `RespuestaConsultaLR.xsd`; service specification v1.0.3 §6.4/11 | `tools:query` | Issued/received XSD requests, saved response fixtures, continuation and inconsistent/unsafe XML checks |
| Query authorization boundaries | Archived `errores.properties` codes 4132, 4140, 4112, 1261, 1272 | [Access guidance](query-access.md) | Document/source review only; no authentication claim |
| Component declaration responsibilities | RD 1007/2023 art.13; Orden HAC/1177/2024 art.15.1–4; declaration FAQ/example v0.5.1 | [Guide](declaration-guide.md), [unsigned worksheet](declaration-template.md) | Unsigned documentation only; producer review remains required |
| Factory output snapshots, supplied-hash checks, delivery uncertainty and redaction | Library engineering invariants, not extra XSD or legal obligations | Record factory, batch/facade, transport results and diagnostics | Mutation/Java getter tests, deterministic replay, typed preflight/failure and redaction tests |

The runtime parser deliberately preserves unknown protocol values rather than claiming full schema validation. Response matching does not imply acceptance. The error catalogue is archived as evidence; a complete incidence/subsanación mapping is still open. Core conditional fiscal fields and live interoperability remain unfinished; see [release plan](../release-plan.md).
