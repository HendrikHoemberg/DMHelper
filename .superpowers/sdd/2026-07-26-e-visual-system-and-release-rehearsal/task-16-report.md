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

## Integration round 2: diagnostic findings and stopped verification

The long-running round-2 verification was stopped on request. A process check after stopping found no remaining Maven, Surefire, Playwright, or ReleaseRehearsal process. The full gate is therefore not green and no release-gate pass is claimed.

### Viewport focus-order reproduction and fix

- `./mvnw -q test -Dtest=ViewportAccessibilityGateTest -Duser.home=/tmp/task16-round2-viewport-full` reproduced the integrated failure: 9 tests with the focused-layer forward-wrap failure.
- The minimal ordered reproduction `./mvnw -q test -Dtest=ViewportAccessibilityGateTest#everyFocusedLayerTrapsAndRestoresFocus -Duser.home=/tmp/task16-round2-viewport-method` initially failed at initial focus.
- Diagnostic DOM state at failure was `focused: "story"`, `layerHidden: false`, `focusLayerDisplay: "flex"`, with the story layer mounted and one focusable button, while `document.activeElement` had returned to the old Session opener. This traced the race to the prior lifecycle's deferred Alpine `$nextTick` focus restoration running after the story focus layer opened.
- The fix records `document.activeElement` at lifecycle close and lets the deferred restore run only when focus has not moved to another live control. If another control owns focus, it clears the stale return target and leaves focus there; forward/backward trap assertions are unchanged.
- `./mvnw -q test -Dtest=ViewportAccessibilityGateTest#everyFocusedLayerTrapsAndRestoresFocus -Duser.home=/tmp/task16-round2-viewport-redgreen` — PASS, 1 test, 0 failures, 0 errors.
- A fresh full-class run before the final cleanup — `./mvnw -q test -Dtest=ViewportAccessibilityGateTest -Duser.home=/tmp/task16-round2-viewport-full-green` — PASS, 9 tests, 0 failures, 0 errors.

### Release rehearsal reproduction and remaining blocker

- The fixture direct precondition remained green: `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest -Duser.home=/tmp/task16-round2-fixture-green` — PASS, 12 tests, 0 failures, 0 errors. The rehearsal fixture now selects its initial approach scene and leaves the hostile encounter unlinked only for rehearsal-shape data, so the scene action can create it during the real UI flow; default fixture behavior remains covered.
- Earlier exact browser/server evidence showed the original transition POST `/api/v1/campaigns/{id}/session/current-scene/follow-transition` returning HTTP 500 because its response serialized a lazy target `Scene` after the transaction boundary (`LazyInitializationException`). The template/JS diagnostic fix resolves the target from the rendered scene picker and uses the existing current-scene PUT path, without injecting service state or weakening selectors.
- The completed rehearsal report at `2026-07-27 19:54:16 +0200` still records `ReleaseRehearsalTest` as 10 tests, 8 errors, 0 failures. The first remaining failure is `step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit` at line 140 waiting for the post-transition scene text to change; later errors are cascades waiting for encounter and other downstream selectors. This means the transition/render path is not yet proven green.
- The focused command `./mvnw -q test '-Dtest=ReleaseRehearsalTest$Linear' -Duser.home=/tmp/task16-round2-release-linear` was started as the minimal serial reproduction but was interrupted before completion. It has no valid exit result and must not be reported as a pass.
- The requested full `./mvnw -q verify` was not completed after the round-2 changes. No claim is made that it passes.

### Round-2 working-tree status

Preserved uncommitted diagnostic changes are limited to:

- `src/main/resources/static/js/session-cockpit.js` — lifecycle focus-restore guard and transition/render path investigation fix.
- `src/main/resources/templates/session/_story-rail.html` — passes the rendered transition target title to the UI handler.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java` — uses the rehearsal-specific fixture shape.
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java` — isolates rehearsal encounter-link state from the default fixture.

Because the full verify is incomplete and ReleaseRehearsalTest remains red in the latest completed report, a focused commit should not be made yet. `git diff --check` had passed before this round; the report append itself still requires a final diff check when work resumes.

## Integration round 2 continuation: transition boundary fix

### Phase 1/2 evidence

