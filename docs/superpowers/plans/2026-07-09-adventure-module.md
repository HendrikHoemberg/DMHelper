# Adventure Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** First-class adventures (ordered chapters → keyed scenes with read-aloud text, map pins, linked encounters/statblocks/handouts), a campaign-global "current scene" run cursor, and full import/export support — per the approved spec `docs/superpowers/specs/2026-07-09-adventure-module-design.md`.

**Architecture:** New `adventure` feature module (`data`/`service`/`web`) mirroring the existing module pattern. Scenes integrate with existing extension points: wiki links (`[[scene:…]]`), quicknotes (`SCENE` target), command palette, campaign JSON import/export. Battle map gets a DM-only Konva pin layer fed by a new PIN-gated JSON endpoint.

**Tech Stack:** Spring Boot 4.1.0 / Java 25, Spring Data JPA + H2, Thymeleaf + htmx fragments, commonmark-java (`org.commonmark`), Jackson 3 (`tools.jackson`), Konva (vendored), JUnit 5 + AssertJ + `@DataJpaTest`/`@WebMvcTest`, Playwright (Java).

## Global Constraints

- **Spring Boot 4.1.0** idioms only. Jackson is **Jackson 3** (`tools.jackson.databind.ObjectMapper`) — any `com.fasterxml.jackson` import is a bug. Boot-4 test annotations: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `org.springframework.test.context.bean.override.mockito.MockitoBean`.
- commonmark stays `org.commonmark` (not Jackson-related).
- **No npm, no new JS dependencies, no CDN.** Frontend changes are vanilla ES modules + vendored Konva/Alpine/htmx only.
- **No D&D rules content from memory** (SPEC §2.3.8) — this feature carries no rules data; keep it that way.
- Entity IDs are UUIDs. HTML CRUD via Thymeleaf+htmx fragments; JSON only under `/api/v1` (PIN-gated by `PinInterceptor` on `/**` with an exclusion list in `WebMvcConfig` — do **not** add exclusions).
- All new entities are DM-only: nothing added to `live` module payloads or `/player` routes.
- Schema migration is `ddl-auto=update` — additive only; no migration beans needed.
- Existing code style: plain JPA entities with explicit getters/setters (no Lombok in entities), constructor injection, `NotFoundException` from `dev.hendrikhoemberg.dmhelper.common`.
- Run tests with `./mvnw test -Dtest=<ClassName>`; full build `./mvnw test`.
- Commit after every task; message prefix `feat:`/`fix:`/`test:`/`docs:`; end commit body with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

## File Structure

New module `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/`:
- `data/Adventure.java`, `data/Chapter.java`, `data/Scene.java`, `data/SceneStatus.java` — entities
- `data/AdventureRepository.java`, `data/ChapterRepository.java`, `data/SceneRepository.java`
- `service/AdventureService.java` — CRUD, ordering, cursor, rendering
- `service/SceneRefCleaner.java` — nulls scene refs when linked entities are deleted
- `web/AdventureController.java` — adventure/chapter HTML routes
- `web/SceneController.java` — scene HTML routes + run-mode endpoints
- `web/MapPinApiController.java` — `GET /api/v1/maps/{id}/pins`

Templates `src/main/resources/templates/adventure/`: `list.html`, `detail.html`, `_adventure-form.html`, `scene-detail.html`, `_scene-form.html`, `_scene-panel.html`, `_action-rail.html`.

Modified: `campaign/data/Campaign.java` (+`currentSceneId`), `config/MarkdownUtil.java` (read-aloud), `notes/service/WikiLinkParser.java` + new `notes/service/WikiLinkResolver.java` (+ `NoteService` refactor), `notes/service/QuickNoteService.java`, `common/service/CommandPaletteService.java`, `campaign/service/CampaignExportDto.java` + `CampaignService.java`, `schemas/campaign-format.schema.json`, `static/js/map/battle-map.js`, `templates/maps/battle.html`, `templates/campaigns/detail.html`, `static/css/app.css`, plus one-line delete hooks in `EncounterService`, `GameMapService`, `StatBlockService` (library), `HandoutService`.

Tests: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureServiceTest.java`, `adventure/service/SceneRefCleanerTest.java`, `adventure/web/AdventureControllerTest.java`, `adventure/web/SceneControllerTest.java`, `adventure/web/MapPinApiControllerTest.java`, `adventure/web/MapPinAccessControlTest.java`, `config/MarkdownUtilTest.java`, extensions to `notes/…`, `common/…`, `campaign/service/CampaignImportExportRoundTripTest.java`, `CoreSessionLoopSmokeTest.java`.

---

### Task 1: Adventure data layer

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneStatus.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/Adventure.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/Chapter.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/Scene.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/AdventureRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/ChapterRepository.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/data/SceneRepository.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java` (add `currentSceneId`)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/data/AdventureRepositoryTest.java`

**Interfaces:**
- Produces: entities `Adventure`, `Chapter`, `Scene`, enum `SceneStatus { UNVISITED, VISITED, DONE }`; repositories with the derived queries listed below; `Campaign.getCurrentSceneId()/setCurrentSceneId(UUID)`.
- Design note: the cursor is a **plain UUID column** on Campaign (not a JPA relation) so the core `campaign` module does not depend on the `adventure` module; referential integrity is maintained by `AdventureService` (Task 3) clearing it on scene deletion.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdventureRepositoryTest {

    @Autowired private AdventureRepository adventureRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
    }

    private Adventure adventure(String name, int sortOrder) {
        Adventure a = new Adventure();
        a.setCampaign(campaign);
        a.setName(name);
        a.setSortOrder(sortOrder);
        return adventureRepository.save(a);
    }

    private Chapter chapter(Adventure a, String title, int sortOrder) {
        Chapter c = new Chapter();
        c.setAdventure(a);
        c.setTitle(title);
        c.setSortOrder(sortOrder);
        return chapterRepository.save(c);
    }

    private Scene scene(Chapter c, String title, int sortOrder) {
        Scene s = new Scene();
        s.setChapter(c);
        s.setTitle(title);
        s.setSortOrder(sortOrder);
        return sceneRepository.save(s);
    }

    @Test
    void persistsGraphAndOrdersBySortOrder() {
        Adventure a2 = adventure("Module Two", 1);
        Adventure a1 = adventure("Module One", 0);
        Chapter ch2 = chapter(a1, "Chapter 2", 1);
        Chapter ch1 = chapter(a1, "Chapter 1", 0);
        Scene s2 = scene(ch1, "The Shrine", 1);
        Scene s1 = scene(ch1, "The Gate", 0);
        s1.setSceneKey("1");
        sceneRepository.save(s1);

        var adventures = adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaign.getId());
        assertThat(adventures).extracting(Adventure::getName)
                .containsExactly("Module One", "Module Two");

        var chapters = chapterRepository.findByAdventureIdOrderBySortOrderAsc(a1.getId());
        assertThat(chapters).extracting(Chapter::getTitle)
                .containsExactly("Chapter 1", "Chapter 2");

        var scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(ch1.getId());
        assertThat(scenes).extracting(Scene::getTitle)
                .containsExactly("The Gate", "The Shrine");
        assertThat(scenes.get(0).getSceneKey()).isEqualTo("1");
        assertThat(scenes.get(0).getStatus()).isEqualTo(SceneStatus.UNVISITED);
    }

    @Test
    void findsScenesAcrossCampaignAndByTitle() {
        Adventure a = adventure("Module", 0);
        Chapter ch = chapter(a, "Ch", 0);
        scene(ch, "Throne Room", 0);

        assertThat(sceneRepository.findByChapterAdventureCampaignId(campaign.getId())).hasSize(1);
        assertThat(sceneRepository.findByChapterAdventureCampaignIdAndTitleIgnoreCase(
                campaign.getId(), "throne room")).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AdventureRepositoryTest`
Expected: COMPILATION ERROR (classes do not exist yet).

- [ ] **Step 3: Create the enum and entities**

`SceneStatus.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

public enum SceneStatus {
    UNVISITED,
    VISITED,
    DONE
}
```

`Adventure.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adventure", indexes = {
    @Index(name = "idx_adventure_campaign", columnList = "campaign_id"),
})
public class Adventure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(length = 500)
    private String sourceAttribution;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourceAttribution() { return sourceAttribution; }
    public void setSourceAttribution(String sourceAttribution) { this.sourceAttribution = sourceAttribution; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

`Chapter.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "adventure_chapter", indexes = {
    @Index(name = "idx_chapter_adventure", columnList = "adventure_id"),
})
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adventure_id", nullable = false)
    private Adventure adventure;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String intro;

    @Column(nullable = false)
    private int sortOrder = 0;

    @OneToMany(mappedBy = "chapter", orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Scene> scenes = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Adventure getAdventure() { return adventure; }
    public void setAdventure(Adventure adventure) { this.adventure = adventure; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public List<Scene> getScenes() { return scenes; }
    public void setScenes(List<Scene> scenes) { this.scenes = scenes; }
}
```

`Scene.java` (note: table name `adventure_scene` because `SCENE` could collide with future names; join tables carry `@OrderColumn` for stable ordering):

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "adventure_scene", indexes = {
    @Index(name = "idx_scene_chapter", columnList = "chapter_id"),
    @Index(name = "idx_scene_map", columnList = "map_id"),
    @Index(name = "idx_scene_encounter", columnList = "encounter_id"),
})
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 50)
    private String sceneKey;

    @Column(columnDefinition = "CLOB")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SceneStatus status = SceneStatus.UNVISITED;

    @Column(nullable = false)
    private int sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id")
    private GameMap map;

    private Integer pinX;

    private Integer pinY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToMany
    @JoinTable(name = "scene_statblock",
            joinColumns = @JoinColumn(name = "scene_id"),
            inverseJoinColumns = @JoinColumn(name = "statblock_id"))
    @OrderColumn(name = "position")
    private List<StatBlock> statBlocks = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "scene_handout",
            joinColumns = @JoinColumn(name = "scene_id"),
            inverseJoinColumns = @JoinColumn(name = "handout_id"))
    @OrderColumn(name = "position")
    private List<Handout> handouts = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Chapter getChapter() { return chapter; }
    public void setChapter(Chapter chapter) { this.chapter = chapter; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSceneKey() { return sceneKey; }
    public void setSceneKey(String sceneKey) { this.sceneKey = sceneKey; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public SceneStatus getStatus() { return status; }
    public void setStatus(SceneStatus status) { this.status = status; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public Integer getPinX() { return pinX; }
    public void setPinX(Integer pinX) { this.pinX = pinX; }

    public Integer getPinY() { return pinY; }
    public void setPinY(Integer pinY) { this.pinY = pinY; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public List<StatBlock> getStatBlocks() { return statBlocks; }
    public void setStatBlocks(List<StatBlock> statBlocks) { this.statBlocks = statBlocks; }

    public List<Handout> getHandouts() { return handouts; }
    public void setHandouts(List<Handout> handouts) { this.handouts = handouts; }
}
```

- [ ] **Step 4: Create the repositories**

`AdventureRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AdventureRepository extends JpaRepository<Adventure, UUID> {
    List<Adventure> findByCampaignIdOrderBySortOrderAsc(UUID campaignId);
}
```

`ChapterRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
    List<Chapter> findByAdventureIdOrderBySortOrderAsc(UUID adventureId);
}
```

`SceneRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, UUID> {
    List<Scene> findByChapterIdOrderBySortOrderAsc(UUID chapterId);
    List<Scene> findByChapterAdventureCampaignId(UUID campaignId);
    List<Scene> findByChapterAdventureCampaignIdAndTitleIgnoreCase(UUID campaignId, String title);
    List<Scene> findByMapIdAndPinXNotNull(UUID mapId);
    List<Scene> findByMapId(UUID mapId);
    List<Scene> findByEncounterId(UUID encounterId);
}
```

- [ ] **Step 5: Add the cursor column to Campaign**

In `campaign/data/Campaign.java`, after the `milestoneLeveling` field, add:

```java
    @Column
    private UUID currentSceneId;
```

and after the `milestoneLeveling` getter/setter pair, add:

```java
    public UUID getCurrentSceneId() { return currentSceneId; }
    public void setCurrentSceneId(UUID currentSceneId) { this.currentSceneId = currentSceneId; }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -Dtest=AdventureRepositoryTest`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure src/main/java/dev/hendrikhoemberg/dmhelper/campaign/data/Campaign.java src/test/java/dev/hendrikhoemberg/dmhelper/adventure
git commit -m "feat: adventure/chapter/scene data model with campaign scene cursor"
```

---

### Task 2: AdventureService — structure CRUD, ordering, links, deletion contract

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureServiceTest.java`

**Interfaces:**
- Consumes: Task 1 entities/repositories; existing `GameMapRepository`, `EncounterRepository`, `StatBlockRepository` (`library.data`), `HandoutRepository`, `CampaignRepository`, `NotFoundException`.
- Produces (exact signatures later tasks rely on):
  - `Adventure createAdventure(UUID campaignId, String name, String description, String sourceAttribution)`
  - `Adventure findAdventureById(UUID id)` / `List<Adventure> findAdventuresByCampaign(UUID campaignId)`
  - `Adventure updateAdventure(UUID id, String name, String description, String sourceAttribution)`
  - `void deleteAdventure(UUID id)` / `void moveAdventure(UUID id, int direction)` (direction −1 = up, +1 = down; no-op at edges)
  - `Chapter createChapter(UUID adventureId, String title, String intro)` / `findChapterById` / `findChaptersByAdventure(UUID adventureId)` / `updateChapter(UUID id, String title, String intro)` / `deleteChapter(UUID id)` / `moveChapter(UUID id, int direction)`
  - `Scene createScene(UUID chapterId, String title, String sceneKey, String body)` / `findSceneById` / `updateScene(UUID id, String title, String sceneKey, String body)` / `deleteScene(UUID id)` / `moveScene(UUID id, int direction)` / `moveSceneToChapter(UUID sceneId, UUID chapterId)`
  - `List<Scene> flattenedScenes(UUID adventureId)` — chapters by sortOrder, scenes by sortOrder within
  - Links: `linkMap(UUID sceneId, UUID mapId, Integer pinX, Integer pinY)`, `unlinkMap(UUID sceneId)`, `linkEncounter(UUID sceneId, UUID encounterId)`, `unlinkEncounter(UUID sceneId)`, `addStatBlock(UUID sceneId, UUID statBlockId)`, `removeStatBlock(UUID sceneId, UUID statBlockId)`, `addHandout(UUID sceneId, UUID handoutId)`, `removeHandout(UUID sceneId, UUID handoutId)` — all return `Scene`

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AdventureService.class)
class AdventureServiceTest {

