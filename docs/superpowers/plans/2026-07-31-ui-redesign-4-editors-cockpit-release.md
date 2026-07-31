# UI Redesign Part 4: Editors, Cockpit, and Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the two viewport-owning workspaces — the map editor and the session cockpit — then close the redesign out: presentation surface, administration and error pages, the form and destructive-action sweep, removal of the legacy compatibility layer, and the whole-product accessibility, viewport, and visual-review release gate.

**Architecture:** The map editor becomes a four-region CSS grid (command bar, tool rail, canvas, contextual inspector) in its own stylesheet, keeping every existing hook so `map-editor.js` and its browser tests keep working. The cockpit keeps its four-zone workbench and custom presets while the four built-in presets are redefined around one unmistakable primary task — a change made in the Java layout model, where it can be tested properly — and modules adapt through container queries. Stage 8 then deletes the `--color-*` bridge, sweeps the remaining forms and overlays, and gates the whole product on measured geometry, keyboard operation, and a captured visual review matrix.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, htmx, Alpine.js, vanilla JavaScript, repository-owned CSS and inline SVG, JUnit 5, AssertJ, jsoup, Playwright for Java, Maven Wrapper.

## Position in the program

This is **Part 4 of 4** of the whole-product UI redesign. The program index is
`docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md`; the design authority is
`docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`.

| Part | Stages | Tasks | Status |
|---|---|---|---|
| 1. Visual foundations | 1 | 1–9 | **must be complete before this part** |
| 2. Shell and shared archetypes | 2 | 10–19 | **must be complete before this part** |
| 3. Feature surfaces | 3–5 | 20–37 | **must be complete before this part** |
| **4. Editors, cockpit, and release (this plan)** | 6–8 | 38–53 | consumes Parts 1–3 |

Task numbers are global across the four parts, so every cross-reference in the program
(`Task 49 removes them`, `Task 30 owns that`) means the same task everywhere.

**Working product after this part:** the redesign is complete. No page uses the former
brown-on-brown system, the compatibility layer is gone, and the accessibility, zoom,
viewport, keyboard, browser-health, and functional regression gates all pass.

Stages 6, 7, and 8 in this part are strictly sequential.

## Scope reconciliation: spec section 11.11

Spec section 11.11 describes a player-facing presentation surface. The shipped product no
longer has one: commits `e6e0a3b8` (remove table-safe screen mode) and `440a6484` (remove
player-surface leftovers) cut it, and the only presentation surface that exists today is
the DM-side full-viewport handout overlay at
`GET /campaigns/{campaignId}/handouts/{id}/present`
(`templates/handout/_present-overlay.html`), plus the `playerSafe` flag on `Handout` and
`Note`.

**Assumption this plan proceeds under:** section 11.11 applies to what exists — the
full-viewport presentation overlay and the player-safe/DM-only Shield semantics that feed
it. Task 47 redesigns that overlay to the spec's "neutral near-black canvas, no DM chrome,
explicit unavailable/error state" requirements and keeps Shield semantics stable. This plan
does **not** re-introduce a separate player projection surface; doing that is a functional
feature, not a redesign, and needs its own spec. If a player projection surface is wanted,
raise it before Stage 8 so the release gate can cover it.

## Global Constraints

Every task's requirements implicitly include this section.

- The design authority is `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`. This program supersedes `docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md` and the deleted `2026-07-31-whole-product-ui-redesign-program.md`; both are absorbed here.
- `docs/ui-polish-spec.md` is superseded, including its brown-palette-reuse and no-responsive-work requirements.
- Desktop and laptop browsers only. Minimum supported viewport **1280x720**; primary range **1440x900**–**1920x1080**; large-screen gate **2560x1440**; zoom gates **125%** and **150%**, applied across the primary range (see Task 51).
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
- Keep every existing controller, template, htmx, package, player-safety, encounter, map, cockpit, and accessibility test green. `./mvnw test` must pass at the end of every stage.

## Shared task protocol

Applies to every task in this plan.

- **Verification model.** The two editors are geometry problems, so they are gated on measured
  geometry: what clips, what scrolls, what share of the viewport the canvas gets, whether a
  preset fits. Static contracts here hold only what geometry cannot see — a region that went
  missing in the move, a tool without an accessible name, a projected surface leaking DM
  chrome, a confirmation that does not name its consequence. Do not add a `*LayoutTest` that
  greps template source for `data-*` attributes; that pattern was deliberately removed from
  this program. Composition quality is signed off from `target/ui-redesign/`.
- Red first when there *is* a test: write or extend it, run it, confirm the failure message
  names the missing thing, then implement.
- One task, one commit. Use `feat:`, `refactor:`, `test:`, or `fix:` prefixes.
- Run the task's focused test command after each red/green cycle; run `./mvnw test` at the
  end of every stage before the stage's review step.
- Never add a new legacy alias. Never raise a migration budget. Budgets only ratchet down.
- When a template moves onto a shared fragment, delete the markup it replaced in the same
  commit. Leaving both is what produces a hybrid page.
- Screenshots go to `target/ui-redesign/<stage-slug>/`; they are human-review evidence, not
  assertions. Automated tests assert behavior, computed styles, and geometry.
- When a test name in this plan does not match the repository's actual class, run the whole
  package (`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.<area>.*' test`) rather than guessing.

## File structure

### Created by this part

| Path | Responsibility | Stage |
|---|---|---|
| `src/main/resources/static/css/map-editor.css` | Isolated map-editor workspace styles | 6 |
| `src/main/resources/static/js/map-inspector.js` | Inspector relevance and layer controls | 6 |

**Test sources — one contract class per stage plus the release gates:**

| Class | Stage | Holds |
|---|---|---|
| `gamemap/web/MapEditorLayoutContractTest` | 6 | Four named regions, tool a11y and shortcuts, six inspector sections |
| `session/web/CockpitSurfaceContractTest` | 7 | Layout chrome hidden while locked, module-role vocabulary, no map dependency in the tracker, runtime-state style roles |
| `handout/web/PresentationSurfaceTest` | 8 | No DM chrome reaches a projected surface, explicit presentation states, Shield parity |
| `campaign/web/AdministrationContractTest` | 8 | Import-preview ranking, destructive confirmations name their consequence |
| `config/FormContractTest` | 8 | Every control labelled, field errors announced, confirmations name the entity |
| `config/LegacyVisualAliasRemovalTest` | 8 | The `--color-*` bridge is gone and unreferenced |
| `config/RedesignCoverageContractTest` | 8 | Archetype coverage, no brown survivors, no decorative gold, one shell |
| `gate/MapEditorRenderGateTest` | 6 | Nothing clips at three viewports, canvas share, captures |
| `gate/CockpitLaptopFitGateTest` | 7 | Every preset fits, modules own their scrolling, tabs when constrained, captures |
| `gate/ViewportMatrixGateTest` | 8 | Viewport sweep, zoom gates, reduced motion, minimum runtime type size |
| `gate/KeyboardOperationGateTest` | 8 | Visible focus, accessible names, landmark and heading order |
| `gate/VisualReviewMatrixGateTest` | 8 | The full populated/empty/overlay capture set |

Task 43 additionally extends the existing `CockpitBuiltInPresetCatalogTest`; those are real
assertions over the Java layout model, not template source, and they carry the whole preset
redesign.

### Deleted by this part

| Path | Replaced by | Stage |
|---|---|---|
| `src/main/resources/templates/common/_empty-state.html` | `fragments/_states.html` | 8 |
| `src/main/resources/templates/common/_skeleton.html` | `fragments/_states.html` | 8 |
| `src/main/resources/templates/common/_error.html` | `fragments/_states.html` | 8 |
| `src/test/java/.../config/LegacyVisualAliasContractTest.java` | `LegacyVisualAliasRemovalTest` | 8 |
| the `--color-*` alias block in `tokens.css` | the semantic roles from Part 1 | 8 |
| `docs/ui-polish-spec.md` | the approved design spec | 8 |
| `docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md` | this program | 8 |

## Public interfaces

### Consumed from Parts 1–3

Every semantic role token and layout role token, every shared fragment
(`_shell`, `_page-header`, `_toolbar`, `_badge`, `_banner`, `_states`, `_context-rail`,
`_overlay`, `_status`, `common/_icon`), the archetype classes, the utility classes
`.u-num` / `.prose` / `.data-table` / `.ref-card`, `window.dmOverlay`, `window.dmToast`,
and the test helpers `CssRules`, `ColorContrast`, `TemplateRules`, `ReleaseRehearsalFixture`,
`BrowserFailureCollector`.

`PageArchetypeContractTest.ARCHETYPES` must be package-visible for Task 53's coverage test.

### Consumed from the existing repository — cockpit layout model

Verified against `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/`:

```java
enum   CockpitZone { PRIMARY, LEFT_SUPPORT, RIGHT_SUPPORT, BOTTOM_UTILITY }
record CockpitLayoutDocument.SplitRatios(double left, double primary, double right, double bottom)
record CockpitLayoutDocument.ZoneLayout(List<String> moduleKeys, String activeModuleKey, boolean collapsed)
record CockpitBuiltInPresetCatalog.BuiltInPreset(String key, String name, CockpitLayoutDocument layout)
       CockpitBuiltInPresetCatalog.all() / require(String key)
       CockpitLayoutDocument.compactModuleKeys()
```

`CockpitLayoutValidator` enforces `|left + primary + right − 1.0| <= 0.001`,
`0.50 <= primary <= 0.65`, and `0.16 <= bottom <= 0.40`. Every ratio set proposed in Task 43
satisfies these; do not relax the validator.

