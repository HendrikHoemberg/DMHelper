# Encounter and Map Depth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver delivery item 9 of the all-in-one DM readiness specification: deepen encounter preparation (library multi-add, waves/reserves/triggers, placement, tactics/rewards, completion summary with DM-confirmed loot/quest updates) and published-map workflow (image-first import with calibration, named regions, DM/player presentation layers) while preserving the existing combat tracker, package-v2 round-trip, and player-safe projections.

**Architecture:** Keep `EncounterService` and the tracker as the single combat runtime. Add first-class `EncounterWave` rows and typed prep/reward JSON on `Encounter` rather than a second combat system. Combatants gain optional wave membership and placement fields; waves spawn into the live roster on explicit DM trigger. End-of-encounter produces a deterministic summary and a reviewable reward draft that only mutates treasury/quest state after confirmation. For maps, extend the existing map-document IMAGE layer and REGION primitives with calibration metadata and stable region keys; keep tokens on one map so DM/player variants share token state and differ only by layer/primitive projection. Extend package-v2 schema, `EncounterSectionAdapter`, `MapSectionAdapter`, and flagship fixtures so every new persistent field round-trips.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2, Jackson, Thymeleaf, HTMX, Alpine, Konva (existing map editor), JSON Schema draft 2020-12, Maven Wrapper, JUnit/AssertJ/MockMvc, existing Playwright smoke coverage.

## Global Constraints

- Master design Workstreams G (§12) and H (§13) and delivery item 9 are authoritative. Do **not** start documentation/agent SDK release (item 10), P3 world/travel/fog/audio (item 11), or full fog-of-war automation in this plan. Map documents may store reveal-ready shapes; do not ship fog gameplay.
- Offline/local-first: no CDN, no new frontend build chain, no network generative services.
- Difficulty remains an **estimate** until authoritative 2024 threshold data exists in-repo. Never present the proxy calculator as exact rules.
- Every new campaign-owned field is **persistent-exported** unless explicitly documented as transient. Export → import must preserve persistent meaning.
- Stable package keys remain identity. Display names are presentation only. New keyed types: `ENCOUNTER_WAVE` (and continue using existing ENCOUNTER / COMBATANT / MAP / TOKEN keys).
- Player-safe projection must never receive: tactics, morale, surrender notes, wave trigger conditions, reward drafts, prep notes, source locators, DM annotations, or region keys that encode secrets. Extend `PlayerSafeProjectionService` tests for every new field that could leak.
- Use reviewed Flyway migration `V9__…`. Do not rely on Hibernate schema generation in production.
- Prefer extending `EncounterService`, `GameMapService`, map-editor.js, package adapters, and existing templates over parallel encounter/map systems.
- Undo must not cross non-undoable boundary markers (`ENCOUNTER_ACTIVATED`, `ENCOUNTER_ENDED`, `WAVE_SPAWNED`, `REWARD_APPLIED`).
- Complete every task with focused tests before moving on. Prefer TDD: failing test → implement → pass → commit.

---

## Audit result: what is already implemented

Verified against the master design status table (spec §22), `docs/campaign-capabilities.md`, git history, and repository evidence as of 2026-07-17:

| # | Delivery item | Spec claim | Audit status | Key evidence |
|---|---|---|---|---|
| 1 | P0 runtime reliability | `IMPLEMENTED` | **Correctly implemented** | Quick notes, `ContentDestinationRegistry`, `dm-request.js` failure handling, package asset safety, difficulty labeled estimate |
| 2 | Campaign contract v1 repair | `IMPLEMENTED` | **Correctly implemented** | Closed schemas, unified dry-run/import validators, v1 fixtures |
| 3 | Package v2 foundation | `IMPLEMENTED` | **Correctly implemented** | ZIP/JSON, package keys (V3), validators, preview, staged assets |
| 4 | Complete round-trip | `IMPLEMENTED` | **Correctly implemented** | Section adapters, semantic snapshot/compare, flagship fixtures |
| 5 | Session cockpit | `IMPLEMENTED` | **Correctly implemented** | Real `/session` page, rails, lifecycle, draft log, resume order (V4) |
| 6 | Structured adventure/quest | `IMPLEMENTED` | **Correctly implemented** | Scene sections/checks/transitions/links, quests/objectives (V5–V6) |
| 7 | Custom compendium expansion | `IMPLEMENTED` | **Correctly implemented** | Ownership + provenance for library types (V7) |
| 8 | Character-sheet completion | `IMPLEMENTED` | **Correctly implemented** | Live party state, attacks/features, inventory states, rest preview, batch ops, package sheet fields (V8) |
| **9** | **Encounter and map depth** | **`PLANNED`** | **Accurate — strong runtime baseline, thin prep + published-map depth** | See gap table below |
| 10–11 | Docs/agent SDK, P3 | `PLANNED` | Out of scope | — |

### Current encounter baseline (keep; do not regress)

| Area | What exists | Primary files |
|---|---|---|
| Model | `Encounter` (status, round, turn, lair, map link, key); `Combatant` (HP, initiative, group, conditions, concentration, legendary/recharge, token/statblock/party links); `CombatLogEntry` with full undo replay | `encounter/data/*` |
| Runtime | Activate/end, next/previous turn, damage/HP, conditions, concentration, legendary/lair, group split, dice log, undo-by-replay | `EncounterService` (~1427 lines) |
| Prep UI | Name + map select; manual combatant quick-add; prefill from map tokens or party | `templates/encounter/*` |
| Difficulty | Labeled estimate using 2014 Medium XP proxy + CR table | `CombatDifficultyCalculator` |
| Package | Encounter + combatants + combat log export/import | `EncounterSectionAdapter` |
| Tracker | Rich HTMX/Alpine tracker fragment | `_tracker.html` |

### Current map baseline (keep; do not regress)

| Area | What exists | Primary files |
|---|---|---|
| Model | `GameMap` grid dims, cell size, document CLOB, tokens | `gamemap/data/*` |
| Editor | Terrain brush, shapes, room/door/region tools, background image via data URL (move/resize), undo stack | `map-editor.js`, `editor.html` |
| Battle map | Live tokens, measurement, AoE, player projection | `battle-map.js`, `PlayerSafeProjectionService` |
| Package | Maps, tokens, IMAGE layers exported as package assets (dataUrl → asset file) | `MapSectionAdapter` |
| Schema | `map-document-v2.schema.json` with REGION primitives lacking keys | `schemas/map-document-v2.schema.json` |

### Hard gaps this plan closes (Workstreams G + H)

| Gap | Spec § | Severity |
|---|---|---|
| No library multi-add with quantity/grouping from encounter prep UI | §12.1 | High |
| No waves / reinforcements / reserves / trigger prompts | §12.1–12.2 | High |
| No starting map placement or placement-region binding for prepared combatants | §12.1 | High |
| No tactics / morale / surrender / environment prep fields | §12.1 | Medium |
| No structured rewards / XP / loot draft; end encounter only flips status | §12.1–12.2, §15.1 | High |
| No deterministic encounter summary at completion | §12.2 | High |
| Undo has no non-undoable boundary markers for activate/end/wave spawn | §12.2 | Medium |
| Hazard/trap as initiative entries incomplete (OBJECT only; no HAZARD kind) | §12.1 | Medium |
| Scene linkage / source locator not first-class on encounter | §12.1 | Medium |
| Background image: no crop, rotate, two-point grid calibration, or lock | §13.1 | High |
| REGION primitives have no stable `key`/`label` for scene/encounter refs | §13.1, §9.2 `mapRegionKey` | High |
| No explicit DM vs player presentation layers without token duplication | §13.1 | High |
| Map document schema version drift (`MapDocumentDto.CURRENT_SCHEMA_VERSION = 1` vs schema const 2) | §13.4, §4.4 | Medium |
| Package schema missing waves, rewards, prep, placement, region keys, calibration | §7.8 | High |
| `FlywayMigrationTest` still asserts V7 latest despite V8 existing | maintainability | Low (fix in this plan) |

