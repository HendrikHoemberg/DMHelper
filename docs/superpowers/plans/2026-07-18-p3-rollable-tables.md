# P3 Rollable Tables and Integrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver roadmap work package 3: first-class DM-only rollable tables with exhaustive validation, deterministic nested resolution, grouped roll/session evidence, scene and location quick access, explicit encounter/reward drafts, and lossless campaign-package v2 round-trip.

**Architecture:** Add a focused `rollabletable` module that follows the existing custom-compendium ownership/provenance model and reuses `DiceEngine`, `CampaignPackageKeyService`, `ContentReference`, scene links, encounter creation, treasury assignments, command-palette destinations, and the package-v2 section registry. Keep table definitions persistent-exported, keep table roll/draft workflow persistent-local but intentionally package-excluded, and expose both normal dice and grouped table rolls through one read-only roll-history DTO. Consequences are derived from the persisted grouped result and require an atomic DM confirm or discard; rolling never mutates encounters, treasury, calendar, scenes, or player state.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2, Jackson 3, Thymeleaf, HTMX, Alpine.js, commonmark-java, JSON Schema draft 2020-12, Maven Wrapper, JUnit 5, AssertJ, MockMvc, and Playwright.

## Global Constraints

- The approved authority is `docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md` workstream M (§§4.1–4.5), cross-cutting §8, quality §9, verification §10, and delivery items 1–2 in §11.
- DM-only all-in-one readiness is the boundary. Do not add player accounts, player rolling, player-controlled state, or table data to player projections.
- Use Flyway migration `V13__add_rollable_tables.sql`; do not rely on Hibernate schema generation.
- Keep campaign package format version `2`. `rollableTables` is an optional root property whose DTO constructor defaults null to an empty list, so older v2 packages remain valid and import without warnings.
- Stable package keys match `^[a-z0-9][a-z0-9._-]{0,99}$`. Runtime compendium `sourceKey` and package-local `key` remain distinct, as they already do for custom library content.
- Reuse `ContentSource.SRD` for bundled read-only tables and `ContentSource.CUSTOM` for user-global/campaign tables. Do not bundle copyrighted table text; seed infrastructure and read-only behavior may ship with zero table rows.
- A table uses exactly one addressing mode: `RANGE` or `WEIGHTED`. Range entries cover the exact parsed minimum through maximum. Weighted entries use positive weights and the service maintains the canonical expression `1d<sum-of-weights>` so digital and physical rolls address identical cumulative slots.
- Nested table resolution has a hard maximum depth of `5`; direct and transitive cycles are rejected before save/import.
- `rollCount` is `1..100`. `REROLL_DUPLICATES` rejects a count greater than the number of root entries and has a defensive attempt ceiling of `1000`; it must never loop indefinitely.
- Entry result text is plain rich text, not Markdown. Only the table description is Markdown. Raw HTML in Markdown is escaped by the shared renderer before any table page uses `th:utext`.
- Table rolls and consequence-draft statuses survive restart but are intentionally not exported. Package export contains table definitions, links, provenance, and dependency closure; existing dice-history export remains unchanged.
- Consequence confirmation is explicit and atomic. A confirmed/discarded roll cannot be resolved a second time. Rich text is never interpreted as creatures, currency, items, or rules.
- Tests use `-DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables` and remain offline/deterministic.
- Execute TDD per task: failing focused test, minimal implementation, green focused test, then commit. Preserve unrelated user changes.

---

## Baseline audit and reuse map

| Requirement | Existing contract to reuse | Gap this plan closes |
|---|---|---|
| Dice expressions and manual numbers | `DiceEngine.roll(String)` already accepts dice syntax and typed integers | Parser/bounds are not reusable; no table selector or nested result |
| Unified ownership/provenance | `ContentSource`, nullable campaign ownership, `ContentProvenance`, `CustomContentSupport` | No table entity/service/editor |
| Typed references | `CampaignContentType`, `ContentReference`, `SceneLink` | No `ROLLABLE_TABLE` type and no table-entry reference model |
| Scene links | Generic `SceneLink` and package `SceneLinkDto` | No `RANDOM_ENCOUNTERS` role or cockpit resolution |
| Location relations | `WorldLocation` plus encounter/travel relations | No role-bearing location-to-table link |
| Roll history | `DiceRoll`, `DiceService`, `/api/v1/roll/history`, shared dice panel | History cannot represent one grouped table roll |
| Session evidence | `SessionDraftService` derives review text from time-bounded evidence | Table rolls are absent |
| Encounter consequence | `EncounterService.create` and `addFromLibrary` | No reviewable table-derived prepared encounter |
| Reward consequence | `TreasuryService.create` creates stashed typed/custom items | No review/confirm/discard flow sourced from table results |
| Search/routing | `CommandPaletteService`, `ContentDestinationRegistry` | No table index or destination |
| Package v2 | Section registry, assembler, schema, semantic validator, fidelity snapshots | No table DTO/schema/adapter/closure/validation |

## Acceptance contract

- [ ] A DM can list, view, create, clone, edit, tag, search, promote, and dependency-aware delete a table; SRD rows are read-only.
- [ ] Both `RANGE` and `WEIGHTED` tables validate and roll through the existing dice engine, including a manually typed root value.
- [ ] Nested rolls resolve recursively as one ordered result tree, stop at depth 5, and reject cycles before persistence/import.
- [ ] `ROLL_ONCE` and `roll N` support `ALLOW_DUPLICATES` and `REROLL_DUPLICATES` with deterministic unit tests.
- [ ] Every grouped result records table key/name, raw dice result, matched entry, quantity result, nested results, timestamp, and campaign in the shared roll-history response.
- [ ] A running session includes time-bounded table rolls in the generated review draft.
- [ ] Scene and world-location links expose one-click table rolling from the cockpit within two deliberate actions.
- [ ] Encounter and treasure rolls create reviewable drafts only; confirm applies the reviewed draft and discard applies nothing.
- [ ] Table definitions, typed refs, scene/location links, campaign tables, and referenced user-global table closure round-trip through package v2 without semantic loss.
- [ ] Dry-run returns entry-path problems for gaps, overlaps, bounds, bad weights, bad expressions, unresolved/wrong-type refs, cycles, and excessive depth.
- [ ] Feature-complete fixture contains all six categories; published-adventure fixture contains a ranged random-encounter table with nested and quantity examples.
- [ ] Hostile description input renders without executable HTML; table data is absent from all player payloads.
- [ ] A depth-5 nested roll resolves under 100 ms locally.
- [ ] Focused tests, Playwright table flow, and the complete Maven suite pass before roadmap row 3 closes.

## Locked domain and interface contracts

### Enums

```java
public enum TableAddressMode { RANGE, WEIGHTED }
public enum TableCategory { ENCOUNTER, TREASURE, WEATHER, RUMOR, EVENT, GENERIC }
public enum TableDuplicatePolicy { ALLOW_DUPLICATES, REROLL_DUPLICATES }
public enum TableReferenceScope { ENTITY, CATALOG }
public enum TableDraftType { ENCOUNTER, REWARD }
public enum TableDraftStatus { NONE, PENDING, CONFIRMED, DISCARDED }
public enum RollHistoryKind { DICE, TABLE }
public enum RollableTableLinkRole { RANDOM_ENCOUNTERS }
```

Allowed entry-reference content types are exactly:

