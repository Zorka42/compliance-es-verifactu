# Public contract refresh, 2026-09-11

The fifteen files already listed in `schemas-aeat/manifest.tsv` before fiscal-identifier source additions were fetched again from their recorded public URLs during local candidate preparation. Every response matched the archived SHA-256 digest byte for byte. The baseline therefore remains unchanged; original retrieval dates in the manifest are retained.

The checked set comprises both consolidated BOE PDFs (RD 1007/2023 and Order HAC/1177/2024), the validation catalogue v1.2.2, service specification v1.0.3, declaration example v0.5.1 and FAQ, issued/received query request and response schemas, submission request/common/response schemas, WSDL, the published error catalogue, XML Signature schema, and W3C XML Schema Part 2 Second Edition.

All source bytes used for behavior and fixtures remain in the repository. In particular, the validation PDF digest remains `426eb926fc098a36a163f66ca5f40d9e0847ca23300bbe5008979832d3513440`. No authenticated portal, registration flow, SOAP operation or taxpayer certificate was used. Fetching unchanged static documents confirms this pinned baseline; it does not establish that AEAT runtime behavior has been verified or replace source review before an eventual external release.
