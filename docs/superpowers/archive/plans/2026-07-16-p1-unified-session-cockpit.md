# P1 Unified Session Cockpit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `/campaigns/{campaignId}/session` first-map redirect with a durable, keyboard-operable session cockpit that coordinates the existing scene, map, encounter, session-plan, presentation, party, calendar, dice, handout, and quick-note modules and produces a reviewable deterministic session log.

**Architecture:** Add one campaign-owned `CampaignSession` aggregate for lifecycle, attendance, workspace-map selection, presentation selection, and scene visits. A read-only `SessionWorkspaceService` composes existing module state for the cockpit; existing `BattleMap`, encounter tracker, `AdventureService`, and `TablePresentationService` remain the sole runtime implementations. Persist only coordination state, add it as a version-2 package section, and create the final historical record as a normal `SESSION_LOG` note.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA, Flyway, Thymeleaf, HTMX, Alpine.js, Konva, Jackson 3, H2, JUnit 5, AssertJ, Mockito, Playwright 1.54, Maven Wrapper.

## Global Constraints

- The DM must be able to reach or operate any prepared element in no more than two deliberate actions from the session cockpit.
- Structured fields own information the app queries, validates, filters, reveals, calculates, or transitions; Markdown remains the reading and free-form editing surface.
- Current scene, active encounter, workspace map, player presentation, and session plan remain independent state and are only visibly coordinated.
- The cockpit reuses `BattleMap`, `encounter/_tracker`, `AdventureService`, and `TablePresentationService`; it must not add a second map, encounter, initiative, or player-projection implementation.
- Runtime features work without internet access; no frontend build chain, runtime CDN, new JavaScript framework, or remote service is introduced.
- Player-safe projection remains enforced on the server. No scene body, private note, hidden token, DM-only handout, or package provenance is added to a player payload.
- Every user-initiated mutation uses `window.dmRequest` and `window.reportActionFailure`; optimistic state is restored or retained after failure and retry is available.
- All new DM page and API routes remain covered by the existing PIN interceptor.
- Persistent schema changes use Flyway; Hibernate continues to validate rather than evolve production schema implicitly.
- Package format remains exactly version `2`; this plan extends the not-yet-released closed contract rather than introducing version 3.
- An open session and its coordination state survive refresh, restart, default export, and import. A completed session is preserved as a `SESSION_LOG` note.
- `Scene.body` is labeled **Scene notes** in this slice. Structured read-aloud, checks, secrets, transitions, quests, and objectives remain delivery item 6 and are not inferred from Markdown.
- Tests run with `-DargLine=-Duser.home=/tmp/dmhelper-session-cockpit` and fail on browser console errors, page errors, malformed requests, and unexpected 4xx/5xx responses.

---

## Audit Basis and Current Implementation Status

The audit used the approved master spec, repository tree at `fa6501c`, the five existing implementation plans, recent commits, production code, checked-in fixtures, and the Maven suite. A sandboxed invocation was invalid because the sandbox blocked Mockito self-attachment and embedded-server sockets; the same command outside the sandbox passed all 736 tests with zero failures and zero errors.

| Master-spec area | Evidence in the current tree | Status before this plan |
|---|---|---|
| Delivery item 1 / P0 reliability | `QuickNoteService`, `ContentDestinationRegistry`, `BrowserFailureCollector`, `dm-request.js`, correlated error handling, retry browser tests, safe package reader, and estimate labeling; commits `9780d15` through `fa6501c` | `SUPPORTED` at the documented P0 checkpoint |
| Delivery item 2 / v1 contract repair | Closed v1 schema, shared validation pipeline, semantic validator, fixtures, schema/controller tests | `SUPPORTED` |
| Delivery item 3 / package v2 foundation | ZIP/JSON readers, stable keys, typed catalog, preview/confirmation, staged assets, v1 migration, closed v2 schema | `SUPPORTED` |
| Delivery item 4 / complete round-trip | Ordered module adapters, three flagship fixtures, semantic snapshots, default-complete histories, explicit log/dice opt-outs | `SUPPORTED` for all currently persisted state |
| Workstream C / session cockpit | `SessionController` redirects to the first sorted map; `maps/battle.html` is a useful map/tracker shell but has no durable lifecycle, end draft, start selection contract, or restart-safe presentation | `UNSUPPORTED`; this plan is the next delivery item |
| Workstream D / structured adventures | Adventure/chapter/scene hierarchy, editorial stepping, status, map/encounter/statblock/handout links exist; typed transitions, scene sections, quests, and objectives do not | `PARTIAL`, deferred to item 6 |
| Workstream E / custom rules | Bundled SRD content and campaign custom statblocks exist; campaign-scoped spells/items/classes/species/backgrounds/feats/rules with provenance do not | `PARTIAL`, deferred to item 7 |
| Workstream F / character management | Derived sheets, resources, spells, rests, party passives, HP state exist; full creation choices, actions, inventory, death/exhaustion/inspiration, and advancement remain incomplete | `PARTIAL` |
| Workstream G / encounters | Tracker, initiative, HP, conditions, concentration, special actions, logs, undo, map/party prefill work; waves, rewards, consequence drafts, and authoritative difficulty data remain open | `STRONG PARTIAL` |
| Workstream H / maps | Editor, battle map, tokens, image layers, measurement, AoE, and player projection work; calibration, regions, runtime terrain semantics, and fog/reveal remain open | `STRONG PARTIAL` |
| Workstream I / notes/search/world | Typed notes, wiki links/backlinks, quick notes, shared destinations, and ranked/capped palette exist; immutable internal note keys, typed world entities, and saved session favorites do not | `PARTIAL` |
| Workstreams J–K / logistics and player view | Treasury, ledger, custom calendar, timeline, curtain/map/handout player projection exist; reward drafts, travel, richer presentation types, preview/device status remain open | `PARTIAL` |
| Workstream L / architecture | Package adapter decomposition, typed campaign settings, destination registry, Flyway migrations exist; a unified content/reference registry and all typed internal JSON are not complete | `PARTIAL` |
| Documentation and agent SDK | v1/v2 format docs, capability matrix, schemas, catalog, and fixtures exist; audience-split DM/session manual, generated error catalog, machine-readable capability manifest, and full agent guide remain open | `PARTIAL` |

## Scope Boundary

This is master-spec delivery item 5 and completes Workstream C's baseline. It includes durable coordination state, cockpit composition, start/resume/pause/end lifecycle, deterministic end-session drafting, version-2 recovery, keyboard support, browser verification, and honest documentation.

It does not add structured scene fields or transitions, quest/objective entities, encounter waves/rewards, custom non-statblock content, map calibration/fog, new player interaction, or generative recap text. The end draft reports only facts that current persisted data can prove.

## Approaches Considered

1. **Recommended — durable coordination aggregate plus composed workspace.** One `CampaignSession` owns only cross-module state; the workspace reads existing modules and embeds the existing battle/tracker runtime. This satisfies restart/import recovery without duplicating domain logic.
2. **Expand `maps/battle.html` with URL-local panels only.** This is faster visually, but start/resume/pause/end, attendance, selected presentation, scene history, restart, and export/import would remain undefined.
3. **Create a second session runtime with copied map and encounter state.** This gives a clean page initially but creates conflicting sources of truth and directly violates master-spec section 8.1.

## Contract Decisions Locked by This Plan

- `CampaignSession` is a reusable one-to-one campaign aggregate. Its statuses are exactly `IDLE`, `RUNNING`, `PAUSED`, and `REVIEW`.
- `IDLE` retains only its immutable database/package identity. Starting clears prior attendees, visits, draft, and presentation refs before taking a new snapshot.
- Opening the cockpit is read-only. `POST /api/v1/campaigns/{campaignId}/session/start` starts; pause, resume, review, cancel-review, and complete are explicit mutations.
- Initial workspace-map priority is: active encounter map, current scene map, first resolvable `SCENE`/`MAP` link in the latest session plan, explicit campaign map choice, then no map.
- Once status is not `IDLE`, the stored workspace map wins on refresh/restart. Mismatches with current scene or active encounter appear as one-click **Switch** actions; they do not silently change the workspace or player view.
- A session plan remains the latest `SESSION_PLAN` note. `SessionPlanService` parses wiki links from the Markdown body in document order and resolves them through existing repositories and `ContentDestinationRegistry`; no plan entity or Markdown semantics beyond explicit wiki links are invented.
- Presentation mode is exactly `CURTAIN`, `MAP`, or `HANDOUT`. Presentation mutations persist the safe typed reference before broadcasting. On restart, the most recently updated non-idle session restores the global table state; missing/deleted refs restore a curtain.
- Attendance starts as the campaign's active party members but is stored independently for this session and remains editable.
- Scene visits are recorded only while `RUNNING`; selecting the same scene is idempotent. A visit records `visitedAt`, and changing that scene to `DONE` records `completedAt`.
- End review changes status to `REVIEW` and persists a generated Markdown draft. The DM may edit the full Markdown. Completion creates one `SESSION_LOG` note, curtains the player view, and resets the aggregate to `IDLE` in one transaction.
- The deterministic draft includes only: start/end time, start/current in-game date, attendance, recorded scenes, encounters with an `ENCOUNTER_ENDED` log during the session, defeated combatant names, absolute summed `DAMAGE` payload amounts, ledger rows created during the session, unresolved quick notes created during the session, free-form recap, and next-session hooks.
- The v2 manifest adds a nullable `session` section with a required stable key when non-null. `SESSION` and `SESSION_SCENE_VISIT` become package content types. Default export includes open coordination state; `IDLE` exports as `null`.

## File Structure

### Files created

