# Contributing

Thanks for considering a contribution to VeriFactu KMP.

The project is currently in specification and repository-structure setup. Code contribution instructions will become more concrete once the Gradle KMP project exists.

## Language

Use English for:

- public API names;
- KDoc and comments;
- documentation;
- commit messages;
- pull requests;
- release notes.

Official Spanish VERI*FACTU terms may remain where they map directly to AEAT protocol, XML, or legal terminology.

## Local Development

Until the Gradle project is created, documentation changes can be reviewed with:

```bash
git diff --check
```

Once the Gradle project exists, contributors should be able to run:

```bash
./gradlew check
```

Expected future checks:

- Kotlin compilation for supported KMP targets;
- formatting/linting;
- static analysis;
- tests;
- coverage;
- Dokka documentation build.

## Compliance-Sensitive Changes

Changes affecting VERI*FACTU behavior must cite official sources.

Use this priority order:

1. BOE legislation.
2. AEAT technical specifications.
3. AEAT XSD/WSDL and validation/error definitions.
4. AEAT FAQ for clarification.

For each compliance-sensitive change:

- link or name the official source in the pull request;
- update `docs/compliance/README.md` or a future traceability matrix entry;
- add or update deterministic tests where applicable;
- avoid treating third-party libraries or blog posts as normative sources.

## Commit Messages

Use Conventional Commits:

```text
feat(core): add cancellation records
fix(hash): normalize decimal representation
docs: explain AEAT error handling
test(xml): add golden registration fixture
```

## Pull Requests

Pull requests should:

- keep scope narrow;
- explain behavior changes;
- include tests for behavior changes where code exists;
- update docs when public behavior changes;
- avoid committing secrets, certificates, private keys, or production credentials.

## Release Changes

Release-related changes must preserve:

- Apache-2.0 licensing;
- Maven Central requirements;
- semantic versioning;
- changelog entries;
- traceability to the official compliance baseline.
