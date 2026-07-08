# Map Editor Polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the functionality, UI, and UX gaps identified in review of the M4 Konva.js map editor (`static/js/map/map-editor.js`, `templates/maps/editor.html`): shape selection/editing, faster terrain painting, discoverable erase, data-loss prevention, primitive authoring tools, background images, export, and toolbar polish.

**Architecture:** All work extends the existing single-class `MapEditor` (Konva.js island) and its Alpine.js `toolbar()` bridge in `editor.html`, following the pattern already established by M4. No new build tooling, no new persisted entities — the map document (`MapDocumentDto`, stored as a CLOB on `GameMap`) grows by one optional field (an embedded background image). Every change autosaves through the existing debounced, optimistic-locked `PUT /api/v1/maps/{id}/document` endpoint.

**Tech Stack:** Spring Boot 4.1.0, Java 25, Jackson 3, Konva.js 9.3.18 (vendored, no npm/CDN), Alpine.js (vendored), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- **Spring Boot 4.1.0 / Java 25.** Jackson 3: databind types from `tools.jackson.databind` (`ObjectMapper`, `JsonMapper.builder()`); annotations stay in `com.fasterxml.jackson.annotation` (`@JsonProperty`, `@JsonInclude`).
- **Boot 4 test-slice packages** (verified in this codebase): `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `org.springframework.test.context.bean.override.mockito.MockitoBean`.
- **No npm, no CDN.** `konva.min.js` and `alpine.min.js` are vendored under `static/vendor/`; all new JS is hand-written ES modules under `static/js/map/`.
- **No JS test runner exists in this repo** (no `package.json`, no Jest/Vitest/Playwright). Every JS-only task is verified manually against the running dev server (`mvn spring-boot:run`, app on port 8081) with exact click/keyboard steps and expected visual results — do not invent a new test framework as a side effect of this plan. Java-side changes (DTOs, services, controllers) keep using JUnit 5 as the existing suite does.
- **Thymeleaf does not process raw `hx-*` / inline JS URL attributes** — this plan adds none; existing patterns (`th:inline="javascript"`, `th:src="@{...}"`) are reused as-is.
- **Errors are RFC 9457 problem+json**, produced centrally by `common/web/GlobalExceptionHandler`. No new `@ExceptionHandler`s.
- **App port is 8081** (`application.properties`).
- **JSDoc annotations** on JS islands, matching the existing `@typedef` block at the top of `map-editor.js`.
- Keep `MapEditor` a single class (matches the established M4 pattern) except where a piece of logic is naturally pure/standalone (flood-fill), which gets its own small module for clarity and reuse.

## File Structure

| File | Change |
|---|---|
| `src/main/resources/static/js/map/map-editor.js` | Heavily extended: brush engine, erase, terrain fill tools, shape selection/transform, label editing, bulk-selection move/cut, primitive tools, image layer, PNG export, cursor feedback, undo/redo state events, touch pan/zoom. |
| `src/main/resources/static/js/map/flood-fill.js` | **New.** Pure function for terrain bucket-fill — no Konva/DOM dependency, easy to reason about in isolation. |
| `src/main/resources/static/js/map/terrain-palette.js` | Adds an `ERASE_KEY` sentinel export used by the palette UI. |
| `src/main/resources/templates/maps/editor.html` | Toolbar additions (fill/bucket/room/door/region tools, image import, export, terrain swatches + management popover), right sidebar (layers + shape properties), status bar (coords/zoom), shortcut help overlay, save-indicator states. |
| `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapLayerDto.java` | Adds an optional `ImageDto image` field for the background-image layer. |
| `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java` | New test covering image-layer document round-trip. |

Tasks are ordered so each one is independently shippable and later tasks build on earlier ones (selection before properties panel, brush engine before erase, etc.). Follow the order.

---

### Task 1: Brush engine — cell index, brush size, stroke interpolation

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:**
- Produces: `this.cellIndex` (`Map<string, Konva.Rect>` keyed by `"col,row"` per terrain layer id), `this.brushSize` (number, 1/2/3), `MapEditor.setBrushSize(n)`, `paintCell(col, row, opts)` now accepts `{ erase?: boolean }`.
- Consumes: nothing new from other tasks (this is the foundation Task 2 builds on).

The current `paintCell` does a linear scan of every child node in the active Konva layer to find the cell being overwritten (`map-editor.js` inside `paintCell`), and only paints the single cell under the pointer, so a fast mouse stroke leaves gaps between two `mousemove` samples. Fix both, and add adjustable brush size (1/2/3 cells) while at it — all three touch the same function.

- [ ] **Step 1: Add a per-layer cell index instead of scanning children**

In `constructor`, after `this.layers = {};`, add:

```js
        this.layers = {};   // layer id -> Konva.Layer
        this.cellIndex = {};   // layer id -> Map<"col,row", Konva.Rect>
        this.brushSize = 1;
        this.lastPaintCell = null;   // {col, row} — last painted cell, for stroke interpolation
```

In `createLayer(id)`, initialize the index alongside the Konva layer:

```js
    createLayer(id) {
        const l = new Konva.Layer();
        l.id(id);
        this.stage.add(l);
        this.layers[id] = l;
        this.cellIndex[id] = new Map();
        return l;
    }
```

`addCellRect` must register (and `paintCell` must deregister/register) nodes in the index. Replace `addCellRect`:

```js
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
            const index = this.cellIndex[konvaLayer.id()];
            if (index) index.set(`${cell.col},${cell.row}`, rect);
        }
        konvaLayer.add(rect);
        return rect;
    }
```

`renderDocument` destroys and rebuilds every layer's children, which invalidates the index; clear it there too. In `renderDocument`, right after `kl.destroyChildren();`, add:

```js
            kl.destroyChildren();
            if (this.cellIndex[layerDto.id]) this.cellIndex[layerDto.id].clear();
```

- [ ] **Step 2: Replace `paintCell` with an indexed, size-aware, erase-aware version**

Replace the whole `paintCell` method:

```js
    /* ---- Brush ---- */

    setBrushSize(n) {
        this.brushSize = Math.max(1, Math.min(3, Math.round(n)));
    }

    /** Cells covered by the brush footprint centered on (col,row), clipped to the grid. */
    brushFootprint(col, row) {
        const cells = [];
        const half = Math.floor((this.brushSize - 1) / 2);
        for (let dr = -half; dr < this.brushSize - half; dr++) {
            for (let dc = -half; dc < this.brushSize - half; dc++) {
                const c = col + dc, r = row + dr;
                if (c >= 0 && c < this.gridWidth && r >= 0 && r < this.gridHeight) cells.push({ col: c, row: r });
            }
        }
        return cells;
    }

    paintCell(col, row, { erase = false } = {}) {
        if (col < 0 || col >= this.gridWidth || row < 0 || row >= this.gridHeight) return;
        if (this.isLocked(this.activeLayerId)) return;
        const layerDto = this.layerDto(this.activeLayerId);
        if (!layerDto || layerDto.type !== 'TERRAIN') {
            this.setStatus('The brush paints on the Terrain layer');
            return;
        }

        for (const cell of this.brushFootprint(col, row)) {
            this.paintOneCell(cell.col, cell.row, erase);
        }
        this.lastPaintCell = { col, row };
        this.layers[this.activeLayerId].batchDraw();
        this.markDirty();
    }

    paintOneCell(col, row, erase) {
        const kl = this.layers[this.activeLayerId];
        const index = this.cellIndex[this.activeLayerId];
        if (!kl || !index) return;

        const key = `${col},${row}`;
        const existing = index.get(key);
        if (existing) {
            existing.destroy();
            index.delete(key);
        }
        const terrain = erase ? DEFAULT_TERRAIN : this.terrain;
        if (terrain !== DEFAULT_TERRAIN) {   // painting floor = erasing
            this.addCellRect(kl, { col, row, terrain }, {});
        }
    }

    /** Fills every cell on the line between the last painted cell and (col,row) — keeps
     *  fast mouse strokes solid instead of leaving gaps between mousemove samples. */
    paintStrokeTo(col, row, erase) {
        if (!this.lastPaintCell) {
            this.paintCell(col, row, { erase });
            return;
        }
        const { col: c0, row: r0 } = this.lastPaintCell;
        if (c0 === col && r0 === row) return;

        for (const { col: c, row: r } of this.bresenham(c0, r0, col, row)) {
            for (const cell of this.brushFootprint(c, r)) this.paintOneCell(cell.col, cell.row, erase);
        }
        this.lastPaintCell = { col, row };
        this.layers[this.activeLayerId].batchDraw();
        this.markDirty();
    }

    /** Integer grid line between two cells (Bresenham), inclusive of both endpoints. */
    bresenham(c0, r0, c1, r1) {
        const pts = [];
        let dc = Math.abs(c1 - c0), dr = -Math.abs(r1 - r0);
        let sc = c0 < c1 ? 1 : -1, sr = r0 < r1 ? 1 : -1;
        let err = dc + dr;
        let c = c0, r = r0;
        while (true) {
            pts.push({ col: c, row: r });
            if (c === c1 && r === r1) break;
            const e2 = 2 * err;
            if (e2 >= dr) { err += dr; c += sc; }
            if (e2 <= dc) { err += dc; r += sr; }
        }
        return pts;
    }
```

- [ ] **Step 3: Wire stroke interpolation and reset `lastPaintCell` into the event handlers**

In `setupEvents`, the `mousedown` handler's brush branch currently reads:

```js
            if (this.activeTool === 'brush') {
                this.pushUndo();
                this.drawing = true;
                this.paintCell(pos.col, pos.row);
            }
```

Replace with:

```js
            if (this.activeTool === 'brush') {
                this.pushUndo();
                this.drawing = true;
                this.lastPaintCell = null;
                this.paintCell(pos.col, pos.row);
            }
```

The `mousemove` handler's brush branch currently reads:

```js
            if (this.activeTool === 'brush') {
                this.paintCell(pos.col, pos.row);
            }
```

Replace with:

```js
            if (this.activeTool === 'brush') {
                this.paintStrokeTo(pos.col, pos.row, false);
            }
```

In the `mouseup` handler, after `this.drawing = false;`, add `this.lastPaintCell = null;` so the next stroke starts fresh:

```js
            if (!this.drawing) return;
            this.drawing = false;
            this.lastPaintCell = null;
```

- [ ] **Step 4: Manual verification**

Run `mvn spring-boot:run`, open a map editor page (`/campaigns/{id}/maps/{mapId}/edit`).

1. Select Brush + Wall terrain, drag quickly across many cells in a straight diagonal line. Expected: a solid unbroken wall line, no gaps (previously fast drags would skip cells).
2. Paint over an existing wall cell with Floor. Expected: the cell clears (still works — regression check on the index rewrite).
3. Open browser DevTools console, confirm no errors during painting.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js
git commit -m "feat(map-editor): index terrain cells and interpolate brush strokes"
```

---

### Task 2: Right-click erase + explicit Erase palette entry

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/static/js/map/terrain-palette.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Consumes: `paintCell(col, row, { erase })`, `paintStrokeTo(col, row, erase)`, `brushFootprint` from Task 1.
- Produces: `ERASE_KEY` export from `terrain-palette.js`; `map-erase` is **not** a real terrain key stored in cells (erasing still just clears the cell, per the existing data model) — it is a UI-only palette entry that calls `setTerrain(DEFAULT_TERRAIN)`, kept visually distinct from "Floor" via a checkerboard icon.

"Painting floor erases" is currently a hidden convention (`DEFAULT_TERRAIN` doubles as both a legitimate terrain and "nothing here"). Rather than changing that data model (which the whole schema already relies on — absent cells render as floor), make erasing discoverable two ways: an explicit "Erase" entry in the terrain picker, and right-click-to-erase while brushing without changing the selected terrain.

- [ ] **Step 1: Export an `ERASE_KEY` sentinel from the palette module**

In `terrain-palette.js`, after `export const DEFAULT_TERRAIN = 'floor';`, add:

```js
/** UI-only sentinel for the palette picker's "Erase" entry — not a real terrain key.
 *  Selecting it sets the active terrain to DEFAULT_TERRAIN, same as painting Floor. */
export const ERASE_KEY = '__erase__';
```

- [ ] **Step 2: Handle right-click erase in the stage `mousedown`/`mousemove`/`mouseup` handlers**

In `setupEvents`, the handler currently starts with:

```js
        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.button === 1) {   // middle mouse: pan
                this.panning = true;
                this.stage.draggable(true);
                return;
            }
            if (this.stage.draggable()) return;   // space-pan active
```

Insert a right-click branch right after the middle-mouse branch:

```js
        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.button === 1) {   // middle mouse: pan
                this.panning = true;
                this.stage.draggable(true);
                return;
            }
            if (e.evt.button === 2 && this.activeTool === 'brush') {   // right mouse: erase
                e.evt.preventDefault();
                if (this.isLocked(this.activeLayerId)) { this.setStatus('Layer is locked'); return; }
                this.pushUndo();
                this.erasing = true;
                this.drawing = true;
                this.lastPaintCell = null;
                const pos = this.cellPos();
                if (pos) this.paintCell(pos.col, pos.row, { erase: true });
                return;
            }
            if (this.stage.draggable()) return;   // space-pan active
```

The `mousemove` brush branch (updated in Task 1 to `this.paintStrokeTo(pos.col, pos.row, false);`) becomes erase-aware:

```js
            if (this.activeTool === 'brush') {
                this.paintStrokeTo(pos.col, pos.row, !!this.erasing);
            }
```

The `mouseup` handler resets `this.erasing`. Find:

```js
            if (!this.drawing) return;
            this.drawing = false;
            this.lastPaintCell = null;
```

Replace with:

```js
            if (!this.drawing) return;
            this.drawing = false;
            this.lastPaintCell = null;
            this.erasing = false;
```

Also suppress the browser's native context menu over the canvas, otherwise right-click erase triggers it on release. In `setupEvents`, after the `dblclick dbltap` handler, add:

```js
        this.stage.on('contextmenu', (e) => { e.evt.preventDefault(); });
```

In the constructor, initialize the new flag next to `this.panning = false;`:

```js
        this.drawing = false;
        this.panning = false;
        this.erasing = false;
```

- [ ] **Step 3: Handle the `ERASE_KEY` sentinel in `setTerrain`**

Import the new export at the top of `map-editor.js`:

```js
import { BUILTIN_TERRAIN, DEFAULT_TERRAIN, ERASE_KEY, SHAPE_COLORS } from './terrain-palette.js';
```

Update `setTerrain`:

```js
    setTerrain(key) {
        this.terrain = key === ERASE_KEY ? DEFAULT_TERRAIN : key;
    }
```

- [ ] **Step 4: Add the Erase entry to the toolbar's terrain list**

In `editor.html`, the Alpine `toolbar()` data has a static `terrains` array used before the palette event arrives:

```js
                terrains: [
                    { key: 'floor', name: 'Floor' },
                    { key: 'wall', name: 'Wall' },
                    { key: 'water', name: 'Water' },
                    { key: 'difficult', name: 'Difficult Terrain' },
                    { key: 'lava', name: 'Lava' },
                    { key: 'pit', name: 'Pit' },
                    { key: 'door', name: 'Door' },
                ],
```

Replace with (Erase listed first so it's always the first, easy-to-find option):

```js
                terrains: [
                    { key: '__erase__', name: '◇ Erase' },
                    { key: 'floor', name: 'Floor' },
                    { key: 'wall', name: 'Wall' },
                    { key: 'water', name: 'Water' },
                    { key: 'difficult', name: 'Difficult Terrain' },
                    { key: 'lava', name: 'Lava' },
                    { key: 'pit', name: 'Pit' },
                    { key: 'door', name: 'Door' },
                ],
```

`map-palette` events rebuild this list from `MapEditor.rebuildPalette()`, which only knows about real terrain keys — so also prepend Erase there. In `editor.html`'s `init()`:

```js
                    window.addEventListener('map-palette', (e) => { this.terrains = e.detail.terrains; });
```

Replace with:

```js
                    window.addEventListener('map-palette', (e) => {
                        this.terrains = [{ key: '__erase__', name: '◇ Erase' }, ...e.detail.terrains];
                    });
```

Update the brush tool group's title to mention right-click. Find:

```html
                <button class="tool-btn" :class="{ active: tool === 'brush' }" @click="setTool('brush')" title="Brush (B)">🖌 Brush</button>
```

Replace with:

```html
                <button class="tool-btn" :class="{ active: tool === 'brush' }" @click="setTool('brush')" title="Brush (B) — right-click to erase">🖌 Brush</button>
```

- [ ] **Step 5: Manual verification**

1. Reload the editor. Confirm the terrain dropdown's first option is "◇ Erase" and selecting it, then brushing, clears cells without changing what "Floor" does.
2. Select Wall, paint a patch. Right-click-drag over part of it. Expected: right-click clears cells without changing the dropdown's selected terrain (still shows "Wall" after).
3. Right-click over empty canvas with a non-brush tool active (e.g. Select) — expected: no browser context menu appears, no error in console.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/static/js/map/terrain-palette.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): discoverable erase — right-click brush and Erase palette entry"
```

---

### Task 3: Terrain rect-fill tool

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Consumes: `paintOneCell(col, row, erase)` from Task 1.
- Produces: new tool id `'fill-rect'` usable via `MapEditor.setTool('fill-rect')`; keyboard shortcut `T`.

Painting a large floor with the 1-cell brush is tedious. Add a "Terrain Rect" tool: drag a rectangle, release to fill every covered cell with the active terrain (or erase, via the same right-click convention as the brush).

- [ ] **Step 1: Add `'fill-rect'` to the tool set and its shape-preview branch**

`DRAW_TOOLS` currently gates the "layer locked" check for drawing tools:

```js
const DRAW_TOOLS = ['brush', 'rect', 'circle', 'line', 'polygon'];
```

Replace with:

```js
const DRAW_TOOLS = ['brush', 'rect', 'circle', 'line', 'polygon', 'fill-rect', 'bucket'];
```

In `setupEvents`, the `mousedown` handler's shape-tool branch currently reads:

```js
            } else if (['rect', 'circle', 'line'].includes(this.activeTool)) {
                this.drawing = true;
                this.shapeStart = { x: this.snapPt(pos.x), y: this.snapPt(pos.y) };
            }
