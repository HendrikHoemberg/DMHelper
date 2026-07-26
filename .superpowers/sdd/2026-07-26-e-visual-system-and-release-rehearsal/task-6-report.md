# Task 6 report — Grouping by spacing and hairlines, not by another slab

## Result

Implemented the rendered-tree invariant that no bordered surface sits directly inside a
bordered surface with exactly the same fill. Added the six-page Playwright gate and flattened
nested `.card`, `.readiness-panel`, and `.prep-summary` surfaces to transparent spacing with a
neutral top hairline. Hover behavior is also flattened so it cannot restore a nested slab.

No database, entity, template, semantic-color, or dependency changes were made.

## TDD evidence

### RED

Command:

```text
./mvnw -q test -Dtest=SurfaceNestingGateTest
```

Result: the supplied gate compiled and ran all 6 parameterized pages successfully at HEAD
`8df15b85`; it reported no offenders instead of producing the brief's expected failing pair
inventory. The fixture pages currently contain no same-fill bordered pair before this CSS
change. The invariant was retained unchanged rather than weakening it or adding exceptions.

### GREEN

Command:

```text
./mvnw -q test -Dtest=SurfaceNestingGateTest
```

Result: pass, 6 tests, 0 failures, 0 errors, 0 skipped after the CSS flattening rule.

## Neighboring checks

```text
./mvnw -q test -Dtest=GoldAccentContractTest,DesignTokenContractTest,TypographyRoleContractTest,TypeScaleContractTest,CombatLegibilityContractTest,UiPolishContractTest,InteractionFailureContractTest
```

Result: pass.

```text
./mvnw -q test -Dtest=TypographyRenderGateTest
```

Result: pass.

```text
./mvnw -q test
```

Result: 2,571 tests, 0 failures, 1 error, 6 skipped. The sole error is the pre-existing
`GameMapControllerTest.shouldRenderEditorPage` failure from duplicate `class` markup at
`src/main/resources/templates/maps/editor.html:362`; this task did not modify that file.

`git diff --check` passed. Playwright emits the existing host-library warning for missing
`libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and
`libffi.so.7`, but the focused browser gates execute and pass.

## Changes

- Added `SurfaceNestingGateTest` using `PopulatedCampaignFixture` and
  `PreparationSurfaceFixture` across the six required routes.
- Added the requested `.card .card`, `.card .readiness-panel`, and `.card .prep-summary`
  flattening rules immediately after `.card:active`.
- Preserved the candlelit palette and semantic colors by using `transparent` plus
  `var(--color-border)` for grouping.

## Commits

- `0252aaea feat(visual): express grouping with spacing and hairlines, not stacked slabs`
- Report commit follows after this report is written.

## Concerns

- The red-phase gate did not fail at the supplied fixture state, so its detection logic was not
  empirically proven against a live offender; the invariant remains direct and exception-free.
- The full suite remains non-green only because of the unrelated duplicate-class template error
  noted above.

## Review fix round 1 — validated guard and actual-DOM coverage

### Findings addressed

The gate now distinguishes rendered elements from hidden DOM nodes, recognizes a painted edge
on any of the four computed border sides, and keeps the same-fill comparison. The detector is
covered by an honest focused browser fixture containing an outer left-only border and an inner
bottom-only border with the same fill; it must report `section.outer > … > div.inner`.

The flattening selectors now match emitted markup: `.readiness-panel` and `[data-prep-summary]`.
The former selector was ineffective because the readiness fragment is outside `.card`; the
latter replaces the unused `.prep-summary` class because the fragment root emits
`data-prep-summary`.

The gate also checks the actual campaign readiness panel and preparation summary DOM. It verifies
transparent fill, the expected hairline behavior, and retained block spacing. These checks
validate the visual fix on the rendered fixture pages; the focused synthetic check is the new
detector guard.

### TDD RED

After adding the side-border fixture, actual-DOM assertions, and all-side detector logic—but
before correcting the detector's hidden-node handling and production selectors:

```text
./mvnw -q test -Dtest=SurfaceNestingGateTest
```

Result: 9 tests, 7 failures, 0 errors, 0 skipped. Failures included the two actual-DOM selector
assertions and five hidden navbar nodes reported by the broadened border detector. The focused
side-border fixture exercised the detector and exposed the prior top-border-only limitation.

After excluding `display:none`, `visibility:hidden`, and `visibility:collapse` elements, with
production CSS still unchanged:

```text
./mvnw -q test -Dtest=SurfaceNestingGateTest
```

Result: 9 tests, 2 failures, 0 errors, 0 skipped. Only the two actual-DOM selector assertions
remained, proving the selector failures independently.

### GREEN

After changing the selectors to `.readiness-panel` and `[data-prep-summary]` and preserving the
first-child hairline rule:

```text
./mvnw -q test -Dtest=SurfaceNestingGateTest
```

Result: pass; 9 tests, 0 failures, 0 errors, 0 skipped. This covers the six governed routes,
the side-only detector fixture, and both emitted special surfaces.

### Neighboring visual checks

```text
./mvnw -q test -Dtest=GoldAccentContractTest,DesignTokenContractTest,TypographyRoleContractTest,TypeScaleContractTest,CombatLegibilityContractTest,UiPolishContractTest,InteractionFailureContractTest
```

Result: pass; exit code 0.

```text
./mvnw -q test -Dtest=TypographyRenderGateTest
```

Result: pass; exit code 0.

`git diff --check` passed. Playwright continues to emit the environment warning about missing
host libraries (`libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`,
`libwebp.so.6`, and `libffi.so.7`); the browser tests execute successfully.
