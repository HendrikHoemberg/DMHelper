(function () {
  'use strict';

  function initLoadingFilament() {
    const bar = document.createElement('div');
    bar.id = 'loading-filament';
    document.body.appendChild(bar);

    let progress = 0;
    let raf = null;
    let pendingCount = 0;
    let showTimer = null;
    let hideTimer = null;

    function frame() {
      if (pendingCount <= 0) return;
      progress += (95 - progress) * 0.08;
      bar.style.width = progress + '%';
      raf = requestAnimationFrame(frame);
    }

    document.body.addEventListener('htmx:beforeRequest', () => {
      pendingCount++;
      clearTimeout(hideTimer);
      progress = 0;
      bar.style.width = '0%';
      showTimer = setTimeout(() => {
        if (pendingCount <= 0) return;
        document.body.classList.add('loading');
        bar.style.opacity = '1';
        raf = requestAnimationFrame(frame);
      }, 150);
    });

    document.body.addEventListener('htmx:afterRequest', () => {
      pendingCount = Math.max(0, pendingCount - 1);
      if (pendingCount > 0) return;
      clearTimeout(showTimer);
      cancelAnimationFrame(raf);
      bar.style.width = '100%';
      hideTimer = setTimeout(() => {
        document.body.classList.remove('loading');
        bar.style.width = '0%';
        bar.style.opacity = '0';
      }, 180);
    });
  }

  function initToasts() {
    /* Re-acquired on every toast rather than captured once: several forms swap the
       whole <body>, which detaches the container, and a toast appended to a detached
       node is a toast nobody sees. */
    function toastContainer() {
      let container = document.getElementById('toast-container');
      if (!container || !container.isConnected) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.setAttribute('role', 'status');
        container.setAttribute('aria-live', 'polite');
        document.body.appendChild(container);
      }
      return container;
    }

    toastContainer();

    window.showToast = function(message, type = 'info', duration = 3000) {
      const toast = document.createElement('div');
      toast.className = 'toast toast-' + type;
      toast.textContent = message;
      toastContainer().appendChild(toast);
      requestAnimationFrame(() => toast.classList.add('show'));
        setTimeout(() => {
          toast.classList.remove('show');
          toast.addEventListener('transitionend', () => toast.remove(), { once: true });
        }, duration);
    };

    /* Every mutation gets an acknowledgement (§3.4). Reads stay silent; anything
       that changed something on disk says so, in the app's own voice. An element
       can override the wording with data-toast, or opt out with data-toast="off". */
    const VERB_CONFIRMATIONS = {
      post: 'Written to the book.',
      put: 'Saved.',
      patch: 'Saved.',
      delete: 'Struck from the record.',
    };

    const PENDING_TOAST = 'dmhelper.pendingToast';

    // A confirmation for a mutation that answers with HX-Redirect would be torn down
    // with the page a moment later, so it rides across the navigation instead.
    const pending = sessionStorage.getItem(PENDING_TOAST);
    if (pending) {
      sessionStorage.removeItem(PENDING_TOAST);
      try {
        const { message, type } = JSON.parse(pending);
        window.showToast(message, type);
      } catch (e) { /* a malformed crumb is not worth a broken page */ }
    }

    document.body.addEventListener('htmx:afterRequest', (evt) => {
      const cfg = evt.detail.requestConfig;
      const xhr = evt.detail.xhr;
      // htmx 2 does not put `successful` on this event's detail — read the status.
      if (!cfg || !xhr || xhr.status < 200 || xhr.status >= 300) return;

      const verb = (cfg.verb || '').toLowerCase();
      if (verb === 'get') return;

      const override = cfg.elt?.getAttribute?.('data-toast');
      if (override === 'off') return;

      const message = override || VERB_CONFIRMATIONS[verb];
      if (!message) return;

      if (xhr.getResponseHeader('HX-Redirect')) {
        sessionStorage.setItem(PENDING_TOAST, JSON.stringify({ message, type: 'success' }));
        return;
      }
      window.showToast(message, 'success');
    });

    /* Errors say what happened and what to do, in the same voice, without apologising. */
    document.body.addEventListener('htmx:responseError', (evt) => {
      const status = evt.detail.xhr?.status;
      window.showToast(
        status === 404 ? 'That page is gone. Reload and try again.'
          : status === 409 ? 'Someone changed this first. Reload to see the current version.'
          : 'The server refused that (' + (status || 'error') + '). Try again.',
        'error', 5000);
    });

    document.body.addEventListener('htmx:sendError', () => {
      window.showToast('No answer from the server. Check it is still running.', 'error', 5000);
    });
  }

  function initShortcutOverlay() {
    if (document.getElementById('shortcut-overlay')) return;
    const overlay = document.createElement('div');
    overlay.id = 'shortcut-overlay';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');
    overlay.setAttribute('aria-label', 'Keyboard shortcuts');
    overlay.innerHTML = `
      <div class="shortcut-overlay-backdrop"></div>
      <div class="shortcut-panel" tabindex="-1">
        <button class="shortcut-close" aria-label="Close shortcuts">✕</button>
        <h2>Keyboard Shortcuts</h2>
        <dl>
          <dt>⌘ / Ctrl + K</dt><dd>Search everything</dd>
          <dt>Ctrl + R</dt><dd>Toggle dice roller</dd>
          <dt>?</dt><dd>Show this overlay</dd>
          <dt>Esc</dt><dd>Close overlays</dd>
        </dl>
      </div>
    `;
    document.body.appendChild(overlay);

    const panel = overlay.querySelector('.shortcut-panel');
    const closeBtn = overlay.querySelector('.shortcut-close');

    overlay.addEventListener('keydown', (e) => {
      if (e.key !== 'Tab') return;
      const focusables = [closeBtn, panel].filter(el => el && el.tabIndex >= -1);
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    });

    let lastFocused = null;
    function toggle(show) {
      if (show) {
        lastFocused = document.activeElement;
      }
      overlay.classList.toggle('open', show);
      document.body.classList.toggle('shortcut-open', show);
      if (show) {
        panel.focus();
      } else if (lastFocused) {
        lastFocused.focus();
      }
    }

    document.addEventListener('keydown', (e) => {
      if (e.key === '?' && !e.ctrlKey && !e.metaKey && !e.altKey) {
        const tag = document.activeElement?.tagName;
        if (tag === 'INPUT' || tag === 'TEXTAREA') return;
        e.preventDefault();
        toggle(true);
      }
      if (e.key === 'Escape') toggle(false);
    });

    overlay.querySelector('.shortcut-overlay-backdrop').addEventListener('click', () => toggle(false));
    overlay.querySelector('.shortcut-close').addEventListener('click', () => toggle(false));
    window.toggleShortcutOverlay = toggle;
  }

  function prefersReducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  /* The "opening the book" signature. The @view-transition rule in base.css opts
     the whole app into cross-document transitions; all this does is give the one
     campaign cover involved in the navigation the same view-transition-name the
     dashboard header carries, so the two sides pair up and the cover morphs into
     the page. Covers are named at the last moment rather than in the template
     because a view-transition-name must be unique in a document, and a shelf of
     sixty covers would otherwise need sixty names in CSS. */
  function tagCampaignCover(url) {
    if (!url) return;
    const path = new URL(url, location.origin).pathname;
    document.querySelectorAll('.book-cover[href]').forEach((cover) => {
      cover.style.viewTransitionName =
        new URL(cover.href, location.origin).pathname === path ? 'campaign-cover' : '';
    });
  }

  /* DM Mode (§6.3) — the safety feature as theatre. Shared, because the battle
     map has its own toggle and no top bar, and both must behave identically.
     Player-safe is a safety feature before it is theatre: whatever happens to
     the animation, `dm-mode-off` must end up applied. The fade only delays
     *hiding* DM content by one 200ms beat while the wash covers the screen, and
     the timers below are the only path that can defer it — both are cleared on
     re-entry, so a fast double-toggle can never strand the app mid-transition. */
  const FADE_MS = 200;   // matches --duration-standard
  const SWEEP_MS = 600;
  let sweepTimer = null;
  let fadeTimer = null;

  function ensureShieldSweep() {
    let sweep = document.getElementById('dm-shield-sweep');
    if (!sweep && document.body) {
      sweep = document.createElement('div');
      sweep.id = 'dm-shield-sweep';
      document.body.appendChild(sweep);
    }
    return sweep;
  }

  /* Defined eagerly rather than on DOM ready: the battle map loads Alpine with
     `defer`, so its x-init runs before DOMContentLoaded and calls this on the way up. */
  window.setDmMode = function (on, options = {}) {
    const body = document.body;
    if (!body) return;
    ensureShieldSweep();

    const animate = options.animate !== false && !prefersReducedMotion();

    clearTimeout(sweepTimer);
    clearTimeout(fadeTimer);
    body.classList.remove('dm-fading', 'dm-sweeping-off', 'dm-sweeping-on');

    if (!animate) {
      body.classList.toggle('dm-mode-off', !on);
      body.classList.toggle('dm-mode-on', on);
      return;
    }

    body.classList.add(on ? 'dm-sweeping-on' : 'dm-sweeping-off');
    sweepTimer = setTimeout(() => {
      body.classList.remove('dm-sweeping-off', 'dm-sweeping-on');
    }, SWEEP_MS);

    if (on) {
      body.classList.remove('dm-mode-off');
      body.classList.add('dm-mode-on');
    } else {
      body.classList.add('dm-fading');
      fadeTimer = setTimeout(() => {
        body.classList.remove('dm-fading', 'dm-mode-on');
        body.classList.add('dm-mode-off');
      }, FADE_MS);
    }
  };

  function initViewTransitions() {
    window.addEventListener('pageswap', (e) => {
      if (!e.viewTransition) return;
      if (prefersReducedMotion()) {
        e.viewTransition.skipTransition();
        return;
      }
      // Leaving the shelf: tag the cover of the campaign we are opening.
      tagCampaignCover(e.activation?.entry?.url);
    });

    window.addEventListener('pagereveal', (e) => {
      if (!e.viewTransition) return;
      if (prefersReducedMotion()) {
        e.viewTransition.skipTransition();
        return;
      }
      // Arriving back on the shelf: tag the cover of the campaign we came from,
      // so the dashboard closes back down into its book.
      tagCampaignCover(window.navigation?.activation?.from?.url);
    });
  }

  /* Statblock side sheet (§5.3). The crown-jewel statblock stops being a page you
     navigate to and becomes the payoff of a click: the card grows into a sheet
     over the library, and your place in the list is never lost. */
  function initSideSheet() {
    const slot = document.getElementById('statblock-sheet-slot');
    const backdrop = document.getElementById('statblock-sheet-backdrop');
    if (!slot || !backdrop) return;

    let lastFocused = null;

    function currentSheet() {
      return slot.querySelector('.side-sheet');
    }

    function close() {
      const sheet = currentSheet();
      if (!sheet) return;
      sheet.classList.remove('open');
      backdrop.classList.remove('open');
      const done = () => {
        slot.innerHTML = '';
        if (lastFocused && document.contains(lastFocused)) lastFocused.focus();
      };
      if (prefersReducedMotion()) done();
      else sheet.addEventListener('transitionend', done, { once: true });
    }

    async function open(link) {
      let html;
      try {
        const resp = await fetch(link.href + '/sheet');
        if (!resp.ok) throw new Error(resp.status);
        html = await resp.text();
      } catch (e) {
        // The sheet is an enhancement; if it can't load, the page still can.
        location.href = link.href;
        return;
      }

      lastFocused = link;
      const card = link.closest('.statblock-card');

      const inject = () => {
        if (card) card.style.viewTransitionName = '';
        slot.innerHTML = html;
        backdrop.classList.add('open');
        const sheet = currentSheet();
        sheet.classList.add('open');
        sheet.focus();
      };

      // The card morphs into the sheet it opens — the quiet echo of the shelf's
      // "opening the book" (§2). The name is on the card only for the length of
      // the transition, and is cleared inside the callback so the new snapshot
      // has exactly one element claiming it.
      if (document.startViewTransition && !prefersReducedMotion() && card) {
        card.style.viewTransitionName = 'statblock-sheet';
        document.startViewTransition(inject);
        return;
      }

      slot.innerHTML = html;
      backdrop.classList.add('open');
      const sheet = currentSheet();
      requestAnimationFrame(() => {
        sheet.classList.add('open');
        sheet.focus();
      });
    }

    document.addEventListener('click', (e) => {
      const link = e.target.closest('[data-statblock-link]');
      if (link && !e.ctrlKey && !e.metaKey && !e.shiftKey && e.button === 0) {
        e.preventDefault();
        open(link);
        return;
      }
      if (e.target.closest('[data-sheet-close]') || e.target === backdrop) close();
    });

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') close();
    });
  }

  function init() {
    initLoadingFilament();
    initToasts();
    initShortcutOverlay();
    ensureShieldSweep();
    initSideSheet();
    initViewTransitions();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
  window.dismissHandout = function(overlay) {
    if (overlay.classList.contains('closing')) return;
    overlay.classList.add('closing');
    setTimeout(() => overlay.remove(), 600);
  };

  window.tickNumber = function(element, to, duration = 300) {
    const from = parseInt(element.textContent, 10) || 0;
    const start = performance.now();
    function step(now) {
      const t = Math.min(1, (now - start) / duration);
      element.textContent = Math.round(from + (to - from) * t);
      if (t < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  };
})();
