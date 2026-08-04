# DMHelper Overengineering Remediation

**Status:** Approved design

**Date:** 2026-08-04

**Scope:** The campaign import/export subsystem, the session cockpit layout engine, the
browser test tier, and test infrastructure resident in production code. Five staged
deletions totalling roughly 11,400 net lines.

## 1. Authority and supersession

This document authorises deliberate capability reductions. Where it conflicts with an
earlier specification, this document wins for the subsystems it names.

- It supersedes `2026-07-31-whole-product-ui-redesign-design.md` §1 on cockpit preset
  storage. That document deferred changes to preset persistence to "a later implementation
  plan identifying a separately approved functional change." This is that change.
- It supersedes the campaign-format compatibility commitment in
  `docs/product/format-compatibility.md`. Format v1 ceases to be readable or writable.
- The **visual design requirements** of the UI redesign specification remain in force as
  design intent. What changes is enforcement: they stop being asserted by automated browser
  gates and become the author's responsibility. No visual requirement is repealed.
- Functional, safety, persistence, package-v2, and runtime requirements in `SPEC.md` remain
  in force and unmodified.

## 2. Problem statement

DMHelper is a single-user, locally-run application (`SPEC.md` §1.2). Measured against that
usage model, effort is distributed in inverse proportion to value:

- The import/export subsystem is **14,224 lines of main code — 25% of the codebase** — plus
  ~13,700 lines of test, to implement "save a campaign to a file and load it back." Two
  complete serialization stacks are live simultaneously.
- The cockpit ships a **drag-and-drop, versioned, DB-persisted workspace layout engine** with
  optimistic locking and a repair resolver, for one user who uses four layouts.
- **30 Playwright classes** exist, of which roughly half assert appearance rather than
  behaviour, enforcing a style guide by machine for an audience of one.
- **671 lines of test-only infrastructure ship inside `src/main`**, scanned by Spring at
  every startup.

Meanwhile the code that actually runs at the table is under-modularised: `EncounterService`
is 2,367 lines and 135 members. The periphery is over-abstracted; the core is not abstracted
at all.

The problem is not that any of this was built. It is that it is still here.

## 3. Non-goals

Recorded explicitly so that later readers can see these were decisions, not oversights.

- **The package-key subsystem stays.** `CampaignPackageKey`, its repository, service, and
  SHA-256 generator remain. All 23 section adapters depend on it for cross-installation
  stable identity. Removing it is a redesign of package identity, not a simplification.
- **The god classes stay.** `EncounterService` (2,367 lines), `SheetService` (1,348),
  `LibraryController` (1,233), `CampaignService` after v1 removal (~520). Breaking these up
  is higher-value long-term work than anything in this document, but it modifies code that
  runs live at the table and carries materially different risk. Recorded here as known debt.
- **Package v2 itself is not touched**, beyond the export-surface trim in Stage 5.
- **No new abstraction is introduced anywhere.** Every stage is subtraction. If a stage
  appears to need a new abstraction to proceed, that is a signal the stage is wrong.

## 4. Stage 1 — Free wins

No behaviour change, no user-visible change, no risk to table operation.

### 4.1 Remove the dead websocket dependency

`spring-boot-starter-websocket` is declared at `pom.xml:80` and has **zero references** in
the entire repository. Verified: no `springframework.web.socket` import, no
`WebSocketHandler`, no `@EnableWebSocketMessageBroker`, no client-side `WebSocket` or
`EventSource`, and no HTMX polling fallback. Delete the dependency.

### 4.2 Move test infrastructure out of production code

These three classes live in `src/main` and are referenced by nothing but three test classes.
`CampaignSemanticComparator` throws `AssertionError` (`CampaignSemanticComparator.java:30`).

| Class | Lines |
|---|---|
| `campaign/packagev2/service/CampaignSemanticSnapshotService.java` | 404 |
| `campaign/packagev2/service/CampaignSemanticComparator.java` | 239 |
| `campaign/packagev2/service/CampaignSemanticSnapshot.java` | 28 |

Move all three to `src/test/java/dev/hendrikhoemberg/dmhelper/support/`. `SnapshotService`
is a Spring `@Service` consumed via injection in tests; it must remain injectable from the
test context after the move. Adjust its stereotype or register it as a test `@Component` as
required — this is the only part of Stage 1 with any mechanical subtlety.

**671 lines out of the production jar and out of component scanning.**

### 4.3 Delete the fixture meta-tests

`PackageShapeProfileExtractor` reads a campaign package from a hardcoded personal path
(`PackageShapeProfileExtractor.java:23` → `~/Documents/DnDCampaigns/lmop-de.dmcampaign`),
extracts a structural profile, commits it, and `FixtureShapeCoverageTest` asserts the test
fixtures cover the same shape. These are tests that test the fixtures of other tests.

