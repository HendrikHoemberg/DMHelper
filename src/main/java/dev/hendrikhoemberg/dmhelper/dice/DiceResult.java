package dev.hendrikhoemberg.dmhelper.dice;

import java.util.List;

public record DiceResult(
        String expression,
        List<DieRoll> rolls,
        int modifier,
        int total,
        boolean advantage,
        boolean disadvantage
) {
    public record DieRoll(String die, List<Integer> values) {}
}
