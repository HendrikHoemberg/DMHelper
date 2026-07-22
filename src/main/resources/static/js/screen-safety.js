(function () {
  'use strict';

  const VALID_BEHAVIORS = new Set(['FILTER', 'HIDE', 'PLAYER_PROJECTION']);
  const FADE_MS = 200;
  const SWEEP_MS = 600;
  let sweepTimer = null;
  let fadeTimer = null;

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

  function validateModule(el) {
    const behavior = el.getAttribute('data-table-safe-behavior');
    if (!behavior) {
      console.warn('[screen-safety] Module missing data-table-safe-behavior:', el.getAttribute('data-runtime-module') || 'unnamed');
      return 'HIDE';
    }
    if (!VALID_BEHAVIORS.has(behavior)) {
      console.warn('[screen-safety] Unknown behavior "' + behavior + '" on module', el.getAttribute('data-runtime-module'), '- defaulting to HIDE');
      return 'HIDE';
    }
    return behavior;
  }

  function applyPrivate() {
    document.querySelectorAll('[data-runtime-module]').forEach(function (module) {
      module.removeAttribute('inert');
      module.removeAttribute('aria-hidden');
      module.querySelectorAll('[data-screen-sensitive]').forEach(function (el) {
        el.removeAttribute('inert');
        el.removeAttribute('aria-hidden');
      });
    });
  }

  function applyTableSafe() {
    document.querySelectorAll('[data-runtime-module]').forEach(function (module) {
      validateModule(module);

      const behavior = module.getAttribute('data-table-safe-behavior');

      if (behavior === 'HIDE') {
        module.setAttribute('inert', '');
        module.setAttribute('aria-hidden', 'true');
      } else if (behavior === 'FILTER') {
        module.querySelectorAll('[data-screen-sensitive]').forEach(function (el) {
          el.setAttribute('inert', '');
          el.setAttribute('aria-hidden', 'true');
        });
      }

      if (behavior === 'PLAYER_PROJECTION') {
      }
    });

    document.querySelectorAll('[data-screen-sensitive]').forEach(function (el) {
      if (!el.closest('[data-runtime-module]')) {
        el.setAttribute('inert', '');
        el.setAttribute('aria-hidden', 'true');
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

    const animate = options && options.animate !== false && !prefersReducedMotion();

    clearTimeout(sweepTimer);
    clearTimeout(fadeTimer);
    body.classList.remove('safety-fading', 'safety-sweeping-off', 'safety-sweeping-on');

    if (!animate) {
      if (mode === 'TABLE_SAFE') {
        applyTableSafe();
        body.dataset.screenSafety = 'TABLE_SAFE';
      } else {
        applyPrivate();
        body.dataset.screenSafety = 'PRIVATE';
      }
      window.dispatchEvent(new CustomEvent('screen-safety-changed', { detail: { mode: mode } }));
      return;
    }

    const goingTableSafe = mode === 'TABLE_SAFE';
    body.classList.add(goingTableSafe ? 'safety-sweeping-off' : 'safety-sweeping-on');
    sweepTimer = setTimeout(function () {
      body.classList.remove('safety-sweeping-off', 'safety-sweeping-on');
    }, SWEEP_MS);

    if (goingTableSafe) {
      body.classList.add('safety-fading');
      fadeTimer = setTimeout(function () {
        body.classList.remove('safety-fading');
        applyTableSafe();
        body.dataset.screenSafety = 'TABLE_SAFE';
        window.dispatchEvent(new CustomEvent('screen-safety-changed', { detail: { mode: mode } }));
      }, FADE_MS);
    } else {
      body.dataset.screenSafety = 'PRIVATE';
      applyPrivate();
      window.dispatchEvent(new CustomEvent('screen-safety-changed', { detail: { mode: mode } }));
    }
  };
})();
