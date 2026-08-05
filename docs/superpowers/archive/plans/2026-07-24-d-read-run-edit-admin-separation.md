# Workstream D — Read/Run versus Edit/Admin Surface Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every DMHelper page one declared job — **Read**, **Run**, **Edit** or **Admin** — so the surfaces a DM reads and runs from stop doubling as CMS forms, and lock that separation with an automated guard.

**Architecture:** No new services, no database change. Each governed full-page template declares `data-surface="read|run|edit|admin"` on its root `<main>`. Content and runtime actions stay on the Read/Run pages; authoring forms, reordering and destructive/package actions move to dedicated Edit and Admin pages that reuse the *existing* endpoints unchanged — only the htmx target ids and the fragment a handler returns change. Three surfaces are rebuilt around this split: the scene (Read rail + new structure Edit page), the encounter (preparation summary + Run action + new setup Edit page) and the party (scan-friendly roster + secondary per-row detail). The campaign home sheds its inline edit form and data tools to a new Admin page and gains explicit Run entry points. A final `SurfaceSeparationContractTest` enforces the rules so the separation cannot silently rot.

**Tech Stack:** Java 21, Spring Boot MVC, Thymeleaf, htmx, Alpine, plain CSS; JUnit 5 + AssertJ + MockMvc + Playwright; Maven (`./mvnw`).

## Global Constraints

- **No database migration and no entity change in D.** Latest Flyway migration stays `V25`. If a task appears to need a schema change, stop — it is out of scope. Rollback for every task is `git revert`; no data is transformed.
- **Server-rendered architecture is fixed** (spec §13): no SPA, no frontend package manager, no third-party UI framework. New behavior is Thymeleaf + htmx + small inline vanilla JS or existing Alpine components only.
- Production sets `spring.jpa.open-in-view=false`. Templates must not trigger lazy loading during render. Only use model attributes assembled inside a controller/service transaction; do not add new entity graph walks to templates.
- DM-sensitive blocks inside the cockpit carry `data-screen-sensitive`. Any DM content moved or re-rendered in `session/**` keeps that attribute.
- Existing route paths and request/response contracts of *endpoints* stay unchanged unless a task explicitly says otherwise. Two endpoints are added: `GET /campaigns/{id}/settings` and `POST /campaigns/{campaignId}/encounters/{id}/run`, plus `GET .../scenes/{id}/structure` and `GET .../encounters/{id}/setup`.
- Anchors `id="pm-card-<uuid>"` on party rows are load-bearing: `ContentDestinationRegistry` and `CommandPaletteService` deep-link to `/party#pm-card-<id>`. They must survive.
- Fixture content must stay synthetic. Never commit names, prose or numbers derived from a published campaign.
- Test command: `./mvnw -q test -Dtest=<ClassName>` (single method: `-Dtest=<ClassName>#<method>`). Full build: `./mvnw -q verify`.
- Commit after every task. Never mark a task complete with failing tests.

---

## Surface rules this plan implements

From spec §9.1, made concrete and testable:

| Mode | Pages | Must contain | Must not contain |
|---|---|---|---|
| **Read** | campaign home, adventure overview, scene detail, encounter detail, party roster | content, status, links to Edit/Admin, Run entry points | package/export/import actions, reorder actions, large metadata forms (`<textarea`, `name="sourceLocator"`), destructive buttons in the page header |
| **Run** | session cockpit | runtime modules only | package/export/import actions, reorder actions, prep metadata forms |
| **Edit** | scene structure, encounter setup | the authoring forms moved out of Read | — |
| **Admin** | campaign settings | import/export/delete/configuration | — |

---

## File Structure

**New templates**
- `adventure/scene-structure.html` — Edit surface for one scene: scene form, live preview, structured-content editor, scene reorder, delete.
- `adventure/_scene-structure-editor.html` — fragment `structureEditor`: every structured CRUD form (metadata, sections, checks, participants, transitions, links) plus statblock/handout unlinking and scene reorder.
- `adventure/_scene-rail.html` — fragments `sceneRail` (concise read rail + runtime actions) and `statusBadge`.
- `adventure/_scene-body.html` — fragment `sceneBody`: the rendered scene body + sections, shared by the Read page and the Edit page's preview.
- `party/_roster.html` — fragments `roster(members, campaignId)`, `row(pm)`, `rowWithSummary(pm, activeMembers)`.
- `encounter/_prep-summary.html` — fragment `prepSummary`: readable preparation summary.
- `encounter/setup.html` — Edit surface holding every raw setup control.
- `campaigns/settings.html` — Admin surface: campaign details form, package tools, delete.

**Deleted templates**
- `adventure/_action-rail.html` (split into `_scene-rail.html` + `_scene-structure-editor.html`).
- `party/_card.html` (replaced by `party/_roster.html`).

**Modified templates**
- `adventure/scene-detail.html`, `adventure/_scene-form.html`, `adventure/_scene-panel.html`, `adventure/detail.html`, `adventure/_chapter-list.html`
- `encounter/detail.html`
- `party/list.html`
- `campaigns/detail.html`
- `fragments/head.html` (link the new stylesheet)

**Modified Java**
- `adventure/web/SceneController.java` — structure page route; structured handlers return the editor fragment; read-side handlers return the rail fragment.
- `encounter/web/EncounterController.java` — setup page route, `run` action, detail model additions.
- `party/web/PartyController.java` — roster fragment returns.
- `campaign/web/CampaignController.java` — settings page route.

**New CSS**
- `src/main/resources/static/css/surfaces.css` — roster, preparation summary, surface header and organize-panel styles.

**New tests**
- `web/SurfaceModeContractTest.java`, `web/SurfaceSeparationContractTest.java`
- `adventure/web/SceneReadSurfaceTest.java`, `adventure/web/SceneStructureEditSurfaceTest.java`
- `encounter/web/EncounterPrepSummaryTest.java`, `encounter/web/EncounterSetupSurfaceTest.java`
- `party/web/PartyRosterTest.java`
- `campaign/web/CampaignAdminSurfaceTest.java`
- `support/PreparationSurfaceFixture.java`

**Modified tests**
- `adventure/web/SceneStructuredTemplateContractTest.java`, `adventure/web/SceneDetailPresentationTest.java`, `adventure/web/AdventureDetailDensityTest.java`
- `encounter/web/EncounterTemplateContractTest.java`
- `web/FullPageRenderSmokeTest.java`
- `CoreSessionLoopSmokeTest.java`

---

## Task 1: Surface-mode vocabulary and declaration guard

**Files:**
- Modify: `src/main/resources/templates/campaigns/detail.html:12`
- Modify: `src/main/resources/templates/adventure/detail.html:11`
- Modify: `src/main/resources/templates/adventure/scene-detail.html:11`
- Modify: `src/main/resources/templates/encounter/detail.html:12`
- Modify: `src/main/resources/templates/party/list.html:11`
- Modify: `src/main/resources/templates/session/cockpit.html:40`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/web/SurfaceModeContractTest.java`

**Interfaces:**
- Produces: the attribute contract `data-surface="read" | "run" | "edit" | "admin"`, declared exactly once per governed page template, on the root `<main>` element. Later tasks add `adventure/scene-structure.html` → `edit`, `encounter/setup.html` → `edit`, `campaigns/settings.html` → `admin` to the same test's map.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/web/SurfaceModeContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 9.1: every governed page states which of the four surface modes
 * it serves. The declaration is what the separation guard later keys off, so it has to be
 * present, unique and spelled correctly.
 */
class SurfaceModeContractTest {

    static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** Governed page template -> declared surface mode. Extended as D adds pages. */
    static Map<String, String> governedSurfaces() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("campaigns/detail.html", "read");
        map.put("adventure/detail.html", "read");
        map.put("adventure/scene-detail.html", "read");
        map.put("encounter/detail.html", "read");
        map.put("party/list.html", "read");
        map.put("session/cockpit.html", "run");
        return map;
    }

    @Test
    void everyGovernedPageDeclaresExactlyOneSurfaceMode() throws IOException {
        for (Map.Entry<String, String> entry : governedSurfaces().entrySet()) {
            String html = Files.readString(TEMPLATES.resolve(entry.getKey()));
            assertThat(occurrences(html, "data-surface=\""))
                    .as("%s must declare exactly one data-surface", entry.getKey())
                    .isEqualTo(1);
            assertThat(html)
                    .as("%s must declare data-surface=\"%s\"", entry.getKey(), entry.getValue())
                    .contains("data-surface=\"" + entry.getValue() + "\"");
        }
    }

    @Test
    void theDeclarationSitsOnTheRootMainElement() throws IOException {
        for (String template : governedSurfaces().keySet()) {
            String html = Files.readString(TEMPLATES.resolve(template));
            int mainAt = html.indexOf("<main");
            int surfaceAt = html.indexOf("data-surface=\"");
            assertThat(mainAt).as("%s must have a <main> element", template).isGreaterThan(-1);
            assertThat(surfaceAt)
                    .as("%s must declare the surface on its <main>, not deeper in the page", template)
                    .isBetween(mainAt, mainAt + 200);
        }
    }

    static int occurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=SurfaceModeContractTest`
Expected: FAIL — `campaigns/detail.html must declare exactly one data-surface expected: 1 but was: 0`.

- [ ] **Step 3: Declare the surface on each governed page**

In `campaigns/detail.html`, `adventure/detail.html`, `adventure/scene-detail.html`, `encounter/detail.html` and `party/list.html`, replace the opening tag:

```html
<main class="app-main">
```

with:

```html
<main class="app-main" data-surface="read">
```

In `session/cockpit.html`, change:

```html
<main class="session-cockpit"
```

to:

```html
<main class="session-cockpit" data-surface="run"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=SurfaceModeContractTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates src/test/java/dev/hendrikhoemberg/dmhelper/web/SurfaceModeContractTest.java
git commit -m "feat(surfaces): declare read/run surface modes on governed pages"
```

---

## Task 2: Preparation-surface test fixture

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PreparationSurfaceFixture.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PreparationSurfaceFixtureTest.java`

**Interfaces:**
- Produces: `@Component PreparationSurfaceFixture` with `Seeded seed()` returning
  `record Seeded(UUID campaignId, UUID adventureId, UUID chapterId, UUID sceneId, UUID encounterId, UUID woundedMemberId, UUID inactiveMemberId)`
  and the public constants `WOUNDED_MEMBER`, `WOUNDED_PLAYER`, `HEALTHY_MEMBER`, `INACTIVE_MEMBER`, `CONDITION_NAME`, `ENCOUNTER_NAME`, `MONSTER_NAME`, `PREP_TACTICS`, `PREP_SCENE_KEY`, `REWARD_XP_TOTAL`.
- Consumes: `CampaignRepository`, `AdventureService`, `PartyMemberService`, `EncounterService`.
- Used by Tasks 3, 8 and 9.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/PreparationSurfaceFixtureTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PreparationSurfaceFixtureTest {

    @Autowired private PreparationSurfaceFixture fixture;
    @Autowired private PartyMemberService party;
    @Autowired private EncounterService encounters;

    @Test
    void seedsARosterWithLiveStateAndAPlannedEncounter() {
        PreparationSurfaceFixture.Seeded seeded = fixture.seed();

        var members = party.findByCampaignId(seeded.campaignId());
        assertThat(members).hasSize(4);
        assertThat(party.findActiveByCampaignId(seeded.campaignId())).hasSize(3);

        var wounded = party.findById(seeded.woundedMemberId());
        assertThat(wounded.getCurrentHp()).isLessThan(wounded.getMaxHp());
        assertThat(wounded.getConditions()).contains(PreparationSurfaceFixture.CONDITION_NAME);

        var encounter = encounters.getById(seeded.encounterId());
        assertThat(encounter.status()).isEqualTo("PLANNED");
        assertThat(encounters.getCombatants(seeded.encounterId())).hasSize(3);
        assertThat(encounters.getPrep(seeded.encounterId()).tactics())
                .isEqualTo(PreparationSurfaceFixture.PREP_TACTICS);
        assertThat(encounters.getRewards(seeded.encounterId()).xpTotal())
                .isEqualTo(PreparationSurfaceFixture.REWARD_XP_TOTAL);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=PreparationSurfaceFixtureTest`
Expected: FAIL — compilation error, `PreparationSurfaceFixture` does not exist.

- [ ] **Step 3: Write the fixture**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/PreparationSurfaceFixture.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPrep;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterRewards;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService.PartyLiveStateDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * A campaign shaped like a prepared session: a roster with live state worth scanning and one
 * PLANNED encounter with combatants, preparation notes and rewards. Everything is invented —
 * no published-campaign content may enter this file.
 */
@Component
public class PreparationSurfaceFixture {

    public record Seeded(
            UUID campaignId,
            UUID adventureId,
            UUID chapterId,
            UUID sceneId,
            UUID encounterId,
            UUID woundedMemberId,
            UUID inactiveMemberId) {}

    public static final String WOUNDED_MEMBER = "Aral Quickfoot";
    public static final String WOUNDED_PLAYER = "Sam";
    public static final String HEALTHY_MEMBER = "Bryn Stonehand";
    public static final String THIRD_MEMBER = "Cora Vale";
    public static final String INACTIVE_MEMBER = "Dain Underbough";
    public static final String CONDITION_NAME = "poisoned";

    public static final String ENCOUNTER_NAME = "Gate Watch Ambush";
    public static final String MONSTER_NAME = "Hooded Ambusher";
    public static final String PREP_TACTICS =
            "The ambushers loose one volley from the gatehouse roof, then drop to the courtyard.";
    public static final String PREP_MORALE = "They break once two of them are down.";
    public static final String PREP_ENVIRONMENT = "Dim light, wet cobbles, 10-foot gatehouse roof.";
    public static final String PREP_SCENE_KEY = "GW1";
    public static final int REWARD_XP_TOTAL = 450;

    private final CampaignRepository campaigns;
    private final AdventureService adventures;
    private final PartyMemberService party;
    private final EncounterService encounters;