- `src/main/resources/db/migration/V4__add_campaign_session.sql` — one-to-one session state, attendance, scene visits, indexes, and foreign keys.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSession.java` — lifecycle and coordination aggregate.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionSceneVisit.java` — ordered/idempotent session-scene evidence.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSessionRepository.java` — campaign lookup and most-recent open-session restoration query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionSceneVisitRepository.java` — session-ordered visit queries and cleanup.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleService.java` — start/pause/resume/review/complete transitions and attendance.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java` — read-only composition and map-selection policy.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionPlanService.java` — ordered explicit wiki-link beats.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java` — deterministic local Markdown draft.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionActivityRecorder.java` — optional scene visit/completion recording without coupling adventure code to session persistence classes.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java` — real cockpit GET and map-play redirect target.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java` — checked lifecycle, workspace, scene, attendance, presentation, and draft endpoints.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/packagev2/SessionSectionAdapter.java` — order-1150 open-session export/import.
- `src/main/resources/templates/session/cockpit.html` — unified three-surface cockpit page.
- `src/main/resources/templates/session/_story-rail.html` — current scene, editorial neighbors, links, notes, and scene quick notes.
- `src/main/resources/templates/session/_encounter-rail.html` — active/planned encounters and the existing tracker fragment.
- `src/main/resources/templates/session/_session-plan.html` — ordered prepared beats.
- `src/main/resources/templates/session/_lifecycle-dialog.html` — start/attendance, pause, and end-review controls.
- `src/main/resources/templates/session/_empty-table.html` — useful no-map state and chooser.
- `src/main/resources/static/js/session-cockpit.js` — page coordinator, keyboard actions, checked requests, and cross-rail refresh.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSessionRepositoryTest.java` — V4/JPA one-to-one and visit ordering.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleServiceTest.java` — transition, attendance, and reset contracts.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceServiceTest.java` — complete selection-priority matrix.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionPlanServiceTest.java` — document-order explicit-link resolution.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java` — deterministic evidence-only Markdown.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionControllerTest.java` — cockpit rendering and map-play redirect.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiControllerTest.java` — lifecycle/status/error web contract.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/packagev2/SessionSectionAdapterTest.java` — stable-reference import/export.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitSecurityTest.java` — PIN and player-payload exclusions.
- `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java` — no copied tracker/map implementation and accessible controls.

### Files modified

- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java` — redirect `/play` to the cockpit with an explicit map selection.
- `src/main/resources/static/css/cockpit.css` — responsive story/table/encounter layout and focus states.
- `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java` — call `SessionActivityRecorder` after current-scene/status mutations.
- `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/CombatLogEntryRepository.java` — time-bounded campaign query for draft evidence.
- `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/data/LedgerEntryRepository.java` — time-bounded campaign query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java` — time-bounded unresolved query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/live/TablePresentationService.java` — persist/restore presentation through session state and throw on invalid refs.
- `src/main/java/dev/hendrikhoemberg/dmhelper/live/web/TablePresentationController.java` — campaign-scoped mutation route; retain read-only player state route.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java` — nullable typed session section.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java` — `SESSION`, `SESSION_SCENE_VISIT`.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyService.java` — delete bindings for transient visit rows when a session completes.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyRepository.java` — exact entity-binding delete query.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignManifestAssembler.java` — one-writer session slot.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java` — session status/reference/date invariants.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignEntityCounts.java` — session/visit counts.
- `src/main/resources/schemas/campaign-format-v2.schema.json` — closed session definition and refs.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshot.java` — open-session semantic state.
- `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotService.java` — UUID-independent session snapshot.
- `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json` — running session with attendance, workspace, curtain, and visits.
- `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json` — paused session-plan/current-scene/map coordination example.
- `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java` — real start/resume/run/end/reimport browser flow.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java` — V4 fresh migration.
- `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java` — V3-to-V4 data-preserving upgrade.
- `docs/campaign-format-v2.md` — open-session section and recovery semantics.
- `docs/campaign-capabilities.md` — mark the baseline cockpit supported only after verification.
- `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md` — mark delivery item 5 complete only after the completion gate passes.

### Files deleted after cutover

- `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/SessionController.java` — replaced by the session-owned controller.
- `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/SessionControllerTest.java` — replaced by the cockpit contract test.
- `src/main/resources/templates/maps/battle.html` — no second runtime page remains after its tested behavior is embedded in `session/cockpit.html`.

---

### Task 1: Persist one typed campaign-session aggregate

**Files:**
- Create: `src/main/resources/db/migration/V4__add_campaign_session.sql`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSession.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionSceneVisit.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSessionRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/data/SessionSceneVisitRepository.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/data/CampaignSessionRepositoryTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java`

**Interfaces:**
- Produces: `CampaignSession.Status { IDLE, RUNNING, PAUSED, REVIEW }`.
- Produces: `CampaignSession.PresentationMode { CURTAIN, MAP, HANDOUT }`.
- Produces: `Optional<CampaignSession> CampaignSessionRepository.findByCampaignId(UUID campaignId)`.
- Produces: `Optional<CampaignSession> CampaignSessionRepository.findFirstByStatusNotOrderByUpdatedAtDesc(Status status)`.
- Produces: `List<SessionSceneVisit> SessionSceneVisitRepository.findBySessionIdOrderByVisitedAtAscIdAsc(UUID sessionId)`.
- Constraint: exactly one `campaign_session` row per campaign and one visit row per `(session_id, scene_id)`.

- [ ] **Step 1: Write the failing repository and migration tests**

```java
@DataJpaTest
class CampaignSessionRepositoryTest {
    @Autowired CampaignRepository campaigns;
    @Autowired CampaignSessionRepository sessions;
    @Autowired SessionSceneVisitRepository visits;

    @Test
    void storesOneSessionPerCampaignAndOrdersDistinctSceneVisits() {
        Campaign campaign = campaigns.save(campaign("Ashes of Dawn"));
        CampaignSession session = sessions.save(CampaignSession.idle(campaign));

        assertThatThrownBy(() -> sessions.saveAndFlush(CampaignSession.idle(campaign)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(session.getStatus()).isEqualTo(CampaignSession.Status.IDLE);
        assertThat(session.getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
    }
}
```

Extend the Flyway tests with exact table/constraint assertions:

```java
assertThat(metadata.tableExists("CAMPAIGN_SESSION")).isTrue();
assertThat(metadata.tableExists("CAMPAIGN_SESSION_ATTENDEE")).isTrue();
assertThat(metadata.tableExists("SESSION_SCENE_VISIT")).isTrue();
assertThat(queryForInt("select count(*) from campaign where name='Legacy Campaign'")).isEqualTo(1);
```

- [ ] **Step 2: Run the tests and verify the state model is absent**

Run:

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=CampaignSessionRepositoryTest,FlywayMigrationTest,FlywayLegacyUpgradeTest test
```

Expected: FAIL because `CampaignSession`, both repositories, and V4 do not exist.

- [ ] **Step 3: Add the exact V4 migration**

```sql
create table campaign_session (
    id uuid not null,
    campaign_id uuid not null,
    status varchar(16) not null,
    started_at timestamp(6) with time zone,
    paused_at timestamp(6) with time zone,
    review_started_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone not null,
    start_in_game_year integer,
    start_in_game_month integer,
    start_in_game_day integer,
    plan_note_id uuid,
    workspace_map_id uuid,
    presentation_mode varchar(16) not null,
    presented_map_id uuid,
    presented_handout_id uuid,
    draft_body CLOB,
    version bigint not null,
    primary key (id),
    constraint uq_campaign_session_campaign unique (campaign_id),
    constraint fk_campaign_session_campaign foreign key (campaign_id) references campaign on delete cascade,
    constraint fk_campaign_session_plan foreign key (plan_note_id) references note,
    constraint fk_campaign_session_workspace_map foreign key (workspace_map_id) references game_map,
    constraint fk_campaign_session_presented_map foreign key (presented_map_id) references game_map,
    constraint fk_campaign_session_presented_handout foreign key (presented_handout_id) references handout,
    constraint ck_campaign_session_presentation check (
        (presentation_mode = 'CURTAIN' and presented_map_id is null and presented_handout_id is null) or
        (presentation_mode = 'MAP' and presented_map_id is not null and presented_handout_id is null) or
        (presentation_mode = 'HANDOUT' and presented_map_id is null and presented_handout_id is not null)
    )
);

create table campaign_session_attendee (
    session_id uuid not null,
    party_member_id uuid not null,
    primary key (session_id, party_member_id),
    constraint fk_session_attendee_session foreign key (session_id) references campaign_session on delete cascade,
    constraint fk_session_attendee_member foreign key (party_member_id) references party_member
);

create table session_scene_visit (
    id uuid not null,
    session_id uuid not null,
    scene_id uuid not null,
    visited_at timestamp(6) with time zone not null,
    completed_at timestamp(6) with time zone,
    primary key (id),
    constraint uq_session_scene_visit unique (session_id, scene_id),
    constraint fk_session_scene_visit_session foreign key (session_id) references campaign_session on delete cascade,
    constraint fk_session_scene_visit_scene foreign key (scene_id) references adventure_scene
);

create index idx_campaign_session_status_updated on campaign_session (status, updated_at);
create index idx_session_scene_visit_order on session_scene_visit (session_id, visited_at, id);
```

- [ ] **Step 4: Implement the aggregate and repositories**

Use these exact entity fields and lifecycle helpers:

```java
@Getter
@Setter
@Entity
@Table(name = "campaign_session", uniqueConstraints =
        @UniqueConstraint(name = "uq_campaign_session_campaign", columnNames = "campaign_id"))
public class CampaignSession {
    public enum Status { IDLE, RUNNING, PAUSED, REVIEW }
    public enum PresentationMode { CURTAIN, MAP, HANDOUT }

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false) private Campaign campaign;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.IDLE;
    private Instant startedAt;
    private Instant pausedAt;
    private Instant reviewStartedAt;
    @Column(nullable = false) private Instant updatedAt;
    private Integer startInGameYear;
    private Integer startInGameMonth;
    private Integer startInGameDay;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "plan_note_id") private Note planNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "workspace_map_id") private GameMap workspaceMap;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private PresentationMode presentationMode = PresentationMode.CURTAIN;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "presented_map_id") private GameMap presentedMap;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "presented_handout_id") private Handout presentedHandout;
    @Column(columnDefinition = "CLOB") private String draftBody;
    @ManyToMany
    @JoinTable(name = "campaign_session_attendee",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "party_member_id"))
    @OrderBy("characterName asc") private List<PartyMember> attendees = new ArrayList<>();
    @Version private long version;

    public static CampaignSession idle(Campaign campaign) {
        CampaignSession value = new CampaignSession();
        value.campaign = campaign;
        value.status = Status.IDLE;
        value.presentationMode = PresentationMode.CURTAIN;
        value.updatedAt = Instant.EPOCH;
        return value;
    }

    @PrePersist @PreUpdate void touch() { updatedAt = Instant.now(); }
    public boolean isOpen() { return status != Status.IDLE; }
}
```

```java
@Getter
@Setter
@Entity
@Table(name = "session_scene_visit", uniqueConstraints =
        @UniqueConstraint(name = "uq_session_scene_visit", columnNames = {"session_id", "scene_id"}))
public class SessionSceneVisit {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false) private CampaignSession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false) private Scene scene;
    @Column(nullable = false, updatable = false) private Instant visitedAt;
    private Instant completedAt;
}
```

```java
public interface CampaignSessionRepository extends JpaRepository<CampaignSession, UUID> {
    Optional<CampaignSession> findByCampaignId(UUID campaignId);
    Optional<CampaignSession> findFirstByStatusNotOrderByUpdatedAtDesc(CampaignSession.Status status);
}

public interface SessionSceneVisitRepository extends JpaRepository<SessionSceneVisit, UUID> {
    Optional<SessionSceneVisit> findBySessionIdAndSceneId(UUID sessionId, UUID sceneId);
    List<SessionSceneVisit> findBySessionIdOrderByVisitedAtAscIdAsc(UUID sessionId);
    void deleteBySessionId(UUID sessionId);
}
```

- [ ] **Step 5: Run the persistence slice**

Run the command from Step 2.

Expected: PASS; Flyway reports four migrations, legacy campaign data remains, and the unique constraints reject duplicate session/visit rows.

- [ ] **Step 6: Commit the durable state foundation**

```bash
git add src/main/resources/db/migration/V4__add_campaign_session.sql \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/data \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/data \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayLegacyUpgradeTest.java
git commit -m "feat: persist campaign session coordination"
```

### Task 2: Implement lifecycle, attendance, and scene activity

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionActivityRecorder.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleServiceTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureServiceTest.java`

**Interfaces:**
- Produces: `CampaignSession start(UUID campaignId, UUID requestedMapId)`.
- Produces: `CampaignSession pause(UUID campaignId)`, `resume(UUID campaignId)`, and `cancelReview(UUID campaignId)`.
- Produces: `CampaignSession setAttendees(UUID campaignId, List<UUID> partyMemberIds)`.
- Produces: `CampaignSession setWorkspaceMap(UUID campaignId, UUID mapId)`.
- Produces: `void SessionActivityRecorder.sceneSelected(UUID campaignId, Scene scene)` and `sceneCompleted(Scene scene)`.
- Consumes: `Clock`, `CalendarService`, `PartyMemberRepository`, `NoteRepository`, and the state repositories from Task 1.

