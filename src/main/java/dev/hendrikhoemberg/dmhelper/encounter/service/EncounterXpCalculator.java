package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import java.util.List;

public final class EncounterXpCalculator {
    private EncounterXpCalculator() {}

    public static int xpFromDefeated(List<Combatant> combatants) {
        if (combatants == null) return 0;
        return combatants.stream()
                .filter(Combatant::isDefeated)
                .filter(c -> !"PC".equals(c.getKind()))
                .filter(c -> c.getStatBlock() != null)
                .mapToInt(c -> Math.max(0, c.getStatBlock().getXp()))
                .sum();
    }

    public static int xpPerPc(int total, int pcCount) {
        return pcCount <= 0 ? total : total / pcCount;
    }
}
