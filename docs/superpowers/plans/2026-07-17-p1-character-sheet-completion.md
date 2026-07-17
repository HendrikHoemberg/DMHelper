# Character-Sheet Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver delivery item 8 of the all-in-one DM readiness specification: complete character creation/advancement, at-table sheet actions, inventory-aware party state, rest preview, multi-member party operations, and package/export fidelity for the extended sheet model—so the app can replace books and spreadsheets for party/character management at the table.

**Architecture:** Keep `CharacterSheet` as the rules/build source (abilities, class levels, proficiencies, spells, resources, features, attacks) and `PartyMember` as the live combat projection (current/temp HP, death saves, conditions, inspiration, exhaustion). Normalize the existing `classSourceKey`/`classRef` storage split so SheetEngine, UI create/level-up, and package import/export share one internal class-level contract. Add typed JSON records (not new micro-tables for every field) for attacks, features, overrides-with-reason, death saves, and rest previews. Deepen treasury integration on the sheet rather than inventing a second inventory system. Encounter combatants that reference a party member sync HP/conditions through one explicit ownership rule. Extend package-v2 `sheet` / `partyMember` schema and `PartySectionAdapter` so round-trip preserves every new persistent field.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2, Jackson, Thymeleaf, HTMX, Alpine, JSON Schema draft 2020-12, Maven Wrapper, JUnit/AssertJ/MockMvc, existing Playwright smoke coverage.

## Global Constraints

- Master design Workstream F (§11.1–11.3) and delivery item 8 are authoritative. Do not start encounter/map depth (item 9), documentation/agent SDK release (item 10), or P3 world/travel/fog/audio work in this plan.
- Offline/local-first: no CDN, no new frontend build chain, no network generative rules services.
- The engine derives only what installed SRD/custom rules data supports. Missing or custom mechanics stay as explicit editable resources/features—never silently invent DCs, spell lists, or ASI tables that are not in data.
- Approximations must be labeled (same honesty rule as encounter difficulty). Do not present incomplete 2024 subclass choice automation as complete rules coverage.
- Every new campaign-owned field is **persistent-exported** unless explicitly documented as transient. Export → import must preserve persistent meaning.
- Stable package keys remain the reference identity. Display names are presentation only.
- Player-safe projection must never receive sheet overrides, death-save failures/successes beyond what is already player-visible combat state, provenance, or private notes. Extend `PlayerSafeProjectionService` tests if any new field could leak.
- Use reviewed Flyway migration `V8__…`. Do not rely on Hibernate schema generation in production.
- Prefer extending `SheetEngine`, `SheetService`, `PartySectionAdapter`, and existing templates over parallel sheet systems.
- Complete every task with focused tests before moving on. Prefer TDD: failing test → implement → pass → commit.

---

## Audit result: what is already implemented

Verified against the master design status table (spec §22) and repository evidence as of 2026-07-17:

| # | Delivery item | Spec claim | Audit status | Key evidence |
|---|---|---|---|---|
| 1 | P0 runtime reliability | `IMPLEMENTED` | **Correctly implemented** | Quick notes without `[[${…}]]`, `ContentDestinationRegistry`, `dm-request.js` failure handling, package asset safety, difficulty labeled estimate; minor residual: map-editor keepalive empty catch |
| 2 | Campaign contract v1 repair | `IMPLEMENTED` | **Correctly implemented** | Closed schemas, unified dry-run/import validators, v1 fixtures |
| 3 | Package v2 foundation | `IMPLEMENTED` | **Correctly implemented** | ZIP/JSON, keys (V3), validators, preview, staged assets; `assets/audio/` not in allow-list (acceptable / later) |
| 4 | Complete round-trip | `IMPLEMENTED` | **Correctly implemented** | Section adapters, semantic snapshot/compare, flagship fixtures |
| 5 | Session cockpit | `IMPLEMENTED` | **Correctly implemented** | Real `/session` page, rails, lifecycle, draft log, resume order (V4) |
| 6 | Structured adventure/quest | `IMPLEMENTED` | **Correctly implemented** | Scene sections/checks/transitions/links, quests/objectives (V5–V6) |
| 7 | Custom compendium expansion | `IMPLEMENTED` | **Correctly implemented** | Ownership + provenance for library types (V7); non-engine types as custom RULE |
| **8** | **Character-sheet completion** | **`PLANNED`** | **Accurate — partial baseline only** | See gap table below |
| 9–11 | Encounter/map depth, docs/agent SDK, P3 | `PLANNED` | Out of scope | — |

