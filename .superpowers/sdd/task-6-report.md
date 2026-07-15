# Task 6 Report: Mirror Library Hierarchy Across Sibling Result Types

## RED

Added `siblingLibraryResultsReuseTitleAndMetadataHierarchy` to
`UiPolishContractTest` before changing implementation files.

`./mvnw test -Dtest=UiPolishContractTest` failed as expected: the spell
fragment did not yet contain `library-card__title` or `library-card__meta`.
The test run reported 8 tests, 1 failure, 0 errors.

## GREEN

Applied the required scoped substitutions:

- Spells now use the shared title and metadata anatomy, with accent library
  chips for both cantrip and level labels while preserving spell expressions
  and school/ritual/concentration content.
- Magic-item and class headings and badge wrappers use the shared title and
  metadata anatomy; their existing badges and expressions remain unchanged.
- Equipment remains a table. Its name, category, weight, and properties cells
  use the requested table anatomy while cost is unchanged.
- Magic-item, equipment, and class empty states are gated on a null or empty
  result collection and use `empty-state__title`.
- Added only token-based `library-table__name` and `library-table__meta` CSS.

`./mvnw test -Dtest=UiPolishContractTest,HtmxTemplateExpressionTest` passed:
9 tests, 0 failures, 0 errors.

## Final tests

- `git diff --check`: passed.
- `./mvnw test`: attempted; blocked by pre-existing environment incompatibility.
  `FileServeControllerSecurityTest` cannot initialize Mockito's inline Byte
  Buddy mock maker because the Java 26 runtime cannot self-attach an agent.
  The isolated reproduction (`./mvnw test -Dtest=FileServeControllerSecurityTest`)
  reports 3 errors before controller assertions run. No Task 6 files,
  dependencies, or test infrastructure were changed to address it.

## Self-review

- Reviewed the final diff against the Task 6 brief.
- Modified only the four requested sibling templates, focused component CSS,
  and `UiPolishContractTest`; this report is the explicitly requested
  handoff artifact.
- No raw colors, dependencies, persistence, responsive behavior, or
  search/filter behavior were added or changed.
- All pre-existing content and Thymeleaf expressions in the specified result
  markup were retained; equipment remains a table.

## Changed files

- `src/main/resources/templates/library/_spell-card.html`
- `src/main/resources/templates/library/_magic-item-card.html`
- `src/main/resources/templates/library/_equipment-card.html`
- `src/main/resources/templates/library/_class-card.html`
- `src/main/resources/static/css/components.css`
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/UiPolishContractTest.java`
- `.superpowers/sdd/task-6-report.md`

## Concerns and limitations

- Browser-tab scans were not run: this harness has no configured interactive
  browser session or host-library data source. The focused static contract and
  Thymeleaf-expression tests cover the changed fragment structure.
- The full suite remains blocked by the Java 26/Mockito inline-agent runtime
  limitation described above; the focused Task 6 suites pass.
