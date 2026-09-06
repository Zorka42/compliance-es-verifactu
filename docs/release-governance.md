# Release Governance

`main` is protected in GitHub. Changes arrive through pull requests, require the current Linux/JVM/Android, Apple, CodeQL, and static-analysis checks, and cannot force-push or delete the branch. Discussions must be resolved before merge. The rule applies to administrators too.

The repository currently has one maintainer. Pull requests therefore require the author to complete the review checklist in `CONTRIBUTING.md`, but do not require an unavailable independent approval. When a second trusted maintainer joins, change the rule to require one approving review and add code ownership for compliance-sensitive paths.

## Compliance Review

For any change affecting fiscal behavior, reviewers must confirm:

- the cited BOE, AEAT technical document, XSD/WSDL, or catalogue version;
- source-to-code-to-test traceability in `docs/compliance/README.md`;
- deterministic tests for every supported affected target;
- no credentials, private keys, personal data, or raw fiscal XML were added.

Uncertain legal, schema, or protocol behavior is an explicit blocker, not an invitation to infer a rule.

## Release Flow

Only a non-`SNAPSHOT` SemVer tag (`vX.Y.Z` or a SemVer pre-release) pointing to a commit already merged into `main` starts `.github/workflows/release.yml`. It validates the tag, then verifies Linux/JVM/Android and Apple artifacts on their respective runners. Only after both gates pass does the `release` environment require explicit owner approval for the Central/signing step, which publishes signed artifacts through the Central Portal compatibility endpoint, closes and releases the staging repository, and then creates the GitHub Release.

The workflow is intentionally unusable until the release owner has verified the `io.github.zorka42` namespace, generated a scoped Central Portal user token, created and distributed a dedicated PGP signing key, and stored these GitHub Actions secrets:

- `CENTRAL_NAMESPACE`
- `CENTRAL_PORTAL_USERNAME`
- `CENTRAL_PORTAL_PASSWORD`
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

No normal build reads these values, and no release tag should be created merely to test credentials. Use the local unsigned preview and a separate Central Portal staging approval first. Maven artifact signing is unrelated to taxpayer certificates or AEAT access. The release owner should consult the [Central Portal token](https://central.sonatype.org/publish/generate-portal-token/) and [Gradle compatibility endpoint](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/) guidance again before the first external staging run.

## Emergency Changes

Do not bypass the protected-branch checks for a convenience fix. For a genuine security or availability incident, document the reason in the pull request or release notes, preserve the usual compliance-source review when relevant, and restore the normal process immediately afterwards.