### Current character baseline (keep; do not regress)

| Area | What exists | Primary files |
|---|---|---|
| Model | `CharacterSheet` with ability scores, class levels JSON, proficiencies JSON, species/background, feat refs, XP, overrides, hitDiceUsed, spellSlotsUsed; `SheetResource`; `SheetSpellReference` | `sheet/data/*` |
| Engine | Mods, PB, saves, skills+expertise, passives, max HP from hit die rolls, multiclass slots, pact vs full/half, spell DC/attack, initiative/speed/AC defaults | `SheetEngine` |
| Service | Create, update, level-up, short/long rest, resources, spells, award XP, batch rest/XP API | `SheetService`, `SheetApiController` |
| UI | Create form (class + scores + species/background), detail with saves/skills/spells/resources/rests/level-up, treasury fragment | `templates/sheet/*` |
| Party | Roster, active flag, combat projection fields; session attendance many-to-many | `PartyMember`, `CampaignSession` |
| Package | Sheet nested under party member; resources/spells/refs export | `PartySectionAdapter`, schema `$defs/sheet` |
| Tests | `SheetEngineTest`, `SheetServiceTest`, party adapter tests, round-trip fixtures | `src/test/…` |

### Hard gaps this plan closes (Workstream F)

| Gap | Spec § | Severity |
|---|---|---|
| **classSourceKey vs classRef split** — UI/engine use `classSourceKey`; package import writes `classRef`; export reads `classRef` only | §11.1, §4.3 | **P0 bug inside item 8** |
| No guided subclass / multiclass / skill / ASI / feat choice flow | §11.1 | High |
| Proficiencies (tools/armor/weapons/languages/expertise) incomplete UX | §11.1 | High |
| Overrides without reason/source | §11.1 | Medium |
| No first-class attacks (expression, damage, range, ammo) | §11.2 | High |
| No actions / BA / reactions / feature text surface | §11.2 | High |
| No death saves, exhaustion, inspiration, concentration, sheet conditions | §11.2 | High |
| Temp HP only on combatants, not party/sheet | §11.2 | High |
| Inventory only via treasury fragment; no equip/carry/weight | §11.2 | High |
| Spells list without prepare toggle, slot spend, searchable text | §11.2 | Medium |
| Rest applies immediately; no preview of recoveries | §11.2 | Medium |
| Multi-member condition/loot ops incomplete; rest/XP APIs exist but thin UI | §11.3 | Medium |
| No explicit map↔sheet↔party HP ownership rule | §11.3 | High |
| Package schema missing new fields | §7.8 | High |

---

## Delivery item 8 acceptance contract

- [ ] Internal class-level JSON uses **one** canonical key (`classSourceKey`) end-to-end; package external format still uses `classRef` ContentReference; import/export dual-read legacy internal `classRef` strings.
- [ ] Character creation supports: class, subclass (when data has subclasses), starting level, ability scores, skill/tool/language choices from class+background data, species, background, optional feats, initial hit die rolls (roll or average).
- [ ] Level-up supports: choose class (including multiclass new class), HP roll or average, ASI or feat when level grants it (explicit choice record; engine does not invent ASI schedule beyond class feature data when available, otherwise DM marks choice).
- [ ] Sheet shows attacks with roll expression, damage, range, properties, ammunition remaining.
- [ ] Sheet shows actions / bonus actions / reactions and feature text (manual entries + optional class feature extract).
- [ ] Live state: current/max/temp HP, death saves, exhaustion, conditions, concentration, inspiration—editable on sheet and party summary; package round-trips them.
- [ ] Inventory on sheet lists treasury assignments with equipped/carried/stashed/consumed/lost; quantity decrement for stackables; optional weight when equipment data has weight.
- [ ] Spell list: search, prepare toggle, slot spend/recover UI, full text expandable.
- [ ] Rest preview returns exactly what will recover; apply requires explicit confirmation.
- [ ] Batch rest, XP (and milestone level set), condition apply, and loot assign can target one or many members from party/sheets overview.
- [ ] HP ownership rule is implemented and tested: party member is projection; sheet engine owns max HP derivation; linked combatants sync per documented rule.
- [ ] Flagship v2 fixtures extended; semantic round-trip passes for all new persistent fields.
- [ ] Capability matrix marks character-sheet completion areas `SUPPORTED` / `PARTIAL` accurately after gates pass.
- [ ] Existing sheet, party, session, library, and round-trip tests remain green.

---

## Domain contracts (authoritative)

