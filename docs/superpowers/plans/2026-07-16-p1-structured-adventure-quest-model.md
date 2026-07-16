# Structured Adventure and Quest Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver delivery item 6 of the all-in-one DM readiness specification: preserve the existing Adventure → Chapter → Scene hierarchy while adding optional structured scene content, typed runtime transitions, campaign-scoped quests and objectives, session evidence for objective changes, and complete campaign-package-v2 round-trip support.

**Architecture:** Keep prose in the existing Scene.body field and add normalized, ordered child entities for structured sections, checks, participants, transitions, and links. Add campaign-owned Quest and QuestObjective entities with explicit dependency edges and no automatic progression. Extend the existing package-v2 manifest and section-adapter pipeline with optional fields, stable package keys, deferred typed references, schema validation, semantic validation, and round-trip comparison. Extend the existing DM scene pages and session cockpit without adding structured DM data to player-facing projections.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Data JPA/Hibernate, Flyway, H2 test database, Jackson, Thymeleaf, HTMX, existing session-cockpit JavaScript, JSON Schema draft 2020-12, Maven Wrapper, JUnit/AssertJ/MockMvc, Playwright smoke coverage.

## Global Constraints

- Preserve the current Adventure → Chapter → Scene hierarchy, Scene status, editorial previous/next stepping, Markdown body, map/encounter/statblock/handout links, and current-scene cursor.
- Treat Scene.body as scene notes during this delivery. Do not silently reinterpret or overwrite it as a structured read-aloud block.
- All structured fields are optional. A legacy v1 package and an existing v2 package without the new properties must import with empty structured collections and no invented content.
- Use a reviewed Flyway migration. Do not rely on Hibernate schema generation or implicit updates.
- Every package reference is a ContentReference. Campaign-owned references use package keys; catalog references use the existing ruleset/source-key contract.
- Scene.sceneKey already exists as a nullable varchar(50) and is the user-facing wiki/palette key consumed by CommandPaletteService, NoteService wiki links, MapPinApiController, CampaignSemanticValidator, and the v1 export DTO. It is **not** the package key and it is not a transition identifier. Leave its column, semantics, and callers unchanged; resolve package identity only through CampaignPackageKeyService, and do not derive the new mapRegionKey from it.
- Every enum in this delivery is a published external contract through the v2 schema. Use exactly the values in the Enum contract section below. Do not add, rename, or reorder values during implementation without updating that section, the schema, and docs/campaign-format-v2.md together.
- Reject invalid or ambiguous source conversions. Do not invent DCs, rules, map coordinates, target scenes, NPC/statblock duplicates, or timeline changes. Persist an explicit SourceAnnotation when an imported field remains unresolved.
- A transition is a runtime action only when it has an in-campaign target scene. Editorial previous/next remains a separate navigation mode. Entrance/exit rows that point to an external destination are displayed as notes and cannot be followed as runtime actions.
- Quest objective state changes are explicit DM actions. Dependency edges describe prerequisites and branching; they never auto-complete, auto-unlock, mutate campaign clocks, or create timeline events.
- Keep all new structured content in the DM-authorized server surface. Player-safe projections must not expose DM advice, secrets, checks/outcomes, source annotations, transition conditions/DM notes, quest prerequisites/rewards, or unresolved source metadata.
- Keep package export deterministic: ordered child collections use their stored order; unordered reference collections use the existing comparator rules; no UUIDs or generated timestamps are emitted as semantic identity.
- Complete every task with focused tests before moving to the next task. Use the existing package-v2 adapters and validation pipeline rather than adding a parallel import/export path.

---

## Audit result: what is already implemented

The master design's delivery decomposition is the source of truth for the next step. Repository evidence as of 2026-07-16 is:

| Spec delivery/workstream | Status in the repository | Evidence and boundary |
|---|---|---|
| 1. P0 runtime reliability | Implemented | Existing interaction-integrity and runtime-reliability plans, visible failure handling, quick-note coverage, command-palette routing, and capability documentation. |
| 2. Campaign contract v1 repair | Implemented | Campaign DTO/schema/semantic validation and legacy import/export tests are present and passing. |
| 3. Package v2 foundation | Implemented | ZIP/JSON reader/writer, safety checks, stable package keys, catalog references, section registry, deferred imports, preview, and atomic persistence are present. |
| 4. Complete round-trip | Implemented for all currently persisted state | The complete round-trip and direct persistence projection cover the current adventure hierarchy, notes, maps, encounters, party, ledger, calendar, dice, and open session state. |
| 5. Unified session cockpit | Implemented | /campaigns/{campaignId}/session, deterministic map selection, session lifecycle, workspace map/presentation, story/encounter rails, session plan, recovery, keyboard actions, and draft generation are present. |
| Structured adventure scene model | Partial | Adventure → Chapter → Scene, Markdown body, status, editorial stepping, map/encounter/statblock/handout links, and map pins exist. Typed transitions, structured sections/checks/participants, source locators, unresolved annotations, and scene links do not. |
| Structured quests/objectives | Unsupported | There is no Quest or QuestObjective entity, package section, objective dependency graph, or objective-change session evidence. Legacy QUEST notes remain the only quest-shaped data. |
| Workstreams E–L | Partial | Custom statblocks and existing character/encounter/map/session foundations are present, but campaign-scoped custom content, character completion, depth work, docs/agent SDK, and P3 subsystems remain outside this delivery. |

Verification already run before writing this plan:

~~~text
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit test
Result: exit code 0; 795 reported tests across Surefire XML files; 0 failures; 0 errors.
Note: the environment logs Playwright host-library warnings. The Maven suite still completed successfully; browser coverage must be rerun in the target environment as a release gate.
~~~

