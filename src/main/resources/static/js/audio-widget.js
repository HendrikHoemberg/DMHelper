(function () {
    'use strict';

    var ERROR_MESSAGES = {
        AUTOPLAY_BLOCKED: 'Playback was blocked. Select Enable or Play to continue.',
        CONTENT_UNAVAILABLE: 'This cue is unavailable. Check the reference or choose another cue.',
        PROVIDER_OFFLINE: 'The audio provider did not respond. Check the connection and retry.',
        POLICY_DISABLED: 'This cue uses an audio provider that is not available here.',
        UNSUPPORTED_CONTROL: 'That control is not supported for this cue.'
    };

    function normalizedCategory(error) {
        var category = error && error.message ? String(error.message).toUpperCase() : '';
        return ERROR_MESSAGES[category] ? category : 'CONTENT_UNAVAILABLE';
    }

    function audioCockpitWidget(config) {
        return {
            campaignId: config.campaignId || '',
            sessionId: config.sessionId || '',
            cueName: '',
            cueOwner: '',
            pendingCueName: '',
            sourceLabel: '',
            muted: false,
            hasPendingConfirmation: false,
            hasCue: false,
            enabled: false,
            playing: false,
            volume: 50,
            canSkip: false,
            canVolume: false,
            errorCategory: '',
            errorMessage: '',
            transitionNotice: '',
            visibilityNotice: '',
            audioCues: [],
            overrideCueId: '',

            adapter: null,
            adapterProviderId: '',
            observer: null,
            visible: false,
            pageVisible: true,

            _pendingCue: null,
            _loadedCueKey: '',
            _stateSequence: 0,
            _operationSequence: 0,
            _stateAbortController: null,
            _victoryTimer: null,
            _retryInFlight: false,
            _lastRetryAt: 0,
            _visibilityHandler: null,
            _railsHandler: null,

            init: function () {
                var self = this;
                self.pageVisible = document.visibilityState === 'visible';
                self._visibilityHandler = function () {
                    self.pageVisible = document.visibilityState === 'visible';
                    self.checkPlaybackGate();
                };
                document.addEventListener('visibilitychange', self._visibilityHandler);

                self.observer = new IntersectionObserver(function (entries) {
                    entries.forEach(function (entry) {
                        self.visible = entry.intersectionRatio > 0.5;
                        self.checkPlaybackGate();
                    });
                }, { threshold: [0, 0.5, 0.51, 1] });

                self._railsHandler = function () { self.fetchState(); };
                window.addEventListener('cockpit-rails-refreshed', self._railsHandler);
                self.$nextTick(function () {
                    if (self.$refs.playerViewport) self.observer.observe(self.$refs.playerViewport);
                    self.fetchState();
                    self.fetchCues();
                });
            },

            desiredBrowserProvider: function (cue) {
                if (!cue || !cue.providerAvailable) return '';
                var testProvider = document.documentElement.dataset.testAudioProvider;
                return testProvider === 'FAKE' ? 'FAKE' : String(cue.providerId || '').toUpperCase();
            },

            ensureAdapter: function (cue) {
                var providerId = this.desiredBrowserProvider(cue);
                if (!providerId) return false;
                if (this.adapter && this.adapterProviderId === providerId) return true;

                this.disposeAdapter();
                var factory = (window.AUDIO_PROVIDER_REGISTRY || {})[providerId];
                if (!factory) return false;

                var self = this;
                this.adapter = factory({
                    mount: self.$refs.playerMount,
                    onState: function (state) {
                        self.playing = state === 1;
                    },
                    onError: function (category) {
                        self.handleProviderError(new Error(category));
                    },
                    onAutoplayBlocked: function () {
                        self.playing = false;
                        self.handleProviderError(new Error('AUTOPLAY_BLOCKED'));
                    }
                });
                this.adapterProviderId = providerId;
                return true;
            },

            disposeAdapter: function () {
                this._operationSequence++;
                if (this.adapter) {
                    this.adapter.destroy().catch(function () { /* best-effort provider cleanup */ });
                }
                this.adapter = null;
                this.adapterProviderId = '';
                this.enabled = false;
                this.playing = false;
                this._loadedCueKey = '';
            },

            playbackGateOpen: function () {
                return this.pageVisible && this.visible && !this.muted;
            },

            checkPlaybackGate: function () {
                if (!this.pageVisible || !this.visible) {
                    this.visibilityNotice = this.hasCue
                        ? 'Keep more than half of the visible player on screen to resume playback.' : '';
                    this.pausePlayback('PAUSED');
                    return;
                }
                this.visibilityNotice = '';
                if (this.enabled && this._pendingCue && !this.muted
                        && !this.hasPendingConfirmation && !this.errorCategory) {
                    this.loadAndPlay(this._pendingCue);
                }
            },

            pausePlayback: function (result) {
                if (!this.adapter || !this.enabled || !this.playing) return Promise.resolve();
                var self = this;
                return this.adapter.pause().then(function () {
                    self.playing = false;
                    self.acknowledge(result || 'PAUSED');
                }).catch(function () { /* pausing must never block cockpit actions */ });
            },

            fetchState: function () {
                var self = this;
                // No session yet (cockpit open while status is IDLE) — nothing to resolve.
                if (!self.sessionId || self.sessionId === 'null') {
                    self.hasCue = false;
                    self._pendingCue = null;
                    self.errorCategory = '';
                    self.errorMessage = '';
                    return Promise.resolve();
                }
                var sequence = ++self._stateSequence;
                if (self._stateAbortController) self._stateAbortController.abort();
                self._stateAbortController = new AbortController();
                var url = '/api/v1/campaigns/' + self.campaignId + '/audio/runtime/state'
                    + '?sessionId=' + encodeURIComponent(self.sessionId);
                return window.dmRequest(url, { signal: self._stateAbortController.signal })
                    .then(function (response) { return response.json(); })
                    .then(function (state) {
                        if (sequence !== self._stateSequence) return;
                        self.applyState(state);
                    })
                    .catch(function (error) {
                        if (sequence !== self._stateSequence) return;
                        if (error && error.name === 'AbortError') return;
                        self.errorCategory = 'PROVIDER_OFFLINE';
                        self.errorMessage = 'Audio state could not be refreshed. Retry when ready.';
                    });
            },

            applyState: function (state) {
                state = state || {};
                var cue = state.cue || null;
                var previousKey = this._pendingCue
                    ? String(this._pendingCue.id) + ':' + this._pendingCue.providerReference : '';
                var nextKey = cue ? String(cue.id) + ':' + cue.providerReference : '';

                this.muted = !!state.muted;
                this.hasPendingConfirmation = !!state.hasPendingConfirmation;
                this.pendingCueName = state.pendingCue
                    ? (state.pendingCue.cachedTitle || state.pendingCue.name || '') : '';
                this.hasCue = !!cue;
                this.cueName = cue ? (cue.cachedTitle || cue.name || '') : '';
                this.cueOwner = cue ? (cue.artistOrOwner || '') : '';
                this.sourceLabel = state.sourceLabel || '';
                this.canSkip = !!(cue && cue.capabilities && cue.capabilities.skip);
                this.canVolume = !!(cue && cue.capabilities && cue.capabilities.volume);
                this.scheduleVictoryExpiry(state.victoryUntil);

                if (previousKey !== nextKey) {
                    this._operationSequence++;
                    this.errorCategory = '';
                    this.errorMessage = '';
                    this.transitionNotice = '';
                    window.dispatchEvent(new CustomEvent('cockpit:module-invalidate', {
                        detail: { moduleKey: 'session-log', reason: 'audio-cue-changed' }
                    }));
                }

                this._pendingCue = cue;
                if (!cue) {
                    this.pausePlayback('PAUSED');
                    return;
                }
                if (!cue.providerAvailable || !this.ensureAdapter(cue)) {
                    this.disposeAdapter();
                    this.handleProviderError(new Error('POLICY_DISABLED'));
                    return;
                }
                if (this.muted || this.hasPendingConfirmation) {
                    this.pausePlayback('PAUSED');
                    return;
                }
                if (this.enabled && this.playbackGateOpen() && !this.errorCategory) {
                    this.loadAndPlay(cue);
                }
            },

            enablePlayback: function () {
                if (!this._pendingCue || this.muted) return;
                if (!this.ensureAdapter(this._pendingCue)) {
                    this.handleProviderError(new Error('POLICY_DISABLED'));
                    return;
                }
                var self = this;
                var operation = ++this._operationSequence;
                this.errorCategory = '';
                this.errorMessage = '';
                this.adapter.enable().then(function () {
                    if (operation !== self._operationSequence) return;
                    self.enabled = true;
                    if (!self.playbackGateOpen()) {
                        self.visibilityNotice = 'Keep more than half of the visible player on screen to start playback.';
                        return;
                    }
                    return self.loadAndPlay(self._pendingCue);
                }).catch(function (error) {
                    if (operation === self._operationSequence) self.handleProviderError(error);
                });
            },

            loadAndPlay: function (cue) {
                if (!cue || !this.adapter || !this.enabled || !this.playbackGateOpen()) return Promise.resolve();
                var self = this;
                var cueKey = String(cue.id) + ':' + cue.providerReference;
                var operation = ++this._operationSequence;

                if (this._loadedCueKey === cueKey) {
                    if (this.playing) return Promise.resolve();
                    return this.adapter.resume().then(function () {
                        if (operation !== self._operationSequence) return;
                        self.playing = true;
                        self.clearProviderError();
                        self.acknowledge('PLAYING');
                    }).catch(function (error) {
                        if (operation === self._operationSequence) self.handleProviderError(error);
                    });
                }

                if (this._loadedCueKey && cue.transitionPreference === 'CROSSFADE') {
                    this.transitionNotice = 'Crossfade is unavailable for this provider; switched with a cut.';
                } else {
                    this.transitionNotice = '';
                }
                var reference = { kind: cue.referenceKind || 'VIDEO', id: cue.providerReference };
                return this.adapter.load(reference).then(function () {
                    if (operation !== self._operationSequence) throw new Error('STALE_OPERATION');
                    if (cue.volumeHint != null && cue.volumeHint >= 0 && cue.volumeHint <= 100) {
                        self.volume = cue.volumeHint;
                    }
                    return self.canVolume ? self.adapter.setVolume(self.volume) : Promise.resolve();
                }).then(function () {
                    if (operation !== self._operationSequence) throw new Error('STALE_OPERATION');
                    return self.adapter.play();
                }).then(function () {
                    if (operation !== self._operationSequence) return;
                    self._loadedCueKey = cueKey;
                    self.playing = true;
                    self.clearProviderError();
                    self.acknowledge('PLAYING');
                }).catch(function (error) {
                    if (error && error.message === 'STALE_OPERATION') return;
                    if (operation === self._operationSequence) self.handleProviderError(error);
                });
            },

            clearProviderError: function () {
                this.errorCategory = '';
                this.errorMessage = '';
            },

            handleProviderError: function (error) {
                var category = normalizedCategory(error);
                this.errorCategory = category;
                this.errorMessage = ERROR_MESSAGES[category];
                this.playing = false;
                this.acknowledge(category);
            },

            acknowledge: function (result) {
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/runtime/acknowledge'
                    + '?sessionId=' + encodeURIComponent(this.sessionId)
                    + '&result=' + encodeURIComponent(result);
                window.dmRequest(url, { method: 'POST' })
                    .catch(function () { /* acknowledgement is bounded to the audio widget */ });
            },

            togglePlayback: function () {
                if (!this.adapter || !this.enabled) return;
                if (this.playing) {
                    this.pausePlayback('PAUSED');
                } else if (this.playbackGateOpen()) {
                    this.loadAndPlay(this._pendingCue);
                } else {
                    this.visibilityNotice = 'Keep more than half of the visible player on screen to resume playback.';
                }
            },

            skip: function () {
                if (!this.adapter || !this.enabled || !this.canSkip) return;
                var self = this;
                this.adapter.skip().then(function () {
                    self.clearProviderError();
                    self.acknowledge('PLAYING');
                }).catch(function (error) { self.handleProviderError(error); });
            },

            setVolume: function () {
                if (!this.adapter || !this.enabled || !this.canVolume) return;
                var self = this;
                this.adapter.setVolume(Number(this.volume))
                    .catch(function (error) { self.handleProviderError(error); });
            },

            toggleMute: function () {
                var self = this;
                var endpoint = self.muted ? 'unmute' : 'mute';
                var url = '/api/v1/campaigns/' + self.campaignId + '/audio/runtime/' + endpoint
                    + '?sessionId=' + encodeURIComponent(self.sessionId);
                window.dmRequest(url, { method: 'POST' }).then(function () {
                    self.muted = !self.muted;
                    if (self.muted) return self.pausePlayback('PAUSED');
                    return self.fetchState();
                }).catch(function (error) {
                    window.reportActionFailure('Could not toggle mute.', error, function () { self.toggleMute(); });
                });
            },

            confirmCue: function () {
                var self = this;
                return self.runtimeMutation('confirm').then(function () {
                    self.hasPendingConfirmation = false;
                    return self.fetchState();
                }).catch(function (error) {
                    window.reportActionFailure('Could not confirm cue.', error, function () { self.confirmCue(); });
                });
            },

            declineCue: function () {
                var self = this;
                return self.runtimeMutation('decline').then(function () {
                    self.hasPendingConfirmation = false;
                    return self.fetchState();
                }).catch(function (error) {
                    window.reportActionFailure('Could not decline cue.', error, function () { self.declineCue(); });
                });
            },

            runtimeMutation: function (action) {
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/runtime/' + action
                    + '?sessionId=' + encodeURIComponent(this.sessionId);
                return window.dmRequest(url, { method: 'POST' });
            },

            retry: function () {
                var now = performance.now();
                if (this._retryInFlight || now - this._lastRetryAt < 500) return;
                this._retryInFlight = true;
                this._lastRetryAt = now;
                this.errorMessage = '';
                this.errorCategory = '';
                var self = this;
                this.fetchState().finally(function () { self._retryInFlight = false; });
            },

            setOverride: function (cueId) {
                if (!cueId) return this.clearOverride();
                var self = this;
                var url = '/api/v1/campaigns/' + self.campaignId + '/audio/runtime/override'
                    + '?sessionId=' + encodeURIComponent(self.sessionId)
                    + '&cueId=' + encodeURIComponent(cueId);
                window.dmRequest(url, { method: 'POST' })
                    .then(function () { return self.fetchState(); })
                    .catch(function (error) {
                        window.reportActionFailure('Could not set override.', error, function () { self.setOverride(cueId); });
                    });
            },

            clearOverride: function () {
                var self = this;
                self.overrideCueId = '';
                var url = '/api/v1/campaigns/' + self.campaignId + '/audio/runtime/override'
                    + '?sessionId=' + encodeURIComponent(self.sessionId);
                window.dmRequest(url, { method: 'DELETE' })
                    .then(function () { return self.fetchState(); })
                    .catch(function (error) {
                        window.reportActionFailure('Could not clear override.', error, function () { self.clearOverride(); });
                    });
            },

            fetchCues: function () {
                var self = this;
                var url = '/api/v1/campaigns/' + self.campaignId + '/audio/cues';
                return window.dmRequest(url).then(function (response) { return response.json(); })
                    .then(function (cues) { self.audioCues = cues; })
                    .catch(function (error) {
                        window.reportActionFailure('Could not load audio cues.', error, function () { self.fetchCues(); });
                    });
            },

            scheduleVictoryExpiry: function (victoryUntil) {
                if (this._victoryTimer) window.clearTimeout(this._victoryTimer);
                this._victoryTimer = null;
                if (!victoryUntil) return;
                var delay = new Date(victoryUntil).getTime() - Date.now();
                if (!Number.isFinite(delay)) return;
                var self = this;
                this._victoryTimer = window.setTimeout(function () {
                    self.runtimeMutation('victory/expire')
                        .then(function () { return self.fetchState(); })
                        .catch(function () { return self.fetchState(); });
                }, Math.max(0, Math.min(delay, 2147483647)));
            },

            destroy: function () {
                if (this.observer) this.observer.disconnect();
                if (this._stateAbortController) this._stateAbortController.abort();
                if (this._victoryTimer) window.clearTimeout(this._victoryTimer);
                document.removeEventListener('visibilitychange', this._visibilityHandler);
                window.removeEventListener('cockpit-rails-refreshed', this._railsHandler);
                this.disposeAdapter();
            }
        };
    }

    window.audioCockpitWidget = audioCockpitWidget;
})();