### Class level internal JSON (CharacterSheet.classLevels CLOB)

```json
[
  {
    "classSourceKey": "srd-2024_fighter",
    "subclassSourceKey": "srd-2024_fighter_champion",
    "level": 5,
    "hitDieRolls": [8, 6, 7, 5]
  }
]
```

- `classSourceKey` — required; base class or (when no separate base) the class entry used for hit die / PB tables.
- `subclassSourceKey` — optional; points at a `CharacterClass` row whose `subclassOf` equals the base `sourceKey`.
- `hitDieRolls` — HP gains for levels 2..n (level 1 uses full hit die in engine, as today).
- Package export maps `classSourceKey` → `classRef` ContentReference and optional `subclassRef`.

### Live combat state (PartyMember columns)

| Field | Type | Notes |
|---|---|---|
| `tempHp` | int ≥ 0 | Default 0 |
| `inspiration` | boolean | Default false |
| `exhaustion` | int 0–6 | Default 0 |
| `deathSaveSuccesses` | int 0–3 | Default 0 |
| `deathSaveFailures` | int 0–3 | Default 0 |
| `concentratingOn` | string(255) nullable | Free text or spell name |
| `conditionsJson` | CLOB | Array of `{ "sourceKey", "name", "notes", "durationRounds?" }` matching encounter condition shape where practical |

Current/max HP already exist on `PartyMember`. Sheet detail edits current/temp HP through party APIs so projection stays single-sourced.

### HP ownership rule (document + code)

1. **`PartyMember.currentHp` / `maxHp` / `tempHp`** are the campaign-persistent combat projection used by party summary, difficulty, and session draft.
2. **`SheetEngine.derive().maxHp()`** is the rules-derived maximum. `SheetService.syncToPartyMember` writes derived max HP, AC, initiative, speed, passives, classAndLevel to the party member.
3. When max HP changes, **clamp** `currentHp` to `[0, maxHp]` (do not auto-heal up to new max).
4. **Linked combatant** (party member combatant in an active encounter): mutations to combatant HP/temp/conditions that the tracker applies **also** write through to the party member when the combatant is player-owned (existing link). Mutations on the sheet/party outside combat update the party member only; on next combat add or explicit “sync to combat” they refresh combatant max/current.
5. Map tokens display party/combatant HP; they never own a third HP store.

### Attack / feature records (CharacterSheet JSON CLOBs)

```json
// attacksJson
[
  {
    "key": "longsword",
    "name": "Longsword",
    "attackBonus": 5,
    "damageExpression": "1d8+3",
    "damageType": "slashing",
    "range": "5 ft",
    "properties": "Versatile (1d10)",
    "ammunition": null,
    "notes": ""
  }
]

// featuresJson
[
  {
    "key": "second-wind",
    "name": "Second Wind",
    "actionType": "BONUS_ACTION",
    "source": "Fighter 1",
    "body": "Regain 1d10 + fighter level HP…",
    "resourceName": "Second Wind"
  }
]
```

`actionType`: `ACTION` | `BONUS_ACTION` | `REACTION` | `FREE` | `PASSIVE` | `OTHER`.

### Overrides with reason

```json
// overrides — extend existing map; known keys remain numeric overrides
{
  "armorClass": 18,
  "maxHp": 48,
  "_meta": {
    "armorClass": { "reason": "Mage Armor", "source": "spell" },
    "maxHp": { "reason": "Aid", "source": "spell" }
  }
}
```

Engine continues to read numeric override keys; UI shows reason from `_meta`.

### Rest preview

```java
public record RestPreviewDto(
        UUID sheetId,
        String restType, // SHORT | LONG
        int hitDiceAvailable,
        int hitDiceToSpend,
        int estimatedHpRecovered, // from hit dice if short; full to max if long (current→max)
        List<String> resourcesToReset,
        Map<String, Integer> spellSlotsToRecover, // level → count recovered
        boolean clearExhaustionOneLevel, // long rest only per rules approximation labeled if incomplete
        List<String> notes
) {}
```

### Inventory presentation state (ItemAssignment)

Add `inventoryState` enum: `EQUIPPED`, `CARRIED`, `STASHED`, `CONSUMED`, `LOST` (default `CARRIED`). Party stash remains `partyMember == null` with state `STASHED`. Do not invent shops in this plan.

### Package schema additions (`campaign-format-v2.schema.json`)

