# All-in-One Release Gate

The executable gate defined in
`docs/superpowers/specs/2026-07-22-phandelver-all-in-one-corrective-design.md` §11.
Every row names the test that proves it. `ReleaseGateIndexContractTest` fails if a named
class disappears, so this table cannot drift away from the suite.

Run the core browser gate (the indexed §11.1 proofs and release-index contract are also run by the full gate):

    ./mvnw -q test -Dtest='ReleaseRehearsalTest,ViewportAccessibilityGateTest,TypographyRenderGateTest,SurfaceNestingGateTest,CoreSessionLoopSmokeTest'

Run the full release gate; this command must pass before the all-in-one premise may be claimed:

    ./mvnw -q verify

## §11.1 Automated coverage

| Req | Requirement | Proved by |
|---|---|---|
| 11.1.1 | Production-parity integration; `open-in-view=false`; fails on lazy access after a service boundary | `ReadinessProductionParityTest`, `FullPageRenderSmokeTest` |
| 11.1.2 | Every cockpit module renders empty, populated, loading, error, compact, focused, Private and Table-safe | `CockpitRuntimeModuleContractTest`, `RuntimeModuleSafetyContractTest` |
| 11.1.3 | Layout schema migration, constraints, docking, serialization, invalid recovery, preset reset | `CockpitLayoutPresetServiceTest`, `CockpitWorkbenchTemplateContractTest` |
| 11.1.4 | Browser interaction: edit lock, dividers, docking, tabs, focus, keyboard, persistence, retry, resize | `CoreSessionLoopSmokeTest`, `ViewportAccessibilityGateTest` |
| 11.1.5 | Every built-in preset in Table-safe: sensitive content neither visible nor focusable; player endpoint carries only its projection | `SessionCockpitSecurityTest`, `PlayerViewSecurityContractTest`, `ReleaseRehearsalTest` |
| 11.1.6 | Unsafe assets blocked; a reviewed derivative matches the player preview | `HandoutDerivativeTemplateContractTest`, `ReleaseRehearsalTest` |
| 11.1.7 | Tracker-driven defeat/revive and a cross-midnight session produce a faithful log | `SessionEncounterEvidenceIntegrationTest`, `ReleaseRehearsalTest` |
| 11.1.8 | No console errors, unhandled rejections, malformed requests or silent non-2xx actions | `BrowserFailureCollector` attached in `ReleaseRehearsalTest`, `ViewportAccessibilityGateTest`, `CoreSessionLoopSmokeTest` |

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

All ten steps, run against two synthetic campaign shapes, in `ReleaseRehearsalTest`
(`Linear` and `Branched` nested classes). The seven gate-fail conditions are asserted at the
point each becomes observable; see the method comments.

The fixtures are `ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP` and `BRANCHED_TWO_MAPS`.
Both are entirely synthetic — `ReleaseRehearsalFixtureTest` fails the build if any published
campaign's vocabulary appears in one.

## §10 Visual system

The visual rules are enforced continuously rather than at gate time:
`DesignTokenContractTest`, `TypeScaleContractTest`, `TypographyRoleContractTest`,
`TypographyRenderGateTest`, `CombatLegibilityContractTest`, `GoldAccentContractTest`,
`SurfaceNestingGateTest`, `ElevationModelContractTest`, `ControlConsistencyContractTest`,
`DestructiveActionContractTest`, `MotionBudgetContractTest`, `RuntimeStatusSurfaceTest`.

## Scope of the claim

Passing this gate supports the all-in-one premise **for the two rehearsed campaign shapes**.
It is not a claim about every published campaign (spec §11.3, closing paragraph).
