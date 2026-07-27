# Task 16 report: gate index and integration round 1

## RED evidence

- Created `ReleaseGateIndexContractTest` before the index document.
- `./mvnw -q test -Dtest=ReleaseGateIndexContractTest` failed as intended: 3 tests ran, with the index missing (`NoSuchFileException`) and the product README link absent.

## Focused GREEN evidence

- Added the release gate index for all §11 requirements and retained every requirement row.
- Corrected the stale `SceneEncounterSeedProductionParityTest` reference to the existing `ReadinessProductionParityTest`, which actually sets `spring.jpa.open-in-view=false` and checks lazy access across the service boundary. Every other indexed `*Test` class resolves to an existing source file.
- `./mvnw -q test -Dtest=ReleaseGateIndexContractTest` — 3 tests, 0 failures, 0 errors.
- `./mvnw -q test -Dtest=DocsIndexContractTest,DmManualContractTest` — 8 tests total, 0 failures, 0 errors (DocsIndexContractTest: 5; DmManualContractTest: 3).

## Initial full verification status

The initial `./mvnw -q verify` completed with exit 1: 2,633 tests, 5 failures, 8 errors, 6 skipped. These direct §11 failures were not introduced by the Task 16 index/report/docs files, but they were real red gates and no release-gate pass was claimable.

## Integration round 1 evidence

- `./mvnw -q test -Dtest=GameMapControllerTest#shouldRenderEditorPage -Duser.home=/tmp/task16-round1-gamemap` — PASS after merging the duplicate `class` tokens in `templates/maps/editor.html:362`.
- `./mvnw -q test -Dtest=SurfaceNestingGateTest#noBorderedSurfaceSitsOnAnIdenticalBorderedSurface -Duser.home=/tmp/task16-round1-surface` — PASS after moving the separator class to an unpainted wrapper inside an `action-row`; the emergency buttons remain `btn-danger` and retain their actions.
- `./mvnw -q test -Dtest=ViewportAccessibilityGateTest#everyFocusedLayerTrapsAndRestoresFocus -Duser.home=/tmp/task16-round1-viewport` — first isolated run was red at initial focus; clean diagnostic rerun with a separate user-home passed (1 test, 0 failures), showing order/timing contamination rather than a stable runtime defect.
- `./mvnw -q test -Dtest=CoreSessionLoopSmokeTest#cockpitViewportGeometryHoldsAcrossSizesAndZoom -Duser.home=/tmp/task16-round1-core-diagnostic2` — RED reproduced at `1366x768@1.25`; measured topbar children extended to approximately x=1497 while the viewport ended at x=1366. The source fix allows topbar controls to shrink and safely clip labels.
- `./mvnw -q test -Dtest=CoreSessionLoopSmokeTest#cockpitViewportGeometryHoldsAcrossSizesAndZoom -Duser.home=/tmp/task16-round1-core-green6` — PASS.
- `./mvnw -q test -Dtest=ReleaseRehearsalTest -Duser.home=/tmp/task16-round1-rehearsal` — red: both shapes observed IDLE and cascaded selector timeouts. The test’s UI-only Start path initiated `window.location.reload()` without waiting for the `/session/start` response/reloaded RUNNING DOM. The test now waits for the real 200 response and post-reload RUNNING state; a nested-class selector attempt did not execute tests and is not evidence of a pass.
- `./mvnw -q test -Dtest='ReleaseRehearsalFixtureTest#theSeededCampaignIsSessionReady' -Duser.home=/tmp/task16-round1-fixture` — PASS; the fixture now explicitly proves that its approach scene is selected.

The remaining full verification is still required. Until `./mvnw -q verify` passes, this report makes no release-gate pass claim. H2/Flyway version, JVM class-sharing, and missing Playwright host-library warnings remain environment warnings; concurrent Maven runs also produced backup timestamp collisions, so all browser reproductions above were serialized with isolated user homes. No DB/schema/entity behavior was changed.

## Latest required verify result

`./mvnw -q verify` completed red (exit 1). The latest reports show the deterministic direct gates green (`GameMapControllerTest`: 6/0/0; `SurfaceNestingGateTest`: 9/0/0; `CoreSessionLoopSmokeTest`: 1/0/0), but the integrated order still exposes:

- `ViewportAccessibilityGateTest`: 9 tests, 1 failure at `.cockpit-focus-layer wraps forward from the last focusable at 1366x768` (line 352). The same focused method passed in isolation; this remains an order/shared-state blocker, not an assertion relaxation.
- `ReleaseRehearsalTest`: 10 tests, 1 failure and 8 errors. The Start response/reload race is gone, but both shapes still fail to render `[data-current-scene]` after the UI-only start and then cascade into scene/encounter/rehearsal selector failures. The fixture’s direct current-scene contract passes, so this remaining issue is between persisted fixture state and the post-start cockpit render and needs a further focused investigation.

`git diff --check` passed. No release-gate pass is claimed; the remaining blockers are the integrated focus-layer order failure and post-start rehearsal scene/render failure.

## Changed paths

- `docs/product/all-in-one-release-gate.md` — executable §11 requirement-to-proof index.
- `docs/product/README.md` — product documentation link.
- `docs/README.md` — key reference link.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseGateIndexContractTest.java` — index existence, requirement coverage, named-test existence, and product-link contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java` — waits for the actual Start response and reload before asserting the rehearsal state.
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java` — selects the synthetic approach scene explicitly.
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixtureTest.java` — locks the fixture’s initial-scene precondition.
- `src/main/resources/templates/maps/editor.html` — merged duplicate button class attributes.
- `src/main/resources/templates/session/_presentation-preview.html` — restored wrapper-based destructive action separation.
- `src/main/resources/static/css/cockpit-layout.css` — made topbar controls shrink and clip labels safely at constrained geometry.
- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-16-report.md` — exact results and blockers.
