# UI Polish & Broken-Flow Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix every defect found in the 2026-07-21 UI/UX evaluation (broken adventure planning, phantom error banners, JSON 404s, PIN leak in player-safe mode, map-island JS crashes, unusable character-sheet layout) and raise overall UI polish through consistent page headers, typography, filter bars, forms, and date formatting.

**Architecture:** Server-rendered Thymeleaf + htmx pages with vanilla-JS islands (Konva map, Alpine sprinkles). Fixes follow existing idioms: hydrate lazy JPA collections inside `@Transactional` service methods before templates render (`open-in-view=false`), template-contract tests as regression nets for markup, and a new full-page render smoke test that catches lazy-init truncation for the whole app. Polish is CSS-token-driven — no new dependencies, no build step.

**Tech Stack:** Spring Boot 4.1 (Java 25), Spring Data JPA + H2, Thymeleaf, htmx, Alpine.js (vendored), Konva (vendored), JUnit 5 + MockMvc/`@SpringBootTest`, Maven wrapper.

## Global Constraints

- `spring.jpa.open-in-view=false` — every lazy collection a template touches MUST be initialized inside a `@Transactional` service method (idiom: `Hibernate.initialize(...)`, see `SessionWorkspaceService.initializeSceneForView`).
- `src/main/resources/static/css/tokens.css` is the ONLY css file allowed to contain raw color/size values; all other CSS uses `var(--...)` tokens.
- No JS build step: vendored libraries only, native ES modules, no new npm/CDN dependencies.
- Test slices use Boot 4 packages: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `@MockitoBean` (not `@MockBean`).
- Tests must never touch `~/.dmhelper` (test profile already forces `jdbc:h2:mem:testdb`).
- Run tests with `./mvnw test -Dtest=<ClassName>`; full suite `./mvnw test`.
- Commit style: `fix(scope): ...` / `feat(scope): ...` / `refactor(scope): ...`, one commit per task.
- The canonical page-header pattern (from `adventure/detail.html`) is:
  ```html
  <div class="page-header">
      <div>
          <div class="page-header-eyebrow">EYEBROW</div>
          <h1>Title</h1>
          <div class="rule-taper rule-taper--gold"></div>
      </div>
      <div class="page-header-actions">
          <a ... class="btn btn-ghost">&larr; Back ...</a>
          <button class="btn btn-primary">+ New ...</button>
      </div>
  </div>
  ```
  Back/ghost links come before the primary button; em dash `—` (never `--`) in titles.

---

## Phase 1 — Regression net + broken flows

### Task 1: Full-page render smoke test (the regression net)

Renders every campaign-scoped GET page against a seeded H2 database and asserts each response is **complete** HTML. This fails today for `/adventures/{id}` (truncated mid-stream by `LazyInitializationException`) and `/adventures/{id}/scenes/{id}` (500). It is written first so Tasks 2–3 turn it green, and it permanently guards against the whole class of "lazy init outside transaction" bugs.

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java`

**Interfaces:**
- Consumes: `AdventureService.createAdventure(UUID campaignId, String name, String description, String sourceAttribution)`, `createChapter(UUID adventureId, String title, String intro)`, `createScene(UUID chapterId, String title, String sceneKey, String body)` — all existing.
- Produces: failing tests that Task 2 and Task 3 make pass. Later tasks keep it green.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every full-page GET must return 200 AND a complete HTML document.
 * With open-in-view=false, a LazyInitializationException thrown mid-render
 * truncates the chunked response after status 200 is already committed —
 * so completeness (closing </html>) is asserted, not just the status.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullPageRenderSmokeTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private AdventureService adventureService;

    private UUID campaignId;
    private UUID adventureId;
    private UUID sceneId;

    @BeforeAll
    void seed() {
        Campaign campaign = new Campaign();
        campaign.setName("Smoke Campaign");
        campaign.setDescription("Render-smoke fixture");
        campaignId = campaignRepository.save(campaign).getId();

        Adventure adventure = adventureService.createAdventure(
                campaignId, "Smoke Adventure", "desc", null);
        adventureId = adventure.getId();
        Chapter chapter = adventureService.createChapter(adventureId, "Chapter One", "intro");
        Scene scene = adventureService.createScene(chapter.getId(), "Opening Scene", "S1", "The hallway is dark.");
        sceneId = scene.getId();
    }

    List<String> pages() {
        String c = "/campaigns/" + campaignId;
        return List.of(
                "/campaigns",
                c,
                c + "/adventures",
                c + "/adventures/" + adventureId,
                c + "/adventures/" + adventureId + "/scenes/" + sceneId,
                c + "/encounters",
                c + "/maps",
                c + "/handouts",
                c + "/audio/cues",
                c + "/notes",
                c + "/party",
                c + "/sheets",
                c + "/treasury",
                c + "/ledger",
                c + "/world/npcs",
                c + "/world/locations",
                c + "/world/factions",
                c + "/quests",
                c + "/calendar",
                c + "/session",
                "/library",
                "/library/tables",
                "/library/traps",
                "/library/hazards"
        );
    }

    @ParameterizedTest
    @MethodSource("pages")
    void pageRendersCompletely(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "text/html");
        ResponseEntity<String> response = rest.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value())
                .as("status for %s", path)
                .isEqualTo(200);
        assertThat(response.getBody())
                .as("body for %s must be a complete document", path)
                .isNotNull();
        assertThat(response.getBody().strip())
                .as("body for %s must end with </html> (truncation = lazy-init mid-render)", path)
                .endsWith("</html>");
    }
}
```

- [ ] **Step 2: Run to verify it fails on the two adventure pages**

Run: `./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: FAIL — `/adventures/{id}` fails the `endsWith </html>` assertion (truncated body), `/adventures/{id}/scenes/{id}` fails with status 500. All other pages pass. If any *additional* page fails, note it — Tasks 2–3 must cover it too.

- [ ] **Step 3: Commit the red test (skipped-marker free, it documents the bug)**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/web/FullPageRenderSmokeTest.java
git commit -m "test(web): add full-page render smoke test exposing adventure lazy-init failures"
```

---

### Task 2: Fix adventure detail truncation (Chapter.scenes lazy init)

`adventure/_chapter-list.html` iterates `ch.scenes` after `AdventureService.findChaptersByAdventure` returns detached entities. Hydrate inside the transaction — same idiom as `SessionWorkspaceService.initializeSceneForView`.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java:139-142`

**Interfaces:**
- Consumes: `ChapterRepository.findByAdventureIdOrderBySortOrderAsc(UUID)` — existing.
- Produces: `findChaptersByAdventure(UUID)` now returns chapters with `getScenes()` initialized. `AdventureController.detail` / `chapterListView` and `SceneController.sceneDetail` (Task 3) rely on this.

- [ ] **Step 1: Modify `findChaptersByAdventure` to hydrate scenes**

In `AdventureService.java`, add the import:

```java
import org.hibernate.Hibernate;
```

and replace:

```java
    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByAdventure(UUID adventureId) {
        return chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId);
    }
```

with:

```java
    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByAdventure(UUID adventureId) {
        List<Chapter> chapters = chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId);
        // open-in-view=false: the chapter list template iterates ch.scenes after the TX ends.
        chapters.forEach(ch -> Hibernate.initialize(ch.getScenes()));
        return chapters;
    }
```

- [ ] **Step 2: Run the smoke test — adventure detail now passes**

Run: `./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: `/adventures/{id}` passes. `/adventures/{id}/scenes/{id}` still fails (Task 3).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java
git commit -m "fix(adventure): hydrate chapter scenes before render, unbreaking adventure detail"
```

---

### Task 3: Fix scene detail 500 (Scene hydration + threat cards in one transaction)

`SceneController.sceneDetail` calls `adventureService.findSceneById(id)` (detached) and then `threatCardAssembler.forScene(scene)` **outside any transaction** — both the template and the assembler touch lazy collections. Add a service method that hydrates the scene and assembles threat cards inside one read-only transaction, mirroring `SessionWorkspaceService.initializeSceneForView`.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java:72-96`

**Interfaces:**
- Consumes: `ThreatCardAssembler.forScene(Scene)` → `Map<UUID, ThreatCardView>` (packages `dev.hendrikhoemberg.dmhelper.threat.service.ThreatCardAssembler`, `dev.hendrikhoemberg.dmhelper.threat.web.ThreatCardView`).
- Produces: `AdventureService.SceneDetailView findSceneDetailView(UUID id)` — record `SceneDetailView(Scene scene, Map<UUID, ThreatCardView> sectionThreatCards)`.

- [ ] **Step 1: Add the hydrating view method to `AdventureService`**

Add imports:

```java
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCardAssembler;
import dev.hendrikhoemberg.dmhelper.threat.web.ThreatCardView;
import java.util.Map;
```

Add a `ThreatCardAssembler threatCardAssembler` field and constructor parameter (append to the existing constructor parameter list and assign like the other fields). Then add:

```java
    public record SceneDetailView(Scene scene, Map<UUID, ThreatCardView> sectionThreatCards) {}

    /**
     * Scene plus everything the scene-detail template and threat cards read after
     * the transaction ends (open-in-view=false). Mirrors
     * SessionWorkspaceService.initializeSceneForView.
     */
    @Transactional(readOnly = true)
    public SceneDetailView findSceneDetailView(UUID id) {
        Scene scene = findSceneById(id);
        Hibernate.initialize(scene.getStatBlocks());
        Hibernate.initialize(scene.getHandouts());
        Hibernate.initialize(scene.getMap());
        Hibernate.initialize(scene.getEncounter());
        Hibernate.initialize(scene.getSceneAudioCue());
        Hibernate.initialize(scene.getSections());
        Hibernate.initialize(scene.getChecks());
        Hibernate.initialize(scene.getParticipants());
        Hibernate.initialize(scene.getTransitions());
        scene.getTransitions().forEach(t -> Hibernate.initialize(t.getTargetScene()));
        Hibernate.initialize(scene.getLinks());
        if (scene.getChapter() != null) {
            Hibernate.initialize(scene.getChapter());
            Hibernate.initialize(scene.getChapter().getAdventure());
        }
        return new SceneDetailView(scene, threatCardAssembler.forScene(scene));
    }
```

- [ ] **Step 2: Use it in `SceneController.sceneDetail`**

Replace lines 77 and 93:

```java
        Scene scene = adventureService.findSceneById(id);
```
→
```java
        AdventureService.SceneDetailView view = adventureService.findSceneDetailView(id);
        Scene scene = view.scene();
```
and
```java
        model.addAttribute("sectionThreatCards", threatCardAssembler.forScene(scene));
```
→
```java
        model.addAttribute("sectionThreatCards", view.sectionThreatCards());
```