The next step is therefore delivery item 6, not another session-cockpit rewrite.

## Delivery item 6 acceptance contract

- [ ] A scene can store optional summary, source locator, tags, map-region hint, ordered read-aloud/advice/secrets/features/treasure/development/scaling sections, checks, participants, links, and typed transitions without losing Scene.body.
- [ ] A transition has a stable key, label, kind, optional condition/DM note/source locator, and either an in-campaign target scene or an external destination according to its kind.
- [ ] The session cockpit presents outgoing runtime choices separately from editorial previous/next and follows only an explicitly selected in-campaign transition.
- [ ] A campaign can store quests, ordered objectives, explicit objective dependencies, rewards/prerequisites/outcomes, a quest giver as a typed GIVER link, and links to existing scenes/notes/timeline entries/catalog rules.
- [ ] A scene can record traps, hazards, environmental effects, and puzzles as typed sections, satisfying master spec 9.2 without inventing mechanical automation reserved for delivery item 9.
- [ ] Every enum reaching the package is constrained by an explicit schema enum list that a contract test proves equal to its Java enum.
- [ ] A DM can change an objective status explicitly; changes made during a running session appear in the deterministic session draft and survive package export/import.
- [ ] Structured scene content, transitions, quests, objectives, annotations, and session objective changes survive v2 export → import → export with semantic and direct-persistence comparison.
- [ ] Existing v1 and v2 fixtures without item-6 fields remain valid and importable.
- [ ] Invalid transitions, cross-campaign references, duplicate keys, broken catalog references, malformed dependency graphs, missing required transition targets, and unresolved checks without annotations are rejected before persistence.
- [ ] Player-safe endpoints remain free of the new DM-only fields, and the browser smoke path reports no console errors or silent action failures.

## File map

| Area | Files to add | Files to modify |
|---|---|---|
| Database | src/main/resources/db/migration/V5__add_structured_adventure_quest.sql | src/test/java/dev/hendrikhoemberg/dmhelper/common/config/FlywayMigrationTest.java, FlywayLegacyUpgradeTest.java |
| Adventure model | adventure/data/SceneSection.java, SceneSectionKind.java, SceneCheck.java, SceneCheckVisibility.java, SceneParticipant.java, SceneParticipantDisposition.java, SceneTransition.java, SceneTransitionKind.java, SceneLink.java, SceneLinkTargetScope.java, SceneSectionRepository.java, SceneCheckRepository.java, SceneParticipantRepository.java, SceneTransitionRepository.java, SceneLinkRepository.java | adventure/data/Scene.java, AdventureService.java, SceneRefCleaner.java |
| Quest model | quest/data/Quest.java, QuestStatus.java, QuestObjective.java, QuestObjectiveStatus.java, QuestObjectiveCompletionMode.java, QuestObjectiveDependency.java, QuestLink.java, QuestLinkRole.java, quest/data/QuestRepository.java, QuestObjectiveRepository.java, QuestObjectiveDependencyRepository.java, QuestLinkRepository.java, quest/service/QuestService.java, QuestObjectiveDependencyValidator.java | campaign/packagev2/key/CampaignContentType.java, session services and cleaners |
| Source fidelity/session evidence | campaign/data/SourceAnnotation.java, SourceAnnotationConfidence.java, SourceAnnotationStatus.java, SourceAnnotationRepository.java; session/data/SessionObjectiveChange.java, SessionObjectiveChangeRepository.java | SessionActivityRecorder.java, SessionDraftService.java, SessionReferenceCleaner.java |
| Package v2 model | None beyond the existing manifest record | campaign/packagev2/model/CampaignManifestV2.java, CampaignManifestAssembler.java, ContentReference validation helpers |
| Package v2 adapters/validation | quest/packagev2/QuestSectionAdapter.java, campaign/packagev2/SourceAnnotationSectionAdapter.java, campaign/packagev2/ContentReferenceTargetCodec.java | adventure/packagev2/AdventureSectionAdapter.java, session/packagev2/SessionSectionAdapter.java, CampaignImportContext.java, CampaignManifestV2SemanticValidator.java, CampaignSemanticSnapshotService.java, CampaignSemanticComparator.java |
| Package schema/fixtures | src/test/resources/campaigns/v2/structured-adventure-quest.dmcampaign/manifest.json and asset fixtures | src/main/resources/schemas/campaign-format-v2.schema.json, campaign-format-v2.md and package contract tests |
| DM authoring UI | adventure/service/SceneStructuredContentService.java, scene structured fragments; quest/web/QuestController.java, quest/web/QuestApiController.java, quest templates | SceneController.java, session/web/SessionApiController.java, SessionWorkspaceService.java, scene/session templates, session-cockpit.js |
| Tests/docs | StructuredAdventurePersistenceTest.java, QuestPersistenceTest.java, SessionObjectiveChangeRepositoryTest.java, SceneStructuredContentServiceTest.java, SceneTransitionServiceTest.java, QuestServiceTest.java, QuestObjectiveDependencyValidatorTest.java, **SessionActivityRecorderTest.java (new — the class has no test today)**, **SessionReferenceCleanerTest.java (new — the class has no test today)**, QuestSectionAdapterTest.java, SourceAnnotationSectionAdapterTest.java, SceneStructuredTemplateContractTest.java, QuestControllerTest.java, QuestTemplateContractTest.java | AdventureServiceTest.java, SceneRefCleanerTest.java, SessionDraftServiceTest.java, CampaignCascadeDeleteTest.java, cockpit/security/smoke tests, docs/campaign-capabilities.md, the master spec status row after release |