---

## Delivery item 9 acceptance contract

- [ ] DM can search the library and add N copies of a creature as a group to a planned encounter in one action.
- [ ] Encounter stores ordered waves (main + reserves) with keys, labels, trigger kind/value, and combatant membership; reserves do not appear in initiative until spawned.
- [ ] DM can trigger a pending wave during an active encounter; spawn creates combatants (and optional tokens at placement) and logs `WAVE_SPAWNED` as a non-undoable boundary.
- [ ] Prepared combatants support pixel start positions and/or a placement region key; activate/spawn places tokens when a map is linked.
- [ ] Prep fields: tactics, morale, surrender/flee, environment notes, source locator, optional scene link — editable on detail, round-tripped.
- [ ] Structured rewards (XP, currency, items, quest objective refs, free notes) are authorable; ending an encounter opens a summary + reward review; nothing hits treasury/ledger/quest until DM confirms.
- [ ] Deterministic summary includes rounds, casualties, damage totals from the log, waves spawned, and attendance snapshot when a session is open.
- [ ] Undo refuses to cross boundary log types and surfaces a clear message.
- [ ] Combatant kind includes `HAZARD` for non-creature initiative entries (no forced HP semantics beyond current OBJECT handling).
- [ ] Difficulty remains labeled **Estimate** with source/assumptions visible.
- [ ] Published-map workflow: import image → optional crop/rotate → two-point calibrate cell size + offset → lock image layer → named regions with keys.
- [ ] DM can mark layers/primitives as player-visible or DM-only; player projection strips DM-only content; tokens are not duplicated.
- [ ] Package v2 schema, adapters, semantic compare, and at least one flagship fixture exercise waves, rewards, prep, placement, region keys, and calibration.
- [ ] Existing tracker, session cockpit activation, package round-trip, and player-safety tests remain green.
- [ ] Capability matrix marks encounter/map depth areas `SUPPORTED` / `PARTIAL` accurately after gates pass.

---

## Domain contracts (authoritative)

### EncounterWave (new table + package type)

```text
encounter_wave
  id UUID PK
  encounter_id UUID NOT NULL FK → encounter ON DELETE CASCADE
  wave_key VARCHAR(100) NOT NULL          -- package key candidate; unique per encounter
  name VARCHAR(255) NOT NULL
  sort_order INT NOT NULL
  status VARCHAR(16) NOT NULL             -- RESERVE | PENDING | ACTIVE | DEPLETED
  trigger_kind VARCHAR(24) NOT NULL       -- MANUAL | ROUND | HP_THRESHOLD | CUSTOM
  trigger_value VARCHAR(255)              -- e.g. "3" for round 3, "0.5" for half HP of leader
  notes CLOB
  UNIQUE (encounter_id, wave_key)
```

Enums:

```java
public enum WaveStatus { RESERVE, PENDING, ACTIVE, DEPLETED }
public enum WaveTriggerKind { MANUAL, ROUND, HP_THRESHOLD, CUSTOM }
```

Semantics:

- Every encounter gets a default wave `main` (sort 0, status ACTIVE when encounter is planned/active) on create and on migration for existing rows.
- Combatants with `wave_id` null are treated as belonging to `main` for backward compatibility.
- Only combatants on ACTIVE waves participate in initiative/turns. RESERVE/PENDING combatants are stored but excluded from `getCombatants` tracker ordering until spawned.
- `spawnWave(encounterId, waveId)`: requires encounter ACTIVE; wave PENDING or RESERVE → ACTIVE; combatants become initiative-eligible; optional token placement; log `WAVE_SPAWNED` with payload `{waveKey}`; mark prior combatants unchanged.

### Combatant extensions

| Field | Type | Notes |
|---|---|---|
| `wave_id` | UUID FK nullable | Wave membership |
| `start_x` | INT nullable | Pixel X from top-left (package + runtime placement) |
| `start_y` | INT nullable | Pixel Y from top-left |
| `placement_region_key` | VARCHAR(100) nullable | Package-local region key on the linked map |
| `kind` | enum + string | Add `HAZARD` to allowed kinds |

### Encounter prep + rewards (CLOBs on encounter)

`prep_json` (typed record, never raw Map across module APIs):

```json
{
  "tactics": "Hit and run from the trees.",
  "morale": "Flee at half strength.",
  "surrender": "Offer information if captured.",
  "environment": "Dim light; difficult roots.",
  "sourceLocator": "LMOP p. 12",
  "scalingNotes": "Add 2 goblins per PC above 4.",
  "sceneKey": "goblin-ambush"
}
```

`rewards_json`:

```json
{
  "xpTotal": 200,
  "xpPerPc": null,
  "currency": [{"currency": "gp", "amount": 15}],
  "items": [
    {"customText": "Healing potion", "quantity": 2},
    {"equipmentItemRef": {"type": "EQUIPMENT_ITEM", "source": "SRD", "ruleset": "SRD_5_2", "sourceKey": "shortsword"}, "quantity": 1}
  ],
  "questObjectiveRefs": [{"type": "OBJECTIVE", "key": "clear-hideout"}],
  "notes": "One potion is already used if the fight lasts 5+ rounds."
}
```

Package form uses ContentReferences for library items and objectives. Runtime store may keep UUID/sourceKey hybrids only inside service mappers — export always emits ContentReferences.

### Encounter summary (computed, not persisted as source of truth)

```java
public record EncounterSummaryDto(
    UUID encounterId,
    String name,
    int rounds,
    int combatantCount,
    int defeatedCount,
    int partyCasualtyCount,
    int totalDamageDealt,
    List<String> wavesSpawned,
    List<String> casualtyNames,
    EncounterRewardsDto rewardsDraft,
    Instant endedAt
) {}
```

Computed from combat log + final combatant state. May be embedded in the end-encounter HTMX response; not a separate table.

### Undo boundaries

Add log types:

```java
WAVE_SPAWNED,
REWARD_APPLIED,
UNDO_BOUNDARY  // generic marker if needed
```

`undo(encounterId)`:

1. If log empty → no-op (or 409 with message).
2. If last entry type is in `{ENCOUNTER_ACTIVATED, ENCOUNTER_ENDED, WAVE_SPAWNED, REWARD_APPLIED, SESSION_END}` → throw `IllegalStateException` / return 409 with message `"Cannot undo past a session boundary: {type}"`.
3. Otherwise existing replay path.

### Map document extensions

Bump internal `MapDocumentDto.CURRENT_SCHEMA_VERSION` to **2** and align export/import with `map-document-v2.schema.json`.

`ImageDto` (runtime, dataUrl form):

```java
public record ImageDto(
    String dataUrl,
    double x, double y, double width, double height,
    Double rotationDeg,     // default 0
    Boolean locked,         // default false
    CalibrationDto calibration  // nullable
) {}

public record CalibrationDto(
    // Two reference points in image/layer cell-space before calibration
    double ax, double ay, double bx, double by,
    // Declared grid distance between A and B in cells (usually 1 or integer span)
    double cellsBetween,
    // Resulting offsets applied to grid origin in pixels
    double offsetXPx, double offsetYPx
) {}
```

