# DMHelper Whole-Product UI Redesign

**Status:** Approved design

**Date:** 2026-07-31

**Scope:** The complete user interface: DM application shell, navigation, preparation
and reference pages, forms, operational workspaces, map editor, session cockpit,
player-facing presentation, overlays, feedback, accessibility, and desktop adaptability.

## 1. Authority and supersession

This is the authoritative visual and interaction design for DMHelper.

- It fully supersedes the former `ui-polish-spec` document (deleted at Task 53), including
  that document's requirements to reuse the existing brown palette and exclude responsive
  or adaptive layout work.
- It supersedes the visual-system requirements in older corrective specifications when
  they conflict with this document.
- Functional, safety, persistence, campaign-package, player-projection, and runtime
  requirements in `SPEC.md` and the approved subsystem specifications remain in force.
- Existing routes, form contracts, campaign-package formats, persisted entities, and
  cockpit preset storage remain compatible unless a later implementation plan identifies
  a separately approved functional change.

The redesign is comprehensive, but it is not a frontend rewrite. DMHelper remains a
server-rendered Spring MVC, Thymeleaf, htmx, Alpine, and vanilla-JavaScript application.

## 2. Problem statement

DMHelper has a recognizable candlelit-study identity, but its current use of that identity
flattens the product:

- page canvas, navigation, panels, cards, and inputs occupy nearly the same brown tonal band;
- gold is used for decoration, borders, selection, focus, headings, badges, and primary
  actions, so it no longer communicates priority;
- important runtime state is often represented only by small colored text;
- sparse pages cluster content in the upper-left and leave unrestricted empty space;
- feature areas independently choose widths, action placement, density, and chrome;
- the map editor and cockpit expose too many equal-weight controls;
- desktop layouts clip or become unusably compressed at laptop-sized viewports;
- decorative fantasy treatment competes with operational clarity.

The problem is not the existence of brown, gold, Cinzel, Alegreya, sigils, or parchment.
The problem is their indiscriminate use.

## 3. Goals

The redesign must:

1. Establish unmistakable visual hierarchy through value, surface role, typography,
   spacing, and restrained accent use.
2. Preserve DMHelper's fantasy identity while making it feel like a professional tool
   built for running a fantasy game.
3. Make preparation pages fast to scan and live-session surfaces legible at arm's length.
4. Provide one coherent application shell and a small set of page archetypes.
5. Make every supported feature feel intentionally designed rather than independently
   styled.
6. Preserve the customizable four-zone cockpit while making every built-in preset useful
   by default.
7. Use laptop and desktop space deliberately from 1280x720 through 2560x1440.
8. Meet WCAG AA, keyboard, focus, zoom, and reduced-motion requirements.
9. Preserve the existing server-rendered architecture and avoid a new frontend dependency
   stack.
10. Be deliverable in independently testable migration stages without leaving a migrated
    page visibly split between old and new systems.

## 4. Supported environment

DMHelper is a mouse-and-keyboard desktop/laptop application.

- **Minimum supported viewport:** 1280x720.
- **Primary optimization range:** 1440x900 through 1920x1080.
- **Large-screen gate:** 2560x1440.
- **Zoom gate:** 125% and 150% browser zoom.
- Phone, touch-first, and narrow mobile layouts are explicit non-goals.
- Standard pages must not develop horizontal document scrolling at supported sizes.
- The cockpit and map editor own the viewport and must not clip essential controls or
  runtime state at the minimum viewport.

The general application rail may be manually collapsed. Supported layouts must remain
usable with it expanded at 1280x720; collapse is a preference, not a requirement for
correctness.

## 5. Design principles

### 5.1 Task hierarchy outranks decoration

The primary task, current state, and next action must be recognizable before decorative
details. Ornament may reinforce identity but may not define basic grouping or compensate
for weak layout.

### 5.2 Warmth comes from selected moments

The interface foundation is neutral charcoal. Warmth comes from ivory text, aged-gold
interaction accents, narrative typography, campaign identity, and selective parchment
surfaces. The entire application is not coated in brown.

### 5.3 Gold is scarce

Gold denotes:

- the singular primary action in an action region;
- current selection;
- keyboard focus;
- exceptional emphasis tied to the preceding roles.

Gold does not outline generic cards, separate every section, decorate ordinary headings,
or replace neutral structure.

### 5.4 Space has a job

