# First Public Release Readiness Checklist

Use this checklist for one immutable release candidate. It is evidence for the library release; it is not a declaración responsable, AEAT approval, or authorization to submit fiscal records.

## Offline Preparation

- [x] Apache-2.0 license, acknowledgements, contributing, changelog, security guidance, and release governance are present.
- [x] `main` requires Linux/JVM/Android, Apple, CodeQL, and static-analysis checks through a pull request; force-push and deletion are disabled.
- [x] The KMP/API/coverage/sample gate and all-platform local Maven publication pass without AEAT access.
- [x] A disposable PGP-key test signs and verifies a JVM publication locally; the release graph signs every KMP publication before Central upload.
- [x] The tag workflow rejects `SNAPSHOT` and non-SemVer tags, requires a `main` ancestor, and waits for release-environment approval.
- [ ] Review the exact release-candidate diff and all CI results after the final release PR is merged.

## Release Owner Inputs

- [ ] Verify ownership of the Central Portal namespace used by the release group.
- [ ] Create a dedicated PGP signing key, retain its revocation material securely, and distribute its public key through a Central-supported key server.
- [ ] Generate an expiring Central Portal user token and store the namespace, token username/password, armored signing key, and signing-key passphrase only as the five `release` environment secrets.
- [ ] Re-download, hash, and review the official AEAT/BOE/XSD/WSDL source baseline that applies to this exact release.
- [ ] Name the legal/component producer, fix the release version and commit, and complete the declaration-material review.

## Controlled Release

- [ ] Create a reviewed non-`SNAPSHOT` SemVer tag on a commit already in `main`.
- [ ] Approve the queued `release` environment deployment in GitHub.
- [ ] Confirm Central validation and publication state; retain the CI and Central deployment URLs as release evidence.
- [ ] Consume the released JVM artifact from a clean external Maven repository and record the result.
- [ ] Check the generated GitHub Release notes and attach no credentials, private keys, personal data, or raw fiscal XML.

## Explicitly Out of Scope

This checklist does not authorize AEAT test-environment traffic, production reads, submission, correction, cancellation, or use of a taxpayer certificate. Those are separate operational tasks with their own authorization and evidence rules in [the release plan](release-plan.md).