Package `ImageDto` continues to use `assetRef` instead of `dataUrl`, plus the same geometry/calibration fields.

`PrimitiveDto`:

```java
public record PrimitiveDto(
    String type,           // ROOM | CORRIDOR | DOOR | REGION | SECRET_DOOR | HAZARD | WALL
    Integer startCol, Integer startRow, Integer endCol, Integer endRow,
    String terrain,
    String key,            // required when type is REGION (pattern: package key)
    String label,
    Boolean playerVisible  // default true; false = DM-only
) {}
```

`MapLayerDto` gains `Boolean playerVisible` (default: false for ANNOTATIONS, true otherwise).

Player projection:

- Drop layers where `playerVisible == false` or type ANNOTATIONS.
- Drop primitives where `playerVisible == false`.
- Never send prep/reward/wave data (already not on map path).

### Spatial units (unchanged, reasserted)

- Token/pin coordinates: **pixels** from top-left.
- Token sizes: **cell counts**.
- Primitive coordinates: **grid columns/rows**.
- Image `x/y/width/height`: **grid-cell units** (existing editor convention).
- Calibration offsets: **pixels**.

---

## File map

| Area | Create | Modify |
|---|---|---|
| DB | `src/main/resources/db/migration/V9__encounter_map_depth.sql` | `FlywayMigrationTest.java` (V8+V9 assertions) |
| Encounter model | `EncounterWave.java`, `WaveStatus.java`, `WaveTriggerKind.java`, `EncounterWaveRepository.java`, `EncounterPrep.java`, `EncounterRewards.java`, `EncounterRewardItem.java`, `EncounterCurrencyGrant.java` | `Encounter.java`, `Combatant.java`, `CombatLogEntry.EntryType` |
| Encounter services | `EncounterPrepService.java` (optional thin helper) | `EncounterService.java`, `EncounterApiController.java`, `EncounterController.java` |
| Map model/services | — | `MapDocumentDto.java`, `MapLayerDto.java`, `GameMapService.java`, `PlayerSafeProjectionService.java` |
| Map editor UI | — | `static/js/map/map-editor.js`, `static/js/map/shared.js`, `templates/maps/editor.html` |
| Encounter UI | `templates/encounter/_prep.html`, `_waves.html`, `_rewards.html`, `_summary-modal.html` | `detail.html`, `_tracker.html`, `_form.html` |
| Package | — | `CampaignContentType.java`, `CampaignManifestV2.java`, `EncounterSectionAdapter.java`, `MapSectionAdapter.java`, `CampaignManifestV2SemanticValidator.java`, `CampaignSemanticSnapshotService.java`, `CampaignSemanticComparator.java`, schemas, `docs/campaign-format-v2.md` |
| Fixtures | extend `feature-complete` and/or `published-adventure-shaped` manifests | contract + round-trip tests |
| Docs | — | `docs/campaign-capabilities.md`, master spec status row after release |

---

### Task 1: Flyway V9 — waves, combatant placement, encounter prep/rewards

**Files:**
- Create: `src/main/resources/db/migration/V9__encounter_map_depth.sql`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Test: same FlywayMigrationTest

**Interfaces:**
- Produces: tables/columns for later tasks; no Java API yet.

- [ ] **Step 1: Write failing Flyway assertions for V8 (fix drift) and V9**

Add/replace the outdated “v7 is latest” assertion with:

```java
@Test
void v8AndV9AreApplied() {
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '8' AND \"success\" = TRUE",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '9' AND \"success\" = TRUE",
            Integer.class)).isEqualTo(1);
}

@Test
void v9CreatesEncounterWaveAndPrepColumns() {
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ENCOUNTER_WAVE'",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ENCOUNTER' AND column_name = 'PREP_JSON'",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ENCOUNTER' AND column_name = 'REWARDS_JSON'",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'COMBATANT' AND column_name = 'WAVE_ID'",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'COMBATANT' AND column_name = 'START_X'",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'COMBATANT' AND column_name = 'PLACEMENT_REGION_KEY'",
            Integer.class)).isEqualTo(1);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=FlywayMigrationTest#v8AndV9AreApplied test`  
Expected: FAIL (V9 missing).

- [ ] **Step 3: Write migration**

```sql
-- V9__encounter_map_depth.sql

ALTER TABLE encounter ADD COLUMN IF NOT EXISTS prep_json CLOB;
ALTER TABLE encounter ADD COLUMN IF NOT EXISTS rewards_json CLOB;

CREATE TABLE encounter_wave (
    id UUID NOT NULL PRIMARY KEY,
    encounter_id UUID NOT NULL,
    wave_key VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    trigger_kind VARCHAR(24) NOT NULL,
    trigger_value VARCHAR(255),
    notes CLOB,
    CONSTRAINT fk_wave_encounter FOREIGN KEY (encounter_id)
        REFERENCES encounter (id) ON DELETE CASCADE,
    CONSTRAINT uq_wave_key_per_encounter UNIQUE (encounter_id, wave_key)
);

CREATE INDEX idx_wave_encounter ON encounter_wave (encounter_id);

ALTER TABLE combatant ADD COLUMN IF NOT EXISTS wave_id UUID;
ALTER TABLE combatant ADD COLUMN IF NOT EXISTS start_x INT;
ALTER TABLE combatant ADD COLUMN IF NOT EXISTS start_y INT;
ALTER TABLE combatant ADD COLUMN IF NOT EXISTS placement_region_key VARCHAR(100);

ALTER TABLE combatant ADD CONSTRAINT fk_combatant_wave
    FOREIGN KEY (wave_id) REFERENCES encounter_wave (id) ON DELETE SET NULL;

-- Backfill a main wave for every existing encounter
INSERT INTO encounter_wave (id, encounter_id, wave_key, name, sort_order, status, trigger_kind)
SELECT RANDOM_UUID(), e.id, 'main', 'Main', 0, 'ACTIVE', 'MANUAL'
FROM encounter e
WHERE NOT EXISTS (
    SELECT 1 FROM encounter_wave w WHERE w.encounter_id = e.id AND w.wave_key = 'main'
);

-- Attach orphan combatants to main wave
UPDATE combatant c
SET wave_id = (
    SELECT w.id FROM encounter_wave w
    WHERE w.encounter_id = c.encounter_id AND w.wave_key = 'main'
)
WHERE c.wave_id IS NULL;
```

(H2 dialect: use `RANDOM_UUID()` as in prior migrations; if existing migrations use a different UUID function, match that style.)

- [ ] **Step 4: Run FlywayMigrationTest**

Run: `./mvnw -q -Dtest=FlywayMigrationTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V9__encounter_map_depth.sql \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java
git commit -m "feat(db): V9 encounter waves, prep/rewards, combatant placement"
```

---

### Task 2: JPA model + typed prep/rewards records

**Files:**
- Create:  
  `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterWave.java`  
  `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/WaveStatus.java`  
  `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/WaveTriggerKind.java`  
  `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterWaveRepository.java`  
  `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPrep.java`  
  `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterRewards.java`  
  `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterWavePersistenceTest.java`
- Modify: `Encounter.java`, `Combatant.java`

**Interfaces:**
- Produces:  
  `EncounterWaveRepository.findByEncounterIdOrderBySortOrderAsc(UUID)`  
  `EncounterPrep` / `EncounterRewards` Jackson-friendly records  
  Entity getters/setters for new columns

- [ ] **Step 1: Write persistence test**

