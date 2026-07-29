# Cockpit DM-Run Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the twelve defects found while running "Die Verlorene Mine von Phandelver" end-to-end as a DM, so that opening the cockpit at the table produces a working map, tracker, and readable scene text.

**Architecture:** Three P0 defects are independent single-file root causes (a client mount-ordering bug in `cockpit-modules.js`, a non-exhaustive `switch` in `SessionEncounterService`, and a `max-height` clamp in `cockpit.css`). The remaining nine are localised template/CSS/service changes. Every task is independently shippable and ends with a passing test; no task depends on another task's code.

**Tech Stack:** Spring Boot 4.1 (Java 25), Thymeleaf, Alpine.js, Konva, H2 + Flyway, JUnit 5 + AssertJ, Playwright 1.54 (Java) for browser tests.

## Global Constraints

- Java version: 25. Build with `./mvnw`, never a system `mvn`.
- Run tests with plain `./mvnw test` — the surefire `argLine` already wires the Mockito agent.
- Schema is owned by Flyway (`src/main/resources/db/migration/`); `spring.jpa.hibernate.ddl-auto=validate`. **No task in this plan changes the schema.** If you think you need a migration, you have misread the task.
- Browser tests run under `@ActiveProfiles("playwright")` with `@SpringBootTest(webEnvironment = RANDOM_PORT)`. Follow the existing setup in `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`.
- New browser tests MUST attach `BrowserFailureCollector` via the existing `guardedPage(context)` helper pattern so console errors and HTTP 4xx/5xx fail the test.
- Server binds to `127.0.0.1` only; `server.port=8081` in dev. Do not change either.
- User-facing copy is **English**; campaign *content* is whatever language the package uses (German here). Never translate campaign content.
- The gold accent (`btn-primary`) marks exactly one primary action per surface. Do not add a second gold button to a surface that already has one.
- Commit after every task with a Conventional Commits prefix (`fix:`, `feat:`, `docs:`, `test:`).
- **Shared test fixture:** Tasks 1, 2, 5, 7, 10 and 11 all use `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java`. **Task 1 creates it**; each later task appends one method. If you execute tasks out of order, create the class from the code in Task 1 Step 1 first. This is the only cross-task file dependency in the plan.

---

## File Structure

| File | Responsibility | Tasks |
|---|---|---|
| `src/main/resources/static/js/cockpit-modules.js` | Lazy module fetch lifecycle | 1 |
| `src/main/java/.../session/service/SessionEncounterService.java` | Encounter activation dispositions | 2 |
| `src/main/java/.../encounter/service/EncounterService.java` | Encounter status transitions | 2 |
| `src/main/resources/static/js/session-cockpit.js` | Cockpit Alpine store, error surfacing | 2, 9 |
| `src/main/resources/static/css/cockpit.css` | Scene card typography and clamps | 3 |
| `src/main/resources/templates/session/_story-rail.html` | Story module markup | 3 |
| `src/main/resources/templates/session/_map-module.html` | Workspace map picker | 4 |
| `src/main/resources/static/css/components.css` | Party summary bar | 5, 8 |
| `src/main/resources/templates/party/_summary-bar.html` | Party chip markup | 8 |
| `src/main/resources/templates/encounter/_tracker.html` | Initiative setup | 6 |
| `src/main/java/.../campaign/readiness/` | Readiness advisories + bulk repair | 7 |
| `src/main/resources/templates/campaigns/_readiness.html` | Readiness panel markup | 7 |
| `src/main/resources/templates/fragments/_dice-roller.html` | Dice drawer | 9 |
| `src/main/resources/static/css/cockpit-layout.css` | Zone sizing | 10 |
| `src/main/java/.../session/service/SessionLifecycleService.java` | Session lifecycle | 11 |
| `docs/dm-manual/03-session-cockpit.md` | DM manual | 12 |

---

## Task 1: Cockpit modules never fetch their bodies on page load

**Severity: P0.** Reopening the cockpit on any preset other than Exploration leaves the Map, Encounter, Reference, Audio and Session-log modules permanently blank — no error, no empty state, no retry. Only a preset toggle repairs it.

**Root cause.** `_cockpit-workbench.html` server-renders `initialBody` for `session-plan`, `story`, `party` and `quick-notes` only (lines 35, 64, 99, 108); the other five pass `initialBody=${null}` (lines 143, 152, 162, 165, 168) and ship with `data-module-loaded="false"` and an empty content div. Those five depend entirely on the client fetch. In `cockpit-modules.js`, `mount()` calls `registerListeners()` *after* `cockpit-layout.js` (loaded first, both `defer`) has already run `emitAllVisibility()` and dispatched `cockpit:layout-applied` synchronously during its own mount. So every visibility event was missed, `this.stale` is still empty, and `flushStale()` at line 42 iterates an empty set. Zero `/session/modules/` requests are issued.

**Blast radius:** Combat (Map + Encounter), Theatre of Mind (Encounter), Session Review (Session log), and any custom preset containing those modules.

**Files:**
- Modify: `src/main/resources/static/js/cockpit-modules.js:21-50`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleInitialLoadBrowserTest.java` (create)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleClientContractTest.java:12` (modify)

**Interfaces:**
- Consumes: existing `this._shells` (Map of moduleKey → shell element, populated by `discoverShells()`), `this.stale` (Set), `this.loaded` (Set), `this.isModuleVisible(key)`, `this.load(key, {force})`.
- Produces: `CockpitModuleController.prototype.seedInitialLoads()` — no arguments, returns `undefined`. Marks every shell whose `[data-module-content]` carries `data-module-loaded="false"` as stale, and records the already-server-rendered ones in `this.loaded`.

- [ ] **Step 1: Write the failing browser test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleInitialLoadBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CockpitModuleInitialLoadBrowserTest {

    @LocalServerPort private int port;

    @Autowired private CockpitInitialLoadFixtures fixtures;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private final BrowserFailureCollector failures = new BrowserFailureCollector();

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        failures.assertNoFailures();
        if (context != null) context.close();
    }

    @Test
    void mapAndEncounterModulesLoadOnFirstPaintWhenCombatPresetIsRestored() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        String url = "http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session";

        // 1. Arrive, switch to Combat so the preset is persisted to localStorage.
        page.navigate(url);
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => window.cockpitLayout.currentPresetKey === 'builtin:combat'");

        // 2. Reload. This is the DM reopening the cockpit at the table.
        List<String> moduleRequests = new ArrayList<>();
        page.onRequest(request -> {
            if (request.url().contains("/session/modules/")) moduleRequests.add(request.url());
        });
        page.reload();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");

        page.waitForFunction(
                "() => document.querySelector('[data-runtime-module=\"map\"] "
                        + "[data-module-content]')?.getAttribute('data-module-loaded') === 'true'");
        page.waitForFunction(
                "() => document.querySelector('[data-runtime-module=\"encounter\"] "
                        + "[data-module-content]')?.getAttribute('data-module-loaded') === 'true'");

        assertThat(moduleRequests)
                .as("map and encounter must fetch their bodies on first paint, not after a preset toggle")
                .anyMatch(u -> u.contains("/session/modules/map"))
                .anyMatch(u -> u.contains("/session/modules/encounter"));

        int mapBodyLength = (int) page.evaluate(
                "() => document.querySelector('[data-runtime-module=\"map\"] "
                        + ".cockpit-module__body').innerText.trim().length");
        int encounterBodyLength = (int) page.evaluate(
                "() => document.querySelector('[data-runtime-module=\"encounter\"] "
                        + ".cockpit-module__body').innerText.trim().length");
        assertThat(mapBodyLength).as("map module body").isGreaterThan(0);
        assertThat(encounterBodyLength).as("encounter module body").isGreaterThan(0);
    }
}
```

Create the fixture helper `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Builds the smallest campaign that renders a full cockpit with a workspace map. */
@Component
public class CockpitInitialLoadFixtures {

    private final CampaignRepository campaigns;
    private final GameMapRepository maps;
    private final SessionLifecycleService lifecycle;

    public CockpitInitialLoadFixtures(CampaignRepository campaigns,
                                      GameMapRepository maps,
                                      SessionLifecycleService lifecycle) {
        this.campaigns = campaigns;
        this.maps = maps;
        this.lifecycle = lifecycle;
    }

