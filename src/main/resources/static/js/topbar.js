(function() {
    if (!/Mac|iP(hone|ad|od)/.test(navigator.platform)) {
        const kbd = document.getElementById('paletteHintKey');
        if (kbd) kbd.textContent = 'Ctrl K';
    }
})();

document.addEventListener('click', async (e) => {
    const btn = e.target.closest('[data-roll]');
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation();

    const expr = btn.dataset.roll;
    const tooltip = document.createElement('div');
    tooltip.className = 'roll-tooltip';
    tooltip.textContent = '...';
    document.body.appendChild(tooltip);

    const rect = btn.getBoundingClientRect();
    tooltip.style.left = rect.left + 'px';
    tooltip.style.top = (rect.top - 32) + 'px';

    try {
        const campId = document.body.dataset.campaignId;
        const body = { expression: expr, campaignId: campId };
        const activeEnc = document.body.dataset.activeEncounterId || null;
        if (activeEnc) body.encounterId = activeEnc;

        const resp = await window.dmRequest('/api/v1/roll', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        const result = await resp.json();
        tooltip.textContent = expr + ' = ' + result.total;
        window.dispatchEvent(new CustomEvent('dice-roll-result', { detail: result }));
    } catch (error) {
        tooltip.textContent = 'Roll failed';
        window.reportActionFailure('Could not roll the dice.', error,
            () => btn.click());
    }

    setTimeout(() => tooltip.remove(), 3000);
});

document.addEventListener('keydown', (e) => {
    if (e.ctrlKey && e.key === 'r') {
        e.preventDefault();
        window.dispatchEvent(new CustomEvent('dice-roller-toggle'));
    }
});
