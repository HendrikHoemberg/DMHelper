# Traps and Hazards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Deliver roadmap row 4 as reusable, provenance-aware trap and hazard compendium content with scene, encounter-tracker, DM-map, package-v2, search, documentation, security, and real-browser integration.

**Architecture:** Model Trap and Hazard as separate aggregate roots because their mechanics and invariants differ, while sharing small typed value records, reference resolution, validation helpers, card rendering, and editor behavior. Persist integrations as explicit typed references from scene sections, non-creature combatants, and DM-only map pins; package those references with the existing section-adapter/key system. Resolution remains advisory: cards can prefill the shared dice roller, but never roll or mutate HP, conditions, treasury, or story state automatically.

**Tech Stack:** Java 25+, Spring Boot 4.1, Spring MVC, Spring Data JPA, Flyway, Jackson 3, Thymeleaf, Alpine.js, Playwright, Maven, JUnit 5, AssertJ, MockMvc, JSON Schema draft 2020-12.

## Global Constraints

- The master readiness specification, approved atmosphere design section 5, and canonical roadmap are authoritative.
- Scope is roadmap row 4 and atmosphere items 3–4 only; do not implement fog, music, travel, or autonomous trap resolution.
- Traps/hazards are DM-only. Definitions, mechanics, references, provenance, and map pins must never enter a player page, WebSocket state, map projection, or player asset response.
- Every new DM API remains behind the existing DM PIN boundary and returns visible, sanitized errors.
- Stable package/source keys match ^[a-z0-9][a-z0-9._-]{0,99}$.
- Ownership is SRD read-only, reusable user-global custom, or campaign-scoped custom/imported.
- Campaign content may reference SRD, global, and same-campaign entities. Global content may reference only SRD/global entities.
- Description is sanitized Markdown. Mechanic labels/triggers/failures/effects are escaped typed text.
- Numeric mechanics are individually optional. Present levels are 1–20. DC/passive values use ThreatValidationRules.MIN_DC = 0 and MAX_DC = 40 as representational, not rules-authority, bounds.
- Damage expressions parse through DiceExpressionSpec.parse. Clicking mechanics pre-fills the roller and never submits a roll.
- Trap attack bonus and saving throw are mutually exclusive. AUTOMATIC reset requires timing.
- Hazard exposure is ON_ENTER, START_OF_TURN, PER_ROUND, or CONTINUOUS.
- Scene TRAP sections reference only traps; HAZARD sections reference only hazards. Existing prose-only sections remain valid and unchanged.
- Encounter threat combatants use kind TRAP/HAZARD and at most one matching threat reference.
- Map threat pins are DM-only pixel coordinates bounded by grid width/height × cell size.
- Package format remains v2. Top-level traps/hazards stay optional/default-empty for older v2 packages.
- Every persistent field is exported. Open card/roller/editor state is intentionally transient.
- Run Maven with -Duser.home=/tmp/dmhelper-p3-traps-hazards; never overwrite argLine.
- Preserve unrelated user changes. Every production change follows a failing test and ends in a reviewable commit.

## Architecture Decisions

1. Use separate Trap and Hazard tables, not one discriminator table, so type-specific invariants do not become a nullable union.
2. Use AbstractThreat only as a mapped superclass for identity, ownership, provenance, severity, level band, description, and timestamps.
3. Use one ThreatReference child entity with exactly one owner, role CONDITION or SALVAGE_ITEM, CampaignContentType, target UUID, display text, and order.
4. Store scene/combatant links as ThreatKind + UUID. ThreatReferenceResolver enforces kind, existence, and visibility at every write/import boundary.
5. Store MapThreatPin separately from MapDocumentDto, keeping DM markers outside player projection by construction.
6. Extract duplicated diceRoller Alpine registration from navbar.html and session/cockpit.html into static/js/dice-roller.js before adding prefill.

---

### Task 0: Start Execution and Preserve the Baseline

**Files:**
- Modify: docs/superpowers/dm-only-readiness-roadmap.md
- Modify: docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md

**Interfaces:**
- Consumes: roadmap row 4 PLANNING and this plan.
- Produces: row 4 IN_PROGRESS, row 5 BLOCKED, atmosphere items 3–4 IN_PROGRESS.

- [ ] **Step 1: Inspect authority and workspace**

~~~bash
git status --short
git log -8 --oneline
~~~

Expected: only acknowledged user changes; the plan/planning commit is present.

- [ ] **Step 2: Run predecessor integration baseline**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=RollableTableServiceTest,SceneStructuredContentServiceTest,AdventureSectionAdapterTest,EncounterServiceTest,EncounterSectionAdapterTest,MapPinApiControllerTest,PlayerSafeMapProjectionTest,CampaignCompleteRoundTripTest,CampaignManifestV2ContractTest,CommandPaletteServiceTest,ContentDestinationRegistryTest test
~~~

Expected: PASS with zero failures/skips. Diagnose regressions before editing.

- [ ] **Step 3: Change only active-package status**

Set row 4 IN_PROGRESS, retain row 5 BLOCKED, change atmosphere items 3–4 to IN_PROGRESS, and name this plan in the recovery note.

- [ ] **Step 4: Validate and commit**

