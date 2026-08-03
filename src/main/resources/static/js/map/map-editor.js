import { BUILTIN_TERRAIN, DEFAULT_TERRAIN, ERASE_KEY, SHAPE_COLORS } from './terrain-palette.js';
import { floodFillCells } from './flood-fill.js';
import { drawGrid, cellPos, snapPt, expandPrimitives, mapPixelBounds, mapSelectionColor,
         setSaveStatus } from './shared.js';
import { boundsImpact, shapeBounds, fitInsideGeometry, fillCoverGeometry, calibratedImageGeometry } from './geometry.js';

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
const DRAW_TOOLS = ['brush', 'rect', 'circle', 'line', 'polygon', 'fill-rect', 'bucket', 'room', 'door', 'region'];

export class MapEditor {
    /**
     * @param {{container: HTMLElement, mapId: string, gridWidth: number, gridHeight: number,
     *          cellSizePx: number, statusEl?: HTMLElement, saveIndicatorEl?: HTMLElement}} opts
     */
    constructor({ container, mapId, gridWidth, gridHeight, cellSizePx, statusEl, saveIndicatorEl, cursorInfoEl }) {
        this.container = container;
        this.mapId = mapId;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.cellSizePx = cellSizePx;
        this.statusEl = statusEl;
        this.saveIndicatorEl = saveIndicatorEl;
        this.cursorInfoEl = cursorInfoEl;

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
        this.erasing = false;
        this.pinchStart = null;   // {dist, scale, stagePointAtCenter} while a two-finger gesture is active
        this.shapeStart = null;
        this.marqueeStart = null;
        this.polygonPoints = [];   // flat [x1, y1, ...] in cell units
        this.selection = null;     // {origin: {col, row}, cells: Cell[], shapes: Shape[]}
        this.clipboard = null;     // {cells: Cell[], shapes: Shape[]} normalized to (0,0)
        this.pasteOffset = null;   // {col, row} — accumulates so repeated Ctrl+V doesn't stack pastes
        this.movingSelection = null;   // {startX, startY} in cell units, while dragging a bulk selection
        this.shapeSelection = null;   // single Konva shape node currently selected for transform, or null
        this.imageSelection = null;    // single Konva Image node for the background layer, or null
        this.transformer = null;

        this.undoStack = [];
        this.redoStack = [];
        this.saveTimer = null;
        this.saveInFlight = null;
        this.dirty = false;

        this.stage = null;
        this.gridLayer = null;
        this.previewLayer = null;
        this.layers = {};   // layer id -> Konva.Layer
        this.cellIndex = {};   // layer id -> Map<"col,row", Konva.Rect>
        this.brushSize = 1;
        this.lastPaintCell = null;   // {col, row} — last painted cell, for stroke interpolation

        this.cropMode = false;
        this.cropStart = null;
        this.calibrateMode = false;
        this.calibratePointA = null;
        this.imageKeepAspect = true;
        this.tokenSnapshot = [];
        this.settingsInFlight = false;
    }

    load() {
        this.stage = new Konva.Stage({
            container: this.container,
            width: this.container.clientWidth,
            height: this.container.clientHeight,
            draggable: false,
        });

        this.createLayer('image');   // background reference image — added first so it renders behind the grid

        this.gridLayer = new Konva.Layer({ listening: false });
        this.stage.add(this.gridLayer);

        this.createLayer('terrain');
        this.createLayer('objects');
        this.createLayer('annotations');

        this.previewLayer = new Konva.Layer();
        this.stage.add(this.previewLayer);

        this.initTransformer();
        this.container.style.cursor = this.cursorForTool(this.activeTool);
        this.drawGrid();
        this.setupEvents();
        this.fetchDocument();
    }

    initTransformer() {
        this.transformer = new Konva.Transformer({
            rotateEnabled: false,
            enabledAnchors: ['top-left', 'top-center', 'top-right', 'middle-left', 'middle-right',
                              'bottom-left', 'bottom-center', 'bottom-right'],
            borderStroke: mapSelectionColor(),
            anchorStroke: mapSelectionColor(),
            anchorFill: '#fff',
            anchorSize: 8,
        });
        this.previewLayer.add(this.transformer);

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
    }

    /* ---- Grid ---- */

