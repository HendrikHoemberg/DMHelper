(function () {
    'use strict';

    document.addEventListener('alpine:init', () => {
        Alpine.data('tableRollPanel', (config = {}) => ({
            tableId: config.tableId || null,
            campaignId: config.campaignId || document.body.dataset.campaignId || null,
            tableName: config.tableName || '',
            compact: Boolean(config.compact),
            open: !Boolean(config.compact),
            request: {
                manualValue: null,
                rollCount: 1,
                duplicatePolicy: 'ALLOW_DUPLICATES'
            },
            result: null,
            rollId: null,
            loading: false,
            draft: null,
            draftName: '',
            draftMapId: '',
            draftCreatures: [],
            draftItems: [],
            draftLoading: false,
            draftConfirmed: false,
            draftDiscarded: false,

            init() {
                if (!this.tableId) {
                    this.tableId = this.$el.dataset.tableId
                        || this.$el.closest('[data-table-id]')?.dataset.tableId
                        || window.TABLE_ID
                        || null;
                }
                window.addEventListener('table-roll-open', event => {
                    this.selectTable(event.detail, false);
                });
                window.addEventListener('table-roll-direct', event => {
                    this.selectTable(event.detail, true);
                });
            },

            async selectTable(detail, rollImmediately) {
                if (!detail?.tableId) return;
                this.tableId = detail.tableId;
                this.tableName = detail.tableName || '';
                this.open = true;
                this.result = null;
                this.rollId = null;
                this.clearDraft();
                if (rollImmediately) await this.roll();
            },

            clearDraft() {
                this.draft = null;
                this.draftName = '';
                this.draftMapId = '';
                this.draftCreatures = [];
                this.draftItems = [];
                this.draftConfirmed = false;
                this.draftDiscarded = false;
            },

            applyResult(result) {
                this.result = result;
                this.rollId = this.result.logId;
                this.tableName = result.tableName || this.tableName;
                this.clearDraft();
                this.draft = result.draft || null;
                if (this.draft) {
                    this.draftName = this.draft.suggestedName || '';
                    this.draftCreatures = (this.draft.creatures || []).map(creature => ({
                        statBlockId: creature.statBlockId,
                        quantity: creature.quantity
                    }));
                    this.draftItems = (this.draft.items || []).map(item => ({
                        targetId: item.targetId,
                        quantity: item.quantity
                    }));
                }
            },

            async roll() {
                if (!this.tableId || !this.campaignId) return;

                this.loading = true;
                try {
                    const response = await window.dmRequest(
                        '/api/v1/rollable-tables/' + this.tableId + '/roll?campaignId='
                        + encodeURIComponent(this.campaignId), {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                manualValue: this.request.manualValue || null,
                                rollCount: Number(this.request.rollCount) || 1,
                                duplicatePolicy: this.request.duplicatePolicy
                            })
                        });

                    this.applyResult(await response.json());
                    window.dispatchEvent(new CustomEvent('table-history-refresh'));
                } catch (error) {
                    window.reportActionFailure('Roll failed.', error, () => this.roll());
                } finally {
                    this.loading = false;
                }
            },

            flattenOutcomes(outcomes = this.result?.outcomes || [], nestedDepth = 0, path = '') {
                const rows = [];
                outcomes.forEach((outcome, index) => {
                    const key = path + index + ':' + (outcome.entryKey || 'result');
                    rows.push({ key, outcome, nestedDepth });
                    rows.push(...this.flattenOutcomes(
                        outcome.nestedRolls || [], nestedDepth + 1, key + '/'));
                });
                return rows;
            },

            referenceUrl(reference) {
                if (!reference?.targetId) return null;
                switch (reference.targetType) {
                    case 'STATBLOCK': return '/library/statblocks/' + reference.targetId;
                    case 'EQUIPMENT_ITEM': return '/library/equipment/' + reference.targetId;
                    case 'MAGIC_ITEM': return '/library/magic-items/' + reference.targetId;
                    case 'ROLLABLE_TABLE': return '/library/tables/' + reference.targetId;
                    case 'NOTE': return '/campaigns/' + this.campaignId + '/notes/' + reference.targetId;
                    case 'ENCOUNTER': return '/campaigns/' + this.campaignId + '/encounters/' + reference.targetId;
                    case 'HANDOUT': return '/campaigns/' + this.campaignId + '/handouts/' + reference.targetId + '/present';
                    default: return null;
                }
            },

            async confirmEncounterDraft() {
                if (!this.campaignId || !this.rollId) return;

                this.draftLoading = true;
                try {
                    await window.dmRequest(
                        '/api/v1/rollable-table-rolls/' + this.rollId + '/encounter/confirm?campaignId='
                        + encodeURIComponent(this.campaignId), {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                name: this.draftName,
                                mapId: this.draftMapId || null,
                                creatures: this.draftCreatures
                            })
                        });
                    this.draftConfirmed = true;
                    window.dispatchEvent(new CustomEvent('table-history-refresh'));
                    window.reportSuccess('Encounter created from table roll.');
                } catch (error) {
                    window.reportActionFailure('Confirm failed.', error, () => this.confirmEncounterDraft());
                } finally {
                    this.draftLoading = false;
                }
            },

            async confirmRewardDraft() {
                if (!this.campaignId || !this.rollId) return;

                this.draftLoading = true;
                try {
                    await window.dmRequest(
                        '/api/v1/rollable-table-rolls/' + this.rollId + '/reward/confirm?campaignId='
                        + encodeURIComponent(this.campaignId), {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ items: this.draftItems })
                        });
                    this.draftConfirmed = true;
                    window.dispatchEvent(new CustomEvent('table-history-refresh'));
                    window.reportSuccess('Rewards stashed from table roll.');
                } catch (error) {
                    window.reportActionFailure('Confirm failed.', error, () => this.confirmRewardDraft());
                } finally {
                    this.draftLoading = false;
                }
            },

            async discardDraft() {
                if (!this.campaignId || !this.rollId) return;

                this.draftLoading = true;
                try {
                    await window.dmRequest(
                        '/api/v1/rollable-table-rolls/' + this.rollId + '/discard?campaignId='
                        + encodeURIComponent(this.campaignId), { method: 'POST' });
                    this.draftDiscarded = true;
                    window.dispatchEvent(new CustomEvent('table-history-refresh'));
                    window.reportSuccess('Table roll draft discarded.');
                } catch (error) {
                    window.reportActionFailure('Discard failed.', error, () => this.discardDraft());
                } finally {
                    this.draftLoading = false;
                }
            }
        }));
    });
})();
