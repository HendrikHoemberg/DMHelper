(function () {
    'use strict';

    const TABLE_ID = typeof window.TABLE_ID !== 'undefined' ? window.TABLE_ID : null;
    const CAMPAIGN_ID = document.body.dataset.campaignId || null;

    function initialEntries() {
        const entries = window.TABLE_ENTRIES || [{ entryKey: 'entry-0', resultText: '', weight: 1, references: [] }];
        return entries.map((entry, index) => ({
            key: entry.entryKey || entry.key || 'entry-' + index,
            rangeStart: entry.rangeStart ?? null,
            rangeEnd: entry.rangeEnd ?? null,
            weight: entry.weight ?? 1,
            resultText: entry.resultText || '',
            quantityExpression: entry.quantityExpression || null,
            references: (entry.references || []).map(ref => ({
                scope: ref.targetScope || ref.scope || 'ENTITY',
                targetType: ref.targetType,
                targetId: ref.targetId || null,
                catalogRuleset: ref.catalogRuleset || null,
                catalogSourceKey: ref.catalogSourceKey || null,
                displayText: ref.displayText || ''
            }))
        }));
    }

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
                entries: initialEntries()
            },
            problems: [],
            loading: false,
            referencePicker: {
                open: false,
                entryIndex: null,
                type: 'STATBLOCK',
                query: '',
                options: [],
                loading: false,
                error: ''
            },

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

            async showReferencePicker(entryIndex) {
                this.referencePicker.entryIndex = entryIndex;
                this.referencePicker.open = true;
                this.referencePicker.query = '';
                await this.fetchReferenceOptions();
            },

            async fetchReferenceOptions() {
                this.referencePicker.loading = true;
                this.referencePicker.error = '';
                const params = new URLSearchParams({
                    type: this.referencePicker.type,
                    q: this.referencePicker.query
                });
                if (CAMPAIGN_ID) params.set('campaignId', CAMPAIGN_ID);
                try {
                    const response = await window.dmRequest(
                        '/api/v1/rollable-tables/reference-options?' + params.toString());
                    this.referencePicker.options = await response.json();
                } catch (error) {
                    this.referencePicker.options = [];
                    this.referencePicker.error = 'Could not load visible references.';
                    window.reportActionFailure(
                        'Could not load table references.', error, () => this.fetchReferenceOptions());
                } finally {
                    this.referencePicker.loading = false;
                }
            },

            addReference(option) {
                const entry = this.form.entries[this.referencePicker.entryIndex];
                if (!entry) return;
                entry.references.push({
                    scope: 'ENTITY',
                    targetType: option.type,
                    targetId: option.id,
                    catalogRuleset: null,
                    catalogSourceKey: null,
                    displayText: option.label
                });
                this.referencePicker.open = false;
                this.referencePicker.options = [];
            },

            removeReference(entryIndex, refIndex) {
                this.form.entries[entryIndex].references.splice(refIndex, 1);
            },

            problemsFor(path) {
                return this.problems.filter(problem =>
                    problem.path === path || problem.path.startsWith(path + '/'));
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
                            rangeStart: this.form.addressMode === 'RANGE' ? e.rangeStart : null,
                            rangeEnd: this.form.addressMode === 'RANGE' ? e.rangeEnd : null,
                            weight: this.form.addressMode === 'WEIGHTED' ? e.weight : null,
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
                        this.problems = error.problem?.problems || [
                            { code: 'UNKNOWN', path: '', message: error.message }
                        ];
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