- `partyMember`: `tempHp`, `inspiration`, `exhaustion`, `deathSaveSuccesses`, `deathSaveFailures`, `concentratingOn`, `conditionsJson` (or structured `conditions` array).
- `sheet`: `attacks`, `features`, optional `subclassRef` on `classLevel`, richer `overrides` (still object).
- `classLevel`: add optional `subclassRef` ContentReference.
- Assignment DTO (treasury section): `inventoryState`.

Existing fixtures without new fields remain valid via defaults on import.

---

## File map

| Area | Create | Modify |
|---|---|---|
| Migration | `src/main/resources/db/migration/V8__character_sheet_completion.sql` | Flyway upgrade tests |
| Sheet model | Typed codecs under `sheet/data/` or `sheet/codec/` for attacks/features/conditions if preferred over ad-hoc maps | `CharacterSheet`, `SheetResource` only if needed |
| Party model | — | `PartyMember` live-state columns; `ItemAssignment.inventoryState` |
| Engine/service | `sheet/service/RestPreviewService.java` (or methods on SheetService); `sheet/service/SheetClassLevelCodec.java` | `SheetEngine`, `SheetService`, `PartyMemberService` |
| Ownership sync | — | `EncounterService` combatant HP/condition write-through; document in service Javadoc |
| Package | — | `CampaignManifestV2` DTOs, schema, `PartySectionAdapter`, `TreasurySectionAdapter`, semantic snapshot/comparator, fixtures |
| Web | Sheet API endpoints for attacks, features, live state, rest preview, prepare/slots | `SheetController`, `SheetApiController`, `PartyController` |
| UI | `_attacks.html`, `_features.html`, `_live-state.html`, `_rest-preview.html`, `_inventory.html`, multi-step create | `sheet/create.html`, `detail.html`, `overview.html`, `party/*`, CSS |
| Docs | — | `docs/campaign-format-v2.md`, `docs/campaign-capabilities.md`, master spec §22 row 8 after release |
| Tests | New sheet/party/package tests listed per task | Round-trip, player safety, session draft if attendance/loot text changes |

---

### Task 1: Canonicalize class-level storage (`classSourceKey`)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetClassLevelCodec.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetEngine.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/party/packagev2/PartySectionAdapter.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/service/SheetClassLevelCodecTest.java`
- Test: extend `SheetEngineTest`, `PartySectionAdapterTest`

**Interfaces:**
- Produces: `SheetClassLevelCodec.read(String json) → List<ClassLevelEntry>`; `write(List<ClassLevelEntry>) → String`; `readClassSourceKey(Map) → String` accepts both `classSourceKey` and legacy `classRef` string.
- ClassLevelEntry gains optional `subclassSourceKey` (null allowed).

- [ ] **Step 1: Write failing codec tests**

```java
@Test
void readsCanonicalClassSourceKey() {
    String json = """
        [{"classSourceKey":"srd-2024_fighter","level":3,"hitDieRolls":[8,6]}]
        """;
    var levels = SheetClassLevelCodec.read(json);
    assertThat(levels).hasSize(1);
    assertThat(levels.get(0).classSourceKey()).isEqualTo("srd-2024_fighter");
    assertThat(levels.get(0).level()).isEqualTo(3);
}

@Test
void readsLegacyInternalClassRefString() {
    String json = """
        [{"classRef":"srd-2024_rogue","level":5,"hitDieRolls":[8,5,6,7,4]}]
        """;
    var levels = SheetClassLevelCodec.read(json);
    assertThat(levels.get(0).classSourceKey()).isEqualTo("srd-2024_rogue");
}

@Test
void writeUsesOnlyClassSourceKey() {
    String out = SheetClassLevelCodec.write(List.of(
            new SheetService.ClassLevelEntry("srd-2024_wizard", 2, List.of(4), null)));
    assertThat(out).contains("classSourceKey");
    assertThat(out).doesNotContain("\"classRef\"");
}
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-sheet \
  -Dtest=SheetClassLevelCodecTest test
```

- [ ] **Step 3: Implement codec + wire engine/service/adapter**

```java
public final class SheetClassLevelCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SheetClassLevelCodec() {}

    public static String classSourceKeyOf(Map<String, Object> entry) {
        Object v = entry.get("classSourceKey");
        if (v == null) v = entry.get("classRef");
        return v == null ? null : v.toString();
    }

    public static List<SheetService.ClassLevelEntry> read(String json) { /* parse list; map keys */ }

    public static String write(List<SheetService.ClassLevelEntry> levels) { /* emit classSourceKey + optional subclassSourceKey */ }
}
```

