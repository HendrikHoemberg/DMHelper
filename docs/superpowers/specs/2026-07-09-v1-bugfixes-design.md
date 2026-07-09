# DMHelper v1 Bug Fixes — Implementation Plan

**Date:** 2026-07-09
**Source:** Audit of SPEC.md implementation
**Status:** Design approved

## Overview

Fix 39 bugs found during comprehensive spec audit, organized into 6 independent modules, each a self-contained PR with tests.

## Module 1: Security Fixes (3 bugs)

### 1.1 — /files/** served without auth (HIGH)

**Problem:** `FileServeController.serveFile()` serves any handout by UUID with zero authorization. The path is excluded from PIN interceptor.

**Fix:**
- Remove `/files/**` from `WebMvcConfig` interceptor exclusions
- Add unauthenticated endpoint `GET /player/files/{id}` that checks `handout.isPresented()` before serving
- Original `GET /files/{id}` stays PIN-gated via interceptor

**Files:** `WebMvcConfig.java`, `FileServeController.java`

**Tests:** `HandoutControllerTest` — test 404 for unpublished, 200 for presented; verify PIN required for /files/

### 1.2 — PIN logged in plaintext (MEDIUM)

**Problem:** `PinManager:37` logs the full PIN at INFO level.

**Fix:** Change to `log.info("DM PIN generated ({} chars)", pin.length())`. Keep `System.out.println` for terminal display.

**Files:** `PinManager.java`

**Tests:** Verify no PIN value in log output.

### 1.3 — No rate limiting on PIN attempts (MEDIUM)

**Problem:** PIN brute-force possible with no delay penalties.

**Fix:** Add `ConcurrentHashMap<String, RateLimitEntry>` in `PinInterceptor`. 5 failures → 2s delay; 10 failures → 429 for 30s.

**Files:** `PinInterceptor.java`

**Tests:** `PinInterceptorTest` — verify rate limiting thresholds and expiry.

---

## Module 2: Player-Safe Projection Fixes (3 bugs)

### 2.1 — Stale document in broadcastCurrentState (HIGH)

**Problem:** `TablePresentationService.broadcastCurrentState()` reuses `currentState.map().document()` verbatim — never re-projects. Players see stale layer data until DM re-presents the map.

**Fix:** Replace `currentState.map().document()` with `projectionService.projectMapDocument(gameMap)` at line 130.

**Files:** `TablePresentationService.java`

**Tests:** `PlayerSafeProjectionServiceTest` — verify re-projection after document changes.

### 2.2 — Invisible layer content leaked (MODERATE)

**Problem:** `projectMapDocument()` keeps `cells`, `shapes`, and `image` data for hidden layers, just setting `visible=false`. Player can extract via devtools.

**Fix:** For layers where `visible != true`, set `cells = null`, `shapes = null`, `image = null`.

**Files:** `PlayerSafeProjectionService.java`

**Tests:** Verify hidden layer content is nulled in projection JSON.

### 2.3 — TokenSnapshot HP fields fragile (LOW)

**Problem:** `TokenSnapshot` record has `currentHp`/`maxHp` fields always passed as `null`. No compile-time safety.

**Fix:** Remove `currentHp` and `maxHp` from `TokenSnapshot`. Create separate `DmTokenSnapshot` if DM consumers need HP on snapshots.

**Files:** `LiveTableState.java`

**Tests:** Verify serialized TokenSnapshot JSON excludes HP fields.

---

## Module 3: Combat Tracker Fixes (9 bugs)

### 3.1 — Legendary resistances reset per round (HIGH)

**Problem:** `resetLegendaryActions()` resets BOTH `legendaryActionsUsed` AND `legendaryResistancesUsed` every round wrap. 5e rules: resistances are per LONG REST.

**Fix:**
- Remove resistance reset from `resetLegendaryActions()`
- Add manual reset endpoint: `POST /api/v1/encounters/{id}/combatants/{id}/reset-legendary-resistances`

**Files:** `EncounterService.java`, `Combatant.java`

**Tests:** Verify round advance resets actions but not resistances.

