# M4: Map Editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a usable tavern map from scratch (SPEC §4.3, milestone M4).

**Architecture:** A `GameMap` JPA entity (ManyToOne to Campaign) carries grid config, a reserved `gridType` (§9), a JPA `@Version` for optimistic document saves (§5), and a `document` JSON blob (the `MapDocument` schema: layers, painted cells, shapes, semantic primitives, custom terrain palette entries). The map editor is a **Konva.js canvas island** — a vanilla JS ES module at `static/js/map/map-editor.js` loaded into a Thymeleaf page, talking JSON to `/api/v1/maps/{id}/document` for autosave (2-second debounce, whole-document replace with optimistic version check). The island has tools (brush / rect / circle / line / freehand polygon / select with copy-paste), snap-to-grid with an unsnapped option, layer management (terrain/objects/annotations with hide/lock), an extensible terrain palette, undo/redo, keyboard shortcuts, and pan/zoom. Campaign export **and import** are extended with map data. The surrounding pages (map list, CRUD) use Thymeleaf + htmx like every other module; the toolbar uses Alpine.js per §2.1.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, H2, Thymeleaf + htmx, Alpine.js (vendored), Konva.js 9.3.18 (vendored), Jackson 3, JUnit 5 + Mockito + AssertJ

## Global Constraints

Copied from SPEC.md — every task's requirements implicitly include these:

- **Spring Boot 4.1.0 / Java 25.** Never downgrade a dependency to fit a Boot-3-era example; port the example forward (§2.1).
- **Jackson 3, not Jackson 2** (§2.1): databind classes come from `tools.jackson.databind` (`ObjectMapper`, `JsonMapper.builder()`); **annotations stay in `com.fasterxml.jackson.annotation`** (`@JsonProperty`, `@JsonInclude`) — that package is retained in Jackson 3. Any `com.fasterxml.jackson.databind` or `tools.jackson.annotation` import is a bug. Jackson 3 exceptions are unchecked.
- **Boot 4 test-slice packages** (verified against this codebase — the Boot-3 packages do not exist here):
  - `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
  - `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
  - `org.springframework.test.context.bean.override.mockito.MockitoBean`
- **No npm, no CDN** (§2.4). `konva.min.js`, `alpine.min.js`, `htmx.min.js` are already vendored under `static/vendor/`.
- **Thymeleaf does not process raw `hx-*` attributes.** URL expressions in htmx attributes must use `th:attr="hx-get=@{…}"`. (Some existing templates, e.g. `party/list.html:17`, use raw `hx-get="@{…}"` — that is a pre-existing bug; do **not** copy the pattern, and do not fix it in this plan.)
- **Errors are RFC 9457 problem+json** (§5), produced centrally by `common/web/GlobalExceptionHandler` (htmx-aware). Controllers do not carry their own `@ExceptionHandler`s.
- **App port is 8081** (`application.properties`).
- **JS islands carry JSDoc annotations** (§2.1).
- No D&D rules content appears in M4, so the provenance rule (§2.3.8) imposes nothing here — terrain names/colors are not rules data.

---

### Task 1: Create GameMap entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMap.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMapRepository.java`

**Interfaces:**
- Produces: `GameMap` entity (getters/setters for `id`, `campaign`, `name`, `gridWidth`, `gridHeight`, `cellSizePx`, `sortOrder`, `gridType`, `version`, `document`); `GameMapRepository.findByCampaignIdOrderBySortOrderAsc(UUID)`, `GameMapRepository.countByCampaignId(UUID)` returning `long`.

- [ ] **Step 1: Create gamemap package directories**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data`

- [ ] **Step 2: Write GameMap entity**

`gridType` is a reserved field per §9 ("the `GameMap` entity and map document schema should reserve a future `gridType` field") — always `"SQUARE"` in v1. `version` is the JPA optimistic-lock counter behind the §5 "whole-document replace with optimistic version check". `setVersion` exists so controller tests can stub versions on mocks; Hibernate manages it at runtime.

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMap.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "game_map", indexes = {
    @Index(name = "idx_gamemap_campaign", columnList = "campaign_id"),
})
public class GameMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private int gridWidth = 30;

    @Column(nullable = false)
    private int gridHeight = 20;

    @Column(nullable = false)
    private int cellSizePx = 48;

    @Column(nullable = false)
    private int sortOrder;

    /** Reserved for post-v1 hex support (SPEC §9); always "SQUARE" in v1. */
    @Column(nullable = false, length = 16)
    private String gridType = "SQUARE";

    /** Optimistic-lock counter for whole-document replaces (SPEC §5). */
    @Version
    private long version;

    @Column(columnDefinition = "CLOB")
    private String document;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getGridWidth() { return gridWidth; }
    public void setGridWidth(int gridWidth) { this.gridWidth = gridWidth; }

    public int getGridHeight() { return gridHeight; }
    public void setGridHeight(int gridHeight) { this.gridHeight = gridHeight; }

    public int getCellSizePx() { return cellSizePx; }
    public void setCellSizePx(int cellSizePx) { this.cellSizePx = cellSizePx; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getGridType() { return gridType; }
    public void setGridType(String gridType) { this.gridType = gridType; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public String getDocument() { return document; }
    public void setDocument(String document) { this.document = document; }
}
```

- [ ] **Step 3: Write GameMapRepository**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMapRepository.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GameMapRepository extends JpaRepository<GameMap, UUID> {

    List<GameMap> findByCampaignIdOrderBySortOrderAsc(UUID campaignId);

    long countByCampaignId(UUID campaignId);
}
```

- [ ] **Step 4: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/ && git commit -m "feat: add GameMap entity (with reserved gridType and optimistic version) and repository"
```

---

### Task 2: Create MapDocument DTO and MapLayer DTO

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentDto.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapLayerDto.java`

**Interfaces:**
- Produces: `MapDocumentDto(int schemaVersion, GridDto grid, List<MapLayerDto> layers, List<PrimitiveDto> primitives, List<TerrainDefDto> customTerrain)` with `createDefault(int, int, int)` and `CURRENT_SCHEMA_VERSION = 1`; `MapLayerDto(String id, String name, LayerType type, Boolean visible, Boolean locked, List<CellDto> cells, List<ShapeDto> shapes)` with factory methods `createTerrainLayer()`, `createObjectsLayer()`, `createAnnotationsLayer()`.

The `MapDocument` is the JSON blob stored on `GameMap.document`. Per §4.3 it carries, besides layers of painted cells and shapes: **semantic primitives** (`ROOM`/`CORRIDOR`/`DOOR`/`REGION`, expanded to cells on render — the way AI-generated maps avoid emitting hundreds of coordinates) and **custom terrain palette entries** (name + color + walkable flag). The layer `type` enum reserves `IMAGE` so post-v1 image backgrounds don't break the format (§4.3), and the grid reserves `gridType` (§9).

- [ ] **Step 1: Create service package**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service`

- [ ] **Step 2: Write MapDocumentDto**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentDto.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapDocumentDto(
        @JsonProperty(required = true) int schemaVersion,
        @JsonProperty(required = true) GridDto grid,
        List<MapLayerDto> layers,
        List<PrimitiveDto> primitives,
        List<TerrainDefDto> customTerrain
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public MapDocumentDto {
        layers = layers != null ? layers : List.of();
        primitives = primitives != null ? primitives : List.of();
        customTerrain = customTerrain != null ? customTerrain : List.of();
    }

    public static MapDocumentDto createDefault(int gridWidth, int gridHeight, int cellSizePx) {
        return new MapDocumentDto(
                CURRENT_SCHEMA_VERSION,
                new GridDto(gridWidth, gridHeight, cellSizePx, "square"),
                List.of(
                        MapLayerDto.createTerrainLayer(),
                        MapLayerDto.createObjectsLayer(),
                        MapLayerDto.createAnnotationsLayer()
                ),
                List.of(),
                List.of()
        );
    }

    /** gridType is reserved for post-v1 hex support (SPEC §9); always "square" in v1. */
    public record GridDto(int width, int height, int cellSizePx, String gridType) {
        public GridDto {
            gridType = gridType != null ? gridType : "square";
        }
    }

    /** Semantic map primitive (SPEC §4.3), expanded to cells on render. The editor emits
     *  painted cells; primitives exist chiefly so generated maps can say
     *  "room from (2,2) to (10,8)" instead of hundreds of coordinates. Coordinates are
     *  grid cells, origin top-left, col before row. */
    public record PrimitiveDto(
            @JsonProperty(required = true) String type,   // ROOM | CORRIDOR | DOOR | REGION
            int startCol, int startRow,
            int endCol, int endRow,
            String terrain   // REGION fill terrain key; ignored by other types
    ) {}

    /** Custom terrain palette entry (SPEC §4.3: palette extensible with
     *  name + color + walkable flag). Stored per map in its document. */
    public record TerrainDefDto(
            @JsonProperty(required = true) String key,
            @JsonProperty(required = true) String name,
            @JsonProperty(required = true) String fill,   // CSS hex color
            boolean walkable
    ) {}
}
```

- [ ] **Step 3: Write MapLayerDto**

`visible`/`locked` are `Boolean` normalized in the compact constructor so hand-authored or generated documents that omit them default to visible/unlocked instead of silently hiding a layer.

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapLayerDto.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapLayerDto(
        @JsonProperty(required = true) String id,
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) LayerType type,
        Boolean visible,
        Boolean locked,
        List<CellDto> cells,
        List<ShapeDto> shapes
) {
    /** IMAGE is reserved for the post-v1 image-background layer (SPEC §4.3) — no v1 renderer. */
    public enum LayerType { TERRAIN, OBJECTS, ANNOTATIONS, IMAGE }

    public MapLayerDto {
        visible = visible != null ? visible : Boolean.TRUE;
        locked = locked != null ? locked : Boolean.FALSE;
        cells = cells != null ? cells : List.of();
        shapes = shapes != null ? shapes : List.of();
    }

    public static MapLayerDto createTerrainLayer() {
        return new MapLayerDto("terrain", "Terrain", LayerType.TERRAIN, true, false, List.of(), List.of());
    }

    public static MapLayerDto createObjectsLayer() {
        return new MapLayerDto("objects", "Objects", LayerType.OBJECTS, true, false, List.of(), List.of());
    }

    public static MapLayerDto createAnnotationsLayer() {
        return new MapLayerDto("annotations", "Annotations (DM only)", LayerType.ANNOTATIONS, true, false, List.of(), List.of());
    }

    /** A single painted cell on a terrain layer. Cells not present in the array are "floor" / default. */
    public record CellDto(int col, int row, String terrain) {}

    /** A shape on a layer. Coordinates are in grid-cell units, origin top-left, col (x) before row (y).
     *  rect: [x, y, width, height] · circle: [cx, cy, radius] · line: [x1, y1, x2, y2] ·
     *  polygon: flat [x1, y1, x2, y2, x3, y3, …]. strokeWidth is in screen pixels. */
    public record ShapeDto(
            @JsonProperty(required = true) String type,   // rect | circle | line | polygon
            List<Double> points,
            String fill,
            String stroke,
            double strokeWidth,
            String label
    ) {
        public ShapeDto {
            points = points != null ? points : List.of();
        }
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentDto.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapLayerDto.java
git commit -m "feat: add MapDocument and MapLayer DTOs with primitives, custom terrain, and reserved slots"
```

---

### Task 3: Write GameMapService tests (TDD — red phase)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`

**Interfaces:**
- Consumes: `GameMap`, `MapDocumentDto` from Tasks 1–2.
- Produces (by specifying them): the `GameMapService` signatures Task 4 must implement, and `dev.hendrikhoemberg.dmhelper.common.NotFoundException`.