    drawGrid() {
        drawGrid(this.gridLayer, this.gridWidth, this.gridHeight, this.cellSizePx);
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
            terrains: Object.entries(this.palette).map(([key, t]) => ({ key, name: t.name, fill: t.fill, custom: !!t.custom })),
        });
    }

    /**
     * Adds a custom terrain entry (persisted in the map document).
     * @returns {string|null} the new terrain key, or null if invalid/duplicate
     */
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

    importBackgroundImage(dataUrl) {
        if (!this.document) return;
        const probe = new Image();
        probe.onload = () => {
            let w = probe.naturalWidth;
            let h = probe.naturalHeight;
            const maxDim = 8192;

            if (w > maxDim || h > maxDim) {
                const scale = w > h ? maxDim / w : maxDim / h;
                w = Math.round(w * scale);
                h = Math.round(h * scale);
            }

            const canvas = document.createElement('canvas');
            canvas.width = w;
            canvas.height = h;
            const ctx = canvas.getContext('2d');
            const alphaCapableSource = /^data:image\/(?:png|webp|gif|svg\+xml)/i.test(dataUrl);
            if (alphaCapableSource) {
                ctx.clearRect(0, 0, w, h);
            } else {
                ctx.fillStyle = '#fff';
                ctx.fillRect(0, 0, w, h);
            }
            ctx.drawImage(probe, 0, 0, w, h);
            const normalizedDataUrl = canvas.toDataURL(
                alphaCapableSource ? 'image/png' : 'image/jpeg', 0.9);

            const imageCellWidth = w / this.cellSizePx;
            const imageCellHeight = h / this.cellSizePx;
            const fit = fitInsideGeometry(this.gridWidth, this.gridHeight, imageCellWidth, imageCellHeight);

            this.pushUndo();
            this.syncDocument();
            let layerDto = this.layerDto('image');
            if (!layerDto) {
                layerDto = { id: 'image', name: 'Background', type: 'IMAGE', visible: true, locked: false, cells: [], shapes: [] };
                this.document.layers.push(layerDto);
            }
            layerDto.image = {
                dataUrl: normalizedDataUrl,
                x: this.round2(fit.x), y: this.round2(fit.y),
                width: this.round2(fit.width), height: this.round2(fit.height),
                rotationDeg: 0, locked: false,
            };
            this.renderDocument();
            this.emitLayerState();
            this.markDirty();
            this.setStatus(`Background image imported (${w}×${h}px) — Select tool to move/resize it`);
        };
        probe.onerror = () => {
            this.setStatus('Failed to load image — file may be corrupt or unsupported.');
        };
        probe.src = dataUrl;
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
        this.clearShapeSelection();
        this.clearImageSelection();
        this.activeTool = tool;
        this.container.style.cursor = this.cursorForTool(tool);
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

    setTerrain(key) {
        this.terrain = key === ERASE_KEY ? DEFAULT_TERRAIN : key;
    }

    setLayer(layerId) {
        this.cancelPolygon();
        this.clearSelection();
        this.clearShapeSelection();
        this.clearImageSelection();
        this.activeLayerId = layerId;
        if (layerId === 'image' && this.layerDto('image')?.image && !this.isImageLocked()) {
            this.setTool('select');
            const imageNode = this.layers.image?.findOne('Image');
            if (imageNode) this.selectImageForTransform(imageNode);
        }
    }

    setSnap(snap) {
        this.snap = !!snap;
    }

    // toggleLayerVisible/toggleLayerLocked intentionally do not pushUndo(): they're view/workflow
    // state, not map content, so Ctrl+Z shouldn't silently flip them while undoing a content edit.
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
            layers[l.id] = { visible: l.visible !== false, locked: !!l.locked, playerVisible: l.playerVisible !== false };
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
            if (layerDto.type === 'IMAGE' && layerDto.image) {
                this.addImageNode(kl, layerDto.image);
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
        this.emitImageState();
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
        // Imported/older map data can contain null or truncated shape records;
        // a single bad record must not take down the whole canvas.
        if (!shape || typeof shape !== 'object' || !Array.isArray(shape.points)) {
            console.warn('Skipping malformed shape record', shape);
            return null;
        }
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

    addImageNode(konvaLayer, imageDto) {
        const htmlImg = new Image();
        htmlImg.onload = () => {
            const bounds = mapPixelBounds(this.gridWidth, this.gridHeight, this.cellSizePx);
            const group = new Konva.Group({
                clip: { x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height },
            });
            konvaLayer.add(group);
            const node = new Konva.Image({
                image: htmlImg,
                x: imageDto.x * this.cellSizePx, y: imageDto.y * this.cellSizePx,
                width: imageDto.width * this.cellSizePx, height: imageDto.height * this.cellSizePx,
                rotation: imageDto.rotationDeg || 0,
                draggable: !imageDto.locked,
                listening: true,
            });
            node.setAttr('_imageLayer', true);
            group.add(node);
            konvaLayer.batchDraw();
        };
        htmlImg.onerror = () => {
            console.warn('Failed to decode background image — dataUrl may be corrupted');
        };
        htmlImg.src = imageDto.dataUrl;
    }

    /* ---- Semantic primitives (§4.3): expanded to cells on render, never serialized ---- */

    /** @returns {Cell[]} */
    expandPrimitives() {
        return expandPrimitives(this.document);
    }

    /* ---- Events ---- */

    setupEvents() {
        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.touches && e.evt.touches.length === 2) {
                e.evt.preventDefault();
                this.startPinch(e.evt.touches);
                return;
            }
            if (e.target instanceof Konva.Transformer || e.target.getParent() instanceof Konva.Transformer) return;   // let the Transformer handle its own anchors
            if (e.evt.button === 1) {   // middle mouse: pan
                this.panning = true;
                this.stage.draggable(true);
                this.container.style.cursor = 'grabbing';
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

            const pos = this.cellPos();
            if (!pos) return;

            if (this.cropMode) {
                const imgLayer = this.layerDto('image');
                if (!imgLayer?.image) { this.setStatus('No background image to crop'); return; }
                this.cropStart = { x: this.snapPt(pos.x), y: this.snapPt(pos.y) };
                this.drawing = true;
                return;
            }

            if (this.calibrateMode) {
                const imgLayer = this.layerDto('image');
                if (!imgLayer?.image) { this.setStatus('No background image to calibrate'); this.calibrateMode = false; return; }
                if (!this.calibratePointA) {
                    this.calibratePointA = { x: pos.x, y: pos.y };
                    this.setStatus('First calibration point set at (' + Math.round(pos.x) + ', ' + Math.round(pos.y) + ') — click a second point');
                } else {
                    this.finishCalibration(pos);
                }
                return;
            }

            if (this.activeTool === 'select') {
                const shapeNode = e.target && e.target.getAttr('_shape') ? e.target : null;
                if (shapeNode) {
                    this.selectShapeForTransform(shapeNode);
                    return;
                }
                const imageNode = e.target && e.target.getAttr('_imageLayer') ? e.target : null;
                if (imageNode) {
                    if (this.isImageLocked()) { this.setStatus('Image is locked'); return; }
                    this.selectImageForTransform(imageNode);
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

            if (DRAW_TOOLS.includes(this.activeTool) && this.isLocked(this.activeLayerId)) {
                this.setStatus('Layer is locked');
                return;
            }

            if (this.activeTool === 'brush') {
                this.erasing = false;
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
            } else if (this.activeTool === 'fill-rect') {
                if (this.isLocked(this.activeLayerId) || this.layerDto(this.activeLayerId)?.type !== 'TERRAIN') {
                    this.setStatus('Terrain Rect paints on the Terrain layer');
                    return;
                }
                this.drawing = true;
                this.shapeStart = { col: pos.col, row: pos.row };
            } else if (this.activeTool === 'bucket') {
                this.floodFillAt(pos.col, pos.row, e.evt.button === 2);
            } else if (this.activeTool === 'room' || this.activeTool === 'region') {
                this.drawing = true;
                this.shapeStart = { col: pos.col, row: pos.row };
            } else if (this.activeTool === 'door') {
                this.commitDoorPrimitive(pos.col, pos.row);
            }
        });

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

            if (this.cropMode && this.cropStart) {
                this.previewCellRect(
                    { col: Math.floor(this.cropStart.x), row: Math.floor(this.cropStart.y) },
                    { col: pos.col, row: pos.row }
                );
                return;
            }

            if (this.activeTool === 'polygon' && this.polygonPoints.length) {
                this.renderPolygonPreview(pos);
                return;
            }
            if (!this.drawing) return;

            if (this.activeTool === 'brush') {
                this.paintStrokeTo(pos.col, pos.row, !!this.erasing);
            } else if (this.activeTool === 'select' && this.movingSelection) {
                this.previewSelectionMove(pos);
            } else if (this.activeTool === 'select' && this.marqueeStart) {
                this.previewMarquee(this.marqueeStart, pos);
            } else if (['fill-rect', 'room', 'region'].includes(this.activeTool) && this.shapeStart) {
                this.previewCellRect(this.shapeStart, { col: pos.col, row: pos.row });
            } else if (this.shapeStart) {
                this.previewShape(this.shapeStart, { x: this.snapPt(pos.x), y: this.snapPt(pos.y) });
            }
        });

        this.stage.on('mouseup touchend', (e) => {
            if (e.evt.touches && e.evt.touches.length < 2) this.pinchStart = null;
            if (this.panning) {
                this.panning = false;
                this.stage.draggable(false);
                this.container.style.cursor = this.cursorForTool(this.activeTool);
                return;
            }

            const pos = this.cellPos();

            if (this.cropMode && this.cropStart && pos) {
                this.confirmCrop(pos);
                this.cropStart = null;
                this.drawing = false;
                return;
            }

            if (!this.drawing) return;
            this.drawing = false;
            this.lastPaintCell = null;
            this.erasing = false;
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
            if (this.activeTool === 'fill-rect' && this.shapeStart && pos) {
                this.commitCellRect(this.shapeStart, { col: pos.col, row: pos.row }, e.evt.button === 2);
            } else if (this.activeTool === 'room' && this.shapeStart && pos) {
                this.commitRoomPrimitive(this.shapeStart, { col: pos.col, row: pos.row });
            } else if (this.activeTool === 'region' && this.shapeStart && pos) {
                this.commitRegionPrimitive(this.shapeStart, { col: pos.col, row: pos.row });
            } else if (this.shapeStart && pos) {
                this.commitShape(this.shapeStart, { x: this.snapPt(pos.x), y: this.snapPt(pos.y) });
            }
            this.clearPreview();
            this.shapeStart = null;
        });

        this.stage.on('dblclick dbltap', (e) => {
            if (this.activeTool === 'polygon') { this.commitPolygon(); return; }
            if (this.activeTool === 'select') {
                const shapeNode = e.target && e.target.getAttr('_shape') ? e.target : null;
                if (shapeNode) this.editShapeLabel(shapeNode);
            }
        });

        this.stage.on('contextmenu', (e) => { e.evt.preventDefault(); });

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
            const p = this.cellPos();
            if (p) this.updateCursorInfo(p);
        });

        this.stage.on('mouseleave', () => {
            this.clearHoverPreview();
            this.previewLayer.batchDraw();
            if (this.cursorInfoEl) this.cursorInfoEl.textContent = '';
        });

        // Keyboard shortcuts (§4.3)
        window.addEventListener('keydown', (e) => {
            if (this.settingsInFlight) return;
            if (e.target.closest?.('input, select, textarea, [contenteditable]')) return;

            if (e.code === 'Space') {
                if (!this.drawing) {
                    e.preventDefault();
                    this.stage.draggable(true);
                    this.container.style.cursor = 'grab';
                }
                return;
            }

            if (e.ctrlKey || e.metaKey) {
                const k = e.key.toLowerCase();
                if (k === 'z') { e.preventDefault(); e.shiftKey ? this.redo() : this.undo(); }
                else if (k === 'y') { e.preventDefault(); this.redo(); }
                else if (k === 'c' && this.selection) { e.preventDefault(); this.copySelection(); }
                else if (k === 'x' && this.selection) { e.preventDefault(); this.cutSelection(); }
                else if (k === 'v' && this.clipboard) { e.preventDefault(); this.pasteClipboard(); }
                return;
            }

            switch (e.key.toLowerCase()) {
                case 'b': this.setTool('brush'); break;
                case 't': this.setTool('fill-rect'); break;
                case 'g': this.setTool('bucket'); break;
                case 'm': this.setTool('room'); break;
                case 'd': this.setTool('door'); break;
                case 'n': this.setTool('region'); break;
                case 'r': this.setTool('rect'); break;
                case 'c': this.setTool('circle'); break;
                case 'l': this.setTool('line'); break;
                case 'p': this.setTool('polygon'); break;
                case 'v': this.setTool('select'); break;
                case 'enter': if (this.polygonPoints.length) this.commitPolygon(); break;
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
                case 'escape':
                    this.cancelPolygon();
                    this.clearSelection();
                    this.clearShapeSelection();
                    this.clearImageSelection();
                    if (this.cropMode) { this.cancelCrop(); }
                    if (this.calibrateMode) { this.cancelCalibrate(); }
                    break;
            }
        });
        window.addEventListener('keyup', (e) => {
            if (e.code === 'Space' && !this.panning) {
                this.stage.draggable(false);
                this.container.style.cursor = this.cursorForTool(this.activeTool);
            }
        });

        window.addEventListener('beforeunload', (e) => {
            if (this.settingsInFlight) {
                e.preventDefault();
                e.returnValue = '';
                return;
            }
            if (!this.dirty) return;
            this.flushSave();
            e.preventDefault();
            e.returnValue = '';
        });
    }

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

    /** Pointer position in cell units, correct under pan/zoom. */
    cellPos() {
        return cellPos(this.stage, this.cellSizePx);
    }

    /** Snap-to-grid with unsnapped option (§4.3): whole cells when on, 1/20 cell when off. */
    snapPt(v) {
        return snapPt(v, this.snap);
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

        const key = prompt('Region key (a-z, 0-9, ., _, -, 1-100 chars):', '')?.trim();
        if (!key) { this.setStatus('Region creation cancelled'); return; }
        if (!/^[a-z0-9][a-z0-9._-]{0,99}$/.test(key)) {
            this.setStatus('Invalid key — must start with a-z/0-9, 1-100 chars');
            return;
        }
        const label = prompt('Region label (display name):', '')?.trim() || '';

        this.pushUndo();
        this.syncDocument();
        this.document.primitives = this.document.primitives || [];
        this.document.primitives.push({
            type: 'REGION', startCol: a.col, startRow: a.row, endCol: b.col, endRow: b.row,
            terrain: this.terrain, key, label, playerVisible: true,
        });
        this.renderDocument();
        this.markDirty();
        this.setStatus('Region "' + label + '" created');
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
            stroke: mapSelectionColor(), strokeWidth: 1.5, dash: [6, 4], listening: false,
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
            bounds,
            cells: structuredClone(cells),
            shapes: structuredClone(shapes),
        };

        this.drawSelectionMarker();
        this.setStatus(`${cells.length} cell(s), ${shapes.length} shape(s) selected — Ctrl+C copy, Ctrl+X cut, Delete remove, drag to move`);
    }

    drawSelectionMarker() {
        const b = this.selection.bounds;
        const px = (v) => v * this.cellSizePx;
        const rect = new Konva.Rect({
            x: px(b.minX), y: px(b.minY), width: px(b.maxX - b.minX), height: px(b.maxY - b.minY),
            stroke: mapSelectionColor(), strokeWidth: 1.5, dash: [6, 4], listening: false,
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
            stroke: mapSelectionColor(), strokeWidth: 1.5, dash: [6, 4], listening: false,
        });
        rect.setAttr('_selection', true);
        this.previewLayer.add(rect);
        this.previewLayer.batchDraw();
    }

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
        this.pasteOffset = { col: 1, row: 1 };
        this.setStatus('Copied — Ctrl+V to paste (offset by one cell)');
    }

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

        if (!this.pasteOffset) this.pasteOffset = { col: 1, row: 1 };
        const off = this.pasteOffset;
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
        this.pasteOffset = { col: off.col + 1, row: off.row + 1 };
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
            if (child !== this.transformer && !child.getAttr('_selection')) child.destroy();
        }
        this.previewLayer.batchDraw();
    }

    clearSelection() {
        this.selection = null;
        this.movingSelection = null;
        this.pasteOffset = null;
        for (const child of [...this.previewLayer.getChildren()]) {
            if (child.getAttr('_selection')) child.destroy();
        }
        this.previewLayer.batchDraw();
    }

    /* ---- Shape selection & transform (click-select, move, resize, delete) ---- */

    selectShapeForTransform(node) {
        this.clearSelection();
        this.cancelPolygon();
        this.clearShapeSelection();
        this.clearImageSelection();
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
        this.emit('map-shapeselect', { shape: { ...node.getAttr('_shape') } });
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
        this.emit('map-shapeselect', { shape: null });
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

    selectImageForTransform(node) {
        if (this.isImageLocked()) { this.setStatus('Image is locked'); return; }
        this.clearShapeSelection();
        this.clearSelection();
        this.cancelPolygon();
        this.imageSelection = node;
        node.draggable(true);
        node.on('dragend.imageselect', () => this.commitImageTransform(node));
        this.transformer.keepRatio(this.imageKeepAspect);
        this.transformer.nodes([node]);
        this.transformer.getLayer().batchDraw();
        this.setStatus('Background image selected — drag to move, handles to resize');
        this.emitImageState();
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
        this.emitImageState();
    }

    commitImageTransform(node) {
        const cs = this.cellSizePx;
        this.updateImageGeometry({
            x: this.round2(node.x() / cs),
            y: this.round2(node.y() / cs),
            width: this.round2((node.width() * node.scaleX()) / cs),
            height: this.round2((node.height() * node.scaleY()) / cs),
            rotationDeg: node.rotation(),
        });
        this.clearImageSelection();
    }

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

    deleteShapeSelection() {
        if (!this.shapeSelection) return;
        this.pushUndo();
        const layer = this.shapeSelection.getLayer();
        this.shapeSelection.destroy();
        this.clearShapeSelection();
        layer.batchDraw();
        this.markDirty();
    }

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

    /* ---- Undo / redo ---- */

    pushUndo(tokenRestoreIds = null) {
        const doc = this.buildDocumentFromCanvas();
        if (!doc) return;
        this.undoStack.push(this.historySnapshot(doc, tokenRestoreIds));
        if (this.undoStack.length > UNDO_MAX) this.undoStack.shift();
        this.redoStack = [];
        this.emitHistoryState();
    }

    async undo() {
        if (!this.undoStack.length) return;
        const snapshot = this.undoStack.pop();
        const current = this.buildDocumentFromCanvas();
        if (current) this.redoStack.push(this.historySnapshot(current, snapshot.tokenRestoreIds));
        if (this.snapshotHasDifferentGrid(snapshot)) {
            try {
                await this.restoreSettingsSnapshot(snapshot);
                const redoSnapshot = this.redoStack.at(-1);
                if (redoSnapshot?.tokenRestoreIds) {
                    redoSnapshot.expectedTokenSnapshot =
                        this.tokenHistorySnapshot(redoSnapshot.tokenRestoreIds);
                }
                this.emitHistoryState();
                return;
            } catch (error) {
                this.redoStack.pop();
                this.undoStack.push(snapshot);
                this.emitHistoryState();
                throw error;
            }
        }
        this.document = snapshot.document;
        this.gridWidth = snapshot.gridWidth;
        this.gridHeight = snapshot.gridHeight;
        this.cellSizePx = snapshot.cellSizePx;
        this.drawGrid();
        this.renderDocument();
        this.emitLayerState();
        this.markDirty();
        this.emitHistoryState();
    }

    async redo() {
        if (!this.redoStack.length) return;
        const snapshot = this.redoStack.pop();
        const current = this.buildDocumentFromCanvas();
        if (current) this.undoStack.push(this.historySnapshot(current, snapshot.tokenRestoreIds));
        if (this.snapshotHasDifferentGrid(snapshot)) {
            try {
                await this.restoreSettingsSnapshot(snapshot);
                const undoSnapshot = this.undoStack.at(-1);
                if (undoSnapshot?.tokenRestoreIds) {
                    undoSnapshot.expectedTokenSnapshot =
                        this.tokenHistorySnapshot(undoSnapshot.tokenRestoreIds);
                }
                this.emitHistoryState();
                return;
            } catch (error) {
                this.undoStack.pop();
                this.redoStack.push(snapshot);
                this.emitHistoryState();
                throw error;
            }
        }
        this.document = snapshot.document;
        this.gridWidth = snapshot.gridWidth;
        this.gridHeight = snapshot.gridHeight;
        this.cellSizePx = snapshot.cellSizePx;
        this.drawGrid();
        this.renderDocument();
        this.emitLayerState();
        this.markDirty();
        this.emitHistoryState();
    }

    emitHistoryState() {
        this.emit('map-historystate', { canUndo: this.undoStack.length > 0, canRedo: this.redoStack.length > 0 });
    }

    snapshotHasDifferentGrid(snapshot) {
        return snapshot.gridWidth !== this.gridWidth
            || snapshot.gridHeight !== this.gridHeight
            || snapshot.cellSizePx !== this.cellSizePx;
    }

    historySnapshot(document, tokenRestoreIds = null) {
        return {
            document,
            gridWidth: this.gridWidth,
            gridHeight: this.gridHeight,
            cellSizePx: this.cellSizePx,
            tokenRestoreIds: tokenRestoreIds ? [...tokenRestoreIds] : null,
            tokenSnapshot: tokenRestoreIds ? this.tokenHistorySnapshot(tokenRestoreIds) : null,
            expectedTokenSnapshot: null,
        };
    }

    tokenHistorySnapshot(tokenRestoreIds = this.tokenSnapshot.map(token => token.id)) {
        const restoreIds = new Set(tokenRestoreIds);
        return this.tokenSnapshot.filter(token => restoreIds.has(token.id)).map(token => ({
            id: token.id,
            name: token.name,
            kind: token.kind,
            positionX: token.positionX,
            positionY: token.positionY,
            sizeCols: token.sizeCols,
            sizeRows: token.sizeRows,
            color: token.color,
            hidden: token.hidden,
            statBlockId: token.statBlockId,
            partyMemberId: token.partyMemberId,
            notes: token.notes,
            icon: token.icon,
        }));
    }

    emitImageState() {
        const img = this.layerDto('image')?.image;
        if (!img) {
            this.emit('map-image-state', { present: false, selected: false, locked: false, x: 0, y: 0, width: 0, height: 0, rotationDeg: 0, aspectRatio: 1 });
            return;
        }
        const aspectRatio = img.width > 0 && img.height > 0 ? img.width / img.height : 1;
        this.emit('map-image-state', {
            present: true,
            selected: !!this.imageSelection,
            locked: !!img.locked,
            x: img.x, y: img.y, width: img.width, height: img.height,
            rotationDeg: img.rotationDeg || 0,
            aspectRatio,
        });
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

    setSaveState(state) {
        setSaveStatus(this.saveIndicatorEl, state);
    }

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

    exportPng() {
        if (!this.stage) return;
        this.clearShapeSelection();
        this.clearImageSelection();
        this.previewLayer.visible(false);   // hide selection/drag preview overlay from the export
        const bounds = mapPixelBounds(this.gridWidth, this.gridHeight, this.cellSizePx);
        const dataUrl = this.stage.toDataURL({ ...bounds, pixelRatio: 2 });
        this.previewLayer.visible(true);
        this.stage.batchDraw();
        this.triggerDownload(dataUrl, `map-${this.mapId}.png`);
    }

    /* ---- Image features: rotate, crop, calibrate, lock ---- */

    isImageLocked() {
        const l = this.layerDto('image');
        return !!(l?.image?.locked);
    }

    rotateImage(deg) {
        const layerDto = this.layerDto('image');
        if (!layerDto?.image) { this.setStatus('No background image'); return; }
        this.updateImageGeometry({ rotationDeg: (layerDto.image.rotationDeg || 0) + deg });
        this.setStatus('Image rotated ' + deg + '°');
    }

    startCrop() {
        if (!this.layerDto('image')?.image) { this.setStatus('No background image to crop'); return; }
        this.cropMode = true;
        this.clearShapeSelection();
        this.clearImageSelection();
        this.setStatus('Crop mode: draw a rectangle on the image, then confirm');
    }

    cancelCrop() {
        this.cropMode = false;
        this.cropStart = null;
        this.clearPreview();
        this.setStatus('Crop cancelled');
    }

    confirmCrop(endPos) {
        const a = this.cropStart;
        const b = { x: this.snapPt(endPos.x), y: this.snapPt(endPos.y) };
        this.clearPreview();

        if (Math.abs(b.x - a.x) < 0.5 || Math.abs(b.y - a.y) < 0.5) {
            this.setStatus('Crop rectangle too small');
            this.cropMode = false;
            this.cropStart = null;
            return;
        }

        const minX = Math.min(a.x, b.x), minY = Math.min(a.y, b.y);
        const maxX = Math.max(a.x, b.x), maxY = Math.max(a.y, b.y);

        const layerDto = this.layerDto('image');
        if (!layerDto?.image) { this.cropMode = false; this.cropStart = null; return; }
        const img = layerDto.image;

        const kl = this.layers['image'];
        const konvaImageNode = kl?.findOne('Image');
        if (!konvaImageNode) { this.cropMode = false; this.cropStart = null; return; }
        const htmlImg = konvaImageNode.image();

        const natW = htmlImg.naturalWidth || htmlImg.width;
        const natH = htmlImg.naturalHeight || htmlImg.height;

        const relX = minX - img.x, relY = minY - img.y;
        const relW = maxX - minX, relH = maxY - minY;

        if (relX < -0.01 || relY < -0.01 || relX + relW > img.width + 0.01 || relY + relH > img.height + 0.01) {
            this.setStatus('Crop rectangle must be within image bounds');
            return;
        }

        const srcX = Math.max(0, (relX / img.width) * natW);
        const srcY = Math.max(0, (relY / img.height) * natH);
        const srcW = Math.min(natW - srcX, (relW / img.width) * natW);
        const srcH = Math.min(natH - srcY, (relH / img.height) * natH);

        if (srcW < 1 || srcH < 1) {
            this.setStatus('Crop area too small');
            return;
        }

        const canvas = document.createElement('canvas');
        canvas.width = srcW;
        canvas.height = srcH;
        const ctx = canvas.getContext('2d');
        const currentDataUrl = img.dataUrl;
        const cropHasTransparency = currentDataUrl.startsWith('data:image/png') || currentDataUrl.startsWith('data:image/webp');
        if (!cropHasTransparency) {
            ctx.fillStyle = '#fff';
            ctx.fillRect(0, 0, srcW, srcH);
        }
        ctx.drawImage(htmlImg, srcX, srcY, srcW, srcH, 0, 0, srcW, srcH);
        const cropFormat = cropHasTransparency ? 'image/png' : 'image/jpeg';
        const newDataUrl = canvas.toDataURL(cropFormat, 0.92);

        this.updateImageGeometry({
            dataUrl: newDataUrl,
            x: this.round2(minX),
            y: this.round2(minY),
            width: this.round2(relW),
            height: this.round2(relH),
        });
        this.cropMode = false;
        this.cropStart = null;
        this.setStatus('Image cropped');
    }

    startCalibration() {
        if (!this.layerDto('image')?.image) { this.setStatus('No background image to calibrate'); return; }
        this.calibrateMode = true;
        this.calibratePointA = null;
        this.clearShapeSelection();
        this.clearImageSelection();
        this.setStatus('Calibration: click a point on the image (e.g. left edge of a known-distance feature), then click another');
    }

    cancelCalibrate() {
        this.calibrateMode = false;
        this.calibratePointA = null;
        this.setStatus('Calibration cancelled');
    }

    finishCalibration(pos) {
        const a = this.calibratePointA;
        const b = { x: pos.x, y: pos.y };

        if (Math.hypot(b.x - a.x, b.y - a.y) < 0.5) {
            this.setStatus('Points too close — calibration cancelled');
            this.calibrateMode = false;
            this.calibratePointA = null;
            return;
        }

        const cellsBetween = parseInt(prompt('How many cells between these points?', '1'), 10);
        if (!cellsBetween || cellsBetween < 1) {
            this.setStatus('Calibration cancelled');
            this.calibrateMode = false;
            this.calibratePointA = null;
            return;
        }

        this.calibrateBackgroundImage(a.x, a.y, b.x, b.y, cellsBetween);
        this.calibrateMode = false;
        this.calibratePointA = null;
    }



    setImageLocked(locked) {
        const layerDto = this.layerDto('image');
        if (!layerDto?.image) { this.setStatus('No background image'); return; }
        this.updateImageGeometry({ locked: !!locked });
        this.setStatus(locked ? 'Image locked' : 'Image unlocked');
    }

    /* ---- Player visible controls ---- */

    toggleLayerPlayerVisible(id) {
        const l = this.layerDto(id);
        if (!l) return;
        l.playerVisible = l.playerVisible === false ? true : false;
        this.markDirty();
    }

    setLayerPlayerVisible(id, visible) {
        const l = this.layerDto(id);
        if (!l) return;
        l.playerVisible = !!visible;
        this.markDirty();
    }

    markDirty() {
        this.dirty = true;
        this.setSaveState('unsaved');
        clearTimeout(this.saveTimer);
        this.saveTimer = setTimeout(() => this.save(), SAVE_DEBOUNCE_MS);
    }

    async save() {
        if (this.saveInFlight) return this.saveInFlight;
        if (this.settingsInFlight) return false;
        if (!this.dirty) return true;

        this.saveInFlight = this.performDocumentSave();
        try {
            return await this.saveInFlight;
        } finally {
            this.saveInFlight = null;
        }
    }

    async performDocumentSave() {
        this.dirty = false;

        const doc = this.buildDocumentFromCanvas();
        if (!doc) return false;
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
                return false;
            }
            if (!res.ok) throw new Error('Save failed: ' + res.status);
            const data = await res.json();
            this.docVersion = data.version;
            this.setSaveState('saved');
            return true;
        } catch (err) {
            console.error('Autosave failed:', err);
            this.setSaveState('error');
            this.setStatus('Save error — retrying');
            this.dirty = true;
            clearTimeout(this.saveTimer);
            this.saveTimer = setTimeout(() => this.save(), SAVE_DEBOUNCE_MS);
            return false;
        }
    }

    /** Best-effort synchronous-ish save for page unload — `keepalive: true` lets the
     *  request outlive navigation. Note: keepalive requests are capped around 64KB in
     *  Chrome, so this is a safety net for the gap since the last debounced autosave,
     *  not a replacement for it — very large maps may still exceed the cap. */
    flushSave() {
        if (this.settingsInFlight || !this.dirty || !this.document) return;
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

    /* ---- Load ---- */

    async fetchDocument() {
        try {
            const res = await fetch(`/api/v1/maps/${this.mapId}/document`);
            if (!res.ok) throw new Error('Fetch failed: ' + res.status);
            const data = await res.json();
            this.docVersion = data.version;
            this.document = data.document;
            if (data.document?.grid) {
                this.gridWidth = data.document.grid.width;
                this.gridHeight = data.document.grid.height;
                this.cellSizePx = data.document.grid.cellSizePx;
            }
            this.rebuildPalette();
            this.renderDocument();
            this.emitLayerState();
            this.emitImageState();
            this.emit('map-gridstate', {
                gridWidth: this.gridWidth,
                gridHeight: this.gridHeight,
                cellSizePx: this.cellSizePx,
            });
            await this.fetchTokens();
            this.setStatus('Ready');
        } catch (err) {
            console.error('Failed to load map document:', err);
            this.setStatus('Failed to load map');
        }
    }

    async fetchTokens() {
        const res = await fetch(`/api/v1/maps/${this.mapId}/tokens`);
        if (!res.ok) throw new Error('Token fetch failed: ' + res.status);
        this.tokenSnapshot = await res.json();
        return this.tokenSnapshot;
    }

    /* ---- Grid settings (authoritative resize, apply, and preview) ---- */

    previewGridResize(width, height, cellSizePx = this.cellSizePx) {
        const impact = boundsImpact(this.document, width, height);
        const maxX = width * cellSizePx;
        const maxY = height * cellSizePx;
        const scale = cellSizePx / this.cellSizePx;
        const affectedTokens = this.tokenSnapshot
            .map(token => ({
                ...token,
                projectedX: Math.round(token.positionX * scale),
                projectedY: Math.round(token.positionY * scale),
            }))
            .filter(token => token.projectedX < 0 || token.projectedY < 0
                || token.projectedX + token.sizeCols * cellSizePx > maxX
                || token.projectedY + token.sizeRows * cellSizePx > maxY)
            .map(token => ({
                ...token,
                edgeX: Math.max(0, Math.min(token.projectedX, maxX - token.sizeCols * cellSizePx)),
                edgeY: Math.max(0, Math.min(token.projectedY, maxY - token.sizeRows * cellSizePx)),
            }));
        return { ...impact, affectedTokens };
    }

    async applyGridSettings({ width, height, cellSizePx, resizeMode, tokenResolutions, shapeRemovals }) {
        if (this.settingsInFlight) throw new Error('A settings save is already in progress');
        if (this.saveInFlight) {
            const saved = await this.saveInFlight;
            if (!saved) throw new Error('Cannot apply grid settings while the map has an unresolved save conflict');
        }
        this.syncDocument();
        const snapshot = {
            document: structuredClone(this.document),
            gridWidth: this.gridWidth,
            gridHeight: this.gridHeight,
            cellSizePx: this.cellSizePx,
            tokenSnapshot: this.tokenHistorySnapshot(),
            dirty: this.dirty,
        };

        if (resizeMode === 'PRESERVE') {
            const impact = boundsImpact(this.document, width, height);
            if (impact.outsideCells.length > 0 || impact.affectedShapes.length > 0
                    || impact.affectedPrimitives.length > 0) {
                throw new Error('Content would be outside the new grid boundary');
            }
        }

        clearTimeout(this.saveTimer);
        const tokenRestoreIds = cellSizePx !== this.cellSizePx
            ? this.tokenSnapshot.map(token => token.id)
            : (tokenResolutions || []).map(resolution => resolution.tokenId);
        this.pushUndo(tokenRestoreIds);
        this.settingsInFlight = true;
        this.setSettingsInteractionLocked(true);
        this.setSaveState('saving');
        try {
            await this.sendSettingsRequest({
                width, height, cellSizePx, resizeMode,
                tokenResolutions: tokenResolutions || [],
                shapeRemovals: shapeRemovals || [],
                document: snapshot.document,
            });
            const history = this.undoStack.at(-1);
            if (history?.tokenRestoreIds) {
                history.expectedTokenSnapshot = this.tokenHistorySnapshot(history.tokenRestoreIds);
            }
            this.dirty = false;
        } catch (err) {
            console.error('Grid settings save failed:', err);
            this.gridWidth = snapshot.gridWidth;
            this.gridHeight = snapshot.gridHeight;
            this.cellSizePx = snapshot.cellSizePx;
            this.document = snapshot.document;
            this.drawGrid();
            this.renderDocument();
            this.undoStack.pop();
            this.emitHistoryState();
            this.dirty = snapshot.dirty;
            if (!err.conflict) {
                this.setSaveState('error');
                this.setStatus('Failed to apply grid settings');
            }
            throw err;
        } finally {
            this.settingsInFlight = false;
            this.setSettingsInteractionLocked(false);
        }
    }

    async restoreSettingsSnapshot(snapshot) {
        clearTimeout(this.saveTimer);
        this.settingsInFlight = true;
        this.setSettingsInteractionLocked(true);
        this.setSaveState('saving');
        try {
            await this.sendSettingsRequest({
                width: snapshot.gridWidth,
                height: snapshot.gridHeight,
                cellSizePx: snapshot.cellSizePx,
                resizeMode: 'PRESERVE',
                tokenResolutions: [],
                shapeRemovals: [],
                tokenRestoreIds: snapshot.tokenRestoreIds || [],
                tokenSnapshot: snapshot.tokenSnapshot || [],
                expectedTokenSnapshot: snapshot.expectedTokenSnapshot || [],
                document: snapshot.document,
            });
            this.dirty = false;
        } finally {
            this.settingsInFlight = false;
            this.setSettingsInteractionLocked(false);
        }
    }

    setSettingsInteractionLocked(locked) {
        const root = this.container.closest('.editor-container');
        if (root) root.inert = !!locked;
        if (this.stage) this.stage.listening(!locked);
    }

    async sendSettingsRequest({
        width, height, cellSizePx, resizeMode, tokenResolutions, shapeRemovals,
        tokenRestoreIds, tokenSnapshot, expectedTokenSnapshot, document,
    }) {
        const res = await fetch(`/api/v1/maps/${this.mapId}/settings`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                expectedVersion: this.docVersion,
                gridWidth: width,
                gridHeight: height,
                cellSizePx,
                resizeMode,
                tokenResolutions,
                shapeRemovals,
                tokenRestoreIds,
                tokenSnapshot,
                expectedTokenSnapshot,
                document,
            }),
        });
        if (res.status === 409) {
            this.setSaveState('conflict');
            this.setStatus('Map was changed elsewhere — download a backup below, then reload to continue');
            this.emit('map-conflict', {});
            const error = new Error('Settings save failed: 409');
            error.conflict = true;
            throw error;
        }
        if (!res.ok) throw new Error('Settings save failed: ' + res.status);
        const data = await res.json();
        this.docVersion = data.version;
        this.gridWidth = width;
        this.gridHeight = height;
        this.cellSizePx = cellSizePx;
        this.document = data.document;
        this.drawGrid();
        this.renderDocument();
        this.emitLayerState();
        await this.fetchTokens();
        this.emit('map-gridstate', { gridWidth: width, gridHeight: height, cellSizePx });
        this.setSaveState('saved');
    }

    updateImageGeometry(patch) {
        if (!this.layerDto('image')?.image) return;
        this.pushUndo();
        this.syncDocument();
        const layerDto = this.layerDto('image');
        Object.assign(layerDto.image, patch);
        this.renderDocument();
        this.markDirty();
    }

    updateImageField(field, value, keepAspect = this.imageKeepAspect) {
        const layerDto = this.layerDto('image');
        if (!layerDto?.image) return;
        const num = parseFloat(value);
        if (!isFinite(num)) return;
        const img = layerDto.image;
        if (field === 'width' || field === 'height') {
            if (num <= 0) return;
            if (keepAspect && img.width > 0 && img.height > 0) {
                const aspect = img.width / img.height;
                if (field === 'width') {
                    this.updateImageGeometry({ width: this.round2(num), height: this.round2(num / aspect) });
                } else {
                    this.updateImageGeometry({ width: this.round2(num * aspect), height: this.round2(num) });
                }
            } else {
                this.updateImageGeometry({ [field]: this.round2(num) });
            }
        } else if (field === 'x' || field === 'y') {
            this.updateImageGeometry({ [field]: this.round2(num) });
        } else if (field === 'rotationDeg') {
            this.updateImageGeometry({ [field]: num });
        }
    }

    setImageAspectLocked(locked) {
        this.imageKeepAspect = !!locked;
        if (this.transformer) this.transformer.keepRatio(this.imageKeepAspect);
    }

    fitBackgroundImage(mode) {
        const layerDto = this.layerDto('image');
        if (!layerDto?.image) return;
        const img = layerDto.image;
        const fn = mode === 'FIT_INSIDE' ? fitInsideGeometry : fillCoverGeometry;
        const result = fn(this.gridWidth, this.gridHeight, img.width, img.height);
        this.updateImageGeometry({
            x: this.round2(result.x),
            y: this.round2(result.y),
            width: this.round2(result.width),
            height: this.round2(result.height),
        });
    }

    async resetBackgroundImage() {
        const imageDto = this.layerDto('image')?.image;
        if (!imageDto) return;
        const dimensions = await this.decodeImageDimensions(imageDto.dataUrl);
        const result = fitInsideGeometry(
            this.gridWidth, this.gridHeight, dimensions.width, dimensions.height);
        this.updateImageGeometry({
            x: this.round2(result.x),
            y: this.round2(result.y),
            width: this.round2(result.width),
            height: this.round2(result.height),
            rotationDeg: 0,
        });
    }

    calibrateBackgroundImage(ax, ay, bx, by, cellsBetween) {
        const layerDto = this.layerDto('image');
        if (!layerDto?.image) return;
        const result = calibratedImageGeometry(
            layerDto.image,
            { x: ax, y: ay },
            { x: bx, y: by },
            cellsBetween,
            this.cellSizePx,
        );
        this.updateImageGeometry({
            x: this.round2(result.x),
            y: this.round2(result.y),
            width: this.round2(result.width),
            height: this.round2(result.height),
            calibration: {
                ax, ay, bx, by, cellsBetween,
                offsetXPx: this.round2(result.x * this.cellSizePx),
                offsetYPx: this.round2(result.y * this.cellSizePx),
            },
        });
    }

    decodeImageDimensions(dataUrl) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.onload = () => resolve({
                width: image.naturalWidth || image.width,
                height: image.naturalHeight || image.height,
            });
            image.onerror = () => reject(new Error('Failed to decode background image'));
            image.src = dataUrl;
        });
    }

    /* ---- Misc ---- */

    setStatus(msg) {
        if (this.statusEl) this.statusEl.textContent = msg;
    }

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

    emit(name, detail) {
        window.dispatchEvent(new CustomEvent(name, { detail }));
    }

    /* ---- DM-only threat pins (not part of MapDocumentDto) ---- */

    async loadThreatPins() {
        if (!this.mapId) return;
        try {
            const res = await window.dmRequest(`/api/v1/maps/${this.mapId}/pins`);
            const pins = await res.json();
            this.emit('map-threat-pins', { pins });
            return pins;
        } catch (error) {
            window.reportActionFailure('Could not load threat pins.', error, () => this.loadThreatPins());
            return [];
        }
    }

    async createThreatPin({ threatKind, threatId, x, y, label }) {
        const keyBase = (label || threatKind || 'pin')
            .toString()
            .toLowerCase()
            .replace(/[^a-z0-9._-]+/g, '-')
            .replace(/^-+|-+$/g, '')
            .slice(0, 80) || 'pin';
        const key = `${keyBase}-${Date.now().toString(36)}`.slice(0, 100);
        const body = {
            key,
            threatKind,
            threatId,
            x: Math.max(0, Math.floor(Number(x) || 0)),
            y: Math.max(0, Math.floor(Number(y) || 0)),
            label: label || null,
            sortOrder: 0,
        };
        const res = await window.dmRequest(`/api/v1/maps/${this.mapId}/pins`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        const created = await res.json();
        await this.loadThreatPins();
        return created;
    }

    async deleteThreatPin(pinId) {
        await window.dmRequest(`/api/v1/maps/${this.mapId}/pins/${pinId}`, { method: 'DELETE' });
        await this.loadThreatPins();
    }
}
