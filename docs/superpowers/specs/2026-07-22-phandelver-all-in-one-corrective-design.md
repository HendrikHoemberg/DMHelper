# Phandelver All-in-One DM Evaluation — Corrective Design Specification

**Date:** 2026-07-22

**Status:** Approved design

**Trigger:** A live-DM walkthrough of the imported German *Die verlorenen Minen von Phandelver*
campaign, including scene operation, encounter creation, combat, player presentation, notes and
session review.

**Product premise under test:** A DM can prepare and run a complete campaign in DMHelper without
consulting books, PDFs, spreadsheets or separate map, initiative, notes, handout and character
tools.

## 1. Purpose and authority

This document records the findings from using DMHelper as an actual DM rather than inspecting its
feature list. It defines the corrective work required before the all-in-one premise can be claimed
for a representative published campaign.

This is a **master corrective specification**, not one implementation unit. Its five workstreams
must be planned, implemented and verified separately in the dependency order in section 12. The
modular cockpit in workstream B received a dedicated design review; its decisions are approved and
are authoritative for subsequent implementation planning.

This specification complements
`2026-07-15-all-in-one-dm-readiness-design.md`. Where an earlier design or walkthrough describes
the current cockpit as successful, the later live-session evidence in this document takes
precedence. Existing focused specifications remain authoritative for defects already fixed unless
this walkthrough observed a regression or a stronger release requirement.

No copyrighted campaign content is added to the repository by this design. A locally supplied
campaign may be used for acceptance testing when the operator has the right to use it. Committed
test fixtures must remain synthetic and content-free.

## 2. Evaluation method and evidence

### 2.1 Scenario exercised

The walkthrough used a private copy of the real imported campaign and performed a representative
Klarg/Cragmaw sequence:

1. open the campaign and inspect the adventure, party, encounters, maps, quests, notes and
   handouts;
2. create four party members and reload the roster;
3. select the Klarg scene and attempt to create its encounter from structured participants;
4. start a session, configure a plan and enter the cockpit;
5. operate the encounter, roll dice, apply damage, mark a creature defeated, add a condition and
   advance turns;
6. capture quick notes and present a handout to the player view;
7. activate the claimed player-safe mode;
8. review and complete the session, then inspect the generated log.

The primary viewport was 1440×1000. The new cockpit has a harder minimum requirement of
1366×768 because that is a common and more demanding table laptop.

### 2.2 Retained evidence

The local evidence directory is `artifacts/dm-evaluation/`. The most relevant captures are:

| Capture | Evidence |
|---|---|
| `02-campaign-home.png` | Strongest existing information hierarchy and visual reference |
| `12-party-reloaded.png` | Awkward sparse party cards and weak scanning |
| `13-klarg-scene-detail.png` | Readable scene content mixed with administrative controls |
| `15-active-encounter-detail.png` | Dense tracker and raw-control presentation |
| `17-cockpit-operational.png` | Oversized empty map, narrow story rail and buried lower strips |
| `20-player-handout.png` | Full scanned source page presented with DM spoilers |
| `21-session-review.png` | Lifecycle dialog rendered as unstyled document content |
| `23-cockpit-dm-mode-off.png` | Quick notes and session-plan information still visible in safe mode |

The evidence directory is intentionally not part of this specification commit because it contains
large local test artifacts. The findings below are written so that no screenshot is required to
understand or reproduce them.

### 2.3 Measured cockpit geometry

At 1440×1000 the cockpit produced a document approximately 1440×1381. The three-column grid was
about 944 px high while the story content was about 2232 px high inside its rail. The central map
surface received roughly 672×944 even though the campaign had no workspace map. Session plan,
audio and party state sat below the grid and required document scrolling.

This is not a cosmetic defect. It prevents the cockpit from functioning as a single table-time
control surface.

## 3. Executive verdict

DMHelper has unusually broad foundations: structured adventures, scene navigation, encounters,
party state, notes, handouts, quests, rules, dice, maps, live player projection and session logs
are all present. The campaign home is already a credible visual direction, and the populated scene
reader is substantially better than the original administrative presentation.

