(function () {
    const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), '
        + 'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const triggers = new WeakMap();
    let trapped = null;

    function focusables(root) {
        return [...root.querySelectorAll(FOCUSABLE)].filter(el => el.offsetParent !== null);
    }

    function open(element) {
        triggers.set(element, document.activeElement);
        element.hidden = false;
        const blocking = element.getAttribute('aria-modal') === 'true';
        if (blocking) trapped = element;
        const first = focusables(element)[0];
        if (first) first.focus();
    }

    function close(element) {
        element.hidden = true;
        if (trapped === element) trapped = null;
        const trigger = triggers.get(element);
        if (trigger && document.contains(trigger)) trigger.focus();
        triggers.delete(element);
    }

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') {
            const openOverlay = document.querySelector(
                '.dialog:not([hidden]), .side-sheet:not([hidden]), .popover:not([hidden])');
            if (openOverlay) {
                event.preventDefault();
                close(openOverlay);
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
