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

Future implementation pull requests must replace `TBD` cells with exact source versions, implementation paths, and test paths.

## Offline integration work, 2026-09-06

The Apple adapter boundary, synthetic testkit, Java entry points, offline consumer samples, and local publication configuration add no new fiscal rules or official error-code mappings. They compose the existing baseline above. Hash/XML/QR golden and local XSD checks remain the source-backed verification; new synthetic fixtures are consumer-testing aids, not official response samples. A platform-specific regex dot-all option in the existing response extractor was replaced with the equivalent common-compatible character class; multiline parser fixtures run on JVM, Android, and supported Apple test targets.

No AEAT endpoint, website, or certificate was used for this work. The listed remote source versions were not refreshed. The response/batch schemas, WSDL, and error catalogue still need to be vendored and reviewed before new submission/correction semantics or an external release. See [implementation status](../implementation-status.md).

JVM schema tests now preload both vendored schemas and disable external DTD/schema access, avoiding the W3C DTD reference in the XML Signature schema. The vendored bytes and existing digest are unchanged. `FixtureSchemaTest` also validates the new synthetic registration and cancellation XML locally; this is structural schema evidence only.