The application does **not yet fulfill the all-in-one premise** for this campaign. The walkthrough
still required working around a broken scene-to-encounter path, an unusable initiative start,
unsafe source-page presentation, a cockpit that exceeded the viewport and a malformed session
review surface. The imported content was readable but not fully operational.

For tracking improvement rather than marketing, the evaluated build is rated:

- **All-in-one readiness: 5/10.** Broad coverage, but the critical live-session path is not yet
  dependable enough to abandon source material and auxiliary tools.
- **UI/UX: 4/10.** The identity is distinctive, but the live surfaces feel like a developer/admin
  application wearing a fantasy palette. Hierarchy, density and task focus require structural
  work, not another decorative pass.

These numbers are contextual assessments, not release metrics. The executable release gate is in
section 11.

## 4. What works and must be preserved

- Campaign import/export, structured scenes, chapter grouping and scene selection provide a
  credible content foundation.
- Scene read-aloud, participant statblocks, transitions and structured checks are available
  without reconstructing them from prose.
- The encounter engine supports damage, conditions, concentration, turns, waves, defeated state,
  undo and player projection.
- Quick notes, session plans, rollable tables, rules search, audio, handouts and session review are
  already separate functional units suitable for modular composition.
- The campaign home has the clearest existing hierarchy and should be used as a reference pattern,
  not discarded.
- The dark candlelit identity can remain. The problem is indiscriminate use of the treatment, not
  the existence of the identity.
- The external player endpoint is valuable and remains the authoritative server-filtered table
  projection.
- German text, umlauts and typographic punctuation render correctly.

## 5. Finding inventory

| ID | Severity | Finding | Release effect |
|---|---|---|---|
| F1 | P0 | Scene encounter seeding responds with HTTP 500 after committing the encounter | DM sees failure while state changes underneath them |
| F2 | P0 | Test configuration does not enforce production `open-in-view=false` | Green controller test masks F1-class failures |
| F3 | P0 | Table-safe mode leaves quick notes and session-plan information visible | Explicit safety claim can expose spoilers |
| F4 | P0 | Full scanned source pages can be presented as player handouts | Player projection exposes DM text and source-page spoilers |
| F5 | P0 | Lifecycle dialog has markup but no dialog styling | Session review appears below the cockpit and breaks the workflow |
| F6 | P1 | Initiative displays em dashes with no discoverable setup action | Combat cannot begin confidently from the tracker |
| F7 | P1 | Cockpit exceeds the viewport and allocates most space to an empty map | Core runtime state is fragmented and hidden by scrolling |
| F8 | P1 | Imported campaign has no operational maps or prepared encounters | Readable conversion is not automatically runnable conversion |
| F9 | P1 | Session time can appear to run backwards across a UTC date boundary | Generated record is misleading |
| F10 | P1 | Defeated creatures can disappear from the generated session log | Review does not faithfully record table events |
| F11 | P2 | Story and encounter rails are too narrow while toolbar controls overflow | Important content is hard to scan and controls clip |
| F12 | P2 | Read/run content remains mixed with edit/admin controls | Live pages feel like CMS forms and create navigation noise |
| F13 | P2 | Party and encounter views are visually dense but informationally weak | Common table decisions take excessive scanning |
| F14 | P2 | Monotone sepia surfaces, microtext and broad serif use flatten hierarchy | Fantasy styling competes with usability |

## 6. Workstream A — Runtime correctness and player safety

Workstream A is the release blocker and must land before cockpit restructuring.

### 6.1 Transaction-safe scene encounter creation

#### Observed failure

`SceneController.loadActionRail` obtains a `Scene` through one service transaction and then calls
`SceneEncounterSeedService.canSeed(scene)` in another. `canSeed` calls `Hibernate.initialize` on
the detached `participants` collection while production has `spring.jpa.open-in-view=false`.

The seed operation itself commits in its own transaction before the controller reloads the action
rail. The reload then throws `LazyInitializationException`, so the browser receives HTTP 500 even
though the encounter and scene link now exist. A retry can therefore meet state the UI said was
never created.

#### Required behavior

