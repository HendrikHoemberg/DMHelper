# M5 Battle Map — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the battle map (play mode) — tokens, movement mode toggle (grid/freeform), grid visibility, AoE templates, measurement, map switching, annotations, and bloodied indicators — enabling a mock fight by hand on a map.

**Architecture:** Tokens are JPA entities (not embedded in the map document) for per-token querying/mutation. A new `BattleMap` JS class (Konva canvas island) reuses the editor's Ko​nva patterns (stage/layers/grid/pan/zoom extracted to a shared module). An Alpine.js toolbar communicates with the Konva island via CustomEvents. Battle map page lives at `/campaigns/{cid}/maps/{mid}/play`.

**Tech Stack:** Spring Boot 4.1.0 / Java 25 / Jackson 3 (`tools.jackson`), Thymeleaf, htmx, Alpine.js, Konva.js (vendored), H2

---

## File Structure

| File | Action | Purpose |
|---|---|---|
| `gamemap/data/GameMap.java` | Modify | Add `movementMode`, `showGrid` fields |
| `gamemap/data/Token.java` | Create | Token JPA entity |
| `gamemap/data/TokenRepository.java` | Create | Token repository |
| `gamemap/service/GameMapService.java` | Modify | Update `update()` for new fields, add `updateMode()` |
| `gamemap/service/TokenService.java` | Create | Token CRUD + business logic |
| `gamemap/web/GameMapApiController.java` | Modify | Add movementMode/showGrid to GameMapDto, PATCH endpoint |
| `gamemap/web/TokenApiController.java` | Create | REST API for token CRUD + movement PATCH |
| `gamemap/web/GameMapController.java` | Modify | Add `/play` route for battle map page |
| `templates/maps/battle.html` | Create | Battle map full-screen page |
| `static/js/map/shared.js` | Create | Shared Konva utilities (grid, pan/zoom, cellPos, snapPt) |
| `static/js/map/map-editor.js` | Modify | Import from shared.js instead of local implementations |
| `static/js/map/battle-map.js` | Create | BattleMap class — tokens, AoE, measurement, map switching |
| `gamemap/service/MapDocumentDto.java` | Modify | Add movementMode, showGrid to GridDto |
| `test/.../TokenServiceTest.java` | Create | Token service tests |
| `test/.../TokenApiControllerTest.java` | Create | Token API controller tests |
| `test/.../GameMapServiceTest.java` | Modify | Add test for movementMode/showGrid defaults |
| `test/.../GameMapApiControllerTest.java` | Modify | Add test for updated fields in DTO |
| `test/.../GameMapControllerTest.java` | Modify | Add test for `/play` route |

---

### Task 1: Add movementMode and showGrid to GameMap entity

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMap.java`
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentDto.java`

- [ ] **Step 1: Add fields to GameMap entity**

Add after the `gridType` field (line 38):

```java
/** Movement mode: GRID (default, token movement snaps to cells) or FREEFORM (pixel movement). */
@Column(nullable = false, length = 16)
private String movementMode = "GRID";

/** Whether to show grid lines on the battle map (independent of movement mode). */
@Column(nullable = false)
private boolean showGrid = true;

public String getMovementMode() { return movementMode; }
public void setMovementMode(String movementMode) { this.movementMode = movementMode; }

public boolean isShowGrid() { return showGrid; }
public void setShowGrid(boolean showGrid) { this.showGrid = showGrid; }
```

- [ ] **Step 2: Add movementMode and showGrid to GridDto in MapDocumentDto.java**

In `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentDto.java`, change the GridDto record (line 39):

```java
record GridDto(int width, int height, int cellSizePx, String gridType, String movementMode, boolean showGrid) {
    GridDto(int width, int height, int cellSizePx, String gridType) {
        this(width, height, cellSizePx, gridType, "GRID", true);
    }
}
```

Update `createDefault()` (line 26) to pass the new fields:

```java
public static MapDocumentDto createDefault(int gridWidth, int gridHeight, int cellSizePx) {
    return new MapDocumentDto(
            CURRENT_SCHEMA_VERSION,
            new GridDto(gridWidth, gridHeight, cellSizePx, "square", "GRID", true),
            List.of(
                    MapLayerDto.createTerrainLayer(gridWidth, gridHeight),
                    MapLayerDto.createObjectsLayer(),
                    MapLayerDto.createAnnotationsLayer()
            ),
            List.of(),
            List.of()
    );
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/GameMap.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapDocumentDto.java
git commit -m "feat: add movementMode and showGrid fields to GameMap entity and GridDto"
```

---

