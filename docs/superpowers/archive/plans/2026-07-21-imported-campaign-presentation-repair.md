# Imported Campaign Presentation Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make DMHelper present a fully populated, published-adventure-scale campaign correctly — no truncated pages, read-aloud text legible, scenes selectable from the cockpit, campaign scale visible, and no raw enum identifiers in the UI.

**Architecture:** Seven independent tasks against the existing Spring Boot 4 + Thymeleaf + HTMX + Alpine stack. Task 1 introduces one shared populated test fixture and fixes the P0 lazy-initialization truncation with JPA `@EntityGraph` fetch plans. Tasks 2–3 restructure the scene page and cockpit story rail. Task 4 introduces a `#enums` Thymeleaf expression object for human labels. Tasks 5–7 add aggregate counts and density handling to the dashboard, adventures index and adventure detail. No changes to the campaign package format, import pipeline, or persistence schema.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Data JPA (Hibernate), H2, Flyway, Thymeleaf 3.1, HTMX, Alpine.js, JUnit 5, AssertJ, Maven wrapper (`./mvnw`).

## Global Constraints

- **Do not change** the campaign package format, JSON schema, validation, or import/export code. §5 of the spec declares these verified correct at real scale.
- **Do not change** the persistence schema. No new Flyway migrations. Fetch plans (`@EntityGraph`) are metadata on repository query methods and require no DDL.
- **Do not delete or weaken the `.card p` clamp rule** at `src/main/resources/static/css/components.css:63-71`. It is load-bearing for the notes list, threat list, rollable-table list, audio cards and all library cards.
- **`spring.jpa.open-in-view=false`** (`src/main/resources/application.properties:12`) stays false. Every lazy association a template dereferences must be fetched inside the service transaction.
- **`th:value=`, `th:selected=` and `hx-vals` must keep raw enum constants.** Only `th:text` display positions get human labels.
- Preserve the existing visual identity: gold-on-dark, `--font-display` Cinzel for headings, `--font-book` Alegreya for prose, drop caps on scene bodies. No redesign.
- Build/test command is always `./mvnw` from the repository root (`/home/hendrik/Documents/Coding/DMHelper`).
- Commit after each task, on a feature branch (not `Main`).

## Corrections to the specification

The spec was written from a browser walkthrough. Three of its claims do not survive contact with the code. **Follow this plan, not the spec, where they disagree:**

1. **`.card p` blast radius is much smaller than §F2 claims.** The spec says "55 templates use `class=\"card`" and warns that the quest list, campaign list and notes list depend on the clamp. Verified: the clamp is a descendant selector `.card p` and only bites `<p>` elements *inside* a `.card`. The quest list (`quest/list.html:33`) uses `<div class="card-body">`, which has **no CSS rule at all**. The campaign list (`campaigns/_card.html`) uses `.book-cover` and has no `<p>`. Neither is clamped today. The genuinely clamped surfaces are `notes/_card.html:12`, `threat/list.html:48`, `rollable-table/list.html:50`, `audio/_card.html:5`, and the library `_*-card.html` fragments. Consequence: **no `.card--clamp` opt-in class is needed and no 55-template audit is required.** Task 2 leaves `.card p` completely untouched.
2. **`.structured-block` and `.structured-read-aloud` have no CSS whatsoever.** The spec calls the cockpit story rail "the model this page should follow" and implies those classes carry styling. `grep -rn "structured" src/main/resources/static/css/` returns nothing. The rail reads well only because the clamp does not reach it. Task 2 must **write** these styles, not merely reuse the class names.
3. **The spec undercounts F1 by three pages.** Beyond `/world/npcs` and `/world/npcs/{id}`:
   - `world/locations-list.html:35-36` dereferences `loc.parentLocation.name`, and `WorldService.getLocations` (line 234-241) force-initializes `travelLocations` and `encounters` but **not** `parentLocation`. `/world/locations` truncates for any campaign with a nested location.
   - `world/locations-detail.html:56-59` dereferences `location.locationAudioCue.name`, and `WorldService.getLocation` (line 221-231) does not initialize it. `/world/locations/{id}` truncates for any location with an audio cue.
   - `WorldController.factionDetail` (line 259-261) calls `worldService.getClocks(campaignId)` and then filters on `c.getFaction()` **in the controller, after the read-only transaction has closed**. `FactionClock.faction` is `FetchType.LAZY`. `/world/factions/{id}` truncates for any faction with a clock.

   Task 1 fixes all five.

Also note: the spec's "33 occurrences of `.name()}` in `th:text`" (§F5) counts false positives. `sheet/_features.html:6`, `sheet/_attacks.html:15` and `sheet/_resources.html:3` call `.name()` on Java **records**, not enums. The true conversion set is enumerated explicitly in Task 4.

And one further pre-existing asset the spec did not find: **`setCurrentScene(sceneId)` is already implemented** in `src/main/resources/static/js/session-cockpit.js:172-186`. F3 needs a `<select>` and a one-line guard, not a new client method.

## Decisions (resolving spec §6)

1. **F1 fix strategy — `@EntityGraph` fetch joins, not read DTOs.** The spec calls DTOs "more durable" but "touches more code". Chosen: fetch joins. They are three annotations, fix the N+1 on the NPC list as a side effect (30 NPCs currently cost 60 queries), require no template changes, and leave the existing `WorldService`/`WorldController` contract intact. A DTO layer for the world module would be a large refactor delivering no additional user-visible behaviour, and the durability argument is answered instead by the log assertion in Task 1 Step 3, which fails on *any* future lazy-init regression regardless of which page introduces it.
2. **F2 clamp strategy — neither `.card--clamp` nor an override.** The spec's premise is wrong (see Correction 1): the only surface the clamp harms is `_action-rail.html:108`, and Task 2 moves that markup out of `.card` entirely. `.card p` is therefore left completely untouched and the 55-template audit is unnecessary. Task 2 Step 9 verifies rather than changes.
3. **F4 scope — three separate tasks (5, 6, 7).** They share a cause but no code, and a reviewer can meaningfully accept the dashboard while rejecting the adventure-detail density model.
4. **Fixture strategy — synthetic seed, not an imported package.** The converted LMoP package is `licenseClassification: NON_REDISTRIBUTABLE` and cannot be committed, so the alternative would mean authoring a synthetic package *and* the code to import it — strictly more work than seeding through the services directly, for a guard on the import path that the spec itself declares already verified (§5). A synthetic `.dmcampaign` round-trip test is worth its own plan later; it is not this plan's job.

---

## File Structure

**Created:**

| Path | Responsibility |
|---|---|
| `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java` | Spring `@Component` (test sources) that seeds one published-adventure-shaped campaign. Single source of truth for every scale-dependent test. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/support/LazyInitLogCapture.java` | Logback `ListAppender` wrapper that records `LazyInitializationException` occurrences during a page sweep. |
| `src/main/resources/templates/adventure/_scene-sections.html` | Read-only, full-width, full-length rendering of a scene's structured sections. Consumed by the scene detail page. |
| `src/main/resources/templates/session/_scene-picker.html` | Chapter-grouped scene `<select>` for the cockpit story rail. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelUtil.java` | Stateless `ENUM_CONSTANT` → `"Enum Constant"` mapper. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelDialect.java` | Exposes `EnumLabelUtil` to templates as `#enums`. Mirrors `MarkdownDialect`. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignScaleService.java` | Assembles aggregate entity counts for the campaign dashboard. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelUtilTest.java` | Unit tests for the label mapper. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneDetailPresentationTest.java` | Asserts scene sections render at full length in the main column. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitScenePickerTest.java` | Asserts the cockpit exposes a scene picker. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignDashboardScaleTest.java` | Asserts the dashboard reports campaign scale. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureIndexTest.java` | Asserts adventure cards carry chapter/scene counts. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureDetailDensityTest.java` | Asserts collapsible chapters, filter field and status legend. |

**Modified:**

| Path | Change |
|---|---|
| `src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java` | Seed via the fixture; sweep detail routes; assert no `LazyInitializationException`. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/world/data/WorldNpcRepository.java` | `@EntityGraph` on both finders. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/world/data/WorldLocationRepository.java` | `@EntityGraph` on both finders. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/world/service/WorldService.java` | Add `getClocksForFaction`. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/world/web/WorldController.java` | Use `getClocksForFaction` instead of a post-transaction filter. |
| `src/main/resources/templates/adventure/scene-detail.html` | Main column renders body + sections. |
| `src/main/resources/templates/adventure/_action-rail.html` | Sections become label-only rows; metadata form behind `<details>`. |
| `src/main/resources/static/css/components.css` | Add `.structured-block` family + `.card-header` wrap fix + `.chapter-*` density styles. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java` | Add `scenePickerGroups`, `adventureSummaries`. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java` | Publish `scenePickerGroups` to the cockpit and the standalone story rail. |
| `src/main/resources/templates/session/_story-rail.html` | Insert the picker in both branches. |
| `src/main/resources/static/js/session-cockpit.js` | One-line guard on the existing `setCurrentScene`. |
| ~25 templates | `.name()` → `#enums.label(...)` (exact list in Task 4). |
| `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java` | Publish `scale`. |
| `src/main/resources/templates/campaigns/detail.html` | Scale panel; edit form behind `<details>`. |
| `src/main/resources/templates/adventure/_adventure-list.html` | Card reading order + counts. |
| `src/main/resources/templates/adventure/_chapter-list.html` | Collapsible chapters, filter, affordances, legend, chapter-bound controls. |

---

### Task 1: Populated fixture + world-module fetch plans (F1, P0)

The browser-hanging defect and the regression harness that proves it. The fixture is built here because F1's test cannot exist without it; Tasks 2–7 then reuse it.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/support/LazyInitLogCapture.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java` (full rewrite)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/world/data/WorldNpcRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/world/data/WorldLocationRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/world/service/WorldService.java:443-451`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/world/web/WorldController.java:254-266`

**Interfaces:**
- Produces: `PopulatedCampaignFixture.seed()` returning `PopulatedCampaignFixture.Seeded`, a record with fields `campaignId, adventureId, chapterOneId, chapterTwoId, richSceneId, secondSceneId, factionId, locationId, childLocationId, npcId, questId, trapId, hazardId, tableId` (all `UUID`). Tasks 2–7 autowire `PopulatedCampaignFixture` and call `seed()`.
- Produces: `WorldService.getClocksForFaction(UUID campaignId, UUID factionId)` → `List<FactionClock>`.
- Consumes: nothing from earlier tasks.

---

- [ ] **Step 1: Create the git branch**

```bash
cd /home/hendrik/Documents/Coding/DMHelper
git checkout -b fix/imported-campaign-presentation
```

- [ ] **Step 2: Create the populated campaign fixture**

This is a `@Component` in test sources. `@SpringBootTest` component-scans from `dev.hendrikhoemberg.dmhelper`, and test classes are on the classpath, so the bean is discovered automatically — no `@Import` needed. Sliced tests (`@WebMvcTest`) do not scan `@Component`, so this is inert there.

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveCompletionMode;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestStatus;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableEntryWrite;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableService;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableWrite;
import dev.hendrikhoemberg.dmhelper.threat.data.*;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardService;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardWrite;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapService;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapWrite;
import dev.hendrikhoemberg.dmhelper.world.data.*;
import dev.hendrikhoemberg.dmhelper.world.service.WorldService;
import dev.hendrikhoemberg.dmhelper.world.service.WorldService.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seeds a campaign shaped like a published adventure: multiple chapters, enough scenes to
 * trigger list-density behaviour, and every optional association that templates dereference
 * populated at least once.
 *
 * <p>The repository's original fixtures created a Campaign, Adventure, Chapter and Scene and
 * no world-graph entities at all, which is why every defect in
 * docs/superpowers/specs/2026-07-21-imported-campaign-presentation-repair.md was invisible to
 * the suite. Adding assertions would not have helped; the fixtures needed data.
 */
@Component
public class PopulatedCampaignFixture {

    /** Identifiers of the seeded graph. All fields are non-null. */
    public record Seeded(
            UUID campaignId,
            UUID adventureId,
            UUID chapterOneId,
            UUID chapterTwoId,
            UUID richSceneId,
            UUID secondSceneId,
            UUID factionId,
            UUID locationId,
            UUID childLocationId,
            UUID npcId,
            UUID questId,
            UUID trapId,
            UUID hazardId,
            UUID tableId) {}

    /** Scenes seeded into chapter two, enough to exercise list density. */
    public static final int CHAPTER_TWO_SCENE_COUNT = 12;

