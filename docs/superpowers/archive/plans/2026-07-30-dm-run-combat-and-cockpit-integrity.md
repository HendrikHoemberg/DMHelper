# DM Run — Combat and Cockpit Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the session cockpit trustworthy under table pressure — the map survives an encounter switch, every tracker control acts on the creature it is attached to, conditions obey 5e, and the app never claims a failure that did not happen.

**Architecture:** Four ideas carry the plan. First, *one transition, one truth*: activating an encounter moves the encounter id and the map id together through a single method on `BattleMap`, and every projection (canvas, participants, markers, picker) is refreshed from that one transition rather than racing it. Second, *the interactive map is a guest of its container, not its owner*: the Konva stage can be re-attached when the module body is re-rendered, so a re-render is never fatal. Third, *a control belongs to the row it sits in*: the tracker's per-row controls address `c.id`, per-row entry fields hold per-row state, and a static contract test forbids write-only component state from reappearing. Fourth, *persistence failures and validation failures are different events*: `dmRequest` classifies them, and only the former touches the global save indicator.

**Tech Stack:** Spring Boot 4.1, Java 26, Spring Data JPA, H2/Flyway, Thymeleaf, Alpine.js, Konva, JUnit 5, AssertJ, MockMvc, Playwright 1.54.

## Global Constraints

- **Every behavioural fix must be covered by a test that fails before the fix.** Write the test, run it, watch it fail, then implement. The three vehicles are: source-text contract tests (`*ContractTest`), Spring service/MockMvc tests, and Playwright browser tests.
- **Browser tests must assert the absence of failed network responses.** Every new Playwright test attaches a `BrowserFailureCollector` and calls `assertNoFailures()` in `@AfterEach`. Finding A1 would have been caught by that assertion alone.
- **No new console errors or warnings.** `BrowserFailureCollector` fails the test on `page.onPageError` and on console errors. J4 resolves the one pre-existing Konva warning.
- **Minimum supported viewport is 1366×768** (product decision D-6). Every layout rule in this plan must hold at that size. The default right rail is 360px wide; module-internal breakpoints use `@container cockpit-module (…)`, never `@media`, because modules are user-resizable.
- **The server is the single authority for turn eligibility and readiness.** Templates and JS consume `actsForGroup` and `ReadinessVerdict`; they never re-derive the rules.
- **UI copy is English.** German content in fixtures and the evaluated campaign is *data*.
- **No browser-native `prompt()`, `confirm()`, or `alert()` may be introduced.** `confirmAbandonSession` keeps its existing `window.confirm` (a pre-existing, tested exception); everything new uses an application `<dialog>`.
- **Every mutating UI action provides busy, success, validation, and retry/error feedback.**
- **No regression in the surfaces §18 of the spec records as sound:** scene rendering with Read-Aloud and Development callouts, the trap surface and its dice prefill, damage application and bloodied/defeated states, group survival after all members are defeated, initiative persistence across reload, the `End active encounter?` confirmation, the review draft's existing sections, and the library statblock page.
- Run the named test after each task and the phase-specific acceptance set at each phase
  boundary. Task 28 runs the complete offline suite with `./mvnw -o test`.

## Product decisions (settled)

| ID | Decision |
|----|----------|
| D-1 | The in-fight statblock is the **full library projection** — abilities, speed, senses, languages, saves, skills, traits, actions, bonus actions, and spellcasting entries carried by the action list. `_tracker.html` already contains the markup for most of it and renders nothing because the endpoint returns a 7-field summary. Task 12 fixes the endpoint, which lights up both surfaces at once. |
| D-2 | **Reference moves to its own zone** in the Combat preset: `zone("story", "party", "reference")` on the left, `zone("encounter")` alone on the right. The tracker never leaves the screen. |
| D-3 | **Cancel Review is a non-destructive back-out.** The session returns to the status it held before review and the draft body survives. Needs one new column. |
| D-4 | A state-changing action whose surface is not in the active preset **reveals that module into the current layout** and announces it. If the module has no home in the active preset, offer the preset switch in the notice. |
| D-5 | The app **reports XP and offers to apply it.** The end-of-encounter summary states the total and per-PC share; an explicit action awards it. |
| D-6 | **Minimum supported viewport: 1366×768.** |

---

## File and Interface Map

### New files

| Path | Responsibility |
|---|---|
| `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockRuntimeProjection.java` | The one DTO shape a DM needs to run a creature. Shared by the library API and the cockpit reference. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/InGameDateFormatter.java` | One in-game date formatter for the cockpit header, the draft, and the calendar page. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterXpCalculator.java` | Derives an XP total from defeated combatants' statblocks when the encounter declares none. |
| `src/main/resources/db/migration/V29__session_pre_review_status.sql` | Remembers the status a session held before review. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java` | A1/A2/A3 — encounter activation moves the whole map surface coherently. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java` | B1–B5 — group rows, per-row targeting, selection, per-row delta, name legibility. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerContractTest.java` | Static contract over `combat-tracker.js` + `_tracker.html`: no write-only state, no shared per-row models. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/ConditionDefaultsTest.java` | C1 — the condition duration table. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java` | D1/D2/D3/D4 — search results render, statblock is runnable, tracker stays visible. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionReviewCancelTest.java` | E1 — cancelling a review restores the prior status and keeps the draft. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/FailureSignallingBrowserTest.java` | F1/F2/F3 — validation vs. save failure, toast policy, off-screen module errors. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/EncounterDiscoverabilityBrowserTest.java` | G1/G3 — running an encounter is visible; re-running a finished one is announced. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterXpCalculatorTest.java` | H1 — XP derivation. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/EncounterRailOrderingTest.java` | I1 — natural ordering and current-scene grouping. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementFramingTest.java` | J1 — auto-placement is framed and unclipped. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresentationContractTest.java` | K1–K15 — copy, affordance, and layout contracts. |

### Existing files with changed responsibilities

| Path | Change |
|---|---|
| `static/js/map/battle-map.js` | Gains `reattach(container)` and `setEncounterAndMap(encounterId, mapId)`; consolidates six Konva layers to four; renders tokens with separation, ordinals, and a shape-based PC/NPC distinction. |
| `static/js/session-cockpit.js` | `activateEncounter` becomes one ordered transition with an in-app retry; the map module's `cockpit:module-content-ready` handler reattaches instead of assuming the container survived; token events carry their map id. |
| `static/js/combat-tracker.js` | Per-row `hpDeltas`; `showConditionMenu` removed; group label derivation; condition catalog defaults to indefinite; `endEncounter` goes through `end-with-summary`. |
| `static/js/cockpit-modules.js` | An off-screen load failure raises module attention and survives until the module is shown. |
| `static/js/cockpit-layout.js` | Splitters explain their inert state outside edit mode; the attention badge is separated from the tab label; `revealModule` is used by the run-encounter flow. |
| `static/js/runtime-status.js` | Only persistence failures touch the save indicator. |
| `static/js/dm-request.js` | `DmRequestError` gains `kind` (`'validation' | 'conflict' | 'server' | 'network'`); `reportActionFailure` follows the toast policy. |
| `templates/encounter/_tracker.html` | Row layout, group toggle, per-row condition control, selection marker, detail heading, condition badges, header layout, initiative-setup copy. |
| `templates/session/modules/_reference.html` | Binds the fields the payload actually has; renders the full statblock. |
| `templates/session/_map-module.html` | Participants and markers show staleness rather than old data. |
| `templates/session/_lifecycle-dialog.html` | Real status heading, themed controls, separated destructive actions. |
| `templates/session/_encounter-rail.html` | Current-scene grouping, natural ordering, one-line meta. |
| `templates/session/_story-rail.html` | Scene action labels and affordances. |
| `static/css/components.css` | Row grid, group toggle, condition badges, selected-row treatment, dice panel width. |
| `layout/CockpitBuiltInPresetCatalog.java` | Reference leaves the encounter zone. |
| `session/service/SessionLifecycleService.java` | `beginReview` records the prior status; `cancelReview` restores it and keeps the draft. |
| `session/service/SessionDraftService.java` | Draft encounter lines carry the XP total; dates go through `InGameDateFormatter`. |
| `session/runtime/CockpitRuntimeModuleViewService.java` | Natural ordering, current-scene grouping, finished-encounter marker. |
| `library/web/LibraryApiController.java` | `/statblocks/{id}` returns the runtime projection. |
| `encounter/service/EncounterPlacementService.java` | Auto-placement centres on the map and never starts at row 0 col 0. |
| `encounter/service/EncounterService.java` | `buildSummary` fills `rewardsDraft.xpTotal` from defeated combatants when unset. |
| `calendar/service/CalendarService.java` | Delegates all human-facing in-game dates to `InGameDateFormatter`. |
| `templates/calendar/_current-date.html` | Uses the shared date formatter instead of rebuilding a date from raw calendar fields. |
| `templates/threat/_mechanics-card.html` | Humanizes damage types and gives dice-prefill actions a resting affordance. |
| `templates/party/_summary-bar.html` | Gives every compact stat an expanded accessible name and visible separation. |

### Stable interfaces introduced by this plan

```javascript
// battle-map.js — the two methods the cockpit calls for a coherent transition.
// reattach moves the existing Konva stage into a freshly rendered container.
// Returns true when the stage now lives in `container`.
reattach(container)            // -> boolean

// One transition: sets activeEncounterId, switches the map if it differs, refetches
// tokens exactly once, and emits maploaded/tokenupdate/state-changed at the end.
// Returns true on success; on failure the previous map and encounter are kept.
async setEncounterAndMap(encounterId, mapId)   // -> Promise<boolean>

// Every token event now carries the map the tokens belong to.
// window 'battle-tokenupdate' detail: { tokens: Array, mapId: string|null }
```

```javascript
// dm-request.js
class DmRequestError extends Error {
  status;          // number, 0 for network failures
  correlationId;   // string|null
  kind;            // 'validation' | 'conflict' | 'server' | 'network'
  problem;         // parsed problem+json body or null
  retryable;       // boolean — false for 'validation'
}
```

```java
// StatBlockRuntimeProjection — everything needed to run a creature's turn.
// The entity stores traits/actions/bonusActions/reactions/legendaryActions as JSON arrays of
// {name, description}, with "+N to hit" and "(NdM+K)" embedded in `name`; skills, senses,
// languages and the damage/condition lists are already display strings.
public record StatBlockRuntimeProjection(
        UUID id, String name, String size, String type, String alignment, String cr,
        int ac, String hp, int xp, String speed, String senses, String languages,
        String skills, String damageResistances, String damageImmunities,
        String conditionImmunities,
        Map<String, Integer> abilityScores,
        List<Save> savingThrows,
        List<Entry> traits, List<Entry> actions, List<Entry> bonusActions,
        List<Entry> reactions, List<Entry> legendaryActions) {

    public record Save(String ability, int modifier) {}
    public record Entry(String name, String description,
                        Integer attackBonus, String damageExpression) {}

}
```

`StatBlockRuntimeProjection.from(StatBlock, ObjectMapper)` is implemented in full in
Task 12, Step 3; the record components above are the cross-task contract.

```java
// EncounterXpCalculator
public static int xpFromDefeated(List<Combatant> combatants);   // sums StatBlock.xp of defeated, non-PC combatants
public static int xpPerPc(int total, int pcCount);              // total / max(1, pcCount), floor
```

```java
// InGameDateFormatter — one renderer for in-game dates.
// Real month name  -> "12. Hammer 1491"
// Placeholder name -> "12.1.1491"  (a placeholder matches ^\d+\.?\s|^Month[- ]\d+$)
public static String format(int year, int month, int day, String[] monthNames);
```

```java
// SessionLifecycleService
// beginReview stores the status it came from; cancelReview restores it and keeps draftBody.
public CampaignSession beginReview(UUID campaignId);
public CampaignSession cancelReview(UUID campaignId);
```

---

## Shared test fixtures

Two fixture methods are added once in Task 1 and used by many later tasks. Their signatures are fixed here so tasks can rely on them.

```java
// CockpitInitialLoadFixtures
/** Campaign with a running session, two maps, and one PLANNED encounter on each map. */
public TwoEncounters campaignWithTwoEncountersOnTwoMaps();
public record TwoEncounters(UUID campaignId, UUID mapA, UUID mapB,
                            UUID encounterA, UUID encounterB) {}

/** Campaign with a running session and one ACTIVE encounter holding four PCs
 *  and one four-member monster group named "Goblin 1".."Goblin 4". */
public GroupedEncounter campaignWithGroupedEncounter();
public record GroupedEncounter(UUID campaignId, UUID mapId, UUID encounterId,
                               String groupId, List<UUID> memberIds) {}
```

---

## Phase A — Runtime map integrity

The whole phase exists because `activateEncounter` performs three half-transitions instead of one. It sets `this.currentMapId` from the activation response, tells `BattleMap` about the new encounter (which immediately refetches tokens against the **old** `bm.mapId` — a 400 the server is documented to reject), and then force-reloads the map module body, which `replaceChildren`s away the `#battleCanvasWrap` the Konva stage is mounted in. `initBattleMap` refuses to build a second stage (`if (this._battleMapInitStarted || window.battleMap) return;`), so nothing re-mounts. Six canvases become zero.

### Task 1: The battle map survives a module re-render

`cockpit-modules.js` replaces `[data-module-content]`'s children on every load. For every other module that is correct. For `map` it detaches a live Konva stage: `stage.container()` still points at the discarded div, `resizeToContainer()` measures a node that is no longer in the document, and nothing renders. A detached map must never be a resting state (spec A1.3).

Konva's `Stage#container(newDiv)` moves the stage's own content element into a new parent. That is the whole fix, plus a handler that notices the container changed.

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js` (add `reattach` after `resizeToContainer`, ~line 813)
- Modify: `src/main/resources/static/js/session-cockpit.js:413-426` (the `cockpit:module-content-ready` handler)
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java` (add the two shared fixtures)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `BattleMap#reattach(container) -> boolean`; the two fixture methods declared in *Shared test fixtures* above. Tasks 2, 3, 4, 6, 7, 8, 9 all use the fixtures.

- [ ] **Step 1: Add the shared fixtures**

Append to `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java`, inside the class. Add `import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;`, `import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;`, `import java.util.ArrayList;`, `import java.util.List;` and constructor-inject `StatBlockRepository statBlocks` alongside the existing dependencies.

```java
    public record TwoEncounters(UUID campaignId, UUID mapA, UUID mapB,
                                UUID encounterA, UUID encounterB) {}

    /** Two PLANNED encounters, each bound to its own map. Reproduces the A1 transition. */
    @Transactional
    public TwoEncounters campaignWithTwoEncountersOnTwoMaps() {
        UUID campaignId = campaignWithRunningSession();
        Campaign campaign = campaigns.findById(campaignId).orElseThrow();
        GameMap mapA = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst();

        GameMap mapB = new GameMap();
        mapB.setCampaign(campaign);
        mapB.setName("Second Map");
        mapB.setSortOrder(1);
        maps.save(mapB);

        UUID encounterA = plannedEncounter(campaign, mapA, "Ambush on the Path", "Goblin");
        UUID encounterB = plannedEncounter(campaign, mapB, "Keep Gatehouse", "Hobgoblin");
        return new TwoEncounters(campaignId, mapA.getId(), mapB.getId(), encounterA, encounterB);
    }

    private UUID plannedEncounter(Campaign campaign, GameMap map, String name, String foe) {
        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setMap(map);
        encounter.setName(name);
        encounter.setStatus(Encounter.Status.PLANNED);
        encounters.save(encounter);
        for (int i = 1; i <= 2; i++) {
            Combatant c = new Combatant();
            c.setEncounter(encounter);
            c.setName(foe + " " + i);
            c.setKind("MONSTER");
            c.setMaxHp(11);
            c.setCurrentHp(11);
            c.setSortOrder(i);
            combatants.save(c);
        }
        return encounter.getId();
    }

    public record GroupedEncounter(UUID campaignId, UUID mapId, UUID encounterId,
                                   String groupId, List<UUID> memberIds) {}

    /** Four PCs plus a four-member monster group, mid-fight. Reproduces B1–B5. */
    @Transactional
    public GroupedEncounter campaignWithGroupedEncounter() {
        UUID campaignId = campaignWithRunningSessionAndFourPartyMembers();
        Campaign campaign = campaigns.findById(campaignId).orElseThrow();
        GameMap map = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst();

        StatBlock goblin = new StatBlock();
        goblin.setCampaign(campaign);
        goblin.setName("Goblin");
        goblin.setCr("1/4");
        goblin.setAc(15);
        goblin.setHp("7 (2d6)");
        goblin.setXp(50);
        statBlocks.save(goblin);

        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setMap(map);
        encounter.setName("Grouped Fixture");
        encounter.setStatus(Encounter.Status.ACTIVE);
        encounter.setCombatPhase(Encounter.CombatPhase.RUNNING);
        encounter.setRound(1);
        encounters.save(encounter);

        int order = 0;
        for (PartyMember pm : partyMembers.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId)) {
            Combatant pc = new Combatant();
            pc.setEncounter(encounter);
            pc.setName(pm.getCharacterName());
            pc.setKind("PC");
            pc.setPartyMember(pm);
            pc.setMaxHp(pm.getMaxHp());
            pc.setCurrentHp(pm.getCurrentHp());
            pc.setInitiative(20 - order);
            pc.setSortOrder(order++);
            combatants.save(pc);
        }

        String groupId = UUID.randomUUID().toString();
        List<UUID> memberIds = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Combatant c = new Combatant();
            c.setEncounter(encounter);
            c.setName("Goblin " + i);
            c.setKind("MONSTER");
            c.setStatBlock(goblin);
            c.setMaxHp(7);
            c.setCurrentHp(7);
            c.setInitiative(12);
            c.setSortOrder(order++);
            c.setGroupId(groupId);
            c.setGroupLeader(i == 1);
            memberIds.add(combatants.save(c).getId());
        }
        return new GroupedEncounter(campaignId, map.getId(), encounter.getId(), groupId, memberIds);
    }
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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

import static org.assertj.core.api.Assertions.assertThat;

/** The map surface is one thing: canvas, map id, encounter id and lists never disagree. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CockpitMapTransitionBrowserTest {

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
        failures.clear();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1366, 768));
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    @Test
    void forceReloadingTheMapModuleLeavesAnAttachedRenderingMap() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => !!window.battleMap");
        page.waitForSelector("#battleCanvasWrap canvas");

        page.evaluate("(mapId) => window.cockpitModules.load('map', { force: true, mapId })",
                seeded.mapA().toString());
        page.waitForFunction(
                "() => document.querySelectorAll('#battleCanvasWrap canvas').length > 0");

        assertThat((Boolean) page.evaluate(
                "() => document.getElementById('battleCanvasWrap')"
                        + ".contains(window.battleMap.stage.container())"))
                .as("the Konva stage must live inside the freshly rendered container")
                .isTrue();
    }
}
```

- [ ] **Step 3: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=CockpitMapTransitionBrowserTest`
Expected: FAIL. The canvas count wait times out — the stage is still mounted in the discarded container, so `#battleCanvasWrap canvas` never reappears.

- [ ] **Step 4: Add `reattach` to BattleMap**

In `src/main/resources/static/js/map/battle-map.js`, immediately after the `resizeToContainer()` method (~line 813):

```javascript
    /**
     * The cockpit re-renders module bodies wholesale, which throws away the div this
     * stage is mounted in. Konva can move a live stage to a new parent, so a re-render
     * costs a reattach rather than the map. A detached map is a bug, never a resting state.
     */
    reattach(container) {
        if (!container) return false;
        if (this.container === container && container.contains(this.stage.container())) {
            return true;
        }
        this.container = container;
        this.stage.container(container);
        this.resizeToContainer();
        this.renderGrid();
        this.renderTokens();
        this.pinLayer.batchDraw();
        return container.contains(this.stage.container());
    }
```

- [ ] **Step 5: Reattach when the map module body is replaced**

In `src/main/resources/static/js/session-cockpit.js`, replace the `cockpit:module-content-ready` handler at lines 413-426 with:

```javascript
            window.addEventListener('cockpit:module-content-ready', (event) => {
                if (event.detail?.moduleKey !== 'map') return;
                this.syncMapPicker();
                const container = document.getElementById('battleCanvasWrap');
                if (!window.battleMap) {
                    // First-time construction: the map fragment (with #battleCanvasWrap) has now
                    // been injected, so the container exists and BattleMap can mount.
                    this.initBattleMap();
                    return;
                }
                // The module body was re-rendered, so the div the stage was mounted in is gone.
                // Move the stage rather than leaving a chrome-only module with no canvas.
                if (container) window.battleMap.reattach(container);
                requestAnimationFrame(() => {
                    window.battleMap.resizeToContainer();
                    window.battleMap.setRenderingActive(true);
                });
            });
```

- [ ] **Step 6: Run the test and watch it pass**

Run: `./mvnw -o test -Dtest=CockpitMapTransitionBrowserTest`
Expected: PASS, 1 test.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/map/battle-map.js \
        src/main/resources/static/js/session-cockpit.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java
git commit -m "fix: re-attach the battle map when its module body is re-rendered"
```

---

### Task 2: Activating an encounter moves map and encounter together

`activateEncounter` calls `bm.setActiveEncounter(id)`, which sets `activeEncounterId` and immediately calls `fetchTokens()` against the stale `bm.mapId`. The server rejects that pair by design (`RuntimeTokenProjectionService.resolveEncounter` throws `IllegalArgumentException("Encounter does not belong to map")`), so three 400s and three toasts. The client owns the ordering; the server guard stays a guard (spec A1.2).

`switchToMap` already does the right thing — it fetches runtime tokens with the *new* map id and the current encounter id, in that order. The fix is to set the encounter first without fetching, then switch the map, and to make the participants list refuse to render tokens that belong to a different map (spec A2).

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js:281-288` (replace `setActiveEncounter`), `:1329-1332` and `:284-286` (token events carry `mapId`)
- Modify: `src/main/resources/static/js/session-cockpit.js:546-571` (`activateEncounter`), `:366-372` and `:391-412` (token event handlers)
- Modify: `src/main/resources/templates/session/_map-module.html:40-70` (participants and markers show staleness)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java`

**Interfaces:**
- Consumes: `BattleMap#reattach` (Task 1), `CockpitInitialLoadFixtures#campaignWithTwoEncountersOnTwoMaps` (Task 1).
- Produces: `BattleMap#setEncounterAndMap(encounterId, mapId) -> Promise<boolean>`; `battle-tokenupdate` detail gains `mapId`; the cockpit component gains `tokensMapId` (string) and the getter `mapProjectionStale` (boolean), both read by `_map-module.html`.

- [ ] **Step 1: Write the failing test**

Add to `CockpitMapTransitionBrowserTest`:

```java
    @Test
    void activatingAnEncounterOnAnotherMapMovesTheWholeMapSurface() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => !!window.battleMap");

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterA().toString());
        page.waitForFunction("(mapId) => window.battleMap.mapId === mapId", seeded.mapA().toString());

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterB().toString());
        page.waitForFunction("(mapId) => window.battleMap.mapId === mapId", seeded.mapB().toString());
        page.waitForSelector("#battleCanvasWrap canvas");

        assertThat(page.evaluate("() => window.battleMap.activeEncounterId"))
                .as("the map must know which encounter it is showing")
                .isEqualTo(seeded.encounterB().toString());
        assertThat(page.locator("#runtimeMapPicker").inputValue())
                .as("the picker must follow the transition")
                .isEqualTo(seeded.mapB().toString());

        var participants = page.locator(".battle-sidebar .token-list-item").allInnerTexts();
        assertThat(participants)
                .as("the participants list must describe encounter B, not the encounter before it")
                .isNotEmpty()
                .allSatisfy(text -> assertThat(text).contains("Hobgoblin"));
        assertThat(participants)
                .noneSatisfy(text -> assertThat(text).contains("Goblin 1"));

        assertThat(page.locator(".toast-error").count())
                .as("a coherent transition produces no error toast")
                .isZero();
    }
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=CockpitMapTransitionBrowserTest#activatingAnEncounterOnAnotherMapMovesTheWholeMapSurface`
Expected: FAIL. `BrowserFailureCollector` reports three `400` responses on `/api/v1/maps/<mapA>/runtime-tokens?encounterId=<encounterB>`, and the participants assertion finds the previous encounter's combatants.

- [ ] **Step 3: Replace `setActiveEncounter` with one ordered transition**

In `src/main/resources/static/js/map/battle-map.js`, replace lines 281-288:

```javascript
    /* ---- Tokens: fetch, render, drag ---- */
    /**
     * One transition. The runtime-tokens endpoint rejects a (map, encounter) pair that does
     * not belong together, so the encounter id and the map id must move in the same step —
     * never encounter-then-fetch against the map we are about to leave.
     * Returns false and keeps the previous map and encounter if the switch fails.
     */
    async setEncounterAndMap(encounterId, mapId) {
        const previousEncounterId = this.activeEncounterId;
        this.activeEncounterId = encounterId;
        if (mapId && mapId !== this.mapId) {
            const switched = await this.switchToMap(mapId);
            if (!switched) {
                this.activeEncounterId = previousEncounterId;
                return false;
            }
            return true;
        }
        if (await this.fetchTokens() === false) {
            this.activeEncounterId = previousEncounterId;
            return false;
        }
        this.renderTokens();
        this.emitTokens();
        this.emit('state-changed');
        return true;
    }

    emitTokens() {
        this.emit('tokenupdate', { tokens: this.tokens, mapId: this.mapId });
    }

    async setActiveEncounter(encounterId) {
        return this.setEncounterAndMap(encounterId, this.mapId);
    }
```

Then replace every remaining `this.emit('tokenupdate', { tokens: this.tokens })` in the file with `this.emitTokens()`. There are two: in `emitState()` (~line 145) and in `switchToMap` (~line 1330).

- [ ] **Step 4: Make `activateEncounter` a single transition**

In `src/main/resources/static/js/session-cockpit.js`, replace `activateEncounter` (lines 546-571):

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
            await this.applyActivation(encounterId, result);
            return result;
        },

        // The map surface must describe one encounter and one map at every moment a DM could
        // look at it. Move the interactive map first — it owns the only request the server
        // will reject for an inconsistent pair — and refresh the server-rendered chrome after.
        async applyActivation(encounterId, result) {
            const mapId = result.workspaceMapId || this.currentMapId || null;
            if (window.battleMap) {
                const moved = await window.battleMap.setEncounterAndMap(encounterId, mapId);
                if (!moved) {
                    // Keep every Alpine projection aligned with the map that BattleMap retained.
                    this.currentMapId = window.battleMap.mapId || '';
                    this.syncMapPicker();
                    return false;
                }
            }
            this.currentMapId = mapId || '';
            this.visitedMapIds.add(this.currentMapId);
            this.syncMapPicker();
            this.refreshModules(['story', 'encounter'], 'encounter-activated');
            if (this.currentMapId) {
                await this.refreshThreatPins();
            }
            return true;
        },
```

Delete the `window.cockpitModules?.load('map', { force: true, mapId })` call and its comment. The map module body no longer needs a server round trip: `currentMapId`, `maps`, and `tokens` are all Alpine state in this component, and the interactive map has already moved. Task 3 restores the one thing the server render did provide.

- [ ] **Step 5: Track which map the tokens describe**

In `src/main/resources/static/js/session-cockpit.js`, add `tokensMapId: ''` next to `currentMapId` in the component's state (line 15), then replace the `battle-tokenupdate` handler at lines 366-372:

```javascript
            window.addEventListener('battle-tokenupdate', (e) => {
                this.tokens = e.detail.tokens;
                this.tokensMapId = e.detail.mapId || '';
                if (this.selectedToken) {
                    const updated = this.tokens.find(t => t.id === this.selectedToken.id);
                    if (updated) this.selectedToken = updated;
                }
            });
```

Add this getter next to the other getters in the component:

```javascript
        // Old data presented as current is worse than no data. The lists below the map are
        // projections of (active map, active encounter); when they lag, they say so.
        get mapProjectionStale() {
            return !!this.currentMapId && this.tokensMapId !== this.currentMapId;
        },
```

- [ ] **Step 6: Make the sidebar lists show staleness instead of stale data**

In `src/main/resources/templates/session/_map-module.html`, replace lines 40-70 (the `<aside>`'s first two sections):

```html
        <aside class="battle-sidebar" aria-label="Map tokens">
          <p class="map-projection-stale u-text-xs text-muted" role="status"
             x-show="mapProjectionStale" x-cloak>
            Refreshing participants and markers for this map…
          </p>
          <section class="sidebar-section" x-show="!mapProjectionStale">
            <h3>Encounter participants</h3>
            <template x-for="t in tokens.filter(tk => tk.source === 'COMBATANT')" :key="t.id">
              <div class="token-list-item" @click="focusToken(t.id)">
                <span class="token-swatch" :style="{ background: t.color }"></span>
                <span :class="{ 'token-dead': t.defeated }" x-text="t.name" style="flex: 1;"></span>
                <span x-show="t.currentHp != null && t.maxHp != null"
                      x-text="t.currentHp + '/' + t.maxHp"></span>
              </div>
            </template>
            <template x-if="tokens.filter(tk => tk.source === 'COMBATANT').length === 0">
              <p class="text-muted u-text-xs">No combatants placed on this map.</p>
            </template>
          </section>
          <section class="sidebar-section" x-show="!mapProjectionStale">
            <h3>Map markers</h3>
            <template x-for="t in tokens.filter(tk => tk.source === 'MARKER')" :key="t.id">
              <div class="token-list-item" @click="focusToken(t.id)">
                <span class="token-swatch" :style="{ background: t.color }"></span>
                <span x-text="t.name" style="flex: 1;"></span>
                <button type="button" class="btn btn-ghost" title="Duplicate token"
                        @click.stop="duplicateToken(t.id)">Copy</button>
                <button type="button" class="btn btn-ghost" title="Delete token"
                        @click.stop="requestTokenDelete(t.id)">Delete</button>
              </div>
            </template>
            <template x-if="tokens.filter(tk => tk.source === 'MARKER').length === 0">
              <p class="text-muted u-text-xs">No markers on this map.</p>
            </template>
          </section>
