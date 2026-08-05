# Encounter, Map, and Token Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make maps reusable terrain, make encounters the single owner of combatants and their placements, and give the DM one predictable preparation-to-session workflow without duplicated HP/death state or mismatched encounter maps.

**Architecture:** Add an `EncounterTokenPlacement` entity that stores only encounter-specific map geometry and visual styling, while `Combatant` remains authoritative for identity, HP, conditions, visibility, and defeat. Keep `Token` as a map-owned, non-combat marker for props and temporary annotations. Build one unified runtime token projection for the DM and player table, migrate existing combatant-linked tokens into placements, and activate encounters through a transactional session command that switches the workspace map atomically.

**Tech Stack:** Spring Boot 4.1, Java 26, Spring Data JPA, H2/Flyway, Thymeleaf, Alpine.js, Konva, JUnit 5, AssertJ, MockMvc, Playwright.

## Global Constraints

- Maps are reusable assets and must not retain encounter combatants after an encounter ends.
- `Combatant` is the only source of truth for combat HP, maximum HP, temporary HP, conditions, hidden state, bloodied state, and defeated state.
- `EncounterTokenPlacement` stores position, footprint, color, icon, and map association only.
- Map-owned markers must not participate in initiative or expose editable combat HP.
- Activating an encounter and selecting its linked workspace map must succeed or fail as one transaction.
- Replacing an active encounter must require an explicit `SUSPEND` or `END` decision.
- Suspending and resuming an encounter must preserve round, phase, initiative, combat state, and placements.
- Player projection must exclude hidden combatants and hidden map markers.
- Existing campaign databases and package-v2 imports must migrate without losing map positions or combatant links.
- Existing map background, grid, pin, movement, and player-presentation behavior must remain unchanged.
- No browser-native `prompt()`, `confirm()`, or `alert()` may be introduced.
- Every mutating UI action must provide busy, success, validation, and retry/error feedback.

---

## File and Interface Map

### New files

- `src/main/resources/db/migration/V26__encounter_token_placements.sql` — creates and backfills encounter placements and adds the suspended encounter status constraint.
- `src/main/resources/db/migration/V27__remove_legacy_combat_token_state.sql` — removes the migrated combatant-token foreign key and duplicated token HP/death columns after application code switches to placements.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterTokenPlacement.java` — encounter placement persistence.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterTokenPlacementRepository.java` — placement lookup and deletion.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementService.java` — placement commands and validation.
- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/RuntimeTokenProjectionService.java` — unified marker/combatant rendering DTOs.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterService.java` — atomic activate/suspend/end/resume command boundary.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/ActiveEncounterReplacementRequiredException.java` — structured 409 conflict for an already-running encounter.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/EncounterNotReadyException.java` — structured 409 conflict carrying blocking readiness issues.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterActivated.java` — after-commit refresh event.
- `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterTokenPlacementPersistenceTest.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementServiceTest.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/RuntimeTokenProjectionServiceTest.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterServiceTest.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterPlacementPackageTest.java`
- `src/test/java/dev/hendrikhoemberg/dmhelper/config/FlywayMigrationTest.java`

### Existing files with changed responsibilities

- `Combatant.java` stops referencing `Token`; it owns combat state and optionally owns one placement.
- `Token.java` becomes a map marker and loses combat HP/death state while retaining optional party/statblock metadata for imported and authored markers.
- `EncounterService.java` manages roster/combat only; token creation and map switching move to focused services.
- `TokenService.java` manages non-combat map markers only.
- `TokenApiController.java` keeps marker endpoints and exposes no HP/dead endpoints.
- `EncounterApiController.java` exposes placement/readiness commands.
- `SessionApiController.java` exposes the atomic encounter transition command.
- `battle-map.js` renders a unified runtime projection and dispatches movement/editing by `source`.
- `_map-module.html` separates encounter participants from map-authoring tools.
- `setup.html` presents roster placement readiness and a map preparation surface.
- `_encounter-rail.html` has one primary run/resume action and explicit replacement choices.
- `PlayerSafeProjectionService.java` projects both safe map markers and active-encounter placements.
- Package-v2 model, adapter, validator, and JSON schema serialize placements on combatants instead of token references.

### Stable public interfaces introduced by this plan

```java
public record PlacementDto(
        UUID id, UUID encounterId, UUID combatantId, UUID mapId,
        int positionX, int positionY, int sizeCols, int sizeRows,
        String color, String icon) {}

public record PlacementUpsertRequest(
        int positionX, int positionY, int sizeCols, int sizeRows,
        String color, String icon) {}

public record PlacementMoveRequest(int positionX, int positionY) {}

public record ChangeEncounterMapRequest(UUID mapId) {}

public enum RuntimeTokenSource { COMBATANT, MARKER }

public record RuntimeTokenDto(
        UUID id, RuntimeTokenSource source, UUID combatantId,
        String name, String kind, int positionX, int positionY,
        int sizeCols, int sizeRows, String color, String icon,
        boolean hidden, Integer currentHp, Integer maxHp,
        boolean bloodied, boolean defeated, List<String> conditions) {}

public enum ActiveEncounterDisposition { SUSPEND, END }

public record ActivateEncounterRequest(
        ActiveEncounterDisposition activeEncounterDisposition) {}

public record EncounterActivationDto(
        UUID encounterId, UUID workspaceMapId, String status,
        UUID replacedEncounterId, String replacedEncounterStatus) {}
```

---

### Task 1: Persist encounter-owned placements and suspended encounters

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterTokenPlacement.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterTokenPlacementRepository.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterTokenPlacementPersistenceTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Encounter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java`

**Interfaces:**
- Produces: `EncounterTokenPlacement`, `EncounterTokenPlacementRepository`, and `Encounter.Status.SUSPENDED`.
- Consumes: existing `Encounter`, `Combatant`, and `GameMap` entities.

- [ ] **Step 1: Write the failing placement persistence tests**

```java
@DataJpaTest
class EncounterTokenPlacementPersistenceTest {
    @Autowired EntityManager entityManager;
    @Autowired EncounterTokenPlacementRepository placements;

