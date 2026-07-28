import { drawGrid } from './map/shared.js';
import { renderRuntimeDocument } from './map/runtime-renderer.js';

const root = document.querySelector('[data-encounter-placement-board]');
const canvas = document.getElementById('placement-canvas');
const API_BASE = '/api/v1';

const state = {
    encounterId: root?.dataset.encounterId,
    mapId: root?.dataset.mapId || null,
    cellSizePx: 48,
    gridWidth: 30,
    gridHeight: 20,
    placements: [],
    combatants: [],
    selectedCombatantId: null,
    cursorCol: 0,
    cursorRow: 0,
    stage: null,
    tokenLayer: null,
    cursorNode: null,
};

function defaultColorFor(combatant) {
    return { PC: '#4a9eff', MONSTER: '#d95c5c', OBJECT: '#8a8a8a' }[combatant?.kind] || '#7b68ee';
}

function combatant(id) {
    return state.combatants.find(item => item.id === id);
}

async function placeCombatant(combatantId, col, row) {
    const member = combatant(combatantId);
    await dmRequest(`${API_BASE}/encounters/${state.encounterId}/combatants/${combatantId}/placement`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            positionX: col * state.cellSizePx,
            positionY: row * state.cellSizePx,
            sizeCols: 1,
            sizeRows: 1,
            color: defaultColorFor(member),
            icon: null,
        }),
    });
    await refreshPlacements();
}

async function moveCombatant(combatantId, x, y) {
    await dmRequest(`${API_BASE}/encounters/${state.encounterId}/combatants/${combatantId}/placement/move`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ positionX: x, positionY: y }),
    });
    await refreshPlacements();
}

function renderPlacements() {
    if (!state.tokenLayer) return;
    state.tokenLayer.destroyChildren();
    for (const placement of state.placements) {
        const member = combatant(placement.combatantId);
        const size = state.cellSizePx;
        const group = new Konva.Group({
            x: placement.positionX,
            y: placement.positionY,
            draggable: true,
        });
        group.add(new Konva.Rect({
            width: placement.sizeCols * size,
            height: placement.sizeRows * size,
            fill: placement.color || defaultColorFor(member),
            stroke: '#f4dfae',
            strokeWidth: 2,
            cornerRadius: 4,
        }));
        group.add(new Konva.Text({
            text: member?.name?.substring(0, 2) || '?',
            width: placement.sizeCols * size,
            height: placement.sizeRows * size,
            align: 'center',
            verticalAlign: 'middle',
            fill: '#fff',
            fontSize: Math.min(24, size * 0.4),
        }));
        group.on('dragend', async () => {
            const maxX = (state.gridWidth - placement.sizeCols) * size;
            const maxY = (state.gridHeight - placement.sizeRows) * size;
            const x = Math.max(0, Math.min(maxX, Math.round(group.x() / size) * size));
            const y = Math.max(0, Math.min(maxY, Math.round(group.y() / size) * size));
            group.position({ x, y });
            state.tokenLayer.batchDraw();
            try {
                await moveCombatant(placement.combatantId, x, y);
            } catch (error) {
                window.showToast(`Failed to move combatant: ${error.message}`, 'error');
                await refreshPlacements();
            }
        });
        state.tokenLayer.add(group);
    }
    state.tokenLayer.batchDraw();
    updateRoster();
}

function updateRoster() {
    const placedIds = new Set(state.placements.map(item => item.combatantId));
    document.querySelectorAll('[data-combatant-id]').forEach(item => {
        const placed = placedIds.has(item.dataset.combatantId);
        item.classList.toggle('is-placed', placed);
        item.draggable = !placed;
        const badge = item.querySelector('.badge');
        if (badge) badge.textContent = placed ? 'Placed' : 'Unplaced';
    });
}

async function refreshPlacements() {
    const response = await dmRequest(`${API_BASE}/encounters/${state.encounterId}/placements`);
    state.placements = await response.json();
    renderPlacements();
}

function resizeStage() {
    if (!state.stage) return;
    const worldWidth = state.gridWidth * state.cellSizePx;
    const worldHeight = state.gridHeight * state.cellSizePx;
    const width = Math.max(300, canvas.clientWidth);
    const scale = Math.min(1, width / worldWidth);
    state.stage.width(worldWidth * scale);
    state.stage.height(worldHeight * scale);
    state.stage.scale({ x: scale, y: scale });
    state.stage.batchDraw();
}

function pointerCell(event) {
    const rect = canvas.getBoundingClientRect();
    const scale = state.stage.scaleX();
    return {
        col: Math.max(0, Math.min(state.gridWidth - 1,
            Math.floor((event.clientX - rect.left) / scale / state.cellSizePx))),
        row: Math.max(0, Math.min(state.gridHeight - 1,
            Math.floor((event.clientY - rect.top) / scale / state.cellSizePx))),
    };
}

function selectForPlacement(id) {
    if (state.placements.some(item => item.combatantId === id)) return;
    state.selectedCombatantId = id;
    canvas.focus();
    renderCursor();
}