### 3.2 — Undo breaks concentration state (HIGH)

**Problem:** Passed concentration checks leave `concentrationCheckPending=true` permanently after undo. `replayEntry` sets flag to `true` on replay but never clears it.

**Fix:** In `replayEntry()` CONCENTRATION_CHECK handler, check payload for pass/fail — if passed, clear the flag. After all replays in undo, call `tickConditionDurations()`.

**Files:** `EncounterService.java`

**Tests:** Verify undo after passed/failed concentration check leaves correct state.

### 3.3 — Undo incorrectly marks combatants defeated (HIGH)

**Problem:** `DEFEATED` log entry replayed after `DAMAGE` replay can set `defeated=true` on combatants with positive HP. Damage replay doesn't re-check auto-defeat threshold.

**Fix:** In `replayEntry()` DEFEATED handler, verify HP <= 0 from payload before setting defeated. Replay DAMAGE entries first, then check if HP <= 0 for defeating.

**Files:** `EncounterService.java`

**Tests:** Verify undo doesn't defeat combatant with non-lethal damage.

### 3.4 — setHp changes invisible to undo (HIGH)

**Problem:** `setHp()` logs as `DAMAGE` type but payload format mismatches. Undo silently ignores HP changes.

**Fix:** Add `SET_HP` entry type to enum. Log `setHp` as `SET_HP` with `{"currentHp": X, "tempHp": Y}` payload. Add replay handler restoring HP from payload.

**Files:** `EncounterService.java`, `CombatLogEntry.java`

**Tests:** Verify undo restores HP from setHp operation.

### 3.5 — Legendary actions reset at round boundary for ALL creatures (MODERATE)

**Problem:** All creatures get legendary actions back simultaneously. 5e rules: each creature gets them at start of ITS turn.

**Fix:** Move `resetLegendaryActions()` call from round-wrap in `nextTurn()` to after advancing to the new combatant, scoped to that combatant.

**Files:** `EncounterService.java`

**Tests:** Verify per-creature reset timing.

### 3.6 — previousTurn doesn't reverse legendary/condition state (MODERATE)

**Problem:** `nextTurn()` calls `tickConditionDurations()` and `resetLegendaryActions()` on round wrap. `previousTurn()` decrements round without reversing these.

**Fix:** In `previousTurn()`, when crossing round boundary backwards: re-apply condition durations (+1 round) and restore legendary action counts from log entries. For v1 simplicity, track previous legendary action state.

**Files:** `EncounterService.java`

**Tests:** Verify previousTurn across round boundary preserves state.

### 3.7 — Sort order mismatch in undo (MODERATE)

**Problem:** `rebuildSortOrderForUndo()` comparator omits `tieBreaker`, producing different ordering than `resortCombatants()`.

**Fix:** Add `tieBreaker` to the comparator, matching `resortCombatants` sort logic exactly.

**Files:** `EncounterService.java`

**Tests:** Verify undo produces identical sort order on tied initiatives.

### 3.8 — Expired conditions reappear after undo (MODERATE)

**Problem:** `CONDITION_TICKED` entries are ignored during undo replay. Conditions that expired naturally are never removed.

**Fix:** Handle `CONDITION_TICKED` in `replayEntry()`: decrement remaining duration, remove condition if 0. Call `tickConditionDurations()` after replay loop.

**Files:** `EncounterService.java`

**Tests:** Verify expired conditions stay expired after undo.

### 3.9 — Combatant add/remove not logged (MODERATE)

**Problem:** `addCombatant()` and `removeCombatant()` never call `logEntry()`. Neither can be undone.

**Fix:** Log `COMBATANT_ADDED` with combatant data (name, HP, initiative). Log `COMBATANT_REMOVED` with combatant id. Add replay handlers: ADDED restores combatant, REMOVED marks as removed.

**Files:** `EncounterService.java`

**Tests:** Verify undo of addCombatant and removeCombatant.

---

## Module 4: Campaign Import/Export Fixes (6 bugs)

### 4.1 — Combatant token references broken on import (HIGH)

