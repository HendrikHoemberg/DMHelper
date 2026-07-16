# P1 Complete Round-trip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a default campaign-package-v2 export a complete, lossless recovery artifact for every campaign-owned field DMHelper currently persists, while retaining explicit opt-outs only for combat logs and dice history.

**Architecture:** Replace the temporary v2-to-v1 compatibility bridge with ordered, module-owned `CampaignSectionExporter` and `CampaignSectionImporter` adapters. A small coordinator owns the transaction, staging lifecycle, export options, package-key registry, and adapter order; each domain module owns its DTO mapping, reference resolution, asset contribution, and persistence. Prove fidelity with semantic snapshots and three import → export → import fixtures rather than comparing generated database UUIDs.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA, Flyway, Jackson 3, JSON Schema draft 2020-12, H2, JUnit 5, AssertJ, Mockito, Playwright 1.54, Maven Wrapper.

## Global Constraints

- Runtime import/export must work without internet access; schemas and catalog data load only from the classpath.
- No new frontend build chain, runtime CDN, archive library, or persistence technology is introduced.
- Format version remains exactly `2`; this plan evolves the not-yet-released v2 contract instead of introducing version 3.
- Plain `.dmcampaign.json` remains valid only when `assets` is empty; an asset-bearing export is a `.dmcampaign` ZIP.
- Default export includes combat logs and dice history. Only explicit `includeCombatLog=false` and/or `includeDiceHistory=false` options may create exclusions.
- `metadata.exclusions` is empty for a default export and may contain only `COMBAT_LOG` and `DICE_HISTORY` for an opted-out export.
- Imported package keys remain immutable and unique within `(campaign, CampaignContentType)`.
- All package references use typed package/catalog references; display names and local UUIDs never become v2 references.
- Import remains additive and atomic. No campaign row, key binding, handout file, log row, or dice row survives a failed confirmation.
- Existing format-v1 schema, direct import/export routes, fixtures, and tests remain frozen and green.
- Player payloads and player routes do not change.
- Tests use an isolated home directory through `-DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip`.
- Do not start session-cockpit, structured-transition/quest, or non-statblock custom-compendium feature work in this plan.

---

## Scope Boundary and Audit Decisions

This is master-spec delivery item 4, **Complete round-trip**. The package-v2 foundation named eleven exclusions, but only eight correspond to state the current application actually persists:

| Foundation exclusion | Resolution in this plan |
|---|---|
| `CAMPAIGN_SETTINGS` | Add typed leveling/calendar settings and preserve the stored meaning. |
| `CURRENT_SCENE` | Add a typed package `SCENE` reference and restore it after scenes exist. |
| `PARTY_CURRENT_HP` | Add `currentHp` to each party member and validate `0..maxHp`. |
| `HANDOUT_PRESENTATION_STATE` | Preserve `dmOnly` and `presented`. |
| `COMBAT_LOG` | Include by default; allow an explicit export opt-out. |
| `DICE_HISTORY` | Include by default; allow an explicit export opt-out. |
| `CALENDAR_CONFIGURATION` | Add a typed calendar configuration using the runtime's zero-based month convention. |
| `CALENDAR_CURRENT_DATE` | Add a typed current date and validate it against that configuration. |
| `CUSTOM_COMPENDIUM_NON_STATBLOCK` | Remove from recovery exclusions and keep as an honest unsupported capability until delivery item 7; there is no campaign-owned row to recover today. |
| `STRUCTURED_SCENE_TRANSITIONS` | Remove from recovery exclusions and keep unsupported until delivery item 6; there is no transition entity/table today. |
| `QUESTS_AND_OBJECTIVES` | Remove from recovery exclusions and keep unsupported until delivery item 6; current `QUEST` notes already round-trip as notes. |

The last three items belong in the capability matrix, not in `metadata.exclusions`: an exclusion means “the export deliberately omitted existing campaign state,” not “the product does not implement a future subsystem.” This distinction prevents an empty default exclusion list from overstating feature breadth.

The database/schema audit also found persisted meaning not named by the foundation exclusions. This plan must preserve it:

| Owner | Additional persistent-exported fields |
|---|---|
| Campaign | `createdAt`, `milestoneLeveling` expressed as typed leveling mode |
| Custom statblock | `createdAt`; `source=CUSTOM` is implied by the section |
| Map/token | map `sortOrder`; token `icon`; token positions remain pixels |
| Encounter | `lairActionTriggered`; combat-log timestamps, sequences, and UUID-bearing payload references |
| Note | `createdAt`; resolved link targets and display text |
| Adventure | `createdAt` |

The following storage fields are intentionally transient/derived and are not compared as recovered meaning:

- database primary/foreign-key UUIDs, generated handout storage filenames, and package-key-row timestamps;
- JPA optimistic-lock `game_map.version`;
- derived note backlink collections, search indexes, controller view models, and campaign `authorLine`;
- preview IDs, staging paths, package `metadata.createdAt`, and ZIP entry metadata.

## Approaches Considered

1. **Recommended — module adapters with a small coordinator.** This follows master-spec §17.1, removes the v1-shaped bottleneck, and gives each domain an independently testable round-trip contract.
2. **Expand `CampaignExportDto` and keep `V2CompatibilityAdapter`.** This would be quicker initially, but v2 would remain constrained by mutable names and v1 omissions, and `CampaignService` would continue owning unrelated repositories.
3. **Replace the persistence model with a generic package document store.** This would make import simple but duplicate runtime state, create synchronization problems, and violate the no-rewrite/YAGNI boundary.

## Adapter Order and Ownership

| Order | Adapter | Owns |
|---:|---|---|
| 100 | `CampaignSectionAdapter` | Campaign root, typed settings, deferred current-scene reference |
| 200 | `LibrarySectionAdapter` | Campaign-scoped custom statblocks |
| 300 | `PartySectionAdapter` | Party members, sheets, resources, spell refs |
| 400 | `MapSectionAdapter` | Maps, documents, tokens, map image assets |
| 500 | `HandoutSectionAdapter` | Handout rows, presentation flags, handout assets |
| 600 | `EncounterSectionAdapter` | Encounters, combatants, combat logs |
| 700 | `TreasurySectionAdapter` | Item assignments |
| 800 | `LedgerSectionAdapter` | Ledger entries |
| 900 | `AdventureSectionAdapter` | Adventures, chapters, scenes, ordered links |
| 1000 | `NotesSectionAdapter` | Notes, explicit links, quick notes after every supported target exists |
| 1100 | `CalendarSectionAdapter` | Timeline events after note targets; campaign calendar settings stay with order 100 |
| 1200 | `DiceSectionAdapter` | Dice history and encounter references |

