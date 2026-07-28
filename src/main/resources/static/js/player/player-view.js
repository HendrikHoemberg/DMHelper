import { renderRuntimeDocument } from '../map/runtime-renderer.js';
import { renderHandout } from './handout-renderer.js';

const WS_URL = `ws://${window.location.host}/ws/table`;

let stage = null;
let documentLayer = null;
let gridLayer = null;
let tokenLayer = null;
let tokenNodes = new Map();
let currentState = null;
let ws = null;
let reconnectTimer = null;
let renderGeneration = 0;
const RECONNECT_DELAY = 2000;

function setStatus(text, color) {
    const el = document.getElementById('pvStatus');
    if (el) {
        el.textContent = text;
        el.style.color = color || 'var(--color-text-muted)';
    }
}

function connect() {
    if (ws && ws.readyState === WebSocket.OPEN) return;

    ws = new WebSocket(WS_URL);
    setStatus('Connecting...');

    ws.onopen = () => {
        setStatus('Connected', 'var(--color-success)');
        if (reconnectTimer) {
            clearTimeout(reconnectTimer);
            reconnectTimer = null;
        }
    };

    ws.onmessage = (event) => {
        try {
            const state = JSON.parse(event.data);
            if (state.type === 'TOKEN_MOVED' && currentState && currentState.mode === 'MAP') {
                currentState.map = { ...currentState.map, tokens: state.map.tokens };
                updateTokensOnly(currentState);
                return;
            }
            if (state.type === 'TURN_CHANGED' && currentState && currentState.mode === 'MAP') {
                currentState.initiative = state.initiative;
                currentState.activeTurnIndex = state.activeTurnIndex;
                updateInitiativeOnly(currentState);
                return;
            }
            currentState = state;
            render(state);
        } catch (e) {
            console.error('Failed to parse table state', e);
        }
    };

    ws.onclose = () => {
        setStatus('Reconnecting...', 'var(--color-warning)');
        reconnectTimer = setTimeout(connect, RECONNECT_DELAY);
    };

    ws.onerror = () => {
        ws.close();
    };
}

function render(state) {
    const generation = ++renderGeneration;
    clearContent();

    if (state.mode === 'CURTAIN') {
        showCurtain();
    } else if (state.mode === 'MAP') {
        showMap(state, generation);
    } else if (state.mode === 'HANDOUT') {
        showHandout(state);
    }
    showInitiative(state);
}

function clearContent() {
    const content = document.getElementById('playerContent');
    if (content) content.innerHTML = '';

    if (stage) {
        stage.destroy();
        stage = null;
        tokenNodes.clear();
    }
}

function showCurtain() {
    const content = document.getElementById('playerContent');
    content.innerHTML = `
        <div class="pv-curtain">
            <div style="text-align: center;">
                <div style="font-size: 3rem; margin-bottom: 16px;">🎲</div>
                <div>Waiting for the DM...</div>
            </div>
        </div>`;
}

async function showMap(state, generation) {
    if (!state.map) {
        showCurtain();
        return;
    }

    const map = state.map;
    const content = document.getElementById('playerContent');
    content.innerHTML = '<div class="pv-canvas-wrap" id="pvCanvas"></div>';

    const wrap = document.getElementById('pvCanvas');
    const cellPx = map.cellSizePx;
    const width = map.gridWidth * cellPx;
    const height = map.gridHeight * cellPx;

    const nextStage = new Konva.Stage({
        container: 'pvCanvas', width: wrap.clientWidth, height: wrap.clientHeight,
    });
    const nextDocumentLayer = new Konva.Layer({ listening: false });
    const nextGridLayer = new Konva.Layer({ listening: false });
    const nextTokenLayer = new Konva.Layer();
    stage = nextStage;
    documentLayer = nextDocumentLayer;
    gridLayer = nextGridLayer;
    tokenLayer = nextTokenLayer;

    nextStage.add(nextDocumentLayer);
    nextStage.add(nextGridLayer);
    nextStage.add(nextTokenLayer);
    await renderRuntimeDocument({
        Konva,
        document: map.document,
        targetLayer: nextDocumentLayer,
        gridWidth: map.gridWidth,
        gridHeight: map.gridHeight,
        cellSizePx: cellPx,
        playerView: true,
    });
    if (generation !== renderGeneration || stage !== nextStage) return;
    drawGrid(width, height, cellPx, map.showGrid);

    for (const token of map.tokens) {
        drawToken(token, cellPx);
    }

    autoFit(width, height, wrap);
    enablePanZoom(stage, wrap);
}

function drawGrid(w, h, cellPx, showGrid) {
    if (!showGrid || !gridLayer) return;
    for (let x = 0; x <= w; x += cellPx) {
        gridLayer.add(new Konva.Line({
            points: [x, 0, x, h], stroke: 'rgba(255,255,255,0.08)', strokeWidth: 0.5
        }));
    }
    for (let y = 0; y <= h; y += cellPx) {
        gridLayer.add(new Konva.Line({
            points: [0, y, w, y], stroke: 'rgba(255,255,255,0.08)', strokeWidth: 0.5
        }));
    }
}

