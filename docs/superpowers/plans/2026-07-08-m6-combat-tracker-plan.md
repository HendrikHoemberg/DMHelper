# M6: Combat Tracker — Implementation Plan

## Overview

M6 delivers the initiative & combat tracker (§4.5 of SPEC.md): the docked panel beside the battle map that runs encounters from a blinking cursor to a defeated boss. Everything in M6 happens on the DM screen — the player-view projection for the tracker is scoped to M7.

### What M6 includes (from SPEC §7)

| Feature | Scope |
|---|---|
| Encounters (CRUD, PLANNED/ACTIVE/DONE states, map linkage) | Full implementation |
| Initiative (roll/type, sort, tie-break, drag to reorder) | Full |
| Monster groups (shared initiative, per-creature HP, split-out) | Full |
| Turn management (next/previous, round counter, active highlight) | Full |
| HP math (damage/healing quick-math, death handling) | Full |
| Conditions (toggle, effect durations, compendium text integration) | Full |
| Concentration (flag, DC calc, save prompt on damage) | Full |
| Legendary/lair actions (per-round counter, resistance counter, lair @ init 20) | Full |
| Combat log with undo (append-only, undo by replay) | Full |
| Difficulty calculator (XP budget from active roster + selected monsters) | Full |
| Map + roster linkage (build encounter from tokens, prefill from party) | Full |

### What M6 does NOT include

- Player view of the tracker (M7)
- Notes, wiki, quicknotes (M8)
- Character sheets (M9 — PartyMember already has sufficient combat fields)
- Dice roller integration (M11 — initiative auto-roll is a stand-in using `Random`, not the M11 roller)
- Campaign import/export of encounters (M12 — reserve DTO slots only)

### Strategy

The tracker is a hybrid component: encounter CRUD and HP mutations go through **htmx** (server-rendered fragments), while the live initiative panel uses **Alpine.js** reactivity (no round-trips per turn change). The combat log is append-only, enabling undo as a deterministic replay from log entry 0 through N-1 (like an event-sourced aggregate, but simpler).

---

## 1. Entity Design

Three new JPA entities in a new `encounter` package, following existing entity patterns (see `Token.java`, `GameMap.java`).

### 1.1 `Encounter` (`src/.../encounter/data/Encounter.java`)

```java
@Entity
@Table(name = "encounter", indexes = {
    @Index(name = "idx_encounter_campaign", columnList = "campaign_id"),
    @Index(name = "idx_encounter_map", columnList = "map_id"),
})
public class Encounter {
    enum Status { PLANNED, ACTIVE, DONE }

    @Id @GeneratedValue(strategy = GenerationType.UUID) UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false) Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id") GameMap map;  // nullable — encounters can be planned without a map

    @Column(nullable = false, length = 255) String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) Status status = Status.PLANNED;
    @Column(nullable = false) int round = 0;       // 0 = not started; increments when turn wraps
    @Column(nullable = false) int activeTurnIndex = -1; // index into sorted combatants list
    @Column(nullable = false) long logSequence = 0; // auto-increment for CombatLogEntry ordering
    @Column(length = 255) String lairActionName;    // name of lair action (if any)
    @Column(columnDefinition = "CLOB") String lairActionDescription;
}
```

Key decisions:
- `campaign` is required (every entity belongs to a campaign, §2.3.1).
- `map` is optional — encounters planned in advance may not have a map yet.
- `round` starts at 0; increments after the last combatant's turn cycles back.
- `logSequence` is a monotonically increasing counter (not DB auto-increment — incremented in service code) so log entries from the same encounter, round, and millisecond are ordered deterministically.
- `lairActionName`/`lairActionDescription` optional — lair actions are encounter metadata, not a separate combatant.

### 1.2 `Combatant` (`src/.../encounter/data/Combatant.java`)

```java
@Entity
@Table(name = "combatant", indexes = {
    @Index(name = "idx_combatant_encounter", columnList = "encounter_id"),
    @Index(name = "idx_combatant_group", columnList = "group_id"),
})
public class Combatant {
    @Id @GeneratedValue(strategy = GenerationType.UUID) UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false) Encounter encounter;

    @Column(nullable = false, length = 255) String name;

    // Initiative
    @Column(nullable = false) int initiative;
    int tieBreaker;               // manual tie-break value for drag-to-reorder proxy
    @Column(nullable = false) int sortOrder; // computed after initiative sorting — used for rendering

    // HP
    @Column(nullable = false) int maxHp;
    int currentHp;
    int tempHp;

    // Type & grouping
    @Column(nullable = false, length = 16) String kind = "NPC"; // PC / NPC / MONSTER
    @Column(length = 36) String groupId;  // UUID string — null = individual; shared = group member
    boolean groupLeader;          // the "primary" entry that initiative applies to

    // External references (nullable — combatant can be ad-hoc)
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "token_id") Token token;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "statblock_id") StatBlock statBlock;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "party_member_id") PartyMember partyMember;

    // State flags
    boolean defeated = false;
    boolean hidden = false;

    // Conditions (stored as JSON array of condition objects — see §1.3)
    @Column(columnDefinition = "CLOB") String conditionsJson = "[]";

    // Concentration
    @Column(length = 255) String concentratingOn; // spell name if concentrating
    boolean concentrationCheckPending = false;

    // Legendary
    int legendaryActionsUsed;
    int legendaryResistancesUsed;
    int legendaryActionsMax;       // from statblock — denormalized for faster access
    int legendaryResistancesMax;

    // Display
    @Column(length = 255) String notes;
}
```

**Condition JSON schema** (stored in `conditionsJson`):

```json
[
  {
    "sourceKey": "paralyzed",       // matches Condition.sourceKey in SRD compendium
    "name": "Paralyzed",
    "durationRounds": 0,             // 0 = indefinite / until saved
    "tickOnSourceTurn": true,        // duration ticks down at start of the creature's own turn
    "appliedInRound": 2,             // which round the condition was applied (for tick-on-source calculation)
    "appliedByCombatantId": "uuid"   // nullable — which combatant applied it
  }
]
```