function renderCursor() {
    state.cursorNode?.destroy();
    state.cursorNode = null;
    if (!state.selectedCombatantId || !state.tokenLayer) return;
    state.cursorNode = new Konva.Rect({
        x: state.cursorCol * state.cellSizePx,
        y: state.cursorRow * state.cellSizePx,
        width: state.cellSizePx,
        height: state.cellSizePx,
        fill: 'rgba(201,163,92,0.25)',
        stroke: '#c9a35c',
        strokeWidth: 2,
        dash: [6, 4],
        listening: false,
    });
    state.tokenLayer.add(state.cursorNode);
    state.cursorNode.moveToTop();
    state.tokenLayer.batchDraw();
}

async function init() {
    if (!root || !canvas || !state.encounterId || !state.mapId) return;
    try {
        const [mapResponse, documentResponse, combatantsResponse, placementsResponse] = await Promise.all([
            dmRequest(`${API_BASE}/maps/${state.mapId}`),
            dmRequest(`${API_BASE}/maps/${state.mapId}/document`),
            dmRequest(`${API_BASE}/encounters/${state.encounterId}/combatants`),
            dmRequest(`${API_BASE}/encounters/${state.encounterId}/placements`),
        ]);
        const map = await mapResponse.json();
        const mapDocument = await documentResponse.json();
        state.combatants = await combatantsResponse.json();
        state.placements = await placementsResponse.json();
        state.cellSizePx = map.cellSizePx;
        state.gridWidth = map.gridWidth;
        state.gridHeight = map.gridHeight;

        state.stage = new Konva.Stage({ container: canvas });
        const terrainLayer = new Konva.Layer();
        const gridLayer = new Konva.Layer();
        state.tokenLayer = new Konva.Layer();
        state.stage.add(terrainLayer, gridLayer, state.tokenLayer);
        await renderRuntimeDocument({
            Konva,
            document: mapDocument.document,
            targetLayer: terrainLayer,
            gridWidth: state.gridWidth,
            gridHeight: state.gridHeight,
            cellSizePx: state.cellSizePx,
            playerView: false,
        });
        drawGrid(gridLayer, state.gridWidth, state.gridHeight, state.cellSizePx);
        renderPlacements();
        resizeStage();
        new ResizeObserver(resizeStage).observe(canvas);
    } catch (error) {
        window.showToast(`Failed to load placement board: ${error.message}`, 'error');
    }
}

document.querySelectorAll('[data-combatant-id]').forEach(item => {
    item.addEventListener('dragstart', event => {
        event.dataTransfer.setData('text/plain', item.dataset.combatantId);
    });
    item.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            selectForPlacement(item.dataset.combatantId);
        }
    });
});

canvas?.addEventListener('dragover', event => event.preventDefault());
canvas?.addEventListener('drop', async event => {
    event.preventDefault();
    const id = event.dataTransfer.getData('text/plain');
    const cell = pointerCell(event);
    if (id) await placeCombatant(id, cell.col, cell.row);
});
canvas?.addEventListener('click', async event => {
    if (!state.selectedCombatantId) return;
    const cell = pointerCell(event);
    await placeCombatant(state.selectedCombatantId, cell.col, cell.row);
    state.selectedCombatantId = null;
    renderCursor();
});
canvas?.addEventListener('keydown', async event => {
    if (!state.selectedCombatantId) return;
    const delta = {
        ArrowLeft: [-1, 0], ArrowRight: [1, 0],
        ArrowUp: [0, -1], ArrowDown: [0, 1],
    }[event.key];
    if (delta) {
        event.preventDefault();
        state.cursorCol = Math.max(0, Math.min(state.gridWidth - 1, state.cursorCol + delta[0]));
        state.cursorRow = Math.max(0, Math.min(state.gridHeight - 1, state.cursorRow + delta[1]));
        renderCursor();
    } else if (event.key === 'Enter') {
        event.preventDefault();
        await placeCombatant(state.selectedCombatantId, state.cursorCol, state.cursorRow);
        state.selectedCombatantId = null;
        renderCursor();
    } else if (event.key === 'Escape') {
        state.selectedCombatantId = null;
        renderCursor();
    }
});

async function autoPlaceCombatants() {
    await runBulk('[data-auto-place]', 'Placing...', 'Place unplaced', 'placements/auto');
}

async function placeMissingParty() {
    await runBulk('[data-place-party]', 'Placing...', 'Place missing party members', 'placements/party');
}

async function runBulk(selector, busyText, idleText, path) {
    const button = document.querySelector(selector);
    button.disabled = true;
    button.textContent = busyText;
    try {
        await dmRequest(`${API_BASE}/encounters/${state.encounterId}/${path}`, { method: 'POST' });
        window.location.reload();
    } catch (error) {
        window.showToast(`Placement failed: ${error.message}`, 'error');
        button.disabled = false;
        button.textContent = idleText;
    }
}

async function changeEncounterMap(mapId) {
    try {
        await dmRequest(`${API_BASE}/encounters/${state.encounterId}/map`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ mapId }),
        });
        window.location.reload();
    } catch (error) {
        window.showToast(`Failed to change map: ${error.message}`, 'error');
    }
}

window.autoPlaceCombatants = autoPlaceCombatants;
window.placeMissingParty = placeMissingParty;
window.changeEncounterMap = changeEncounterMap;
window.placementBoard = state;

init();
