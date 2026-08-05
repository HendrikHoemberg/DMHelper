# Runtime Transaction and Test Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make scene-to-encounter creation return a truthful successful response, remain atomic and idempotent, and work from both the scene page and cockpit Story module under production persistence settings.

**Architecture:** Seed eligibility and creation load the scene by campaign and ID inside the `SceneEncounterSeedService` transaction. Controllers consume a fully hydrated `SceneDetailView` rather than passing detached entities into later transactions. The cockpit calls a JSON session endpoint, then refreshes both runtime rails through the existing `refreshRails()` path.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC, Spring Data JPA/Hibernate, Thymeleaf, Alpine.js, htmx, JUnit 5, MockMvc, Mockito, AssertJ, H2.

## Global Constraints

- Production and test execution both use `spring.jpa.open-in-view=false`.
- No new Maven, npm or runtime dependencies.
- Every scene lookup used for encounter creation is scoped by both `campaignId` and `sceneId`.
- A failed seed transaction leaves no encounter, combatant or scene link behind.
- A repeated successful request returns the existing encounter and creates no duplicates.
- Participants without statblocks remain visible in `skippedParticipants`; do not invent combat statistics.
- The cockpit action must not navigate to an Edit/Admin surface.
- Do not commit the private Phandelver package, source PDF or `artifacts/` screenshots.
- Preserve the existing Spring/Thymeleaf/htmx/Alpine architecture.
- Follow TDD: observe each specified failure before applying its production fix.

---

## File Structure

### Production files

- `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedService.java`
  - Owns campaign-scoped eligibility, atomic creation and idempotent result reporting.
- `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java`
  - Renders the scene page and action-rail fragment from a hydrated scene view.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java`
  - Exposes typed JSON encounter seeding to the live cockpit.
- `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`
  - Supplies seed eligibility to initial and refreshed Story fragments.
- `src/main/resources/templates/session/_story-rail.html`
  - Renders the cockpit action and skipped-participant result.
- `src/main/resources/static/js/session-cockpit.js`
  - Calls the session API, reports failure, and refreshes both rails after success.
- `src/test/resources/application.properties`
  - Pins the production persistence boundary in every Spring integration test.

### Test files

- Create `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/PersistenceProfileParityTest.java`
  - Prevents the test property from drifting back to Boot's open-in-view default.
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedServiceTest.java`
  - Runs without a test-managed transaction and verifies eligibility, scoping and idempotency.
- Create `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedAtomicityTest.java`
  - Forces a participant-add failure and verifies database rollback.
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneSeedEncounterControllerTest.java`
  - Reproduces the real HTTP fragment flow with open-in-view disabled.
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneControllerTest.java`
  - Pins the campaign-scoped service calls in the MVC slice.
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiControllerTest.java`
  - Verifies the typed cockpit endpoint and delegation.
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionControllerTest.java`
  - Verifies Story-module eligibility rendering.
- Modify `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
  - Pins the Alpine action, API path and rail refresh contract.

### Documentation

- Modify `docs/dm-manual/03-session-cockpit.md`
  - Documents encounter creation, idempotency and skipped participants from the Story module.

---

### Task 1: Repair Transaction Boundaries and Production-Parity Tests

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/common/config/PersistenceProfileParityTest.java`
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedAtomicityTest.java`
- Modify: `src/test/resources/application.properties`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneSeedEncounterControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneControllerTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java`

**Interfaces:**
- Consumes: `SceneRepository.findByIdAndCampaignId(UUID campaignId, UUID sceneId)`, `AdventureService.findSceneDetailView(UUID sceneId)`, `EncounterService.create(...)`, `EncounterService.addFromLibrary(...)`, `AdventureService.linkEncounter(...)`.
- Produces: `boolean SceneEncounterSeedService.canSeed(UUID campaignId, UUID sceneId)` and the existing `SeedResult SceneEncounterSeedService.seedFromScene(UUID campaignId, UUID sceneId)` with campaign ownership enforced.

- [ ] **Step 1: Write the persistence-profile contract test**

Create `PersistenceProfileParityTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceProfileParityTest {

    @Test
    void integrationTestsDisableOpenInViewLikeProduction() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/test/resources/application.properties"))) {
            properties.load(reader);
        }

        assertThat(properties.getProperty("spring.jpa.open-in-view"))
                .as("controller integration tests must expose detached-entity failures")
                .isEqualTo("false");
    }
}
```