Key decisions:
- `conditionsJson` uses a typed JSON array, not a join table. Combat conditions are mutated frequently; a JSON column avoids N+1 queries and additional tables. The compendium `Condition` table provides the authoritative text — the combatant stores just keys and duration metadata.
- `sortOrder` is computed after initiative is set (server-side) and used for consistent rendering.
- `tieBreaker` is a synthetic field set by the DM dragging items to reorder — not exposed as a visible number.
- Grouping uses a `groupId` string (UUID) — all combatants with the same `groupId` share an initiative. The `groupLeader` flag marks the primary entry visible in the initiative list.
- Legendary max values are **denormalized** from the statblock into the combatant at creation time. The statblock might be edited later; combatant values reflect the snapshot at encounter start.

### 1.3 `CombatLogEntry` (`src/.../encounter/data/CombatLogEntry.java`)

```java
@Entity
@Table(name = "combat_log_entry", indexes = {
    @Index(name = "idx_log_encounter", columnList = "encounter_id"),
})
public class CombatLogEntry {
    enum EntryType {
        INITIATIVE_SET, TURN_START, TURN_END, ROUND_ADVANCE,
        DAMAGE, HEAL, TEMP_HP,
        CONDITION_ADDED, CONDITION_REMOVED, CONDITION_TICKED,
        CONCENTRATION_SET, CONCENTRATION_LOST, CONCENTRATION_CHECK,
        LEGENDARY_ACTION, LEGENDARY_RESISTANCE,
        DEFEATED, REVIVED,
        COMBATANT_ADDED, COMBATANT_REMOVED, COMBATANT_REORDERED,
        GROUP_SPLIT, LAIR_ACTION,
        NOTE
    }

    @Id @GeneratedValue(strategy = GenerationType.UUID) UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false) Encounter encounter;

    int round;
    long sequence;   // from Encounter.logSequence
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) EntryType type;
    @Column(nullable = false, length = 36) String combatantId; // UUID string of the Combatant

    @Column(columnDefinition = "CLOB") String payload; // JSON — structure depends on type

    @Column(nullable = false, updatable = false) Instant createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
```

**Undo strategy**: The combat log is append-only. Undo works by replaying the full log from entry 0 through N-1, applying mutations to an in-memory copy of the encounter state, then persisting the reconstructed state. This is simpler than reverse-mutation undo (which is bug-prone for conditions, groups, reordering) and equally fast for encounters under ~200 log entries (a long combat). Implementation: `CombatService.undo(encounterId)` — fetch encounter + all combatants + all log entries, replay first N-1, save all combatants, delete the Nth log entry, decrement `logSequence` to N-1. The deleted entry is truly deleted (not "soft-deleted") since we're replaying the authoritative state.

---

## 2. Package Structure

New `encounter` package following the project's feature-module convention:

```
src/main/java/dev/hendrikhoemberg/dmhelper/encounter/
├── data/
│   ├── Encounter.java
│   ├── EncounterRepository.java
│   ├── Combatant.java
│   ├── CombatantRepository.java
│   ├── CombatLogEntry.java
│   └── CombatLogEntryRepository.java
├── service/
│   └── EncounterService.java          (all business logic — one service, as the tracker is cohesive)
│   └── CombatDifficultyCalculator.java (XP budget math — extracted for testability)
└── web/
    ├── EncounterController.java        (Thymeleaf/htmx endpoints — HTML fragments)
    └── EncounterApiController.java     (JSON endpoints for the Alpine.js tracker)
```

---

## 3. Service Layer Design

### 3.1 `EncounterService`

A single `@Service @Transactional` class because combat operations are deeply intertwined (applying damage needs to check concentration, which updates the log, which affects undo). Splitting would create circular dependencies or choreography overhead.

**Core methods:**

```
// Encounter lifecycle
createEncounter(campaignId, createRequest) -> EncounterDto
updateEncounter(id, updateRequest) -> EncounterDto
deleteEncounter(id)
activateEncounter(id)                    // PLANNED -> ACTIVE, init round 1, log ENCOUNTER_ACTIVATED
endEncounter(id)                         // ACTIVE -> DONE, log ENCOUNTER_ENDED

// Combatant management
addCombatant(encounterId, createRequest) -> CombatantDto          // from token, statblock, party member, or ad-hoc
removeCombatant(id)
splitGroupMember(combatantId) -> CombatantDto                     // split a group member into its own entry
prefillFromMap(encounterId, mapId)                                // add combatants for all tokens on a map
prefillFromParty(encounterId, campaignId)                         // add combatants for all active party members

// Initiative
setInitiative(combatantId, initiative, tieBreaker?)               // per-combatant; re-sorts all combatants
autoRollInitiative(encounterId)                                   // roll DEX-based initiative for all without one
reorderCombatants(encounterId, orderedIds[])                      // drag-to-reorder; updates sortOrder

// Turns
nextTurn(encounterId)                    // advance to next combatant (rounds up if needed)
previousTurn(encounterId)                // go to previous (undocumented in spec but needed for misclicks)
setActiveTurn(encounterId, combatantId)

// HP mutations
applyDamage(combatantId, amount)         // negative amount = heal; apply tempHP first, then real HP
applyHeal(combatantId, amount)
setHp(combatantId, currentHp, tempHp)

// Death handling
markDefeated(combatantId, boolean)       // auto on HP <= 0 for monsters; manual for PCs (death saves)
markRevived(combatantId)

// Conditions
toggleCondition(combatantId, sourceKey, durationRounds)     // add or remove a condition
tickConditionDurations(encounterId)                         // called on turn end for the active combatant
removeCondition(combatantId, sourceKey)

// Concentration
setConcentration(combatantId, spellName?)
promptConcentrationCheck(combatantId, damageAmount)          // sets concentrationCheckPending = true
resolveConcentrationCheck(combatantId, passed)               // clears pending; logs result

// Legendary/Lair
useLegendaryAction(combatantId)                               // increments used, logs
useLegendaryResistance(combatantId)                           // increments used, logs
resetLegendaryActions(encounterId)                            // called at round start
activateLairAction(encounterId)

// Combat log
getLog(encounterId) -> List<CombatLogEntryDto>
undo(encounterId)                                             // replay log from 0..N-1

// Difficulty
calculateDifficulty(campaignId, encounterId) -> DifficultyDto // XP budget rating

// Queries
getEncounter(id) -> EncounterDto
getCombatants(encounterId, sorted) -> List<CombatantDto>
getCombatant(id) -> CombatantDto
getActiveEncounter(campaignId) -> EncounterDto?               // one ACTIVE encounter per campaign at a time
```