- `./mvnw -q test '-Dtest=ReleaseRehearsalTest$Linear#step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit' -Duser.home=/tmp/task16-round2-transition-red` — RED, 1 test, 1 error at the scene-text wait.
- The temporary network/DOM diagnostic after the click showed: current scene remained `Mossbound Approach`; the clicked button carried transition id `c2de1d1e-6363-435f-9969-ebb71d038021` and target title `Beacon Undercroft`; `responses: []`; and `#cockpitScenePicker` contained only `{value:"", text:"Jump to scene…"}`. The fallback therefore never issued a PUT because the refreshed module had no target options.
- The data-flow cause was the runtime module controller: the full session controller populated `scenePickerGroups`, but `CockpitRuntimeModuleController.story` did not. Every `refreshModules(['story'], ...)` response consequently rendered an empty picker.
- The original POST remained the correct interface. Its HTTP 500 was caused by returning the lazy `SceneTransition.targetScene` entity after the transaction; `SessionApiController.sceneData` accessed the detached target's lazy state during response conversion.

### Root-cause fixes

- `SceneTransitionService.followTransition` now initializes the returned target entity, map, and encounter before the transaction boundary. This is DTO-response preparation only; no entity mapping or schema behavior changed.
- `CockpitRuntimeModuleController.story` now supplies the same `scenePickerGroups` model data as the full cockpit/story-rail controller, restoring the existing `setCurrentScene` action after module refresh.
- Restored `followTransition` to the intended POST and removed the title/picker client fallback. HTTP 500s are not masked.
- Added a controller contract assertion that a refreshed story module renders a real picker option.

### Focused GREEN evidence

- `./mvnw -q test -Dtest=CockpitRuntimeModuleControllerTest,SceneTransitionServiceTest,SessionApiControllerTest,SessionCockpitTemplateContractTest -Duser.home=/tmp/task16-round2-boundary-green` — PASS: 13 + 11 + 5 + 24 tests, all 0 failures/errors.
- `./mvnw -q test '-Dtest=ReleaseRehearsalTest$Linear#step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit' -Duser.home=/tmp/task16-round2-transition-green` — PASS, 1 test, 0 failures/errors; fresh report timestamp 20:07:35.
- `./mvnw -q test '-Dtest=ReleaseRehearsalTest$Branched#step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit' -Duser.home=/tmp/task16-round2-branched-transition-green` — PASS, 1 test, 0 failures/errors; fresh report timestamp 20:16:13.
- `./mvnw -q test -Dtest=ReleaseRehearsalFixtureTest -Duser.home=/tmp/task16-round2-fixture-final` — PASS, 12 tests, 0 failures/errors.
- `./mvnw -q test -Dtest=ViewportAccessibilityGateTest -Duser.home=/tmp/task16-round2-viewport-final` — PASS, 9 tests, 0 failures/errors; the lifecycle focus guard remains green.

### Full-class and verify status

- `./mvnw -q test -Dtest=ReleaseRehearsalTest -Duser.home=/tmp/task16-round2-rehearsal-green-final` did not produce a valid final report. Surefire created a JVM dump while `ReleaseRehearsalTest$RehearsalSteps.closePage` blocked in Playwright `BrowserContext.close`/`PipeTransport`; the run was stopped after no Maven/Java process remained. The dump also records unsupported-OS Playwright fallback browser warnings. This is an environment/runner teardown block, not evidence of a green full class.
- `./mvnw -q test -Dtest='ReleaseRehearsalTest$Linear' -DforkCount=0 -Duser.home=/tmp/task16-round2-linear-nofork` likewise did not produce a valid final report; the process ended without a completed Surefire result after Playwright fallback-browser warnings. The focused transition method is the valid Linear proof currently available.
- `timeout --signal=TERM 120 ./mvnw -q verify -Duser.home=/tmp/task16-round2-verify-final` failed before project loading with exit 1 because the isolated home lacked the cached Spring Boot parent and DNS could not resolve Maven Central (`repo.maven.apache.org: Temporary failure in name resolution`).
- The required `./mvnw -q verify` was then started with the established cache and reached the integrated test suites, including `ReleaseRehearsalTest`, but it did not produce a final exit/report before the runner became process-less and was stopped. No verify-pass claim is made.

No commit was made. The working tree remains uncommitted pending a complete release rehearsal/full verify result or an explicitly accepted environment-only block. `git diff --check` is required again after this report append.

### Complete round-2 changed-path inventory

- `.superpowers/sdd/2026-07-26-e-visual-system-and-release-rehearsal/task-16-report.md`
- `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneTransitionService.java`
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitRuntimeModuleController.java`
- `src/main/resources/static/js/session-cockpit.js`
- `src/main/resources/templates/session/_story-rail.html`
- `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitRuntimeModuleControllerTest.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/support/ReleaseRehearsalFixture.java`