### Consumed from the existing repository — the browser fixture

```java
public record Seeded(UUID campaignId, UUID adventureId, UUID hostileSceneId,
                     UUID ambushSceneId, UUID branchedEncounterId, UUID branchedMainWaveId,
                     UUID branchedReserveWaveId, List<UUID> branchedReserveCombatantIds,
                     UUID branchSceneId, UUID playableMapId, UUID playerSafeHandoutId,
                     UUID dmSourceHandoutId, UUID questId,
                     List<UUID> partyMemberIds) {}
```

There is no `sceneId()`, `encounterId()`, or `emptyCampaignId()` accessor. Use
`hostileSceneId()`, `branchedEncounterId()`, and `playableMapId()`. Task 52 needs a second,
deliberately empty campaign; add an `emptyCampaignId()` that creates a campaign with no
records, without altering any existing seeded data.

### Produced by this part

```js
window.mapInspector.setContext(key) // 'tool' | 'selection' | 'layers' | 'map'
```

CSS regions `.mapedit`, `.mapedit__commandbar`, `.mapedit__rail`, `.mapedit__canvas`,
`.mapedit__inspector`, `.mapedit__status`; attributes `data-tool`,
`data-inspector-section`, `data-command-slot`, `data-module-role`, `data-runtime-state`,
`data-runtime-action`.

---

# Stage 6: Map editor

**Working product after this stage:** the map editor is a viewport-owned workspace with a top
command bar, a compact tool rail, a large canvas, and a contextual inspector. Every existing
layer, token, pin, calibration, import, crop, rotation, undo/redo, keyboard, and export
behavior is preserved, as is the 60fps performance target.

## Task 38: Split the editor into command bar, tool rail, canvas, and inspector

**Files:**
- Create: `src/main/resources/static/css/map-editor.css`
- Modify: `src/main/resources/templates/maps/editor.html`
- Modify: `src/main/resources/templates/fragments/head.html` (load the new sheet on the
  editor page only, via a `th:if` on an `editorPage` model flag)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorLayoutContractTest.java`

**Interfaces:**
- Produces: `.mapedit`, `.mapedit__commandbar`, `.mapedit__rail`, `.mapedit__canvas`,
  `.mapedit__inspector`, `.mapedit__status`; `data-tool`, `data-inspector-section`.

- [ ] **Step 1: Write the failing layout contract**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 13, structural only. Region *layout* — canvas share, nothing clipped, no
 * document scroll — is measured by MapEditorRenderGateTest at three viewports, which is a
 * stronger check than grepping map-editor.css for a grid declaration.
 */
class MapEditorLayoutContractTest {

    private static final Path EDITOR = Path.of("src/main/resources/templates/maps/editor.html");

    private static String markup() throws Exception {
        return Files.readString(EDITOR);
    }

    @Test
    void theEditorHasFourNamedRegions() throws Exception {
        String editor = markup();
        for (String region : List.of("mapedit__commandbar", "mapedit__rail",
                "mapedit__canvas", "mapedit__inspector")) {
            assertThat(editor).as(region).contains(region);
        }
    }

    @Test
    void everyToolLivesInTheToolRailWithALabelAndShortcut() throws Exception {
        var document = Jsoup.parse(markup(), "", Parser.xmlParser());
        var rail = document.selectFirst(".mapedit__rail");
        assertThat(rail).isNotNull();
        var tools = rail.select("[data-tool]");
        assertThat(tools.size()).as("tool count").isGreaterThanOrEqualTo(11);
        for (var tool : tools) {
            assertThat(tool.hasAttr("aria-label")).as("label on %s", tool.attr("data-tool")).isTrue();
            assertThat(tool.hasAttr("aria-pressed")).as("selected state on %s", tool.attr("data-tool")).isTrue();
            assertThat(tool.hasAttr("data-shortcut")).as("shortcut hint on %s", tool.attr("data-tool")).isTrue();
        }
    }

    @Test
    void theInspectorOwnsEverySixSection() throws Exception {
        String editor = markup();
        for (String section : List.of("tool", "palette", "selection", "layers", "map", "pins")) {
            assertThat(editor).as("inspector section %s", section)
                    .contains("data-inspector-section=\"" + section + "\"");
        }
    }
}
```

Three assertions, each covering something a screenshot cannot: a region that silently went
missing, a tool without an accessible name or selected state, an inspector section that was
dropped during the move. The command bar's contents are reviewed in
`target/ui-redesign/map-editor/`, not enumerated here.

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='MapEditorLayoutContractTest' test
```

- [ ] **Step 3: Restructure `editor.html`**

Replace `editor-container` / `editor-toolbar` / `editor-body` / `editor-sidebar` /
`editor-statusbar` with:

```html
<div id="page-content" class="mapedit">
    <div class="mapedit__commandbar">
        <a class="btn btn-ghost" data-command="back"
           th:href="@{/campaigns/{id}/maps(id=${campaignId})}">
            <th:block th:replace="~{common/_icon :: icon(name='chevron-right')}"></th:block>
            Back to Maps</a>
        <span class="mapedit__name" data-command="map-name" th:text="${map.name}">Map</span>
        <button type="button" class="icon-btn" data-command="undo" aria-label="Undo"
                title="Undo (Ctrl+Z)">
            <th:block th:replace="~{common/_icon :: icon(name='undo')}"></th:block></button>
        <button type="button" class="icon-btn" data-command="redo" aria-label="Redo"
                title="Redo (Ctrl+Shift+Z)">
            <th:block th:replace="~{common/_icon :: icon(name='redo')}"></th:block></button>
        <span class="mapedit__spacer"></span>
        <th:block th:replace="~{fragments/_status :: save-status(id='mapSaveStatus')}"></th:block>
        <button type="button" class="btn" data-command="import">Import</button>
        <button type="button" class="btn" data-command="export">Export</button>
        <button type="button" class="icon-btn" data-command="help" aria-label="Keyboard help">
            <th:block th:replace="~{common/_icon :: icon(name='help')}"></th:block></button>
    </div>

    <nav class="mapedit__rail" aria-label="Tools">
        <!-- one button per tool; group with <div class="mapedit__toolgroup"> -->
        <button type="button" class="tool-btn" data-tool="brush" data-shortcut="B"
                aria-label="Brush" aria-pressed="false" title="Brush (B)">
            <th:block th:replace="~{common/_icon :: icon(name='brush')}"></th:block></button>
        <!-- fill, select, rect, circle, line, polygon, room, door, corridor, region -->
    </nav>

    <div class="mapedit__canvas" id="editorCanvasWrap"><!-- existing canvas markup --></div>

    <aside class="mapedit__inspector" aria-label="Inspector">
        <section data-inspector-section="tool"><!-- active tool options --></section>
        <section data-inspector-section="palette"><!-- terrain/color palette --></section>
        <section data-inspector-section="selection"><!-- selected object properties --></section>
        <section data-inspector-section="layers"><!-- visibility, lock, ordering --></section>
        <section data-inspector-section="map"><!-- size, grid, background --></section>
        <section data-inspector-section="pins"><!-- threat pins --></section>
    </aside>

    <div class="mapedit__status"><!-- existing status readout --></div>
</div>
```

Move every existing control into exactly one region. Nothing is deleted; the terrain panel,
resize dialog, background section, shape properties, and threat-pin list all become inspector
sections. Keep every `id`, `x-data`, `data-map-control`, and `data-image-control` hook so
`map-editor.js` and its tests keep working.

- [ ] **Step 4: Write `map-editor.css`**

```css
.mapedit {
  display: grid;
  grid-template-columns: auto 1fr auto;
  grid-template-rows: auto 1fr auto;
  grid-template-areas:
    "commandbar commandbar commandbar"
    "rail canvas inspector"
    "status status status";
  height: 100%;
  min-height: 0;
  background: var(--surface-canvas);
}
.mapedit__commandbar {
  grid-area: commandbar;
  display: flex; align-items: center; gap: var(--space-xs);
  padding: var(--space-2xs) var(--space-sm);
  background: var(--surface-navigation);
  border-bottom: 1px solid var(--border-subtle);
}
.mapedit__spacer { flex: 1; }
.mapedit__name { font-weight: 600; color: var(--text-primary); }
.mapedit__rail {
  grid-area: rail;
  width: 56px;
  display: flex; flex-direction: column; gap: var(--space-2xs);
  padding: var(--space-2xs);
  background: var(--surface-navigation);
  border-right: 1px solid var(--border-subtle);
  overflow-y: auto;
}
.mapedit__toolgroup { display: contents; }
.mapedit__toolgroup + .mapedit__toolgroup::before {
  content: ""; height: 1px; background: var(--border-subtle); margin-block: var(--space-2xs);
}
.tool-btn {
  display: grid; place-items: center; width: 40px; height: 40px;
  background: transparent; border: 1px solid transparent; border-radius: var(--radius);
  color: var(--text-secondary); cursor: pointer;
}
.tool-btn:hover { color: var(--text-primary); background: var(--neutral-hover-surface); }
.tool-btn[aria-pressed="true"] {
  color: var(--text-primary);
  background: var(--selection-surface);
  border-color: var(--selection-accent);
}
.mapedit__canvas { grid-area: canvas; min-width: 0; min-height: 0; overflow: hidden; position: relative; }
.mapedit__inspector {
  grid-area: inspector;
  width: 320px; min-width: 0;
  padding: var(--space-sm);
  background: var(--surface-panel);
  border-left: 1px solid var(--border-subtle);
  overflow-y: auto;
  display: flex; flex-direction: column; gap: var(--space-sm);
}
.mapedit__status {
  grid-area: status;
  display: flex; align-items: center; gap: var(--space-sm);
  padding: var(--space-2xs) var(--space-sm);
  background: var(--surface-navigation);
  border-top: 1px solid var(--border-subtle);
  color: var(--text-tertiary); font-size: var(--text-sm);
}
/* At the minimum viewport the inspector narrows before the canvas does. */
@media (max-width: 1439px) {
  .mapedit__inspector { width: 280px; }
}
```

- [ ] **Step 5: Run it and watch it pass**

```bash
./mvnw -Dtest='MapEditorLayoutContractTest,GameMapControllerTest,MapEditorBrowserTest' test
```

`MapEditorBrowserTest` is the existing behavior gate. If it fails, a control was moved
without its hook — restore the hook, do not change the test.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/maps/editor.html src/main/resources/static/css/map-editor.css src/main/resources/templates/fragments/head.html src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorLayoutContractTest.java
git commit -m "feat: split the map editor into command bar, tool rail, canvas, and inspector"
```