## Enum contract

These values are the authoritative source for the Java enums (Task 1.7), the schema enum
constraints (Task 4.4), and the documentation (Task 6.1). Persist all of them with
`@Enumerated(EnumType.STRING)` and emit them verbatim in the manifest. No value carries automatic
behavior: none of them advance a scene, complete an objective, widen a player-safe payload, or
change a campaign clock.

~~~java
// Scene sections. Covers every narrative block type required by master spec 9.2.
public enum SceneSectionKind {
    READ_ALOUD,    // boxed/player-facing text the DM reads aloud
    DM_ADVICE,     // DM exposition, tactics, and guidance
    SECRET,        // discoverable information hidden until found
    FEATURE,       // static location features and dressing
    TRAP,          // trap description; no mechanical automation
    HAZARD,        // environmental hazard description
    ENVIRONMENT,   // ambient/environmental effects
    PUZZLE,        // puzzle description and solution
    TREASURE,      // treasure and rewards found in the scene
    DEVELOPMENT,   // how the scene evolves during play
    CONSEQUENCE,   // downstream results of the scene
    SCALING        // party-size/level scaling guidance
}

// Whether the DM normally reveals a check to the table. Advisory only: every check row stays
// DM-only in player-safe projections regardless of this value.
public enum SceneCheckVisibility {
    PLAYER_FACING, // DM may state the check and DC openly
    DM_FACING,     // resolved without telling the players
    PASSIVE        // compared against a passive score, never rolled
}

public enum SceneParticipantDisposition {
    HOSTILE, UNFRIENDLY, NEUTRAL, FRIENDLY, ALLY, UNKNOWN
}

public enum SceneTransitionKind {
    CHOICE,    // runtime branch; requires an in-campaign target scene
    ENTRANCE,  // how the party arrives; exactly one of target/external
    EXIT       // how the party leaves; exactly one of target/external
}

// Mirrors ContentReference.Scope so polymorphic link rows round-trip without a second vocabulary.
public enum SceneLinkTargetScope { PACKAGE, CATALOG }

// Authoring purpose of a link. The target's CampaignContentType travels in targetRef; the semantic
// validator rejects a role/target-type combination that contradicts itself.
public enum SceneLinkRole {
    REFERENCE, HANDOUT, RULE, QUEST, TIMELINE_EVENT, RELATED_SCENE, NPC, LOCATION
}

public enum QuestStatus {
    NOT_STARTED, ACTIVE, ON_HOLD, COMPLETED, FAILED, ABANDONED
}

public enum QuestObjectiveStatus {
    NOT_STARTED, ACTIVE, COMPLETED, FAILED, SKIPPED
}

// How this objective's inbound dependency edges are read. Descriptive documentation of the source's
// branching; the DM still sets every status explicitly.
public enum QuestObjectiveCompletionMode {
    ALL, // every prerequisite objective is expected first
    ANY  // any one prerequisite objective is expected first
}

// GIVER satisfies the quest giver required by master spec 9.4; there is no giver column.
public enum QuestLinkRole {
    GIVER, REFERENCE, HANDOUT, RULE, RELATED_SCENE, NPC, LOCATION, FACTION, TIMELINE_EVENT, REWARD
}

public enum SourceAnnotationConfidence { HIGH, MEDIUM, LOW, UNKNOWN }

public enum SourceAnnotationStatus { OPEN, RESOLVED, DISMISSED }
~~~

Contract notes:

- **Quest giver** is a QuestLink with role GIVER pointing at a NOTE or STATBLOCK, not a scalar
  field. A quest may hold at most one GIVER link; the semantic validator enforces this.
- **Traps, hazards, environmental effects, and puzzles** are SceneSectionKind values in this
  delivery — prose blocks with an optional source locator. Typed trap/hazard mechanics and
  non-creature initiative entries stay in delivery item 9 and must not be inferred from these
  sections.
- **NPC and LOCATION roles** target existing notes. First-class world entities are delivery item 11
  (master spec 14.3); do not create entity tables for them here.
- **SceneSectionKind is not a uniqueness constraint.** A scene may hold several sections of the
  same kind; stored sort order is the only ordering.

## Implementation tasks

### 1. Lock the persistence contract and add Flyway V5

Start with failing persistence tests. The tests must prove that the new model is additive and that deleting a scene, quest, or session cannot leave orphaned structured rows.

- [ ] 1.1 Add src/test/java/dev/hendrikhoemberg/dmhelper/adventure/data/StructuredAdventurePersistenceTest.java with a campaign containing one scene and one row of every structured child type. Assert that summary/sourceLocator/tags/mapRegionKey, section order, check fields, participant fields, transition fields, and link target fields reload unchanged.
- [ ] 1.2 Add src/test/java/dev/hendrikhoemberg/dmhelper/quest/data/QuestPersistenceTest.java covering quest status, objective order, completion mode, dependency rows, links, source annotations, and nullable legacy fields.
- [ ] 1.3 Extend FlywayMigrationTest to assert V5 is the latest migration and that all new tables/columns exist. Extend FlywayLegacyUpgradeTest with a V1 → V5 upgrade containing an old scene and no structured rows.
- [ ] 1.4 Add deletion assertions to SceneRefCleanerTest, CampaignCascadeDeleteTest, and a new SessionObjectiveChangeRepositoryTest: deleting a scene cascades owned sections/checks/participants/links/transitions, removes inbound transition/link references, deleting a quest cascades objectives/dependencies/links, and deleting a session cascades objective changes.
- [ ] 1.5 Run the focused tests and confirm they fail for the missing schema/entities:

~~~text
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit -Dtest=StructuredAdventurePersistenceTest,QuestPersistenceTest,FlywayMigrationTest,FlywayLegacyUpgradeTest,SceneRefCleanerTest,CampaignCascadeDeleteTest,SessionObjectiveChangeRepositoryTest test
Expected before implementation: compilation or missing-table failures identifying each new contract.
~~~

- [ ] 1.6 Add src/main/resources/db/migration/V5__add_structured_adventure_quest.sql. It must:
  - add nullable summary varchar(2000), source_locator varchar(500), tags varchar(1000), and map_region_key varchar(100) to adventure_scene;
  - create scene_section(id, scene_id, kind, label, body, source_locator, sort_order) with a scene cascade foreign key and a unique (scene_id, sort_order);
  - create scene_check(id, scene_id, label, ability, skill, dc, visibility, success, failure, partial, rule_scope, rule_ruleset, rule_source_key, source_locator, sort_order) with a scene cascade foreign key and a unique (scene_id, sort_order);
  - create scene_participant(id, scene_id, display_name, quantity, disposition, placement_hint, statblock_id, note_id, source_locator, sort_order) with scene/statblock/note foreign keys and a scene cascade foreign key;
  - create scene_transition(id, scene_id, kind, label, target_scene_id, external_destination, condition, dm_note, source_locator, sort_order) with scene/target-scene foreign keys and a unique (scene_id, sort_order);
  - create scene_link(id, scene_id, role, target_scope, target_type, target_id, catalog_ruleset, catalog_source_key, display_text, condition, sort_order) with a scene cascade foreign key;
  - create quest(id, campaign_id, title, status, summary, source_locator, tags, rewards, prerequisites, outcome_notes, created_at) and quest_objective(id, quest_id, title, description, status, completion_mode, sort_order, source_locator) with campaign/quest cascade foreign keys and unique (quest_id, sort_order);
  - create quest_objective_dependency(quest_id, objective_id, prerequisite_objective_id) with cascade foreign keys and a unique composite primary key;
  - create quest_link(id, quest_id, role, target_scope, target_type, target_id, catalog_ruleset, catalog_source_key, display_text, condition, sort_order) with quest cascade foreign keys;
  - create source_annotation(id, campaign_id, owner_type, owner_id, field_path, message, confidence, source_locator, status, resolution_note, created_at) with a campaign cascade foreign key and an index on (campaign_id, owner_type, owner_id);
  - create session_objective_change(id, session_id, objective_id, previous_status, new_status, changed_at) with session/objective foreign keys and an index on (session_id, changed_at, id);
  - use on delete cascade for aggregate-owned rows and retain nullable target columns for polymorphic package/catalog links. A database constraint must ensure a transition target cannot be combined with an external destination. Follow the existing V4 style (`ck_campaign_session_presentation`) for that check constraint rather than enforcing the rule only in Java.

  Known forward-compat cost, recorded deliberately: `adventure_scene.map_region_key` is an unvalidated free-text hint with no foreign key, because named map regions do not exist until delivery item 9 (master spec 13.1). Do not invent a region registry here. When item 9 lands, this column becomes a real reference and needs its own migration; state that boundary in docs/campaign-format-v2.md (Task 6.1) so the schema does not imply a guarantee the app cannot check.
- [ ] 1.7 Add the JPA entities and enums under the paths in the file map, taking every enum value verbatim from the Enum contract section. Use UUID identifiers, explicit @Enumerated(EnumType.STRING), length limits matching the migration, aggregate ownership with cascade = CascadeType.ALL, orphanRemoval = true, and explicit @OrderBy("sortOrder ASC") collections. Keep Scene.body and Scene.sceneKey unchanged.
- [ ] 1.8 Add repositories with campaign-scoped queries, deterministic ordering, and target lookups. Required query shapes are:

~~~java
List<SceneTransition> findBySceneIdOrderBySortOrderAsc(UUID sceneId);
List<SceneTransition> findByTargetSceneId(UUID sceneId);
List<Quest> findByCampaignIdOrderByCreatedAtAscIdAsc(UUID campaignId);
List<QuestObjective> findByQuestIdOrderBySortOrderAsc(UUID questId);
List<SessionObjectiveChange> findBySessionIdOrderByChangedAtAscIdAsc(UUID sessionId);
~~~

- [ ] 1.9 Add TagCodec in the adventure package or a shared common package. Match the existing comma-separated tag convention used by handouts/notes, trim empty values, preserve deterministic order, and expose parse(String)/format(List<String>).
- [ ] 1.10 Run the focused tests again. Expected result: exit code 0, all focused tests pass, and the V1 → V5 migration leaves old scenes with null/empty item-6 fields.

### 2. Implement structured scene authoring and typed transition runtime behavior

The domain service must enforce ownership and transition invariants before any controller or package adapter is added.

- [ ] 2.1 Add failing SceneStructuredContentServiceTest cases for campaign ownership, required labels/bodies, deterministic renumbering, optional source locators, tag normalization, and CRUD for sections/checks/participants/links.
- [ ] 2.2 Add failing SceneTransitionServiceTest cases for:
  - CHOICE requiring a same-campaign target scene and rejecting an external destination;
  - ENTRANCE/EXIT accepting exactly one target scene or external destination;
  - rejecting a target scene from another campaign;
  - preserving transition order and stable package-key cleanup on deletion;
  - following a transition changing the campaign current-scene cursor and recording the same scene visit behavior as existing session navigation;
  - refusing to follow an external-only transition;
  - leaving editorial stepCurrentScene(campaignId, direction) behavior unchanged.
