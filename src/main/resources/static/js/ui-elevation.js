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

    let lastFocused = null;
    function toggle(show) {
      if (show) {
        lastFocused = document.activeElement;
      }
      overlay.classList.toggle('open', show);
      if (show) {
        overlay.querySelector('.shortcut-panel').focus();
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
    window.toggleShortcutOverlay = toggle;
  }

  function init() {
    initLoadingFilament();
    initToasts();
    initShortcutOverlay();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
