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
        presentingMap: config.presentationMode === 'MAP'
            && config.presentedMapId === config.mapId,
        playerViewUrl: window.location.origin + '/player',
        lifecycleOpen: false,
        campaignId: config.campaignId || '',
        sessionStatus: config.sessionStatus || 'IDLE',
        presentationMode: config.presentationMode || 'CURTAIN',
        presentedMapId: config.presentedMapId || '',
        startMapId: config.mapId || '',
        attendeeIds: config.attendeeIds || [],
        draftTitle: '',
        draftBody: config.draftBody || '',

        async mutateSession(path, options, summary, retry) {
            try {
                const response = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session${path}`, options);
                const state = await response.json();
                this.sessionStatus = state.status;
                this.presentationMode = state.presentationMode;
                this.presentedMapId = '';
                this.presentingMap = false;
                return state;
            } catch (error) {
                window.reportActionFailure(summary, error, retry);
                throw error;
            }
        },

        async startSession() {
            try {
                const resp = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/start`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ mapId: this.startMapId || null }),
                    });
                const state = await resp.json();
                this.sessionStatus = state.status;
                this.presentationMode = state.presentationMode;
                this.presentedMapId = '';
                this.presentingMap = false;
                this.attendeeIds = state.attendeeIds || [];
                this.lifecycleOpen = false;
                window.location.reload();
            } catch (error) {
                window.reportActionFailure('Could not start the session.', error,
                    () => this.startSession());
            }
        },

        async pauseSession() {
            try {
                const resp = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/pause`, { method: 'POST' });
                const state = await resp.json();
                this.sessionStatus = state.status;
            } catch (error) {
                window.reportActionFailure('Could not pause the session.', error,
                    () => this.pauseSession());
            }
        },

        async resumeSession() {
            try {
                const resp = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/resume`, { method: 'POST' });
                const state = await resp.json();
                this.sessionStatus = state.status;
            } catch (error) {
                window.reportActionFailure('Could not resume the session.', error,
                    () => this.resumeSession());
            }
        },

        async cancelReview() {
            try {
                const resp = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/cancel-review`, { method: 'POST' });
                const state = await resp.json();
                this.sessionStatus = state.status;
                this.draftBody = '';
                this.draftTitle = '';
                this.closeLifecycle();
            } catch (error) {
                window.reportActionFailure('Could not cancel the review.', error,
                    () => this.cancelReview());
            }
        },

        async beginReview() {
            try {
                const resp = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/review`, { method: 'POST' });
                const state = await resp.json();
                this.sessionStatus = state.status;
                this.draftBody = state.draftBody || '';
            } catch (error) {
                window.reportActionFailure('Could not begin the review.', error,
                    () => this.beginReview());
            }
        },

        async completeSession(title, body) {
            const t = title || this.draftTitle;
            const b = body || this.draftBody;
            if (!t || !t.trim()) {
                window.reportActionFailure('A session log title is required.', null, null);
                return;
            }
            if (!b || !b.trim()) {
                window.reportActionFailure('A session log body is required.', null, null);
                return;
            }
            try {
                const resp = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/complete`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ title: t.trim(), body: b.trim() }),
                    });
                const result = await resp.json();
                this.sessionStatus = 'IDLE';
                this.presentationMode = 'CURTAIN';
                this.presentedMapId = '';
                this.presentingMap = false;
                this.draftBody = '';
                this.draftTitle = '';
                this.lifecycleOpen = false;
                window.location.href = result.url;
            } catch (error) {
                window.reportActionFailure('Could not complete the session.', error,
                    () => this.completeSession(t, b));
            }
        },

        async setCurrentScene(sceneId) {
            try {
                const resp = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/current-scene`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ sceneId }),
                });
                await resp.json();
                window.location.reload();
            } catch (error) {
                window.reportActionFailure('Could not set the current scene.', error,
                    () => this.setCurrentScene(sceneId));
            }
        },

        async stepScene(direction) {
            try {
                await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/current-scene/step`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ direction }),
                    });
                window.location.reload();
            } catch (error) {
                window.reportActionFailure('Could not step the scene.', error,
                    () => this.stepScene(direction));
            }
        },

        async switchWorkspaceMap(mapId, retry = () => this.switchMap(mapId)) {
            try {
                await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/workspace-map`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ mapId: mapId || null }),
                    });
                return true;
            } catch (error) {
                window.reportActionFailure('Could not switch the workspace map.', error,
                    retry);
                return false;
            }
        },

        async updateAttendance() {
            try {
                const response = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/attendance`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ partyMemberIds: this.attendeeIds }),
                });
                const state = await response.json();
                this.attendeeIds = state.attendeeIds || [];
            } catch (error) {
                window.reportActionFailure('Could not update session attendance.', error,
                    () => this.updateAttendance());
            }
        },

        async sendToTableWithMap(mapId) {
            try {
                await window.dmRequest(`/api/v1/campaigns/${this.campaignId}/table/presentation`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ mode: 'MAP', ref: mapId }),
                });
                this.presentedMapId = mapId;
                this.presentingMap = mapId === this.currentMapId;
                this.presentationMode = 'MAP';
            } catch (error) {
                this.presentingMap = false;
                window.reportActionFailure('Could not show this map to the table.', error,
                    () => this.sendToTableWithMap(mapId));
            }
        },

        async presentHandout(handoutId) {
            if (!handoutId) return;
            try {
                await window.dmRequest(`/api/v1/campaigns/${this.campaignId}/table/presentation`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ mode: 'HANDOUT', ref: handoutId }),
                });
                this.presentingMap = false;
                this.presentedMapId = '';
                this.presentationMode = 'HANDOUT';
            } catch (error) {
                window.reportActionFailure('Could not show this handout to the table.', error,
                    () => this.presentHandout(handoutId));
            }
        },

        closeLifecycle() {
            this.lifecycleOpen = false;
            this.$nextTick(() => this.$refs.sessionButton?.focus());
        },

        lifecycleFocusable(container) {
            return Array.from(container.querySelectorAll(
                'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])'))
                .filter(element => {
                    const style = window.getComputedStyle(element);
                    return !element.hidden && style.display !== 'none'
                        && style.visibility !== 'hidden' && element.getClientRects().length > 0;
                });
        },

        openLifecycle() {
            this.lifecycleOpen = true;
            this.$nextTick(() => {
                const dialog = document.querySelector('[aria-label="Session lifecycle"]');
                if (dialog) this.lifecycleFocusable(dialog)[0]?.focus();
            });
        },

        trapLifecycleFocus(event) {
            const focusable = this.lifecycleFocusable(event.currentTarget);
            if (!focusable.length) return;
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        },

        handleKeyboard(event) {
            if (event.ctrlKey || event.metaKey) return;
            const tag = event.target.tagName;
            const visibleModal = Array.from(document.querySelectorAll('[aria-modal="true"]'))
                .some(element => {
                    const style = window.getComputedStyle(element);
                    return !element.hidden
                        && style.display !== 'none'
                        && style.visibility !== 'hidden'
                        && element.getClientRects().length > 0;
                });
            if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT'
                || event.target.isContentEditable || this.lifecycleOpen
                || visibleModal) return;
            switch (event.key) {
                case '[':
                    event.preventDefault();
                    this.stepScene(-1);
                    break;
                case ']':
                    event.preventDefault();
                    this.stepScene(1);
                    break;
                case 'n':
                    event.preventDefault();
                    this.nextTurn();
                    break;
                case 'q':
                    event.preventDefault();
                    document.querySelector('.quicknotes-form input')?.focus();
                    break;
                case 'h':
                    event.preventDefault();
                    document.getElementById('cockpitHandoutPicker')?.focus();
                    break;
                case 'p':
                    if (!this.currentMapId) break;
                    event.preventDefault();
                    this.sendToTable();
                    break;
            }
        },

        request(url, options = {}) {
            return window.dmRequest(url, options);
        },
        failure(summary, error, retry) {
            window.reportActionFailure(summary, error, retry);
        },

        init() {
            this.sessionStatus = config.sessionStatus || 'IDLE';
            this.presentationMode = config.presentationMode || 'CURTAIN';
            this.presentingMap = config.presentationMode === 'MAP'
                && config.presentedMapId === config.mapId;
            this.presentedMapId = config.presentedMapId || '';
            this.draftBody = config.draftBody || '';
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
            this.loadMaps();
            this.loadPlannedEncounters();
            this.loadActiveEncounter();
            if (config.mapId) {
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
                window.location.reload();
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
                this.activeEncounter = resp.status === 204 ? null : await resp.json();
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
                this.presentedMapId = this.currentMapId;
                this.presentationMode = 'MAP';
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
                this.presentedMapId = '';
                this.presentationMode = 'CURTAIN';
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
        async switchMap(mapId) {
            const bm = window.battleMap;
            const previousMapId = bm?.mapId || this.currentMapId;
            if (!mapId) {
                if (this.sessionStatus === 'IDLE') {
                    this.restoreMapPicker(previousMapId);
                    return;
                }
                if (!await this.switchWorkspaceMap(null, () => this.switchMap(null))) {
                    this.restoreMapPicker(previousMapId);
                    return;
                }
                window.location.href = `/campaigns/${this.campaignId}/session`;
                return;
            }
            if (bm && mapId !== bm.mapId) {
                const switched = await bm.switchToMap(mapId, () => this.switchMap(mapId));
                if (!switched) {
                    this.restoreMapPicker(previousMapId);
                    return;
                }
            }
            if (this.sessionStatus !== 'IDLE'
                && !await this.switchWorkspaceMap(mapId, () => this.switchMap(mapId))) {
                if (bm && previousMapId && bm.mapId !== previousMapId) {
                    await bm.switchToMap(previousMapId);
                }
                this.restoreMapPicker(previousMapId);
                return;
            }
            if (!bm) {
                window.location.href = `/campaigns/${this.campaignId}/session?mapId=${mapId}`;
                return;
            }
            this.currentMapId = mapId;
            if (this.sessionStatus === 'IDLE') this.startMapId = mapId;
            this.visitedMapIds.add(mapId);
            this.presentingMap = this.presentationMode === 'MAP'
                && this.presentedMapId === mapId;
            history.replaceState(null, '', `/campaigns/${this.campaignId}/session?mapId=${mapId}`);
        },

        restoreMapPicker(mapId) {
            this.currentMapId = mapId || '';
            this.$nextTick(() => {
                const picker = document.getElementById('cockpitMapPicker');
                if (picker) picker.value = this.currentMapId;
            });
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

        async followTransition(transitionId) {
            try {
                await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/current-scene/follow-transition`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ transitionId }),
                    });
                window.location.reload();
            } catch (error) {
                window.reportActionFailure('Could not follow the transition.', error,
                    () => this.followTransition(transitionId));
            }
        },

        async setObjectiveStatus(objectiveId, status) {
            try {
                await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/quests/objectives/${objectiveId}/status`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ status }),
                    });
                window.location.reload();
            } catch (error) {
                window.reportActionFailure('Could not update the objective status.', error,
                    () => this.setObjectiveStatus(objectiveId, status));
            }
        },

        nextTurn() {
            window.dispatchEvent(new CustomEvent('tracker-next-turn'));
        },

        openSearch() {
            window.dispatchEvent(new CustomEvent('command-palette-toggle'));
        },

        openDice() {
            window.dispatchEvent(new CustomEvent('dice-roller-toggle'));
        },

        openLinkedTable(tableId, tableName) {
            if (!tableId) return;
            window.dispatchEvent(new CustomEvent('table-roll-open', {
                detail: { tableId, tableName }
            }));
        },

        rollLinkedTable(tableId, tableName) {
            if (!tableId) return;
            window.dispatchEvent(new CustomEvent('table-roll-direct', {
                detail: { tableId, tableName }
            }));
        },
    };
}

window.sessionCockpit = sessionCockpit;