    @Test
    void oneCombatantHasAtMostOnePlacement() {
        Combatant combatant = TestEncounterGraph.persistCombatant(entityManager);
        GameMap map = combatant.getEncounter().getMap();
        placements.saveAndFlush(placement(combatant, map, 48, 96));

        assertThatThrownBy(() ->
                placements.saveAndFlush(placement(combatant, map, 96, 96)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingEncounterCascadesItsPlacements() {
        Combatant combatant = TestEncounterGraph.persistCombatant(entityManager);
        placements.saveAndFlush(placement(combatant, combatant.getEncounter().getMap(), 48, 96));
        UUID encounterId = combatant.getEncounter().getId();

        entityManager.remove(combatant.getEncounter());
        entityManager.flush();

        assertThat(placements.findByEncounterIdOrderByCombatantSortOrderAsc(encounterId)).isEmpty();
    }
}
```

- [ ] **Step 2: Run the persistence test and verify it fails**

Run:

```bash
./mvnw -Dtest=EncounterTokenPlacementPersistenceTest test
```

Expected: compilation fails because the placement entity and repository do not exist.

- [ ] **Step 3: Add the placement entity and repository**

```java
@Entity
@Table(name = "encounter_token_placement",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_encounter_placement_combatant",
               columnNames = "combatant_id"),
       indexes = {
           @Index(name = "idx_encounter_placement_encounter", columnList = "encounter_id"),
           @Index(name = "idx_encounter_placement_map", columnList = "map_id")
       })
public class EncounterTokenPlacement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    @org.hibernate.annotations.OnDelete(
            action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Encounter encounter;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "combatant_id", nullable = false)
    @org.hibernate.annotations.OnDelete(
            action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Combatant combatant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    private GameMap map;

    private int positionX;
    private int positionY;
    private int sizeCols = 1;
    private int sizeRows = 1;

    @Column(nullable = false, length = 7)
    private String color = "#7b68ee";

    @Column(length = 100)
    private String icon;
}
```

Add ordinary getters and setters for `id`, `encounter`, `combatant`, `map`,
`positionX`, `positionY`, `sizeCols`, `sizeRows`, `color`, and `icon`, following
the style already used by `Combatant`.

```java
public interface EncounterTokenPlacementRepository
        extends JpaRepository<EncounterTokenPlacement, UUID> {
    Optional<EncounterTokenPlacement> findByCombatantId(UUID combatantId);
    List<EncounterTokenPlacement> findByEncounterIdOrderByCombatantSortOrderAsc(UUID encounterId);
    List<EncounterTokenPlacement> findByMapIdAndEncounterIdOrderByCombatantSortOrderAsc(
            UUID mapId, UUID encounterId);
    long countByEncounterId(UUID encounterId);
    void deleteByCombatantId(UUID combatantId);
}
```

Add `SUSPENDED` to `Encounter.Status`. Add a read-only `@OneToOne(mappedBy = "combatant")` placement association to `Combatant`; do not add position or visual fields to `Combatant`.

- [ ] **Step 4: Run the persistence tests**

Run:

```bash
./mvnw -Dtest=EncounterTokenPlacementPersistenceTest,EncounterWavePersistenceTest test
```

Expected: all tests pass.

- [ ] **Step 5: Commit the persistence model**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/data
git commit -m "feat: add encounter-owned token placements"
```

---

### Task 2: Migrate existing combatant-linked tokens without data loss

**Files:**
- Create: `src/main/resources/db/migration/V26__encounter_token_placements.sql`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/config/FlywayMigrationTest.java`

**Interfaces:**
- Consumes: `combatant.token_id` and the geometry/state columns on `token`.
- Produces: one placement per linked combatant while retaining the legacy token link for the compatibility phase.

- [ ] **Step 1: Add a failing migration test using a V25-shaped database**

Create a test fixture that migrates only through V25, inserts:

```sql
insert into token (
    id, map_id, name, kind, positionx, positiony, size_cols, size_rows,
    color, hidden, current_hp, max_hp, dead
) values (
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'Goblin', 'MONSTER', 144, 96, 1, 1,
    '#55aa55', false, 3, 7, false
);
update combatant
set token_id = '10000000-0000-0000-0000-000000000001'
where id = '30000000-0000-0000-0000-000000000001';
```

Migrate to latest and assert:

```java
assertThat(jdbc.queryForObject("""
        select count(*) from encounter_token_placement
        where combatant_id = '30000000-0000-0000-0000-000000000001'
          and position_x = 144 and position_y = 96
        """, Integer.class)).isEqualTo(1);
assertThat(columnExists("COMBATANT", "TOKEN_ID")).isTrue();
assertThat(columnExists("TOKEN", "CURRENT_HP")).isTrue();
```

- [ ] **Step 2: Run the migration test and verify it fails**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest test
```

Expected: the placement table does not exist.

- [ ] **Step 3: Create V26 to add and backfill placements**

```sql
create table encounter_token_placement (
    id uuid not null primary key,
    encounter_id uuid not null,
    combatant_id uuid not null,
    map_id uuid not null,
    position_x integer not null,
    position_y integer not null,
    size_cols integer not null,
    size_rows integer not null,
    color varchar(7) not null,
    icon varchar(100),
    constraint uq_encounter_placement_combatant unique (combatant_id),
    constraint fk_encounter_placement_encounter foreign key (encounter_id)
        references encounter(id) on delete cascade,
    constraint fk_encounter_placement_combatant foreign key (combatant_id)
        references combatant(id) on delete cascade,
    constraint fk_encounter_placement_map foreign key (map_id)
        references game_map(id) on delete cascade
);

create index idx_encounter_placement_encounter
    on encounter_token_placement(encounter_id);
create index idx_encounter_placement_map
    on encounter_token_placement(map_id);

insert into encounter_token_placement (
    id, encounter_id, combatant_id, map_id,
    position_x, position_y, size_cols, size_rows, color, icon
)
select random_uuid(), c.encounter_id, c.id, t.map_id,
       t.positionx, t.positiony, t.size_cols, t.size_rows, t.color, t.icon
from combatant c
join token t on t.id = c.token_id
where c.token_id is not null;
```

The application enum supplies `SUSPENDED`; if the installed H2 version materializes the original `status` enum as a closed database enum, V26 must alter it to `varchar(16)` before any suspended status is persisted:

```sql
alter table encounter alter column status varchar(16);
alter table encounter add constraint ck_encounter_status
    check (regexp_like(status, '^(PLANNED|ACTIVE|SUSPENDED|DONE)$'));
```

- [ ] **Step 4: Run migration and JPA persistence suites**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest,EncounterTokenPlacementPersistenceTest test
```

Expected: migration and placement persistence assertions pass.

- [ ] **Step 5: Commit the migration**

```bash
git add src/main/resources/db/migration \
  src/test/java/dev/hendrikhoemberg/dmhelper/config/FlywayMigrationTest.java
git commit -m "feat: backfill encounter token placements"
```

---

### Task 3: Implement placement commands, validation, and encounter readiness

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterPlacementServiceTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiControllerTest.java`

**Interfaces:**
- Consumes: placement repository and encounter/combatant/map repositories.
- Produces: placement CRUD, automatic placement, party placement, and `EncounterReadinessDto`.

- [ ] **Step 1: Write failing service tests for placement invariants**

Cover these exact cases. The first test establishes the ownership guard:

```java
@Test
void upsertRejectsCombatantFromAnotherEncounter() {
    Encounter first = fixture.encounterWithMap("First");
    Encounter second = fixture.encounterWithMap("Second");
    Combatant foreignCombatant = fixture.combatant(second, "Goblin");

    assertThatThrownBy(() -> service.upsert(
            first.getId(), foreignCombatant.getId(),
            new PlacementUpsertRequest(48, 48, 1, 1, "#55aa55", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Combatant does not belong to encounter");
}

@Test
void upsertClampsFootprintInsideMapBounds() {
    Encounter encounter = fixture.encounterWithMap("Room", 10, 8, 48);
    Combatant ogre = fixture.combatant(encounter, "Ogre");

    PlacementDto result = service.upsert(
            encounter.getId(), ogre.getId(),
            new PlacementUpsertRequest(9 * 48, 7 * 48, 2, 2, "#55aa55", null));

    assertThat(result.positionX()).isEqualTo(8 * 48);
    assertThat(result.positionY()).isEqualTo(6 * 48);
}

@Test
void movingPlacementDoesNotChangeCombatState() {
    Encounter encounter = fixture.encounterWithMap("Room");
    Combatant goblin = fixture.combatant(encounter, "Goblin");
    goblin.setCurrentHp(3);
    goblin.setDefeated(true);
    combatants.save(goblin);
    service.upsert(encounter.getId(), goblin.getId(),
            new PlacementUpsertRequest(0, 0, 1, 1, "#55aa55", null));

    service.move(encounter.getId(), goblin.getId(), 96, 144);

    Combatant unchanged = combatants.findById(goblin.getId()).orElseThrow();
    assertThat(unchanged.getCurrentHp()).isEqualTo(3);
    assertThat(unchanged.isDefeated()).isTrue();
}
```

Add equally focused tests asserting:

- a combatant cannot be placed on a map different from its encounter map;
- removing a placement leaves its combatant in the roster;
- calling `placeMissingParty` twice yields one combatant and placement per active party member;
- an encounter without a map reports `MISSING_MAP`;
- a roster with one missing placement reports `UNPLACED_COMBATANTS` and the exact placed/unplaced counts.

Define readiness as:

```java
public record EncounterReadinessDto(
        UUID encounterId,
        boolean canRun,
        UUID mapId,
        int combatantCount,
        int placedCombatantCount,
        int unplacedCombatantCount,
        List<ReadinessIssueDto> issues) {}

public record ReadinessIssueDto(String code, String message, String severity) {}
```

Codes are stable API values:

```text
MISSING_MAP
EMPTY_ROSTER
UNPLACED_COMBATANTS
PLACEMENT_MAP_MISMATCH
```

`canRun` is false only when at least one issue has severity `ERROR`.
`MISSING_MAP` and `PLACEMENT_MAP_MISMATCH` are errors. `EMPTY_ROSTER` and
`UNPLACED_COMBATANTS` are warnings so the DM may still run an improvised
or partially hidden encounter.

- [ ] **Step 2: Run the service tests and verify they fail**

Run:

```bash
./mvnw -Dtest=EncounterPlacementServiceTest test
```

Expected: compilation fails because `EncounterPlacementService` does not exist.

- [ ] **Step 3: Implement focused placement operations**

```java
@Service
@Transactional
public class EncounterPlacementService {
    public PlacementDto upsert(
            UUID encounterId, UUID combatantId, PlacementUpsertRequest request);
    public PlacementDto move(
            UUID encounterId, UUID combatantId, int positionX, int positionY);
    public void remove(UUID encounterId, UUID combatantId);
    public List<PlacementDto> list(UUID encounterId);
    public List<PlacementDto> autoPlaceUnplaced(UUID encounterId);
    public List<PlacementDto> placeUnplacedPartyCombatants(UUID encounterId);
    public EncounterReadinessDto readiness(UUID encounterId);
    public EncounterReadinessDto changeMapAndResetPlacements(
            UUID encounterId, UUID mapId);

    private static String defaultColor(String kind) {
        return switch (kind == null ? "NPC" : kind) {
            case "PC" -> "#4a9eff";
            case "MONSTER" -> "#d95c5c";
            case "OBJECT" -> "#8a8a8a";
            default -> "#7b68ee";
        };
    }
}
```

Avoid a service dependency cycle: `EncounterPlacementService` must depend on
repositories, never on `EncounterService`. Add this orchestration method to
`EncounterService`:

```java
public List<PlacementDto> placeMissingParty(UUID encounterId) {
    Encounter encounter = findEntityById(encounterId);
    prefillFromParty(encounterId, encounter.getCampaign().getId());
    return placementService.placeUnplacedPartyCombatants(encounterId);
}
```

Use pixel positions consistently with the existing battle map. Clamp with:

```java
int maxX = Math.max(0, (map.getGridWidth() - sizeCols) * map.getCellSizePx());
int maxY = Math.max(0, (map.getGridHeight() - sizeRows) * map.getCellSizePx());
int x = Math.max(0, Math.min(request.positionX(), maxX));
int y = Math.max(0, Math.min(request.positionY(), maxY));
```

`autoPlaceUnplaced` must use explicit `startX/startY`, then `placementRegionKey`, then the first free grid cells. It must never overwrite an existing placement.

`EncounterService.addFromLibrary` must call `upsert` when the request contains
both `startX` and `startY`. This makes the existing library-add request the
single command used by the live map to create a combatant and placement:

```java
if (request.startX() != null && request.startY() != null) {
    placementService.upsert(encounterId, saved.getId(),
            new PlacementUpsertRequest(
                    request.startX(), request.startY(), 1, 1,
                    defaultColor(saved.getKind()), null));
}
```

- [ ] **Step 4: Remove token creation from encounter activation**

Delete `placeTokensForWave`. Replace it with `EncounterPlacementService.autoPlaceUnplaced(encounterId)` when a wave becomes active. Update `CombatantDto`:

```java
public record CombatantDto(
        UUID id, UUID encounterId, String name, Integer initiative,
        int sortOrder, int currentHp, int maxHp, int tempHp,
        String kind, String groupId, boolean groupLeader,
        UUID placementId, UUID statBlockId, UUID partyMemberId,
        boolean defeated, boolean hidden, boolean bloodied,
        List<ConditionStateDto> conditions,
        String concentratingOn, boolean concentrationCheckPending,
        int legendaryActionsUsed, int legendaryActionsMax,
        int legendaryResistancesUsed, int legendaryResistancesMax,
        String notes, UUID waveId, Integer startX, Integer startY,
        String placementRegionKey, ThreatKind threatKind,
        UUID threatId, ThreatCardView threatCard) {}
```

- [ ] **Step 5: Add placement and readiness endpoints**

```java
@GetMapping("/encounters/{id}/placements")
List<PlacementDto> listPlacements(@PathVariable UUID id)

@PutMapping("/encounters/{id}/combatants/{combatantId}/placement")
PlacementDto upsertPlacement(
        @PathVariable UUID id,
        @PathVariable UUID combatantId,
        @RequestBody PlacementUpsertRequest request)

@PatchMapping("/encounters/{id}/combatants/{combatantId}/placement/move")
PlacementDto movePlacement(
        @PathVariable UUID id,
        @PathVariable UUID combatantId,
        @RequestBody PlacementMoveRequest request)

@DeleteMapping("/encounters/{id}/combatants/{combatantId}/placement")
@PutMapping("/encounters/{id}/map")
EncounterReadinessDto changeMap(
        @PathVariable UUID id,
        @RequestBody ChangeEncounterMapRequest request)
```

Use these complete method bodies for the response wrappers:

```java
@DeleteMapping("/encounters/{id}/combatants/{combatantId}/placement")
ResponseEntity<Void> removePlacement(
        @PathVariable UUID id, @PathVariable UUID combatantId) {
    placements.remove(id, combatantId);
    return ResponseEntity.noContent().build();
}

@PostMapping("/encounters/{id}/placements/auto")
List<PlacementDto> autoPlace(@PathVariable UUID id) {
    return placements.autoPlaceUnplaced(id);
}

@PostMapping("/encounters/{id}/placements/party")
List<PlacementDto> placeMissingParty(@PathVariable UUID id) {
    return encounterService.placeMissingParty(id);
}

@GetMapping("/encounters/{id}/readiness")
EncounterReadinessDto readiness(@PathVariable UUID id) {
    return placements.readiness(id);
}

@PutMapping("/encounters/{id}/map")
EncounterReadinessDto changeMap(
        @PathVariable UUID id,
        @RequestBody ChangeEncounterMapRequest request) {
    return placements.changeMapAndResetPlacements(id, request.mapId());
}
```

- [ ] **Step 6: Run service and controller tests**

Run:

```bash
./mvnw -Dtest=EncounterPlacementServiceTest,EncounterServiceTest,EncounterApiControllerTest test
```

Expected: all tests pass.

- [ ] **Step 7: Commit placement behavior**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter
git commit -m "feat: add encounter placement and readiness commands"
```

---

### Task 4: Restrict map tokens to non-combat markers

**Files:**
- Create: `src/main/resources/db/migration/V27__remove_legacy_combat_token_state.sql`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/Token.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/TokenService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/TokenApiController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/TokenServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/TokenApiControllerTest.java`

**Interfaces:**
- Consumes: simplified `Token` entity.
- Produces: `MapMarkerDto` and marker-only create/move/update/delete/duplicate endpoints.

- [ ] **Step 1: Replace combat-token tests with marker contract tests**

```java
@Test void createdMarkerHasNoCombatHpOrDefeatedFields() {
    MapMarkerDto marker = service.create(mapId,
            new MapMarkerRequest("Fallen pillar", "OBJECT", 48, 96, 2, 1,
                    "#777777", false, null, null));
    assertThat(marker.name()).isEqualTo("Fallen pillar");
}

@Test void markerCannotBeAddedToInitiativeByTokenId() {
    assertThatThrownBy(() -> encounterService.addCombatant(encounterId,
            new CombatantCreateRequest(null, 0, null, markerId, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
}
```

Add a MockMvc contract asserting `PATCH /api/v1/tokens/{id}/hp` and
`PATCH /api/v1/tokens/{id}/dead` return 405.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run:

```bash
./mvnw -Dtest=TokenServiceTest,TokenApiControllerTest test
```

Expected: old DTOs and endpoints still expose combat state.

- [ ] **Step 3: Introduce marker-only DTOs and operations**

```java
public record MapMarkerDto(
        UUID id, String name, String kind,
        int positionX, int positionY, int sizeCols, int sizeRows,
        String color, boolean hidden,
        UUID statBlockId, UUID partyMemberId,
        String notes, String icon) {}

public record MapMarkerRequest(
        String name, String kind,
        int positionX, int positionY, int sizeCols, int sizeRows,
        String color, boolean hidden,
        UUID statBlockId, UUID partyMemberId) {}
```

Rename method symbols from token language to marker language where they are internal. Preserve `/api/v1/maps/{mapId}/tokens` temporarily as an HTTP compatibility alias returning marker DTOs, and add canonical `/api/v1/maps/{mapId}/markers`. Remove the HP/dead endpoints completely.

- [ ] **Step 4: Remove legacy database links and duplicated combat state**

Create V27. Delete only tokens referenced through the legacy combatant link
because V26 copied each of those links into an encounter placement. Preserve
every unlinked token as a map marker.

```sql
alter table combatant drop constraint if exists FKsi7tk9ad9t0cjv96mm0u72dyh;

delete from token
where id in (
    select distinct token_id from combatant where token_id is not null
);

alter table combatant drop column token_id;

alter table token drop column current_hp;
alter table token drop column max_hp;
alter table token drop column dead;
```

Remove `Combatant.token`, `Token.currentHp`, `Token.maxHp`, and `Token.dead`
from the JPA entities. Keep token name, kind, geometry, color, hidden, notes,
icon, statblock, and party-member metadata so old unlinked tokens remain
useful as markers.

Extend `FlywayMigrationTest` to assert:

```java
assertThat(columnExists("COMBATANT", "TOKEN_ID")).isFalse();
assertThat(columnExists("TOKEN", "CURRENT_HP")).isFalse();
assertThat(columnExists("TOKEN", "MAX_HP")).isFalse();
assertThat(columnExists("TOKEN", "DEAD")).isFalse();
```

- [ ] **Step 5: Remove map-level party placement and map-to-encounter prefill**

Delete:

```text
POST /api/v1/maps/{mapId}/tokens/add-party
POST /api/v1/encounters/{id}/prefill/map
TokenService.addPartyToMap(UUID)
EncounterService.prefillFromMap(UUID, UUID)
CombatantCreateRequest.tokenId
```

The replacements are:

```text
POST /api/v1/encounters/{id}/placements/party
POST /api/v1/encounters/{id}/combatants/from-library
PUT  /api/v1/encounters/{id}/combatants/{combatantId}/placement
```

- [ ] **Step 6: Run marker, migration, and encounter regression tests**

Run:

```bash
./mvnw -Dtest=FlywayMigrationTest,TokenServiceTest,TokenApiControllerTest,EncounterServiceTest,EncounterApiControllerTest test
```

Expected: all tests pass and no service API accepts a marker as a combatant.

- [ ] **Step 7: Commit marker separation**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter \
  src/main/resources/db/migration/V27__remove_legacy_combat_token_state.sql \
  src/test/java/dev/hendrikhoemberg/dmhelper/config/FlywayMigrationTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/gamemap \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter
git commit -m "refactor: separate map markers from combatants"
```

---

### Task 5: Build one authoritative runtime token projection

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/RuntimeTokenProjectionService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/RuntimeTokenProjectionServiceTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java`

**Interfaces:**
- Consumes: map markers, encounter placements, and combatants.
- Produces: `RuntimeTokenDto` and `GET /api/v1/maps/{mapId}/runtime-tokens`.

- [ ] **Step 1: Write failing projection tests**

```java
@Test
void placementUsesCombatantHpDefeatAndVisibility() {
    Encounter encounter = fixture.activeEncounterWithMap();
    Combatant combatant = fixture.placedCombatant(encounter, 96, 144);
    combatant.setCurrentHp(3);
    combatant.setMaxHp(10);
    combatant.setDefeated(true);
    combatant.setHidden(true);
    combatants.save(combatant);

    RuntimeTokenDto token = service.project(
            encounter.getMap().getId(), encounter.getId()).stream()
            .filter(item -> combatant.getId().equals(item.combatantId()))
            .findFirst().orElseThrow();

    assertThat(token.source()).isEqualTo(RuntimeTokenSource.COMBATANT);
    assertThat(token.currentHp()).isEqualTo(3);
    assertThat(token.maxHp()).isEqualTo(10);
    assertThat(token.bloodied()).isTrue();
    assertThat(token.defeated()).isTrue();
    assertThat(token.hidden()).isTrue();
}

@Test
void inactiveEncounterPlacementsAreNotIncludedByDefault() {
    Encounter planned = fixture.plannedEncounterWithMap();
    fixture.placedCombatant(planned, 96, 144);

    assertThat(service.project(planned.getMap().getId(), null))
            .noneMatch(token -> token.source() == RuntimeTokenSource.COMBATANT);
}
```

Also assert that a marker projects with `source == MARKER`, null
`combatantId/currentHp/maxHp`, and that an explicitly requested preparation
encounter is accepted only when it belongs to the requested map.

The default selection rule is:

```text
explicit encounterId on the same map
otherwise the campaign's ACTIVE encounter when linked to this map
otherwise no encounter overlay
```

- [ ] **Step 2: Run the projection tests and verify they fail**

Run:

```bash
./mvnw -Dtest=RuntimeTokenProjectionServiceTest test
```

Expected: compilation fails because the projection service does not exist.

- [ ] **Step 3: Implement unified projection**

```java
@Service
@Transactional(readOnly = true)
public class RuntimeTokenProjectionService {
    public List<RuntimeTokenDto> project(UUID mapId, UUID encounterId) {
        List<RuntimeTokenDto> markers = markerRepository
                .findByMapIdOrderByNameAsc(mapId).stream()
                .map(this::markerDto)
                .toList();
        List<RuntimeTokenDto> participants =
                resolveEncounter(mapId, encounterId)
                        .map(this::combatantDtos)
                        .orElseGet(List::of);
        return Stream.concat(markers.stream(), participants.stream()).toList();
    }
}
```

For combatant placements derive:

```java
boolean bloodied = combatant.getMaxHp() > 0
        && combatant.getCurrentHp() <= combatant.getMaxHp() / 2;
boolean hidden = combatant.isHidden();
boolean defeated = combatant.isDefeated();
```

- [ ] **Step 4: Add the runtime endpoint**

```java
@GetMapping("/maps/{mapId}/runtime-tokens")
List<RuntimeTokenDto> runtimeTokens(
        @PathVariable UUID mapId,
        @RequestParam(required = false) UUID encounterId) {
    return runtimeTokens.project(mapId, encounterId);
}
```

- [ ] **Step 5: Run projection and API tests**

Run:

```bash
./mvnw -Dtest=RuntimeTokenProjectionServiceTest,GameMapApiControllerTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit the projection**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap \
  src/test/java/dev/hendrikhoemberg/dmhelper/gamemap
git commit -m "feat: project map markers and encounter participants"
```

---

### Task 6: Make encounter transitions atomic and non-destructive

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/ActiveEncounterReplacementRequiredException.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/EncounterNotReadyException.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterActivated.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionEncounterServiceTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiControllerTest.java`

**Interfaces:**
- Consumes: encounter repository, session repository, placement readiness, and table presentation.
- Produces: atomic activation, suspension, resumption, and ending.

- [ ] **Step 1: Write failing transaction tests**

```java
@Test
void replacingActiveEncounterWithoutDispositionIsRejected() {
    Encounter active = fixture.activeEncounter("Ambush", 3);
    Encounter requested = fixture.plannedEncounterWithMap("Boss");

    assertThatThrownBy(() ->
            service.activate(campaignId, requested.getId(), null))
            .isInstanceOf(ActiveEncounterReplacementRequiredException.class)
            .extracting("activeEncounterId")
            .isEqualTo(active.getId());

    assertThat(encounters.findById(active.getId()).orElseThrow().getStatus())
            .isEqualTo(Encounter.Status.ACTIVE);
    assertThat(encounters.findById(requested.getId()).orElseThrow().getStatus())
            .isEqualTo(Encounter.Status.PLANNED);
}

@Test
void suspendAndResumePreserveCombatStateAndPlacements() {
    Encounter active = fixture.activeEncounter("Ambush", 3);
    active.setCombatPhase(Encounter.CombatPhase.RUNNING);
    active.setActiveTurnIndex(2);
    Combatant goblin = fixture.placedCombatant(active, 96, 144);
    goblin.setCurrentHp(3);
    combatants.save(goblin);
    Encounter requested = fixture.plannedEncounterWithMap("Boss");

    service.activate(campaignId, requested.getId(),
            ActiveEncounterDisposition.SUSPEND);
    service.activate(campaignId, active.getId(),
            ActiveEncounterDisposition.SUSPEND);

    Encounter resumed = encounters.findById(active.getId()).orElseThrow();
    assertThat(resumed.getStatus()).isEqualTo(Encounter.Status.ACTIVE);
    assertThat(resumed.getRound()).isEqualTo(3);
    assertThat(resumed.getActiveTurnIndex()).isEqualTo(2);
    assertThat(combatants.findById(goblin.getId()).orElseThrow().getCurrentHp())
            .isEqualTo(3);
    assertThat(placements.findByCombatantId(goblin.getId())).isPresent();
}
```

Add tests proving fresh activation selects the encounter map, `END` keeps
placements for historical replay, a missing map produces a conflict with
`MISSING_MAP`, and any workspace-map persistence exception rolls back every
encounter status change.

- [ ] **Step 2: Run the transaction tests and verify they fail**

Run:

```bash
./mvnw -Dtest=SessionEncounterServiceTest test
```

Expected: compilation fails because the session encounter service does not exist.

- [ ] **Step 3: Extract lifecycle methods that do not implicitly finish another encounter**

```java
Encounter activateFresh(UUID encounterId); // resets only PLANNED encounters
Encounter resume(UUID encounterId);        // SUSPENDED -> ACTIVE, preserves combat state
Encounter suspend(UUID encounterId);       // ACTIVE -> SUSPENDED
Encounter endEncounter(UUID encounterId);  // ACTIVE/SUSPENDED -> DONE
```

`activateFresh` must no longer query for and mutate a different active encounter.

- [ ] **Step 4: Add structured conflict types and the refresh event**

```java
public final class ActiveEncounterReplacementRequiredException
        extends RuntimeException {
    private final UUID activeEncounterId;
    private final String activeEncounterName;

    public ActiveEncounterReplacementRequiredException(
            UUID activeEncounterId, String activeEncounterName) {
        super("Choose whether to suspend or end the active encounter");
        this.activeEncounterId = activeEncounterId;
        this.activeEncounterName = activeEncounterName;
    }

    public UUID getActiveEncounterId() { return activeEncounterId; }
    public String getActiveEncounterName() { return activeEncounterName; }
}

public final class EncounterNotReadyException extends RuntimeException {
    private final EncounterReadinessDto readiness;

    public EncounterNotReadyException(EncounterReadinessDto readiness) {
        super("Encounter has blocking readiness issues");
        this.readiness = readiness;
    }

    public EncounterReadinessDto getReadiness() { return readiness; }
}

public record SessionEncounterActivated(UUID campaignId, UUID encounterId) {}
```

Map both exceptions to HTTP 409 in the existing global problem-details
handler. The replacement response must expose code
`ACTIVE_ENCOUNTER_REPLACEMENT_REQUIRED`; the readiness response must expose
code `ENCOUNTER_NOT_READY` and the complete `EncounterReadinessDto`.

- [ ] **Step 5: Implement the transactional orchestration**

```java
@Service
@Transactional
public class SessionEncounterService {
    public EncounterActivationDto activate(
            UUID campaignId,
            UUID encounterId,
            ActiveEncounterDisposition disposition) {
        Encounter requested = requireCampaignEncounter(campaignId, encounterId);
        EncounterReadinessDto readiness = placements.readiness(encounterId);
        if (!readiness.canRun()) {
            throw new EncounterNotReadyException(readiness);
        }
        Encounter replaced = encounters
                .findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE)
                .filter(active -> !active.getId().equals(encounterId))
                .orElse(null);
        if (replaced != null && disposition == null) {
            throw new ActiveEncounterReplacementRequiredException(
                    replaced.getId(), replaced.getName());
        }
        if (replaced != null && disposition == ActiveEncounterDisposition.SUSPEND) {
            encounterService.suspend(replaced.getId());
        }
        if (replaced != null && disposition == ActiveEncounterDisposition.END) {
            encounterService.endEncounter(replaced.getId());
        }
        Encounter activated = requested.getStatus() == Encounter.Status.SUSPENDED
                ? encounterService.resume(encounterId)
                : encounterService.activateFresh(encounterId);
        CampaignSession session = sessions.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalStateException("No campaign session"));
        session.setWorkspaceMap(activated.getMap());
        sessions.save(session);
        events.publishEvent(new SessionEncounterActivated(campaignId, encounterId));
        return new EncounterActivationDto(
                encounterId, activated.getMap().getId(),
                activated.getStatus().name(),
                replaced == null ? null : replaced.getId(),
                replaced == null ? null : replaced.getStatus().name());
    }
}
```

If `activeEncounterDisposition` is absent while a different encounter is active, throw a conflict exception containing:

```json
{
  "code": "ACTIVE_ENCOUNTER_REPLACEMENT_REQUIRED",
  "activeEncounterId": "uuid",
  "activeEncounterName": "Goblin Ambush"
}
```

- [ ] **Step 6: Add the session endpoint and retire direct cockpit activation**

```java
@PostMapping("/encounters/{encounterId}/activate")
EncounterActivationDto activateEncounter(
        @PathVariable UUID campaignId,
        @PathVariable UUID encounterId,
        @RequestBody ActivateEncounterRequest request)
