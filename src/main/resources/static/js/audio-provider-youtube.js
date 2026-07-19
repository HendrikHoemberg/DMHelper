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

    function mapErrorCode(code) {
        return YT_ERROR_MAP[code] || ERROR_DEFAULT;
    }

    var apiLoading = false;
    var apiLoaded = false;
    var loadQueue = [];

    function onYouTubeIframeAPIReady() {
        apiLoaded = true;
        for (var i = 0; i < loadQueue.length; i++) {
            loadQueue[i]();
        }
        loadQueue = [];
    }

    window.onYouTubeIframeAPIReady = onYouTubeIframeAPIReady;

    function ensureApi(callback) {
        if (apiLoaded) {
            callback();
            return;
        }
        loadQueue.push(callback);
        if (apiLoading) return;
        apiLoading = true;

        var tag = document.createElement('script');
        tag.src = 'https://www.youtube.com/iframe_api';
        tag.setAttribute('origin', window.location.origin);
        document.head.appendChild(tag);
    }

    window.registerAudioProvider('YOUTUBE', function (opts) {
        var mount = opts.mount;
        var onState = opts.onState;
        var onError = opts.onError;
        var onAutoplayBlocked = opts.onAutoplayBlocked;

        var player = null;
        var enabled = false;
        var currentRef = null;
        var pendingVolume = null;
        var pendingAutoplayBlocked = false;

        return {
            enable: function () {
                if (enabled) return Promise.resolve({ enabled: true });
                return new Promise(function (resolve, reject) {
                    ensureApi(function () {
                        try {
                            player = new YT.Player(mount, {
                                width: 480,
                                height: 270,
                                playerVars: {
                                    autoplay: 0,
                                    controls: 1,
                                    modestbranding: 1,
                                    rel: 0,
                                    origin: window.location.origin
                                },
                                events: {
                                    onReady: function () {
                                        enabled = true;
                                        if (pendingVolume !== null) {
                                            player.setVolume(pendingVolume);
                                            pendingVolume = null;
                                        }
                                        resolve({ enabled: true });
                                    },
                                    onStateChange: function (e) {
                                        onState(e.data);
                                    },
                                    onError: function (e) {
                                        var category = mapErrorCode(e.data);
                                        if (category === 'CONTENT_UNAVAILABLE') {
                                            onError(category);
                                        } else {
                                            onError(category);
                                        }
                                    },
                                    onAutoplayBlocked: function () {
                                        pendingAutoplayBlocked = true;
                                        onAutoplayBlocked();
                                    }
                                }
                            });
                        } catch (err) {
                            reject(new Error('PROVIDER_OFFLINE'));
                        }
                    });
                });
            },

            load: function (ref) {
                if (!enabled || !player) {
                    return Promise.reject(new Error('Not enabled'));
                }
                currentRef = ref;
                try {
                    if (ref.kind === 'PLAYLIST') {
                        player.loadPlaylist({ list: ref.id, listType: 'playlist' });
                    } else {
                        player.loadVideoById(ref.id);
                    }
                    return Promise.resolve({ loaded: true });
                } catch (err) {
                    return Promise.reject(new Error('CONTENT_UNAVAILABLE'));
                }
            },

            play: function () {
                if (!player) return Promise.reject(new Error('Not enabled'));
                try {
                    player.playVideo();
                    return Promise.resolve({ playing: true });
                } catch (err) {
                    return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                }
            },

            pause: function () {
                if (!player) return Promise.reject(new Error('Not enabled'));
                try {
                    player.pauseVideo();
                    return Promise.resolve({ paused: true });
                } catch (err) {
                    return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                }
            },

            resume: function () {
                if (!player) return Promise.reject(new Error('Not enabled'));
                try {
                    player.playVideo();
                    return Promise.resolve({ resumed: true });
                } catch (err) {
                    if (pendingAutoplayBlocked) {
                        pendingAutoplayBlocked = false;
                        onAutoplayBlocked();
                        return Promise.reject(new Error('AUTOPLAY_BLOCKED'));
                    }
                    return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                }
            },

            skip: function () {
                if (!player) return Promise.reject(new Error('Not enabled'));
                try {
                    player.nextVideo();
                    return Promise.resolve({ skipped: true });
                } catch (err) {
                    return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                }
            },

            setVolume: function (v) {
                if (player && enabled) {
                    try {
                        player.setVolume(v);
                    } catch (err) {
                        return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                    }
                } else {
                    pendingVolume = v;
                }
                return Promise.resolve({ volume: v });
            },

            destroy: function () {
                if (player) {
                    try {
                        player.destroy();
                    } catch (err) {
                    }
                    player = null;
                }
                enabled = false;
                currentRef = null;
                return Promise.resolve({ destroyed: true });
            }
        };
    });
})();