**Initiative auto-roll logic** (for monsters only):
```
initiative = d20 + dexModifier(statBlock)
```
Uses `Math.random()` seeded with `new Random()` — not the M11 dice roller (which doesn't exist yet). The `StatBlock.dexScore` field provides the DEX score; modifier = `(dexScore - 10) / 2` (integer division, D&D 5e standard). Party members use their `initiativeBonus` directly; the DM types the roll result.

**Sorting rule**: initiative descending, then tieBreaker descending, then name ascending (stable sort).

### 3.2 `CombatDifficultyCalculator`

A pure function extracted into its own class (no `@Service` needed — injected as a bean into `EncounterService`):

```
calculate(partyMembers: List<PartyMember>, monsters: List<StatBlock>) -> DifficultyResult
  - foreach party member: compute level from classAndLevel string, look up Easy/Moderate/Hard/Deadly XP thresholds from the 2024 DMG table
  - sum monster XP values; apply the 2024 encounter multiplier (not 2014)
  - compare total against party thresholds
  - return: { rating: "LOW"|"MODERATE"|"HIGH"|"DEADLY", adjustedXp: int, thresholdXp: int, multiplierInfo: string }
```

**2024 DMG XP thresholds** (hardcoded table — these are game mechanics, not SRD content, so a small static table is fine; the provenance rule §2.3.8 applies to SRD content, not pure math derived from the rules):
- Per-level thresholds: copied from the 2024 DMG "Encounter Budget XP Per Character" table.
- Multiplier: 2024 rules use a flat per-monster budget, not the 2014 "group size multiplier" — double-check against SRD 5.2 document.
- If compendium-derived thresholds exist later (M9), the calculator can be refactored; for M6, a small static table is the practical approach.

---

## 4. API Design

### 4.1 REST/JSON API (`EncounterApiController` — `/api/v1`)

Used by the Alpine.js tracker panel for real-time combat operations:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/campaigns/{cid}/encounters` | Create encounter |
| `PUT` | `/api/v1/encounters/{id}` | Update encounter |
| `DELETE` | `/api/v1/encounters/{id}` | Delete encounter |
| `POST` | `/api/v1/encounters/{id}/activate` | Activate encounter |
| `POST` | `/api/v1/encounters/{id}/end` | End encounter |

| `GET` | `/api/v1/encounters/{id}/combatants` | List combatants (sorted) |
| `POST` | `/api/v1/encounters/{id}/combatants` | Add combatant |
| `PUT` | `/api/v1/combatants/{id}` | Update combatant |
| `DELETE` | `/api/v1/combatants/{id}` | Remove combatant |
| `PUT` | `/api/v1/encounters/{id}/combatants/reorder` | Reorder combatants (`{ "orderedIds": [...] }`) |
| `POST` | `/api/v1/encounters/{id}/prefill/map` | Prefill combatants from map tokens (`{ "mapId": "..." }`) |
| `POST` | `/api/v1/encounters/{id}/prefill/party` | Prefill combatants from party |

| `PUT` | `/api/v1/combatants/{id}/initiative` | Set initiative (`{ "initiative": 18 }`) |
| `POST` | `/api/v1/encounters/{id}/auto-roll` | Auto-roll monster initiative |

| `POST` | `/api/v1/encounters/{id}/next-turn` | Advance to next turn |
| `POST` | `/api/v1/encounters/{id}/previous-turn` | Go back to previous turn |

| `PUT` | `/api/v1/combatants/{id}/hp` | Set HP (`{ "currentHp": 42, "tempHp": 5 }`) |
| `POST` | `/api/v1/combatants/{id}/damage` | Apply damage/healing (`{ "amount": -12 }`) |
| `PUT` | `/api/v1/combatants/{id}/defeated` | Mark defeated/revive (`{ "defeated": true }`) |

| `PUT` | `/api/v1/combatants/{id}/conditions` | Toggle condition (`{ "sourceKey": "paralyzed", "durationRounds": 3 }`) |
| `POST` | `/api/v1/combatants/{id}/conditions/{key}/remove` | Remove single condition |
| `POST` | `/api/v1/encounters/{id}/tick-conditions` | Tick durations for current round |

| `PUT` | `/api/v1/combatants/{id}/concentration` | Set concentration (`{ "spellName": "Bless" }`) |
| `POST` | `/api/v1/combatants/{id}/concentration-check` | Resolve check (`{ "passed": true }`) |

| `POST` | `/api/v1/combatants/{id}/legendary-action` | Use legendary action |
| `POST` | `/api/v1/combatants/{id}/legendary-resistance` | Use legendary resistance |
| `POST` | `/api/v1/encounters/{id}/reset-legendary` | Reset legendary actions for new round |
| `POST` | `/api/v1/encounters/{id}/lair-action` | Activate lair action |

| `GET` | `/api/v1/encounters/{id}/log` | Get combat log entries |
| `POST` | `/api/v1/encounters/{id}/undo` | Undo last action |

| `GET` | `/api/v1/encounters/{id}/difficulty` | Get difficulty rating |

| `GET` | `/api/v1/campaigns/{cid}/encounters/active` | Get the active encounter (if any) |

### 4.2 MVC/htmx Endpoints (`EncounterController` — HTML fragments)

Used for encounter CRUD views, accessed from the campaign dashboard and battle page:

| Method | Path | Returns |
|---|---|---|
| `GET` | `/campaigns/{cid}/encounters` | Encounter list page (full page or fragment) |
| `GET` | `/campaigns/{cid}/encounters/new` | New encounter form (modal/fragment) |
| `POST` | `/campaigns/{cid}/encounters` | Redirect after create |
| `GET` | `/encounters/{id}/edit` | Edit encounter form (fragment) |
| `PUT` | `/encounters/{id}` | Redirect after update |
| `DELETE` | `/encounters/{id}` | Remove encounter row (htmx target) |
| `GET` | `/encounters/{id}/detail` | Encounter detail/prep view |

### 4.3 DTOs

Inner records in services/controllers, following the `TokenService.TokenDto` pattern:

```java
// EncounterDto — returned by all encounter endpoints
record EncounterDto(UUID id, UUID campaignId, UUID mapId, String name,
                    String status, int round, int activeTurnIndex,
                    int combatantCount, String lairActionName,
                    List<CombatantDto> combatants) {}

// CombatantDto — returned by combatant endpoints and embedded in EncounterDto
record CombatantDto(UUID id, UUID encounterId, String name, int initiative,
                    int sortOrder, int currentHp, int maxHp, int tempHp,
                    String kind, String groupId, boolean groupLeader,
                    UUID tokenId, UUID statBlockId, UUID partyMemberId,
                    boolean defeated, boolean hidden,
                    boolean bloodied,
                    List<ConditionStateDto> conditions,
                    String concentratingOn, boolean concentrationCheckPending,
                    int legendaryActionsUsed, int legendaryActionsMax,
                    int legendaryResistancesUsed, int legendaryResistancesMax,
                    String notes) {}

// ConditionStateDto
record ConditionStateDto(String sourceKey, String name, String description,
                         int durationRounds, boolean tickOnSourceTurn,
                         int appliedInRound, UUID appliedByCombatantId) {}

// CombatLogEntryDto
record CombatLogEntryDto(UUID id, int round, long sequence, String type,
                         String combatantId, String combatantName,
                         String payload, Instant createdAt) {}

// DifficultyDto
record DifficultyDto(String rating, int adjustedXp, int partyThreshold, String details) {}
```

---

## 5. Frontend Design

### 5.1 Layout Changes to `battle.html`

The battle sidebar gets a new **Combat Tracker section** that replaces the existing sidebar content when an encounter is active. When no encounter is active, the existing sidebar (map switcher, statblock search, token list) stays as-is.

**Layout approach**: Two modes of the sidebar — controlled by an Alpine.js `encounterActive` flag:

```
Sidebar in "no encounter" mode (existing):
  - Map switcher
  - From Library (statblock search)
  - Tokens list
  - Selected token editor

Sidebar in "encounter active" mode (new):
  - Encounter header (name, round counter, End button)
  - Turn controls (Prev / Next / Lair Action buttons)
  - Initiative tracker list (Alpine-for over combatants, with HP boxes, condition toggles)
  - Selected combatant detail panel (HP math input, conditions, concentration, legendary)
  - Quick-action bar (damage/heal quick-entry, condition shortcuts)
```

The tracker expands the sidebar width from 240px to 320px when active (for condition columns and HP inputs). This is a CSS `max-width` transition.

### 5.2 Alpine.js Tracker Component

A new `combatTracker()` Alpine component — separate from `battleToolbar()` to keep each focused. The two components communicate via the `window` event bus (same pattern already used by `battle-toolchange`, `battle-tokenupdate`).

```javascript
function combatTracker() {
    return {
        encounter: null,           // current EncounterDto
        combatants: [],            // sorted list from API
        activeCombatantId: null,   // highlighted combatant
        selectedCombatantId: null, // detail panel for this one
        hpDelta: '',               // quick-math input: "+12" or "-5"
        conditionSearch: '',       // quick condition filter
        conditionsCatalog: [],     // all Condition compendium entries

        init() {
            // Load conditions catalog once for tooltips
            this.loadConditionsCatalog();
            // Listen for battle-map events
            window.addEventListener('tracker-encounter', (e) => {
                this.encounter = e.detail.encounter;
                this.combatants = e.detail.combatants;
                this.activeCombatantId = e.detail.activeCombatantId;
            });
        },

        // Turn management
        async nextTurn() { ... POST /next-turn; reload combatants ... },
        async previousTurn() { ... },

        // HP quick-math
        async applyHpDelta(combatantId) {
            const delta = parseInt(this.hpDelta, 10);
            if (isNaN(delta)) return;
            await fetch(`/api/v1/combatants/${combatantId}/damage`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ amount: delta })
            });
            await this.reloadCombatants();
            this.hpDelta = '';
        },

        // Initiative
        async setInitiative(combatantId, value) { ... },
        async autoRoll() { ... },

        // Conditions
        async toggleCondition(combatantId, sourceKey, duration) { ... },
        getConditionText(sourceKey) {
            const c = this.conditionsCatalog.find(c => c.sourceKey === sourceKey);
            return c ? `${c.name}: ${c.description}` : sourceKey;
        },

        // Concentration
        async setConcentration(combatantId, spellName) { ... },
        async resolveConcentration(combatantId, passed) { ... },

        // Legendary
        async useLegendaryAction(combatantId) { ... },
        async useLegendaryResistance(combatantId) { ... },

        // Drag-to-reorder (using Alpine's sort plugin — or a lightweight drag handler)
        // Undo
        async undo() { ... },

        // Refresh
        async reloadCombatants() { ... GET /combatants ... },

        selectCombatant(id) { this.selectedCombatantId = id; },
    };
}
```

### 5.3 Tracker HTML Structure

All tracker markup lives in a new Thymeleaf fragment `templates/encounter/_tracker.html`:

```html
<div x-data="combatTracker()" class="tracker-panel">
    <!-- Header -->
    <div class="tracker-header">
        <h3 x-text="encounter?.name || 'Combat'"></h3>
        <span>Round <strong x-text="encounter?.round || 0"></strong></span>
        <button @click="endEncounter()">End Combat</button>
    </div>

    <!-- Turn controls -->
    <div class="tracker-turns">
        <button @click="previousTurn()">◀ Prev</button>
        <span>Active: <strong x-text="activeCombatant?.name"></strong></span>
        <button @click="nextTurn()">Next ▶</button>
        <button @click="activateLairAction()" x-show="encounter?.lairActionName">Lair</button>
    </div>

    <!-- Initiative list -->
    <div class="tracker-list">
        <template x-for="(c, idx) in combatants" :key="c.id">
            <div class="combatant-row"
                 :class="{ active: c.id === activeCombatantId, defeated: c.defeated, hidden: c.hidden }"
                 @click="selectCombatant(c.id)"
                 draggable="true"
                 @dragstart="dragStart(idx)" @dragover.prevent @drop="drop(idx)">
                <!-- Initiative number -->
                <span class="init-badge" x-text="c.initiative"></span>

                <!-- Name + group indicator -->
                <span class="combatant-name">
                    <template x-if="c.groupId && !c.groupLeader">  ├ </template>
                    <span x-text="c.name"></span>
                    <template x-if="c.groupId && c.groupLeader">
                        <span class="group-count" x-text="'(' + groupCount(c.groupId) + ')'"></span>
                    </template>
                </span>

                <!-- HP bar (mini) -->
                <div class="hp-mini">
                    <div class="hp-mini-fill" :style="{ width: hpPercent(c) + '%' }"
                         :class="{ bloodied: c.bloodied, dead: c.defeated }"></div>
                </div>

                <!-- HP numbers + quick-entry -->
                <div class="hp-entry">
                    <input type="text" class="hp-delta-input"
                           placeholder="±HP"
                           @keydown.enter="applyHpDelta(c.id)"
                           x-model="hpDelta">
                </div>

                <!-- Condition icons (colored dots with tooltips) -->
                <div class="condition-icons">
                    <template x-for="cond in c.conditions" :key="cond.sourceKey">
                        <span class="cond-icon"
                              :style="{ background: conditionColor(cond.sourceKey) }"
                              :title="getConditionText(cond.sourceKey)"
                              x-text="cond.durationRounds > 0 ? cond.durationRounds : '∞'"></span>
                    </template>
                    <button @click.stop="openConditionMenu(c.id)">＋</button>
                </div>

                <!-- Concentration indicator -->
                <span x-show="c.concentratingOn" class="conc-badge" title="Concentrating">⏀</span>
            </div>
        </template>
    </div>

    <!-- Selected combatant detail -->
    <div class="combatant-detail" x-show="selectedCombatantId">
        <!-- HP section -->
        <div class="detail-hp">
            <label>HP</label>
            <input type="number" x-model="selected.hp" @change="setHp()">
            <span>/</span>
            <span x-text="selected.maxHp"></span>
            <span class="hp-flag bloodied" x-show="selected.bloodied">Bloodied</span>
            <span class="hp-flag dead" x-show="selected.defeated">Defeated</span>
        </div>

        <!-- Quick math buttons -->
        <div class="hp-quick">
            <button @click="applyQuick(-5)">-5</button>
            <button @click="applyQuick(-1)">-1</button>
            <button @click="applyQuick(1)">+1</button>
            <button @click="applyQuick(5)">+5</button>
            <button @click="applyQuick(selected.hp - selected.maxHp)">Max</button>
        </div>

        <!-- Conditions panel -->
        <div class="detail-conditions">
            <h4>Conditions</h4>
            <template x-for="cond in fullConditionList" :key="cond.sourceKey">
                <label class="condition-toggle">
                    <input type="checkbox"
                           :checked="hasCondition(selected, cond.sourceKey)"
                           @change="toggleCondition(selected.id, cond.sourceKey, cond.defaultDuration)">
                    <span x-text="cond.name"></span>
                    <span class="cond-help" :title="cond.description">?</span>
                </label>
            </template>
        </div>

        <!-- Concentration -->
        <div class="detail-concentration" x-show="selected.concentratingOn || selected.concentrationCheckPending">
            <template x-if="selected.concentratingOn">
                <div>Concentrating: <strong x-text="selected.concentratingOn"></strong>
                    <button @click="clearConcentration(selected.id)">Drop</button>
                </div>
            </template>
            <template x-if="selected.concentrationCheckPending">
                <div class="conc-check">
                    Concentration check! DC: <span x-text="concDc"></span>
                    <button @click="resolveConcentration(selected.id, true)">Passed</button>
                    <button @click="resolveConcentration(selected.id, false)">Failed</button>
                </div>
            </template>
        </div>

        <!-- Legendary section -->
        <div class="detail-legendary" x-show="selected.legendaryActionsMax > 0">
            <h4>Legendary</h4>
            <div>Actions: <span x-text="selected.legendaryActionsUsed"></span> / <span x-text="selected.legendaryActionsMax"></span>
                <button @click="useLegendaryAction(selected.id)">Use</button>
            </div>
            <div>Resistances: <span x-text="selected.legendaryResistancesUsed"></span> / <span x-text="selected.legendaryResistancesMax"></span>
                <button @click="useLegendaryResistance(selected.id)">Use</button>
            </div>
        </div>

        <!-- Defeat / Revive / Remove -->
        <div class="detail-actions">
            <button x-show="!selected.defeated" @click="markDefeated(selected.id, true)">Defeat</button>
            <button x-show="selected.defeated" @click="markDefeated(selected.id, false)">Revive</button>
            <button @click="removeCombatant(selected.id)">Remove</button>
        </div>
    </div>

    <!-- Undo button -->
    <div class="tracker-footer">
        <button @click="undo()" :disabled="!canUndo">↩ Undo</button>
        <span class="log-summary" x-text="logSummary"></span>
    </div>