```

The cockpit must use this campaign-scoped endpoint. Keep the old
`POST /api/v1/encounters/{id}/activate` only for non-session encounter administration and make it reject replacement of another active encounter.

Change the MVC `POST /campaigns/{campaignId}/encounters/{id}/run` action to
redirect to:

```text
/campaigns/{campaignId}/session?runEncounter={id}
```

The cockpit consumes `runEncounter` once after initialization and executes
the same guarded activation flow as the encounter rail. This prevents the
detail page from bypassing the replacement decision.

- [ ] **Step 7: Run lifecycle, controller, and workspace tests**

Run:

```bash
./mvnw -Dtest=SessionEncounterServiceTest,SessionApiControllerTest,EncounterServiceTest,SessionWorkspaceMapSelectionTest test
```

Expected: all tests pass.

- [ ] **Step 8: Commit transactional encounter transitions**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter
git commit -m "feat: make session encounter activation atomic"
```

---

### Task 7: Rebuild encounter preparation around roster placement

**Files:**
- Modify: `src/main/resources/templates/encounter/setup.html`
- Modify: `src/main/resources/templates/encounter/_form.html`
- Modify: `src/main/resources/templates/encounter/detail.html`
- Create: `src/main/resources/templates/encounter/_placement-board.html`
- Create: `src/main/resources/static/js/encounter-placement.js`
- Modify: `src/main/resources/static/css/encounter.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterSetupSurfaceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java`