```

Replace with:

```js
            } else if (['rect', 'circle', 'line'].includes(this.activeTool)) {
                this.drawing = true;
                this.shapeStart = { x: this.snapPt(pos.x), y: this.snapPt(pos.y) };
            } else if (this.activeTool === 'fill-rect') {
                if (this.isLocked(this.activeLayerId) || this.layerDto(this.activeLayerId)?.type !== 'TERRAIN') {
                    this.setStatus('Terrain Rect paints on the Terrain layer');
                    return;
                }
                this.drawing = true;
                this.shapeStart = { col: pos.col, row: pos.row };
            } else if (this.activeTool === 'bucket') {
                this.floodFillAt(pos.col, pos.row, e.evt.button === 2);
            }
```

The `mousemove` handler's shape-preview branch currently reads:

```js
            } else if (this.shapeStart) {
                this.previewShape(this.shapeStart, { x: this.snapPt(pos.x), y: this.snapPt(pos.y) });
            }
```

Replace with:

```js
            } else if (this.activeTool === 'fill-rect' && this.shapeStart) {
                this.previewCellRect(this.shapeStart, { col: pos.col, row: pos.row });
            } else if (this.shapeStart) {
                this.previewShape(this.shapeStart, { x: this.snapPt(pos.x), y: this.snapPt(pos.y) });
            }
