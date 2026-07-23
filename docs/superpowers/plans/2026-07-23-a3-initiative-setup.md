# A3 Initiative Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit, dependable pre-combat initiative phase in which unset values are distinct from zero, the DM can enter party values or roll only unset NPCs, the final order is visible, and combat cannot begin accidentally.

**Architecture:** Keep encounter lifecycle (`PLANNED`, `ACTIVE`, `DONE`) separate from combat progress by adding `Encounter.CombatPhase { SETUP, RUNNING }`, and make `Combatant.initiative` nullable. `EncounterService` remains the authoritative transition boundary: activation enters setup, initiative mutations maintain a deterministic order, and `startCombat` validates or explicitly accepts unresolved values before starting the first turn. The existing Thymeleaf/Alpine tracker renders setup and running states from the same DTOs and uses the existing request/failure infrastructure.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2, Thymeleaf, Alpine.js, htmx, JUnit 5, MockMvc, Mockito, AssertJ, Playwright.

## Global Constraints

- Workstream A remains a release blocker and lands before cockpit layout workstream B1.
- Production and test execution both use `spring.jpa.open-in-view=false`.
- No new Maven, npm, runtime, docking, or frontend-framework dependency.
- Preserve the existing Spring/Thymeleaf/htmx/Alpine architecture.
- Initiative `null` means **unset**. Numeric `0` and negative numbers are valid resolved initiatives.
- Activation enters initiative setup; it does not silently start round 1.
- Setup lists every encounter combatant, including pending/reserve waves; later-wave rows are visibly labelled so they are not mistaken for current turn candidates.
- Automatic rolling affects every non-PC encounter combatant whose initiative is unset. This pre-resolves reinforcements before their wave is spawned.
- Automatic rolling uses `d20 + authoritative statblock Dexterity modifier`; combatants without a statblock use modifier `0`.
- Manual initiative values, including NPC values, are never overwritten by automatic rolling.
- Unset combatants accepted by the DM remain `null`, sort after resolved combatants, and retain deterministic relative order.
- Equal resolved initiatives sort by descending `tieBreaker`, then prior `sortOrder`, then name.
- **Start combat** is unavailable until every initiative is resolved or the DM explicitly accepts the remaining unset values.
- Starting combat selects the first eligible combatant, sets round `1`, and writes the existing `TURN_START` evidence. Do not add a new combat-log enum solely for this transition.
- `nextTurn`, `previousTurn`, and explicit active-turn changes reject encounters still in setup.
- Campaign format v2 changes are additive and backward compatible; do not increment `formatVersion`.
- Existing package files with integer initiatives continue to import unchanged.
- No private campaign package, source PDF, or `artifacts/` capture may be committed.
- Follow TDD: run each specified red test and observe the stated failure before changing production code.

## Chosen Model and Rejected Alternatives

Use both a nullable initiative and an explicit combat phase.

- **Chosen — nullable initiative plus `CombatPhase`:** represents unset values honestly and prevents lifecycle status or round numbers from carrying hidden meaning.
- **Rejected — integer sentinel such as `Integer.MIN_VALUE`:** leaks an implementation value into JSON, sorting, player projection, packages, and templates.
- **Rejected — infer setup from `round == 0` or `activeTurnIndex == -1`:** preserves the current ambiguity and makes restored/imported encounters difficult to reason about.

The migration interprets a legacy zero as unset only when the encounter has never established a turn (`active_turn_index = -1`, `round <= 1`, and not `DONE`). Zeros in running or completed encounters remain numeric zero. This is the least destructive conversion available because the legacy schema did not record whether a pre-combat zero was deliberate.

---

## File Structure

### Database and domain

- Create `src/main/resources/db/migration/V20__add_initiative_setup_phase.sql`
  - Makes `combatant.initiative` nullable, adds `encounter.combat_phase`, and conservatively classifies legacy rows.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java`
  - Changes initiative from `int` to `Integer`.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Encounter.java`
  - Owns `CombatPhase.SETUP` and `CombatPhase.RUNNING`.

### Service and HTTP boundary

- Create `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/InitiativeSetupIncompleteException.java`
  - Represents a safe HTTP 409 conflict when setup cannot start.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
  - Owns setup transitions, nullable ordering, manual clearing, NPC-only rolling, turn guards, undo fidelity, and active-combatant preservation during reordering.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java`
  - Adds the start-combat endpoint and returns a typed conflict for unresolved setup.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java`
  - Makes projected initiative nullable.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionService.java`
  - Preserves null rather than substituting zero.

### Package compatibility

- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java`
  - Makes combatant initiative nullable and adds optional encounter combat phase.
- Modify `src/main/resources/schemas/campaign-format-v2.schema.json`
  - Allows omitted/null initiative and optional `combatPhase`.
- Modify `src/main/resources/schemas/campaign-format.schema.json`
  - Keeps the legacy JSON export schema aligned with its nullable initiative and optional phase DTO.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterSectionAdapter.java`
  - Round-trips phase and nullable initiative with conservative defaults for older packages.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java`
  - Carries legacy nullable/default semantics into v2.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java`
  - Rejects contradictory phase/round/turn state using the existing `INVALID_STATE` code.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java`
  - Normalizes omitted legacy phase to its inferred value before comparison.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
  - Makes legacy export initiative nullable and includes optional phase.
- Modify `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
  - Imports the legacy DTO fields without coercing null to zero.

### Tracker and documentation

- Modify `src/main/resources/templates/encounter/_tracker.html`
  - Renders the compact setup panel, accessible initiative inputs, roll action, tie explanation, explicit acceptance, and Start combat action.
- Modify `src/main/resources/static/css/components.css`
  - Styles setup rows and keeps the tracker usable in narrow cockpit modules.
- Modify `docs/dm-manual/03-session-cockpit.md`
  - Documents the setup workflow and acceptance semantics.
- Modify `docs/campaign-format-v2.md`
  - Documents nullable initiative and `combatPhase`.

### Tests

- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/threat/data/ThreatMigrationTest.java`
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterInitiativeSetupServiceTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterWaveServiceTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiControllerTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterTemplateContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignDtoSchemaCompatibilityTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/EncounterSectionAdapterTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidatorTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionServiceTest.java`
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

---

### Task 1: Add Nullable Initiative and an Explicit Combat Phase

**Files:**

- Create: `src/main/resources/db/migration/V20__add_initiative_setup_phase.sql`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Encounter.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/threat/data/ThreatMigrationTest.java`

**Interfaces:**

- Produces: `Encounter.CombatPhase { SETUP, RUNNING }`.
- Produces: `Integer Combatant.getInitiative()` and `void Combatant.setInitiative(Integer initiative)`.
- Migration default: new and never-started encounters are `SETUP`.
- Migration inference: `DONE`, `active_turn_index >= 0`, or `round > 1` becomes `RUNNING`.

- [ ] **Step 1: Write the red migration assertions**

Add to `FlywayMigrationTest`:

```java
@Test
void v20AddsExplicitInitiativeSetupState() {
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"flyway_schema_history\" "
                    + "WHERE \"version\" = '20' AND \"success\" = TRUE",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'ENCOUNTER'
              AND column_name = 'COMBAT_PHASE'
              AND is_nullable = 'NO'
            """, Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'COMBATANT'
              AND column_name = 'INITIATIVE'
              AND is_nullable = 'YES'
            """, Integer.class)).isEqualTo(1);
}
```

Extend `FlywayLegacyUpgradeTest.createLegacyDatabase()` with one setup encounter containing initiative `0`, one running encounter containing initiative `0`, and one setup encounter containing initiative `12`:

```java
stmt.execute("""
        INSERT INTO encounter (
            id, campaign_id, name, status, round, active_turn_index,
            log_sequence, lair_action_triggered)
        SELECT RANDOM_UUID(), id, 'Legacy Setup', 'ACTIVE', 1, -1, 0, FALSE
        FROM campaign WHERE name = 'Curse of Strahd'
        """);
stmt.execute("""
        INSERT INTO encounter (
            id, campaign_id, name, status, round, active_turn_index,
            log_sequence, lair_action_triggered)
        SELECT RANDOM_UUID(), id, 'Legacy Running', 'ACTIVE', 1, 0, 0, FALSE
        FROM campaign WHERE name = 'Curse of Strahd'
        """);
stmt.execute("""
        INSERT INTO encounter (
            id, campaign_id, name, status, round, active_turn_index,
            log_sequence, lair_action_triggered)
        SELECT RANDOM_UUID(), id, 'Legacy Manual', 'PLANNED', 0, -1, 0, FALSE
        FROM campaign WHERE name = 'Curse of Strahd'
        """);
stmt.execute("""
        INSERT INTO combatant (
            id, encounter_id, name, kind, initiative, tie_breaker, sort_order,
            max_hp, current_hp, temp_hp, defeated, hidden, group_leader,
            concentration_check_pending, legendary_actions_used,
            legendary_actions_max, legendary_resistances_used,
            legendary_resistances_max)
        SELECT RANDOM_UUID(), e.id, 'Unset Goblin', 'NPC', 0, 0, 0,
               10, 10, 0, FALSE, FALSE, FALSE, FALSE, 0, 0, 0, 0
        FROM encounter e WHERE e.name = 'Legacy Setup'
        """);
stmt.execute("""
        INSERT INTO combatant (
            id, encounter_id, name, kind, initiative, tie_breaker, sort_order,
            max_hp, current_hp, temp_hp, defeated, hidden, group_leader,
            concentration_check_pending, legendary_actions_used,
            legendary_actions_max, legendary_resistances_used,
            legendary_resistances_max)
        SELECT RANDOM_UUID(), e.id, 'Zero Rogue', 'PC', 0, 0, 0,
               10, 10, 0, FALSE, FALSE, FALSE, FALSE, 0, 0, 0, 0
        FROM encounter e WHERE e.name = 'Legacy Running'
        """);
stmt.execute("""
        INSERT INTO combatant (
            id, encounter_id, name, kind, initiative, tie_breaker, sort_order,
            max_hp, current_hp, temp_hp, defeated, hidden, group_leader,
            concentration_check_pending, legendary_actions_used,
            legendary_actions_max, legendary_resistances_used,
            legendary_resistances_max)
        SELECT RANDOM_UUID(), e.id, 'Manual Orc', 'NPC', 12, 0, 0,
               10, 10, 0, FALSE, FALSE, FALSE, FALSE, 0, 0, 0, 0
        FROM encounter e WHERE e.name = 'Legacy Manual'
        """);
```

The assertions are:

```java
@Test
void v20ConservativelyClassifiesLegacyInitiativeState() {
    assertThat(jdbc.queryForObject(
            "SELECT combat_phase FROM encounter WHERE name='Legacy Setup'", String.class))
            .isEqualTo("SETUP");
    assertThat(jdbc.queryForObject(
            "SELECT initiative FROM combatant WHERE name='Unset Goblin'", Integer.class))
            .isNull();
    assertThat(jdbc.queryForObject(
            "SELECT combat_phase FROM encounter WHERE name='Legacy Running'", String.class))
            .isEqualTo("RUNNING");
    assertThat(jdbc.queryForObject(
            "SELECT initiative FROM combatant WHERE name='Zero Rogue'", Integer.class))
            .isZero();
    assertThat(jdbc.queryForObject(
            "SELECT initiative FROM combatant WHERE name='Manual Orc'", Integer.class))
            .isEqualTo(12);
}
```

- [ ] **Step 2: Run the migration tests and observe the failure**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest,FlywayLegacyUpgradeTest test
```

Expected: failures because migration 20, `COMBAT_PHASE`, and nullable `INITIATIVE` do not exist.

- [ ] **Step 3: Add migration V20**

Create `V20__add_initiative_setup_phase.sql`:

```sql
alter table encounter
    add column combat_phase varchar(16) not null default 'SETUP';

update encounter
set combat_phase = case
    when status = 'DONE' or active_turn_index >= 0 or round > 1 then 'RUNNING'
    else 'SETUP'
end;

alter table encounter
    add constraint ck_encounter_combat_phase
    check (combat_phase in ('SETUP', 'RUNNING'));

alter table combatant
    alter column initiative drop not null;

update combatant
set initiative = null
where initiative = 0
  and encounter_id in (
      select id from encounter where combat_phase = 'SETUP'
  );
```

- [ ] **Step 4: Implement the domain types**

In `Encounter`:

```java
public enum Status { PLANNED, ACTIVE, DONE }
public enum CombatPhase { SETUP, RUNNING }

@Enumerated(EnumType.STRING)
@Column(name = "combat_phase", nullable = false, length = 16)
private CombatPhase combatPhase = CombatPhase.SETUP;

public CombatPhase getCombatPhase() { return combatPhase; }
public void setCombatPhase(CombatPhase combatPhase) {
    this.combatPhase = combatPhase == null ? CombatPhase.SETUP : combatPhase;
}
```

In `Combatant`:

```java
@Column
private Integer initiative;

public Integer getInitiative() { return initiative; }
public void setInitiative(Integer initiative) { this.initiative = initiative; }
```

Do not add a second boolean such as `initiativeSet`; null is the single source of truth.

- [ ] **Step 5: Update the migration-count guard**

Change `ThreatMigrationTest.flywayReportsNineteenMigrations` to `flywayReportsTwentyMigrations` and assert `20` for both count and current version.

- [ ] **Step 6: Run migration and persistence tests**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest,FlywayLegacyUpgradeTest,ThreatMigrationTest test
```

Expected: all tests pass, including the legacy zero-preservation cases.

- [ ] **Step 7: Commit the schema foundation**

```bash
git add src/main/resources/db/migration/V20__add_initiative_setup_phase.sql \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Encounter.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat/data/ThreatMigrationTest.java
git commit -m "feat(encounter): add explicit initiative setup state"
```

---

### Task 2: Enforce Initiative Setup in the Service and API

**Files:**

- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/InitiativeSetupIncompleteException.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterInitiativeSetupServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterWaveServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiControllerTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionServiceTest.java`

**Interfaces:**

- Produces: `CombatantDto(..., Integer initiative, ...)`.
- Produces: `EncounterDto(..., String combatPhase, ...)`.
- Produces: `InitiativeRequest(Integer initiative)`.
- Produces: `StartCombatRequest(boolean acceptUnset)`.
- Produces: `EncounterDto EncounterService.startCombat(UUID encounterId, boolean acceptUnset)`.
- Produces: `List<CombatantDto> EncounterService.getInitiativeSetupCombatants(UUID encounterId)`.
- Produces: `List<CombatantDto> EncounterService.rollUnsetNpcInitiatives(UUID encounterId)`.
- HTTP: `GET /api/v1/encounters/{id}/initiative-setup/combatants` returns all waves for the setup panel.
- HTTP: `POST /api/v1/encounters/{id}/start-combat` with `{"acceptUnset":false}`.
- HTTP: existing `POST /api/v1/encounters/{id}/auto-roll` now means “roll unset NPC initiatives.”

- [ ] **Step 1: Write red service tests for setup semantics**

Create `EncounterInitiativeSetupServiceTest` using the same `@DataJpaTest`, repository mocks, campaign fixture, and map fixture as `EncounterServiceTest`. Copy the existing `@Import` list **without** `DiceEngine.class`, then replace that real bean with:

```java
@MockitoBean
private DiceEngine diceEngine;
```

Add these tests:

```java
@Test
void activationEntersSetupWithoutStartingRoundOne() {
    EncounterDto encounter = service.create(campaign.getId(), new CreateRequest("Setup", null));

    EncounterDto active = service.activate(encounter.id());

    assertThat(active.status()).isEqualTo("ACTIVE");
    assertThat(active.combatPhase()).isEqualTo("SETUP");
    assertThat(active.round()).isZero();
    assertThat(active.activeTurnIndex()).isEqualTo(-1);
}