**Interfaces:**
- Consumes: combatants, placement endpoints, readiness endpoint, and runtime renderer.
- Produces: a preparation board with placed/unplaced status and direct placement controls.

- [ ] **Step 1: Add failing template and browser contracts**

Assert that setup contains:

```html
data-encounter-placement-board
data-unplaced-combatants
data-readiness-summary
data-place-party
data-auto-place
```

Assert that it does not contain:

```text
Prefill from Map
Prefill from Party
Open battle map
```

Add a Playwright test that drags an unplaced combatant onto the map, reloads the setup page, and verifies the placement remains at the snapped cell.

- [ ] **Step 2: Run focused UI tests and verify they fail**

Run:

```bash
./mvnw -Dtest=EncounterSetupSurfaceTest,EncounterTemplateContractTest,MapEditorBrowserTest test
```

Expected: the placement-board contracts fail.

- [ ] **Step 3: Build the preparation layout**

Use this information hierarchy:

```html
<section class="encounter-preparation">
  <header data-readiness-summary>
    <span>Map: Cragmaw Hideout</span>
    <span>6 combatants · 4 placed · 2 unplaced</span>
    <button data-auto-place>Place unplaced</button>
    <button data-place-party>Place missing party members</button>
  </header>
  <div class="encounter-preparation__workspace">
    <aside data-unplaced-combatants><!-- draggable roster rows --></aside>
    <div data-encounter-placement-board><!-- shared runtime map --></div>
  </div>
</section>
```