</div>
```

### 5.4 Integration with Battle Map

When an encounter is active, the `BattleMap` class needs:
1. **Active-turn highlight**: the active combatant's linked token gets a glow/outline. The tracker publishes `tracker-active-turn` event; `BattleMap` listens and updates the token's visual.
2. **Token selection → combatant selection**: clicking a token while the tracker is open selects the corresponding combatant in the tracker. Existing `battle-tokenselect` event is already dispatched; the tracker listens for it and sets `selectedCombatantId` if the token has a combatant link.
3. **Combatant defeat → token dead**: when a combatant is defeated, `BattleMap` receives the event and marks the linked token as dead (if linked).
4. **Encounter start**: building an encounter from current map tokens — calls `POST /api/v1/encounters/{id}/prefill/map`.

The `BattleMap` class currently has no awareness of encounters. A **new event bus message** `tracker-encounter-state` carries the active encounter status; the map listens for it and adjusts rendering accordingly.

### 5.5 Encounter List & Prep Views

New Thymeleaf templates:

- `templates/encounter/list.html` — campaign's encounter list (accessible from campaign dashboard)
- `templates/encounter/_card.html` — encounter summary card (name, status badge, map name, combatant count, round)
- `templates/encounter/_form.html` — create/edit form (name, map selector dropdown, status)
- `templates/encounter/detail.html` — encounter prep view (before activating): shows combatants, difficulty rating, add-combatant form with statblock search and party prefill buttons

The encounter list links from the campaign detail page and from a sidebar item. The battle page's toolbar gains an **"Encounter" button** that shows a dropdown: "New encounter from map" / "Activate existing".

### 5.6 CSS Design Tokens

New CSS variables for tracker-specific colors (added to `app.css`):

```css
:root {
    --color-hp-bar: var(--color-success);
    --color-hp-bloodied: #e67e22;
    --color-hp-dead: var(--color-danger);
    --color-condition-active: var(--color-accent);
    --color-concentration: #9b59b6;
    --color-legendary: #f1c40f;
    --color-tracker-bg: var(--color-surface);
    --color-combatant-active: rgba(74, 158, 255, 0.15);
    --color-combatant-hover: rgba(255, 255, 255, 0.05);
    --color-initiative-badge: var(--color-accent);
}
```

---

## 6. Implementation Order (9 Steps)

Each step produces a testable, working state. Steps are ordered by dependency — the combat log (step 4) must exist before HP mutations (step 5) since every mutation logs. But you can build and test entities (step 1) and basic CRUD (step 2) before touching the log.

### Step 1: Entities & Repositories (backend foundation)

**Files to create:**
- `src/.../encounter/data/Encounter.java`
- `src/.../encounter/data/EncounterRepository.java`
- `src/.../encounter/data/Combatant.java`
- `src/.../encounter/data/CombatantRepository.java`
- `src/.../encounter/data/CombatLogEntry.java`
- `src/.../encounter/data/CombatLogEntryRepository.java`

**Verification:** Start the app — `ddl-auto=update` creates the three new tables. Query them via H2 console.

**Repository query methods needed:**
```java
// EncounterRepository
List<Encounter> findByCampaignIdOrderByNameAsc(UUID campaignId);
Optional<Encounter> findByCampaignIdAndStatus(UUID campaignId, Encounter.Status status);
List<Encounter> findByMapIdOrderByNameAsc(UUID mapId);

