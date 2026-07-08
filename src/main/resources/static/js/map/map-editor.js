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
        this.cellIndex = {};   // layer id -> Map<"col,row", Konva.Rect>
        this.brushSize = 1;
        this.lastPaintCell = null;   // {col, row} — last painted cell, for stroke interpolation
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
        this.cellIndex[id] = new Map();
        return l;
    }

    renderDocument() {
        if (!this.document) return;

        for (const layerDto of this.document.layers) {
            const kl = this.layers[layerDto.id];
            if (!kl) continue;
            kl.destroyChildren();
            if (this.cellIndex[layerDto.id]) this.cellIndex[layerDto.id].clear();

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
            const index = this.cellIndex[konvaLayer.id()];
            if (index) index.set(`${cell.col},${cell.row}`, rect);
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
                    // open floor; nothing to paint (absent cells are floor).
                    // REGION with terrain can be used for visible corridor flooring.
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
                this.lastPaintCell = null;
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
                this.paintStrokeTo(pos.col, pos.row, false);
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
            this.lastPaintCell = null;

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