```text
STATBLOCK, EQUIPMENT_ITEM, MAGIC_ITEM, NOTE, ROLLABLE_TABLE, ENCOUNTER, HANDOUT
```

### Public service records

```java
public record RollableTableWrite(
        String sourceKey, String name, String description, TableAddressMode addressMode,
        String rollExpression, TableCategory category, List<String> tags,
        List<RollableTableEntryWrite> entries) {}

public record RollableTableEntryWrite(
        String key, Integer rangeStart, Integer rangeEnd, Integer weight,
        String resultText, String quantityExpression,
        List<RollableTableReferenceWrite> references) {}

public record RollableTableReferenceWrite(
        TableReferenceScope scope, CampaignContentType targetType, UUID targetId,
        String catalogRuleset, String catalogSourceKey, String displayText) {}

public record ResolvedTableReference(
        TableReferenceScope scope, CampaignContentType targetType, UUID targetId,
        String catalogRuleset, String catalogSourceKey, String displayText) {}

public record TableRollRequest(
        Integer manualValue, int rollCount, TableDuplicatePolicy duplicatePolicy) {}

public record TableRollGroup(
        UUID logId, UUID campaignId, UUID tableId, String tableKey, String tableName,
        List<TableRollOutcome> outcomes, TableConsequenceDraft draft, Instant createdAt) {}

public record TableRollOutcome(
        String tableKey, String tableName, DiceResult rawRoll, String entryKey,
        String resultText, DiceResult quantityRoll, List<TableResolvedReference> references,
        List<TableRollOutcome> nestedRolls) {}

public sealed interface TableConsequenceDraft
        permits EncounterTableDraft, RewardTableDraft {}

public record EncounterTableDraft(
        String suggestedName, String sourceText,
        List<EncounterCreatureDraft> creatures) implements TableConsequenceDraft {}

public record EncounterCreatureDraft(
        UUID statBlockId, String displayName, int quantity) {}

public record RewardTableDraft(
        String sourceText,
        List<RewardItemDraft> items) implements TableConsequenceDraft {}

public record RewardItemDraft(
        CampaignContentType type, UUID targetId, String displayName, int quantity) {}

public record TableDependency(
        String kind, UUID dependentId, String label, String path) {}

public record TableDeletionImpact(List<TableDependency> dependencies) {
    public boolean hasDependents() { return !dependencies.isEmpty(); }
}
```

`TableRollGroupCodec` stores a versioned JSON envelope (`schemaVersion: 1`) in `table_roll_log.result_json`. API code decodes through the codec rather than serializing JPA entities.

### Persistent/export classification

| Data | Persistence | Package v2 |
|---|---|---|
| Table, entries, entry refs, ownership, provenance | Persistent | Exported |
| Scene/location table links | Persistent | Exported |
| Table roll grouped result | Persistent operational evidence | Intentionally excluded |
| Draft status/resolution target | Persistent operational workflow | Intentionally excluded |
| Editor state, open modal, retry counters | Transient | Excluded |

---

### Task 0: Reconfirm the execution baseline

**Files:**
- Read: `docs/superpowers/dm-only-readiness-roadmap.md`
- Read: `docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md`
- Read: `docs/architecture/modules-and-ownership.md`
- Read: `docs/architecture/testing-strategy.md`

**Interfaces:**
- Consumes: committed roadmap row 3 in `READY` state.
- Produces: a clean, green execution baseline; no source changes.

- [ ] **Step 1: Check branch and preserve unrelated work**

Run:

```bash
git status --short
git log -5 --oneline
```

Expected: understand every dirty path before editing; do not discard or overwrite user changes.

- [ ] **Step 2: Run the predecessor release gates**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=CampaignCompleteRoundTripTest,CoreSessionLoopSmokeTest test
```

Expected: PASS. If it fails before table edits, diagnose and record the pre-existing failure rather than masking it in this feature.

- [ ] **Step 3: Mark row 3 in progress**

Modify only the row-3 status and recovery note in `docs/superpowers/dm-only-readiness-roadmap.md`:

```markdown
| 3 | Rollable tables and integrations | `IN_PROGRESS` | 2 | Atmosphere delivery items 1–2 | [Implementation plan](plans/2026-07-18-p3-rollable-tables.md) | Table model/editor/validation, nested deterministic rolls, log integration, scene/location links, encounter/reward drafts, package round-trip, fixtures, and focused/full tests pass. |
```

- [ ] **Step 4: Commit the execution start**

```bash
git add docs/superpowers/dm-only-readiness-roadmap.md
git commit -m "docs(roadmap): start rollable-table work package"
```

---

### Task 1: Dice bounds, table persistence, and Flyway schema

**Files:**
- Create: `src/main/resources/db/migration/V13__add_rollable_tables.sql`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/DiceExpressionSpec.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/DiceEngine.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/TableAddressMode.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/TableCategory.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/TableReferenceScope.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/TableDraftType.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/TableDraftStatus.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTable.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTableEntry.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTableEntryReference.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/TableRollLog.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTableRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTableEntryReferenceRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/TableRollLogRepository.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/dice/DiceExpressionSpecTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTablePersistenceTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java`

**Interfaces:**
- Consumes: existing `DiceResult`, `ContentSource`, `ContentProvenance`, and `Campaign` ownership.
- Produces: `DiceExpressionSpec.parse(String)`, `min()`, `max()`; JPA aggregate `RollableTable`; persistent `TableRollLog`.

- [ ] **Step 1: Write failing dice-expression bounds tests**

```java
@ParameterizedTest
@CsvSource({"1d100,1,100", "2d6,2,12", "2d6+3,5,15", "1d20-2,-1,18", "1d20 adv,1,20", "7,7,7"})
void parsesTheSameGrammarAndExposesInclusiveBounds(String expression, int min, int max) {
    DiceExpressionSpec spec = DiceExpressionSpec.parse(expression);
    assertThat(spec.min()).isEqualTo(min);
    assertThat(spec.max()).isEqualTo(max);
}

@Test
void engineAndParserRejectTheSameInvalidExpression() {
    assertThatIllegalArgumentException().isThrownBy(() -> DiceExpressionSpec.parse("2d0"));
    assertThatIllegalArgumentException().isThrownBy(() -> new DiceEngine().roll("2d0"));
}
```

- [ ] **Step 2: Run the RED dice test**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=DiceExpressionSpecTest,DiceEngineTest test
```

Expected: FAIL because `DiceExpressionSpec` does not exist.

- [ ] **Step 3: Extract the parser without changing roll behavior**

Implement this public shape and have `DiceEngine.roll` consume it:

```java
public record DiceExpressionSpec(
        String expression, int count, int sides, int modifier,
        boolean advantage, boolean disadvantage, Integer typedValue) {
    private static final Pattern DICE = Pattern.compile(
            "^(\\d*)d(\\d+)([-+]\\d+)?(\\s+(adv|dis))?$");
    private static final Pattern TYPED = Pattern.compile("^\\d+$");

    public static DiceExpressionSpec parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression);
        }
        String value = expression.trim();
        try {
            if (TYPED.matcher(value).matches()) {
                int typed = Integer.parseInt(value);
                return new DiceExpressionSpec("typed: " + typed, 0, 0, 0,
                        false, false, typed);
            }
            Matcher matcher = DICE.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid dice expression: " + expression);
            }
            int count = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
            if (count > 1000) {
                throw new IllegalArgumentException(
                        "Dice count exceeds maximum (1000): " + expression);
            }
            int sides = Integer.parseInt(matcher.group(2));
            if (sides == 0) {
                throw new IllegalArgumentException(
                        "Invalid dice expression: sides cannot be 0 in " + expression);
            }
            int modifier = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            boolean advantage = "adv".equals(matcher.group(5));
            boolean disadvantage = "dis".equals(matcher.group(5));
            return new DiceExpressionSpec(value, count, sides, modifier,
                    advantage, disadvantage, null);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression, error);
        }
    }
    public int min() {
        if (typedValue != null) return typedValue;
        return (advantage || disadvantage ? 1 : count) + modifier;
    }
    public int max() {
        if (typedValue != null) return typedValue;
        return (advantage || disadvantage ? sides : Math.multiplyExact(count, sides)) + modifier;
    }
}
```

Move the current regex, blank/count/sides checks, and error messages into `parse`; keep the 1000-dice limit and `adv`/`dis` semantics byte-for-byte compatible.

- [ ] **Step 4: Write failing migration and persistence tests**

Assert V13 creates these tables/columns and constraints:

```text
rollable_table(id, source_key, source, campaign_id_fk, provenance columns,
               name, description, address_mode, roll_expression, category, tags, created_at)