    /** Verbatim boxed text long enough that a two-line clamp would visibly truncate it. */
    public static final String READ_ALOUD_BODY =
            "Auf dem Schreibtisch, zwischen Bestellungen für die Werkstatt, liegt ein "
            + "zusammengefalteter Brief. Das Wachssiegel ist gebrochen. Die Handschrift ist "
            + "sauber und eng, und der Text ist in einer Sprache verfasst, die keiner von euch "
            + "auf Anhieb erkennt — bis euch auffällt, dass die Unterschrift ein einzelnes "
            + "Wort ist, das ihr sehr wohl kennt.";

    public static final String SECRET_BODY =
            "Der Brief stammt von der Schwarzen Spinne. Wer ihn liest und Zwergisch beherrscht, "
            + "erfährt, dass die Mine bereits besetzt ist.";

    public static final String TREASURE_BODY =
            "In der verschlossenen Truhe unter dem Schreibtisch liegen 120 gp und ein Paar "
            + "Stiefel der Elfenhaftigkeit.";

    private final CampaignRepository campaigns;
    private final AdventureService adventures;
    private final SceneStructuredContentService structured;
    private final WorldService world;
    private final QuestService quests;
    private final TrapService traps;
    private final HazardService hazards;
    private final RollableTableService tables;

    public PopulatedCampaignFixture(CampaignRepository campaigns,
                                    AdventureService adventures,
                                    SceneStructuredContentService structured,
                                    WorldService world,
                                    QuestService quests,
                                    TrapService traps,
                                    HazardService hazards,
                                    RollableTableService tables) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.structured = structured;
        this.world = world;
        this.quests = quests;
        this.traps = traps;
        this.hazards = hazards;
        this.tables = tables;
    }

    @Transactional
    public Seeded seed() {
        Campaign campaign = new Campaign();
        campaign.setName("Populated Fixture Campaign");
        campaign.setDescription("Published-adventure-shaped fixture");
        UUID campaignId = campaigns.save(campaign).getId();

        Adventure adventure = adventures.createAdventure(
                campaignId, "Die Verlorene Probe", "A two-part fixture adventure", "Fixture, S. 1");
        UUID adventureId = adventure.getId();

        Chapter one = adventures.createChapter(adventureId, "Teil 1: Auf der Straße", "Opening chapter.");
        Chapter two = adventures.createChapter(adventureId, "Teil 2: Die Spinne", "Second chapter.");

        Scene rich = adventures.createScene(one.getId(), "Der Schreibtisch", "S1",
                "Ein enger Raum mit einem Schreibtisch aus dunklem Holz.");
        Scene second = adventures.createScene(one.getId(), "Der Gang", "S2",
                "Ein Gang, der nach Norden führt.");

        // Chapter two carries enough scenes that a flat list becomes unreadable.
        for (int i = 1; i <= CHAPTER_TWO_SCENE_COUNT; i++) {
            adventures.createScene(two.getId(), "Raum " + i, "R" + i, "Beschreibung für Raum " + i + ".");
        }

        // Structured content on the rich scene: the sections F2 must render in full.
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.READ_ALOUD, "Der Brief", READ_ALOUD_BODY, "Fixture, S. 22", 0));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.SECRET, "Absender", SECRET_BODY, "Fixture, S. 22", 1));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.TREASURE, "Truhe", TREASURE_BODY, "Fixture, S. 23", 2));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.DM_ADVICE, "Hinweis", "Lass die Gruppe suchen.", null, 3));

        structured.addCheck(campaignId, rich.getId(), new SceneCheckCommand(
                "Brief entziffern", "int", "investigation", 13, SceneCheckVisibility.PLAYER_FACING,
                "Der Absender wird klar.", "Nichts.", null, null, null, null, "Fixture, S. 22", 0));

        structured.addParticipant(campaignId, rich.getId(), new SceneParticipantCommand(
                "Späher der Redbrands", 2, SceneParticipantDisposition.HOSTILE,
                "Hinter der Tür", null, null, "Fixture, S. 22", 0));

        structured.addTransition(campaignId, rich.getId(), new SceneTransitionCommand(
                SceneTransitionKind.CHOICE, "Weiter in den Gang", second.getId(),
                null, null, "Nur wenn die Truhe offen ist.", "Fixture, S. 23", 0));

        structured.addLink(campaignId, rich.getId(), new SceneLinkCommand(
                SceneLinkRole.RELATED_SCENE, SceneLinkTargetScope.PACKAGE, "SCENE", second.getId(),
                null, null, "Der Gang", null, 0));

        // World graph. The NPC carries BOTH a faction and a location — this is the exact
        // shape that made /world/npcs truncate mid-render.
        Faction faction = world.createFaction(campaignId, new FactionCommand(
                "Orden des Panzerhandschuhs", "Ordnung herstellen", "Kontakte", null,
                null, "order", "Fixture, S. 30"));

        WorldLocation parent = world.createLocation(campaignId, new LocationCommand(
                "Phandalin", LocationKind.SETTLEMENT, null, null, null, null,
                "Ein Grenzdorf.", "Schmied, Gasthaus", "Die Redbrands halten den Ort.",
                null, null, "town", "Fixture, S. 28"));

        // A CHILD location, so locations-list.html's ${loc.parentLocation.name} is exercised.
        WorldLocation child = world.createLocation(campaignId, new LocationCommand(
                "Stonehill Inn", LocationKind.SITE, parent.getId(), null, null, null,
                "Das Gasthaus am Platz.", "Zimmer, Bier", null,
                null, null, "inn", "Fixture, S. 29"));

        WorldNpc npc = world.createNpc(campaignId, new NpcCommand(
                "Daran Edermath", "Obstbauer und Ex-Ritter", WorldDisposition.FRIENDLY,
                faction.getId(), child.getId(), null, null,
                "Ein hochgewachsener Halbelf mit weißem Haar.", "Ruhig, bedacht",
                "Will den Orden wiederbeleben.", "War früher Ritter.", "Langschwert",
                WorldNpcStatus.ALIVE, "ally", "Fixture, S. 30"));

        // A clock, so /world/factions/{id} exercises the post-transaction faction deref.
        world.createClock(campaignId, new ClockCommand(
                faction.getId(), "Der Orden formiert sich", 4, 1, null, null,
                "Füllt sich, wenn die Gruppe hilft.", "Fixture, S. 30", 0));

        world.createRelationship(campaignId, new RelationshipCommand(
                RelationshipKind.MEMBER_OF, "NPC", npc.getId(), "FACTION", faction.getId(),
                true, RelationshipKnowledge.PUBLIC, RelationshipStatus.ACTIVE,
                "Gründungsmitglied.", "Fixture, S. 30", 0));

        Quest quest = quests.createQuest(campaignId, new QuestCommand(
                "Die Mine finden", QuestStatus.NOT_STARTED,
                "Findet den Eingang zur Wave Echo Cave.", "Fixture, S. 40",
                "main", "500 gp", "Teil 1 abgeschlossen", null));
        quests.addObjective(campaignId, quest.getId(), new QuestObjectiveCommand(
                "Karte beschaffen", "Die Karte liegt bei Daran.", QuestObjectiveStatus.NOT_STARTED,
                QuestObjectiveCompletionMode.ALL, 0, "Fixture, S. 40"));

        Trap trap = traps.create(campaignId, new TrapWrite(
                "pit-trap", "Fallgrube", "Eine zehn Fuß tiefe Grube unter loser Erde.",
                ThreatSeverity.SETBACK, null, null, null, null, null, null,
                List.of(), null, null, null, List.of(), null,
                ThreatResetMode.MANUAL, null, null, null, List.of()), null);

        Hazard hazard = hazards.create(campaignId, new HazardWrite(
                "green-slime", "Grüner Schleim", "Ätzender Schleim an der Decke.",
                ThreatSeverity.SETBACK, 1, 4, HazardExposureMode.ON_ENTER,
                "Beim Betreten des Feldes", "10-Fuß-Feld", null,
                "1d6", List.of(DamageType.ACID), null, "Wird mit Feuer zerstört.",
                List.of()), null);

        RollableTable table = tables.create(campaignId, new RollableTableWrite(
                "wilderness", "Zufallsbegegnungen Wildnis", "Für Reisen zwischen Orten.",
                TableAddressMode.RANGE, "1d6", TableCategory.ENCOUNTER, List.of("travel"),
                List.of(
                        new RollableTableEntryWrite("goblins", 1, 3, null, "2 Goblins", null, List.of()),
                        new RollableTableEntryWrite("nothing", 4, 6, null, "Nichts passiert", null, List.of())
                )), null);

        return new Seeded(campaignId, adventureId, one.getId(), two.getId(),
                rich.getId(), second.getId(), faction.getId(), parent.getId(), child.getId(),
                npc.getId(), quest.getId(), trap.getId(), hazard.getId(), table.getId());
    }
}
```

Two things to check when this first compiles:

- `traps.create(...)`, `hazards.create(...)` and `tables.create(...)` are passed `null` provenance, matching `RollableTableServiceTest:174` (`service.create(campaignId, write, null)`). If `TrapService.create` or `HazardService.create` rejects a null `ContentProvenance`, construct an empty one the way `LibraryControllerCustomContentTest:97` does (`ContentProvenance prov = new ContentProvenance();`) and pass that instead.
- `TrapWrite` and `HazardWrite` argument order is copied from `TrapServiceTest.validWrite` (line 313) and `ThreatValidatorTest` (line 198). If the records have since changed shape, follow those call sites rather than the literal argument lists here.

- [ ] **Step 3: Create the lazy-init log capture helper**

The `</html>` assertion catches truncation only when it happens *before* the closing tag. A future template could throw after it. Capturing the log closes that gap.

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/LazyInitLogCapture.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Records whether any log event during a page sweep carried a LazyInitializationException,
 * directly or as a cause. The </html> assertion alone is insufficient: a template that
 * dereferences a detached proxy after the closing tag has been flushed would still pass it.
 */
public final class LazyInitLogCapture implements AutoCloseable {

    private static final String TARGET = "LazyInitializationException";

    private final Logger root;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public LazyInitLogCapture() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.WARN);
    }

    /** Messages that mention a lazy-initialization failure, empty when the sweep was clean. */
    public List<String> lazyInitFailures() {
        return appender.list.stream()
                .filter(LazyInitLogCapture::mentionsLazyInit)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static boolean mentionsLazyInit(ILoggingEvent event) {
        if (event.getFormattedMessage() != null && event.getFormattedMessage().contains(TARGET)) {
            return true;
        }
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            if (t.getClassName().contains(TARGET)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
```

- [ ] **Step 4: Rewrite the smoke test to sweep a populated campaign**

Replace the entire contents of `src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.web;

import dev.hendrikhoemberg.dmhelper.support.LazyInitLogCapture;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullPageRenderSmokeTest {

    @LocalServerPort private int port;
    @Autowired private PopulatedCampaignFixture fixture;

    // A finite timeout matters: the lazy-init failure abandons the response stream without
    // closing it, so an unbounded client hangs instead of failing.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeAll
    void seed() {
        seeded = fixture.seed();
    }

    List<String> pages() {
        String c = "/campaigns/" + seeded.campaignId();
        return List.of(
                "/campaigns",
                c,
                c + "/adventures",
                c + "/adventures/" + seeded.adventureId(),
                c + "/adventures/" + seeded.adventureId() + "/scenes/" + seeded.richSceneId(),
                c + "/encounters",
                c + "/maps",
                c + "/handouts",
                c + "/audio/cues",
                c + "/notes",
                c + "/party",
                c + "/sheets",
                c + "/treasury",
                c + "/ledger",
                c + "/world/npcs",
                c + "/world/npcs/" + seeded.npcId(),
                c + "/world/locations",
                c + "/world/locations/" + seeded.locationId(),
                c + "/world/locations/" + seeded.childLocationId(),
                c + "/world/factions",
                c + "/world/factions/" + seeded.factionId(),
                c + "/quests",
                c + "/quests/" + seeded.questId(),
                c + "/calendar",
                c + "/session",
                "/library",
                "/library/tables",
                "/library/tables/" + seeded.tableId(),
                "/library/traps",
                "/library/traps/" + seeded.trapId(),
                "/library/hazards",
                "/library/hazards/" + seeded.hazardId()
        );
    }

    @ParameterizedTest
    @MethodSource("pages")
    void pageRendersCompletely(String path) throws Exception {
        HttpResponse<String> response = get(path);

        assertThat(response.statusCode())
                .as("status for %s", path)
                .isEqualTo(200);
        assertThat(response.body())
                .as("body for %s must be a complete document", path)
                .isNotNull();
        assertThat(response.body().strip())
                .as("body for %s must end with </html> (truncation = lazy-init mid-render)", path)
                .endsWith("</html>");
    }

    @Test
    void fullSweepLogsNoLazyInitializationException() throws Exception {
        try (LazyInitLogCapture capture = new LazyInitLogCapture()) {
            for (String path : pages()) {
                get(path);
            }
            assertThat(capture.lazyInitFailures())
                    .as("no LazyInitializationException may be logged during a full page sweep")
                    .isEmpty();
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
```