## Task 39: Make the inspector contextual

**Files:**
- Create: `src/main/resources/static/js/map-inspector.js`
- Modify: `src/main/resources/templates/maps/editor.html`
- Modify: `src/main/resources/static/css/map-editor.css`

**Interfaces:**
- Produces: `window.mapInspector.setContext(key)` where key is `tool`, `selection`, `layers`,
  or `map`.

- [ ] **Step 1: Write the failing browser assertion**

Add to `MapEditorBrowserTest`:

```java
    @Test
    void onlySectionsRelevantToTheActiveToolArePromoted() {
        openEditor();
        page.click("[data-tool='brush']");
        assertThat(page.getAttribute("[data-inspector-section='palette']", "data-relevance"))
                .isEqualTo("primary");
        assertThat(page.getAttribute("[data-inspector-section='selection']", "data-relevance"))
                .isEqualTo("secondary");

        page.click("[data-tool='select']");
        assertThat(page.getAttribute("[data-inspector-section='selection']", "data-relevance"))
                .isEqualTo("primary");
        assertThat(page.getAttribute("[data-inspector-section='palette']", "data-relevance"))
                .isEqualTo("secondary");
    }

    @Test
    void everySectionRemainsReachableEvenWhenSecondary() {
        openEditor();
        page.click("[data-tool='brush']");
        for (String section : java.util.List.of("tool", "palette", "selection", "layers",
                "map", "pins")) {
            assertThat(page.isVisible("[data-inspector-section='" + section + "']"))
                    .as("section %s stays reachable", section).isTrue();
        }
    }
```

Reuse whatever helper `MapEditorBrowserTest` already has for opening the editor; if it is
named differently from `openEditor()`, use that name.

- [ ] **Step 2: Run red, implement, run green**

`map-inspector.js`:

```js
(function () {
    const RELEVANCE = {
        brush: 'palette', fill: 'palette', room: 'palette', corridor: 'palette',
        region: 'palette', door: 'palette',
        rect: 'tool', circle: 'tool', line: 'tool', polygon: 'tool',
        select: 'selection'
    };

    function setContext(primary) {
        document.querySelectorAll('[data-inspector-section]').forEach(section => {
            const key = section.dataset.inspectorSection;
            section.dataset.relevance = key === primary ? 'primary' : 'secondary';
        });
    }

    document.addEventListener('click', event => {
        const tool = event.target.closest('[data-tool]');
        if (!tool) return;
        document.querySelectorAll('[data-tool]').forEach(
            button => button.setAttribute('aria-pressed', String(button === tool)));
        setContext(RELEVANCE[tool.dataset.tool] ?? 'tool');
    });

    window.mapInspector = { setContext };
    document.addEventListener('DOMContentLoaded', () => setContext('palette'));
})();
```

CSS: a `primary` section shows expanded; a `secondary` section collapses to its heading with
a disclosure the DM can open. Never `display: none` — every section stays reachable.

```css
.mapedit__inspector [data-relevance="secondary"] > :not(h2) { display: none; }
.mapedit__inspector [data-relevance="secondary"][open] > * { display: revert; }
.mapedit__inspector [data-relevance="primary"] { order: -1; }
```

Render each section as a `<details>` with a `<summary>` heading so the collapse is native and
keyboard-operable, and set `open` on the primary one from `map-inspector.js`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/map-inspector.js src/main/resources/templates/maps/editor.html src/main/resources/static/css/map-editor.css src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
git commit -m "feat: make the map inspector contextual to the active tool"
```

## Task 40: Canvas legibility and quiet save state

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`, `battle-map.js`
- Modify: `src/main/resources/static/css/map-editor.css`

- [ ] **Step 1: Write the failing browser assertions**

Add to `MapEditorBrowserTest`:

```java
    @Test
    void theCanvasReceivesTheMajorityOfTheViewport() {
        page.setViewportSize(1280, 720);
        openEditor();
        double canvas = ((Number) page.evaluate(
                "() => document.querySelector('.mapedit__canvas').getBoundingClientRect().width"))
                .doubleValue();
        assertThat(canvas / 1280.0).as("canvas share of width").isGreaterThan(0.6);
    }

    @Test
    void gridAndSelectionStayLegibleOverImportedImagery() {
        openEditor();
        Object contrastPair = page.evaluate("""
                () => {
                  const s = getComputedStyle(document.documentElement);
                  return [s.getPropertyValue('--map-grid-line').trim(),
                          s.getPropertyValue('--map-selection').trim()];
                }
                """);
        assertThat((java.util.List<?>) contrastPair).doesNotContain("");
    }

    @Test
    void autosaveStateIsVisibleButQuiet() {
        openEditor();
        assertThat(page.isVisible("#mapSaveStatus")).isTrue();
        String color = (String) page.evaluate(
                "() => getComputedStyle(document.querySelector('#mapSaveStatus')).color");
        assertThat(color).as("save state must not use the primary text role")
                .isNotEqualTo("rgb(238, 232, 220)");
    }
```

- [ ] **Step 2: Run red, implement, run green**

- Add `--map-grid-line` and `--map-selection` to `tokens.css` with values that keep 3:1
  against both `--surface-canvas` and a mid-grey imported image; draw the grid with a
  1px dark stroke plus a 1px light stroke offset by 1px so it reads on any imagery.
- Point the editor's autosave writes at `#mapSaveStatus`, setting
  `data-save-status` to `saving`, `saved`, or `error`.
- Keep every existing render path; do not touch the 60fps draw loop's structure.

```bash
./mvnw -Dtest='MapEditorBrowserTest,MapEditorLayoutContractTest,RawVisualValueContractTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/map src/main/resources/static/css src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
git commit -m "feat: keep the map canvas legible and its save state quiet"
```

