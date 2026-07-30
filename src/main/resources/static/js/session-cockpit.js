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
        activeCombatants: [],
        plannedEncounters: [],
        suspendedEncounters: [],
        lifecycleOpen: false,
        campaignId: config.campaignId || '',
        seedingSceneEncounter: false,
        sessionStatus: config.sessionStatus || 'IDLE',
        startMapId: config.mapId || '',
        attendeeIds: config.attendeeIds || [],
        draftTitle: '',
        draftBody: config.draftBody || '',
        newBody: '',
        threatPinKind: 'TRAP',
        threatPinQuery: '',
        threatPinResults: [],
        selectedThreatForPin: null,
        threatPinLabel: '',
        threatPinX: 0,
        threatPinY: 0,
        threatPins: [],
        tokenDraft: {
            name: '', kind: 'NPC', sizeCols: 1, sizeRows: 1,
            col: 0, row: 0, color: '#2ecc71', hidden: false,
        },
        tokenDialogBusy: false,
        tokenDialogError: '',
        pendingTokenDelete: null,
        tokenDeleteBusy: false,
        annotationText: '',
        annotationPosition: null,
        _replacementPendingId: null,
        _replacementActiveId: null,
        _replacementActiveName: null,
        _readinessEncounterId: null,
        _readinessData: null,
        readinessCanRun: false,

        async mutateSession(path, options, summary, retry) {
            try {
                const response = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session${path}`, options);
                const state = await response.json();
                this.sessionStatus = state.status;
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
                this.draftBody = '';
                this.draftTitle = '';
                this.lifecycleOpen = false;
                window.location.href = result.url;
            } catch (error) {
                window.reportActionFailure('Could not complete the session.', error,
                    () => this.completeSession(t, b));
            }
        },

        // Campaign mutations remain, but all bookkeeping for this run is removed without
        // producing a SESSION_LOG note. Confirm because that session-only history is gone.
        async confirmAbandonSession() {
            if (!window.confirm(
                'Discard this session? No session log is created. Campaign changes remain, '
                + 'but this session\u2019s visits, draft, and audio state are removed.')) {
                return;
            }
            try {
                await this.request(
                    `/api/v1/campaigns/${this.campaignId}/session/abandon`, { method: 'POST' });
                window.location.reload();
            } catch (error) {
                window.cockpitLayout?.showNotice(
                    'The session could not be discarded. Nothing was changed.');
            }
        },

        async setCurrentScene(sceneId) {
            if (!sceneId) return;
            try {
                const resp = await window.dmRequest(
                    `/api/v1/campaigns/${this.campaignId}/session/current-scene`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ sceneId }),
                });
                await resp.json();
                this.refreshModules(['story', 'session-plan'], 'scene-selected');
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
                this.refreshModules(['story', 'session-plan'], 'scene-stepped');
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

        closeLifecycle() {
            const focusAtClose = document.activeElement;
            if (this.$refs.lifecycleDialog?.open) {
                this.$refs.lifecycleDialog.close();
            }
            this.lifecycleOpen = false;
            this.$nextTick(() => {
                if (document.activeElement !== focusAtClose
                    && document.activeElement !== document.body) {
                    this._lastActiveElement = null;
                    return;
                }
                const target = this._lastActiveElement?.isConnected
                    ? this._lastActiveElement
                    : this.$refs.sessionButton;
                target?.focus();
                this._lastActiveElement = null;
            });
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
            this._lastActiveElement = document.activeElement;
            this.lifecycleOpen = true;
            if (this.$refs.lifecycleDialog && !this.$refs.lifecycleDialog.open) {
                this.$refs.lifecycleDialog.showModal();
            }
            this.$nextTick(() => {
                const dialog = this.$refs.lifecycleDialog;
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
                    if (window.cockpitLayout?.revealQuickNotesCapture) {
                        window.cockpitLayout.revealQuickNotesCapture();
                    } else {
                        document.querySelector('.quicknotes-form input')?.focus();
                    }
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
            this.draftBody = config.draftBody || '';
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
                const previousMapId = this.currentMapId;
                this.maps = e.detail.maps;
                this.currentMapId = e.detail.currentMapId;
                this.syncMapPicker();
                this.visitedMapIds.add(this.currentMapId);
                if (this.currentMapId !== previousMapId) {
                    this.refreshThreatPins();
                }
            });
            window.addEventListener('battle-annotation-text-request', (event) => {
                this.openAnnotationDialog(event.detail);
            });
            window.addEventListener('tracker-encounter-state', (e) => {
                this.activeEncounter = e.detail.encounter;
                // combatants may include threatKind/threatId/threatCard for trap/hazard turns;
                // the shared tracker renders the active-turn mechanics card only.
                this.activeCombatants = e.detail.combatants || [];
                if (this.activeEncounter) {
                    this.showTracker = true;
                    this.activeTab = 'tracker';
                }
                // Refresh runtime tokens to get updated HP, bloodied, defeated
                window.battleMap?.fetchTokens();
            });
            window.addEventListener('tracker-encounter-state', (e) => {
                if (!e.detail?.combatants) return;
                for (const c of e.detail.combatants) {
                    const token = this.tokens.find(t => t.combatantId === c.id);
                    if (!token) continue;
                    token.currentHp = c.currentHp;
                    token.maxHp = c.maxHp;
                    token.defeated = c.defeated;
                }
            });
            window.addEventListener('cockpit:module-content-ready', (event) => {
                if (event.detail?.moduleKey !== 'map') return;
                this.syncMapPicker();
                if (!window.battleMap) {
                    // First-time construction: the map fragment (with #battleCanvasWrap) has now
                    // been injected, so the container exists and BattleMap can mount.
                    this.initBattleMap();
                    return;
                }
                requestAnimationFrame(() => {
                    window.battleMap.resizeToContainer();
                    window.battleMap.setRenderingActive(true);
                });
            });
            window.addEventListener('cockpit-encounter-ended', () => {
                this.showTracker = false;
                this.activeEncounter = null;
                this.activeCombatants = [];
                if (window.battleMap) {
                    window.battleMap.activeEncounterId = null;
                    window.battleMap.fetchTokens();
                }
                this.refreshModules(['encounter', 'story'], 'encounter-ended');
                window.dispatchEvent(new CustomEvent('cockpit:module-invalidate', {
                    detail: { moduleKey: 'session-log', reason: 'encounter-ended' }
                }));
            });
            window.addEventListener('cockpit-encounter-wave-changed', () => {
                this.refreshModules(['encounter', 'story'], 'wave-changed');
            });
            window.addEventListener('party-runtime-changed', () => {
                if (window.cockpitModules) {
                    window.cockpitModules.refresh('party', 'party-runtime-changed');
                } else {
                    this.refreshModules(['party'], 'party-runtime-changed');
                }
            });
            window.addEventListener('quicknote-created', () => {
                window.dispatchEvent(new CustomEvent('cockpit:module-invalidate', {
                    detail: { moduleKey: 'session-log', reason: 'quicknote-created' }
                }));
            });
            window.addEventListener('session-lifecycle-changed', () => {
                window.dispatchEvent(new CustomEvent('cockpit:module-invalidate', {
                    detail: { moduleKey: 'session-log', reason: 'lifecycle-changed' }
                }));
            });
            this.loadMaps();
            this.loadPlannedEncounters();
            this.refreshThreatPins();
            this.loadActiveEncounter();
            this._bindMapVisibility();
            if (config.mapId) {
                this.visitedMapIds.add(this.currentMapId);
                this.initBattleMap();
            }
            // Handle runEncounter query parameter
            const params = new URLSearchParams(window.location.search);
            const runEncounterId = params.get('runEncounter');
            if (runEncounterId) {
                history.replaceState(null, '', window.location.pathname);
                this.runEncounter(runEncounterId);
            }
        },

        _bindMapVisibility() {
            if (this._mapVisibilityBound) return;
            this._mapVisibilityBound = true;
            window.addEventListener('cockpit:module-visibility', (event) => {
                if (event.detail?.moduleKey !== 'map') return;
                // Deferred first-time construction when the map module becomes visible.
                if (!window.battleMap && this._pendingMapInit && event.detail.visible) {
                    this._pendingMapInit();
                    return;
                }
                if (!window.battleMap) return;
                window.battleMap.setRenderingActive(Boolean(event.detail.visible));
                if (event.detail.visible) {
                    requestAnimationFrame(() => window.battleMap.resizeToContainer());
                }
            });
        },

        initBattleMap() {
            const container = document.getElementById('battleCanvasWrap');
            if (!container || this._battleMapInitStarted || window.battleMap) return;

            const mapVisible = window.cockpitLayout?.isModuleVisible('map') ?? true;
            if (!mapVisible) {
                // One pending initializer; removed after successful construction.
                if (this._pendingMapInit) return;
                this._pendingMapInit = () => {
                    this._pendingMapInit = null;
                    this._constructBattleMap(container);
                };
                return;
            }
            this._constructBattleMap(container);
        },

        _constructBattleMap(container) {
            if (this._battleMapInitStarted || window.battleMap) return;
            this._battleMapInitStarted = true;
            import('/js/map/battle-map.js').then(mod => {
                if (window.battleMap) return;
                const BattleMap = mod.BattleMap;
                const bm = new BattleMap({
                    container,
                    mapId: config.mapId,
                    campaignId: config.campaignId,
                    gridWidth: config.gridWidth,
                    gridHeight: config.gridHeight,
                    cellSizePx: config.cellSizePx,
                    movementMode: config.movementMode,
                    showGrid: config.showGrid,
                    statusEl: document.getElementById('battleStatusMessage'),
                    saveIndicatorEl: document.getElementById('battleSaveIndicator'),
                    cursorInfoEl: document.getElementById('battleCursorInfo'),
                });
                window.battleMap = bm;
                if (this.activeEncounter) {
                    bm.activeEncounterId = this.activeEncounter.id;
                }
                bm.load().then(() => {
                    const visible = window.cockpitLayout?.isModuleVisible('map') ?? true;
                    bm.setRenderingActive(visible);
                    if (visible) {
                        requestAnimationFrame(() => bm.resizeToContainer());
                    }
                });
            });
        },

        async activateEncounter(encounterId, disposition) {
            const body = disposition ? { activeEncounterDisposition: disposition } : {};
            const resp = await this.request(
                `/api/v1/campaigns/${this.campaignId}/session/encounters/${encounterId}/activate`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body),
                });
            const result = await resp.json();
            if (result.workspaceMapId && result.workspaceMapId !== this.currentMapId) {
                this.currentMapId = result.workspaceMapId;
            }
            if (window.battleMap) {
                window.battleMap.setActiveEncounter(encounterId);
            }
            this.refreshModules(['story', 'encounter', 'map'], 'encounter-activated');
            return result;
        },

        async runEncounter(encounterId) {
            try {
                const readinessResponse = await this.request(`/api/v1/encounters/${encounterId}/readiness`);
                const readiness = await readinessResponse.json();
                // Only ERROR-severity issues block a run, and the server already folds those
                // into canRun. Stopping for warnings stranded the DM in a dialog for things
                // that are not problems: UNPLACED_COMBATANTS is warned about here and then
                // fixed by the auto-placement that activation itself performs.
                if (readiness.canRun === false) {
                    this.showReadinessDialog(readiness, encounterId);
                    return;
                }
                await this.activateEncounter(encounterId, null);
            } catch (e) {
                const problem = e.problem || {};
                if (problem.code === 'ACTIVE_ENCOUNTER_REPLACEMENT_REQUIRED') {
                    this.showReplacementDialog(problem.activeEncounterId, problem.activeEncounterName, encounterId);
                } else if (problem.code === 'ENCOUNTER_NOT_READY') {
                    this.showReadinessDialog(problem.readiness, encounterId);
                } else {
                    throw e;
                }
            }
        },

        showReplacementDialog(activeId, activeName, pendingId) {
            this._replacementPendingId = pendingId;
            this._replacementActiveId = activeId;
            this._replacementActiveName = activeName;
            const dialog = document.getElementById('encounterReplacementDialog');
            if (dialog && !dialog.open) dialog.showModal();
        },

        closeReplacementDialog() {
            const dialog = document.getElementById('encounterReplacementDialog');
            if (dialog?.open) dialog.close();
            this._replacementPendingId = null;
            this._replacementActiveId = null;
            this._replacementActiveName = null;
        },

        async confirmSuspendCurrent() {
            const pendingId = this._replacementPendingId;
            this.closeReplacementDialog();
            if (!pendingId) return;
            await this._activateOrNotify(pendingId, 'SUSPEND');
        },

        async confirmEndCurrent() {
            const pendingId = this._replacementPendingId;
            this.closeReplacementDialog();
            if (!pendingId) return;
            await this._activateOrNotify(pendingId, 'END');
        },

        // A rejected activation used to escape as an unhandled Alpine expression error: the
        // dialog closed, nothing loaded, and the only clue was the status chip reading
        // "Not saved". Failures must reach the DM as words.
        async _activateOrNotify(encounterId, disposition) {
            try {
                await this.activateEncounter(encounterId, disposition);
            } catch (error) {
                const detail = error?.problem?.detail
                    || 'The encounter could not be started. Nothing was changed.';
                window.cockpitLayout?.showNotice(detail);
            }
        },

        showReadinessDialog(readiness, encounterId) {
            this._readinessEncounterId = encounterId;
            this._readinessData = readiness;
            this.readinessCanRun = readiness?.canRun === true;
            const dialog = document.getElementById('encounterReadinessDialog');
            if (dialog && !dialog.open) dialog.showModal();
        },

        closeReadinessDialog() {
            const dialog = document.getElementById('encounterReadinessDialog');
            if (dialog?.open) dialog.close();
            this._readinessEncounterId = null;
            this._readinessData = null;
            this.readinessCanRun = false;
        },

        async confirmRunReady() {
            const encounterId = this._readinessEncounterId;
            const canRun = this._readinessData?.canRun === true;
            this.closeReadinessDialog();
            if (!encounterId || !canRun) return;
            await this.runEncounterWithoutReadinessCheck(encounterId);
        },

        async runEncounterWithoutReadinessCheck(encounterId) {
            try {
                await this.activateEncounter(encounterId, null);
            } catch (e) {
                const problem = e.problem || {};
                if (problem.code === 'ACTIVE_ENCOUNTER_REPLACEMENT_REQUIRED') {
                    this.showReplacementDialog(problem.activeEncounterId, problem.activeEncounterName, encounterId);
                    return;
                }
                throw e;
            }
        },

        openEncounterSetup() {
            const encounterId = this._readinessEncounterId;
            this.closeReadinessDialog();
            if (encounterId) {
                window.open(`/campaigns/${this.campaignId}/encounters/${encounterId}/setup`, '_blank');
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

        dialog(selector) {
            return document.querySelector(selector);
        },

        visibleCenterCell() {
            const bm = window.battleMap;
            if (!bm?.stage) return { col: 0, row: 0 };
            const scale = bm.stage.scaleX();
            const x = (-bm.stage.x() + bm.container.clientWidth / 2) / scale;
            const y = (-bm.stage.y() + bm.container.clientHeight / 2) / scale;
            return {
                col: Math.max(0, Math.min(bm.gridWidth - 1, Math.floor(x / bm.cellSizePx))),
                row: Math.max(0, Math.min(bm.gridHeight - 1, Math.floor(y / bm.cellSizePx))),
            };
        },

        openTokenDialog() {
            if (!window.battleMap) return;
            this._dialogReturnFocus = document.activeElement;
            const center = this.visibleCenterCell();
            this.tokenDraft = {
                name: '', kind: 'NPC', sizeCols: 1, sizeRows: 1,
                col: center.col, row: center.row,
                color: '#2ecc71', hidden: false,
            };
            this.tokenDialogError = '';
            const dialog = this.dialog('[data-token-dialog]');
            if (dialog && !dialog.open) dialog.showModal();
            this.$nextTick(() => dialog?.querySelector('[data-token-name]')?.focus());
        },

        closeDialog(selector) {
            const dialog = this.dialog(selector);
            if (dialog?.open) dialog.close();
            const focus = this._dialogReturnFocus;
            this._dialogReturnFocus = null;
            this.$nextTick(() => focus?.isConnected && focus.focus());
        },

        closeTokenDialog() {
            if (this.tokenDialogBusy) return;
            this.closeDialog('[data-token-dialog]');
        },

        async submitTokenDialog() {
            const bm = window.battleMap;
            if (!bm || this.tokenDialogBusy) return;
            const draft = this.tokenDraft;
            if (!draft.name?.trim()) {
                this.tokenDialogError = 'Enter a token name.';
                return;
            }
            const sizeCols = Math.max(1, Math.min(bm.gridWidth, Number(draft.sizeCols) || 1));
            const sizeRows = Math.max(1, Math.min(bm.gridHeight, Number(draft.sizeRows) || 1));
            const col = Math.max(0, Math.min(bm.gridWidth - sizeCols, Number(draft.col) || 0));
            const row = Math.max(0, Math.min(bm.gridHeight - sizeRows, Number(draft.row) || 0));
            this.tokenDialogBusy = true;
            this.tokenDialogError = '';
            const created = await bm.createToken({
                name: draft.name.trim(),
                kind: draft.kind,
                positionX: col * bm.cellSizePx,
                positionY: row * bm.cellSizePx,
                sizeCols,
                sizeRows,
                color: draft.color,
                hidden: !!draft.hidden,
            });
            this.tokenDialogBusy = false;
            if (created) {
                this.closeTokenDialog();
            } else {
                this.tokenDialogError = 'The token could not be added. Try again.';
            }
        },

        async addParty() { await window.battleMap?.addPartyToMap(); },
        requestTokenDelete(id) {
            this._dialogReturnFocus = document.activeElement;
            this.pendingTokenDelete = this.tokens.find(token => token.id === id) || { id };
            const dialog = this.dialog('[data-token-delete-dialog]');
            if (dialog && !dialog.open) dialog.showModal();
        },
        closeTokenDeleteDialog() {
            if (this.tokenDeleteBusy) return;
            this.pendingTokenDelete = null;
            this.closeDialog('[data-token-delete-dialog]');
        },
        async confirmTokenDelete() {
            if (!this.pendingTokenDelete || this.tokenDeleteBusy) return;
            this.tokenDeleteBusy = true;
            const deleted = await window.battleMap?.deleteToken(this.pendingTokenDelete.id);
            this.tokenDeleteBusy = false;
            if (deleted !== false) this.closeTokenDeleteDialog();
        },
        openAnnotationDialog(position) {
            this._dialogReturnFocus = document.activeElement;
            this.annotationPosition = position;
            this.annotationText = '';
            const dialog = this.dialog('[data-annotation-text-dialog]');
            if (dialog && !dialog.open) dialog.showModal();
            this.$nextTick(() => dialog?.querySelector('[data-annotation-text]')?.focus());
        },
        closeAnnotationDialog() {
            this.annotationPosition = null;
            this.closeDialog('[data-annotation-text-dialog]');
        },
        confirmAnnotationText() {
            if (!this.annotationText?.trim() || !this.annotationPosition) return;
            window.battleMap?.commitAnnotationText(this.annotationPosition, this.annotationText);
            this.closeAnnotationDialog();
        },
        openEncounterEndDialog() {
            this._dialogReturnFocus = document.activeElement;
            const dialog = this.dialog('[data-encounter-end-dialog]');
            if (dialog && !dialog.open) dialog.showModal();
        },
        closeEncounterEndDialog() {
            this.closeDialog('[data-encounter-end-dialog]');
        },
        confirmEncounterEnd() {
            this.closeEncounterEndDialog();
            window.dispatchEvent(new CustomEvent('encounter-end-confirmed'));
        },
        async duplicateToken(id) { await window.battleMap?.duplicateToken(id); },
        async toggleDead() {
            if (this.selectedToken) {
                const requestedDefeated = this.selectedToken.defeated;
                await window.battleMap?.toggleDefeated(
                    this.selectedToken.id, requestedDefeated);
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
                this.syncMapPicker();
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
            history.replaceState(null, '', `/campaigns/${this.campaignId}/session?mapId=${mapId}`);
            await this.refreshThreatPins();
        },

        // The picker and its x-for options may be initialized in either order because the Map
        // module is injected lazily. Alpine's select binding does not revisit a value that had
        // no matching option, so synchronize at both the map-list and module-ready boundaries.
        syncMapPicker() {
            this.$nextTick(() => {
                const picker = document.getElementById('runtimeMapPicker');
                if (!picker) return;
                const expected = String(this.currentMapId || '');
                if (Array.from(picker.options).some(option => option.value === expected)) {
                    picker.value = expected;
                }
            });
        },

        restoreMapPicker(mapId) {
            this.currentMapId = mapId || '';
            this.syncMapPicker();
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
        async addStatblockToken(statblockId) {
            const bm = window.battleMap;
            if (!bm) return;
            let created = false;
            try {
                if (bm.activeEncounterId) {
                    const center = this.visibleCenterCell();
                    await this.request(`/api/v1/encounters/${bm.activeEncounterId}/combatants/from-library`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            statBlockId: statblockId,
                            quantity: 1,
                            startX: center.col * bm.cellSizePx,
                            startY: center.row * bm.cellSizePx,
                        }),
                    });
                    await bm.fetchTokens();
                    bm.renderTokens();
                    bm.emit('tokenupdate', { tokens: bm.tokens });
                    created = true;
                } else {
                    created = await bm.createTokenFromStatblock(statblockId);
                }
            } catch (error) {
                this.failure('Could not add the statblock.', error, () => this.addStatblockToken(statblockId));
            }
            if (created) {
                this.sbSearch = '';
                this.sbResults = [];
            }
        },

        async searchThreatPins() {
            if (!this.threatPinQuery || this.threatPinQuery.length < 1) {
                this.threatPinResults = [];
                return;
            }
            try {
                const base = this.threatPinKind === 'HAZARD' ? '/api/v1/hazards/search' : '/api/v1/traps/search';
                const url = `${base}?campaignId=${encodeURIComponent(this.campaignId || '')}&q=${encodeURIComponent(this.threatPinQuery)}`;
                const resp = await window.dmRequest(url);
                this.threatPinResults = await resp.json();
            } catch (error) {
                window.reportActionFailure('Threat search failed', error, () => this.searchThreatPins());
            }
        },

        selectThreatForPin(item) {
            this.selectedThreatForPin = item;
            this.threatPinResults = [];
            this.threatPinQuery = item.name || '';
            if (!this.threatPinLabel) this.threatPinLabel = item.name || '';
        },

        async refreshThreatPins() {
            if (!this.currentMapId) {
                this.threatPins = [];
                return;
            }
            try {
                const resp = await window.dmRequest(`/api/v1/maps/${this.currentMapId}/pins`);
                const pins = await resp.json();
                this.threatPins = (pins || []).filter(p => p.pinKind === 'THREAT' || p.threatKind);
            } catch (error) {
                window.reportActionFailure('Could not load threat pins.', error, () => this.refreshThreatPins());
            }
        },

        async createThreatPin() {
            if (!this.selectedThreatForPin || !this.currentMapId) return;
            try {
                const label = this.threatPinLabel || this.selectedThreatForPin.name || 'pin';
                const keyBase = label.toString().toLowerCase()
                    .replace(/[^a-z0-9._-]+/g, '-')
                    .replace(/^-+|-+$/g, '')
                    .slice(0, 80) || 'pin';
                const body = {
                    key: `${keyBase}-${Date.now().toString(36)}`.slice(0, 100),
                    threatKind: this.selectedThreatForPin.kind || this.threatPinKind,
                    threatId: this.selectedThreatForPin.id,
                    x: Math.max(0, Math.floor(Number(this.threatPinX) || 0)),
                    y: Math.max(0, Math.floor(Number(this.threatPinY) || 0)),
                    label: this.threatPinLabel || null,
                    sortOrder: 0,
                };
                await window.dmRequest(`/api/v1/maps/${this.currentMapId}/pins`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body),
                });
                this.selectedThreatForPin = null;
                this.threatPinQuery = '';
                this.threatPinLabel = '';
                await this.refreshThreatPins();
                await window.battleMap?.loadPins(this.currentMapId);
                window.showToast?.('Threat pin created', 'success');
            } catch (error) {
                window.reportActionFailure('Could not create threat pin', error, () => this.createThreatPin());
            }
        },

        async deleteThreatPin(pinId) {
            if (!this.currentMapId) return;
            try {
                await window.dmRequest(`/api/v1/maps/${this.currentMapId}/pins/${pinId}`, { method: 'DELETE' });
                await this.refreshThreatPins();
                await window.battleMap?.loadPins(this.currentMapId);
            } catch (error) {
                window.reportActionFailure('Could not delete threat pin', error, () => this.deleteThreatPin(pinId));
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
                this.refreshModules(['story'], 'transition-followed');
            } catch (error) {
                window.reportActionFailure('Could not follow the transition.', error,
                    () => this.followTransition(transitionId));
            }
        },

        async seedCurrentScene(sceneId) {
            if (this.seedingSceneEncounter) return;
            this.seedingSceneEncounter = true;
            let result = null;
            try {
                const response = await this.request(
                    `/api/v1/campaigns/${this.campaignId}/session/scenes/${sceneId}/seed-encounter`,
                    { method: 'POST' });
                result = await response.json();
                this.refreshModules(['story', 'encounter'], 'encounter-seeded');
                const skipped = result.skippedParticipants || [];
                const message = result.alreadyExisted
                    ? `${result.encounterName} was already linked.`
                    : `${result.encounterName}: ${result.combatantsAdded} combatants added`
                        + (skipped.length ? `; skipped: ${skipped.join(', ')}` : '');
                const status = document.getElementById('battleStatusMessage');
                if (status) status.textContent = message;
                window.showToast?.(message, skipped.length ? 'warning' : 'success');
                await this.runEncounter(result.encounterId);
            } catch (error) {
                if (result) {
                    window.reportActionFailure(
                        `${result.encounterName} was created, but the cockpit modules could not refresh.`,
                        error, () => this.refreshModules(['story', 'encounter'], 'encounter-seeded'));
                } else {
                    window.reportActionFailure(
                        'Could not create the scene encounter.', error,
                        () => this.seedCurrentScene(sceneId));
                }
            } finally {
                this.seedingSceneEncounter = false;
            }
        },

        refreshModules(keys, reason) {
            for (const moduleKey of keys) {
                window.dispatchEvent(new CustomEvent('cockpit:module-refresh', {
                    detail: { moduleKey, reason }
                }));
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
                this.refreshModules(['session-plan'], 'objective-mutated');
                window.dispatchEvent(new CustomEvent('cockpit:module-invalidate', {
                    detail: { moduleKey: 'session-log', reason: 'objective-mutated' }
                }));
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
