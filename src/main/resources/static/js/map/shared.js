/**
 * Shared Konva utilities for canvas islands.
 * Used by both MapEditor and BattleMap.
 */

/** A CSS custom property's resolved value, read once per call. The canvas cannot read
 *  CSS tokens directly, so Konva consumers resolve them through computed style.
 *  The tokens are contractually defined in tokens.css, so no hardcoded fallback is
 *  kept here (that would be a second source of truth). */
function cssColor(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

/** The map editor's selection-marker color (token --map-selection). */
export function mapSelectionColor() {
    return cssColor('--map-selection');
}

/**
 * Draw grid lines onto a Konva container. MapEditor passes a Konva.Layer and
 * BattleMap passes a Konva.Group; both accept `add` and `destroyChildren`.
 * @param {import('konva').Layer | import('konva').Group} layer
 * @param {number} gridWidth
 * @param {number} gridHeight
 * @param {number} cellSizePx
 */
export function drawGrid(layer, gridWidth, gridHeight, cellSizePx) {
    layer.destroyChildren();
    const s = cellSizePx;
    const gridLine = cssColor('--map-grid-line');
    const gridShadow = cssColor('--map-grid-shadow');
    /* A 1px light stroke plus a 1px dark stroke offset by 1px reads on any imagery:
       on a light image the dark companion carries the line, on a dark one the light
       stroke does. Both tokens hold ≥3:1 against --surface-canvas and mid-grey alike. */
    for (let col = 0; col <= gridWidth; col++) {
        const x = col * s;
        layer.add(new Konva.Line({
            points: [x + 1, 0, x + 1, gridHeight * s],
            stroke: gridShadow, strokeWidth: 1, listening: false,
        }));
        layer.add(new Konva.Line({
            points: [x, 0, x, gridHeight * s],
            stroke: gridLine, strokeWidth: 1, listening: false,
        }));
    }
    for (let row = 0; row <= gridHeight; row++) {
        const y = row * s;
        layer.add(new Konva.Line({
            points: [0, y + 1, gridWidth * s, y + 1],
            stroke: gridShadow, strokeWidth: 1, listening: false,
        }));
        layer.add(new Konva.Line({
            points: [0, y, gridWidth * s, y],
            stroke: gridLine, strokeWidth: 1, listening: false,
        }));
    }
    if (typeof layer.batchDraw === 'function') layer.batchDraw();
}

/**
 * Setup pan (space-drag, middle-mouse) and scroll-wheel zoom on a Konva.Stage.
 * @param {import('konva').Stage} stage
 * @param {HTMLElement} container
 */
export function setupPanAndZoom(stage, container) {
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
 * Convert cell coordinates to pixel position.
 * @param {number} col
 * @param {number} row
 * @param {number} cellSizePx
 * @returns {{x: number, y: number}}
 */
export function cellToPixel(col, row, cellSizePx) {
    return { x: col * cellSizePx, y: row * cellSizePx };
}

/**
 * Convert pixel coordinates to nearest cell.
 * @param {number} px
 * @param {number} py
 * @param {number} cellSizePx
 * @returns {{col: number, row: number}}
 */
export function pixelToCell(px, py, cellSizePx) {
    return { col: Math.floor(px / cellSizePx), row: Math.floor(py / cellSizePx) };
}

/**
 * Return the authoritative map boundary rectangle in pixel units.
 * @param {number} gridWidth
 * @param {number} gridHeight
 * @param {number} cellSizePx
 * @returns {{x: number, y: number, width: number, height: number}}
 */
export function mapPixelBounds(gridWidth, gridHeight, cellSizePx) {
    return { x: 0, y: 0, width: gridWidth * cellSizePx, height: gridHeight * cellSizePx };
}

/**
 * Expand semantic map primitives (ROOM, DOOR, REGION, CORRIDOR) into cell arrays.
 * @param {{primitives?: Array}} document
 * @returns {Array<{col: number, row: number, terrain: string}>}
 */
export function expandPrimitives(document) {
    const cells = [];
    for (const p of (document?.primitives || [])) {
        const c0 = Math.min(p.startCol, p.endCol), c1 = Math.max(p.startCol, p.endCol);
        const r0 = Math.min(p.startRow, p.endRow), r1 = Math.max(p.startRow, p.endRow);
        switch (p.type) {
            case 'ROOM':
                for (let r = r0; r <= r1; r++) {
                    for (let c = c0; c <= c1; c++) {
                        const edge = r === r0 || r === r1 || c === c0 || c === c1;
                        cells.push({ col: c, row: r, terrain: edge ? 'wall' : 'floor' });
                    }
                }
                break;
            case 'CORRIDOR':
                break;
            case 'DOOR':
                cells.push({ col: p.startCol, row: p.startRow, terrain: 'door' });
                break;
            case 'REGION': {
                const terrain = p.terrain || 'floor';
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

/**
 * Write a save state onto a shared `fragments/_status :: save-status` element.
 *
 * <p>Both canvases used to render their own `.save-indicator` span and set a
 * `save-{state}` class on it. That collided with the htmx indicator of the same name in
 * components.css — which is `display: none` until a request is in flight — so the editor
 * carried a `display: inline-block` override just to stay visible, and the colour rules
 * were duplicated verbatim in cockpit.css and map-editor.css. The shared primitive owns
 * both now (spec sections 10 and 18.2).
 */
export function setSaveStatus(element, state) {
    if (!element) return;
    const labels = {
        idle: 'Saved',
        unsaved: 'Unsaved…',
        saving: 'Saving…',
        saved: 'Saved',
        error: 'Save failed',
        conflict: 'Save conflict'
    };
    element.dataset.saveStatus = state;
    const label = element.querySelector('.save-status__label');
    if (label) label.textContent = labels[state] || state;
}