## Task 41: Stage 6 gate

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/MapEditorRenderGateTest.java`

- [ ] **Step 1: Write the gate**

Copy the `NarrativePreparationRenderGateTest` structure with
`SHOTS = Path.of("target/ui-redesign/map-editor")` and:

```java
    @Test
    void nothingEssentialClipsAtTheMinimumViewport() {
        for (int[] viewport : new int[][]{{1280, 720}, {1440, 900}, {1920, 1080}}) {
            page.setViewportSize(viewport[0], viewport[1]);
            open("/campaigns/" + seeded.campaignId() + "/maps/" + seeded.playableMapId() + "/edit");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> geometry = (java.util.Map<String, Object>) page.evaluate("""
                    () => {
                      const clipped = [...document.querySelectorAll(
                          '.mapedit__commandbar [data-command], .mapedit__rail [data-tool]')]
                        .filter(el => {
                          const r = el.getBoundingClientRect();
                          return r.width === 0 || r.height === 0
                              || r.right > innerWidth + 1 || r.bottom > innerHeight + 1;
                        }).map(el => el.dataset.command ?? el.dataset.tool);
                      return {
                        clipped,
                        docOverflow: document.documentElement.scrollHeight
                                   - document.documentElement.clientHeight
                      };
                    }
                    """);
            assertThat((java.util.List<?>) geometry.get("clipped"))
                    .as("clipped controls at %dx%d", viewport[0], viewport[1]).isEmpty();
            assertThat(((Number) geometry.get("docOverflow")).intValue())
                    .as("document scroll at %dx%d", viewport[0], viewport[1])
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void captureTheEditorReviewSet() {
        for (int[] viewport : new int[][]{{1280, 720}, {1920, 1080}}) {
            page.setViewportSize(viewport[0], viewport[1]);
            open("/campaigns/" + seeded.campaignId() + "/maps/" + seeded.playableMapId() + "/edit");
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SHOTS.resolve("editor-" + viewport[0] + ".png")));
        }
        assertThat(SHOTS.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(2);
    }
```

Use the route `GameMapController` actually maps for the editor.

- [ ] **Step 2: Run the Stage 6 gate**

```bash
./mvnw -Dtest='MapEditorLayoutContractTest,MapEditorBrowserTest,MapEditorRenderGateTest' test
./mvnw test
```

- [ ] **Step 3: Review `target/ui-redesign/map-editor/` and commit**

```bash
git add -A
git commit -m "feat: complete the map editor stage"
```


---

# Stage 7: Session cockpit

**Working product after this stage:** the cockpit keeps its four-zone workbench, custom
presets, Focus/Return, locked default, Edit layout mode, keyboard arrangement, and persisted
device state, while every built-in preset is useful without layout editing and the whole
surface fits 1280x720.

Preset changes stay manual. Current scene, active encounter, and workspace map stay
independent functional state. No preset is removed.

## Task 42: Cockpit command bar

**Files:**
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/css/cockpit.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitSurfaceContractTest.java`

- [ ] **Step 1: Write the Stage 7 contract**

This is the one static contract class for Stage 7; Task 44 extends it. The eight command-bar
slots are *not* enumerated here — `CockpitLaptopFitGateTest` already asserts that no command
bar control is clipped or zero-width at three viewports, and whether the slots read well in
that order is a review question against `target/ui-redesign/cockpit/`.

```java
package dev.hendrikhoemberg.dmhelper.session.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** The structural invariants of Stage 7. Composition is reviewed, not asserted. */
class CockpitSurfaceContractTest {

    private static String cockpit() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
    }

    @Test
    void layoutChromeIsAbsentWhileLocked() throws Exception {
        assertThat(cockpit())
                .as("spec 12.5: no layout editing chrome while locked")
                .contains("th:if=\"${layoutEditing}\"");
    }
}
```

- [ ] **Step 2: Run red, restructure, run green**

Rebuild the cockpit's top bar as `[data-cockpit-commandbar]` with the eight
`data-command-slot` regions in spec order. Move Edit layout, Reset layout, provider settings,
and any other rare administrative action into the `more` popover. Guard every layout-editing
control with `th:if="${layoutEditing}"`.

```bash
./mvnw -Dtest='CockpitSurfaceContractTest,SessionControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/session/cockpit.html src/main/resources/static/css/cockpit.css src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitSurfaceContractTest.java
git commit -m "feat: rebuild the cockpit command bar on the approved slots"
```

## Task 43: Redesign the four built-in presets

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalog.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/layout/CockpitBuiltInPresetCatalogTest.java`

**Interfaces:**
- Consumes: `CockpitLayoutDocument.SplitRatios(left, primary, right, bottom)`.
- Constraint from `CockpitLayoutValidator`: `left + primary + right == 1.0` (±0.001),
  `0.50 <= primary <= 0.65`, `0.16 <= bottom <= 0.40`, `left > 0`, `right > 0`.

- [ ] **Step 1: Write the failing preset composition test**

Add to `CockpitBuiltInPresetCatalogTest`:

```java
    private static java.util.List<String> zone(CockpitBuiltInPresetCatalog.BuiltInPreset preset,
                                               CockpitZone zone) {
        return preset.layout().zones().get(zone).moduleKeys();
    }

    @Test
    void explorationLeadsWithTheStoryWorkflow() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:exploration");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("story");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("session-plan");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("party");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "audio", "reference");
        assertThat(preset.layout().zones().get(CockpitZone.BOTTOM_UTILITY).collapsed())
                .as("quick notes are the default bottom module, not a collapsed drawer")
                .isFalse();
    }

    @Test
    void combatLeadsWithTheMapAndKeepsTheEncounterProminent() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:combat");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("map");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("encounter");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("story", "party");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "reference", "audio", "session-log");
        assertThat(preset.layout().compactModuleKeys()).contains("story", "party");
        assertThat(preset.layout().compactModuleKeys())
                .as("the encounter is prominent support, never compact").doesNotContain("encounter");
    }

    @Test
    void theatreOfMindLeadsWithTheEncounterAndExpandsTheStory() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:theatre-of-mind");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("encounter");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("story");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("party", "reference");
        assertThat(zone(preset, CockpitZone.BOTTOM_UTILITY))
                .containsExactly("quick-notes", "audio", "session-log");
        assertThat(preset.layout().compactModuleKeys()).doesNotContain("story");
    }

    @Test
    void sessionReviewLeadsWithTheLogDraft() {
        var preset = new CockpitBuiltInPresetCatalog().require("builtin:session-review");
        assertThat(zone(preset, CockpitZone.PRIMARY)).containsExactly("session-log");
        assertThat(zone(preset, CockpitZone.LEFT_SUPPORT)).containsExactly("session-plan");
        assertThat(zone(preset, CockpitZone.RIGHT_SUPPORT)).containsExactly("quick-notes", "party");
    }

    @Test
    void everyPresetSatisfiesTheLayoutValidator() {
        var validator = new CockpitLayoutValidator();
        for (var preset : new CockpitBuiltInPresetCatalog().all()) {
            assertThat(validator.validate(preset.layout()))
                    .as("validation problems for %s", preset.key())
                    .isEmpty();
        }
    }

    @Test
    void noPresetLeavesTheBottomZoneEmptyAndUncollapsed() {
        for (var preset : new CockpitBuiltInPresetCatalog().all()) {
            var bottom = preset.layout().zones().get(CockpitZone.BOTTOM_UTILITY);
            assertThat(bottom.moduleKeys().isEmpty() && !bottom.collapsed())
                    .as("%s wastes bottom space", preset.key()).isFalse();
        }
    }
```

Adapt `validator.validate(...)` to the validator's actual method name and return type.

- [ ] **Step 2: Run it and watch it fail**

```bash
./mvnw -Dtest='CockpitBuiltInPresetCatalogTest' test
```

- [ ] **Step 3: Rewrite the catalog with per-preset ratios**

```java
    private static final List<BuiltInPreset> PRESETS = List.of(
            preset("builtin:exploration", "Exploration",
                    new CockpitLayoutDocument.SplitRatios(0.22, 0.54, 0.24, 0.16),
                    zone("story"), zone("session-plan"), zone("party"),
                    zone("quick-notes", "audio", "reference"),
                    Set.of("session-plan", "party", "audio", "reference")),
            preset("builtin:combat", "Combat",
                    new CockpitLayoutDocument.SplitRatios(0.18, 0.52, 0.30, 0.20),
                    zone("map"), zone("story", "party"), zone("encounter"),
                    zone("quick-notes", "reference", "audio", "session-log"),
                    Set.of("story", "party", "quick-notes", "reference", "audio", "session-log")),
            preset("builtin:theatre-of-mind", "Theatre of Mind",
                    new CockpitLayoutDocument.SplitRatios(0.19, 0.50, 0.31, 0.16),
                    zone("encounter"), zone("party", "reference"), zone("story"),
                    zone("quick-notes", "audio", "session-log"),
                    Set.of("party", "reference", "quick-notes", "audio", "session-log")),
            preset("builtin:session-review", "Session Review",
                    new CockpitLayoutDocument.SplitRatios(0.22, 0.56, 0.22, 0.16),
                    zone("session-log"), zone("session-plan"), zone("quick-notes", "party"),
                    collapsedZone(),
                    Set.of("session-plan", "quick-notes", "party"))
    );
```

Change `preset(...)` to take the ratios as its third parameter and pass them into the
`CockpitLayoutDocument` instead of `DEFAULT_RATIOS`; delete `DEFAULT_RATIOS`. Keep the
existing `zone(...)`/`collapsedZone(...)` helpers and the `BuiltInPreset` record unchanged.

- [ ] **Step 4: Run the whole cockpit layout package**

```bash
./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.session.layout.*,CockpitLayoutApiControllerTest' test
```

Expected: PASS. Saved custom presets are untouched — this changes only the built-in
definitions, and `CockpitLayoutResolver` already falls back to Exploration for unknown keys.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/layout src/test/java/dev/hendrikhoemberg/dmhelper/session/layout
git commit -m "feat: redesign the four built-in cockpit presets around one primary task"
```

## Task 44: Module hierarchy and runtime emphasis

**Files:**
- Modify: `src/main/resources/templates/session/_cockpit-module-shell.html`
- Modify: `src/main/resources/templates/session/modules/*.html`
- Modify: `src/main/resources/static/css/cockpit-modules.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitSurfaceContractTest.java`

- [ ] **Step 1: Extend the Stage 7 contract**

Whether the Story module *fills* the primary zone and whether an empty module reads as
useful are review questions — look at `target/ui-redesign/cockpit/`. What goes in the
contract is the module-role vocabulary the shell keys off, the theatre-of-mind invariant
that the tracker never depends on the map module, and the runtime-state style roles.

```java
package dev.hendrikhoemberg.dmhelper.session.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

// Add to CockpitSurfaceContractTest (spec sections 12.4 and 12.5):

    private static final Path MODULES = Path.of("src/main/resources/templates/session/modules");

    private static String module(String name) throws Exception {
        return Files.readString(MODULES.resolve(name));
    }

    @Test
    void everyModuleDeclaresItsRoleSoZonesCanRankThem() throws Exception {
        try (var files = Files.list(MODULES)) {
            for (Path module : files.toList()) {
                assertThat(Files.readString(module)).as(module.getFileName().toString())
                        .contains("data-module-role");
            }
        }
    }

    @Test
    void theEncounterModuleIsFullyOperableWithoutAMap() throws Exception {
        String encounter = module("_encounter.html");
        assertThat(encounter).contains("data-encounter-turn").contains("data-encounter-initiative");
        assertThat(encounter)
                .as("theatre of mind: the tracker must not require the map module")
                .doesNotContain("data-requires-map");
    }

    @Test
    void runtimeStateRolesAreDistinctAndSubstantial() throws Exception {
        String css = Files.readString(
                Path.of("src/main/resources/static/css/cockpit-modules.css"));
        for (String role : List.of("[data-runtime-state=\"current-scene\"]",
                "[data-runtime-state=\"active-encounter\"]",
                "[data-runtime-state=\"map-mismatch\"]",
                "[data-runtime-state=\"active-turn\"]",
                "[data-runtime-state=\"session\"]",
                "[data-runtime-state=\"connection\"]")) {
            assertThat(css).as("style for %s", role).contains(role);
        }
    }
```

