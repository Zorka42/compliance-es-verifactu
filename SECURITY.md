# Security Policy

## Supported Versions

No production version has been released yet.

Security support starts when the first public release is published.

## Reporting a Vulnerability

Do not open public issues for vulnerabilities that expose credentials, certificates, private keys, personal data, fiscal data, or exploitable transport behavior.

Until a private reporting channel is published, contact the maintainers privately through the repository owner.

Include the affected version or commit, platform/JDK, a concise impact description, and a minimal synthetic example. Do not attach real invoices, full production XML, certificate files, keys, passwords, or tokens. No response-time commitment is made before the first supported release.

## Pre-release limitations

The current response parser is a minimal field extractor, not a validating XML parser. Transport delivery classification and redaction of remote/exception diagnostics need further work. See [implementation status](docs/implementation-status.md); these are reasons the library is not production-ready.

Offline fixtures and test adapters must never be used as evidence of live AEAT acceptance. Normal test runs do not load personal certificates, contact AEAT, or require publishing/signing credentials.

## Security Principles

- No secrets, certificates, private keys, or production credentials may be committed.
- Raw XML may contain personal and fiscal data and must not be logged by default.
- Credential material must not appear in `toString`, exceptions, normal logs, test fixtures, or CI output.
- The library must not collect telemetry.
- Network submission must go only to the explicitly configured AEAT endpoint.
- Dependency and license changes must be reviewed before release.
