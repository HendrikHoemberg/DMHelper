(function () {
  'use strict';

  class DmRequestError extends Error {
    constructor(message, status = 0, correlationId = null, problem = null) {
      super(message);
      this.name = 'DmRequestError';
      this.status = status;
      this.correlationId = correlationId;
      // A 4xx caused by what the DM typed and a 5xx that lost their work are different
      // events. Only the second is a persistence failure, and only the second can be
      // usefully retried.
      this.problem = problem;
      if (status === 0) {
        this.kind = 'network';
      } else if (status === 409) {
        this.kind = 'conflict';
      } else if (status >= 400 && status < 500) {
        this.kind = 'validation';
      } else {
        this.kind = 'server';
      }
      this.retryable = this.kind !== 'validation';
    }
  }

  async function responseError(response) {
    let detail = response.status === 409
      ? 'The item changed before this request completed. Reload and try again.'
      : `The server refused that request (${response.status}).`;
    let correlationId = response.headers.get('X-Correlation-ID');
    let problem = null;
    const contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('json')) {
      try {
        const body = await response.json();
        problem = body;
        detail = body.detail || detail;
        correlationId = body.correlationId || correlationId;
      } catch (_) {
        // A malformed error body must not hide the status/header fallback.
      }
    }
    const error = new DmRequestError(detail, response.status, correlationId);
    error.problem = problem;
    return error;
  }

  function emitRequestEvent(type, detail) {
    document.dispatchEvent(new CustomEvent(type, { detail }));
  }

  window.dmRequest = async function dmRequest(url, options = {}) {
    const detail = { url, options };
    emitRequestEvent('dm:request-start', detail);

    let response;
    try {
      response = await fetch(url, options);
    } catch (_) {
      const error = new DmRequestError('No answer from the server. Check it is still running.');
      emitRequestEvent('dm:request-failure', { ...detail, error, kind: error.kind });
      throw error;
    }
    if (!response.ok) {
      const error = await responseError(response);
      emitRequestEvent('dm:request-failure', { ...detail, error, response, kind: error.kind });
      throw error;
    }
    emitRequestEvent('dm:request-success', { ...detail, response });
    return response;
  };

  window.reportActionFailure = function reportActionFailure(summary, error, retry) {
    const detail = error?.message ? ` ${error.message}` : '';
    // A validation failure is fixed by correcting the input, never by sending it again.
    const offerRetry = Boolean(retry) && error?.retryable !== false;
    window.showToast(summary + detail, 'error', offerRetry ? 15000 : 7000, {
      dedupeKey: summary + '|' + (error?.status ?? 0),
      reference: error?.correlationId || null,
      action: offerRetry ? { label: 'Retry', handler: retry } : null
    });
  };

  window.DmRequestError = DmRequestError;
})();