- [ ] **Step 2: Run red, implement, run green**

- Every module template declares `data-module-role` of `primary`, `support`, or `utility`;
  `_cockpit-module-shell.html` uses it to pick heading size, padding, and density.
- `_story.html` gains the complete current-scene workflow: scene title and state, read-aloud,
  transitions, and linked references — enough to fill the primary zone rather than one short
  card above empty canvas. Judge "enough" from the Exploration preset capture, not a checklist.
- `_encounter.html` renders turn order and unresolved initiative inline
  (`data-encounter-turn`, `data-encounter-initiative`) with no dependency on the map module.
- `cockpit-modules.css` gives each `data-runtime-state` a distinct treatment combining at
  least two signals; the active combatant gets a substantial panel or row, not a hairline.
- The module shell renders `~{fragments/_states :: empty}` with a next action when a module
  has no content.

```bash
./mvnw -Dtest='CockpitSurfaceContractTest,CockpitRuntimeModuleControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/session src/main/resources/static/css/cockpit-modules.css src/test/java/dev/hendrikhoemberg/dmhelper/session/web/CockpitSurfaceContractTest.java
git commit -m "feat: rank cockpit modules and make runtime state substantial"
```

## Task 45: Laptop fit with container queries

**Files:**
- Modify: `src/main/resources/static/css/cockpit-layout.css`, `cockpit-modules.css`
- Modify: `src/main/resources/static/js/cockpit-layout.js` (support-zone tabbing only)
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/CockpitLaptopFitGateTest.java`

- [ ] **Step 1: Write the failing gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

// same @SpringBootTest / @ActiveProfiles("playwright") scaffolding as
// ViewportAccessibilityGateTest, including lifecycleService.start(...) in @BeforeAll

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}};

    @Test
    void everyBuiltInPresetFitsWithoutClippingOrDocumentScroll() {
        for (String preset : java.util.List.of("builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review")) {
            for (int[] viewport : VIEWPORTS) {
                openCockpitWithPreset(preset, viewport[0], viewport[1]);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> report =
                        (java.util.Map<String, Object>) page.evaluate("""
                        () => ({
                          docOverflow: document.documentElement.scrollWidth
                                     - document.documentElement.clientWidth,
                          clipped: [...document.querySelectorAll('[data-cockpit-commandbar] button,'
                                   + ' [data-cockpit-commandbar] a')]
                            .filter(el => {
                              const r = el.getBoundingClientRect();
                              return r.width === 0 || r.right > innerWidth + 1;
                            }).length,
                          scrollingModules: [...document.querySelectorAll('.cockpit-module')]
                            .filter(m => getComputedStyle(m).overflow === 'visible').length
                        })
                        """);
                assertThat(((Number) report.get("docOverflow")).intValue())
                        .as("%s at %dx%d document overflow", preset, viewport[0], viewport[1])
                        .isLessThanOrEqualTo(1);
                assertThat(((Number) report.get("clipped")).intValue())
                        .as("%s at %dx%d clipped command bar controls", preset, viewport[0], viewport[1])
                        .isZero();
                assertThat(((Number) report.get("scrollingModules")).intValue())
                        .as("%s at %dx%d modules must own their scrolling", preset, viewport[0], viewport[1])
                        .isZero();
            }
        }
    }

    @Test
    void noRequiredTextWrapsOneWordPerLine() {
        openCockpitWithPreset("builtin:combat", 1280, 720);
        Object worst = page.evaluate("""
                () => {
                  const els = [...document.querySelectorAll(
                      '.cockpit-module h2, .cockpit-module h3, .cockpit-module button')];
                  return Math.max(0, ...els.map(el => {
                    const words = el.textContent.trim().split(/\\s+/).length;
                    if (words < 2) return 0;
                    const lines = Math.round(el.getBoundingClientRect().height
                        / parseFloat(getComputedStyle(el).lineHeight));
                    return lines >= words ? words : 0;
                  }));
                }
                """);
        assertThat(((Number) worst).intValue()).as("one-word-per-line wrapping").isZero();
    }

    @Test
    void supportModulesBecomeTabsWhenTheZoneIsConstrained() {
        openCockpitWithPreset("builtin:combat", 1280, 720);
        assertThat(page.locator("[data-zone='LEFT_SUPPORT'] [role='tab']").count())
                .as("story and party share the constrained left zone as tabs")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void primaryRuntimeActionsStayVisibleWithoutScrolling() {
        openCockpitWithPreset("builtin:combat", 1280, 720);
        Object hidden = page.evaluate("""
                () => [...document.querySelectorAll('[data-runtime-action]')]
                        .filter(el => {
                          const r = el.getBoundingClientRect();
                          return r.bottom > innerHeight + 1 || r.width === 0;
                        }).length
                """);
        assertThat(((Number) hidden).intValue()).isZero();
    }
```

Write `openCockpitWithPreset(String presetKey, int width, int height)` to set the viewport,
navigate to the session route, wait for `window.cockpitLayout?.mounted === true`, then apply
the preset through the existing preset control — the same manual path a DM uses. Do not add
an automatic preset switch.

- [ ] **Step 2: Run red, implement, run green**

- Give each module `container-type: inline-size` and move internal breakpoints to
  `@container` queries so a module adapts to its zone, not the viewport.
- Enforce zone minima in `cockpit-layout.css` before text clips; the validator's ratios set
  the target, the minima protect it.
- In `cockpit-layout.js`, when a support zone holds more than one module and its measured
  width is below the widest module's `minWidth`, render the modules as `role="tablist"` tabs
  within the zone. Preserve keyboard arrangement and Focus/Return.
- Mark primary runtime actions with `data-runtime-action` so the gate can find them.
- Every module keeps `overflow: auto`; the document never scrolls.

```bash
./mvnw -Dtest='CockpitLaptopFitGateTest,ViewportAccessibilityGateTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css src/main/resources/static/js/cockpit-layout.js src/test/java/dev/hendrikhoemberg/dmhelper/gate/CockpitLaptopFitGateTest.java
git commit -m "feat: fit every cockpit preset on a laptop with container queries"
```

## Task 46: Stage 7 gate

- [ ] **Step 1: Capture every preset in idle and active-session states**

Add to `CockpitLaptopFitGateTest`:

```java
    @Test
    void captureTheCockpitReviewSet() throws Exception {
        java.nio.file.Path shots = java.nio.file.Path.of("target/ui-redesign/cockpit");
        java.nio.file.Files.createDirectories(shots);
        for (String preset : java.util.List.of("builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review")) {
            openCockpitWithPreset(preset, 1440, 900);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(shots.resolve(preset.replace(':', '-') + "-active.png")));
        }
        assertThat(shots.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(4);
    }
```

Run it once with an active session (the fixture already starts one in `@BeforeAll`) and once
with the session ended, saving the second set with an `-idle` suffix.

- [ ] **Step 2: Run the Stage 7 gate**

```bash
./mvnw -Dtest='CockpitSurfaceContractTest,CockpitBuiltInPresetCatalogTest,CockpitLaptopFitGateTest,ViewportAccessibilityGateTest' test
./mvnw test
```

Then update `docs/dm-manual/03-session-cockpit.md` wherever the preset compositions changed.
This is no longer test-enforced — `DmManualCockpitAccuracyTest` was removed by the test-suite
triage (`docs/test-suite-triage.md`) because it asserted on prose phrasing — so it is a
manual step. The presets themselves are covered by `CockpitBuiltInPresetCatalogTest`.

- [ ] **Step 3: Review `target/ui-redesign/cockpit/` and commit**

```bash
git add -A
git commit -m "feat: complete the session cockpit stage"
```


---

# Stage 8: Consistency and release gate

**Working product after this stage:** the redesign is complete. No page uses the former
brown-on-brown system, the compatibility layer is gone, and the accessibility, zoom, viewport,
keyboard, browser-health, and functional regression gates all pass.

## Task 47: Presentation surface and player-safe semantics

**Files:**
- Modify: `src/main/resources/templates/handout/_present-overlay.html`
- Modify: `src/main/resources/static/css/components.css`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/PresentationSurfaceTest.java`

Scope note: see "Scope reconciliation" at the top of this plan. This task redesigns the
presentation surface that exists — the full-viewport handout overlay — and keeps Shield
semantics consistent between it and the DM preview. It does not create a separate player
projection surface.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.handout.web;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 11.11, scoped to the presentation surface the product actually has. This class
 * survives the Stage 8 trim because leaking DM-only chrome onto a projected surface is a
 * safety failure, not an aesthetic one — a screenshot review would not reliably catch it.
 */
class PresentationSurfaceTest {


    private static String overlay() throws Exception {
        return Files.readString(
                Path.of("src/main/resources/templates/handout/_present-overlay.html"));
    }

    @Test
    void thePresentationCanvasCarriesNoDmChrome() throws Exception {
        var document = Jsoup.parse(overlay(), "", Parser.xmlParser());
        for (String chrome : List.of(".app-topbar", ".rail", ".page-header", ".toolbar",
                ".btn-danger")) {
            assertThat(document.select(chrome))
                    .as("DM chrome %s must not reach the presentation surface", chrome)
                    .isEmpty();
        }
        assertThat(overlay())
                .as("no fragment include may drag DM chrome in either")
                .doesNotContain("_topbar ::").doesNotContain("_rail ::")
                .doesNotContain("_page-header ::").doesNotContain("_toolbar ::");
    }

    @Test
    void thePresentedAssetReceivesTheViewportRatherThanACard() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/components.css"));
        var rule = css.substring(css.indexOf(".handout-overlay"));
        assertThat(rule).contains("position: fixed").contains("inset: 0");
        assertThat(rule).doesNotContain("var(--surface-raised)");
    }

    @Test
    void unavailableAndErrorPresentationStatesAreExplicit() throws Exception {
        assertThat(overlay())
                .contains("data-presentation-state")
                .contains("_states :: unavailable");
    }

    @Test
    void shieldSemanticsMatchTheDmPreview() throws Exception {
        String card = Files.readString(
                Path.of("src/main/resources/templates/handout/_card.html"));
        assertThat(card).contains("tone='shield'");
        assertThat(overlay())
                .as("the overlay states its player-safe status the same way")
                .contains("data-handout-visibility");
    }
}
```