- [ ] **Step 1: Write failing lifecycle and activity tests**

```java
@Test
void startSnapshotsActiveAttendanceDatePlanAndSelectedMap() {
    when(clock.instant()).thenReturn(Instant.parse("2026-07-16T18:00:00Z"));
    when(calendar.getCurrentDate(campaignId)).thenReturn(new InGameDate(1492, 6, 12));
    when(party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId))
            .thenReturn(List.of(aria, borin));
    when(notes.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_PLAN))
            .thenReturn(List.of(plan));

    CampaignSession result = service.start(campaignId, mapId);

    assertThat(result.getStatus()).isEqualTo(RUNNING);
    assertThat(result.getStartedAt()).isEqualTo(Instant.parse("2026-07-16T18:00:00Z"));
    assertThat(result.getAttendees()).containsExactly(aria, borin);
    assertThat(result.getStartInGameYear()).isEqualTo(1492);
    assertThat(result.getWorkspaceMap()).isEqualTo(map);
    assertThat(result.getPlanNote()).isEqualTo(plan);
    assertThat(result.getPresentationMode()).isEqualTo(CURTAIN);
}

@Test
void rejectsIllegalLifecycleTransitionsWithoutMutatingState() {
    session.setStatus(IDLE);
    assertThatThrownBy(() -> service.pause(campaignId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only a running session can be paused.");
    assertThat(session.getStatus()).isEqualTo(IDLE);
}

@Test
void selectingSceneWhileRunningRecordsOneVisitAndCompletionTime() {
    recorder.sceneSelected(campaignId, scene);
    recorder.sceneSelected(campaignId, scene);
    recorder.sceneCompleted(scene);

    assertThat(visits.findBySessionIdOrderByVisitedAtAscIdAsc(sessionId)).singleElement()
            .satisfies(visit -> {
                assertThat(visit.getVisitedAt()).isEqualTo(firstInstant);
                assertThat(visit.getCompletedAt()).isEqualTo(completedInstant);
            });
}
```

- [ ] **Step 2: Run the focused tests and verify the services are absent**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=SessionLifecycleServiceTest,AdventureServiceTest test
```

Expected: FAIL at test compilation because both session services are missing.

- [ ] **Step 3: Implement legal transitions and campaign ownership checks**

```java
@Service
@Transactional
public class SessionLifecycleService {
    private final Clock clock;
    private final CampaignRepository campaigns;
    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;
    private final GameMapRepository maps;
    private final PartyMemberRepository party;
    private final NoteRepository notes;
    private final CalendarService calendar;

    public CampaignSession start(UUID campaignId, UUID requestedMapId) {
        Campaign campaign = requireCampaign(campaignId);
        CampaignSession session = sessions.findByCampaignId(campaignId)
                .orElseGet(() -> CampaignSession.idle(campaign));
        requireStatus(session, CampaignSession.Status.IDLE, "Only an idle campaign can start a session.");
        Instant now = clock.instant();
        var date = calendar.getCurrentDate(campaignId);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setStartedAt(now);
        session.setPausedAt(null);
        session.setReviewStartedAt(null);
        session.setStartInGameYear(date.year());
        session.setStartInGameMonth(date.month());
        session.setStartInGameDay(date.day());
        session.setPlanNote(notes.findByCampaignIdAndTypeOrderByCreatedAtDesc(
                campaignId, NoteType.SESSION_PLAN).stream().findFirst().orElse(null));
        session.setWorkspaceMap(requestedMapId == null ? null : requireCampaignMap(campaignId, requestedMapId));
        session.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
        session.setPresentedMap(null);
        session.setPresentedHandout(null);
        session.setDraftBody(null);
        session.getAttendees().clear();
        session.getAttendees().addAll(party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId));
        CampaignSession saved = sessions.save(session);
        visits.deleteBySessionId(saved.getId());
        return saved;
    }

    public CampaignSession pause(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        requireStatus(session, CampaignSession.Status.RUNNING, "Only a running session can be paused.");
        session.setStatus(CampaignSession.Status.PAUSED);
        session.setPausedAt(clock.instant());
        return sessions.save(session);
    }

    public CampaignSession resume(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        requireStatus(session, CampaignSession.Status.PAUSED, "Only a paused session can be resumed.");
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setPausedAt(null);
        return sessions.save(session);
    }

    public CampaignSession cancelReview(UUID campaignId) {
        CampaignSession session = requireSession(campaignId);
        requireStatus(session, CampaignSession.Status.REVIEW, "Only a session under review can return to play.");
        session.setStatus(CampaignSession.Status.PAUSED);
        session.setReviewStartedAt(null);
        session.setDraftBody(null);
        return sessions.save(session);
    }

    public CampaignSession setAttendees(UUID campaignId, List<UUID> ids) {
        CampaignSession session = requireOpenSession(campaignId);
        List<PartyMember> selected = party.findAllById(ids);
        if (selected.size() != new HashSet<>(ids).size()
                || selected.stream().anyMatch(member -> !member.getCampaign().getId().equals(campaignId))) {
            throw new IllegalArgumentException("Every attendee must belong to this campaign.");
        }
        session.getAttendees().clear();
        session.getAttendees().addAll(selected.stream()
                .sorted(Comparator.comparing(PartyMember::getCharacterName)).toList());
        return sessions.save(session);
    }

    public CampaignSession setWorkspaceMap(UUID campaignId, UUID mapId) {
        CampaignSession session = requireOpenSession(campaignId);
        session.setWorkspaceMap(mapId == null ? null : requireCampaignMap(campaignId, mapId));
        return sessions.save(session);
    }
}
```

Provide a `Clock` bean once, in a focused configuration class or the application class:

```java
@Bean
Clock systemClock() {
    return Clock.systemUTC();
}
```

- [ ] **Step 4: Implement optional, idempotent scene activity recording**

```java
@Service
@Transactional
public class SessionActivityRecorder {
    private final Clock clock;
    private final CampaignSessionRepository sessions;
    private final SessionSceneVisitRepository visits;

    public void sceneSelected(UUID campaignId, Scene scene) {
        sessions.findByCampaignId(campaignId)
                .filter(session -> session.getStatus() == CampaignSession.Status.RUNNING)
                .ifPresent(session -> visits.findBySessionIdAndSceneId(session.getId(), scene.getId())
                        .orElseGet(() -> {
                            SessionSceneVisit visit = new SessionSceneVisit();
                            visit.setSession(session);
                            visit.setScene(scene);
                            visit.setVisitedAt(clock.instant());
                            return visits.save(visit);
                        }));
    }

    public void sceneCompleted(Scene scene) {
        UUID campaignId = scene.getChapter().getAdventure().getCampaign().getId();
        sessions.findByCampaignId(campaignId)
                .filter(session -> session.getStatus() == CampaignSession.Status.RUNNING)
                .ifPresent(session -> {
                    SessionSceneVisit visit = visits.findBySessionIdAndSceneId(session.getId(), scene.getId())
                            .orElseGet(() -> {
                                SessionSceneVisit created = new SessionSceneVisit();
                                created.setSession(session);
                                created.setScene(scene);
                                created.setVisitedAt(clock.instant());
                                return created;
                            });
                    if (visit.getCompletedAt() == null) visit.setCompletedAt(clock.instant());
                    visits.save(visit);
                });
    }
}
```

Inject the interface into `AdventureService` and call it only after successful persistence:

```java
public Scene setStatus(UUID sceneId, SceneStatus status) {
    Scene saved = sceneRepository.save(setStatusOn(findSceneById(sceneId), status));
    if (status == SceneStatus.DONE) sessionActivity.sceneCompleted(saved);
    return saved;
}

public Scene setCurrentScene(UUID campaignId, UUID sceneId) {
    // Keep the existing campaign cursor and UNVISITED -> VISITED behavior.
    Scene saved = persistCurrentScene(campaignId, sceneId);
    sessionActivity.sceneSelected(campaignId, saved);
    return saved;
}
```

Do not record authoring page reads, map switches, or scene-panel expansion as visits.

- [ ] **Step 5: Run lifecycle and adventure tests**

Run the command from Step 2.

Expected: PASS; illegal transitions leave the entity unchanged and existing adventure stepping remains green.

- [ ] **Step 6: Commit lifecycle and activity evidence**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/service \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleServiceTest.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureServiceTest.java
git commit -m "feat: add session lifecycle and activity tracking"
```

### Task 3: Compose the workspace and ordered session-plan beats

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionPlanService.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionPlanServiceTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceServiceTest.java`

**Interfaces:**
- Produces: `SessionPlan(UUID noteId, String title, String renderedBody, List<SessionPlanBeat> beats)`.
- Produces: `SessionPlanBeat(int position, String type, UUID targetId, String label, String url, UUID mapId, boolean resolved)`.
- Produces: `SessionWorkspace load(UUID campaignId, UUID requestedMapId)` with campaign/session, selected map, selection source, current/editorial-neighbor scenes, active/planned encounters, plan, party, handouts, maps, and current date.
- Selection source values are exactly `ACTIVE_ENCOUNTER`, `CURRENT_SCENE`, `SESSION_PLAN`, `EXPLICIT_MAP`, `STORED_SESSION`, and `NONE`.
- Consumes: existing repositories and services read-only; it never changes presentation or domain status.

- [ ] **Step 1: Write the failing ordered-plan tests**

```java
@Test
void resolvesExplicitPlanLinksInMarkdownOrderAndKeepsBrokenBeats() {
    plan.setBody("Meet [[NPC:Veyra]], then [[SCENE:Crypt Door]], " +
            "show [[HANDOUT:Inscription]], use [[MAP:Lower Crypt]], " +
            "and inspect [[ENCOUNTER:Missing Guards]].");

    SessionPlan result = service.latest(campaignId).orElseThrow();

    assertThat(result.beats()).extracting(SessionPlanBeat::type, SessionPlanBeat::label,
                    SessionPlanBeat::resolved)
            .containsExactly(
                    tuple("NOTE", "Veyra", false),
                    tuple("SCENE", "Crypt Door", true),
                    tuple("HANDOUT", "Inscription", true),
                    tuple("MAP", "Lower Crypt", true),
                    tuple("ENCOUNTER", "Missing Guards", false));
    assertThat(result.beats()).extracting(SessionPlanBeat::position)
            .containsExactly(0, 1, 2, 3, 4);
}
```

The first `NPC` link is intentionally a note lookup because the current note model uses `NOTE` for unrecognized prefixes. Do not add world entities in this task.

- [ ] **Step 2: Write the full workspace-selection matrix**

```java
@ParameterizedTest
@MethodSource("selectionCases")
void selectsInitialMapByContract(SelectionFixture fixture, SelectionSource expected) {
    fixture.stub(repositories);
    SessionWorkspace result = service.load(campaignId, fixture.requestedMapId());
    assertThat(result.selectionSource()).isEqualTo(expected);
    assertThat(result.workspaceMap()).extracting(GameMap::getId)
            .isEqualTo(fixture.expectedMapId());
}

static Stream<Arguments> selectionCases() {
    return Stream.of(
            arguments(caseWithRunningStoredMap(), STORED_SESSION),
            arguments(caseWithActiveEncounterMap(), ACTIVE_ENCOUNTER),
            arguments(caseWithCurrentSceneMap(), CURRENT_SCENE),
            arguments(caseWithFirstPlanSceneMap(), SESSION_PLAN),
            arguments(caseWithFirstPlanMap(), SESSION_PLAN),
            arguments(caseWithExplicitMapOnly(), EXPLICIT_MAP),
            arguments(caseWithNoMap(), NONE));
}

