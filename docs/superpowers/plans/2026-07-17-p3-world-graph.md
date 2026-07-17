# P3 World Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first P3 expansion slice of the all-in-one DM readiness specification: first-class campaign-scoped world entities (NPCs, locations, factions, relationships) and faction clocks that complement notes, resolve as typed package keys, integrate with quest/scene links and search, and round-trip through campaign package v2.

**Architecture:** Add a new `world` module with JPA entities, services, package section adapter, and DM authoring UI. Reuse package keys (`CampaignPackageKeyService`), `ContentReference`, deferred import resolution, and the existing quest/scene link machinery rather than inventing a second identity or graph store. Notes with `NoteType.NPC` / `LOCATION` remain valid prose documents; world entities optionally link to a note via `noteRef` and never replace the wiki. Faction clocks cover the “campaign clocks” portion of P3 without inventing travel, weather, fog, audio, or interactive player systems.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2, Jackson 3, Thymeleaf, HTMX, Alpine, JSON Schema draft 2020-12, Maven Wrapper, JUnit 5 / AssertJ / MockMvc, existing package-v2 pipeline and flagship fixtures.

## Global Constraints

- Master design §14.3 (Structured world entities), §5 P3, and delivery item 11 first slice are authoritative. Do **not** implement travel/weather routes, rollable-table engines, fog-of-war gameplay, audio presentation, or interactive player permissions in this plan (those remain later P3 slices).
- World entities are **optional**. Existing packages without world arrays must validate and import unchanged (arrays default to empty; do not add world fields to the schema `required` root array).
- Notes remain the flexible authoring surface. Do not delete, migrate, or reinterpret existing `NoteType.NPC` / `LOCATION` rows into world entities automatically.
- Every package-owned world entity has a stable key matching `^[a-z0-9][a-z0-9._-]{0,99}$` via `CampaignPackageKeyService`. References use `ContentReference` with `scope: PACKAGE` and the correct `CampaignContentType`.
- Secrets, motivations, voice notes, DM-only disposition detail, relationship `SECRET` knowledge, and clock notes are DM-only. Player-safe projections must never include them.
- Import remains additive and atomic. No silent inventing of map coordinates, faction reputation numbers, or relationship edges when source data is missing.
- Use Flyway migration `V12__add_world_graph.sql`. Do not rely on Hibernate schema generation.
- Package format version stays `2`. Do not introduce format version 3.
- Offline/local-first: no CDN, no network generative services, no new frontend build chain.
- Tests use isolated home: `-DargLine=-Duser.home=/tmp/dmhelper-world-graph`.
- Complete every task with focused tests before moving on. Prefer TDD: failing test → implement → pass → commit.
- Delivery item 11 remains multi-slice: after this plan, mark **world.graph** `SUPPORTED` and leave travel/fog/player/audio `UNSUPPORTED`. Do **not** set master design §22 item 11 to `IMPLEMENTED` until remaining P3 slices land.

---

## Audit result: which delivery steps are already implemented

Verified against master design §22 status table, capability manifest, git history (through documentation/agent SDK commits), migrations V1–V11, and repository evidence as of 2026-07-17:

| # | Delivery item | Spec status | Audit status | Key evidence |
|---|---|---|---|---|
| 1 | P0 runtime reliability | `IMPLEMENTED` | **Correct** | Quick notes, `ContentDestinationRegistry`, correlated errors, package asset safety, difficulty labeled estimate |
| 2 | Campaign contract v1 repair | `IMPLEMENTED` | **Correct** | Closed v1 schemas, unified dry-run/import, v1 fixtures |
| 3 | Package v2 foundation | `IMPLEMENTED` | **Correct** | ZIP/JSON, keys, validation pipeline, preview, staged assets, migrations |
| 4 | Complete round-trip | `IMPLEMENTED` | **Correct** | Section adapters, semantic snapshot/compare, flagship fixtures |
| 5 | Session cockpit | `IMPLEMENTED` | **Correct** | Real `/campaigns/{id}/session`, lifecycle, rails, session package adapter (V4) |
| 6 | Structured adventure/quest | `IMPLEMENTED` | **Correct** | Scene structure, transitions, quests/objectives (V5–V6) |
| 7 | Custom compendium expansion | `IMPLEMENTED` | **Correct** | Ownership + provenance + package custom arrays (V7) |
| 8 | Character-sheet completion | `IMPLEMENTED` | **Correct** | Live party state, attacks/features, inventory, rest, batch ops (V8, V11) |
| 9 | Encounter and map depth | `IMPLEMENTED` | **Correct** | Waves/rewards/placement, HAZARD kind, calibration/regions (V9–V10) |
| 10 | Documentation/agent SDK | `IMPLEMENTED` | **Correct** | `agent/capability-manifest.json`, validation-error catalog, PIN-free APIs, `docs/agent`, `docs/dm-manual`, `docs/architecture`, `docs/product`, executable examples |
| **11** | **P3 expansion** | **`PLANNED`** | **Not started** | Capability rows `world.graph`, `travel.weather`, `map.fog_of_war`, `player.interaction` are all `UNSUPPORTED` |

### What already exists that world graph must reuse (not rewrite)

| Area | Exists today | Implication |
|---|---|---|
| Notes `NPC` / `LOCATION` types | `NoteType` enum + wiki | Keep; world entities optional complement |
| Quest link roles | `NPC`, `LOCATION`, `FACTION` on `QuestLinkRole` | Role names stay; **target type** currently forced toward `NOTE` in semantic validator — must accept world content types |
| Scene participants | Optional `note_id` FK | Keep note link; add optional `worldNpc` FK |
| Package keys | `CampaignPackageKeyService` + `CampaignContentType` | Extend enum; no second key system |
| Import orders | Ledger 800 → Adventure 900 → Quest 950 → Notes 1000 | World adapter order **850** so adventures/quests can resolve world refs |
| Nav “World” group | Calendar + Library only | Add NPCs / Locations / Factions links |
| Capability manifest | `world.graph` = `UNSUPPORTED` | Flip only after acceptance contract passes |

### Hard gaps this plan closes (master design §14.3)