### Task 2: Update GameMapService for new fields

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java`

- [ ] **Step 1: Add updateMode() method**

Add after the existing `update()` method (line 103):

```java
public GameMap updateMode(UUID mapId, String movementMode, Boolean showGrid) {
    GameMap map = findById(mapId);
    if (movementMode != null) {
        if (!movementMode.equals("GRID") && !movementMode.equals("FREEFORM")) {
            throw new IllegalArgumentException("Invalid movementMode: " + movementMode);
        }
        map.setMovementMode(movementMode);
    }
    if (showGrid != null) {
        map.setShowGrid(showGrid);
    }
    return repository.save(map);
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapService.java
git commit -m "feat: add updateMode() to GameMapService for movement mode and grid visibility"
```

---

### Task 3: Update GameMap API to include new fields and add PATCH endpoint

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java`

- [ ] **Step 1: Update GameMapDto to include new fields**

```java
record GameMapDto(UUID id, String name, int gridWidth, int gridHeight,
                  int cellSizePx, int sortOrder, String gridType, long version,
                  String movementMode, boolean showGrid) {
    static GameMapDto from(GameMap m) {
        return new GameMapDto(m.getId(), m.getName(), m.getGridWidth(), m.getGridHeight(),
                m.getCellSizePx(), m.getSortOrder(), m.getGridType(), m.getVersion(),
                m.getMovementMode(), m.isShowGrid());
    }
}
```

- [ ] **Step 2: Add a ModeUpdateRequest record and PATCH endpoint**

Add after the `MapRequest` record:

```java
record ModeUpdateRequest(String movementMode, Boolean showGrid) {}
```

Add endpoint after the `updateMap` method:

```java
@PatchMapping("/maps/{id}")
public GameMapDto updateMode(@PathVariable UUID id, @RequestBody ModeUpdateRequest request) {
    GameMap updated = service.updateMode(id, request.movementMode(), request.showGrid());
    return GameMapDto.from(updated);
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiController.java
git commit -m "feat: add movementMode/showGrid to GameMapDto and PATCH endpoint for mode updates"
```

---

### Task 4: Create Token entity and repository

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/Token.java`
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/TokenRepository.java`

- [ ] **Step 1: Create Token entity**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.data;

import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "token", indexes = {
    @Index(name = "idx_token_map", columnList = "map_id"),
})
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id", nullable = false)
    private GameMap map;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 16)
    private String kind = "NPC";  // PC, NPC, MONSTER, OBJECT

    @Column(nullable = false)
    private int positionX = 0;

    @Column(nullable = false)
    private int positionY = 0;

    @Column(nullable = false)
    private int sizeCols = 1;

    @Column(nullable = false)
    private int sizeRows = 1;

    @Column(nullable = false, length = 7)
    private String color = "#7b68ee";

    @Column(nullable = false)
    private boolean hidden = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statblock_id")
    private StatBlock statBlock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_member_id")
    private PartyMember partyMember;

    @Column
    private Integer currentHp;

    @Column
    private Integer maxHp;

    @Column(columnDefinition = "CLOB")
    private String notes;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public int getPositionX() { return positionX; }
    public void setPositionX(int positionX) { this.positionX = positionX; }

    public int getPositionY() { return positionY; }
    public void setPositionY(int positionY) { this.positionY = positionY; }

    public int getSizeCols() { return sizeCols; }
    public void setSizeCols(int sizeCols) { this.sizeCols = sizeCols; }

    public int getSizeRows() { return sizeRows; }
    public void setSizeRows(int sizeRows) { this.sizeRows = sizeRows; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public StatBlock getStatBlock() { return statBlock; }
    public void setStatBlock(StatBlock statBlock) { this.statBlock = statBlock; }

    public PartyMember getPartyMember() { return partyMember; }
    public void setPartyMember(PartyMember partyMember) { this.partyMember = partyMember; }

    public Integer getCurrentHp() { return currentHp; }
    public void setCurrentHp(Integer currentHp) { this.currentHp = currentHp; }

    public Integer getMaxHp() { return maxHp; }
    public void setMaxHp(Integer maxHp) { this.maxHp = maxHp; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
```

- [ ] **Step 2: Create TokenRepository**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {
    List<Token> findByMapIdOrderByNameAsc(UUID mapId);
    void deleteByMapId(UUID mapId);
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/Token.java \
        src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/data/TokenRepository.java
git commit -m "feat: add Token entity and repository"
```

---

### Task 5: Create TokenService

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/TokenService.java`

- [ ] **Step 1: Write TokenDto records and TokenService**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TokenService {

    private final TokenRepository repository;
    private final GameMapRepository mapRepository;
    private final PartyMemberRepository partyMemberRepository;

    public TokenService(TokenRepository repository, GameMapRepository mapRepository,
                        PartyMemberRepository partyMemberRepository) {
        this.repository = repository;
        this.mapRepository = mapRepository;
        this.partyMemberRepository = partyMemberRepository;
    }

    public record TokenDto(UUID id, String name, String kind, int positionX, int positionY,
                           int sizeCols, int sizeRows, String color, boolean hidden,
                           Integer currentHp, Integer maxHp, boolean bloodied,
                           UUID statBlockId, UUID partyMemberId) {}

    public record TokenCreateRequest(String name, String kind, int positionX, int positionY,
                                     int sizeCols, int sizeRows, String color, boolean hidden,
                                     Integer currentHp, Integer maxHp, UUID statBlockId, UUID partyMemberId) {}

    public record TokenMoveRequest(int positionX, int positionY) {}

    public record TokenHpRequest(int currentHp) {}

    public static TokenDto toDto(Token t) {
        boolean bloodied = t.getCurrentHp() != null && t.getMaxHp() != null
                && t.getMaxHp() > 0 && t.getCurrentHp() <= t.getMaxHp() / 2;
        return new TokenDto(t.getId(), t.getName(), t.getKind(),
                t.getPositionX(), t.getPositionY(), t.getSizeCols(), t.getSizeRows(),
                t.getColor(), t.isHidden(), t.getCurrentHp(), t.getMaxHp(), bloodied,
                t.getStatBlock() != null ? t.getStatBlock().getId() : null,
                t.getPartyMember() != null ? t.getPartyMember().getId() : null);
    }

    @Transactional(readOnly = true)
    public List<TokenDto> findByMapId(UUID mapId) {
        return repository.findByMapIdOrderByNameAsc(mapId).stream()
                .map(TokenService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Token findEntityById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Token not found: " + id));
    }

    public TokenDto create(UUID mapId, TokenCreateRequest req) {
        GameMap map = mapRepository.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Map not found: " + mapId));
        Token t = new Token();
        t.setMap(map);
        t.setName(req.name() != null ? req.name() : "Token");
        t.setKind(req.kind() != null ? req.kind() : "NPC");
        t.setPositionX(req.positionX());
        t.setPositionY(req.positionY());
        t.setSizeCols(req.sizeCols() > 0 ? req.sizeCols() : 1);
        t.setSizeRows(req.sizeRows() > 0 ? req.sizeRows() : 1);
        t.setColor(req.color() != null ? req.color() : "#7b68ee");
        t.setHidden(req.hidden());
        t.setCurrentHp(req.currentHp());
        t.setMaxHp(req.maxHp());
        return toDto(repository.save(t));
    }

    public TokenDto move(UUID id, TokenMoveRequest req) {
        Token t = findEntityById(id);
        t.setPositionX(req.positionX());
        t.setPositionY(req.positionY());
        return toDto(repository.save(t));
    }

    public TokenDto updateHp(UUID id, TokenHpRequest req) {
        Token t = findEntityById(id);
        t.setCurrentHp(req.currentHp());
        return toDto(repository.save(t));
    }

    public TokenDto update(UUID id, TokenCreateRequest req) {
        Token t = findEntityById(id);
        if (req.name() != null) t.setName(req.name());
        if (req.kind() != null) t.setKind(req.kind());
        t.setPositionX(req.positionX());
        t.setPositionY(req.positionY());
        if (req.sizeCols() > 0) t.setSizeCols(req.sizeCols());
        if (req.sizeRows() > 0) t.setSizeRows(req.sizeRows());
        if (req.color() != null) t.setColor(req.color());
        t.setHidden(req.hidden());
        t.setCurrentHp(req.currentHp());
        t.setMaxHp(req.maxHp());
        return toDto(repository.save(t));
    }

    public void delete(UUID id) {
        repository.delete(findEntityById(id));
    }

    /** Create tokens for all active party members and place them on the map. */
    public List<TokenDto> addPartyToMap(UUID mapId) {
        GameMap map = mapRepository.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Map not found: " + mapId));
        List<PartyMember> members = partyMemberRepository
                .findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(map.getCampaign().getId());
        int x = 0;
        int y = 0;
        for (PartyMember pm : members) {
            Token t = new Token();
            t.setMap(map);
            t.setName(pm.getCharacterName());
            t.setKind("PC");
            t.setPositionX(x * map.getCellSizePx());
            t.setPositionY(y * map.getCellSizePx());
            t.setSizeCols(1);
            t.setSizeRows(1);
            t.setColor("#4a9eff");
            t.setHidden(false);
            t.setCurrentHp(pm.getMaxHp());
            t.setMaxHp(pm.getMaxHp());
            t.setPartyMember(pm);
            repository.save(t);
            x++;
            if (x >= map.getGridWidth()) { x = 0; y++; }
        }
        return findByMapId(mapId);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/TokenService.java
git commit -m "feat: add TokenService with CRUD, HP, move, and addPartyToMap"
```

---

### Task 6: Create Token API controller

**Files:**
- Create: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/TokenApiController.java`

- [ ] **Step 1: Write TokenApiController**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TokenApiController {

    private final TokenService service;

    public TokenApiController(TokenService service) {
        this.service = service;
    }

    @GetMapping("/maps/{mapId}/tokens")
    public List<TokenDto> listTokens(@PathVariable UUID mapId) {
        return service.findByMapId(mapId);
    }

    @PostMapping("/maps/{mapId}/tokens")
    public ResponseEntity<TokenDto> createToken(@PathVariable UUID mapId,
                                                @RequestBody TokenCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(mapId, request));
    }

    @GetMapping("/tokens/{id}")
    public TokenDto getToken(@PathVariable UUID id) {
        return TokenService.toDto(service.findEntityById(id));
    }

    @PatchMapping("/tokens/{id}/move")
    public TokenDto moveToken(@PathVariable UUID id, @RequestBody TokenMoveRequest request) {
        return service.move(id, request);
    }

    @PatchMapping("/tokens/{id}/hp")
    public TokenDto updateHp(@PathVariable UUID id, @RequestBody TokenHpRequest request) {
        return service.updateHp(id, request);
    }

    @PutMapping("/tokens/{id}")
    public TokenDto updateToken(@PathVariable UUID id, @RequestBody TokenCreateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/tokens/{id}")
    public ResponseEntity<Void> deleteToken(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/maps/{mapId}/tokens/add-party")
    public List<TokenDto> addPartyToMap(@PathVariable UUID mapId) {
        return service.addPartyToMap(mapId);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/TokenApiController.java
git commit -m "feat: add TokenApiController with CRUD, move, HP, and add-party endpoints"
```

---

### Task 7: Add battle map page route to GameMapController

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java`

- [ ] **Step 1: Add /play route**

Add this endpoint before the closing brace of the controller:

```java
@GetMapping("/{mapId}/play")
public String playMap(@PathVariable UUID campaignId, @PathVariable UUID mapId, Model model) {
    GameMap map = service.findById(mapId);
    model.addAttribute("campaignId", campaignId);
    model.addAttribute("map", map);
    return "maps/battle";
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapController.java
git commit -m "feat: add /play route to GameMapController for battle map page"
```

---

### Task 8: Create shared.js — extract grid, pan/zoom, and coordinate utilities

**Files:**
- Create: `src/main/resources/static/js/map/shared.js`
- Modify: `src/main/resources/static/js/map/map-editor.js`

- [ ] **Step 1: Write shared.js**

```js
/**
 * Shared Konva utilities for canvas islands.
 * Used by both MapEditor and BattleMap.
 */

/**
 * Draw grid lines onto a Konva.Layer.
 * @param {import('konva').Layer} layer
 * @param {number} gridWidth
 * @param {number} gridHeight
 * @param {number} cellSizePx
 */
export function drawGrid(layer, gridWidth, gridHeight, cellSizePx) {
    layer.destroyChildren();
    const s = cellSizePx;
    for (let col = 0; col <= gridWidth; col++) {
        layer.add(new Konva.Line({
            points: [col * s, 0, col * s, gridHeight * s],
            stroke: '#333', strokeWidth: 0.5, listening: false,
        }));
    }
    for (let row = 0; row <= gridHeight; row++) {
        layer.add(new Konva.Line({
            points: [0, row * s, gridWidth * s, row * s],
            stroke: '#333', strokeWidth: 0.5, listening: false,
        }));
    }
    layer.batchDraw();
}

/**
 * Setup pan (space-drag, middle-mouse) and pinch-to-zoom on a Konva.Stage.
 * @param {import('konva').Stage} stage
 * @param {HTMLElement} container
 * @param {(p: {x: number, y: number, col: number, row: number}) => void} [onCursorMove]
 */
export function setupPanAndZoom(stage, container, onCursorMove) {
    // Pan: hold space or middle mouse
    window.addEventListener('keydown', (e) => {
        if (e.code === 'Space' && !e.target.closest?.('input, select, textarea, [contenteditable]')) {
            e.preventDefault();
            stage.draggable(true);
            container.style.cursor = 'grab';
        }
    });
    window.addEventListener('keyup', (e) => {
        if (e.code === 'Space') {
            stage.draggable(false);
            container.style.cursor = container.style.cursor === 'grab' ? 'default' : container.style.cursor;
        }
    });

    stage.on('mousedown', (e) => {
        if (e.evt.button === 1) {
            e.evt.preventDefault();
            stage.draggable(true);
            container.style.cursor = 'grabbing';
        }
    });
    stage.on('mouseup', (e) => {
        if (e.evt.button === 1) {
            stage.draggable(false);
            container.style.cursor = 'default';
        }
    });

    // Zoom with scroll wheel
    stage.on('wheel', (e) => {
        e.evt.preventDefault();
        const oldScale = stage.scaleX();
        const pointer = stage.getPointerPosition();
        if (!pointer) return;

        const direction = e.evt.deltaY > 0 ? -1 : 1;
        const newScale = Math.max(0.2, Math.min(5, oldScale + direction * 0.1 * oldScale));

        const mousePointTo = {
            x: (pointer.x - stage.x()) / oldScale,
            y: (pointer.y - stage.y()) / oldScale,
        };
        stage.scale({ x: newScale, y: newScale });
        stage.position({
            x: pointer.x - mousePointTo.x * newScale,
            y: pointer.y - mousePointTo.y * newScale,
        });
        stage.batchDraw();
        if (onCursorMove) {
            const p = cellPos(stage, oldScale > 0 ? oldScale : 1); // use previous cellSizePx? No — pass externally
        }
    });
}

/**
 * Get the current pointer position in cell units, correct under pan/zoom.
 * @param {import('konva').Stage} stage
 * @param {number} cellSizePx
 * @returns {{x: number, y: number, col: number, row: number}|null}
 */
export function cellPos(stage, cellSizePx) {
    const p = stage.getRelativePointerPosition();
    if (!p) return null;
    const x = p.x / cellSizePx;
    const y = p.y / cellSizePx;
    return { x, y, col: Math.floor(x), row: Math.floor(y) };
}

/**
 * Snap a coordinate value to grid (whole cells when snap=true, 1/20 cell when false).
 * @param {number} v
 * @param {boolean} snap
 * @returns {number}
 */
export function snapPt(v, snap) {
    return snap ? Math.round(v) : Math.round(v * 20) / 20;
}

/**
 * Snap pixel position to grid, converting to pixel space.
 * @param {number} px - pixel x or y
 * @param {number} cellSizePx
 * @param {boolean} snap
 * @returns {number}
 */
export function snapPixel(px, cellSizePx, snap) {
    if (!snap) return px;
    const cell = Math.round(px / cellSizePx);
    return cell * cellSizePx;
}

/**
 * Convert cell coordinates to pixel center of the cell.
 * @param {number} col
 * @param {number} row
 * @param {number} cellSizePx
 * @returns {{x: number, y: number}}
 */
export function cellToPixel(col, row, cellSizePx) {
    return {
        x: col * cellSizePx,
        y: row * cellSizePx,
    };
}

/**
 * Convert pixel coordinates to nearest cell.
 * @param {number} px
 * @param {number} py
 * @param {number} cellSizePx
 * @returns {{col: number, row: number}}
 */
export function pixelToCell(px, py, cellSizePx) {
    return {
        col: Math.floor(px / cellSizePx),
        row: Math.floor(py / cellSizePx),
    };
}
```

- [ ] **Step 2: Replace drawGrid, cellPos, snapPt in map-editor.js with shared.js imports**

In `map-editor.js`, replace the `drawGrid()` method (lines 128-143) with a call to shared:

```js
import { drawGrid, setupPanAndZoom, cellPos, snapPt } from './shared.js';
```

Replace the `drawGrid` method:
```js
drawGrid() {
    drawGrid(this.gridLayer, this.gridWidth, this.gridHeight, this.cellSizePx);
}
```

Replace `cellPos()` (lines 774-779):
```js
cellPos() {
    return cellPos(this.stage, this.cellSizePx);
}
```

Replace `snapPt()` (lines 783-785):
```js
snapPt(v) {
    return snapPt(v, this.snap);
}
```

The zoom/pan setup in `setupEvents()` (lines 629-653) stays in map-editor.js because the editor has additional behavior (cursor tracking) tied to scroll. The editor's existing pan/zoom code remains unchanged — shared.js only provides the utility for BattleMap.

- [ ] **Step 3: Verify compilation / no JS errors**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/map/shared.js \
        src/main/resources/static/js/map/map-editor.js
git commit -m "feat: extract shared Konva utilities (grid, cellPos, snapPt) into shared.js"
```

---

### Task 9: Create battle.html template

**Files:**
- Create: `src/main/resources/templates/maps/battle.html`

- [ ] **Step 1: Write the battle.html template**

```html
<!DOCTYPE html>
<html lang="en" data-theme="dark" xmlns:th="http://www.thymeleaf.org">
<head>
    <th:block th:replace="~{fragments/head :: head}"></th:block>
    <title th:text="'DMHelper — ' + ${map.name} + ' (Battle)'">DMHelper — Battle Map</title>
    <style>
        .battle-container {
            display: flex;
            flex-direction: column;
            height: 100vh;
            background: var(--color-bg);
        }
        .battle-toolbar {
            display: flex;
            align-items: center;
            flex-wrap: wrap;
            gap: var(--space-sm);
            padding: var(--space-sm) var(--space-md);
            background: var(--color-surface);
            border-bottom: 1px solid var(--color-border);
            flex-shrink: 0;
        }
        .battle-toolbar .tool-group {
            display: flex;
            align-items: center;
            gap: 4px;
            padding-right: var(--space-md);
            border-right: 1px solid var(--color-border);
        }
        .battle-toolbar .tool-btn {
            padding: 6px 10px;
            font-size: var(--text-sm);
            border: 1px solid var(--color-border);
            border-radius: 4px;
            background: transparent;
            color: var(--color-text);
            cursor: pointer;
        }
        .battle-toolbar .tool-btn:hover { background: var(--color-surface-hover); }
        .battle-toolbar .tool-btn.active {
            background: var(--color-accent);
            border-color: var(--color-accent);
            color: #fff;
        }
        .battle-body {
            display: flex;
            flex: 1;
            overflow: hidden;
        }
        .battle-canvas-wrap {
            flex: 1;
            overflow: hidden;
            position: relative;
            background: #0a0a1a;
        }
        .battle-sidebar {
            width: 240px;
            flex-shrink: 0;
            overflow-y: auto;
            background: var(--color-surface);
            border-left: 1px solid var(--color-border);
            padding: var(--space-sm);
        }
        .battle-statusbar {
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
        .sidebar-section {
            margin-bottom: var(--space-md);
        }
        .sidebar-section h3 {
            font-size: var(--text-sm);
            text-transform: uppercase;
            letter-spacing: 0.03em;
            color: var(--color-text-muted);
            margin: 0 0 var(--space-xs);
        }
        .token-list-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: var(--text-sm);
            cursor: pointer;
        }
        .token-list-item:hover { background: var(--color-surface-hover); }
        .token-swatch {
            width: 14px;
            height: 14px;
            border-radius: 3px;
            display: inline-block;
            border: 1px solid var(--color-border);
            flex-shrink: 0;
            margin-right: 6px;
        }
        .save-indicator.save-saved { color: var(--color-success); }
        .save-indicator.save-saving,
        .save-indicator.save-unsaved { color: var(--color-warning); }
        .save-indicator.save-error { color: var(--color-danger); }
        .hp-input {
            width: 50px;
            padding: 2px 4px;
            font-size: var(--text-sm);
            border: 1px solid var(--color-border);
            border-radius: 3px;
            background: var(--color-bg);
            color: var(--color-text);
            text-align: center;
        }
        .map-switcher select {
            padding: 4px 8px;
            font-size: var(--text-sm);
            background: var(--color-bg);
            color: var(--color-text);
            border: 1px solid var(--color-border);
            border-radius: 4px;
        }
    </style>
</head>
<body>
    <div class="battle-container" x-data="battleToolbar()">
        <div class="battle-toolbar">
            <a th:href="@{/campaigns/{cid}/maps(cid=${campaignId})}" class="btn btn-ghost">&larr; Maps</a>

            <!-- Token tools -->
            <div class="tool-group">
                <button class="tool-btn" :class="{ active: tool === 'select' }" @click="setTool('select')" title="Select/Move tokens">☝ Select</button>
                <button class="tool-btn" :class="{ active: tool === 'measure' }" @click="setTool('measure')" title="Measure distance">↔ Measure</button>
                <button class="tool-btn" @click="addToken()" title="Add a token">＋ Token</button>
                <button class="tool-btn" @click="addParty()" title="Add all active party members to map">👥 Add Party</button>
            </div>

            <!-- AoE templates -->
            <div class="tool-group">
                <button class="tool-btn" :class="{ active: tool === 'cone' }" @click="setTool('cone')" title="Cone template">◤ Cone</button>
                <button class="tool-btn" :class="{ active: tool === 'sphere' }" @click="setTool('sphere')" title="Sphere/Circle template">○ Sphere</button>
                <button class="tool-btn" :class="{ active: tool === 'cube' }" @click="setTool('cube')" title="Cube template">□ Cube</button>
                <button class="tool-btn" :class="{ active: tool === 'line' }" @click="setTool('line')" title="Line template">╱ Line</button>
            </div>

            <!-- Mode toggles -->
            <div class="tool-group">
                <button class="tool-btn" :class="{ active: movementMode === 'GRID' }"
                        @click="toggleMovementMode()"
                        :title="movementMode === 'GRID' ? 'Grid mode: tokens snap to cells' : 'Freeform mode: free pixel movement'">
                    <span x-show="movementMode === 'GRID'">⬡ Grid</span>
                    <span x-show="movementMode === 'FREEFORM'">⊡ Free</span>
                </button>
                <button class="tool-btn" :class="{ active: showGrid }"
                        @click="toggleShowGrid()"
                        title="Toggle grid visibility">
                    <span x-show="showGrid"># Grid On</span>
                    <span x-show="!showGrid"># Grid Off</span>
                </button>
            </div>

            <!-- DM Mode (local to battle map for M5) -->
            <div class="tool-group" style="border-right: none;">
                <button class="tool-btn" :class="{ active: dmMode }"
                        @click="toggleDmMode()"
                        :title="dmMode ? 'DM Mode: all data visible' : 'Player view: hidden tokens dimmed'">
                    <span x-show="dmMode">👁 DM</span>
                    <span x-show="!dmMode">🚫 Player</span>
                </button>
            </div>
        </div>

        <div class="battle-body">
            <div class="battle-canvas-wrap" id="battleCanvasWrap">
                <!-- Konva stage mounts here -->
            </div>

            <div class="battle-sidebar">
                <!-- Map switcher -->
                <div class="sidebar-section" x-show="maps.length > 1">
                    <h3>Maps</h3>
                    <div class="map-switcher">
                        <select x-model="currentMapId" @change="switchMap()">
                            <template x-for="m in maps" :key="m.id">
                                <option :value="m.id" x-text="m.name" :selected="m.id === currentMapId"></option>
                            </template>
                        </select>
                    </div>
                </div>

                <!-- Tokens list -->
                <div class="sidebar-section">
                    <h3>Tokens (<span x-text="tokens.length"></span>)</h3>
                    <template x-for="t in tokens" :key="t.id">
                        <div class="token-list-item" @click="focusToken(t.id)">
                            <span class="token-swatch" :style="{ background: t.color }"></span>
                            <span x-text="t.name" style="flex: 1;"></span>
                            <template x-if="t.currentHp != null && t.maxHp != null">
                                <span x-text="t.currentHp + '/' + t.maxHp"
                                      style="font-size: 0.75rem; color: var(--color-text-muted);"></span>
                            </template>
                            <button @click.stop="deleteToken(t.id)" class="btn btn-ghost" style="padding: 0 4px; font-size: var(--text-sm);">✕</button>
                        </div>
                    </template>
                </div>

                <!-- Selected token details -->
                <div class="sidebar-section" x-show="selectedToken" style="display: none;">
                    <h3>Selected</h3>
                    <div style="font-size: var(--text-sm);">
                        <div class="sidebar-field">
                            <label>Name</label>
                            <input type="text" x-model="selectedToken.name" @change="updateToken()">
                        </div>
                        <div class="sidebar-field">
                            <label>HP</label>
                            <div style="display: flex; gap: 4px;">
                                <input type="number" class="hp-input" x-model="selectedToken.currentHp" @change="updateToken()">
                                <span>/</span>
                                <input type="number" class="hp-input" x-model="selectedToken.maxHp" @change="updateToken()">
                            </div>
                        </div>
                        <div class="sidebar-field">
                            <label>Size</label>
                            <div style="display: flex; gap: 4px;">
                                <select x-model="selectedToken.sizeCols" @change="updateToken()" style="font-size: var(--text-sm);">
                                    <option value="1">1</option>
                                    <option value="2">2</option>
                                    <option value="3">3</option>
                                    <option value="4">4</option>
                                </select>
                                <span>×</span>
                                <select x-model="selectedToken.sizeRows" @change="updateToken()" style="font-size: var(--text-sm);">
                                    <option value="1">1</option>
                                    <option value="2">2</option>
                                    <option value="3">3</option>
                                    <option value="4">4</option>
                                </select>
                            </div>
                        </div>
                        <div class="sidebar-field">
                            <label>Hidden</label>
                            <input type="checkbox" x-model="selectedToken.hidden" @change="updateToken()">
                        </div>
                        <div class="sidebar-field">
                            <label>Color</label>
                            <input type="color" x-model="selectedToken.color" @change="updateToken()">
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="battle-statusbar">
            <span id="battleStatusMessage">Ready</span>
            <span id="battleCursorInfo" style="color: var(--color-text-muted);"></span>
            <span id="battleSaveIndicator" class="save-indicator save-saved">Saved</span>
        </div>
    </div>

    <script th:src="@{/vendor/konva.min.js}"></script>
    <script>
        function battleToolbar() {
            return {
                tool: 'select',
                movementMode: /*[[${map.movementMode}]]*/ 'GRID',
                showGrid: /*[[${map.showGrid}]]*/ true,
                dmMode: true,
                currentMapId: /*[[${map.id}]]*/ '',
                maps: [],
                tokens: [],
                selectedToken: null,

                init() {
                    window.addEventListener('battle-toolchange', (e) => { this.tool = e.detail.tool; });
                    window.addEventListener('battle-tokenupdate', (e) => {
                        this.tokens = e.detail.tokens;
                        if (this.selectedToken) {
                            const updated = this.tokens.find(t => t.id === this.selectedToken.id);
                            if (updated) this.selectedToken = updated;
                        }
                    });
                    window.addEventListener('battle-tokenselect', (e) => { this.selectedToken = e.detail.token; });
                    window.addEventListener('battle-modestate', (e) => {
                        this.movementMode = e.detail.movementMode;
                        this.showGrid = e.detail.showGrid;
                    });
                    window.addEventListener('battle-maplist', (e) => {
                        this.maps = e.detail.maps;
                        this.currentMapId = e.detail.currentMapId;
                    });
                    // Fetch maps list
                    this.loadMaps();
                },

                setTool(t) { window.battleMap?.setTool(t); },

                async addToken() {
                    const name = prompt('Token name:') || 'Token';
                    const kind = prompt('Kind (PC/NPC/MONSTER/OBJECT):', 'NPC') || 'NPC';
                    const colors = { PC: '#4a9eff', NPC: '#2ecc71', MONSTER: '#e74c3c', OBJECT: '#f39c12' };
                    await window.battleMap?.createToken({
                        name, kind, positionX: 0, positionY: 0,
                        sizeCols: 1, sizeRows: 1,
                        color: colors[kind] || '#7b68ee', hidden: false,
                        currentHp: null, maxHp: null,
                    });
                },

                async addParty() {
                    await window.battleMap?.addPartyToMap();
                },

                async deleteToken(id) {
                    if (confirm('Delete this token?')) {
                        await window.battleMap?.deleteToken(id);
                    }
                },

                focusToken(id) { window.battleMap?.selectToken(id); },

                async updateToken() {
                    if (this.selectedToken) {
                        await window.battleMap?.updateToken(this.selectedToken.id, this.selectedToken);
                    }
                },

                async toggleMovementMode() {
                    this.movementMode = this.movementMode === 'GRID' ? 'FREEFORM' : 'GRID';
                    await window.battleMap?.setMovementMode(this.movementMode);
                },

                async toggleShowGrid() {
                    this.showGrid = !this.showGrid;
                    await window.battleMap?.setShowGrid(this.showGrid);
                },

                toggleDmMode() {
                    this.dmMode = !this.dmMode;
                    window.battleMap?.setDmMode(this.dmMode);
                },

                async loadMaps() {
                    try {
                        const resp = await fetch(`/api/v1/campaigns/${/*[[${campaignId}]]*/ ''}/maps`);
                        this.maps = await resp.json();
                    } catch (e) { /* noop */ }
                },

                async switchMap() {
                    window.location.href = `/campaigns/${/*[[${campaignId}]]*/ ''}/maps/${this.currentMapId}/play`;
                },
            };
        }
    </script>
    <script th:src="@{/vendor/alpine.min.js}" defer></script>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/maps/battle.html
git commit -m "feat: add battle.html template with toolbar, sidebar, and map switcher"
```

---

### Task 10: Create battle-map.js — core class, stage setup, token loading

**Files:**
- Create: `src/main/resources/static/js/map/battle-map.js`

- [ ] **Step 1: Write battle-map.js — part 1: imports, typedefs, constructor, stage setup, load**

```js
import { drawGrid, setupPanAndZoom, cellPos, snapPt, snapPixel, cellToPixel, pixelToCell } from './shared.js';

/**
 * @typedef {{id: string, name: string, kind: string, positionX: number, positionY: number,
 *            sizeCols: number, sizeRows: number, color: string, hidden: boolean,
 *            currentHp: number|null, maxHp: number|null, bloodied: boolean}} TokenData
 */

const TERRAIN_COLORS = {
    floor: '#2a2a3e', wall: '#4a4a5e', water: '#1a3a6e',
    difficult: '#3a4a1e', lava: '#6e2a1a', pit: '#1a1a1a', door: '#8a6a2e',
};
const KIND_RING_COLORS = { PC: '#4a9eff', NPC: '#2ecc71', MONSTER: '#e74c3c', OBJECT: '#f39c12' };
const HP_COLORS = { high: '#2ecc71', mid: '#f39c12', low: '#e74c3c' };

export class BattleMap {
    /**
     * @param {{container: HTMLElement, mapId: string, gridWidth: number, gridHeight: number,
     *          cellSizePx: number, movementMode: string, showGrid: boolean,
     *          statusEl?: HTMLElement, saveIndicatorEl?: HTMLElement, cursorInfoEl?: HTMLElement}} opts
     */
    constructor({ container, mapId, gridWidth, gridHeight, cellSizePx, movementMode, showGrid,
                  statusEl, saveIndicatorEl, cursorInfoEl }) {
        this.container = container;
        this.mapId = mapId;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.cellSizePx = cellSizePx;
        this.movementMode = movementMode;
        this.showGrid = showGrid;
        this.dmMode = true;
        this.statusEl = statusEl;
        this.saveIndicatorEl = saveIndicatorEl;
        this.cursorInfoEl = cursorInfoEl;

        /** @type {TokenData[]} */
        this.tokens = [];
        this.docVersion = 0;
        this.activeTool = 'select';
        this.selectedTokenId = null;

        // Token Konva nodes: tokenId -> { group, body, label, hpBar, hpText, ring }
        this.tokenNodes = {};

        // AoE template Konva nodes on the preview layer
        this.aoeNodes = [];
        // Measurement
        this.measureLine = null;
        this.measureLabel = null;

        this.stage = null;
        this.gridLayer = null;
        this.terrainLayer = null;
        this.tokenLayer = null;
        this.annotationLayer = null;
        this.previewLayer = null;
    }

    /** Initialize the Konva stage and layers. */
    async load() {
        this.stage = new Konva.Stage({
            container: this.container,
            width: this.container.clientWidth,
            height: this.container.clientHeight,
            draggable: false,
        });

        this.terrainLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.terrainLayer);

        this.gridLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.gridLayer);

        this.tokenLayer = new Konva.Layer();
        this.stage.add(this.tokenLayer);

        this.annotationLayer = new Konva.Layer();
        this.stage.add(this.annotationLayer);

        this.previewLayer = new Konva.Layer();
        this.stage.add(this.previewLayer);

        setupPanAndZoom(this.stage, this.container);
        this.setupEvents();
        await this.fetchMapDocument();
        await this.fetchTokens();
        this.renderGrid();
        this.renderTokens();
        this.emitState();
    }

    emit(event, detail) {
        window.dispatchEvent(new CustomEvent('battle-' + event, { detail }));
    }

    emitState() {
        this.emit('modestate', { movementMode: this.movementMode, showGrid: this.showGrid });
        this.emit('tokenupdate', { tokens: this.tokens });
    }
```

- [ ] **Step 2: Write battle-map.js — part 2: map document fetch, terrain render, grid render**

```js
    async fetchMapDocument() {
        const resp = await fetch(`/api/v1/maps/${this.mapId}/document`);
        const data = await resp.json();
        this.docVersion = data.version;
        this.renderTerrain(data.document);
    }

    renderTerrain(doc) {
        this.terrainLayer.destroyChildren();
        if (!doc || !doc.layers) return;
        const terrainLayer = doc.layers.find(l => l.id === 'terrain' && l.type === 'TERRAIN');
        if (!terrainLayer) return;
        const s = this.cellSizePx;
        for (const cell of (terrainLayer.cells || [])) {
            const color = TERRAIN_COLORS[cell.terrain] || '#2a2a3e';
            const rect = new Konva.Rect({
                x: cell.col * s, y: cell.row * s,
                width: s, height: s,
                fill: color, stroke: '#222', strokeWidth: 0.5,
            });
            this.terrainLayer.add(rect);
        }
        this.terrainLayer.batchDraw();
    }

    renderGrid() {
        if (this.showGrid) {
            drawGrid(this.gridLayer, this.gridWidth, this.gridHeight, this.cellSizePx);
            this.gridLayer.show();
        } else {
            this.gridLayer.hide();
        }
        this.gridLayer.batchDraw();
    }
```

- [ ] **Step 3: Write battle-map.js — part 3: token fetch, render, drag**

```js
    async fetchTokens() {
        const resp = await fetch(`/api/v1/maps/${this.mapId}/tokens`);
        this.tokens = await resp.json();
    }

    /** Render all tokens on the token layer. */
    renderTokens() {
        // Clear old nodes
        for (const nodes of Object.values(this.tokenNodes)) {
            for (const node of [nodes.group]) {
                node.destroy();
            }
        }
        this.tokenNodes = {};
        this.tokenLayer.destroyChildren();

        for (const token of this.tokens) {
            this.addTokenNode(token);
        }
        this.tokenLayer.batchDraw();
    }

    /**
     * Create Konva nodes for a single token.
     * @param {TokenData} token
     */
    addTokenNode(token) {
        const s = this.cellSizePx;
        const px = token.positionX;
        const py = token.positionY;
        const w = token.sizeCols * s;
        const h = token.sizeRows * s;

        const group = new Konva.Group({ x: px, y: py, draggable: true, name: 'token' });
        group._tokenId = token.id;  // stash for events

        // Body
        const body = new Konva.Rect({
            width: w, height: h,
            fill: token.color || '#7b68ee',
            stroke: KIND_RING_COLORS[token.kind] || '#fff',
            strokeWidth: 2,
            cornerRadius: 4,
            opacity: this.dmMode ? 1 : (token.hidden ? 0.3 : 1),
        });
        group.add(body);

        // Bloodied ring
        const ring = new Konva.Rect({
            width: w + 4, height: h + 4,
            x: -2, y: -2,
            stroke: '#e74c3c',
            strokeWidth: 2,
            cornerRadius: 4,
            fillEnabled: false,
            visible: token.bloodied,
            listening: false,
        });
        group.add(ring);

        // Name label
        const label = new Konva.Text({
            text: token.name.substring(0, 2),
            fontSize: Math.min(w, h) * 0.4,
            fill: '#fff',
            align: 'center',
            verticalAlign: 'middle',
            width: w,
            height: h,
        });
        group.add(label);

        // HP bar
        const hpBarHeight = 4;
        const hasHp = token.currentHp != null && token.maxHp != null && token.maxHp > 0;
        const hpBar = new Konva.Rect({
            y: h,
            width: w,
            height: hpBarHeight,
            fill: '#2ecc71',
            visible: this.dmMode && hasHp,
        });
        group.add(hpBar);

        // HP text (below HP bar)
        const hpText = new Konva.Text({
            y: h + hpBarHeight + 2,
            text: hasHp ? `${token.currentHp}/${token.maxHp}` : '',
            fontSize: 10,
            fill: '#ccc',
            align: 'center',
            width: w,
            visible: this.dmMode && hasHp,
        });
        group.add(hpText);

        // Update HP bar color
        if (hasHp) {
            const ratio = token.currentHp / token.maxHp;
            hpBar.fill(ratio > 0.5 ? HP_COLORS.high : ratio > 0.25 ? HP_COLORS.mid : HP_COLORS.low);
            hpBar.width(w * Math.max(0, ratio));
        }

        // Drag handlers
        group.on('dragstart', () => {
            group._dragStartX = px;
            group._dragStartY = py;
        });
        group.on('dragmove', () => {
            // Show position in status bar
            if (this.cursorInfoEl) {
                const cell = pixelToCell(group.x(), group.y(), s);
                this.cursorInfoEl.textContent = `(${cell.col}, ${cell.row})`;
            }
        });
        group.on('dragend', () => {
            let nx = group.x();
            let ny = group.y();
            if (this.movementMode === 'GRID') {
                nx = snapPixel(nx, s, true);
                ny = snapPixel(ny, s, true);
                group.x(nx);
                group.y(ny);
            }
            this.tokenLayer.batchDraw();
            this.saveTokenMove(token.id, Math.round(nx), Math.round(ny));
        });

        // Click to select
        group.on('click tap', () => {
            this.selectToken(token.id);
        });

        this.tokenLayer.add(group);
        this.tokenNodes[token.id] = { group, body, label, hpBar, hpText, ring };
        return group;
    }

    async saveTokenMove(tokenId, x, y) {
        const token = this.tokens.find(t => t.id === tokenId);
        if (!token) return;
        token.positionX = x;
        token.positionY = y;
        this.emit('tokenupdate', { tokens: this.tokens });
        try {
            await fetch(`/api/v1/tokens/${tokenId}/move`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ positionX: x, positionY: y }),
            });
            if (this.saveIndicatorEl) {
                this.saveIndicatorEl.textContent = 'Saved';
                this.saveIndicatorEl.className = 'save-indicator save-saved';
            }
        } catch (e) {
            if (this.saveIndicatorEl) {
                this.saveIndicatorEl.textContent = 'Save error';
                this.saveIndicatorEl.className = 'save-indicator save-error';
            }
        }
    }
```

- [ ] **Step 4: Write battle-map.js — part 4: tool switching, events**

```js
    setupEvents() {
        const s = this.cellSizePx;

        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.button !== 0) return; // only left click
            const pos = this.stage.getRelativePointerPosition();
            if (!pos) return;

            if (this.activeTool === 'measure') {
                this.startMeasure(pos);
            } else if (['cone', 'sphere', 'cube', 'line'].includes(this.activeTool)) {
                this.startAoeTemplate(pos);
            }
        });

        this.stage.on('mousemove touchmove', () => {
            const p = cellPos(this.stage, s);
            if (p && this.cursorInfoEl) {
                this.cursorInfoEl.textContent = `(${p.col}, ${p.row})`;
            }
            if (this.activeTool === 'measure' && this.measureLine) {
                this.updateMeasure();
            }
            if (['cone', 'sphere', 'cube', 'line'].includes(this.activeTool) && this.aoeNodes.length) {
                this.updateAoeTemplate();
            }
        });

        this.stage.on('mouseup touchend', () => {
            if (this.activeTool === 'measure' && this.measureLine) {
                this.finishMeasure();
            }
            if (['cone', 'sphere', 'cube', 'line'].includes(this.activeTool) && this.aoeNodes.length) {
                this.finishAoeTemplate();
            }
        });

        // Click on empty space deselects
        this.stage.on('click tap', (e) => {
            if (e.target === this.stage || e.target.getParent()?.name() !== 'token') {
                this.deselectToken();
            }
        });
    }

    setTool(tool) {
        this.activeTool = tool;
        this.container.style.cursor = tool === 'measure' ? 'crosshair' : 'default';
        this.emit('toolchange', { tool });
    }

    setMovementMode(mode) {
        this.movementMode = mode;
        this.emit('modestate', { movementMode: mode, showGrid: this.showGrid });
    }

    setShowGrid(show) {
        this.showGrid = show;
        this.renderGrid();
        this.emit('modestate', { movementMode: this.movementMode, showGrid: show });
    }

    setDmMode(dm) {
        this.dmMode = dm;
        this.renderTokens();
    }
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/map/battle-map.js
git commit -m "feat: add battle-map.js core class with stage, terrain, grid, token rendering and drag"
```

---

### Task 11: Add token creation, update, delete, and selection methods to battle-map.js

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js`

- [ ] **Step 1: Append token CRUD methods**

```js
    async createToken(req) {
        try {
            const resp = await fetch(`/api/v1/maps/${this.mapId}/tokens`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(req),
            });
            const token = await resp.json();
            this.tokens.push(token);
            this.addTokenNode(token);
            this.tokenLayer.batchDraw();
            this.emit('tokenupdate', { tokens: this.tokens });
        } catch (e) {
            console.error('Failed to create token:', e);
        }
    }

    async deleteToken(id) {
        try {
            await fetch(`/api/v1/tokens/${id}`, { method: 'DELETE' });
            const node = this.tokenNodes[id];
            if (node) { node.group.destroy(); delete this.tokenNodes[id]; }
            this.tokens = this.tokens.filter(t => t.id !== id);
            this.tokenLayer.batchDraw();
            if (this.selectedTokenId === id) this.deselectToken();
            this.emit('tokenupdate', { tokens: this.tokens });
        } catch (e) {
            console.error('Failed to delete token:', e);
        }
    }

    selectToken(id) {
        this.selectedTokenId = id;
        const token = this.tokens.find(t => t.id === id);
        this.emit('tokenselect', { token });

        // Highlight selected token
        for (const [tid, nodes] of Object.entries(this.tokenNodes)) {
            nodes.body.stroke(tid === id ? '#ff0' : KIND_RING_COLORS[this.tokens.find(t => t.id === tid)?.kind] || '#fff');
            nodes.body.strokeWidth(tid === id ? 3 : 2);
        }
        this.tokenLayer.batchDraw();
    }

    deselectToken() {
        this.selectedTokenId = null;
        this.emit('tokenselect', { token: null });
        for (const [tid, nodes] of Object.entries(this.tokenNodes)) {
            nodes.body.stroke(KIND_RING_COLORS[this.tokens.find(t => t.id === tid)?.kind] || '#fff');
            nodes.body.strokeWidth(2);
        }
        this.tokenLayer.batchDraw();
    }

    async updateToken(id, data) {
        try {
            const resp = await fetch(`/api/v1/tokens/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data),
            });
            const updated = await resp.json();
            const idx = this.tokens.findIndex(t => t.id === id);
            if (idx >= 0) this.tokens[idx] = updated;
            this.renderTokens();
            if (this.selectedTokenId === id) {
                this.emit('tokenselect', { token: updated });
            }
            this.emit('tokenupdate', { tokens: this.tokens });
        } catch (e) {
            console.error('Failed to update token:', e);
        }
    }

    async addPartyToMap() {
        try {
            const resp = await fetch(`/api/v1/maps/${this.mapId}/tokens/add-party`, { method: 'POST' });
            this.tokens = await resp.json();
            this.renderTokens();
            this.emit('tokenupdate', { tokens: this.tokens });
        } catch (e) {
            console.error('Failed to add party:', e);
        }
    }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/js/map/battle-map.js
git commit -m "feat: add token CRUD, selection, and addPartyToMap methods to battle-map.js"
```

---

### Task 12: Add AoE templates to battle-map.js

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js`

- [ ] **Step 1: Append AoE template methods**

```js
    // AoE template state
    aoeStartPos = null;    // {x, y} in pixels
    aoeRadius = 60;        // default 15ft cone → 3 cells * cellSizePx

    startAoeTemplate(pos) {
        this.aoeStartPos = pos;
        this.clearAoeNodes();
        this.aoeRadius = 2 * this.cellSizePx; // default ~10ft radius
        this.drawAoeTemplate(pos, this.aoeRadius);
    }

    drawAoeTemplate(pos, radius) {
        this.clearAoeNodes();
        const s = this.cellSizePx;
        const mode = this.movementMode;
        const snap = mode === 'GRID';

        let node;
        switch (this.activeTool) {
            case 'sphere':
                node = new Konva.Circle({
                    x: snap ? snapPixel(pos.x, s, true) + s/2 : pos.x,
                    y: snap ? snapPixel(pos.y, s, true) + s/2 : pos.y,
                    radius,
                    fill: 'rgba(255, 100, 100, 0.2)',
                    stroke: 'rgba(255, 100, 100, 0.6)',
                    strokeWidth: 2,
                    listening: false,
                });
                break;
            case 'cone': {
                // Cone pointing in drag direction
                const angle = Math.atan2(0, -radius); // default upward
                const cx = snap ? snapPixel(pos.x, s, true) + s/2 : pos.x;
                const cy = snap ? snapPixel(pos.y, s, true) + s/2 : pos.y;
                node = new Konva.Wedge({
                    x: cx, y: cy,
                    radius, angle: 53,
                    rotation: -26.5, // centered upward
                    fill: 'rgba(255, 100, 100, 0.2)',
                    stroke: 'rgba(255, 100, 100, 0.6)',
                    strokeWidth: 2,
                    listening: false,
                });
                break;
            }
            case 'cube':
                node = new Konva.Rect({
                    x: snap ? snapPixel(pos.x, s, true) : pos.x,
                    y: snap ? snapPixel(pos.y, s, true) : pos.y,
                    width: radius, height: radius,
                    fill: 'rgba(100, 100, 255, 0.2)',
                    stroke: 'rgba(100, 100, 255, 0.6)',
                    strokeWidth: 2,
                    listening: false,
                });
                break;
            case 'line':
                node = new Konva.Line({
                    points: [pos.x, pos.y, pos.x + radius, pos.y],
                    stroke: 'rgba(255, 255, 100, 0.6)',
                    strokeWidth: Math.max(2, s / 4),
                    lineCap: 'round',
                    listening: false,
                });
                break;
        }
        if (node) {
            this.previewLayer.add(node);
            this.aoeNodes.push(node);
            this.previewLayer.batchDraw();
        }
    }

    updateAoeTemplate() {
        const pos = this.stage.getRelativePointerPosition();
        if (!pos || !this.aoeStartPos) return;
        const dx = pos.x - this.aoeStartPos.x;
        const dy = pos.y - this.aoeStartPos.y;
        const radius = Math.max(this.cellSizePx, Math.sqrt(dx * dx + dy * dy));
        this.aoeRadius = radius;
        this.drawAoeTemplate(this.aoeStartPos, radius);
    }

    finishAoeTemplate() {
        // Template stays on preview layer until cleared or tool changes
        this.aoeStartPos = null;
    }

    clearAoeNodes() {
        for (const node of this.aoeNodes) node.destroy();
        this.aoeNodes = [];
        this.previewLayer.batchDraw();
    }
```

- [ ] **Step 2: Clear AoE templates when switching away from AoE tools. Add to setTool():**

In the `setTool()` method, add before `this.emit`:

```js
    if (!['cone', 'sphere', 'cube', 'line'].includes(tool)) {
        this.clearAoeNodes();
    }
    if (tool !== 'measure') {
        this.clearMeasure();
    }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/js/map/battle-map.js
git commit -m "feat: add AoE template tools (cone, sphere, cube, line) to battle-map.js"
```

---

### Task 13: Add measurement tool to battle-map.js

**Files:**
- Modify: `src/main/resources/static/js/map/battle-map.js`

- [ ] **Step 1: Append measurement methods**

```js
    // Measurement state
    measureStart = null;  // {x, y} in pixels

    startMeasure(pos) {
        this.clearMeasure();
        this.measureStart = pos;
        this.measureLine = new Konva.Line({
            points: [pos.x, pos.y, pos.x, pos.y],
            stroke: '#4a9eff',
            strokeWidth: 2,
            dash: [6, 4],
            listening: false,
        });
        this.previewLayer.add(this.measureLine);

        this.measureLabel = new Konva.Text({
            text: '',
            fontSize: 13,
            fill: '#4a9eff',
            listening: false,
        });
        this.previewLayer.add(this.measureLabel);
        this.previewLayer.batchDraw();
    }

    updateMeasure() {
        const pos = this.stage.getRelativePointerPosition();
        if (!pos || !this.measureStart || !this.measureLine) return;

        const s = this.cellSizePx;
        let x2 = pos.x;
        let y2 = pos.y;
        if (this.movementMode === 'GRID') {
            x2 = snapPixel(pos.x, s, true);
            y2 = snapPixel(pos.y, s, true);
        }
        this.measureLine.points([this.measureStart.x, this.measureStart.y, x2, y2]);

        const dx = x2 - this.measureStart.x;
        const dy = y2 - this.measureStart.y;
        const distPx = Math.sqrt(dx * dx + dy * dy);
        const cells = distPx / s;
        const feet = Math.round(cells * 5);

        let label;
        if (this.movementMode === 'GRID') {
            label = `${Math.round(cells * 10) / 10} cells (${feet} ft)`;
        } else {
            label = `${feet} ft`;
        }
        this.measureLabel.text(label);
        this.measureLabel.position({
            x: (this.measureStart.x + x2) / 2 + 5,
            y: (this.measureStart.y + y2) / 2 - 15,
        });
        this.previewLayer.batchDraw();
    }

    finishMeasure() {
        // Measurement stays visible until next measure or tool change
        this.measureStart = null;
    }

    clearMeasure() {
        if (this.measureLine) { this.measureLine.destroy(); this.measureLine = null; }
        if (this.measureLabel) { this.measureLabel.destroy(); this.measureLabel = null; }
        this.measureStart = null;
        this.previewLayer.batchDraw();
    }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/static/js/map/battle-map.js
git commit -m "feat: add measurement tool to battle-map.js"
```

---

### Task 14: Wire battle-map.js initialization into battle.html

**Files:**
- Modify: `src/main/resources/templates/maps/battle.html`

- [ ] **Step 1: Add the JS module import at the bottom of battle.html, before `</body>`**

```html
    <script type="module" th:inline="javascript">
        import { BattleMap } from '/js/map/battle-map.js';

        const battleMap = new BattleMap({
            container: document.getElementById('battleCanvasWrap'),
            mapId: /*[[${map.id}]]*/ '',
            gridWidth: /*[[${map.gridWidth}]]*/ 30,
            gridHeight: /*[[${map.gridHeight}]]*/ 20,
            cellSizePx: /*[[${map.cellSizePx}]]*/ 48,
            movementMode: /*[[${map.movementMode}]]*/ 'GRID',
            showGrid: /*[[${map.showGrid}]]*/ true,
            statusEl: document.getElementById('battleStatusMessage'),
            saveIndicatorEl: document.getElementById('battleSaveIndicator'),
            cursorInfoEl: document.getElementById('battleCursorInfo'),
        });
        window.battleMap = battleMap;
        battleMap.load();
    </script>
```

- [ ] **Step 2: Verify the template compiles**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/maps/battle.html
git commit -m "feat: wire battle-map.js initialization into battle.html"
```

---

### Task 15: Add play button to map card

**Files:**
- Modify: `src/main/resources/templates/maps/_card.html`

- [ ] **Step 1: Add a "Play" button to the map card**

Read the existing `_card.html` to find the edit link pattern, then add a play button.

Assuming the existing card has something like:
```html
<a th:href="@{/campaigns/{cid}/maps/{mid}/edit(cid=${campaignId}, mid=${map.id})}" class="btn btn-ghost">Edit</a>
```

Add after it:
```html
<a th:href="@{/campaigns/{cid}/maps/{mid}/play(cid=${campaignId}, mid=${map.id})}" class="btn btn-primary">Play</a>
```

- [ ] **Step 2: Verify the card renders**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/maps/_card.html
git commit -m "feat: add Play button to map card linking to battle map"
```

---

### Task 16: Write TokenService tests

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/TokenServiceTest.java`

- [ ] **Step 1: Write test class**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService.*;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({TokenService.class, GameMapService.class})
class TokenServiceTest {

    @Autowired private TokenService tokenService;
    @Autowired private GameMapService mapService;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;
    private GameMap map;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        em.persist(campaign);
        em.flush();
        map = mapService.create(campaign.getId(), "Test Map", 30, 20, 48);
    }

    @Test
    void shouldCreateToken() {
        var req = new TokenCreateRequest("Goblin", "MONSTER", 100, 200, 1, 1,
                "#e74c3c", false, 7, 7, null, null);
        TokenDto dto = tokenService.create(map.getId(), req);

        assertThat(dto.id()).isNotNull();
        assertThat(dto.name()).isEqualTo("Goblin");
        assertThat(dto.kind()).isEqualTo("MONSTER");
        assertThat(dto.positionX()).isEqualTo(100);
        assertThat(dto.positionY()).isEqualTo(200);
        assertThat(dto.maxHp()).isEqualTo(7);
        assertThat(dto.currentHp()).isEqualTo(7);
        assertThat(dto.bloodied()).isFalse();
    }

    @Test
    void shouldDetectBloodied() {
        var req = new TokenCreateRequest("Hurt Goblin", "MONSTER", 0, 0, 1, 1,
                "#e74c3c", false, 3, 10, null, null);
        TokenDto dto = tokenService.create(map.getId(), req);
        assertThat(dto.bloodied()).isTrue();
    }

    @Test
    void shouldMoveToken() {
        var req = new TokenCreateRequest("Mover", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null, null, null);
        TokenDto created = tokenService.create(map.getId(), req);

        TokenDto moved = tokenService.move(created.id(), new TokenMoveRequest(500, 300));
        assertThat(moved.positionX()).isEqualTo(500);
        assertThat(moved.positionY()).isEqualTo(300);
    }

    @Test
    void shouldUpdateHp() {
        var req = new TokenCreateRequest("Healer", "PC", 0, 0, 1, 1,
                "#4a9eff", false, 10, 12, null, null);
        TokenDto created = tokenService.create(map.getId(), req);

        TokenDto updated = tokenService.updateHp(created.id(), new TokenHpRequest(5));
        assertThat(updated.currentHp()).isEqualTo(5);
        assertThat(updated.bloodied()).isTrue();
    }

    @Test
    void shouldDeleteToken() {
        var req = new TokenCreateRequest("DeleteMe", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null, null, null);
        TokenDto created = tokenService.create(map.getId(), req);

        tokenService.delete(created.id());

        assertThatThrownBy(() -> tokenService.findEntityById(created.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldListTokensByMap() {
        tokenService.create(map.getId(), new TokenCreateRequest("A", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null, null, null));
        tokenService.create(map.getId(), new TokenCreateRequest("B", "NPC", 100, 100, 1, 1,
                "#fff", false, null, null, null, null));

        List<TokenDto> tokens = tokenService.findByMapId(map.getId());
        assertThat(tokens).hasSize(2);
        assertThat(tokens.get(0).name()).isEqualTo("A");
        assertThat(tokens.get(1).name()).isEqualTo("B");
    }

    @Test
    void shouldFindEntityByIdOrThrow() {
        var req = new TokenCreateRequest("FindMe", "NPC", 0, 0, 1, 1,
                "#fff", false, null, null, null, null);
        TokenDto created = tokenService.create(map.getId(), req);

        Token entity = tokenService.findEntityById(created.id());
        assertThat(entity.getName()).isEqualTo("FindMe");
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl . -Dtest=TokenServiceTest -q`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/TokenServiceTest.java
git commit -m "test: add TokenService tests for CRUD, HP, bloodied, and move"
```

---

### Task 17: Write TokenApiController tests

**Files:**
- Create: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/TokenApiControllerTest.java`

- [ ] **Step 1: Write test class**

```java
package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.TokenService.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TokenApiController.class)
class TokenApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TokenService service;

    private TokenDto token(UUID id, String name) {
        return new TokenDto(id, name, "NPC", 0, 0, 1, 1, "#fff", false, 10, 10, false, null, null);
    }

    @Test
    void shouldListTokens() throws Exception {
        UUID mapId = UUID.randomUUID();
        when(service.findByMapId(mapId)).thenReturn(List.of(token(UUID.randomUUID(), "Goblin")));

        mockMvc.perform(get("/api/v1/maps/{mapId}/tokens", mapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Goblin"));
    }

    @Test
    void shouldCreateToken() throws Exception {
        UUID mapId = UUID.randomUUID();
        TokenDto dto = token(UUID.randomUUID(), "Goblin");
        when(service.create(eq(mapId), any())).thenReturn(dto);
        String body = """
                {"name":"Goblin","kind":"MONSTER","positionX":100,"positionY":200,
                 "sizeCols":1,"sizeRows":1,"color":"#e74c3c","hidden":false,
                 "currentHp":7,"maxHp":7}""";

        mockMvc.perform(post("/api/v1/maps/{mapId}/tokens", mapId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Goblin"));
    }

    @Test
    void shouldMoveToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.move(eq(id), any())).thenReturn(token(id, "Moved"));
        String body = """
                {"positionX":500,"positionY":300}""";

        mockMvc.perform(patch("/api/v1/tokens/{id}/move", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Moved"));
    }

    @Test
    void shouldDeleteToken() throws Exception {
        mockMvc.perform(delete("/api/v1/tokens/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldAddPartyToMap() throws Exception {
        UUID mapId = UUID.randomUUID();
        when(service.addPartyToMap(mapId)).thenReturn(List.of());
        mockMvc.perform(post("/api/v1/maps/{mapId}/tokens/add-party", mapId))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl . -Dtest=TokenApiControllerTest -q`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/TokenApiControllerTest.java
git commit -m "test: add TokenApiController tests for list, create, move, delete, add-party"
```

---

### Task 18: Update existing tests for movementMode/showGrid

**Files:**
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java`

- [ ] **Step 1: Add movementMode/defaults test to GameMapServiceTest**

```java
@Test
void shouldDefaultMovementModeAndShowGrid() {
    GameMap map = service.create(campaign.getId(), "Battle", 30, 20, 48);

    assertThat(map.getMovementMode()).isEqualTo("GRID");
    assertThat(map.isShowGrid()).isTrue();
}

@Test
void shouldUpdateMovementMode() {
    GameMap map = service.create(campaign.getId(), "ModeMap", 10, 10, 48);
    service.updateMode(map.getId(), "FREEFORM", false);

    GameMap updated = service.findById(map.getId());
    assertThat(updated.getMovementMode()).isEqualTo("FREEFORM");
    assertThat(updated.isShowGrid()).isFalse();
}

@Test
void shouldRejectInvalidMovementMode() {
    GameMap map = service.create(campaign.getId(), "BadMode", 10, 10, 48);

    assertThatThrownBy(() -> service.updateMode(map.getId(), "HEX", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid movementMode");
}
```

- [ ] **Step 2: Update GameMapApiControllerTest to verify new fields in response**

Add to `shouldListMaps()`: `.andExpect(jsonPath("$[0].movementMode").value("GRID"))`

Add a new test:

```java
@Test
void shouldUpdateMovementMode() throws Exception {
    UUID id = UUID.randomUUID();
    GameMap m = map("Tavern", 0);
    m.setMovementMode("FREEFORM");
    m.setShowGrid(false);
    when(service.updateMode(eq(id), eq("FREEFORM"), eq(false))).thenReturn(m);
    String body = """
            {"movementMode":"FREEFORM","showGrid":false}""";

    mockMvc.perform(patch("/api/v1/maps/{id}", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.movementMode").value("FREEFORM"))
            .andExpect(jsonPath("$.showGrid").value(false));
}
```

- [ ] **Step 3: Add play route test to GameMapControllerTest**

```java
@Test
void shouldRenderBattlePage() throws Exception {
    GameMap m = map("Tavern");
    when(service.findById(m.getId())).thenReturn(m);

    mockMvc.perform(get("/campaigns/{campaignId}/maps/{mapId}/play", UUID.randomUUID(), m.getId()))
            .andExpect(status().isOk())
            .andExpect(view().name("maps/battle"));
}
```

- [ ] **Step 4: Run all tests**

Run: `mvn test -pl . -Dtest="GameMapServiceTest,GameMapApiControllerTest,GameMapControllerTest" -q`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapApiControllerTest.java \
        src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/web/GameMapControllerTest.java
git commit -m "test: add tests for movementMode/showGrid and battle map play route"
```

---

### Task 19: Integration smoke test — run the app and verify

**Files:** None (manual test)

- [ ] **Step 1: Build and run the app**

```bash
mvn clean package -DskipTests -q && java -jar target/dmhelper-*.jar
```

Expected: App starts, opens browser

- [ ] **Step 2: Create a campaign and map, then open /play**

- Create a campaign "Smoke Test"
- Create a map "Battlefield" (30×20, 48px cells)
- Click "Play" on the map card
- Expected: Battle map page loads with terrain grid, toolbar, sidebar

- [ ] **Step 3: Add tokens and move them**

- Click "+ Token", name it "Goblin", kind "MONSTER"
- Expected: Token appears in top-left corner
- Drag the token across the map
- Expected: Token snaps to grid cells (default mode)
- Expected: Position updates in status bar

- [ ] **Step 4: Toggle freeform mode**

- Click "Grid" button to switch to "Free"
- Drag a token
- Expected: Token moves freely without grid snapping

- [ ] **Step 5: Toggle grid visibility**

- Click "Grid On" button to switch to "Grid Off"
- Expected: Grid lines disappear, tokens remain

- [ ] **Step 6: Test AoE templates**

- Select "Sphere" tool, click and drag on canvas
- Expected: A semi-transparent red circle appears
- Select "Cone", click and drag
- Expected: A cone appears

- [ ] **Step 7: Test measurement**

- Select "Measure" tool, click and drag
- Expected: Dashed line with distance label appears

- [ ] **Step 8: Test map switching**

- Create another map in the same campaign
- Use the map switcher dropdown in the battle map sidebar
- Expected: Page navigates to the new map's battle page

- [ ] **Step 9: Stop the app**

Press Ctrl+C in the terminal.

- [ ] **Step 10: Commit (if any fixes were made)**

```bash
git add -A
git commit -m "fix: battle map integration fixes from smoke test"
```

---

### Task 20: Run full test suite and verify

**Files:** None (verification only)

- [ ] **Step 1: Run all tests**

```bash
mvn test -pl .
```

Expected: All tests pass (BUILD SUCCESS)

- [ ] **Step 2: Verify no compilation warnings**

```bash
mvn compile -pl .
```

Expected: BUILD SUCCESS with no warnings

---

## Completion Checklist

After all tasks are done, verify:

- [ ] `mvn test` passes all tests
- [ ] `mvn package -DskipTests` produces a runnable JAR
- [ ] Battle map page loads at `/campaigns/{cid}/maps/{mid}/play`
- [ ] Tokens can be created, moved (grid and freeform), HP-edited, and deleted
- [ ] Grid visibility toggles independently of movement mode
- [ ] AoE templates (cone, sphere, cube, line) render and can be cleared
- [ ] Measurement tool shows distance in cells+feet (grid) or feet-only (freeform)
- [ ] Map switcher navigates between maps
- [ ] "Add Party" creates tokens from party roster
- [ ] Hidden tokens dim in player mode
- [ ] Bloodied indicator (red ring) shows when HP ≤ half max
- [ ] Sidebar shows token list and selected-token edit panel
- [ ] Play button on map cards links to battle page
