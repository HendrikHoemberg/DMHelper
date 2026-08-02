# Session Cockpit

The session cockpit at `/campaigns/{campaignId}/session` is the central during-play interface.

## Layout workbench

The play surface is a four-zone workbench owned by the viewport. The **document itself does not scroll**; each module scrolls internally.

| Zone | Role |
|------|------|
| **Primary** (centre) | Dominant stage for the active table surface (Map, Encounter, Session log, …) |
| **Left support** | Secondary rail (Story, Session plan, Party, …) |
| **Right support** | Secondary rail (Encounter, Reference, Party, …) |
| **Bottom utility** | Compact strip (Quick notes, Audio, Session log, …) that may collapse |

Nine modules ship in the registry: Story, Map, Encounter, Party, Session plan, Quick notes, Reference, Audio, and Session log. Every module appears at most once.

Command chrome (identity, preset picker, Edit layout, Search, Dice, Session) stays reachable above the workbench.

### Locked default and Edit layout

Every page load starts **locked**. While locked:

- dividers, Add module, Remove, docking, and reorder are unavailable;
- Story / Encounter / Map content remain fully operable;
- combat and scene changes **never** switch layouts — hidden or inactive modules only receive **attention badges**.

Click **Edit layout** once to unlock layout chrome. Click **Done** (the same control) once to leave edit:

- no changes → locks immediately;
- dirty layout → Save preset / Discard changes dialog.

**Save preset** writes a named custom preset (or updates the open custom one). **Discard changes** restores the snapshot from when you entered edit and leaves all campaign/session state untouched.

### Built-in presets and shortcuts

Four immutable built-ins ship with the app:

| Shortcut | Preset | Typical primary |
|----------|--------|-----------------|
| `Alt+Shift+1` | Exploration | Story |
| `Alt+Shift+2` | Combat | Map |
| `Alt+Shift+3` | Theatre of Mind | Encounter |
| `Alt+Shift+4` | Session Review | Session log |

Shortcuts apply only when no modal or text field owns the keystroke. Preset changes are always manual; layout chrome settles within 100 ms.

### Custom presets

Use the overflow actions to **Duplicate**, **Rename**, **Delete**, or **Restore** a built-in after local edits. Custom presets persist in local application data (not inside a campaign package). They never ride campaign export/import.

### Add, arrange, dock, and separators

In edit mode:

- **Add module** opens a dialog of modules not currently placed (no duplicates);
- each module’s **Arrange** menu is the keyboard equivalent of pointer docking (zone moves, earlier/later tab order);
- drag a module header onto a zone dock target for pointer docking;
- Map cannot leave Primary; Primary always keeps at least one module;
- separators resize only adjacent zones and clamp to ratio plus module minima (`aria-valuenow` updates; arrows step 2%, Shift+arrows 10%).

### Focus and Return

Modules that support Focus open a full-workbench focus layer. **Return** (or Escape) restores the previous layout and puts keyboard focus back on the Focus control. Focus never mutates a named preset.

### Device-only recovery

Last preset, active tabs, and unfinished edit drafts live in browser `localStorage` keys under `dmhelper.cockpit.*`. Corrupt values are ignored with a non-blocking notice. Failed preset saves keep edit mode and the recoverable draft.

## Module responsibilities

Nine runtime modules ship in the cockpit registry. Four modules are server-rendered for first paint: Session plan, Story, Party, and Quick notes. The other five — Map, Encounter, Reference, Audio, and Session log — load from their module endpoints on first visibility. When a restored preset requires a different mode, a server-rendered body is refetched before use.

| Module | Responsibility | Endpoint | Initial delivery |
|--------|---------------|----------|------------------|
| **Story** | Current scene, editorial neighbours, scene links, linked rollable tables, scene quick notes | story | server-rendered |
| **Map** | Workspace battle map with token, measurement, and AoE tools | map | first visibility |
| **Encounter** | Active encounter tracker, planned encounters list | encounter | first visibility |
| **Session plan** | Ordered prepared beats parsed from the latest `SESSION_PLAN` note | session-plan | server-rendered |
| **Party** | Party member summary with HP bars, AC, passive perception | party | server-rendered |
| **Quick notes** | Create, view, and resolve quick notes during play | quick-notes | server-rendered |
| **Reference** | Rules reference, spells, conditions lookup | reference | first visibility |
| **Audio** | Ambient music, soundscapes, and encounter-linked cues | audio | first visibility |
| **Session log** | Running timeline of events during the session | session-log | first visibility |