Every importer may register deferred reference setters. The coordinator runs all adapters in order, then executes deferred setters, flushes once, and only then schedules preview cleanup after commit.

## File Structure

### Files created

- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignExportExclusion.java` — closed opt-out enum containing only combat log and dice history.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettings.java` — typed leveling/calendar settings stored in the existing campaign CLOB.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettingsCodec.java` — strict read/write/default handling for campaign settings JSON.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionExporter.java` — module export SPI.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionImporter.java` — module import SPI.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignExportContext.java` — campaign, options, keys, references, and staged asset collection.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignImportContext.java` — imported entity registry, typed reference resolution, assets, and deferred setters.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignManifestAssembler.java` — strongly typed one-writer-per-section manifest assembly.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionRegistry.java` — deterministic adapter ordering and duplicate-order guard.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignExportOptions.java` — default-complete export flags and exclusion derivation.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/CombatLogPayloadCodec.java` — encounter-owned package-key/local-UUID rewriting for log payloads that contain combatant IDs.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignAssetCollector.java` — collision-proof descriptor/source collection shared by map and handout exporters.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshot.java` — test-facing semantic projection of all persistent-exported meaning.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotService.java` — deterministic snapshot builder used by flagship tests and future diagnostics.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java` — path-oriented snapshot mismatch reporting.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/package-info.java` — documents coordinator/adapter dependency direction.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/package-info.java` — documents SPI ownership rules.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/CampaignSectionAdapter.java` — campaign root/settings/current-scene mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/library/packagev2/LibrarySectionAdapter.java` — library-owned custom-statblock mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/party/packagev2/PartySectionAdapter.java` — party-owned member/sheet mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/packagev2/MapSectionAdapter.java` — map-owned map/token/document mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutSectionAdapter.java` — handout-owned row/asset mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterSectionAdapter.java` — encounter-owned encounter/combatant/log mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/packagev2/NotesSectionAdapter.java` — notes-owned note/link/quick-note mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/treasury/packagev2/TreasurySectionAdapter.java` — treasury-owned assignment mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/packagev2/LedgerSectionAdapter.java` — ledger-owned entry mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/packagev2/CalendarSectionAdapter.java` — calendar-owned timeline mapping and date validation support.
- `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/AdventureSectionAdapter.java` — adventure-owned adventure/chapter/scene mapping.
- `src/main/java/dev/hendrikhoemberg/dmhelper/dice/packagev2/DiceSectionAdapter.java` — dice-owned history mapping.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionRegistryTest.java` — ordering and duplicate-ownership contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparatorTest.java` — stable UUID-independent comparison behavior.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java` — three flagship double-import tests plus opt-out variants.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportAtomicityTest.java` — rollback at every adapter boundary and asset cleanup.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageArchitectureTest.java` — source-level guard against restoring the v2-to-v1 dependency.
- `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/CombatLogPayloadCodecTest.java` — combatant-reference payload rewriting.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettingsCodecTest.java` — typed/default/invalid settings behavior.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/LibrarySectionAdapterTest.java` — custom-statblock adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/PartySectionAdapterTest.java` — party/sheet adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/MapSectionAdapterTest.java` — map/token/document/asset adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/HandoutSectionAdapterTest.java` — handout flags/asset/rollback contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/EncounterSectionAdapterTest.java` — encounter/combatant/log adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/DiceSectionAdapterTest.java` — dice-history adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/NotesSectionAdapterTest.java` — note/link/quick-note adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/TreasurySectionAdapterTest.java` — assignment adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/LedgerSectionAdapterTest.java` — ledger adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/CalendarSectionAdapterTest.java` — timeline/date adapter contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/AdventureSectionAdapterTest.java` — adventure/chapter/scene adapter contract.
- `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json` — every current persistent-exported field and both histories.
- `src/test/resources/campaigns/v2/feature-complete.dmcampaign/assets/handouts/players-map.png` — handout asset.
- `src/test/resources/campaigns/v2/feature-complete.dmcampaign/assets/maps/crypt-map.webp` — map-layer asset.
- `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json` — multi-chapter current-surface adventure fixture with sources, links, maps, encounters, and handouts.
- `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/assets/handouts/inscription.png` — published-shape handout.
- `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/assets/maps/ruins.webp` — published-shape map.
- `docs/campaign-capabilities.md` — machine-readable-style `SUPPORTED`/`PARTIAL`/`UNSUPPORTED` matrix separating format omissions from future product capabilities.

### Files modified

- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java` — complete current persistence DTO.
- `src/main/resources/schemas/campaign-format-v2.schema.json` — complete closed contract, runtime enums, zero-based calendar months, opt-out enum.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java` — add `COMBAT_LOG_ENTRY` and `DICE_ROLL`.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java` — new keys/references, settings/date/log validation, exclusion/data agreement.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java` — deterministic defaults and legacy kind normalization; no permanent recovery exclusions.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignEntityCounts.java` — add combat-log, dice-roll, and note-link counts.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStore.java` — compute expanded counts and string exclusions.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignExportCoordinator.java` — assemble adapters directly and accept options.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinator.java` — invoke import adapters directly in one transaction.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageController.java` — explicit history query options.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` — retain v1 only; remove v2 pointer receipts after the cutover.
- `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarService.java` — consume `CampaignSettingsCodec` instead of raw `Map<String,Object>`.
- `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetController.java` — read typed leveling mode.
- `src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRollRepository.java` — deterministic full-history ordering.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLinkRepository.java` — campaign/source ordered queries needed by export.
- `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/StatBlockRepository.java` — deterministic campaign-custom statblock query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMemberRepository.java` — deterministic party query with UUID tie breaker.
- `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/CharacterSheetRepository.java` — party-member sheet lookup.
- `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetResourceRepository.java` — deterministic sheet-resource query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetSpellReferenceRepository.java` — deterministic sheet-spell query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMapRepository.java` — sort-order/UUID campaign query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/TokenRepository.java` — deterministic map-token query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterRepository.java` — deterministic campaign-encounter query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/CombatantRepository.java` — sort-order/UUID encounter query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/CombatLogEntryRepository.java` — sequence/UUID encounter query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteRepository.java` — created-at/UUID campaign query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLinkRepository.java` — source-note ordered query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java` — created-at/UUID campaign query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/treasury/data/ItemAssignmentRepository.java` — deterministic campaign-assignment query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/data/LedgerEntryRepository.java` — timestamp/UUID campaign query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/data/TimelineEventRepository.java` — date/UUID campaign query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/AdventureRepository.java` — sort-order/UUID campaign query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/ChapterRepository.java` — sort-order/UUID adventure query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneRepository.java` — sort-order/UUID chapter query.
- `src/test/resources/campaigns/v2/minimal.dmcampaign.json` — complete required empty sections/settings and empty exclusions.
- `src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json` — migrate to the complete contract or replace references with the feature-complete fixture.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java` — DTO/schema compatibility for every new field.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2MigrationTest.java` — deterministic defaults/warnings.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipelineTest.java` — new semantic failures.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStoreTest.java` — expanded counts and exclusions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinatorTest.java` — adapter-based confirmation.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java` — default empty exclusions and semantic comparison.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageControllerTest.java` — query option contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java` — frozen v1 regression only.
- `src/test/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarServiceTest.java` — typed settings behavior.
- `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java` — export/preview/confirm browser recovery flow.
- `src/main/resources/templates/campaigns/detail.html` — export dialog checkboxes defaulting to include both histories.
- `docs/campaign-format-v2.md` — complete field, option, recovery, and capability semantics.
- `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` — mark only delivery item 4 complete.

### Files deleted after the cutover

- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/V2CompatibilityAdapter.java`
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/PreparedV1Import.java`
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPersistenceReceipt.java` — v1 `CampaignService.importValidated` returns `Campaign` directly after v2 stops requesting pointer receipts.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/HandoutImportSource.java` — v1 no longer needs the v2-only external-handout overload after `CampaignImportContext` owns staged assets.

---

### Task 1: Close the complete v2 DTO, schema, and semantic contract

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignExportExclusion.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipelineTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2MigrationTest.java`
- Modify: `src/test/resources/campaigns/v2/minimal.dmcampaign.json`
- Modify: `src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json`

**Interfaces:**
- Produces: `CampaignExportExclusion { COMBAT_LOG, DICE_HISTORY }`.
- Produces: the exact DTO signatures below; later adapters must use them without parallel DTOs.
- Consumes: existing `ContentReference`, `AssetDescriptor`, map document DTOs, and catalog reference rules.

- [ ] **Step 1: Add failing DTO/schema contract tests**

Add tests that deserialize `minimal.dmcampaign.json`, serialize it, validate it offline, and assert:

```java
assertThat(manifest.metadata().exclusions()).isEmpty();
assertThat(manifest.campaign().settings().levelingMode()).isEqualTo(LevelingMode.XP);
assertThat(manifest.diceRolls()).isEmpty();
assertThat(schema.validate(mapper.valueToTree(manifest))).isEmpty();
```

Add dynamic negative cases for unknown exclusions, `currentHp > maxHp`, non-runtime token/combatant kinds, one-based/out-of-range calendar months, current-scene type mismatch, duplicate log/dice keys, broken note-link targets, and contradictory history exclusions.

- [ ] **Step 2: Run the contract tests to verify failure**

Run:

```bash
./mvnw -Dtest=CampaignManifestV2ContractTest,CampaignPackageValidationPipelineTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

Expected: FAIL because the complete DTO fields and semantic checks do not exist.

- [ ] **Step 3: Add the closed exclusion and content-type vocabulary**

```java
public enum CampaignExportExclusion {
    COMBAT_LOG,
    DICE_HISTORY
}
```

Append `COMBAT_LOG_ENTRY` and `DICE_ROLL` to `CampaignContentType`. Update the schema `contentType` enum in the same commit.

- [ ] **Step 4: Expand `CampaignManifestV2` with exact typed fields**

Keep existing records and add/change these signatures:

```java
public record Metadata(
        String packageKey,
        Instant createdAt,
        String generator,
        String catalogVersion,
        String catalogSha256,
        List<CampaignExportExclusion> exclusions) {}

public enum LevelingMode { XP, MILESTONE }

public record CampaignDto(
        String key,
        String name,
        String description,
        Instant createdAt,
        CampaignSettingsDto settings,
        ContentReference currentSceneRef) {}

public record CampaignSettingsDto(
        LevelingMode levelingMode,
        CalendarConfigDto calendar,
        InGameDateDto currentDate) {}

public record CalendarConfigDto(
        List<Integer> monthLengths,
        List<String> monthNames,
        List<String> weekdayNames) {}

public record InGameDateDto(int year, int month, int day) {}

public record PartyMemberDto(
        String key, String characterName, String playerName, String classAndLevel,
        int ac, int maxHp, int currentHp, int initiativeBonus, int speed,
        int passivePerception, int passiveInsight, int passiveInvestigation,
        String notes, boolean active, SheetDto sheet) {}

public record HandoutDto(
        String key, String title, List<String> tags, String assetRef,
        String contentType, boolean dmOnly, boolean presented) {}

public record CombatLogEntryDto(
        String key, int round, long sequence, String type,
        ContentReference combatantRef, JsonNode payload, Instant createdAt) {}

public record DiceRollDto(
        String key, String expression, List<DiceResult.DieRoll> rolls,
        int modifier, int total, boolean advantage, boolean disadvantage,
        ContentReference encounterRef, Instant createdAt) {}

public record NoteLinkDto(
        String targetType, ContentReference targetRef,
        String displayText, boolean resolved) {}