Empty space belongs to reading, map manipulation, or the current operational task. It
must not result from narrow content stranded inside an unrestricted viewport. Sparse
states use a useful summary, next action, contextual help, or intentionally bounded
composition.

### 5.5 Runtime state is substantial

Active turn, health, danger, success, warning, presentation safety, connection, save,
and session state use meaningful surfaces, icons, labels, or row treatment. Color alone
and tiny metadata are insufficient.

### 5.6 Progressive complexity

Common actions stay visible. Configuration, destructive actions, rare metadata, and
layout editing remain available but do not compete with routine preparation or play.

## 6. Visual foundation

### 6.1 Normative palette

The implementation may adjust a value only when measured contrast requires it. Semantic
roles and relative ordering are fixed.

| Role | Target value | Use |
|---|---:|---|
| Canvas | `#101113` | document and editor background |
| Navigation | `#151619` | application rail and global chrome |
| Workspace | `#18191d` | standard page workspace |
| Panel | `#1d1f24` | major grouped surface |
| Raised | `#24262c` | card, active module, side sheet |
| Inset | `#111216` | input, well, embedded code/data region |
| Border subtle | `#30333a` | decorative separation |
| Border strong | `#686c75` | active grouping and interactive boundaries |
| Text primary | `#eee8dc` | headings and primary content |
| Text secondary | `#b8b4aa` | supporting content |
| Text tertiary | `#929089` | optional metadata only |
| Aged gold | `#c9a35c` | primary action, selection, focus |
| Gold hover | `#ddb977` | active gold interaction |
| Success | `#89ad69` | ready, healthy, saved, completed |
| Warning | `#e0a34d` | unresolved, caution, pending input |
| Danger | `#d36a61` | destructive, defeated, failed, unsafe |
| Information | `#74a3c1` | neutral runtime information |
| Player-safe shield | `#8fa8b8` | projected or player-safe state |
| Concentration | `#b58ac1` | concentration state only |

Opaque semantic background, border, and foreground tokens must be derived in
`tokens.css`. Components consume semantic tokens rather than embedding color values.

### 6.2 Surface ladder

The surface ordering is:

`Canvas -> Navigation/Workspace -> Panel -> Raised -> Overlay`

- Adjacent same-level surfaces use spacing or a rule, not another nested card.
- Inputs and data wells use the Inset role.
- Interactive control boundaries meet the 3:1 non-text contrast requirement.
- Shadows are reserved for floating layers and do not make ordinary cards look detached.
- Texture is permitted only on campaign identity, narrative/parchment, handout, and
  intentionally in-world surfaces.
- A vignette may remain on immersive runtime/editor canvases only when it does not reduce
  information contrast.

### 6.3 Gold discipline

- Each action region has at most one filled-gold primary action.
- Selected navigation and tabs use a gold indicator plus high-contrast text, not a large
  gold-filled background.
- Focus rings are gold and remain visible independently of selection.
- Secondary buttons are neutral.
- Destructive buttons use the Danger role and are spatially separated from the primary
  routine action.
- Decorative gold borders, rules, and generic badges are removed.

### 6.4 Semantic state

Every semantic state combines at least two of:

- foreground color;
- tinted background or border;
- icon;
- explicit text;
- shape or row emphasis.

Success, warning, danger, information, player-safe, and concentration colors keep stable
meanings across all features.

## 7. Typography and iconography

### 7.1 Typeface roles

- **System UI:** navigation, controls, labels, tables, values, metadata, editor chrome,
  and cockpit chrome.
- **Alegreya:** narrative prose, read-aloud content, in-world notes, and document-like
  material.
- **Cinzel:** the product wordmark and at most one principal campaign, adventure, scene,
  or page title per view.
- **Monospace:** identifiers, formulas, dice expressions, source keys, and code-like data.

Cinzel must not be used for card titles, every heading level, modal chrome, table headers,
badges, or ordinary controls.

### 7.2 Type hierarchy

- Primary page title: clearly dominant but not oversized.
- Section headings: system UI, semibold, sentence case.
- Card/entity title: system UI, semibold.
- Narrative body: Alegreya at a comfortable reading size and line height.
- Required runtime information: never below the existing `--text-sm`.
- Tiny text is restricted to optional provenance or timestamps and must still meet contrast.
- Combat numbers, dates, quantities, currency, and resources use tabular figures.

### 7.3 Icons