1. No first-class NPC/location/faction entities — only free-form notes and quest/scene role labels.
2. No typed relationships (edges) with direction or public/secret knowledge.
3. No faction/campaign clocks.
4. Quest/scene links with role `NPC`/`LOCATION`/`FACTION` cannot target real world package keys (`CampaignManifestV2SemanticValidator` maps those roles to `NOTE`).
5. No world destinations in `ContentDestinationRegistry` / command palette.
6. No package arrays or round-trip for world graph data.
7. Capability matrix cannot honestly claim structured world knowledge.

### Explicitly deferred (later P3 plans)

| Slice | Spec refs | Why not this plan |
|---|---|---|
| Travel / weather / pace / supplies | §15.3 | Separate subsystem integrating calendar + party resources |
| Rollable tables engine | §5 P3, §10.1 tables | Generic `RULE` already stores table text; mechanical roller is separate |
| Fog of war gameplay | §13.3 | Map document may later hold reveal state; gameplay is post-readiness depth |
| Audio presentation | §5 P3, §7.1 assets/audio | Asset folder exists conceptually; player presentation needs its own design |
| Interactive players | §16 future | Requires separate permissions design |
| Trap/hazard **automation** | §12 prep | Scene `TRAP`/`HAZARD` sections and combatant `HAZARD` kind already cover descriptive + initiative cases |

---

## Acceptance contract (this slice)

- [ ] Campaign can CRUD **WorldNpc**, **WorldLocation**, **Faction**, **WorldRelationship**, and **FactionClock** entities with package keys.
- [ ] Each entity stores the minimum structured fields from §14.3 (see domain contract below); all fields except identity/title are optional.
- [ ] Notes are not deleted or auto-converted; optional `noteRef` links a world entity to a prose note.
- [ ] Relationships are typed directed edges between two package world entities (or faction) with public/secret knowledge and status.
- [ ] Faction clocks have title, segment count, filled count, optional linked objective/scene refs, and never auto-tick on quest completion.
- [ ] Package v2 export/import round-trips all world entities; semantic snapshot/compare includes them.
- [ ] Existing fixtures without world arrays remain valid.
- [ ] Quest links with roles `NPC`/`LOCATION`/`FACTION` accept `WORLD_NPC` / `WORLD_LOCATION` / `FACTION` package refs (and still accept legacy `NOTE` targets).
- [ ] Scene participants may optionally reference a world NPC in addition to note/statblock.
- [ ] Command palette and destination registry open valid world detail routes for each new type.
- [ ] DM-only fields absent from player network payloads / live projection.
- [ ] Capability matrix + agent manifest set `world.graph` to `SUPPORTED`; travel/fog/player remain `UNSUPPORTED`.
- [ ] Master design §22 item 11 stays `PLANNED` (or note “in progress”) — only the world-graph capability row advances.

---

## Domain contracts (authoritative)

### Content types

Add to `CampaignContentType` and capability `contentTypes`:

```text
WORLD_NPC
WORLD_LOCATION
FACTION
WORLD_RELATIONSHIP
FACTION_CLOCK
```

### Enums (persist `@Enumerated(EnumType.STRING)`; schema enum lists must match exactly)

```java
public enum WorldNpcStatus {
    ALIVE, DEAD, MISSING, UNKNOWN
}

public enum WorldDisposition {
    HOSTILE, UNFRIENDLY, NEUTRAL, FRIENDLY, ALLY, UNKNOWN
}

public enum LocationKind {
    SITE, REGION, SETTLEMENT, PLANE, OTHER
}

public enum RelationshipKind {
    ALLY, ENEMY, RIVAL, MEMBER_OF, LEADS, SERVES, RELATED, KNOWS, OWNS, LOCATED_IN, TRAVELS_TO, OTHER
}

public enum RelationshipKnowledge {
    PUBLIC, SECRET
}

public enum RelationshipStatus {
    ACTIVE, STRAINED, BROKEN, UNKNOWN
}
```

### Package DTO shapes (`CampaignManifestV2`)

Optional root arrays (not in schema `required`):

```json
{
  "worldNpcs": [],
  "worldLocations": [],
  "factions": [],
  "worldRelationships": [],
  "factionClocks": []
}
```

```java
// Nested records on CampaignManifestV2 — field names are the external contract.
public record WorldNpcDto(
        String key,
        String name,
        String role,
        String disposition,          // WorldDisposition name or null
        ContentReference factionRef, // PACKAGE FACTION
        ContentReference locationRef,// PACKAGE WORLD_LOCATION
        ContentReference noteRef,    // PACKAGE NOTE (optional prose twin)
        ContentReference statblockRef, // PACKAGE STATBLOCK or CATALOG STATBLOCK
        String appearance,
        String voice,
        String motivation,
        String secret,               // DM-only
        String inventoryText,        // free-form; no treasury automation in this slice
        String status,               // WorldNpcStatus
        List<String> tags,
        String sourceLocator,
        Instant createdAt
) {}

public record WorldLocationDto(
        String key,
        String name,
        String kind,                 // LocationKind
        ContentReference parentLocationRef,
        ContentReference mapRef,     // PACKAGE MAP
        String mapRegionKey,         // named region on map document; free string
        ContentReference noteRef,
        String summary,
        String services,             // free-form
        String secrets,              // DM-only
        List<ContentReference> occupantNpcRefs,
        List<ContentReference> encounterRefs,
        List<ContentReference> travelLocationRefs, // adjacent locations only; not full travel subsystem
        List<String> tags,
        String sourceLocator,
        Instant createdAt
) {}

public record FactionDto(
        String key,
        String name,
        String goals,
        String resources,
        String reputationNotes,
        ContentReference noteRef,
        List<String> tags,
        String sourceLocator,
        Instant createdAt
) {}

public record WorldRelationshipDto(
        String key,
        String kind,                 // RelationshipKind
        ContentReference fromRef,    // WORLD_NPC | FACTION | WORLD_LOCATION
        ContentReference toRef,
        boolean directed,            // false = undirected for display; still stores from/to order
        String knowledge,            // RelationshipKnowledge
        String status,               // RelationshipStatus
        String notes,                // DM-only when knowledge=SECRET
        String sourceLocator,
        int sortOrder
) {}

public record FactionClockDto(
        String key,
        ContentReference factionRef, // required PACKAGE FACTION
        String title,
        int segments,                // >= 1
        int filled,                  // 0..segments
        ContentReference objectiveRef, // optional OBJECTIVE
        ContentReference sceneRef,     // optional SCENE
        String notes,
        String sourceLocator,
        int sortOrder
) {}
```