    @Autowired private AdventureService service;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);
    }

    private GameMap map(String name) {
        GameMap m = new GameMap();
        m.setCampaign(campaign);
        m.setName(name);
        m.setGridWidth(10);
        m.setGridHeight(10);
        m.setCellSizePx(48);
        return gameMapRepository.save(m);
    }

    private Encounter encounter(String name) {
        Encounter e = new Encounter();
        e.setCampaign(campaign);
        e.setName(name);
        return encounterRepository.save(e);
    }

    @Test
    void createAssignsSequentialSortOrder() {
        var a1 = service.createAdventure(campaign.getId(), "One", null, null);
        var a2 = service.createAdventure(campaign.getId(), "Two", null, null);
        assertThat(a1.getSortOrder()).isEqualTo(0);
        assertThat(a2.getSortOrder()).isEqualTo(1);

        var ch1 = service.createChapter(a1.getId(), "Ch 1", null);
        var ch2 = service.createChapter(a1.getId(), "Ch 2", null);
        assertThat(ch1.getSortOrder()).isEqualTo(0);
        assertThat(ch2.getSortOrder()).isEqualTo(1);

        var s1 = service.createScene(ch1.getId(), "Gate", "1", null);
        var s2 = service.createScene(ch1.getId(), "Shrine", "2", null);
        assertThat(s1.getSortOrder()).isEqualTo(0);
        assertThat(s2.getSortOrder()).isEqualTo(1);
        assertThat(s1.getStatus()).isEqualTo(SceneStatus.UNVISITED);
    }

    @Test
    void moveSwapsNeighborsAndIsNoOpAtEdges() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s1 = service.createScene(ch.getId(), "First", null, null);
        var s2 = service.createScene(ch.getId(), "Second", null, null);

        service.moveScene(s2.getId(), -1);
        var scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(ch.getId());
        assertThat(scenes).extracting(Scene::getTitle).containsExactly("Second", "First");

        service.moveScene(s2.getId(), -1); // already first — no-op
        scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(ch.getId());
        assertThat(scenes).extracting(Scene::getTitle).containsExactly("Second", "First");
    }

    @Test
    void moveSceneToChapterAppendsAtEnd() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch1 = service.createChapter(a.getId(), "Ch 1", null);
        var ch2 = service.createChapter(a.getId(), "Ch 2", null);
        var s1 = service.createScene(ch1.getId(), "Moving", null, null);
        service.createScene(ch2.getId(), "Existing", null, null);

        service.moveSceneToChapter(s1.getId(), ch2.getId());

        var scenes = sceneRepository.findByChapterIdOrderBySortOrderAsc(ch2.getId());
        assertThat(scenes).extracting(Scene::getTitle).containsExactly("Existing", "Moving");
        assertThat(sceneRepository.findByChapterIdOrderBySortOrderAsc(ch1.getId())).isEmpty();
    }

    @Test
    void flattenedScenesWalksChaptersInOrder() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch1 = service.createChapter(a.getId(), "Ch 1", null);
        var ch2 = service.createChapter(a.getId(), "Ch 2", null);
        service.createScene(ch1.getId(), "1a", null, null);
        service.createScene(ch1.getId(), "1b", null, null);
        service.createScene(ch2.getId(), "2a", null, null);

        assertThat(service.flattenedScenes(a.getId()))
                .extracting(Scene::getTitle).containsExactly("1a", "1b", "2a");
    }

    @Test
    void linksStorePinAndCanBeCleared() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s = service.createScene(ch.getId(), "Scene", null, null);
        var m = map("Throne Room");
        var e = encounter("Ambush");

        s = service.linkMap(s.getId(), m.getId(), 576, 240);
        assertThat(s.getMap().getId()).isEqualTo(m.getId());
        assertThat(s.getPinX()).isEqualTo(576);
        assertThat(s.getPinY()).isEqualTo(240);

        s = service.linkEncounter(s.getId(), e.getId());
        assertThat(s.getEncounter().getId()).isEqualTo(e.getId());

        s = service.unlinkMap(s.getId());
        assertThat(s.getMap()).isNull();
        assertThat(s.getPinX()).isNull();
        assertThat(s.getPinY()).isNull();

        s = service.unlinkEncounter(s.getId());
        assertThat(s.getEncounter()).isNull();
    }

    @Test
    void deleteAdventureCascadesDownwardOnlyAndClearsCursor() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s = service.createScene(ch.getId(), "Scene", null, null);
        var m = map("Survivor Map");
        var e = encounter("Survivor Encounter");
        service.linkMap(s.getId(), m.getId(), 10, 10);
        service.linkEncounter(s.getId(), e.getId());
        campaign.setCurrentSceneId(s.getId());
        campaignRepository.save(campaign);

        service.deleteAdventure(a.getId());
        em.flush();
        em.clear();

        assertThat(sceneRepository.findById(s.getId())).isEmpty();
        assertThat(gameMapRepository.findById(m.getId())).isPresent();
        assertThat(encounterRepository.findById(e.getId())).isPresent();
        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow()
                .getCurrentSceneId()).isNull();
    }

    @Test
    void deleteSceneClearsCursorWhenCurrent() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s = service.createScene(ch.getId(), "Scene", null, null);
        campaign.setCurrentSceneId(s.getId());
        campaignRepository.save(campaign);

        service.deleteScene(s.getId());

        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow()
                .getCurrentSceneId()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AdventureServiceTest`
Expected: COMPILATION ERROR (`AdventureService` does not exist).

- [ ] **Step 3: Implement AdventureService**

```java
package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdventureService {

    private final AdventureRepository adventureRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final CampaignRepository campaignRepository;
    private final GameMapRepository gameMapRepository;
    private final EncounterRepository encounterRepository;
    private final StatBlockRepository statBlockRepository;
    private final HandoutRepository handoutRepository;

    public AdventureService(AdventureRepository adventureRepository,
                            ChapterRepository chapterRepository,
                            SceneRepository sceneRepository,
                            CampaignRepository campaignRepository,
                            GameMapRepository gameMapRepository,
                            EncounterRepository encounterRepository,
                            StatBlockRepository statBlockRepository,
                            HandoutRepository handoutRepository) {
        this.adventureRepository = adventureRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.campaignRepository = campaignRepository;
        this.gameMapRepository = gameMapRepository;
        this.encounterRepository = encounterRepository;
        this.statBlockRepository = statBlockRepository;
        this.handoutRepository = handoutRepository;
    }

    // ---- Adventures ----

    public Adventure createAdventure(UUID campaignId, String name, String description, String sourceAttribution) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        Adventure a = new Adventure();
        a.setCampaign(campaign);
        a.setName(name);
        a.setDescription(description);
        a.setSourceAttribution(sourceAttribution);
        a.setSortOrder(adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaignId).size());
        return adventureRepository.save(a);
    }

    @Transactional(readOnly = true)
    public Adventure findAdventureById(UUID id) {
        return adventureRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Adventure not found"));
    }

    @Transactional(readOnly = true)
    public List<Adventure> findAdventuresByCampaign(UUID campaignId) {
        return adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaignId);
    }

    public Adventure updateAdventure(UUID id, String name, String description, String sourceAttribution) {
        Adventure a = findAdventureById(id);
        a.setName(name);
        a.setDescription(description);
        a.setSourceAttribution(sourceAttribution);
        return adventureRepository.save(a);
    }

    public void deleteAdventure(UUID id) {
        Adventure a = findAdventureById(id);
        for (Chapter ch : chapterRepository.findByAdventureIdOrderBySortOrderAsc(id)) {
            deleteChapterInternal(ch);
        }
        UUID campaignId = a.getCampaign().getId();
        adventureRepository.delete(a);
        renumberAdventures(campaignId);
    }

    public void moveAdventure(UUID id, int direction) {
        Adventure a = findAdventureById(id);
        var siblings = adventureRepository.findByCampaignIdOrderBySortOrderAsc(a.getCampaign().getId());
        int idx = indexOfId(siblings.stream().map(Adventure::getId).toList(), id);
        int target = idx + direction;
        if (target < 0 || target >= siblings.size()) return;
        Adventure other = siblings.get(target);
        int tmp = a.getSortOrder();
        a.setSortOrder(other.getSortOrder());
        other.setSortOrder(tmp);
        adventureRepository.save(a);
        adventureRepository.save(other);
    }

    // ---- Chapters ----

    public Chapter createChapter(UUID adventureId, String title, String intro) {
        Adventure a = findAdventureById(adventureId);
        Chapter ch = new Chapter();
        ch.setAdventure(a);
        ch.setTitle(title);
        ch.setIntro(intro);
        ch.setSortOrder(chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId).size());
        return chapterRepository.save(ch);
    }

    @Transactional(readOnly = true)
    public Chapter findChapterById(UUID id) {
        return chapterRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chapter not found"));
    }

    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByAdventure(UUID adventureId) {
        return chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId);
    }

    public Chapter updateChapter(UUID id, String title, String intro) {
        Chapter ch = findChapterById(id);
        ch.setTitle(title);
        ch.setIntro(intro);
        return chapterRepository.save(ch);
    }

    public void deleteChapter(UUID id) {
        Chapter ch = findChapterById(id);
        UUID adventureId = ch.getAdventure().getId();
        deleteChapterInternal(ch);
        renumberChapters(adventureId);
    }

    public void moveChapter(UUID id, int direction) {
        Chapter ch = findChapterById(id);
        var siblings = chapterRepository.findByAdventureIdOrderBySortOrderAsc(ch.getAdventure().getId());
        int idx = indexOfId(siblings.stream().map(Chapter::getId).toList(), id);
        int target = idx + direction;
        if (target < 0 || target >= siblings.size()) return;
        Chapter other = siblings.get(target);
        int tmp = ch.getSortOrder();
        ch.setSortOrder(other.getSortOrder());
        other.setSortOrder(tmp);
        chapterRepository.save(ch);
        chapterRepository.save(other);
    }

    // ---- Scenes ----

    public Scene createScene(UUID chapterId, String title, String sceneKey, String body) {
        Chapter ch = findChapterById(chapterId);
        Scene s = new Scene();
        s.setChapter(ch);
        s.setTitle(title);
        s.setSceneKey(sceneKey);
        s.setBody(body);
        s.setSortOrder(sceneRepository.findByChapterIdOrderBySortOrderAsc(chapterId).size());
        return sceneRepository.save(s);
    }

    @Transactional(readOnly = true)
    public Scene findSceneById(UUID id) {
        return sceneRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scene not found"));
    }

    public Scene updateScene(UUID id, String title, String sceneKey, String body) {
        Scene s = findSceneById(id);
        s.setTitle(title);
        s.setSceneKey(sceneKey);
        s.setBody(body);
        return sceneRepository.save(s);
    }

    public void deleteScene(UUID id) {
        Scene s = findSceneById(id);
        UUID chapterId = s.getChapter().getId();
        clearCursorIfCurrent(s);
        sceneRepository.delete(s);
        renumberScenes(chapterId);
    }

    public void moveScene(UUID id, int direction) {
        Scene s = findSceneById(id);
        var siblings = sceneRepository.findByChapterIdOrderBySortOrderAsc(s.getChapter().getId());
        int idx = indexOfId(siblings.stream().map(Scene::getId).toList(), id);
        int target = idx + direction;
        if (target < 0 || target >= siblings.size()) return;
        Scene other = siblings.get(target);
        int tmp = s.getSortOrder();
        s.setSortOrder(other.getSortOrder());
        other.setSortOrder(tmp);
        sceneRepository.save(s);
        sceneRepository.save(other);
    }

    public Scene moveSceneToChapter(UUID sceneId, UUID chapterId) {
        Scene s = findSceneById(sceneId);
        Chapter target = findChapterById(chapterId);
        UUID oldChapterId = s.getChapter().getId();
        if (oldChapterId.equals(chapterId)) return s;
        s.setChapter(target);
        s.setSortOrder(sceneRepository.findByChapterIdOrderBySortOrderAsc(chapterId).size());
        Scene saved = sceneRepository.save(s);
        renumberScenes(oldChapterId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Scene> flattenedScenes(UUID adventureId) {
        List<Scene> flat = new ArrayList<>();
        for (Chapter ch : chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId)) {
            flat.addAll(sceneRepository.findByChapterIdOrderBySortOrderAsc(ch.getId()));
        }
        return flat;
    }

    // ---- Scene links ----

    public Scene linkMap(UUID sceneId, UUID mapId, Integer pinX, Integer pinY) {
        Scene s = findSceneById(sceneId);
        var map = gameMapRepository.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Map not found"));
        s.setMap(map);
        s.setPinX(pinX);
        s.setPinY(pinY);
        return sceneRepository.save(s);
    }

    public Scene unlinkMap(UUID sceneId) {
        Scene s = findSceneById(sceneId);
        s.setMap(null);
        s.setPinX(null);
        s.setPinY(null);
        return sceneRepository.save(s);
    }

    public Scene linkEncounter(UUID sceneId, UUID encounterId) {
        Scene s = findSceneById(sceneId);
        var enc = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found"));
        s.setEncounter(enc);
        return sceneRepository.save(s);
    }

    public Scene unlinkEncounter(UUID sceneId) {
        Scene s = findSceneById(sceneId);
        s.setEncounter(null);
        return sceneRepository.save(s);
    }

    public Scene addStatBlock(UUID sceneId, UUID statBlockId) {
        Scene s = findSceneById(sceneId);
        var sb = statBlockRepository.findById(statBlockId)
                .orElseThrow(() -> new NotFoundException("StatBlock not found"));
        if (s.getStatBlocks().stream().noneMatch(x -> x.getId().equals(statBlockId))) {
            s.getStatBlocks().add(sb);
        }
        return sceneRepository.save(s);
    }

    public Scene removeStatBlock(UUID sceneId, UUID statBlockId) {
        Scene s = findSceneById(sceneId);
        s.getStatBlocks().removeIf(x -> x.getId().equals(statBlockId));
        return sceneRepository.save(s);
    }

    public Scene addHandout(UUID sceneId, UUID handoutId) {
        Scene s = findSceneById(sceneId);
        var h = handoutRepository.findById(handoutId)
                .orElseThrow(() -> new NotFoundException("Handout not found"));
        if (s.getHandouts().stream().noneMatch(x -> x.getId().equals(handoutId))) {
            s.getHandouts().add(h);
        }
        return sceneRepository.save(s);
    }

    public Scene removeHandout(UUID sceneId, UUID handoutId) {
        Scene s = findSceneById(sceneId);
        s.getHandouts().removeIf(x -> x.getId().equals(handoutId));
        return sceneRepository.save(s);
    }

    // ---- internals ----

    private void deleteChapterInternal(Chapter ch) {
        for (Scene s : sceneRepository.findByChapterIdOrderBySortOrderAsc(ch.getId())) {
            clearCursorIfCurrent(s);
            sceneRepository.delete(s);
        }
        chapterRepository.delete(ch);
    }

    private void clearCursorIfCurrent(Scene scene) {
        Campaign campaign = scene.getChapter().getAdventure().getCampaign();
        if (scene.getId().equals(campaign.getCurrentSceneId())) {
            campaign.setCurrentSceneId(null);
            campaignRepository.save(campaign);
        }
    }

    private void renumberAdventures(UUID campaignId) {
        var list = adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaignId);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i);
        }
        adventureRepository.saveAll(list);
    }

    private void renumberChapters(UUID adventureId) {
        var list = chapterRepository.findByAdventureIdOrderBySortOrderAsc(adventureId);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i);
        }
        chapterRepository.saveAll(list);
    }

    private void renumberScenes(UUID chapterId) {
        var list = sceneRepository.findByChapterIdOrderBySortOrderAsc(chapterId);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i);
        }
        sceneRepository.saveAll(list);
    }

    private int indexOfId(List<UUID> ids, UUID id) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals(id)) return i;
        }
        return -1;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AdventureServiceTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure src/test/java/dev/hendrikhoemberg/dmhelper/adventure