```

The `mouseup` handler's signature currently takes no event parameter:

```js
        this.stage.on('mouseup touchend', () => {
```

The erase-detection below needs `e.evt.button`, so replace with:

```js
        this.stage.on('mouseup touchend', (e) => {
```

Further down, the same handler reads:

```js
            if (this.shapeStart && pos) {
                this.commitShape(this.shapeStart, { x: this.snapPt(pos.x), y: this.snapPt(pos.y) });
            }
            this.clearPreview();
            this.shapeStart = null;
```

Replace with:

```js
            if (this.activeTool === 'fill-rect' && this.shapeStart && pos) {
                this.commitCellRect(this.shapeStart, { col: pos.col, row: pos.row }, e.evt.button === 2);
            } else if (this.shapeStart && pos) {
                this.commitShape(this.shapeStart, { x: this.snapPt(pos.x), y: this.snapPt(pos.y) });
            }
            this.clearPreview();
            this.shapeStart = null;
```

- [ ] **Step 2: Implement the cell-rect preview and commit**

Add these two methods right after `commitShape` (which ends the "Shape tools (rect / circle / line)" section):

```js
    /* ---- Terrain rect-fill ---- */

    previewCellRect(a, b) {
        this.clearPreview();
        const s = this.cellSizePx;
        const c0 = Math.min(a.col, b.col), c1 = Math.max(a.col, b.col);
        const r0 = Math.min(a.row, b.row), r1 = Math.max(a.row, b.row);
        this.previewLayer.add(new Konva.Rect({
            x: c0 * s, y: r0 * s, width: (c1 - c0 + 1) * s, height: (r1 - r0 + 1) * s,
            fill: 'rgba(139,69,19,0.3)', stroke: '#8B4513', strokeWidth: 2, dash: [4, 4], listening: false,
        }));
        this.previewLayer.batchDraw();
    }

    commitCellRect(a, b, erase) {
        const c0 = Math.min(a.col, b.col), c1 = Math.max(a.col, b.col);
        const r0 = Math.min(a.row, b.row), r1 = Math.max(a.row, b.row);
        this.pushUndo();
        for (let r = r0; r <= r1; r++) {
            for (let c = c0; c <= c1; c++) this.paintOneCell(c, r, erase);
        }
        this.layers[this.activeLayerId].batchDraw();
        this.markDirty();
    }
```

- [ ] **Step 3: Add tool switching and the toolbar button**

`setTool` already handles arbitrary tool names generically (`this.activeTool = tool;`), so no change needed there beyond its status message. In `setTool`, extend the status-message ternary:

```js
        this.setStatus(tool === 'polygon'
            ? 'Polygon: click vertices, double-click or Enter to close, Esc to cancel'
            : tool === 'select'
                ? 'Select: drag a box, Ctrl+C copy, Ctrl+V paste'
                : 'Ready');
```

Replace with:

```js
        this.setStatus(tool === 'polygon'
            ? 'Polygon: click vertices, double-click or Enter to close, Esc to cancel'
            : tool === 'select'
                ? 'Select: drag a box, Ctrl+C copy, Ctrl+V paste'
                : tool === 'fill-rect'
                    ? 'Terrain Rect: drag to fill an area, right-click drag to erase'
                    : tool === 'bucket'
                        ? 'Bucket: click a region to fill it with the active terrain, right-click to erase'
                        : 'Ready');
```

Add the `T` keyboard shortcut in `setupEvents`'s `switch (e.key.toLowerCase())`:

```js
                case 'b': this.setTool('brush'); break;
```

Replace with:

```js
                case 'b': this.setTool('brush'); break;
                case 't': this.setTool('fill-rect'); break;
```

In `editor.html`, add the button to the first `tool-group` (terrain group), right after the Brush button:

```html
                <button class="tool-btn" :class="{ active: tool === 'brush' }" @click="setTool('brush')" title="Brush (B) — right-click to erase">🖌 Brush</button>
```

Replace with:

```html
                <button class="tool-btn" :class="{ active: tool === 'brush' }" @click="setTool('brush')" title="Brush (B) — right-click to erase">🖌 Brush</button>
                <button class="tool-btn" :class="{ active: tool === 'fill-rect' }" @click="setTool('fill-rect')" title="Terrain Rect (T) — right-click to erase">▤ Terrain Rect</button>
```

- [ ] **Step 4: Manual verification**

1. Reload the editor. Press `T` or click "Terrain Rect", pick Wall, drag a rectangle across ~5×5 cells. Expected: on release, all covered cells become Wall in one action; a dashed preview rectangle tracked the drag.
2. Right-click-drag with Terrain Rect over the same area. Expected: cells clear.
3. Ctrl+Z once. Expected: the whole rect-fill undoes as a single step (not cell-by-cell).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): add Terrain Rect fill tool"
```

---

### Task 4: Terrain bucket (flood-fill) tool

**Files:**
- Create: `src/main/resources/static/js/map/flood-fill.js`
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Produces: `floodFillCells(cells, gridWidth, gridHeight, startCol, startRow)` — pure function, exported from `flood-fill.js`, returns `{col, row}[]` (the contiguous same-terrain region containing the start cell, 4-directionally connected).
- Consumes: `cells` in the shape `{col, row, terrain}[]` — same shape `buildDocumentFromCanvas` already produces for a layer.

`bucket` tool id and its `mousedown` wiring (`this.floodFillAt(...)`) were already added to `map-editor.js` in Task 3 Step 1 — this task implements `floodFillAt` and its pure region-finder.

- [ ] **Step 1: Write `flood-fill.js` — pure, no Konva/DOM dependency**

```js
/**
 * Terrain bucket-fill: finds every cell 4-directionally connected to (startCol, startRow)
 * that shares its terrain (absent cells count as the same "default floor" terrain).
 * Pure function — no DOM/Konva — so it's trivial to reason about and reuse (e.g. from a
 * future test runner) independently of the canvas.
 *
 * @param {{col:number, row:number, terrain:string}[]} cells — painted cells on one layer
 * @param {number} gridWidth
 * @param {number} gridHeight
 * @param {number} startCol
 * @param {number} startRow
 * @param {string} defaultTerrain — terrain key for cells absent from `cells`
 * @returns {{col:number, row:number}[]} the connected region (always includes the start cell)
 */
export function floodFillCells(cells, gridWidth, gridHeight, startCol, startRow, defaultTerrain) {
    if (startCol < 0 || startCol >= gridWidth || startRow < 0 || startRow >= gridHeight) return [];

    const terrainAt = new Map();
    for (const c of cells) terrainAt.set(`${c.col},${c.row}`, c.terrain);
    const targetTerrain = terrainAt.get(`${startCol},${startRow}`) ?? defaultTerrain;

    const visited = new Set();
    const region = [];
    const stack = [{ col: startCol, row: startRow }];

    while (stack.length) {
        const { col, row } = stack.pop();
        const key = `${col},${row}`;
        if (visited.has(key)) continue;
        if (col < 0 || col >= gridWidth || row < 0 || row >= gridHeight) continue;
        const terrain = terrainAt.get(key) ?? defaultTerrain;
        if (terrain !== targetTerrain) continue;

        visited.add(key);
        region.push({ col, row });
        stack.push({ col: col + 1, row }, { col: col - 1, row }, { col, row: row + 1 }, { col, row: row - 1 });
    }
    return region;
}
```

- [ ] **Step 2: Wire `floodFillAt` into `MapEditor`**

Import the new module at the top of `map-editor.js`:

```js
import { BUILTIN_TERRAIN, DEFAULT_TERRAIN, ERASE_KEY, SHAPE_COLORS } from './terrain-palette.js';
import { floodFillCells } from './flood-fill.js';
```

Add the method right after `commitCellRect` (end of the "Terrain rect-fill" section added in Task 3):

```js
    /* ---- Terrain bucket (flood-fill) ---- */

    floodFillAt(col, row, erase) {
        if (this.isLocked(this.activeLayerId)) { this.setStatus('Layer is locked'); return; }
        const layerDto = this.layerDto(this.activeLayerId);
        if (!layerDto || layerDto.type !== 'TERRAIN') {
            this.setStatus('Bucket paints on the Terrain layer');
            return;
        }
        this.syncDocument();
        const cells = this.layerDto(this.activeLayerId)?.cells || [];
        const region = floodFillCells(cells, this.gridWidth, this.gridHeight, col, row, DEFAULT_TERRAIN);
        if (!region.length) return;

        this.pushUndo();
        for (const cell of region) this.paintOneCell(cell.col, cell.row, erase);
        this.layers[this.activeLayerId].batchDraw();
        this.markDirty();
        this.setStatus(`Filled ${region.length} cell(s)`);
    }
```

`pushUndo` calls `buildDocumentFromCanvas()`, which is safe to call after `syncDocument()` above (both read live canvas state; `syncDocument` just also refreshes `this.document` so `layerDto()` sees current cells for the flood-fill scan).

- [ ] **Step 3: Add the toolbar button and shortcut**

In `editor.html`, add the Bucket button next to Terrain Rect:

```html
                <button class="tool-btn" :class="{ active: tool === 'fill-rect' }" @click="setTool('fill-rect')" title="Terrain Rect (T) — right-click to erase">▤ Terrain Rect</button>
```

Replace with:

```html
                <button class="tool-btn" :class="{ active: tool === 'fill-rect' }" @click="setTool('fill-rect')" title="Terrain Rect (T) — right-click to erase">▤ Terrain Rect</button>
                <button class="tool-btn" :class="{ active: tool === 'bucket' }" @click="setTool('bucket')" title="Bucket (G) — right-click to erase">🪣 Bucket</button>
```

In `map-editor.js`'s keyboard shortcut switch, add `G` next to `T`:

```js
                case 'b': this.setTool('brush'); break;
                case 't': this.setTool('fill-rect'); break;
```

Replace with:

```js
                case 'b': this.setTool('brush'); break;
                case 't': this.setTool('fill-rect'); break;
                case 'g': this.setTool('bucket'); break;
```

- [ ] **Step 4: Manual verification**

1. Reload the editor. Paint an irregular Wall shape (a blob) with the brush.
2. Press `G`, click Water, then click inside the empty Floor area next to the wall blob. Expected: the entire connected floor region turns Water, stopping at the wall boundary.
3. Click Water on a single isolated cell surrounded by Wall — expected: only that one cell fills.
4. Right-click the same filled region with Bucket active — expected: the whole region erases back to Floor in one action; Ctrl+Z undoes it as one step.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/map/flood-fill.js src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): add terrain bucket (flood-fill) tool"
```

---

### Task 5: Shape selection & transform (click-select, move, resize, delete)

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:**
- Produces: `this.shapeSelection` (the currently transform-selected Konva shape node, or `null`), `MapEditor.selectShapeForTransform(node)`, `MapEditor.clearShapeSelection()`, `MapEditor.bakeShapeTransform(node)`.
- Consumes: nothing new.

Shapes are currently drawn with `listening: false` and are otherwise inert — there is no way to click one afterwards to move, resize, or delete it (the existing marquee Select tool only does bulk copy/paste of a bounding box, not single-shape editing). This task adds click-to-select with a Konva `Transformer` (move + resize handles; rotation intentionally disabled — the schema has no rotation field and none of the four shape types need it) and a Delete key. Label editing is Task 6; bulk marquee move/cut is Task 7.

- [ ] **Step 1: Make shape nodes listening, and add selection state fields**

In `addShapeNode`, each `case` currently sets `listening: false`. Change all four to `listening: true` so stage-level `mousedown` can identify which shape was clicked via `e.target`:

```js
        switch (shape.type) {
            case 'rect':
                node = new Konva.Rect({
                    x: px(pts[0]), y: px(pts[1]), width: px(pts[2]), height: px(pts[3]),
                    fill, stroke, strokeWidth: sw, listening: true,
                });
                break;
            case 'circle':
                node = new Konva.Circle({
                    x: px(pts[0]), y: px(pts[1]), radius: px(pts[2]),
                    fill, stroke, strokeWidth: sw, listening: true,
                });
                break;
            case 'line':
                node = new Konva.Line({
                    points: [px(pts[0]), px(pts[1]), px(pts[2]), px(pts[3])],
                    stroke, strokeWidth: sw, lineCap: 'round', listening: true,
                });
                break;
            case 'polygon':
                node = new Konva.Line({
                    points: pts.map(px), closed: true,
                    fill, stroke, strokeWidth: sw, lineJoin: 'round', listening: true,
                });
                break;
            default:
                return null;
        }
```

In the constructor, add selection-state fields right after `this.clipboard = null;`:

```js
        this.selection = null;     // {origin: {col, row}, cells: Cell[], shapes: Shape[]}
        this.clipboard = null;     // {cells: Cell[], shapes: Shape[]} normalized to (0,0)
        this.shapeSelection = null;   // single Konva shape node currently selected for transform, or null
        this.transformer = null;
```

- [ ] **Step 2: Create the Transformer during `load()`**

`load()` currently reads:

```js
        this.previewLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.previewLayer);

        this.drawGrid();
        this.setupEvents();
        this.fetchDocument();
```

The `previewLayer` is created with `listening: false`, which would prevent a Transformer added to it from receiving pointer events on its own handles. Replace this whole block:

```js
        this.previewLayer = new Konva.Layer();
        this.stage.add(this.previewLayer);

        this.initTransformer();
        this.drawGrid();
        this.setupEvents();
        this.fetchDocument();
```

Add `initTransformer` right after `load()` (still inside the class, before the "Grid" section):

```js
    initTransformer() {
        this.transformer = new Konva.Transformer({
            rotateEnabled: false,
            enabledAnchors: ['top-left', 'top-center', 'top-right', 'middle-left', 'middle-right',
                              'bottom-left', 'bottom-center', 'bottom-right'],
            borderStroke: '#4a9eff',
            anchorStroke: '#4a9eff',
            anchorFill: '#fff',
            anchorSize: 8,
        });
        this.previewLayer.add(this.transformer);

        this.transformer.on('transformend', () => {
            const node = this.shapeSelection;
            if (!node) return;
            this.pushUndo();
            this.bakeShapeTransform(node);
            this.syncDocument();
            this.clearShapeSelection();
            this.renderDocument();
            this.markDirty();
        });
    }
```

- [ ] **Step 3: Guard the Transformer's own anchors from the stage's tool-dispatch logic**

`setupEvents`'s `mousedown touchstart` handler must not treat clicks on the Transformer's resize handles as canvas clicks (otherwise resizing would also start a marquee selection and deselect the shape mid-drag). It currently starts:

```js
        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.button === 1) {   // middle mouse: pan
```

Replace with:

```js
        this.stage.on('mousedown touchstart', (e) => {
            if (e.target.getParent() instanceof Konva.Transformer) return;   // let the Transformer handle its own anchors
            if (e.evt.button === 1) {   // middle mouse: pan
```

- [ ] **Step 4: Click-to-select a shape in the Select tool's `mousedown` branch**

Find the Select-tool branch (unchanged by earlier tasks):

```js
            if (this.activeTool === 'select') {
                this.clearSelection();
                this.marqueeStart = { x: pos.x, y: pos.y };
                this.drawing = true;
                return;
            }
```

Replace with:

```js
            if (this.activeTool === 'select') {
                const shapeNode = e.target && e.target.getAttr('_shape') ? e.target : null;
                if (shapeNode) {
                    this.selectShapeForTransform(shapeNode);
                    return;
                }
                this.clearShapeSelection();
                this.clearSelection();
                this.marqueeStart = { x: pos.x, y: pos.y };
                this.drawing = true;
                return;
            }
```

- [ ] **Step 5: Implement `selectShapeForTransform`, `clearShapeSelection`, `bakeShapeTransform`**

Add these methods right after `clearSelection` (end of the "Select / copy / paste" section):

```js
    /* ---- Shape selection & transform (click-select, move, resize, delete) ---- */

    selectShapeForTransform(node) {
        this.clearSelection();
        this.cancelPolygon();
        this.clearShapeSelection();
        this.shapeSelection = node;
        node.draggable(true);
        node.on('dragend.shapeselect', () => {
            this.pushUndo();
            this.bakeShapeTransform(node);
            this.syncDocument();
            this.clearShapeSelection();
            this.renderDocument();
            this.markDirty();
        });
        this.transformer.keepRatio(node.getAttr('_shape')?.type === 'circle');
        this.transformer.nodes([node]);
        this.transformer.getLayer().batchDraw();
        this.setStatus('Shape selected — drag to move, handles to resize, Delete to remove, double-click to edit label');
    }

    clearShapeSelection() {
        if (this.shapeSelection) {
            this.shapeSelection.draggable(false);
            this.shapeSelection.off('dragend.shapeselect');
        }
        this.shapeSelection = null;
        if (this.transformer) {
            this.transformer.nodes([]);
            this.transformer.getLayer()?.batchDraw();
        }
    }

    /** Reads the node's current on-screen transform (position + scale) back into its
     *  `_shape` data (cell units) and leaves the Konva transform in place — the caller
     *  re-renders the whole document immediately after, which replaces this node. */
    bakeShapeTransform(node) {
        const shape = node.getAttr('_shape');
        if (!shape) return;
        const cs = this.cellSizePx;
        const nx = node.x(), ny = node.y();
        const sx = node.scaleX(), sy = node.scaleY();

        switch (shape.type) {
            case 'rect': {
                const w = node.width() * sx, h = node.height() * sy;
                shape.points = [this.round2(nx / cs), this.round2(ny / cs), this.round2(w / cs), this.round2(h / cs)];
                break;
            }
            case 'circle': {
                const r = node.radius() * ((sx + sy) / 2);
                shape.points = [this.round2(nx / cs), this.round2(ny / cs), this.round2(r / cs)];
                break;
            }
            case 'line':
            case 'polygon': {
                const pts = node.points();
                const abs = [];
                for (let i = 0; i + 1 < pts.length; i += 2) abs.push(nx + pts[i] * sx, ny + pts[i + 1] * sy);
                shape.points = abs.map((v) => this.round2(v / cs));
                break;
            }
        }
        node.setAttr('_shape', shape);
    }
```

- [ ] **Step 6: Delete key removes the selected shape; Escape and tool/layer switches clear the selection**

In `setTool`, find:

```js
    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.activeTool = tool;
```

Replace with:

```js
    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.activeTool = tool;
```

In `setLayer`, find:

```js
    setLayer(layerId) {
        this.cancelPolygon();
        this.clearSelection();
        this.activeLayerId = layerId;
    }
```

Replace with:

```js
    setLayer(layerId) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.activeLayerId = layerId;
    }
```

In the keyboard handler's `switch (e.key.toLowerCase())`, find the `escape` case:

```js
                case 'escape':
                    this.cancelPolygon();
                    this.clearSelection();
                    break;
```

Replace with:

```js
                case 'delete':
                case 'backspace':
                    if (this.shapeSelection) {
                        e.preventDefault();
                        this.pushUndo();
                        const layer = this.shapeSelection.getLayer();
                        this.shapeSelection.destroy();
                        this.clearShapeSelection();
                        layer.batchDraw();
                        this.markDirty();
                    }
                    break;
                case 'escape':
                    this.cancelPolygon();
                    this.clearSelection();
                    this.clearShapeSelection();
                    break;
```

- [ ] **Step 7: Manual verification**

1. Reload the editor. Draw a rectangle shape on the Objects layer. Switch to Select (`V`), click directly on the rectangle. Expected: blue resize handles (no rotate handle) appear around it, status bar says "Shape selected…".
2. Drag the shape to a new position. Expected: it moves, handles disappear (selection clears after commit, by design — click the shape again to keep editing), and the move survives a page reload (i.e. it saved).
3. Click the shape again, drag a corner handle to resize it. Expected: it resizes correctly (rect grows/shrinks); repeat for a circle (resizes as a circle, not an ellipse — `keepRatio` is on) and a line (endpoints move apart correctly).
4. Click a shape, press Delete. Expected: it's removed; Ctrl+Z restores it.
5. Click a shape, then click empty canvas. Expected: handles disappear, no marquee box was left behind.
6. With a shape selected, press `Escape`. Expected: handles disappear.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js
git commit -m "feat(map-editor): click-to-select shapes with move/resize transform and delete"
```

---

### Task 6: Shape label editing (double-click inline text)

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Consumes: `shapeSelection`-style shape-node detection (`e.target.getAttr('_shape')`) from Task 5; `renderDocument()` already positions each shape's label `Text` node from `shape.points`, so a label always stays attached to its shape after any transform (Task 5 re-renders the whole document on every move/resize) — no extra repositioning code is needed here.
- Produces: `MapEditor.editShapeLabel(node)`.

The `label` field has existed on every shape since M4 (`ShapeDto.label`, rendered by `addShapeNode`) but nothing in the UI could ever set it. Double-clicking a shape while the Select tool is active now opens a small inline text input over it.

- [ ] **Step 1: Add `editShapeLabel` to `MapEditor`**

Add this method right after `bakeShapeTransform` (end of the "Shape selection & transform" section added in Task 5):

```js
    /** Opens a floating text input over the shape's label anchor (its first point,
     *  same anchor `addShapeNode` uses to draw the label `Text` node). */
    editShapeLabel(node) {
        const shape = node.getAttr('_shape');
        if (!shape) return;
        const pts = shape.points || [];
        const stagePt = { x: pts[0] * this.cellSizePx, y: pts[1] * this.cellSizePx };
        const screenPt = this.stage.getAbsoluteTransform().point(stagePt);

        const input = document.createElement('input');
        input.type = 'text';
        input.value = shape.label || '';
        input.placeholder = 'Label…';
        input.className = 'map-label-input';
        input.style.left = `${screenPt.x + 2}px`;
        input.style.top = `${screenPt.y + 2}px`;
        this.container.appendChild(input);
        input.focus();
        input.select();

        const commit = () => {
            const newLabel = input.value.trim();
            input.remove();
            if (newLabel === (shape.label || '')) return;   // no-op edit, no undo entry
            this.pushUndo();
            shape.label = newLabel;
            node.setAttr('_shape', shape);
            this.syncDocument();
            this.clearShapeSelection();   // renderDocument() below destroys `node` — drop any stale reference to it first
            this.renderDocument();
            this.markDirty();
        };
        const cancel = () => input.remove();

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') commit();
            else if (e.key === 'Escape') cancel();
        });
        input.addEventListener('blur', commit);
    }
```

- [ ] **Step 2: Wire double-click on a shape (Select tool) to open the editor**

`setupEvents`'s double-click handler currently reads:

```js
        this.stage.on('dblclick dbltap', () => {
            if (this.activeTool === 'polygon') this.commitPolygon();
        });
```

Replace with:

```js
        this.stage.on('dblclick dbltap', (e) => {
            if (this.activeTool === 'polygon') { this.commitPolygon(); return; }
            if (this.activeTool === 'select') {
                const shapeNode = e.target && e.target.getAttr('_shape') ? e.target : null;
                if (shapeNode) this.editShapeLabel(shapeNode);
            }
        });
```

- [ ] **Step 3: Style the inline input**

In `editor.html`'s `<style>` block, add after the `.layer-item button` rule:

```css
        .map-label-input {
            position: absolute;
            z-index: 20;
            font-size: 12px;
            padding: 2px 4px;
            border: 1px solid #4a9eff;
            border-radius: 3px;
            background: #1a1a2e;
            color: #fff;
            min-width: 80px;
        }
```

- [ ] **Step 4: Manual verification**

1. Reload the editor, draw a rectangle, switch to Select, double-click it. Expected: a small text input appears anchored at the shape's top-left corner.
2. Type "Trap!" and press Enter. Expected: the input closes and "Trap!" renders as white text at the shape's corner; reload the page — the label persists.
3. Double-click the shape again, clear the text, press Enter. Expected: the label disappears.
4. Double-click, type text, press Escape. Expected: input closes, no change (verify by reload).
5. Pan/zoom the canvas, then double-click a shape. Expected: the input still appears exactly over the shape's corner (confirms the stage-transform math accounts for pan/zoom).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): double-click a shape to edit its label"
```

---

### Task 7: Bulk selection — drag-to-move, cut, delete, and the paste-stacking fix

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:**
- Consumes: `this.selection` shape from the existing marquee Select tool; `shiftShape`, `sameShape`-style comparison needed fresh.
- Produces: `MapEditor.pointInSelectionBounds(pos)`, `MapEditor.previewSelectionMove(pos)`, `MapEditor.commitSelectionMove(pos)`, `MapEditor.drawSelectionMarker()`, `MapEditor.cutSelection()`, `MapEditor.deleteSelectionContents()`, `MapEditor.sameShape(a, b)`.

The marquee Select tool can copy/paste but not move, cut, or delete its selection, and pasting twice stacks both copies on the same cells (the offset is always computed from the *original* selection origin instead of accumulating). This task fixes all four.

- [ ] **Step 1: Store selection bounds and add move/paste-offset state**

`finishSelection` currently builds `this.selection` without keeping the bounding box, and always draws the marker inline. Find:

```js
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
```

Replace with:

```js
        this.selection = {
            origin: { col: Math.floor(bounds.minX), row: Math.floor(bounds.minY) },
            bounds,
            cells: structuredClone(cells),
            shapes: structuredClone(shapes),
        };

        this.drawSelectionMarker();
        this.setStatus(`${cells.length} cell(s), ${shapes.length} shape(s) selected — Ctrl+C copy, Ctrl+X cut, Delete remove, drag to move`);
    }
```

Extract the marker-drawing into its own method — add right after `finishSelection`:

```js
    drawSelectionMarker() {
        const b = this.selection.bounds;
        const px = (v) => v * this.cellSizePx;
        const rect = new Konva.Rect({
            x: px(b.minX), y: px(b.minY), width: px(b.maxX - b.minX), height: px(b.maxY - b.minY),
            stroke: '#4a9eff', strokeWidth: 1.5, dash: [6, 4], listening: false,
        });
        rect.setAttr('_selection', true);
        this.previewLayer.add(rect);
        this.previewLayer.batchDraw();
    }

    pointInSelectionBounds(pos) {
        if (!this.selection) return false;
        const b = this.selection.bounds;
        return pos.x >= b.minX && pos.x <= b.maxX && pos.y >= b.minY && pos.y <= b.maxY;
    }
```

In the constructor, add the new state fields right after `this.clipboard = null;` (from its current form after Task 5's edit):

```js
        this.clipboard = null;     // {cells: Cell[], shapes: Shape[]} normalized to (0,0)
        this.shapeSelection = null;   // single Konva shape node currently selected for transform, or null
        this.transformer = null;
```

Replace with:

```js
        this.clipboard = null;     // {cells: Cell[], shapes: Shape[]} normalized to (0,0)
        this.pasteOffset = null;   // {col, row} — accumulates so repeated Ctrl+V doesn't stack pastes
        this.movingSelection = null;   // {startX, startY} in cell units, while dragging a bulk selection
        this.shapeSelection = null;   // single Konva shape node currently selected for transform, or null
        this.transformer = null;
```

`clearSelection` must reset the new fields too. Find:

```js
    clearSelection() {
        this.selection = null;
        for (const child of [...this.previewLayer.getChildren()]) {
            if (child.getAttr('_selection')) child.destroy();
        }
        this.previewLayer.batchDraw();
    }
```

Replace with:

```js
    clearSelection() {
        this.selection = null;
        this.movingSelection = null;
        this.pasteOffset = null;
        for (const child of [...this.previewLayer.getChildren()]) {
            if (child.getAttr('_selection')) child.destroy();
        }
        this.previewLayer.batchDraw();
    }
```

- [ ] **Step 2: Start a move-drag when clicking inside an existing selection**

Find the Select-tool `mousedown` branch (as left by Task 5):

```js
            if (this.activeTool === 'select') {
                const shapeNode = e.target && e.target.getAttr('_shape') ? e.target : null;
                if (shapeNode) {
                    this.selectShapeForTransform(shapeNode);
                    return;
                }
                this.clearShapeSelection();
                this.clearSelection();
                this.marqueeStart = { x: pos.x, y: pos.y };
                this.drawing = true;
                return;
            }
```

Replace with:

```js
            if (this.activeTool === 'select') {
                const shapeNode = e.target && e.target.getAttr('_shape') ? e.target : null;
                if (shapeNode) {
                    this.selectShapeForTransform(shapeNode);
                    return;
                }
                this.clearShapeSelection();
                if (this.selection && this.pointInSelectionBounds(pos)) {
                    this.movingSelection = { startX: pos.x, startY: pos.y };
                    this.drawing = true;
                    return;
                }
                this.clearSelection();
                this.marqueeStart = { x: pos.x, y: pos.y };
                this.drawing = true;
                return;
            }
```

- [ ] **Step 3: Preview and commit the move in `mousemove` / `mouseup`**

The `mousemove` handler's select branch currently reads (unchanged since M4):

```js
            } else if (this.activeTool === 'select' && this.marqueeStart) {
                this.previewMarquee(this.marqueeStart, pos);
            } else if (this.activeTool === 'fill-rect' && this.shapeStart) {
```

Replace with:

```js
            } else if (this.activeTool === 'select' && this.movingSelection) {
                this.previewSelectionMove(pos);
            } else if (this.activeTool === 'select' && this.marqueeStart) {
                this.previewMarquee(this.marqueeStart, pos);
            } else if (this.activeTool === 'fill-rect' && this.shapeStart) {
```

The `mouseup` handler (its arrow function now takes `(e)`, per Task 3 Step 1) reads:

```js
            const pos = this.cellPos();
            if (this.activeTool === 'select' && this.marqueeStart && pos) {
                this.finishSelection(this.marqueeStart, pos);
                this.marqueeStart = null;
                return;
            }
```

Replace with:

```js
            const pos = this.cellPos();
            if (this.activeTool === 'select' && this.movingSelection && pos) {
                this.commitSelectionMove(pos);
                this.movingSelection = null;
                return;
            }
            if (this.activeTool === 'select' && this.marqueeStart && pos) {
                this.finishSelection(this.marqueeStart, pos);
                this.marqueeStart = null;
                return;
            }
```

- [ ] **Step 4: Implement `previewSelectionMove`, `commitSelectionMove`, `sameShape`**

Add these methods right after `pointInSelectionBounds` (added in Step 1):

```js
    previewSelectionMove(pos) {
        const dx = pos.x - this.movingSelection.startX;
        const dy = pos.y - this.movingSelection.startY;
        for (const child of [...this.previewLayer.getChildren()]) {
            if (child.getAttr('_selection')) child.destroy();
        }
        const b = this.selection.bounds;
        const px = (v) => v * this.cellSizePx;
        const rect = new Konva.Rect({
            x: px(b.minX + dx), y: px(b.minY + dy),
            width: px(b.maxX - b.minX), height: px(b.maxY - b.minY),
            stroke: '#4a9eff', strokeWidth: 1.5, dash: [6, 4], listening: false,
        });
        rect.setAttr('_selection', true);
        this.previewLayer.add(rect);
        this.previewLayer.batchDraw();
    }

    /** Value-equality for shapes — selections aren't tracked by id, so a move/delete/cut
     *  identifies "the shapes that were selected" by matching their serialized data. Two
     *  literally identical shapes at the same position could collide; acceptable for a
     *  DM map editor's scale. */
    sameShape(a, b) {
        return JSON.stringify(a) === JSON.stringify(b);
    }

    commitSelectionMove(pos) {
        const dx = Math.round(pos.x - this.movingSelection.startX);
        const dy = Math.round(pos.y - this.movingSelection.startY);
        for (const child of [...this.previewLayer.getChildren()]) {
            if (child.getAttr('_selection')) child.destroy();
        }
        if (!dx && !dy) {
            this.drawSelectionMarker();
            return;
        }
        this.pushUndo();
        this.syncDocument();
        const layerDto = this.layerDto(this.activeLayerId);
        if (!layerDto) return;

        const removedCellKeys = new Set(this.selection.cells.map((c) => `${c.col},${c.row}`));
        layerDto.cells = (layerDto.cells || []).filter((c) => !removedCellKeys.has(`${c.col},${c.row}`));
        layerDto.shapes = (layerDto.shapes || []).filter((s) => !this.selection.shapes.some((rs) => this.sameShape(rs, s)));

        layerDto.cells = layerDto.cells || [];
        for (const c of this.selection.cells) {
            const col = c.col + dx, row = c.row + dy;
            if (col < 0 || col >= this.gridWidth || row < 0 || row >= this.gridHeight) continue;
            layerDto.cells = layerDto.cells.filter((x) => !(x.col === col && x.row === row));
            layerDto.cells.push({ col, row, terrain: c.terrain });
        }
        layerDto.shapes = layerDto.shapes || [];
        for (const s of this.selection.shapes) {
            layerDto.shapes.push(this.shiftShape(structuredClone(s), dx, dy));
        }

        this.selection = {
            origin: { col: this.selection.origin.col + dx, row: this.selection.origin.row + dy },
            bounds: {
                minX: this.selection.bounds.minX + dx, maxX: this.selection.bounds.maxX + dx,
                minY: this.selection.bounds.minY + dy, maxY: this.selection.bounds.maxY + dy,
            },
            cells: this.selection.cells.map((c) => ({ ...c, col: c.col + dx, row: c.row + dy })),
            shapes: this.selection.shapes.map((s) => this.shiftShape(structuredClone(s), dx, dy)),
        };
        this.pasteOffset = null;

        this.renderDocument();
        this.drawSelectionMarker();
        this.markDirty();
    }
```

- [ ] **Step 5: Cut and delete**

Add these right after `copySelection` (end of the "Select / copy / paste" section):

```js
    cutSelection() {
        if (!this.selection) {
            this.setStatus('Nothing selected — use the Select tool (V) first');
            return;
        }
        this.copySelection();
        this.deleteSelectionContents();
    }

    deleteSelectionContents() {
        if (!this.selection) return;
        this.pushUndo();
        this.syncDocument();
        const layerDto = this.layerDto(this.activeLayerId);
        if (!layerDto) return;

        const removedCellKeys = new Set(this.selection.cells.map((c) => `${c.col},${c.row}`));
        layerDto.cells = (layerDto.cells || []).filter((c) => !removedCellKeys.has(`${c.col},${c.row}`));
        layerDto.shapes = (layerDto.shapes || []).filter((s) => !this.selection.shapes.some((rs) => this.sameShape(rs, s)));

        this.clearSelection();
        this.renderDocument();
        this.markDirty();
        this.setStatus('Deleted');
    }
```

Wire `Ctrl+X` in the keyboard handler's ctrl-combo block:

```js
            if (e.ctrlKey || e.metaKey) {
                const k = e.key.toLowerCase();
                if (k === 'z') { e.preventDefault(); e.shiftKey ? this.redo() : this.undo(); }
                else if (k === 'y') { e.preventDefault(); this.redo(); }
                else if (k === 'c' && this.selection) { e.preventDefault(); this.copySelection(); }
                else if (k === 'v' && this.clipboard) { e.preventDefault(); this.pasteClipboard(); }
                return;
            }
```

Replace with:

```js
            if (e.ctrlKey || e.metaKey) {
                const k = e.key.toLowerCase();
                if (k === 'z') { e.preventDefault(); e.shiftKey ? this.redo() : this.undo(); }
                else if (k === 'y') { e.preventDefault(); this.redo(); }
                else if (k === 'c' && this.selection) { e.preventDefault(); this.copySelection(); }
                else if (k === 'x' && this.selection) { e.preventDefault(); this.cutSelection(); }
                else if (k === 'v' && this.clipboard) { e.preventDefault(); this.pasteClipboard(); }
                return;
            }
```

Extend the Delete/Backspace case added in Task 5 to also cover a bulk selection. Find:

```js
                case 'delete':
                case 'backspace':
                    if (this.shapeSelection) {
                        e.preventDefault();
                        this.pushUndo();
                        const layer = this.shapeSelection.getLayer();
                        this.shapeSelection.destroy();
                        this.clearShapeSelection();
                        layer.batchDraw();
                        this.markDirty();
                    }
                    break;
```

Replace with:

```js
                case 'delete':
                case 'backspace':
                    if (this.shapeSelection) {
                        e.preventDefault();
                        this.pushUndo();
                        const layer = this.shapeSelection.getLayer();
                        this.shapeSelection.destroy();
                        this.clearShapeSelection();
                        layer.batchDraw();
                        this.markDirty();
                    } else if (this.selection) {
                        e.preventDefault();
                        this.deleteSelectionContents();
                    }
                    break;
```

- [ ] **Step 6: Fix the paste-offset accumulation**

`copySelection` currently reads:

```js
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
```

Replace with:

```js
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
        this.pasteOffset = { col: 1, row: 1 };
        this.setStatus('Copied — Ctrl+V to paste (offset by one cell)');
    }
```

`pasteClipboard` currently computes the offset from the *original* selection origin every time, so repeated pastes land on top of each other. Find:

```js
        this.pushUndo();
        this.syncDocument();
        const target = this.layerDto(this.activeLayerId);
        if (!target) return;

        const off = { col: (this.selection?.origin.col ?? 0) + 1, row: (this.selection?.origin.row ?? 0) + 1 };
        if (target.type === 'TERRAIN') {
```

Replace with:

```js
        if (!this.pasteOffset) this.pasteOffset = { col: 1, row: 1 };
        this.pushUndo();
        this.syncDocument();
        const target = this.layerDto(this.activeLayerId);
        if (!target) return;

        const off = this.pasteOffset;
        if (target.type === 'TERRAIN') {
```

And its end, currently:

```js
        this.renderDocument();
        this.markDirty();
        this.setStatus('Pasted');
    }
```

(this is the end of `pasteClipboard` — distinguish it from `deleteSelectionContents`, added in Step 5 above, by the preceding `target.shapes.push` loop). Replace with:

```js
        this.renderDocument();
        this.markDirty();
        this.setStatus('Pasted');
        this.pasteOffset = { col: off.col + 1, row: off.row + 1 };   // next paste lands one cell further
    }
```

- [ ] **Step 7: Manual verification**

1. Reload the editor, paint a small terrain patch, switch to Select, drag a marquee around it. Expected: dashed selection box, status bar mentions "drag to move".
2. Click inside the dashed box and drag to a new spot. Expected: the patch moves as one action (not left behind); Ctrl+Z undoes the whole move in one step.
3. Re-select the patch, press Ctrl+X, then Ctrl+V. Expected: the original is gone (cut) and a copy appears one cell over.
4. Press Ctrl+V three more times in a row. Expected: each paste lands one further cell down/right than the last — no more stacking on the same cells.
5. Select a patch, press Delete. Expected: it's removed in one undoable step.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js
git commit -m "feat(map-editor): drag-to-move, cut, and delete for bulk selections; fix paste stacking"
```

---

### Task 8: Right sidebar — relocate layers, add a shape properties panel

**Files:**
- Modify: `src/main/resources/templates/maps/editor.html`
- Modify: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:**
- Consumes: `this.shapeSelection` from Task 5.
- Produces: `map-shapeselect` window event (`{ shape: ShapeDto|null }`), `MapEditor.setShapeFill(color)`, `MapEditor.setShapeStroke(color)`, `MapEditor.setShapeLabel(text)`, `MapEditor.deleteShapeSelection()` (also used by Task 5/7's Delete-key handler, replacing its inline duplicate).

The toolbar is already at capacity (7 tool groups) and shapes have no way to change color — every shape is hardcoded to `SHAPE_COLORS.fill`/`SHAPE_COLORS.stroke`. Move the layer list into a proper right sidebar and add a shape properties panel there, which appears whenever a shape is transform-selected (Task 5).

- [ ] **Step 1: Restructure the page layout — canvas + sidebar row**

In `editor.html`, the body currently reads:

```html
    <div class="editor-container" x-data="toolbar()">
        <div class="editor-toolbar">
```

...(toolbar contents, unchanged in this task)...

```html
        <div class="editor-canvas-wrap" id="editorCanvasWrap">
            <!-- Konva stage mounts here -->
        </div>

        <div class="editor-statusbar">
```

Wrap the canvas in a new `.editor-body` flex row and move the layer-list `tool-group` out of the toolbar into a new `.editor-sidebar`. Find:

```html
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
```

Replace with:

```html
        </div>

        <div class="editor-body">
            <div class="editor-canvas-wrap" id="editorCanvasWrap">
                <!-- Konva stage mounts here -->
            </div>

            <div class="editor-sidebar">
                <div class="sidebar-section">
                    <h3>Layers</h3>
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

                <div class="sidebar-section" x-show="selectedShape" style="display: none;">
                    <h3>Shape</h3>
                    <label class="sidebar-field">Fill
                        <input type="color" x-model="selectedShape.fill" @change="applyShapeFill()">
                    </label>
                    <label class="sidebar-field">Stroke
                        <input type="color" x-model="selectedShape.stroke" @change="applyShapeStroke()">
                    </label>
                    <label class="sidebar-field">Label
                        <input type="text" x-model="selectedShape.label" @change="applyShapeLabel()" placeholder="Label…">
                    </label>
                    <button class="btn btn-ghost" style="width: 100%; margin-top: var(--space-xs);" @click="deleteShape()">Delete shape</button>
                </div>
            </div>
        </div>

        <div class="editor-statusbar">
```

Note the leading `</div>` in the replacement closes the `.editor-toolbar` div (previously closed further down, right before the canvas wrap — this replacement moves that closing tag up to right after the toolbar's last real tool-group, since the layer list block that used to be the toolbar's last child now lives in the sidebar).

- [ ] **Step 2: Add the layout CSS**

In `editor.html`'s `<style>` block, find:

```css
        .editor-canvas-wrap {
            flex: 1;
            overflow: hidden;
            position: relative;
            background: #0a0a1a;
        }
```

Replace with:

```css
        .editor-body {
            display: flex;
            flex: 1;
            overflow: hidden;
        }
        .editor-canvas-wrap {
            flex: 1;
            overflow: hidden;
            position: relative;
            background: #0a0a1a;
        }
        .editor-sidebar {
            width: 220px;
            flex-shrink: 0;
            overflow-y: auto;
            background: var(--color-surface);
            border-left: 1px solid var(--color-border);
            padding: var(--space-sm);
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
        .sidebar-field {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--space-xs);
            font-size: var(--text-sm);
            margin-bottom: var(--space-xs);
        }
        .sidebar-field input[type="text"] {
            flex: 1;
            min-width: 0;
        }
```

- [ ] **Step 3: Wire `selectedShape` state and its handlers into the Alpine `toolbar()` data**

Find:

```js
                layers: {
                    terrain: { visible: true, locked: false },
                    objects: { visible: true, locked: false },
                    annotations: { visible: true, locked: false },
                },
                init() {
                    window.addEventListener('map-toolchange', (e) => { this.tool = e.detail.tool; });
                    window.addEventListener('map-palette', (e) => {
                        this.terrains = [{ key: '__erase__', name: '◇ Erase' }, ...e.detail.terrains];
                    });
                    window.addEventListener('map-layerstate', (e) => {
                        for (const [id, state] of Object.entries(e.detail.layers)) {
                            if (this.layers[id]) Object.assign(this.layers[id], state);
                        }
                    });
                },
```

Replace with:

```js
                layers: {
                    terrain: { visible: true, locked: false },
                    objects: { visible: true, locked: false },
                    annotations: { visible: true, locked: false },
                },
                selectedShape: null,
                init() {
                    window.addEventListener('map-toolchange', (e) => { this.tool = e.detail.tool; });
                    window.addEventListener('map-palette', (e) => {
                        this.terrains = [{ key: '__erase__', name: '◇ Erase' }, ...e.detail.terrains];
                    });
                    window.addEventListener('map-layerstate', (e) => {
                        for (const [id, state] of Object.entries(e.detail.layers)) {
                            if (this.layers[id]) Object.assign(this.layers[id], state);
                        }
                    });
                    window.addEventListener('map-shapeselect', (e) => { this.selectedShape = e.detail.shape; });
                },
```

Add the panel's methods right after `toggleLocked(id) { window.mapEditor?.toggleLayerLocked(id); },`:

```js
                toggleLocked(id) { window.mapEditor?.toggleLayerLocked(id); },
                applyShapeFill() { window.mapEditor?.setShapeFill(this.selectedShape.fill); },
                applyShapeStroke() { window.mapEditor?.setShapeStroke(this.selectedShape.stroke); },
                applyShapeLabel() { window.mapEditor?.setShapeLabel(this.selectedShape.label); },
                deleteShape() { window.mapEditor?.deleteShapeSelection(); },
```

- [ ] **Step 4: Emit `map-shapeselect` from `MapEditor`, and add the color/label/delete methods**

In `selectShapeForTransform` (Task 5), find its last line:

```js
        this.setStatus('Shape selected — drag to move, handles to resize, Delete to remove, double-click to edit label');
    }
```

Replace with:

```js
        this.setStatus('Shape selected — drag to move, handles to resize, Delete to remove, double-click to edit label');
        this.emit('map-shapeselect', { shape: { ...node.getAttr('_shape') } });
    }
```

In `clearShapeSelection` (Task 5), find:

```js
    clearShapeSelection() {
        if (this.shapeSelection) {
            this.shapeSelection.draggable(false);
            this.shapeSelection.off('dragend.shapeselect');
        }
        this.shapeSelection = null;
        if (this.transformer) {
            this.transformer.nodes([]);
            this.transformer.getLayer()?.batchDraw();
        }
    }
```

Replace with:

```js
    clearShapeSelection() {
        if (this.shapeSelection) {
            this.shapeSelection.draggable(false);
            this.shapeSelection.off('dragend.shapeselect');
        }
        this.shapeSelection = null;
        if (this.transformer) {
            this.transformer.nodes([]);
            this.transformer.getLayer()?.batchDraw();
        }
        this.emit('map-shapeselect', { shape: null });
    }
```

Add the properties-panel methods right after `bakeShapeTransform` (and before `editShapeLabel`, added in Task 6):

```js
    setShapeFill(color) {
        if (!this.shapeSelection) return;
        this.pushUndo();
        const shape = this.shapeSelection.getAttr('_shape');
        shape.fill = color;
        this.shapeSelection.setAttr('_shape', shape);
        this.shapeSelection.fill(color);
        this.shapeSelection.getLayer().batchDraw();
        this.markDirty();
    }

    setShapeStroke(color) {
        if (!this.shapeSelection) return;
        this.pushUndo();
        const shape = this.shapeSelection.getAttr('_shape');
        shape.stroke = color;
        this.shapeSelection.setAttr('_shape', shape);
        this.shapeSelection.stroke(color);
        this.shapeSelection.getLayer().batchDraw();
        this.markDirty();
    }

    /** Shared by the sidebar's Delete button and the Delete/Backspace shortcut. */
    deleteShapeSelection() {
        if (!this.shapeSelection) return;
        this.pushUndo();
        const layer = this.shapeSelection.getLayer();
        this.shapeSelection.destroy();
        this.clearShapeSelection();
        layer.batchDraw();
        this.markDirty();
    }
```

`setShapeLabel(text)` duplicates `editShapeLabel`'s commit path but is driven by the sidebar's bound input instead of a floating `<input>` — add it right after `deleteShapeSelection`:

```js
    setShapeLabel(text) {
        if (!this.shapeSelection) return;
        const shape = this.shapeSelection.getAttr('_shape');
        const newLabel = (text || '').trim();
        if (newLabel === (shape.label || '')) return;
        this.pushUndo();
        shape.label = newLabel;
        this.shapeSelection.setAttr('_shape', shape);
        this.syncDocument();
        this.clearShapeSelection();
        this.renderDocument();
        this.markDirty();
    }
```

- [ ] **Step 5: Replace the Delete/Backspace keyboard case's inline duplicate with `deleteShapeSelection()`**

Find (as left by Task 7):

```js
                case 'delete':
                case 'backspace':
                    if (this.shapeSelection) {
                        e.preventDefault();
                        this.pushUndo();
                        const layer = this.shapeSelection.getLayer();
                        this.shapeSelection.destroy();
                        this.clearShapeSelection();
                        layer.batchDraw();
                        this.markDirty();
                    } else if (this.selection) {
                        e.preventDefault();
                        this.deleteSelectionContents();
                    }
                    break;
```

Replace with:

```js
                case 'delete':
                case 'backspace':
                    if (this.shapeSelection) {
                        e.preventDefault();
                        this.deleteShapeSelection();
                    } else if (this.selection) {
                        e.preventDefault();
                        this.deleteSelectionContents();
                    }
                    break;
```

- [ ] **Step 6: Manual verification**

1. Reload the editor. Expected: layers list now renders in a right sidebar, canvas fills the remaining width, no layout overflow/scrollbar issues at a normal window size.
2. Draw a rectangle, select it (Select tool, click it). Expected: a "Shape" section appears in the sidebar below Layers, with Fill/Stroke color swatches (both showing the current brown tones) and a Label field.
3. Change the Fill color swatch. Expected: the shape's fill updates immediately on canvas; the resize handles stay visible (selection is not lost, unlike a label edit).
4. Type into the sidebar's Label field and click elsewhere (blur/change). Expected: label appears on the shape; the Shape panel disappears (selection clears, matching the double-click label editor's behavior).
5. Select a shape, click "Delete shape" in the sidebar. Expected: it's removed and the panel disappears; Ctrl+Z restores it.
6. Click empty canvas. Expected: the Shape panel disappears.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/maps/editor.html src/main/resources/static/js/map/map-editor.js
git commit -m "feat(map-editor): right sidebar with layers and a shape color/label properties panel"
```

---

### Task 9: Undo consistency for custom terrain (layer visibility/lock intentionally excluded)

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:** none new.

`addCustomTerrain` mutates the document (adds a palette entry you can then paint with) without pushing an undo snapshot, while every other content edit does — mistakenly adding a bad terrain currently can't be undone with Ctrl+Z. Layer visibility/lock toggles are different: they're view/workflow state, not map content (nobody expects Ctrl+Z to undo a tool switch either), so this task leaves them out of the undo stack on purpose and says so in a comment, rather than blindly pushing undo everywhere for "consistency."

- [ ] **Step 1: Push undo before mutating custom terrain**

Find:

```js
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
```

Replace with:

```js
    addCustomTerrain(name, fill, walkable) {
        const key = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
        if (!key || this.palette[key] || !this.document) return null;
        this.pushUndo();
        this.syncDocument();
        this.document.customTerrain = this.document.customTerrain || [];
        this.document.customTerrain.push({ key, name, fill, walkable });
        this.rebuildPalette();
        this.setTerrain(key);
        this.markDirty();
        return key;
    }
```

- [ ] **Step 2: Document why layer toggles are excluded from undo**

Find:

```js
    toggleLayerVisible(id) {
        const l = this.layerDto(id);
```

Replace with:

```js
    // toggleLayerVisible/toggleLayerLocked intentionally do not pushUndo(): they're view/workflow
    // state, not map content, so Ctrl+Z shouldn't silently flip them while undoing a content edit.
    toggleLayerVisible(id) {
        const l = this.layerDto(id);
```

- [ ] **Step 3: Manual verification**

1. Reload the editor. Add a custom terrain via "+ Terrain" (still using `prompt()` at this point — Task 15 replaces that UI). Expected: it appears in the picker.
2. Press Ctrl+Z. Expected: the custom terrain disappears from the picker (the add is undone); Ctrl+Y (or Ctrl+Shift+Z) restores it.
3. Toggle a layer's visibility, then press Ctrl+Z. Expected: Ctrl+Z undoes your *last content edit*, not the visibility toggle (confirms the deliberate exclusion) — e.g. paint a cell, toggle Objects layer hidden, Ctrl+Z: the cell paint undoes, the layer stays hidden.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js
git commit -m "feat(map-editor): undo support for adding custom terrain"
```

---

### Task 10: Flush unsaved changes on navigation away

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:**
- Produces: `MapEditor.flushSave()`.

Autosave debounces 2 seconds. Draw something and immediately click "← Maps" (or close the tab) and the edit is silently lost — there is no `beforeunload` handler. Add one that best-effort flushes the pending save using `fetch(..., { keepalive: true })`, which (unlike a normal `fetch`) is allowed to outlive page unload in all evergreen browsers, and warns the user via the browser's native "leave site?" prompt.

- [ ] **Step 1: Add `flushSave()`**

Add this method right after `save()` (end of the "Autosave" section):

```js
    /** Best-effort synchronous-ish save for page unload — `keepalive: true` lets the
     *  request outlive navigation. Note: keepalive requests are capped around 64KB in
     *  Chrome, so this is a safety net for the gap since the last debounced autosave,
     *  not a replacement for it — very large maps may still exceed the cap. */
    flushSave() {
        if (!this.dirty || !this.document) return;
        clearTimeout(this.saveTimer);
        const doc = this.buildDocumentFromCanvas();
        if (!doc) return;
        this.dirty = false;
        fetch(`/api/v1/maps/${this.mapId}/document?expectedVersion=${this.docVersion}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(doc),
            keepalive: true,
        }).catch(() => {});
    }
```

- [ ] **Step 2: Warn on unload and trigger the flush**

In `setupEvents`, add right after the `window.addEventListener('keyup', ...)` block at the very end of the method:

```js
        window.addEventListener('keyup', (e) => {
            if (e.code === 'Space' && !this.panning) this.stage.draggable(false);
        });
    }