```

- [ ] **Step 7: Run the test and watch it pass**

Run: `./mvnw -o test -Dtest=CockpitMapTransitionBrowserTest`
Expected: PASS, 2 tests. `assertNoFailures()` proves no 4xx was issued during the transition.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/js/map/battle-map.js \
        src/main/resources/static/js/session-cockpit.js \
        src/main/resources/templates/session/_map-module.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java
git commit -m "fix: move the map and the encounter in one transition on activation"
```

---

### Task 3: The map transition recovers in-app

Once A1 happened, nothing but `F5` brought the map back: the toasts offered "Retry", but that retry re-ran `fetchTokens()` against the same impossible (map, encounter) pair. A retry that cannot succeed is worse than none (spec A1.4, A3).

The map module also needs its own retry callback registered, so the module shell's Retry button re-runs the whole transition rather than a sub-request.

**Files:**
- Modify: `src/main/resources/static/js/session-cockpit.js` (register a map retry, harden `_activateOrNotify`)
- Modify: `src/main/resources/static/js/map/battle-map.js:320` (`fetchTokens` failure retries the transition, not the request)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java`

**Interfaces:**
- Consumes: `BattleMap#setEncounterAndMap` and `applyActivation` (Task 2).
- Produces: `sessionCockpit#retryMapSurface() -> Promise<boolean>`, registered as the `map` module's retry callback and used by the failure toast.

- [ ] **Step 1: Write the failing test**

Add to `CockpitMapTransitionBrowserTest`:

```java
    @Test
    void aFailedMapLoadRecoversFromTheModulesOwnRetry() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => !!window.battleMap");
        page.waitForSelector("#battleCanvasWrap canvas");

        // One transport failure, then the network heals. The module must recover without a
        // navigation: "reload the page" is advice, never the only mechanism.
        failures.expectConsoleError(java.util.regex.Pattern.compile("runtime-tokens"));
        page.evaluate("""
                () => {
                  window.__navigations = 0;
                  const push = history.pushState.bind(history);
                  history.pushState = (...args) => { window.__navigations++; return push(...args); };
                  const original = window.dmRequest;
                  let failed = false;
                  window.dmRequest = (url, options) => {
                    if (!failed && String(url).includes('/runtime-tokens')) {
                      failed = true;
                      return Promise.reject(new window.DmRequestError('boom', 503, null));
                    }
                    return original(url, options);
                  };
                }
                """);

        String beforeMapId = seeded.mapA().toString();
        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id).catch(() => {})", seeded.encounterA().toString());
        page.waitForSelector("[data-runtime-module='map'] [data-module-retry]");

        page.click("[data-runtime-module='map'] [data-module-retry]");
        page.waitForFunction("(mapId) => window.battleMap.mapId === mapId", beforeMapId);
        page.waitForSelector("#battleCanvasWrap canvas");

        assertThat(page.evaluate("() => window.__navigations")).isEqualTo(0);
        assertThat((Boolean) page.evaluate(
                "() => document.getElementById('battleCanvasWrap')"
                        + ".contains(window.battleMap.stage.container())"))
                .isTrue();
    }
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=CockpitMapTransitionBrowserTest#aFailedMapLoadRecoversFromTheModulesOwnRetry`
Expected: FAIL. No `[data-module-retry]` appears in the map module, so `waitForSelector` times out.

- [ ] **Step 3: Give the cockpit one recovery entry point**

In `src/main/resources/static/js/session-cockpit.js`, add next to `applyActivation`:

```javascript
        // The only retry worth offering is one that can succeed. Re-running the whole
        // transition can; re-running the one sub-request that failed because the map and the
        // encounter disagreed cannot.
        async retryMapSurface() {
            const encounterId = this.activeEncounter?.id
                || window.battleMap?.activeEncounterId
                || null;
            const container = document.getElementById('battleCanvasWrap');
            if (window.battleMap && container) window.battleMap.reattach(container);
            if (!window.battleMap) {
                this.initBattleMap();
                return true;
            }
            const ok = await window.battleMap.setEncounterAndMap(
                encounterId, this.currentMapId || null);
            if (ok) {
                window.cockpitLayout?.setModuleState?.('map', 'ready');
                this.syncMapPicker();
            }
            return ok;
        },

        _registerMapRecovery() {
            if (this._mapRecoveryBound) return;
            this._mapRecoveryBound = true;
            window.dispatchEvent(new CustomEvent('cockpit:module-state', {
                detail: { moduleKey: 'map', state: 'ready', retry: () => this.retryMapSurface() }
            }));
        },
```

Call `this._registerMapRecovery();` from `init()`, immediately after `this._bindMapVisibility();` (line 464).

- [ ] **Step 4: Route the map's own failures through it**

In `src/main/resources/static/js/map/battle-map.js`, replace the `catch` in `fetchTokens` (line 319-322):

```javascript
        } catch (error) {
            // Retrying this one request cannot help when the failure is that the map and the
            // encounter disagree. Hand recovery to the surface that owns both.
            this._failure('Could not load map tokens.', error,
                () => window.cockpitSession?.retryMapSurface?.() ?? this.fetchTokens());
            this.emit('load-failed', { mapId: this.mapId, encounterId: this.activeEncounterId });
            return false;
        }
```

- [ ] **Step 5: Surface the failure as a module error state**

In `src/main/resources/static/js/session-cockpit.js`, add inside `init()` right after `this._registerMapRecovery();`:

```javascript
            window.addEventListener('battle-load-failed', () => {
                window.dispatchEvent(new CustomEvent('cockpit:module-state', {
                    detail: {
                        moduleKey: 'map',
                        state: 'error',
                        message: 'The map could not be brought up to date.',
                        retry: () => this.retryMapSurface(),
                    }
                }));
            });
```

Expose the component so `battle-map.js` can reach it: at the end of the component's `init()`, add `window.cockpitSession = this;`.

- [ ] **Step 6: Run the test and watch it pass**

Run: `./mvnw -o test -Dtest=CockpitMapTransitionBrowserTest`
Expected: PASS, 3 tests.

- [ ] **Step 7: Run the phase suite**

Run: `./mvnw -o test -Dtest='Cockpit*Test,SessionCockpit*Test,MapEditorBrowserTest'`
Expected: PASS, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/js/session-cockpit.js \
        src/main/resources/static/js/map/battle-map.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java
