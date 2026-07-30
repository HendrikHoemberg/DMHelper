# Encounter Run Fidelity and Cockpit Fit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the defects found while running the imported *Verlorene Mine von Phandelver* campaign as a DM — the cockpit modules that clip their own controls, the initiative list that shows entities instead of turns, and the state and status inconsistencies between server and client.

**Architecture:** Three ideas carry the whole plan. First, cockpit modules are resizable panes whose width is independent of the viewport, so every breakpoint inside a module becomes a CSS container query instead of a media query. Second, "who takes this turn" and "is this encounter runnable" are decided once on the server and shipped to the client as data (`actsForGroup`, `ReadinessVerdict`) rather than re-derived by each template. Third, activation is a single transition whose response already carries everything the client needs to refresh; the client stops ignoring it.

**Tech Stack:** Spring Boot 4.1, Java 26, Spring Data JPA, H2/Flyway, Thymeleaf, Alpine.js, Konva, JUnit 5, AssertJ, MockMvc, Playwright 1.54.

## Global Constraints

- Cockpit modules are user-resizable. No layout rule inside a module may key off viewport width; use `@container` against the module's own inline size.
- The server is the single authority for turn eligibility and encounter readiness. Templates and JS consume those decisions; they never re-implement the rules.
- Warnings never block a DM action. Only `ERROR`-severity readiness issues stop an activation.
- A group of monsters occupies exactly one turn in the initiative order, and exactly one row by default.
- Every existing test must keep passing. The full suite is `./mvnw -o test` and currently reports 2678 tests, 0 failures, 0 errors, 6 skipped.
- There is no JavaScript unit-test harness in this project. Client behaviour is proven with Playwright against a real Chromium, following the pattern in `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReleaseRehearsalTest.java`.
- No browser-native `prompt()`, `confirm()`, or `alert()` may be introduced.
- Every mutating UI action must provide busy, success, validation, and retry/error feedback.
- German campaign content in fixtures and screenshots is data, not UI copy. UI copy stays English.

---

## File and Interface Map

### New files

- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/ReadinessVerdict.java` — the three-state readiness answer shared by the API and the cockpit view.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java` — Playwright test proving modules do not clip their controls at rail width.
- `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterGroupTurnTest.java` — group representation, collapse data, and grouped initiative rolling.

### Existing files with changed responsibilities

- `EncounterPlacementService.java` — owns the readiness rule as one static function; returns a `ReadinessVerdict` alongside the existing `canRun`.
- `EncounterService.java` — publishes `actsForGroup` on `CombatantDto`; rolls one initiative per group; stops advancing `round` during setup.
- `CockpitRuntimeModuleViewService.java` — reuses the shared readiness rule instead of its own `ready` formula, and exposes finished encounters so they can be reopened.
- `session-cockpit.js` — refreshes the map module after activation using the `workspaceMapId` the server already returns.
- `combat-tracker.js` — collapses groups to their representative and expands on demand.
- `_tracker.html` — group badge follows the acting representative; grouped rows collapse.
- `_encounter-rail.html` — encounter rows become a three-part grid (title / meta / action) with the status as a real chip.
- `components.css`, `cockpit-modules.css` — module-internal breakpoints become container queries.
- `SceneStructuredContentService.java` — validates `mapRegionKey` on write so it cannot break export later.

### Stable interfaces introduced by this plan

```java
public enum ReadinessVerdict { RUNNABLE, RUNNABLE_WITH_NOTES, BLOCKED }

// EncounterPlacementService — the single readiness rule
public static ReadinessVerdict verdictFor(boolean hasMap, boolean placementMapMismatch,
                                          int combatantCount, int unplacedCount);

public record EncounterReadinessDto(UUID encounterId, boolean canRun, ReadinessVerdict verdict,
                                    UUID mapId, int combatantCount, int placedCombatantCount,
                                    int unplacedCombatantCount, List<ReadinessIssueDto> issues) {}

// EncounterService — CombatantDto gains a trailing component
// boolean actsForGroup: true when the initiative order stops on this combatant
```

---

## Phase A — Cockpit fit

### Task 1: Module-internal breakpoints become container queries

The initiative setup panel is unusable in the Combat preset. `.initiative-setup` has a responsive rule at `@media (max-width: 760px)` that stacks the header and wraps the rows, but the module sits in a ~370px right rail inside a 1600px viewport, so the media query never fires. The header keeps `flex-direction: row` with `justify-content: space-between` across a title block and two buttons, the rows keep `white-space: nowrap`, and the content is cut off at the panel edge. During the walkthrough the initiative inputs could not be reached at all and combat had to be started through the API.

`cockpit-layout.css:50` already uses `container-type: inline-size` with `@container (max-width: 70rem)` for the topbar, so this is the established pattern here.

**Files:**
- Modify: `src/main/resources/static/css/cockpit-modules.css` (add the container declaration near the existing module rules at line 415)
- Modify: `src/main/resources/static/css/components.css:1077-1086` (convert the breakpoint)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `.cockpit-module` becomes a named query container `cockpit-module`. Later CSS tasks query it with `@container cockpit-module (max-width: …)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** The encounter module must stay usable at rail width, not only when focused. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CockpitModuleFitTest {

    @LocalServerPort int port;
    @Autowired ReleaseRehearsalFixture fixture;

    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;
    ReleaseRehearsalFixture.Seeded seeded;

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
        seeded = fixture.seedForRehearsal(ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP);
        context = browser.newContext();
        page = context.newPage();
        page.setViewportSize(1600, 1000);
    }

    @AfterEach
    void closePage() {
        if (context != null) context.close();
    }

    @Test
    void initiativeSetupFitsTheEncounterRail() {
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
        page.evaluate("() => window.cockpitLayout.applyPreset('builtin:combat', { skipDirtyCheck: true })");
        page.waitForFunction("() => document.querySelector('#cockpitPresetPicker')?.value === 'builtin:combat'");

        Locator runRow = page.locator(".planned-encounter-row button").first();
        runRow.waitFor();
        runRow.click();
        page.waitForSelector("[data-runtime-module='encounter'] [data-initiative-setup]");

        // Nothing inside the rail may spill outside it.
        Object fit = page.evaluate("""
                () => {
                  const panel = document.querySelector("[data-runtime-module='encounter'] [data-initiative-setup]");
                  const overflow = panel.scrollWidth - panel.clientWidth;
                  const input = panel.querySelector('input[data-initiative-input]');
                  const roll = panel.querySelector('button[data-roll-unset-initiative]');
                  const inside = el => {
                    const r = el.getBoundingClientRect();
                    const p = panel.getBoundingClientRect();
                    return r.left >= p.left - 1 && r.right <= p.right + 1;
                  };
                  return {overflow, inputInside: inside(input), rollInside: inside(roll)};
                }
                """);
        assertThat(fit.toString())
                .as("the initiative panel and its controls stay inside the rail")
                .isEqualTo("{overflow=0, inputInside=true, rollInside=true}");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=CockpitModuleFitTest`