```

Replace with:

```js
        window.addEventListener('keyup', (e) => {
            if (e.code === 'Space' && !this.panning) this.stage.draggable(false);
        });

        window.addEventListener('beforeunload', (e) => {
            if (!this.dirty) return;
            this.flushSave();
            e.preventDefault();
            e.returnValue = '';
        });
    }
```

- [ ] **Step 3: Manual verification**

1. Reload the editor, open DevTools Network tab. Paint a cell, then immediately (within 2 seconds) click "← Maps".
2. Expected: the browser shows a native "leave site?" confirmation; a `PUT .../document` request appears in the Network tab regardless of your choice (fired from `flushSave` before the prompt).
3. Confirm leaving, navigate back into the same map's editor. Expected: the painted cell is present (the flush saved it).
4. With no unsaved changes, click "← Maps" again. Expected: no confirmation prompt (only fires when `this.dirty` is true).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js
git commit -m "feat(map-editor): flush unsaved changes and warn before navigating away"
```

---

### Task 11: Save indicator states + softer conflict handling with a JSON backup download

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Produces: `MapEditor.setSaveState(state)` (`'unsaved'|'saving'|'saved'|'error'|'conflict'`), `MapEditor.triggerDownload(blob, fileName)`, `MapEditor.downloadDocumentBackup()`; `map-conflict` window event. `triggerDownload` is reused by Task 14 (PNG export).