git commit -m "fix: give the map surface an in-app recovery path"
```

---

## Phase B — Combat tracker identity and targeting safety

Four findings compound here. The DM cannot read who a row is (B1, B5), cannot see which row the editor is bound to (B3), one control silently addresses a different creature (B2), and one entry field shows one row's typing in every row (B4). The root cause of B1 and B5 is the same: `.combatant-row` is a flex line that hands its fixed columns — initiative badge, HP text, HP bar, delta input, condition icons, add-condition button — about 296px of a 360px rail and leaves the name whatever is left. The row becomes a grid with two lines.

### Task 4: A group row carries a stable label and a reachable toggle

`_tracker.html` puts the `.group-count` toggle *inside* `.combatant-name`, which is `overflow: hidden; text-overflow: ellipsis`. Ellipsis truncation applies to the text run; an inline-block sibling is clipped out of the box entirely. At 360px neither survives, and the row renders `— 11/11`. Separately, the group row follows the *acting* member, so the label changed from `Goblin 1` to `Goblin 3` as members died and the group appeared to become a different creature (K13).

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html:336-391` (the initiative list row)
- Modify: `src/main/resources/static/js/combat-tracker.js` (add `groupLabel`, `nameBase`, `nameOrdinal`)
- Modify: `src/main/resources/static/css/components.css:776-857` (the row grid, name split, toggle)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java`

**Interfaces:**
- Consumes: `CockpitInitialLoadFixtures#campaignWithGroupedEncounter` (Task 1).
- Produces: `combatTracker#groupLabel(groupId) -> string`, `#nameBase(c) -> string`, `#nameOrdinal(c) -> string`. The row is a CSS grid with areas `init`, `name`, `hp`, `toggle`, `meta`; Tasks 7, 8, 9 and 13 place elements into those areas.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A DM must be able to tell who a row is, and every control must act on that row. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackerIdentityBrowserTest {

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
        failures.clear();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1366, 768));
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    /** Opens the cockpit in the Combat preset with the grouped fixture loaded. */
    private CockpitInitialLoadFixtures.GroupedEncounter openCombat() {
        var seeded = fixtures.campaignWithGroupedEncounter();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector(".combatant-row");
        return seeded;
    }

    @Test
    void theGroupRowIsLabelledAndItsToggleIsInsideTheModule() {
        openCombat();

        var groupRow = page.locator(".combatant-row:has(.group-count)").first();
        assertThat(groupRow.locator(".combatant-name").innerText().trim())
                .as("a combatant row always renders an identifying label")
                .isNotEmpty()
                .isEqualTo("Goblin");

        var toggleBox = groupRow.locator(".group-count").boundingBox();
        var moduleBox = page.locator("[data-runtime-module='encounter']").boundingBox();
        assertThat(toggleBox).isNotNull();
        assertThat(toggleBox.x).isGreaterThanOrEqualTo(moduleBox.x);
        assertThat(toggleBox.x + toggleBox.width)
                .as("the group toggle must not be clipped out of the module")
                .isLessThanOrEqualTo(moduleBox.x + moduleBox.width);
        assertThat(toggleBox.width).isGreaterThan(0);
    }

    @Test
    void theGroupTogglesByPointerInBothDirections() {
        openCombat();
        var groupToggle = page.locator(".combatant-row .group-count").first();

        assertThat(groupToggle.getAttribute("aria-expanded")).isEqualTo("false");
        assertThat(page.locator(".combatant-row .group-branch").count()).isZero();

        groupToggle.click();
        page.waitForSelector(".combatant-row .group-branch");
        assertThat(groupToggle.getAttribute("aria-expanded")).isEqualTo("true");
        assertThat(page.locator(".combatant-row .group-branch").count()).isEqualTo(3);

        groupToggle.click();
        page.waitForFunction(
                "() => document.querySelectorAll('.combatant-row .group-branch').length === 0");
        assertThat(groupToggle.getAttribute("aria-expanded")).isEqualTo("false");
    }

    @Test
    void expandedGroupMembersArePairwiseDistinct() {
        openCombat();
        page.locator(".combatant-row .group-count").first().click();
        page.waitForSelector(".combatant-row .group-branch");

        List<String> labels = page.locator(".combatant-row:has(.group-branch) .combatant-name")
                .allInnerTexts().stream().map(String::trim).toList();
        assertThat(labels).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    void theGroupLabelSurvivesItsMembersDying() {
        var seeded = openCombat();
        String before = page.locator(".combatant-row:has(.group-count) .combatant-name")
                .first().innerText().trim();

        page.evaluate("""
                async (ids) => {
                  for (const id of ids.slice(0, 2)) {
                    await window.dmRequest('/api/v1/combatants/' + id + '/defeated', {
                      method: 'PUT',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ defeated: true }),
                    });
                  }
                  const el = document.querySelector('.tracker-panel');
                  await window.Alpine.$data(el).reloadCombatants();
                }
                """, seeded.memberIds().stream().map(java.util.UUID::toString).toList());

        page.waitForFunction("() => document.querySelectorAll('.combatant-row.defeated').length >= 2");
        assertThat(page.locator(".combatant-row:has(.group-count) .combatant-name")
                .first().innerText().trim())
                .as("a group keeps its identity when members die")
                .isEqualTo(before);
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest=TrackerIdentityBrowserTest`
Expected: FAIL, 4 tests. The first reports an accessible name of `"Goblin 1+3 more"` (or an empty box at rail width); the toggle's bounding box extends past the module; the group label changes to `Goblin 3` after defeats.

- [ ] **Step 3: Add the label helpers**

In `src/main/resources/static/js/combat-tracker.js`, add next to `groupMoreCount` (~line 746):

```javascript
            // A group is one creature type, so its row is labelled by the type, not by whoever
            // happens to be acting. Members are named "<base> <n>"; the base is the identity.
            groupLabel(groupId) {
                const members = this.combatants
                    .filter(c => c.groupId === groupId)
                    .sort((a, b) => a.sortOrder - b.sortOrder);
                if (members.length === 0) return '';
                return this._stripOrdinal(members[0].name).base || members[0].name;
            },

            _stripOrdinal(name) {
                const match = /^(.*?)(\s+\d+)$/.exec(name || '');
                return match
                    ? { base: match[1], ordinal: match[2] }
                    : { base: name || '', ordinal: '' };
            },

            // The distinguishing part of a name must survive truncation, so it is rendered as
            // its own non-shrinking element rather than as the tail of an ellipsised string.
            nameBase(c) {
                if (this.isGroupRow(c)) return this.groupLabel(c.groupId);
                return this._stripOrdinal(c.name).base;
            },

            nameOrdinal(c) {
                if (this.isGroupRow(c)) return '';
                return this._stripOrdinal(c.name).ordinal;
            },
```

- [ ] **Step 4: Restructure the row**

In `src/main/resources/templates/encounter/_tracker.html`, replace lines 336-391 (the `<template x-for="(c, idx) in combatants">` block) with:

```html
                <template x-for="(c, idx) in combatants" :key="c.id">
                    <div class="combatant-row"
                          :data-cid="c.id"
                          :class="{ active: c.id === activeCombatantId, selected: c.id === selectedCombatantId, defeated: c.defeated, hidden: c.hidden, bloodied: c.bloodied }"
                          x-show="!c.hidden && showsInOrder(c)"
                         @click="selectCombatant(c.id)">
                        <!-- Initiative badge -->
                        <span class="init-badge" x-text="c.initiative ?? '—'"></span>

                        <!-- Name. The base truncates; the ordinal never does, so siblings in a
                             group stay distinguishable at any rail width. -->
                        <span class="combatant-name">
                            <template x-if="c.groupId && !isGroupRow(c)">
                                <span class="group-branch" aria-hidden="true">├</span>
                            </template>
                            <span class="combatant-name__base" x-text="nameBase(c)"></span>
                            <span class="combatant-name__ordinal"
                                  x-show="nameOrdinal(c)" x-text="nameOrdinal(c)"></span>
                        </span>

                        <!-- Numeric HP — DM only, and the number the log will agree with -->
                        <span class="combatant-hp u-num" x-text="hpLabel(c)"></span>

                        <!-- Group disclosure. Its own grid cell: it must never compete with the
                             name cell's truncation, which clipped it out of the row entirely. -->
                        <button type="button" class="group-count btn btn-ghost btn-xs"
                                x-show="isGroupRow(c)"
                                :aria-expanded="isGroupExpanded(c.groupId) ? 'true' : 'false'"
                                :aria-label="(isGroupExpanded(c.groupId) ? 'Collapse ' : 'Expand ')
                                             + groupLabel(c.groupId) + ' group, '
                                             + groupMoreCount(c.groupId) + ' more'"
                                @click.stop="toggleGroup(c.groupId)"
                                x-text="(isGroupExpanded(c.groupId) ? '−' : '+') + groupMoreCount(c.groupId)"></button>

                        <div class="combatant-row__meta">
                            <!-- HP bar mini -- DM only -->
                            <div class="hp-mini">
                                <div class="hp-mini-fill"
                                     :style="{ width: hpPercent(c) + '%' }"
                                     :class="{ bloodied: c.bloodied, dead: c.defeated }"></div>
                            </div>

                            <!-- HP delta quick-entry -- DM only -->
                            <input type="text" class="hp-delta-input"
                                   placeholder="±HP"
                                   :aria-label="'Hit point change for ' + c.name"
                                   @click.stop
                                   @keydown.enter="applyHpDelta(c.id)"
                                   x-model="hpDelta">

                            <!-- Condition icons (always visible) -->
                            <div class="condition-icons">
                                <template x-for="cond in c.conditions" :key="cond.sourceKey">
                                    <span class="cond-icon"
                                          :style="{ background: conditionColor(cond.sourceKey) }"
                                          :title="getConditionText(cond.sourceKey)"
                                          x-text="cond.durationRounds > 0
                                            ? cond.durationRounds : '∞'"></span>
                                </template>
                                <button type="button"
                                        class="btn btn-ghost tracker-small-button condition-add"
                                        :aria-label="'Add a condition to ' + c.name"
                                        @click.stop="openConditionMenu(c.id)">＋</button>
                            </div>

                            <!-- Concentration badge -->
                            <span x-show="c.concentratingOn" class="conc-badge"
                                  :title="c.concentratingOn">⏀</span>
                        </div>
                    </div>
                </template>
```

Task 4 changes only identity and layout. The existing HP and condition bindings stay intact so
this commit is independently runnable; Tasks 5, 7, and 10 change those behaviors under their
own failing tests.

- [ ] **Step 5: Make the row a grid**

In `src/main/resources/static/css/components.css`, replace the `.combatant-row` rule (lines 776-784) and the `.combatant-name` / `.group-count` rules (lines 831-837, 853-857):

```css
/* Two lines, so identity never loses to the instrument cluster. Line one is who and how
   much; line two is the bar, the delta box and the conditions. At a 360px rail the name
   cell keeps ~210px instead of the ~53px the single-line flex layout left it. */
.combatant-row {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto auto;
    grid-template-areas:
        "init name hp toggle"
        "init meta meta meta";
    column-gap: var(--space-xs);
    row-gap: 2px;
    align-items: center;
    padding: var(--space-xs) var(--space-md);
    border-left: 3px solid transparent;
    cursor: pointer;
    transition: background var(--duration-standard) var(--ease-out), border-left-color var(--duration-standard) var(--ease-out), transform var(--duration-standard) var(--ease-out), box-shadow var(--duration-standard) var(--ease-out);
}

.combatant-row > .init-badge { grid-area: init; }
.combatant-row > .combatant-name { grid-area: name; }
.combatant-row > .combatant-hp { grid-area: hp; }
.combatant-row > .group-count { grid-area: toggle; }

.combatant-row__meta {
    grid-area: meta;
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    min-width: 0;
}
```

```css
.combatant-name {
    display: flex;
    align-items: baseline;
    gap: 2px;
    min-width: 0;
}

/* The base shortens; the ordinal is what tells Goblin 3 from Goblin 4, so it never does. */
.combatant-name__base {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.combatant-name__ordinal {
    flex: 0 0 auto;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
}

.group-branch { flex: 0 0 auto; color: var(--color-text-muted); }
```

```css
.group-count {
    font-size: var(--text-xs);
    font-variant-numeric: tabular-nums;
    padding: 0 6px;
    min-width: 2.25rem;
    flex-shrink: 0;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    color: var(--color-text);
}

.group-count:hover { border-color: var(--color-border-strong); }
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest=TrackerIdentityBrowserTest`
Expected: PASS, 4 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/js/combat-tracker.js \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java
git commit -m "fix: give group rows a stable label and a reachable toggle"
```

---

### Task 5: The per-row condition control targets its own row

`openConditionMenu(c.id)` assigns `this.showConditionMenu = id`. No template in the repository reads `showConditionMenu` — it is write-only state. The button also carries `@click.stop`, which suppresses the row's `selectCombatant(c.id)`. So the visible Conditions palette stays bound to whatever `selectedCombatantId` was, and clicking `＋` on Grumbar's row and then "Grappled" applied Grappled to Sildar. No control in the tracker may mutate a combatant other than the one it is visually attached to.

Resolution: the per-row control **retargets the shared editor** to its own row. The static contract test in Step 5 makes write-only targeting state a build failure, so this class of bug cannot come back.

**Files:**
- Modify: `src/main/resources/static/js/combat-tracker.js:40-41` (remove `showConditionMenu`, `conditionDuration`), `:655-665` (`selectCombatant`), `:690-693` (replace `openConditionMenu`)
- Modify: `src/main/resources/templates/encounter/_tracker.html` (row condition action calls the new method)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerContractTest.java`

**Interfaces:**
- Consumes: the `.condition-add` button wired in Task 4.
- Produces: `combatTracker#editConditionsFor(id)`. `showConditionMenu` and `conditionDuration` cease to exist.

- [ ] **Step 1: Write the failing browser test**

Add to `TrackerIdentityBrowserTest`:

```java
    @Test
    void theRowConditionControlAppliesToItsOwnRow() {
        openCombat();
        var rows = page.locator(".combatant-row");
        String targetId = rows.nth(1).getAttribute("data-cid");
        String otherId = rows.nth(0).getAttribute("data-cid");

        rows.nth(0).click();                       // bind the editor somewhere else first
        rows.nth(1).locator(".condition-add").click();
        page.locator(".detail-conditions .condition-quick button", new Page.LocatorOptions())
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("Prone"))
                .first().click();

        page.waitForFunction(
                "(id) => document.querySelector(`[data-cid='${id}'] .cond-icon`) !== null",
                targetId);
        assertThat(page.locator("[data-cid='" + targetId + "'] .cond-icon").count()).isEqualTo(1);
        assertThat(page.locator("[data-cid='" + otherId + "'] .cond-icon").count())
                .as("no other combatant may gain the condition")
                .isZero();
        assertThat(page.evaluate(
                "() => window.Alpine.$data(document.querySelector('.tracker-panel')).selectedCombatantId"))
                .as("the shared editor must have been retargeted to the row that was clicked")
                .isEqualTo(targetId);
    }
```

- [ ] **Step 2: Write the failing contract test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dead targeting state is how B2 happened: a control wrote `showConditionMenu` that no
 * template ever read, so the button provably could not affect the row it sat in. Any
 * component property that is written and never read is a bug of that shape.
 */
class TrackerContractTest {

    private static final Path JS = Path.of("src/main/resources/static/js/combat-tracker.js");
    private static final Path TEMPLATE = Path.of("src/main/resources/templates/encounter/_tracker.html");

    /** Keys read by other scripts or by the shell rather than by this component's template. */
    private static final Set<String> EXTERNALLY_READ = Set.of("campaignId", "trackerMode");

    @Test
    void everyStateKeyIsReadSomewhere() throws IOException {
        String js = Files.readString(JS);
        String template = Files.readString(TEMPLATE);

        List<String> dead = new ArrayList<>();
        for (String key : stateKeys(js)) {
            if (key.startsWith("_") || EXTERNALLY_READ.contains(key)) continue;
            if (template.contains(key)) continue;
            if (isReadInJs(js, key)) continue;
            dead.add(key);
        }
        assertThat(dead)
                .as("component state that is written but never read cannot affect what a DM sees")
                .isEmpty();
    }

    @Test
    void theRemovedConditionMenuStateDoesNotComeBack() throws IOException {
        assertThat(Files.readString(JS)).doesNotContain("showConditionMenu", "openConditionMenu");
    }

    /** The keys of the object literal `combatTracker` returns, up to the first method. */
    private static List<String> stateKeys(String js) {
        int start = js.indexOf("function combatTracker(");
        assertThat(start).as("combatTracker factory must exist").isGreaterThan(-1);
        String body = js.substring(start);
        Matcher matcher = Pattern.compile("^\\s{12}([a-zA-Z_][\\w$]*):\\s", Pattern.MULTILINE)
                .matcher(body);
        List<String> keys = new ArrayList<>();
        while (matcher.find()) keys.add(matcher.group(1));
        assertThat(keys).as("state keys must be discoverable").isNotEmpty();
        return keys;
    }

    private static boolean isReadInJs(String js, String key) {
        Matcher matcher = Pattern.compile("this\\." + Pattern.quote(key) + "\\b\\s*(?!=[^=])")
                .matcher(js);
        while (matcher.find()) {
            String rest = js.substring(matcher.end());
            if (!rest.startsWith("=") || rest.startsWith("==")) return true;
        }
        return false;
    }
}
```

- [ ] **Step 3: Run both tests and watch them fail**

Run: `./mvnw -o test -Dtest='TrackerContractTest,TrackerIdentityBrowserTest#theRowConditionControlAppliesToItsOwnRow'`
Expected: FAIL. `everyStateKeyIsReadSomewhere` reports `["showConditionMenu", "conditionDuration"]`; the browser test finds the condition on the wrong row.

- [ ] **Step 4: Retarget the shared editor**

In `src/main/resources/static/js/combat-tracker.js`:

Delete lines 40-41 (`showConditionMenu: null,` and `conditionDuration: '',`).

Replace `selectCombatant` (lines 655-665):

```javascript
            selectCombatant(id) {
                this.selectedCombatantId = id;
                if (this.selected) {
                    this.editHp = this.selected.currentHp;
                    this.editTempHp = this.selected.tempHp || 0;
                }
                if (this.trackerMode === 'FOCUSED' && this.selected?.statBlockId) {
                    this.loadFocusedStatblock(this.selected.statBlockId);
                }
            },
```

Replace `openConditionMenu` (lines 690-693) with:

```javascript
            // The row's "+" and the detail panel's palette are the same editor. Selecting the
            // row is the whole binding: a control that appears attached to a row and edits a
            // different creature is the defect this replaces.
            editConditionsFor(id) {
                this.selectCombatant(id);
                this.$nextTick(() => {
                    const panel = this.$root.querySelector('.detail-conditions');
                    if (!panel) return;
                    panel.scrollIntoView({ block: 'nearest' });
                    panel.querySelector('.condition-quick button')?.focus();
                });
            },
```

In `_tracker.html`, change the row action from
`@click.stop="openConditionMenu(c.id)"` to:

```html
@click.stop="editConditionsFor(c.id)"
```

- [ ] **Step 5: Run both tests and watch them pass**

Run: `./mvnw -o test -Dtest='TrackerContractTest,TrackerIdentityBrowserTest'`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/js/combat-tracker.js \
        src/main/resources/templates/encounter/_tracker.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerContractTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java
git commit -m "fix: make the per-row condition control edit its own row"
```

---

### Task 6: The detail panel names its combatant, and selection is visible

The detail panel's first element is the HP field: nothing says whose HP it is. The tracker's only row emphasis is the active turn, which is a different concept from selection. With the turn on Nienna and the panel bound to Sildar, the highlight and the editor pointed at different creatures.

Selection also needs a defined lifecycle rather than an emergent one. The rules this task implements:

| Event | Selection |
|---|---|
| Turn advances | Unchanged — the DM chose it |
| Combatant becomes defeated | Unchanged — a downed PC is exactly who you keep editing |
| Combatant removed | Cleared |
| Combatant no longer in the roster after a reload | Cleared |
| Encounter ends | Cleared |

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html:406-419` (detail panel heading)
- Modify: `src/main/resources/static/js/combat-tracker.js:233-252` (`reloadCombatants` prunes a vanished selection)
- Modify: `src/main/resources/static/css/components.css` (`.combatant-row.selected`, `.combatant-detail__title`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java`

**Interfaces:**
- Consumes: the `selected` class binding added to the row in Task 4.
- Produces: `[data-selected-combatant-name]` on the detail panel heading; `.combatant-row.selected` as a treatment distinct from `.combatant-row.active`.

- [ ] **Step 1: Write the failing test**

Add to `TrackerIdentityBrowserTest`:

```java
    @Test
    void theDetailPanelNamesTheCombatantItEdits() {
        openCombat();
        var rows = page.locator(".combatant-row");
        String firstName = rows.nth(0).locator(".combatant-name").innerText().trim();
        String secondName = rows.nth(1).locator(".combatant-name").innerText().trim();

        rows.nth(0).click();
        page.waitForSelector("[data-selected-combatant-name]");
        assertThat(page.locator("[data-selected-combatant-name]").innerText().trim())
                .isEqualTo(firstName);

        rows.nth(1).click();
        page.waitForFunction("(name) => document.querySelector('[data-selected-combatant-name]')"
                + "?.textContent.trim() === name", secondName);
    }

    @Test
    void selectionAndActiveTurnAreDifferentTreatments() {
        openCombat();
        String activeId = (String) page.evaluate(
                "() => window.Alpine.$data(document.querySelector('.tracker-panel')).activeCombatantId");
        var otherRow = page.locator(".combatant-row:not([data-cid='" + activeId + "'])").first();
        otherRow.click();
        page.waitForSelector(".combatant-row.selected");

        assertThat(page.locator(".combatant-row.active").count()).isEqualTo(1);
        assertThat(page.locator(".combatant-row.selected").count()).isEqualTo(1);
        assertThat(page.locator(".combatant-row.active.selected").count())
                .as("the fixture must have selection and active turn on different rows")
                .isZero();

        Object activeShadow = page.evaluate(
                "() => getComputedStyle(document.querySelector('.combatant-row.active')).boxShadow");
        Object selectedShadow = page.evaluate(
                "() => getComputedStyle(document.querySelector('.combatant-row.selected')).boxShadow");
        assertThat(selectedShadow)
                .as("selection must be readable as something other than the active turn")
                .isNotEqualTo(activeShadow);
        assertThat(String.valueOf(selectedShadow)).isNotEqualTo("none");
    }

    @Test
    void selectionSurvivesTheTurnAdvancingAndClearsWhenTheCombatantLeaves() {
        var seeded = openCombat();
        var rows = page.locator(".combatant-row");
        String selectedId = rows.nth(0).getAttribute("data-cid");
        rows.nth(0).click();
        page.waitForSelector(".combatant-row.selected");

        page.evaluate("() => window.Alpine.$data(document.querySelector('.tracker-panel')).nextTurn()");
        page.waitForFunction("(id) => window.Alpine.$data(document.querySelector('.tracker-panel'))"
                + ".selectedCombatantId === id", selectedId);

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.tracker-panel'))"
                + ".removeCombatant(id)", selectedId);
        page.waitForFunction("() => window.Alpine.$data(document.querySelector('.tracker-panel'))"
                + ".selectedCombatantId === null");
        assertThat(page.locator("[data-selected-combatant-name]").count()).isZero();
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest=TrackerIdentityBrowserTest`
Expected: FAIL on the three new tests — `[data-selected-combatant-name]` does not exist and `.combatant-row.selected` has no distinct treatment.

- [ ] **Step 3: Give the detail panel a heading**

In `src/main/resources/templates/encounter/_tracker.html`, replace lines 406-408 (the opening of `.combatant-detail` and the start of the HP section):

```html
            <!-- Selected combatant detail — DM only -->
            <div class="combatant-detail" x-show="selectedCombatantId && selected" x-effect="tickNumber($refs.hpDisplay, selected?.currentHp || 0, 300)">
                <div class="combatant-detail__header">
                    <h4 class="combatant-detail__title" data-selected-combatant-name
                        x-text="selected?.name || ''"></h4>
                    <span class="badge combatant-detail__turn-hint"
                          x-show="selected && selected.id === activeCombatantId">Active turn</span>
                </div>
                <!-- HP section -->
                <div class="detail-hp">
```

- [ ] **Step 4: Prune a selection that left the roster**

In `src/main/resources/static/js/combat-tracker.js`, inside `reloadCombatants`, immediately after `this.combatants = await resp.json();` (line 241):

```javascript
                    // Selection lifecycle: it survives turn changes and defeat, and is dropped
                    // only when the combatant it names is no longer in the encounter.
                    if (this.selectedCombatantId
                        && !this.combatants.some(c => c.id === this.selectedCombatantId)) {
                        this.selectedCombatantId = null;
                    }
```

- [ ] **Step 5: Style selection distinctly from the active turn**

In `src/main/resources/static/css/components.css`, after the `.combatant-row.active` rule (line 787):

```css
/* Active turn and selection are different questions: "whose go is it" and "who am I
   editing". They must never be told apart by intensity alone. */
.combatant-row.selected {
    background: var(--color-combatant-hover);
    box-shadow: inset 2px 0 0 var(--color-text-muted);
}
.combatant-row.active.selected {
    box-shadow: inset 0 0 0 1px var(--color-gold-soft), inset 2px 0 0 var(--color-text-muted),
                var(--shadow-warm-sm);
}
```

After the `.combatant-detail` rule (line 971):

```css
.combatant-detail__header {
    display: flex;
    align-items: baseline;
    gap: var(--space-xs);
    margin-bottom: var(--space-xs);
}

.combatant-detail__title {
    margin: 0;
    font-size: var(--text-base);
    font-weight: 700;
    color: var(--color-text);
}
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest=TrackerIdentityBrowserTest`
Expected: PASS, 10 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/js/combat-tracker.js \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java
git commit -m "fix: name and mark the combatant the tracker detail panel edits"
```

---

### Task 7: The HP delta input holds per-row state

Every row's `±HP` box carries `x-model="hpDelta"` against one component-level string. Typing `-6` into one row makes the screen state that eight creatures are about to take 6 damage. The behaviour is right — Enter applies it only to the focused row — but a per-row entry field must display per-row state.

**Files:**
- Modify: `src/main/resources/static/js/combat-tracker.js:37` (`hpDelta` → `hpDeltas`), `:497-502` (`applyHpDelta`)
- Modify: `src/main/resources/templates/encounter/_tracker.html` (key the row binding by combatant id)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerContractTest.java`

**Interfaces:**
- Consumes: the row layout added in Task 4.
- Produces: `combatTracker.hpDeltas` — an object keyed by combatant id. `hpDelta` ceases to exist.

- [ ] **Step 1: Write the failing tests**

Add to `TrackerIdentityBrowserTest`:

```java
    @Test
    void typingADeltaInOneRowLeavesEveryOtherRowEmpty() {
        openCombat();
        var inputs = page.locator(".combatant-row .hp-delta-input");
        int count = inputs.count();
        assertThat(count).isGreaterThan(1);

        inputs.nth(1).fill("-6");
        assertThat(inputs.nth(1).inputValue()).isEqualTo("-6");
        for (int i = 0; i < count; i++) {
            if (i == 1) continue;
            assertThat(inputs.nth(i).inputValue())
                    .as("row %d must not show another row's in-progress entry", i)
                    .isEmpty();
        }
    }

    @Test
    void applyingADeltaChangesOnlyTheFocusedRow() {
        openCombat();
        var rows = page.locator(".combatant-row");
        String targetId = rows.nth(1).getAttribute("data-cid");
        String hpBefore = rows.nth(0).locator(".combatant-hp").innerText();

        rows.nth(1).locator(".hp-delta-input").fill("-3");
        rows.nth(1).locator(".hp-delta-input").press("Enter");
        page.waitForFunction("(id) => document.querySelector(`[data-cid='${id}'] .hp-delta-input`)"
                + ".value === ''", targetId);

        assertThat(rows.nth(0).locator(".combatant-hp").innerText()).isEqualTo(hpBefore);
    }
```

Add to `TrackerContractTest`:

```java
    @Test
    void noPerRowFieldBindsASharedModel() throws IOException {
        String template = Files.readString(TEMPLATE);
        int rowStart = template.indexOf("<template x-for=\"(c, idx) in combatants\"");
        assertThat(rowStart).as("the initiative row loop must exist").isGreaterThan(-1);
        String row = template.substring(rowStart, template.indexOf("</template>", rowStart));

        Matcher models = Pattern.compile("x-model=\"([^\"]+)\"").matcher(row);
        List<String> shared = new ArrayList<>();
        while (models.find()) {
            String expression = models.group(1);
            if (!expression.contains("c.id")) shared.add(expression);
        }
        assertThat(shared)
                .as("a per-row entry field must bind per-row state, keyed by the row's combatant")
                .isEmpty();
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest='TrackerContractTest,TrackerIdentityBrowserTest'`
Expected: FAIL — the contract reports shared `x-model="hpDelta"` and typing in one row mirrors
the value into the others.

- [ ] **Step 3: Make the model per-row**

In `src/main/resources/static/js/combat-tracker.js`, replace line 37:

```javascript
            // Keyed by combatant id: one row's in-progress entry must never appear in another.
            hpDeltas: {},
```

In `_tracker.html`, replace the row input binding:

```html
                                   x-model="hpDeltas[c.id]">
```

Replace `applyHpDelta` (lines 497-502):

```javascript
            async applyHpDelta(combatantId) {
                const amount = parseInt(this.hpDeltas[combatantId], 10);
                if (Number.isNaN(amount) || amount === 0) return;
                const succeeded = await this.quickHp(combatantId, amount);
                if (succeeded) this.hpDeltas[combatantId] = '';
            },
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='TrackerContractTest,TrackerIdentityBrowserTest'`
Expected: PASS, 13 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/combat-tracker.js \
        src/main/resources/templates/encounter/_tracker.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerContractTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java
git commit -m "fix: hold the tracker HP delta per row instead of once per component"
```

---

### Task 8: Names stay readable at the default rail width, and splitters explain themselves

Task 4's grid already gives the name cell ~210px at a 360px rail instead of ~53px. This task pins that with a measurement test and fixes the contributing factor: `cockpit-layout.js` gates both `onSplitterKey` and `onSplitterPointerDown` on `layoutMode === 'edit'`, and nothing on the splitter says it is inert. A DM who wants a wider tracker mid-fight has to discover "Edit layout" first.

Splitters stay edit-mode-only — mid-fight drag-resize would fight the preset system — but they now say so.

**Files:**
- Modify: `src/main/resources/static/js/cockpit-layout.js:1366-1372` (`onSplitterPointerDown`), `:1309-1311` (`onSplitterKey`), `:780-782` (`syncEditChrome` splitter tab order)
- Modify: `src/main/resources/static/css/cockpit-layout.css` (inert splitter cursor)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java`

**Interfaces:**
- Consumes: the grid row from Task 4, `cockpitLayout#showNotice` (existing).
- Produces: `[data-cockpit-splitter]` gains `aria-disabled` and a `title` outside edit mode.

- [ ] **Step 1: Write the failing tests**

Add to `TrackerIdentityBrowserTest`:

```java
    @Test
    void namesAreNotTruncatedAtTheDefaultRailWidth() {
        openCombat();
        page.waitForSelector(".combatant-row .combatant-name__base");

        Object railWidth = page.evaluate(
                "() => document.querySelector('[data-cockpit-zone=\"RIGHT_SUPPORT\"]')"
                        + ".getBoundingClientRect().width");
        assertThat(((Number) railWidth).doubleValue())
                .as("this test is only meaningful at the cramped default rail")
                .isLessThanOrEqualTo(420.0);

        Object truncated = page.evaluate("""
                () => Array.from(document.querySelectorAll('.combatant-row .combatant-name__base'))
                    .filter(el => el.scrollWidth > el.clientWidth + 1)
                    .map(el => el.textContent)
                """);
        assertThat((List<?>) truncated)
                .as("Phandelver-length names must survive the default rail width")
                .isEmpty();
    }

    @Test
    void anInertSplitterExplainsItself() {
        openCombat();
        var splitter = page.locator("[data-cockpit-splitter='PRIMARY_RIGHT']");

        assertThat(splitter.getAttribute("aria-disabled")).isEqualTo("true");
        assertThat(splitter.getAttribute("title")).contains("Edit layout");

        splitter.click();
        page.waitForFunction(
                "() => document.getElementById('cockpitLayoutNotice')?.textContent.trim().length > 0");
        assertThat(page.locator("#cockpitLayoutNotice").innerText())
                .containsIgnoringCase("edit layout");
    }
```

`showNotice` writes into `#cockpitLayoutNotice` (`cockpit-layout.js:28, 2102`). Assert on that element; do not weaken this to "some element appeared".

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest='TrackerIdentityBrowserTest#namesAreNotTruncatedAtTheDefaultRailWidth+anInertSplitterExplainsItself'`
Expected: FAIL — the splitter carries no `aria-disabled` and clicking it does nothing.

- [ ] **Step 3: Make the splitter say why it is inert**

In `src/main/resources/static/js/cockpit-layout.js`, replace the guard in `onSplitterPointerDown` (line 1367):

```javascript
    onSplitterPointerDown(splitter, event) {
      if (this.workbench.dataset.layoutMode !== 'edit') {
        // Silence reads as breakage. Panel sizes are part of the layout document, so
        // resizing lives in edit mode — but the handle has to say so when it is touched.
        this.showNotice('Panel sizes are part of the layout. Choose "Edit layout" to resize.');
        return;
      }
```

Replace the guard in `onSplitterKey` (line 1310):

```javascript
    onSplitterKey(splitter, event) {
      if (this.workbench.dataset.layoutMode !== 'edit') {
        const keys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'];
        if (keys.includes(event.key)) {
          event.preventDefault();
          this.showNotice('Panel sizes are part of the layout. Choose "Edit layout" to resize.');
        }
        return;
      }
```

In `syncEditChrome`, replace the splitter loop (lines 780-782):

```javascript
      document.querySelectorAll('[data-cockpit-splitter]').forEach((splitter) => {
        splitter.tabIndex = 0;
        splitter.setAttribute('aria-disabled', editing ? 'false' : 'true');
        splitter.title = editing
          ? 'Drag or use arrow keys to resize'
          : 'Choose "Edit layout" to resize panels';
      });
```

Splitters keep `tabIndex = 0` in both modes so a keyboard user can reach the handle and hear the explanation.

- [ ] **Step 4: Match the cursor to the affordance**

In `src/main/resources/static/css/cockpit-layout.css`, add next to the existing splitter rules:

```css
[data-cockpit-splitter][aria-disabled="true"] {
  cursor: default;
}
[data-cockpit-splitter][aria-disabled="false"] {
  cursor: col-resize;
}
[data-cockpit-splitter][aria-orientation="horizontal"][aria-disabled="false"] {
  cursor: row-resize;
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest=TrackerIdentityBrowserTest`
Expected: PASS, 15 tests.

- [ ] **Step 6: Run the phase suite**

Run: `./mvnw -o test -Dtest='Tracker*Test,Cockpit*Test,Initiative*Test,SessionCockpit*Test'`
Expected: PASS, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/cockpit-layout.js \
        src/main/resources/static/css/cockpit-layout.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java
git commit -m "fix: keep combatant names legible at rail width and explain inert splitters"
```

---

## Phase C — Rules fidelity of conditions

### Task 9: Conditions default to indefinite

All fourteen entries in `COMMON_CONDITIONS` carry `defaultDuration: 1`, `loadConditionsCatalog` overwrites every server-supplied condition with `defaultDuration: 1`, and `toggleCondition` coerces with `duration || 1` — so even an explicit `0` becomes one round. Sildar marked Prone in round 1 was unmarked in round 2.

In SRD 5.2 **no condition carries an inherent duration**. Blinded, Charmed, Deafened, Frightened, Grappled, Incapacitated, Invisible, Paralyzed, Petrified, Poisoned, Prone, Restrained, Stunned and Unconscious all persist until something ends them — a successful save, the end of a spell, an action, or the DM. Exhaustion likewise. The duration belongs to the *effect that imposed the condition*, never to the condition itself. The catalogue therefore defaults every entry to `0`, which the tracker already renders as `∞` and which the server already treats as never-expiring (`EncounterService` line 1291: `if (cond.durationRounds() <= 0) return true;`).

The decrement and expiry mechanism is sound and is not touched. Only the defaults are wrong.

**Files:**
- Modify: `src/main/resources/static/js/combat-tracker.js:12-27` (`COMMON_CONDITIONS`), `:183-195` (`loadConditionsCatalog`), `:537-551` (`toggleCondition`)
- Modify: `src/main/resources/templates/encounter/_tracker.html:440-460` (the quick palette and catalogue list)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/ConditionDefaultsTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java`

**Interfaces:**
- Consumes: `editConditionsFor` (Task 5), the fixture from Task 1.
- Produces: `combatTracker.CONDITION_DEFAULT_DURATIONS` — an exported-by-source-text map of `sourceKey -> rounds`, all `0`, asserted by `ConditionDefaultsTest`.

- [ ] **Step 1: Write the failing unit test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/ConditionDefaultsTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewed against SRD 5.2: no condition carries an inherent duration. A duration belongs to
 * the effect that imposed it — a spell, a save, an action — never to the condition. A DM who
 * marks a downed PC Unconscious and finds them unmarked next round has been told something
 * untrue by the tool.
 */
class ConditionDefaultsTest {

    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("blinded", 0),
            Map.entry("charmed", 0),
            Map.entry("deafened", 0),
            Map.entry("exhaustion", 0),
            Map.entry("frightened", 0),
            Map.entry("grappled", 0),
            Map.entry("incapacitated", 0),
            Map.entry("invisible", 0),
            Map.entry("paralyzed", 0),
            Map.entry("petrified", 0),
            Map.entry("poisoned", 0),
            Map.entry("prone", 0),
            Map.entry("restrained", 0),
            Map.entry("stunned", 0),
            Map.entry("unconscious", 0)));

    @Test
    void everyCatalogueConditionDefaultsToIndefinite() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/combat-tracker.js"));
        Matcher matcher = Pattern.compile(
                "^\\s*([a-z]+):\\s*(\\d+),\\s*$", Pattern.MULTILINE)
                .matcher(defaultsBlock(js));

        Map<String, Integer> actual = new LinkedHashMap<>();
        while (matcher.find()) actual.put(matcher.group(1), Integer.parseInt(matcher.group(2)));

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(EXPECTED);
    }

    @Test
    void theCatalogueLoaderDoesNotOverrideTheDefaults() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/combat-tracker.js"));
        assertThat(js)
                .as("a server-supplied condition must not be coerced back to one round")
                .doesNotContain("defaultDuration: 1")
                .doesNotContain("durationRounds: duration || 1");
    }

    private static String defaultsBlock(String js) {
        int start = js.indexOf("const CONDITION_DEFAULT_DURATIONS");
        assertThat(start).as("CONDITION_DEFAULT_DURATIONS must exist").isGreaterThan(-1);
        return js.substring(start, js.indexOf("};", start));
    }
}
```

- [ ] **Step 2: Write the failing browser test**

Add to `TrackerIdentityBrowserTest`:

```java
    @Test
    void proneSurvivesTwoFullRounds() {
        openCombat();
        var rows = page.locator(".combatant-row");
        String targetId = rows.nth(0).getAttribute("data-cid");
        rows.nth(0).click();
        page.locator(".detail-conditions .condition-quick button")
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("Prone"))
                .first().click();
        page.waitForSelector("[data-cid='" + targetId + "'] .cond-icon");

        int combatants = rows.count();
        page.evaluate("""
                async (turns) => {
                  const tracker = window.Alpine.$data(document.querySelector('.tracker-panel'));
                  for (let i = 0; i < turns; i++) await tracker.nextTurn();
                }
                """, combatants * 2);

        assertThat(page.locator("[data-cid='" + targetId + "'] .cond-icon").count())
                .as("Prone persists until something removes it")
                .isEqualTo(1);
        assertThat(page.locator("[data-cid='" + targetId + "'] .cond-icon__duration").innerText())
                .isEqualTo("∞");
    }

    @Test
    void anExplicitOneRoundDurationStillExpires() {
        openCombat();
        var rows = page.locator(".combatant-row");
        String targetId = rows.nth(0).getAttribute("data-cid");
        rows.nth(0).click();
        page.fill(".detail-conditions [data-condition-duration]", "1");
        page.locator(".detail-conditions .condition-quick button")
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("Prone"))
                .first().click();
        page.waitForSelector("[data-cid='" + targetId + "'] .cond-icon");
        assertThat(page.locator("[data-cid='" + targetId + "'] .cond-icon__duration").innerText())
                .isEqualTo("1");

        int combatants = rows.count();
        page.evaluate("""
                async (turns) => {
                  const tracker = window.Alpine.$data(document.querySelector('.tracker-panel'));
                  for (let i = 0; i < turns; i++) await tracker.nextTurn();
                }
                """, combatants * 2);

        page.waitForFunction("(id) => document.querySelectorAll(`[data-cid='${id}'] .cond-icon`)"
                + ".length === 0", targetId);
    }
```

- [ ] **Step 3: Run both tests and watch them fail**

Run: `./mvnw -o test -Dtest='ConditionDefaultsTest,TrackerIdentityBrowserTest#proneSurvivesTwoFullRounds+anExplicitOneRoundDurationStillExpires'`
Expected: FAIL. `CONDITION_DEFAULT_DURATIONS` does not exist; Prone vanishes after one round; there is no duration input.

- [ ] **Step 4: Replace the durations with the reviewed table**

In `src/main/resources/static/js/combat-tracker.js`, insert before `COMMON_CONDITIONS` (line 11):

```javascript
    /* SRD 5.2: a condition has no inherent duration. It ends when the effect that imposed it
       ends — a save, a spell, an action, or the DM. Anything with a positive number here would
       be the tool inventing a rule. Reviewed 2026-07-30. */
    const CONDITION_DEFAULT_DURATIONS = {
        blinded: 0,
        charmed: 0,
        deafened: 0,
        exhaustion: 0,
        frightened: 0,
        grappled: 0,
        incapacitated: 0,
        invisible: 0,
        paralyzed: 0,
        petrified: 0,
        poisoned: 0,
        prone: 0,
        restrained: 0,
        stunned: 0,
        unconscious: 0,
    };

    function defaultDurationFor(sourceKey) {
        return CONDITION_DEFAULT_DURATIONS[sourceKey] ?? 0;
    }
```

In `COMMON_CONDITIONS` (lines 12-27), replace every `defaultDuration: 1` with `defaultDuration: 0`.

In `loadConditionsCatalog` (line 191), replace `defaultDuration: 1,` with:

```javascript
                            defaultDuration: defaultDurationFor(c.sourceKey),
```

In `toggleCondition` (line 545), replace the body line:

```javascript
                        body: JSON.stringify({
                            sourceKey,
                            // 0 means indefinite and the server honours it; `|| 1` turned every
                            // explicit "no duration" into a one-round condition.
                            durationRounds: Number.isFinite(Number(duration)) ? Number(duration) : 0,
                        }),
```

- [ ] **Step 5: Let the DM state a duration and end a condition**

In `src/main/resources/templates/encounter/_tracker.html`, replace the `.detail-conditions` block (lines 440-461):

```html
                <!-- Conditions panel -->
                <div class="detail-conditions"
                     x-data="{ condFilter: '', applyDuration: '',
                               commonKeys: ['prone','grappled','restrained','frightened','poisoned','unconscious'] }">
                    <h4>Conditions</h4>
                    <label class="detail-conditions__duration u-text-xs">
                        Rounds (blank = until removed)
                        <input type="number" min="1" max="99" class="form-input"
                               data-condition-duration
                               placeholder="∞"
                               x-model="applyDuration">
                    </label>
                    <div class="condition-quick">
                        <template x-for="key in commonKeys" :key="key">
                            <button type="button" class="btn btn-xs"
                                    :class="hasCondition(selected, key) ? 'is-active' : 'btn-ghost'"
                                    @click="toggleCondition(selected.id, key, applyDuration)"
                                    x-text="key.charAt(0).toUpperCase() + key.slice(1)"></button>
                        </template>
                    </div>
                    <div class="condition-active" x-show="selected?.conditions?.length">
                        <template x-for="cond in (selected?.conditions || [])" :key="cond.sourceKey">
                            <span class="condition-active__chip">
                                <span x-text="conditionName(cond.sourceKey)"></span>
                                <span class="u-text-xs"
                                      x-text="cond.durationRounds > 0 ? cond.durationRounds + 'r' : '∞'"></span>
                                <button type="button" class="btn btn-ghost btn-xs"
                                        :aria-label="'End ' + conditionName(cond.sourceKey)
                                                     + ' on ' + selected.name"
                                        @click="removeCondition(selected.id, cond.sourceKey)">×</button>
                            </span>
                        </template>
                    </div>
                    <input type="search" class="form-input" x-model="condFilter"
                           placeholder="Filter conditions..." style="margin-bottom: 4px;">
                    <template x-for="cond in conditionsCatalog.filter(c => !condFilter || c.name.toLowerCase().includes(condFilter.toLowerCase()))" :key="cond.sourceKey">
                        <label class="condition-toggle">
                            <input type="checkbox"
                                   :checked="hasCondition(selected, cond.sourceKey)"
                                   @change="toggleCondition(selected.id, cond.sourceKey,
                                                            applyDuration || cond.defaultDuration)">
                            <span x-text="cond.name"></span>
                            <span class="cond-help" :title="cond.description" style="cursor: help; color: var(--color-text-muted);">ⓘ</span>
                        </label>
                    </template>
                </div>
```

Add the name lookup to `combat-tracker.js`, next to `getConditionText`:

```javascript
            conditionName(sourceKey) {
                return this.conditionsCatalog.find(c => c.sourceKey === sourceKey)?.name
                    || sourceKey;
            },
```

- [ ] **Step 6: Style the new controls**

In `src/main/resources/static/css/components.css`, after the `.cond-icon` rules:

```css
.condition-quick { display: flex; gap: 4px; flex-wrap: wrap; margin-bottom: 6px; }

.detail-conditions__duration {
    display: block;
    color: var(--color-text-muted);
    margin-bottom: var(--space-xs);
}
.detail-conditions__duration input { width: 5rem; }

.condition-active { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: var(--space-xs); }
.condition-active__chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 1px 4px 1px 8px;
    border: 1px solid var(--color-border);
    border-radius: 999px;
    font-size: var(--text-xs);
}
```

- [ ] **Step 7: Run both tests and watch them pass**

Run: `./mvnw -o test -Dtest='ConditionDefaultsTest,TrackerIdentityBrowserTest'`
Expected: PASS, 19 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/js/combat-tracker.js \
        src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/ConditionDefaultsTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java
git commit -m "fix: default conditions to indefinite and let the DM set a duration"
```

---

### Task 10: Condition badges are readable without hover

A condition renders as a coloured dot containing the remaining round count. Prone was a blue "1", Grappled an orange "1". Identity lives in the colour and a `title` attribute. At the table nobody hovers, and colour alone must never be the sole carrier of meaning.

**Files:**
- Modify: `src/main/resources/static/js/combat-tracker.js` (add `CONDITION_ABBREVIATIONS` and `conditionAbbr`)
- Modify: `src/main/resources/templates/encounter/_tracker.html` (render abbreviation and duration as separate text)
- Modify: `src/main/resources/static/css/components.css:927-943` (`.cond-icon`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java`

**Interfaces:**
- Consumes: the `.cond-icon` loop retained by Task 4.
- Produces: `combatTracker#conditionAbbr(sourceKey) -> string` — a three-letter uppercase code, unique across the catalogue.

- [ ] **Step 1: Write the failing test**

Add to `TrackerIdentityBrowserTest`:

```java
    @Test
    void conditionBadgesCarryTextNotOnlyColour() {
        openCombat();
        var rows = page.locator(".combatant-row");
        String targetId = rows.nth(0).getAttribute("data-cid");
        rows.nth(0).click();
        page.locator(".detail-conditions .condition-quick button")
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("Grappled"))
                .first().click();
        page.waitForSelector("[data-cid='" + targetId + "'] .cond-icon");

        var badge = page.locator("[data-cid='" + targetId + "'] .cond-icon").first();
        assertThat(badge.locator(".cond-icon__abbr").innerText().trim())
                .as("a condition must be identifiable without a pointer")
                .isEqualTo("GRA");
        assertThat(badge.locator(".cond-icon__duration").innerText().trim()).isEqualTo("∞");

        Object abbreviations = page.evaluate(
                "() => { const t = window.Alpine.$data(document.querySelector('.tracker-panel'));"
                        + " return t.conditionsCatalog.map(c => t.conditionAbbr(c.sourceKey)); }");
        assertThat((List<?>) abbreviations)
                .as("abbreviations must tell conditions apart, not just shorten them")
                .doesNotHaveDuplicates();
    }
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=TrackerIdentityBrowserTest#conditionBadgesCarryTextNotOnlyColour`
Expected: FAIL — `conditionAbbr` is not a function.

- [ ] **Step 3: Add the abbreviation table**

In `src/main/resources/static/js/combat-tracker.js`, after `CONDITION_DEFAULT_DURATIONS`:

```javascript
    /* Colour alone cannot carry meaning, and nobody hovers at the table. Three letters that
       stay distinct from one another: PAR/PET/POI/PRO are the ones worth checking. */
    const CONDITION_ABBREVIATIONS = {
        blinded: 'BLI', charmed: 'CHA', deafened: 'DEA', exhaustion: 'EXH',
        frightened: 'FRI', grappled: 'GRA', incapacitated: 'INC', invisible: 'INV',
        paralyzed: 'PAR', petrified: 'PET', poisoned: 'POI', prone: 'PRO',
        restrained: 'RES', stunned: 'STU', unconscious: 'UNC', concentration: 'CON',
    };
```

Add the accessor next to `conditionColor` (~line 651):

```javascript
            conditionAbbr(sourceKey) {
                return CONDITION_ABBREVIATIONS[sourceKey]
                    || String(sourceKey || '?').slice(0, 3).toUpperCase();
            },
```

- [ ] **Step 4: Render the abbreviation and duration**

In `_tracker.html`, replace the row's condition span:

```html
                                    <span class="cond-icon"
                                          :style="{ background: conditionColor(cond.sourceKey) }"
                                          :title="getConditionText(cond.sourceKey)">
                                        <span class="cond-icon__abbr"
                                              x-text="conditionAbbr(cond.sourceKey)"></span>
                                        <span class="cond-icon__duration"
                                              x-text="cond.durationRounds > 0
                                                ? cond.durationRounds : '∞'"></span>
                                    </span>
```

- [ ] **Step 5: Turn the dot into a pill**

In `src/main/resources/static/css/components.css`, replace the `.cond-icon` rule (lines 927-939):

```css
.cond-icon {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    min-height: 20px;
    padding: 0 5px;
    border-radius: 999px;
    font-size: var(--text-xs);
    line-height: 1;
    color: var(--color-text);
    cursor: pointer;
    background: var(--color-warning);
    animation: pop-in var(--duration-micro) var(--ease-spring) forwards;
}

.cond-icon__abbr { font-weight: 700; letter-spacing: 0.04em; }
.cond-icon__duration { font-variant-numeric: tabular-nums; opacity: 0.85; }
```

- [ ] **Step 6: Run the test and watch it pass**

Run: `./mvnw -o test -Dtest=TrackerIdentityBrowserTest`
Expected: PASS, 20 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/combat-tracker.js \
        src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/TrackerIdentityBrowserTest.java
git commit -m "fix: name conditions on the badge instead of relying on colour"
```

---

## Phase D — Reference and statblock availability during a fight

### Task 11: Compendium search results render

`CommandPaletteService.SearchResultItem` is `{id, title, type, subtype, url}`. `_reference.html` binds `x-text="item.name"` and `x-text="item.source"` — neither field exists. Twenty result rows exist in the DOM, are clickable, and render as invisible strips. The contract between payload and template must be pinned by a test so a field rename cannot silently blank the list again.

**Files:**
- Modify: `src/main/resources/templates/session/modules/_reference.html:28-34`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java`

**Interfaces:**
- Consumes: `CockpitInitialLoadFixtures#campaignWithGroupedEncounter` (Task 1) — its statblock is named "Goblin", which the search will find.
- Produces: `[data-reference-result]` rows expose `.reference-item__title` and `.reference-item__qualifier`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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

import static org.assertj.core.api.Assertions.assertThat;

/** Reference material has to be readable, complete, and never at the cost of the tracker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CockpitReferenceBrowserTest {

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
        failures.clear();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1366, 768));
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private CockpitInitialLoadFixtures.GroupedEncounter openCombat() {
        var seeded = fixtures.campaignWithGroupedEncounter();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector(".combatant-row");
        return seeded;
    }

    @Test
    void everySearchResultRowHasVisibleText() {
        openCombat();
        page.fill(".reference-search-form input", "Gob");
        page.waitForSelector("[data-reference-result]");

        var rows = page.locator("[data-reference-result]");
        int count = rows.count();
        assertThat(count).isGreaterThan(0);
        for (int i = 0; i < count; i++) {
            assertThat(rows.nth(i).innerText().trim())
                    .as("result row %d must not render as an invisible strip", i)
                    .isNotEmpty();
        }
        assertThat(rows.first().locator(".reference-item__title").innerText().trim())
                .as("the row's title is the payload's `title`, which is what the endpoint returns")
                .isEqualTo("Goblin");
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=CockpitReferenceBrowserTest`
Expected: FAIL. `.reference-item__title` does not exist and every row's inner text is empty.

- [ ] **Step 3: Bind the fields the payload has**

In `src/main/resources/templates/session/modules/_reference.html`, replace lines 28-34:

```html
            <template x-for="item in group.items" :key="item.id">
              <div class="reference-item u-text-sm u-px-xs u-py-xs" data-reference-result
                   @click="selectItem(item)">
                <!-- The search payload is {id, title, type, subtype, url}. Binding anything
                     else renders a clickable, invisible strip. -->
                <span class="reference-item__title" x-text="item.title"></span>
                <span class="reference-item__qualifier u-text-xs text-muted"
                      x-text="item.subtype || item.type"></span>
              </div>
            </template>
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./mvnw -o test -Dtest=CockpitReferenceBrowserTest`
Expected: PASS, 1 test.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/session/modules/_reference.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java
git commit -m "fix: render compendium search results against the payload they receive"
```

---

### Task 12: The in-run statblock is runnable

`GET /api/v1/library/statblocks/{id}` returns `StatBlockSummary(id, name, cr, type, hp, ac, xp)`. Both in-cockpit surfaces consume it: the Reference module card and the tracker's FOCUSED statblock panel. The FOCUSED panel already contains full markup for abilities, saves, skills, speed, senses, languages, traits, actions with attack bonuses and damage prefill, and spellcasting — and renders none of it, because none of those fields arrive. The DM cannot read the goblin's Scimitar without leaving the cockpit.

Product decision D-1: one endpoint returns the full runtime projection, which lights up both surfaces at once. `LibraryController.enrichStatBlock` already parses these JSON columns and extracts `+N to hit` / `(NdM+K)` from the entry name; that logic moves into the projection so the API and the library page cannot drift.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockRuntimeProjection.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java:64-69`
- Modify: `src/main/resources/templates/session/modules/_reference.html:41-55`
- Modify: `src/main/resources/templates/encounter/_tracker.html:505-507` (`armorClass` → `ac`), `:524-531` (skills is a string)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiControllerTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `StatBlockRuntimeProjection` and `StatBlockRuntimeProjection.from(StatBlock, ObjectMapper)` as declared in *Stable interfaces*. `GET /api/v1/library/statblocks/{id}` returns it. `/statblocks/search` keeps returning `StatBlockSummary` — a search result list does not need actions.

- [ ] **Step 1: Write the failing API test**

`LibraryApiControllerTest` already exists as a `@WebMvcTest` with a mocked
`StatBlockService`. Add `import java.util.UUID;` and this method inside the existing class:

```java
    @Test
    void theRuntimeStatblockExposesWhatRunningTheCreatureNeeds() throws Exception {
        UUID id = UUID.randomUUID();
        StatBlock sb = new StatBlock();
        sb.setId(id);
        sb.setName("Goblin");
        sb.setCr("1/4");
        sb.setType("Humanoid");
        sb.setAc(15);
        sb.setHp("7 (2d6)");
        sb.setXp(50);
        sb.setSpeed("30 ft.");
        sb.setSenses("Darkvision 60 ft.");
        sb.setLanguages("Common, Goblin");
        sb.setDexScore(14);
        sb.setDexSave(4);
        sb.setActions("[{\"name\":\"Scimitar. Melee Attack Roll: +4 to hit, reach 5 ft.\","
                + "\"description\":\"Hit: 5 (1d6+2) Slashing damage.\"}]");
        sb.setBonusActions("[{\"name\":\"Nimble Escape\","
                + "\"description\":\"Takes the Disengage or Hide action.\"}]");
        when(statBlockService.findById(id)).thenReturn(sb);

        mockMvc.perform(get("/api/v1/library/statblocks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ac").value(15))
                .andExpect(jsonPath("$.speed").value("30 ft."))
                .andExpect(jsonPath("$.senses").value("Darkvision 60 ft."))
                .andExpect(jsonPath("$.languages").value("Common, Goblin"))
                .andExpect(jsonPath("$.abilityScores.dex").value(14))
                .andExpect(jsonPath("$.savingThrows[0].ability").value("dex"))
                .andExpect(jsonPath("$.savingThrows[0].modifier").value(4))
                .andExpect(jsonPath("$.actions[0].attackBonus").value(4))
                .andExpect(jsonPath("$.actions[0].damageExpression").value("1d6+2"))
                .andExpect(jsonPath("$.bonusActions[0].name").value("Nimble Escape"));
    }
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=LibraryApiControllerTest`
Expected: FAIL — `$.speed` does not exist; the endpoint returns the seven-field summary.

- [ ] **Step 3: Write the projection**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockRuntimeProjection.java`:

```java
package dev.hendrikhoemberg.dmhelper.library.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything a DM needs to take a creature's turn without leaving the cockpit: attacks with
 * to-hit and damage, and the traits and bonus actions that change how the creature acts.
 * AC, HP and XP alone are not runnable.
 */
public record StatBlockRuntimeProjection(
        UUID id, String name, String size, String type, String alignment, String cr,
        int ac, String hp, int xp, String speed, String senses, String languages,
        String skills, String damageResistances, String damageImmunities,
        String conditionImmunities,
        Map<String, Integer> abilityScores,
        List<Save> savingThrows,
        List<Entry> traits, List<Entry> actions, List<Entry> bonusActions,
        List<Entry> reactions, List<Entry> legendaryActions) {

    public record Save(String ability, int modifier) {}

    public record Entry(String name, String description,
                        Integer attackBonus, String damageExpression) {}

    private static final Pattern ATTACK = Pattern.compile("\\+(\\d+) to hit");
    private static final Pattern DAMAGE = Pattern.compile("\\((\\d+d\\d+[-+]?\\d*)\\)");

    public static StatBlockRuntimeProjection from(StatBlock sb, ObjectMapper mapper) {
        Map<String, Integer> abilities = new LinkedHashMap<>();
        abilities.put("str", sb.getStrScore());
        abilities.put("dex", sb.getDexScore());
        abilities.put("con", sb.getConScore());
        abilities.put("int", sb.getIntScore());
        abilities.put("wis", sb.getWisScore());
        abilities.put("cha", sb.getChaScore());

        List<Save> saves = new ArrayList<>();
        addSave(saves, "str", sb.getStrSave());
        addSave(saves, "dex", sb.getDexSave());
        addSave(saves, "con", sb.getConSave());
        addSave(saves, "int", sb.getIntSave());
        addSave(saves, "wis", sb.getWisSave());
        addSave(saves, "cha", sb.getChaSave());

        return new StatBlockRuntimeProjection(
                sb.getId(), sb.getName(), sb.getSize(), sb.getType(), sb.getAlignment(),
                sb.getCr(), sb.getAc(), sb.getHp(), sb.getXp(), sb.getSpeed(),
                sb.getSenses(), sb.getLanguages(), sb.getSkills(),
                sb.getDamageResistances(), sb.getDamageImmunities(), sb.getConditionImmunities(),
                abilities, List.copyOf(saves),
                entries(sb.getTraits(), mapper),
                entries(sb.getActions(), mapper),
                entries(sb.getBonusActions(), mapper),
                entries(sb.getReactions(), mapper),
                entries(sb.getLegendaryActions(), mapper));
    }

    private static void addSave(List<Save> out, String ability, Integer modifier) {
        if (modifier != null) out.add(new Save(ability, modifier));
    }

    /**
     * The entity stores these as JSON arrays of {name, description}. The to-hit bonus and the
     * damage expression live inside `name` in SRD prose, so they are lifted out here — the
     * tracker offers a damage prefill and needs the expression, not the sentence.
     */
    private static List<Entry> entries(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return List.of();
        List<Map<String, String>> raw;
        try {
            raw = mapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return List.of(new Entry("(unreadable entry)", json, null, null));
        }
        List<Entry> out = new ArrayList<>(raw.size());
        for (Map<String, String> item : raw) {
            String entryName = item.getOrDefault("name", "");
            String description = item.getOrDefault("description", "");
            Matcher attack = ATTACK.matcher(entryName);
            Matcher damage = DAMAGE.matcher(entryName + " " + description);
            out.add(new Entry(entryName, description,
                    attack.find() ? Integer.valueOf(attack.group(1)) : null,
                    damage.find() ? damage.group(1) : null));
        }
        return List.copyOf(out);
    }
}
```

- [ ] **Step 4: Serve the projection**

In `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java`, add
`import tools.jackson.databind.ObjectMapper;` and
`import dev.hendrikhoemberg.dmhelper.library.service.StatBlockRuntimeProjection;`, add an
`ObjectMapper objectMapper` constructor parameter and field, then replace `getStatblock`
(lines 64-69):

```java
    /**
     * The full runtime projection, not the search summary. Both cockpit surfaces — the
     * Reference card and the tracker's focused panel — read this, and both need the actions.
     */
    @GetMapping("/statblocks/{id}")
    public StatBlockRuntimeProjection getStatblock(@PathVariable UUID id) {
        return StatBlockRuntimeProjection.from(statBlockService.findById(id), objectMapper);
    }
```

- [ ] **Step 5: Render it in the Reference card**

In `src/main/resources/templates/session/modules/_reference.html`, replace the `<template x-if="statblock">` block (lines 41-55):

```html
      <!-- x-if, not x-show: x-show keeps the bindings live, so every x-text below would
           evaluate against a null statblock on first paint and throw. -->
      <template x-if="statblock">
        <div class="reference-detail" x-transition:enter>
          <button class="btn btn-ghost u-text-xs u-mb-sm" @click="clearStatblock()" type="button">&larr; Back to results</button>
          <div class="statblock-render">
            <h2 x-text="statblock.name"></h2>
            <div class="sb-subtitle"
                 x-text="[statblock.size, statblock.type, statblock.alignment]
                          .filter(Boolean).join(' ') + (statblock.cr ? ' · CR ' + statblock.cr : '')"></div>
            <hr class="sb-rule">
            <div class="sb-stat-row">
              <div><span>Armor Class</span> <span x-text="statblock.ac"></span></div>
              <div><span>Hit Points</span> <span x-text="statblock.hp"></span></div>
              <div><span>Speed</span> <span x-text="statblock.speed || '—'"></span></div>
              <div><span>XP</span> <span x-text="statblock.xp"></span></div>
            </div>
            <div class="sb-abilities">
              <template x-for="(score, key) in (statblock.abilityScores || {})" :key="key">
                <span class="badge" x-text="key.toUpperCase() + ' ' + score"></span>
              </template>
            </div>
            <p class="u-text-xs" x-show="statblock.savingThrows?.length">
              <strong>Saving Throws</strong>
              <template x-for="s in (statblock.savingThrows || [])" :key="s.ability">
                <span x-text="' ' + s.ability.toUpperCase() + ' +' + s.modifier"></span>
              </template>
            </p>
            <p class="u-text-xs" x-show="statblock.skills"><strong>Skills</strong> <span x-text="statblock.skills"></span></p>
            <p class="u-text-xs" x-show="statblock.senses"><strong>Senses</strong> <span x-text="statblock.senses"></span></p>
            <p class="u-text-xs" x-show="statblock.languages"><strong>Languages</strong> <span x-text="statblock.languages"></span></p>

            <template x-for="section in statblockSections()" :key="section.label">
              <div class="sb-section" x-show="section.entries.length">
                <h3 class="u-text-sm" x-text="section.label"></h3>
                <template x-for="entry in section.entries" :key="entry.name">
                  <p class="u-text-sm">
                    <strong x-text="entry.name"></strong>
                    <span x-text="entry.description"></span>
                    <button type="button" class="btn btn-ghost btn-xs"
                            x-show="entry.damageExpression"
                            @click="prefill(entry)">Prefill damage</button>
                  </p>
                </template>
              </div>
            </template>
          </div>
        </div>
      </template>
```

Add to `src/main/resources/static/js/cockpit-reference.js`, inside the Alpine data object:

```javascript
      statblockSections() {
        const sb = this.statblock || {};
        return [
          { label: 'Traits', entries: sb.traits || [] },
          { label: 'Actions', entries: sb.actions || [] },
          { label: 'Bonus Actions', entries: sb.bonusActions || [] },
          { label: 'Reactions', entries: sb.reactions || [] },
          { label: 'Legendary Actions', entries: sb.legendaryActions || [] },
        ];
      },

      prefill(entry) {
        window.dispatchEvent(new CustomEvent('dice-roller-prefill', {
          detail: {
            expression: entry.damageExpression,
            label: (this.statblock?.name || '') + ' — ' + entry.name,
          }
        }));
      },
```

- [ ] **Step 6: Point the focused tracker panel at the same field names**

In `src/main/resources/templates/encounter/_tracker.html`:

Replace lines 505-507:

```html
                        <div x-show="focusedStatblock.ac" class="u-text-xs">
                            <span>AC: </span><span x-text="focusedStatblock.ac"></span>
                        </div>
```

Replace the Skills block (lines 524-531) — the entity stores skills as one display string, so iterating it was never going to work:

```html
                        <div class="u-mt-xs" x-show="focusedStatblock.skills">
                            <strong class="u-text-xs">Skills</strong>
                            <span class="u-text-xs" x-text="focusedStatblock.skills"></span>
                        </div>
```

Replace the Saving Throws inner loop (lines 519-521) so it reads `Save.ability`/`Save.modifier`:

```html
                                <template x-for="st in focusedStatblock.savingThrows" :key="st.ability">
                                    <span class="badge tracker-statblock-badge"
                                          x-text="st.ability.toUpperCase() + ' +' + st.modifier"></span>
                                </template>
```

Delete the separate Spellcasting block (lines 571-578). The entity has no separate
spellcasting column: SRD spellcasting is an entry in `actions`, so it renders in the Actions
section from the same projection instead of through a permanently empty property.

Fix the stale property name at line 581: `selected?.statBlockId` (capital B), matching `CombatantDto`:

```html
                    <div x-show="!focusedStatblockLoading && !focusedStatblock && selected?.statBlockId" class="u-text-xs text-muted">
                        Statblock unavailable
                    </div>
```

- [ ] **Step 7: Write the failing browser test**

Add to `CockpitReferenceBrowserTest`:

```java
    @Test
    void theInRunStatblockShowsTheCreaturesActions() {
        openCombat();
        page.fill(".reference-search-form input", "Gob");
        page.waitForSelector("[data-reference-result]");
        page.locator("[data-reference-result]").first().click();
        page.waitForSelector(".statblock-render");

        assertThat(page.locator(".statblock-render").innerText())
                .as("AC/HP/XP alone is not runnable")
                .contains("Speed")
                .contains("Actions");
    }
```

The fixture's goblin needs actions for this to mean anything. Extend `campaignWithGroupedEncounter` in `CockpitInitialLoadFixtures`, right after `goblin.setXp(50);`:

```java
        goblin.setSpeed("30 ft.");
        goblin.setSenses("Darkvision 60 ft.");
        goblin.setLanguages("Common, Goblin");
        goblin.setDexScore(14);
        goblin.setDexSave(4);
        goblin.setActions("[{\"name\":\"Scimitar. Melee Attack Roll: +4 to hit, reach 5 ft.\","
                + "\"description\":\"Hit: 5 (1d6+2) Slashing damage.\"}]");
        goblin.setBonusActions("[{\"name\":\"Nimble Escape\","
                + "\"description\":\"Takes the Disengage or Hide action.\"}]");
```

- [ ] **Step 8: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='LibraryApiControllerTest,CockpitReferenceBrowserTest'`
Expected: PASS, 3 tests.

- [ ] **Step 9: Verify every direct consumer against the new shape**

Run:

```bash
rg -n "api/v1/library/statblocks/" \
  src/main/resources/static/js \
  src/main/resources/templates
```

Expected direct-detail consumers:

- `static/js/cockpit-reference.js` renders the full projection from Step 5.
- `static/js/combat-tracker.js` feeds the focused panel corrected in Step 6.
- `static/js/map/battle-map.js#createTokenFromStatblock` reads only `name`, which the new
  projection preserves.

The two `/statblocks/search` consumers still receive `StatBlockSummary` and are unaffected.
Also run:

```bash
rg -n "focusedStatblock\\.armorClass|statblock\\.armorClass" \
  src/main/resources/static/js \
  src/main/resources/templates
```

Expected: no matches. The runtime projection's field is `ac`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockRuntimeProjection.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiController.java \
        src/main/resources/templates/session/modules/_reference.html \
        src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/js/cockpit-reference.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitInitialLoadFixtures.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryApiControllerTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java
git commit -m "feat: serve a runnable statblock inside the cockpit"
```

---

### Task 13: Reference and the dice roller stop hiding the tracker

Two surfaces cover the thing they are consulted alongside. In the Combat preset, `encounter` and `reference` are tabs of the same zone, so looking up a rule hides the initiative order and every HP bar. And the Dice Roller is a 320px right-edge drawer that covers the whole right rail — the two surfaces a DM uses together cannot be seen together.

Product decision D-2: reference joins the left-support zone. The dice drawer is offset by the right zone's width so it docks beside the rail rather than over it.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java:22-25`
- Modify: `src/main/resources/static/js/cockpit-layout.js` (publish the right zone's width)
- Modify: `src/main/resources/static/css/components.css:1611-1625` (`.dice-panel`)
- Modify: `src/main/resources/templates/fragments/_dice-roller.html:13-14` (accessible close label — K11)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: CSS custom property `--cockpit-right-zone-width` on `:root`, set by the layout controller on every render. `.dice-panel` reads it.

- [ ] **Step 1: Write the failing tests**

Add to `CockpitReferenceBrowserTest`:

```java
    @Test
    void openingAStatblockLeavesTheInitiativeOrderVisible() {
        openCombat();
        var trackerBefore = page.locator(".tracker-list").boundingBox();
        assertThat(trackerBefore).isNotNull();

        page.fill(".reference-search-form input", "Gob");
        page.waitForSelector("[data-reference-result]");
        page.locator("[data-reference-result]").first().click();
        page.waitForSelector(".statblock-render");

        assertThat(page.locator(".combatant-row").first().isVisible())
                .as("consulting a reference must not remove the tracker from the screen")
                .isTrue();
        assertThat(page.locator(".tracker-list").boundingBox()).isNotNull();
    }

    @Test
    void theDiceRollerDocksBesideTheTrackerNotOverIt() {
        openCombat();
        page.evaluate("() => window.dispatchEvent(new CustomEvent('dice-roller-toggle'))");
        page.waitForSelector(".dice-panel:not(.closed)");

        var dice = page.locator(".dice-panel").boundingBox();
        var tracker = page.locator(".tracker-list").boundingBox();
        assertThat(dice).isNotNull();
        assertThat(tracker).isNotNull();
        assertThat(dice.x + dice.width)
                .as("the dice drawer must not overlap the initiative order")
                .isLessThanOrEqualTo(tracker.x + 1);

        assertThat(page.locator(".dice-panel-close").getAttribute("aria-label"))
                .isEqualTo("Close dice roller");
    }
```

Add to `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java`:

```java
    @Test
    void theCombatPresetDoesNotMakeReferenceCompeteWithTheTracker() {
        var combat = new CockpitBuiltInPresetCatalog().require("builtin:combat");
        var right = combat.layout().zones().get(CockpitZone.RIGHT_SUPPORT);
        var left = combat.layout().zones().get(CockpitZone.LEFT_SUPPORT);

        assertThat(right.moduleKeys()).containsExactly("encounter");
        assertThat(left.moduleKeys()).contains("reference");
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest='CockpitBuiltInPresetCatalogTest,CockpitReferenceBrowserTest'`
Expected: FAIL — the right zone still holds `["encounter", "reference"]`, and the dice panel starts at the viewport's right edge.

- [ ] **Step 3: Move reference out of the tracker's zone**

In `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java`, replace the combat preset (lines 22-25):

```java
            // Reference lives beside story and party, never as a tab over the tracker: looking
            // up a rule must not remove the initiative order and every HP bar from the screen.
            preset("builtin:combat", "Combat",
                    zone("map"), zone("story", "party", "reference"), zone("encounter"),
                    zone("quick-notes", "audio"),
                    Set.of("story", "party", "encounter", "quick-notes", "audio", "reference")),
```

In the existing
`CockpitBuiltInPresetCatalogTest#combatPresetHasTheApprovedModulePlacement`, change the
affected expectations:

```java
        assertThat(layout.zones().get(CockpitZone.LEFT_SUPPORT).moduleKeys())
                .containsExactly("story", "party", "reference");
        assertThat(layout.zones().get(CockpitZone.RIGHT_SUPPORT).moduleKeys())
                .containsExactly("encounter");
```

- [ ] **Step 4: Publish the right zone's width**

In `src/main/resources/static/js/cockpit-layout.js`, inside `renderLayout()`, add this after
the final `for (const key of this.modules.keys())` loop and before the method's closing brace:

```javascript
      // Overlays that sit on the right edge need to know where the rail begins. The dice
      // drawer used to cover the tracker outright — the two surfaces a DM uses together.
      const rightZone = this.workbench?.querySelector('[data-cockpit-zone="RIGHT_SUPPORT"]');
      const rightWidth = rightZone && rightZone.dataset.collapsed !== 'true'
        ? Math.round(rightZone.getBoundingClientRect().width)
        : 0;
      document.documentElement.style.setProperty(
        '--cockpit-right-zone-width', rightWidth + 'px');
```

- [ ] **Step 5: Dock the dice drawer beside the rail**

In `src/main/resources/static/css/components.css`, replace lines 1611-1616:

```css
.dice-panel {
    position: fixed;
    right: var(--cockpit-right-zone-width, 0px);
    top: 0;
    bottom: 0;
    width: 320px;
```

and replace the closed transform (lines 1626-1628) so the drawer still slides fully off-screen:

```css
.dice-panel.closed {
    transform: translateX(calc(100% + var(--cockpit-right-zone-width, 0px)));
}
```

- [ ] **Step 6: Name the close control (K11)**

In `src/main/resources/templates/fragments/_dice-roller.html`, replace lines 13-14:

```html
        <button type="button" class="btn btn-ghost dice-panel-close"
                aria-label="Close dice roller"
                @click="open = false">&times;</button>
```

- [ ] **Step 7: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='CockpitBuiltInPresetCatalogTest,CockpitReferenceBrowserTest,DiceQuickRollBrowserTest,CockpitLayout*Test'`
Expected: PASS, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java \
        src/main/resources/static/js/cockpit-layout.js \
        src/main/resources/static/css/components.css \
        src/main/resources/templates/fragments/_dice-roller.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java
git commit -m "fix: keep the tracker on screen while consulting references and dice"
```

---

## Phase E — Session lifecycle truthfulness

### Task 14: Cancel Review returns the session to where it was

`beginReview` moves `RUNNING → REVIEW` and generates a draft. `cancelReview` sets `Status.PAUSED`, `reviewStartedAt = null` and `draftBody = null` — unconditionally. A DM who opens the review to glance at the draft and backs out has silently paused their session and lost their edits.

Product decision D-3: cancel means "return to the state I was in". `beginReview` records the status it came from; `cancelReview` restores it and keeps the draft.

**Files:**
- Create: `src/main/resources/db/migration/V29__session_pre_review_status.sql`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSession.java` (add `preReviewStatus`)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleService.java:134-141` and `:164-173` and `:233-248`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionReviewCancelTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `CampaignSession#getPreReviewStatus()` / `#setPreReviewStatus(Status)`, nullable.
  Task 15 reads the restored status; Task 22's draft flow relies on the draft surviving.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionReviewCancelTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** "Cancel" means "return to the state I was in". Anything else is a trap door. */
@SpringBootTest
@Transactional
class SessionReviewCancelTest {

    @Autowired private SessionLifecycleService lifecycle;
    @Autowired private CampaignSessionRepository sessions;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    @Test
    void cancellingAReviewEnteredWhileRunningReturnsToRunning() {
        UUID campaignId = fixtures.campaignWithRunningSession();

        lifecycle.beginReview(campaignId);
        assertThat(sessions.findByCampaignId(campaignId).orElseThrow().getStatus())
                .isEqualTo(CampaignSession.Status.REVIEW);

        lifecycle.cancelReview(campaignId);
        assertThat(sessions.findByCampaignId(campaignId).orElseThrow().getStatus())
                .isEqualTo(CampaignSession.Status.RUNNING);
    }

    @Test
    void cancellingAReviewEnteredWhilePausedReturnsToPaused() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.pause(campaignId);

        lifecycle.beginReview(campaignId);
        lifecycle.cancelReview(campaignId);

        assertThat(sessions.findByCampaignId(campaignId).orElseThrow().getStatus())
                .isEqualTo(CampaignSession.Status.PAUSED);
    }

    @Test
    void cancellingAReviewKeepsTheDraft() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.beginReview(campaignId);
        String draft = sessions.findByCampaignId(campaignId).orElseThrow().getDraftBody();
        assertThat(draft).isNotBlank();

        lifecycle.cancelReview(campaignId);

        assertThat(sessions.findByCampaignId(campaignId).orElseThrow().getDraftBody())
                .as("draft content must not be lost without an explicit confirmation")
                .isEqualTo(draft);
    }

    @Test
    void reopeningTheReviewKeepsTheDmsEdits() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        lifecycle.beginReview(campaignId);
        CampaignSession session = sessions.findByCampaignId(campaignId).orElseThrow();
        session.setDraftBody("## Recap\nThe goblins broke on the third round.\n");
        sessions.save(session);

        lifecycle.cancelReview(campaignId);
        lifecycle.beginReview(campaignId);

        assertThat(sessions.findByCampaignId(campaignId).orElseThrow().getDraftBody())
                .contains("broke on the third round");
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest=SessionReviewCancelTest`
Expected: FAIL, 4 tests. Every cancel lands on `PAUSED` and clears the draft.

- [ ] **Step 3: Add the column**

Create `src/main/resources/db/migration/V29__session_pre_review_status.sql`:

```sql
-- Cancelling a review must return the session to the status it held before the review,
-- which nothing recorded. Nullable: only a session that has entered review has one.
ALTER TABLE campaign_session ADD COLUMN pre_review_status VARCHAR(16);
```

In `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSession.java`, next to the `status` field (after line 37):

```java
    /** The status this session held when review began, so Cancel can restore it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pre_review_status", length = 16)
    private Status preReviewStatus;
```

and with the other accessors:

```java
    public Status getPreReviewStatus() { return preReviewStatus; }
    public void setPreReviewStatus(Status preReviewStatus) { this.preReviewStatus = preReviewStatus; }
```

Confirm the class already imports `jakarta.persistence.Enumerated` and `jakarta.persistence.EnumType` — the existing `status` field is `@Column(nullable = false, length = 16)`, so add whichever annotation import is missing.

- [ ] **Step 4: Record and restore it**

In `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleService.java`, replace `cancelReview` (lines 134-141):

```java
    /**
     * A back-out, not a state change. The session returns to whatever it was doing and the
     * draft survives, so opening the review to glance at it costs nothing.
     */
    public CampaignSession cancelReview(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        requireStatus(session, CampaignSession.Status.REVIEW, "Only a session under review can return to play.");
        CampaignSession.Status restored = session.getPreReviewStatus() != null
                ? session.getPreReviewStatus()
                : CampaignSession.Status.RUNNING;
        session.setStatus(restored);
        session.setPreReviewStatus(null);
        session.setReviewStartedAt(null);
        return saveForState(session);
    }
```

Replace `beginReview` (lines 164-173):

```java
    public CampaignSession beginReview(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        if (session.getStatus() != CampaignSession.Status.RUNNING && session.getStatus() != CampaignSession.Status.PAUSED)
            throw new IllegalStateException("Only a running or paused session can be reviewed.");
        Instant now = clock.instant();
        session.setPreReviewStatus(session.getStatus());
        session.setStatus(CampaignSession.Status.REVIEW);
        session.setReviewStartedAt(now);
        // A draft the DM has already edited is their work. Regenerate only the first time.
        if (session.getDraftBody() == null || session.getDraftBody().isBlank()) {
            session.setDraftBody(drafts.generate(session, now));
        }
        return saveForState(session);
    }
```

In `resetToIdle` (lines 233-248), add next to `session.setReviewStartedAt(null);`:

```java
        session.setPreReviewStatus(null);
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='SessionReviewCancelTest,SessionAbandonTest,SessionLifecycle*Test'`
Expected: PASS, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V29__session_pre_review_status.sql \
        src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSession.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionReviewCancelTest.java
git commit -m "fix: make Cancel Review return the session to where it was"
```

---

### Task 15: The lifecycle dialog states the session's real status, and destructive actions look destructive

`_lifecycle-dialog.html:26` is a hardcoded `<h3>Session Running</h3>` inside a block shown for `RUNNING || PAUSED`. With the badge reading `PAUSED` and the primary action reading `Resume`, the heading still said Running.

The same dialog puts `Discard session` inline with `Close` at identical weight, in both the lifecycle and review blocks. The `End active encounter?` dialog is the standard to meet: clear title, plain consequence statement, danger-styled confirm. And both dialogs use unstyled native `<select>` and native checkboxes (system blue) inside the app's dark/gold theme (K6).

**Files:**
- Modify: `src/main/resources/templates/session/_lifecycle-dialog.html` (heading, control styling, destructive separation)
- Modify: `src/main/resources/static/js/session-cockpit.js` (`confirmAbandonSession` uses the app dialog)
- Modify: `src/main/resources/static/css/components.css` (themed `select`/`checkbox`, `.lifecycle-dialog__danger`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java`

**Interfaces:**
- Consumes: `preReviewStatus` restoration (Task 14) — the dialog's Resume/Pause pair must reflect the restored status.
- Produces: `[data-lifecycle-heading]` on the dialog heading; `#sessionDiscardDialog` as the confirm dialog; `sessionCockpit#requestAbandonSession()` / `#confirmAbandonSession()`.

- [ ] **Step 1: Write the failing contract test**

Add to `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`:

```java
    @Test
    void theLifecycleDialogStatesTheRealStatusAndConfirmsDestruction() throws IOException {
        String dialog = Files.readString(
                Path.of("src/main/resources/templates/session/_lifecycle-dialog.html"));

        assertThat(dialog)
                .as("the heading must be derived from sessionStatus, never hardcoded")
                .doesNotContain("<h3>Session Running</h3>")
                .contains("data-lifecycle-heading");

        assertThat(dialog)
                .as("every destructive lifecycle action confirms and carries the danger treatment")
                .contains("requestAbandonSession()")
                .contains("lifecycle-dialog__danger")
                .doesNotContain("@click=\"confirmAbandonSession()\"");

        assertThat(dialog)
                .as("dialog controls must wear the app's theme, as End active encounter? does")
                .contains("class=\"form-input\"")
                .contains("class=\"form-check\"");
    }
```

- [ ] **Step 2: Write the failing browser assertion**

Add to `CockpitReferenceBrowserTest` (it already boots the cockpit; the assertion moves nowhere else):

```java
    @Test
    void thePausedSessionDialogSaysPaused() {
        openCombat();
        page.evaluate("() => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".pauseSession()");
        page.waitForFunction("() => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".sessionStatus === 'PAUSED'");
        page.evaluate("() => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".openLifecycle()");
        page.waitForSelector("[data-lifecycle-heading]");

        assertThat(page.locator("[data-lifecycle-heading]").innerText().trim())
                .isEqualTo("Session Paused");
    }
```

`session-cockpit.js` exposes this method as `openLifecycle()`; use that existing method and do
not add an alias.

- [ ] **Step 3: Run both and watch them fail**

Run: `./mvnw -o test -Dtest='SessionCockpitTemplateContractTest,CockpitReferenceBrowserTest#thePausedSessionDialogSaysPaused'`
Expected: FAIL — the heading is hardcoded and there is no `data-lifecycle-heading`.

- [ ] **Step 4: Rewrite the dialog**

Replace `src/main/resources/templates/session/_lifecycle-dialog.html` lines 10-69 (both status blocks) with:

```html
    <div x-show="sessionStatus === 'IDLE'">
      <h3 data-lifecycle-heading>Start Session</h3>
      <p>Choose a starting map (optional):</p>
      <label class="u-block">
        <span class="sr-only">Starting map</span>
        <select x-model="startMapId" class="form-input">
          <option value="">No map</option>
          <template x-for="m in maps" :key="m.id">
            <option :value="m.id" x-text="m.name"></option>
          </template>
        </select>
      </label>
      <div class="cockpit-dialog__actions">
        <button class="btn btn-primary" @click="startSession()">Start</button>
        <button class="btn btn-ghost" @click="closeLifecycle()">Cancel</button>
      </div>
    </div>

    <div x-show="sessionStatus === 'RUNNING' || sessionStatus === 'PAUSED'">
      <h3 data-lifecycle-heading
          x-text="sessionStatus === 'PAUSED' ? 'Session Paused' : 'Session Running'">Session Running</h3>
      <fieldset style="margin-bottom: var(--space-md);">
        <legend>Attendance</legend>
        <label class="form-check" th:each="member : ${attendanceMembers}">
          <input type="checkbox" class="form-check__input" x-model="attendeeIds" th:value="${member.id}">
          <span th:text="${member.characterName}">Character</span>
          <span th:unless="${member.active}" class="text-muted"> (inactive)</span>
        </label>
        <button class="btn btn-ghost" type="button" @click="updateAttendance()">Save attendance</button>
      </fieldset>
      <div class="cockpit-dialog__actions">
        <button class="btn" x-show="sessionStatus === 'RUNNING'"
                @click="pauseSession()">Pause</button>
        <button class="btn" x-show="sessionStatus === 'PAUSED'"
                @click="resumeSession()">Resume</button>
        <button class="btn btn-ghost" @click="beginReview()">Review &amp; Complete</button>
        <button class="btn btn-ghost" @click="closeLifecycle()">Close</button>
      </div>
      <!-- Discarding a session is not a sibling of Close. Same treatment as
           "End active encounter?": separated, named, danger-styled, confirmed. -->
      <div class="lifecycle-dialog__danger">
        <button type="button" class="btn btn-danger action-row__destructive"
                data-abandon-session
                @click="requestAbandonSession()">Discard session</button>
      </div>
    </div>

    <form x-show="sessionStatus === 'REVIEW'" @submit.prevent="completeSession()">
      <h3 data-lifecycle-heading>Review Session</h3>
      <div>
        <label for="sessionDraftTitle">Title</label>
        <input id="sessionDraftTitle" type="text" x-model="draftTitle" class="form-input" placeholder="Session log title" required>
      </div>
      <div style="margin-top: var(--space-sm);">
        <label for="sessionDraftBody">Draft</label>
        <textarea id="sessionDraftBody" x-model="draftBody" class="form-input" rows="12"
                  placeholder="Session draft will appear here..."></textarea>
      </div>
      <div class="cockpit-dialog__actions">
        <button type="submit" class="btn btn-primary" data-complete-session>Save &amp; Complete</button>
        <button type="button" class="btn btn-ghost" @click="cancelReview()">Cancel Review</button>
        <button type="button" class="btn btn-ghost" @click="closeLifecycle()">Close</button>
      </div>
      <p class="u-text-xs text-muted">Cancelling returns the session to where it was and keeps this draft.</p>
      <div class="lifecycle-dialog__danger">
        <button type="button" class="btn btn-danger action-row__destructive"
                data-abandon-session
                @click="requestAbandonSession()">Discard session</button>
      </div>
    </form>
```

Add the confirm dialog immediately before the closing `</dialog>` of the lifecycle dialog is *not* possible — nested dialogs do not stack. Add it as a sibling, after `</dialog>` and before `</th:block>`:

```html
<dialog id="sessionDiscardDialog" class="cockpit-dialog"
        aria-labelledby="sessionDiscardTitle"
        @cancel.prevent="closeAbandonDialog()">
  <h2 id="sessionDiscardTitle">Discard this session?</h2>
  <p>The session log draft, the scenes visited, and the attendance record for this run are
     deleted. Campaign content, encounters and party sheets are not affected.</p>
  <div class="cockpit-dialog__actions">
    <button type="button" class="btn btn-danger action-row__destructive"
            data-confirm-abandon-session
            @click="confirmAbandonSession()">Discard session</button>
    <button type="button" class="btn btn-ghost" @click="closeAbandonDialog()">Keep session</button>
  </div>
</dialog>
```

- [ ] **Step 5: Replace the native confirm**

In `src/main/resources/static/js/session-cockpit.js`, find `confirmAbandonSession` (it currently calls `window.confirm`) and replace it with the pair:

```javascript
        requestAbandonSession() {
            const dialog = document.getElementById('sessionDiscardDialog');
            if (dialog && !dialog.open) dialog.showModal();
        },

        closeAbandonDialog() {
            const dialog = document.getElementById('sessionDiscardDialog');
            if (dialog?.open) dialog.close();
        },

        async confirmAbandonSession() {
            this.closeAbandonDialog();
            await this.abandonSession();
        },

        async abandonSession() {
            try {
                await this.request(
                    `/api/v1/campaigns/${this.campaignId}/session/abandon`,
                    { method: 'POST' });
                window.location.reload();
            } catch (error) {
                window.cockpitLayout?.showNotice(
                    'The session could not be discarded. Nothing was changed.');
            }
        },
```

This replaces the entire existing `confirmAbandonSession` method, including its
`window.confirm` and request body. In
`SessionCockpitTemplateContractTest#sessionActionsUseApplicationDialogsAndOfferSafeExit`,
replace:

```java
        assertThat(extractFunction(cockpitJs, "confirmAbandonSession"))
                .contains("window.confirm(");
```

with:

```java
        assertThat(cockpitJs).doesNotContain("window.confirm(");
        assertThat(cockpit).contains("data-confirm-abandon-session");
```

In `cockpitOwnsOneRuntimeIslandAndAccessibleRailControls`, replace
`assertThat(lifecycle).contains("confirmAbandonSession()");` with:

```java
        assertThat(lifecycle)
                .contains("requestAbandonSession()", "data-confirm-abandon-session");
```

- [ ] **Step 6: Theme the dialog controls**

In `src/main/resources/static/css/components.css`, add near the other form rules:

```css
/* Native controls inside a dark/gold dialog read as somebody else's UI. */
.cockpit-dialog select.form-input,
.lifecycle-dialog select.form-input {
    background: var(--color-bg);
    color: var(--color-text);
    border: 1px solid var(--color-border);
    border-radius: 4px;
    padding: 4px 8px;
}

.form-check { display: flex; align-items: center; gap: var(--space-xs); }
.form-check__input { accent-color: var(--color-gold-soft); }

.lifecycle-dialog__danger {
    margin-top: var(--space-md);
    padding-top: var(--space-sm);
    border-top: 1px solid var(--color-border);
}
```

- [ ] **Step 7: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='SessionCockpitTemplateContractTest,CockpitReferenceBrowserTest,SessionAbandonTest'`
Expected: PASS, 0 failures.

- [ ] **Step 8: Run the phase suite**

Run: `./mvnw -o test -Dtest='Session*Test,Cockpit*Test,Tracker*Test,Condition*Test,Library*Test'`
Expected: PASS, 0 failures.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/session/_lifecycle-dialog.html \
        src/main/resources/static/js/session-cockpit.js \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitReferenceBrowserTest.java
git commit -m "fix: state the real session status and confirm destructive lifecycle actions"
```

---

## Phase F — Failure signalling

### Task 16: A rejected input is not a failed save

`runtime-status.js#failed()` sets `setSave('error', 'Not saved')` for any failed DM mutation, including a 400 from `POST /api/v1/roll`. Typing `2d6+2 slashing` into the dice roller turned the cockpit header's persistence indicator red. Nothing had failed to save. The persistence indicator must report persistence: validation failures caused by user input and save failures (5xx, network, conflict) are different events and only the latter may change it.

`dm-request.js` is the one place that already sees the status code, so it classifies.

**Files:**
- Modify: `src/main/resources/static/js/dm-request.js` (add `kind`/`retryable` to `DmRequestError`, follow the toast policy)
- Modify: `src/main/resources/static/js/runtime-status.js:82-112` (validation failures do not touch the save atom)
- Modify: `src/main/resources/templates/session/cockpit.html:51-53` (a second atom for input rejections)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/FailureSignallingBrowserTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeStatusJavascriptContractTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `DmRequestError.kind` ∈ `{'validation','conflict','server','network'}` and `DmRequestError.retryable`; the `dm:request-failure` event detail gains `kind`; `[data-status-input]` is the new header atom.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/FailureSignallingBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The app may not tell a DM their session failed to save when nothing failed to save. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FailureSignallingBrowserTest {

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
        failures.clear();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1366, 768));
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private void openCockpit() {
        var seeded = fixtures.campaignWithGroupedEncounter();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
    }

    @Test
    void anInvalidDiceExpressionDoesNotSayTheSessionFailedToSave() {
        failures.expectHttpFailure("POST", Pattern.compile(".*/api/v1/roll$"), 400);
        openCockpit();
        page.evaluate("() => window.dispatchEvent(new CustomEvent('dice-roller-toggle'))");
        page.waitForSelector(".dice-panel:not(.closed)");

        page.fill(".dice-input-row input[type='text']", "3d8 fire damage");
        page.click(".dice-input-row .btn-primary");
        page.waitForSelector(".toast-error");

        assertThat(page.locator("[data-status-save]").getAttribute("data-state"))
                .as("a rejected input is not a failed save")
                .isNotEqualTo("error");
        assertThat(page.locator("[data-status-input]").innerText())
                .as("the rejection must still be reported, just not as a persistence failure")
                .containsIgnoringCase("not accepted");
    }

    @Test
    void aGenuineSaveFailureDoesEnterTheErrorState() {
        openCockpit();
        page.evaluate("""
                () => {
                  document.dispatchEvent(new CustomEvent('dm:request-start', {
                    detail: { url: '/api/v1/test', options: { method: 'POST' } } }));
                  document.dispatchEvent(new CustomEvent('dm:request-failure', {
                    detail: { url: '/api/v1/test', options: { method: 'POST' },
                              error: { status: 500, kind: 'server' } } }));
                }
                """);
        page.waitForFunction(
                "() => document.querySelector('[data-status-save]').dataset.state === 'error'");
        assertThat(page.locator("[data-status-save]").innerText()).isEqualTo("Not saved");
    }
}
```

Add to `src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeStatusJavascriptContractTest.java`:

```java
    @Test
    void validationFailuresNeverTouchTheSaveIndicator() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/runtime-status.js"));
        assertThat(js)
                .as("the persistence indicator must report persistence")
                .contains("kind === 'validation'")
                .contains("data-status-input");
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest='FailureSignallingBrowserTest,RuntimeStatusJavascriptContractTest'`
Expected: FAIL — the save atom reads `error` after the rejected expression, and `[data-status-input]` does not exist.

- [ ] **Step 3: Classify the failure at the one place that sees the status**

In `src/main/resources/static/js/dm-request.js`, replace lines 4-33:

```javascript
  /**
   * A 4xx caused by what the DM typed and a 5xx that lost their work are different events.
   * Only the second is a persistence failure, and only the second can be usefully retried.
   */
  function classify(status) {
    if (status === 0) return 'network';
    if (status === 409) return 'conflict';
    if (status >= 400 && status < 500) return 'validation';
    return 'server';
  }

  class DmRequestError extends Error {
    constructor(message, status = 0, correlationId = null) {
      super(message);
      this.name = 'DmRequestError';
      this.status = status;
      this.correlationId = correlationId;
      this.kind = classify(status);
      this.retryable = this.kind !== 'validation';
    }
  }

  async function responseError(response) {
    let detail = response.status === 409
      ? 'The item changed before this request completed. Reload and try again.'
      : `The server refused that request (${response.status}).`;
    let correlationId = response.headers.get('X-Correlation-ID');
    let problem = null;
    const contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('json')) {
      try {
        const body = await response.json();
        problem = body;
        detail = body.detail || detail;
        correlationId = body.correlationId || correlationId;
      } catch (_) {
        // A malformed error body must not hide the status/header fallback.
      }
    }
    const error = new DmRequestError(detail, response.status, correlationId);
    error.problem = problem;
    return error;
  }
```

In `window.dmRequest`, pass the classification along on both failure paths:

```javascript
    if (!response.ok) {
      const error = await responseError(response);
      emitRequestEvent('dm:request-failure', { ...detail, error, response, kind: error.kind });
      throw error;
    }
```

and for the network catch:

```javascript
    } catch (_) {
      const error = new DmRequestError('No answer from the server. Check it is still running.');
      emitRequestEvent('dm:request-failure', { ...detail, error, kind: error.kind });
      throw error;
    }
```

- [ ] **Step 4: Keep validation out of the save indicator**

In `src/main/resources/static/js/runtime-status.js`, add `const input = cluster.querySelector('[data-status-input]');` next to the `save` lookup (line 8), then replace `failed` and the `dm:request-failure` listener (lines 82-112):

```javascript
    function setInputRejection(message) {
      if (!input) return;
      input.hidden = false;
      input.textContent = message;
      clearTimeout(input._settle);
      input._settle = setTimeout(() => {
        input.hidden = true;
        input.textContent = '';
      }, 8000);
    }

    function failed(source) {
      if (source === 'dm') dmInFlight = Math.max(0, dmInFlight - 1);
      failedMutationEpoch = mutationEpoch;
      clearTimeout(settleTimer);
      setSave('error', 'Not saved');
    }

    // A 4xx the DM caused by typing is not a persistence failure. It is reported, loudly, in
    // its own atom; the save indicator keeps meaning what it says.
    function rejected(source) {
      if (source === 'dm') dmInFlight = Math.max(0, dmInFlight - 1);
      clearTimeout(settleTimer);
      setInputRejection('Input not accepted');
      markSaved();
    }

    document.body.addEventListener('htmx:responseError', (evt) => {
      settleHtmx(evt.detail);
      const status = evt.detail?.xhr?.status || 0;
      if (status >= 400 && status < 500 && status !== 409) rejected('htmx');
      else failed('htmx');
    });
    document.body.addEventListener('htmx:sendError', (evt) => {
      settleHtmx(evt.detail);
      failed('htmx');
    });

    document.addEventListener('dm:request-start', (evt) => {
      if (!isMutation(evt.detail)) return;
      startMutation('dm');
    });

    document.addEventListener('dm:request-success', (evt) => {
      if (!isMutation(evt.detail)) return;
      dmInFlight = Math.max(0, dmInFlight - 1);
      markSaved();
    });

    document.addEventListener('dm:request-failure', (evt) => {
      if (!isMutation(evt.detail)) return;
      const kind = evt.detail?.kind || evt.detail?.error?.kind;
      if (kind === 'validation') rejected('dm');
      else failed('dm');
    });
```

- [ ] **Step 5: Add the atom**

In `src/main/resources/templates/session/cockpit.html`, replace lines 51-53:

```html
    <span id="runtimeStatus" class="cockpit-status" role="status" aria-live="polite">
      <span class="cockpit-status__atom" data-status-save data-state="idle">Up to date</span>
      <span class="cockpit-status__atom cockpit-status__atom--input"
            data-status-input data-state="rejected" hidden></span>
    </span>
```

Add to `src/main/resources/static/css/cockpit-layout.css`, next to the other `.cockpit-status__atom` rules:

```css
.cockpit-status__atom--input { color: var(--color-warning); }
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='FailureSignallingBrowserTest,RuntimeStatusJavascriptContractTest,RuntimeStatusSurfaceTest'`
Expected: PASS, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/dm-request.js \
        src/main/resources/static/js/runtime-status.js \
        src/main/resources/templates/session/cockpit.html \
        src/main/resources/static/css/cockpit-layout.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/FailureSignallingBrowserTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/RuntimeStatusJavascriptContractTest.java
git commit -m "fix: stop reporting a rejected input as a failed save"
```

---

### Task 17: Toasts offer retries that can work, and stop stacking

The dice error toast read "The dice roll was not saved. Invalid dice expression: 3d8 fire damage Reference: `240ed084-…`." with a Retry that can only fail again. The map token toasts printed a UUID inline and stacked three deep. Three rules: a correlation id stays available for support but is not primary body text; Retry is offered only where retrying can succeed; repeated identical failures coalesce.

**Files:**
- Modify: `src/main/resources/static/js/dm-request.js:60-69` (`reportActionFailure`)
- Modify: `src/main/resources/static/js/ui-elevation.js:70-107` (`showToast` coalesces)
- Modify: `src/main/resources/static/css/components.css` (`.toast__reference`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/FailureSignallingBrowserTest.java`

**Interfaces:**
- Consumes: `DmRequestError.retryable` (Task 16).
- Produces: `showToast(message, type, duration, action, options)` where `options.dedupeKey` coalesces; toasts carry `[data-toast-count]` when coalesced and `.toast__reference` for the correlation id.

- [ ] **Step 1: Write the failing tests**

Add to `FailureSignallingBrowserTest`:

```java
    @Test
    void aValidationFailureOffersNoRetryAndDemotesTheCorrelationId() {
        failures.expectHttpFailure("POST", Pattern.compile(".*/api/v1/roll$"), 400);
        openCockpit();
        page.evaluate("() => window.dispatchEvent(new CustomEvent('dice-roller-toggle'))");
        page.waitForSelector(".dice-panel:not(.closed)");
        page.fill(".dice-input-row input[type='text']", "2d6+2 slashing");
        page.click(".dice-input-row .btn-primary");
        page.waitForSelector(".toast-error");

        var toast = page.locator(".toast-error").first();
        assertThat(toast.locator(".toast-action").count())
                .as("retrying an input the server will reject again is not a remedy")
                .isZero();
        assertThat(toast.locator("span").first().innerText())
                .as("the correlation id is support material, not body text")
                .doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}");
        assertThat(toast.locator(".toast__reference").count()).isEqualTo(1);
    }

    @Test
    void identicalFailuresCoalesceIntoOneToast() {
        openCockpit();
        page.evaluate("""
                () => {
                  for (let i = 0; i < 3; i++) {
                    window.reportActionFailure('Could not load map tokens.',
                      new window.DmRequestError('boom', 503, 'abc-123'), () => {});
                  }
                }
                """);
        page.waitForSelector(".toast-error");

        assertThat(page.locator(".toast-error").count())
                .as("three identical failures are one problem")
                .isEqualTo(1);
        assertThat(page.locator(".toast-error [data-toast-count]").innerText()).isEqualTo("3");
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest=FailureSignallingBrowserTest`
Expected: FAIL — the validation toast carries a Retry and an inline UUID; three toasts stack.

- [ ] **Step 3: Coalesce and structure the toast**

In `src/main/resources/static/js/ui-elevation.js`, replace `window.showToast` (lines 70-107):

```javascript
    const liveToasts = new Map();

    window.showToast = function(message, type = 'info', duration = 3000, action = null,
                                options = {}) {
      const dedupeKey = options.dedupeKey || null;
      // Three copies of one failure is one problem told three times. Count it instead.
      if (dedupeKey && liveToasts.has(dedupeKey)) {
        const existing = liveToasts.get(dedupeKey);
        if (existing.isConnected) {
          const counter = existing.querySelector('[data-toast-count]');
          counter.textContent = String(Number(counter.textContent || '1') + 1);
          counter.hidden = false;
          return;
        }
        liveToasts.delete(dedupeKey);
      }

      const toast = document.createElement('div');
      toast.className = 'toast toast-' + type;
      if (type === 'error') {
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
      }
      const text = document.createElement('span');
      text.textContent = message;
      toast.appendChild(text);

      const counter = document.createElement('span');
      counter.className = 'toast__count';
      counter.setAttribute('data-toast-count', '');
      counter.setAttribute('aria-label', 'occurrences');
      counter.textContent = '1';
      counter.hidden = true;
      toast.appendChild(counter);

      if (options.reference) {
        const reference = document.createElement('span');
        reference.className = 'toast__reference';
        reference.title = 'Reference for support: ' + options.reference;
        reference.textContent = 'ref ' + String(options.reference).slice(0, 8);
        toast.appendChild(reference);
      }

      if (action) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'toast-action';
        button.textContent = action.label;
        button.addEventListener('click', () => {
          if (dedupeKey) liveToasts.delete(dedupeKey);
          toast.remove();
          Promise.resolve()
            .then(() => action.handler())
            .catch(error => {
              if (window.reportActionFailure) {
                window.reportActionFailure(
                  'The retry did not complete.', error, action.handler);
              } else {
                window.showToast('The retry did not complete.', 'error', 7000);
              }
            });
        });
        toast.appendChild(button);
      }
      toastContainer().appendChild(toast);
      if (dedupeKey) liveToasts.set(dedupeKey, toast);
      requestAnimationFrame(() => toast.classList.add('show'));
      setTimeout(() => {
        if (dedupeKey) liveToasts.delete(dedupeKey);
        if (!toast.isConnected) return;
        toast.classList.remove('show');
        toast.addEventListener('transitionend', () => toast.remove(), { once: true });
      }, duration);
    };
```

- [ ] **Step 4: Apply the policy at the reporting site**

In `src/main/resources/static/js/dm-request.js`, replace `reportActionFailure` (lines 60-69):

```javascript
  window.reportActionFailure = function reportActionFailure(summary, error, retry) {
    const detail = error?.message ? ` ${error.message}` : '';
    // A validation failure is fixed by correcting the input, never by sending it again.
    const offerRetry = retry && error?.retryable !== false;
    window.showToast(
      summary + detail,
      'error',
      offerRetry ? 15000 : 7000,
      offerRetry ? { label: 'Retry', handler: retry } : null,
      { dedupeKey: summary + '|' + (error?.status ?? 0),
        reference: error?.correlationId || null }
    );
  };
```

- [ ] **Step 5: Style the reference and the counter**

In `src/main/resources/static/css/components.css`, next to the `.toast-action` rule:

```css
.toast__reference,
.toast__count {
    font-size: var(--text-xs);
    color: var(--color-text-muted);
    font-variant-numeric: tabular-nums;
}
.toast__count::before { content: '×'; }
```

- [ ] **Step 6: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest=FailureSignallingBrowserTest`
Expected: PASS, 4 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/dm-request.js \
        src/main/resources/static/js/ui-elevation.js \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/FailureSignallingBrowserTest.java
git commit -m "fix: offer retries only where they can work and coalesce repeat failures"
```

---

### Task 18: A module that fails off-screen says so when it is shown

While the Encounter module sat in the module depot, it held the rendered error "Encounter could not refresh. Existing content was kept." with a Retry — invisible, because the module was not on screen. `cockpit-modules.js` raises attention for a stale *invalidation* of a hidden module, but a load *failure* dispatches only `module-state: error` and is lost if nobody is looking. The module's attention badge is also concatenated straight onto the tab label, producing `Encounter1` (K7).

**Files:**
- Modify: `src/main/resources/static/js/cockpit-modules.js:326-352` (a hidden failure raises attention)
- Modify: `src/main/resources/static/js/cockpit-layout.js:622-640` (separate the badge from the tab label)
- Modify: `src/main/resources/static/css/cockpit-layout.css` (badge spacing)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/FailureSignallingBrowserTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleClientContractTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `cockpit:module-state` with `state: 'error'` for a hidden module also records attention; the tab label is wrapped in `.cockpit-zone__tab-label`.

- [ ] **Step 1: Write the failing tests**

Add to `FailureSignallingBrowserTest`:

```java
    @Test
    void aModuleThatFailedWhileHiddenShowsItsErrorWhenMadeVisible() {
        openCockpit();
        page.selectOption("#cockpitPresetPicker", "builtin:exploration");
        page.waitForFunction("() => window.cockpitModules !== undefined");

        // The encounter module is not in the Exploration preset, so it fails unseen.
        page.evaluate("""
                () => {
                  const original = window.dmRequest;
                  window.dmRequest = (url, options) => {
                    if (String(url).includes('/session/modules/encounter')) {
                      return Promise.reject(new window.DmRequestError('boom', 503, null));
                    }
                    return original(url, options);
                  };
                  window.cockpitModules.load('encounter', { force: true });
                }
                """);
        page.waitForFunction(
                "() => document.querySelector('[data-module-key=\"encounter\"]')"
                        + "?.hasAttribute('data-module-attention')");

        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector("[data-runtime-module='encounter'] [data-module-retry]");
        assertThat(page.locator("[data-runtime-module='encounter'] [data-module-retry]").isVisible())
                .as("a failure that happened off-screen must surface when the module is shown")
                .isTrue();
    }

    @Test
    void theAttentionBadgeIsSeparatedFromTheTabLabel() {
        openCockpit();
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector("[data-module-tab]");
        page.evaluate("() => window.cockpitLayout.setAttention('reference', 1)");
        page.waitForSelector("[data-module-tab='reference'] .cockpit-module__attention");

        assertThat(page.locator("[data-module-tab='reference'] .cockpit-zone__tab-label")
                .innerText().trim())
                .as("the count must not run into the module title")
                .isEqualTo("Reference");
    }
```

Add to `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleClientContractTest.java`, inside `moduleControllerExportsExpectedApi`'s `assertThat(js).contains(...)` list:

```java
                "recordHiddenFailure",
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest='FailureSignallingBrowserTest,CockpitModuleClientContractTest'`
Expected: FAIL — no attention attribute appears, and the tab label reads `Reference1`.

- [ ] **Step 3: Record a hidden failure as attention**

In `src/main/resources/static/js/cockpit-modules.js`, add this method to `CockpitModuleController` next to `invalidate`:

```javascript
    /**
     * An error rendered into a module nobody can see is an error nobody will act on. Depot,
     * collapsed and inactive-tab modules raise the attention badge instead, and keep their
     * error body so it is there when the module is shown.
     */
    recordHiddenFailure(moduleKey) {
      const shell = this._shells.get(moduleKey);
      if (!shell || this.isModuleVisible(moduleKey)) return;
      this.stale.add(moduleKey);
      const count = (this.attention.get(moduleKey) || 0) + 1;
      this.attention.set(moduleKey, count);
      shell.setAttribute('data-module-attention', String(count));
      window.dispatchEvent(new CustomEvent('cockpit:module-state', {
        detail: { moduleKey, state: 'attention', count }
      }));
    }
```

In the `.catch(...)` block of `load` (lines 326-352), immediately after the `cockpit:module-state` dispatch:

```javascript
          this.recordHiddenFailure(moduleKey);
```

The error body written into `contentEl` when it was empty already survives, so revealing the module shows the message and its Retry.

- [ ] **Step 4: Separate the badge from the tab label**

In `src/main/resources/static/js/cockpit-layout.js`, replace lines 631-639:

```javascript
          const label = document.createElement('span');
          label.className = 'cockpit-zone__tab-label';
          label.textContent = title;
          tab.appendChild(label);
          const attention = this.attention.get(key) || 0;
          if (attention > 0 && !selected) {
            const badge = document.createElement('span');
            badge.className = 'cockpit-module__attention';
            badge.textContent = String(attention);
            badge.setAttribute('aria-label', `${attention} updates`);
            tab.appendChild(badge);
          }
```

In `src/main/resources/static/css/cockpit-layout.css`, next to the other tab rules:

```css
.cockpit-zone__tab { display: inline-flex; align-items: center; gap: var(--space-xs); }

.cockpit-module__attention {
  min-width: 1.25rem;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--color-warning);
  color: var(--color-bg);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  text-align: center;
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='FailureSignallingBrowserTest,CockpitModuleClientContractTest,CockpitModuleInitialLoadBrowserTest'`
Expected: PASS, 0 failures.

- [ ] **Step 6: Run the phase suite**

Run: `./mvnw -o test -Dtest='Cockpit*Test,RuntimeStatus*Test,Failure*Test,Dice*Test'`
Expected: PASS, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/cockpit-modules.js \
        src/main/resources/static/js/cockpit-layout.js \
        src/main/resources/static/css/cockpit-layout.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/FailureSignallingBrowserTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleClientContractTest.java
git commit -m "fix: surface module failures that happened off-screen"
```

---

## Phase G — Making the encounter and map surfaces discoverable

### Task 19: Running an encounter produces a visible result

`builtin:exploration` contains `story, session-plan, party, quick-notes, audio, session-log` — neither `map` nor `encounter`. Clicking a scene's "Run this encounter" in that preset produced a pixel-identical screen. The encounter *had* started; the DM only found out by switching presets. The Start Session dialog's map choice has the same problem: no map surface exists in that preset.

Product decision D-4: reveal the affected module into the current layout, and if it has no home there, say what happened and offer the switch.

**Files:**
- Modify: `src/main/resources/static/js/session-cockpit.js` (`applyActivation` reveals the encounter surface)
- Modify: `src/main/resources/static/js/cockpit-layout.js:1600-1616` (`revealModule` reports honestly when the module is not in the preset)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/EncounterDiscoverabilityBrowserTest.java`

**Interfaces:**
- Consumes: `applyActivation` (Task 2), `cockpitLayout#revealModule` and `#showNotice` (existing).
- Produces: `sessionCockpit#surfaceEncounter()` — reveals `encounter`, and when that is impossible offers a preset switch through `#cockpitLayoutNotice`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/EncounterDiscoverabilityBrowserTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncounterDiscoverabilityBrowserTest {

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
        failures.clear();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1366, 768));
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    @Test
    void runningAnEncounterFromExplorationSurfacesTheInitiativeOrder() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:exploration");
        page.waitForFunction("() => window.cockpitLayout.current?.name === 'Exploration'");
        assertThat(page.locator(".combatant-row").count()).isZero();

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterA().toString());

        // One interaction: either the module was revealed, or the notice offers the switch
        // and taking it is that one interaction.
        page.waitForSelector("#cockpitLayoutNotice button, .combatant-row");
        if (page.locator("#cockpitLayoutNotice button").count() > 0) {
            page.locator("#cockpitLayoutNotice button").first().click();
        }
        page.waitForSelector(".combatant-row");
        assertThat(page.locator(".tracker-list").isVisible()).isTrue();
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=EncounterDiscoverabilityBrowserTest`
Expected: FAIL. Nothing appears; the wait times out.

- [ ] **Step 3: Let `revealModule` admit when it cannot**

In `src/main/resources/static/js/cockpit-layout.js`, replace `revealModule` (lines 1600-1616):

```javascript
    /**
     * Reveals a module in the current layout. Returns false when the active preset has no
     * place for it, so the caller can offer the DM the switch rather than changing the
     * layout out from under them.
     */
    revealModule(key) {
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      const zoneEl = shell?.closest('[data-cockpit-zone]');
      if (!zoneEl) return false;
      const zone = zoneEl.getAttribute('data-cockpit-zone');
      const zoneLayout = this.current?.zones?.[zone];
      if (zoneLayout?.collapsed) {
        if (zone === 'BOTTOM_UTILITY') {
          this.toggleBottomUtility();
        } else {
          zoneLayout.collapsed = false;
          this.renderLayout();
        }
      }
      this.selectTab(zone, key);
      return this.isModuleVisible(key);
    }

    /** A notice with one action. Used when a state change happened somewhere the DM is not. */
    offerPresetSwitch(message, presetKey, actionLabel) {
      if (!this.notice) return;
      this.notice.replaceChildren();
      const text = document.createElement('span');
      text.textContent = message;
      this.notice.appendChild(text);
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'btn btn-ghost btn-xs';
      button.textContent = actionLabel;
      button.addEventListener('click', () => {
        this.clearNotice();
        this.applyPreset(presetKey);
      });
      this.notice.appendChild(button);
    }
```

- [ ] **Step 4: Surface the encounter after activation**

In `src/main/resources/static/js/session-cockpit.js`, add to `applyActivation`, as its last statement:

```javascript
            this.surfaceEncounter();
```

and add the method next to it:

```javascript
        // An action that changes runtime state must produce a visible result on the screen
        // where it was invoked. In Exploration the tracker has no home, so say what happened
        // and offer the one interaction that shows it.
        surfaceEncounter() {
            const layout = window.cockpitLayout;
            if (!layout) return;
            if (layout.revealModule('encounter')) return;
            layout.offerPresetSwitch(
                'The encounter is running. The initiative order lives in the Combat layout.',
                'builtin:combat',
                'Switch to Combat');
        },
```

- [ ] **Step 5: Run the test and watch it pass**

Run: `./mvnw -o test -Dtest=EncounterDiscoverabilityBrowserTest`
Expected: PASS, 1 test.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/js/session-cockpit.js \
        src/main/resources/static/js/cockpit-layout.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/EncounterDiscoverabilityBrowserTest.java
git commit -m "feat: surface the initiative order when an encounter starts"
```

---

### Task 20: The scene action row reads as controls, and re-running a finished encounter says so

Two findings in one surface. The same slot reads "Start encounter from this scene" when the scene has no prepared encounter and "Run this encounter" when it does, and beside them "Open" and "Map" render as low-affordance plain text (`.btn-ghost` is transparent with muted text and only reveals a border on hover). And clicking "Run this encounter" on a scene whose encounter has already been fought reopens it at Round 0 with all monsters still dead and initiative preserved — no warning, no offer to reset.

**Files:**
- Modify: `src/main/resources/templates/session/_story-rail.html:90-116` (labels and affordance)
- Modify: `src/main/resources/static/js/session-cockpit.js:573-596` (`runEncounter` checks for a finished encounter)
- Modify: `src/main/resources/templates/session/cockpit.html` (add the resume/reset dialog)
- Modify: `src/main/resources/static/css/components.css` (`.scene-actions .btn` resting affordance)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/EncounterDiscoverabilityBrowserTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`

**Interfaces:**
- Consumes: `applyActivation` (Task 2), `EncounterService` status (`DONE`).
- Produces: `sessionCockpit#showFinishedEncounterDialog(encounterId, name)`, `#confirmResumeFinished()`, `#confirmResetFinished()`; `#encounterFinishedDialog` in the cockpit template.

- [ ] **Step 1: Write the failing tests**

Add to `SessionCockpitTemplateContractTest`:

```java
    @Test
    void theSceneActionRowDistinguishesCreatingFromRunning() throws IOException {
        String story = Files.readString(Path.of("src/main/resources/templates/session/_story-rail.html"));

        assertThat(story)
                .as("creating an ad-hoc encounter and running a prepared one are different acts")
                .contains("Create an encounter here")
                .contains("Run the prepared encounter");
        assertThat(story)
                .as("every control in the row carries the same affordance level")
                .contains("class=\"btn scene-actions__control\"");
    }
```

Add to `EncounterDiscoverabilityBrowserTest`:

```java
    @Test
    void reRunningAFinishedEncounterTellsTheDmItIsResuming() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => !!window.battleMap");

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterA().toString());
        page.waitForSelector(".combatant-row");
        page.evaluate("(id) => window.dmRequest('/api/v1/encounters/' + id + '/end',"
                + " { method: 'POST' })", seeded.encounterA().toString());
        page.waitForFunction("() => document.querySelectorAll('.combatant-row').length === 0");

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterA().toString());
        page.waitForSelector("#encounterFinishedDialog[open]");

        assertThat(page.locator("#encounterFinishedDialog").innerText())
                .containsIgnoringCase("already been fought");
        assertThat(page.locator("#encounterFinishedDialog [data-resume-finished]").count()).isEqualTo(1);
        assertThat(page.locator("#encounterFinishedDialog [data-reset-finished]").count()).isEqualTo(1);
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./mvnw -o test -Dtest='SessionCockpitTemplateContractTest,EncounterDiscoverabilityBrowserTest'`
Expected: FAIL — the labels are the old ones and no dialog exists.

- [ ] **Step 3: Rewrite the scene action row**

In `src/main/resources/templates/session/_story-rail.html`, replace lines 90-116:

```html
      <div class="scene-actions">
        <a class="btn scene-actions__control" target="_blank"
           th:if="${view.adventureId != null}"
           th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}(cid=${campaignId},aid=${view.adventureId},sid=${view.sceneId})}"
           title="Open the full scene page">Open scene</a>
        <button class="btn scene-actions__control"
                th:if="${view.mapId != null}"
                th:attr="data-map-id=${view.mapId}"
                @click="switchMap($el.dataset.mapId)"
                title="Show this scene's map">Show map</button>
        <!-- Two different acts, two different labels: one makes an encounter, the other runs
             the one the campaign already prepared. -->
        <button class="btn scene-actions__control" data-seed-scene-encounter
                th:if="${view.canSeedEncounter}"
                th:attr="data-scene-id=${view.sceneId}"
                :disabled="seedingSceneEncounter"
                :aria-busy="seedingSceneEncounter"
                @click="seedCurrentScene($el.dataset.sceneId)">
          <span x-text="seedingSceneEncounter ? 'Creating encounter…' : 'Create an encounter here'">
            Create an encounter here
          </span>
        </button>
        <button class="btn btn-primary scene-actions__control"
                th:if="${view.linkedEncounterId != null}"
                th:attr="data-encounter-id=${view.linkedEncounterId}"
                @click="runEncounter($el.dataset.encounterId)">
          Run the prepared encounter
        </button>
      </div>
```

In `src/main/resources/static/css/components.css`:

```css
/* The scene action row is where a session actually starts. Every control in it looks like
   one at rest, not only under the pointer. */
.scene-actions { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.scene-actions__control {
    border: 1px solid var(--color-border);
    color: var(--color-text);
}
.scene-actions__control:hover { border-color: var(--color-border-strong); }
```

- [ ] **Step 4: Add the resume/reset dialog**

In `src/main/resources/templates/session/cockpit.html`, next to the other cockpit dialogs (search for `encounterReplacementDialog` and add a sibling):

```html
    <dialog id="encounterFinishedDialog" class="cockpit-dialog"
            aria-labelledby="encounterFinishedTitle"
            @cancel.prevent="closeFinishedEncounterDialog()">
      <h2 id="encounterFinishedTitle">This encounter has already been fought</h2>
      <p>
        <strong x-text="finishedEncounterName || 'It'"></strong> ended earlier in this campaign.
        Resuming reopens it exactly as it was left — defeated creatures stay defeated and the
        initiative order is preserved. Resetting restores every combatant to full hit points and
        clears the initiative order.
      </p>
      <div class="cockpit-dialog__actions">
        <button type="button" class="btn btn-primary" data-resume-finished
                @click="confirmResumeFinished()">Resume as it was</button>
        <button type="button" class="btn btn-danger action-row__destructive" data-reset-finished
                @click="confirmResetFinished()">Reset and run again</button>
        <button type="button" class="btn btn-ghost"
                @click="closeFinishedEncounterDialog()">Cancel</button>
      </div>
    </dialog>
```

- [ ] **Step 5: Check the status before activating**

In `src/main/resources/static/js/session-cockpit.js`, add `finishedEncounterName: ''` to the component state, then replace `runEncounter` (lines 573-596):

```javascript
        async runEncounter(encounterId) {
            try {
                const encounterResponse = await this.request(`/api/v1/encounters/${encounterId}`);
                const encounter = await encounterResponse.json();
                // Reopening a finished fight silently is how a DM ends up running a room of
                // corpses. Say what resuming means and offer the alternative.
                if (encounter.status === 'DONE') {
                    this.showFinishedEncounterDialog(encounterId, encounter.name);
                    return;
                }
                const readinessResponse = await this.request(`/api/v1/encounters/${encounterId}/readiness`);
                const readiness = await readinessResponse.json();
                // Only ERROR-severity issues block a run, and the server already folds those
                // into canRun. Stopping for warnings stranded the DM in a dialog for things
                // that are not problems: UNPLACED_COMBATANTS is warned about here and then
                // fixed by the auto-placement that activation itself performs.
                if (readiness.canRun === false) {
                    this.showReadinessDialog(readiness, encounterId);
                    return;
                }
                await this.activateEncounter(encounterId, null);
            } catch (e) {
                const problem = e.problem || {};
                if (problem.code === 'ACTIVE_ENCOUNTER_REPLACEMENT_REQUIRED') {
                    this.showReplacementDialog(problem.activeEncounterId, problem.activeEncounterName, encounterId);
                } else if (problem.code === 'ENCOUNTER_NOT_READY') {
                    this.showReadinessDialog(problem.readiness, encounterId);
                } else {
                    throw e;
                }
            }
        },

        showFinishedEncounterDialog(encounterId, name) {
            this._finishedEncounterId = encounterId;
            this.finishedEncounterName = name || '';
            const dialog = document.getElementById('encounterFinishedDialog');
            if (dialog && !dialog.open) dialog.showModal();
        },

        closeFinishedEncounterDialog() {
            const dialog = document.getElementById('encounterFinishedDialog');
            if (dialog?.open) dialog.close();
            this._finishedEncounterId = null;
            this.finishedEncounterName = '';
        },

        async confirmResumeFinished() {
            const encounterId = this._finishedEncounterId;
            this.closeFinishedEncounterDialog();
            if (encounterId) await this._activateOrNotify(encounterId, null);
        },

        async confirmResetFinished() {
            const encounterId = this._finishedEncounterId;
            this.closeFinishedEncounterDialog();
            if (!encounterId) return;
            try {
                await this.request(`/api/v1/encounters/${encounterId}/reset`, { method: 'POST' });
            } catch (error) {
                window.reportActionFailure(
                    'The encounter could not be reset. Nothing was changed.', error,
                    () => this.confirmResetFinished());
                return;
            }
            await this._activateOrNotify(encounterId, null);
        },
```

- [ ] **Step 6: Provide the reset endpoint**

`EncounterApiController` has only `/reset-legendary`; there is no whole-encounter reset.
Add to `EncounterApiController`:

```java
    /** Restores a finished encounter to a runnable state: full HP, no defeats, no initiative. */
    @PostMapping("/encounters/{id}/reset")
    public EncounterService.EncounterDto reset(@PathVariable UUID id) {
        return service.resetEncounter(id);
    }
```

Add to `EncounterService`:

```java
    @Transactional
    public EncounterDto resetEncounter(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        for (Combatant c : combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId)) {
            c.setCurrentHp(c.getMaxHp());
            c.setTempHp(0);
            c.setDefeated(false);
            c.setInitiative(null);
            c.setConditionsJson("[]");
            c.setConcentratingOn(null);
            c.setConcentrationCheckPending(false);
            combatantRepo.save(c);
        }
        e.setStatus(Encounter.Status.PLANNED);
        e.setCombatPhase(Encounter.CombatPhase.SETUP);
        e.setRound(0);
        e.setActiveTurnIndex(-1);
        return toDto(encounterRepo.save(e));
    }
```

Add to `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterCompletionTest.java`:

```java
    @Test
    void resetRestoresAFinishedEncounterWithoutChangingItsRoster() {
        var enc = service.create(campaign.getId(), new CreateRequest("Reset me", null));
        service.activate(enc.id());
        var goblin = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 10, "MONSTER", null, null));
        service.setInitiative(goblin.id(), 17);
        service.applyDamage(goblin.id(), -10);
        service.endEncounter(enc.id());

        EncounterDto reset = service.resetEncounter(enc.id());

        assertThat(reset.status()).isEqualTo("PLANNED");
        assertThat(reset.combatPhase()).isEqualTo("SETUP");
        assertThat(reset.round()).isZero();
        assertThat(reset.combatants()).singleElement().satisfies(c -> {
            assertThat(c.currentHp()).isEqualTo(10);
            assertThat(c.defeated()).isFalse();
            assertThat(c.initiative()).isNull();
            assertThat(c.conditions()).isEmpty();
        });
    }
```

- [ ] **Step 7: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='SessionCockpitTemplateContractTest,EncounterDiscoverabilityBrowserTest,StoryRailSceneBodyContractTest'`
Expected: PASS, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/session/_story-rail.html \
        src/main/resources/templates/session/cockpit.html \
        src/main/resources/static/js/session-cockpit.js \
        src/main/resources/static/css/components.css \
        src/main/java/dev/hendrikhoemberg/dmhelper/encounter \
        src/test/java/dev/hendrikhoemberg/dmhelper/session \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter
git commit -m "feat: name the scene actions and announce a resumed encounter"
```

---

## Phase H — Combat close-out

### Task 21: Ending an encounter reports the XP

Ending the goblin ambush produced a "FINISHED THIS SESSION" rail row with a Reopen action and nothing else. No XP total (the ambush is 200 XP), no rewards prompt, no record that the fight happened. The input data exists — encounter payloads carry `xp` per statblock — and so does most of the machinery: `EncounterService.buildSummary`, `/encounters/{id}/end-with-summary`, `/encounters/{id}/rewards/apply`, and `encounter/_summary-modal.html`. Two things are missing. `buildSummary` fills `rewardsDraft` from `getRewards(encounterId)` — the *authored* rewards JSON, empty for every imported encounter — so `xpTotal` is null. And the cockpit tracker's `endEncounter()` calls plain `/end`, so the summary modal is only reachable from the standalone encounter page.

Product decision D-5: report the total and the per-PC share, and offer to apply it.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterXpCalculator.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java:552-704` (`buildSummary`, `applyRewards`)
- Modify: `src/main/resources/static/js/combat-tracker.js:623-638` (`endEncounter`)
- Modify: `src/main/resources/templates/encounter/_summary-modal.html` (make the encounter id/name runtime data)
- Modify: `src/main/resources/templates/encounter/setup.html`, `src/main/resources/templates/session/cockpit.html` (include the runtime-driven modal)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterXpCalculatorTest.java`, `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterCompletionTest.java`

**Interfaces:**
- Consumes: `CockpitInitialLoadFixtures#campaignWithGroupedEncounter` (Task 1) — its goblins carry `xp = 50`.
- Produces: `EncounterXpCalculator.xpFromDefeated(List<Combatant>)` and `.xpPerPc(int, int)` as declared in *Stable interfaces*. `resolvedRewards(Encounter, List<Combatant>)` is the single package-private source used by both summary and application, so an offered derived XP award cannot become a no-op.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterXpCalculatorTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The encounter already knows what each creature is worth. Concluding it must say so. */
class EncounterXpCalculatorTest {

    @Test
    void defeatedMonstersContributeTheirStatblockXp() {
        List<Combatant> roster = List.of(
                monster("Goblin 1", 50, true),
                monster("Goblin 2", 50, true),
                monster("Goblin 3", 50, true),
                monster("Goblin 4", 50, true));

        assertThat(EncounterXpCalculator.xpFromDefeated(roster)).isEqualTo(200);
    }

    @Test
    void survivorsAndPartyMembersContributeNothing() {
        List<Combatant> roster = List.of(
                monster("Goblin 1", 50, true),
                monster("Goblin 2", 50, false),
                pc("Sildar", true));

        assertThat(EncounterXpCalculator.xpFromDefeated(roster)).isEqualTo(50);
    }

    @Test
    void aCombatantWithoutAStatblockIsWorthNothingRatherThanBreakingTheSummary() {
        Combatant nameless = new Combatant();
        nameless.setName("Improvised thug");
        nameless.setKind("MONSTER");
        nameless.setDefeated(true);

        assertThat(EncounterXpCalculator.xpFromDefeated(List.of(nameless))).isZero();
    }

    @Test
    void thePerPcShareFloorsAndSurvivesAnEmptyParty() {
        assertThat(EncounterXpCalculator.xpPerPc(200, 4)).isEqualTo(50);
        assertThat(EncounterXpCalculator.xpPerPc(200, 3)).isEqualTo(66);
        assertThat(EncounterXpCalculator.xpPerPc(200, 0)).isEqualTo(200);
    }

    private static Combatant monster(String name, int xp, boolean defeated) {
        StatBlock sb = new StatBlock();
        sb.setName(name);
        sb.setXp(xp);
        Combatant c = new Combatant();
        c.setName(name);
        c.setKind("MONSTER");
        c.setStatBlock(sb);
        c.setDefeated(defeated);
        return c;
    }

    private static Combatant pc(String name, boolean defeated) {
        StatBlock sb = new StatBlock();
        sb.setXp(500);
        Combatant c = new Combatant();
        c.setName(name);
        c.setKind("PC");
        c.setStatBlock(sb);
        c.setDefeated(defeated);
        return c;
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=EncounterXpCalculatorTest`
Expected: FAIL — `EncounterXpCalculator` does not exist.

- [ ] **Step 3: Write the calculator**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterXpCalculator.java`:

```java
package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;

import java.util.List;

/**
 * XP for a concluded encounter, derived from what was actually defeated. Only used when the
 * encounter does not declare its own rewards — an authored xpTotal always wins.
 */
public final class EncounterXpCalculator {

    private EncounterXpCalculator() {}

    public static int xpFromDefeated(List<Combatant> combatants) {
        if (combatants == null) return 0;
        return combatants.stream()
                .filter(Combatant::isDefeated)
                .filter(c -> !"PC".equals(c.getKind()))
                .filter(c -> c.getStatBlock() != null)
                .mapToInt(c -> Math.max(0, c.getStatBlock().getXp()))
                .sum();
    }

    /** Floor division, as the table does it. An empty party gets the whole pool reported. */
    public static int xpPerPc(int total, int pcCount) {
        return pcCount <= 0 ? total : total / pcCount;
    }
}
```

- [ ] **Step 4: Make summary and application resolve the same rewards**

In `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`,
add this method next to `buildSummary`:

```java
    EncounterRewards resolvedRewards(Encounter encounter, List<Combatant> all) {
        // Imported encounters often declare no rewards, but defeated statblocks carry XP.
        // Authored values win; derived values fill only missing XP fields.
        EncounterRewards authored = getRewards(encounter.getId());
        int derivedXp = EncounterXpCalculator.xpFromDefeated(all);
        int pcCount = (int) all.stream().filter(c -> "PC".equals(c.getKind())).count();
        Integer total = authored.xpTotal() != null && authored.xpTotal() > 0
                ? authored.xpTotal()
                : (derivedXp > 0 ? derivedXp : null);
        Integer perPc = authored.xpPerPc() != null && authored.xpPerPc() > 0
                ? authored.xpPerPc()
                : (total != null ? EncounterXpCalculator.xpPerPc(total, pcCount) : null);
        return new EncounterRewards(total, perPc, authored.currency(), authored.items(),
                authored.questObjectiveRefs(), authored.notes());
    }
```

Pass `resolvedRewards(e, all)` in place of `getRewards(encounterId)` in `buildSummary`.
At the start of `applyRewards`, replace:

```java
        EncounterRewards rewards = getRewards(encounterId);
        if (rewards == null) {
            rewards = EncounterRewards.empty();
        }
```

with:

```java
        List<Combatant> roster =
                combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        EncounterRewards rewards = resolvedRewards(e, roster);
```

Add to `EncounterCompletionTest` after `applyRewardsAwardsXpToActivePartyAndIsIdempotent`:

```java
    @Test
    void derivedXpShownInTheSummaryIsTheXpThatConfirmRewardsApplies() {
        var hero = new dev.hendrikhoemberg.dmhelper.party.data.PartyMember();
        hero.setCampaign(campaign);
        hero.setCharacterName("Fighter");
        hero.setAc(16);
        hero.setMaxHp(20);
        hero.setCurrentHp(20);
        hero.setSpeed(30);
        hero.setPassivePerception(12);
        hero.setPassiveInsight(10);
        hero.setPassiveInvestigation(10);
        hero.setActive(true);
        em.persist(hero);

        var block = new dev.hendrikhoemberg.dmhelper.library.data.StatBlock();
        block.setSource(dev.hendrikhoemberg.dmhelper.library.data.ContentSource.CUSTOM);
        block.setCampaign(campaign);
        block.setName("Goblin");
        block.setCr("1/4");
        block.setType("Humanoid");
        block.setHp("7");
        block.setXp(50);
        em.persist(block);
        em.flush();

        var enc = service.create(campaign.getId(), new CreateRequest("Derived rewards", null));
        var goblin = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 7, "MONSTER", block.getId(), null));
        service.activate(enc.id());
        service.applyDamage(goblin.id(), -7);

        var ended = service.endEncounterWithSummary(enc.id());
        assertThat(ended.summary().rewardsDraft().xpTotal()).isEqualTo(50);

        service.applyRewards(enc.id(),
                new EncounterService.ApplyRewardsRequest(
                        true, List.of(hero.getId()), false, false, false));
        em.flush();
        em.clear();
        assertThat(em.find(dev.hendrikhoemberg.dmhelper.party.data.PartyMember.class, hero.getId())
                .getXp()).isEqualTo(50);
    }
```

- [ ] **Step 5: End through the summary in the cockpit**

The existing modal requires a server-rendered `encounter` object and keeps its initial id even
when `show(id)` is called. Make it runtime-driven. In
`src/main/resources/templates/encounter/_summary-modal.html`, replace the fragment root:

```html
<div th:fragment="summary-modal(campaignId)"
     x-data="endEncounterModal()"
     @end-encounter.window="show($event.detail?.id)">
```

Replace the encounter-name paragraph with:

```html
                    <p><strong x-text="summary.name || 'Encounter'">Encounter</strong></p>
```

Change `Alpine.data('endEncounterModal', (encounterId) => ({` to
`Alpine.data('endEncounterModal', () => ({`, initialize `encounterId: null`, and make the first
lines of `show`:

```javascript
            async show(id) {
                if (!id) return;
                this.encounterId = id;
                try {
                    const resp = await window.dmRequest(
```

The remainder of `show` keeps using `id` in the URL. Change the include in
`src/main/resources/templates/encounter/setup.html` to:

```html
<div th:replace="~{encounter/_summary-modal :: summary-modal(${campaignId})}"></div>
```

and include the same fragment once in `src/main/resources/templates/session/cockpit.html`
next to the encounter dialogs:

```html
<div th:replace="~{encounter/_summary-modal :: summary-modal(${campaignId})}"></div>
```

In `src/main/resources/static/js/combat-tracker.js`, replace `endEncounter` (lines 623-638):

```javascript
            async endEncounter() {
                if (!this._encounterId) return;
                // The summary modal owns ending from here: it calls end-with-summary, shows the
                // XP and rewards, and dispatches cockpit-encounter-ended when the DM is done.
                window.dispatchEvent(new CustomEvent('end-encounter', {
                    detail: { id: this._encounterId }
                }));
            },

            _clearAfterEncounterEnd() {
                this.encounter = null;
                this.combatants = [];
                this.activeCombatantId = null;
                this.selectedCombatantId = null;
                this._encounterId = null;
                this.dispatchState();
            },
```

and register the listener in `init()`, next to the `battle-tokenselect` listener:

```javascript
                window.addEventListener('cockpit-encounter-ended', () => this._clearAfterEncounterEnd());
```

- [ ] **Step 6: Add the browser assertion**

Add to `EncounterDiscoverabilityBrowserTest`:

```java
    @Test
    void endingAnEncounterReportsTheXpItWasWorth() {
        var seeded = fixtures.campaignWithGroupedEncounter();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector(".combatant-row");

        page.evaluate("""
                async (ids) => {
                  for (const id of ids) {
                    await window.dmRequest('/api/v1/combatants/' + id + '/defeated', {
                      method: 'PUT', headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ defeated: true }),
                    });
                  }
                }
                """, seeded.memberIds().stream().map(java.util.UUID::toString).toList());

        page.click("[data-end-encounter]");
        page.click("[data-encounter-end-dialog] .btn-danger");
        page.waitForSelector(".summary-stats");

        assertThat(page.locator(".summary-stats").innerText())
                .as("four 50 XP goblins are 200 XP; concluding must say so")
                .contains("200");
    }
```

`cockpit.html` defines the confirmation as
`[data-encounter-end-dialog] .btn-danger`; keep that selector.

- [ ] **Step 7: Run the tests and watch them pass**

Run: `./mvnw -o test -Dtest='EncounterXpCalculatorTest,EncounterDiscoverabilityBrowserTest'`
Expected: PASS, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterXpCalculator.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java \
        src/main/resources/static/js/combat-tracker.js \
        src/main/resources/templates/encounter/_summary-modal.html \
        src/main/resources/templates/encounter/setup.html \
        src/main/resources/templates/session/cockpit.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterXpCalculatorTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterCompletionTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/EncounterDiscoverabilityBrowserTest.java
git commit -m "feat: report the XP an encounter was worth when it ends"
```

---

### Task 22: The session log draft states the XP the session earned

`SessionDraftService` does emit an `## Encounters` section, but the run's draft contained no encounter line at all and no XP anywhere. The draft is the DM's record of the session; a session whose main event was a fight must not produce a log that omits it.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java:178-232` (`encounterLines`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterEvidenceIntegrationTest.java`

**Interfaces:**
- Consumes: `EncounterXpCalculator` (Task 21), `preReviewStatus`-preserving `beginReview` (Task 14).
- Produces: encounter draft lines end with `"; XP: <n>"` when the fight earned any.

- [ ] **Step 1: Write the failing test**

In `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterEvidenceIntegrationTest.java`,
add these injected repositories:

```java
    @Autowired private dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository combatants;
    @Autowired private dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository statBlocks;
```

Then add this test. It uses the class's existing endpoint helpers but keeps the defeated
combatant in the roster so the XP calculator has authoritative statblock data:

```java
    @Test
    void theDraftNamesTheEncounterItsOutcomeAndTheXpEarned() throws Exception {
        var block = new dev.hendrikhoemberg.dmhelper.library.data.StatBlock();
        block.setSource(dev.hendrikhoemberg.dmhelper.library.data.ContentSource.CUSTOM);
        block.setCampaign(campaignRepo.findById(campaignId).orElseThrow());
        block.setName("Goblin");
        block.setCr("1/4");
        block.setType("Humanoid");
        block.setHp("7");
        block.setXp(50);
        block = statBlocks.save(block);

        UUID encounterId = createEncounter("Grouped Fixture");
        UUID combatantId = addCombatant(encounterId, "Goblin 1", 7);
        var combatant = combatants.findById(combatantId).orElseThrow();
        combatant.setStatBlock(block);
        combatants.save(combatant);
        activateEncounter(encounterId);
        applyDamage(combatantId, -7);
        endEncounter(encounterId);

        lifecycle.beginReview(campaignId);
        String draft = sessionRepo.findByCampaignId(campaignId).orElseThrow().getDraftBody();

        assertThat(draft).contains("## Encounters");
        assertThat(draft).containsPattern("Grouped Fixture .*(round|completed)");
        assertThat(draft).contains("defeated: Goblin 1");
        assertThat(draft).contains("XP: 50");
    }
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./mvnw -o test -Dtest=SessionEncounterEvidenceIntegrationTest`
Expected: FAIL — no `XP:` appears in the draft.

- [ ] **Step 3: Add the XP to the encounter lines**

In `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java`, add `import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterXpCalculator;`, then inside `encounterLines`, after the `damage` calculation and before the `if (names.isEmpty() && damage == 0)` branch:

```java
            // The draft is the DM's record of the session. A combat-heavy session that reports
            // no XP has left the most actionable number of the night on the floor.
            int xp = EncounterXpCalculator.xpFromDefeated(
                    combatants.findByEncounterIdOrderBySortOrderAsc(encounter.getId()));
```

Replace the empty-evidence branch and the line assembly that follow it:

```java
            if (names.isEmpty() && damage == 0 && xp == 0) {
                lines.add(encounter.getName() + " — completed; no defeat or damage evidence recorded");
                continue;
            }
            StringBuilder line = new StringBuilder(encounter.getName()).append(" — ");
            if (rounds > 0) line.append(rounds).append(rounds == 1 ? " round" : " rounds");
            else line.append("completed");
            if (!names.isEmpty()) line.append("; defeated: ").append(String.join(", ", names));
            if (damage > 0) line.append("; damage recorded: ").append(damage);
            if (xp > 0) line.append("; XP: ").append(xp);
            lines.add(line.toString());
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./mvnw -o test -Dtest='SessionEncounterEvidenceIntegrationTest,SessionDraftServiceTest'`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterEvidenceIntegrationTest.java
git commit -m "feat: state each encounter's outcome and XP in the session draft"
```

---

## Phase I — Encounter rail information architecture

### Task 23: The rail orders naturally and foregrounds the current scene

"Planned encounters" lists all 49 encounters in the campaign as one flat, lexicographically sorted list: `Burg Cragmaw 12, Burg Cragmaw 13, Burg Cragmaw 14, Burg Cragmaw 3, Burg Cragmaw 4, Burg Cragmaw 6`. The numeric ordering reads as broken, and nothing indicates which encounters belong to the scene the DM is in. The filter box stays. The finished row's meta line also wraps across two ragged lines at rail width (I2), which the same grid change fixes.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java:276-328` (ordering and scene grouping)
- Modify: `src/main/resources/templates/session/_encounter-rail.html` (a "This scene" section, one-line meta)
- Modify: `src/main/resources/static/css/cockpit-modules.css` (meta line)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/EncounterRailOrderingTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/EncounterDiscoverabilityBrowserTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `CockpitRuntimeModuleViewService.NaturalOrder.compare(String, String)` (package-visible static, tested directly); `PlannedEncounterView` gains a trailing `boolean forCurrentScene`; `EncounterView` gains `List<PlannedEncounterView> currentScenePlanned` before `planned`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/EncounterRailOrderingTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.encounter;

import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** "Burg Cragmaw 3" comes before "Burg Cragmaw 12". Lexicographic order reads as broken. */
class EncounterRailOrderingTest {

    @Test
    void numericSuffixesSortNaturally() {
        List<String> names = new java.util.ArrayList<>(List.of(
                "Burg Cragmaw 12", "Burg Cragmaw 3", "Burg Cragmaw 14",
                "Burg Cragmaw 4", "Burg Cragmaw 13", "Burg Cragmaw 6"));
        names.sort(CockpitRuntimeModuleViewService.NaturalOrder::compare);

        assertThat(names).containsExactly(
                "Burg Cragmaw 3", "Burg Cragmaw 4", "Burg Cragmaw 6",
                "Burg Cragmaw 12", "Burg Cragmaw 13", "Burg Cragmaw 14");
    }

    @Test
    void namesWithoutNumbersKeepAlphabeticalOrder() {
        List<String> names = new java.util.ArrayList<>(List.of(
                "Zeltlager", "Alter Eulenbrunnen", "Mondbrunnen"));
        names.sort(CockpitRuntimeModuleViewService.NaturalOrder::compare);

        assertThat(names).containsExactly("Alter Eulenbrunnen", "Mondbrunnen", "Zeltlager");
    }

    @Test
    void embeddedNumbersSortNaturallyToo() {
        List<String> names = new java.util.ArrayList<>(List.of(
                "Kapitel 10 — Rückweg", "Kapitel 2 — Anfang", "Kapitel 2 — Zweiter Teil"));
        names.sort(CockpitRuntimeModuleViewService.NaturalOrder::compare);

        assertThat(names).containsExactly(
                "Kapitel 2 — Anfang", "Kapitel 2 — Zweiter Teil", "Kapitel 10 — Rückweg");
    }
}
```

- [ ] **Step 2: Write the failing current-scene browser test**

In `EncounterDiscoverabilityBrowserTest`, inject:

```java
    @Autowired private dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture releaseFixtures;
    @Autowired private dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService lifecycle;
    @Autowired private dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService adventures;
    @Autowired private dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService sceneSeeder;
    @Autowired private dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService encounterService;
```

and add:

```java
    @Test
    void theCurrentScenesEncounterAppearsBeforeTheUnrelatedList() throws Exception {
        var seeded = releaseFixtures.seedForRehearsal(
                dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP);
        lifecycle.start(seeded.campaignId(), seeded.playableMapId());
        adventures.setCurrentScene(seeded.campaignId(), seeded.hostileSceneId());
        var linked = sceneSeeder.seedFromScene(seeded.campaignId(), seeded.hostileSceneId());
        encounterService.create(seeded.campaignId(),
                new dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest(
                        "Unrelated cellar", seeded.playableMapId()));

        page.navigate("http://127.0.0.1:" + port + "/campaigns/"
                + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector("[data-runtime-module='encounter'] h3:text('This scene')");

        var current = page.locator("[data-runtime-module='encounter'] .sidebar-section")
                .filter(new com.microsoft.playwright.Locator.FilterOptions()
                        .setHasText("This scene"));
        var all = page.locator("[data-runtime-module='encounter'] details")
                .filter(new com.microsoft.playwright.Locator.FilterOptions()
                        .setHasText("All planned encounters"));

        assertThat(current.innerText()).contains(linked.encounterName());
        assertThat(all.innerText()).contains("Unrelated cellar");
        assertThat(current.boundingBox().y).isLessThan(all.boundingBox().y);
    }
```

- [ ] **Step 3: Run the tests and watch them fail**

Run:
`./mvnw -o test -Dtest='EncounterRailOrderingTest,EncounterDiscoverabilityBrowserTest#theCurrentScenesEncounterAppearsBeforeTheUnrelatedList'`

Expected: FAIL — `NaturalOrder` does not exist and there is no `This scene` section.

- [ ] **Step 4: Add the comparator**

In `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java`, add as a nested class:

```java
    /**
     * Digit runs compare as numbers, everything else as text. "Burg Cragmaw 3" before
     * "Burg Cragmaw 12" — the ordering a DM expects from a numbered room list.
     */
    public static final class NaturalOrder {
        private NaturalOrder() {}

        public static int compare(String left, String right) {
            String a = left == null ? "" : left;
            String b = right == null ? "" : right;
            int i = 0;
            int j = 0;
            while (i < a.length() && j < b.length()) {
                char ca = a.charAt(i);
                char cb = b.charAt(j);
                if (Character.isDigit(ca) && Character.isDigit(cb)) {
                    int startA = i;
                    int startB = j;
                    while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                    while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                    String numA = a.substring(startA, i).replaceFirst("^0+(?=.)", "");
                    String numB = b.substring(startB, j).replaceFirst("^0+(?=.)", "");
                    if (numA.length() != numB.length()) return numA.length() - numB.length();
                    int digits = numA.compareTo(numB);
                    if (digits != 0) return digits;
                    continue;
                }
                int letters = Character.compare(
                        Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (letters != 0) return letters;
                i++;
                j++;
            }
            return (a.length() - i) - (b.length() - j);
        }
    }
```

- [ ] **Step 5: Order and group the rail**

In the same file, extend `PlannedEncounterView` (line 86) with a trailing component and `EncounterView` (line 76) with the new list:

```java
    public record PlannedEncounterView(UUID id, String name, UUID mapId, String mapName,
                                       ReadinessVerdict verdict, int combatantCount,
                                       int unplacedCount, boolean forCurrentScene) {}

    public record EncounterView(UUID activeEncounterId, String activeEncounterName,
                                UUID activeEncounterMapId, String combatPhase,
                                List<CombatantView> combatants,
                                List<PlannedEncounterView> currentScenePlanned,
                                List<PlannedEncounterView> planned,
                                List<PlannedEncounterView> suspended,
                                List<PlannedEncounterView> finished) {}
```

In `encounter(UUID campaignId)`, replace the sorting and the return (lines 278-283 and 307-327):

```java
        Comparator<Encounter> naturalByName =
                (a, b) -> NaturalOrder.compare(a.getName(), b.getName());
        List<Encounter> all = encounters.findByCampaignIdOrderByNameAsc(campaignId);
        List<Encounter> planned = all.stream()
                .filter(e -> e.getStatus() == Encounter.Status.PLANNED)
                .sorted(naturalByName)
                .toList();
        List<Encounter> suspended = all.stream()
                .filter(e -> e.getStatus() == Encounter.Status.SUSPENDED)
                .sorted(naturalByName)
                .toList();
```

```java
        // Scene owns the link (Scene.encounter); Encounter deliberately has no back-reference.
        // A DM in a scene wants that linked encounter before an alphabet of 49.
        Scene currentScene = adventures.getCurrentScene(campaignId).orElse(null);
        Set<UUID> sceneEncounterIds = currentScene != null && currentScene.getEncounter() != null
                ? Set.of(currentScene.getEncounter().getId())
                : Set.of();

        java.util.function.Function<Encounter, PlannedEncounterView> toView = e -> {
            var combatantList = combatants.findByEncounterIdOrderBySortOrderAsc(e.getId());
            int combatantCount = combatantList.size();
            long unplacedCount = combatantList.stream().filter(c -> c.getPlacement() == null).count();
            ReadinessVerdict verdict = EncounterPlacementService.verdictFor(
                    e.getMap() != null, false, combatantCount, (int) unplacedCount);
            return new PlannedEncounterView(e.getId(), e.getName(),
                    e.getMap() != null ? e.getMap().getId() : null,
                    e.getMap() != null ? e.getMap().getName() : null,
                    verdict, combatantCount, (int) unplacedCount,
                    sceneEncounterIds.contains(e.getId()));
        };

        List<PlannedEncounterView> plannedViews = planned.stream().map(toView).toList();
        return new EncounterView(
                active != null ? active.getId() : null,
                active != null ? active.getName() : null,
                active != null && active.getMap() != null ? active.getMap().getId() : null,
                active != null ? active.getCombatPhase().name() : null,
                combatantViews,
                List.copyOf(plannedViews.stream().filter(PlannedEncounterView::forCurrentScene).toList()),
                List.copyOf(plannedViews.stream().filter(v -> !v.forCurrentScene()).toList()),
                List.copyOf(suspended.stream().map(toView).toList()),
                List.copyOf(done.stream().map(toView).toList()));
```

Add `import java.util.Comparator;` and `import java.util.Set;`. `Scene` is already imported by
`CockpitRuntimeModuleViewService`.

- [ ] **Step 6: Render the two sections**

In `src/main/resources/templates/session/_encounter-rail.html`, insert this section before the existing `<summary>Planned encounters</summary>` block's `<details>` (i.e. as a new `.sidebar-section` between the tracker section and the planned list):

```html
  <div class="sidebar-section" th:if="${!view.currentScenePlanned.isEmpty()}">
    <h3>This scene</h3>
    <th:block th:each="enc : ${view.currentScenePlanned}">
      <div class="planned-encounter-row" th:attr="data-name=${enc.name}">
        <span class="planned-encounter-row__title" th:text="${enc.name}">Goblin Ambush</span>
        <span class="planned-encounter-row__meta">
          <span th:text="${enc.mapName != null ? enc.mapName : 'No map'}">Map</span>
          <span aria-hidden="true">·</span>
          <span th:text="${enc.combatantCount + (enc.combatantCount == 1 ? ' combatant' : ' combatants')}">6 combatants</span>
        </span>
        <button class="btn btn-ghost encounter-rail__action"
                th:attr="data-encounter-id=${enc.id}"
                @click="runEncounter($el.dataset.encounterId)">Run</button>
      </div>
    </th:block>
  </div>
```

Change the remaining list's summary text from `Planned encounters` to:

```html
      <summary>All planned encounters</summary>
```

- [ ] **Step 7: Stop the meta line wrapping raggedly (I2)**

In `src/main/resources/static/css/cockpit-modules.css`, next to the existing `.planned-encounter-row` rules:

```css
/* One line, elided. The map name and the combatant count are orientation, not content;
   wrapping them across two ragged lines made every row read as a three-column jumble. */
.planned-encounter-row__meta {
    display: flex;
    align-items: baseline;
    gap: 4px;
    min-width: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}
.planned-encounter-row__meta > span:first-child {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
}
```

- [ ] **Step 8: Run the tests and watch them pass**

Run:
`./mvnw -o test -Dtest='EncounterRailOrderingTest,EncounterDiscoverabilityBrowserTest#theCurrentScenesEncounterAppearsBeforeTheUnrelatedList,CockpitRuntimeModuleContractTest,SessionControllerTest'`
Expected: PASS, 0 failures. `SessionControllerTest#encounterRailFragmentReturnsPartialHtml`
constructs `EncounterView`; add one extra `List.of()` argument for `currentScenePlanned`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java \
        src/main/resources/templates/session/_encounter-rail.html \
        src/main/resources/static/css/cockpit-modules.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter/EncounterRailOrderingTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/EncounterDiscoverabilityBrowserTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionControllerTest.java
git commit -m "feat: order the encounter rail naturally and foreground the current scene"
```

---

## Phase J — Map token legibility

### Task 24: Auto-placement starts near the map centre and the encounter view frames it

The fallback placement search currently begins at row 0, column 0. That is a valid database
coordinate but a poor runtime default: every unprepared encounter becomes a clipped strip in
the top-left corner. Explicit coordinates and named placement regions remain authoritative.
Only the no-hint fallback changes, and the client frames the resulting encounter once after the
coherent encounter/map transition from Task 2.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementService.java:170-221,350-388` (centred fallback)
- Modify: `src/main/resources/static/js/map/battle-map.js` (`frameEncounterTokens`, called by `setEncounterAndMap`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementFramingTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java`

**Interfaces:**
- Consumes: `BattleMap#setEncounterAndMap(encounterId, mapId)` (Task 2).
- Produces: package-visible
  `EncounterPlacementService.findCenteredFreeCell(Set<String>, int, int, int, int) -> int[]|null`;
  `BattleMap#frameEncounterTokens() -> boolean`.

- [ ] **Step 1: Write the failing centred-placement test**

Create
`src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementFramingTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.encounter.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EncounterPlacementFramingTest {

    @Test
    void theFirstFallbackCellIsNearTheMapCentreAndNotOnAnEdge() {
        int[] cell = EncounterPlacementService.findCenteredFreeCell(
                new HashSet<>(), 20, 12, 1, 1);

        assertThat(cell).isNotNull();
        assertThat(cell[0]).isBetween(8, 11);
        assertThat(cell[1]).isBetween(4, 7);
        assertThat(cell[0]).isNotIn(0, 19);
        assertThat(cell[1]).isNotIn(0, 11);
    }

    @Test
    void successiveFallbackCellsStaySeparateAndInsideTheMargin() {
        Set<String> occupied = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            int[] cell = EncounterPlacementService.findCenteredFreeCell(
                    occupied, 20, 12, 1, 1);
            assertThat(cell).isNotNull();
            assertThat(cell[0]).isBetween(1, 18);
            assertThat(cell[1]).isBetween(1, 10);
            assertThat(occupied.add(cell[0] + "," + cell[1])).isTrue();
        }
        assertThat(occupied).hasSize(8);
    }

    @Test
    void aTinyOrFullInteriorStillFallsBackToAnyValidCell() {
        Set<String> occupied = new HashSet<>(Set.of("1,1"));
        int[] cell = EncounterPlacementService.findCenteredFreeCell(
                occupied, 2, 2, 1, 1);

        assertThat(cell).isNotNull();
        assertThat(cell[0]).isBetween(0, 1);
        assertThat(cell[1]).isBetween(0, 1);
    }
}
```

- [ ] **Step 2: Write the failing browser framing test**

Add to `CockpitMapTransitionBrowserTest`:

```java
    @Test
    void aNewEncounterFramesEveryAutoPlacedTokenInsideTheCanvas() {
        var seeded = fixtures.campaignWithTwoEncountersOnTwoMaps();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/"
                + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForFunction("() => !!window.battleMap");

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.session-cockpit'))"
                + ".runEncounter(id)", seeded.encounterB().toString());
        page.waitForFunction("(id) => window.battleMap.activeEncounterId === id",
                seeded.encounterB().toString());
        page.waitForFunction("() => window.battleMap.tokens"
                + ".filter(t => t.source === 'COMBATANT').length > 0");

        @SuppressWarnings("unchecked")
        var bounds = (java.util.Map<String, Object>) page.evaluate("""
                () => {
                  const bm = window.battleMap;
                  const stage = bm.stage;
                  const inset = 6;
                  const failures = bm.tokens
                    .filter(t => t.source === 'COMBATANT')
                    .map(t => bm.tokenNodes[bm.tokenKey(t)]?.group)
                    .filter(Boolean)
                    .map(group => {
                      const r = group.getClientRect({ relativeTo: stage });
                      const scale = stage.scaleX();
                      return {
                        left: stage.x() + r.x * scale,
                        top: stage.y() + r.y * scale,
                        right: stage.x() + (r.x + r.width) * scale,
                        bottom: stage.y() + (r.y + r.height) * scale
                      };
                    })
                    .filter(r => r.left < inset || r.top < inset
                      || r.right > stage.width() - inset
                      || r.bottom > stage.height() - inset);
                  return { count: failures.length, width: stage.width(), height: stage.height() };
                }
                """);

        assertThat(((Number) bounds.get("count")).intValue())
                .as("auto-placed token groups, including HP labels, stay inside the canvas")
                .isZero();
    }
```

- [ ] **Step 3: Run both tests and watch them fail**

Run:
`./mvnw -o test -Dtest='EncounterPlacementFramingTest,CockpitMapTransitionBrowserTest#aNewEncounterFramesEveryAutoPlacedTokenInsideTheCanvas'`

Expected: FAIL — the helper does not exist, and the browser finds token bounds at the top
canvas edge.

- [ ] **Step 4: Search for a free cell from the centre outward**

In `EncounterPlacementService`, add:

```java
    /**
     * Runtime fallback only. Explicit start coordinates and placement regions are handled
     * before this method. Prefer an inset cell nearest the map centre, then fall back to any
     * valid cell when a small or crowded map has no free interior.
     */
    static int[] findCenteredFreeCell(Set<String> occupiedCells,
                                      int gridWidth, int gridHeight,
                                      int sizeCols, int sizeRows) {
        int insetX = gridWidth >= sizeCols + 2 ? 1 : 0;
        int insetY = gridHeight >= sizeRows + 2 ? 1 : 0;
        List<int[]> candidates = new ArrayList<>();
        for (int row = insetY; row <= gridHeight - sizeRows - insetY; row++) {
            for (int col = insetX; col <= gridWidth - sizeCols - insetX; col++) {
                if (isCellFree(occupiedCells, col, row, sizeCols, sizeRows,
                        gridWidth, gridHeight)) {
                    candidates.add(new int[]{col, row});
                }
            }
        }
        long centreX2 = gridWidth - sizeCols;
        long centreY2 = gridHeight - sizeRows;
        candidates.sort(Comparator
                .comparingLong((int[] cell) -> {
                    long dx2 = 2L * cell[0] - centreX2;
                    long dy2 = 2L * cell[1] - centreY2;
                    return dx2 * dx2 + dy2 * dy2;
                })
                .thenComparingInt(cell -> cell[1])
                .thenComparingInt(cell -> cell[0]));
        return candidates.isEmpty()
                ? findFreeCell(occupiedCells, gridWidth, gridHeight, sizeCols, sizeRows)
                : candidates.getFirst();
    }
```

Add `import java.util.Comparator;` if the wildcard import has been replaced by explicit
imports. In `preferredOrFreeCell`, replace its final return:

```java
        return findCenteredFreeCell(occupiedCells, gridWidth, gridHeight, 1, 1);
```

In `placeUnplacedPartyCombatants`, replace its call to `findFreeCell` with:

```java
            int[] cell = findCenteredFreeCell(
                    occupiedCells, map.getGridWidth(), map.getGridHeight(), 1, 1);
```

Do not change the explicit-coordinate or named-region branches.

- [ ] **Step 5: Frame the encounter once after its transition**

Add to `BattleMap`, after `resizeToContainer()`:

```javascript
    /**
     * Fit the active encounter's complete token groups (body, HP bar and HP text) into the
     * current stage. This runs at encounter activation, not on every token update, so the
     * DM's later pan/zoom choices remain theirs.
     */
    frameEncounterTokens() {
        if (!this.stage || !this.container?.isConnected) return false;
        const groups = this.tokens
            .filter(token => token.source === 'COMBATANT')
            .map(token => this.tokenNodes[this.tokenKey(token)]?.group)
            .filter(Boolean);
        if (groups.length === 0) return false;

        const boxes = groups.map(group => group.getClientRect({ relativeTo: this.stage }));
        const minX = Math.min(...boxes.map(box => box.x));
        const minY = Math.min(...boxes.map(box => box.y));
        const maxX = Math.max(...boxes.map(box => box.x + box.width));
        const maxY = Math.max(...boxes.map(box => box.y + box.height));
        const padding = Math.max(12, this.cellSizePx * 0.4);
        const availableWidth = Math.max(1, this.stage.width() - padding * 2);
        const availableHeight = Math.max(1, this.stage.height() - padding * 2);
        const contentWidth = Math.max(1, maxX - minX);
        const contentHeight = Math.max(1, maxY - minY);
        const scale = Math.max(0.2, Math.min(
            1, availableWidth / contentWidth, availableHeight / contentHeight));

        this.stage.scale({ x: scale, y: scale });
        this.stage.position({
            x: this.stage.width() / 2 - ((minX + maxX) / 2) * scale,
            y: this.stage.height() / 2 - ((minY + maxY) / 2) * scale,
        });
        this.stage.batchDraw();
        return true;
    }
```

In the Task 2 implementation of `setEncounterAndMap`, call
`this.frameEncounterTokens()` after `switchToMap(mapId)` succeeds. In its same-map branch,
call it immediately after `this.renderTokens()`. These are the only two call sites.

- [ ] **Step 6: Run both tests and watch them pass**

Run:
`./mvnw -o test -Dtest='EncounterPlacementFramingTest,EncounterPlacementServiceTest,CockpitMapTransitionBrowserTest'`

Expected: PASS, 0 failures. The existing explicit-coordinate and named-region tests prove
those higher-priority placement paths did not change.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementService.java \
        src/main/resources/static/js/map/battle-map.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementFramingTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitMapTransitionBrowserTest.java
git commit -m "fix: centre and frame fallback encounter placements"
```

---

### Task 25: Tokens remain distinct and Konva uses four layers

Adjacent one-cell placements currently touch edge-to-edge, while labels take the first two
letters of a name. Eight enemies therefore become one coloured slab labelled `Go`, `Go`,
`Ho`, `Ho`. PC/NPC identity is colour-only. The same stage also creates six `Konva.Layer`
instances, which causes Konva's repeated layer-count warning.

Keep terrain, actors, annotations, and transient previews as the four actual layers. Grid and
pins become groups inside terrain and actor layers respectively. Tokens gain an inset gutter,
a stable visible ordinal, a shape distinction, and a stronger active-turn halo.

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresentationContractTest.java`

**Interfaces:**
- Consumes: stable group labels from Task 4 and the four-layer architecture stated in the
  plan header.
- Produces: `tokenLabel(name, duplicateOrdinal) -> string`;
  `kindCornerRadius(kind, width, height) -> number`; exactly four stage layers.

- [ ] **Step 1: Write the failing source contract**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresentationContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitPresentationContractTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }

    @Test
    void theBattleMapUsesFourLayersAndShapeBasedTokenIdentity() throws IOException {
        String js = read("src/main/resources/static/js/map/battle-map.js");

        assertThat(occurrences(js, "new Konva.Layer("))
                .as("terrain, actors, annotations, preview")
                .isEqualTo(4);
        assertThat(js)
                .contains("new Konva.Group({ name: 'runtime-grid'")
                .contains("new Konva.Group({ name: 'runtime-pins'")
                .contains("TOKEN_GUTTER_PX")
                .contains("tokenLabel(")
                .contains("kindCornerRadius(")
                .contains("shadowBlur: 12");
    }
}
```

- [ ] **Step 2: Run the contract and watch it fail**

Run:
`./mvnw -o test -Dtest=CockpitPresentationContractTest#theBattleMapUsesFourLayersAndShapeBasedTokenIdentity`

Expected: FAIL — the source contains six `new Konva.Layer(` calls.

- [ ] **Step 3: Add the token-label and shape helpers**

At the top of `battle-map.js`, after the colour constants, add:

```javascript
const TOKEN_GUTTER_PX = 2;

function tokenLabel(name, duplicateOrdinal = null) {
    const value = String(name || '?').trim();
    const suffix = value.match(/(?:^|\s)(\d+)$/)?.[1] || null;
    const initial = value.match(/[\p{L}\p{N}]/u)?.[0]?.toUpperCase() || '?';
    if (suffix) return `${initial}${suffix}`;
    if (duplicateOrdinal != null) return `${initial}${duplicateOrdinal}`;
    return value.slice(0, 3);
}

function kindCornerRadius(kind, width, height) {
    if (kind === 'PC') return Math.min(width, height) / 2;
    if (kind === 'NPC') return Math.min(8, Math.min(width, height) / 4);
    if (kind === 'OBJECT') return 0;
    return 2;
}
```

- [ ] **Step 4: Replace six layers with four layers and two groups**

In the constructor, replace the `gridLayer` and `pinLayer` fields with group fields while
retaining their names so the existing show/hide calls remain readable:

```javascript
        this.terrainLayer = null;
        this.gridLayer = null;       // Konva.Group inside terrainLayer
        this.tokenLayer = null;
        this.tokenGroup = null;      // combatant and marker tokens
        this.pinLayer = null;        // Konva.Group inside tokenLayer
        this.annotationLayer = null;
        this.previewLayer = null;
```

In `load()`, replace the six layer declarations with:

```javascript
        this.terrainLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.terrainLayer);
        this.gridLayer = new Konva.Group({ name: 'runtime-grid', listening: false });
        this.terrainLayer.add(this.gridLayer);

        this.tokenLayer = new Konva.Layer();
        this.stage.add(this.tokenLayer);
        this.tokenGroup = new Konva.Group({ name: 'runtime-tokens' });
        this.pinLayer = new Konva.Group({ name: 'runtime-pins' });
        this.tokenLayer.add(this.tokenGroup);
        this.tokenLayer.add(this.pinLayer);

        this.annotationLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.annotationLayer);

        this.previewLayer = new Konva.Layer();
        this.stage.add(this.previewLayer);
```

Change `renderTerrain` to pass `targetLayer: this.terrainLayer` as it does today. Because
`renderRuntimeDocument` calls `destroyChildren`, re-add the grid group after that await:

```javascript
    async renderTerrain(doc) {
        await renderRuntimeDocument({
            Konva,
            document: doc,
            targetLayer: this.terrainLayer,
            gridWidth: this.gridWidth,
            gridHeight: this.gridHeight,
            cellSizePx: this.cellSizePx,
            playerView: false,
        });
        this.gridLayer = new Konva.Group({ name: 'runtime-grid', listening: false });
        this.terrainLayer.add(this.gridLayer);
    }
```

`renderGrid()` continues to call
`drawGrid(this.gridLayer, this.gridWidth, this.gridHeight, this.cellSizePx)`, but its final draw becomes
`this.terrainLayer.batchDraw()`. In `renderTokens()`, change
`this.tokenLayer.destroyChildren()` to `this.tokenGroup.destroyChildren()`. In
`addTokenNode`, change `this.tokenLayer.add(group)` to `this.tokenGroup.add(group)`.

In `loadPins`, `_drawScenePin`, and `_drawThreatPin`, keep adding/destroying children through
`this.pinLayer`, but replace every `this.pinLayer.draw()` with
`this.tokenLayer.batchDraw()`. Replace the one `this.pinLayer.batchDraw()` in Task 1's
`reattach` implementation with `this.tokenLayer.batchDraw()`.

- [ ] **Step 5: Give every token separation, an ordinal, and shape identity**

In `renderTokens`, replace the `nameCounts`/`nameIndex` block and the label override with:

```javascript
        const baseName = name => String(name || '').replace(/\s+\d+$/, '').trim();
        const counts = {};
        for (const token of this.tokens) {
            const base = baseName(token.name);
            counts[base] = (counts[base] || 0) + 1;
        }
        const indexes = {};

        for (const token of this.tokens) {
            const key = this.tokenKey(token);
            const base = baseName(token.name);
            indexes[base] = (indexes[base] || 0) + 1;
            const group = this.addTokenNode(token);
            const node = this.tokenNodes[key];
            node.label.text(tokenLabel(
                token.name,
                counts[base] > 1 ? indexes[base] : null));
```

Keep the animation block that follows and close the loop in the same place it closes today.

In `addTokenNode`, replace the `body` declaration:

```javascript
        const gutter = Math.min(TOKEN_GUTTER_PX, Math.max(0, Math.min(w, h) / 8));
        const body = new Konva.Rect({
            x: gutter,
            y: gutter,
            width: Math.max(1, w - gutter * 2),
            height: Math.max(1, h - gutter * 2),
            fill: defeated ? '#555' : (token.color || '#c9a35c'),
            stroke: selected ? SELECTION_GOLD : (KIND_RING_COLORS[token.kind] || '#fff'),
            strokeWidth: selected ? 3 : 2,
            cornerRadius: kindCornerRadius(token.kind, w - gutter * 2, h - gutter * 2),
            opacity: defeated ? 0.6 : 1,
        });
```

Replace the label's `text` and font size:

```javascript
            text: tokenLabel(token.name),
            fontSize: Math.min(w, h) * 0.32,
```

In `highlightActiveTurn`, replace the `glow` declaration with:

```javascript
        const glow = new Konva.Rect({
            width: tw + 10, height: th + 10,
            x: -5, y: -5,
            stroke: '#ffd700',
            strokeWidth: 4,
            cornerRadius: kindCornerRadius(token.kind, tw, th) + 5,
            fillEnabled: false,
            shadowColor: '#ffd700',
            shadowBlur: 12,
            shadowOpacity: 0.9,
            listening: false,
            name: 'turn-highlight',
        });
```

The existing condition pips, HP bar, defeated cross, drag behavior, selection pulse, and
reduced-motion branch remain unchanged.

- [ ] **Step 6: Run the tests and watch them pass**

Run:
`./mvnw -o test -Dtest='CockpitPresentationContractTest,SessionCockpitMapContractTest,CockpitMapTransitionBrowserTest'`

Expected: PASS, and `BrowserFailureCollector` reports no Konva layer-count warning.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/map/battle-map.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresentationContractTest.java
git commit -m "fix: separate map tokens and consolidate the runtime map layers"
```

---

## Phase K — Copy, controls and presentation

K6 is implemented by Task 15, K7 by Task 18, K11 by Task 13, and K13 by Task 4.
K14 is explicitly contextual in the specification, not a defect; this plan makes no
space-filling layout change. K12 is settled here: negative HP remains because overkill is
DM-useful, and the UI states that meaning instead of silently looking like an arithmetic bug.

### Task 26: Tracker setup and turn controls read cleanly at rail width

This task closes K1–K5, K10, and K12 on the single surface where they interact. Initiative
actions get a resting button treatment; the unset copy becomes one sentence; tie rules become
progressive disclosure; encounter termination is separated from round state; previous/active/
next stays one row; trap prefill actions remain obvious before hover; and negative HP explains
itself.

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/templates/threat/_mechanics-card.html`
- Modify: `src/main/resources/static/css/components.css`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresentationContractTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java`

**Interfaces:**
- Consumes: the row grid and stable label from Task 4.
- Produces: `[data-initiative-primary]`, `[data-tie-help]`, `[data-unset-summary]`,
  `[data-dice-prefill]`, and `.tracker-header__end-zone` presentation contracts.

- [ ] **Step 1: Write the failing presentation contract**

Add to `CockpitPresentationContractTest`:

```java
    @Test
    void trackerPrimaryControlsAndCopyHaveStablePresentationHooks() throws IOException {
        String tracker = read("src/main/resources/templates/encounter/_tracker.html");
        String threat = read("src/main/resources/templates/threat/_mechanics-card.html");

        assertThat(tracker)
                .contains("data-initiative-primary")
                .contains("<details class=\"initiative-setup__tie-help\" data-tie-help>")
                .contains("data-unset-summary")
                .contains("tracker-header__end-zone")
                .contains("End encounter")
                .contains("tracker-turns__active-name")
                .contains("Negative HP shows damage beyond 0");
        assertThat(tracker).doesNotContain(">End</button>");
        assertThat(threat).contains("data-dice-prefill");
    }
```

- [ ] **Step 2: Extend the rail-width browser assertion**

In `CockpitModuleFitTest`, add
`import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;`, add:

```java
    final BrowserFailureCollector failures = new BrowserFailureCollector();
```

In `openPage`, call `failures.clear()`, create the context with a 1366×768 viewport, create
the page, and attach the collector:

```java
        failures.clear();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1366, 768));
        page = context.newPage();
        failures.attach(page);
```

Replace `closePage` with:

```java
    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }
```

In `initiativeSetupFitsTheEncounterRail`, extend the evaluated result:

```javascript
                  const primary = Array.from(
                    panel.querySelectorAll('[data-initiative-primary]'));
                  return {
                    overflow,
                    inputInside: inside(input),
                    rollInside: inside(roll),
                    primaryInside: primary.every(inside)
                  };
```

and change the expected map string to:

```java
        assertThat(fit.toString()).isEqualTo(
                "{overflow=0, inputInside=true, rollInside=true, "
                        + "primaryInside=true}");
```

- [ ] **Step 3: Run the tests and watch them fail**

Run:
`./mvnw -o test -Dtest='CockpitPresentationContractTest#trackerPrimaryControlsAndCopyHaveStablePresentationHooks,CockpitModuleFitTest#initiativeSetupFitsTheEncounterRail'`

Expected: FAIL — the hooks do not exist and the old unset copy wraps into fragments.

- [ ] **Step 4: Restructure the tracker header and setup copy**

In `_tracker.html`, replace the encounter header with:

```html
            <div class="tracker-header">
                <div class="tracker-header__identity">
                    <h3 data-encounter-name x-text="encounter?.name || 'Combat'"></h3>
                    <span class="tracker-heading__round">
                      Round <strong class="round-counter" x-ref="roundCounter"
                                    x-text="encounter?.round || 0"></strong>
                    </span>
                    <span class="badge badge-warning" x-show="pendingWaves.length > 0"
                          x-text="pendingWaves.length + ' pending wave'
                            + (pendingWaves.length !== 1 ? 's' : '')"></span>
                </div>
                <div class="tracker-header__end-zone">
                    <button class="btn btn-danger tracker-small-button"
                            data-end-encounter
                            @click="$dispatch('request-encounter-end')">
                      End encounter
                    </button>
                </div>
            </div>
```

In the initiative header, wrap both actions in one action group:

```html
                <div class="initiative-setup__actions">
                    <button type="button"
                            class="btn btn-sm initiative-setup__primary"
                            data-add-party-to-encounter
                            data-initiative-primary
                            :disabled="setupBusy || setupSaveCount > 0"
                            @click="addPartyToEncounter()">
                        Add party
                    </button>
                    <button type="button"
                            class="btn btn-sm initiative-setup__primary"
                            data-roll-unset-initiative
                            data-initiative-primary
                            :disabled="setupBusy || setupSaveCount > 0 || unsetNpcCount === 0"
                            @click="rollUnsetNpcs()">
                        Roll unset NPCs
                    </button>
                </div>
```

Remove the two original sibling buttons.

Replace the tie paragraph with:

```html
                <details class="initiative-setup__tie-help" data-tie-help>
                    <summary>How ties are ordered</summary>
                    <p>Higher tie-breaker first, then prior order, then name.
                       Accepted unset combatants act last.</p>
                </details>
```

Replace the unset label contents with:

```html
                <label class="initiative-setup__accept" x-show="unsetCombatants.length > 0">
                    <input type="checkbox" x-model="acceptUnset">
                    <span data-unset-summary
                          x-text="'Start combat with ' + unsetCombatants.length
                            + ' unset combatant' + (unsetCombatants.length === 1 ? '' : 's')
                            + ' (they act last).'"></span>
                </label>
```

Replace the middle span in `.tracker-turns` with:

```html
                <span class="tracker-turns__active">
                    <span class="tracker-turns__active-prefix">Active:</span>
                    <strong class="tracker-turns__active-name"
                            :title="activeCombatant?.name || ''"
                            x-text="activeCombatant?.name || '—'"></strong>
                </span>
```

On `.combatant-hp`, add:

```html
:title="c.currentHp < 0
  ? 'Negative HP shows damage beyond 0; this is retained for the DM.'
  : null"
```

- [ ] **Step 5: Give every threat-prefill action a resting affordance**

In `_tracker.html`, on every `Prefill 1d20`, `Prefill attack`, `Prefill damage`, and disarm
prefill button inside `.active-threat-card`, use:

```html
class="btn btn-sm threat-prefill-action" data-dice-prefill
```

Apply the same class and attribute to all six prefill button sites in
`threat/_mechanics-card.html`. Do not change their event payloads; the prefill behavior is a
known-good surface protected by `ThreatDicePrefillContractTest`.

- [ ] **Step 6: Apply the rail-width layout**

In `components.css`, replace the current `.tracker-header` and `.tracker-turns` rules with:

```css
.tracker-header {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: stretch;
    gap: var(--space-sm);
    padding: var(--space-sm) var(--space-md);
    border-bottom: 1px solid var(--color-border);
}
.tracker-header__identity {
    min-width: 0;
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: var(--space-xs) var(--space-sm);
}
.tracker-header__identity h3 {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin: 0;
}
.tracker-header__end-zone {
    display: flex;
    align-items: center;
    padding-left: var(--space-sm);
    border-left: 1px solid var(--color-border);
}
.tracker-turns {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    align-items: center;
    padding: var(--space-xs) var(--space-md);
    border-bottom: 1px solid var(--color-border);
    gap: var(--space-sm);
}
.tracker-turns__active {
    min-width: 0;
    display: flex;
    justify-content: center;
    gap: 0.35em;
}
.tracker-turns__active-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
```

Add:

```css
.initiative-setup__primary,
.threat-prefill-action {
    color: var(--color-text);
    background: var(--elevation-raised-bg);
    border: 1px solid var(--color-border-strong);
}
.initiative-setup__actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: var(--space-xs);
}
.initiative-setup__tie-help summary {
    cursor: pointer;
    color: var(--color-text-muted);
}
.initiative-setup__tie-help p { margin: var(--space-xs) 0 0; }
.initiative-setup__accept [data-unset-summary] {
    min-width: 0;
}
```

Within the existing
`@container cockpit-module (max-width: 34rem)` rule, keep the initiative header as a grid
instead of a vertical stack:

```css
    .initiative-setup__header {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: start;
    }
    .initiative-setup__actions {
        grid-column: 2;
        display: grid;
        justify-items: stretch;
    }
