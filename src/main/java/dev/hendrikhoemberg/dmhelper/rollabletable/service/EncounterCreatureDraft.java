package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import java.util.UUID;

public record EncounterCreatureDraft(
        UUID statBlockId,
        String displayName,
        int quantity) {
}