### Reference rules

| From | Field | Allowed target types |
|---|---|---|
| WorldNpc | factionRef | FACTION |
| WorldNpc | locationRef | WORLD_LOCATION |
| WorldNpc | noteRef | NOTE |
| WorldNpc | statblockRef | STATBLOCK (PACKAGE or CATALOG) |
| WorldLocation | parentLocationRef | WORLD_LOCATION (no cycles) |
| WorldLocation | mapRef | MAP |
| WorldLocation | occupantNpcRefs | WORLD_NPC |
| WorldLocation | encounterRefs | ENCOUNTER |
| WorldLocation | travelLocationRefs | WORLD_LOCATION |
| WorldRelationship | fromRef / toRef | WORLD_NPC, FACTION, WORLD_LOCATION |
| FactionClock | factionRef | FACTION |
| FactionClock | objectiveRef | OBJECTIVE |
| FactionClock | sceneRef | SCENE |
| QuestLink role NPC | targetRef | WORLD_NPC or NOTE (legacy) |
| QuestLink role LOCATION | targetRef | WORLD_LOCATION or NOTE |
| QuestLink role FACTION | targetRef | FACTION or NOTE |
| SceneParticipant | worldNpc (new) | WORLD_NPC entity FK |

### Validation problem codes (new)

Add to `ImportProblemCodes` and `agent/validation-error-catalog.json`:

```text
WORLD_LOCATION_CYCLE
WORLD_RELATIONSHIP_SELF
FACTION_CLOCK_RANGE
INVALID_WORLD_REFERENCE_TYPE
```

Reuse existing `UNRESOLVED_REFERENCE`, `DUPLICATE_KEY`, `SCHEMA_VIOLATION` where applicable.

### Import order

```text
WorldSectionAdapter.order() == 850
```

Between ledger (800) and adventure (900). Relationships and clocks may defer objective/scene resolution if needed; prefer registering NPCs/locations/factions first in the same adapter, then relationships/clocks, with `context.defer` only for targets imported later (scenes/objectives).

### Routes

| Surface | Path |
|---|---|
| NPC list | `GET /campaigns/{id}/world/npcs` |
| NPC detail/edit | `GET/POST/PUT/DELETE /campaigns/{id}/world/npcs[/{npcId}]` |
| Location list/detail | `/campaigns/{id}/world/locations[/{locationId}]` |
| Faction list/detail | `/campaigns/{id}/world/factions[/{factionId}]` |
| Relationship POST/DELETE | nested under faction or global `/campaigns/{id}/world/relationships` |
| Clock POST/PUT/DELETE | `/campaigns/{id}/world/factions/{factionId}/clocks[/{clockId}]` |
| JSON API (optional thin) | `/api/v1/campaigns/{id}/world/**` for HTMX/JSON consumers if needed |

Destination registry campaign types:

```text
WORLD_NPC, WORLD_LOCATION, FACTION
```

---

## File map

| Area | Create | Modify |
|---|---|---|
| DB | `src/main/resources/db/migration/V12__add_world_graph.sql` | `FlywayMigrationTest.java` (assert V12) |
| Domain | `world/data/*` entities, enums, repositories | `CampaignContentType.java` |
| Service | `world/service/WorldService.java`, `WorldReferenceCleaner.java`, `WorldLocationCycleValidator.java` | `CampaignCascadeDeleteTest.java`, quest/scene cleaners as needed |
| Package model | — | `CampaignManifestV2.java`, `CampaignManifestAssembler.java` |
| Package adapter | `world/packagev2/WorldSectionAdapter.java` | semantic validator, snapshot service, comparator if field-based |
| Schema/docs | fixture `src/test/resources/campaigns/v2/world-graph.dmcampaign/` | `campaign-format-v2.schema.json`, `docs/campaign-format-v2.md`, `docs/authoring/*`, agent manifest |
| Quest/scene integration | — | `QuestService` link target rules, `CampaignManifestV2SemanticValidator` role→type map, `SceneParticipant` + adventure adapter, templates for link pickers |
| UI | `world/web/WorldController.java`, templates under `templates/world/` | `_appnav.html`, command palette, destination registry + route contract tests |
| Capabilities | — | `capability-manifest.json`, `docs/campaign-capabilities.md`, `docs/product/known-limitations.md` |
| Tests | persistence, service, adapter, controller, semantic, round-trip, player-safety | existing package contract tests that construct manifests |

---

### Task 1: Flyway V12 and domain entities