- [ ] 2.3 Implement SceneStructuredContentService with explicit command records:

~~~java
public record SceneMetadataCommand(
        String summary, String sourceLocator, String tags, String mapRegionKey) {}

public record SceneSectionCommand(
        SceneSectionKind kind, String label, String body,
        String sourceLocator, int sortOrder) {}

public record SceneCheckCommand(
        String label, String ability, String skill, Integer dc,
        SceneCheckVisibility visibility, String success, String failure,
        String partial, String ruleScope, String ruleRuleset,
        String ruleSourceKey, String sourceLocator, int sortOrder) {}

public record SceneParticipantCommand(
        String displayName, int quantity, SceneParticipantDisposition disposition,
        String placementHint, UUID statBlockId, UUID noteId,
        String sourceLocator, int sortOrder) {}

public record SceneTransitionCommand(
        SceneTransitionKind kind, String label, UUID targetSceneId,
        String externalDestination, String condition, String dmNote,
        String sourceLocator, int sortOrder) {}
~~~

Expose campaign-checked methods for metadata update, section/check/participant/link/transition create/update/delete, and list methods used by both the DM scene page and session workspace. Each mutation loads the scene through a campaign-scoped query and renumbers the affected ordered collection after removal.
- [ ] 2.4 Implement SceneTransitionService.follow(UUID campaignId, UUID transitionId) and delegate from AdventureService through:

~~~java
@Transactional
public Scene followTransition(UUID campaignId, UUID transitionId) {
    SceneTransition transition = transitions.findByIdAndSceneCampaignId(transitionId, campaignId)
            .orElseThrow(() -> new NotFoundException("Transition not found in campaign"));
    if (transition.getTargetScene() == null) {
        throw new IllegalArgumentException("This transition has no runtime target scene");
    }
    Scene target = transition.getTargetScene();
    return setCurrentScene(campaignId, target.getId());
}
~~~

Keep stepCurrentScene as the editorial predecessor/successor path. followTransition must never select the first sorted scene, infer a target, or mutate scene status automatically.
- [ ] 2.5 Extend SceneRefCleaner to delete owned transition bindings, remove inbound transitions that target a deleted scene, remove polymorphic links whose target is the deleted scene, and delete source annotations owned by the deleted scene or its deleted transitions. Add SceneTransition/SceneLink repository methods needed for this cleanup.
- [ ] 2.6 Add SceneController handlers for metadata and each repeatable structured child using the existing HTMX fragment pattern. Every handler returns the updated scene detail/action fragment and preserves the submitted values when validation fails through the existing visible-error mechanism.
- [ ] 2.7 Run:

~~~text
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit -Dtest=SceneStructuredContentServiceTest,SceneTransitionServiceTest,AdventureServiceTest,SceneRefCleanerTest,SceneControllerTest test
Expected: exit code 0; editorial stepping and existing scene links remain green, and transition follow tests cover only explicit target scenes.
~~~

### 3. Implement quests, objective dependencies, and session evidence

Quests are campaign content, not a replacement for legacy QUEST notes. The service owns validation; the session recorder owns historical evidence.

- [ ] 3.1 Add failing QuestServiceTest cases for quest CRUD, status changes, objective ordering, link CRUD, package-key-safe campaign ownership, and explicit DM status changes.
- [ ] 3.2 Add failing QuestObjectiveDependencyValidatorTest cases for same-quest enforcement, self-dependency, duplicate edges, cycles, missing prerequisites, and valid ALL/ANY branching. Use a depth-first traversal over objective keys/IDs and return a field-specific validation problem for every invalid edge.
- [ ] 3.3 Add failing SessionActivityRecorderTest (a new file; SessionActivityRecorder has no test today) for objective-change recording: one row per real status change, no row when the status is unchanged, no row when no session is running, and a nullable previous status on first change. Extend the existing SessionDraftServiceTest for deterministic chronological objective-change output and repeated changes to the same objective.
- [ ] 3.4 Implement QuestService with commands and methods:

~~~java
public record QuestCommand(
        String title, QuestStatus status, String summary, String sourceLocator,
        String tags, String rewards, String prerequisites, String outcomeNotes) {}

public record QuestObjectiveCommand(
        String title, String description, QuestObjectiveStatus status,
        QuestObjectiveCompletionMode completionMode, int sortOrder,
        String sourceLocator) {}

@Transactional
public QuestObjective setObjectiveStatus(
        UUID campaignId, UUID objectiveId, QuestObjectiveStatus nextStatus);

@Transactional
public QuestObjectiveDependency addDependency(
        UUID campaignId, UUID objectiveId, UUID prerequisiteObjectiveId);
~~~

The concrete implementation must validate that all IDs belong to the campaign, reject dependency cycles before saving, renumber objective order after edits, and keep legacy NoteType.QUEST rows untouched. A DM may explicitly set any objective status; the dependency graph is descriptive and is never advanced automatically.

QuestCommand carries no giver field. The quest giver required by master spec 9.4 is a QuestLink with role GIVER targeting a NOTE or STATBLOCK, so a giver survives export/import as a typed reference instead of a duplicated name string. QuestService must reject a second GIVER link on the same quest, and QuestServiceTest must cover adding, replacing, and clearing it.
- [ ] 3.5 Add SessionObjectiveChange recording to SessionActivityRecorder. When a running session exists, write one row for a real status change with previous status, next status, and Instant from the service transaction. Do not record unchanged writes or changes made while no session is running. Keep the session objective change linked to the objective and session for package references.
- [ ] 3.6 Extend SessionDraftService with a Quest Progress section after Scenes and before encounter/loot sections. Sort rows by changedAt ASC, id ASC, render quest title/objective title/previous status/new status, and keep all existing draft sections and headings stable.
- [ ] 3.7 Extend SessionReferenceCleaner and campaign/quest cascade behavior so session objective changes cannot retain deleted objective/session IDs. Add package-key deletion for SESSION_OBJECTIVE_CHANGE, QUEST, OBJECTIVE, and SOURCE_ANNOTATION. Add SessionReferenceCleanerTest as a new file — the class has no test today, so its existing presentation/plan cleanup behavior must be pinned in the same pass as the new objective-change cleanup.
- [ ] 3.8 Run:

~~~text
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit -Dtest=QuestServiceTest,QuestObjectiveDependencyValidatorTest,SessionActivityRecorderTest,SessionDraftServiceTest,SessionReferenceCleanerTest test
Expected: exit code 0; valid branching persists, cycles fail before persistence, and the draft includes only real running-session changes.
Note: SessionActivityRecorderTest and SessionReferenceCleanerTest must exist from tasks 3.3 and 3.7 before this command runs. Surefire fails the run with "No tests were executed" if a -Dtest name matches nothing, so never list a class before it is created.
~~~

### 4. Extend the package-v2 contract, adapters, validation, and fidelity gate

Add the Java contract and schema before changing adapters. Existing v2 JSON remains valid by making item-6 properties optional on input; newly exported manifests emit empty arrays for complete section shape.

- [ ] 4.1 Add failing contract tests to CampaignManifestV2ContractTest for:
  - old minimal/current/published-adventure fixtures without quests or annotations;
  - a structured scene with sections, a check with a catalog rule reference, a participant with a package statblock reference, a choice transition, an external exit, and a source annotation;
  - a quest with ordered objectives, ALL and ANY dependencies, package links, catalog rule links, and a session objective change;
  - closed-object rejection for unknown fields and enum rejection for unknown kinds/statuses.
- [ ] 4.2 Extend CampaignManifestV2 with optional fields appended to the existing records so Java call sites remain easy to migrate:

~~~java
public record SceneDto(
        String key, String title, String body, String status, int sortOrder,
        ContentReference mapRef, Map<String, Integer> pin,
        ContentReference encounterRef, List<ContentReference> statblockRefs,
        List<ContentReference> handoutRefs,
        String summary, String sourceLocator, List<String> tags,
        String mapRegionKey, List<SceneSectionDto> sections,
        List<SceneCheckDto> checks, List<SceneParticipantDto> participants,
        List<SceneTransitionDto> transitions, List<SceneLinkDto> links) {}

public record QuestObjectiveDto(
        String key, String title, String description, String status,
        String completionMode, int sortOrder,
        List<ContentReference> prerequisiteRefs, String sourceLocator) {}

public record SessionObjectiveChangeDto(
        String key, ContentReference objectiveRef, String previousStatus,
        String newStatus, Instant changedAt) {}
~~~

Add quests and annotations to the top-level manifest, objectiveChanges to SessionDto, and these records with exact fields: SceneSectionDto(kind/label/body/sourceLocator/sortOrder), SceneCheckDto(label/ability/skill/dc/visibility/success/failure/partial/ruleRef/sourceLocator/sortOrder), SceneParticipantDto(displayName/quantity/disposition/placementHint/statblockRef/noteRef/sourceLocator/sortOrder), SceneTransitionDto(key/kind/label/targetSceneRef/externalDestination/condition/dmNote/sourceLocator/sortOrder), SceneLinkDto(role/targetRef/displayText/condition/sortOrder), QuestDto(key/title/status/summary/sourceLocator/tags/rewards/prerequisites/outcomeNotes/links/objectives/createdAt), QuestLinkDto(role/targetRef/displayText/condition/sortOrder), and SourceAnnotationDto(key/ownerRef/fieldPath/message/confidence/sourceLocator/status/resolutionNote/createdAt).
- [ ] 4.3 Update CampaignManifestAssembler with quests(List<QuestDto>) and annotations(List<SourceAnnotationDto>) setters, require both sections for newly assembled manifests, pass List.of() from every existing test builder that does not use item-6 content, and append the fields to the record constructor. Treat absent JSON arrays as empty in adapters.
- [ ] 4.4 Update src/main/resources/schemas/campaign-format-v2.schema.json:
  - leave the existing required top-level list unchanged so old v2 packages validate;
  - add optional quests and annotations array properties;
  - add optional Scene properties and $defs for each new DTO;
  - keep additionalProperties: false on every new object;
  - constrain keys with ^[a-z0-9][a-z0-9._-]{0,99}$, text lengths with the package limits, and quantity/sortOrder to non-negative integers;
  - spell out every enum's members from the Enum contract section as an explicit JSON Schema `enum` list. Do not describe them as "the Java enum values"; the schema is the published contract and must be readable without the source. Add a contract test asserting each schema enum list equals the corresponding Java enum's values, so the two cannot drift;
  - use conditional schema branches so a CHOICE transition requires targetSceneRef and forbids externalDestination, while ENTRANCE/EXIT require exactly one of targetSceneRef/externalDestination;
  - allow a nullable check DC at schema level, with the semantic validator enforcing the source-annotation requirement.
- [ ] 4.5 Add TRANSITION, QUEST, OBJECTIVE, SOURCE_ANNOTATION, and SESSION_OBJECTIVE_CHANGE to CampaignContentType. Extend CampaignSemanticSnapshotService ownership queries with SceneTransition, Quest, QuestObjective, SourceAnnotation, and SessionObjectiveChange using these paths:

~~~java
new OwnershipQuery(CampaignContentType.TRANSITION, SceneTransition.class,
        "scene.chapter.adventure.campaign.id");
new OwnershipQuery(CampaignContentType.QUEST, Quest.class, "campaign.id");
new OwnershipQuery(CampaignContentType.OBJECTIVE, QuestObjective.class,
        "quest.campaign.id");
new OwnershipQuery(CampaignContentType.SOURCE_ANNOTATION, SourceAnnotation.class,
        "campaign.id");
new OwnershipQuery(CampaignContentType.SESSION_OBJECTIVE_CHANGE,
        SessionObjectiveChange.class, "session.campaign.id");
~~~

Add ordered keyed collections transitions and objectives to CampaignSemanticComparator. Keep sections/checks/participants/links ordered by stored sort order and retain the existing ordered handling for statblockRefs/handoutRefs.
- [ ] 4.6 Extend AdventureSectionAdapter to export/import Scene metadata, ordered sections/checks/participants/links, stable-keyed transitions, catalog rule references, and deferred package references. Export Scene.body unchanged. Add QuestSectionAdapter under quest/packagev2 with order 950 and SourceAnnotationSectionAdapter with order 980; update the adapter-order test to assert Adventure 900 → Quest 950 → SourceAnnotation 980 → Notes 1000 and no duplicate section names/orders.
- [ ] 4.7 Extend SessionSectionAdapter to export/import objectiveChanges after quests/objectives are registered. Restore previous/new enum values and timestamps through deferred objective references. Continue refusing DM-only presented handouts on import as the current session adapter does.
- [ ] 4.8 Add semantic validation tests to CampaignManifestV2SemanticValidatorTest for duplicate keys, cross-campaign targets, package/catalog type mismatches, invalid target combinations, missing annotations for unresolved DC/rule/source fields, objective dependency cycles, duplicate dependency edges, invalid owner references, and absent catalog entries. Implement the validator rules before persistence and return field paths such as /adventures/0/chapters/0/scenes/0/transitions/1/targetSceneRef.
- [ ] 4.9 Update LegacyV1ToV2Migration so converted scenes receive null/empty item-6 fields and converted manifests contain empty quests/annotations. Do not convert NoteType.QUEST into a structured Quest automatically.
- [ ] 4.10 Add structured-adventure-quest.dmcampaign based on the existing published-adventure fixture. Include at least two scenes with a choice transition, an external exit, ordered read-aloud/DM-advice/secret sections, a check with a source annotation, a participant linked to an SRD statblock, one quest with two dependency branches, one scene link, and a session objective change.
- [ ] 4.11 Extend AdventureSectionAdapterTest, add QuestSectionAdapterTest and SourceAnnotationSectionAdapterTest, and extend SessionSectionAdapterTest to assert export/import of every new field and deferred reference. Extend CampaignCompleteRoundTripTest to compare the new fixture's manifest and direct persistence projection.
- [ ] 4.12 Add invalid structured packages to CampaignPackageValidationPipelineTest and CampaignImportAtomicityTest. Assert validation fails before campaign rows, child rows, or package-key rows are committed.
- [ ] 4.13 Run:

~~~text
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit -Dtest=CampaignManifestV2ContractTest,CampaignManifestV2SemanticValidatorTest,CampaignPackageValidationPipelineTest,CampaignImportAtomicityTest,AdventureSectionAdapterTest,QuestSectionAdapterTest,SourceAnnotationSectionAdapterTest,SessionSectionAdapterTest,CampaignCompleteRoundTripTest,LegacyV1ToV2MigrationTest test
Expected: exit code 0; old fixtures remain valid, invalid structured packages are atomic failures, and the structured fixture is semantically equivalent after round trip.
~~~

### 5. Add DM authoring pages and session-cockpit runtime integration

The UI must expose the structured model without collapsing it back into one Markdown field, and the cockpit must distinguish runtime choices from editorial stepping.

- [ ] 5.1 Add failing SceneControllerTest and SceneStructuredTemplateContractTest cases for rendering section kinds, checks, participants, source locators, outgoing transitions, map-region hints, validation errors with retained input, and existing quick-note/action-rail behavior.
- [ ] 5.2 Add failing QuestControllerTest and QuestTemplateContractTest cases for quest list/detail, objective ordering, dependency display, status mutation, source annotations, related links, and visible error responses.
- [ ] 5.3 Extend SessionWorkspaceService.SessionWorkspace with a DM-only structured-scene view and active quest progress view loaded through campaign-scoped queries. Use records rather than exposing lazy JPA collections directly:

~~~java
public record StructuredSceneView(
        Scene scene, List<SceneSection> sections, List<SceneCheck> checks,
        List<SceneParticipant> participants, List<SceneTransition> transitions,
        List<SceneLink> links) {}

public record QuestProgressView(
        Quest quest, List<QuestObjective> objectives) {}
~~~

Populate both views for the session route and keep the existing map-selection precedence and session-lifecycle model unchanged.
- [ ] 5.4 Update adventure/scene-detail.html, _scene-form.html, _scene-panel.html, and _action-rail.html:
  - show Scene.body under the existing Scene notes label;
  - render sections grouped by kind with ordered read-aloud blocks and separate blocks for every other SceneSectionKind in the Enum contract (DM advice, secrets, features, traps, hazards, environment, puzzles, treasure, developments, consequences, scaling), rendering a kind's group only when it has rows;
  - render check visibility, outcomes, source locator, participant disposition/quantity/placement, and linked resources;
  - add repeatable HTMX forms for each child type with stable data attributes and delete/reorder actions;
  - render transitions with target title, kind, condition, and DM note in the DM view, while external-only transitions remain non-actionable;
  - retain existing map/encounter/statblock/handout selectors and quick-note actions.
