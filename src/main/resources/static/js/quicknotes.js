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
        const response = await fetch(url, options);
        if (!response.ok) {
          throw new Error('HTTP ' + response.status);
        }
        return response;
      },

      reportFailure(message, error) {
        console.error(message, error);
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
          this.reportFailure('Could not load quick notes.', error);
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
        } catch (error) {
          this.newBody = body;
          this.reportFailure('Could not save the quick note. Your text has been kept.', error);
        }
      },

      async remove(id) {
        try {
          await this.request(
            '/api/v1/campaigns/' + this.campaignId + '/quicknotes/' + id,
            { method: 'DELETE' }
          );
          this.items = this.items.filter(item => item.id !== id);
        } catch (error) {
          this.reportFailure('Could not delete the quick note. Nothing was changed.', error);
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
          window.location = result.url;
        } catch (error) {
          this.reportFailure('Could not promote the quick note. Nothing was changed.', error);
        }
      },

      formatTime(iso) {
        if (!iso) return '';
        return new Date(iso).toLocaleString();
      }
    }));
  });
})();