**Files:**
- Create: `src/main/resources/db/migration/V12__add_world_graph.sql`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/world/data/*.java` (entities, enums, repos)
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/world/data/WorldPersistenceTest.java`

**Interfaces:**
- Produces: JPA entities `WorldNpc`, `WorldLocation`, `Faction`, `WorldRelationship`, `FactionClock` with campaign FK cascade-delete

- [ ] **Step 1: Write failing Flyway assertion**

Append to `FlywayMigrationTest`:

```java
@Test
void v12CreatesWorldGraphTables() {
    Integer applied = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '12' AND \"success\" = TRUE",
            Integer.class);
    assertThat(applied).isEqualTo(1);

    for (String table : List.of(
            "WORLD_NPC", "WORLD_LOCATION", "FACTION", "WORLD_RELATIONSHIP", "FACTION_CLOCK")) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, table);
        assertThat(count).as(table).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-world-graph -Dtest=FlywayMigrationTest#v12CreatesWorldGraphTables test`

Expected: FAIL (version 12 not applied / tables missing).

- [ ] **Step 3: Write migration SQL**

```sql
-- V12__add_world_graph.sql

create table faction (
    id uuid not null,
    campaign_id uuid not null,
    name varchar(500) not null,
    goals CLOB,
    resources CLOB,
    reputation_notes CLOB,
    note_id uuid,
    tags varchar(1000),
    source_locator varchar(500),
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_faction_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_faction_note foreign key (note_id) references note on delete set null
);
create index idx_faction_campaign on faction (campaign_id);

create table world_location (
    id uuid not null,
    campaign_id uuid not null,
    name varchar(500) not null,
    kind varchar(20) not null default 'SITE',
    parent_location_id uuid,
    map_id uuid,
    map_region_key varchar(100),
    note_id uuid,
    summary CLOB,
    services CLOB,
    secrets CLOB,
    tags varchar(1000),
    source_locator varchar(500),
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_world_location_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_world_location_parent foreign key (parent_location_id) references world_location on delete set null,
    constraint fk_world_location_map foreign key (map_id) references game_map on delete set null,
    constraint fk_world_location_note foreign key (note_id) references note on delete set null
);
create index idx_world_location_campaign on world_location (campaign_id);

create table world_npc (
    id uuid not null,
    campaign_id uuid not null,
    name varchar(500) not null,
    role varchar(500),
    disposition varchar(20),
    faction_id uuid,
    location_id uuid,
    note_id uuid,
    statblock_id uuid,
    appearance CLOB,
    voice CLOB,
    motivation CLOB,
    secret CLOB,
    inventory_text CLOB,
    status varchar(20) not null default 'UNKNOWN',
    tags varchar(1000),
    source_locator varchar(500),
    created_at timestamp with time zone not null,
    primary key (id),
    constraint fk_world_npc_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_world_npc_faction foreign key (faction_id) references faction on delete set null,
    constraint fk_world_npc_location foreign key (location_id) references world_location on delete set null,
    constraint fk_world_npc_note foreign key (note_id) references note on delete set null,
    constraint fk_world_npc_statblock foreign key (statblock_id) references stat_block on delete set null
);
create index idx_world_npc_campaign on world_npc (campaign_id);

-- Optional occupancy without forcing bidirectional ownership complexity:
-- occupants are derived from world_npc.location_id; location encounter links as join table.

create table world_location_encounter (
    location_id uuid not null,
    encounter_id uuid not null,
    sort_order integer not null default 0,
    primary key (location_id, encounter_id),
    constraint fk_wle_location foreign key (location_id) references world_location on delete cascade,
    constraint fk_wle_encounter foreign key (encounter_id) references encounter on delete cascade
);

create table world_location_travel (
    location_id uuid not null,
    target_location_id uuid not null,
    sort_order integer not null default 0,
    primary key (location_id, target_location_id),
    constraint fk_wlt_from foreign key (location_id) references world_location on delete cascade,
    constraint fk_wlt_to foreign key (target_location_id) references world_location on delete cascade,
    constraint ck_wlt_not_self check (location_id <> target_location_id)
);

create table world_relationship (
    id uuid not null,
    campaign_id uuid not null,
    kind varchar(30) not null,
    from_type varchar(30) not null,
    from_id uuid not null,
    to_type varchar(30) not null,
    to_id uuid not null,
    directed boolean not null default true,
    knowledge varchar(20) not null default 'PUBLIC',
    status varchar(20) not null default 'ACTIVE',
    notes CLOB,
    source_locator varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_world_relationship_campaign foreign key (campaign_id) references campaign on delete cascade
);
create index idx_world_relationship_campaign on world_relationship (campaign_id);

create table faction_clock (
    id uuid not null,
    campaign_id uuid not null,
    faction_id uuid not null,
    title varchar(500) not null,
    segments integer not null,
    filled integer not null default 0,
    objective_id uuid,
    scene_id uuid,
    notes CLOB,
    source_locator varchar(500),
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_faction_clock_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_faction_clock_faction foreign key (faction_id) references faction on delete cascade,
    constraint fk_faction_clock_objective foreign key (objective_id) references quest_objective on delete set null,
    constraint fk_faction_clock_scene foreign key (scene_id) references adventure_scene on delete set null,
    constraint ck_faction_clock_segments check (segments >= 1),
    constraint ck_faction_clock_filled check (filled >= 0 and filled <= segments)
);
create index idx_faction_clock_campaign on faction_clock (campaign_id);

-- Scene participant optional world NPC
alter table scene_participant add column world_npc_id uuid;
alter table scene_participant add constraint fk_scene_participant_world_npc
    foreign key (world_npc_id) references world_npc on delete set null;
```

- [ ] **Step 4: Implement entities/enums/repos**

Mirror existing quest style (UUID id, campaign ManyToOne, Instant createdAt, getters/setters). Relationships store polymorphic `fromType`/`fromId` as strings/UUIDs matching `CampaignContentType` names (`WORLD_NPC`, `FACTION`, `WORLD_LOCATION`) — same pattern as `QuestLink.targetType`/`targetId`.

Minimum entity fields must match the DTO contract above.

- [ ] **Step 5: Persistence test**

```java
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:world-persist;DB_CLOSE_DELAY=-1")
class WorldPersistenceTest {
    @Autowired CampaignRepository campaigns;
    @Autowired FactionRepository factions;
    @Autowired WorldNpcRepository npcs;
    @Autowired WorldLocationRepository locations;
    @Autowired WorldRelationshipRepository relationships;
    @Autowired FactionClockRepository clocks;

    @Test
    void persistsNpcLocationFactionRelationshipAndClock() {
        Campaign c = new Campaign();
        c.setName("W");
        c = campaigns.save(c);

        Faction f = new Faction();
        f.setCampaign(c);
        f.setName("Iron Ring");
        f.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        f = factions.save(f);

        WorldLocation loc = new WorldLocation();
        loc.setCampaign(c);
        loc.setName("Harbor");
        loc.setKind(LocationKind.SETTLEMENT);
        loc.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        loc = locations.save(loc);

        WorldNpc npc = new WorldNpc();
        npc.setCampaign(c);
        npc.setName("Mira");
        npc.setFaction(f);
        npc.setLocation(loc);
        npc.setStatus(WorldNpcStatus.ALIVE);
        npc.setDisposition(WorldDisposition.FRIENDLY);
        npc.setSecret("Works for the Ring");
        npc.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        npc = npcs.save(npc);

        WorldRelationship rel = new WorldRelationship();
        rel.setCampaign(c);
        rel.setKind(RelationshipKind.MEMBER_OF);
        rel.setFromType("WORLD_NPC");
        rel.setFromId(npc.getId());
        rel.setToType("FACTION");
        rel.setToId(f.getId());
        rel.setDirected(true);
        rel.setKnowledge(RelationshipKnowledge.PUBLIC);
        rel.setStatus(RelationshipStatus.ACTIVE);
        relationships.save(rel);

        FactionClock clock = new FactionClock();
        clock.setCampaign(c);
        clock.setFaction(f);
        clock.setTitle("Ring influence");
        clock.setSegments(6);
        clock.setFilled(2);
        clocks.save(clock);

        assertThat(npcs.findByCampaignIdOrderByNameAscIdAsc(c.getId())).hasSize(1);
        assertThat(relationships.findByCampaignIdOrderBySortOrderAscIdAsc(c.getId())).hasSize(1);
        assertThat(clocks.findByFactionIdOrderBySortOrderAscIdAsc(f.getId())).hasSize(1);
    }
}
```

- [ ] **Step 6: Run tests**

Run: `./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-world-graph -Dtest=FlywayMigrationTest,WorldPersistenceTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V12__add_world_graph.sql \
  src/main/java/dev/hendrikhoemberg/dmhelper/world \
  src/test/java/dev/hendrikhoemberg/dmhelper/world \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java
git commit -m "$(cat <<'EOF'
feat(world): add V12 world graph tables and entities

Introduce campaign-scoped NPCs, locations, factions, relationships,
and faction clocks as the first P3 world-graph foundation.
EOF
)"
```

---

### Task 2: Content types, WorldService, package keys, cascade cleanup

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/world/service/WorldService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/world/service/WorldLocationCycleValidator.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/world/service/WorldReferenceCleaner.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/world/service/WorldServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignCascadeDeleteTest.java`

**Interfaces:**
- Consumes: repositories from Task 1, `CampaignPackageKeyService`, `CampaignRepository`
- Produces:
  - `WorldService.createNpc(campaignId, NpcCommand) → WorldNpc`
  - `WorldService.updateNpc(...)`, `deleteNpc(...)` (+ location/faction/relationship/clock analogs)
  - `WorldLocationCycleValidator.assertNoCycle(campaignId, locationId, parentId)`
  - On delete: `packageKeys.deleteBindings(...)` and null-out FKs from scene participants

- [ ] **Step 1: Extend content types**

```java
// Append before SOURCE_ANNOTATION or at end of enum:
WORLD_NPC,
WORLD_LOCATION,
FACTION,
WORLD_RELATIONSHIP,
FACTION_CLOCK,
```

- [ ] **Step 2: Failing service test for create + parent cycle rejection**

```java
@Test
void rejectsLocationParentCycle() {
    Campaign c = campaignService.create("Cycle", null);
    WorldLocation a = worldService.createLocation(c.getId(), new LocationCommand(
            "A", LocationKind.REGION, null, null, null, null, null, null, null, List.of(), List.of(), null));
    WorldLocation b = worldService.createLocation(c.getId(), new LocationCommand(
            "B", LocationKind.SITE, a.getId(), null, null, null, null, null, null, List.of(), List.of(), null));
    assertThatThrownBy(() -> worldService.updateLocation(c.getId(), a.getId(), new LocationCommand(
            "A", LocationKind.REGION, b.getId(), null, null, null, null, null, null, List.of(), List.of(), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cycle");
}

@Test
void deleteNpcRemovesPackageKeyBinding() {
    Campaign c = campaignService.create("Keys", null);
    WorldNpc npc = worldService.createNpc(c.getId(), minimalNpc("Mira"));
    String key = packageKeys.getOrCreate(c.getId(), CampaignContentType.WORLD_NPC, npc.getId(), npc.getName());
    assertThat(key).isNotBlank();
    worldService.deleteNpc(c.getId(), npc.getId());
    assertThat(packageKeys.find(c.getId(), CampaignContentType.WORLD_NPC, npc.getId())).isEmpty();
}
```

- [ ] **Step 3: Run tests — expect FAIL** (service missing)

- [ ] **Step 4: Implement WorldService**

Follow `QuestService` patterns:

```java
@Service
@Transactional
public class WorldService {
    public record NpcCommand(
            String name, String role, WorldDisposition disposition,
            UUID factionId, UUID locationId, UUID noteId, UUID statblockId,
            String appearance, String voice, String motivation, String secret,
            String inventoryText, WorldNpcStatus status, String tags, String sourceLocator) {}

    public record LocationCommand(
            String name, LocationKind kind, UUID parentLocationId, UUID mapId, String mapRegionKey,
            UUID noteId, String summary, String services, String secrets,
            List<UUID> encounterIds, List<UUID> travelLocationIds,
            String tags, String sourceLocator) {}

    public record FactionCommand(
            String name, String goals, String resources, String reputationNotes,
            UUID noteId, String tags, String sourceLocator) {}

    public record RelationshipCommand(
            RelationshipKind kind, String fromType, UUID fromId, String toType, UUID toId,
            boolean directed, RelationshipKnowledge knowledge, RelationshipStatus status,
            String notes, String sourceLocator, int sortOrder) {}

    public record ClockCommand(
            String title, int segments, int filled, UUID objectiveId, UUID sceneId,
            String notes, String sourceLocator, int sortOrder) {}

    // create/update/delete for each; validate campaign ownership on every FK;
    // reject self-relationship; validate clock filled in [0, segments];
    // on delete call packageKeys.deleteBindings for the entity type.
}
```

`WorldLocationCycleValidator`: walk parent chain; if `locationId` reappears → throw.

`WorldReferenceCleaner`: when deleting NPC, clear `scene_participant.world_npc_id`; when deleting faction, clocks cascade via FK.

- [ ] **Step 5: Cascade delete test**

In `CampaignCascadeDeleteTest`:

```java
@Test
void deletingCampaignCascadesWorldGraph() {
    Campaign c = campaignService.create("WorldCascade", null);
    Faction f = worldService.createFaction(c.getId(), new FactionCommand("F", null, null, null, null, null, null));
    worldService.createNpc(c.getId(), /* faction f */ ...);
    UUID campaignId = c.getId();
    campaignService.delete(campaignId);
    assertThat(factionRepository.findByCampaignIdOrderByNameAscIdAsc(campaignId)).isEmpty();
}
```

- [ ] **Step 6: Run tests — expect PASS**

Run: `./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-world-graph -Dtest=WorldServiceTest,CampaignCascadeDeleteTest test`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/world/service \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/world/service \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignCascadeDeleteTest.java
git commit -m "$(cat <<'EOF'
feat(world): add WorldService CRUD with package keys and cycle checks
EOF
)"
```

