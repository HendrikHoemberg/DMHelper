(function () {
    'use strict';

    const RELEVANCE = {
        brush: 'palette', room: 'palette',
        region: 'palette', door: 'palette', 'fill-rect': 'palette', bucket: 'palette',
        rect: 'tool', circle: 'tool', line: 'tool', polygon: 'tool',
        select: 'selection'
    };

    function setContext(primary) {
        document.querySelectorAll('[data-inspector-section]').forEach(section => {
            const key = section.dataset.inspectorSection;
            const isPrimary = key === primary;
            section.dataset.relevance = isPrimary ? 'primary' : 'secondary';
            if (isPrimary) section.open = true;
        });
    }

    document.addEventListener('click', event => {
        const tool = event.target.closest('[data-tool]');
        if (!tool) return;
        setContext(RELEVANCE[tool.dataset.tool] ?? 'tool');
    });

    window.addEventListener('map-toolchange', event => {
        setContext(RELEVANCE[event.detail.tool] ?? 'tool');
    });

    document.addEventListener('DOMContentLoaded', () => setContext('palette'));
})();