@Test
void manualInitiativeDistinguishesUnsetZeroAndNegative() {
    EncounterDto encounter = service.create(campaign.getId(), new CreateRequest("Setup", null));
    CombatantDto zero = add(encounter.id(), "Zero", "PC");
    CombatantDto negative = add(encounter.id(), "Negative", "PC");

    assertThat(zero.initiative()).isNull();
    service.setInitiative(zero.id(), 0);
    service.setInitiative(negative.id(), -2);

    assertThat(service.getCombatant(zero.id()).initiative()).isZero();
    assertThat(service.getCombatant(negative.id()).initiative()).isEqualTo(-2);
}

@Test
void clearingManualInitiativeReturnsCombatantToUnset() {
    EncounterDto encounter = service.create(campaign.getId(), new CreateRequest("Setup", null));
    CombatantDto combatant = add(encounter.id(), "Scout", "PC");
    service.setInitiative(combatant.id(), 18);

    service.setInitiative(combatant.id(), null);

    assertThat(service.getCombatant(combatant.id()).initiative()).isNull();
}

@Test
void autoRollTouchesOnlyUnsetNonPcCombatants() {
    EncounterDto encounter = service.create(campaign.getId(), new CreateRequest("Setup", null));
    CombatantDto manualNpc = add(encounter.id(), "Manual NPC", "MONSTER");
    CombatantDto unsetNpc = add(encounter.id(), "Unset NPC", "MONSTER");
    CombatantDto unsetPc = add(encounter.id(), "Unset PC", "PC");
    service.setInitiative(manualNpc.id(), 7);
    when(diceEngine.roll("d20")).thenReturn(
            new DiceResult("d20", List.of(), 0, 14, false, false));

    service.rollUnsetNpcInitiatives(encounter.id());

    assertThat(service.getCombatant(manualNpc.id()).initiative()).isEqualTo(7);
    assertThat(service.getCombatant(unsetNpc.id()).initiative()).isEqualTo(14);
    assertThat(service.getCombatant(unsetPc.id()).initiative()).isNull();
    verify(diceEngine, times(1)).roll("d20");
}

@Test
void setupIncludesAndPreRollsPendingWaveCombatants() {
    EncounterDto encounter = service.create(campaign.getId(), new CreateRequest("Waves", null));
    WaveDto reserve = service.createWave(
            encounter.id(),
            new CreateWaveRequest(
                    "reserve", "Reserve", WaveTriggerKind.MANUAL, null, null));
    CombatantDto reserveNpc = addToWave(
            encounter.id(), reserve.id(), "Reserve Goblin", "MONSTER");
    when(diceEngine.roll("d20")).thenReturn(
            new DiceResult("d20", List.of(), 0, 11, false, false));

    service.activate(encounter.id());
    service.rollUnsetNpcInitiatives(encounter.id());

    assertThat(service.getCombatants(encounter.id())).isEmpty();
    assertThat(service.getInitiativeSetupCombatants(encounter.id()))
            .extracting(CombatantDto::id)
            .contains(reserveNpc.id());
    assertThat(service.getCombatant(reserveNpc.id()).initiative()).isEqualTo(11);
}

@Test
void startCombatRejectsUnacceptedUnsetValuesWithoutMutation() {
    EncounterDto encounter = activeEncounter("Blocked");
    add(encounter.id(), "Unset PC", "PC");

    assertThatThrownBy(() -> service.startCombat(encounter.id(), false))
            .isInstanceOf(InitiativeSetupIncompleteException.class)
            .hasMessageContaining("1");

    EncounterDto unchanged = service.getById(encounter.id());
    assertThat(unchanged.combatPhase()).isEqualTo("SETUP");
    assertThat(unchanged.round()).isZero();
    assertThat(unchanged.activeTurnIndex()).isEqualTo(-1);
}

@Test
void explicitAcceptanceStartsInDisplayedOrder() {
    EncounterDto encounter = activeEncounter("Accepted");
    CombatantDto resolved = add(encounter.id(), "Resolved", "MONSTER");
    add(encounter.id(), "Unset", "PC");
    service.setInitiative(resolved.id(), 0);

    EncounterDto started = service.startCombat(encounter.id(), true);

    assertThat(started.combatPhase()).isEqualTo("RUNNING");
    assertThat(started.round()).isEqualTo(1);
    assertThat(service.getCombatants(encounter.id()).get(started.activeTurnIndex()).name())
            .isEqualTo("Resolved");
}

@Test
void turnEndpointsRejectSetupPhase() {
    EncounterDto encounter = activeEncounter("Not started");
    service.setInitiative(add(encounter.id(), "Ready", "PC").id(), 10);

    assertThatThrownBy(() -> service.nextTurn(encounter.id()))
            .isInstanceOf(InitiativeSetupIncompleteException.class);
    assertThatThrownBy(() -> service.previousTurn(encounter.id()))
            .isInstanceOf(InitiativeSetupIncompleteException.class);
}
```

Use small local helpers:

```java
private CombatantDto add(UUID encounterId, String name, String kind) {
    return service.addCombatant(encounterId,
            new CombatantCreateRequest(name, 10, kind, null, null, null));
}

private EncounterDto activeEncounter(String name) {
    EncounterDto created = service.create(campaign.getId(), new CreateRequest(name, null));
    return service.activate(created.id());
}
```

Implement `addToWave` with the test fixture's existing combatant-update path: add the combatant, then assign its `waveId` through `CombatantUpdateRequest`. Do not make pending waves visible through the existing running-combat `getCombatants` contract.

- [ ] **Step 2: Write red ordering, logging, and active-turn preservation tests**

Add:

```java
@Test
void orderingPlacesUnsetLastAndPreservesZeroAndNegative() {
    EncounterDto encounter = service.create(campaign.getId(), new CreateRequest("Order", null));
    CombatantDto unset = add(encounter.id(), "Unset", "PC");
    CombatantDto zero = add(encounter.id(), "Zero", "PC");
    CombatantDto negative = add(encounter.id(), "Negative", "PC");
    service.setInitiative(zero.id(), 0);
    service.setInitiative(negative.id(), -1);

    assertThat(service.getCombatants(encounter.id()))
            .extracting(CombatantDto::name)
            .containsExactly("Zero", "Negative", "Unset");
}

@Test
void changingInitiativeDuringCombatPreservesActiveCombatantIdentity() {
    EncounterDto encounter = activeEncounter("Running");
    CombatantDto a = add(encounter.id(), "A", "PC");
    CombatantDto b = add(encounter.id(), "B", "PC");
    service.setInitiative(a.id(), 20);
    service.setInitiative(b.id(), 10);
    EncounterDto started = service.startCombat(encounter.id(), false);
    UUID activeId = service.getCombatants(encounter.id()).get(started.activeTurnIndex()).id();

    service.setInitiative(b.id(), 25);

    EncounterDto reordered = service.getById(encounter.id());
    assertThat(service.getCombatants(encounter.id()).get(reordered.activeTurnIndex()).id())
            .isEqualTo(activeId);
}