Each roster row must display:

```text
name · kind · current/max HP · Placed|Unplaced|Hidden
```

The map selector remains in encounter settings. Changing it must show a dialog:

```text
Change encounter map?
Existing placements do not fit a different map.
[Move and reset placements] [Cancel]
```

“Move and reset placements” deletes the old placements and updates the encounter map in one server transaction.

- [ ] **Step 4: Implement keyboard and pointer placement**

`encounter-placement.js` must support:

```javascript
async function placeCombatant(combatantId, col, row) {
    return dmRequest(
        `/api/v1/encounters/${encounterId}/combatants/${combatantId}/placement`,
        {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                positionX: col * cellSizePx,
                positionY: row * cellSizePx,
                sizeCols: 1,
                sizeRows: 1,
                color: defaultColorFor(combatant),
                icon: null
            })
        });
}
```

Keyboard flow: focus roster item, press Enter to enter placement mode, move the preview with arrow keys, press Enter to place, and Escape to cancel.

- [ ] **Step 5: Run preparation UI tests**

Run:

```bash
./mvnw -Dtest=EncounterSetupSurfaceTest,EncounterTemplateContractTest,MapEditorBrowserTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit encounter preparation UX**

```bash
git add src/main/resources/templates/encounter \
  src/main/resources/static/js/encounter-placement.js \
  src/main/resources/static/css/encounter.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter \
  src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