rollable_table_entry(id, table_id, entry_key, range_start, range_end, weight,
                     result_text, quantity_expression, sort_order)
rollable_table_entry_reference(id, entry_id, target_scope, target_type, target_id,
                               catalog_ruleset, catalog_source_key, display_text, sort_order)
world_location_table_link(id, location_id, table_id, role, sort_order)
table_roll_log(id, campaign_id, table_id nullable, table_key_snapshot, table_name_snapshot,
               result_json, draft_type nullable, draft_status, resolved_target_ids,
               resolved_at, created_at)
```

The persistence test must save one campaign table with two ordered range entries, one `STATBLOCK` ref, tags, and provenance, clear the entity manager, then assert exact rehydration order and fields.

- [ ] **Step 5: Run the RED persistence gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=FlywayMigrationTest,FlywayLegacyUpgradeTest,RollableTablePersistenceTest test
```

Expected: FAIL because V13 and the entities do not exist.

- [ ] **Step 6: Implement the migration and aggregate**

Use `@OneToMany(mappedBy = "table", cascade = ALL, orphanRemoval = true)` plus `@OrderBy("sortOrder asc")` for entries. On `RollableTableEntry.references`, use `@OneToMany(mappedBy = "entry", cascade = ALL, orphanRemoval = true)` plus `@OrderBy("sortOrder asc")`. Use a nullable `@ManyToOne Campaign campaign`, embedded `ContentProvenance`, and `ContentSource`. Enforce `UNIQUE(table_id, entry_key)` in SQL. Use `ON DELETE SET NULL` from `table_roll_log.table_id` so historical evidence survives a confirmed table deletion; use restrictive table-link and nested-reference cleanup through the service, not blind database cascades.

- [ ] **Step 7: Run the GREEN domain gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=DiceExpressionSpecTest,DiceEngineTest,FlywayMigrationTest,FlywayLegacyUpgradeTest,RollableTablePersistenceTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V13__add_rollable_tables.sql \
  src/main/java/dev/hendrikhoemberg/dmhelper/dice \
  src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data \
  src/test/java/dev/hendrikhoemberg/dmhelper/dice/DiceExpressionSpecTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTablePersistenceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java
git commit -m "feat(tables): add rollable-table persistence and dice bounds"
```

---

### Task 2: Definition validation, reference resolution, CRUD, clone, and dependency-aware deletion

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableWrite.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableEntryWrite.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableReferenceWrite.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableValidationProblem.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableValidationException.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableValidator.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/ResolvedTableReference.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableReferenceResolver.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableDependency.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableDeletionImpact.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableDependencyService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/LibraryReferenceCleaner.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableValidatorTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableServiceTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignCascadeDeleteTest.java`

**Interfaces:**
- Consumes: Task 1 aggregate and `DiceExpressionSpec`.
- Produces: `validate(write, currentTableId)` returning path-aware problems; table CRUD/clone/promote/delete APIs; `TableDeletionImpact`.

- [ ] **Step 1: Write the validation matrix as failing parameterized tests**

Required assertions and paths:

```text
/rollExpression                         INVALID_TABLE_EXPRESSION
/entries/1/rangeStart                   TABLE_RANGE_GAP
/entries/1/rangeStart                   TABLE_RANGE_OVERLAP
/entries/0/rangeStart                   TABLE_RANGE_BOUNDS
/entries/0/weight                       TABLE_WEIGHT_INVALID
/entries/0/quantityExpression           INVALID_QUANTITY_EXPRESSION
/entries/0/references/0                 INVALID_TABLE_REFERENCE_TYPE
/entries/0/references/0                 UNRESOLVED_REFERENCE
/entries/0/references/0                 TABLE_REFERENCE_CYCLE
/entries/0/references/0                 TABLE_REFERENCE_DEPTH_EXCEEDED
```

Also assert a `2d6` range table covering `2..12` passes and a weighted table with weights `3,2` is normalized to `1d5`.

- [ ] **Step 2: Run the RED validator test**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=RollableTableValidatorTest test
```

Expected: FAIL because the validator and problem types do not exist.

- [ ] **Step 3: Implement the pure structural validator**

Use this result type:

```java
public record TableValidationProblem(String code, String path, String message) {}

public final class RollableTableValidationException extends IllegalArgumentException {
    private final List<TableValidationProblem> problems;
    public RollableTableValidationException(List<TableValidationProblem> problems) {
        super("Rollable table validation failed");
        this.problems = List.copyOf(problems);
    }
    public List<TableValidationProblem> problems() { return problems; }
}
```

Validation order is deterministic: table fields, entries in sort order, references in sort order, then graph cycle/depth. For range mode, sort a copy by `rangeStart` for coverage checks but report the original entry index. For weighted mode, reject range fields, require each weight `> 0`, sum with `Math.addExact`, and set the saved expression to `1d<sum>`. Require nonblank result text or at least one reference.

- [ ] **Step 4: Implement typed resolution and graph validation**

`TableReferenceResolver` must expose:

```java
public ResolvedTableReference require(UUID campaignIdOrNull, RollableTableReferenceWrite ref)
public boolean isVisibleToCampaign(UUID tableId, UUID campaignId)
```

For `ENTITY` references, load the exact repository by `CampaignContentType`, reject cross-campaign entities, and allow SRD/global content plus same-campaign content. For `CATALOG`, require `CampaignCatalogService.resolve`. Nested table traversal uses a DFS color map and reports the entry/reference path where the back edge or sixth level occurs.

- [ ] **Step 5: Write failing CRUD and deletion tests**

Cover:

```java
service.create(null, write, provenance);              // user-global custom
service.create(campaignId, write, provenance);        // campaign custom
service.cloneAsCustom(srdId, campaignId, "Clone");   // editable clone, copied entries/refs
service.promoteToGlobal(campaignTableId);              // clears campaign and package key
service.promoteToGlobal(tableWithCampaignRefsId);      // rejected because refs would cross scope
service.updateCustom(srdId, write, null);              // rejected read-only
service.deletionImpact(id);                            // nested/scene/location dependents listed
service.deleteCustom(id, false);                       // rejected when dependents exist
service.deleteCustom(id, true);                        // removes explicit links/refs, preserves log snapshot
```

- [ ] **Step 6: Implement minimal CRUD and dependency APIs**

Use these signatures:

```java
public RollableTable create(UUID campaignIdOrNull, RollableTableWrite write, ContentProvenance provenance)
public RollableTable updateCustom(UUID id, RollableTableWrite write, ContentProvenance provenance)
public RollableTable cloneAsCustom(UUID sourceId, UUID campaignIdOrNull, String newName)
public RollableTable promoteToGlobal(UUID id)
public TableDeletionImpact deletionImpact(UUID id)
public void deleteCustom(UUID id, boolean confirmed)
```

Use `CustomContentSupport.assertAvailableSourceKey` for create/clone/update collisions in SRD, user-global, and campaign scope. Promotion first validates that every ENTITY reference remains visible without a campaign; reject promotion while any campaign-owned note/encounter/handout/custom-content reference exists.

`TableDeletionImpact` contains stable display rows for referencing table entries, scenes, and world locations. A confirmed delete removes scene/location links and replaces each nested-table reference with the explicit plain-text marker `[Deleted table reference: <table name>]` on its existing entry before removing the reference, so range/weight addressing remains valid and no rules text is invented. Perform the rewrite in one transaction, delete the table package key when campaign-scoped, and leave `table_roll_log` snapshots intact.

- [ ] **Step 7: Run the GREEN service gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=RollableTableValidatorTest,RollableTableServiceTest,CampaignCascadeDeleteTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service \
  src/main/java/dev/hendrikhoemberg/dmhelper/library/service/LibraryReferenceCleaner.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/service \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignCascadeDeleteTest.java
git commit -m "feat(tables): validate and manage table definitions"
```