@Test
void activeEncounterWithoutMapFallsThroughToCurrentSceneMap() {
    when(encounters.findByCampaignIdAndStatus(campaignId, ACTIVE)).thenReturn(Optional.of(maplessEncounter));
    when(adventures.getCurrentScene(campaignId)).thenReturn(Optional.of(sceneWithMap));
    assertThat(service.load(campaignId, null).selectionSource()).isEqualTo(CURRENT_SCENE);
}
```

- [ ] **Step 3: Run the service tests and verify both composers are absent**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=SessionPlanServiceTest,SessionWorkspaceServiceTest test
```

Expected: FAIL at compilation because the records and services do not exist.

- [ ] **Step 4: Implement exact ordered link resolution**

```java
@Service
@Transactional(readOnly = true)
public class SessionPlanService {
    public record SessionPlan(UUID noteId, String title, String renderedBody, List<SessionPlanBeat> beats) {}
    public record SessionPlanBeat(int position, String type, UUID targetId, String label,
                                  String url, UUID mapId, boolean resolved) {}

    public Optional<SessionPlan> latest(UUID campaignId) {
        return noteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, NoteType.SESSION_PLAN)
                .stream().findFirst().map(note -> {
                    List<SessionPlanBeat> beats = new ArrayList<>();
                    int position = 0;
                    for (var target : parser.extractReferences(note.getBody())) {
                        beats.add(resolve(campaignId, position++, target));
                    }
                    return new SessionPlan(note.getId(), note.getTitle(),
                            markdown.toHtml(noteService.renderBody(note)), List.copyOf(beats));
                });
    }

    private SessionPlanBeat resolve(UUID campaignId, int position, WikiLinkTarget target) {
        return switch (target.targetType()) {
            case "SCENE" -> sceneRepository
                    .findByChapterAdventureCampaignIdAndTitleIgnoreCase(campaignId, target.title())
                    .stream().findFirst()
                    .map(scene -> beat(position, "SCENE", scene.getId(), scene.getTitle(),
                            destinations.campaign(SCENE, campaignId, scene.getId(),
                                    scene.getChapter().getAdventure().getId()),
                            scene.getMap() == null ? null : scene.getMap().getId()))
                    .orElseGet(() -> broken(position, target));
            case "MAP" -> gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId).stream()
                    .filter(map -> map.getName().equalsIgnoreCase(target.title())).findFirst()
                    .map(map -> beat(position, "MAP", map.getId(), map.getName(),
                            destinations.campaign(MAP, campaignId, map.getId(), null), map.getId()))
                    .orElseGet(() -> broken(position, target));
            case "ENCOUNTER" -> encounterRepository.findByCampaignIdOrderByNameAsc(campaignId).stream()
                    .filter(encounter -> encounter.getName().equalsIgnoreCase(target.title())).findFirst()
                    .map(encounter -> beat(position, "ENCOUNTER", encounter.getId(), encounter.getName(),
                            destinations.campaign(ENCOUNTER, campaignId, encounter.getId(), null),
                            encounter.getMap() == null ? null : encounter.getMap().getId()))
                    .orElseGet(() -> broken(position, target));
            case "HANDOUT" -> handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId).stream()
                    .filter(handout -> handout.getTitle().equalsIgnoreCase(target.title())).findFirst()
                    .map(handout -> beat(position, "HANDOUT", handout.getId(), handout.getTitle(),
                            destinations.campaign(HANDOUT, campaignId, handout.getId(), null), null))
                    .orElseGet(() -> broken(position, target));
            default -> noteRepository.findByCampaignIdAndTitle(campaignId, target.title()).stream().findFirst()
                    .map(note -> beat(position, "NOTE", note.getId(), note.getTitle(),
                            destinations.campaign(NOTE, campaignId, note.getId(), null), null))
                    .orElseGet(() -> broken(position, target));
        };
    }
}
```

Use private `beat`/`broken` helpers to construct the record; `broken` preserves the original type/title with `targetId`, `url`, and `mapId` null and `resolved=false`.

- [ ] **Step 5: Implement the read-only workspace and explicit priority**

```java
@Service
@Transactional(readOnly = true)
public class SessionWorkspaceService {
    public enum SelectionSource {
        ACTIVE_ENCOUNTER, CURRENT_SCENE, SESSION_PLAN, EXPLICIT_MAP, STORED_SESSION, NONE
    }

    public record SessionWorkspace(
            Campaign campaign,
            CampaignSession session,
            GameMap workspaceMap,
            SelectionSource selectionSource,
            Scene currentScene,
            Scene previousScene,
            Scene nextScene,
            Encounter activeEncounter,
            List<Encounter> plannedEncounters,
            SessionPlanService.SessionPlan sessionPlan,
            List<GameMap> maps,
            List<Handout> handouts,
            List<PartyMember> partyMembers,
            CalendarService.InGameDate currentDate) {}

    public SessionWorkspace load(UUID campaignId, UUID requestedMapId) {
        Campaign campaign = campaigns.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        CampaignSession session = sessions.findByCampaignId(campaignId)
                .orElseGet(() -> CampaignSession.idle(campaign));
        Scene current = adventures.getCurrentScene(campaignId).orElse(null);
        Encounter active = encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE).orElse(null);
        SessionPlanService.SessionPlan plan = plans.latest(campaignId).orElse(null);
        Selection selection = select(session, active, current, plan, requestedMapId, campaignId);
        List<Scene> neighbors = editorialNeighbors(current);
        return new SessionWorkspace(campaign, session, selection.map(), selection.source(), current,
                neighbors.get(0), neighbors.get(1), active,
                encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                        .filter(value -> value.getStatus() == Encounter.Status.PLANNED).toList(),
                plan, maps.findByCampaignIdOrderBySortOrderAsc(campaignId),
                handouts.findByCampaignIdOrderByTitleAsc(campaignId),
                party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId),
                calendar.getCurrentDate(campaignId));
    }

    private Selection select(CampaignSession session, Encounter active, Scene current,
                             SessionPlanService.SessionPlan plan, UUID requestedMapId, UUID campaignId) {
        if (session.isOpen() && session.getWorkspaceMap() != null)
            return new Selection(session.getWorkspaceMap(), SelectionSource.STORED_SESSION);
        if (active != null && active.getMap() != null)
            return new Selection(active.getMap(), SelectionSource.ACTIVE_ENCOUNTER);
        if (current != null && current.getMap() != null)
            return new Selection(current.getMap(), SelectionSource.CURRENT_SCENE);
        if (plan != null) {
            Optional<GameMap> firstPlanMap = plan.beats().stream()
                    .filter(SessionPlanBeat::resolved).map(SessionPlanBeat::mapId)
                    .filter(Objects::nonNull).map(maps::findById).flatMap(Optional::stream)
                    .filter(map -> map.getCampaign().getId().equals(campaignId)).findFirst();
            if (firstPlanMap.isPresent()) return new Selection(firstPlanMap.get(), SelectionSource.SESSION_PLAN);
        }
        if (requestedMapId != null) {
            GameMap requested = maps.findById(requestedMapId)
                    .filter(map -> map.getCampaign().getId().equals(campaignId))
                    .orElseThrow(() -> new NotFoundException("Map not found in campaign"));
            return new Selection(requested, SelectionSource.EXPLICIT_MAP);
        }
        return new Selection(null, SelectionSource.NONE);
    }
}
```

Implement `editorialNeighbors` using `AdventureService.flattenedScenes` and return two nullable values. Never use transition semantics in this slice.

- [ ] **Step 6: Run workspace and plan tests**

Run the command from Step 3.

Expected: PASS for every selection case, broken beats remain visible, and plan resolution performs no writes.

- [ ] **Step 7: Commit the workspace composition contract**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionPlanService.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionPlanServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceServiceTest.java
git commit -m "feat: compose the session workspace"
```

### Task 4: Make player presentation durable and campaign-scoped

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/TablePresentationService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/live/web/TablePresentationController.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/live/PlayerSafeProjectionServiceTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitSecurityTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`

**Interfaces:**
- Produces: `LiveTableState presentMap(UUID campaignId, UUID mapId)`.
- Produces: `LiveTableState presentHandout(UUID campaignId, UUID handoutId)`.
- Produces: `LiveTableState curtain(UUID campaignId)`.
- Produces: `LiveTableState restoreLatestPresentation()` and `restorePresentation(UUID campaignId)`.
- HTTP mutations move to `PUT /api/v1/campaigns/{campaignId}/table/presentation`; `GET /api/v1/table/state` remains the player-safe global projection.
- Missing refs and cross-campaign refs throw `NotFoundException` or `IllegalArgumentException`; they never return the prior state as apparent success.

- [ ] **Step 1: Write failing ownership, restart, and player-safety tests**

```java
@Test
void crossCampaignMapCannotBecomeThePresentation() {
    assertThatThrownBy(() -> service.presentMap(campaignA, campaignBMap))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Map not found in campaign");
    assertThat(sessionA.getPresentationMode()).isEqualTo(CURTAIN);
}

@Test
void restartRestoresMostRecentlyUpdatedOpenSessionPresentation() {
    session.setStatus(PAUSED);
    session.setPresentationMode(MAP);
    session.setPresentedMap(map);
    when(sessions.findFirstByStatusNotOrderByUpdatedAtDesc(IDLE)).thenReturn(Optional.of(session));

    LiveTableState restored = service.restoreLatestPresentation();

    assertThat(restored.mode()).isEqualTo("MAP");
    assertThat(restored.map().mapId()).isEqualTo(map.getId().toString());
    assertPlayerStateContainsNo(restored, "scene", "note", "dmOnly", "hidden");
}
```

Add MockMvc assertions that the new mutation route is PIN-gated and that `/api/v1/table/state` remains readable by the player route configuration.

- [ ] **Step 2: Run the presentation/security slice and capture the old contract**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=PlayerSafeProjectionServiceTest,SessionCockpitSecurityTest test
```

Expected: FAIL because the old service is global/in-memory, accepts unscoped refs, and returns its prior state for a missing object.

- [ ] **Step 3: Persist first, then project and broadcast**

```java
@Transactional
public LiveTableState presentMap(UUID campaignId, UUID mapId) {
    CampaignSession session = requireOpenSession(campaignId);
    GameMap map = gameMapRepository.findById(mapId)
            .filter(value -> value.getCampaign().getId().equals(campaignId))
            .orElseThrow(() -> new NotFoundException("Map not found in campaign"));
    session.setPresentationMode(CampaignSession.PresentationMode.MAP);
    session.setPresentedMap(map);
    session.setPresentedHandout(null);
    sessionRepository.saveAndFlush(session);
    currentCampaignId = campaignId;
    currentState = projectMap(map);
    broadcast();
    return currentState;
}

@Transactional
public LiveTableState presentHandout(UUID campaignId, UUID handoutId) {
    CampaignSession session = requireOpenSession(campaignId);
    Handout handout = handoutRepository.findById(handoutId)
            .filter(value -> value.getCampaign().getId().equals(campaignId) && !value.isDmOnly())
            .orElseThrow(() -> new NotFoundException("Presentable handout not found in campaign"));
    session.setPresentationMode(CampaignSession.PresentationMode.HANDOUT);
    session.setPresentedMap(null);
    session.setPresentedHandout(handout);
    sessionRepository.saveAndFlush(session);
    currentCampaignId = campaignId;
    currentState = projectHandout(handout);
    broadcast();
    return currentState;
}

