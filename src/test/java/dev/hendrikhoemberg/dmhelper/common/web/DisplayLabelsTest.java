package dev.hendrikhoemberg.dmhelper.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisplayLabelsTest {

    @Test
    void humanizesScreamingSnakeCase() {
        assertThat(DisplayLabels.humanize("NOT_STARTED")).isEqualTo("Not started");
        assertThat(DisplayLabels.humanize("READ_ALOUD")).isEqualTo("Read aloud");
        assertThat(DisplayLabels.humanize((String) null)).isEqualTo("\u2014");
    }

    @Test
    void humanizesEnum() {
        assertThat(DisplayLabels.humanizeEnum(DummyEnum.VALUE_NAME)).isEqualTo("Value name");
    }

    @Test
    void emptyStringReturnsDash() {
        assertThat(DisplayLabels.humanize("")).isEqualTo("\u2014");
        assertThat(DisplayLabels.humanize("   ")).isEqualTo("\u2014");
    }

    enum DummyEnum { VALUE_NAME }
}