- Application chrome uses a consistent repository-owned inline-SVG icon set.
- Icons use 16px, 20px, and 24px roles with consistent stroke weight.
- An icon-only control requires an accessible name and tooltip.
- Emoji and mixed Unicode pictograms are removed from application chrome.
- Authored content may contain emoji when it is part of the content itself.

## 8. Application shell and navigation

### 8.1 Global top bar

The top bar contains only global or session-wide utilities:

- product or campaign identity;
- global search;
- dice;
- current session state when applicable;
- an overflow menu for infrequent utilities.

Page creation, editing, filtering, and navigation actions belong to the page header or
local toolbar.

### 8.2 Campaign navigation

The expanded campaign rail is grouped as follows:

1. **Campaign**
   - Campaign Home
   - Run Session
2. **Prepare**
   - Adventures
   - Encounters
   - Maps
   - Handouts
   - Audio
3. **Party & World**
   - Party
   - Quests
   - NPCs
   - Locations
   - Factions
   - Calendar
4. **Records**
   - Notes
   - Treasury
   - Ledger
5. **Reference**
   - Library
   - Tables
   - Traps
   - Hazards

The rail has a persisted expanded/collapsed preference. The collapsed state uses the
standard icon system and accessible labels.

`Run Session` is not duplicated as a permanent footer button. Campaign Home may expose
a contextual Start/Resume action because it describes current campaign state.

### 8.3 Global navigation

Outside a campaign, the rail contains:

- Campaigns
- Library
- Tables
- Traps
- Hazards
- About

Global reference pages must not pretend to be inside a campaign.

### 8.4 Page header

Every standard page header follows one contract:

- optional breadcrumb;
- one principal title;
- optional one-line description or status summary;
- one primary action;
- a bounded secondary-action group or overflow.

Back navigation, creation, edit, run, and settings actions must not be scattered across
unrelated corners of the page.

## 9. Page archetypes

Every standard page is assigned one of five archetypes.

### 9.1 Index

Structure:

1. page header;
2. optional summary/status strip;
3. standard search/filter toolbar;
4. grid or table appropriate to the content;
5. useful empty state when no records exist.

Index grids fill horizontally before flowing down. A single item does not become a
full-width stretched card.

### 9.2 Detail

Detail pages use:

- a flexible primary content column;
- an optional contextual rail of approximately 300-340px;
- a 24px standard gap;
- related state and actions in the rail;
- readable content bounded to approximately 70-80 characters where prose dominates.

The rail may become an inline lower section only when necessary at the minimum viewport.
It may not squeeze the primary content below a usable width.

### 9.3 Form

Forms use:

- a content width appropriate to the task, normally 720-800px;
- titled field groups;
- consistent label, help, required, and validation placement;
- stable Cancel/Save actions;
- a separate labelled danger section;
- retained input and visible Retry on recoverable submission failure.

### 9.4 Operational

Operational workspaces use the available width for aligned data and controls. Examples
include encounter setup, party state, readiness, and character management. They may use
split panes, tables, inspectors, and sticky local controls.

### 9.5 Editor

The session cockpit and map editor own the viewport below their command bar. The document
does not scroll; internal regions do.

### 9.6 Width behavior

- Reading surfaces are constrained.
- Operational tables and editors use available width.
- Large monitors gain controlled side margins or purposeful secondary context.
- At 1280x720, current state and the primary action remain visible without horizontal
  document scrolling.

## 10. Shared components

The redesigned system provides reusable Thymeleaf and CSS contracts for:

- page header and breadcrumb;
- primary and secondary action groups;
- search/filter toolbar;
- cards and record rows;
- data tables;
- status, provenance, and scope badges;
- form field, help, error, and action footer;
- empty, loading, unavailable, and error states;
- detail contextual rail;
- tabs and local category navigation;
- dialog, side sheet, popover, and toast;
- inline icon;
- save/loading/connection status;
- semantic banner;
- skeleton placeholder.

Features may specialize content, but they must not recreate these primitives.

## 11. Feature-area designs

### 11.1 Campaign selection

- Retain book-cover identity and deterministic campaign sigils.
- Place covers on the neutral Workspace surface.
- Show campaign name, party size, last activity, and readiness without decorative
  all-caps forcing awkward title wraps.
- Treat import and create as clear but distinct actions.
- Empty state explains the two creation paths without filling the viewport with ornament.

### 11.2 Campaign Home

Campaign Home is the operational overview and the reference standard for hierarchy.

Order:

