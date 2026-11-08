# AEAT TEST matrix (ZA-111)

This host-tool workflow prepares the entire manual interoperability matrix. It does not run AEAT operations implicitly, issue invoices for real business activity, establish production readiness or sign a declaración responsable. Production is unavailable in these commands. The repository launcher must be run from the checkout; the installed `verifactu` entry point can be used from another directory.

## Owner-run missing-cancellation result, 2026-11-08

The owner executed the separate `cancellation-missing` case. The saved request matches its reviewed SHA-256 and the exact attempt request bytes. Request and native response URLs both identify the fixed HTTPS `prewww1.aeat.es` TEST service; redirects were refused and normal system trust remained enabled. The response was HTTP 200, `RESPONSE_RECEIVED`, matched to the cancellation target and operation, with aggregate/record rejection and code **3002**. Expected scenario verification is **PASS**, CLI exit 0; the reply supplied a 60-second wait. Local reinspection of the saved response also passes without network or certificate access.

This externally verifies the ordinary absent-record rejection path. The agreed small functional suite now has live registration, duplicate, warning, subsanation, cancellation, rejection and both-role consultation evidence. No repeat send or additional owner command is needed for this case. It does not change the original pre-cutoff acceptance/REVIEW result or unlock the original matrix. Live 10001-record pagination remains postponed and full ZA-111 is incomplete. Private request/response XML, identities, invoice numbers and certificate data remain outside Git; existing wholly synthetic 3002 fixtures/tests cover the public protocol behavior.

## Owner-run TEST observations, 2026-11-07

The owner executed the small suite with synthetic invoice data through native certificate selection. Reviewed private responses and request/response URL logs identify the fixed `prewww1.aeat.es` TEST service, HTTP 200 and matched response correlation. No private identities, invoice numbers, certificate references or raw live XML are reproduced here.

| Case | Observed result |
| --- | --- |
| Registration | Accepted without errors |
| Exact duplicate | Rejected as duplicate, code 3000; scenario PASS |
| Intentional first-record warning | Accepted with errors, code 2007; scenario PASS |
| Subsanation | Accepted without errors; response echoed `Subsanacion=S` |
| Cancellation | Accepted without errors; subsequent issued/received queries both showed `Anulado` |
| Pre-cutoff issue date | **Unexpected acceptance** of 27-10-2024; scenario REVIEW, not PASS |

Both query roles returned the valid registration as `Correcto` and the cancelled invoice as `Anulado` for November 2026. A separate October 2024 consultation returned the unexpectedly accepted pre-cutoff invoice as `Correcto` in both roles. Query period follows the operation date, or issue date when absent, rather than the submission month (service v1.0.3 section 6.4).

The public validation specification v1.2.2 section 3.1.3.1 and archived error 1152 still prohibit an issue date before 28-10-2024. The observed TEST acceptance does not explain the discrepancy, repeal that rule or establish production behavior. Keep the negative case in REVIEW; do not weaken validation or change the expected result merely to obtain PASS. Further writes in this session remain blocked. `MatrixEvidenceTest.acceptedPreCutoffRecordCannotPassTheRejectionCase` reproduces this mismatch with entirely synthetic inputs and responses.

An earlier request prepared roughly half an hour before sending was accepted with warning 2004. That live reply stated a 240-second tolerance; the archived public message does not specify a number. Treat this as an observation, not a universal constant. Run `prepare` immediately before `show` and the reviewed `send`, without a chat round trip in between. Repeating `prepare` does not refresh an existing bundle; never alter or discard prior attempts to bypass evidence guards.

Live replies requested 60 seconds between writes, and the reviewed small-suite attempt intervals respected those deadlines. This does not verify every flow-control branch. The owner postponed the 10001-record live pagination test; offline paging tests pass, but live paging remains unverified. The full ZA-111 matrix is incomplete. The strict `matrix query --scope small` gate also remains blocked by the unexpected rejection-case result. The final read-only evidence was obtained with `sandbox invoices list`; it is not relabelled as a successful full matrix report.

## Identities and initialization

The taxpayer issuing the synthetic records and the software producer are separate identities. `config init` stores the taxpayer only. A producer file contains exactly `nif` and `name` in UTF-8 Java properties. Use agreed identities in private files outside Git, not the repository's synthetic fixture identities. The producer metadata identifies the TEST harness; it is not a signed compliance declaration. See the [component declaration guide](compliance/declaration-guide.md) before release.

`verifactu sandbox matrix init --producer-file FILE --out NEW_DIRECTORY` reads the configured taxpayer, or an explicit `--party-file FILE`. It validates fiscal identities and creates a private session with:

- `base.properties`, `party.properties` and `matrix.properties`: agreed inputs, current Madrid month, unique invoice prefix and SIF installation;
- `plan.tsv`: exact case names, dependencies and record counts;
- `cases/` and `queries/`: new per-case bundles/attempts and consultation evidence.

Directories are 0700 and files 0600. Initialization is local, with no certificate, Keychain or network access. It never overwrites a session. Keep the entire session private; generated fiscal XML contains real identity metadata even though the invoice data is synthetic. Reports omit identities, invoice numbers and remote descriptions; the request digest remains inspectable.

The matrix uses F1 registrations with the taxpayer also explicitly named as recipient. The archived validation history removed the equal-NIF prohibition for autoconsumo in March 2025. The owner-run TEST observations above confirm visibility in both query roles for this dataset and selected identity; they do not establish other taxpayers' authorization. If AEAT behaves differently, stop and retain evidence; do not substitute empty received results or invent authorization. These are synthetic TEST cases, not a tax classification for an actual self-supply.

## Local rehearsal

`verifactu sandbox matrix rehearse --run DIRECTORY --out NEW_DIRECTORY` generates every actual request, validates its XSD, constructs synthetic responses and correlates every response line. It covers all 10001 pagination records as well as the small suite. Saved artifacts are labelled `OFFLINE_SIMULATION` and are isolated from live case attempts. Rehearsal cannot satisfy live acceptance, flow-control or query evidence.

`verifactu sandbox matrix show --run DIRECTORY` displays the plan and current evidence states. No network or Keychain access occurs.

## Small suite

Run each case separately: `prepare`, `show`, then a separately authorized `send`. There is no command that sends the entire suite automatically.

| Case | Request | Expected observation | Dependency |
| --- | --- | --- | --- |
| `registration` | New F1 record, synthetic amount 121.00, first chain record | Clean matched acceptance | None |
| `duplicate` | Exact original registration SOAP bytes and digest | Duplicate classification, published code 3000 when present | Accepted registration |
| `warning` | New invoice deliberately marked `PrimerRegistro=S` for the same SIF/taxpayer | Accepted with errors, code 2007 | Accepted registration |
| `subsanation` | Same warning invoice with `Subsanacion=S`, linked to the warning record's hash | Clean matched acceptance with the correction flag echoed | Expected warning |
| `cancellation` | Cancellation of the corrected warning invoice, continuing its chain | Clean matched cancellation acceptance | Accepted subsanation |
| `rejection` | New invoice with explicit issue date 27-10-2024 | Rejected with code 1152 | Accepted cancellation |

All positive cases retain normal fiscal validation and hashes. Negative cases use schema-valid inputs that deliberately exercise published remote checks; there is no general validation bypass. Rejection is last, so further records do not guess how to continue after the negative case. The warning deliberately starts a new chain as the condition under test; the correction links to that warning record. This is subsanation of a fiscal record, not an R-type credit-note scenario.

`verifactu sandbox matrix prepare --run DIRECTORY --case NAME` generates the record timestamp at that explicit invocation, and prints a concrete preview/send command including the request SHA-256 and exact count. A previously prepared bundle is immutable: repeating `prepare` cannot silently refresh its timestamp, invoice identity or hash. Prepare each case shortly before reviewing it. The operator must maintain a correctly synchronized clock; the tool does not alter the clock.

`verifactu sandbox matrix show --run DIRECTORY --case NAME` prints the exact SOAP XML, TEST endpoint, count and SHA-256. Inspect it before confirming the send. Directional control characters are escaped for terminal viewing; the saved request bytes remain unchanged.

`verifactu sandbox matrix send --run DIRECTORY --case NAME --confirm-sha256 DIGEST --confirm-records COUNT [--issuer NAME]` revalidates the exact bundle and dependency evidence before creating a new numbered attempt. Native macOS selection independently checks the SHA-256, count and TEST route before touching identities. Each write requires fresh explicit selection; no key export, password file, saved identity reference or certificate import is used. The picker displays TEST WRITE, count, operation and digest. `--issuer "AC FNMT Usuarios"` is an optional exact issuer CN filter.

An expected negative response returns success **for that matrix case** only when its code, identity, operation and flags match. It still prints the actual warning/rejected/duplicate classification. A clean response to the warning/rejection case is unexpected and requires review.

## Flow control and delivery uncertainty

The previous reply's `TiempoEsperaEnvio` sets the earliest next send. This manual harness conservatively waits until that interval has elapsed even for a full 1000-record batch; the official full-batch early-send exception is not used. The command reports the deadline and exits before identity selection if it is too early. There is no sleeping, background process, automatic retry or scheduler.