```

Also add `createdAt` to `StatBlockDto`, `NoteDto`, and `AdventureDto`; `sortOrder` to `MapDto`; `icon` to `TokenDto`; `lairActionTriggered` and `List<CombatLogEntryDto> combatLog` to `EncounterDto`; `List<NoteLinkDto> links` to `NoteDto`; and top-level `List<DiceRollDto> diceRolls` to `CampaignManifestV2`.

- [ ] **Step 5: Make the schema match runtime units and enums**

Apply these exact contract rules:

```json
"exclusions": {
  "type": "array",
  "uniqueItems": true,
  "items": { "enum": ["COMBAT_LOG", "DICE_HISTORY"] }
},
"kind": { "enum": ["PC", "NPC", "MONSTER", "OBJECT"] },
"inGameMonth": { "type": "integer", "minimum": 0 },
"positionX": { "type": "integer", "minimum": 0, "description": "Pixels from the map's top-left origin." },
"positionY": { "type": "integer", "minimum": 0, "description": "Pixels from the map's top-left origin." }
```

Require every top-level array, including `diceRolls`. Require campaign settings, party current HP, handout flags, map sort order, encounter lair state/log array, note timestamp/link array, and adventure timestamp. Close every new object with `additionalProperties: false`.

- [ ] **Step 6: Add semantic checks that JSON Schema cannot express**

Implement and test:

- party `currentHp` is between `0` and `maxHp`;
- current scene is a package `SCENE` reference;
- calendar arrays are non-empty and equal length where applicable, every month length is positive, current/timeline/ledger dates fit the configured month;
- at most one active encounter remains allowed;
- log sequences are strictly increasing and do not exceed the encounter's `logSequence`;
- log `combatantRef`, dice `encounterRef`, and note-link targets resolve to the declared type;
- `COMBAT_LOG` exclusion requires every encounter log to be empty, while non-empty/default logs forbid that exclusion;
- `DICE_HISTORY` exclusion requires `diceRolls` to be empty, while included history forbids that exclusion;
- token and combatant kinds use the runtime vocabulary.

- [ ] **Step 7: Migrate v1 deterministically into the complete shape**

Remove `LegacyV1ToV2Migration.EXCLUSIONS`. For fields v1 never carried, use deterministic values: `Instant.EPOCH` for entity creation times, typed default settings, `currentSceneRef=null`, party `currentHp=maxHp`, handout `dmOnly=true/presented=false`, map `sortOrder` from source array position, `lairActionTriggered=false`, and empty logs/dice/links. Normalize legacy kinds as `character/player → PC`, `creature → MONSTER`, and `hazard/effect → OBJECT`. Emit `LEGACY_STATE_DEFAULTED` warnings naming each category that could not be recovered from v1; do not encode those warnings as v2 export exclusions.

- [ ] **Step 8: Update both checked-in foundation fixtures**

Give the minimal and current-surface fixtures deterministic typed settings, `createdAt`, empty histories/links, and `"exclusions": []`. Do not add unsupported quest, transition, or custom-content sections.

- [ ] **Step 9: Run the contract slice**

Run the Step 2 command again.

Expected: PASS.

- [ ] **Step 10: Commit the executable contract**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java src/main/resources/schemas/campaign-format-v2.schema.json src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignPackageValidationPipelineTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2MigrationTest.java src/test/resources/campaigns/v2/minimal.dmcampaign.json src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json
git commit -m "feat: complete campaign package v2 contract"
```

### Task 2: Introduce the ordered section SPI and typed contexts

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionExporter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionImporter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignExportContext.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignImportContext.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignManifestAssembler.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionRegistry.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/package-info.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignAssetCollector.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignExportOptions.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionRegistryTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyServiceTest.java`

**Interfaces:**
- Produces: ordered exporter/importer SPIs and contexts used by every later task.
- Consumes: `CampaignPackageKeyService`, `PendingCampaignImport`, `ContentReference`, and `CampaignManifestV2`.

- [ ] **Step 1: Write failing registry and context tests**

Cover deterministic order, duplicate order/name rejection, typed entity registration, wrong-type resolution failure, missing reference failure, imported-key binding, one-time deferred execution, asset lookup, duplicate asset-key rejection, and exact export exclusions.

- [ ] **Step 2: Run the focused tests to verify failure**

```bash
./mvnw -Dtest=CampaignSectionRegistryTest,CampaignPackageKeyServiceTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

Expected: FAIL because the SPI does not exist.

- [ ] **Step 3: Add the exact SPI**

```java
public interface CampaignSectionExporter {
    String sectionName();
    int order();
    void exportSection(CampaignExportContext context, CampaignManifestAssembler target);
}

public interface CampaignSectionImporter {
    String sectionName();
    int order();
    void importSection(CampaignManifestV2 source, CampaignImportContext context);
}

public record CampaignExportOptions(boolean includeCombatLog, boolean includeDiceHistory) {
    public static CampaignExportOptions complete() {
        return new CampaignExportOptions(true, true);
    }

    public List<CampaignExportExclusion> exclusions() {
        var values = new ArrayList<CampaignExportExclusion>();
        if (!includeCombatLog) values.add(CampaignExportExclusion.COMBAT_LOG);
        if (!includeDiceHistory) values.add(CampaignExportExclusion.DICE_HISTORY);
        return List.copyOf(values);
    }
}
```

- [ ] **Step 4: Implement typed contexts**

`CampaignExportContext` must expose:

```java
UUID campaignId();
Campaign campaign();
CampaignExportOptions options();
String key(CampaignContentType type, UUID entityId, String displayName);
ContentReference packageRef(CampaignContentType type, UUID entityId, String displayName);
ContentReference catalogRef(CampaignContentType type, String sourceKey);
CampaignAssetCollector assets();
```

`CampaignImportContext` must expose:

```java
Campaign campaign();
PendingCampaignImport pending();
void setCampaign(Campaign campaign);
void register(CampaignContentType type, String key, Object entity, UUID entityId);
<T> T require(ContentReference reference, CampaignContentType expectedType, Class<T> javaType);
Path requireAsset(String assetKey);
void defer(String description, Runnable setter);
void runDeferred();
```

`setCampaign` may be called exactly once by the order-100 campaign adapter. `campaign()` fails before that call. `register` binds the imported package key immediately inside the coordinator transaction. `require` rejects catalog references unless the calling adapter explicitly resolves catalog content through its repository.

- [ ] **Step 5: Implement the assembler and registry**

The assembler has one setter per top-level section and throws if a section is written twice or omitted at `build(metadata)`. The registry sorts by `order`, rejects duplicate order/name pairs at construction, and returns immutable lists.

- [ ] **Step 6: Run focused tests and commit**