1. current scene and active encounter;
2. one Start/Resume Session action;
3. compact readiness summary;
4. party condition and resource summary;
5. preparation counts with meaningful warnings;
6. session plan and recent notes;
7. secondary settings and package/admin actions.

The page must not show repeated Run Session actions or equal-weight metric tiles for
irrelevant zero counts.

### 11.3 Adventures and scenes

- Adventure index uses compact cards with chapter/scene progress and current position.
- Adventure detail presents a scannable chapter outline with aligned scene rows.
- Scene detail makes narrative content primary and places status, map, encounter,
  handouts, references, and edit action in the contextual rail.
- Read-aloud text receives the selective warm/parchment treatment.
- Editorial metadata and structure controls stay behind Edit.
- Current-scene state and transitions remain visible without dominating the narrative.

### 11.4 Encounters

- Index shows status, difficulty, combatant count, map, and readiness in a scan-friendly
  grid or table with status filters.
- Detail is a readable preparation summary with one Run/Resume action.
- Setup uses a deliberate two-column Operational layout: combatants and sources on the
  left; encounter settings and placement on the right.
- Setup controls use usable widths and do not compress quantity, wave, search, and group
  inputs into ambiguous micro-controls.
- Live combat is visually distinct from preparation and setup.
- Health, active turn, conditions, concentration, defeated state, and unresolved
  initiative use substantial semantic treatment and tabular figures.

### 11.5 Party and character sheets

- Party is a compact roster with aligned name, player, AC, HP, passives, conditions, and
  resources.
- Bulk actions and selection use a standard table toolbar.
- Character sheets keep a persistent identity and derived-stat summary.
- Sheet sections use clear local hierarchy rather than a stack of same-color cards.
- Rest, level-up, inventory, spell, and resource state communicate pending and applied
  changes clearly.

### 11.6 Treasury and ledger

- Treasury and ledger use transactional tables.
- Ownership, item state, quantity, attunement, amount, direction, and balance are aligned.
- Semantic warnings use banners or row states, not tiny annotations.
- Entry forms use the standard Form archetype.
- Destructive and irreversible state changes remain separated and explicit.

### 11.7 Quests, world, notes, and calendar

These features share a Records pattern:

- standard search/filter toolbar;
- dense index rows or cards with aligned metadata;
- Detail archetype with relationship/context rail;
- consistent status chips, objective progress, faction clocks, backlinks, and quick notes;
- clear empty states and creation actions.

Specific requirements:

- Quest objectives make prerequisites and completion mode legible without exposing graph
  mechanics as raw data.
- NPC, location, and faction relationships are visually grouped by role and direction.
- Notes prioritize title, type, tags, body, and backlinks; quick-note actions remain fast.
- Calendar separates current date controls, upcoming events, and history instead of
  stacking equal-weight panels.

### 11.8 Maps, handouts, and audio catalogs

- Index pages use visual asset cards with concise operational metadata.
- Player-safe and DM-only handout state uses the stable Shield/Danger semantics.
- Present/preview actions are distinct from edit/delete actions.
- Audio cue state distinguishes source, assignment, availability, and runtime status.
- Empty and provider-unavailable states retain useful next actions.

### 11.9 Library and authored reference content

Library is a reference workspace, not a generic card wall.

- The page title matches the active category.
- A stable local category navigator exposes Monsters, Spells, Conditions, Rules,
  Equipment, Magic Items, Classes, Species, Backgrounds, and Feats.
- Search and category-specific filters remain visible while browsing.
- The creation action names the content type; ambiguous `New Homebrew` copy is removed.
- Cards show scan-critical information only.
- Long descriptions are clamped and opened in a detail page or side sheet.
- Repeated fields align between cards so CR, level, school, rarity, category, and source
  can be scanned.
- Provenance is present but visually secondary.
- Tables, traps, and hazards share the reference visual language while retaining their
  existing top-level destinations and task-specific renderers.

### 11.10 Campaign administration and system pages

- Campaign settings use the standard Form archetype and separate identity, progression,
  package, and danger concerns.
- Import preview distinguishes validation errors, warnings, package contents, and session
  readiness in a reviewable hierarchy before confirmation.
- Export, backup, and destructive campaign actions state their result and recovery
  implications.
- About and attribution pages use a restrained reading layout rather than inheriting a
  data-management surface.
- Error pages identify the failed action in plain language, preserve the correlation ID as
  secondary diagnostic information, and offer a safe retry or navigation path.
