# Offline Invoice Query Tool

Prepare an issued or received VERI*FACTU consultation, or inspect a saved response. The command has no network code, certificate lookup, credentials or submission operation. Actual taxpayer records have not been queried. See [scope and authorization](../../docs/compliance/query-access.md): results cover records sent through VERI*FACTU, not every invoice involving a taxpayer.

Run from the repository root with the normal build toolchain. Add `--offline` if dependencies are cached. Place personal identity and response files outside the checkout; the paths below are examples. The UTF-8 party file contains:

```properties
nif=89890001K
name=Synthetic taxpayer
```

Prepare either direction for a year/month of imputación (operation date, falling back to invoice issue date):

```bash
./gradlew :tools:query:run --args='prepare --direction issued --party-file /private/tmp/party.properties --year 2026 --month 09 --out /private/tmp/issued-request.xml'
./gradlew :tools:query:run --args='prepare --direction received --party-file /private/tmp/party.properties --year 2026 --month 09 --out /private/tmp/received-request.xml'
```

The output is a locally XSD-validated SOAP 1.1 request. It is not sent. Existing output files are never overwritten. Passing identity through a file keeps its contents out of command arguments. The tool uses the caller-selected file permissions; keep these local files private.

Inspect one of the checked-in synthetic response fixtures now:

```bash
./gradlew :tools:query:run --args='inspect --input tools/query/src/jvmTest/resources/query-fixtures/received-page.xml'
```

Default output contains direction, period, record count, state counts and whether another page exists. Add `--details` to explicitly print invoice identifiers and totals. Tabs, line breaks, terminal control characters and directional overrides in remote text are escaped. Totals remain exact strings; a sparse cancelled record has no invented amount.

For an actual saved response, pass its local path to `inspect`. If it contains a continuation key, reuse that response to prepare the next request with exactly the same direction, taxpayer and period:

```bash
./gradlew :tools:query:run --args='prepare --direction received --party-file /private/tmp/party.properties --year 2026 --month 09 --page-from /private/tmp/received-response.xml --out /private/tmp/received-next-request.xml'
```

The tool uses AEAT's returned `ClavePaginacion`, validates it, and rejects cross-query reuse or attempts to continue after the final page. It does not aggregate pages, infer invoices from empty results, or fetch missing pages. Optional counterparty/date/software filters and live transport are future additions.

Input is UTF-8 with a 64 MiB file limit and XML depth limit of 64. DTDs, external resolution, wrong namespaces, invalid schemas and inconsistent pagination are rejected. Errors omit XML and parser diagnostic text. The trusted compiled XSD has a larger occurrence limit to support the official 10,000-record query page.

`./gradlew :tools:query:jvmTest` validates synthetic fixtures, Unicode/escaping, both query directions, stored states, sparse cancellation, continuation, invalid/unsafe XML, command behavior, output non-overwrite and archived source digests. No live environment or local certificate is involved.
