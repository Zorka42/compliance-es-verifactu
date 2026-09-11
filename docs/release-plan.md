# Remaining Work and Verification Plan

Assessment: 2026-09-11. This is a pre-release open-source Kotlin/Java library, not a complete SIF or an externally verified compliance claim. The authoritative backlog is [Zorka Accounting – Tasks](https://app.notion.com/p/3bd4f302230980d3bb72ffbd0068aa8b), project `Verifactu`. Current local evidence is recorded in [implementation status](implementation-status.md).

## Implemented foundations and next work

ZA-113–115 now implement the decimal checks, fiscal identifier formats and explicit receipt-date context described below. ZA-116 adds real SQLite persistence in an optional local app integration profile. ZA-117 rehearses local candidate publication and Java consumption. These changes require external verification before `Done`; none establishes AEAT acceptance or a Central release.

The next offline tasks are:

1. [Connect fiscal configuration to the real app issue action](https://app.notion.com/3d84f302230981f58c3dd5413adb6f58): supply complete fiscal inputs and connect the transactional entry point to the existing desktop flow.
2. [Correlate durable receipts and expose reconciliation state](https://app.notion.com/3d84f302230981b89fa6fa234ca2eac2): interpret saved responses against saved identity/operation evidence, with restart tests.
3. [Resolve remaining public-source gaps](https://app.notion.com/3d84f302230981109fadf80c8a0a74b6): zero-rate surcharge, special VAT-country pairs and unsupported checksum algorithms.
4. [Expand the parser and API regression corpus](https://app.notion.com/3d84f3022309812c95dacc865eb5ba17): malformed inputs, resource limits, unknown protocol additions and Java/native compatibility evidence.
5. Complete the release-owner inputs and agree on the separately authorized AEAT test stage described below. The offline issued/received query tool is ready for saved fixtures, but has not read this taxpayer's records.

| Work | Outcome needed | Verification |
| --- | --- | --- |
| [Decimal consistency](https://app.notion.com/3d84f30223098134a37ae951ebc637dc) | Extend the conditional-field matrix with exact arithmetic, sign/tolerance checks, simplified-invoice amount limits and total consistency warnings | Archived validation PDF sections 15.7, 15.8, 16, 17; exact decimal positive/negative boundaries and regime exceptions on every supported target |
| [Explicit validation context](https://app.notion.com/3d84f30223098138832ad5d3deeefce7) | Represent receipt-date and history-dependent checks without a hidden clock or inventing AEAT knowledge | Supplied/unknown context tests; IPSI transition at 2027-01-01 must use receipt date, never record generation time |
| [Fiscal identifier formats](https://app.notion.com/3d84f302230981bb8953da00924e4331) | Extend structural NIF/IDOtro checks using exact official format and checksum rules | Archived sources and cross-platform fixtures; keep identity/census existence outside offline validation |
| [Durable app integration](https://app.notion.com/3d84f302230981b289c8ede9e5d008ba) | Wire the tested adapter contract into app-accounting's actual issue transaction and storage | Real database atomicity, unique finalization, per-chain conflict and restart tests with fake transport; preserve app ownership of DB, queue, UI and credentials |
| Parser/resource review | Fuzz malformed XML, resource limits, unsupported protocol additions and Unicode boundaries; finalize API compatibility policy and native API checks | Same input corpus on JVM, Android and Apple; Java consumer tests and versioned API baselines |
| Transport interoperability | Verify the implemented JVM adapter against AEAT and the host-provided Android/Apple adapters on their real networking stacks | Local JVM mTLS covers identity, SOAP headers, bounded bodies and full-response deadlines; actual AEAT and device interoperability remain separate evidence |
| ZA-85 Maven release preparation | Protected tag workflow, Central Portal staging endpoint, in-memory signing and GitHub Release automation are configured | Confirm namespace ownership, provide dedicated secrets, then perform the separately authorized first staging/release |
| ZA-86/88 governance/readiness | `main` protection and release governance are configured | Release checklist, dependency/license audit, legal/technical snapshot review, producer/component declaration and actual signed-release evidence |

The executable preparation checklist is [release-readiness-checklist.md](release-readiness-checklist.md). It distinguishes completed offline evidence from the secrets, producer decision, source refresh, and first external release that must remain manually controlled.

The library now combines conditional F1/F3/R1–R5 data, exact arithmetic, fiscal identifier validation, explicit receipt-date context, catalogue semantics, Unicode provider checks and loopback mTLS. Public unchecked record constructors remain low-level APIs. Passing local validation is not proof of complete fiscal compliance: source ambiguities, census/history evidence, application activation and service interoperability remain explicit.

Core validation uses XML Schema character counts. JVM XSD and query tests probe the selected provider with a synthetic supplementary-character boundary and compare it to the archived AEAT schema. Providers counting UTF-16 units may conservatively reject valid supplementary text near a length boundary. No global JVM switch or truncation hides that difference. Android host and Apple common tests verify deterministic character semantics; these are not device XSD-provider or live AEAT validation claims. Check the intended runtime and, later, AEAT before release.

Supplementary invoice-number fixtures exercise the low-level XSD/parser contract. Factory validation now enforces the narrower service profile in validation PDF v1.2.2 §3.1.3.1 (error 1130: printable ASCII except character codes 34, 39, 60, 61 and 62). The unchecked serializer still preserves structural XML text. The unresolved zero-VAT/surcharge difference between PDF §3.1.3.15.3 and `errores.properties` 1170 remains an explicit warning; receipt-date context cannot resolve that operation-date rule.

## Where Certificates or AEAT Are Needed

| Stage | Credentials / service | What it proves |
| --- | --- | --- |
| Normal build, schema/golden tests, fake transport, offline query tool | None | Deterministic local behavior and compatibility with archived contracts |
| Loopback TLS adapter tests | Newly generated disposable test identities, if enabled; never an existing personal certificate | TLS/client identity wiring, timeout boundaries, bounded responses, redacted errors |
| Maven signing/publication | Dedicated artifact signing identity and Central credentials | Artifact provenance and publication; unrelated to the taxpayer's AEAT certificate |
| Component declaration preparation | No personal certificate; unsigned worksheet and producer evidence | Documentation readiness only; not a completed declaration or certification |
| AEAT test-environment integration | Separately authorized test endpoint, explicitly chosen suitable client certificate and agreed synthetic data | Actual SOAP/mTLS behavior, schema assumptions, acceptance/error/duplicate/flow-control behavior |
| Actual issued/received history | Separately authorized production consultation, explicit taxpayer identity and period, suitable certificate/consultation powers | Records returned by VERI*FACTU for that taxpayer and period; not every invoice in all systems |
| Production submission/correction/cancellation | Separate explicit operational authorization | Fiscal changes; never implied by approval to read invoice history |

No personal certificate, private key, authenticated AEAT portal, registration website, or SOAP endpoint was accessed in this work. Only public static sources were retrieved and archived. Do not discover or select certificates automatically. Provide paths/identities explicitly when that stage is authorized; never put secrets or real fiscal responses in the repository.

## Test Sequence

1. Keep ordinary CI entirely offline with respect to AEAT: core golden hashes, record/batch/response XSDs, schema-backed synthetic query fixtures, unsafe XML regressions, response correlation, attempt-state tests and archived-file digest checks.
2. Run JVM, Android host tests, iOS simulator and macOS tests; compile iOS device code. Execute the independent Maven consumer on Java 11 in CI. Device TLS and Intel Apple targets are separate coverage gaps.
3. Exercise optional transport against loopback fixtures and disposable identities. Capture exact attempt classification; an HTTP response is not fiscal acceptance.
4. After authorization, use the AEAT test environment. Agree on synthetic records and cleanup/retention behavior first; verify alta, cancellation, duplicates, warning/rejection, timeout uncertainty, flow control and both query roles. Keep any returned data outside tracked fixtures until anonymization and explicit review.
5. After separate production-read authorization, query one agreed month for both issued and received records using the query operation. Follow the returned pagination key; compare with user-provided expectations. Record authorization failures separately from empty query results. Supplier invoices are visible only where the supplier's VERI*FACTU record identifies this recipient.
6. Complete release review: regulatory/source versions, producer declaration responsibilities, full license inventory, signed artifacts and verified Maven consumption. Publish only after the release prerequisites and external permissions are concrete.

The [query access document](compliance/query-access.md) contains the exact operation, namespaces, rights and source versions. [tools/query](../tools/query/README.md) already prepares requests and inspects saved responses for both directions; adding live read transport is a later, separately authorized step.