The save indicator's color is hardcoded green in the template (`style="color: var(--color-success);"`), so "Save failed!" and "Conflict!" both render green — actively misleading. And the 409-conflict path just tells the user to reload, discarding whatever they had unsaved with no recovery option. Fix the color coding and add a one-click JSON backup download before that reload.

- [ ] **Step 1: Add save-state CSS classes**

In `editor.html`'s `<style>` block, add after `.editor-statusbar { ... }`:

```css
        .save-indicator.save-saved { color: var(--color-success); }
        .save-indicator.save-saving,
        .save-indicator.save-unsaved { color: var(--color-warning); }
        .save-indicator.save-error,
        .save-indicator.save-conflict { color: var(--color-danger); }
```

- [ ] **Step 2: Add `setSaveState`, `triggerDownload`, `downloadDocumentBackup` to `MapEditor`**

Add these right before `markDirty` (start of the "Autosave" section):

```js
    setSaveState(state) {
        if (!this.saveIndicatorEl) return;
        const labels = { unsaved: 'Unsaved…', saving: 'Saving…', saved: 'Saved', error: 'Save failed!', conflict: 'Conflict!' };
        this.saveIndicatorEl.textContent = labels[state] || state;
        this.saveIndicatorEl.className = `save-indicator save-${state}`;
    }

    triggerDownload(blob, fileName) {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    }

    downloadDocumentBackup() {
        const doc = this.buildDocumentFromCanvas() || this.document;
        if (!doc) return;
        const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' });
        this.triggerDownload(blob, `map-backup-${this.mapId}.json`);
    }
```

- [ ] **Step 3: Use `setSaveState` throughout `markDirty`/`save`, and soften the conflict message**

Find:

```js
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
```

Replace with:

```js
    markDirty() {
        this.dirty = true;
        this.setSaveState('unsaved');
        clearTimeout(this.saveTimer);
        this.saveTimer = setTimeout(() => this.save(), SAVE_DEBOUNCE_MS);
    }

    async save() {
        if (!this.dirty) return;
        this.dirty = false;

        const doc = this.buildDocumentFromCanvas();
        if (!doc) return;
        this.document = doc;

        this.setSaveState('saving');
        try {
            const res = await fetch(
                `/api/v1/maps/${this.mapId}/document?expectedVersion=${this.docVersion}`,
                {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(doc),
                });
            if (res.status === 409) {
                this.setSaveState('conflict');
                this.setStatus('Map was changed elsewhere — download a backup below, then reload to continue');
                this.emit('map-conflict', {});
                return;
            }
            if (!res.ok) throw new Error('Save failed: ' + res.status);
            const data = await res.json();
            this.docVersion = data.version;
            this.setSaveState('saved');
        } catch (err) {
            console.error('Autosave failed:', err);
            this.setSaveState('error');
            this.setStatus('Save error — retrying');
            this.dirty = true;
            clearTimeout(this.saveTimer);
            this.saveTimer = setTimeout(() => this.save(), SAVE_DEBOUNCE_MS);
        }
    }
```

- [ ] **Step 4: Add the backup button to the status bar**

Find:

```html
        <div class="editor-statusbar">
            <span id="statusMessage">Ready</span>
            <span id="saveIndicator" style="color: var(--color-success);">Saved</span>
        </div>
```

Replace with:

```html
        <div class="editor-statusbar">
            <span id="statusMessage">Ready</span>
            <span>
                <button class="btn btn-ghost" style="padding: 0 6px; font-size: var(--text-sm); display: none;" x-show="conflict" @click="downloadBackup()">⬇ Backup</button>
                <span id="saveIndicator" class="save-indicator save-saved">Saved</span>
            </span>
        </div>
```

- [ ] **Step 5: Wire the `conflict` flag and backup action into Alpine**

Find:

```js
                selectedShape: null,
                init() {
```

Replace with:

```js
                selectedShape: null,
                conflict: false,
                init() {
```

Find (inside `init()`, the last listener added by Task 8):

```js
                    window.addEventListener('map-shapeselect', (e) => { this.selectedShape = e.detail.shape; });
                },
```

Replace with:

```js
                    window.addEventListener('map-shapeselect', (e) => { this.selectedShape = e.detail.shape; });
                    window.addEventListener('map-conflict', () => { this.conflict = true; });
                },
```

Add the `downloadBackup` method next to `deleteShape`:

```js
                deleteShape() { window.mapEditor?.deleteShapeSelection(); },
                downloadBackup() { window.mapEditor?.downloadDocumentBackup(); },
```

- [ ] **Step 6: Manual verification**

1. Reload the editor, paint a cell. Expected: indicator shows amber "Unsaved…", then amber-ish "Saving…", then green "Saved" ~2s later.
2. Open the same map in a second browser tab, paint something there and let it save (version bumps). Back in the first tab, paint something (still on the old version) and wait for autosave. Expected: indicator turns red "Conflict!", status bar says to download a backup, and a "⬇ Backup" button appears next to the indicator.
3. Click "⬇ Backup". Expected: a `map-backup-<id>.json` file downloads containing the current in-browser document (valid JSON, inspect it — your unsaved edits are in there).
4. Kill the backend (stop `mvn spring-boot:run`) mid-edit, paint something. Expected: indicator turns red "Save failed!" (not green), status bar says "Save error — retrying".

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): color-coded save states and a backup download on save conflict"
```

---

### Task 12: Primitive authoring tools — Room, Door, Region

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Produces: tools `'room'`, `'door'`, `'region'`; `MapEditor.commitRoomPrimitive(a, b)`, `MapEditor.commitRegionPrimitive(a, b)`, `MapEditor.commitDoorPrimitive(col, row)`.

`expandPrimitives()` already renders `ROOM` (walled border), `DOOR` (a door cell), `CORRIDOR` (intentionally invisible — open floor), and `REGION` (a filled rectangle) primitives, but nothing in the UI can ever create one — the whole primitive system has been dead code since M4. This task adds Room, Door, and Region tools. `CORRIDOR` is deliberately **not** given a tool: `expandPrimitives()` renders it as a no-op ("nothing to paint... REGION with terrain can be used for visible corridor flooring" — see its comment), so an interactive Corridor tool would place an invisible primitive with no visual feedback, which is a worse UX trap than not having the tool at all.

- [ ] **Step 1: Register the three new tools and gate them like the other terrain tools**

`DRAW_TOOLS` (extended by Task 3) currently reads:

```js
const DRAW_TOOLS = ['brush', 'rect', 'circle', 'line', 'polygon', 'fill-rect', 'bucket'];
```

Replace with:

```js
const DRAW_TOOLS = ['brush', 'rect', 'circle', 'line', 'polygon', 'fill-rect', 'bucket', 'room', 'door', 'region'];
```

- [ ] **Step 2: Wire `mousedown` / `mousemove` / `mouseup` for drag-based Room/Region and click-based Door**

In the `mousedown` handler, find (as left by Task 3):

```js
            } else if (this.activeTool === 'bucket') {
                this.floodFillAt(pos.col, pos.row, e.evt.button === 2);
            }
```

Replace with:

```js
            } else if (this.activeTool === 'bucket') {
                this.floodFillAt(pos.col, pos.row, e.evt.button === 2);
            } else if (this.activeTool === 'room' || this.activeTool === 'region') {
                this.drawing = true;
                this.shapeStart = { col: pos.col, row: pos.row };
            } else if (this.activeTool === 'door') {
                this.commitDoorPrimitive(pos.col, pos.row);
            }
```

In the `mousemove` handler, find (as left by Task 3):

```js
            } else if (this.activeTool === 'fill-rect' && this.shapeStart) {
                this.previewCellRect(this.shapeStart, { col: pos.col, row: pos.row });
```

Replace with:

```js
            } else if (['fill-rect', 'room', 'region'].includes(this.activeTool) && this.shapeStart) {
                this.previewCellRect(this.shapeStart, { col: pos.col, row: pos.row });
```

In the `mouseup` handler, find (as left by Task 3):

```js
            if (this.activeTool === 'fill-rect' && this.shapeStart && pos) {
                this.commitCellRect(this.shapeStart, { col: pos.col, row: pos.row }, e.evt.button === 2);
            } else if (this.shapeStart && pos) {
```

Replace with:

```js
            if (this.activeTool === 'fill-rect' && this.shapeStart && pos) {
                this.commitCellRect(this.shapeStart, { col: pos.col, row: pos.row }, e.evt.button === 2);
            } else if (this.activeTool === 'room' && this.shapeStart && pos) {
                this.commitRoomPrimitive(this.shapeStart, { col: pos.col, row: pos.row });
            } else if (this.activeTool === 'region' && this.shapeStart && pos) {
                this.commitRegionPrimitive(this.shapeStart, { col: pos.col, row: pos.row });
            } else if (this.shapeStart && pos) {
```

- [ ] **Step 3: Implement the three commit methods**

Add this section right after `floodFillAt` (end of the "Terrain bucket" section added in Task 4). Each one must call `syncDocument()` before mutating `this.document.primitives` and before `renderDocument()` — `renderDocument()` rebuilds every layer's Konva nodes straight from `this.document`, so skipping the sync would silently discard any brush/shape edits made since the last sync:

```js
    /* ---- Primitive authoring: Room / Door / Region (SPEC §4.3) ---- */

    commitRoomPrimitive(a, b) {
        if (this.layerDto(this.activeLayerId)?.type !== 'TERRAIN') {
            this.setStatus('Room/Door/Region primitives apply to the Terrain layer');
            return;
        }
        this.pushUndo();
        this.syncDocument();
        this.document.primitives = this.document.primitives || [];
        this.document.primitives.push({ type: 'ROOM', startCol: a.col, startRow: a.row, endCol: b.col, endRow: b.row });
        this.renderDocument();
        this.markDirty();
    }

    commitRegionPrimitive(a, b) {
        if (this.layerDto(this.activeLayerId)?.type !== 'TERRAIN') {
            this.setStatus('Room/Door/Region primitives apply to the Terrain layer');
            return;
        }
        this.pushUndo();
        this.syncDocument();
        this.document.primitives = this.document.primitives || [];
        this.document.primitives.push({
            type: 'REGION', startCol: a.col, startRow: a.row, endCol: b.col, endRow: b.row, terrain: this.terrain,
        });
        this.renderDocument();
        this.markDirty();
    }

    commitDoorPrimitive(col, row) {
        if (this.layerDto(this.activeLayerId)?.type !== 'TERRAIN') {
            this.setStatus('Room/Door/Region primitives apply to the Terrain layer');
            return;
        }
        this.pushUndo();
        this.syncDocument();
        this.document.primitives = this.document.primitives || [];
        this.document.primitives.push({ type: 'DOOR', startCol: col, startRow: row, endCol: col, endRow: row });
        this.renderDocument();
        this.markDirty();
    }
```

- [ ] **Step 4: Consolidate `setTool`'s status messages into a lookup table and add the new ones**

The nested ternary in `setTool` (grown across Tasks 3 and 4) is getting hard to read. Find:

```js
    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.activeTool = tool;
        this.emit('map-toolchange', { tool });
        this.setStatus(tool === 'polygon'
            ? 'Polygon: click vertices, double-click or Enter to close, Esc to cancel'
            : tool === 'select'
                ? 'Select: drag a box, Ctrl+C copy, Ctrl+V paste'
                : tool === 'fill-rect'
                    ? 'Terrain Rect: drag to fill an area, right-click drag to erase'
                    : tool === 'bucket'
                        ? 'Bucket: click a region to fill it with the active terrain, right-click to erase'
                        : 'Ready');
    }
```

Replace with:

```js
    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.activeTool = tool;
        this.emit('map-toolchange', { tool });
        const messages = {
            polygon: 'Polygon: click vertices, double-click or Enter to close, Esc to cancel',
            select: 'Select: drag a box, Ctrl+C copy, Ctrl+X cut, Ctrl+V paste, Delete remove',
            'fill-rect': 'Terrain Rect: drag to fill an area, right-click drag to erase',
            bucket: 'Bucket: click a region to fill it with the active terrain, right-click to erase',
            room: 'Room: drag a rectangle — walls the border, floors the interior',
            door: 'Door: click a cell to place a door',
            region: 'Region: drag a rectangle filled with the active terrain',
        };
        this.setStatus(messages[tool] || 'Ready');
    }
```

- [ ] **Step 5: Add keyboard shortcuts and toolbar buttons**

In the keyboard handler's `switch`, find (as left by Task 4):

```js
                case 'b': this.setTool('brush'); break;
                case 't': this.setTool('fill-rect'); break;
                case 'g': this.setTool('bucket'); break;
```

Replace with:

```js
                case 'b': this.setTool('brush'); break;
                case 't': this.setTool('fill-rect'); break;
                case 'g': this.setTool('bucket'); break;
                case 'm': this.setTool('room'); break;
                case 'd': this.setTool('door'); break;
                case 'n': this.setTool('region'); break;
```

In `editor.html`, find the Undo/Redo tool group (unchanged since M4):

```html
            <div class="tool-group">
                <button class="tool-btn" @click="undo()" title="Undo (Ctrl+Z)">↩ Undo</button>
                <button class="tool-btn" @click="redo()" title="Redo (Ctrl+Y)">↪ Redo</button>
            </div>
```

Replace with:

```html
            <div class="tool-group">
                <button class="tool-btn" :class="{ active: tool === 'room' }" @click="setTool('room')" title="Room (M): drag a rectangle — walls the border, floors the interior">▢ Room</button>
                <button class="tool-btn" :class="{ active: tool === 'door' }" @click="setTool('door')" title="Door (D): click a cell to place a door">▯ Door</button>
                <button class="tool-btn" :class="{ active: tool === 'region' }" @click="setTool('region')" title="Region (N): drag a rectangle filled with the active terrain">▥ Region</button>
            </div>

            <div class="tool-group">
                <button class="tool-btn" @click="undo()" title="Undo (Ctrl+Z)">↩ Undo</button>
                <button class="tool-btn" @click="redo()" title="Redo (Ctrl+Y)">↪ Redo</button>
            </div>
```

- [ ] **Step 6: Manual verification**

1. Reload the editor. Press `M` (or click Room), drag a 6×4 rectangle. Expected: on release, the border cells become Wall and the interior stays Floor — one undoable action.
2. Press `D`, click a wall cell on that room's border. Expected: it turns into a Door-colored cell.
3. Press `N`, pick Water, drag a rectangle elsewhere. Expected: the whole rectangle fills with Water in one action (functionally similar to Terrain Rect, but recorded as a semantic `REGION` primitive rather than individual cells).
4. Ctrl+Z three times. Expected: Region, then Door, then Room each undo as one step apiece.
5. Reload the page after placing a Room. Expected: the room persists (primitives round-trip through `PUT .../document` and `expandPrimitives()` re-renders them on load).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): Room, Door, and Region primitive authoring tools"
```

---

### Task 13: Background image layer (import, move, resize)

**Files:**
- Modify: `src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapLayerDto.java`
- Modify: `src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java`
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Produces (Java): `MapLayerDto.ImageDto(String dataUrl, double x, double y, double width, double height)`, `MapLayerDto.image()`.
- Produces (JS): `MapEditor.importBackgroundImage(dataUrl)`, `MapEditor.selectImageForTransform(node)`, `MapEditor.clearImageSelection()`, `MapEditor.commitImageTransform(node)`.

`LayerType.IMAGE` has existed since M4 with the comment "reserved for the post-v1 image-background layer — no v1 renderer." This app has no file-storage subsystem (no S3, no upload endpoint elsewhere), so rather than build one, the image is embedded directly as a base64 data URL inside the map document — consistent with how everything else in the document (cells, shapes) already lives in one CLOB. This keeps the whole feature client-side plus one small DTO field; no new backend endpoints.

- [ ] **Step 1: Add `ImageDto` to `MapLayerDto`**

Find:

```java
public record MapLayerDto(
        @JsonProperty(required = true) String id,
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) LayerType type,
        Boolean visible,
        Boolean locked,
        List<CellDto> cells,
        List<ShapeDto> shapes
) {
```

Replace with:

```java
public record MapLayerDto(
        @JsonProperty(required = true) String id,
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) LayerType type,
        Boolean visible,
        Boolean locked,
        List<CellDto> cells,
        List<ShapeDto> shapes,
        ImageDto image
) {
```

Find the `ShapeDto` record at the end of the file (the last record in `MapLayerDto`):

```java
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

Replace with:

```java
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

    /** Background reference image for a LayerType.IMAGE layer. The image is embedded as a
     *  base64 data URL — this app has no separate file-storage subsystem, so it lives in the
     *  document CLOB alongside everything else. x/y/width/height are in grid-cell units, the
     *  same coordinate space shapes use; no rotation, matching the shape schema's scope. */
    public record ImageDto(
            @JsonProperty(required = true) String dataUrl,
            double x, double y, double width, double height
    ) {}
}
```

- [ ] **Step 2: Test the document round-trips an image layer**

Add this test to `GameMapServiceTest` (same package as `MapLayerDto`, no import needed), right after `shouldCreateMapWithDefaultDocument`:

```java
    @Test
    void shouldRoundTripImageLayer() {
        GameMap map = service.create(campaign.getId(), "Cave", 10, 10, 48);

        String documentJson = """
                {
                  "schemaVersion": 1,
                  "grid": {"width": 10, "height": 10, "cellSizePx": 48, "gridType": "square"},
                  "layers": [
                    {"id": "terrain", "name": "Terrain", "type": "TERRAIN", "cells": [], "shapes": []},
                    {"id": "objects", "name": "Objects", "type": "OBJECTS", "cells": [], "shapes": []},
                    {"id": "annotations", "name": "Annotations (DM only)", "type": "ANNOTATIONS", "cells": [], "shapes": []},
                    {"id": "image", "name": "Background", "type": "IMAGE", "cells": [], "shapes": [],
                     "image": {"dataUrl": "data:image/png;base64,AAAA", "x": 0, "y": 0, "width": 10, "height": 10}}
                  ],
                  "primitives": [],
                  "customTerrain": []
                }
                """;

        long version = service.updateDocument(map.getId(), documentJson, map.getVersion());
        assertThat(version).isGreaterThan(map.getVersion());

        MapDocumentDto doc = service.getDocument(map.getId());
        assertThat(doc.layers()).hasSize(4);
        MapLayerDto imageLayer = doc.layers().get(3);
        assertThat(imageLayer.id()).isEqualTo("image");
        assertThat(imageLayer.type()).isEqualTo(MapLayerDto.LayerType.IMAGE);
        assertThat(imageLayer.image()).isNotNull();
        assertThat(imageLayer.image().dataUrl()).isEqualTo("data:image/png;base64,AAAA");
        assertThat(imageLayer.image().width()).isEqualTo(10);
    }
