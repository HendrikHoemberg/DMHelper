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

    /* `trigger` is explicit for overlays that arrive asynchronously: hx-disabled-elt blurs
       the button while its request is in flight, so by the time the swap lands
       document.activeElement is the body and focus would be restored to nowhere. */
    function open(element, trigger) {
        if (!openOverlays.includes(element)) openOverlays.push(element);
        triggers.set(element, trigger || document.activeElement);
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
        /* Escape and backdrop dismissal go through here too, so anything waiting on an
           answer (dmConfirm) hears about a close it did not initiate. */
        element.dispatchEvent(new CustomEvent('dm-overlay-closed', { bubbles: false }));
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
        const overlay = dismiss.closest('.dialog, .side-sheet, .popover, .handout-overlay');
        if (overlay) close(overlay);
    });

    /* ── Presentation surface (spec 11.11 and 15) ─────────────────────────────────
       The handout overlay arrives from htmx already visible rather than through
       dmOverlay.open's hidden flip, so it adopts itself on arrival. Without this it
       had no Escape, no focus trap, and no focus restoration — the only blocking
       surface in the product outside the elevation model. */
    function adoptPresentationOverlay(event) {
        const overlay = document.querySelector(
            '[data-presentation-overlay]:not([data-overlay-adopted])');
        if (!overlay) return;
        overlay.dataset.overlayAdopted = 'true';
        const trigger = event?.detail?.requestConfig?.elt;

        /* An asset that 404s or is unreadable is a presentation error, not an empty
           canvas: swap the state so the failed panel and its Retry become visible. */
        const asset = overlay.querySelector('[data-presentation-asset]');
        if (asset) {
            asset.addEventListener('error', () => {
                overlay.dataset.presentationState = 'error';
                const panel = overlay.querySelector('#presentationError');
                if (panel) panel.hidden = false;
                retrap();
            });
        }

        /* Clicking the canvas itself closes; clicking the asset, the caption, or any
           control inside them does not. */
        overlay.addEventListener('click', event => {
            if (event.target === overlay) close(overlay);
        });

        /* close() restores focus and hides; the overlay then plays its fold-out and
           removes itself, so a second Present starts from a clean document. */
        overlay.addEventListener('dm-overlay-closed', () => {
            overlay.hidden = false;
            overlay.classList.add('closing');
            const remove = () => overlay.remove();
            overlay.addEventListener('transitionend', remove, { once: true });
            setTimeout(remove, 800);
        });

        open(overlay, trigger);
    }

    document.addEventListener('htmx:afterSwap', adoptPresentationOverlay);

    /* Retry re-requests the same route and replaces the overlay in place (spec 16). */
    document.addEventListener('click', event => {
        const retry = event.target.closest('[data-presentation-retry]');
        if (!retry) return;
        const overlay = retry.closest('[data-presentation-overlay]');
        const route = retry.dataset.presentationRoute;
        if (!overlay || !route) return;
        close(overlay);
        if (window.htmx) window.htmx.ajax('GET', route, { target: 'body', swap: 'beforeend' });
    });

    window.dmOverlay = { open, close };

    /* ── Confirmation (spec 14 and 15) ────────────────────────────────────────────
       window.confirm() is not one of the five elevation levels: it has no accessible
       name of ours, no focus restoration, no bounded sizing, and no way to carry the
       consequence line the spec requires. dmConfirm renders the shared dialog instead
       and resolves a promise, so htmx and hand-written handlers share one contract. */
    function dmConfirm({ question, consequence = '', acceptLabel = 'Delete' }) {
        const dialog = document.getElementById('confirmDialog');
        if (!dialog) return Promise.resolve(window.confirm(question));

        const questionEl = dialog.querySelector('[data-confirm-question]');
        const consequenceEl = dialog.querySelector('[data-confirm-consequence-text]');
        const accept = dialog.querySelector('[data-confirm-accept]');
        const cancel = dialog.querySelector('[data-confirm-cancel]');

        questionEl.textContent = question;
        consequenceEl.textContent = consequence;
        /* An empty <p> would still claim aria-describedby and announce nothing. */
        consequenceEl.hidden = !consequence;
        accept.textContent = acceptLabel;

        return new Promise(resolve => {
            function settle(answer) {
                accept.removeEventListener('click', onAccept);
                cancel.removeEventListener('click', onCancel);
                dialog.removeEventListener('dm-overlay-closed', onCancel);
                close(dialog);
                resolve(answer);
            }
            function onAccept() { settle(true); }
            function onCancel() { settle(false); }

            accept.addEventListener('click', onAccept);
            cancel.addEventListener('click', onCancel);
            /* Escape and the backdrop close through dmOverlay, which does not know it is
               settling a promise — so listen for the close it broadcasts. */
            dialog.addEventListener('dm-overlay-closed', onCancel);
            open(dialog);
            /* Cancel takes focus, not the destructive button: a stray Enter on a confirmation
               that opened under the cursor should not delete the entity (spec 14). */
            cancel.focus();
        });
    }

    window.dmConfirm = dmConfirm;

    /* htmx fires htmx:confirm for every request; detail.question is null unless the
       element carries hx-confirm. Preventing the event stops htmx's own window.confirm,
       and issueRequest(true) re-issues it with the check already satisfied. */
    document.addEventListener('htmx:confirm', event => {
        const question = event.detail.question;
        if (!question) return;
        event.preventDefault();
        const trigger = event.detail.elt;
        dmConfirm({
            question,
            consequence: trigger.getAttribute('data-confirm-consequence') || '',
            acceptLabel: trigger.getAttribute('data-confirm-accept') || 'Delete'
        }).then(confirmed => {
            if (confirmed) event.detail.issueRequest(true);
        });
    });

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
