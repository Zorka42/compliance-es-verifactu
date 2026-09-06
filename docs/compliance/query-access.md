# Querying Registered VERI*FACTU Records

The query tool has two distinct purposes: inspecting records for invoices issued by a taxpayer and inspecting records for invoices received by that taxpayer. Preparing requests and inspecting saved responses are local operations. Retrieving the taxpayer's actual records requires a separately authorized AEAT connection and client certificate.

## What the Service Can Return

| Query mode | Header identity | Intended records |
| --- | --- | --- |
| Issued | `ObligadoEmision` | Records submitted for invoices issued by the selected taxpayer |
| Received | `Destinatario` | Records submitted by suppliers for invoices addressed to the selected taxpayer |

AEAT documents both modes for voluntary VERI*FACTU submissions. The required year and month refer to the operation date, falling back to the issue date. Results are ordered by submission date, with at most 10,000 records per page. Continue using the returned pagination key when more data exists. The submission receipt's CSV cannot be recovered through later queries. Source: [service specification v1.0.3, 2025-07-28, sections 6.4, 6.4.3, 6.4.4 and 11](https://sede.agenciatributaria.gob.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/Veri-Factu_Descripcion_SWeb.pdf).

Consequently, this is not a complete inventory of every invoice involving an autónomo. In particular, a successful empty result does not establish that no invoice exists outside the queried VERI*FACTU records and period. The response is fiscal-record data, not an invoice PDF or an accounting ledger.

## Identity and Authorization

The public error catalogue establishes these boundaries:

- `4132`: a received-record query requires the certificate holder to be the recipient, an authorized representative (`Apoderado`), or a successor.
- `4140`: invoice consultation can be refused when the necessary consultation powers are absent.
- `4112`: the issuer-side identity check names the issuer, social collaborator, authorized representative, or successor. This does not establish unrestricted consultation rights for every submitter.
- `1261`: `IndicadorRepresentante` is only applicable when querying by `ObligadoEmision`.
- `1272`: received-record queries must omit `MostrarSistemaInformatico` or use `N`.

Source: [AEAT errors.properties, retrieved 2026-09-06](https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/errores.properties). The qualified-certificate authentication requirement is described in sections 4.1 and 4.3 of the service specification above.

For the first live check, use the taxpayer's own explicitly selected identity and certificate. Delegated consultation needs a separate verification of the specific consultation powers. The utility must not infer authorization from possession of a certificate or from successful submissions.

## Wire Contract

The [WSDL](https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/SistemaFacturacion.wsdl) exposes `ConsultaFactuSistemaFacturacion` through the VERI*FACTU port type. Its binding uses SOAP 1.1 document/literal messages with an empty SOAP action. The operation is separate from `RegFactuSistemaFacturacion`; the service under a formal information request does not expose consultation.

Namespace identifiers use `tike/cont/ws/`, even though the downloadable contract locations use `tikeV1.0/cont/ws/`. XML namespace URLs identify names; an offline parser must never retrieve them.

| Prefix used in this document | Namespace |
| --- | --- |
| `soap` | `http://schemas.xmlsoap.org/soap/envelope/` |
| `con` | `https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/ConsultaLR.xsd` |
| `resp` | `https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/RespuestaConsultaLR.xsd` |
| `sf` | `https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd` |

### Request

The [request schema](https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/ConsultaLR.xsd) declares the body root `con:ConsultaFactuSistemaFacturacion`. Its children, in order, are `con:Cabecera`, `con:FiltroConsulta`, and optional `con:DatosAdicionalesRespuesta`.

The filter requires `con:PeriodoImputacion`. Optional filters, in schema order, are invoice number, counterparty, issue date or issue-date range, software identity, external reference, and pagination key. Additional response options select issuer name and software details.

The [common schema](https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/SuministroInformacion.xsd) defines header children `sf:IDVersion` (`1.0`), issuer or recipient identity, and optional representative indicator. Identities contain name and NIF. The period contains `sf:Ejercicio` and `sf:Periodo` (`01`–`12`). A cursor contains issuer NIF, invoice number, and issue date. Counterparty identification is a name plus NIF or foreign identification. Date filters support a single date or range; fiscal dates use `dd-mm-yyyy`.

### Response

The [response schema](https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/RespuestaConsultaLR.xsd) declares `resp:RespuestaConsultaFactuSistemaFacturacion`. It contains header, period, pagination indicator, query result, zero to 10,000 record entries, and optional cursor.

`ResultadoConsulta` is `ConDatos` or `SinDatos`; `IndicadorPaginacion` is `S` or `N`. Each entry contains invoice identity, record data, optional submission details, and record state. Query states are `Correcto`, `AceptadoConErrores`, and `Anulado`. Many record-data fields are optional; the parser must allow a sparse cancelled record without manufacturing amounts or invoice type.

