# Advanced sandbox console tool

For everyday use, start with the unified [VeriFactu CLI](../query/README.md): build once with `./gradlew :tools:query:installDist`, then run `./verifactu sandbox demo` or `./verifactu sandbox demo --response timeout`. The commands below expose the explicit bundle/XML workflow for diagnostics and separately authorized submissions.

The unified launcher also exposes `sandbox scenarios`, `prepare`, `show`, `demo --bundle`, `inspect` and `send`, with contextual help. Its explicit `send` uses the native macOS Keychain identity chooser and the same reviewed bundle/digest, persistence and response semantics described here. The standalone launcher below retains its explicit PKCS#12 transport; credential flags are not accepted by the unified Keychain command.

A standalone JVM host utility for preparing VERI*FACTU test requests, reviewing XML, exercising response scenarios and inspecting saved responses. Build and test it locally without AEAT access or an existing certificate. It is separate from the published library and is not a complete invoicing system.

Only the explicit `send` command can load an explicitly selected PKCS#12 identity and make a network request. It supports the library's **TEST** submission endpoints only. Preparation defaults to an unsendable `offline` mode. No command discovers certificates, registers software, selects production, schedules work or retries a request.

## Build and run locally

Use the repository build prerequisites from [getting started](../../docs/getting-started.md). Run from the checkout root; add Gradle's `--offline` option when dependencies are cached:

```sh
./gradlew :tools:sandbox:installDist
cli=tools/sandbox/build/install/verifactu-sandbox/bin/verifactu-sandbox
"$cli" --help
"$cli" scenarios

work_dir="$(mktemp -d)"
"$cli" prepare --scenario registration \
  --config tools/sandbox/examples/offline.properties --out "$work_dir/registration"
"$cli" show --bundle "$work_dir/registration"
"$cli" demo --bundle "$work_dir/registration"
"$cli" demo --bundle "$work_dir/registration" --response timeout
```

The installed launcher requires **Java 11+** and no Gradle runtime. Select another runtime with `JAVA_HOME`. A Windows `.bat` launcher is generated; Windows execution and ACL behavior have not been verified. `./gradlew :tools:sandbox:run --args='scenarios'` is also available.

`demo` uses synthetic responses and the testkit's fake transport: acceptance, acceptance with errors, rejection, duplicate, flow control, SOAP fault, timeout and definitely-not-sent failure. It never accesses credentials or the network, including for an `aeat-test` bundle. A completed demo exits 0 even when demonstrating rejection or delivery failure; its output is labelled `OFFLINE SIMULATION`.

## Explicit inputs and scenario limits

Copy [examples/offline.properties](examples/offline.properties) to a private local file before changing it. It contains synthetic identities for local tests, not an authorized taxpayer or producer configuration. The UTF-8 file must contain exactly these keys, with no duplicates:

| Key | Meaning |
| --- | --- |
| `issuer.nif`, `issuer.name` | Taxpayer issuing the record |
| `invoice.number`, `invoice.date` | Explicit invoice identity; date `dd-mm-yyyy` |
| `generated.at` | Explicit generation timestamp with offset, e.g. `2024-01-01T12:00:00+01:00` |
| `producer.nif`, `producer.name` | Explicit software producer identity |
| `system.name`, `system.id`, `system.version`, `system.installation` | Explicit software and installation metadata |

The registration scenario is deliberately narrow: **F2**, IVA regime `01`, subject/non-exempt, taxable base **100.00**, rate **21%**, charged tax **21.00**, invoice total **121.00**, no recipient. Its system metadata declares VERI*FACTU-only use and one taxpayer. It does not cover rectification, subsanation, exemption, reverse charge, multiple taxpayers or arbitrary invoice import. Use the library APIs for those workflows. No date, invoice number, chain position or installation identity is generated from the clock or random values.

Preparation composes the library's validation, record factory, hash/chain, XML, batch and TEST QR APIs, then checks the batch against packaged archived XSDs with external resource access disabled. Warnings remain in `validation.txt`. Passing these checks is not AEAT acceptance, proof of census status or proof of authority to submit.

## Chains, cancellation and exact duplicates

For a second registration, create another complete config with a different invoice number and an explicitly selected timestamp, then pass `--previous` with the prior bundle. The issuer, producer, system metadata, mode and certificate route must match. This is explicit local chaining; the tool does not establish that the previous record was received remotely.

For cancellation, copy the original config to `cancellation.properties`, retain the invoice identity and update `generated.at` explicitly:

```sh
"$cli" prepare --scenario cancellation --config cancellation.properties \
  --previous "$work_dir/registration" --out "$work_dir/cancellation"
"$cli" show --bundle "$work_dir/cancellation"
"$cli" demo --bundle "$work_dir/cancellation"

"$cli" prepare --scenario duplicate --previous "$work_dir/registration" \
  --out "$work_dir/duplicate"
```

The cancellation scenario directly follows the matching registration. For a real chain with intervening records, use the library and the host application's authoritative chain state. `duplicate` copies the registration's exact SOAP bytes, hash, original generation timestamp, chain inputs, mode and route. It accepts no replacement config or mode. It does not invent a new fiscal record or assert that resubmission is appropriate.

## Review and saved responses

Each preparation creates a **new** directory; its parent must exist. Existing files/directories are never overwritten. Bundles contain:

| File | Content |
| --- | --- |
| `request.xml` | Exact UTF-8 SOAP bytes to review |
| `record.xml` | Prepared fiscal record XML |
| `bundle.properties` | Versioned explicit inputs, previous-chain snapshot, mode, route and request SHA-256 |
| `validation.txt` | Local validation severity, code and field path, including warnings |
| `qr.txt` | TEST QR URL for a registration |

