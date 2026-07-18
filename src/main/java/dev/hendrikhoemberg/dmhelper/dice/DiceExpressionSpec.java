package dev.hendrikhoemberg.dmhelper.dice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DiceExpressionSpec(
        String expression, int count, int sides, int modifier,
        boolean advantage, boolean disadvantage, Integer typedValue) {

    private static final Pattern DICE = Pattern.compile(
            "^(\\d*)d(\\d+)([-+]\\d+)?(\\s+(adv|dis))?$");
    private static final Pattern TYPED = Pattern.compile("^\\d+$");

    public static DiceExpressionSpec parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression);
        }
        String value = expression.trim();
        try {
            if (TYPED.matcher(value).matches()) {
                int typed = Integer.parseInt(value);
                return new DiceExpressionSpec("typed: " + typed, 0, 0, 0,
                        false, false, typed);
            }
            Matcher matcher = DICE.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid dice expression: " + expression);
            }
            int count = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
            if (count > 1000) {
                throw new IllegalArgumentException(
                        "Dice count exceeds maximum (1000): " + expression);
            }
            int sides = Integer.parseInt(matcher.group(2));
            if (sides == 0) {
                throw new IllegalArgumentException(
                        "Invalid dice expression: sides cannot be 0 in " + expression);
            }
            int modifier = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            boolean advantage = "adv".equals(matcher.group(5));
            boolean disadvantage = "dis".equals(matcher.group(5));
            return new DiceExpressionSpec(value, count, sides, modifier,
                    advantage, disadvantage, null);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression, error);
        }
    }

    public int min() {
        if (typedValue != null) return typedValue;
        return (advantage || disadvantage ? 1 : count) + modifier;
    }

    public int max() {
        if (typedValue != null) return typedValue;
        return (advantage || disadvantage ? sides : Math.multiplyExact(count, sides)) + modifier;
    }
}
