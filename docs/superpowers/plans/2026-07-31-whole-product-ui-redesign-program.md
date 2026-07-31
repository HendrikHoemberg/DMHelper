# Whole-Product UI Redesign Delivery Program Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved whole-product desktop redesign as eight independently reviewable stages without changing DMHelper's persisted domain behavior.

**Architecture:** The work proceeds from shared visual contracts to shell and page archetypes, then migrates complete feature families, editors, and runtime surfaces before a final compatibility-removal gate. Each stage must leave a coherent, usable product and has its own executable TDD plan; this program fixes stage ownership, dependencies, test gates, and review evidence so the separate plans compose into one redesign.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, htmx, Alpine.js, vanilla JavaScript, repository-owned CSS and SVG, JUnit 5, AssertJ, jsoup, Playwright for Java, Maven Wrapper.

## Global Constraints

- The design authority is `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`.
- Support desktop and laptop browsers only, with a minimum viewport of 1280x720.
- Verify 1280x720, 1440x900, 1920x1080, and 2560x1440, plus 125% and 150% zoom.
- Preserve Spring MVC, Thymeleaf, htmx, Alpine.js, vanilla JavaScript, existing routes, persisted semantics, campaign-package formats, player safety, and the customizable four-zone cockpit with saved presets.
- Do not introduce a SPA, frontend package manager, CSS framework, icon font, or third-party component system.
- Keep `tokens.css` as the only source of raw UI color values; feature code consumes semantic tokens.
- Use system UI for chrome, controls, tables, labels, values, and metadata; Alegreya for narrative/document content; Cinzel only for the product wordmark and at most one principal title per view.
- Use aged gold only for a primary action, selection, or visible focus; use semantic colors consistently and never rely on color alone.
- A stage is complete only when every page in its scope is fully migrated; visibly hybrid pages do not pass review.
- No database migration is expected. View-only DTO or model additions are permitted only when required to render approved summaries or relationship rails.
- Keep existing functional, safety, controller, template, package, htmx, map, encounter, cockpit, and accessibility tests green.

---

## Program file map

| Plan | Owns | Depends on |
|---|---|---|
| `2026-07-31-ui-redesign-visual-foundations.md` | Token roles, contrast, typography, icon sprite, focus, motion, elevation, migration budgets | Approved design spec |
| `2026-08-01-ui-redesign-shell-and-archetypes.md` | Top bar, navigation rail, app frame, page header, five archetypes, shared controls, feedback, overlays | Visual Foundations |
| `2026-08-02-ui-redesign-campaign-and-narrative.md` | Campaign selection/home, adventures, scenes, quests, world, notes, calendar | Shell and Archetypes |
| `2026-08-03-ui-redesign-operational-preparation.md` | Encounters, party/sheets, treasury/ledger, handouts, audio | Shell and Archetypes |
| `2026-08-04-ui-redesign-reference-workspace.md` | Library categories, tables, traps, hazards, authored-reference detail surfaces | Shell and Archetypes |
| `2026-08-05-ui-redesign-map-editor.md` | Map command bar, tool rail, inspector, canvas, save/conflict feedback | Shell and Archetypes; icon and overlay contracts |
| `2026-08-06-ui-redesign-session-cockpit.md` | Cockpit command bar, module hierarchy, four built-in presets, laptop behavior, runtime state | Operational Preparation; Map Editor contracts |
| `2026-08-07-ui-redesign-consistency-release-gate.md` | Remaining system/player pages and overlays, legacy removal, complete matrix, final screenshots | All preceding stages |

The dates in these filenames are sequencing identifiers, not delivery estimates. The
detailed plan for a stage is written and reviewed immediately before that stage begins,
using the finished code from the preceding stage as its baseline.

## Shared stage protocol

Every detailed stage plan must follow this order:

- [ ] Re-read the approved design spec and this program, then inventory every route,
  template, stylesheet, behavior script, and existing test in the stage's ownership.
