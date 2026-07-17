package dev.hendrikhoemberg.dmhelper.encounter.service;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncounterPrep(
        String tactics,
        String morale,
        String surrender,
        String environment,
        String sourceLocator,
        String scalingNotes,
        String sceneKey
) {
    public static EncounterPrep empty() {
        return new EncounterPrep(null, null, null, null, null, null, null);
    }
}
