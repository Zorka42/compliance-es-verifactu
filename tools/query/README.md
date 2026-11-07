# VeriFactu CLI

One entry point for offline sandbox scenarios, reviewed TEST submissions and read-only AEAT TEST consultations. The CLI is a host tool, separate from the published library. Commands follow resource/action groups with contextual help, as in [gh](https://cli.github.com/manual/gh) and [gcloud](https://docs.cloud.google.com/sdk/gcloud/reference/topic/command-conventions).

Explicit, separately authorized TEST submissions are also available through the same launcher with native macOS certificate selection. Preparing, showing, simulating and inspecting a bundle remain local operations.

## Install locally

Java 11+ is required to run it; use the repository's build toolchain to build. macOS additionally needs Xcode command-line tools to compile the native certificate helper.

```sh
./gradlew :tools:query:installDist
./verifactu --help
```

The repository launcher never builds or downloads anything implicitly. For use outside the checkout, add `tools/query/build/install/verifactu-query/bin` to `PATH`, then run `verifactu` directly. Keep the installed `bin` and `lib` directories together. Offline demo works on Linux and macOS; Keychain consultations require macOS 13+. Windows consultation support is not provided.

## Run a demo

```sh
./verifactu sandbox demo
./verifactu sandbox demo --response timeout
./verifactu sandbox demo --help
```

No configuration, certificate, network or manual temporary directory is needed. The command prepares synthetic registration inputs automatically, runs all eight response scenarios (or one selected response), and removes the temporary synthetic bundle. These scenarios demonstrate parser behavior, not fiscal acceptance. For direct XML inspection and explicit submission harness commands, see the [advanced sandbox tool](../sandbox/README.md).

## Review and execute a TEST scenario

All steps have contextual `--help`. Configure the explicit fiscal inputs in a private file using the [documented input keys and scenario limits](../sandbox/README.md#explicit-inputs-and-scenario-limits). Taxpayer configuration for `invoices list` does not silently fill producer identity, invoice identity, chain or generation time. Agree on those values before preparing a sendable bundle. Checked-in synthetic inputs are for local tests only.

With `vf_inputs` pointing to the agreed private config and `vf_run` to a private existing parent directory:

```sh
./verifactu sandbox scenarios
./verifactu sandbox prepare --scenario registration --config "$vf_inputs" \
  --mode aeat-test --out "$vf_run/registration"
./verifactu sandbox show --bundle "$vf_run/registration"
./verifactu sandbox demo --bundle "$vf_run/registration" --response accepted
```

Preparation defaults to `offline` when `--mode` is omitted; offline bundles cannot be sent. The demo is always local, even with an `aeat-test` bundle. Review the actual XML, endpoint and digest displayed by `show`. After separately authorizing this TEST write, set `reviewed_sha256` to the digest you reviewed, then execute:

```sh
./verifactu sandbox send --bundle "$vf_run/registration" \
  --confirm-sha256 "$reviewed_sha256" --issuer "AC FNMT Usuarios" \
  --out "$vf_run/registration-attempt"
./verifactu sandbox inspect --bundle "$vf_run/registration" \
  --input "$vf_run/registration-attempt/response.xml"
./verifactu sandbox invoices list --month 2026-11 --direction issued \
  --issuer "AC FNMT Usuarios" --out "$vf_run/consultation"
```

Only `send` in this sequence writes a record. It rebuilds/XSD-validates the bundle and checks the exact confirmed SHA-256 before the native helper can select an identity. The helper has a separate submission confirmation gate, accepts one `RegFactuSistemaFacturacion` registration/cancellation record or an explicitly counted registration batch (1–1000 records), and refuses production/other URLs before Keychain access. The certificate chooser explicitly says `AEAT TEST WRITE`, registration/cancellation, invoice identity and digest. Normal system HTTPS trust and redirect refusal remain enabled. No private key is exported; no CLI password, PKCS#12 file or saved identity preference is required. The existing query path continues to reject registration XML.

Each send uses a new `0700` attempt directory, exact request/start evidence before transport, native receipt/body under `native/`, a shareable `network.log`, interpreted `summary.txt`, `outcome.properties` and returned `response.xml` when XML was received. Files are `0600`. A single attempt performs no retries. Cancellation is NOT_SENT; uncertainty, malformed responses or inability to persist results cannot establish acceptance. Preserve evidence and reconcile before any repeat. Inspection is local and cannot prove delivery. Flow-control advice is displayed without sleeping or automatically continuing.

The same `prepare` command supports `--scenario duplicate --previous REGISTRATION_BUNDLE` for exact bytes, and `--scenario cancellation --config CANCELLATION_CONFIG --previous REGISTRATION_BUNDLE` for the matching invoice with explicitly updated generation time. Review each new bundle/digest and use a new attempt directory for every authorized send. The default F2 scenario has no recipient. The [ZA-111 matrix](../../docs/aeat-test-matrix.md) adds explicit F1 self-recipient inputs, correction/subsanation and a pagination dataset. Warning/rejection/flow-control demo responses do not guarantee those outcomes from AEAT.

For a separate negative TEST case, `prepare --scenario cancellation-missing` takes a distinct absent invoice and `--previous` with the reconciled last accepted chain record. It snapshots generation time at preparation and prints a send command with exact digest/count. See the [full command workflow and expected 3002 semantics](../sandbox/README.md#negative-test-cancellation-of-an-absent-invoice). Existing matrix evidence remains unchanged.

## Prepare the full ZA-111 matrix

Use `verifactu sandbox matrix --help` and the [matrix operator guide](../../docs/aeat-test-matrix.md). `init`, `prepare`, `show`, `rehearse` and `report` are local only; `send` and `query` are separate explicit TEST operations. Private taxpayer/producer files never enter the repository.

## List TEST invoices

Configure the taxpayer once in an interactive terminal:

```sh
./verifactu config init
```

Enter the taxpayer NIF and legal name at the prompts. Only these two fields are stored in `~/.config/verifactu/taxpayer.properties`, with owner-only permissions. Existing configuration is never overwritten. No Keychain or network access occurs during configuration. For automation, `config init --party-file FILE` reads UTF-8 properties containing exactly `nif` and `name`; keep the source file outside Git.

Then consult TEST:

```sh
./verifactu sandbox invoices list --month 2026-11
```

A native macOS certificate chooser appears. Select the identity you intend to use. macOS controls private-key authorization: depending on existing key ACLs and cached authorization, it may request a Keychain password, Touch ID or permission; a prompt on every request cannot be guaranteed. The CLI never asks for that password, exports a private key, imports an identity, changes ACLs or saves a certificate preference. Cancellation sends no consultation. An unavailable identity or failed authorization is an error, never an empty result.

Defaults: both issued and received directions, current month in `Europe/Madrid`, table output. The month is the AEAT period of imputación (operation date, falling back to invoice issue date), not an arbitrary invoice-date filter. Listing covers only records submitted through VERI*FACTU **in TEST**, not every invoice involving the taxpayer. The chosen identity must be authorized for that taxpayer and query role. This tool does not establish those rights.

```sh
./verifactu sandbox invoices list
./verifactu sandbox invoices list --direction received --month 2026-11
./verifactu sandbox invoices list --month 2026-11 --issuer "AC FNMT Usuarios"
./verifactu sandbox invoices list --month=2026-11 --format=tsv
./verifactu sandbox invoices list --help
```

`--issuer NAME` narrows the native chooser to identities whose certificate issuer common name exactly matches `NAME` (case-sensitive). It reads the public certificate's issuer field, not its subject/owner name or a substring. The filter applies to both directions and every page in that run; there is no fallback to another issuer when none matches. It is a selection convenience, not an additional trust anchor or proof of taxpayer authorization. Other certificate providers remain available when the option is omitted. Renewed/expired copies with the same issuer may still appear; TLS trust and key authorization remain unchanged. Help/config/demo still access neither Keychain nor network.

The current chooser is opened before the TLS exchange and initially enumerates all accessible identities. Browsers can also narrow candidates using the server's client-certificate request; Foundation exposes its accepted issuer names through [URLProtectionSpace.distinguishedNames](https://developer.apple.com/documentation/foundation/urlprotectionspace/distinguishednames). The explicit issuer option does not implement automatic server-driven filtering.

Rows go to stdout; status, incompleteness and the evidence directory go to stderr. `--format tsv` provides a stable six-column header: `direction`, `issuer`, `invoice`, `issue_date`, `state`, `total`. Fiscal text is preserved with backslash/control-character escaping; totals remain exact strings, and absent amounts remain empty. Partial rows can be printed after failure, but the command returns a nonzero exit status.

Evidence is retained in an automatically created private temporary directory. Use `--out NEW_DIRECTORY` to choose a location, or `--party-file FILE` to override the configured taxpayer for one command. New directories are `0700`, files `0600`; no existing file is overwritten. XML and invoice rows contain personal/fiscal data: keep them outside Git and shared terminal capture. Temporary evidence is not durable application storage; preserve it elsewhere if needed.

The explicit listing command uses Keychain and network. It sends SOAP `ConsultaFactuSistemaFacturacion` over HTTPS POST to the fixed standard TEST endpoint `prewww1.aeat.es`. The operation is read-only; it is not HTTP GET. There is no production, endpoint override, registration, correction or cancellation option in this command. The native guard rejects other SOAP operations and non-TEST routes before identity access. Normal server trust remains enabled; redirects are refused. The same explicitly selected public-certificate fingerprint is required across pages and directions within a run; no selection persists between runs.

Each page records exact `request.xml` and `started.properties` before transport; returned XML, native receipt and delivery metadata are retained. The tool validates responses against archived AEAT XSDs, correlates NIF/role/period, follows `ClavePaginacion`, and rejects repeated cursors. Limits are 100 pages total, 100,000 rows total, 64 MiB per response and a native 30-second resource timeout. Limits or a failed second direction produce `INCOMPLETE`, not a successful truncated list. No application retry occurs. Root `invoices.tsv`, `summary.txt` and `outcome.properties` distinguish complete from partial consultations. A started page without complete evidence must be investigated; do not infer that the request was never sent.

### Share network evidence

`network.log` contains the intended URL/domain, read-only SOAP operation, HTTP method, redirect/trust policies and each page's response URL, HTTP status and delivery classification. The response URL comes from the native `HTTPURLResponse.url`, independently of the intended URL; the parent rejects a missing response URL for an HTTP response or any different URL. An unavailable response URL does not prove that no request was sent. Each page also retains its own report if later persistence fails. Reports omit XML, invoice rows, taxpayer identity and certificate fingerprints.

```sh
umask 077
vf_run="$(mktemp -d "${TMPDIR:-/tmp}/verifactu-network.XXXXXX")"
if ./verifactu sandbox invoices list --month 2026-11 --issuer "AC FNMT Usuarios" \
  --out "$vf_run/consultation" >"$vf_run/invoices.txt" 2>"$vf_run/cli.log"; then
  vf_status=0
else
  vf_status=$?
fi
printf 'CLI exit code: %s\n' "$vf_status"
cat "$vf_run/consultation/network.log" "$vf_run/consultation/outcome.properties"
printf 'Private evidence: %s\n' "$vf_run"
```

Share only the printed network report and outcome for routing review. Other evidence files can contain personal/fiscal data. `tls.server.trust.policy=SYSTEM_DEFAULT` describes the configured policy, not a separate certificate audit. The report supports verification of the public TEST endpoint under normal HTTPS trust; it cannot independently establish AEAT's internal database isolation. The official [WSDL TEST ports](../../schemas-aeat/SistemaFacturacion.wsdl) provide the endpoint classification.

| Exit | Meaning |
| --- | --- |
| 0 | All requested consultations completed, or the local/demo command completed |
| 2 | Local input/configuration/access error |
| 3 | Response, correlation or pagination requires review |
| 4 | Transport failure/uncertainty or incomplete artifact persistence/display |

## Advanced offline query commands

The installed `verifactu-query` launcher retains offline `prepare` and `inspect` commands, and a low-level `fetch --keychain` interface. Their help is available with `--help`. Preparation and saved-response inspection never access certificates or network.

```sh
tools/query/build/install/verifactu-query/bin/verifactu-query inspect \
  --input tools/query/src/jvmTest/resources/query-fixtures/received-page.xml
```

Default inspection omits invoice identifiers; `--details` prints them explicitly. Input must be UTF-8 and at most 64 MiB, with XML depth at most 64. DTDs, external resolution, wrong namespaces, invalid schemas and inconsistent pagination are rejected. Errors omit XML/parser diagnostics. The trusted compiled XSD permits the official 10,000-record query page. Selected JAXP providers may conservatively count supplementary characters as two UTF-16 units near XSD length boundaries; the tool neither truncates text nor changes global JVM settings.

## Local verification and remaining evidence

```sh
./gradlew :tools:query:check :tools:query:installDist :tools:query:koverXmlReportJvm
./gradlew :tools:query:testKeychainHelper
```

JVM tests cover command discovery, flags/defaults, private taxpayer configuration, output streams, schema-backed query/response fixtures, correlation, pagination, safe XML, cancellation/failure evidence and the native-process receipt contract. Native Swift tests use real loopback mTLS with newly generated identities imported **to memory only** on macOS 15+, exact SOAP bytes, default trust rejection and bounded responses. They never enumerate real Keychain identities, open the actual identity picker or contact AEAT. The installed offline demo is also checked on Java 11.

The owner reported a successful native selection and complete issued/received TEST consultation for 2026-11, returning zero records for each direction. This is external smoke-test evidence, not a verified nonempty/paginated result or production-history evidence. The new issuer filter still needs the owner's native UI verification. See [query access and sources](../../docs/compliance/query-access.md) and [release stages](../../docs/release-plan.md).
