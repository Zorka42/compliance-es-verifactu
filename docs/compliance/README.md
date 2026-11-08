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

The [ZA-126 date contract](current-date-validation.md) traces current-date rules to validation v1.2.2 §§3.1.3.1 and 3.1.3.7 and errors 1112/1125/1134/1173. `RegistrationCurrentDateValidation.kt` and common core/AEAT tests cover explicit context, unknown and disputed-boundary warnings, deterministic calendar comparisons and preparation/batch propagation.

The [ZA-120 public-source review](source-gaps-2026-11-06.md) rechecks the exact validation PDF, error catalogue and common-XSD bytes and archives additional BOE/AEAT evidence. The zero-rate surcharge and supplied EL/GR–XI/GB mappings remain unresolved; entity/K/L/M checksum warnings remain deliberate. No new fiscal rejection is derived from the research.

The [shared XML regression corpus](../xml-regression-corpus.md) traces parser whitespace and Unicode behavior to the archived XML 1.0 Fifth Edition §§2.2, 2.3, 2.10 and 2.11. `AeatResponseParser.kt` now uses XML `S` rather than the broader Kotlin whitespace set for structural text. `AeatXmlRegressionCorpus.kt` and `AeatXmlRegressionTest.kt` provide malformed/ambiguous/extension and exact resource-limit checks through the public parser on JVM, Android host and Apple.

The [fiscal identifier contract](identifier-validation.md) traces invoice-number characters, Spanish NIF formats, DNI/NIE check characters, VAT country formats and GB/XI dates to the archived official sources. `FiscalIdentifierValidator.kt`, `FiscalRecordIdentifierValidation.kt` and `FiscalIdentifierValidationTest.kt` retain the source-to-code-to-test relationship. Entity/K/L/M checksum warnings and inferred EL/GR–XI/GB country mappings remain explicit validation evidence.

The exact decimal checks and their synthetic boundary fixtures are described in [fiscal arithmetic](fiscal-arithmetic.md). The implementation uses the archived validation catalogue 1.2.2 §§3.1.3.15.7–17 and error codes 1140/1142/1143/1144/1150/2005/2006. `FiscalArithmeticValidation.kt` and `FiscalArithmeticValidationTest.kt` retain the source-to-code-to-test relationship, including warning-only totals and the documented regime exceptions.

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

The tax tables use the original public files archived in [`schemas-aeat`](../../schemas-aeat/README.md); its manifest records the download URLs and SHA-256 values. Local validation covers the listed rules and does not establish full AEAT acceptance. Exact sign/amount arithmetic and invoice-total tolerances are now implemented in [fiscal arithmetic](fiscal-arithmetic.md). Identifier business formats are checked separately from structural parsing. Census status, unsupported checksum algorithms and actual service acceptance remain outside local verification.

The zero-rate IVA surcharge rule from 2024-10-01 needs source resolution before release: validation catalogue v1.2.2 §3.1.3.15.3 documents the earlier zero-surcharge interval (2023-01-01 through 2024-09-30), while archived `errores.properties` error 1170 specifies 0.26 thereafter (also compare 1165 and 1277). The library emits `VF-TAX-ZERO-RATE-UNRESOLVED` as its own warning for the later period, with no asserted AEAT error code or acceptance outcome. A warning-only report remains locally valid and must remain visible through record creation and submission preparation.

`VF-TAX-IPSI-TRANSITION` retains an unknown-context warning when no receipt date is supplied. [Explicit validation context](validation-context.md) now resolves its date-dependent severity using a caller-supplied AEAT receipt date, preserving that input through factory, batch and facade validation. It never substitutes generation time or reads a clock. The independent `aeatCurrentDate` enables the [current-date checks](current-date-validation.md) without establishing AEAT’s actual date; generation-time, census and history facts remain unestablished.

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

The runtime parser deliberately preserves unknown protocol values rather than claiming full schema validation. Response matching does not imply acceptance. Published incidence groups are typed with explicit source metadata and location; the JVM contract test compares all 247 archived codes and verifies that unlisted codes remain unknown. Automatic correction decisions belong to the host. Remaining source gaps, application activation, receipt reconciliation and live interoperability work are tracked in the [release plan](../release-plan.md).

## Sandbox console composition, 2026-11-06

`tools/sandbox` composes the existing library behavior without adding fiscal rules. It uses the original archived `SistemaFacturacion.wsdl` SOAP 1.1 operation/TEST addresses, `SuministroLR.xsd/RegFactuSistemaFacturacion` and `SuministroInformacion.xsd` record types; exact snapshot URLs and digests remain in [the contract manifest](../../schemas-aeat/manifest.tsv). Prepared registration/cancellation batches are checked against packaged original XSDs with external access disabled. `SandboxPreparationTest` covers deterministic bytes, supplied chain state, XSD validity and exact duplicate replay. `SandboxInspectionTest` exercises public parser/correlation semantics and conservatively requires review for contradictory error diagnostics. Response scenarios come from the explicitly synthetic testkit, whose existing contract tests validate them against `RespuestaSuministro.xsd`; they are not captured AEAT responses.