    public PreparationSurfaceFixture(CampaignRepository campaigns,
                                     AdventureService adventures,
                                     PartyMemberService party,
                                     EncounterService encounters) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.party = party;
        this.encounters = encounters;
    }

    @Transactional
    public Seeded seed() {
        Campaign campaign = new Campaign();
        campaign.setName("Preparation Surface Fixture");
        campaign.setDescription("Roster and prepared encounter for surface tests");
        UUID campaignId = campaigns.save(campaign).getId();

        Adventure adventure = adventures.createAdventure(
                campaignId, "The Gate Watch", "Single-chapter fixture", "Fixture, p. 1");
        Chapter chapter = adventures.createChapter(
                adventure.getId(), "Chapter One", "Opening chapter.");
        Scene scene = adventures.createScene(
                chapter.getId(), "The Gatehouse", PREP_SCENE_KEY, "A squat stone gatehouse.");

        PartyMember wounded = party.create(campaignId, WOUNDED_MEMBER, WOUNDED_PLAYER,
                "Rogue 3", 15, 24, 4, 30, 14, 12, 13, null);
        party.updateLiveState(wounded.getId(), new PartyLiveStateDto(
                0, false, 0, 0, 0, null, "[\"" + CONDITION_NAME + "\"]", 9, 24));

        PartyMember healthy = party.create(campaignId, HEALTHY_MEMBER, "Kim",
                "Fighter 3", 18, 30, 1, 30, 12, 11, 10, null);
        party.updateLiveState(healthy.getId(), new PartyLiveStateDto(
                5, false, 0, 0, 0, null, "[]", 30, 30));

        party.create(campaignId, THIRD_MEMBER, "Jo",
                "Cleric 3", 16, 22, 2, 30, 13, 15, 11, null);

        PartyMember inactive = party.create(campaignId, INACTIVE_MEMBER, "Lee",
                "Wizard 2", 12, 14, 2, 30, 11, 10, 16, null);
        party.setActive(inactive.getId(), false);

        UUID encounterId = encounters.create(campaignId, new CreateRequest(ENCOUNTER_NAME, null)).id();
        encounters.addCombatant(encounterId,
                new CombatantCreateRequest(MONSTER_NAME + " A", 16, "MONSTER", null, null, null));
        encounters.addCombatant(encounterId,
                new CombatantCreateRequest(MONSTER_NAME + " B", 16, "MONSTER", null, null, null));
        encounters.addCombatant(encounterId,
                new CombatantCreateRequest(WOUNDED_MEMBER, 24, "PC", null, null, wounded.getId()));

        encounters.updatePrep(encounterId, new EncounterPrep(
                PREP_TACTICS, PREP_MORALE, "They surrender if surrounded.",
                PREP_ENVIRONMENT, "Fixture, p. 4", "Add one ambusher per extra character.",
                PREP_SCENE_KEY));
        encounters.updateRewards(encounterId, new EncounterRewards(
                REWARD_XP_TOTAL, 150, List.of(), List.of(), List.of(),
                "Split the purse between the watch and the party."));

        return new Seeded(campaignId, adventure.getId(), chapter.getId(), scene.getId(),
                encounterId, wounded.getId(), inactive.getId());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q test -Dtest=PreparationSurfaceFixtureTest`
Expected: PASS.

If `updateLiveState` rejects a `currentHp` above `maxHp` or normalises differently, read `PartyMemberService.updateLiveState` and adjust the DTO values — do not change the assertions in the test.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/support/
git commit -m "test(surfaces): add a prepared-session fixture for surface tests"
```

---

## Task 3: Party roster replaces the sparse card grid

Spec §9.2: *"Party: replace tall sparse cards with a scan-friendly roster showing name, player, AC, HP, passives and relevant conditions; edit details remain secondary."*

**Files:**
- Create: `src/main/resources/templates/party/_roster.html`
- Create: `src/main/resources/static/css/surfaces.css`
- Delete: `src/main/resources/templates/party/_card.html`
- Modify: `src/main/resources/templates/party/list.html`
- Modify: `src/main/resources/templates/fragments/head.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java:77,96,105`
- Modify: `src/main/resources/static/css/components.css:19,1933`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java:539`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyRosterTest.java`

**Interfaces:**
- Consumes: `PreparationSurfaceFixture.Seeded` (Task 2).
- Produces: `party/_roster.html` fragments `roster(members, campaignId)`, `row(pm)` and `rowWithSummary(pm, activeMembers)`. `PartyController.create/update/toggleActive` return `"party/_roster :: rowWithSummary"`. Each row is `<details id="pm-card-<uuid>" class="roster-row">` whose `<summary>` carries name, player, AC, HP, PP/PI/PInv and condition badges, and whose body carries Sheet/Edit/Activate/Remove plus the quick-notes strip.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/party/web/PartyRosterTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.party.web;

import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartyRosterTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PreparationSurfaceFixture fixture;

    private PreparationSurfaceFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{c}/party", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void everyMemberIsOneScannableRow() {
        assertThat(body).contains("class=\"roster\"");
        // One row per member, identified by its stable deep-link anchor.
        assertThat(occurrences(body, "id=\"pm-card-")).isEqualTo(4);
    }

    @Test
    void aRowShowsTheDecisionFieldsWithoutOpeningAnything() {
        String row = rowFor(seeded.woundedMemberId());
        String summary = row.substring(row.indexOf("<summary"), row.indexOf("</summary>"));

        assertThat(summary).contains(PreparationSurfaceFixture.WOUNDED_MEMBER);
        assertThat(summary).contains(PreparationSurfaceFixture.WOUNDED_PLAYER);
        assertThat(summary).as("AC").contains(">15<");
        assertThat(summary).as("current and maximum HP").contains("9/24");
        assertThat(summary).as("passive perception").contains(">14<");
        assertThat(summary).as("passive insight").contains(">12<");
        assertThat(summary).as("passive investigation").contains(">13<");
        assertThat(summary).as("relevant conditions belong on the row")
                .contains(PreparationSurfaceFixture.CONDITION_NAME);
    }

    @Test
    void temporaryHitPointsAreVisibleOnTheRow() {
        String row = rowForName(PreparationSurfaceFixture.HEALTHY_MEMBER);
        assertThat(row).contains("+5");
    }

    @Test
    void editingAndRemovingAreSecondaryNotOnTheRow() {
        String row = rowFor(seeded.woundedMemberId());
        String summary = row.substring(row.indexOf("<summary"), row.indexOf("</summary>"));

        assertThat(summary).as("the row itself stays scannable").doesNotContain("Remove");
        assertThat(summary).doesNotContain("hx-delete");
        assertThat(row).as("but the actions are still reachable once expanded").contains("Remove");
        assertThat(row).contains("quicknotes-strip");
    }

    @Test
    void deepLinkAnchorsSurvive() {
        assertThat(body)
                .as("ContentDestinationRegistry and the command palette link to /party#pm-card-<id>")
                .contains("id=\"pm-card-" + seeded.woundedMemberId() + "\"");
    }

    @Test
    void inactiveMembersAreMarkedRatherThanHidden() {
        String row = rowFor(seeded.inactiveMemberId());
        assertThat(row).contains("roster-row--inactive");
    }

    @Test
    void bulkActionsAreBehindADisclosure() {
        int bulkAt = body.indexOf("data-bulk-actions");
        assertThat(bulkAt).as("bulk controls must exist").isGreaterThan(-1);
        assertThat(body.substring(Math.max(0, bulkAt - 200), bulkAt))
                .as("seven bulk buttons must not compete with the roster")
                .contains("<details");
    }

    private String rowFor(java.util.UUID memberId) {
        int start = body.indexOf("id=\"pm-card-" + memberId + "\"");
        assertThat(start).as("row for %s", memberId).isGreaterThan(-1);
        int rowStart = body.lastIndexOf("<details", start);
        int rowEnd = body.indexOf("</details>", start);
        return body.substring(rowStart, rowEnd);
    }

    private String rowForName(String memberName) {
        int nameAt = body.indexOf(memberName);
        assertThat(nameAt).as("row for %s", memberName).isGreaterThan(-1);
        int rowStart = body.lastIndexOf("<details", nameAt);
        int rowEnd = body.indexOf("</details>", nameAt);
        return body.substring(rowStart, rowEnd);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=PartyRosterTest`
Expected: FAIL — `everyMemberIsOneScannableRow` reports `class="roster"` missing; the page still renders `party-member-card`.

- [ ] **Step 3: Write the roster template**

Create `src/main/resources/templates/party/_roster.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="roster(members, campaignId)" id="party-roster" class="roster">
  <div class="roster__head">
    <span class="roster__col roster__col--select"></span>
    <span class="roster__col roster__col--name">Character</span>
    <span class="roster__col roster__col--player">Player</span>
    <span class="roster__col roster__col--num">AC</span>
    <span class="roster__col roster__col--hp">HP</span>
    <span class="roster__col roster__col--num" title="Passive Perception">PP</span>
    <span class="roster__col roster__col--num" title="Passive Insight">PI</span>
    <span class="roster__col roster__col--num" title="Passive Investigation">PInv</span>
    <span class="roster__col roster__col--conditions">Conditions</span>
  </div>
  <th:block th:each="pm : ${members}">
    <th:block th:replace="~{party/_roster :: row(pm=${pm})}"></th:block>
  </th:block>
</div>

<!-- One member per row. The summary carries every field a DM decides on mid-session; the
     body carries the prep-time actions, which must not compete with scanning. -->
<details class="roster-row" th:fragment="row(pm)"
         th:id="'pm-card-' + ${pm.id}"
         th:classappend="${!pm.active} ? 'roster-row--inactive'"
         th:attr="data-active=${pm.active}">
  <summary class="roster-row__summary">
    <span class="roster__col roster__col--select">
      <!-- Inside <summary>, a bare click would toggle the disclosure instead of selecting. -->
      <input type="checkbox" class="member-select" th:value="${pm.id}"
             th:attr="data-active=${pm.active}" th:id="'sel-' + ${pm.id}"
             onclick="event.stopPropagation()">
    </span>
    <span class="roster__col roster__col--name" th:text="${pm.characterName}">Character</span>
    <span class="roster__col roster__col--player">
      <span class="roster-row__player" th:if="${pm.playerName != null and !pm.playerName.isBlank()}"
            th:text="${pm.playerName}">Player</span>
      <span class="roster-row__class" th:if="${pm.classAndLevel != null and !pm.classAndLevel.isBlank()}"
            th:text="${pm.classAndLevel}">Class</span>
    </span>
    <span class="roster__col roster__col--num" th:text="${pm.ac}">15</span>
    <span class="roster__col roster__col--hp">
      <span class="roster-row__hp-text" th:text="${pm.currentHp} + '/' + ${pm.maxHp}">9/24</span>
      <span class="roster-row__hp-temp" th:if="${pm.tempHp > 0}"
            th:text="'+' + ${pm.tempHp}">+5</span>
      <span class="roster-row__hp-bar">
        <span class="roster-row__hp-fill"
              th:style="'width:' + (${pm.maxHp} > 0 ? (${pm.currentHp} * 100 / ${pm.maxHp}) : 0) + '%'"
              th:classappend="${pm.maxHp > 0 and pm.currentHp * 2 < pm.maxHp} ? 'is-low'"></span>
      </span>
    </span>
    <span class="roster__col roster__col--num" th:text="${pm.passivePerception}">14</span>
    <span class="roster__col roster__col--num" th:text="${pm.passiveInsight}">12</span>
    <span class="roster__col roster__col--num" th:text="${pm.passiveInvestigation}">13</span>
    <span class="roster__col roster__col--conditions">
      <span class="badge badge-warning" th:each="cond : ${pm.conditions}" th:text="${cond}">condition</span>
      <span class="badge badge-danger"
            th:if="${pm.deathSaveSuccesses > 0 or pm.deathSaveFailures > 0}"
            th:text="'Death saves ' + ${pm.deathSaveSuccesses} + '/' + ${pm.deathSaveFailures}">Death saves</span>
      <span class="badge" th:if="${pm.concentratingOn != null and !pm.concentratingOn.isBlank()}"
            th:text="'Conc: ' + ${pm.concentratingOn}">Concentrating</span>
      <span class="badge" th:if="${!pm.active}">Inactive</span>
    </span>
  </summary>

  <div class="roster-row__detail">
    <div class="roster-row__meta">
      <span th:text="'Initiative +' + ${pm.initiativeBonus}">Initiative +4</span>
      <span th:text="'Speed ' + ${pm.speed} + ' ft.'">Speed 30 ft.</span>
      <span th:if="${pm.exhaustion > 0}" th:text="'Exhaustion ' + ${pm.exhaustion}">Exhaustion 1</span>
    </div>
    <div class="roster-row__actions">
      <a th:if="${pm.characterSheet != null}"
         th:href="@{/campaigns/{cid}/party/{pid}/sheet(cid=${pm.campaign.id}, pid=${pm.id})}"
         class="btn btn-ghost">Sheet</a>
      <button class="btn btn-ghost"
              th:hx-get="@{/campaigns/{cid}/party/{pid}/edit(cid=${pm.campaign.id}, pid=${pm.id})}"
              hx-target="#party-form-modal" hx-swap="innerHTML">Edit</button>
      <button class="btn btn-ghost"
              th:hx-put="@{/campaigns/{cid}/party/{pid}/toggle-active(cid=${pm.campaign.id}, pid=${pm.id})}"
              hx-target="closest .roster-row" hx-swap="outerHTML"
              th:text="${pm.active} ? 'Mark inactive' : 'Mark active'">Mark inactive</button>
      <span class="roster-row__destructive">
        <button class="btn btn-danger"
                th:hx-delete="@{/campaigns/{cid}/party/{pid}(cid=${pm.campaign.id}, pid=${pm.id})}"
                hx-confirm="Remove this party member?"
                hx-target="closest .roster-row" hx-swap="outerHTML">Remove</button>
      </span>
    </div>
    <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${pm.campaign.id}, targetType='PARTY_MEMBER', targetId=${pm.id})}"></th:block>
  </div>
</details>

<!-- Row plus an out-of-band refresh of the summary bar, kept in sync after add/edit/toggle. -->
<th:block th:fragment="rowWithSummary(pm, activeMembers)">
  <th:block th:replace="~{party/_roster :: row(pm=${pm})}"></th:block>
  <th:block th:replace="~{party/_summary-bar :: summary-bar(view=${activeMembers}, mode=${null}, oob='true')}"></th:block>
</th:block>
</html>
```

- [ ] **Step 4: Rewrite the party page around the roster**

In `src/main/resources/templates/party/list.html`, replace everything from the `<div class="batch-bar" id="batch-bar">` opening (line 30) through the closing `</div>` of `#party-grid` (line 62) with:

```html
            <details class="party-bulk">
              <summary>Bulk actions</summary>
              <div class="batch-bar" id="batch-bar" data-bulk-actions>
                <div class="batch-bar-left">
                    <label class="batch-toggle">
                        <input type="checkbox" id="select-all" onchange="toggleSelectAll(this)">
                        Select All
                    </label>
                    <label class="batch-toggle batch-toggle-inactive">
                        <input type="checkbox" id="include-inactive" onchange="toggleInactive()">
                        Include inactive
                    </label>
                    <span class="batch-count" id="batch-count">0 selected</span>
                </div>
                <div class="batch-bar-actions">
                    <button class="btn btn-xs batch-btn" onclick="batchRest('SHORT')" title="Short Rest">Short Rest</button>
                    <button class="btn btn-xs batch-btn" onclick="batchRest('LONG')" title="Long Rest">Long Rest</button>
                    <button class="btn btn-xs batch-btn" onclick="batchXp()">Award XP</button>
                    <button class="btn btn-xs batch-btn" onclick="batchMilestone()">Set Level</button>
                    <button class="btn btn-xs batch-btn" onclick="batchCondition()">Condition</button>
                    <button class="btn btn-xs batch-btn" onclick="batchLoot()">Assign Loot</button>
                    <button class="btn btn-xs batch-btn" onclick="batchClearDeathSaves()">Clear Death Saves</button>
                </div>
              </div>
            </details>

            <th:block th:if="${members == null or members.isEmpty()}">
                <th:block th:replace="~{common/_empty-state :: empty-state-with-icon(
                    'No heroes yet', 'Add member', @{/campaigns/{id}/party/new(id=${campaign.id})},
                    'Add the player characters so combat, passive senses, and difficulty math work.', '♜')}"></th:block>
            </th:block>
            <th:block th:unless="${members == null or members.isEmpty()}">
                <th:block th:replace="~{party/_roster :: roster(members=${members}, campaignId=${campaign.id})}"></th:block>
            </th:block>
```