---

### Task 3: Package DTOs, schema, assembler

**Files:**
- Modify: `CampaignManifestV2.java`, `CampaignManifestAssembler.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Test: extend `CampaignDtoSchemaCompatibilityTest` / schema example tests

**Interfaces:**
- Produces: optional `worldNpcs`, `worldLocations`, `factions`, `worldRelationships`, `factionClocks` on manifest; assembler setters default missing to empty lists in `build()`

- [ ] **Step 1: Failing schema fixture**

Add `src/test/resources/campaigns/v2/world-graph.dmcampaign/manifest.json` with one of each world entity and valid keys. Assert it validates once schema exists; first commit the fixture with world arrays and expect schema rejection until `$defs` added.

Minimal world fragment:

```json
"worldNpcs": [{
  "key": "npc-mira",
  "name": "Mira",
  "role": "Harbor contact",
  "disposition": "FRIENDLY",
  "factionRef": { "scope": "PACKAGE", "type": "FACTION", "key": "faction-iron-ring" },
  "locationRef": { "scope": "PACKAGE", "type": "WORLD_LOCATION", "key": "loc-harbor" },
  "status": "ALIVE",
  "secret": "Works for the Ring",
  "tags": ["contact"],
  "createdAt": "2026-01-01T00:00:00Z"
}],
"worldLocations": [{
  "key": "loc-harbor",
  "name": "Salt Harbor",
  "kind": "SETTLEMENT",
  "summary": "Busy docks",
  "secrets": "Smuggler tunnels",
  "travelLocationRefs": [],
  "encounterRefs": [],
  "createdAt": "2026-01-01T00:00:00Z"
}],
"factions": [{
  "key": "faction-iron-ring",
  "name": "Iron Ring",
  "goals": "Control docks",
  "createdAt": "2026-01-01T00:00:00Z"
}],
"worldRelationships": [{
  "key": "rel-mira-ring",
  "kind": "MEMBER_OF",
  "fromRef": { "scope": "PACKAGE", "type": "WORLD_NPC", "key": "npc-mira" },
  "toRef": { "scope": "PACKAGE", "type": "FACTION", "key": "faction-iron-ring" },
  "directed": true,
  "knowledge": "PUBLIC",
  "status": "ACTIVE",
  "sortOrder": 0
}],
"factionClocks": [{
  "key": "clock-ring-influence",
  "factionRef": { "scope": "PACKAGE", "type": "FACTION", "key": "faction-iron-ring" },
  "title": "Dock control",
  "segments": 6,
  "filled": 2,
  "sortOrder": 0
}]
```

Copy structure from `minimal.dmcampaign.json` for required root arrays, then add the world arrays.

- [ ] **Step 2: Extend Java DTOs and assembler**

Add records and five list fields to `CampaignManifestV2` and matching assembler fields. In `build()`, default null world lists to `List.of()` like quests. **Update every test/manual constructor call** of `CampaignManifestV2` that the compiler flags.

- [ ] **Step 3: Schema `$defs` and optional root properties**

Add to root `properties` (do **not** add to `required`):

```json
"worldNpcs": { "type": "array", "items": { "$ref": "#/$defs/worldNpc" } },
"worldLocations": { "type": "array", "items": { "$ref": "#/$defs/worldLocation" } },
"factions": { "type": "array", "items": { "$ref": "#/$defs/faction" } },
"worldRelationships": { "type": "array", "items": { "$ref": "#/$defs/worldRelationship" } },
"factionClocks": { "type": "array", "items": { "$ref": "#/$defs/factionClock" } }
```

`$defs` must set `additionalProperties: false`, require `key` (+ name/title/createdAt as appropriate), enum-constrain disposition/kind/status/knowledge, and `$ref` content references through existing `#/$defs/contentReference`.

