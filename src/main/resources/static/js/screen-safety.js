(function () {
  'use strict';

  const VALID_BEHAVIORS = new Set(['FILTER', 'HIDE', 'PLAYER_PROJECTION']);
  const SWEEP_MS = 600;
  let sweepTimer = null;

  function prefersReducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function ensureSweep() {
    let sweep = document.getElementById('screen-safety-sweep');
    if (!sweep && document.body) {
      sweep = document.createElement('div');
      sweep.id = 'screen-safety-sweep';
      document.body.appendChild(sweep);
    }
    return sweep;
  }

  function reportInvalidModule(el, message) {
    console.warn('[screen-safety] ' + message, el.getAttribute('data-runtime-module') || 'unnamed');
    if (!el.hasAttribute('data-screen-safety-warning-reported')) {
      el.setAttribute('data-screen-safety-warning-reported', '');
      if (window.showToast) {
        window.showToast('A screen-safety module was invalid and has been hidden.', 'warning');
      }
    }
  }

  function validateModule(el) {
    const behavior = el.getAttribute('data-table-safe-behavior');
    if (!behavior) {
      reportInvalidModule(el, 'Module missing data-table-safe-behavior; defaulting to HIDE:');
      return 'HIDE';
    }
    if (!VALID_BEHAVIORS.has(behavior)) {
      reportInvalidModule(el, 'Unknown behavior "' + behavior + '"; defaulting to HIDE:');
      return 'HIDE';
    }
    return behavior;
  }

  function protect(el) {
    el.setAttribute('data-screen-safety-managed', '');
    if (!el.hasAttribute('inert')) {
      el.setAttribute('inert', '');
      el.setAttribute('data-screen-safety-added-inert', '');
    }
    if (el.getAttribute('aria-hidden') !== 'true') {
      if (el.hasAttribute('aria-hidden')) {
        el.setAttribute('data-screen-safety-previous-aria-hidden', el.getAttribute('aria-hidden'));
      }
      el.setAttribute('data-screen-safety-changed-aria', '');
      el.setAttribute('aria-hidden', 'true');
    }
  }

  function applyPrivate() {
    document.querySelectorAll('[data-screen-safety-managed]').forEach(function (el) {
      if (el.hasAttribute('data-screen-safety-added-inert')) {
        el.removeAttribute('inert');
      }
      if (el.hasAttribute('data-screen-safety-changed-aria')) {
        if (el.hasAttribute('data-screen-safety-previous-aria-hidden')) {
          el.setAttribute('aria-hidden', el.getAttribute('data-screen-safety-previous-aria-hidden'));
        } else {
          el.removeAttribute('aria-hidden');
        }
      }
      el.removeAttribute('data-screen-safety-managed');
      el.removeAttribute('data-screen-safety-added-inert');
      el.removeAttribute('data-screen-safety-changed-aria');
      el.removeAttribute('data-screen-safety-previous-aria-hidden');
    });
  }

  function applyTableSafe() {
    document.querySelectorAll('[data-runtime-module]').forEach(function (module) {
      const behavior = validateModule(module);
      if (behavior === 'HIDE') {
        protect(module);
      } else if (behavior === 'FILTER') {
        module.querySelectorAll('[data-screen-sensitive]').forEach(protect);
      }
    });

    document.querySelectorAll('[data-screen-sensitive]').forEach(function (el) {
      if (!el.closest('[data-runtime-module]')) {
        protect(el);
      }
    });
  }

  window.setScreenSafety = function (mode, options) {
    if (mode !== 'PRIVATE' && mode !== 'TABLE_SAFE') {
      console.warn('[screen-safety] Invalid mode:', mode, '- defaulting to PRIVATE');
      mode = 'PRIVATE';
    }

    const body = document.body;
    if (!body) return;
    ensureSweep();

    const goingTableSafe = mode === 'TABLE_SAFE';
    if (goingTableSafe) {
      body.dataset.screenSafety = 'TABLE_SAFE';
      applyTableSafe();
    } else {
      body.dataset.screenSafety = 'PRIVATE';
      applyPrivate();
    }

    window.dispatchEvent(new CustomEvent('screen-safety-changed', { detail: { mode: mode } }));

    const animate = (!options || options.animate !== false) && !prefersReducedMotion();
    clearTimeout(sweepTimer);
    body.classList.remove('safety-sweeping-off', 'safety-sweeping-on');
    if (animate) {
      body.classList.add(goingTableSafe ? 'safety-sweeping-off' : 'safety-sweeping-on');
      sweepTimer = setTimeout(function () {
        body.classList.remove('safety-sweeping-off', 'safety-sweeping-on');
      }, SWEEP_MS);
    }
  };
})();