- Replace every `entry.get("classSourceKey")` in `SheetEngine` with `SheetClassLevelCodec.classSourceKeyOf(entry)`.
- `PartySectionAdapter.exportClassLevels`: read via codec; emit package `classRef` / optional `subclassRef`.
- `importClassLevels`: write **canonical** `classSourceKey` (not `classRef`) into the CLOB.

- [ ] **Step 4: Run sheet + party adapter tests**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-sheet \
  -Dtest=SheetClassLevelCodecTest,SheetEngineTest,SheetServiceTest,PartySectionAdapterTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/sheet \
  src/main/java/dev/hendrikhoemberg/dmhelper/party/packagev2/PartySectionAdapter.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/sheet \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/PartySectionAdapterTest.java
git commit -m "fix(sheet): canonicalize class level JSON to classSourceKey"
```

---

### Task 2: Live combat state on PartyMember + package fields

**Files:**
- Create: `src/main/resources/db/migration/V8__character_sheet_completion.sql` (party live state columns first; inventory enum may land in same file in Task 5—prefer one V8 with all columns)
- Modify: `PartyMember.java`, `PartyMemberService`, party forms/cards
- Modify: `CampaignManifestV2.PartyMemberDto`, schema `partyMember`, `PartySectionAdapter`
- Modify: semantic snapshot/comparator paths that hash party members
- Test: `PartyMemberLiveStatePersistenceTest`, extend `PartySectionAdapterTest`, fixture defaults

**V8 SQL (party portion):**

```sql
ALTER TABLE party_member ADD COLUMN temp_hp INT NOT NULL DEFAULT 0;
ALTER TABLE party_member ADD COLUMN inspiration BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE party_member ADD COLUMN exhaustion INT NOT NULL DEFAULT 0;
ALTER TABLE party_member ADD COLUMN death_save_successes INT NOT NULL DEFAULT 0;
ALTER TABLE party_member ADD COLUMN death_save_failures INT NOT NULL DEFAULT 0;
ALTER TABLE party_member ADD COLUMN concentrating_on VARCHAR(255);
ALTER TABLE party_member ADD COLUMN conditions_json CLOB;
```

- [ ] **Step 1: Failing persistence + export tests** for temp HP, death saves, inspiration, exhaustion, concentration, conditions array round-trip.

- [ ] **Step 2: Implement migration + entity + adapter mapping** with defaults on null import.

- [ ] **Step 3: API**

```java
// SheetApiController or PartyApi
@PutMapping("/party/{memberId}/live-state")
public PartyLiveStateDto updateLiveState(@PathVariable UUID memberId, @RequestBody PartyLiveStateDto body);
```

Clamp exhaustion 0–6, death saves 0–3, tempHp ≥ 0, currentHp ≥ 0.

- [ ] **Step 4: UI fragment `sheet/_live-state.html`** on detail page + compact badges on `party/_card.html`.

- [ ] **Step 5: Tests + commit**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-sheet \
  -Dtest=PartyMemberLiveStatePersistenceTest,PartySectionAdapterTest,FlywayMigrationTest test
```

```bash
git commit -m "feat(party): persist temp HP, death saves, conditions, inspiration"
```

---

### Task 3: HP ownership sync with combatants

**Files:**
- Modify: `SheetService.syncToPartyMember`
- Modify: `EncounterService` setHp / conditions methods
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/party/service/PartyCombatProjectionService.java` (optional façade)
- Test: `PartyCombatProjectionServiceTest` or `EncounterPartyHpSyncTest`

**Rule implementation checklist:**

1. `syncToPartyMember`: set maxHp from derived; `currentHp = min(currentHp, maxHp)` (if current was 0 and never set, leave 0; if new sheet, set current = max).
2. When creating a combatant from a party member, copy current/max/temp/conditions.
3. When tracker updates a combatant that has `partyMemberId` (use existing link field—inspect `Combatant` for party member association; if only name-matched, add optional `partyMember` FK in V8 only if missing).

Inspect first:

```bash
rg -n "partyMember|party_member" src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java
```

If no FK exists, add nullable `party_member_id` on combatant in V8 and wire “Add party to encounter” to set it. Do not break unnamed NPC combatants.

- [ ] **Step 1: Failing test** — change sheet max HP → party max updates, current clamped; set combatant HP → party current matches when linked.

- [ ] **Step 2: Implement write-through** with no silent failure (use existing action-failure paths for API).

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(party): sync sheet-derived max HP and linked combatant HP"
```

---

### Task 4: Attacks and features on the sheet

