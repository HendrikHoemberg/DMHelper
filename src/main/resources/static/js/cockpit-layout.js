(function () {
  'use strict';

  const ZONES = ['PRIMARY', 'LEFT_SUPPORT', 'RIGHT_SUPPORT', 'BOTTOM_UTILITY'];
  const DEFAULT_KEY = 'builtin:exploration';
  const API_BASE = '/api/v1/cockpit-layout';
  const INVALID_STORAGE_MSG =
    'The saved device layout state was invalid and has been ignored.';

  class CockpitLayoutController {
    constructor(config) {
      if (!config || !Array.isArray(config.modules) || !Array.isArray(config.presets)) {
        throw new Error('window.cockpitLayoutConfig is required.');
      }
      this.config = config;
      this.campaignId = String(config.campaignId);
      this.modules = new Map(config.modules.map((module) => [module.key, module]));
      this.presets = new Map(config.presets.map((preset) => [preset.key, this.clone(preset)]));
      this.builtInCatalog = new Map(
        config.presets
          .filter((preset) => preset.builtIn)
          .map((preset) => [preset.key, this.clone(preset)])
      );
      this.workbench = document.querySelector('[data-cockpit-workbench]');
      this.depot = document.querySelector('[data-cockpit-depot]');
      this.picker = document.getElementById('cockpitPresetPicker');
      this.modeButton = document.getElementById('cockpitLayoutModeButton');
      this.notice = document.getElementById('cockpitLayoutNotice');
      this.exitDialog = document.getElementById('cockpitLayoutExitDialog');
      this.nameDialog = document.getElementById('cockpitPresetNameDialog');
      this.nameInput = document.getElementById('cockpitPresetNameInput');
      this.currentPresetKey = config.defaultPresetKey || DEFAULT_KEY;
      this.current = null;
      this.editSnapshot = null;
      this.focusedModuleKey = null;
      this.attention = new Map();
      this.metrics = { firstMeaningfulMs: null, lastApplyMs: null };
      this.mounted = false;
      this._pendingNameResolve = null;
      this._draftPromptShown = false;
    }

    clone(value) {
      return window.structuredClone
        ? window.structuredClone(value)
        : JSON.parse(JSON.stringify(value));
    }

    storageKey(kind) {
      return `dmhelper.cockpit.${kind}.v1.${this.campaignId}`;
    }

    mount() {
      if (!this.workbench || !this.depot) {
        console.error('Cockpit workbench markup is missing.');
        return;
      }

      const shells = document.querySelectorAll('[data-module-key]');
      const keys = Array.from(shells).map((el) => el.getAttribute('data-module-key'));
      if (new Set(keys).size !== keys.length) {
        throw new Error('Cockpit module shells must be unique.');
      }
      if (keys.length !== this.modules.size) {
        console.warn('Cockpit module shell count does not match registry.');
      }

      this.bindControls();
      this.lockMode({ silent: true });

      let startKey = this.readLastPreset() || this.config.defaultPresetKey || DEFAULT_KEY;
      if (!this.presets.has(startKey)) {
        startKey = DEFAULT_KEY;
      }

      this.applyPreset(startKey, { skipDirtyCheck: true });
      this.enableControls();
      this.maybeOfferDraftRecovery();
      this.mounted = true;
    }

    enableControls() {
      if (this.picker) {
        this.picker.disabled = false;
        this.picker.removeAttribute('aria-disabled');
      }
      if (this.modeButton) {
        this.modeButton.disabled = false;
        this.modeButton.removeAttribute('aria-disabled');
      }
      const overflow = document.getElementById('cockpitPresetOverflow');
      if (overflow) {
        overflow.hidden = false;
        overflow.querySelectorAll('button').forEach((btn) => {
          btn.disabled = false;
          btn.removeAttribute('aria-disabled');
        });
      }
      this.syncPresetActionState();
    }

    bindControls() {
      if (this.picker) {
        this.picker.addEventListener('change', () => {
          const key = this.picker.value;
          if (!this.applyPreset(key)) {
            this.picker.value = this.currentPresetKey;
          }
        });
      }

      if (this.modeButton) {
        this.modeButton.addEventListener('click', () => {
          if (this.workbench.dataset.layoutMode === 'edit') {
            this.requestExitEditMode();
          } else {
            this.enterEditMode();
          }
        });
      }

      this.workbench.addEventListener('click', (event) => {
        const tab = event.target.closest('[data-module-tab]');
        if (!tab || !this.workbench.contains(tab)) return;
        const zoneEl = tab.closest('[data-cockpit-zone]');
        if (!zoneEl) return;
        event.preventDefault();
        this.selectTab(zoneEl.getAttribute('data-cockpit-zone'), tab.getAttribute('data-module-tab'));
      });

      document.querySelectorAll('[data-preset-action]').forEach((btn) => {
        btn.addEventListener('click', () => {
          const action = btn.getAttribute('data-preset-action');
          if (action === 'duplicate') this.duplicatePreset();
          else if (action === 'rename') this.renamePreset();
          else if (action === 'delete') this.deletePreset();
          else if (action === 'restore') this.restoreBuiltIn();
        });
      });

      if (this.exitDialog) {
        this.exitDialog.querySelectorAll('[data-layout-exit]').forEach((btn) => {
          btn.addEventListener('click', () => {
            const action = btn.getAttribute('data-layout-exit');
            this.exitDialog.close();
            if (action === 'save') this.saveEdit();
            else if (action === 'discard') this.discardEdit();
          });
        });
      }

      if (this.nameDialog) {
        this.nameDialog.querySelectorAll('[data-preset-name]').forEach((btn) => {
          btn.addEventListener('click', () => {
            const action = btn.getAttribute('data-preset-name');
            if (action === 'confirm') {
              const value = (this.nameInput?.value || '').trim();
              this.nameDialog.close();
              if (this._pendingNameResolve) {
                const resolve = this._pendingNameResolve;
                this._pendingNameResolve = null;
                resolve(value || null);
              }
            } else {
              this.nameDialog.close();
              if (this._pendingNameResolve) {
                const resolve = this._pendingNameResolve;
                this._pendingNameResolve = null;
                resolve(null);
              }
            }
          });
        });
      }
    }

    applyPreset(key, options = {}) {
      if (!options.skipDirtyCheck
          && this.workbench.dataset.layoutMode === 'edit'
          && this.isDirty()) {
        this.showNotice('Save or discard layout changes before switching presets.');
        return false;
      }
      const preset = this.presets.get(key);
      if (!preset || !preset.layout) {
        this.showNotice('That layout preset is unavailable. Exploration was used instead.');
        key = DEFAULT_KEY;
        const fallback = this.presets.get(key);
        if (!fallback) return false;
        return this.applyPreset(key, { skipDirtyCheck: true });
      }

      const started = performance.now();
      this.current = this.clone(preset.layout);
      this.overlayActiveTabs(this.current);
      this.currentPresetKey = key;
      this.renderLayout();

      if (this.picker) this.picker.value = key;
      this.writeLastPreset(key);
      this.emitAllVisibility();
      this.syncPresetActionState();

      if (this.workbench.dataset.layoutMode === 'edit' && !options.keepEdit) {
        this.lockMode({ silent: true });
      }

      const durationMs = performance.now() - started;
      this.metrics.lastApplyMs = durationMs;
      if (this.metrics.firstMeaningfulMs === null) {
        this.metrics.firstMeaningfulMs = performance.now();
      }
      window.dispatchEvent(new CustomEvent('cockpit:layout-applied', {
        detail: { presetKey: key, durationMs }
      }));
      return true;
    }

    overlayActiveTabs(layout) {
      const stored = this.readJson('active-tabs');
      if (!stored || typeof stored !== 'object') return;
      for (const zone of ZONES) {
        const zoneLayout = layout.zones?.[zone];
        const key = stored[zone];
        if (!zoneLayout || !key) continue;
        if (Array.isArray(zoneLayout.moduleKeys) && zoneLayout.moduleKeys.includes(key)) {
          zoneLayout.activeModuleKey = key;
        }
      }
    }

    collectActiveTabs() {
      const tabs = {};
      if (!this.current?.zones) return tabs;
      for (const zone of ZONES) {
        const zoneLayout = this.current.zones[zone];
        if (zoneLayout?.activeModuleKey) tabs[zone] = zoneLayout.activeModuleKey;
      }
      return tabs;
    }

    renderLayout() {
      if (!this.current) return;
      const layout = this.current;
      const placed = new Set();
      const compact = new Set(layout.compactModuleKeys || []);

      for (const zone of ZONES) {
        const zoneEl = this.workbench.querySelector(`[data-cockpit-zone="${zone}"]`);
        if (!zoneEl) continue;
        const zoneLayout = layout.zones?.[zone] || {
          moduleKeys: [],
          activeModuleKey: null,
          collapsed: false
        };
        const moduleKeys = Array.isArray(zoneLayout.moduleKeys) ? zoneLayout.moduleKeys.slice() : [];
        let active = zoneLayout.activeModuleKey;
        if (!active || !moduleKeys.includes(active)) {
          active = moduleKeys[0] || null;
          zoneLayout.activeModuleKey = active;
        }

        const tabsEl = zoneEl.querySelector('.cockpit-zone__tabs');
        const panelsEl = zoneEl.querySelector('[data-zone-panels]');
        if (!tabsEl || !panelsEl) continue;

        const existingPanels = new Map();
        panelsEl.querySelectorAll('[data-module-panel]').forEach((panel) => {
          existingPanels.set(panel.getAttribute('data-module-panel'), panel);
        });

        for (const [key, panel] of existingPanels) {
          if (!moduleKeys.includes(key)) {
            const mod = panel.querySelector('[data-module-key]');
            if (mod && this.depot) this.depot.appendChild(mod);
            panel.remove();
            existingPanels.delete(key);
          }
        }

        tabsEl.replaceChildren();
        moduleKeys.forEach((key) => {
          const def = this.modules.get(key);
          const title = def?.title || key;
          const tabId = `cockpitTab-${zone}-${key}`;
          const panelId = `cockpitPanel-${zone}-${key}`;
          const selected = key === active;

          const tab = document.createElement('button');
          tab.type = 'button';
          tab.className = 'cockpit-zone__tab';
          tab.setAttribute('role', 'tab');
          tab.id = tabId;
          tab.setAttribute('aria-controls', panelId);
          tab.setAttribute('aria-selected', selected ? 'true' : 'false');
          tab.setAttribute('data-module-tab', key);
          if (!selected) tab.tabIndex = -1;
          tab.textContent = title;
          const attention = this.attention.get(key) || 0;
          if (attention > 0 && !selected) {
            const badge = document.createElement('span');
            badge.className = 'cockpit-module__attention';
            badge.textContent = String(attention);
            badge.setAttribute('aria-label', `${attention} updates`);
            tab.appendChild(badge);
          }
          tabsEl.appendChild(tab);

          let panel = existingPanels.get(key);
          if (!panel) {
            panel = document.createElement('div');
            panel.className = 'cockpit-zone__panel';
            panel.setAttribute('role', 'tabpanel');
            panel.setAttribute('data-module-panel', key);
            panelsEl.appendChild(panel);
          }
          panel.id = panelId;
          panel.setAttribute('aria-labelledby', tabId);

          let shell = panel.querySelector(`[data-module-key="${key}"]`);
          if (!shell) {
            shell = document.querySelector(`[data-module-key="${key}"]`);
            if (shell) panel.appendChild(shell);
          }
          if (shell) {
            if (def?.compactSupported && compact.has(key) && selected) {
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

          if (selected) {
            panel.hidden = false;
            panel.removeAttribute('inert');
          } else {
            panel.hidden = true;
            panel.setAttribute('inert', '');
          }
          placed.add(key);
        });

        // Drop leftover panels that are no longer assigned
        panelsEl.querySelectorAll('[data-module-panel]').forEach((panel) => {
          const key = panel.getAttribute('data-module-panel');
          if (!moduleKeys.includes(key)) {
            const mod = panel.querySelector('[data-module-key]');
            if (mod && this.depot) this.depot.appendChild(mod);
            panel.remove();
          }
        });

        zoneEl.dataset.collapsed = zoneLayout.collapsed ? 'true' : 'false';
      }

      const bottom = layout.zones?.BOTTOM_UTILITY;
      this.workbench.dataset.bottomCollapsed = bottom?.collapsed ? 'true' : 'false';

      document.querySelectorAll('[data-module-key]').forEach((shell) => {
        const key = shell.getAttribute('data-module-key');
        if (!placed.has(key) && this.depot && !this.depot.contains(shell)) {
          this.depot.appendChild(shell);
          shell.removeAttribute('data-compact');
        }
      });

      const ratios = layout.ratios || { left: 0.2, primary: 0.56, right: 0.24, bottom: 0.24 };
      this.workbench.style.setProperty('--left-size', `${ratios.left * 100}fr`);
      this.workbench.style.setProperty('--primary-size', `${ratios.primary * 100}fr`);
      this.workbench.style.setProperty('--right-size', `${ratios.right * 100}fr`);
      const topShare = Math.max(0.01, 1 - (ratios.bottom || 0));
      this.workbench.style.setProperty('--top-size', `${topShare * 100}fr`);
      this.workbench.style.setProperty('--bottom-size', `${(ratios.bottom || 0) * 100}fr`);
    }

    selectTab(zone, key) {
      const zoneLayout = this.current?.zones?.[zone];
      if (!zoneLayout || !zoneLayout.moduleKeys?.includes(key)) return;
      zoneLayout.activeModuleKey = key;
      this.renderLayout();
      this.writeJson('active-tabs', this.collectActiveTabs());
      this.clearAttention(key);
      this.emitAllVisibility();
    }

    setModuleState(key, state, detail = {}) {
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      if (!shell) return;
      const status = shell.querySelector('[data-module-status]');
      const error = shell.querySelector('[data-module-error]');
      const def = this.modules.get(key);
      if (status) {
        if (state === 'loading') {
          status.hidden = false;
          status.textContent = def?.states?.loadingMessage || 'Loading…';
        } else if (state === 'empty') {
          status.hidden = false;
          status.textContent = detail.message || def?.states?.emptyMessage || '';
        } else if (state === 'ready' || state === 'idle') {
          status.hidden = true;
          status.textContent = '';
        } else {
          status.hidden = true;
        }
      }
      if (error) {
        if (state === 'error') {
          error.hidden = false;
          const span = error.querySelector('span');
          if (span) {
            span.textContent = detail.message || def?.states?.errorMessage || 'Refresh failed.';
          }
        } else {
          error.hidden = true;
        }
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

    emitAllVisibility() {
      for (const key of this.modules.keys()) {
        const shell = document.querySelector(`[data-module-key="${key}"]`);
        const panel = shell?.closest('[role="tabpanel"]');
        const zone = shell?.closest('[data-cockpit-zone]');
        const visible = !!(
          shell
          && zone
          && panel
          && !panel.hidden
          && zone.dataset.collapsed !== 'true'
          && this.depot
          && !this.depot.contains(shell)
        );
        window.dispatchEvent(new CustomEvent('cockpit:module-visibility', {
          detail: { moduleKey: key, visible }
        }));
      }
    }

    enterEditMode() {
      if (!this.current) return;
      this.editSnapshot = this.clone(this.current);
      this.workbench.dataset.layoutMode = 'edit';
      document.querySelectorAll('[data-layout-edit-only]').forEach((el) => {
        el.hidden = false;
        if ('disabled' in el) {
          el.disabled = false;
          el.removeAttribute('aria-disabled');
        }
      });
      document.querySelectorAll('[data-cockpit-splitter]').forEach((splitter) => {
        splitter.tabIndex = 0;
      });
      if (this.modeButton) this.modeButton.textContent = 'Done';
      this.persistDraft();
    }

    requestExitEditMode() {
      if (this.workbench.dataset.layoutMode !== 'edit') return;
      if (!this.isDirty()) {
        this.lockMode();
        this.clearStorage('edit-draft');
        return;
      }
      if (this.exitDialog && typeof this.exitDialog.showModal === 'function') {
        this.exitDialog.showModal();
      } else {
        this.discardEdit();
      }
    }

    discardEdit() {
      if (this.editSnapshot) {
        this.current = this.clone(this.editSnapshot);
        this.renderLayout();
        this.emitAllVisibility();
      }
      this.clearStorage('edit-draft');
      this.lockMode();
    }

    async saveEdit() {
      const preset = this.presets.get(this.currentPresetKey);
      if (!preset || !this.current) return;
      try {
        if (preset.builtIn) {
          const name = await this.promptName(`Copy of ${preset.name}`);
          if (!name) return;
          const layout = this.clone(this.current);
          layout.name = name;
          const response = await this.request(`${API_BASE}/presets`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, layout })
          });
          const dto = await response.json();
          this.presets.set(dto.key, dto);
          this.refreshPickerOptions(dto.key);
          this.clearStorage('edit-draft');
          this.lockMode({ silent: true });
          this.applyPreset(dto.key, { skipDirtyCheck: true });
        } else {
          const layout = this.clone(this.current);
          layout.name = preset.name;
          const response = await this.request(`${API_BASE}/presets/${preset.id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              name: preset.name,
              version: preset.version,
              layout
            })
          });
          const dto = await response.json();
          this.presets.set(dto.key, dto);
          this.refreshPickerOptions(dto.key);
          this.clearStorage('edit-draft');
          this.lockMode({ silent: true });
          this.applyPreset(dto.key, { skipDirtyCheck: true });
        }
      } catch (error) {
        const reference = error?.correlationId ? ` Reference: ${error.correlationId}.` : '';
        this.showNotice((error?.message || 'Could not save layout.') + reference);
      }
    }

    lockMode(options = {}) {
      this.workbench.dataset.layoutMode = 'locked';
      this.editSnapshot = null;
      document.querySelectorAll('[data-layout-edit-only]').forEach((el) => {
        el.hidden = true;
      });
      document.querySelectorAll('[data-cockpit-splitter]').forEach((splitter) => {
        splitter.tabIndex = -1;
      });
      if (this.modeButton) this.modeButton.textContent = 'Edit layout';
      if (!options.silent) this.clearNoticeIfDraftActions();
    }

    isDirty() {
      if (!this.editSnapshot || !this.current) return false;
      return JSON.stringify(this.current) !== JSON.stringify(this.editSnapshot);
    }

    persistDraft() {
      this.writeJson('edit-draft', {
        presetKey: this.currentPresetKey,
        layout: this.current
      });
    }

    maybeOfferDraftRecovery() {
      if (this._draftPromptShown) return;
      const draft = this.readJson('edit-draft');
      if (!draft || !draft.layout || !draft.layout.zones) return;
      this._draftPromptShown = true;
      this.showDraftRecovery(draft);
    }

    showDraftRecovery(draft) {
      if (!this.notice) return;
      this.notice.replaceChildren();
      const text = document.createElement('span');
      text.textContent = 'An unfinished layout edit was found on this device. ';
      const resume = document.createElement('button');
      resume.type = 'button';
      resume.className = 'btn btn-ghost';
      resume.textContent = 'Resume edit';
      resume.addEventListener('click', () => {
        this.current = this.clone(draft.layout);
        if (draft.presetKey && this.presets.has(draft.presetKey)) {
          this.currentPresetKey = draft.presetKey;
          if (this.picker) this.picker.value = draft.presetKey;
        }
        this.renderLayout();
        this.emitAllVisibility();
        this.enterEditMode();
        this.clearNotice();
      });
      const discard = document.createElement('button');
      discard.type = 'button';
      discard.className = 'btn btn-ghost';
      discard.textContent = 'Discard draft';
      discard.addEventListener('click', () => {
        this.clearStorage('edit-draft');
        this.clearNotice();
      });
      this.notice.append(text, resume, document.createTextNode(' '), discard);
    }

    async duplicatePreset() {
      const preset = this.presets.get(this.currentPresetKey);
      if (!preset) return;
      const name = await this.promptName(`Copy of ${preset.name}`);
      if (!name) return;
      try {
        const layout = this.clone(this.current || preset.layout);
        layout.name = name;
        const response = await this.request(`${API_BASE}/presets`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name, layout })
        });
        const dto = await response.json();
        this.presets.set(dto.key, dto);
        this.refreshPickerOptions(dto.key);
        this.applyPreset(dto.key, { skipDirtyCheck: true });
      } catch (error) {
        const reference = error?.correlationId ? ` Reference: ${error.correlationId}.` : '';
        this.showNotice((error?.message || 'Could not duplicate layout.') + reference);
      }
    }

    async renamePreset() {
      const preset = this.presets.get(this.currentPresetKey);
      if (!preset || preset.builtIn || !preset.id) return;
      const name = await this.promptName(preset.name);
      if (!name) return;
      try {
        const layout = this.clone(this.current || preset.layout);
        layout.name = name;
        const response = await this.request(`${API_BASE}/presets/${preset.id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name,
            version: preset.version,
            layout
          })
        });
        const dto = await response.json();
        this.presets.set(dto.key, dto);
        this.refreshPickerOptions(dto.key);
        this.currentPresetKey = dto.key;
        if (this.current) this.current.name = name;
      } catch (error) {
        const reference = error?.correlationId ? ` Reference: ${error.correlationId}.` : '';
        this.showNotice((error?.message || 'Could not rename layout.') + reference);
      }
    }

    async deletePreset() {
      const preset = this.presets.get(this.currentPresetKey);
      if (!preset || preset.builtIn || !preset.id) return;
      try {
        await this.request(`${API_BASE}/presets/${preset.id}`, { method: 'DELETE' });
        this.presets.delete(preset.key);
        this.refreshPickerOptions(DEFAULT_KEY);
        if (this.readLastPreset() === preset.key) {
          this.clearStorage('last-preset');
        }
        this.applyPreset(DEFAULT_KEY, { skipDirtyCheck: true });
      } catch (error) {
        const reference = error?.correlationId ? ` Reference: ${error.correlationId}.` : '';
        this.showNotice((error?.message || 'Could not delete layout.') + reference);
      }
    }

    restoreBuiltIn() {
      const preset = this.presets.get(this.currentPresetKey);
      if (!preset || !preset.builtIn) return;
      const original = this.builtInCatalog.get(this.currentPresetKey);
      if (!original) return;
      this.presets.set(this.currentPresetKey, this.clone(original));
      this.clearStorage('active-tabs');
      this.clearStorage('edit-draft');
      this.applyPreset(this.currentPresetKey, { skipDirtyCheck: true });
    }

    syncPresetActionState() {
      const preset = this.presets.get(this.currentPresetKey);
      const isCustom = !!(preset && !preset.builtIn);
      const isBuiltIn = !!(preset && preset.builtIn);
      document.querySelectorAll('[data-preset-action="rename"], [data-preset-action="delete"]').forEach((btn) => {
        btn.disabled = !isCustom;
        if (isCustom) btn.removeAttribute('aria-disabled');
        else btn.setAttribute('aria-disabled', 'true');
      });
      document.querySelectorAll('[data-preset-action="restore"]').forEach((btn) => {
        btn.disabled = !isBuiltIn;
        if (isBuiltIn) btn.removeAttribute('aria-disabled');
        else btn.setAttribute('aria-disabled', 'true');
      });
    }

    refreshPickerOptions(selectKey) {
      if (!this.picker) return;
      const current = selectKey || this.currentPresetKey;
      this.picker.replaceChildren();
      for (const preset of this.presets.values()) {
        const option = document.createElement('option');
        option.value = preset.key;
        option.textContent = preset.name;
        this.picker.appendChild(option);
      }
      this.picker.value = current;
      this.syncPresetActionState();
    }

    promptName(initial) {
      return new Promise((resolve) => {
        if (!this.nameDialog || !this.nameInput) {
          resolve(window.prompt('Layout name', initial || '') || null);
          return;
        }
        this._pendingNameResolve = resolve;
        this.nameInput.value = initial || '';
        if (typeof this.nameDialog.showModal === 'function') {
          this.nameDialog.showModal();
          this.nameInput.focus();
          this.nameInput.select();
        } else {
          resolve(window.prompt('Layout name', initial || '') || null);
        }
      });
    }

    async request(url, options = {}) {
      if (typeof window.dmRequest === 'function') {
        return window.dmRequest(url, options);
      }
      let response;
      try {
        response = await fetch(url, options);
      } catch (_) {
        throw Object.assign(new Error('No answer from the server. Check it is still running.'), {
          correlationId: null
        });
      }
      if (!response.ok) {
        let detail = `The server refused that request (${response.status}).`;
        let correlationId = response.headers.get('X-Correlation-ID');
        try {
          const body = await response.json();
          detail = body.detail || detail;
          correlationId = body.correlationId || correlationId;
        } catch (_) {
          // ignore malformed body
        }
        throw Object.assign(new Error(detail), { correlationId, status: response.status });
      }
      return response;
    }

    readLastPreset() {
      try {
        const raw = localStorage.getItem(this.storageKey('last-preset'));
        if (raw == null || raw === '') return null;
        if (raw.startsWith('"') || raw.startsWith('{')) {
          const parsed = JSON.parse(raw);
          if (typeof parsed === 'string') return parsed;
          if (parsed && typeof parsed.key === 'string') return parsed.key;
          this.forgetKey('last-preset');
          return null;
        }
        return raw;
      } catch (_) {
        this.forgetKey('last-preset');
        return null;
      }
    }

    writeLastPreset(key) {
      try {
        localStorage.setItem(this.storageKey('last-preset'), key);
      } catch (_) {
        // storage may be unavailable; layout still works in-session
      }
    }

    readJson(kind) {
      try {
        const raw = localStorage.getItem(this.storageKey(kind));
        if (raw == null || raw === '') return null;
        return JSON.parse(raw);
      } catch (_) {
        this.forgetKey(kind);
        return null;
      }
    }

    writeJson(kind, value) {
      try {
        localStorage.setItem(this.storageKey(kind), JSON.stringify(value));
      } catch (_) {
        // ignore quota / private mode
      }
    }

    clearStorage(kind) {
      try {
        localStorage.removeItem(this.storageKey(kind));
      } catch (_) {
        // ignore
      }
    }

    forgetKey(kind) {
      this.clearStorage(kind);
      this.showNotice(INVALID_STORAGE_MSG);
      window.dispatchEvent(new CustomEvent('cockpit:layout-warning', {
        detail: { message: INVALID_STORAGE_MSG, kind }
      }));
    }

    showNotice(message) {
      if (!this.notice) return;
      this.notice.textContent = message || '';
    }

    clearNotice() {
      if (!this.notice) return;
      this.notice.replaceChildren();
    }

    clearNoticeIfDraftActions() {
      if (!this.notice) return;
      if (this.notice.querySelector('button')) return;
      this.clearNotice();
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