- `canSeed` must not accept or initialize a detached entity. It must load by ID inside its own
  transaction or be derived as part of a hydrated scene view DTO.
- The action-rail model must be assembled inside an explicit read transaction and contain no lazy
  entity access during template rendering.
- Successful seeding returns HTTP 200 with the linked encounter and a report of added and skipped
  participants.
- Failure before completion rolls back encounter creation, combatants and the scene link.
- Retrying a completed request is idempotent and reports the existing encounter.
- The cockpit Story module exposes the same action; the DM must not leave the cockpit to create the
  encounter.

#### Regression requirements

- Test resources explicitly set `spring.jpa.open-in-view=false`; they must not rely on Spring
  Boot's default.
- A production-profile integration test detaches the scene before evaluating seed eligibility.
- A controller test posts the seed action, asserts HTTP 200 and a complete fragment, then verifies
  exactly one encounter and one set of combatants exist.
- A forced failure test proves no partial encounter remains.

### 6.2 Initiative setup as an explicit encounter phase

The tracker currently contains an `autoRoll()` client method and server endpoint but renders no
visible roll action. Initiative value `0` is displayed as an em dash through a falsy expression,
even though zero and negative initiative are valid outcomes.

Before turns can advance, the Encounter module must show a compact setup state:

- list every combatant and whether its initiative is unset;
- allow manual initiative entry per combatant;
- offer one action to roll unset NPC initiatives using authoritative modifiers;
- allow party initiatives to be entered without opening character editors;
- display zero and negative values correctly;
- offer **Start combat** only when the DM has resolved or explicitly accepted every unset value;
- preserve manual values when rolling the remaining combatants;
- explain ties and expose the resulting order before the first turn.

The domain must distinguish **unset** from numeric zero. A nullable initiative or explicit setup
state is required; rendering heuristics based on truthiness are forbidden.

### 6.3 Screen safety

Rename the cockpit's ambiguous **DM Mode** to **Screen safety**, with two explicit states:

- **Private:** all DM content is available.
- **Table-safe:** all DM-sensitive content is immediately masked and removed from keyboard focus.

Quick notes, secret session-plan beats, quest details, statblocks, unrevealed sections, hidden
tokens, encounter mechanics and unsafe handouts are sensitive by default. Read-aloud material,
revealed maps and explicitly safe handouts may remain.

The command bar adopts the existing shield-steel treatment in Table-safe state so the state is
recognizable from across the table. Every runtime module declares its sensitivity behavior in its
module contract; safety coverage must not depend on authors remembering an isolated CSS class.

The external `/player` projection remains server-filtered. A Presentation module previews the
same server projection rather than approximating it from DM markup.

### 6.4 Player-safe asset derivatives

Changing a full source page from DM-only to player-visible is not a safe presentation workflow.
Every presentable image must support explicit review and classification:

- `DM_SOURCE`: never presentable;
- `PLAYER_SAFE`: presentable as stored;
- `PLAYER_DERIVATIVE`: cropped or redacted from a DM source and presentable;
- `UNREVIEWED`: blocked from presentation.

The DM can create a non-destructive crop/redaction derivative while retaining the original and
its provenance. Before presentation, the module shows the exact player output at the player
viewport. A source page containing surrounding prose is never inferred safe merely because the
DM toggled a boolean.

Presentation of `UNREVIEWED` or `DM_SOURCE` assets is blocked by default. An emergency override is
available only from the exact player preview, requires a second explicit confirmation, records a
session audit entry and never changes the source classification.

### 6.5 Session lifecycle and log fidelity

The lifecycle fragment must render as a centered modal overlay with backdrop, bounded height,
internal scrolling, focus trapping, initial focus, Escape handling and focus restoration. It must
never enter normal document flow while open or closed.

Session time formatting must use the configured application timezone, include a timezone label
and print both dates when a session crosses midnight. A same-day range may abbreviate the second
date; a cross-day range may not.

Encounter review must retain final defeated state even when the evidence came from automatic
zero-HP handling, an explicit Defeat action or a later Revive action. The generated line is based
on the final ordered event state and is covered through the actual tracker endpoint, not only
service mocks. A completed encounter with no qualifying evidence must say so rather than silently
disappear.

