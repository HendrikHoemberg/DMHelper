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

Edit the draft freely, then provide a title and click **Complete**. A `SESSION_LOG` note is created, the player view is curtained, and the session resets to `IDLE`.

## Current Scene & Active Encounter

The **current scene** (set via the story rail) and the **active encounter** (set via the encounter rail) are independent state. The cockpit shows both; switching one does not automatically switch the other. A one-click **Switch** action appears when the workspace map differs from the current scene or active encounter map.

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