git commit -m "feat: add encounter roster placement workflow"
```

---

### Task 8: Update the session map to distinguish participants and markers

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/templates/session/_map-module.html`
- Modify: `src/main/resources/static/css/cockpit.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitMapContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java`

**Interfaces:**
- Consumes: `RuntimeTokenDto`, placement commands, marker commands, and combatant commands.
- Produces: source-aware rendering and editing.

- [ ] **Step 1: Add failing runtime contracts**

Assert the session map:

```text
loads /runtime-tokens
dispatches COMBATANT movement to /placement/move
dispatches MARKER movement to /tokens/{id}/move
never PATCHes /tokens/{id}/hp
labels the two sidebar sections Encounter participants and Map markers
labels marker creation Add temporary marker
labels party placement Place missing party members
```

Add a browser test that damages a combatant in the tracker and verifies the map token immediately shows the same HP/bloodied/defeated state after the tracker refresh event.

- [ ] **Step 2: Run the runtime tests and verify they fail**

Run:

```bash
./mvnw -Dtest=SessionCockpitMapContractTest,SessionCockpitTemplateContractTest,MapEditorBrowserTest test
```

Expected: existing code still loads and mutates legacy map tokens.

- [ ] **Step 3: Make `BattleMap` source-aware**

Use stable DOM/Konva identity keys:

```javascript
tokenKey(token) {
    return `${token.source}:${token.id}`;
}
```

Dispatch movement:

```javascript
async persistMove(token, positionX, positionY) {
    if (token.source === 'COMBATANT') {
        return this._request(
            `/api/v1/encounters/${this.activeEncounterId}`
            + `/combatants/${token.combatantId}/placement/move`,
            {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ positionX, positionY })
            });
    }
    return this._request(`/api/v1/tokens/${token.id}/move`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ positionX, positionY })
    });
}
```

For a combatant selection, show HP, conditions, visibility, and defeat controls that call combatant endpoints. For a marker selection, show name, color, size, visibility, copy, and delete only.

- [ ] **Step 4: Reorganize the sidebar**

The default live-play sidebar contains:

```html
<section>
  <h3>Encounter participants</h3>
  <!-- placed and unplaced active roster -->
</section>
<section>
  <h3>Map markers</h3>
  <!-- non-combat markers -->
</section>
<details>
  <summary>Map authoring</summary>
  <!-- statblock lookup for encounter add, threat pins, temporary marker -->
</details>
```

When an encounter is active, selecting a statblock must create a combatant and placement together. When no encounter is active, the same authoring area may create a temporary map marker, but the button copy must say **Add temporary marker**.

- [ ] **Step 5: Refresh runtime tokens from tracker events**

On `tracker-encounter-state` and `tracker-conditions-changed`, reload or patch the `COMBATANT` runtime tokens from encounter DTO state. Do not copy encounter state into marker records.

- [ ] **Step 6: Run session map and browser tests**

Run:

```bash
./mvnw -Dtest=SessionCockpitMapContractTest,SessionCockpitTemplateContractTest,MapEditorBrowserTest test
```

Expected: all tests pass.

- [ ] **Step 7: Commit the source-aware session map**

```bash
git add src/main/resources/static/js/map/battle-map.js \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/templates/session/_map-module.html \
  src/main/resources/static/css/cockpit.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
git commit -m "feat: separate encounter participants from map markers"
```

