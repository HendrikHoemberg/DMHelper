(function () {
    'use strict';

    function audioCockpitWidget(config) {
        return {
            campaignId: config.campaignId || '',
            sessionId: config.sessionId || '',

            cueName: '',
            sourceLabel: '',
            muted: false,
            hasPendingConfirmation: false,
            hasCue: false,
            enabled: false,
            playing: false,
            volume: 50,
            errorMessage: '',
            audioCues: [],
            overrideCueId: '',

            adapter: null,
            observer: null,
            visible: false,
            pageVisible: true,

            _pendingCue: null,

            init: function () {
                var self = this;

                self.pageVisible = document.visibilityState === 'visible';
                document.addEventListener('visibilitychange', function () {
                    self.pageVisible = document.visibilityState === 'visible';
                    self.checkPlaybackGate();
                });

                self.observer = new IntersectionObserver(function (entries) {
                    entries.forEach(function (entry) {
                        self.visible = entry.intersectionRatio > 0.5;
                        self.checkPlaybackGate();
                    });
                });

                self.$nextTick(function () {
                    if (self.$refs.playerMount) {
                        self.observer.observe(self.$refs.playerMount);
                    }
                    self.buildAdapter();
                    self.fetchState();
                    self.fetchCues();
                });
                document.addEventListener('cockpit-rails-refreshed', function () {
                    self.fetchState();
                });
            },

            buildAdapter: function () {
                var providers = window.AUDIO_PROVIDER_REGISTRY || {};
                var providerId = 'YOUTUBE';

                if (document.documentElement.dataset.testAudioProvider) {
                    providerId = document.documentElement.dataset.testAudioProvider;
                }

                var factory = providers[providerId];
                if (!factory) return;

                var self = this;
                self.adapter = factory({
                    mount: self.$refs.playerMount,
                    onState: function (state) {
                        self.playing = state === 1;
                    },
                    onError: function (category) {
                        self.errorMessage = category;
                        self.$nextTick(function () {
                            var live = self.$el.querySelector('[aria-live]');
                            if (live) live.textContent = self.errorMessage;
                        });
                    },
                    onAutoplayBlocked: function () {
                        self.enabled = false;
                        self.errorMessage = 'AUTOPLAY_BLOCKED';
                    }
                });
            },

            checkPlaybackGate: function () {
                if (!this.adapter) return;
                if (!this.pageVisible || !this.visible) {
                    if (this.playing) {
                        var self = this;
                        this.adapter.pause()['catch'](function () {});
                        self.playing = false;
                    }
                }
            },

            fetchState: function () {
                var self = this;
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/runtime/state'
                    + '?sessionId=' + encodeURIComponent(this.sessionId);
                window.dmRequest(url)
                    .then(function (resp) { return resp.json(); })
                    .then(function (state) { self.applyState(state); })
                    .catch(function () {});
            },

            applyState: function (state) {
                if (!state || !state.actionableAvailable) {
                    this.hasCue = false;
                    this.cueName = '';
                    this.sourceLabel = '';
                    return;
                }
                this.hasCue = true;
                this.hasPendingConfirmation = state.hasPendingConfirmation;
                this.muted = state.muted;
                this.cueName = state.cue ? (state.cue.cachedTitle || state.cue.name) : '';
                this.sourceLabel = state.sourceLabel || '';

                if (!state.muted && state.cue && !this.hasPendingConfirmation) {
                    this.loadAndPlay(state.cue);
                }
            },

            loadAndPlay: function (cue) {
                if (!this.adapter) {
                    this._pendingCue = cue;
                    return;
                }
                if (!this.pageVisible || !this.visible) {
                    this._pendingCue = cue;
                    return;
                }

                var self = this;
                var promise;

                if (!this.enabled) {
                    this.errorMessage = '';
                    promise = this.adapter.enable()
                        .then(function () {
                            self.enabled = true;
                            return self.loadCueAndPlay(cue);
                        });
                } else {
                    promise = this.loadCueAndPlay(cue);
                }

                promise['catch'](function (err) {
                    self.handleProviderError(err);
                });
            },

            loadCueAndPlay: function (cue) {
                var self = this;
                var ref = {
                    kind: cue.referenceKind || 'VIDEO',
                    id: cue.providerReference
                };

                return this.adapter.load(ref)
                    .then(function () {
                        if (cue.volumeHint != null && cue.volumeHint >= 0 && cue.volumeHint <= 100) {
                            self.volume = cue.volumeHint;
                        }
                        return self.adapter.setVolume(self.volume);
                    })
                    .then(function () {
                        return self.adapter.play();
                    })
                    .then(function () {
                        self.playing = true;
                        self.errorMessage = '';
                    });
            },

            handleProviderError: function (err) {
                var msg = err && err.message ? err.message : 'CONTENT_UNAVAILABLE';
                if (msg === 'AUTOPLAY_BLOCKED'
                    || msg === 'CONTENT_UNAVAILABLE'
                    || msg === 'PROVIDER_OFFLINE'
                    || msg === 'POLICY_DISABLED'
                    || msg === 'UNSUPPORTED_CONTROL') {
                    this.errorMessage = msg;
                } else {
                    this.errorMessage = 'CONTENT_UNAVAILABLE';
                }
            },

            enablePlayback: function () {
                if (!this.adapter) return;
                var self = this;
                this.errorMessage = '';
                this.adapter.enable()
                    .then(function () {
                        self.enabled = true;
                        if (self._pendingCue) {
                            var cue = self._pendingCue;
                            self._pendingCue = null;
                            return self.loadCueAndPlay(cue);
                        }
                    })
                    .catch(function (err) {
                        self.handleProviderError(err);
                    });
            },

            togglePlayback: function () {
                if (!this.adapter) return;
                var self = this;
                if (this.playing) {
                    this.adapter.pause()
                        .then(function () { self.playing = false; })
                        .catch(function () {});
                } else {
                    this.adapter.resume()
                        .then(function () { self.playing = true; self.errorMessage = ''; })
                        .catch(function (err) {
                            if (err && err.message === 'AUTOPLAY_BLOCKED') {
                                self.errorMessage = 'AUTOPLAY_BLOCKED';
                            }
                        });
                }
            },

            skip: function () {
                if (!this.adapter) return;
                var self = this;
                this.adapter.skip()
                    .then(function () { self.errorMessage = ''; })
                    .catch(function (err) {
                        self.errorMessage = (err && err.message) || 'UNSUPPORTED_CONTROL';
                    });
            },

            setVolume: function () {
                if (!this.adapter) return;
                var self = this;
                this.adapter.setVolume(this.volume)
                    .catch(function () {});
            },

            toggleMute: function () {
                var self = this;
                var endpoint = this.muted ? 'unmute' : 'mute';
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/runtime/' + endpoint
                    + '?sessionId=' + encodeURIComponent(this.sessionId);
                window.dmRequest(url, { method: 'POST' })
                    .then(function () { self.muted = !self.muted; })
                    .catch(function (err) {
                        window.reportActionFailure('Could not toggle mute.', err, function () { self.toggleMute(); });
                    });
            },

            confirmCue: function () {
                var self = this;
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/runtime/confirm'
                    + '?sessionId=' + encodeURIComponent(this.sessionId);
                window.dmRequest(url, { method: 'POST' })
                    .then(function () {
                        self.hasPendingConfirmation = false;
                        self.fetchState();
                    })
                    .catch(function (err) {
                        window.reportActionFailure('Could not confirm cue.', err, function () { self.confirmCue(); });
                    });
            },

            declineCue: function () {
                var self = this;
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/runtime/decline'
                    + '?sessionId=' + encodeURIComponent(this.sessionId);
                window.dmRequest(url, { method: 'POST' })
                    .then(function () {
                        self.hasPendingConfirmation = false;
                        self.hasCue = false;
                    })
                    .catch(function (err) {
                        window.reportActionFailure('Could not decline cue.', err, function () { self.declineCue(); });
                    });
            },

            retry: function () {
                this.errorMessage = '';
                this.fetchState();
            },

            setOverride: function (cueId) {
                if (!cueId) return;
                var self = this;
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/runtime/override'
                    + '?sessionId=' + encodeURIComponent(this.sessionId)
                    + '&cueId=' + encodeURIComponent(cueId);
                window.dmRequest(url, { method: 'POST' })
                    .then(function () { self.fetchState(); })
                    .catch(function (err) {
                        window.reportActionFailure('Could not set override.', err, function () { self.setOverride(cueId); });
                    });
            },

            clearOverride: function () {
                var self = this;
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/runtime/override'
                    + '?sessionId=' + encodeURIComponent(this.sessionId);
                window.dmRequest(url, { method: 'DELETE' })
                    .then(function () { self.fetchState(); })
                    .catch(function (err) {
                        window.reportActionFailure('Could not clear override.', err, function () { self.clearOverride(); });
                    });
            },

            fetchCues: function () {
                var self = this;
                var url = '/api/v1/campaigns/' + this.campaignId + '/audio/cues';
                window.dmRequest(url)
                    .then(function (resp) { return resp.json(); })
                    .then(function (cues) { self.audioCues = cues; })
                    .catch(function () {});
            },

            destroy: function () {
                if (this.observer) {
                    this.observer.disconnect();
                }
                if (this.adapter) {
                    var self = this;
                    this.adapter.destroy()['catch'](function () {});
                }
            }
        };
    }

    window.audioCockpitWidget = audioCockpitWidget;
})();
