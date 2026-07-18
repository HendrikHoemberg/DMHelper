package dev.hendrikhoemberg.dmhelper.dice;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class DiceEngine {

    private final Random random;

    public DiceEngine() {
        this.random = new Random();
    }

    public DiceResult roll(String expression) {
        DiceExpressionSpec spec = DiceExpressionSpec.parse(expression);

        if (spec.typedValue() != null) {
            return new DiceResult(spec.expression(), List.of(), 0, spec.typedValue(), false, false);
        }

        int rollsToMake = (spec.advantage() || spec.disadvantage()) ? 2 : spec.count();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < rollsToMake; i++) {
            values.add(random.nextInt(spec.sides()) + 1);
        }

        int total;
        if (spec.advantage()) {
            total = values.stream().mapToInt(Integer::intValue).max().orElse(0) + spec.modifier();
        } else if (spec.disadvantage()) {
            total = values.stream().mapToInt(Integer::intValue).min().orElse(0) + spec.modifier();
        } else {
            total = values.stream().mapToInt(Integer::intValue).sum() + spec.modifier();
        }

        List<DiceResult.DieRoll> dieRolls = List.of(new DiceResult.DieRoll("d" + spec.sides(), List.copyOf(values)));
        return new DiceResult(spec.expression(), dieRolls, spec.modifier(), total, spec.advantage(), spec.disadvantage());
    }
}
