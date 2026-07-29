    /* Condition colors for icon dots */
    const CONDITION_COLORS = {
        blinded: '#8a8578', charmed: '#c25a7c', deafened: '#6e7a80',
        exhaustion: '#7a5b48', frightened: '#8a5fa0', grappled: '#b06038',
        incapacitated: '#85796a', invisible: '#5f9ea0', paralyzed: '#c98a3d',
        petrified: '#8d7a6b', poisoned: '#6f9a4f', prone: '#5a7fa8',
        restrained: '#c9b03d', stunned: '#b06038', unconscious: '#a83a32',
        concentration: '#966a9e',
    };

    /* Hardcoded common conditions (fallback if API unavailable) */
    const COMMON_CONDITIONS = [
        { sourceKey: 'blinded', name: 'Blinded', description: 'A blinded creature can\'t see and automatically fails ability checks that require sight.', defaultDuration: 1 },
        { sourceKey: 'charmed', name: 'Charmed', description: 'A charmed creature can\'t attack the charmer and has advantage on social interactions.', defaultDuration: 1 },
        { sourceKey: 'deafened', name: 'Deafened', description: 'A deafened creature can\'t hear and automatically fails ability checks that require hearing.', defaultDuration: 1 },
        { sourceKey: 'frightened', name: 'Frightened', description: 'A frightened creature has disadvantage on ability checks and attacks while the source is visible.', defaultDuration: 1 },
        { sourceKey: 'grappled', name: 'Grappled', description: 'A grappled creature\'s speed becomes 0. The condition ends if the grappler is incapacitated. The condition also ends if an effect removes the grappled creature from the reach of the grappler.', defaultDuration: 1 },
        { sourceKey: 'incapacitated', name: 'Incapacitated', description: 'An incapacitated creature can\'t take actions or reactions.', defaultDuration: 1 },
        { sourceKey: 'invisible', name: 'Invisible', description: 'An invisible creature is impossible to see without magical aid.', defaultDuration: 1 },
        { sourceKey: 'paralyzed', name: 'Paralyzed', description: 'A paralyzed creature is incapacitated and can\'t move or speak.', defaultDuration: 1 },
        { sourceKey: 'petrified', name: 'Petrified', description: 'A petrified creature is turned to stone and is incapacitated.', defaultDuration: 1 },
        { sourceKey: 'poisoned', name: 'Poisoned', description: 'A poisoned creature has disadvantage on attack rolls and ability checks.', defaultDuration: 1 },
        { sourceKey: 'prone', name: 'Prone', description: 'A prone creature\'s only movement option is crawling. Attack rolls against the creature have advantage if the attacker is within 5 feet. The creature\'s attack rolls have disadvantage.', defaultDuration: 1 },
        { sourceKey: 'restrained', name: 'Restrained', description: 'A restrained creature\'s speed becomes 0 and attacks against it have advantage.', defaultDuration: 1 },
        { sourceKey: 'stunned', name: 'Stunned', description: 'A stunned creature is incapacitated, can\'t move, and speaks falteringly.', defaultDuration: 1 },
        { sourceKey: 'unconscious', name: 'Unconscious', description: 'An unconscious creature is incapacitated, can\'t move or speak, and is unaware.', defaultDuration: 1 },
    ];

    function combatTracker(options = {}) {
        return {
            encounter: null,
            combatants: [],
            activeCombatantId: null,
            selectedCombatantId: null,
            editHp: '',
            editTempHp: 0,
            hpDelta: '',
            rechargePrompts: [],
            conditionsCatalog: [],
            showConditionMenu: null,
            conditionDuration: '',
            waves: [],
            spawnError: '',
            acceptUnset: false,
            setupBusy: false,
            setupSaveCount: 0,
            trackerMode: options.trackerMode || 'STANDARD',
            focusedStatblock: null,
            focusedStatblockLoading: false,

            request(url, options = {}) {
                return window.dmRequest(url, options);
            },

            failure(summary, error, retry) {
                window.reportActionFailure(summary, error, retry);
            },

            async mutate(summary, url, options, afterSuccess) {
                try {
                    const response = await this.request(url, options);
                    if (afterSuccess) await afterSuccess(response);
                    return true;
                } catch (error) {
                    this.failure(summary, error,
                        () => this.mutate(summary, url, options, afterSuccess));
                    return false;
                }
            },

            campaignId: options.campaignId || '',
            _encounterId: null,

            get activeCombatant() {
                if (this.encounter && this.encounter.activeTurnIndex >= 0 &&
                    this.encounter.activeTurnIndex < this.combatants.length) {
                    return this.combatants[this.encounter.activeTurnIndex];
                }
                return null;
            },

            get selected() {
                return this.combatants.find(c => c.id === this.selectedCombatantId) || null;
            },

            get pendingWaves() {
                return (this.waves || []).filter(w => w.status === 'PENDING' || w.status === 'RESERVE');
            },

            get canUndo() {
                return this._encounterId != null && this.combatants.length > 0;
            },

            get lairActionRowVisible() {
                return this.encounter?.lairActionName != null;
            },

            get activeThreatCard() {
                const active = this.activeCombatant;
                if (!active || !active.threatCard) return null;
                return active.threatCard;
            },

            get activeThreatMechanics() {
                const card = this.activeThreatCard;
                if (!card) return null;
                return card.trap || card.hazard || null;
            },

            get inInitiativeSetup() {
                return this.encounter?.combatPhase === 'SETUP';
            },

            get unsetCombatants() {
                return this.combatants.filter(c => c.initiative == null);
            },

            get unsetNpcCount() {
                return this.unsetCombatants.filter(c => c.kind !== 'PC').length;
            },

            get activeSetupCombatants() {
                return this.combatants.filter(c => {
                    const wave = this.waveFor(c);
                    return wave == null || wave.status === 'ACTIVE';
                });
            },

            get canStartCombat() {
                return this.activeSetupCombatants.length > 0
                    && (this.unsetCombatants.length === 0 || this.acceptUnset);
            },

            get initiativeTies() {
                const counts = new Map();
                this.combatants
                    .filter(c => c.initiative != null)
                    .forEach(c => counts.set(c.initiative, (counts.get(c.initiative) || 0) + 1));
                return new Set([...counts.entries()]
                    .filter(([, count]) => count > 1)
                    .map(([initiative]) => initiative));
            },

            waveFor(combatant) {
                return combatant.waveId == null
                    ? null
                    : this.waves.find(w => w.id === combatant.waveId) || null;
            },

            waveLabel(combatant) {
                const wave = this.waveFor(combatant);
                return wave != null && wave.status !== 'ACTIVE'
                    ? `${wave.name} · later wave`
                    : '';
            },

            prefillDice(expression, label) {
                window.dispatchEvent(new CustomEvent('dice-roller-prefill', {
                    detail: { expression: expression || '1d20', label: label || '' }
                }));
            },

            signedBonus(bonus) {
                if (bonus == null) return '';
                return bonus >= 0 ? '+' + bonus : String(bonus);
            },

            async init() {
                window.addEventListener('battle-tokenselect', (e) => {
                    const token = e.detail.token;
                    if (token) {
                        const combatant = this.combatants.find(c => c.tokenId === token.id);
                        if (combatant) this.selectCombatant(combatant.id);
                    }
                });
                this.loadConditionsCatalog();
                if (this.campaignId) {
                    await this.loadActiveEncounter();
                }
            },

            loadConditionsCatalog() {
                this.request('/api/v1/library/conditions')
                    .then(response => response.json())
                    .then(data => {
                        this.conditionsCatalog = data.map(c => ({
                            sourceKey: c.sourceKey,
                            name: c.name,
                            description: c.description || '',
                            defaultDuration: 1,
                        }));
                    })
                    .catch(() => { this.conditionsCatalog = COMMON_CONDITIONS; });
            },

            async loadActiveEncounter() {
                try {
                    const resp = await this.request(`/api/v1/campaigns/${this.campaignId}/encounters/active`);
                    // 204 No Content = no active encounter (empty body is not JSON)
                    if (resp.status === 204) {
                        this.encounter = null;
                        this.combatants = [];
                        this._encounterId = null;
                        return;
                    }
                    const data = await resp.json();
                    if (!data || !data.id) return;
                    await this.loadEncounter(data.id);
                } catch (error) {
                    if (error?.status === 404) return;
                    this.failure('Could not load the active encounter.', error,
                        () => this.loadActiveEncounter());
                }
            },

            async loadEncounter(encounterId) {
                this._encounterId = encounterId;
                this.acceptUnset = false;
                try {
                    const resp = await this.request(`/api/v1/encounters/${encounterId}`);
                    this.encounter = await resp.json();
                    await this.loadWaves();
                    await this.reloadCombatants();
                    this.dispatchState();
                    this.dispatchTurnEvent();
                } catch (error) {
                    this.failure('Could not load the encounter.', error,
                        () => this.loadEncounter(encounterId));
                }
            },

            async reloadCombatants() {
                if (!this._encounterId) return;
                try {
                    const combatantUrl = this.inInitiativeSetup
                        ? `/api/v1/encounters/${this._encounterId}/initiative-setup/combatants`
                        : `/api/v1/encounters/${this._encounterId}/combatants`;
                    const resp = await this.request(combatantUrl);
                    const before = this._capturePositions();
                    this.combatants = await resp.json();
                    this.deriveActiveCombatant();
                    if (this.selected) {
                        this.editHp = this.selected.currentHp;
                        this.editTempHp = this.selected.tempHp || 0;
                    }
                    this._playFlip(before);
                    this._positionTurnMarker();
                } catch (error) {
                    this.failure('Could not reload combatants.', error,
                        () => this.reloadCombatants());
                }
            },

            async loadWaves() {
                if (!this._encounterId) return;
                try {
                    const resp = await this.request(`/api/v1/encounters/${this._encounterId}/waves`);
                    this.waves = await resp.json();
                } catch (error) {
                    // waves are optional; ignore loading failure
                }
            },

            async spawnWave(waveId) {
                this.spawnError = '';
                try {
                    await this.request(`/api/v1/encounters/${this._encounterId}/waves/${waveId}/spawn`, { method: 'POST' });
                    await this.reloadCombatants();
                    await this.loadWaves();
                    window.dispatchEvent(new CustomEvent('cockpit-encounter-wave-changed'));
                } catch (error) {
                    this.spawnError = error?.message || 'Failed to spawn wave';
                }
            },

            _reducedMotion() {
                return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            },

            _rows() {
                return this.$root.querySelectorAll('.combatant-row[data-cid]');
            },

            /* Rows reorder when initiative changes. FLIP: remember where each row
               was, let Alpine re-render, then animate each row from where it used
               to be to where it now is — so the list rearranges instead of
               teleporting (§6.1). */
            _capturePositions() {
                const positions = new Map();
                if (this._reducedMotion()) return positions;
                this._rows().forEach(row => {
                    positions.set(row.dataset.cid, row.getBoundingClientRect().top);
                });
                return positions;
            },

            _playFlip(before) {
                if (!before.size || this._reducedMotion()) return;
                this.$nextTick(() => {
                    this._rows().forEach(row => {
                        const wasAt = before.get(row.dataset.cid);
                        if (wasAt == null) return;
                        const delta = wasAt - row.getBoundingClientRect().top;
                        if (!delta) return;
                        row.animate(
                            [{ transform: `translateY(${delta}px)` }, { transform: 'none' }],
                            { duration: 250, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' },
                        );
                    });
                });
            },

            /* The gold marker slides to whoever is up. */
            _positionTurnMarker() {
                this.$nextTick(() => {
                    const marker = this.$refs.turnMarker;
                    if (!marker) return;
                    const row = this.activeCombatantId
                        ? this.$root.querySelector(`.combatant-row[data-cid="${this.activeCombatantId}"]`)
                        : null;
                    if (!row) {
                        marker.classList.remove('visible');
                        return;
                    }
                    marker.style.height = row.offsetHeight + 'px';
                    marker.style.transform = `translateY(${row.offsetTop}px)`;
                    marker.classList.add('visible');
                });
            },

            /* Damage feels dealt, healing feels granted. */
            _flashHp(combatantId, amount) {
                if (!amount || this._reducedMotion()) return;
                this.$nextTick(() => {
                    const row = this.$root.querySelector(`.combatant-row[data-cid="${combatantId}"]`);
                    if (!row) return;
                    const cls = amount < 0 ? 'hp-damage' : 'hp-heal';
                    row.classList.remove('hp-damage', 'hp-heal');
                    void row.offsetWidth;   // restart the animation if it is already running
                    row.classList.add(cls);
                    setTimeout(() => row.classList.remove(cls), 700);
                });
            },

            _pulseRound() {
                if (this._reducedMotion()) return;
                this.$nextTick(() => {
                    const counter = this.$refs.roundCounter;
                    if (!counter) return;
                    counter.classList.remove('pulse');
                    void counter.offsetWidth;
                    counter.classList.add('pulse');
                    setTimeout(() => counter.classList.remove('pulse'), 400);
                });
            },

            deriveActiveCombatant() {
                if (this.encounter && this.encounter.activeTurnIndex >= 0 &&
                    this.encounter.activeTurnIndex < this.combatants.length) {
                    this.activeCombatantId = this.combatants[this.encounter.activeTurnIndex].id;
                } else {
                    this.activeCombatantId = null;
                }
            },

            async nextTurn() {
                if (!this._encounterId) return;
                const roundBefore = this.encounter?.round;
                await this.mutate(
                    'Could not advance the turn. Initiative was not changed.',
                    `/api/v1/encounters/${this._encounterId}/next-turn`,
                    { method: 'POST' },
                    async response => {
                        this.encounter = await response.json();
                        await this.reloadCombatants();
                        this.rechargePrompts = this.encounter.rechargePrompts || [];
                        if (this.encounter.round !== roundBefore) this._pulseRound();
                        this.dispatchTurnEvent();
                        this.dispatchState();
                    });
            },

            async previousTurn() {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not move to the previous turn. Initiative was not changed.',
                    `/api/v1/encounters/${this._encounterId}/previous-turn`,
                    { method: 'POST' },
                    async response => {
                        this.encounter = await response.json();
                        await this.reloadCombatants();
                        this.dispatchTurnEvent();
                        this.dispatchState();
                    });
            },

            async setInitiative(combatantId, init) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not save initiative. The previous order was kept.',
                    `/api/v1/combatants/${combatantId}/initiative`,
                    {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ initiative: init }),
                    },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async autoRoll() {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not roll initiative. The previous order was kept.',
                    `/api/v1/encounters/${this._encounterId}/auto-roll`,
                    { method: 'POST' },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async saveSetupInitiative(combatantId, rawValue) {
                const initiative = rawValue === '' ? null : Number(rawValue);
                if (initiative !== null && !Number.isInteger(initiative)) return;
                this.setupSaveCount += 1;
                try {
                    await this.setInitiative(combatantId, initiative);
                } finally {
                    this.setupSaveCount -= 1;
                }
            },

            async rollUnsetNpcs() {
                if (!this._encounterId || this.unsetNpcCount === 0
                    || this.setupSaveCount > 0) return;
                this.setupBusy = true;
                try {
                    await this.mutate(
                        'Could not roll unset NPC initiatives. Manual values were kept.',
                        `/api/v1/encounters/${this._encounterId}/auto-roll`,
                        { method: 'POST' },
                        async () => {
                            await this.reloadCombatants();
                            this.dispatchState();
                        });
                } finally {
                    this.setupBusy = false;
                }
            },

            async startCombat() {
                if (!this._encounterId || !this.canStartCombat
                    || this.setupSaveCount > 0) return;
                this.setupBusy = true;
                try {
                    await this.mutate(
                        'Could not start combat. Initiative setup was kept.',
                        `/api/v1/encounters/${this._encounterId}/start-combat`,
                        {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ acceptUnset: this.acceptUnset }),
                        },
                        async response => {
                            this.encounter = await response.json();
                            await this.reloadCombatants();
                            this.deriveActiveCombatant();
                            this.dispatchTurnEvent();
                            this.dispatchState();
                        });
                } finally {
                    this.setupBusy = false;
                }
            },

            async applyHpDelta(combatantId) {
                const amount = parseInt(this.hpDelta, 10);
                if (isNaN(amount) || amount === 0) return;
                const succeeded = await this.quickHp(combatantId, amount);
                if (succeeded) this.hpDelta = '';
            },

            async quickHp(combatantId, amount) {
                if (!this._encounterId) return false;
                return this.mutate(
                    'Could not change hit points. The previous value was kept.',
                    `/api/v1/combatants/${combatantId}/damage`,
                    {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ amount }),
                    },
                    async () => {
                        await this.reloadCombatants();
                        this._flashHp(combatantId, amount);
                        this.dispatchState();
                    });
            },

            async markDefeated(combatantId, defeated) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not change the defeated status. The tracker was kept unchanged.',
                    `/api/v1/combatants/${combatantId}/defeated`,
                    {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ defeated }),
                    },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async toggleCondition(combatantId, sourceKey, duration) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not toggle the condition. The tracker was kept unchanged.',
                    `/api/v1/combatants/${combatantId}/conditions`,
                    {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ sourceKey, durationRounds: duration || 1 }),
                    },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async removeCondition(combatantId, sourceKey) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not remove the condition. The tracker was kept unchanged.',
                    `/api/v1/combatants/${combatantId}/conditions/${sourceKey}/remove`,
                    { method: 'POST' },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async setHp() {
                if (!this.selected || !this._encounterId) return;
                const hp = parseInt(this.editHp, 10);
                if (isNaN(hp)) return;
                const combatantId = this.selected.id;
                const delta = hp - this.selected.currentHp;
                await this.mutate(
                    'Could not save hit points. The previous value was kept.',
                    `/api/v1/combatants/${combatantId}/hp`,
                    {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ currentHp: hp, tempHp: this.editTempHp }),
                    },
                    async () => {
                        await this.reloadCombatants();
                        this._flashHp(combatantId, delta);
                        this.dispatchState();
                    });
            },

            async removeCombatant(combatantId) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not remove the combatant. The tracker was kept unchanged.',
                    `/api/v1/combatants/${combatantId}`,
                    { method: 'DELETE' },
                    async () => {
                        if (this.selectedCombatantId === combatantId) {
                            this.selectedCombatantId = null;
                        }
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async activateLairAction() {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not record the lair action.',
                    `/api/v1/encounters/${this._encounterId}/lair-action`,
                    { method: 'POST' },
                    async () => {
                        await this.loadEncounter(this._encounterId);
                    });
            },

            async undo() {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not undo the last action. The tracker was kept unchanged.',
                    `/api/v1/encounters/${this._encounterId}/undo`,
                    { method: 'POST' },
                    async () => {
                        await this.loadEncounter(this._encounterId);
                    });
            },

            async endEncounter() {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not end the encounter. It remains active.',
                    `/api/v1/encounters/${this._encounterId}/end`,
                    { method: 'POST' },
                    async () => {
                        this.encounter = null;
                        this.combatants = [];
                        this.activeCombatantId = null;
                        this.selectedCombatantId = null;
                        this._encounterId = null;
                        this.dispatchState();
                        window.dispatchEvent(new CustomEvent('cockpit-encounter-ended'));
                    });
            },

            hpPercent(c) {
                if (!c || !c.maxHp || c.maxHp <= 0) return 0;
                return Math.max(0, Math.min(100, (c.currentHp / c.maxHp) * 100));
            },

            hpLabel(c) {
                if (!c || c.currentHp == null) return '—';
                if (!c.maxHp) return String(c.currentHp);
                return c.currentHp + '/' + c.maxHp;
            },

            conditionColor(sourceKey) {
                return CONDITION_COLORS[sourceKey] || '#c9a35c';
            },

            selectCombatant(id) {
                this.selectedCombatantId = id;
                this.showConditionMenu = null;
                if (this.selected) {
                    this.editHp = this.selected.currentHp;
                    this.editTempHp = this.selected.tempHp || 0;
                }
                if (this.trackerMode === 'FOCUSED' && this.selected?.statBlockId) {
                    this.loadFocusedStatblock(this.selected.statBlockId);
                }
            },

            async loadFocusedStatblock(statBlockId) {
                if (!statBlockId) return;
                this.focusedStatblockLoading = true;
                try {
                    const resp = await this.request(`/api/v1/library/statblocks/${statBlockId}`);
                    this.focusedStatblock = await resp.json();
                } catch (error) {
                    this.focusedStatblock = null;
                } finally {
                    this.focusedStatblockLoading = false;
                }
            },

            async showStatblock(combatantId) {
                const resp = await window.dmRequest(
                    `/api/v1/encounters/${this._encounterId}/combatants/${combatantId}/statblock`);
                if (resp.status === 204) return;
                const sb = await resp.json();
                window.dispatchEvent(new CustomEvent('cockpit:show-reference', {
                    detail: { type: 'statblock', id: sb.statblockId, name: sb.name }
                }));
            },

            openConditionMenu(id) {
                this.showConditionMenu = this.showConditionMenu === id ? null : id;
                this.conditionDuration = '';
            },

            hasCondition(combatant, sourceKey) {
                return combatant?.conditions?.some(c => c.sourceKey === sourceKey) || false;
            },

            getConditionText(sourceKey) {
                const cond = this.conditionsCatalog.find(c => c.sourceKey === sourceKey);
                return cond ? `${cond.name}: ${cond.description}` : sourceKey;
            },

            groupCount(groupId) {
                return this.combatants.filter(c => c.groupId === groupId).length;
            },

            async setConcentration(combatantId, spellName) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not set concentration. The previous value was kept.',
                    `/api/v1/combatants/${combatantId}/concentration`,
                    {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ spellName: spellName || null }),
                    },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async resolveConcentrationCheck(combatantId, passed) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not record the concentration check.',
                    `/api/v1/combatants/${combatantId}/concentration-check`,
                    {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ passed }),
                    },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async useLegendaryAction(combatantId) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not record the legendary action.',
                    `/api/v1/combatants/${combatantId}/legendary-action`,
                    { method: 'POST' },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async useLegendaryResistance(combatantId) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not record the legendary resistance.',
                    `/api/v1/combatants/${combatantId}/legendary-resistance`,
                    { method: 'POST' },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async resolveRecharge(combatantId, abilityName, rollResult) {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not record the recharge check.',
                    `/api/v1/combatants/${combatantId}/recharge-check`,
                    {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ abilityName, rollResult }),
                    },
                    async () => {
                        this.rechargePrompts = this.rechargePrompts.filter(p => p.abilityName !== abilityName);
                    });
            },

            dispatchState() {
                window.dispatchEvent(new CustomEvent('tracker-encounter-state', {
                    detail: { encounter: this.encounter, combatants: this.combatants }
                }));
                window.dispatchEvent(new CustomEvent('tracker-conditions-changed', {
                    detail: { combatants: this.combatants }
                }));
            },

            addForm: {
                name: '',
                maxHp: '',
                kind: 'NPC',
            },

            async quickAdd() {
                if (!this._encounterId || !this.addForm.name) return;
                await this.mutate(
                    'Could not add combatant.',
                    `/api/v1/encounters/${this._encounterId}/combatants`,
                    {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            name: this.addForm.name,
                            maxHp: parseInt(this.addForm.maxHp, 10) || 10,
                            kind: this.addForm.kind,
                        }),
                    },
                    async () => {
                        this.addForm = { name: '', maxHp: '', kind: 'NPC' };
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            async addMissingParty() {
                if (!this._encounterId) return;
                await this.mutate(
                    'Could not add missing party members.',
                    `/api/v1/encounters/${this._encounterId}/prefill/party`,
                    { method: 'POST' },
                    async () => {
                        await this.reloadCombatants();
                        this.dispatchState();
                    });
            },

            dispatchTurnEvent() {
                if (this.activeCombatantId) {
                    window.dispatchEvent(new CustomEvent('tracker-active-turn', {
                        detail: { combatantId: this.activeCombatantId }
                    }));
                }
            },
        };
    }