---

### Task 3: Nested roll resolver, grouped log, shared history, and session-draft evidence

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableDuplicatePolicy.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableRollRequest.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableResolvedReference.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableRollOutcome.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableRollGroup.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableConsequenceDraft.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/EncounterTableDraft.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/EncounterCreatureDraft.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RewardTableDraft.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RewardItemDraft.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableRollGroupCodec.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableRollService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/service/RollHistoryKind.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/service/RollHistoryItem.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/service/RollHistoryService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/dice/web/DiceApiController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableRollServiceTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableRollGroupCodecTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/dice/web/DiceApiControllerTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java`

**Interfaces:**
- Consumes: validated tables and existing `DiceEngine`.
- Produces: `RollableTableRollService.roll(UUID, UUID, TableRollRequest)`, versioned grouped JSON, merged `RollHistoryItem` list, `Table Rolls` session-draft section.

- [ ] **Step 1: Write deterministic failing resolver tests**

Mock `DiceEngine.roll` with an ordered answer queue and assert:

```java
when(dice.roll("1d2")).thenReturn(die("1d2", 2), die("1d2", 1));
when(dice.roll("2d4")).thenReturn(die("2d4", 6));

TableRollGroup group = service.roll(campaignId, parentId,
        new TableRollRequest(null, 2, TableDuplicatePolicy.REROLL_DUPLICATES));

assertThat(group.outcomes()).extracting(TableRollOutcome::entryKey)
        .containsExactly("second", "first");
assertThat(group.outcomes().getFirst().quantityRoll().total()).isEqualTo(6);
assertThat(group.outcomes().getFirst().nestedRolls()).hasSize(1);
```

Separate tests cover manual root value, allowed duplicates, impossible unique count, missing range match defense, depth 5 success/depth 6 failure, and one saved log for N outcomes.

- [ ] **Step 2: Run the RED resolver gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=RollableTableRollServiceTest,TableRollGroupCodecTest test
```

Expected: FAIL because roll result types and service do not exist.

- [ ] **Step 3: Implement resolution and duplicate policy**

`RollableTableRollService` must:

1. verify the table is visible to `campaignId`;
2. validate `rollCount` and unique feasibility before rolling;
3. call `DiceEngine.roll(Integer.toString(manualValue))` only for the root manual path;
4. match range or cumulative weighted slot;
5. evaluate the entry quantity once when present;
6. resolve nested table references depth-first in reference order;
7. build immutable outcome and consequence-draft records before persistence;
8. derive draft type from table category and typed refs without applying it;
9. save one `TableRollLog` with snapshot key/name and encoded group;
10. return the group with the generated log ID.

The duplicate identity is the root `entryKey`; nested variation does not make a duplicate unique.

- [ ] **Step 4: Add codec round-trip and corruption behavior**

Use an envelope:

```java
public record StoredTableRoll(int schemaVersion, List<TableRollOutcome> outcomes) {}
```

`encode` writes schema version 1. `decode` rejects unsupported versions and malformed JSON with `IllegalStateException("Unreadable table roll log: " + id)`; API history maps that row to a visible unavailable-history item instead of returning a 500.

- [ ] **Step 5: Write failing merged-history and session-evidence tests**

Assert `/api/v1/roll/history` returns newest-first items with:

```json
{"kind":"DICE","expression":"1d20","total":17}
{"kind":"TABLE","tableName":"Forest Encounters","outcomes":[{"entryKey":"wolves","resultText":"2 wolves"}]}
```

For a RUNNING session, create one log inside and one before the session window, end the session, and assert the generated draft contains only:

```markdown
## Table Rolls
- Forest Encounters — Wolves (2×), Old shrine
```

- [ ] **Step 6: Implement history and draft integration**

Use `RollHistoryItem` as an explicit DTO; do not return `DiceRoll` or `TableRollLog` entities. `RollHistoryService.recent(campaignId, 20)` merges both repositories, sorts by `createdAt desc` then ID, and limits after merging. Add `tableRollLines(campaignId, startedAt, endedAt)` to `SessionDraftService` and place `Table Rolls` after `Encounters` and before `Loot & Ledger Changes`.

- [ ] **Step 7: Run the GREEN roll-evidence gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=RollableTableRollServiceTest,TableRollGroupCodecTest,DiceApiControllerTest,SessionDraftServiceTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service \
  src/main/java/dev/hendrikhoemberg/dmhelper/dice/service \
  src/main/java/dev/hendrikhoemberg/dmhelper/dice/web/DiceApiController.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/service \
  src/test/java/dev/hendrikhoemberg/dmhelper/dice/web/DiceApiControllerTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java
git commit -m "feat(tables): resolve nested rolls and record grouped evidence"
```

---

### Task 4: DM table library, editor, roll panel, safe rendering, destinations, and palette search

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableController.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableApiController.java`
- Create: `src/main/resources/templates/rollable-table/list.html`
- Create: `src/main/resources/templates/rollable-table/detail.html`
- Create: `src/main/resources/templates/rollable-table/form.html`
- Create: `src/main/resources/templates/rollable-table/_roll-panel.html`
- Create: `src/main/resources/static/js/rollable-table-editor.js`
- Create: `src/main/resources/static/js/rollable-table-roll.js`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/templates/fragments/_dice-roller.html`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtil.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistry.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableControllerTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableApiControllerTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/RollableTableTemplateContractTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtilTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistryTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRouteContractTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteServiceTest.java`

**Interfaces:**
- Consumes: Tasks 2–3 service APIs.
- Produces: `/library/tables`, `/library/tables/{id}`, JSON CRUD/roll endpoints, table palette result type `rollable-table`.

- [ ] **Step 1: Write failing route, rendering, and search tests**

Assert:

```text
GET  /library/tables                         200 and filters by campaign/category/tag/text
GET  /library/tables/{id}                    200 with ordered entries and Roll button
GET  /library/tables/{id}/edit               200 for CUSTOM; 403/visible read-only for SRD
POST /api/v1/rollable-tables                 201 with Location header
PUT  /api/v1/rollable-tables/{id}            200
POST /api/v1/rollable-tables/{id}/clone      201
POST /api/v1/rollable-tables/{id}/promote    200
DELETE /api/v1/rollable-tables/{id}?confirmed=true 204
POST /api/v1/rollable-tables/{id}/roll       200 grouped result
```

The destination must be `/library/tables/{id}`. A campaign table title match ranks before a user-global/SRD title match. The HTML contract must contain add/remove/reorder entry controls, mode-specific inputs, reference picker, quantity expression, manual number, N count, and duplicate policy.

- [ ] **Step 2: Write the hostile Markdown regression first**

```java
@Test
void escapesRawHtmlInOrdinaryMarkdown() {
    String html = markdownUtil.toHtml("# Safe\n<script>alert(1)</script><img src=x onerror=alert(2)>");
    assertThat(html).doesNotContain("<script>", "onerror=");
    assertThat(html).contains("&lt;script&gt;");
}
```

- [ ] **Step 3: Run the RED web gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=RollableTableControllerTest,RollableTableApiControllerTest,RollableTableTemplateContractTest,MarkdownUtilTest,ContentDestinationRegistryTest,ContentDestinationRouteContractTest,CommandPaletteServiceTest test
```

Expected: FAIL on missing controllers/routes and ordinary raw HTML execution.

- [ ] **Step 4: Make shared Markdown rendering safe**

Change `HtmlRenderer.builder().escapeHtml(false)` to `.escapeHtml(true)`. Keep the custom `read-aloud` renderer, which already writes literal text through `HtmlWriter.text`. Run all existing Markdown/template tests immediately to expose content that incorrectly depended on raw HTML.

- [ ] **Step 5: Implement MVC pages and JSON endpoints**

The editor sends `RollableTableWrite` JSON through `window.dmRequest`; validation failures return `ProblemDetail` with a `problems` property containing `{code,path,message}`. The form starts with one entry, preserves entered JSON after a 400, and displays problems beside matching `data-path` elements. Reference options come from:

```text
GET /api/v1/rollable-tables/reference-options?campaignId={id}&type={type}&q={text}
```

Only visible, allowed targets are returned. Do not accept arbitrary cross-campaign UUIDs from the browser.

- [ ] **Step 6: Implement roll panel and grouped history rendering**

`rollable-table-roll.js` posts:

```json
{"manualValue":null,"rollCount":1,"duplicatePolicy":"ALLOW_DUPLICATES"}
```

Render result text with `x-text`, never `x-html`. Render typed refs as server-provided safe labels/URLs. Update both dice-panel script copies to branch on `item.kind`; normal dice rows retain critical highlighting and table rows show table name plus a compact outcome summary.

- [ ] **Step 7: Register routes and palette indexing**

Add `ROLLABLE_TABLE("tables")` to `ContentDestinationRegistry.LibraryType`. Add `RollableTableRepository` to `CommandPaletteService`, searching campaign-scoped plus user-global/SRD visible tables by name, description, category, and tags. Return type `rollable-table` and subtype equal to category.

- [ ] **Step 8: Run the GREEN web gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=RollableTableControllerTest,RollableTableApiControllerTest,RollableTableTemplateContractTest,MarkdownUtilTest,ContentDestinationRegistryTest,ContentDestinationRouteContractTest,CommandPaletteServiceTest,UiPolishContractTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web \
  src/main/resources/templates/rollable-table src/main/resources/static/js/rollable-table-editor.js \
  src/main/resources/static/js/rollable-table-roll.js src/main/resources/static/css/components.css \
  src/main/resources/templates/fragments src/main/resources/templates/session/cockpit.html \
  src/main/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtil.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/common/service \
  src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable \
  src/test/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtilTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/service
git commit -m "feat(tables): add DM editor, rolling UI, and search"
```

---

### Task 5: Scene/location links and cockpit quick access

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneLinkRole.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneStructuredContentService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java`
- Modify: `src/main/resources/templates/adventure/_action-rail.html`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/RollableTableLinkRole.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/WorldLocationTableLink.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/WorldLocationTableLinkRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableLinkService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/world/web/WorldController.java`
- Modify: `src/main/resources/templates/world/locations-detail.html`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java`
- Modify: `src/main/resources/templates/session/_story-rail.html`
- Create: `src/main/resources/templates/session/_linked-tables.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/RollableTableLinkServiceTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneStructuredContentServiceTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneControllerTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/world/web/WorldControllerTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceServiceTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`

**Interfaces:**
- Consumes: `ROLLABLE_TABLE` entity visibility and roll API.
- Produces: `SceneLinkRole.RANDOM_ENCOUNTERS`, role-bearing `WorldLocationTableLink`, `LinkedRollableTableView` on session workspace.

- [ ] **Step 1: Write failing link-visibility tests**

Cover a scene direct link, a scene `LOCATION` link whose world location has a table link, de-duplication when both reach the same table, SRD/user-global/campaign visibility, wrong-campaign rejection, and stable order (`scene links` before `location links`, then `sortOrder`, then name).

```java
List<LinkedRollableTableView> linked = service.forScene(campaignId, sceneId);
assertThat(linked).extracting(LinkedRollableTableView::source)
        .containsExactly("SCENE", "LOCATION");
```

- [ ] **Step 2: Run the RED link gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=RollableTableLinkServiceTest,SceneStructuredContentServiceTest,SceneControllerTest,WorldControllerTest,SessionWorkspaceServiceTest,SessionCockpitTemplateContractTest test
```

Expected: FAIL on missing role/link entity/workspace view.

- [ ] **Step 3: Implement authoring links with server validation**

Add `RANDOM_ENCOUNTERS` to `SceneLinkRole`. When target type is `ROLLABLE_TABLE`, `SceneStructuredContentService` must require `targetScope=PACKAGE`, non-null target ID, and table visibility to the scene campaign. `WorldController` endpoints are:

```text
POST   /campaigns/{campaignId}/world/locations/{locationId}/tables
DELETE /campaigns/{campaignId}/world/locations/{locationId}/tables/{linkId}
```

They accept `tableId`, `role=RANDOM_ENCOUNTERS`, and `sortOrder`; the detail page lists and removes links.

- [ ] **Step 4: Add linked tables to the workspace**

Extend `SessionWorkspace` with:

```java
List<LinkedRollableTableView> linkedTables
```

`LinkedRollableTableView` contains table ID/key/name/category, source (`SCENE` or `LOCATION`), and source label. Resolve the location through the current scene's existing `SceneLinkRole.LOCATION`/`WORLD_LOCATION` package link. No current scene means an empty list.

- [ ] **Step 5: Add one-click cockpit controls**

Render linked tables in the story rail and a compact `Roll linked table…` picker in the cockpit top bar. Selecting a table opens its roll panel; the next click rolls. A direct `Roll` button on each story-rail row rolls immediately, satisfying the one-click scene requirement. Failures use `window.reportActionFailure` with retry and never alter scene/encounter state.

