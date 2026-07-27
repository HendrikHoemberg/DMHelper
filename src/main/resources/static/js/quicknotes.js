(function () {
  'use strict';

  document.addEventListener('alpine:init', () => {
    Alpine.data('quicknotes', () => ({
      items: [],
      newBody: '',
      campaignId: '',
      targetType: '',
      targetId: '',

      async init() {
        this.campaignId = this.$el.dataset.campaignId;
        this.targetType = this.$el.dataset.targetType;
        this.targetId = this.$el.dataset.targetId;
        await this.load();
      },

      async request(url, options = {}) {
        return window.dmRequest(url, options);
      },

      reportFailure(message, error, retry = null) {
        if (window.reportActionFailure) {
          window.reportActionFailure(message, error, retry);
          return;
        }
        window.showToast?.(message, 'error', 5000);
      },

      async load() {
        try {
          const response = await this.request(
            '/api/v1/campaigns/' + this.campaignId + '/quicknotes?targetType='
              + this.targetType + '&targetId=' + this.targetId
          );
          this.items = await response.json();
        } catch (error) {
          this.reportFailure('Could not load quick notes.', error, () => this.load());
        }
      },

      async add() {
        const body = this.newBody.trim();
        if (!body) return;
        try {
          const formData = new FormData();
          formData.append('targetType', this.targetType);
          formData.append('targetId', this.targetId);
          formData.append('body', body);
          const response = await this.request(
            '/api/v1/campaigns/' + this.campaignId + '/quicknotes',
            { method: 'POST', body: formData }
          );
          this.items.push(await response.json());
          this.newBody = '';
          this._notifyModuleRefresh();
        } catch (error) {
          this.newBody = body;
          this.reportFailure('Could not save the quick note. Your text has been kept.', error,
            () => this.add());
        }
      },

      async remove(id) {
        try {
          await this.request(
            '/api/v1/campaigns/' + this.campaignId + '/quicknotes/' + id,
            { method: 'DELETE' }
          );
          this.items = this.items.filter(item => item.id !== id);
          this._notifyModuleRefresh();
        } catch (error) {
          this.reportFailure('Could not delete the quick note. Nothing was changed.', error,
            () => this.remove(id));
        }
      },

      async promote(quicknote) {
        try {
          const response = await this.request(
            '/api/v1/campaigns/' + this.campaignId + '/quicknotes/' + quicknote.id + '/promote',
            { method: 'POST' }
          );
          const result = await response.json();
          this.items = this.items.filter(item => item.id !== quicknote.id);
          this._notifyModuleRefresh();
          window.location = result.url;
        } catch (error) {
          this.reportFailure('Could not promote the quick note. Nothing was changed.', error,
            () => this.promote(quicknote));
        }
      },

      async promoteSessionPlan(quicknote) {
        try {
          const params = new URLSearchParams({
            title: 'Release rehearsal plan',
            type: 'SESSION_PLAN'
          });
          const response = await this.request(
            '/api/v1/campaigns/' + this.campaignId + '/quicknotes/' + quicknote.id
              + '/promote?' + params.toString(),
            { method: 'POST' }
          );
          await response.json();
          this.items = this.items.filter(item => item.id !== quicknote.id);
          this._notifyModuleRefresh();
          window.dispatchEvent(new CustomEvent('cockpit:module-refresh', {
            detail: { moduleKey: 'session-plan', reason: 'quicknote-promoted-to-session-plan' }
          }));
        } catch (error) {
          this.reportFailure('Could not promote the quick note to the session plan. Nothing was changed.', error,
            () => this.promoteSessionPlan(quicknote));
        }
      },

      formatTime(iso) {
        if (!iso) return '';
        return new Date(iso).toLocaleString();
      },

      _notifyModuleRefresh() {
        window.dispatchEvent(new CustomEvent('cockpit:module-refresh', {
          detail: { moduleKey: 'quick-notes', reason: 'quicknote-mutated' }
        }));
        window.dispatchEvent(new CustomEvent('cockpit:module-invalidate', {
          detail: { moduleKey: 'session-log', reason: 'quicknote-mutated' }
        }));
      }
    }));
  });
})();