// CombatantRepository
List<Combatant> findByEncounterIdOrderBySortOrderAsc(UUID encounterId);
List<Combatant> findByEncounterIdAndGroupId(UUID encounterId, String groupId);
void deleteByEncounterId(UUID encounterId);

// CombatLogEntryRepository
List<CombatLogEntry> findByEncounterIdOrderBySequenceAsc(UUID encounterId);
void deleteByEncounterId(UUID encounterId);
```

### Step 2: Basic Encounter CRUD (backend + HTML)

**Files to create/modify:**
- `src/.../encounter/service/EncounterService.java` (stub with create, read, update, delete, list)
- `src/.../encounter/web/EncounterController.java` (htmx fragments for list, form, card, detail)
- `src/.../encounter/web/EncounterApiController.java` (JSON create/update/delete/list)
- `templates/encounter/list.html`
- `templates/encounter/_card.html`
- `templates/encounter/_form.html`
- `templates/encounter/detail.html`

Update `templates/campaigns/detail.html` to include an Encounters section linking to `/campaigns/{cid}/encounters`.

**Verification:** Create encounters (PLANNED state), edit them, see them in the campaign dashboard. All via HTML interface.

### Step 3: Combatant Management (backend + API)

**Add to `EncounterService`:**
- `addCombatant()` — from token, statblock, party member, or ad-hoc (name + HP + kind only)
- `removeCombatant()`
- `prefillFromMap(encounterId, mapId)` — iterate tokens on map, create combatants, copy HP/name/kind/refs
- `prefillFromParty(encounterId, campaignId)` — iterate active party members, create PC combatants with initiative bonus shown
- `splitGroupMember(combatantId)` — remove from group, create independent entry

**Add to `EncounterApiController`:** combatant CRUD endpoints (see §4.1).

**Add to `EncounterController`:** the encounter detail page shows combatant list with add/remove buttons.

**Verification:** Create an encounter, add combatants manually, prefill from a map with tokens, prefill from party. See them in the detail page.

### Step 4: Combat Log (backend)

**Add to `EncounterService`:**
- Internal `logEntry(encounterId, type, combatantId, payload)` — creates CombatLogEntry, increments `encounter.logSequence`
- `getLog(encounterId)` — returns ordered log entries
- `undo(encounterId)` — replay strategy (§1.3)

**Replay algorithm for undo:**
```
snapshot = { combatants: Map<UUID, Combatant>, encounter: Encounter }
log = getLog(encounterId).sorted()
replay = log.subList(0, log.size() - 1)  // all but last
for each entry in replay:
    switch entry.type:
        DAMAGE: snapshot[entry.combatantId].currentHp += payload.amount (negative = damage)
        HEAL:    snapshot[entry.combatantId].currentHp += payload.amount
        CONDITION_ADDED: add to conditionsJson list
        CONDITION_REMOVED: remove from list
        DEFEATED: set defeated = true
        REVIVED: set defeated = false
        INITIATIVE_SET: snapshot[entry.combatantId].initiative = payload.initiative
        ... (one case per EntryType)