- [ ] **Step 6: Run the GREEN integration gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=RollableTableLinkServiceTest,SceneStructuredContentServiceTest,SceneControllerTest,WorldControllerTest,SessionWorkspaceServiceTest,SessionCockpitTemplateContractTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure \
  src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable \
  src/main/java/dev/hendrikhoemberg/dmhelper/world/web/WorldController.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java \
  src/main/resources/templates/adventure src/main/resources/templates/world/locations-detail.html \
  src/main/resources/templates/session src/main/resources/static/js/session-cockpit.js \
  src/test/java/dev/hendrikhoemberg/dmhelper/adventure \
  src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable \
  src/test/java/dev/hendrikhoemberg/dmhelper/world/web/WorldControllerTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session
git commit -m "feat(tables): link tables to scenes, locations, and cockpit"
```

---

### Task 6: Reviewable encounter and reward drafts with atomic confirm/discard

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableConsequenceService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/data/TableRollLogRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableApiController.java`
- Create: `src/main/resources/templates/rollable-table/_encounter-draft.html`
- Create: `src/main/resources/templates/rollable-table/_reward-draft.html`
- Modify: `src/main/resources/static/js/rollable-table-roll.js`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/service/TableConsequenceServiceTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableApiControllerTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/treasury/service/TreasuryServiceTest.java`

**Interfaces:**
- Consumes: Task 3 `TableConsequenceDraft` records, persisted `TableRollGroup`, `EncounterService.create/addFromLibrary/updatePrep`, and `TreasuryService.create`.
- Produces: pending draft DTOs plus confirm/discard endpoints and idempotent status transitions.

- [ ] **Step 1: Write failing pure-draft tests**

Assert an ENCOUNTER table aggregates resolved `STATBLOCK` refs and quantity rolls into editable creature rows; a TREASURE table aggregates only `MAGIC_ITEM`/`EQUIPMENT_ITEM` refs into stashed reward rows. Prose and `NOTE`/`HANDOUT`/`ENCOUNTER` refs remain review context, never automatic mutations.

```java
EncounterTableDraft draft = (EncounterTableDraft) service.preview(logId, campaignId);
assertThat(draft.creatures()).extracting(EncounterCreatureDraft::quantity)
        .containsExactly(4);
assertThat(encounterRepository.count()).isZero();
```

- [ ] **Step 2: Write failing state-transition tests**

Cover:

```text
PENDING -> CONFIRMED creates exactly one planned encounter and its reviewed combatants
PENDING -> CONFIRMED creates reviewed stash assignments only
PENDING -> DISCARDED creates nothing
CONFIRMED/DISCARDED -> any second transition returns 409 and creates nothing
cross-campaign roll ID returns 404
edited quantity outside 1..50 returns 400
edited reference not present in the stored result returns 400
```

- [ ] **Step 3: Run the RED draft gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=TableConsequenceServiceTest,RollableTableApiControllerTest test
```

Expected: FAIL because consequence types/endpoints do not exist.

- [ ] **Step 4: Implement pessimistically locked transitions**

Add repository method:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select l from TableRollLog l where l.id = :id and l.campaign.id = :campaignId")
Optional<TableRollLog> findForResolution(UUID id, UUID campaignId);
```

`confirmEncounter` re-derives allowed statblock IDs from stored JSON, validates edits, creates a PLANNED encounter, calls `addFromLibrary` per reviewed creature group, and stores result prose/table key in `EncounterPrep.environment/sourceLocator`. `confirmReward` re-derives allowed item IDs and calls `TreasuryService.create` with `partyMemberId=null` and `InventoryState.STASHED`. Flush successful mutations before setting `CONFIRMED`; one transaction rolls all changes back on error.

- [ ] **Step 5: Add explicit endpoints and UI**

```text
GET  /api/v1/rollable-table-rolls/{rollId}/draft?campaignId={campaignId}
POST /api/v1/rollable-table-rolls/{rollId}/encounter/confirm
POST /api/v1/rollable-table-rolls/{rollId}/reward/confirm
POST /api/v1/rollable-table-rolls/{rollId}/discard
```

The encounter dialog edits name, optional map, and quantities. The reward dialog edits quantities and shows destination `Party stash`. Both show source prose/refs, `Confirm` and `Discard`, and a visible warning that no state changes until confirmation.

- [ ] **Step 6: Run the GREEN consequence gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=TableConsequenceServiceTest,RollableTableApiControllerTest,EncounterServiceTest,TreasuryServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable \
  src/main/resources/templates/rollable-table src/main/resources/static/js/rollable-table-roll.js \
  src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/treasury/service/TreasuryServiceTest.java
git commit -m "feat(tables): add confirmed encounter and reward drafts"
```

---

### Task 7: Package-v2 DTO, schema, semantic validation, dependency closure, links, and fidelity

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignManifestAssembler.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/packagev2/RollableTableSectionAdapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/packagev2/RollableTableExportClosureService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/packagev2/LibrarySectionAdapter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/AdventureSectionAdapter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/world/packagev2/WorldSectionAdapter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/catalog/CampaignCatalogService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/ImportProblemCodes.java`
- Modify: `src/main/resources/agent/validation-error-catalog.json`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/RollableTableSectionAdapterTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/packagev2/RollableTableExportClosureServiceTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidatorTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignDtoSchemaCompatibilityTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionRegistryTest.java`
- Modify test: all tests constructing `CampaignManifestV2` or priming `CampaignManifestAssembler`, found with the commands in Step 5.

**Interfaces:**
- Consumes: table/link aggregates, package key/context/deferred import, catalog/library adapters.
- Produces: optional `rollableTables` root section, `ROLLABLE_TABLE` content type, adapter order 250, dry-run problems with exact entry paths.

- [ ] **Step 1: Write the DTO/schema contract first**

Add these nested records and root field:

```java
List<RollableTableDto> rollableTables

public record RollableTableDto(
        String key, String sourceKey, String name, String description,
        String addressMode, String rollExpression, String category,
        List<String> tags, List<RollableTableEntryDto> entries,
        ProvenanceDto provenance) {}

public record RollableTableEntryDto(
        String key, Integer rangeStart, Integer rangeEnd, Integer weight,
        String resultText, String quantityExpression,
        List<ContentReference> references) {}

public record WorldLocationTableLinkDto(
        String role, ContentReference tableRef, int sortOrder) {}
```

Append `List<WorldLocationTableLinkDto> tableLinks` to `WorldLocationDto`. Add schema `$defs` with `additionalProperties:false`, exact enums, required table/entry identity fields, mode-specific `oneOf`, and examples for a d100 range table and weighted rumor table. Add `ROLLABLE_TABLE` to `$defs.contentType`. Do not add `rollableTables` to the root `required` array.