---

### Task 9: Simplify planned encounter actions and guard map mismatch

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/runtime/CockpitRuntimeModuleViewService.java`
- Modify: `src/main/resources/templates/session/_encounter-rail.html`
- Modify: `src/main/resources/templates/session/_map-module.html`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/main/resources/static/css/cockpit.css`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/CockpitRuntimeModuleContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionWorkspaceMapSelectionTest.java`

**Interfaces:**
- Consumes: atomic activation API and active encounter map ID.
- Produces: one run/resume action, replacement dialog, readiness indicators, and detached-map warning.

- [ ] **Step 1: Add failing session UX contracts**

Assert:

```text
planned rows contain Run encounter but no separate Map button
suspended rows contain Resume encounter
active encounter view contains encounterMapId
replacement dialog offers Suspend current and End current
map mismatch banner contains Return to encounter map
activation performs one request and no client-side second switchMap call
runEncounter query parameter is consumed once and removed from browser history
```

- [ ] **Step 2: Run session contracts and verify they fail**

Run:

```bash
./mvnw -Dtest=CockpitRuntimeModuleContractTest,SessionCockpitTemplateContractTest,SessionWorkspaceMapSelectionTest test
```

Expected: the old Activate/Map controls and two-step client activation remain.

- [ ] **Step 3: Enrich encounter rail view records**

```java
public record EncounterView(
        UUID activeEncounterId,
        String activeEncounterName,
        UUID activeEncounterMapId,
        String combatPhase,
        List<CombatantView> combatants,
        List<PlannedEncounterView> planned,
        List<PlannedEncounterView> suspended) {}

public record PlannedEncounterView(
        UUID id, String name, UUID mapId, String mapName,
        boolean ready, int combatantCount, int unplacedCount) {}
```

- [ ] **Step 4: Replace rail actions with one primary command**

Example row:

```html
<div class="planned-encounter-row">
  <span>
    <strong>Goblin Ambush</strong>
    <small>Cragmaw Hideout · 6 combatants · Ready</small>
  </span>
  <button @click="runEncounter(id)">Run encounter</button>
</div>
```

If readiness has warnings, the action opens a readiness dialog that lists the exact issues and offers **Run anyway** and **Open setup**. `EMPTY_ROSTER` and `UNPLACED_COMBATANTS` are warnings; `MISSING_MAP` and `PLACEMENT_MAP_MISMATCH` are blocking.

- [ ] **Step 5: Implement explicit active-encounter replacement**

The first activation request has no disposition. On HTTP 409 with
`ACTIVE_ENCOUNTER_REPLACEMENT_REQUIRED`, open an app dialog:

```text
Another encounter is running
Goblin Ambush is currently at round 3.
[Suspend current and run] [End current and run] [Cancel]
```

The two primary choices retry once with:

```json
{ "activeEncounterDisposition": "SUSPEND" }
```

or:

```json
{ "activeEncounterDisposition": "END" }
```

During cockpit initialization, read `runEncounter` with `URLSearchParams`,
call `runEncounter(id)`, and immediately remove the parameter using
`history.replaceState`. A refresh must not repeat the activation.

- [ ] **Step 6: Add the detached-map state**

When `currentMapId !== activeEncounterMapId`, show:

```html
<div class="map-context-warning" role="status">
  Viewing a different map while Goblin Ambush is active.
  <button @click="switchMap(activeEncounterMapId)">
    Return to encounter map
  </button>
</div>
```

Switching maps manually remains allowed because a DM may need to reference another location. The warning makes the detached state explicit. Never change the active encounter merely because the workspace map changes.

- [ ] **Step 7: Run session UX tests**

Run:

```bash
./mvnw -Dtest=CockpitRuntimeModuleContractTest,SessionCockpitTemplateContractTest,SessionWorkspaceMapSelectionTest,SessionApiControllerTest test
```

Expected: all tests pass.

- [ ] **Step 8: Commit encounter session UX**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session \
  src/main/resources/templates/session \
  src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/css/cockpit.css \
  src/test/java/dev/hendrikhoemberg/dmhelper/session
git commit -m "feat: clarify encounter activation and map context"
```

---

### Task 10: Project authoritative encounter tokens to the player table

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/LiveTableState.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/TablePresentationService.java`
- Modify: `src/main/resources/static/js/player/player-view.js`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/TablePresentationServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterEvidenceIntegrationTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java`

**Interfaces:**
- Consumes: runtime token projection and active encounter.
- Produces: safe player snapshots sourced from combatants.

- [ ] **Step 1: Add failing player-safety tests**

```java
@Test
void hiddenCombatantAndHiddenMarkerAreNotProjected() {
    Encounter encounter = fixture.activeEncounterWithMap();
    fixture.placedCombatant(encounter, "Visible", false);
    fixture.placedCombatant(encounter, "Hidden", true);
    fixture.marker(encounter.getMap(), "Visible marker", false);
    fixture.marker(encounter.getMap(), "Hidden marker", true);

    List<TokenSnapshot> result =
            service.projectTokens(encounter.getMap(), encounter);

    assertThat(result).extracting(TokenSnapshot::name)
            .containsExactlyInAnyOrder("Visible", "Visible marker");
}

@Test
void detachedMapContainsNoActiveEncounterOverlay() {
    Encounter encounter = fixture.activeEncounterWithMap();
    fixture.placedCombatant(encounter, "Goblin", false);
    GameMap otherMap = fixture.map("Other room");
    fixture.marker(otherMap, "Table", false);

    List<TokenSnapshot> result = service.projectTokens(otherMap, encounter);

    assertThat(result).extracting(TokenSnapshot::name)
            .containsExactly("Table");
}
```

Add assertions that suspended encounter placements are absent and a visible
combatant's `defeated` and `bloodied` values are derived from its current
combatant state.

Add a browser assertion that a player token updates after damage without any map-token HP mutation.

- [ ] **Step 2: Run live projection tests and verify they fail**

Run:

```bash
./mvnw -Dtest=PlayerSafeProjectionServiceTest,TablePresentationServiceTest,SessionEncounterEvidenceIntegrationTest,MapEditorBrowserTest test
```

Expected: live projection still reads only `TokenRepository`.

- [ ] **Step 3: Reuse the unified projection and filter for player safety**

Change:

```java
public List<LiveTableState.TokenSnapshot> projectTokens(GameMap gameMap)
```

to:

```java
public List<LiveTableState.TokenSnapshot> projectTokens(
        GameMap gameMap, Encounter activeEncounter)
```

Filter `RuntimeTokenDto.hidden()`, and map combatant-derived `defeated` and `bloodied`. Include `source` and `combatantId` in `TokenSnapshot` so the browser can maintain stable identity without exposing private fields.

- [ ] **Step 4: Broadcast after every state that changes token appearance**

Verify these commands invoke `broadcastCurrentState(campaignId)` after commit:

```text
damage
heal
set HP
defeat/revive
hide/show combatant
add/remove condition
move/add/remove placement
activate/suspend/end/resume encounter
move/add/remove/hide marker
```

Use an after-commit event if broadcasting inside the transaction could expose uncommitted state.

- [ ] **Step 5: Run live and browser tests**

Run:

```bash
./mvnw -Dtest=PlayerSafeProjectionServiceTest,TablePresentationServiceTest,SessionEncounterEvidenceIntegrationTest,MapEditorBrowserTest test
```

Expected: all tests pass and player output contains no hidden participants.

