package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import java.util.List;

public record EncounterTableDraft(
        String suggestedName,
        String sourceText,
        List<EncounterCreatureDraft> creatures) implements TableConsequenceDraft {
}