```bash
./mvnw -Dtest=CampaignSectionRegistryTest,CampaignPackageKeyServiceTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignAssetCollector.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignExportOptions.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionRegistryTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyServiceTest.java
git commit -m "refactor: add campaign package section SPI"
```

Expected: focused tests PASS.

### Task 3: Make campaign settings, calendar state, and current scene typed

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettings.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettingsCodec.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/CampaignSectionAdapter.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettingsCodecTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinatorTest.java`

**Interfaces:**
- Produces: `CampaignSettings`, `CampaignSettingsCodec.read/write`, and adapter order 100.
- Consumes: deferred `SCENE` resolution supplied by Task 2 and scenes registered by Task 8.

- [ ] **Step 1: Write failing codec and adapter tests**

Test blank settings defaults, exact existing JSON decoding, malformed nonblank JSON failure, consistent milestone mode, arbitrary unknown setting rejection, created-at preservation, and deferred current-scene binding.

- [ ] **Step 2: Verify failure**

```bash
./mvnw -Dtest=CampaignSettingsCodecTest,CalendarServiceTest,CampaignImportCoordinatorTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

Expected: FAIL because settings are still raw maps and v2 omits current scene.

- [ ] **Step 3: Add the typed storage record and codec**

```java
public record CampaignSettings(
        CampaignManifestV2.LevelingMode levelingMode,
        CalendarService.CalendarConfig calendar,
        CalendarService.InGameDate currentDate) {
    public static CampaignSettings defaults() {
        return new CampaignSettings(CampaignManifestV2.LevelingMode.XP,
                CalendarService.DEFAULT_CALENDAR,
                new CalendarService.InGameDate(1492, 0, 1));
    }
}
```

`CampaignSettingsCodec.read(Campaign)` returns defaults only for blank settings, throws `IllegalStateException("Campaign settings are malformed")` for nonblank invalid JSON, and reconciles old `levelingMode` with `campaign.milestoneLeveling`. `write` emits only `levelingMode`, `calendarConfig`, and `currentInGameDate`, and updates `milestoneLeveling` consistently.

- [ ] **Step 4: Rewire calendar and sheet reads**

Replace raw map parsing in `CalendarService` and `SheetController` with the codec. Preserve the public `CalendarConfig` and `InGameDate` APIs so templates/controllers do not change.

- [ ] **Step 5: Implement `CampaignSectionAdapter`**

Export root fields and settings at order 100. Import/save the campaign immediately, register its key, then defer `currentSceneId` until `AdventureSectionAdapter` has registered scenes. A non-null unresolved current scene must fail confirmation and roll back.

- [ ] **Step 6: Run tests and commit**

```bash
./mvnw -Dtest=CampaignSettingsCodecTest,CalendarServiceTest,CampaignImportCoordinatorTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettings.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettingsCodec.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/CampaignSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarService.java src/main/java/dev/hendrikhoemberg/dmhelper/sheet/web/SheetController.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignSettingsCodecTest.java src/test/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarServiceTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinatorTest.java
git commit -m "feat: round-trip campaign settings and current scene"
```

### Task 4: Move custom-statblock and party/sheet mapping into module adapters

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/library/packagev2/LibrarySectionAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/party/packagev2/PartySectionAdapter.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/LibrarySectionAdapterTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/PartySectionAdapterTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/data/StatBlockRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/party/data/PartyMemberRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/CharacterSheetRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetResourceRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/sheet/data/SheetSpellReferenceRepository.java`

**Interfaces:**
- Produces: adapters at orders 200 and 300.
- Consumes: campaign registration from Task 3 and catalog resolution through existing repositories.

- [ ] **Step 1: Write failing adapter round-trip tests**

Seed two renamed custom statblocks, two party members with distinct current/max HP, a full sheet, multiclass order, resources, spell refs, and catalog refs. Assert package keys remain stable after rename and imported semantic values equal the source.

- [ ] **Step 2: Verify failure**

```bash
./mvnw -Dtest=LibrarySectionAdapterTest,PartySectionAdapterTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

Expected: FAIL because adapters do not exist and current HP/created-at are omitted.

- [ ] **Step 3: Implement library order 200**

Map every `StatBlock` field already present in `StatBlockDto`, plus `createdAt`. Import with `source=CUSTOM`, bind the campaign relation and supplied key, and resolve catalog statblocks without copying them. Preserve the deprecated shadow `campaign_id` column only through the existing entity compatibility behavior; do not expose it in v2.

- [ ] **Step 4: Implement party order 300**

Use two passes: save/register party members first, then save/register sheets and resources and resolve species/background/feat/class/spell catalog refs. Import `currentHp` exactly; never reset it to `maxHp`. Preserve list order for class levels, hit-die rolls, resources, feats, and spell refs.

- [ ] **Step 5: Run tests and commit**

```bash
./mvnw -Dtest=LibrarySectionAdapterTest,PartySectionAdapterTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add src/main/java/dev/hendrikhoemberg/dmhelper/library/packagev2/LibrarySectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/party/packagev2/PartySectionAdapter.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/LibrarySectionAdapterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/PartySectionAdapterTest.java
git commit -m "refactor: own library and party package sections"
```

### Task 5: Round-trip maps, token semantics, handout state, and assets

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/packagev2/MapSectionAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutSectionAdapter.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/MapSectionAdapterTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/HandoutSectionAdapterTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMapRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/TokenRepository.java`

**Interfaces:**
- Produces: adapters at orders 400 and 500 plus shared `CampaignAssetCollector` contributions.
- Consumes: custom-statblock and party registrations from Task 4.

- [ ] **Step 1: Write failing map/handout tests**

Cover map sort order, runtime token kinds, pixel coordinates, icon, HP/hidden/dead state, map document image asset refs, handout `dmOnly/presented`, asset digest/signature, generated storage names, and rollback cleanup.

- [ ] **Step 2: Verify failure**

```bash
./mvnw -Dtest=MapSectionAdapterTest,HandoutSectionAdapterTest,HandoutServiceTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

- [ ] **Step 3: Implement map order 400**

Export maps by `sortOrder`, then UUID as a deterministic tie breaker. Preserve the full document and tokens. Convert image data to asset descriptors/sources through `CampaignAssetCollector`; import validated asset bytes back into runtime data URLs only at confirmation. Register maps before tokens so encounter refs can resolve later.

- [ ] **Step 4: Implement handout order 500**