- [ ] **Step 2: Run the contract test and observe the missing property**

Run:

```bash
./mvnw -Dtest=PersistenceProfileParityTest test
```

Expected: FAIL because the actual value is `null`, not `false`.

- [ ] **Step 3: Pin open-in-view off in test resources**

Add directly after the datasource credentials in `src/test/resources/application.properties`:

```properties
# Match production transaction boundaries so MVC tests cannot hide detached lazy access.
spring.jpa.open-in-view=false
```

- [ ] **Step 4: Confirm parity and reproduce the real controller failure**

Run:

```bash
./mvnw -Dtest=PersistenceProfileParityTest test
./mvnw -Dtest=SceneSeedEncounterControllerTest test
```

Expected: `PersistenceProfileParityTest` PASS. `SceneSeedEncounterControllerTest` FAIL on the
scene page or seed response with a detached `Scene.participants` access or
`LazyInitializationException`. This is the required red reproduction of the production bug.

- [ ] **Step 5: Make the service integration test cross real transaction boundaries**

In `SceneEncounterSeedServiceTest.java`:

1. replace `BeforeAll` with `BeforeEach`;
2. remove `TestInstance` and `Transactional` imports and annotations;
3. inject `EncounterRepository`;
4. replace the eligibility test and add the campaign-ownership test below.

```java
@SpringBootTest
class SceneEncounterSeedServiceTest {

    @Autowired private SceneEncounterSeedService seeder;
    @Autowired private EncounterService encounters;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private AdventureService adventures;
    @Autowired private PopulatedCampaignFixture fixture;

    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeEach
    void setUp() {
        seeded = fixture.seed();
    }

    @Test
    void aSceneWithNoStatblockLinkedParticipantsCannotBeSeeded() {
        assertThat(seeder.canSeed(seeded.campaignId(), seeded.secondSceneId()))
                .as("the action must be absent for a scene the app knows nothing about")
                .isFalse();
        assertThat(seeder.canSeed(seeded.campaignId(), seeded.richSceneId())).isTrue();
    }

    @Test
    void rejectsASceneFromAnotherCampaignWithoutCreatingAnEncounter() {
        PopulatedCampaignFixture.Seeded otherCampaign = fixture.seed();
        long before = encounterRepository.count();

        assertThatThrownBy(() ->
                seeder.seedFromScene(seeded.campaignId(), otherCampaign.richSceneId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Scene not found in campaign");

        assertThat(encounterRepository.count()).isEqualTo(before);
    }
}
```

Keep the four existing seed-result, link and idempotency tests unchanged inside the class. Add
these imports:

```java
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 6: Add a forced rollback integration test**

Create `SceneEncounterSeedAtomicityTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class SceneEncounterSeedAtomicityTest {

    @Autowired private SceneEncounterSeedService seeder;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CombatantRepository combatantRepository;
    @Autowired private SceneRepository sceneRepository;
    @MockitoSpyBean private EncounterService encounterService;

    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeEach
    void setUp() {
        seeded = fixture.seed();
    }

    @Test
    void participantFailureRollsBackEncounterCombatantsAndSceneLink() {
        long encountersBefore = encounterRepository.count();
        long combatantsBefore = combatantRepository.count();
        doThrow(new IllegalStateException("forced participant failure"))
                .when(encounterService)
                .addFromLibrary(any(UUID.class),
                        any(EncounterService.AddFromLibraryRequest.class));

        assertThatThrownBy(() ->
                seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced participant failure");

        assertThat(encounterRepository.count()).isEqualTo(encountersBefore);
        assertThat(combatantRepository.count()).isEqualTo(combatantsBefore);
        assertThat(sceneRepository.findById(seeded.richSceneId()).orElseThrow().getEncounter())
                .isNull();
    }
}
```

- [ ] **Step 7: Update the HTTP regression test to prove idempotent persistence**

Change `SceneSeedEncounterControllerTest` to use `@BeforeEach`, inject
`EncounterRepository`, and extend the first test after its existing first POST:

```java
@BeforeEach
void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).build();
    seeded = fixture.seed();
}
```

```java
long encountersBefore = encounterRepository.count();

