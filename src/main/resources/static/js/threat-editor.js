(function () {
    'use strict';

    const KIND = typeof window.THREAT_KIND !== 'undefined' ? window.THREAT_KIND : 'TRAP';
    const THREAT_ID = typeof window.THREAT_ID !== 'undefined' ? window.THREAT_ID : null;
    const API_BASE = typeof window.THREAT_API_BASE !== 'undefined' ? window.THREAT_API_BASE : '/api/v1/traps';
    const LIST_HREF = typeof window.THREAT_LIST_HREF !== 'undefined' ? window.THREAT_LIST_HREF : '/library/traps';
    const CAMPAIGN_ID = document.body && document.body.dataset ? document.body.dataset.campaignId || null : null;
    const EXISTING = window.THREAT_DTO || null;

    const ALL_DAMAGE_TYPES = [
        'ACID', 'BLUDGEONING', 'COLD', 'FIRE', 'FORCE', 'LIGHTNING', 'NECROTIC',
        'PIERCING', 'POISON', 'PSYCHIC', 'RADIANT', 'SLASHING', 'THUNDER'
    ];

    let keySeq = 0;
    function nextKey(prefix) {
        keySeq += 1;
        return (prefix || 'k') + '-' + keySeq;
    }

    function emptyCheck() {
        return { mode: '', ability: '', skill: '', dc: null };
    }

    function fromCheck(check) {
        if (!check) return emptyCheck();
        return {
            mode: check.mode || '',
            ability: check.ability || '',
            skill: check.skill || '',
            dc: check.dc ?? null
        };
    }

    function toCheckWrite(check) {
        if (!check) return null;
        const empty = !check.mode && !check.ability && !check.skill && (check.dc === null || check.dc === undefined || check.dc === '');
        if (empty) return null;
        return {
            mode: check.mode || null,
            ability: check.ability || null,
            skill: check.skill || null,
            dc: check.dc === '' || check.dc === undefined ? null : check.dc
        };
    }

    function initialForm() {
        const dto = EXISTING || {};
        return {
            name: dto.name || '',
            sourceKey: dto.sourceKey || '',
            description: dto.description || '',
            severity: dto.severity || 'DANGEROUS',
            minLevel: dto.minLevel ?? null,
            maxLevel: dto.maxLevel ?? null,
            triggerDescription: dto.triggerDescription || '',
            triggerAreaHint: dto.triggerAreaHint || '',
            detectionPassiveThreshold: dto.detectionPassiveThreshold ?? null,
            detectionCheck: fromCheck(dto.detectionCheck),
            disarmMethods: (dto.disarmMethods || []).map((m, i) => ({
                _key: nextKey('disarm'),
                key: m.key || 'method-' + i,
                label: m.label || '',
                ability: m.ability || '',
                skill: m.skill || '',
                tool: m.tool || '',
                dc: m.dc ?? null,
                failureConsequence: m.failureConsequence || '',
                sortOrder: m.sortOrder ?? i
            })),
            attackBonus: dto.attackBonus ?? null,
            save: fromCheck(dto.save),
            damageExpression: dto.damageExpression || '',
            damageTypes: Array.isArray(dto.damageTypes) ? dto.damageTypes.slice() : [],
            additionalEffect: dto.additionalEffect || '',
            resetMode: dto.resetMode || 'NONE',
            resetTiming: dto.resetTiming || '',
            statBlockId: dto.statBlockId || null,
            statBlockLabel: dto.statBlockLabel || '',
            countermeasureNotes: dto.countermeasureNotes || '',
            exposureMode: dto.exposureMode || 'ON_ENTER',
            exposureText: dto.exposureText || '',
            areaHint: dto.areaHint || '',
            check: fromCheck(dto.check),
            escalationText: dto.escalationText || '',
            endingConditions: dto.endingConditions || '',
            references: (dto.references || []).map((r, i) => ({
                _key: nextKey('ref'),
                role: r.role || 'CONDITION',
                targetType: r.targetType,
                targetId: r.targetId,
                displayText: r.displayText || ''
            }))
        };
    }

    /** Build TrapWrite shape for the API. */
    function toTrapWrite(form) {
        return {
            sourceKey: form.sourceKey || form.name.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
            name: form.name,
            description: form.description || null,
            severity: form.severity,
            minLevel: form.minLevel === '' ? null : form.minLevel,
            maxLevel: form.maxLevel === '' ? null : form.maxLevel,
            triggerDescription: form.triggerDescription || null,
            triggerAreaHint: form.triggerAreaHint || null,
            detectionPassiveThreshold: form.detectionPassiveThreshold === '' ? null : form.detectionPassiveThreshold,
            detectionCheck: toCheckWrite(form.detectionCheck),
            disarmMethods: (form.disarmMethods || []).map((m, i) => ({
                key: m.key || 'method-' + i,
                label: m.label,
                ability: m.ability || null,
                skill: m.skill || null,
                tool: m.tool || null,
                dc: m.dc === '' || m.dc === undefined ? null : m.dc,
                failureConsequence: m.failureConsequence || null,
                sortOrder: i
            })),
            attackBonus: form.attackBonus === '' ? null : form.attackBonus,
            save: toCheckWrite(form.save),
            damageExpression: form.damageExpression || null,
            damageTypes: form.damageTypes || [],
            additionalEffect: form.additionalEffect || null,
            resetMode: form.resetMode || 'NONE',
            resetTiming: form.resetTiming || null,
            statBlockId: form.statBlockId || null,
            countermeasureNotes: form.countermeasureNotes || null,
            references: (form.references || []).map(r => ({
                role: r.role,
                targetType: r.targetType,
                targetId: r.targetId,
                displayText: r.displayText || null
            }))
        };
    }

    /** Build HazardWrite shape for the API. */
    function toHazardWrite(form) {
        return {
            sourceKey: form.sourceKey || form.name.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
            name: form.name,
            description: form.description || null,
            severity: form.severity,
            minLevel: form.minLevel === '' ? null : form.minLevel,
            maxLevel: form.maxLevel === '' ? null : form.maxLevel,
            exposureMode: form.exposureMode,
            exposureText: form.exposureText || null,
            areaHint: form.areaHint || null,
            check: toCheckWrite(form.check),
            damageExpression: form.damageExpression || null,
            damageTypes: form.damageTypes || [],
            escalationText: form.escalationText || null,
            endingConditions: form.endingConditions || null,
            references: (form.references || []).map(r => ({
                role: r.role,
                targetType: r.targetType,
                targetId: r.targetId,
                displayText: r.displayText || null
            }))
        };
    }

    document.addEventListener('alpine:init', () => {
        Alpine.data('threatEditor', () => ({
            kind: KIND,
            form: initialForm(),
            problems: [],
            loading: false,
            allDamageTypes: ALL_DAMAGE_TYPES,
            referencePicker: {
                open: false,
                type: 'CONDITION',
                mode: 'reference', // 'reference' | 'statblock'
                query: '',
                options: [],
                loading: false,
                error: ''
            },

            addDisarmMethod() {
                this.form.disarmMethods.push({
                    _key: nextKey('disarm'),
                    key: 'method-' + this.form.disarmMethods.length,
                    label: '',
                    ability: '',
                    skill: '',
                    tool: '',
                    dc: null,
                    failureConsequence: '',
                    sortOrder: this.form.disarmMethods.length
                });
            },

            removeDisarmMethod(index) {
                this.form.disarmMethods.splice(index, 1);
            },

            moveDisarmMethod(from, to) {
                if (to < 0 || to >= this.form.disarmMethods.length) return;
                const method = this.form.disarmMethods.splice(from, 1)[0];
                this.form.disarmMethods.splice(to, 0, method);
            },

            toggleDamageType(dt) {
                const idx = this.form.damageTypes.indexOf(dt);
                if (idx >= 0) this.form.damageTypes.splice(idx, 1);
                else this.form.damageTypes.push(dt);
            },

            async showReferencePicker(type) {
                this.referencePicker.type = type;
                this.referencePicker.mode = type === 'STATBLOCK' ? 'statblock' : 'reference';
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
                        API_BASE + '/reference-options?' + params.toString());
                    this.referencePicker.options = await response.json();
                } catch (error) {
                    this.referencePicker.options = [];
                    this.referencePicker.error = 'Could not load visible references.';
                    window.reportActionFailure(
                        'Could not load threat references.', error, () => this.fetchReferenceOptions());
                } finally {
                    this.referencePicker.loading = false;
                }
            },

            addReference(option) {
                if (this.referencePicker.mode === 'statblock') {
                    this.form.statBlockId = option.id;
                    this.form.statBlockLabel = option.label;
                    this.referencePicker.open = false;
                    this.referencePicker.options = [];
                    return;
                }
                const role = option.type === 'CONDITION' ? 'CONDITION' : 'SALVAGE_ITEM';
                this.form.references.push({
                    _key: nextKey('ref'),
                    role: role,
                    targetType: option.type,
                    targetId: option.id,
                    displayText: option.label
                });
                this.referencePicker.open = false;
                this.referencePicker.options = [];
            },

            removeReference(index) {
                this.form.references.splice(index, 1);
            },

            problemsFor(path) {
                return this.problems.filter(problem =>
                    problem.path === path || (problem.path && problem.path.startsWith(path + '/')));
            },

            async save() {
                this.loading = true;
                this.problems = [];
                try {
                    // Emit separate TrapWrite / HazardWrite shapes
                    const body = this.kind === 'TRAP'
                        ? toTrapWrite(this.form)
                        : toHazardWrite(this.form);

                    const url = THREAT_ID
                        ? API_BASE + '/' + THREAT_ID
                        : API_BASE + (CAMPAIGN_ID ? '?campaignId=' + CAMPAIGN_ID : '');
                    const method = THREAT_ID ? 'PUT' : 'POST';

                    const resp = await window.dmRequest(url, {
                        method: method,
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(body)
                    });

                    const result = await resp.json();
                    const listBase = this.kind === 'TRAP' ? '/library/traps/' : '/library/hazards/';
                    window.location.href = listBase + result.id;
                } catch (error) {
                    if (error.status === 400) {
                        this.problems = error.problem?.problems || [
                            { code: 'UNKNOWN', path: '', message: error.message }
                        ];
                    } else {
                        window.reportActionFailure('Could not save the threat.', error, () => this.save());
                    }
                } finally {
                    this.loading = false;
                }
            }
        }));

        Alpine.data('threatManagement', () => ({
            managementLoading: false,
            managementMessage: '',

            /* Confirmations name the entity (spec 14). */
            threatName() {
                return document.querySelector('[data-threat-name]')?.dataset.threatName
                    || 'this threat';
            },

            threatId() {
                return document.querySelector('[data-threat-id]')?.dataset.threatId || window.THREAT_ID;
            },

            apiBase() {
                return document.querySelector('[data-api-base]')?.dataset.apiBase || API_BASE;
            },

            listHref() {
                return document.querySelector('[data-list-href]')?.dataset.listHref || LIST_HREF;
            },

            campaignId() {
                return document.body.dataset.campaignId || null;
            },

            async cloneThreat() {
                const requestedName = window.prompt('Clone name (leave blank to use “Copy”)', '');
                if (requestedName === null) return;
                const params = new URLSearchParams();
                if (this.campaignId()) params.set('campaignId', this.campaignId());
                if (requestedName.trim()) params.set('name', requestedName.trim());
                this.managementLoading = true;
                this.managementMessage = 'Cloning…';
                try {
                    const response = await window.dmRequest(
                        this.apiBase() + '/' + this.threatId() + '/clone?' + params.toString(),
                        { method: 'POST' });
                    const clone = await response.json();
                    window.reportSuccess('Threat cloned.');
                    const listBase = this.listHref();
                    window.location.href = listBase + '/' + clone.id + '/edit';
                } catch (error) {
                    this.managementMessage = 'Clone failed.';
                    window.reportActionFailure('Could not clone the threat.', error, () => this.cloneThreat());
                } finally {
                    this.managementLoading = false;
                }
            },

            async promoteThreat() {
                const promote = await window.dmConfirm({
                    question: 'Promote ' + this.threatName() + ' to the global library?',
                    consequence: 'The threat becomes available in every campaign and leaves this'
                        + ' campaign’s threat list.',
                    acceptLabel: 'Promote'
                });
                if (!promote) return;
                this.managementLoading = true;
                this.managementMessage = 'Promoting…';
                try {
                    await window.dmRequest(
                        this.apiBase() + '/' + this.threatId() + '/promote',
                        { method: 'POST' });
                    this.managementMessage = 'Promoted to global library.';
                    window.reportSuccess(this.managementMessage);
                    window.location.reload();
                } catch (error) {
                    this.managementMessage = 'Promotion failed.';
                    window.reportActionFailure('Could not promote the threat.', error, () => this.promoteThreat());
                } finally {
                    this.managementLoading = false;
                }
            },

            async deleteThreat() {
                this.managementLoading = true;
                this.managementMessage = 'Checking dependencies…';
                try {
                    const impactResponse = await window.dmRequest(
                        this.apiBase() + '/' + this.threatId() + '/deletion-impact');
                    const impact = await impactResponse.json();
                    const count = (impact.dependencies || []).length;
                    const consequence = count > 0
                        ? 'This threat has ' + count + ' dependent link(s); each reference is'
                            + ' cleared. The threat itself is deleted permanently.'
                        : 'The threat is deleted permanently. Nothing currently points at it.';
                    const confirmed = await window.dmConfirm({
                        question: 'Delete ' + this.threatName() + '?',
                        consequence
                    });
                    if (!confirmed) {
                        this.managementMessage = '';
                        return;
                    }
                    await window.dmRequest(
                        this.apiBase() + '/' + this.threatId() + '?confirmed=' + (count > 0),
                        { method: 'DELETE' });
                    window.reportSuccess('Threat deleted.');
                    const suffix = this.campaignId()
                        ? '?campaignId=' + encodeURIComponent(this.campaignId())
                        : '';
                    window.location.href = this.listHref() + suffix;
                } catch (error) {
                    this.managementMessage = 'Delete failed.';
                    window.reportActionFailure('Could not delete the threat.', error, () => this.deleteThreat());
                } finally {
                    this.managementLoading = false;
                }
            }
        }));
    });
})();