**Problem:** Export extracts token IDs as old UUIDs. `tokenIdMap` is always passed as empty map. Import tries `findById(oldUuid)` against fresh DB — always null.

**Fix:**
- Add `tokens` list to `MapExportDto`
- During import, after creating each token, populate `tokenIdMap` (oldUUID → newUUID)
- Pass populated map to `CombatantExportDto.from()` for remapping
- Build a second map for tokens embedded in MapDocument JSON and remap those IDs

**Files:** `CampaignService.java`, `CampaignExportDto.java`

**Tests:** Verify token references survive round-trip import.

### 4.2 — dmOnly note flag lost on import (HIGH)

**Problem:** `CampaignExportDto.NoteExportDto` includes `dmOnly`. `NoteService.create()` has no `dmOnly` parameter.

**Fix:** Add `boolean dmOnly` parameter to `NoteService.create()`. Pass `nDto.dmOnly()` from import.

**Files:** `NoteService.java`, `CampaignService.java`

**Tests:** Verify dmOnly flag survives round-trip.

### 4.3 — Quicknote targetId becomes orphan (MODERATE)

**Problem:** Quicknotes reference old entity UUIDs. `idMappings` always empty.

**Fix:** Export quicknotes with `targetRef` (stable key instead of UUID). Resolve to new UUID during import using mapping tables.

**Files:** `CampaignService.java`, `CampaignExportDto.java`

**Tests:** Verify quicknotes link to correct imported entities.

### 4.4 — Item lookup full table scan (LOW)

**Problem:** `findAll().stream().filter()` instead of indexed `findBySourceKey()`.

**Fix:** Add `findBySourceKey(String)` to `MagicItemRepository` and `EquipmentItemRepository`. Replace scans.

**Files:** `MagicItemRepository.java`, `EquipmentItemRepository.java`, `CampaignService.java`

**Tests:** Existing round-trip test covers.

### 4.5 — spellSlotsUsed not exported (LOW)

**Problem:** Import always hardcodes `spellSlotsUsed` to `"{}"`.

**Fix:** Add `Map<String, Object> spellSlotsUsed` to `SheetExportDto`. Export/import the value.

**Files:** `CampaignExportDto.java`, `CampaignService.java`

**Tests:** Verify spell slots survive round-trip.

### 4.6 — JSON schema incomplete (LOW)

**Problem:** `campaign-format.schema.json` missing quicknotes, assignments, ledger, timeline definitions. Combatant and sheet definitions incomplete.

**Fix:** Add all missing top-level arrays and complete entity definitions from export DTOs.

**Files:** `schemas/campaign-format.schema.json`

**Tests:** Unit test validating exported JSON against schema.

---

## Module 5: Character Sheet Engine Fixes (7 bugs)

### 5.1 — 1/3 casters treated as FULL (HIGH)

**Problem:** Subclass-based casters (EK, AT) contribute full caster levels due to `classKey.contains()` substring matching.

**Fix:** Add `"THIRD"` type to `CASTER_TYPES`. Add `"fighter"` and `"rogue"` with `"THIRD"`. Replace substring matching with explicit key lookup. Add `floor(level/3)` contribution for third-casters.

**Files:** `SheetEngine.java`

**Tests:** Verify EK5/Wizard5 = caster level 6.

### 5.2 — spellSaveDC/spellAttackBonus no override (HIGH)

**Problem:** Most common magic items (Rod of Pact Keeper, Arcane Grimoire, etc.) modify these values but no override path exists.

**Fix:** Add override lookups for `"spellSaveDc"` and `"spellAttackBonus"` in `derive()`. Add `spellSaveDc` and `spellAttackBonus` to `DerivedValues` record.

**Files:** `SheetEngine.java`

**Tests:** Verify spell DC override applies.

### 5.3 — Batch rest hardcodes hitDiceSpent=0 (HIGH)

**Problem:** `SheetApiController:75` calls `shortRest(id, 0)` — players can never spend hit dice via batch API.

**Fix:** Add hit dice spent parameter to batch rest endpoint. Default to 1 HD per member when not specified.

**Files:** `SheetApiController.java`, `SheetService.java`

