package dev.hendrikhoemberg.dmhelper.audio.service;

import java.util.List;

public final class AudioCueValidationException extends IllegalArgumentException {

    private final List<AudioCueValidationProblem> problems;

    public AudioCueValidationException(List<AudioCueValidationProblem> problems) {
        super("Audio cue validation failed");
        this.problems = List.copyOf(problems);
    }

    public List<AudioCueValidationProblem> problems() {
        return problems;
    }
}