## 7. Workstream B — Curated tiling session cockpit

### 7.1 Chosen model

The cockpit becomes a **dock-zone workbench**: a curated tiling system similar to an IDE workspace.
It provides the useful properties of a tiling window manager—complete viewport use, adjacent
resizing and no overlap—without arbitrary recursive splits or floating-window management.

Rejected alternatives:

- **Recursive split tree:** closer to i3/tmux but permits unusably small and difficult-to-recover
  arrangements.
- **Snap-to-grid dashboard:** visually direct but leaves gaps and responds poorly to viewport and
  zoom changes.
- **Floating windows:** maximum freedom at the cost of occlusion, z-index, focus, keyboard and
  accidental-movement problems during play.

### 7.2 Viewport and zones

The cockpit occupies the viewport below a compact command bar. The document never scrolls; each
module owns its internal scrolling.

Four dock zones are available:

1. **Primary:** dominant task, normally 50–65% of the available area.
2. **Left support:** story navigation, plan or reference context.
3. **Right support:** encounter, party or quick-note context.
4. **Bottom utility:** compact audio, dice history or session log; may collapse completely.

A zone contains one module or a tab stack. Dividers reallocate space only between adjacent zones.
Every module declares minimum dimensions, and the layout engine clamps split ratios rather than
creating broken slivers. The hard acceptance viewport is 1366×768; 1920×1080 is also tested.

### 7.3 Layout lock and editing

The workspace is fully locked by default. In locked mode module content and tab selection work,
but moving, resizing, closing, adding and reordering modules are disabled.

A persistent command-bar control switches between **Layout locked** and **Edit layout** in one
click. Edit mode exposes splitters, docking targets, tab reordering, module removal and **Add
module**. Exiting edit mode offers **Save preset** or **Discard changes** when required.

Any module can enter a temporary one-action Focus view and return without editing the preset.

Layout editing is not drag-only. Every module header also offers keyboard-operable commands to
move the module to each allowed zone and to reorder it within a tab stack. Splitters expose
`role="separator"`, their current value and arrow-key resizing in small and large increments.

### 7.4 Runtime module contract

Each module registers:

- stable module key and human title;
- Thymeleaf fragment or endpoint;
- minimum width and height;
- allowed zones;
- compact and focused capabilities;
- Screen-safety classification and safe rendering behavior;
- empty, loading, error and attention states.

The initial catalog is:

| Module | Responsibility |
|---|---|
| Story | Scene picker, full scene text, read-aloud, navigation and scene actions |
| Map | Battle map and map-specific controls |
| Encounter | Setup, initiative, HP, conditions, turns and statblocks |
| Session plan | Beats, upcoming scenes, quests and progress |
| Party | Compact party state and passive scores |
| Quick notes | One-step DM capture and promotion |
| Presentation | Exact player preview and curtain/map/handout controls |
| Reference | Global search and rules/compendium results |
| Audio | Current cue and minimal playback controls |
| Session log | Timeline, decisions, defeated creatures, notes and review |

A module appears at most once in a workspace. Dice and global search remain available through the
command bar and keyboard shortcuts; extended results or history may open as utility modules.

Deep adventure, encounter, map and character editing remains in the preparation interface. The
cockpit contains runtime modules only.

### 7.5 Manual presets

Layout changes are always deliberate. Starting combat or following a scene transition updates
module content but never rearranges the workspace. A hidden affected tab receives an attention
badge instead.

Built-in presets are editable starting points:

| Preset | Primary | Left support | Right support | Bottom utility |
|---|---|---|---|---|
| Exploration | Story | Session plan | Party / Quick notes | Audio / Log, collapsed |
| Combat | Map | Story / Party | Encounter | Quick notes / Audio |
| Theatre of Mind | Encounter | Story | Party / Quick notes | Audio / Log, collapsed |
| Presentation | Map or player preview | Story | Presentation | Collapsed |
| Session Review | Session log | Session plan | Quick notes / Party | Collapsed |