The hardcoded path is **already stale** — the file on disk is `lmop-de3.dmcampaign`. This
machinery is unmaintained as well as unnecessary.

Delete `FixtureShapeCoverageTest` (266), `PackageShapeProfileExtractor` (197),
`PackageShapeProfile` (13), `PackageShapeProfileTest` (48), and the committed profile
resource. **524 lines.**

The fixtures themselves — `PopulatedCampaignFixture`, `ReleaseRehearsalFixture`,
`PreparationSurfaceFixture` — are used by real tests and stay.

## 5. Stage 2 — Excise campaign format v1

### 5.1 Justification

There are no v1 campaign files. The only `.dmcampaign.json` files in existence are test
fixtures and documentation examples; the sole real campaign on disk
(`~/Documents/DnDCampaigns/lmop-de3.dmcampaign`) is a v2 ZIP. Read support is being
maintained for a file type that does not exist.

The current pipeline also validates v1 content **twice**: `CampaignSemanticValidator` (909
lines) runs during migration, then `CampaignManifestV2SemanticValidator` (1,623 lines) runs
on the migrated manifest — `CampaignPackageValidationPipeline.java:51-72`.

### 5.2 Delete

| Path | Lines |
|---|---|
| `campaign/service/CampaignExportDto.java` | 371 |
| `campaign/service/validation/CampaignSemanticValidator.java` | 909 |
| `campaign/service/validation/CampaignSchemaValidator.java` | 60 |
| `campaign/service/validation/CampaignImportValidator.java` | 68 |
| `campaign/service/validation/CampaignCatalogResolver.java` | 76 |
| `campaign/service/validation/CampaignValidationResult.java` | 31 |
| `campaign/packagev2/migration/LegacyV1ToV2Migration.java` | 486 |
| `campaign/packagev2/migration/FormatMigrationRegistry.java` | 45 |
| `campaign/packagev2/validation/CampaignFormatMigration.java` | 10 |
| `CampaignService.exportToJson` / `importFromJson` / `importValidated` + helpers | ~790 |
| `resources/schemas/campaign-format.schema.json` | — |

Endpoints removed: `GET /campaigns/{id}/export` (`CampaignController.java:206`) and
`POST /campaigns/import` (`CampaignController.java:225`), plus the `DryRunResult` record.

UI removed: the entire `data-settings-group="data"` "Legacy data" section,
`templates/campaigns/settings.html:85-104`.

**Retained — these are shared with v2 and must not be deleted:**
`ImportProblemCodes` (164), `CampaignImportProblem` (13), `ImportSeverity` (5). Prune only
the problem codes that become unreachable once v1 is gone.

`CampaignService` drops from 1,306 to roughly 520 lines.

### 5.3 Pipeline simplification

`CampaignPackageValidationPipeline` loses its `v1Validator` constructor dependency and its
`ContainerKind.V1_JSON` branch. `StagedCampaignPackage.ContainerKind` loses that variant.
A `.dmcampaign.json` file declaring `formatVersion: 1` now fails with
`UNSUPPORTED_FORMAT_VERSION` — the existing code path for unmigratable input.

### 5.4 Tests and fixtures

Delete: `CampaignImportExportRoundTripTest` (908), `CampaignSemanticValidatorTest` (364),
`CampaignSchemaValidatorTest` (152), `CampaignDtoSchemaCompatibilityTest` (118),
`CampaignImportValidatorTest` (115). Prune the v1 portions of `CampaignServiceTest` (344)
and `CampaignControllerTest` (323).

Delete fixtures `src/test/resources/campaigns/v1/*` and the v1 `docs-examples/*` files, plus
any documentation-example test that consumes them.

**~2,900 lines of main and ~1,700 of test.**

### 5.5 Documentation

Delete `docs/campaign-format-v1.md`. Update `docs/product/format-compatibility.md` to a
single supported format. Prune v1 references from `docs/authoring/validation-errors.md`,
`docs/agent/`, and `docs/campaign-format-v2.md` (which currently advertises v1 acceptance).

## 6. Stage 3 — Reduce the cockpit layout engine to fixed presets

### 6.1 What the cockpit becomes

The four zones — `PRIMARY`, `LEFT_SUPPORT`, `RIGHT_SUPPORT`, `BOTTOM_UTILITY` — remain as
structure. A preset stops being a document that is resolved, validated, repaired and
persisted, and becomes **a CSS class on the cockpit root** plus a server-rendered
module-to-zone assignment.

Retained behaviour: the four built-in layouts (Exploration, Combat, Theatre of Mind, Session
Review), their digit-key shortcuts, and per-zone collapse. Preset choice and collapse state
persist in `localStorage`, as preset choice already does
(`cockpit-layout.js:2134,2152`).

