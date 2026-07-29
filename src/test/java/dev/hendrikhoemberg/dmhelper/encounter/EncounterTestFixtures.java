package dev.hendrikhoemberg.dmhelper.encounter;

import java.util.UUID;

public final class EncounterTestFixtures {

    private EncounterTestFixtures() {}

    public record EncounterWithGoblin(UUID encounterId, UUID combatantId, UUID statblockId) {}

    public record EncounterWithQuickAdd(UUID encounterId, UUID combatantId) {}

    public static EncounterWithGoblin encounterWithGoblinFromLibrary() {
        return new EncounterWithGoblin(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    public static EncounterWithQuickAdd encounterWithQuickAddedNpc() {
        return new EncounterWithQuickAdd(UUID.randomUUID(), UUID.randomUUID());
    }
}
