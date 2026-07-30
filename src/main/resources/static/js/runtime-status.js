(function () {
  'use strict';

  function init() {
    const cluster = document.getElementById('runtimeStatus');
    if (!cluster) return;

    const save = cluster.querySelector('[data-status-save]');

    let htmxInFlight = 0;
    let dmInFlight = 0;
    let mutationEpoch = 0;
    let failedMutationEpoch = null;
    let settleTimer = null;
    const htmxRequestStates = new WeakMap();

    const input = cluster.querySelector('[data-status-input]');

    function setSave(state, label) {
      save.dataset.state = state;
      save.textContent = label;
    }

    function setInputRejection(message) {
      if (!input) return;
      input.hidden = false;
      input.dataset.state = 'rejected';
      input.textContent = message;
      clearTimeout(input._rejectionTimer);
      input._rejectionTimer = setTimeout(() => {
        input.hidden = true;
      }, 8000);
    }

    function hasInFlightRequests() {
      return htmxInFlight > 0 || dmInFlight > 0;
    }

    function markSaved() {
      if (hasInFlightRequests() || failedMutationEpoch === mutationEpoch) return;
      setSave('saved', 'Saved');
      // The cluster is a status line, not a log: it returns to quiet on its own.
      settleTimer = setTimeout(() => setSave('idle', 'Up to date'), 4000);
    }

    function startMutation(source) {
      if (!hasInFlightRequests()) {
        mutationEpoch++;
        failedMutationEpoch = null;
      }
      if (source === 'htmx') htmxInFlight++;
      if (source === 'dm') dmInFlight++;
      clearTimeout(settleTimer);
      setSave('busy', 'Saving…');
    }

    function isMutation(detail) {
      return (detail?.options?.method || 'GET').toLowerCase() !== 'get';
    }

    function htmxRequestState(detail) {
      const request = detail?.xhr || detail?.requestConfig;
      if (!request || (typeof request !== 'object' && typeof request !== 'function')) return null;
      let state = htmxRequestStates.get(request);
      if (!state) {
        state = {settled: false};
        htmxRequestStates.set(request, state);
      }
      return state;
    }

    function settleHtmx(detail) {
      const state = htmxRequestState(detail);
      if (state?.settled) return;
      if (state) state.settled = true;
      htmxInFlight = Math.max(0, htmxInFlight - 1);
    }

    document.body.addEventListener('htmx:beforeRequest', (evt) => {
      const verb = (evt.detail.requestConfig?.verb || '').toLowerCase();
      if (verb === 'get') return;
      htmxRequestState(evt.detail);
      startMutation('htmx');
    });

    document.body.addEventListener('htmx:afterRequest', (evt) => {
      const verb = (evt.detail.requestConfig?.verb || '').toLowerCase();
      if (verb === 'get') return;
      settleHtmx(evt.detail);
      const xhr = evt.detail.xhr;
      if (!xhr || xhr.status < 200 || xhr.status >= 300) return;
      markSaved();
    });

    function rejected(source) {
      if (source === 'dm') dmInFlight = Math.max(0, dmInFlight - 1);
      setInputRejection('Input not accepted');
      markSaved();
    }

    function failed(source) {
      if (source === 'dm') dmInFlight = Math.max(0, dmInFlight - 1);
      failedMutationEpoch = mutationEpoch;
      clearTimeout(settleTimer);
      setSave('error', 'Not saved');
    }

    document.body.addEventListener('htmx:responseError', (evt) => {
      settleHtmx(evt.detail);
      const status = evt.detail?.xhr?.status;
      if (status >= 400 && status < 500 && status !== 409) {
        rejected('htmx');
      } else {
        failed('htmx');
      }
    });
    document.body.addEventListener('htmx:sendError', (evt) => {
      settleHtmx(evt.detail);
      failed('htmx');
    });

    document.addEventListener('dm:request-start', (evt) => {
      if (!isMutation(evt.detail)) return;
      startMutation('dm');
    });

    document.addEventListener('dm:request-success', (evt) => {
      if (!isMutation(evt.detail)) return;
      dmInFlight = Math.max(0, dmInFlight - 1);
      markSaved();
    });

    document.addEventListener('dm:request-failure', (evt) => {
      if (!isMutation(evt.detail)) return;
      if (evt.detail.error?.kind === 'validation') {
        rejected('dm');
      } else {
        failed('dm');
      }
    });

  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