- [ ] **Step 5: Run the test to verify it fails on the expected pages**

```bash
./mvnw test -Dtest=FullPageRenderSmokeTest
```

Expected: FAIL. At minimum these five parameterized cases fail with
`body for … must end with </html> (truncation = lazy-init mid-render)`:
`/world/npcs`, `/world/npcs/{npcId}`, `/world/locations`, `/world/locations/{childLocationId}`, `/world/factions/{factionId}`.
`fullSweepLogsNoLazyInitializationException` also fails.

If a page fails instead with a **status** assertion or a timeout, that is a different defect — stop and investigate before continuing.

- [ ] **Step 6: Add fetch plans to `WorldNpcRepository`**

Replace the whole file `src/main/java/dev/hendrikhoemberg/dmhelper/world/data/WorldNpcRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorldNpcRepository extends JpaRepository<WorldNpc, UUID> {

    // spring.jpa.open-in-view=false: the transaction closes before Thymeleaf renders, so every
    // association a template dereferences must be fetched here. npcs-list.html reads
    // npc.faction.name; npcs-detail.html reads faction and location id + name.
    @EntityGraph(attributePaths = {"faction", "location"})
    List<WorldNpc> findByCampaignIdOrderByNameAscIdAsc(UUID campaignId);

    @EntityGraph(attributePaths = {"faction", "location"})
    Optional<WorldNpc> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<WorldNpc> findByLocationId(UUID locationId);
    List<WorldNpc> findByFactionId(UUID factionId);
}
```

- [ ] **Step 7: Add fetch plans to `WorldLocationRepository`**

Replace the whole file `src/main/java/dev/hendrikhoemberg/dmhelper/world/data/WorldLocationRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorldLocationRepository extends JpaRepository<WorldLocation, UUID> {

    // locations-list.html reads loc.parentLocation.name for every row.
    @EntityGraph(attributePaths = {"parentLocation"})
    List<WorldLocation> findByCampaignIdOrderByNameAscIdAsc(UUID campaignId);

    // locations-detail.html reads parentLocation.name and locationAudioCue.name/providerId/category.
    @EntityGraph(attributePaths = {"parentLocation", "locationAudioCue"})
    Optional<WorldLocation> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<WorldLocation> findByLocationAudioCueId(UUID cueId);
}
```

- [ ] **Step 8: Add `getClocksForFaction` to `WorldService`**

In `src/main/java/dev/hendrikhoemberg/dmhelper/world/service/WorldService.java`, immediately after the existing `getClocks` method (which ends at line 451), insert:

```java
    /**
     * Clocks belonging to one faction. The controller previously loaded all campaign clocks and
     * filtered on clock.getFaction() after this read-only transaction had closed, which threw
     * LazyInitializationException mid-render because FactionClock.faction is LAZY.
     */
    @Transactional(readOnly = true)
    public List<FactionClock> getClocksForFaction(UUID campaignId, UUID factionId) {
        findFactionInCampaign(campaignId, factionId);
        return clockRepository.findByFactionIdOrderBySortOrderAscIdAsc(factionId);
    }
```

- [ ] **Step 9: Use it from `WorldController.factionDetail`**

In `src/main/java/dev/hendrikhoemberg/dmhelper/world/web/WorldController.java`, replace lines 259-261:

```java
        model.addAttribute("clocks", worldService.getClocks(campaignId).stream()
                .filter(c -> c.getFaction() != null && c.getFaction().getId().equals(factionId))
                .toList());
```

with:

```java
        model.addAttribute("clocks", worldService.getClocksForFaction(campaignId, factionId));
```

- [ ] **Step 10: Run the smoke test to verify it passes**

```bash
./mvnw test -Dtest=FullPageRenderSmokeTest
```

Expected: PASS — all parameterized cases plus `fullSweepLogsNoLazyInitializationException`.

- [ ] **Step 11: Run the full suite for regressions**

```bash
./mvnw test
```

Expected: BUILD SUCCESS. The `@EntityGraph` annotations change fetch plans for `findByIdAndCampaignId`, which `WorldService.updateNpc`/`deleteNpc`/`updateLocation` also use inside write transactions; a LEFT JOIN FETCH there is harmless but confirm nothing breaks.

- [ ] **Step 12: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/support/ \
        src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/world/
git commit -m "$(cat <<'EOF'
fix(world): fetch NPC/location associations before rendering

World pages dereferenced LAZY @ManyToOne proxies after the read-only
transaction closed. With spring.jpa.open-in-view=false this threw
LazyInitializationException mid-render; because the response was already
committed the stream was abandoned without a closing </html>, so browsers
hung on a partial document.

Five routes were affected, not the two the walkthrough found:
/world/npcs, /world/npcs/{id}, /world/locations (parentLocation),
/world/locations/{id} (locationAudioCue), and /world/factions/{id}, where
the controller filtered clocks on a lazy faction outside the transaction.

Adds a populated test fixture at published-adventure scale. The smoke test
already asserted the exact failure mode; it passed only because the old
seed created no world-graph entities.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Scene page reads as a document (F2, P1)

Read-aloud text moves from a 300 px rail clamped to two lines into the main column at full width and full length, styled distinctly. The rail keeps navigation, relations and editing controls.

**Files:**
- Create: `src/main/resources/templates/adventure/_scene-sections.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneDetailPresentationTest.java`
- Modify: `src/main/resources/templates/adventure/scene-detail.html:33-53`
- Modify: `src/main/resources/templates/adventure/_action-rail.html:72-126`
- Modify: `src/main/resources/static/css/components.css` (append)

**Interfaces:**
- Consumes: `PopulatedCampaignFixture` and `PopulatedCampaignFixture.Seeded` from Task 1; the constants `READ_ALOUD_BODY`, `SECRET_BODY`, `TREASURE_BODY`.
- Produces: Thymeleaf fragment `adventure/_scene-sections :: sceneSections`. It takes no parameters and reads `${scene}` and `${sectionThreatCards}` from the model, both of which `SceneController.sceneDetail` already publishes (lines 81 and 94).