Expected: FAIL — `overflow` is greater than 0 and/or `inputInside=false`, because the `760px` media query never matches at a 1600px viewport.

- [ ] **Step 3: Make the module a query container**

In `src/main/resources/static/css/cockpit-modules.css`, immediately above the existing `/* Story / encounter rails scroll inside the module body only. */` block at line 415, add:

```css
/* Modules are user-resizable panes. Their width has nothing to do with the viewport,
   so every breakpoint inside a module queries the module, never the screen. */
.cockpit-module {
  container-type: inline-size;
  container-name: cockpit-module;
}
```

- [ ] **Step 4: Convert the initiative breakpoint**

In `src/main/resources/static/css/components.css`, replace lines 1077-1086:

```css
@media (max-width: 760px) {
    .initiative-setup__header {
        align-items: flex-start;
        flex-direction: column;
    }

    .initiative-setup__row {
        flex-wrap: wrap;
    }
}
```

with:

```css
/* Fires on the module's own width: the encounter rail is ~370px even on a 1600px screen. */
@container cockpit-module (max-width: 34rem) {
    .initiative-setup__header {
        align-items: flex-start;
        flex-direction: column;
    }

    .initiative-setup__row {
        flex-wrap: wrap;
    }
}

/* Standalone encounter pages have no module container; keep them responsive too. */
@media (max-width: 760px) {
    .initiative-setup__header {
        align-items: flex-start;
        flex-direction: column;
    }

    .initiative-setup__row {
        flex-wrap: wrap;
    }
}
```

- [ ] **Step 5: Give the panel a horizontal safety net**

In `src/main/resources/static/css/cockpit-modules.css`, inside the existing `.initiative-setup__order, .initiative-setup__row` rule at line 425, the `max-width: 100%` is already present. Add below that block:

```css
/* If a future control still cannot fit, scroll it rather than clipping it silently. */
.cockpit-module .initiative-setup { overflow-x: auto; }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=CockpitModuleFitTest`
Expected: PASS

- [ ] **Step 7: Run the release gate, which exercises the same panel**

Run: `./mvnw -o test -Dtest=ReleaseRehearsalTest`
Expected: PASS — 18 tests, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/css/cockpit-modules.css \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java
git commit -m "fix: key module breakpoints to module width so the initiative panel fits its rail"
```

---

### Task 2: Encounter rail rows become scannable

Rows render `<strong>` title and `<small>` metadata as inline siblings, so they wrap into one blob. The `Ready` / `Not ready` word lands wherever the wrap drops it, and `Run encounter` wraps to two lines with a shifting baseline. `enc.mapName` is concatenated unguarded, so a map-less encounter renders the literal text `null · 4 combatants`.

**Files:**
- Modify: `src/main/resources/templates/session/_encounter-rail.html:26-40`
- Modify: `src/main/resources/static/css/cockpit-modules.css` (append rail rules)

**Interfaces:**
- Consumes: `.cockpit-module` container from Task 1.
- Produces: `.planned-encounter-row` becomes a three-column grid; `.encounter-chip` is the status chip class reused in Task 5.

- [ ] **Step 1: Restructure the row markup**

In `src/main/resources/templates/session/_encounter-rail.html`, replace lines 26-40 with:

```html
            <div class="planned-encounter-row"
                 th:attr="data-name=${enc.name}"
                 x-show="!plannedFilter || $el.dataset.name.toLowerCase().includes(plannedFilter.toLowerCase())">
              <span class="planned-encounter-row__title" th:text="${enc.name}">Goblin Ambush</span>
              <span class="planned-encounter-row__meta">
                <span th:text="${enc.mapName != null ? enc.mapName : 'No map'}">Map</span>
                <span aria-hidden="true">·</span>
                <span th:text="${enc.combatantCount} + (${enc.combatantCount} == 1 ? ' combatant' : ' combatants')">6 combatants</span>
              </span>
              <span class="encounter-chip"
                    th:classappend="${enc.ready ? 'encounter-chip--ready' : 'encounter-chip--notes'}"
                    th:text="${enc.ready ? 'Ready' : 'Needs setup'}"
                    th:title="${enc.ready ? 'Every combatant has a statblock and a map placement' : 'Runnable now; placement happens on activation'}">Ready</span>
              <!-- Task 7 replaces this two-state chip with the three-state verdict. -->

              <button class="btn btn-ghost encounter-rail__action"
                      th:attr="data-encounter-id=${enc.id}"
                      @click="runEncounter($el.dataset.encounterId)">Run</button>
            </div>
```

- [ ] **Step 2: Add the grid rules**

Append to `src/main/resources/static/css/cockpit-modules.css`:

```css
.planned-encounter-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    "title  action"
    "meta   action"
    "chip   action";
  align-items: center;
  gap: 2px var(--space-sm);
  padding: var(--space-xs) 0;
}

.planned-encounter-row__title {
  grid-area: title;
  font-weight: 600;
  min-width: 0;
  overflow-wrap: anywhere;
}

.planned-encounter-row__meta {
  grid-area: meta;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  display: flex;
  gap: 4px;
  min-width: 0;
}

.encounter-chip {
  grid-area: chip;
  justify-self: start;
  padding: 1px 6px;
  border-radius: var(--radius);
  font-size: var(--text-xs);
  white-space: nowrap;
}

.encounter-chip--ready { background: var(--elevation-raised-bg); }
.encounter-chip--notes { background: var(--elevation-raised-bg); color: var(--color-warning); }
.encounter-chip--blocked { background: var(--color-danger); color: var(--color-bg); }

