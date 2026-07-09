# M11 — Dice Roller Design

**Date:** 2026-07-09
**Status:** design approved
**Source spec:** SPEC.md §4.16, §2.3.9, milestone table §7 (M11)

---

## 1. Scope

M11 adds a server-side dice roller with a floating panel accessible from every screen, clickable
roll buttons on statblocks and character sheets, and combat-log integration. The roller is
**optional-first** (§2.3.9): every integration point offers both a roll action and a typed numeric
input, so physical dice remain the first-class path.

**Definition of done:** Run a fight rolling digitally *and* typing physical rolls interchangeably.

---

## 2. Architecture

### 2.1 Dice Engine (`dice/DiceEngine.java`)

Stateless Java utility. Parses dice expression strings and rolls using `java.util.Random`.

**Grammar:**
- `XdY` — X dice with Y sides (e.g., `2d6`, `d20` is `1d20`)
- `XdY+Z` / `XdY-Z` — modifier arithmetic (e.g., `2d6+4`, `3d8-2`)
- `d20 adv` — roll twice, take highest (`advantage: true` in result)
- `d20 dis` — roll twice, take lowest (`disadvantage: true` in result)
- Plain integer — accepted as a typed input ("physical dice"); returns total with empty rolls

**Error handling:** Invalid expressions (non-numeric, unrecognized syntax) throw
`IllegalArgumentException`, mapped to HTTP 400 (Problem Detail) by the controller.

**Result record** (`DiceResult`):
```java
public record DiceResult(
    String expression,
    List<DieRoll> rolls,     // {die: "d6", values: [3, 5]}
    int modifier,
    int total,
    boolean advantage,
    boolean disadvantage
) {}
public record DieRoll(String die, List<Integer> values) {}
```

### 2.2 Dice Service (`dice/service/DiceService.java`)

Orchestrates engine execution, history persistence, and combat log integration.

- `roll(String expression, UUID encounterId)` → `DiceResult`
  1. If expression is a plain integer → typed-input path: `new DiceResult(expression, [], 0, value, false, false)`
  2. Otherwise → `DiceEngine.roll(expression)`
  3. Save `DiceRoll` entity (history)
  4. If `encounterId != null` → call `EncounterService.logEntry(DICE_ROLL, ...)`
  5. Return result

- `getHistory()` → last 20 `DiceRoll` entities ordered by `createdAt DESC`

### 2.3 Persisted History (`dice/data/DiceRoll.java`)

JPA entity. Not campaign data; not exported/imported. Purged beyond last 20 per query (not per
cleanup job — the repository query caps at 20; the table can accumulate but only the most recent
are shown).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `expression` | String | e.g. `"2d6+4"` or `"typed: 12"` |
| `rolls` | JSON (CLOB) | `[{"die":"d6","values":[3,5]}]` |
| `modifier` | int | |
| `total` | int | |
| `advantage` | boolean | |
| `disadvantage` | boolean | |
| `encounterId` | UUID? | nullable; set when roll occurs during active encounter |
| `createdAt` | Instant | |

### 2.4 Dice API Controller (`dice/web/DiceApiController.java`)

| Endpoint | Method | Request | Response |
|---|---|---|---|
| `/api/v1/roll` | POST | `{"expression": "2d6+4"}` | `DiceResult` JSON |
| `/api/v1/roll` | POST | `{"expression": "15"}` | typed-input `DiceResult` |
| `/api/v1/roll/history` | GET | — | `List<DiceRoll>` JSON (last 20) |

The POST endpoint accepts an optional `encounterId` field. If provided (frontend detects active
encounter), the roll is logged to the combat log in addition to the history table.

**PIN gate:** All `/api/v1/roll*` endpoints require the session PIN (§2.3.7).

**Error response:** Invalid expression → `400 Problem Detail` with descriptive `detail` field.

---

## 3. Combat Log Integration

### 3.1 New Entry Type

`DICE_ROLL` added to `CombatLogEntry.EntryType` enum.

Payload:
```json
{"expression": "2d6+4", "total": 12, "rolls": [{"die": "d6", "values": [3, 5]}]}
```

Typed-input variant:
```json
{"expression": "typed: 15", "total": 15, "rolls": []}
```

### 3.2 Undo

`replayEntry()` in `EncounterService` handles `DICE_ROLL` as a no-op — dice rolls are
informational entries that don't change encounter state. Deleting/omitting them during undo
has no side effects.

### 3.3 Active Encounter Detection

The frontend detects an active encounter via the combat tracker's Alpine component state. When
the dice roller panel dispatches a roll, it includes the active encounter ID from
`combatTracker.encounter.id`. If no encounter is active, `encounterId` is null and no combat log
entry is created.

