# Draft Component Release and Declaration Worksheet

**Status: UNISSUED — ALL PLACEHOLDERS REQUIRE REVIEW.**

This is an English preparation worksheet for VeriFactu KMP and its integrating producer. It contains no signature or assertion of completed conformity. It is not the final Spanish declaración responsable. Follow the [preparation guide](declaration-guide.md) before producing or distributing a declaration.

## 1. Component Identification

| Field | Fill for the exact release |
| --- | --- |
| Responsible component producer | `<legal person/entity; unresolved until identified>` |
| Component product | `<name and scope of the released component>` |
| Release version | `<fixed version; identify snapshot bytes separately if evaluating a preview>` |
| Source repository and commit | `<URL and full commit identifier>` |
| Source archive and digest | `<file/link and SHA-256>` |
| Downstream modifications | `<patch identifiers, producer and review status; or verified none>` |
| Supported targets and runtime | `<only variants verified for this release>` |
| Host SIF producer/product/version | `<integrator-supplied identity; distinct from library metadata>` |
| Review status | `<draft / evidence incomplete / ready for producer review>` |

Do not substitute the repository owner, Maven group ID or contributor names for the legal producer without review. Do not substitute the library version for the host SIF version.

## 2. Delivered Modules and Dependencies

Include one row per delivered module/variant. Remove unused modules instead of implying that they were reviewed.

| Module | Exact Maven coordinates/variant | Binary digest | Scope and evidence |
| --- | --- | --- | --- |
| `verifactu-core` | `<group:artifact:version>` | `<SHA-256>` | `<models, validation, record/hash/chain behavior actually reviewed>` |
| `verifactu-xml` | `<group:artifact:version or excluded>` | `<SHA-256>` | `<serialization behavior actually reviewed>` |
| `verifactu-qr` | `<group:artifact:version or excluded>` | `<SHA-256>` | `<payload behavior; host rendering evidence separate>` |
| `verifactu-aeat` | `<group:artifact:version or excluded>` | `<SHA-256>` | `<operations and platform adapters actually reviewed>` |
| `verifactu-testkit` | `<group:artifact:version or excluded>` | `<SHA-256>` | `<test-only or other explicitly justified distribution scope>` |

| Dependency record | Evidence |
| --- | --- |
| Resolved runtime dependency inventory | `<report/SBOM location, versions and digests>` |
| Licenses and notices | `<original license/NOTICE files and distribution review>` |
| Build/test tooling | `<separate versioned inventory>` |
| Copied/adapted third-party material | `<provenance and obligations; or evidence supporting none>` |

## 3. Evidence and Limitations

| Behavior | Official source/version/section | Implementation | Verification/report | Result and remaining gap |
| --- | --- | --- | --- | --- |
| `<specific behavior>` | `<exact reference and digest>` | `<path/API>` | `<command, platform, date, report>` | `<not run / passed / failed / limited scope>` |

Record the following independently:

- `<deterministic generation, hash, XML and QR checks>`
- `<host persistence, concurrency and crash recovery checks>`
- `<response reconciliation, retries and correction workflow checks>`
- `<loopback transport and platform credential-boundary checks>`
- `<AEAT test-environment verification, or explicitly not performed>`
- `<production verification, or explicitly not performed>`
- `<known unsupported behavior, unresolved source questions and operational prerequisites>`

## 4. Final Declaration Field Mapping

These prompts map [Orden HAC/1177/2024, article 15.1](https://www.boe.es/buscar/act.php?id=BOE-A-2024-22138#a1-7). They are not the prescribed final wording. The producer must use the official Spanish labels and order in the issued document.

| Article field | Information to prepare |
| --- | --- |
| a | `<product name>` |
| b | `<producer-assigned system identifier>` |
| c | `<complete product version>` |
| d | `<hardware/software composition and functions>` |
| e | `<verified exclusive-VERI*FACTU characteristic>` |
| f | `<verified multiple-taxpayer characteristic>` |
| g | `<applicable non-VERI*FACTU record-signature information>` |
| h | `<producer's legal name>` |
| i | `<producer identification and, where applicable, type/country>` |
| j | `<producer postal contact address>` |
| k | `<reserved for the producer's reviewed conformity statement; NOT ASSERTED>` |
| l | `<actual subscription date and locality/country; NOT SET>` |

For this library's VERI*FACTU scope, do not invent support for non-VERI*FACTU record signing. A producer must review the appropriate field-g wording for the actual delivered product. Do not infer the host's multi-taxpayer capability from a stateless library API.

## 5. Producer Review and Distribution Record

| Review item | Pending record |
| --- | --- |
| Component declaration applicability and responsible producer | `<reviewer, decision and supporting sources>` |
| Complete host declaration and other component declarations | `<document/version identifiers and availability>` |
| Release gaps resolved | `<evidence; retain unresolved items visibly>` |
| Final wording and required-field review | `<producer/legal review reference>` |
| Current-version access in the host | `<verified location and customer delivery method>` |
| Historical declaration retention | `<archive owner and versioned location>` |
| Final declaration, if subsequently issued | `<separate document identifier; otherwise UNISSUED>` |

Filling this worksheet does not issue a declaration or authorize use of any certificate, connection to AEAT, publication, or signature.