Removed behaviour: drag-and-drop docking between zones, user-created named presets,
adjustable split ratios, and layout schema versioning with repair.

### 6.2 Delete

| Path | Lines |
|---|---|
| `session/layout/CockpitLayoutResolver.java` | 215 |
| `session/layout/CockpitLayoutValidator.java` | 115 |
| `session/layout/CockpitLayoutDocument.java` | 32 |
| `session/layout/CockpitLayoutCodec.java` | 29 |
| `session/layout/CockpitModuleStateContract.java` | 8 |
| `session/service/CockpitLayoutPresetService.java` | 200 |
| `session/web/CockpitLayoutApiController.java` | 65 |
| `session/data/CockpitLayoutPreset.java` | 59 |
| `session/data/CockpitLayoutPresetRepository.java` | 12 |

Plus their tests: `CockpitLayoutPresetServiceTest`, `CockpitLayoutApiControllerTest`,
`CockpitBuiltInPresetCatalogTest`, `CockpitLayoutResolverTest`, `CockpitLayoutValidatorTest`.

### 6.3 Rewrite

- `static/js/cockpit-layout.js`: **2,246 → ~200.** Apply preset class, toggle zone collapse,
  remember both in `localStorage`. Nothing else.
- `static/css/cockpit-layout.css`: **702 → ~250.** Four grid definitions and a collapse rule.
- `session/layout/CockpitBuiltInPresetCatalog.java` (80): simplified to the source of truth
  for the four preset keys and their module-to-zone assignments.
- `session/layout/CockpitModuleRegistry.java` (89) and `CockpitModuleDefinition.java` (27):
  reduced to "which modules exist, which zone each belongs to." The `allowedZones` set
  collapses to a single zone per module.

### 6.4 Untouched

Everything that renders module *content* never depended on layout being configurable and is
not modified: `CockpitRuntimeModuleViewService` (451), `CockpitRuntimeModuleController`
(157), `SessionLogModuleService` (167), `static/js/cockpit-modules.js` (460),
`static/js/session-cockpit.js` (1,281), `static/css/cockpit-modules.css` (773).

### 6.5 Database

Add `V30__drop_cockpit_layout_presets.sql`, dropping the table created by
`V22__add_cockpit_layout_presets.sql`.

**Any custom presets currently stored are destroyed by this migration.** This is accepted:
the application has one user, who has approved the removal. No export path is provided.

**Net ~3,233 lines of main and static** (3,683 removed against ~450 rewritten), plus ~400
lines of layout tests — ~3,700 in total.

## 7. Stage 4 — Consolidate the appearance gates

Sequenced deliberately after Stage 3: four of these gates assert properties of the layout
engine being removed, and would otherwise be rewritten twice.

### 7.1 Retain — behavioural browser tests

These assert that the application *works* and genuinely need a layout engine or real event
dispatch. All stay: `CoreSessionLoopSmokeTest`, `MapEditorBrowserTest`,
`TrackerIdentityBrowserTest`, `RuntimeStatusJavascriptContractTest`,
`FailureSignallingBrowserTest`, `SheetSaveBrowserTest`, `CockpitModuleInitialLoadBrowserTest`,
`CockpitMapTransitionBrowserTest`, `CockpitReferenceBrowserTest`,
`CockpitRuntimeMapPickerBrowserTest`, `EncounterDiscoverabilityBrowserTest`,
`DiceQuickRollBrowserTest`, `KeyboardOperationGateTest`, `OverlayBehaviorGateTest`,
`ReleaseRehearsalTest`.

### 7.2 Replace — one overflow gate

`ViewportAccessibilityGateTest` (480) is rewritten to roughly 250 lines as the single
retained appearance gate. It loads each major surface at the real viewport sizes in use and
asserts exactly two properties:

1. `document.body` has no horizontal overflow.
2. No content element is clipped below a legibility threshold.

That assertion set is what distinguishes a genuine table failure — a cockpit unusable at
1366px — from a stylistic preference.

### 7.3 Delete

`VisualReviewMatrixGateTest` (273), `SurfaceNestingGateTest` (210),
`VisualFoundationRenderGateTest` (204), `CockpitLaptopFitGateTest` (189),
`ViewportMatrixGateTest` (173), `CockpitModuleFitTest` (157), `ShellRenderGateTest` (148),
`OperationalPreparationRenderGateTest` (162), `TypographyRenderGateTest` (132),
`ReferenceWorkspaceRenderGateTest` (128), `NarrativePreparationRenderGateTest` (114),
`MapEditorRenderGateTest` (111), `PartyRailAlignmentBrowserTest` (96),
`CockpitBottomZoneSizeBrowserTest` (89). **2,186 lines.**