const KIND_COLORS = { PC: '#4a9eff', NPC: '#2ecc71', MONSTER: '#e74c3c', OBJECT: '#f39c12' };

function drawToken(token, cellPx) {
    const size = token.sizeCols * cellPx;
    const color = KIND_COLORS[token.kind] || token.color || '#c9a35c';

    let fill = color;
    let strokeWidth = 2;
    let stroke = color;

    if (token.dead) {
        fill = 'rgba(100,100,100,0.7)';
        stroke = '#666';
    } else if (token.bloodied) {
        stroke = '#e74c3c';
        strokeWidth = 3;
    }

    const group = new Konva.Group({
        x: token.positionX, y: token.positionY,
        draggable: false,
    });

    const rect = new Konva.Rect({
        width: size, height: size,
        fill: fill, stroke: stroke, strokeWidth: strokeWidth,
        cornerRadius: 3,
    });
    group.add(rect);

    const label = new Konva.Text({
        text: token.name,
        fontSize: Math.max(10, cellPx * 0.25),
        fill: '#fff', align: 'center',
        width: size,
        y: size / 2 - 6,
        listening: false,
    });
    group.add(label);

    tokenLayer.add(group);
    tokenNodes.set(token.id, group);
}

function autoFit(w, h, wrap) {
    if (!stage) return;
    const scale = Math.min(wrap.clientWidth / w, wrap.clientHeight / h, 2);
    stage.scale({ x: scale, y: scale });
    stage.position({
        x: (wrap.clientWidth - w * scale) / 2,
        y: (wrap.clientHeight - h * scale) / 2,
    });
    stage.draw();
}

function enablePanZoom(stage, container) {
    let isPanning = false;
    let lastX = 0, lastY = 0;

    container.addEventListener('mousedown', (e) => {
        if (e.button === 1 || (e.button === 0 && e.ctrlKey)) {
            isPanning = true;
            lastX = e.clientX;
            lastY = e.clientY;
            container.style.cursor = 'grabbing';
        }
    });

    window.addEventListener('mousemove', (e) => {
        if (!isPanning) return;
        const dx = e.clientX - lastX;
        const dy = e.clientY - lastY;
        lastX = e.clientX;
        lastY = e.clientY;
        stage.position({ x: stage.x() + dx, y: stage.y() + dy });
        stage.batchDraw();
    });

    window.addEventListener('mouseup', () => {
        isPanning = false;
        container.style.cursor = 'default';
    });

    container.addEventListener('wheel', (e) => {
        e.preventDefault();
        const oldScale = stage.scaleX();
        const pointer = stage.getPointerPosition();
        const scaleBy = 1.1;
        const newScale = e.deltaY < 0 ? oldScale * scaleBy : oldScale / scaleBy;
        const clamped = Math.max(0.1, Math.min(5, newScale));

        const mouseX = pointer.x / oldScale - stage.x() / oldScale;
        const mouseY = pointer.y / oldScale - stage.y() / oldScale;

        stage.scale({ x: clamped, y: clamped });
        stage.position({
            x: -(mouseX - pointer.x / clamped) * clamped,
            y: -(mouseY - pointer.y / clamped) * clamped,
        });
        stage.batchDraw();
    }, { passive: false });
}

function showHandout(state) {
    if (!state.handout) {
        showCurtain();
        return;
    }
    const content = document.getElementById('playerContent');
    renderHandout(content, state);
}

function showInitiative(state) {
    if (!state.initiative || state.initiative.length === 0) return;
    const initiative = document.createElement('div');
    initiative.className = 'pv-initiative';
    initiative.id = 'pvInitiative';
    state.initiative.forEach((combatant, index) => {
        const chip = document.createElement('div');
        chip.className = 'combatant-chip';
        if (combatant.defeated) chip.classList.add('defeated');
        if (combatant.active || index === state.activeTurnIndex) chip.classList.add('active');
        const name = document.createElement('span');
        name.textContent = combatant.name || '';
        chip.appendChild(name);
        (combatant.conditions || []).forEach(condition => {
            const dot = document.createElement('span');
            dot.className = 'cond-dot';
            dot.textContent = String(condition).charAt(0).toUpperCase();
            chip.appendChild(dot);
        });
        initiative.appendChild(chip);
    });
    document.getElementById('playerContent').appendChild(initiative);
}

function updateTokensOnly(state) {
    if (!state.map || !tokenLayer) return;
    for (const token of state.map.tokens) {
        const existing = tokenNodes.get(token.id);
        if (existing) {
            existing.position({ x: token.positionX, y: token.positionY });
            existing.children().forEach(c => {
                if (c.getAttr('name') === 'tokenRect') {
                    c.stroke(token.bloodied ? '#e74c3c' : (KIND_COLORS[token.kind] || '#c9a35c'));
                    c.strokeWidth(token.bloodied ? 3 : 2);
                }
            });
        }
    }
    if (tokenLayer && tokenLayer.getStage()) tokenLayer.batchDraw();
}

function updateInitiativeOnly(state) {
    const existing = document.getElementById('pvInitiative');
    if (existing) existing.remove();
    showInitiative(state);
}

connect();