- [ ] 5.5 Add quest/web/QuestController.java, QuestApiController.java, and templates quest/list.html, quest/detail.html, _form.html, _objective-list.html, _dependency-list.html, _link-list.html, and _annotation-list.html. Use campaign-scoped service calls for every mutation and make objective status changes explicit DM actions.
- [ ] 5.6 Extend session/cockpit.html, _story-rail.html, and _session-plan.html to show the current scene summary, ordered DM/read-aloud blocks, actionable outgoing CHOICE transitions, and active quest objectives. Keep editorial previous/next buttons labeled and wired to the existing step endpoint.
- [ ] 5.7 Add POST /api/v1/campaigns/{campaignId}/session/current-scene/follow-transition with:

~~~java
public record FollowTransitionRequest(UUID transitionId) {}
~~~

Resolve the transition through SceneTransitionService, verify that it belongs to the current campaign and has a target scene, call the existing current-scene/session-activity path, and return the same SessionSceneDto shape used by editorial stepping. Keep the existing current-scene/step endpoint unchanged.
- [ ] 5.8 Add the objective status endpoint PUT /api/v1/campaigns/{campaignId}/quests/objectives/{objectiveId}/status with a small status request record. Return the updated objective and the generated session-change identifier when a running session records the change. Reject player/session payloads that attempt to call this DM endpoint.
- [ ] 5.9 Extend session-cockpit.js with followTransition(transitionId) and setObjectiveStatus(objectiveId, status) using the existing dmRequest/reportActionFailure flow. On success, refresh the story rail/workspace; on failure, keep the current scene/objective state and show the server error. Do not add transition or DM-section data to any player WebSocket/table payload.
- [ ] 5.10 Extend SessionCockpitTemplateContractTest, SessionControllerTest, SessionApiControllerTest, SessionCockpitSecurityTest, PlayerSafeProjectionTest, and CoreSessionLoopSmokeTest to assert:
  - the cockpit shows runtime choices and editorial navigation as separate controls;
  - following a transition updates the current scene and records a visit;
  - the objective status change appears in the next session draft;
  - refresh/reconnect preserves the selected scene and structured content;
  - no player-safe JSON contains secrets, DM advice, check outcomes, transition conditions/DM notes, source annotations, or quest prerequisites/rewards;
  - browser console errors and failed action responses are surfaced visibly.
- [ ] 5.11 Run:

~~~text
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit -Dtest=SceneControllerTest,SceneStructuredTemplateContractTest,QuestControllerTest,QuestTemplateContractTest,SessionCockpitTemplateContractTest,SessionControllerTest,SessionApiControllerTest,SessionCockpitSecurityTest,PlayerSafeProjectionTest,CoreSessionLoopSmokeTest test
Expected: exit code 0. If Playwright host libraries are unavailable, the Maven result may report the browser test as skipped; run the same smoke test in the provisioned browser environment before release.
~~~

### 6. Document the contract and close the release gate

- [ ] 6.1 Update docs/campaign-format-v2.md with the new DTO fields, enum values, adapter order, typed-reference rules, conditional transition rules, objective dependency semantics, source-annotation format, session objective-change format, and v1 compatibility behavior. Include one minimal structured-scene and quest JSON example.
- [ ] 6.2 Update docs/campaign-capabilities.md: mark structured scene transitions and structured quests/objectives as SUPPORTED only after the complete round-trip, security, and browser gates pass; leave campaign-scoped custom non-statblock content as the item-7 boundary.
- [ ] 6.3 Update the master spec's delivery item 6 status from PLANNED to IMPLEMENTED only after every acceptance checkbox in this plan is satisfied. Record the actual migration, fixture, and test names in the implementation checkpoint.
- [ ] 6.4 Run focused validation, full tests, whitespace checks, and repository-status checks:

~~~text
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit -Dtest=CampaignCompleteRoundTripTest,CampaignPackageValidationPipelineTest,SessionDraftServiceTest,CoreSessionLoopSmokeTest test
./mvnw -q -DargLine=-Duser.home=/tmp/dmhelper-spec-audit test
git diff --check
git status --short
Expected: all Maven commands exit 0; Surefire reports 0 failures and 0 errors; git diff --check prints no diagnostics; status contains only intended item-6 changes.
~~~
- [ ] 6.5 Perform a final manual audit with rg for empty catches, unhandled structured POST actions, direct player serialization of new entities, and any conversion path that assigns invented defaults. Confirm the existing no-silent-failure interaction contract still applies to every new HTMX/API action.

## Commit checkpoints

Commit after each green checkpoint so failures can be isolated without mixing persistence, package, and UI changes:

1. feat: persist structured adventure and quest content after Task 1.
2. feat: add scene transitions and quest session evidence after Tasks 2–3.
3. feat: round-trip structured adventures and quests in campaign packages after Task 4.
4. feat: add structured adventure authoring and session runtime after Task 5.
5. docs: document structured adventure and quest readiness after Task 6.

Each commit must pass the focused command for its task and git diff --check. Do not mark delivery item 6 implemented or update the capability matrix before the final full-suite and browser gates pass.

## Definition of done

The implementation is ready to hand off when the repository can import old v1/v2 fixtures, author and run a branching structured scene, explicitly update quest objectives during a session, produce deterministic session evidence, export/import/re-export the complete item-6 state, reject invalid or unresolved packages before persistence, and prove through security/template/smoke tests that player-facing payloads remain safe.