@Test
void nullableInitiativeRoundTripsThroughUndoEvidence() {
    EncounterDto encounter = service.create(campaign.getId(), new CreateRequest("Undo", null));
    CombatantDto combatant = add(encounter.id(), "Scout", "PC");
    service.setInitiative(combatant.id(), 0);
    service.setInitiative(combatant.id(), null);

    service.undo(encounter.id());

    assertThat(service.getCombatant(combatant.id()).initiative()).isZero();
}
```

In `EncounterWaveServiceTest`, add a running encounter whose active combatant is not at index
zero, activate a pre-resolved reserve wave, and assert both:

- the newly active wave is inserted according to `INITIATIVE_ORDER`; and
- the encounter's `activeTurnIndex` is rewritten to the same previously active combatant ID.

Update `spawnWave` to invoke the same identity-preserving `resortCombatants` path after changing
the wave status. This makes pre-rolled reinforcements immediately join the correct turn order
without stealing the current turn.

- [ ] **Step 3: Run the red service tests**

Run:

```bash
./mvnw -Dtest=EncounterInitiativeSetupServiceTest test
```

Expected: compilation failures because initiative is still exposed through primitive DTOs and setup APIs do not exist.

- [ ] **Step 4: Add the service/API contracts**

Change the records in `EncounterService`:

```java
public record CombatantDto(
        UUID id, UUID encounterId, String name, Integer initiative, int sortOrder,
        int currentHp, int maxHp, int tempHp, String kind, String groupId,
        boolean groupLeader, UUID tokenId, UUID statBlockId, UUID partyMemberId,
        boolean defeated, boolean hidden, boolean bloodied,
        List<ConditionStateDto> conditions, String concentratingOn,
        boolean concentrationCheckPending, int legendaryActionsUsed,
        int legendaryActionsMax, int legendaryResistancesUsed,
        int legendaryResistancesMax, String notes, UUID waveId,
        Integer startX, Integer startY, String placementRegionKey,
        ThreatKind threatKind, UUID threatId, ThreatCardView threatCard
) {}

public record InitiativeRequest(Integer initiative) {}
public record StartCombatRequest(boolean acceptUnset) {}

public record EncounterDto(
        UUID id, UUID campaignId, UUID mapId, String name, String status,
        int round, int activeTurnIndex, String combatPhase, int combatantCount,
        String lairActionName, String lairActionDescription,
        boolean lairActionAvailable, List<CombatantDto> combatants,
        List<RechargePrompt> rechargePrompts
) {}
```

All `EncounterDto` factories must emit `e.getCombatPhase().name()`. All combatant creation paths leave initiative null unless a request explicitly supplies it.

Create:

```java
package dev.hendrikhoemberg.dmhelper.encounter.service;

public final class InitiativeSetupIncompleteException extends RuntimeException {
    private final int unsetCount;

    public InitiativeSetupIncompleteException(String message, int unsetCount) {
        super(message);
        this.unsetCount = unsetCount;
    }

    public int unsetCount() {
        return unsetCount;
    }
}
```

- [ ] **Step 5: Implement deterministic nullable ordering**

Add one comparator and use it in `resortCombatants` and undo order rebuilding:

```java
private static final Comparator<Combatant> INITIATIVE_ORDER =
        Comparator.comparing(
                        Combatant::getInitiative,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        Comparator.comparingInt(Combatant::getTieBreaker).reversed())
                .thenComparingInt(Combatant::getSortOrder)
                .thenComparing(Combatant::getName, String.CASE_INSENSITIVE_ORDER);

private static int initiativeForThreshold(Combatant combatant) {
    return combatant.getInitiative() == null
            ? Integer.MIN_VALUE
            : combatant.getInitiative();
}
```

Before sorting a running encounter, capture the active combatant ID. After saving the new `sortOrder` values, recompute and save `activeTurnIndex` by that ID. Use `initiativeForThreshold` for the lair-action crossing check so accepted unset values cannot throw a null-unboxing exception.

- [ ] **Step 6: Implement manual initiative, NPC rolling, activation, and start**

`setInitiative` accepts `Integer`. Build its log payload with an `ObjectNode`, because `Map.of` rejects null:

```java
ObjectNode payload = JSON_MAPPER.createObjectNode();
if (initiative == null) payload.putNull("initiative");
else payload.put("initiative", initiative);
if (previous == null) payload.putNull("previousInitiative");
else payload.put("previousInitiative", previous);
```

Replace `autoRollInitiative` with:

```java
public List<CombatantDto> rollUnsetNpcInitiatives(UUID encounterId) {
    Encounter encounter = requireSetup(encounterId);
    for (Combatant combatant
            : combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId)) {
        if (combatant.getInitiative() != null || "PC".equals(combatant.getKind())) {
            continue;
        }
        int modifier = combatant.getStatBlock() == null
                ? 0
                : dexModifier(combatant.getStatBlock());
        int roll = diceEngine.roll("d20").total();
        combatant.setInitiative(roll + modifier);
        combatantRepo.save(combatant);
        logInitiativeRoll(encounter.getId(), combatant, roll, modifier);
    }
    resortCombatants(encounterId);
    return getCombatants(encounterId);
}
```

Add a read-only setup query that returns
`combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId)` mapped to DTOs without the existing active-wave filter. Expose it at
`GET /api/v1/encounters/{id}/initiative-setup/combatants`. Keep the existing
`GET /api/v1/encounters/{id}/combatants` behavior unchanged for running combat, wave tests, the active-turn index, and player projection.

Activation becomes:

```java
e.setStatus(Encounter.Status.ACTIVE);
e.setCombatPhase(Encounter.CombatPhase.SETUP);
e.setRound(0);
e.setActiveTurnIndex(-1);
```

`requireSetup` must require both `status == ACTIVE` and `combatPhase == SETUP`.
`requireRunning` must require both `status == ACTIVE` and `combatPhase == RUNNING`; use the same
`InitiativeSetupIncompleteException` for setup-phase turn attempts and the existing lifecycle
exception style for non-active encounters.

Add:

```java
public EncounterDto startCombat(UUID encounterId, boolean acceptUnset) {
    Encounter encounter = requireSetup(encounterId);
    List<Combatant> allCombatants =
            combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
    if (allCombatants.isEmpty()) {
        throw new InitiativeSetupIncompleteException(
                "Combat cannot start without combatants.", 0);
    }
    long unset = allCombatants.stream()
            .filter(c -> c.getInitiative() == null)
            .count();
    if (unset > 0 && !acceptUnset) {
        throw new InitiativeSetupIncompleteException(
                unset + " combatant(s) still have unset initiative.", (int) unset);
    }

    resortCombatants(encounterId);
    List<Combatant> combatants = getActiveCombatants(encounterId);
    int first = firstEligibleTurnIndex(combatants);
    if (first < 0) {
        throw new InitiativeSetupIncompleteException(
                "Combat cannot start without an eligible active-wave combatant.",
                (int) unset);
    }

    encounter.setCombatPhase(Encounter.CombatPhase.RUNNING);
    encounter.setRound(1);
    encounter.setActiveTurnIndex(first);
    encounterRepo.save(encounter);
    resetLegendaryActions(combatants.get(first));
    logEntry(encounterId, CombatLogEntry.EntryType.TURN_START,
            combatants.get(first).getId().toString(),
            "{\"activeTurnIndex\":" + first + "}");
    tablePresentationService.broadcastCurrentState(encounter.getCampaign().getId());
    return toDto(encounter);
}
```

Extract `firstEligibleTurnIndex` from the same defeated/group-leader rule already used by `nextTurn`. Add `requireRunning(encounterId)` to `nextTurn`, `previousTurn`, and `setActiveTurn`.

- [ ] **Step 7: Make undo and add/remove evidence null-safe**

Apply these rules:

- `resetCombatantToBaseline` sets initiative to `null`.
- `INITIATIVE_SET` replay distinguishes a missing/JSON-null node from numeric zero.
- `COMBATANT_ADDED` and `COMBATANT_REMOVED` payloads use mutable maps or `ObjectNode`, never `Map.of`, when initiative may be null.
- Restoring a combatant reads `initiative` with:

```java
JsonNode initiativeNode = node.get("initiative");
restored.setInitiative(
        initiativeNode == null || initiativeNode.isNull()
                ? null
                : initiativeNode.asInt());