@Transactional
public LiveTableState curtain(UUID campaignId) {
    CampaignSession session = requireOpenSession(campaignId);
    session.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
    session.setPresentedMap(null);
    session.setPresentedHandout(null);
    sessionRepository.saveAndFlush(session);
    currentCampaignId = campaignId;
    currentState = LiveTableState.curtain();
    broadcast();
    return currentState;
}
```

Refactor existing map projection code into `projectMap(GameMap)` without changing `PlayerSafeProjectionService`. `restorePresentation` uses the same projectors and catches only missing/deleted persisted refs by clearing them to `CURTAIN` in a transaction.

- [ ] **Step 4: Scope the mutation controller and update callers**

```java
@PutMapping("/api/v1/campaigns/{campaignId}/table/presentation")
public LiveTableState setPresentation(@PathVariable UUID campaignId,
                                      @RequestBody PresentationRequest request) {
    return switch (request.mode()) {
        case "MAP" -> presentationService.presentMap(campaignId, UUID.fromString(request.ref()));
        case "HANDOUT" -> presentationService.presentHandout(campaignId, UUID.fromString(request.ref()));
        case "CURTAIN" -> presentationService.curtain(campaignId);
        default -> throw new IllegalArgumentException("Unknown presentation mode: " + request.mode());
    };
}
```

Keep refresh/AoE operations campaign-scoped too, so a stale cockpit cannot refresh another campaign's presentation:

```java
@PostMapping("/api/v1/campaigns/{campaignId}/table/refresh")
public LiveTableState refresh(@PathVariable UUID campaignId) {
    return presentationService.broadcastCurrentState(campaignId);
}
```

Update all production JavaScript and smoke-test URL patterns from `/api/v1/table/presentation` to `/api/v1/campaigns/${campaignId}/table/presentation`.

- [ ] **Step 5: Run presentation, security, and interaction tests**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=PlayerSafeProjectionServiceTest,SessionCockpitSecurityTest,InteractionFailureContractTest test
```

Expected: PASS; cross-campaign refs are rejected, current state restores after service reconstruction, and DM-only data remains absent.

- [ ] **Step 6: Commit durable presentation state**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/live \
  src/test/java/dev/hendrikhoemberg/dmhelper/live \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitSecurityTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
git commit -m "feat: persist campaign table presentation"
```

### Task 5: Replace the redirect with the composed cockpit page

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`
- Create: `src/main/resources/templates/session/cockpit.html`
- Create: `src/main/resources/templates/session/_story-rail.html`
- Create: `src/main/resources/templates/session/_encounter-rail.html`
- Create: `src/main/resources/templates/session/_session-plan.html`
- Create: `src/main/resources/templates/session/_empty-table.html`
- Create: `src/main/resources/static/js/session-cockpit.js`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionControllerTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java`
- Modify: `src/main/resources/static/css/cockpit.css`
- Delete: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/SessionController.java`
- Delete: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/SessionControllerTest.java`
- Delete: `src/main/resources/templates/maps/battle.html`

**Interfaces:**
- `GET /campaigns/{campaignId}/session?mapId={optionalUuid}` renders `session/cockpit` with a `workspace` model attribute.
- `GET /campaigns/{campaignId}/maps/{mapId}/play` redirects to `/campaigns/{campaignId}/session?mapId={mapId}`.
- The template has exactly one `#battleCanvasWrap`, one inclusion of `encounter/_tracker`, and no copied tracker mutation functions.
- JavaScript exposes one Alpine component `sessionCockpit(config)` and instantiates the existing `BattleMap` only when `config.mapId` is non-null.

- [ ] **Step 1: Replace redirect assertions with real-page and explicit-map tests**

```java
@WebMvcTest(SessionController.class)
class SessionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SessionWorkspaceService workspaces;

    @Test
    void rendersCockpitEvenWhenNoMapExists() throws Exception {
        when(workspaces.load(campaignId, null)).thenReturn(emptyWorkspace);
        mvc.perform(get("/campaigns/{id}/session", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("session/cockpit"))
                .andExpect(model().attribute("workspace", emptyWorkspace));
    }

    @Test
    void passesExplicitMapSelectionToWorkspacePolicy() throws Exception {
        when(workspaces.load(campaignId, mapId)).thenReturn(mapWorkspace);
        mvc.perform(get("/campaigns/{id}/session", campaignId).param("mapId", mapId.toString()))
                .andExpect(status().isOk());
        verify(workspaces).load(campaignId, mapId);
    }
}
```

Add a `GameMapControllerTest` assertion for the new redirect URL.

- [ ] **Step 2: Add static no-duplication and accessibility contracts**

```java
@Test
void cockpitOwnsOneRuntimeIslandAndAccessibleRailControls() throws IOException {
    String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
    String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
    assertThat(count(html, "id=\"battleCanvasWrap\"")).isEqualTo(1);
    assertThat(count(html, "encounter/_tracker :: tracker")).isEqualTo(1);
    assertThat(js).contains("new BattleMap(");
    assertThat(js).doesNotContain("nextTurn(id)", "applyDamage(combatant", "projectTokens(");
    assertThat(html).contains("aria-label=\"Story rail\"", "aria-label=\"Encounter rail\"",
            "aria-label=\"Session plan\"", "aria-live=\"polite\"");
}
```

- [ ] **Step 3: Run the controller/template tests and verify the old redirect fails**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=SessionControllerTest,GameMapControllerTest,SessionCockpitTemplateContractTest test
```

Expected: FAIL because `/session` redirects and the cockpit files are absent.

- [ ] **Step 4: Add the session-owned controller and map-play redirect**

```java
@Controller
public class SessionController {
    private final SessionWorkspaceService workspaces;

    public SessionController(SessionWorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @GetMapping("/campaigns/{campaignId}/session")
    public String cockpit(@PathVariable UUID campaignId,
                          @RequestParam(required = false) UUID mapId,
                          Model model) {
        model.addAttribute("workspace", workspaces.load(campaignId, mapId));
        model.addAttribute("campaignId", campaignId);
        return "session/cockpit";
    }
}
```

```java
@GetMapping("/{mapId}/play")
public String play(@PathVariable UUID campaignId, @PathVariable UUID mapId) {
    service.requireInCampaign(campaignId, mapId);
    return "redirect:/campaigns/" + campaignId + "/session?mapId=" + mapId;
}
```

Delete the old redirect-only controller/test after the new test passes.

- [ ] **Step 5: Build the page from the existing runtime island**

Use this exact top-level template structure:

```html
<body>
<main class="session-cockpit"
      x-data="sessionCockpit(window.sessionCockpitConfig)"
      th:attr="data-campaign-id=${campaignId}"
      @session-next-turn.window="nextTurn()"
      @session-scene-step.window="stepScene($event.detail.direction)">
  <header class="cockpit-topbar">
    <h1 th:text="${workspace.campaign.name}">Campaign</h1>
    <span class="badge" th:text="${workspace.session.status}">IDLE</span>
    <span th:text="${workspace.currentDate.year + '-' + (workspace.currentDate.month + 1) + '-' + workspace.currentDate.day}">1492-7-12</span>
    <button class="btn btn-ghost" @click="openSearch()">Search</button>
    <button class="btn btn-ghost" @click="openDice()">Dice</button>
    <button class="btn btn-ghost" @click="lifecycleOpen = true">Session</button>
  </header>
  <section class="cockpit-grid">
    <aside class="cockpit-story" aria-label="Story rail"
           th:replace="~{session/_story-rail :: story(workspace=${workspace})}"></aside>
    <section class="cockpit-table" aria-label="Table surface">
      <div th:if="${workspace.workspaceMap != null}" id="battleCanvasWrap"></div>
      <th:block th:unless="${workspace.workspaceMap != null}"
                th:replace="~{session/_empty-table :: empty(workspace=${workspace})}"></th:block>
      <div class="battle-statusbar" aria-live="polite">
        <span id="battleStatusMessage">Ready</span>
        <span id="battleCursorInfo"></span>
        <span id="battleSaveIndicator" class="save-indicator save-saved">Saved</span>
      </div>
    </section>
    <aside class="cockpit-encounter" aria-label="Encounter rail"
           th:replace="~{session/_encounter-rail :: encounters(workspace=${workspace})}"></aside>
  </section>
  <section class="cockpit-plan" aria-label="Session plan"
           th:replace="~{session/_session-plan :: plan(workspace=${workspace})}"></section>
  <footer class="cockpit-partybar">
    <th:block th:replace="~{party/_summary-bar :: summary-bar(members=${workspace.partyMembers}, oob=null)}"></th:block>
  </footer>
</main>
<th:block th:replace="~{fragments/_command-palette :: command-palette}"></th:block>
<th:block th:replace="~{fragments/_dice-roller :: dice-roller}"></th:block>
<script th:src="@{/vendor/konva.min.js}"></script>
<script type="module" th:src="@{/js/session-cockpit.js}"></script>
</body>
```

Move the battle toolbar markup and `BattleMap` configuration from `maps/battle.html` into the table section without changing endpoint payloads. Move `battleToolbar()` behavior into `session-cockpit.js`; do not copy anything from `encounter/_tracker.html`.

- [ ] **Step 6: Render honest story, encounter, plan, and empty rails**

`_story-rail.html` must show title, status, scene key, **Scene notes**, previous/next editorial buttons, linked map/encounter/statblocks/handouts, and scene quick notes. `_encounter-rail.html` must show the existing tracker when active plus planned-encounter activation buttons. `_session-plan.html` must render beats in `position` order, visually mark broken beats, and provide `Open`, `Switch map`, or `Present` according to type. `_empty-table.html` must contain a map `<select>` and links to create a map/adventure, not redirect away.

Use only checked calls such as:

```javascript
async switchWorkspaceMap(mapId) {
  const previous = this.mapId;
  try {
    await window.dmRequest(`/api/v1/campaigns/${this.campaignId}/session/workspace-map`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mapId })
    });
    await window.battleMap?.switchToMap(mapId);
    this.mapId = mapId;
    history.replaceState(null, '', `/campaigns/${this.campaignId}/session?mapId=${mapId}`);
  } catch (error) {
    this.mapId = previous;
    window.reportActionFailure('Could not switch the session workspace map.', error,
      () => this.switchWorkspaceMap(mapId));
  }
}
```

- [ ] **Step 7: Add responsive and focus-visible cockpit layout**

```css
.cockpit-grid {
  display: grid;
  grid-template-columns: minmax(16rem, 22rem) minmax(24rem, 1fr) minmax(20rem, 26rem);
  min-height: 0;
  flex: 1;
}
.cockpit-story, .cockpit-encounter { overflow: auto; min-height: 0; }
.cockpit-table { position: relative; min-width: 0; min-height: 24rem; }
.cockpit-plan { max-height: 14rem; overflow: auto; border-top: 1px solid var(--color-border); }
.session-cockpit :focus-visible { outline: 3px solid var(--color-accent); outline-offset: 2px; }
@media (max-width: 1100px) {
  .cockpit-grid { grid-template-columns: 18rem minmax(22rem, 1fr); }
  .cockpit-encounter { grid-column: 1 / -1; max-height: 22rem; }
}
@media (max-width: 760px) {
  .cockpit-grid { display: flex; flex-direction: column; overflow: auto; }
  .cockpit-story, .cockpit-encounter { max-height: none; }
}
```

- [ ] **Step 8: Run page, template, and existing map/tracker tests**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=SessionControllerTest,GameMapControllerTest,SessionCockpitTemplateContractTest,GameMapApiControllerTest,TokenApiControllerTest,EncounterApiControllerTest test
```

