import { expandPrimitives } from '../map/shared.js';

const WS_URL = `ws://${window.location.host}/ws/table`;

let stage = null;
let gridLayer = null;
let tokenLayer = null;
let tokenNodes = new Map();
let currentState = null;
let ws = null;
let reconnectTimer = null;
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
    clearContent();

    if (state.mode === 'CURTAIN') {
        showCurtain();
    } else if (state.mode === 'MAP') {
        showMap(state);
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

function showMap(state) {
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

    stage = new Konva.Stage({ container: 'pvCanvas', width: wrap.clientWidth, height: wrap.clientHeight });
    gridLayer = new Konva.Layer();
    tokenLayer = new Konva.Layer();

    drawGrid(width, height, cellPx, map.showGrid);

    if (map.document && map.document.layers) {
        const explicitCells = [];
        for (const layer of map.document.layers) {
            if (layer.cells) {
                for (const cell of layer.cells) {
                    explicitCells.push(cell);
                }
            }
        }
        const explicitKeys = new Set(explicitCells.map(c => `${c.col},${c.row}`));
        const primitiveCells = expandPrimitives(map.document).filter(c => !explicitKeys.has(`${c.col},${c.row}`));
        for (const cell of [...primitiveCells, ...explicitCells]) {
            const fill = getTerrainFill(cell.terrain, map.document.customTerrain);
            const rect = new Konva.Rect({
                x: cell.col * cellPx, y: cell.row * cellPx,
                width: cellPx, height: cellPx,
                fill: fill, stroke: 'rgba(255,255,255,0.05)', strokeWidth: 0.5,
            });
            gridLayer.add(rect);
        }
    }

    for (const token of map.tokens) {
        drawToken(token, cellPx);
    }

    stage.add(gridLayer);
    stage.add(tokenLayer);

    autoFit(width, height, wrap);
    enablePanZoom(stage, wrap);
}

function getTerrainFill(terrainKey, customTerrain) {
    const defaults = {
        floor: '#2b2b45', wall: '#4c4c60', water: '#1f4570',
        'difficult': '#3e5228', lava: '#6e2525', pit: '#12121e'
    };
    if (defaults[terrainKey]) return defaults[terrainKey];
    if (customTerrain) {
        const ct = customTerrain.find(t => t.key === terrainKey);
        if (ct) return ct.fill;
    }
    return defaults.floor;
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
    const color = KIND_COLORS[token.kind] || token.color || '#7b68ee';

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
    content.innerHTML = `
        <div class="pv-handout">
            <img src="/files/${state.handout.id}" alt="${state.handout.title}">
        </div>`;
}

function showInitiative(state) {
    let html = '';
    if (state.initiative && state.initiative.length > 0) {
        html += '<div class="pv-initiative" id="pvInitiative">';
        for (let i = 0; i < state.initiative.length; i++) {
            const c = state.initiative[i];
            const classes = ['combatant-chip'];
            if (c.defeated) classes.push('defeated');
            if (c.active || i === state.activeTurnIndex) classes.push('active');
            html += `<div class="${classes.join(' ')}">
                <span>${c.name}</span>
                ${(c.conditions || []).map(cd => `<span class="cond-dot">${cd.charAt(0).toUpperCase()}</span>`).join('')}
            </div>`;
        }
        html += '</div>';
    }
    document.getElementById('playerContent').insertAdjacentHTML('beforeend', html);
}

function updateTokensOnly(state) {
    if (!state.map || !tokenLayer) return;
    for (const token of state.map.tokens) {
        const existing = tokenNodes.get(token.id);
        if (existing) {
            existing.position({ x: token.positionX, y: token.positionY });
            existing.children().forEach(c => {
                if (c.getAttr('name') === 'tokenRect') {
                    c.stroke(token.bloodied ? '#e74c3c' : (KIND_COLORS[token.kind] || '#7b68ee'));
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
