# Fiscal Identifier Validation

`FiscalIdentifierValidator` adds deterministic business checks while `TaxIdentifier.parse` and `InvoiceNumber.parse` retain their structural XML contract. Record validation applies these checks to the current invoice, prior-chain invoice, rectified/replaced invoices, software producer and recipient/third-party identifiers through `FiscalRecordIdentifierValidation.kt`. It neither checks ownership nor contacts a census or VIES service.

| Check | Archived official source | Implementation and evidence |
| --- | --- | --- |
| Invoice-number repertoire | [Validation catalogue 1.2.2](../../schemas-aeat/Validaciones_Errores_Veri-Factu.pdf), §3.1.3.1; error 1130 | Printable ASCII 32–126 except 34, 39, 60, 61 and 62. The common test exercises every printable ASCII value and rejects supplementary characters, accents and internal control whitespace. |
| VAT identifier country formats | Same catalogue, Note (1), page 18 | Exact published numeric/alphanumeric classes and alternative lengths, uppercase values, Romanian leading-zero restriction. Tests cover every listed prefix and length boundary. This table is not a claim to validate national VAT checksums. |
| GB/XI transition | Same Note (1), BREXIT; [error catalogue](../../schemas-aeat/errores.properties) 1254/1255 | FechaOperacion takes precedence over FechaExpedicionFactura. GB is allowed through 31 January 2021; XI is allowed from 1 January 2021. Inclusive and adjacent dates have common tests. |
| Spanish natural-person formats | [RD 1065/2007](../../schemas-aeat/BOE-A-2007-15984-consolidado.pdf), articles 19–20, consolidated 2 April 2025 | DNI and NIE forms, plus K/L/M with seven **alphanumeric** characters and a final letter. Tests preserve the alphanumeric K/L/M allowance in the legislation. |
| Spanish entity format | [Order EHA/451/2008](../../schemas-aeat/BOE-A-2008-3580-consolidado.pdf), articles 2–5, consolidated 15 January 2016 | Published entity prefixes, seven digits and a control character. Tests cover every prefix. |
| DNI/NIE check character | [Interior factual snapshot](../../schemas-aeat/interior-nif-nie-checksum.json), extracted from the linked official page | Remainder-23 table and NIE prefix weights X=0, Y=1, Z=2. Tests include the published 12345678Z example, leading zeros and different results for identical NIE digits. |
| IDType 07 requires a natural person | Error catalogue 1131/1290 | Entity NIFs are rejected; malformed personal values and wrong DNI/NIE check letters are rejected. K/L/M retains the explicit checksum warning. |

The Interior archive is an explicitly identified factual extraction, not original HTML: direct retrieval returned HTTP 403. Its source URL, retrieval date, complete algorithm facts and published example are retained. The BOE PDFs are original downloaded bytes. All three entries are recorded in [manifest.tsv](../../schemas-aeat/manifest.tsv).

## Explicit limits and assumptions

The archived sources establish no implemented algorithm for entity or K/L/M check characters. A structurally matching value returns `VF-ID-CHECKSUM-UNVERIFIED` with `WARNING` severity. It does not silently gain a verified-checksum status. These warnings remain inspectable in factory, batch and preparation reports. The validator also does not infer country-specific passport or other foreign-document algorithms.

For a supplied country code, error 1122 requires agreement with the VAT prefix. The catalogue uses `EL` and `XI`, while the XML country enumeration supplies `GR` and `GB`. Mapping EL→GR and XI→GB is an **inference combining those sources**, rather than a verified interpretation of error 1122's literal first-two-character wording. Those matching pairs therefore return `VF-ID-COUNTRY-PREFIX-ASSUMPTION` with `ASSUMPTION` severity. An omitted VAT country code, which the catalogue permits, needs no mapping. Interoperability evidence remains necessary for the inferred pairs.

`FiscalIdentifierValidationTest` provides common tests for the rules, immutable issue lists, stable nested field paths and diagnostics without identifier values. Existing XML/parser tests may intentionally use unchecked records for structurally valid text outside the service character repertoire; they establish serialization behaviour, not service acceptance.