Also remove `target/ui-redesign/` screenshot output and its `.gitignore` entry if present.

### 7.4 Documentation

`docs/test-tiers.md` is updated: the browser tier drops from 30 classes to 16, and the
recorded wall-clock figures are re-measured rather than estimated.

**Net ~2,400 lines.**

## 8. Stage 5 — Trim the export surface and reconcile documentation

### 8.1 Drop dice-roll history from packages

`DiceSectionAdapter` (136) exists to round-trip a dice-roll *log*. A log is not campaign
content; it does not survive as anything a DM prepares or reuses. Remove the adapter, the
`DiceRollDto` from `CampaignManifestV2`, its schema entry, and its adapter test. The
`dice_roll` table and the in-app roller are unaffected.

This is a package-format change, and it must not break the existing package on disk.
`lmop-de3.dmcampaign` carries a `diceRolls` key (currently an empty array) at manifest top
level, so the v2 schema **must continue to accept the field**. It becomes
optional-and-ignored on import: the schema keeps the property, the importer discards it, and
the exporter stops writing it. **The manifest version is not bumped** — the format remains
v2, and packages written before and after this change import identically.

### 8.2 Retain session scene visits

`SessionSectionAdapter` round-trips `SessionSceneVisit` records. Unlike dice rolls, these
feed `SessionLogModuleService`, which the DM reads during and after a session. They are
campaign state, not a log. **No change.**

### 8.3 Reconcile documentation with reality

The repository carries 103 markdown files totalling 78,246 lines, of which
`docs/superpowers/` is 74,011 lines of historical process artifacts (45 plans, 15 specs, 5
verification records).

- **Historical plans and superseded specs are archived, not deleted** — they are the record
  of why the code looks as it does, and git history alone does not surface them. Move
  superseded material under `docs/superpowers/archive/` with a short index.
- **User-facing documentation is corrected, not thinned for its own sake.** After Stages 2–4,
  the DM manual, authoring guide, and agent SDK reference describe capabilities that no
  longer exist. Every statement about v1 import/export, custom cockpit presets, or
  drag-and-drop layout must be removed or rewritten. Documentation that describes a deleted
  feature is worse than no documentation.

## 9. Verification

Every stage ends with `./mvnw test -P gates` green and is committed independently. A stage
that cannot go green is not partially landed.

Additional per-stage verification:

| Stage | Additional check |
|---|---|
| 1 | Application starts; `CampaignSemanticSnapshotService` still injects in the test context. |
| 2 | Export and re-import `lmop-de3.dmcampaign` (v2 ZIP) with no semantic drift. A v1 JSON file is rejected with `UNSUPPORTED_FORMAT_VERSION`, not a stack trace. |
| 3 | Manual cockpit pass at 1366px and at the primary table display: all four presets, digit shortcuts, zone collapse, and every module renders in its assigned zone. |
| 4 | Re-measure and record both tier wall-clock times. |
| 5 | Export/import round-trip again; confirm the session log still populates from scene visits. |

Stage 3 carries verification that automated tests cannot fully provide. The manual pass is
part of the stage, not optional follow-up.

## 10. Expected outcome

Net lines removed per stage, counting all trees (`src/main/java`, `src/main/resources/static`,
`src/test/java`):

| Stage | Java main | Static | Test | Net |
|---|---|---|---|---|
| 1 — Free wins | −671 (relocated) | — | −524, +671 | ~−525 |
| 2 — v1 excision | −2,846 | — | −1,700 | ~−4,550 |
| 3 — Layout engine | −785 | −2,498 | −400 | ~−3,700 |
| 4 — Appearance gates | — | — | −2,416 | ~−2,400 |
| 5 — Export trim | −160 | — | −100 | ~−260 |
| **Total** | **−4,462** | **−2,498** | **−4,469** | **~−11,400** |

Stage 1's 671 lines are relocated from `src/main` to `src/test`, not deleted — they leave the
production jar and component scanning but remain in the repository.

Resulting totals:

| Tree | Before | After |
|---|---|---|
| `src/main/java` | 56,573 | ~52,100 |
| `src/main/resources/static` | 21,932 | ~19,400 |
| `src/test/java` | 68,854 | ~64,400 |

The import/export subsystem falls from 14,224 lines (25% of main) to ~10,550 (~20%). The
browser tier falls from 30 classes to 16.

All line counts in this document are measured, not estimated, except those marked `~`, which
are projections for code being rewritten rather than deleted.

No capability that survives is degraded. Four capabilities are deliberately withdrawn:
v1 campaign import, v1 campaign export, custom cockpit layout presets, and automated
enforcement of visual style requirements.