```java
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:wave-persist;DB_CLOSE_DELAY=-1")
class EncounterWavePersistenceTest {
    @Autowired EncounterRepository encounterRepo;
    @Autowired EncounterWaveRepository waveRepo;
    @Autowired CombatantRepository combatantRepo;
    @Autowired CampaignRepository campaignRepo;

    @Test
    void waveAndPlacementRoundTripThroughJpa() {
        Campaign c = new Campaign();
        c.setName("Waves");
        c = campaignRepo.save(c);

        Encounter e = new Encounter();
        e.setCampaign(c);
        e.setName("Ambush");
        e.setPrepJson("{\"tactics\":\"flank\"}");
        e.setRewardsJson("{\"xpTotal\":100}");
        e = encounterRepo.save(e);

        EncounterWave w = new EncounterWave();
        w.setEncounter(e);
        w.setWaveKey("main");
        w.setName("Main");
        w.setSortOrder(0);
        w.setStatus(WaveStatus.ACTIVE);
        w.setTriggerKind(WaveTriggerKind.MANUAL);
        w = waveRepo.save(w);

        Combatant m = new Combatant();
        m.setEncounter(e);
        m.setName("Goblin");
        m.setInitiative(0);
        m.setSortOrder(0);
        m.setMaxHp(7);
        m.setCurrentHp(7);
        m.setKind("MONSTER");
        m.setWave(w);
        m.setStartX(96);
        m.setStartY(144);
        m.setPlacementRegionKey("tree-line");
        combatantRepo.save(m);

        Combatant reloaded = combatantRepo.findById(m.getId()).orElseThrow();
        assertThat(reloaded.getWave().getWaveKey()).isEqualTo("main");
        assertThat(reloaded.getStartX()).isEqualTo(96);
        assertThat(reloaded.getPlacementRegionKey()).isEqualTo("tree-line");
        assertThat(encounterRepo.findById(e.getId()).orElseThrow().getPrepJson())
                .contains("flank");
    }
}
```

- [ ] **Step 2: Run test — expect fail (classes/columns missing)**

Run: `./mvnw -q -Dtest=EncounterWavePersistenceTest test`

- [ ] **Step 3: Implement entities**

`EncounterWave` entity (essential fields):

```java
@Entity
@Table(name = "encounter_wave", indexes = {
    @Index(name = "idx_wave_encounter", columnList = "encounter_id")
})
public class EncounterWave {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(name = "wave_key", nullable = false, length = 100)
    private String waveKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WaveStatus status = WaveStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_kind", nullable = false, length = 24)
    private WaveTriggerKind triggerKind = WaveTriggerKind.MANUAL;

    @Column(name = "trigger_value", length = 255)
    private String triggerValue;

    @Column(columnDefinition = "CLOB")
    private String notes;
    // getters/setters
}
```

Add to `Encounter`: `prepJson`, `rewardsJson` CLOB fields.  
Add to `Combatant`: `wave` ManyToOne, `startX`, `startY`, `placementRegionKey`.

Typed records:

```java
public record EncounterPrep(
        String tactics,
        String morale,
        String surrender,
        String environment,
        String sourceLocator,
        String scalingNotes,
        String sceneKey
) {
    public static EncounterPrep empty() {
        return new EncounterPrep(null, null, null, null, null, null, null);
    }
}

public record EncounterRewards(
        Integer xpTotal,
        Integer xpPerPc,
        List<EncounterCurrencyGrant> currency,
        List<EncounterRewardItem> items,
        List<ContentReference> questObjectiveRefs,
        String notes
) {
    public static EncounterRewards empty() {
        return new EncounterRewards(null, null, List.of(), List.of(), List.of(), null);
    }
}

public record EncounterCurrencyGrant(String currency, double amount) {}
public record EncounterRewardItem(
        String customText,
        ContentReference magicItemRef,
        ContentReference equipmentItemRef,
        int quantity
) {}
```

Repository:

```java
public interface EncounterWaveRepository extends JpaRepository<EncounterWave, UUID> {
    List<EncounterWave> findByEncounterIdOrderBySortOrderAsc(UUID encounterId);
    Optional<EncounterWave> findByEncounterIdAndWaveKey(UUID encounterId, String waveKey);
}
```

- [ ] **Step 4: Run persistence test — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterWavePersistenceTest.java
git commit -m "feat(encounter): wave entity and typed prep/rewards records"
```

---

### Task 3: Ensure main wave on create + library multi-add with grouping

**Files:**
- Modify: `EncounterService.java`, `EncounterApiController.java`, `EncounterController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPrepBuilderTest.java`

**Interfaces:**
- Consumes: `EncounterWaveRepository`, `StatBlockRepository`
- Produces:  
  `EncounterDto create(...)` ensures main wave  
  `List<CombatantDto> addFromLibrary(UUID encounterId, AddFromLibraryRequest req)`  
  `record AddFromLibraryRequest(UUID statBlockId, int quantity, String groupName, UUID waveId, Integer startX, Integer startY, String placementRegionKey)`

- [ ] **Step 1: Write failing tests**

```java
@Test
void createEncounterCreatesMainWave() {
    var dto = service.create(campaignId, new CreateRequest("Ambush", null));
    var waves = waveRepo.findByEncounterIdOrderBySortOrderAsc(dto.id());
    assertThat(waves).hasSize(1);
    assertThat(waves.getFirst().getWaveKey()).isEqualTo("main");
    assertThat(waves.getFirst().getStatus()).isEqualTo(WaveStatus.ACTIVE);
}

@Test
void addFromLibraryCreatesGroupedCombatants() {
    StatBlock goblin = seedGoblin(); // use existing test helpers or library seed
    var enc = service.create(campaignId, new CreateRequest("Ambush", null));
    var created = service.addFromLibrary(enc.id(),
            new AddFromLibraryRequest(goblin.getId(), 3, "Goblin squad", null, 48, 96, "tree-line"));
    assertThat(created).hasSize(3);
    assertThat(created).allMatch(c -> c.groupId() != null && !c.groupId().isBlank());
    assertThat(created.getFirst().groupLeader()).isTrue();
    assertThat(created.subList(1, 3)).allMatch(c -> !c.groupLeader());
    assertThat(created).allMatch(c -> c.maxHp() == goblin.getHp());
    assertThat(created.getFirst().startX()).isEqualTo(48);
    assertThat(created.getFirst().placementRegionKey()).isEqualTo("tree-line");
}

