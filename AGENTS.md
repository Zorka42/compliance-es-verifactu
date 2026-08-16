# AGENTS.md - VeriFactu KMP

Project-specific instructions for coding agents reviewing code or making changes in the VeriFactu KMP repository.

**Precedence:** Anything in this file overrides user-level agent settings. Project-specific instructions win over general preferences.

---

## Working Principles

Behavioral guidelines to reduce common LLM coding mistakes. Merge with the project-specific conventions below as needed.

**Tradeoff:** These guidelines bias toward correctness and traceability over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

Do not assume compliance behavior. Do not hide uncertainty.

Before implementing:

- State assumptions explicitly when they affect public API, compliance behavior, module boundaries, or tests.
- If multiple interpretations exist, name them instead of silently choosing.
- If a simpler approach exists, say so.
- If a legal, AEAT, BOE, schema, or protocol requirement is unclear, stop and ask.

### 2. Simplicity First

Minimum code that solves the requested problem.

- No features beyond what was asked.
- No abstractions for single-use code.
- No configurability that is not required by the product spec or compliance sources.
- No hidden global state, background work, persistence, network calls, or certificate handling.
- If an API can stay smaller without losing required behavior, keep it smaller.

### 3. Surgical Changes

Touch only what the task requires.

- Do not reformat or refactor adjacent code unless it is necessary for the requested change.
- Match nearby style and module boundaries.
- Remove imports, declarations, fixtures, or docs that your own change made obsolete.
- Do not remove pre-existing dead code unless asked.

Every changed line should trace to the user's request, a failing check, or an official compliance source.

### 4. Goal-Driven Execution

Turn tasks into verifiable goals:

```text
1. Change behavior -> verify with deterministic tests.
2. Change public API -> verify compile/tests/docs examples.
3. Change compliance logic -> verify source traceability + fixtures.
```

For multi-step tasks, state a brief plan and loop until the relevant checks pass or the blocker is concrete.

---

## Project Overview

**VeriFactu KMP** is an Apache-2.0 Kotlin Multiplatform library for building VERI*FACTU fiscal-record, XML, QR, and optional AEAT submission functionality into invoicing software.

The repository is currently draft/pre-implementation. It defines the product and open-source documentation structure before Kotlin modules are created.

| Aspect | Details |
| --- | --- |
| Language | Kotlin |
| Platform | Kotlin Multiplatform |
| Target source sets | `commonMain`, `jvmMain`, `androidMain`, `iosMain`, `macosMain` |
| Product mode | VERI*FACTU |
| License | Apache-2.0 |
| Canonical language | English |
| Compliance sources | Official BOE and AEAT materials |

This library is not a complete Sistema Informatico de Facturacion. It must not grow a database, invoice store, durable queue, background scheduler, certificate vault, UI, invoice PDF renderer, accounting system, hosted API, or legal/tax advisory layer unless the product scope explicitly changes.

---

## Project Structure

Current repository structure:

```text
verifactu-kmp/
├── docs/
│   ├── compliance/
│   ├── core-concepts.md
│   ├── error-handling.md
│   ├── getting-started.md
│   ├── integration-flow.md
│   ├── integration-responsibilities.md
│   └── protocol-behavior.md
├── ACKNOWLEDGEMENTS.md
├── CHANGELOG.md
├── CONTRIBUTING.md
├── LICENSE
├── OPEN_SOURCE_REQUIREMENTS.md
├── PRODUCT_SPEC.md
├── README.md
└── SECURITY.md
```

Planned implementation shape:

```text
verifactu-kmp/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── modules/ or src/
├── schemas-aeat/
└── docs/
```

Recommended Gradle modules from `PRODUCT_SPEC.md`:

- `verifactu-core`
- `verifactu-xml`
- `verifactu-qr`
- `verifactu-aeat`
- `verifactu-testkit`

Keep the deterministic core usable without XML serialization and transport modules.