---

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneDetailPresentationTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SceneDetailPresentationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        MvcResult result = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andReturn();
        body = result.getResponse().getContentAsString();
    }

    @Test
    void readAloudTextIsPresentInFull() {
        assertThat(body)
                .as("verbatim boxed text must be rendered whole, not abbreviated")
                .contains(PopulatedCampaignFixture.READ_ALOUD_BODY);
    }

    @Test
    void allSectionKindsRenderTheirBodies() {
        assertThat(body).contains(PopulatedCampaignFixture.SECRET_BODY);
        assertThat(body).contains(PopulatedCampaignFixture.TREASURE_BODY);
    }

    @Test
    void sectionsRenderInTheMainColumnNotTheClampedRail() {
        int mainColumnStart = body.indexOf("id=\"sceneBody\"");
        int railStart = body.indexOf("id=\"actionRail\"");
        int readAloudAt = body.indexOf(PopulatedCampaignFixture.READ_ALOUD_BODY);

        assertThat(mainColumnStart).as("#sceneBody must exist").isGreaterThan(-1);
        assertThat(railStart).as("#actionRail must exist").isGreaterThan(mainColumnStart);
        assertThat(readAloudAt)
                .as("read-aloud body must sit inside #sceneBody, before the rail begins")
                .isBetween(mainColumnStart, railStart);
    }

    @Test
    void readAloudIsTypographicallyDistinguished() {
        assertThat(body)
                .as("read-aloud sections carry the distinguishing class used by the cockpit rail")
                .contains("structured-read-aloud");
    }

    @Test
    void metadataFormIsBehindADisclosure() {
        int disclosureAt = body.indexOf("data-structured-metadata");
        assertThat(disclosureAt).as("metadata block must exist").isGreaterThan(-1);
        // The <details> wrapper opens within the 200 chars preceding the marker attribute.
        String preceding = body.substring(Math.max(0, disclosureAt - 200), disclosureAt);
        assertThat(preceding)
                .as("prep-time admin fields must not occupy prime real estate unprompted")
                .contains("<details");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./mvnw test -Dtest=SceneDetailPresentationTest
```

Expected: FAIL. `sectionsRenderInTheMainColumnNotTheClampedRail`, `readAloudIsTypographicallyDistinguished` and `metadataFormIsBehindADisclosure` fail. (`readAloudTextIsPresentInFull` currently *passes* — the text is in the DOM; CSS hides it. That is exactly the defect: the markup lies about what the DM can read.)

- [ ] **Step 3: Create the read-only sections fragment**

Create `src/main/resources/templates/adventure/_scene-sections.html`:

```html
<div th:fragment="sceneSections" xmlns:th="http://www.thymeleaf.org"
     th:if="${scene.sections != null and !scene.sections.isEmpty()}"
     class="scene-sections">

  <!-- Read-aloud first: this is the text a DM speaks verbatim to the table. -->
  <div th:each="section : ${scene.sections}"
       th:if="${section.kind.name() == 'READ_ALOUD'}"
       class="structured-block structured-read-aloud"
       th:attr="data-section-id=${section.id}">
    <span class="badge badge-info">Read Aloud</span>
    <strong th:if="${section.label}" th:text="${section.label}">Label</strong>
    <p th:text="${section.body}">Read aloud text</p>
    <span th:if="${section.sourceLocator}" class="u-text-xs text-muted"
          th:text="|Source: ${section.sourceLocator}|">Source</span>
  </div>

  <!-- Everything else, in stored order, muted relative to the read-aloud blocks. -->
  <div th:each="section : ${scene.sections}"
       th:if="${section.kind.name() != 'READ_ALOUD'}"
       class="structured-block structured-dm"
       th:attr="data-section-id=${section.id}">
    <span class="badge" th:text="${section.kind.name()}">KIND</span>
    <strong th:text="${section.label}">Label</strong>
    <p th:text="${section.body}">Body</p>
    <span th:if="${section.sourceLocator}" class="u-text-xs text-muted"
          th:text="|Source: ${section.sourceLocator}|">Source</span>
    <div th:if="${section.threatId != null and sectionThreatCards != null and sectionThreatCards[section.id] != null}"
         class="u-mt-xs" th:with="card=${sectionThreatCards[section.id]}">
      <!-- Nest th:if outside th:replace: replace has higher precedence than if. -->
      <th:block th:if="${card.trap != null}">
        <th:block th:replace="~{threat/_mechanics-card :: mechanics-card(threat=${card.trap}, kind=${card.kind})}"></th:block>
      </th:block>
      <th:block th:if="${card.hazard != null}">
        <th:block th:replace="~{threat/_mechanics-card :: mechanics-card(threat=${card.hazard}, kind=${card.kind})}"></th:block>
      </th:block>
    </div>
  </div>
</div>
```

- [ ] **Step 4: Render the fragment in the scene page's main column**

In `src/main/resources/templates/adventure/scene-detail.html`, replace lines 34-46 (the whole `<div id="sceneBody">` block) with:

```html
                <div id="sceneBody" class="scene-body" style="flex: 1; min-width: 0;">
                    <div class="detail-section">
                        <div th:if="${scene.sceneKey}" class="detail-meta">
                            <span class="badge" th:text="${scene.sceneKey}">KEY</span>
                        </div>
                        <div class="note-body drop-cap" th:if="${renderedBody != null and !renderedBody.isEmpty()}" th:utext="${renderedBody}">
                            Rendered body
                        </div>
                        <div th:unless="${renderedBody != null and !renderedBody.isEmpty()}" style="color: var(--color-text-muted); font-style: italic;">
                            No body content yet.
                        </div>
                    </div>
                    <th:block th:replace="~{adventure/_scene-sections :: sceneSections}"></th:block>
                </div>
```

Note the `Edit` button at line 24 targets `#sceneBody` with `hx-swap="innerHTML"`, so the inline edit form still replaces this whole block. That is unchanged behaviour.

- [ ] **Step 5: Put the metadata form behind a disclosure in the rail**

In `src/main/resources/templates/adventure/_action-rail.html`, replace lines 72-99 (the `<!-- Structured metadata -->` block) with:

```html
    <!-- Structured metadata: prep-time admin, collapsed so content outranks it. -->
    <details class="u-mb-md" data-structured-metadata>
      <summary class="u-text-sm">Edit metadata</summary>
      <form class="inline-form u-mt-xs"
            th:hx-post="@{/campaigns/{cid}/adventures/{aid}/chapters/{ch}/scenes/{sid}/metadata(cid=${campaignId},aid=${adventure.id},ch=${scene.chapter.id},sid=${scene.id})}"
            hx-target="#actionRail" hx-swap="innerHTML">
        <div class="form-group">
          <label class="form-label">Summary</label>
          <textarea name="summary" class="form-input" rows="2" th:text="${scene.summary}"
                    data-field="summary"></textarea>
        </div>
        <div class="form-group">
          <label class="form-label">Source Locator</label>
          <input type="text" name="sourceLocator" class="form-input"
                 th:value="${scene.sourceLocator}" data-field="sourceLocator"/>
        </div>
        <div class="form-group">
          <label class="form-label">Tags</label>
          <input type="text" name="tags" class="form-input" th:value="${scene.tags}" data-field="tags"/>
        </div>
        <div class="form-group">
          <label class="form-label">Map Region Key</label>
          <input type="text" name="mapRegionKey" class="form-input"
                 th:value="${scene.mapRegionKey}" data-field="mapRegionKey"/>
        </div>
        <button type="submit" class="btn btn-sm btn-primary">Save metadata</button>
      </form>
    </details>
```

The `data-structured-metadata` and `data-field` attributes are preserved — verify nothing else selects on them:

```bash
grep -rn "data-structured-metadata\|data-field=" src/main/resources/static/js/ src/test/java/ | head
```

If a browser test asserts the form is immediately visible, update it to open the `<details>` first.

- [ ] **Step 6: Reduce the rail's section list to label-only rows**

Still in `_action-rail.html`, replace lines 104-126 (the `th:each="section : ${scene.sections}"` card and everything inside it, up to and including its closing `</div>`, but **not** the surrounding `<div class="u-mb-md" data-structured-sections>` or the `<details>Add section` block that follows) with:

```html
      <div th:each="section : ${scene.sections}" class="card card--compact u-mb-xs"
           th:attr="data-section-id=${section.id}">
        <span class="badge" th:text="${section.kind.name()}">KIND</span>
        <strong th:text="${section.label}">Label</strong>
        <!-- Body text lives in the main column (adventure/_scene-sections), where it is
             not subject to the .card p two-line clamp. The rail keeps only the handle. -->
        <div class="u-flex u-gap-xs u-mt-xs">
          <button class="btn btn-danger btn-sm"
                  th:hx-delete="@{/campaigns/{cid}/adventures/{aid}/chapters/{ch}/scenes/{sid}/sections/{sectionId}(cid=${campaignId},aid=${adventure.id},ch=${scene.chapter.id},sid=${scene.id},sectionId=${section.id})}"
                  hx-target="#actionRail" hx-swap="innerHTML"
                  hx-confirm="Delete this section?">&times;</button>
        </div>
      </div>
```

The threat-card block moves to the main column with the body, so it is deliberately absent here.

- [ ] **Step 7: Write the structured-block styles**

These classes are referenced by `session/_story-rail.html:51-79` today but **have no CSS at all**. Append to `src/main/resources/static/css/components.css`:

```css
/* Structured scene sections — shared by the prep-time scene page and the session
   story rail. Deliberately NOT built on .card: the global `.card p` two-line clamp
   (see line 63) is correct for index summaries and wrong for verbatim boxed text. */
.structured-block {
  background: var(--color-surface);
  border-left: 3px solid var(--color-border);
  border-radius: 0 var(--radius) var(--radius) 0;
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-sm);
}

.structured-block > p {
  margin-top: var(--space-xs);
  line-height: 1.6;
  /* Explicitly opt out in case a .structured-block is ever nested inside a .card. */
  display: block;
  -webkit-line-clamp: none;
  overflow: visible;
}

.structured-block > strong {
  font-family: var(--font-ui);
  font-weight: 600;
}

/* Read-aloud: the text a DM speaks verbatim. Gold rule, book face, generous leading. */
.structured-read-aloud {
  border-left-color: var(--color-accent);
  background: color-mix(in srgb, var(--color-accent) 8%, var(--color-surface));
}

.structured-read-aloud > p {
  font-family: var(--font-book);
  font-size: var(--text-base);
  line-height: 1.75;
  color: var(--color-text);
}

/* DM-facing prose: present but visually subordinate to read-aloud. */
.structured-dm > p {
  font-family: var(--font-ui);
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.scene-sections {
  margin-top: var(--space-lg);
}
```

- [ ] **Step 8: Run the test to verify it passes**

```bash
./mvnw test -Dtest=SceneDetailPresentationTest
```

Expected: PASS, all five tests.

- [ ] **Step 9: Verify the clamp still protects the surfaces that need it**

The `.card p` rule was not touched, so this is a confirmation, not a change:

```bash
grep -n -A9 '^\.card p {' src/main/resources/static/css/components.css
```

Expected: the rule is intact with `-webkit-line-clamp: 2`.

```bash
grep -rn '<p' src/main/resources/templates/notes/_card.html \
              src/main/resources/templates/threat/list.html \
              src/main/resources/templates/rollable-table/list.html
```

Expected: each still has a `<p>` inside a `.card`, i.e. still clamped. Confirm the quest and campaign lists were never clamped in the first place:

```bash
grep -n 'card-body\|<p' src/main/resources/templates/quest/list.html src/main/resources/templates/campaigns/_card.html
```

Expected: `quest/list.html:33` uses `<div class="card-body">` and `campaigns/_card.html` has no `<p>` — neither has ever matched `.card p`, so neither can regress.

- [ ] **Step 10: Run the full suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/templates/adventure/ src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneDetailPresentationTest.java
git commit -m "$(cat <<'EOF'
fix(adventure): render scene sections as a document, not a clamped rail

Read-aloud boxed text rendered in a 300px rail inside .card, where the
global `.card p` rule clamped it to two lines with no expand affordance.
The full text was in the DOM; CSS hid it. Meanwhile the main column held
only the DM summary and ~60% empty viewport.

Sections now render full-width and full-length in the main column, with
READ_ALOUD distinguished the way the cockpit story rail intends. Those
classes had no CSS at all, so the styles are written here rather than
merely reused. The rail keeps the section handles, the add/delete
controls, and the metadata form -- now behind a disclosure.

`.card p` is untouched: the notes, threat, rollable-table, audio and
library card summaries still clamp.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Cockpit scene picker (F3, P1)

`PUT /current-scene` already works; only the UI is missing. A DM currently has to leave the cockpit mid-session to start an adventure.

**Files:**
- Create: `src/main/resources/templates/session/_scene-picker.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitScenePickerTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java` (append methods)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`
- Modify: `src/main/resources/templates/session/_story-rail.html:7` and `:116-119`
- Modify: `src/main/resources/static/js/session-cockpit.js`

**Interfaces:**
- Consumes: `PopulatedCampaignFixture` from Task 1.
- Produces: `AdventureService.ScenePickerOption(UUID id, String title, String sceneKey)` and `AdventureService.ScenePickerGroup(String label, List<ScenePickerOption> scenes)`; `AdventureService.scenePickerGroups(UUID campaignId)` → `List<ScenePickerGroup>`. Model attribute name: `scenePickerGroups`.
- Pre-existing: the Alpine method `setCurrentScene(sceneId)` is already implemented at `session-cockpit.js:172-186`. The picker calls it; Step 7 only adds a guard.

---

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitScenePickerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.session.web;

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
class CockpitScenePickerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;
    private String cockpit;

    @BeforeAll
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        cockpit = mvc.perform(get("/campaigns/{c}/session", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void cockpitOffersASceneSelector() {
        assertThat(cockpit)
                .as("a DM must be able to set the current scene without leaving the cockpit")
                .contains("cockpitScenePicker");
    }

    @Test
    void everySceneInTheCampaignIsSelectable() {
        assertThat(cockpit).contains(seeded.richSceneId().toString());
        assertThat(cockpit).contains(seeded.secondSceneId().toString());
    }

    @Test
    void scenesAreGroupedByChapter() {
        assertThat(cockpit).contains("<optgroup");
        assertThat(cockpit).contains("Teil 1: Auf der Straße");
        assertThat(cockpit).contains("Teil 2: Die Spinne");
    }

    @Test
    void pickerIsPresentWhenNoSceneIsSet() {
        // No current scene has been set on the fixture campaign, so the picker must appear
        // in place of the "No current scene. Set one from an adventure." dead end.
        int emptyStateAt = cockpit.indexOf("No current scene");
        assertThat(emptyStateAt).as("empty-state branch is the one under test").isGreaterThan(-1);
        assertThat(cockpit.indexOf("cockpitScenePicker"))
                .as("picker must be reachable from the empty state")
                .isGreaterThan(-1);
    }

    @Test
    void standaloneStoryRailAlsoCarriesThePicker() throws Exception {
        String rail = mvc.perform(get("/campaigns/{c}/session/rails/story", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(rail)
                .as("the rail is re-fetched standalone after scene changes; it must not lose the picker")
                .contains("cockpitScenePicker");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./mvnw test -Dtest=CockpitScenePickerTest
```

Expected: FAIL — all five tests, with `cockpitScenePicker` absent from the response.

- [ ] **Step 3: Add the picker query to `AdventureService`**

Append to `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java`, immediately before the class's closing brace:

```java
    /** One selectable scene in the cockpit picker. */
    public record ScenePickerOption(UUID id, String title, String sceneKey) {}

    /** One <optgroup> in the cockpit picker: "Adventure — Chapter". */
    public record ScenePickerGroup(String label, List<ScenePickerOption> scenes) {}

    /**
     * Every scene in the campaign, grouped by chapter, for the session cockpit's scene
     * selector. Flattened into records inside the transaction so the cockpit template never
     * touches a lazy proxy (spring.jpa.open-in-view=false).
     */
    @Transactional(readOnly = true)
    public List<ScenePickerGroup> scenePickerGroups(UUID campaignId) {
        List<ScenePickerGroup> groups = new ArrayList<>();
        for (Adventure adventure : adventureRepository.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)) {
            for (Chapter chapter : chapterRepository.findByAdventureIdOrderBySortOrderAscIdAsc(adventure.getId())) {
                List<ScenePickerOption> options =
                        sceneRepository.findByChapterIdOrderBySortOrderAscIdAsc(chapter.getId()).stream()
                                .map(s -> new ScenePickerOption(s.getId(), s.getTitle(), s.getSceneKey()))
                                .toList();
                if (!options.isEmpty()) {
                    groups.add(new ScenePickerGroup(
                            adventure.getName() + " — " + chapter.getTitle(), options));
                }
            }
        }
        return groups;
    }
```

Confirm the imports `java.util.ArrayList`, `java.util.List` and `java.util.UUID` are present at the top of the file; add whichever are missing. Confirm the fields `adventureRepository`, `chapterRepository` and `sceneRepository` exist on the class (they do — see the constructor at line 43).

- [ ] **Step 4: Publish the groups from `SessionController`**

In `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`:

Add the field and constructor parameter:

```java
    private final SessionWorkspaceService workspaces;
    private final dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService adventures;

    @Value("${dmhelper.audio.test-provider:false}")
    private boolean testAudioProvider;

    public SessionController(SessionWorkspaceService workspaces,
                             dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService adventures) {
        this.workspaces = workspaces;
        this.adventures = adventures;
    }
```

Then add this line to **both** `cockpit` (before `return "session/cockpit";` at line 48) and `storyRail` (before `return "session/_story-rail :: story";` at line 56):

```java
        model.addAttribute("scenePickerGroups", adventures.scenePickerGroups(campaignId));
```

- [ ] **Step 5: Create the picker fragment**

Create `src/main/resources/templates/session/_scene-picker.html`:

```html
<div th:fragment="scenePicker(campaignId, currentSceneId)" xmlns:th="http://www.thymeleaf.org"
     class="scene-picker">
  <label for="cockpitScenePicker" class="sr-only">Set current scene</label>
  <select id="cockpitScenePicker" class="form-input u-w-full"
          aria-label="Set current scene"
          @change="setCurrentScene($event.target.value)">
    <option value="">Jump to scene…</option>
    <optgroup th:each="group : ${scenePickerGroups}" th:label="${group.label}">
      <option th:each="opt : ${group.scenes}"
              th:value="${opt.id}"
              th:selected="${currentSceneId != null and currentSceneId.toString() == opt.id.toString()}"
              th:text="${opt.sceneKey != null ? opt.sceneKey + ' · ' + opt.title : opt.title}">Scene</option>
    </optgroup>
  </select>
  <div th:if="${scenePickerGroups == null or scenePickerGroups.isEmpty()}"
       class="u-text-xs text-muted">No scenes in this campaign yet.</div>
</div>
```

- [ ] **Step 6: Insert the picker into both branches of the story rail**

In `src/main/resources/templates/session/_story-rail.html`, replace line 7 (`<th:block th:if="${workspace.currentScene != null}">`) with:

```html
    <th:block th:if="${workspace.currentScene != null}">
      <th:block th:replace="~{session/_scene-picker :: scenePicker(campaignId=${workspace.campaign.id}, currentSceneId=${workspace.currentScene.id})}"></th:block>
```

and replace lines 116-119 (the `currentScene == null` branch) with:

```html
    <th:block th:if="${workspace.currentScene == null}">
      <div class="empty-state">No current scene yet — pick one to begin.</div>
      <th:block th:replace="~{session/_scene-picker :: scenePicker(campaignId=${workspace.campaign.id}, currentSceneId=${null})}"></th:block>
      <th:block th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${workspace.campaign.id}, targetType='CAMPAIGN', targetId=${workspace.campaign.id})}"></th:block>
    </th:block>
```

- [ ] **Step 7: Guard the existing `setCurrentScene` against the placeholder option**

`setCurrentScene(sceneId)` **already exists** at `src/main/resources/static/js/session-cockpit.js:172-186` and already does exactly what the picker needs — `PUT /current-scene`, then `refreshRails()`, with `reportActionFailure` and a retry callback. No new method is required.

It needs one guard: the picker's `<option value="">Jump to scene…</option>` fires `@change` with an empty string, which would `PUT {"sceneId": ""}`. In `session-cockpit.js`, change line 173 from:

```javascript
        async setCurrentScene(sceneId) {
            try {
```

to:

```javascript
        async setCurrentScene(sceneId) {
            if (!sceneId) return;
            try {
```

Leave the rest of the method untouched.

- [ ] **Step 8: Run the test to verify it passes**

```bash
./mvnw test -Dtest=CockpitScenePickerTest
```

Expected: PASS, all five tests.

- [ ] **Step 9: Verify Prev/Next and follow-transition still work**

```bash
./mvnw test -Dtest=CoreSessionLoopSmokeTest
```

Expected: PASS. This exercises the session loop end to end.

- [ ] **Step 10: Run the full suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java \
        src/main/resources/templates/session/ \
        src/main/resources/static/js/session-cockpit.js \
        src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitScenePickerTest.java
git commit -m "$(cat <<'EOF'
feat(session): select the current scene from inside the cockpit

The STORY panel's "No current scene. Set one from an adventure." was a
dead end. Starting an adventure meant leaving the cockpit, opening the
adventure, scrolling a list of 90 scenes, opening one, pressing "Set as
Current Scene", and navigating back -- mid-session, at the table.

Only the UI was missing: PUT /current-scene, POST /current-scene/step and
POST /current-scene/follow-transition already existed. Adds a
chapter-grouped scene selector to the story rail, present both when a
scene is set (for jumping) and when none is.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Human-readable enum labels (F5, P3)

`NOT_STARTED` appears 13 times on the quest list of a real campaign. `quest/_form.html` already carries correct display strings in its `<option>` elements — they were simply never used for badges.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelUtil.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelDialect.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelUtilTest.java`
- Modify: 25 templates (exact list below)
- Modify: `src/main/resources/static/css/components.css` (append — quest card header wrap)

**Interfaces:**
- Consumes: nothing from earlier tasks (but Task 2 must land first — it moved `adventure/_action-rail.html:106` into `adventure/_scene-sections.html`).
- Produces: Thymeleaf expression object `#enums` with one method, `String label(Object value)`. Usage: `th:text="${#enums.label(quest.status)}"`.

---

- [ ] **Step 1: Write the failing unit test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelUtilTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import dev.hendrikhoemberg.dmhelper.quest.data.QuestStatus;
import dev.hendrikhoemberg.dmhelper.world.data.LocationKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumLabelUtilTest {

    private final EnumLabelUtil util = new EnumLabelUtil();

    @Test
    void titleCasesUnderscoreSeparatedConstants() {
        assertThat(util.label(QuestStatus.NOT_STARTED)).isEqualTo("Not Started");
        assertThat(util.label(QuestStatus.ON_HOLD)).isEqualTo("On Hold");
    }

    @Test
    void titleCasesSingleWordConstants() {
        assertThat(util.label(QuestStatus.ACTIVE)).isEqualTo("Active");
        assertThat(util.label(LocationKind.SITE)).isEqualTo("Site");
    }

    @Test
    void rendersNullAsEmptyStringSoTemplatesNeedNoGuard() {
        assertThat(util.label(null)).isEmpty();
    }

    @Test
    void passesThroughNonEnumValuesUnchanged() {
        // Some call sites hold a String already (e.g. record accessors named name()).
        assertThat(util.label("Second Wind")).isEqualTo("Second Wind");
    }

    @Test
    void handlesConstantsWithLeadingOrRepeatedUnderscores() {
        assertThat(util.labelOf("PLAYER_FACING")).isEqualTo("Player Facing");
        assertThat(util.labelOf("A__B")).isEqualTo("A B");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./mvnw test -Dtest=EnumLabelUtilTest
```

Expected: FAIL — compilation error, `EnumLabelUtil` does not exist.

- [ ] **Step 3: Write the label mapper**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelUtil.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Turns an enum constant into a display label: NOT_STARTED -> "Not Started".
 *
 * <p>Single presentation-layer mapping, applied uniformly. Raw constants must still be used
 * for th:value, th:selected and hx-vals -- only display positions get labels.
 */
@Component
public class EnumLabelUtil {

    /** Display label for any value; null renders as "" so templates need no guard. */
    public String label(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Enum<?> constant) {
            return labelOf(constant.name());
        }
        return value.toString();
    }

    /** Display label for a raw constant name. */
    public String labelOf(String constantName) {
        if (constantName == null || constantName.isBlank()) {
            return "";
        }
        return Arrays.stream(constantName.split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
```

- [ ] **Step 4: Run the unit test to verify it passes**

```bash
./mvnw test -Dtest=EnumLabelUtilTest
```

Expected: PASS, all five tests.

- [ ] **Step 5: Expose it to templates as `#enums`**

Thymeleaf 3.1 forbids `${@bean...}` inside `th:text`, so a Spring bean reference will not work — an expression object is required. Mirror `MarkdownDialect` exactly.

Create `src/main/java/dev/hendrikhoemberg/dmhelper/config/EnumLabelDialect.java`:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

import java.util.Set;

/**
 * Exposes {@link EnumLabelUtil} to templates as the {@code #enums} expression object,
 * so badges read "Not Started" instead of NOT_STARTED.
 *
 * <p>See {@link MarkdownDialect} for why an expression object is required rather than a
 * Spring bean reference: Thymeleaf 3.1 evaluates th:text in a restricted context that
 * forbids ${@bean...}, object instantiation and static access.
 */
@Component
public class EnumLabelDialect extends AbstractDialect implements IExpressionObjectDialect {

    private static final String NAME = "enums";

    private final IExpressionObjectFactory factory;

    public EnumLabelDialect() {
        super("enums");
        // Stateless helper with no dependencies, so the dialect owns its own instance. This
        // keeps it working in sliced contexts (@WebMvcTest) that auto-include IDialect beans
        // but not arbitrary @Components.
        EnumLabelUtil util = new EnumLabelUtil();
        this.factory = new IExpressionObjectFactory() {
            @Override
            public Set<String> getAllExpressionObjectNames() {
                return Set.of(NAME);
            }

            @Override
            public Object buildObject(IExpressionContext context, String expressionObjectName) {
                return util;
            }

            @Override
            public boolean isCacheable(String expressionObjectName) {
                return true;
            }
        };
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return factory;
    }
}
```

- [ ] **Step 6: Convert every display site**

Apply these edits exactly. Line numbers are pre-edit; if a file has shifted, match on the old text.

**`.name()` display positions — replace `${X.name()}` with `${#enums.label(X)}`:**

| File | Line | Old | New |
|---|---|---|---|
| `session/_session-plan.html` | 46 | `${qpv.quest.status.name()}` | `${#enums.label(qpv.quest.status)}` |
| `session/_session-plan.html` | 54 | `${obj.status.name()}` | `${#enums.label(obj.status)}` |
| `world/npcs-list.html` | 31 | `${npc.status.name()}` | `${#enums.label(npc.status)}` |
| `world/npcs-detail.html` | 22 | `${npc.status.name()}` | `${#enums.label(npc.status)}` |
| `world/npcs-detail.html` | 37 | `\|Disposition: ${npc.disposition.name()}\|` | `\|Disposition: ${#enums.label(npc.disposition)}\|` |
| `world/locations-list.html` | 31 | `${loc.kind.name()}` | `${#enums.label(loc.kind)}` |
| `world/locations-detail.html` | 22 | `${location.kind.name()}` | `${#enums.label(location.kind)}` |
| `world/locations-detail.html` | 36 | `\|Kind: ${location.kind.name()}\|` | `\|Kind: ${#enums.label(location.kind)}\|` |
| `world/locations-detail.html` | 68 | `${link.role.name()}` | `${#enums.label(link.role)}` |
| `world/_relationship-row.html` | 17 | `${rel.kind.name()}` | `${#enums.label(rel.kind)}` |
| `world/_relationship-row.html` | 20 | `${rel.knowledge.name()}` | `${#enums.label(rel.knowledge)}` |
| `world/_relationship-row.html` | 21 | `${rel.status.name()}` | `${#enums.label(rel.status)}` |
| `quest/list.html` | 31 | `${quest.status.name()}` | `${#enums.label(quest.status)}` |
| `quest/detail.html` | 22 | `${quest.status.name()}` | `${#enums.label(quest.status)}` |
| `quest/_objective-list.html` | 7 | `${obj.status.name()}` | `${#enums.label(obj.status)}` |
| `quest/_link-list.html` | 6 | `${link.role.name()}` | `${#enums.label(link.role)}` |
| `adventure/_scene-sections.html` | (Task 2) | `${section.kind.name()}` | `${#enums.label(section.kind)}` |
| `adventure/_action-rail.html` | ~106 | `${section.kind.name()}` | `${#enums.label(section.kind)}` |
| `adventure/_action-rail.html` | ~346 | `${t.kind.name()}` | `${#enums.label(t.kind)}` |
| `adventure/_action-rail.html` | ~410 | `${link.role.name()}` | `${#enums.label(link.role)}` |
| `adventure/_action-rail.html` | ~497 | `${scene.status.name()}` | `${#enums.label(scene.status)}` |
| `session/_story-rail.html` | 63 | `${section.kind.name()}` | `${#enums.label(section.kind)}` |
| `session/_story-rail.html` | 87 | `${check.visibility.name()}` | `${#enums.label(check.visibility)}` |
| `session/_story-rail.html` | 108 | `\|${t.kind.name()}: ...\|` | `\|${#enums.label(t.kind)}: ...\|` |
| `sheet/_inventory.html` | 26 | `${assignment.inventoryState().name()}` | `${#enums.label(assignment.inventoryState())}` |
| `audio/_card.html` | 7 | `' · ' + ${cue.category.name()}` | `' · ' + ${#enums.label(cue.category)}` |
| `notes/list.html` | 48 | `th:text="${t.name()}"` | `th:text="${#enums.label(t)}"` |
| `notes/_form.html` | 26 | `th:text="${t.name()}"` | `th:text="${#enums.label(t)}"` |

**Bare enum toString positions — replace `${X}` with `${#enums.label(X)}`:**

| File | Line | Old | New |
|---|---|---|---|
| `session/_story-rail.html` | 9 | `${workspace.currentScene.status}` | `${#enums.label(workspace.currentScene.status)}` |
| `adventure/_action-rail.html` | ~216 | `${check.visibility}` | `${#enums.label(check.visibility)}` |
| `adventure/_action-rail.html` | ~290 | `${p.disposition}` | `${#enums.label(p.disposition)}` |
| `notes/_card.html` | 10 | `${note.type}` | `${#enums.label(note.type)}` |
| `campaigns/detail.html` | 47 | `${n.type}` | `${#enums.label(n.type)}` |

**Do NOT convert** — these are Java **record accessors named `name()`**, not enums, and converting them would be a behaviour change with no benefit:
- `sheet/_features.html:6` — `${feature.name()}`
- `sheet/_attacks.html:15` — `${attack.name()}`
- `sheet/_resources.html:3` — `${res.name()}`

**Do NOT convert** — value bindings that must stay raw:
- Every `th:value="${t.name()}"` (e.g. `notes/list.html:47`, `notes/_form.html:25`)
- Every `th:selected="…name() == …"` comparison (e.g. `notes/list.html:49`)
- Every `th:classappend` comparison on `.name()` (e.g. `adventure/_action-rail.html:8,13,495`) — these drive CSS class choice, not display
- `_chapter-list.html:26` `${s.status.name().charAt(0)}` — Task 7 replaces this entirely

- [ ] **Step 7: Verify no display site was missed**

```bash
grep -rn "\.name()}" src/main/resources/templates/ | grep "th:text"
```

Expected: exactly three lines remain — `sheet/_features.html:6`, `sheet/_attacks.html:15`, `sheet/_resources.html:3` (record accessors), plus `adventure/_chapter-list.html:26` if Task 7 has not run yet. Anything else in a `th:text` is a miss.

```bash
grep -rn "th:value=\"\${.*\.name()}\"" src/main/resources/templates/ | wc -l
```

Expected: unchanged from before this task — value bindings must not have been touched.

- [ ] **Step 8: Fix the ragged quest card headers**

Long quest titles push the status badge onto a second line. Append to `src/main/resources/static/css/components.css`:

```css
/* Card headers: keep the trailing status badge on the title's baseline rather than
   letting a long title wrap it onto a second line. */
.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
}

.card-header > .badge {
  flex-shrink: 0;
  align-self: flex-start;
}
```

Check whether `.card-header` already has a rule elsewhere in the file first:

```bash
grep -n "^\.card-header" src/main/resources/static/css/components.css
```

If one exists, merge these declarations into it instead of appending a duplicate.

- [ ] **Step 9: Verify labels render end to end**

```bash
./mvnw test -Dtest=FullPageRenderSmokeTest
```

Expected: PASS. Then confirm the quest list actually shows the label:

```bash
./mvnw test -Dtest=EnumLabelUtilTest,FullPageRenderSmokeTest,SceneDetailPresentationTest,CockpitScenePickerTest
```

Expected: PASS.

- [ ] **Step 10: Run the full suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS. Tests that assert on raw constants in rendered HTML (e.g. an existing test expecting the literal `NOT_STARTED` in a badge) will fail — update those assertions to the display label. Do **not** revert a template to make an old assertion pass.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/config/ \
        src/test/java/dev/hendrikhoemberg/dmhelper/config/ \
        src/main/resources/templates/ src/main/resources/static/css/components.css
git commit -m "$(cat <<'EOF'
feat(ui): show human labels instead of raw enum identifiers

NOT_STARTED appeared 13 times on the quest list of a real imported
campaign, and the pattern recurred across quests, world, session, sheet
and notes surfaces. quest/_form.html already carried the correct display
strings in its <option> elements -- they were simply never used for badges.

Adds a single presentation-layer mapping exposed to templates as
#enums.label(...), following MarkdownDialect (Thymeleaf 3.1 forbids
${@bean...} in th:text, so an expression object is required). Converted
all display positions; th:value, th:selected and th:classappend keep raw
constants. Record accessors that happen to be named name() are untouched.

Also stops long quest titles wrapping the status badge onto a second line.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Campaign dashboard reports scale (F4a, P2)

Four cards, three empty, plus an inline edit form — nothing indicating that 90 scenes, 13 quests, 30 NPCs, 5 traps and 3 tables are loaded.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignScaleService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignDashboardScaleTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java:102-116` and `:118-132`
- Modify: `src/main/resources/templates/campaigns/detail.html:27-85`
- Modify: `src/main/resources/static/css/components.css` (append)
- Modify: repositories listed in Step 3

**Interfaces:**
- Consumes: `PopulatedCampaignFixture` (Task 1); `#enums` is available but unused here.
- Produces: `CampaignScaleService.CampaignScale(long adventures, long chapters, long scenes, long quests, long npcs, long locations, long factions, long traps, long hazards, long tables, long handouts, long maps, long notes)` and `CampaignScaleService.scaleOf(UUID campaignId)`. Model attribute name: `scale`.

---

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignDashboardScaleTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

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
class CampaignDashboardScaleTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private PopulatedCampaignFixture.Seeded seeded;
    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{id}", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void dashboardReportsCampaignScale() {
        assertThat(body).contains("data-scale-panel");
        // 2 scenes in chapter one + CHAPTER_TWO_SCENE_COUNT in chapter two.
        int expectedScenes = 2 + PopulatedCampaignFixture.CHAPTER_TWO_SCENE_COUNT;
        assertThat(body).contains("data-scale=\"scenes\"");
        assertThat(body).contains(">" + expectedScenes + "<");
    }

    @Test
    void everyLoadedContentTypeHasAnEntryPoint() {
        String c = "/campaigns/" + seeded.campaignId();
        assertThat(body).contains(c + "/adventures");
        assertThat(body).contains(c + "/quests");
        assertThat(body).contains(c + "/world/npcs");
        assertThat(body).contains(c + "/world/locations");
        assertThat(body).contains(c + "/world/factions");
    }

    @Test
    void editFormIsDemotedBehindADisclosure() {
        int formAt = body.indexOf("id=\"editName\"");
        assertThat(formAt).as("edit form must still exist").isGreaterThan(-1);
        int detailsAt = body.lastIndexOf("<details", formAt);
        int gridEndAt = body.lastIndexOf("dash-grid", formAt);
        assertThat(detailsAt)
                .as("edit form must sit inside a disclosure, not a top-level dashboard card")
                .isGreaterThan(gridEndAt);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./mvnw test -Dtest=CampaignDashboardScaleTest
```

Expected: FAIL — all three tests; `data-scale-panel` is absent and the edit form is a top-level `.dash-card`.

- [ ] **Step 3: Add the count queries**

Add these derived count methods. Spring Data generates the SQL from the method names; no `@Query` needed.

`src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/AdventureRepository.java` — add:
```java
    long countByCampaignId(UUID campaignId);
```

`src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/ChapterRepository.java` — add:
```java
    long countByAdventureCampaignId(UUID campaignId);
```

`src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneRepository.java` — add:
```java
    long countByChapterAdventureCampaignId(UUID campaignId);
```

`src/main/java/dev/hendrikhoemberg/dmhelper/quest/data/QuestRepository.java` — add:
```java
    long countByCampaignId(UUID campaignId);
```

`src/main/java/dev/hendrikhoemberg/dmhelper/world/data/WorldNpcRepository.java`, `WorldLocationRepository.java`, `FactionRepository.java` — add to each:
```java
    long countByCampaignId(UUID campaignId);
```

`src/main/java/dev/hendrikhoemberg/dmhelper/threat/data/TrapRepository.java`, `HazardRepository.java`, `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTableRepository.java`, `src/main/java/dev/hendrikhoemberg/dmhelper/handout/data/HandoutRepository.java`, `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMapRepository.java`, `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteRepository.java` — add to each:
```java
    long countByCampaignId(UUID campaignId);
```

Ensure `java.util.UUID` is imported in each file. For traps, hazards and tables, `campaign` is nullable (global content has `campaign == null`); `countByCampaignId` correctly counts only campaign-scoped rows, which is what "loaded in this campaign" means.

- [ ] **Step 4: Write the scale service**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignScaleService.java`:

```java
package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.world.data.FactionRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldNpcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Aggregate entity counts for the campaign dashboard.
 *
 * <p>A campaign carrying 90 scenes, 13 quests, 30 NPCs, 5 traps and 3 tables previously
 * showed three empty cards and no indication that anything had been imported at all.
 */
@Service
public class CampaignScaleService {

    /** How much content a campaign carries, by type. */
    public record CampaignScale(
            long adventures, long chapters, long scenes, long quests,
            long npcs, long locations, long factions,
            long traps, long hazards, long tables,
            long handouts, long maps, long notes) {}

    private final AdventureRepository adventures;
    private final ChapterRepository chapters;
    private final SceneRepository scenes;
    private final QuestRepository quests;
    private final WorldNpcRepository npcs;
    private final WorldLocationRepository locations;
    private final FactionRepository factions;
    private final TrapRepository traps;
    private final HazardRepository hazards;
    private final RollableTableRepository tables;
    private final HandoutRepository handouts;
    private final GameMapRepository maps;
    private final NoteRepository notes;

    public CampaignScaleService(AdventureRepository adventures, ChapterRepository chapters,
                                SceneRepository scenes, QuestRepository quests,
                                WorldNpcRepository npcs, WorldLocationRepository locations,
                                FactionRepository factions, TrapRepository traps,
                                HazardRepository hazards, RollableTableRepository tables,
                                HandoutRepository handouts, GameMapRepository maps,
                                NoteRepository notes) {
        this.adventures = adventures;
        this.chapters = chapters;
        this.scenes = scenes;
        this.quests = quests;
        this.npcs = npcs;
        this.locations = locations;
        this.factions = factions;
        this.traps = traps;
        this.hazards = hazards;
        this.tables = tables;
        this.handouts = handouts;
        this.maps = maps;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public CampaignScale scaleOf(UUID campaignId) {
        return new CampaignScale(
                adventures.countByCampaignId(campaignId),
                chapters.countByAdventureCampaignId(campaignId),
                scenes.countByChapterAdventureCampaignId(campaignId),
                quests.countByCampaignId(campaignId),
                npcs.countByCampaignId(campaignId),
                locations.countByCampaignId(campaignId),
                factions.countByCampaignId(campaignId),
                traps.countByCampaignId(campaignId),
                hazards.countByCampaignId(campaignId),
                tables.countByCampaignId(campaignId),
                handouts.countByCampaignId(campaignId),
                maps.countByCampaignId(campaignId),
                notes.countByCampaignId(campaignId));
    }
}
```

- [ ] **Step 5: Publish `scale` from the controller**

In `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`, add the field and constructor parameter following the existing style (the class already injects `partyMemberService`, `audioCueRepository`, `noteService`):

```java
    private final dev.hendrikhoemberg.dmhelper.campaign.service.CampaignScaleService scaleService;
```

Then add this line to **both** methods that `return "campaigns/detail"` — `detail` (before the return at line 116) and the update handler (before the return at line 132):

```java
        model.addAttribute("scale", scaleService.scaleOf(id));
```

- [ ] **Step 6: Add the scale panel and demote the edit form**

In `src/main/resources/templates/campaigns/detail.html`, insert this block immediately after the `party/_summary-bar` line (line 25) and before `<div class="dash-grid">`:

```html
            <section class="scale-panel" data-scale-panel aria-label="Campaign contents">
                <a class="scale-tile" data-scale="adventures"
                   th:href="@{/campaigns/{id}/adventures(id=${campaign.id})}">
                    <span class="scale-tile__value" th:text="${scale.adventures}">0</span>
                    <span class="scale-tile__label">Adventures</span>
                </a>
                <a class="scale-tile" data-scale="scenes"
                   th:href="@{/campaigns/{id}/adventures(id=${campaign.id})}">
                    <span class="scale-tile__value" th:text="${scale.scenes}">0</span>
                    <span class="scale-tile__label" th:text="|Scenes in ${scale.chapters} chapters|">Scenes</span>
                </a>
                <a class="scale-tile" data-scale="quests"
                   th:href="@{/campaigns/{id}/quests(id=${campaign.id})}">
                    <span class="scale-tile__value" th:text="${scale.quests}">0</span>
                    <span class="scale-tile__label">Quests</span>
                </a>
                <a class="scale-tile" data-scale="npcs"
                   th:href="@{/campaigns/{id}/world/npcs(id=${campaign.id})}">
                    <span class="scale-tile__value" th:text="${scale.npcs}">0</span>
                    <span class="scale-tile__label">NPCs</span>
                </a>
                <a class="scale-tile" data-scale="locations"
                   th:href="@{/campaigns/{id}/world/locations(id=${campaign.id})}">
                    <span class="scale-tile__value" th:text="${scale.locations}">0</span>
                    <span class="scale-tile__label">Locations</span>
                </a>
                <a class="scale-tile" data-scale="factions"
                   th:href="@{/campaigns/{id}/world/factions(id=${campaign.id})}">
                    <span class="scale-tile__value" th:text="${scale.factions}">0</span>
                    <span class="scale-tile__label">Factions</span>
                </a>
                <a class="scale-tile" data-scale="threats" href="/library/traps">
                    <span class="scale-tile__value" th:text="${scale.traps + scale.hazards}">0</span>
                    <span class="scale-tile__label">Traps &amp; hazards</span>
                </a>
                <a class="scale-tile" data-scale="tables" href="/library/tables">
                    <span class="scale-tile__value" th:text="${scale.tables}">0</span>
                    <span class="scale-tile__label">Rollable tables</span>
                </a>
                <a class="scale-tile" data-scale="handouts"
                   th:href="@{/campaigns/{id}/handouts(id=${campaign.id})}">
                    <span class="scale-tile__value" th:text="${scale.handouts}">0</span>
                    <span class="scale-tile__label">Handouts</span>
                </a>
                <a class="scale-tile" data-scale="maps"
                   th:href="@{/campaigns/{id}/maps(id=${campaign.id})}">
                    <span class="scale-tile__value" th:text="${scale.maps}">0</span>
                    <span class="scale-tile__label">Maps</span>
                </a>
            </section>
```

Then delete the entire `<section class="card dash-card" style="--stagger: 2">` block containing `<h3>Edit Campaign</h3>` (lines 65-84) from the `.dash-grid`, and add this immediately before the existing `<details class="dash-data-tools u-mt-lg">` (line 87):

```html
            <details class="dash-edit u-mt-lg">
                <summary>Edit campaign details</summary>
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
                        <button type="submit" class="btn btn-primary">Save Changes</button>
                    </div>
                </form>
            </details>
```

- [ ] **Step 7: Style the scale panel**

Append to `src/main/resources/static/css/components.css`:

```css
/* Campaign scale tiles: what this campaign actually carries, with a way into each. */
.scale-panel {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--space-sm);
  margin-top: var(--space-lg);
}

.scale-tile {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-sm) var(--space-md);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  text-decoration: none;
  color: var(--color-text);
  transition: border-color var(--duration-micro) var(--ease-out),
              background var(--duration-micro) var(--ease-out);
}

.scale-tile:hover {
  border-color: var(--color-accent);
  background: var(--color-surface-hover);
}

.scale-tile__value {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  line-height: 1.1;
  color: var(--color-accent);
}

.scale-tile__label {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
```

Verify `--text-2xl`, `--text-xs`, `--duration-micro` and `--ease-out` exist:

```bash
grep -n "\--text-2xl\|--text-xs\|--duration-micro\|--ease-out" src/main/resources/static/css/tokens.css
```

If any is missing, substitute the nearest defined token rather than inventing one.

- [ ] **Step 8: Run the test to verify it passes**

```bash
./mvnw test -Dtest=CampaignDashboardScaleTest
```

Expected: PASS, all three tests.

- [ ] **Step 9: Run the full suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/quest/data/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/world/data/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/threat/data/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/handout/data/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/ \
        src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/ \
        src/main/resources/templates/campaigns/detail.html \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/
git commit -m "$(cat <<'EOF'
feat(campaign): surface campaign scale on the dashboard

A campaign carrying 90 scenes, 13 quests, 30 NPCs, 5 traps and 3 tables
showed four cards, three of them empty, and an inline edit form. Nothing
indicated any content had been imported.

Adds a tile row of aggregate counts, each linking to its section, and
demotes the edit form to a disclosure alongside the existing data tools.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Adventures index shows structure (F4b, P2)

One row — title, description, ↑ ↓ Edit Delete — for an adventure containing 90 scenes, with the control cluster wedged between the title and the description.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureIndexTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java` (append)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureController.java:42` and `:97`
- Modify: `src/main/resources/templates/adventure/_adventure-list.html`

**Interfaces:**
- Consumes: `PopulatedCampaignFixture` (Task 1); `#enums` (Task 4).
- Produces: `AdventureService.AdventureSummary(Adventure adventure, long chapterCount, long sceneCount, long doneCount)` and `AdventureService.adventureSummaries(UUID campaignId)` → `List<AdventureSummary>`. Model attribute `adventures` changes type from `List<Adventure>` to `List<AdventureSummary>`; the template accesses the adventure as `a.adventure`.

---

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureIndexTest.java`:

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
class AdventureIndexTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        PopulatedCampaignFixture.Seeded seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{c}/adventures", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void adventureCardReportsChapterAndSceneCounts() {
        int expectedScenes = 2 + PopulatedCampaignFixture.CHAPTER_TWO_SCENE_COUNT;
        assertThat(body).contains("data-adventure-counts");
        assertThat(body).contains("2 chapters");
        assertThat(body).contains(expectedScenes + " scenes");
    }

    @Test
    void adventureCardReportsCompletionProgress() {
        int expectedScenes = 2 + PopulatedCampaignFixture.CHAPTER_TWO_SCENE_COUNT;
        assertThat(body).contains("0/" + expectedScenes + " done");
    }

    @Test
    void descriptionPrecedesTheControlCluster() {
        int descriptionAt = body.indexOf("A two-part fixture adventure");
        int controlsAt = body.indexOf("card-actions");
        assertThat(descriptionAt).as("description must render").isGreaterThan(-1);
        assertThat(controlsAt).as("controls must render").isGreaterThan(-1);
        assertThat(descriptionAt)
                .as("controls between title and description break reading order")
                .isLessThan(controlsAt);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./mvnw test -Dtest=AdventureIndexTest
```

Expected: FAIL — all three tests.

- [ ] **Step 3: Add the summary query to `AdventureService`**

Append to `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java`, before the closing brace:

```java
    /** An adventure with its structural counts, for the adventures index. */
    public record AdventureSummary(Adventure adventure, long chapterCount,
                                   long sceneCount, long doneCount) {}

    /**
     * Adventures with chapter/scene counts and completion progress. The index previously
     * rendered a single title row for an adventure containing 90 scenes.
     */
    @Transactional(readOnly = true)
    public List<AdventureSummary> adventureSummaries(UUID campaignId) {
        List<AdventureSummary> summaries = new ArrayList<>();
        for (Adventure adventure : adventureRepository.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId)) {
            List<Chapter> chapters = chapterRepository.findByAdventureIdOrderBySortOrderAscIdAsc(adventure.getId());
            long sceneCount = 0;
            long doneCount = 0;
            for (Chapter chapter : chapters) {
                List<Scene> scenes = sceneRepository.findByChapterIdOrderBySortOrderAscIdAsc(chapter.getId());
                sceneCount += scenes.size();
                doneCount += scenes.stream().filter(s -> s.getStatus() == SceneStatus.DONE).count();
            }
            summaries.add(new AdventureSummary(adventure, chapters.size(), sceneCount, doneCount));
        }
        return summaries;
    }
```

- [ ] **Step 4: Publish summaries from the controller**

In `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureController.java`, replace **both** occurrences (line 42 and line 97) of:

```java
        model.addAttribute("adventures", adventureService.findAdventuresByCampaign(campaignId));
```

with:

```java
        model.addAttribute("adventures", adventureService.adventureSummaries(campaignId));
```

Check whether any other template or handler consumes the `adventures` model attribute as a bare `Adventure` list:

```bash
grep -rn '\${adventures}\|adventures :\|"adventures"' src/main/resources/templates/ src/main/java/
```

Only `_adventure-list.html` and these two handlers should appear. If another site turns up, leave it on `findAdventuresByCampaign` rather than changing its contract.

- [ ] **Step 5: Rewrite the adventure card**

Replace lines 9-38 of `src/main/resources/templates/adventure/_adventure-list.html` (the `th:each="a : ${adventures}"` card) with:

```html
    <div th:each="a : ${adventures}" class="card">
      <div class="card-header">
        <h3>
          <a th:href="@{/campaigns/{cid}/adventures/{id}(cid=${campaignId},id=${a.adventure.id})}"
             th:text="${a.adventure.name}">Adventure</a>
        </h3>
        <span class="badge" th:if="${a.sceneCount > 0}"
              th:text="|${a.doneCount}/${a.sceneCount} done|">0/0 done</span>
      </div>
      <div class="card-meta" data-adventure-counts
           th:text="|${a.chapterCount} chapters · ${a.sceneCount} scenes|">0 chapters · 0 scenes</div>
      <div class="card-meta" th:if="${a.adventure.sourceAttribution}"
           th:text="${a.adventure.sourceAttribution}">Source</div>
      <div class="card-body" th:if="${a.adventure.description}"
           th:text="${a.adventure.description}">Description</div>
      <div class="card-actions">
        <button class="btn btn-ghost btn-sm"
                th:hx-put="@{/campaigns/{cid}/adventures/{id}/move(cid=${campaignId},id=${a.adventure.id})}"
                hx-vals='{"direction": -1}'
                hx-target="closest .detail-section"
                hx-swap="outerHTML">↑</button>
        <button class="btn btn-ghost btn-sm"
                th:hx-put="@{/campaigns/{cid}/adventures/{id}/move(cid=${campaignId},id=${a.adventure.id})}"
                hx-vals='{"direction": 1}'
                hx-target="closest .detail-section"
                hx-swap="outerHTML">↓</button>
        <button class="btn btn-ghost btn-sm"
                th:hx-get="@{/campaigns/{cid}/adventures/{id}/edit(cid=${campaignId},id=${a.adventure.id})}"
                hx-target="closest .card"
                hx-swap="outerHTML">Edit</button>
        <button class="btn btn-danger btn-sm"
                th:hx-delete="@{/campaigns/{cid}/adventures/{id}(cid=${campaignId},id=${a.adventure.id})}"
                hx-confirm="Delete this adventure and all its chapters and scenes?"
                hx-target="body" hx-swap="outerHTML">Delete</button>
      </div>
    </div>
```

The control cluster now follows the description instead of sitting between title and description. `.card-actions` already has `margin-top: var(--space-md); justify-content: flex-end` (components.css:81-86), so no new CSS is needed.

- [ ] **Step 6: Run the test to verify it passes**

```bash
./mvnw test -Dtest=AdventureIndexTest
```

Expected: PASS, all three tests.

- [ ] **Step 7: Run the full suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS. Any existing test asserting `${adventures}` holds bare `Adventure` objects must be updated to the `AdventureSummary` shape.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/ \
        src/main/resources/templates/adventure/_adventure-list.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureIndexTest.java
git commit -m "$(cat <<'EOF'
feat(adventure): show chapter/scene counts on the adventures index

The index rendered one row -- title, description, controls -- for an
adventure containing 90 scenes, with the rest of the page blank. The
control cluster sat between the title and the description, breaking
reading order.

Cards now carry chapter and scene counts plus completion progress, and
the controls follow the description.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Adventure detail handles density (F4c, P2)

All 90 scenes render as one flat list; one chapter alone is 33 consecutive rows. No collapse, no filter, no in-page search. Each row shows a title and an unexplained single-character badge (`U`). Chapter controls render *after* the last scene row, appearing to belong to that scene.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureDetailDensityTest.java`
- Modify: `src/main/resources/templates/adventure/_chapter-list.html` (full rewrite)
- Modify: `src/main/resources/static/css/components.css` (append)

**Interfaces:**
- Consumes: `PopulatedCampaignFixture` (Task 1); `#enums` (Task 4).
- Produces: nothing consumed by later tasks.

---

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureDetailDensityTest.java`:

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
class AdventureDetailDensityTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;

    private String body;

    @BeforeAll
    void setUp() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        PopulatedCampaignFixture.Seeded seeded = fixture.seed();
        body = mvc.perform(get("/campaigns/{c}/adventures/{a}",
                        seeded.campaignId(), seeded.adventureId()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void chaptersAreCollapsible() {
        assertThat(body).contains("data-chapter-block");
        assertThat(body).contains("<details");
    }

    @Test
    void largeChaptersDefaultToCollapsed() {
        // Chapter one has 2 scenes (open); chapter two has CHAPTER_TWO_SCENE_COUNT (collapsed).
        int chapterOneAt = body.indexOf("Teil 1: Auf der Straße");
        int chapterTwoAt = body.indexOf("Teil 2: Die Spinne");
        assertThat(chapterOneAt).isGreaterThan(-1);
        assertThat(chapterTwoAt).isGreaterThan(chapterOneAt);

        String chapterOneBlock = body.substring(
                body.lastIndexOf("<details", chapterOneAt), chapterOneAt);
        String chapterTwoBlock = body.substring(
                body.lastIndexOf("<details", chapterTwoAt), chapterTwoAt);

        assertThat(chapterOneBlock).as("small chapter stays open").contains("open");
        assertThat(chapterTwoBlock).as("chapter past the threshold defaults collapsed")
                .doesNotContain("open");
    }

    @Test
    void aSceneFilterIsAvailable() {
        assertThat(body).contains("data-scene-filter");
    }

    @Test
    void sceneStatusIsSpelledOutNotAbbreviatedToOneLetter() {
        assertThat(body)
                .as("a bare 'U' badge is unexplained; the status must read as a word")
                .contains("Unvisited");
    }

    @Test
    void aStatusLegendExplainsTheBadges() {
        assertThat(body).contains("data-status-legend");
    }

    @Test
    void chapterControlsAreBoundToTheChapterHeaderNotTheLastScene() {
        int chapterOneAt = body.indexOf("Teil 1: Auf der Straße");
        int lastSceneOfChapterOneAt = body.indexOf("Der Gang", chapterOneAt);
        int controlsAt = body.indexOf("data-chapter-controls", chapterOneAt);

        assertThat(lastSceneOfChapterOneAt).isGreaterThan(-1);
        assertThat(controlsAt)
                .as("controls after the last scene row appear to belong to that scene")
                .isLessThan(lastSceneOfChapterOneAt);
    }

    @Test
    void rowsCarryContentAffordances() {
        // The rich scene has read-aloud sections, a hostile participant, and a check.
        assertThat(body).contains("data-affordance=\"read-aloud\"");
        assertThat(body).contains("data-affordance=\"participants\"");
        assertThat(body).contains("data-affordance=\"checks\"");
    }

    @Test
    void progressCountersAreRetained() {
        assertThat(body).as("0/N done counters already worked and must survive")
                .contains("0/2 done");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./mvnw test -Dtest=AdventureDetailDensityTest
```

Expected: FAIL — all tests except `progressCountersAreRetained`, which already passes and is there to catch a regression.

- [ ] **Step 3: Rewrite the chapter list**

Replace the entire contents of `src/main/resources/templates/adventure/_chapter-list.html`:

```html
<div th:fragment="chapterList" xmlns:th="http://www.thymeleaf.org">
  <div class="detail-section">
    <h2>Chapters
      <button class="btn btn-primary btn-sm"
              th:hx-get="@{/campaigns/{cid}/adventures/{aid}/chapters/new(cid=${campaignId},aid=${adventure.id})}"
              hx-target="#chapter-form-placeholder"
              hx-swap="innerHTML">+ New Chapter</button>
    </h2>
    <div id="chapter-form-placeholder"></div>

    <div class="chapter-toolbar">
      <label for="sceneFilter" class="sr-only">Filter scenes</label>
      <input type="search" id="sceneFilter" class="form-input" data-scene-filter
             placeholder="Filter scenes by title or key…"
             oninput="window.dmHelperFilterScenes &amp;&amp; window.dmHelperFilterScenes(this.value)">
      <div class="status-legend" data-status-legend>
        <span class="badge">Unvisited</span>
        <span class="badge badge-warning">Visited</span>
        <span class="badge badge-success">Done</span>
      </div>
    </div>

    <details th:each="ch : ${chapters}" class="chapter-block" data-chapter-block
             th:with="doneCount = ${ch.scenes != null ? #lists.size(ch.scenes.?[status.name() == 'DONE']) : 0},
                      totalCount = ${ch.scenes != null ? #lists.size(ch.scenes) : 0}"
             th:attr="open=${totalCount le 8 ? 'open' : null}">
      <summary class="chapter-summary">
        <span class="chapter-title" th:text="${ch.title}">Chapter</span>
        <span class="chapter-progress"
              th:text="${totalCount > 0 ? doneCount + '/' + totalCount + ' done' : 'no scenes'}">0/0 done</span>
      </summary>

      <!-- Controls sit directly under the header they act on. Rendering them after the
           last scene row made them read as that scene's controls. -->
      <div class="chapter-controls" data-chapter-controls>
        <button class="btn btn-ghost btn-sm"
                th:hx-put="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/move(cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                hx-vals='{"direction": -1}'
                hx-target="closest .detail-section" hx-swap="outerHTML">↑</button>
        <button class="btn btn-ghost btn-sm"
                th:hx-put="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/move(cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                hx-vals='{"direction": 1}'
                hx-target="closest .detail-section" hx-swap="outerHTML">↓</button>
        <button class="btn btn-ghost btn-sm"
                th:hx-get="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/edit(cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                hx-target="closest .chapter-block" hx-swap="outerHTML">Edit</button>
        <button class="btn btn-danger btn-sm"
                th:hx-delete="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}(cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                hx-confirm="Delete this chapter and all its scenes?"
                hx-target="closest .detail-section" hx-swap="outerHTML">Delete</button>
      </div>

      <div th:if="${ch.intro}" class="detail-meta chapter-intro"
           th:utext="${#markdown.toHtml(ch.intro)}">Intro</div>

      <div th:if="${ch.scenes != null and !ch.scenes.isEmpty()}" class="scene-rows">
        <div th:each="s : ${ch.scenes}" class="scene-row"
             th:attr="data-scene-search=${(s.title ?: '') + ' ' + (s.sceneKey ?: '')}">
          <span class="badge scene-row__status"
                th:classappend="${s.status == SceneStatus.DONE} ? 'badge-success' :
                                (${s.status == SceneStatus.VISITED} ? 'badge-warning' : '')"
                th:text="${#enums.label(s.status)}">Unvisited</span>
          <span th:if="${s.sceneKey}" class="badge" th:text="${s.sceneKey}">14</span>
          <a class="scene-row__title"
             th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}
                          (cid=${campaignId},aid=${adventure.id},sid=${s.id})}"
             th:text="${s.title}">Scene Title</a>
          <span class="scene-row__affordances">
            <span th:if="${!#lists.isEmpty(s.sections.?[kind.name() == 'READ_ALOUD'])}"
                  class="affordance" data-affordance="read-aloud" title="Has read-aloud text">&#x1F4D6;</span>
            <span th:if="${!#lists.isEmpty(s.participants)}"
                  class="affordance" data-affordance="participants" title="Has combat participants">&#x2694;</span>
            <span th:if="${!#lists.isEmpty(s.sections.?[kind.name() == 'TRAP' or kind.name() == 'HAZARD'])}"
                  class="affordance" data-affordance="threats" title="Has a trap or hazard">&#x26A0;</span>
            <span th:if="${!#lists.isEmpty(s.checks)}"
                  class="affordance" data-affordance="checks" title="Has ability checks">&#x1F3B2;</span>
            <span th:if="${!#lists.isEmpty(s.transitions)}"
                  class="affordance" data-affordance="transitions"
                  th:title="|${#lists.size(s.transitions)} outgoing transitions|">&#x2192;</span>
          </span>
        </div>
      </div>
    </details>
  </div>

  <script>
    // Client-side filter across every chapter. Chapters containing a match are forced open
    // so results are never hidden behind a collapsed <details>.
    window.dmHelperFilterScenes = function (query) {
      var needle = (query || '').trim().toLowerCase();
      document.querySelectorAll('[data-chapter-block]').forEach(function (chapter) {
        var anyVisible = false;
        chapter.querySelectorAll('.scene-row').forEach(function (row) {
          var haystack = (row.getAttribute('data-scene-search') || '').toLowerCase();
          var match = needle === '' || haystack.indexOf(needle) !== -1;
          row.hidden = !match;
          if (match) anyVisible = true;
        });
        chapter.hidden = needle !== '' && !anyVisible;
        if (needle !== '' && anyVisible) chapter.open = true;
      });
    };
  </script>
</div>
```

Two things to verify before moving on:

1. The chapter `Edit` button's `hx-target` changed from `closest div[style*='border']` to `closest .chapter-block`, because the inline style it selected on is gone. Confirm `_chapter-form.html` returns markup that can replace a `<details>` element, or adjust the target to `closest .detail-section` (as the move/delete buttons use) if it cannot.
2. `s.sections`, `s.participants`, `s.checks` and `s.transitions` must be initialized when this template renders. `AdventureController.detail` (line 50) calls `adventureService.findChaptersByAdventure(id)`. **Run the test at Step 4 and check for truncation** — if `/adventures/{id}` now fails the `</html>` assertion in `FullPageRenderSmokeTest`, these collections are lazy and un-fetched, and `findChaptersByAdventure` needs an `@EntityGraph` or in-transaction initialization exactly like `WorldService.getLocation` does at lines 224-229.

- [ ] **Step 4: Style the density affordances**

Append to `src/main/resources/static/css/components.css`:

```css
/* Adventure detail: 90 scenes in one flat list is unreadable. Chapters collapse,
   scenes filter, and each row advertises what it contains. */
.chapter-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-wrap: wrap;
  margin-bottom: var(--space-md);
}

.chapter-toolbar input[type="search"] {
  flex: 1;
  min-width: 220px;
}

.status-legend {
  display: flex;
  gap: var(--space-xs);
}

.chapter-block {
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: var(--space-md);
  margin-bottom: var(--space-md);
}

.chapter-summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
  cursor: pointer;
}

.chapter-title {
  font-family: var(--font-display);
  font-size: var(--text-lg);
}

.chapter-progress {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.chapter-controls {
  display: flex;
  gap: var(--space-xs);
  margin: var(--space-sm) 0;
}

.chapter-intro {
  margin: var(--space-sm) 0;
}

.scene-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;
  border-bottom: 1px solid var(--color-border);
}

.scene-row__status {
  min-width: 72px;
  text-align: center;
}

.scene-row__title {
  flex: 1;
  min-width: 0;
}

.scene-row__affordances {
  display: flex;
  gap: var(--space-xs);
  flex-shrink: 0;
}

.affordance {
  font-size: var(--text-sm);
  opacity: 0.75;
  cursor: help;
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./mvnw test -Dtest=AdventureDetailDensityTest
```

Expected: PASS, all eight tests.

- [ ] **Step 6: Verify the adventure detail page still renders completely**

```bash
./mvnw test -Dtest=FullPageRenderSmokeTest
```

Expected: PASS. If `/campaigns/{c}/adventures/{a}` now fails the `</html>` assertion, the new affordance expressions dereferenced a lazy collection — apply the fix described in Step 3, note 2.

- [ ] **Step 7: Run the full suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/templates/adventure/_chapter-list.html \
        src/main/resources/static/css/components.css \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureDetailDensityTest.java
git commit -m "$(cat <<'EOF'
feat(adventure): make a 90-scene adventure navigable

All scenes rendered as one flat list -- one chapter alone was 33
consecutive rows -- with no collapse, filter or in-page search. Rows
showed a title and a single-character status badge, producing an
unexplained "U". Chapter controls rendered after the last scene row, so
they read as that scene's controls. The 85 imported transitions were not
represented at all.

Chapters are now collapsible (default-collapsed past 8 scenes), a filter
searches titles and scene keys across every chapter, rows advertise what
they contain (read-aloud / participants / threats / checks / transitions),
status badges spell out the word, a legend explains them, and chapter
controls sit under the chapter header. The 0/N progress counters are kept.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Verification

After all seven tasks:

- [ ] **Full suite green**

```bash
./mvnw clean test
```

Expected: BUILD SUCCESS.

- [ ] **Manual walkthrough against the real imported campaign**

Re-run the reproduction from §1.2 of the spec: start the app, import `scratchpad/lmop/build/out/lmop-de.dmcampaign`, and confirm each acceptance criterion:

| Finding | Check |
|---|---|
| F1 | `/world/npcs` lists all 30 NPCs and the page finishes loading; open Daran Edermath (faction-linked) and a location with a parent; open a faction with a clock. No spinner, no partial document. |
| F2 | Open the scene containing the Black Spider's letter. The full German text is readable in the main column without interaction, visually distinct from DM prose. |
| F3 | Start a session with no current scene. Set one from the STORY panel without navigating away, then use Prev/Next and follow the `CHOICE` transition. |
| F4 | Dashboard shows 90 scenes / 13 quests / 30 NPCs etc. Adventures index shows counts. Adventure detail collapses Teil 3 and filters. |
| F5 | Quest list badges read "Not Started", not `NOT_STARTED`. Long titles keep their badge on the title line. |

- [ ] **Confirm nothing in §3 of the spec regressed**

Populated session cockpit (story rail, badges, Prev/Next, quest progress, map toolbar, encounter rails); quest list card grid with `Source: S. …`; player view curtain; handout delivery at full resolution; gold-on-dark identity; German umlauts and typographic quotes throughout.

- [ ] **Finish the branch**

Use `superpowers:finishing-a-development-branch` to decide between merge, PR, or further work.