- [ ] **Step 2: Run the RED schema/DTO gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=CampaignManifestV2ContractTest,CampaignDtoSchemaCompatibilityTest test
```

Expected: FAIL until DTO and schema fields agree exactly.

- [ ] **Step 3: Write failing adapter and closure tests**

Test all ownership branches:

```text
campaign table -> exported as PACKAGE table
campaign scene/location -> referenced user-global table -> copied into rollableTables
global parent -> global nested child -> complete transitive closure exported
SRD nested table -> CATALOG ROLLABLE_TABLE ref, no copied DTO
global table provenance/sourceKey -> preserved; import row becomes CUSTOM campaign-scoped
table -> global custom statblock/item -> copied by LibrarySectionAdapter dependency set
unreferenced user-global table -> not exported
import order 250 registers tables before world/adventure deferred link resolution
```

- [ ] **Step 4: Implement closure and adapter**

`RollableTableExportClosureService.forCampaign(campaignId)` starts from campaign-owned tables plus table IDs referenced by campaign scenes/locations, then walks nested table refs. It returns sorted table IDs and referenced global custom library IDs grouped by content type. `RollableTableSectionAdapter` order is `250`; it exports campaign tables plus closure tables, maps SRD refs to catalog refs, maps copied custom refs to package refs, imports every DTO as CUSTOM campaign-scoped, registers table keys immediately, and defers entry reference IDs.

Extend `LibrarySectionAdapter` to union campaign-owned custom rows with exactly the global custom IDs in this closure, preserving provenance and binding them as campaign package entries on import. Do not export unrelated global content.

- [ ] **Step 5: Repair constructor/assembler compilation mechanically**

Run:

```bash
rg -l "new CampaignManifestV2\\(" src/main/java src/test/java
rg -l "new CampaignManifestAssembler\\(" src/main/java src/test/java
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables -DskipTests compile test-compile
```

Expected before repair: compilation errors at every canonical constructor and assembler fixture. Add `rollableTables` in canonical order and call `assembler.rollableTables(List.of())` in test assemblers. Repeat compile until it passes; do not add an ambiguous overloaded constructor that hides missing test data.

- [ ] **Step 6: Add semantic validation and error-catalog entries**

Register these codes in `ImportProblemCodes` and `validation-error-catalog.json` with `ERROR` severity and actionable repair hints:

```text
INVALID_TABLE_EXPRESSION
INVALID_QUANTITY_EXPRESSION
TABLE_RANGE_GAP
TABLE_RANGE_OVERLAP
TABLE_RANGE_BOUNDS
TABLE_WEIGHT_INVALID
INVALID_TABLE_REFERENCE_TYPE
TABLE_REFERENCE_CYCLE
TABLE_REFERENCE_DEPTH_EXCEEDED
```

Use existing `UNRESOLVED_REFERENCE`/`UNRESOLVED_CATALOG_REFERENCE` for missing targets. Build the full key map before checking refs. For example, the first bad quantity expression reports `/rollableTables/0/entries/0/quantityExpression`, and the second entry's first bad reference reports `/rollableTables/0/entries/1/references/0`. Validate scene link role/type and location table links. Old v2 fixtures with no table property produce no table warnings.

- [ ] **Step 7: Complete package links and fidelity ownership**

`AdventureSectionAdapter` already transports a generic scene link; teach semantic validation and import resolution that `RANDOM_ENCOUNTERS` requires `ROLLABLE_TABLE`. `WorldSectionAdapter` maps `tableLinks`. Add `ROLLABLE_TABLE` ownership query for campaign-scoped `RollableTable`; entries/refs remain nested aggregate values and do not receive package keys. Add `tableLinks` to ordered collections in `CampaignSemanticComparator` only if their declared `sortOrder` must be retained.

- [ ] **Step 8: Run the GREEN package gate**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=CampaignManifestV2ContractTest,CampaignDtoSchemaCompatibilityTest,RollableTableSectionAdapterTest,RollableTableExportClosureServiceTest,CampaignManifestV2SemanticValidatorTest,CampaignSectionRegistryTest,LibrarySectionAdapterTest,AdventureSectionAdapterTest,WorldSectionAdapterTest,ImportProblemCodesCoverageTest,ValidationErrorCatalogTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/ImportProblemCodes.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/library/packagev2/LibrarySectionAdapter.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/AdventureSectionAdapter.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/world/packagev2/WorldSectionAdapter.java \
  src/main/resources/schemas/campaign-format-v2.schema.json \
  src/main/resources/agent/validation-error-catalog.json src/test/java
git commit -m "feat(tables): round-trip tables and dependency closure"
```

---

### Task 8: Flagship fixtures, docs, capability contracts, security, performance, and browser acceptance

**Files:**
- Modify: `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json`
- Modify: `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json`
- Modify: `src/test/resources/campaigns/v2/current-surface.dmcampaign/manifest.json`
- Modify: `src/test/resources/campaigns/v2/minimal.dmcampaign.json` only if explicit empty-array serialization is chosen consistently
- Modify: `src/main/resources/agent/capability-manifest.json`
- Modify: `docs/campaign-capabilities.md`
- Modify: `docs/campaign-format-v2.md`
- Create: `docs/dm-manual/07-rollable-tables.md`
- Modify: `docs/dm-manual/README.md`
- Modify: `docs/authoring/README.md`
- Modify: `docs/agent/README.md`
- Modify: `docs/agent/mapping-rules.md`
- Modify: `docs/agent/conversion-playbook.md`
- Modify: `docs/product/release-notes.md`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/RollableTablePerformanceTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/rollabletable/RollableTablePlayerSafetyTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/CapabilityManifestContractTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/CapabilityMatrixMarkdownSyncTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/DmManualContractTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/agent/AgentGuideContractTest.java`
- Modify test: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Consumes: complete table behavior and package contract.
- Produces: executable published-shaped examples, `tables.rollable` capability, conversion guidance, performance/security/browser release evidence.

- [ ] **Step 1: Extend fixtures with source-shaped tables**

Feature-complete must have at least one table in every category:

```text
ENCOUNTER forest-encounters (nested table + statblock quantity)
TREASURE crypt-treasure (equipment/magic-item refs)
WEATHER road-weather
RUMOR tavern-rumors (WEIGHTED)
EVENT city-events
GENERIC npc-mannerisms
```

Published-adventure-shaped must include a contiguous d100 random-encounter table linked to a scene and world location, with one nested table and one `2d4` quantity. All source locators/provenance must identify existing fixture source material; do not invent missing DCs, rules, or copyrighted text.

- [ ] **Step 2: Run round-trip RED/GREEN until deep compare passes**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=CampaignManifestV2ContractTest,CampaignCompleteRoundTripTest,CampaignSemanticComparatorTest test
```

Expected final result: PASS for minimal, current-surface, feature-complete, published-adventure, structured-adventure-quest, and world-graph fixtures; pre-table fixtures have no warnings.

- [ ] **Step 3: Add capability and documentation contract tests first**

Add capability:

```json
{
  "id": "tables.rollable",
  "name": "Rollable tables",
  "status": "SUPPORTED",
  "deliveryItem": 11,
  "notes": "DM-only ranged/weighted tables, nested rolls, scene/location links, confirmed encounter/reward drafts, and package-v2 round-trip"
}
```

Add `ROLLABLE_TABLE` to `contentTypes`. Contract tests assert the capability, content type, DM manual page, schema field, error-catalog codes, and conversion mapping rules are all present before the tests pass.

- [ ] **Step 4: Write the actual docs**

`07-rollable-tables.md` covers scope choice, range/weight authoring, physical dice, roll N/duplicates, nested depth, scene/location linking, draft confirm/discard, deletion impact, import/export, and troubleshooting. `campaign-format-v2.md` documents exact DTO/schema examples and local-only roll history. Agent mapping rules explain die-column parsing and range normalization; the playbook explicitly forbids inventing missing ranges/weights/quantities and requires structured dry-run repair.

- [ ] **Step 5: Add performance and player-safety tests**

```java
@Test
void depthFiveNestedRollResolvesWithinLocalBudget() {
    assertTimeout(Duration.ofMillis(100), () -> service.roll(
            campaignId, depthFiveRootId,
            new TableRollRequest(null, 1, TableDuplicatePolicy.ALLOW_DUPLICATES)));
}
```