**Files:**
- Modify: `CharacterSheet` — `attacksJson`, `featuresJson` CLOBs (V8)
- Modify: `SheetService` CRUD helpers; `SheetDto` fields
- Modify: `PartySectionAdapter` export/import; schema `sheet.attacks`, `sheet.features`
- Create templates: `sheet/_attacks.html`, `sheet/_features.html`
- Test: `SheetAttacksFeaturesTest`, engine-independent service tests

- [ ] **Step 1: Failing tests** for add/update/delete attack; export includes attacks; import restores damage expression.

- [ ] **Step 2: Service API**

```java
public record AttackDto(
        String key, String name, Integer attackBonus, String damageExpression,
        String damageType, String range, String properties, Integer ammunition, String notes) {}

public record FeatureDto(
        String key, String name, String actionType, String source, String body, String resourceName) {}
```

Generate `key` with existing `PackageKeyGenerator` slug rules when empty (`^[a-z0-9][a-z0-9._-]{0,99}$`).

- [ ] **Step 3: UI** — list + add forms on detail; roll buttons use `data-roll` like saves (`d20+{attackBonus}`, damage expression). Digital rolling remains optional; values remain readable.

- [ ] **Step 4: Optional helper** — “Add weapon attack from equipment” copies name from assigned equipment if present (no mandatory magic).

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(sheet): attacks and feature actions on character sheet"
```

---

### Task 5: Inventory states + sheet inventory panel

**Files:**
- V8: `item_assignment.inventory_state VARCHAR(16) NOT NULL DEFAULT 'CARRIED'`
- Modify: `ItemAssignment`, `TreasuryService`, `TreasurySectionAdapter`, schema assignment DTO
- Create: `sheet/_inventory.html` replacing pure HTMX loading stub with structured states
- Test: `InventoryStateRoundTripTest`, treasury service tests

- [ ] **Step 1: Failing tests** for equip/attune constraints (attuned implies equipped or carried—document: attunement requires `EQUIPPED` or `CARRIED`, not `STASHED`/`LOST`).

- [ ] **Step 2: Service methods**

```java
void setInventoryState(UUID assignmentId, InventoryState state);
void adjustQuantity(UUID assignmentId, int delta); // floor at 0; 0 may mark CONSUMED for stackables
```

- [ ] **Step 3: Weight** — if `EquipmentItem`/`MagicItem` has weight field in data, sum CARRIED+EQUIPPED; show total; do not invent encumbrance thresholds without SRD table parse—label “Weight (total lb)” only.

- [ ] **Step 4: Wire sheet detail section** to show member assignments with state controls (HTMX or Alpine + `dm-request.js`).

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(treasury): inventory states and sheet inventory panel"
```

---

### Task 6: Spell UX — prepare, slots, search, full text

**Files:**
- Modify: `SheetApiController` (toggle prepared already on service—expose HTTP), slot spend endpoint
- Modify: `sheet/_spell-list.html`, `SheetController` model attributes (spell full text via spell entity)
- Modify: `SheetService` for `spendSpellSlot(sheetId, level)` / `recover` via rest
- Test: `SheetSpellSlotServiceTest`

`spellSlotsUsed` map shape (lock in tests):

```json
{ "1": 2, "2": 1, "pact": 1 }
```

- [ ] **Step 1: Failing tests** for prepare toggle, spend slot capped by derived slots, long rest clears used.

- [ ] **Step 2: UI** — search input filters client-side; expandable description; prepared checkbox; slot trackers per level with −/+ .

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(sheet): spell prepare toggles and slot tracking UI"
```

---

### Task 7: Rest preview and confirmed apply

**Files:**
- Modify: `SheetService` — `previewRest`, keep `shortRest`/`longRest` as apply
- Modify: `SheetApiController`, `SheetController`
- Create: `sheet/_rest-preview.html` dialog
- Test: `RestPreviewServiceTest`

- [ ] **Step 1: Failing tests**

```java
@Test
void shortRestPreviewListsShortResetResourcesAndHitDice() {
    // create sheet with SHORT_REST resource; preview SHORT with hitDiceToSpend=1
    // assert resourcesToReset contains resource name; estimatedHpRecovered uses avg or formula note
}

@Test
void longRestPreviewRecoversSlotsAndHalfHitDice() {
    // assert spellSlotsToRecover non-empty when slots used; hit dice recovery note
}
```

- [ ] **Step 2: Implement preview pure function** from current sheet + derived values (no mutation).

- [ ] **Step 3: UI flow** — Short/Long Rest opens dialog with preview; Confirm posts apply; Cancel leaves state.

- [ ] **Step 4: Label exhaustion recovery honestly** if rules data is incomplete (“Long rest: reduce exhaustion by 1 (SRD rest rule)”).

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(sheet): rest preview before applying recovery"
```

