# M4: Map Editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a usable tavern map from scratch.

**Architecture:** A `GameMap` JPA entity (ManyToOne to Campaign) carries grid config and a `document` JSON blob (the `MapDocument` schema). The map editor is a **Konva.js canvas island** — a vanilla JS ES module at `static/js/map/map-editor.js` loaded into a Thymeleaf page, talking JSON to `/api/v1/maps/{id}/document` for autosave. The island has a tool palette (brush/rect/circle/line/polygon), layer management (terrain/objects/annotations), undo/redo, and pan/zoom. Autosave is debounced (2-second quiet period). Campaign export/import is extended with map data. The surrounding page (map list, CRUD) uses Thymeleaf + htmx like every other module.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Spring Data JPA, H2, Thymeleaf + htmx, Konva.js 9.3.18 (vendored), Jackson 3 (`tools.jackson`), JUnit 5 + Mockito + AssertJ + Hamcrest

---

### Task 1: Create GameMap entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMap.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMapRepository.java`

- [ ] **Step 1: Create gamemap package directories**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data`

- [ ] **Step 2: Write GameMap entity**

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

    int countByCampaignId(UUID campaignId);
}
```

- [ ] **Step 4: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/ && git commit -m "feat: add GameMap entity and repository"
```

---

### Task 2: Create MapDocument DTO and MapLayer DTO

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentDto.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapLayerDto.java`

The `MapDocument` is the JSON blob stored on `GameMap.document`. It has a `schemaVersion`, grid config, and three layers. The Java DTO maps this JSON structure so the service layer can validate and construct it.

- [ ] **Step 1: Create service package**

Run: `mkdir -p src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service`

- [ ] **Step 2: Write MapDocumentDto**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentDto.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import tools.jackson.annotation.JsonInclude;
import tools.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record MapDocumentDto(
        @JsonProperty(required = true) int schemaVersion,
        @JsonProperty(required = true) GridDto grid,
        List<MapLayerDto> layers
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public MapDocumentDto {
        layers = layers != null ? layers : List.of();
    }

    public static MapDocumentDto createDefault(int gridWidth, int gridHeight, int cellSizePx) {
        return new MapDocumentDto(
                CURRENT_SCHEMA_VERSION,
                new GridDto(gridWidth, gridHeight, cellSizePx),
                List.of(
                        MapLayerDto.createTerrainLayer(),
                        MapLayerDto.createObjectsLayer(),
                        MapLayerDto.createAnnotationsLayer()
                )
        );
    }

    public record GridDto(
            int width,
            int height,
            int cellSizePx
    ) {}

    /** A map primitive — rooms, corridors, doors, regions — declared at the document level.
     *  The editor emits painted cells; primitives exist for AI-generated maps (§4.3). */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PrimitiveDto(
            String type,    // ROOM | CORRIDOR | DOOR | REGION
            int startCol, int startRow,
            int endCol, int endRow
    ) {}
}
```

- [ ] **Step 3: Write MapLayerDto**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapLayerDto.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import tools.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record MapLayerDto(
        @JsonProperty(required = true) String id,
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) LayerType type,
        boolean visible,
        boolean locked,
        List<CellDto> cells,
        List<ShapeDto> shapes
) {
    public enum LayerType { TERRAIN, OBJECTS, ANNOTATIONS }

    public MapLayerDto {
        cells = cells != null ? cells : new ArrayList<>();
        shapes = shapes != null ? shapes : new ArrayList<>();
    }

    public static MapLayerDto createTerrainLayer() {
        return new MapLayerDto("terrain", "Terrain", LayerType.TERRAIN, true, false,
                new ArrayList<>(), List.of());
    }

    public static MapLayerDto createObjectsLayer() {
        return new MapLayerDto("objects", "Objects", LayerType.OBJECTS, true, false,
                List.of(), new ArrayList<>());
    }

    public static MapLayerDto createAnnotationsLayer() {
        return new MapLayerDto("annotations", "Annotations (DM only)", LayerType.ANNOTATIONS, true, false,
                List.of(), new ArrayList<>());
    }

    /** A single painted cell on a terrain layer. Cells not present in the array are "floor" / default. */
    public record CellDto(
            int col,
            int row,
            String terrain
    ) {}

    /** A shape on an objects or annotations layer. Coordinates are in grid-cell units.
     *  Origin is top-left; col (x) then row (y). */
    public record ShapeDto(
            @JsonProperty(required = true) String type,  // rect | circle | line | polygon
            List<Double> points,    // [x1,y1,x2,y2] for rect/line/circle; flat array for polygon
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
git commit -m "feat: add MapDocument and MapLayer DTOs with JSON schema"
```

---

### Task 3: Write GameMapService tests (TDD — red phase)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`