Then, in the same file's `<script>` block, replace the body of `toggleInactive()`:

```javascript
function toggleInactive() {
    const show = document.getElementById('include-inactive').checked;
    document.querySelectorAll('.roster-row--inactive').forEach(c => {
        c.style.display = show ? '' : 'none';
    });
    updateBatchCount();
}
```

and append to the existing `DOMContentLoaded` handler, immediately after `toggleInactive();`:

```javascript
    // Deep links land on /party#pm-card-<id>; a collapsed row would hide the target.
    if (location.hash.startsWith('#pm-card-')) {
        const target = document.querySelector(location.hash);
        if (target && target.tagName === 'DETAILS') {
            target.open = true;
            target.scrollIntoView({ block: 'center' });
        }
    }
```

- [ ] **Step 5: Point the controller at the roster fragment**

In `src/main/java/dev/hendrikhoemberg/dmhelper/party/web/PartyController.java`, change all three occurrences of

```java
        return "party/_card :: cardWithSummary";
```

to

```java
        return "party/_roster :: rowWithSummary";
```

(lines 77, 96 and 105 — `create`, `update`, `toggleActive`).

Then delete the obsolete template:

```bash
git rm src/main/resources/templates/party/_card.html
```

- [ ] **Step 6: Add the stylesheet**

Create `src/main/resources/static/css/surfaces.css`:

```css
/* Read/Run surface furniture: dense, scannable, quiet. Decoration must never win over
   the field a DM is looking for. Spec 2026-07-22 section 9. */

.roster {
    display: grid;
    grid-template-columns:
        2rem minmax(9rem, 1.4fr) minmax(7rem, 1fr)
        3rem 7rem 3rem 3rem 4rem minmax(6rem, 1fr);
    align-items: center;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    overflow: hidden;
}

.roster__head,
.roster-row__summary {
    display: grid;
    grid-column: 1 / -1;
    grid-template-columns: subgrid;
    align-items: center;
    gap: var(--space-sm);
    padding: var(--space-xs) var(--space-sm);
}

.roster__head {
    font-size: var(--text-xs);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--color-text-muted);
    background: var(--color-surface-2, rgba(255, 255, 255, 0.03));
}

.roster-row {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: subgrid;
    border-top: 1px solid var(--color-border);
}

.roster-row__summary {
    cursor: pointer;
    list-style: none;
}

.roster-row__summary::-webkit-details-marker { display: none; }
.roster-row[open] > .roster-row__summary,
.roster-row__summary:hover { background: var(--color-surface-2, rgba(255, 255, 255, 0.04)); }

.roster__col { min-width: 0; }
.roster__col--name { font-weight: 600; }
.roster__col--player { font-size: var(--text-sm); color: var(--color-text-muted); }
.roster-row__class::before { content: " · "; }

/* Combat numbers are read across a table, so they get tabular figures. */
.roster__col--num,
.roster-row__hp-text {
    font-variant-numeric: tabular-nums;
    text-align: right;
}

.roster__col--hp { display: flex; align-items: center; gap: var(--space-xxs); }
.roster-row__hp-temp { font-size: var(--text-xs); color: var(--color-info, #7fb2e5); }
.roster-row__hp-bar {
    flex: 1;
    height: 4px;
    min-width: 1.5rem;
    background: var(--color-border);
    border-radius: 2px;
    overflow: hidden;
}
.roster-row__hp-fill { display: block; height: 100%; background: var(--color-success, #5aa469); }
.roster-row__hp-fill.is-low { background: var(--color-danger, #b3453e); }

.roster__col--conditions { display: flex; flex-wrap: wrap; gap: var(--space-xxs); }

.roster-row--inactive > .roster-row__summary { opacity: 0.55; }

.roster-row__detail {
    grid-column: 1 / -1;
    padding: var(--space-sm) var(--space-sm) var(--space-md) 2.5rem;
    border-top: 1px dashed var(--color-border);
}
.roster-row__meta {
    display: flex;
    gap: var(--space-md);
    font-size: var(--text-sm);
    color: var(--color-text-muted);
    margin-bottom: var(--space-xs);
}
.roster-row__actions {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    margin-bottom: var(--space-sm);
}
/* A destructive action must never sit flush against the common one. */
.roster-row__destructive { margin-left: auto; padding-left: var(--space-lg); }

.party-bulk { margin-bottom: var(--space-md); }
.party-bulk > summary { cursor: pointer; color: var(--color-text-muted); font-size: var(--text-sm); }

@media (max-width: 900px) {
    .roster { grid-template-columns: 2rem minmax(8rem, 1.4fr) 3rem 6rem minmax(5rem, 1fr); }
    .roster__head .roster__col--player,
    .roster-row__summary .roster__col--player,
    .roster__head .roster__col--num:nth-of-type(n + 2),
    .roster-row__summary .roster__col--num:nth-of-type(n + 2) { display: none; }
}
```

In `src/main/resources/templates/fragments/head.html`, add after the `book.css` link:

```html
    <link rel="stylesheet" th:href="@{/css/surfaces.css}">
```

In `src/main/resources/static/css/components.css`, update the two empty-state rules that name the deleted card class:

- line 19: `.card-grid:has(.party-member-card) .empty-state {` → `.card-grid:has(.roster-row) .empty-state {`
- line 1933: `.empty-state-host:has(> .card, > .party-member-card) {` → `.empty-state-host:has(> .card, > .roster-row) {`

- [ ] **Step 7: Update the browser smoke test**

In `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`, in `quickNotesWorkOnAFirstPartyMemberInsertedByHtmx`, replace

```java
        Locator card = dmPage.locator(".party-member-card", new Page.LocatorOptions().setHasText("Dynamic Hero"));
        card.waitFor();
```

with

```java
        Locator card = dmPage.locator(".roster-row", new Page.LocatorOptions().setHasText("Dynamic Hero"));
        card.waitFor();
        // Prep-time actions are secondary now: open the row before reaching its quick notes.
        card.locator("summary").click();
        card.locator(".quicknotes-form").waitFor();
```

- [ ] **Step 8: Run the tests**

Run: `./mvnw -q test -Dtest=PartyRosterTest`
Expected: PASS (7 tests).

Run: `./mvnw -q test -Dtest=PartyControllerTest`
Expected: PASS — its assertions are content-based (`Thia`, the form modal id) and survive the roster.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/party src/main/resources/templates/fragments/head.html \
        src/main/resources/static/css src/main/java/dev/hendrikhoemberg/dmhelper/party \
        src/test/java/dev/hendrikhoemberg/dmhelper/party src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat(party): replace sparse cards with a scan-friendly roster"
```

---

## Task 4: Campaign Admin surface and a Read-only campaign home

Spec §9.1: *"Admin: import/export, deletion, ordering, source metadata and campaign configuration."* §9.2: *"Campaign home: retain its strong hierarchy while adding readiness status and direct Run entry points; do not turn it back into an inline edit form."*

**Files:**
- Create: `src/main/resources/templates/campaigns/settings.html`
- Modify: `src/main/resources/templates/campaigns/detail.html:20-23,125-194`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/web/SurfaceModeContractTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignAdminSurfaceTest.java`