---

### Task 8: Creation and advancement choices

**Files:**
- Modify: `sheet/create.html` → multi-step wizard (single page sections OK)
- Modify: `SheetController` create POST to accept skills, tools, languages, subclass, hit die choice
- Modify: level-up dialog for subclass choice, ASI/feat choice records
- Modify: `SheetService.createSheet` / `levelUp` to apply proficiency choices into proficiencies JSON
- Test: `CharacterCreationServiceTest`, template contract test optional

**Proficiencies JSON shape (canonical):**

```json
{
  "skills": ["athletics", "perception"],
  "expertise": ["perception"],
  "tools": ["thieves_tools"],
  "languages": ["common", "elvish"],
  "armor": ["light", "medium", "shields"],
  "weapons": ["simple", "martial"],
  "saving_throws": ["str", "con"]
}
```

- [ ] **Step 1: Failing test** — create fighter with two skill choices → skill bonuses include proficiency.

- [ ] **Step 2: Load class skill choice lists** from existing `SheetEngine` skill choice parsing (`skillChoices` on DerivedValues already exists—expose on create form via service method `creationOptions(classSourceKey)`).

```java
public record CreationOptionsDto(
        String classSourceKey,
        String hitDie,
        List<String> savingThrows,
        int skillChoiceCount,
        List<String> skillOptions,
        List<SubclassOption> subclasses,
        List<String> armorProficiencies,
        List<String> weaponProficiencies
) {}
```

Parse from class features/proficiencies JSON already seeded; if unparsable, allow free-text multi-select of all skills with a warning banner “Class skill list incomplete—manual selection.”

- [ ] **Step 3: Subclass** — dropdown of classes where `subclassOf == baseSourceKey`; store `subclassSourceKey` on the class level entry.

- [ ] **Step 4: Level-up** — when adding a level, if subclass empty and level ≥ 3 (configurable constant `SUBCLASS_LEVEL = 3` matching SRD common pattern), require or prompt subclass; for ASI levels, if class features text contains ASI/feat at that level, prompt ASI (+2 or two +1) or feat ref—store ASI in ability scores and feat in featRefs; if feature data missing, show manual “Record ASI/feat” checkbox.

- [ ] **Step 5: Multiclass** — level-up select can add a **new** classSourceKey entry (already partially supported); UI lists all base classes; minimum ability prerequisites are **not** enforced unless present in rules data—show advisory note.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(sheet): creation choices, subclass, and level-up ASI/feat prompts"
```

---

### Task 9: Overrides with reason + proficiency editor UX

**Files:**
- Modify: overrides form on detail; proficiencies form (replace comma-text with chip multi-select where practical)
- Modify: `SheetService.updateSheet` to accept `_meta` reasons
- Test: `SheetOverridesTest`

- [ ] **Step 1: Failing test** — set AC override 18 with reason “Shield of Faith”; derive uses 18; DTO includes reason.

- [ ] **Step 2: UI** — override key, value, reason, source; list current overrides with remove.

- [ ] **Step 3: Proficiency editor** — skills multi-select from `SKILL_ABILITY_MAP` keys; expertise subset of skills; tools/languages free tags; armor/weapons checkboxes for common categories.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(sheet): overrides with reasons and structured proficiency editor"
```

---

### Task 10: Party multi-member operations UI

**Files:**
- Modify: `sheet/overview.html`, `party/list.html` — batch action bar
- Modify: reuse `SheetApiController` batch rest/XP; add batch live-state condition apply
- Test: `PartyBatchOperationsTest` (MockMvc)

- [ ] **Step 1: Failing MockMvc tests** for POST batch rest with multiple member IDs; batch XP EQUAL split; batch add condition.

- [ ] **Step 2: UI** — checkboxes on overview/party cards; actions: Short Rest, Long Rest (with shared preview summary), Award XP, Set Milestone Level, Apply Condition, Clear Death Saves.

