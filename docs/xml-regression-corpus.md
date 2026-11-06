# Shared XML regression corpus — ZA-121

`verifactu-aeat/src/commonTest/kotlin/dev/verifactu/aeat/AeatXmlRegressionCorpus.kt` retains named, synthetic inputs in source so every supported test target runs the same data without resource-loading adapters. `AeatXmlRegressionTest.kt` checks the results through the public submission/fault parser. These inputs are intentionally not all XSD-valid; they test defensive parsing, not AEAT fiscal acceptance.

| Group | Evidence |
| --- | --- |
| Malformed/unsafe | Truncation, second root, text outside the document, mismatched tags, duplicate attributes, undeclared prefix/entity, unfinished comment, invalid characters/references/surrogates, DTD/internal/parameter entities and unsupported encoding |
| Deterministic mutations | Every truncated prefix of a synthetic submission response and SOAP fault is rejected, including truncation within names, namespace bindings and closing tags |
| Ambiguity | Namespace reset/rebinding, duplicate identity leaves, nested scalars, mixed structural text, invalid structural whitespace, duplicate SOAP bodies/payloads, header-only response and mismatched SOAP version |
| Forward compatibility | Unknown extension containers cannot replace direct known fields; unknown aggregate/record/operation values stay `UNKNOWN_STATE`, never acceptance |
| Unicode | Literal supplementary characters, numeric references, combining marks, CDATA, CR/CRLF normalization and character-reference CR preservation |
| Resource boundaries | Exactly 8 Mi UTF-16 code units versus one over; depth 64 versus 65; 100,000 elements versus 100,001; 1,000 response lines versus 1,001 |
| Diagnostics | Rejection produces the stable typed invalid result and no document text; decoded values remain available through explicit parsed fields |

The resource thresholds are defensive library limits, not new AEAT validation rules. The text limit is measured on the supplied Kotlin `String` (UTF-16 code units), not Unicode code points or network bytes; the transport's separate byte cap remains independently configurable.

The new corpus exposed use of Kotlin's broader Unicode-whitespace predicate for element-only XML content. Parsing now permits only XML `S` characters (space, tab, CR, LF) between known structural elements. Nonbreaking and em spaces remain valid inside text leaves and are not stripped. The source is the original archived [XML 1.0 Fifth Edition](../schemas-aeat/w3c-xml-20081126.html), §2.3 production [3] and §2.10; §2.2 and §2.11 underpin character and line-ending cases. Source URL/digest is in the contract manifest.

JVM and Android host execute JAXP SAX; iOS simulator and macOS execute Foundation `NSXMLParser`. The common corpus exercises both implementations. Android host evidence is not Android-device parser/TLS evidence. iOS simulator evidence is not device networking evidence. No live endpoint, external entity retrieval or existing certificate access belongs to these tests. ABI/Java compatibility checks and their limits are documented in [the API policy](api-compatibility.md).
