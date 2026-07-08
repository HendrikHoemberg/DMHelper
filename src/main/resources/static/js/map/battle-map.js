import { drawGrid, setupPanAndZoom, cellPos, snapPixel, pixelToCell } from './shared.js';

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

        /** @type {Object.<string, {group: import('konva').Group, body: import('konva').Rect, label: import('konva').Text, hpBar: import('konva').Rect, hpText: import('konva').Text, ring: import('konva').Rect}>} */
        this.tokenNodes = {};
        this.aoeNodes = [];
        this.aoeStartPos = null;
        this.aoeRadius = 60;
        this.measureLine = null;
        this.measureLabel = null;
        this.measureStart = null;

        this.stage = null;
        this.gridLayer = null;
        this.terrainLayer = null;
        this.tokenLayer = null;
        this.annotationLayer = null;
        this.previewLayer = null;
    }

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

        this.annotationLayer = new Konva.Layer({ listening: false });
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

    /* ---- Events ---- */
    emit(event, detail) {
        window.dispatchEvent(new CustomEvent('battle-' + event, { detail }));
    }

    emitState() {
        this.emit('modestate', { movementMode: this.movementMode, showGrid: this.showGrid });
        this.emit('tokenupdate', { tokens: this.tokens });
    }

    setupEvents() {
        const s = this.cellSizePx;

        this.stage.on('mousedown touchstart', (e) => {
            if (e.evt.button !== 0) return;
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
            if (this.activeTool === 'measure') { this.finishMeasure(); }
            if (['cone', 'sphere', 'cube', 'line'].includes(this.activeTool)) { this.finishAoeTemplate(); }
        });

        this.stage.on('click tap', (e) => {
            if (e.target === this.stage) {
                this.deselectToken();
            }
        });
    }

    /* ---- Map Document & Grid ---- */
    async fetchMapDocument() {
        const resp = await fetch(`/api/v1/maps/${this.mapId}/document`);
        const data = await resp.json();
        this.docVersion = data.version;
        this.renderTerrain(data.document);
    }

    renderTerrain(doc) {
        this.terrainLayer.destroyChildren();
        if (!doc || !doc.layers) return;
        const terrainLayer = doc.layers.find(l => l.id === 'terrain');
        if (!terrainLayer || !terrainLayer.cells) return;
        const s = this.cellSizePx;
        for (const cell of terrainLayer.cells) {
            const color = TERRAIN_COLORS[cell.terrain] || '#2a2a3e';
            this.terrainLayer.add(new Konva.Rect({
                x: cell.col * s, y: cell.row * s, width: s, height: s,
                fill: color, stroke: '#222', strokeWidth: 0.5,
            }));
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

    /* ---- Tokens: fetch, render, drag ---- */
    async fetchTokens() {
        const resp = await fetch(`/api/v1/maps/${this.mapId}/tokens`);
        this.tokens = await resp.json();
    }

    renderTokens() {
        for (const nodes of Object.values(this.tokenNodes)) nodes.group.destroy();
        this.tokenNodes = {};
        this.tokenLayer.destroyChildren();

        for (const token of this.tokens) this.addTokenNode(token);
        this.tokenLayer.batchDraw();
    }

    addTokenNode(token) {
        const s = this.cellSizePx;
        const px = token.positionX;
        const py = token.positionY;
        const w = token.sizeCols * s;
        const h = token.sizeRows * s;
        const isDm = this.dmMode;

        const group = new Konva.Group({ x: px, y: py, draggable: true, name: 'token' });
        group._tokenId = token.id;

        const body = new Konva.Rect({
            width: w, height: h,
            fill: token.color || '#7b68ee',
            stroke: this.selectedTokenId === token.id ? '#ff0' : (KIND_RING_COLORS[token.kind] || '#fff'),
            strokeWidth: this.selectedTokenId === token.id ? 3 : 2,
            cornerRadius: 4,
            opacity: (!isDm && token.hidden) ? 0.3 : 1,
        });
        group.add(body);

        const ring = new Konva.Rect({
            width: w + 4, height: h + 4, x: -2, y: -2,
            stroke: '#e74c3c', strokeWidth: 2, cornerRadius: 4,
            fillEnabled: false, visible: token.bloodied, listening: false,
        });
        group.add(ring);

        const label = new Konva.Text({
            text: token.name.substring(0, 2),
            fontSize: Math.min(w, h) * 0.4,
            fill: '#fff', align: 'center', verticalAlign: 'middle',
            width: w, height: h,
        });
        group.add(label);

        const hpBarHeight = 4;
        const hasHp = token.currentHp != null && token.maxHp != null && token.maxHp > 0;
        const hpBar = new Konva.Rect({
            y: h, width: w, height: hpBarHeight,
            fill: HP_COLORS.high, visible: isDm && hasHp,
        });
        group.add(hpBar);

        const hpText = new Konva.Text({
            y: h + hpBarHeight + 2,
            text: hasHp ? `${token.currentHp}/${token.maxHp}` : '',
            fontSize: 10, fill: '#ccc', align: 'center', width: w,
            visible: isDm && hasHp,
        });
        group.add(hpText);

        if (hasHp) {
            const ratio = token.currentHp / token.maxHp;
            hpBar.fill(ratio > 0.5 ? HP_COLORS.high : ratio > 0.25 ? HP_COLORS.mid : HP_COLORS.low);
            hpBar.width(w * Math.max(0, ratio));
        }

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

        group.on('click tap', () => { this.selectToken(token.id); });

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
            this._setSaved();
        } catch (e) { this._setError(); }
    }

    /* ---- Token CRUD ---- */
    async createToken(req) {
        const resp = await fetch(`/api/v1/maps/${this.mapId}/tokens`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(req),
        });
        const token = await resp.json();
        this.tokens.push(token);
        this.addTokenNode(token);
        this.tokenLayer.batchDraw();
        this.emit('tokenupdate', { tokens: this.tokens });
    }

    async deleteToken(id) {
        await fetch(`/api/v1/tokens/${id}`, { method: 'DELETE' });
        const node = this.tokenNodes[id];
        if (node) { node.group.destroy(); delete this.tokenNodes[id]; }
        this.tokens = this.tokens.filter(t => t.id !== id);
        this.tokenLayer.batchDraw();
        if (this.selectedTokenId === id) this.deselectToken();
        this.emit('tokenupdate', { tokens: this.tokens });
    }

    selectToken(id) {
        this.selectedTokenId = id;
        const token = this.tokens.find(t => t.id === id);
        this.emit('tokenselect', { token });
        this.renderTokens();
    }

    deselectToken() {
        this.selectedTokenId = null;
        this.emit('tokenselect', { token: null });
        this.renderTokens();
    }

    async updateToken(id, data) {
        const resp = await fetch(`/api/v1/tokens/${id}`, {
            method: 'PUT', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data),
        });
        const updated = await resp.json();
        const idx = this.tokens.findIndex(t => t.id === id);
        if (idx >= 0) this.tokens[idx] = updated;
        this.renderTokens();
        this.emit('tokenupdate', { tokens: this.tokens });
        if (this.selectedTokenId === id) this.emit('tokenselect', { token: updated });
    }

    async addPartyToMap() {
        const resp = await fetch(`/api/v1/maps/${this.mapId}/tokens/add-party`, { method: 'POST' });
        this.tokens = await resp.json();
        this.renderTokens();
        this.emit('tokenupdate', { tokens: this.tokens });
    }

    /* ---- Tool & Mode Switching ---- */
    setTool(tool) {
        this.activeTool = tool;
        this.container.style.cursor = tool === 'measure' ? 'crosshair' : 'default';
        if (!['cone', 'sphere', 'cube', 'line'].includes(tool)) this.clearAoeNodes();
        if (tool !== 'measure') this.clearMeasure();
        this.emit('toolchange', { tool });
    }

    async setMovementMode(mode) {
        this.movementMode = mode;
        this._patchMode(mode, null);
        this.emit('modestate', { movementMode: mode, showGrid: this.showGrid });
    }

    async setShowGrid(show) {
        this.showGrid = show;
        this.renderGrid();
        this._patchMode(null, show);
        this.emit('modestate', { movementMode: this.movementMode, showGrid: show });
    }

    async _patchMode(movementMode, showGrid) {
        try {
            await fetch(`/api/v1/maps/${this.mapId}`, {
                method: 'PATCH', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ movementMode, showGrid }),
            });
        } catch (e) { /* non-critical */ }
    }

    setDmMode(dm) {
        this.dmMode = dm;
        this.renderTokens();
    }

    _setSaved() {
        if (this.saveIndicatorEl) {
            this.saveIndicatorEl.textContent = 'Saved';
            this.saveIndicatorEl.className = 'save-indicator save-saved';
        }
    }

    _setError() {
        if (this.saveIndicatorEl) {
            this.saveIndicatorEl.textContent = 'Save error';
            this.saveIndicatorEl.className = 'save-indicator save-error';
        }
    }

    /* ---- AoE Templates ---- */
    startAoeTemplate(pos) {
        this.aoeStartPos = pos;
        this.aoeRadius = 2 * this.cellSizePx;
        this.drawAoeTemplate(pos, this.aoeRadius);
    }

    drawAoeTemplate(pos, radius) {
        this.clearAoeNodes();
        const s = this.cellSizePx;
        const snap = this.movementMode === 'GRID';

        let node;
        switch (this.activeTool) {
            case 'sphere':
                node = new Konva.Circle({
                    x: snap ? snapPixel(pos.x, s, true) + s / 2 : pos.x,
                    y: snap ? snapPixel(pos.y, s, true) + s / 2 : pos.y,
                    radius,
                    fill: 'rgba(255, 100, 100, 0.2)',
                    stroke: 'rgba(255, 100, 100, 0.6)', strokeWidth: 2,
                    listening: false,
                });
                break;
            case 'cone':
                node = new Konva.Wedge({
                    x: snap ? snapPixel(pos.x, s, true) + s / 2 : pos.x,
                    y: snap ? snapPixel(pos.y, s, true) + s / 2 : pos.y,
                    radius, angle: 53, rotation: -26.5,
                    fill: 'rgba(255, 100, 100, 0.2)',
                    stroke: 'rgba(255, 100, 100, 0.6)', strokeWidth: 2,
                    listening: false,
                });
                break;
            case 'cube':
                node = new Konva.Rect({
                    x: snap ? snapPixel(pos.x, s, true) : pos.x,
                    y: snap ? snapPixel(pos.y, s, true) : pos.y,
                    width: radius, height: radius,
                    fill: 'rgba(100, 100, 255, 0.2)',
                    stroke: 'rgba(100, 100, 255, 0.6)', strokeWidth: 2,
                    listening: false,
                });
                break;
            case 'line':
                node = new Konva.Line({
                    points: [pos.x, pos.y, pos.x + radius, pos.y],
                    stroke: 'rgba(255, 255, 100, 0.6)',
                    strokeWidth: Math.max(2, s / 4), lineCap: 'round',
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
        this.aoeRadius = Math.max(this.cellSizePx, Math.sqrt(dx * dx + dy * dy));
        this.drawAoeTemplate(this.aoeStartPos, this.aoeRadius);
    }

    finishAoeTemplate() { this.aoeStartPos = null; }

    clearAoeNodes() {
        for (const node of this.aoeNodes) node.destroy();
        this.aoeNodes = [];
        this.previewLayer.batchDraw();
    }

    /* ---- Measurement ---- */
    startMeasure(pos) {
        this.clearMeasure();
        this.measureStart = pos;
        this.measureLine = new Konva.Line({
            points: [pos.x, pos.y, pos.x, pos.y],
            stroke: '#4a9eff', strokeWidth: 2, dash: [6, 4],
            listening: false,
        });
        this.previewLayer.add(this.measureLine);
        this.measureLabel = new Konva.Text({
            text: '', fontSize: 13, fill: '#4a9eff', listening: false,
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

        const label = this.movementMode === 'GRID'
            ? `${Math.round(cells * 10) / 10} cells (${feet} ft)`
            : `${feet} ft`;
        this.measureLabel.text(label);
        this.measureLabel.position({
            x: (this.measureStart.x + x2) / 2 + 5,
            y: (this.measureStart.y + y2) / 2 - 15,
        });
        this.previewLayer.batchDraw();
    }

    finishMeasure() { this.measureStart = null; }

    clearMeasure() {
        if (this.measureLine) { this.measureLine.destroy(); this.measureLine = null; }
        if (this.measureLabel) { this.measureLabel.destroy(); this.measureLabel = null; }
        this.measureStart = null;
        this.previewLayer.batchDraw();
    }
}
