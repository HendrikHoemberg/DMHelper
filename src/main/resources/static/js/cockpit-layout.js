(function () {
  'use strict';

  const ZONES = ['PRIMARY', 'LEFT_SUPPORT', 'RIGHT_SUPPORT', 'BOTTOM_UTILITY'];
  // Chrome rank by zone (spec 12.4): the shell is ranked from here, not the template.
  const ZONE_RANK = {
    PRIMARY: 'primary',
    LEFT_SUPPORT: 'support',
    RIGHT_SUPPORT: 'support',
    BOTTOM_UTILITY: 'utility'
  };
  const DEFAULT_KEY = 'builtin:exploration';
  // Per-preset compact rendering is fixed client-side configuration; COMPACT mode lives on.
  const PRESET_COMPACT_KEYS = {
    'builtin:exploration': ['session-plan', 'party', 'audio', 'reference'],
    'builtin:combat': ['story', 'party', 'quick-notes', 'reference', 'audio', 'session-log'],
    'builtin:theatre-of-mind': ['party', 'reference', 'quick-notes', 'audio', 'session-log'],
    'builtin:session-review': ['session-plan', 'quick-notes', 'party']
  };

  class CockpitLayoutController {
    constructor(config) {
      if (!config || !Array.isArray(config.modules) || !Array.isArray(config.presets)) {
        throw new Error('window.cockpitLayoutConfig is required.');
      }
      this.config = config;
      this.campaignId = String(config.campaignId);
      this.modules = new Map(config.modules.map((module) => [module.key, module]));
      this.presets = new Map(config.presets.map((preset) => [preset.key, preset]));
      this.workbench = document.querySelector('[data-cockpit-workbench]');
      this.depot = document.querySelector('[data-cockpit-depot]');
      this.picker = document.getElementById('cockpitPresetPicker');
      this.notice = document.getElementById('cockpitLayoutNotice');
      this.currentPresetKey = config.defaultPresetKey || DEFAULT_KEY;
      this.current = null;
      this.activeTabs = {};
      this.focusedModuleKey = null;
      this._focusReturnEl = null;
      this._focusHomePanel = null;
      this.attention = new Map();
      /** @type {Map<string, Function>} in-memory only — never serialized */
      this._retryCallbacks = new Map();
      this.metrics = { firstMeaningfulMs: null, lastApplyMs: null };
      this.mounted = false;
      this.collapsedZones = new Set();
      this._collapseButtons = new Map();
      this.focusLayer = document.getElementById('cockpitFocusLayer');
      this.focusReturn = document.getElementById('cockpitFocusReturn');
      this.focusMount = this.focusLayer?.querySelector('[data-focus-mount]') || null;
    }

    storageKey(kind) {
      return `dmhelper.cockpit.${kind}.v1.${this.campaignId}`;
    }

    mount() {
      if (!this.workbench || !this.depot) {
        console.error('Cockpit workbench markup is missing.');
        return;
      }
      const keys = Array.from(document.querySelectorAll('[data-module-key]'),
        (el) => el.getAttribute('data-module-key'));
      if (new Set(keys).size !== keys.length) throw new Error('Cockpit module shells must be unique.');
      if (keys.length !== this.modules.size) console.warn('Cockpit module shell count does not match registry.');
      this.bindControls();
      this.readCollapsedZones();
      let startKey = this.readLastPreset() || this.config.defaultPresetKey || DEFAULT_KEY;
      if (!this.presets.has(startKey)) {
        this.forgetKey('preset');
        startKey = DEFAULT_KEY;
      }
      this.applyPreset(startKey);
      this.enableControls();
      this.mounted = true;
    }
    enableControls() {
      if (!this.picker) return;
      this.picker.disabled = false;
      this.picker.removeAttribute('aria-disabled');
    }
    bindControls() {
      if (this.picker) {
        this.picker.addEventListener('change', () => {
          const key = this.picker.value;
          if (!this.applyPreset(key)) this.picker.value = this.currentPresetKey;
        });
      }
      this.workbench.addEventListener('click', (event) => {
        const collapseBtn = event.target.closest('[data-zone-collapse]');
        if (collapseBtn) {
          const zoneEl = collapseBtn.closest('[data-cockpit-zone]');
          if (zoneEl) {
            event.preventDefault();
            this.setZoneCollapsed(zoneEl.getAttribute('data-cockpit-zone'), zoneEl.dataset.collapsed !== 'true');
          }
          return;
        }
        const tab = event.target.closest('[data-module-tab]');
        if (!tab || !this.workbench.contains(tab)) return;
        const zoneEl = tab.closest('[data-cockpit-zone]');
        if (!zoneEl) return;
        event.preventDefault();
        this.selectTab(zoneEl.getAttribute('data-cockpit-zone'), tab.getAttribute('data-module-tab'));
      });
      // renderLayout rebuilds tab strips, so keep a handle to re-append the button.
      for (const zone of ZONES) {
        const zoneEl = this.workbench.querySelector(`[data-cockpit-zone="${zone}"]`);
        const button = zoneEl?.querySelector('[data-zone-collapse]') || null;
        if (button) this._collapseButtons.set(zone, button);
      }
      this.bindFocusChrome();
      this.bindPresetShortcuts();
      this.bindTabKeyboard();
      this.bindModuleState();
    }
    bindPresetShortcuts() {
      const BUILTIN_BY_DIGIT = {
        Digit1: 'builtin:exploration',
        Digit2: 'builtin:combat',
        Digit3: 'builtin:theatre-of-mind',
        Digit4: 'builtin:session-review'
      };
      document.addEventListener('keydown', (event) => {
        if (event.ctrlKey || event.metaKey) return;
        const presetKey = BUILTIN_BY_DIGIT[event.code];
        if (!presetKey) return;
        if (this.isModalOpen()) return;
        if (this.isEditableTarget(event.target)) return;
        event.preventDefault();
        this.applyPreset(presetKey);
      });
    }
    bindTabKeyboard() {
      this.workbench.addEventListener('keydown', (event) => {
        const tab = event.target.closest('[role="tab"][data-module-tab]');
        if (!tab || !this.workbench.contains(tab)) return;
        const tabsEl = tab.closest('[role="tablist"]');
        if (!tabsEl) return;
        const tabs = Array.from(tabsEl.querySelectorAll('[role="tab"][data-module-tab]'));
        if (tabs.length === 0) return;
        const index = tabs.indexOf(tab);
        let next = -1;
        if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
          next = (index + 1) % tabs.length;
        } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
          next = (index - 1 + tabs.length) % tabs.length;
        } else if (event.key === 'Home') {
          next = 0;
        } else if (event.key === 'End') {
          next = tabs.length - 1;
        } else {
          return;
        }
        event.preventDefault();
        const zoneEl = tab.closest('[data-cockpit-zone]');
        if (!zoneEl) return;
        const zone = zoneEl.getAttribute('data-cockpit-zone');
        const key = tabs[next].getAttribute('data-module-tab');
        this.selectTab(zone, key);
        tabsEl.querySelector(`[role="tab"][data-module-tab="${key}"]`)?.focus();
      });
    }
    isEditableTarget(target) {
      if (!target || !(target instanceof Element)) return false;
      if (target.isContentEditable) return true;
      return !!target.closest('input, textarea, select, [contenteditable="true"]');
    }
    bindFocusChrome() {
      document.addEventListener('click', (event) => {
        const focusBtn = event.target.closest('[data-module-focus]');
        if (!focusBtn) return;
        const shell = focusBtn.closest('[data-module-key]');
        if (!shell) return;
        event.preventDefault();
        this.focusModule(shell.getAttribute('data-module-key'), focusBtn);
      });
      if (this.focusReturn) this.focusReturn.addEventListener('click', () => this.restoreFocus());
      document.addEventListener('keydown', (event) => {
        if (event.key === 'Tab' && this.focusedModuleKey && this.focusLayer
            && !this.focusLayer.hidden) {
          const focusable = Array.from(this.focusLayer.querySelectorAll(
            'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), '
            + 'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
          )).filter((element) => {
            const style = window.getComputedStyle(element);
            return !element.hidden && style.display !== 'none'
              && style.visibility !== 'hidden' && element.getClientRects().length > 0;
          });
          if (focusable.length === 0) {
            event.preventDefault();
            this.focusReturn?.focus();
            return;
          }
          const first = focusable[0];
          const last = focusable[focusable.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
          }
          return;
        }
        if (event.key !== 'Escape') return;
        if (this.focusedModuleKey == null || this.isModalOpen()) return;
        event.preventDefault();
        this.restoreFocus();
      });
    }
    bindModuleState() {
      window.addEventListener('cockpit:module-state', (event) => {
        const detail = event.detail || {};
        const key = detail.moduleKey;
        const state = detail.state;
        if (!key || !state) return;
        const allowed = new Set(['loading', 'ready', 'empty', 'error', 'attention']);
        if (!allowed.has(state)) return;
        if (state === 'attention') {
          const prev = this.attention.get(key) || 0;
          this.setAttention(key, detail.count != null ? detail.count : prev + 1);
          return;
        }
        this.setModuleState(key, state, detail);
      });
      document.addEventListener('click', (event) => {
        const retryBtn = event.target.closest('[data-module-retry]');
        if (!retryBtn) return;
        const shell = retryBtn.closest('[data-module-key]');
        if (!shell) return;
        event.preventDefault();
        this.retryModule(shell.getAttribute('data-module-key'));
      });
    }
    isModalOpen() {
      return !!document.querySelector('dialog[open]');
    }
    applyPreset(key) {
      const preset = this.presets.get(key);
      if (!preset || !preset.moduleKeysByZone) {
        this.showNotice('That layout preset is unavailable.');
        return false;
      }
      // Focus moves a live shell outside its zone; restore it before panels are rebuilt.
      if (this.focusedModuleKey) this.restoreFocus({ silent: true });

      const started = performance.now();
      this.current = preset;
      this.activeTabs = {};
      for (const zone of ZONES) {
        const moduleKeys = preset.moduleKeysByZone[zone] || [];
        this.activeTabs[zone] = moduleKeys.length ? moduleKeys[0] : null;
      }
      this.currentPresetKey = key;
      this.workbench.setAttribute('data-cockpit-preset', key);
      this.renderLayout();
      if (this.picker) this.picker.value = key;
      this.writeJson('preset', key);
      this.emitAllVisibility();

      this.metrics.lastApplyMs = performance.now() - started;
      if (this.metrics.firstMeaningfulMs === null) this.metrics.firstMeaningfulMs = performance.now();
      window.dispatchEvent(new CustomEvent('cockpit:layout-applied', {
        detail: { presetKey: key, durationMs: this.metrics.lastApplyMs }
      }));
      return true;
    }
    renderLayout() {
      if (!this.current) return;
      const layout = this.current;
      const placed = new Set();
      const compact = new Set(PRESET_COMPACT_KEYS[this.currentPresetKey] || []);

      for (const zone of ZONES) {
        const zoneEl = this.workbench.querySelector(`[data-cockpit-zone="${zone}"]`);
        if (!zoneEl) continue;
        const moduleKeys = (layout.moduleKeysByZone?.[zone] || []).slice();
        let active = this.activeTabs[zone];
        if (!moduleKeys.includes(active)) this.activeTabs[zone] = active = moduleKeys[0] || null;

        const stacked = this.supportZoneShouldStack(zone, zoneEl, moduleKeys);
        zoneEl.setAttribute('data-zone', zone);
        zoneEl.dataset.zoneMode = stacked ? 'stacked' : 'tabs';

        const tabsEl = zoneEl.querySelector('.cockpit-zone__tabs');
        const panelsEl = zoneEl.querySelector('[data-zone-panels]');
        if (!tabsEl || !panelsEl) continue;

        panelsEl.querySelectorAll('[data-module-panel]').forEach((panel) => {
          const key = panel.getAttribute('data-module-panel');
          if (!moduleKeys.includes(key)) {
            const mod = panel.querySelector('[data-module-key]');
            if (mod && this.depot) this.depot.appendChild(mod);
            panel.remove();
          }
        });

        tabsEl.replaceChildren();
        tabsEl.hidden = stacked;
        moduleKeys.forEach((key) => {
          const def = this.modules.get(key);
          const title = def?.title || key;
          const tabId = `cockpitTab-${zone}-${key}`;
          const panelId = `cockpitPanel-${zone}-${key}`;
          const selected = key === active;
          const attention = this.attention.get(key) || 0;

          if (!stacked) {
            const tab = document.createElement('button');
            tab.type = 'button';
            tab.className = 'cockpit-zone__tab';
            tab.setAttribute('role', 'tab');
            tab.id = tabId;
            tab.setAttribute('aria-controls', panelId);
            tab.setAttribute('aria-selected', selected ? 'true' : 'false');
            tab.setAttribute('data-module-tab', key);
            if (!selected) tab.tabIndex = -1;
            const tabLabel = document.createElement('span');
            tabLabel.className = 'cockpit-zone__tab-label';
            tabLabel.textContent = title;
            tab.appendChild(tabLabel);
            if (attention > 0 && !selected) {
              const badge = document.createElement('span');
              badge.className = 'cockpit-module__attention';
              badge.textContent = String(attention);
              badge.setAttribute('aria-label', `${attention} updates`);
              tab.appendChild(badge);
            }
            tabsEl.appendChild(tab);
          }

          let panel = panelsEl.querySelector(`[data-module-panel="${key}"]`);
          if (!panel) {
            panel = document.createElement('div');
            panel.className = 'cockpit-zone__panel';
            panel.setAttribute('data-module-panel', key);
            panelsEl.appendChild(panel);
          }
          panel.id = panelId;
          if (stacked) {
            panel.setAttribute('role', 'region');
            panel.setAttribute('aria-label', title);
          } else {
            panel.setAttribute('role', 'tabpanel');
            panel.setAttribute('aria-labelledby', tabId);
          }

          let shell = panel.querySelector(`[data-module-key="${key}"]`);
          if (!shell && this.focusedModuleKey !== key) {
            shell = document.querySelector(`[data-module-key="${key}"]`);
            if (shell && this.focusMount && this.focusMount.contains(shell)) {
              shell = null;
            } else if (shell) {
              panel.appendChild(shell);
            }
          }
          if (shell) {
            shell.dataset.moduleRank = ZONE_RANK[zone] || 'support';
            if (def?.compactSupported && compact.has(key) && (selected || stacked)) {
              shell.setAttribute('data-compact', 'true');
            } else {
              shell.removeAttribute('data-compact');
            }
            const shellBadge = shell.querySelector('.cockpit-module__attention');
            if (shellBadge) {
              if (attention > 0 && !selected) {
                shellBadge.hidden = false;
                shellBadge.textContent = String(attention);
                shellBadge.setAttribute('aria-label', `${attention} updates`);
              } else {
                shellBadge.hidden = true;
                shellBadge.textContent = '';
              }
            }
          }

          if (selected || stacked) {
            panel.hidden = false;
            panel.removeAttribute('inert');
          } else {
            panel.hidden = true;
            panel.setAttribute('inert', '');
          }
          placed.add(key);
        });

        const collapsed = this.collapsedZones.has(zone);
        zoneEl.dataset.collapsed = collapsed ? 'true' : 'false';
        const collapseBtn = this._collapseButtons.get(zone);
        if (collapseBtn) {
          collapseBtn.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
          tabsEl.appendChild(collapseBtn);
        }
      }

      this.workbench.dataset.bottomCollapsed =
        this.collapsedZones.has('BOTTOM_UTILITY') ? 'true' : 'false';

      document.querySelectorAll('[data-module-key]').forEach((shell) => {
        const key = shell.getAttribute('data-module-key');
        if (!placed.has(key) && this.depot && !this.depot.contains(shell)) {
          this.depot.appendChild(shell);
          shell.removeAttribute('data-compact');
        }
      });

      this.modules.forEach((_definition, key) => window.dispatchEvent(new CustomEvent('cockpit:module-mode',
        { detail: { moduleKey: key, mode: this.moduleMode(key) } })));
      const rightZone = this.workbench.querySelector('[data-cockpit-zone="RIGHT_SUPPORT"]');
      if (rightZone) document.documentElement.style.setProperty(
        '--cockpit-right-zone-width', rightZone.getBoundingClientRect().width + 'px');
    }
    selectTab(zone, key) {
      const moduleKeys = this.current?.moduleKeysByZone?.[zone];
      if (!Array.isArray(moduleKeys) || !moduleKeys.includes(key)) return;
      this.activeTabs[zone] = key;
      this.renderLayout();
      this.clearAttention(key);
      this.emitAllVisibility();
    }
    revealQuickNotesCapture() {
      const zone = this.findModuleZone('story');
      const input = document.querySelector('[data-module-key="story"] .quicknotes-form input');
      if (!zone || !input) {
        this.showNotice('Add the Story module to use quick-note capture.');
        return false;
      }
      this.selectTab(zone, 'story');
      input.scrollIntoView?.({ block: 'nearest' });
      input.focus();
      return document.activeElement === input;
    }
    findModuleZone(key) {
      if (!this.current?.moduleKeysByZone) return null;
      for (const zone of ZONES) {
        const keys = this.current.moduleKeysByZone[zone];
        if (Array.isArray(keys) && keys.includes(key)) return zone;
      }
      return null;
    }
    // Spec 12.4: a support zone stacks while wide enough for the widest at min width.
    supportZoneShouldStack(zone, zoneEl, moduleKeys) {
      if (zone !== 'LEFT_SUPPORT' && zone !== 'RIGHT_SUPPORT') return false;
      if (moduleKeys.length < 2) return false;
      const widest = Math.max(...moduleKeys.map((key) => this.modules.get(key)?.minWidthPx || 0));
      if (widest === 0) return false;
      return zoneEl.getBoundingClientRect().width > widest;
    }
    setZoneCollapsed(zone, collapsed) {
      if (collapsed) this.collapsedZones.add(zone);
      else this.collapsedZones.delete(zone);
      this.writeJson('collapsed', Array.from(this.collapsedZones));
      this.renderLayout();
      this.emitAllVisibility();
    }
    readCollapsedZones() {
      const stored = this.readJson('collapsed');
      if (!Array.isArray(stored)) return;
      for (const zone of stored) {
        if (ZONES.includes(zone)) this.collapsedZones.add(zone);
      }
    }
    /* ---- Focus ---- */
    focusModule(key, returnElement = document.activeElement) {
      const def = this.modules.get(key);
      if (!def || !def.focusSupported) return false;
      const shellSelector = `[data-module-key="${key}"]`;
      const openerShell = returnElement?.closest?.(shellSelector);
      const shell = openerShell || Array.from(document.querySelectorAll(shellSelector))
        .find((candidate) => {
          const style = window.getComputedStyle(candidate);
          return !candidate.hidden && style.display !== 'none'
            && style.visibility !== 'hidden' && candidate.getClientRects().length > 0;
        });
      if (!shell || !this.focusLayer || !this.focusMount) return false;
      if (this.focusedModuleKey) this.restoreFocus({ silent: true });

      this._focusReturnEl = returnElement || document.activeElement;
      this._focusHomePanel = shell.closest('[data-module-panel]') || shell.parentElement;
      this.focusedModuleKey = key;
      this.focusMount.appendChild(shell);
      this.workbench.setAttribute('inert', '');
      this.focusLayer.hidden = false;
      this.clearAttention(key);
      this.emitAllVisibility();
      window.dispatchEvent(new CustomEvent('cockpit:module-mode', {
        detail: { moduleKey: key, mode: this.moduleMode(key) }
      }));
      this.focusReturn?.focus();
      return true;
    }
    restoreFocus(options = {}) {
      if (!this.focusedModuleKey) return;
      const key = this.focusedModuleKey;
      const shell = this.focusMount?.querySelector(`[data-module-key="${key}"]`)
        || document.querySelector(`[data-module-key="${key}"]`);
      if (shell && this._focusHomePanel) {
        this._focusHomePanel.appendChild(shell);
      } else if (shell) {
        this.renderLayout();
      }
      this.focusedModuleKey = null;
      this._focusHomePanel = null;
      this.workbench.removeAttribute('inert');
      if (this.focusLayer) this.focusLayer.hidden = true;
      this.emitAllVisibility();
      this.modules.forEach((_definition, mk) => window.dispatchEvent(new CustomEvent('cockpit:module-mode',
        { detail: { moduleKey: mk, mode: this.moduleMode(mk) } })));
      if (!options.silent) {
        const prefer = this._focusReturnEl;
        this._focusReturnEl = null;
        const focusBtn = document.querySelector(`[data-module-key="${key}"] [data-module-focus]`);
        const target = (prefer && document.contains(prefer))
          ? prefer
          : ((focusBtn && document.contains(focusBtn)) ? focusBtn : null);
        target?.focus?.();
      } else {
        this._focusReturnEl = null;
      }
    }
    // Module chrome states; existing body stays on loading/error so failures recover.
    setModuleState(key, state, detail = {}) {
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      if (!shell) return;
      const status = shell.querySelector('[data-module-status]');
      const error = shell.querySelector('[data-module-error]');
      const body = shell.querySelector('[data-module-body]');
      const def = this.modules.get(key);
      const loadingMsg = def?.states?.loadingMessage
        || shell.getAttribute('data-loading-message') || 'Loading…';
      const emptyMsg = detail.message
        || def?.states?.emptyMessage
        || shell.getAttribute('data-empty-message') || '';
      const errorMsg = detail.message
        || def?.states?.errorMessage
        || shell.getAttribute('data-error-message') || 'Refresh failed.';

      if (typeof detail.retry === 'function') this._retryCallbacks.set(key, detail.retry);
      else if (state === 'ready' || state === 'empty') this._retryCallbacks.delete(key);

      if (status) {
        if (state === 'loading') {
          status.hidden = false;
          status.textContent = loadingMsg;
        } else if (state === 'empty') {
          status.hidden = false;
          status.textContent = emptyMsg;
        } else {
          status.hidden = true;
          status.textContent = '';
        }
      }
      if (error) {
        if (state === 'error') {
          error.hidden = false;
          const span = error.querySelector('span');
          if (span) span.textContent = errorMsg;
        } else {
          error.hidden = true;
        }
      }
      // A mismatched body stays hidden while its request loads/fails.
      const modeMismatch = body?.querySelector(
        '[data-module-content][data-module-mode-mismatch]') != null;
      if (body && modeMismatch && (state === 'loading' || state === 'error')) {
        body.hidden = true;
      } else if (body && (state === 'ready' || state === 'loading' || state === 'error')) {
        body.hidden = false;
      }
    }
    async retryModule(key) {
      const cb = this._retryCallbacks.get(key);
      this.setModuleState(key, 'loading');
      if (typeof cb !== 'function') return;
      try {
        await cb();
      } catch (error) {
        this.setModuleState(key, 'error', { message: error?.message, retry: cb });
      }
    }
    setAttention(moduleKey, count = 1) {
      this.attention.set(moduleKey, count);
      this.renderLayout();
    }
    clearAttention(moduleKey) {
      if (this.attention.has(moduleKey)) {
        this.attention.delete(moduleKey);
        this.renderLayout();
      }
    }
    revealModule(key) {
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      const zoneEl = shell?.closest('[data-cockpit-zone]');
      if (!zoneEl) return false;
      const zone = zoneEl.getAttribute('data-cockpit-zone');
      if (this.collapsedZones.has(zone)) this.setZoneCollapsed(zone, false);
      this.selectTab(zone, key);
      return this.isModuleVisible(key);
    }
    isModuleVisible(key) {
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      if (!shell) return false;
      if (this.focusedModuleKey === key) {
        return !!(this.focusMount && this.focusMount.contains(shell));
      }
      if (this.depot && this.depot.contains(shell)) return false;
      const panel = shell.closest('[role="tabpanel"], [role="region"]');
      const zone = shell.closest('[data-cockpit-zone]');
      if (!panel || !zone) return false;
      return !panel.hidden && zone.dataset.collapsed !== 'true';
    }
    moduleMode(key) {
      if (this.focusedModuleKey === key) return 'FOCUSED';
      if ((PRESET_COMPACT_KEYS[this.currentPresetKey] || []).includes(key)) return 'COMPACT';
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      if (shell && shell.getAttribute('data-compact') === 'true') return 'COMPACT';
      return 'STANDARD';
    }
    emitAllVisibility() {
      for (const key of this.modules.keys()) {
        window.dispatchEvent(new CustomEvent('cockpit:module-visibility',
          { detail: { moduleKey: key, visible: this.isModuleVisible(key) } }));
      }
    }
    readLastPreset() {
      try {
        const raw = localStorage.getItem(this.storageKey('preset'));
        return raw && raw.startsWith('"') ? JSON.parse(raw) : raw;
      } catch (_) {
        this.forgetKey('preset');
        return null;
      }
    }
    readJson(kind) {
      try {
        const raw = localStorage.getItem(this.storageKey(kind));
        return raw ? JSON.parse(raw) : null;
      } catch (_) {
        this.forgetKey(kind);
        return null;
      }
    }
    writeJson(kind, value) {
      try {
        localStorage.setItem(this.storageKey(kind), JSON.stringify(value));
      } catch (_) {
        // ignore storage errors
      }
    }
    forgetKey(kind) {
      try {
        localStorage.removeItem(this.storageKey(kind));
      } catch (_) {
        // ignore
      }
      this.showNotice('The saved device layout state was invalid and has been ignored.');
    }
    showNotice(message) {
      if (!this.notice) return;
      this.notice.textContent = message || '';
    }
    offerPresetSwitch(message, presetKey, actionLabel) {
      if (!this.notice) return;
      this.notice.replaceChildren();
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn btn-primary';
      btn.textContent = actionLabel;
      btn.addEventListener('click', () => {
        this.notice.replaceChildren();
        this.applyPreset(presetKey);
      });
      this.notice.append(document.createTextNode(message + ' '), btn);
    }

  }

  window.CockpitLayoutController = CockpitLayoutController;

  function boot() {
    if (!window.cockpitLayoutConfig) {
      console.error('cockpitLayoutConfig missing; layout controller not mounted.');
      return;
    }
    try {
      window.cockpitLayout = new CockpitLayoutController(window.cockpitLayoutConfig);
      window.cockpitLayout.mount();
    } catch (error) {
      console.error('Failed to mount cockpit layout controller', error);
    }
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