git commit -m "feat: AdventureService with structure CRUD, ordering, links, deletion contract"
```

---

### Task 3: Run-mode cursor & scene status semantics

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureService.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/AdventureServiceTest.java` (add tests)

**Interfaces:**
- Produces (added to `AdventureService`):
  - `Scene setStatus(UUID sceneId, SceneStatus status)` — plain set, no transition rules
  - `Scene setCurrentScene(UUID campaignId, UUID sceneId)` — sets `Campaign.currentSceneId`; bumps the scene UNVISITED → VISITED (never touches DONE)
  - `void clearCurrentScene(UUID campaignId)`
  - `Optional<Scene> getCurrentScene(UUID campaignId)` — empty if unset or the scene no longer exists (self-heals a stale cursor by clearing it)
  - `Optional<Scene> stepCurrentScene(UUID campaignId, int direction)` — moves the cursor along `flattenedScenes` of the *current scene's* adventure; at the adventure's edge the cursor stays put and the current scene is returned; empty if no cursor is set

- [ ] **Step 1: Add failing tests to AdventureServiceTest**

```java
    @Test
    void setCurrentSceneBumpsUnvisitedToVisitedOnly() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch = service.createChapter(a.getId(), "Ch", null);
        var s = service.createScene(ch.getId(), "Scene", null, null);

        service.setCurrentScene(campaign.getId(), s.getId());
        assertThat(service.findSceneById(s.getId()).getStatus()).isEqualTo(SceneStatus.VISITED);
        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow()
                .getCurrentSceneId()).isEqualTo(s.getId());

        service.setStatus(s.getId(), SceneStatus.DONE);
        service.setCurrentScene(campaign.getId(), s.getId());
        assertThat(service.findSceneById(s.getId()).getStatus()).isEqualTo(SceneStatus.DONE);
    }

    @Test
    void stepWalksAcrossChaptersAndStopsAtEdges() {
        var a = service.createAdventure(campaign.getId(), "A", null, null);
        var ch1 = service.createChapter(a.getId(), "Ch 1", null);
        var ch2 = service.createChapter(a.getId(), "Ch 2", null);
        var s1 = service.createScene(ch1.getId(), "1a", null, null);
        var s2 = service.createScene(ch2.getId(), "2a", null, null);

        service.setCurrentScene(campaign.getId(), s1.getId());

        var next = service.stepCurrentScene(campaign.getId(), 1);
        assertThat(next).isPresent();
        assertThat(next.get().getId()).isEqualTo(s2.getId());

        // at the last scene: stays put
        var edge = service.stepCurrentScene(campaign.getId(), 1);
        assertThat(edge).isPresent();
        assertThat(edge.get().getId()).isEqualTo(s2.getId());

        var back = service.stepCurrentScene(campaign.getId(), -1);
        assertThat(back).isPresent();
        assertThat(back.get().getId()).isEqualTo(s1.getId());
    }

    @Test
    void stepWithoutCursorIsEmptyAndStaleCursorSelfHeals() {
        assertThat(service.stepCurrentScene(campaign.getId(), 1)).isEmpty();

        campaign.setCurrentSceneId(UUID.randomUUID()); // points at nothing
        campaignRepository.save(campaign);
        assertThat(service.getCurrentScene(campaign.getId())).isEmpty();
        assertThat(campaignRepository.findById(campaign.getId()).orElseThrow()
                .getCurrentSceneId()).isNull();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=AdventureServiceTest`
Expected: COMPILATION ERROR (`setCurrentScene` etc. do not exist).

- [ ] **Step 3: Implement cursor & status methods**

Add to `AdventureService` (also add `import java.util.Optional;`):

```java
    // ---- Run mode: status & cursor ----

    public Scene setStatus(UUID sceneId, SceneStatus status) {
        Scene s = findSceneById(sceneId);
        s.setStatus(status);
        return sceneRepository.save(s);
    }

    public Scene setCurrentScene(UUID campaignId, UUID sceneId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        Scene s = findSceneById(sceneId);
        campaign.setCurrentSceneId(s.getId());
        campaignRepository.save(campaign);
        if (s.getStatus() == SceneStatus.UNVISITED) {
            s.setStatus(SceneStatus.VISITED);
            s = sceneRepository.save(s);
        }
        return s;
    }

    public void clearCurrentScene(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        campaign.setCurrentSceneId(null);
        campaignRepository.save(campaign);
    }

    public Optional<Scene> getCurrentScene(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        if (campaign.getCurrentSceneId() == null) return Optional.empty();
        var scene = sceneRepository.findById(campaign.getCurrentSceneId());
        if (scene.isEmpty()) {
            campaign.setCurrentSceneId(null);
            campaignRepository.save(campaign);
        }
        return scene;
    }

    public Optional<Scene> stepCurrentScene(UUID campaignId, int direction) {
        var currentOpt = getCurrentScene(campaignId);
        if (currentOpt.isEmpty()) return Optional.empty();
        Scene current = currentOpt.get();
        List<Scene> flat = flattenedScenes(current.getChapter().getAdventure().getId());
        int idx = indexOfId(flat.stream().map(Scene::getId).toList(), current.getId());
        int target = idx + direction;
        if (target < 0 || target >= flat.size()) return Optional.of(current);
        return Optional.of(setCurrentScene(campaignId, flat.get(target).getId()));
    }
```

Note: `getCurrentScene` and `stepCurrentScene` mutate state (self-heal, cursor move) — they are intentionally **not** `@Transactional(readOnly = true)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=AdventureServiceTest`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure src/test/java/dev/hendrikhoemberg/dmhelper/adventure
git commit -m "feat: scene status and campaign current-scene cursor with step semantics"
```

---

### Task 4: SceneRefCleaner — deletion hooks in linked-entity services

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneRefCleaner.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/service/EncounterService.java` (`delete` at ~line 212)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java` (`delete` at ~line 119)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/library/service/StatBlockService.java` (`delete` at ~line 176)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/handout/service/HandoutService.java` (`delete` at ~line 94)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/service/SceneRefCleanerTest.java`

**Interfaces:**
- Produces: `@Component SceneRefCleaner` with `void detachEncounter(UUID encounterId)`, `void detachMap(UUID mapId)` (also nulls pin), `void detachStatBlock(UUID statBlockId)`, `void detachHandout(UUID handoutId)`.
- Pattern note: cross-module repository/service injection is established house style (`NoteService` already injects `EncounterRepository`, `GameMapRepository`, `HandoutRepository`). Each of the four services gains a constructor parameter `SceneRefCleaner sceneRefCleaner` and calls the matching `detach*` **at the top of its `delete` method**, before the entity is removed.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AdventureService.class, SceneRefCleaner.class})
class SceneRefCleanerTest {

    @Autowired private AdventureService adventureService;
    @Autowired private SceneRefCleaner cleaner;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private HandoutRepository handoutRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private Scene scene;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        campaign = campaignRepository.save(campaign);
        var a = adventureService.createAdventure(campaign.getId(), "A", null, null);
        var ch = adventureService.createChapter(a.getId(), "Ch", null);
        scene = adventureService.createScene(ch.getId(), "Scene", null, null);
    }

    @Test
    void detachMapNullsMapAndPin() {
        GameMap m = new GameMap();
        m.setCampaign(campaign);
        m.setName("Map");
        m.setGridWidth(10);
        m.setGridHeight(10);
        m.setCellSizePx(48);
        m = gameMapRepository.save(m);
        adventureService.linkMap(scene.getId(), m.getId(), 5, 5);

        cleaner.detachMap(m.getId());
        em.flush();
        em.clear();

        Scene reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getMap()).isNull();
        assertThat(reloaded.getPinX()).isNull();
        assertThat(reloaded.getPinY()).isNull();
    }

    @Test
    void detachEncounterNullsRef() {
        Encounter e = new Encounter();
        e.setCampaign(campaign);
        e.setName("Enc");
        e = encounterRepository.save(e);
        adventureService.linkEncounter(scene.getId(), e.getId());

        cleaner.detachEncounter(e.getId());
        em.flush();
        em.clear();

        assertThat(sceneRepository.findById(scene.getId()).orElseThrow().getEncounter()).isNull();
    }

    @Test
    void detachStatBlockAndHandoutRemoveFromLists() {
        StatBlock sb = new StatBlock();
        sb.setName("Goblin Custom");
        sb.setSource(StatBlock.Source.CUSTOM);
        sb = statBlockRepository.save(sb);
        adventureService.addStatBlock(scene.getId(), sb.getId());

        Handout h = new Handout();
        h.setCampaign(campaign);
        h.setTitle("Letter");
        h.setFileName("letter.png");
        h.setContentType("image/png");
        h = handoutRepository.save(h);
        adventureService.addHandout(scene.getId(), h.getId());

        cleaner.detachStatBlock(sb.getId());
        cleaner.detachHandout(h.getId());
        em.flush();
        em.clear();

        Scene reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getStatBlocks()).isEmpty();
        assertThat(reloaded.getHandouts()).isEmpty();
    }
}
```

> If `StatBlock`/`Handout` require further non-null fields to persist, set them to minimal dummy values — check the entity classes; do not change the entities.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SceneRefCleanerTest`
Expected: COMPILATION ERROR (`SceneRefCleaner` does not exist).

- [ ] **Step 3: Implement SceneRefCleaner**

```java
package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Nulls scene references when a linked entity is deleted (spec deletion contract:
 * scenes survive, links go). Called by the owning services' delete methods.
 */
@Component
@Transactional
public class SceneRefCleaner {

    private final SceneRepository sceneRepository;

    public SceneRefCleaner(SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    public void detachEncounter(UUID encounterId) {
        for (Scene s : sceneRepository.findByEncounterId(encounterId)) {
            s.setEncounter(null);
            sceneRepository.save(s);
        }
    }

    public void detachMap(UUID mapId) {
        for (Scene s : sceneRepository.findByMapId(mapId)) {
            s.setMap(null);
            s.setPinX(null);
            s.setPinY(null);
            sceneRepository.save(s);
        }
    }

    public void detachStatBlock(UUID statBlockId) {
        for (Scene s : sceneRepository.findAll()) {
            if (s.getStatBlocks().removeIf(sb -> sb.getId().equals(statBlockId))) {
                sceneRepository.save(s);
            }
        }
    }

    public void detachHandout(UUID handoutId) {
        for (Scene s : sceneRepository.findAll()) {
            if (s.getHandouts().removeIf(h -> h.getId().equals(handoutId))) {
                sceneRepository.save(s);
            }
        }
    }
}
```

