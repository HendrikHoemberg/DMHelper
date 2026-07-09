package dev.hendrikhoemberg.dmhelper.dice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiceEngineTest {

    private final DiceEngine engine = new DiceEngine();

    @Test
    void shouldRollSingleDie() {
        DiceResult result = engine.roll("d20");
        assertThat(result.expression()).isEqualTo("d20");
        assertThat(result.rolls()).hasSize(1);
        assertThat(result.rolls().get(0).die()).isEqualTo("d20");
        assertThat(result.rolls().get(0).values()).hasSize(1);
        assertThat(result.rolls().get(0).values().get(0)).isBetween(1, 20);
        assertThat(result.modifier()).isZero();
        assertThat(result.total()).isBetween(1, 20);
        assertThat(result.advantage()).isFalse();
        assertThat(result.disadvantage()).isFalse();
    }

    @Test
    void shouldRollMultipleDice() {
        DiceResult result = engine.roll("2d6");
        assertThat(result.rolls()).hasSize(1);
        assertThat(result.rolls().get(0).die()).isEqualTo("d6");
        assertThat(result.rolls().get(0).values()).hasSize(2);
        assertThat(result.rolls().get(0).values().get(0)).isBetween(1, 6);
        assertThat(result.rolls().get(0).values().get(1)).isBetween(1, 6);
        assertThat(result.modifier()).isZero();
        assertThat(result.total()).isBetween(2, 12);
    }

    @Test
    void shouldRollWithPositiveModifier() {
        DiceResult result = engine.roll("2d6+4");
        assertThat(result.rolls()).hasSize(1);
        assertThat(result.rolls().get(0).die()).isEqualTo("d6");
        assertThat(result.rolls().get(0).values()).hasSize(2);
        assertThat(result.modifier()).isEqualTo(4);
        assertThat(result.total()).isBetween(6, 16);
    }

    @Test
    void shouldRollWithNegativeModifier() {
        DiceResult result = engine.roll("1d4-1");
        assertThat(result.modifier()).isEqualTo(-1);
        assertThat(result.total()).isBetween(0, 3);
    }

    @Test
    void shouldRollWithAdvantage() {
        DiceResult result = engine.roll("d20 adv");
        assertThat(result.advantage()).isTrue();
        assertThat(result.disadvantage()).isFalse();
        assertThat(result.rolls().get(0).values()).hasSize(2);
        int v1 = result.rolls().get(0).values().get(0);
        int v2 = result.rolls().get(0).values().get(1);
        assertThat(result.total()).isEqualTo(Math.max(v1, v2));
    }

    @Test
    void shouldRollWithDisadvantage() {
        DiceResult result = engine.roll("d20 dis");
        assertThat(result.advantage()).isFalse();
        assertThat(result.disadvantage()).isTrue();
        assertThat(result.rolls().get(0).values()).hasSize(2);
        int v1 = result.rolls().get(0).values().get(0);
        int v2 = result.rolls().get(0).values().get(1);
        assertThat(result.total()).isEqualTo(Math.min(v1, v2));
    }

    @Test
    void shouldRollBareDieWithoutCount() {
        DiceResult result = engine.roll("d8");
        assertThat(result.rolls().get(0).die()).isEqualTo("d8");
        assertThat(result.rolls().get(0).values()).hasSize(1);
        assertThat(result.total()).isBetween(1, 8);
    }

    @Test
    void shouldRollLargeCount() {
        DiceResult result = engine.roll("5d10+3");
        assertThat(result.rolls().get(0).values()).hasSize(5);
        assertThat(result.total()).isBetween(8, 53);
    }

    @Test
    void shouldRollPercentileDie() {
        DiceResult result = engine.roll("d100");
        assertThat(result.rolls().get(0).die()).isEqualTo("d100");
        assertThat(result.total()).isBetween(1, 100);
    }

    @Test
    void shouldRejectEmptyExpression() {
        assertThatThrownBy(() -> engine.roll(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dice expression");
    }

    @Test
    void shouldRejectNullExpression() {
        assertThatThrownBy(() -> engine.roll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dice expression");
    }

    @Test
    void shouldRejectInvalidExpression() {
        assertThatThrownBy(() -> engine.roll("notdice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dice expression");
    }

    @Test
    void shouldRejectDoubleSignModifier() {
        assertThatThrownBy(() -> engine.roll("2d6+-3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid dice expression");
    }

    @Test
    void shouldAcceptTypedInteger() {
        DiceResult result = engine.roll("15");
        assertThat(result.expression()).isEqualTo("typed: 15");
        assertThat(result.rolls()).isEmpty();
        assertThat(result.modifier()).isZero();
        assertThat(result.total()).isEqualTo(15);
        assertThat(result.advantage()).isFalse();
        assertThat(result.disadvantage()).isFalse();
    }

    @Test
    void d0ThrowsValidationError() {
        assertThatThrownBy(() -> engine.roll("1d0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sides cannot be 0");
    }

    @Test
    void hugeCountThrowsValidationError() {
        assertThatThrownBy(() -> engine.roll("99999d6"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dice count exceeds maximum");
    }

    @Test
    void shouldAcceptTypedZero() {
        DiceResult result = engine.roll("0");
        assertThat(result.expression()).isEqualTo("typed: 0");
        assertThat(result.total()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"d20", "d6", "2d6", "3d8+2", "1d4-1", "d100"})
    void shouldProduceValidResults(String expression) {
        DiceResult result = engine.roll(expression);
        assertThat(result.expression()).isEqualTo(expression);
        assertThat(result.total()).isNotNegative();
        for (DiceResult.DieRoll roll : result.rolls()) {
            assertThat(roll.die()).startsWith("d");
            assertThat(roll.values()).isNotEmpty();
        }
    }
}