```

- [ ] **Step 7: Run the tests and watch them pass**

Run:
`./mvnw -o test -Dtest='CockpitPresentationContractTest,CockpitModuleFitTest,ThreatDicePrefillContractTest,TrackerIdentityBrowserTest'`

Expected: PASS at 1366×768 with no failed responses, page errors, console errors, or
warnings.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/templates/threat/_mechanics-card.html \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresentationContractTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitModuleFitTest.java
git commit -m "fix: clarify combat setup and turn controls at rail width"
```

---

### Task 27: Dates and compact runtime labels read as human language

K8, K9, and K15 are the remaining content-level findings. The app already has
`DisplayLabels.humanizeEnum`, and the party summary already carries `title` expansion for its
abbreviations; this task makes those contracts apply consistently to the runtime surfaces.
For imported placeholder month names such as `1. Monat`, a numeric date is more truthful than
pretending the placeholder is a real month name.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/InGameDateFormatter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java`
- Modify: `src/main/resources/templates/calendar/_current-date.html`
- Modify: `src/main/resources/templates/threat/_mechanics-card.html`
- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/static/js/combat-tracker.js`
- Modify: `src/main/resources/templates/party/_summary-bar.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/calendar/CalendarFormatDateTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/party/PartyStatAbbreviationTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresentationContractTest.java`