```

- Undo restores encounter phase, round, and active turn consistently. An encounter with no retained `TURN_START` evidence is `SETUP`; one with retained turn evidence is `RUNNING`.

- [ ] **Step 8: Add the HTTP endpoint and a shared conflict response**

In `EncounterApiController`:

```java
@PostMapping("/encounters/{id}/start-combat")
public EncounterService.EncounterDto startCombat(
        @PathVariable UUID id,
        @RequestBody EncounterService.StartCombatRequest request) {
    return service.startCombat(id, request.acceptUnset());
}

@ExceptionHandler(InitiativeSetupIncompleteException.class)
public ResponseEntity<ProblemDetail> initiativeSetupConflict(
        InitiativeSetupIncompleteException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Initiative Setup Incomplete");
    problem.setProperty("unsetCount", ex.unsetCount());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
}
```

Keep `/auto-roll` for compatibility, but call `service.rollUnsetNpcInitiatives(id)`. Add the setup-combatants GET endpoint beside the existing filtered combatants endpoint. The class-level exception handler applies equally to Start combat and guarded Next/Previous/Set-active requests, so setup violations never leak as HTTP 500.

- [ ] **Step 9: Add API contract tests**

Add to `EncounterApiControllerTest`:

```java
@Test
void startCombatPassesExplicitAcceptance() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.startCombat(id, true)).thenReturn(
            new EncounterDto(id, UUID.randomUUID(), null, "Started", "ACTIVE",
                    1, 0, "RUNNING", 2, null, null, false, List.of(), List.of()));

    mockMvc.perform(post("/api/v1/encounters/{id}/start-combat", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"acceptUnset\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.combatPhase").value("RUNNING"))
            .andExpect(jsonPath("$.round").value(1));
}

@Test
void incompleteSetupReturnsTypedConflict() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.startCombat(id, false))
            .thenThrow(new InitiativeSetupIncompleteException(
                    "2 combatant(s) still have unset initiative.", 2));

    mockMvc.perform(post("/api/v1/encounters/{id}/start-combat", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"acceptUnset\":false}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Initiative Setup Incomplete"))
            .andExpect(jsonPath("$.unsetCount").value(2));
}

@Test
void setupCombatantsUsesTheUnfilteredSetupQuery() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.getInitiativeSetupCombatants(id))
            .thenReturn(List.of(combatant("Reserve Goblin")));

    mockMvc.perform(get(
                    "/api/v1/encounters/{id}/initiative-setup/combatants", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Reserve Goblin"));

    verify(service).getInitiativeSetupCombatants(id);
    verify(service, never()).getCombatants(id);
}

@Test
void initiativeRequestAcceptsZeroNegativeAndNull() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.setInitiative(id, 0)).thenReturn(combatant("Zero"));
    mockMvc.perform(put("/api/v1/combatants/{id}/initiative", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"initiative\":0}"))
            .andExpect(status().isOk());

    when(service.setInitiative(id, -3)).thenReturn(combatant("Negative"));
    mockMvc.perform(put("/api/v1/combatants/{id}/initiative", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"initiative\":-3}"))
            .andExpect(status().isOk());

    when(service.setInitiative(id, null)).thenReturn(combatant("Unset"));
    mockMvc.perform(put("/api/v1/combatants/{id}/initiative", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"initiative\":null}"))
            .andExpect(status().isOk());
}
```

- [ ] **Step 10: Make player projection nullable**

Change `LiveTableState.CombatantSnapshot.initiative` from `int` to `Integer`. Keep the value unchanged in `PlayerSafeProjectionService`; do not coerce null to zero. Add a focused test with one null, one zero, and one negative initiative and assert all three JSON/domain values remain distinct.

- [ ] **Step 11: Update existing turn tests**

Every service test that currently does `activate(); nextTurn();` must resolve initiative and call:

```java
service.startCombat(encounterId, false);
```

The first combatant is active immediately after `startCombat`; update expected indices accordingly. Tests that intentionally exercise unresolved acceptance use `startCombat(encounterId, true)`.

- [ ] **Step 12: Run service, API, projection, and regression tests**

Run:

```bash
./mvnw -Dtest=EncounterInitiativeSetupServiceTest,EncounterServiceTest,EncounterWaveServiceTest,EncounterApiControllerTest,PlayerSafeProjectionServiceTest,ThreatEncounterIntegrationTest test
```

Expected: all tests pass; zero and negative values remain numeric, null remains unset, and turn methods cannot bypass setup.

- [ ] **Step 13: Commit the authoritative setup boundary**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/InitiativeSetupIncompleteException.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterInitiativeSetupServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterWaveServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiControllerTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatEncounterIntegrationTest.java
git commit -m "feat(encounter): enforce initiative setup before combat"
```

---

### Task 3: Preserve A3 State Through Campaign Packages

**Files:**

- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Modify: `src/main/resources/schemas/campaign-format.schema.json`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterSectionAdapter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignDtoSchemaCompatibilityTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/EncounterSectionAdapterTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidatorTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparatorTest.java`

**Interfaces:**

- `CampaignManifestV2.CombatantDto.initiative()` becomes `Integer`.
- `CampaignManifestV2.EncounterDto` adds `String combatPhase`.
- Old package inference:
  - `DONE`, `activeTurnIndex >= 0`, or `round > 1` → `RUNNING`.
  - otherwise → `SETUP`.
- Omitted initiative imports as unset; explicit `0` imports as numeric zero.

- [ ] **Step 1: Write red adapter round-trip tests**

Add three package cases:

```java
@Test
void roundTripsSetupPhaseWithUnsetZeroAndNegativeInitiative() {
    // Persist one SETUP encounter with initiatives null, 0, and -2.
    // Export, import into a new campaign, then assert:
    assertThat(importedEncounter.getCombatPhase()).isEqualTo(Encounter.CombatPhase.SETUP);
    assertThat(importedCombatants)
            .extracting(Combatant::getInitiative)
            .containsExactly(null, 0, -2);
}

@Test
void olderPackageWithoutCombatPhaseInfersSetup() {
    CampaignManifestV2.EncounterDto legacy = encounterDto(
            "ACTIVE", 1, -1, null, List.of(combatantDto(null)));

    importManifest(withEncounter(legacy));

    assertThat(importedEncounter.getCombatPhase()).isEqualTo(Encounter.CombatPhase.SETUP);
}

@Test
void olderPackageWithActiveTurnInfersRunning() {
    CampaignManifestV2.EncounterDto legacy = encounterDto(
            "ACTIVE", 1, 0, null, List.of(combatantDto(0)));

    importManifest(withEncounter(legacy));

    assertThat(importedEncounter.getCombatPhase()).isEqualTo(Encounter.CombatPhase.RUNNING);
}
```

Use the existing complete record constructors/helpers in `EncounterSectionAdapterTest`; do not introduce a second test-only JSON model.

- [ ] **Step 2: Write red semantic-validation tests**

Add:

```java
@Test
void setupPhaseRejectsEstablishedActiveTurn() {
    var encounter = encounterDto("ACTIVE", 0, 0, "SETUP", List.of());

    assertThat(validator.validate(withEncounter(encounter)))
            .extracting(CampaignImportProblem::code)
            .contains("INVALID_STATE");
}