(Leave the controller's `threatCardAssembler` field in place — other endpoints still use it.)

- [ ] **Step 3: Run the smoke test — everything green**

Run: `./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS, all 24 pages.

- [ ] **Step 4: Run the full suite to catch fallout**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java
git commit -m "fix(adventure): hydrate scene and assemble threat cards in one transaction"
```

---

### Task 4: Kill the phantom error banner (th:if + th:replace precedence)

15 templates use `<div th:if="${error != null}" th:replace="...">`. `th:replace` (precedence 100) executes **before** `th:if` (300), so the banner always renders — with an empty message and a bare correlation UUID. `fragments/navbar.html:79-81` already documents the correct pattern. Wrap in `th:block th:if`, and make `_error.html` fall back to a readable message.

**Files:**
- Modify (identical one-line change in each):
  - `src/main/resources/templates/world/npcs-list.html:19`
  - `src/main/resources/templates/world/npcs-form.html:15`
  - `src/main/resources/templates/world/npcs-detail.html:33`
  - `src/main/resources/templates/world/locations-list.html:19`
  - `src/main/resources/templates/world/locations-form.html`
  - `src/main/resources/templates/world/locations-detail.html`
  - `src/main/resources/templates/world/factions-list.html:19`
  - `src/main/resources/templates/world/factions-form.html:15`
  - `src/main/resources/templates/world/factions-detail.html`
  - `src/main/resources/templates/quest/list.html:19`
  - `src/main/resources/templates/quest/detail.html:33`
  - `src/main/resources/templates/quest/_objective-list.html:2`
  - `src/main/resources/templates/quest/_dependency-list.html`
  - `src/main/resources/templates/quest/_link-list.html`
  - `src/main/resources/templates/adventure/_action-rail.html:2`
- Modify: `src/main/resources/templates/common/_error.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/world/web/WorldControllerErrorBannerTest.java` (create)

**Interfaces:**
- Consumes: `WorldService` (mocked), `CampaignRepository` (mocked) — `WorldController` dependencies also include `RollableTableRepository`, `WorldLocationTableLinkRepository`, `TableReferenceResolver`, `AudioCueRepository`; all mocked.
- Produces: banner renders only when `error` is non-null.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.world.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableReferenceResolver;
import dev.hendrikhoemberg.dmhelper.world.service.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorldController.class)
class WorldControllerErrorBannerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WorldService worldService;
    @MockitoBean private CampaignRepository campaignRepository;
    @MockitoBean private RollableTableRepository rollableTableRepository;
    @MockitoBean private WorldLocationTableLinkRepository locationTableLinkRepository;
    @MockitoBean private TableReferenceResolver referenceResolver;
    @MockitoBean private AudioCueRepository audioCueRepository;

    private UUID stubCampaign() {
        UUID id = UUID.randomUUID();
        Campaign c = new Campaign();
        c.setId(id);
        c.setName("Test");
        when(campaignRepository.findById(id)).thenReturn(Optional.of(c));
        return id;
    }

    @Test
    void npcListWithoutErrorShowsNoBanner() throws Exception {
        UUID cid = stubCampaign();
        when(worldService.getNpcs(cid)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/" + cid + "/world/npcs"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("alert-error"))));
    }

    @Test
    void failedNpcCreateShowsBannerWithMessage() throws Exception {
        UUID cid = stubCampaign();
        when(worldService.createNpc(eq(cid), any()))
                .thenThrow(new IllegalArgumentException("Name is required"));
        when(worldService.getFactions(cid)).thenReturn(List.of());
        when(worldService.getLocations(cid)).thenReturn(List.of());

        mockMvc.perform(post("/campaigns/" + cid + "/world/npcs").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert-error")))
                .andExpect(content().string(containsString("Name is required")));
    }
}
```

- [ ] **Step 2: Run to verify the first test fails**

Run: `./mvnw test -Dtest=WorldControllerErrorBannerTest`
Expected: `npcListWithoutErrorShowsNoBanner` FAILS (banner present); `failedNpcCreateShowsBannerWithMessage` passes.

- [ ] **Step 3: Fix all 15 templates**

In each listed file, replace the line (whitespace varies per file, keep the file's indentation):

```html
<div th:if="${error != null}" th:replace="~{common/_error :: error(message=${error})}"></div>
```

with:

```html
<th:block th:if="${error != null}"><div th:replace="~{common/_error :: error(message=${error})}"></div></th:block>
```

Verify no stragglers remain:

Run: `grep -rn 'th:if="${error != null}" th:replace' src/main/resources/templates/`
Expected: no output.

- [ ] **Step 4: Harden the fragment against empty messages**

In `common/_error.html` replace:

```html
    <span th:text="${message}">The request could not be completed.</span>
```

with:

```html
    <span th:text="${message != null and !#strings.isEmpty(message) ? message : 'The request could not be completed.'}">The request could not be completed.</span>
```

- [ ] **Step 5: Run tests**

Run: `./mvnw test -Dtest=WorldControllerErrorBannerTest`
Expected: PASS (both).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates src/test/java/dev/hendrikhoemberg/dmhelper/world/web/WorldControllerErrorBannerTest.java
git commit -m "fix(templates): stop error banner rendering unconditionally (th:replace precedence)"
```

---

### Task 5: Styled 404 page for browser navigations

Unmapped URLs opened in a browser return raw `problem+json`. The styled `error.html` exists — route browser-facing 404s to it via a dedicated advice that outranks `GlobalExceptionHandler`'s base-class handling.

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/NotFoundPageAdvice.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/web/NotFoundPageAdviceTest.java` (create)

**Interfaces:**
- Consumes: `CorrelationIdFilter.current(HttpServletRequest)` → `String`; template `error.html` (model keys `status`, `correlationId`).
- Produces: HTML 404 for `Accept: text/html` non-htmx requests; ProblemDetail JSON otherwise.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.campaign.web.CampaignController;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CampaignController.class)
class NotFoundPageAdviceTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CampaignService service;
    @MockitoBean private NoteService noteService;
    @MockitoBean private PartyMemberService partyMemberService;
    @MockitoBean private AudioCueRepository audioCueRepository;
    @MockitoBean private CampaignRepository campaignRepository;

    @Test
    void browserNavigationGetsStyled404Page() throws Exception {
        mockMvc.perform(get("/definitely-not-a-page")
                        .header("Accept", "text/html,application/xhtml+xml"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("wandered off the map")));
    }

    @Test
    void apiClientStillGetsProblemJson() throws Exception {
        mockMvc.perform(get("/definitely-not-a-page")
                        .header("Accept", "application/json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
```

(If `CampaignController` has different constructor dependencies than mocked above, mirror the `@MockitoBean` set from the existing `CampaignControllerTest`.)

- [ ] **Step 2: Run to verify the HTML test fails**

Run: `./mvnw test -Dtest=NotFoundPageAdviceTest`
Expected: `browserNavigationGetsStyled404Page` FAILS (JSON body); the JSON test passes.

- [ ] **Step 3: Implement the advice**

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

/**
 * Browser navigations to unknown URLs get the styled error page; API/htmx
 * clients keep ProblemDetail JSON. Must outrank GlobalExceptionHandler's
 * ResponseEntityExceptionHandler base handling, hence HIGHEST_PRECEDENCE.
 */
@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotFoundPageAdvice {

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Object handleMissingPage(Exception ex, HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        boolean htmx = "true".equals(request.getHeader("HX-Request"));
        if (!htmx && accept != null && accept.contains("text/html")) {
            ModelAndView mav = new ModelAndView("error");
            mav.addObject("status", 404);
            mav.addObject(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
            mav.setStatus(HttpStatus.NOT_FOUND);
            return mav;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "The requested item could not be found. Reload and try again.");
        problem.setTitle("Not Found");
        problem.setType(URI.create("urn:dmhelper:http-404"));
        problem.setProperty(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
```

Note: `@ControllerAdvice(annotations = Controller.class)` still catches `NoResourceFoundException` because it is raised by the dispatcher, not a controller — if the HTML test still fails after this step, drop the `annotations` restriction to plain `@ControllerAdvice`.

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest=NotFoundPageAdviceTest`
Expected: PASS (both).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/web/NotFoundPageAdvice.java src/test/java/dev/hendrikhoemberg/dmhelper/common/web/NotFoundPageAdviceTest.java
git commit -m "fix(web): serve styled 404 page to browsers instead of raw problem JSON"
```

---

### Task 6: Hide the PIN (and DM-flavored dashboard content) in player-safe mode

The PIN exists to keep LAN players out of the DM UI — it must vanish exactly when the screen is shown to players. The app already has the convention: `body.dm-mode-off .dm-only { ... }` in `base.css:174`.

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html:73-74`
- Modify: `src/main/resources/templates/campaigns/detail.html:82-92` (Recent Notes card)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/PlayerSafeChromeContractTest.java` (create)

**Interfaces:**
- Consumes: existing CSS `body.dm-mode-off .dm-only` (hidden) — no CSS change needed.
- Produces: `.pin-display` and the Recent Notes card carry `dm-only`.

- [ ] **Step 1: Write the failing contract test** (file-content style, like `SessionCockpitTemplateContractTest`)

```java
package dev.hendrikhoemberg.dmhelper.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerSafeChromeContractTest {

    @Test
    void pinDisplayIsHiddenInPlayerSafeMode() throws IOException {
        String navbar = Files.readString(
                Path.of("src/main/resources/templates/fragments/navbar.html"));
        assertThat(navbar)
                .as("PIN must carry dm-only so player-safe mode hides it")
                .contains("class=\"pin-display dm-only\"");
    }

    @Test
    void recentNotesCardIsHiddenInPlayerSafeMode() throws IOException {
        String dashboard = Files.readString(
                Path.of("src/main/resources/templates/campaigns/detail.html"));
        assertThat(dashboard)
                .as("Recent note titles can spoil quests; hide the card in player-safe mode")
                .contains("card dash-card dm-only");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=PlayerSafeChromeContractTest`
Expected: FAIL (both).

- [ ] **Step 3: Apply the template changes**

`fragments/navbar.html` — replace:

```html
        <span class="pin-display" id="pinDisplay"
              th:text="${pinDisplay}">PIN: -----</span>
```

with:

```html
        <span class="pin-display dm-only" id="pinDisplay"
              th:text="${pinDisplay}">PIN: -----</span>
```

`campaigns/detail.html` — the Recent Notes card (and the Session Plan card, which also previews DM prep) get `dm-only`:

```html
                <section class="card dash-card" style="--stagger: 0">
                    <h3>Session Plan</h3>
```
→
```html
                <section class="card dash-card dm-only" style="--stagger: 0">
                    <h3>Session Plan</h3>
```

```html
                <section class="card dash-card" style="--stagger: 1">
                    <h3>Recent Notes</h3>
```
→
```html
                <section class="card dash-card dm-only" style="--stagger: 1">
                    <h3>Recent Notes</h3>
```

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest=PlayerSafeChromeContractTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/fragments/navbar.html src/main/resources/templates/campaigns/detail.html src/test/java/dev/hendrikhoemberg/dmhelper/common/PlayerSafeChromeContractTest.java
git commit -m "fix(player-safe): hide PIN and DM prep cards when DM mode is off"
```

---

### Task 7: Workspace map selection — honor explicit map, fall through null stored map

`SessionWorkspaceService.select()` returns the stored workspace map for any open session **even when it is null**, so a running session shows "No map selected" although the current scene has a linked map, and `/maps/{id}/play` (which passes `requestedMapId`) is ignored. New precedence: explicit request → stored (non-null) → active encounter → current scene → session plan → none.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java:213-237` (the `select` method)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionWorkspaceMapSelectionTest.java` (create)

**Interfaces:**
- Consumes: `SessionWorkspaceService.load(UUID campaignId, UUID requestedMapId)` → `SessionWorkspace` (record field `workspaceMap()`, `selectionSource()`); `SessionLifecycleService` to open a session (mirror usage from `CoreSessionLoopSmokeTest`); `GameMapService`/`GameMapRepository` to create maps; `AdventureService` for scene + `Scene.setMap`.
- Produces: fixed `select()`; cockpit shows the linked map when resuming.

- [ ] **Step 1: Write the failing integration test**

```java
package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SessionWorkspaceMapSelectionTest {

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private GameMapRepository mapRepository;
    @Autowired private AdventureService adventureService;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SessionLifecycleService lifecycleService;
    @Autowired private SessionWorkspaceService workspaceService;

    private UUID campaignId;
    private GameMap sceneMap;
    private GameMap otherMap;

    @BeforeEach
    void seed() {
        Campaign campaign = new Campaign();
        campaign.setName("Selection Test");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();

        sceneMap = new GameMap();
        sceneMap.setCampaign(campaign);
        sceneMap.setName("Scene Map");
        sceneMap = mapRepository.save(sceneMap);

        otherMap = new GameMap();
        otherMap.setCampaign(campaign);
        otherMap.setName("Other Map");
        otherMap = mapRepository.save(otherMap);

        Adventure adventure = adventureService.createAdventure(campaignId, "A", null, null);
        Chapter chapter = adventureService.createChapter(adventure.getId(), "C1", null);
        Scene scene = adventureService.createScene(chapter.getId(), "S1", null, null);
        scene.setMap(sceneMap);
        sceneRepository.save(scene);
        adventureService.setCurrentScene(campaignId, scene.getId());
    }

    @Test
    void openSessionWithoutStoredMapFallsThroughToCurrentSceneMap() {
        lifecycleService.start(campaignId);

        SessionWorkspaceService.SessionWorkspace ws = workspaceService.load(campaignId, null);

        assertThat(ws.workspaceMap()).isNotNull();
        assertThat(ws.workspaceMap().getId()).isEqualTo(sceneMap.getId());
        assertThat(ws.selectionSource())
                .isEqualTo(SessionWorkspaceService.SelectionSource.CURRENT_SCENE);
    }

    @Test
    void explicitlyRequestedMapWinsEvenWithOpenSession() {
        lifecycleService.start(campaignId);

        SessionWorkspaceService.SessionWorkspace ws =
                workspaceService.load(campaignId, otherMap.getId());

        assertThat(ws.workspaceMap().getId()).isEqualTo(otherMap.getId());
        assertThat(ws.selectionSource())
                .isEqualTo(SessionWorkspaceService.SelectionSource.EXPLICIT_MAP);
    }
}
```

Adjust two call sites to the actual API if they differ (check before running): the lifecycle start method (`SessionLifecycleService` — use the method `CoreSessionLoopSmokeTest` uses to open a session) and `adventureService.setCurrentScene` (find the real name with `grep -n "urrentScene" src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java`). `GameMap` setters: verify with `grep -n "void set" src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMap.java` and set any required fields (e.g. `widthCells`/`heightCells`) if the entity mandates them.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=SessionWorkspaceMapSelectionTest`
Expected: both tests FAIL — `workspaceMap` is null / stored-session source wins.

- [ ] **Step 3: Fix `select()`**

Replace the method body:

```java
    private Selection select(CampaignSession session, Encounter active, Scene current,
                             SessionPlanService.SessionPlan plan, UUID requestedMapId, UUID campaignId) {
        if (requestedMapId != null) {
            GameMap requested = maps.findById(requestedMapId)
                    .filter(m -> m.getCampaign().getId().equals(campaignId))
                    .orElseThrow(() -> new NotFoundException("Map not found in campaign"));
            return new Selection(requested, SelectionSource.EXPLICIT_MAP);
        }
        if (session.isOpen() && session.getWorkspaceMap() != null)
            return new Selection(session.getWorkspaceMap(), SelectionSource.STORED_SESSION);
        if (active != null && active.getMap() != null)
            return new Selection(active.getMap(), SelectionSource.ACTIVE_ENCOUNTER);
        if (current != null && current.getMap() != null)
            return new Selection(current.getMap(), SelectionSource.CURRENT_SCENE);
        if (plan != null) {
            Optional<GameMap> firstPlanMap = plan.beats().stream()
                    .filter(SessionPlanService.SessionPlanBeat::resolved)
                    .map(SessionPlanService.SessionPlanBeat::mapId)
                    .filter(Objects::nonNull)
                    .map(maps::findById).flatMap(Optional::stream)
                    .filter(m -> m.getCampaign().getId().equals(campaignId)).findFirst();
            if (firstPlanMap.isPresent()) return new Selection(firstPlanMap.get(), SelectionSource.SESSION_PLAN);
        }
        return new Selection(null, SelectionSource.NONE);
    }
```

- [ ] **Step 4: Run the new test and the whole session test package**

Run: `./mvnw test -Dtest='SessionWorkspaceMapSelectionTest' && ./mvnw test -Dtest='dev.hendrikhoemberg.dmhelper.session.*'`
Expected: PASS. If an existing test pinned the old "open session always wins" behavior, update that test's expectation — the old behavior is the bug.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/session/service/SessionWorkspaceService.java src/test/java/dev/hendrikhoemberg/dmhelper/session/SessionWorkspaceMapSelectionTest.java
git commit -m "fix(session): honor requested map and fall through null stored workspace map"
```

---

### Task 8: Map islands — never crash on malformed shape records

`map-editor.js` `addShapeNode(konvaLayer, shape)` dereferences `shape.fill` / `shape.stroke` / `shape.label` without a null guard; a null/garbage record in a layer's `shapes` array throws uncaught (`Cannot read properties of null (reading 'fill')` — observed on the editor and play pages). Guard the render path and drop malformed records at load.

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js` (`addShapeNode`, ~line 351)
- Modify: `src/main/resources/static/js/map/battle-map.js` (same guard if it has a shape-render function — check with `grep -n "addShapeNode\|shape.fill" src/main/resources/static/js/map/battle-map.js`)

**Interfaces:**
- Produces: `addShapeNode` returns `null` for malformed records; render loops already tolerate `null` (the `default:` case returns null today).

- [ ] **Step 1: Add the guard at the top of `addShapeNode` in `map-editor.js`**

```js
    addShapeNode(konvaLayer, shape) {
        // Imported/older map data can contain null or truncated shape records;
        // a single bad record must not take down the whole canvas.
        if (!shape || typeof shape !== 'object' || !Array.isArray(shape.points)) {
            console.warn('Skipping malformed shape record', shape);
            return null;
        }
        const px = (v) => v * this.cellSizePx;
        const pts = shape.points || [];
```

(The existing body continues unchanged from `const fill = shape.fill || SHAPE_COLORS.fill;`.)

- [ ] **Step 2: Apply the identical guard to `battle-map.js` if it renders shapes**

Run: `grep -n "shape" src/main/resources/static/js/map/battle-map.js | head -20` — if a function iterates shape records and reads `.fill`/`.label`, insert the same three-line guard at its top. If battle-map only renders terrain/tokens (no shapes), skip.

- [ ] **Step 3: Verify in the running app**

```bash
./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--dmhelper.open-browser=false" &
timeout 60 bash -c 'until curl -sf http://localhost:8081/ >/dev/null; do sleep 2; done'
```

Open the map editor and a map play page for an existing map (or drive headless Chromium as in `docs/superpowers/plans/` verification convention) and confirm the browser console shows **no** uncaught `Cannot read properties of null` errors — at most the new `Skipping malformed shape record` warnings. Then stop the server: `lsof -ti:8081 -sTCP:LISTEN | xargs -r kill`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/static/js/map/battle-map.js
git commit -m "fix(map): skip malformed shape records instead of crashing the canvas"
```

---

## Phase 2 — Character sheet redesign

### Task 9: Sheet CSS primitives — stat grid, ability tiles, sheet layout

The sheet page renders as stacked label/value lines because `.pm-stats` is only styled inside `.party-member-card`, and the ability grid collapses because the six tiles sit inside a single `<form>` child of a 3-column grid. Introduce reusable primitives.

**Files:**
- Modify: `src/main/resources/static/css/components.css`

**Interfaces:**
- Produces CSS classes consumed by Tasks 10–11: `.stat-grid`, `.stat-grid--tiles`, `.sheet-layout`, `.sheet-toc`, fixed `.sheet-ability-grid`.

- [ ] **Step 1: Append the primitives to `components.css`**

```css
/* ---- Sheet redesign primitives (2026-07-21 UI polish) ---- */

/* Inline label-over-value stat row; replaces bare <dl> stacking on sheet pages. */
.stat-grid {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-sm) var(--space-lg);
    margin: 0;
}
.stat-grid > div { min-width: 72px; }
.stat-grid dt {
    color: var(--color-text-muted);
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    white-space: nowrap;
}
.stat-grid dd {
    margin: 0;
    font-weight: 600;
    font-size: var(--text-lg);
    font-variant-numeric: tabular-nums;
}

/* Hero variant: boxed tiles for AC / HP / Init / Speed. */
.stat-grid--tiles > div {
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    padding: var(--space-sm) var(--space-md);
    text-align: center;
}

/* The ability form must not count as one grid child. */
.sheet-ability-grid { max-width: none; }
.sheet-ability-grid > form { display: contents; }
.sheet-ability-grid {
    grid-template-columns: repeat(auto-fit, minmax(88px, 110px));
}
.sheet-ability-grid .btn { grid-column: 1 / -1; justify-self: start; }

/* Two-column sheet: sticky section nav + content. */
.sheet-layout {
    display: grid;
    grid-template-columns: 200px minmax(0, 1fr);
    gap: var(--space-lg);
    align-items: start;
}
.sheet-toc {
    position: sticky;
    top: var(--space-lg);
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
    font-size: var(--text-sm);
}
.sheet-toc a {
    color: var(--color-text-muted);
    text-decoration: none;
    padding: 2px var(--space-sm);
    border-left: 2px solid transparent;
}
.sheet-toc a:hover {
    color: var(--color-accent);
    border-left-color: var(--color-gold-soft);
}
@media (max-width: 900px) {
    .sheet-layout { grid-template-columns: 1fr; }
    .sheet-toc { position: static; flex-direction: row; flex-wrap: wrap; }
}

/* Condition picker chips (replaces the raw JSON input). */
.condition-picker {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-xs);
}
.condition-chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: 1px solid var(--color-border);
    border-radius: 999px;
    padding: 2px 10px;
    font-size: var(--text-sm);
    cursor: pointer;
    user-select: none;
}
.condition-chip:has(input:checked) {
    border-color: var(--color-accent);
    color: var(--color-accent);
    background: var(--color-gold-soft);
}
.condition-chip input { position: absolute; opacity: 0; pointer-events: none; }

/* Death-save pips. */
.death-saves { display: flex; align-items: center; gap: var(--space-sm); }
.death-saves .pip-group { display: inline-flex; gap: 4px; }
```

- [ ] **Step 2: Sanity-check the CSS parses (app boots, no template change yet)**

Run: `./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS (CSS-only change).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/components.css
git commit -m "feat(sheet): add stat-grid, sheet-layout and condition-picker CSS primitives"
```

---

### Task 10: Sheet detail restructure — hero strip, section nav, stat grids

**Files:**
- Modify: `src/main/resources/templates/sheet/detail.html`
- Modify: `src/main/resources/templates/sheet/_derived-stats.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/SheetTemplateContractTest.java` (create)

**Interfaces:**
- Consumes: Task 9 CSS classes.
- Produces: sheet page with `.sheet-layout`, `#sheet-<section>` anchor ids, `stat-grid` classes.

- [ ] **Step 1: Write the failing contract test**

```java
package dev.hendrikhoemberg.dmhelper.sheet;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SheetTemplateContractTest {

    @Test
    void sheetUsesLayoutWithSectionNavAndStatGrids() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/sheet/detail.html"));
        assertThat(detail).contains("sheet-layout");
        assertThat(detail).contains("sheet-toc");
        assertThat(detail).contains("id=\"sheet-combat\"");
        assertThat(detail).contains("id=\"sheet-skills\"");
        assertThat(detail).contains("stat-grid");
        // The bare <dl> overview must be gone.
        assertThat(detail).doesNotContain("<dl>\n                    <dt>Class &amp; Level</dt>");
    }

    @Test
    void derivedStatsRenderAsTiles() throws IOException {
        String derived = Files.readString(Path.of("src/main/resources/templates/sheet/_derived-stats.html"));
        assertThat(derived).contains("stat-grid stat-grid--tiles");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=SheetTemplateContractTest`
Expected: FAIL.

- [ ] **Step 3: Restructure `sheet/detail.html`**

Inside `<div th:if="${hasSheet}">`, wrap everything in the two-column layout and convert the Overview `<dl>`. The opening becomes:

```html
        <div th:if="${hasSheet}">
          <div class="sheet-layout">
            <nav class="sheet-toc" aria-label="Sheet sections">
                <a href="#sheet-overview">Overview</a>
                <a href="#sheet-abilities">Abilities</a>
                <a href="#sheet-combat">Combat</a>
                <a href="#sheet-live">Live State</a>
                <a href="#sheet-saves">Saves</a>
                <a href="#sheet-skills">Skills</a>
                <a href="#sheet-passives">Passives</a>
                <a href="#sheet-attacks">Attacks</a>
                <a href="#sheet-features">Features</a>
                <a href="#sheet-spells">Spells</a>
                <a href="#sheet-resources">Resources</a>
                <a href="#sheet-overrides">Overrides</a>
                <a href="#sheet-proficiencies">Proficiencies</a>
                <a href="#sheet-inventory">Inventory</a>
            </nav>
            <div>
            <div class="detail-section" id="sheet-overview">
                <h2>Overview</h2>
                <dl class="stat-grid">
                    <div><dt>Class &amp; Level</dt><dd th:text="${sheet.derivedValues().classAndLevel()}">Fighter 5</dd></div>
                    <div><dt>Total Level</dt><dd th:text="${sheet.derivedValues().totalLevel()}">5</dd></div>
                    <div><dt>Prof. Bonus</dt><dd th:text="'+' + ${sheet.derivedValues().proficiencyBonus()}">+3</dd></div>
                    <div><dt>Species</dt><dd th:text="${sheet.speciesName() != null ? sheet.speciesName() : '—'}">Human</dd></div>
                    <div><dt>Background</dt><dd th:text="${sheet.backgroundName() != null ? sheet.backgroundName() : '—'}">Soldier</dd></div>
                    <div th:if="${levelingMode == null or levelingMode == 'XP'}"><dt>XP</dt><dd th:text="${sheet.xp()}">6500</dd></div>
                    <div th:if="${levelingMode == 'MILESTONE'}"><dt>Mode</dt><dd>Milestone</dd></div>
                </dl>
            </div>
```

Then, mechanically:
1. Give every existing `detail-section` its anchor id (`id="sheet-abilities"`, `id="sheet-combat"`, `id="sheet-live"`, `id="sheet-saves"`, `id="sheet-skills"`, `id="sheet-passives"`, `id="sheet-spellcasting"`, `id="sheet-attacks"`, `id="sheet-features"`, `id="sheet-spells"`, `id="sheet-resources"`, `id="sheet-actions"`, `id="sheet-overrides"`, `id="sheet-proficiencies"`; add `id="sheet-inventory"` on the inventory `div th:replace` wrapper by wrapping it: `<div id="sheet-inventory" th:replace="...">`).
2. Add `class="stat-grid"` to every `dl class="pm-stats"` in this file (Saving Throws, Passive Scores, hit-dice block): `<dl class="pm-stats stat-grid">`. For the plain `<dl>` in Spellcasting use `<dl class="stat-grid">` with each `dt`+`dd` pair wrapped in a `<div>` (same shape as Overview above).
3. Close the two new wrapper divs (`</div></div>` for `.sheet-layout` content column and grid) immediately before `<th:block th:unless="${hasSheet}">`.
4. While here, fix the pre-existing double-close at the bottom of the file: the template currently has two `</main></div>` pairs (lines 260-261 and 264-265) — remove the duplicate pair so the document closes each element once.

- [ ] **Step 4: Convert `_derived-stats.html` to tiles**

```html
<div th:fragment="derivedStats(sheet)" class="sheet-derived-stats">
    <dl class="stat-grid stat-grid--tiles">
        <div><dt>AC</dt><dd th:text="${sheet.derivedValues().armorClass()}">16</dd></div>
        <div><dt>HP</dt><dd th:text="${sheet.derivedValues().maxHp()}">52</dd></div>
        <div><dt>Init</dt><dd><span th:text="'+' + ${sheet.derivedValues().initiativeBonus()}">+2</span> <button class="roll-btn" th:attr="data-roll='d20+${sheet.derivedValues().initiativeBonus()}'" type="button" title="Initiative">d20</button></dd></div>
        <div><dt>Speed</dt><dd th:text="${sheet.derivedValues().speed() + ' ft.'}">30 ft.</dd></div>
    </dl>
</div>
```

- [ ] **Step 5: Run tests**

Run: `./mvnw test -Dtest=SheetTemplateContractTest && ./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/sheet/detail.html src/main/resources/templates/sheet/_derived-stats.html src/test/java/dev/hendrikhoemberg/dmhelper/sheet/SheetTemplateContractTest.java
git commit -m "feat(sheet): two-column layout with section nav, stat grids and hero tiles"
```

---

### Task 11: Live state — condition chips and death-save pips replace raw JSON

**Files:**
- Modify: `src/main/resources/templates/sheet/_live-state.html`
- Test: extend `src/test/java/dev/hendrikhoemberg/dmhelper/sheet/SheetTemplateContractTest.java`

**Interfaces:**
- Consumes: existing PUT `/api/v1/campaigns/{cid}/party/{mid}/live-state` accepting `conditionsJson` (string), `deathSaveSuccesses`/`deathSaveFailures` (int) — unchanged.
- Produces: `.condition-picker` chips writing a JSON array string; death saves as 3+3 checkbox pips.

- [ ] **Step 1: Add failing assertions to `SheetTemplateContractTest`**

```java
    @Test
    void liveStateHasConditionChipsNotRawJson() throws IOException {
        String live = Files.readString(Path.of("src/main/resources/templates/sheet/_live-state.html"));
        assertThat(live).contains("condition-picker");
        assertThat(live).contains("death-saves");
        assertThat(live).doesNotContain("Conditions (JSON)");
    }
```

Run: `./mvnw test -Dtest=SheetTemplateContractTest` — expected: new test FAILS.

- [ ] **Step 2: Replace the Death Saves and Conditions blocks in `_live-state.html`**

Replace:

```html
            <div>
                <dt>Death Saves</dt>
                <dd>
                    S <input id="ls-dss" type="number" min="0" max="3" class="small-input"
                             th:value="${member.deathSaveSuccesses}">
                    F <input id="ls-dsf" type="number" min="0" max="3" class="small-input"
                             th:value="${member.deathSaveFailures}">
                </dd>
            </div>
```

with:

```html
            <div>
                <dt>Death Saves</dt>
                <dd class="death-saves">
                    <span class="pip-group" title="Successes">
                        <input type="checkbox" class="ls-dss" th:each="i : ${#numbers.sequence(1,3)}"
                               th:checked="${member.deathSaveSuccesses >= i}"
                               th:aria-label="'Death save success ' + ${i}">
                    </span>
                    /
                    <span class="pip-group" title="Failures">
                        <input type="checkbox" class="ls-dsf" th:each="i : ${#numbers.sequence(1,3)}"
                               th:checked="${member.deathSaveFailures >= i}"
                               th:aria-label="'Death save failure ' + ${i}">
                    </span>
                </dd>
            </div>
```

Replace:

```html
            <div>
                <dt>Conditions (JSON)</dt>
                <dd>
                    <input id="ls-conditions" type="text" class="small-input" style="min-width: 16rem"
                           th:value="${member.conditionsJson}" placeholder="[]">
                </dd>
            </div>
```

with:

```html
            <div style="flex-basis: 100%">
                <dt>Conditions</dt>
                <dd class="condition-picker"
                    th:with="activeConditions=${member.conditionsJson != null ? member.conditionsJson : '[]'}">
                    <label class="condition-chip"
                           th:each="cond : ${T(java.util.List).of('Blinded','Charmed','Deafened','Frightened','Grappled','Incapacitated','Invisible','Paralyzed','Petrified','Poisoned','Prone','Restrained','Stunned','Unconscious')}">
                        <input type="checkbox" class="ls-condition" th:value="${cond}"
                               th:checked="${#strings.contains(activeConditions, '&quot;' + cond + '&quot;')}">
                        <span th:text="${cond}">Blinded</span>
                    </label>
                </dd>
            </div>
```

- [ ] **Step 3: Update the form's `hx-vals` to build the values from the new controls**

Replace the two lines inside the existing `hx-vals` expression:

```
              deathSaveSuccesses: parseInt(document.getElementById("ls-dss").value) || 0,
              deathSaveFailures: parseInt(document.getElementById("ls-dsf").value) || 0,
```
→
```
              deathSaveSuccesses: document.querySelectorAll(".ls-dss:checked").length,
              deathSaveFailures: document.querySelectorAll(".ls-dsf:checked").length,
```
and
```
              conditionsJson: document.getElementById("ls-conditions").value || null,
```
→
```
              conditionsJson: JSON.stringify(Array.from(document.querySelectorAll(".ls-condition:checked")).map(cb => cb.value)),
```

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest=SheetTemplateContractTest && ./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/sheet/_live-state.html src/test/java/dev/hendrikhoemberg/dmhelper/sheet/SheetTemplateContractTest.java
git commit -m "feat(sheet): condition chips and death-save pips replace raw JSON input"
```

---

### Task 12: Sheets overview + party cards use stat grids

**Files:**
- Modify: `src/main/resources/templates/sheet/overview.html` (stat block area, around line 64)
- Modify: `src/main/resources/templates/party/_card.html:22-24`

**Interfaces:** consumes `.stat-grid` from Task 9.

- [ ] **Step 1: Fix `sheet/overview.html` — the stats `dl` renders unstyled because `.pm-stats` is scoped to `.party-member-card` and these cards use `.card`**

Replace (around line 59):

```html
                <dl class="pm-stats">
                    <div><dt>AC</dt><dd th:text="${pm.ac}">16</dd></div>
                    <div><dt>HP</dt><dd th:text="${pm.maxHp}">38</dd></div>
                    <div><dt>Init</dt><dd th:text="'+' + ${pm.initiativeBonus}">+4</dd></div>
                    <div><dt>Speed</dt><dd th:text="${pm.speed + ' ft.'}">30 ft.</dd></div>
                    <div><dt>P. Perception</dt><dd th:text="${pm.passivePerception}">17</dd></div>
                </dl>
```

with:

```html
                <dl class="pm-stats stat-grid">
                    <div><dt>AC</dt><dd th:text="${pm.ac}">16</dd></div>
                    <div><dt>HP</dt><dd th:text="${pm.maxHp}">38</dd></div>
                    <div><dt>Init</dt><dd th:text="'+' + ${pm.initiativeBonus}">+4</dd></div>
                    <div><dt>Speed</dt><dd th:text="${pm.speed + ' ft.'}">30 ft.</dd></div>
                    <div><dt>Perception</dt><dd th:text="${pm.passivePerception}">17</dd></div>
                </dl>
```

- [ ] **Step 1b: Shorten the passive labels in `party/_card.html`** (stops the `P.`-orphan wrapping; the section already says these are passives). Replace lines 22-24:

```html
        <div><dt>P. Perception</dt><dd th:text="${pm.passivePerception}">17</dd></div>
        <div><dt>P. Insight</dt><dd th:text="${pm.passiveInsight}">12</dd></div>
        <div><dt>P. Investigation</dt><dd th:text="${pm.passiveInvestigation}">14</dd></div>
```

with:

```html
        <div><dt>Perception</dt><dd th:text="${pm.passivePerception}">17</dd></div>
        <div><dt>Insight</dt><dd th:text="${pm.passiveInsight}">12</dd></div>
        <div><dt>Investigation</dt><dd th:text="${pm.passiveInvestigation}">14</dd></div>
```

Also add `stat-grid` to that file's stats `dl` if it reads `<dl class="pm-stats">` (party roster cards keep their scoped styling too; the classes are compatible).

- [ ] **Step 2: Verify render**

Run: `./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/sheet/overview.html src/main/resources/templates/party/_card.html
git commit -m "feat(party): stat-grid layout for roster and sheet overview cards"
```

---

## Phase 3 — Consistency sweep

### Task 13: Page-header normalization (eyebrows, em dashes, action order)

One canonical pattern everywhere (see Global Constraints). Campaign-scoped pages get the campaign name as eyebrow. A new `CampaignModelAdvice` supplies `campaign` to the three controllers that don't add it (`EncounterController`, `GameMapController`, `AudioCueController`).

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CampaignModelAdvice.java`
- Modify: `src/main/resources/templates/party/list.html:5,15`
- Modify: `src/main/resources/templates/sheet/overview.html:5,15` (and its eyebrow line)
- Modify: `src/main/resources/templates/encounter/list.html`, `maps/list.html`, `notes/list.html`, `audio/list.html`, `treasury/list.html`, `world/npcs-list.html`, `world/locations-list.html`, `world/factions-list.html`, `quest/list.html`, `world/npcs-form.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/common/PageHeaderContractTest.java` (create)

**Interfaces:**
- Produces: model attribute `campaign` available on every `@Controller` route with a `campaignId` path variable.

- [ ] **Step 1: Write the failing contract test**

```java
package dev.hendrikhoemberg.dmhelper.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageHeaderContractTest {

    private static final List<String> CAMPAIGN_LIST_PAGES = List.of(
            "src/main/resources/templates/encounter/list.html",
            "src/main/resources/templates/maps/list.html",
            "src/main/resources/templates/notes/list.html",
            "src/main/resources/templates/audio/list.html",
            "src/main/resources/templates/party/list.html",
            "src/main/resources/templates/sheet/overview.html",
            "src/main/resources/templates/treasury/list.html",
            "src/main/resources/templates/world/npcs-list.html",
            "src/main/resources/templates/world/locations-list.html",
            "src/main/resources/templates/world/factions-list.html",
            "src/main/resources/templates/quest/list.html");

    @Test
    void everyCampaignPageUsesCampaignNameEyebrowAndGoldRule() throws IOException {
        for (String page : CAMPAIGN_LIST_PAGES) {
            String html = Files.readString(Path.of(page));
            assertThat(html).as("%s eyebrow", page)
                    .contains("page-header-eyebrow\" th:text=\"${campaign.name}\"");
            assertThat(html).as("%s gold rule", page)
                    .contains("rule-taper rule-taper--gold");
        }
    }

    @Test
    void noAsciiDoubleHyphenPseudoDashes() throws IOException {
        for (String page : CAMPAIGN_LIST_PAGES) {
            assertThat(Files.readString(Path.of(page)))
                    .as("%s must use — not --", page)
                    .doesNotContain(" -- ");
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=PageHeaderContractTest`
Expected: FAIL.

- [ ] **Step 3: Create `CampaignModelAdvice`**

```java
package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Every server-rendered page under /campaigns/{campaignId}/** can rely on
 * ${campaign} — page headers use the campaign name as the eyebrow line.
 * Idempotent with controllers that also add the attribute themselves.
 */
@ControllerAdvice(annotations = Controller.class)
public class CampaignModelAdvice {

    private final CampaignRepository campaigns;

    public CampaignModelAdvice(CampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    @ModelAttribute
    public void addCampaign(@PathVariable(name = "campaignId", required = false) UUID campaignId,
                            Model model) {
        if (campaignId == null) {
            return;
        }
        campaigns.findById(campaignId).ifPresent(c -> {
            model.addAttribute("campaign", c);
            model.addAttribute("campaignId", campaignId);
        });
    }
}
```

Note for `@WebMvcTest` slices: this advice is auto-scanned; slice tests for controllers under `/campaigns/{campaignId}` gain a `CampaignRepository` dependency — add `@MockitoBean CampaignRepository campaignRepository` to any existing slice test that starts failing with a missing-bean error.

- [ ] **Step 4: Normalize the headers**

Apply per file (pattern is identical; exact current snippets shown earlier in recon):

1. `encounter/list.html`, `maps/list.html`, `notes/list.html`, `audio/list.html`, `treasury/list.html`: change the eyebrow line from the static section name to
   ```html
   <div class="page-header-eyebrow" th:text="${campaign.name}">Campaign</div>
   ```
   In `audio/list.html` also swap the two actions so the Back link precedes `+ New Cue` (currently reversed), and set the title tag to `th:text="|${listTitle} — ${campaign.name}|"`.
   In `encounter/list.html` and `maps/list.html` set the titles to `th:text="|Encounters — ${campaign.name}|"` / `th:text="|Maps — ${campaign.name}|"`.
2. `party/list.html`: title line 5 → `th:text="|Party — ${campaign.name}|"`; h1 line 15 → `<h1>Party</h1>` with eyebrow `th:text="${campaign.name}"` (replace the current static eyebrow `Party`).
3. `sheet/overview.html`: title → `th:text="|Party Sheets — ${campaign.name}|"`; eyebrow → `th:text="${campaign.name}"`; h1 → `<h1>Party Sheets</h1>`.
4. `world/npcs-list.html`, `world/locations-list.html`, `world/factions-list.html`, `quest/list.html`: wrap the bare `<h1>` in the canonical structure:
   ```html
   <div class="page-header">
       <div>
           <div class="page-header-eyebrow" th:text="${campaign.name}">Campaign</div>
           <h1>NPCs</h1>
           <div class="rule-taper rule-taper--gold"></div>
       </div>
       <div class="page-header-actions">
           <a th:href="@{/campaigns/{id}(id=${campaignId})}" class="btn btn-ghost">&larr; Campaign</a>
           <!-- existing + New button stays after the back link -->
       </div>
   </div>
   ```
   (h1 text per page: NPCs / Locations / Factions / Quests; keep each page's existing `+ New` button markup, moved after the new back link.)
5. `world/npcs-form.html`: swap the form-actions order so Cancel precedes the primary button:
   ```html
   <div class="form-actions">
       <a class="btn btn-ghost"
          th:href="@{${npc.id != null}
              ? '/campaigns/{cid}/world/npcs/{nid}(cid=${campaignId},nid=${npc.id})'
              : '/campaigns/{cid}/world/npcs(cid=${campaignId})'}">Cancel</a>
       <button type="submit" class="btn btn-primary" th:text="${npc.id != null} ? 'Update' : 'Create'">Save</button>
   </div>
   ```

- [ ] **Step 5: Run tests**

Run: `./mvnw test -Dtest=PageHeaderContractTest && ./mvnw test -Dtest=FullPageRenderSmokeTest && ./mvnw test`
Expected: PASS (fix any slice tests per the Step 3 note).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/web/CampaignModelAdvice.java src/main/resources/templates src/test/java/dev/hendrikhoemberg/dmhelper/common/PageHeaderContractTest.java
git commit -m "feat(ui): canonical page headers with campaign eyebrow across all sections"
```

---

### Task 14: Global anchor styling + platform-aware shortcut hint + distinct header icons

**Files:**
- Modify: `src/main/resources/static/css/base.css`
- Modify: `src/main/resources/templates/fragments/navbar.html:10,14-15`

- [ ] **Step 1: Style bare anchors app-wide** (fixes the browser-blue "Linked Note →" on calendar and anywhere else an unclassed link appears). Append to `base.css`:

```css
/* Unclassed anchors inside content default to the accent, not browser blue. */
.app-main a:not([class]) {
    color: var(--color-accent-hover);
    text-decoration: none;
}
.app-main a:not([class]):hover {
    text-decoration: underline;
}
```

- [ ] **Step 2: Platform-aware palette hint + distinct tables icon**

In `navbar.html` replace line 10's `<kbd class="palette-hint__key">⌘K</kbd>` with `<kbd class="palette-hint__key" id="paletteHintKey">⌘K</kbd>`, and replace the tables link (lines 14-15):

```html
        <a href="/library/tables" class="btn btn-ghost u-text-sm"
           title="Rollable Tables">&#x1F3B2;</a>
```

with:

```html
        <a href="/library/tables" class="btn btn-ghost u-text-sm"
           title="Rollable Tables" aria-label="Rollable Tables">&#x1F4DC;</a>
```

(scroll glyph `📜` — the dice glyph now belongs solely to the dice roller). Then add, inside the existing inline `<script>` block that wires the DM-mode checkbox (before its closing `})();`):

```js
            if (!/Mac|iP(hone|ad|od)/.test(navigator.platform)) {
                const kbd = document.getElementById('paletteHintKey');
                if (kbd) kbd.textContent = 'Ctrl K';
            }
```

- [ ] **Step 3: Verify render + eyeball**

Run: `./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS. Optionally boot the app and confirm the header shows `Ctrl K` and 📜.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/base.css src/main/resources/templates/fragments/navbar.html
git commit -m "feat(ui): accent-colored bare links, platform-aware shortcut hint, distinct tables icon"
```

---

### Task 15: Library — long-name overflow fix and "Show more" pagination

**Files:**
- Modify: `src/main/resources/static/css/components.css:329-335` (`.library-card__title`)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java:70-85`
- Modify: `src/main/resources/templates/library/_card.html:44-64`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryShowMoreTest.java` (create)

**Interfaces:**
- Consumes: `LibraryService.search(ContentSource, String cr, String type, String search)` — existing.
- Produces: `GET /library/statblocks?limit=N` (default 60) rendering N cards and a Show-more button that requests `limit=N+120` targeting `#statblock-results`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.library.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryShowMoreTest {

    @Test
    void cardListCapComesFromModelAndOffersShowMore() throws IOException {
        String tpl = Files.readString(Path.of("src/main/resources/templates/library/_card.html"));
        assertThat(tpl).doesNotContain("th:with=\"cap=60\"");
        assertThat(tpl).contains("id=\"statblock-results\"");
        assertThat(tpl).contains("Show ");
        assertThat(tpl).contains("hx-get");
    }

    @Test
    void titleWrapsInsteadOfOverlappingCrBadge() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/static/css/components.css"));
        assertThat(css).contains("library-card__title");
        assertThat(css).contains("overflow-wrap: anywhere");
    }
}
```

Run: `./mvnw test -Dtest=LibraryShowMoreTest` — expected: FAIL.

- [ ] **Step 2: CSS — let long names wrap**

In `.library-card__title` (components.css:329) add one declaration:

```css
.library-card__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 700;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
```

- [ ] **Step 3: Controller — accept and echo `limit` + filters**

Replace the `search` handler:

```java
    @GetMapping("/statblocks")
    public String search(@RequestParam(required = false) String search,
                         @RequestParam(required = false) String cr,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) String source,
                         @RequestParam(required = false, defaultValue = "60") int limit,
                         Model model) {
        ContentSource sourceEnum = null;
        if (source != null && !source.isBlank()) {
            sourceEnum = ContentSource.valueOf(source);
        }
        List<StatBlock> results = service.search(sourceEnum, cr, type, search);
        model.addAttribute("statblocks", results);
        // When the source filter is on, every badge on screen says the same thing.
        model.addAttribute("sourceFiltered", sourceEnum != null);
        model.addAttribute("cap", Math.max(1, limit));
        model.addAttribute("filterSearch", search == null ? "" : search);
        model.addAttribute("filterCr", cr == null ? "" : cr);
        model.addAttribute("filterType", type == null ? "" : type);
        model.addAttribute("filterSource", source == null ? "" : source);
        return "library/_card :: card-list";
    }
```

- [ ] **Step 4: Template — model-driven cap and Show-more button**

Replace the `card-list` and `more-results` fragments in `library/_card.html`:

```html
<div th:fragment="card-list(statblocks)" th:if="${statblocks != null}" class="settle-in"
     id="statblock-results"
     th:with="cap=${cap != null ? cap : 60}">
    <div class="result-count"
         th:text="${statblocks.size()} + (${statblocks.size()} == 1 ? ' monster' : ' monsters')">312 monsters</div>
    <div class="card-grid card-grid--library"
         th:classappend="${sourceFiltered} ? 'card-grid--source-filtered'">
        <th:block th:if="${statblocks.isEmpty()}">
            <th:block th:replace="~{common/_empty-state :: empty-state('Nothing in the bestiary matches that.', null, null, 'Try a broader search, or clear a filter.')}"></th:block>
        </th:block>
        <!-- Cap the rendered cards; the full SRD bestiary is hundreds of entries. -->
        <th:block th:each="sb,iter : ${statblocks}" th:if="${iter.index < cap}">
            <th:block th:replace="~{library/_card :: card(sb=${sb})}"></th:block>
        </th:block>
    </div>
    <div th:if="${statblocks.size() > cap}" class="result-truncated text-muted u-text-sm u-mt-md">
        Showing the first <span th:text="${cap}">60</span> of <span th:text="${statblocks.size()}">312</span>.
        <button class="btn btn-ghost btn-sm"
                th:hx-get="@{/library/statblocks(search=${filterSearch},cr=${filterCr},type=${filterType},source=${filterSource},limit=${cap + 120})}"
                hx-target="#statblock-results" hx-swap="outerHTML"
                th:text="'Show ' + ${T(java.lang.Math).min(120, statblocks.size() - cap)} + ' more'">Show 120 more</button>
    </div>
</div>
```

(The old standalone `more-results` fragment is deleted; grep for other usages first: `grep -rn "more-results" src/main/resources/templates/` — if another template references it, keep the fragment but leave it unused by `card-list`.)

- [ ] **Step 5: Run tests**

Run: `./mvnw test -Dtest=LibraryShowMoreTest && ./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/css/components.css src/main/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryController.java src/main/resources/templates/library/_card.html src/test/java/dev/hendrikhoemberg/dmhelper/library/web/LibraryShowMoreTest.java
git commit -m "feat(library): show-more pagination and long-name wrap on bestiary cards"
```

---

### Task 16: In-game date formatting — one formatter, used by the cockpit

The cockpit header shows `1492-4-15` (and wraps mid-date); the calendar page shows "15 April 1492". Add `CalendarService.formatDate` and use it in the cockpit topbar.

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarService.java`
- Modify: `src/main/resources/templates/session/cockpit.html:40`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/calendar/CalendarFormatDateTest.java` (create)

**Interfaces:**
- Consumes: `CalendarService.getCalendarConfig(UUID)` → `CalendarConfig(int[] monthLengths, String[] monthNames, String[] weekdayNames)`; `InGameDate(int year, int month, int day)`.
- Produces: `public String formatDate(UUID campaignId, InGameDate date)` → e.g. `"15 April 1492"`.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.calendar;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CalendarFormatDateTest {

    @Autowired private CalendarService calendarService;
    @Autowired private CampaignRepository campaignRepository;

    @Test
    void formatsDayMonthNameYearUsingCampaignCalendar() {
        Campaign c = new Campaign();
        c.setName("Cal");
        UUID id = campaignRepository.save(c).getId();

        String formatted = calendarService.formatDate(id,
                new CalendarService.InGameDate(1492, 3, 15)); // month index 3 = April by default config

        assertThat(formatted).isEqualTo("15 April 1492");
    }
}
```

Run: `./mvnw test -Dtest=CalendarFormatDateTest` — expected: compile FAILURE (`formatDate` missing).

- [ ] **Step 2: Implement `formatDate` in `CalendarService`**

```java
    /** "15 April 1492" using the campaign's month names; falls back to "Month N". */
    public String formatDate(UUID campaignId, InGameDate date) {
        CalendarConfig config = getCalendarConfig(campaignId);
        String month = config.monthNames() != null && config.monthNames().length > date.month()
                ? config.monthNames()[date.month()]
                : "Month " + (date.month() + 1);
        return date.day() + " " + month + " " + date.year();
    }
```

- [ ] **Step 3: Use it in the cockpit topbar**

In `session/cockpit.html` replace line 40:

```html
    <span th:text="${workspace.currentDate.year + '-' + (workspace.currentDate.month + 1) + '-' + workspace.currentDate.day}">1492-7-12</span>
```

with:

```html
    <span class="u-nowrap" th:text="${@calendarService.formatDate(campaignId, workspace.currentDate)}">12 July 1492</span>
```

(`@calendarService` is the Spring bean reference; the bean name is the default for class `CalendarService`.) If `u-nowrap` does not exist in the utility CSS (`grep -n "u-nowrap" src/main/resources/static/css/*.css`), add to `base.css`: `.u-nowrap { white-space: nowrap; }`.

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest=CalendarFormatDateTest && ./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/calendar/service/CalendarService.java src/main/resources/templates/session/cockpit.html src/main/resources/static/css/base.css src/test/java/dev/hendrikhoemberg/dmhelper/calendar/CalendarFormatDateTest.java
git commit -m "feat(calendar): shared in-game date formatter, used by session cockpit"
```

---

### Task 17: Dashboard cleanup — primary actions up front, data tools tucked away

The dashboard's top strip mixes Run Session with export/import plumbing and a red Delete button. Keep the two primary actions; move the rest into a collapsed disclosure at the page bottom.

**Files:**
- Modify: `src/main/resources/templates/campaigns/detail.html:20-64` and after the `dash-grid`
- Modify: `src/main/resources/static/css/components.css` (append)

- [ ] **Step 1: Reduce `dash-actions` to the primaries**

Replace lines 20-64 (`<div class="dash-actions u-mb-lg">` … `</div>` that closes it) with:

```html
            <div class="dash-actions u-mb-lg">
                <a class="btn btn-primary" th:href="@{/campaigns/{id}/session(id=${campaign.id})}">Run Session</a>
                <a class="btn" th:href="@{/campaigns/{id}/adventures(id=${campaign.id})}">Plan Adventures</a>
            </div>
```

- [ ] **Step 2: Add the disclosure after `</div>` closing `dash-grid` (before `</main>`)**

```html
            <details class="dash-data-tools u-mt-lg">
                <summary>Data &amp; import/export</summary>
                <div class="dash-data-tools__body">
                    <div class="export-package-group">
                        <form th:action="@{/campaigns/{id}/package(id=${campaign.id})}"
                              method="GET">
                            <input type="hidden" name="includeCombatLog" value="true" id="includeCombatLog">
                            <input type="hidden" name="includeDiceHistory" value="true" id="includeDiceHistory">
                            <label class="checkbox-label">
                                <input type="checkbox" checked
                                       onchange="document.getElementById('includeCombatLog').value=this.checked">
                                Combat Log
                            </label>
                            <label class="checkbox-label">
                                <input type="checkbox" checked
                                       onchange="document.getElementById('includeDiceHistory').value=this.checked">
                                Dice History
                            </label>
                            <button type="submit" class="btn">Export Campaign Package</button>
                        </form>
                    </div>
                    <a th:href="@{/campaigns/{id}/export(id=${campaign.id})}"
                       class="btn" download>Export Legacy v1 JSON</a>
                    <button class="btn"
                            onclick="Alpine.$data(document.querySelector('[x-data=\'campaignImport\']')).openDialog()">
                        Import Campaign Package
                    </button>
                    <form hx-post="/campaigns/import"
                          hx-encoding="multipart/form-data"
                          class="u-contents">
                        <label class="btn">
                            Import Legacy v1 JSON
                            <input type="file" name="file" accept=".dmcampaign.json,application/json"
                                   onchange="this.form.requestSubmit()"
                                   class="hidden">
                        </label>
                    </form>
                    <button class="btn btn-danger"
                            th:hx-delete="@{/campaigns/{id}(id=${campaign.id})}"
                            hx-confirm="Delete this campaign?"
                            hx-target="body"
                            hx-swap="outerHTML">
                        Delete Campaign
                    </button>
                </div>
            </details>
```

- [ ] **Step 3: Style the disclosure** (append to `components.css`):

```css
.dash-data-tools {
    border: 1px solid var(--color-border);
    border-radius: var(--radius);
    padding: var(--space-sm) var(--space-md);
}
.dash-data-tools summary {
    cursor: pointer;
    color: var(--color-text-muted);
    font-size: var(--text-sm);
    text-transform: uppercase;
    letter-spacing: 0.5px;
}
.dash-data-tools__body {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-sm);
    padding-top: var(--space-md);
}
.dash-data-tools .btn-danger { margin-left: auto; }
```

- [ ] **Step 4: Verify render + full suite** (the export/import forms moved — any test asserting their presence on the page still finds them):

Run: `./mvnw test -Dtest=FullPageRenderSmokeTest && ./mvnw test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/campaigns/detail.html src/main/resources/static/css/components.css
git commit -m "feat(dashboard): tuck import/export and delete into a data-tools disclosure"
```

---

### Task 18: Small-fix sweep — treasury controls, notes tag chips, editor status bar, quest header spacing

**Files:**
- Modify: `src/main/resources/templates/treasury/_card.html:20-33`
- Modify: `src/main/resources/templates/notes/detail.html:29-31`
- Modify: `src/main/resources/templates/maps/editor.html:400-409` (+ its `.editor-statusbar` inline CSS near line 86)

- [ ] **Step 1: Treasury card — self-explaining controls**

In `treasury/_card.html` replace the actions block:

```html
        <form th:method="'put'"
              th:hx-put="@{/campaigns/{cid}/treasury/{id}/attune(cid=${assignment.campaignId()}, id=${assignment.id()})}"
              hx-target="closest .detail-section" hx-swap="outerHTML" class="u-inline">
            <button class="btn btn-ghost btn-xs">Attune</button>
        </form>
        <form th:method="'put'"
              th:hx-put="@{/campaigns/{cid}/treasury/{id}/quantity(cid=${assignment.campaignId()}, id=${assignment.id()})}"
              hx-target="closest .card" hx-swap="outerHTML" class="u-flex u-gap-xs">
            <input type="number" name="quantity" th:value="${assignment.quantity()}" min="1"
                   class="qty-input" />
            <button class="btn btn-ghost btn-xs">Qty</button>
        </form>
```

with:

```html
        <form th:method="'put'"
              th:hx-put="@{/campaigns/{cid}/treasury/{id}/attune(cid=${assignment.campaignId()}, id=${assignment.id()})}"
              hx-target="closest .detail-section" hx-swap="outerHTML" class="u-inline">
            <button class="btn btn-ghost btn-xs"
                    th:text="${assignment.attuned()} ? 'Unattune' : 'Attune'">Attune</button>
        </form>
        <form th:method="'put'"
              th:hx-put="@{/campaigns/{cid}/treasury/{id}/quantity(cid=${assignment.campaignId()}, id=${assignment.id()})}"
              hx-target="closest .card" hx-swap="outerHTML" class="u-flex u-gap-xs">
            <label class="u-flex u-gap-xs" style="align-items: center;">
                <span class="text-muted u-text-xs">Qty</span>
                <input type="number" name="quantity" th:value="${assignment.quantity()}" min="1"
                       class="qty-input" aria-label="Quantity" />
            </label>
            <button class="btn btn-ghost btn-xs">Set</button>
        </form>
```

- [ ] **Step 2: Notes tags as chips**

In `notes/detail.html` replace:

```html
                    <span th:if="${note.tags != null and !note.tags.isBlank()}"
```
…through its closing (the `'Tags: ' + ${note.tags}` span) with:

```html
                    <th:block th:if="${note.tags != null and !note.tags.isBlank()}">
                        <span class="badge" th:each="tag : ${note.tags.split(',')}"
                              th:text="${#strings.trim(tag)}">tag</span>
                    </th:block>
```

- [ ] **Step 3: Map editor status bar**

In `maps/editor.html` delete the stray empty bar (lines 402-403):

```html
        <div class="editor-statusbar">

```
(the one with no children — keep the populated one), and in the page's inline `<style>` for `.editor-statusbar` (around line 86) ensure it lays out with separation — replace the existing `.editor-statusbar { ... }` rule's layout properties so it contains:

```css
        .editor-statusbar {
            display: flex;
            align-items: center;
            gap: var(--space-md);
        }
        .editor-statusbar #saveIndicator { margin-left: auto; }
```

(keep any existing color/background declarations in that rule).

- [ ] **Step 4: Verify render**

Run: `./mvnw test -Dtest=FullPageRenderSmokeTest`
Expected: PASS. Boot the app briefly and confirm the editor status bar reads `Ready … Saved` with spacing.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/treasury/_card.html src/main/resources/templates/notes/detail.html src/main/resources/templates/maps/editor.html
git commit -m "fix(ui): treasury control labels, note tag chips, editor status bar spacing"
```

---

## Phase 4 — Verification

### Task 19: Full-suite run and visual walkthrough

**Files:** none (verification only).

- [ ] **Step 1: Full test suite**

Run: `./mvnw test`
Expected: PASS, zero failures.

- [ ] **Step 2: Boot and walk the app**

```bash
./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--dmhelper.open-browser=false" &
timeout 60 bash -c 'until curl -sf http://localhost:8081/ >/dev/null; do sleep 2; done'
```

Walk (browser or headless driver), checking each item from the evaluation:

1. Campaign → Adventures → open the adventure → open a scene: full pages, no truncation, no 500.
2. Adventures page → Resume: loads the scene page.
3. NPCs / Locations / Factions / Quests: **no** red banner.
4. `http://localhost:8081/nope`: styled "wandered off the map" page.
5. Toggle DM Mode off: PIN vanishes, Session Plan and Recent Notes cards vanish, PLAYER-SAFE chip shows.
6. Character sheet: two columns, section nav, stat tiles, ability tiles in one row, condition chips, death-save pips; no "Conditions (JSON)".
7. Session cockpit: date reads "15 April 1492" style; with a running session and no stored map, the current scene's map loads.
8. Map editor + map play: browser console free of uncaught errors.
9. Library: search "ankylosaurus" — name wraps, CR badge clear; clear filters — "Show 120 more" appends more cards.
10. Header: `Ctrl K` hint (on Linux), one dice glyph + one scroll glyph.
11. Dashboard: two primary actions; data tools collapsed; delete inside disclosure.
12. Party / Sheets / Encounters / Maps / Notes / Music / Treasury: eyebrow = campaign name, em dashes, back-then-primary action order.

Stop the server: `lsof -ti:8081 -sTCP:LISTEN | xargs -r kill`

- [ ] **Step 3: Update the evaluation record**

Append a short "fixed in" note to `docs/superpowers/specs/` if the project keeps one for this evaluation; otherwise skip.

- [ ] **Step 4: Final commit (only if Step 3 changed files)**

```bash
git add -A && git commit -m "docs: record UI polish verification walkthrough"
```

---

## Self-Review Notes

- **Coverage vs. evaluation:** adventure truncation (T2), scene 500 (T3), phantom banner (T4), JSON 404 (T5), PIN + dashboard leak (T6), workspace map selection (T7), map JS crashes (T8), sheet layout + JSON conditions (T9-T11), roster/sheet cards (T12), header/em-dash/action-order/eyebrow drift (T13), blue links + ⌘K + dice-icon ambiguity (T14), library overlap + 60-cap (T15), date formats (T16), dashboard action strip (T17), treasury controls + note tags + "ReadySaved" (T18). Not addressed (accepted): handout black thumbnails (fixture data, not UI), encounter card stale "Round 3" copy, mobile nav collapse (player view is the mobile surface).
- **Type consistency:** `AdventureService.SceneDetailView(Scene, Map<UUID, ThreatCardView>)` is defined in T3 and consumed only there; `CalendarService.formatDate(UUID, InGameDate)` defined T16 step 2, consumed step 3; `.stat-grid` defined T9, consumed T10/T12; `cap`/`filter*` model attrs defined T15 step 3, consumed step 4.
- **Known API-verification points** (flagged inline where the exact signature was not confirmed during planning): `SessionLifecycleService` start method and `AdventureService` current-scene setter (T7 step 1), `GameMap` required fields (T7), `CampaignController` slice mocks (T5), battle-map.js shape rendering presence (T8 step 2). Each has a grep command in its step.
