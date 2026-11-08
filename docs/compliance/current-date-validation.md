# AEAT current-date validation — ZA-126

## Evidence and scope

These rules use the original [validation specification](../../schemas-aeat/Validaciones_Errores_Veri-Factu.pdf), v1.2.2 dated 2026-04-08, sections 3.1.3.1 (p.8) and 3.1.3.7 (p.9), and [error catalogue](../../schemas-aeat/errores.properties). Their SHA-256 digests are respectively `426eb926fc098a36a163f66ca5f40d9e0847ca23300bbe5008979832d3513440` and `06519ceb23422bd6b0ad3bfb659e3007615050da4920781d12cff536481d5902`; source URLs and retrieval dates are in the [manifest](../../schemas-aeat/manifest.tsv). The archived developer FAQ was checked for date-boundary clarification; none was found in that bounded review. No new contract download, operational request or certificate access is needed.

This is local preflight validation of registration drafts. It does not explain the pre-cutoff TEST acceptance tracked by ZA-124 or establish AEAT acceptance. Generic calendar parsing, historical references, chain identities and cancellation targets retain their existing behavior.

## Explicit context

`ValidationContext.aeatCurrentDate` is an optional caller-supplied calendar date representing AEAT's current system date for these comparisons. It uses the existing `InvoiceIssueDate` type and `DD-MM-YYYY` format. No machine clock, generation timestamp, receipt date or planned send date is substituted. The host is responsible for the provenance and applicability of its input. A hypothetical date produces a conditional result, not evidence of AEAT's actual date.

`aeatReceiptDate` remains independent and controls only the IPSI transition rule. Neither field fills the other. Supplying both is allowed; the library does not infer remote chronology. The zero-argument and receipt-only Java constructors remain available through `@JvmOverloads`; Java callers supply both dates with the two-argument constructor. Data-class copy/component ABI baselines change before the first release.

```kotlin
val currentDate = (InvoiceIssueDate.parse("08-11-2026") as ValueResult.Valid).value
val context = ValidationContext(aeatCurrentDate = currentDate)
val report = RegistroAltaValidator.validate(draft, context)
val created = FiscalRecordFactory.createRegistration(draft, context)
val prepared = FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.TEST, context)
```

Context changes reports and whether preparation succeeds; it never rewrites fiscal fields, hashes, XML, QR data or timestamps. Batch preparation revalidates every registration with the supplied context, including records created under another context. Cancellation validation has no current-date context.

An absent date produces `VF-DATE-CONTEXT-UNKNOWN`, warning severity, at `validationContext.aeatCurrentDate`, without an AEAT error code. It states that the current-date checks were not performed. Independent structural, static cutoff and tax checks still run. A warning-only report remains locally valid; callers must inspect issues.

## Source-to-rule decisions

On 2026-11-08 the owner explicitly approved implementing unambiguous checks with warnings for disputed boundaries. This is a product decision about incomplete validation coverage, not an authoritative interpretation of missing AEAT details.

| Source | Local rule | Structured outcome |
| --- | --- | --- |
| §3.1.3.1; 1112 | Issue date after the supplied current date fails; equality passes this check. | `VF-DATE-1112`, error, `invoice.issueDate`, AEAT 1112 |
| §3.1.3.7; 1134 | Operation date before current date minus twenty calendar years fails; equality passes when the anniversary exists. | `VF-DATE-1134`, error, `conditionalData.operationDate`, AEAT 1134 |
| Same section | When the twenty-year anniversary is an invalid leap day, February 28 is disputed between clamping and rolling; earlier dates fail and March 1 or later do not fail this bound. | `VF-DATE-OPERATION-LEAP-BOUND-UNRESOLVED`, warning, no AEAT code |
| §3.1.3.7; 1125 | Dates beyond the following calendar year fail under either reading of the upper limit. | `VF-DATE-1125`, error, `conditionalData.operationDate`, AEAT 1125 |
| Same section | Preserve the band between the one-year anniversary and the end of the following calendar year as unresolved. | `VF-DATE-OPERATION-UPPER-BOUND-UNRESOLVED`, warning, no AEAT code |
| §3.1.3.7; 1173 | For operation after the current date, every IVA, IGIC or omitted-tax detail requires regime 14 or 15. Equality does not invoke this rule; IPSI and other-tax details do not invoke it. | `VF-DATE-1173`, error, `taxBreakdown.details[index].regimeCode`, AEAT 1173 |