@Test
void activeRunningPhaseRequiresPositiveRoundAndActiveTurn() {
    var encounter = encounterDto("ACTIVE", 0, -1, "RUNNING", List.of());

    assertThat(validator.validate(withEncounter(encounter)))
            .extracting(CampaignImportProblem::code)
            .contains("INVALID_STATE");
}
```

The valid rules are:

- `SETUP`: `activeTurnIndex == -1` and `round` is `0` or legacy `1`.
- `RUNNING` + `ACTIVE`: `round >= 1` and `activeTurnIndex >= 0`.
- `RUNNING` + `DONE`: `round >= 0`; `activeTurnIndex` may be `-1` because a completed import may discard the last active turn, but any non-negative index must still point at a combatant.
- The existing active-index bounds check remains authoritative for every status and phase.

- [ ] **Step 3: Run package tests and observe the failure**

Run:

```bash
./mvnw -Dtest=EncounterSectionAdapterTest,CampaignManifestV2SemanticValidatorTest,CampaignImportExportRoundTripTest,CampaignSemanticComparatorTest test
```

Expected: compilation/schema failures because initiative is primitive and `combatPhase` is absent.

- [ ] **Step 4: Extend the v2 model and schema**

In the existing full `CampaignManifestV2.CombatantDto` declaration, change only the component `int initiative` to `Integer initiative`; do not reorder any record components.

Add `String combatPhase` beside encounter `status`, `round`, and `activeTurnIndex`.

In the JSON schema:

```json
"combatPhase": {
  "type": "string",
  "enum": ["SETUP", "RUNNING"]
}
```

Change initiative to:

```json
"initiative": { "type": ["integer", "null"] }
```

Remove `"initiative"` from the combatant `required` array so older/newer producers may omit an unset value. Keep `additionalProperties: false`.

Mirror the nullable/optional `initiative` rule and optional `combatPhase` property in
`campaign-format.schema.json` for `CampaignExportDto`. Extend
`CampaignDtoSchemaCompatibilityTest` with null, zero, and negative initiative fixtures and keep
its exact schema-property/record-component parity assertion green.

- [ ] **Step 5: Implement import/export defaults**

In `EncounterSectionAdapter`:

```java
private Encounter.CombatPhase importedPhase(EncounterDto dto) {
    if (dto.combatPhase() != null) {
        return Encounter.CombatPhase.valueOf(dto.combatPhase());
    }
    return dto.status() != null && "DONE".equals(dto.status())
            || dto.activeTurnIndex() >= 0
            || dto.round() > 1
            ? Encounter.CombatPhase.RUNNING
            : Encounter.CombatPhase.SETUP;
}
```

Parenthesize the boolean expression in production code for readability. Export `encounter.getCombatPhase().name()` and pass nullable initiatives unchanged.

Apply the same inference in `CampaignService` for the legacy DTO. Change `CampaignExportDto.CombatantExportDto.initiative` to `Integer` and add optional `combatPhase` to `EncounterExportDto`.

`LegacyV1ToV2Migration` passes initiative through unchanged and sets inferred phase. It must not translate explicit zero to null; only database migration V20 handles ambiguous pre-A3 rows.

- [ ] **Step 6: Normalize legacy omissions for semantic comparison**

In `CampaignSemanticComparator`, normalize missing `combatPhase` with the same inference used by the importer. Treat omitted initiative and JSON null as equivalent, but keep null distinct from numeric zero.

Add a comparator test proving:

```java
assertThat(compare(packageWithOmittedInitiative(), packageWithNullInitiative())).isEmpty();
assertThat(compare(packageWithNullInitiative(), packageWithZeroInitiative())).isNotEmpty();
```

- [ ] **Step 7: Run package and full-shape tests**

Run:

```bash
./mvnw -Dtest=EncounterSectionAdapterTest,CampaignManifestV2SemanticValidatorTest,CampaignImportExportRoundTripTest,CampaignSemanticComparatorTest,CampaignManifestV2ContractTest,CampaignDtoSchemaCompatibilityTest test
```

Expected: all tests pass and package round trips preserve phase plus all three initiative states.

- [ ] **Step 8: Commit package fidelity**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java \
  src/main/resources/schemas/campaign-format-v2.schema.json \
  src/main/resources/schemas/campaign-format.schema.json \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterSectionAdapter.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/migration/LegacyV1ToV2Migration.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparator.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignDtoSchemaCompatibilityTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/EncounterSectionAdapterTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidatorTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticComparatorTest.java
git commit -m "feat(package): preserve initiative setup state"
```

---

### Task 4: Render and Operate the Compact Setup Panel

**Files:**

- Modify: `src/main/resources/templates/encounter/_tracker.html`
- Modify: `src/main/resources/static/css/components.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`

**Interfaces:**

- Consumes: `encounter.combatPhase`.
- Consumes: nullable `combatant.initiative`.
- Calls: `GET /api/v1/encounters/{id}/initiative-setup/combatants` while in setup.
- Calls: `PUT /api/v1/combatants/{id}/initiative`.
- Calls: `POST /api/v1/encounters/{id}/auto-roll`.
- Calls: `POST /api/v1/encounters/{id}/start-combat`.
- Dispatches existing `tracker-encounter-state` after every successful mutation.

- [ ] **Step 1: Write red template-contract tests**

Add to `EncounterTemplateContractTest`:

```java
@Test
void trackerExposesAccessibleInitiativeSetupActions() throws IOException {
    String html = Files.readString(
            Path.of("src/main/resources/templates/encounter/_tracker.html"));

    assertThat(html).contains(
            "data-initiative-setup",
            "encounter?.combatPhase === 'SETUP'",
            "Roll unset NPCs",
            "Start combat",
            "acceptUnset",
            "/start-combat",
            "/auto-roll",
            "c.initiative ?? '—'",
            "type=\"number\"",
            "aria-label");
    assertThat(html).doesNotContain("c.initiative || '—'");
}

@Test
void runningTurnControlsAreHiddenDuringSetup() throws IOException {
    String html = Files.readString(
            Path.of("src/main/resources/templates/encounter/_tracker.html"));

    assertThat(html).contains(
            "data-running-turn-controls",
            "x-show=\"encounter?.combatPhase === 'RUNNING'\"");
}
```

Add a focused assertion to `SessionCockpitTemplateContractTest` that the existing encounter runtime module still declares `data-table-safe-behavior="HIDE"`. The setup panel is inside that module, so Table-safe mode hides and inerts it as a unit; do not invent a new screen-safety attribute.

- [ ] **Step 2: Run the red contract tests**

Run:

```bash
./mvnw -Dtest=EncounterTemplateContractTest,SessionCockpitTemplateContractTest test
```

Expected: failures because setup markup and null-safe rendering are absent.

- [ ] **Step 3: Add Alpine setup state and computed values**

Inside `combatTracker` add:

```javascript
acceptUnset: false,
setupBusy: false,

get inInitiativeSetup() {
    return this.encounter?.combatPhase === 'SETUP';
},

get unsetCombatants() {
    return this.combatants.filter(c => c.initiative == null);
},

get unsetNpcCount() {
    return this.unsetCombatants.filter(c => c.kind !== 'PC').length;
},

get activeSetupCombatants() {
    return this.combatants.filter(c => {
        const wave = this.waveFor(c);
        return wave == null || wave.status === 'ACTIVE';
    });
},

get canStartCombat() {
    return this.activeSetupCombatants.length > 0
        && (this.unsetCombatants.length === 0 || this.acceptUnset);
},

get initiativeTies() {
    const counts = new Map();
    this.combatants
        .filter(c => c.initiative != null)
        .forEach(c => counts.set(c.initiative, (counts.get(c.initiative) || 0) + 1));
    return new Set([...counts.entries()]
        .filter(([, count]) => count > 1)
        .map(([initiative]) => initiative));
},

waveFor(combatant) {
    return combatant.waveId == null
        ? null
        : this.waves.find(w => w.id === combatant.waveId) || null;
},

waveLabel(combatant) {
    const wave = this.waveFor(combatant);
    return wave != null && wave.status !== 'ACTIVE'
        ? `${wave.name} · later wave`
        : '';
},
```

Reset `acceptUnset` to false whenever a different encounter loads.

- [ ] **Step 4: Add null-safe mutation methods**

Change `reloadCombatants()` to select its URL from the phase:

```javascript
const combatantUrl = this.inInitiativeSetup
    ? `/api/v1/encounters/${this._encounterId}/initiative-setup/combatants`
    : `/api/v1/encounters/${this._encounterId}/combatants`;
const resp = await this.request(combatantUrl);
```