~~~bash
git diff --check
git diff -- docs/superpowers/dm-only-readiness-roadmap.md docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md
git add docs/superpowers/dm-only-readiness-roadmap.md docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md
git commit -m "docs(roadmap): start traps and hazards"
~~~

---

### Task 1: Typed Persistence Model and V14 Migration

**Files:**
- Create: src/main/resources/db/migration/V14__add_traps_and_hazards.sql
- Create under src/main/java/dev/hendrikhoemberg/dmhelper/threat/data/: AbstractThreat.java, ThreatCheck.java, Trap.java, Hazard.java, TrapDisarmMethod.java, ThreatReference.java, MapThreatPin.java
- Create in the same package: ThreatKind.java, ThreatSeverity.java, ThreatCheckMode.java, ThreatResetMode.java, HazardExposureMode.java, DamageType.java, ThreatReferenceRole.java
- Create repositories: TrapRepository.java, HazardRepository.java, TrapDisarmMethodRepository.java, ThreatReferenceRepository.java, MapThreatPinRepository.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneSection.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/data/ThreatPersistenceTest.java
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/data/ThreatMigrationTest.java

**Interfaces:**
- Produces: two aggregates, ordered mechanics/references, MapThreatPin, and visible-scope repositories.

- [ ] **Step 1: Write RED graph and migration tests**

Persist complete trap/hazard graphs, clear the EntityManager, reload detailed views, and assert ordering, provenance, campaign ownership, damage types, disarm methods, refs, and pin coordinates. Also prove existing prose sections/combatants migrate with null threat fields.

~~~java
Trap loaded = trapRepository.findDetailedById(saved.getId()).orElseThrow();
assertThat(loaded.getDisarmMethods())
        .extracting(TrapDisarmMethod::getMethodKey)
        .containsExactly("jam-gears", "arcana-bypass");
assertThat(loaded.getDamageTypes())
        .containsExactly(DamageType.PIERCING, DamageType.POISON);
assertThat(loaded.getReferences())
        .extracting(ThreatReference::getRole)
        .containsExactly(ThreatReferenceRole.CONDITION, ThreatReferenceRole.SALVAGE_ITEM);
~~~

- [ ] **Step 2: Run RED**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatPersistenceTest,ThreatMigrationTest test
~~~

Expected: compilation failure because the model/migration do not exist.

- [ ] **Step 3: Implement exact enums**

~~~java
public enum ThreatKind { TRAP, HAZARD }
public enum ThreatSeverity { SETBACK, DANGEROUS, DEADLY }
public enum ThreatCheckMode { CHECK, SAVE }
public enum ThreatResetMode { NONE, MANUAL, AUTOMATIC }
public enum HazardExposureMode { ON_ENTER, START_OF_TURN, PER_ROUND, CONTINUOUS }
public enum ThreatReferenceRole { CONDITION, SALVAGE_ITEM }
public enum DamageType {
    ACID, BLUDGEONING, COLD, FIRE, FORCE, LIGHTNING, NECROTIC,
    PIERCING, POISON, PSYCHIC, RADIANT, SLASHING, THUNDER
}
~~~

- [ ] **Step 4: Implement aggregate fields**

AbstractThreat owns id/sourceKey/source/campaign/provenance/name/description/severity/minLevel/maxLevel/createdAt. ThreatCheck is an embeddable mode/ability/skill/DC value; use AttributeOverrides for trap detection, trap save, and hazard check columns. Trap adds trigger, detection passive/check, ordered disarm methods, attack-or-save, damage expression/types, conditions/items, extra effect, reset, optional statblock, and countermeasures. Hazard adds exposure mode/text, area, save/check, damage/types, conditions/items, escalation, and ending conditions.

ThreatReference has nullable trap_id/hazard_id FKs with a check requiring exactly one owner. MapThreatPin has map_id, pin_key, threat_kind/id, x_px/y_px, label, sort_order.

- [ ] **Step 5: Write additive V14**

Create trap, hazard, trap_disarm_method, trap_damage_type, hazard_damage_type, threat_reference, and map_threat_pin. Add nullable threat_kind/threat_id to scene_section and combatant. Add campaign/name/dependency/map indexes. Do not transform existing prose.

- [ ] **Step 6: Add repository methods**

~~~java
List<Trap> findVisibleByCampaignId(UUID campaignIdOrNull);
Optional<Trap> findDetailedById(UUID id);
List<Trap> findByNameContainingIgnoreCaseOrderByNameAsc(String query);
boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String key);
boolean existsByCampaignIdAndSourceKey(UUID campaignId, String key);
~~~

Mirror for Hazard; add target lookup for ThreatReference and map/threat lookups for MapThreatPin.
TrapRepository also provides countByStatBlockId(UUID) so direct creature-like trap dependencies are visible before statblock deletion.

- [ ] **Step 7: Run GREEN and commit**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatPersistenceTest,ThreatMigrationTest test
git add src/main/resources/db/migration/V14__add_traps_and_hazards.sql \
  src/main/java/dev/hendrikhoemberg/dmhelper/threat/data \
  src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneSection.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat/data
git commit -m "feat(threats): add typed trap and hazard model"
~~~