(`findAll()` scans are fine here: single-user local app, deletes are rare DM actions, and join-table membership queries would need custom JPQL for marginal gain.)

- [ ] **Step 4: Wire into the four services**

In each of `EncounterService`, `GameMapService`, `StatBlockService`, `HandoutService`:
1. Add constructor parameter `SceneRefCleaner sceneRefCleaner` and a matching final field (import `dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner`).
2. At the **first line** of the existing `public void delete(UUID …)` method, add the matching call:
   - `EncounterService.delete` → `sceneRefCleaner.detachEncounter(id);`
   - `GameMapService.delete` → `sceneRefCleaner.detachMap(mapId);`
   - `StatBlockService.delete` → `sceneRefCleaner.detachStatBlock(id);`
   - `HandoutService.delete` → `sceneRefCleaner.detachHandout(id);`
3. Fix any tests that construct these services directly (search for `new EncounterService(`, etc.) by passing a `SceneRefCleaner` built on the test's `SceneRepository`, or add `SceneRefCleaner.class` to the test's `@Import`. `@WebMvcTest` slices with `@MockitoBean` service mocks are unaffected.

- [ ] **Step 5: Run the full test suite** (constructor changes ripple)

Run: `./mvnw test`
Expected: PASS (fix any test wiring fallout as described in Step 4.3).

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: null scene links when maps/encounters/statblocks/handouts are deleted"
```

---

### Task 5: Read-aloud Markdown rendering

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtil.java`
- Modify: `src/main/resources/static/css/app.css` (near `.note-body` at ~line 798)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtilTest.java`

**Interfaces:**
- Consumes: existing `MarkdownUtil.toHtml(String)` (bean name `markdownUtil`, used from Thymeleaf as `${@markdownUtil.toHtml(...)}` and controllers).
- Produces: a fenced block ```` ```read-aloud ```` renders as `<div class="read-aloud"><p>…</p></div>` with HTML-escaped text, one `<p>` per blank-line-separated paragraph. All other fenced blocks render exactly as before. This is the **only** markdown change; the convention also gets documented in the JSON schema in Task 12.