Built-ins are immutable defaults. The DM can duplicate, rename, customize and restore them.
Preset switching is manual through the command bar or keyboard shortcuts.

### 7.6 Layout architecture and persistence

The workbench is a thin layout layer around existing server-rendered modules. CSS Grid provides
the structural zones, while a focused client controller owns only docking, tab selection,
resizing, focus, locking and preset switching. Existing Alpine, htmx, Konva and server services
continue to own game state and module behavior. No third-party docking dependency or frontend
framework rewrite is introduced.

Versioned preset JSON contains:

- zone assignments and tab order;
- active tab in each zone;
- normalized splitter ratios;
- collapsed zones;
- module compact-mode preferences;
- preset name and layout schema version.

Named custom presets are durable local application data and are excluded from campaign package
export. Browser storage retains only device-specific transient state: last preset per campaign,
last active tabs and an unfinished edit draft. An invalid preset is migrated when possible and
otherwise restored to the nearest built-in with a non-blocking explanation.

Consequential state—current scene, encounter, session lifecycle, map, notes, presentation and
combat changes—remains server-owned. A preset operation can never alter it.

### 7.7 Command bar and module behavior

The compact command bar contains campaign/session identity, current preset, the one-click layout
lock, Screen safety, presentation status and an overflow menu. Actions specific to maps, handouts,
audio or encounters move into their modules, correcting the current clipped toolbar.

The primary module is spacious and visually quiet. Support modules summarize first and reveal
detail inline or in Focus view. Missing maps, encounters or plans show explanatory next actions
rather than empty surfaces. Heavy components such as Konva initialize only when visible and
suspend rendering while hidden without losing viewport state.

### 7.8 Workspace failure behavior

- One failed module shows an inline retry state without taking down the workspace.
- Existing content remains visible if a refresh fails.
- Failed preset saving keeps edit mode and preserves a recoverable local draft.
- Missing module keys are omitted with a warning instead of breaking the preset.
- Reset and discard never alter session data.
- Preset switching preserves map position, draft notes, selected scene and encounter state.
- No user action may fail silently or appear successful after a failed request.

On the populated synthetic reference campaign, the cockpit reaches its first meaningful render in
under two seconds. A preset change updates visible layout chrome within 100 ms; a lazily loaded
module shows its loading state within that interval and follows the existing two-second module
render budget. Splitter dragging must remain responsive without forcing hidden Konva canvases to
render.

## 8. Workstream C — Campaign operational fidelity

The import is structurally rich—90 scenes, quests, NPCs, threats and tables—but the evaluated
campaign began with zero maps and zero prepared encounters. Seven image assets were source-page
captures rather than purpose-built player or battle assets. A valid package is therefore not
necessarily a runnable package.

### 8.1 Readiness report

Import preview and campaign home must expose an operational-readiness report distinct from schema
validity. It reports:

- scenes with participants that can seed an encounter;
- participants missing resolvable statblocks;
- scenes requiring maps and whether a playable or reference map is linked;
- source images classified as DM-only, reviewed player-safe or unreviewed;
- encounters, quests, rollable tables, treasures and transitions with unresolved runtime links;
- content areas deliberately omitted by the converter;
- post-import actions needed before the first session.

The report may label a campaign **Valid but not session-ready**. Zero maps or zero prepared
encounters is not automatically an error, but it must be an explicit, explained choice rather than
an invisible absence.

### 8.2 Source asset semantics

Converters must not use `handout` as a generic bucket for captured pages. Every source image is
classified as a player handout, DM reference, regional map, tactical map, illustration or source
page. Where rights permit local extraction, player-safe crops and map derivatives retain
provenance back to the source locator.

A printed tactical map becomes either:

- a calibrated playable map with grid and scale metadata;
- a reviewed flat player map;
- a DM reference explicitly marked non-playable; or
- a readiness warning stating that conversion could not derive safe geometry.

The converter must never invent geometry or conceal uncertainty.

### 8.3 Encounter readiness

A hostile scene is operational when it has a linked prepared encounter or all intended combatants
can be seeded from resolved participant statblocks. The readiness report links directly to the
repair action for incomplete scenes. Scene encounter creation is available from both preparation
and the Story module.

