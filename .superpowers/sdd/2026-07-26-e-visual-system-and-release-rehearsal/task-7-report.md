# Task 7 report: One elevation ladder

## Status

Implemented the prescribed elevation ladder and mapped all governed non-local raw `z-index`
declarations without changing their relative stacking order.

## Changes

- Added the 16 strictly ascending `--z-*` tokens to `tokens.css`, from `30` through `1500`.
- Replaced the governed elevations in `base.css`, `components.css`, `cockpit.css`, and
  `cockpit-layout.css` with the corresponding ladder tokens.
- Preserved the local component stacking values `1` and `2`.
- Added `ElevationModelContractTest` with coverage for ladder definition/order and raw elevation
  prevention.
- Made no database, entity, dependency, or unrelated source changes.

## TDD evidence

The new contract was run before the CSS changes and failed as expected: the ladder was missing and
the governed raw elevations were reported. After the minimal CSS/token changes, the contract passed.

## Verification

- `./mvnw -q test -Dtest=ElevationModelContractTest` — PASS.
- `./mvnw -q test -Dtest=ElevationModelContractTest,DesignTokenContractTest,TypographyRoleContractTest,TypeScaleContractTest,SurfaceNestingGateTest` — PASS.
- `git diff --check` — PASS.
- Raw CSS inventory confirms only the permitted local `z-index: 1`/`z-index: 2` declarations remain.

## Concern

The required browser smoke test
`./mvnw -q test -Dtest=CoreSessionLoopSmokeTest#lifecycleDialogGeometryAndFocusAtMultipleViewports`
did not complete. It timed out waiting for `button[x-ref='sessionButton']` after
`GET /campaigns/null/session` returned HTTP 400. Playwright also reported missing host libraries
(`libicudata.so.66`, `libicui18n.so.66`, `libicuuc.so.66`, `libxml2.so.2`, `libwebp.so.6`, and
`libffi.so.7`). The failure occurred before the elevation interaction/assertion.

## Commits

- `a7661623 feat(visual): replace 27 ad-hoc z-indexes with one readable elevation ladder`
- Documentation commit containing this report: `docs(visual): record task 7 elevation ladder evidence`.

## Review fix round 1 evidence

- Extended `ElevationModelContractTest` to walk every HTML template and reject raw numeric
  `z-index` values above `2`; the new test first failed on the summary modal and three map-editor
  elevations (`1000`, `20`, `30`, `30`).
- Replaced the encounter summary modal inline `1000` with `var(--z-modal)`.
- Replaced map-editor `20`/`30`/`30` with `var(--z-workspace-chrome)`/
  `var(--z-workspace-focus)`/`var(--z-workspace-focus)`, preserving their local order.
- Made `lifecycleDialogGeometryAndFocusAtMultipleViewports` self-sufficient by calling the existing
  campaign fixture setup when run outside the ordered suite.
- `./mvnw -q test -Dtest=ElevationModelContractTest` — PASS after the red phase.
- `./mvnw -q test -Dtest=CoreSessionLoopSmokeTest#lifecycleDialogGeometryAndFocusAtMultipleViewports` — PASS; fresh standalone run completed with exit code 0.
- `./mvnw -q test -Dtest=ElevationModelContractTest,DesignTokenContractTest,TypographyRoleContractTest,TypeScaleContractTest,SurfaceNestingGateTest` — PASS.
- Playwright still prints host-library warnings, but the mandated smoke test completes successfully.