**Interfaces:**
- Consumes: `DisplayLabels.humanizeEnum(Enum<?>)` (existing).
- Produces: `InGameDateFormatter.format(Integer, Integer, Integer, String[]) -> String`;
  `combatTracker#humanizeConstant(value) -> string`.

- [ ] **Step 1: Write the failing formatter tests**

Add these tests to `CalendarFormatDateTest`:

```java
    @Test
    void aRealMonthNameRendersAsAReadableFantasyDate() {
        assertThat(dev.hendrikhoemberg.dmhelper.common.web.InGameDateFormatter.format(
                1491, 0, 12, new String[]{"Hammer"}))
                .isEqualTo("12. Hammer 1491");
    }

    @Test
    void anImportedPlaceholderMonthRendersAsAnUnambiguousNumericDate() {
        assertThat(dev.hendrikhoemberg.dmhelper.common.web.InGameDateFormatter.format(
                1491, 0, 12, new String[]{"1. Monat"}))
                .isEqualTo("12.1.1491");
        assertThat(dev.hendrikhoemberg.dmhelper.common.web.InGameDateFormatter.format(
                1491, 1, 3, new String[]{"Month-1", "Month-2"}))
                .isEqualTo("3.2.1491");
    }
```

Change the existing expected value from `"15 April 1492"` to `"15. April 1492"`.