### 3.4 Export/Import

No changes. `DiceRoll` entities are not campaign data and are not exported. `DICE_ROLL` entries
in the combat log are exported/imported as part of the existing `CombatLogEntry` export path —
the new enum value is handled automatically since the export reads `entry.type.name()` and JSON
payloads are stored as-is.

---

## 4. User Interface

### 4.1 Floating Dice Roller Panel

**Technology:** Alpine.js component `diceRoller()` loaded globally in `navbar.html`.

**Placement:** Embedded in `navbar.html` so it's available on every page without per-template edits.

**State:**
```javascript
{
    open: false,
    expression: '',
    advantage: false,
    disadvantage: false,
    result: null,      // DiceResult JSON
    history: [],       // last 20 rolls
    loading: false
}
```

**Layout** (320px wide, slides from right):
```
┌──────────────────────────┐
│ 🎲 Dice Roller       [✕] │
├──────────────────────────┤
│ [2d6+4        ] [Roll]   │
│ ☐ Adv  ☐ Dis             │
├──────────────────────────┤
│ Result: 12                │
│ ➜ d6: [3] [5] + 4 = 12  │
├──────────────────────────┤
│ History                   │
│ 2d6+4 → 12    just now   │
│ d20 adv → 17   2m ago    │
│ typed: 8 → 8   5m ago    │
│ ...                       │
└──────────────────────────┘
```

**Behaviors:**
- Toggle: button in navbar-right (dice icon) + keyboard shortcut `Ctrl+R`
- `Enter` in expression input → roll
- `Adv`/`Dis` checkboxes mutually exclusive (toggling one unchecks the other); only valid with
  d20 expressions
- Result animates in (brief highlight)
- History auto-refreshes on each roll; scrollable
- Panel closes on click-outside or `Esc`
- `x-show="open"` controlled by Alpine state; renders via `x-html` for result details
- Roll dispatches `CustomEvent('dice-roll-result', { detail: result })` for other components

**DM Mode:** Panel and toggle button hidden when `body.dm-mode-off` is active (CSS rule).

### 4.2 Statblock Roll Buttons

**Modified file:** `library/_statblock-renderer.html`

Each action/bonus action/reaction entry gets two small icon buttons appended:

| Button | Target | Expression |
|---|---|---|
| `d20` | Attack roll | `d20 + <parsed to-hit modifier>` from action name text |
| `d6` | Damage roll | `<parsed damage expression>` from action name text |

**Parsing logic:** Simple regex on the action `name` field:
- Attack: `/+(\d+) to hit/` → extracts modifier → `d20+N`
- Damage: `/\((\d+d\d+[+-]?\d*)\)/` → extracts dice expression → `XdY+Z`

If parsing fails for an action, the button is suppressed (no false rolls). Homebrew actions the
DM writes with standard 5.5e phrasing work; non-standard phrasing gets no button.

**Behavior:** Clicking fires `POST /api/v1/roll` via fetch, displays result in a tooltip/popover
next to the button for 3 seconds, then dismisses. Also dispatches `dice-roll-result` event.

### 4.3 Character Sheet Roll Buttons

**Modified files:** `sheet/detail.html`, `sheet/_ability-scores.html`, `sheet/_derived-stats.html`

Each derived value gets a small `d20` icon-button:

| Location | Roll | Expression source |
|---|---|---|
| Ability score mods | Ability check | `d20 + DerivedValues.{str|dex|con|int|wis|cha}Mod()` |
| Saving throws | Save roll | `d20 + DerivedValues.save{Str|Dex|...}()` |
| Skills list | Skill check | `d20 + DerivedValues.skillBonuses().get(skillName)` |
| Derived stats | Initiative | `d20 + DerivedValues.initiativeBonus()` |
| Spellcasting | Spell attack | `d20 + DerivedValues.spellAttackBonus()` |
| Hit dice row | Hit die roll | `d{N}` where N is the class hit die (from `CharacterClass.hitDie`) |

All modifiers come from the `SheetDto.derivedValues()` — no re-parsing needed, values are
pre-computed by `SheetEngine`.

**Behavior:** Same as statblock buttons — fetch to `/api/v1/roll`, tooltip result, event dispatch.

---

## 5. CSS

Additions to `static/css/app.css` (~50 lines):

- `.dice-panel` — fixed position, right: 0, top: 0, bottom: 0, 320px width, `var(--color-surface)`
  background, `var(--color-border)` left border, z-index: 100