Expected: PASS; `/session` returns 200 with or without maps, `/play` redirects into it, and no runtime implementation is duplicated.

- [ ] **Step 9: Commit the cockpit page cutover**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java \
  src/main/resources/templates/session src/main/resources/static/js/session-cockpit.js \
  src/main/resources/static/css/cockpit.css src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java
git add -u src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/SessionController.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/SessionControllerTest.java \
  src/main/resources/templates/maps/battle.html
git commit -m "feat: replace map redirect with session cockpit"
```

### Task 6: Wire checked cockpit actions and keyboard operation

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiControllerTest.java`
- Create: `src/main/resources/templates/session/_lifecycle-dialog.html`
- Modify: `src/main/resources/templates/session/cockpit.html`
- Modify: `src/main/resources/templates/session/_story-rail.html`
- Modify: `src/main/resources/templates/session/_encounter-rail.html`
- Modify: `src/main/resources/templates/session/_session-plan.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/config/InteractionFailureContractTest.java`

**Interfaces:**
- `POST /api/v1/campaigns/{campaignId}/session/start` consumes `{ "mapId": UUID|null }`.
- `POST /api/v1/campaigns/{campaignId}/session/pause`, `/resume`, and `/cancel-review` have no body.
- `PUT /api/v1/campaigns/{campaignId}/session/attendance` consumes `{ "partyMemberIds": [UUID...] }`.
- `PUT /api/v1/campaigns/{campaignId}/session/workspace-map` consumes `{ "mapId": UUID|null }`.
- `PUT /api/v1/campaigns/{campaignId}/session/current-scene` consumes `{ "sceneId": UUID }`.
- `POST /api/v1/campaigns/{campaignId}/session/current-scene/step` consumes `{ "direction": -1|1 }`.
- All endpoints return `SessionStateDto` or `SessionSceneDto`, never JPA entities.

- [ ] **Step 1: Write failing API state and validation tests**

```java
@WebMvcTest(SessionApiController.class)
class SessionApiControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SessionLifecycleService lifecycle;
    @MockitoBean AdventureService adventures;

    @Test
    void startsWithNullableMapAndReturnsTypedState() throws Exception {
        when(lifecycle.start(campaignId, null)).thenReturn(runningSession);
        mvc.perform(post("/api/v1/campaigns/{id}/session/start", campaignId)
                        .contentType(APPLICATION_JSON).content("{\"mapId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.presentationMode").value("CURTAIN"));
    }

    @Test
    void rejectsInvalidSceneDirectionBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/campaigns/{id}/session/current-scene/step", campaignId)
                        .contentType(APPLICATION_JSON).content("{\"direction\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adventures);
    }
}
```

Add ownership tests for attendance, workspace map, and current scene through the service mocks' safe exception responses.

- [ ] **Step 2: Run the API tests and verify the controller is absent**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=SessionApiControllerTest test
```

Expected: FAIL at compilation because `SessionApiController` and its DTOs do not exist.

- [ ] **Step 3: Implement typed controller records and exact routes**

```java
@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/session")
public class SessionApiController {
    public record StartRequest(UUID mapId) {}
    public record AttendanceRequest(List<UUID> partyMemberIds) {}
    public record WorkspaceMapRequest(UUID mapId) {}
    public record CurrentSceneRequest(UUID sceneId) {}
    public record StepSceneRequest(int direction) {}
    public record SessionStateDto(String status, UUID workspaceMapId, String presentationMode,
                                  List<UUID> attendeeIds, String draftBody) {}
    public record SessionSceneDto(UUID id, String title, String status, UUID mapId,
                                  UUID encounterId, UUID previousId, UUID nextId) {}

    @PostMapping("/start")
    SessionStateDto start(@PathVariable UUID campaignId, @RequestBody StartRequest request) {
        return state(lifecycle.start(campaignId, request.mapId()));
    }

    @PostMapping("/pause")
    SessionStateDto pause(@PathVariable UUID campaignId) { return state(lifecycle.pause(campaignId)); }

    @PostMapping("/resume")
    SessionStateDto resume(@PathVariable UUID campaignId) { return state(lifecycle.resume(campaignId)); }

    @PostMapping("/cancel-review")
    SessionStateDto cancelReview(@PathVariable UUID campaignId) {
        return state(lifecycle.cancelReview(campaignId));
    }

    @PutMapping("/attendance")
    SessionStateDto attendance(@PathVariable UUID campaignId,
                               @RequestBody AttendanceRequest request) {
        return state(lifecycle.setAttendees(campaignId, List.copyOf(request.partyMemberIds())));
    }

    @PutMapping("/workspace-map")
    SessionStateDto workspaceMap(@PathVariable UUID campaignId,
                                 @RequestBody WorkspaceMapRequest request) {
        return state(lifecycle.setWorkspaceMap(campaignId, request.mapId()));
    }

    @PutMapping("/current-scene")
    SessionSceneDto currentScene(@PathVariable UUID campaignId,
                                 @RequestBody CurrentSceneRequest request) {
        return scene(adventures.setCurrentScene(campaignId, request.sceneId()), campaignId);
    }

    @PostMapping("/current-scene/step")
    SessionSceneDto stepScene(@PathVariable UUID campaignId, @RequestBody StepSceneRequest request) {
        if (Math.abs(request.direction()) != 1)
            throw new IllegalArgumentException("Scene direction must be -1 or 1.");
        Scene result = adventures.stepCurrentScene(campaignId, request.direction())
                .orElseThrow(() -> new IllegalStateException("No current scene to step from."));
        return scene(result, campaignId);
    }
}
```

The private mapping methods output IDs and strings only. `scene(...)` computes editorial neighbors through `SessionWorkspaceService`; it does not serialize lazy associations.

- [ ] **Step 4: Add checked lifecycle and coordination functions to the Alpine component**

```javascript
async mutateSession(path, options, summary, retry) {
  try {
    const response = await window.dmRequest(
      `/api/v1/campaigns/${this.campaignId}/session${path}`, options);
    const state = await response.json();
    this.sessionStatus = state.status;
    this.presentationMode = state.presentationMode;
    return state;
  } catch (error) {
    window.reportActionFailure(summary, error, retry);
    throw error;
  }
},
async startSession() {
  await this.mutateSession('/start', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mapId: this.mapId || null })
  }, 'Could not start the session.', () => this.startSession());
},
async pauseSession() {
  await this.mutateSession('/pause', { method: 'POST' },
    'Could not pause the session.', () => this.pauseSession());
},
async resumeSession() {
  await this.mutateSession('/resume', { method: 'POST' },
    'Could not resume the session.', () => this.resumeSession());
}
```

On failure, keep the lifecycle dialog open and retain selected attendance/draft input.

- [ ] **Step 5: Add keyboard commands with input/dialog guards**

```javascript
installSessionShortcuts() {
  window.addEventListener('keydown', event => {
    const target = event.target;
    if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
        || target instanceof HTMLSelectElement || target?.isContentEditable
        || document.querySelector('[aria-modal="true"]:not([hidden])')) return;
    if (event.key === ']') { event.preventDefault(); this.stepScene(1); }
    if (event.key === '[') { event.preventDefault(); this.stepScene(-1); }
    if (event.key.toLowerCase() === 'n') { event.preventDefault(); this.nextTurn(); }
    if (event.key.toLowerCase() === 'q') { event.preventDefault(); this.focusQuickNote(); }
    if (event.key.toLowerCase() === 'h') { event.preventDefault(); this.openHandouts(); }
  });
}
```

Reuse global `Ctrl/Cmd+K` search and `Ctrl+R` dice shortcuts. Add the five session commands to the existing keyboard help overlay and give every shortcut an equivalent named button.

- [ ] **Step 6: Strengthen the interaction-failure contract**

Add static assertions that every `fetch(` is absent from `session-cockpit.js`, every lifecycle method calls `window.dmRequest`, and every `catch` passes a retry callback to `window.reportActionFailure`.

```java
assertThat(sessionJs).doesNotContain("fetch(", "catch (error) {}", "catch(error){}");
assertThat(sessionJs).contains("window.dmRequest(", "window.reportActionFailure(");
```

- [ ] **Step 7: Run API and interaction tests**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=SessionApiControllerTest,InteractionFailureContractTest,SessionCockpitTemplateContractTest test
```

Expected: PASS; API validation is deterministic and every session action is checked/retryable.

- [ ] **Step 8: Commit checked session operations**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiControllerTest.java \
  src/main/resources/templates/session src/main/resources/static/js/session-cockpit.js \
  src/test/java/dev/hendrikhoemberg/dmhelper/config/InteractionFailureContractTest.java
git commit -m "feat: coordinate cockpit session actions"
```

### Task 7: Generate, review, and save the deterministic session log

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftService.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/service/SessionDraftServiceTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionLifecycleService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/CombatLogEntryRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/ledger/data/LedgerEntryRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyRepository.java`
- Modify: `src/main/resources/templates/session/_lifecycle-dialog.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`

**Interfaces:**
- Produces: `String SessionDraftService.generate(CampaignSession session, Instant endedAt)`.
- Produces: `CampaignSession beginReview(UUID campaignId)` and `Note complete(UUID campaignId, String title, String body)`.
- HTTP: `POST .../session/review` returns persisted draft; `POST .../session/complete` consumes `{ "title": string, "body": string }` and returns `{ "noteId": UUID, "url": string }`.
- Repository queries are bounded by campaign and `createdAt/timestamp >= session.startedAt` and `<= reviewStartedAt`.

- [ ] **Step 1: Write a deterministic evidence-only draft test**

```java
@Test
void generatesStableMarkdownFromSessionEvidence() {
    String result = service.generate(session, Instant.parse("2026-07-16T22:30:00Z"));

    assertThat(result).isEqualTo("""
            ## Session Date
            16 July 2026, 18:00–22:30 UTC

            ## In-Game Date
            Started: 12 Flamerule 1492
            Ended: 13 Flamerule 1492

            ## Attendance
            - Aria — Hendrik
            - Borin

            ## Scenes
            - Crypt Door — visited, completed
            - Lower Crypt — visited

            ## Encounters
            - Crypt Guardians — 4 rounds; defeated: Goblin 1, Goblin 2; damage recorded: 37

            ## Loot & Ledger Changes
            - +25 GP — party stash — Crypt cache

            ## Unresolved Quick Notes
            - MAP / Lower Crypt: Check the western door inscription.

            ## Recap


            ## Next-Session Hooks

            """);
    assertThat(result).doesNotContain("null", "unknown casualty", "quest", "objective");
}
```

Use a fixed UTC formatter in this baseline. Locale/time-zone customization is separate; the stored `Instant` remains authoritative.

- [ ] **Step 2: Add time-bounded repository tests and queries**

```java
@Query("select log from CombatLogEntry log where log.encounter.campaign.id = :campaignId " +
       "and log.createdAt >= :from and log.createdAt <= :to order by log.createdAt, log.sequence, log.id")
List<CombatLogEntry> findSessionEvidence(UUID campaignId, Instant from, Instant to);

List<LedgerEntry> findByCampaignIdAndTimestampBetweenOrderByTimestampAscIdAsc(
        UUID campaignId, Instant from, Instant to);

List<QuickNote> findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(
        UUID campaignId, Instant from, Instant to);
```

Assert exact lower/upper boundary inclusion and deterministic ID tie-breaking.

- [ ] **Step 3: Run draft and repository tests and verify failure**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=SessionDraftServiceTest,LedgerServiceTest,QuickNoteServiceTest,EncounterServiceTest test
```

Expected: FAIL because the draft service and bounded queries are missing.

- [ ] **Step 4: Implement deterministic summarization without invented facts**

```java
@Service
@Transactional(readOnly = true)
public class SessionDraftService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm")
            .withLocale(Locale.ENGLISH).withZone(ZoneOffset.UTC);

    public String generate(CampaignSession session, Instant endedAt) {
        UUID campaignId = session.getCampaign().getId();
        Instant startedAt = requireNonNull(session.getStartedAt(), "Session start time is required.");
        StringBuilder out = new StringBuilder();
        section(out, "Session Date", DAY.format(startedAt) + "–" +
                DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneOffset.UTC).format(endedAt));
        section(out, "In-Game Date", formatGameDates(session, calendar.getCurrentDate(campaignId)));
        listSection(out, "Attendance", attendanceLines(session));
        listSection(out, "Scenes", sceneLines(session));
        listSection(out, "Encounters", encounterLines(campaignId, startedAt, endedAt));
        listSection(out, "Loot & Ledger Changes", ledgerLines(campaignId, startedAt, endedAt));
        listSection(out, "Unresolved Quick Notes", quickNoteLines(campaignId, startedAt, endedAt));
        out.append("## Recap\n\n\n## Next-Session Hooks\n\n");
        return out.toString();
    }
}
```

For encounters, group log rows by encounter. Include only groups containing `ENCOUNTER_ENDED`; take the maximum logged/current round, names for `DEFEATED` combatant IDs that still resolve, and sum `abs(payload.amount)` only for `DAMAGE`. If payload JSON is malformed, omit that numeric contribution and log a warning with the log-entry ID; do not fail ending the session or fabricate zero as an exact total.

- [ ] **Step 5: Implement review and atomic completion transitions**

```java
public CampaignSession beginReview(UUID campaignId) {
    CampaignSession session = requireSession(campaignId);
    if (session.getStatus() != RUNNING && session.getStatus() != PAUSED)
        throw new IllegalStateException("Only a running or paused session can be reviewed.");
    Instant now = clock.instant();
    session.setStatus(REVIEW);
    session.setReviewStartedAt(now);
    session.setDraftBody(drafts.generate(session, now));
    return sessions.save(session);
}

