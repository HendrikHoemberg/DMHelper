# UI Redesign Part 3: Feature Surfaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure every preparation and reference surface — campaign selection and home, adventures and scenes, quests, world records, notes, calendar, encounters, party, character sheets, treasury, ledger, maps, handouts, audio, Library, tables, traps, and hazards — onto the archetypes and shared components, so each page has real hierarchy rather than a repaint.

**Architecture:** Each task restructures one feature family onto the shared fragments and archetypes, keeps that feature's existing controller and template tests green, and commits. Routes, form contracts, htmx targets, and persisted semantics do not change; where a summary or state cluster cannot be rendered from the current view model, a read-only controller-model addition is permitted. Verification is deliberately split: three Playwright render gates — one per stage — assert computed geometry and capture screenshots, three small contract classes hold the handful of invariants a screenshot cannot show, and visual hierarchy is signed off by a human looking at the captures.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, htmx, Alpine.js, vanilla JavaScript, repository-owned CSS and inline SVG, JUnit 5, AssertJ, jsoup, Playwright for Java, Maven Wrapper.

## Position in the program

This is **Part 3 of 4** of the whole-product UI redesign. The program index is
`docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md`; the design authority is
`docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`.

| Part | Stages | Tasks | Status |
|---|---|---|---|
| 1. Visual foundations | 1 | 1–9 | **must be complete before this part** |
| 2. Shell and shared archetypes | 2 | 10–19 | **must be complete before this part** |
| **3. Feature surfaces (this plan)** | 3–5 | 20–37 | consumes Parts 1–2 |
| 4. Editors, cockpit, and release | 6–8 | 38–53 | consumes this part |

Task numbers are global across the four parts, so every cross-reference in the program
(`Task 49 removes them`, `Task 30 owns that`) means the same task everywhere.

**Working product after this part:** every preparation and reference page is restructured,
not just repainted. Only the map editor, the session cockpit, the presentation overlay, the
administration/error pages, and the compatibility-layer removal remain — all of them Part 4.

### The three stages in this part are independent

Stage 3 (Tasks 20–26), Stage 4 (Tasks 27–33), and Stage 5 (Tasks 34–37) each depend only on
Part 2, not on each other. Execute them in any order, or in parallel git worktrees — see
`superpowers:using-git-worktrees` and `superpowers:dispatching-parallel-agents`. Each stage
ends with its own gate and its own `./mvnw test`.

## Global Constraints

Every task's requirements implicitly include this section.

- The design authority is `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`. This program supersedes the deleted `2026-07-31-ui-redesign-visual-foundations.md` and `2026-07-31-whole-product-ui-redesign-program.md`; both are absorbed here.
- The former `ui-polish-spec` document (deleted at Task 53) is superseded, including its
  brown-palette-reuse and no-responsive-work requirements.
- Desktop and laptop browsers only. Minimum supported viewport **1280x720**; primary range **1440x900**–**1920x1080**; large-screen gate **2560x1440**; zoom gates **125%** and **150%**.
- Preserve Spring MVC, Thymeleaf, htmx, Alpine.js, and vanilla JavaScript. No SPA, no frontend package manager, no CSS framework, no icon font, no third-party component system.
- Preserve existing routes, form contracts, persisted entities, campaign-package formats, player-safety filtering, cockpit preset storage, and the customizable four-zone cockpit.
- `tokens.css` is the only file allowed to contain raw UI color values. Persisted terrain, drawing, and campaign-sigil colors are domain data, not UI chrome, and are outside that rule.
- Type roles: system UI for chrome, controls, labels, tables, values, metadata, editor and cockpit chrome; Alegreya for narrative and document content; Cinzel only for the product wordmark and at most one principal title per view; monospace for identifiers, dice expressions, and source keys.
- Aged gold is only ever a primary action, current selection, or visible focus. Secondary controls and ordinary boundaries stay neutral. At most one filled-gold primary action per action region.
- Every semantic state combines at least two of: foreground color, tinted background or border, icon, explicit text, row or shape emphasis. Never color alone.
- Required runtime information never renders below `--text-sm`. Combat numbers, dates, quantities, currency, and resources use tabular figures.
- WCAG AA text contrast; 3:1 for interactive boundaries and focus indicators; complete keyboard operation; reduced-motion support.
- No database migration. View-only DTO or controller-model additions are permitted where a summary, state cluster, or relationship rail cannot be rendered safely from the existing view model; they must not change persisted semantics.
- A stage is complete only when every page in its scope is fully migrated. A visibly hybrid page fails review.
- Keep every existing controller, template, htmx, package, player-safety, encounter, map, cockpit, and accessibility test green. `./mvnw test` must pass at the end of every stage, and `./mvnw test -P gates` — which adds the Playwright gates — at the end of the part.
- **How tests may assert.** A test may assert on rendered output, parsed CSS rules, a Java
  model, or measured browser geometry. A test may not assert that a template or stylesheet
  *source file* contains a particular string, unless that string is a structural marker with
  no visual or editorial meaning — a `th:fragment` signature, a `data-*` hook, a CSS selector
  resolved through `CssRules`. Never slice source at a character offset (`indexOf` +
  `substring`) and assert on the slice; parse it with Jsoup instead. A scan that can match
  nothing must assert it matched something before asserting what it found.
  `docs/test-suite-triage.md` records why: 27 test classes were deleted in July 2026 for
  failing this rule, and one offset-slice guard was passing while a destructive control sat
  in a page header.

## Shared task protocol

Applies to every task in this plan.

- **Verification model for this part.** Most tasks here change how a page is *composed*, and
  composition quality is not something a source-text assertion can prove — a template can
  satisfy every substring check and still read badly. So each task is verified by three things
  instead: the feature's existing controller and template tests stay green, the stage's
  Playwright render gate passes, and you look at the stage's screenshots and judge them
  against spec section 5. Do not add a per-feature `*LayoutTest` that greps template source
  for `data-*` attributes; that pattern was deliberately removed from this plan.