```

- [ ] **Step 3: Run the Java tests**

Run: `mvn -q -Dtest=GameMapServiceTest test`
Expected: `BUILD SUCCESS`, both `shouldCreateMapWithDefaultDocument` and `shouldRoundTripImageLayer` pass.

- [ ] **Step 4: Commit the backend change**

```bash
git add src/main/java/dev/hendrikhoemberg/dmhelper/gamemap/service/MapLayerDto.java src/test/java/dev/hendrikhoemberg/dmhelper/gamemap/service/GameMapServiceTest.java
git commit -m "feat(gamemap): add an optional background-image field to the IMAGE layer DTO"
```

- [ ] **Step 5: Create the Konva image layer first, so it renders behind the grid**

In `load()`, find:

```js
        this.gridLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.gridLayer);

        this.createLayer('terrain');
```

Replace with:

```js
        this.createLayer('image');   // background reference image — added first so it renders behind the grid

        this.gridLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.gridLayer);

        this.createLayer('terrain');
```

- [ ] **Step 6: Render the image in `renderDocument`**

Find:

```js
            if (layerDto.type === 'TERRAIN') {
                for (const cell of this.expandPrimitives()) {
                    this.addCellRect(kl, cell, { primitive: true });
                }
            }
            for (const cell of (layerDto.cells || [])) {
```

Replace with:

```js
            if (layerDto.type === 'TERRAIN') {
                for (const cell of this.expandPrimitives()) {
                    this.addCellRect(kl, cell, { primitive: true });
                }
            }
            if (layerDto.type === 'IMAGE' && layerDto.image) {
                this.addImageNode(kl, layerDto.image);
            }
            for (const cell of (layerDto.cells || [])) {
```

Add `addImageNode` right after `addShapeNode` (end of the "Rendering" section):

```js
    addImageNode(konvaLayer, imageDto) {
        const htmlImg = new Image();
        htmlImg.onload = () => {
            const node = new Konva.Image({
                image: htmlImg,
                x: imageDto.x * this.cellSizePx, y: imageDto.y * this.cellSizePx,
                width: imageDto.width * this.cellSizePx, height: imageDto.height * this.cellSizePx,
                listening: true,
            });
            node.setAttr('_imageLayer', true);
            konvaLayer.add(node);
            konvaLayer.batchDraw();
        };
        htmlImg.src = imageDto.dataUrl;
    }
```

- [ ] **Step 7: `importBackgroundImage` — read the file, size it to the grid, add/replace the layer**

Add this method right after `addCustomTerrain` (end of the "Palette" section):

```js
    /** Imports a background reference image (data URL from a FileReader). Sized to span the
     *  grid's full width with the image's own aspect ratio, at (0,0) — the user repositions
     *  and rescales it afterward with the Select tool (see selectImageForTransform). */
    importBackgroundImage(dataUrl) {
        if (!this.document) return;
        const probe = new Image();
        probe.onload = () => {
            this.pushUndo();
            this.syncDocument();
            const aspect = probe.naturalHeight / probe.naturalWidth;
            const width = this.gridWidth;
            const height = Math.max(1, Math.round(width * aspect));
            let layerDto = this.layerDto('image');
            if (!layerDto) {
                layerDto = { id: 'image', name: 'Background', type: 'IMAGE', visible: true, locked: false, cells: [], shapes: [] };
                this.document.layers.push(layerDto);
            }
            layerDto.image = { dataUrl, x: 0, y: 0, width, height };
            this.renderDocument();
            this.emitLayerState();
            this.markDirty();
            this.setStatus('Background image imported — Select tool to move/resize it');
        };
        probe.src = dataUrl;
    }
```

- [ ] **Step 8: Select/move/resize the image, reusing the Transformer from Task 5**

In the constructor, find (as left by Task 5):

```js
        this.shapeSelection = null;   // single Konva shape node currently selected for transform, or null
        this.transformer = null;
```

Replace with:

```js
        this.shapeSelection = null;   // single Konva shape node currently selected for transform, or null
        this.imageSelection = null;   // background-image Konva node currently selected for transform, or null
        this.transformer = null;
```

`selectShapeForTransform` (Task 5) must also drop any active image selection, and vice versa — the two are mutually exclusive. Find:

```js
    selectShapeForTransform(node) {
        this.clearSelection();
        this.cancelPolygon();
        this.clearShapeSelection();
        this.shapeSelection = node;
```

Replace with:

```js
    selectShapeForTransform(node) {
        this.clearSelection();
        this.cancelPolygon();
        this.clearShapeSelection();
        this.clearImageSelection();
        this.shapeSelection = node;
```

Add `selectImageForTransform`, `clearImageSelection`, `commitImageTransform` right after `bakeShapeTransform` (before the color/label methods added in Task 8):

```js
    selectImageForTransform(node) {
        this.clearShapeSelection();
        this.clearSelection();
        this.cancelPolygon();
        this.imageSelection = node;
        node.draggable(true);
        node.on('dragend.imageselect', () => this.commitImageTransform(node));
        this.transformer.keepRatio(false);
        this.transformer.nodes([node]);
        this.transformer.getLayer().batchDraw();
        this.setStatus('Background image selected — drag to move, handles to resize');
    }

    clearImageSelection() {
        if (this.imageSelection) {
            this.imageSelection.draggable(false);
            this.imageSelection.off('dragend.imageselect');
        }
        this.imageSelection = null;
        if (this.transformer) {
            this.transformer.nodes([]);
            this.transformer.getLayer()?.batchDraw();
        }
    }

    commitImageTransform(node) {
        this.pushUndo();
        this.syncDocument();
        const layerDto = this.layerDto('image');
        if (layerDto && layerDto.image) {
            const cs = this.cellSizePx;
            layerDto.image = {
                dataUrl: layerDto.image.dataUrl,
                x: this.round2(node.x() / cs), y: this.round2(node.y() / cs),
                width: this.round2((node.width() * node.scaleX()) / cs),
                height: this.round2((node.height() * node.scaleY()) / cs),
            };
        }
        this.clearImageSelection();
        this.renderDocument();
        this.markDirty();
    }
```

The Transformer's `transformend` handler (Task 5) only knows about shapes — extend it to also handle the image. Find:

```js
        this.transformer.on('transformend', () => {
            const node = this.shapeSelection;
            if (!node) return;
            this.pushUndo();
            this.bakeShapeTransform(node);
            this.syncDocument();
            this.clearShapeSelection();
            this.renderDocument();
            this.markDirty();
        });
```

Replace with:

```js
        this.transformer.on('transformend', () => {
            if (this.shapeSelection) {
                const node = this.shapeSelection;
                this.pushUndo();
                this.bakeShapeTransform(node);
                this.syncDocument();
                this.clearShapeSelection();
                this.renderDocument();
                this.markDirty();
            } else if (this.imageSelection) {
                this.commitImageTransform(this.imageSelection);
            }
        });
```

Click-to-select in the Select tool's `mousedown` branch (as left by Task 7) must also recognize the image node. Find:

```js
            if (this.activeTool === 'select') {
                const shapeNode = e.target && e.target.getAttr('_shape') ? e.target : null;
                if (shapeNode) {
                    this.selectShapeForTransform(shapeNode);
                    return;
                }
                this.clearShapeSelection();
                if (this.selection && this.pointInSelectionBounds(pos)) {
```

Replace with:

```js
            if (this.activeTool === 'select') {
                const shapeNode = e.target && e.target.getAttr('_shape') ? e.target : null;
                if (shapeNode) {
                    this.selectShapeForTransform(shapeNode);
                    return;
                }
                const imageNode = e.target && e.target.getAttr('_imageLayer') ? e.target : null;
                if (imageNode) {
                    this.selectImageForTransform(imageNode);
                    return;
                }
                this.clearShapeSelection();
                this.clearImageSelection();
                if (this.selection && this.pointInSelectionBounds(pos)) {
```

- [ ] **Step 9: Clear the image selection alongside the shape selection on tool/layer switches and Escape**

Find (as left by Task 5):

```js
    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.activeTool = tool;
```

Replace with:

```js
    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.clearImageSelection();
        this.activeTool = tool;
```

Find (as left by Task 5):

```js
    setLayer(layerId) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.activeLayerId = layerId;
    }
```

Replace with:

```js
    setLayer(layerId) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.clearImageSelection();
        this.activeLayerId = layerId;
    }
```

Find (as left by Task 5):

```js
                case 'escape':
                    this.cancelPolygon();
                    this.clearSelection();
                    this.clearShapeSelection();
                    break;
```

Replace with:

```js
                case 'escape':
                    this.cancelPolygon();
                    this.clearSelection();
                    this.clearShapeSelection();
                    this.clearImageSelection();
                    break;
```

- [ ] **Step 10: Add the Import Image button, the Background layer entry, and Alpine wiring**

In `editor.html`, find (this exact boundary was left by Task 8's Step 1 — the toolbar's last group followed by the body wrapper):

```html
        </div>

        <div class="editor-body">
```

Replace with:

```html
            <div class="tool-group">
                <button class="tool-btn" @click="$refs.imageInput.click()" title="Import a background reference image (PNG/JPG)">🖼 Import Image</button>
                <input type="file" accept="image/*" x-ref="imageInput" style="display: none;" @change="importImage($event)">
            </div>
        </div>

        <div class="editor-body">
```

Add a "Background" entry to the sidebar's layer list. Find:

```js
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
```

Replace with:

```js
                layerList: [
                    { id: 'image', name: 'Background' },
                    { id: 'terrain', name: 'Terrain' },
                    { id: 'objects', name: 'Objects' },
                    { id: 'annotations', name: 'Annotations (DM only)' },
                ],
                layers: {
                    image: { visible: true, locked: false },
                    terrain: { visible: true, locked: false },
                    objects: { visible: true, locked: false },
                    annotations: { visible: true, locked: false },
                },
```

Add the `importImage` method next to `downloadBackup` (added in Task 11):

```js
                downloadBackup() { window.mapEditor?.downloadDocumentBackup(); },
                importImage(event) {
                    const file = event.target.files[0];
                    if (!file) return;
                    const reader = new FileReader();
                    reader.onload = () => window.mapEditor?.importBackgroundImage(reader.result);
                    reader.readAsDataURL(file);
                    event.target.value = '';
                },
```

- [ ] **Step 11: Manual verification**

1. `mvn spring-boot:run`, open the map editor. Click "Import Image", pick any PNG/JPG. Expected: the image appears spanning the grid's width, behind the grid lines and any painted terrain.
2. Switch to Select, click the image. Expected: resize handles appear (no rotate handle); status bar says "Background image selected…".
3. Drag it to a new position; drag a corner handle to resize it. Expected: both work and persist across a page reload.
4. In the sidebar, toggle the "Background" layer's visibility. Expected: the image hides/shows without affecting Terrain/Objects/Annotations.
5. Paint a terrain cell, then immediately import an image (without reloading). Expected: the painted cell is still there after import (confirms `importBackgroundImage`'s `syncDocument()` call prevented the render from discarding it).

- [ ] **Step 12: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): background reference image — import, move, and resize"
```

---

### Task 14: PNG export

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Produces: `MapEditor.exportPng()`.
- Consumes/refines: `MapEditor.triggerDownload` (Task 11) — widened from "take a Blob" to "take any URL (object URL or data URL)" so both the JSON backup and the PNG export share one code path.

There's no way to get a map out of the editor for use in a VTT or for printing. `Konva.Stage.toDataURL()` already renders the whole visible stage to a PNG data URL synchronously — export just needs to hide the selection/preview overlay first and reuse the download helper from Task 11.

- [ ] **Step 1: Widen `triggerDownload` to accept a URL directly**

Find (from Task 11):

```js
    triggerDownload(blob, fileName) {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    }

    downloadDocumentBackup() {
        const doc = this.buildDocumentFromCanvas() || this.document;
        if (!doc) return;
        const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' });
        this.triggerDownload(blob, `map-backup-${this.mapId}.json`);
    }
```

Replace with:

```js
    /** Triggers a browser download for any URL — an object URL (caller creates/revokes it)
     *  or a data: URL (e.g. Konva's toDataURL(), which needs no object URL at all). */
    triggerDownload(url, fileName) {
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
    }

    downloadDocumentBackup() {
        const doc = this.buildDocumentFromCanvas() || this.document;
        if (!doc) return;
        const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        this.triggerDownload(url, `map-backup-${this.mapId}.json`);
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
```

- [ ] **Step 2: Add `exportPng`**

Add this method right after `downloadDocumentBackup`:

```js
    exportPng() {
        if (!this.stage) return;
        this.clearShapeSelection();
        this.clearImageSelection();
        this.previewLayer.visible(false);   // hide selection/drag preview overlay from the export
        const dataUrl = this.stage.toDataURL({ pixelRatio: 2 });
        this.previewLayer.visible(true);
        this.stage.batchDraw();
        this.triggerDownload(dataUrl, `map-${this.mapId}.png`);
    }
```

- [ ] **Step 3: Add the toolbar button**

In `editor.html`, find the Import Image group (added in Task 13):

```html
            <div class="tool-group">
                <button class="tool-btn" @click="$refs.imageInput.click()" title="Import a background reference image (PNG/JPG)">🖼 Import Image</button>
                <input type="file" accept="image/*" x-ref="imageInput" style="display: none;" @change="importImage($event)">
            </div>
```

Replace with:

```html
            <div class="tool-group">
                <button class="tool-btn" @click="$refs.imageInput.click()" title="Import a background reference image (PNG/JPG)">🖼 Import Image</button>
                <input type="file" accept="image/*" x-ref="imageInput" style="display: none;" @change="importImage($event)">
                <button class="tool-btn" @click="exportPng()" title="Export the current map view as a PNG">⬇ Export PNG</button>
            </div>
```

Add the Alpine method next to `importImage`:

```js
                importImage(event) {
                    const file = event.target.files[0];
                    if (!file) return;
                    const reader = new FileReader();
                    reader.onload = () => window.mapEditor?.importBackgroundImage(reader.result);
                    reader.readAsDataURL(file);
                    event.target.value = '';
                },
                exportPng() { window.mapEditor?.exportPng(); },
```

- [ ] **Step 4: Manual verification**

1. Paint some terrain, add a shape with a label, import a background image. Click "Export PNG". Expected: a `map-<id>.png` file downloads.
2. Open the downloaded PNG. Expected: it shows the grid, terrain, the background image, and the shape with its label — matching what's on screen — with no dashed selection boxes or resize handles even if something was selected when you clicked Export.
3. Click a shape to select it (handles visible), then Export. Expected: handles do not appear in the exported PNG, and — separately — the shape is still selected in the live editor afterward is not guaranteed (export deselects); reselect and continue editing normally.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): export the map as a PNG"
```

---

### Task 15: Terrain palette UI — color swatches, and an add/edit/delete popover replacing `prompt()`/`confirm()`

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Produces: `MapEditor.removeCustomTerrain(key)`; `map-palette` event's `terrains` entries now carry `fill` and `custom` (previously just `key`/`name`).

The terrain picker is a `<select>` of names with no color preview, and adding a custom terrain currently chains three native `prompt()`/`confirm()` dialogs — the roughest UI edge in the editor. Replace the dropdown with clickable color swatches, and the add flow with a small popover that also lists (and can delete) existing custom terrains.

- [ ] **Step 1: Emit color and custom-ness with the palette event**

Find:

```js
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
```

Replace with:

```js
    rebuildPalette() {
        this.palette = { ...BUILTIN_TERRAIN };
        for (const def of (this.document?.customTerrain || [])) {
            this.palette[def.key] = {
                name: def.name, fill: def.fill, stroke: def.fill,
                walkable: !!def.walkable, custom: true,
            };
        }
        this.emit('map-palette', {
            terrains: Object.entries(this.palette).map(([key, t]) => ({ key, name: t.name, fill: t.fill, custom: !!t.custom })),
        });
    }
```

- [ ] **Step 2: Add `removeCustomTerrain`**

Add this method right after `addCustomTerrain`:

```js
    /** Removes a custom terrain from the palette. Cells already painted with it keep their
     *  terrain key; `addCellRect`'s `this.palette[cell.terrain] || this.palette[DEFAULT_TERRAIN]`
     *  fallback already renders unknown keys as the default terrain, so no migration is needed. */
    removeCustomTerrain(key) {
        if (!this.document || !(this.document.customTerrain || []).some((t) => t.key === key)) return;
        this.pushUndo();
        this.syncDocument();
        this.document.customTerrain = (this.document.customTerrain || []).filter((t) => t.key !== key);
        this.rebuildPalette();
        if (this.terrain === key) this.setTerrain(DEFAULT_TERRAIN);
        this.renderDocument();
        this.markDirty();
    }
```

- [ ] **Step 3: Replace the `<select>` + "+ Terrain" button with swatches and a manage popover**

In `editor.html`, find (the terrain half of the first tool-group, as left by Tasks 2–4):

```html
                <select class="tool-btn" style="padding: 6px 8px;" x-model="terrain" @change="applyTerrain()">
                    <template x-for="t in terrains" :key="t.key">
                        <option :value="t.key" x-text="t.name"></option>
                    </template>
                </select>
                <button class="tool-btn" @click="addTerrain()" title="Add custom terrain (name + color + walkable)">+ Terrain</button>
```

Replace with:

```html
                <template x-for="t in terrains" :key="t.key">
                    <button class="terrain-swatch" :class="{ active: terrain === t.key, erase: t.key === '__erase__' }"
                            :style="t.fill ? ('background:' + t.fill) : ''"
                            :title="t.name" @click="terrain = t.key; applyTerrain()"></button>
                </template>
                <button class="tool-btn" @click="terrainPanelOpen = !terrainPanelOpen" title="Manage terrain palette">⚙</button>
                <div class="terrain-panel" x-show="terrainPanelOpen" style="display: none;" @click.outside="terrainPanelOpen = false">
                    <div class="terrain-panel-list">
                        <template x-for="t in terrains.filter((x) => x.key !== '__erase__')" :key="t.key">
                            <div class="terrain-panel-row">
                                <span class="terrain-swatch-sm" :style="t.fill ? ('background:' + t.fill) : ''"></span>
                                <span style="flex: 1;" x-text="t.name"></span>
                                <button x-show="t.custom" @click="deleteTerrain(t.key)" title="Delete this terrain">🗑</button>
                            </div>
                        </template>
                    </div>
                    <div class="terrain-panel-form">
                        <input type="text" x-model="newTerrainName" placeholder="New terrain name" @keydown.enter="addTerrain()">
                        <input type="color" x-model="newTerrainColor">
                        <label style="display: flex; align-items: center; gap: 4px; font-size: var(--text-sm);">
                            <input type="checkbox" x-model="newTerrainWalkable"> Walkable
                        </label>
                        <button class="btn btn-primary" @click="addTerrain()">Add terrain</button>
                    </div>
                </div>
```

The parent tool-group needs `position: relative;` so the popover anchors correctly. Find:

```html
            <div class="tool-group">
                <button class="tool-btn" :class="{ active: tool === 'brush' }" @click="setTool('brush')" title="Brush (B) — right-click to erase">🖌 Brush</button>
```

Replace with:

```html
            <div class="tool-group" style="position: relative;">
                <button class="tool-btn" :class="{ active: tool === 'brush' }" @click="setTool('brush')" title="Brush (B) — right-click to erase">🖌 Brush</button>
```

- [ ] **Step 4: Style the swatches and popover**

In `editor.html`'s `<style>` block, add after `.layer-item button { ... }`:

```css
        .terrain-swatch {
            width: 22px;
            height: 22px;
            border: 2px solid var(--color-border);
            border-radius: 4px;
            cursor: pointer;
            padding: 0;
        }
        .terrain-swatch.active { border-color: var(--color-accent); }
        .terrain-swatch.erase { background: repeating-linear-gradient(45deg, #444 0 4px, #222 4px 8px); }
        .terrain-swatch-sm {
            width: 14px;
            height: 14px;
            border-radius: 3px;
            display: inline-block;
            border: 1px solid var(--color-border);
            flex-shrink: 0;
        }
        .terrain-panel {
            position: absolute;
            top: 100%;
            left: 0;
            z-index: 30;
            margin-top: 4px;
            width: 220px;
            background: var(--color-surface);
            border: 1px solid var(--color-border);
            border-radius: 6px;
            padding: var(--space-sm);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
        }
        .terrain-panel-list {
            max-height: 200px;
            overflow-y: auto;
            margin-bottom: var(--space-sm);
        }
        .terrain-panel-row {
            display: flex;
            align-items: center;
            gap: var(--space-xs);
            padding: 2px 0;
            font-size: var(--text-sm);
        }
        .terrain-panel-form {
            display: flex;
            flex-direction: column;
            gap: 4px;
            border-top: 1px solid var(--color-border);
            padding-top: var(--space-xs);
        }
        .terrain-panel-form input[type="text"] { width: 100%; }
```

- [ ] **Step 5: Update Alpine state — seed colors for the pre-load swatches, and the new form/popover fields**

Find:

```js
                terrains: [
                    { key: '__erase__', name: '◇ Erase' },
                    { key: 'floor', name: 'Floor' },
                    { key: 'wall', name: 'Wall' },
                    { key: 'water', name: 'Water' },
                    { key: 'difficult', name: 'Difficult Terrain' },
                    { key: 'lava', name: 'Lava' },
                    { key: 'pit', name: 'Pit' },
                    { key: 'door', name: 'Door' },
                ],
```

Replace with (colors mirror `BUILTIN_TERRAIN` in `terrain-palette.js` — this is only a placeholder used for the first paint, before the `map-palette` event's authoritative colors arrive a moment later, matching the pre-existing seed-list pattern this array already followed):

```js
                terrains: [
                    { key: '__erase__', name: '◇ Erase' },
                    { key: 'floor', name: 'Floor', fill: '#2a2a3e' },
                    { key: 'wall', name: 'Wall', fill: '#4a4a5e' },
                    { key: 'water', name: 'Water', fill: '#1a3a6e' },
                    { key: 'difficult', name: 'Difficult Terrain', fill: '#3a4a1e' },
                    { key: 'lava', name: 'Lava', fill: '#6e2a1a' },
                    { key: 'pit', name: 'Pit', fill: '#1a1a1a' },
                    { key: 'door', name: 'Door', fill: '#8a6a2e' },
                ],
                terrainPanelOpen: false,
                newTerrainName: '',
                newTerrainColor: '#2a6e3a',
                newTerrainWalkable: true,
```

- [ ] **Step 6: Replace `addTerrain()` (no more `prompt()`/`confirm()`) and add `deleteTerrain()`**

Find:

```js
                addTerrain() {
                    const name = prompt('Terrain name:');
                    if (!name || !name.trim()) return;
                    const fill = prompt('Fill color (hex, e.g. #2a6e3a):', '#2a6e3a');
                    if (!fill || !fill.trim()) return;
                    const walkable = confirm('Walkable terrain? (OK = yes, Cancel = no)');
                    const key = window.mapEditor?.addCustomTerrain(name.trim(), fill.trim(), walkable);
                    if (key) this.terrain = key;
                },
```

Replace with:

```js
                addTerrain() {
                    const name = this.newTerrainName.trim();
                    if (!name) return;
                    const key = window.mapEditor?.addCustomTerrain(name, this.newTerrainColor, this.newTerrainWalkable);
                    if (key) {
                        this.terrain = key;
                        this.newTerrainName = '';
                    }
                },
                deleteTerrain(key) { window.mapEditor?.removeCustomTerrain(key); },
```

- [ ] **Step 7: Manual verification**

1. Reload the editor. Expected: a row of colored square swatches (Erase, Floor, Wall, …) replaces the dropdown; clicking one selects it (blue border) exactly like the old dropdown did.
2. Click the ⚙ button. Expected: a popover opens listing every terrain with its swatch; built-in terrains have no delete (🗑) button, only custom ones will once added.
3. Type a name, pick a color, toggle Walkable off, click "Add terrain". Expected: no native browser dialogs appear; the new terrain shows up as both a toolbar swatch and a popover list row with a 🗑 button; it's auto-selected as the active terrain.
4. Click 🗑 next to the new terrain. Expected: it disappears from both the toolbar and the popover list; if it was the active terrain, the active terrain resets to Floor.
5. Click outside the popover. Expected: it closes (`@click.outside`).
6. Reload the page after adding a custom terrain. Expected: it's still there (persisted via `document.customTerrain`).
7. Delete a custom terrain and re-add one with the *same name* but a different color. Expected: any cells already painted with it pick up the new color immediately — `addCellRect` looks up `this.palette[cell.terrain]` by key at render time rather than baking a color into each cell, and `addCustomTerrain`'s key is a deterministic slug of the name, so delete-then-re-add with the same name is a full in-place edit (color and walkable both update) without needing a separate "edit" affordance or any extra code.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): terrain color swatches and a proper add/delete popover"
```

---

### Task 16: Cursor feedback — brush hover preview, per-tool cursors, status-bar coordinates and zoom

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Produces: `MapEditor.cursorForTool(tool)`, `MapEditor.updateHoverPreview(pos)`, `MapEditor.clearHoverPreview()`, `MapEditor.updateCursorInfo(pos)`; new constructor option `cursorInfoEl`.

There's no feedback about what a click will do before you click: no ghost of the brush footprint, no cursor change between tools, and no coordinate/zoom readout. `brushFootprint` (Task 1) and `this.stage.getRelativePointerPosition()` already have everything needed.

- [ ] **Step 1: Add the `cursorInfoEl` constructor option**

Find:

```js
    constructor({ container, mapId, gridWidth, gridHeight, cellSizePx, statusEl, saveIndicatorEl }) {
        this.container = container;
        this.mapId = mapId;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.cellSizePx = cellSizePx;
        this.statusEl = statusEl;
        this.saveIndicatorEl = saveIndicatorEl;
```

Replace with:

```js
    constructor({ container, mapId, gridWidth, gridHeight, cellSizePx, statusEl, saveIndicatorEl, cursorInfoEl }) {
        this.container = container;
        this.mapId = mapId;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.cellSizePx = cellSizePx;
        this.statusEl = statusEl;
        this.saveIndicatorEl = saveIndicatorEl;
        this.cursorInfoEl = cursorInfoEl;
```

- [ ] **Step 2: Add `cursorForTool`, hover preview, and cursor-info methods**

Add these right after `setStatus` (end of the "Misc" section):

```js
    cursorForTool(tool) {
        return tool === 'select' ? 'default' : 'crosshair';
    }

    updateHoverPreview(pos) {
        this.clearHoverPreview();
        if (this.drawing) return;
        if (this.activeTool !== 'brush' && this.activeTool !== 'bucket') return;

        const s = this.cellSizePx;
        const cells = this.activeTool === 'brush' ? this.brushFootprint(pos.col, pos.row) : [{ col: pos.col, row: pos.row }];
        const t = this.palette[this.terrain] || this.palette[DEFAULT_TERRAIN];
        for (const c of cells) {
            if (c.col < 0 || c.col >= this.gridWidth || c.row < 0 || c.row >= this.gridHeight) continue;
            const rect = new Konva.Rect({
                x: c.col * s, y: c.row * s, width: s, height: s,
                fill: t.fill, opacity: 0.5, listening: false,
            });
            rect.setAttr('_hover', true);
            this.previewLayer.add(rect);
        }
        this.previewLayer.batchDraw();
    }

    clearHoverPreview() {
        for (const child of [...this.previewLayer.getChildren()]) {
            if (child.getAttr('_hover')) child.destroy();
        }
    }

    updateCursorInfo(pos) {
        if (!this.cursorInfoEl) return;
        const zoom = Math.round(this.stage.scaleX() * 100);
        this.cursorInfoEl.textContent = `(${pos.col}, ${pos.row}) · ${zoom}%`;
    }
```

- [ ] **Step 3: Set the cursor per tool in `setTool`, and initialize it at load**

Find (as left by Task 13):

```js
    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.clearImageSelection();
        this.activeTool = tool;
```

Replace with:

```js
    setTool(tool) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.clearImageSelection();
        this.activeTool = tool;
        this.container.style.cursor = this.cursorForTool(tool);
```

In `load()`, find:

```js
        this.initTransformer();
        this.drawGrid();
        this.setupEvents();
        this.fetchDocument();
```

Replace with:

```js
        this.initTransformer();
        this.container.style.cursor = this.cursorForTool(this.activeTool);
        this.drawGrid();
        this.setupEvents();
        this.fetchDocument();
```

- [ ] **Step 4: Pan cursor (grab/grabbing) for middle-mouse and Space-drag panning**

Find:

```js
            if (e.evt.button === 1) {   // middle mouse: pan
                this.panning = true;
                this.stage.draggable(true);
                return;
            }
```

Replace with:

```js
            if (e.evt.button === 1) {   // middle mouse: pan
                this.panning = true;
                this.stage.draggable(true);
                this.container.style.cursor = 'grabbing';
                return;
            }
```

Find:

```js
            if (this.panning) {
                this.panning = false;
                this.stage.draggable(false);
                return;
            }
```

Replace with:

```js
            if (this.panning) {
                this.panning = false;
                this.stage.draggable(false);
                this.container.style.cursor = this.cursorForTool(this.activeTool);
                return;
            }
```

Find:

```js
            if (e.code === 'Space') {
                if (!this.drawing) {
                    e.preventDefault();
                    this.stage.draggable(true);
                }
                return;
            }
```

Replace with:

```js
            if (e.code === 'Space') {
                if (!this.drawing) {
                    e.preventDefault();
                    this.stage.draggable(true);
                    this.container.style.cursor = 'grab';
                }
                return;
            }
```

Find (as left by Task 10):

```js
        window.addEventListener('keyup', (e) => {
            if (e.code === 'Space' && !this.panning) this.stage.draggable(false);
        });
```

Replace with:

```js
        window.addEventListener('keyup', (e) => {
            if (e.code === 'Space' && !this.panning) {
                this.stage.draggable(false);
                this.container.style.cursor = this.cursorForTool(this.activeTool);
            }
        });
```

- [ ] **Step 5: Update the hover preview and cursor info on every `mousemove`, and on zoom**

Find:

```js
        this.stage.on('mousemove touchmove', () => {
            if (this.panning) return;
            const pos = this.cellPos();
            if (!pos) return;

            if (this.activeTool === 'polygon' && this.polygonPoints.length) {
```

Replace with:

```js
        this.stage.on('mousemove touchmove', () => {
            if (this.panning) return;
            const pos = this.cellPos();
            if (!pos) return;
            this.updateHoverPreview(pos);
            this.updateCursorInfo(pos);

            if (this.activeTool === 'polygon' && this.polygonPoints.length) {
```

Add a stage `mouseleave` handler right after the `wheel` handler (so the readout clears when the pointer leaves the canvas). Find:

```js
        // Keyboard shortcuts (§4.3)
```

Replace with:

```js
        this.stage.on('mouseleave', () => {
            this.clearHoverPreview();
            this.previewLayer.batchDraw();
            if (this.cursorInfoEl) this.cursorInfoEl.textContent = '';
        });

        // Keyboard shortcuts (§4.3)
```

In the `wheel` handler, find its last line:

```js
            this.stage.batchDraw();
        });

        // Keyboard shortcuts (§4.3)
```

This text now includes the `mouseleave` handler just inserted above — to avoid ambiguity, anchor on the wheel handler's body instead. Find:

```js
            this.stage.position({
                x: pointer.x - mousePointTo.x * newScale,
                y: pointer.y - mousePointTo.y * newScale,
            });
            this.stage.batchDraw();
        });
```

Replace with:

```js
            this.stage.position({
                x: pointer.x - mousePointTo.x * newScale,
                y: pointer.y - mousePointTo.y * newScale,
            });
            this.stage.batchDraw();
            const p = this.cellPos();
            if (p) this.updateCursorInfo(p);
        });
```

- [ ] **Step 6: Add the status-bar cursor-info element and wire it in**

In `editor.html`, find:

```html
        <div class="editor-statusbar">
            <span id="statusMessage">Ready</span>
            <span>
```

Replace with:

```html
        <div class="editor-statusbar">
            <span id="statusMessage">Ready</span>
            <span id="cursorInfo" style="color: var(--color-text-muted);"></span>
            <span>
```

In the module script at the bottom of the page, find:

```js
        const editor = new MapEditor({
            container: document.getElementById('editorCanvasWrap'),
            mapId: /*[[${map.id}]]*/ '',
            gridWidth: /*[[${map.gridWidth}]]*/ 30,
            gridHeight: /*[[${map.gridHeight}]]*/ 20,
            cellSizePx: /*[[${map.cellSizePx}]]*/ 48,
            statusEl: document.getElementById('statusMessage'),
            saveIndicatorEl: document.getElementById('saveIndicator'),
        });
```

Replace with:

```js
        const editor = new MapEditor({
            container: document.getElementById('editorCanvasWrap'),
            mapId: /*[[${map.id}]]*/ '',
            gridWidth: /*[[${map.gridWidth}]]*/ 30,
            gridHeight: /*[[${map.gridHeight}]]*/ 20,
            cellSizePx: /*[[${map.cellSizePx}]]*/ 48,
            statusEl: document.getElementById('statusMessage'),
            saveIndicatorEl: document.getElementById('saveIndicator'),
            cursorInfoEl: document.getElementById('cursorInfo'),
        });
```

- [ ] **Step 7: Manual verification**

1. Reload the editor. Move the mouse over the canvas with Brush active. Expected: a translucent ghost of the current terrain color follows the cursor, snapped to the grid cell; increase brush size (Task 1, via console `window.mapEditor.setBrushSize(3)` if no UI control was added for it) and confirm the ghost covers a 3×3 footprint.
2. Switch to Select. Expected: cursor becomes a normal pointer; switch to Rect/Room/Bucket/etc. Expected: cursor becomes a crosshair.
3. Hold Space and drag. Expected: cursor shows a grab/grabbing hand while panning, then reverts to the active tool's cursor on release.
4. Watch the status bar's middle segment while moving the mouse. Expected: it shows `(col, row) · 100%`; scroll to zoom. Expected: the percentage updates even without moving the mouse.
5. Move the mouse off the canvas entirely. Expected: the ghost preview and coordinate readout both clear.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): brush hover preview, per-tool cursors, and a coordinate/zoom readout"
```

---

### Task 17: Disable Undo/Redo when their stacks are empty; add a keyboard-shortcuts help panel

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`
- Modify: `src/main/resources/templates/maps/editor.html`

**Interfaces:**
- Produces: `MapEditor.emitHistoryState()`; `map-historystate` window event (`{ canUndo: boolean, canRedo: boolean }`).

Undo/Redo are always clickable even with empty stacks (they just silently no-op), and every shortcut is discoverable only by hovering a `title` tooltip one button at a time.

- [ ] **Step 1: Emit undo/redo stack state**

Add this method right after `redo` (end of the "Undo / redo" section):

```js
    emitHistoryState() {
        this.emit('map-historystate', { canUndo: this.undoStack.length > 0, canRedo: this.redoStack.length > 0 });
    }
```

Find:

```js
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
```

Replace with:

```js
    pushUndo() {
        const snapshot = this.buildDocumentFromCanvas();
        if (!snapshot) return;
        this.undoStack.push(snapshot);
        if (this.undoStack.length > UNDO_MAX) this.undoStack.shift();
        this.redoStack = [];
        this.emitHistoryState();
    }

    undo() {
        if (!this.undoStack.length) return;
        const current = this.buildDocumentFromCanvas();
        if (current) this.redoStack.push(current);
        this.document = this.undoStack.pop();
        this.renderDocument();
        this.emitLayerState();
        this.markDirty();
        this.emitHistoryState();
    }

    redo() {
        if (!this.redoStack.length) return;
        const current = this.buildDocumentFromCanvas();
        if (current) this.undoStack.push(current);
        this.document = this.redoStack.pop();
        this.renderDocument();
        this.emitLayerState();
        this.markDirty();
        this.emitHistoryState();
    }
```

- [ ] **Step 2: Disable the Undo/Redo buttons and wire the shortcuts help panel**

In `editor.html`, find (as left by Task 12):

```html
            <div class="tool-group">
                <button class="tool-btn" @click="undo()" title="Undo (Ctrl+Z)">↩ Undo</button>
                <button class="tool-btn" @click="redo()" title="Redo (Ctrl+Y)">↪ Redo</button>
            </div>
```

Replace with:

```html
            <div class="tool-group">
                <button class="tool-btn" :disabled="!canUndo" @click="undo()" title="Undo (Ctrl+Z)">↩ Undo</button>
                <button class="tool-btn" :disabled="!canRedo" @click="redo()" title="Redo (Ctrl+Y)">↪ Redo</button>
            </div>
```

Add the Help group right before the toolbar closes. Find (this boundary was introduced by Task 8 and is still unique after Tasks 13–14's edits, which only changed content *inside* the preceding group, not this boundary):

```html
            </div>
        </div>

        <div class="editor-body">
```

Replace with:

```html
            </div>

            <div class="tool-group" style="border-right: none; position: relative;">
                <button class="tool-btn" @click="helpOpen = !helpOpen" title="Keyboard shortcuts">❓ Help</button>
                <div class="shortcuts-panel" x-show="helpOpen" style="display: none;" @click.outside="helpOpen = false">
                    <h3>Keyboard Shortcuts</h3>
                    <dl>
                        <dt>B</dt><dd>Brush (right-click erases)</dd>
                        <dt>T</dt><dd>Terrain Rect</dd>
                        <dt>G</dt><dd>Bucket fill</dd>
                        <dt>M</dt><dd>Room</dd>
                        <dt>D</dt><dd>Door</dd>
                        <dt>N</dt><dd>Region</dd>
                        <dt>R / C / L / P</dt><dd>Rect / Circle / Line / Polygon shape</dd>
                        <dt>V</dt><dd>Select</dd>
                        <dt>Space (hold)</dt><dd>Pan</dd>
                        <dt>Scroll</dt><dd>Zoom</dd>
                        <dt>Ctrl+Z / Ctrl+Y</dt><dd>Undo / Redo</dd>
                        <dt>Ctrl+C / X / V</dt><dd>Copy / Cut / Paste selection</dd>
                        <dt>Delete</dt><dd>Remove selected shape or selection</dd>
                        <dt>Enter</dt><dd>Close polygon</dd>
                        <dt>Esc</dt><dd>Cancel polygon / clear selection</dd>
                    </dl>
                </div>
            </div>
        </div>

        <div class="editor-body">
```

- [ ] **Step 3: Style the panel and disabled buttons**

In `editor.html`'s `<style>` block, add after `.editor-toolbar .tool-btn.active { ... }`:

```css
        .editor-toolbar .tool-btn:disabled {
            opacity: 0.4;
            cursor: not-allowed;
        }
        .shortcuts-panel {
            position: absolute;
            top: 100%;
            right: 0;
            z-index: 30;
            margin-top: 4px;
            width: 280px;
            background: var(--color-surface);
            border: 1px solid var(--color-border);
            border-radius: 6px;
            padding: var(--space-sm) var(--space-md);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
        }
        .shortcuts-panel h3 {
            margin: 0 0 var(--space-xs);
            font-size: var(--text-sm);
            text-transform: uppercase;
            color: var(--color-text-muted);
        }
        .shortcuts-panel dl {
            display: grid;
            grid-template-columns: auto 1fr;
            gap: 4px var(--space-sm);
            margin: 0;
            font-size: var(--text-sm);
        }
        .shortcuts-panel dt { font-weight: 600; }
        .shortcuts-panel dd { margin: 0; color: var(--color-text-muted); }
```

- [ ] **Step 4: Add `canUndo`/`canRedo`/`helpOpen` to the Alpine state**

Find (as left by Task 11):

```js
                selectedShape: null,
                conflict: false,
                init() {
```

Replace with:

```js
                selectedShape: null,
                conflict: false,
                canUndo: false,
                canRedo: false,
                helpOpen: false,
                init() {
```

Find (as left by Task 11, the last listener in `init()`):

```js
                    window.addEventListener('map-conflict', () => { this.conflict = true; });
                },
```

Replace with:

```js
                    window.addEventListener('map-conflict', () => { this.conflict = true; });
                    window.addEventListener('map-historystate', (e) => {
                        this.canUndo = e.detail.canUndo;
                        this.canRedo = e.detail.canRedo;
                    });
                },
```

- [ ] **Step 5: Manual verification**

1. Reload the editor. Expected: both Undo and Redo render dimmed/disabled (nothing to undo/redo yet).
2. Paint a cell. Expected: Undo becomes enabled; Redo stays disabled.
3. Click Undo. Expected: Redo becomes enabled; once the stack empties, Undo dims again.
4. Click "❓ Help". Expected: a panel lists every shortcut in two columns; click outside it — it closes.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js src/main/resources/templates/maps/editor.html
git commit -m "feat(map-editor): disable empty Undo/Redo and add a keyboard-shortcuts help panel"
```

---

### Task 18: Touch/tablet — two-finger pan and pinch-zoom

**Files:**
- Modify: `src/main/resources/static/js/map/map-editor.js`

**Interfaces:**
- Produces: `MapEditor.startPinch(touches)`, `MapEditor.updatePinch(touches)`, `MapEditor.pinchState(touches)`.

Single-touch already draws (the stage listens to `touchstart`/`touchmove`/`touchend` alongside the mouse events), but panning currently needs a middle mouse button or held Space, and zoom needs a scroll wheel — neither exists on a tablet, making the editor unusable there. Add two-finger pan+zoom, mirroring the wheel handler's "keep the point under the pointer fixed" zoom math but anchored on the pinch midpoint instead of the mouse position.

- [ ] **Step 1: Add pinch state and detect the start of a two-finger gesture**

In the constructor, find (as left by Task 2):

```js
        this.drawing = false;
        this.panning = false;
        this.erasing = false;
```

Replace with:

```js
        this.drawing = false;
        this.panning = false;
        this.erasing = false;
        this.pinchStart = null;   // {dist, scale, stagePointAtCenter} while a two-finger gesture is active
```

In `setupEvents`, find (as left by Task 5):

```js
        this.stage.on('mousedown touchstart', (e) => {
            if (e.target.getParent() instanceof Konva.Transformer) return;   // let the Transformer handle its own anchors
            if (e.evt.button === 1) {   // middle mouse: pan
```

Replace with:

```js
        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.touches && e.evt.touches.length === 2) {
                e.evt.preventDefault();
                this.startPinch(e.evt.touches);
                return;
            }
            if (e.target.getParent() instanceof Konva.Transformer) return;   // let the Transformer handle its own anchors
            if (e.evt.button === 1) {   // middle mouse: pan
```

- [ ] **Step 2: Implement the pinch math**

Add these methods right after the `wheel` handler's own section — i.e. right after `setupEvents` closes, before `cellPos`:

```js
    /** Distance and midpoint (in container-local pixels) between two active touches. */
    pinchState(touches) {
        const [t1, t2] = touches;
        const dist = Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY);
        const rect = this.container.getBoundingClientRect();
        const center = {
            x: (t1.clientX + t2.clientX) / 2 - rect.left,
            y: (t1.clientY + t2.clientY) / 2 - rect.top,
        };
        return { dist, center };
    }

    startPinch(touches) {
        this.drawing = false;
        this.marqueeStart = null;
        this.movingSelection = null;
        this.shapeStart = null;
        this.polygonPoints = [];
        this.clearPreview();
        const state = this.pinchState(touches);
        const scale = this.stage.scaleX();
        this.pinchStart = {
            dist: state.dist,
            scale,
            stagePointAtCenter: {
                x: (state.center.x - this.stage.x()) / scale,
                y: (state.center.y - this.stage.y()) / scale,
            },
        };
    }

    /** Rescales/repositions the stage so the point under the pinch midpoint at gesture
     *  start stays under the (moving) midpoint now — same technique as the wheel handler,
     *  anchored on the two-finger center instead of the mouse pointer. */
    updatePinch(touches) {
        if (!this.pinchStart) return;
        const state = this.pinchState(touches);
        const newScale = Math.max(0.2, Math.min(5, this.pinchStart.scale * (state.dist / this.pinchStart.dist)));
        this.stage.scale({ x: newScale, y: newScale });
        this.stage.position({
            x: state.center.x - this.pinchStart.stagePointAtCenter.x * newScale,
            y: state.center.y - this.pinchStart.stagePointAtCenter.y * newScale,
        });
        this.stage.batchDraw();
    }
```

- [ ] **Step 3: Route two-finger `touchmove` to the pinch handler, and end the gesture in `touchend`**

Find (as left by Task 16):

```js
        this.stage.on('mousemove touchmove', () => {
            if (this.panning) return;
            const pos = this.cellPos();
            if (!pos) return;
            this.updateHoverPreview(pos);
            this.updateCursorInfo(pos);

            if (this.activeTool === 'polygon' && this.polygonPoints.length) {
```

Replace with:

```js
        this.stage.on('mousemove touchmove', (e) => {
            if (this.pinchStart && e.evt.touches && e.evt.touches.length === 2) {
                e.evt.preventDefault();
                this.updatePinch(e.evt.touches);
                return;
            }
            if (this.panning) return;
            const pos = this.cellPos();
            if (!pos) return;
            this.updateHoverPreview(pos);
            this.updateCursorInfo(pos);

            if (this.activeTool === 'polygon' && this.polygonPoints.length) {
```

Find (its signature already takes `(e)`, per Task 3 Step 1):

```js
        this.stage.on('mouseup touchend', (e) => {
            if (this.panning) {
```

Replace with:

```js
        this.stage.on('mouseup touchend', (e) => {
            if (e.evt.touches && e.evt.touches.length < 2) this.pinchStart = null;
            if (this.panning) {
```

- [ ] **Step 4: Manual verification**

Chrome DevTools' device toolbar simulates multi-touch (hold Shift while dragging to get a second synthetic touch point), or test on an actual tablet/touchscreen.

1. Open the editor on a touch device (or DevTools touch simulation). Single-finger drag with Brush active. Expected: unchanged — it paints, same as before.
2. Two-finger drag. Expected: the canvas pans, tracking the midpoint of the two fingers; no painting happens.
3. Two-finger pinch (fingers moving apart/together). Expected: the canvas zooms in/out, and the content under the midpoint of your fingers stays roughly stationary (not flying to a corner) — same feel as the existing scroll-wheel zoom.
4. Lift one finger while the other stays down (going from 2 touches to 1). Expected: no stray paint/draw action fires from the remaining finger's position; pinch mode cleanly ends.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/map/map-editor.js
git commit -m "feat(map-editor): two-finger pan and pinch-zoom for touch devices"
```

---

## Self-Review Notes

Coverage check against every finding from the original review — each maps to exactly one task: shape select/move/resize/delete/label → Tasks 5–6; terrain rect-fill/bucket → Tasks 3–4; brush stroke gaps → Task 1; erase discoverability → Task 2; selection cut/delete/drag-move + paste-stacking → Task 7; data loss on navigation → Task 10; harsh 409 handling → Task 11; dead primitive code → Task 12; background image → Task 13; PNG export → Task 14; custom terrain add/delete (+edit via same-name re-add, Task 15 Step 7) and prompt()/confirm() replacement and color swatches → Task 15; brush hover preview + per-tool cursor + status-bar coords/zoom → Task 16; save indicator always green → Task 11; disabled Undo/Redo + shortcut help → Task 17; layers moved to sidebar → Task 8; pushUndo consistency (with the deliberate exception documented) → Task 9; paintCell linear-scan perf → Task 1; touch/tablet → Task 18. Nothing from the original review is unaddressed.

Three bugs were caught and fixed while drafting (all corrected in place above, not left as separate follow-up tasks): Task 3's `mouseup` handler referenced `e.evt.button` before its arrow function accepted an `e` parameter; Task 6's inline label editor called `renderDocument()` (which destroys and rebuilds every shape node) without first clearing `this.shapeSelection`/the Transformer, leaving a dangling reference to a destroyed node; and Task 11's status-bar button briefly had two conflicting `style` attributes on one tag.

Every task ends with its own manual verification against the running dev server — there is no JS test runner in this repo to automate against, so treat those steps as required, not optional, before moving to the next task.