- [ ] **Step 2: Write the failing copy contracts**

Add to `CockpitPresentationContractTest`:

```java
    @Test
    void runtimeEnumsAndDatesUseDisplayFormatters() throws IOException {
        String threat = read("src/main/resources/templates/threat/_mechanics-card.html");
        String tracker = read("src/main/resources/templates/encounter/_tracker.html");
        String calendar = read("src/main/resources/templates/calendar/_current-date.html");
        String draft = read("src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java");

        assertThat(threat).contains("DisplayLabels).humanizeEnum(dt)");
        assertThat(tracker).contains("humanizeConstant(dt)");
        assertThat(calendar).contains("@calendarService.formatDate");
        assertThat(draft).contains("InGameDateFormatter.format");
    }
```

Extend `PartyStatAbbreviationTest#everyAbbreviatedPartyStatCarriesAnExpandedTitle`:

```java
        assertThat(html)
                .contains("aria-label=|Armour Class")
                .contains("aria-label=|Hit Points")
                .contains("aria-label=|Passive Perception")
                .contains("aria-label=|Passive Insight")
                .contains("aria-label=|Passive Investigation")
                .contains("aria-label=|Speed");
```

- [ ] **Step 3: Run the tests and watch them fail**

Run:
`./mvnw -o test -Dtest='CalendarFormatDateTest,CockpitPresentationContractTest#runtimeEnumsAndDatesUseDisplayFormatters,PartyStatAbbreviationTest'`

