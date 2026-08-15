# VeriFactu KMP — Open Source Repository Requirements

Status: Draft
Canonical language: English
License: Apache-2.0

## 1. Purpose

This document defines the minimum requirements for maintaining VeriFactu KMP as a professional open-source library.

It covers repository structure, documentation, code quality, testing, CI, releases, and contribution basics.

Product behavior and VERI*FACTU compliance requirements belong in `PRODUCT_SPEC.md`.

The goal is deliberately simple:

> Keep the repository easy to understand, easy to build, well tested, well documented, and safe to release.

---

## 2. Principles

The repository MUST follow these principles:

1. **English-first.**
2. **Simple repository structure.**
3. **Reproducible local build.**
4. **Automated checks on every pull request.**
5. **Documentation is part of the product.**
6. **`main` should stay releasable.**
7. **Official releases are built by CI.**
8. **AEAT/BOE specifications, not other libraries, are the compliance source of truth.**

Avoid adding process, files, workflows, or governance unless they solve a real project need.

---

## 3. Language

English MUST be used for:

- `README.md`;
- documentation;
- public Kotlin API;
- KDoc;
- code comments;
- commit messages;
- pull requests;
- release notes.

Official Spanish VERI*FACTU terms MAY remain in code or documentation where using the original term improves precision, especially at protocol/XML boundaries.

Developer-facing API names SHOULD normally be English.

---

## 4. Repository structure

The repository SHOULD stay compact.

A reasonable baseline is:

