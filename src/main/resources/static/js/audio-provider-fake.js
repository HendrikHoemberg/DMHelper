(function () {
    'use strict';

    var testAttr = document.documentElement.dataset.testAudioProvider;
    if (!testAttr) return;

    var providerId = testAttr;

    function createFakeAdapter(opts) {
        var mount = opts.mount;
        var onState = opts.onState;
        var onError = opts.onError;
        var onAutoplayBlocked = opts.onAutoplayBlocked;

        var commands = [];
        var enabled = false;
        var loaded = false;
        var playing = false;
        var volume = 50;
        var injectedFailures = [];

        function record(cmd) {
            commands.push(cmd);
            return cmd;
        }

        return {
            commands: commands,

            injectFailure: function (category) {
                injectedFailures.push(category);
            },

            clearFailures: function () {
                injectedFailures = [];
            },

            enable: function () {
                record('enable');
                if (injectedFailures.length) {
                    var fail = injectedFailures.shift();
                    return Promise.reject(new Error(fail));
                }
                enabled = true;
                return Promise.resolve({ enabled: true });
            },

            load: function (ref) {
                record('load:' + ref.kind + ':' + ref.id);
                if (!enabled) return Promise.reject(new Error('Not enabled'));
                if (injectedFailures.length) {
                    return Promise.reject(new Error(injectedFailures.shift()));
                }
                loaded = true;
                return Promise.resolve({ loaded: true });
            },

            play: function () {
                record('play');
                if (!loaded) return Promise.reject(new Error('Nothing loaded'));
                if (injectedFailures.length) {
                    var fail = injectedFailures.shift();
                    if (fail === 'AUTOPLAY_BLOCKED') {
                        onAutoplayBlocked();
                        return Promise.reject(new Error('AUTOPLAY_BLOCKED'));
                    }
                    return Promise.reject(new Error(fail));
                }
                playing = true;
                onState(1);
                return Promise.resolve({ playing: true });
            },

            pause: function () {
                record('pause');
                playing = false;
                onState(2);
                return Promise.resolve({ paused: true });
            },

            resume: function () {
                record('resume');
                if (injectedFailures.length) {
                    var fail = injectedFailures.shift();
                    if (fail === 'AUTOPLAY_BLOCKED') {
                        onAutoplayBlocked();
                        return Promise.reject(new Error('AUTOPLAY_BLOCKED'));
                    }
                    return Promise.reject(new Error(fail));
                }
                playing = true;
                onState(1);
                return Promise.resolve({ resumed: true });
            },

            skip: function () {
                record('skip');
                if (injectedFailures.length) {
                    return Promise.reject(new Error(injectedFailures.shift()));
                }
                return Promise.resolve({ skipped: true });
            },

            setVolume: function (v) {
                volume = v;
                record('setVolume:' + v);
                return Promise.resolve({ volume: volume });
            },

            destroy: function () {
                record('destroy');
                enabled = false;
                loaded = false;
                playing = false;
                return Promise.resolve({ destroyed: true });
            }
        };
    }

    window.registerAudioProvider(providerId, createFakeAdapter);
})();