saveAll(snapshot.combatants.values())
update(snapshot.encounter, round, activeTurnIndex, logSequence = replay.size())
deleteLastLogEntry(encounterId)
```

**Verification:** Apply damage to a combatant, check log contains a DAMAGE entry. Undo it, verify HP reverts and log entry is gone.

### Step 5: Initiative & Turn Management (backend + API)

**Add to `EncounterService`:**
- `setInitiative(combatantId, initiative)` — sets value, re-sorts all combatants in the encounter, logs
- `autoRollInitiative(encounterId)` — monsters get `d20 + DEX mod`; PCs not modified (DM types theirs)
- `reorderCombatants(encounterId, orderedIds)` — syncs sortOrder to match the array order, logs
- `nextTurn(encounterId)` — increments activeTurnIndex, wraps to 0 and increments round, resets legendary, ticks conditions that tick at "end of next turn", prompts recharge where applicable
- `previousTurn(encounterId)` — decrements activeTurnIndex, wraps backward
- `setActiveTurn(encounterId, combatantId)` — manual jump to a combatant

**Turn-wrapping logic** (nextTurn):
```
// Skip defeated combatants
idx = (activeTurnIndex + 1) % combatants.size()
loopCount = 0
while combatants[idx].defeated && loopCount < combatants.size():
    idx = (idx + 1) % combatants.size()
    loopCount++
if loopCount >= combatants.size(): // all defeated
    // encounter is over — don't auto-end; DM presses "End Combat"
    return error("All combatants defeated")
