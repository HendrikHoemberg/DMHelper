(function () {
  'use strict';

  const PRESERVED_KEYS = new Set(['map']);

  class CockpitModuleController {
    constructor(config) {
      this.config = config;
      this.requests = new Map();
      this.inflightModes = new Map();
      this.revisions = new Map();
      this.stale = new Set();
      this.loaded = new Set();
      this.preserveContent = new Set();
      this.attention = new Map();
      this._mounted = false;
      this._layoutApplied = false;
      this._shells = new Map();
    }

    mount() {
      this.discoverShells();
      this.registerListeners();

      // The layout controller boots before this one and dispatches cockpit:layout-applied
      // synchronously during its own mount, so that event has usually already fired by now.
      // Fall back to the layout controller's own mounted flag rather than waiting forever.
      if (this._layoutApplied || window.cockpitLayout?.mounted) {
        this._layoutApplied = true;
        this._mounted = true;
        this.flushStale();
        return;
      }

      window.addEventListener('cockpit:layout-applied', () => {
        this._layoutApplied = true;
        this._mounted = true;
        this.flushStale();
      }, { once: true });
    }

    flushStale() {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          for (const key of [...this.stale]) {
            if (this.isModuleVisible(key)) this.load(key, { force: true });
          }
        });
      });
    }

    discoverShells() {
      this._shells.clear();
      document.querySelectorAll('[data-module-key]').forEach((shell) => {
        const key = shell.getAttribute('data-module-key');
        if (key) this._shells.set(key, shell);
      });
    }

    registerListeners() {
      window.addEventListener('cockpit:module-visibility', (event) => {
        if (!this.config.runtimeModulesEnabled) return;
        const detail = event.detail || {};
        const key = detail.moduleKey;
        if (!key || !this._shells.has(key)) return;
        if (detail.visible && this.attention.has(key)) {
          this.clearAttention(key);
        }
      });

      window.addEventListener('cockpit:module-refresh', (event) => {
        if (!this.config.runtimeModulesEnabled) return;
        const detail = event.detail || {};
        const key = detail.moduleKey;
        if (!key || !this._shells.has(key)) return;
        this.refresh(key, detail.reason);
      });

      window.addEventListener('cockpit:module-invalidate', (event) => {
        if (!this.config.runtimeModulesEnabled) return;
        const detail = event.detail || {};
        const key = detail.moduleKey;
        if (!key || !this._shells.has(key)) return;
        this.invalidate(key, detail.reason);
      });

      window.addEventListener('cockpit:module-mode', (event) => {
        if (!this.config.runtimeModulesEnabled) return;
        const detail = event.detail || {};
        const key = detail.moduleKey;
        if (!key || !this._shells.has(key)) return;
        if (!this._mounted) {
          this.stale.add(key);
          return;
        }
        if (this.preserveContent.has(key)) return;
        this.load(key, { mode: detail.mode });
      });


    }

    isModuleVisible(key) {
      const shell = this._shells.get(key);
      if (!shell) return false;
      // The layout controller is authoritative about zones, active tabs, collapsed state,
      // focus, and the hidden depot. A depot/collapsed/inactive-tab shell can
      // still report a non-null offsetParent, so delegate rather than approximate here.
      if (window.cockpitLayout && typeof window.cockpitLayout.isModuleVisible === 'function') {
        return window.cockpitLayout.isModuleVisible(key);
      }
      if (shell.offsetParent === null) return false;
      if (!document.contains(shell)) return false;
      return true;
    }

    modeFor(moduleKey) {
      if (window.cockpitLayout && typeof window.cockpitLayout.moduleMode === 'function') {
        return window.cockpitLayout.moduleMode(moduleKey);
      }
      const shell = this._shells.get(moduleKey);
      if (!shell) return 'STANDARD';
      if (shell.getAttribute('data-compact') === 'true') return 'COMPACT';
      return 'STANDARD';
    }

    isLoaded(moduleKey) {
      return this.loaded.has(moduleKey);
    }

    load(moduleKey, options = {}) {
      const shell = this._shells.get(moduleKey);
      if (!shell) {
        console.warn('CockpitModuleController: unknown key', moduleKey);
        return;
      }

      const visible = this.isModuleVisible(moduleKey);

      if (!options.force && !visible) return;

      const requestedMode = options.mode || this.modeFor(moduleKey);

      if (!options.force
        && this.loaded.has(moduleKey)
        && !this.stale.has(moduleKey)
        && requestedMode === this.modeFor(moduleKey)) {
        return;
      }

      // If an identical (same-mode) request is already in flight, let it finish rather than
      // aborting and refetching. Rapid preset cycling fires module-mode repeatedly before the
      // first fetch resolves; without this guard each re-trigger would abort+restart the load.
      if (!options.force
        && this.requests.has(moduleKey)
        && this.inflightModes.get(moduleKey) === requestedMode) {
        return;
      }

      if (this.requests.has(moduleKey)) {
        this.requests.get(moduleKey).abort();
      }

      const revision = (this.revisions.get(moduleKey) || 0) + 1;
      this.revisions.set(moduleKey, revision);

      this.dispatchState(moduleKey, 'loading');

      const contentEl = shell.querySelector('[data-module-content]');
      if (!contentEl) {
        console.warn('CockpitModuleController: no data-module-content for', moduleKey);
        return;
      }

      let endpoint = contentEl.getAttribute('data-module-endpoint') || '';
      endpoint = endpoint.replace('{campaignId}', this.config.campaignId || '');

      const params = new URLSearchParams();
      params.set('mode', requestedMode);
      const mapId = options.mapId || this.config.mapId || '';
      if (mapId) params.set('mapId', mapId);
      const sep = endpoint.includes('?') ? '&' : '?';
      endpoint += sep + params.toString();

      const loadStarted = performance.now();

      const controller = new AbortController();
      this.requests.set(moduleKey, controller);
      this.inflightModes.set(moduleKey, requestedMode);

      const fetchOptions = {
        signal: controller.signal,
        headers: {
          'Accept': 'text/html',
          'X-Cockpit-Module': moduleKey
        }
      };

      window.dmRequest(endpoint, fetchOptions)
        .then(async (response) => {
          const html = await response.text();
          if (this.revisions.get(moduleKey) !== revision) return;

          const template = document.createElement('template');
          template.innerHTML = html;
          const fragments = template.content.querySelectorAll(
            `[data-cockpit-module-fragment="${moduleKey}"]`
          );
          if (fragments.length !== 1) {
            throw new Error(
              `Module ${moduleKey} response must contain exactly one [data-cockpit-module-fragment="${moduleKey}"], got ${fragments.length}`
            );
          }
          const fragment = fragments[0];

          const focusedId = document.activeElement?.id || null;

          if (!fragment.childNodes.length) {
            this.loaded.add(moduleKey);
            this.stale.delete(moduleKey);
            contentEl.setAttribute('data-module-loaded', 'true');
            contentEl.setAttribute('data-module-stale', 'false');
            const durationMs = performance.now() - loadStarted;
            this.dispatchState(moduleKey, 'empty', { durationMs, revision: this.revisions.get(moduleKey) });
            return;
          }

          const body = shell.querySelector('[data-module-body]');
          if (body) {
            const root = body.querySelector('[data-module-content]') || body;
            root.replaceChildren(...fragment.childNodes);
            window.Alpine?.initTree(root);
          } else {
            console.warn('CockpitModuleController: no [data-module-body] for', moduleKey, '- falling back to contentEl');
            contentEl.replaceChildren(...fragment.childNodes);
            window.Alpine?.initTree(contentEl);
          }

          if (focusedId) {
            const nextFocus = shell.querySelector(`#${CSS.escape(focusedId)}`);
            if (nextFocus && document.activeElement !== nextFocus) {
              nextFocus.focus();
            }
          }

          this.loaded.add(moduleKey);
          this.stale.delete(moduleKey);
          contentEl.setAttribute('data-module-loaded', 'true');
          contentEl.setAttribute('data-module-stale', 'false');

          if (PRESERVED_KEYS.has(moduleKey)) {
            this.preserveContent.add(moduleKey);
          }

          const durationMs = performance.now() - loadStarted;
          this.dispatchState(moduleKey, 'ready', { durationMs, revision: this.revisions.get(moduleKey) });

          window.dispatchEvent(new CustomEvent('cockpit:module-content-ready', {
            detail: { moduleKey }
          }));
        })
        .catch((error) => {
          if (error.name === 'AbortError') return;
          if (this.revisions.get(moduleKey) !== revision) return;
          const retry = () => this.load(moduleKey, { ...options, force: true });
          const existingContent = contentEl.innerHTML.trim();
          if (!existingContent) {
            const errorMsg = shell.getAttribute('data-error-message') || 'Refresh failed.';
            contentEl.innerHTML = '<p>' + errorMsg.replace(/</g, '&lt;') + '</p>'
              + '<button type="button" class="btn btn-ghost btn-xs" data-module-retry>Retry</button>';
          }
          window.dispatchEvent(new CustomEvent('cockpit:module-load-failed', {
            detail: {
              moduleKey,
              state: 'error',
              message: error.message || 'Load failed',
              retry
            }
          }));
          window.dispatchEvent(new CustomEvent('cockpit:module-state', {
            detail: {
              moduleKey,
              state: 'error',
              message: error.message || 'Load failed',
              retry
            }
          }));
        })
        .finally(() => {
          // Only clear in-flight tracking if a newer request has not superseded this one.
          if (this.revisions.get(moduleKey) === revision) {
            this.requests.delete(moduleKey);
            this.inflightModes.delete(moduleKey);
          }
        });
    }

    refresh(moduleKey, reason) {
      const shell = this._shells.get(moduleKey);
      if (!shell) return;

      const visible = this.isModuleVisible(moduleKey);

      if (!visible) {
        this.invalidate(moduleKey, reason);
        return;
      }

      if (this.preserveContent.has(moduleKey)) return;

      this.load(moduleKey, { force: true });
    }

    invalidate(moduleKey, reason) {
      const shell = this._shells.get(moduleKey);
      if (!shell) return;

      this.stale.add(moduleKey);
      const contentEl = shell.querySelector('[data-module-content]');
      if (contentEl) contentEl.setAttribute('data-module-stale', 'true');

      const visible = this.isModuleVisible(moduleKey);
      if (!visible) {
        const count = (this.attention.get(moduleKey) || 0) + 1;
        this.attention.set(moduleKey, count);
        shell.setAttribute('data-module-attention', String(count));
      }

      window.dispatchEvent(new CustomEvent('cockpit:module-state', {
        detail: { moduleKey, state: 'attention', count: this.attention.get(moduleKey) || 1 }
      }));
    }

    clearAttention(moduleKey) {
      this.attention.delete(moduleKey);
      const shell = this._shells.get(moduleKey);
      if (shell) {
        shell.removeAttribute('data-module-attention');
      }
    }

    dispatchState(moduleKey, state, extra = {}) {
      window.dispatchEvent(new CustomEvent('cockpit:module-state', {
        detail: { moduleKey, state, ...extra }
      }));
      if (state === 'ready') {
        window.dispatchEvent(new CustomEvent('cockpit:module-loaded', {
          detail: { moduleKey, state, ...extra }
        }));
      }
    }
  }

  window.CockpitModuleController = CockpitModuleController;

  function boot() {
    if (!window.cockpitLayoutConfig) {
      console.error('cockpitLayoutConfig missing; module controller not mounted.');
      return;
    }
    try {
      window.cockpitModules = new CockpitModuleController(window.cockpitLayoutConfig);
      window.cockpitModules.mount();
    } catch (error) {
      console.error('Failed to mount cockpit module controller', error);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