Expected: FAIL — `InGameDateFormatter` and the formatter call sites do not exist.

- [ ] **Step 4: Implement the one in-game date formatter**

Create
`src/main/java/dev/hendrikhoemberg/dmhelper/common/web/InGameDateFormatter.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import java.util.regex.Pattern;

/** Human-facing rendering for the campaign's zero-based in-game month index. */
public final class InGameDateFormatter {
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)^(?:\\d+\\.?\\s.*|month[- ]?\\d+)$");

    private InGameDateFormatter() {}

    public static String format(Integer year, Integer month, Integer day,
                                String[] monthNames) {
        if (year == null || month == null || day == null) return "\u2014";
        String monthName = month >= 0 && monthNames != null && month < monthNames.length
                ? monthNames[month]
                : null;
        if (monthName == null || monthName.isBlank()
                || PLACEHOLDER.matcher(monthName.trim()).matches()) {
            return day + "." + (month + 1) + "." + year;
        }
        return day + ". " + monthName.trim() + " " + year;
    }
}
```

In `CalendarService.formatDate`, replace its body after loading the config with:

```java
        return InGameDateFormatter.format(
                date.year(), date.month(), date.day(), config.monthNames());
```

and import `dev.hendrikhoemberg.dmhelper.common.web.InGameDateFormatter`.

