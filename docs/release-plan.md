# Remaining Work and Verification Plan

Assessment: 2026-09-06. This is a pre-release open-source Kotlin/Java library, not a complete SIF or an externally verified compliance claim. The authoritative backlog is [Zorka Accounting – Tasks](https://app.notion.com/p/3bd4f302230980d3bb72ffbd0068aa8b), project `Verifactu`. Current local evidence is recorded in [implementation status](implementation-status.md).

## Finish Offline First

| Work | Outcome needed | Verification |
| --- | --- | --- |
| Conditional fiscal models | Complete F1/F3/R1–R5 recipient, replacement, rectification, exemption and operation-specific fields; deterministic validation of supported combinations | Exact source/code/test matrix against archived common XSD and validation catalogue; positive/negative fixtures on every supported target |
| ZA-77 incidence and correction policy | Typed, source-backed incidence/subsanación/rechazo-previo behavior, with explicit unknown-code handling | Catalogue-backed fixtures and manual review of each mapped rule; no invented retry decisions |
| ZA-74/76 outcome completeness | Integrate the remaining incidence semantics; document reconciliation/retry decisions for ambiguous delivery and duplicates | Full matching, unknown and duplicate branches, preserving original request/hash/timestamp; application explicitly decides retries |
| ZA-89–91 workflow completion | Consolidate the current pure preparation facade and host-owned attempt contract into the final public API; verify persistence/recovery against an integrating application | Deterministic cross-module suite, atomic host transaction/chain locking tests, crash/restart/replay tests outside the library |
| ZA-93 app adapter | Map finalized app invoice DTOs, explicit SIF metadata and fiscal result states to library types | App-side contract tests against the local preview; preserve app ownership of DB, queue, UI and credentials |
| Parser/resource review | Fuzz malformed XML, resource limits, unsupported protocol additions and Unicode boundaries; finalize API compatibility policy and native API checks | Same input corpus on JVM, Android and Apple; Java consumer tests and versioned API baselines |
| JVM transport completion | Verify SOAPAction/header behavior, bound response-body reads, reuse/close client resources appropriately and test cancellation/timeouts before live use | Loopback request capture and oversized-response tests, then separately authorized AEAT interoperability |
| ZA-85 Maven release preparation | Protected tag workflow, Central Portal staging endpoint, in-memory signing and GitHub Release automation are configured | Confirm namespace ownership, provide dedicated secrets, then perform the separately authorized first staging/release |
| ZA-86/88 governance/readiness | `main` protection and release governance are configured | Release checklist, dependency/license audit, legal/technical snapshot review, producer/component declaration and actual signed-release evidence |

The executable preparation checklist is [release-readiness-checklist.md](release-readiness-checklist.md). It distinguishes completed offline evidence from the secrets, producer decision, source refresh, and first external release that must remain manually controlled.

The current public unchecked record constructors remain low-level APIs. Creating an object is not proof that all conditional fiscal requirements are represented. The core NIF parser still supplies structural validation rather than a complete identity/checksum service.

The local JDK 22 XSD provider counts supplementary Unicode as UTF-16 units for length facets by default, while XML Schema defines character counts. Core tests retain Unicode character boundaries; XSD fixtures use supplementary text within both limits and full BMP boundaries. No internal JVM global switch is enabled to hide this difference. The query tool uses the selected JAXP provider and may reject supplementary text near a length boundary conservatively. Check this boundary against the intended validator and, later, AEAT before release.

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
