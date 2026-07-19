(function () {
    'use strict';

    var YT_ERROR_MAP = {
        2: 'CONTENT_UNAVAILABLE',
        5: 'CONTENT_UNAVAILABLE',
        100: 'CONTENT_UNAVAILABLE',
        101: 'CONTENT_UNAVAILABLE',
        150: 'CONTENT_UNAVAILABLE',
        153: 'PROVIDER_OFFLINE'
    };
    var ERROR_DEFAULT = 'UNSUPPORTED_CONTROL';
    var API_TIMEOUT_MS = 10000;
    var apiPromise = null;

    function mapErrorCode(code) {
        return YT_ERROR_MAP[code] || ERROR_DEFAULT;
    }

    function ensureApi() {
        if (window.YT && window.YT.Player) return Promise.resolve();
        if (apiPromise) return apiPromise;

        apiPromise = new Promise(function (resolve, reject) {
            var settled = false;
            var priorReady = window.onYouTubeIframeAPIReady;
            var timeout = window.setTimeout(function () {
                if (settled) return;
                settled = true;
                apiPromise = null;
                reject(new Error('PROVIDER_OFFLINE'));
            }, API_TIMEOUT_MS);

            window.onYouTubeIframeAPIReady = function () {
                if (typeof priorReady === 'function') priorReady();
                if (settled) return;
                settled = true;
                window.clearTimeout(timeout);
                resolve();
            };

            var tag = document.createElement('script');
            tag.src = 'https://www.youtube.com/iframe_api';
            tag.onerror = function () {
                if (settled) return;
                settled = true;
                window.clearTimeout(timeout);
                apiPromise = null;
                reject(new Error('PROVIDER_OFFLINE'));
            };
            document.head.appendChild(tag);
        });
        return apiPromise;
    }

    window.registerAudioProvider('YOUTUBE', function (opts) {
        var mount = opts.mount;
        var onState = opts.onState;
        var onError = opts.onError;
        var onAutoplayBlocked = opts.onAutoplayBlocked;
        var player = null;
        var enabled = false;
        var pendingVolume = null;
        var pendingAutoplayBlocked = false;

        function requirePlayer() {
            if (!enabled || !player) throw new Error('UNSUPPORTED_CONTROL');
        }

        return {
            enable: function () {
                if (enabled) return Promise.resolve({ enabled: true });
                return ensureApi().then(function () {
                    return new Promise(function (resolve, reject) {
                        var settled = false;
                        var readyTimeout = window.setTimeout(function () {
                            if (settled) return;
                            settled = true;
                            reject(new Error('PROVIDER_OFFLINE'));
                        }, API_TIMEOUT_MS);
                        try {
                            player = new window.YT.Player(mount, {
                                width: 480,
                                height: 270,
                                playerVars: {
                                    autoplay: 0,
                                    controls: 1,
                                    rel: 0,
                                    origin: window.location.origin
                                },
                                events: {
                                    onReady: function () {
                                        if (settled) return;
                                        settled = true;
                                        window.clearTimeout(readyTimeout);
                                        enabled = true;
                                        if (pendingVolume !== null) {
                                            player.setVolume(pendingVolume);
                                            pendingVolume = null;
                                        }
                                        resolve({ enabled: true });
                                    },
                                    onStateChange: function (event) {
                                        onState(event.data);
                                    },
                                    onError: function (event) {
                                        onError(mapErrorCode(event.data));
                                    },
                                    onAutoplayBlocked: function () {
                                        pendingAutoplayBlocked = true;
                                        onAutoplayBlocked();
                                    }
                                }
                            });
                        } catch (_) {
                            window.clearTimeout(readyTimeout);
                            reject(new Error('PROVIDER_OFFLINE'));
                        }
                    });
                });
            },

            load: function (ref) {
                try {
                    requirePlayer();
                    if (ref.kind === 'PLAYLIST') {
                        player.loadPlaylist({ list: ref.id, listType: 'playlist' });
                    } else {
                        player.loadVideoById(ref.id);
                    }
                    return Promise.resolve({ loaded: true });
                } catch (_) {
                    return Promise.reject(new Error('CONTENT_UNAVAILABLE'));
                }
            },

            play: function () {
                try {
                    requirePlayer();
                    player.playVideo();
                    return Promise.resolve({ playing: true });
                } catch (_) {
                    return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                }
            },

            pause: function () {
                try {
                    requirePlayer();
                    player.pauseVideo();
                    return Promise.resolve({ paused: true });
                } catch (_) {
                    return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                }
            },

            resume: function () {
                if (pendingAutoplayBlocked) {
                    pendingAutoplayBlocked = false;
                    onAutoplayBlocked();
                    return Promise.reject(new Error('AUTOPLAY_BLOCKED'));
                }
                return this.play().then(function () { return { resumed: true }; });
            },

            skip: function () {
                try {
                    requirePlayer();
                    player.nextVideo();
                    return Promise.resolve({ skipped: true });
                } catch (_) {
                    return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                }
            },

            setVolume: function (value) {
                if (!enabled || !player) {
                    pendingVolume = value;
                    return Promise.resolve({ volume: value });
                }
                try {
                    player.setVolume(value);
                    return Promise.resolve({ volume: value });
                } catch (_) {
                    return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                }
            },

            destroy: function () {
                if (player) {
                    try { player.destroy(); } catch (_) { /* best-effort provider cleanup */ }
                }
                player = null;
                enabled = false;
                return Promise.resolve({ destroyed: true });
            }
        };
    });
})();