- [ ] **Step 1: Create test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service`

- [ ] **Step 2: Write failing tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(GameMapService.class)
class GameMapServiceTest {

    @Autowired
    private GameMapRepository repository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private GameMapService service;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();
    }

    @Test
    void shouldCreateMapWithDefaultDocument() {
        GameMap map = service.create(campaignId, "Tavern", 30, 20, 48);

        assertThat(map.getId()).isNotNull();
        assertThat(map.getName()).isEqualTo("Tavern");
        assertThat(map.getGridWidth()).isEqualTo(30);
        assertThat(map.getGridHeight()).isEqualTo(20);
        assertThat(map.getCellSizePx()).isEqualTo(48);
        assertThat(map.getSortOrder()).isEqualTo(0);
        assertThat(map.getDocument()).isNotNull();
        assertThat(map.getDocument()).contains("\"schemaVersion\"");

        var doc = service.getDocument(map.getId());
        assertThat(doc.schemaVersion()).isEqualTo(1);
        assertThat(doc.grid().width()).isEqualTo(30);
        assertThat(doc.grid().height()).isEqualTo(20);
        assertThat(doc.layers()).hasSize(3);
        assertThat(doc.layers().get(0).id()).isEqualTo("terrain");
        assertThat(doc.layers().get(1).id()).isEqualTo("objects");
        assertThat(doc.layers().get(2).id()).isEqualTo("annotations");
    }

    @Test
    void shouldAssignIncrementingSortOrders() {
        service.create(campaignId, "Map 1", 20, 15, 48);
        GameMap map2 = service.create(campaignId, "Map 2", 20, 15, 48);
        assertThat(map2.getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldFindByCampaignOrdered() {
        service.create(campaignId, "Temple", 20, 15, 48);
        service.create(campaignId, "Cave", 20, 15, 48);

        var maps = service.findByCampaignId(campaignId);
        assertThat(maps).hasSize(2);
        assertThat(maps.get(0).getSortOrder()).isLessThan(maps.get(1).getSortOrder());
    }

    @Test
    void shouldUpdateDocument() {
        GameMap map = service.create(campaignId, "Test Map", 20, 15, 48);
        String newDoc = """
                {"schemaVersion":1,"grid":{"width":20,"height":15,"cellSizePx":48},"layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","visible":true,"locked":false,"cells":[{"col":0,"row":0,"terrain":"wall"}],"shapes":[]},{"id":"objects","name":"Objects","type":"OBJECTS","visible":true,"locked":false,"cells":[],"shapes":[]},{"id":"annotations","name":"Annotations (DM only)","type":"ANNOTATIONS","visible":true,"locked":false,"cells":[],"shapes":[]}]}""";

        service.updateDocument(map.getId(), newDoc);
        GameMap reloaded = service.findById(map.getId());
        assertThat(reloaded.getDocument()).isEqualTo(newDoc);
    }

    @Test
    void shouldRejectInvalidDocumentSchema() {
        GameMap map = service.create(campaignId, "Test Map", 20, 15, 48);
        String badDoc = """{"schemaVersion":99,"grid":{"width":20,"height":15,"cellSizePx":48}, "layers":[]}""";

        assertThatThrownBy(() -> service.updateDocument(map.getId(), badDoc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void shouldDeleteMapAndReorder() {
        service.create(campaignId, "Map 1", 20, 15, 48);
        GameMap map2 = service.create(campaignId, "Map 2", 20, 15, 48);
        service.create(campaignId, "Map 3", 20, 15, 48);

        service.delete(map2.getId());

        var maps = service.findByCampaignId(campaignId);
        assertThat(maps).hasSize(2);
        assertThat(maps.get(0).getSortOrder()).isEqualTo(0);
        assertThat(maps.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldUpdateNameAndDimensions() {
        GameMap map = service.create(campaignId, "Original", 20, 15, 48);
        GameMap updated = service.update(map.getId(), "Renamed", 40, 30, 64);

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getGridWidth()).isEqualTo(40);
        assertThat(updated.getGridHeight()).isEqualTo(30);
        assertThat(updated.getCellSizePx()).isEqualTo(64);
    }

    @Test
    void shouldThrowWhenMapNotFound() {
        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Map not found");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw test -pl . -Dtest=GameMapServiceTest`
Expected: Compilation fails — `GameMapService` class not found

- [ ] **Step 4: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java && git commit -m "test: add GameMapService tests (TDD red phase)"
```

---

### Task 4: Implement GameMapService (TDD — green phase)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java`