- [ ] **Step 2: Run red, implement, run green**

- The overlay becomes a fixed, inset-0 near-black canvas (`--surface-canvas`) with the asset
  centred and no card chrome, DM-only labels, or management controls.
- Add `data-presentation-state` with `ready`, `unavailable`, or `error`; render
  `~{fragments/_states :: unavailable}` when the asset cannot load, with a Retry that re-fetches
  the same route.
- Carry `data-handout-visibility` so the presented state matches the DM-side Shield badge.

```bash
./mvnw -Dtest='PresentationSurfaceTest,HandoutControllerTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/handout src/main/resources/static/css/components.css src/test/java/dev/hendrikhoemberg/dmhelper/handout/web/PresentationSurfaceTest.java
git commit -m "feat: give the presentation surface a clean canvas and explicit states"
```

## Task 48: Administration, About, and error pages

**Files:**
- Modify: `src/main/resources/templates/campaigns/settings.html`, `campaigns/new.html`,
  `campaigns/_import-dialog.html`
- Modify: `src/main/resources/templates/about.html`, `error.html`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/AdministrationContractTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.campaign.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 11.10, structural only. Whether campaign settings and About *read* well is a
 * review question; the ranking of an import preview and the wording of a destructive
 * confirmation are not — both change what a DM decides.
 */
class AdministrationContractTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    @Test
    void importPreviewRanksErrorsWarningsContentsAndReadiness() throws Exception {
        String dialog = read("campaigns/_import-dialog.html");
        int errors = dialog.indexOf("data-import-section=\"errors\"");
        int warnings = dialog.indexOf("data-import-section=\"warnings\"");
        int contents = dialog.indexOf("data-import-section=\"contents\"");
        int readiness = dialog.indexOf("data-import-section=\"readiness\"");
        assertThat(List.of(errors, warnings, contents, readiness)).doesNotContain(-1);
        assertThat(errors).isLessThan(warnings);
        assertThat(warnings).isLessThan(contents);
        assertThat(contents).isLessThan(readiness);
    }

    @Test
    void destructiveCampaignActionsStateTheirConsequence() throws Exception {
        String settings = read("campaigns/settings.html");
        assertThat(settings).contains("data-settings-group=\"danger\"");
        assertThat(settings).contains("data-confirm-consequence");
    }

}
```

- [ ] **Step 2: Run red, implement, run green**

- `settings.html` → form archetype with four `data-settings-group` sections; the danger group
  is labelled, visually separated, and every destructive control carries
  `data-confirm-consequence` naming the entity and the consequence.
- `_import-dialog.html` → the four ranked `data-import-section` regions before the confirm
  action; validation errors block confirmation, warnings do not.
- `about.html` → detail archetype, `.prose`, no data-management chrome.
- `error.html` → plain-language `data-error-action`, a `data-error-recovery` link back to a
  safe destination, and the correlation id in `--text-tertiary` marked
  `data-error-correlation-id`.

```bash
./mvnw -Dtest='AdministrationContractTest,CampaignSettingsControllerTest,GlobalExceptionHandlerTest,NotFoundPageAdviceTest,RenderFailureIsCleanTest' test
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates src/test/java/dev/hendrikhoemberg/dmhelper/campaign/web/AdministrationContractTest.java
git commit -m "feat: restructure administration, About, and error pages"
```

## Task 49: Remove the compatibility layer

**Files:**
- Modify: `src/main/resources/static/css/tokens.css` and every stylesheet still referencing
  a legacy alias
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasRemovalTest.java`

- [ ] **Step 1: Write the failing removal test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 18.1 and acceptance criterion 11: completed migration leaves one semantic
 * token system.
 */
class LegacyVisualAliasRemovalTest {

    @Test
    void noLegacyAliasIsStillDefined() {
        assertThat(CssRules.read("tokens.css"))
                .as("the migration bridge must be gone")
                .doesNotContain("--color-");
    }

    @Test
    void noLegacyAliasIsStillReferenced() {
        String source = CssRules.allApplicationCss() + "\n" + CssRules.allTemplateMarkup();
        var matcher = java.util.regex.Pattern.compile("var\\(\\s*(--color-[a-z0-9-]+)")
                .matcher(source);
        var offenders = matcher.results().map(r -> r.group(1))
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        assertThat(offenders).as("remaining legacy references").isEmpty();
    }
}
```

- [ ] **Step 2: Run it, then replace every remaining reference**

```bash
./mvnw -Dtest='LegacyVisualAliasRemovalTest' test
```

Work through the reported list token by token using the mapping already encoded in the alias
block from Task 3 — each `--color-x` has exactly one semantic replacement. Ratchet the Task 5
budgets down to `0L` as you go, and delete the alias block from `tokens.css` last.

- [ ] **Step 3: Delete `LegacyVisualAliasContractTest`**

Its job is finished; `LegacyVisualAliasRemovalTest` is stricter and permanent.

```bash
git rm src/test/java/dev/hendrikhoemberg/dmhelper/config/LegacyVisualAliasContractTest.java
```

- [ ] **Step 4: Delete the superseded common fragments**

```bash
git rm src/main/resources/templates/common/_empty-state.html
git rm src/main/resources/templates/common/_skeleton.html
git rm src/main/resources/templates/common/_error.html
```

Repoint any remaining caller at `fragments/_states.html`. Keep
`common/_field-error.html` and `common/_form-cta.html` — Task 50 owns them.

- [ ] **Step 5: Run everything**

```bash
./mvnw -Dtest='LegacyVisualAliasRemovalTest,RawVisualValueContractTest,DesignTokenContractTest,SharedComponentContractTest' test
./mvnw test
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove the legacy visual compatibility layer"
```

## Task 50: Form, action, and destructive-behavior sweep

**Files:**
- Modify: every form template that the earlier stages did not already touch
- Modify: `src/main/resources/templates/common/_field-error.html`, `common/_form-cta.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/DestructiveActionContractTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/FormContractTest.java`

- [ ] **Step 1: Write the failing form contract**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 14. Kept deliberately narrow: an unlabelled control and a confirmation that
 * does not name its consequence are defects no screenshot review reliably catches. Action
 * placement and destructive separation are reviewed against the capture set instead — the
 * character-distance heuristic that used to live here produced both false passes and false
 * failures.
 */
class FormContractTest {

    @Test
    void everyControlHasALabelInTheSameOrder() {
        for (Path template : TemplateRules.allTemplates()) {
            var document = TemplateRules.parse(template);
            for (var control : document.select("input:not([type=hidden]), select, textarea")) {
                String id = control.attr("id");
                boolean labelled = (!id.isBlank() && !document.select("label[for=" + id + "]").isEmpty())
                        || control.hasAttr("aria-label")
                        || control.hasAttr("aria-labelledby")
                        || !control.parents().select("label").isEmpty();
                assertThat(labelled)
                        .as("%s: unlabelled control %s", template, control.outerHtml())
                        .isTrue();
            }
        }
    }

    @Test
    void fieldErrorsSitBesideTheirControlAndAreAnnounced() {
        String fragment = TemplateRules.read(TemplateRules.ROOT.resolve("common/_field-error.html"));
        assertThat(fragment).contains("role=\"alert\"").contains("aria-live");
    }

    @Test
    void confirmationCopyNamesTheEntityAndConsequence() {
        for (Path template : TemplateRules.allTemplates()) {
            String markup = TemplateRules.read(template);
            if (!markup.contains("data-confirm")) continue;
            assertThat(markup)
                    .as("%s: confirmation must name the consequence", template)
                    .contains("data-confirm-consequence");
        }
    }
}
```

- [ ] **Step 2: Run red, fix every reported form, run green**

For each report: add the missing label, move the submit pair into a
`data-action-region` footer, separate the destructive control into a labelled danger section
or a confirmation dialog, and add `data-confirm-consequence` copy naming the entity. On
recoverable submission failure, keep entered values and render
`~{fragments/_states :: failed}` with a visible Retry — `dm-request.js` already carries the
retry callback contract.

```bash
./mvnw -Dtest='FormContractTest,DestructiveActionContractTest,InteractionFailureContractTest' test
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: bring every form onto the shared action and destructive contracts"
```

