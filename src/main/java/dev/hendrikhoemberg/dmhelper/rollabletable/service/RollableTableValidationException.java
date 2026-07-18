package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import java.util.List;

public final class RollableTableValidationException extends IllegalArgumentException {
    private final List<TableValidationProblem> problems;

    public RollableTableValidationException(List<TableValidationProblem> problems) {
        super("Rollable table validation failed");
        this.problems = List.copyOf(problems);
    }

    public List<TableValidationProblem> problems() { return problems; }
}