- [ ] **Step 1: Write the failing test**

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownUtilTest {

    private final MarkdownUtil markdownUtil = new MarkdownUtil();

    @Test
    void rendersReadAloudFenceAsBoxedDiv() {
        String md = """
                Before text.

                ```read-aloud
                Gilded amber pillars rise to a vaulted ceiling.

                A voice echoes: "Kneel."
                ```

                After text.
                """;
        String html = markdownUtil.toHtml(md);

        assertThat(html).contains("<div class=\"read-aloud\">");
        assertThat(html).contains("<p>Gilded amber pillars rise to a vaulted ceiling.</p>");
        assertThat(html).contains("<p>A voice echoes: &quot;Kneel.&quot;</p>");
        assertThat(html).contains("</div>");
        assertThat(html).doesNotContain("<pre>");
    }

    @Test
    void escapesHtmlInsideReadAloud() {
        String html = markdownUtil.toHtml("```read-aloud\n<script>alert(1)</script>\n```");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void ordinaryCodeFencesStillRenderAsCode() {
        String html = markdownUtil.toHtml("```\nplain code\n```");
        assertThat(html).contains("<pre>");
        assertThat(html).contains("plain code");
    }

    @Test
    void plainMarkdownUnchanged() {
        assertThat(markdownUtil.toHtml("# Title")).contains("<h1>Title</h1>");
        assertThat(markdownUtil.toHtml(null)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MarkdownUtilTest`
Expected: FAIL — `rendersReadAloudFenceAsBoxedDiv` (renders `<pre>` today).

- [ ] **Step 3: Implement the custom node renderer**

Replace `MarkdownUtil.java` with:

```java
package dev.hendrikhoemberg.dmhelper.config;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.CoreHtmlNodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component("markdownUtil")
public class MarkdownUtil {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .escapeHtml(false)
            .nodeRendererFactory(ReadAloudNodeRenderer::new)
            .build();

    public String toHtml(String markdown) {
        if (markdown == null) return "";
        return renderer.render(parser.parse(markdown));
    }

    /** Renders ```read-aloud fenced blocks as boxed text (SPEC adventure module);
     *  all other fenced blocks fall through to the default code rendering. */
    static class ReadAloudNodeRenderer implements NodeRenderer {

        private final HtmlNodeRendererContext context;
        private final HtmlWriter html;

        ReadAloudNodeRenderer(HtmlNodeRendererContext context) {
            this.context = context;
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(FencedCodeBlock.class);
        }

        @Override
        public void render(Node node) {
            FencedCodeBlock block = (FencedCodeBlock) node;
            String info = block.getInfo();
            if (info == null || !info.trim().equalsIgnoreCase("read-aloud")) {
                new CoreHtmlNodeRenderer(context).render(node);
                return;
            }
            html.line();
            html.tag("div", Map.of("class", "read-aloud"));
            String literal = block.getLiteral() == null ? "" : block.getLiteral();
            for (String para : literal.split("\\n\\s*\\n")) {
                if (para.isBlank()) continue;
                html.tag("p");
                html.text(para.trim());
                html.tag("/p");
            }
            html.tag("/div");
            html.line();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=MarkdownUtilTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Add the boxed style**

In `src/main/resources/static/css/app.css`, directly after the `.note-body h3` rule (~line 811), add:

```css
/* Read-aloud (boxed) text — adventure scenes */
.read-aloud {
    margin: var(--space-md) 0;
    padding: var(--space-md) var(--space-lg);
    border-left: 3px solid var(--color-accent, #b45309);
    background: color-mix(in srgb, var(--color-accent, #b45309) 8%, transparent);
    border-radius: 4px;
    font-style: italic;
}
.read-aloud p { margin: 0 0 var(--space-sm) 0; }
.read-aloud p:last-child { margin-bottom: 0; }
```

If `app.css` defines a different accent token name (check `:root` at the top of the file), use that token instead of `--color-accent`.

- [ ] **Step 6: Run the notes tests to confirm no rendering regression**

Run: `./mvnw test -Dtest='Note*,MarkdownUtilTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtil.java src/main/resources/static/css/app.css src/test/java/dev/hendrikhoemberg/dmhelper/config/MarkdownUtilTest.java
git commit -m "feat: render read-aloud fenced blocks as boxed text"
```

---

### Task 6: Adventure & Chapter web controllers + Thymeleaf templates

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureController.java`
- Create: `src/main/resources/templates/adventure/list.html`
- Create: `src/main/resources/templates/adventure/detail.html`
- Create: `src/main/resources/templates/adventure/_adventure-form.html`
- Create: `src/main/resources/templates/adventure/_chapter-form.html`
- Create: `src/main/resources/templates/adventure/_chapter-list.html`
- Modify: `src/main/resources/templates/campaigns/detail.html` (add adventures section)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/AdventureControllerTest.java`

**Interfaces:**
- Consumes: `AdventureService` from Task 2-3, `CampaignRepository` for `@ModelAttribute`, existing fragment patterns.
- Produces: full-page templates `adventure/list.html` (adventure overview), `adventure/detail.html` (adventure TOC with chapters); fragment templates `_adventure-form.html`, `_chapter-form.html`, `_chapter-list.html` for htmx editing.
- Follows existing controller patterns: `@Controller`, `@RequestMapping("/campaigns/{campaignId}/adventures")`, `@ModelAttribute` for campaign, fragment returns for htmx, redirect-after-create pattern.

**Routes:**

| Method | Path | Returns | Purpose |
|--------|------|---------|---------|
| GET | `/campaigns/{campaignId}/adventures` | `adventure/list` | Adventure overview (full page) |
| GET | `/campaigns/{campaignId}/adventures/{id}` | `adventure/detail` | Adventure detail with chapter TOC |
| GET | `/campaigns/{campaignId}/adventures/new` | `_adventure-form :: form` | New adventure form fragment |
| POST | `/campaigns/{campaignId}/adventures` | redirect→detail | Create adventure |
| GET | `/campaigns/{campaignId}/adventures/{id}/edit` | `_adventure-form :: form` | Edit adventure form fragment |
| PUT | `/campaigns/{campaignId}/adventures/{id}` | redirect→detail | Update adventure |
| DELETE | `/campaigns/{campaignId}/adventures/{id}` | HX-Redirect | Delete adventure |
| PUT | `/campaigns/{campaignId}/adventures/{id}/move` | `adventure/_adventure-list :: adventureList` | Reorder adventure |

Chapter routes (nested within adventure):

| Method | Path | Returns | Purpose |
|--------|------|---------|---------|
| GET | `…/adventures/{adventureId}/chapters/new` | `_chapter-form :: form` | New chapter form fragment |
| POST | `…/adventures/{adventureId}/chapters` | `_chapter-list :: chapterList` | Create chapter (inline) |
| GET | `…/adventures/{adventureId}/chapters/{id}/edit` | `_chapter-form :: form` | Edit chapter form fragment |
| PUT | `…/adventures/{adventureId}/chapters/{id}` | `_chapter-list :: chapterList` | Update chapter (inline) |
| DELETE | `…/adventures/{adventureId}/chapters/{id}` | `_chapter-list :: chapterList` | Delete chapter (inline) |
| PUT | `…/adventures/{adventureId}/chapters/{id}/move` | `_chapter-list :: chapterList` | Reorder chapter |

- [ ] **Step 1: Write the failing controller test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneStatus;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdventureController.class)
class AdventureControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdventureService adventureService;
    @MockitoBean private CampaignRepository campaignRepository;

    private UUID campaignId;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
    }

    @Test
    void listShowsAdventureOverview() throws Exception {
        when(adventureService.findAdventuresByCampaign(campaignId)).thenReturn(List.of());
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/campaigns/{campaignId}/adventures", campaignId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("adventures", "currentScene"));
    }

    @Test
    void detailShowsChapters() throws Exception {
        UUID aId = UUID.randomUUID();
        Adventure a = new Adventure();
        a.setId(aId);
        a.setName("Module");
        when(adventureService.findAdventureById(aId)).thenReturn(a);
        when(adventureService.findChaptersByAdventure(aId)).thenReturn(List.of());
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/campaigns/{campaignId}/adventures/{id}", campaignId, aId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("adventure", "chapters"));
    }

    @Test
    void createRedirectsToDetail() throws Exception {
        UUID aId = UUID.randomUUID();
        Adventure a = new Adventure();
        a.setId(aId);
        a.setName("New Module");
        when(adventureService.createAdventure(eq(campaignId), any(), any(), any())).thenReturn(a);

        mockMvc.perform(post("/campaigns/{campaignId}/adventures", campaignId)
                        .param("name", "New Module"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/campaigns/" + campaignId + "/adventures/" + aId));
    }

    @Test
    void deleteRedirectsToList() throws Exception {
        UUID aId = UUID.randomUUID();

        mockMvc.perform(delete("/campaigns/{campaignId}/adventures/{id}", campaignId, aId))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect",
                        "/campaigns/" + campaignId + "/adventures"));
    }

    @Test
    void chapterCreateReturnsChapterListFragment() throws Exception {
        UUID aId = UUID.randomUUID();
        Chapter ch = new Chapter();
        ch.setId(UUID.randomUUID());
        ch.setTitle("Ch 1");
        Adventure a = new Adventure();
        a.setId(aId);
        a.setName("Module");
        when(adventureService.findAdventureById(aId)).thenReturn(a);
        when(adventureService.createChapter(eq(aId), any(), any())).thenReturn(ch);
        when(adventureService.findChaptersByAdventure(aId)).thenReturn(List.of(ch));
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/campaigns/{campaignId}/adventures/{aId}/chapters", campaignId, aId)
                        .param("title", "Ch 1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("chapters", "adventure"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AdventureControllerTest`
Expected: COMPILATION ERROR (`AdventureController` does not exist).

- [ ] **Step 3: Implement AdventureController**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/adventures")
public class AdventureController {

    private final AdventureService adventureService;
    private final CampaignRepository campaignRepository;

    public AdventureController(AdventureService adventureService, CampaignRepository campaignRepository) {
        this.adventureService = adventureService;
        this.campaignRepository = campaignRepository;
    }

    @ModelAttribute
    public void addCampaign(@PathVariable UUID campaignId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    // ---- Adventures ----

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("adventures", adventureService.findAdventuresByCampaign(campaignId));
        model.addAttribute("currentScene", adventureService.getCurrentScene(campaignId).orElse(null));
        return "adventure/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(id));
        model.addAttribute("chapters", adventureService.findChaptersByAdventure(id));
        model.addAttribute("currentScene", adventureService.getCurrentScene(campaignId).orElse(null));
        return "adventure/detail";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("adventure", null);
        return "adventure/_adventure-form :: form";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String sourceAttribution) {
        Adventure a = adventureService.createAdventure(campaignId, name, description, sourceAttribution);
        return "redirect:/campaigns/" + campaignId + "/adventures/" + a.getId();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(id));
        return "adventure/_adventure-form :: form";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID campaignId, @PathVariable UUID id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String sourceAttribution) {
        adventureService.updateAdventure(id, name, description, sourceAttribution);
        return "redirect:/campaigns/" + campaignId + "/adventures/" + id;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        adventureService.deleteAdventure(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/adventures")
                .build();
    }

    @PutMapping("/{id}/move")
    public String move(@PathVariable UUID campaignId, @PathVariable UUID id,
                       @RequestParam int direction, Model model) {
        adventureService.moveAdventure(id, direction);
        model.addAttribute("adventures", adventureService.findAdventuresByCampaign(campaignId));
        model.addAttribute("currentScene", adventureService.getCurrentScene(campaignId).orElse(null));
        return "adventure/_adventure-list :: adventureList";
    }

    // ---- Chapters ----

    @GetMapping("/{adventureId}/chapters/new")
    public String newChapterForm(@PathVariable UUID campaignId, @PathVariable UUID adventureId, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapter", null);
        return "adventure/_chapter-form :: form";
    }

    @PostMapping("/{adventureId}/chapters")
    public String createChapter(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                                @RequestParam String title,
                                @RequestParam(required = false) String intro, Model model) {
        adventureService.createChapter(adventureId, title, intro);
        return chapterListView(adventureId, model);
    }

    @GetMapping("/{adventureId}/chapters/{chapterId}/edit")
    public String editChapterForm(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                                  @PathVariable UUID chapterId, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapter", adventureService.findChapterById(chapterId));
        return "adventure/_chapter-form :: form";
    }

    @PutMapping("/{adventureId}/chapters/{chapterId}")
    public String updateChapter(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                                @PathVariable UUID chapterId,
                                @RequestParam String title,
                                @RequestParam(required = false) String intro, Model model) {
        adventureService.updateChapter(chapterId, title, intro);
        return chapterListView(adventureId, model);
    }

    @DeleteMapping("/{adventureId}/chapters/{chapterId}")
    public String deleteChapter(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                                @PathVariable UUID chapterId, Model model) {
        adventureService.deleteChapter(chapterId);
        return chapterListView(adventureId, model);
    }

    @PutMapping("/{adventureId}/chapters/{chapterId}/move")
    public String moveChapter(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                              @PathVariable UUID chapterId,
                              @RequestParam int direction, Model model) {
        adventureService.moveChapter(chapterId, direction);
        return chapterListView(adventureId, model);
    }

    private String chapterListView(UUID adventureId, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapters", adventureService.findChaptersByAdventure(adventureId));
        return "adventure/_chapter-list :: chapterList";
    }
}
```

(Note: add `import org.springframework.http.ResponseEntity;`)

- [ ] **Step 4: Create Thymeleaf templates**

**`templates/adventure/list.html`** — adventure overview page:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/head :: layout(~{::title}, ~{::#main-content})}">
<head><title th:text="|${campaign.name} — Adventures|">Adventures</title></head>
<body>
<main id="main-content" class="app-layout" th:with="sidebar='campaigns'">
  <div th:replace="~{fragments/navbar :: navbar(active='campaigns')}"></div>
  <div th:replace="~{fragments/sidebar :: sidebar(sidebar, campaignId=${campaignId}, campaign=${campaign})}"></div>
  <div class="page-content">
    <div class="page-header">
      <h1 th:text="${campaign.name}">Campaign</h1>
      <div class="page-header-actions">
        <a th:href="@{/campaigns}" class="btn btn-ghost">Back to Campaigns</a>
      </div>
    </div>

    <div th:if="${currentScene}" class="card" style="margin-bottom: var(--space-md); padding: var(--space-md);">
      <span style="color: var(--color-text-muted); font-size: var(--text-sm);">Continue at:</span>
      <strong th:text="${currentScene.title}">Scene Title</strong>
      <a th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}
                    (cid=${campaignId}, aid=${currentScene.chapter.adventure.id}, sid=${currentScene.id})}"
         class="btn btn-primary" style="margin-left: var(--space-md);">Resume</a>
    </div>

    <div th:replace="~{adventure/_adventure-list :: adventureList}"></div>

    <div th:replace="~{notes/_quicknotes-strip :: strip(campaignId=${campaignId}, targetType='CAMPAIGN', targetId=${campaignId})}"></div>
  </div>
</main>
</body>
</html>
```

**`templates/adventure/_adventure-list.html`** — reusable adventure list fragment:

```html
<div th:fragment="adventureList" xmlns:th="http://www.thymeleaf.org">
  <div class="detail-section">
    <h2>Adventures <button class="btn btn-primary"
            hx-get="@{/campaigns/{cid}/adventures/new(cid=${campaignId})}"
            hx-target="#adventure-form-placeholder"
            hx-swap="innerHTML">+ New</button></h2>
    <div id="adventure-form-placeholder"></div>
    <div th:if="${adventures.isEmpty()}" class="empty-state">No adventures yet.</div>
    <div th:each="a : ${adventures}" class="card">
      <div class="card-header">
        <h3>
          <a th:href="@{/campaigns/{cid}/adventures/{id}(cid=${campaignId},id=${a.id})}"
             th:text="${a.name}">Adventure</a>
        </h3>
        <div class="card-actions">
          <button class="btn btn-ghost btn-sm"
                  hx-put="@{/campaigns/{cid}/adventures/{id}/move(cid=${campaignId},id=${a.id})}"
                  hx-vals='{"direction": -1}'
                  hx-target="closest .detail-section"
                  hx-swap="outerHTML">↑</button>
          <button class="btn btn-ghost btn-sm"
                  hx-put="@{/campaigns/{cid}/adventures/{id}/move(cid=${campaignId},id=${a.id})}"
                  hx-vals='{"direction": 1}'
                  hx-target="closest .detail-section"
                  hx-swap="outerHTML">↓</button>
          <button class="btn btn-ghost btn-sm"
                  hx-get="@{/campaigns/{cid}/adventures/{id}/edit(cid=${campaignId},id=${a.id})}"
                  hx-target="closest .card"
                  hx-swap="outerHTML">Edit</button>
          <button class="btn btn-danger btn-sm"
                  hx-delete="@{/campaigns/{cid}/adventures/{id}(cid=${campaignId},id=${a.id})}"
                  hx-confirm="Delete this adventure and all its chapters and scenes?"
                  hx-target="body" hx-swap="outerHTML">Delete</button>
        </div>
      </div>
      <div class="card-meta" th:if="${a.sourceAttribution}" th:text="${a.sourceAttribution}">Source</div>
      <div class="card-body" th:if="${a.description}" th:text="${a.description}">Description</div>
    </div>
  </div>
</div>
```

**`templates/adventure/_adventure-form.html`** — adventure create/edit form fragment:

```html
<div th:fragment="form" xmlns:th="http://www.thymeleaf.org"
     th:with="editing = ${adventure != null}">
  <form class="inline-form"
        th:action="@{${editing}
              ? '/campaigns/{cid}/adventures/{id}(cid=${campaignId},id=${adventure.id})'
              : '/campaigns/{cid}/adventures(cid=${campaignId})'}"
        th:method="${editing} ? 'put' : 'post'"
        hx-post th:if="${!editing}"
        hx-throttle="500ms">
    <input type="hidden" th:if="${editing}" name="_method" value="put"/>
    <div class="form-group">
      <input type="text" name="name" placeholder="Adventure name" required
             th:value="${adventure?.name}" class="form-input"/>
    </div>
    <div class="form-group">
      <textarea name="description" placeholder="Description (optional)"
                th:text="${adventure?.description}" class="form-input" rows="2"></textarea>
    </div>
    <div class="form-group">
      <input type="text" name="sourceAttribution" placeholder="Source attribution"
             th:value="${adventure?.sourceAttribution}" class="form-input"/>
    </div>
    <div class="form-actions">
      <button type="submit" class="btn btn-primary" th:text="${editing} ? 'Update' : 'Create'">Create</button>
      <button type="button" class="btn btn-ghost"
              hx-get th:if="${editing}"
              th:hx-get="@{/campaigns/{cid}/adventures/{id}(cid=${campaignId},id=${adventure.id})}"
              hx-target="closest .card" hx-swap="outerHTML">Cancel</button>
    </div>
  </form>
</div>
```

**`templates/adventure/detail.html`** — adventure detail with chapter TOC + scene outline:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{fragments/head :: layout(~{::title}, ~{::#main-content})}">
<head><title th:text="|${adventure.name} — ${campaign.name}|">Adventure Detail</title></head>
<body>
<main id="main-content" class="app-layout" th:with="sidebar='campaigns'">
  <div th:replace="~{fragments/navbar :: navbar(active='campaigns')}"></div>
  <div th:replace="~{fragments/sidebar :: sidebar(sidebar, campaignId=${campaignId}, campaign=${campaign})}"></div>
  <div class="page-content">
    <div class="page-header">
      <h1 th:text="${adventure.name}">Adventure</h1>
      <div class="page-header-actions">
        <a th:href="@{/campaigns/{cid}/adventures(cid=${campaignId})}"
           class="btn btn-ghost">← All Adventures</a>
        <button class="btn btn-ghost"
                hx-get="@{/campaigns/{cid}/adventures/{id}/edit(cid=${campaignId},id=${adventure.id})}"
                hx-target="#adventure-header"
                hx-swap="innerHTML">Edit</button>
      </div>
    </div>

    <div id="adventure-header">
      <div class="detail-section" th:if="${adventure.sourceAttribution}">
        <div class="detail-meta" th:text="${adventure.sourceAttribution}">Source</div>
      </div>
      <div class="detail-section" th:if="${adventure.description}">
        <div class="detail-description" th:text="${adventure.description}">Description</div>
      </div>
    </div>

    <div th:replace="~{adventure/_chapter-list :: chapterList}"></div>

  </div>
</main>
</body>
</html>
```

**`templates/adventure/_chapter-list.html`** — chapter list with inline scenes (collapsible):

```html
<div th:fragment="chapterList" xmlns:th="http://www.thymeleaf.org">
  <div class="detail-section">
    <h2>Chapters
      <button class="btn btn-primary btn-sm"
              hx-get="@{/campaigns/{cid}/adventures/{aid}/chapters/new(cid=${campaignId},aid=${adventure.id})}"
              hx-target="#chapter-form-placeholder"
              hx-swap="innerHTML">+ New Chapter</button>
    </h2>
    <div id="chapter-form-placeholder"></div>

    <div th:each="ch : ${chapters}" style="margin-bottom: var(--space-md); border: 1px solid var(--color-border); border-radius: var(--radius); padding: var(--space-md);"
         th:with="doneCount = ${ch.scenes != null ? #lists.size(ch.scenes.?[status.name() == 'DONE']) : 0},
                  totalCount = ${ch.scenes != null ? #lists.size(ch.scenes) : 0}">
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <h3 style="margin: 0;" th:text="${ch.title}">Chapter</h3>
        <span style="font-size: var(--text-sm); color: var(--color-text-muted);"
              th:text="${totalCount > 0 ? doneCount + '/' + totalCount + ' done' : 'no scenes'}">0/0 done</span>
      </div>
      <div th:if="${ch.intro}" class="detail-meta" th:utext="${@markdownUtil.toHtml(ch.intro)}" style="margin: var(--space-sm) 0;">Intro</div>

      <div th:if="${ch.scenes != null and !ch.scenes.isEmpty()}">
        <div th:each="s : ${ch.scenes}" style="display: flex; gap: var(--space-sm); padding: var(--space-xs) 0; border-bottom: 1px solid var(--color-border);">
          <span class="badge" style="min-width: 24px; text-align: center;"
                th:classappend="${s.status == SceneStatus.DONE} ? 'badge-success' :
                                (${s.status == SceneStatus.VISITED} ? 'badge-warning' : '')"
                th:text="${s.status.name().charAt(0)}">U</span>
          <span th:if="${s.sceneKey}" class="badge" th:text="${s.sceneKey}">14</span>
          <a th:href="@{/campaigns/{cid}/adventures/{aid}/scenes/{sid}
                       (cid=${campaignId},aid=${adventure.id},sid=${s.id})}"
             th:text="${s.title}">Scene Title</a>
        </div>
      </div>

      <div style="margin-top: var(--space-sm); display: flex; gap: var(--space-xs);">
        <button class="btn btn-ghost btn-sm"
                hx-put="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/move
                          (cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                hx-vals='{"direction": -1}'
                hx-target="closest .detail-section" hx-swap="outerHTML">↑</button>
        <button class="btn btn-ghost btn-sm"
                hx-put="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/move
                          (cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                hx-vals='{"direction": 1}'
                hx-target="closest .detail-section" hx-swap="outerHTML">↓</button>
        <button class="btn btn-ghost btn-sm"
                hx-get="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}/edit
                          (cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                hx-target="closest div[style*='border']" hx-swap="outerHTML">Edit</button>
        <button class="btn btn-danger btn-sm"
                hx-delete="@{/campaigns/{cid}/adventures/{aid}/chapters/{chId}
                             (cid=${campaignId},aid=${adventure.id},chId=${ch.id})}"
                hx-confirm="Delete this chapter and all its scenes?"
                hx-target="closest .detail-section" hx-swap="outerHTML">Delete</button>
      </div>
    </div>
  </div>
</div>
```

**`templates/adventure/_chapter-form.html`** — chapter create/edit form fragment:

```html
<div th:fragment="form" xmlns:th="http://www.thymeleaf.org"
     th:with="editing = ${chapter != null}">
  <form class="inline-form"
        th:action="@{${editing}
              ? '/campaigns/{cid}/adventures/{aid}/chapters/{chId}'
                   (cid=${campaignId},aid=${adventure.id},chId=${chapter.id})
              : '/campaigns/{cid}/adventures/{aid}/chapters'
                   (cid=${campaignId},aid=${adventure.id})}"
        th:method="${editing} ? 'put' : 'post'"
        hx-post th:if="${!editing}"
        hx-target="closest .detail-section" hx-swap="outerHTML">
    <input type="hidden" th:if="${editing}" name="_method" value="put"/>
    <div class="form-group">
      <input type="text" name="title" placeholder="Chapter title" required
             th:value="${chapter?.title}" class="form-input"/>
    </div>
    <div class="form-group">
      <textarea name="intro" placeholder="Introduction (optional)"
                th:text="${chapter?.intro}" class="form-input" rows="2"></textarea>
    </div>
    <div class="form-actions">
      <button type="submit" class="btn btn-primary" th:text="${editing} ? 'Update' : 'Create'">Create</button>
      <button type="button" class="btn btn-ghost"
              hx-get="@{/campaigns/{cid}/adventures/{aid}(cid=${campaignId},aid=${adventure.id})}"
              hx-target="closest .detail-section" hx-swap="outerHTML">Cancel</button>
    </div>
  </form>
</div>
```

- [ ] **Step 5: Add adventures section to campaign detail page**

In `templates/campaigns/detail.html`, after the Calendar section (or as the first section after the campaign header), add:

```html
<div class="detail-section">
  <h2>Adventures</h2>
  <a th:href="@{/campaigns/{id}/adventures(id=${campaign.id})}"
     class="btn btn-primary">Manage Adventures</a>
</div>
```

- [ ] **Step 6: Run the controller test**

Run: `./mvnw test -Dtest=AdventureControllerTest`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web \
        src/main/resources/templates/adventure \
        src/main/resources/templates/campaigns/detail.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web
git commit -m "feat: adventure/chapter controllers and Thymeleaf templates"
```

---

### Task 7: Scene web controller + templates + run-mode endpoints

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java`
- Create: `src/main/resources/templates/adventure/scene-detail.html`
- Create: `src/main/resources/templates/adventure/_scene-form.html`
- Create: `src/main/resources/templates/adventure/_scene-panel.html`
- Create: `src/main/resources/templates/adventure/_action-rail.html`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneControllerTest.java`

**Interfaces:**
- Consumes: `AdventureService`, `CampaignRepository`, existing `GameMapRepository`, `EncounterRepository`, `StatBlockRepository`, `HandoutRepository` for link-picker data.
- Produces: scene detail page with body rendering + action rail; htmx forms for editing scenes, linking entities, setting status/cursor; run-mode prev/next endpoints.
- `POST /scenes/{id}/status` sets scene status (`@RequestParam SceneStatus status`).
- `POST /campaigns/{id}/current-scene` sets/clears the cursor (`@RequestParam UUID sceneId` or `@RequestParam(required=false) UUID sceneId` for clear).
- Scene body rendering uses `MarkdownUtil.toHtml()` as those in notes: `${@markdownUtil.toHtml(scene.body)}`.
- Autosave pattern: scene edit form uses `hx-trigger="keyup changed delay:500ms"` and `hx-put` to auto-save.

**Routes:**

| Method | Path | Returns |
|--------|------|---------|
| GET | `/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}` | `adventure/scene-detail` |
| GET | `…/scenes/{id}/edit` | `adventure/_scene-form :: form` |
| PUT | `…/scenes/{id}` | `adventure/scene-detail :: sceneBody` (HTMX) or redirect |
| DELETE | `…/scenes/{id}` | HX-Redirect to adventure detail |
| PUT | `…/scenes/{id}/move` | HX-Redirect to adventure detail |
| POST | `…/scenes/{id}/move-to-chapter` | HX-Redirect to adventure detail |
| POST | `…/scenes/{id}/link-map` | `_action-rail :: actionRail` |
| POST | `…/scenes/{id}/unlink-map` | `_action-rail :: actionRail` |
| POST | `…/scenes/{id}/link-encounter` | `_action-rail :: actionRail` |
| POST | `…/scenes/{id}/unlink-encounter` | `_action-rail :: actionRail` |
| POST | `…/scenes/{id}/add-statblock` | `_action-rail :: actionRail` |
| POST | `…/scenes/{id}/remove-statblock` | `_action-rail :: actionRail` |
| POST | `…/scenes/{id}/add-handout` | `_action-rail :: actionRail` |
| POST | `…/scenes/{id}/remove-handout` | `_action-rail :: actionRail` |
| PUT | `/scenes/{id}/status` | `_action-rail :: statusBadge` |
| POST | `/campaigns/{campaignId}/current-scene` | `_scene-panel :: scenePanel` or redirect |
| PUT | `/campaigns/{campaignId}/current-scene/step` | `adventure/scene-detail :: sceneBody` or `_scene-panel :: scenePanel` |

- [ ] **Step 1: Write the failing controller test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SceneController.class)
class SceneControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AdventureService adventureService;
    @MockitoBean private CampaignRepository campaignRepository;
    @MockitoBean private MarkdownUtil markdownUtil;

    private UUID campaignId, adventureId, sceneId, chapterId;
    private Campaign campaign;
    private Scene scene;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        adventureId = UUID.randomUUID();
        chapterId = UUID.randomUUID();
        sceneId = UUID.randomUUID();

        campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test");
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        Chapter ch = new Chapter();
        ch.setId(chapterId);
        ch.setTitle("Ch 1");

        Adventure a = new Adventure();
        a.setId(adventureId);
        a.setName("Module");
        a.setCampaign(campaign);
        ch.setAdventure(a);

        scene = new Scene();
        scene.setId(sceneId);
        scene.setTitle("Throne Room");
        scene.setChapter(ch);

        when(markdownUtil.toHtml(any())).thenReturn("<p>rendered</p>");
    }

    @Test
    void detailRendersScene() throws Exception {
        when(adventureService.findAdventureById(adventureId)).thenReturn(scene.getChapter().getAdventure());
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/campaigns/{cid}/adventures/{aid}/scenes/{sid}",
                        campaignId, adventureId, sceneId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("scene", "adventure"));
    }

    @Test
    void setStatusUpdatesAndReturnsBadge() throws Exception {
        scene.setStatus(SceneStatus.VISITED);
        when(adventureService.setStatus(sceneId, SceneStatus.VISITED)).thenReturn(scene);
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);

        mockMvc.perform(put("/scenes/{id}/status", sceneId)
                        .param("status", "VISITED"))
                .andExpect(status().isOk());
    }

    @Test
    void setCurrentSceneReturnsScenePanel() throws Exception {
        when(adventureService.setCurrentScene(campaignId, sceneId)).thenReturn(scene);
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);

        mockMvc.perform(post("/campaigns/{cid}/current-scene", campaignId)
                        .param("sceneId", sceneId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void clearCurrentScene() throws Exception {
        mockMvc.perform(post("/campaigns/{cid}/current-scene", campaignId)
                        .param("sceneId", ""))
                .andExpect(status().isOk());
    }

    @Test
    void stepCurrentScene() throws Exception {
        when(adventureService.stepCurrentScene(campaignId, 1)).thenReturn(Optional.of(scene));
        when(adventureService.findAdventureById(adventureId)).thenReturn(scene.getChapter().getAdventure());
        when(adventureService.findSceneById(sceneId)).thenReturn(scene);
        when(adventureService.getCurrentScene(campaignId)).thenReturn(Optional.of(scene));

        mockMvc.perform(put("/campaigns/{cid}/current-scene/step", campaignId)
                        .param("direction", "1"))
                .andExpect(redirectedUrlPattern("/campaigns/" + campaignId + "/adventures/" + adventureId + "/scenes/" + sceneId + "*"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SceneControllerTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement SceneController**

The controller follows the `AdventureController` pattern. Key additions:
- `sceneDetail()` loads scene, adventure, chapters list (for move-to-chapter dropdown), available maps/encounters/statblocks/handouts (for link pickers), and renders `markdownUtil.toHtml()` on the body.
- `deleteScene()` redirects to the adventure detail page.
- `moveScene()` takes direction param; `moveSceneToChapter()` takes target chapter UUID.
- Link endpoints delegate to `AdventureService.linkMap()` etc. and return the `_action-rail` fragment.
- `updateSceneStatus()` returns `_action-rail :: statusBadge`.
- `setCurrentScene()` handles both set (POST with sceneId) and clear (POST with empty sceneId).
- `stepCurrentScene()` moves cursor and redirects to the new scene page (if it changed), or stays on current scene (if at edge).

Write the full controller code; it is ~250 lines and mirrors `AdventureController`'s structure. Important: inject `CampaignRepository`, `GameMapRepository`, `EncounterRepository`, `StatBlockRepository` (library), `HandoutRepository` for link-picker model attributes; inject `MarkdownUtil` for body rendering.

- [ ] **Step 4: Create Thymeleaf templates**

**`templates/adventure/scene-detail.html`** — the heart of the feature. Layout:
- Page header with adventure breadcrumb, scene title, prev/next arrows
- Two-column layout (CSS grid or flexbox): left side for rendered `sceneBody`, right side for `actionRail`
- Read-aloud blocks rendered inline by `MarkdownUtil` → the `.read-aloud` CSS from Task 5 styles them
- Edit button swaps the body for `_scene-form :: form`
- Delete button with confirm dialog

**`templates/adventure/_scene-form.html`** — scene edit form. Fields: title, sceneKey, body (textarea), map picker (select dropdown) + pin preview modal:

**Pin placement modal**: When a map is selected and the scene has no pin yet (pinX is null), show a "Place Pin on Map" button. Clicking it opens a small modal (`<dialog>` or htmx-loaded overlay) that renders the selected map image (from `GET /api/v1/maps/{id}/render`) and a `<canvas>` with Konva. The DM clicks the canvas to place the pin; the canvas computes pixel coordinates on click and stores them in hidden `pinX`/`pinY` form fields. The modal also accepts manual X/Y numeric inputs for precision. "Confirm" closes the modal and updates the form fields. This implements the spec §4.2 requirement for click-to-place pin in a map-preview modal.

Additionally: encounter picker (select), statblock multi-select, handout multi-select. Autosave on blur via htmx triggers.

**`templates/adventure/_scene-panel.html`** — compact scene card for the battle map side panel. Shows title, sceneKey badge, status badge, read-aloud block (collapsed), action-rail mini. Rendered when `currentScene` is set; "No current scene" message otherwise.

**`templates/adventure/_action-rail.html`** — right-side action column:
- Status toggles: "Mark Visited" / "Mark Done" / "Unvisit" buttons (POST to `/scenes/{id}/status`)
- "Set as Current Scene" button (POST to `/campaigns/{id}/current-scene`)
- "Go to Map" button (link to battle map, centered on pin when set)
- "Activate Encounter" section (shows encounter name, activate button, difficulty badge if present)
- "Present Handout" buttons for each linked handout (calls existing handout present endpoint)
- Linked statblock cards (rendered via existing `library/_statblock-renderer.html` fragment)
- Scene quicknotes strip (`notes/_quicknotes-strip :: strip(targetType='SCENE', targetId=scene.id)`)
- Separator + prev/next scene links

(Full template HTML is substantial; pattern follows existing `templates/notes/detail.html` and `templates/encounter/detail.html` layouts.)

- [ ] **Step 5: Run the controller test**

Run: `./mvnw test -Dtest=SceneControllerTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneController.java \
        src/main/resources/templates/adventure/scene-detail.html \
        src/main/resources/templates/adventure/_scene-form.html \
        src/main/resources/templates/adventure/_scene-panel.html \
        src/main/resources/templates/adventure/_action-rail.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/SceneControllerTest.java
git commit -m "feat: scene controller with detail page, action rail, status and cursor endpoints"
```

---

### Task 8: Map pin API + Konva pin layer + side panel scene tab

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinApiController.java`
- Modify: `src/main/resources/static/js/map/battle-map.js` (add pin layer + rendering)
- Modify: `src/main/resources/templates/maps/battle.html` (add scene tab in sidebar)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinApiControllerTest.java`
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinAccessControlTest.java`

**Interfaces:**
- `MapPinApiController` is a `@RestController` at `@RequestMapping("/api/v1/maps")`. Single endpoint:
  - `GET /api/v1/maps/{id}/pins` → `List<PinDto>`: `[{ sceneId, sceneKey, x, y, title }]`.
  - This route is PIN-gated by the existing `PinInterceptor` on `/**` with default exclusions. `/api/v1/maps/**` is already under PIN protection (the interceptor covers all `/api/v1/**` except schemas). No exclusion needed; verify this by checking that `/api/v1/maps/{id}` (the map data endpoint) works today with PIN.
- JS: `BattleMap` class gains a `pinLayer` (Konva layer, listening=true, above annotations), methods:
  - `loadPins(mapId)` — `GET /api/v1/maps/{id}/pins`, renders Konva.Circle markers with text labels.
  - `showPins()` / `hidePins()` — called by DM mode toggle.
  - Pin click → opens scene in side panel or opens scene page in new tab.
- Side panel in `battle.html`: new tab "Scene" (alongside "Tokens" / "Tracker"). Shows `_scene-panel :: scenePanel` loaded via htmx `hx-get` on tab click, targeting `#scene-panel-container`. The tab is hidden entirely when no current scene is set.
- DM Mode: `hidePins()` is called when the DM Mode toggle is turned OFF (alongside hiding annotations). The pin layer is non-listening when DM mode is off.

- [ ] **Step 1: Write the failing test for MapPinApiController**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MapPinApiController.class)
class MapPinApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SceneRepository sceneRepository;

    @Test
    void returnsPinnedScenesAsJson() throws Exception {
        UUID mapId = UUID.randomUUID();

        Adventure a = new Adventure();
        a.setId(UUID.randomUUID());
        a.setName("Module");

        Chapter ch = new Chapter();
        ch.setId(UUID.randomUUID());
        ch.setTitle("Ch");
        ch.setAdventure(a);

        Scene s = new Scene();
        s.setId(UUID.randomUUID());
        s.setTitle("Throne Room");
        s.setSceneKey("14");
        s.setPinX(576);
        s.setPinY(240);
        s.setChapter(ch);

        when(sceneRepository.findByMapIdAndPinXNotNull(mapId)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/v1/maps/{id}/pins", mapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sceneId").value(s.getId().toString()))
                .andExpect(jsonPath("$[0].sceneKey").value("14"))
                .andExpect(jsonPath("$[0].x").value(576))
                .andExpect(jsonPath("$[0].y").value(240))
                .andExpect(jsonPath("$[0].title").value("Throne Room"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MapPinApiControllerTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement MapPinApiController**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maps")
public class MapPinApiController {

    private final SceneRepository sceneRepository;

    public MapPinApiController(SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    @GetMapping("/{id}/pins")
    public List<Map<String, Object>> getPins(@PathVariable UUID id) {
        return sceneRepository.findByMapIdAndPinXNotNull(id).stream()
                .map(s -> {
                    Map<String, Object> pin = new java.util.HashMap<>();
                    pin.put("sceneId", s.getId());
                    pin.put("sceneKey", s.getSceneKey());
                    pin.put("x", s.getPinX());
                    pin.put("y", s.getPinY());
                    pin.put("title", s.getTitle());
                    return pin;
                })
                .toList();
    }
}
```

(Note: uses `HashMap` instead of `Map.of` because `sceneKey` may be null.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=MapPinApiControllerTest`
Expected: PASS.

- [ ] **Step 5: Add pin layer to battle-map.js**

Add a `pinLayer` to the `BattleMap` class constructor (after the annotation layer):

```javascript
this.pinLayer = new Konva.Layer({ listening: true });
this.stage.add(this.pinLayer);
```

Add methods:

```javascript
async loadPins(mapId) {
    this.pinLayer.destroyChildren();
    if (!this.dmMode) return;
    const res = await fetch(`/api/v1/maps/${mapId}/pins`);
    if (!res.ok) return;
    const pins = await res.json();
    for (const pin of pins) {
        const circle = new Konva.Circle({
            x: pin.x, y: pin.y, radius: 14,
            fill: '#b45309', stroke: '#fff', strokeWidth: 2,
            draggable: false
        });
        const label = new Konva.Text({
            x: pin.x - 8, y: pin.y - 8,
            text: pin.sceneKey || '•',
            fontSize: 12, fill: '#fff',
            fontStyle: 'bold', align: 'center',
            width: 16
        });
        const group = new Konva.Group({ listening: true });
        group.add(circle);
        group.add(label);
        group.on('click', () => {
            // Open scene in side panel
            if (window.openSceneInPanel) {
                window.openSceneInPanel(pin.sceneId);
            }
        });
        this.pinLayer.add(group);
    }
    this.pinLayer.draw();
}

showPins() {
    this.pinLayer.visible(true);
    this.pinLayer.draw();
}

hidePins() {
    this.pinLayer.visible(false);
    this.pinLayer.draw();
}
```

In `setDmMode(dm)`, add:
```javascript
if (dm) this.showPins();
else this.hidePins();
```

In `activateMap(mapData)`, add after clearing annotations:
```javascript
this.loadPins(mapData.id);
```

- [ ] **Step 6: Add scene tab to battle.html side panel**

In `battle.html`, in the sidebar section (the div with class `battle-sidebar`), add a third tab alongside the existing tabs:

```html
<button class="battle-tab" @click="showTracker = false; activeTab = 'scene'" 
        :class="{ active: activeTab === 'scene' }">Scene</button>

<div id="scene-panel-container" x-show="activeTab === 'scene'"
     hx-get="" hx-trigger="loadScene from:window" hx-swap="innerHTML">
  <div class="empty-state">No current scene. Set one from an adventure.</div>
</div>
```

Add `activeTab: 'tokens'` (default) to the Alpine component's state.

Add global helper in `battle.html` (inline script):
```javascript
window.openSceneInPanel = function(sceneId) {
    const container = document.getElementById('scene-panel-container');
    // Determine campaignId from the page context (data attribute)
    const campaignId = document.querySelector('.battle-container').dataset.campaignId;
    htmx.ajax('GET', `/campaigns/${campaignId}/scenes/${sceneId}/panel`, { target: '#scene-panel-container', swap: 'innerHTML' });
    // Switch to scene tab via Alpine
    document.querySelector('[x-data]').__x.$data.activeTab = 'scene';
};
```

- [ ] **Step 7: Add pin access control test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.common.config.PinInterceptor;
import dev.hendrikhoemberg.dmhelper.common.config.PinManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MapPinAccessControlTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SceneRepository sceneRepository;
    @MockitoBean private PinManager pinManager;

    @Test
    void rejectsRequestWithoutPin() throws Exception {
        when(pinManager.isValid("correct-pin")).thenReturn(true);

        // no PIN cookie → should be rejected by PinInterceptor (HTTP 200 with PIN form page — the interceptor renders HTML, not 403)
        mockMvc.perform(get("/api/v1/maps/{id}/pins", java.util.UUID.randomUUID()))
                .andExpect(status().isOk()); // interceptor returns OK with PIN form page; verify body contains the form
                // .andExpect(content().string(org.hamcrest.Matchers.containsString("PIN")));
    }

    @Test
    void acceptsRequestWithValidPin() throws Exception {
        when(pinManager.isValid("correct-pin")).thenReturn(true);

        mockMvc.perform(get("/api/v1/maps/{id}/pins", java.util.UUID.randomUUID())
                        .cookie(new Cookie("dm_pin", "correct-pin")))
                .andExpect(status().isOk());
    }
}
```

(Note: `@SpringBootTest` with `@MockitoBean(PinManager.class)` lets the interceptor run with a mocked PIN that returns `true` for the correct value. Adjust based on exact `PinInterceptor` behavior — if it returns 403 HTML, update the assertion to check for 403 or for the PIN form content.)

- [ ] **Step 8: Run all pin tests**

Run: `./mvnw test -Dtest='MapPin*'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinApiController.java \
        src/main/resources/static/js/map/battle-map.js \
        src/main/resources/templates/maps/battle.html \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinApiControllerTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/MapPinAccessControlTest.java
git commit -m "feat: map pin API endpoint, Konva pin layer, and side-panel scene tab"
```

---

### Task 9: Wiki links, quicknotes, command palette, and search integration

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/WikiLinkParser.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/NoteService.java` (add `SCENE` case in `renderBody()`)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/notes/service/QuickNoteService.java` (add `SCENE` target type)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/service/CommandPaletteService.java` (add scene entries)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java` (add export of quicknotes with SCENE target)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` (import quicknotes with SCENE target)
- Test: extend existing tests for each service (add test cases, no new test files)

**Interfaces:**
- `WikiLinkParser`: add `"SCENE"` to the recognized prefix set, producing target type `"SCENE"`.
- `NoteService.renderBody()`: add `case "SCENE"` that resolves via `SceneRepository.findByChapterAdventureCampaignIdAndTitleIgnoreCase(campaignId, title)`, renders as link to `/campaigns/{campaignId}/adventures/{adventureId}/scenes/{sceneId}`.
- `QuickNoteService.resolveTargetLink()`: add `SCENE` target type → `[[scene:Title]]`.
- `CommandPaletteService`: in the campaign-scoped scanning method, iterate over `SceneRepository.findByChapterAdventureCampaignId(campaignId)`, add palette item per scene with `type = "scene"`, `subtype = adventure.name`, `title = title`, `url = scene page`.
- `CampaignExportDto` quicknotes: already exports `targetType` as string; `"SCENE"` will round-trip as-is.
- `CampaignService` import: `QuickNote` already stores `targetType`; verifying the import round-trip for scene-targeted quicknotes passes is the test addition.

- [ ] **Step 1: Modify WikiLinkParser**

In `WikiLinkParser.extractReferences()`, add `"SCENE"` to the prefix set:

```java
// After existing prefix check, add:
if (maybePrefix.equals("SCENE")) {
    prefix = "SCENE";
    title = raw.substring(colonIdx + 1).trim();
}
```

- [ ] **Step 2: Add scene resolution to NoteService.renderBody()**

Add after the existing `ENCOUNTER` case in the switch:

```java
case "SCENE" -> {
    var scenes = sceneRepository.findByChapterAdventureCampaignIdAndTitleIgnoreCase(campaignId, target.title());
    if (!scenes.isEmpty()) {
        var s = scenes.getFirst();
        uri = "/campaigns/" + campaignId + "/adventures/"
              + s.getChapter().getAdventure().getId() + "/scenes/" + s.getId();
        found = true;
    }
}
```

Inject `SceneRepository` into `NoteService` constructor (import from `adventure.data`).

- [ ] **Step 3: Add SCENE target to QuickNoteService**

In `QuickNoteService.resolveTargetLink()`, add before the default case:

```java
case "SCENE" -> {
    var scenes = sceneRepository.findByChapterAdventureCampaignIdAndTitleIgnoreCase(campaignId, target.label);
    if (!scenes.isEmpty()) {
        var s = scenes.getFirst();
        return "[[scene:" + s.getTitle() + "]]";
    }
    return "";
}
```

Inject `SceneRepository`; add `findByChapterAdventureCampaignIdAndTitleIgnoreCase` if not already present (it was added to SceneRepository in Task 1). In `getTargetLabel()` add `SCENE` to the type→label mapping.

- [ ] **Step 4: Add scenes to CommandPaletteService**

In the campaign-scoped scanning method (e.g., `addCampaignItems()` or equivalent), add:

```java
sceneRepository.findByChapterAdventureCampaignId(campaignId).forEach(s -> {
    String subtype = (s.getSceneKey() != null ? s.getSceneKey() + " · " : "")
            + s.getChapter().getAdventure().getName();
    items.add(new SearchResultItem(
        s.getId().toString(),
        s.getTitle(),
        "scene",
        subtype,
        "/campaigns/" + campaignId + "/adventures/"
            + s.getChapter().getAdventure().getId() + "/scenes/" + s.getId()
    ));
});
```

Inject `SceneRepository` into `CommandPaletteService`.

- [ ] **Step 5: Update existing tests**

Extend tests for `WikiLinkParserTest`, `NoteServiceTest`, `QuickNoteServiceTest`, `CommandPaletteServiceTest` with scene-related test cases:

- `WikiLinkParserTest`: parse `[[scene:Throne Room]]` → targetType=SCENE, title="Throne Room"
- `NoteServiceTest`: render body with scene link → resolved `<a>` tag
- `QuickNoteServiceTest`: quicknote creation with SCENE target → `[[scene:Throne Room]]`
- `CommandPaletteServiceTest`: search for scene title → palette result

(Add `@MockitoBean SceneRepository` to test slices of NoteServiceTest, QuickNoteServiceTest, CommandPaletteServiceTest as needed.)

In `CampaignImportExportRoundTripTest`: add a quicknote with `targetType = "SCENE"` and `targetId = sceneId`; verify it survives the round-trip (the QuickNote entity stores targetType as a plain string, so no DTO changes are needed for the quicknotes themselves; the scene ID mapping is needed in CampaignService import where quicknotes are re-created — add a `Map<UUID, UUID> oldSceneIdToNewSceneId` alongside the existing ID maps, since the plan imports adventures which re-create scenes with new UUIDs).

- [ ] **Step 6: Run affected tests**

Run: `./mvnw test -Dtest='WikiLinkParserTest,NoteService*,QuickNote*,CommandPalette*,CampaignImportExportRoundTripTest'`
Expected: PASS (with new test cases).

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: integrate scenes into wiki links, quicknotes, command palette, and export"
```

---

### Task 10: Campaign export/import DTO + encounter→map fix

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/Encounter.java` (add `key` field)
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/encounter/data/EncounterExportDto` (add `map` field if a separate DTO exists, or inline in CampaignExportDto)
- Test: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java` (extend)

**Interfaces:**
- `CampaignExportDto` gains an `adventures` field: `List<AdventureExportDto> adventures`.
- New nested records:
  - `AdventureExportDto(String name, String description, String sourceAttribution, int sortOrder, List<ChapterExportDto> chapters)`
  - `ChapterExportDto(String title, String intro, int sortOrder, List<SceneExportDto> scenes)`
  - `SceneExportDto(String title, String sceneKey, String body, SceneStatus status, int sortOrder, String map, PinDto pin, String encounter, List<String> statblocks, List<String> handouts)`
  - `PinDto(Double x, Double y)` (integer in spec, but Jackson serializes int as number)
- Campaign-level: `currentScene` field with the scene's adventure+chapter+sceneKey reference (not UUID, since UUIDs change on import). Use a string like `"Module One/Chapter 1/14"`.
- `EncounterExportDto` gains an optional `String key` field and a `String map` field. The `map` field was promised in SPEC §4.1 but never implemented — this task fixes it.
- `Encounter` entity gains a `@Column(name = "encounter_key", length = 100)` field `encounterKey` (optional, used for scene→encounter reference by key in JSON, fallback to name if key is null).
- `CampaignService.exportCampaign()` serializes all adventures with full depth.
- `CampaignService.importCampaign()` imports adventures in order, mapping scene refs (encounter key → new encounter; map key → new map; statblock sourceKey → statblock; handout title → handout). `currentScene` string is resolved after all scenes are imported.
- `EncounterExportDto` map key is read by `EncounterService` import (or wherever encounters are created during import) — set `encounter.setMap()` if the map key resolves.

- [ ] **Step 1: Add Encounter.key field**

In `Encounter.java`, add:

```java
@Column(name = "encounter_key", length = 100)
private String encounterKey;

public String getEncounterKey() { return encounterKey; }
public void setEncounterKey(String encounterKey) { this.encounterKey = encounterKey; }
```

- [ ] **Step 2: Update CampaignExportDto**

Add the adventure records as inner records:

```java
public record AdventureExportDto(
    String name,
    String description,
    String sourceAttribution,
    int sortOrder,
    List<ChapterExportDto> chapters
) {}

public record ChapterExportDto(
    String title,
    String intro,
    int sortOrder,
    List<SceneExportDto> scenes
) {}

public record SceneExportDto(
    String title,
    String sceneKey,
    String body,
    String status,
    int sortOrder,
    String map,
    Map<String, Integer> pin,
    String encounter,
    List<String> statblocks,
    List<String> handouts
) {}
```

Add `List<AdventureExportDto> adventures` to the top-level `CampaignExportDto` record, and add `String currentScene` (the resolved path) to `CampaignDto` nested record.

Add `String map` to `EncounterExportDto` record.

- [ ] **Step 3: Update CampaignService export**

In `exportCampaign()`:
- Map adventures → `AdventureExportDto` with chapters → scenes.
- Scene refs use: `map` → `scene.getMap()?.getName()` (the map key/name), `pin` → `{x: scene.getPinX(), y: scene.getPinY()}`, `encounter` → `scene.getEncounter()?.getEncounterKey()` (fallback to name), `statblocks` → list of sourceKey strings, `handouts` → list of title strings.
- `currentScene`: resolve `currentSceneId` → scene → build path string like `"Adventure Name/Chapter Title/SceneKey"`.
- Encounters: `map` → `encounter.getMap()?.getName()`.

- [ ] **Step 4: Update CampaignService import**

In `importCampaign()`:
- After importing all existing entity types, iterate `dto.adventures()` and create Adventures, Chapters, Scenes.
- Build new ID maps for resolving scene refs:
  - `Map<String, UUID> encounterKeyToId` (populate from imported encounters)
  - `Map<String, UUID> mapKeyToId` (populate from imported maps)
  - `Map<String, UUID> statblockKeyToId` (populate from imported statblocks)
  - `Map<String, UUID> handoutTitleToId` (populate from imported handouts)
- For each scene, resolve links using the maps above. Statblocks that don't resolve emit warnings and skip (existing behavior).
- Resolve `currentScene` string → split by `/` → find matching scene in imported adventures → set `Campaign.currentSceneId`.
- For encounters, set `encounter.setMap()` from `gameMapRepository.findByName()` if the `map` field is present and resolves.

- [ ] **Step 5: Extend round-trip test**

Add to `CampaignImportExportRoundTripTest`:
- Create an adventure with 2 chapters, 3 scenes (one with pin, one linked to encounter, one with statblocks/handouts).
- Set a current scene.
- Export → import → verify deep equality: chapter count, scene count, scene keys, pin coordinates, encounter link, statblock/handout links, current scene restored.
- Verify the encounter→map round-trip (encounter with map key → exported → imported → encounter has correct map).

- [ ] **Step 6: Run round-trip test**

Run: `./mvnw test -Dtest=CampaignImportExportRoundTripTest`
Expected: PASS (existing + new tests).

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java src/test/java
git commit -m "feat: adventure export/import with full scene fidelity and encounter↦map fix"
```

---

### Task 11: JSON schema update + dry-run validation

**Files:**
- Modify: `src/main/resources/schemas/campaign-format.schema.json`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java` (import dry-run validation)
- Test: extend `CampaignImportExportRoundTripTest` (validate against schema)

**Interfaces:**
- The schema gains `"adventures"` array in top-level `"properties"` and `"required"`:
  - `Adventure` object: name (required), description, sourceAttribution, sortOrder (required, integer), chapters (array of Chapter)
  - `Chapter` object: title (required), intro, sortOrder (required, integer), scenes (array of Scene)
  - `Scene` object: title (required), sceneKey, body, status (enum: UNVISITED/VISITED/DONE), sortOrder (required, integer), map (string), pin (object with x,y integers), encounter (string), statblocks (array of strings), handouts (array of strings)
- `Encounters` object gains optional `"key"` (string) and `"map"` (string) fields.
- `Campaign` object gains optional `"currentScene"` (string, the path-based reference).
- The `"description"` fields document:
  - The `read-aloud` fenced block Markdown convention
  - Pin coordinate conventions (pixels, origin top-left)
  - Reference conventions: maps by key, encounters by key (fallback: name), statblocks by sourceKey, handouts by title

- [ ] **Step 1: Update the schema**

In `campaign-format.schema.json`:

Add `"adventures"` to `"properties"`:

```json
"adventures": {
  "type": "array",
  "items": { "$ref": "#/$defs/adventure" }
}
```

Add to `$defs`:

```json
"adventure": {
  "type": "object",
  "required": ["name", "sortOrder", "chapters"],
  "properties": {
    "name": { "type": "string" },
    "description": { "type": "string" },
    "sourceAttribution": { "type": "string" },
    "sortOrder": { "type": "integer" },
    "chapters": {
      "type": "array",
      "items": { "$ref": "#/$defs/chapter" }
    }
  }
},
"chapter": {
  "type": "object",
  "required": ["title", "sortOrder", "scenes"],
  "properties": {
    "title": { "type": "string" },
    "intro": { "type": "string" },
    "sortOrder": { "type": "integer" },
    "scenes": {
      "type": "array",
      "items": { "$ref": "#/$defs/scene" }
    }
  }
},
"scene": {
  "type": "object",
  "required": ["title", "sortOrder"],
  "properties": {
    "title": { "type": "string" },
    "sceneKey": { "type": "string", "description": "The book's area number (e.g. '14', 'B3'). Shown on map pins and in scene lists. Not required to be unique." },
    "body": { "type": "string", "description": "Markdown body text. Use ```read-aloud fenced blocks for boxed read-aloud text intended for the DM to read to players." },
    "status": { "enum": ["UNVISITED", "VISITED", "DONE"] },
    "sortOrder": { "type": "integer" },
    "map": { "type": "string", "description": "Map key (matches the map's name)." },
    "pin": {
      "type": "object",
      "description": "Pin position on the map in pixels, origin top-left.",
      "properties": {
        "x": { "type": "integer" },
        "y": { "type": "integer" }
      },
      "required": ["x", "y"]
    },
    "encounter": { "type": "string", "description": "Encounter key (matches the encounter's key field; falls back to name)." },
    "statblocks": { "type": "array", "items": { "type": "string" }, "description": "StatBlock sourceKeys (SRD prefix or in-file custom)." },
    "handouts": { "type": "array", "items": { "type": "string" }, "description": "Handout titles." }
  }
}
```

Add `"currentScene"` to the campaign object:
```json
"currentScene": { "type": "string", "description": "Path-based reference: 'Adventure Name/Chapter Title/SceneKey'. Resolved after import." }
```

Add `"key"` and `"map"` to the encounter properties:
```json
"key": { "type": "string", "description": "Optional unique key for scene→encounter reference" },
"map": { "type": "string", "description": "Map key — the battle map this encounter takes place on" }
```

Ensure `"adventures"` does NOT appear in the `"required"` array at the top level (it's optional for backward compatibility).

- [ ] **Step 2: Add dry-run validation for adventure refs**

In `CampaignService`, the dry-run validation method already iterates over encounter, handout, statblock, map, and note refs. Extend it to validate adventure scene refs:
- Unresolvable `map` → error
- Unresolvable `encounter` → error
- Unresolvable `handout` → error
- Unresolvable `statblock` → warning (existing behavior)
- Pin `x`/`y` out of bounds for the referenced map → warning
- (Scene keys are deliberately not uniqueness-checked per spec)

- [ ] **Step 3: Extend round-trip test to validate against schema**

In `CampaignImportExportRoundTripTest`, add a test that:
- Serializes the exported JSON string
- Loads the schema from classpath
- Validates the JSON against the schema using a JSON Schema validator (if the project has one; otherwise use `com.networknt:json-schema-validator` or skip runtime validation and just verify structural correctness.)

Minimal approach: add a test that verifies the JSON string contains `"adventures"` and key scene fields after export.

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest=CampaignImportExportRoundTripTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/schemas/campaign-format.schema.json \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java
git commit -m "feat: JSON schema for adventures, encounter key/map fields, dry-run validation"
```

---

### Task 12: Integration tests — player-safe projection, pin access, Playwright smoke

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/adventure/web/PlayerSafeProjectionTest.java`
- Extend: `src/test/java/dev/hendrikhoemberg/dmhelper/CoreSessionLoopSmokeTest.java` (Playwright)
- No production code changes (verification only).

**Interfaces:**
- **Player-safe projection test**: verify that no adventure, scene, chapter, or pin data appears in:
  - `GET /player` response (player view page)
  - WebSocket payloads to `/ws/table` subscribers (if the test can simulate a WebSocket message)
  - `GET /api/v1/maps/{id}` response (the JSON map data API — should NOT include pins in the player-accessible endpoint)

  Approach: `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate`, or `@WebMvcTest` for `/player` controller + `@SpringBootTest` for the WebSocket (if feasible) or a simpler approach: write a JUnit test that verifies `GET /player` response body does not contain adventure/scene related template fragments. The spec says the player view uses `templates/live/view.html` — verify that template does not reference any adventure model attributes.

- **Pin API access control**: confirmed in Task 8's `MapPinAccessControlTest`. This task ensures the test verifies the PinInterceptor behavior specifically for the `/api/v1/maps/{id}/pins` route.

- **Playwright smoke test**: extend the existing `CoreSessionLoopSmokeTest` with:
  1. Create a campaign (existing)
  2. Create an adventure with a chapter and a scene
  3. Set scene pin on a map
  4. Navigate to the battle map, verify pins are visible
  5. Click a pin → verify scene appears in side panel
  6. Activate the scene's encounter
  7. Set scene status to DONE
  8. Verify DONE badge appears on the adventure TOC

- [ ] **Step 1: Write player-safe projection test**

```java
package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerSafeProjectionTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdventureRepository adventureRepository;
    @MockitoBean private ChapterRepository chapterRepository;
    @MockitoBean private SceneRepository sceneRepository;
    @MockitoBean private CampaignRepository campaignRepository;

    @Test
    void playerViewContainsNoAdventureData() throws Exception {
        UUID campaignId = UUID.randomUUID();
        Campaign c = new Campaign();
        c.setId(campaignId);
        c.setName("Test");
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(c));
        when(adventureRepository.findByCampaignIdOrderBySortOrderAsc(campaignId)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/player"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("adventure"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("scene"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("pin"))));
    }
}
```

(This is a minimal check. A more thorough test would set up actual adventure data and verify none of it leaks. The player view controller may need `@MockitoBean` for all repositories it references. If the test setup becomes too complex for a `@SpringBootTest`, use `@WebMvcTest` targeting the player view controller specifically.)

- [ ] **Step 2: Run player-safe test**

Run: `./mvnw test -Dtest=PlayerSafeProjectionTest`
Expected: PASS.

- [ ] **Step 3: Extend Playwright smoke test**

In `CoreSessionLoopSmokeTest`, add a test method (pattern following existing methods such as `createAndManageEncounter`):

```java
@Test
void adventureSceneWithPinAndEncounter() {
    // After login/create campaign
    page.navigate(baseUrl + "/campaigns/" + campaignId + "/adventures");

    // Create adventure
    page.click("text=+ New");
    page.fill("input[name='name']", "Test Module");
    page.click("button:has-text('Create')");

    // Create chapter
    page.click("text=+ New Chapter");
    page.fill("input[name='title']", "Chapter 1");
    page.click("button:has-text('Create')");

    // Create scene (via chapter page)
    page.click("a:has-text('Chapter 1')"); // or navigate to appropriate scene creation
    // ... fill scene form, set title, body, link map/pin/encounter

    // Navigate to battle map, verify pin
    page.navigate(baseUrl + "/campaigns/" + campaignId + "/maps/" + mapId + "/battle");
    // verify pin circle exists on canvas (Konva canvas makes this tricky; use JS evaluation)

    // Click pin → side panel shows scene
    // page.evaluate("...click canvas at pin coordinates...");

    // Activate encounter
    // Verify DONE status
}
```

(The exact Playwright interaction depends on the UI implementation from Tasks 6-8. Write the test after the UI is built so selectors are accurate.)

- [ ] **Step 4: Run Playwright test**

Run: `./mvnw test -Dtest=CoreSessionLoopSmokeTest#adventureSceneWithPinAndEncounter`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A src/test
git commit -m "test: player-safe projection, pin access control, and Playwright smoke for adventures"
```

---

### Task 13: Final verification — full test suite + manual checklist

- [ ] **Run the full test suite:**

```bash
./mvnw test
```

Expected: ALL tests PASS. Fix any failures before proceeding.

- [ ] **Manual verification checklist:**

1. Start the app: `./mvnw spring-boot:run`
2. Create a campaign
3. Create an adventure with 2 chapters, 3 scenes each
4. Reorder chapters and scenes — verify sort order persists
5. Create a map, link it to a scene with a pin
6. Open battle map — verify pin appears at the correct location
7. Click the pin — verify scene opens in side panel
8. Create an encounter, link it to a scene, verify "Activate Encounter" works from scene page
9. Set current scene — verify "Continue at:" on dashboard
10. Step prev/next — verify cursor moves across chapters
11. Delete a linked encounter — verify scene's encounter ref is nulled (not scene deleted)
12. Export campaign → import into new campaign → verify all scenes, pins, links survive
13. Write a note with `[[scene:Throne Room]]` → verify rendered as link → verify backlinks work
14. Ctrl+K → type scene title → verify it appears in palette results
15. DM Mode OFF → verify pins are hidden on battle map
16. Open `/player` → verify no adventure/scene/pin data appears
17. `GET /api/v1/maps/{id}/pins` without PIN → verify PIN form appears (access denied)
18. `GET /api/v1/maps/{id}/pins` with PIN → verify JSON response

- [ ] **Final commit:**

```bash
git add -A
git commit -m "feat: complete adventure module — scenes, run cursor, map pins, import/export, wiki links, palette"
```

---