Digest confirmation, strict TEST routing, private non-overwriting artifact files, start-before-send evidence, selected-alias TLS and redacted summaries are host-tool engineering controls, not new legal obligations. `SandboxSendTest` verifies delivery/uncertainty boundaries; `SandboxMtlsTest` checks actual HTTPS with newly generated disposable identities on localhost. No operational AEAT request or existing personal certificate is needed for these tests. Live authorization/acceptance and current upstream-version review remain open; see [tool usage and limits](../../tools/sandbox/README.md).


## Native TEST consultation host tool

`verifactu sandbox invoices list` composes the archived `ConsultaLR.xsd` / `RespuestaConsultaLR.xsd` role, period and continuation contract with `SistemaFacturacion.wsdl` SOAP 1.1 document/literal binding, empty SOAPAction and standard TEST service address. Exact versions/digests remain in [the manifest](../../schemas-aeat/manifest.tsv); query rights and stored-state semantics remain in [query access](query-access.md). No new fiscal rule is introduced.

`KeychainQueryTest` covers both roles, complete/partial reads, NIF/period/role correlation, continuation, identity consistency, SOAP/HTTP errors and start-before-transport evidence. `NativeQueryProcessTest` verifies correlated native receipts and sanitized delivery failures. `CliTest` checks command discovery, local taxpayer setup, defaults and display semantics. Swift `QueryGuardTests` rejects non-query and non-TEST inputs before credential selection; `NativeTransportTests` verifies cancellation plus actual loopback client-certificate TLS with fresh memory-only identities, bounded responses and default trust rejection.

