# DM Safety, Encounter Seeding and Fixture Fidelity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make DM Mode actually player-safe across every template, give DMs a way to present handouts, pin the test fixture to the shape of a real imported package, and let a DM start an encounter from a scene in one click.

**Architecture:** Four independent work items from `docs/superpowers/specs/2026-07-22-dm-safety-encounters-and-fixture-fidelity.md`. F1 tags six exposed template sites and then replaces the whitelist-shaped `DmModeCoverageTest` with a jsoup-backed, nesting-aware rule that scans *every* template. F2 adds a DM-only toggle to the handout card and an honest empty state to the cockpit picker — no service changes, `HandoutService.setDmOnly` already does the right thing. F3 extracts a content-free shape profile from the real package, commits it, and asserts the synthetic fixture covers every field the real package populates. F4 adds a `SceneEncounterSeedService` that composes the two existing primitives (`EncounterService.create` + `EncounterService.addFromLibrary`) and a button on the scene action rail.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Thymeleaf, htmx + Alpine, JPA/Hibernate, Flyway, JUnit 5 + AssertJ + MockMvc, H2 in tests, Maven wrapper (`./mvnw`).

## Global Constraints

- **No changes to the package format, schema, import pipeline or persistence model.** No new Flyway migration, no entity field additions. If a task appears to need one, stop and escalate.
- **`READ_ALOUD` scene sections must never be tagged `dm-only`.** It is the one section kind a DM shows the table. `DmModeCoverageTest.readAloudStaysVisibleBecauseItIsMeantForThePlayers` pins this and must keep passing.
- **The real package `/home/hendrik/Documents/DnDCampaigns/lmop-de.dmcampaign` must never be committed** — it is a verbatim German translation of a copyrighted WotC adventure. Only counts and field-population statistics derived from it may be committed. **No content strings.**
- **Baseline: the full suite is `./mvnw test`, currently 2107 passing, 0 failures.** Every task ends with the full suite green. These existing tests must not regress: `DmModeCoverageTest` (16 assertions), `FullPageRenderSmokeTest`, `SceneStructuredTemplateContractTest`, `ThreatTemplateContractTest`, `EncounterServiceTest`, `EncounterWaveServiceTest`, `ThreatEncounterIntegrationTest`, `EncounterPartyHpSyncTest`, `EnumLabelUtilTest`.
- **Tests run on in-memory H2** (`src/test/resources/application.properties`) and must never touch the user's database at `~/.dmhelper/data`.
- **`AdventureService.findSceneDetailView` and `SessionWorkspaceService` both `Hibernate.initialize(participant.getStatBlock())`.** Removing either reintroduces a page-truncating `LazyInitializationException`. Do not touch those loops.
- **Enum labels in templates:** use `#enums.label(...)` for real enum values and `#enums.labelOf(...)` for DTO fields that flatten an enum to a `String`. `label()` silently no-ops on strings.
- Commit after every task. Never use `git add -A`; stage the exact files listed.

---

## File Structure

**Created:**

| Path | Responsibility |
|---|---|
| `src/test/java/dev/hendrikhoemberg/dmhelper/common/DmSensitiveFieldCoverageTest.java` | The F1 rule. Parses every template with jsoup, fails when a DM-sensitive field renders outside a `dm-only` subtree. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfile.java` | Immutable record of a package's shape: counts, section/transition kinds, field-population statistics. Jackson-serialisable. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileExtractor.java` | Reads a `.dmcampaign` ZIP's `manifest.json` and produces a `PackageShapeProfile`. Runnable against any future package. |
| `src/test/resources/campaigns/shape-profiles/lmop-de.profile.json` | The committed profile. Counts and statistics only — no content strings. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileTest.java` | Regenerates the profile from the real package when present and asserts it matches the committed file. Skips when the package is absent. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/support/FixtureShapeCoverageTest.java` | Asserts the synthetic fixture covers every field the committed profile marks populated. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedService.java` | F4. Composes `EncounterService.create` + `addFromLibrary` to build an encounter from a scene's participants. Bridges adventure ↔ encounter so neither service grows. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedServiceTest.java` | F4 service tests. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutDmOnlyToggleTest.java` | F2 controller + template tests. |

**Modified:**

| Path | Change |
|---|---|
| `pom.xml` | Add jsoup 1.17.2, test scope. |
| `src/main/resources/templates/quest/detail.html:39-50` | Tag Prerequisites, Rewards, Outcome Notes `dm-only`. |
| `src/main/resources/templates/world/locations-detail.html:50-53` | Tag Secrets `dm-only`. |
| `src/main/resources/templates/world/factions-detail.html:36-39,44-47` | Tag Goals and Reputation Notes `dm-only`. |
| `src/main/resources/templates/threat/_mechanics-card.html:4` | Tag the fragment root `dm-only` — trap/hazard DCs and damage are DM-facing on every page that renders it. |
| `src/main/resources/templates/handout/_card.html` | Add the DM-only toggle control. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutController.java` | Add `PUT /{id}/dm-only` returning the card fragment. |
| `src/main/resources/templates/session/cockpit.html:47-53` | Picker states when every handout is DM-only. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java` | Extend to cover every field the profile marks populated. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/common/DmModeCoverageTest.java` | Add rendered assertions for the six newly tagged sites. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java` | Add `POST /{id}/seed-encounter`. |
| `src/main/resources/templates/adventure/_action-rail.html:43-51` | Add the "Start encounter from this scene" action and its result banner. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructuredTemplateContractTest.java` | Add a contract assertion for the seed action. |

---

## Resolved Open Decisions

The spec left six decisions to the plan author. All are resolved here; do not re-litigate them during execution.

1. **F1 enforcement shape** → **Template-source contract test with jsoup** (spec option *a*). jsoup 1.17.2 added test-scope. A rendered-page test only covers pages the fixture reaches, which is the same whitelist failure mode that let F1 ship.
2. **F1 form-template policy** → **`*-form.html` and `_form.html` are excluded**, via an explicit, commented constant in the test. Edit forms are reachable only by deliberate DM navigation.
3. **F2 converter scope** → **UI toggle only.** The converter lives outside this repository. The spec states the UI toggle alone satisfies the acceptance criteria.
4. **F3 extractor location** → **Test-scope Java utility**, so it compiles against the repo and cannot rot. The fixture⊇profile assertion runs on **every build**; the reproduce-the-profile check runs only when the real package is present on disk.
5. **F4 unlinked participants** → **Skip them and report the count.** No invented HP; the DM is told exactly what to add by hand.
6. **F4 sequencing** → **F3 lands before F4**, so F4's tests are written against fixture data that mirrors reality.

Two facts discovered while planning, which override the spec text:

- **Playwright *is* already a test-scope dependency** (`pom.xml:127-132`, version 1.54.0). The spec says it is not. No task in this plan needs it; just don't be surprised.
- **A naive field-name grep over templates is badly misleading.** Most hits in `encounter/_tracker.html` are Alpine `x-text` bindings and a JavaScript method literally named `failure(...)`. **The F1 rule must consider `th:text` and `th:utext` attribute values only.** Scoped that way, the entire codebase yields exactly 30 sites across 11 files, which Task 2 enumerates precisely.

---

## Task 1: Tag the six exposed DM-facing fields (F1a)

Quest prerequisites, rewards and outcome notes; location secrets; faction goals and reputation notes all render underneath the **PLAYER-SAFE** badge with DM Mode off. Tag them, and pin each with a rendered assertion so the fix cannot silently regress.

Two fixture fields are currently `null` (`quest.outcomeNotes`, `faction.reputationNotes`), so the pages render nothing to assert on. Populate them first.

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java:181-183, 211-214`
- Modify: `src/main/resources/templates/quest/detail.html:39-50`
- Modify: `src/main/resources/templates/world/locations-detail.html:50-53`
- Modify: `src/main/resources/templates/world/factions-detail.html:36-39, 44-47`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/DmModeCoverageTest.java`

**Interfaces:**
- Consumes: `PopulatedCampaignFixture.Seeded` (existing 14-component record) — uses `campaignId()`, `questId()`, `locationId()`, `factionId()`.
- Produces: five new `public static final String` constants on `PopulatedCampaignFixture`, consumed by Task 2's exclusion sanity check and Task 5's coverage test:
  - `QUEST_PREREQUISITES = "Teil 1 abgeschlossen"`
  - `QUEST_REWARDS = "500 gp"`
  - `QUEST_OUTCOME_NOTES` (new value, see Step 3)
  - `LOCATION_SECRETS = "Die Redbrands halten den Ort."`
  - `FACTION_REPUTATION_NOTES` (new value, see Step 3)

- [ ] **Step 1: Write the failing tests**

Append these six tests to `src/test/java/dev/hendrikhoemberg/dmhelper/common/DmModeCoverageTest.java`, immediately before the closing brace. They reuse the existing private helper `enclosingTag(html, needle, marker)` — these detail pages wrap each field in a `<div class="u-mb-md">`, so `"u-mb-md"` is the marker.

```java
    private String questPage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/quests/{q}", seeded.campaignId(), seeded.questId()))
                .andReturn().getResponse().getContentAsString();
    }

    private String locationPage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/world/locations/{l}",
                        seeded.campaignId(), seeded.locationId()))
                .andReturn().getResponse().getContentAsString();
    }

    private String factionPage() throws Exception {
        return mvc.perform(get("/campaigns/{c}/world/factions/{f}",
                        seeded.campaignId(), seeded.factionId()))
                .andReturn().getResponse().getContentAsString();
    }

    /** The field's own `u-mb-md` wrapper must carry dm-only. */
    private static void assertFieldBlockIsDmOnly(String html, String needle, String why) {
        String tag = enclosingTag(html, needle, "u-mb-md");
        assertThat(tag).as("a .u-mb-md block must enclose %s", abbreviate(needle)).isNotNull();
        assertThat(tag).as("%s -- tag was: %s", why, tag).contains("dm-only");
    }

    @Test
    void questPrerequisitesAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsDmOnly(questPage(), PopulatedCampaignFixture.QUEST_PREREQUISITES,
                "prerequisites tell players exactly what gates the plot");
    }

    @Test
    void questRewardsAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsDmOnly(questPage(), PopulatedCampaignFixture.QUEST_REWARDS,
                "rewards are the payoff the DM has not offered yet");
    }

    @Test
    void questOutcomeNotesAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsDmOnly(questPage(), PopulatedCampaignFixture.QUEST_OUTCOME_NOTES,
                "outcome notes describe how the quest resolves");
    }

    @Test
    void locationSecretsAreHiddenFromTheTable() throws Exception {
        // A section literally labelled "Secrets" stayed on screen under the PLAYER-SAFE badge.
        assertFieldBlockIsDmOnly(locationPage(), PopulatedCampaignFixture.LOCATION_SECRETS,
                "a location's secrets are the thing players are meant to discover");
    }

    @Test
    void factionGoalsAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsDmOnly(factionPage(), "Ordnung herstellen",
                "faction goals are plot structure the table should not read");
    }

    @Test
    void factionReputationNotesAreHiddenFromTheTable() throws Exception {
        assertFieldBlockIsDmOnly(factionPage(), PopulatedCampaignFixture.FACTION_REPUTATION_NOTES,
                "reputation notes record how the faction privately regards the party");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=DmModeCoverageTest`

Expected: FAIL. `questOutcomeNotesAreHiddenFromTheTable` and `factionReputationNotesAreHiddenFromTheTable` fail first with `content ... must be present in the page` (the fields are `null` in the fixture). The other four fail with `... tag was: <div th:if=... class="u-mb-md"> ... to contain "dm-only"`.

- [ ] **Step 3: Populate the two null fixture fields and add the constants**

In `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java`, add these constants next to the existing `TREASURE_BODY` constant (around line 79):

```java
    /**
     * DM-facing detail fields on the quest / location / faction detail pages. The real package
     * populates all of these (quest.rewards 12/13, quest.outcomeNotes 3/13, quest.prerequisites
     * 1/13, worldLocation.secrets 6/11, faction.reputationNotes 9/9), and every one of them
     * rendered under the PLAYER-SAFE badge until this fixture could prove otherwise.
     */
    public static final String QUEST_PREREQUISITES = "Teil 1 abgeschlossen";
    public static final String QUEST_REWARDS = "500 gp";
    public static final String QUEST_OUTCOME_NOTES =
            "Wenn die Gruppe die Karte verliert, führt Sildar sie stattdessen zur Höhle.";
    public static final String LOCATION_SECRETS = "Die Redbrands halten den Ort.";
    public static final String FACTION_REPUTATION_NOTES =
            "Der Orden traut der Gruppe erst nach der Befreiung von Phandalin.";
```

Then replace the faction creation (currently lines 181-183) with:

```java
        Faction faction = world.createFaction(campaignId, new FactionCommand(
                "Orden des Panzerhandschuhs", "Ordnung herstellen", "Kontakte",
                FACTION_REPUTATION_NOTES,
                null, "order", "Fixture, S. 30"));
```

Replace the parent-location creation (currently lines 185-188) so the secrets string flows from the constant:

