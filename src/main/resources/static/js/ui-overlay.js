(function () {
    const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), '
        + 'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const triggers = new WeakMap();
    const openOverlays = [];
    let trapped = null;

    /* getClientRects() rather than offsetParent: offsetParent is null for position:fixed
       elements and descendants of some transform/filter containing blocks, which would
       wrongly exclude them. A focusable with layout boxes is a focusable we can move to. */
    function focusables(root) {
        return [...root.querySelectorAll(FOCUSABLE)]
            .filter(el => el.getClientRects().length > 0);
    }

    function topBlocking() {
        for (let i = openOverlays.length - 1; i >= 0; i--) {
            if (openOverlays[i].getAttribute('aria-modal') === 'true') return openOverlays[i];
        }
        return null;
    }

    function retrap() {
        trapped = topBlocking();
    }

    function open(element) {
        if (!openOverlays.includes(element)) openOverlays.push(element);
        triggers.set(element, document.activeElement);
        element.hidden = false;
        retrap();
        const first = focusables(element)[0];
        if (first) first.focus();
    }

    function close(element) {
        const index = openOverlays.indexOf(element);
        if (index !== -1) openOverlays.splice(index, 1);
        element.hidden = true;
        const trigger = triggers.get(element);
        triggers.delete(element);
        retrap();
        if (trigger && document.contains(trigger)) trigger.focus();
    }

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            /* Only overlays dmOverlay opened are ours to close. The legacy statblock sheet
               (ui-elevation.js) manages its own Escape and slide-out, so it stays out of
               reach. Closing the last-opened overlay also keeps nested stacks sane. */
            const overlay = openOverlays[openOverlays.length - 1];
            if (overlay) {
                event.preventDefault();
                close(overlay);
            }
            return;
        }
        if (event.key !== 'Tab' || !trapped) return;
        const items = focusables(trapped);
        if (items.length === 0) return;
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });

    document.addEventListener('click', event => {
        const dismiss = event.target.closest('[data-overlay-dismiss]');
        if (!dismiss) return;
        const overlay = dismiss.closest('.dialog, .side-sheet, .popover');
        if (overlay) close(overlay);
    });

    window.dmOverlay = { open, close };

    window.dmToast = {
        show(message, tone = 'neutral') {
            const region = document.getElementById('toastRegion');
            if (!region) return;
            const toast = document.createElement('div');
            toast.className = 'toast toast--' + tone;
            toast.textContent = message;
            region.appendChild(toast);
            requestAnimationFrame(() => toast.classList.add('show'));
            setTimeout(() => toast.remove(), 5000);
        }
    };
})();
