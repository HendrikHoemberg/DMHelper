window.AOE_PRESETS = {
    cone: [{ label: '15 ft', cells: 3 }, { label: '30 ft', cells: 6 }, { label: '60 ft', cells: 12 }],
    sphere: [{ label: '5 ft', cells: 1 }, { label: '10 ft', cells: 2 }, { label: '20 ft', cells: 4 }, { label: '30 ft', cells: 6 }],
    cube: [{ label: '5 ft', cells: 1 }, { label: '10 ft', cells: 2 }, { label: '15 ft', cells: 3 }, { label: '20 ft', cells: 4 }],
    line: [{ label: '30 ft', cells: 6 }, { label: '60 ft', cells: 12 }, { label: '120 ft', cells: 24 }],
};

function sessionCockpit(config) {
    return {
        tool: 'select',
        movementMode: 'GRID',
        showGrid: true,
        dmMode: true,
        showTracker: false,
        activeTab: 'tokens',
        currentMapId: config.mapId || '',
        maps: [],
        visitedMapIds: new Set(),
        tokens: [],
        selectedToken: null,
        sbSearch: '',
        sbResults: [],
        currentAoEPresets: [],
        encounterDropdownOpen: false,
        activeEncounter: null,
        plannedEncounters: [],
        presentingMap: false,
        playerViewUrl: window.location.origin + '/player',
        lifecycleOpen: false,
        campaignId: config.campaignId || '',

        request(url, options = {}) {
            return window.dmRequest(url, options);
        },
        failure(summary, error, retry) {
            window.reportActionFailure(summary, error, retry);
        },

        init() {
            window.setDmMode(this.dmMode, { animate: false });
            window.addEventListener('battle-state-changed', () => {
                if (this.presentingMap) {
                    const cid = this.campaignId;
                    this.request(`/api/v1/campaigns/${cid}/table/refresh`, { method: 'POST' })
                        .catch(error => window.reportActionFailure(
                            'The player display could not refresh.', error,
                            () => this.request(`/api/v1/campaigns/${cid}/table/refresh`, { method: 'POST' })));
                }
            });
            window.addEventListener('battle-toolchange', (e) => {
                this.tool = e.detail.tool;
                this.currentAoEPresets = AOE_PRESETS[this.tool] || [];
            });
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
                this.visitedMapIds.add(this.currentMapId);
            });
            window.addEventListener('tracker-encounter-state', (e) => {
                this.activeEncounter = e.detail.encounter;
                if (this.activeEncounter) {
                    this.showTracker = true;
                    this.activeTab = 'tracker';
                }
            });
            if (config.mapId) {
                this.loadMaps();
                this.loadPlannedEncounters();
                this.loadActiveEncounter();
                this.visitedMapIds.add(this.currentMapId);
                this.initBattleMap();
            }
        },

        initBattleMap() {
            const container = document.getElementById('battleCanvasWrap');
            if (!container) return;
            import('/js/map/battle-map.js').then(mod => {
                const BattleMap = mod.BattleMap;
                const bm = new BattleMap({
                    container,
                    mapId: config.mapId,
                    campaignId: config.campaignId,
                    statusEl: document.getElementById('battleStatusMessage'),
                    saveIndicatorEl: document.getElementById('battleSaveIndicator'),
                    cursorInfoEl: document.getElementById('battleCursorInfo'),
                });
                window.battleMap = bm;
                bm.load();
            });
        },

        async newEncounter() {
            try {
                const cid = this.campaignId;
                const resp = await this.request(`/api/v1/campaigns/${cid}/encounters`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name: 'New Encounter', mapId: this.currentMapId }),
                });
                const enc = await resp.json();
                await this.request(`/api/v1/encounters/${enc.id}/activate`, { method: 'POST' });
                this.loadPlannedEncounters();
            } catch (error) {
                this.failure('Could not create the encounter.', error,
                    () => this.newEncounter());
            }
        },

        async activateEncounter(id) {
            try {
                await this.request(`/api/v1/encounters/${id}/activate`, { method: 'POST' });
            } catch (error) {
                this.failure('Could not activate the encounter.', error,
                    () => this.activateEncounter(id));
            }
        },

        async loadPlannedEncounters() {
            try {
                const cid = this.campaignId;
                const resp = await this.request(`/api/v1/campaigns/${cid}/encounters`);
                this.plannedEncounters = await resp.json();
            } catch (error) {
                this.failure('Could not load planned encounters.', error,
                    () => this.loadPlannedEncounters());
            }
        },

        async loadActiveEncounter() {
            try {
                const cid = this.campaignId;
                const resp = await this.request(`/api/v1/campaigns/${cid}/encounters/active`);
                this.activeEncounter = await resp.json();
                if (this.activeEncounter) {
                    this.showTracker = true;
                    this.activeTab = 'tracker';
                }
            } catch (error) {
                this.failure('Could not load the active encounter.', error,
                    () => this.loadActiveEncounter());
            }
        },

        setTool(t) { window.battleMap?.setTool(t); },

        async addToken() {
            const name = prompt('Token name:') || 'Token';
            const kind = prompt('Kind (PC/NPC/MONSTER/OBJECT):', 'NPC') || 'NPC';
            if (!window.battleMap) return;
            const s = window.battleMap.cellSizePx;
            let px = (-window.battleMap.stage.x() + window.battleMap.container.clientWidth / 2) / window.battleMap.stage.scaleX();
            let py = (-window.battleMap.stage.y() + window.battleMap.container.clientHeight / 2) / window.battleMap.stage.scaleY();
            const colors = { PC: '#4a9eff', NPC: '#2ecc71', MONSTER: '#e74c3c', OBJECT: '#f39c12' };
            await window.battleMap.createToken({
                name, kind, positionX: Math.round(px), positionY: Math.round(py),
                sizeCols: 1, sizeRows: 1,
                color: colors[kind] || '#c9a35c', hidden: false,
                currentHp: null, maxHp: null,
            });
        },

        async addParty() { await window.battleMap?.addPartyToMap(); },
        async deleteToken(id) { if (confirm('Delete this token?')) await window.battleMap?.deleteToken(id); },
        async duplicateToken(id) { await window.battleMap?.duplicateToken(id); },
        async toggleDead() {
            if (this.selectedToken) {
                const requestedDead = this.selectedToken.dead;
                await window.battleMap?.markDead(
                    this.selectedToken.id, requestedDead, !requestedDead);
            }
        },
        focusToken(id) { window.battleMap?.focusToken(id); },

        async updateToken() {
            if (this.selectedToken) {
                await window.battleMap?.updateToken(this.selectedToken.id, this.selectedToken);
            }
        },

        async toggleMovementMode() {
            const newMode = this.movementMode === 'GRID' ? 'FREEFORM' : 'GRID';
            await window.battleMap?.setMovementMode(newMode);
        },
        async toggleShowGrid() {
            await window.battleMap?.setShowGrid(!this.showGrid);
        },
        toggleDmMode() {
            this.dmMode = !this.dmMode;
            window.setDmMode(this.dmMode);
            window.battleMap?.setDmMode(this.dmMode);
            window.dispatchEvent(new CustomEvent('dm-mode-changed', { detail: { dmMode: this.dmMode } }));
        },
        async sendToTable() {
            try {
                const cid = this.campaignId;
                await this.request(`/api/v1/campaigns/${cid}/table/presentation`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ mode: 'MAP', ref: this.currentMapId }),
                });
                this.presentingMap = true;
            } catch (error) {
                this.presentingMap = false;
                this.failure('Could not show this map to the table.', error,
                    () => this.sendToTable());
            }
        },
        async curtain() {
            const wasPresenting = this.presentingMap;
            try {
                const cid = this.campaignId;
                await this.request(`/api/v1/campaigns/${cid}/table/presentation`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ mode: 'CURTAIN', ref: '' }),
                });
                this.presentingMap = false;
            } catch (error) {
                this.presentingMap = wasPresenting;
                this.failure('Could not lower the curtain.', error, () => this.curtain());
            }
        },
        clearAoe() { window.battleMap?.clearAoeNodes(); },
        clearAnnotations() { window.battleMap?.clearAnnotations(); },
        applyAoePreset(cells) {
            window.battleMap?.applyAoePreset(this.tool, cells);
        },

        async loadMaps() {
            try {
                const cid = this.campaignId;
                const resp = await this.request(`/api/v1/campaigns/${cid}/maps`);
                this.maps = await resp.json();
            } catch (error) {
                this.failure('Could not load the map switcher.', error,
                    () => this.loadMaps());
            }
        },
        switchMap(mapId) {
            const bm = window.battleMap;
            if (bm && mapId !== bm.mapId) {
                bm.switchToMap(mapId);
                this.currentMapId = mapId;
                this.visitedMapIds.add(mapId);
            }
            history.replaceState(null, '', `/campaigns/${this.campaignId}/session?mapId=${mapId}`);
        },

        async searchStatblocks() {
            if (!this.sbSearch || this.sbSearch.length < 2) { this.sbResults = []; return; }
            try {
                const resp = await this.request(`/api/v1/library/statblocks/search?q=${encodeURIComponent(this.sbSearch)}`);
                this.sbResults = await resp.json();
            } catch (error) {
                this.failure('Could not search statblocks.', error,
                    () => this.searchStatblocks());
            }
        },
        async addStatblockToken(id) {
            const created = await window.battleMap?.createTokenFromStatblock(id);
            if (created) {
                this.sbSearch = '';
                this.sbResults = [];
            }
        },

        nextTurn() {
            window.dispatchEvent(new CustomEvent('tracker-next-turn'));
        },

        stepScene(direction) {
            window.dispatchEvent(new CustomEvent('scene-step', { detail: { direction } }));
        },

        openSearch() {
            const palette = document.querySelector('[x-data="commandPalette"]');
            if (palette && palette.__x) palette.__x.$data.open = true;
        },

        openDice() {
            const dice = document.querySelector('[x-data="diceRoller()"]');
            if (dice && dice.__x) dice.__x.$data.open = true;
        },
    };
}

window.sessionCockpit = sessionCockpit;