if idx == 0: // wrapped around
    round++
    resetLegendaryActions()
    // tick conditions that tick on "source's turn" here
    // prompt recharge rolls
activeTurnIndex = idx
save(encounter)
```

**DEX modifier calculation:**
```java
static int dexModifier(StatBlock sb) {
    return Math.floorDiv(sb.getDexScore() - 10, 2);
}
```

**Verification:** Set initiative for each combatant, observe sorted order. Auto-roll monsters. Advance turns, verify round increments, legendary resets, defeated combatants are skipped.

### Step 6: HP Math & Conditions (backend + frontend tracker panel)

**Add to `EncounterService`:**
- `applyDamage(combatantId, amount)` — applies to temp HP first, then real HP; if HP <= 0 and kind != PC → auto defeat; logs DAMAGE
- `applyHeal(combatantId, amount)` — caps at maxHp; logs HEAL
- `toggleCondition(combatantId, sourceKey, durationRounds)`
- `tickConditionDurations(encounterId)` — decrement duration where applicable; remove expired; log CONDITION_TICKED/REMOVED
- `removeCondition(combatantId, sourceKey)`
- `markDefeated/markRevived`

**Build the Alpine.js tracker panel (frontend):**
- Create `templates/encounter/_tracker.html` fragment
- Create `combatTracker()` Alpine function in an inline `<script>` in `battle.html` (or a separate JS module if it grows large)
- Update `battle.html` to include the tracker panel, toggled by encounter active state
- Wire up the `window` event bus for map↔tracker communication

**Condition toggle integration with compendium:**
- The condition list in the detail panel is populated from `GET /api/v1/library/conditions` (existing endpoint).
- Each condition has a `defaultDuration` (0 = indefinite) suggested by the UI (common durations: "until end of next turn" = 1 round for tick-on-source, "1 minute" = 10 rounds).
- Condition text shown on hover/tap uses the compendium description.

**Bloodied calculation** (same as TokenService): `currentHp <= maxHp / 2 && currentHp > 0`.

**Verification:** Create an encounter with combatants. Apply damage, see HP update, bloodied state toggle. Add/remove conditions with durations. Advance turns, verify durations tick down and expire.

### Step 7: Concentration, Legendary & Lair Actions (backend + frontend)

**Add to `EncounterService`:**
- `setConcentration(combatantId, spellName)` — sets `concentratingOn`, logs
- `clearConcentration(combatantId)` — clears `concentratingOn`, logs
- `promptConcentrationCheck(combatantId, damageAmount)` — sets `concentrationCheckPending = true`, calculates DC = max(10, damageAmount / 2)
- `resolveConcentrationCheck(combatantId, passed)` — if passed: clears pending; if failed: clears concentration flag + pending
- `useLegendaryAction(combatantId)` — increments used (must be < max)
- `useLegendaryResistance(combatantId)` — increments used
- `resetLegendaryActions(encounterId)` — zeroes used counters for all combatants
- `activateLairAction(encounterId)` — logs LAIR_ACTION

**Frontend updates:**
- Add legendary actions/resistances counter to combatant detail panel
- Add concentration set/clear controls
- Add concentration check prompt (shown as a callout when pending)
- Add lair action button in turn controls

**Concentration DC:** `max(10, damageAmount / 2)` — 5.5e rules. Integer division, round down.

**Verification:** Set concentration on a combatant. Apply damage — concentration check prompt appears. Resolve it — pass clears pending, fail clears concentration. Use legendary actions/resistances, see counters update, reset on round wrap.

### Step 8: Difficulty Calculator (backend + frontend)

**Create `CombatDifficultyCalculator.java`:**
- `calculate(List<PartyMember> party, List<CombatantDto> monsters) -> DifficultyDto`
- Hardcoded 2024 DMG XP threshold table (per-level Easy/Moderate/Hard thresholds)
- Parse classAndLevel to extract level (handle "Rogue 5", "Fighter 3 / Wizard 2", etc.)
- Sum monster XP (from statblock.xp or manual entry for ad-hoc combatants)
- Compute rating

**Frontend:**
- On encounter detail/prep page: show difficulty section with rating badge and breakdown
- Update live as combatants are added/removed (recalculate on each mutation)

**XP Threshold Table (2024 DMG, sample for levels 1-20):**
```java
// Easy thresholds per level (multiply by party size for total)
static final int[] EASY =  { 25, 50, 75, 125, 250, 300, 350, 450, 550, 600, 800, 1000, 1100, 1250, 1400, 1600, 2000, 2100, 2400, 2800 };
static final int[] MODERATE = { 50, 100, 150, 250, 500, 600, 750, 900, 1100, 1200, 1600, 2000, 2200, 2500, 2800, 3200, 3900, 4100, 4900, 5700 };
static final int[] HARD = { 75, 150, 225, 375, 750, 900, 1100, 1400, 1600, 1900, 2400, 3000, 3400, 3800, 4300, 4800, 5900, 6300, 7300, 8500 };
// Deadly = HARD * 2 (but check 2024 DMG for exact values)
```
**Important:** These numbers MUST be verified against the official SRD 5.2 document before committing. If the SRD 5.2 document provides different values, those take precedence (provenance rule §2.3.8). If no official source is available for the 2024 thresholds, use the 2014 DMG values as a baseline and mark the feature as "2014 DMG reference — 2024 update pending source availability." Do NOT have the AI fill in values from memory.

**Verification:** Set up a party of 4 level-5 PCs. Add 3 CR-3 monsters. See difficulty rating.

### Step 9: Integration, Polish & Tests

**Integration work:**
- Wire the encounter system into the battle map toolbar ("Encounter ▼" dropdown with "New from map", "Activate existing", etc.)
- Wire encounter CRUD pages into campaign navigation
- Handle edge cases: activating an encounter while another is active (auto-end the old one), combatants with no map, combatants with no token
- Export/import: add `encounters` section to `CampaignExportDto` (use `List.of()` placeholder for now — full serialization in M12)

**Testing:**

| Test type | What to test |
|---|---|
| Unit | `CombatDifficultyCalculator` — known inputs → expected ratings |
| Unit | `EncounterService` — replay-based undo for each mutation type |
| Unit | `EncounterService` — initiative sorting edge cases (ties, empty list, all same initiative) |
| Unit | DEX modifier calculation |
| Unit | HP math edge cases (0 HP, negative HP, temp HP, heal-capped-at-max) |
| Integration | `EncounterApiController` — full CRUD + turn flow via MockMvc |
| Integration | `EncounterController` — htmx fragment rendering |
| Integration | Combat flow end-to-end: create encounter → add combatants → set initiative → advance turns → apply damage → add conditions → undo |
| Round-trip | Export/import encounter data (stub — full test in M12) |

**Test pattern:** Follow existing test patterns in the project:
- `@SpringBootTest` / `@WebMvcTest` for controller tests
- `@DataJpaTest` for repository tests
- Constructor injection (no `@Autowired` fields)
- MockMvc for API tests with `objectMapper.writeValueAsString()` for request bodies
- `TestEntityManager` for data setup in integration tests

---

## 7. Files Changed Summary

### New files (~18 files)

```
src/main/java/dev/hendrikhoemberg/dmhelper/encounter/
├── data/
│   ├── Encounter.java
│   ├── EncounterRepository.java
│   ├── Combatant.java
│   ├── CombatantRepository.java
│   ├── CombatLogEntry.java
│   └── CombatLogEntryRepository.java
├── service/
│   ├── EncounterService.java
│   └── CombatDifficultyCalculator.java
└── web/
    ├── EncounterController.java
    └── EncounterApiController.java