```java
        WorldLocation parent = world.createLocation(campaignId, new LocationCommand(
                "Phandalin", LocationKind.SETTLEMENT, null, null, null, null,
                "Ein Grenzdorf.", "Schmied, Gasthaus", LOCATION_SECRETS,
                null, null, "town", "Fixture, S. 28"));
```

And replace the quest creation (currently lines 211-214):

```java
        Quest quest = quests.createQuest(campaignId, new QuestCommand(
                "Die Mine finden", QuestStatus.NOT_STARTED,
                "Findet den Eingang zur Wave Echo Cave.", "Fixture, S. 40",
                "main", QUEST_REWARDS, QUEST_PREREQUISITES, QUEST_OUTCOME_NOTES));
```

- [ ] **Step 4: Tag the six template sites**

In `src/main/resources/templates/quest/detail.html`, replace the three blocks at lines 39-50:

```html
                <!-- Prerequisites, rewards and outcome notes are prep material: they tell the
                     table what gates the plot and what it pays. dm-only hides them when the
                     laptop faces the players. -->
                <div th:if="${quest.prerequisites}" class="u-mb-md dm-only">
                    <h4>Prerequisites</h4>
                    <p th:text="${quest.prerequisites}">Prerequisites</p>
                </div>
                <div th:if="${quest.rewards}" class="u-mb-md dm-only">
                    <h4>Rewards</h4>
                    <p th:text="${quest.rewards}">Rewards</p>
                </div>
                <div th:if="${quest.outcomeNotes}" class="u-mb-md dm-only">
                    <h4>Outcome Notes</h4>
                    <p th:text="${quest.outcomeNotes}">Outcome Notes</p>
                </div>
```

In `src/main/resources/templates/world/locations-detail.html`, replace lines 50-53:

```html
                <div th:if="${location.secrets}" class="u-mb-md dm-only">
                    <h4>Secrets</h4>
                    <p th:text="${location.secrets}">Secrets</p>
                </div>
```

In `src/main/resources/templates/world/factions-detail.html`, replace the Goals block at lines 36-39 and the Reputation Notes block at lines 44-47. Leave `resources` and `tags` untagged — they are colour, not plot.

```html
                <div th:if="${faction.goals}" class="u-mb-md dm-only">
                    <h4>Goals</h4>
                    <p th:text="${faction.goals}">Goals</p>
                </div>
```

```html
                <div th:if="${faction.reputationNotes}" class="u-mb-md dm-only">
                    <h4>Reputation Notes</h4>
                    <p th:text="${faction.reputationNotes}">Reputation Notes</p>
                </div>
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=DmModeCoverageTest`

Expected: PASS — 22 tests (the original 16 plus 6). Confirm `readAloudStaysVisibleBecauseItIsMeantForThePlayers` is among the passes.

- [ ] **Step 6: Run the full suite**

Run: `./mvnw test`

Expected: BUILD SUCCESS, 2113 tests, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/quest/detail.html \
        src/main/resources/templates/world/locations-detail.html \
        src/main/resources/templates/world/factions-detail.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/common/DmModeCoverageTest.java
git commit -m "fix(dm-mode): hide quest, location and faction prep behind DM Mode

Quest prerequisites/rewards/outcome notes, location secrets and faction
goals/reputation notes all rendered underneath the PLAYER-SAFE badge with
DM Mode off. A half-covered safety mechanism is worse than none, because
the badge asserts safety.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Nesting-aware DM-sensitive field rule (F1b)

Task 1 fixed six known sites. `DmModeCoverageTest` is a whitelist: a new DM-facing field ships exposed and the suite stays green. This task inverts it into a rule that scans *every* template and fails on anything unlisted.

**Files:**
- Modify: `pom.xml` (after the playwright dependency, before `</dependencies>`)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/common/DmSensitiveFieldCoverageTest.java`
- Modify: `src/main/resources/templates/threat/_mechanics-card.html:4`

**Interfaces:**
- Consumes: nothing from earlier tasks. Reads template files from disk; no Spring context.
- Produces: nothing consumed by later tasks. It is a guard rail — later tasks must keep it green.

### Design notes for the implementer

The rule is: **for every element whose `th:text` or `th:utext` value references a DM-sensitive property, that element or one of its ancestors must carry `dm-only`.**

Three details make or break this:

1. **Only `th:text` / `th:utext`.** Not `th:if`, not `x-text`, not inline JavaScript. `encounter/_tracker.html` contains an Alpine binding `x-text="...detectionCheck?.dc"` and a JS method named `this.failure(...)`; a looser rule flags 20 false positives there and gets disabled. Verified: no template uses Thymeleaf's `[[${...}]]` inline syntax with a sensitive field, so attribute scanning is complete.
2. **Real ancestor walk, via jsoup's `Element.parents()`.** A line-level or N-preceding-lines scan produces the false positives the spec calls out: `adventure/_action-rail.html:207-209` sit inside the `dm-only` check card opened at line 200, and `session/_story-rail.html:111-112` inside the `.scene-checks dm-only` block opened at line 104. An ancestor walk sees those correctly.
3. **Fragments are checked in isolation, so a fragment must be self-sufficient.** `threat/_mechanics-card.html` renders trap and hazard DCs but has no `dm-only` of its own; its three callers wrap it (`adventure/_scene-sections.html:30,33` and `session/_story-rail.html:74,77` are inside `dm-only` blocks) — but `threat/detail.html:40` is **not**. Rather than build cross-fragment reasoning or an allowlist, tag the fragment root itself. Nested `dm-only` is idempotent, and trap mechanics are DM-facing on the threat detail page too. This removes the need for any allowlist.

**Exact current inventory**, so you know what the test should and should not flag (30 sites, 11 files):

| File | Lines | Status |
|---|---|---|
| `adventure/_action-rail.html` | 203, 207, 208, 209, 284, 285, 286, 288, 348 | already inside `dm-only` ancestors — must pass |
| `session/_story-rail.html` | 94, 95, 96, 99, 108, 111, 112 | already inside `dm-only` ancestors — must pass |
| `world/npcs-detail.html` | 58, 62 | already tagged (`fab79f9`) — must pass |
| `quest/detail.html` | 41, 45, 49 | tagged in Task 1 — must pass |
| `world/locations-detail.html` | 52 | tagged in Task 1 — must pass |
| `world/factions-detail.html` | 38, 46 | tagged in Task 1 — must pass |
| `threat/_mechanics-card.html` | 34, 47, 75, 135 | **the only remaining violation** — fixed in Step 4 |
| `quest/_form.html` | 37, 41, 45 | excluded (edit form) |
| `world/factions-form.html` | 37 | excluded (edit form) |
| `world/locations-form.html` | 58 | excluded (edit form) |
| `world/npcs-form.html` | 79, 83 | excluded (edit form) |

- [ ] **Step 1: Add jsoup as a test-scope dependency**

In `pom.xml`, insert immediately after the closing `</dependency>` of the playwright block (line 132) and before `</dependencies>`:

```xml
		<!-- Test-scope only. DmSensitiveFieldCoverageTest needs a real ancestor walk over
		     template markup; the hand-rolled backwards scan in DmModeCoverageTest is already
		     at the edge of what is maintainable and cannot see nesting. Version is pinned
		     because Spring Boot's BOM does not manage jsoup. -->
		<dependency>
			<groupId>org.jsoup</groupId>
			<artifactId>jsoup</artifactId>
			<version>1.17.2</version>
			<scope>test</scope>
		</dependency>
```

Verify it resolves: `./mvnw -q dependency:resolve -Dsilent=true && ./mvnw dependency:tree | grep jsoup`
Expected: a line containing `org.jsoup:jsoup:jar:1.17.2:test`.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/common/DmSensitiveFieldCoverageTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.common;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM Mode's coverage used to be a whitelist: DmModeCoverageTest asserts tagging on the
 * surfaces it names, so a new DM-facing field shipped exposed and the suite stayed green.
 * A section labelled "Secret" sat on screen underneath a badge reading PLAYER-SAFE.
 *
 * <p>This test inverts that. It walks every template and fails when a DM-sensitive entity
 * field is rendered outside a {@code dm-only} subtree. To add a genuinely player-facing
 * field, you must either tag its block or justify an entry below -- not simply forget.
 */
class DmSensitiveFieldCoverageTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /**
     * Entity properties that describe plot, mechanics, rewards, secrets, monster statistics
     * or placement. Player-visible by policy: scene title, READ_ALOUD section bodies, the
     * map/handout surface, the party bar. Everything else is DM-facing.
     */
    private static final Pattern DM_SENSITIVE = Pattern.compile(
            "\\.(secrets?|motivation|dmNote|rewards|prerequisites|outcomeNotes|reputationNotes"
            + "|goals|success|failure|partial|dc|placementHint|statBlock)\\b");

    /**
     * Only these attributes render content to the page. Deliberately excludes th:if (a
     * visibility guard, not a render) and every non-Thymeleaf attribute: encounter/_tracker.html
     * carries Alpine x-text bindings and a JavaScript method literally named failure(), which
     * a looser rule flags twenty times over. Thymeleaf's [[${...}]] inline syntax is never
     * used with these fields -- verified across all templates.
     */
    private static final Set<String> RENDERING_ATTRIBUTES = Set.of("th:text", "th:utext");

    /**
     * Edit forms are excluded by policy. They render the same DM-sensitive fields into
     * <input> and <textarea> values, but are reachable only by deliberate DM navigation --
     * a DM does not open "Edit Faction" with the laptop facing the table. This exclusion is
     * explicit rather than accidental; if that judgement changes, delete this method and tag
     * the form blocks instead.
     */
    private static boolean isEditForm(Path template) {
        String name = template.getFileName().toString();
        return name.endsWith("-form.html") || name.equals("_form.html") || name.equals("form.html");
    }

    private record Violation(String file, int approximateLine, String expression) {
        @Override public String toString() {
            return "%s (near line %d): %s".formatted(file, approximateLine, expression);
        }
    }

    @Test
    void everyDmSensitiveFieldRendersInsideADmOnlySubtree() throws IOException {
        List<Violation> violations = new ArrayList<>();
        List<Path> scanned = new ArrayList<>();

        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path template : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                if (isEditForm(template)) {
                    continue;
                }
                scanned.add(template);
                String source = Files.readString(template);
                // htmlParser (not xmlParser): templates are fragments with unclosed <img>,
                // <th:block> and bare attributes. The HTML parser builds the same tree the
                // browser would, which is the tree dm-only's CSS descendant selector matches.
                Document doc = Jsoup.parse(source, "", Parser.htmlParser());

                for (Element element : doc.getAllElements()) {
                    String expression = dmSensitiveExpression(element);
                    if (expression == null || hasDmOnly(element)) {
                        continue;
                    }
                    violations.add(new Violation(
                            TEMPLATES.relativize(template).toString(),
                            approximateLine(source, expression),
                            expression));
                }
            }
        }

        assertThat(scanned)
                .as("the scan must actually reach the templates; a broken path would pass vacuously")
                .hasSizeGreaterThan(100);

        assertThat(violations)
                .as("""
                    These render DM-facing content outside any dm-only subtree, so they stay on \
                    screen when a DM turns the laptop to the table under the PLAYER-SAFE badge. \
                    Add dm-only to the enclosing block -- never to a READ_ALOUD body.""")
                .isEmpty();
    }

    /** The first DM-sensitive rendering expression on this element, or null. */
    private static String dmSensitiveExpression(Element element) {
        for (var attribute : element.attributes()) {
            if (!RENDERING_ATTRIBUTES.contains(attribute.getKey())) {
                continue;
            }
            if (DM_SENSITIVE.matcher(attribute.getValue()).find()) {
                return attribute.getKey() + "=\"" + attribute.getValue() + "\"";
            }
        }
        return null;
    }

    /** True if this element or any ancestor carries dm-only or data-dm-only. */
    private static boolean hasDmOnly(Element element) {
        if (isTagged(element)) {
            return true;
        }
        for (Element ancestor : element.parents()) {
            if (isTagged(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTagged(Element element) {
        return element.hasClass("dm-only") || element.hasAttr("data-dm-only");
    }

    /** Line number of the expression in the source, for a message a human can act on. */
    private static int approximateLine(String source, String expression) {
        String needle = expression.substring(expression.indexOf('"') + 1, expression.length() - 1);
        int at = source.indexOf(needle);
        return at < 0 ? 0 : (int) source.substring(0, at).chars().filter(c -> c == '\n').count() + 1;
    }

    /**
     * A rule nobody can read is a rule that gets deleted. READ_ALOUD is the one section kind
     * a DM shows the table, and tagging it defeats DM Mode entirely.
     */
    @Test
    void readAloudBlockIsNotTagged() throws IOException {
        String sections = Files.readString(TEMPLATES.resolve("adventure/_scene-sections.html"));
        int readAloudBlock = sections.indexOf("structured-read-aloud");
        assertThat(readAloudBlock).as("the read-aloud block must exist").isGreaterThan(-1);
        String openingTag = sections.substring(sections.lastIndexOf('<', readAloudBlock),
                sections.indexOf('>', readAloudBlock) + 1);
        assertThat(openingTag)
                .as("read-aloud is meant to be shown; tagging it defeats DM Mode")
                .doesNotContain("dm-only");
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw test -Dtest=DmSensitiveFieldCoverageTest`