- System-wide empty, unavailable, and degraded states use the shared feedback contracts.

### 11.11 Presentation and player-facing output

Player-facing presentation remains intentionally minimal and server-filtered.

- It uses a neutral near-black canvas without DM navigation or management controls.
- Presented maps, handouts, initiative, and connection state receive the available viewport
  rather than appearing inside DM-style cards.
- Reconnecting, paused, curtain, unavailable, and presentation-error states are explicit.
- Player-safe Shield semantics remain consistent with the DM preview, but DM-only labels,
  controls, and decorative application chrome never reach the player surface.
- The player view remains usable at the same desktop viewport and zoom gates where applicable.

## 12. Session cockpit

### 12.1 Preserved model

The four-zone workbench, manual presets, custom presets, Focus/Return behavior, locked
default, Edit layout mode, keyboard arrangement, and persisted device state remain.

Preset changes remain manual. Current scene, active encounter, and workspace map remain
independent functional state.

### 12.2 Command bar

The command bar contains:

- campaign identity;
- session state;
- in-game date;
- active preset;
- Search;
- Dice;
- Session;
- More.

Rare layout, provider, and administrative actions live behind their appropriate menu.
The command bar must remain fully reachable at 1280x720.

### 12.3 Built-in preset compositions

#### Exploration

- Primary: Story.
- Support: Session Plan and Party.
- Bottom utility: Quick Notes by default, with Audio and Reference reachable.

The Story module uses the primary space for the complete current-scene workflow instead
of leaving a short card above an empty canvas.

#### Combat

- Primary: Map.
- Prominent support: Encounter.
- Compact support: Story and Party.
- Bottom utility: Quick Notes, Reference, Audio, and Session Log.

Active turn and unresolved initiative are visible without opening a secondary surface.

#### Theatre of Mind

- Primary: Encounter.
- Expanded support: Story.
- Support: Party and Reference.
- Bottom utility: Quick Notes, Audio, and Session Log.

The encounter tracker must remain fully operable without a map.

#### Session Review

- Primary: Session Log draft/timeline.
- Support: unresolved Quick Notes, Session Plan, and party/session summary.
- The completion action remains singular and stable.

### 12.4 Laptop behavior

- Modules use container queries for internal adaptation.
- Zone minima are enforced before text or controls clip.
- Support modules become tabs within their support zone when space is constrained.
- No required text wraps one word per line.
- No essential action is hidden below an unreachable internal region.
- Each module owns its scrolling; the document does not scroll.
- Empty modules provide a next action, collapse when allowed by the preset, or surrender
  space to the primary task.

### 12.5 Runtime emphasis

- Current scene, active encounter, workspace-map mismatch, active turn, presentation
  state, session state, and save/connection state use distinct roles.
- Active combatant uses a substantial row or panel treatment.
- Health and resource changes remain readable at arm's length.
- Semantic state never depends only on a thin colored border or tiny label.
- Primary runtime actions stay visible without scrolling.
- Layout editing chrome is absent while locked.

## 13. Map editor

The map editor becomes a three-part viewport-owned workspace.

### 13.1 Top command bar

Contains:

- Back to Maps;
- map name;
- Undo/Redo;
- save state;
- Import;
- Export;
- Help.

It does not contain every drawing property.

### 13.2 Tool rail

A compact tool rail groups:

- Brush and terrain;
- Fill;
- Selection;
- Rectangle, circle, line, and polygon;
- Room, door, corridor, and region tools.

Tools use the standard icon set, accessible labels, shortcut hints, and a strong selected
state.

### 13.3 Contextual inspector

The right inspector owns:

- active tool options;
- color/terrain palette;
- selected-object properties;
- layer visibility, lock, and ordering;
- map size and grid settings;
- threat pins.

Only options relevant to the active tool or selection are prominent.

### 13.4 Canvas

- The canvas receives the majority of the viewport.
- Grid and selection contrast remain legible against imported images and dark terrain.
- Autosave state stays visible but quiet.
- Existing layer, token, pin, calibration, import, crop, rotation, undo/redo, keyboard,
  and export behavior remains.
- The existing 60fps map performance target remains.

## 14. Forms, actions, and destructive behavior