- [ ] Record the exact file map and public fragment/data-attribute interfaces before
  defining tasks.
- [ ] Write failing static or browser contracts for the stage's visible and behavioral
  requirements.
- [ ] Implement one independently reviewable vertical slice at a time, keeping htmx
  fragment boundaries and recoverable form state intact.
- [ ] Run the focused test after each red/green cycle and commit that slice.
- [ ] Run `./mvnw test` after the final slice and resolve failures without weakening
  unrelated gates.
- [ ] Capture the stage's populated, empty, loading, warning, error, and unavailable
  states from the synthetic campaign where those states exist.
- [ ] Review screenshots at the required viewports, keyboard-test the changed paths,
  and record the evidence in the stage plan's completion section.
- [ ] Reduce the legacy-token budgets for every migrated stylesheet/template and refuse
  any new legacy alias.

## Stage 1: Visual Foundations

**Executable plan:** `docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md`

**Working product after the stage:** Every existing page renders on the approved charcoal
surface ladder with the new text and semantic colors. The existing structure remains
functional, while new code can rely on stable semantic tokens, SVG icons, measured
contrast, focus, motion, and elevation contracts.

**Required outputs:**

- Automatic discovery of every shipped stylesheet, including `encounter.css`.
- Semantic surface, text, action, selection, focus, border, overlay, and state tokens.
- Legacy aliases mapped to the semantic system and guarded by non-increasing reference
  budgets.
- No raw UI color literal outside `tokens.css`; persisted/authored map colors remain
  domain data and are explicitly outside this CSS rule.
- A repository-owned 16/20/24px inline-SVG icon contract.
- Updated typography, gold, focus, motion, elevation, and contrast tests.
- Browser evidence for campaign, index, detail, form, operational, cockpit, and editor
  representatives at 1440x900.

**Exit gate:**

```bash
./mvnw -Dtest='CssInventoryContractTest,DesignTokenContractTest,RawVisualValueContractTest,LegacyVisualAliasContractTest,TypographyRoleContractTest,TypeScaleContractTest,GoldAccentContractTest,ElevationModelContractTest,MotionBudgetContractTest,IconSystemContractTest,VisualFoundationRenderGateTest' test
./mvnw test
```

## Stage 2: Application Shell and Shared Archetypes

**Owned production files:**

- `src/main/resources/templates/fragments/head.html`
- `src/main/resources/templates/fragments/navbar.html`
- `src/main/resources/templates/fragments/_appnav.html`
- New fragments under `src/main/resources/templates/common/` for page header,
  breadcrumb, action group, filter toolbar, contextual rail, status cluster, banner,
  loading/unavailable/error state, tabs, dialog, side sheet, popover, and toast.
- `src/main/resources/static/css/base.css`
- `src/main/resources/static/css/components.css`
- `src/main/resources/static/css/surfaces.css`
- Existing global behavior scripts for navigation, overlays, focus restoration, and
  status announcement.

**Working product after the stage:** Global and campaign pages share one desktop app
frame. Campaign navigation follows the approved five groups, persists collapse state,
and exposes accessible SVG icons. Index, Detail, Form, Operational, and Editor classes
have explicit width and scrolling behavior. Common headers, actions, filters, feedback,
and overlays are fragments rather than copied markup.

**Required behavioral interfaces:**

- `.app-shell`, `.app-topbar`, `.app-rail`, `.app-workspace`
- `.page.page--index`, `.page--detail`, `.page--form`, `.page--operational`,
  `.page--editor`
- `data-page-primary-action`, `data-overlay-trigger`, `data-overlay-close`
- `dmhelper.navCollapsed` remains the local-storage key for the rail preference.
- Overlay open/close restores focus to the trigger and announces asynchronous failure.

**Exit gate:**

- Global rail shows Campaigns, Library, Tables, Traps, Hazards, About.
- Campaign rail shows the exact approved group order and does not duplicate a permanent
  footer `Run Session` control.