Expected: FAIL on `everyDmSensitiveFieldRendersInsideADmOnlySubtree` with exactly four violations, all in `threat/_mechanics-card.html` (near lines 34, 47, 75, 135), each an expression referencing `.dc`. `readAloudBlockIsNotTagged` passes.

If any *other* file appears in the failure list, stop — something regressed in Task 1 or an assumption is wrong. Do not widen the exclusion list to make it green.

- [ ] **Step 4: Tag the mechanics-card fragment root**

In `src/main/resources/templates/threat/_mechanics-card.html`, replace line 4:

```html
    <div class="threat-mechanics-card dm-only" th:attr="data-threat-kind=${kind}">
```

Add this comment directly above it, inside the fragment (line 3 is `<th:block th:fragment="mechanics-card(threat, kind)">`):

```html
    <!-- Trap and hazard mechanics -- detection DCs, saves, damage -- are DM-facing wherever
         this fragment lands, including threat/detail.html, which is the one caller that does
         not already sit inside a dm-only block. Tagging the fragment root makes it
         self-sufficient; nesting it inside another dm-only block is harmless. -->
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=DmSensitiveFieldCoverageTest`
Expected: PASS, 2 tests.

Then confirm the rule actually bites. Temporarily strip `dm-only` from `world/npcs-detail.html:56` (the Motivation block), re-run, and confirm it now reports `world/npcs-detail.html (near line 58)`. Restore the file with `git checkout -- src/main/resources/templates/world/npcs-detail.html` before continuing. **A guard rail you have not seen fail is not a guard rail.**

- [ ] **Step 6: Run the full suite**

Run: `./mvnw test`

Expected: BUILD SUCCESS, 2115 tests, 0 failures. `ThreatTemplateContractTest` must still pass — check it does not assert on the exact class attribute of `.threat-mechanics-card`. If it does, update that assertion to expect `dm-only` and note it in the commit body.

- [ ] **Step 7: Commit**

```bash
git add pom.xml \
        src/test/java/dev/hendrikhoemberg/dmhelper/common/DmSensitiveFieldCoverageTest.java \
        src/main/resources/templates/threat/_mechanics-card.html
git commit -m "test(dm-mode): fail the build on any untagged DM-sensitive field

DmModeCoverageTest is a whitelist -- it asserts tagging on the surfaces it
names, so a new DM-facing field ships exposed and the suite stays green.
This inverts it: jsoup parses every template and walks real ancestors, so
nesting is understood and the action-rail/story-rail false positives stay
quiet. Scoped to th:text/th:utext, because encounter/_tracker.html carries
Alpine x-text bindings and a JS method named failure().

Tags the threat mechanics-card fragment root so it is self-sufficient --
threat/detail.html renders it outside any dm-only block.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Handout DM-only toggle and an honest cockpit picker (F2)

All 7 imported handouts carry `dmOnly = true`, including a regional map that is player-facing in the printed book, so the cockpit's *"Present handout…"* dropdown renders with zero options. The only way to flip the flag is `PUT /api/v1/handouts/{id}/dm-only` — a DM cannot fix their own import.

`HandoutService.setDmOnly` (`handout/service/HandoutService.java:193-201`) already detaches a presented handout from the live session and clears `presented` on transition to DM-only. **No service work.** This is a UI affordance plus an empty state.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutController.java` (after `setPresented`, line 63)
- Modify: `src/main/resources/templates/handout/_card.html:14-36`
- Modify: `src/main/resources/templates/session/cockpit.html:47-53`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutDmOnlyToggleTest.java`

**Interfaces:**
- Consumes: `PopulatedCampaignFixture.Seeded` from Task 1.
- Produces: two new components on the `Seeded` record — `UUID dmOnlyHandoutId` and `UUID playerHandoutId`, appended **after** `tableId`. The record grows from 14 to 16 components. Task 5's coverage test and Task 4's fixture assertions rely on these existing. Existing call sites use accessor methods, not positional destructuring, so appending is safe.
- Produces: `HandoutController.setDmOnly(UUID campaignId, UUID id, boolean dmOnly, Model model)` returning the `handout/_card :: card` fragment, mirroring `setPresented`.

- [ ] **Step 1: Seed two handouts in the fixture**

The fixture has zero handouts (`handouts=0`), which is exactly why F2 was invisible. Add both a DM-only and a player-facing one — the spec requires one of each.

In `PopulatedCampaignFixture.java`, add the import and constructor dependency. Add to the imports block:

```java
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
```

Add the field next to `private final StatBlockRepository statBlocks;`:

```java
    private final HandoutRepository handouts;
```

Add the constructor parameter after `StatBlockRepository statBlocks` and assign it:

```java
                                    StatBlockRepository statBlocks,
                                    HandoutRepository handouts) {
```
```java
        this.handouts = handouts;
```

Add these constants next to `LOCATION_SECRETS`:

```java
    /**
     * The real package imports all 7 handouts as dmOnly, including a regional map that is
     * player-facing in the printed book -- so the cockpit picker rendered empty. The fixture
     * carries one of each so both the toggle and the picker's empty state are exercised.
     */
    public static final String DM_ONLY_HANDOUT_TITLE = "Karte: Cragmaw-Versteck";
    public static final String PLAYER_HANDOUT_TITLE = "Regionalkarte: Schwertküste";
```

Add this helper method to the class, after `seed()`:

```java
    /**
     * Saved through the repository rather than HandoutService.create, so the fixture never
     * writes to the filesystem. Templates only build a /files/{id} URL from the entity; they
     * never read the bytes, so an unbacked fileName renders correctly.
     */
    private Handout handout(Campaign campaign, String title, String tags, boolean dmOnly) {
        Handout handout = new Handout();
        handout.setCampaign(campaign);
        handout.setTitle(title);
        handout.setTags(tags);
        handout.setContentType("image/png");
        handout.setFileName(UUID.randomUUID() + ".png");
        handout.setDmOnly(dmOnly);
        handout.setPresented(false);
        return handouts.save(handout);
    }
```

Add to `seed()`, immediately before the `return new Seeded(...)` statement:

```java
        Campaign campaignRef = campaigns.findById(campaignId).orElseThrow();
        Handout dmHandout = handout(campaignRef, DM_ONLY_HANDOUT_TITLE, "karte,versteck", true);
        Handout playerHandout = handout(campaignRef, PLAYER_HANDOUT_TITLE, "karte,region", false);
```

Extend the `Seeded` record with the two new components (append after `UUID tableId`):

```java
            UUID tableId,
            UUID dmOnlyHandoutId,
            UUID playerHandoutId) {}