.encounter-rail__action {
  grid-area: action;
  align-self: center;
  white-space: nowrap;
  padding: 2px 10px;
}
```

- [ ] **Step 3: Verify the template still binds**

Run: `./mvnw -o test -Dtest=SessionCockpitTemplateContractTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/session/_encounter-rail.html \
        src/main/resources/static/css/cockpit-modules.css
git commit -m "fix: lay encounter rail rows out as title/meta/status/action instead of one wrapping blob"
```

---

### Task 3: Fix the remaining layout and copy defects

Four small defects seen in the walkthrough screenshots. The encounter heading renders `ALTER EULENBRUNNEN: HAMUN KOST & ZOMBIESRound 1` with no separation. Quest badges are flex children that shrink, so `Not started` breaks across two lines and destroys the list's left alignment. The `End` button is nearly invisible against the panel. Every module prints its name twice — once as a zone tab, once in the module header bar underneath.

**Files:**
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/main/resources/templates/encounter/_tracker.html` (heading spacing)

**Interfaces:**
- Consumes: `.cockpit-module` container from Task 1.
- Produces: nothing consumed later.

- [ ] **Step 1: Separate the encounter heading from the round counter**

In `src/main/resources/templates/encounter/_tracker.html`, find the heading block that renders the encounter name immediately followed by `Round`. Wrap the name in its own element so the two cannot butt together:

```html
<span class="tracker-heading__name" x-text="encounter?.name"></span>
<span class="tracker-heading__round">Round <strong x-text="encounter?.round"></strong></span>
```

- [ ] **Step 2: Add the spacing, badge, and contrast rules**

Append to `src/main/resources/static/css/cockpit-modules.css`:

```css
.tracker-heading__name { margin-right: var(--space-sm); }
.tracker-heading__round { white-space: nowrap; }

/* Status badges are labels, not text that may reflow mid-word. */
.session-plan-module-inner .quest-progress-item .badge {
  flex: 0 0 auto;
  white-space: nowrap;
}

/* Ending a fight is deliberate but must still be findable. */
.cockpit-module [data-end-encounter] {
  color: var(--color-danger);
  opacity: 1;
}

/* The zone tab already names the module; the header bar repeats it and costs a row of turns. */
.cockpit-zone__tabs + .cockpit-module .cockpit-module__header-title { display: none; }
```

- [ ] **Step 3: Confirm the tracker still renders**

Run: `./mvnw -o test -Dtest=SessionCockpitTemplateContractTest,ReleaseRehearsalTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/css/cockpit-modules.css
git commit -m "fix: heading spacing, badge wrapping, End-button contrast, duplicated module titles"
```

---

## Phase B — Turns, not entities

### Task 4: The server says who represents a group

`_tracker.html:349` binds the `(n)` group badge to `c.groupLeader`. That flag is fixed when the group is built, so after the leader dies the corpse keeps the badge while a different member silently takes the group's turns. `EncounterService.takesTurn` already computes the truth for turn advancement; the client needs the same answer.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java` (`CombatantDto`, `toDto`, `getCombatants`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterGroupTurnTest.java`

**Interfaces:**
- Consumes: `takesTurn(Combatant, List<Combatant>)`, already present and `private static`.
- Produces: `CombatantDto.actsForGroup()` — `true` when the initiative order stops on this combatant. Task 5 renders it.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterGroupTurnTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.*;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({EncounterService.class, EncounterPlacementService.class, CombatDifficultyCalculator.class,
        GameMapService.class, DiceEngine.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
        dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver.class,
        dev.hendrikhoemberg.dmhelper.config.MarkdownUtil.class})
class EncounterGroupTurnTest {

