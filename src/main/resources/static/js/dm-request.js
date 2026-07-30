(function () {
  'use strict';

  class DmRequestError extends Error {
    constructor(message, status = 0, correlationId = null, problem = null) {
      super(message);
      this.name = 'DmRequestError';
      this.status = status;
      this.correlationId = correlationId;
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
    const error = new DmRequestError(detail, response.status, correlationId, problem);
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
      emitRequestEvent('dm:request-failure', { ...detail, error });
      throw error;
    }
    if (!response.ok) {
      const error = await responseError(response);
      emitRequestEvent('dm:request-failure', { ...detail, error, response });
      throw error;
    }
    emitRequestEvent('dm:request-success', { ...detail, response });
    return response;
  };

  window.reportActionFailure = function reportActionFailure(summary, error, retry) {
    const detail = error?.message ? ` ${error.message}` : '';
    const message = summary + detail;
    window.showToast(message, 'error', retry ? 15000 : 7000, {
      dedupeKey: summary + '|' + (error?.status ?? 0),
      reference: error?.correlationId || null,
      action: (error?.retryable !== false && retry) ? { label: 'Retry', handler: retry } : null
    });
  };

  window.DmRequestError = DmRequestError;
})();