- Where a task *does* carry a new assertion, it goes in that stage's single contract class
  (`NarrativeSurfaceContractTest`, `OperationalSurfaceContractTest`,
  `ReferenceSurfaceContractTest`). Those hold orderings, cardinalities, and safety semantics —
  things that are invisible in a screenshot and expensive to get wrong. Keep them small; one
  class per stage also means three parallel worktrees never collide on the same test file.
- Red first when there *is* a test: write or extend it, run it, confirm the failure message
  names the missing thing, then implement.
- One task, one commit. Use `feat:`, `refactor:`, `test:`, or `fix:` prefixes.
- Run the task's focused test command after each red/green cycle; run `./mvnw test` at the
  end of every stage before the stage's review step.
- **Browser gates are opt-in.** Every class that launches Playwright carries `@Tag("browser")`
  and is excluded from the default `./mvnw test`, which is why that command is ~70s and not
  ~4min. Add `-P gates` to include them. The exclusion also applies to `-Dtest=`, so selecting
  a gate class by name needs the profile too: `./mvnw -P gates -Dtest='SomeRenderGateTest' test`.
  A new render gate must be tagged: an untagged one runs in both tiers and hands the edit
  loop its browser cost back.
- Any new browser gate navigates through `support.PageReady.open(page, base, path)` rather than
  calling `waitForLoadState(NETWORKIDLE)` directly. NETWORKIDLE bills a fixed 500ms to every
  navigation; `PageReady` spends it only on the two route families that actually finish after
  the load event.
- Never add a new legacy alias. Never raise a migration budget. Budgets only ratchet down.
- When a template moves onto a shared fragment, delete the markup it replaced in the same
  commit. Leaving both is what produces a hybrid page.
- Screenshots go to `target/ui-redesign/<stage-slug>/`; they are human-review evidence, not
  assertions. Automated tests assert behavior, computed styles, and geometry.
- When a test name in this plan does not match the repository's actual class, run the whole
  package (`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.<area>.*' test`) rather than guessing.

## File structure

This part modifies feature templates and their stylesheets; it creates no new shared
fragments. Every template it names already exists in the repository.

**Modified:** `templates/campaigns/*`, `adventure/*`, `quest/*`, `world/*`, `notes/*`,
`calendar/*`, `encounter/*`, `party/*`, `sheet/*`, `treasury/*`, `ledger/*`, `maps/list.html`,
`maps/_card.html`, `handout/*`, `audio/*`, `library/*`, `rollable-table/*`, `threat/*`;
`static/css/surfaces.css`, `book.css`, `components.css`, `encounter.css`; and, for view-model
additions only, `campaign/web/CampaignController.java` and `library/web/LibraryController.java`.

**Created (tests) — six classes for eighteen tasks:**

| Class | Stage | Holds |
|---|---|---|
| `campaign/web/NarrativeSurfaceContractTest` | 3 | Campaign Home section order, single start action, quest graph-identifier leak, note Shield tone |
| `encounter/web/OperationalSurfaceContractTest` | 4 | Setup's two columns, setup-vs-live mode marking, handout Shield tone |
| `library/web/ReferenceSurfaceContractTest` | 5 | Library category completeness, no ambiguous creation copy, traps/hazards destinations |
| `gate/NarrativePreparationRenderGateTest` | 3 | Start action above the fold, prose measure, review captures |
| `gate/OperationalPreparationRenderGateTest` | 4 | Minimum control width at 1280x720, active-combatant signal count, review captures |
| `gate/ReferenceWorkspaceRenderGateTest` | 5 | Every category renders, metadata rows align geometrically, review captures |

Task 29 additionally extends the existing `CombatLegibilityContractTest`; it asserts over
parsed CSS rules, not template source, which is why it survives.

## Public interfaces consumed

Everything Parts 1 and 2 published. In particular:

```html
~{fragments/_shell :: page(pageTitle=…, archetype=…, surface=…, header=~{::#page-header},
                           content=~{::#page-content}, rail=~{::#page-rail})}
~{fragments/_page-header :: page-header(title, summary, breadcrumb, primary, secondary)}
~{fragments/_toolbar :: toolbar(action, searchValue, searchPlaceholder, filters, actions)}
~{fragments/_toolbar :: table-toolbar(selectionLabel, actions)}
~{fragments/_badge :: badge(tone, icon, label)}
~{fragments/_banner :: banner(tone, title, body, actions)}
~{fragments/_states :: empty(icon, title, description, cta)}
~{fragments/_states :: unavailable(title, description, retry)}
~{fragments/_states :: failed(title, description, retry)}
~{fragments/_context-rail :: rail(body)}
~{fragments/_context-rail :: rail-section(title, body)}
~{fragments/_overlay :: dialog(id, title, body, actions)}
~{fragments/_overlay :: side-sheet(id, title, body)}
~{common/_icon :: icon(name)}   ~{common/_icon :: icon-sized(name, size)}
```