Expected: tests PASS; Flyway reports 14 migrations.

---

### Task 2: Validation, Visibility, CRUD, and Dependency Safety

**Files:**
- Create under src/main/java/dev/hendrikhoemberg/dmhelper/threat/service/: ThreatValidationRules.java, ThreatValidationProblem.java, ThreatValidationException.java, ThreatCheckWrite.java, ThreatReferenceWrite.java, TrapDisarmMethodWrite.java, TrapWrite.java, HazardWrite.java, ResolvedThreatTarget.java, ThreatReferenceResolver.java, ThreatValidator.java, TrapService.java, HazardService.java, ThreatDependency.java, ThreatDependencyService.java, ThreatDeletionImpact.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/library/service/LibraryReferenceCleaner.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/library/service/ConditionService.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/library/service/EquipmentItemService.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/library/service/MagicItemService.java
- Test under src/test/java/dev/hendrikhoemberg/dmhelper/threat/service/: ThreatValidatorTest.java, TrapServiceTest.java, HazardServiceTest.java, ThreatDependencyServiceTest.java, ThreatLibraryDependencyTest.java

**Interfaces:**
- Produces: validateTrap/validateHazard, requireVisible(kind,id,campaign), CRUD/clone/promote/delete, and finite deletion impact.

- [ ] **Step 1: Define write records**

~~~java
public record ThreatCheckWrite(ThreatCheckMode mode, String ability, String skill, Integer dc) {}
public record ThreatReferenceWrite(
        ThreatReferenceRole role, CampaignContentType targetType,
        UUID targetId, String displayText) {}
public record TrapDisarmMethodWrite(
        String key, String label, String ability, String skill, String tool,
        Integer dc, String failureConsequence, int sortOrder) {}
public record TrapWrite(
        String sourceKey, String name, String description, ThreatSeverity severity,
        Integer minLevel, Integer maxLevel, String triggerDescription, String triggerAreaHint,
        Integer detectionPassiveThreshold, ThreatCheckWrite detectionCheck,
        List<TrapDisarmMethodWrite> disarmMethods, Integer attackBonus, ThreatCheckWrite save,
        String damageExpression, List<DamageType> damageTypes, String additionalEffect,
        ThreatResetMode resetMode, String resetTiming, UUID statBlockId,
        String countermeasureNotes, List<ThreatReferenceWrite> references) {}
public record HazardWrite(
        String sourceKey, String name, String description, ThreatSeverity severity,
        Integer minLevel, Integer maxLevel, HazardExposureMode exposureMode,
        String exposureText, String areaHint, ThreatCheckWrite check,
        String damageExpression, List<DamageType> damageTypes,
        String escalationText, String endingConditions,
        List<ThreatReferenceWrite> references) {}
~~~

- [ ] **Step 2: Write RED validation matrix**

Cover invalid/duplicate keys, blank required prose, missing severity/exposure, partial/reversed/out-of-bound levels, DC/passive bounds, SAVE with skill, CHECK without ability/skill, attack+save conflict, bad damage expression, damage without type, duplicate disarm keys, method without ability/skill/tool, AUTOMATIC without timing, invalid role/type, missing target, cross-campaign target, and global-to-campaign target.

~~~java
assertThatThrownBy(() -> validator.validateTrap(write, campaignId))
    .isInstanceOfSatisfying(ThreatValidationException.class, ex ->
        assertThat(ex.problems()).contains(
            new ThreatValidationProblem(
                "TRAP_EFFECT_MODE_CONFLICT", "/attackBonus",
                "Attack bonus and save are mutually exclusive")));
~~~

- [ ] **Step 3: Run RED**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatValidatorTest,TrapServiceTest,HazardServiceTest,ThreatDependencyServiceTest test
~~~

- [ ] **Step 4: Implement validation/resolution**

ThreatValidationRules exposes KEY_PATTERN, MIN_DC/MAX_DC, MIN_LEVEL/MAX_LEVEL. Resolver supports CONDITION, STATBLOCK, EQUIPMENT_ITEM, MAGIC_ITEM and checks source/campaign visibility.

~~~java
return switch (ref.role()) {
    case CONDITION -> ref.targetType() == CampaignContentType.CONDITION;
    case SALVAGE_ITEM -> ref.targetType() == CampaignContentType.EQUIPMENT_ITEM
            || ref.targetType() == CampaignContentType.MAGIC_ITEM;
};
~~~

- [ ] **Step 5: Implement service contracts**

~~~java
Trap create(UUID campaignIdOrNull, TrapWrite write, ContentProvenance provenance);
Trap updateCustom(UUID id, TrapWrite write, ContentProvenance provenance);
Trap cloneAsCustom(UUID sourceId, UUID campaignIdOrNull, String newName);
Trap promoteToGlobal(UUID id);
ThreatDeletionImpact deletionImpact(UUID id);
void deleteCustom(UUID id, boolean confirmed);
~~~

HazardService mirrors them. Use CustomContentSupport. Promotion rejects references invisible globally. Confirmed delete nulls scene/combatant refs while retaining prose/name/notes/logs, deletes pins/package keys, then definition.

Use these finite dependency records:

