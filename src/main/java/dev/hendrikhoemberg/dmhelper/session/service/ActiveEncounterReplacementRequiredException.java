package dev.hendrikhoemberg.dmhelper.session.service;

import java.util.UUID;

public final class ActiveEncounterReplacementRequiredException extends RuntimeException {
    private final UUID activeEncounterId;
    private final String activeEncounterName;

    public ActiveEncounterReplacementRequiredException(UUID activeEncounterId, String activeEncounterName) {
        super("Choose whether to suspend or end the active encounter");
        this.activeEncounterId = activeEncounterId;
        this.activeEncounterName = activeEncounterName;
    }

    public UUID getActiveEncounterId() { return activeEncounterId; }
    public String getActiveEncounterName() { return activeEncounterName; }
}
