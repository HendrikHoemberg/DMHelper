package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;

public record ResolvedAudioCue(
        AudioCue cue,
        AudioCueSource source
) {
    public boolean isSilence() {
        return cue == null;
    }
}