### Lazy loading and retry behaviour

- Map, Encounter, Reference, Audio, and Session log load their bodies via a `GET` to `/campaigns/{cid}/session/modules/{key}?mode=STANDARD|COMPACT` on first visibility.
- Server-rendered bodies are reused only when their `data-module-mode` matches the restored preset. A mismatch is refetched before the body is shown.
- The shell shows a **live region** (`aria-live="polite"`) during loading without moving focus.
- On fetch failure the shell persists the loaded body, shows an **error banner** (`role="alert"`), and makes a **Retry** button keyboard-reachable.
- A rejected Retry shows a new error — the module does not unload or fall back to an empty shell.
- Rapid preset switching never triggers duplicate requests for the same module.

### Compact vs Focus behaviour

- `COMPACT` is **not** derived from the zone. Each preset names the modules it wants condensed in its own `compactModuleKeys` set (`CockpitBuiltInPresetCatalog.java`); everything else renders `STANDARD`. Exploration condenses Session plan, Party, Audio and Reference. Combat condenses Story, Party, Quick notes, Reference, Audio and Session log. Theatre of Mind condenses Party, Reference, Quick notes, Audio and Session log. Session Review condenses Session plan, Quick notes and Party.
- A `COMPACT` Story module shows the scene title and read-aloud text only — its summary, DM notes, sections and participants are `STANDARD`-only. That is deliberate: in Combat the Story rail is a prompter, not a reference.
- Modules that support **Focus** open a full-workbench overlay. **Return** (or `Escape`) restores the previous layout and returns keyboard focus to the Focus trigger.

### Where actions live

| Action | Location |
|--------|----------|
| **Map** controls | Map module (Primary in combat preset) |
| **Encounter** tracker + planned encounters | Encounter module (Right support in combat preset) |

| **Reference** lookup | Reference module (via top-bar Search or `?` shortcut) |
| **Audio** widget | Audio module (Bottom utility tab in combat preset) |
| **Quick notes** | Quick notes module (Bottom utility default tab) |
| **Session log** timeline | Session log module (Primary in session-review preset) |

### No automatic preset switching

Combat and scene changes never switch the active preset. Hidden or inactive modules receive **attention badges** instead — the DM must choose to switch presets manually (picker, keyboard shortcut, or edit-mode add).

### Runtime vs Edit/Admin boundary

- Runtime module bodies are rendered by dedicated `CockpitRuntimeModuleController` endpoints and do **not** depend on the larger campaign edit view model.
- Edit/Admin pages (maps, encounters, handouts, party, library, notes) exist on separate URL trees and are not part of the session cockpit runtime.
- The command palette, dice panel, session lifecycle dialog, and keyboard shortcut overlay are **top-bar actions**, not module content.

### Keyboard workflow

| Key | Action |
|-----|--------|
| `Alt+Shift+1`…`4` | Select built-in preset |
| `[` / `]` | Step previous / next scene |
| `n` | Advance encounter turn |
| `q` | Focus quick notes input |
| `?` | Open keyboard shortcut help |
| `Escape` | Close topmost layer and restore focus trigger |
| Arrow keys / `Tab` | Navigate zone tabs and module chrome |

## Timezone

Session times in the end-review draft and saved log are formatted in the server-configured timezone. When a session starts before midnight and ends after, the formatting handles the cross-midnight boundary correctly: start and end times are computed from the server's configured zone, not UTC.

## Lifecycle

### Start

Click **Session** → **Start Session**. The status changes to `RUNNING`. The workspace map is selected automatically (priority: active encounter map, current scene map, first session plan link, campaign default).

### Pause / Resume

Click **Session** → **Pause** (status becomes `PAUSED`). Click **Resume** to continue. Session state survives refresh, restart, and browser close.

### Discard without a log

Use **Session** → **Discard session** to abandon the current run. No `SESSION_LOG` note is created. Campaign changes remain; session visits, the end-review draft, audio runtime state, and presentation/session bookkeeping are removed before the session returns to `IDLE`.

