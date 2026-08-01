(function () {
    const path = location.pathname;
    let best = null;
    document.querySelectorAll('#rail .rail__link').forEach(link => {
        const href = link.getAttribute('href').split('?')[0];
        if (href === '/' || !(path === href || path.startsWith(href + '/'))) return;
        if (!best || href.length > best.getAttribute('href').split('?')[0].length) best = link;
    });
    if (best) best.setAttribute('aria-current', 'page');

    const shell = document.querySelector('.app-shell');
    const toggle = document.getElementById('railCollapse');
    if (!shell || !toggle) return;

    function apply(collapsed) {
        shell.classList.toggle('app-shell--rail-collapsed', collapsed);
        toggle.setAttribute('aria-expanded', String(!collapsed));
        toggle.setAttribute('aria-label', collapsed ? 'Expand navigation' : 'Collapse navigation');
        toggle.title = collapsed ? 'Expand navigation' : 'Collapse navigation';
    }

    const stored = localStorage.getItem('dmhelper.railCollapsed')
        ?? localStorage.getItem('dmhelper.navCollapsed');
    apply(stored === '1');

    toggle.addEventListener('click', () => {
        const collapsed = !shell.classList.contains('app-shell--rail-collapsed');
        localStorage.setItem('dmhelper.railCollapsed', collapsed ? '1' : '0');
        apply(collapsed);
    });
})();