- [ ] **Step 1: Create test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service`

- [ ] **Step 2: Write failing tests**

Note the Boot 4 `@DataJpaTest` import and the `em.persist` campaign setup — both match `party/service/PartyMemberServiceTest.java`. Text blocks always put the opening `"""` on its own line (content on the same line is a compile error).

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(GameMapService.class)
class GameMapServiceTest {

    @Autowired private GameMapService service;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldCreateMapWithDefaultDocument() {
        GameMap map = service.create(campaign.getId(), "Tavern", 30, 20, 48);

        assertThat(map.getId()).isNotNull();
        assertThat(map.getName()).isEqualTo("Tavern");
        assertThat(map.getGridWidth()).isEqualTo(30);
        assertThat(map.getGridHeight()).isEqualTo(20);
        assertThat(map.getCellSizePx()).isEqualTo(48);
        assertThat(map.getSortOrder()).isEqualTo(0);
        assertThat(map.getGridType()).isEqualTo("SQUARE");
        assertThat(map.getDocument()).contains("\"schemaVersion\"");

        MapDocumentDto doc = service.getDocument(map.getId());
        assertThat(doc.schemaVersion()).isEqualTo(1);
        assertThat(doc.grid().width()).isEqualTo(30);
        assertThat(doc.grid().height()).isEqualTo(20);
        assertThat(doc.grid().gridType()).isEqualTo("square");
        assertThat(doc.layers()).hasSize(3);
        assertThat(doc.layers().get(0).id()).isEqualTo("terrain");
        assertThat(doc.layers().get(1).id()).isEqualTo("objects");
        assertThat(doc.layers().get(2).id()).isEqualTo("annotations");
        assertThat(doc.primitives()).isEmpty();
        assertThat(doc.customTerrain()).isEmpty();
    }

    @Test
    void shouldAssignIncrementingSortOrders() {
        service.create(campaign.getId(), "Map 1", 20, 15, 48);
        GameMap map2 = service.create(campaign.getId(), "Map 2", 20, 15, 48);
        assertThat(map2.getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldFindByCampaignOrdered() {
        service.create(campaign.getId(), "Temple", 20, 15, 48);
        service.create(campaign.getId(), "Cave", 20, 15, 48);

        var maps = service.findByCampaignId(campaign.getId());
        assertThat(maps).hasSize(2);
        assertThat(maps.get(0).getSortOrder()).isLessThan(maps.get(1).getSortOrder());
    }

    @Test
    void shouldUpdateDocumentAndBumpVersion() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        long initialVersion = map.getVersion();
        String newDoc = """
                {"schemaVersion":1,"grid":{"width":20,"height":15,"cellSizePx":48,"gridType":"square"},"layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","visible":true,"locked":false,"cells":[{"col":0,"row":0,"terrain":"wall"}],"shapes":[]}]}""";

        long newVersion = service.updateDocument(map.getId(), newDoc, initialVersion);

        assertThat(newVersion).isEqualTo(initialVersion + 1);
        assertThat(service.findById(map.getId()).getDocument()).isEqualTo(newDoc);
    }

    @Test
    void shouldRejectStaleVersion() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        String doc = """
                {"schemaVersion":1,"grid":{"width":20,"height":15,"cellSizePx":48},"layers":[]}""";

        assertThatThrownBy(() -> service.updateDocument(map.getId(), doc, map.getVersion() + 7))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldRejectInvalidDocumentSchema() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        String badDoc = """
                {"schemaVersion":99,"grid":{"width":20,"height":15,"cellSizePx":48},"layers":[]}""";

        assertThatThrownBy(() -> service.updateDocument(map.getId(), badDoc, map.getVersion()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void shouldPreservePrimitivesAndCustomTerrain() {
        GameMap map = service.create(campaign.getId(), "Dungeon", 20, 15, 48);
        String doc = """
                {"schemaVersion":1,
                 "grid":{"width":20,"height":15,"cellSizePx":48,"gridType":"square"},
                 "layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","cells":[],"shapes":[]}],
                 "primitives":[{"type":"ROOM","startCol":2,"startRow":2,"endCol":10,"endRow":8},
                               {"type":"DOOR","startCol":10,"startRow":5,"endCol":10,"endRow":5}],
                 "customTerrain":[{"key":"moss","name":"Moss","fill":"#2a6e3a","walkable":true}]}""";

        service.updateDocument(map.getId(), doc, map.getVersion());

        MapDocumentDto parsed = service.getDocument(map.getId());
        assertThat(parsed.primitives()).hasSize(2);
        assertThat(parsed.primitives().get(0).type()).isEqualTo("ROOM");
        assertThat(parsed.customTerrain()).hasSize(1);
        assertThat(parsed.customTerrain().get(0).key()).isEqualTo("moss");
        // omitted visible/locked flags default to visible & unlocked
        assertThat(parsed.layers().get(0).visible()).isTrue();
        assertThat(parsed.layers().get(0).locked()).isFalse();
    }

    @Test
    void shouldDeleteMapAndReorder() {
        service.create(campaign.getId(), "Map 1", 20, 15, 48);
        GameMap map2 = service.create(campaign.getId(), "Map 2", 20, 15, 48);
        service.create(campaign.getId(), "Map 3", 20, 15, 48);

        service.delete(map2.getId());

        var maps = service.findByCampaignId(campaign.getId());
        assertThat(maps).hasSize(2);
        assertThat(maps.get(0).getSortOrder()).isEqualTo(0);
        assertThat(maps.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldUpdateNameAndDimensions() {
        GameMap map = service.create(campaign.getId(), "Original", 20, 15, 48);
        GameMap updated = service.update(map.getId(), "Renamed", 40, 30, 64);

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getGridWidth()).isEqualTo(40);
        assertThat(updated.getGridHeight()).isEqualTo(30);
        assertThat(updated.getCellSizePx()).isEqualTo(64);
    }

    @Test
    void shouldThrowWhenMapNotFound() {
        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Map not found");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -Dtest=GameMapServiceTest`
Expected: Compilation fails — `GameMapService` and `NotFoundException` not found

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java && git commit -m "test: add GameMapService tests (TDD red phase)"
```

---

### Task 4: Implement NotFoundException, exception handlers, and GameMapService (TDD — green phase)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/common/NotFoundException.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java`

**Interfaces:**
- Consumes: `GameMap`, `GameMapRepository`, `MapDocumentDto` (Tasks 1–2), existing `CampaignRepository`.
- Produces: `NotFoundException` (→ HTTP 404), `OptimisticLockingFailureException` handling (→ HTTP 409), and `GameMapService` with:
  - `GameMap create(UUID campaignId, String name, int gridWidth, int gridHeight, int cellSizePx)`
  - `GameMap findById(UUID id)` — throws `NotFoundException`
  - `List<GameMap> findByCampaignId(UUID campaignId)`
  - `MapDocumentDto getDocument(UUID mapId)`
  - `long updateDocument(UUID mapId, String documentJson, long expectedVersion)` — returns new version
  - `GameMap update(UUID mapId, String name, int gridWidth, int gridHeight, int cellSizePx)`
  - `void delete(UUID mapId)`

REST semantics: a missing map is **404** (`NotFoundException`), a stale document version is **409** (`OptimisticLockingFailureException`), and bad input — including an unknown campaign on create, matching the existing services' convention — stays **400** (`IllegalArgumentException`).

- [ ] **Step 1: Write NotFoundException**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/common/NotFoundException.java`:

```java
package dev.hendrikhoemberg.dmhelper.common;

/** Missing domain entity → HTTP 404 via GlobalExceptionHandler. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Extend GlobalExceptionHandler**

In `src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java`, add two handlers after the existing `handleIllegalArgument` method (add the imports `dev.hendrikhoemberg.dmhelper.common.NotFoundException` and `org.springframework.dao.OptimisticLockingFailureException`):

```java
    @ExceptionHandler(NotFoundException.class)
    public Object handleNotFound(NotFoundException ex, HttpServletRequest request) {
        if ("true".equals(request.getHeader("HX-Request"))) {
            ModelAndView mav = new ModelAndView("common/_error");
            mav.addObject("message", ex.getMessage());
            mav.setStatus(HttpStatus.NOT_FOUND);
            return mav;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("urn:dmhelper:not-found"));
        problem.setTitle("Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConflict(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("urn:dmhelper:conflict"));
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
```

- [ ] **Step 3: Write GameMapService**

Note the Jackson 3 idiom (`JsonMapper.builder()`, unchecked exceptions) matching `CampaignService`. `updateDocument` checks the caller's `expectedVersion` explicitly and uses `saveAndFlush` so the incremented `@Version` value is visible before returning. When a schema v2 ever exists, older documents are migrated forward here instead of rejected (§3); with only v1, anything that isn't `CURRENT_SCHEMA_VERSION` is rejected.

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GameMapService {

    private final GameMapRepository repository;
    private final CampaignRepository campaignRepository;
    private final ObjectMapper objectMapper;

    public GameMapService(GameMapRepository repository, CampaignRepository campaignRepository) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.objectMapper = JsonMapper.builder().build();
    }

    public GameMap create(UUID campaignId, String name, int gridWidth, int gridHeight, int cellSizePx) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName(name);
        map.setGridWidth(gridWidth);
        map.setGridHeight(gridHeight);
        map.setCellSizePx(cellSizePx);
        map.setSortOrder((int) repository.countByCampaignId(campaignId));
        map.setDocument(objectMapper.writeValueAsString(
                MapDocumentDto.createDefault(gridWidth, gridHeight, cellSizePx)));
        return repository.save(map);
    }

    @Transactional(readOnly = true)
    public GameMap findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Map not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<GameMap> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderBySortOrderAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public MapDocumentDto getDocument(UUID mapId) {
        GameMap map = findById(mapId);
        if (map.getDocument() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(map.getDocument(), MapDocumentDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse map document: " + mapId, e);
        }
    }

    /** Whole-document replace with optimistic version check (SPEC §5). Returns the new version. */
    public long updateDocument(UUID mapId, String documentJson, long expectedVersion) {
        GameMap map = findById(mapId);
        if (map.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Map " + mapId + " changed concurrently: expected version " + expectedVersion
                    + " but is " + map.getVersion());
        }

        MapDocumentDto doc;
        try {
            doc = objectMapper.readValue(documentJson, MapDocumentDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid map document JSON: " + e.getMessage(), e);
        }
        if (doc.schemaVersion() != MapDocumentDto.CURRENT_SCHEMA_VERSION) {
            // When schema v2 exists, migrate older documents forward here (SPEC §3)
            // instead of rejecting.
            throw new IllegalArgumentException(
                    "Unsupported schemaVersion: " + doc.schemaVersion()
                    + ". Expected: " + MapDocumentDto.CURRENT_SCHEMA_VERSION);
        }

        map.setDocument(documentJson);
        repository.saveAndFlush(map);
        return map.getVersion();
    }

    public GameMap update(UUID mapId, String name, int gridWidth, int gridHeight, int cellSizePx) {
        GameMap map = findById(mapId);
        map.setName(name);
        map.setGridWidth(gridWidth);
        map.setGridHeight(gridHeight);
        map.setCellSizePx(cellSizePx);
        return repository.save(map);
    }

    public void delete(UUID mapId) {
        GameMap map = findById(mapId);
        UUID campaignId = map.getCampaign().getId();
        repository.delete(map);

        var remaining = repository.findByCampaignIdOrderBySortOrderAsc(campaignId);
        for (int i = 0; i < remaining.size(); i++) {
            var m = remaining.get(i);
            if (m.getSortOrder() != i) {
                m.setSortOrder(i);
                repository.save(m);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./mvnw test -Dtest=GameMapServiceTest`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/common/NotFoundException.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/common/web/GlobalExceptionHandler.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java
git commit -m "feat: implement GameMapService with optimistic document versioning, 404/409 handling"
```

---

### Task 5: Extend campaign export AND import with maps

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignImportExportRoundTripTest.java`

**Interfaces:**
- Consumes: `GameMapService` (Task 4), `MapDocumentDto` (Task 2).
- Produces: `CampaignExportDto.MapExportDto(String key, String name, GridDto grid, MapDocumentDto document)` with nested `GridDto(int w, int h, int cellPx, String gridType)`; `CampaignExportDto.from(Campaign, List<PartyMemberExportDto>, List<StatBlockExportDto>, List<MapExportDto>)`.

Export/import must round-trip maps (§6: "export → import → deep equality … as they land"). The export format matches the §4.1 sketch: `maps: [{ key, name, grid: {w, h, cellPx}, document: {...} }]`.

- [ ] **Step 1: Add MapExportDto and maps field to CampaignExportDto**

In `CampaignExportDto.java`:

1. Add imports:

```java
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
```

2. Change the record component `List<Object> maps` to `List<MapExportDto> maps`.

3. Replace the two existing `from(...)` factories with:

```java
    public static CampaignExportDto from(
            dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign,
            List<PartyMemberExportDto> party,
            List<StatBlockExportDto> statBlocks,
            List<MapExportDto> maps) {
        return new CampaignExportDto(
                CURRENT_FORMAT_VERSION,
                new CampaignDto(campaign.getName(), campaign.getDescription()),
                party,
                statBlocks,
                List.of(), maps, List.of(), List.of()
        );
    }

    public static CampaignExportDto from(
            dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign,
            List<PartyMemberExportDto> party,
            List<StatBlockExportDto> statBlocks) {
        return from(campaign, party, statBlocks, List.of());
    }

    public static CampaignExportDto from(dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign) {
        return from(campaign, List.of(), List.of(), List.of());
    }
```

4. Add after the `StatBlockExportDto` record:

```java
    public record MapExportDto(
            String key,
            String name,
            GridDto grid,
            MapDocumentDto document
    ) {
        public record GridDto(int w, int h, int cellPx, String gridType) {}

        public static MapExportDto from(GameMap map, MapDocumentDto document) {
            return new MapExportDto(
                    map.getId().toString(),
                    map.getName(),
                    new GridDto(map.getGridWidth(), map.getGridHeight(),
                            map.getCellSizePx(), map.getGridType()),
                    document
            );
        }
    }
```

- [ ] **Step 2: Wire maps into CampaignService export and import**

In `CampaignService.java`:

1. Add imports:

```java
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
```

2. Add a `private final GameMapService gameMapService;` field and a matching constructor parameter (assign it like the others).

3. In `exportToJson`, before `CampaignExportDto dto = ...`, build the maps list and pass it to the four-argument factory:

```java
        var maps = gameMapService.findByCampaignId(id).stream()
                .map(m -> CampaignExportDto.MapExportDto.from(m, gameMapService.getDocument(m.getId())))
                .toList();
        CampaignExportDto dto = CampaignExportDto.from(campaign, party, statBlocks, maps);
```

4. In `importFromJson`, after the `dto.statBlocks()` loop and before `return saved;`, add:

```java
        if (dto.maps() != null) {
            for (var mapDto : dto.maps()) {
                var grid = mapDto.grid();
                var map = gameMapService.create(saved.getId(), mapDto.name(),
                        grid != null ? grid.w() : 30,
                        grid != null ? grid.h() : 20,
                        grid != null ? grid.cellPx() : 48);
                if (mapDto.document() != null) {
                    gameMapService.updateDocument(map.getId(),
                            objectMapper.writeValueAsString(mapDto.document()),
                            map.getVersion());
                }
            }
        }
```

- [ ] **Step 3: Update CampaignServiceTest's imported beans**

`CampaignService` now requires a `GameMapService`, so its `@DataJpaTest` slices must provide one. In `src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignServiceTest.java` (line 20), change:

```java
@Import(CampaignService.class)
```

to:

```java
@Import({CampaignService.class, GameMapService.class})
```

and add the import `dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService`.

- [ ] **Step 4: Extend the round-trip test**

In `CampaignImportExportRoundTripTest.java`:

1. Add `GameMapService.class` to the `@Import({...})` list, add `@Autowired private GameMapService gameMapService;`, and add imports for `dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap`, `dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService`, and `dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto`.

2. Add this test method:

```java
    @Test
    void roundTripPreservesMapsAndDocuments() {
        Campaign c = campaignService.create("Map Trip", "maps");
        GameMap map = gameMapService.create(c.getId(), "Tavern", 30, 20, 48);
        String doc = """
                {"schemaVersion":1,
                 "grid":{"width":30,"height":20,"cellSizePx":48,"gridType":"square"},
                 "layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","visible":true,"locked":false,
                            "cells":[{"col":1,"row":1,"terrain":"wall"}],"shapes":[]}],
                 "primitives":[{"type":"ROOM","startCol":2,"startRow":2,"endCol":8,"endRow":6}],
                 "customTerrain":[{"key":"moss","name":"Moss","fill":"#2a6e3a","walkable":true}]}""";
        gameMapService.updateDocument(map.getId(), doc, map.getVersion());

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<GameMap> maps = gameMapService.findByCampaignId(imported.getId());
        assertThat(maps).hasSize(1);
        assertThat(maps.get(0).getName()).isEqualTo("Tavern");
        MapDocumentDto reDoc = gameMapService.getDocument(maps.get(0).getId());
        assertThat(reDoc.layers().get(0).cells()).hasSize(1);
        assertThat(reDoc.layers().get(0).cells().get(0).terrain()).isEqualTo("wall");
        assertThat(reDoc.primitives()).hasSize(1);
        assertThat(reDoc.customTerrain()).hasSize(1);
    }
```

- [ ] **Step 5: Run export/import tests**

Run: `./mvnw test -Dtest='CampaignServiceTest,CampaignImportExportRoundTripTest'`
Expected: All pass, including the new maps round-trip

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/ \
        src/test/java/dev/hendrikhoemberg/dmhelper/campaign/service/
git commit -m "feat: round-trip maps (metadata + documents) through campaign export/import"
```

---

### Task 6: Write GameMapApiController tests (TDD — red phase)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java`

**Interfaces:**
- Consumes: `GameMapService` signatures (Task 4), `MapDocumentDto` (Task 2), `NotFoundException` (Task 4).
- Produces (by specifying it): the `GameMapApiController` HTTP contract Task 7 implements.

Uses `@WebMvcTest` + `@MockitoBean` like `library/web/LibraryApiControllerTest.java` — the `@ControllerAdvice` (`GlobalExceptionHandler`) is part of the slice, so status-code mapping is tested for real.

- [ ] **Step 1: Create web test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web`

- [ ] **Step 2: Write controller tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameMapApiController.class)
class GameMapApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GameMapService service;

    private GameMap map(String name, long version) {
        GameMap m = new GameMap();
        m.setId(UUID.randomUUID());
        m.setName(name);
        m.setGridWidth(30);
        m.setGridHeight(20);
        m.setCellSizePx(48);
        m.setVersion(version);
        return m;
    }

    @Test
    void shouldListMaps() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.findByCampaignId(campaignId)).thenReturn(List.of(map("Tavern", 0)));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/maps", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tavern"))
                .andExpect(jsonPath("$[0].gridType").value("SQUARE"));
    }

    @Test
    void shouldCreateMap() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.create(eq(campaignId), eq("Tavern"), eq(30), eq(20), eq(48)))
                .thenReturn(map("Tavern", 0));
        String body = """
                {"name":"Tavern","gridWidth":30,"gridHeight":20,"cellSizePx":48}""";

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/maps", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tavern"));
    }

    @Test
    void shouldRejectBlankMapName() throws Exception {
        String body = """
                {"name":"   "}""";

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/maps", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404ForMissingMap() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new NotFoundException("Map not found: " + id));

        mockMvc.perform(get("/api/v1/maps/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetDocumentWithVersion() throws Exception {
        GameMap m = map("Tavern", 3);
        when(service.findById(m.getId())).thenReturn(m);
        when(service.getDocument(m.getId()))
                .thenReturn(MapDocumentDto.createDefault(30, 20, 48));

        mockMvc.perform(get("/api/v1/maps/{id}/document", m.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.document.schemaVersion").value(1))
                .andExpect(jsonPath("$.document.layers.length()").value(3));
    }

    @Test
    void shouldSaveDocumentAndReturnNewVersion() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateDocument(eq(id), anyString(), eq(3L))).thenReturn(4L);
        String doc = """
                {"schemaVersion":1,"grid":{"width":30,"height":20,"cellSizePx":48},"layers":[]}""";

        mockMvc.perform(put("/api/v1/maps/{id}/document", id)
                        .param("expectedVersion", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void shouldReturn409OnStaleDocumentVersion() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateDocument(eq(id), anyString(), eq(1L)))
                .thenThrow(new OptimisticLockingFailureException("stale"));
        String doc = """
                {"schemaVersion":1,"grid":{"width":30,"height":20,"cellSizePx":48},"layers":[]}""";

        mockMvc.perform(put("/api/v1/maps/{id}/document", id)
                        .param("expectedVersion", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doc))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldDeleteMap() throws Exception {
        mockMvc.perform(delete("/api/v1/maps/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -Dtest=GameMapApiControllerTest`
Expected: Compilation fails — `GameMapApiController` not found

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java && git commit -m "test: add GameMapApiController tests (TDD red phase)"
```

---

### Task 7: Implement GameMapApiController (JSON API for the map editor island)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java`

**Interfaces:**
- Consumes: `GameMapService` (Task 4).
- Produces the HTTP contract the island (Task 11) calls:
  - `GET /api/v1/campaigns/{campaignId}/maps` → `[GameMapDto]`
  - `POST /api/v1/campaigns/{campaignId}/maps` → 201 `GameMapDto`
  - `GET /api/v1/maps/{id}` → `GameMapDto`
  - `GET /api/v1/maps/{id}/document` → `{"version": n, "document": {...}}`
  - `PUT /api/v1/maps/{id}/document?expectedVersion=n` → 200 `{"version": n+1}` | 409
  - `PUT /api/v1/maps/{id}` → `GameMapDto`
  - `DELETE /api/v1/maps/{id}` → 204

Responses are records, never JPA entities — serializing `GameMap` directly would trip over the lazy `campaign` reference and leak the whole campaign. Error mapping (400/404/409) lives in `GlobalExceptionHandler` (Task 4); this controller has no `@ExceptionHandler`.

- [ ] **Step 1: Create web package**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web`

- [ ] **Step 2: Write GameMapApiController**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GameMapApiController {

    private final GameMapService service;

    public GameMapApiController(GameMapService service) {
        this.service = service;
    }

    record MapRequest(String name, Integer gridWidth, Integer gridHeight, Integer cellSizePx) {}

    record GameMapDto(UUID id, String name, int gridWidth, int gridHeight,
                      int cellSizePx, int sortOrder, String gridType, long version) {
        static GameMapDto from(GameMap m) {
            return new GameMapDto(m.getId(), m.getName(), m.getGridWidth(), m.getGridHeight(),
                    m.getCellSizePx(), m.getSortOrder(), m.getGridType(), m.getVersion());
        }
    }

    record MapDocumentResponse(long version, MapDocumentDto document) {}

    record SaveDocumentResponse(long version) {}

    @GetMapping("/campaigns/{campaignId}/maps")
    public List<GameMapDto> listMaps(@PathVariable UUID campaignId) {
        return service.findByCampaignId(campaignId).stream().map(GameMapDto::from).toList();
    }

    @PostMapping("/campaigns/{campaignId}/maps")
    public ResponseEntity<GameMapDto> createMap(@PathVariable UUID campaignId,
                                                @RequestBody MapRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Map name is required");
        }
        GameMap map = service.create(campaignId, request.name(),
                request.gridWidth() != null ? request.gridWidth() : 30,
                request.gridHeight() != null ? request.gridHeight() : 20,
                request.cellSizePx() != null ? request.cellSizePx() : 48);
        return ResponseEntity.status(HttpStatus.CREATED).body(GameMapDto.from(map));
    }

    @GetMapping("/maps/{id}")
    public GameMapDto getMap(@PathVariable UUID id) {
        return GameMapDto.from(service.findById(id));
    }

    @GetMapping("/maps/{id}/document")
    public MapDocumentResponse getDocument(@PathVariable UUID id) {
        GameMap map = service.findById(id);
        return new MapDocumentResponse(map.getVersion(), service.getDocument(id));
    }

    @PutMapping("/maps/{id}/document")
    public SaveDocumentResponse saveDocument(@PathVariable UUID id,
                                             @RequestParam long expectedVersion,
                                             @RequestBody String documentJson) {
        long newVersion = service.updateDocument(id, documentJson, expectedVersion);
        return new SaveDocumentResponse(newVersion);
    }

    @PutMapping("/maps/{id}")
    public GameMapDto updateMap(@PathVariable UUID id, @RequestBody MapRequest request) {
        GameMap existing = service.findById(id);
        GameMap updated = service.update(id,
                request.name() != null && !request.name().isBlank() ? request.name() : existing.getName(),
                request.gridWidth() != null ? request.gridWidth() : existing.getGridWidth(),
                request.gridHeight() != null ? request.gridHeight() : existing.getGridHeight(),
                request.cellSizePx() != null ? request.cellSizePx() : existing.getCellSizePx());
        return GameMapDto.from(updated);
    }

    @DeleteMapping("/maps/{id}")
    public ResponseEntity<Void> deleteMap(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./mvnw test -Dtest=GameMapApiControllerTest`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java && git commit -m "feat: add GameMapApiController — JSON CRUD and versioned document save for map editor island"
```

---

### Task 8: Create GameMapController (Thymeleaf views for map list and editor page)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java`

**Interfaces:**
- Consumes: `GameMapService` (Task 4).
- Produces view routes Task 9–10 templates rely on: `GET /campaigns/{cid}/maps` → `maps/list`; `GET /campaigns/{cid}/maps/new` → `maps/_form :: form` (htmx fragment, mirroring the party module); `POST /campaigns/{cid}/maps` → `maps/_card :: card`; `GET /campaigns/{cid}/maps/{mid}/edit` → `maps/editor`; `DELETE /campaigns/{cid}/maps/{mid}` → 200 empty (htmx removes the card).

- [ ] **Step 1: Write GameMapController**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/maps")
public class GameMapController {

    private final GameMapService service;

    public GameMapController(GameMapService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("maps", service.findByCampaignId(campaignId));
        return "maps/list";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        return "maps/_form :: form";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam String name,
                         @RequestParam(defaultValue = "30") int gridWidth,
                         @RequestParam(defaultValue = "20") int gridHeight,
                         @RequestParam(defaultValue = "48") int cellSizePx,
                         Model model) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Map name is required");
        }
        var map = service.create(campaignId, name, gridWidth, gridHeight, cellSizePx);
        model.addAttribute("map", map);
        model.addAttribute("campaignId", campaignId);
        return "maps/_card :: card";
    }

    @GetMapping("/{mapId}/edit")
    public String edit(@PathVariable UUID campaignId,
                       @PathVariable UUID mapId,
                       Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("map", service.findById(mapId));
        return "maps/editor";
    }

    @DeleteMapping("/{mapId}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId,
                                       @PathVariable UUID mapId) {
        service.delete(mapId);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java && git commit -m "feat: add GameMapController for map list and editor views"
```

---

### Task 9: Create map list Thymeleaf page and fragments

**Files:**
- Create: `src/main/resources/templates/maps/list.html`
- Create: `src/main/resources/templates/maps/_card.html`
- Create: `src/main/resources/templates/maps/_form.html`

htmx attributes containing URL expressions use `th:attr` (see Global Constraints). The new-map form is fetched from `GET …/maps/new` and appended into `#map-grid`, mirroring the party module's flow; a successful `POST` swaps the form for the returned card.

- [ ] **Step 1: Create maps template directory**

Run: `mkdir -p src/main/resources/templates/maps`

- [ ] **Step 2: Write map card fragment**

Create `src/main/resources/templates/maps/_card.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="card" th:fragment="card(map, campaignId)">
    <h3>
        <a th:href="@{/campaigns/{cid}/maps/{mid}/edit(cid=${campaignId}, mid=${map.id})}"
           th:text="${map.name}">Map Name</a>
    </h3>
    <div class="card-meta" style="color: var(--color-text-muted); font-size: var(--text-sm);">
        <span th:text="${map.gridWidth} + '×' + ${map.gridHeight} + ' · ' + ${map.cellSizePx} + 'px cells'">30×20 · 48px cells</span>
    </div>
    <div class="card-actions">
        <a class="btn btn-ghost"
           th:href="@{/campaigns/{cid}/maps/{mid}/edit(cid=${campaignId}, mid=${map.id})}">
            Edit
        </a>
        <button class="btn btn-danger"
                th:attr="hx-delete=@{/campaigns/{cid}/maps/{mid}(cid=${campaignId}, mid=${map.id})}"
                hx-confirm="Delete this map?"
                hx-target="closest .card"
                hx-swap="outerHTML">
            Delete
        </button>
    </div>
</div>
</html>
```

- [ ] **Step 3: Write map form fragment**

Create `src/main/resources/templates/maps/_form.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<div class="inline-form" th:fragment="form(campaignId)">
    <h3>New Map</h3>
    <form th:attr="hx-post=@{/campaigns/{cid}/maps(cid=${campaignId})}"
          hx-target="closest .inline-form"
          hx-swap="outerHTML">
        <div class="form-group">
            <label for="mapName">Name</label>
            <input type="text" id="mapName" name="name" required
                   placeholder="Map name" autofocus>
        </div>
        <div style="display: flex; gap: var(--space-md);">
            <div class="form-group" style="flex: 1;">
                <label for="gridWidth">Grid Width</label>
                <input type="number" id="gridWidth" name="gridWidth" value="30" min="5" max="100">
            </div>
            <div class="form-group" style="flex: 1;">
                <label for="gridHeight">Grid Height</label>
                <input type="number" id="gridHeight" name="gridHeight" value="20" min="5" max="100">
            </div>
            <div class="form-group" style="flex: 1;">
                <label for="cellSizePx">Cell Size (px)</label>
                <input type="number" id="cellSizePx" name="cellSizePx" value="48" min="16" max="128" step="8">
            </div>
        </div>
        <div class="form-actions">
            <button type="button" class="btn btn-ghost"
                    th:attr="hx-get=@{/campaigns/{cid}/maps(cid=${campaignId})}"
                    hx-target="body"
                    hx-swap="outerHTML">Cancel</button>
            <button type="submit" class="btn btn-primary">Create Map</button>
        </div>
    </form>
</div>
</html>
```

- [ ] **Step 4: Write map list page**

Create `src/main/resources/templates/maps/list.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title>DMHelper — Maps</title>
</head>
<body>
    <th:block th:replace="~{fragments/navbar :: navbar}"></th:block>

    <div class="app-layout">
        <main>
            <div class="page-header">
                <div>
                    <h1>Maps</h1>
                    <a th:href="@{/campaigns/{id}(id=${campaignId})}" class="btn btn-ghost"
                       style="margin-top: var(--space-xs);">&larr; Back to Campaign</a>
                </div>
                <button class="btn btn-primary"
                        th:attr="hx-get=@{/campaigns/{cid}/maps/new(cid=${campaignId})}"
                        hx-target="#map-grid"
                        hx-swap="beforeend">+ New Map</button>
            </div>

            <div id="map-grid" class="card-grid">
                <th:block th:if="${maps == null or maps.isEmpty()}">
                    <th:block th:replace="~{common/_empty-state :: empty-state('No maps yet. Create your first map!')}"></th:block>
                </th:block>
                <th:block th:each="map : ${maps}">
                    <th:block th:replace="~{maps/_card :: card(map=${map}, campaignId=${campaignId})}"></th:block>
                </th:block>
            </div>
        </main>

        <th:block th:replace="~{fragments/sidebar :: sidebar}"></th:block>
    </div>
</body>
</html>
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/maps/ && git commit -m "feat: add map list page, card, and form fragments"
```

---

### Task 10: Create the map editor Thymeleaf page

**Files:**
- Create: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Consumes: the `MapEditor` public API from Task 11 (read that task's class skeleton for the method list): `setTool(tool)`, `setTerrain(key)`, `setLayer(id)`, `setSnap(bool)`, `toggleLayerVisible(id)`, `toggleLayerLocked(id)`, `undo()`, `redo()`, `addCustomTerrain(name, fill, walkable) → key|null`, plus window events `map-toolchange {tool}`, `map-palette {terrains: [{key,name}]}`, `map-layerstate {layers: {id: {visible, locked}}}`.

The page hosts the Konva island. Ordering matters: the classic inline script defining `toolbar()` comes **before** `alpine.min.js` (Alpine evaluates `x-data` when it starts), and the `type="module"` script that constructs the editor publishes it as `window.mapEditor` — the toolbar methods call it lazily on click, so init order is safe. Thymeleaf inlining in the module script requires `th:inline="javascript"`.

- [ ] **Step 1: Write map editor page**

Create `src/main/resources/templates/maps/editor.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper — Edit ' + ${map.name}">DMHelper — Edit Map</title>
    <style>
        .editor-container {
            display: flex;
            flex-direction: column;
            height: 100vh;
            background: var(--color-bg);
        }
        .editor-toolbar {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: var(--space-sm);
            padding: var(--space-sm) var(--space-md);
            background: var(--color-surface);
            border-bottom: 1px solid var(--color-border);
            flex-shrink: 0;
        }
        .editor-toolbar .tool-group {
            display: flex;
            align-items: center;
            gap: 4px;
            padding-right: var(--space-md);
            border-right: 1px solid var(--color-border);
        }
        .editor-toolbar .tool-btn {
            padding: 6px 10px;
            font-size: var(--text-sm);
            border: 1px solid var(--color-border);
            border-radius: 4px;
            background: transparent;
            color: var(--color-text);
            cursor: pointer;
        }
        .editor-toolbar .tool-btn:hover { background: var(--color-surface-hover); }
        .editor-toolbar .tool-btn.active {
            background: var(--color-accent);
            border-color: var(--color-accent);
            color: #fff;
        }
        .editor-canvas-wrap {
            flex: 1;
            overflow: hidden;
            position: relative;
            background: #0a0a1a;
        }
        .editor-statusbar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 2px var(--space-md);
            background: var(--color-surface);
            border-top: 1px solid var(--color-border);
            font-size: var(--text-sm);
            color: var(--color-text-muted);
            flex-shrink: 0;
        }
        .layer-item {
            display: flex;
            align-items: center;
            gap: var(--space-xs);
            padding: 2px 8px;
            border-radius: 4px;
            font-size: var(--text-sm);
            user-select: none;
        }
        .layer-item .layer-name { cursor: pointer; }
        .layer-item:hover { background: var(--color-surface-hover); }
        .layer-item.active { background: var(--color-accent); color: #fff; }
        .layer-item button {
            background: none;
            border: none;
            cursor: pointer;
            font-size: var(--text-sm);
            padding: 0 2px;
        }
    </style>
</head>
<body>
    <div class="editor-container" x-data="toolbar()">
        <div class="editor-toolbar">
            <a th:href="@{/campaigns/{cid}/maps(cid=${campaignId})}" class="btn btn-ghost">&larr; Maps</a>

            <div class="tool-group">
                <button class="tool-btn" :class="{ active: tool === 'brush' }" @click="setTool('brush')" title="Brush (B)">🖌 Brush</button>
                <select class="tool-btn" style="padding: 6px 8px;" x-model="terrain" @change="applyTerrain()">
                    <template x-for="t in terrains" :key="t.key">
                        <option :value="t.key" x-text="t.name"></option>
                    </template>
                </select>
                <button class="tool-btn" @click="addTerrain()" title="Add custom terrain (name + color + walkable)">+ Terrain</button>
            </div>

            <div class="tool-group">
                <button class="tool-btn" :class="{ active: tool === 'rect' }" @click="setTool('rect')" title="Rectangle (R)">▭ Rect</button>
                <button class="tool-btn" :class="{ active: tool === 'circle' }" @click="setTool('circle')" title="Circle (C)">○ Circle</button>
                <button class="tool-btn" :class="{ active: tool === 'line' }" @click="setTool('line')" title="Line (L)">╱ Line</button>
                <button class="tool-btn" :class="{ active: tool === 'polygon' }" @click="setTool('polygon')" title="Polygon (P): click vertices, double-click or Enter to close, Esc to cancel">⬡ Polygon</button>
                <button class="tool-btn" :class="{ active: tool === 'select' }" @click="setTool('select')" title="Select (V): drag a box, Ctrl+C copy, Ctrl+V paste">▦ Select</button>
                <button class="tool-btn" :class="{ active: snap }" @click="toggleSnap()" title="Snap shapes to grid (on) or place freely (off)">⌗ Snap</button>
            </div>

            <div class="tool-group">
                <button class="tool-btn" @click="undo()" title="Undo (Ctrl+Z)">↩ Undo</button>
                <button class="tool-btn" @click="redo()" title="Redo (Ctrl+Y)">↪ Redo</button>
            </div>

            <div class="tool-group" style="border-right: none; margin-left: auto;">
                <template x-for="l in layerList" :key="l.id">
                    <div class="layer-item" :class="{ active: activeLayer === l.id }">
                        <span class="layer-name" @click="setLayer(l.id)" x-text="l.name"></span>
                        <button @click="toggleVisible(l.id)"
                                :title="layers[l.id].visible ? 'Hide layer' : 'Show layer'"
                                x-text="layers[l.id].visible ? '👁' : '🚫'"></button>
                        <button @click="toggleLocked(l.id)"
                                :title="layers[l.id].locked ? 'Unlock layer' : 'Lock layer'"
                                x-text="layers[l.id].locked ? '🔒' : '🔓'"></button>
                    </div>
                </template>
            </div>
        </div>

        <div class="editor-canvas-wrap" id="editorCanvasWrap">
            <!-- Konva stage mounts here -->
        </div>

        <div class="editor-statusbar">
            <span id="statusMessage">Ready</span>
            <span id="saveIndicator" style="color: var(--color-success);">Saved</span>
        </div>
    </div>

    <script th:src="@{/vendor/konva.min.js}"></script>
    <script>
        // Defined before Alpine loads so x-data="toolbar()" resolves.
        // All editor calls go through window.mapEditor (set by the module script below)
        // and are invoked lazily on user interaction, so load order is safe.
        function toolbar() {
            return {
                tool: 'brush',
                terrain: 'floor',
                snap: true,
                activeLayer: 'terrain',
                terrains: [
                    { key: 'floor', name: 'Floor' },
                    { key: 'wall', name: 'Wall' },
                    { key: 'water', name: 'Water' },
                    { key: 'difficult', name: 'Difficult Terrain' },
                    { key: 'lava', name: 'Lava' },
                    { key: 'pit', name: 'Pit' },
                    { key: 'door', name: 'Door' },
                ],
                layerList: [
                    { id: 'terrain', name: 'Terrain' },
                    { id: 'objects', name: 'Objects' },
                    { id: 'annotations', name: 'Annotations (DM only)' },
                ],
                layers: {
                    terrain: { visible: true, locked: false },
                    objects: { visible: true, locked: false },
                    annotations: { visible: true, locked: false },
                },
                init() {
                    window.addEventListener('map-toolchange', (e) => { this.tool = e.detail.tool; });
                    window.addEventListener('map-palette', (e) => { this.terrains = e.detail.terrains; });
                    window.addEventListener('map-layerstate', (e) => {
                        for (const [id, state] of Object.entries(e.detail.layers)) {
                            if (this.layers[id]) Object.assign(this.layers[id], state);
                        }
                    });
                },
                setTool(t) { window.mapEditor?.setTool(t); },
                applyTerrain() { window.mapEditor?.setTerrain(this.terrain); },
                setLayer(id) { this.activeLayer = id; window.mapEditor?.setLayer(id); },
                toggleVisible(id) { window.mapEditor?.toggleLayerVisible(id); },
                toggleLocked(id) { window.mapEditor?.toggleLayerLocked(id); },
                toggleSnap() { this.snap = !this.snap; window.mapEditor?.setSnap(this.snap); },
                undo() { window.mapEditor?.undo(); },
                redo() { window.mapEditor?.redo(); },
                addTerrain() {
                    const name = prompt('Terrain name:');
                    if (!name || !name.trim()) return;
                    const fill = prompt('Fill color (hex, e.g. #2a6e3a):', '#2a6e3a');
                    if (!fill || !fill.trim()) return;
                    const walkable = confirm('Walkable terrain? (OK = yes, Cancel = no)');
                    const key = window.mapEditor?.addCustomTerrain(name.trim(), fill.trim(), walkable);
                    if (key) this.terrain = key;
                },
            };
        }
    </script>
    <script th:src="@{/vendor/alpine.min.js}" defer></script>
    <script type="module" th:inline="javascript">
        import { MapEditor } from '/js/map/map-editor.js';

        const editor = new MapEditor({
            container: document.getElementById('editorCanvasWrap'),
            mapId: /*[[${map.id}]]*/ '',
            gridWidth: /*[[${map.gridWidth}]]*/ 30,
            gridHeight: /*[[${map.gridHeight}]]*/ 20,
            cellSizePx: /*[[${map.cellSizePx}]]*/ 48,
            statusEl: document.getElementById('statusMessage'),
            saveIndicatorEl: document.getElementById('saveIndicator'),
        });
        window.mapEditor = editor;
        editor.load();
    </script>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/maps/editor.html && git commit -m "feat: add map editor page with Alpine toolbar bridged to the Konva island"
```

---

### Task 11: Create the Konva.js map editor JS module

**Files:**
- Create: `src/main/resources/static/js/map/terrain-palette.js`
- Create: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:**
- Consumes: `GET/PUT /api/v1/maps/{id}/document` (Task 7 contract), the DOM contract from Task 10 (`window.mapEditor`, events `map-toolchange` / `map-palette` / `map-layerstate`).
- Produces: `MapEditor` class with the public API listed in Task 10.

The core of M4. Covers every §4.3 editor requirement: brush painting from an **extensible** palette; rect/circle/line/**freehand polygon** shape tools; **snap-to-grid with an unsnapped option**; three layers with **hide/lock**; **undo/redo**; **select + copy/paste**; pan (space-drag or middle-mouse) and wheel zoom; **keyboard shortcuts**; debounced **autosave** with optimistic version handling; and render-time expansion of **semantic primitives**.

Design notes for the implementer:
- Every painted cell / committed shape node carries its source record as a Konva attr (`_cell` / `_shape`), so serialization reads records back instead of reverse-engineering colors and pixel sizes.
- Primitive-expanded cells carry `_primitive: true` and are **never** serialized — primitives stay in `document.primitives` untouched, per §4.3 ("the editor emits painted cells").
- Undo snapshots are built **from the canvas** (`buildDocumentFromCanvas`), not from the possibly-stale `this.document`.
- Pointer math uses `stage.getRelativePointerPosition()` so painting stays correct after pan/zoom.
- Painting the default terrain (`floor`) erases the cell — absent cells are floor by definition.

- [ ] **Step 1: Create JS directory**

Run: `mkdir -p src/main/resources/static/js/map`

- [ ] **Step 2: Write terrain palette config**

Create `src/main/resources/static/js/map/terrain-palette.js`:

```javascript
/**
 * Built-in terrain palette. Custom entries (name + color + walkable, SPEC §4.3)
 * are stored per map in the map document's `customTerrain` array and merged in
 * by the editor at load time.
 */
export const BUILTIN_TERRAIN = {
    floor:     { name: 'Floor',             fill: '#2a2a3e', stroke: '#3a3a5e', walkable: true },
    wall:      { name: 'Wall',              fill: '#4a4a5e', stroke: '#5a5a6e', walkable: false },
    water:     { name: 'Water',             fill: '#1a3a6e', stroke: '#2a4a7e', walkable: false },
    difficult: { name: 'Difficult Terrain', fill: '#3a4a1e', stroke: '#4a5a2e', walkable: true },
    lava:      { name: 'Lava',              fill: '#6e2a1a', stroke: '#7e3a2a', walkable: false },
    pit:       { name: 'Pit',               fill: '#1a1a1a', stroke: '#2a2a2a', walkable: false },
    door:      { name: 'Door',              fill: '#8a6a2e', stroke: '#9a7a3e', walkable: true },
};

export const DEFAULT_TERRAIN = 'floor';

export const SHAPE_COLORS = {
    fill: '#8B4513',
    stroke: '#654321',
};
```

- [ ] **Step 3: Write map-editor.js**

Create `src/main/resources/static/js/map/map-editor.js`:

```javascript
import { BUILTIN_TERRAIN, DEFAULT_TERRAIN, SHAPE_COLORS } from './terrain-palette.js';

/**
 * @typedef {{col: number, row: number, terrain: string}} Cell
 * @typedef {{type: string, points: number[], fill: string, stroke: string, strokeWidth: number, label: string}} Shape
 * @typedef {{id: string, name: string, type: string, visible: boolean, locked: boolean, cells: Cell[], shapes: Shape[]}} MapLayer
 * @typedef {{type: string, startCol: number, startRow: number, endCol: number, endRow: number, terrain?: string}} Primitive
 * @typedef {{key: string, name: string, fill: string, walkable: boolean}} TerrainDef
 * @typedef {{schemaVersion: number, grid: {width: number, height: number, cellSizePx: number, gridType: string},
 *            layers: MapLayer[], primitives: Primitive[], customTerrain: TerrainDef[]}} MapDocument
 */

const SAVE_DEBOUNCE_MS = 2000;
const UNDO_MAX = 50;
const DRAW_TOOLS = ['brush', 'rect', 'circle', 'line', 'polygon'];

export class MapEditor {
    /**
     * @param {{container: HTMLElement, mapId: string, gridWidth: number, gridHeight: number,
     *          cellSizePx: number, statusEl?: HTMLElement, saveIndicatorEl?: HTMLElement}} opts
     */
    constructor({ container, mapId, gridWidth, gridHeight, cellSizePx, statusEl, saveIndicatorEl }) {
        this.container = container;
        this.mapId = mapId;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.cellSizePx = cellSizePx;
        this.statusEl = statusEl;
        this.saveIndicatorEl = saveIndicatorEl;

        /** @type {MapDocument|null} */
        this.document = null;
        this.docVersion = 0;

        this.activeTool = 'brush';
        this.activeLayerId = 'terrain';
        this.terrain = DEFAULT_TERRAIN;
        this.snap = true;
        this.palette = { ...BUILTIN_TERRAIN };

        this.drawing = false;
        this.panning = false;
        this.shapeStart = null;
        this.marqueeStart = null;
        this.polygonPoints = [];   // flat [x1, y1, ...] in cell units
        this.selection = null;     // {origin: {col, row}, cells: Cell[], shapes: Shape[]}
        this.clipboard = null;     // {cells: Cell[], shapes: Shape[]} normalized to (0,0)

        this.undoStack = [];
        this.redoStack = [];
        this.saveTimer = null;
        this.dirty = false;

        this.stage = null;
        this.gridLayer = null;
        this.previewLayer = null;
        this.layers = {};   // layer id -> Konva.Layer
    }

    load() {
        this.stage = new Konva.Stage({
            container: this.container,
            width: this.container.clientWidth,
            height: this.container.clientHeight,
            draggable: false,
        });

        this.gridLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.gridLayer);

        this.createLayer('terrain');
        this.createLayer('objects');
        this.createLayer('annotations');

        this.previewLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.previewLayer);

        this.drawGrid();
        this.setupEvents();
        this.fetchDocument();
    }

    /* ---- Grid ---- */

    drawGrid() {
        const s = this.cellSizePx;
        for (let col = 0; col <= this.gridWidth; col++) {
            this.gridLayer.add(new Konva.Line({
                points: [col * s, 0, col * s, this.gridHeight * s],
                stroke: '#333', strokeWidth: 0.5, listening: false,
            }));
        }
        for (let row = 0; row <= this.gridHeight; row++) {
            this.gridLayer.add(new Konva.Line({
                points: [0, row * s, this.gridWidth * s, row * s],
                stroke: '#333', strokeWidth: 0.5, listening: false,
            }));
        }
        this.gridLayer.batchDraw();
    }

    /* ---- Palette (extensible, §4.3) ---- */

    rebuildPalette() {
        this.palette = { ...BUILTIN_TERRAIN };
        for (const def of (this.document?.customTerrain || [])) {
            this.palette[def.key] = {
                name: def.name, fill: def.fill, stroke: def.fill,
                walkable: !!def.walkable, custom: true,
            };
        }
        this.emit('map-palette', {
            terrains: Object.entries(this.palette).map(([key, t]) => ({ key, name: t.name })),
        });
    }

    /**
     * Adds a custom terrain entry (persisted in the map document).
     * @returns {string|null} the new terrain key, or null if invalid/duplicate
     */
    addCustomTerrain(name, fill, walkable) {
        const key = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
        if (!key || this.palette[key] || !this.document) return null;
        this.syncDocument();
        this.document.customTerrain = this.document.customTerrain || [];
        this.document.customTerrain.push({ key, name, fill, walkable });
        this.rebuildPalette();
        this.setTerrain(key);
        this.markDirty();
        return key;
    }

    /* ---- Document / layer helpers ---- */

    layerDto(id) {
        return (this.document?.layers || []).find((l) => l.id === id);
    }

    isLocked(id) {
        const l = this.layerDto(id);
        return !!(l && l.locked);
    }

    /** Re-derives this.document from the current canvas state. */
    syncDocument() {
        const doc = this.buildDocumentFromCanvas();
        if (doc) this.document = doc;
    }

    /* ---- Tool & layer state (called by the toolbar and shortcuts) ---- */

    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.activeTool = tool;
        this.emit('map-toolchange', { tool });
        this.setStatus(tool === 'polygon'
            ? 'Polygon: click vertices, double-click or Enter to close, Esc to cancel'
            : tool === 'select'
                ? 'Select: drag a box, Ctrl+C copy, Ctrl+V paste'
                : 'Ready');
    }

    setTerrain(key) {
        this.terrain = key;
    }

    setLayer(layerId) {
        this.cancelPolygon();
        this.clearSelection();
        this.activeLayerId = layerId;
    }

    setSnap(snap) {
        this.snap = !!snap;
    }

    toggleLayerVisible(id) {
        const l = this.layerDto(id);
        if (!l) return;
        l.visible = !(l.visible !== false);
        const kl = this.layers[id];
        if (kl) {
            kl.visible(l.visible);
            kl.batchDraw();
        }
        this.emitLayerState();
        this.markDirty();
    }

    toggleLayerLocked(id) {
        const l = this.layerDto(id);
        if (!l) return;
        l.locked = !l.locked;
        this.emitLayerState();
        this.markDirty();
    }

    emitLayerState() {
        const layers = {};
        for (const l of (this.document?.layers || [])) {
            layers[l.id] = { visible: l.visible !== false, locked: !!l.locked };
        }
        this.emit('map-layerstate', { layers });
    }

    /* ---- Rendering ---- */

    createLayer(id) {
        const l = new Konva.Layer();
        l.id(id);
        this.stage.add(l);
        this.layers[id] = l;
        return l;
    }

    renderDocument() {
        if (!this.document) return;

        for (const layerDto of this.document.layers) {
            const kl = this.layers[layerDto.id];
            if (!kl) continue;
            kl.destroyChildren();

            if (layerDto.type === 'TERRAIN') {
                for (const cell of this.expandPrimitives()) {
                    this.addCellRect(kl, cell, { primitive: true });
                }
            }
            for (const cell of (layerDto.cells || [])) {
                this.addCellRect(kl, cell, {});
            }
            for (const shape of (layerDto.shapes || [])) {
                this.addShapeNode(kl, shape);
            }

            kl.visible(layerDto.visible !== false);
        }
        this.stage.batchDraw();
    }

    addCellRect(konvaLayer, cell, { primitive = false } = {}) {
        const t = this.palette[cell.terrain] || this.palette[DEFAULT_TERRAIN];
        const s = this.cellSizePx;
        const rect = new Konva.Rect({
            x: cell.col * s, y: cell.row * s, width: s, height: s,
            fill: t.fill, stroke: t.stroke, strokeWidth: 1, listening: false,
        });
        if (primitive) {
            rect.setAttr('_primitive', true);
        } else {
            rect.setAttr('_cell', { col: cell.col, row: cell.row, terrain: cell.terrain });
        }
        konvaLayer.add(rect);
        return rect;
    }

    addShapeNode(konvaLayer, shape) {
        const px = (v) => v * this.cellSizePx;
        const pts = shape.points || [];
        const fill = shape.fill || SHAPE_COLORS.fill;
        const stroke = shape.stroke || SHAPE_COLORS.stroke;
        const sw = shape.strokeWidth || 2;
        let node;

        switch (shape.type) {
            case 'rect':
                node = new Konva.Rect({
                    x: px(pts[0]), y: px(pts[1]), width: px(pts[2]), height: px(pts[3]),
                    fill, stroke, strokeWidth: sw, listening: false,
                });
                break;
            case 'circle':
                node = new Konva.Circle({
                    x: px(pts[0]), y: px(pts[1]), radius: px(pts[2]),
                    fill, stroke, strokeWidth: sw, listening: false,
                });
                break;
            case 'line':
                node = new Konva.Line({
                    points: [px(pts[0]), px(pts[1]), px(pts[2]), px(pts[3])],
                    stroke, strokeWidth: sw, lineCap: 'round', listening: false,
                });
                break;
            case 'polygon':
                node = new Konva.Line({
                    points: pts.map(px), closed: true,
                    fill, stroke, strokeWidth: sw, lineJoin: 'round', listening: false,
                });
                break;
            default:
                return null;
        }

        node.setAttr('_shape', shape);
        konvaLayer.add(node);

        if (shape.label) {
            konvaLayer.add(new Konva.Text({
                x: px(pts[0]) + 2, y: px(pts[1]) + 2,
                text: shape.label,
                fontSize: this.cellSizePx * 0.3,
                fill: '#fff', listening: false,
            }));
        }
        return node;
    }

    /* ---- Semantic primitives (§4.3): expanded to cells on render, never serialized ---- */

    /** @returns {Cell[]} */
    expandPrimitives() {
        const cells = [];
        for (const p of (this.document?.primitives || [])) {
            const c0 = Math.min(p.startCol, p.endCol), c1 = Math.max(p.startCol, p.endCol);
            const r0 = Math.min(p.startRow, p.endRow), r1 = Math.max(p.startRow, p.endRow);
            switch (p.type) {
                case 'ROOM':
                    for (let r = r0; r <= r1; r++) {
                        for (let c = c0; c <= c1; c++) {
                            const edge = r === r0 || r === r1 || c === c0 || c === c1;
                            if (edge) cells.push({ col: c, row: r, terrain: 'wall' });
                        }
                    }
                    break;
                case 'CORRIDOR':
                    // corridors are open floor; nothing to paint (absent cells are floor),
                    // but a REGION with terrain can be used for visible corridor flooring
                    break;
                case 'DOOR':
                    cells.push({ col: p.startCol, row: p.startRow, terrain: 'door' });
                    break;
                case 'REGION': {
                    const terrain = p.terrain || DEFAULT_TERRAIN;
                    if (terrain === DEFAULT_TERRAIN) break;
                    for (let r = r0; r <= r1; r++) {
                        for (let c = c0; c <= c1; c++) {
                            cells.push({ col: c, row: r, terrain });
                        }
                    }
                    break;
                }
            }
        }
        return cells;
    }

    /* ---- Events ---- */

    setupEvents() {
        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.button === 1) {   // middle mouse: pan
                this.panning = true;
                this.stage.draggable(true);
                return;
            }
            if (this.stage.draggable()) return;   // space-pan active

            const pos = this.cellPos();
            if (!pos) return;

            if (this.activeTool === 'select') {
                this.clearSelection();
                this.marqueeStart = { x: pos.x, y: pos.y };
                this.drawing = true;
                return;
            }

            if (DRAW_TOOLS.includes(this.activeTool) && this.isLocked(this.activeLayerId)) {
                this.setStatus('Layer is locked');
                return;
            }

            if (this.activeTool === 'brush') {
                this.pushUndo();
                this.drawing = true;
                this.paintCell(pos.col, pos.row);
            } else if (this.activeTool === 'polygon') {
                this.polygonPoints.push(this.snapPt(pos.x), this.snapPt(pos.y));
                this.renderPolygonPreview(pos);
            } else if (['rect', 'circle', 'line'].includes(this.activeTool)) {
                this.drawing = true;
                this.shapeStart = { x: this.snapPt(pos.x), y: this.snapPt(pos.y) };
            }
        });

        this.stage.on('mousemove touchmove', () => {
            if (this.panning) return;
            const pos = this.cellPos();
            if (!pos) return;

            if (this.activeTool === 'polygon' && this.polygonPoints.length) {
                this.renderPolygonPreview(pos);
                return;
            }
            if (!this.drawing) return;

            if (this.activeTool === 'brush') {
                this.paintCell(pos.col, pos.row);
            } else if (this.activeTool === 'select' && this.marqueeStart) {
                this.previewMarquee(this.marqueeStart, pos);
            } else if (this.shapeStart) {
                this.previewShape(this.shapeStart, { x: this.snapPt(pos.x), y: this.snapPt(pos.y) });
            }
        });

        this.stage.on('mouseup touchend', () => {
            if (this.panning) {
                this.panning = false;
                this.stage.draggable(false);
                return;
            }
            if (!this.drawing) return;
            this.drawing = false;

            const pos = this.cellPos();
            if (this.activeTool === 'select' && this.marqueeStart && pos) {
                this.finishSelection(this.marqueeStart, pos);
                this.marqueeStart = null;
                return;
            }
            if (this.shapeStart && pos) {
                this.commitShape(this.shapeStart, { x: this.snapPt(pos.x), y: this.snapPt(pos.y) });
            }
            this.clearPreview();
            this.shapeStart = null;
        });

        this.stage.on('dblclick dbltap', () => {
            if (this.activeTool === 'polygon') this.commitPolygon();
        });

        // Zoom (wheel)
        this.stage.on('wheel', (e) => {
            e.evt.preventDefault();
            const oldScale = this.stage.scaleX();
            const pointer = this.stage.getPointerPosition();
            if (!pointer) return;

            const direction = e.evt.deltaY > 0 ? -1 : 1;
            const newScale = Math.max(0.2, Math.min(5, oldScale + direction * 0.1 * oldScale));

            const mousePointTo = {
                x: (pointer.x - this.stage.x()) / oldScale,
                y: (pointer.y - this.stage.y()) / oldScale,
            };
            this.stage.scale({ x: newScale, y: newScale });
            this.stage.position({
                x: pointer.x - mousePointTo.x * newScale,
                y: pointer.y - mousePointTo.y * newScale,
            });
            this.stage.batchDraw();
        });

        // Keyboard shortcuts (§4.3)
        window.addEventListener('keydown', (e) => {
            if (e.target.closest?.('input, select, textarea, [contenteditable]')) return;

            if (e.code === 'Space') {
                if (!this.drawing) {
                    e.preventDefault();
                    this.stage.draggable(true);
                }
                return;
            }

            if (e.ctrlKey || e.metaKey) {
                const k = e.key.toLowerCase();
                if (k === 'z') { e.preventDefault(); e.shiftKey ? this.redo() : this.undo(); }
                else if (k === 'y') { e.preventDefault(); this.redo(); }
                else if (k === 'c' && this.selection) { e.preventDefault(); this.copySelection(); }
                else if (k === 'v' && this.clipboard) { e.preventDefault(); this.pasteClipboard(); }
                return;
            }

            switch (e.key.toLowerCase()) {
                case 'b': this.setTool('brush'); break;
                case 'r': this.setTool('rect'); break;
                case 'c': this.setTool('circle'); break;
                case 'l': this.setTool('line'); break;
                case 'p': this.setTool('polygon'); break;
                case 'v': this.setTool('select'); break;
                case 'enter': if (this.polygonPoints.length) this.commitPolygon(); break;
                case 'escape':
                    this.cancelPolygon();
                    this.clearSelection();
                    break;
            }
        });
        window.addEventListener('keyup', (e) => {
            if (e.code === 'Space' && !this.panning) this.stage.draggable(false);
        });
    }

    /** Pointer position in cell units, correct under pan/zoom. */
    cellPos() {
        const p = this.stage.getRelativePointerPosition();
        if (!p) return null;
        const x = p.x / this.cellSizePx;
        const y = p.y / this.cellSizePx;
        return { x, y, col: Math.floor(x), row: Math.floor(y) };
    }

    /** Snap-to-grid with unsnapped option (§4.3): whole cells when on, 1/20 cell when off. */
    snapPt(v) {
        return this.snap ? Math.round(v) : Math.round(v * 20) / 20;
    }

    round2(v) {
        return Math.round(v * 100) / 100;
    }

    /* ---- Brush ---- */

    paintCell(col, row) {
        if (col < 0 || col >= this.gridWidth || row < 0 || row >= this.gridHeight) return;
        if (this.isLocked(this.activeLayerId)) return;
        const layerDto = this.layerDto(this.activeLayerId);
        if (!layerDto || layerDto.type !== 'TERRAIN') {
            this.setStatus('The brush paints on the Terrain layer');
            return;
        }
        const kl = this.layers[this.activeLayerId];
        if (!kl) return;

        for (const child of kl.getChildren()) {
            const c = child.getAttr('_cell');
            if (c && c.col === col && c.row === row) {
                child.destroy();
                break;
            }
        }
        if (this.terrain !== DEFAULT_TERRAIN) {   // painting floor = erasing
            this.addCellRect(kl, { col, row, terrain: this.terrain }, {});
        }
        kl.batchDraw();
        this.markDirty();
    }

    /* ---- Shape tools (rect / circle / line) ---- */

    previewShape(a, b) {
        this.clearPreview();
        const px = (v) => v * this.cellSizePx;
        const dash = { fill: 'rgba(139,69,19,0.3)', stroke: '#8B4513', strokeWidth: 2, dash: [4, 4], listening: false };
        let shape;

        switch (this.activeTool) {
            case 'rect':
                shape = new Konva.Rect({
                    x: px(Math.min(a.x, b.x)), y: px(Math.min(a.y, b.y)),
                    width: px(Math.abs(b.x - a.x)), height: px(Math.abs(b.y - a.y)), ...dash,
                });
                break;
            case 'circle':
                shape = new Konva.Circle({
                    x: px(a.x), y: px(a.y), radius: px(Math.hypot(b.x - a.x, b.y - a.y)), ...dash,
                });
                break;
            case 'line':
                shape = new Konva.Line({ points: [px(a.x), px(a.y), px(b.x), px(b.y)], ...dash, fill: undefined });
                break;
            default:
                return;
        }
        this.previewLayer.add(shape);
        this.previewLayer.batchDraw();
    }

    commitShape(a, b) {
        let record;
        switch (this.activeTool) {
            case 'rect': {
                const w = this.round2(Math.abs(b.x - a.x)), h = this.round2(Math.abs(b.y - a.y));
                if (!w || !h) return;
                record = { type: 'rect', points: [Math.min(a.x, b.x), Math.min(a.y, b.y), w, h] };
                break;
            }
            case 'circle': {
                const r = this.round2(Math.hypot(b.x - a.x, b.y - a.y));
                if (!r) return;
                record = { type: 'circle', points: [a.x, a.y, r] };
                break;
            }
            case 'line': {
                if (a.x === b.x && a.y === b.y) return;
                record = { type: 'line', points: [a.x, a.y, b.x, b.y] };
                break;
            }
            default:
                return;
        }
        record = { ...record, fill: SHAPE_COLORS.fill, stroke: SHAPE_COLORS.stroke, strokeWidth: 2, label: '' };
        this.pushUndo();
        this.addShapeNode(this.layers[this.activeLayerId], record);
        this.layers[this.activeLayerId].batchDraw();
        this.markDirty();
    }

    /* ---- Freehand polygon (§4.3) ---- */

    renderPolygonPreview(cursor) {
        this.clearPreview();
        const px = (v) => v * this.cellSizePx;
        const pts = this.polygonPoints.map(px);
        pts.push(px(this.snapPt(cursor.x)), px(this.snapPt(cursor.y)));
        this.previewLayer.add(new Konva.Line({
            points: pts, stroke: '#8B4513', strokeWidth: 2, dash: [4, 4], listening: false,
        }));
        for (let i = 0; i < this.polygonPoints.length; i += 2) {
            this.previewLayer.add(new Konva.Circle({
                x: px(this.polygonPoints[i]), y: px(this.polygonPoints[i + 1]),
                radius: 3, fill: '#8B4513', listening: false,
            }));
        }
        this.previewLayer.batchDraw();
    }

    commitPolygon() {
        // drop consecutive duplicate vertices (a double-click adds two at the same spot)
        const pts = [];
        for (let i = 0; i < this.polygonPoints.length; i += 2) {
            const x = this.polygonPoints[i], y = this.polygonPoints[i + 1];
            const n = pts.length;
            if (n === 0 || pts[n - 2] !== x || pts[n - 1] !== y) pts.push(x, y);
        }
        if (pts.length < 6) {   // fewer than 3 distinct vertices
            this.cancelPolygon();
            return;
        }
        const record = {
            type: 'polygon', points: pts,
            fill: SHAPE_COLORS.fill, stroke: SHAPE_COLORS.stroke, strokeWidth: 2, label: '',
        };
        this.pushUndo();
        this.addShapeNode(this.layers[this.activeLayerId], record);
        this.layers[this.activeLayerId].batchDraw();
        this.polygonPoints = [];
        this.clearPreview();
        this.markDirty();
    }

    cancelPolygon() {
        this.polygonPoints = [];
        this.clearPreview();
    }

    /* ---- Select / copy / paste (§4.3) ---- */

    previewMarquee(a, b) {
        this.clearPreview();
        const px = (v) => v * this.cellSizePx;
        this.previewLayer.add(new Konva.Rect({
            x: px(Math.min(a.x, b.x)), y: px(Math.min(a.y, b.y)),
            width: px(Math.abs(b.x - a.x)), height: px(Math.abs(b.y - a.y)),
            stroke: '#4a9eff', strokeWidth: 1.5, dash: [6, 4], listening: false,
        }));
        this.previewLayer.batchDraw();
    }

    finishSelection(a, b) {
        const bounds = {
            minX: Math.min(a.x, b.x), minY: Math.min(a.y, b.y),
            maxX: Math.max(a.x, b.x), maxY: Math.max(a.y, b.y),
        };
        this.clearPreview();
        this.syncDocument();
        const layerDto = this.layerDto(this.activeLayerId);
        if (!layerDto) return;

        const cells = (layerDto.cells || []).filter((c) =>
            c.col + 0.5 >= bounds.minX && c.col + 0.5 <= bounds.maxX &&
            c.row + 0.5 >= bounds.minY && c.row + 0.5 <= bounds.maxY);
        const shapes = (layerDto.shapes || []).filter((s) => this.shapeInBounds(s, bounds));

        if (!cells.length && !shapes.length) {
            this.selection = null;
            this.setStatus('Nothing selected');
            return;
        }

        this.selection = {
            origin: { col: Math.floor(bounds.minX), row: Math.floor(bounds.minY) },
            cells: structuredClone(cells),
            shapes: structuredClone(shapes),
        };

        const px = (v) => v * this.cellSizePx;
        const rect = new Konva.Rect({
            x: px(bounds.minX), y: px(bounds.minY),
            width: px(bounds.maxX - bounds.minX), height: px(bounds.maxY - bounds.minY),
            stroke: '#4a9eff', strokeWidth: 1.5, dash: [6, 4], listening: false,
        });
        rect.setAttr('_selection', true);
        this.previewLayer.add(rect);
        this.previewLayer.batchDraw();
        this.setStatus(`${cells.length} cell(s), ${shapes.length} shape(s) selected — Ctrl+C to copy`);
    }

    shapeInBounds(s, b) {
        const pts = s.points || [];
        const within = (x, y) => x >= b.minX && x <= b.maxX && y >= b.minY && y <= b.maxY;
        switch (s.type) {
            case 'rect':
                return within(pts[0], pts[1]) && within(pts[0] + pts[2], pts[1] + pts[3]);
            case 'circle':
                return within(pts[0] - pts[2], pts[1] - pts[2]) && within(pts[0] + pts[2], pts[1] + pts[2]);
            default: {   // line | polygon
                if (pts.length < 4) return false;
                for (let i = 0; i + 1 < pts.length; i += 2) {
                    if (!within(pts[i], pts[i + 1])) return false;
                }
                return true;
            }
        }
    }

    copySelection() {
        if (!this.selection) {
            this.setStatus('Nothing selected — use the Select tool (V) first');
            return;
        }
        const { col, row } = this.selection.origin;
        this.clipboard = {
            cells: this.selection.cells.map((c) => ({ ...c, col: c.col - col, row: c.row - row })),
            shapes: this.selection.shapes.map((s) => this.shiftShape(structuredClone(s), -col, -row)),
        };
        this.setStatus('Copied — Ctrl+V to paste (offset by one cell)');
    }

    pasteClipboard() {
        if (!this.clipboard || !this.document) return;
        if (this.isLocked(this.activeLayerId)) {
            this.setStatus('Layer is locked');
            return;
        }
        this.pushUndo();
        this.syncDocument();
        const target = this.layerDto(this.activeLayerId);
        if (!target) return;

        const off = { col: (this.selection?.origin.col ?? 0) + 1, row: (this.selection?.origin.row ?? 0) + 1 };
        if (target.type === 'TERRAIN') {
            target.cells = target.cells || [];
            for (const c of this.clipboard.cells) {
                const col = c.col + off.col, row = c.row + off.row;
                if (col < 0 || col >= this.gridWidth || row < 0 || row >= this.gridHeight) continue;
                target.cells = target.cells.filter((x) => !(x.col === col && x.row === row));
                target.cells.push({ col, row, terrain: c.terrain });
            }
        }
        target.shapes = target.shapes || [];
        for (const s of this.clipboard.shapes) {
            target.shapes.push(this.shiftShape(structuredClone(s), off.col, off.row));
        }

        this.renderDocument();
        this.markDirty();
        this.setStatus('Pasted');
    }

    shiftShape(shape, dx, dy) {
        const pts = shape.points;
        switch (shape.type) {
            case 'rect':
            case 'circle':
                pts[0] += dx; pts[1] += dy;   // width/height/radius unaffected
                break;
            default:   // line | polygon: every coordinate pair
                for (let i = 0; i + 1 < pts.length; i += 2) {
                    pts[i] += dx; pts[i + 1] += dy;
                }
        }
        return shape;
    }

    /* ---- Preview / selection overlay housekeeping ---- */

    clearPreview() {
        for (const child of [...this.previewLayer.getChildren()]) {
            if (!child.getAttr('_selection')) child.destroy();
        }
        this.previewLayer.batchDraw();
    }

    clearSelection() {
        this.selection = null;
        for (const child of [...this.previewLayer.getChildren()]) {
            if (child.getAttr('_selection')) child.destroy();
        }
        this.previewLayer.batchDraw();
    }

    /* ---- Undo / redo ---- */

    pushUndo() {
        const snapshot = this.buildDocumentFromCanvas();
        if (!snapshot) return;
        this.undoStack.push(snapshot);
        if (this.undoStack.length > UNDO_MAX) this.undoStack.shift();
        this.redoStack = [];
    }

    undo() {
        if (!this.undoStack.length) return;
        const current = this.buildDocumentFromCanvas();
        if (current) this.redoStack.push(current);
        this.document = this.undoStack.pop();
        this.renderDocument();
        this.emitLayerState();
        this.markDirty();
    }

    redo() {
        if (!this.redoStack.length) return;
        const current = this.buildDocumentFromCanvas();
        if (current) this.undoStack.push(current);
        this.document = this.redoStack.pop();
        this.renderDocument();
        this.emitLayerState();
        this.markDirty();
    }

    /* ---- Serialization: canvas → document ---- */

    /** @returns {MapDocument|null} */
    buildDocumentFromCanvas() {
        if (!this.document) return null;
        const doc = structuredClone(this.document);   // preserves grid, primitives, customTerrain, flags

        for (const layerDto of doc.layers) {
            const kl = this.layers[layerDto.id];
            if (!kl) continue;
            const cells = [];
            const shapes = [];
            for (const child of kl.getChildren()) {
                const cell = child.getAttr('_cell');
                if (cell) {
                    cells.push({ ...cell });
                    continue;
                }
                const shape = child.getAttr('_shape');
                if (shape) shapes.push(structuredClone(shape));
                // _primitive nodes and label Text nodes carry neither attr → skipped
            }
            layerDto.cells = cells;
            layerDto.shapes = shapes;
        }
        return doc;
    }

    /* ---- Autosave (debounced, with optimistic version check §5) ---- */

    markDirty() {
        this.dirty = true;
        if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Unsaved…';
        clearTimeout(this.saveTimer);
        this.saveTimer = setTimeout(() => this.save(), SAVE_DEBOUNCE_MS);
    }

    async save() {
        if (!this.dirty) return;
        this.dirty = false;

        const doc = this.buildDocumentFromCanvas();
        if (!doc) return;
        this.document = doc;

        if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Saving…';
        try {
            const res = await fetch(
                `/api/v1/maps/${this.mapId}/document?expectedVersion=${this.docVersion}`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(doc),
                });
            if (res.status === 409) {
                if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Conflict!';
                this.setStatus('Map was changed elsewhere — reload the page to continue');
                return;
            }
            if (!res.ok) throw new Error('Save failed: ' + res.status);
            const data = await res.json();
            this.docVersion = data.version;
            if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Saved';
        } catch (err) {
            console.error('Autosave failed:', err);
            if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Save failed!';
            this.setStatus('Save error — retrying');
            this.dirty = true;
            clearTimeout(this.saveTimer);
            this.saveTimer = setTimeout(() => this.save(), SAVE_DEBOUNCE_MS);
        }
    }

    /* ---- Load ---- */

    async fetchDocument() {
        try {
            const res = await fetch(`/api/v1/maps/${this.mapId}/document`);
            if (!res.ok) throw new Error('Fetch failed: ' + res.status);
            const data = await res.json();
            this.docVersion = data.version;
            this.document = data.document;
            this.rebuildPalette();
            this.renderDocument();
            this.emitLayerState();
            this.setStatus('Ready');
        } catch (err) {
            console.error('Failed to load map document:', err);
            this.setStatus('Failed to load map');
        }
    }

    /* ---- Misc ---- */

    setStatus(msg) {
        if (this.statusEl) this.statusEl.textContent = msg;
    }

    emit(name, detail) {
        window.dispatchEvent(new CustomEvent(name, { detail }));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/map/ && git commit -m "feat: add Konva.js map editor island — tools, polygon, select/copy-paste, layers, snap toggle, primitives, undo/redo, versioned autosave"
```

---

### Task 12: Add Maps link to campaign detail page

**Files:**
- Modify: `src/main/resources/templates/campaigns/detail.html`

- [ ] **Step 1: Add Maps section to campaign detail page**

In `detail.html`, directly after the Party block (the `<h2>Party</h2>` heading and its `detail-actions` div, around lines 27–32), add:

```html
                <h2>Maps</h2>
                <div class="detail-actions" style="margin-bottom: var(--space-lg);">
                    <a th:href="@{/campaigns/{id}/maps(id=${campaign.id})}" class="btn btn-primary">
                        Manage Maps
                    </a>
                </div>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/campaigns/detail.html && git commit -m "feat: add Maps link to campaign detail page"
```

---

### Task 13: Create GameMapController view tests

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java`

**Interfaces:**
- Consumes: `GameMapController` routes (Task 8), `GameMapService` (mocked), templates from Tasks 9–10.

- [ ] **Step 1: Write controller tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GameMapController.class)
class GameMapControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GameMapService service;

    private GameMap map(String name) {
        GameMap m = new GameMap();
        m.setId(UUID.randomUUID());
        m.setName(name);
        m.setGridWidth(30);
        m.setGridHeight(20);
        m.setCellSizePx(48);
        return m;
    }

    @Test
    void shouldRenderMapList() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.findByCampaignId(campaignId)).thenReturn(List.of(map("Tavern")));

        mockMvc.perform(get("/campaigns/{campaignId}/maps", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/list"));
    }

    @Test
    void shouldRenderNewMapForm() throws Exception {
        mockMvc.perform(get("/campaigns/{campaignId}/maps/new", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/_form :: form"));
    }

    @Test
    void shouldCreateMapAndReturnCard() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(service.create(eq(campaignId), eq("Tavern"), eq(30), eq(20), eq(48)))
                .thenReturn(map("Tavern"));

        mockMvc.perform(post("/campaigns/{campaignId}/maps", campaignId)
                        .param("name", "Tavern"))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/_card :: card"));
    }

    @Test
    void shouldRenderEditorPage() throws Exception {
        GameMap m = map("Tavern");
        when(service.findById(m.getId())).thenReturn(m);

        mockMvc.perform(get("/campaigns/{campaignId}/maps/{mapId}/edit", UUID.randomUUID(), m.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("maps/editor"));
    }

    @Test
    void shouldDeleteMap() throws Exception {
        mockMvc.perform(delete("/campaigns/{campaignId}/maps/{mapId}",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./mvnw test -Dtest=GameMapControllerTest`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java && git commit -m "test: add GameMapController view tests"
```

---

### Task 14: Verification — build and smoke test

- [ ] **Step 1: Full build**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

Run: `./mvnw test`
Expected: All tests pass (existing M1–M3 tests + new M4 tests)

- [ ] **Step 3: Manual smoke test**

Start the app: `./mvnw spring-boot:run`

1. Open `http://localhost:8081/campaigns`, create/open a campaign, click "Manage Maps".
2. Create a new map ("Test Tavern", 30×20, 48px) and open it.
3. Verify the Konva canvas renders with a grid.
4. **Brush**: pick "Wall", paint cells; paint "Floor" over one — it erases.
5. **Custom terrain**: "+ Terrain" → name "Moss", color `#2a6e3a`, walkable — it appears in the dropdown and paints.
6. **Shapes**: draw a rect, a circle, a line (dashed preview, then commit).
7. **Polygon**: click 4 vertices, press Enter (or double-click) — closed polygon appears; Esc cancels a half-done one.
8. **Snap toggle**: turn Snap off, draw a rect — corners land between grid lines.
9. **Layers**: switch to Objects and draw; hide Terrain (👁 → 🚫) — painted cells vanish; lock Objects (🔓 → 🔒) — drawing on it is refused with a status message.
10. **Select/copy/paste**: Select tool, drag a box over painted cells, Ctrl+C, Ctrl+V — copy appears offset by one cell.
11. **Undo/redo**: Ctrl+Z / Ctrl+Y step through all of the above.
12. **Pan/zoom**: space-drag and mouse wheel; painting still lands under the cursor afterwards.
13. **Autosave**: indicator cycles Unsaved… → Saving… → Saved within ~2 s of a change.
14. Reload the page — everything (cells, shapes, custom terrain, hidden/locked layer state) is still there.
15. **Export/import**: from the campaign page, Export JSON, re-import it — the imported campaign contains the map with its document.

- [ ] **Step 4: Commit any fixes**

```bash
git add -A && git commit -m "chore: M4 verification fixes"
```

---

## Summary

M4 delivers a working map editor with:
- **Grid canvas** (configurable dimensions, cell size) rendered with Konva.js; `gridType` reserved on entity and document (§9)
- **Extensible terrain palette** — 7 built-in types + per-map custom entries (name, color, walkable) stored in the document (§4.3)
- **Brush tool** — click/drag to paint terrain cells; painting floor erases
- **Shape tools** — rectangle, circle, line, **freehand polygon** (click vertices, Enter/double-click to close)
- **Snap-to-grid with an unsnapped option** (§4.3)
- **3 layers** — terrain, objects, annotations (DM-only) — each **hideable and lockable**; `IMAGE` layer type reserved (§4.3)
- **Select tool with copy/paste** of cells and shapes (§4.3)
- **Undo/redo** (canvas-accurate snapshots, 50 levels) and **keyboard shortcuts** (B/R/C/L/P/V, Ctrl+Z/Y/C/V, Esc, Enter, Space-pan)
- **Semantic primitives** (`ROOM`/`CORRIDOR`/`DOOR`/`REGION`) in the document schema, expanded to cells on render (§4.3)
- **Autosave** — 2-second debounced `PUT /api/v1/maps/{id}/document` with **optimistic version check** (409 on conflict, §5)
- **CRUD** via Thymeleaf + htmx (map list, create, delete) and a **JSON API** returning DTOs (404 for missing maps via `NotFoundException`)
- **Campaign export _and_ import** round-tripping map metadata + documents

Tasks: 14 · Commits: 14 (one per task)