    @Transactional
    public UUID campaignWithRunningSession() {
        Campaign campaign = new Campaign();
        campaign.setName("Initial Load Fixture");
        campaigns.save(campaign);

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Fixture Map");
        maps.save(map);

        lifecycle.start(campaign.getId(), map.getId());
        return campaign.getId();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=CockpitModuleInitialLoadBrowserTest`
Expected: FAIL. The `page.waitForFunction` on `data-module-loaded === 'true'` for `map` times out, because zero `/session/modules/` requests are issued on load.

- [ ] **Step 3: Add `seedInitialLoads()` and call it from `mount()`**

In `src/main/resources/static/js/cockpit-modules.js`, replace `mount()` (currently lines 21-40) with:

```js
    mount() {
      this.discoverShells();
      this.registerListeners();
      this.seedInitialLoads();

      // The layout controller boots before this one and dispatches cockpit:layout-applied
      // synchronously during its own mount, so that event has usually already fired by now.
      // Fall back to the layout controller's own mounted flag rather than waiting forever.
      if (this._layoutApplied || window.cockpitLayout?.mounted) {
        this._layoutApplied = true;
        this._mounted = true;
        this.flushStale();
        return;
      }

      window.addEventListener('cockpit:layout-applied', () => {
        this._layoutApplied = true;
        this._mounted = true;
        this.flushStale();
      }, { once: true });
    }

    // The layout controller has already emitted every visibility event by the time this
    // controller registers its listeners, so nothing else will ever mark a module stale on
    // first paint. Server-rendered bodies (initialBody != null in _cockpit-workbench.html)
    // count as loaded; everything else must be queued or its panel stays blank until the
    // DM toggles presets.
    seedInitialLoads() {
      for (const [key, shell] of this._shells) {
        const content = shell.querySelector('[data-module-content]');
        if (content && content.getAttribute('data-module-loaded') === 'true') {
          this.loaded.add(key);
          continue;
        }
        this.stale.add(key);
      }
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=CockpitModuleInitialLoadBrowserTest`
Expected: PASS.

- [ ] **Step 5: Extend the source contract test**

In `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleClientContractTest.java`, add `"seedInitialLoads"` to the existing `assertThat(js).contains(...)` argument list (after `"cockpit:layout-applied"`).

- [ ] **Step 6: Run the full cockpit suite to check for regressions**

Run: `./mvnw test -Dtest='Cockpit*Test,CoreSessionLoopSmokeTest'`
Expected: PASS. Pay attention to `CockpitHydrationTest` — it asserts hydration counts and may need its expected request count raised now that first paint issues real fetches.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/cockpit-modules.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleInitialLoadBrowserTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleClientContractTest.java
git commit -m "fix: load cockpit modules on first paint instead of only after a preset toggle"
```

---

## Task 2: Running a finished encounter returns HTTP 500 with no visible error

**Severity: P0.** `SessionEncounterService.activate` (line 75-79) switches on the requested encounter's status:

```java
Encounter activated = switch (requested.getStatus()) {
    case ACTIVE -> requested;
    case SUSPENDED -> encounterService.resume(encounterId);
    default -> encounterService.activateFresh(encounterId);
};
```

`Encounter.Status` is `{ PLANNED, ACTIVE, SUSPENDED, DONE }`. A `DONE` encounter falls into `default` and hits `EncounterService.activateFresh` (line 413), which throws `IllegalStateException("Only planned encounters can be activated fresh")`. The `GlobalExceptionHandler` logs "Unhandled request failure" and returns 500. Re-running a finished encounter is ordinary DM behaviour (the party retreated and came back).

Client-side, `confirmSuspendCurrent()` (`session-cockpit.js:579`) awaits `activateEncounter` with no `catch`, so the rejection escapes as an unhandled Alpine expression error. The DM's only feedback is the top-bar status chip flipping to "Not saved".

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java:413-423`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterService.java:75-79`
- Modify: `src/main/resources/static/js/session-cockpit.js:579-591`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterReactivationTest.java` (create)

**Interfaces:**
- Consumes: `Encounter.Status` enum `{ PLANNED, ACTIVE, SUSPENDED, DONE }`; `EncounterService.resume(UUID)`, `EncounterService.suspend(UUID)`, `EncounterService.activateFresh(UUID)`, all returning `Encounter`.
- Produces: `EncounterService.reopen(UUID encounterId)` returning `Encounter` — moves a `DONE` encounter back to `ACTIVE`, resets `round` to 1, and throws `IllegalStateException` for any other status.
- Produces: `SessionEncounterService.activate` becomes exhaustive over `Encounter.Status` with no `default` branch, so a future status value breaks the build instead of 500ing at runtime.

- [ ] **Step 1: Write the failing service test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterReactivationTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionEncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SessionEncounterReactivationTest {

    @Autowired private SessionEncounterService sessionEncounters;
    @Autowired private EncounterService encounters;
    @Autowired private EncounterRepository encounterRepo;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    @Test
    void reRunningAFinishedEncounterReactivatesItInsteadOfThrowing() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        UUID encounterId = fixtures.plannedEncounterWithOneCombatant(campaignId);

        sessionEncounters.activate(campaignId, encounterId, null);
        encounters.endEncounter(encounterId);
        assertThat(encounterRepo.findById(encounterId).orElseThrow().getStatus())
                .isEqualTo(Encounter.Status.DONE);

        var result = sessionEncounters.activate(campaignId, encounterId, null);

        assertThat(result.status()).isEqualTo("ACTIVE");
        Encounter reopened = encounterRepo.findById(encounterId).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(Encounter.Status.ACTIVE);
        assertThat(reopened.getRound()).isEqualTo(1);
    }
}
```

Add this method to `CockpitInitialLoadFixtures` (created in Task 1 — if you are doing Task 2 first, create that class now using the code in Task 1 Step 1):

```java
    @Transactional
    public UUID plannedEncounterWithOneCombatant(UUID campaignId) {
        Campaign campaign = campaigns.findById(campaignId).orElseThrow();
        GameMap map = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst();
        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setMap(map);
        encounter.setName("Reactivation Fixture");
        encounter.setStatus(Encounter.Status.PLANNED);
        encounters.save(encounter);

        Combatant goblin = new Combatant();
        goblin.setEncounter(encounter);
        goblin.setName("Goblin");
        goblin.setMaxHp(7);
        goblin.setCurrentHp(7);
        combatants.save(goblin);
        return encounter.getId();
    }
```

with the matching `EncounterRepository encounters` and `CombatantRepository combatants` constructor injections and imports (`dev.hendrikhoemberg.dmhelper.encounter.data.Encounter`, `...data.EncounterRepository`, `...data.Combatant`, `...data.CombatantRepository`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SessionEncounterReactivationTest`
Expected: FAIL with `java.lang.IllegalStateException: Only planned encounters can be activated fresh`.

- [ ] **Step 3: Add `EncounterService.reopen`**

In `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`, immediately after `activateFresh` (which ends at line 423), add:

```java
    /**
     * Puts a finished encounter back into play. A DM re-runs a fight when the party retreats
     * and returns, so DONE is a resumable state rather than a terminal one. The combat log is
     * kept: the end-review draft replays it and reports the latest state per combatant.
     */
    public Encounter reopen(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        if (e.getStatus() != Encounter.Status.DONE) {
            throw new IllegalStateException("Only finished encounters can be reopened");
        }
        e.setStatus(Encounter.Status.ACTIVE);
        e.setRound(1);
        encounterRepo.save(e);
        placementService.autoPlaceUnplaced(encounterId);
        return e;
    }
```

- [ ] **Step 4: Make the activation switch exhaustive**

In `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterService.java`, replace lines 75-79 with:

```java
        // Exhaustive on purpose: no default branch, so adding a status to Encounter.Status
        // becomes a compile error here instead of a 500 at the table.
        Encounter activated = switch (requested.getStatus()) {
            case ACTIVE -> requested;
            case SUSPENDED -> encounterService.resume(encounterId);
            case PLANNED -> encounterService.activateFresh(encounterId);
            case DONE -> encounterService.reopen(encounterId);
        };
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SessionEncounterReactivationTest`
Expected: PASS.

- [ ] **Step 6: Surface activation failures in the cockpit**

In `src/main/resources/static/js/session-cockpit.js`, replace `confirmSuspendCurrent` and `confirmEndCurrent` (lines 579-591) with:

```js
        async confirmSuspendCurrent() {
            const pendingId = this._replacementPendingId;
            this.closeReplacementDialog();
            if (!pendingId) return;
            await this._activateOrNotify(pendingId, 'SUSPEND');
        },

        async confirmEndCurrent() {
            const pendingId = this._replacementPendingId;
            this.closeReplacementDialog();
            if (!pendingId) return;
            await this._activateOrNotify(pendingId, 'END');
        },

        // A rejected activation used to escape as an unhandled Alpine expression error: the
        // dialog closed, nothing loaded, and the only clue was the status chip reading
        // "Not saved". Failures must reach the DM as words.
        async _activateOrNotify(encounterId, disposition) {
            try {
                await this.activateEncounter(encounterId, disposition);
            } catch (error) {
                const detail = error?.problem?.detail
                    || 'The encounter could not be started. Nothing was changed.';
                window.cockpitLayout?.showNotice(detail);
            }
        },
```

- [ ] **Step 7: Run the browser smoke suite**

Run: `./mvnw test -Dtest='CoreSessionLoopSmokeTest,EncounterRunThroughTest,EncounterServiceTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterService.java \
        src/main/resources/static/js/session-cockpit.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterReactivationTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java
git commit -m "fix: reopen finished encounters instead of failing activation with a 500"
```

---

## Task 3: Scene notes are clipped at 6rem with no way to read the rest

**Severity: P0.** `cockpit.css:259-265`:

```css
.scene-body {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  margin: var(--space-xs) 0;
  max-height: 6rem;
  overflow: hidden;
}
```

Measured in the **Exploration** preset, where Story is `STANDARD` in the primary zone (Cragmaw-Versteck: Übersicht): `clientHeight 96`, `scrollHeight 168`, `hiddenChars 845`. **43% of the block is unreachable** — and the hidden tail is exactly the mechanical detail a DM needs mid-scene: *"Wurf auf Weisheit (Wahrnehmung) gegen SG 15 … Stalagmiten: können Deckung bieten. Bach: nur ca. 60 Zentimeter tief"*. `overflow: hidden` means no scrollbar and no wheel scrolling (`scrollTop` stays 0 after a wheel event over the element), the element has no `tabindex`, and `_story-rail.html:18` renders no expand control. Meanwhile ~190px of empty panel sits directly below the clipped block.

> **Note on a larger number you may see in the run report:** a `scrollHeight` of 681 was also measured for this block in the Combat preset's narrow left rail. That reading is an artifact of the Task 1 bug — Combat declares `story` in its `compactModuleKeys` (`CockpitBuiltInPresetCatalog.java:20`), so once module loading is fixed the Combat rail renders Story in `COMPACT`, which drops `.scene-body` entirely. Do not use 681 to size this fix; the primary-zone `STANDARD` case above is the real one.

`.cockpit-module__body` already declares `flex: 1 1 auto; overflow: auto` (`cockpit-layout.css:220-223`), so the module scrolls internally once the clamp is gone — which is exactly the documented workbench contract ("the document itself does not scroll; each module scrolls internally").

**Files:**
- Modify: `src/main/resources/static/css/cockpit.css:259-265`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/StoryRailSceneBodyContractTest.java` (create)

**Interfaces:**
- Consumes: `.cockpit-module__body { overflow: auto }` from `cockpit-layout.css:220`.
- Produces: no new symbols. `.scene-body` no longer constrains its own height; the module body owns scrolling.

- [ ] **Step 1: Write the failing contract test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/StoryRailSceneBodyContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StoryRailSceneBodyContractTest {

    /**
     * A DM reads scene notes mid-session. Clamping .scene-body to a fixed height with
     * overflow:hidden makes the tail unreachable: no scrollbar, no wheel scrolling, no
     * tabindex, and the story rail renders no expand control. The module body already
     * scrolls (cockpit-layout.css .cockpit-module__body { overflow: auto }), so the
     * clamp must not come back.
     */
    @Test
    void sceneBodyIsNotHeightClamped() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/static/css/cockpit.css"));
        Matcher rule = Pattern.compile("\\.scene-body\\s*\\{([^}]*)}").matcher(css);

        assertThat(rule.find()).as(".scene-body rule must exist in cockpit.css").isTrue();
        String body = rule.group(1);

        assertThat(body)
                .as(".scene-body must not clamp its height — the tail becomes unreachable")
                .doesNotContain("max-height")
                .doesNotContain("overflow: hidden")
                .doesNotContain("overflow:hidden")
                .doesNotContain("-webkit-line-clamp");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=StoryRailSceneBodyContractTest`
Expected: FAIL — the rule still contains `max-height` and `overflow: hidden`.

- [ ] **Step 3: Remove the clamp**

In `src/main/resources/static/css/cockpit.css`, replace lines 259-265 with:

```css
/* No height clamp: the module body owns scrolling (.cockpit-module__body has
   overflow:auto), and a clamp here hides DM tactics text behind no affordance at all —
   overflow:hidden gives no scrollbar, no wheel scrolling, and the rail renders no
   expand control. */
.scene-body {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  margin: var(--space-xs) 0;
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=StoryRailSceneBodyContractTest`
Expected: PASS.

- [ ] **Step 5: Verify no visual gate regressed**

Run: `./mvnw test -Dtest='SurfaceNestingGateTest,TypographyRenderGateTest,ViewportAccessibilityGateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/css/cockpit.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/StoryRailSceneBodyContractTest.java
git commit -m "fix: stop clipping scene notes at 6rem in the story rail"
```

---

## Task 4: Workspace map picker reads "No map" while a map is rendered

**Severity: P1.** `_map-module.html:14` binds the select with Alpine's `:value`:

```html
<select id="runtimeMapPicker" aria-label="Workspace map" :value="currentMapId"
        @change="switchMap($event.target.value)">
  <option value="" :disabled="sessionStatus === 'IDLE' && !!currentMapId">No map</option>
  <template x-for="m in maps" :key="m.id">
    <option :value="m.id" x-text="m.name"></option>
  </template>
</select>
```

`:value` on a `<select>` sets the property before `x-for` has produced the `<option>` elements, so the assignment finds no matching option and the select falls back to the first one — "No map". Observed live: picker `value=""` / "No map" while 19 tokens rendered on a 6-layer Konva stage. The DM cannot tell which map is on screen, and the control looks broken.

`x-model` is Alpine's supported binding for selects and re-syncs after `x-for` renders.

**Files:**
- Modify: `src/main/resources/templates/session/_map-module.html:13-20`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitMapContractTest.java` (modify — add one test method)

**Interfaces:**
- Consumes: Alpine store properties `currentMapId` (string, `session-cockpit.js:15`), `maps` (array of `{id, name}`), `sessionStatus` (string), and the method `switchMap(mapId)` (`session-cockpit.js:874`).
- Produces: no new symbols. The picker's rendered `selected` option now tracks `currentMapId`.

- [ ] **Step 1: Write the failing contract test**

Add to `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitMapContractTest.java`:

```java
    /**
     * Alpine's :value on a <select> is evaluated before x-for has produced the <option>
     * elements, so the assignment finds no match and the picker falls back to "No map"
     * while a map is plainly rendered. x-model re-syncs after the options exist.
     */
    @Test
    void workspaceMapPickerUsesXModelSoItReflectsTheLoadedMap() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/session/_map-module.html"));

        assertThat(html)
                .as("the workspace map picker must bind with x-model")
                .contains("x-model=\"currentMapId\"");
        assertThat(html)
                .as(":value on a select silently loses the selection when options come from x-for")
                .doesNotContain(":value=\"currentMapId\"");
    }
```

Ensure the file imports `java.io.IOException`, `java.nio.file.Files`, `java.nio.file.Path` and `static org.assertj.core.api.Assertions.assertThat`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SessionCockpitMapContractTest`
Expected: FAIL — the template still uses `:value="currentMapId"`.

- [ ] **Step 3: Switch the picker to `x-model`**

In `src/main/resources/templates/session/_map-module.html`, replace lines 13-20 with:

```html
          <label for="runtimeMapPicker" class="sr-only">Workspace map</label>
          <select id="runtimeMapPicker" aria-label="Workspace map" x-model="currentMapId"
                  @change="switchMap($event.target.value)">
            <option value="" :disabled="sessionStatus === 'IDLE' && !!currentMapId">No map</option>
            <template x-for="m in maps" :key="m.id">
              <option :value="m.id" x-text="m.name"></option>
            </template>
          </select>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SessionCockpitMapContractTest`
Expected: PASS.

- [ ] **Step 5: Verify the picker in a real browser**

Run: `./mvnw test -Dtest=CoreSessionLoopSmokeTest`
Expected: PASS. The smoke test drives map switching; a broken `x-model` binding would surface as a failed map switch.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/session/_map-module.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitMapContractTest.java
git commit -m "fix: bind the workspace map picker with x-model so it shows the loaded map"
```

---

## Task 5: First party chip is indented by the inline "PARTY" label

**Severity: P1.** `components.css:506-516` makes the cockpit copy of the bar wrap:

```css
.cockpit-module .party-summary-bar {
  margin-bottom: 0;
  border: none;
  border-radius: 0;
  min-height: 0;
  height: 100%;
  align-content: flex-start;
  flex-wrap: wrap;
  overflow: auto;
  overscroll-behavior: contain;
}
```

`.bar-label` ("PARTY", `_summary-bar.html:5`) is the first flex item and shares the first wrapped line with the first chip. Measured: chip 1 at `x:1298`, chips 2-4 at `x:1236` — a 62px indent on the first member only. Inside the cockpit the label is redundant anyway: the module header already reads "Party".

**Files:**
- Modify: `src/main/resources/static/css/components.css:506-516`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/PartyRailAlignmentBrowserTest.java` (create)

**Interfaces:**
- Consumes: `.cockpit-module .party-summary-bar` (existing), `.party-summary-bar .bar-label` (existing, `components.css:499`), `.party-member-chip` (existing).
- Produces: no new symbols.

- [ ] **Step 1: Write the failing browser test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/PartyRailAlignmentBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartyRailAlignmentBrowserTest {

    @LocalServerPort private int port;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    void everyPartyChipSharesTheSameLeftEdgeInTheNarrowRail() {
        UUID campaignId = fixtures.campaignWithRunningSessionAndFourPartyMembers();

        BrowserContext context = browser.newContext();
        Page page = context.newPage();
        page.setViewportSize(1600, 1000);
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForSelector(".runtime-party .party-member-chip");

        var chips = page.locator(".runtime-party .party-member-chip").all();
        assertThat(chips).as("fixture must render four chips").hasSize(4);

        double firstLeft = chips.getFirst().boundingBox().x;
        for (int i = 1; i < chips.size(); i++) {
            BoundingBox box = chips.get(i).boundingBox();
            assertThat(box.x)
                    .as("party chip %d must align with the first chip", i)
                    .isCloseTo(firstLeft, org.assertj.core.data.Offset.offset(1.0));
        }
        context.close();
    }
}
```

Add to `CockpitInitialLoadFixtures`:

```java
    @Transactional
    public UUID campaignWithRunningSessionAndFourPartyMembers() {
        UUID campaignId = campaignWithRunningSession();
        Campaign campaign = campaigns.findById(campaignId).orElseThrow();
        for (String name : new String[] {"Nym", "Lyra", "Grimm", "Thorin"}) {
            PartyMember member = new PartyMember();
            member.setCampaign(campaign);
            member.setName(name);
            member.setAc(14);
            member.setMaxHp(10);
            member.setCurrentHp(10);
            member.setPassivePerception(12);
            partyMembers.save(member);
        }
        return campaignId;
    }
```

with a `PartyMemberRepository partyMembers` constructor injection and the `dev.hendrikhoemberg.dmhelper.party.data.PartyMember` / `...PartyMemberRepository` imports.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=PartyRailAlignmentBrowserTest`
Expected: FAIL — chip 1 sits ~62px right of chips 2-4.

- [ ] **Step 3: Give the label its own line inside the cockpit**

In `src/main/resources/static/css/components.css`, replace lines 506-516 with:

```css
.cockpit-module .party-summary-bar {
  margin-bottom: 0;
  border: none;
  border-radius: 0;
  min-height: 0;
  height: 100%;
  align-content: flex-start;
  flex-wrap: wrap;
  overflow: auto;
  overscroll-behavior: contain;
}
/* The bar wraps in the narrow rail, and an inline label would share line one with the
   first chip and indent it alone. The module header already says "Party", so the label
   is redundant here — keep it for screen readers, take it out of the flow. */
.cockpit-module .party-summary-bar .bar-label {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=PartyRailAlignmentBrowserTest`
Expected: PASS.

- [ ] **Step 5: Confirm the campaign-home bar is untouched**

Run: `./mvnw test -Dtest='SurfaceNestingGateTest,ViewportAccessibilityGateTest'`
Expected: PASS. The new rule is scoped to `.cockpit-module`, so the horizontal bar on campaign home keeps its visible label.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/PartyRailAlignmentBrowserTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java
git commit -m "fix: stop the PARTY label indenting the first chip in the cockpit rail"
```

---

## Task 6: Initiative setup cannot add the party

**Severity: P1.** The initiative panel (`_tracker.html:33-48`) says "Enter party rolls, then roll any remaining NPCs", but an encounter seeded from a scene contains only its NPC participants — the four PCs are absent and the panel offers no way to add them. The only party-placement control in the whole cockpit is "Place missing party members" on the **map** toolbar (`_map-module.html:27`), which calls `POST /api/v1/encounters/{id}/placements/party`. A DM in Theatre of Mind (no Map module) has no path at all.

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html:36-48`
- Modify: `src/main/resources/static/js/combat-tracker.js`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/InitiativeSetupPartyContractTest.java` (create)

**Interfaces:**
- Consumes: `POST /api/v1/encounters/{encounterId}/placements/party` (existing, used by `battle-map.js:725`); Alpine tracker state `encounter` (object with `id`, `combatPhase`), `combatants` (array), `setupBusy` (boolean); existing tracker method `refresh()`.
- Produces: Alpine tracker method `addPartyToEncounter()` — no arguments, returns `Promise<void>`; POSTs the placements endpoint, then calls `refresh()`. Sets `setupBusy` while in flight.

- [ ] **Step 1: Write the failing contract test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/InitiativeSetupPartyContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InitiativeSetupPartyContractTest {

    /**
     * "Enter party rolls" is impossible when the party is not in the tracker. The only
     * party-placement control used to live on the map toolbar, which Theatre of Mind does
     * not show at all.
     */
    @Test
    void initiativeSetupOffersAnAddPartyAction() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/encounter/_tracker.html"));
        assertThat(html)
                .contains("data-add-party-to-encounter")
                .contains("addPartyToEncounter()");
    }

    @Test
    void trackerImplementsAddPartyAgainstThePlacementsEndpoint() throws IOException {
        String js = Files.readString(
                Path.of("src/main/resources/static/js/combat-tracker.js"));
        assertThat(js)
                .contains("addPartyToEncounter")
                .contains("/placements/party");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=InitiativeSetupPartyContractTest`
Expected: FAIL on both methods — neither the markup nor the method exists.

- [ ] **Step 3: Add the control to the initiative setup header**

In `src/main/resources/templates/encounter/_tracker.html`, replace lines 36-48 with:

```html
                <div class="initiative-setup__header">
                    <div>
                        <strong>Set initiative</strong>
                        <p>Enter party rolls, then roll any remaining NPCs.</p>
                    </div>
                    <button type="button"
                            class="btn btn-ghost btn-xs"
                            data-add-party-to-encounter
                            x-show="missingPartyCount > 0"
                            :disabled="setupBusy || setupSaveCount > 0"
                            @click="addPartyToEncounter()">
                        Add party
                    </button>
                    <button type="button"
                            class="btn btn-ghost btn-xs"
                            data-roll-unset-initiative
                            :disabled="setupBusy || setupSaveCount > 0 || unsetNpcCount === 0"
                            @click="rollUnsetNpcs()">
                        Roll unset NPCs
                    </button>
                </div>
```

- [ ] **Step 4: Implement the tracker method and the `missingPartyCount` getter**

In `src/main/resources/static/js/combat-tracker.js`, add to the Alpine component object (next to the existing `rollUnsetNpcs` method):

```js
        // The map toolbar's "Place missing party members" is the only other route to this
        // endpoint, and Theatre of Mind has no Map module — so the tracker needs its own.
        get missingPartyCount() {
            const placed = new Set(
                (this.combatants || [])
                    .filter(c => c.partyMemberId)
                    .map(c => c.partyMemberId));
            return (this.partyMembers || []).filter(m => !placed.has(m.id)).length;
        },

        async addPartyToEncounter() {
            if (!this.encounter?.id || this.setupBusy) return;
            this.setupBusy = true;
            try {
                await window.dmRequest(
                    `/api/v1/encounters/${this.encounter.id}/placements/party`,
                    { method: 'POST' });
                await this.refresh();
            } catch (error) {
                window.cockpitLayout?.showNotice(
                    'Could not add the party. Nothing was changed.');
            } finally {
                this.setupBusy = false;
            }
        },
```

If the component does not already carry a `partyMembers` array, populate it in the component's existing init/refresh path from `GET /api/v1/campaigns/{campaignId}/party` alongside the combatant fetch, storing the response array as `this.partyMembers`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=InitiativeSetupPartyContractTest`
Expected: PASS.

- [ ] **Step 6: Run the encounter suite**

Run: `./mvnw test -Dtest='Encounter*Test,CoreSessionLoopSmokeTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/js/combat-tracker.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/InitiativeSetupPartyContractTest.java
git commit -m "feat: add the party to an encounter straight from initiative setup"
```

---

## Task 7: Sixteen seed advisories require sixteen separate navigations

**Severity: P1.** The Phandelver package produced 16 `Ready to seed encounter` advisories. `_readiness.html:70-81` renders each with one `Open preparation` link to `/campaigns/{cid}/adventures/{aid}/scenes/{sid}` (`ReadinessRepairService:24`). Seeding all 16 means 16 round trips through the scene editor before a session. `SceneEncounterSeedService.seedFromScene(campaignId, sceneId)` already does exactly one item's work and is already exposed at `POST /campaigns/{cid}/session/scenes/{sceneId}/seed-encounter` (`SessionApiController:128`); it returns `SeedResult(encounterId, encounterName, combatantsAdded, skippedParticipants, alreadyExisted)` and is documented as safe to repeat.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/BulkSeedService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/web/ReadinessController.java`
- Modify: `src/main/resources/templates/campaigns/_readiness.html:67-69`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/BulkSeedServiceTest.java` (create)

**Interfaces:**
- Consumes: `CampaignReadinessFacade.reportForCampaign(UUID campaignId)` returning `CampaignReadinessReport`, whose `items()` yields `List<ReadinessItem>`. `ReadinessItem` is a record with accessors `key()`, `category()`, `state()`, `title()`, `detail()`, `repairKind()`, `targetId()`. Also `ReadinessRepairKind.SEED_ENCOUNTER` and `SceneEncounterSeedService.seedFromScene(UUID campaignId, UUID sceneId)` returning `SceneEncounterSeedService.SeedResult(UUID encounterId, String encounterName, int combatantsAdded, List<String> skippedParticipants, boolean alreadyExisted)`.
- Produces: `BulkSeedService.seedAll(UUID campaignId)` returning `BulkSeedService.BulkSeedResult(int scenesProcessed, int encountersSeeded, int combatantsAdded, List<String> skipped)`.

Note: `CampaignReadinessService.compute(inputs, acceptedKeys)` is a pure function over pre-assembled inputs — **use the facade, not the service**, so acknowledgements and input assembly are handled for you.

- [ ] **Step 1: Write the failing service test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/BulkSeedServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.session.CockpitInitialLoadFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BulkSeedServiceTest {

    @Autowired private BulkSeedService bulkSeed;
    @Autowired private CampaignReadinessFacade readiness;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    @Test
    void seedAllClearsEverySeedEncounterAdvisoryInOneCall() {
        UUID campaignId = fixtures.campaignWithThreeSeedableScenes();

        long before = readiness.reportForCampaign(campaignId).items().stream()
                .filter(i -> i.repairKind() == ReadinessRepairKind.SEED_ENCOUNTER)
                .count();
        assertThat(before).isEqualTo(3);

        BulkSeedService.BulkSeedResult result = bulkSeed.seedAll(campaignId);

        assertThat(result.scenesProcessed()).isEqualTo(3);
        assertThat(result.encountersSeeded()).isEqualTo(3);
        assertThat(result.combatantsAdded()).isGreaterThan(0);

        long after = readiness.reportForCampaign(campaignId).items().stream()
                .filter(i -> i.repairKind() == ReadinessRepairKind.SEED_ENCOUNTER)
                .count();
        assertThat(after).isZero();
    }

    @Test
    void seedAllIsSafeToRepeat() {
        UUID campaignId = fixtures.campaignWithThreeSeedableScenes();
        bulkSeed.seedAll(campaignId);

        BulkSeedService.BulkSeedResult second = bulkSeed.seedAll(campaignId);

        assertThat(second.encountersSeeded())
                .as("already-seeded scenes must not produce duplicate encounters")
                .isZero();
    }
}
```

Add `campaignWithThreeSeedableScenes()` to `CockpitInitialLoadFixtures`, building an adventure with one chapter and three scenes. Each scene must satisfy the exact predicate that raises the advisory (`CampaignReadinessService.java:31-36`): `hostile() && !hasLinkedEncounter() && anyParticipantHasStatblock()`. So mark each scene hostile, link **no** encounter, and give each one participant that resolves to a stat block — mirror the participant construction in `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/EncounterTestFixtures.java`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=BulkSeedServiceTest`
Expected: FAIL — `BulkSeedService` does not exist (compilation error).

- [ ] **Step 3: Implement `BulkSeedService`**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/BulkSeedService.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Seeds every scene the readiness report flags with SEED_ENCOUNTER. A freshly imported
 * published adventure raises one advisory per combat scene — sixteen for Phandelver — and
 * clearing them one navigation at a time is the slowest part of session prep.
 */
@Service
public class BulkSeedService {

    public record BulkSeedResult(int scenesProcessed, int encountersSeeded,
                                 int combatantsAdded, List<String> skipped) {}

    private final CampaignReadinessFacade readiness;
    private final SceneEncounterSeedService seeder;

    public BulkSeedService(CampaignReadinessFacade readiness,
                           SceneEncounterSeedService seeder) {
        this.readiness = readiness;
        this.seeder = seeder;
    }

    @Transactional
    public BulkSeedResult seedAll(UUID campaignId) {
        List<UUID> sceneIds = readiness.reportForCampaign(campaignId).items().stream()
                .filter(item -> item.repairKind() == ReadinessRepairKind.SEED_ENCOUNTER)
                .map(ReadinessItem::targetId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        int seeded = 0;
        int combatants = 0;
        List<String> skipped = new ArrayList<>();

        for (UUID sceneId : sceneIds) {
            SceneEncounterSeedService.SeedResult result =
                    seeder.seedFromScene(campaignId, sceneId);
            if (!result.alreadyExisted()) seeded++;
            combatants += result.combatantsAdded();
            skipped.addAll(result.skippedParticipants());
        }

        return new BulkSeedResult(sceneIds.size(), seeded, combatants, List.copyOf(skipped));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=BulkSeedServiceTest`
Expected: PASS.

- [ ] **Step 5: Expose the endpoint**

`ReadinessController` is already mapped at `@RequestMapping("/campaigns/{campaignId}/readiness")` and has a private `renderFragment(UUID, Model)` that returns `"campaigns/_readiness :: readiness"`. Add `BulkSeedService bulkSeed` as a constructor parameter and field, then add this handler next to `accept`:

```java
    @PostMapping("/seed-all")
    @Transactional
    public String seedAll(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("bulkSeedResult", bulkSeed.seedAll(campaignId));
        return renderFragment(campaignId, model);
    }
```

- [ ] **Step 6: Add the bulk control to the advisories disclosure**

In `src/main/resources/templates/campaigns/_readiness.html`, replace lines 67-69 with:

```html
    <details class="readiness-advisories" th:if="${!advisories.isEmpty()}">
      <summary th:text="|Notes &amp; advisories (${advisories.size()})|">Notes &amp; advisories</summary>
      <form th:action="@{|/campaigns/${campaignId}/readiness/seed-all|}" method="post"
            class="inline-form readiness-advisories__bulk"
            th:hx-post="@{|/campaigns/${campaignId}/readiness/seed-all|}"
            hx-target="closest .readiness-panel" hx-swap="outerHTML"
            th:if="${advisories.?[repairKind().name() == 'SEED_ENCOUNTER'].size() > 1}">
        <button type="submit" class="btn btn-ghost btn-xs">Seed all encounters</button>
      </form>
      <ul>
```

Leave the rest of the `<ul>` body and the closing tags exactly as they are.

- [ ] **Step 7: Run the readiness suite**

Run: `./mvnw test -Dtest='*Readiness*Test,BulkSeedServiceTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/BulkSeedService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/ \
        src/main/resources/templates/campaigns/_readiness.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/readiness/BulkSeedServiceTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java
git commit -m "feat: seed every flagged encounter from the readiness panel in one action"
```

---

## Task 8: Party stat abbreviations are undecipherable

**Severity: P2.** `_summary-bar.html:25-27` renders `PI 12`, `PInv 13`, `Spd 25` with no `title`, no `<abbr>`, and no legend. A live DOM sweep for `[title], abbr` across the party stats returned nothing. `PP` (line 20) has the same problem. `PI`/`PInv` are not standard D&D abbreviations.

**Files:**
- Modify: `src/main/resources/templates/party/_summary-bar.html:17-28`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/party/PartyStatAbbreviationTest.java` (create)

**Interfaces:**
- Consumes: party member view properties `ac`, `currentHp`, `maxHp`, `passivePerception`, `tempHp`, `passiveInsight`, `passiveInvestigation`, `speed`.
- Produces: no new symbols. Each abbreviated stat gains a `title` attribute with the expanded name.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/party/PartyStatAbbreviationTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.party;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PartyStatAbbreviationTest {

    /** PI / PInv / PP / Spd are not standard abbreviations; they must explain themselves. */
    @Test
    void everyAbbreviatedPartyStatCarriesAnExpandedTitle() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/party/_summary-bar.html"));
        assertThat(html)
                .contains("title=\"Armour Class\"")
                .contains("title=\"Hit Points\"")
                .contains("title=\"Passive Perception\"")
                .contains("title=\"Temporary Hit Points\"")
                .contains("title=\"Passive Insight\"")
                .contains("title=\"Passive Investigation\"")
                .contains("title=\"Speed (feet per turn)\"");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=PartyStatAbbreviationTest`
Expected: FAIL — no `title` attributes present.

- [ ] **Step 3: Add the titles**

In `src/main/resources/templates/party/_summary-bar.html`, replace lines 17-28 with:

```html
            <div class="chip-stats">
                <span title="Armour Class" th:text="'AC ' + ${m.ac}">AC 16</span>
                <span title="Hit Points" th:text="'HP ' + ${m.currentHp} + '/' + ${m.maxHp}">HP 32/32</span>
                <span title="Passive Perception" th:text="'PP ' + ${m.passivePerception}">PP 14</span>
                <span title="Temporary Hit Points" th:if="${m.tempHp > 0}" th:text="'THP ' + ${m.tempHp}">THP 0</span>
            </div>
            <th:block th:if="${mode == null or mode.name() != 'COMPACT'}">
                <div class="chip-detail">
                    <span title="Passive Insight" th:if="${m.passiveInsight > 0}" th:text="'PI ' + ${m.passiveInsight}">PI 12</span>
                    <span title="Passive Investigation" th:if="${m.passiveInvestigation > 0}" th:text="'PInv ' + ${m.passiveInvestigation}">PInv 14</span>
                    <span title="Speed (feet per turn)" th:if="${m.speed > 0}" th:text="'Spd ' + ${m.speed}">Spd 30</span>
                </div>
```

Leave the `chip-status` block and the `Sheet` link that follow exactly as they are.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=PartyStatAbbreviationTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/party/_summary-bar.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/party/PartyStatAbbreviationTest.java
git commit -m "feat: explain the abbreviated party stats with titles"
```

---

## Task 9: Dice roller has no quick-roll buttons

**Severity: P2.** The drawer offers an expression field, `Roll`, `Adv`, `Dis` and history. Every roll requires typing an expression; a DM rolls `1d20` dozens of times per session.

**Files:**
- Modify: `src/main/resources/templates/fragments/_dice-roller.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/DiceQuickRollContractTest.java` (create)

**Interfaces:**
- Consumes: the `diceRoller` Alpine component (`dice-roller.js:6`), specifically its `expression` string property (line 8) and `async roll()` method (line 41). `roll()` clears `expression` on success (line 76), so a quick-roll button that sets then rolls leaves the field empty as usual.
- Produces: no new symbols. A `.dice-quick-rolls` row of buttons that set `expression` and invoke `roll()`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/DiceQuickRollContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DiceQuickRollContractTest {

    @Test
    void diceDrawerOffersOneClickRollsForTheStandardDice() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/fragments/_dice-roller.html"));
        assertThat(html).contains("dice-quick-rolls");
        for (String die : new String[] {"d4", "d6", "d8", "d10", "d12", "d20", "d100"}) {
            assertThat(html)
                    .as("quick-roll button for %s", die)
                    .contains("data-quick-roll=\"1" + die + "\"");
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=DiceQuickRollContractTest`
Expected: FAIL — no quick-roll markup exists.

- [ ] **Step 3: Add the quick-roll row**

In `src/main/resources/templates/fragments/_dice-roller.html`, insert directly below the closing `</div>` of `.dice-input-row` and above the `Adv`/`Dis` checkboxes:

```html
    <div class="dice-quick-rolls" role="group" aria-label="Quick rolls">
      <button type="button" class="btn btn-ghost btn-xs" data-quick-roll="1d4"
              @click="expression = '1d4'; roll()">d4</button>
      <button type="button" class="btn btn-ghost btn-xs" data-quick-roll="1d6"
              @click="expression = '1d6'; roll()">d6</button>
      <button type="button" class="btn btn-ghost btn-xs" data-quick-roll="1d8"
              @click="expression = '1d8'; roll()">d8</button>
      <button type="button" class="btn btn-ghost btn-xs" data-quick-roll="1d10"
              @click="expression = '1d10'; roll()">d10</button>
      <button type="button" class="btn btn-ghost btn-xs" data-quick-roll="1d12"
              @click="expression = '1d12'; roll()">d12</button>
      <button type="button" class="btn btn-ghost btn-xs" data-quick-roll="1d20"
              @click="expression = '1d20'; roll()">d20</button>
      <button type="button" class="btn btn-ghost btn-xs" data-quick-roll="1d100"
              @click="expression = '1d100'; roll()">d100</button>
    </div>
```

These are ghost buttons — the drawer's single gold `Roll` button (`btn btn-primary`) stays the only primary action, per the one-primary-action constraint.

- [ ] **Step 4: Add the layout rule**

In `src/main/resources/static/css/components.css`, append:

```css
.dice-quick-rolls {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-xs);
  margin: var(--space-xs) 0;
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=DiceQuickRollContractTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/fragments/_dice-roller.html \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/DiceQuickRollContractTest.java
git commit -m "feat: add one-click quick rolls to the dice drawer"
```

---

## Task 10: The bottom utility zone takes 227px for an empty input

**Severity: P2.** Measured in the Combat preset at a 1000px viewport: the bottom zone is 227px tall to hold a single-line quick-note input and the sentence "No quick notes yet." The manual calls this zone a "compact strip".

The ratio is server-side, in `CockpitBuiltInPresetCatalog.java:9-10`:

```java
    private static final CockpitLayoutDocument.SplitRatios DEFAULT_RATIOS =
            new CockpitLayoutDocument.SplitRatios(0.20, 0.56, 0.24, 0.24);
```

`SplitRatios` is `(double left, double primary, double right, double bottom)` (`CockpitLayoutDocument.java:30`). `bottom = 0.24` × the 952px workbench = 228px, matching the measurement.

`DEFAULT_RATIOS` is shared by all four presets, but **Combat is the only preset with an uncollapsed bottom zone** — Exploration and Theatre of Mind use `collapsedZone("audio", "session-log")` and Session Review uses `collapsedZone()` (`CockpitBuiltInPresetCatalog.java:15, 19, 23, 27`). So changing the shared value only shows up in Combat. Do not introduce a per-preset ratio override for this.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java:9-10`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitBottomZoneSizeBrowserTest.java` (create)

**Interfaces:**
- Consumes: `CockpitLayoutDocument.SplitRatios(double left, double primary, double right, double bottom)`.
- Produces: no new symbols. Only the `bottom` component of `DEFAULT_RATIOS` changes, from `0.24` to `0.13`.

- [ ] **Step 1: Write the failing browser test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitBottomZoneSizeBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CockpitBottomZoneSizeBrowserTest {

    @LocalServerPort private int port;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    void combatPresetBottomStripStaysCompact() {
        UUID campaignId = fixtures.campaignWithRunningSession();

        BrowserContext context = browser.newContext();
        Page page = context.newPage();
        page.setViewportSize(1600, 1000);
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => window.cockpitLayout.currentPresetKey === 'builtin:combat'");

        double height = page.locator(".cockpit-zone--bottom").boundingBox().height;
        assertThat(height)
                .as("the bottom utility strip must stay compact at a 1000px viewport")
                .isLessThanOrEqualTo(160.0);

        context.close();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=CockpitBottomZoneSizeBrowserTest`
Expected: FAIL — measured height is ~227px.

- [ ] **Step 3: Shrink the bottom ratio**

In `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java`, replace lines 9-10 with:

```java
    // The bottom strip is a utility rail for quick notes and audio, not a panel. At a
    // bottom ratio of 0.24 it took 227px of a 1000px viewport to hold one input line.
    // Combat is the only preset with an uncollapsed bottom zone, so this is the only
    // place it shows. The splitter still lets a DM grow it for the session log.
    private static final CockpitLayoutDocument.SplitRatios DEFAULT_RATIOS =
            new CockpitLayoutDocument.SplitRatios(0.20, 0.56, 0.24, 0.13);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=CockpitBottomZoneSizeBrowserTest`
Expected: PASS.

- [ ] **Step 5: Check the layout suite**

Run: `./mvnw test -Dtest='Cockpit*Test,ViewportAccessibilityGateTest'`
Expected: PASS. `CockpitLayoutValidator` and any preset-catalog test that asserts exact ratios will need its expected `bottom` value updated to `0.13`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitBottomZoneSizeBrowserTest.java
git commit -m "fix: keep the combat bottom utility strip compact"
```

---

## Task 11: A started session cannot be abandoned

**Severity: P2.** `CampaignSession.Status` is `{ IDLE, RUNNING, PAUSED, REVIEW }`. `SessionLifecycleService` exposes `start`, `pause`, `resume`, `cancelReview` and `complete` — but `complete` (line 175) is the only route back to `IDLE`, and it writes a `SESSION_LOG` note. A DM who starts a session by mistake, or runs a five-minute test, is forced to create a junk note.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleService.java` (add next to `cancelReview`, line 134)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java`
- Modify: `src/main/resources/templates/session/_lifecycle-dialog.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionAbandonTest.java` (create)

**Interfaces:**
- Consumes: `CampaignSession.Status` enum `{ IDLE, RUNNING, PAUSED, REVIEW }` (`CampaignSession.java:24`); `CampaignSessionRepository.findByCampaignId(UUID)`. The session's review text field is **`draftBody`** (`CampaignSession.java:75`) — setter `setDraftBody(String)`. The workspace map field is `workspaceMap` (line 60).
- Produces: `SessionLifecycleService.abandon(UUID campaignId)` returning `CampaignSession` — resets status to `IDLE`, clears `workspaceMap`, `draftBody`, `startedAt`, `pausedAt` and `reviewStartedAt`, writes **no** note, and throws `IllegalStateException` if the session is already `IDLE`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionAbandonTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SessionAbandonTest {

    @Autowired private SessionLifecycleService lifecycle;
    @Autowired private NoteRepository notes;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    @Test
    void abandoningASessionReturnsToIdleWithoutWritingASessionLog() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        int logsBefore = notes
                .findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_LOG)
                .size();

        CampaignSession session = lifecycle.abandon(campaignId);

        assertThat(session.getStatus()).isEqualTo(CampaignSession.Status.IDLE);
        assertThat(notes.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_LOG))
                .as("abandoning must not create a session log note")
                .hasSize(logsBefore);
    }

    @Test
    void abandoningAnIdleSessionIsRejected() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.abandon(campaignId);

        assertThatThrownBy(() -> lifecycle.abandon(campaignId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already idle");
    }
}
```

`NoteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(UUID, NoteType)` already exists (line 17) — do not add a new query method. Import `static org.assertj.core.api.Assertions.assertThatThrownBy` alongside `assertThat`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SessionAbandonTest`
Expected: FAIL — `abandon` does not exist (compilation error).

- [ ] **Step 3: Implement `abandon`**

Add to `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleService.java`, next to `cancelReview` (line 134):

```java
    /**
     * Drops a session without producing a log. Completing is the only other route back to
     * IDLE and it always writes a SESSION_LOG note, so a mistaken start or a short test run
     * had no exit that did not leave junk in the campaign.
     */
    @Transactional
    public CampaignSession abandon(UUID campaignId) {
        CampaignSession session = sessions.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalStateException("No session for campaign"));
        if (session.getStatus() == CampaignSession.Status.IDLE) {
            throw new IllegalStateException("Session is already idle");
        }
        session.setStatus(CampaignSession.Status.IDLE);
        session.setWorkspaceMap(null);
        session.setDraftBody(null);
        session.setStartedAt(null);
        session.setPausedAt(null);
        session.setReviewStartedAt(null);
        session.getAttendees().clear();
        return sessions.save(session);
    }
```

Use the repository field name that `SessionLifecycleService` already holds for `CampaignSessionRepository` (read the constructor — the other methods in this class already look it up the same way).

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SessionAbandonTest`
Expected: PASS.

- [ ] **Step 5: Add the control and its confirmation**

In `src/main/resources/templates/session/_lifecycle-dialog.html`, add alongside the existing `Pause` button in the running-session state:

```html
        <button type="button" class="btn btn-ghost btn-xs"
                data-abandon-session
                @click="confirmAbandonSession()">Discard session</button>
```

and in `src/main/resources/static/js/session-cockpit.js` add to the cockpit component:

```js
        // Completing is the only other exit and it always writes a SESSION_LOG note.
        // Discarding is destructive in the sense that the running session is gone, so it
        // asks first.
        async confirmAbandonSession() {
            if (!window.confirm(
                'Discard this session? Nothing is logged and the session returns to idle.')) {
                return;
            }
            try {
                await this.request(
                    `/api/v1/campaigns/${this.campaignId}/session/abandon`, { method: 'POST' });
                window.location.reload();
            } catch (error) {
                window.cockpitLayout?.showNotice(
                    'The session could not be discarded. Nothing was changed.');
            }
        },
```

Add the matching handler to `SessionApiController` (which is already mapped under `/api/v1/campaigns/{campaignId}/session` — the sibling of `activateEncounter` at line 134):

```java
    @PostMapping("/abandon")
    void abandonSession(@PathVariable UUID campaignId) {
        sessionLifecycleService.abandon(campaignId);
    }
```

using whatever field name the controller already holds for `SessionLifecycleService`.

- [ ] **Step 6: Run the session suite**

Run: `./mvnw test -Dtest='Session*Test,CoreSessionLoopSmokeTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/ \
        src/main/resources/templates/session/ \
        src/main/resources/static/js/session-cockpit.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionAbandonTest.java
git commit -m "feat: let a DM discard a session without writing a session log"
```

---

## Task 12: The DM manual documents a cockpit that no longer exists

**Severity: P2, docs only.** After the DM-only cut, `cockpit-layout.js:200` states plainly: "Alt+Shift+1…4 selects the four immutable built-ins", and the picker offers exactly `builtin:exploration`, `builtin:combat`, `builtin:theatre-of-mind`, `builtin:session-review`. The code is right; `docs/dm-manual/03-session-cockpit.md` is stale:

- Line ~37-45 lists **five** built-ins including **Presentation**, and maps `Alt+Shift+4` to Presentation and `Alt+Shift+5` to Session Review. Verified live: `Alt+Shift+4` selects Session Review; `Alt+Shift+5` does nothing.
- Line ~16 says "Ten modules ship in the registry" including Presentation.
- Line ~83 lists a **Presentation** module row with a `presentation` endpoint.
- Lines ~99-101 claim COMPACT is derived from the zone ("Modules serving in the Bottom utility zone render in COMPACT; all other zones render in STANDARD"). It is not. Compact-ness is an **explicit per-preset set** in `CockpitBuiltInPresetCatalog.java`: Exploration declares `Set.of("session-plan", "party", "quick-notes", "audio", "session-log")` (line 16) and Combat declares `Set.of("story", "party", "encounter", "quick-notes", "audio", "reference")` (line 20). Observed fetches match: `party?mode=COMPACT` and `session-plan?mode=COMPACT` in a *left support* zone, `encounter?mode=COMPACT` in a *right support* zone. This matters because `_story-rail.html:16-38` gates the summary, body, sections and participants behind `mode.name() != 'COMPACT'` — so a DM in the Combat preset gets read-aloud text only in the Story rail, by design.
- Lines ~109, ~111, ~142, ~275 reference the Handout picker, Presentation module and the `p` "Present current map" shortcut.
- Lines ~116-122 ("Player preview guarantees") and ~279 ("All cockpit routes are covered by the PIN interceptor") describe surfaces removed by the DM-only cut; `application.properties:37` records "PIN gate removed; server binds to loopback as compensating control."

**Files:**
- Modify: `docs/dm-manual/03-session-cockpit.md`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/docs/DmManualCockpitAccuracyTest.java` (create)

**Interfaces:**
- Consumes: the preset key list in `cockpit-layout.js` (`builtin:exploration`, `builtin:combat`, `builtin:theatre-of-mind`, `builtin:session-review`).
- Produces: no new symbols.

- [ ] **Step 1: Write the failing docs test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/docs/DmManualCockpitAccuracyTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The DM-only cut removed the Presentation preset, the Presentation module and the PIN
 * gate. The manual is what a DM reads before a session, so it must not promise a preset
 * the picker does not offer.
 */
class DmManualCockpitAccuracyTest {

    private static final Path MANUAL = Path.of("docs/dm-manual/03-session-cockpit.md");
    private static final Path LAYOUT_JS =
            Path.of("src/main/resources/static/js/cockpit-layout.js");

    @Test
    void manualDocumentsOnlyThePresetsTheCockpitShips() throws IOException {
        String manual = Files.readString(MANUAL);
        String js = Files.readString(LAYOUT_JS);

        for (String key : new String[] {
                "builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review"}) {
            assertThat(js).as("preset %s must exist in the layout controller", key).contains(key);
        }
        assertThat(js)
                .as("Presentation preset was removed by the DM-only cut")
                .doesNotContain("builtin:presentation");

        assertThat(manual)
                .as("the manual must not document a Presentation preset or module")
                .doesNotContain("| Presentation |")
                .doesNotContain("Five immutable built-ins")
                .doesNotContain("Ten modules ship")
                .doesNotContain("Ten runtime modules");
        assertThat(manual)
                .as("Alt+Shift+5 selects nothing")
                .doesNotContain("Alt+Shift+5");
        assertThat(manual)
                .as("the PIN gate was removed; loopback binding is the compensating control")
                .doesNotContain("PIN interceptor");
        assertThat(manual)
                .as("COMPACT is a per-preset set, not a zone rule")
                .doesNotContain("Modules serving in the **Bottom utility** zone render in `COMPACT`");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=DmManualCockpitAccuracyTest`
Expected: FAIL — the manual still says "Five immutable built-ins", "Alt+Shift+5" and "PIN interceptor".

Note the assertions are deliberately narrow rather than banning the word "Presentation" outright: `SessionAuditEntry.EntryType.PRESENTATION_OVERRIDE` still exists and the End Review section legitimately names it. **Leave the End Review section untouched.**

- [ ] **Step 3: Correct the manual**

In `docs/dm-manual/03-session-cockpit.md`:

1. Replace the built-in preset table with:

```markdown
| Shortcut | Preset | Typical primary |
|----------|--------|-----------------|
| `Alt+Shift+1` | Exploration | Story |
| `Alt+Shift+2` | Combat | Map |
| `Alt+Shift+3` | Theatre of Mind | Encounter |
| `Alt+Shift+4` | Session Review | Session log |
```

and change "Five immutable built-ins ship with the app" to "Four immutable built-ins ship with the app".

2. Change "Ten modules ship in the registry: Story, Map, Encounter, Party, Presentation, Session plan, Quick notes, Audio, Session log, …" to "Nine modules ship in the registry: Story, Map, Encounter, Party, Session plan, Quick notes, Reference, Audio, and Session log."

3. Delete the **Presentation** row from the module responsibilities table and change "Ten runtime modules ship in the cockpit registry" to "Nine runtime modules ship in the cockpit registry".

4. Replace the "Compact vs Focus behaviour" bullets with:

```markdown
- `COMPACT` is **not** derived from the zone. Each preset names the modules it wants condensed in its own `compactModuleKeys` set (`CockpitBuiltInPresetCatalog.java`); everything else renders `STANDARD`. Exploration condenses Session plan, Party, Quick notes, Audio and Session log. Combat additionally condenses Story, Encounter and Reference.
- A `COMPACT` Story module shows the scene title and read-aloud text only — its summary, DM notes, sections and participants are `STANDARD`-only. That is deliberate: in Combat the Story rail is a prompter, not a reference.
- Modules that support **Focus** open a full-workbench overlay. **Return** (or `Escape`) restores the previous layout and returns keyboard focus to the Focus trigger.
```

5. In the "Where actions live" table, delete the **Handout** picker and **Presentation** rows.

6. Delete the entire "Player preview guarantees" section.

7. Delete the `p` / "Present current map" row from **both** keyboard tables (the one under "Keyboard workflow" and the one under "Keyboard Actions"), and change both `Alt+Shift+1`…`5` entries to `Alt+Shift+1`…`4` with the Presentation name removed.

8. Replace the closing line "All cockpit routes are covered by the PIN interceptor." with:

```markdown
The server binds to `127.0.0.1` only; loopback binding is the access control for all cockpit routes.
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=DmManualCockpitAccuracyTest`
Expected: PASS.

- [ ] **Step 5: Check no other doc test broke**

Run: `./mvnw test -Dtest='*Docs*Test,*Manual*Test,*Coverage*Test'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add docs/dm-manual/03-session-cockpit.md \
        src/test/java/dev/hendrikhoemberg/dmhelper/docs/DmManualCockpitAccuracyTest.java
git commit -m "docs: realign the cockpit manual with the DM-only preset and module set"
```

---

## Final verification

- [ ] **Run the whole suite**

Run: `./mvnw test`
Expected: PASS, no skips introduced by this plan.

- [ ] **Re-run the DM journey by hand**

```bash
./mvnw -DskipTests spring-boot:run -Dspring-boot.run.arguments="--dmhelper.open-browser=false"
```

Then, against the Phandelver campaign, confirm each fix in the browser:

1. Cockpit → Combat preset → reload. Map and Encounter render immediately, with no preset toggle. **(Task 1)**
2. Story → a scene whose encounter is `DONE` → "Run this encounter" → "Suspend current and run". The encounter activates; if anything fails, a readable notice appears. **(Task 2)**
3. Story rail on Goblin-Hinterhalt: the whole tactics paragraph is readable by scrolling the module. **(Task 3)**
4. Map module picker names the loaded map, not "No map". **(Task 4)**
5. Party rail: all four chips share one left edge. **(Task 5)**
6. Initiative setup shows "Add party"; clicking it puts all four PCs in the order. **(Task 6)**
7. Campaign home → advisories → "Seed all encounters" clears all sixteen in one action. **(Task 7)**
8. Hovering `PInv` shows "Passive Investigation". **(Task 8)**
9. Dice drawer: clicking `d20` rolls without typing. **(Task 9)**
10. Combat preset bottom strip is ~130px, not ~230px. **(Task 10)**
11. Session → "Discard session" returns to IDLE and writes no note. **(Task 11)**
12. `Alt+Shift+4` selects Session Review, matching the manual. **(Task 12)**

- [ ] **Restore the working campaign if needed**

The DM run that produced these findings left the campaign session `PAUSED`, the "Bereich 2: Goblinwachposten" encounter `SUSPENDED`, and one `1d20+4` roll in history. A pre-run backup sits at `~/.dmhelper/backups/dmhelper-20260729-135203`.
