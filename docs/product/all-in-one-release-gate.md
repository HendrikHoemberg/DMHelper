# All-in-One Release Gate

The executable gate defined in
`docs/superpowers/specs/2026-07-22-phandelver-all-in-one-corrective-design.md` §11.
Every requirement row names an executable proof.

> **This index is human-maintained.** It was previously validated by
> `ReleaseGateIndexContractTest`, which resolved every backticked test reference against
> `src/test/java`. That test was removed by the test-suite triage
> (`docs/test-suite-triage.md`) because it made every test rename a three-place edit.
> Names here can now drift from the suite — verify them by hand when you rely on this table.

Run the core browser gate (the indexed §11.1 proofs and release-index contract are also run by the full gate):

    ./mvnw -q test -Dtest='ReleaseRehearsalTest,ViewportAccessibilityGateTest,TypographyRenderGateTest,SurfaceNestingGateTest,CoreSessionLoopSmokeTest'

Run the full release gate; this command must pass before the all-in-one premise may be claimed:

    ./mvnw -q verify

## §11.1 Automated coverage

| Req | Requirement | Proved by |
|---|---|---|
| 11.1.1 | Production-parity integration; `open-in-view=false`; fails on lazy access after a service boundary | `ReadinessProductionParityTest`, `FullPageRenderSmokeTest` |
| 11.1.2 | Every cockpit module renders empty, populated, loading, error, compact and focused | `RuntimeModuleShellContractTest`, `CockpitRuntimeModuleControllerTest`, `CockpitRuntimeModuleViewServiceTest`, `CockpitModuleInitialLoadBrowserTest` — **partial, see note** |
| 11.1.3 | Layout schema migration, constraints, docking, serialization, invalid recovery, preset reset | `CockpitLayoutPresetServiceTest`, `CockpitWorkbenchTemplateContractTest` |
| 11.1.4 | Browser interaction: edit lock, dividers, docking, tabs, focus, keyboard, persistence, retry, resize | `CoreSessionLoopSmokeTest`, `ViewportAccessibilityGateTest` |
| 11.1.7 | Tracker-driven defeat/revive and a cross-midnight session produce a faithful log | `SessionEncounterEvidenceIntegrationTest`, `ReleaseRehearsalTest` |
| 11.1.8 | No console errors, unhandled rejections, malformed requests or silent non-2xx actions | `BrowserFailureCollector` attached in `ReleaseRehearsalTest`, `ViewportAccessibilityGateTest`, `CoreSessionLoopSmokeTest` |
| 11.1.9 | App ships with no authentication layer; must bind to loopback (`server.address=127.0.0.1`). Any change to `server.address` is a security decision requiring explicit sign-off. | `ReleaseRehearsalTest` |

## §11.2 Viewport and accessibility gate — 1366×768 and 1920×1080

| Requirement | Proved by |
|---|---|
| No document-level scrolling | `ViewportAccessibilityGateTest#theCockpitNeverScrollsTheDocument` |
| Modules do not overlap or clip | `ViewportAccessibilityGateTest#modulesNeitherOverlapNorClip` |
| Command bar fully reachable | `ViewportAccessibilityGateTest#theCommandBarStaysFullyReachable` |
| Minimum module sizes respected | `ViewportAccessibilityGateTest#everyVisibleModuleRespectsItsDeclaredMinimum` |
| Keyboard: edit mode, tabs, focus/restore, splitters | `ViewportAccessibilityGateTest#keyboardUsersCanDriveTheWorkspace` |
| Focus visible and restored after dialogs | `ViewportAccessibilityGateTest#focusIsVisibleAndRestoredAfterAFocusedLayer` |
| Reduced motion respected | `ViewportAccessibilityGateTest#reducedMotionIsRespected`, `MotionBudgetContractTest` |

## §11.3 Representative release rehearsal

All ten steps, run against two synthetic campaign shapes, are declared in the shared
`ReleaseRehearsalTest$RehearsalSteps` class and executed by its `Linear` and `Branched`
nested runners:

`ReleaseRehearsalTest$RehearsalSteps#step1_readinessReportIsInspectedAndClear`,
`ReleaseRehearsalTest$RehearsalSteps#step2_sessionStartsAtTheSelectedScene`,
`ReleaseRehearsalTest$RehearsalSteps#step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit`,
`ReleaseRehearsalTest$RehearsalSteps#step4_theEncounterIsPreparedAndRunThroughTheReadinessFlow`,
`ReleaseRehearsalTest$RehearsalSteps#step5_initiativeDamageConditionsDefeatAndTurnsResolve`,
`ReleaseRehearsalTest$RehearsalSteps#step6_statblocksAndRulesAreConsultedInsideDmhelper`,
`ReleaseRehearsalTest$RehearsalSteps#step7_notesAreCapturedAndThePlanIsUpdated`,
`ReleaseRehearsalTest$RehearsalSteps#step9_theEncounterAndSessionAreCompleted`, and
`ReleaseRehearsalTest$RehearsalSteps#step10_theGeneratedLogAgreesWithWhatHappened`.

The seven gate-fail conditions are asserted at the point each becomes observable; see the
method comments.

The fixtures are `ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP` and `BRANCHED_TWO_MAPS`.
Both are entirely synthetic. `ReleaseRehearsalFixtureTest` checks their provenance against the
reviewed exclusion vocabulary currently named in that test (`phandelver`, `klarg`, `cragmaw`,
`wave echo`, `sildar`, `gundren`, `rockseeker`, `neverwinter`, and `tresendar`). This is a
bounded regression check for the known published vocabulary and fixture content under review,
not an unbounded guarantee about every possible published campaign.

## §10 Visual system

The visual rules are enforced continuously rather than at gate time:
`DesignTokenContractTest`, `TypeScaleContractTest`, `TypographyRoleContractTest`,
`TypographyRenderGateTest`, `CombatLegibilityContractTest`, `GoldAccentContractTest`,
`SurfaceNestingGateTest`, `ElevationModelContractTest`, `ControlConsistencyContractTest`,
`DestructiveActionContractTest`, `MotionBudgetContractTest`.

## Note on reduced coverage (test-suite triage)

`docs/test-suite-triage.md` removed 27 test classes that asserted on template and stylesheet
*source text* rather than on rendered output. Two rows above are affected:

- **11.1.2** previously also named `CockpitRuntimeModuleContractTest`, which asserted the
  per-module × per-state matrix by grepping template source. The surviving tests prove each
  module route returns its fragment and that the rendered cockpit emits exactly one root per
  registry key; the exhaustive state matrix is now covered by the browser gates and visual
  review rather than by static assertion.
- **§10** previously also named `RuntimeStatusSurfaceTest`, which booted a full Spring
  context and then only grepped two files. The status cluster's behaviour remains covered by
  `RuntimeStatusJavascriptContractTest`, which drives it in a real browser.

## Scope of the claim

Passing this gate supports the all-in-one premise **for the two rehearsed campaign shapes**.
It is not a claim about every published campaign (spec §11.3, closing paragraph).

## DM-only scope

The player view, table presentation, and handout safety classification have been removed.
The app is DM-only. The former §11.1.5 (table-safe presets and the player projection),
§11.1.6 (asset safety classification and reviewed derivatives) and rehearsal step 8
(presenting a player-safe asset) were retired with the features they covered; their rows and
proof references were removed rather than left dangling. No player-facing endpoint exists.

The app ships with no authentication layer. It binds to loopback by default. Any change to
`server.address` or the introduction of an authentication layer is a security decision
requiring explicit sign-off.