In `SessionDraftService`, import the same class and replace its private `formatDate` body:

```java
        return InGameDateFormatter.format(year, month, day, monthNames);
```

In `calendar/_current-date.html`, replace the three date spans and `th:with` on their parent
with:

```html
        <div class="calendar-current-date-value" style="font-weight: 600;"
             th:text="${@calendarService.formatDate(campaignId, currentDate)}">
            15. March 1492
        </div>
```

- [ ] **Step 5: Humanize damage types on server and client surfaces**

In both damage-type loops in `threat/_mechanics-card.html`, replace the `th:text` expression:

```html
th:text="${(iter.first ? ' ' : ', ')
  + T(dev.hendrikhoemberg.dmhelper.common.web.DisplayLabels).humanizeEnum(dt)}"
```

In `combat-tracker.js`, add next to `signedBonus`:

```javascript
            humanizeConstant(value) {
                const words = String(value || '')
                    .replaceAll('_', ' ')
                    .toLowerCase();
                return words ? words.charAt(0).toUpperCase() + words.slice(1) : '—';
            },
```

In both active threat damage blocks in `_tracker.html`, immediately after the damage
expression span, add:

```html
                                <span x-show="activeThreatMechanics.damageTypes?.length"
                                      x-text="' ' + activeThreatMechanics.damageTypes
                                        .map(dt => humanizeConstant(dt)).join(', ')"></span>
```

- [ ] **Step 6: Make compact party stats self-explanatory without hover**

In `party/_summary-bar.html`, add bound `aria-label` values to the stat spans while keeping
their short visible labels. The complete primary stat block becomes:

```html
            <div class="chip-stats" aria-label="Armour Class, Hit Points, Passive Perception">
                <span title="Armour Class"
                      th:attr="aria-label=|Armour Class ${m.ac}|"
                      th:text="'AC ' + ${m.ac}">AC 16</span>
                <span aria-hidden="true">·</span>
                <span title="Hit Points"
                      th:attr="aria-label=|Hit Points ${m.currentHp} of ${m.maxHp}|"
                      th:text="'HP ' + ${m.currentHp} + '/' + ${m.maxHp}">HP 32/32</span>
                <span aria-hidden="true">·</span>
                <span title="Passive Perception"
                      th:attr="aria-label=|Passive Perception ${m.passivePerception}|"
                      th:text="'PP ' + ${m.passivePerception}">PP 14</span>
                <span title="Temporary Hit Points" th:if="${m.tempHp > 0}"
                      th:attr="aria-label=|Temporary Hit Points ${m.tempHp}|"
                      th:text="'THP ' + ${m.tempHp}">THP 0</span>
            </div>
```

Replace the three `.chip-detail` stat spans with:

```html
                    <span title="Passive Insight" th:if="${m.passiveInsight > 0}"
                          th:attr="aria-label=|Passive Insight ${m.passiveInsight}|"
                          th:text="'PI ' + ${m.passiveInsight}">PI 12</span>
                    <span aria-hidden="true">·</span>
                    <span title="Passive Investigation" th:if="${m.passiveInvestigation > 0}"
                          th:attr="aria-label=|Passive Investigation ${m.passiveInvestigation}|"
                          th:text="'PInv ' + ${m.passiveInvestigation}">PInv 14</span>
                    <span aria-hidden="true">·</span>
                    <span title="Speed (feet per turn)" th:if="${m.speed > 0}"
                          th:attr="aria-label=|Speed ${m.speed} feet per turn|"
                          th:text="'Spd ' + ${m.speed}">Spd 30</span>
```

- [ ] **Step 7: Run the tests and watch them pass**

Run:
`./mvnw -o test -Dtest='CalendarFormatDateTest,CalendarControllerTest,SessionDraftServiceTest,PartyStatAbbreviationTest,CockpitPresentationContractTest,ThreatDicePrefillContractTest'`

Expected: PASS. The normal fantasy-calendar case and the placeholder-import case now use the
same formatter in the cockpit header, calendar page, and review draft.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/web/InGameDateFormatter.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java \
        src/main/resources/templates/calendar/_current-date.html \
        src/main/resources/templates/threat/_mechanics-card.html \
        src/main/resources/templates/encounter/_tracker.html \
        src/main/resources/static/js/combat-tracker.js \
        src/main/resources/templates/party/_summary-bar.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/calendar/CalendarFormatDateTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/party/PartyStatAbbreviationTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitPresentationContractTest.java
git commit -m "fix: humanize runtime dates, damage types, and compact party stats"
```

---

### Task 28: Run the complete acceptance and regression gate

This is the integration gate for the whole plan. It creates no production behavior; it proves
that the phase commits compose and that the surfaces §18 marked as sound remain sound.

**Files:**
- Verify only; no files change.

**Interfaces:**
- Consumes: every task in Phases A–K.
- Produces: a green targeted acceptance run, a green offline full suite, and a clean working
  tree apart from intentional plan/spec files.

- [ ] **Step 1: Run the static and service acceptance set**

Run:

```bash
./mvnw -o test -Dtest='TrackerContractTest,ConditionDefaultsTest,CockpitPresentationContractTest,SessionReviewCancelTest,EncounterXpCalculatorTest,EncounterPlacementFramingTest,EncounterRailOrderingTest,SessionDraftServiceTest,EncounterCompletionTest'
```

Expected: PASS, 0 failures.

- [ ] **Step 2: Run the cockpit browser acceptance set**

Run:

```bash
./mvnw -o test -Dtest='CockpitMapTransitionBrowserTest,TrackerIdentityBrowserTest,CockpitReferenceBrowserTest,FailureSignallingBrowserTest,EncounterDiscoverabilityBrowserTest,CockpitModuleFitTest'
```

Expected: PASS, 0 failures. Every class attaches `BrowserFailureCollector`; therefore the run
also proves zero failed HTTP responses, page errors, console errors, and console warnings.

- [ ] **Step 3: Run the preserved-surface regression set**

Run:

```bash
./mvnw -o test -Dtest='StoryRailSceneBodyContractTest,ThreatDicePrefillContractTest,EncounterPartyHpSyncTest,EncounterCompletionTest,EncounterGroupTurnTest,CoreSessionLoopSmokeTest,Library*Test'
```

Expected: PASS, including scene callouts, trap prefill, damage/bloodied/defeated state, group
survival, initiative persistence, encounter-end confirmation, draft sections, and library
statblocks.

- [ ] **Step 4: Run the full offline suite**

Run: `./mvnw -o test`

Expected: `BUILD SUCCESS`, 0 failures, 0 errors.

- [ ] **Step 5: Check the diff and repository state**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` prints nothing. `git status --short` contains only the intended
changes from this plan; no generated browser artifacts, local database files, or fixture
mutations are present.

---

## Requirement-to-task index

| Spec requirement | Implemented and proved by |
|---|---|
| A1, A2, A3 map transition, stale projections, in-app recovery | Tasks 1–3 |
| B1, B2, B3, B4, B5 identity, targeting, selection, per-row state, rail width | Tasks 4–8 |
| C1, C2 condition rules and readable badges | Tasks 9–10 |
| D1, D2, D3, D4 reference payload, full statblock, zoning, dice drawer | Tasks 11–13 |
| E1, E2, E3 review truthfulness and destructive lifecycle treatment | Tasks 14–15 |
| F1, F2, F3 validation/save distinction, toast policy, hidden errors | Tasks 16–18 |
| G1, G2, G3 visible encounter activation, scene actions, finished encounters | Tasks 19–20 |
| H1, H2 XP report/application and encounter draft evidence | Tasks 21–22 |
| I1, I2 natural ordering, current-scene foregrounding, rail meta | Task 23 |
| J1, J2, J3, J4 centred/framed placement, distinct tokens, shape identity, four layers | Tasks 24–25 |
| K1, K2, K3, K4, K5 setup/turn copy and controls | Task 26 |
| K6 themed lifecycle controls | Task 15 |
| K7 separated attention badge | Task 18 |
| K8–K9 humanized damage and in-game dates | Task 27 |
| K10 visible trap prefill affordance | Task 26 |
| K11 accessible dice close control | Task 13 |
| K12 intentional negative HP explained | Task 26 |
| K13 stable group identity | Task 4 |
| K14 contextual empty space; no requested behavior | No code change |
| K15 expanded compact party stat labels | Task 27 |
