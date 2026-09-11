# Fiscal arithmetic validation

The normative baseline is the archived AEAT `Validaciones_Errores_Veri-Factu.pdf`, version 1.2.2 dated 2026-04-08, sections 3.1.3.15.7–17 (pages 14–15), together with the archived error catalogue. Original bytes, retrieval dates and SHA-256 digests are in [`schemas-aeat/manifest.tsv`](../../schemas-aeat/manifest.tsv). The fixtures are synthetic boundary cases derived from those rules, not live AEAT acceptance evidence.

| Rule | Local behavior | AEAT code |
| --- | --- | --- |
| S1 charged tax, §15.7 | Compare against effective base × rate / 100 with inclusive ±EUR 10 tolerance; use cost base when supplied | 1142 ordinary base; 1144 cost base |
| S1 sign, §15.7 | Reject opposite nonzero signs of charged tax and effective base; zero, including signed zero, is neutral | 1143 ordinary base; 1140 cost base |
| Rectification exceptions, §15.7 | Skip the preceding arithmetic/sign checks for rectification by difference or invoice types R2/R3; mandatory fields remain independently validated | — |
| Simplified invoice F2, §15.8 | Sum all taxable bases and charged tax, excluding surcharge; reject values greater than EUR 3,010; this is a signed upper bound, not an absolute-value limit | 1150 |
| F2 exceptions, §15.8 | Skip its limit when a billing-agreement registration number is supplied or article 6.1(d) recipient exemption is S | — |
| Total tax, §16 | Warn when total tax differs from all charged tax plus surcharge by more than EUR 10 | 2006 |
| Total amount, §17 | Warn when total amount differs from all bases plus charged tax plus surcharge by more than EUR 10 | 2005 |

The total checks are excluded when a detail has regime 03, 05, 06, 08 or 09. For mixed details, an excluded detail prevents reconstructing the full declared total, so the aggregate check is skipped; no partial sum is compared to the full invoice total. This interpretation does not assert that AEAT will accept a mixed-regime record. Other regime and operation rules still apply.

Amounts are converted to signed cents. The schema allows at most twelve details, each amount with twelve integral and two fractional digits, so all aggregate sums fit in `Long`. Rates have three integral and two fractional digits. Multiplication divides the base into quotient and remainder first, retaining the exact fractional-cent remainder; it neither overflows an intermediate product nor rounds a tolerance boundary. Values such as `100.01 × 21% = 21.0021` are therefore distinguished from `21.00`. Malformed rates and invalid detail counts are handled by structural validation without throwing during arithmetic.

`FiscalArithmeticValidationTest` covers both tolerance edges, fractional-cent boundaries, negative and signed-zero values, maximum schema magnitudes, multiple details, exceptions and warning propagation through record creation. The common implementation runs on each advertised test target. Stable local codes use `VF-AMOUNT-<AEAT code>` with field paths and exact source references. Warning-only reports remain locally valid and inspectable; they do not establish AEAT acceptance.