Each issue cites the source version and section. Operation bounds apply only to supplied `FechaOperacion`; omission does not acquire an artificial value. Existing error 1146 for operation after issue remains independent and can coexist with 1173. Warnings never suppress other errors.

For current date 08-11-2026:

- Lower bound: 07-11-2006 fails; 08-11-2006 and 09-11-2006 pass this bound.
- Upper bound: through 08-11-2027 is within both readings; 09-11-2027 through 31-12-2027 warns; from 01-01-2028 fails.
- Operation on 08-11-2026 does not invoke 1173; on 09-11-2026 it requires regime 14 or 15 for every applicable detail, regardless of the upper-bound result.

For current date 29-02-2120, the nominal twenty-year anniversary 29-02-2100 does not exist: 27-02-2100 fails, 28-02-2100 warns, and 01-03-2100 passes the lower-bound check. For 29-02-2420 the anniversary 29-02-2400 exists and equality is unambiguous. For a February 29 upper anniversary into a non-leap year, February 28 is within either reading; March 1 and the remaining disputed band warn. Integer calendar comparisons also handle years near 0001 and 9999 without overflow or constructing invalid dates.

The catalogue also contains 1133 for an issue date older than twenty years, but §3.1.3.1 does not state that dynamic lower bound. It is not added by copying another library; the static 28-10-2024 cutoff remains separate. Generation-time tolerance, including the observed 240 seconds, is outside this change.

## Source-to-test requirements

| Requirement | Synthetic repository test |
| --- | --- |
| Issue date before/equal/after, structured source evidence, factory propagation | `RegistrationDateValidationTest.issueDateUsesOnlyExplicitAeatCurrentDateAndPropagatesThroughFactory` |
| No implicit date from receipt/generation; unknown warning; unchanged record/hash/chain | `RegistrationDateValidationTest.unknownCurrentDateIsInspectableAndReceiptAndGenerationDatesCannotReplaceIt` |
| Inclusive twenty-year bound, rollover, leap days, Gregorian centuries and early years | `RegistrationDateValidationTest.operationLowerBoundIsInclusiveAcrossOrdinaryAndLeapYears` |
| Disputed leap day warns; surrounding days remain decisive | `RegistrationDateValidationTest.unresolvedLeapDayLowerBoundWarnsOnlyOnTheDisputedDay` |
| Disputed upper band, rollover, leap anniversary and year 9999 | `RegistrationDateValidationTest.operationUpperBoundRetainsTheDisputedBandWithoutInventingAnAeatError` |
| Every IVA/IGIC detail, omitted tax, both permitted regimes and non-applicable taxes | `RegistrationDateValidationTest.futureOperationChecksEveryIvaAndIgicDetailIncludingImplicitIva` |
| Preparation/batch errors and warnings with indexed paths; unchanged XML/SOAP/QR | `FiscalSubmissionPreparationTest.currentDateIssuesPropagateThroughPreparationAndBatchRevalidationWithoutChangingBytes` |
| Historical dates and original cutoff preserved | Existing cutoff and historical-reference tests in `RegistrationDateValidationTest` |
| Java constructors, getters and context separation | Executable offline Java example and JVM/Android ABI baselines |

Common tests run on advertised JVM, Android host, iOS simulator and macOS targets. Future calendar contexts are simulations, not claims about future services. Normal verification does not call AEAT.

## Remaining official clarification (not sent)

1. Does §3.1.3.7's upper limit mean the same calendar date in the next year or December 31? Are boundary dates inclusive, and what happens to an invalid leap-day anniversary?
2. Does the twenty-year lower anniversary clamp an invalid February 29 to February 28 or roll it to March 1?
3. Does error 1133 still define an additional issue-date lower bound despite its absence from current §3.1.3.1, and in which request contexts?

No enquiry has been sent. A follow-up task must preserve any official clarification in the repository with version/digest, replace only the resolved decisions, and add boundary regressions. Existing warnings must not silently become claims of remote acceptance.
