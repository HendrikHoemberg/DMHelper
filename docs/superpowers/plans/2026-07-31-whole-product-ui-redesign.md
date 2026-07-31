# Whole-Product UI Redesign — Program Index

> **This file is an index, not an executable plan.** The work lives in four part plans, each
> self-contained and sized to be loaded on its own. Do not load this file *and* a part plan
> expecting more detail here — everything an implementer needs is in the part.

**Goal:** Deliver the approved whole-product desktop redesign — charcoal foundation, one
application shell, five page archetypes, redesigned map editor and session cockpit — across
every shipped DMHelper page without changing routes, persisted semantics, or campaign-package
formats.

**Architecture:** Work flows from shared visual contracts outward. `tokens.css` becomes the
only raw-value authority and publishes semantic roles; a set of shared Thymeleaf fragments
becomes the only implementation of the app shell, page header, toolbar, feedback, and overlay
contracts; then feature families migrate onto those contracts one complete family at a time.
The map editor and cockpit are restructured last among feature work because they consume every
earlier contract, and a final stage removes the temporary compatibility layer and runs the
whole-product gate. Every stage is guarded by static JUnit contracts over CSS and template
source plus Playwright gates over computed geometry, so a half-migrated page fails the build
rather than shipping.

**Tech Stack:** Java 25, Spring Boot 4.1, Thymeleaf, htmx, Alpine.js, vanilla JavaScript,
repository-owned CSS and inline SVG, JUnit 5, AssertJ, jsoup, Playwright for Java, Maven
Wrapper.

**Design authority:** `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`.

## The four parts

Execute in order. Each part restates the global constraints, the interfaces it consumes and
produces, and its own exit gate, so a fresh session needs only that one file.

| Part | Plan | Stages | Tasks | Lines |
|---|---|---|---|---|
| 1 | [`2026-07-31-ui-redesign-1-visual-foundations.md`](2026-07-31-ui-redesign-1-visual-foundations.md) | 1 | 1–9 | ~1400 |
| 2 | [`2026-07-31-ui-redesign-2-shell-and-archetypes.md`](2026-07-31-ui-redesign-2-shell-and-archetypes.md) | 2 | 10–19 | ~2600 |
| 3 | [`2026-07-31-ui-redesign-3-feature-surfaces.md`](2026-07-31-ui-redesign-3-feature-surfaces.md) | 3–5 | 20–37 | ~1900 |
| 4 | [`2026-07-31-ui-redesign-4-editors-cockpit-release.md`](2026-07-31-ui-redesign-4-editors-cockpit-release.md) | 6–8 | 38–53 | ~2000 |

Task numbers are **global**: Task 30 means the same task in every part, so cross-references
survive the split.

### What each part delivers

1. **Visual foundations** — semantic charcoal tokens, measured WCAG contrast, raw-value
   containment, frozen legacy-alias budgets, the repository-owned SVG icon system, and the
   neutral document/control/focus/type roles. Structure unchanged.
2. **Application shell and shared archetypes** — top bar, task-grouped navigation rail, shell
   fragment, page-header contract, five archetypes, toolbar/badge/rail/feedback/overlay
   primitives, and the migration of all 59 page templates onto them. Content unchanged in
   substance.
3. **Feature surfaces** — campaign selection and home, adventures and scenes, quests, world,
   notes, calendar (Stage 3); encounters, party, sheets, treasury, ledger, maps, handouts,
   audio (Stage 4); Library, tables, traps, hazards (Stage 5). The three stages are
   independent of each other and may run in parallel worktrees.
4. **Editors, cockpit, and release** — map editor as a four-region workspace (Stage 6),
   session cockpit command bar, presets, module hierarchy and laptop fit (Stage 7), then the
   presentation surface, administration and error pages, form sweep, compatibility-layer
   removal, and the whole-product accessibility/viewport/visual-review release gate (Stage 8).

## Stage dependencies

| Stage | Owns | Depends on |
|---|---|---|
| 1. Visual foundations | Semantic tokens, contrast, type roles, icon sprite, focus, motion, elevation, legacy budgets | Approved spec |
| 2. Shell and archetypes | Top bar, rail, app frame, page header, five archetypes, shared controls, feedback, overlays | Stage 1 |
| 3. Campaign and narrative | Campaign selection/home, adventures, scenes, quests, world, notes, calendar | Stage 2 |
| 4. Operational preparation | Encounters, party, sheets, treasury, ledger, maps index, handouts, audio | Stage 2 |
| 5. Reference workspace | Library categories, tables, traps, hazards | Stage 2 |
| 6. Map editor | Command bar, tool rail, contextual inspector, canvas and save feedback | Stage 2 |
| 7. Session cockpit | Command bar, module hierarchy, four built-in presets, laptop fit, runtime state | Stages 2, 4 |
| 8. Consistency and release gate | System/admin/presentation pages, overlay and form sweep, legacy removal, full matrix | All |

Stages 3, 4, and 5 depend only on Stage 2 and may be executed in any order or in parallel.
Everything else is strictly sequential.

## Global constraints

Restated in full inside every part plan; reproduced here so the index stands alone.

- The design authority is `docs/superpowers/specs/2026-07-31-whole-product-ui-redesign-design.md`. This program supersedes `docs/superpowers/plans/2026-07-31-ui-redesign-visual-foundations.md` and the deleted `2026-07-31-whole-product-ui-redesign-program.md`; both are absorbed here.
- `docs/ui-polish-spec.md` is superseded, including its brown-palette-reuse and no-responsive-work requirements.
- Desktop and laptop browsers only. Minimum supported viewport **1280x720**; primary range **1440x900**–**1920x1080**; large-screen gate **2560x1440**; zoom gates **125%** and **150%**, applied across the primary range.
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

