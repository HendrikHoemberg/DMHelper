(function () {
    'use strict';

    var registry = {};

    window.registerAudioProvider = function (id, factory) {
        if (registry[id]) {
            throw new Error('Audio provider "' + id + '" is already registered.');
        }
        registry[id] = factory;
    };

    window.AUDIO_PROVIDER_REGISTRY = registry;
})();