**Interfaces:**
- Consumes: `CampaignService`, `AdventureService.getCurrentScene(UUID)`, `EncounterRepository.findByCampaignIdAndStatus(...)` — see Step 4 for the exact model attributes.
- Produces: `GET /campaigns/{id}/settings` → view `campaigns/settings`, model attributes `campaign`, `campaignId`. Campaign home gains a `dash-run` block with `data-run-entry` markers.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignAdminSurfaceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CampaignAdminSurfaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PreparationSurfaceFixture fixture;

    private MockMvc mvc;
    private PreparationSurfaceFixture.Seeded seeded;
    private String home;
    private String settings;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        home = mvc.perform(get("/campaigns/{id}", seeded.campaignId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        settings = mvc.perform(get("/campaigns/{id}/settings", seeded.campaignId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theHomeCarriesNoAdminTooling() {
        assertThat(home).doesNotContain("Export Campaign Package");
        assertThat(home).doesNotContain("Import Campaign Package");
        assertThat(home).doesNotContain("Delete Campaign");
        assertThat(home).as("no inline campaign edit form on the read surface")
                .doesNotContain("id=\"editDescription\"");
    }

    @Test
    void theHomeLinksToTheAdminSurface() {
        assertThat(home).contains("/campaigns/" + seeded.campaignId() + "/settings");
    }

    @Test
    void theAdminSurfaceCarriesEveryToolThatLeftTheHome() {
        assertThat(settings).contains("data-surface=\"admin\"");
        assertThat(settings).contains("Export Campaign Package");
        assertThat(settings).contains("Import Campaign Package");
        assertThat(settings).contains("Delete Campaign");
        assertThat(settings).contains("id=\"editDescription\"");
        assertThat(settings).as("the import dialog must travel with its button")
                .contains("campaignImport");
    }

    @Test
    void theHomeOffersDirectRunEntryPoints() {
        assertThat(home).contains("data-run-entry=\"session\"");
        assertThat(home).contains("/campaigns/" + seeded.campaignId() + "/session");
    }

    @Test
    void theHomeStillShowsReadinessAndScale() {
        assertThat(home).contains("readiness-panel");
        assertThat(home).contains("scale-panel");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=CampaignAdminSurfaceTest`
Expected: FAIL — `GET /campaigns/{id}/settings` returns 404 (`status expected:<200> but was:<404>`).

- [ ] **Step 3: Add the settings route**

In `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`, add after the `detail` method (line 132):

```java
    /**
     * Admin surface. Package tools, campaign configuration and deletion live here so the
     * campaign home can stay a reading and running surface (spec 2026-07-22 section 9.1).
     */
    @GetMapping("/{id}/settings")
    public String settings(@PathVariable UUID id, Model model) {
        model.addAttribute("campaign", service.findById(id));
        model.addAttribute("campaignId", id);
        return "campaigns/settings";
    }
```

- [ ] **Step 4: Create the Admin page**

Create `src/main/resources/templates/campaigns/settings.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="|Settings — ${campaign.name}|">DMHelper — Campaign settings</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>

    <div class="app-shell">
        <th:block th:replace="~{fragments/_appnav :: appnav}"></th:block>
        <main class="app-main" data-surface="admin">
            <div class="page-header">
                <div>
                    <div class="breadcrumb">
                        <a th:href="@{/campaigns/{id}(id=${campaign.id})}" th:text="${campaign.name}">Campaign</a>
                        <span> / </span>
                        <span>Settings</span>
                    </div>
                    <h1>Campaign settings</h1>
                    <div class="rule-taper rule-taper--gold"></div>
                </div>
                <div class="page-header-actions">
                    <a th:href="@{/campaigns/{id}(id=${campaign.id})}" class="btn btn-ghost">&larr; Back to campaign</a>
                </div>
            </div>

            <section class="detail-section">
                <h2>Details</h2>
                <form th:hx-put="@{/campaigns/{id}(id=${campaign.id})}"
                      hx-target="body"
                      hx-swap="outerHTML">
                    <div class="form-group">
                        <label for="editName">Name</label>
                        <input type="text" id="editName" name="name" required
                               th:value="${campaign.name}">
                    </div>
                    <div class="form-group">
                        <label for="editDescription">Description</label>
                        <textarea id="editDescription" name="description"
                                  th:text="${campaign.description}"></textarea>
                    </div>
                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">Save changes</button>
                    </div>
                </form>
            </section>

            <section class="detail-section">
                <h2>Data &amp; packages</h2>
                <div class="dash-data-tools__body">
                    <div class="export-package-group">
                        <form th:action="@{/campaigns/{id}/package(id=${campaign.id})}" method="GET">
                            <input type="hidden" name="includeCombatLog" value="true" id="includeCombatLog">
                            <input type="hidden" name="includeDiceHistory" value="true" id="includeDiceHistory">
                            <label class="checkbox-label">
                                <input type="checkbox" checked
                                       onchange="document.getElementById('includeCombatLog').value=this.checked">
                                Combat Log
                            </label>
                            <label class="checkbox-label">
                                <input type="checkbox" checked
                                       onchange="document.getElementById('includeDiceHistory').value=this.checked">
                                Dice History
                            </label>
                            <button type="submit" class="btn">Export Campaign Package</button>
                        </form>
                    </div>
                    <a th:href="@{/campaigns/{id}/export(id=${campaign.id})}" class="btn" download>Export Legacy v1 JSON</a>
                    <button class="btn"
                            onclick="Alpine.$data(document.querySelector('[x-data=\'campaignImport\']')).openDialog()">
                        Import Campaign Package
                    </button>
                    <form hx-post="/campaigns/import"
                          hx-encoding="multipart/form-data"
                          class="u-contents">
                        <label class="btn">
                            Import Legacy v1 JSON
                            <input type="file" name="file" accept=".dmcampaign.json,application/json"
                                   onchange="this.form.requestSubmit()"
                                   class="hidden">
                        </label>
                    </form>
                </div>
            </section>

            <section class="detail-section surface-danger-zone">
                <h2>Danger zone</h2>
                <p class="text-muted u-text-sm">Deleting a campaign removes its adventures, encounters, notes and session history.</p>
                <button class="btn btn-danger"
                        th:hx-delete="@{/campaigns/{id}(id=${campaign.id})}"
                        hx-confirm="Delete this campaign?"
                        hx-target="body"
                        hx-swap="outerHTML">
                    Delete Campaign
                </button>
            </section>
        </main>
    </div>
    <th:block th:replace="~{campaigns/_import-dialog :: import-dialog}"></th:block>
</body>
</html>
```

- [ ] **Step 5: Strip the home and add Run entry points**

In `src/main/resources/templates/campaigns/detail.html`:

1. Delete the entire `<details class="dash-edit u-mt-lg">` block (lines 125–144) and the entire `<details class="dash-data-tools u-mt-lg">` block (lines 146–191).
2. Delete the trailing import-dialog include (line 194): `<th:block th:replace="~{campaigns/_import-dialog :: import-dialog}"></th:block>`.
3. Replace the `dash-actions` block (lines 20–23) with:

```html
            <div class="dash-actions dash-run u-mb-lg">
                <a class="btn btn-primary" data-run-entry="session"
                   th:href="@{/campaigns/{id}/session(id=${campaign.id})}">Run session</a>
                <a class="btn" data-run-entry="scene"
                   th:if="${currentScene != null}"
                   th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}(cid=${campaign.id},aid=${currentScene.chapter.adventure.id},sid=${currentScene.id})}"
                   th:text="|Current scene: ${currentScene.title}|">Current scene</a>
                <a class="btn" data-run-entry="encounter"
                   th:if="${activeEncounterId != null}"
                   th:href="@{/campaigns/{cid}/encounters/{eid}(cid=${campaign.id},eid=${activeEncounterId})}">Active encounter</a>
                <a class="btn btn-ghost" th:href="@{/campaigns/{id}/adventures(id=${campaign.id})}">Plan adventures</a>
                <a class="btn btn-ghost" th:href="@{/campaigns/{id}/settings(id=${campaign.id})}">Settings</a>
            </div>
```

- [ ] **Step 6: Feed the new home model attributes**

In `CampaignController`, add the two collaborators. Add imports:

```java
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
```

Add the fields and constructor parameters (append them to the existing constructor signature and assignments):

```java
    private final AdventureService adventureService;
    private final EncounterRepository encounterRepository;
```

Then add this private helper below `update` (leave the rest of both methods alone — the only change is one extra call in each):

```java
    /**
     * Run entry points for the home. Both are optional: a fresh campaign has neither a current
     * scene nor an active encounter, and the home must simply omit the links in that case.
     */
    private void addRunEntryPoints(UUID campaignId, Model model) {
        adventureService.getCurrentScene(campaignId)
                .ifPresent(scene -> model.addAttribute("currentScene", scene));
        encounterRepository.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE)
                .ifPresent(encounter -> model.addAttribute("activeEncounterId", encounter.getId()));
    }
```

and call `addRunEntryPoints(id, model);` immediately before the `return "campaigns/detail";` in **both** `detail` (line 131) and `update` (line 151).

`getCurrentScene` returns a hydrated `Scene`; the template walks `currentScene.chapter.adventure.id`. If that walk throws a `LazyInitializationException` under `open-in-view=false`, add the adventure id to the model instead:

```java
        adventureService.getCurrentScene(campaignId).ifPresent(scene -> {
            model.addAttribute("currentScene", scene);
            model.addAttribute("currentSceneAdventureId", scene.getChapter().getAdventure().getId());
        });
```

and use `aid=${currentSceneAdventureId}` in the template.

- [ ] **Step 7: Register the new surface in the declaration test**

In `SurfaceModeContractTest.governedSurfaces()`, add:

```java
        map.put("campaigns/settings.html", "admin");
```

- [ ] **Step 8: Run the tests**

Run: `./mvnw -q test -Dtest=CampaignAdminSurfaceTest`
Expected: PASS (5 tests).

Run: `./mvnw -q test -Dtest='SurfaceModeContractTest,CampaignControllerTest,CampaignHomeReadinessTest,CampaignDashboardScaleTest'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/campaigns src/main/java/dev/hendrikhoemberg/dmhelper/campaign \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign src/test/java/dev/hendrikhoemberg/dmhelper/web
git commit -m "feat(campaign): move admin tooling to a settings surface and add run entries"
```

---

## Task 5: Scene structure Edit surface

Spec §9.2: *"Scene detail: … metadata editing is behind an explicit Edit action."* Today `adventure/_action-rail.html` puts five create-forms, five delete controls, a metadata form and a reorder pair in the rail beside the prose.

**Files:**
- Create: `src/main/resources/templates/adventure/scene-structure.html`
- Create: `src/main/resources/templates/adventure/_scene-structure-editor.html`
- Create: `src/main/resources/templates/adventure/_scene-body.html`
- Modify: `src/main/resources/templates/adventure/_scene-form.html`
- Modify: `src/main/resources/templates/adventure/scene-detail.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructuredTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/web/SurfaceModeContractTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructureEditSurfaceTest.java`

**Interfaces:**
- Produces:
  - `GET /campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/structure` → view `adventure/scene-structure`, `data-surface="edit"`.
  - Fragment `adventure/_scene-structure-editor :: structureEditor`, rendered into the container `id="sceneStructureEditor"`.
  - Fragment `adventure/_scene-body :: sceneBody` (inner content only, no wrapper div).
  - `SceneController.loadStructureEditor(UUID campaignId, UUID adventureId, UUID sceneId, Model model)` returning `"adventure/_scene-structure-editor :: structureEditor"`.
- Consumes: every existing structured endpoint under `/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/…`, unchanged in path and parameters.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructureEditSurfaceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SceneStructureEditSurfaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;
    private String page;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        page = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}/structure",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theEditSurfaceDeclaresItself() {
        assertThat(page).contains("data-surface=\"edit\"");
    }

    @Test
    void everyStructuredAuthoringFormLivesHere() {
        assertThat(page).contains("data-create=\"section\"");
        assertThat(page).contains("data-create=\"check\"");
        assertThat(page).contains("data-create=\"participant\"");
        assertThat(page).contains("data-create=\"transition\"");
        assertThat(page).contains("data-create=\"link\"");
        assertThat(page).contains("data-structured-metadata");
        assertThat(page).contains("name=\"mapRegionKey\"");
    }

    @Test
    void theSceneFieldsAndDestructiveActionsLiveHereToo() {
        assertThat(page).as("scene title/body form").contains("name=\"sceneKey\"");
        assertThat(page).as("scene deletion").contains("hx-confirm=\"Delete this scene?\"");
        assertThat(page).as("scene reordering").contains("/move");
        assertThat(page).as("reorder controls must be labelled as reordering, not navigation")
                .contains("Move earlier")
                .contains("Move later");
    }

    @Test
    void thePageShowsWhatTheReaderWillSee() {
        assertThat(page).as("live preview of the rendered scene")
                .contains("id=\"sceneBody\"")
                .contains(PopulatedCampaignFixture.READ_ALOUD_BODY);
    }

    @Test
    void aStructuredWriteReturnsTheEditorFragmentNotTheRail() throws Exception {
        String fragment = mvc.perform(post(
                        "/campaigns/{c}/adventures/{a}/chapters/{ch}/scenes/{s}/checks",
                        seeded.campaignId(), seeded.adventureId(), seeded.chapterOneId(),
                        seeded.richSceneId())
                        .param("label", "Spot the loose flagstone")
                        .param("dc", "12"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment).contains("Spot the loose flagstone");
        assertThat(fragment).as("swapped back into the editor container")
                .contains("data-create=\"check\"");
        assertThat(fragment).as("the read rail is a different fragment")
                .doesNotContain("Set as Current Scene");
    }

    @Test
    void theEditSurfaceLinksBackToTheReadSurface() {
        assertThat(page).contains("/campaigns/" + seeded.campaignId()
                + "/adventures/" + seeded.adventureId() + "/scenes/" + seeded.richSceneId() + "\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=SceneStructureEditSurfaceTest`
Expected: FAIL — the `/structure` route 404s.

- [ ] **Step 3: Extract the shared scene body fragment**

Create `src/main/resources/templates/adventure/_scene-body.html` with the *inner* content currently inside `#sceneBody` in `scene-detail.html` (lines 35–46):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<th:block th:fragment="sceneBody">
    <div class="detail-section">
        <div th:if="${scene.sceneKey}" class="detail-meta">
            <span class="badge" th:text="${scene.sceneKey}">KEY</span>
        </div>
        <div class="note-body drop-cap" th:if="${renderedBody != null and !renderedBody.isEmpty()}"
             th:utext="${renderedBody}">
            Rendered body
        </div>
        <div th:unless="${renderedBody != null and !renderedBody.isEmpty()}"
             style="color: var(--color-text-muted); font-style: italic;">
            No body content yet.
        </div>
    </div>
    <th:block th:replace="~{adventure/_scene-sections :: sceneSections}"></th:block>
</th:block>
</html>
```

In `scene-detail.html`, replace those same lines 35–46 (everything between `<div id="sceneBody" …>` and its closing `</div>`) with:

```html
                    <th:block th:replace="~{adventure/_scene-body :: sceneBody}"></th:block>
```

In `SceneController.updateScene` (line 142), change the return value to the extracted fragment:

```java
        return "adventure/_scene-body :: sceneBody";
```

- [ ] **Step 4: Create the structure editor fragment**

Create `src/main/resources/templates/adventure/_scene-structure-editor.html`. Its content is **lines 95–497 of `adventure/_action-rail.html` moved verbatim** — the `Edit metadata` disclosure and the Sections, Checks, Participants, Transitions and Links blocks — wrapped as shown and with three mechanical changes applied to the moved markup (the scene reorder pair at lines 499–508 is *not* copied; it is rewritten below):

1. every `hx-target="#actionRail"` becomes `hx-target="#sceneStructureEditor"`;
2. the disclosures that were `<details>`/`<summary>` collapsed become open sections (this is the Edit surface — the forms are the point): change `<details class="u-mt-xs">` + `<summary class="u-text-sm">Add section</summary>` into `<section class="editor-block">` + `<h4>Add section</h4>`, and the same for check/participant/transition/link; keep `data-create="…"` on the forms;
3. the reorder pair at the end is relabelled.

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="structureEditor" class="structure-editor">
  <th:block th:if="${error != null}"><div th:replace="~{common/_error :: error(message=${error})}"></div></th:block>

  <!-- MOVED VERBATIM from adventure/_action-rail.html lines 95–497, with
       hx-target="#actionRail" -> hx-target="#sceneStructureEditor" and the
       "Add …" disclosures promoted to open sections. Keep every form field,
       every data-create marker, the threat-selector script and every hx-confirm. -->

  <!-- …metadata form (data-structured-metadata)… -->
  <!-- …sections list + add form (data-structured-sections, data-create="section")… -->
  <!-- …checks list + add form (data-structured-checks, data-create="check")… -->
  <!-- …participants list + add form (data-structured-participants, data-create="participant")… -->
  <!-- …transitions list + add form (data-structured-transitions, data-create="transition")… -->
  <!-- …links list + add form (data-structured-links, data-create="link")… -->

  <section class="editor-block">
    <h4>Position in chapter</h4>
    <div class="u-flex u-gap-xs">
      <button class="btn btn-ghost btn-sm"
              th:hx-put="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}/move(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}"
              hx-vals='{"direction": -1}'
              hx-target="body" hx-swap="outerHTML">Move earlier</button>
      <button class="btn btn-ghost btn-sm"
              th:hx-put="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}/move(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}"
              hx-vals='{"direction": 1}'
              hx-target="body" hx-swap="outerHTML">Move later</button>
    </div>
  </section>
</div>
</html>
```

Perform the move mechanically:

```bash
sed -n '95,497p' src/main/resources/templates/adventure/_action-rail.html > /tmp/claude-1000/-home-hendrik-Documents-Coding-DMHelper/56bd1743-d007-474e-8dca-c0f65dc72125/scratchpad/structure-body.html
sed -i 's/hx-target="#actionRail"/hx-target="#sceneStructureEditor"/g' \
    /tmp/claude-1000/-home-hendrik-Documents-Coding-DMHelper/56bd1743-d007-474e-8dca-c0f65dc72125/scratchpad/structure-body.html
```

then paste that file's content in place of the comment placeholders above and apply change (2) by hand.

- [ ] **Step 5: Create the Edit page**

Create `src/main/resources/templates/adventure/scene-structure.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="|Edit ${scene.title} — ${adventure.name}|">Edit scene</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>
    <div class="app-shell">
        <th:block th:replace="~{fragments/_appnav :: appnav}"></th:block>
        <main class="app-main" data-surface="edit">
            <div class="page-header">
                <div>
                    <div class="breadcrumb">
                        <a th:href="@{/campaigns/{cid}(cid=${campaignId})}" th:text="${campaign.name}">Campaign</a>
                        <span> / </span>
                        <a th:href="@{/campaigns/{cid}/adventures/{aid}(cid=${campaignId},aid=${adventure.id})}"
                           th:text="${adventure.name}">Adventure</a>
                        <span> / </span>
                        <a th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}"
                           th:text="${scene.title}">Scene</a>
                        <span> / </span>
                        <span>Edit</span>
                    </div>
                    <h1 th:text="|Edit: ${scene.title}|">Edit scene</h1>
                </div>
                <div class="page-header-actions">
                    <a class="btn btn-primary"
                       th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}">
                        Done — back to scene
                    </a>
                </div>
            </div>

            <div class="edit-layout">
                <div id="sceneEditPane" class="edit-pane">
                    <h2>Scene</h2>
                    <th:block th:replace="~{adventure/_scene-form :: form}"></th:block>
                </div>
                <div class="edit-preview">
                    <h2>Preview</h2>
                    <div id="sceneBody">
                        <th:block th:replace="~{adventure/_scene-body :: sceneBody}"></th:block>
                    </div>
                </div>
            </div>

            <h2>Structured content</h2>
            <div id="sceneStructureEditor">
                <th:block th:replace="~{adventure/_scene-structure-editor :: structureEditor}"></th:block>
            </div>

            <section class="detail-section surface-danger-zone">
                <h2>Danger zone</h2>
                <button class="btn btn-danger"
                        th:hx-delete="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}"
                        hx-confirm="Delete this scene?"
                        hx-target="body" hx-swap="outerHTML">Delete scene</button>
            </section>
        </main>
    </div>
</body>
</html>
```

- [ ] **Step 6: Fix the scene form for its new home**

In `src/main/resources/templates/adventure/_scene-form.html`:

1. Change the remove-handout button's `hx-target="#actionRail"` to `hx-target="#sceneStructureEditor"` (line ~127).
2. Replace the Cancel button with a link back to the Read surface:

```html
      <a class="btn btn-ghost"
         th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}">Cancel</a>
```

The form's `hx-target="#sceneBody"` stays: `#sceneBody` is the preview pane on this page, and `updateScene` now returns `adventure/_scene-body :: sceneBody`.

- [ ] **Step 7: Wire the controller**

In `SceneController`:

1. Add the structure route after `editForm` (line 115):

```java
    /**
     * Edit surface for one scene: the scene fields, a live preview and every structured-content
     * form. Split out of the read rail so narrative outranks authoring (spec section 9.2).
     */
    @GetMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/structure")
    public String sceneStructure(@PathVariable UUID campaignId,
                                 @PathVariable UUID adventureId,
                                 @PathVariable UUID id,
                                 Model model) {
        AdventureService.SceneDetailView view = adventureService.findSceneDetailView(id);
        Scene scene = view.scene();
        model.addAttribute("campaign", campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found")));
        model.addAttribute("scene", scene);
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapters", adventureService.findChaptersByAdventure(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("renderedBody", markdownUtil.toHtml(scene.getBody()));
        model.addAttribute("maps", gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("encounters", encounterRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("statBlocks", statBlockRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("handouts", handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId));
        model.addAttribute("visibleTraps", trapRepository.findVisibleByCampaignId(campaignId));
        model.addAttribute("visibleHazards", hazardRepository.findVisibleByCampaignId(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("sectionThreatCards", view.sectionThreatCards());
        return "adventure/scene-structure";
    }
```

2. Rename the private helper `loadActionRail` to `loadStructureEditor` and change its return value:

```java
    private String loadStructureEditor(UUID campaignId, UUID adventureId, UUID sceneId, Model model) {
        // …body unchanged…
        return "adventure/_scene-structure-editor :: structureEditor";
    }
```

3. Update every call site. The structured-content handlers keep calling the renamed helper:
   `updateMetadata`, `addSection`, `updateSection`, `deleteSection`, `addCheck`, `updateCheck`, `deleteCheck`, `addParticipant`, `updateParticipant`, `deleteParticipant`, `addLink`, `updateLink`, `deleteLink`, `addTransition`, `updateTransition`, `deleteTransition`, plus `addStatBlock`, `removeStatBlock`, `addHandout`, `removeHandout`, `linkMap`, `unlinkMap`, `linkEncounter`, `unlinkEncounter`.
   `seedEncounter` is handled in Task 6 — for now point it at `loadStructureEditor` too so the code compiles.

```bash
sed -i 's/loadActionRail(/loadStructureEditor(/g' \
    src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java
```

- [ ] **Step 8: Retarget the contract test at the new files**

In `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructuredTemplateContractTest.java`, replace every

```java
Path.of("src/main/resources/templates/adventure/_action-rail.html")
```

with

```java
Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html")
```

and rename the affected test methods from `actionRail…` to `structureEditor…`. Two of them describe read-side behavior and must instead read the rail file — leave them failing for now; Task 6 creates `_scene-rail.html` and repoints them:

- `existingQuickNoteAndMapEncounterSelectorsPreserved`
- `actionRailOffersSeedEncounterOnlyWhenTheSceneHasResolvableParticipants`
- `actionRailReportsParticipantsTheSeedCouldNotResolve`

Comment these three out with `// re-pointed at _scene-rail.html in Task 6` and a `@org.junit.jupiter.api.Disabled("moves to the read rail in Task 6")` annotation so the suite stays green between tasks.

- [ ] **Step 9: Register the new surface**

In `SurfaceModeContractTest.governedSurfaces()`, add:

```java
        map.put("adventure/scene-structure.html", "edit");
```

- [ ] **Step 10: Run the tests**

Run: `./mvnw -q test -Dtest='SceneStructureEditSurfaceTest,SceneStructuredTemplateContractTest,SurfaceModeContractTest,SceneControllerTest'`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/templates/adventure src/main/java/dev/hendrikhoemberg/dmhelper/adventure \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure src/test/java/dev/hendrikhoemberg/dmhelper/web
git commit -m "feat(scene): add a structure edit surface for scene authoring"
```

---

## Task 6: Scene Read surface keeps prose and runtime actions only

**Files:**
- Create: `src/main/resources/templates/adventure/_scene-rail.html`
- Delete: `src/main/resources/templates/adventure/_action-rail.html`
- Modify: `src/main/resources/templates/adventure/scene-detail.html`
- Modify: `src/main/resources/templates/adventure/_scene-panel.html:9`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneDetailPresentationTest.java:53-79`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructuredTemplateContractTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneReadSurfaceTest.java`

**Interfaces:**
- Produces: fragments `adventure/_scene-rail :: sceneRail` (container id `sceneRail`) and `adventure/_scene-rail :: statusBadge`. `SceneController.loadSceneRail(UUID campaignId, UUID adventureId, UUID sceneId, Model model)` returns `"adventure/_scene-rail :: sceneRail"`; `seedEncounter`, `linkMap`, `unlinkMap`, `linkEncounter` and `unlinkEncounter` call it.
- Consumes: `adventure/_scene-structure-editor :: structureEditor` (Task 5) for the Edit link target.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneReadSurfaceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SceneReadSurfaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private PopulatedCampaignFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void noAuthoringFormsRemainBesideTheProse() {
        assertThat(body).doesNotContain("data-create=\"section\"");
        assertThat(body).doesNotContain("data-create=\"check\"");
        assertThat(body).doesNotContain("data-create=\"participant\"");
        assertThat(body).doesNotContain("data-create=\"transition\"");
        assertThat(body).doesNotContain("data-create=\"link\"");
        assertThat(body).doesNotContain("data-structured-metadata");
        assertThat(body).as("no delete controls on a reading surface").doesNotContain("hx-delete");
        assertThat(body).as("no reordering on a reading surface").doesNotContain("hx-vals='{\"direction\"");
    }

    @Test
    void theStructuredContentIsStillReadable() {
        assertThat(body).as("participants").contains("Späher der Redbrands");
        assertThat(body).as("resolved statblock stats")
                .contains(PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_NAME)
                .contains("AC " + PopulatedCampaignFixture.PARTICIPANT_STATBLOCK_AC);
        assertThat(body).as("checks").contains("Brief entziffern").contains("DC 13");
        assertThat(body).as("transitions").contains("Weiter in den Gang");
        assertThat(body).as("links").contains("Der Gang");
    }

    @Test
    void runtimeActionsSurvive() {
        assertThat(body).contains("Set as Current Scene");
        assertThat(body).contains("Start encounter from this scene");
        assertThat(body).contains("status-badge-container");
    }

    @Test
    void anExplicitEditActionLeadsToTheEditSurface() {
        assertThat(body).contains("/adventures/" + seeded.adventureId()
                + "/scenes/" + seeded.richSceneId() + "/structure");
    }

    @Test
    void thePageHeaderCarriesNoDestructiveAction() {
        int headerAt = body.indexOf("page-header-actions");
        int headerEnd = body.indexOf("</div>", headerAt);
        assertThat(headerAt).isGreaterThan(-1);
        assertThat(body.substring(headerAt, headerEnd))
                .as("Delete must not sit in the header of a reading surface")
                .doesNotContain("btn-danger");
    }

    @Test
    void proseStillOutranksTheRail() {
        int mainColumnStart = body.indexOf("id=\"sceneBody\"");
        int railStart = body.indexOf("id=\"sceneRail\"");
        int readAloudAt = body.indexOf(PopulatedCampaignFixture.READ_ALOUD_BODY);

        assertThat(mainColumnStart).isGreaterThan(-1);
        assertThat(railStart).isGreaterThan(mainColumnStart);
        assertThat(readAloudAt).isBetween(mainColumnStart, railStart);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=SceneReadSurfaceTest`
Expected: FAIL — `noAuthoringFormsRemainBesideTheProse` finds `data-create="section"`; the page still includes the old action rail.

- [ ] **Step 3: Write the read rail**

Create `src/main/resources/templates/adventure/_scene-rail.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="sceneRail" class="scene-rail">
  <th:block th:if="${error != null}"><div th:replace="~{common/_error :: error(message=${error})}"></div></th:block>

  <div class="detail-section">
    <h3>Run</h3>

    <div class="u-flex u-gap-xs u-mb-md">
      <button class="btn btn-sm"
              th:classappend="${scene.status.name() == 'VISITED'} ? 'btn-primary' : 'btn-ghost'"
              th:hx-put="@{/scenes/{id}/status(id=${scene.id})}"
              hx-vals='{"status": "VISITED"}'
              hx-target="closest .status-badge-container" hx-swap="outerHTML">Visited</button>
      <button class="btn btn-sm"
              th:classappend="${scene.status.name() == 'DONE'} ? 'btn-success' : 'btn-ghost'"
              th:hx-put="@{/scenes/{id}/status(id=${scene.id})}"
              hx-vals='{"status": "DONE"}'
              hx-target="closest .status-badge-container" hx-swap="outerHTML">Done</button>
      <button class="btn btn-sm btn-ghost"
              th:hx-put="@{/scenes/{id}/status(id=${scene.id})}"
              hx-vals='{"status": "UNVISITED"}'
              hx-target="closest .status-badge-container" hx-swap="outerHTML">Unvisit</button>
    </div>
    <th:block th:replace="~{adventure/_scene-rail :: statusBadge}"></th:block>

    <div class="u-mb-md">
      <button class="btn btn-primary btn-sm u-w-full"
              th:hx-post="@{/campaigns/{cid}/current-scene(cid=${campaignId})}"
              th:attr="hx-vals='{&quot;sceneId&quot;: &quot;' + ${scene.id} + '&quot;}'"
              hx-target="#scenePanel"
              hx-swap="innerHTML">Set as Current Scene</button>
    </div>

    <div th:if="${scene.map != null}" class="u-mb-md">
      <h4 class="u-text-sm u-mb-xs">Linked Map</h4>
      <div class="u-flex u-gap-xs">
        <span th:text="${scene.map.name}">Map Name</span>
        <a th:href="@{/campaigns/{cid}/maps/{mid}/play(cid=${campaignId},mid=${scene.map.id})}"
           class="btn btn-sm" target="_blank">Open</a>
      </div>
      <div th:if="${scene.pinX != null and scene.pinY != null}" class="u-text-xs text-muted">
        Pin: (<span th:text="${scene.pinX}">X</span>, <span th:text="${scene.pinY}">Y</span>)
      </div>
    </div>

    <div th:if="${scene.encounter != null}" class="u-mb-md">
      <h4 class="u-text-sm u-mb-xs">Linked Encounter</h4>
      <div class="u-flex u-gap-xs">
        <span th:text="${scene.encounter.name}">Encounter Name</span>
        <a th:href="@{/campaigns/{cid}/encounters/{eid}(cid=${campaignId},eid=${scene.encounter.id})}"
           class="btn btn-sm">Open encounter</a>
      </div>
    </div>

    <!-- An imported campaign carries no encounters at all, so combat used to start with the
         DM retyping monsters the app already knows. Offered only when there is something to
         build from, and only until the scene actually has an encounter. -->
    <div th:if="${scene.encounter == null and canSeedEncounter}" class="u-mb-md">
      <button class="btn btn-sm btn-primary"
              th:hx-post="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}/seed-encounter(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}"
              hx-target="#sceneRail" hx-swap="innerHTML">
        Start encounter from this scene
      </button>
    </div>

    <div th:if="${seedResult != null and !seedResult.alreadyExisted}" class="card card--compact u-mb-md">
      <strong th:text="${seedResult.encounterName}">Encounter</strong>
      <span class="u-text-xs"
            th:text="|${seedResult.combatantsAdded} combatants added|">0 combatants added</span>
      <div th:if="${!seedResult.skippedParticipants.isEmpty()}" class="u-text-xs u-mt-xs">
        <span th:text="|${#lists.size(seedResult.skippedParticipants)} participant(s) skipped — no statblock:|">Skipped:</span>
        <span th:text="${#strings.listJoin(seedResult.skippedParticipants, ', ')}">Names</span>
      </div>
    </div>
  </div>

  <div class="detail-section">
    <h3>At a glance</h3>

    <div class="u-mb-md" th:if="${scene.participants != null and !scene.participants.isEmpty()}"
         data-rail-participants>
      <h4 class="u-text-sm u-mb-xs">Participants</h4>
      <div th:each="p : ${scene.participants}" class="rail-row" data-screen-sensitive>
        <strong th:text="${p.displayName}">Name</strong>
        <span class="badge" th:if="${p.quantity > 1}" th:text="|x${p.quantity}|">Qty</span>
        <span class="badge" th:if="${p.disposition != null}"
              th:text="${#enums.label(p.disposition)}">Disposition</span>
        <a th:if="${p.statBlock != null}" class="participant-statblock" target="_blank"
           th:href="@{/library/statblocks/{id}(id=${p.statBlock.id})}"
           th:title="|Open ${p.statBlock.name}|">
          <span class="participant-statblock__name" th:text="${p.statBlock.name}">Statblock</span>
          <span class="participant-statblock__stat" th:text="|AC ${p.statBlock.ac}|">AC 14</span>
          <span class="participant-statblock__stat" th:text="|HP ${p.statBlock.hp}|">HP 16</span>
        </a>
        <span th:if="${p.placementHint}" class="u-text-xs text-muted"
              th:text="${p.placementHint}">Placement</span>
      </div>
    </div>

    <div class="u-mb-md" th:if="${scene.checks != null and !scene.checks.isEmpty()}" data-rail-checks>
      <h4 class="u-text-sm u-mb-xs">Checks</h4>
      <div th:each="check : ${scene.checks}" class="rail-row" data-screen-sensitive>
        <strong th:text="${check.label}">Check</strong>
        <span class="badge" th:if="${check.dc != null}" th:text="|DC ${check.dc}|">DC</span>
        <span class="badge" th:if="${check.visibility != null}"
              th:text="${#enums.label(check.visibility)}">Visibility</span>
        <span th:if="${check.ability}" class="u-text-xs"
              th:text="|${check.ability} ${check.skill}|">Ability</span>
        <div th:if="${check.success}" class="u-text-xs">Success: <span th:text="${check.success}">Success</span></div>
        <div th:if="${check.failure}" class="u-text-xs">Failure: <span th:text="${check.failure}">Failure</span></div>
        <div th:if="${check.partial}" class="u-text-xs">Partial: <span th:text="${check.partial}">Partial</span></div>
      </div>
    </div>

    <div class="u-mb-md" th:if="${scene.transitions != null and !scene.transitions.isEmpty()}"
         data-rail-transitions>
      <h4 class="u-text-sm u-mb-xs">Transitions</h4>
      <div th:each="t : ${scene.transitions}" class="rail-row" data-screen-sensitive>
        <span class="badge" th:text="${#enums.label(t.kind)}">KIND</span>
        <strong th:text="${t.label}">Label</strong>
        <span th:if="${t.targetScene != null}" th:text="${t.targetScene.title}">Target</span>
        <span th:if="${t.externalDestination}" class="u-text-xs"
              th:text="|→ ${t.externalDestination}|">External</span>
        <div th:if="${t.condition}" class="u-text-xs">Condition: <span th:text="${t.condition}">Condition</span></div>
        <div th:if="${t.dmNote}" class="u-text-xs text-muted">DM: <span th:text="${t.dmNote}">DM Note</span></div>
      </div>
    </div>

    <div class="u-mb-md" th:if="${scene.statBlocks != null and !scene.statBlocks.isEmpty()}">
      <h4 class="u-text-sm u-mb-xs">Linked Statblocks</h4>
      <div th:each="sb : ${scene.statBlocks}" class="rail-row">
        <a th:href="@{/library/statblocks/{id}(id=${sb.id})}" target="_blank"
           th:text="${sb.name}">StatBlock</a>
      </div>
    </div>

    <div class="u-mb-md" th:if="${scene.handouts != null and !scene.handouts.isEmpty()}">
      <h4 class="u-text-sm u-mb-xs">Linked Handouts</h4>
      <div th:each="h : ${scene.handouts}" class="linked-handout-row">
        <span th:text="${h.title}">Handout</span>
        <a th:href="@{/campaigns/{cid}/handouts/{hid}/present(cid=${campaignId},hid=${h.id})}"
           class="btn btn-sm" target="_blank">Present</a>
      </div>
    </div>

    <div class="u-mb-md" th:if="${scene.links != null and !scene.links.isEmpty()}" data-rail-links>
      <h4 class="u-text-sm u-mb-xs">Links</h4>
      <div th:each="link : ${scene.links}" class="rail-row">
        <span class="badge" th:text="${#enums.label(link.role)}">ROLE</span>
        <span th:text="${link.displayText ?: link.targetType}">Target</span>
        <span th:if="${link.condition}" class="u-text-xs">Condition: <span th:text="${link.condition}">Condition</span></span>
      </div>
    </div>

    <a class="btn btn-ghost btn-sm u-w-full"
       th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}/structure(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}">
      Edit scene &amp; structure
    </a>
  </div>
</div>

<div th:fragment="statusBadge" xmlns:th="http://www.thymeleaf.org">
  <div class="status-badge-container">
    <span class="badge"
          th:classappend="${scene.status.name() == 'DONE'} ? 'badge-success' :
                          (${scene.status.name() == 'VISITED'} ? 'badge-warning' : '')"
          th:text="${#enums.label(scene.status)}">STATUS</span>
  </div>
</div>
</html>
```

- [ ] **Step 4: Repoint the pages and delete the old rail**

In `adventure/scene-detail.html`:

1. Replace the page-header actions block (lines 21–30) with:

```html
                <div class="page-header-actions">
                    <a class="btn btn-ghost"
                       th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}/structure(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}">Edit</a>
                </div>
```

2. Change the rail container and include (lines 48–49):

```html
                <div id="sceneRail" style="width: 300px; flex-shrink: 0;">
                    <th:block th:replace="~{adventure/_scene-rail :: sceneRail}"></th:block>
```

In `adventure/_scene-panel.html` line 9, change the fragment source:

```html
      <th:block th:replace="~{adventure/_scene-rail :: statusBadge(scene=${currentScene})}"></th:block>
```

Then remove the superseded template:

```bash
git rm src/main/resources/templates/adventure/_action-rail.html
```

- [ ] **Step 5: Split the controller helper**

In `SceneController`, add a second loader beside `loadStructureEditor`:

```java
    /**
     * Read-surface rail: status, current-scene, links and the seed action. Read-only about the
     * structured content — authoring lives on the structure surface.
     */
    private String loadSceneRail(UUID campaignId, UUID adventureId, UUID sceneId, Model model) {
        AdventureService.SceneDetailView view = adventureService.findSceneDetailView(sceneId);
        model.addAttribute("scene", view.scene());
        model.addAttribute("canSeedEncounter", encounterSeeder.canSeed(campaignId, sceneId));
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("sectionThreatCards", view.sectionThreatCards());
        return "adventure/_scene-rail :: sceneRail";
    }
```

Change these five handlers to call `loadSceneRail` instead of `loadStructureEditor`: `seedEncounter` (line 228), `linkMap`, `unlinkMap`, `linkEncounter`, `unlinkEncounter`.

Change `updateSceneStatus` (line 277) to return the rail's badge:

```java
        return "adventure/_scene-rail :: statusBadge";
```

- [ ] **Step 6: Repoint and re-enable the moved contract tests**

In `SceneStructuredTemplateContractTest`, re-enable the three tests disabled in Task 5, remove the `@Disabled` annotations, point them at `src/main/resources/templates/adventure/_scene-rail.html`, and rename them:

```java
    @Test
    void sceneRailKeepsStatusCurrentSceneAndLinkedContent() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-rail.html"));
        assertThat(html).contains("scene.statBlocks", "scene.handouts");
        assertThat(html).contains("hx-vals='{\"status\": \"VISITED\"}'");
        assertThat(html).contains("Set as Current Scene");
    }

    @Test
    void sceneRailOffersSeedEncounterOnlyWhenTheSceneHasResolvableParticipants() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-rail.html"));
        assertThat(html).contains("seed-encounter");
        assertThat(html).contains("canSeedEncounter");
        assertThat(html)
                .as("re-running must not be offered once the scene already has an encounter")
                .contains("scene.encounter == null");
    }

    @Test
    void sceneRailReportsParticipantsTheSeedCouldNotResolve() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-rail.html"));
        assertThat(html).contains("seedResult.skippedParticipants");
        assertThat(html).contains("seedResult.combatantsAdded");
    }
```

- [ ] **Step 7: Update the presentation test**

In `SceneDetailPresentationTest`:

- `sectionsRenderInTheMainColumnNotTheClampedRail`: change `body.indexOf("id=\"actionRail\"")` to `body.indexOf("id=\"sceneRail\"")` and the two assertion messages from `#actionRail` to `#sceneRail`.
- `metadataFormIsBehindADisclosure`: the metadata form is no longer on this page. Replace the whole method with:

```java
    @Test
    void metadataEditingIsBehindAnExplicitEditAction() {
        assertThat(body)
                .as("prep-time admin fields live on the edit surface, reachable by one link")
                .doesNotContain("data-structured-metadata")
                .contains("/structure");
    }
```

- [ ] **Step 8: Run the tests**

Run: `./mvnw -q test -Dtest='SceneReadSurfaceTest,SceneDetailPresentationTest,SceneStructuredTemplateContractTest,SceneSeedEncounterControllerTest,ParticipantStatBlockRenderTest,PlayerSafeProjectionTest,SceneControllerTest'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/adventure src/main/java/dev/hendrikhoemberg/dmhelper/adventure \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure
git commit -m "feat(scene): keep the scene page to prose, runtime actions and one edit link"
```

---

## Task 7: Encounter setup Edit surface

Spec §9.2: *"Encounter detail: … raw setup controls are grouped progressively rather than shown as one dense form."* Today the detail page stacks a quick-add form, two prefill forms, library search, threat search, waves, prep, rewards and the summary modal under one heading.

**Files:**
- Create: `src/main/resources/templates/encounter/setup.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterTemplateContractTest.java:19-27,132-139`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/web/SurfaceModeContractTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterSetupSurfaceTest.java`

**Interfaces:**
- Produces: `GET /campaigns/{campaignId}/encounters/{id}/setup` → view `encounter/setup`, `data-surface="edit"`, model attributes `encounter`, `combatants`, `campaignId`, `maps`, `audioCues`, `waves`, `prep`, `rewards`, `encounterEntity`.
- Consumes: existing fragments `encounter/_library-add :: library-add`, `_threat-add :: threat-add`, `_waves :: waves`, `_prep :: prep`, `_rewards :: rewards`, `_summary-modal :: summary-modal`, `_form :: form`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterSetupSurfaceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncounterSetupSurfaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PreparationSurfaceFixture fixture;

    private PreparationSurfaceFixture.Seeded seeded;
    private String page;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        page = mvc.perform(get("/campaigns/{c}/encounters/{e}/setup",
                        seeded.campaignId(), seeded.encounterId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theSetupSurfaceDeclaresItself() {
        assertThat(page).contains("data-surface=\"edit\"");
    }

    @Test
    void everySetupControlIsPresent() {
        assertThat(page).contains("libraryAdd");
        assertThat(page).contains("threatAdd");
        assertThat(page).contains("name=\"tactics\"");
        assertThat(page).contains("encounterRewardsForm");
        assertThat(page).contains("waveKey");
        assertThat(page).contains("prefill/party");
        assertThat(page).contains("combatant-quickadd");
    }

    @Test
    void controlsAreGroupedProgressivelyNotStacked() {
        assertThat(page).contains("data-setup-group=\"combatants\"");
        assertThat(page).contains("data-setup-group=\"waves\"");
        assertThat(page).contains("data-setup-group=\"notes\"");
        assertThat(page).contains("data-setup-group=\"rewards\"");
        assertThat(page).as("groups past the first open on demand").contains("<details");
    }

    @Test
    void destructiveActionsLiveHereNotOnTheReadSurface() {
        assertThat(page).contains("hx-confirm=\"Delete this encounter?\"");
    }

    @Test
    void itLinksBackToTheEncounterOverview() {
        assertThat(page).contains("/campaigns/" + seeded.campaignId()
                + "/encounters/" + seeded.encounterId() + "\"");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=EncounterSetupSurfaceTest`
Expected: FAIL — `/setup` returns 404.

- [ ] **Step 3: Add the setup route**

In `EncounterController`, extract the shared model wiring and add the route. Replace the existing `detail` method (lines 145–161) with:

```java
    @GetMapping("/{id}")
    public String detail(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        addEncounterModel(campaignId, id, model);
        return "encounter/detail";
    }

    /**
     * Edit surface. Every raw setup control lives here so the encounter overview can be read
     * and run without scrolling past eight forms (spec 2026-07-22 section 9.2).
     */
    @GetMapping("/{id}/setup")
    public String setup(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        addEncounterModel(campaignId, id, model);
        model.addAttribute("maps", mapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId));
        return "encounter/setup";
    }

    private void addEncounterModel(UUID campaignId, UUID id, Model model) {
        model.addAttribute("encounter", encounterService.getById(id));
        model.addAttribute("combatants", encounterService.getCombatants(id));
        model.addAttribute("difficulty", encounterService.calculateDifficulty(campaignId, id));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        encounterRepository.findById(id).ifPresent(e -> {
            model.addAttribute("encounterEntity", e);
            model.addAttribute("encounterCombatCue", e.getCombatAudioCue());
            model.addAttribute("encounterVictoryCue", e.getVictoryAudioCue());
            model.addAttribute("encounterVictoryDuration", e.getVictoryCueDurationSeconds());
        });
        model.addAttribute("waves", encounterService.listWaves(id));
        model.addAttribute("prep", encounterService.getPrep(id));
        model.addAttribute("rewards", encounterService.getRewards(id));
    }
```

- [ ] **Step 4: Create the setup page**

Create `src/main/resources/templates/encounter/setup.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="|Setup — ${encounter.name}|">DMHelper — Encounter setup</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>
    <div class="app-shell">
        <th:block th:replace="~{fragments/_appnav :: appnav}"></th:block>
        <main class="app-main" data-surface="edit">
            <div class="page-header">
                <div>
                    <div class="breadcrumb">
                        <a th:href="@{/campaigns/{cid}/encounters(cid=${campaignId})}">Encounters</a>
                        <span> / </span>
                        <a th:href="@{/campaigns/{cid}/encounters/{eid}(cid=${campaignId},eid=${encounter.id})}"
                           th:text="${encounter.name}">Encounter</a>
                        <span> / </span>
                        <span>Setup</span>
                    </div>
                    <h1 th:text="|Setup: ${encounter.name}|">Encounter setup</h1>
                </div>
                <div class="page-header-actions">
                    <a class="btn btn-primary"
                       th:href="@{/campaigns/{cid}/encounters/{eid}(cid=${campaignId},eid=${encounter.id})}">
                        Done — back to encounter
                    </a>
                </div>
            </div>

            <!-- Group 1 is the one a DM always needs, so it is the only one open by default. -->
            <details class="setup-group" data-setup-group="combatants" open>
                <summary><h2>Combatants</h2></summary>
                <div class="setup-group__body">
                    <form method="post"
                          th:action="@{/campaigns/{cid}/encounters/{eid}/combatants(cid=${campaignId}, eid=${encounter.id})}"
                          class="combatant-quickadd u-flex">
                        <input type="text" name="name" placeholder="Name" required>
                        <input type="number" name="maxHp" placeholder="HP" value="10">
                        <select name="kind">
                            <option value="NPC">NPC</option>
                            <option value="PC">PC</option>
                            <option value="MONSTER">Monster</option>
                            <option value="OBJECT">Object</option>
                            <option value="HAZARD">Hazard</option>
                        </select>
                        <button type="submit" class="btn btn-sm btn-primary">Add</button>
                    </form>

                    <div class="detail-actions u-mb-lg">
                        <th:block th:if="${encounter.mapId != null}">
                            <form method="post"
                                  th:action="@{/campaigns/{cid}/encounters/{eid}/prefill/map(cid=${campaignId}, eid=${encounter.id})}"
                                  class="u-inline">
                                <button type="submit" class="btn btn-sm">Prefill from Map</button>
                            </form>
                        </th:block>
                        <form method="post"
                              th:action="@{/campaigns/{cid}/encounters/{eid}/prefill/party(cid=${campaignId}, eid=${encounter.id})}"
                              class="u-inline">
                            <button type="submit" class="btn btn-sm">Prefill from Party</button>
                        </form>
                    </div>

                    <div class="u-mb-lg" th:replace="~{encounter/_library-add :: library-add(${campaignId}, ${encounter.id})}"></div>
                    <div class="u-mb-lg" th:replace="~{encounter/_threat-add :: threat-add(${campaignId}, ${encounter.id})}"></div>
                </div>
            </details>

            <details class="setup-group" data-setup-group="waves">
                <summary><h2>Waves</h2></summary>
                <div class="setup-group__body">
                    <div th:replace="~{encounter/_waves :: waves(${campaignId}, ${encounter})}"></div>
                </div>
            </details>

            <details class="setup-group" data-setup-group="notes">
                <summary><h2>Preparation notes</h2></summary>
                <div class="setup-group__body">
                    <div th:replace="~{encounter/_prep :: prep(${campaignId}, ${encounter}, ${prep})}"></div>
                </div>
            </details>

            <details class="setup-group" data-setup-group="rewards">
                <summary><h2>Rewards</h2></summary>
                <div class="setup-group__body">
                    <div th:replace="~{encounter/_rewards :: rewards(${campaignId}, ${encounter}, ${rewards})}"></div>
                </div>
            </details>

            <details class="setup-group" data-setup-group="details">
                <summary><h2>Name, map and audio</h2></summary>
                <div class="setup-group__body">
                    <div th:replace="~{encounter/_form :: form}"></div>
                </div>
            </details>

            <section class="detail-section surface-danger-zone">
                <h2>Danger zone</h2>
                <button class="btn btn-danger"
                        th:attr="hx-delete=@{/campaigns/{cid}/encounters/{eid}(cid=${campaignId}, eid=${encounter.id})}"
                        hx-confirm="Delete this encounter?"
                        hx-target="body"
                        hx-swap="outerHTML">Delete encounter</button>
            </section>

            <div th:replace="~{encounter/_summary-modal :: summary-modal(${campaignId}, ${encounter})}"></div>
        </main>
    </div>
</body>
</html>
```

- [ ] **Step 5: Add the setup-group styles**

Append to `src/main/resources/static/css/surfaces.css`:

```css
/* Edit surfaces: forms are the content, but only the first group starts open so the page
   does not read as one wall of fields. */
.setup-group { border-top: 1px solid var(--color-border); padding: var(--space-sm) 0; }
.setup-group > summary { cursor: pointer; list-style: none; }
.setup-group > summary::-webkit-details-marker { display: none; }
.setup-group > summary h2 { display: inline; font-size: var(--text-lg); }
.setup-group > summary::before { content: "▸ "; color: var(--color-text-muted); }
.setup-group[open] > summary::before { content: "▾ "; }
.setup-group__body { padding: var(--space-sm) 0 var(--space-md); }

.edit-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-lg); }
@media (max-width: 1100px) { .edit-layout { grid-template-columns: minmax(0, 1fr); } }
.editor-block { margin-bottom: var(--space-md); }

.surface-danger-zone { border: 1px solid var(--color-danger, #b3453e); border-radius: var(--radius-md); }
.surface-danger-zone h2 { color: var(--color-danger, #b3453e); }
```

- [ ] **Step 6: Repoint the encounter contract test**

In `EncounterTemplateContractTest`, change `detailIncludesLibraryAddWavePrepRewardsSummary` to read the setup template and rename it:

```java
    @Test
    void setupIncludesLibraryAddWavePrepRewardsSummary() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/setup.html"));
        assertThat(html).contains("encounter/_library-add :: library-add");
        assertThat(html).contains("encounter/_threat-add :: threat-add");
        assertThat(html).contains("encounter/_waves :: waves");
        assertThat(html).contains("encounter/_prep :: prep");
        assertThat(html).contains("encounter/_rewards :: rewards");
        assertThat(html).contains("encounter/_summary-modal :: summary-modal");
    }
```

`controllerAddsWavePrepRewardsModelAttrs` still passes: the strings now live in `addEncounterModel`.

- [ ] **Step 7: Register the new surface**

In `SurfaceModeContractTest.governedSurfaces()`, add:

```java
        map.put("encounter/setup.html", "edit");
```

- [ ] **Step 8: Run the tests**

Run: `./mvnw -q test -Dtest='EncounterSetupSurfaceTest,EncounterTemplateContractTest,SurfaceModeContractTest,EncounterControllerTest'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/templates/encounter src/main/resources/static/css/surfaces.css \
        src/main/java/dev/hendrikhoemberg/dmhelper/encounter src/test/java/dev/hendrikhoemberg/dmhelper
git commit -m "feat(encounter): move raw setup controls to a grouped edit surface"
```

---

## Task 8: Encounter preparation summary and one clear Run action

Spec §9.2: *"Encounter detail: provide a readable preparation summary and a clear Run action."*

**Files:**
- Create: `src/main/resources/templates/encounter/_prep-summary.html`
- Modify: `src/main/resources/templates/encounter/detail.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterController.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterPrepSummaryTest.java`

**Interfaces:**
- Produces: `POST /campaigns/{campaignId}/encounters/{id}/run` → activates the encounter and redirects to `/campaigns/{campaignId}/session`. Fragment `encounter/_prep-summary :: prepSummary` reading `encounter`, `combatants`, `difficulty`, `prep`, `rewards`, `waves`, `encounterCombatCue`, `encounterVictoryCue`.
- Consumes: `PreparationSurfaceFixture` (Task 2), `addEncounterModel` (Task 7).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterPrepSummaryTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.encounter.web;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncounterPrepSummaryTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PreparationSurfaceFixture fixture;
    @Autowired private EncounterService encounters;

    private MockMvc mvc;
    private PreparationSurfaceFixture.Seeded seeded;
    private String page;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        page = mvc.perform(get("/campaigns/{c}/encounters/{e}",
                        seeded.campaignId(), seeded.encounterId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theOverviewReadsAsAPreparationSummary() {
        assertThat(page).contains("data-prep-summary");
        assertThat(page).as("what is in the fight").contains("3 combatants");
        assertThat(page).as("who they are").contains(PreparationSurfaceFixture.MONSTER_NAME);
        assertThat(page).as("how hard it is").contains("Estimate:");
        assertThat(page).as("how to run it").contains(PreparationSurfaceFixture.PREP_TACTICS);
        assertThat(page).as("what it pays").contains(String.valueOf(PreparationSurfaceFixture.REWARD_XP_TOTAL));
        assertThat(page).as("where it belongs").contains(PreparationSurfaceFixture.PREP_SCENE_KEY);
    }

    @Test
    void theRunActionIsSingularAndObvious() {
        assertThat(page).contains("data-run-action");
        assertThat(page).contains("/encounters/" + seeded.encounterId() + "/run");
    }

    @Test
    void rawSetupFormsAreGone() {
        assertThat(page).doesNotContain("combatant-quickadd");
        assertThat(page).doesNotContain("libraryAdd");
        assertThat(page).doesNotContain("threatAdd");
        assertThat(page).doesNotContain("name=\"tactics\"");
        assertThat(page).doesNotContain("encounterRewardsForm");
        assertThat(page).as("no delete on the reading surface").doesNotContain("hx-delete");
    }

    @Test
    void theSetupSurfaceIsOneLinkAway() {
        assertThat(page).contains("/encounters/" + seeded.encounterId() + "/setup");
    }

    @Test
    void runActivatesTheEncounterAndLandsInTheCockpit() throws Exception {
        mvc.perform(post("/campaigns/{c}/encounters/{e}/run",
                        seeded.campaignId(), seeded.encounterId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + seeded.campaignId() + "/session"));

        assertThat(encounters.getById(seeded.encounterId()).status()).isEqualTo("ACTIVE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=EncounterPrepSummaryTest`
Expected: FAIL — `data-prep-summary` missing; `/run` returns 404 or 405.

- [ ] **Step 3: Add the run action**

In `EncounterController`, add after `activate` (line 101):

```java
    /**
     * The one deliberate action between a prepared encounter and the table: activate it and
     * land in the cockpit. Activating an encounter closes any other active one (see
     * EncounterService.activate), so this is safe to call from a stale page.
     */
    @PostMapping("/{id}/run")
    public String run(@PathVariable UUID campaignId, @PathVariable UUID id) {
        if (!"ACTIVE".equals(encounterService.getById(id).status())) {
            encounterService.activate(id);
        }
        return "redirect:/campaigns/" + campaignId + "/session";
    }
```

- [ ] **Step 4: Write the preparation summary fragment**

Create `src/main/resources/templates/encounter/_prep-summary.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div th:fragment="prepSummary" class="prep-summary" data-prep-summary
     th:with="monsters=${combatants.?[kind == 'MONSTER']},
              pcs=${combatants.?[kind == 'PC']},
              unsetInitiative=${combatants.?[initiative == null]}">

  <dl class="prep-summary__facts">
    <div>
      <dt>Roster</dt>
      <dd>
        <span th:text="|${#lists.size(combatants)} combatants|">0 combatants</span>
        <span class="text-muted u-text-sm"
              th:text="|${#lists.size(monsters)} monsters · ${#lists.size(pcs)} PCs|">0 monsters</span>
      </dd>
    </div>
    <div th:if="${difficulty.rating != 'N/A'}">
      <dt>Difficulty</dt>
      <dd>
        <span class="badge badge-rating"
              th:classappend="'badge-' + ${#strings.toLowerCase(difficulty.rating)}"
              th:text="'Estimate: ' + ${difficulty.rating}">Estimate: LOW</span>
        <span class="text-muted u-text-sm" th:text="|${difficulty.adjustedXp} XP vs threshold ${difficulty.partyThreshold}|">XP</span>
      </dd>
    </div>
    <div th:if="${prep.sceneKey != null and !prep.sceneKey.isBlank()}">
      <dt>Scene</dt>
      <dd th:text="${prep.sceneKey}">GW1</dd>
    </div>
    <div th:if="${encounter.mapId != null}">
      <dt>Map</dt>
      <dd><a th:href="@{/campaigns/{cid}/maps/{mid}/play(cid=${campaignId},mid=${encounter.mapId})}"
             target="_blank">Open battle map</a></dd>
    </div>
    <div th:if="${waves != null and !waves.isEmpty()}">
      <dt>Waves</dt>
      <dd th:text="|${#lists.size(waves)} configured|">0 configured</dd>
    </div>
    <div th:if="${rewards.xpTotal != null}">
      <dt>Rewards</dt>
      <dd>
        <span th:text="|${rewards.xpTotal} XP total|">0 XP total</span>
        <span class="text-muted u-text-sm" th:if="${rewards.xpPerPc != null}"
              th:text="|${rewards.xpPerPc} per character|">per character</span>
      </dd>
    </div>
    <div th:if="${encounterCombatCue != null}">
      <dt>Audio</dt>
      <dd th:text="${encounterCombatCue.name}">Combat cue</dd>
    </div>
    <div th:if="${!unsetInitiative.isEmpty()}">
      <dt>Initiative</dt>
      <dd class="u-text-warning"
          th:text="|${#lists.size(unsetInitiative)} combatant(s) still unset — resolved in the cockpit|">unset</dd>
    </div>
  </dl>

  <div class="prep-summary__notes" th:if="${prep.tactics != null or prep.morale != null
                                            or prep.environment != null or prep.surrender != null
                                            or prep.scalingNotes != null}">
    <h3>How to run it</h3>
    <p th:if="${prep.tactics != null and !prep.tactics.isBlank()}">
      <strong>Tactics.</strong> <span th:text="${prep.tactics}">Tactics</span>
    </p>
    <p th:if="${prep.morale != null and !prep.morale.isBlank()}">
      <strong>Morale.</strong> <span th:text="${prep.morale}">Morale</span>
    </p>
    <p th:if="${prep.surrender != null and !prep.surrender.isBlank()}">
      <strong>Surrender.</strong> <span th:text="${prep.surrender}">Surrender</span>
    </p>
    <p th:if="${prep.environment != null and !prep.environment.isBlank()}">
      <strong>Environment.</strong> <span th:text="${prep.environment}">Environment</span>
    </p>
    <p th:if="${prep.scalingNotes != null and !prep.scalingNotes.isBlank()}">
      <strong>Scaling.</strong> <span th:text="${prep.scalingNotes}">Scaling</span>
    </p>
    <p class="text-muted u-text-sm" th:if="${prep.sourceLocator != null and !prep.sourceLocator.isBlank()}"
       th:text="|Source: ${prep.sourceLocator}|">Source</p>
  </div>

  <div class="prep-summary__notes" th:if="${encounter.lairActionName != null and !encounter.lairActionName.isBlank()}">
    <h3>Lair actions</h3>
    <p><strong th:text="${encounter.lairActionName}">Lair action</strong></p>
    <p th:text="${encounter.lairActionDescription}">Description</p>
  </div>

  <details class="prep-summary__estimate">
    <summary>Estimate source and assumptions</summary>
    <p th:text="${difficulty.source}">2014 DMG encounter XP thresholds</p>
    <ul>
      <li th:each="assumption : ${difficulty.assumptions}" th:text="${assumption}">Assumption</li>
    </ul>
  </details>
</div>
</html>
```

- [ ] **Step 5: Rebuild the encounter overview**

Replace `src/main/resources/templates/encounter/detail.html` in full:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper — ' + ${encounter.name}">DMHelper — Encounter</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>

    <div class="app-shell">
        <th:block th:replace="~{fragments/_appnav :: appnav}"></th:block>
        <main class="app-main" data-surface="read">
            <div class="page-header">
                <div>
                    <div class="page-header-eyebrow">Encounters</div>
                    <h1 th:text="${encounter.name}">Encounter Name</h1>
                    <div class="rule-taper rule-taper--gold"></div>
                    <div class="detail-meta">
                        <span th:if="${encounter.status == 'ACTIVE'}" class="u-text-success u-fw-bold">ACTIVE</span>
                        <span th:if="${encounter.status == 'PLANNED'}" class="u-text-info u-fw-bold">PLANNED</span>
                        <span th:if="${encounter.status == 'DONE'}" class="text-muted u-fw-bold">DONE</span>
                        <span th:if="${encounter.round > 0}" th:text="' · Round ' + ${encounter.round}"> · Round 1</span>
                    </div>
                </div>
                <div class="page-header-actions">
                    <form th:if="${encounter.status != 'ACTIVE'}" method="post"
                          th:action="@{/campaigns/{cid}/encounters/{eid}/run(cid=${campaignId}, eid=${encounter.id})}"
                          class="u-inline">
                        <button type="submit" class="btn btn-primary" data-run-action>Run this encounter</button>
                    </form>
                    <a th:if="${encounter.status == 'ACTIVE'}" class="btn btn-primary" data-run-action
                       th:href="@{/campaigns/{cid}/session(cid=${campaignId})}">Open in cockpit</a>
                    <a class="btn btn-ghost"
                       th:href="@{/campaigns/{cid}/encounters/{eid}/setup(cid=${campaignId}, eid=${encounter.id})}">Setup</a>
                    <a class="btn btn-ghost"
                       th:href="@{/campaigns/{cid}/encounters(cid=${campaignId})}">&larr; All encounters</a>
                </div>
            </div>

            <div class="detail-section">
                <th:block th:replace="~{encounter/_prep-summary :: prepSummary}"></th:block>
            </div>

            <div class="detail-section">
                <h2>Combatants</h2>
                <table class="data-table" th:if="${not #lists.isEmpty(combatants)}">
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Kind</th>
                            <th>HP</th>
                            <th>Initiative</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr th:each="c : ${combatants}"
                            th:classappend="${c.defeated} ? 'row-defeated' : (${c.bloodied} ? 'row-bloodied' : '')">
                            <td th:text="${c.name}">Name</td>
                            <td th:text="${c.kind}">Kind</td>
                            <td th:text="${c.currentHp + ' / ' + c.maxHp}">HP</td>
                            <!-- Zero is a legal initiative; only a null is unset (workstream A3). -->
                            <td th:text="${c.initiative != null} ? ${c.initiative} : 'not set'">0</td>
                            <td>
                                <span th:if="${c.defeated}" class="u-text-danger">Defeated</span>
                                <span th:if="${c.bloodied and !c.defeated}" class="u-text-warning">Bloodied</span>
                                <span th:if="${!c.bloodied and !c.defeated}" class="u-text-success">Healthy</span>
                            </td>
                        </tr>
                    </tbody>
                </table>
                <th:block th:if="${#lists.isEmpty(combatants)}">
                    <th:block th:replace="~{common/_empty-state :: empty-state(
                        'No combatants yet.', 'Add combatants', @{/campaigns/{cid}/encounters/{eid}/setup(cid=${campaignId}, eid=${encounter.id})}, null)}"></th:block>
                </th:block>
            </div>

            <div class="u-mt-lg">
                <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${campaignId}, targetType='ENCOUNTER', targetId=${encounter.id})}"></th:block>
            </div>
        </main>
    </div>
</body>
</html>
```

- [ ] **Step 6: Add the summary styles**

Append to `src/main/resources/static/css/surfaces.css`:

```css
.prep-summary__facts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
    gap: var(--space-md);
    margin-bottom: var(--space-lg);
}
.prep-summary__facts dt {
    font-size: var(--text-xs);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--color-text-muted);
}
.prep-summary__facts dd { margin: 0; font-variant-numeric: tabular-nums; }
.prep-summary__notes { max-width: 62ch; margin-bottom: var(--space-lg); }
.prep-summary__notes p { margin-bottom: var(--space-xs); }
.prep-summary__estimate > summary { cursor: pointer; color: var(--color-text-muted); font-size: var(--text-sm); }
```

- [ ] **Step 7: Run the tests**

Run: `./mvnw -q test -Dtest='EncounterPrepSummaryTest,EncounterSetupSurfaceTest,EncounterControllerTest,EncounterTemplateContractTest'`
Expected: PASS.

If `theOverviewReadsAsAPreparationSummary` fails on `3 combatants`, check the SpEL projection syntax `${combatants.?[kind == 'MONSTER']}` against `CombatantDto.kind()` — it is a `String`, so the comparison holds; a failure means the model attribute name drifted.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/encounter src/main/resources/static/css/surfaces.css \
        src/main/java/dev/hendrikhoemberg/dmhelper/encounter src/test/java/dev/hendrikhoemberg/dmhelper/encounter
git commit -m "feat(encounter): make the overview a preparation summary with one run action"
```

