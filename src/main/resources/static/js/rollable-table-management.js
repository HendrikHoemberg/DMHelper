(function () {
    'use strict';

    document.addEventListener('alpine:init', () => {
        Alpine.data('tableManagement', () => ({
            managementLoading: false,
            managementMessage: '',

            tableId() {
                return document.querySelector('[data-table-id]')?.dataset.tableId || window.TABLE_ID;
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
                if (!window.confirm('Promote this table to the global library?')) return;
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
                    const message = count > 0
                        ? 'This table has ' + count + ' dependent link(s). Delete it and replace those references with markers?'
                        : 'Delete this table? Roll history and draft snapshots will be retained.';
                    if (!window.confirm(message)) {
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