---

## Build Commands

Until the Gradle project exists, documentation changes can be checked with:

```bash
git diff --check
```

Once the Gradle KMP project exists, expected checks are:

```bash
./gradlew check
./gradlew compileKotlinMetadata
./gradlew jvmTest
./gradlew testDebugUnitTest
./gradlew iosSimulatorArm64Test
./gradlew macosX64Test
```

Use the concrete tasks defined by the repository once Gradle files are present.

---

## Compliance Conventions

Compliance-sensitive behavior must be derived from official sources in this priority order:

1. BOE legislation.
2. AEAT technical specifications.
3. AEAT XSD/WSDL and published validation/error definitions.
4. AEAT FAQ for clarification.

Rules:

- Third-party libraries, examples, blog posts, and vendor documentation are not normative sources.
- Every compliance-sensitive implementation change must cite the exact source version or section in the PR or traceability docs.
- Update `docs/compliance/README.md` or the future traceability matrix when behavior changes.
- Add or update deterministic tests for compliance behavior.
- Recheck regulatory dates and source versions before release work.

---

## Kotlin Style

Use the official Kotlin coding conventions as the default style. Use Android Kotlin style only for Android-specific source sets if the project later opts into it explicitly.

### Formatting

- 4-space indentation, no tabs.
- Java-style braces: opening brace at the end of the construct line, closing brace aligned with the construct.
- No semicolons.
- Spaces around binary operators, no spaces around unary operators, `.` / `?.`, `::`, or nullable `?`.
- Prefer expression bodies for simple single-expression functions.
- Wrap long function/class signatures with one parameter per line.
- Use trailing commas at declaration sites for multiline declarations; use them at call sites when they improve diffs and readability.
- Do not use horizontal alignment. Renaming an identifier should not require re-aligning nearby code.

### Files and Layout

- Follow package-directory layout under each source root.
- For a file with one primary class or interface, name the file after that type.
- For multiple related top-level declarations, use a descriptive `UpperCamelCase.kt` name. Avoid meaningless names such as `Util.kt`.
- In multiplatform source sets, suffix platform-specific top-level declaration files when needed to avoid JVM file-facade clashes, for example `Platform.jvm.kt`, `Platform.android.kt`, or `Platform.ios.kt`.
- Class member order: properties and initializer blocks, secondary constructors, methods, companion object.
- Keep related methods together. Do not sort members alphabetically or mechanically by visibility.
- Keep overloads next to each other.
- When implementing an interface, keep implementation members in the same order as the interface where practical.

### Naming

- Packages: lowercase, no underscores.
- Classes, interfaces, objects, annotations, enum classes, and value classes: `UpperCamelCase`.
- Functions, properties, parameters, and local variables: `lowerCamelCase`.
- Constants: `UPPER_SNAKE_CASE` only for `const val` or deeply immutable top-level/object `val` values.
- Backing properties: prefix the private mutable property with `_` only when it backs a public read-only property.
- Two-letter acronyms may stay uppercase (`IO`); longer acronyms use normal camel casing (`Xml`, `Http`, `Aeat`) unless an official protocol token is clearer.
- Test names may use backticks in non-Android-only tests. For Android runtime compatibility, prefer regular camelCase test names if the test must run on Android.

### Public API

VeriFactu KMP is a library, so public API stability matters.

- Enable Kotlin explicit API mode when implementation starts.
- Public declarations must have explicit visibility and explicit return/property types.
- Public API and KDoc are English-first.
- Preserve official Spanish VERI*FACTU, AEAT, BOE, XML, or legal terms where they improve protocol precision.
- Prefer immutable public values.
- Use domain types for fiscal concepts instead of primitive obsession.
- Never use `Double` or `Float` for fiscal amounts.
- Use explicit date/time and decimal types.
- Keep raw XML and parsed AEAT response data inspectable for diagnostics.
- Avoid boolean arguments in public API when a named function, enum, or domain type would be clearer.
- Keep parameter order consistent across related APIs: essential domain inputs first, optional/configuration inputs last.
- Use nullable returns for absent data, typed results for expected domain outcomes, and exceptions only for exceptional failures.
- Do not use exceptions for normal control flow.