- [ ] **Step 3: Session attendance remains session-scoped** (already on `CampaignSession`); party `active` remains character-level. Batch ops default to **active** members; optional “include inactive.”

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(party): multi-member rest, XP, and condition operations"
```

---

### Task 11: Package fixtures, semantic compare, capability docs

**Files:**
- Modify: `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json` (and published-adventure if it has sheets)
- Modify: `CampaignSemanticSnapshotService` / comparator for new fields
- Modify: `docs/campaign-format-v2.md`, `docs/campaign-capabilities.md`
- Modify: master design §22 row 8 → `IMPLEMENTED` only after tests pass
- Test: `CampaignCompleteRoundTripTest`, `CustomCompendiumPackageRoundTripTest` still green

- [ ] **Step 1: Extend feature-complete fixture** with attacks, features, live state, inventoryState, subclassRef, spellSlotsUsed, override meta.

- [ ] **Step 2: Run full package + sheet suite**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-sheet \
  -Dtest=SheetEngineTest,SheetServiceTest,SheetClassLevelCodecTest,PartySectionAdapterTest,CampaignCompleteRoundTripTest,CustomCompendiumPackageRoundTripTest,RestPreviewServiceTest,PartyCombatProjectionServiceTest,InventoryStateRoundTripTest,PartyBatchOperationsTest,FlywayMigrationTest,FlywayLegacyUpgradeTest test
```

Expected: PASS.

- [ ] **Step 3: Update capability matrix rows**, for example:

| Capability | Status |
|---|---|
| Character creation choices (class/subclass/skills) | `SUPPORTED` |
| At-table attacks/features | `SUPPORTED` |
| Live HP/temp/death saves/conditions on party | `SUPPORTED` |
| Sheet inventory states | `SUPPORTED` |
| Rest preview | `SUPPORTED` |
| Full automated ASI/subclass feature schedule from all class JSON | `PARTIAL` if any class still needs manual choice |

- [ ] **Step 4: Commit**

```bash
git commit -m "test(docs): character sheet completion round-trip and capabilities"
```

---

### Task 12: Browser smoke + player-safety regression

**Files:**
- Modify: `CoreSessionLoopSmokeTest` or add `CharacterSheetSmokeTest` if Playwright harness exists
- Modify: `PlayerSafeProjectionTest` — ensure conditions/inspiration rules: player view may show public combat conditions on tokens but not sheet override reasons or private notes

- [ ] **Step 1: Add smoke steps** — open sheet, edit temp HP, preview rest, toggle prepared spell (if browser suite allows form posts).

- [ ] **Step 2: Assert no browser console errors** (existing collector).

- [ ] **Step 3: Full test suite**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-sheet test
```

- [ ] **Step 4: Final commit** if smoke needed fixes

```bash
git commit -m "test(sheet): smoke and player-safety coverage for sheet completion"
```

---

## Self-review (plan vs spec)

| Spec §11 requirement | Task(s) |
|---|---|
| Class/subclass/multiclass, species, background, feats, choices | 1, 8 |
| Ability scores and improvements | 8, 9 |
| Saves/skills/tools/armor/weapons/languages; expertise | 8, 9 |
| HP and hit-die history | 1, 7, 8 (existing rolls + UI) |
| Features and limited-use resources | 4, 6, existing resources |
| Spell lists, prepared, slots | 6 |
| Overrides with reason/source | 9 |
| XP and milestone | existing + 10 |
| Attacks with expression/damage/range/ammo | 4 |
| Actions/BA/reactions/feature text | 4 |
| Saves/skills/initiative/movement/senses/passives | existing engine + live state |
| HP/temp/death saves/exhaustion/conditions/concentration/inspiration | 2, 3 |
| Inventory equip/carry/consume/weight | 5 |
| Spells searchable full text + controls | 6 |
| Rest preview | 7 |
| Attendance session-specific + character active | existing session + 10 |
| Multi-member rest/XP/loot/conditions | 10 |
| Encounter/map sync ownership rule | 3 |
| Party import/export current state | 2, 4, 5, 11 |

**Placeholder scan:** no TBD steps; code samples are concrete; commands listed.

**Type consistency:** `ClassLevelEntry` gains `subclassSourceKey`; package uses `subclassRef`; live state on `PartyMember`; attacks/features on sheet JSON; inventory on `ItemAssignment`.

**Out of scope (do not pull in):**
- Full 2014/2024 automation of every subclass feature grant table without data
- Shop systems, travel encumbrance rules beyond total weight
- Player-editable character sheets from player view
- Encounter waves/rewards (item 9)
- Fog of war / audio assets

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-17-p1-character-sheet-completion.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks, fast iteration (`superpowers:subagent-driven-development`)
2. **Inline Execution** — execute tasks in this session with checkpoints (`superpowers:executing-plans`)

**Which approach?**