---

## Task 9: Adventure overview keeps reading and hides organizing

Spec §9.2: *"Adventure overview: preserve chapter grouping, filtering and content affordances at published-campaign scale."* The grouping, filter and affordances already exist and must survive; what must leave the reading flow are the per-chapter reorder/edit/delete controls.

**Files:**
- Modify: `src/main/resources/templates/adventure/_chapter-list.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureDetailDensityTest.java:76-85`
- Test: same file, extended

**Interfaces:**
- Produces: a single page-level `<details class="organize-panel" data-organize-mode>` holding "+ New chapter" and, per chapter, move-up/move-down/edit/delete. Chapter blocks keep `data-chapter-block`, `data-scene-filter`, `data-status-legend` and `data-affordance` markers; they no longer contain `data-chapter-controls`.

- [ ] **Step 1: Write the failing test**

In `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureDetailDensityTest.java`, replace `chapterControlsAreBoundToTheChapterHeaderNotTheLastScene` with:

```java
    @Test
    void chapterManagementIsBehindAnOrganizeDisclosure() {
        int organizeAt = body.indexOf("data-organize-mode");
        assertThat(organizeAt).as("an organize panel must exist").isGreaterThan(-1);
        assertThat(body.substring(Math.max(0, organizeAt - 200), organizeAt))
                .as("reordering and deletion must not read as part of the chapter list")
                .contains("<details");
    }

    @Test
    void theReadingFlowCarriesNoReorderOrDeleteControls() {
        int organizeEnd = body.indexOf("</details>", body.indexOf("data-organize-mode"));
        String readingFlow = body.substring(organizeEnd);

        assertThat(readingFlow).as("chapter rows are for reading").doesNotContain("data-chapter-controls");
        assertThat(readingFlow).doesNotContain("hx-confirm=\"Delete this chapter and all its scenes?\"");
        assertThat(readingFlow).doesNotContain("hx-vals='{\"direction\"");
    }

    @Test
    void theOrganizePanelStillOffersEveryChapterAction() {
        int organizeAt = body.indexOf("data-organize-mode");
        int organizeEnd = body.indexOf("</details>", organizeAt);
        String panel = body.substring(organizeAt, organizeEnd);

        assertThat(panel).contains("New Chapter");
        assertThat(panel).contains("data-chapter-controls");
        assertThat(panel).contains("hx-confirm=\"Delete this chapter and all its scenes?\"");
        assertThat(panel).contains("Teil 1: Auf der Straße");
        assertThat(panel).contains("Teil 2: Die Spinne");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dtest=AdventureDetailDensityTest`
