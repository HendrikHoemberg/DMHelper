# All-in-One Release Gate

The executable gate defined in
`docs/superpowers/specs/2026-07-22-phandelver-all-in-one-corrective-design.md` §11.
Every requirement row names an executable proof. `ReleaseGateIndexContractTest` parses the
§11.1 and §11.2 tables, requires each row to name at least one proof, and resolves every
backticked `*Test`, `*Test#method`, or nested `*Test$Nested#method` reference to
`src/test/java` and the exact declaring class scope.
The same reference check covers the representative §11.3 rehearsal claim, so the index cannot
silently drift away from the suite.

Run the core browser gate (the indexed §11.1 proofs and release-index contract are also run by the full gate):

    ./mvnw -q test -Dtest='ReleaseRehearsalTest,ViewportAccessibilityGateTest,TypographyRenderGateTest,SurfaceNestingGateTest,CoreSessionLoopSmokeTest'

Run the full release gate; this command must pass before the all-in-one premise may be claimed:

    ./mvnw -q verify

## §11.1 Automated coverage

| Req | Requirement | Proved by |
|---|---|---|
| 11.1.1 | Production-parity integration; `open-in-view=false`; fails on lazy access after a service boundary | `ReadinessProductionParityTest`, `FullPageRenderSmokeTest` |
| 11.1.2 | Every cockpit module renders empty, populated, loading, error, compact and focused | `CockpitRuntimeModuleContractTest`, `RuntimeModuleShellContractTest` |
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
`DestructiveActionContractTest`, `MotionBudgetContractTest`, `RuntimeStatusSurfaceTest`.

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