Load waves before combatants in `loadEncounter`, because setup rows use wave metadata. Running
combat still uses the original filtered endpoint, preserving the meaning of `activeTurnIndex`.

```javascript
async saveSetupInitiative(combatantId, rawValue) {
    const initiative = rawValue === '' ? null : Number(rawValue);
    if (initiative !== null && !Number.isInteger(initiative)) return;
    await this.setInitiative(combatantId, initiative);
},

async rollUnsetNpcs() {
    if (!this._encounterId || this.unsetNpcCount === 0) return;
    this.setupBusy = true;
    await this.mutate(
        'Could not roll unset NPC initiatives. Manual values were kept.',
        `/api/v1/encounters/${this._encounterId}/auto-roll`,
        { method: 'POST' },
        async () => {
            await this.reloadCombatants();
            this.dispatchState();
        });
    this.setupBusy = false;
},

async startCombat() {
    if (!this._encounterId || !this.canStartCombat) return;
    this.setupBusy = true;
    await this.mutate(
        'Could not start combat. Initiative setup was kept.',
        `/api/v1/encounters/${this._encounterId}/start-combat`,
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ acceptUnset: this.acceptUnset }),
        },
        async response => {
            this.encounter = await response.json();
            await this.reloadCombatants();
            this.deriveActiveCombatant();
            this.dispatchTurnEvent();
            this.dispatchState();
        });
    this.setupBusy = false;
},
```

Use `try/finally` around `setupBusy` in the final implementation so a rejected request cannot leave controls disabled.

- [ ] **Step 5: Render the setup panel**

Place it between the encounter header and running turn controls:

```html
<section class="initiative-setup"
         data-initiative-setup
         x-show="encounter?.combatPhase === 'SETUP'"
         x-cloak>
    <div class="initiative-setup__header">
        <div>
            <strong>Set initiative</strong>
            <p>Enter party rolls, then roll any remaining NPCs.</p>
        </div>
        <button type="button"
                class="btn btn-ghost btn-xs"
                :disabled="setupBusy || unsetNpcCount === 0"
                @click="rollUnsetNpcs()">
            Roll unset NPCs
        </button>
    </div>

    <ol class="initiative-setup__order" aria-label="Resulting initiative order">
        <template x-for="(c, index) in combatants" :key="c.id">
            <li class="initiative-setup__row"
                :class="{ 'initiative-setup__row--unset': c.initiative == null }">
                <span class="initiative-setup__position" x-text="index + 1"></span>
                <span class="initiative-setup__name" x-text="c.name"></span>
                <span class="badge" x-text="c.kind"></span>
                <span class="badge"
                      x-show="waveLabel(c)"
                      x-text="waveLabel(c)"></span>
                <span class="badge badge-warning"
                      x-show="initiativeTies.has(c.initiative)">Tie</span>
                <input type="number"
                       inputmode="numeric"
                       :value="c.initiative ?? ''"
                       :aria-label="'Initiative for ' + c.name"
                       placeholder="Unset"
                       @change="saveSetupInitiative(c.id, $event.target.value)">
            </li>
        </template>
    </ol>

    <p class="initiative-setup__tie-help">
        Ties keep the displayed order: higher tie-breaker first, then prior order, then name.
        Accepted unset combatants act after resolved initiatives.
    </p>

    <label class="initiative-setup__accept" x-show="unsetCombatants.length > 0">
        <input type="checkbox" x-model="acceptUnset">
        Start with <span x-text="unsetCombatants.length"></span> unset
        <span>— they remain last in the displayed order</span>
    </label>

    <button type="button"
            class="btn btn-primary"
            data-action="start-combat"
            :disabled="setupBusy || !canStartCombat"
            @click="startCombat()">
        Start combat
    </button>
</section>
```

Add `data-running-turn-controls` and `x-show="encounter?.combatPhase === 'RUNNING'"` to existing Prev/Next controls. Change every initiative display to:

```html
<span class="init-badge" x-text="c.initiative ?? '—'"></span>
```

- [ ] **Step 6: Add compact responsive styling**

Add focused styles in `components.css`:

```css
.initiative-setup {
    display: grid;
    gap: var(--space-sm);
    min-height: 0;
    padding: var(--space-sm) var(--space-md);
    overflow: auto;
    border-bottom: 1px solid var(--color-border);
}

.initiative-setup__header,
.initiative-setup__row {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
}

.initiative-setup__header {
    justify-content: space-between;
}

.initiative-setup__header p,
.initiative-setup__tie-help {
    margin: 0;
    color: var(--color-text-muted);
    font-size: var(--text-xs);
}

.initiative-setup__order {
    display: grid;
    gap: 2px;
    margin: 0;
    padding: 0;
    list-style: none;
}

.initiative-setup__row {
    min-height: 34px;
    padding: 3px var(--space-xs);
    border-radius: var(--radius);
    background: var(--elevation-raised-bg);
}

.initiative-setup__row--unset {
    border-left: 2px solid var(--color-warning);
}

.initiative-setup__position {
    width: 2ch;
    color: var(--color-text-muted);
    font-variant-numeric: tabular-nums;
}

.initiative-setup__name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.initiative-setup input[type="number"] {
    width: 5.5rem;
    min-width: 0;
}

.initiative-setup__accept {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    font-size: var(--text-sm);
}

@media (max-width: 760px) {
    .initiative-setup__header {
        align-items: flex-start;
        flex-direction: column;
    }

    .initiative-setup__row {
        flex-wrap: wrap;
    }
}
```

Use the existing tokens shown above; do not introduce hard-coded fantasy colors.

- [ ] **Step 7: Run UI contract tests**

Run:

```bash
./mvnw -Dtest=EncounterTemplateContractTest,SessionCockpitTemplateContractTest test
```

Expected: all tests pass and the old truthiness expression no longer exists.

- [ ] **Step 8: Commit the setup UI**

```bash
git add src/main/resources/templates/encounter/_tracker.html \
  src/main/resources/static/css/components.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterTemplateContractTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java
git commit -m "feat(encounter): add compact initiative setup panel"
```

---

### Task 5: Prove the Complete Browser Workflow and Document It

**Files:**

- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify: `docs/dm-manual/03-session-cockpit.md`
- Modify: `docs/campaign-format-v2.md`

**Interfaces:**

- Acceptance uses only visible cockpit controls for initiative setup.
- Acceptance verifies null, zero, negative, manual NPC preservation, automatic NPC rolling, tie ordering, explicit acceptance, and the first active turn.
- Browser health remains enforced by `BrowserFailureCollector`.

- [ ] **Step 1: Replace direct first-turn setup in the browser smoke flow**

In `runsTheCompleteCockpitFlowThroughVisibleControls`, after clicking **Activate**, wait for `[data-initiative-setup]` instead of `[data-action='next-turn']`.

Make the planned encounter contain:

- one PC left unset;
- one PC manually set to `0`;
- one PC manually set to `-1`;
- one NPC manually set to `17`;
- one NPC left unset for automatic rolling.

Drive the visible controls:

```java
Locator setup = dmPage.locator("[data-initiative-setup]");
setup.waitFor();

Locator zeroInput = setup.getByLabel("Initiative for Zero Hero");
zeroInput.fill("0");
zeroInput.press("Tab");

Locator negativeInput = setup.getByLabel("Initiative for Slow Hero");
negativeInput.fill("-1");
negativeInput.press("Tab");

Locator manualNpcInput = setup.getByLabel("Initiative for Manual Goblin");
manualNpcInput.fill("17");
manualNpcInput.press("Tab");

setup.getByRole(AriaRole.BUTTON,
        new Locator.GetByRoleOptions().setName("Roll unset NPCs")).click();
```

Assert:

```java
assertThat(zeroInput.inputValue()).isEqualTo("0");
assertThat(negativeInput.inputValue()).isEqualTo("-1");
assertThat(manualNpcInput.inputValue()).isEqualTo("17");
assertThat(setup.getByLabel("Initiative for Auto Goblin").inputValue()).isNotBlank();
assertThat(setup.getByLabel("Initiative for Unset Hero").inputValue()).isBlank();
assertThat(setup.getByRole(AriaRole.BUTTON,
        new Locator.GetByRoleOptions().setName("Start combat")).isDisabled()).isTrue();
```

Check the explicit-acceptance box, click **Start combat**, and assert:

```java
dmPage.getByLabel(Pattern.compile("Start with 1 unset")).check();
setup.getByRole(AriaRole.BUTTON,
        new Locator.GetByRoleOptions().setName("Start combat")).click();
dmPage.locator("[data-running-turn-controls]").waitFor();

var started = encounterService.getById(plannedEncounterId);
assertThat(started.combatPhase()).isEqualTo("RUNNING");
assertThat(started.round()).isEqualTo(1);
assertThat(started.activeTurnIndex()).isGreaterThanOrEqualTo(0);
assertThat(encounterService.getCombatants(plannedEncounterId))
        .extracting(EncounterService.CombatantDto::initiative)
        .contains(0, -1, 17, null);
```

Then continue the existing keyboard `n` and failure/retry coverage from the now-running encounter.

- [ ] **Step 2: Update non-browser smoke setup**

Where `CoreSessionLoopSmokeTest` or threat workflow helpers call `activate(); nextTurn();`, replace that pair with:

```java
encounterService.activate(encounterId);
encounterService.startCombat(encounterId, false);
```

Resolve every test fixture initiative first, or pass `true` only in a test explicitly covering accepted unset values.

- [ ] **Step 3: Run the real browser campaign**

Run:

```bash
./mvnw -Dtest=CoreSessionLoopSmokeTest test
```

Expected: all 32+ ordered browser steps pass with no console errors, unhandled promise rejections, failed requests, or stale setup state. The exact count may increase if A3 receives a dedicated ordered method.

- [ ] **Step 4: Document the DM workflow**

Add to `docs/dm-manual/03-session-cockpit.md`:

```markdown
## Initiative setup

Activating an encounter opens Initiative setup before any turn begins.

1. Enter the party's rolled initiatives directly in the Encounter module.
2. Enter any manual NPC values you want to preserve.
3. Choose **Roll unset NPCs** to roll only the remaining non-player combatants.
4. Review the displayed order. Initiative 0 and negative values are valid; an em dash means unset.
5. Resolve every unset value, or explicitly check **Start with … unset**. Accepted unset combatants remain last in the displayed order.
6. Choose **Start combat**. Round 1 begins with the first eligible combatant active.

Ties use the displayed order: higher tie-breaker first, then the existing order, then name.
```

Add to `docs/campaign-format-v2.md`:

```markdown
### Encounter combat phase and initiative

`encounter.combatPhase` is `SETUP` or `RUNNING`. The field is optional for older v2 packages; import infers it from status, round, and active turn.

`combatant.initiative` is an optional nullable integer:

- omitted or `null`: unset;
- `0`: resolved initiative zero;
- negative integer: valid resolved initiative.

Exporters must not substitute `0` for unset initiative.
```

- [ ] **Step 5: Run focused A3 verification**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest,FlywayLegacyUpgradeTest,ThreatMigrationTest,EncounterInitiativeSetupServiceTest,EncounterServiceTest,EncounterWaveServiceTest,EncounterApiControllerTest,EncounterTemplateContractTest,SessionCockpitTemplateContractTest,PlayerSafeProjectionServiceTest,EncounterSectionAdapterTest,CampaignManifestV2SemanticValidatorTest,CampaignImportExportRoundTripTest,CampaignSemanticComparatorTest,CampaignManifestV2ContractTest,CampaignDtoSchemaCompatibilityTest,CoreSessionLoopSmokeTest test
```

Expected: every selected test passes.

- [ ] **Step 6: Commit browser acceptance and documentation**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java \
  docs/dm-manual/03-session-cockpit.md \
  docs/campaign-format-v2.md
git commit -m "test(encounter): prove initiative setup workflow"
```

---

### Task 6: Full Regression, Rollback Checkpoint, and Manual Acceptance

**Files:**

- No production files unless verification finds an A3 regression.
- Inspect: all files changed in Tasks 1–5.

**Interfaces:**

- Produces: a green A3 release gate.
- Produces: a clean handoff to B1 Cockpit Layout Foundation.

- [ ] **Step 1: Run a clean full build**

```bash
./mvnw clean test
```

Expected: all tests pass after recompiling production and test sources from scratch.

- [ ] **Step 2: Check patch hygiene and tracked artifacts**

```bash
git diff --check
git status --short
git ls-files artifacts
```

Expected: no whitespace errors, only intended source/docs changes, and no tracked private artifacts.

- [ ] **Step 3: Perform the manual 1366×768 acceptance checkpoint**

Use a synthetic encounter with two PCs and two NPCs:

1. Activate the encounter and confirm the tracker says setup, round 0, with no active turn.
2. Confirm every unset initiative is visibly marked **Unset** or `—`.
3. Enter `0` for one PC and `-2` for the other; reload and confirm both survive.
4. Enter `15` for one NPC.
5. Choose **Roll unset NPCs** and confirm the manual `15` is unchanged.
6. Confirm the final order is visible without opening an Edit/Admin surface.
7. Confirm **Start combat** is disabled while one value remains unset.
8. Check explicit acceptance and start combat.
9. Confirm round 1 begins on the first displayed eligible combatant.
10. Advance and reverse turns; confirm no setup control reappears and no active identity changes unexpectedly.
11. Switch to Table-safe mode and confirm setup mechanics are hidden and removed from keyboard focus.
12. Reload the cockpit and confirm phase, order, values, and active turn remain correct.

- [ ] **Step 4: Perform the 1920×1080 and keyboard checkpoint**

Confirm:

- each initiative input has an accessible name;
- Tab order follows displayed initiative order;
- Space toggles explicit acceptance;
- Enter or Space operates Roll unset NPCs and Start combat;
- focus remains visible;
- setup rows do not overflow the Encounter module;
- `prefers-reduced-motion: reduce` disables reorder animation without changing order.

- [ ] **Step 5: Record rollback instructions in the handoff**

Flyway migrations are forward-only. Before deployment, rely on the application backup created at startup. To reverse A3 in a development copy:

```sql
update combatant set initiative = 0 where initiative is null;
alter table combatant alter column initiative set not null;
alter table encounter drop constraint ck_encounter_combat_phase;
alter table encounter drop column combat_phase;
```

Then restore the pre-A3 application binary. This rollback intentionally loses the distinction between unset and numeric zero, so production rollback should restore the pre-migration backup instead of applying this SQL in place.

- [ ] **Step 6: Confirm the dependency handoff**

Review A3 against corrective-spec section 6.2:

- unset is distinct from zero;
- manual entry works for every combatant;
- unset NPC rolling uses authoritative modifiers;
- manual values survive rolling;
- zero and negative values render;
- combat start is gated or explicitly accepted;
- tie behavior is explained;
- final order is visible before the first turn;
- the complete cockpit flow passes.

Expected: all items are satisfied. The next dependency-ordered plan is **B1 — Cockpit layout foundation**.

## Final Acceptance Gate

A3 is complete only when all of the following are true:

- The database, JPA model, runtime DTO, player projection, undo log, legacy export, and package v2 all preserve null, zero, and negative initiative distinctly.
- An active encounter in `SETUP` cannot advance or reverse turns through service or HTTP APIs.
- Automatic rolling never overwrites a PC or a resolved NPC initiative.
- Start combat requires either zero unset initiatives or explicit acceptance.
- The displayed pre-combat order is exactly the order used to select the first active combatant.
- Reordering a running encounter preserves active combatant identity.
- The real browser campaign completes without hidden direct-service setup shortcuts for the principal encounter.
- `./mvnw clean test` and `git diff --check` pass.
