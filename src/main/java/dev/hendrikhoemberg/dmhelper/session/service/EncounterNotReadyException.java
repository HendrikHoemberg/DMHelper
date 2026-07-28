package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService.EncounterReadinessDto;

public final class EncounterNotReadyException extends RuntimeException {
    private final EncounterReadinessDto readiness;

    public EncounterNotReadyException(EncounterReadinessDto readiness) {
        super("Encounter has blocking readiness issues");
        this.readiness = readiness;
    }

    public EncounterReadinessDto getReadiness() { return readiness; }
}