## Task 51: Accessibility, zoom, and viewport matrix

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/ViewportMatrixGateTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/KeyboardOperationGateTest.java`

- [ ] **Step 1: Write the viewport matrix gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

// same scaffolding as ShellRenderGateTest

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}, {2560, 1440}};

    /**
     * Zoom is emulated by shrinking the viewport, because that is exactly what browser zoom
     * does to the CSS viewport: 125% of 1440x900 is 1152x720 CSS px.
     *
     * <p>The zoom gates run over the primary range only. Crossing every viewport with every
     * zoom would gate 1280x720 @ 150% = 853x480 CSS px, which contradicts spec section 4's
     * 1280x720 floor and this program's own minimum-viewport assertions (ShellRenderGateTest
     * requires .app-main to be at least 960px wide). 1440 @ 150% = 960x600 is the narrowest
     * CSS viewport the product commits to.
     */
    private static final int[][] ZOOMED = {
            {1440, 900, 125}, {1440, 900, 150}, {1920, 1080, 125}, {1920, 1080, 150}};

    @Test
    void everyReviewedPageSurvivesEverySupportedViewport() {
        for (int[] viewport : VIEWPORTS) {
            page.setViewportSize(viewport[0], viewport[1]);
            assertNoHorizontalOverflow("%dx%d".formatted(viewport[0], viewport[1]));
        }
    }

    @Test
    void everyReviewedPageSurvivesTheZoomGatesAcrossThePrimaryRange() {
        for (int[] gate : ZOOMED) {
            double zoom = gate[2] / 100.0;
            page.setViewportSize((int) (gate[0] / zoom), (int) (gate[1] / zoom));
            assertNoHorizontalOverflow("%dx%d @ %d%%".formatted(gate[0], gate[1], gate[2]));
        }
    }

    private void assertNoHorizontalOverflow(String label) {
        for (String path : reviewedPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            int overflow = ((Number) page.evaluate(
                    "() => document.documentElement.scrollWidth"
                            + " - document.documentElement.clientWidth")).intValue();
            assertThat(overflow).as("%s at %s", path, label).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void reducedMotionRemovesNonEssentialAnimation() {
        BrowserContext reduced = browser.newContext(new Browser.NewContextOptions()
                .setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE)
                .setViewportSize(1440, 900));
        Page reducedPage = reduced.newPage();
        try {
            reducedPage.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
            reducedPage.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            Object animated = reducedPage.evaluate("""
                    () => [...document.querySelectorAll('*')]
                            .filter(el => {
                              const s = getComputedStyle(el);
                              return s.animationName !== 'none'
                                  && parseFloat(s.animationDuration) > 0.05;
                            }).length
                    """);
            assertThat(((Number) animated).intValue()).isZero();
        } finally {
            reduced.close();
        }
    }

    @Test
    void requiredRuntimeTextNeverFallsBelowTheMinimumSize() {
        page.setViewportSize(1280, 720);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        Object tooSmall = page.evaluate("""
                () => [...document.querySelectorAll('.cockpit-module [data-runtime-state],'
                        + ' .cockpit-module [data-runtime-action]')]
                        .filter(el => parseFloat(getComputedStyle(el).fontSize) < 14)
                        .length
                """);
        assertThat(((Number) tooSmall).intValue()).isZero();
    }
```

`reviewedPages()` returns the same list as `ShellRenderGateTest.standardPages()` plus the
map editor and the session cockpit. Adjust the 14px floor to the computed value of
`--text-sm` if the token differs.

- [ ] **Step 2: Write the keyboard gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

// same scaffolding

    @Test
    void everyReviewedPageIsFullyKeyboardReachableWithVisibleFocus() {
        page.setViewportSize(1440, 900);
        for (String path : reviewedPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            Object invisible = page.evaluate("""
                    () => {
                      const focusable = [...document.querySelectorAll(
                        'button:not([disabled]), [href], input:not([disabled]), '
                        + 'select:not([disabled]), textarea:not([disabled]), '
                        + '[tabindex]:not([tabindex="-1"])')]
                        .filter(el => el.offsetParent !== null);
                      let bad = 0;
                      for (const el of focusable) {
                        el.focus();
                        const s = getComputedStyle(el);
                        if (s.outlineStyle === 'none' && s.boxShadow === 'none') bad++;
                      }
                      return bad;
                    }
                    """);
            assertThat(((Number) invisible).intValue())
                    .as("controls without a visible focus indicator on %s", path).isZero();
        }
    }

    @Test
    void everyIconOnlyControlHasAnAccessibleName() {
        page.setViewportSize(1440, 900);
        for (String path : reviewedPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            Object unnamed = page.evaluate("""
                    () => [...document.querySelectorAll('button, a')]
                            .filter(el => el.offsetParent !== null)
                            .filter(el => !el.textContent.trim()
                                       && !el.getAttribute('aria-label')
                                       && !el.getAttribute('aria-labelledby')
                                       && !el.getAttribute('title'))
                            .length
                    """);
            assertThat(((Number) unnamed).intValue())
                    .as("unnamed icon-only controls on %s", path).isZero();
        }
    }

    @Test
    void landmarksAndHeadingOrderAreLogical() {
        page.setViewportSize(1440, 900);
        for (String path : reviewedPages()) {
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> structure =
                    (java.util.Map<String, Object>) page.evaluate("""
                    () => {
                      const levels = [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')]
                        .map(h => Number(h.tagName[1]));
                      let skips = 0;
                      for (let i = 1; i < levels.length; i++) {
                        if (levels[i] - levels[i - 1] > 1) skips++;
                      }
                      return {
                        main: document.querySelectorAll('main').length,
                        nav: document.querySelectorAll('nav').length,
                        h1: levels.filter(l => l === 1).length,
                        skips
                      };
                    }
                    """);
            assertThat(((Number) structure.get("main")).intValue()).as("main on %s", path).isEqualTo(1);
            assertThat(((Number) structure.get("nav")).intValue()).as("nav on %s", path).isGreaterThanOrEqualTo(1);
            assertThat(((Number) structure.get("h1")).intValue()).as("h1 on %s", path).isEqualTo(1);
            assertThat(((Number) structure.get("skips")).intValue())
                    .as("skipped heading levels on %s", path).isZero();
        }
    }
```

- [ ] **Step 3: Run both, fix every failure at the source**

```bash
./mvnw -Dtest='ViewportMatrixGateTest,KeyboardOperationGateTest,ViewportAccessibilityGateTest' test
```

Fix the page, never the assertion. A page that cannot pass at 150% zoom needs its layout
adjusted, not its gate relaxed.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: gate the whole product on the viewport, zoom, and keyboard matrix"
```

## Task 52: Capture the visual review matrix

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualReviewMatrixGateTest.java`

**Interfaces:**
- Consumes: `ReleaseRehearsalFixture` (the feature-complete synthetic campaign).

Spec section 20.3 fixes the matrix. Capture into `target/ui-redesign/matrix/<surface>/<state>.png`.

- [ ] **Step 1: Write the capture gate**

```java
package dev.hendrikhoemberg.dmhelper.gate;

// same scaffolding as ShellRenderGateTest, viewport 1440x900

    // Each test owns a subtree, so the three capture tests cannot corrupt each other's
    // counts. JUnit does not guarantee method order, and a shared parent directory made the
    // "exactly N entries" assertion depend on which test ran first.
    private static final Path MATRIX = Path.of("target/ui-redesign/matrix");
    private static final Path POPULATED = MATRIX.resolve("populated");
    private static final Path EMPTY = MATRIX.resolve("empty");
    private static final Path OVERLAYS = MATRIX.resolve("overlays");

    private record Surface(String name, String path) {
    }

    private List<Surface> surfaces() {
        String c = "/campaigns/" + seeded.campaignId();
        return List.of(
                new Surface("campaign-selection", "/campaigns"),
                new Surface("campaign-home", c),
                new Surface("adventure-index", c + "/adventures"),
                new Surface("adventure-detail", c + "/adventures/" + seeded.adventureId()),
                new Surface("scene-detail", c + "/adventures/" + seeded.adventureId()
                        + "/scenes/" + seeded.hostileSceneId()),
                new Surface("quests", c + "/quests"),
                new Surface("npcs", c + "/world/npcs"),
                new Surface("locations", c + "/world/locations"),
                new Surface("factions", c + "/world/factions"),
                new Surface("notes", c + "/notes"),
                new Surface("calendar", c + "/calendar"),
                new Surface("encounter-index", c + "/encounters"),
                new Surface("encounter-detail", c + "/encounters/" + seeded.branchedEncounterId()),
                new Surface("encounter-setup", c + "/encounters/" + seeded.branchedEncounterId() + "/setup"),
                new Surface("party", c + "/party"),
                new Surface("sheets", c + "/sheets"),
                new Surface("treasury", c + "/treasury"),
                new Surface("ledger", c + "/ledger"),
                new Surface("handouts", c + "/handouts"),
                new Surface("audio", c + "/audio/cues"),
                new Surface("maps-index", c + "/maps"),
                new Surface("map-editor", c + "/maps/" + seeded.playableMapId() + "/edit"),
                new Surface("library-monsters", "/library"),
                new Surface("library-spells", "/library/spells"),
                new Surface("library-conditions", "/library/conditions"),
                new Surface("library-rules", "/library/rules"),
                new Surface("library-equipment", "/library/equipment"),
                new Surface("library-magic-items", "/library/magic-items"),
                new Surface("library-classes", "/library/classes"),
                new Surface("library-species", "/library/species"),
                new Surface("library-backgrounds", "/library/backgrounds"),
                new Surface("library-feats", "/library/feats"),
                new Surface("tables", "/library/tables"),
                new Surface("traps", "/library/traps"),
                new Surface("hazards", "/library/hazards"),
                new Surface("campaign-settings", c + "/settings"),
                new Surface("about", "/library/about"),
                new Surface("session-cockpit", c + "/session"));
    }

    @Test
    void capturePopulatedStatesForEverySurface() throws Exception {
        for (Surface surface : surfaces()) {
            Path directory = POPULATED.resolve(surface.name());
            Files.createDirectories(directory);
            page.navigate("http://localhost:" + port + surface.path());
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(directory.resolve("populated.png")).setFullPage(true));
        }
        assertThat(POPULATED.toFile().listFiles()).hasSize(surfaces().size());
    }

    @Test
    void captureEmptyStatesFromTheUnseededCampaign() throws Exception {
        // ReleaseRehearsalFixture seeds a second, deliberately empty campaign; if it does not,
        // add one that creates a campaign with no records and returns its id.
        String empty = "/campaigns/" + seeded.emptyCampaignId();
        for (String surface : List.of("/adventures", "/encounters", "/maps", "/handouts",
                "/audio/cues", "/notes", "/party", "/treasury", "/ledger", "/quests",
                "/world/npcs", "/world/locations", "/world/factions")) {
            Path directory = EMPTY.resolve(surface.substring(1).replace('/', '-'));
            Files.createDirectories(directory);
            page.navigate("http://localhost:" + port + empty + surface);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            assertThat(page.locator(".state--empty").count())
                    .as("empty state on %s", surface).isEqualTo(1);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(directory.resolve("empty.png")).setFullPage(true));
        }
    }

    @Test
    void captureOverlayAndTransientSurfaces() throws Exception {
        Path directory = OVERLAYS;
        Files.createDirectories(directory);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        page.keyboard().press("Control+k");
        page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve("command-palette.png")));
        page.keyboard().press("Escape");

        // The dice shortcut must preventDefault; without it Ctrl+R reloads the page and the
        // screenshot captures a fresh document instead of the roller.
        page.keyboard().press("Control+r");
        assertThat(page.locator("#diceRoller").isVisible())
                .as("Ctrl+R opened the roller rather than reloading").isTrue();
        page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve("dice-roller.png")));
        page.keyboard().press("Escape");

        page.evaluate("() => window.dmToast.show('Saved', 'success')");
        page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve("toast.png")));

        assertThat(directory.toFile().listFiles()).hasSizeGreaterThanOrEqualTo(3);
    }