Use `HandoutService.createImported(UUID, String, String, String, String, InputStreamSource, long, String)` with the retained staged source. After creation, set `dmOnly` and `presented` exactly and save in the same transaction. Never derive one flag from the other during import, because recovery reproduces stored state rather than replaying UI commands.

- [ ] **Step 5: Run tests and commit**

```bash
./mvnw -Dtest=MapSectionAdapterTest,HandoutSectionAdapterTest,HandoutServiceTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/packagev2/MapSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/handout/packagev2/HandoutSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMapRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/TokenRepository.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/MapSectionAdapterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/HandoutSectionAdapterTest.java
git commit -m "feat: round-trip map and handout state"
```

### Task 6: Preserve encounters, replay-safe combat logs, dice history, and opt-outs

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterSectionAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/packagev2/DiceSectionAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/CombatLogPayloadCodec.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/CombatLogPayloadCodecTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/EncounterSectionAdapterTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/DiceSectionAdapterTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRollRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageController.java`
- Modify: `src/main/resources/templates/campaigns/detail.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageControllerTest.java`

**Interfaces:**
- Produces: adapters at orders 600 and 1200; `CampaignExportCoordinator.export(UUID, CampaignExportOptions)`.
- Consumes: map/token, party, and statblock registrations.

- [ ] **Step 1: Write failing payload, adapter, and controller tests**

Test every UUID-bearing log shape: `combatantId`, `COMBATANT_REORDERED.payload.orderedIds`, and `SORT_ORDER` object keys. Test ordinary payloads remain structurally equal. Test full dice-roll values and encounter ref. Test default export includes both histories and each false query option produces exactly one matching exclusion and an empty section.

- [ ] **Step 2: Verify failure**

```bash
./mvnw -Dtest=CombatLogPayloadCodecTest,EncounterSectionAdapterTest,DiceSectionAdapterTest,CampaignPackageControllerTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

- [ ] **Step 3: Implement replay-safe payload translation**

`CombatLogPayloadCodec.toPackage` replaces local combatant UUID strings with package keys for the three known locations. `toLocal` performs the inverse after combatants are registered. For payload CLOBs containing valid JSON, preserve the JSON value; for legacy empty/non-JSON text, use a JSON string node and restore its exact text. Unknown JSON structure for a known UUID-bearing entry type is an import/export error with the log key and sequence; it is never copied silently.

- [ ] **Step 4: Implement encounter order 600**

Save/register encounters, save/register combatants, then logs. Preserve status, round, active index, `logSequence`, lair fields including `lairActionTriggered`, every combatant field, log sequence/type/payload/time, and typed relations. When logs are excluded, emit `combatLog=[]` without changing live rows.

- [ ] **Step 5: Implement dice order 1200**

Add `findByCampaignIdOrderByCreatedAtAscIdAsc(UUID)` and preserve the full typed `DiceResult.DieRoll` list. Resolve optional encounter refs by package key. When dice history is excluded, emit `diceRolls=[]` without deleting or mutating live rows.

- [ ] **Step 6: Add explicit web options**

```java
@GetMapping("/{campaignId}/package")
public ResponseEntity<?> exportV2(
        @PathVariable UUID campaignId,
        @RequestParam(defaultValue = "true") boolean includeCombatLog,
        @RequestParam(defaultValue = "true") boolean includeDiceHistory) {
    var options = new CampaignExportOptions(includeCombatLog, includeDiceHistory);
    CampaignPackageArtifact artifact = exportCoordinator.export(campaignId, options);
    // existing streaming response path remains unchanged
}
```

The export dialog labels both checked options clearly and states that clearing one records the omission in the package.

- [ ] **Step 7: Run tests and commit**

```bash
./mvnw -Dtest=CombatLogPayloadCodecTest,EncounterSectionAdapterTest,DiceSectionAdapterTest,CampaignPackageControllerTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/CombatLogPayloadCodec.java src/main/java/dev/hendrikhoemberg/dmhelper/dice/packagev2/DiceSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/dice/data/DiceRollRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageController.java src/main/resources/templates/campaigns/detail.html src/test/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/CombatLogPayloadCodecTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/EncounterSectionAdapterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/DiceSectionAdapterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/web/CampaignPackageControllerTest.java
git commit -m "feat: preserve campaign histories with explicit opt-outs"
```

### Task 7: Move notes, quick notes, treasury, ledger, and timeline into owned adapters

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/packagev2/NotesSectionAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/treasury/packagev2/TreasurySectionAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/packagev2/LedgerSectionAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/packagev2/CalendarSectionAdapter.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/NotesSectionAdapterTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/TreasurySectionAdapterTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/LedgerSectionAdapterTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/CalendarSectionAdapterTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLinkRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/treasury/data/ItemAssignmentRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/data/LedgerEntryRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/data/TimelineEventRepository.java`

**Interfaces:**
- Produces: treasury/ledger adapters at orders 700/800 and notes/calendar adapters at orders 1000/1100.
- Consumes: all target entities registered by earlier adapters and typed calendar settings from Task 3.

- [ ] **Step 1: Write failing section tests**

Seed duplicate display names with distinct package keys, note timestamps and explicit links, all supported quick-note target types, stash/member assignments, catalog item refs, ledger assignment refs, and timeline note refs. Assert no import resolution uses names.

- [ ] **Step 2: Verify failure**

```bash
./mvnw -Dtest=NotesSectionAdapterTest,TreasurySectionAdapterTest,LedgerSectionAdapterTest,CalendarSectionAdapterTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

- [ ] **Step 3: Implement notes order 1000**

Save/register every note first; import explicit links second; import quick notes last. Preserve note/quick-note timestamps. Resolve link and quick-note targets exclusively through typed refs. Do not call `NoteService.rebuildLinks` during v2 import because that reparses mutable titles and can change the exported link set.

- [ ] **Step 4: Implement treasury, ledger, and calendar orders 700, 800, and 1100**

Resolve party/catalog refs for assignments, assignment refs for ledger rows, and note refs for timeline rows. Preserve timestamps/currency/date values. Validate timeline and ledger dates against the imported `CampaignSettings.calendar`; months are zero-based everywhere.

- [ ] **Step 5: Run tests and commit**

