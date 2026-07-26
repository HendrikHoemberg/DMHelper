# D — Surface Separation Verification Record

**Date:** 2026-07-24 (recording 2026-07-26)
**Decision:** PENDING

## Surface map

| Route | Surface mode | Notes |
|-------|-------------|-------|
| `/campaigns/{id}` | read | Dashboard with session readiness and run entry points |
| `/campaigns/{id}/adventures/{aid}` | read | Adventure detail with chapter list |
| `/campaigns/{id}/adventures/{aid}/scenes/{sid}` | read | Scene detail with rail and body |
| `/campaigns/{id}/encounters/{eid}` | read | Encounter detail with prep summary |
| `/campaigns/{id}/party` | read | Party roster with bulk actions |
| `/campaigns/{id}/session` | run | Cockpit with runtime modules |
| `/campaigns/{id}/adventures/{aid}/scenes/{sid}/structure` | edit | Scene structure editor |
| `/campaigns/{id}/encounters/{eid}/setup` | edit | Encounter setup (combatants, prep, rewards) |
| `/campaigns/{id}/settings` | admin | Campaign settings (edit, export, import, delete) |

## htmx swap map for the split fragments

Splitting one rail into a Read fragment and an Edit fragment means every control must swap
into a container the page it lives on actually has:

| Control (lives in) | Target | Handler returns |
|---|---|---|
| Seed encounter (`_scene-rail.html`) | `#sceneRail` | `_scene-rail :: sceneRail` |
| Scene status (`_scene-rail.html`) | `#sceneStatusBadge` | `_scene-rail :: statusBadge` |
| Title/body/metadata (`_scene-form.html`) | `#sceneBody` | `_scene-body :: sceneBody` |
| Map, encounter, statblock, handout links (`_scene-form.html`) | `#sceneEditPane` | `_scene-form :: form` |
| Structured CRUD (`_scene-structure-editor.html`) | `#sceneStructureEditor` | `_scene-structure-editor :: structureEditor` |
| Add member (`party/_form.html`) | `#party-roster` | `party/_roster :: rowWithSummary` |

## Automated coverage

| Test | What it guards |
|------|---------------|
| `SurfaceModeContractTest` | Every governed page declares exactly one `data-surface` on its root `<main>` |
| `SurfaceSeparationContractTest.readAndRunSurfacesEmbedNoEditOrAdminTooling` | No `/package`, `/export`, import, reordering, `name="sourceLocator"`, `data-structured-metadata` in read/run pages or their owned fragments |
| `SurfaceSeparationContractTest.readAndRunSurfacesCarryNoDestructiveActionInTheirHeader` | No `btn-danger` in `page-header-actions` of read/run pages |
| `SurfaceSeparationContractTest.theCockpitContainsRuntimeModulesOnly` | No sourceLocator, /package, or direction reordering in session/module templates |
| `SurfaceSeparationContractTest.everyEditAndAdminSurfaceIsReachableFromItsReadSurface` | campaign/detail → /settings, scene-rail → /structure, encounter/detail → /setup |
| `FullPageRenderSmokeTest` | All governed routes (including new /structure, /setup, /settings, /party from prep fixture) render HTML that ends with `</html>` |
| `PreparationSurfaceFixture` | Seeds a campaign with party members and a fully-prepped encounter for prep-surface tests |
| `SceneStructureEditSurfaceTest.formControlsSwapIntoAContainerThePageHas` | The edit page owns every id its controls target, and unlinking a map returns the form rather than the read rail |
| `PartyRosterTest.newMembersAreAppendedInsideTheRosterNotAfterIt` | A member added by htmx lands inside `#party-roster` |
| `PartyRosterTest.aDeepLinkedRowOpensItself` | `/party#pm-card-<id>` opens the collapsed row it points at |
| `EncounterPrepSummaryTest.anUnresolvedInitiativeSaysSoRatherThanRenderingBlank` | Unset initiative reads as "not set" (spec section 6.2) |

## Manual acceptance checklist

- [ ] Campaign dashboard shows read-only data, no package export/import buttons
- [ ] Adventure detail shows chapter list but no reorder controls or chapter/scene CRUD forms
- [ ] Scene detail shows rail with structured content but no "add section" or "add check" inline forms
- [ ] Encounter detail shows prep summary and combatant table but no waves/combatant add forms or delete buttons
- [ ] Party list shows roster with bulk actions (rest, xp, loot, condition) but no danger-zone delete-all
- [ ] Session cockpit shows runtime modules only; no package/import/source-editing affordances
- [ ] Scene structure (edit) page sits behind a link on the scene rail
- [ ] Encounter setup (edit) page sits behind a link on the encounter detail page
- [ ] Campaign settings (admin) page sits behind a link on the campaign dashboard
- [ ] Every edit/admin page data-surface declaration is correct

## What is not covered by D

- **Player projection** (surface mode "player") is not governed by D — it has its own contract suite.
- **Library detail pages** (`/library/*`) are screen-safe reading and not part of the campaign-surface model.
- **Edit forms rendered inline via htmx** on read surfaces (e.g. the "Edit" button on adventure detail that swaps in a form) are not checked — they are temporary overlays, not navigable pages.
- **CSS-level hiding** of edit tooling (e.g. `data-layout-edit-only`) is not enforced by the contract test. A separate audit must verify that JS-disabled browsers do not see *visible* edit controls in read/run surfaces (though the contract test does catch the most dangerous patterns like DELETE buttons and reordering).