- `.dice-panel.closed` — transform translateX(100%), transition
- `.dice-result` — bold total, green highlight for high d20 rolls (≥15), red for low (≤5)
- `.dice-history-item` — row with expression, arrow, total, timestamp
- `.dice-roll-btn` — small icon button, 24px, `var(--color-accent)` hover
- `.dice-roll-tooltip` — absolute popover, dark surface, brief appearance
- DM Mode rule: `body.dm-mode-off .dice-panel, body.dm-mode-off .dice-toggle-btn { display: none; }`

---

## 6. File Plan

### New Files (6 Java + 1 Thymeleaf)

| File | Purpose |
|---|---|
| `dice/DiceEngine.java` | Expression parser + roller |
| `dice/DiceResult.java` | Result record |
| `dice/data/DiceRoll.java` | JPA history entity |
| `dice/data/DiceRollRepository.java` | Spring Data repository |
| `dice/service/DiceService.java` | Orchestration + combat log |
| `dice/web/DiceApiController.java` | REST endpoints |
| `fragments/_dice-roller.html` | Alpine.js panel + styles inline |

### Modified Files (7 existing)

| File | Change |
|---|---|
| `fragments/navbar.html` | Toggle button + embed `_dice-roller` fragment |
| `library/_statblock-renderer.html` | Attack + damage roll buttons per action |
| `sheet/detail.html` | Roll buttons on saves, skills, spellcasting values |
| `sheet/_ability-scores.html` | Roll buttons on ability mods |
| `sheet/_derived-stats.html` | Roll buttons on initiative |
| `encounter/data/CombatLogEntry.java` | Add `DICE_ROLL` to EntryType |
| `encounter/service/EncounterService.java` | No-op `DICE_ROLL` case in `replayEntry()` |
| `static/css/app.css` | Panel + roll button styles (~50 lines) |

### No Changes

- No new dependencies (Maven `pom.xml` unchanged — `java.util.Random` and Jackson are already on classpath)
- No schema migration needed (Hibernate `ddl-auto=update` handles new entity; enum addition is additive)
- No changes to export/import
- No changes to player view or WebSocket

---

## 7. Testing Strategy

### Unit Tests (`DiceEngineTest`)
Table-driven, 15+ cases:
- `"2d6+4"` → result has 2 d6 rolls, modifier 4, total in [6, 16]
- `"d20"` → 1 d20 roll, total in [1, 20]
- `"3d8"` → 3 d8 rolls, modifier 0
- `"1d4-1"` → total in [0, 3]
- `"d20 adv"` → 2 d20 rolls, `advantage: true`, total = max of the two
- `"d20 dis"` → `disadvantage: true`, total = min of the two
- `"5d10+3"` → total in [8, 53]
- `""` → throws `IllegalArgumentException`
- `"invalid"` → throws `IllegalArgumentException`
- `"d20 + 5"` (spaces) → handles or rejects consistently
- `"2d6+-3"` → rejects (invalid syntax)
- `"d100"` → valid, total in [1, 100]
- Roll distribution sanity: 10,000 d20 rolls → each face within 3σ of expected

### Unit Tests (`DiceServiceTest`)
- Roll with valid expression → history saved, result returned
- Roll during active encounter → combat log entry created
- Roll with null encounter → no combat log entry
- Typed input `"15"` → typed result, empty rolls
- History returns capped at 20

### Integration Tests (`DiceApiControllerTest` — `@WebMvcTest`)
- `POST /api/v1/roll` valid expression → 200 with correct `DiceResult` structure
- `POST /api/v1/roll` invalid expression → 400 Problem Detail
- `POST /api/v1/roll` typed input → 200, no rolls, total = input value
- `GET /api/v1/roll/history` → 200 with array
- All endpoints reject requests without PIN → 401/403

### Integration Tests (Combat Log — `EncounterServiceTest` or dedicated test)
- Roll during active encounter → `DICE_ROLL` entry in combat log
- Undo removes `DICE_ROLL` entry
- Roll when no encounter active → no log entry

### No Frontend Tests
The Alpine.js panel is straightforward show/hide + fetch interactions. Covered by the M12
Playwright smoke test when it adds the full session loop.

---

## 8. Implementation Order

Tasks in dependency order:

1. `DiceResult` + `DieRoll` records
2. `DiceEngine` + unit tests
3. `DiceRoll` entity + `DiceRollRepository`
4. `DiceService` + unit tests (mocking repo + encounter service)
5. `DiceApiController` + integration tests
6. `CombatLogEntry.EntryType` + `DICE_ROLL` + `replayEntry()` no-op
7. CSS additions for panel + roll buttons
8. `_dice-roller.html` Alpine.js panel fragment
9. `navbar.html` integration (toggle + embed)
10. `_statblock-renderer.html` roll buttons
11. Sheet template roll buttons (saves, skills, abilities, initiative, spell attack)
12. Manual integration testing (full session flow)
