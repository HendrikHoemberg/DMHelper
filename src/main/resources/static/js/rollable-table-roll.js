(function () {
    'use strict';

    document.addEventListener('alpine:init', () => {
        Alpine.data('tableRollPanel', () => ({
            request: {
                manualValue: null,
                rollCount: 1,
                duplicatePolicy: 'ALLOW_DUPLICATES'
            },
            result: null,
            loading: false,

            async roll() {
                const tableId = this.$el.closest('[data-table-id]')
                    ? this.$el.closest('[data-table-id]').dataset.tableId
                    : window.TABLE_ID;

                const campId = document.body.dataset.campaignId;
                if (!tableId || !campId) return;

                this.loading = true;
                try {
                    const resp = await window.dmRequest(
                        '/api/v1/rollable-tables/' + tableId + '/roll?campaignId=' + encodeURIComponent(campId), {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                manualValue: this.request.manualValue,
                                rollCount: this.request.rollCount,
                                duplicatePolicy: this.request.duplicatePolicy
                            })
                        });

                    this.result = await resp.json();
                    window.dispatchEvent(new CustomEvent('table-roll-result', { detail: this.result }));
                } catch (error) {
                    window.reportActionFailure('Roll failed.', error, () => this.roll());
                } finally {
                    this.loading = false;
                }
            }
        }));

        Alpine.data('tableDraftPanel', () => ({
            draft: null,
            draftName: '',
            draftMapId: '',
            draftCreatures: [],
            draftItems: [],
            draftLoading: false,
            draftConfirmed: false,
            draftDiscarded: false,

            init() {
                window.addEventListener('table-roll-result', (event) => {
                    const detail = event.detail;
                    if (detail && detail.draft) {
                        this.draft = detail.draft;
                        this.draftName = detail.draft.suggestedName || '';
                        this.draftMapId = '';
                        if (detail.draft.creatures) {
                            this.draftCreatures = detail.draft.creatures.map(c => ({
                                statBlockId: c.statBlockId,
                                quantity: c.quantity
                            }));
                        }
                        if (detail.draft.items) {
                            this.draftItems = detail.draft.items.map(i => ({
                                targetId: i.targetId,
                                quantity: i.quantity
                            }));
                        }
                        this.draftConfirmed = false;
                        this.draftDiscarded = false;
                    }
                });
            },

            async confirmEncounterDraft() {
                const campId = document.body.dataset.campaignId;
                const rollId = this.result?.logId;
                if (!campId || !rollId) return;

                this.draftLoading = true;
                try {
                    await window.dmRequest(
                        '/api/v1/rollable-table-rolls/' + rollId + '/encounter/confirm?campaignId=' + encodeURIComponent(campId), {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                name: this.draftName,
                                mapId: this.draftMapId || null,
                                creatures: this.draftCreatures
                            })
                        });
                    this.draftConfirmed = true;
                    window.reportSuccess('Encounter created from table roll.');
                } catch (error) {
                    window.reportActionFailure('Confirm failed.', error, () => this.confirmEncounterDraft());
                } finally {
                    this.draftLoading = false;
                }
            },

            async confirmRewardDraft() {
                const campId = document.body.dataset.campaignId;
                const rollId = this.result?.logId;
                if (!campId || !rollId) return;

                this.draftLoading = true;
                try {
                    await window.dmRequest(
                        '/api/v1/rollable-table-rolls/' + rollId + '/reward/confirm?campaignId=' + encodeURIComponent(campId), {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                items: this.draftItems
                            })
                        });
                    this.draftConfirmed = true;
                    window.reportSuccess('Rewards stashed from table roll.');
                } catch (error) {
                    window.reportActionFailure('Confirm failed.', error, () => this.confirmRewardDraft());
                } finally {
                    this.draftLoading = false;
                }
            },

            async discardDraft() {
                const campId = document.body.dataset.campaignId;
                const rollId = this.result?.logId;
                if (!campId || !rollId) return;

                this.draftLoading = true;
                try {
                    await window.dmRequest(
                        '/api/v1/rollable-table-rolls/' + rollId + '/discard?campaignId=' + encodeURIComponent(campId), {
                            method: 'POST'
                        });
                    this.draftDiscarded = true;
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