## Standing rules

- Never widen a gate to make a page pass. Fix the page.
- Never add a `--color-*` token. The vocabulary is frozen at Task 5 and deleted at Task 49.
- Never leave a page half-migrated across a commit boundary within a stage's own scope.
- Never change a route, form field name, htmx target id, persisted field, or package format.
  If a redesign appears to require one, stop and raise it — that is a functional change and
  needs its own spec.
- When a test name in a part plan does not match the repository's actual class, run the whole
  package (`./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.<area>.*' test`) rather than guessing.

## Scope reconciliation: spec section 11.11

Spec section 11.11 describes a player-facing presentation surface. The shipped product no
longer has one: commits `e6e0a3b8` (remove table-safe screen mode) and `440a6484` (remove
player-surface leftovers) cut it, and the only presentation surface that exists today is the
DM-side full-viewport handout overlay at
`GET /campaigns/{campaignId}/handouts/{id}/present`
(`templates/handout/_present-overlay.html`), plus the `playerSafe` flag on `Handout` and
`Note`.

**Assumption this program proceeds under:** section 11.11 applies to what exists. Task 47
(Part 4) redesigns that overlay to the spec's "neutral near-black canvas, no DM chrome,
explicit unavailable/error state" requirements and keeps Shield semantics stable. The program
does **not** re-introduce a separate player projection surface; that is a functional feature,
not a redesign, and needs its own spec. If one is wanted, raise it before Stage 8 so the
release gate can cover it.

## Whole-redesign acceptance criteria

Task 53 records evidence for each of the twelve criteria in spec section 21. The full list is
reproduced in Part 4, Appendix B. Criteria 4, 5, and 8 are human judgement calls made against
`target/ui-redesign/matrix/`.

## Spec coverage map

| Spec section | Part | Covered by |
|---|---|---|
| 4 Supported environment | 2, 4 | Tasks 11, 19, 41, 45, 51 |
| 5 Design principles | 1, 3, 4 | Tasks 7, 21, 44, 52 (review) |
| 6.1 Normative palette | 1 | Task 3 |
| 6.2 Surface ladder | 1 | Tasks 3, 7, 8 |
| 6.3 Gold discipline | 1, 2, 4 | Tasks 7, 15, 53 |
| 6.4 Semantic state | 1, 2, 3, 4 | Tasks 3, 16, 29, 44 |
| 7.1–7.2 Typeface and type roles | 1, 2, 3 | Tasks 7, 15, 23 |
| 7.3 Icons | 1, 2 | Tasks 6, 12, 13 |
| 8.1 Global top bar | 2 | Task 12 |
| 8.2–8.3 Navigation | 2 | Task 13 |
| 8.4 Page header | 2 | Tasks 15, 19 |
| 9.1–9.6 Archetypes and width | 2 | Tasks 11, 19 |
| 10 Shared components | 2 | Tasks 15, 16, 17, 18 |
| 11.1 Campaign selection | 3 | Task 20 |
| 11.2 Campaign Home | 3 | Task 21 |
| 11.3 Adventures and scenes | 3 | Tasks 22, 23 |
| 11.4 Encounters | 3 | Tasks 27, 28, 29 |
| 11.5 Party and sheets | 3 | Tasks 30, 31 |
| 11.6 Treasury and ledger | 3 | Task 32 |
| 11.7 Quests, world, notes, calendar | 3 | Tasks 24, 25, 26 |
| 11.8 Maps, handouts, audio | 3, 4 | Task 33 (catalogs), Task 38 (editor) |
| 11.9 Library and reference | 3 | Tasks 34, 35, 36 |
| 11.10 Administration and system pages | 4 | Task 48 |
| 11.11 Presentation | 4 | Task 47 (see Scope reconciliation) |
| 12.1–12.5 Session cockpit | 4 | Tasks 42, 43, 44, 45, 46 |
| 13 Map editor | 4 | Tasks 38, 39, 40, 41 |
| 14 Forms and destructive behavior | 4 | Task 50 |
| 15 Overlays | 2 | Task 18 |
| 16 Loading and feedback | 2, 4 | Tasks 17, 40 |
| 17 Accessibility | 1, 2, 4 | Tasks 7, 18, 50, 51 |
| 18.1 CSS ownership | 1, 4 | Tasks 1, 3, 4, 38, 49 |
| 18.2 Template ownership | 2, 4 | Tasks 10, 16, 19, 53 |
| 18.3 JavaScript | 2, 4 | Tasks 12, 13, 18, 39, 45 |
| 18.4 Backend compatibility | 3 | Tasks 21, 34 |
| 19 Delivery decomposition | — | The four-part structure of this program |
| 20.1 Static contracts | 1, 2, 4 | Tasks 1–7, 10–18, 49, 53 |
| 20.2 Browser gates | 1, 2, 3, 4 | Tasks 8, 19, 26, 33, 37, 41, 45, 51 |
| 20.3 Visual review matrix | 4 | Task 52 |
| 20.4 Functional regression | all | Every stage's `./mvnw test` step; Task 53 |
| 21 Acceptance criteria | 4 | Task 53 |

## Status log

Append one line per completed stage: the stage, the date, the gate command that passed, and
the screenshot directory. Task 9 records the final legacy-alias budget numbers here; Task 53
appends the release evidence for the twelve acceptance criteria.

| Stage | Date | Evidence |
|---|---|---|
| — | — | not started |