public Note complete(UUID campaignId, String title, String body) {
    CampaignSession session = requireSession(campaignId);
    requireStatus(session, REVIEW, "Review the session draft before saving it.");
    if (title == null || title.isBlank()) throw new IllegalArgumentException("Session log title is required.");
    if (body == null || body.isBlank()) throw new IllegalArgumentException("Session log body is required.");
    Note note = noteService.create(campaignId, NoteType.SESSION_LOG, title.strip(), body, "session-log", true);
    presentation.curtain(campaignId);
    List<UUID> visitIds = visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId()).stream()
            .map(SessionSceneVisit::getId).toList();
    resetToIdle(session);
    visits.deleteBySessionId(session.getId());
    packageKeys.deleteBindings(campaignId, CampaignContentType.SESSION_SCENE_VISIT, visitIds);
    sessions.save(session);
    return note;
}
```

Add the exact key-cleanup API:

```java
void deleteByCampaignIdAndEntityTypeAndEntityIdIn(
        UUID campaignId, String entityType, Collection<UUID> entityIds);

public void deleteBindings(UUID campaignId, CampaignContentType type, Collection<UUID> entityIds) {
    if (!entityIds.isEmpty())
        repository.deleteByCampaignIdAndEntityTypeAndEntityIdIn(campaignId, type.name(), entityIds);
}
```

`resetToIdle` clears all timestamps, dates, plan/workspace/presentation refs, draft, and attendees and sets presentation to `CURTAIN`. The transaction must roll back the note, state reset, visit deletion, and key cleanup if any step fails.

- [ ] **Step 6: Add the review dialog without losing edits on failure**

```html
<form x-show="sessionStatus === 'REVIEW'" @submit.prevent="completeSession()">
  <label for="session-log-title">Session log title</label>
  <input id="session-log-title" x-model="sessionLogTitle" required maxlength="500">
  <label for="session-log-body">Review session log</label>
  <textarea id="session-log-body" x-model="sessionDraft" rows="24" required></textarea>
  <button type="button" class="btn btn-ghost" @click="cancelReview()">Return to session</button>
  <button type="submit" class="btn btn-primary">Save session log and end</button>
</form>
```

The completion catch block reports failure and leaves `sessionDraft`, `sessionLogTitle`, and dialog state unchanged.

- [ ] **Step 7: Run draft, lifecycle, API, and note tests**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=SessionDraftServiceTest,SessionLifecycleServiceTest,SessionApiControllerTest,NoteServiceTest test
```

Expected: PASS; review is durable, completion creates one DM-only `SESSION_LOG`, and rollback leaves the session in `REVIEW` with its draft.

- [ ] **Step 8: Commit the deterministic end workflow**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/CombatLogEntryRepository.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/ledger/data/LedgerEntryRepository.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/notes/data/QuickNoteRepository.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyService.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignPackageKeyRepository.java \
  src/main/resources/templates/session/_lifecycle-dialog.html \
  src/main/resources/static/js/session-cockpit.js
git commit -m "feat: review and save deterministic session logs"
```

### Task 8: Round-trip open session state through campaign package v2

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/session/packagev2/SessionSectionAdapter.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/session/packagev2/SessionSectionAdapterTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignManifestAssembler.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignEntityCounts.java`
- Modify: `src/main/resources/schemas/campaign-format-v2.schema.json`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshot.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignSemanticSnapshotService.java`
- Modify: `src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json`
- Modify: `src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json`

**Interfaces:**
- Adds `SessionDto session` after `adventures` and before `diceRolls` in `CampaignManifestV2`.
- `SessionDto` fields: `key`, `status`, `startedAt`, `pausedAt`, `reviewStartedAt`, `startInGameDate`, `planNoteRef`, `workspaceMapRef`, `presentationMode`, `presentedRef`, `attendeeRefs`, `sceneVisits`, `draftBody`.
- `SessionSceneVisitDto` fields: `key`, `sceneRef`, `visitedAt`, `completedAt`.
- `presentedRef` type is `MAP` only for `MAP`, `HANDOUT` only for `HANDOUT`, and null only for `CURTAIN`.
- Adapter order is exactly `1150`, after calendar/notes/adventures and before dice.

- [ ] **Step 1: Write failing DTO/schema and adapter contracts**

```java
@Test
void exportsRunningSessionWithOnlyStableTypedReferences() {
    adapter.exportSection(context, assembler);
    SessionDto dto = assembler.session();
    assertThat(dto.status()).isEqualTo("RUNNING");
    assertThat(dto.workspaceMapRef()).isEqualTo(new ContentReference(MAP, "lower-crypt"));
    assertThat(dto.attendeeRefs()).containsExactly(new ContentReference(PARTY_MEMBER, "aria"));
    assertThat(dto.sceneVisits()).singleElement().satisfies(visit -> {
        assertThat(visit.sceneRef()).isEqualTo(new ContentReference(SCENE, "crypt-door"));
        assertThat(visit.visitedAt()).isEqualTo(Instant.parse("2026-07-16T18:15:00Z"));
    });
    assertThat(mapper.writeValueAsString(dto)).doesNotContain(localMapId.toString(), localSceneId.toString());
}

@Test
void idleSessionExportsAsNullAndImportsWithoutCreatingOpenState() {
    adapter.exportSection(idleContext, assembler);
    assertThat(assembler.session()).isNull();
}
```

Extend the manifest contract test with a valid `CURTAIN`, `MAP`, and `HANDOUT` example plus invalid status/ref combinations.

- [ ] **Step 2: Run the package contract slice and verify failure**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=CampaignManifestV2ContractTest,SessionSectionAdapterTest,CampaignSectionRegistryTest test
```

Expected: FAIL because the session DTO/section/type/schema definitions are missing.

- [ ] **Step 3: Add the exact DTO and assembler slot**

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionDto(
        String key,
        String status,
        Instant startedAt,
        Instant pausedAt,
        Instant reviewStartedAt,
        InGameDateDto startInGameDate,
        ContentReference planNoteRef,
        ContentReference workspaceMapRef,
        String presentationMode,
        ContentReference presentedRef,
        List<ContentReference> attendeeRefs,
        List<SessionSceneVisitDto> sceneVisits,
        String draftBody) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionSceneVisitDto(
        String key,
        ContentReference sceneRef,
        Instant visitedAt,
        Instant completedAt) {}
```

Add `SESSION` and `SESSION_SCENE_VISIT` to `CampaignContentType`. `CampaignManifestAssembler` gets a session field, setter, read accessor for adapter tests, required-write tracking, and passes the nullable value into `CampaignManifestV2`. The section is structurally present as JSON `null` when idle because the schema property is required and has type `[object, null]`.

- [ ] **Step 4: Close the JSON Schema and semantic invariants**

```json
"session": {
  "oneOf": [
    { "type": "null" },
    { "$ref": "#/$defs/session" }
  ]
}
```

The `session` definition has `additionalProperties:false`, requires every listed field (nullable refs use union types), restricts status to `RUNNING|PAUSED|REVIEW`, presentation mode to `CURTAIN|MAP|HANDOUT`, and uses `if/then` rules for `presentedRef`. Semantic validation checks:

```java
if (session != null) {
    uniqueKey(SESSION, session.key(), "/session/key", keys, problems);
    requireRefType(session.planNoteRef(), NOTE, "/session/planNoteRef", problems);
    requireRefType(session.workspaceMapRef(), MAP, "/session/workspaceMapRef", problems);
    requireAllRefType(session.attendeeRefs(), PARTY_MEMBER, "/session/attendeeRefs", problems);
    if ("CURTAIN".equals(session.presentationMode()) && session.presentedRef() != null)
        error(problems, "INVALID_SESSION_PRESENTATION", "/session/presentedRef",
                "Curtain presentation cannot reference content.");
    if ("REVIEW".equals(session.status()) &&
            (session.draftBody() == null || session.draftBody().isBlank()))
        error(problems, "MISSING_SESSION_DRAFT", "/session/draftBody",
                "A session under review requires its persisted draft.");
}
```

- [ ] **Step 5: Implement order-1150 export/import**

```java
@Component
public class SessionSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {
    @Override public String sectionName() { return "session"; }
    @Override public int order() { return 1150; }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        CampaignSession session = sessions.findByCampaignId(context.campaign().getId()).orElse(null);
        if (session == null || session.getStatus() == CampaignSession.Status.IDLE) {
            target.session(null);
            return;
        }
        target.session(toDto(session, visits.findBySessionIdOrderByVisitedAtAscIdAsc(session.getId()), context));
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        SessionDto dto = source.session();
        if (dto == null) return;
        CampaignSession session = CampaignSession.idle(context.campaign());
        session.setStatus(CampaignSession.Status.valueOf(dto.status()));
        session.setStartedAt(dto.startedAt());
        session.setPausedAt(dto.pausedAt());
        session.setReviewStartedAt(dto.reviewStartedAt());
        session.setDraftBody(dto.draftBody());
        CampaignSession saved = sessions.save(session);
        context.register(CampaignContentType.SESSION, dto.key(), saved);
        context.defer("session references", () -> restoreReferences(saved, dto, context));
    }
}
```

`restoreReferences` requires every typed ref from the context, checks campaign ownership through the registry, saves attendees and visits in DTO order, and registers every `SESSION_SCENE_VISIT` key. It restores presentation refs without broadcasting during import.

- [ ] **Step 6: Add session semantics to flagship snapshots and fixtures**

The feature-complete fixture uses `RUNNING`, `workspaceMapRef=lower-crypt`, `presentationMode=CURTAIN`, two attendees, and two visits. The published-adventure-shaped fixture uses `PAUSED`, a session plan note, one current-scene visit, and a map presentation. Snapshot comparison includes status/timestamps/dates/typed refs/attendance/visit order/draft but excludes database IDs, version, and `updatedAt`.

- [ ] **Step 7: Run package, semantic, atomicity, and flagship tests**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=CampaignManifestV2ContractTest,SessionSectionAdapterTest,CampaignPackageValidationPipelineTest,CampaignImportAtomicityTest,CampaignCompleteRoundTripTest,CampaignSemanticComparatorTest test
```

