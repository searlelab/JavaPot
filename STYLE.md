# STYLE.md

## 1) Prime directive
- Match the local style of the file you are editing.
- Keep diffs small, targeted, and easy to review.
- Reuse existing code paths/helpers before introducing new abstractions.
- If behavior changes, add or update a test in the matching package.
- Keep the blast radius minimal (few files, minimal API churn)!

## 2) Indentation and formatting
- Java source is tab-indented (`\t`) for block indentation.
- Use one statement per line except in legacy code that already uses compact style.
- Use K&R braces (`if (...) {` on same line).
- Use compact spacing around operators (`a=b`, `i<n`), but preserve nearby style where older spacing exists.
- Prefer explicit line wrapping over very long lines (>150 characters); follow nearby wrapping style.
- Do not perform broad reformatting changes.

## 3) Naming conventions
- Classes/interfaces/enums: `PascalCase`.
- Methods/fields/local variables: `camelCase`.
- Constants (`static final`): `UPPER_SNAKE_CASE`.
- Tests:
  - Unit tests: `*Test`
  - Integration/end-to-end tests: `*IT` (many ITs are gated/skipped by Maven defaults)

## 4) Import style
- Always use imports at top of file; do not use inline fully-qualified class names.
- Group imports by logical origin with blank lines between groups (e.g., `java.*`, third-party, project packages, static imports).
- Prefer explicit imports instead of wildcard imports.

## 5) Package layout and placement
- Production code is under `src/main/java/...`.
- Production resources live in `src/main/resources/...`.
- Tests mirror production package layout under `src/test/java/...`.
- Test fixtures/resources live in `src/test/resources/...`.
- Core package groups include: `algorithms`, `filereaders`, `filewriters`, `datastructures`, `utils`, `cli`, `gui`, `jobs`
- Add new classes to the closest existing package by responsibility (avoid creating new top-level package families unless necessary).

## 6) Design patterns in this repo
- Prefer to use primitives or net.sf.trove4j primitive-backed objects. 
- Prefer to store data in SQLite databases with org.xerial.sqlite-jdbc.
- Outputs and algorithms must be deterministic. Only generate random numbers with fixed seeds.

## 7) Test design patterns in this repo
- Tests use `org.junit.jupiter`.
- Prefer deterministic tests that assert counts/thresholds/ranges, not fragile exact values unless stable.

## 8) Build/verification expectations before finalizing
- At minimum, run compile:
  - `mvn -am -Dmaven.test.skip=true compile`
- Then run focused tests relevant to the changed area:
  - `mvn -am -Dtest=<TestClass> test`
- Run complete compile/tests after every major change:
  - `mvn clean test`

## 9) Things to avoid
- Changing behavior for existing algorithms without explicit request.
- Introducing new dependencies/tooling without explicit request.
- Large opportunistic refactors in unrelated files.
- Reformatting legacy files wholesale.
- Changing generated/build output under `target/` instead of source.