```

- [ ] **Step 2: Run the capture and review every image**

```bash
./mvnw -Dtest='VisualReviewMatrixGateTest' test
```

Open every file under `target/ui-redesign/matrix/`. For each, answer: is the primary task
obvious, is the current state visible, is there exactly one primary action, is gold rare, is
space doing a job? Record any surface that fails and fix it before Task 53.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gate/VisualReviewMatrixGateTest.java
git commit -m "test: capture the whole-product visual review matrix"
```

## Task 53: Whole-product release gate

**Files:**
- Modify: `docs/product/all-in-one-release-gate.md`
- Modify: `docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md`
- Delete: `docs/ui-polish-spec.md`
- Delete: `docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md`

- [ ] **Step 1: Register the new gates in the release gate index**

Add a row for each new gate to `docs/product/all-in-one-release-gate.md`:
`ShellRenderGateTest`, `OverlayBehaviorGateTest`, `NarrativePreparationRenderGateTest`,
`OperationalPreparationRenderGateTest`, `ReferenceWorkspaceRenderGateTest`,
`MapEditorRenderGateTest`, `CockpitLaptopFitGateTest`, `ViewportMatrixGateTest`,
`KeyboardOperationGateTest`, and `VisualReviewMatrixGateTest`.

The index is a human-maintained document. `ReleaseGateIndexContractTest`, which used to
verify that every name in it resolved to a real class and method, was removed by the
test-suite triage (`docs/test-suite-triage.md`): it made every test rename a three-place
edit. Check the names by hand against `src/test/java`.

- [ ] **Step 2: Write the acceptance-criteria checklist test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 21: the machine-checkable half of the acceptance criteria. */
class RedesignCoverageContractTest {

    @Test
    void everyShippedPageIsAssignedAnApprovedArchetype() {
        for (Path template : TemplateRules.pageTemplates()) {
            if (template.getFileName().toString().equals("error.html")) continue;
            String markup = TemplateRules.read(template);
            assertThat(markup).as("%s declares an archetype", template).contains("archetype='");
            assertThat(PageArchetypeContractTest.ARCHETYPES)
                    .as("%s uses an approved archetype", template)
                    .anySatisfy(archetype -> assertThat(markup).contains("archetype='" + archetype + "'"));
        }
    }

    @Test
    void noShippedPageUsesTheFormerBrownSurfaceSystem() {
        String source = CssRules.allApplicationCss() + "\n" + CssRules.allTemplateMarkup();
        for (String brown : java.util.List.of("#17120c", "#211a12", "#2b2318", "#3a3125", "#564936")) {
            assertThat(source).as("superseded brown %s", brown).doesNotContain(brown);
        }
    }

    @Test
    void goldIsAbsentFromGenericCardBordersAndDecorativeSeparators() {
        CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> !rule.file().equals("tokens.css"))
                .filter(rule -> rule.selector().matches(".*\\.(card|panel|divider|rule|section)\\b.*"))
                .forEach(rule -> assertThat(
                        String.valueOf(rule.value("border")) + rule.value("border-color"))
                        .as("decorative gold in %s", rule.where())
                        .doesNotContain("--action-primary"));
    }

    @Test
    void everyDuplicateShellImplementationIsGone() {
        long shells = TemplateRules.allTemplates().stream()
                .filter(path -> TemplateRules.read(path).contains("class=\"app-shell\""))
                .count();
        assertThat(shells).isEqualTo(1);
    }
}
```

Make `PageArchetypeContractTest.ARCHETYPES` package-visible so this test can reuse it.

- [ ] **Step 3: Run the whole build**

```bash
./mvnw clean verify
```

Expected: PASS with no skipped gates.

- [ ] **Step 4: Retire the superseded documents**

```bash
git rm docs/ui-polish-spec.md
git rm docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md
```

Search for and update any reference to either path
(`grep -rn "ui-polish-spec\|ui-redesign-visual-foundations" --include='*.md' --include='*.java' .`).

- [ ] **Step 5: Record the acceptance evidence**

Append a "Release evidence" section to the program index,
`docs/superpowers/plans/2026-07-31-whole-product-ui-redesign.md`, listing, for each of the twelve acceptance
criteria in spec section 21, the test class or screenshot directory that demonstrates it, and
note any criterion that required a human judgement call with the reviewer's conclusion.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: pass the whole-product UI redesign release gate"
```

---

# Appendix A: Spec coverage for this part

| Spec section | Covered by |
|---|---|
| 4 Supported environment | Tasks 41, 45, 51 |
| 5 Design principles | Tasks 44, 52 (review) |
| 6.3 Gold discipline | Task 53 |
| 6.4 Semantic state | Task 44 |
| 11.8 Map editor | Task 38 (the maps *index* is Part 3, Task 33) |
| 11.10 Administration and system pages | Task 48 |
| 11.11 Presentation | Task 47 (see Scope reconciliation) |
| 12.1–12.5 Session cockpit | Tasks 42, 43, 44, 45, 46 |
| 13 Map editor | Tasks 38, 39, 40, 41 |
| 14 Forms and destructive behavior | Task 50 |
| 16 Loading and feedback | Task 40 |
| 17 Accessibility | Tasks 50, 51 |
| 18.1 CSS ownership | Tasks 38, 49 |
| 18.2 Template ownership | Task 53 |
| 18.3 JavaScript | Tasks 39, 45 |
| 20.1 Static contracts | Tasks 49, 53 |
| 20.2 Browser gates | Tasks 41, 45, 51 |
| 20.3 Visual review matrix | Task 52 |
| 20.4 Functional regression | Every stage's `./mvnw test` step; Task 53 |
| 21 Acceptance criteria | Task 53 |

# Appendix B: Whole-redesign acceptance criteria

Task 53 Step 5 records evidence for each. The redesign is complete only when:

1. Every shipped DM-facing page is assigned to and rendered with an approved archetype.
2. No shipped page uses the former brown-on-brown surface system.
3. Gold is absent from generic card borders and ordinary decorative separators.
4. Primary action and current state are identifiable on every reviewed page.
5. Campaign, preparation, reference, editor, and runtime surfaces feel related but
   appropriately specialized.
6. Every built-in cockpit preset is useful without layout editing.
7. The cockpit and map editor remain fully operable at 1280x720.
8. Standard pages use ultrawide space intentionally and do not strand narrow content in
   unrestricted empty canvases.
9. All asynchronous components have explicit loading, empty, error, and degraded behavior.
10. Accessibility, zoom, viewport, keyboard, browser-health, and functional regression gates
    pass.
11. Legacy palette and component compatibility rules are removed or confined to a documented,
    temporary allowlist with a removal task.
12. The visual review matrix has been captured and approved against the feature-complete
    synthetic campaign.

Criteria 4, 5, and 8 are human judgement calls made against `target/ui-redesign/matrix/`;
record the reviewer's conclusion rather than asserting them in code.

# Appendix C: Standing rules for every task

- Never widen a gate to make a page pass. Fix the page.
- Never add a `--color-*` token. The vocabulary is frozen at Task 5 and deleted at Task 49.
- Never leave a page half-migrated across a commit boundary within a stage's own scope.
- Never change a route, form field name, htmx target id, persisted field, or package format.
  If a redesign appears to require one, stop and raise it — that is a functional change and
  needs its own spec.
- When a test name in this plan does not match the repository's actual class, run the whole
  package (`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.<area>.*' test`) rather than guessing.
