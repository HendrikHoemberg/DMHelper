(function () {
  'use strict';

  document.addEventListener('alpine:init', () => {
    Alpine.data('cockpitReference', () => ({
      query: '',
      results: [],
      loading: false,
      errorMessage: '',
      campaignId: '',
      abortController: null,
      debounceTimer: null,
      statblock: null,
      statblockError: '',

      init() {
        const shell = this.$el.closest('[data-module-key]');
        if (shell) {
          const campaignEl = document.querySelector('[data-campaign-id]');
          if (campaignEl) {
            this.campaignId = campaignEl.getAttribute('data-campaign-id');
          }
        }
        window.addEventListener('cockpit:show-reference', (e) => {
          if (e.detail?.type !== 'statblock' || !e.detail.id) return;
          window.cockpitLayout?.revealModule?.('reference');
          this.showStatblock(e.detail.id);
        });
      },

      async search() {
        const q = (this.query || '').trim();
        if (q.length < 2) {
          this.results = [];
          this.errorMessage = '';
          return;
        }

        if (this.debounceTimer) {
          clearTimeout(this.debounceTimer);
        }

        this.debounceTimer = setTimeout(() => {
          this._doSearch(q);
        }, 250);
      },

      async _doSearch(q) {
        if (this.abortController) {
          this.abortController.abort();
        }
        this.abortController = new AbortController();

        this.loading = true;
        this.errorMessage = '';

        try {
          const endpoint = '/api/v1/search?q=' + encodeURIComponent(q)
            + (this.campaignId ? '&campaignId=' + encodeURIComponent(this.campaignId) : '');
          const response = await window.dmRequest(endpoint, {
            signal: this.abortController.signal,
          });
          const data = await response.json();
          this.results = this._groupResults(data);
        } catch (error) {
          if (error && error.name === 'AbortError') return;
          this.errorMessage = 'Search failed. Please try again.';
          this.results = [];
        } finally {
          this.loading = false;
        }
      },

      _groupResults(data) {
        const groups = [];
        const byType = {};
        if (Array.isArray(data)) {
          for (const item of data) {
            const type = item.type || 'other';
            if (!byType[type]) byType[type] = [];
            byType[type].push(item);
          }
        } else if (data && typeof data === 'object') {
          for (const key of Object.keys(data)) {
            const items = Array.isArray(data[key]) ? data[key] : [];
            if (items.length > 0) byType[key] = items;
          }
        }
        const typeOrder = ['campaign', 'rules', 'statblocks', 'conditions'];
        const typeLabels = {
          campaign: 'Campaign',
          rules: 'Rules',
          statblocks: 'Statblocks',
          conditions: 'Conditions',
        };
        for (const type of typeOrder) {
          if (byType[type] && byType[type].length > 0) {
            groups.push({
              label: typeLabels[type] || type,
              items: byType[type],
            });
          }
        }
        for (const type of Object.keys(byType)) {
          if (!typeOrder.includes(type)) {
            groups.push({
              label: typeLabels[type] || type,
              items: byType[type],
            });
          }
        }
        return groups;
      },

      selectItem(item) {
        if (!item || !item.id) return;
        if (item.type === 'statblock') {
          this.clearStatblock();
          this.showStatblock(item.id);
          return;
        }
        let url = '';
        switch (item.type) {
          case 'rules':
            url = '/library/rules/' + item.id;
            break;
          case 'conditions':
            url = '/library/conditions/' + item.id;
            break;
          default:
            url = item.url || null;
        }
        if (url) {
          window.open(url, '_blank', 'noopener');
        }
      },

      async showStatblock(id) {
        this.statblockError = '';
        try {
          const resp = await window.dmRequest('/api/v1/library/statblocks/' + encodeURIComponent(id));
          this.statblock = await resp.json();
        } catch (error) {
          this.statblock = null;
          this.statblockError = 'Could not load that statblock.';
        }
      },

      clearStatblock() {
        this.statblock = null;
        this.statblockError = '';
      },
    }));
  });
})();