Each attempt records start/completion times; the report checks actual intervals against prior replies. Missing/unknown wait data or unexpected replies require review. A live session with uncertain delivery blocks **all further writes**, including the separate pagination installation, until reconciled. Only a positively established `NOT_SENT` attempt can be manually repeated with the same immutable bytes. Completed cases cannot be implicitly replayed. Do not delete evidence to bypass a block.

## Nonempty queries

After the small suite, `verifactu sandbox matrix query --run DIRECTORY --scope small [--issuer NAME]` explicitly performs read-only consultations for both directions and the saved session month. The report requires the registration to be visible as `Correcto` and the warning invoice to be visible as `Anulado` in each role. All raw consultation evidence remains private; the command prints summaries rather than invoice rows.

## Actual pagination

The query contract returns at most 10000 records per page and has no caller-selected page size. A real pagination test therefore needs more than 10000 matching records. The plan provides `pagination-01` through `pagination-11`: ten batches of 1000 and a final batch of one. Every batch needs its own prepare, XML/digest review, explicit count confirmation and native selection. There is no implicit bulk upload.

Pagination has a separate SIF installation suffix `-paging`, a unique invoice prefix and a sequential chain across all 11 batches. It does not continue the deliberately rejected small-suite record. The synthetic amount per invoice remains 121.00; these records must never go to production.

After all 11 accepted batches, `verifactu sandbox matrix query --run DIRECTORY --scope pagination [--issuer NAME]` consults both roles. Verification requires all 10001 expected invoice identities, dates and `Correcto` states, at least two actual pages per role, correct continuation requests, no repeated cursors/invoices, and complete native HTTP evidence. A single page, a partial list or unrelated pre-existing records cannot satisfy the test.

## Report and evidence review

`verifactu sandbox matrix report --run DIRECTORY` is local. It re-reads private bundles, request digests, response lines, flow-control timing and complete query pages. Query evidence must follow the scope's writes, match the session metadata, reproduce the saved TSV exactly, and show the fixed TEST request/response URL and successful HTTP results. Missing/unexpected evidence produces INCOMPLETE/REVIEW and a nonzero exit code. Simulations cannot substitute for live attempts.

The live host writes each request/start marker before transport, and retains the native response URL, receipt/body, network log, interpreted summary and outcome. A started attempt without complete evidence is uncertain. Client evidence supports observed routing to the WSDL TEST port; it does not independently prove AEAT internal database isolation.

Share the output of `matrix report` and the relevant `network.log`/`outcome.properties` for review. Do not share raw XML, invoice TSV, private inputs or native certificate fingerprints. Preserve originals outside Git; only reviewed, anonymized public-contract regression fixtures belong in the repository. Any observed protocol difference must update [traceability](compliance/README.md) before ZA-111 can be externally verified.

## Sources and deterministic verification

Exact archived URLs, versions and digests are in [schemas-aeat/manifest.tsv](../schemas-aeat/manifest.tsv). The scenario derivation uses:

- Service specification v1.0.3, section 3 (1000-record submission limit), section 6.4/11 (queries/10000-record paging), section 6.4.4.1 and Orden HAC/1177/2024 art.16.2 (flow control).
- Validation specification v1.2.2, revision history v1.0.3 of 18-03-2025 (equal issuer/recipient NIF allowed), section 3.1.3.1 (issue-date cutoff) and section 4.3.1 (misused first-record flag).
- Archived `errores.properties`: 1152, 2007 and 3000. `SuministroInformacion.xsd` correction/operation types and `SuministroLR.xsd` submission structure.

`MatrixTest` generates the full dataset and checks chaining, immutable retries, exact expected codes, flow deadlines and uncertainty blocks. `MatrixQueryTest` runs the actual query engine and XSD validation with complete synthetic 10000+1 pages in both roles, then verifies report rejection of wrong states, missing data, changed lists, simulation markers and production URLs. Native tests check explicit batch counts and transmit 1000 exact synthetic records over real loopback mTLS with disposable memory-only identities. Normal verification never contacts AEAT or enumerates personal Keychain identities.

### Local correction and separate negative follow-up

Normal registration validation now rejects pre-28-10-2024 issue dates with structured AEAT 1152 evidence; historical dates and original vectors are preserved. This does not reinterpret the owner's actual acceptance or make the existing rejection case PASS. The date source discrepancy and unsent clarification draft remain in [validation context](compliance/validation-context.md#public-source-recheck-and-live-discrepancy).

The separate [`cancellation-missing` workflow](../tools/sandbox/README.md#negative-test-cancellation-of-an-absent-invoice) prepares an ordinary cancellation of an absent invoice, expecting 3002. It has its own bundle/digest/attempt and requires a reconciled chain tail, distinct target and owner-established absence. Local simulation is covered; the owner-run 3002 rejection was verified on 2026-11-08. It does not unlock the old matrix or prove live pagination.
