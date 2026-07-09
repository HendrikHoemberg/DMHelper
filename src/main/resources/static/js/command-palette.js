// command-palette.js — Ctrl+K command palette for DMHelper

(function () {
  'use strict';

  document.addEventListener('alpine:init', () => {
    Alpine.data('commandPalette', () => ({
      open: false,
      query: '',
      results: [],
      selectedIndex: -1,
      loading: false,
      _debounceTimer: null,
      _abortController: null,

      init() {
        window.addEventListener('command-palette-toggle', () => {
          this.open = !this.open;
          if (this.open) {
            this.$nextTick(() => {
              this.$refs.input?.focus();
            });
          }
        });
      },

      onInput() {
        clearTimeout(this._debounceTimer);
        if (this.query.trim().length < 2) {
          this.results = [];
          this.selectedIndex = -1;
          return;
        }
        this._debounceTimer = setTimeout(() => this.doSearch(), 250);
      },

      async doSearch() {
        if (this._abortController) {
          this._abortController.abort();
        }
        this._abortController = new AbortController();

        const q = this.query.trim();
        if (q.length < 2) {
          this.results = [];
          this.selectedIndex = -1;
          return;
        }
        this.loading = true;
        this.selectedIndex = -1;
        try {
          const campaignId = document.body.dataset.campaignId || '';
          let url = '/api/v1/search?q=' + encodeURIComponent(q);
          if (campaignId) {
            url += '&campaignId=' + encodeURIComponent(campaignId);
          }
          const resp = await fetch(url, { signal: this._abortController.signal });
          if (resp.ok) {
            this.results = await resp.json();
          } else {
            this.results = [];
          }
        } catch (e) {
          if (e.name !== 'AbortError') {
            this.results = [];
          }
        } finally {
          this.loading = false;
        }
      },

      onKeydown(e) {
        if (e.key === 'ArrowDown') {
          e.preventDefault();
          this.selectedIndex = Math.min(this.selectedIndex + 1, this.results.length - 1);
          return;
        }
        if (e.key === 'ArrowUp') {
          e.preventDefault();
          this.selectedIndex = Math.max(this.selectedIndex - 1, -1);
          return;
        }
        if (e.key === 'Enter') {
          e.preventDefault();
          if (this.selectedIndex >= 0 && this.selectedIndex < this.results.length) {
            this.navigateTo(this.results[this.selectedIndex]);
          }
          return;
        }
      },

      navigateTo(item) {
        this.open = false;
        this.query = '';
        this.results = [];
        if (item.url) {
          window.location.href = item.url;
        }
      },

      typeLabel(type) {
        const labels = {
          'note': 'Note', 'quicknote': 'Quick Note', 'statblock': 'Monster',
          'spell': 'Spell', 'condition': 'Condition', 'rule': 'Rule',
          'equipment': 'Equipment', 'magic-item': 'Magic Item',
          'class': 'Class', 'species': 'Species', 'background': 'Background',
          'feat': 'Feat', 'map': 'Map', 'encounter': 'Encounter',
          'handout': 'Handout', 'party-member': 'Party Member'
        };
        return labels[type] || type;
      }
    }));
  });
})();
