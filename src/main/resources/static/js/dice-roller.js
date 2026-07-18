/**
 * Shared Alpine diceRoller component for navbar and session cockpit shells.
 * Prefill is advisory: expression is set but never auto-submitted.
 */
document.addEventListener('alpine:init', () => {
    Alpine.data('diceRoller', () => ({
        open: false,
        expression: '',
        advantage: false,
        disadvantage: false,
        result: null,
        history: [],
        loading: false,
        _encounterId: null,

        async init() {
            await this.loadHistory();
            window.addEventListener('tracker-encounter-state', (e) => {
                const encId = e.detail?.encounter?.id || null;
                this._encounterId = encId;
                document.body.dataset.activeEncounterId = encId || '';
            });

            window.addEventListener('dice-roller-toggle', () => { this.open = !this.open; });
            window.addEventListener('table-history-refresh', () => this.loadHistory());
            window.addEventListener('dice-roller-prefill', (event) => {
                this.expression = (event.detail && event.detail.expression) || '';
                this.advantage = false;
                this.disadvantage = false;
                this.result = null;
                this.open = true;
                this.$nextTick(() => this.$refs.expressionInput && this.$refs.expressionInput.focus());
            });
            this.$watch('open', (isOpen) => {
                document.getElementById('diceToggle')?.classList.toggle('active', isOpen);
            });
        },

        async roll() {
            let expr = this.expression.trim();
            if (!expr) return;

            if (this.advantage && !expr.toLowerCase().includes('adv')) {
                expr = expr + ' adv';
            } else if (this.disadvantage && !expr.toLowerCase().includes('dis')) {
                expr = expr + ' dis';
            }

            this.loading = true;
            try {
                const campId = document.body.dataset.campaignId;
                const body = { expression: expr, campaignId: campId };
                if (this._encounterId) body.encounterId = this._encounterId;

                const resp = await window.dmRequest('/api/v1/roll', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });

                this.result = await resp.json();
                this.$nextTick(() => {
                    const el = this.$refs.resultBox;
                    if (el) {
                        el.classList.remove('bounce');
                        void el.offsetWidth;
                        el.classList.add('bounce');
                    }
                });
                window.dispatchEvent(new CustomEvent('dice-roll-result', {
                    detail: this.result
                }));
                await this.loadHistory();
                this.expression = '';
                this.advantage = false;
                this.disadvantage = false;
            } catch (error) {
                window.reportActionFailure('The dice roll was not saved.', error, () => this.roll());
            } finally {
                this.loading = false;
            }
        },

        /* Natural 20 flashes gold, natural 1 flashes ember (§6.5). It is the die
           that has to be natural, not the total: a +7 modifier does not make a
           critical hit. With advantage or disadvantage the used value is the one
           the engine kept, so read the same end of the pair it did. */
        critClass(rolls, advantage, disadvantage) {
            const d20 = (rolls || []).find(r => r.die === 'd20');
            if (!d20 || !d20.values || !d20.values.length) return '';

            let used;
            if ((advantage || disadvantage) && d20.values.length === 2) {
                used = advantage ? Math.max(...d20.values) : Math.min(...d20.values);
            } else if (d20.values.length === 1) {
                used = d20.values[0];
            } else {
                return '';
            }

            if (used === 20) return 'crit-high';
            if (used === 1) return 'crit-low';
            return '';
        },

        /* History rows come back from the API with `rolls` still a JSON string. */
        historyCritClass(item) {
            if (!item || !item.rolls) return '';
            let rolls = item.rolls;
            if (typeof rolls === 'string') {
                try { rolls = JSON.parse(rolls); } catch (e) { return ''; }
            }
            return this.critClass(rolls, item.advantage, item.disadvantage);
        },

        async loadHistory() {
            try {
                const campId = document.body.dataset.campaignId;
                const resp = await window.dmRequest('/api/v1/roll/history?campaignId=' + encodeURIComponent(campId));
                this.history = await resp.json();
            } catch (error) {
                window.reportActionFailure('Could not refresh dice history.', error, () => this.loadHistory());
            }
        }
    }));
});