- [ ] **Step 4: Run DTO/schema compatibility tests**

Run: `./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-world-graph -Dtest=CampaignDtoSchemaCompatibilityTest,CampaignSchemaValidatorTest test`

Expected: PASS for existing packages; world fixture validates.

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(package): add world graph arrays to v2 manifest and schema
EOF
)"
```

---

### Task 4: WorldSectionAdapter + semantic validation + snapshot

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/world/packagev2/WorldSectionAdapter.java`
- Modify: `CampaignManifestV2SemanticValidator.java`
- Modify: `CampaignSemanticSnapshotService.java` (ownership queries)
- Modify: `ImportProblemCodes.java`, `src/main/resources/agent/validation-error-catalog.json`
- Test: `WorldSectionAdapterTest.java`, semantic validator tests for cycle/self-edge/clock range

**Interfaces:**
- Consumes: DTOs from Task 3, entities from Task 1
- Produces: `sectionName() = "World"`, `order() = 850`, export/import registration for all five content types

- [ ] **Step 1: Failing adapter round-trip test**

```java
@Test
void exportImportPreservesWorldGraph() {
    // create campaign + faction + location + npc + relationship + clock via WorldService
    // export via CampaignExportCoordinator
    // delete campaign / import package
    // assert names, secret, filled clock segments, relationship endpoints by package key
}
```

- [ ] **Step 2: Implement adapter**

Export pattern (mirror quest adapter):

