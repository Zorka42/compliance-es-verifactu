# Security Policy

## Supported Versions

No production version has been released yet.

Security support starts when the first public release is published.

## Reporting a Vulnerability

Do not open public issues for vulnerabilities that expose credentials, certificates, private keys, personal data, fiscal data, or exploitable transport behavior.

Until a private reporting channel is published, contact the maintainers privately through the repository owner.

## Security Principles

- No secrets, certificates, private keys, or production credentials may be committed.
- Raw XML may contain personal and fiscal data and must not be logged by default.
- Credential material must not appear in `toString`, exceptions, normal logs, test fixtures, or CI output.
- The library must not collect telemetry.
- Network submission must go only to the explicitly configured AEAT endpoint.
- Dependency and license changes must be reviewed before release.