Qualification matters: the response period's `Ejercicio` and `Periodo` are declared in the **response** namespace, whereas the request period's children belong to the **common** namespace. The nested state element has the same local name as its outer state block. Match namespace and structural position, not local name alone.

## Offline Verification and Later Live Checks

The following is the project's proposed verification sequence, not an additional AEAT protocol requirement:

1. Validate issued and received request fixtures against vendored XSDs. Include accents, XML escaping, invalid dates, explicit month selection, and pagination keys. Preserve original source files and digests.
2. Parse saved synthetic responses covering empty results, ordinary records, accepted-with-errors records, cancellations with sparse data, optional submission details, and continuation pages. Keep official examples distinguishable from project-authored fixtures.
3. Reject malformed XML, wrong namespaces, duplicate envelope bodies, invalid state values, inconsistent pagination, and external-entity declarations. Keep schema resolution local. An offline inspection must not trigger a network request or discover certificates.
4. Exercise JVM transport separately against a loopback server with disposable test identities. Check TLS, timeout classification, response limits, and redacted diagnostics. These tests do not require the taxpayer's certificate or an AEAT connection.
5. After explicit authorization, select a client certificate and query synthetic records in the AEAT test environment. Verify both roles, authorization failures, pagination and schema assumptions. Do not treat test-environment records as the taxpayer's production history.
6. After a separate production-read authorization, query an agreed month for both issued and received records. Keep retrieved fiscal data outside the repository and compare the returned records with user-provided expectations. Submission, correction, cancellation and QR checks remain separately authorized activities.

No SOAP endpoint, authenticated portal, local certificate store, private key or taxpayer record was accessed while researching this contract.

## Source Inventory

The following original files were retrieved on 2026-09-06 for repository archival and deterministic tests. Mutable `tikeV1.0` artifacts have no independent published revision in their content; their digests identify this snapshot. The source URL for every XSD, WSDL and properties file is `https://prewww2.aeat.es/static_files/common/internet/dep/aplicaciones/es/aeat/tikeV1.0/cont/ws/` followed by its exact filename.

| Artifact | Version/date | SHA-256 of original download |
| --- | --- | --- |
| `ConsultaLR.xsd` | `tikeV1.0`, retrieved 2026-09-06 | `bf2cdb8fc4b95b291757a72b76d8fffca06a6d30d9329122ca2fd6b2d5f8f1b1` |
| `RespuestaConsultaLR.xsd` | `tikeV1.0`, retrieved 2026-09-06 | `de35063acb8d9ba0d6ae51acc6b595de9c2b12333250e95e13108ef5f2670d45` |
| `SuministroInformacion.xsd` | `tikeV1.0`, retrieved 2026-09-06 | `ee4c1655175644de44c4c25055ffeb8e5f4bb4bc3834ce8254d4222ef18c8aa1` |
| `SuministroLR.xsd` | `tikeV1.0`, retrieved 2026-09-06 | `cbdac8d427cc5ab5d77ca48974cab0f35d6bb819c4c66db361681e3710aeba36` |
| `RespuestaSuministro.xsd` | `tikeV1.0`, retrieved 2026-09-06 | `82acf80f785643caac13087aae66808ed721a13f08ca5218cf8ae81b695549ef` |
| `SistemaFacturacion.wsdl` | `tikeV1.0`, retrieved 2026-09-06 | `05919120708ff7650612fa6683c9336eaf919335d9a4db10e86759190af48602` |
| `errores.properties` | `tikeV1.0`, retrieved 2026-09-06 | `06519ceb23422bd6b0ad3bfb659e3007615050da4920781d12cff536481d5902` |
| [Service specification](https://sede.agenciatributaria.gob.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/Veri-Factu_Descripcion_SWeb.pdf) | 1.0.3, 2025-07-28 | `b3570f6a308ce98a5f52001a0dc427310ad6cf7bccd60a9ee98720a59e553c02` |
| [Validation specification](https://www.agenciatributaria.es/static_files/AEAT_Desarrolladores/EEDD/IVA/VERI-FACTU/Validaciones_Errores_Veri-Factu.pdf) | 1.2.2, 2026-04-08 | `426eb926fc098a36a163f66ca5f40d9e0847ca23300bbe5008979832d3513440` |

The original common schema differs from the repository's existing test copy (`34ef72b3f5ba2c6c5cd2d9a7c3b5b7b226b59d754e569c5c74a04d1c27762989`) only in the XML Signature import location: the original uses a W3C HTTP URL; the test copy points to the vendored `xmldsig-core-schema.xsd`. Record this adaptation explicitly instead of claiming that the test-copy digest identifies the original download.
