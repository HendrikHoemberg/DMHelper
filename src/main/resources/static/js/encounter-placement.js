(function() {
    'use strict';

    const state = {
        encounterId: document.querySelector('[data-encounter-placement-board]')?.dataset.encounterId,
        cellSizePx: 48,
        gridWidth: 30,
        gridHeight: 20,
        placements: [],
        combatants: []
    };

    const API_BASE = '/api/v1';

    async function placeCombatant(combatantId, col, row) {
        return dmRequest(
            `${API_BASE}/encounters/${state.encounterId}/combatants/${combatantId}/placement`,
            {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    positionX: col * state.cellSizePx,
                    positionY: row * state.cellSizePx,
                    sizeCols: 1,
                    sizeRows: 1,
                    color: defaultColorFor(findCombatant(combatantId)),
                    icon: null
                })
            });
    }

    function findCombatant(combatantId) {
        return state.combatants.find(c => c.id === combatantId);
    }

    function defaultColorFor(combatant) {
        if (!combatant) return '#7b68ee';
        switch (combatant.kind) {
            case 'PC': return '#4a9eff';
            case 'MONSTER': return '#d95c5c';
            case 'OBJECT': return '#8a8a8a';
            default: return '#7b68ee';
        }
    }

    async function autoPlaceCombatants() {
        const btn = document.querySelector('[data-auto-place]');
        btn.disabled = true;
        btn.textContent = 'Placing...';
        try {
            const res = await dmRequest(`${API_BASE}/encounters/${state.encounterId}/placements/auto`, { method: 'POST' });
            window.location.reload();
        } catch (e) {
            window.showToast('Failed to auto-place: ' + e.message, 'error');
        } finally {
            btn.disabled = false;
            btn.textContent = 'Place unplaced';
        }
    }

    async function placeMissingParty() {
        const btn = document.querySelector('[data-place-party]');
        btn.disabled = true;
        btn.textContent = 'Placing...';
        try {
            const res = await dmRequest(`${API_BASE}/encounters/${state.encounterId}/placements/party`, { method: 'POST' });
            window.location.reload();
        } catch (e) {
            window.showToast('Failed to place party: ' + e.message, 'error');
        } finally {
            btn.disabled = false;
            btn.textContent = 'Place missing party members';
        }
    }

    async function changeEncounterMap(mapId) {
        try {
            await dmRequest(`${API_BASE}/encounters/${state.encounterId}/map`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mapId })
            });
            window.location.reload();
        } catch (e) {
            window.showToast('Failed to change map: ' + e.message, 'error');
        }
    }

    window.autoPlaceCombatants = autoPlaceCombatants;
    window.placeMissingParty = placeMissingParty;
    window.changeEncounterMap = changeEncounterMap;
    window.placementBoard = state;
})();