Shareable `network.log` reports distinguish the fixed TEST request URL/domain from each native HTTP response URL. Receipt tests reject production/mismatched/missing response routes; `QueryNetworkEvidenceTest` excludes unexpected URLs and certificate identifiers from reports. Native loopback tests verify observed response URLs for XML, non-XML, oversized and refused-redirect responses, and an absent URL on trust failure. Source: the archived WSDL `SistemaVerifactuPruebas` port; see [routing evidence and limits](query-access.md#wire-contract).

The unified host CLI also exposes the existing sandbox bundle workflow and separately confirmed TEST submission through Keychain. `SuministroLR.xsd` defines `RegFactuSistemaFacturacion`, `Cabecera` and the `RegistroFactura` choice of `RegistroAlta` / `RegistroAnulacion`; `SuministroInformacion.xsd` defines header and invoice identifiers. The JVM rebuilds and XSD-validates the exact bundle before transport. Native `SubmissionGuardTests` independently restrict explicit write intent to one matching record at an allowlisted TEST endpoint; the query guard still refuses writes. Both modules' `SandboxWorkflowTest` cover local command execution, confirmation/tamper rejection, shared response/correlation semantics, one-attempt evidence, cancellation and unknown delivery. `NativeTransportTests` additionally transmits exact synthetic submission bytes through real loopback mTLS with fresh memory-only identities. The one-record restriction, SHA-256 confirmation, chooser intent and separate send command are host controls, not additional AEAT requirements. See the [unified workflow](../../tools/query/README.md#review-and-execute-a-test-scenario) for scope and live-verification limits.

Native certificate selection and HTTPS use Apple's [SFChooseIdentityPanel](https://developer.apple.com/documentation/securityinterface/sfchooseidentitypanel), [URLCredential identity initializer](https://developer.apple.com/documentation/foundation/urlcredential/init(identity:certificates:persistence:)) and [memory-only test import](https://developer.apple.com/documentation/security/ksecimporttomemoryonly). Certificate selection/OS authorization, artifact privacy, application limits and no automatic retries are host engineering controls, not legal requirements. Normal tests access neither real Keychain identities nor AEAT. Live TEST interoperability and authorization remain unverified.


The host CLI's optional `--issuer NAME` filter compares the public leaf certificate's issuer CN exactly, as exposed by Apple's [SecCertificateCopyValues](https://developer.apple.com/documentation/security/seccertificatecopyvalues(_:_:_:)) issuer-name property and common-name OID. It does not change trust evaluation, certificate storage or AEAT authorization rules. `CertificateIssuerTests` checks issuer versus a misleading subject and a missing CN with freshly generated certificates; `NativeTransportTests` checks inclusion/exclusion with memory-only identities. JVM tests verify forwarding across both query roles, pagination and the native process boundary, plus rejection of blank/control/oversized filters before credential access. No existing user identity is required.


## ZA-111 manual matrix preparation, 2026-11-07

The [operator guide](../aeat-test-matrix.md) now composes public contract behavior in host tools. This extends the earlier one-record native gate to explicitly counted registration batches (1–1000); single cancellation and the separate read-only gate remain unchanged. Published library behavior and contract snapshot bytes are unchanged.

| Test condition | Exact archived source | Implementation and evidence |
| --- | --- | --- |
| F1 self-recipient synthetic data | Validation v1.2.2 revision history v1.0.3, 18-03-2025: equal issuer/recipient prohibition removed | `registrationDraft`, `MatrixTest`; owner-run TEST reads confirmed both roles for the selected identity/dataset |
| Warning after an accepted record of the same SIF/taxpayer | Validation v1.2.2 section 4.3.1; error catalogue 2007 | `warning-first`, correlation/code checks and correction-chain tests |
| Explicit pre-cutoff issue-date rejection | Validation v1.2.2 section 3.1.3.1; error 1152 | `rejection-date` uses 27-10-2024; actual TEST acceptance remains an unresolved discrepancy; `MatrixEvidenceTest` prevents a false PASS |
| Exact duplicate and subsanation echo | Error 3000; common XSD `SubsanacionType`/`OperacionType` | Immutable SOAP replay, correction flags and response correlation tests |
| 10001 records in 11 confirmed batches, both query roles and real pagination | Service v1.0.3 section 3, section 6.4/11; submission/query XSD cardinalities | `PaginationPlan`, full offline rehearsal and XSD-backed 10000+1 response tests |
| Reply wait controls next manual send | Service v1.0.3 section 6.4.4.1; Orden HAC/1177/2024 art.16.2 | Deadline guard, stored actual timing and interval report; no automatic scheduling, full-batch early-send exception unused |
| Digest/count confirmations, private artifacts and TEST routing | Host engineering controls, not extra fiscal requirements | JVM/native tests; real loopback batch mTLS using disposable memory-only identities |

Synthetic responses are explicitly labelled and cannot satisfy live evidence. No declaration is signed by configuring a TEST producer identity. Live outcomes, authorization, protocol differences and personal-certificate selection require owner execution and external verification; normal checks remain offline from AEAT.

Owner-run TEST evidence and its limits are recorded in the [operator guide](../aeat-test-matrix.md): initial acceptance, duplicate 3000, warning 2007, subsanation, cancellation and both query roles were observed. The pre-cutoff record was unexpectedly accepted and subsequently listed as `Correcto`; the negative scenario stays REVIEW. A live warning 2004 reported a 240-second generation-time tolerance, which is not a numeric rule established by the archived specification. Live pagination is postponed. These observations do not change published validation, original contract bytes or production authorization. Raw private evidence remains outside Git; the rejection-mismatch regression uses synthetic data only.

The follow-up [date-rule audit](validation-context.md#date-rule-audit-after-owner-run-test-verification-2026-11-07) separates source coverage from implementation coverage. The fixed 28-10-2024 lower bound is now implemented by `RegistrationDateValidation.kt` in normal `RegistroAltaValidator` validation. It returns `VF-DATE-1152` at `invoice.issueDate`, error severity, AEAT code 1152 and validation v1.2.2 §3.1.3.1 p.8 as source. `RegistrationDateValidationTest` covers before/boundary/after and historical references/cancellations; `FiscalSubmissionPreparationTest` covers preparation and batch revalidation. Valid synthetic fixtures are updated; generic calendar parsing, raw official hash vectors and original contract files are preserved. The safety regression in `MatrixEvidenceTest` still checks harness classification rather than the fiscal rule. The remaining unambiguous current-date rules are now implemented with independent `aeatCurrentDate` input; unknown context and disputed year/leap boundaries retain explicit warnings. See the [ZA-126 contract and tests](current-date-validation.md). Passing schemas or line coverage does not prove complete business-rule coverage.

The separate host `cancellation-missing` scenario expects ordinary cancellation of an absent invoice to be rejected with 3002: validation v1.2.2 Annex 6.2 p.28 ERROR (6), plus `errores.properties` 3002. `MissingCancellationTest` and unified `SandboxWorkflowTest` verify target/chain separation, normal validation, schema-valid synthetic rejection, conservative REVIEW outcomes and native-boundary forwarding. The owner-run outcome on 2026-11-08 was a matched HTTP 200 rejection with code 3002 and known 60-second flow control, scenario PASS and exit 0. Saved request digest/bytes and fixed TEST request/response URLs were checked; local saved-response reinspection also passes. This verifies this rejection case, while the date discrepancy and postponed live pagination remain open. The legacy date fixture uses a narrow explicit host-only construction path to preserve saved bytes while normal registration validation rejects them; it cannot waive other local errors or TEST transport controls.