**Tests:** Verify batch short rest with hit dice spent.

### 5.4 — setLevel only modifies first class (HIGH)

**Problem:** `setLevel()` takes first entry from `classLevels` list. Breaks multiclass characters.

**Fix:** Add `classSourceKey` parameter. Search `classLevels` for matching entry. Update that entry's level.

**Files:** `SheetService.java`

**Tests:** Verify setLevel on second class in multiclass sheet.

### 5.5 — Level 20 unachievable via XP (MODERATE)

**Problem:** `XP_THRESHOLDS` has length 20 but loop condition `i < thresholds.length` means index 19 is last — level 20 unreachable.

**Fix:** Extend array to length 21 (element 0 = 0, element 20 = threshold for level 20). Fix loop to `i < XP_THRESHOLDS.length`.

**Files:** `SheetService.java`

**Tests:** Verify level 19 → 20 with sufficient XP.

### 5.6 — Skill proficiencies add ALL choices (MODERATE)

**Problem:** `buildClassSkillProficiencies()` parses "choose N from: A, B, C" but adds all options instead of just N.

**Fix:** Parse "choose N from" pattern. Expose options separately from granted proficiencies. Fallback: don't add any skills if parsing fails.

**Files:** `SheetEngine.java`

**Tests:** Verify Wizard gets only 2 skills from class, not all 6.

### 5.7 — FeatRefs loaded but never used (MODERATE)

**Problem:** `featRefs` field is read into `derive()` but never contributes to any calculation.

**Fix:** Load feat entities. Parse `Feat.benefit` for ASI modifiers. Apply to ability scores. Flag non-numeric feat benefits as "manual" in derived values.

**Files:** `SheetEngine.java`, `FeatRepository.java`

**Tests:** Verify feat with ASI modifies ability scores.

---

## Module 6: Data Model Compliance Fixes (7 bugs)

### 6.1 — Token missing icon field (MODERATE)

**Fix:** Add `@Column(length = 100) private String icon;` to Token entity. ddl-auto=update handles the new column.

**Files:** `Token.java`

### 6.2 — Background missing equipment field (MODERATE)

**Fix:** Add `@Column(columnDefinition = "CLOB") private String equipment;` to Background entity.

**Files:** `Background.java`

### 6.3 — CharacterClass missing proficiencies field (MODERATE)

**Fix:** Add `@Column(columnDefinition = "CLOB") private String proficiencies;` to CharacterClass entity.

**Files:** `CharacterClass.java`

### 6.4 — RuleSection body vs description naming (LOW)

**Fix:** Rename `description` to `body`. Add `@PostConstruct` migration bean: `UPDATE rule_section SET body = description WHERE body IS NULL;`.

**Files:** `RuleSection.java`, new migration bean in `config/`

### 6.5 — CharacterSheet missing @OneToMany collections (LOW)

**Fix:** Add `@OneToMany(mappedBy = "sheet") List<SheetResource> resources` and `List<SheetSpellReference> spells` to CharacterSheet. Mark `@JsonIgnore`.

**Files:** `CharacterSheet.java`

### 6.6 — Note missing @OneToMany to NoteLink (LOW)

**Fix:** Add `@OneToMany(mappedBy = "sourceNote") List<NoteLink> links` to Note.

**Files:** `Note.java`

### 6.7 — StatBlock campaignId raw UUID (LOW)

**Fix:** Convert from `UUID campaignId` to `@ManyToOne Campaign campaign`. Requires `@PostConstruct` migration: rename column, migrate data, add FK.

**Files:** `StatBlock.java`, new migration bean in `config/`

---

## Execution Order

1. **Module 1** (Security) — zero data model changes
2. **Module 6** (Data Model) — column additions first
3. **Module 4** (Import/Export) — depends on stable data model
4. **Module 2** (Player Projection) — self-contained
5. **Module 5** (Sheet Engine) — derivation logic
6. **Module 3** (Combat Tracker) — most complex, do last

---

## Verification

After each module: run `mvn test` to verify no regressions. After all modules: run `mvn verify` for full build including Playwright E2E tests if configured.