The synthetic populated fixture must cover the field-population shape of the private package,
including statblock-linked participants, unlinked participants, safe and unsafe handouts, threats
and maps. Only counts, kinds and population booleans derived from copyrighted sources may be
committed.

## 9. Workstream D — Read/Run versus Edit/Admin information architecture

DMHelper currently exposes too many forms and management controls in the same hierarchy as the
content a DM is trying to understand or run.

### 9.1 Surface modes

- **Read:** campaign, adventure, chapter and scene content optimized for comprehension.
- **Run:** the modular cockpit and focused runtime detail layers.
- **Edit:** explicit forms for changing content.
- **Admin:** import/export, deletion, ordering, source metadata and campaign configuration.

Read and Run surfaces may link to Edit but must not embed large metadata forms above primary
content. Delete, reorder and package actions remain out of the live cockpit.

### 9.2 Targeted surface corrections

- **Scene detail:** narrative and read-aloud dominate; structured checks, participants and links
  remain concise; metadata editing is behind an explicit Edit action.
- **Encounter detail:** provide a readable preparation summary and a clear Run action; raw setup
  controls are grouped progressively rather than shown as one dense form.
- **Party:** replace tall sparse cards with a scan-friendly roster showing name, player, AC, HP,
  passives and relevant conditions; edit details remain secondary.
- **Adventure overview:** preserve chapter grouping, filtering and content affordances at
  published-campaign scale.
- **Campaign home:** retain its strong hierarchy while adding readiness status and direct Run
  entry points; do not turn it back into an inline edit form.

## 10. Workstream E — Visual-system refinement

The objective is not to remove the fantasy identity. It is to make task hierarchy stronger than
decoration.

### 10.1 Typography

- System UI font for controls, labels, values and module chrome.
- Alegreya for narrative prose and read-aloud text.
- Cinzel for at most the primary campaign, adventure or scene title in a view.
- No required runtime information below the existing `--text-sm`; use `--text-xs` only for truly
  secondary metadata.
- Numeric combat values use tabular figures and remain legible at arm's length.

### 10.2 Color and surfaces

- Gold denotes focus, selection and primary actions rather than bordering every card.
- Danger, warning, success, concentration and Screen safety retain stable semantic colors.
- Reduce nested same-color cards; use spacing, rules and surface elevation to express grouping.
- Empty space belongs to the primary task, not to empty modules.
- Motion confirms state changes but does not delay table-time actions.

### 10.3 Controls and feedback

- Forms use consistent dark controls, labels, focus rings and error placement.
- Primary actions are singular and obvious within each module.
- Save, loading, connection and error status are visible without dominating the command bar.
- Destructive actions are visually distinct and never adjacent to the most common runtime action
  without separation.
- Modals, popovers, toasts and focused detail layers follow one elevation and focus model.

## 11. Verification and all-in-one release gate

### 11.1 Automated coverage

1. **Production-parity integration:** tests explicitly use `open-in-view=false` and fail on lazy
   access after transactional service boundaries.
2. **Module contracts:** every cockpit module renders valid empty, populated, loading, error,
   compact, focused, Private and Table-safe states.
3. **Layout model:** schema migration, constraints, docking, serialization, invalid recovery and
   preset reset.
4. **Browser interaction:** edit lock, dividers, docking, tabs, focus, keyboard, preset persistence,
   module retry and viewport resize.
5. **Safety:** every built-in preset is exercised in Table-safe mode; sensitive content is neither
   visible nor focusable, and the player endpoint contains only its server projection.
6. **Asset presentation:** unsafe assets are blocked; a reviewed derivative matches the player
   preview byte-for-byte or through the same rendered endpoint.
7. **Session evidence:** a tracker-driven defeat/revive sequence and cross-midnight session produce
   a faithful log.
8. **Browser health:** no console errors, unhandled promise rejections, malformed requests or
   silent non-2xx actions during the scenario.

### 11.2 Viewport and accessibility gate

At 1366×768 and 1920×1080:

- the cockpit has no document-level scrolling;
- modules do not overlap or clip;
- the command bar remains fully reachable;
- minimum module sizes are respected;
- keyboard users can enter/exit edit mode, choose tabs, focus/restore modules and operate
  splitters;
- focus is visible and restored after dialogs and focused layers;
- reduced-motion preference is respected.

### 11.3 Representative Phandelver release rehearsal

The release candidate must complete, from a fresh imported copy:

1. inspect the readiness report and resolve its blockers;
2. start or resume a session at a selected scene;
3. navigate exploration and a branch without leaving the cockpit;
4. create or activate the Klarg encounter from the scene;
5. resolve initiative, damage, conditions, defeated state and multiple turns;
6. consult the required statblocks and rules inside DMHelper;
7. capture notes and update the session plan;
8. present a reviewed player-safe map or handout and verify the external display;
9. complete the encounter and session;
10. verify that the generated log accurately records time, scenes, encounter outcome, defeated
    creatures and unresolved notes.

During the rehearsal the DM may not use a book, PDF, spreadsheet or separate initiative, map,
rules, note or presentation tool. External source material may be opened only after the run to
audit conversion fidelity.

The gate fails if:

- any required information is absent from DMHelper;
- any critical runtime action requires navigating to an Edit/Admin surface;
- a prepared element requires more than two deliberate actions from the cockpit;
- a request fails or partially commits behind an error response;
- the DM cannot identify the current scene, turn, encounter, presentation state or save/error
  state without scrolling;
- player-visible output contains unreviewed or DM-only information;
- the session log disagrees with the actions performed.

Passing this rehearsal supports the premise for the representative scenario, not every published
campaign. At least one additional synthetic campaign with different branching, map and encounter
shapes must pass before a general all-in-one readiness claim.

## 12. Delivery decomposition and dependency order

This master specification decomposes into separately reviewed implementation plans:

1. **A1 — Runtime transaction and test parity:** F1, F2 and no-partial-commit behavior.
2. **A2 — Safety, presentation derivatives and lifecycle fidelity:** F3–F5, F9 and F10.
3. **A3 — Initiative setup:** F6 and its nullable/explicit setup model.
4. **B1 — Cockpit layout foundation:** module registry, zones, lock, presets and persistence.
5. **B2 — Runtime module migration:** Story, Encounter, Map, Party and Presentation first;
   utilities second.
6. **C — Operational-readiness report and campaign conversion fidelity.**
7. **D — Read/Run versus Edit/Admin surface separation.**
8. **E — Visual-system refinement and final release rehearsal.**

Workstream A precedes B so the new cockpit does not preserve broken behavior inside better
containers. B precedes broad visual polish because its information architecture determines the
surfaces to style. Campaign fidelity may proceed after A in parallel with B only when its work does
not modify shared cockpit fragments.

Each implementation plan must define its own database migration, rollback, test selection and
manual acceptance checkpoint. No plan may claim the overall premise until section 11 passes.

## 13. Explicit non-goals

- Floating or overlapping cockpit windows.
- Arbitrary user-created recursive splits.
- Automatic preset switching when an encounter or scene changes.
- Embedding deep Edit/Admin pages as cockpit modules.
- Player accounts or player-controlled character sheets and tokens.
- Inventing map geometry, encounter statistics or missing copyrighted content.
- Committing the private Phandelver package, source PDF or screenshot evidence.
- Replacing the server-rendered Spring/Thymeleaf/htmx/Alpine architecture with a SPA.
- Adding a general frontend package manager or third-party docking framework solely for layout.
- Treating visual polish as sufficient proof of all-in-one readiness.

## 14. Resolved design decisions

- Curated dock-zone workbench, not floating windows or a full tiling tree.
- Focus-plus-context composition, not a dense equal-weight dashboard.
- Runtime modules only.
- Manual presets only; no automatic layout switching.
- 1366×768 minimum viewport.
- Layout fully locked by default, including dividers.
- One-click entry to and exit from edit mode.
- Durable named presets in local application data; transient device state in browser storage.
- Existing visual identity retained but subordinated to runtime hierarchy.
- This document is a master corrective spec with multiple implementation plans, not a monolithic
  delivery task.
