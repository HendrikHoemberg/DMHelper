package dev.hendrikhoemberg.dmhelper.encounter.service;

public final class InitiativeSetupIncompleteException extends RuntimeException {
    private final int unsetCount;

    public InitiativeSetupIncompleteException(String message, int unsetCount) {
        super(message);
        this.unsetCount = unsetCount;
    }

    public int unsetCount() {
        return unsetCount;
    }
}