```bash
./mvnw -Dtest=NotesSectionAdapterTest,TreasurySectionAdapterTest,LedgerSectionAdapterTest,CalendarSectionAdapterTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add src/main/java/dev/hendrikhoemberg/dmhelper/notes/packagev2/NotesSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/treasury/packagev2/TreasurySectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/ledger/packagev2/LedgerSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/calendar/packagev2/CalendarSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/NoteLinkRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/treasury/data/ItemAssignmentRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/ledger/data/LedgerEntryRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/calendar/data/TimelineEventRepository.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/NotesSectionAdapterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/TreasurySectionAdapterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/LedgerSectionAdapterTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/CalendarSectionAdapterTest.java
git commit -m "refactor: own notes and logistics package sections"
```

### Task 8: Move adventures/scenes into an owned adapter and restore the current scene

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/AdventureSectionAdapter.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/AdventureSectionAdapterTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/AdventureRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/ChapterRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/CampaignSectionAdapter.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinatorTest.java`

**Interfaces:**
- Produces: adapter order 900 and registered `ADVENTURE`, `CHAPTER`, `SCENE` entities before notes/quick notes run.
- Consumes: map, encounter, statblock, and handout registrations; satisfies Task 3's deferred current-scene setter.

- [ ] **Step 1: Write the failing adventure/current-scene test**

Seed multiple adventures with equal sort positions, created timestamps, chapters, scenes, map pins, ordered statblock/handout links, and a current scene. Assert semantic equality and stable keys after rename.

- [ ] **Step 2: Verify failure**

```bash
./mvnw -Dtest=AdventureSectionAdapterTest,CampaignSettingsCodecTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

- [ ] **Step 3: Implement order 900**

Use nested passes that save/register adventure → chapter → scene before resolving scene relations. Preserve `createdAt`, editorial order, status, body, source attribution, pins, and ordered many-to-many links. Do not add transition or quest tables/DTOs in this task.

- [ ] **Step 4: Prove deferred current-scene restoration and commit**

```bash
./mvnw -Dtest=AdventureSectionAdapterTest,CampaignImportCoordinatorTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/AdventureSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/CampaignSectionAdapter.java src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/AdventureRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/ChapterRepository.java src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneRepository.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/AdventureSectionAdapterTest.java
git commit -m "feat: round-trip adventures and current scene"
```

### Task 9: Cut coordinators over and delete the v1 compatibility boundary

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignExportCoordinator.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinator.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinatorTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportAtomicityTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageArchitectureTest.java`
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/V2CompatibilityAdapter.java`
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/PreparedV1Import.java`
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPersistenceReceipt.java`
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/HandoutImportSource.java`

**Interfaces:**
- Consumes: complete ordered adapter registry from Tasks 2–8.
- Produces: v2 coordinators with no dependency on `CampaignExportDto` or `CampaignService.importValidated`.

- [ ] **Step 1: Add failing architecture and rollback tests**

In `CampaignPackageArchitectureTest`, walk `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2`, read every `.java` file, and assert none contains `campaign.service.CampaignExportDto`, `V2CompatibilityAdapter`, or `PreparedV1Import`. Parameterize atomicity failures after every adapter order and during deferred setters; assert zero campaign rows, key rows, final handout files, and retained partial state after rollback, while the preview remains retryable.

- [ ] **Step 2: Verify failure**

```bash
./mvnw -Dtest=CampaignPackageArchitectureTest,CampaignImportAtomicityTest,CampaignImportCoordinatorTest,CampaignPackageV2IntegrationTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

- [ ] **Step 3: Rewrite export coordination**

Load the campaign, create `CampaignExportContext`, invoke every exporter, build metadata using `options.exclusions()`, build the manifest, validate the assembled manifest before returning an artifact, and pass collected asset sources to the existing writer. A default `export(UUID)` delegates to `export(UUID, CampaignExportOptions.complete())`.

- [ ] **Step 4: Rewrite import coordination**

Retain the warning gate and asset revalidation. Create `CampaignImportContext`, invoke importers in order, run deferred setters, flush, register preview cleanup only after commit, and return `context.campaign()`. Do not catch adapter failures inside the transaction.

- [ ] **Step 5: Remove the compatibility path**

Delete all four compatibility files named above. Keep `CampaignService.exportToJson` and `importValidated(CampaignExportDto)` solely for v1 endpoints/tests, change the latter to return `Campaign` directly, remove its external-handout overload, and remove package-v2 pointer bookkeeping and constructor dependencies.

- [ ] **Step 6: Run cutover tests and commit**

```bash
./mvnw -Dtest=CampaignPackageArchitectureTest,CampaignImportAtomicityTest,CampaignImportCoordinatorTest,CampaignPackageV2IntegrationTest,CampaignImportExportRoundTripTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add -A src/main/java/dev/hendrikhoemberg/dmhelper/campaign src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageArchitectureTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportAtomicityTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportCoordinatorTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java
git commit -m "refactor: replace v2 compatibility adapter with module sections"
```

Expected: all cutover tests and frozen v1 regression PASS.

### Task 10: Prove semantic fidelity with three flagship fixtures

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshot.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparatorTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java`
- Create: `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json`
- Create: `src/test/resources/campaigns/v2/feature-complete.dmcampaign/assets/handouts/players-map.png`
- Create: `src/test/resources/campaigns/v2/feature-complete.dmcampaign/assets/maps/crypt-map.webp`
- Create: `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json`
- Create: `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/assets/handouts/inscription.png`
- Create: `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/assets/maps/ruins.webp`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignPackageV2IntegrationTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignEntityCounts.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStore.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStoreTest.java`
- Modify: `src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json`

**Interfaces:**
- Produces: `CampaignSemanticSnapshotService.snapshot(UUID)` and `CampaignSemanticComparator.assertEquivalent(expected, actual)`.
- Consumes: only public repositories/services and package keys; it must not compare local UUIDs or generated storage names.

- [ ] **Step 1: Write failing comparator tests**

Prove that different local UUIDs/storage filenames compare equal when keys/digests/meaning match, while a changed nested HP, note link, log payload, calendar day, token icon, or asset digest reports an exact semantic path.

- [ ] **Step 2: Implement canonical snapshots**

Snapshot every persistent-exported field listed in this plan, replacing local entity relations with package keys and stored assets with `(mediaType, size, sha256)`. Sort unordered database collections by package key; retain order where order is domain meaning.

