(function () {
  'use strict';

  const POLL_MS = 5000;

  function init() {
    const cluster = document.getElementById('runtimeStatus');
    if (!cluster) return;

    const save = cluster.querySelector('[data-status-save]');
    const table = cluster.querySelector('[data-status-table]');

    let inFlight = 0;
    let settleTimer = null;

    function setSave(state, label) {
      save.dataset.state = state;
      save.textContent = label;
    }

    document.body.addEventListener('htmx:beforeRequest', (evt) => {
      const verb = (evt.detail.requestConfig?.verb || '').toLowerCase();
      if (verb === 'get') return;
      inFlight++;
      clearTimeout(settleTimer);
      setSave('busy', 'Saving…');
    });

    document.body.addEventListener('htmx:afterRequest', (evt) => {
      const verb = (evt.detail.requestConfig?.verb || '').toLowerCase();
      if (verb === 'get') return;
      inFlight = Math.max(0, inFlight - 1);
      const xhr = evt.detail.xhr;
      if (!xhr || xhr.status < 200 || xhr.status >= 300) return;
      if (inFlight > 0) return;
      setSave('saved', 'Saved');
      // The cluster is a status line, not a log: it returns to quiet on its own.
      settleTimer = setTimeout(() => setSave('idle', 'Up to date'), 4000);
    });

    function failed() {
      inFlight = 0;
      clearTimeout(settleTimer);
      setSave('error', 'Not saved');
    }

    document.body.addEventListener('htmx:responseError', failed);
    document.body.addEventListener('htmx:sendError', failed);

    async function pollTable() {
      try {
        const response = await fetch('/api/table/status', { headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error(response.status);
        const { connected } = await response.json();
        table.dataset.state = connected > 0 ? 'connected' : 'disconnected';
        table.textContent = connected > 0
          ? (connected === 1 ? 'Table connected' : connected + ' tables connected')
          : 'No table screen';
      } catch (e) {
        table.dataset.state = 'disconnected';
        table.textContent = 'Table unreachable';
      }
    }

    pollTable();
    setInterval(pollTable, POLL_MS);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