### Comments and KDoc

- Public API should have KDoc once implementation begins.
- Do not add obvious implementation comments.
- Add implementation comments only for hidden constraints, non-obvious compliance decisions, subtle platform behavior, or source-backed workarounds.
- Comments and KDoc must not claim legal certainty beyond the cited official sources.

---

## Architecture Conventions

### Layering

The product has three layers:

```text
deterministic core
  models
  validation
  hash input construction
  SHA-256 hashing
  fiscal record creation
  chain state
  QR payload
  compliance metadata

serialization
  deterministic XML
  request XML
  response XML
  SOAP fault parsing
  schema fixtures

AEAT transport
  environments
  SOAP client
  TLS/client certificate adapters
  typed submission results
  flow control
  retry/incidence support
```

Core rules:

- `commonMain` owns deterministic fiscal behavior whenever possible.
- Platform source sets contain only platform-specific adapters or interop code.
- Core record generation must perform no I/O.
- Validation must be deterministic and side-effect free.
- QR functionality returns payloads/URLs; host applications render images and invoice layouts.
- Transport is optional and must not own scheduling, sleeping, persistence, durable queues, or certificate storage.

### KMP Source-Set Contract

Advertise a target only when it compiles and has relevant tests in CI.

Expected v1 source sets:

```text
commonMain
commonTest
jvmMain
jvmTest
androidMain
androidUnitTest
iosMain
iosX64Test
iosArm64Test
iosSimulatorArm64Test
macosMain
macosX64Test
macosArm64Test
```

### Determinism

Compliance output must be deterministic across supported targets:

- hash input construction;
- SHA-256 hashes;
- XML bytes;
- QR payloads;
- validation issue codes and field paths;
- parsed response semantics for fixed inputs.

No locale, timezone, platform default charset, map iteration order, floating-point formatting, wall-clock lookup, or random value may leak into deterministic outputs.

### Errors and Diagnostics

- Local validation returns structured issues: stable library issue code, field path, severity, message, optional AEAT code, and optional compliance source reference.
- Submission outcomes distinguish accepted records, accepted-with-errors records, rejected records, SOAP faults, transport failures where the request was not sent, unknown-delivery failures, and parsed flow-control information.
- Core functionality should not log by default.
- Raw XML logging must be opt-in because XML can contain personal and fiscal data.
- Credentials, certificate bytes, private keys, tokens, and secrets must never appear in logs, exceptions, snapshots, fixtures, or `toString()` output.

---

## Code Review Checklist

When reviewing changes, lead with bugs, risks, regressions, missing tests, and compliance gaps.

Check:

- Does compliance behavior cite BOE/AEAT sources?
- Is source-to-code-to-test traceability updated?
- Are deterministic fixtures or tests added for behavior changes?
- Does core code avoid I/O, persistence, network calls, clocks, randomness, and platform defaults?
- Are public APIs explicit, stable, documented, and small?
- Are fiscal amounts protected from `Double` / `Float`?
- Are date/time and decimal representations explicit?
- Are target-specific APIs isolated to the correct source set?
- Are errors typed and actionable rather than generic booleans or strings?
- Are secrets, certificates, personal data, and raw XML protected from accidental logging?
- Do docs and examples match the implemented API?

---

## Anti-Patterns to Flag