Slot wrappers carry the archetype grid's classes: `<div id="page-header"
class="page-header-slot">`, `<div id="page-content" class="page-content">`, and — Detail
pages only — `<div id="page-rail" class="page-rail-slot">`. Never put `class="page-header"`
or `class="page-rail"` on a wrapper; `SharedComponentContractTest` rejects those in feature
templates because they belong to the shared fragments nested inside.

Utility classes: `.u-num` (tabular figures), `.prose` (narrative measure), `.data-table`,
`.ref-card` (introduced by Task 35 and reused by Task 36).

Test helpers: `TemplateRules`, `CssRules`, `ColorContrast`, `ReleaseRehearsalFixture`,
`BrowserFailureCollector`.

---

# Stage 3: Campaign and narrative preparation

**Working product after this stage:** campaign selection, Campaign Home, adventures, scenes,
quests, world records, notes, and calendar are restructured onto the archetypes with real
hierarchy — not just repainted.

Every task in this stage shares one shape: extend the feature's existing controller/template
test with the new structural assertions, run it red, restructure the templates, run it green,
commit. Feature behavior, routes, and htmx targets do not change.

## Task 20: Campaign selection

**Files:**
- Modify: `src/main/resources/templates/campaigns/list.html`, `campaigns/_card.html`,
  `campaigns/_new-button.html`, `campaigns/_import-dialog.html`, `campaigns/new.html`
- Modify: `src/main/resources/static/css/surfaces.css`

**Interfaces:**
- Consumes: `page-header`, `badge`, `states :: empty`, `overlay :: dialog`.

- [ ] **Step 1: Restructure the templates**

- `list.html`: index archetype; page header `Campaigns` with `New campaign` as the single
  primary and `Import campaign` as a neutral secondary that opens the import dialog through
  `window.dmOverlay.open`.
- `_card.html`: keep `book-cover` and the deterministic sigil; place them on
  `var(--surface-workspace)`; show campaign name, party size, and last activity; render
  readiness with `~{fragments/_badge :: badge(tone=…, icon=…, label=…)}` where tone is
  `success` when ready, `warning` when unresolved, `neutral` when unstarted.
- Remove uppercase transforms from the campaign name; let it wrap naturally at two lines.
- `_import-dialog.html`: move onto `~{fragments/_overlay :: dialog}`.
- Empty state: `~{fragments/_states :: empty(icon='castle', title='No campaigns yet', description='Create one from scratch, or import a campaign package.', cta=~{::#createPaths})}`.

- [ ] **Step 2: Run it green and run the existing campaign tests**

```bash
./mvnw -Dtest='CampaignControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/campaigns src/main/resources/static/css/surfaces.css
git commit -m "feat: restructure campaign selection onto the index archetype"
```

## Task 21: Campaign Home hierarchy

**Files:**
- Modify: `src/main/resources/templates/campaigns/detail.html`, `campaigns/_readiness.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web/CampaignController.java`
  (view-model additions only)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/NarrativeSurfaceContractTest.java`

**Interfaces:**
- Produces: `NarrativeSurfaceContractTest` — the one static contract class for Stage 3.
  Campaign Home is otherwise the reference standard other pages are reviewed against by eye.

Spec section 11.2 fixes the order: current scene and active encounter, one Start/Resume
action, compact readiness, party condition and resources, preparation counts with meaningful
warnings, session plan and recent notes, then secondary settings and package actions.

- [ ] **Step 1: Write the Stage 3 contract**

This is the **only** new static contract class in Stage 3. It holds the three assertions that
are genuinely structural — an ordering, a cardinality, and a safety-semantics leak — and
nothing that a screenshot answers better. Everything else in this stage is verified by the
feature's existing controller tests staying green, the Stage 3 render gate (Task 26), and
human review of `target/ui-redesign/narrative/`.

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** The structural invariants of Stage 3. Visual hierarchy is reviewed, not asserted. */
class NarrativeSurfaceContractTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    private static Document parse(String template) throws Exception {
        return Jsoup.parse(read(template));
    }

    /** Spec 11.2 fixes this order; reading order is not something a screenshot proves. */
    @Test
    void campaignHomeSectionsAppearInTheApprovedOrder() throws Exception {
        List<String> sections = parse("campaigns/detail.html").select("[data-home-section]")
                .stream()
                .map(section -> section.attr("data-home-section"))
                .toList();

        assertThat(sections)
                .as("spec 11.2 fixes the reading order of the campaign home")
                .containsExactly("current", "start", "readiness", "party",
                        "preparation", "plan", "admin");
    }

    /** Spec 11.2: the repeated Run Session controls are the specific defect being fixed. */
    @Test
    void campaignHomeOffersExactlyOneStartOrResumeAction() throws Exception {
        assertThat(parse("campaigns/detail.html").select("[data-action=start-session]"))
                .as("the duplicated Run Session control is the defect spec 11.2 fixes")
                .hasSize(1);
    }

    /** Spec 11.7: graph mechanics are an implementation detail, never DM-facing copy. */
    @Test
    void questObjectivesDoNotLeakGraphIdentifiers() throws Exception {
        assertThat(read("quest/_objective-list.html"))
                .doesNotContain("edgeType")
                .doesNotContain("nodeId");
    }

    /** Player-safe state is a safety semantic; it must not drift per feature. */
    @Test
    void playerSafeNotesUseTheStableShieldTone() throws Exception {
        assertThat(read("notes/_card.html")).contains("tone='shield'");
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='NarrativeSurfaceContractTest' test
```

- [ ] **Step 3: Restructure `campaigns/detail.html`**

Use `archetype='operational'`. Emit seven sections in order, each marked with its
`data-home-section` value:

1. `current` — current scene title and active encounter as a raised panel with
   `~{fragments/_badge :: badge}` for state; if neither exists, a single line saying so with a
   link to Adventures.
2. `start` — one `btn-primary` marked `data-action="start-session"`, labelled `Start session`
   or `Resume session` from the existing session state.
3. `readiness` — compact summary reusing `_readiness.html`, rendered as a row of labelled
   badges rather than a grid of tiles.
4. `party` — condition and resource summary from the existing party view model.
5. `preparation` — render a count only when it is non-zero or when its absence is itself a
   warning, and raise `~{fragments/_banner :: banner(tone='warning', …)}` for unresolved
   preparation. A grid of zero-valued tiles is the defect being removed.
6. `plan` — session plan and recent notes.
7. `admin` — settings, export, import, and package actions behind a neutral secondary group
   or overflow popover; destructive campaign actions in a labelled danger region.

Delete every duplicated Run Session control on this page.

- [ ] **Step 4: Add only the view-model fields the sections need**

If a section cannot be rendered from the current model, add a read-only record to
`CampaignController`'s model. Do not touch services, entities, or package adapters.

- [ ] **Step 5: Run it green**

```bash
./mvnw -Dtest='NarrativeSurfaceContractTest,CampaignControllerTest,CampaignReadinessControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/campaigns src/main/java/dev/hendrikhoemberg/dmhelper/campaign/web src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/NarrativeSurfaceContractTest.java
git commit -m "feat: give Campaign Home the approved operational hierarchy"
```

## Task 22: Adventure index and detail

**Files:**
- Modify: `src/main/resources/templates/adventure/list.html`, `_adventure-list.html`,
  `detail.html`, `_chapter-list.html`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

- `list.html` → index archetype, toolbar with search, compact cards showing chapter/scene
  progress and a current-position badge.
- `detail.html` → detail archetype; primary column is a scannable chapter outline whose scene
  rows align name, status badge, map, and encounter; contextual rail carries adventure status,
  current position, and the Edit action.
- Structure editing stays on `scene-structure.html`, reachable from the rail's Edit action.

```bash
./mvnw -Dtest='AdventureControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/adventure
git commit -m "feat: restructure the adventure index and detail"
```

## Task 23: Scene detail as a narrative surface

**Files:**
- Modify: `src/main/resources/templates/adventure/scene-detail.html`, `_scene-body.html`,
  `_scene-rail.html`, `_scene-sections.html`
- Modify: `src/main/resources/static/css/book.css`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

- Primary column: scene narrative in `.prose` bounded to `var(--measure-prose)`, read-aloud
  blocks in `.read-aloud` with the parchment texture and Alegreya.
- Rail (`_scene-rail.html`): `rail-section` per Status, Map, Encounter, Handouts, References,
  plus the Edit action. Current-scene state and transitions render as badges in the Status
  section, not as a banner over the narrative.
- Editorial metadata (participants, checks, links editing) moves behind Edit.

```bash
./mvnw -Dtest='SceneControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/adventure src/main/resources/static/css/book.css
git commit -m "feat: make scene detail a narrative-first surface"
```

## Task 24: Quests

**Files:**
- Modify: `src/main/resources/templates/quest/list.html`, `detail.html`,
  `_objective-list.html`, `_dependency-list.html`, `_link-list.html`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

- `list.html` → index archetype, `toolbar` with search plus a status filter group, dense rows
  with aligned status chip and objective progress.
- `detail.html` → detail archetype; objectives and narrative in the primary column;
  dependencies, links, and annotations in `rail-section`s.
- `_objective-list.html` → each objective renders its prerequisite as a sentence
  (`data-objective-prerequisite`) and its completion mode as a labelled badge
  (`data-objective-completion-mode`). Never print raw graph identifiers.

```bash
./mvnw -Dtest='QuestControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/quest
git commit -m "feat: restructure quests onto the records pattern"
```

## Task 25: World records and notes

**Files:**
- Modify: `src/main/resources/templates/world/*.html`, `world/_relationship-row.html`,
  `world/_clock-row.html`
- Modify: `src/main/resources/templates/notes/list.html`, `detail.html`, `_card.html`,
  `_quicknotes-strip.html`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

Restructure all nine world templates and the four note templates onto the Records pattern:
index archetype + toolbar + dense rows + empty state; detail archetype + context rail;
relationships grouped by role and direction; faction clocks as accessible progress bars;
note player-safe state as a Shield badge.

```bash
./mvnw -Dtest='NpcControllerTest,LocationControllerTest,FactionControllerTest,NoteControllerTest,FullPageRenderSmokeTest' test
```

Use the actual controller test names in `src/test/java/dev/hendrikhoemberg/dmhelper/world/web`
and `.../notes/web`; run the whole package if unsure:
`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.world.web.*,dev.hendrikhoemberg.dmhelper.notes.web.*' test`.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/world src/main/resources/templates/notes src/test/java/dev/hendrikhoemberg/dmhelper/world src/test/java/dev/hendrikhoemberg/dmhelper/notes
git commit -m "feat: restructure world records and notes onto the records pattern"
```

## Task 26: Calendar and the Stage 3 gate

**Files:**
- Modify: `src/main/resources/templates/calendar/overview.html`, `_current-date.html`,
  `_timeline-list.html`, `_event-card.html`, `_config-form.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/NarrativePreparationRenderGateTest.java`

- [ ] **Step 1: Restructure the calendar and run it green**

`overview.html` → operational archetype with three marked regions: `current` (prominent
date controls and advance action), `upcoming` (events table), `history` (timeline). Calendar
configuration moves into a side sheet opened from a neutral secondary action.

```bash
./mvnw -Dtest='CalendarControllerTest,CalendarFormatDateTest' test
```

- [ ] **Step 2: Write the Stage 3 render gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NarrativePreparationRenderGateTest {

    private static final Path SHOTS = Path.of("target/ui-redesign/narrative");

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
        Files.createDirectories(SHOTS);
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void newContext() {
        failures = new BrowserFailureCollector();
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closeContext() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private void open(String path) {
        page.navigate("http://localhost:" + port + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    @Test
    void campaignHomeShowsCurrentStateAboveTheFold() {
        open("/campaigns/" + seeded.campaignId());
        double y = ((Number) page.evaluate(
                "() => document.querySelector('[data-home-section=\"start\"]')"
                        + ".getBoundingClientRect().bottom")).doubleValue();
        assertThat(y).as("Start/Resume must be visible without scrolling").isLessThan(720);
    }

    @Test
    void sceneNarrativeStaysWithinAReadableMeasure() {
        open("/campaigns/" + seeded.campaignId() + "/adventures/" + seeded.adventureId()
                + "/scenes/" + seeded.hostileSceneId());
        double width = ((Number) page.evaluate(
                "() => document.querySelector('.prose').getBoundingClientRect().width"))
                .doubleValue();
        double fontSize = ((Number) page.evaluate(
                "() => parseFloat(getComputedStyle(document.querySelector('.prose')).fontSize)"))
                .doubleValue();
        assertThat(width / fontSize).as("characters per line ≈ width / font-size / 0.5")
                .isBetween(60.0 * 0.5, 90.0 * 0.5);
    }

    @Test
    void captureTheNarrativeReviewSet() {
        String c = "/campaigns/" + seeded.campaignId();
        List<String[]> shots = List.of(
                new String[]{"campaigns", "/campaigns"},
                new String[]{"campaign-home", c},
                new String[]{"adventures", c + "/adventures"},
                new String[]{"adventure-detail", c + "/adventures/" + seeded.adventureId()},
                new String[]{"quests", c + "/quests"},
                new String[]{"npcs", c + "/world/npcs"},
                new String[]{"notes", c + "/notes"},
                new String[]{"calendar", c + "/calendar"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
}
```

`ReleaseRehearsalFixture.Seeded` is the record

```java
public record Seeded(UUID campaignId, UUID adventureId, UUID hostileSceneId,
                     UUID ambushSceneId, UUID branchedEncounterId, UUID branchedMainWaveId,
                     UUID branchedReserveWaveId, List<UUID> branchedReserveCombatantIds,
                     UUID branchSceneId, UUID playableMapId, UUID playerSafeHandoutId,
                     UUID dmSourceHandoutId, UUID questId,
                     List<UUID> partyMemberIds) {}
```

There is no `sceneId()` or `encounterId()` accessor — use `hostileSceneId()` and
`branchedEncounterId()` as above. Do not add new accessors or change the fixture's data.

- [ ] **Step 3: Run the Stage 3 gate**

```bash
./mvnw -P gates -Dtest='NarrativeSurfaceContractTest,NarrativePreparationRenderGateTest' test
./mvnw test
```

- [ ] **Step 4: Review `target/ui-redesign/narrative/` and commit**

```bash
git add -A
git commit -m "feat: complete the campaign and narrative preparation stage"
```


---

# Stage 4: Operational preparation

**Working product after this stage:** encounters, party, character sheets, treasury, ledger,
handouts, and audio are aligned operational surfaces with substantial runtime state.

## Task 27: Encounter index and detail

**Files:**
- Modify: `src/main/resources/templates/encounter/list.html`, `_card.html`, `detail.html`,
  `_prep-summary.html`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

- `list.html` → index archetype with a toolbar carrying search and a `data-filter="status"`
  group; grid of cards (or table when the campaign has more than 12 encounters — reuse the
  existing view-model count, do not add a preference).
- `_card.html` → the five `data-encounter-*` fields, status and readiness as badges, counts
  in `.u-num`.
- `detail.html` → detail archetype; readable preparation summary in the primary column;
  contextual rail with map, difficulty, readiness, and the single
  `data-action="run-encounter"` primary.

```bash
./mvnw -Dtest='EncounterControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/encounter
git commit -m "feat: restructure the encounter index and detail"
```

## Task 28: Encounter setup as a two-column operational workspace

**Files:**
- Modify: `src/main/resources/templates/encounter/setup.html`, `_combatant-list.html`,
  `_library-add.html`, `_threat-add.html`, `_waves.html`, `_placement-board.html`
- Modify: `src/main/resources/static/css/encounter.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/OperationalSurfaceContractTest.java`

- [ ] **Step 1: Write the Stage 4 contract**

This is the **only** new static contract class in Stage 4 (Task 29 additionally extends the
existing `CombatLegibilityContractTest`). Control widths are *not* asserted here — the Stage 4
render gate measures the narrowest rendered control at 1280x720, which is the thing that
actually matters and cannot be faked by a CSS rule existing somewhere.

```java
package dev.hendrikhoemberg.dmhelper.encounter.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** The structural invariants of Stage 4. Density and alignment are reviewed, not asserted. */
class OperationalSurfaceContractTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    /** The two columns are load-bearing: encounter.css keys its grid off them. */
    @Test
    void setupDeclaresTheTwoOperationalColumns() throws Exception {
        assertThat(read("encounter/setup.html"))
                .contains("data-setup-column=\"sources\"")
                .contains("data-setup-column=\"settings\"");
    }

    /** Spec 11.4: setup and live combat must not be mistaken for each other mid-session. */
    @Test
    void setupAndLiveCombatDeclareDistinctModes() throws Exception {
        assertThat(read("encounter/setup.html")).contains("data-encounter-mode=\"setup\"");
        assertThat(read("encounter/_tracker.html")).contains("data-encounter-mode=\"live\"");
    }

    /** Player-safe state is a safety semantic; it must not drift per feature. */
    @Test
    void playerSafeHandoutsUseTheStableShieldTone() throws Exception {
        assertThat(read("handout/_card.html")).contains("tone='shield'");
    }
}
```

- [ ] **Step 2: Restructure, then keep the existing feature tests green**

- `setup.html` → operational archetype; left column `data-setup-column="sources"` holds the
  combatant list and the library/threat/party add sources; right column
  `data-setup-column="settings"` holds encounter settings, waves, and placement.
- Give `.setup-qty` a minimum of `4.5rem`, `.setup-wave` `7rem`, `.setup-search` `16rem`, and
  `.setup-group` `12rem` in `encounter.css`, each with a visible label.
- Mark the mode on both surfaces so the CSS and later gates can tell setup from live combat;
  give the live tracker a distinctly darker canvas and heavier row treatment.

```bash
./mvnw -Dtest='OperationalSurfaceContractTest,EncounterSetupControllerTest,FullPageRenderSmokeTest' test
```

Run the whole encounter web package if the setup controller test has a different name:
`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.encounter.web.*' test`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/encounter src/main/resources/static/css/encounter.css src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/OperationalSurfaceContractTest.java
git commit -m "feat: rebuild encounter setup as a two-column operational workspace"
```

## Task 29: Live combat legibility

**Files:**
- Modify: `src/main/resources/templates/encounter/_tracker.html`, `_combatant-list.html`
- Modify: `src/main/resources/static/css/encounter.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/CombatLegibilityContractTest.java`

- [ ] **Step 1: Extend the existing combat legibility contract**

```java
    @Test
    void theActiveCombatantGetsSubstantialRowTreatment() {
        var active = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> rule.selector().contains(".combatant-row--active"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(".combatant-row--active is not styled"));
        long signals = java.util.stream.Stream
                .of(active.value("background"), active.value("border-left"),
                        active.value("box-shadow"), active.value("outline"))
                .filter(java.util.Objects::nonNull)
                .count();
        assertThat(signals)
                .as("active turn needs more than a thin colored border (spec 12.5)")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void everyRuntimeStateCombinesColorWithSomethingElse() {
        String tracker = CssRules.allTemplateMarkup();
        for (String state : java.util.List.of("defeated", "concentrating", "unresolved-initiative")) {
            assertThat(tracker)
                    .as("state %s needs an icon or explicit label, not color alone", state)
                    .contains("data-combatant-state=\"" + state + "\"");
        }
    }

    @Test
    void combatNumbersUseTabularFigures() {
        var rules = CssRules.of(CssRules.ALL_FILES);
        assertThat(rules).anySatisfy(rule -> {
            assertThat(rule.selector()).containsAnyOf(".combatant-hp", ".combatant-row");
            assertThat(rule.value("font-variant-numeric")).contains("tabular-nums");
        });
    }
```

- [ ] **Step 2: Run red, implement, run green**

- Add `data-combatant-state` to each row with `defeated`, `concentrating`, or
  `unresolved-initiative`, each rendering an icon and a text label alongside its color.
- Style `.combatant-row--active` with a raised background, a 3px `--selection-accent` left
  edge, and a subtle `--glow-primary` — three signals, not one.
- Apply `font-variant-numeric: tabular-nums` to HP, AC, and initiative cells.

```bash
./mvnw -Dtest='CombatLegibilityContractTest,EncounterCombatControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/encounter src/main/resources/static/css/encounter.css src/test/java/dev/hendrikhoemberg/dmhelper/config/CombatLegibilityContractTest.java
git commit -m "feat: give live combat substantial runtime state treatment"
```

## Task 30: Party roster

**Files:**
- Modify: `src/main/resources/templates/party/list.html`, `_roster.html`, `_summary-bar.html`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

- `list.html` → operational archetype; `table-toolbar` for selection and bulk actions;
  `Character sheets` as a neutral secondary action in the page header pointing at
  `/campaigns/{id}/sheets`.
- `_roster.html` → one `.data-table` with the seven aligned columns; HP and AC in tabular
  figures; conditions as badges with icon and label.

```bash
./mvnw -Dtest='PartyControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/party
git commit -m "feat: rebuild the party roster as an aligned operational table"
```

## Task 31: Character sheets

**Files:**
- Modify: `src/main/resources/templates/sheet/detail.html`, `overview.html`,
  `_derived-stats.html`, `_live-state.html`, `_rest-preview.html`, `_level-up-dialog.html`,
  `_inventory.html`, `_resources.html`, `_spell-list.html`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

- `detail.html` → operational archetype; a sticky `data-sheet-identity` strip carrying name,
  class/level, AC, HP, speed, and proficiency; sections marked `data-sheet-section` and
  separated by headings and rules rather than nested cards.
- Rest, level-up, inventory, spell, and resource surfaces mark
  `data-change-state="pending"` versus `"applied"` and use the warning/success tones
  accordingly.
- `_level-up-dialog.html` moves onto the shared dialog.

```bash
./mvnw -Dtest='SheetControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/sheet src/main/resources/static/css/components.css
git commit -m "feat: rebuild character sheets with real local hierarchy"
```

## Task 32: Treasury and ledger

**Files:**
- Modify: `src/main/resources/templates/treasury/list.html`, `_card.html`,
  `_holder-section.html`, `_attunement-warn.html`, `_form.html`
- Modify: `src/main/resources/templates/ledger/list.html`, `_card.html`, `_balance.html`,
  `_form.html`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

```bash
./mvnw -Dtest='TreasuryControllerTest,LedgerControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/treasury src/main/resources/templates/ledger
git commit -m "feat: rebuild treasury and ledger as transactional tables"
```

## Task 33: Maps, handouts, audio, and the Stage 4 gate

**Files:**
- Modify: `src/main/resources/templates/maps/list.html`, `maps/_card.html`
- Modify: `src/main/resources/templates/handout/list.html`, `_card.html`
- Modify: `src/main/resources/templates/audio/list.html`, `_card.html`, `detail.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/OperationalPreparationRenderGateTest.java`

Spec section 11.8 covers **maps, handouts, and audio** as one asset-catalog family. The maps
index is in scope here; the map *editor* is Part 4's Stage 6.

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

All three index pages become visual asset card grids on the index archetype with concise
operational metadata; present/preview separates from edit/delete; audio adds the four cue
state fields and an `unavailable` state that still offers "Configure provider".

- `maps/list.html` → index archetype, toolbar with search, empty state; `maps/_card.html`
  shows a thumbnail, dimensions, grid calibration as a badge, and which scenes or encounters
  reference the map, with Edit separated from the destructive group.

```bash
./mvnw -Dtest='GameMapControllerTest,HandoutControllerTest,AudioCueControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Write the Stage 4 render gate**

Copy the structure of `NarrativePreparationRenderGateTest` into
`OperationalPreparationRenderGateTest` with `SHOTS = Path.of("target/ui-redesign/operational")`
and these tests:

```java
    @Test
    void encounterSetupControlsAreNotMicroControlsAtTheMinimumViewport() {
        page.setViewportSize(1280, 720);
        open("/campaigns/" + seeded.campaignId() + "/encounters/" + seeded.branchedEncounterId() + "/setup");
        double narrowest = ((Number) page.evaluate("""
                () => Math.min(...[...document.querySelectorAll(
                        '[data-setup-column] input, [data-setup-column] select')]
                        .map(el => el.getBoundingClientRect().width))
                """)).doubleValue();
        assertThat(narrowest).as("narrowest setup control").isGreaterThanOrEqualTo(64.0);
    }

    @Test
    void theActiveCombatantIsDistinguishableByMoreThanColor() {
        open("/campaigns/" + seeded.campaignId() + "/encounters/" + seeded.branchedEncounterId());
        Object signals = page.evaluate("""
                () => {
                  const row = document.querySelector('.combatant-row--active');
                  if (!row) return -1;
                  const s = getComputedStyle(row);
                  let count = 0;
                  if (s.borderLeftWidth !== '0px') count++;
                  if (s.boxShadow !== 'none') count++;
                  if (s.backgroundColor !== getComputedStyle(row.parentElement).backgroundColor) count++;
                  return count;
                }
                """);
        assertThat(((Number) signals).intValue()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void captureTheOperationalReviewSet() {
        String c = "/campaigns/" + seeded.campaignId();
        java.util.List<String[]> shots = java.util.List.of(
                new String[]{"encounters", c + "/encounters"},
                new String[]{"encounter-detail", c + "/encounters/" + seeded.branchedEncounterId()},
                new String[]{"encounter-setup", c + "/encounters/" + seeded.branchedEncounterId() + "/setup"},
                new String[]{"party", c + "/party"},
                new String[]{"sheets", c + "/sheets"},
                new String[]{"treasury", c + "/treasury"},
                new String[]{"ledger", c + "/ledger"},
                new String[]{"maps", c + "/maps"},
                new String[]{"handouts", c + "/handouts"},
                new String[]{"audio", c + "/audio/cues"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
```

- [ ] **Step 3: Run the Stage 4 gate**

```bash
./mvnw -P gates -Dtest='OperationalSurfaceContractTest,CombatLegibilityContractTest,OperationalPreparationRenderGateTest' test
./mvnw test
```

- [ ] **Step 4: Review `target/ui-redesign/operational/` and commit**

```bash
git add -A
git commit -m "feat: complete the operational preparation stage"
```


---

# Stage 5: Reference workspace

**Working product after this stage:** Library is a reference workspace with a stable category
navigator, scan-aligned cards, and detail surfaces; tables, traps, and hazards share the
visual language while keeping their own top-level destinations and renderers.

## Task 34: Library workspace and category navigator

**Files:**
- Modify: `src/main/resources/templates/library/list.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java`
  (page title and active-category model attributes only)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/ReferenceSurfaceContractTest.java`

**Interfaces:**
- Produces: `data-library-category` on each navigator entry; `activeCategory` and
  `activeCategorySingular` model attributes.

- [ ] **Step 1: Write the Stage 5 contract**

This is the **only** new static contract class in Stage 5. Category completeness is worth
asserting because a missing category is invisible until a DM goes looking for it; card
alignment is not, because the Stage 5 render gate measures it geometrically.

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** The structural invariants of Stage 5. Scan quality is reviewed, not asserted. */
class ReferenceSurfaceContractTest {

    static final List<String> CATEGORIES = List.of("Monsters", "Spells", "Conditions", "Rules",
            "Equipment", "Magic Items", "Classes", "Species", "Backgrounds", "Feats");

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    /** A category missing from the navigator is unreachable and invisible until searched for. */
    @Test
    void theCategoryNavigatorIsStableAndComplete() throws Exception {
        String list = read("library/list.html");
        for (String category : CATEGORIES) {
            assertThat(list).as("navigator entry %s", category)
                    .contains("data-library-category=\"" + category + "\"");
        }
    }

    /** Spec 11.9 names the ambiguous "New Homebrew" copy as the defect being removed. */
    @Test
    void theCreationActionNamesTheContentType() throws Exception {
        assertThat(read("library/list.html")).doesNotContain("New Homebrew");
    }

    /** Spec 11.9: traps and hazards keep their own top-level destinations. */
    @Test
    void trapsAndHazardsKeepDistinctTopLevelDestinations() throws Exception {
        String rail = read("fragments/_rail.html");
        assertThat(rail).contains("/library/traps").contains("/library/hazards");
    }
}
```

- [ ] **Step 2: Restructure, then keep the existing feature tests green**

- `list.html` → index archetype. The page header title binds to `activeCategory`; the primary
  action label binds to `'New ' + activeCategorySingular`.
- Add a local category navigator above the toolbar: one horizontal tab strip with
  `data-library-category` per category and `aria-current="page"` on the active one. Selection
  uses a gold indicator plus high-contrast text, never a gold-filled pill.
- Add `activeCategory` and `activeCategorySingular` to the controller model for every category
  route. No service or repository change.

```bash
./mvnw -Dtest='ReferenceSurfaceContractTest,LibraryControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/library src/main/java/dev/hendrikhoemberg/dmhelper/library/web src/test/java/dev/hendrikhoemberg/dmhelper/library/web/ReferenceSurfaceContractTest.java
git commit -m "feat: turn Library into a reference workspace with a category navigator"
```

## Task 35: Library card alignment, clamping, and detail

**Files:**
- Modify: every `src/main/resources/templates/library/_*-card.html`
- Modify: `src/main/resources/templates/library/_scope-badge.html`,
  `library/_provenance-fields.html`
- Modify: `src/main/resources/static/css/components.css`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

- Give every card the same `.ref-card` grid: title row, one `.ref-card__meta` row whose
  slots sit at the same offset in every category (CR, level, school, rarity, category,
  source), a clamped `.ref-card__summary`, and a `.ref-card__provenance` line in
  `--text-tertiary`. The Stage 5 render gate measures that alignment geometrically.
- Clamp with `display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;`.
- The full description opens in the existing detail route, or in a side sheet on the index
  where the category already has one.
- `_scope-badge.html` moves onto the shared badge.

```bash
./mvnw -Dtest='ReferenceSurfaceContractTest,LibraryControllerTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/library src/main/resources/static/css/components.css
git commit -m "feat: align library cards on one scan grid"
```

## Task 36: Tables, traps, and hazards

**Files:**
- Modify: `src/main/resources/templates/rollable-table/list.html`, `detail.html`, `form.html`,
  `_roll-panel.html`
- Modify: `src/main/resources/templates/threat/list.html`, `detail.html`, `form.html`,
  `_mechanics-card.html`, `_provenance.html`

- [ ] **Step 1: Restructure, then keep the existing feature tests green**

```bash
./mvnw -Dtest='RollableTableControllerTest,ThreatControllerTest,FullPageRenderSmokeTest' test
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/rollable-table src/main/resources/templates/threat
git commit -m "feat: bring tables, traps, and hazards into the reference language"
```

## Task 37: Stage 5 gate

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ReferenceWorkspaceRenderGateTest.java`

- [ ] **Step 1: Write the gate**

Copy the structure of `NarrativePreparationRenderGateTest` with
`SHOTS = Path.of("target/ui-redesign/reference")` and these tests:

```java
    @Test
    void everyLibraryCategoryRendersWithItsOwnTitleAndTheSameCardGrid() {
        for (String category : java.util.List.of("", "/spells", "/conditions", "/rules",
                "/equipment", "/magic-items", "/classes", "/species", "/backgrounds", "/feats")) {
            open("/library" + category);
            String title = page.locator("h1").innerText();
            assertThat(title).as("title for %s", category).isNotEqualTo("Library");
            assertThat(page.locator("[data-library-category][aria-current='page']").count())
                    .as("exactly one active category for %s", category).isEqualTo(1);
            assertThat(page.locator(".ref-card").count())
                    .as("cards render for %s", category).isGreaterThan(0);
        }
    }

    @Test
    void referenceCardsAlignTheirMetadataRow() {
        open("/library/spells");
        Object misaligned = page.evaluate("""
                () => {
                  const rows = [...document.querySelectorAll('.ref-card__meta')];
                  if (rows.length < 2) return 0;
                  const tops = rows.map(r => Math.round(
                      r.getBoundingClientRect().top - r.closest('.ref-card').getBoundingClientRect().top));
                  return new Set(tops).size - 1;
                }
                """);
        assertThat(((Number) misaligned).intValue())
                .as("metadata rows must sit at the same offset in every card").isZero();
    }

    @Test
    void captureTheReferenceReviewSet() {
        java.util.List<String[]> shots = java.util.List.of(
                new String[]{"library-monsters", "/library"},
                new String[]{"library-spells", "/library/spells"},
                new String[]{"library-magic-items", "/library/magic-items"},
                new String[]{"tables", "/library/tables"},
                new String[]{"traps", "/library/traps"},
                new String[]{"hazards", "/library/hazards"});
        for (String[] shot : shots) {
            open(shot[1]);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve(shot[0] + ".png")).setFullPage(true));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(shots.size());
    }
```

Adjust the category paths to the routes `LibraryController` actually maps.

- [ ] **Step 2: Run the Stage 5 gate**

```bash
./mvnw -P gates -Dtest='ReferenceSurfaceContractTest,ReferenceWorkspaceRenderGateTest' test
./mvnw test
```

- [ ] **Step 3: Review `target/ui-redesign/reference/` and commit**

```bash
git add -A
git commit -m "feat: complete the reference workspace stage"
```

---

# Appendix A: Spec coverage for this part

| Spec section | Covered by |
|---|---|
| 5 Design principles | Task 21 (Campaign Home is the hierarchy reference standard) |
| 6.4 Semantic state | Task 29 |
| 7.1–7.2 Type roles | Task 23 |
| 11.1 Campaign selection | Task 20 |
| 11.2 Campaign Home | Task 21 |
| 11.3 Adventures and scenes | Tasks 22, 23 |
| 11.4 Encounters | Tasks 27, 28, 29 |
| 11.5 Party and sheets | Tasks 30, 31 |
| 11.6 Treasury and ledger | Task 32 |
| 11.7 Quests, world, notes, calendar | Tasks 24, 25, 26 |
| 11.8 Maps, handouts, audio | Task 33 (the map *editor* is Part 4, Task 38) |
| 11.9 Library and reference | Tasks 34, 35, 36 |
| 18.4 Backend compatibility | Tasks 21, 34 (view-model additions only) |
| 20.2 Browser gates | Tasks 26, 33, 37 |
| 20.4 Functional regression | Tasks 26, 33, 37 |

# Appendix B: Standing rules for every task

- Never widen a gate to make a page pass. Fix the page.
- Never add a `--color-*` token. The vocabulary is frozen at Task 5 and deleted at Task 49.
- Never leave a page half-migrated across a commit boundary within a stage's own scope.
- Never change a route, form field name, htmx target id, persisted field, or package format.
  If a redesign appears to require one, stop and raise it — that is a functional change and
  needs its own spec.
- When a test name in this plan does not match the repository's actual class, run the whole
  package (`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.<area>.*' test`) rather than guessing.

# Handoff

Part 3 is done when all three stage gates and `./mvnw test -P gates` are green and the review sets in
`target/ui-redesign/narrative/`, `operational/`, and `reference/` have been looked at.
Continue with
`docs/superpowers/plans/2026-07-31-ui-redesign-4-editors-cockpit-release.md`.
