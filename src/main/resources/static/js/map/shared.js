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