- At most one filled primary action exists in each header/action region.
- Standard pages have no horizontal document overflow at all four viewports.
- All new shared fragments have rendering/contract tests and are used by the stage's
  reference templates.

## Stage 3: Campaign and Narrative Preparation

**Owned feature families:**

- Campaign selection, create/import preview, Campaign Home, and campaign settings.
- Adventure index/detail, scene detail/read/structure/edit, and scene relationship rail.
- Quest index/detail/form.
- NPC, location, and faction index/detail/form.
- Notes index/detail/form and quick notes.
- Calendar overview, timeline, events, and calendar configuration.

**Working product after the stage:** Narrative preparation uses clear Index, Detail, and
Form archetypes; relationship context appears in a 300-340px rail; prose stays near
70-80 characters; parchment and book typography appear only inside authored content.
Campaign Home presents current session state, upcoming work, recent records, and one
contextual Start/Resume action.

**Exit gate:**

- Every owned route has exactly one archetype marker and one principal title.
- Every list has a bounded grid/table, shared filter toolbar where applicable, and a
  useful shared empty state.
- Every form retains input on failure, places validation beside the field, and separates
  destructive actions in a labelled danger section.
- Scene, quest, note, and world details expose related state without nesting generic
  cards inside cards.
- Populated and empty screenshots pass at 1280x720 and 1920x1080.

## Stage 4: Operational Preparation

**Owned feature families:**

- Encounter index/detail/new/setup, initiative, tracker fragments, and summaries.
- Party roster, party state, character sheet overview/detail/create, and resource controls.
- Treasury inventory, item state, attunement, and ledger.
- Handout catalog/upload/detail/presentation controls.
- Audio catalog/detail/form/widget and cue state.

**Working product after the stage:** Operational screens use available width for aligned
tables, state clusters, local toolbars, and inspectors. Required runtime information is
never below `--text-sm`; quantities and combat values use tabular figures; state combines
color with icon/text/shape. Destructive actions are separated from routine work.

**Exit gate:**

- Encounter readiness, initiative, active combat, party resources, treasury quantities,
  ledger balances, handout projection, and audio playback states are visually distinct
  and keyboard operable.
- All htmx-replaced fragments preserve listeners, request deduplication, form drafts, and
  explicit retry behavior.
- Existing combat, player-safety, treasury, audio, and sheet tests remain green.
- Loading, empty, warning, error, and unavailable evidence exists for asynchronous
  operational components.

## Stage 5: Reference Workspace

**Owned feature families:**

- Library landing/category navigation and every result category.
- Creature, spell, item, class, species, background, feat, equipment, condition, and rule
  detail views.
- Rollable tables, traps, and hazards list/detail/form/management surfaces.
- Statblock and authored-reference book surfaces.

**Working product after the stage:** Library navigation is a dense, legible reference
workspace instead of an oversized undifferentiated catalog. Search and source/category
filters remain visible, results use stable title/metadata alignment, and reference detail
pages distinguish chrome from document content.

**Exit gate:**

- Every category is reachable by keyboard and preserves filter state through htmx updates.
- Each result family uses the shared result-card or data-table contract.
- Library titles and page actions do not collide at 1280x720 or 150% zoom.
- Book texture, Alegreya, and identity gold remain confined to authored/reference content.
- All category screenshots and functional source/filter tests pass.

## Stage 6: Map Editor

**Owned production files:**

- `src/main/resources/templates/maps/editor.html`
- New `src/main/resources/static/css/map-editor.css`
- `src/main/resources/static/js/map/map-editor.js`
- Supporting map scripts only where semantic data attributes or focus behavior require it.

**Working product after the stage:** The map editor owns the viewport below a compact
command bar. A fixed tool rail selects tools, a contextual inspector edits the active
object/layer, and the canvas receives the remaining space. Save, conflict, unavailable,
and recovery states remain visible without covering the canvas.

**Required invariants:**

