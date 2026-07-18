(function () {
    'use strict';

    const TABLE_ID = typeof window.TABLE_ID !== 'undefined' ? window.TABLE_ID : null;
    const CAMPAIGN_ID = document.body.dataset.campaignId || null;

    document.addEventListener('alpine:init', () => {
        Alpine.data('tableEditor', () => ({
            form: {
                name: window.TABLE_NAME || '',
                sourceKey: window.TABLE_SOURCE_KEY || '',
                description: window.TABLE_DESCRIPTION || '',
                addressMode: window.TABLE_ADDRESS_MODE || 'WEIGHTED',
                rollExpression: window.TABLE_ROLL_EXPRESSION || '',
                category: window.TABLE_CATEGORY || 'GENERIC',
                tags: window.TABLE_TAGS || '',
                entries: window.TABLE_ENTRIES || [{ resultText: '', weight: 1, references: [] }]
            },
            problems: [],
            loading: false,

            addEntry() {
                this.form.entries.push({
                    key: 'entry-' + this.form.entries.length,
                    rangeStart: null,
                    rangeEnd: null,
                    weight: 1,
                    resultText: '',
                    quantityExpression: null,
                    references: []
                });
            },

            removeEntry(index) {
                this.form.entries.splice(index, 1);
            },

            moveEntry(from, to) {
                if (to < 0 || to >= this.form.entries.length) return;
                const entry = this.form.entries.splice(from, 1)[0];
                this.form.entries.splice(to, 0, entry);
            },

            showReferencePicker(entryIndex) {
                // Reference picker to be implemented
            },

            removeReference(entryIndex, refIndex) {
                this.form.entries[entryIndex].references.splice(refIndex, 1);
            },

            async save() {
                this.loading = true;
                this.problems = [];
                try {
                    const body = {
                        name: this.form.name,
                        sourceKey: this.form.sourceKey || this.form.name.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
                        description: this.form.description,
                        addressMode: this.form.addressMode,
                        rollExpression: this.form.rollExpression || null,
                        category: this.form.category,
                        tags: this.form.tags ? this.form.tags.split(',').map(t => t.trim()).filter(t => t) : [],
                        entries: this.form.entries.map((e, i) => ({
                            key: e.key || 'entry-' + i,
                            rangeStart: e.rangeStart,
                            rangeEnd: e.rangeEnd,
                            weight: e.weight,
                            resultText: e.resultText,
                            quantityExpression: e.quantityExpression || null,
                            references: e.references || []
                        }))
                    };

                    const url = TABLE_ID
                        ? '/api/v1/rollable-tables/' + TABLE_ID
                        : '/api/v1/rollable-tables' + (CAMPAIGN_ID ? '?campaignId=' + CAMPAIGN_ID : '');
                    const method = TABLE_ID ? 'PUT' : 'POST';

                    const resp = await window.dmRequest(url, {
                        method: method,
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(body)
                    });

                    const result = await resp.json();
                    window.location.href = '/library/tables/' + result.id;
                } catch (error) {
                    if (error.status === 400) {
                        try {
                            const problemBody = JSON.parse(error.message);
                            this.problems = problemBody.problems || [];
                        } catch (_) {
                            this.problems = [{ code: 'UNKNOWN', path: '', message: error.message }];
                        }
                    } else {
                        window.reportActionFailure('Could not save the table.', error, () => this.save());
                    }
                } finally {
                    this.loading = false;
                }
            }
        }));
    });
})();
