# Task 16 report: gate index

## RED evidence

- Created `ReleaseGateIndexContractTest` before the index document.
- `./mvnw -q test -Dtest=ReleaseGateIndexContractTest` failed as intended: 3 tests ran, with the index missing (`NoSuchFileException`) and the product README link absent.

## Focused GREEN evidence

- Added the release gate index for all §11 requirements and retained every requirement row.
- Corrected the stale `SceneEncounterSeedProductionParityTest` reference to the existing `ReadinessProductionParityTest`, which actually sets `spring.jpa.open-in-view=false` and checks lazy access across the service boundary. Every other indexed `*Test` class resolves to an existing source file.
- `./mvnw -q test -Dtest=ReleaseGateIndexContractTest` — 3 tests, 0 failures, 0 errors.
- `./mvnw -q test -Dtest=DocsIndexContractTest,DmManualContractTest` — 8 tests total, 0 failures, 0 errors (DocsIndexContractTest: 5; DmManualContractTest: 3).

## Full verification

`./mvnw -q verify` completed with exit 1: 2,633 tests, 5 failures, 8 errors, 6 skipped.

Existing/out-of-scope failures recorded without changing production behavior:

- `GameMapControllerTest.shouldRenderEditorPage` — Thymeleaf parse error at `templates/maps/editor.html:362`, duplicate `class` attribute.
- `ViewportAccessibilityGateTest.everyFocusedLayerTrapsAndRestoresFocus` — initial focus remains the Session button rather than entering `.cockpit-focus-layer`.
- `SurfaceNestingGateTest.noBorderedSurfaceSitsOnAnIdenticalBorderedSurface` — same-fill bordered surface in the presentation preview destructive action.
- `CoreSessionLoopSmokeTest.cockpitViewportGeometryHoldsAcrossSizesAndZoom` — command-bar reachability fails at `1366x768@1.25`.
- `ReleaseRehearsalTest` failed in both Linear and Branched shapes: session remained `IDLE` instead of `RUNNING`, the scene-to-encounter action was unavailable, and subsequent rehearsal steps timed out waiting for story, initiative, combatant, quick-note, presentation, encounter-end, and session-log selectors.

The full run also emitted existing H2/Flyway version warnings, JVM class-sharing warnings, and expected application error logs from tests exercising failure handling. None is caused by the Task 16 files. No database, schema, entity, controller, or runtime behavior was changed.

## Changed paths

- `docs/product/all-in-one-release-gate.md` — executable §11 requirement-to-proof index.
- `docs/product/README.md` — product documentation link.
- `docs/README.md` — key reference link.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseGateIndexContractTest.java` — index existence, requirement coverage, named-test existence, and product-link contract.
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-16-report.md` — exact results and blockers.