~~~java
public record ResolvedThreatTarget(
        CampaignContentType type, UUID id, String displayName,
        ContentSource source, UUID campaignId) {}
public record ThreatDependency(
        String kind, UUID dependentId, String label, String destination) {}
public record ThreatDeletionImpact(
        ThreatKind threatKind, UUID threatId, List<ThreatDependency> dependencies) {
    public boolean hasDependents() { return !dependencies.isEmpty(); }
}
~~~

- [ ] **Step 6: Protect referenced library content**

LibraryReferenceCleaner counts ThreatReference targets for conditions/items and direct Trap.statBlock links for statblocks. Condition/statblock/equipment/magic-item deletion rejects referenced content with a dependency count instead of FK failure or silent loss.

- [ ] **Step 7: Run GREEN/regressions and commit**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatValidatorTest,TrapServiceTest,HazardServiceTest,ThreatDependencyServiceTest,ThreatLibraryDependencyTest,RollableTableServiceTest,CustomContentOwnershipPersistenceTest test
git add src/main/java/dev/hendrikhoemberg/dmhelper/threat/service \
  src/main/java/dev/hendrikhoemberg/dmhelper/library/service \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat/service \
  src/test/java/dev/hendrikhoemberg/dmhelper/library/service
git commit -m "feat(threats): enforce authoring and dependency rules"
~~~

---

### Task 3: Finite APIs, Shared Editor, Library Pages, and Search

**Files:**
- Create under src/main/java/dev/hendrikhoemberg/dmhelper/threat/web/: TrapResponse.java, HazardResponse.java, ThreatCardView.java, ThreatWebMapper.java, ThreatApiController.java, ThreatController.java
- Create under src/main/resources/templates/threat/: list.html, form.html, detail.html, _mechanics-card.html, _provenance.html
- Create: src/main/resources/static/js/threat-editor.js
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistry.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java
- Modify: src/main/resources/templates/fragments/_appnav.html
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/web/ThreatApiControllerTest.java
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/web/ThreatControllerTest.java
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatTemplateContractTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteServiceTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRegistryTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/common/service/ContentDestinationRouteContractTest.java

**Interfaces:**
- Produces: /library/traps, /library/hazards, /api/v1/traps, /api/v1/hazards, reference options, palette types trap/hazard.

- [ ] **Step 1: Write RED API/route tests**

Assert create/update/clone/promote/dependency/delete, finite ordered JSON, campaign isolation, SRD read-only, RFC 9457 paths, 404/409 boundaries, and DM PIN checks.

~~~java
mockMvc.perform(post("/api/v1/traps")
        .param("campaignId", campaign.getId().toString())
        .contentType(APPLICATION_JSON)
        .content(json.writeValueAsBytes(write)))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.kind").value("TRAP"))
    .andExpect(jsonPath("$.disarmMethods[0].key").value("jam-gears"))
    .andExpect(jsonPath("$.references[0].targetType").value("CONDITION"))
    .andExpect(jsonPath("$.references[0].trap").doesNotExist());
~~~

- [ ] **Step 2: Write RED template/palette tests**

Assert lists have create/search/scope; detail has clone/promote/dependency-aware delete; shared form has typed dynamic mechanics/refs; provenance displays; hostile Markdown is inert; search excludes other campaigns and ranks same-campaign first without duplicates.

- [ ] **Step 3: Run RED**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatApiControllerTest,ThreatControllerTest,ThreatTemplateContractTest,CommandPaletteServiceTest,ContentDestinationRegistryTest,ContentDestinationRouteContractTest test
~~~

- [ ] **Step 4: Implement DTO/controller boundary**

TrapResponse/HazardResponse have immutable nested check/damage/disarm/reference records. ThreatWebMapper is the sole entity-to-web mapper. Validation returns ProblemDetail.problems; dependency conflict is 409.

ThreatCardView is a finite union used by scenes, encounters, and pins:

~~~java
public record ThreatCardView(
        ThreatKind kind, UUID id, String name, String descriptionHtml,
        TrapResponse trap, HazardResponse hazard) {}
~~~

- [ ] **Step 5: Implement shared UI**

threat-editor.js receives kind/campaign/existing DTO; supports stable Alpine keys, add/remove/reorder disarm methods, condition/item/statblock search, per-path errors, and retryable dmRequest failures. It emits separate TrapWrite/HazardWrite shapes.

- [ ] **Step 6: Register content**

Add TRAP/HAZARD CampaignContentType and LibraryType values/tabs. Palette searches visible repositories and returns trap/hazard routes. Add DM navigation only.

- [ ] **Step 7: Run GREEN and commit**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatApiControllerTest,ThreatControllerTest,ThreatTemplateContractTest,CommandPaletteServiceTest,ContentDestinationRegistryTest,ContentDestinationRouteContractTest test
git add src/main/java/dev/hendrikhoemberg/dmhelper/threat/web \
  src/main/resources/templates/threat src/main/resources/static/js/threat-editor.js \
  src/main/java/dev/hendrikhoemberg/dmhelper/common/service \
  src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java \
  src/main/resources/templates/fragments/_appnav.html \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/service
git commit -m "feat(threats): add compendium authoring and search"
~~~

---