`show` reloads the metadata, rebuilds and validates the request, compares both XML snapshots and checks SHA-256 before displaying the endpoint, digest and full XML. Directional controls are escaped in the console preview; `request.xml` retains the original bytes. A digest is a consistency check, not a signature or authenticated approval of metadata.

Inspect a response saved outside the repository against the exact request bundle:

```sh
"$cli" inspect --bundle "$work_dir/registration" --input saved-response.xml
"$cli" inspect --bundle "$work_dir/registration" --input saved-response.xml --details
```

Default output omits invoice identifiers, CSV/remote identifiers, descriptions and exception text. `--details` opts into invoice/error diagnostics; control and directional characters are escaped and each field is bounded. Raw XML remains available in the saved file. Saved-file inspection is not proof of network delivery.

The utility uses the library's response/fault parsers and correlates invoice identity and operation against the prepared record. Only a matched clean acceptance has exit 0; a non-success HTTP response, contradictory error diagnostics, warnings, duplicates, rejections, SOAP faults, unknown states, malformed XML or correlation mismatch requires review. The parser preserves unknown protocol values; it does not assert full response-schema or fiscal validation. Flow-control seconds are displayed without sleeping or resending.

## Explicit TEST delivery, for a separately authorized session

Local development does not require this step. Before a live test, agree on the taxpayer/producer identity, synthetic invoice number, generation time, chain, certificate access and intended operation. Do not send the checked-in local fixture as your taxpayer's data. Certificate subject fields do not establish authorization for the configured taxpayer.

Prepare a separate sendable bundle using the agreed private config:

```sh
"$cli" prepare --scenario registration --config private-test-inputs.properties \
  --mode aeat-test --certificate-route standard --out "$work_dir/reviewed-test"
"$cli" show --bundle "$work_dir/reviewed-test"
```

`standard` and `seal` resolve through `AeatEndpointConfiguration` to the archived WSDL's TEST routes (`prewww1.aeat.es` and `prewww10.aeat.es`). The tool has no URL/production override. Choose the route appropriate to the explicitly selected certificate; it is not inferred automatically.

After reviewing the actual XML and TEST endpoint, set `reviewed_sha256` to the digest you reviewed. Make the password available via an explicitly chosen environment variable using your shell's private input mechanism; never pass a password value as a command argument or save it in this repository. The future send command is:

```sh
"$cli" send --bundle "$work_dir/reviewed-test" --out "$work_dir/attempt-1" \
  --pkcs12 /explicit/private/selected.p12 --alias selected-private-key-alias \
  --password-env VF_SANDBOX_PASSWORD --confirm-sha256 "$reviewed_sha256"
```

The selected file must be PKCS#12 with a private key and certificate chain using the supplied password for both store and key. The tool isolates the selected alias into a single-identity TLS context, uses the JDK's default server trust, and clears its temporary password character array after loading. The environment value remains owned by the process/shell; remove it afterwards according to your shell's conventions. There is no certificate vault or password persistence. The transport has a 30-second complete-response deadline and the library's default 1 MiB response-body limit.

Before invoking transport once, an attempt directory receives the exact `request.xml` snapshot and `started.properties`. Returned XML, including SOAP faults, is saved in `response.xml`; `summary.txt` and `outcome.properties` record the interpreted result, delivery state, transport result kind, HTTP status when available, exit code and request digest. Credentials and certificate paths/aliases are not included. A non-XML or oversized body is not retained.

If execution stops with only `started.properties`, the delivery result is **unknown**. A crash or failure saving results must never be read as proof that nothing was sent. Preserve the attempt and reconcile it before any new attempt. Even a parsed duplicate requires inspection of the original record state. The utility never advances durable application state or retries automatically. A failed credential load can leave an empty attempt directory; use a new output directory after fixing it.

On POSIX filesystems, new artifact directories are owner-only (`0700`) and files owner-only (`0600`). Other filesystems inherit local ACLs; configure those separately. Inputs, bundles and responses contain fiscal/personal data. Keep them outside the checkout and avoid shared terminal capture. SHA-256 values can also correlate requests.

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Local preparation/show/demo completed, or a clean matched accepted response was parsed |
| `2` | Local input, file, credential or access error |
| `3` | Response requires review; no clean matched acceptance established |
| `4` | Delivery failure/uncertainty, or inability to persist results after transport started |

These codes do not replace the delivery state or parsed record statuses. In particular, `demo` completion and saved-file inspection cannot establish real acceptance.

## Verification and remaining evidence

```sh
./gradlew :tools:sandbox:ktlintCheck :tools:sandbox:detekt :tools:sandbox:check
./gradlew :tools:sandbox:koverXmlReportJvm :tools:sandbox:installDist
```

Normal tests cover deterministic schema-valid bundles, chain/cancellation/duplicate behavior, safe XML parsing, identity correlation, uncertain delivery, no overwrites, password clearing and default redaction. A real localhost HTTPS test generates disposable server/client identities in its temporary directory, verifies the explicitly selected client among multiple aliases, sends exact SOAP bytes through the JVM transport and persists a matched response. It reads no existing certificate and contacts no AEAT endpoint. CI also runs the installed tool's offline preparation/demo on Java 11.

Live AEAT acceptance, taxpayer/certificate authorization, duplicate history, actual flow-control behavior and Android/Apple networking remain separate verification work. The [release plan](../../docs/release-plan.md) describes those stages. The [query tool](../query/README.md) separately prepares issued/received consultations and inspects saved responses; this submission harness does not retrieve invoice history.
