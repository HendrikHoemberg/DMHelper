# DM-Only Cockpit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every player-facing surface from DMHelper and fix the defects found in the 2026-07-28 live-session evaluation, so a DM can prepare and run a session end-to-end on one screen without the app failing under them.

**Architecture:** Three moves, in order. First delete the player view, the presentation pipeline, handout safety classification, table-safe mode and the LAN PIN/QR gate — this removes ~1,800 lines and eliminates six evaluation findings outright, so later work touches a smaller codebase. Second, fix the five blockers that stop a session being run at all. Third, close the coherence gaps between cockpit modules and the presentation-layer defects. Deletion is first so no fix lands in code that is about to be removed.

**Tech Stack:** Spring Boot 4.1 / Java 25, Spring Data JPA + H2 (file), Flyway migrations, Thymeleaf + htmx, Alpine.js + vanilla ES modules (all vendored, no build step), Konva.js for the map, JUnit 5 + MockMvc.

## Global Constraints

- `spring.jpa.hibernate.ddl-auto=validate` — **any entity field change requires a matching Flyway migration in the same task**, or the app will not start. Next free version is `V28`.
- Flyway migrations are append-only. Never edit `V1`–`V27`.
- No JS build step. All client code is vendored ES modules or inline Alpine components. Do not add npm dependencies to the runtime.
- `CampaignImportValidator` and `CampaignService` both enable `FAIL_ON_UNKNOWN_PROPERTIES`, and `campaign-format-v2.schema.json` sets `"additionalProperties": false` at every level. **Removing a field from the package DTO breaks import of every existing `.dmcampaign` package that carries it.** Deletions in Phase 1 must keep the import path tolerant (accept-and-discard) while stopping export from writing the field.
- `CampaignManifestV2.HandoutDto` (line 273) carries **three** fields this plan retires: `safetyClassification`, `derivativeRecipe` and `sourceRef`. All three must stay on the record as accept-and-discard. It is annotated `@JsonInclude(NON_NULL)`, so a component left permanently `null` already disappears from export — no annotation strictly required, but be explicit anyway. It also has a 10-arg legacy convenience constructor (lines 286-290) that must keep compiling.
- Test command is `./mvnw test`. A single class is `./mvnw test -Dtest=ClassName`. The suite has 343 test classes; a full run is slow — run targeted classes during a task, full suite before the phase-closing commit.
- **Before deleting any symbol, grep `src/test` as well as `src/main`.** Several tasks below were originally scoped from a `src/main` grep alone and under-counted their blast radius by 2-3x. Each deletion task now states its real file count; if your grep returns more, trust the grep.
- Thymeleaf fragment rule: a `<script>` that registers an Alpine component must live **inside** the element carrying `th:fragment`, or it is never emitted. This caused blocker #2 and must not be reintroduced.
- `window.cockpitLayout` is the layout controller. Its real API is `selectTab(zone, key)` (`cockpit-layout.js:817`), `focusModule(key, returnEl)` (`:1440`), `isModuleVisible(key)` (`:1607`), `moduleMode(key)` (`:1630`). There is **no** `cockpit:focus-module` event and **no** `data-cockpit-root` attribute — do not write code against either.
- Commit after every task. Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`).

---

## File Structure

### Deleted in Phase 1

| Path | Responsibility being removed |
|---|---|
| `src/main/java/.../live/` (8 classes, 749 LOC) | Player projection, table presentation state, table WebSocket |
| `src/test/java/.../live/` (6 classes + `web/`) | `HandoutPreviewParityTest`, `PlayerSafeMapProjectionTest`, `PlayerSafeProjectionServiceTest`, `PlayerViewSecurityContractTest`, `TablePresentationServiceTest`, `TableStateWebSocketHandlerTest`, `web/CampaignTableControllerTest` |
| `src/test/java/.../adventure/web/PlayerSafeProjectionTest.java` | Player-safe map projection contract |
| `src/main/resources/templates/player/view.html` | Player screen |
| `src/main/resources/templates/session/_presentation-preview.html` | Preview + emergency-override modal |
| `src/main/resources/templates/session/modules/_presentation.html` | Presentation cockpit module |
| `src/main/resources/static/css/player-projection.css` | Player screen styling |
| `src/main/resources/static/js/screen-safety.js` | Table-safe toggle behaviour |
| `src/main/resources/static/js/player/` | Player screen client |
| `src/main/java/.../session/layout/CockpitScreenSafetyBehavior.java` | `FILTER`/`HIDE`/`PLAYER_PROJECTION` per-module policy |
| `src/main/java/.../common/config/WebSocketConfig.java` | Only registers the table handler |
| `src/main/java/.../common/config/QrCodeController.java` | Player-view QR |

### Modified in Phase 1

| Path | Change |
|---|---|
| `session/layout/CockpitModuleRegistry.java:51` | Drop `presentation` module; drop `screenSafety` from `CockpitModuleDefinition` |
| `session/layout/CockpitBuiltInPresetCatalog.java:24-26` | Drop `builtin:presentation` preset |
| `session/layout/CockpitModuleDefinition.java` | Drop `screenSafety` component |
| `session/runtime/CockpitRuntimeModuleViewService.java:70,112,276,379-392` | Drop `PresentationView` record + `presentation()` method + `presentationMode` on the runtime view |
| `session/service/SessionDraftService.java:113` | Drop "Presentation Safety Overrides" section |
| `handout/data/Handout.java:15-21` | Drop `SafetyClassification` enum + field |
| `handout/**` (`HandoutService`, `HandoutController`, `HandoutApiController`, `HandoutViewDto`, `HandoutSectionAdapter`) | Drop classification read/write, derivative creation |
| `campaign/readiness/` (`CampaignReadinessService:47-55`, `ReadinessInputs:40`, `ReadinessInputsAssembler:49`, `PreviewReadinessAssembler:89-94`) | Drop the "linked for presentation but not player-safe" rule |
| `templates/handout/_card.html:29-70` | Card actions reduce to View + Delete |
| `templates/fragments/navbar.html`, `templates/session/cockpit.html` | Drop safety toggle, Player View link, QR, PIN chip |
| `src/main/resources/db/migration/V28__drop_handout_safety_classification.sql` | **Create** — drop column + constraints from `V18`/`V19` |

**Not touched, deliberately.** `Session.presentationMode` (read at `CockpitRuntimeModuleViewService:276,390`) stays as an entity field and a live column. Dropping it would need its own migration and buys nothing once nothing reads it; leaving both halves in place keeps `ddl-auto=validate` happy. Stop *reading* it in Task 1.2; leave the field and column alone.

### Created or modified in Phases 2–4

| Path | Change |
|---|---|
| `templates/sheet/create.html:64` | Fix SpEL list literal (blocker #1) |
| `templates/encounter/_library-add.html`, `_threat-add.html` | Move `<script>` inside fragment; fix `data-encounter-id`; drop server/client `results` collision (blocker #2) |
| `static/js/cockpit-modules.js:148-150` | Fix hydration gate so modules load when first painted (blocker #3) |
| `session/layout/CockpitBuiltInPresetCatalog.java` | Add `reference` to the Combat preset (blocker #4) |
| `static/js/cockpit-layout.js` | Add `revealModule(key)` — the missing "bring this module into view" primitive (blocker #4) |
| `static/js/cockpit-reference.js` | Add `showStatblock(id)` + a detail pane; today `selectItem` only does `window.open` (blocker #4) |
| `static/js/command-palette.js:97` | Route `type === 'statblock'` hits into the Reference module when in the cockpit (blocker #4) |
| `templates/encounter/_combatant-list.html` | **Create** — the missing combatant list (blocker #5) |
| `templates/session/_story-rail.html` | "Run this encounter" for scenes that already have one |
| `static/js/map/battle-map.js:220-235`, `templates/session/_map-module.html:43` | Consume HP from `tracker-encounter-state` |
| `campaign/readiness/ReadinessInputs.java`, `ReadinessInputsAssembler.java`, `CampaignReadinessService.java`, `templates/campaigns/_readiness.html` | Party-count blocker (note: template dir is `campaigns/`, plural) |
| `static/css/components.css`, `surfaces.css`, `base.css` | `.data-table`, `.scene-row__title`, `.card-actions`, `.roster-hp-fill` |

---

## Phase 0 — Regression net

The deletion crosses the campaign package contract. Pin that behaviour before touching it.

### Task 0.1: Pin package import tolerance for legacy classification

**Files:**
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/LegacyHandoutSafetyToleranceTest.java` (create)
- Fixture: **none — reuse existing ones.**

**Interfaces:**
- Consumes: existing `CampaignManifestV2` deserialisation path, `CampaignImportValidator`, and the `CampaignService` import path.
- Produces: `LegacyHandoutSafetyToleranceTest` — the contract that Task 1.4 must keep green.

> **Do not author a new fixture.** The original plan said to `cp -r .../minimal.dmcampaign` — but `minimal.dmcampaign` is a **file** (`minimal.dmcampaign.json`), not a directory, so the copy fails and there is no `manifest.json` to edit. Three existing fixtures already carry `safetyClassification` on their handouts: `current-surface.dmcampaign`, `feature-complete.dmcampaign`, `published-adventure-shaped.dmcampaign`. Pin against those.

- [ ] **Step 1: Confirm the baseline fixtures still carry the field**

```bash
grep -l safetyClassification src/test/resources/campaigns/v2/*/manifest.json
```

Expected: `current-surface`, `feature-complete`, `published-adventure-shaped`. The real handout shape there is `key / title / tags / assetRef / contentType / dmOnly / presented / safetyClassification` — matching `CampaignManifestV2.HandoutDto` (line 273), which *also* has `sourceRef`, `derivativeRecipe` and `assetKind`.

- [ ] **Step 2: Write the guard test — both paths, all three retiring fields**

The Global Constraints note that `CampaignImportValidator` **and** `CampaignService` each enable `FAIL_ON_UNKNOWN_PROPERTIES`. A test that only exercises the validator does not protect the path that actually imports a package, which is the likelier breakage. Cover both, and cover `derivativeRecipe`/`sourceRef` as well as `safetyClassification` — Task 1.4 retires all three.

```java
package dev.hendrikhoemberg.dmhelper.campaign.packagev2;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class LegacyHandoutSafetyToleranceTest {

    private static final Path FIXTURE = Path.of(
            "src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json");

    @Autowired
    private dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void theFixtureStillExercisesTheRetiredFields() throws Exception {
        assertThat(Files.readString(FIXTURE)).contains("safetyClassification");
    }

    @Test
    void schemaValidationAcceptsPackagesThatStillCarrySafetyClassification() throws Exception {
        String json = Files.readString(FIXTURE);

        assertThatCode(() -> validator.validate(json))
                .as("packages authored before the DM-only cut must still validate")
                .doesNotThrowAnyException();
    }

    @Test
    void deserialisationAcceptsAllThreeRetiredHandoutFields() throws Exception {
        String json = """
                {"key":"legacy","title":"Karte","tags":[],"assetRef":"a1",
                 "contentType":"image/png","dmOnly":true,"presented":false,
                 "safetyClassification":"DM_SOURCE","derivativeRecipe":"{}"}
                """;

        assertThatCode(() -> objectMapper.readValue(json, CampaignManifestV2.HandoutDto.class))
                .as("FAIL_ON_UNKNOWN_PROPERTIES must not reject retired fields")
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 3: Run it and confirm all three pass today**

Run: `./mvnw test -Dtest=LegacyHandoutSafetyToleranceTest`
Expected: PASS. If `CampaignImportValidator.validate` has a different signature, or if the injected `ObjectMapper` is not the one configured with `FAIL_ON_UNKNOWN_PROPERTIES`, adapt the wiring to the real one — do not change the assertions' intent. Getting the *right* mapper matters: a lenient mapper makes this test vacuous.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/LegacyHandoutSafetyToleranceTest.java
git commit -m "test: pin package import tolerance for legacy handout classification"
```

### Task 0.2: Pin the cockpit's post-deletion module set

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistryTest.java`

- [ ] **Step 1: Add a test asserting the intended final module set**

```java
@Test
void registryExposesOnlyDmFacingModules() {
    var keys = registry.all().stream().map(CockpitModuleDefinition::key).toList();

    assertThat(keys).containsExactlyInAnyOrder(
            "story", "map", "encounter", "party",
            "quick-notes", "session-plan", "session-log", "audio", "reference");
    assertThat(keys).doesNotContain("presentation");
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./mvnw test -Dtest=CockpitModuleRegistryTest`
Expected: FAIL — `presentation` is still registered. Adjust the expected list to match the registry's real keys minus `presentation` if any name differs; the `doesNotContain("presentation")` assertion is the load-bearing one.

- [ ] **Step 3: Commit the failing test**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitModuleRegistryTest.java
git commit -m "test: assert cockpit registry drops the presentation module"
```

---

## Phase 1 — Deletion

Kills evaluation findings #6 (map presentation spoiled the dungeon), #7 (table status lied), #8 (broken safety switch), #9 (orphaned "Present anyway…"), #10 (ungated Present), and the handout `.card-actions` overflow.

### Task 1.1: Remove the player view and live module

**Files:**
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/live/` (all 8 classes)
- Delete: `src/main/resources/templates/player/view.html`
- Delete: `src/main/resources/static/css/player-projection.css`
- Delete: `src/main/resources/static/js/player/`
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebSocketConfig.java`
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/live/` — **the whole directory, 7 test classes**, not just `TableStateWebSocketHandlerTest`: `HandoutPreviewParityTest`, `PlayerSafeMapProjectionTest`, `PlayerSafeProjectionServiceTest`, `PlayerViewSecurityContractTest`, `TablePresentationServiceTest`, `TableStateWebSocketHandlerTest`, `web/CampaignTableControllerTest`
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/PlayerSafeProjectionTest.java`
- Modify: `src/main/resources/templates/fragments/navbar.html` (Player View link)

**Interfaces:**
- Consumes: nothing.
- Produces: removal of `/player`, `/campaigns/{id}/table/**`, and the `/ws/table` endpoint. Later tasks must not reference `LiveTableState`, `TablePresentationService`, or `PlayerSafeProjectionService`.

- [ ] **Step 1: Delete the module and its clients**

```bash
git rm -r src/main/java/dev/hendrikhoemberg/dmhelper/live
git rm -r src/main/resources/templates/player
git rm -r src/main/resources/static/js/player
git rm src/main/resources/static/css/player-projection.css
git rm src/main/java/dev/hendrikhoemberg/dmhelper/common/config/WebSocketConfig.java
git rm -r src/test/java/dev/hendrikhoemberg/dmhelper/live
git rm src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/PlayerSafeProjectionTest.java
```

- [ ] **Step 2: Remove the Player View link from the navbar**

In `src/main/resources/templates/fragments/navbar.html`, delete the anchor at line ~21 (`title="Open player view in a new tab"`) and the surrounding list item.

- [ ] **Step 3: Find every remaining reference**

Run: `grep -rn "live\.\|PlayerView\|LiveTableState\|player-projection\|/ws/table" src/main src/test --include="*.java" --include="*.html" --include="*.js" --include="*.css"`
Expected: only hits inside files scheduled for deletion in Tasks 1.2–1.5. Delete any stray import or link you find here.

- [ ] **Step 4: Compile**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS. Every failure names a caller you still need to strip — fix them, do not re-add the deleted class.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: remove player view and live table projection"
```

### Task 1.2: Remove the Presentation module and preset

**Files:**
- Delete: `src/main/resources/templates/session/modules/_presentation.html`
- Delete: `src/main/resources/templates/session/_presentation-preview.html`
- Modify: `src/main/java/.../session/layout/CockpitModuleRegistry.java:51`
- Modify: `src/main/java/.../session/layout/CockpitBuiltInPresetCatalog.java:24-26`
- Modify: `src/main/java/.../session/runtime/CockpitRuntimeModuleViewService.java` — drop the `PresentationView` record (`:112`), the `presentation(UUID)` method (`:379-392`), and the `presentationMode` component of the runtime view record (`:70`, populated at `:276`). **Leave `Session.presentationMode` itself alone** (entity field and column both stay — see File Structure).
- Test: `src/test/java/.../session/layout/CockpitModuleRegistryTest.java` (from Task 0.2)
- Test: `src/test/java/.../session/service/CockpitLayoutPresetServiceTest.java` — note the package is `session/service/`, **not** `session/layout/`

**Interfaces:**
- Consumes: `CockpitModuleRegistryTest.registryExposesOnlyDmFacingModules` from Task 0.2.
- Produces: `builtin:presentation` no longer resolvable. `CockpitLayoutResolver` must fall back for saved layouts that name it — Step 3.

- [ ] **Step 1: Delete the templates and registry entry**

```bash
git rm src/main/resources/templates/session/modules/_presentation.html
git rm src/main/resources/templates/session/_presentation-preview.html
```

Then delete the `endpoint("presentation", "Presentation", 280, 220, …)` entry at `CockpitModuleRegistry.java:51` and the whole `preset("builtin:presentation", "Presentation", …)` block at `CockpitBuiltInPresetCatalog.java:24-26` (it spans three lines: the `preset(...)` call, its `zone(...)` line, and its `Set.of("story")` line).

- [ ] **Step 2: Write the failing test for stale saved layouts**

A DM who last used the Presentation preset has `builtin:presentation` persisted. Add to `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/CockpitLayoutPresetServiceTest.java` (**`session/service/`**, not `session/layout/`):

```java
@Test
void unknownPresetFallsBackToExplorationInsteadOfFailing() {
    var resolved = resolver.resolve("builtin:presentation", CockpitZone.values());

    assertThat(resolved).isNotNull();
    assertThat(resolved.presetId()).isEqualTo("builtin:exploration");
}
```

- [ ] **Step 3: Run it to see how it fails**

Run: `./mvnw test -Dtest=CockpitLayoutPresetServiceTest`
Expected: FAIL — either a thrown exception or a null layout. Adapt the call to `CockpitLayoutResolver`'s real signature; the behaviour under test is "unknown preset id degrades to Exploration, never throws".

- [ ] **Step 4: Implement the fallback**

In `CockpitLayoutResolver`, when the requested preset id is not found in the catalog, log at `debug` and return the `builtin:exploration` preset rather than propagating.

- [ ] **Step 5: Run both tests**

Run: `./mvnw test -Dtest=CockpitLayoutPresetServiceTest+CockpitModuleRegistryTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove presentation cockpit module and preset"
```

### Task 1.3: Remove table-safe mode

**Files:**
- Delete: `src/main/resources/static/js/screen-safety.js`
- Delete: `src/main/java/.../session/layout/CockpitScreenSafetyBehavior.java`
- Modify: `src/main/java/.../session/layout/CockpitModuleDefinition.java` (drop `screenSafety` component)
- Modify: `src/main/java/.../session/layout/CockpitModuleRegistry.java` (drop the argument at each `endpoint(...)` call)
- Modify: `src/main/resources/templates/session/cockpit.html`, `fragments/navbar.html` (drop `.switch-control` block)
- Modify: `src/main/resources/templates/session/_cockpit-module-shell.html` (drop `data-table-safe-behavior`)
- Modify: `src/main/resources/static/js/cockpit-modules.js`, `cockpit-layout.js` (drop screen-safety branches)
- Modify: `src/main/resources/templates/session/_map-module.html` (drop `data-screen-sensitive`)

**Interfaces:**
- Consumes: nothing.
- Produces: `tableSafe` no longer exists as an Alpine state key. Task 3.3 edits the same map-module Alpine component and must not reintroduce it.

- [ ] **Step 1: Delete the behaviour enum and client script**

```bash
git rm src/main/resources/static/js/screen-safety.js
git rm src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitScreenSafetyBehavior.java
```

- [ ] **Step 2: Strip the `screenSafety` component from module definitions**

In `CockpitModuleDefinition.java` remove the `CockpitScreenSafetyBehavior screenSafety` record component. In `CockpitModuleRegistry.java` remove the corresponding argument from every `endpoint(...)` call.

- [ ] **Step 3: Remove the toggle from both headers**

Delete the `<label class="switch-control" title="Screen Safety toggles table-safe projection">` block and its `<input>`, `.switch-track` and `.switch-label` children from `templates/fragments/navbar.html` and `templates/session/cockpit.html`. Then delete the now-unused `.switch-control`, `.switch-track`, `.switch-label` rules at `src/main/resources/static/css/base.css:409-440` — this is the CSS that produced finding #8.

- [ ] **Step 4: Strip `tableSafe` from templates and client code**

Run: `grep -rn "tableSafe\|table-safe\|TABLE_SAFE\|data-screen-sensitive\|screen-safety\|screenSafety" src/main src/test`
For each hit: an `x-show="!tableSafe"` becomes unconditional (delete the attribute, keep the element); an `x-show="tableSafe"` element is deleted entirely; `data-table-safe-behavior` attributes are deleted. Expected final grep output: empty.

Two specific hits worth naming because they are easy to miss:

- `cockpit-modules.js:118-121` — inside `isModuleVisible`, the `document.body?.dataset?.screenSafety === 'TABLE_SAFE'` branch that reads `data-table-safe-behavior`. Delete the whole `if` block. **Task 2.3 rewrites this same method** — do this one first so 2.3 starts from clean code.
- `_map-module.html:40` — `data-screen-sensitive` on the `<aside class="battle-sidebar">`. Delete the attribute, keep the element. **Task 3.3 edits this same file.**

- [ ] **Step 5: Compile and run the cockpit tests**

Run: `./mvnw test -Dtest='Cockpit*Test'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove table-safe screen mode"
```

### Task 1.4: Remove handout safety classification

This is the only task that changes the schema. `ddl-auto=validate` means the migration and the entity change must land together.

**Files:**
- Create: `src/main/resources/db/migration/V28__drop_handout_safety_classification.sql`
- Modify: `src/main/java/.../handout/data/Handout.java:15-21` (drop enum + field)
- Delete: `src/main/java/.../handout/data/DerivativeRecipe.java`
- Delete: `src/main/resources/static/js/handout-derivative-editor.js`
- Modify: `handout/service/HandoutService.java`, `web/HandoutController.java`, `web/HandoutApiController.java`, `web/HandoutViewDto.java`, `packagev2/HandoutSectionAdapter.java`
- Modify: `campaign/readiness/CampaignReadinessService.java:47-55`, `PreviewReadinessAssembler.java:89-94`, `ReadinessInputs.java:40`, `ReadinessInputsAssembler.java:49`
- Modify: `templates/handout/_card.html:29-70`
- Test: `src/test/java/.../campaign/packagev2/LegacyHandoutSafetyToleranceTest.java` (must stay green)

**Test files that reference the field and are NOT in the list above.** The original scoping missed these; `grep -rln "safetyClassification\|SafetyClassification\|safety_classification" src/main src/test` returns 37 files. Budget for all of them:

| File | Likely action |
|---|---|
| `common/config/FlywayMigrationTest.java` | Update — asserts on schema shape, will break on V28 |
| `common/config/FlywayLegacyUpgradeTest.java` | Update — same |
| `handout/data/HandoutSafetyPersistenceTest.java` | **Delete** — covers only the removed field |
| `handout/service/HandoutDerivativeServiceTest.java` | **Delete** — covers only the removed derivative feature |
| `handout/service/HandoutServiceTest.java` | Update — strip classification assertions |
| `handout/web/HandoutDmOnlyToggleTest.java` | **Keep green** — covers `dm_only`, which survives (see Step 1) |
| `handout/FileServeControllerSecurityTest.java` | Update |
| `campaign/packagev2/model/CampaignManifestV2ContractTest.java` | Update — pins the DTO shape |
| `campaign/packagev2/adapter/HandoutSectionAdapterTest.java` | Update |
| `campaign/readiness/CampaignReadinessServiceTest.java` | Update — drop the presentation-safety rule's cases |
| `CoreSessionLoopSmokeTest.java` | Update |
| `support/CampaignFixtures.java`, `support/PopulatedCampaignFixture.java`, `support/ReleaseRehearsalFixture.java`, `support/ReleaseRehearsalFixtureTest.java` | Update — these construct handouts positionally |
| `src/test/resources/campaigns/v2/*.dmcampaign/manifest.json` (3 files) | **Leave untouched** — they are the Task 0.1 guard |

**Interfaces:**
- Consumes: `LegacyHandoutSafetyToleranceTest` from Task 0.1.
- Produces: `Handout` has no `safetyClassification`. `HandoutViewDto` loses `safetyClassification()`. `ReadinessInputs.AssetInput` (**not** `HandoutAsset` — that name does not exist) loses its `safety` component. `CampaignManifestV2.HandoutDto` **keeps** `safetyClassification`, `derivativeRecipe` and `sourceRef` as accepted-and-discarded fields.

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V28__drop_handout_safety_classification.sql`. These names are taken from `V18` (which added `safety_classification`, `derivative_recipe`, `source_handout_id` and `fk_handout_source`) and `V19` (which added both check constraints):

```sql
-- The app no longer has a player-facing surface, so handouts have no
-- classification to enforce. V18 added the columns, V19 added the invariants.
-- Order matters: constraints first, then the FK, then the columns they guard.
alter table handout drop constraint if exists ck_handout_derivative_source;
alter table handout drop constraint if exists ck_handout_safety_classification;
alter table handout drop constraint if exists fk_handout_source;

alter table handout drop column if exists derivative_recipe;
alter table handout drop column if exists source_handout_id;
alter table handout drop column if exists safety_classification;
```

**Keep `dm_only`.** It predates the classification system and marks a handout as DM reference material rather than gating a player screen. Removing it is a separate decision and is out of scope here — `HandoutDmOnlyToggleTest` must stay green. (An earlier draft of this task also listed that test for deletion; that was a contradiction. It stays.)

- [ ] **Step 2: Strip the entity**

In `handout/data/Handout.java`, delete the `SafetyClassification` enum (lines 15-21), the `safetyClassification` field with its `@Enumerated`/`@Column`, the `derivedFrom` association, and their accessors.

- [ ] **Step 3: Strip the readiness rule**

In `CampaignReadinessService.java`, delete the whole loop at lines 46-56 — not just the `if`, since `presentedAssets` has no other rule:

```java
for (ReadinessInputs.AssetInput asset : inputs.presentedAssets()) {
    if (asset.linkedForPresentation() && !asset.safety().isPresentable()) {
        items.add(blockerOrAccepted(
                "asset:" + asset.id(), ReadinessCategory.ASSET, ...));
    }
}
```

Then remove `Handout.SafetyClassification safety` from `ReadinessInputs.AssetInput` (line 40) and from its construction in `ReadinessInputsAssembler.java:49`, which currently reads:

```java
assetInputs.add(new ReadinessInputs.AssetInput(
        h.getId(), h.getTitle(), h.getAssetKind(), h.getSafetyClassification(), h.isPresented()));
```

Decide whether `presentedAssets` earns its place at all once its only rule is gone. If nothing else reads it, drop the component from `ReadinessInputs` and the `AssetInput` record with it — `ReadinessInputs` becomes a 3-component record, and `ReadinessInputsAssembler`'s return becomes `new ReadinessInputs(sceneInputs, List.of(), List.of())`. **Task 3.5 adds a component to this same record**, so leave it in a shape you are happy to extend. Also delete `ReadinessCategory.ASSET` and `ReadinessRepairKind.REVIEW_HANDOUT_SAFETY` if they become unreferenced.

Delete `safetyOf(HandoutDto)` from `PreviewReadinessAssembler.java:89-94` and its call sites.

- [ ] **Step 4: Keep the package contract tolerant — all three fields**

In `campaign/packagev2/model/CampaignManifestV2.java`, `HandoutDto` (line 273) carries **three** components this task retires: `safetyClassification`, `derivativeRecipe` and `sourceRef`. Keep all three on the record so `FAIL_ON_UNKNOWN_PROPERTIES` still accepts them, and stop populating them on export:

```java
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
String safetyClassification,
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
ContentReference sourceRef,
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
String derivativeRecipe,
```

The record is already `@JsonInclude(NON_NULL)`, so a component the exporter always leaves null would vanish from output anyway — the annotation makes the intent explicit and survives someone later removing `NON_NULL`.

Also keep the 10-arg legacy convenience constructor at lines 286-290 compiling; it forwards positionally and will break silently if you reorder components. Do not reorder — only annotate.

Leave `campaign-format-v2.schema.json` unchanged — the fields stay legal in the format, they are simply no longer produced. Delete the classification checks in `CampaignManifestV2SemanticValidator` and `CampaignSemanticComparator`.

- [ ] **Step 5: Reduce the handout card to two actions**

In `templates/handout/_card.html`, replace the whole `<div class="card-actions">` block (lines 29-70) with:

```html
<div class="card-actions">
    <button class="btn btn-ghost"
            th:hx-get="@{/campaigns/{cid}/handouts/{id}/present(cid=${handout.campaign.id},id=${handout.id})}"
            hx-target="body" hx-swap="beforeend">
        View
    </button>
    <button class="btn btn-danger action-row__destructive"
            th:hx-delete="@{/campaigns/{cid}/handouts/{id}(cid=${handout.campaign.id},id=${handout.id})}"
            hx-confirm="Delete this handout?"
            hx-swap="none">
        Delete
    </button>
</div>
```

`Present` becomes `View` because with no player screen it only ever opened a fullscreen viewer on the DM's own display. Dropping the three classification buttons is what fixes the `.card-actions` overflow — verified: with them removed the cards stop escaping at 1600/1366/1280px.

- [ ] **Step 6: Delete the derivative editor**

```bash
git rm src/main/java/dev/hendrikhoemberg/dmhelper/handout/data/DerivativeRecipe.java
git rm src/main/resources/static/js/handout-derivative-editor.js
```

Remove the `<script src="/js/handout-derivative-editor.js">` tag and the `openDerivativeDialog` function reference from the handout templates.

- [ ] **Step 7: Delete a stale H2 file and start the app to prove the migration runs**

```bash
cp ~/.dmhelper/data/dmhelper.mv.db ~/.dmhelper/data/dmhelper.mv.db.pre-v28
./mvnw -q -o spring-boot:run -Dspring-boot.run.arguments="--server.port=8083 --dmhelper.open-browser=false"
```

Expected: the app starts, Flyway logs `Migrating schema "PUBLIC" to version "28 - drop handout safety classification"`, and Hibernate's `validate` passes. If validation fails, the entity and migration disagree — fix the migration, never the `ddl-auto` setting. Stop with `pkill -f "server.port=8083"`.

- [ ] **Step 8: Run every test that touches the field**

The original glob (`LegacyHandoutSafetyToleranceTest+Handout*Test+*Readiness*Test`) misses the Flyway and packagev2 suites, which is how this task would otherwise ship a red commit under the "commit after every task" rule. Use:

```bash
./mvnw test -Dtest='LegacyHandoutSafetyToleranceTest+Handout*Test+*Readiness*Test+Flyway*Test+CampaignManifestV2*Test+*SectionAdapterTest+CoreSessionLoopSmokeTest+ReleaseRehearsalFixtureTest'
```

Expected: PASS. `LegacyHandoutSafetyToleranceTest` failing here means export/import compatibility broke — fix Step 4, do not weaken the test. `Flyway*Test` failing means V28 disagrees with what those tests assert about the schema — update the test to the new expected shape, never the `ddl-auto` setting.

If the compile still fails after this, run a bare `grep -rln "safetyClassification\|SafetyClassification\|safety_classification\|DerivativeRecipe\|derivativeRecipe" src/main src/test` and work the remainder. Expected residue: only `CampaignManifestV2.java`, `campaign-format-v2.schema.json`, the three `.dmcampaign` manifests, `LegacyHandoutSafetyToleranceTest`, and `V18`/`V19`/`V28`.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: remove handout safety classification"
```

### Task 1.5: Remove the PIN gate and QR code, and bind to localhost

> **Read this before starting — the original framing of this task was wrong.**
>
> `PinInterceptor` is **not** a LAN-device admission gate. `WebMvcConfig:38-54` registers it on `/**` and *excludes* `/player`, `/ws/table`, `/dm/authenticate`, static assets and a handful of read-only APIs. In other words it gates **the DM surface** and lets the player surface through. There is no `spring-boot-starter-security` in `pom.xml`, so this interceptor plus its 429 rate limiter (`PinInterceptor:38-72`) is the application's *only* access control.
>
> Deleting it therefore leaves every DM endpoint unauthenticated to anything that can reach the port. `WebMvcConfig` also defaults `dmhelper.pin-enabled` to **`true`** in code (`@Value("${dmhelper.pin-enabled:true}")`); only `application.properties` sets it false, so a deployment that omits that property is currently gated.
>
> **Decision taken:** delete the gate *and* bind the server to loopback, so the app is unreachable from the LAN rather than reachable and open. Step 5 is not optional — without it this task is a straight downgrade in security posture.

**Files:**
- Delete: `src/main/java/.../common/config/QrCodeController.java`
- Delete: `src/main/java/.../common/config/PinManager.java`
- Delete: `src/main/java/.../common/config/PinInterceptor.java`
- Delete: `src/main/java/.../common/config/PinModelAdvice.java`
- Delete: `src/test/java/.../common/config/PinInterceptorTest.java`
- Delete: `src/test/java/.../common/PinInterceptorRateLimitTest.java`
- Modify: `src/main/java/.../common/config/WebMvcConfig.java` — drop the `PinManager` field, the `@Value` binding, the constructor param and the whole `addInterceptors` override
- Modify: `src/main/resources/application.properties` (drop `dmhelper.pin-enabled`, add `server.address`)
- Modify: `src/test/resources/application.properties` (drop `dmhelper.pin-enabled`)
- Modify: `templates/fragments/navbar.html` (drop `PIN:` chip and `QR` control)
- Modify: `pom.xml` (two `zxing` dependency blocks at lines ~64-71)
- Modify: 4 tests that authenticate with a PIN before asserting something else — `campaign/packagev2/web/CatalogControllerTest`, `session/SessionCockpitSecurityTest`, `audio/web/AudioCockpitSecurityTest`, `agent/web/AgentContractControllerTest`. Strip the PIN setup; keep whatever else they assert.

**Do not touch map pins.** `MapPinApiController`, `MapThreatPin`, `MapThreatPinRepository`, `MapThreatPinService`, `MapThreatPinWrite` and `MapPinDto` are battle-map pins and are unrelated to the DM PIN. `adventure/web/MapPinAccessControlTest` matches the grep below for that reason — leave it alone.

- [ ] **Step 1: Find the full surface**

Run: `grep -rln "pin-enabled\|PinManager\|PinInterceptor\|PinModelAdvice\|QrCode\|/qr/\|DM PIN" src/main src/test`
Expected: **15 files** (an earlier draft said 7 — it had greped `src/main` only). Every one is deleted or edited in this task except `adventure/web/MapPinAccessControlTest.java`, which is a false positive on battle-map pins.

- [ ] **Step 2: Delete the classes, their tests, and the config**

```bash
git rm src/main/java/dev/hendrikhoemberg/dmhelper/common/config/QrCodeController.java
git rm src/main/java/dev/hendrikhoemberg/dmhelper/common/config/PinManager.java
git rm src/main/java/dev/hendrikhoemberg/dmhelper/common/config/PinInterceptor.java
git rm src/main/java/dev/hendrikhoemberg/dmhelper/common/config/PinModelAdvice.java
git rm src/test/java/dev/hendrikhoemberg/dmhelper/common/config/PinInterceptorTest.java
git rm src/test/java/dev/hendrikhoemberg/dmhelper/common/PinInterceptorRateLimitTest.java
```

In `WebMvcConfig.java` delete the `PinManager` field, the `@Value("${dmhelper.pin-enabled:true}")` field, the constructor parameter (the constructor becomes implicit — remove it), and the entire `addInterceptors` override. Keep `hiddenHttpMethodFilter` and `addResourceHandlers`. Remove `dmhelper.pin-enabled` from **both** `src/main/resources/application.properties` and `src/test/resources/application.properties`.

- [ ] **Step 3: Strip the header controls**

In `templates/fragments/navbar.html` delete the `PIN: …` chip, the `QR` button and the QR `<img th:src="@{/qr/player-view}">` popover (line ~26).

- [ ] **Step 4: Remove the `zxing` dependency**

Run: `grep -n -B2 -A4 "zxing" pom.xml`
Expected: two `<dependency>` blocks (`core` and `javase`) around lines 64-71. `QrCodeController` is their only consumer — remove both. Re-run `grep -rn "com.google.zxing" src/` to confirm nothing else imports them.

- [ ] **Step 5: Bind the server to loopback**

This replaces the access control being removed. In `src/main/resources/application.properties`:

```properties
# No authentication layer ships with the app (the DM PIN gate was removed in the
# DM-only cut). Bind to loopback so the DM surface is not reachable from the LAN.
# Overridable for deliberate LAN use — do that only behind a trusted network.
server.address=127.0.0.1
```

Verify it took effect: start the app, then from another machine on the same network `curl -m 3 http://<this-host-lan-ip>:8080/` and expect a connection refusal, while `curl http://127.0.0.1:8080/` on the host succeeds. If you have no second machine, `ss -tlnp | grep 8080` must show `127.0.0.1:8080`, not `0.0.0.0:8080` or `*:8080`.

- [ ] **Step 6: Fix the four tests that authenticated before asserting**

`CatalogControllerTest`, `SessionCockpitSecurityTest`, `AudioCockpitSecurityTest` and `AgentContractControllerTest` each set a `dm_pin` cookie or `dmhelper.pin-enabled` property as *setup*, then assert something unrelated. Strip only the setup. If a test's entire subject was the gate (i.e. it asserts 403 for a missing PIN), delete that test method and note the removal in its class javadoc — do not silently weaken it into a 200 assertion.

- [ ] **Step 7: Compile and run the full suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS. This is the phase-closing full run — fix or delete any test that only covered deleted behaviour.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: remove LAN PIN gate and QR code, bind server to loopback"
```

### Task 1.6: Drop the presentation section from the session log

**Files:**
- Modify: `src/main/java/.../session/service/SessionDraftService.java:113,128-150`
- Modify: `src/main/java/.../session/data/SessionAuditEntry.java` (drop `PRESENTATION_OVERRIDE` enum constant)
- Test: `src/test/java/.../session/service/SessionDraftServiceTest.java`

- [ ] **Step 1: Write the failing test**

In `SessionDraftServiceTest`:

```java
@Test
void draftOmitsPresentationSafetySection() {
    String draft = service.generate(session, Instant.now());

    assertThat(draft).doesNotContain("Presentation Safety Overrides");
    assertThat(draft).contains("## Scenes", "## Encounters", "## Recap");
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=SessionDraftServiceTest#draftOmitsPresentationSafetySection`
Expected: FAIL — the section is still emitted.

- [ ] **Step 3: Remove the section and its builder**

Delete line 113 (`listSection(out, "Presentation Safety Overrides", …)`), the `auditOverrideLines` and `auditOverrideLine` methods, the `auditRepo` field and constructor parameter, and the `PRESENTATION_OVERRIDE` constant from `SessionAuditEntry.EntryType`. Remove any now-dead assertions from the existing tests.

- [ ] **Step 4: Run the test**

Run: `./mvnw test -Dtest=SessionDraftServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: drop presentation safety section from session log"
```

---

## Phase 2 — Blockers

The five defects that stop a session being run at all.

### Task 2.1: Fix the character sheet creation 500 (blocker #1)

`sheet/create.html:64` uses `${{'STR','DEX',…}}`. Thymeleaf's `${{…}}` is *conversion* syntax: it strips the outer braces and hands SpEL `'STR','DEX',…`, which fails with `EL1041E: After parsing a valid expression, there is still more data in the expression: 'comma(,)'`. The page has never rendered.

**Files:**
- Modify: `src/main/resources/templates/sheet/create.html:64`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetCreateFormRenderTest.java` (create)

**Interfaces:**
- Consumes: `SheetController.createForm` at `sheet/web/SheetController.java:104`, unchanged.
- Produces: `GET /campaigns/{cid}/party/{pid}/sheet/create` returns 200.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.sheet.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SheetCreateFormRenderTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void createFormRendersWithAnAbilityFieldForEachScore() throws Exception {
        var fixture = SheetTestFixtures.campaignWithOneMember();

        mvc.perform(get("/campaigns/{cid}/party/{pid}/sheet/create",
                        fixture.campaignId(), fixture.memberId()))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("STR")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("CHA")));
    }
}
```

If no `SheetTestFixtures` helper exists, seed a campaign and party member inline using the repositories the way `HandoutDmOnlyToggleTest` does.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=SheetCreateFormRenderTest`
Expected: FAIL with status 500 and `SpelParseException … EL1041E` in the log.

- [ ] **Step 3: Fix the expression**

In `src/main/resources/templates/sheet/create.html:64` change:

```html
<div class="form-group" th:each="ability : ${{'STR','DEX','CON','INT','WIS','CHA'}}">
```

to:

```html
<div class="form-group" th:each="ability : ${ {'STR','DEX','CON','INT','WIS','CHA'} }">
```

The inner space stops Thymeleaf reading `${{` as the conversion form, so SpEL receives a valid inline list.

- [ ] **Step 4: Run the test**

Run: `./mvnw test -Dtest=SheetCreateFormRenderTest`
Expected: PASS.

- [ ] **Step 5: Check for the same mistake elsewhere**

Run: `grep -rn '\${{' src/main/resources/templates/`
Any other hit whose inner expression is a list literal has the same bug — fix it the same way and extend the test.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "fix: render character sheet creation form (SpEL inline list)"
```

### Task 2.2: Restore "Add from Library" and "Add Trap / Hazard" (blocker #2)

Three faults in two files. The `<script th:inline="javascript">` that calls `Alpine.data('libraryAdd', …)` sits *outside* the element carrying `th:fragment`, so Thymeleaf never emits it — hence `libraryAdd is not defined` and the cascade of `query/quantity/waveId/results is not defined`. Separately, `data-encounter-id="${encounterId}"` emits the literal string, and `th:each="sb : ${results}"` iterates a server variable that does not exist, so Alpine results could never render.

**Files:**
- Modify: `src/main/resources/templates/encounter/_library-add.html:1-91`
- Modify: `src/main/resources/templates/encounter/_threat-add.html:1-96`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterSetupFragmentTest.java` (create)

**Interfaces:**
- Consumes: `POST /api/v1/encounters/{id}/combatants/from-library` and `/from-threat`, unchanged.
- Produces: `GET /campaigns/{cid}/encounters/{eid}/setup` HTML contains `Alpine.data('libraryAdd'` and `Alpine.data('threatAdd'`, and a real UUID in `data-encounter-id`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void setupPageShipsTheAlpineComponentsItsWidgetsNeed() throws Exception {
    var f = EncounterTestFixtures.campaignWithEncounter();

    String html = mvc.perform(get("/campaigns/{cid}/encounters/{eid}/setup",
                    f.campaignId(), f.encounterId()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(html).contains("Alpine.data('libraryAdd'");
    assertThat(html).contains("Alpine.data('threatAdd'");
    assertThat(html).doesNotContain("${encounterId}");
    assertThat(html).contains("data-encounter-id=\"" + f.encounterId() + "\"");
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=EncounterSetupFragmentTest`
Expected: FAIL on the first assertion — the script is absent from the response.

- [ ] **Step 3: Move the script inside the fragment**

In `_library-add.html`, the fragment root `<div th:fragment="library-add(campaignId, encounterId)">` currently closes at line 41, with `<script>` at 42-90 and a stray `</div>` at 91. Move the closing `</div>` of the fragment root to after `</script>` and delete the stray one, so the structure is:

```html
<div th:fragment="library-add(campaignId, encounterId)"
     x-data="libraryAdd"
     th:attr="data-encounter-id=${encounterId}">
    <h3>Add from Library</h3>
    ...
    <script th:inline="javascript">
    document.addEventListener('alpine:init', () => {
        Alpine.data('libraryAdd', () => ({ ... }));
    });
    </script>
</div>
```

Note line 3 also changes from `data-encounter-id="${encounterId}"` to `th:attr="data-encounter-id=${encounterId}"`.

- [ ] **Step 4: Apply the identical change to `_threat-add.html`**

Same shape: fragment root opens line 1, currently closes line 43, script 44-95, stray `</div>` line 96. Line 3 already uses `th:attr` correctly there — leave it.

- [ ] **Step 5: Remove the server/client `results` collision**

In `_library-add.html`, the result card at line ~23 carries `th:each="sb : ${results}"` alongside Alpine's `x-text="sb.name"`. Delete the `th:each` attribute and wrap the card in an Alpine template instead:

```html
<template x-for="sb in results" :key="sb.id">
    <div class="card">
        <div class="card-body">
            <h4 x-text="sb.name" class="card-title">Name</h4>
            <p class="text-muted u-text-sm">
                <span x-text="sb.cr"></span> CR · <span x-text="sb.type"></span> ·
                HP <span x-text="sb.hp"></span> · AC <span x-text="sb.ac"></span> ·
                <span x-text="sb.xp"></span> XP
            </p>
        </div>
        <div class="card-actions">
            <button class="btn btn-sm btn-primary" @click="addFromLibrary(sb.id)">Add</button>
        </div>
    </div>
</template>
```

- [ ] **Step 6: Run the test**

Run: `./mvnw test -Dtest=EncounterSetupFragmentTest`
Expected: PASS.

- [ ] **Step 7: Verify in the browser**

Start the app, open an encounter's Setup page, type `goblin` into "Search creatures by name…". Expected: SRD goblin cards appear with CR badges and a working Add button; the browser console is clean.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "fix: emit Alpine components for encounter library and threat search"
```

### Task 2.3: Stop the cockpit losing its encounter module on load (blocker #3)

Reproducible: open the cockpit with the Combat preset, navigate away, come back. Only `party`, `quick-notes` and `session-plan` fragments load; the Encounter module renders as a bare header with no content, no empty state and no error — despite `data-empty-message` and `data-error-message` being set on the shell. The server fragment returns 200 with 52KB. Toggling the preset recovers it; a plain reload does not.

The prime suspect is the visibility gate at `cockpit-modules.js:148-150` — `if (!options.force && !visible) return;` combined with `isModuleVisible` returning false for a shell whose `offsetParent` is null during first paint. Nothing marks the module stale afterwards, so it never retries.

> **The evaluation's own evidence contradicts that diagnosis. Resolve this before touching code.**
>
> The report says the Combat preset loaded only `party`, `quick-notes` and `session-plan`. But **`session-plan` is not in the Combat preset** — `builtin:combat` is `map` / `story`+`party` / `encounter` / `quick-notes`+`audio` (`CockpitBuiltInPresetCatalog:18-20`). `party` + `quick-notes` + `session-plan` is exactly the module set of **`builtin:exploration`** (`:13-16`).
>
> So the likelier fault is that the saved layout silently resolved to Exploration rather than Combat — i.e. a *persistence or resolution* bug, not a hydration bug. That also explains the two symptoms the hydration theory struggles with: "toggling the preset recovers it" (re-selecting Combat re-resolves the layout) and "a plain reload does not" (the reload re-reads the same bad saved layout).
>
> Note also that `isModuleVisible` delegates to `window.cockpitLayout.isModuleVisible(key)` whenever the layout controller is present (`cockpit-modules.js:115-117`) — the `offsetParent` check on line 118 is a fallback that almost never runs in the real cockpit. That weakens the stated theory further.

**Files:**
- Modify: `src/main/resources/static/js/cockpit-modules.js:110-160`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitHydrationTest.java` (create)

**Interfaces:**
- Consumes: `GET /campaigns/{cid}/session/modules/{key}` — unchanged.
- Produces: every module in the active layout reaches state `loaded` or `error` after first paint; none stays blank.

- [ ] **Step 1: Reproduce before changing anything**

REQUIRED SUB-SKILL: use superpowers:systematic-debugging. Do not edit `cockpit-modules.js` until you have observed the failure and confirmed its actual cause.

Start the app, open the cockpit, select the Combat preset, navigate to any library page, return to `/campaigns/{cid}/session`, and in the console run — **in this order**:

```js
// 1. WHICH LAYOUT actually resolved? Check this first; see the note above.
window.cockpitLayoutConfig?.presetId
document.querySelectorAll('[data-module-key]').forEach(e =>
  console.log(e.getAttribute('data-module-key'), window.cockpitLayout.isModuleVisible(
    e.getAttribute('data-module-key'))));

// 2. Only if the preset IS builtin:combat, ask the hydration question.
document.querySelectorAll('[data-cockpit-module-fragment]').length
```

If step 1 reports `builtin:exploration` (or a saved layout whose module set is Exploration's), **stop — the bug is in layout persistence/resolution, not hydration, and Steps 2-4 below do not apply.** Fix it there instead: find where the resolved preset id is written and read back, and why the Combat selection did not survive the navigation. Then write a server-side or JS test pinning "the layout that was saved is the layout that resolves".

If step 1 confirms Combat resolved correctly and modules are genuinely missing, continue: instrument `isModuleVisible` and `load` with `console.log` to record which guard returns early for `encounter`.

- [ ] **Step 2: Write the failing test**

```java
@Test
void everyModuleInTheLayoutDeclaresAnEndpointAndAnEmptyMessage() throws Exception {
    var f = SessionTestFixtures.runningSessionWithCombatPreset();

    String html = mvc.perform(get("/campaigns/{cid}/session", f.campaignId()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    var doc = org.jsoup.Jsoup.parse(html);
    var shells = doc.select("[data-runtime-module]");

    assertThat(shells).isNotEmpty();
    for (var shell : shells) {
        assertThat(shell.select("[data-module-content]").first())
                .as("module %s must expose a content root the loader can fill",
                        shell.attr("data-module-key"))
                .isNotNull();
    }
}
```

This is the server half of the contract. The client half is verified in Step 5 by hand, because there is no JS test harness in this project and adding one is out of scope.

- [ ] **Step 3: Run it**

Run: `./mvnw test -Dtest=CockpitHydrationTest`
Expected: PASS or FAIL depending on what Step 1 found. If it passes, the fault is entirely client-side — proceed to Step 4.

- [ ] **Step 4: Fix the gate** *(only if Step 1 confirmed a hydration fault)*

Replace the early return at `cockpit-modules.js:150` so a module that is not yet visible is queued rather than dropped:

```js
if (!options.force && !visible) {
  this.stale.add(moduleKey);
  return;
}
```

and add a post-layout flush in the controller's init, after the first `requestAnimationFrame`:

```js
requestAnimationFrame(() => {
  requestAnimationFrame(() => {
    for (const key of [...this.stale]) {
      if (this.isModuleVisible(key)) this.load(key, { force: true });
    }
  });
});
```

Additionally, make a failed or skipped load visible instead of silent: in the `catch` branch, render `data-error-message` into the content root with a Retry button, and when a load resolves with an empty fragment render `data-empty-message`. A blank panel must never be a reachable state.

**This last paragraph is worth doing regardless of what Step 1 found.** Whatever the root cause turns out to be, a module that silently renders as a bare header is the reason the defect went undiagnosed for a whole session. Ship the visible-failure states even if the queueing fix turns out to be unnecessary.

Note that a module sitting in an **inactive tab** is legitimately invisible and must stay queued, not force-loaded — `isModuleVisible` returns false for it by design. Task 2.4 depends on that distinction.

- [ ] **Step 5: Verify the repro is gone**

Repeat Step 1's sequence. Expected: 6 fragments, the combat tracker present, console clean. Then reload three times in a row and confirm the tracker survives each.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "fix: hydrate cockpit modules that are not visible at first paint"
```

### Task 2.4: Keep statblock lookup inside the session (blocker #4)

Today, Enter on a monster in the command palette full-page-navigates to `/library/statblocks/{id}`, which swaps in the global sidebar with no campaign context, no route back to the session, and no dice roller. Combined with blocker #3 it also cost the combat tracker. A **Reference module already exists** (`static/js/cockpit-reference.js`, `templates/session/modules/_reference.html`, registered at `CockpitModuleRegistry:54`) but is in no built-in preset.

> **Three things this task originally assumed do not exist.** Corrected below; do not write code against the originals.
>
> 1. **`cockpit:focus-module` and `data-cockpit-root`.** Neither appears anywhere in `src/main/resources`. The layout controller's real API is `window.cockpitLayout.selectTab(zone, key)` (`cockpit-layout.js:817`) and `focusModule(key, returnEl)` (`:1440`) — and `focusModule` means *fullscreen the module in the focus layer*, which is not what we want here. Step 3b adds the missing primitive.
> 2. **`cockpit-reference.js` has no `showStatblock` and nowhere to render one.** Its component is `Alpine.data('cockpitReference', …)` with `query`, `results`, `search()`, `_doSearch()`, `_groupResults()` and `selectItem(item)` — and `selectItem` currently does `window.open(url, '_blank')`, i.e. **the Reference module also navigates away today**. The module is a search box with a result list and no detail pane. Step 5 builds one.
> 3. **The palette's result type for monsters is `'statblock'`, not `'MONSTER'`,** and its activation function is `navigateTo(item)` using `item.url` (`command-palette.js:97`) — there is no `result.href` and no `result.type === 'MONSTER'`. `typeLabel()` maps `'statblock' → 'Monster'`, which is where the evaluation's `Monster` badge came from.

**Files:**
- Modify: `src/main/java/.../session/layout/CockpitBuiltInPresetCatalog.java`
- Modify: `src/main/resources/static/js/cockpit-layout.js` (**add `revealModule(key)`**)
- Modify: `src/main/resources/static/js/cockpit-reference.js` (**add `showStatblock(id)` + a detail pane**)
- Modify: `src/main/resources/templates/session/modules/_reference.html` (detail pane markup)
- Modify: `src/main/resources/static/js/command-palette.js:96-99`
- Test: `src/test/java/.../session/service/CockpitLayoutPresetServiceTest.java` (**`session/service/`**, not `session/layout/`)

**Interfaces:**
- Consumes: `reference` module key from `CockpitModuleRegistry:54`; `Alpine.data('cockpitReference')` from `cockpit-reference.js`.
- Produces:
  - `window.cockpitLayout.revealModule(key)` — finds the zone containing `key`, `selectTab`s to it, un-collapses the zone if needed, returns `true` if the module is now visible. Task 3.7 reuses it.
  - A `cockpit:show-reference` window event with `{ detail: { type: 'statblock', id, name } }`, handled inside the `cockpitReference` component's `init()`. Task 3.7 dispatches the same event.

- [ ] **Step 1: Write the failing test**

```java
@Test
void combatPresetIncludesTheReferenceModule() {
    var preset = catalog.byId("builtin:combat").orElseThrow();

    assertThat(preset.moduleKeys()).contains("reference");
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=CockpitLayoutPresetServiceTest#combatPresetIncludesTheReferenceModule`
Expected: FAIL — `reference` is in no preset.

- [ ] **Step 3: Add reference to the Combat preset**

In `CockpitBuiltInPresetCatalog.java`, the `builtin:combat` preset (lines 18-20) is:

```java
preset("builtin:combat", "Combat",
        zone("map"), zone("story", "party"), zone("encounter"),
        zone("quick-notes", "audio"),
        Set.of("story", "party", "encounter", "quick-notes", "audio")),
```

Change the RIGHT_SUPPORT zone to `zone("encounter", "reference")` so reference shares space with the tracker rather than competing for width. Add `"reference"` to the compact-module set too. `reference` declares `RIGHT_SUPPORT` among its allowed zones (`CockpitModuleRegistry:54`), so this placement is legal — verify that with `CockpitModuleRegistry.require("reference").allowedZones()` if the layout resolver rejects it.

**Consequence you must handle:** as a second tab, `reference` is *inactive* by default, so `window.cockpitLayout.isModuleVisible('reference')` is false and Task 2.3's queueing fix will correctly leave it unloaded. Without Step 3b the statblock loads into a panel nobody sees. That is the whole point of the next step.

- [ ] **Step 3b: Add the missing `revealModule` primitive**

In `cockpit-layout.js`, next to `selectTab(zone, key)` (line 817), add:

```js
/**
 * Bring `key` into view: activate its tab and expand its zone.
 * Returns true if the module ended up visible. Unlike focusModule(), this does
 * not move the shell into the focus layer.
 */
revealModule(key) {
  const shell = document.querySelector(`[data-module-key="${key}"]`);
  const zoneEl = shell?.closest('[data-cockpit-zone]');
  if (!zoneEl) return false;
  const zone = zoneEl.getAttribute('data-cockpit-zone');
  if (this.isZoneCollapsed?.(zone)) this.toggleZone?.(zone);
  this.selectTab(zone, key);
  return this.isModuleVisible(key);
}
```

Adapt `isZoneCollapsed`/`toggleZone` to whatever the controller actually calls its collapse API — read the file; `collapsedZone(...)` in the preset catalog implies one exists. If a zone cannot be un-collapsed programmatically, `selectTab` alone is acceptable for now; note the limitation in a comment.

- [ ] **Step 4: Route palette statblock hits into the module**

`command-palette.js:96-99` currently reads:

```js
navigateTo(item) {
  this.open = false;
  this.query = '';
  this.results = [];
  if (item.url) {
    window.location.href = item.url;
  }
}
```

Replace the body's tail. The type token is `'statblock'` (confirmed against `typeLabel()` at `:100-104`, which maps it to the `Monster` badge the evaluation saw), and cockpit presence is detected via the layout controller, not a marker attribute:

```js
navigateTo(item) {
  this.open = false;
  this.query = '';
  this.results = [];

  const inCockpit = typeof window.cockpitLayout?.revealModule === 'function';
  if (inCockpit && item.type === 'statblock' && item.id) {
    window.dispatchEvent(new CustomEvent('cockpit:show-reference', {
      detail: { type: 'statblock', id: item.id, name: item.name }
    }));
    return;
  }

  if (item.url) {
    window.location.href = item.url;
  }
}
```

- [ ] **Step 5: Give the reference module a detail pane and a `showStatblock`**

`cockpit-reference.js` has no detail pane — `selectItem(item)` does `window.open(url, '_blank')`, so today the Reference module *also* leaves the session. Fix both entry points at once.

In `_reference.html`, add a detail region below the results list, shown when `statblock` is set, with a Back control that clears it. In `cockpit-reference.js`, extend the `cockpitReference` component. The listener **must be registered inside `init()`** — the component is a factory function, so a `window.addEventListener` at module scope has no `this`:

```js
// added to the component's state
statblock: null,
statblockError: '',

init() {
  // ...existing campaignId lookup...
  window.addEventListener('cockpit:show-reference', (e) => {
    if (e.detail?.type !== 'statblock' || !e.detail.id) return;
    window.cockpitLayout?.revealModule?.('reference');
    this.showStatblock(e.detail.id);
  });
},

async showStatblock(id) {
  this.statblockError = '';
  try {
    const resp = await window.dmRequest('/api/v1/library/statblocks/' + encodeURIComponent(id));
    this.statblock = await resp.json();
  } catch (error) {
    this.statblock = null;
    this.statblockError = 'Could not load that statblock.';
  }
},

clearStatblock() { this.statblock = null; this.statblockError = ''; },
```

Then change `selectItem(item)` so a `'statblock'` type calls `this.showStatblock(item.id)` instead of `window.open` — leave the other types navigating for now, since they have no in-cockpit renderer.

Confirm the endpoint before writing the fetch: `grep -rn "library/statblocks" src/main/java --include=*.java`. If the JSON route differs from `/api/v1/library/statblocks/{id}`, use the real one.

Render the detail pane from the same fields `templates/library/_statblock.html` uses. Getting AC, HP, speed, saves and the action list in is enough for this task; a pixel match with the library page is not required.

- [ ] **Step 6: Run the test and verify by hand**

Run: `./mvnw test -Dtest=CockpitLayoutPresetServiceTest`
Expected: PASS.

Then start a session, switch to Combat, press Ctrl+K, type `goblin`, press Enter on "Goblin Warrior". Expected: **the Reference tab activates on its own**, the statblock appears in it, the URL does not change, no new browser tab opens, and the combat tracker is still populated when you switch back to it.

If the statblock loads but the tab does not activate, `revealModule` returned false — that is Step 3b, not Step 5.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: show statblocks in the cockpit reference module instead of navigating away"
```

### Task 2.5: Add the missing combatant list to encounter setup (blocker #5)

`templates/encounter/setup.html:31-52` renders three add-forms and nothing else. There is no way to see, rename, re-HP, reorder or remove a combatant. The only listing anywhere is the placement board's truncated name chips.

**Files:**
- Create: `src/main/resources/templates/encounter/_combatant-list.html`
- Modify: `src/main/resources/templates/encounter/setup.html` — insert after the `_threat-add` include at **line 50** (the `setup-group__body` opens at line 33; `_library-add` is line 49, `_threat-add` line 50, `_placement-board` line 58)
- Test: `src/test/java/.../encounter/web/EncounterSetupFragmentTest.java` (extend from Task 2.2)

**Interfaces:**
- Consumes: `${combatants}` — already in the model, passed to `_placement-board` at `setup.html:58`. No controller change needed; drop the "add it if absent" step.
- Produces: **nothing new.** The routes already exist:
  - `DELETE /api/v1/combatants/{id}` — `EncounterApiController:156`, calls `service.removeCombatant(id)`, returns `204`
  - `PUT /api/v1/combatants/{id}` — `EncounterApiController:151`, takes `CombatantUpdateRequest`, returns `CombatantDto`

  Note both are keyed on the **combatant id alone** and live under `/api/v1`, not under `/campaigns/{cid}/encounters/{eid}/…`. Do not add a parallel route family.

- [ ] **Step 1: Write the failing test**

```java
@Test
void setupPageListsEveryCombatantWithItsHp() throws Exception {
    var f = EncounterTestFixtures.encounterWithTwoGoblins();

    String html = mvc.perform(get("/campaigns/{cid}/encounters/{eid}/setup",
                    f.campaignId(), f.encounterId()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(html).contains("Goblin-Bogenschütze 1");
    assertThat(html).contains("Goblin-Nahkämpfer 1");
    assertThat(html).contains("data-combatant-row");
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=EncounterSetupFragmentTest#setupPageListsEveryCombatantWithItsHp`
Expected: FAIL — the names are absent from the setup page.

- [ ] **Step 3: Create the fragment**

`src/main/resources/templates/encounter/_combatant-list.html`:

```html
<div th:fragment="combatant-list(campaignId, encounterId, combatants)" class="combatant-list">
    <table class="data-table" th:if="${!combatants.isEmpty()}">
        <thead>
            <tr><th>Name</th><th>Kind</th><th>HP</th><th>Init</th><th>Wave</th><th></th></tr>
        </thead>
        <tbody>
            <tr th:each="c : ${combatants}" data-combatant-row th:attr="data-combatant-id=${c.id}">
                <td th:text="${c.name}">Name</td>
                <td><span class="badge" th:text="${#strings.capitalizeWords(#strings.toLowerCase(c.kind))}">Monster</span></td>
                <td th:text="|${c.currentHp} / ${c.maxHp}|">7 / 7</td>
                <td th:text="${c.initiative} ?: '—'">—</td>
                <td th:text="${c.waveName} ?: 'Main wave'">Main wave</td>
                <td>
                    <button class="btn btn-ghost btn-xs action-row__destructive"
                            th:hx-delete="@{/api/v1/combatants/{id}(id=${c.id})}"
                            th:hx-confirm="|Remove ${c.name} from this encounter?|"
                            hx-target="closest [data-combatant-row]"
                            hx-swap="delete">
                        Remove
                    </button>
                </td>
            </tr>
        </tbody>
    </table>
    <p th:if="${combatants.isEmpty()}" class="text-muted u-text-sm">
        No combatants yet. Add one above, or search the library.
    </p>
</div>
```

`.data-table` is styled in Task 4.1 — until then this renders unpadded, which is expected.

Two details that matter:

- **`hx-swap="delete"`, not `"outerHTML"`.** `DELETE /api/v1/combatants/{id}` returns `204 No Content`, and htmx performs **no swap** on a 204 — an `outerHTML` swap would leave the row on screen until the next reload. `hx-swap="delete"` removes the target regardless of body.
- The API is keyed on the combatant id alone, so `campaignId`/`encounterId` are unused in the URL. Keep them as fragment parameters anyway — the empty-state copy and any future per-encounter action will want them.

- [ ] **Step 4: Include it in the setup page**

In `templates/encounter/setup.html`, immediately after the `_threat-add` include at line 50 and before the closing `</div>` of `setup-group__body` (line 51):

```html
<div th:replace="~{encounter/_combatant-list :: combatant-list(${campaignId}, ${encounter.id}, ${combatants})}"></div>
```

- [ ] **Step 5: Confirm the DELETE endpoint — with a grep that cannot lie to you**

The obvious grep gives a **false positive**: `grep "combatants/{combatantId}"` matches `@DeleteMapping("/encounters/{id}/combatants/{combatantId}/placement")` at `EncounterApiController:360`, which removes a *token placement*, not a combatant. An executor trusting that match would skip this step and ship a dead button.

Run instead:

```bash
grep -n 'DeleteMapping("/combatants/{id}")\|PutMapping("/combatants/{id}")' \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java
```

Expected: two hits, at lines 156 and 151. Both already exist — **no new endpoint is needed and none should be added.** If they are somehow absent, add them to `EncounterApiController` at `/combatants/{id}` (not under `/encounters/...`) and add a service test.

- [ ] **Step 6: Run the test**

Run: `./mvnw test -Dtest=EncounterSetupFragmentTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: list and remove combatants on the encounter setup page"
```

---

## Phase 3 — Coherence

The cockpit's panels can disagree with each other. With the player view gone the DM screen is the only screen, so these matter more, not less.

### Task 3.1: Run a scene's existing encounter from the cockpit

`_story-rail.html:100` gates the encounter button on `view.canSeedEncounter`, which is false precisely when the scene *already has* a linked encounter — so for authored content like Phandelver there is no way to activate the right encounter from the cockpit. The DM must scroll a campaign-wide alphabetical list instead.

**Files:**
- Modify: `src/main/resources/templates/session/_story-rail.html:100-108`
- Modify: `src/main/java/.../session/runtime/StoryModuleViewService.java` (add `linkedEncounterId` to the view record)
- Test: `src/test/java/.../session/runtime/StoryModuleViewServiceTest.java`

**Interfaces:**
- Consumes: `Scene.linkedEncounter` association.
- Produces: story view record gains `UUID linkedEncounterId` (null when the scene has none). Task 3.2 does not touch this record.

- [ ] **Step 1: Write the failing test**

```java
@Test
void storyViewExposesTheScenesLinkedEncounter() {
    var f = SceneTestFixtures.sceneWithLinkedEncounter();

    var view = service.buildView(f.campaignId(), f.sceneId(), CockpitModuleMode.STANDARD);

    assertThat(view.linkedEncounterId()).isEqualTo(f.encounterId());
    assertThat(view.canSeedEncounter()).isFalse();
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=StoryModuleViewServiceTest`
Expected: FAIL — `linkedEncounterId` does not exist.

- [ ] **Step 3: Add the field**

Add `UUID linkedEncounterId` to the story view record and populate it from the scene's linked encounter, or null.

- [ ] **Step 4: Add the button**

In `_story-rail.html`, next to the existing seed button:

```html
<button class="btn btn-primary"
        th:if="${view.linkedEncounterId != null}"
        th:attr="data-encounter-id=${view.linkedEncounterId}"
        @click="activateEncounter($el.dataset.encounterId)">
  Run this encounter
</button>
```

Add `activateEncounter(id)` to the story rail's Alpine component: `POST /api/v1/encounters/{id}/activate`, then dispatch `cockpit:module-refresh` for `encounter` and `map`.

- [ ] **Step 5: Run the test and verify by hand**

Run: `./mvnw test -Dtest=StoryModuleViewServiceTest`
Expected: PASS.

Then in the cockpit jump the Story module to "Goblin-Hinterhalt" and press "Run this encounter". Expected: the Encounter rail switches to "Goblin-Hinterhalt auf dem Dreieber-Pfad" and the map follows.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: activate a scene's linked encounter from the cockpit"
```

### Task 3.2: Scope and filter the planned-encounters list

The rail lists every encounter in the campaign, alphabetically, unfiltered — session 1 requires scrolling past Cragmaw Castle and Wave Echo Cave. It also reads "1 combatants" and shows a bare "Ready"/"Not ready" badge with no explanation.

**Files:**
- Modify: `src/main/java/.../session/runtime/EncounterModuleViewService.java`
- Modify: `src/main/resources/templates/session/modules/_encounter.html`
- Test: `src/test/java/.../session/runtime/EncounterModuleViewServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void plannedEncountersPrioritiseTheCurrentSceneAndMap() {
    var f = SessionTestFixtures.campaignWithEncountersAcrossFourChapters();

    var view = service.buildView(f.campaignId(), CockpitModuleMode.STANDARD);

    assertThat(view.plannedEncounters()).hasSizeLessThanOrEqualTo(8);
    assertThat(view.plannedEncounters().getFirst().mapId()).isEqualTo(f.currentMapId());
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=EncounterModuleViewServiceTest`
Expected: FAIL — the list is unbounded and alphabetical.

- [ ] **Step 3: Sort and cap the list**

Order by: encounters on the current map first, then encounters in the current chapter, then the rest by chapter order. Cap at 8 with a "Show all" disclosure.

- [ ] **Step 4: Add a filter input and fix the copy**

In `_encounter.html`, add an Alpine-filtered text input above the list, and fix the pluralisation:

```html
<span x-text="e.combatantCount + (e.combatantCount === 1 ? ' combatant' : ' combatants')"></span>
```

Give the readiness badge a `title` explaining the rule, e.g. `title="Every combatant has a statblock and a map placement"` — read `EncounterReadiness` for the real condition and use its wording.

- [ ] **Step 5: Collapse the list during live combat**

Wrap the planned list in `<details>` that is closed when an encounter is active, so the tracker owns the rail mid-fight:

```html
<details class="planned-encounters" :open="!encounter">
  <summary>Planned encounters</summary>
  ...
</details>
```

- [ ] **Step 6: Run the test**

Run: `./mvnw test -Dtest=EncounterModuleViewServiceTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: scope planned encounters to the current scene and map"
```

### Task 3.3: Sync HP from the tracker to the map module

After 6 damage the tracker read 4/10 while the map module's "Encounter participants" list and the map token label both still read 10/10, live, on the same screen. `combat-tracker.js:757` already dispatches `tracker-encounter-state` with the full combatant array; `battle-map.js:220` receives it but uses it for conditions only, and `_map-module.html:42` binds to a `tokens` array that nothing updates.

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js:220-235`
- Modify: `src/main/resources/templates/session/_map-module.html` (Alpine component init)

**Interfaces:**
- Consumes: `tracker-encounter-state` event, `detail.combatants[] = { id, placementId, currentHp, maxHp, defeated, conditions[] }`.
- Produces: no new events.

- [ ] **Step 1: Reproduce**

Start a session, run an encounter, damage a combatant with the `±HP` field, and compare the tracker's number against the map module's participant row and the token label. Expected: they disagree until reload.

- [ ] **Step 2: Extend the existing handler to carry HP**

> **Do not look up tokens by `t.id`.** `tokenKey(token)` is `` `${token.source}:${token.id}` `` (`battle-map.js:283-285`), so `_combatantTokenMap[c.id]` holds a **prefixed** key like `COMBATANT:<placementId>` — never equal to any `t.id`. The original snippet did `this.tokens?.find(t => t.id === key)`, which matches nothing, and `if (!token) continue` swallows the miss silently. The map labels would never update and the task would look done while fixing nothing.
>
> Tokens carry `combatantId` directly (`battle-map.js:303`: `if (t.source === 'COMBATANT' && t.combatantId)`). Match on that.

In `battle-map.js`, inside the `tracker-encounter-state` listener (line 220) after the token map is rebuilt:

```js
for (const c of e.detail.combatants) {
  const token = this.tokens?.find(t => t.source === 'COMBATANT' && t.combatantId === c.id);
  if (!token) continue;
  token.currentHp = c.currentHp;
  token.maxHp = c.maxHp;
  token.defeated = c.defeated;
}
this.renderTokenLabels();
```

If `renderTokenLabels` does not exist, add it: redraw each token's Konva HP text from `token.currentHp/maxHp`, keyed by `this.tokenKey(token)` to match `this.tokenNodes` (see `renderTokens` at `:322`), and skip the label when either value is null.

**Prove the lookup resolves before moving on.** Temporarily log `e.detail.combatants.length` and the number of tokens actually matched; they should be equal for any combatant that has a placement. A zero match means you are still on the wrong key.

- [ ] **Step 3: Make the participants list reactive**

The list at `_map-module.html:43` (`x-for="t in tokens.filter(tk => tk.source === 'COMBATANT')"`) binds `t.currentHp` from the module's Alpine `tokens` array. Add the same listener to that component so the array is patched in place (Alpine tracks mutation of array elements it already proxies). This snippet matches on `combatantId` and is correct as written — it is the Step 2 one that was wrong:

```js
window.addEventListener('tracker-encounter-state', (e) => {
    if (!e.detail?.combatants) return;
    for (const c of e.detail.combatants) {
        const token = this.tokens.find(t => t.combatantId === c.id);
        if (!token) continue;
        token.currentHp = c.currentHp;
        token.maxHp = c.maxHp;
        token.defeated = c.defeated;
    }
});
```

- [ ] **Step 4: Distinguish the tokens**

While here, fix the four identical `Go` labels seen in the evaluation: label tokens with the combatant's disambiguated suffix (`Go 1`, `Go 2`) by taking the trailing digits of the combatant name when several tokens share a prefix.

- [ ] **Step 5: Verify**

Repeat Step 1. Expected: tracker, participants list and token label all read `4/10` immediately, with no reload.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "fix: propagate combatant HP from the tracker to the map module"
```

### Task 3.4: Add a combatant from the running tracker

The tracker's controls are `End`, `Roll unset NPCs`, `Start combat`, `Prev`/`Next`, HP, `Activate`, `Use`, `Defeat`, `Revive`, `Remove`, `Undo`. There is no Add. If an ally joins the fight or the PCs were never added, the DM must leave the cockpit for the setup page.

**Files:**
- Modify: `src/main/resources/templates/session/modules/_encounter.html`
- Modify: `src/main/resources/static/js/combat-tracker.js`
- Test: `src/test/java/.../encounter/service/EncounterServiceTest.java`

**Interfaces:**
- Consumes: `POST /api/v1/encounters/{id}/combatants` (quick add — `EncounterApiController:133`) and `POST /api/v1/encounters/{id}/prefill/party` (`:162`, returns `List<CombatantDto>`).
- Produces: nothing new for later tasks.

> The party endpoint is **`/prefill/party`**, not `/combatants/from-party` as originally written — that route does not exist. Verify with `grep -n 'PostMapping("/encounters/{id}/' src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java` before writing the fetch.

- [ ] **Step 1: Write the failing test**

```java
@Test
void combatantsCanBeAddedWhileCombatIsRunning() {
    var encounter = fixtures.encounterInRound(3);

    service.addCombatant(encounter.getId(), new QuickCombatantRequest("Sildar Hallwinter", 27, Kind.NPC));

    var reloaded = service.get(encounter.getId());
    assertThat(reloaded.getCombatants()).extracting("name").contains("Sildar Hallwinter");
    assertThat(reloaded.getRound()).isEqualTo(3);
}
```

- [ ] **Step 2: Run it**

Run: `./mvnw test -Dtest=EncounterServiceTest#combatantsCanBeAddedWhileCombatIsRunning`
Expected: FAIL if the service rejects adds after combat starts; PASS if the service already allows it, in which case this task is UI-only.

- [ ] **Step 3: Allow mid-combat adds in the service**

If it threw, permit the add and place the new combatant at the end of the current round's order with `initiative = null`, matching the existing "accepted unset combatants act after resolved initiatives" rule already stated in the setup UI.

- [ ] **Step 4: Add the tracker control**

In `_encounter.html`, above the combatant rows:

```html
<details class="tracker-add">
  <summary>Add combatant</summary>
  <form @submit.prevent="quickAdd()">
    <input type="text" x-model="addForm.name" placeholder="Name" required>
    <input type="number" x-model="addForm.maxHp" placeholder="HP" min="1">
    <select x-model="addForm.kind">
      <option value="NPC">NPC</option>
      <option value="MONSTER">Monster</option>
      <option value="PC">PC</option>
    </select>
    <button class="btn btn-sm btn-primary" type="submit">Add</button>
  </form>
  <button class="btn btn-ghost btn-sm" @click="addMissingParty()">Add missing party members</button>
</details>
```

Implement `quickAdd()` and `addMissingParty()` in `combat-tracker.js` against the endpoints above (`POST …/combatants` and `POST …/prefill/party`), refreshing tracker state on success. `prefillFromParty` already skips party members that are present, so `addMissingParty()` is a straight call — do not re-implement the diff client-side.

- [ ] **Step 5: Run the test and verify by hand**

Run: `./mvnw test -Dtest=EncounterServiceTest`
Expected: PASS. Then add an NPC mid-combat in the browser and confirm it appears in the order without disturbing the round counter.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add combatants from the running combat tracker"
```

### Task 3.5: Make campaign readiness mean something

The panel reported "Ready — Every blocker is resolved or accepted" with **zero party members**, and its 16 advisories were identical-bodied unlinked text ("Ready to seed encounter: X" / "Seed the encounter from its participants before the session.") with no way to act on them.

> **Every API this task originally named is wrong.** The real shapes, read from source:
>
> - `CampaignReadinessService.compute(ReadinessInputs inputs, Set<String> acceptedKeys)` — **not** `evaluate(inputs)`.
> - `ReadinessInputs` is a 4-component record `(scenes, presentedAssets, runtimeLinks, omissions)` with **no builder**, no `campaignId()`, no `partyMemberCount()`. Task 1.4 may already have reduced it to 3 components.
> - `ReadinessItem` is a **7-component** record `(key, category, state, title, detail, repairKind, targetId)`. The 3-arg `new ReadinessItem(title, detail, href)` in the original does not compile.
> - `CampaignReadinessReport` exposes `items()`, `sessionReady()`, `blockerCount()`, `byState(state)`, `blockerGroups()` — there is **no** `status()`, `blockers()` or `advisories()`. Use `byState(ReadinessState.BLOCKER)`.
> - The template is `templates/campaigns/_readiness.html` — **plural** `campaigns/`.
>
> **Do not add `href`.** `ReadinessItem` already carries `repairKind` + `targetId`, and `ReadinessRepairService` + `ReadinessRepairKind` exist to turn those into actions. The "Ready to seed encounter: X" advisories are already built with `ReadinessRepairKind.SEED_ENCOUNTER` and the scene id (`CampaignReadinessService:22-27`). The evaluation's "no way to act on them" is a **rendering** gap in the template, not a model gap — adding a parallel `href` field would duplicate working machinery.

- [ ] **Step 0: Check what the last two commits already fixed**

`a379e17c feat: make campaign readiness actionable` and `6704ffc7 fix: harden readiness action layout` landed after the evaluation. Read `templates/campaigns/_readiness.html` and confirm whether repair actions already render. If they do, Steps 4-5 below may be partly or wholly done — **verify before rewriting**, and cut what is already there.

**Files:**
- Modify: `src/main/java/.../campaign/readiness/ReadinessInputs.java` (add `int partyMemberCount` and `UUID campaignId`)
- Modify: `src/main/java/.../campaign/readiness/ReadinessInputsAssembler.java:49` (populate them; needs the party repository injected)
- Modify: `src/main/java/.../campaign/readiness/CampaignReadinessService.java` (add the party rule)
- Modify: `src/main/java/.../campaign/readiness/ReadinessCategory.java` (add a `PARTY` constant if none fits)
- Modify: `src/main/resources/templates/campaigns/_readiness.html`
- Test: `src/test/java/.../campaign/readiness/CampaignReadinessServiceTest.java`

**Interfaces:**
- Consumes: `ReadinessInputs` (already stripped of `safety` in Task 1.4). **Task 1.4 and this task both change this record** — do them in order and re-read it here.
- Produces: `ReadinessInputs` gains `campaignId` and `partyMemberCount`. `ReadinessItem` is **unchanged**.

- [ ] **Step 1: Write the failing test**

```java
@Test
void anEmptyPartyBlocksReadiness() {
    var inputs = new ReadinessInputs(CAMPAIGN_ID, 0, List.of(), List.of(), List.of());

    var report = service.compute(inputs, Set.of());

    assertThat(report.sessionReady()).isFalse();
    assertThat(report.byState(ReadinessState.BLOCKER))
            .extracting(ReadinessItem::title)
            .contains("No party members");
}

@Test
void aPopulatedPartyDoesNotBlockReadiness() {
    var inputs = new ReadinessInputs(CAMPAIGN_ID, 4, List.of(), List.of(), List.of());

    var report = service.compute(inputs, Set.of());

    assertThat(report.byState(ReadinessState.BLOCKER)).isEmpty();
}

@Test
void seedAdvisoriesCarryARepairTarget() {
    var inputs = new ReadinessInputs(
            CAMPAIGN_ID, 4, List.of(sceneNeedingSeed("Bereich 4: Steiler Durchgang")),
            List.of(), List.of());

    var report = service.compute(inputs, Set.of());

    assertThat(report.items())
            .filteredOn(i -> i.repairKind() == ReadinessRepairKind.SEED_ENCOUNTER)
            .isNotEmpty()
            .allSatisfy(i -> assertThat(i.targetId()).isNotNull());
}
```

Adjust the `ReadinessInputs` constructor arity to whatever Task 1.4 left behind. The third test should **pass already** — it pins the existing repair machinery so Step 4 renders against a contract rather than inventing one.

- [ ] **Step 2: Run them to verify the first two fail**

Run: `./mvnw test -Dtest=CampaignReadinessServiceTest`
Expected: the two party tests FAIL to compile (no such component), the seed test PASSES.

- [ ] **Step 3: Add the party count and the blocker**

Add `UUID campaignId` and `int partyMemberCount` to `ReadinessInputs`, populate them in `ReadinessInputsAssembler` (inject the party-member repository and count by campaign), and add to `CampaignReadinessService.compute`, before the scene loop:

```java
if (inputs.partyMemberCount() == 0) {
    items.add(blockerOrAccepted(
            "party:" + inputs.campaignId(), ReadinessCategory.PARTY,
            "No party members",
            "Combat, passive senses and difficulty estimates need the party roster.",
            ReadinessRepairKind.OPEN_PARTY_ROSTER, inputs.campaignId(), acceptedKeys));
}
```

Add `ReadinessCategory.PARTY` and `ReadinessRepairKind.OPEN_PARTY_ROSTER` if they do not exist, and teach `ReadinessRepairService` to route the new kind to `/campaigns/{id}/party`. Going through `blockerOrAccepted` means a DM who genuinely runs partyless can accept the blocker, consistent with every other rule.

- [ ] **Step 4: Make advisories actionable in the template**

In `templates/campaigns/_readiness.html`, render each item's repair action when `repairKind` and `targetId` are non-null, using whatever `ReadinessRepairService` already exposes for URL construction. **If Step 0 found this already done, skip.**

- [ ] **Step 5: Stop claiming "Ready" while listing work**

`CampaignReadinessReport.label()` returns `"Ready"` whenever `blockerCount() == 0`, regardless of how many non-blocker items are outstanding. Change it to name both, e.g. `Ready to run · 16 advisories`, and only say `Nothing outstanding` when `items()` is empty. Add a test for the three cases: no items, blockers present, advisories only.

- [ ] **Step 6: Run the tests**

Run: `./mvnw test -Dtest='CampaignReadinessServiceTest+*Readiness*Test'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: block readiness on an empty party and link advisories to their scenes"
```

### Task 3.6: Record encounters left running when a session ends

`SessionDraftService:216` skips any encounter without an `ENCOUNTER_ENDED` log entry, so a fight still in progress when the DM completes the session vanishes from the log with no indication. This is the only surviving part of evaluation finding #11 — the other sections were correctly empty.

**Files:**
- Modify: `src/main/java/.../session/service/SessionDraftService.java:208-240`
- Test: `src/test/java/.../session/service/SessionDraftServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void anEncounterStillRunningAtSessionEndIsRecordedAsUnfinished() {
    var session = fixtures.sessionWithCombatStartedButNotEnded("Bereich 2: Goblinwachposten");

    String draft = service.generate(session, Instant.now());

    assertThat(draft).contains("Bereich 2: Goblinwachposten");
    assertThat(draft).contains("still in progress");
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=SessionDraftServiceTest#anEncounterStillRunningAtSessionEndIsRecordedAsUnfinished`
Expected: FAIL — the encounter is skipped entirely.

- [ ] **Step 3: Emit an unfinished line instead of skipping**

The current head of the loop body (lines 216-218) is:

```java
if (evidence.stream().noneMatch(entry -> entry.getType() == CombatLogEntry.EntryType.ENCOUNTER_ENDED))
    continue;
Encounter encounter = evidence.getFirst().getEncounter();
```

Replace those three lines with the following. Note it **hoists the existing `Encounter encounter` declaration above the guard** rather than adding a second one \u2014 pasting a fresh `Encounter encounter = \u2026` while line 218 still declares it is a duplicate-variable compile error:

```java
Encounter encounter = evidence.getFirst().getEncounter();
boolean ended = evidence.stream()
        .anyMatch(entry -> entry.getType() == CombatLogEntry.EntryType.ENCOUNTER_ENDED);
if (!ended) {
    lines.add(encounter.getName() + " \u2014 still in progress at session end (round "
            + encounter.getRound() + ")");
    continue;
}
```

Use a real `\u2014` in the string if the file's other lines do; `SessionDraftService` output is Markdown read by a human, and Task 4.4 exists because `\u2026` escapes leaked to screen elsewhere. A Java source `"\u2014"` is a real em dash at compile time (unlike the HTML-attribute case in Task 4.4), so either form is correct here \u2014 just be consistent with the surrounding code.

- [ ] **Step 4: Run the tests**

Run: `./mvnw test -Dtest=SessionDraftServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "fix: record unfinished encounters in the session log"
```

### Task 3.7: Reach a combatant's statblock from the tracker

Selecting a combatant in the running tracker opens HP controls and a 15-item scrolling condition checklist, but never shows AC, attacks, speed or saves. Mid-turn the DM needs the goblin's attack bonus for the goblin *in front of them*, not a search result. Task 2.4 made statblocks reachable by search; this makes them reachable by selection.

**Files:**
- Modify: `src/main/resources/templates/session/modules/_encounter.html`
- Modify: `src/main/resources/static/js/combat-tracker.js`
- Modify: `src/main/java/.../encounter/web/EncounterApiController.java`
- Test: `src/test/java/.../encounter/web/EncounterCombatantStatblockTest.java` (create)

**Interfaces:**
- Consumes: `cockpit:show-reference` from Task 2.4 — same event, same handler registered inside `cockpitReference.init()`. Task 2.4's `revealModule` activates the tab, so this task does not need its own focus logic.
- Produces: `GET /api/v1/encounters/{eid}/combatants/{cid}/statblock` returning the combatant's source statblock id and name, or `204` when the combatant has none (a PC, an object, an ad-hoc NPC).

> **No migration is needed.** `Combatant` already has the association — `@JoinColumn(name = "statblock_id") private StatBlock statBlock;` at `encounter/data/Combatant.java:56-57`, with `getStatBlock()`/`setStatBlock()` at `:148-149`. The original Step 3 said to add one plus a `V29` Flyway migration; that would collide with the existing column. Read the entity before writing anything.

- [ ] **Step 1: Write the failing test**

```java
@Test
void aLibraryBackedCombatantExposesItsSourceStatblock() throws Exception {
    var f = EncounterTestFixtures.encounterWithGoblinFromLibrary();

    mvc.perform(get("/api/v1/encounters/{eid}/combatants/{cid}/statblock",
                    f.encounterId(), f.combatantId()))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.statblockId").value(f.statblockId().toString()))
       .andExpect(jsonPath("$.name").value("Goblin Warrior"));
}

@Test
void anAdHocCombatantHasNoStatblock() throws Exception {
    var f = EncounterTestFixtures.encounterWithQuickAddedNpc();

    mvc.perform(get("/api/v1/encounters/{eid}/combatants/{cid}/statblock",
                    f.encounterId(), f.combatantId()))
       .andExpect(status().isNoContent());
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -Dtest=EncounterCombatantStatblockTest`
Expected: FAIL with 404 — the route does not exist.

- [ ] **Step 3: Add the endpoint**

In `EncounterApiController`, add a handler that loads the combatant, reads `getStatBlock()`, and returns `{ "statblockId": …, "name": … }` — or `204 No Content` when it is null.

Confirm the field first: `grep -n "StatBlock" src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java`
Expected: the import at line 3, `@JoinColumn(name = "statblock_id")` at 56, the field at 57, accessors at 148-149. **No entity change and no migration.** If `from-library` adds are not populating it, that is the bug to fix — in `EncounterService.addFromLibrary`, which already has the id at insert time.

Route it under the existing `/api/v1` prefix. Note `EncounterApiController` keys most combatant routes on the combatant id alone (`/combatants/{id}`); `/encounters/{eid}/combatants/{cid}/statblock` is fine for readability but `/combatants/{id}/statblock` would match the file's prevailing convention better. Either is acceptable — match whichever the tests assert.

- [ ] **Step 4: Add the tracker affordance**

In `_encounter.html`, inside the selected-combatant detail panel and above the Conditions block:

```html
<button class="btn btn-ghost btn-sm"
        x-show="selected?.statblockId"
        @click="showStatblock(selected.id)">
  Statblock
</button>
```

In `combat-tracker.js`:

```js
async showStatblock(combatantId) {
    const resp = await window.dmRequest(
        `/api/v1/encounters/${this.encounterId}/combatants/${combatantId}/statblock`);
    if (resp.status === 204) return;
    const sb = await resp.json();
    window.dispatchEvent(new CustomEvent('cockpit:show-reference', {
        detail: { type: 'statblock', id: sb.statblockId, name: sb.name }
    }));
}
```

- [ ] **Step 5: Make the condition list usable**

The 15-item checklist scrolls inside a nested scroller in a 363px rail, so applying Prone means scrolling twice. Replace the raw list with a filter input plus the six conditions D&D actually applies most:

```html
<div class="condition-picker">
  <div class="condition-picker__common">
    <template x-for="c in ['Prone','Grappled','Restrained','Frightened','Poisoned','Unconscious']" :key="c">
      <button class="btn btn-ghost btn-xs"
              :class="{ 'is-active': hasCondition(selected, c) }"
              @click="toggleCondition(selected.id, c)"
              x-text="c"></button>
    </template>
  </div>
  <input type="search" x-model="conditionFilter" placeholder="Filter conditions…">
  <template x-for="c in allConditions.filter(x => x.toLowerCase().includes(conditionFilter.toLowerCase()))" :key="c">
    <label><input type="checkbox" :checked="hasCondition(selected, c)"
                  @change="toggleCondition(selected.id, c)"> <span x-text="c"></span></label>
  </template>
</div>
```

- [ ] **Step 6: Run the tests and verify by hand**

Run: `./mvnw test -Dtest=EncounterCombatantStatblockTest`
Expected: PASS.

Then start a combat, click a goblin in the tracker, press "Statblock". Expected: the Goblin Warrior statblock appears in the Reference tab with AC 15 and Scimitar +4, the URL does not change, and the tracker keeps its state. Apply Prone from the common row in one click.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: open a combatant's statblock and common conditions from the tracker"
```

---

## Phase 4 — Presentation defects

All verified by measurement during the evaluation. Cheap, high visibility.

### Task 4.1: Style `.data-table`

The class is used by 7 templates — including `encounter/detail.html` and every rollable-table surface, which are read live at the table — and is **defined in no stylesheet**. Cells render with zero padding, so the combat roster reads `MONSTER7 / 716`.

**Files:**
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Confirm the class is undefined**

Run: `grep -rn "\.data-table" src/main/resources/static/css/`
Expected: no output.

- [ ] **Step 2: Add the rules**

Append to `components.css`:

```css
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-variant-numeric: tabular-nums;
}

.data-table th,
.data-table td {
  padding: var(--space-xs) var(--space-sm);
  text-align: left;
  border-bottom: 1px solid var(--color-border);
  vertical-align: middle;
}

.data-table th {
  font-size: var(--text-xs);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  font-weight: 600;
}

.data-table tbody tr:hover {
  background: var(--color-surface-muted);
}

.data-table td:last-child,
.data-table th:last-child {
  text-align: right;
}
```

- [ ] **Step 3: Verify on all seven surfaces**

Run: `grep -rl "data-table" src/main/resources/templates/`
Open each rendered surface and confirm columns are legible and no row overflows its container.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/components.css
git commit -m "fix: style the data-table class used by seven surfaces"
```

### Task 4.2: Style scene rows and harden card actions

`.scene-row__title` (`components.css:3167`) carries layout only, so the 90-scene chapter list renders as default `#0000EE` underlined links. `.card-actions` (`components.css:115`) is `justify-content: flex-end` with no wrap, so overflow escapes leftward out of the card — measured on handouts, where card 1's "Present" landed 127px outside the card, on top of the sidebar's *Ledger* link. Task 1.4 removed the buttons that triggered it; this hardens the rule so it cannot recur.

**Files:**
- Modify: `src/main/resources/static/css/components.css:115-120, 3167-3170`

- [ ] **Step 1: Style the scene row title**

```css
.scene-row__title {
  flex: 1;
  min-width: 0;
  color: var(--color-text);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-row__title:hover,
.scene-row__title:focus-visible {
  color: var(--color-accent);
  text-decoration: underline;
}
```

- [ ] **Step 2: Harden card actions**

```css
.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
  margin-top: var(--space-md);
  justify-content: flex-end;
}
```

- [ ] **Step 3: Verify no control escapes its card at any width**

Open `/campaigns/{id}/handouts`, `/library`, `/campaigns/{id}/maps` and `/campaigns/{id}/adventures` at 1600, 1366, 1280 and 1100px and confirm every button sits inside its card. In the console:

```js
[...document.querySelectorAll('.card')].flatMap(c => {
  const cr = c.getBoundingClientRect();
  return [...c.querySelectorAll('.card-actions button, .card-actions a')]
    .filter(b => { const r = b.getBoundingClientRect();
                   return r.width > 0 && (r.left < cr.left - 2 || r.right > cr.right + 2); })
    .map(b => b.innerText.trim());
});
```

Expected: `[]` at every width.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/components.css
git commit -m "fix: style scene row links and stop card actions escaping their card"
```

### Task 4.3: Fix the roster HP bar and the initiative rail overflow

`.roster-hp-fill` (`surfaces.css:160`) is a `<span>` with no `display`, so it stays inline and `width: 100%` / `height: 100%` never apply — the roster HP bar is a permanently empty trough at every HP value. Separately the initiative block measures 368px inside a 363px rail and pushes past the viewport, clipping the initiative inputs and truncating names to `Goblin (…`.

**Files:**
- Modify: `src/main/resources/static/css/surfaces.css:160-169`
- Modify: `src/main/resources/static/css/cockpit-modules.css` (initiative rail)

- [ ] **Step 1: Fix the fill element**

```css
.roster-hp-fill {
  display: block;
  height: 100%;
  background: var(--color-success);
  border-radius: 3px;
  transition: width 200ms var(--ease-out);
}
```

`.hp-mini-fill` in the cockpit tracker is already a `<div>` and renders correctly — this brings the roster in line with it.

- [ ] **Step 2: Verify the bar tracks HP**

Open the roster with a party member at full HP, then at half. Expected: the bar fills fully, then half, and turns red via `.roster-hp-fill.low`.

- [ ] **Step 3: Contain the initiative rail**

In the initiative setup rules, let the name column shrink and keep the numeric input from being squeezed out:

```css
.initiative-setup__order,
.initiative-setup__row {
  max-width: 100%;
  box-sizing: border-box;
}

.initiative-setup__name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.initiative-setup__row input[data-initiative-input] {
  flex: 0 0 4.5rem;
  width: 4.5rem;
}
```

Apply the same `min-width: 0` treatment to `.combatant-name` so live-combat rows stop truncating to `Goblin (…`.

- [ ] **Step 4: Verify nothing exceeds the viewport**

With the Combat preset open, in the console:

```js
[...document.querySelectorAll('*')]
  .filter(e => { const r = e.getBoundingClientRect();
                 return r.width > 0 && r.right > window.innerWidth + 1; })
  .map(e => e.className);
```

Expected: `[]`. Before this task it returned `initiative-setup__header`, `initiative-setup__order`, `initiative-setup__row` and the initiative inputs at `right: 1612` against a 1600px viewport.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/css/surfaces.css src/main/resources/static/css/cockpit-modules.css
git commit -m "fix: render the roster HP bar and contain the initiative rail"
```

### Task 4.4: Fix leaked escapes, raw enums and duplicated content

**Two** literal `\u2026` sequences render as visible text \u2014 not three. `_quick-notes.html:13` and `_reference.html:9` put `\u2026` in a plain HTML `placeholder` attribute, where nothing interprets the escape, so the user sees `Quick note\u2026`. The third hit, `_story-rail.html:106`, is inside `x-text="\u2026 ? 'Creating encounter\u2026' : \u2026"` \u2014 an **Alpine JS expression**, where `'\u2026'` is a valid string escape that evaluates to `\u2026` correctly. Normalise it anyway for consistency, but do not report it as a fixed defect. Raw enum constants reach the UI in several places — `NOT_STARTED` on all 13 quests, `READ_ALOUD` as a badge, `MONSTER`/`HOSTILE` on participants. The cockpit story panel shows the same read-aloud text twice, once styled and once with a raw badge. The roster shows the same four heroes twice on one screen in two different sort orders.

**Files:**
- Modify: `templates/session/modules/_quick-notes.html:13`, `session/modules/_reference.html:9`, `session/_story-rail.html:106`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/DisplayLabels.java`
- Modify: `templates/session/_story-rail.html` (duplicate read-aloud)
- Modify: `templates/party/_roster.html` (duplicate party strip)

- [ ] **Step 1: Fix the literal escapes**

Replace `\u2026` with a real `…` in all three files — two are genuine defects, the third is normalisation (see above).

Run: `grep -rn '\\u[0-9a-fA-F]\{4\}' src/main/resources/templates/`
Expected after the fix: no output.

- [ ] **Step 2: Add a label helper**

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import java.util.Locale;

/** Turns enum constants into display copy: NOT_STARTED -> "Not started". */
public final class DisplayLabels {

    private DisplayLabels() {}

    public static String humanize(Enum<?> value) {
        if (value == null) return "\u2014";
        return humanize(value.name());
    }

    public static String humanize(String constant) {
        if (constant == null || constant.isBlank()) return "\u2014";
        String lower = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
```

- [ ] **Step 3: Write its test**

```java
class DisplayLabelsTest {
    @Test
    void humanizesScreamingSnakeCase() {
        assertThat(DisplayLabels.humanize("NOT_STARTED")).isEqualTo("Not started");
        assertThat(DisplayLabels.humanize("READ_ALOUD")).isEqualTo("Read aloud");
        assertThat(DisplayLabels.humanize((String) null)).isEqualTo("—");
    }
}
```

Run: `./mvnw test -Dtest=DisplayLabelsTest`
Expected: PASS.

- [ ] **Step 4: Apply it at every leak site**

Run: `grep -rn "NOT_STARTED\|READ_ALOUD\|IN_PROGRESS" src/main/resources/templates/`
For each, wrap the value: `th:text="${T(dev.hendrikhoemberg.dmhelper.common.web.DisplayLabels).humanize(q.status)}"`. For Alpine-rendered badges (`x-text="c.kind"`) map client-side in the component rather than printing the constant.

- [ ] **Step 5: Remove the duplicated read-aloud block**

In `_story-rail.html` the read-aloud text is emitted twice — once as the styled callout and once in the generic beat loop with a raw `READ_ALOUD` badge. Exclude `READ_ALOUD` beats from the generic loop, since the callout above already renders them.

- [ ] **Step 6: Drop the redundant party strip on the roster page**

`templates/party/_roster.html` renders the global party strip above a table containing the same four heroes with the same stats, in a different sort order. Suppress the strip when the page *is* the roster, and make both surfaces sort identically (by initiative bonus descending, then name) wherever both remain visible.

- [ ] **Step 7: Verify**

Open the cockpit, the roster and a quest list. Expected: no `\u2026`, no SCREAMING_SNAKE_CASE, one read-aloud block, one party listing.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "fix: humanize enum labels and remove duplicated cockpit content"
```

### Task 4.5: Reduce map glare and fix the recap editor

The battle map renders bright white against an otherwise dark app — the only high-luminance element on the screen, at a table that is usually dim. The session recap textarea is ~175px wide inside a 390px modal on a 1600px screen, which is unusable for writing a recap.

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js` (default background)
- Modify: `src/main/resources/static/css/cockpit.css` (review modal)

- [ ] **Step 1: Tone down the default map background**

Find the Konva background fill for a map with no image layer and change it from white to a parchment tone that sits inside the app's palette, e.g. `#d9d0bd`, with grid lines at `rgba(0,0,0,0.18)`. Maps that carry a background image are unaffected.

- [ ] **Step 2: Give the recap modal room**

```css
.session-review-modal {
  width: min(900px, 92vw);
}

.session-review-modal label {
  display: block;
  margin-bottom: var(--space-xs);
}

.session-review-modal input[type="text"],
.session-review-modal textarea {
  width: 100%;
  box-sizing: border-box;
}

.session-review-modal textarea {
  min-height: 22rem;
  font-family: var(--font-mono);
  line-height: 1.5;
}
```

Use the real class from `templates/session/_session-lifecycle.html`; add one if the modal has none.

- [ ] **Step 3: Verify**

Complete a session and confirm the draft is readable and editable full-width. Open a map with no background image and confirm it no longer glares.

- [ ] **Step 4: Run the full suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "fix: reduce battle map glare and widen the session recap editor"
```

---

## Closing: end-to-end rehearsal

### Task 5.1: Re-run the evaluation walkthrough

**Files:**
- Modify: `docs/product/all-in-one-release-gate.md`

- [ ] **Step 1: Walk the original path**

With a fresh app start and the Phandelver campaign: add a party member; open Adventures → Teil 1 → Goblin-Hinterhalt; press "Run this encounter"; roll initiative; damage a goblin and confirm tracker, participants list and token agree; press Ctrl+K and open the Goblin Warrior statblock without leaving the cockpit; reload the page and confirm the tracker survives; add an NPC mid-combat; end the encounter; complete the session and read the log.

- [ ] **Step 2: Confirm every removed surface is gone**

```bash
curl -s -o /dev/null -w "player=%{http_code}\n" http://127.0.0.1:8083/player   # 404
curl -s -o /dev/null -w "qr=%{http_code}\n" http://127.0.0.1:8083/qr/player-view # 404
```

Run: `grep -rn "tableSafe\|SafetyClassification\|PlayerView\|screen-safety\|/qr/\|PinInterceptor\|PinManager" src/main src/test`
Expected residue and nothing else: `CampaignManifestV2.java`, `campaign-format-v2.schema.json`, the three `.dmcampaign` manifests, `LegacyHandoutSafetyToleranceTest`, and `V18`/`V19`/`V28`. Anything under `src/main/java` or `src/main/resources/templates` is a miss — go back and finish it.

- [ ] **Step 2b: Confirm the loopback bind held**

Run: `ss -tlnp | grep 8083`
Expected: bound to `127.0.0.1`, not `0.0.0.0` or `*`. Task 1.5 removed the app's only authentication; this bind is what replaces it, and a rehearsal that skips this check cannot tell the difference between "secure" and "open to the LAN".

- [ ] **Step 3: Update the release gate**

Record in `docs/product/all-in-one-release-gate.md` that the player view, table presentation and handout classification are removed as of this change, and that the gate's player-safety criteria no longer apply. **Add a new criterion in their place:** the app ships with no authentication layer and must bind to loopback; any change to `server.address` is a security decision requiring explicit sign-off.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: record the DM-only scope in the release gate"
```

---

## Self-Review

**Spec coverage.** Every finding from the 2026-07-28 evaluation maps to a task: blockers #1–#5 → Tasks 2.1–2.5; table-safety #6–#10 → Phase 1 (deletion); #11 → corrected and reduced to Task 3.6; coherence gaps → Tasks 3.1–3.7; presentation defects → Tasks 4.1–4.5. Note that Tasks 2.4 and 3.7 both end in the Reference module but solve different problems — 2.4 is reaching a statblock *by search*, 3.7 is reaching the statblock of the combatant *already selected in the tracker*. Neither subsumes the other. The user's two decisions — full cut including table-safe mode, deletion sequenced first — are reflected in Phase 1 covering all of `live/`, presentation, classification, table-safe and PIN/QR before any fix work begins.

**Known risk not yet resolved.** Task 2.3 prescribes a fix for a defect whose root cause is *suspected* (the `!visible` early return at `cockpit-modules.js:150`) but not proven — and the evaluation's own evidence points elsewhere: the module set it reported for the "Combat" preset is Exploration's, not Combat's. Task 2.3 Step 1 now checks which preset actually resolved **before** instrumenting anything, and explicitly permits abandoning Steps 2-4 if the fault is in layout persistence. Treat Step 1's finding as authoritative over Step 4's patch.

**Security posture change.** Task 1.5 removes `PinInterceptor`, which — contrary to this plan's first draft — is not a LAN-device gate but the application's only access control (there is no Spring Security). The compensating control is `server.address=127.0.0.1`, added in the same task and re-verified in Task 5.1 Step 2b. If that bind is ever removed, the app is unauthenticated on the network. This was a deliberate decision, not a side effect.

**Schema risk.** Task 1.4 is the only irreversible step. It drops columns under `ddl-auto=validate`, so the migration and entity edit must land in one commit, and Step 7 takes a DB backup first. `LegacyHandoutSafetyToleranceTest` from Task 0.1 is the guard that keeps existing `.dmcampaign` packages importable; if it fails, the fix is in the DTO annotations, never in the test. Note the guard must cover **three** retired DTO fields (`safetyClassification`, `derivativeRecipe`, `sourceRef`) and **both** the validator and the deserialisation path — an earlier draft covered one field and one path.

**Type consistency.** Verified against source, not assumed:

- `ReadinessInputs` is touched **twice** — Task 1.4 removes `AssetInput.safety` (and possibly `presentedAssets` entirely), Task 3.5 adds `campaignId` and `partyMemberCount`. Run them in order and re-read the record at the start of 3.5.
- `ReadinessItem` is **not** modified by any task. Its existing `repairKind` + `targetId` are the actionability mechanism; do not add `href`.
- The story view record gains `linkedEncounterId` in Task 3.1 only.
- `tracker-encounter-state`'s payload shape is read, not changed, in Task 3.3 — but it must be read by `combatantId`, never by `t.id`, because `tokenKey()` prefixes the source (`COMBATANT:<id>`).
- `CockpitModuleDefinition` loses `screenSafety` in Task 1.3, before Tasks 2.4 and 3.2 touch the registry and presets.
- `Combatant.statBlock` already exists — Task 3.7 needs no entity change and no `V29`. `V28` remains the only migration in this plan.

**Verified-absent APIs.** These were referenced by earlier drafts and do not exist. If you find yourself writing one, you are working from a stale copy: `cockpit:focus-module`, `data-cockpit-root`, `ReadinessInputs.builder()`, `CampaignReadinessService.evaluate()`, `CampaignReadinessReport.status()/blockers()/advisories()`, `POST /api/v1/encounters/{id}/combatants/from-party`, `DELETE /campaigns/{cid}/encounters/{eid}/combatants/{id}`, `cockpitReference.showStatblock()`, `ReadinessInputs.HandoutAsset`, `src/test/resources/campaigns/v2/minimal.dmcampaign/`.
