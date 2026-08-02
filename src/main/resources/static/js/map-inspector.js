(function () {
    const RELEVANCE = {
        brush: 'palette', fill: 'palette', room: 'palette', corridor: 'palette',
        region: 'palette', door: 'palette', 'fill-rect': 'palette', bucket: 'palette',
        rect: 'tool', circle: 'tool', line: 'tool', polygon: 'tool',
        select: 'selection'
    };

    function setContext(primary) {
        document.querySelectorAll('[data-inspector-section]').forEach(section => {
            const key = section.dataset.inspectorSection;
            const isPrimary = key === primary;
            section.dataset.relevance = isPrimary ? 'primary' : 'secondary';
            section.open = isPrimary;
        });
    }

    document.addEventListener('click', event => {
        const tool = event.target.closest('[data-tool]');
        if (!tool) return;
        document.querySelectorAll('[data-tool]').forEach(
            button => button.setAttribute('aria-pressed', String(button === tool)));
        setContext(RELEVANCE[tool.dataset.tool] ?? 'tool');
    });

    window.mapInspector = { setContext };
    document.addEventListener('DOMContentLoaded', () => setContext('palette'));
})();