```java
@Component
public class WorldSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {
    @Override public String sectionName() { return "World"; }
    @Override public int order() { return 850; }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        // factions, locations (parents as package refs), npcs, relationships, clocks
        // use context.key(type, id, name) and context.packageRef(...)
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        // 1) factions register FACTION
        // 2) locations without parent, then defer parent links OR topological order
        // 3) npcs
        // 4) relationships (require from/to already registered)
        // 5) clocks — defer objective/scene via context.defer if present
        // context.register + packageKeys.bindImported via import context helpers already used by other adapters
    }
}
```

Location parent cycles: detect during semantic validation (Task 4 semantic) and during service updates; on import, if cycle present, fail with `WORLD_LOCATION_CYCLE` before persist (semantic phase).

- [ ] **Step 3: Semantic validator rules**

In `CampaignManifestV2SemanticValidator`:

1. Index keys for new arrays.
2. Resolve all world ContentReferences; wrong type → `INVALID_WORLD_REFERENCE_TYPE`.
3. Location parent graph cycle → `WORLD_LOCATION_CYCLE`.
4. Relationship `fromRef.key == toRef.key && same type` → `WORLD_RELATIONSHIP_SELF`.
5. Clock `filled > segments || segments < 1` → `FACTION_CLOCK_RANGE` (also schema min/max).
6. Update role→type mapping:

```java
// OLD (quest/scene roles):
case "NPC", "LOCATION", "FACTION" -> NOTE;

// NEW: accept either world types or legacy NOTE depending on targetRef.type
// Prefer validating targetRef.type ∈ allowed set for role:
// NPC -> {WORLD_NPC, NOTE}
// LOCATION -> {WORLD_LOCATION, NOTE}
// FACTION -> {FACTION, NOTE}
```

- [ ] **Step 4: Snapshot ownership queries**

Add rows like:

```java
new OwnershipQuery(CampaignContentType.WORLD_NPC, WorldNpc.class, "campaign.id"),
new OwnershipQuery(CampaignContentType.WORLD_LOCATION, WorldLocation.class, "campaign.id"),
new OwnershipQuery(CampaignContentType.FACTION, Faction.class, "campaign.id"),
new OwnershipQuery(CampaignContentType.WORLD_RELATIONSHIP, WorldRelationship.class, "campaign.id"),
new OwnershipQuery(CampaignContentType.FACTION_CLOCK, FactionClock.class, "campaign.id"),
```

Ensure semantic compare path includes the new manifest arrays (if comparator is structural on exported JSON/snapshot maps, export coverage is enough; if field-listed, extend the list).

- [ ] **Step 5: Register problem codes + catalog entries**

```java
public static final String WORLD_LOCATION_CYCLE = "WORLD_LOCATION_CYCLE";
public static final String WORLD_RELATIONSHIP_SELF = "WORLD_RELATIONSHIP_SELF";
public static final String FACTION_CLOCK_RANGE = "FACTION_CLOCK_RANGE";
public static final String INVALID_WORLD_REFERENCE_TYPE = "INVALID_WORLD_REFERENCE_TYPE";
```

Catalog JSON entry example:

```json
{
  "code": "WORLD_LOCATION_CYCLE",
  "severity": "ERROR",
  "meaning": "A world location parent chain contains a cycle.",
  "repair": "Remove or retarget parentLocationRef so each location has an acyclic parent chain."
}
```

- [ ] **Step 6: Run adapter + semantic + package tests**

Run: `./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-world-graph -Dtest=WorldSectionAdapterTest,CampaignManifestV2SemanticValidator* test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(package): world graph export/import adapter and semantic checks
EOF
)"
```

---

### Task 5: Quest and scene integration

**Files:**
- Modify: `QuestService.java` (GIVER and role target type rules)
- Modify: `adventure/data/SceneParticipant.java` (+ service/templates)
- Modify: `AdventureSectionAdapter.java` for `worldNpcRef` on participants
- Modify: schema `sceneParticipant` `$defs` if needed
- Test: quest link tests, adventure adapter tests

**Interfaces:**
- Consumes: world entity IDs/types
- Produces: quest links and scene participants that resolve to world package keys

- [ ] **Step 1: Failing tests**

```java
@Test
void questNpcRoleAcceptsWorldNpcTarget() {
    // create world NPC; add quest link role=NPC targetType=WORLD_NPC targetId=npcId
    // export/import; link still resolves
}

@Test
void sceneParticipantExportsWorldNpcRef() {
    // participant with worldNpc set exports package ref type WORLD_NPC
}
```

- [ ] **Step 2: Relax QuestService target rules**

```java
// GIVER: NOTE | STATBLOCK | WORLD_NPC
// Role NPC: WORLD_NPC | NOTE
// Role LOCATION: WORLD_LOCATION | NOTE
// Role FACTION: FACTION | NOTE
```

Validate entity exists in campaign when scope is PACKAGE.

- [ ] **Step 3: SceneParticipant**

Add:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "world_npc_id")
private WorldNpc worldNpc;
```

Export/import via `ContentReference worldNpcRef` on participant DTO (optional). Keep existing `noteRef`.

- [ ] **Step 4: Run integration tests — PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(world): resolve quest and scene links to world entities
EOF
)"
```

---

### Task 6: DM UI, navigation, destinations, search

**Files:**
- Create: `world/web/WorldController.java` (+ optional `WorldApiController`)
- Create: `src/main/resources/templates/world/*.html` (list/detail/form fragments for NPC, location, faction; relationship and clock fragments)
- Modify: `templates/fragments/_appnav.html`
- Modify: `ContentDestinationRegistry.java`, `CommandPaletteService.java`
- Modify: route contract tests
- Test: `WorldControllerTest.java`, `ContentDestinationRouteContractTest.java`

**Interfaces:**
- Produces: keyboard-reachable list/detail pages; palette results with working destinations

- [ ] **Step 1: Destination registry failing route test**

```java
// ContentDestinationRegistry.CampaignType += WORLD_NPC, WORLD_LOCATION, FACTION
// campaign(WORLD_NPC, campaignId, npcId, null) -> /campaigns/{id}/world/npcs/{npcId}
```

- [ ] **Step 2: Implement routes**

