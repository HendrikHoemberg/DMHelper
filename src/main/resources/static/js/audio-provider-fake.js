(function () {
    'use strict';

    var providerId = document.documentElement.dataset.testAudioProvider;
    if (providerId !== 'FAKE') return;

    window.registerAudioProvider('FAKE', function (opts) {
        var mount = opts.mount;
        var onState = opts.onState;
        var onAutoplayBlocked = opts.onAutoplayBlocked;
        var commands = [];
        var failures = [];
        var enabled = false;
        var loaded = false;
        var playing = false;

        function record(command) {
            commands.push({ command: command, at: performance.now() });
            if (mount) mount.dataset.lastAudioCommand = command;
        }

        function failure(command) {
            var index = failures.findIndex(function (entry) {
                return typeof entry === 'string' || entry.command === command;
            });
            if (index < 0) return null;
            var entry = failures.splice(index, 1)[0];
            return typeof entry === 'string' ? entry : entry.category;
        }

        function rejectFailure(category) {
            if (category === 'AUTOPLAY_BLOCKED') onAutoplayBlocked();
            return Promise.reject(new Error(category));
        }

        var adapter = {
            commands: commands,
            injectFailure: function (category) { failures.push(category); },
            injectFailureOn: function (command, category) {
                failures.push({ command: command, category: category });
            },
            clearFailures: function () { failures = []; },
            snapshot: function () {
                return { enabled: enabled, loaded: loaded, playing: playing, commands: commands.slice() };
            },
            enable: function () {
                record('enable');
                var fail = failure('enable');
                if (fail) return rejectFailure(fail);
                enabled = true;
                if (mount) {
                    mount.textContent = 'Deterministic test player';
                    mount.dataset.fakeAudioPlayer = 'enabled';
                }
                return Promise.resolve({ enabled: true });
            },
            load: function (ref) {
                record('load:' + ref.kind + ':' + ref.id);
                if (!enabled) return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                var fail = failure('load');
                if (fail) return rejectFailure(fail);
                loaded = true;
                if (mount) mount.dataset.loadedAudioReference = ref.id;
                return Promise.resolve({ loaded: true });
            },
            play: function () {
                record('play');
                if (!loaded) return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                var fail = failure('play');
                if (fail) return rejectFailure(fail);
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
                var fail = failure('resume');
                if (fail) return rejectFailure(fail);
                if (!loaded) return Promise.reject(new Error('UNSUPPORTED_CONTROL'));
                playing = true;
                onState(1);
                return Promise.resolve({ resumed: true });
            },
            skip: function () {
                record('skip');
                var fail = failure('skip');
                return fail ? rejectFailure(fail) : Promise.resolve({ skipped: true });
            },
            setVolume: function (value) {
                record('setVolume:' + value);
                return Promise.resolve({ volume: value });
            },
            destroy: function () {
                record('destroy');
                enabled = false;
                loaded = false;
                playing = false;
                return Promise.resolve({ destroyed: true });
            }
        };
        window.__DMHELPER_AUDIO_FAKE__ = adapter;
        return adapter;
    });
})();
