package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorContrastTest {

    @Test
    void blackOnWhiteIsTwentyOneToOne() {
        assertThat(ColorContrast.ratio("#000000", "#ffffff")).isCloseTo(21.0, within(0.01));
    }

    @Test
    void theRatioIsSymmetric() {
        assertThat(ColorContrast.ratio("#eee8dc", "#101113"))
                .isCloseTo(ColorContrast.ratio("#101113", "#eee8dc"), within(0.0001));
    }

    @Test
    void shortHexExpands() {
        assertThat(ColorContrast.ratio("#fff", "#000"))
                .isCloseTo(ColorContrast.ratio("#ffffff", "#000000"), within(0.0001));
    }

    @Test
    void nonHexInputIsRejectedLoudly() {
        assertThatThrownBy(() -> ColorContrast.ratio("var(--text-primary)", "#101113"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hex");
    }

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