- Persisted terrain and drawing colors are domain data, not UI palette tokens.
- Existing map geometry, flood fill, terrain palette, autosave, conflict backup, and
  player-safe rendering semantics remain unchanged.
- Tool selection, layer visibility, inspector fields, undo/redo, zoom, and canvas
  navigation are fully keyboard reachable.
- The document never scrolls at 1280x720; internal panes scroll independently.

**Exit gate:**

- Inline editor CSS is removed from `maps/editor.html` and owned by `map-editor.css`.
- Command bar, tool rail, inspector, and save state remain visible at all required
  viewports and zoom levels.
- Browser geometry tests assert no essential clipping and no overlay escapes.
- Existing map unit, controller, autosave, and player-output tests remain green.

## Stage 7: Session Cockpit

**Owned production files:**

- Session/cockpit templates and module fragments.
- `cockpit.css`, `cockpit-layout.css`, `cockpit-modules.css`, and `encounter.css`.
- `cockpit-layout.js`, `cockpit-modules.js`, `cockpit-reference.js`,
  `session-cockpit.js`, and `combat-tracker.js` where presentation contracts require it.
- Built-in preset definitions and their tests.

**Working product after the stage:** The four-zone customizable cockpit is preserved, but
the locked default experience has a compact command bar, clear primary module, quieter
support modules, and four useful built-in compositions: Exploration, Combat, Theatre of
Mind, and Session Review. Editing the layout remains an explicit mode.

**Preset acceptance:**

- Exploration prioritizes map/scene with party and notes support.
- Combat prioritizes active encounter/map with initiative and party state.
- Theatre of Mind prioritizes scene/read-aloud with encounter and reference support.
- Session Review prioritizes log/notes with party and unresolved-state support.
- Each preset is useful without resizing or moving a module.

**Exit gate:**

- Existing saved custom layouts and preset persistence continue to load.
- Primary zone owns approximately 50-65% of usable module area.
- No module violates declared minimum dimensions or overlaps another.
- Current turn, concentration, player-safe, save, connection, and unresolved state use
  stable semantic treatments with text/icon/shape support.
- The complete viewport, zoom, reduced-motion, splitter, tab, and keyboard gate passes.

## Stage 8: Consistency and Release Gate

**Owned scope:**

- Player presentation: curtain, map, handout, initiative, reconnecting, and error states.
- Campaign administration, import preview, About, generic errors, and system pages.
- Command palette, dice roller, dialogs, sheets, popovers, banners, toasts, skeletons,
  unavailable states, and any remaining shipped fragment.
- All legacy token aliases, compatibility selectors, inline application styles, emoji
  chrome, duplicate shell markup, and temporary migration budgets.

**Working product after the stage:** Every shipped page and transient surface belongs to
the same semantic system, no page uses the brown-on-brown palette, the full desktop
matrix is accessible and functional, and before/after evidence is ready for product
approval.

**Final automated gate:**

```bash
./mvnw test
git diff --check
```

**Final evidence gate:**

- [ ] Capture the complete visual review matrix from the feature-complete campaign at
  1440x900.
- [ ] Capture 1280x720 for every editor, cockpit preset, dense table, and overlay family.
- [ ] Capture representative standard pages at 1920x1080 and 2560x1440.
- [ ] Repeat representative standard, operational, editor, cockpit, and overlay paths at
  125% and 150% zoom.
- [ ] Complete keyboard-only traversal and reduced-motion review.
- [ ] Confirm no console error, unhandled rejection, or failed application request in the
  browser gate.
- [ ] Compare against `artifacts/ui-review/` before images and approve primary-action
  hierarchy, current-state clarity, surface separation, type roles, gold scarcity, and
  intentional use of wide space.
- [ ] Confirm every legacy budget is zero, then delete the aliases and the migration
  budget test.
- [ ] Confirm every whole-redesign acceptance criterion in spec section 21 has an
  automated assertion or named screenshot evidence.