| Anti-pattern | What to suggest instead |
| --- | --- |
| Compliance behavior copied from a blog, vendor docs, or another library | Use BOE/AEAT sources and record traceability |
| Hidden I/O in `verifactu-core` | Pure deterministic function with explicit inputs |
| Library-owned invoice database, chain storage, queue, scheduler, or UI | Host-application responsibility |
| `Double` / `Float` for fiscal amounts | Explicit decimal/domain money type |
| Platform default timezone, locale, charset, or map ordering in deterministic output | Explicit formatting and stable ordering |
| Unstructured `Boolean success` or stringly errors | Typed validation issues and submission outcomes |
| Exceptions used for expected validation/control-flow outcomes | Typed result or validation model |
| Public API relying on inferred return types | Explicit API mode and explicit types |
| Boolean mode flags in public API | Named functions, enums, or domain types |
| Large catch-all `Util`, `Manager`, or `Helper` APIs | Focused domain names and small modules |
| Android/JVM-only dependency in `commonMain` | `expect`/`actual` or platform adapter |
| Logging raw XML, PII, credentials, certificates, or private keys | Opt-in redacted diagnostics |
| Tests requiring live AEAT availability in normal PR checks | Mocked/fixed fixtures; live checks only as explicit/manual jobs |
| Advertising a KMP target that only metadata-compiles | Compile and run target-relevant tests in CI |
| Docs updated without code/tests for behavior changes | Keep docs, implementation, and tests together |

---

## Key Files

- `README.md` - project summary, status, installation target, quick start, documentation index.
- `PRODUCT_SPEC.md` - product scope, architecture, success criteria, roadmap, compliance behavior.
- `OPEN_SOURCE_REQUIREMENTS.md` - repository, quality, release, and contribution requirements.
- `CONTRIBUTING.md` - contributor language, local development, compliance-sensitive change, commit, and PR rules.
- `docs/compliance/sources.md` - official compliance source list.
- `docs/compliance/README.md` - traceability rules and initial matrix.
- `docs/error-handling.md` - validation, submission outcome, unknown delivery, and diagnostics expectations.

## Project Task Tracking

The authoritative implementation backlog for this repository is the Notion data source **Zorka Accounting - Tasks**:

- Database: https://app.notion.com/p/3bd4f302230980d3bb72ffbd0068aa8b
- Data source: `collection://3bd4f302-2309-8053-8a55-000b43accb58`
- Project property: `Verifactu`
- Milestones: `VF0 Foundation`, `VF1 Domain Core`, `VF2 XML QR and Protocol Artifacts`, `VF3 AEAT Submission Runtime`, `VF4 End-to-End Integration`, `VF5 OSS Release Hardening`

Update a task to `To verify` only after its acceptance criteria are implemented and its relevant non-mocked checks pass. Do not mark a task `Done` without external verification.

---

## Dependencies and Tooling

Expected baseline once implementation starts:

- Gradle Kotlin DSL.
- Kotlin Multiplatform.
- `ktlint` for formatting/style.
- `detekt` for static analysis.
- Kover for coverage unless a better standard Kotlin solution is adopted.
- Dokka for public API documentation.

Prefer standard Kotlin tooling over custom scripts.

---

## How to Verify Changes

### Documentation-only changes

```bash
git diff --check
```

Also verify links and examples manually when touched.

### Kotlin changes

Run the narrowest relevant check first, then the broader check:

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew check
```

For KMP behavior, run target-specific tests relevant to the touched source sets.

### Compliance-sensitive changes

Verification must include:

- official source citation;
- traceability update;
- deterministic tests or fixtures;
- target checks for every affected advertised platform.

Do not rely on live AEAT availability for normal PR verification.

---

## Commit Conventions

Use Conventional Commits:

```text
feat(core): add cancellation records
fix(hash): normalize decimal representation
docs: explain AEAT error handling
test(xml): add golden registration fixture
```

Rules:

- Subject line should be concise, imperative, and scoped when useful.
- One logical change per commit.
- Docs and code changes can share a commit only when they describe the same behavior change.
- Do not include secrets, certificates, private keys, credentials, or production data.
- Commit or push only when the user asks.