src/main/resources/templates/encounter/
├── list.html
├── _card.html
├── _form.html
├── detail.html
└── _tracker.html

src/test/java/.../encounter/
├── service/EncounterServiceTest.java
├── service/CombatDifficultyCalculatorTest.java
├── web/EncounterApiControllerTest.java
└── web/EncounterControllerTest.java
```

### Modified files (~5 files)

```
templates/maps/battle.html                                — add tracker panel, encounter toolbar button, combatTracker() Alpine component
templates/campaigns/detail.html                           — add Encounters section
templates/fragments/navbar.html                           — add Encounters link (if campaign nav exists)
src/main/resources/static/css/app.css                     — tracker CSS tokens + styles
src/main/java/.../campaign/service/CampaignExportDto.java — add encounters stub
```

---

## 8. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| **Undo complexity** — replay is simple but might be slow for very long combats | Cap log entries at 500 per encounter; warn DM if exceeded. An encounter with 500+ mutations is rare (most combats are 3-5 rounds with 4-8 combatants). If perf becomes an issue post-M6, optimise with checkpoint snapshots. |
| **Alpine.js state sync** — two Alpine components (`battleToolbar` and `combatTracker`) must stay in sync | Use the existing `window` event bus pattern. `combatTracker` owns the truth for encounter state; `battleToolbar` reads from it for UI toggles (sidebar mode). |
| **Concurrent access** — DM might have two browser tabs open, both trying to advance turns | Not a v1 concern (single-user). Use `@Version` on Encounter for optimistic locking in the future. For now, last-write-wins via the in-memory H2 DB. |
| **Monster-group complexity** — splitting groups, tracking per-creature HP within a group | Start simple: per-creature HP is tracked by individual Combatant entities; the `groupId` only affects initiative display. Split-out creates a new Combatant with `groupId = null`. Group leader shows total group information. |
| **Condition timer accuracy** — "until end of next turn" vs "1 minute" | The tracker asks the DM to specify duration in rounds at toggle time. Auto-tick decrements at the right point (on `nextTurn`, based on `tickOnSourceTurn` flag). This is good enough for table play. |
| **2024 DMG XP thresholds** — may not be available in open5e or SRD 5.2 document | Use 2014 DMG values with a clear marker in code comments. The feature is prep-time guidance, not rules enforcement — approximate is fine. |
| **Vendor JS surface** — Alpine.js is already vendored; no new frontend dependencies | Good. |
| **Schema migration** — new tables, no destructive changes | `ddl-auto=update` handles new tables. No `@PostConstruct` migration needed. |

---

## 9. Design Notes

1. **Single `EncounterService` rather than split services**: Combat operations (damage → check concentration → log → check defeat) are a single transactional flow. Splitting would create circular references. The service class will be large (~600-800 lines) but cohesive. If it exceeds 1000 lines, extract `CombatLogService` (log + undo) as a second class — but don't pre-empt this.

2. **Conditions as JSON, not a join table**: The conditions list is part of a combatant's in-memory state, frequently mutated, and always fetched with the combatant. A JSON column avoids the N+1 query problem and doesn't benefit from referential integrity (the conditions are already validated against the compendium at toggle time). The compendium `Condition` table remains the source of truth for condition text.

3. **Initiative is typed, not auto-rolled for PCs**: The SPEC explicitly says "PCs typed in, with their roster initiative bonus shown." Auto-roll is for monsters only. The DM always has the choice to type a value instead.

4. **The tracker panel `_tracker.html` is an HTMX fragment loaded into `battle.html`**: It can be requested via htmx (`hx-get`) when an encounter is activated, and its Alpine component self-initializes. This keeps `battle.html` from growing to 1000+ lines.

5. **No player-view projection yet**: `dmOnly`/`hidden` flags on Encounter and Combatant exist from day one, but the server-side player-safe projection (for M7 WebSocket) is not wired in M6. The DM Mode toggle on the battle map already hides HP — the tracker has no DM Mode toggle of its own because the tracker is always DM-only (players see nothing of it in v1).

6. **Recharge abilities**: The SPEC mentions "Statblock recharge abilities ('Recharge 5-6') prompt a recharge roll at the start of the creature's turn." The `StatBlock` entity stores actions/legendaryActions as JSON strings. Recharge detection requires parsing these JSON strings for `recharge` fields. For M6, implement this as a regex scan of the action JSON at `nextTurn` time for the active combatant's statblock. If no statblock is linked, skip.

7. **Lair actions at initiative 20**: The SPEC says "a lair action entry pinned at initiative 20." In D&D 5e, lair actions happen on initiative count 20 (losing ties). For M6, implement this as: the lair action appears as a dedicated **button in the turn controls**, labelled with the lair action name. The DM clicks it whenever the table reaches initiative 20 — the software does not auto-trigger. This is simpler and gives the DM full control over timing. The lair action button is only visible when `encounter.lairActionName` is set.