Expected: FAIL — `an organize panel must exist expected: > -1 but was: -1`.

- [ ] **Step 3: Restructure the chapter list**

In `src/main/resources/templates/adventure/_chapter-list.html`:

1. Replace the `<h2>Chapters …</h2>` heading and the `#chapter-form-placeholder` div (lines 3–9) with a plain heading plus the organize panel:

```html
    <h2>Chapters</h2>

    <details class="organize-panel" data-organize-mode>
      <summary>Organize chapters</summary>
      <div class="organize-panel__body">
        <button class="btn btn-primary btn-sm"
                th:hx-get="@{/campaigns/{cid}/adventures/{aid}/chapters/new(cid=${campaignId},aid=${adventure.id})}"
                hx-target="#chapter-form-placeholder"
                hx-swap="innerHTML">+ New Chapter</button>
        <div id="chapter-form-placeholder"></div>

        <div th:each="ch : ${chapters}" class="organize-panel__row">
          <span class="organize-panel__title" th:text="${ch.title}">Chapter</span>
          <div class="chapter-controls" data-chapter-controls>
            <button class="btn btn-ghost btn-sm" title="Move earlier"
                    th:hx-put="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/move(cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                    hx-vals='{"direction": -1}'
                    hx-target="closest .detail-section" hx-swap="outerHTML">&uarr;</button>
            <button class="btn btn-ghost btn-sm" title="Move later"
                    th:hx-put="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/move(cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                    hx-vals='{"direction": 1}'
                    hx-target="closest .detail-section" hx-swap="outerHTML">&darr;</button>
            <button class="btn btn-ghost btn-sm"
                    th:hx-get="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/edit(cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                    hx-target="closest .organize-panel__row" hx-swap="outerHTML">Edit</button>
            <button class="btn btn-danger btn-sm"
                    th:hx-delete="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}(cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                    hx-confirm="Delete this chapter and all its scenes?"
                    hx-target="closest .detail-section" hx-swap="outerHTML">Delete</button>
          </div>
        </div>
      </div>
    </details>
```