- [ ] **Step 6: Commit player projection**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/live \
  src/main/resources/static/js/player/player-view.js \
  src/test/java/dev/hendrikhoemberg/dmhelper/live \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionEncounterEvidenceIntegrationTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/MapEditorBrowserTest.java
git commit -m "feat: project encounter placements to the player table"
```

---

### Task 11: Update campaign package import/export and semantic validation

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterSectionAdapter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/packagev2/MapSectionAdapter.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterPlacementPackageTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/CampaignPackageRoundTripTest.java`

**Interfaces:**
- Consumes: placement DTOs and marker-only map tokens.
- Produces: package-v2 round trips preserving encounter geometry.

- [ ] **Step 1: Add failing package round-trip tests**

Build a package containing:

```json
{
  "key": "goblin-1",
  "name": "Goblin 1",
  "currentHp": 5,
  "maxHp": 7,
  "placement": {
    "positionX": 144,
    "positionY": 96,
    "sizeCols": 1,
    "sizeRows": 1,
    "color": "#55aa55"
  }
}
```

Assert export → import → export preserves the placement and does not create a map marker. Add a legacy fixture containing `tokenRef` and assert it imports by converting the referenced map token geometry into a placement.

- [ ] **Step 2: Run package tests and verify they fail**

Run:

```bash
./mvnw -Dtest=EncounterPlacementPackageTest,CampaignPackageRoundTripTest test
```

Expected: the manifest model has no placement object.

- [ ] **Step 3: Add placement to the package model and schema**

```java
public record CombatantPlacementDto(
        int positionX,
        int positionY,
        int sizeCols,
        int sizeRows,
        String color,
        String icon) {}
```

Add optional `placement` to the existing combatant schema:

```json
"combatantPlacement": {
  "type": "object",
  "additionalProperties": false,
  "required": [
    "positionX", "positionY", "sizeCols", "sizeRows", "color"
  ],
  "properties": {
    "positionX": { "type": "integer", "minimum": 0 },
    "positionY": { "type": "integer", "minimum": 0 },
    "sizeCols": { "type": "integer", "minimum": 1 },
    "sizeRows": { "type": "integer", "minimum": 1 },
    "color": { "type": "string", "pattern": "^#[0-9a-fA-F]{6}$" },
    "icon": { "type": "string" }
  }
}
```

Keep `tokenRef` accepted during import for backward compatibility, but never emit it during export.

Add `SUSPENDED` to every encounter-status enum in the package model and JSON
schema. Remove `currentHp`, `maxHp`, and `dead` from the map-token export
shape; keep those properties optional and ignored on legacy import so older
packages continue to validate and load.

- [ ] **Step 4: Update adapters and semantic validation**

Export placements from `EncounterSectionAdapter`, not `MapSectionAdapter`. During legacy import:

```java
if (combatantDto.placement() != null) {
    placementImporter.create(combatant, encounter.getMap(), combatantDto.placement());
} else if (combatantDto.tokenRef() != null) {
    Token legacy = context.resolveMapToken(combatantDto.tokenRef());
    placementImporter.copyGeometry(combatant, legacy);
}
```

Validate that an encounter with a placement has a map and that all geometry fits its map bounds.

- [ ] **Step 5: Run package, schema, and migration tests**

Run:

```bash
./mvnw -Dtest=EncounterPlacementPackageTest,CampaignPackageRoundTripTest,CampaignManifestV2SemanticValidatorTest,FlywayMigrationTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit package compatibility**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/packagev2 \
  src/main/resources/schemas/campaign-format-v2.schema.json \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2 \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2
git commit -m "feat: preserve encounter placements in campaign packages"
```

---

### Task 12: Remove legacy UX language and complete end-to-end verification

**Files:**
- Modify: `src/main/resources/templates/maps/list.html`
- Modify: `src/main/resources/templates/maps/_card.html`
- Modify: `src/main/resources/templates/encounter/_card.html`
- Modify: `src/main/resources/templates/encounter/_prep-summary.html`
- Modify: `src/main/resources/templates/session/_map-module.html`
- Modify: `src/main/resources/templates/session/_encounter-rail.html`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterTemplateContractTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`

**Interfaces:**
- Consumes: all completed backend and runtime behavior.
- Produces: consistent language and final release evidence.

- [ ] **Step 1: Add terminology and readiness contracts**

Assert that application surfaces use:

```text
Run encounter
Resume encounter
Encounter participants
Map markers
Add temporary marker
Place missing party members
Return to encounter map
Placed
Unplaced
```

Assert that these ambiguous labels are absent:

```text
Prefill from Map
Prefill from Party
Add Party
+ Token
Activate
```

- [ ] **Step 2: Add useful summary metadata**

Encounter cards show:

```text
Map name · combatant count · placed/unplaced count · Planned|Suspended|Active|Done
```

Map cards show:

```text
dimensions · marker count · encounters using this map
```

The encounter prep summary links to **Prepare placements** rather than opening a separate battle-map tab.

- [ ] **Step 3: Run the focused domain suites**

Run:

```bash
./mvnw -Dtest='dev.hendrikhoemberg.dmhelper.encounter.**,dev.hendrikhoemberg.dmhelper.gamemap.**,dev.hendrikhoemberg.dmhelper.session.**,dev.hendrikhoemberg.dmhelper.live.**' test
```

Expected: all focused tests pass with zero failures and zero errors.

- [ ] **Step 4: Run the full automated suite**

Run:

```bash
./mvnw test
```

Expected: Maven exits 0.

- [ ] **Step 5: Run the browser workflow**

Use a test campaign and verify:

```text
1. Create one map and two encounters linked to it.
2. Add different monsters to each encounter and place them.
3. Run encounter A; only A's participants and persistent markers appear.
4. Damage and defeat a participant; DM and player views update together.
5. Try to run encounter B; cancel replacement and verify A is unchanged.
6. Run B with Suspend current; verify B's map overlay replaces A's.
7. Resume A; verify its round, HP, conditions, initiative, and positions return.
8. Switch the workspace to another map; verify the detached warning appears.
9. Return to the encounter map using the warning action.
10. End A; verify the base map contains no encounter creatures.
11. Export and import the campaign; verify both encounter placements survive.
```

- [ ] **Step 6: Check static contracts and the diff**

Run:

```bash
rg -n "prefillFromMap|addPartyToMap|tokenId|/tokens/.*/hp|/tokens/.*/dead" \
  src/main src/test
git diff --check
git status --short
```

Expected: no production references to removed combat-token workflows; `git diff --check` exits 0.

- [ ] **Step 7: Commit final UX and verification changes**

```bash
git add src/main/resources/templates \
  src/main/resources/static \
  src/test
git commit -m "test: verify encounter map and marker workflow"
```

---

## Acceptance Criteria

- A map can be linked to multiple encounters without participants leaking between them.
- Running an encounter selects its linked map in the same successful server transaction.
- Switching away from the encounter map is allowed but visibly marked as detached.
- A different active encounter cannot be silently marked done.
- Suspended encounters resume with their round, initiative, HP, conditions, and placements intact.
- A participant has one authoritative HP/death/visibility state: its combatant.
- The DM and player map render that authoritative state without persisting it to a map marker.
- Map markers cannot be added to initiative or edited as combatants.
- Party placement is encounter-scoped, idempotent, and clearly labelled.
- Encounter setup visibly identifies placed and unplaced combatants.
- Existing linked token geometry migrates to placements.
- Existing unlinked map tokens survive as markers.
- Package-v2 export emits placements; package-v2 import accepts both placements and legacy token references.
- No browser-native modal dialogue is used.
- Focused tests, full Maven tests, browser workflow, and `git diff --check` all pass.
