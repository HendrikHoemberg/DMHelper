# Session Cockpit

The session cockpit at `/campaigns/{campaignId}/session` is the central during-play interface.

## Layout

The cockpit is organised into five areas:

| Area | Purpose |
|------|---------|
| **Story rail** (left sidebar) | Current scene, editorial neighbours, scene links, linked rollable tables, scene quick notes |
| **Table surface** (centre) | Workspace battle map with token, measurement, and AoE tools |
| **Encounter rail** (right sidebar) | Active encounter tracker, planned encounters list |
| **Session plan** (bottom strip) | Ordered prepared beats parsed from the latest `SESSION_PLAN` note |
| **Party bar** (footer) | Party member summary with HP bars, AC, passive perception |
| **Quick access toolbar** (top bar) | Search, dice/table rollers, handouts, rules reference, calendar, session lifecycle |

## Screen Safety

The cockpit provides two display modes controlled via the checkbox toggle (`Ctrl+Shift+D`):

| Mode | Badge | Behaviour |
|------|-------|-----------|
| **Private** | *none* | Full DM interface shown. All content visible. |
| **Table-safe** | `Table-safe` | Content marked `data-screen-sensitive` is hidden and not focusable via Tab. |

### What disappears

When table-safe is active:

- Scene summaries, body text, and transitions
- Quest progress panels and rewards
- NPC secrets and faction goals
- Scene checks, participants, and treasure sections
- Encounter mechanics and threat cards
- Any element tagged `data-screen-sensitive`

### What stays visible

- **Read-aloud text** — this is the one section kind a DM is meant to show or read to the table; tagging it as sensitive would defeat the feature.
- **Player preview content** — any element rendered specifically for player consumption.
- **The story rail module root** — remains as a visible container.

## Timezone

Session times in the end-review draft and saved log are formatted in the server-configured timezone. When a session starts before midnight and ends after, the formatting handles the cross-midnight boundary correctly: start and end times are computed from the server's configured zone, not UTC.

## Lifecycle

### Start

Click **Session** → **Start Session**. The status changes to `RUNNING`. The workspace map is selected automatically (priority: active encounter map, current scene map, first session plan link, campaign default).

### Pause / Resume

Click **Session** → **Pause** (status becomes `PAUSED`). Click **Resume** to continue. Session state survives refresh, restart, and browser close.

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

Edit the draft freely, then provide a title and click **Complete**. A `SESSION_LOG` note is created, the player view is curtained, and the session resets to `IDLE`.

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
creatures. DM-only threat map pins are managed from the cockpit map sidebar; pins never appear on
the player table. See [08-traps-and-hazards.md](08-traps-and-hazards.md).

## Session Log Draft

The draft is generated on first end-review and contains only what persisted data can prove. Session logs are normal `SESSION_LOG` notes after completion.

## Keyboard Actions

When no modal or input is focused:

| Key | Action |
|-----|--------|
| `[` / `]` | Step to previous / next scene |
| `n` | Advance encounter turn |
| `q` | Focus quick notes input |
| `h` | Focus handout picker |
| `p` | Present current map to player table |

All cockpit routes are covered by the PIN interceptor.