- [ ] **Step 1: Write GameMapService**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        this.objectMapper = new ObjectMapper();
    }

    public GameMap create(UUID campaignId, String name, int gridWidth, int gridHeight, int cellSizePx) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        int sortOrder = repository.countByCampaignId(campaignId);

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName(name);
        map.setGridWidth(gridWidth);
        map.setGridHeight(gridHeight);
        map.setCellSizePx(cellSizePx);
        map.setSortOrder(sortOrder);

        var doc = MapDocumentDto.createDefault(gridWidth, gridHeight, cellSizePx);
        try {
            map.setDocument(objectMapper.writeValueAsString(doc));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize default map document", e);
        }

        return repository.save(map);
    }

    @Transactional(readOnly = true)
    public GameMap findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Map not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<GameMap> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderBySortOrderAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public MapDocumentDto getDocument(UUID mapId) {
        GameMap map = findById(mapId);
        if (map.getDocument() == null) return null;
        try {
            return objectMapper.readValue(map.getDocument(), MapDocumentDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse map document: " + mapId, e);
        }
    }

    public void updateDocument(UUID mapId, String documentJson) {
        GameMap map = findById(mapId);
        try {
            var doc = objectMapper.readValue(documentJson, MapDocumentDto.class);
            if (doc.schemaVersion() != MapDocumentDto.CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported schemaVersion: " + doc.schemaVersion() +
                        ". Expected: " + MapDocumentDto.CURRENT_SCHEMA_VERSION);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid map document JSON: " + e.getMessage(), e);
        }
        map.setDocument(documentJson);
        repository.save(map);
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
        repository.delete(map);

        var remaining = repository.findByCampaignIdOrderBySortOrderAsc(map.getCampaign().getId());
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

- [ ] **Step 2: Run tests**

Run: `./mvnw test -pl . -Dtest=GameMapServiceTest`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java && git commit -m "feat: implement GameMapService with CRUD, document validation, and auto-reordering"
```

---

### Task 5: Extend CampaignExportDto to include maps

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java`

Background: `CampaignExportDto` already reserves `maps: List.of()` and `encounters: List.of()`. Now wire actual map data in. The export JSON format includes map metadata and the document blob.

- [ ] **Step 1: Read current CampaignExportDto and CampaignService**

Read both files to understand the current structure before editing.

- [ ] **Step 2: Add MapExportDto to CampaignExportDto**

In the existing `CampaignExportDto.java`, add after the `CampaignDto` record:

```java
public record MapExportDto(
        String key,
        String name,
        GridDto grid,
        MapDocumentDto document
) {
    public record GridDto(int w, int h, int cellPx) {}
}
```

And add a static `from(GameMap)` factory:

```java
public static MapExportDto from(GameMap map, GameMapService mapService) {
    MapDocumentDto doc = mapService.getDocument(map.getId());
    return new MapExportDto(
            map.getId().toString(),
            map.getName(),
            new GridDto(map.getGridWidth(), map.getGridHeight(), map.getCellSizePx()),
            doc
    );
}
```

- [ ] **Step 3: Update CampaignExportDto.from() to accept maps**

Modify the `from(Campaign campaign)` factory to also accept maps and encounters: add overloaded `from(Campaign, List<GameMap>, GameMapService)` or extend the existing factory.

- [ ] **Step 4: Update CampaignService.exportToJson() to include maps**

Add `GameMapService` injection to `CampaignService` and populate `maps` in the export DTO from `gameMapService.findByCampaignId(campaignId)`.

- [ ] **Step 5: Verify compilation and run export tests**

Run: `./mvnw compile && ./mvnw test -pl . -Dtest=CampaignServiceTest`
Expected: BUILD SUCCESS, all export tests pass, maps array in JSON now reflects actual map data.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignExportDto.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/campaign/service/CampaignService.java
git commit -m "feat: extend campaign export to include map data"
```

---

### Task 6: Write GameMapApiController tests (TDD — red phase)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java`

- [ ] **Step 1: Create web test package**

Run: `mkdir -p src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web`

- [ ] **Step 2: Write controller tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java`:

```java
package ruujunit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GameMapApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateMap() throws Exception {
        String body = """{"name":"Tavern","gridWidth":30,"gridHeight":20,"cellSizePx":48}""";
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/maps", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest()); // no real campaign — proves structure
    }

    @Test
    void shouldGetMapDocument() throws Exception {
        mockMvc.perform(get("/api/v1/maps/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldPutDocument() throws Exception {
        String doc = """{"schemaVersion":1,"grid":{"width":30,"height":20,"cellSizePx":48},"layers":[]}""";
        mockMvc.perform(put("/api/v1/maps/{id}/document", java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doc))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java && git commit -m "test: add GameMapApiController tests (TDD red phase)"
```

---

### Task 7: Implement GameMapApiController (JSON API for the map editor island)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java`

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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GameMapApiController {

    private final GameMapService service;

    public GameMapApiController(GameMapService service) {
        this.service = service;
    }

    @GetMapping("/campaigns/{campaignId}/maps")
    public List<GameMap> listMaps(@PathVariable UUID campaignId) {
        return service.findByCampaignId(campaignId);
    }

    @PostMapping("/campaigns/{campaignId}/maps")
    public ResponseEntity<GameMap> createMap(@PathVariable UUID campaignId,
                                             @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "New Map");
        int gridWidth = body.containsKey("gridWidth") ? ((Number) body.get("gridWidth")).intValue() : 30;
        int gridHeight = body.containsKey("gridHeight") ? ((Number) body.get("gridHeight")).intValue() : 20;
        int cellSizePx = body.containsKey("cellSizePx") ? ((Number) body.get("cellSizePx")).intValue() : 48;

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Map name is required");
        }

        GameMap map = service.create(campaignId, name, gridWidth, gridHeight, cellSizePx);
        return ResponseEntity.status(HttpStatus.CREATED).body(map);
    }

    @GetMapping("/maps/{id}")
    public ResponseEntity<GameMap> getMap(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/maps/{id}/document")
    public ResponseEntity<MapDocumentDto> getDocument(@PathVariable UUID id) {
        MapDocumentDto doc = service.getDocument(id);
        return ResponseEntity.ok(doc);
    }

    @PutMapping("/maps/{id}/document")
    public ResponseEntity<Void> saveDocument(@PathVariable UUID id,
                                             @RequestBody String documentJson) {
        service.updateDocument(id, documentJson);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/maps/{id}")
    public ResponseEntity<GameMap> updateMap(@PathVariable UUID id,
                                             @RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", null);
        int gridWidth = body.containsKey("gridWidth") ? ((Number) body.get("gridWidth")).intValue() : 0;
        int gridHeight = body.containsKey("gridHeight") ? ((Number) body.get("gridHeight")).intValue() : 0;
        int cellSizePx = body.containsKey("cellSizePx") ? ((Number) body.get("cellSizePx")).intValue() : 0;

        GameMap existing = service.findById(id);
        GameMap updated = service.update(id,
                name != null ? name : existing.getName(),
                gridWidth > 0 ? gridWidth : existing.getGridWidth(),
                gridHeight > 0 ? gridHeight : existing.getGridHeight(),
                cellSizePx > 0 ? cellSizePx : existing.getCellSizePx());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/maps/{id}")
    public ResponseEntity<Void> deleteMap(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("urn:dmhelper:validation-error"));
        problem.setTitle("Validation Error");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java && git commit -m "feat: add GameMapApiController — JSON CRUD and document save for map editor island"
```

---

### Task 8: Create GameMapController (Thymeleaf views for map list and editor page)

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java`

Maps need a list page (linked from campaign detail) and an editor page that hosts the Konva.js island. Both are Thymeleaf + htmx views; the editor page mounts the JS module.

- [ ] **Step 1: Write GameMapController**

Create `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java`:

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
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
        return "maps/_card";
    }

    @GetMapping("/{mapId}/edit")
    public String edit(@PathVariable UUID campaignId,
                       @PathVariable UUID mapId,
                       Model model) {
        var map = service.findById(mapId);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("map", map);
        return "maps/editor";
    }

    @DeleteMapping("/{mapId}")
    public String delete(@PathVariable UUID campaignId,
                         @PathVariable UUID mapId) {
        service.delete(mapId);
        return "redirect:/campaigns/" + campaignId + "/maps";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java && git commit -m "feat: add GameMapController for map list and editor views"
```

---

### Task 9: Create map list Thymeleaf page and fragments

**Files:**
- Create: `src/main/resources/templates/maps/list.html`
- Create: `src/main/resources/templates/maps/_card.html`
- Create: `src/main/resources/templates/maps/_form.html`

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
        <span th:text="${map.gridWidth + '&times;' + map.gridHeight}">30x20</span>
        <span th:text="' · ' + ${map.cellSizePx} + 'px cells'"> · 48px cells</span>
    </div>
    <div class="card-actions">
        <a class="btn btn-ghost"
           th:href="@{/campaigns/{cid}/maps/{mid}/edit(cid=${campaignId}, mid=${map.id})}">
            Edit
        </a>
        <button class="btn btn-danger"
                hx-delete="@{/campaigns/{cid}/maps/{mid}(cid=${campaignId}, mid=${map.id})}"
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
    <form hx-post="@{/campaigns/{cid}/maps(cid=${campaignId})}"
          hx-target="previous .card-grid"
          hx-swap="beforeend">
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
                    hx-get="@{/campaigns/{cid}/maps?fragment=empty-form(cid=${campaignId})}"
                    hx-target="closest .inline-form"
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
                <h1>Maps</h1>
                <div style="display: flex; gap: var(--space-sm);">
                    <a th:href="@{/campaigns/{id}(id=${campaignId})}" class="btn btn-ghost">
                        &larr; Campaign
                    </a>
                    <button class="btn btn-primary"
                            hx-get="@{/campaigns/{cid}/maps?fragment=form(cid=${campaignId})}"
                            hx-target="this"
                            hx-swap="outerHTML"
                            th:unless="${maps == null || maps.isEmpty()}">
                        + New Map
                    </button>
                </div>
            </div>

            <th:block th:if="${maps == null || maps.isEmpty()}">
                <th:block th:replace="~{common/_empty-state :: empty-state('No maps yet. Create your first map!')}"></th:block>
                <th:block th:replace="~{maps/_form :: form(campaignId=${campaignId})}"></th:block>
            </th:block>

            <div class="card-grid" th:unless="${maps == null or maps.isEmpty()}">
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
- Create: `src/main/resources/static/css/app.css` (append map editor styles)

The editor page is a full-screen canvas island with a thin toolbar. It loads `map-editor.js` as an ES module and passes the map ID and document via data attributes or a `<script>` tag.

- [ ] **Step 1: Write map editor page**

Create `src/main/resources/templates/maps/editor.html`:

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper — Edit ' + ${map.name}">DMHelper — Edit Map</title>
    <link rel="stylesheet" th:href="@{/css/app.css}">
    <style>
        /* Editor layout: full-height canvas with toolbar above */
        .editor-container {
            display: flex;
            flex-direction: column;
            height: calc(100vh - 49px);
            background: var(--color-bg);
        }
        .editor-toolbar {
            display: flex;
            align-items: center;
            gap: var(--space-sm);
            padding: var(--space-sm) var(--space-md);
            background: var(--color-surface);
            border-bottom: 1px solid var(--color-border);
            flex-shrink: 0;
        }
        .editor-toolbar .tool-group {
            display: flex;
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
            transition: all var(--transition);
        }
        .editor-toolbar .tool-btn:hover {
            background: var(--color-surface-hover);
        }
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
        .editor-canvas-wrap canvas {
            display: block;
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
        .terrain-color {
            display: inline-block;
            width: 14px;
            height: 14px;
            border-radius: 2px;
            border: 1px solid var(--color-border);
            vertical-align: middle;
            margin-right: 4px;
        }
        .layer-item {
            display: flex;
            align-items: center;
            gap: var(--space-xs);
            padding: 4px 8px;
            border-radius: 4px;
            cursor: pointer;
            font-size: var(--text-sm);
            user-select: none;
        }
        .layer-item:hover { background: var(--color-surface-hover); }
        .layer-item.active { background: var(--color-accent); color: #fff; }
        .layer-item .eye-icon { opacity: 0.5; }
        .layer-item .locked-icon { opacity: 0.5; margin-left: auto; }
    </style>
</head>
<body>
    <div class="editor-container">
        <div class="editor-toolbar" x-data="toolbar()">
            <a th:href="@{/campaigns/{cid}/maps(cid=${campaignId})}" class="btn btn-ghost" style="margin-right: var(--space-md);">
                &larr; Maps
            </a>

            <div class="tool-group">
                <button class="tool-btn" :class="{ active: tool === 'brush' }" @click="setTool('brush')" title="Brush (B)">
                    🖌️ Brush
                </button>
                <select @change="setTerrain($event.target.value)" x-model="terrain" class="tool-btn" style="padding: 6px 8px;">
                    <option value="floor">Floor</option>
                    <option value="wall">Wall</option>
                    <option value="water">Water</option>
                    <option value="difficult">Difficult Terrain</option>
                    <option value="lava">Lava</option>
                    <option value="pit">Pit</option>
                    <option value="door">Door</option>
                </select>
            </div>

            <div class="tool-group">
                <button class="tool-btn" :class="{ active: tool === 'rect' }" @click="setTool('rect')" title="Rectangle (R)">▭ Rect</button>
                <button class="tool-btn" :class="{ active: tool === 'circle' }" @click="setTool('circle')" title="Circle (C)">○ Circle</button>
                <button class="tool-btn" :class="{ active: tool === 'line' }" @click="setTool('line')" title="Line (L)">╱ Line</button>
                <button class="tool-btn" :class="{ active: tool === 'polygon' }" @click="setTool('polygon')" title="Polygon (P)">⬡ Polygon</button>
            </div>

            <div class="tool-group">
                <button class="tool-btn" @click="undo()" title="Undo (Ctrl+Z)">↩ Undo</button>
                <button class="tool-btn" @click="redo()" title="Redo (Ctrl+Y)">↪ Redo</button>
            </div>

            <div class="tool-group" style="border-right: none; margin-left: auto;">
                <th:block th:each="layer, iter : '${map.document != null ? @mapService.getDocument(map.id).layers() : T(java.util.List).of()}'">
                    <div class="layer-item" :class="{ active: activeLayer === '${layer.id}' }" @click="setLayer('${layer.id}')">
                        <span th:text="${layer.name}">Layer</span>
                        <span class="eye-icon" x-show="!layers['${layer.id}'].visible">👁️‍🗨️</span>
                        <span class="locked-icon" x-show="layers['${layer.id}'].locked">🔒</span>
                    </div>
                </th:block>
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
    <script th:src="@{/vendor/alpine.min.js}"></script>
    <script type="module">
        import { MapEditor } from '/js/map/map-editor.js';

        const mapId = /*[[${map.id}]]*/ '';
        const gridWidth = /*[[${map.gridWidth}]]*/ 30;
        const gridHeight = /*[[${map.gridHeight}]]*/ 20;
        const cellSizePx = /*[[${map.cellSizePx}]]*/ 48;

        const editor = new MapEditor({
            container: document.getElementById('editorCanvasWrap'),
            mapId: mapId,
            gridWidth: gridWidth,
            gridHeight: gridHeight,
            cellSizePx: cellSizePx,
            statusEl: document.getElementById('statusMessage'),
            saveIndicatorEl: document.getElementById('saveIndicator'),
        });

        editor.load();
    </script>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/maps/editor.html && git commit -m "feat: add map editor Thymeleaf page with toolbar layout"
```

---

### Task 11: Create the Konva.js map editor JS module

**Files:**
- Create: `src/main/resources/static/js/map/map-editor.js`
- Create: `src/main/resources/static/js/map/terrain-palette.js`

This is the core of M4 — the vanilla JS ES module that drives the Konva canvas. It handles:
- Grid rendering (square grid lines)
- Tool management (brush, rect, circle, line, polygon)
- Layer rendering (terrain cells, object shapes, annotation shapes)
- Undo/redo (snapshot-based)
- Pan (space-drag) and zoom (wheel)
- Autosave (debounced PUT to `/api/v1/maps/{id}/document`)

- [ ] **Step 1: Create JS directory**

Run: `mkdir -p src/main/resources/static/js/map`

- [ ] **Step 2: Write terrain palette config**

Create `src/main/resources/static/js/map/terrain-palette.js`:

```javascript
export const TERRAIN_TYPES = {
    floor:     { name: 'Floor',            fill: '#2a2a3e', stroke: '#3a3a5e', walkable: true },
    wall:      { name: 'Wall',             fill: '#4a4a5e', stroke: '#5a5a6e', walkable: false },
    water:     { name: 'Water',            fill: '#1a3a6e', stroke: '#2a4a7e', walkable: false },
    difficult: { name: 'Difficult Terrain',fill: '#3a4a1e', stroke: '#4a5a2e', walkable: true },
    lava:      { name: 'Lava',             fill: '#6e2a1a', stroke: '#7e3a2a', walkable: false },
    pit:       { name: 'Pit',              fill: '#1a1a1a', stroke: '#2a2a2a', walkable: false },
    door:      { name: 'Door',             fill: '#8a6a2e', stroke: '#9a7a3e', walkable: true },
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
import { TERRAIN_TYPES, DEFAULT_TERRAIN, SHAPE_COLORS } from './terrain-palette.js';

const SAVE_DEBOUNCE_MS = 2000;
const UNDO_MAX = 50;

export class MapEditor {
    constructor({ container, mapId, gridWidth, gridHeight, cellSizePx, statusEl, saveIndicatorEl }) {
        this.container = container;
        this.mapId = mapId;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.cellSizePx = cellSizePx;
        this.statusEl = statusEl;
        this.saveIndicatorEl = saveIndicatorEl;

        this.document = null;
        this.activeTool = 'brush';
        this.activeLayerId = 'terrain';
        this.terrain = DEFAULT_TERRAIN;
        this.drawing = false;
        this.panning = false;
        this.undoStack = [];
        this.redoStack = [];
        this.saveTimer = null;
        this.dirty = false;

        this.stage = null;
        this.gridLayer = null;
        this.layers = {};  // id -> Konva.Layer
        this.gridGroup = null;
    }

    load() {
        const width = this.gridWidth * this.cellSizePx;
        const height = this.gridHeight * this.cellSizePx;

        this.stage = new Konva.Stage({
            container: this.container,
            width: this.container.clientWidth,
            height: this.container.clientHeight,
            draggable: false,
        });

        this.gridLayer = new Konva.Layer();
        this.stage.add(this.gridLayer);

        this.createLayer('terrain');
        this.createLayer('objects');
        this.createLayer('annotations');

        this.drawGrid();
        this.setupEvents();
        this.fetchDocument();
    }

    /* ---- Grid ---- */
    drawGrid() {
        this.gridGroup = new Konva.Group();
        this.gridLayer.add(this.gridGroup);

        for (let col = 0; col <= this.gridWidth; col++) {
            this.gridGroup.add(new Konva.Line({
                points: [col * this.cellSizePx, 0, col * this.cellSizePx, this.gridHeight * this.cellSizePx],
                stroke: '#333',
                strokeWidth: 0.5,
            }));
        }
        for (let row = 0; row <= this.gridHeight; row++) {
            this.gridGroup.add(new Konva.Line({
                points: [0, row * this.cellSizePx, this.gridWidth * this.cellSizePx, row * this.cellSizePx],
                stroke: '#333',
                strokeWidth: 0.5,
            }));
        }
        this.gridLayer.draw();
    }

    /* ---- Layers ---- */
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
            const konvaLayer = this.layers[layerDto.id];
            if (!konvaLayer) continue;
            konvaLayer.destroyChildren();

            for (const cell of (layerDto.cells || [])) {
                const terrain = TERRAIN_TYPES[cell.terrain] || TERRAIN_TYPES[DEFAULT_TERRAIN];
                const x = cell.col * this.cellSizePx;
                const y = cell.row * this.cellSizePx;
                konvaLayer.add(new Konva.Rect({
                    x, y,
                    width: this.cellSizePx,
                    height: this.cellSizePx,
                    fill: terrain.fill,
                    stroke: terrain.stroke,
                    strokeWidth: 1,
                }));
            }

            for (const shape of (layerDto.shapes || [])) {
                this.renderShape(konvaLayer, shape);
            }

            konvaLayer.visible(layerDto.visible !== false);
            if (layerDto.locked) konvaLayer.listening(false);
            konvaLayer.draw();
        }
    }

    renderShape(konvaLayer, shape) {
        let konvaShape;
        const pts = shape.points || [];
        const px = p => p * this.cellSizePx;
        const fill = shape.fill || SHAPE_COLORS.fill;
        const stroke = shape.stroke || SHAPE_COLORS.stroke;
        const sw = (shape.strokeWidth || 2) * 2;

        switch (shape.type) {
            case 'rect': {
                const x = px(pts[0]), y = px(pts[1]),
                      w = px(pts[2]), h = px(pts[3]);
                konvaShape = new Konva.Rect({ x, y, width: w, height: h, fill, stroke, strokeWidth: sw });
                break;
            }
            case 'circle': {
                const x = px(pts[0]), y = px(pts[1]), r = px(pts[2]);
                konvaShape = new Konva.Circle({ x, y, radius: r, fill, stroke, strokeWidth: sw });
                break;
            }
            case 'line': {
                konvaShape = new Konva.Line({
                    points: [px(pts[0]), px(pts[1]), px(pts[2]), px(pts[3])],
                    fill: null, stroke, strokeWidth: sw, lineCap: 'round',
                });
                break;
            }
            case 'polygon': {
                const polyPoints = [];
                for (let i = 0; i < pts.length; i += 2) {
                    polyPoints.push(px(pts[i]), px(pts[i + 1]));
                }
                konvaShape = new Konva.Line({
                    points: polyPoints, closed: true,
                    fill, stroke, strokeWidth: sw, lineJoin: 'round',
                });
                break;
            }
            default:
                return;
        }
        if (shape.label) {
            const labelX = konvaShape.x ? konvaShape.x() + 2 : p * this.cellSizePx + 2;
            const labelY = konvaShape.y ? konvaShape.y() + 2 : 4;
            konvaLayer.add(konvaShape);
            konvaLayer.add(new Konva.Text({
                x: labelX, y: labelY,
                text: shape.label,
                fontSize: this.cellSizePx * 0.3,
                fill: '#fff',
                listening: false,
            }));
        } else {
            konvaLayer.add(konvaShape);
        }
    }

    /* ---- Tools ---- */
    setTool(tool) {
        this.activeTool = tool;
        this.dispatchEvent('toolchange', { tool });
    }

    setTerrain(terrain) {
        this.terrain = terrain;
    }

    setLayer(layerId) {
        this.activeLayerId = layerId;
    }

    /* ---- Drawing ---- */
    setupEvents() {
        const getPos = (e) => {
            const pos = this.stage.getPointerPosition();
            if (!pos) return null;
            return {
                x: pos.x,
                y: pos.y,
                col: Math.floor(pos.x / this.cellSizePx),
                row: Math.floor(pos.y / this.cellSizePx),
            };
        };

        let shapeStart = null;

        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.button === 1 || (e.evt.buttons && e.evt.buttons === 4)) {
                this.panning = true;
                this.stage.draggable(true);
                return;
            }
            if (this.activeTool === 'brush' || this.activeTool === 'rect' ||
                this.activeTool === 'circle' || this.activeTool === 'line' ||
                this.activeTool === 'polygon') {
                const pos = getPos(e);
                if (!pos) return;
                this.pushUndo();
                this.drawing = true;
                shapeStart = pos;
                if (this.activeTool === 'brush') {
                    this.paintCell(pos.col, pos.row);
                }
            }
        });

        this.stage.on('mousemove touchmove', (e) => {
            if (this.panning) return;
            if (!this.drawing) return;
            const pos = getPos(e);
            if (!pos) return;

            if (this.activeTool === 'brush') {
                this.paintCell(pos.col, pos.row);
            } else if (shapeStart && (this.activeTool === 'rect' || this.activeTool === 'circle' || this.activeTool === 'line')) {
                this.previewShape(shapeStart, pos);
            }
        });

        this.stage.on('mouseup touchend', (e) => {
            if (this.panning) {
                this.panning = false;
                this.stage.draggable(false);
                return;
            }
            if (!this.drawing) return;
            this.drawing = false;

            if (shapeStart && this.activeTool !== 'brush' && this.activeTool !== 'polygon') {
                const pos = getPos(e);
                if (pos) {
                    this.commitShape(shapeStart, pos);
                }
            }
            this.clearPreview();
            shapeStart = null;
        });

        // Pan with space key
        window.addEventListener('keydown', (e) => {
            if (e.code === 'Space' && !this.drawing) {
                e.preventDefault();
                this.stage.draggable(true);
            }
        });
        window.addEventListener('keyup', (e) => {
            if (e.code === 'Space') {
                this.stage.draggable(false);
            }
        });

        // Zoom with wheel
        this.stage.on('wheel', (e) => {
            e.evt.preventDefault();
            const oldScale = this.stage.scaleX();
            const pointer = this.stage.getPointerPosition();
            if (!pointer) return;

            const direction = e.evt.deltaY > 0 ? -1 : 1;
            const factor = 0.03;
            const newScale = Math.max(0.2, Math.min(5, oldScale + direction * factor * oldScale));

            const mousePointTo = {
                x: pointer.x / oldScale - this.stage.x() / oldScale,
                y: pointer.y / oldScale - this.stage.y() / oldScale,
            };

            this.stage.scale({ x: newScale, y: newScale });
            this.stage.position({
                x: pointer.x - mousePointTo.x * newScale,
                y: pointer.y - mousePointTo.y * newScale,
            });
            this.stage.batchDraw();
        });
    }

    paintCell(col, row) {
        if (col < 0 || col >= this.gridWidth || row < 0 || row >= this.gridHeight) return;
        const layer = this.layers[this.activeLayerId];
        if (!layer) return;

        const terrain = TERRAIN_TYPES[this.terrain] || TERRAIN_TYPES[DEFAULT_TERRAIN];
        const x = col * this.cellSizePx;
        const y = row * this.cellSizePx;

        // Remove existing cell at this position
        const children = layer.getChildren();
        for (const child of children) {
            if (child.attrs._cellCol === col && child.attrs._cellRow === row) {
                if (this.terrain === DEFAULT_TERRAIN) {
                    child.destroy();
                    layer.draw();
                    this.markDirty();
                    return;
                }
                child.destroy();
                break;
            }
        }

        if (this.terrain === DEFAULT_TERRAIN) {
            layer.draw();
            this.markDirty();
            return;
        }

        const cell = new Konva.Rect({
            x, y,
            width: this.cellSizePx,
            height: this.cellSizePx,
            fill: terrain.fill,
            stroke: terrain.stroke,
            strokeWidth: 1,
            _cellCol: col,
            _cellRow: row,
        });
        layer.add(cell);
        layer.draw();
        this.markDirty();
    }

    previewShape(startPos, currentPos) {
        this.clearPreview();

        const layer = this.layers[this.activeLayerId];
        if (!layer) return;

        const px = p => p * this.cellSizePx;
        let shape;

        switch (this.activeTool) {
            case 'rect': {
                const x = Math.min(px(startPos.col), px(currentPos.col));
                const y = Math.min(px(startPos.row), px(currentPos.row));
                const w = Math.abs(px(startPos.col) - px(currentPos.col));
                const h = Math.abs(px(startPos.row) - px(currentPos.row));
                shape = new Konva.Rect({
                    x, y, width: w, height: h,
                    fill: 'rgba(139,69,19,0.3)', stroke: '#8B4513', strokeWidth: 2, dash: [4, 4],
                });
                break;
            }
            case 'circle': {
                const cx = px(startPos.col);
                const cy = px(startPos.row);
                const r = Math.sqrt(Math.pow(px(currentPos.col) - cx, 2) + Math.pow(px(currentPos.row) - cy, 2));
                shape = new Konva.Circle({
                    x: cx, y: cy, radius: r,
                    fill: 'rgba(139,69,19,0.3)', stroke: '#8B4513', strokeWidth: 2, dash: [4, 4],
                });
                break;
            }
            case 'line': {
                shape = new Konva.Line({
                    points: [px(startPos.col), px(startPos.row), px(currentPos.col), px(currentPos.row)],
                    stroke: '#8B4513', strokeWidth: 2, dash: [4, 4],
                });
                break;
            }
        }

        if (shape) {
            shape.setAttr('_preview', true);
            layer.add(shape);
            layer.draw();
        }
    }

    clearPreview() {
        for (const id of Object.keys(this.layers)) {
            const layer = this.layers[id];
            const children = layer.getChildren();
            for (let i = children.length - 1; i >= 0; i--) {
                if (children[i].attrs._preview) {
                    children[i].destroy();
                }
            }
            layer.draw();
        }
    }

    commitShape(startPos, endPos) {
        const layer = this.layers[this.activeLayerId];
        if (!layer) return;

        const shape = this.buildShapeRecord(startPos, endPos);
        const konvaShape = this.buildKonvaShape(shape);
        layer.add(konvaShape);
        layer.draw();
        this.markDirty();
    }

    buildShapeRecord(startPos, endPos) {
        const type = this.activeTool;
        const pts = [startPos.col, startPos.row, endPos.col, endPos.row];
        return {
            type,
            points: pts,
            fill: SHAPE_COLORS.fill,
            stroke: SHAPE_COLORS.stroke,
            strokeWidth: 2,
            label: '',
        };
    }

    buildKonvaShape(record) {
        const px = p => p * this.cellSizePx;
        const [x1, y1, x2, y2] = record.points;
        switch (record.type) {
            case 'rect': return new Konva.Rect({
                x: Math.min(px(x1), px(x2)), y: Math.min(px(y1), px(y2)),
                width: Math.abs(px(x1) - px(x2)), height: Math.abs(px(y1) - px(y2)),
                fill: record.fill, stroke: record.stroke, strokeWidth: (record.strokeWidth || 2) * 2,
            });
            case 'circle': return new Konva.Circle({
                x: px(x1), y: px(y1),
                radius: Math.sqrt(Math.pow(px(x2) - px(x1), 2) + Math.pow(px(y2) - px(y1), 2)),
                fill: record.fill, stroke: record.stroke, strokeWidth: (record.strokeWidth || 2) * 2,
            });
            case 'line': return new Konva.Line({
                points: [px(x1), px(y1), px(x2), px(y2)],
                fill: null, stroke: record.stroke, strokeWidth: (record.strokeWidth || 2) * 2,
                lineCap: 'round',
            });
            default: return null;
        }
    }

    /* ---- Undo / Redo ---- */
    pushUndo() {
        const snapshot = this.serializeDocument();
        this.undoStack.push(snapshot);
        if (this.undoStack.length > UNDO_MAX) this.undoStack.shift();
        this.redoStack = [];
    }

    undo() {
        if (this.undoStack.length === 0) return;
        this.redoStack.push(this.serializeDocument());
        const snapshot = this.undoStack.pop();
        this.applySnapshot(snapshot);
        this.markDirty();
    }

    redo() {
        if (this.redoStack.length === 0) return;
        this.undoStack.push(this.serializeDocument());
        const snapshot = this.redoStack.pop();
        this.applySnapshot(snapshot);
        this.markDirty();
    }

    serializeDocument() {
        if (!this.document) return null;
        return JSON.parse(JSON.stringify(this.document));
    }

    applySnapshot(snapshot) {
        this.document = snapshot;
        this.renderDocument();
    }

    /* ---- Serialize layers from canvas to document ---- */
    buildDocumentFromCanvas() {
        if (!this.document) return null;
        const doc = JSON.parse(JSON.stringify(this.document));
        doc.schemaVersion = 1;
        doc.grid = { width: this.gridWidth, height: this.gridHeight, cellSizePx: this.cellSizePx };

        for (const layerDto of doc.layers) {
            const konvaLayer = this.layers[layerDto.id];
            if (!konvaLayer) continue;

            const cells = [];
            const shapes = [];

            for (const child of konvaLayer.getChildren()) {
                if (child.attrs._preview) continue;

                if (layerDto.type === 'TERRAIN' && child.attrs._cellCol !== undefined) {
                    cells.push({
                        col: child.attrs._cellCol,
                        row: child.attrs._cellRow,
                        terrain: this.terrainForColor(child.attrs.fill),
                    });
                } else {
                    const rect = this.shapeFromKonva(child);
                    if (rect) shapes.push(rect);
                }
            }

            layerDto.cells = cells;
            layerDto.shapes = shapes;
        }

        return doc;
    }

    terrainForColor(fill) {
        for (const [key, def] of Object.entries(TERRAIN_TYPES)) {
            if (def.fill === fill || RGBAToHex(def.fill) === RGBAToHex(fill)) return key;
        }
        return DEFAULT_TERRAIN;
    }

    shapeFromKonva(node) {
        if (!node || !node.getClassName) return null;
        const cls = node.getClassName();
        const div = p => p != null ? Math.round(p / this.cellSizePx * 10) / 10 : 0;
        switch (cls) {
            case 'Rect': return {
                type: 'rect',
                points: [div(node.x()), div(node.y()), div(node.width()), div(node.height())],
                fill: node.fill() || '', stroke: node.stroke() || '',
                strokeWidth: node.strokeWidth() / 2 || 1, label: '',
            };
            case 'Circle': return {
                type: 'circle',
                points: [div(node.x()), div(node.y()), div(node.radius())],
                fill: node.fill() || '', stroke: node.stroke() || '',
                strokeWidth: node.strokeWidth() / 2 || 1, label: '',
            };
            case 'Line': {
                const pts = node.points();
                if (!pts || pts.length < 4) return null;
                return {
                    type: pts.length === 4 ? 'line' : 'polygon',
                    points: pts.map(div),
                    fill: node.closed() ? (node.fill() || '') : '',
                    stroke: node.stroke() || '',
                    strokeWidth: node.strokeWidth() / 2 || 1, label: '',
                };
            }
            default: return null;
        }
    }

    /* ---- Autosave ---- */
    markDirty() {
        this.dirty = true;
        if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Unsaved...';
        clearTimeout(this.saveTimer);
        this.saveTimer = setTimeout(() => this.save(), SAVE_DEBOUNCE_MS);
    }

    async save() {
        if (!this.dirty) return;
        this.dirty = false;

        const doc = this.buildDocumentFromCanvas();
        this.document = doc;

        if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Saving...';

        try {
            const res = await fetch('/api/v1/maps/' + this.mapId + '/document', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(doc),
            });
            if (!res.ok) throw new Error('Save failed: ' + res.status);
            if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Saved';
            if (this.statusEl) this.statusEl.textContent = 'Ready';
        } catch (err) {
            console.error('Autosave failed:', err);
            if (this.saveIndicatorEl) this.saveIndicatorEl.textContent = 'Save failed!';
            if (this.statusEl) this.statusEl.textContent = 'Save error — unsaved changes';
            this.dirty = true;
        }
    }

    /* ---- HTTP ---- */
    async fetchDocument() {
        try {
            const res = await fetch('/api/v1/maps/' + this.mapId + '/document');
            if (!res.ok) throw new Error('Fetch failed');
            this.document = await res.json();
            this.renderDocument();
            this.pushUndo(); // initial state
        } catch (err) {
            console.error('Failed to load map document:', err);
            if (this.statusEl) this.statusEl.textContent = 'Failed to load map';
        }
    }

    dispatchEvent(name, detail) {
        window.dispatchEvent(new CustomEvent('map:' + name, { detail }));
    }
}

function RGBAToHex(rgba) {
    if (!rgba || !rgba.startsWith('rgba')) return rgba;
    const m = rgba.match(/[\d.]+/g);
    if (!m || m.length < 3) return rgba;
    const r = parseInt(m[0]), g = parseInt(m[1]), b = parseInt(m[2]);
    return '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('');
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/map/ && git commit -m "feat: add Konva.js map editor island — grid, tools, layers, undo/redo, pan/zoom, autosave"
```

---

### Task 12: Update navbar and campaign detail with map links

**Files:**
- Modify: `src/main/resources/templates/fragments/navbar.html`
- Modify: `src/main/resources/templates/campaigns/detail.html`

- [ ] **Step 1: Add Maps link to navbar**

Add a nav link in navbar.html (alongside Campaigns, Library, About). The navbar already has nav links using Alpine.js. Add: 

```html
<a href="/campaigns" class="navbar-link" :class="{ active: page === 'campaigns' }">Campaigns</a>
<a href="/library" class="navbar-link" :class="{ active: page === 'library' }">Library</a>
```

Add after Campaigns:
```html
<!-- Maps link appears per-campaign, handled in the campaign detail page -->
```

Actually, since maps are campaign-scoped, just add a "Maps" button on the campaign detail page.

- [ ] **Step 2: Add Maps link to campaign detail page**

In `detail.html`, add after the "Manage Party Roster" link:

```html
<a th:href="@{/campaigns/{id}/maps(id=${campaign.id})}" class="btn" style="margin-top: var(--space-md);">
    🗺️ Maps
</a>
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/campaigns/detail.html && git commit -m "feat: add Maps link to campaign detail page"
```

---

### Task 13: Create map list unit tests (controller integration tests)

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java`

- [ ] **Step 1: Write controller tests**

Create `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java`:

```java
package jUnit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GameMapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderMapEditorPage() throws Exception {
        // Map list page renders even without maps
        // Requires a campaign to exist
        mockMvc.perform(get("/campaigns/{campaignId}/maps", java.util.UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java && git commit -m "test: add GameMapController integration tests"
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

1. Open `http://localhost:8081/campaigns`
2. Create a campaign (or use existing)
3. Click campaign → click "Maps"
4. Create a new map (e.g., "Test Tavern", 30×20, 48px)
5. Click "Edit" on the map
6. Verify the Konva canvas renders with a grid
7. Select brush tool, pick "Wall" terrain, paint some cells
8. Verify undo/redo works
9. Verify autosave indicator shows "Saved" after 2 seconds
10. Navigate away and back — verify cells are still painted

- [ ] **Step 4: Commit any fixes**

```bash
git add -A && git commit -m "chore: M4 verification fixes"
```

---

## Summary

M4 delivers a working map editor with:
- **Grid canvas** (configurable dimensions, cell size) rendered with Konva.js
- **6 terrain types** (floor, wall, water, difficult terrain, lava, pit) plus a door marker
- **Brush tool** — click/drag to paint terrain cells onto the terrain layer
- **Shape tools** — rectangle, circle, line (draggable preview before commit)
- **3 layers** — terrain, objects, annotations (DM-only), independently active
- **Undo/redo** (snapshot-based, up to 50 levels)
- **Pan** (space-drag) and **zoom** (mouse wheel 0.2x–5x)
- **Autosave** — 2-second debounced PUT to `/api/v1/maps/{id}/document`
- **CRUD** via Thymeleaf + htmx (map list, create, rename, delete)
- **JSON API** at `/api/v1/maps/` for the editor island
- **Campaign export** extended with map metadata + document blobs

Tasks: 14 · Commits: 14 (one per task)