    @MockitoBean dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService keys;
    @Autowired EncounterService service;
    @Autowired jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Group Turns");
        em.persist(campaign);
        em.flush();
    }

    private static CombatantUpdateRequest group(String groupId, boolean leader) {
        return new CombatantUpdateRequest(null, null, null, null, null, null, null,
                groupId, leader, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @Test
    void groupRepresentationMovesToASurvivorWhenTheLeaderFalls() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto leader = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin 1", 7, "MONSTER", null, null));
        CombatantDto mook = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin 2", 7, "MONSTER", null, null));
        service.updateCombatant(leader.id(), group("goblins", true));
        service.updateCombatant(mook.id(), group("goblins", false));
        service.setInitiative(leader.id(), 20);
        service.setInitiative(mook.id(), 18);

        assertThat(service.getCombatants(enc.id()))
                .filteredOn(CombatantDto::actsForGroup)
                .extracting(CombatantDto::name)
                .containsExactly("Goblin 1");

        service.markDefeated(leader.id(), true);

        assertThat(service.getCombatants(enc.id()))
                .filteredOn(CombatantDto::actsForGroup)
                .extracting(CombatantDto::name)
                .containsExactly("Goblin 2");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=EncounterGroupTurnTest`
Expected: FAIL to compile — `CombatantDto` has no `actsForGroup()`.

- [ ] **Step 3: Add the component to the DTO**

In `EncounterService.java`, append `boolean actsForGroup` as the final component of `CombatantDto` (currently ending `ThreatKind threatKind, UUID threatId, ThreatCardView threatCard`):

```java
                               ThreatKind threatKind, UUID threatId, ThreatCardView threatCard,
                               boolean actsForGroup) {}
```

- [ ] **Step 4: Thread it through `toDto`**

Replace the existing single-argument `toDto(Combatant c)` with a two-argument form plus a conservative default, so the many existing single-combatant call sites keep compiling:

```java
    CombatantDto toDto(Combatant c) {
        // Single-combatant responses have no ordered roster to reason about; fall back to the
        // stored flag, which is correct whenever the flagged leader is still alive.
        return toDto(c, c.getGroupId() == null || c.isGroupLeader());
    }

    CombatantDto toDto(Combatant c, boolean actsForGroup) {
```

and add `actsForGroup` as the final argument of the `new CombatantDto(...)` expression inside it, after `resolveThreatCard(c)`.

- [ ] **Step 5: Compute it where the roster is known**

Replace `getCombatants`:

```java
    @Transactional(readOnly = true)
    public List<CombatantDto> getCombatants(UUID encounterId) {
        List<Combatant> active = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
                .filter(this::isOnActiveWave)
                .toList();
        Set<UUID> representatives = active.stream()
                .filter(c -> takesTurn(c, active))
                .map(Combatant::getId)
                .collect(Collectors.toSet());
        return active.stream()
                .map(c -> toDto(c, representatives.contains(c.getId())))
                .toList();
    }
```

Apply the same treatment to `getInitiativeSetupCombatants`, which feeds the setup list:

```java
    @Transactional(readOnly = true)
    public List<CombatantDto> getInitiativeSetupCombatants(UUID encounterId) {
        List<Combatant> all = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        Set<UUID> representatives = all.stream()
                .filter(c -> takesTurn(c, all))
                .map(Combatant::getId)
                .collect(Collectors.toSet());
        return all.stream()
                .map(c -> toDto(c, representatives.contains(c.getId())))
                .toList();
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=EncounterGroupTurnTest`
Expected: PASS

- [ ] **Step 7: Run everything that constructs a CombatantDto**

Run: `./mvnw -o test -Dtest='dev.hendrikhoemberg.dmhelper.encounter.**,dev.hendrikhoemberg.dmhelper.session.**'`
Expected: PASS. If any test constructs `CombatantDto` positionally, add the trailing `false`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterGroupTurnTest.java
git commit -m "feat: publish actsForGroup so clients can show which combatant represents a group"
```

---

### Task 5: Collapse groups to one row

The Alter Eulenbrunnen fight rendered 17 rows for 5 actual actors: 11 zombies at initiatives 11, 10, 8, 7, 5, 5, 4, 2, 2, 1 and 0, interleaved with the party, only one of which ever takes a turn. The list should show turns.

**Files:**
- Modify: `src/main/resources/static/js/combat-tracker.js:703` (`groupCount`, plus new collapse state)
- Modify: `src/main/resources/templates/encounter/_tracker.html:335-355`
- Modify: `src/main/resources/static/css/cockpit-modules.css`

**Interfaces:**
- Consumes: `CombatantDto.actsForGroup` from Task 4.
- Produces: nothing consumed later.

- [ ] **Step 1: Add collapse state and helpers**

In `src/main/resources/static/js/combat-tracker.js`, replace `groupCount` (line 703) with:

```javascript
            groupCount(groupId) {
                return this.combatants.filter(c => c.groupId === groupId).length;
            },

            groupAliveCount(groupId) {
                return this.combatants.filter(c => c.groupId === groupId && !c.defeated).length;
            },

            // A group takes one turn, so it gets one row. Expanding reveals the members for
            // individual HP tracking without putting eleven never-acting rows in the order.
            isGroupExpanded(groupId) {
                return !!this.expandedGroups[groupId];
            },

            toggleGroup(groupId) {
                this.expandedGroups = {
                    ...this.expandedGroups,
                    [groupId]: !this.expandedGroups[groupId],
                };
            },

            showsInOrder(c) {
                if (!c.groupId) return true;
                return c.actsForGroup || this.isGroupExpanded(c.groupId);
            },
```

Add `expandedGroups: {},` to the component's returned state object alongside the other reactive fields.

- [ ] **Step 2: Render one row per group**

In `src/main/resources/templates/encounter/_tracker.html`, change the row's visibility test at line 340 from `x-show="!c.hidden"` to:

```html
                         x-show="!c.hidden && showsInOrder(c)"
```

and replace the name block at lines 346-353 with:

```html
                        <span class="combatant-name">
                            <template x-if="c.groupId && !c.actsForGroup">
                                <span class="group-branch">├ </span>
                            </template>
                            <span x-text="c.name"></span>
                            <template x-if="c.groupId && c.actsForGroup">
                                <button type="button" class="group-count"
                                        :aria-expanded="isGroupExpanded(c.groupId)"
                                        @click.stop="toggleGroup(c.groupId)"
                                        x-text="'+' + (groupAliveCount(c.groupId) - 1) + ' more'"></button>
                            </template>
                        </span>
```

- [ ] **Step 3: Style the disclosure**

Append to `src/main/resources/static/css/cockpit-modules.css`:

```css
.group-count {
  background: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  padding: 0 6px;
  margin-left: 4px;
  cursor: pointer;
}
.group-count[aria-expanded="true"] { color: var(--color-text); }
```

- [ ] **Step 4: Verify against the release gate**

Run: `./mvnw -o test -Dtest=ReleaseRehearsalTest`
Expected: PASS. `ReleaseRehearsalTest` step 5 iterates combatant rows by `data-cid`; if it now misses collapsed members, expand the group first by clicking `.group-count` before the HP loop.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/combat-tracker.js \
        src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/css/cockpit-modules.css
git commit -m "feat: collapse monster groups to the row that actually takes the turn"
```

---

### Task 6: One initiative roll per group

`rollUnsetNpcInitiatives` rolls for every combatant with a null initiative, including non-representative group members. That is what scattered eleven zombies across the order at 11, 10, 8, 7, 5, 5, 4, 2, 2, 1 and 0 when they act as one.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java:2220-2232`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterGroupTurnTest.java`

**Interfaces:**
- Consumes: `takesTurn` from the existing code; `actsForGroup` from Task 4.
- Produces: nothing consumed later.

- [ ] **Step 1: Write the failing test**

Append to `EncounterGroupTurnTest`:

```java
    @Test
    void everyMemberOfAGroupSharesOneInitiativeRoll() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        String groupId = "zombies";
        for (int i = 1; i <= 5; i++) {
            CombatantDto c = service.addCombatant(enc.id(),
                    new CombatantCreateRequest("Zombie " + i, 22, "MONSTER", null, null));
            service.updateCombatant(c.id(), group(groupId, i == 1));
        }

        service.rollUnsetNpcInitiatives(enc.id());

        assertThat(service.getCombatants(enc.id()))
                .extracting(CombatantDto::initiative)
                .as("one group, one initiative")
                .containsOnly(service.getCombatants(enc.id()).get(0).initiative());
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=EncounterGroupTurnTest#everyMemberOfAGroupSharesOneInitiativeRoll`
Expected: FAIL — five independent rolls produce differing values.

- [ ] **Step 3: Roll once per group**

Replace `rollUnsetNpcInitiatives` in `EncounterService.java`:

```java
    public List<CombatantDto> rollUnsetNpcInitiatives(UUID encounterId) {
        Encounter encounter = requireSetup(encounterId);
        List<Combatant> all = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        // A group acts on one turn, so it rolls one initiative. Rolling per member scattered
        // the members through the order at values they would never act on.
        Map<String, Integer> rolledByGroup = new HashMap<>();
        for (Combatant combatant : all) {
            if (combatant.getInitiative() != null || "PC".equals(combatant.getKind())) continue;
            int modifier = combatant.getStatBlock() == null ? 0 : dexModifier(combatant.getStatBlock());
            String groupId = combatant.getGroupId();
            int roll;
            if (groupId == null) {
                roll = diceEngine.roll("d20").total();
            } else {
                roll = rolledByGroup.computeIfAbsent(groupId, unused -> diceEngine.roll("d20").total());
            }
            combatant.setInitiative(roll + modifier);
            combatantRepo.save(combatant);
            logInitiativeRoll(encounter.getId(), combatant, roll, modifier);
        }
        resortCombatants(encounterId);
        return getCombatants(encounterId);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=EncounterGroupTurnTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterGroupTurnTest.java
git commit -m "fix: roll one initiative per monster group instead of one per member"
```

---

## Phase C — One source of truth for state

### Task 7: A single readiness verdict

Two independent formulas answer "is this encounter ready". `EncounterPlacementService.readiness` grades by severity and only `ERROR` sets `canRun=false`. `CockpitRuntimeModuleViewService` line 301 uses `combatantCount > 0 && unplacedCount == 0`. The rail therefore labelled *Alter Eulenbrunnen — 13 combatants — Not ready* for an encounter that ran with no dialog at all. That divergence is what produced the activation bug already fixed in `6521d0c3`.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/ReadinessVerdict.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementService.java:59-61,243-284`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java:297-306`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `ReadinessVerdict`, `EncounterPlacementService.verdictFor(...)`, and `EncounterReadinessDto.verdict()`.

- [ ] **Step 1: Write the failing test**

Append to `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementServiceTest.java`:

```java
    @Test
    void unplacedCombatantsAreNotesRatherThanBlockers() {
        assertThat(EncounterPlacementService.verdictFor(true, false, 4, 4))
                .isEqualTo(ReadinessVerdict.RUNNABLE_WITH_NOTES);
        assertThat(EncounterPlacementService.verdictFor(true, false, 4, 0))
                .isEqualTo(ReadinessVerdict.RUNNABLE);
        assertThat(EncounterPlacementService.verdictFor(false, false, 4, 0))
                .isEqualTo(ReadinessVerdict.BLOCKED);
        assertThat(EncounterPlacementService.verdictFor(true, true, 4, 0))
                .isEqualTo(ReadinessVerdict.BLOCKED);
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=EncounterPlacementServiceTest#unplacedCombatantsAreNotesRatherThanBlockers`
Expected: FAIL to compile — `ReadinessVerdict` and `verdictFor` do not exist.

- [ ] **Step 3: Create the enum**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/ReadinessVerdict.java`:

```java
package dev.hendrikhoemberg.dmhelper.encounter.service;

/**
 * The one answer to "can the DM run this now". BLOCKED stops activation; RUNNABLE_WITH_NOTES
 * is worth showing on a list but must never interrupt an action.
 */
public enum ReadinessVerdict {
    RUNNABLE,
    RUNNABLE_WITH_NOTES,
    BLOCKED
}
```

- [ ] **Step 4: Add the shared rule and widen the DTO**

In `EncounterPlacementService.java`, add above `readiness(...)`:

```java
    /** The single readiness rule. Both the API and the cockpit rail call this. */
    public static ReadinessVerdict verdictFor(boolean hasMap, boolean placementMapMismatch,
                                              int combatantCount, int unplacedCount) {
        if (!hasMap || placementMapMismatch) return ReadinessVerdict.BLOCKED;
        if (combatantCount == 0 || unplacedCount > 0) return ReadinessVerdict.RUNNABLE_WITH_NOTES;
        return ReadinessVerdict.RUNNABLE;
    }
```

Change the record at line 59:

```java
    public record EncounterReadinessDto(UUID encounterId, boolean canRun, ReadinessVerdict verdict,
                                        UUID mapId,
                                        int combatantCount, int placedCombatantCount, int unplacedCombatantCount,
                                        List<ReadinessIssueDto> issues) {}
```

and the return at the end of `readiness(...)`:

```java
        ReadinessVerdict verdict = verdictFor(encounter.getMap() != null, mismatch,
                combatants.size(), unplacedCount);
        return new EncounterReadinessDto(encounterId, verdict != ReadinessVerdict.BLOCKED, verdict,
                encounter.getMap() != null ? encounter.getMap().getId() : null,
                combatants.size(), placedCount, unplacedCount, issues);
```

Introduce `boolean mismatch = false;` where `hasError` is currently set by the `PLACEMENT_MAP_MISMATCH` branch, and set `mismatch = true` there alongside `hasError = true`.

- [ ] **Step 5: Make the cockpit use the same rule**

In `CockpitRuntimeModuleViewService.java`, replace the `toView` lambda body at lines 297-306:

```java
        java.util.function.Function<Encounter, PlannedEncounterView> toView = e -> {
            var combatantList = combatants.findByEncounterIdOrderBySortOrderAsc(e.getId());
            int combatantCount = combatantList.size();
            long unplacedCount = combatantList.stream().filter(c -> c.getPlacement() == null).count();
            // Same rule the activation gate uses, so the chip cannot contradict the button.
            ReadinessVerdict verdict = EncounterPlacementService.verdictFor(
                    e.getMap() != null, false, combatantCount, (int) unplacedCount);
            return new PlannedEncounterView(e.getId(), e.getName(),
                    e.getMap() != null ? e.getMap().getId() : null,
                    e.getMap() != null ? e.getMap().getName() : null,
                    verdict == ReadinessVerdict.RUNNABLE, combatantCount, (int) unplacedCount);
        };
```

Add the import `dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService` and `...ReadinessVerdict`.

- [ ] **Step 6: Carry the verdict all the way to the chip**

A boolean cannot distinguish "runnable, needs placement" from "cannot run at all", so a map-less
encounter would wrongly read *Needs setup*. Replace the `boolean ready` component of
`PlannedEncounterView` with `ReadinessVerdict verdict`:

```java
    public record PlannedEncounterView(UUID id, String name, UUID mapId, String mapName,
                                       ReadinessVerdict verdict, int combatantCount, int unplacedCount) {}
```

and return `verdict` from `toView` instead of `verdict == ReadinessVerdict.RUNNABLE`.

Then in `src/main/resources/templates/session/_encounter-rail.html`, replace the chip added in
Task 2 (and delete the `<!-- Task 7 replaces ... -->` comment):

```html
              <span class="encounter-chip"
                    th:classappend="${enc.verdict.name() == 'RUNNABLE'} ? 'encounter-chip--ready'
                                  : (${enc.verdict.name() == 'BLOCKED'} ? 'encounter-chip--blocked' : 'encounter-chip--notes')"
                    th:text="${enc.verdict.name() == 'RUNNABLE'} ? 'Ready'
                           : (${enc.verdict.name() == 'BLOCKED'} ? 'Blocked' : 'Needs setup')"
                    th:title="${enc.verdict.name() == 'BLOCKED'} ? 'Assign a map before running this encounter'
                            : 'Runnable now; placement happens on activation'">Ready</span>
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw -o test -Dtest='dev.hendrikhoemberg.dmhelper.encounter.**,dev.hendrikhoemberg.dmhelper.session.**'`
Expected: PASS. Any test constructing `EncounterReadinessDto` positionally needs the new `verdict`
argument; any test reading `PlannedEncounterView.ready()` now reads `verdict()`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/ReadinessVerdict.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java \
        src/main/resources/templates/session/_encounter-rail.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementServiceTest.java
git commit -m "refactor: one readiness rule shared by the activation gate and the encounter rail"
```

---

### Task 8: The round counter stays at zero until combat starts

`activate(UUID)` sets `round=0, activeTurnIndex=-1`. `activateFresh(UUID)`, the path the session uses for a PLANNED encounter, sets `round=1` and leaves the turn index untouched. The cockpit therefore displays **Round 1** while the DM is still typing initiative, and `startCombat` sets round 1 again a moment later.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java:434-444`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: nothing consumed later.

- [ ] **Step 1: Write the failing test**

Append to `SessionEncounterServiceTest`:

```java
    @Test
    void activatingAPlannedEncounterDoesNotAdvanceTheRoundCounter() {
        var activation = service.activate(campaignId, plannedEncounterId, null);

        var encounter = encounterRepo.findById(plannedEncounterId).orElseThrow();
        assertThat(encounter.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(encounter.getCombatPhase().name()).isEqualTo("SETUP");
        assertThat(encounter.getRound()).as("combat has not started yet").isZero();
        assertThat(encounter.getActiveTurnIndex()).isEqualTo(-1);
        assertThat(activation.status()).isEqualTo("ACTIVE");
    }
```

Adjust `campaignId` / `plannedEncounterId` to the fixture names already used in that test class.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=SessionEncounterServiceTest#activatingAPlannedEncounterDoesNotAdvanceTheRoundCounter`
Expected: FAIL — `expected: 0 but was: 1`.

- [ ] **Step 3: Align the two activation paths**

Replace `activateFresh` in `EncounterService.java`:

```java
    public Encounter activateFresh(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        if (e.getStatus() != Encounter.Status.PLANNED) {
            throw new IllegalStateException("Only planned encounters can be activated fresh");
        }
        e.setStatus(Encounter.Status.ACTIVE);
        // Matches activate(UUID): the round counter belongs to combat, and combat has not
        // started until startCombat sets RUNNING. Showing "Round 1" during setup is a lie.
        e.setCombatPhase(Encounter.CombatPhase.SETUP);
        e.setRound(0);
        e.setActiveTurnIndex(-1);
        encounterRepo.save(e);
        placementService.autoPlaceUnplaced(encounterId);
        return e;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=SessionEncounterServiceTest`
Expected: PASS

- [ ] **Step 5: Run the gate, which reads the round counter**

Run: `./mvnw -o test -Dtest=ReleaseRehearsalTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterServiceTest.java
git commit -m "fix: keep the round counter at zero until combat actually starts"
```

---

### Task 9: The map module follows the activated encounter

`SessionEncounterService.activate` sets the session workspace map and returns `workspaceMapId`. The client discards it: `activateEncounter` refreshes only `['story', 'encounter']`. During the walkthrough the Map module still read *No map selected* while a mapped encounter was active in the Combat preset.

**Files:**
- Modify: `src/main/resources/static/js/session-cockpit.js:546-560`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java`

**Interfaces:**
- Consumes: `EncounterActivationDto.workspaceMapId`, already returned by the server.
- Produces: nothing consumed later.

- [ ] **Step 1: Write the failing test**

Append to `CockpitModuleFitTest`:

```java
    @Test
    void activatingAnEncounterShowsItsMap() {
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
        page.evaluate("() => window.cockpitLayout.applyPreset('builtin:combat', { skipDirtyCheck: true })");
        page.waitForFunction("() => document.querySelector('#cockpitPresetPicker')?.value === 'builtin:combat'");

        page.locator(".planned-encounter-row button").first().click();
        page.waitForSelector("[data-runtime-module='encounter'] [data-initiative-setup]");

        page.waitForFunction(
                "() => !document.querySelector(\"[data-runtime-module='map']\")?.innerText.includes('No map selected')");
        assertThat(page.locator("[data-runtime-module='map']").innerText())
                .as("the activated encounter's map is displayed")
                .doesNotContain("No map selected");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=CockpitModuleFitTest#activatingAnEncounterShowsItsMap`
Expected: FAIL — the wait times out because the map module still reads "No map selected".

- [ ] **Step 3: Use the map id the server already returns**

Replace `activateEncounter` in `session-cockpit.js`:

```javascript
        async activateEncounter(encounterId, disposition) {
            const body = disposition ? { activeEncounterDisposition: disposition } : {};
            const resp = await this.request(
                `/api/v1/campaigns/${this.campaignId}/session/encounters/${encounterId}/activate`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body),
                });
            const result = await resp.json();
            if (window.battleMap) {
                window.battleMap.setActiveEncounter(encounterId);
            }
            // Activation moves the session's workspace map server-side. Without adopting it the
            // Map module keeps rendering whatever was selected before — usually nothing.
            if (result.workspaceMapId && result.workspaceMapId !== this.currentMapId) {
                this.currentMapId = result.workspaceMapId;
            }
            this.refreshModules(['story', 'encounter', 'map'], 'encounter-activated');
            return result;
        },
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -o test -Dtest=CockpitModuleFitTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/session-cockpit.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java
git commit -m "fix: adopt the workspace map returned by encounter activation"
```

---

### Task 10: Finished encounters can be reopened from the cockpit

`SessionEncounterService.activate` has a deliberate, exhaustively-switched `case DONE -> encounterService.reopen(encounterId)`. `CockpitRuntimeModuleViewService.encounter` lists only PLANNED and SUSPENDED, so that branch is unreachable from the session UI. A fight ended by mistake can only be recovered from the encounter admin page in another tab.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java:273-316`
- Modify: `src/main/resources/templates/session/_encounter-rail.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewServiceTest.java`

**Interfaces:**
- Consumes: `PlannedEncounterView` and `ReadinessVerdict` from Task 7.
- Produces: `EncounterView.finished()` — at most five most-recently-finished encounters.

- [ ] **Step 1: Write the failing test**

Append to `CockpitRuntimeModuleViewServiceTest`:

```java
    @Test
    void finishedEncountersRemainReopenableFromTheCockpit() {
        var view = service.encounter(campaignId);

        assertThat(view.finished())
                .as("a fight ended by mistake must be recoverable without leaving the session")
                .extracting(CockpitRuntimeModuleViewService.PlannedEncounterView::name)
                .contains("Finished Fight");
    }
```

Seed a DONE encounter named `Finished Fight` in that class's existing `@BeforeEach` fixture.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=CockpitRuntimeModuleViewServiceTest#finishedEncountersRemainReopenableFromTheCockpit`
Expected: FAIL to compile — `EncounterView` has no `finished()`.

- [ ] **Step 3: Add the finished list**

In `CockpitRuntimeModuleViewService.java`, add a `List<PlannedEncounterView> finished` component as the final element of the `EncounterView` record, then inside `encounter(UUID)` add:

```java
        List<Encounter> finished = encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(e -> e.getStatus() == Encounter.Status.DONE)
                .limit(5)
                .toList();
        finished.forEach(e -> Hibernate.initialize(e.getMap()));
```

and extend the returned `EncounterView` with `List.copyOf(finished.stream().map(toView).toList())`.

- [ ] **Step 4: Render the section**

In `src/main/resources/templates/session/_encounter-rail.html`, after the suspended section (line 62), add:

```html
  <div class="sidebar-section" th:if="${!view.finished.isEmpty()}">
    <h3>Finished this session</h3>
    <th:block th:each="enc : ${view.finished}">
      <div class="planned-encounter-row">
        <span class="planned-encounter-row__title" th:text="${enc.name}">Goblin Ambush</span>
        <span class="planned-encounter-row__meta"
              th:text="${enc.combatantCount} + (${enc.combatantCount} == 1 ? ' combatant' : ' combatants')">6 combatants</span>
        <button class="btn btn-ghost encounter-rail__action"
                th:attr="data-encounter-id=${enc.id}"
                @click="runEncounter($el.dataset.encounterId)">Reopen</button>
      </div>
    </th:block>
  </div>
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -o test -Dtest='dev.hendrikhoemberg.dmhelper.session.**'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java \
        src/main/resources/templates/session/_encounter-rail.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewServiceTest.java
git commit -m "feat: expose finished encounters in the cockpit so reopen is reachable"
```

---

## Phase D — Hardening

### Task 11: `mapRegionKey` is validated where it is typed

The scene form offers `mapRegionKey` as free text (`_scene-form.html:45-47`, `placeholder="optional map region hint"`), and `SceneStructuredContentService.updateMetadata` stores whatever arrives. The package schema requires `^[a-z0-9][a-z0-9._-]{0,99}$`, and `CampaignExportCoordinator.validate` throws `IllegalStateException` on any ERROR. A DM who types `Cave, area 3` silently makes the whole campaign un-exportable, with no pointer back to the field. This was hit for real while building the Phandelver package: 67 scenes failed with `SCHEMA_VIOLATION`.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneStructuredContentService.java:94-101`
- Modify: `src/main/resources/templates/adventure/_scene-form.html:45-47`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneStructuredContentServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: nothing consumed later.

- [ ] **Step 1: Write the failing test**

Append to `SceneStructuredContentServiceTest`:

```java
    @Test
    void freeTextMapRegionKeyIsRejectedAtTheFormRatherThanAtExport() {
        assertThatThrownBy(() -> service.updateMetadata(sceneId,
                new SceneStructuredContentService.MetadataRequest(null, null, "Cave, area 3", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("map region key");
    }

    @Test
    void slugMapRegionKeyIsAccepted() {
        service.updateMetadata(sceneId,
                new SceneStructuredContentService.MetadataRequest(null, null, "map-cragmaw-a3", null));
        assertThat(sceneRepository.findById(sceneId).orElseThrow().getMapRegionKey())
                .isEqualTo("map-cragmaw-a3");
    }
```

Match `MetadataRequest`'s real component order in that class.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -o test -Dtest=SceneStructuredContentServiceTest`
Expected: FAIL — the free-text value is stored without complaint.

- [ ] **Step 3: Validate on write**

In `SceneStructuredContentService.java`, add the pattern and check it where `mapRegionKey` is assigned:

```java
    /** Mirrors campaign-format-v2.schema.json#/$defs/key; export refuses anything else. */
    private static final java.util.regex.Pattern MAP_REGION_KEY =
            java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");

    private static String requireValidMapRegionKey(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (!MAP_REGION_KEY.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Invalid map region key '" + trimmed + "'. Use lowercase letters, digits, "
                    + "dots, dashes or underscores, for example map-cragmaw-a3.");
        }
        return trimmed;
    }
```

and replace the direct assignment with `scene.setMapRegionKey(requireValidMapRegionKey(request.mapRegionKey()));`.

- [ ] **Step 4: Tell the DM the rule in the form**

In `src/main/resources/templates/adventure/_scene-form.html`, replace the input at lines 45-47:

```html
        <input type="text" class="form-input" name="mapRegionKey"
               th:value="${scene?.mapRegionKey}"
               pattern="[a-z0-9][a-z0-9._\-]{0,99}"
               placeholder="e.g. map-cragmaw-a3"
               title="Lowercase letters, digits, dots, dashes and underscores only">
        <small class="text-muted">Links this scene to a named map region. Lowercase key, no spaces.</small>
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw -o test -Dtest=SceneStructuredContentServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneStructuredContentService.java \
        src/main/resources/templates/adventure/_scene-form.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneStructuredContentServiceTest.java
git commit -m "fix: validate mapRegionKey on write so free text cannot break campaign export"
```

---

### Task 12: A campaign cannot hold two active encounters

`EncounterRepository.findByCampaignIdAndStatus` returns `Optional<Encounter>`. `EncounterService.activate` guards against a second ACTIVE encounter, but `update(UUID, UpdateRequest)` accepts `status` verbatim — `e.setStatus(Encounter.Status.valueOf(req.status()))` — with no check, and it is reachable through `PUT /api/v1/encounters/{id}`. Two ACTIVE rows make that `Optional` query throw `IncorrectResultSizeDataAccessException`, which takes down the whole cockpit encounter module and the map selection with it.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java:336-353`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: nothing consumed later.

- [ ] **Step 1: Write the failing test**

Append to `EncounterServiceTest`:

```java
    @Test
    void updateCannotCreateASecondActiveEncounter() {
        EncounterDto first = service.create(campaign.getId(), new CreateRequest("First", null));
        service.activate(first.id());
        EncounterDto second = service.create(campaign.getId(), new CreateRequest("Second", null));

        assertThatThrownBy(() -> service.update(second.id(),
                new EncounterService.UpdateRequest("Second", null, "ACTIVE", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o test -Dtest=EncounterServiceTest#updateCannotCreateASecondActiveEncounter`
Expected: FAIL — no exception is thrown and two encounters end up ACTIVE.

- [ ] **Step 3: Guard the status transition**

In `EncounterService.update`, replace the status branch:

```java
        if (req.status() != null) {
            Encounter.Status requested = Encounter.Status.valueOf(req.status());
            if (requested == Encounter.Status.ACTIVE && e.getStatus() != Encounter.Status.ACTIVE) {
                // A second ACTIVE row makes findByCampaignIdAndStatus throw and takes the whole
                // cockpit down. Activation belongs to the session workflow, which handles the
                // existing encounter explicitly.
                encounterRepo.findByCampaignIdAndStatus(e.getCampaign().getId(), Encounter.Status.ACTIVE)
                        .filter(other -> !other.getId().equals(e.getId()))
                        .ifPresent(other -> {
                            throw new IllegalStateException(
                                    "Another encounter is already active; use the session activation workflow");
                        });
            }
            e.setStatus(requested);
        }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -o test -Dtest='dev.hendrikhoemberg.dmhelper.encounter.**'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java
git commit -m "fix: refuse a second active encounter through the generic update endpoint"
```

---

### Task 13: Delete the dead encounter module view

`EncounterModuleViewService` is referenced only by its own test. The live cockpit uses `CockpitRuntimeModuleViewService.encounter`. The dead copy carries a stale `.limit(8)` and its own `ready` formula — exactly the divergence Task 7 removed — so leaving it is an invitation to fix the wrong file.

**Files:**
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/EncounterModuleViewService.java`
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/session/runtime/EncounterModuleViewServiceTest.java`

**Interfaces:**
- Consumes: nothing. Produces: nothing.

- [ ] **Step 1: Prove it is unreferenced**

Run: `grep -rn "EncounterModuleViewService" src/main src/test`
Expected: matches only in the two files being deleted.

- [ ] **Step 2: Delete both files**

```bash
git rm src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/EncounterModuleViewService.java \
       src/test/java/dev/hendrikhoemberg/dmhelper/session/runtime/EncounterModuleViewServiceTest.java
```

- [ ] **Step 3: Run the full suite**

Run: `./mvnw -o test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git commit -m "chore: remove the unused EncounterModuleViewService and its test"
```

---

### Task 14: Full verification

- [ ] **Step 1: Run the whole suite**

Run: `./mvnw -o test`
Expected: PASS. Baseline before this plan was 2678 tests, 0 failures, 0 errors, 6 skipped; this plan adds roughly 10 tests.

- [ ] **Step 2: Walk the cockpit by hand against the real campaign**

With the app running on `127.0.0.1:8081`, open the Phandelver campaign, start a session, apply the Combat preset, and confirm each of the following without using Focus:

- the initiative panel's inputs and the "Roll unset NPCs" button are fully visible and clickable
- a monster group occupies one row with a `+N more` disclosure that expands and collapses
- killing the group's leader moves the disclosure to the survivor that now acts
- the round counter reads 0 during setup and 1 after Start combat
- the Map module shows the activated encounter's map
- an encounter with unplaced combatants runs without a dialog, and its chip reads *Needs setup* rather than *Not ready*
- ending a fight leaves it listed under "Finished this session" with a working Reopen

- [ ] **Step 3: Restore the campaign**

Encounters touched during the walkthrough should be returned to PLANNED with monsters revived and initiatives cleared, and the session abandoned, so the campaign is left ready to play.

---

## Appendix — package content, not application code

These were found during the same walkthrough but are data in `lmop-de3.dmcampaign`, not defects in DMHelper. They need a package rebuild, not a code change, and are listed so they are not lost.

1. **No encounter awards XP.** Every encounter's rewards read `{"currency": [], "items": [], "questObjectiveRefs": []}` with `xpTotal` and `xpPerPc` unset, so `applyRewards` grants nothing. For the stated goal of retiring the PDF, all 50 encounters need XP values.
2. **Placeholder calendar.** `monthNames` are `"1. Monat" … "12. Monat"` and `weekdayNames` are `"1. Tag" … "10. Tag"`, which is why the cockpit header reads `1 1. Monat 1491`. The Calendar of Harptos names (Hammer, Alturiak, Ches, Tarsakh, …) belong here.
3. **No waves on imported encounters.** `ensureMainWave` runs on `create` and `addFromLibrary` but not on package import, so imported encounters have an empty wave list. Combatants with a null wave are treated as active, so play is unaffected, but the wave UI has nothing to show until the DM creates one.