Expected: PASS; open session state survives import → export → import, idle packages remain null, and failure in deferred session refs rolls back all campaign state.

- [ ] **Step 8: Commit session package recovery**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/packagev2 \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2 \
  src/main/resources/schemas/campaign-format-v2.schema.json \
  src/test/resources/campaigns/v2 src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2
git commit -m "feat: round-trip open session state"
```

### Task 9: Prove the complete cockpit flow and publish honest status

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java`
- Modify: `docs/campaign-format-v2.md`
- Modify: `docs/campaign-capabilities.md`
- Modify: `docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md`

**Interfaces:**
- Browser proof covers initial selection, start, refresh, scene step, encounter activation/turn, independent map presentation, quick note, pause/resume, review edit, retry after one failed completion, saved session log, curtain, export/import, and restored state.
- Performance evidence measures `DOMContentLoaded` to `#battleCanvasWrap`/empty-state visibility and asserts under 2 seconds on the synthetic fixture.
- Documentation distinguishes baseline cockpit support from deferred structured scenes/quests/rewards.

- [ ] **Step 1: Add the guarded end-to-end cockpit test**

```java
@Test
void runsAndRecoversACompleteSessionFromTheCockpit() {
    Page dm = guardedPage();
    dm.navigate(baseUrl + "/campaigns/" + campaignId + "/session");
    assertThat(dm.locator(".session-cockpit").isVisible()).isTrue();
    assertThat(dm.locator("[data-selection-source='ACTIVE_ENCOUNTER']").count()).isEqualTo(1);

    dm.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Start session")).click();
    dm.reload();
    assertThat(dm.locator("[data-session-status='RUNNING']").count()).isEqualTo(1);

    dm.keyboard().press("]");
    expect(dm.locator("[data-current-scene]")).toContainText("Lower Crypt");
    dm.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Activate Crypt Guardians")).click();
    dm.keyboard().press("n");
    expect(dm.locator("[data-active-turn]")).not().toBeEmpty();

    dm.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Show Lower Crypt to table")).click();
    assertThat(presentationService.getCurrentState().mode()).isEqualTo("MAP");
    assertThat(adventureService.getCurrentScene(campaignId).orElseThrow().getTitle()).isEqualTo("Lower Crypt");

    dm.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Pause session")).click();
    dm.reload();
    dm.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Resume session")).click();
    dm.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Review and end session")).click();

    String correlation = "session-complete-retry";
    failOnce(dm, "**/session/complete", "POST", Pattern.compile(".*/session/complete"), correlation);
    Locator body = dm.getByLabel("Review session log");
    body.fill(body.inputValue() + "\nThe western seal remains unresolved.\n");
    dm.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Save session log and end")).click();
    assertThat(body.inputValue()).contains("western seal");
    dm.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Retry")).click();

    assertThat(noteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(campaignId, SESSION_LOG))
            .singleElement().satisfies(note -> assertThat(note.getBody()).contains("western seal"));
    assertThat(presentationService.getCurrentState().mode()).isEqualTo("CURTAIN");
    collector.assertNoUnexpectedFailures();
}
```

Use the existing collector APIs and stable `data-*` selectors; do not inspect Alpine internals.

- [ ] **Step 2: Add restart, reimport, disconnected-player, and keyboard assertions**

In the same smoke class or focused integration tests:

```java
assertThat(reconstructedPresentationService.restoreLatestPresentation().mode()).isEqualTo("MAP");
assertThat(roundTrippedSnapshot.session()).isEqualTo(originalSnapshot.session());
assertThat(sessionService.pause(campaignId).getStatus()).isEqualTo(PAUSED); // no player page exists
```

Test every session shortcut while focus is outside a form and assert no shortcut fires while the draft textarea or a modal owns focus.

- [ ] **Step 3: Add the meaningful-render budget assertion**

```java
double elapsed = (double) dm.evaluate("""
    () => performance.getEntriesByType('navigation')[0].domContentLoadedEventEnd -
          performance.getEntriesByType('navigation')[0].startTime
    """);
assertThat(elapsed).isLessThan(2000.0);
```

Run this against the checked-in published-adventure-shaped fixture on the local test server. If CI hardware makes this unstable, record the timing as a separately tagged performance test but keep the 2000 ms assertion; do not loosen the master-spec budget.

- [ ] **Step 4: Run the focused cockpit verification**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit \
  -Dtest=Session*Test,CoreSessionLoopSmokeTest,PlayerSafeProjection*Test,InteractionFailureContractTest test
```

Expected: PASS with zero failures/errors; the browser collector reports no console, page, malformed-request, or unexpected HTTP failures.

- [ ] **Step 5: Run the complete isolated suite**

```bash
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-session-cockpit test
```

Expected: PASS with zero failures and zero errors. Record the actual test count in the commit message body or verification report.

- [ ] **Step 6: Verify route, duplication, schema, and diff invariants**

```bash
rg -n "redirect:/campaigns/.*/maps|maps/battle|/api/v1/table/presentation|catch\s*\([^)]*\)\s*\{\s*\}" src/main src/test
rg -n '"session"|SESSION_SCENE_VISIT|order\(\).*1150' \
  src/main/resources/schemas/campaign-format-v2.schema.json src/main/java
git diff --check
git status --short
```

Expected: the first command finds no legacy redirect/template/unscoped presentation/empty-catch occurrences in the cockpit slice; the second finds the schema, content type, and adapter; diff check exits 0; status lists only intended files.

- [ ] **Step 7: Publish format and capability documentation**

Add an **Open session state** section to `docs/campaign-format-v2.md` describing statuses, nullable idle state, typed refs, presentation invariants, timestamps, attendance, visits, and deterministic recovery. Update `docs/campaign-capabilities.md` to:

```markdown
| Session cockpit | `SUPPORTED` | Durable start/resume/pause/end coordination across the existing scene, map, encounter, plan, presentation, party, calendar, dice, handout, and quick-note modules; deterministic reviewed session logs and v2 recovery are covered. |
| Structured scene transitions | `UNSUPPORTED` | Delivery item 6; cockpit previous/next remains editorial order. |
| Structured quests/objectives | `UNSUPPORTED` | Delivery item 6; QUEST notes remain descriptive documents. |
| Encounter waves/reward drafts | `UNSUPPORTED` | Delivery item 9; the cockpit activates existing planned encounters only. |
```

Update only Workstream C and delivery item 5 status in the master spec. Do not mark P1/all-in-one readiness complete while structured adventure and custom content gaps remain.

- [ ] **Step 8: Commit the verified cockpit checkpoint**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/BrowserFailureCollector.java \
  docs/campaign-format-v2.md docs/campaign-capabilities.md \
  docs/superpowers/specs/2026-07-15-all-in-one-dm-readiness-design.md
git commit -m "docs: publish unified session cockpit checkpoint"
```

## Completion Gate

This plan is complete only when:

- `/campaigns/{campaignId}/session` renders the cockpit and never chooses the first sorted map merely because it is first;
- initial selection follows active encounter → current scene → first resolvable session-plan scene/map → explicit map → useful empty state;
- refresh/restart restores stored workspace, lifecycle, attendance, scene visits, draft, and presentation state;
- current scene, active encounter, workspace map, player presentation, and session plan remain independent and visibly coordinated;
- the battle map and encounter tracker each have one runtime implementation;
- the DM can navigate scenes, activate/run an encounter, take quick notes, search, roll dice, present a map/handout/curtain, view party passives/date, and use the plan without leaving the cockpit;
- all core actions have keyboard equivalents and do not fire while typing or while a modal owns focus;
- failure paths retain or restore local state, display a correlation ID, and expose a retry action;
- start/resume/pause/review/complete transitions reject illegal states and cross-campaign references;
- end review produces only deterministic evidence, remains editable, and saves exactly one DM-only `SESSION_LOG` atomically;
- no player device is required for any cockpit action;
- player payloads contain no DM-only scene, note, token, handout, or session-draft data;
- open session state round-trips through default format-v2 export/import using stable typed refs;
- V4 migrates both fresh and legacy databases without changing existing campaign data;
- cockpit meaningful render is under 2 seconds on the reference synthetic campaign;
- all focused browser, contract, security, migration, package, and full-suite tests pass;
- documentation marks only the implemented cockpit baseline supported and preserves every delivery-item-6+ limitation.

## Spec Coverage Review

- Master §4.1: quick-access rails and keyboard/named controls enforce the two-action session UX.
- Master §4.2: this slice labels unstructured body as scene notes and adds typed state only where the app acts.
- Master §§4.3–4.5: session/package refs are typed and all persistent state is exported or explicitly reset to a session-log note.
- Master §§4.6–4.8: no online dependency is added, estimates/provenance are not changed, and player projection stays server-safe.
- Master §§8.1–8.4: Tasks 3–7 cover every layout, selection, lifecycle, end-draft, independence, refresh, keyboard, and player-safety acceptance criterion.
- Master §§17.1–17.4: Task 8 uses a module adapter, Task 1 uses typed persistence and Flyway, and existing destination/reference contracts are reused.
- Master §§20–21: Tasks 4, 6, 8, and 9 cover correctness, performance, accessibility, security, data safety, contract, round-trip, browser, and upgrade verification.
- Deferred by the master delivery order: structured scene/quest content, custom compendium breadth, character completion, encounter waves/rewards, published-map calibration, and P3 subsystems.

## Type and Naming Consistency Review

- Java entity: `CampaignSession`; package DTO: `SessionDto`; content type: `SESSION`; adapter section: `session`; JSON property: `session`.
- Java entity: `SessionSceneVisit`; package DTO: `SessionSceneVisitDto`; content type: `SESSION_SCENE_VISIT`; JSON property: `sceneVisits`.
- Lifecycle statuses are exactly `IDLE`, `RUNNING`, `PAUSED`, `REVIEW`; v2 excludes `IDLE` by encoding the section as null.
- Presentation modes are exactly `CURTAIN`, `MAP`, `HANDOUT` in persistence, DTO, schema, service, JavaScript, and player state.
- Adapter order `1150` is after adventure/note/calendar refs and before dice order `1200`.
- All coordinates and map behavior remain owned by the existing map DTO and `BattleMap`; the session DTO stores refs, never coordinates.

## Next Plan Boundary

After this gate, write the separate **Structured Adventure and Quest Model** design/implementation plan from delivery item 6. It may replace editorial-only scene stepping with typed transitions and enrich the story rail, but it must migrate and extend this cockpit rather than couple structured content back into the map or encounter runtime.