2. Delete the per-chapter `<div class="chapter-controls" data-chapter-controls>…</div>` block from inside the chapter `<details>` (old lines 35–51).

Everything else in the file — the filter input, the status legend, the chapter summaries with progress counters, the scene rows and their affordance markers — stays exactly as it is.

- [ ] **Step 4: Style the organize panel**

Append to `src/main/resources/static/css/surfaces.css`:

```css
.organize-panel { margin-bottom: var(--space-md); }
.organize-panel > summary { cursor: pointer; color: var(--color-text-muted); font-size: var(--text-sm); }
.organize-panel__body { padding: var(--space-sm) 0; }
.organize-panel__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-md);
    padding: var(--space-xxs) 0;
    border-bottom: 1px solid var(--color-border);
}
.organize-panel__title { font-weight: 600; }
```

- [ ] **Step 5: Run the tests**

Run: `./mvnw -q test -Dtest='AdventureDetailDensityTest,AdventureControllerTest,AdventureIndexTest'`
Expected: PASS.

The chapter-edit handler swaps into `closest .organize-panel__row`; if `AdventureController`'s chapter edit/update returns a fragment that assumed `.chapter-block`, read the handler and keep the swap target consistent with what it returns. Adjust the `hx-target` in the markup, not the controller.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/adventure/_chapter-list.html \
        src/main/resources/static/css/surfaces.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureDetailDensityTest.java