```text
verifactu-kmp/
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── release.yml
├── docs/
├── schemas-aeat/
├── src/ or modules/
├── README.md
├── PRODUCT_SPEC.md
├── OPEN_SOURCE_REQUIREMENTS.md
├── ACKNOWLEDGEMENTS.md
├── CONTRIBUTING.md
├── CHANGELOG.md
├── LICENSE
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

Additional directories or workflows SHOULD be added only when the project actually needs them.

The exact Gradle module structure is defined by the product architecture, not by this document.

---

## 5. Required repository files

The repository MUST contain:

```text
README.md
LICENSE
ACKNOWLEDGEMENTS.md
CONTRIBUTING.md
CHANGELOG.md
PRODUCT_SPEC.md
OPEN_SOURCE_REQUIREMENTS.md
```

`LICENSE` MUST contain the Apache-2.0 license.

If code from another open-source project is copied, adapted, translated, or ported, all attribution and notice requirements of that project's license MUST be preserved.

`ACKNOWLEDGEMENTS.md` does not replace mandatory license notices.

---

## 6. README

`README.md` MUST let a developer quickly understand what the project is and how to use it.

Recommended structure:

```text
Project name
Badges
One-sentence description
Status
Installation
Quick start
Main capabilities
What the library does not do
Documentation
Contributing
License
```

The README SHOULD stay short. Detailed explanations belong in `docs/`.

The basic quickstart SHOULD use the real public API and be kept in sync with the implementation.

---

## 7. Badges

The README SHOULD include a small set of useful badges:

- Maven Central version;
- CI status;
- code coverage;
- Apache-2.0 license.

A documentation badge MAY be added if documentation is published separately.

Badges MUST reflect automated state.

Avoid decorative badge collections.

---

## 8. Documentation

The repository MUST contain developer documentation under `docs/`.

Documentation SHOULD cover the parts of the public library that cannot be understood from the API alone, including:

- getting started;
- core concepts;
- main integration flow;
- important protocol behavior;
- error handling;
- responsibilities of the integrating application;
- compliance sources.

Public API documentation SHOULD be generated with Dokka.

Documentation MUST be updated when a public API or important behavior changes.

Documentation examples SHOULD be tested or compiled where practical.

A separate documentation website is optional.

---

## 9. Code quality

The project MUST use automated Kotlin quality tooling.

Baseline:

- `ktlint` for formatting/style;
- `detekt` for static analysis.

CI MUST fail on configured formatting or static-analysis errors.

Configuration MUST live in the repository.

The project SHOULD prefer conventional Kotlin tooling over custom scripts.

---

## 10. Tests

Behavior changes MUST normally include tests.

The test suite SHOULD include, where applicable:

- unit tests;
- validation tests;
- serialization/parsing tests;
- negative/error-path tests;
- golden/reference tests for deterministic protocol output.

Golden tests are especially important for compliance-sensitive output such as hashes and serialized XML.

Tests that require real AEAT credentials, certificates, or external availability SHOULD NOT run as normal pull-request tests.

---

## 11. Coverage

Coverage MUST be measured automatically.

Kover SHOULD be used unless a better standard Kotlin solution is adopted later.

Coverage SHOULD be published to a service such as Codecov so the repository can expose:

- a README coverage badge;
- coverage information on pull requests.

Initial target:

```text
common/JVM line coverage >= 90%
```

Compliance-critical code SHOULD have particularly strong test coverage, but the project does not need complex per-package coverage governance at this stage.

Coverage is not proof of compliance. Specification-based and golden tests remain more important than chasing 100%.

---

## 12. CI

Every pull request MUST run automated checks.

The main CI workflow SHOULD cover:

```text
build
format/lint
static analysis
tests
coverage
documentation build
```

CI SHOULD compile all officially supported KMP targets where practical.

Do not add operating-system matrices unless they catch meaningful platform-specific problems.

Local development SHOULD use the Gradle Wrapper.

A contributor SHOULD be able to run most checks with:

```bash
./gradlew check
```

---

## 13. Pull requests

`main` SHOULD be protected.

Normal development SHOULD happen through pull requests.

A pull request MUST be mergeable only when required CI checks pass.

At least one review SHOULD be required once the project has more than one active maintainer.

Changes affecting VERI*FACTU behavior SHOULD link or name the relevant AEAT/BOE source in the pull request.

No elaborate PR governance is required beyond this unless the contributor base grows.

---

## 14. CONTRIBUTING.md

`CONTRIBUTING.md` MUST explain:

- required JDK/toolchain;
- how to build;
- how to run tests;
- how to run lint/static analysis;
- how to generate documentation;
- expected pull-request workflow;
- how compliance-sensitive changes should reference official sources.

The document SHOULD be short enough that a new contributor will actually read it.

Conventional Commits SHOULD be used.

Examples:

```text
feat(core): add cancellation records
fix(hash): normalize decimal representation
docs: explain AEAT error handling
test(xml): add golden registration fixture
```

---

## 15. Releases

The project MUST use Semantic Versioning.

Before `1.0.0`, the public API may evolve.

Official releases MUST be created from Git tags by CI.

Typical release flow:

```text
tag vX.Y.Z
→ build and test
→ create artifacts
→ sign artifacts
→ publish to Maven Central
→ create GitHub Release
```

Publishing release artifacts manually from a maintainer workstation SHOULD be avoided.

The release pipeline MUST satisfy Maven Central requirements.

---

## 16. CHANGELOG

`CHANGELOG.md` MUST summarize meaningful changes between releases.

It SHOULD highlight:

- new features;
- important fixes;
- breaking changes;
- VERI*FACTU/compliance-related behavior changes.

It does not need to duplicate every commit.

---

## 17. Dependencies and basic security

Dependencies SHOULD be kept current.

Dependabot SHOULD be enabled for Gradle and GitHub Actions.

Secrets, certificates, and production credentials MUST NEVER be committed.

Third-party GitHub Actions SHOULD use stable, trustworthy upstream actions and SHOULD be pinned to immutable revisions where practical.

More advanced security tooling can be added later if the project or contributor base justifies it.

---

## 18. External open-source references

Existing VERI*FACTU libraries MAY be studied as engineering references.

Initial reference projects are documented in `ACKNOWLEDGEMENTS.md`.

They are not normative sources.

Official AEAT and BOE specifications remain the source of truth for compliance decisions.

If implementation code is copied or adapted from another project, its license obligations MUST be followed explicitly.

---

## 19. Minimum bar for the first public release

Before the first meaningful public release, the repository SHOULD have:

- Apache-2.0 license;
- English README;
- installation instructions;
- working quickstart;
- `ACKNOWLEDGEMENTS.md`;
- `CONTRIBUTING.md`;
- `CHANGELOG.md`;
- useful developer documentation;
- CI;
- ktlint;
- detekt;
- tests;
- coverage reporting and badge;
- automated Maven Central release;
- documented official compliance sources.

Anything beyond this should be added because the project needs it, not because a generic open-source checklist says it exists.