```java
@Controller
public class WorldController {
    @GetMapping("/campaigns/{campaignId}/world/npcs")
    public String listNpcs(@PathVariable UUID campaignId, Model model) { ... }

    @GetMapping("/campaigns/{campaignId}/world/npcs/{npcId}")
    public String npcDetail(...) { ... }

    // create GET + POST, edit GET + PUT, DELETE
    // same for locations and factions
    // POST relationships; POST/PUT clocks under faction detail
}
```

Templates: reuse quest list/detail CSS classes (`card`, `btn`, form layout). Show DM-only secret fields only on DM pages (default app is DM-gated).

Nav World group:

```html
<a class="appnav-link" data-icon="&#x1F464;" data-label="NPCs"
   th:href="@{/campaigns/{id}/world/npcs(id=${campaignId})}"><span class="appnav-text">NPCs</span></a>
<a class="appnav-link" data-icon="&#x1F3D9;" data-label="Locations"
   th:href="@{/campaigns/{id}/world/locations(id=${campaignId})}"><span class="appnav-text">Locations</span></a>
<a class="appnav-link" data-icon="&#x1F3F7;" data-label="Factions"
   th:href="@{/campaigns/{id}/world/factions(id=${campaignId})}"><span class="appnav-text">Factions</span></a>
```

- [ ] **Step 3: Command palette**

In `CommandPaletteService`, after notes search, query world repositories by name/tags and emit results with destinations. Include type label (`NPC`, `Location`, `Faction`) in subtitle.

- [ ] **Step 4: Controller tests**

MockMvc: list returns 200; create redirects to detail; delete redirects to list; PIN interceptor still applies (same as other campaign routes).

- [ ] **Step 5: Run UI/route tests — PASS**

- [ ] **Step 6: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(world): DM UI, nav, destinations, and palette search
EOF
)"
```

---

### Task 7: Flagship fixture, docs, capabilities, player safety

**Files:**
- Create/update: `src/test/resources/campaigns/v2/world-graph.dmcampaign/` complete package
- Optionally enrich: `feature-complete.dmcampaign` with one NPC/location/faction if that fixture is the full-field gate
- Modify: `docs/campaign-format-v2.md`, `docs/campaign-capabilities.md`, `docs/product/known-limitations.md`, `docs/authoring/README.md`, agent playbook mention
- Modify: `src/main/resources/agent/capability-manifest.json`
- Test: package round-trip test for world fixture; player-safety test that secret fields never appear on `/player/**` payloads

- [ ] **Step 1: Round-trip test**

```java
@Test
void worldGraphFixtureRoundTrips() throws Exception {
    // dry-run → import → export → import → semantic compare equal
}
```

- [ ] **Step 2: Capability updates**

```json
{
  "id": "world.graph",
  "name": "World graph",
  "status": "SUPPORTED",
  "deliveryItem": 11,
  "notes": "NPCs, locations, factions, relationships, faction clocks; travel/fog/audio/players remain deferred P3 slices"
}
```

Markdown matrix row: `World graph | SUPPORTED | ...`

Keep travel/fog/player `UNSUPPORTED`.

- [ ] **Step 3: Docs**

Document package arrays, keys, reference rules, and that notes remain complementary. Update conversion playbook step “Build the campaign graph” to allow world entity keys. Add known-limitation notes only for deferred P3 slices.

- [ ] **Step 4: Player safety**

If any live/player DTO currently dumps campaign notes indiscriminately, ensure world secrets are not added. Prefer an explicit assertion:

```java
@Test
void playerPayloadOmitsWorldSecrets() {
    // create NPC with secret; fetch player view / websocket snapshot if applicable
    // assert response body does not contain secret string
}
```

World pages are DM routes (PIN-gated); no player pages required for this slice.

- [ ] **Step 5: Full module test run**

Run: `./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-world-graph test`

Expected: all tests PASS (fix any pre-existing failures unrelated to this work before claiming success).

- [ ] **Step 6: Do not mark delivery item 11 fully implemented**

Update master design only if you add a note under item 11, e.g. `PLANNED (world graph slice done)` — optional. Capability row is the honest product signal.

- [ ] **Step 7: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs(world): capability matrix, authoring docs, and world-graph fixture

Mark world.graph SUPPORTED while leaving remaining P3 slices deferred.
EOF
)"
```

---

## Self-review checklist

### 1. Spec coverage (§14.3)

| Requirement | Task |
|---|---|
| NPC fields (role, disposition, faction, location, appearance, voice, motivation, secret, statblock, inventory, status) | 1–2, 3 |
| Location (parent, map/region, occupants, services, encounters, secrets, travel links) | 1–2, 3 (occupants via `world_npc.location_id`; travel as adjacent refs only) |
| Faction (goals, resources, allies/enemies, reputation, clocks, members) | allies/enemies via relationships; members via NPC.faction + MEMBER_OF edges; clocks Task 1–2 |
| Relationship typed edge, direction, public/secret, status | 1–4 |
| Complement notes, not replace | Global constraints; optional noteRef |
| Package keys + validation | 2–4 |
| Search destinations | 6 |
| Player-safe secrets | 7 |

### 2. Placeholder scan

No TBD steps; concrete SQL, DTOs, routes, tests, and commit messages included.

### 3. Type consistency

- Content types: `WORLD_NPC`, `WORLD_LOCATION`, `FACTION`, `WORLD_RELATIONSHIP`, `FACTION_CLOCK`
- Root arrays: `worldNpcs`, `worldLocations`, `factions`, `worldRelationships`, `factionClocks`
- Adapter order: `850`
- Migration: `V12`

### 4. Out of scope reminder

Travel weather, fog gameplay, audio, interactive players, and rollable-table engines are **not** part of this plan. After merge, the next P3 plan should pick one remaining capability row (recommend: **rollable tables** or **travel** depending on product priority).

---

## Execution handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-17-p3-world-graph.md`.**

**Two execution options:**

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration  
   **REQUIRED SUB-SKILL:** superpowers:subagent-driven-development

2. **Inline Execution** — execute tasks in this session with executing-plans and checkpoints  
   **REQUIRED SUB-SKILL:** superpowers:executing-plans

**Which approach?**
