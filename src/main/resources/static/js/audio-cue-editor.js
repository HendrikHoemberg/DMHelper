(function () {
    'use strict';

    const CUE_ID = typeof window.CUE_ID !== 'undefined' ? window.CUE_ID : null;
    const CAMPAIGN_ID = typeof window.CUE_CAMPAIGN_ID !== 'undefined' ? window.CUE_CAMPAIGN_ID
        : (document.body && document.body.dataset ? document.body.dataset.campaignId || null : null);
    const API_BASE = '/api/v1/campaigns/' + CAMPAIGN_ID + '/audio/cues';
    const LIST_HREF = '/campaigns/' + CAMPAIGN_ID + '/audio/cues';
    const EXISTING = window.CUE_DTO || null;

    function initialForm() {
        const dto = EXISTING || {};
        return {
            cueKey: dto.cueKey || '',
            name: dto.name || '',
            providerId: dto.providerId || '',
            providerReference: dto.providerReference || '',
            category: dto.category || 'AMBIENT',
            transitionPreference: dto.transitionPreference || 'CROSSFADE',
            volumeHint: dto.volumeHint ?? null,
            durationSeconds: dto.durationSeconds ?? null,
            cachedTitle: dto.cachedTitle || '',
            artistOrOwner: dto.artistOrOwner || '',
            artworkUrl: dto.artworkUrl || '',
            notes: dto.notes || ''
        };
    }

    function audioCueEditor() {
        return {
            form: initialForm(),
            problems: [],
            loading: false,
            unsupportedMessage: '',

            save() {
                this.loading = true;
                this.problems = [];
                this.unsupportedMessage = '';
                const method = CUE_ID ? 'PUT' : 'POST';
                const url = CUE_ID ? API_BASE + '/' + CUE_ID : API_BASE;
                fetch(url, {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.form)
                })
                .then(res => {
                    if (res.ok) {
                        window.location.href = LIST_HREF;
                    } else if (res.status === 400) {
                        return res.json().then(data => {
                            this.problems = data.problems || [];
                        });
                    } else {
                        this.unsupportedMessage = 'Server error: ' + res.status;
                    }
                })
                .catch(err => {
                    this.unsupportedMessage = 'Network error: ' + err.message;
                })
                .finally(() => {
                    this.loading = false;
                });
            },

            problemsFor(path) {
                return this.problems.filter(p => p.path === path || p.path.startsWith(path + '/'));
            }
        };
    }

    function audioCueManagement() {
        return {
            managementLoading: false,
            managementMessage: '',

            cloneCue() {
                this.managementLoading = true;
                this.managementMessage = '';
                const newKey = prompt('Enter cue key for clone:');
                if (!newKey) {
                    this.managementLoading = false;
                    return;
                }
                fetch(API_BASE + '/' + CUE_ID + '/clone?cueKey=' + encodeURIComponent(newKey), {
                    method: 'POST'
                })
                .then(res => {
                    if (res.ok) {
                        window.location.reload();
                    } else {
                        this.managementMessage = 'Clone failed';
                    }
                })
                .catch(() => {
                    this.managementMessage = 'Network error';
                })
                .finally(() => {
                    this.managementLoading = false;
                });
            },

            deleteCue() {
                this.managementLoading = true;
                this.managementMessage = '';
                fetch(API_BASE + '/' + CUE_ID + '/deletion-impact')
                .then(res => res.json())
                .then(impact => {
                    if (impact.hasDependents) {
                        const msg = impact.dependencies.map(d => d.kind + ': ' + d.label).join(', ');
                        if (!confirm('This cue has dependents:\n' + msg + '\n\nDelete anyway?')) {
                            this.managementLoading = false;
                            return;
                        }
                    } else if (!confirm('Delete this audio cue?')) {
                        this.managementLoading = false;
                        return;
                    }
                    return fetch(API_BASE + '/' + CUE_ID + '?confirmed=true', { method: 'DELETE' });
                })
                .then(res => {
                    if (res && res.ok) {
                        window.location.href = LIST_HREF;
                    } else if (res && !res.ok) {
                        this.managementMessage = 'Delete failed';
                    }
                })
                .catch(() => {
                    this.managementMessage = 'Network error';
                })
                .finally(() => {
                    this.managementLoading = false;
                });
            }
        };
    }

    window.audioCueEditor = audioCueEditor;
    window.audioCueManagement = audioCueManagement;
})();