Warm the service once before timing and use deterministic mocked dice/repositories so startup/DB time is outside the measurement. Player-safety test creates a table with a unique secret marker, fetches actual `/player` page and player snapshot/presentation endpoints, and asserts the marker, table key, typed refs, and draft payload are absent.

- [ ] **Step 6: Add browser acceptance to the existing guarded harness**

Extend `CoreSessionLoopSmokeTest` with ordered actions that:

1. create a TREASURE table through the JSON API;
2. link it to the current scene;
3. open the session cockpit and roll from the story rail with one click;
4. observe grouped history and a pending reward draft;
5. discard once and assert treasury unchanged;
6. roll again, confirm, and assert the typed item exists in party stash;
7. create/roll an ENCOUNTER table, edit quantity, confirm, and assert a PLANNED encounter with combatants;
8. assert `BrowserFailureCollector` has no unexpected HTTP/console/page failures.

Do not use arbitrary sleeps for application state; wait on response URLs, visible draft elements, repository conditions, or locators.

- [ ] **Step 7: Run docs/security/performance/browser gates**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=CapabilityManifestContractTest,CapabilityMatrixMarkdownSyncTest,DmManualContractTest,AgentGuideContractTest,RollableTablePerformanceTest,RollableTablePlayerSafetyTest,MarkdownUtilTest,CoreSessionLoopSmokeTest test
```

Expected: PASS and no unexpected browser failures.

- [ ] **Step 8: Commit**

```bash
git add src/test/resources/campaigns/v2 src/main/resources/agent/capability-manifest.json \
  docs/campaign-capabilities.md docs/campaign-format-v2.md docs/dm-manual docs/authoring \
  docs/agent docs/product/release-notes.md src/test/java
git commit -m "docs(tables): add fixtures, guidance, and acceptance gates"
```

---

### Task 9: Final regression, manual evidence, and roadmap handoff

**Files:**
- Modify: `docs/superpowers/dm-only-readiness-roadmap.md`
- Modify: `docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md` only to change delivery items 1–2 to `IMPLEMENTED` with a dated evidence note; do not change later items.

**Interfaces:**
- Consumes: all prior task commits and acceptance evidence.
- Produces: row 3 `COMPLETE`, row 4 `READY`, current NEXT item 4.

- [ ] **Step 1: Run the focused table/package gate from a clean test home**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=DiceExpressionSpecTest,RollableTablePersistenceTest,RollableTableValidatorTest,RollableTableServiceTest,RollableTableRollServiceTest,TableRollGroupCodecTest,TableConsequenceServiceTest,RollableTableControllerTest,RollableTableApiControllerTest,RollableTableLinkServiceTest,RollableTableSectionAdapterTest,RollableTableExportClosureServiceTest,CampaignManifestV2ContractTest,CampaignManifestV2SemanticValidatorTest,CampaignCompleteRoundTripTest,SessionDraftServiceTest,RollableTablePlayerSafetyTest,RollableTablePerformanceTest test
```

Expected: PASS.

- [ ] **Step 2: Run the browser release gate independently**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables \
  -Dtest=CoreSessionLoopSmokeTest test
```

Expected: all ordered tests PASS and `BrowserFailureCollector` is clean.

- [ ] **Step 3: Run the complete suite**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-p3-rollable-tables test
```

Expected: PASS with zero failures/errors.

- [ ] **Step 4: Perform the bounded manual table acceptance**

Run the app with the isolated home, then verify in the browser:

```bash
./mvnw -Dspring-boot.run.jvmArguments=-Duser.home=/tmp/dmhelper-p3-rollable-tables spring-boot:run
```

Record evidence in the commit message or roadmap recovery note for: create/edit/clone; physical range roll; roll N with both policies; nested result; scene/location cockpit access; reward discard and confirm; encounter confirm; dependency-aware delete; export/import and reopened table. This is table-slice acceptance, not the final representative release session from roadmap row 11.

- [ ] **Step 5: Verify diff hygiene**

```bash
git status --short
git diff --check
git diff --stat
```

Expected: only intended table/docs/roadmap changes; no whitespace errors, secrets, generated browser data, provider tokens, or local database files.

- [ ] **Step 6: Close only this roadmap row**

Set row 3 to `COMPLETE`, link this plan as `Completed plan`, set row 4 to `READY`, set `Current NEXT item` to `4 — Traps and hazards`, and update the recovery note with exact focused/full/browser/manual evidence. In the atmosphere design, change delivery items 1–2 to `IMPLEMENTED`; leave items 3–9 `PLANNED`. Do not claim overall DM-only readiness.

- [ ] **Step 7: Commit the verified handoff**

```bash
git add docs/superpowers/dm-only-readiness-roadmap.md \
  docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md
git commit -m "docs(roadmap): close rollable tables and unblock traps"
```

---

## Self-review checklist

### Spec coverage

| Approved requirement | Plan task |
|---|---|
| Unified table ownership, provenance, categories, tags | 1–2 |
| Range/weight entries, text/typed refs, quantity expression | 1–2 |
| Existing dice engine and exact bounds | 1–3 |
| Nested rolls, depth 5, cycle detection | 2–3, 7 |
| Manual number, N rolls, duplicate policy | 3–4 |
| Grouped roll log and current session draft | 3 |
| CRUD/clone/search/destination/delete impact | 2, 4 |
| Scene and location links; cockpit one-click | 5 |
| Encounter draft and explicit confirm/discard | 6 |
| Reward draft and explicit confirm/discard | 6 |
| Package section/schema/DTO/semantic paths | 7 |
| User-global dependency closure and preserved provenance | 7 |
| Old v2 compatibility | 7–8 |
| Six-category flagship and published-adventure examples | 8 |
| Docs/capability/agent conversion | 8 |
| Hostile rendering/player safety/performance/browser | 4, 8 |
| Focused/full/manual closeout and next-row handoff | 9 |

### Type consistency

- Domain uses `TableAddressMode.WEIGHTED`; schema and DTO serialize `WEIGHTED`.
- Runtime refs use `TableReferenceScope.ENTITY|CATALOG`; package refs use existing `ContentReference.Scope.PACKAGE|CATALOG`.
- Stable table content type is `CampaignContentType.ROLLABLE_TABLE`; palette wire type is `rollable-table`.
- Root manual value affects only the root outcome; nested tables use digital dice and remain in the grouped tree.
- `TableRollLog` is local operational evidence and is never added to `CampaignManifestV2` or semantic ownership queries.
- `WorldLocationTableLinkDto` is ordered and nested under `WorldLocationDto`; it is not a separately keyed package entity.
- Draft status starts `NONE` without consequence refs or `PENDING` with an ENCOUNTER/TREASURE consequence; terminal states are `CONFIRMED`/`DISCARDED`.

### Scope guard

This plan does not implement traps/hazards, travel/weather state, fog, audio providers/cues, interactive players, automatic damage/condition application, automatic currency parsing, or copyrighted bundled tables. Those remain in roadmap rows 4–10.

### Completion evidence required before claiming success

- Focused table/package suite green.
- Independent `CoreSessionLoopSmokeTest` green with failure collector clean.
- Complete Maven suite green from isolated home.
- Manual table-slice acceptance recorded.
- `git diff --check` clean.
- Roadmap row 3 closed and row 4 unblocked only after all preceding evidence exists.
