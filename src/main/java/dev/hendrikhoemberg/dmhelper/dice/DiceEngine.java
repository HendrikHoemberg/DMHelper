package dev.hendrikhoemberg.dmhelper.dice;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiceEngine {

    private static final Pattern DICE_PATTERN = Pattern.compile(
            "^(\\d*)d(\\d+)([-+]\\d+)?(\\s+(adv|dis))?$");
    private static final Pattern TYPED_PATTERN = Pattern.compile("^\\d+$");

    private final Random random;

    public DiceEngine() {
        this.random = new Random();
    }

    public DiceResult roll(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression);
        }

        String trimmed = expression.trim();

        if (TYPED_PATTERN.matcher(trimmed).matches()) {
            int value = Integer.parseInt(trimmed);
            return new DiceResult("typed: " + value, List.of(), 0, value, false, false);
        }

        Matcher matcher = DICE_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression);
        }

        String countStr = matcher.group(1);
        int count;
        try {
            count = (countStr == null || countStr.isEmpty()) ? 1 : Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression);
        }
        if (count > 1000) {
            throw new IllegalArgumentException("Dice count exceeds maximum (1000): " + expression);
        }
        String sidesStr = matcher.group(2);
        int sides = Integer.parseInt(sidesStr);
        if (sides == 0) {
            throw new IllegalArgumentException("Invalid dice expression: sides cannot be 0 in " + expression);
        }
        String modifierStr = matcher.group(3);
        int modifier = 0;
        if (modifierStr != null) {
            modifier = Integer.parseInt(modifierStr);
        }
        String advDisGroup = matcher.group(5);
        boolean advantage = "adv".equals(advDisGroup);
        boolean disadvantage = "dis".equals(advDisGroup);

        int rollsToMake = (advantage || disadvantage) ? 2 : count;
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < rollsToMake; i++) {
            values.add(random.nextInt(sides) + 1);
        }

        int total;
        if (advantage) {
            total = values.stream().mapToInt(Integer::intValue).max().orElse(0) + modifier;
        } else if (disadvantage) {
            total = values.stream().mapToInt(Integer::intValue).min().orElse(0) + modifier;
        } else {
            total = values.stream().mapToInt(Integer::intValue).sum() + modifier;
        }

        List<DiceResult.DieRoll> dieRolls = List.of(new DiceResult.DieRoll("d" + sides, List.copyOf(values)));
        return new DiceResult(trimmed, dieRolls, modifier, total, advantage, disadvantage);
    }
}