- Labels, descriptions, controls, errors, and required markers use one consistent order.
- Field errors appear beside the responsible control and are announced.
- Recoverable failures retain entered values.
- Save and Cancel stay in a stable footer or action region.
- Each action region has one primary action.
- Delete, discard, reset, and irreversible transitions live in a labelled danger region
  or confirmation dialog.
- Destructive controls are not immediately adjacent to the common primary action.
- Confirmation copy names the affected entity and consequence.

## 15. Overlays and transient UI

The elevation model is:

1. **Popover:** short local choice.
2. **Side sheet:** reference or detail without losing page context.
3. **Modal dialog:** blocking decision or focused form.
4. **Toast:** transient confirmation.
5. **Inline banner:** persistent warning, degraded state, or recoverable error.

All overlays share:

- backdrop and shadow rules appropriate to their level;
- accessible name and role;
- predictable Escape behavior;
- focus trapping where blocking;
- focus restoration to the trigger;
- visible focus;
- bounded viewport sizing and internal scrolling.

A persistent error may not exist only as a timed toast.

## 16. Loading, feedback, and state coverage

Every asynchronous surface defines:

- loading;
- populated;
- empty;
- success;
- warning;
- error;
- unavailable or degraded state.

Requirements:

- Loading placeholders preserve layout and do not move primary controls.
- Autosave uses a quiet persistent indicator.
- Connection and runtime state remain discoverable without dominating the top bar.
- Recoverable failure provides Retry and retains existing content.
- Disabled controls explain why when the reason is not obvious.
- Success feedback names the result when ambiguity is possible.
- htmx fragment swaps preserve focus or intentionally restore it.

## 17. Accessibility

The completed redesign requires:

- WCAG AA text contrast;
- 3:1 visible boundaries for interactive controls and focus indicators;
- no state communicated by color alone;
- complete keyboard operation;
- logical headings and landmarks;
- labelled icon-only controls;
- visible focus on all interactive elements;
- focus trapping and restoration for overlays;
- reduced-motion support;
- usable layouts at 125% and 150% zoom;
- adequate runtime type size and tabular numeric values;
- screen-reader announcement of validation, loading, save, and error state.

Keyboard coverage includes global navigation, page actions, dialogs, side sheets,
cockpit tabs and splitters, map tools, tables, and editor inspectors.

## 18. Technical architecture

### 18.1 CSS ownership

- `tokens.css`: the only source of raw visual values and semantic role definitions.
- `base.css`: document defaults, accessibility foundations, app shell, and page
  archetypes.
- `components.css`: reusable controls, cards, tables, toolbars, forms, feedback, and
  overlays.
- `surfaces.css`: standard page and feature-surface composition.
- Existing feature stylesheets: specialized encounter, book/statblock, cockpit, and
  layout behavior.
- A dedicated map-editor stylesheet may be introduced to isolate the editor workspace.

Completed migration leaves one semantic token system. Legacy aliases may exist only as
temporary migration compatibility and must have a removal task.

### 18.2 Template ownership

Shared Thymeleaf fragments own the contracts listed in section 10. Feature templates own
their content and domain-specific interactions.

No feature template may duplicate global app-shell markup or create a private version of
a standard control without an explicitly documented reason.

### 18.3 JavaScript

- Existing htmx, Alpine, and vanilla-JavaScript behavior remains.
- No SPA or frontend package manager is introduced.
- UI state remains local to the component or existing browser-storage contract.
- Behavior scripts consume semantic classes/data attributes rather than styling details.
- Fragment replacement must not orphan listeners, duplicate requests, or lose recoverable
  drafts.

### 18.4 Backend compatibility

The redesign requires no database migration.

View-specific DTOs or controller model additions are permitted when a summary, state
cluster, or relationship rail cannot be rendered safely from the existing view model.
Such additions must not change persisted semantics or campaign-package formats.

## 19. Delivery decomposition

The whole redesign is one product design but must be implemented through staged plans.

1. **Visual foundations**
   - semantic tokens;
   - type roles;
   - icon system;
   - contrast, focus, motion, and elevation contracts.
2. **Application shell and shared archetypes**
   - top bar;
   - global/campaign navigation;
   - page header;
   - index, detail, form, operational, and editor foundations;
   - shared feedback and overlay primitives.
3. **Campaign and narrative preparation**
   - campaign selection/home;
   - adventures/scenes;
   - quests;
   - world;
   - notes;
   - calendar.
4. **Operational preparation**
   - encounters;
   - party/sheets;
   - treasury/ledger;
   - handouts;
   - audio.
