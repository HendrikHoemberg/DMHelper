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
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.setAttribute('role', 'status');
      container.setAttribute('aria-live', 'polite');
      document.body.appendChild(container);
    }

    window.showToast = function(message, type = 'info', duration = 3000) {
      const toast = document.createElement('div');
      toast.className = 'toast toast-' + type;
      toast.textContent = message;
      container.appendChild(toast);
      requestAnimationFrame(() => toast.classList.add('show'));
        setTimeout(() => {
          toast.classList.remove('show');
          toast.addEventListener('transitionend', () => toast.remove(), { once: true });
        }, duration);
    };
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

  function initViewTransitions() {
    if (!document.startViewTransition) return;
    document.addEventListener('click', (e) => {
      if (e.target.closest('[hx-delete],[hx-get],[hx-post],[hx-put],[hx-patch]')) return;
      const link = e.target.closest('[data-view-transition]');
      if (!link) return;
      if (e.ctrlKey || e.metaKey || e.shiftKey) return;
      if (link.origin !== location.origin) return;
      e.preventDefault();
      document.startViewTransition(() => {
        location.href = link.href;
      });
    });
  }

  function init() {
    initLoadingFilament();
    initToasts();
    initShortcutOverlay();
    initViewTransitions();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

window.dismissHandout = function(overlay) {
  if (overlay.classList.contains('closing')) return;
  overlay.classList.add('closing');
  setTimeout(() => overlay.remove(), 320);
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
