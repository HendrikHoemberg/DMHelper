package dev.hendrikhoemberg.dmhelper.dice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DiceExpressionSpecTest {

    @ParameterizedTest
    @CsvSource({"1d100,1,100", "2d6,2,12", "2d6+3,5,15", "1d20-2,-1,18", "1d20 adv,1,20", "1d20 dis,1,20", "7,7,7"})
    void parsesTheSameGrammarAndExposesInclusiveBounds(String expression, int min, int max) {
        DiceExpressionSpec spec = DiceExpressionSpec.parse(expression);
        assertThat(spec.min()).isEqualTo(min);
        assertThat(spec.max()).isEqualTo(max);
    }

    @Test
    void engineAndParserRejectTheSameInvalidExpression() {
        assertThatIllegalArgumentException().isThrownBy(() -> DiceExpressionSpec.parse("2d0"));
        assertThatIllegalArgumentException().isThrownBy(() -> new DiceEngine().roll("2d0"));
    }

    @Test
    void rejectsNullExpression() {
        assertThatIllegalArgumentException().isThrownBy(() -> DiceExpressionSpec.parse(null));
    }

    @Test
    void rejectsBlankExpression() {
        assertThatIllegalArgumentException().isThrownBy(() -> DiceExpressionSpec.parse("   "));
    }
}