- [ ] **Step 3: Build the three fixtures**

1. `minimal.dmcampaign.json`: empty, asset-free, default complete export.
2. `feature-complete.dmcampaign`: every current entity/field, asset type used by the app, link type, calendar edge, combat-log payload shape, and dice-roll shape.
3. `published-adventure-shaped.dmcampaign`: multiple chapters/scenes, source attribution, repeated catalog refs, map/pin/encounter/handout/note relationships, and enough volume to exercise ordering.

The third fixture intentionally does not invent structured transitions, quest objectives, or non-statblock campaign content. `docs/campaign-capabilities.md` records those as later unsupported capabilities, and delivery items 6/7 will expand this same fixture when their schemas exist.

- [ ] **Step 4: Add the double-import matrix**

For each fixture:

```text
schema validate → preview → confirm import A → semantic snapshot A
→ default export → assert exclusions=[] → preview → confirm import B
→ semantic snapshot B → deep semantic compare A/B
```

Also export the feature-complete fixture with each opt-out and both opt-outs. Compare all non-excluded meaning and assert only the selected histories are absent.

- [ ] **Step 5: Expand preview counts**

Include combat log entries, dice rolls, and note links. Assert preview counts match fixture contents and exclusions are displayed as strings.

- [ ] **Step 6: Run flagship tests and commit**

```bash
./mvnw -Dtest=CampaignSemanticComparatorTest,CampaignCompleteRoundTripTest,CampaignImportAtomicityTest,CampaignImportPreviewStoreTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshot.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotService.java src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparatorTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignImportAtomicityTest.java src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStoreTest.java src/test/resources/campaigns/v2
git commit -m "test: prove complete campaign package round-trip"
```

### Task 11: Verify browser recovery, publish the contract, and record the checkpoint

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Create: `docs/campaign-capabilities.md`
- Modify: `docs/campaign-format-v2.md`
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md`

**Interfaces:**
- Produces: executable documentation and the implementation-status checkpoint.
- Consumes: completed implementation and flagship fixtures from Tasks 1–10.

- [ ] **Step 1: Extend the guarded browser flow**

Export a seeded campaign with both history options checked, preview the exported package, confirm it, and open representative restored campaign/map/encounter/note/handout pages. Reuse `BrowserFailureCollector`; any console/page/request/unexpected HTTP failure fails the test.

- [ ] **Step 2: Run the browser test**

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

Expected: PASS with no browser failures.

- [ ] **Step 3: Update campaign-format documentation**

Document complete settings/date units, runtime kind enums, persistent/transient classification, module adapter flow, default-complete behavior, explicit history opt-outs, semantic comparison, and all three fixture paths. Remove the “Foundation Exclusions” block.

- [ ] **Step 4: Publish the capability distinction**

Create `docs/campaign-capabilities.md` with at least:

| Capability | Status after this plan |
|---|---|
| Current persisted campaign state recovery | `SUPPORTED` |
| Combat log/dice recovery | `SUPPORTED`, explicitly excludable |
| Structured scene transitions | `UNSUPPORTED`, delivery item 6 |
| Structured quests/objectives | `UNSUPPORTED`, delivery item 6 |
| Campaign-scoped non-statblock custom content | `UNSUPPORTED`, delivery item 7 |
| Session cockpit | `UNSUPPORTED`, delivery item 5 |

- [ ] **Step 5: Run the complete isolated suite**

```bash
./mvnw -DargLine=-Duser.home=/tmp/dmhelper-p1-roundtrip test
```

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 6: Verify staging and file cleanup**

```bash
find /tmp/dmhelper-p1-roundtrip/.dmhelper/import-staging -mindepth 1 -print
git diff --check
git status --short
```

Expected: `find` prints nothing or the directory does not exist; `git diff --check` exits 0; status lists only intended documentation/test changes since the last commit.

- [ ] **Step 7: Update only the completed master-spec status**

Record delivery item 4 as complete: module adapters, current persistent state, empty default exclusions, explicit history opt-outs, atomic import, and three semantic fixtures. Keep session cockpit, structured adventure/quests, custom compendium expansion, and remaining P0 reliability work open.

- [ ] **Step 8: Commit the verified checkpoint**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java docs/campaign-format-v2.md docs/campaign-capabilities.md docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md
git commit -m "docs: publish complete campaign round-trip contract"
```

## Completion Gate

This plan is complete only when:

- a default v2 export has `metadata.exclusions=[]`;
- the only legal non-empty exclusions are explicit combat-log/dice-history opt-outs;
- campaign settings, leveling mode, created time, calendar config/date, and current scene recover exactly;
- party current HP and all existing sheet state recover exactly;
- map order, documents, image assets, token icons/state/typed refs, and pixel coordinates recover exactly;
- handout assets, tags, content type, `dmOnly`, and `presented` recover exactly;
- encounter state includes `lairActionTriggered`, combatants, and replay-safe combat logs;
- dice history includes full roll breakdown, flags, timestamps, and encounter refs;
- note timestamps, explicit links, quick-note timestamps, and every supported target type recover by key;
- treasury, ledger, timeline, adventure/chapter/scene order and references recover by key;
- schema/runtime enum and zero-based calendar conventions agree;
- `V2CompatibilityAdapter` and v2 dependencies on `CampaignExportDto` are gone;
- each domain's v2 mapping lives behind an ordered module adapter;
- failures at every adapter/deferred/asset boundary roll back database rows, keys, and installed files;
- minimal, feature-complete, and published-adventure-shaped fixtures pass validate → import → export → import → semantic deep compare;
- opt-out fixture variants differ only in the selected history sections and declared exclusions;
- v1 schema/import/export regression tests remain green;
- browser recovery produces no console, page, malformed-request, or unexpected HTTP failures;
- documentation distinguishes complete recovery of implemented state from unsupported future capabilities;
- the complete isolated Maven suite reports zero failures and zero errors.

## Next Plan Boundary

After this gate, write the separate **Unified Session Cockpit** design/implementation plan from master-spec delivery item 5. It must coordinate the existing map, tracker, scene, handout/presentation, quick-note, dice, party, calendar, and session-plan modules without introducing a second encounter or presentation state model. Do not fold structured transitions/quests or custom-compendium expansion into the cockpit plan; those remain delivery items 6 and 7.
