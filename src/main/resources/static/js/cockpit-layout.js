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
      this._focusReturnEl = null;
      this._focusHomePanel = null;
      this.attention = new Map();
      /** @type {Map<string, Function>} in-memory only — never serialized */
      this._retryCallbacks = new Map();
      this.metrics = { firstMeaningfulMs: null, lastApplyMs: null };
      this.mounted = false;
      this._pendingNameResolve = null;
      this._nameDialogResult = undefined;
      this._draftPromptShown = false;
      this._arrangeModuleKey = null;
      this.resizeFrame = null;
      this._pendingResize = null;
      this._splitterPointer = null;
      this.addDialog = document.getElementById('cockpitAddModuleDialog');
      this.addButton = document.getElementById('cockpitAddModuleButton');
      this.bottomToggle = document.getElementById('cockpitBottomUtilityToggle');
      this.arrangeMenu = document.getElementById('cockpitModuleArrangeMenu');
      this.focusLayer = document.getElementById('cockpitFocusLayer');
      this.focusReturn = document.getElementById('cockpitFocusReturn');
      this.focusMount = this.focusLayer?.querySelector('[data-focus-mount]') || null;
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
        this.forgetKey('last-preset');
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
              this._nameDialogResult = value || null;
            } else {
              this._nameDialogResult = null;
            }
            this.nameDialog.close();
          });
        });
        this.nameDialog.addEventListener('close', () => {
          this.resolveNamePrompt(
            this._nameDialogResult === undefined ? null : this._nameDialogResult
          );
          this._nameDialogResult = undefined;
        });
      }

      this.bindEditInteractions();
      this.bindSplitters();
      this.bindFocusChrome();
      this.bindPresetShortcuts();
      this.bindTabKeyboard();
      this.bindAttention();
      this.bindModuleState();
    }

    /**
     * Alt+Shift+1…5 selects the five immutable built-ins when no modal or text field
     * owns the keystroke. Custom presets are never on this shortcut strip.
     */
    bindPresetShortcuts() {
      const BUILTIN_BY_DIGIT = {
        Digit1: 'builtin:exploration',
        Digit2: 'builtin:combat',
        Digit3: 'builtin:theatre-of-mind',
        Digit4: 'builtin:presentation',
        Digit5: 'builtin:session-review'
      };
      document.addEventListener('keydown', (event) => {
        if (!event.altKey || !event.shiftKey || event.ctrlKey || event.metaKey) return;
        const presetKey = BUILTIN_BY_DIGIT[event.code];
        if (!presetKey) return;
        if (this.isModalOpen()) return;
        if (this.isEditableTarget(event.target)) return;
        event.preventDefault();
        this.applyPreset(presetKey);
      });
    }

    /**
     * Roving tabindex + arrow-key selection for zone tablists (WAI-ARIA tabs pattern).
     */
    bindTabKeyboard() {
      this.workbench.addEventListener('keydown', (event) => {
        const tab = event.target.closest('[role="tab"][data-module-tab]');
        if (!tab || !this.workbench.contains(tab)) return;
        const tabsEl = tab.closest('[role="tablist"]');
        if (!tabsEl) return;
        const tabs = Array.from(tabsEl.querySelectorAll('[role="tab"][data-module-tab]'));
        if (tabs.length === 0) return;
        const index = tabs.indexOf(tab);
        if (index < 0) return;
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
        const focused = tabsEl.querySelector(`[role="tab"][data-module-tab="${key}"]`);
        focused?.focus();
      });
    }

    isEditableTarget(target) {
      if (!target || !(target instanceof Element)) return false;
      if (target.isContentEditable) return true;
      const el = target.closest('input, textarea, select, [contenteditable="true"]');
      return !!el;
    }

    bindEditInteractions() {
      document.addEventListener('click', (event) => {
        const focusBtn = event.target.closest('[data-module-focus]');
        if (focusBtn) {
          const shell = focusBtn.closest('[data-module-key]');
          if (shell) {
            event.preventDefault();
            this.focusModule(shell.getAttribute('data-module-key'), focusBtn);
          }
          return;
        }

        if (this.workbench?.dataset.layoutMode !== 'edit') return;

        const removeBtn = event.target.closest('[data-module-remove]');
        if (removeBtn) {
          const shell = removeBtn.closest('[data-module-key]');
          if (shell) {
            event.preventDefault();
            this.removeModule(shell.getAttribute('data-module-key'));
          }
          return;
        }

        const menuBtn = event.target.closest('[data-module-menu]');
        if (menuBtn) {
          const shell = menuBtn.closest('[data-module-key]');
          if (shell) {
            event.preventDefault();
            this.openArrangeMenu(shell.getAttribute('data-module-key'), menuBtn);
          }
          return;
        }

        const arrangeZone = event.target.closest('[data-arrange-zone]');
        if (arrangeZone && this.arrangeMenu && this.arrangeMenu.contains(arrangeZone)) {
          event.preventDefault();
          const key = this._arrangeModuleKey;
          const zone = arrangeZone.getAttribute('data-arrange-zone');
          this.closeArrangeMenu();
          if (key) this.moveModule(key, zone);
          return;
        }

        const arrangeMove = event.target.closest('[data-arrange-move]');
        if (arrangeMove && this.arrangeMenu && this.arrangeMenu.contains(arrangeMove)) {
          event.preventDefault();
          const key = this._arrangeModuleKey;
          const dir = arrangeMove.getAttribute('data-arrange-move');
          this.closeArrangeMenu();
          if (key) this.reorderModule(key, dir === 'earlier' ? -1 : 1);
          return;
        }

        if (this.arrangeMenu && !this.arrangeMenu.hidden
            && !this.arrangeMenu.contains(event.target)
            && !event.target.closest('[data-module-menu]')) {
          this.closeArrangeMenu();
        }
      });

      if (this.addButton) {
        this.addButton.addEventListener('click', () => this.openAddModuleDialog());
      }
      if (this.bottomToggle) {
        this.bottomToggle.addEventListener('click', () => this.toggleBottomUtility());
      }

      document.addEventListener('click', (event) => {
        const quickNotes = event.target.closest('[data-open-quick-notes]');
        if (!quickNotes) return;
        event.preventDefault();
        this.revealQuickNotesCapture();
      });

      if (this.addDialog) {
        this.addDialog.addEventListener('click', (event) => {
          const choice = event.target.closest('[data-add-module][data-add-zone]');
          if (!choice) return;
          event.preventDefault();
          this.addModule(
            choice.getAttribute('data-add-module'),
            choice.getAttribute('data-add-zone')
          );
        });
      }

      // Docking: native HTML5 drag only while editing.
      document.addEventListener('dragstart', (event) => {
        if (this.workbench?.dataset.layoutMode !== 'edit') return;
        const header = event.target.closest('.cockpit-module__header');
        if (!header) return;
        const shell = header.closest('[data-module-key]');
        if (!shell || !header.draggable) return;
        const key = shell.getAttribute('data-module-key');
        event.dataTransfer.setData('text/x-dmhelper-module', key);
        event.dataTransfer.effectAllowed = 'move';
        shell.setAttribute('data-dragging', 'true');
      });

      document.addEventListener('dragend', (event) => {
        const shell = event.target.closest?.('[data-module-key]');
        if (shell) shell.removeAttribute('data-dragging');
        document.querySelectorAll('[data-dock-active]').forEach((el) => {
          el.removeAttribute('data-dock-active');
        });
      });

      document.addEventListener('dragover', (event) => {
        if (this.workbench?.dataset.layoutMode !== 'edit') return;
        const target = event.target.closest('[data-dock-target], [data-cockpit-zone]');
        if (!target || !this.workbench.contains(target)) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
        const zoneEl = target.closest('[data-cockpit-zone]') || target;
        // Clear peer zone highlights so only the current drop target stays sticky.
        document.querySelectorAll('[data-dock-active]').forEach((el) => {
          if (el !== zoneEl) el.removeAttribute('data-dock-active');
        });
        zoneEl.setAttribute('data-dock-active', 'true');
      });

      document.addEventListener('dragleave', (event) => {
        const zoneEl = event.target.closest?.('[data-cockpit-zone]');
        if (!zoneEl) return;
        if (zoneEl.contains(event.relatedTarget)) return;
        zoneEl.removeAttribute('data-dock-active');
      });

      document.addEventListener('drop', (event) => {
        if (this.workbench?.dataset.layoutMode !== 'edit') return;
        const target = event.target.closest('[data-dock-target], [data-cockpit-zone]');
        if (!target || !this.workbench.contains(target)) return;
        event.preventDefault();
        const zoneEl = target.closest('[data-cockpit-zone]') || target;
        zoneEl.removeAttribute('data-dock-active');
        const zone = zoneEl.getAttribute('data-dock-target')
          || zoneEl.getAttribute('data-cockpit-zone');
        const key = event.dataTransfer.getData('text/x-dmhelper-module');
        if (!key || !zone) return;
        const tab = event.target.closest('[data-module-tab]');
        let index;
        if (tab) {
          const zoneLayout = this.current?.zones?.[zone];
          const tabKey = tab.getAttribute('data-module-tab');
          index = zoneLayout?.moduleKeys?.indexOf(tabKey) ?? 0;
        }
        this.moveModule(key, zone, index);
      });
    }

    bindSplitters() {
      this.workbench?.querySelectorAll('[data-cockpit-splitter]').forEach((splitter) => {
        splitter.addEventListener('keydown', (event) => this.onSplitterKey(splitter, event));
        splitter.addEventListener('pointerdown', (event) => this.onSplitterPointerDown(splitter, event));
      });
      window.addEventListener('pointermove', (event) => this.onSplitterPointerMove(event));
      window.addEventListener('pointerup', (event) => this.onSplitterPointerUp(event));
      window.addEventListener('pointercancel', (event) => this.onSplitterPointerUp(event));
    }

    bindFocusChrome() {
      if (this.focusReturn) {
        this.focusReturn.addEventListener('click', () => this.restoreFocus());
      }
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
        if (this.focusedModuleKey == null) return;
        if (this.isModalOpen()) return;
        event.preventDefault();
        this.restoreFocus();
      });
    }

    bindAttention() {
      window.addEventListener('cockpit:module-attention', (event) => {
        const detail = event.detail || {};
        if (!detail.moduleKey) return;
        this.setAttention(detail.moduleKey, detail.count ?? 1);
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

      // Focus temporarily moves a live module shell outside its zone. Restore it
      // before zone panels are rebuilt so a preset change cannot orphan that shell.
      if (this.focusedModuleKey) {
        this.restoreFocus({ silent: true });
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
          if (!shell && this.focusedModuleKey !== key) {
            shell = document.querySelector(`[data-module-key="${key}"]`);
            // Never steal the focused shell out of the focus layer.
            if (shell && this.focusMount && this.focusMount.contains(shell)) {
              shell = null;
            } else if (shell) {
              panel.appendChild(shell);
            }
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
            const header = shell.querySelector('.cockpit-module__header');
            if (header) {
              header.draggable = this.workbench.dataset.layoutMode === 'edit';
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
      if (this.bottomToggle) {
        const expanded = !bottom?.collapsed;
        this.bottomToggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        this.bottomToggle.textContent = expanded ? 'Hide utilities' : 'Show utilities';
      }

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
      this.syncSplitterAria(ratios);
      this.syncEditChrome();
      for (const key of this.modules.keys()) {
        window.dispatchEvent(new CustomEvent('cockpit:module-mode', {
          detail: { moduleKey: key, mode: this.moduleMode(key) }
        }));
      }
    }

    /**
     * Keep edit-only controls, drag handles, and splitter tab order in sync with layoutMode.
     * Called after every render so moved shells do not reappear locked.
     * Focused shells keep Arrange/Remove hidden (mutations restore focus first).
     */
    syncEditChrome() {
      const editing = this.workbench?.dataset.layoutMode === 'edit';
      const focusedKey = this.focusedModuleKey;
      document.querySelectorAll('[data-layout-edit-only]').forEach((el) => {
        if (el.id === 'cockpitModuleArrangeMenu' || el.getAttribute('role') === 'menu') {
          // Shared arrange popover stays closed until openArrangeMenu().
          if (!editing) el.hidden = true;
          return;
        }
        const shell = el.closest('[data-module-key]');
        const onFocusedShell = !!(focusedKey
          && shell
          && shell.getAttribute('data-module-key') === focusedKey);
        if (editing && !onFocusedShell) {
          el.hidden = false;
          el.removeAttribute('hidden');
          if ('disabled' in el) {
            el.disabled = false;
            el.removeAttribute('aria-disabled');
          }
        } else {
          el.hidden = true;
          if ('disabled' in el && onFocusedShell) {
            el.disabled = true;
          }
        }
      });
      document.querySelectorAll('[data-module-key] .cockpit-module__header').forEach((header) => {
        const shell = header.closest('[data-module-key]');
        const onFocusedShell = !!(focusedKey
          && shell
          && shell.getAttribute('data-module-key') === focusedKey);
        header.draggable = editing && !onFocusedShell;
      });
      document.querySelectorAll('[data-cockpit-splitter]').forEach((splitter) => {
        splitter.tabIndex = editing ? 0 : -1;
      });
    }

    syncSplitterAria(ratios) {
      const r = ratios || this.current?.ratios || {};
      const left = this.workbench.querySelector('[data-cockpit-splitter="LEFT_PRIMARY"]');
      const right = this.workbench.querySelector('[data-cockpit-splitter="PRIMARY_RIGHT"]');
      const bottom = this.workbench.querySelector('[data-cockpit-splitter="BOTTOM_UTILITY"]');
      if (left) {
        const bounds = this.splitterBounds('LEFT_PRIMARY');
        left.setAttribute('aria-valuemin', String(Math.round(bounds.min * 100)));
        left.setAttribute('aria-valuemax', String(Math.round(bounds.max * 100)));
        left.setAttribute('aria-valuenow', String(Math.round((r.left || 0) * 100)));
      }
      if (right) {
        const bounds = this.splitterBounds('PRIMARY_RIGHT');
        // Report right column share so the separator mirrors the side it resizes.
        right.setAttribute('aria-valuemin', String(Math.round(bounds.minRight * 100)));
        right.setAttribute('aria-valuemax', String(Math.round(bounds.maxRight * 100)));
        right.setAttribute('aria-valuenow', String(Math.round((r.right || 0) * 100)));
      }
      if (bottom) {
        const bounds = this.splitterBounds('BOTTOM_UTILITY');
        bottom.setAttribute('aria-valuemin', String(Math.round(bounds.min * 100)));
        bottom.setAttribute('aria-valuemax', String(Math.round(bounds.max * 100)));
        bottom.setAttribute('aria-valuenow', String(Math.round((r.bottom || 0) * 100)));
      }
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

    revealQuickNotesCapture() {
      const zone = this.findModuleZone('story');
      const input = document.querySelector(
        '[data-module-key="story"] .quicknotes-form input'
      );
      if (!zone || !input) {
        this.showNotice('Add the Story module to use quick-note capture.');
        return false;
      }
      this.selectTab(zone, 'story');
      input.scrollIntoView?.({ block: 'nearest' });
      input.focus();
      return document.activeElement === input;
    }

    assertEditing() {
      if (this.workbench.dataset.layoutMode !== 'edit') {
        throw new Error('Layout changes require edit mode.');
      }
    }

    /**
     * Layout mutations must run against the workbench placement model.
     * If a module is focused its shell is outside the workbench — restore first
     * so render/placement cannot orphan the focused module.
     */
    prepareLayoutMutation() {
      this.assertEditing();
      if (this.focusedModuleKey) {
        this.restoreFocus({ silent: true });
      }
    }

    allowedZonesFor(def) {
      if (!def) return [];
      const raw = def.allowedZones;
      if (Array.isArray(raw)) return raw.map(String);
      if (raw && typeof raw === 'object') return Object.keys(raw);
      return [];
    }

    findModuleZone(key) {
      if (!this.current?.zones) return null;
      for (const zone of ZONES) {
        const keys = this.current.zones[zone]?.moduleKeys;
        if (Array.isArray(keys) && keys.includes(key)) return zone;
      }
      return null;
    }

    placedModuleKeys() {
      const placed = new Set();
      if (!this.current?.zones) return placed;
      for (const zone of ZONES) {
        const keys = this.current.zones[zone]?.moduleKeys || [];
        keys.forEach((k) => placed.add(k));
      }
      return placed;
    }

    ensureZoneLayout(zone) {
      if (!this.current.zones[zone]) {
        this.current.zones[zone] = {
          moduleKeys: [],
          activeModuleKey: null,
          collapsed: zone === 'BOTTOM_UTILITY'
        };
      }
      if (!Array.isArray(this.current.zones[zone].moduleKeys)) {
        this.current.zones[zone].moduleKeys = [];
      }
      return this.current.zones[zone];
    }

    repairActive(zoneLayout) {
      const keys = zoneLayout.moduleKeys || [];
      if (!keys.includes(zoneLayout.activeModuleKey)) {
        zoneLayout.activeModuleKey = keys[0] || null;
      }
    }

    toggleBottomUtility() {
      this.prepareLayoutMutation();
      const bottom = this.ensureZoneLayout('BOTTOM_UTILITY');
      bottom.collapsed = !bottom.collapsed;
      this.renderLayout();
      this.persistDraft();
      this.emitAllVisibility();
      return !bottom.collapsed;
    }

    moveModule(key, targetZone, index) {
      this.prepareLayoutMutation();
      const def = this.modules.get(key);
      if (!def) {
        this.showNotice(`Unknown module: ${key}`);
        return false;
      }
      if (!ZONES.includes(targetZone)) {
        this.showNotice('That zone is not available.');
        return false;
      }
      const allowed = this.allowedZonesFor(def);
      if (!allowed.includes(targetZone)) {
        this.showNotice(`${def.title || key} cannot move to that zone.`);
        return false;
      }

      const sourceZone = this.findModuleZone(key);
      if (sourceZone === 'PRIMARY'
          && this.current.zones.PRIMARY.moduleKeys.length === 1
          && targetZone !== 'PRIMARY') {
        this.showNotice('Primary must keep at least one module.');
        return false;
      }

      // Remove from every zone first (uniqueness).
      for (const zone of ZONES) {
        const zl = this.ensureZoneLayout(zone);
        const i = zl.moduleKeys.indexOf(key);
        if (i >= 0) zl.moduleKeys.splice(i, 1);
        this.repairActive(zl);
      }

      const target = this.ensureZoneLayout(targetZone);
      let insertAt = index;
      if (insertAt == null || Number.isNaN(insertAt)) insertAt = target.moduleKeys.length;
      insertAt = Math.max(0, Math.min(insertAt, target.moduleKeys.length));
      target.moduleKeys.splice(insertAt, 0, key);
      // Activate the moved module so its chrome (Arrange/Remove/Focus) remains reachable.
      target.activeModuleKey = key;
      if (targetZone === 'BOTTOM_UTILITY') {
        target.collapsed = false;
        this.workbench.dataset.bottomCollapsed = 'false';
      }

      this.renderLayout();
      this.writeJson('active-tabs', this.collectActiveTabs());
      this.persistDraft();
      this.emitAllVisibility();
      return true;
    }

    reorderModule(key, delta) {
      this.prepareLayoutMutation();
      const zone = this.findModuleZone(key);
      if (!zone) return false;
      const zl = this.ensureZoneLayout(zone);
      const i = zl.moduleKeys.indexOf(key);
      if (i < 0) return false;
      const j = i + delta;
      if (j < 0 || j >= zl.moduleKeys.length) return false;
      zl.moduleKeys.splice(i, 1);
      zl.moduleKeys.splice(j, 0, key);
      this.renderLayout();
      this.writeJson('active-tabs', this.collectActiveTabs());
      this.persistDraft();
      this.emitAllVisibility();
      return true;
    }

    removeModule(key) {
      this.prepareLayoutMutation();
      const zone = this.findModuleZone(key);
      if (!zone) return false;
      if (zone === 'PRIMARY' && this.current.zones.PRIMARY.moduleKeys.length === 1) {
        this.showNotice('Primary must keep at least one module.');
        return false;
      }
      const zl = this.ensureZoneLayout(zone);
      const i = zl.moduleKeys.indexOf(key);
      if (i >= 0) zl.moduleKeys.splice(i, 1);
      this.repairActive(zl);
      if (Array.isArray(this.current.compactModuleKeys)) {
        this.current.compactModuleKeys = this.current.compactModuleKeys.filter((k) => k !== key);
      }
      this.renderLayout();
      this.writeJson('active-tabs', this.collectActiveTabs());
      this.persistDraft();
      this.emitAllVisibility();
      return true;
    }

    addModule(key, zone) {
      this.prepareLayoutMutation();
      if (this.placedModuleKeys().has(key)) {
        this.showNotice('That module is already in the workspace.');
        return false;
      }
      const ok = this.moveModule(key, zone);
      if (ok) {
        this.closeAddModuleDialog({ restoreFocus: true });
      }
      return ok;
    }

    openArrangeMenu(key, anchor) {
      if (!this.arrangeMenu) return;
      this.prepareLayoutMutation();
      const def = this.modules.get(key);
      if (!def) return;
      this._arrangeModuleKey = key;
      const allowed = new Set(this.allowedZonesFor(def));
      const currentZone = this.findModuleZone(key);
      this.arrangeMenu.querySelectorAll('[data-arrange-zone]').forEach((btn) => {
        const zone = btn.getAttribute('data-arrange-zone');
        const enabled = allowed.has(zone) && zone !== currentZone;
        btn.hidden = !enabled;
        btn.disabled = !enabled;
      });
      const keys = currentZone ? (this.current.zones[currentZone]?.moduleKeys || []) : [];
      const idx = keys.indexOf(key);
      const earlier = this.arrangeMenu.querySelector('[data-arrange-move="earlier"]');
      const later = this.arrangeMenu.querySelector('[data-arrange-move="later"]');
      if (earlier) {
        earlier.disabled = idx <= 0;
        earlier.hidden = keys.length < 2;
      }
      if (later) {
        later.disabled = idx < 0 || idx >= keys.length - 1;
        later.hidden = keys.length < 2;
      }
      this.arrangeMenu.hidden = false;
      if (anchor && typeof anchor.getBoundingClientRect === 'function') {
        const rect = anchor.getBoundingClientRect();
        this.arrangeMenu.style.position = 'fixed';
        this.arrangeMenu.style.left = `${Math.round(rect.left)}px`;
        this.arrangeMenu.style.top = `${Math.round(rect.bottom + 4)}px`;
        this.arrangeMenu.style.zIndex = '50';
      }
      const first = this.arrangeMenu.querySelector('[role="menuitem"]:not([hidden]):not([disabled])');
      first?.focus();
    }

    closeArrangeMenu() {
      if (!this.arrangeMenu) return;
      this.arrangeMenu.hidden = true;
      this._arrangeModuleKey = null;
    }

    openAddModuleDialog() {
      this.prepareLayoutMutation();
      if (!this.addDialog) return;
      const list = this.addDialog.querySelector('[data-add-module-list]');
      if (!list) return;
      list.replaceChildren();
      const placed = this.placedModuleKeys();
      for (const def of this.modules.values()) {
        if (placed.has(def.key)) continue;
        const zones = this.allowedZonesFor(def);
        if (!zones.length) continue;
        const item = document.createElement('li');
        item.style.listStyle = 'none';
        item.style.marginBottom = '0.5rem';
        const title = document.createElement('div');
        title.textContent = def.title || def.key;
        title.style.fontWeight = '600';
        item.appendChild(title);
        const actions = document.createElement('div');
        actions.style.display = 'flex';
        actions.style.flexWrap = 'wrap';
        actions.style.gap = '4px';
        for (const zone of zones) {
          const btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'btn btn-ghost';
          btn.setAttribute('data-add-module', def.key);
          btn.setAttribute('data-add-zone', zone);
          btn.textContent = this.zoneLabel(zone);
          actions.appendChild(btn);
        }
        item.appendChild(actions);
        list.appendChild(item);
      }
      if (!list.children.length) {
        const empty = document.createElement('li');
        empty.textContent = 'Every module is already in the workspace.';
        list.appendChild(empty);
      }
      if (typeof this.addDialog.showModal === 'function') {
        this.addDialog.showModal();
      } else {
        this.addDialog.hidden = false;
      }
    }

    closeAddModuleDialog(options = {}) {
      if (!this.addDialog) return;
      if (typeof this.addDialog.close === 'function' && this.addDialog.open) {
        this.addDialog.close();
      } else {
        this.addDialog.hidden = true;
      }
      if (options.restoreFocus && this.addButton) {
        this.addButton.focus();
      }
    }

    zoneLabel(zone) {
      switch (zone) {
        case 'PRIMARY': return 'Primary';
        case 'LEFT_SUPPORT': return 'Left support';
        case 'RIGHT_SUPPORT': return 'Right support';
        case 'BOTTOM_UTILITY': return 'Bottom utility';
        default: return zone;
      }
    }

    /* ---- Splitters ---- */

    zoneActiveMinWidth(zone) {
      const zl = this.current?.zones?.[zone];
      const key = zl?.activeModuleKey || zl?.moduleKeys?.[0];
      if (!key) return 0;
      const def = this.modules.get(key);
      return def?.minWidthPx || 0;
    }

    zoneActiveMinHeight(zone) {
      const zl = this.current?.zones?.[zone];
      const key = zl?.activeModuleKey || zl?.moduleKeys?.[0];
      if (!key) return 0;
      const def = this.modules.get(key);
      return def?.minHeightPx || 0;
    }

    workbenchMetrics() {
      const rect = this.workbench.getBoundingClientRect();
      // Two vertical splitters (~6px) between three columns.
      const hSplitters = 12;
      const vSplitter = 6;
      return {
        width: Math.max(1, rect.width - hSplitters),
        height: Math.max(1, rect.height - vSplitter)
      };
    }

    splitterBounds(which) {
      const metrics = this.workbenchMetrics();
      const leftMinPx = this.zoneActiveMinWidth('LEFT_SUPPORT');
      const primaryMinPx = this.zoneActiveMinWidth('PRIMARY') || 240;
      const rightMinPx = this.zoneActiveMinWidth('RIGHT_SUPPORT');
      const bottomMinPx = this.zoneActiveMinHeight('BOTTOM_UTILITY');

      const leftMin = Math.max(0.05, leftMinPx / metrics.width);
      const primaryMin = Math.max(0.50, primaryMinPx / metrics.width);
      const primaryMax = 0.65;
      const rightMin = Math.max(0.05, rightMinPx / metrics.width);

      if (which === 'LEFT_PRIMARY') {
        // left grows/shrinks against primary; primary must stay [0.50, 0.65]
        const ratios = this.current?.ratios || {};
        const right = ratios.right ?? 0.24;
        // left + primary = 1 - right
        const pair = 1 - right;
        const min = Math.max(leftMin, pair - primaryMax);
        const max = Math.min(pair - primaryMin, pair - 0.50, 1 - right - rightMin);
        return { min: Math.max(0.05, min), max: Math.max(min, max) };
      }
      if (which === 'PRIMARY_RIGHT') {
        const ratios = this.current?.ratios || {};
        const left = ratios.left ?? 0.20;
        const pair = 1 - left;
        // right share bounds while primary stays in range
        const minRight = Math.max(rightMin, pair - primaryMax);
        const maxRight = Math.min(pair - primaryMin, pair - 0.50);
        return {
          minRight: Math.max(0.05, minRight),
          maxRight: Math.max(minRight, maxRight),
          min: Math.max(0.05, minRight),
          max: Math.max(minRight, maxRight)
        };
      }
      // BOTTOM
      const minBottom = Math.max(0.16, bottomMinPx / metrics.height);
      const maxBottom = 0.40;
      return { min: Math.min(minBottom, maxBottom), max: maxBottom };
    }

    clampRatios(next) {
      const ratios = {
        left: next.left,
        primary: next.primary,
        right: next.right,
        bottom: next.bottom
      };
      let sum = ratios.left + ratios.primary + ratios.right;
      if (!(sum > 0)) {
        ratios.left = 0.2;
        ratios.primary = 0.56;
        ratios.right = 0.24;
        sum = 1;
      } else {
        ratios.left /= sum;
        ratios.primary /= sum;
        ratios.right /= sum;
      }

      const metrics = this.workbenchMetrics();
      const leftMin = Math.max(0.05, this.zoneActiveMinWidth('LEFT_SUPPORT') / metrics.width);
      const rightMin = Math.max(0.05, this.zoneActiveMinWidth('RIGHT_SUPPORT') / metrics.width);
      const primaryMinPx = this.zoneActiveMinWidth('PRIMARY') || 240;
      const primaryMin = Math.max(0.50, primaryMinPx / metrics.width);
      const primaryMax = 0.65;

      ratios.primary = Math.min(primaryMax, Math.max(primaryMin, ratios.primary));
      const side = 1 - ratios.primary;
      let left = ratios.left;
      let right = ratios.right;
      let sideSum = left + right;
      if (sideSum <= 0) {
        left = side / 2;
        right = side / 2;
      } else {
        left = (left / sideSum) * side;
        right = (right / sideSum) * side;
      }
      if (left < leftMin) {
        left = leftMin;
        right = side - left;
      }
      if (right < rightMin) {
        right = rightMin;
        left = side - right;
      }
      // Re-check primary dominance if sides needed more room than available.
      if (left + right + ratios.primary !== 1) {
        const total = left + right + ratios.primary;
        left /= total;
        right /= total;
        ratios.primary /= total;
      }
      ratios.left = left;
      ratios.right = right;

      // Final primary clamp with redistribution onto the larger side.
      if (ratios.primary < 0.50) {
        const need = 0.50 - ratios.primary;
        ratios.primary = 0.50;
        if (ratios.left >= ratios.right) ratios.left = Math.max(leftMin, ratios.left - need);
        else ratios.right = Math.max(rightMin, ratios.right - need);
      }
      if (ratios.primary > 0.65) {
        const extra = ratios.primary - 0.65;
        ratios.primary = 0.65;
        ratios.left += extra / 2;
        ratios.right += extra / 2;
      }
      sum = ratios.left + ratios.primary + ratios.right;
      ratios.left /= sum;
      ratios.primary /= sum;
      ratios.right /= sum;

      const bottomMinPx = this.zoneActiveMinHeight('BOTTOM_UTILITY');
      const bottomMin = Math.max(0.16, bottomMinPx / metrics.height);
      ratios.bottom = Math.min(0.40, Math.max(bottomMin, ratios.bottom));

      return ratios;
    }

    resize(splitter, delta) {
      if (this.workbench.dataset.layoutMode !== 'edit') return false;
      if (!this.current?.ratios) return false;
      const ratios = { ...this.current.ratios };
      if (splitter === 'LEFT_PRIMARY') {
        ratios.left += delta;
        ratios.primary -= delta;
      } else if (splitter === 'PRIMARY_RIGHT') {
        // Positive delta moves separator right → grow primary, shrink right.
        ratios.primary += delta;
        ratios.right -= delta;
      } else if (splitter === 'BOTTOM_UTILITY') {
        // Positive delta grows bottom (pointer drag downward / ArrowDown).
        ratios.bottom += delta;
        const bottomZone = this.ensureZoneLayout('BOTTOM_UTILITY');
        if (bottomZone.collapsed && delta > 0) {
          bottomZone.collapsed = false;
          this.workbench.dataset.bottomCollapsed = 'false';
        }
      } else {
        return false;
      }
      this.current.ratios = this.clampRatios(ratios);
      this.renderLayout();
      this.persistDraft();
      return true;
    }

    onSplitterKey(splitter, event) {
      if (this.workbench.dataset.layoutMode !== 'edit') return;
      const which = splitter.getAttribute('data-cockpit-splitter');
      const orientation = splitter.getAttribute('aria-orientation') || 'vertical';
      const step = event.shiftKey ? 0.10 : 0.02;
      let delta = 0;
      if (orientation === 'vertical') {
        if (event.key === 'ArrowRight') delta = step;
        else if (event.key === 'ArrowLeft') delta = -step;
        else if (event.key === 'Home') {
          event.preventDefault();
          this.resizeToBound(which, 'min');
          return;
        } else if (event.key === 'End') {
          event.preventDefault();
          this.resizeToBound(which, 'max');
          return;
        } else return;
      } else {
        if (event.key === 'ArrowDown') delta = step;
        else if (event.key === 'ArrowUp') delta = -step;
        else if (event.key === 'Home') {
          event.preventDefault();
          this.resizeToBound(which, 'min');
          return;
        } else if (event.key === 'End') {
          event.preventDefault();
          this.resizeToBound(which, 'max');
          return;
        } else return;
      }
      event.preventDefault();
      this.resize(which, delta);
    }

    resizeToBound(which, edge) {
      if (this.workbench.dataset.layoutMode !== 'edit' || !this.current?.ratios) return;
      const bounds = this.splitterBounds(which);
      const ratios = { ...this.current.ratios };
      if (which === 'LEFT_PRIMARY') {
        const target = edge === 'min' ? bounds.min : bounds.max;
        const delta = target - ratios.left;
        ratios.left = target;
        ratios.primary -= delta;
      } else if (which === 'PRIMARY_RIGHT') {
        const target = edge === 'min' ? bounds.minRight : bounds.maxRight;
        const delta = ratios.right - target;
        ratios.right = target;
        ratios.primary += delta;
      } else if (which === 'BOTTOM_UTILITY') {
        ratios.bottom = edge === 'min' ? bounds.min : bounds.max;
      }
      this.current.ratios = this.clampRatios(ratios);
      this.renderLayout();
      this.persistDraft();
    }

    onSplitterPointerDown(splitter, event) {
      if (this.workbench.dataset.layoutMode !== 'edit') return;
      if (event.button != null && event.button !== 0) return;
      event.preventDefault();
      const which = splitter.getAttribute('data-cockpit-splitter');
      try {
        splitter.setPointerCapture(event.pointerId);
      } catch (_) {
        // ignore
      }
      this._splitterPointer = {
        id: event.pointerId,
        which,
        lastX: event.clientX,
        lastY: event.clientY,
        splitter
      };
    }

    onSplitterPointerMove(event) {
      if (!this._splitterPointer || this._splitterPointer.id !== event.pointerId) return;
      const metrics = this.workbenchMetrics();
      const dx = event.clientX - this._splitterPointer.lastX;
      const dy = event.clientY - this._splitterPointer.lastY;
      this._splitterPointer.lastX = event.clientX;
      this._splitterPointer.lastY = event.clientY;
      const which = this._splitterPointer.which;
      let delta = 0;
      if (which === 'BOTTOM_UTILITY') {
        delta = dy / metrics.height;
      } else {
        delta = dx / metrics.width;
      }
      this._pendingResize = { which, delta: (this._pendingResize?.delta || 0) + delta };
      if (!this.resizeFrame) {
        this.resizeFrame = requestAnimationFrame(() => {
          this.applyPendingResize();
          this.resizeFrame = null;
        });
      }
    }

    onSplitterPointerUp(event) {
      if (!this._splitterPointer || this._splitterPointer.id !== event.pointerId) return;
      const splitter = this._splitterPointer.splitter;
      this._splitterPointer = null;
      if (this.resizeFrame) {
        cancelAnimationFrame(this.resizeFrame);
        this.resizeFrame = null;
      }
      this.applyPendingResize();
      try {
        splitter?.releasePointerCapture?.(event.pointerId);
      } catch (_) {
        // ignore
      }
    }

    applyPendingResize() {
      if (!this._pendingResize) return;
      const { which, delta } = this._pendingResize;
      this._pendingResize = null;
      if (Math.abs(delta) < 0.0001) return;
      this.resize(which, delta);
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
      this._focusHomePanel = shell.closest('[data-module-panel]')
        || shell.parentElement;
      this.focusedModuleKey = key;
      this.focusMount.appendChild(shell);
      this.workbench.setAttribute('inert', '');
      this.focusLayer.hidden = false;
      this.clearAttention(key);
      this.syncEditChrome();
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
        // Fall back to re-render placement.
        this.renderLayout();
      }
      this.focusedModuleKey = null;
      this._focusHomePanel = null;
      this.workbench.removeAttribute('inert');
      if (this.focusLayer) this.focusLayer.hidden = true;
      this.syncEditChrome();
      this.emitAllVisibility();
      for (const mk of this.modules.keys()) {
        window.dispatchEvent(new CustomEvent('cockpit:module-mode', {
          detail: { moduleKey: mk, mode: this.moduleMode(mk) }
        }));
      }
      if (!options.silent) {
        const prefer = this._focusReturnEl;
        this._focusReturnEl = null;
        const focusBtn = document.querySelector(
          `[data-module-key="${key}"] [data-module-focus]`
        );
        // Prefer the module Focus control so Escape/Return always restore an actionable
        // trigger, even when focus was entered programmatically or from another control.
        const target = (prefer && document.contains(prefer))
          ? prefer
          : ((focusBtn && document.contains(focusBtn)) ? focusBtn : null);
        target?.focus?.();
      } else {
        this._focusReturnEl = null;
      }
    }

    /**
     * Standardized module chrome states. Existing body content is retained on
     * loading/error so failures stay recoverable without destroying runtime DOM.
     */
    setModuleState(key, state, detail = {}) {
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      if (!shell) return;
      const status = shell.querySelector('[data-module-status]');
      const error = shell.querySelector('[data-module-error]');
      const body = shell.querySelector('[data-module-body]');
      const def = this.modules.get(key);
      const loadingMsg = def?.states?.loadingMessage
        || shell.getAttribute('data-loading-message')
        || 'Loading…';
      const emptyMsg = detail.message
        || def?.states?.emptyMessage
        || shell.getAttribute('data-empty-message')
        || '';
      const errorMsg = detail.message
        || def?.states?.errorMessage
        || shell.getAttribute('data-error-message')
        || 'Refresh failed.';

      if (typeof detail.retry === 'function') {
        this._retryCallbacks.set(key, detail.retry);
      } else if (state === 'ready' || state === 'empty') {
        this._retryCallbacks.delete(key);
      }

      if (status) {
        if (state === 'loading') {
          status.hidden = false;
          status.textContent = loadingMsg;
        } else if (state === 'empty') {
          status.hidden = false;
          status.textContent = emptyMsg;
        } else if (state === 'ready' || state === 'idle') {
          status.hidden = true;
          status.textContent = '';
        } else if (state === 'error') {
          // Keep any prior loading/empty status cleared; error chrome owns the alert.
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
          if (span) span.textContent = errorMsg;
        } else {
          error.hidden = true;
        }
      }

      // Ready/empty/error never wipe existing body content; only empty may leave it hidden
      // when the body has no meaningful children (B2 modules may clear themselves).
      if (body && (state === 'ready' || state === 'loading' || state === 'error')) {
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
        this.setModuleState(key, 'error', {
          message: error?.message,
          retry: cb
        });
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

    /**
     * A module is visible when its panel is active, its zone is not collapsed,
     * Screen Safety is not hiding it (HIDE), and either the workbench or focus layer exposes it.
     */
    isModuleVisible(key) {
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      if (!shell) return false;

      if (this.focusedModuleKey === key) {
        return !!(this.focusMount && this.focusMount.contains(shell));
      }

      if (this.depot && this.depot.contains(shell)) return false;

      const panel = shell.closest('[role="tabpanel"]');
      const zone = shell.closest('[data-cockpit-zone]');
      if (!panel || !zone) return false;
      if (panel.hidden) return false;
      if (zone.dataset.collapsed === 'true') return false;
      return true;
    }

    moduleMode(key) {
      if (this.focusedModuleKey === key) return 'FOCUSED';
      const shell = document.querySelector(`[data-module-key="${key}"]`);
      if (shell && shell.getAttribute('data-compact') === 'true') return 'COMPACT';
      return 'STANDARD';
    }

    emitAllVisibility() {
      for (const key of this.modules.keys()) {
        window.dispatchEvent(new CustomEvent('cockpit:module-visibility', {
          detail: { moduleKey: key, visible: this.isModuleVisible(key) }
        }));
      }
    }

    enterEditMode(options = {}) {
      if (!this.current) return;
      if (!options.preserveSnapshot) {
        this.editSnapshot = this.clone(this.current);
      }
      this.workbench.dataset.layoutMode = 'edit';
      this.syncEditChrome();
      if (this.modeButton) this.modeButton.textContent = 'Done';
      this.syncSplitterAria(this.current?.ratios);
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
      this.closeArrangeMenu();
      this.syncEditChrome();
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
      if (!draft) return;
      if (!this.isValidDraftLayout(draft.layout)) {
        this.forgetKey('edit-draft');
        return;
      }
      this._draftPromptShown = true;
      this.showDraftRecovery(draft);
    }

    isValidDraftLayout(layout) {
      if (!layout || typeof layout !== 'object' || layout.schemaVersion !== 1) return false;
      if (typeof layout.name !== 'string'
          || !layout.name.trim()
          || layout.name.trim().length > 80) return false;
      if (!layout.zones || typeof layout.zones !== 'object') return false;

      const seen = new Set();
      const placed = new Set();
      for (const zone of ZONES) {
        const zoneLayout = layout.zones[zone];
        if (!zoneLayout || !Array.isArray(zoneLayout.moduleKeys)
            || typeof zoneLayout.collapsed !== 'boolean') return false;
        if (zone !== 'BOTTOM_UTILITY' && zoneLayout.collapsed) return false;
        if (zone === 'PRIMARY' && zoneLayout.moduleKeys.length === 0) return false;

        for (const key of zoneLayout.moduleKeys) {
          const def = this.modules.get(key);
          if (!def || seen.has(key) || !this.allowedZonesFor(def).includes(zone)) return false;
          seen.add(key);
          placed.add(key);
        }
        const active = zoneLayout.activeModuleKey;
        if (zoneLayout.moduleKeys.length === 0) {
          if (active != null) return false;
        } else if (!zoneLayout.moduleKeys.includes(active)) {
          return false;
        }
      }
      if (Object.keys(layout.zones).some((zone) => !ZONES.includes(zone))) return false;

      const ratios = layout.ratios;
      const values = ratios && [ratios.left, ratios.primary, ratios.right, ratios.bottom];
      if (!values || values.some((value) => !Number.isFinite(value))) return false;
      if (Math.abs(ratios.left + ratios.primary + ratios.right - 1) > 0.001) return false;
      if (ratios.primary < 0.5 || ratios.primary > 0.65) return false;
      if (ratios.bottom < 0.16 || ratios.bottom > 0.4) return false;
      if (ratios.left <= 0 || ratios.right <= 0) return false;

      if (!Array.isArray(layout.compactModuleKeys)) return false;
      const compact = new Set();
      for (const key of layout.compactModuleKeys) {
        const def = this.modules.get(key);
        if (!def || !def.compactSupported || !placed.has(key) || compact.has(key)) return false;
        compact.add(key);
      }
      return true;
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
        this.resumeDraft(draft);
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

    /**
     * Resume an unfinished edit-draft.
     * Baseline (editSnapshot) is the named preset layout so isDirty() reflects
     * draft vs preset; working layout (current) comes from the draft.
     */
    resumeDraft(draft) {
      if (!draft || !this.isValidDraftLayout(draft.layout)) {
        this.forgetKey('edit-draft');
        return false;
      }
      let presetKey = null;
      if (draft.presetKey && this.presets.has(draft.presetKey)) {
        presetKey = draft.presetKey;
      } else if (this.presets.has(this.currentPresetKey)) {
        presetKey = this.currentPresetKey;
      } else {
        presetKey = DEFAULT_KEY;
      }

      this.currentPresetKey = presetKey;
      if (this.picker) this.picker.value = presetKey;

      const named = this.presets.get(presetKey);
      // Dirty baseline: named preset layout (not the draft).
      this.editSnapshot = this.clone(named?.layout || draft.layout);
      // Working layout: draft contents.
      this.current = this.clone(draft.layout);
      this.renderLayout();
      this.emitAllVisibility();
      this.enterEditMode({ preserveSnapshot: true });
      return true;
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

    resolveNamePrompt(value) {
      if (!this._pendingNameResolve) return;
      const resolve = this._pendingNameResolve;
      this._pendingNameResolve = null;
      resolve(value);
    }

    promptName(initial) {
      return new Promise((resolve) => {
        if (!this.nameDialog || !this.nameInput) {
          resolve(window.prompt('Layout name', initial || '') || null);
          return;
        }
        // Avoid stacking resolvers if a previous prompt is still open.
        this.resolveNamePrompt(null);
        this._nameDialogResult = undefined;
        this._pendingNameResolve = resolve;
        this.nameInput.value = initial || '';
        if (typeof this.nameDialog.showModal === 'function') {
          this.nameDialog.showModal();
          this.nameInput.focus();
          this.nameInput.select();
        } else {
          this._pendingNameResolve = null;
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