```

And extend the return statement:

```java
        return new Seeded(campaignId, adventureId, one.getId(), two.getId(),
                rich.getId(), second.getId(), faction.getId(), parent.getId(), child.getId(),
                npc.getId(), quest.getId(), trap.getId(), hazard.getId(), table.getId(),
                dmHandout.getId(), playerHandout.getId());
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutDmOnlyToggleTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.handout.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every handout in the imported campaign arrived dmOnly=true, so the cockpit's
 * "Present handout..." dropdown rendered with zero options -- indistinguishable from a
 * broken control. The handout card showed a "DM only" badge but offered no way to change
 * it: the only recovery path was PUT /api/v1/handouts/{id}/dm-only by hand.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HandoutDmOnlyToggleTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private HandoutRepository handouts;
    @Autowired private HandoutService handoutService;
    @Autowired private AdventureService adventures;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeAll
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
    }

    @Test
    void handoutCardOffersAControlToLeaveDmOnly() throws Exception {
        String html = mvc.perform(get("/campaigns/{c}/handouts", seeded.campaignId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("a DM must be able to fix their own import without calling the API by hand")
                .contains("/dm-only");
    }

    @Test
    void togglingDmOnlyOffMakesTheHandoutPresentable() throws Exception {
        String card = mvc.perform(put("/campaigns/{c}/handouts/{h}/dm-only",
                        seeded.campaignId(), seeded.dmOnlyHandoutId())
                        .param("dmOnly", "false"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(handouts.findById(seeded.dmOnlyHandoutId()).orElseThrow().isDmOnly())
                .isFalse();
        assertThat(card)
                .as("the endpoint must return the refreshed card so htmx can swap it in place")
                .contains(PopulatedCampaignFixture.DM_ONLY_HANDOUT_TITLE)
                .doesNotContain(">DM only<");
    }

    @Test
    void togglingAPresentedHandoutToDmOnlyStillDetachesIt() {
        // Existing HandoutService behaviour; the new UI path must not route around it.
        handoutService.setPresented(seeded.playerHandoutId(), true);
        assertThat(handouts.findById(seeded.playerHandoutId()).orElseThrow().isPresented()).isTrue();

        handoutService.setDmOnly(seeded.playerHandoutId(), true);

        var after = handouts.findById(seeded.playerHandoutId()).orElseThrow();
        assertThat(after.isPresented())
                .as("a handout going DM-only must stop being presented to the table")
                .isFalse();
        assertThat(after.isDmOnly()).isTrue();

        handoutService.setDmOnly(seeded.playerHandoutId(), false);
    }

    @Test
    void cockpitPickerListsNonDmOnlyHandouts() throws Exception {
        adventures.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
        String html = mvc.perform(get("/campaigns/{c}/session", seeded.campaignId()))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(PopulatedCampaignFixture.PLAYER_HANDOUT_TITLE);
        assertThat(html)
                .as("a DM-only handout must never appear in the player-facing picker")
                .doesNotContain(PopulatedCampaignFixture.DM_ONLY_HANDOUT_TITLE);
    }

    @Test
    void cockpitPickerSaysSoWhenEveryHandoutIsDmOnly() throws Exception {
        handoutService.setDmOnly(seeded.playerHandoutId(), true);
        try {
            adventures.setCurrentScene(seeded.campaignId(), seeded.richSceneId());
            String html = mvc.perform(get("/campaigns/{c}/session", seeded.campaignId()))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html)
                    .as("an empty picker is indistinguishable from a broken one")
                    .contains("All handouts are DM-only");
        } finally {
            handoutService.setDmOnly(seeded.playerHandoutId(), false);
        }
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=HandoutDmOnlyToggleTest`

Expected: FAIL. `handoutCardOffersAControlToLeaveDmOnly` fails on the missing `/dm-only` string; `togglingDmOnlyOffMakesTheHandoutPresentable` fails with a 404 (no such mapping); `cockpitPickerSaysSoWhenEveryHandoutIsDmOnly` fails on the missing message. `togglingAPresentedHandoutToDmOnlyStillDetachesIt` and `cockpitPickerListsNonDmOnlyHandouts` should already pass — they pin existing behaviour.

- [ ] **Step 4: Add the controller mapping**

In `src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutController.java`, insert after `setPresented` (line 63):

```java
    /**
     * The import marks every handout dmOnly, so a DM whose regional map arrived DM-only had
     * no way to present it short of calling the API by hand. Returns the card fragment for an
     * in-place htmx swap, mirroring setPresented. HandoutService.setDmOnly already detaches a
     * presented handout from the live session on the way in.
     */
    @PutMapping("/{id}/dm-only")
    public String setDmOnly(@PathVariable UUID campaignId,
                            @PathVariable UUID id,
                            @RequestParam boolean dmOnly,
                            Model model) {
        Handout handout = handoutService.setDmOnly(id, dmOnly);
        model.addAttribute("handout", handout);
        return "handout/_card :: card";
    }
```

- [ ] **Step 5: Add the toggle to the handout card**

In `src/main/resources/templates/handout/_card.html`, replace the `card-actions` block (lines 24-36) with:

```html
    <div class="card-actions">
        <button class="btn btn-ghost"
                th:hx-get="@{/campaigns/{cid}/handouts/{id}/present(cid=${handout.campaign.id},id=${handout.id})}"
                hx-target="body" hx-swap="beforeend">
            Present
        </button>
        <!-- Without this the only way out of dmOnly was PUT /api/v1/handouts/{id}/dm-only.
             Targets the card's own id so the badge and this label refresh together. -->
        <button class="btn btn-ghost"
                th:hx-put="@{/campaigns/{cid}/handouts/{id}/dm-only(cid=${handout.campaign.id},id=${handout.id})}"
                th:attr="hx-vals='{&quot;dmOnly&quot;: ' + ${!handout.dmOnly} + '}'"
                th:hx-target="'#handout-' + ${handout.id}"
                hx-swap="outerHTML"
                th:text="${handout.dmOnly} ? 'Make player-visible' : 'Mark DM only'">
            Make player-visible
        </button>
        <button class="btn btn-danger"
                th:hx-delete="@{/campaigns/{cid}/handouts/{id}(cid=${handout.campaign.id},id=${handout.id})}"
                hx-confirm="Delete this handout?"
                hx-swap="none">
            Delete
        </button>
    </div>
```

- [ ] **Step 6: Give the cockpit picker an honest empty state**

In `src/main/resources/templates/session/cockpit.html`, replace lines 47-53 with:

```html
    <select id="cockpitHandoutPicker" class="form-input" aria-label="Present handout"
            :disabled="sessionStatus === 'IDLE'"
            th:with="presentable=${#lists.size(workspace.handouts.?[!dmOnly])}"
            @change="presentHandout($event.target.value); $event.target.value = ''">
      <!-- Every handout in an imported campaign arrives dmOnly, and an empty dropdown is
           indistinguishable from a broken one. Say which it is. -->
      <option value="" th:if="${presentable > 0}">Present handout…</option>
      <option value="" th:if="${presentable == 0}">All handouts are DM-only — flip one on the Handouts page</option>
      <option th:each="handout : ${workspace.handouts}" th:unless="${handout.dmOnly}"
              th:value="${handout.id}" th:text="${handout.title}">Handout</option>
    </select>
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=HandoutDmOnlyToggleTest`
Expected: PASS, 5 tests.

- [ ] **Step 8: Run the full suite**

Run: `./mvnw test`

Expected: BUILD SUCCESS, 2120 tests, 0 failures. Watch two things:
- `FullPageRenderSmokeTest` hits `/campaigns/{c}/handouts` and asserts the page closes with `</html>` and logs no `LazyInitializationException`. The fixture now seeds handouts, so this page renders real cards for the first time. If it fails on a lazy `handout.campaign` proxy, that is a genuine finding — fix it by initialising in the controller, not by removing the fixture handouts.
- `DmSensitiveFieldCoverageTest` must stay green.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutController.java \
        src/main/resources/templates/handout/_card.html \
        src/main/resources/templates/session/cockpit.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/HandoutDmOnlyToggleTest.java
git commit -m "feat(handout): let a DM take a handout out of DM-only

All 7 imported handouts arrived dmOnly=true -- including a regional map
that is player-facing in the printed book -- so the cockpit's handout
picker rendered with zero options and no control existed to fix it short
of calling the API by hand.

Adds the toggle to the handout card, and makes the picker say when every
handout is DM-only rather than rendering empty. No service change:
HandoutService.setDmOnly already detaches a presented handout.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Extract and commit the package shape profile (F3a)

Every defect in this spec and the previous one shares a cause: the fixtures do not resemble real imported data, so the suite cannot see the defect. `PopulatedCampaignFixture` set `statBlockId = null` while the real package populates it 65 times out of 67.

The real package cannot be committed. Instead, extract **counts and field-population statistics only, no content strings**, and commit that.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfile.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileExtractor.java`
- Create: `src/test/resources/campaigns/shape-profiles/lmop-de.profile.json`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces, consumed by Task 5:
  - `PackageShapeProfile(String generatedFrom, int formatVersion, List<String> sectionKinds, List<String> transitionKinds, SortedMap<String,Integer> counts, SortedMap<String,Integer> populatedFields)`
  - `PackageShapeProfileExtractor.extract(Path dmcampaignZip) → PackageShapeProfile`
  - `PackageShapeProfileExtractor.load(Path profileJson) → PackageShapeProfile`
  - `PackageShapeProfileExtractor.COMMITTED_PROFILE` — `Path` constant for the committed file.
  - `PackageShapeProfileExtractor.REAL_PACKAGE` — `Path` constant for the developer-machine package.

### Design notes

The manifest is a single `manifest.json` inside a ZIP. Verified top-level shape: `adventures[].chapters[].scenes[]` with each scene carrying `sections`, `checks`, `participants`, `transitions`, `links`; plus flat `worldNpcs`, `worldLocations`, `factions`, `quests`, `handouts`, `traps`, `hazards`, `rollableTables`, `notes`, `annotations`, `customStatBlocks`, `customMagicItems`, `worldRelationships`, `maps`, `encounters`, `party`, `audioCues`, `factionClocks`.

A field counts as **populated** when its JSON value is not `null`, `""`, `[]` or `{}`. Structural keys are excluded from `populatedFields` — they carry no coverage signal and would bloat the profile: `key`, `sortOrder`, `sourceKey`, `provenance`, `createdAt`, `assetRef`, `contentType`, `ownerRef`, `entries`, `objectives`, `links`, `chapters`, `sections`, `checks`, `participants`, `transitions`, `conditionRefs`, `salvageItemRefs`, `disarmMethods`.

- [ ] **Step 1: Write the profile record**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfile.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import java.util.List;
import java.util.SortedMap;

/**
 * The shape of a real campaign package, reduced to statistics.
 *
 * <p>lmop-de.dmcampaign cannot be committed -- it is a verbatim German translation of a
 * copyrighted adventure. But the reason the fixtures kept drifting from reality is that
 * nothing recorded what reality looked like. This record carries counts and field-population
 * statistics and nothing else: no titles, no bodies, no names. Serialised to JSON it is safe
 * to commit, and FixtureShapeCoverageTest asserts the synthetic fixture covers every field
 * a real package populates.
 *
 * @param populatedFields "entity.field" -> how many rows have a non-empty value. This is the
 *                        coverage floor, not a ceiling: the fixture may exceed it, and
 *                        existing coverage must never be removed to match it.
 */
public record PackageShapeProfile(
        String generatedFrom,
        int formatVersion,
        List<String> sectionKinds,
        List<String> transitionKinds,
        SortedMap<String, Integer> counts,
        SortedMap<String, Integer> populatedFields) {
}
```

- [ ] **Step 2: Write the extractor**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileExtractor.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads a .dmcampaign package and reduces it to a {@link PackageShapeProfile}. Deliberately
 * generic over the manifest's arrays so it keeps working against future packages: refresh the
 * committed profile by pointing {@link #main} at a new file.
 */
public final class PackageShapeProfileExtractor {

    /** Developer-machine path. Absent on CI, which is why PackageShapeProfileTest skips. */
    public static final Path REAL_PACKAGE =
            Path.of(System.getProperty("user.home"), "Documents/DnDCampaigns/lmop-de.dmcampaign");

    public static final Path COMMITTED_PROFILE =
            Path.of("src/test/resources/campaigns/shape-profiles/lmop-de.profile.json");

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Keys that carry no coverage signal: identity, ordering, provenance, and the nested
     * collections already counted in their own right.
     */
    private static final Set<String> STRUCTURAL = Set.of(
            "key", "sortOrder", "sourceKey", "provenance", "createdAt", "assetRef",
            "contentType", "ownerRef", "entries", "objectives", "links", "chapters",
            "sections", "checks", "participants", "transitions", "conditionRefs",
            "salvageItemRefs", "disarmMethods");

    private PackageShapeProfileExtractor() {}

    public static PackageShapeProfile extract(Path dmcampaignZip) throws IOException {
        JsonNode manifest = readManifest(dmcampaignZip);

        List<JsonNode> adventures = children(manifest, "adventures");
        List<JsonNode> chapters = flatten(adventures, "chapters");
        List<JsonNode> scenes = flatten(chapters, "scenes");
        List<JsonNode> sections = flatten(scenes, "sections");
        List<JsonNode> participants = flatten(scenes, "participants");
        List<JsonNode> transitions = flatten(scenes, "transitions");
        List<JsonNode> checks = flatten(scenes, "checks");
        List<JsonNode> links = flatten(scenes, "links");

        SortedMap<String, Integer> counts = new TreeMap<>();
        counts.put("adventures", adventures.size());
        counts.put("chapters", chapters.size());
        counts.put("scenes", scenes.size());
        counts.put("sceneSections", sections.size());
        counts.put("sceneParticipants", participants.size());
        counts.put("sceneTransitions", transitions.size());
        counts.put("sceneChecks", checks.size());
        counts.put("sceneLinks", links.size());
        for (String top : List.of("worldNpcs", "worldLocations", "factions", "worldRelationships",
                "quests", "handouts", "customStatBlocks", "customMagicItems", "traps", "hazards",
                "rollableTables", "notes", "annotations", "encounters", "maps", "party",
                "audioCues", "factionClocks")) {
            counts.put(top, children(manifest, top).size());
        }

        SortedMap<String, Integer> populated = new TreeMap<>();
        tally(populated, "scene", scenes);
        tally(populated, "sceneSection", sections);
        tally(populated, "sceneParticipant", participants);
        tally(populated, "sceneTransition", transitions);
        tally(populated, "sceneCheck", checks);
        tally(populated, "sceneLink", links);
        tally(populated, "worldNpc", children(manifest, "worldNpcs"));
        tally(populated, "worldLocation", children(manifest, "worldLocations"));
        tally(populated, "faction", children(manifest, "factions"));
        tally(populated, "quest", children(manifest, "quests"));
        tally(populated, "handout", children(manifest, "handouts"));
        tally(populated, "trap", children(manifest, "traps"));
        tally(populated, "hazard", children(manifest, "hazards"));
        tally(populated, "rollableTable", children(manifest, "rollableTables"));

        return new PackageShapeProfile(
                dmcampaignZip.getFileName().toString(),
                manifest.path("formatVersion").asInt(),
                distinct(sections, "kind"),
                distinct(transitions, "kind"),
                counts,
                populated);
    }

    public static PackageShapeProfile load(Path profileJson) throws IOException {
        return JSON.readValue(Files.readString(profileJson), PackageShapeProfile.class);
    }

    public static void write(PackageShapeProfile profile, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, JSON.writeValueAsString(profile) + "\n");
    }

    private static JsonNode readManifest(Path zip) throws IOException {
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            ZipEntry entry = archive.getEntry("manifest.json");
            if (entry == null) {
                throw new IOException("No manifest.json in " + zip);
            }
            try (InputStream in = archive.getInputStream(entry)) {
                return JSON.readTree(in);
            }
        }
    }

    private static List<JsonNode> children(JsonNode parent, String field) {
        List<JsonNode> out = new ArrayList<>();
        parent.path(field).forEach(out::add);
        return out;
    }

    private static List<JsonNode> flatten(List<JsonNode> parents, String field) {
        List<JsonNode> out = new ArrayList<>();
        parents.forEach(p -> out.addAll(children(p, field)));
        return out;
    }

    private static List<String> distinct(List<JsonNode> rows, String field) {
        Set<String> values = new TreeSet<>();
        for (JsonNode row : rows) {
            if (row.hasNonNull(field)) {
                values.add(row.get(field).asText());
            }
        }
        return List.copyOf(values);
    }

    /** Records, per field, how many rows carry a non-empty value. Zero-count fields are omitted. */
    private static void tally(SortedMap<String, Integer> into, String prefix, List<JsonNode> rows) {
        Set<String> fields = new TreeSet<>();
        rows.forEach(row -> row.fieldNames().forEachRemaining(fields::add));
        for (String field : fields) {
            if (STRUCTURAL.contains(field)) {
                continue;
            }
            int count = 0;
            for (JsonNode row : rows) {
                if (isPopulated(row.get(field))) {
                    count++;
                }
            }
            if (count > 0) {
                into.put(prefix + "." + field, count);
            }
        }
    }

    private static boolean isPopulated(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.asText().isBlank();
        }
        if (value.isContainerNode()) {
            return !value.isEmpty();
        }
        return true;
    }

    /** Refresh the committed profile: {@code java PackageShapeProfileExtractor <package>}. */
    public static void main(String[] args) throws IOException {
        Path source = args.length > 0 ? Path.of(args[0]) : REAL_PACKAGE;
        write(extract(source), COMMITTED_PROFILE);
        System.out.println("Wrote " + COMMITTED_PROFILE + " from " + source);
    }
}
```

- [ ] **Step 3: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The committed profile is only trustworthy if it can be regenerated. This test re-runs the
 * extractor against the real package and asserts the result matches what is on disk, so the
 * profile cannot quietly drift from the package it claims to describe.
 *
 * <p>Skipped when the package is absent -- it is copyrighted and never committed, so it exists
 * only on a developer machine. FixtureShapeCoverageTest, which does the real enforcement,
 * runs everywhere.
 */
class PackageShapeProfileTest {

    static boolean realPackageIsPresent() {
        return Files.isRegularFile(PackageShapeProfileExtractor.REAL_PACKAGE);
    }

    @Test
    void committedProfileParsesAndCarriesNoContentStrings() throws IOException {
        PackageShapeProfile profile =
                PackageShapeProfileExtractor.load(PackageShapeProfileExtractor.COMMITTED_PROFILE);

        assertThat(profile.counts()).containsEntry("scenes", 90);
        assertThat(profile.populatedFields()).containsEntry("sceneParticipant.statblockRef", 65);

        // Everything in the file must be a field name, an entity name, or a number. If a title
        // or body ever leaks in, this is a licensing problem, not a test failure.
        String raw = Files.readString(PackageShapeProfileExtractor.COMMITTED_PROFILE);
        assertThat(raw)
                .as("the profile must carry statistics only -- never content from the adventure")
                .doesNotContain("Phandelver", "Phandalin", "Cragmaw", "Redbrand", "Rotbrenner",
                        "Schwertk", "Wave Echo", "Wellenhall");
    }

    @Test
    @EnabledIf("realPackageIsPresent")
    void extractorReproducesTheCommittedProfile() throws IOException {
        PackageShapeProfile regenerated =
                PackageShapeProfileExtractor.extract(PackageShapeProfileExtractor.REAL_PACKAGE);
        PackageShapeProfile committed =
                PackageShapeProfileExtractor.load(PackageShapeProfileExtractor.COMMITTED_PROFILE);

        assertThat(regenerated)
                .as("""
                    The committed profile no longer describes the package. If the package changed \
                    on purpose, refresh it: ./mvnw test-compile exec:java \
                    -Dexec.mainClass=dev.hendrikhoemberg.dmhelper.support.PackageShapeProfileExtractor \
                    -Dexec.classpathScope=test""")
                .isEqualTo(committed);
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./mvnw test -Dtest=PackageShapeProfileTest`

Expected: FAIL — `NoSuchFileException: src/test/resources/campaigns/shape-profiles/lmop-de.profile.json`.

- [ ] **Step 5: Generate the committed profile**

Run:

```bash
./mvnw -q test-compile
java -cp "target/test-classes:target/classes:$(./mvnw -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -DincludeScope=test 2>/dev/null | tail -1)" \
     dev.hendrikhoemberg.dmhelper.support.PackageShapeProfileExtractor
```

Expected: `Wrote src/test/resources/campaigns/shape-profiles/lmop-de.profile.json from /home/hendrik/Documents/DnDCampaigns/lmop-de.dmcampaign`

Then read the generated file and confirm it matches this content exactly. **If it does not, stop and investigate — do not adjust this plan's expectations to match a surprise.** This is the ground truth measured from the package:

```json
{
  "generatedFrom" : "lmop-de.dmcampaign",
  "formatVersion" : 2,
  "sectionKinds" : [ "DEVELOPMENT", "HAZARD", "READ_ALOUD", "SCALING", "SECRET", "TRAP", "TREASURE" ],
  "transitionKinds" : [ "CHOICE", "EXIT" ],
  "counts" : {
    "adventures" : 1, "annotations" : 9, "audioCues" : 0, "chapters" : 4,
    "customMagicItems" : 7, "customStatBlocks" : 10, "encounters" : 0, "factionClocks" : 0,
    "factions" : 9, "handouts" : 7, "hazards" : 5, "maps" : 0, "notes" : 4, "party" : 0,
    "quests" : 13, "rollableTables" : 3, "sceneChecks" : 33, "sceneLinks" : 44,
    "sceneParticipants" : 67, "sceneSections" : 169, "sceneTransitions" : 85, "scenes" : 90,
    "traps" : 5, "worldLocations" : 11, "worldNpcs" : 30, "worldRelationships" : 10
  },
  "populatedFields" : {
    "faction.goals" : 9, "faction.name" : 9, "faction.reputationNotes" : 9,
    "faction.resources" : 9, "faction.sourceLocator" : 9, "faction.tags" : 9,
    "handout.dmOnly" : 7, "handout.presented" : 7, "handout.tags" : 7, "handout.title" : 7,
    "hazard.areaHint" : 5, "hazard.check" : 4, "hazard.damage" : 5, "hazard.description" : 5,
    "hazard.endingConditions" : 2, "hazard.escalationText" : 2, "hazard.exposureMode" : 5,
    "hazard.exposureText" : 5, "hazard.name" : 5, "hazard.severity" : 5,
    "quest.outcomeNotes" : 3, "quest.prerequisites" : 1, "quest.rewards" : 12,
    "quest.sourceLocator" : 13, "quest.status" : 13, "quest.summary" : 13, "quest.tags" : 13,
    "quest.title" : 13, "rollableTable.addressMode" : 3, "rollableTable.category" : 3,
    "rollableTable.description" : 3, "rollableTable.name" : 3,
    "rollableTable.rollExpression" : 3, "rollableTable.tags" : 3,
    "scene.body" : 90, "scene.sourceLocator" : 90, "scene.status" : 90, "scene.summary" : 90,
    "scene.tags" : 90, "scene.title" : 90,
    "sceneCheck.ability" : 33, "sceneCheck.dc" : 32, "sceneCheck.failure" : 1,
    "sceneCheck.label" : 33, "sceneCheck.partial" : 3, "sceneCheck.skill" : 33,
    "sceneCheck.sourceLocator" : 33, "sceneCheck.success" : 33, "sceneCheck.visibility" : 33,
    "sceneLink.displayText" : 44, "sceneLink.role" : 44, "sceneLink.targetRef" : 44,
    "sceneParticipant.displayName" : 67, "sceneParticipant.disposition" : 67,
    "sceneParticipant.noteRef" : 4, "sceneParticipant.placementHint" : 24,
    "sceneParticipant.quantity" : 67, "sceneParticipant.sourceLocator" : 67,
    "sceneParticipant.statblockRef" : 65,
    "sceneSection.body" : 169, "sceneSection.kind" : 169, "sceneSection.label" : 169,
    "sceneSection.sourceLocator" : 159, "sceneSection.threatRef" : 9,
    "sceneTransition.condition" : 2, "sceneTransition.dmNote" : 2,
    "sceneTransition.externalDestination" : 1, "sceneTransition.kind" : 85,
    "sceneTransition.label" : 85, "sceneTransition.targetSceneRef" : 84,
    "trap.additionalEffect" : 4, "trap.countermeasureNotes" : 1, "trap.damage" : 5,
    "trap.description" : 5, "trap.detectionCheck" : 5,
    "trap.detectionPassiveThreshold" : 3, "trap.name" : 5, "trap.resetMode" : 5,
    "trap.save" : 4, "trap.severity" : 5, "trap.triggerDescription" : 5,
    "worldLocation.kind" : 11, "worldLocation.name" : 11, "worldLocation.secrets" : 6,
    "worldLocation.services" : 1, "worldLocation.sourceLocator" : 11,
    "worldLocation.summary" : 11, "worldLocation.tableLinks" : 2, "worldLocation.tags" : 11,
    "worldNpc.appearance" : 11, "worldNpc.disposition" : 30, "worldNpc.factionRef" : 11,
    "worldNpc.locationRef" : 14, "worldNpc.motivation" : 19, "worldNpc.name" : 30,
    "worldNpc.noteRef" : 3, "worldNpc.role" : 30, "worldNpc.secret" : 14,
    "worldNpc.sourceLocator" : 30, "worldNpc.statblockRef" : 17, "worldNpc.status" : 30,
    "worldNpc.tags" : 30, "worldNpc.voice" : 3
  }
}
```

(Jackson writes one key per line rather than the compact grouping shown here; that formatting difference is expected and fine. The keys and values must match.)

Manually read the generated file top to bottom and confirm no string from the adventure appears. This is a licensing check, not a formality.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./mvnw test -Dtest=PackageShapeProfileTest`
Expected: PASS, 2 tests (both run — the package is present on this machine).

- [ ] **Step 7: Run the full suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS, 2122 tests, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfile.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileExtractor.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/support/PackageShapeProfileTest.java \
        src/test/resources/campaigns/shape-profiles/lmop-de.profile.json
git commit -m "test(fixture): commit a content-free shape profile of the real package

The fixtures drift from real imported data because nothing recorded what
real data looks like -- PopulatedCampaignFixture set statBlockId=null
while the real package populates it 65 times out of 67.

lmop-de.dmcampaign is a verbatim translation of a copyrighted adventure and
cannot be committed. This commits its shape instead: counts and field-
population statistics, no content strings. The extractor runs against any
package so the profile can be refreshed.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Make the fixture cover the profile (F3b)

The profile is inert until something asserts against it. This task writes the coverage test, watches it fail, then extends the fixture until it passes.

The assertion is **coverage, not equality** — the fixture needs one of each field, not 90 scenes. And the profile is a **floor, not a ceiling**: the fixture already covers `worldLocation.parentLocation` (via `childLocationId`) which the real package never populates, and that coverage must not be removed. `world/locations-list.html:35` dereferences `loc.parentLocation.name`; deleting the nested location would reopen a page truncation fixed in `2e67299`.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/support/FixtureShapeCoverageTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java`

**Interfaces:**
- Consumes: `PackageShapeProfileExtractor.load`, `PackageShapeProfileExtractor.COMMITTED_PROFILE` (Task 4); `PopulatedCampaignFixture.Seeded` with 16 components (Task 3).
- Produces: an extended fixture. No new public API beyond fields already on `Seeded`.

- [ ] **Step 1: Write the failing coverage test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/support/FixtureShapeCoverageTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.support;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The participant-statblock bug survived because PopulatedCampaignFixture set statBlockId=null
 * while the real package populates it 65 times out of 67. The fixture was written from a prose
 * checklist rather than from the shape of real data.
 *
 * <p>This asserts fixture superset-of profile: for every field the real package populates at
 * least once, the fixture must populate it at least once. It is a floor, not a ceiling -- the
 * fixture legitimately exceeds it (nested locations, which the real package has none of) and
 * that coverage must never be deleted to match.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FixtureShapeCoverageTest {

    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private EntityManager em;

    private PopulatedCampaignFixture.Seeded seeded;
    private PackageShapeProfile profile;

    @BeforeAll
    void setUp() throws IOException {
        seeded = fixture.seed();
        profile = PackageShapeProfileExtractor.load(PackageShapeProfileExtractor.COMMITTED_PROFILE);
    }

    /**
     * Profile key -> JPQL counting fixture rows where that field is populated. Every key the
     * profile marks populated must appear here; a refreshed profile carrying a new field fails
     * loudly in {@link #everyProfileFieldHasACoverageQuery} rather than passing silently.
     *
     * <p>Keys omitted deliberately, with reasons:
     * <ul>
     *   <li>handout.presented / handout.dmOnly -- booleans; the profile counts "false" as
     *       populated only because it is non-null. Both states are seeded regardless.
     *   <li>trap.detectionCheck / trap.save / hazard.check -- embedded value objects; covered
     *       by their component columns below.
     * </ul>
     */
    private Map<String, String> coverageQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("scene.title", "select count(s) from Scene s where s.title is not null");
        q.put("scene.body", "select count(s) from Scene s where s.body is not null");
        q.put("scene.summary", "select count(s) from Scene s where s.summary is not null");
        q.put("scene.status", "select count(s) from Scene s where s.status is not null");
        q.put("scene.tags", "select count(s) from Scene s where s.tags is not null");
        q.put("scene.sourceLocator", "select count(s) from Scene s where s.sourceLocator is not null");

        q.put("sceneSection.kind", "select count(x) from SceneSection x where x.kind is not null");
        q.put("sceneSection.label", "select count(x) from SceneSection x where x.label is not null");
        q.put("sceneSection.body", "select count(x) from SceneSection x where x.body is not null");
        q.put("sceneSection.sourceLocator", "select count(x) from SceneSection x where x.sourceLocator is not null");
        q.put("sceneSection.threatRef", "select count(x) from SceneSection x where x.threatId is not null");

        q.put("sceneParticipant.displayName", "select count(x) from SceneParticipant x where x.displayName is not null");
        q.put("sceneParticipant.quantity", "select count(x) from SceneParticipant x where x.quantity > 0");
        q.put("sceneParticipant.disposition", "select count(x) from SceneParticipant x where x.disposition is not null");
        q.put("sceneParticipant.placementHint", "select count(x) from SceneParticipant x where x.placementHint is not null");
        q.put("sceneParticipant.sourceLocator", "select count(x) from SceneParticipant x where x.sourceLocator is not null");
        q.put("sceneParticipant.statblockRef", "select count(x) from SceneParticipant x where x.statBlock is not null");
        q.put("sceneParticipant.noteRef", "select count(x) from SceneParticipant x where x.note is not null");

        q.put("sceneTransition.kind", "select count(x) from SceneTransition x where x.kind is not null");
        q.put("sceneTransition.label", "select count(x) from SceneTransition x where x.label is not null");
        q.put("sceneTransition.targetSceneRef", "select count(x) from SceneTransition x where x.targetScene is not null");
        q.put("sceneTransition.externalDestination", "select count(x) from SceneTransition x where x.externalDestination is not null");
        q.put("sceneTransition.condition", "select count(x) from SceneTransition x where x.condition is not null");
        q.put("sceneTransition.dmNote", "select count(x) from SceneTransition x where x.dmNote is not null");

        q.put("sceneCheck.label", "select count(x) from SceneCheck x where x.label is not null");
        q.put("sceneCheck.ability", "select count(x) from SceneCheck x where x.ability is not null");
        q.put("sceneCheck.skill", "select count(x) from SceneCheck x where x.skill is not null");
        q.put("sceneCheck.dc", "select count(x) from SceneCheck x where x.dc is not null");
        q.put("sceneCheck.visibility", "select count(x) from SceneCheck x where x.visibility is not null");
        q.put("sceneCheck.success", "select count(x) from SceneCheck x where x.success is not null");
        q.put("sceneCheck.failure", "select count(x) from SceneCheck x where x.failure is not null");
        q.put("sceneCheck.partial", "select count(x) from SceneCheck x where x.partial is not null");
        q.put("sceneCheck.sourceLocator", "select count(x) from SceneCheck x where x.sourceLocator is not null");

        q.put("sceneLink.role", "select count(x) from SceneLink x where x.role is not null");
        q.put("sceneLink.displayText", "select count(x) from SceneLink x where x.displayText is not null");
        q.put("sceneLink.targetRef", "select count(x) from SceneLink x where x.targetId is not null");

        q.put("worldNpc.name", "select count(x) from WorldNpc x where x.name is not null");
        q.put("worldNpc.role", "select count(x) from WorldNpc x where x.role is not null");
        q.put("worldNpc.disposition", "select count(x) from WorldNpc x where x.disposition is not null");
        q.put("worldNpc.status", "select count(x) from WorldNpc x where x.status is not null");
        q.put("worldNpc.appearance", "select count(x) from WorldNpc x where x.appearance is not null");
        q.put("worldNpc.voice", "select count(x) from WorldNpc x where x.voice is not null");
        q.put("worldNpc.motivation", "select count(x) from WorldNpc x where x.motivation is not null");
        q.put("worldNpc.secret", "select count(x) from WorldNpc x where x.secret is not null");
        q.put("worldNpc.tags", "select count(x) from WorldNpc x where x.tags is not null");
        q.put("worldNpc.sourceLocator", "select count(x) from WorldNpc x where x.sourceLocator is not null");
        q.put("worldNpc.factionRef", "select count(x) from WorldNpc x where x.faction is not null");
        q.put("worldNpc.locationRef", "select count(x) from WorldNpc x where x.location is not null");
        q.put("worldNpc.statblockRef", "select count(x) from WorldNpc x where x.statBlock is not null");
        q.put("worldNpc.noteRef", "select count(x) from WorldNpc x where x.note is not null");

        q.put("worldLocation.name", "select count(x) from WorldLocation x where x.name is not null");
        q.put("worldLocation.kind", "select count(x) from WorldLocation x where x.kind is not null");
        q.put("worldLocation.summary", "select count(x) from WorldLocation x where x.summary is not null");
        q.put("worldLocation.services", "select count(x) from WorldLocation x where x.services is not null");
        q.put("worldLocation.secrets", "select count(x) from WorldLocation x where x.secrets is not null");
        q.put("worldLocation.tags", "select count(x) from WorldLocation x where x.tags is not null");
        q.put("worldLocation.sourceLocator", "select count(x) from WorldLocation x where x.sourceLocator is not null");
        q.put("worldLocation.tableLinks", "select count(x) from WorldLocationTableLink x");

        q.put("faction.name", "select count(x) from Faction x where x.name is not null");
        q.put("faction.goals", "select count(x) from Faction x where x.goals is not null");
        q.put("faction.resources", "select count(x) from Faction x where x.resources is not null");
        q.put("faction.reputationNotes", "select count(x) from Faction x where x.reputationNotes is not null");
        q.put("faction.tags", "select count(x) from Faction x where x.tags is not null");
        q.put("faction.sourceLocator", "select count(x) from Faction x where x.sourceLocator is not null");

        q.put("quest.title", "select count(x) from Quest x where x.title is not null");
        q.put("quest.status", "select count(x) from Quest x where x.status is not null");
        q.put("quest.summary", "select count(x) from Quest x where x.summary is not null");
        q.put("quest.tags", "select count(x) from Quest x where x.tags is not null");
        q.put("quest.rewards", "select count(x) from Quest x where x.rewards is not null");
        q.put("quest.prerequisites", "select count(x) from Quest x where x.prerequisites is not null");
        q.put("quest.outcomeNotes", "select count(x) from Quest x where x.outcomeNotes is not null");
        q.put("quest.sourceLocator", "select count(x) from Quest x where x.sourceLocator is not null");

        q.put("handout.title", "select count(x) from Handout x where x.title is not null");
        q.put("handout.tags", "select count(x) from Handout x where x.tags is not null");
        q.put("handout.dmOnly", "select count(x) from Handout x where x.dmOnly = true");
        q.put("handout.presented", "select count(x) from Handout x where x.dmOnly = false");

        q.put("trap.name", "select count(x) from Trap x where x.name is not null");
        q.put("trap.description", "select count(x) from Trap x where x.description is not null");
        q.put("trap.severity", "select count(x) from Trap x where x.severity is not null");
        q.put("trap.resetMode", "select count(x) from Trap x where x.resetMode is not null");
        q.put("trap.triggerDescription", "select count(x) from Trap x where x.triggerDescription is not null");
        q.put("trap.detectionPassiveThreshold", "select count(x) from Trap x where x.detectionPassiveThreshold is not null");
        q.put("trap.detectionCheck", "select count(x) from Trap x where x.detectionCheck.dc is not null");
        q.put("trap.save", "select count(x) from Trap x where x.save.dc is not null");
        q.put("trap.damage", "select count(x) from Trap x where x.damageExpression is not null");
        q.put("trap.additionalEffect", "select count(x) from Trap x where x.additionalEffect is not null");
        q.put("trap.countermeasureNotes", "select count(x) from Trap x where x.countermeasureNotes is not null");

        q.put("hazard.name", "select count(x) from Hazard x where x.name is not null");
        q.put("hazard.description", "select count(x) from Hazard x where x.description is not null");
        q.put("hazard.severity", "select count(x) from Hazard x where x.severity is not null");
        q.put("hazard.exposureMode", "select count(x) from Hazard x where x.exposureMode is not null");
        q.put("hazard.exposureText", "select count(x) from Hazard x where x.exposureText is not null");
        q.put("hazard.areaHint", "select count(x) from Hazard x where x.areaHint is not null");
        q.put("hazard.check", "select count(x) from Hazard x where x.check.dc is not null");
        q.put("hazard.damage", "select count(x) from Hazard x where x.damageExpression is not null");
        q.put("hazard.escalationText", "select count(x) from Hazard x where x.escalationText is not null");
        q.put("hazard.endingConditions", "select count(x) from Hazard x where x.endingConditions is not null");

        q.put("rollableTable.name", "select count(x) from RollableTable x where x.name is not null");
        q.put("rollableTable.description", "select count(x) from RollableTable x where x.description is not null");
        q.put("rollableTable.addressMode", "select count(x) from RollableTable x where x.addressMode is not null");
        q.put("rollableTable.rollExpression", "select count(x) from RollableTable x where x.rollExpression is not null");
        q.put("rollableTable.category", "select count(x) from RollableTable x where x.category is not null");
        q.put("rollableTable.tags", "select count(x) from RollableTable x where x.tags is not null");
        return q;
    }

    @Test
    void everyProfileFieldHasACoverageQuery() {
        assertThat(coverageQueries().keySet())
                .as("""
                    The profile was refreshed with fields nobody taught this test to check. Add a \
                    coverage query for each, or document why it is exempt -- do not let a new \
                    real-world field pass silently.""")
                .containsAll(profile.populatedFields().keySet());
    }

    @Test
    @Transactional
    void fixtureCoversEveryFieldTheRealPackagePopulates() {
        var uncovered = new TreeSet<String>();
        for (var entry : coverageQueries().entrySet()) {
            if (!profile.populatedFields().containsKey(entry.getKey())) {
                continue;
            }
            long count = (Long) em.createQuery(entry.getValue()).getSingleResult();
            if (count == 0) {
                uncovered.add(entry.getKey());
            }
        }

        assertThat(uncovered)
                .as("""
                    The real package populates these; PopulatedCampaignFixture does not, so no \
                    test can see a defect in how they render. Extend the fixture -- this is a \
                    floor, not a ceiling, and existing coverage the profile does not mention \
                    (nested locations) must stay.""")
                .isEmpty();
    }

    @Test
    void sectionAndTransitionKindsFromTheRealPackageAreExercised() {
        var missingSections = new TreeSet<>(profile.sectionKinds());
        var seededSections = em.createQuery(
                "select distinct x.kind from SceneSection x", Object.class).getResultList();
        seededSections.forEach(k -> missingSections.remove(k.toString()));
        assertThat(missingSections)
                .as("the real package has TRAP, HAZARD, SCALING and DEVELOPMENT sections; "
                    + "the fixture never rendered threat/_mechanics-card.html inside a scene")
                .isEmpty();

        var missingTransitions = new TreeSet<>(profile.transitionKinds());
        var seededTransitions = em.createQuery(
                "select distinct x.kind from SceneTransition x", Object.class).getResultList();
        seededTransitions.forEach(k -> missingTransitions.remove(k.toString()));
        assertThat(missingTransitions).isEmpty();
    }

    @Test
    void nestedLocationCoverageIsNotSacrificedToMatchTheProfile() {
        // The real package has worldLocation.parentLocation populated zero times, yet
        // world/locations-list.html dereferences loc.parentLocation.name -- a truncation fixed
        // in 2e67299. The profile is a floor; nobody may delete this to make the numbers line up.
        assertThat(seeded.childLocationId()).isNotNull();
        long nested = (Long) em.createQuery(
                "select count(x) from WorldLocation x where x.parentLocation is not null")
                .getSingleResult();
        assertThat(nested)
                .as("nested locations exceed the profile on purpose -- keep them")
                .isGreaterThan(0);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=FixtureShapeCoverageTest`

Expected: FAIL on `fixtureCoversEveryFieldTheRealPackagePopulates` and `sectionAndTransitionKindsFromTheRealPackageAreExercised`. The uncovered set should be approximately:

```
hazard.check, hazard.endingConditions, sceneCheck.partial, sceneParticipant.noteRef,
sceneSection.threatRef, sceneTransition.condition, sceneTransition.externalDestination,
trap.additionalEffect, trap.countermeasureNotes, trap.damage, trap.detectionCheck,
trap.detectionPassiveThreshold, trap.save, trap.triggerDescription, worldLocation.tableLinks,
worldNpc.noteRef, worldNpc.statblockRef
```

Missing section kinds: `DEVELOPMENT`, `HAZARD`, `SCALING`, `TRAP`. Missing transition kind: `EXIT`.

If `everyProfileFieldHasACoverageQuery` fails, a coverage query is missing — add it before proceeding. If a JPQL query fails to parse, the property name is wrong for that entity; read the entity and correct the query. Do not delete the query.

- [ ] **Step 3: Extend the fixture**

All edits are in `src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java`.

**3a. New imports** — add to the imports block:

```java
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableLinkRole;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLink;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCheckWrite;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
```

**3b. New dependencies** — add fields, constructor parameters and assignments alongside the existing ones:

```java
    private final NoteService notes;
    private final WorldLocationTableLinkRepository locationTableLinks;
```

**3c. Traps and hazards get their full mechanics.** The real package populates trigger text, detection, saves, damage and countermeasures on every trap; the fixture's bare trap meant `threat/_mechanics-card.html` rendered almost nothing. Replace the `Trap trap = traps.create(...)` call (currently lines 219-223):

```java
        // Fully specified so threat/_mechanics-card.html renders every row it can: the real
        // package populates triggerDescription, detectionCheck, save, damage and
        // countermeasureNotes on all 5 of its traps.
        Trap trap = traps.create(campaignId, new TrapWrite(
                "pit-trap", "Fallgrube", "Eine zehn Fuß tiefe Grube unter loser Erde.",
                ThreatSeverity.SETBACK, 1, 4,
                "Wer auf die lose Erde tritt.", "Der Gang vor der Tür",
                12, new ThreatCheckWrite(ThreatCheckMode.ABILITY_CHECK, "wis", "perception", 12),
                List.of(), null,
                new ThreatCheckWrite(ThreatCheckMode.SAVING_THROW, "dex", null, 13),
                "2d6", List.of(DamageType.BLUDGEONING),
                "Das Opfer liegt am Boden.",
                ThreatResetMode.MANUAL, null, null,
                "Ein Brett über der Grube macht sie harmlos.", List.of()), null);
```

Replace the `Hazard hazard = hazards.create(...)` call (currently lines 225-230):

```java
        Hazard hazard = hazards.create(campaignId, new HazardWrite(
                "green-slime", "Grüner Schleim", "Ätzender Schleim an der Decke.",
                ThreatSeverity.SETBACK, 1, 4, HazardExposureMode.ON_ENTER,
                "Beim Betreten des Feldes", "10-Fuß-Feld",
                new ThreatCheckWrite(ThreatCheckMode.ABILITY_CHECK, "int", "nature", 11),
                "1d6", List.of(DamageType.ACID),
                "Der Schleim frisst sich durch Rüstung.",
                "Feuer oder Kälte zerstören ihn.",
                List.of()), null);
```

**3d. TRAP, HAZARD, SCALING and DEVELOPMENT sections, one wired to a threat.** The trap and hazard must already exist, so move the trap/hazard creation **above** the `structured.addSection(...)` calls if it is not already, or add these sections after the threats are created. The `SceneSectionCommand` 7-argument constructor takes `threatKind` and `threatId`.

```java
        // The real package carries all seven section kinds. Without TRAP and HAZARD sections
        // wired to a threat, threat/_mechanics-card.html never rendered inside a scene at all.
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.TRAP, "Fallgrube im Gang",
                "Der Gang vor der Tür ist untergraben.", "Fixture, S. 23", 4,
                ThreatKind.TRAP, trap.getId()));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.HAZARD, "Schleim an der Decke",
                "Über dem Schreibtisch hängt grüner Schleim.", "Fixture, S. 23", 5,
                ThreatKind.HAZARD, hazard.getId()));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.SCALING, "Für größere Gruppen",
                "Bei fünf oder mehr Charakteren: ein weiterer Späher.", "Fixture, S. 23", 6));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.DEVELOPMENT, "Wenn die Gruppe zu lange braucht",
                "Der Bote kehrt zurück und schlägt Alarm.", "Fixture, S. 23", 7));
```

**3e. A check with a `partial` outcome.** Add after the existing `addCheck` call:

```java
        structured.addCheck(campaignId, rich.getId(), new SceneCheckCommand(
                "Siegel erkennen", "int", "history", 15, SceneCheckVisibility.DM_ONLY,
                "Die Gruppe erkennt das Wappen sofort.", "Nichts.",
                "Die Gruppe erkennt es als adelig, aber nicht welches Haus.",
                null, null, null, "Fixture, S. 22", 1));
```

If `SceneCheckVisibility` has no `DM_ONLY` constant, read the enum and use the DM-facing value it does define.

**3f. An EXIT transition with a condition and an external destination.** Add after the existing `addTransition` call:

```java
        // EXIT is half the real package's transitions; the fixture only had CHOICE.
        structured.addTransition(campaignId, rich.getId(), new SceneTransitionCommand(
                SceneTransitionKind.EXIT, "Zurück nach Phandalin", null,
                "Phandalin, Kapitel 2", "Nur bei Tageslicht.",
                "Die Gruppe verliert einen halben Tag.", "Fixture, S. 23", 1));
```

**3g. Notes referenced by a participant and an NPC.** Create the note before the participants, then wire both refs. Replace the unlinked-participant call (currently lines 169-171):

```java
        Note boteNote = notes.create(campaignId, NoteType.NPC, "Der Bote",
                "Trug den Brief, kennt den Absender nicht.", "bote", true);

        // Deliberately statless, mirroring the 2 of 67 in the real package -- but carrying a
        // note ref, which 4 of 67 do.
        structured.addParticipant(campaignId, rich.getId(), new SceneParticipantCommand(
                "Namenloser Bote", 1, SceneParticipantDisposition.NEUTRAL,
                "Am Eingang", null, boteNote.getId(), "Fixture, S. 22", 1));
```

**3h. The NPC gets a statblock and a note.** Replace the `world.createNpc(...)` call (currently lines 195-200) — note the existing `null, null` in positions 6 and 7 are `noteId` and `statblockId`:

```java
        Note daranNote = notes.create(campaignId, NoteType.NPC, "Daran Edermath",
                "Weiß, wo die Karte liegt.", "npc", true);

        WorldNpc npc = world.createNpc(campaignId, new NpcCommand(
                "Daran Edermath", "Obstbauer und Ex-Ritter", WorldDisposition.FRIENDLY,
                faction.getId(), child.getId(), daranNote.getId(), spaeher.getId(),
                "Ein hochgewachsener Halbelf mit weißem Haar.", "Ruhig, bedacht",
                "Will den Orden wiederbeleben.", "War früher Ritter.", "Langschwert",
                WorldNpcStatus.ALIVE, "ally", "Fixture, S. 30"));
```

**3i. A location↔table link.** Add after the rollable table is created, before the `return`:

```java
        // 2 of 11 real locations link a random-encounter table; world pages dereference it.
        WorldLocationTableLink locationTable = new WorldLocationTableLink();
        locationTable.setLocation(parent);
        locationTable.setTable(table);
        locationTable.setRole(RollableTableLinkRole.RANDOM_ENCOUNTERS);
        locationTable.setSortOrder(0);
        locationTableLinks.save(locationTable);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=FixtureShapeCoverageTest`
Expected: PASS, 4 tests.

If `fixtureCoversEveryFieldTheRealPackagePopulates` still lists entries, add them — do not remove their coverage queries.

- [ ] **Step 5: Run the full suite**

Run: `./mvnw test`

Expected: BUILD SUCCESS, 2126 tests, 0 failures.

The fixture now renders content it never rendered before, so failures here are **findings, not noise**:
- `FullPageRenderSmokeTest` renders the scene page with TRAP/HAZARD sections, so `threat/_mechanics-card.html` runs inside a scene for the first time. A `LazyInitializationException` on the threat's embedded check or damage types is a real bug — fix the initialisation in `ThreatCardAssembler` or `AdventureService.findSceneDetailView`.
- `DmSensitiveFieldCoverageTest` must stay green — Task 2 tagged the mechanics-card root.
- `DmModeCoverageTest` must stay green — the new sections are non-`READ_ALOUD`, so `_scene-sections.html:20` already tags them.

Investigate every failure. Do not weaken the fixture to make a test pass.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/support/PopulatedCampaignFixture.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/support/FixtureShapeCoverageTest.java
git commit -m "test(fixture): cover every field the real package populates

Asserts fixture superset-of the committed shape profile. The fixture never
exercised TRAP, HAZARD, SCALING or DEVELOPMENT sections, so
threat/_mechanics-card.html had never rendered inside a scene; it had no
handouts, which is why the empty cockpit picker was invisible; and traps
carried no mechanics at all.

Coverage, not equality -- and a floor, not a ceiling. Nested locations
exceed the profile on purpose and a test now says so.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Seed an encounter from a scene — service (F4a)

The imported campaign has 0 encounters, so the cockpit's ACTIVE and PLANNED ENCOUNTER panels are permanently empty, while 22 scenes carry hostile participants. When combat starts the DM builds the encounter by hand even though the app knows every combatant, its count and its statblock.

Both primitives already exist. This service composes them.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedService.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedServiceTest.java`

**Interfaces:**
- Consumes: `EncounterService.create(UUID campaignId, CreateRequest(String name, UUID mapId))` → `EncounterDto`; `EncounterService.addFromLibrary(UUID encounterId, AddFromLibraryRequest(UUID statBlockId, int quantity, String groupName, UUID waveId, Integer startX, Integer startY, String placementRegionKey))` → `List<CombatantDto>`; `AdventureService.findSceneById(UUID)` → `Scene`; `AdventureService.linkEncounter(UUID sceneId, UUID encounterId)` → `Scene`.
- Produces, consumed by Task 7:
  - `SceneEncounterSeedService.SeedResult(UUID encounterId, String encounterName, int combatantsAdded, List<String> skippedParticipants, boolean alreadyExisted)`
  - `SceneEncounterSeedService.seedFromScene(UUID campaignId, UUID sceneId) → SeedResult`
  - `SceneEncounterSeedService.canSeed(Scene scene) → boolean`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The imported campaign has zero encounters while 22 of its scenes carry hostile
 * participants, so a DM had to rebuild by hand what the app already knew: every combatant,
 * its count and its statblock.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SceneEncounterSeedServiceTest {

    @Autowired private SceneEncounterSeedService seeder;
    @Autowired private EncounterService encounters;
    @Autowired private AdventureService adventures;
    @Autowired private PopulatedCampaignFixture fixture;

    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeAll
    void setUp() {
        seeded = fixture.seed();
    }

    @Test
    void seedsOneCombatantPerParticipantCopyWithTheStatblocksHp() {
        var result = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());

        assertThat(result.alreadyExisted()).isFalse();
        assertThat(result.encounterName()).contains("Der Schreibtisch");

        // The rich scene has one statblock-linked participant at quantity 2.
        assertThat(result.combatantsAdded()).isEqualTo(2);

        var combatants = encounters.findCombatants(result.encounterId());
        assertThat(combatants).hasSize(2);
        assertThat(combatants).allSatisfy(c -> {
            assertThat(c.name()).contains("Späher der Redbrands");
            assertThat(c.kind()).isEqualTo("MONSTER");
            // PARTICIPANT_STATBLOCK_HP is "16 (3W8+3)"; parseHpAsInt takes the leading number.
            assertThat(c.maxHp()).isEqualTo(16);
        });
        assertThat(combatants).extracting("groupId").containsOnly(combatants.get(0).groupId());
        assertThat(combatants).filteredOn("groupLeader", true).hasSize(1);
    }

    @Test
    void reportsParticipantsItCouldNotResolveRatherThanDroppingThem() {
        var result = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());

        // 2 of 67 real participants carry no statblock. Inventing HP for them would be worse
        // than saying so.
        assertThat(result.skippedParticipants())
                .as("a participant that silently vanishes is a monster the DM forgets to run")
                .containsExactly("Namenloser Bote");
    }

    @Test
    void linksTheEncounterBackToTheScene() {
        var result = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());

        var scene = adventures.findSceneById(seeded.richSceneId());
        assertThat(scene.getEncounter()).isNotNull();
        assertThat(scene.getEncounter().getId()).isEqualTo(result.encounterId());
    }

    @Test
    void runningItTwiceDoesNotDuplicateTheEncounterOrTheCombatants() {
        var first = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());
        var second = seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());

        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.encounterId()).isEqualTo(first.encounterId());
        assertThat(second.combatantsAdded()).isZero();
        assertThat(encounters.findCombatants(first.encounterId())).hasSize(2);
    }

    @Test
    void aSceneWithNoStatblockLinkedParticipantsCannotBeSeeded() {
        var empty = adventures.findSceneById(seeded.secondSceneId());
        assertThat(seeder.canSeed(empty))
                .as("the action must be absent for a scene the app knows nothing about")
                .isFalse();

        var rich = adventures.findSceneById(seeded.richSceneId());
        assertThat(seeder.canSeed(rich)).isTrue();
    }
}
```

> **Note on `findCombatants`:** `EncounterService` exposes combatants for an encounter, but confirm the exact method name before running — read `EncounterService` and use whatever it actually provides (it may be `listCombatants`, or reachable via `findById(encounterId).combatants()`). Adjust the three call sites in this test accordingly; do not add a new method to `EncounterService` for the test's convenience.

> **Note on `@BeforeAll` and idempotency:** the fixture is seeded once per class, and JUnit does not guarantee method order. `linksTheEncounterBackToTheScene` and `reportsParticipantsItCouldNotResolveRatherThanDroppingThem` call `seedFromScene` again, which by design returns the existing encounter. That is what makes them safe in any order — and it is exactly the idempotency the spec requires.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SceneEncounterSeedServiceTest`

Expected: FAIL to compile — `cannot find symbol: class SceneEncounterSeedService`.

- [ ] **Step 3: Write the service**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedService.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds an encounter from the monsters a scene already describes.
 *
 * <p>An imported campaign carries zero encounters but 22 scenes with hostile participants, so
 * combat started with the DM retyping what the app already knew. Both halves of the job
 * existed: EncounterService.create makes the encounter, and addFromLibrary resolves a
 * statblock, parses its HP, creates N combatants, groups them and attaches them to the main
 * wave. A SceneParticipant carries exactly statBlock + quantity + displayName. This class is
 * the seam between them, so neither adventure nor encounter has to know about the other.
 */
@Service
public class SceneEncounterSeedService {

    /**
     * @param skippedParticipants display names of participants with no statblock. The real
     *                            package has 2 of 67. They are reported rather than given
     *                            invented HP, and rather than silently disappearing.
     * @param alreadyExisted      true when the scene was already linked to an encounter and
     *                            nothing was created or added.
     */
    public record SeedResult(UUID encounterId, String encounterName, int combatantsAdded,
                             List<String> skippedParticipants, boolean alreadyExisted) {}

    private final AdventureService adventures;
    private final EncounterService encounters;

    public SceneEncounterSeedService(AdventureService adventures, EncounterService encounters) {
        this.adventures = adventures;
        this.encounters = encounters;
    }

    /** True when this scene has at least one participant the app can turn into a combatant. */
    public boolean canSeed(Scene scene) {
        if (scene.getParticipants() == null) {
            return false;
        }
        return scene.getParticipants().stream().anyMatch(p -> p.getStatBlock() != null);
    }

    @Transactional
    public SeedResult seedFromScene(UUID campaignId, UUID sceneId) {
        Scene scene = adventures.findSceneById(sceneId);
        Hibernate.initialize(scene.getParticipants());
        for (SceneParticipant participant : scene.getParticipants()) {
            Hibernate.initialize(participant.getStatBlock());
        }

        // Re-running the action must not silently duplicate the encounter or double its
        // combatants -- a DM who clicks twice at the table would otherwise be running two.
        if (scene.getEncounter() != null) {
            Hibernate.initialize(scene.getEncounter());
            return new SeedResult(scene.getEncounter().getId(), scene.getEncounter().getName(),
                    0, List.of(), true);
        }

        String name = "Encounter: " + scene.getTitle();
        UUID mapId = scene.getMap() != null ? scene.getMap().getId() : null;
        var encounter = encounters.create(campaignId, new EncounterService.CreateRequest(name, mapId));

        int added = 0;
        List<String> skipped = new ArrayList<>();
        for (SceneParticipant participant : scene.getParticipants()) {
            if (participant.getStatBlock() == null) {
                skipped.add(participant.getDisplayName());
                continue;
            }
            // displayName as groupName keeps the scene's own wording ("Späher der Redbrands")
            // rather than falling back to the statblock's catalog name.
            var combatants = encounters.addFromLibrary(encounter.id(),
                    new EncounterService.AddFromLibraryRequest(
                            participant.getStatBlock().getId(),
                            Math.max(1, participant.getQuantity()),
                            participant.getDisplayName(),
                            null, null, null, null));
            added += combatants.size();
        }

        adventures.linkEncounter(sceneId, encounter.id());
        return new SeedResult(encounter.id(), name, added, List.copyOf(skipped), false);
    }
}
```

> If `EncounterDto`'s accessor for its id is not `id()`, read the record and use the correct one.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SceneEncounterSeedServiceTest`
Expected: PASS, 5 tests.

- [ ] **Step 5: Run the encounter suite, then the full suite**

Run: `./mvnw test -Dtest='EncounterServiceTest,EncounterWaveServiceTest,ThreatEncounterIntegrationTest,EncounterPartyHpSyncTest'`
Expected: PASS — the spec names these explicitly as must-not-regress.

Run: `./mvnw test`
Expected: BUILD SUCCESS, 2131 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedServiceTest.java
git commit -m "feat(encounter): build an encounter from a scene's participants

The imported campaign has zero encounters and 22 scenes with hostile
participants, so combat started with the DM retyping what the app already
knew. EncounterService.create and addFromLibrary already did the whole job;
this is the seam between them.

Participants with no statblock (2 of 67 in the real package) are reported
by name rather than given invented HP. Re-running on a scene that already
has an encounter returns the existing one.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Seed an encounter from a scene — UI (F4b)

Put the action where the DM is: the scene page's action rail, next to the Linked Encounter block it populates.

The cockpit story rail is deliberately **not** covered here. The spec says "the scene page **and/or** the cockpit story rail", and the scene page is where a DM preps. Adding it to the cockpit means a second endpoint and a rail-refresh path; propose it separately if table use shows it is wanted.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java` (after `unlinkEncounter`, line 214)
- Modify: `src/main/resources/templates/adventure/_action-rail.html:43-51`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructuredTemplateContractTest.java`

**Interfaces:**
- Consumes: `SceneEncounterSeedService.seedFromScene`, `SceneEncounterSeedService.canSeed`, `SceneEncounterSeedService.SeedResult` (Task 6); `SceneController.loadActionRail(campaignId, adventureId, sceneId, model)` (existing private helper, line 658).
- Produces: `POST /campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/seed-encounter`, returning `adventure/_action-rail :: actionRail` with a `seedResult` model attribute.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructuredTemplateContractTest.java`, before the private `count` helper:

```java
    @Test
    void actionRailOffersSeedEncounterOnlyWhenTheSceneHasResolvableParticipants() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("seed-encounter");
        assertThat(html).contains("canSeedEncounter");
        assertThat(html)
                .as("re-running must not be offered once the scene already has an encounter")
                .contains("scene.encounter == null");
    }

    @Test
    void actionRailReportsParticipantsTheSeedCouldNotResolve() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("seedResult.skippedParticipants");
        assertThat(html).contains("seedResult.combatantsAdded");
    }
```

Then add the end-to-end test. `SceneStructuredTemplateContractTest` reads template files and has no Spring context, so this needs its own class. Create `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneSeedEncounterControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
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
class SceneSeedEncounterControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private AdventureService adventures;

    private MockMvc mvc;
    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeAll
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        seeded = fixture.seed();
    }

    @Test
    void scenePageOffersTheActionBeforeSeedingAndTheLinkAfter() throws Exception {
        String before = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(before).contains("Start encounter from this scene");

        String rail = mvc.perform(post("/campaigns/{c}/adventures/{a}/scenes/{s}/seed-encounter",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(rail)
                .as("the rail must come back showing what was built and what was not")
                .contains("Encounter: Der Schreibtisch")
                .contains("Namenloser Bote");

        String after = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(after)
                .as("once linked, the scene shows its encounter instead of offering to build one")
                .contains("Linked Encounter")
                .doesNotContain("Start encounter from this scene");
    }

    @Test
    void aSceneWithNoResolvableParticipantsDoesNotOfferTheAction() throws Exception {
        String html = mvc.perform(get("/campaigns/{c}/adventures/{a}/scenes/{s}",
                        seeded.campaignId(), seeded.adventureId(), seeded.secondSceneId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("Start encounter from this scene");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest='SceneStructuredTemplateContractTest,SceneSeedEncounterControllerTest'`

Expected: FAIL. The two contract tests fail on missing strings in the template; `scenePageOffersTheActionBeforeSeedingAndTheLinkAfter` fails on the missing `Start encounter from this scene` label.

- [ ] **Step 3: Add the controller mapping and the model flag**

In `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java`:

Add the import and constructor dependency. Import:

```java
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
```

Field, alongside the others:

```java
    private final SceneEncounterSeedService encounterSeeder;
```

Constructor parameter (append after `AudioCueRepository audioCueRepository`) and assignment:

```java
                           AudioCueRepository audioCueRepository,
                           SceneEncounterSeedService encounterSeeder) {
```
```java
        this.encounterSeeder = encounterSeeder;
```

Insert the mapping after `unlinkEncounter` (line 214):

```java
    /**
     * The app already knows every combatant in a scene, its count and its statblock, so the
     * DM should not have to retype them when initiative starts. Returns the action rail so the
     * new Linked Encounter block and the seed report swap in together.
     */
    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/seed-encounter")
    public String seedEncounter(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID id,
                                Model model) {
        model.addAttribute("seedResult", encounterSeeder.seedFromScene(campaignId, id));
        return loadActionRail(campaignId, adventureId, id, model);
    }
```

Both `sceneDetail` (line 72) and `loadActionRail` (line 658) must expose the flag the template gates on. Add this line to **both**, immediately after `model.addAttribute("scene", scene);`:

```java
        model.addAttribute("canSeedEncounter", encounterSeeder.canSeed(scene));
```

In `sceneDetail` the local variable is `scene` (assigned from `view.scene()` at line 78), so the same line works verbatim.

- [ ] **Step 4: Add the action and the report to the action rail**

In `src/main/resources/templates/adventure/_action-rail.html`, replace the Linked Encounter block (lines 43-51) with:

```html
    <div th:if="${scene.encounter != null}" class="u-mb-md">
      <h4 class="u-text-sm u-mb-xs">Linked Encounter</h4>
      <div class="u-flex u-gap-xs">
        <span th:text="${scene.encounter.name}">Encounter Name</span>
        <a th:href="@{/campaigns/{cid}/encounters/{eid}(cid=${campaignId},eid=${scene.encounter.id})}"
           class="btn btn-sm">Activate</a>
      </div>
    </div>

    <!-- An imported campaign carries no encounters at all, so combat used to start with the
         DM retyping monsters the app already knows. Offered only when there is something to
         build from, and only until the scene actually has an encounter. -->
    <div th:if="${scene.encounter == null and canSeedEncounter}" class="u-mb-md">
      <button class="btn btn-sm btn-primary"
              th:hx-post="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}/seed-encounter(cid=${campaignId},aid=${adventure.id},sid=${scene.id})}"
              hx-target="#actionRail" hx-swap="innerHTML">
        Start encounter from this scene
      </button>
    </div>

    <div th:if="${seedResult != null and !seedResult.alreadyExisted}" class="card card--compact u-mb-md">
      <strong th:text="${seedResult.encounterName}">Encounter</strong>
      <span class="u-text-xs"
            th:text="|${seedResult.combatantsAdded} combatants added|">0 combatants added</span>
      <!-- 2 of 67 real participants carry no statblock. Naming them beats inventing their HP,
           and beats letting them disappear. -->
      <div th:if="${!seedResult.skippedParticipants.isEmpty()}" class="u-text-xs u-mt-xs">
        <span th:text="|${#lists.size(seedResult.skippedParticipants)} participant(s) skipped — no statblock:|">Skipped:</span>
        <span th:text="${#strings.listJoin(seedResult.skippedParticipants, ', ')}">Names</span>
      </div>
    </div>
```

> If `#strings.listJoin` is unavailable in this Thymeleaf version, use `${#strings.arrayJoin(seedResult.skippedParticipants, ', ')}` or iterate with `th:each`. Verify against how the codebase joins lists elsewhere.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test -Dtest='SceneStructuredTemplateContractTest,SceneSeedEncounterControllerTest,SceneEncounterSeedServiceTest'`
Expected: PASS.

- [ ] **Step 6: Run the full suite**

Run: `./mvnw test`

Expected: BUILD SUCCESS, 2135 tests, 0 failures. `DmSensitiveFieldCoverageTest` must stay green — the new rail markup renders no DM-sensitive entity field via `th:text` (`seedResult` is a service record, not an entity, and its properties are not in the field list).

- [ ] **Step 7: Verify in the running app**

Run: `./mvnw spring-boot:run`, open `http://localhost:8081`, import or open the LMoP campaign, navigate to a scene with hostile participants, and confirm:

1. The scene shows **Start encounter from this scene**.
2. Clicking it swaps the rail in place, showing the new encounter name, the combatant count, and any skipped participant by name.
3. The Linked Encounter block now appears and the seed button is gone.
4. Clicking **Activate** opens an encounter whose combatants match the participants' statblocks, counts and HP.
5. On the handouts page, **Make player-visible** flips a handout's badge in place; the cockpit picker then lists it.
6. With DM Mode off on a quest, location and faction detail page, no rewards, prerequisites, outcome notes, secrets, goals or reputation notes are visible, while the navbar reads PLAYER-SAFE.

Stop the app before committing.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java \
        src/main/resources/templates/adventure/_action-rail.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructuredTemplateContractTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneSeedEncounterControllerTest.java
git commit -m "feat(adventure): start an encounter from a scene in one click

Puts SceneEncounterSeedService behind a button on the scene action rail,
shown only when the scene has statblock-linked participants and no
encounter yet. The rail swaps back in place reporting what was built and
naming any participant that had no statblock to resolve.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Verification Against the Spec

Run this before declaring the plan complete. Every acceptance criterion in the spec maps to a task and a named test.

| Spec criterion | Task | Test |
|---|---|---|
| F1: no quest reward/prereq/outcome, location secret or faction reputation note in DM-Mode-off output | 1 | `DmModeCoverageTest.questRewardsAreHiddenFromTheTable` + 5 siblings |
| F1: a new DM-sensitive field rendered without `dm-only` fails the suite | 2 | `DmSensitiveFieldCoverageTest.everyDmSensitiveFieldRendersInsideADmOnlySubtree` |
| F1: `READ_ALOUD` bodies remain visible | 1, 2 | `DmModeCoverageTest.readAloudStaysVisibleBecauseItIsMeantForThePlayers`, `DmSensitiveFieldCoverageTest.readAloudBlockIsNotTagged` |
| F1: existing 16 `DmModeCoverageTest` assertions still pass | 1 | full suite |
| F2: a DM can make a handout presentable without the API | 3 | `HandoutDmOnlyToggleTest.handoutCardOffersAControlToLeaveDmOnly`, `.togglingDmOnlyOffMakesTheHandoutPresentable` |
| F2: cockpit picker lists a non-DM-only handout | 3 | `HandoutDmOnlyToggleTest.cockpitPickerListsNonDmOnlyHandouts` |
| F2: picker communicates the all-DM-only state | 3 | `HandoutDmOnlyToggleTest.cockpitPickerSaysSoWhenEveryHandoutIsDmOnly` |
| F2: presented → DM-only still detaches | 3 | `HandoutDmOnlyToggleTest.togglingAPresentedHandoutToDmOnlyStillDetachesIt` |
| F3: committed profile has no content strings | 4 | `PackageShapeProfileTest.committedProfileParsesAndCarriesNoContentStrings` |
| F3: re-running the extractor reproduces the profile | 4 | `PackageShapeProfileTest.extractorReproducesTheCommittedProfile` |
| F3: a test fails when the fixture stops covering a populated field | 5 | `FixtureShapeCoverageTest.fixtureCoversEveryFieldTheRealPackagePopulates` |
| F3: fixture covers TRAP, HAZARD, SCALING, DEVELOPMENT, a resolved `threatId`, and two handouts | 3, 5 | `FixtureShapeCoverageTest.sectionAndTransitionKindsFromTheRealPackageAreExercised`, `.fixtureCoversEveryFieldTheRealPackagePopulates` |
| F3: profile is a floor, not a ceiling | 5 | `FixtureShapeCoverageTest.nestedLocationCoverageIsNotSacrificedToMatchTheProfile` |
| F4: one action produces an encounter matching statblocks, counts and HP | 6 | `SceneEncounterSeedServiceTest.seedsOneCombatantPerParticipantCopyWithTheStatblocksHp` |
| F4: absent for a scene with no statblock-linked participants | 6, 7 | `SceneEncounterSeedServiceTest.aSceneWithNoStatblockLinkedParticipantsCannotBeSeeded`, `SceneSeedEncounterControllerTest.aSceneWithNoResolvableParticipantsDoesNotOfferTheAction` |
| F4: running twice does not duplicate | 6 | `SceneEncounterSeedServiceTest.runningItTwiceDoesNotDuplicateTheEncounterOrTheCombatants` |
| F4: links back to the scene | 6 | `SceneEncounterSeedServiceTest.linksTheEncounterBackToTheScene` |
| F4: unlinked participants reported, not dropped | 6, 7 | `SceneEncounterSeedServiceTest.reportsParticipantsItCouldNotResolveRatherThanDroppingThem` |
| F4: existing encounter tests still pass | 6 | `EncounterServiceTest`, `EncounterWaveServiceTest`, `ThreatEncounterIntegrationTest`, `EncounterPartyHpSyncTest` |

Final gate:

```bash
./mvnw test
git log --oneline fab79f9..HEAD
```

Expected: BUILD SUCCESS with roughly 2135 tests and 0 failures, and seven commits.
