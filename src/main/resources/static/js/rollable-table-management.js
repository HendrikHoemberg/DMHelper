(function () {
    'use strict';

    document.addEventListener('alpine:init', () => {
        Alpine.data('tableManagement', () => ({
            managementLoading: false,
            managementMessage: '',

            tableId() {
                return document.querySelector('[data-table-id]')?.dataset.tableId || window.TABLE_ID;
            },

            /* Confirmations name the entity (spec 14), so the table's name has to reach the
               script; "this table" is the fallback when the panel is rendered without it. */
            tableName() {
                return document.querySelector('[data-table-name]')?.dataset.tableName
                    || 'this table';
            },

            campaignId() {
                return document.body.dataset.campaignId || null;
            },

            async cloneTable() {
                const requestedName = window.prompt('Clone name (leave blank to use “Copy”)', '');
                if (requestedName === null) return;
                const params = new URLSearchParams();
                if (this.campaignId()) params.set('campaignId', this.campaignId());
                if (requestedName.trim()) params.set('name', requestedName.trim());
                this.managementLoading = true;
                this.managementMessage = 'Cloning…';
                try {
                    const response = await window.dmRequest(
                        '/api/v1/rollable-tables/' + this.tableId() + '/clone?' + params.toString(),
                        { method: 'POST' });
                    const clone = await response.json();
                    window.reportSuccess('Table cloned.');
                    window.location.href = '/library/tables/' + clone.id + '/edit';
                } catch (error) {
                    this.managementMessage = 'Clone failed.';
                    window.reportActionFailure('Could not clone the table.', error, () => this.cloneTable());
                } finally {
                    this.managementLoading = false;
                }
            },

            async promoteTable() {
                const promote = await window.dmConfirm({
                    question: 'Promote ' + this.tableName() + ' to the global library?',
                    consequence: 'The table becomes available in every campaign and leaves this'
                        + ' campaign’s table list.',
                    acceptLabel: 'Promote'
                });
                if (!promote) return;
                this.managementLoading = true;
                this.managementMessage = 'Promoting…';
                try {
                    await window.dmRequest(
                        '/api/v1/rollable-tables/' + this.tableId() + '/promote',
                        { method: 'POST' });
                    this.managementMessage = 'Promoted to global library.';
                    window.reportSuccess(this.managementMessage);
                    window.location.reload();
                } catch (error) {
                    this.managementMessage = 'Promotion failed.';
                    window.reportActionFailure('Could not promote the table.', error, () => this.promoteTable());
                } finally {
                    this.managementLoading = false;
                }
            },

            async deleteTable() {
                this.managementLoading = true;
                this.managementMessage = 'Checking dependencies…';
                try {
                    const impactResponse = await window.dmRequest(
                        '/api/v1/rollable-tables/' + this.tableId() + '/deletion-impact');
                    const impact = await impactResponse.json();
                    const count = (impact.dependencies || []).length;
                    const consequence = count > 0
                        ? 'This table has ' + count + ' dependent link(s); each one is replaced'
                            + ' with a marker. Roll history and draft snapshots are kept.'
                        : 'Roll history and draft snapshots are kept.';
                    const confirmed = await window.dmConfirm({
                        question: 'Delete ' + this.tableName() + '?',
                        consequence
                    });
                    if (!confirmed) {
                        this.managementMessage = '';
                        return;
                    }
                    await window.dmRequest(
                        '/api/v1/rollable-tables/' + this.tableId() + '?confirmed=' + (count > 0),
                        { method: 'DELETE' });
                    window.reportSuccess('Table deleted.');
                    const suffix = this.campaignId()
                        ? '?campaignId=' + encodeURIComponent(this.campaignId())
                        : '';
                    window.location.href = '/library/tables' + suffix;
                } catch (error) {
                    this.managementMessage = 'Delete failed.';
                    window.reportActionFailure('Could not delete the table.', error, () => this.deleteTable());
                } finally {
                    this.managementLoading = false;
                }
            }
        }));
    });
})();