@Test
void addFromLibraryRejectsQuantityLessThanOne() {
    assertThatThrownBy(() -> service.addFromLibrary(encId,
            new AddFromLibraryRequest(statBlockId, 0, null, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run tests — expect fail**

Run: `./mvnw -q -Dtest=EncounterPrepBuilderTest test`

- [ ] **Step 3: Implement**

In `create`:

```java
Encounter saved = encounterRepo.save(e);
ensureMainWave(saved);
return toDto(saved);
```

```java
private EncounterWave ensureMainWave(Encounter e) {
    return waveRepo.findByEncounterIdAndWaveKey(e.getId(), "main")
            .orElseGet(() -> {
                EncounterWave w = new EncounterWave();
                w.setEncounter(e);
                w.setWaveKey("main");
                w.setName("Main");
                w.setSortOrder(0);
                w.setStatus(WaveStatus.ACTIVE);
                w.setTriggerKind(WaveTriggerKind.MANUAL);
                return waveRepo.save(w);
            });
}
```

`addFromLibrary`:

```java
@Transactional
public List<CombatantDto> addFromLibrary(UUID encounterId, AddFromLibraryRequest req) {
    if (req.quantity() < 1 || req.quantity() > 50) {
        throw new IllegalArgumentException("quantity must be 1..50");
    }
    Encounter e = findEntityById(encounterId);
    StatBlock sb = statBlockRepo.findById(req.statBlockId())
            .orElseThrow(() -> new NotFoundException("StatBlock not found: " + req.statBlockId()));
    EncounterWave wave = req.waveId() != null
            ? waveRepo.findById(req.waveId()).orElseThrow()
            : ensureMainWave(e);
    String groupId = UUID.randomUUID().toString();
    String baseName = req.groupName() != null && !req.groupName().isBlank()
            ? req.groupName() : sb.getName();
    List<CombatantDto> out = new ArrayList<>();
    for (int i = 0; i < req.quantity(); i++) {
        CombatantCreateRequest one = new CombatantCreateRequest(
                req.quantity() == 1 ? baseName : baseName + " " + (i + 1),
                sb.getHp() > 0 ? sb.getHp() : 10,
                "MONSTER",
                null,
                sb.getId(),
                null);
        CombatantDto dto = addCombatant(encounterId, one);
        // second update for group/wave/placement
        dto = updateCombatant(dto.id(), new CombatantUpdateRequest(
                null, null, null, null, null, null,
                groupId, i == 0, null, null, null, null, null, null, null, null, null,
                wave.getId(), req.startX(), req.startY(), req.placementRegionKey()));
        out.add(dto);
    }
    return out;
}
```

Extend `CombatantDto`, `CombatantCreateRequest`, `CombatantUpdateRequest`, and `toDto`/`updateCombatant` to include waveId, startX, startY, placementRegionKey.

API:

```java
@PostMapping("/encounters/{id}/combatants/from-library")
public ResponseEntity<List<CombatantDto>> addFromLibrary(
        @PathVariable UUID id, @RequestBody AddFromLibraryRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.addFromLibrary(id, req));
}
```

- [ ] **Step 4: Run tests — PASS; run existing EncounterServiceTest suite**

Run: `./mvnw -q -Dtest=EncounterPrepBuilderTest,EncounterServiceTest test`

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(encounter): main wave bootstrap and library multi-add"
```

---

### Task 4: Wave CRUD + spawn during active encounter

**Files:**
- Modify: `EncounterService.java`, `EncounterApiController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterWaveServiceTest.java`

**Interfaces:**
- Produces:  
  `WaveDto`, `CreateWaveRequest`, `UpdateWaveRequest`  
  `List<WaveDto> listWaves(UUID encounterId)`  
  `WaveDto createWave(...)`  
  `WaveDto updateWave(...)`  
  `void deleteWave(...)` — only if no combatants or reassign to main  
  `EncounterDto spawnWave(UUID encounterId, UUID waveId)`  
  Tracker `getCombatants` filters to ACTIVE-wave combatants only (plus null wave treated as main)

- [ ] **Step 1: Failing tests**

```java
@Test
void reserveWaveCombatantsHiddenFromInitiativeUntilSpawn() {
    var enc = service.create(campaignId, new CreateRequest("Boss", null));
    service.activate(enc.id());
    var reserve = service.createWave(enc.id(), new CreateWaveRequest(
            "reinforcements", "Reinforcements", WaveTriggerKind.ROUND, "3", null));
    StatBlock wolf = seedWolf();
    service.addFromLibrary(enc.id(), new AddFromLibraryRequest(
            wolf.getId(), 2, "Wolves", reserve.id(), null, null, null));

    assertThat(service.getCombatants(enc.id())).isEmpty(); // only main, empty

    service.spawnWave(enc.id(), reserve.id());
    assertThat(service.getCombatants(enc.id())).hasSize(2);
    assertThat(service.getLog(enc.id()).getLast().type()).isEqualTo("WAVE_SPAWNED");
}

@Test
void spawnWaveRequiresActiveEncounter() {
    var enc = service.create(campaignId, new CreateRequest("Boss", null));
    var reserve = service.createWave(enc.id(), new CreateWaveRequest(
            "r1", "R1", WaveTriggerKind.MANUAL, null, null));
    assertThatThrownBy(() -> service.spawnWave(enc.id(), reserve.id()))
            .isInstanceOf(IllegalStateException.class);
}

@Test
void undoRefusesWaveSpawnBoundary() {
    // activate, spawn, undo → 409 / IllegalStateException
}
```

- [ ] **Step 2: Run — fail**

- [ ] **Step 3: Implement**

Filter:

```java
public List<CombatantDto> getCombatants(UUID encounterId) {
    return combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
            .filter(this::isOnActiveWave)
            .map(this::toDto)
            .toList();
}

private boolean isOnActiveWave(Combatant c) {
    if (c.getWave() == null) return true;
    return c.getWave().getStatus() == WaveStatus.ACTIVE;
}
```

Spawn:

```java
@Transactional
public EncounterDto spawnWave(UUID encounterId, UUID waveId) {
    Encounter e = findEntityById(encounterId);
    if (e.getStatus() != Encounter.Status.ACTIVE) {
        throw new IllegalStateException("Encounter must be ACTIVE to spawn a wave");
    }
    EncounterWave wave = waveRepo.findById(waveId)
            .orElseThrow(() -> new NotFoundException("Wave not found: " + waveId));
    if (!wave.getEncounter().getId().equals(encounterId)) {
        throw new IllegalArgumentException("Wave does not belong to encounter");
    }
    if (wave.getStatus() == WaveStatus.ACTIVE || wave.getStatus() == WaveStatus.DEPLETED) {
        throw new IllegalStateException("Wave already " + wave.getStatus());
    }
    wave.setStatus(WaveStatus.ACTIVE);
    waveRepo.save(wave);
    placeTokensForWave(e, wave);
    logEntry(encounterId, CombatLogEntry.EntryType.WAVE_SPAWNED, "",
            "{\"waveKey\":\"" + wave.getWaveKey() + "\"}");
    // boundary marker: nothing further
    return toDto(e);
}
```

`placeTokensForWave`: for each combatant on wave with map + (startX/Y or region key), create or update linked Token at those pixels; region key resolves to region primitive center when positions null.

Add API routes under `/api/v1/encounters/{id}/waves` (list/create) and `/api/v1/encounters/{id}/waves/{waveId}/spawn`.

- [ ] **Step 4: Tests pass; full encounter service tests green**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(encounter): wave CRUD and runtime spawn with log boundary"
```

---

### Task 5: Undo boundary enforcement

**Files:**
- Modify: `EncounterService.undo`, `CombatLogEntry.EntryType`, `EncounterApiController`
- Create/modify: `EncounterUndoBoundaryTest.java`

- [ ] **Step 1: Test**

```java
@Test
void cannotUndoEncounterActivated() {
    var enc = service.create(...);
    service.activate(enc.id());
    assertThatThrownBy(() -> service.undo(enc.id()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("boundary");
}

@Test
void canUndoDamageAfterActivate() {
    var enc = activateWithGoblin();
    var c = service.getCombatants(enc.id()).getFirst();
    service.applyDamage(c.id(), 2);
    service.undo(enc.id());
    assertThat(service.getCombatant(c.id()).currentHp()).isEqualTo(c.maxHp());
}
```

- [ ] **Step 2–4: Implement boundary set; map to HTTP 409 in controller advice or controller catch; pass tests; commit**

```java
private static final Set<CombatLogEntry.EntryType> UNDO_BOUNDARIES = EnumSet.of(
        CombatLogEntry.EntryType.ENCOUNTER_ACTIVATED,
        CombatLogEntry.EntryType.ENCOUNTER_ENDED,
        CombatLogEntry.EntryType.SESSION_END,
        CombatLogEntry.EntryType.WAVE_SPAWNED,
        CombatLogEntry.EntryType.REWARD_APPLIED
);

@Transactional
public void undo(UUID encounterId) {
    List<CombatLogEntry> log = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId);
    if (log.isEmpty()) {
        throw new IllegalStateException("Nothing to undo");
    }
    CombatLogEntry last = log.get(log.size() - 1);
    if (UNDO_BOUNDARIES.contains(last.getType())) {
        throw new IllegalStateException("Cannot undo past a session boundary: " + last.getType());
    }
    // existing replay implementation...
}
```

```bash
git commit -am "fix(encounter): refuse undo across encounter lifecycle boundaries"
```

---

### Task 6: Prep/rewards update API + end-encounter summary + reward confirm

**Files:**
- Modify: `EncounterService`, controllers, templates
- Create: `EncounterCompletionTest.java`
- Touch: `TreasuryService`, `LedgerService`, `QuestService` (confirm only — reuse existing assign/XP/objective APIs)

**Interfaces:**
- Produces:  
  `void updatePrep(UUID id, EncounterPrep prep)`  
  `void updateRewards(UUID id, EncounterRewards rewards)`  
  `EncounterEndResult endEncounterWithSummary(UUID id)`  
  `record EncounterEndResult(EncounterDto encounter, EncounterSummaryDto summary)`  
  `void applyRewards(UUID encounterId, ApplyRewardsRequest req)`  
  `record ApplyRewardsRequest(boolean awardXp, List<UUID> partyMemberIds, boolean createLedger, boolean applyItems, boolean applyQuestObjectives)`

- [ ] **Step 1: Tests**

```java
@Test
void endEncounterBuildsDeterministicSummary() {
    var enc = activateWithTwoGoblins();
    service.applyDamage(goblin1, 7); // defeat
    service.nextTurn(enc.id());
    var result = service.endEncounterWithSummary(enc.id());
    assertThat(result.encounter().status()).isEqualTo("DONE");
    assertThat(result.summary().defeatedCount()).isEqualTo(1);
    assertThat(result.summary().rounds()).isGreaterThanOrEqualTo(1);
    assertThat(result.summary().rewardsDraft().xpTotal()).isEqualTo(200); // from prep rewards
}

@Test
void applyRewardsCreatesLedgerAndXpOnlyOnConfirm() {
    var enc = doneEncounterWithRewards();
    int ledgerBefore = ledgerCount(campaignId);
    service.applyRewards(enc.id(), new ApplyRewardsRequest(true, List.of(pcId), true, true, false));
    assertThat(ledgerCount(campaignId)).isGreaterThan(ledgerBefore);
    // second apply should be blocked or no-op with REWARD_APPLIED boundary
    assertThatThrownBy(() -> service.applyRewards(enc.id(), ...))
            .hasMessageContaining("already");
}
```

- [ ] **Step 2: Implement summary computation**

```java
public EncounterSummaryDto buildSummary(UUID encounterId) {
    Encounter e = findEntityById(encounterId);
    List<Combatant> all = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
    List<CombatLogEntry> log = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId);
    int damage = log.stream()
            .filter(l -> l.getType() == EntryType.DAMAGE)
            .mapToInt(l -> extractAmount(l.getPayload()))
            .sum();
    List<String> waves = log.stream()
            .filter(l -> l.getType() == EntryType.WAVE_SPAWNED)
            .map(l -> extractWaveKey(l.getPayload()))
            .toList();
    return new EncounterSummaryDto(
            e.getId(), e.getName(), e.getRound(),
            all.size(),
            (int) all.stream().filter(Combatant::isDefeated).count(),
            (int) all.stream().filter(c -> "PC".equals(c.getKind()) && c.isDefeated()).count(),
            damage,
            waves,
            all.stream().filter(Combatant::isDefeated).map(Combatant::getName).toList(),
            readRewards(e),
            Instant.now()
    );
}
```

`applyRewards`:

1. Load rewards_json.
2. If XP: call existing party/sheet XP award for selected members (`xpPerPc` or split `xpTotal`).
3. If currency: create ledger GAIN entries via `LedgerService`.
4. If items: `TreasuryService.create` assignments to party stash or selected holders.
5. If quest objectives: call `QuestService` status update only for listed objective keys that belong to campaign — never auto-complete without DM flag in request.
6. Log `REWARD_APPLIED`.

Change form POST end + API end to return summary fragment/JSON. Tracker and detail page show modal with Confirm rewards / Skip.

- [ ] **Step 3–5: Implement UI fragments, pass tests, commit**

```bash
git commit -am "feat(encounter): prep/rewards, completion summary, DM-confirmed apply"
```

---

### Task 7: Encounter prep UI (detail page)

**Files:**
- Create: `templates/encounter/_prep.html`, `_waves.html`, `_rewards.html`, `_library-add.html`, `_summary-modal.html`
- Modify: `detail.html`, `EncounterController.java` (model attrs: waves, prep, rewards, difficulty)

- [ ] **Step 1: Template contract test** (pattern from `QuestTemplateContractTest`)

```java
@Test
void detailRendersPrepWavesRewardsAndLibraryAdd() {
    // MockMvc GET detail
    assertThat(html).contains("data-encounter-prep", "data-encounter-waves",
            "from-library", "Difficulty estimate", "Estimate:");
}
```

- [ ] **Step 2: Implement fragments**

`_library-add.html` Alpine/HTMX:

- Search input → `GET /library/statblocks?search=` (existing library search fragment or JSON endpoint — if only HTML search exists, call `StatBlockService.search` via new thin JSON endpoint `GET /api/v1/library/statblocks?q=` returning `{id,name,cr,hp,type}`).
- Quantity number (default 1), optional group name, wave select, optional region key.
- Submit → `POST /api/v1/encounters/{id}/combatants/from-library` via `dm-request.js`.

`_waves.html`: list waves with status badges; add wave form; Spawn button when encounter ACTIVE and wave PENDING/RESERVE.

`_prep.html` / `_rewards.html`: forms posting to update endpoints.

- [ ] **Step 3: Manual smoke path documented in commit message; automated contract test green**

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(encounter): prep UI for library add, waves, rewards"
```

---

### Task 8: Tracker wave prompts + HAZARD kind

**Files:**
- Modify: `_tracker.html`, `EncounterService` kind validation, schema later in Task 11
- Test: `EncounterHazardCombatantTest.java`

- [ ] **Step 1: Allow kind HAZARD**

```java
private static final Set<String> KINDS = Set.of("PC", "NPC", "MONSTER", "OBJECT", "HAZARD");
```

HAZARD defaults maxHp=1, does not require statblock; appears in initiative; player projection already hides `hidden` combatants — keep HAZARD visible unless DM hides it.

- [ ] **Step 2: Tracker banner**

When active encounter has PENDING/RESERVE waves whose `trigger_kind=ROUND` and `trigger_value` equals current round, show:

```html
<div class="banner banner-warning" th:if="${pendingWavePrompts}">
  Wave ready: <span th:text="${wave.name}">Reinforcements</span>
  <button hx-post=".../waves/{id}/spawn">Spawn</button>
</div>
```

Also list manual pending waves in a compact select.

- [ ] **Step 3: Tests + commit**

```bash
git commit -am "feat(encounter): hazard initiative kind and tracker wave prompts"
```

---

### Task 9: Map document v2 alignment — named regions, calibration, playerVisible

**Files:**
- Modify: `MapDocumentDto.java`, `MapLayerDto.java`, `map-document-v2.schema.json`, `PlayerSafeProjectionService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentContractTest.java`, `PlayerSafeMapProjectionTest.java`

**Interfaces:**
- Produces: schemaVersion **2** for new saves; reader accepts 1 and 2
- `PrimitiveDto` with key/label/playerVisible
- `ImageDto` with rotationDeg/locked/calibration
- Layer `playerVisible`

- [ ] **Step 1: Failing tests**

```java
@Test
void schemaVersionIsTwoForNewDocuments() {
    var doc = MapDocumentDto.createDefault(20, 15, 48);
    assertThat(doc.schemaVersion()).isEqualTo(2);
}

@Test
void playerProjectionStripsDmOnlyLayersAndPrimitives() throws Exception {
    // document with ANNOTATIONS + REGION playerVisible=false + IMAGE playerVisible=true
    MapDocumentDto projected = projection.projectMapDocument(map);
    assertThat(projected.layers()).noneMatch(l -> l.type() == LayerType.ANNOTATIONS);
    assertThat(projected.primitives()).allMatch(p -> !Boolean.FALSE.equals(p.playerVisible()));
}
```

- [ ] **Step 2: Implement DTO + projection**

```java
public static final int CURRENT_SCHEMA_VERSION = 2;

// ImageDto
public record ImageDto(
        String dataUrl,
        double x, double y, double width, double height,
        Double rotationDeg,
        Boolean locked,
        CalibrationDto calibration
) {
    public ImageDto(String dataUrl, double x, double y, double width, double height) {
        this(dataUrl, x, y, width, height, 0.0, false, null);
    }
}

public record CalibrationDto(
        double ax, double ay, double bx, double by,
        double cellsBetween,
        double offsetXPx, double offsetYPx
) {}

// PrimitiveDto — add key, label, playerVisible; expand type enum in schema
```

Update `map-document-v2.schema.json` `$defs.primitive` and `$defs.image` accordingly. Keep `additionalProperties: false`.

Jackson: when reading schemaVersion 1 documents, still load; on next save write 2.

- [ ] **Step 3: Pass tests; commit**

```bash
git commit -am "feat(map): named regions, calibration fields, playerVisible projection"
```

---

### Task 10: Map editor published-map workflow UI

**Files:**
- Modify: `static/js/map/map-editor.js`, `templates/maps/editor.html`, optionally `shared.js`
- Create: unit-ish tests if project has JS tests; otherwise Java contract test that saved document retains calibration after API round-trip (`GameMapServiceTest`)

**Behavior to implement:**

1. **Import** (exists) — keep file picker.
2. **Rotate** — toolbar control sets `image.rotationDeg` in 90° steps or free number; Konva `rotation` + offset.
3. **Crop** — select rectangle on image → rewrite `dataUrl` via canvas crop → reset x/y/width/height to cropped content fitted to grid.
4. **Calibrate** — tool `calibrate`: click point A, click point B, prompt “How many cells between these points?” (default 1). Compute:

```javascript
calibrateFromPoints(ax, ay, bx, by, cellsBetween) {
    const distPx = Math.hypot((bx - ax) * this.cellSizePx, (by - ay) * this.cellSizePx);
    // points are in cell units already in editor — convert consistently
    const distCellUnits = Math.hypot(bx - ax, by - ay);
    const newCellSize = Math.max(8, Math.round(distPx / cellsBetween));
    // Prefer adjusting image scale so that distance maps to cellsBetween * cellSizePx
    // without changing GameMap.cellSizePx unless DM confirms "Apply to map grid"
    const scale = (cellsBetween) / distCellUnits;
    const img = this.layerDto('image').image;
    img.width *= scale;
    img.height *= scale;
    img.calibration = { ax, ay, bx, by, cellsBetween, offsetXPx: img.x * this.cellSizePx, offsetYPx: img.y * this.cellSizePx };
    this.markDirty();
}
```

Also offer **Apply cell size to map** calling existing map update API when DM confirms.

5. **Lock image** — `image.locked = true` prevents drag/transform until unlocked.
6. **Named region** — when finishing region tool, prompt for `key` (pattern `^[a-z0-9][a-z0-9._-]{0,99}$`) and `label`; store on primitive; show label on canvas.
7. **Player visible toggle** — layer panel checkbox; region property checkbox.

- [ ] **Step 1: Service test that PUT document with region key + calibration persists**

```java
@Test
void saveDocumentPersistsRegionKeyAndCalibration() {
    String doc = """
      {"schemaVersion":2,"grid":{"width":10,"height":10,"cellSizePx":48,"gridType":"square"},
       "layers":[{"id":"image","name":"Background","type":"IMAGE","visible":true,"locked":false,
         "image":{"dataUrl":"data:image/png;base64,aa","x":0,"y":0,"width":10,"height":10,
                  "rotationDeg":0,"locked":true,
                  "calibration":{"ax":0,"ay":0,"bx":1,"by":0,"cellsBetween":1,"offsetXPx":0,"offsetYPx":0}}}],
       "primitives":[{"type":"REGION","startCol":1,"startRow":1,"endCol":3,"endRow":3,
                      "key":"tree-line","label":"Tree line","playerVisible":true}]}
      """;
    service.replaceDocument(mapId, doc, version);
    MapDocumentDto reloaded = service.getDocument(mapId);
    assertThat(reloaded.primitives().getFirst().key()).isEqualTo("tree-line");
    assertThat(reloaded.layers().getFirst().image().locked()).isTrue();
}
```

- [ ] **Step 2–4: Implement editor methods + toolbar buttons; pass API test; commit**

```bash
git commit -am "feat(map): published-map calibrate, crop, rotate, lock, named regions"
```

---

### Task 11: Package v2 schema, content types, adapters, semantic validation

**Files:**
- Modify:  
  `CampaignContentType.java` — add `ENCOUNTER_WAVE`  
  `CampaignManifestV2.java` — extend `EncounterDto`, add `WaveDto`, extend combatant/map image/primitive  
  `campaign-format-v2.schema.json`, `map-document-v2.schema.json`  
  `EncounterSectionAdapter.java`, `MapSectionAdapter.java`  
  `CampaignManifestV2SemanticValidator.java`  
  `CampaignSemanticSnapshotService` / `Comparator`  
  `docs/campaign-format-v2.md`
- Test: extend `EncounterSectionAdapterTest`, `CampaignManifestV2ContractTest`, `CampaignCompleteRoundTripTest`

**Package shapes (add to encounter object):**

```json
{
  "key": "crypt-guardians",
  "name": "Crypt Guardians",
  "status": "PLANNED",
  "prep": {
    "tactics": "Defend the altar",
    "morale": "Fight to the death",
    "surrender": null,
    "environment": "Desecrated ground",
    "sourceLocator": "Adv p.40",
    "scalingNotes": null,
    "sceneKey": "crypt-entrance"
  },
  "rewards": {
    "xpTotal": 450,
    "currency": [{"currency": "gp", "amount": 25}],
    "items": [{"customText": "Silver chalice", "quantity": 1}],
    "questObjectiveRefs": [{"type": "OBJECTIVE", "key": "secure-relic"}],
    "notes": null
  },
  "waves": [
    {
      "key": "main",
      "name": "Main",
      "sortOrder": 0,
      "status": "ACTIVE",
      "triggerKind": "MANUAL",
      "triggerValue": null,
      "notes": null
    },
    {
      "key": "reinforcements",
      "name": "Reinforcements",
      "sortOrder": 1,
      "status": "RESERVE",
      "triggerKind": "ROUND",
      "triggerValue": "3",
      "notes": "From the west door"
    }
  ],
  "combatants": [
    {
      "key": "skeleton-1",
      "name": "Skeleton 1",
      "waveKey": "main",
      "startX": 96,
      "startY": 144,
      "placementRegionKey": "altar",
      "kind": "MONSTER"
      // ...existing required fields...
    }
  ],
  "combatLog": [],
  "lairActionTriggered": false
}
```

Semantic checks:

- Wave keys unique per encounter.
- Combatant `waveKey` must resolve.
- `placementRegionKey` if set should exist on linked map’s REGION primitives (WARNING if map missing region; ERROR if map present and key absent — choose WARNING with preview for conversion friendliness; document choice: **WARNING**).
- Reward objective refs must resolve to package objectives when present (ERROR).
- Region primitive keys unique per map; pattern match package key.
- Image assetRef still required for package IMAGE layers (existing).

- [ ] **Step 1: Contract test that new fixture fragment validates**

- [ ] **Step 2: Implement adapter export/import for waves, prep, rewards, combatant placement, map primitives/image fields**

Export waves before combatants; import waves first then attach combatants by waveKey.

Register package keys:

```java
context.key(CampaignContentType.ENCOUNTER_WAVE, wave.getId(), wave.getWaveKey());
```

Prefer stored `waveKey` as package key when it matches `^[a-z0-9][a-z0-9._-]{0,99}$`.

- [ ] **Step 3: Round-trip test green for feature-complete + new fields**

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(package): encounter waves/rewards and map region/calibration round-trip"
```

---

### Task 12: Flagship fixtures + capabilities + player-safety

**Files:**
- Modify:  
  `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json`  
  `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json` (optional regions)  
  `docs/campaign-capabilities.md`  
  `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` status row 9 → `IMPLEMENTED` only after full gate  
  Player-safety tests under `live/` or smoke

- [ ] **Step 1: Extend feature-complete encounter with wave + rewards + prep; map primitive with key**

- [ ] **Step 2: Run full suite**

```bash
./mvnw -q test
```

Expected: all tests pass (including `CampaignCompleteRoundTripTest`).

- [ ] **Step 3: Update capabilities**

Add rows:

| Capability | Status | Notes |
|---|---|---|
| Encounter library multi-add / groups | `SUPPORTED` | Quantity + group leader |
| Encounter waves / reserves / spawn | `SUPPORTED` | Manual + round prompts |
| Encounter prep notes + structured rewards | `SUPPORTED` | DM-confirmed apply |
| Encounter completion summary | `SUPPORTED` | Deterministic from log |
| Undo lifecycle boundaries | `SUPPORTED` | Activate/end/wave/reward |
| Published map calibrate/crop/rotate/lock | `SUPPORTED` | Image-first workflow |
| Named map regions with keys | `SUPPORTED` | Scene/encounter placement |
| DM/player map layer split (shared tokens) | `SUPPORTED` | playerVisible flags |
| Authoritative 2024 encounter difficulty | `PARTIAL` | Still labeled estimate |
| Full fog of war gameplay | `UNSUPPORTED` | Deferred P3; document may store reveal-ready shapes later |

- [ ] **Step 4: Player-safety test**

```java
@Test
void playerPayloadOmitsPrepRewardsAndDmOnlyRegions() {
    // activate presentation; fetch player state JSON
    assertThat(json).doesNotContain("tactics", "rewards", "secret-region-key");
}
```

- [ ] **Step 5: Commit**

```bash
git commit -am "test(docs): encounter/map depth fixtures, capabilities, player safety"
```

---

### Task 13: Cockpit integration smoke + final gate

**Files:**
- Modify: session cockpit encounter rail if it lists combatants (show pending wave count badge only — no DM tactics text on player path)
- Optional: Playwright smoke step for spawn wave + end summary modal

- [ ] **Step 1: Ensure cockpit activate still uses `EncounterService.activate` and tracker fragment; wave banner appears when applicable**

- [ ] **Step 2: Run**

```bash
./mvnw -q test
./mvnw -q -Dtest=FlywayMigrationTest,EncounterWaveServiceTest,EncounterCompletionTest,CampaignCompleteRoundTripTest,PlayerSafeMapProjectionTest test
```

- [ ] **Step 3: Mark master design delivery item 9 as `IMPLEMENTED` only if acceptance contract checkboxes are satisfied**

- [ ] **Step 4: Commit**

```bash
git commit -am "docs: mark encounter and map depth delivery ready"
```

---

## Self-review (plan vs spec)

### Spec coverage (Workstream G + H)

| Spec requirement | Task(s) |
|---|---|
| Library creature selection + quantity + grouping | 3, 7 |
| Party-based scaling variants | **Partial** — `scalingNotes` free text in prep (Task 2/6); automated scaling variants deferred as YAGNI unless time remains (optional follow-up: duplicate encounter at ±N CR). Document as PARTIAL in capabilities. |
| Waves, reinforcements, triggers, reserves | 1–4, 8 |
| Starting positions / placement regions | 1–3, 9–10 |
| Tactics, morale, surrender, environment | 2, 6, 7 |
| Traps/hazards / non-creature initiative | 8 (HAZARD kind); scene TRAP sections already exist from item 6 |
| Rewards, loot, XP, quest consequences | 6–7, 11 |
| Source locator + scene linkage | 2, 6 (`sceneKey` + sourceLocator) |
| Preserve tracker, legendary/lair, undo, player projection | 4–5, 9, 12 |
| Visible failure handling | Reuse `dm-request.js` (P0); no empty catches on new UI |
| Combat log export/import | Existing + new entry types (11) |
| Undo scope + boundaries | 5 |
| Wave prompts | 4, 8 |
| Deterministic summary + DM-confirmed rewards | 6 |
| Difficulty estimate honesty | Keep Task 6/7 labels; no fake 2024 exactness |
| Image-first published map, crop/rotate, two-point calibrate, lock | 9–10 |
| DM/player variants without duplicating tokens | 9–10 (`playerVisible`) |
| Named regions | 9–10 |
| Image-only maps OK | Already true; do not force terrain paint |
| Runtime semantic primitives advisory | Extended types; no automation |
| Fog deferred but not precluded | Explicit out of scope; no schema that forbids future reveal fields |
| Spatial unit contract | Restated; validators already check token bounds |

### Placeholder scan

No TBD/TODO implementation steps left without code or exact commands. Optional party auto-scaling is explicitly deferred and labeled PARTIAL.

### Type consistency

- `WaveStatus` / `WaveTriggerKind` names used consistently across Tasks 1–4, 11.
- Package uses `waveKey` on combatants; JPA uses `wave` relation + `wave_key` on wave entity.
- `EncounterPrep` / `EncounterRewards` names shared by service, package DTO mapping, and UI.
- Map `schemaVersion = 2` aligned across DTO, schema, and tests.
- Flyway version **V9** after existing V8.

### Out of scope (do not implement here)

- Full fog-of-war / vision automation (P3)
- Authoritative 2024 difficulty tables without SRD data
- Automated party CR scaling variants beyond notes
- Audio assets, travel, world graph
- Documentation/agent SDK release (delivery item 10)
- Second encounter tracker or second map editor