5. **Reference workspace**
   - Library categories;
   - tables;
   - traps;
   - hazards.
6. **Map editor**
   - command bar;
   - tool rail;
   - contextual inspector;
   - canvas and state feedback.
7. **Session cockpit**
   - command bar;
   - module hierarchy;
   - four built-in presets;
   - laptop constraints;
   - runtime semantic states.
8. **Consistency and release gate**
   - remaining overlays/forms;
   - legacy token/style removal;
   - accessibility and viewport matrix;
   - full browser rehearsal and screenshot review.

A stage is complete only when every migrated page is fully on the new system. Compatibility
must not produce a visibly hybrid page.

## 20. Verification strategy

### 20.1 Static and contract tests

Automated contracts must verify:

- every referenced token exists;
- raw visual values are confined to the approved token layer;
- legacy visual aliases have a bounded migration allowlist;
- typography roles and one-display-title rule;
- minimum runtime text size;
- gold use is restricted to approved roles;
- icon-only controls have accessible names;
- focus styles exist;
- reduced-motion coverage exists;
- shared fragments are used by migrated page families;
- no duplicate app-shell implementations remain.

### 20.2 Browser layout gates

Playwright browser gates cover:

- 1280x720;
- 1440x900;
- 1920x1080;
- 2560x1440;
- 125% zoom;
- 150% zoom;
- reduced motion;
- keyboard-only operation.

Assertions include:

- no horizontal document overflow on standard pages;
- no essential cockpit/editor clipping;
- command bars remain reachable;
- overlays stay within the viewport;
- focus is visible and restored;
- supported tables and toolbars remain usable;
- no console errors, unhandled promise rejections, or failed application requests.

### 20.3 Visual review matrix

The feature-complete synthetic campaign is the repeatable visual fixture.

Review empty, populated, loading, warning, error, and unavailable states where applicable
for:

- campaign selection and Campaign Home;
- adventure index/detail and scene detail/edit;
- quest, NPC, location, faction, note, and calendar;
- encounter index/detail/setup/initiative/active combat;
- party roster and character sheet;
- treasury, ledger, handout, and audio;
- maps index and map editor;
- every Library category;
- tables, traps, and hazards;
- campaign settings, import preview, About, and error pages;
- every cockpit preset in idle and active-session states;
- player presentation in curtain, map, handout, initiative, reconnecting, and error states;
- command palette, dice roller, dialogs, side sheets, popovers, banners, and toasts.

Before/after screenshots are retained for human review. Automated tests assert behavior,
computed styles, and geometry rather than brittle pixel-perfect equality.

### 20.4 Functional regression

Existing controller, template, htmx, package, player-safety, encounter, map, cockpit,
accessibility, and full Maven verification remain green. Visual migration may not weaken
runtime correctness or safety gates.

## 21. Whole-redesign acceptance criteria

The redesign is complete only when:

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

## 22. Explicit non-goals

- Phone or touch-first layouts.
- Light theme.
- Replacing Thymeleaf/htmx/Alpine with a SPA.
- Adding a frontend package manager or third-party component framework.
- Changing campaign-package formats or persisted domain semantics.
- Removing cockpit presets or custom layout support.
- Automatically switching cockpit presets when runtime state changes.
- Floating windows or arbitrary recursive cockpit splits.
- Turning every feature into an identical generic dashboard.
- Removing all fantasy identity.
- Using visual polish as proof of functional all-in-one readiness.

## 23. Resolved decisions

- Systemic product redesign, not a token-only reskin or clean-slate frontend rewrite.
- Desktop/laptop only.
- 1280x720 minimum; 1440x900-1920x1080 primary; 2560x1440 large-screen gate.
- Structural navigation, layout, action-placement, and density changes are allowed.
- Existing features and backend behavior are preserved.
- Neutral charcoal foundation with warm ivory, scarce aged gold, and stable semantic colors.
- Restrained fantasy identity: one display title, narrative typography, campaign sigils,
  and selective parchment.
- Persistent collapsible left rail with task-oriented campaign groups.
- Five standard page archetypes.
- Four-zone customizable cockpit and custom presets preserved.
- Built-in cockpit presets redesigned around one unmistakable primary task.
- Map editor redesigned around command bar, tool rail, canvas, and contextual inspector.
- One semantic token system and repository-owned inline-SVG icon system.
- No database migration expected.
- Staged implementation with a whole-product release gate.