### Task 4: Scene References, Cockpit Cards, and Dice Prefill

**Files:**
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneSection.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneStructuredContentService.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java
- Modify: src/main/resources/templates/adventure/_action-rail.html
- Modify: src/main/resources/templates/session/_story-rail.html
- Create: src/main/resources/static/js/dice-roller.js
- Modify: src/main/resources/templates/fragments/navbar.html
- Modify: src/main/resources/templates/session/cockpit.html
- Modify: src/main/resources/templates/fragments/_dice-roller.html
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatDicePrefillContractTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneStructuredContentServiceTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneStructuredTemplateContractTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java

**Interfaces:**
- Produces: SceneSectionCommand with ThreatKind/threatId; dice-roller-prefill event; inline mechanics cards.

- [ ] **Step 1: Write RED scene tests**

TRAP accepts visible trap but not hazard/cross-campaign; HAZARD mirrors it; other section kinds reject refs; removing ref preserves body/label/source locator. Templates expose selector and cockpit card.

- [ ] **Step 2: Write RED prefill contract**

Prove one shared diceRoller registration, both shells load dice-roller.js, and cards dispatch dice-roller-prefill without POSTing /api/v1/roll.

~~~javascript
window.dispatchEvent(new CustomEvent('dice-roller-prefill', {
    detail: { expression: expression, label: label }
}));
~~~

- [ ] **Step 3: Run RED**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=SceneStructuredContentServiceTest,SceneStructuredTemplateContractTest,SessionCockpitTemplateContractTest,ThreatDicePrefillContractTest test
~~~

- [ ] **Step 4: Enforce scene references**

Extend add/update commands and forms. Validate section-kind/type match before persistence. Null refs preserve old behavior and byte-equivalent prose.

- [ ] **Step 5: Extract/extend diceRoller**

Move duplicated Alpine code to dice-roller.js. On prefill set expression, clear adv/dis/result, open, retain encounter ID, and focus x-ref expressionInput.

~~~javascript
window.addEventListener('dice-roller-prefill', event => {
    this.expression = (event.detail && event.detail.expression) || '';
    this.advantage = false;
    this.disadvantage = false;
    this.result = null;
    this.open = true;
    this.$nextTick(() => this.$refs.expressionInput && this.$refs.expressionInput.focus());
});
~~~

- [ ] **Step 6: Render advisory buttons**

Checks/saves prefill 1d20 with DC separate; attack prefill 1d20 plus signed bonus; damage prefill stored expression. No card invokes damage, condition, treasury, or story mutations.

- [ ] **Step 7: Run GREEN/regressions and commit**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=SceneStructuredContentServiceTest,SceneStructuredTemplateContractTest,SessionCockpitTemplateContractTest,ThreatDicePrefillContractTest,DiceApiControllerTest,RollHistoryServiceTest test
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure \
  src/main/resources/templates/adventure src/main/resources/templates/session \
  src/main/resources/templates/fragments src/main/resources/static/js/dice-roller.js \
  src/test/java/dev/hendrikhoemberg/dmhelper/adventure \
  src/test/java/dev/hendrikhoemberg/dmhelper/session \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatDicePrefillContractTest.java
git commit -m "feat(threats): render advisory scene mechanics"
~~~

---

### Task 5: Encounter Preparation and Active-Turn Cards

**Files:**
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Combatant.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiController.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterController.java
- Create: src/main/resources/templates/encounter/_threat-add.html
- Modify: src/main/resources/templates/encounter/detail.html
- Modify: src/main/resources/templates/encounter/_tracker.html
- Modify: src/main/resources/templates/session/_encounter-rail.html
- Modify: src/main/resources/static/js/session-cockpit.js
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatEncounterIntegrationTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterServiceTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterApiControllerTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterTemplateContractTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java

**Interfaces:**
- Produces: addThreatCombatant(encounterId,request), CombatantDto threat fields/card, active-turn rendering.

- [ ] **Step 1: Write RED integration tests**

Add visible trap/hazard non-creature combatants; reject cross-campaign/mismatch; keep HP zero/no statblock/party; preserve ref over reorder/activation/refresh; render only active card; prove no automatic mutation.

~~~java
public record ThreatCombatantRequest(
        ThreatKind threatKind, UUID threatId, String name,
        Integer initiative, UUID waveId) {}
~~~

- [ ] **Step 2: Prove manual action logging**

Activate a trap turn, use existing damage/condition endpoints on a creature/party combatant, assert DAMAGE/CONDITION_ADDED logs. Activation alone must add none.

- [ ] **Step 3: Run RED**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatEncounterIntegrationTest,EncounterServiceTest,EncounterApiControllerTest,EncounterTemplateContractTest,SessionCockpitTemplateContractTest test
~~~

- [ ] **Step 4: Implement service/DTO**

Resolve against encounter campaign; create kind TRAP/HAZARD, HP 0, default definition name. CombatantDto adds nullable threatKind/threatId/finite ThreatCardView. Updates cannot change to mismatched kind.

- [ ] **Step 5: Add prep/tracker UI**

_threat-add searches visible definitions. Tracker/cockpit render shared card for active threat. Existing damage/condition/note controls remain sole mutation path.

