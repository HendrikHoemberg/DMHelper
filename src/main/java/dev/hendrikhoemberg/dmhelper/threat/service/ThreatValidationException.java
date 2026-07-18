package dev.hendrikhoemberg.dmhelper.threat.service;

import java.util.List;

public final class ThreatValidationException extends IllegalArgumentException {

    private final List<ThreatValidationProblem> problems;

    public ThreatValidationException(List<ThreatValidationProblem> problems) {
        super("Threat validation failed");
        this.problems = List.copyOf(problems);
    }

    public List<ThreatValidationProblem> problems() {
        return problems;
    }
}