String rail = mvc.perform(post("/campaigns/{c}/adventures/{a}/scenes/{s}/seed-encounter",
                seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

assertThat(rail)
        .contains("Encounter: Der Schreibtisch")
        .contains("Namenloser Bote")
        .contains("Linked Encounter");

mvc.perform(post("/campaigns/{c}/adventures/{a}/scenes/{s}/seed-encounter",
                seeded.campaignId(), seeded.adventureId(), seeded.richSceneId()))
        .andExpect(status().isOk());

assertThat(encounterRepository.count()).isEqualTo(encountersBefore + 1);
```

Remove `@BeforeAll` and `@TestInstance`, and add:

```java
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
```

- [ ] **Step 8: Run the new tests and observe the service-contract failures**

Run:

```bash
./mvnw -Dtest=PersistenceProfileParityTest,SceneEncounterSeedServiceTest,SceneEncounterSeedAtomicityTest,SceneSeedEncounterControllerTest test
```

Expected: compilation FAIL because `canSeed(UUID, UUID)` does not exist. This is the red state for
the final campaign-scoped interface; do not weaken the test to call the detached-entity overload.

- [ ] **Step 9: Implement campaign-scoped transactional loading**

Modify `SceneEncounterSeedService` constructor and fields:

```java
private final AdventureService adventures;
private final EncounterService encounters;
private final SceneRepository scenes;

public SceneEncounterSeedService(AdventureService adventures,
                                 EncounterService encounters,
                                 SceneRepository scenes) {
    this.adventures = adventures;
    this.encounters = encounters;
    this.scenes = scenes;
}
```

Replace `canSeed(Scene scene)` with:

```java
@Transactional(readOnly = true)
public boolean canSeed(UUID campaignId, UUID sceneId) {
    Scene scene = findSceneInCampaign(campaignId, sceneId);
    Hibernate.initialize(scene.getParticipants());
    for (SceneParticipant participant : scene.getParticipants()) {
        Hibernate.initialize(participant.getStatBlock());
    }
    return scene.getEncounter() == null
            && scene.getParticipants().stream().anyMatch(p -> p.getStatBlock() != null);
}
```

At the start of `seedFromScene`, replace the unscoped load with:

```java
Scene scene = findSceneInCampaign(campaignId, sceneId);
```

Add this helper at the end of the service:

```java
private Scene findSceneInCampaign(UUID campaignId, UUID sceneId) {
    return scenes.findByIdAndCampaignId(campaignId, sceneId)
            .orElseThrow(() -> new NotFoundException("Scene not found in campaign"));
}
```

Add imports:

```java
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
```

The existing method-level `@Transactional` on `seedFromScene` remains the single atomic boundary.
Do not catch `RuntimeException` inside it.

- [ ] **Step 10: Render both scene responses from the hydrated detail view**

In `SceneController.sceneDetail`, change eligibility to:

```java
model.addAttribute("canSeedEncounter", encounterSeeder.canSeed(campaignId, id));
```

Replace the first and last parts of `loadActionRail` with:

```java
private String loadActionRail(UUID campaignId, UUID adventureId, UUID sceneId, Model model) {
    AdventureService.SceneDetailView view = adventureService.findSceneDetailView(sceneId);
    Scene scene = view.scene();
    model.addAttribute("scene", scene);
    model.addAttribute("canSeedEncounter", encounterSeeder.canSeed(campaignId, sceneId));
```

```java
    model.addAttribute("sectionThreatCards", view.sectionThreatCards());
    return "adventure/_action-rail :: actionRail";
}
```

Delete the `ThreatCardAssembler` field, constructor parameter, assignment and import from
`SceneController`; the assembler remains owned by `AdventureService.findSceneDetailView`.

In `SceneControllerTest`, remove its `@MockitoBean ThreatCardAssembler` field. Where scene detail
eligibility is asserted, stub and verify the final interface:

```java
when(encounterSeeder.canSeed(campaignId, sceneId)).thenReturn(true);
```

```java
verify(encounterSeeder).canSeed(campaignId, sceneId);
```

- [ ] **Step 11: Run the complete transaction-focused test set**

Run:

```bash
./mvnw -Dtest=PersistenceProfileParityTest,SceneEncounterSeedServiceTest,SceneEncounterSeedAtomicityTest,SceneSeedEncounterControllerTest,SceneControllerTest test
```

Expected: PASS, zero failures and zero errors. The logs must contain no
`LazyInitializationException`.

- [ ] **Step 12: Commit Task 1**

```bash
git add src/test/resources/application.properties \
  src/test/java/dev/hendrikhoemberg/dmhelper/common/config/PersistenceProfileParityTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedServiceTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedAtomicityTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneSeedEncounterControllerTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneControllerTest.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneEncounterSeedService.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java
git commit -m "fix(adventure): make scene encounter seeding transactional"
```

---

### Task 2: Add Encounter Creation to the Cockpit Story Module

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java`
- Modify: `src/main/resources/templates/session/_story-rail.html`
- Modify: `src/main/resources/static/js/session-cockpit.js`

**Interfaces:**
- Consumes: `SceneEncounterSeedService.canSeed(UUID campaignId, UUID sceneId)`, `SceneEncounterSeedService.seedFromScene(UUID campaignId, UUID sceneId)`, existing `sessionCockpit.refreshRails()`.
- Produces: `POST /api/v1/campaigns/{campaignId}/session/scenes/{sceneId}/seed-encounter` returning `SeedResult` JSON, plus `sessionCockpit.seedCurrentScene(sceneId)`.

- [ ] **Step 1: Write the failing session API test**

Add a `@MockitoBean SceneEncounterSeedService encounterSeeder` to
`SessionApiControllerTest`, then add:

```java
@Test
void seedsTheCurrentStorySceneAndReturnsTheTypedReport() throws Exception {
    UUID sceneId = UUID.randomUUID();
    UUID encounterId = UUID.randomUUID();
    when(encounterSeeder.seedFromScene(campaignId, sceneId))
            .thenReturn(new SceneEncounterSeedService.SeedResult(
                    encounterId, "Encounter: Klarg", 4,
                    List.of("Unresolved wolf"), false));

    mvc.perform(post("/api/v1/campaigns/{campaignId}/session/scenes/{sceneId}/seed-encounter",
                    campaignId, sceneId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.encounterId").value(encounterId.toString()))
            .andExpect(jsonPath("$.combatantsAdded").value(4))
            .andExpect(jsonPath("$.skippedParticipants[0]").value("Unresolved wolf"))
            .andExpect(jsonPath("$.alreadyExisted").value(false));

    verify(encounterSeeder).seedFromScene(campaignId, sceneId);
}
```

Add the import:

```java
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
```

- [ ] **Step 2: Write the failing Story-module rendering test**

In `SessionControllerTest`, add `@MockitoBean SceneEncounterSeedService encounterSeeder` and a
test using the populated current-scene workspace already constructed by
`structuredSceneViewPopulatedWhenCurrentScene`:

```java
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
```

```java
@Test
void storyModuleOffersEncounterCreationForAnEligibleCurrentScene() throws Exception {
    SessionWorkspace ws = workspaceWithCurrentScene();
    UUID sceneId = ws.currentScene().getId();
    when(workspaces.load(campaignId, null)).thenReturn(ws);
    when(encounterSeeder.canSeed(campaignId, sceneId)).thenReturn(true);

    mvc.perform(get("/campaigns/{id}/session/rails/story", campaignId))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                    "Start encounter from this scene")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                    "data-scene-id=\"" + sceneId + "\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                    "seedCurrentScene($el.dataset.sceneId)")));
}
```

Extract the current-scene setup presently inside
`structuredSceneViewPopulatedWhenCurrentScene` into:

```java
private SessionWorkspace workspaceWithCurrentScene() {
    Campaign campaign = new Campaign();
    campaign.setId(campaignId);
    campaign.setName("Test Campaign");
    var adventure = new dev.hendrikhoemberg.dmhelper.adventure.data.Adventure();
    adventure.setId(UUID.randomUUID());
    adventure.setName("Test Adventure");
    adventure.setCampaign(campaign);
    var chapter = new dev.hendrikhoemberg.dmhelper.adventure.data.Chapter();
    chapter.setId(UUID.randomUUID());
    chapter.setTitle("Chapter 1");
    chapter.setAdventure(adventure);
    Scene scene = new Scene();
    scene.setId(UUID.randomUUID());
    scene.setTitle("Throne Room");
    scene.setChapter(chapter);
    StructuredSceneView structured = new StructuredSceneView(scene,
            scene.getSections(), scene.getChecks(), scene.getParticipants(),
            scene.getTransitions(), scene.getLinks(), java.util.Map.of());
    return new SessionWorkspace(campaign, CampaignSession.idle(campaign),
            null, SessionWorkspaceService.SelectionSource.NONE,
            scene, null, null, null,
            List.of(), null, List.of(), List.of(), List.of(),
            new CalendarService.InGameDate(1492, 7, 12),
            structured, List.of(), List.of());
}
```

- [ ] **Step 3: Pin the client contract before implementation**

Add to `SessionCockpitTemplateContractTest`:

```java
@Test
void storyCanSeedAnEncounterAndRefreshBothRails() throws IOException {
    String story = Files.readString(
            Path.of("src/main/resources/templates/session/_story-rail.html"));
    String script = Files.readString(
            Path.of("src/main/resources/static/js/session-cockpit.js"));

    assertThat(story)
            .contains("Start encounter from this scene")
            .contains("seedCurrentScene");
    assertThat(script)
            .contains("async seedCurrentScene(sceneId)")
            .contains("/session/scenes/${sceneId}/seed-encounter")
            .contains("await this.refreshRails()")
            .contains("Could not create the scene encounter");
}
```

- [ ] **Step 4: Run the three red tests**

Run:

```bash
./mvnw -Dtest=SessionApiControllerTest,SessionControllerTest,SessionCockpitTemplateContractTest test
```

Expected: FAIL because the endpoint, model attribute, template action and Alpine method do not
exist.

- [ ] **Step 5: Add the typed session endpoint**

Inject `SceneEncounterSeedService` into `SessionApiController`. Replace its constructor with:

```java
public SessionApiController(SessionLifecycleService lifecycle,
                            AdventureService adventures,
                            SessionWorkspaceService workspaces,
                            SceneTransitionService sceneTransitionService,
                            QuestService questService,
                            SceneEncounterSeedService encounterSeeder) {
    this.lifecycle = lifecycle;
    this.adventures = adventures;
    this.workspaces = workspaces;
    this.sceneTransitionService = sceneTransitionService;
    this.questService = questService;
    this.encounterSeeder = encounterSeeder;
}
```

Add the field and endpoint:

```java
@PostMapping("/scenes/{sceneId}/seed-encounter")
SceneEncounterSeedService.SeedResult seedEncounter(@PathVariable UUID campaignId,
                                                    @PathVariable UUID sceneId) {
    return encounterSeeder.seedFromScene(campaignId, sceneId);
}
```

Use this field and constructor parameter:

```java
private final SceneEncounterSeedService encounterSeeder;
```

- [ ] **Step 6: Supply eligibility to both Story render paths**

Inject `SceneEncounterSeedService` into `SessionController` by replacing its constructor with:

```java
public SessionController(SessionWorkspaceService workspaces,
                         AdventureService adventures,
                         SceneEncounterSeedService encounterSeeder) {
    this.workspaces = workspaces;
    this.adventures = adventures;
    this.encounterSeeder = encounterSeeder;
}
```

Add the field and helper:

```java
private final SceneEncounterSeedService encounterSeeder;
```

```java
private void addSeedEligibility(UUID campaignId,
                                SessionWorkspaceService.SessionWorkspace workspace,
                                Model model) {
    boolean eligible = workspace.currentScene() != null
            && workspace.currentScene().getEncounter() == null
            && encounterSeeder.canSeed(campaignId, workspace.currentScene().getId());
    model.addAttribute("canSeedEncounter", eligible);
}
```

Call it after the workspace is loaded in both `cockpit(...)` and `storyRail(...)`:

```java
addSeedEligibility(campaignId, workspace, model);
```

- [ ] **Step 7: Render the Story action**

Inside `.scene-actions` in `_story-rail.html`, add:

```html
<button class="btn btn-primary"
        th:if="${workspace.currentScene.encounter == null and canSeedEncounter}"
        th:attr="data-scene-id=${workspace.currentScene.id}"
        @click="seedCurrentScene($el.dataset.sceneId)">
  Start encounter from this scene
</button>
```

- [ ] **Step 8: Add the Alpine action with visible success and failure**

Add next to `followTransition(...)` in `session-cockpit.js`:

```javascript
async seedCurrentScene(sceneId) {
    try {
        const response = await this.request(
            `/api/v1/campaigns/${this.campaignId}/session/scenes/${sceneId}/seed-encounter`,
            { method: 'POST' });
        const result = await response.json();
        await this.refreshRails();
        const skipped = result.skippedParticipants || [];
        const message = result.alreadyExisted
            ? `${result.encounterName} was already linked.`
            : `${result.encounterName}: ${result.combatantsAdded} combatants added`
                + (skipped.length ? `; skipped: ${skipped.join(', ')}` : '');
        const status = document.getElementById('battleStatusMessage');
        if (status) status.textContent = message;
        window.showToast?.(message, skipped.length ? 'warning' : 'success');
    } catch (error) {
        window.reportActionFailure(
            'Could not create the scene encounter.', error,
            () => this.seedCurrentScene(sceneId));
    }
},
```

- [ ] **Step 9: Run the cockpit-focused tests**

Run:

```bash
./mvnw -Dtest=SessionApiControllerTest,SessionControllerTest,SessionCockpitTemplateContractTest,SceneSeedEncounterControllerTest test
```

Expected: PASS, zero failures and zero errors.

- [ ] **Step 10: Commit Task 2**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiControllerTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/web/SessionControllerTest.java \
  src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionCockpitTemplateContractTest.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionApiController.java \
  src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java \
  src/main/resources/templates/session/_story-rail.html \
  src/main/resources/static/js/session-cockpit.js
git commit -m "feat(session): seed scene encounters from the cockpit"
```

---

### Task 3: Document and Verify the Corrected Workflow

**Files:**
- Modify: `docs/dm-manual/03-session-cockpit.md`

**Interfaces:**
- Consumes: the Story-module action and `SeedResult` behavior delivered by Tasks 1 and 2.
- Produces: operator instructions and final evidence that the workstream is safe to hand off.

- [ ] **Step 1: Update the DM manual with the exact workflow**

Add this subsection immediately after the **Current Scene & Active Encounter** paragraph in
`docs/dm-manual/03-session-cockpit.md`:

```markdown
### Starting an encounter from the current scene

When the current scene contains at least one participant linked to a statblock and has no linked
encounter, the Story module shows **Start encounter from this scene**. The action creates one
encounter, adds the resolved participant quantities, links it back to the scene, and refreshes the
Story and Encounter modules.

Participants without statblocks are named in the result instead of disappearing or receiving
invented statistics. Add those participants manually if they should enter combat. Repeating the
action is safe: DMHelper reports the existing linked encounter and does not duplicate combatants.
```

- [ ] **Step 2: Run formatting and targeted regression checks**

Run:

```bash
git diff --check
./mvnw -Dtest=PersistenceProfileParityTest,SceneEncounterSeedServiceTest,SceneEncounterSeedAtomicityTest,SceneSeedEncounterControllerTest,SceneControllerTest,SessionApiControllerTest,SessionControllerTest,SessionCockpitTemplateContractTest test
```

Expected: `git diff --check` emits no output; Maven reports zero failures and zero errors.

- [ ] **Step 3: Run the complete automated suite**

Run:

```bash
set -o pipefail
./mvnw test 2>&1 | tee /tmp/dmhelper-a1-tests.log
! rg -n "LazyInitializationException" /tmp/dmhelper-a1-tests.log
```

Expected: BUILD SUCCESS with zero failures and zero errors. The final `rg` command returns no
matches and the negation exits successfully.

- [ ] **Step 4: Perform the browser smoke against a disposable campaign**

Start the app with:

```bash
./mvnw spring-boot:run
```

In a disposable synthetic or private local campaign:

1. open a scene with at least one statblock-linked participant;
2. confirm the scene page offers **Start encounter from this scene**;
3. use the action and confirm the response succeeds without an error toast;
4. enter the cockpit, select another eligible scene and use the Story-module action;
5. confirm the Story module shows the linked encounter and the Encounter module lists it;
6. repeat the action through the API or UI and confirm no duplicate encounter or combatants;
7. inspect application logs and confirm no `LazyInitializationException` occurred.

Expected: every action returns success, each scene has exactly one linked encounter, and skipped
participant names are visible.

- [ ] **Step 5: Commit Task 3**

```bash
git add docs/dm-manual/03-session-cockpit.md
git commit -m "docs(session): explain scene encounter creation"
```

- [ ] **Step 6: Record final repository evidence**

Run:

```bash
git status --short
git log -3 --oneline
```

Expected: no tracked changes remain. The only permitted untracked path is the pre-existing local
`artifacts/` evidence directory. The last three commits correspond to Tasks 1–3.