- [ ] **Step 6: Run GREEN and commit**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatEncounterIntegrationTest,EncounterServiceTest,EncounterApiControllerTest,EncounterTemplateContractTest,SessionCockpitTemplateContractTest test
git add src/main/java/dev/hendrikhoemberg/dmhelper/encounter \
  src/main/resources/templates/encounter src/main/resources/templates/session \
  src/main/resources/static/js/session-cockpit.js \
  src/test/java/dev/hendrikhoemberg/dmhelper/encounter \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatEncounterIntegrationTest.java
git commit -m "feat(threats): integrate encounter tracker cards"
~~~

---

### Task 6: DM-Only Map Pins and Player Safety

**Files:**
- Create: src/main/java/dev/hendrikhoemberg/dmhelper/threat/service/MapThreatPinService.java
- Create: src/main/java/dev/hendrikhoemberg/dmhelper/threat/service/MapThreatPinWrite.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinApiController.java
- Modify: src/main/resources/templates/maps/editor.html
- Modify: src/main/resources/static/js/map/map-editor.js
- Modify: src/main/resources/static/js/map/battle-map.js
- Modify: src/main/resources/templates/session/cockpit.html
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/service/MapThreatPinServiceTest.java
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatPlayerSafetyTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinApiControllerTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinAccessControlTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java

**Interfaces:**
- Produces: typed pin CRUD, combined MapPinDto, threat-card-open event.

- [ ] **Step 1: Write RED map/API tests**

Cover stable key, bounds, map/campaign ownership, visible threat, defaults, order, update/delete ownership, scene+threat response, DM PIN.

~~~java
public record MapThreatPinWrite(
        String key, ThreatKind threatKind, UUID threatId,
        int x, int y, String label, int sortOrder) {}
public record MapPinDto(
        String pinKind, UUID id, String key, int x, int y, String title,
        UUID sceneId, String sceneKey, ThreatKind threatKind, UUID threatId) {}
~~~

- [ ] **Step 2: Write RED leak test**

Place unique markers in definition/mechanics/provenance/pin. Request /player, table state, player bootstrap/WebSocket/map/document. Assert markers/IDs/types/pin keys absent while DM pins include them.

- [ ] **Step 3: Run RED**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=MapThreatPinServiceTest,MapPinApiControllerTest,MapPinAccessControlTest,ThreatPlayerSafetyTest test
~~~

- [ ] **Step 4: Implement pin service/API**

Validate 0 <= x < width×cell and 0 <= y < height×cell. Combine finite scene/threat pins. Require route map ownership on write/delete.

- [ ] **Step 5: Add DM UI**

Map sidebar adds type/search/label/x/y/create/delete with dmRequest errors. Battle map draws distinct DM marker; click dispatches threat-card-open. Do not add pins to MapDocumentDto or PlayerSafeProjectionService.

- [ ] **Step 6: Run GREEN/regressions and commit**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=MapThreatPinServiceTest,MapPinApiControllerTest,MapPinAccessControlTest,ThreatPlayerSafetyTest,PlayerSafeMapProjectionTest,RollableTablePlayerSafetyTest test
git add src/main/java/dev/hendrikhoemberg/dmhelper/threat/service/MapThreatPinService.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinApiController.java \
  src/main/resources/templates/maps src/main/resources/static/js/map \
  src/main/resources/templates/session/cockpit.html \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat \
  src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web
git commit -m "feat(threats): add DM-only map markers"
~~~

---

### Task 7: Package-v2 Schema, Adapters, Validation, and Round-Trip

**Files:**
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignManifestAssembler.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/key/CampaignContentType.java
- Create: src/main/java/dev/hendrikhoemberg/dmhelper/threat/packagev2/ThreatExportClosureService.java
- Create: src/main/java/dev/hendrikhoemberg/dmhelper/threat/packagev2/ThreatSectionAdapter.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2/AdventureSectionAdapter.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2/EncounterSectionAdapter.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/packagev2/MapSectionAdapter.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidator.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignEntityCounts.java
- Modify: src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/preview/CampaignImportPreviewStore.java
- Modify: src/main/resources/schemas/campaign-format-v2.schema.json
- Modify: src/main/resources/agent/validation-error-catalog.json
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/packagev2/ThreatSectionAdapterTest.java
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/packagev2/ThreatExportClosureServiceTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/model/CampaignManifestV2ContractTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/validation/CampaignDtoSchemaCompatibilityTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/validation/CampaignManifestV2SemanticValidatorTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/AdventureSectionAdapterTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/EncounterSectionAdapterTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/adapter/MapSectionAdapterTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/service/CampaignCompleteRoundTripTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2/section/CampaignSectionRegistryTest.java
- Modify all direct new CampaignManifestV2 calls under src/main and src/test with two new lists in declared order.

**Interfaces:**
- Produces: top-level traps/hazards; shared mechanic DTOs; SceneSectionDto/CombatantDto threatRef; MapDto threatPins; adapter order 250.

- [ ] **Step 1: Define DTOs/defaults**

Append List<TrapDto> traps and List<HazardDto> hazards after rollableTables; null defaults to empty. Add:

~~~java
public record ThreatCheckDto(String mode, String ability, String skill, Integer dc) {}
public record ThreatDamageDto(String expression, List<String> types) {}
public record TrapDisarmMethodDto(
        String key, String label, String ability, String skill, String tool,
        Integer dc, String failureConsequence, int sortOrder) {}
public record MapThreatPinDto(
        String key, int x, int y, String label,
        ContentReference threatRef, int sortOrder) {}
~~~

TrapDto/HazardDto include every persistent field, conditionRefs, salvageItemRefs, createdAt, provenance. Add nullable threatRef to SceneSectionDto/CombatantDto; add null-safe threatPins to MapDto.

- [ ] **Step 2: Write RED schema/DTO tests**

Assert closed objects, exact enums, key regex, required identity/name/description/severity, optional numeric mechanics, attack/save exclusivity, reset timing, correct ref types, optional roots, and unchanged old v2 fixtures.

- [ ] **Step 3: Write RED semantic paths**

Cover bad expression; DC/passive/level bounds; wrong/unresolved condition/statblock/item; scene/combatant kind mismatch; pin bounds; duplicate disarm keys; exact paths such as /traps/0/disarmMethods/1/dc.

- [ ] **Step 4: Run RED**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=CampaignManifestV2ContractTest,CampaignDtoSchemaCompatibilityTest,CampaignManifestV2SemanticValidatorTest,ThreatSectionAdapterTest,ThreatExportClosureServiceTest,AdventureSectionAdapterTest,EncounterSectionAdapterTest,MapSectionAdapterTest,CampaignSectionRegistryTest test
~~~

- [ ] **Step 5: Extend schema**

Add optional traps/hazards and closed shared definitions. Use oneOf for trap attack/save/no-effect and if/then for AUTOMATIC reset. Extend sceneSection/combatant/map with refs/pins.

- [ ] **Step 6: Implement closure/adapter**

Closure includes campaign definitions, referenced global definitions from scene/combatant/pin, and required global/custom conditions/statblocks/items. Adapter order is 250, registers definitions before map/encounter/adventure, and defers library refs.

- [ ] **Step 7: Reuse runtime validation**

Map DTOs to write records, call ThreatValidator structural checks with package paths, then resolve package/catalog/integration refs. Add stable error catalog entries.

- [ ] **Step 8: Repair constructor compilation**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards -DskipTests compile
~~~

Append List.of(), List.of() in traps/hazards order at direct manifest constructors without changing earlier args; repeat until compile succeeds.

- [ ] **Step 9: Run GREEN/behavioral round-trip and commit**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=CampaignManifestV2ContractTest,CampaignDtoSchemaCompatibilityTest,CampaignManifestV2SemanticValidatorTest,ThreatSectionAdapterTest,ThreatExportClosureServiceTest,AdventureSectionAdapterTest,EncounterSectionAdapterTest,MapSectionAdapterTest,CampaignSectionRegistryTest,CampaignCompleteRoundTripTest,CampaignPackageV2IntegrationTest,CampaignSemanticSnapshotServiceTest test
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/threat/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/adventure/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/encounter/packagev2 \
  src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/packagev2 \
  src/main/resources/schemas/campaign-format-v2.schema.json \
  src/main/resources/agent/validation-error-catalog.json \
  src/test/java/dev/hendrikhoemberg/dmhelper/campaign/packagev2 \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat/packagev2
git commit -m "feat(threats): preserve package fidelity"
~~~

Expected: imported definitions/refs/pins load and render after both imports, not merely compare JSON.

---

### Task 8: Fixtures, Documentation, Browser Acceptance, and Security

**Files:**
- Modify: src/test/resources/campaigns/v2/feature-complete.dmcampaign/manifest.json
- Modify: src/test/resources/campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json
- Modify: src/main/resources/agent/capability-manifest.json
- Create: docs/dm-manual/08-traps-and-hazards.md
- Modify: docs/dm-manual/README.md
- Modify: docs/dm-manual/03-session-cockpit.md
- Modify: docs/dm-manual/04-maps-encounters-party.md
- Modify: docs/campaign-format-v2.md
- Modify: docs/campaign-capabilities.md
- Modify: docs/agent/conversion-playbook.md
- Modify: docs/agent/mapping-rules.md
- Modify: docs/authoring/validation-errors.md
- Modify: docs/product/release-notes.md
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatDocumentationContractTest.java
- Test: src/test/java/dev/hendrikhoemberg/dmhelper/threat/ThreatHostileContentTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocumentationExampleValidationTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/agent/DmManualContractTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/agent/DocsIndexContractTest.java
- Modify: src/test/java/dev/hendrikhoemberg/dmhelper/agent/CapabilityManifestContractTest.java

**Interfaces:**
- Produces: executable fixtures/docs and real HTTP/DOM evidence.

- [ ] **Step 1: Add source-shaped fixtures**

Feature-complete gets full trap/hazard, condition/item/statblock refs, scene ref, encounter combatant, DM pin. Published-adventure gets provenance-rich trap referenced from prose section/encounter; unresolved source values remain absent/annotated rather than invented.

- [ ] **Step 2: Run fixture round-trip**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=CampaignCompleteRoundTripTest,CampaignManifestV2SemanticValidatorTest test
~~~