### End Review

Click **End Review** to generate a deterministic Markdown draft containing:
- Start/end time and in-game date
- Attendance (party members)
- Recorded scenes (with completion timestamps)
- Encounters with an `ENCOUNTER_ENDED` log during the session
- Defeated combatant names, summed damage
- Ledger rows and unresolved quick notes created during the session
- Free-form recap and next-session hooks
- Any `PRESENTATION_OVERRIDE` audit entries (handout title and original classification)

### Defeated / Revived Evidence

The draft reconstructs per-combatant final state by replaying the combat log for each ended encounter. For each combatant, the draft records the **latest** entry in the log:

| Last entry type | Draft result |
|----------------|--------------|
| `DEFEATED` | Combatant is listed as defeated |
| `REVIVED` | Combatant is NOT listed (alive at encounter end) |
| `DEFEATED` after a `REVIVED` | Combatant is listed as finally defeated |

This means a token that was defeated, revived, and then defeated again appears in the draft as defeated with the correct final state.

Edit the draft freely, then provide a title and click **Complete**. A `SESSION_LOG` note is created, session-only runtime state is cleared, and the session resets to `IDLE`.

## Current Scene & Active Encounter

The **current scene** (set via the story rail) and the **active encounter** (set via the encounter rail) are independent state. The cockpit shows both; switching one does not automatically switch the other. A one-click **Switch** action appears when the workspace map differs from the current scene or active encounter map.

### Starting an encounter from the current scene

When the current scene contains at least one participant linked to a statblock and has no linked
encounter, the Story module shows **Start encounter from this scene**. The action creates one
encounter, adds the resolved participant quantities, links it back to the scene, and refreshes the
Story and Encounter modules.

Participants without statblocks are named in the result instead of disappearing or receiving
invented statistics. Add those participants manually if they should enter combat. Repeating the
action is safe: DMHelper reports the existing linked encounter and does not duplicate combatants.

## Initiative setup

Activating an encounter opens Initiative setup before any turn begins.

1. Enter the party's rolled initiatives directly in the Encounter module.
2. Enter any manual NPC values you want to preserve.
3. Choose **Roll unset NPCs** to roll only the remaining non-player combatants.
4. Review the displayed order. Initiative 0 and negative values are valid; an em dash means unset.
5. Resolve every unset value, or explicitly check **Start with … unset**. Accepted unset combatants remain last in the displayed order.
6. Choose **Start combat**. Round 1 begins with the first eligible combatant active.

Ties use the displayed order: higher tie-breaker first, then the existing order, then name.

## Rollable Tables

Tables linked directly to the current scene, or through one of its linked world locations, appear in
the story rail. Clicking **Roll** there is the immediate one-action path. The top-bar table picker
opens the shared roll panel for manual range results, multiple rolls, and duplicate policy. Results,
nested outcomes, unavailable-history markers, and pending encounter/reward drafts remain visible in
the cockpit; confirming or discarding a draft is always a separate DM action.

## Traps and Hazards

Scene sections with kind `TRAP` or `HAZARD` that reference a threat definition render a mechanics
card in the story rail. Clicking detection/check/attack/damage **Prefill** buttons opens the shared
dice roller with the expression filled — they do not submit a roll.

When the active encounter turn is a trap or hazard combatant, the encounter rail shows the same
mechanics card. Resolution remains manual: use existing tracker damage and condition controls on
creatures. DM-only threat map pins are managed from the cockpit map sidebar and remain visible only to the DM. See [08-traps-and-hazards.md](08-traps-and-hazards.md).

## Session Log Draft

The draft is generated on first end-review and contains only what persisted data can prove. Session logs are normal `SESSION_LOG` notes after completion.

## Keyboard Actions

When no modal or input is focused:

| Key | Action |
|-----|--------|
| `Alt+Shift+1`…`4` | Select built-in Exploration / Combat / Theatre of Mind / Session Review |
| `[` / `]` | Step to previous / next scene |
| `n` | Advance encounter turn |
| `q` | Focus quick notes input |
| Arrow keys on zone tabs | Move selection among tabs in that zone |
| Escape | Close the topmost layer (focus, dialog) and restore its trigger when possible |

The server binds to `127.0.0.1` only; loopback binding is the access control for all cockpit routes.