git commit -m "feat(adventure): move chapter organizing out of the reading flow"
```

---

## Task 10: Lock the separation with a guard, cover the new routes, record the decision

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/web/SurfaceSeparationContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java`
- Create: `docs/superpowers/verification/2026-07-24-d-surface-separation.md`

**Interfaces:**
- Consumes: `SurfaceModeContractTest.governedSurfaces()` (Task 1, extended in Tasks 4, 5 and 7) and the fragment files each surface owns.
- Produces: the enforced rule set — Read/Run templates and their owned fragments contain no package/export/import action, no reorder action, no large metadata form, and no destructive control in the page header.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/web/SurfaceSeparationContractTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 9.1. Read and Run surfaces may link to Edit and Admin, but must not
 * embed their tooling. This is a file-level guard: it is cheap, it names the offending file,
 * and it fails the moment a form drifts back into a reading surface.
 */
class SurfaceSeparationContractTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /**
     * Read/Run page -> every fragment file that page owns.
     * `adventure/_chapter-list.html` is deliberately absent: chapter reordering legitimately
     * lives on the adventure page inside an explicit Organize disclosure, and
     * AdventureDetailDensityTest proves it stays out of the reading flow.
     */
    private static final Map<String, List<String>> READ_AND_RUN_SURFACES = Map.of(
            "campaigns/detail.html", List.of("campaigns/_readiness.html"),
            "adventure/detail.html", List.of(),
            "adventure/scene-detail.html", List.of("adventure/_scene-rail.html", "adventure/_scene-body.html"),
            "encounter/detail.html", List.of("encounter/_prep-summary.html"),
            "party/list.html", List.of("party/_roster.html"),
            "session/cockpit.html", List.of("session/_cockpit-workbench.html"));

    /** Substrings that mark tooling belonging to Edit or Admin. */
    private static final Map<String, String> FORBIDDEN = Map.of(
            "/package", "campaign package export",
            "/export", "data export",
            "hx-post=\"/campaigns/import", "campaign import",
            "hx-vals='{\"direction\"", "reordering",
            "name=\"sourceLocator\"", "source-metadata authoring",
            "data-structured-metadata", "structured metadata authoring");

    @Test
    void readAndRunSurfacesEmbedNoEditOrAdminTooling() throws IOException {
        for (Map.Entry<String, List<String>> surface : READ_AND_RUN_SURFACES.entrySet()) {
            for (String file : allFilesOf(surface)) {
                String html = Files.readString(TEMPLATES.resolve(file));
                FORBIDDEN.forEach((needle, what) ->
                        assertThat(html)
                                .as("%s is part of a read/run surface and must not carry %s", file, what)
                                .doesNotContain(needle));
            }
        }
    }

    @Test
    void readAndRunSurfacesCarryNoDestructiveActionInTheirHeader() throws IOException {
        for (String page : READ_AND_RUN_SURFACES.keySet()) {
            String html = Files.readString(TEMPLATES.resolve(page));
            int headerAt = html.indexOf("page-header-actions");
            if (headerAt == -1) {
                continue; // the cockpit uses a command bar, covered by the rule below
            }
            String header = html.substring(headerAt, html.indexOf("</div>", headerAt));
            assertThat(header)
                    .as("%s must not offer a destructive action in its page header", page)
                    .doesNotContain("btn-danger");
        }
    }

    @Test
    void theCockpitContainsRuntimeModulesOnly() throws IOException {
        try (var stream = Files.list(TEMPLATES.resolve("session/modules"))) {
            for (Path module : stream.toList()) {
                String html = Files.readString(module);
                assertThat(html)
                        .as("%s is a runtime module: prep authoring belongs to the edit surfaces", module)
                        .doesNotContain("name=\"sourceLocator\"")
                        .doesNotContain("/package")
                        .doesNotContain("hx-vals='{\"direction\"");
            }
        }
    }

    @Test
    void everyEditAndAdminSurfaceIsReachableFromItsReadSurface() throws IOException {
        assertThat(Files.readString(TEMPLATES.resolve("campaigns/detail.html")))
                .as("campaign home links to settings").contains("/settings");
        assertThat(Files.readString(TEMPLATES.resolve("adventure/_scene-rail.html")))
                .as("scene rail links to its structure editor").contains("/structure");
        assertThat(Files.readString(TEMPLATES.resolve("encounter/detail.html")))
                .as("encounter overview links to its setup surface").contains("/setup");
    }

    private static List<String> allFilesOf(Map.Entry<String, List<String>> surface) {
        return java.util.stream.Stream
                .concat(java.util.stream.Stream.of(surface.getKey()), surface.getValue().stream())
                .toList();
    }
}
```

- [ ] **Step 2: Run test to verify it passes or names a real leak**

Run: `./mvnw -q test -Dtest=SurfaceSeparationContractTest`
Expected: PASS if Tasks 3–9 are complete. If it fails, the message names the file and the tooling. Default to fixing the template. Only relax the rule when the flagged control is genuinely *runtime* state rather than authoring — and then add the exception to the map with a comment saying why, never by deleting an assertion. Two known judgement calls:

- `campaigns/_readiness.html` contains repair links to preparation surfaces — those are links, not embedded tooling, and match no forbidden substring.
- `session/_cockpit-workbench.html` contains preset controls — runtime state, allowed.

- [ ] **Step 3: Cover the new routes in the render smoke test**

In `src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java`, extend `pages()`. The class currently seeds only `PopulatedCampaignFixture`; add the preparation fixture too:

```java
    @Autowired private dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture prepFixture;

    private dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture.Seeded prepared;
```

and in `seed()`:

```java
        seeded = fixture.seed();
        prepared = prepFixture.seed();
```

then add to the returned list, after the existing scene entry:

```java
                c + "/adventures/" + seeded.adventureId() + "/scenes/" + seeded.richSceneId() + "/structure",
                c + "/settings",
```

and, at the top of `pages()`, next to the existing `String c = …` line:

```java
        String p = "/campaigns/" + prepared.campaignId();
```

with these entries appended inside the same `List.of(...)` call:

```java
                p + "/party",
                p + "/encounters/" + prepared.encounterId(),
                p + "/encounters/" + prepared.encounterId() + "/setup",
```

- [ ] **Step 4: Run the full suite**

Run: `./mvnw -q verify`
Expected: BUILD SUCCESS. Record the actual output. If anything fails, fix it before continuing — this is the task where D's changes meet every other test in the repository.

- [ ] **Step 5: Write the verification note**

Create `docs/superpowers/verification/2026-07-24-d-surface-separation.md`:

```markdown
# Workstream D verification — Read/Run versus Edit/Admin

**Plan:** `docs/superpowers/plans/2026-07-24-d-read-run-edit-admin-separation.md`
**Spec:** `docs/superpowers/specs/2026-07-22-phandelver-all-in-one-corrective-design.md` section 9

## Surface map

| Surface | Route | Mode |
|---|---|---|
| Campaign home | `/campaigns/{id}` | read |
| Campaign settings | `/campaigns/{id}/settings` | admin |
| Adventure overview | `/campaigns/{id}/adventures/{aid}` | read |
| Scene | `/campaigns/{id}/adventures/{aid}/scenes/{sid}` | read |
| Scene structure | `/campaigns/{id}/adventures/{aid}/scenes/{sid}/structure` | edit |
| Encounter | `/campaigns/{id}/encounters/{eid}` | read |
| Encounter setup | `/campaigns/{id}/encounters/{eid}/setup` | edit |
| Party roster | `/campaigns/{id}/party` | read |
| Session cockpit | `/campaigns/{id}/session` | run |

## Automated coverage

- `SurfaceModeContractTest` — every governed page declares exactly one mode on its `<main>`.
- `SurfaceSeparationContractTest` — read/run surfaces embed no edit/admin tooling; every edit/admin surface is reachable by one link.
- `SceneReadSurfaceTest`, `SceneStructureEditSurfaceTest`, `EncounterPrepSummaryTest`,
  `EncounterSetupSurfaceTest`, `PartyRosterTest`, `CampaignAdminSurfaceTest`,
  `AdventureDetailDensityTest` — per-surface behavior.
- `FullPageRenderSmokeTest` — the three new pages render complete documents with no lazy-init truncation.

## Manual acceptance checkpoint

At 1366×768, from a fresh campaign with an imported adventure:

1. Campaign home shows readiness and a Run session entry; no export, import, delete or edit form is on the page.
2. Settings offers export, import, rename and delete, and returns to the home.
3. The scene page reads as prose with a concise rail; one Edit link reaches every authoring form; the structure page previews what the reader sees.
4. The encounter page states roster, difficulty, tactics, rewards and scene in one screen and runs in one click.
5. The party page fits the whole roster without scrolling at four members and shows AC, HP, passives and conditions per row.
6. No action performed on a read surface changes structure; no action on an edit surface is needed to run the encounter.

## Not covered by D

- Visual-system refinement (workstream E): typography scale, gold usage, control styling.
- The party bulk actions still use `prompt()`/`alert()`; §10.3 covers that in E.
- Remaining pages (world, quests, notes, library, maps, handouts) have not been assigned a surface mode. Extend `SurfaceModeContractTest.governedSurfaces()` as they are corrected.
```

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/web docs/superpowers/verification
git commit -m "test(surfaces): lock read/run separation and cover the new surfaces"
```

---

## Self-review notes

**Spec §9 coverage**

| Requirement | Task |
|---|---|
| §9.1 four surface modes, declared | 1 |
| §9.1 Read/Run may link to Edit, must not embed metadata forms | 5, 6, 7, 8, 10 |
| §9.1 delete/reorder/package out of the live cockpit | 10 (`theCockpitContainsRuntimeModulesOnly`) |
| §9.2 scene detail — narrative dominates, structured content concise, metadata behind Edit | 5, 6 |
| §9.2 encounter detail — readable prep summary, clear Run, progressive setup | 7, 8 |
| §9.2 party — scan-friendly roster, secondary edit | 3 |
| §9.2 adventure overview — grouping, filtering and affordances preserved | 9 |
| §9.2 campaign home — readiness kept, Run entry points added, no inline edit form | 4 |
| F12 (read/run mixed with edit/admin) | 4–9 |
| F13 (party and encounter dense but informationally weak) | 3, 8 |

**Out of scope, deliberately:** F11 and F14 (rail widths, typography, colour) belong to workstream E; the initiative model is A3 and is only *displayed* correctly here; no database migration is needed or permitted.

**Known coupling to check while executing:** `SceneController` keeps two loaders (`loadSceneRail`, `loadStructureEditor`) — a handler that returns the wrong one swaps the wrong markup into the DOM. Task 6 Step 5 lists exactly which handler uses which.