- [ ] **Step 3: Write RED docs contracts, then prose**

Require ownership/provenance/management, scene/tracker/pin, dice prefill/manual resolution, schema/error paths, package closure, and conversion non-invention. Document exact routes/workflows and player-safety/non-goals.

- [ ] **Step 4: Add hostile/access evidence**

Store script/event-handler Markdown/plain text; render detail/cockpit/tracker and prove inert. Include all new APIs in PIN tests.

- [ ] **Step 5: Implement real-browser flow**

CoreSessionLoopSmokeTest must:

1. create trap/hazard through editor request paths;
2. verify detail/provenance/mechanics;
3. attach one trap to two scenes without duplication;
4. open story card;
5. click check/attack/damage and prove prefill before roll;
6. add/activate encounter threat and see tracker card;
7. use existing damage/condition controls and verify log;
8. create/open DM pin and verify player marker absence;
9. export/import and reopen refs/cards.

Unexpected console/page/request/failure-collector errors fail the test.

- [ ] **Step 6: Run docs/security/browser gates**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatDocumentationContractTest,ThreatHostileContentTest,ThreatPlayerSafetyTest,DmManualContractTest,DocsIndexContractTest,DocumentationExampleValidationTest,CapabilityManifestContractTest test
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=CoreSessionLoopSmokeTest test
~~~

Expected: PASS; fresh browser XML has zero failures/skips.

- [ ] **Step 7: Commit**

~~~bash
git add src/test/resources/campaigns/v2 \
  src/main/resources/agent/capability-manifest.json docs \
  src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/threat
git commit -m "test(threats): prove DM workflows and package fidelity"
~~~

---

### Task 9: Final Verification and Roadmap Handoff

**Files:**
- Modify only after gates pass: docs/superpowers/dm-only-readiness-roadmap.md
- Modify only after gates pass: docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md

**Interfaces:**
- Produces: row 4 COMPLETE, row 5 READY, items 3–4 IMPLEMENTED, exact evidence.

- [ ] **Step 1: Run focused gate**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards \
  -Dtest=ThreatPersistenceTest,ThreatMigrationTest,ThreatValidatorTest,TrapServiceTest,HazardServiceTest,ThreatDependencyServiceTest,ThreatLibraryDependencyTest,ThreatApiControllerTest,ThreatControllerTest,ThreatTemplateContractTest,ThreatDicePrefillContractTest,ThreatEncounterIntegrationTest,MapThreatPinServiceTest,ThreatPlayerSafetyTest,ThreatHostileContentTest,ThreatSectionAdapterTest,ThreatExportClosureServiceTest,CampaignManifestV2ContractTest,CampaignDtoSchemaCompatibilityTest,CampaignManifestV2SemanticValidatorTest,AdventureSectionAdapterTest,EncounterSectionAdapterTest,MapSectionAdapterTest,CampaignCompleteRoundTripTest,CommandPaletteServiceTest,ContentDestinationRegistryTest,ContentDestinationRouteContractTest,SessionCockpitTemplateContractTest,EncounterTemplateContractTest test
~~~

- [ ] **Step 2: Run independent browser gate**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards -Dtest=CoreSessionLoopSmokeTest test
~~~

Record fresh XML test count/actions/failures/skips.

- [ ] **Step 3: Run complete suite**

~~~bash
./mvnw -q -Duser.home=/tmp/dmhelper-p3-traps-hazards test
~~~

Expected: exit 0. Count only fresh XML suites/tests/failures/errors/skips.

- [ ] **Step 4: Record bounded browser acceptance**

Record create/clone/promote; scene/prose preservation; cockpit/prefill; tracker/manual log; DM pin; export/import/reopen; player absence. Do not label automated evidence manual.

- [ ] **Step 5: Inspect hygiene**

~~~bash
git status --short
git diff --check
git diff --stat
~~~

Confirm V14 additive, schema/DTO/adapter alignment, player boundaries, and no later roadmap scope.

- [ ] **Step 6: Close only row 4**

Set row 4 COMPLETE, row 5 READY, atmosphere items 3–4 IMPLEMENTED, Current NEXT 5 — Travel core. Link this plan and exact evidence. Do not close item 9 or overall readiness.

- [ ] **Step 7: Commit**

~~~bash
git add docs/superpowers/dm-only-readiness-roadmap.md \
  docs/superpowers/specs/2026-07-17-table-fidelity-and-atmosphere-design.md
git commit -m "docs(roadmap): close traps and hazards"
~~~

## Self-Review Checklist

- Every atmosphere 5.2 field maps to Tasks 1–2 and Task 7 DTO/schema.
- Full compendium ownership/provenance/management maps to Tasks 2–3.
- Prose-compatible scene refs/two-action cockpit cards/dice prefill map to Task 4.
- Initiative cards/manual action-log evidence map to Task 5.
- DM-only map pins/server leak coverage map to Task 6.
- Older-v2 compatibility, exact paths, closure, fixtures, behavioral round-trip map to Tasks 7–8.
- Hostile rendering, PIN, docs, non-invention, real browser map to Task 8.
- Fog/music/travel/player interaction/autonomous effects/copyrighted content remain out of scope.
