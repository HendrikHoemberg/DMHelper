package dev.hendrikhoemberg.dmhelper.config;

import dev.hendrikhoemberg.dmhelper.quest.data.QuestStatus;
import dev.hendrikhoemberg.dmhelper.world.data.LocationKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumLabelUtilTest {

    private final EnumLabelUtil util = new EnumLabelUtil();

    @Test
    void titleCasesUnderscoreSeparatedConstants() {
        assertThat(util.label(QuestStatus.NOT_STARTED)).isEqualTo("Not Started");
        assertThat(util.label(QuestStatus.ON_HOLD)).isEqualTo("On Hold");
    }

    @Test
    void titleCasesSingleWordConstants() {
        assertThat(util.label(QuestStatus.ACTIVE)).isEqualTo("Active");
        assertThat(util.label(LocationKind.SITE)).isEqualTo("Site");
    }

    @Test
    void rendersNullAsEmptyStringSoTemplatesNeedNoGuard() {
        assertThat(util.label(null)).isEmpty();
    }

    @Test
    void passesThroughNonEnumValuesUnchanged() {
        assertThat(util.label("Second Wind")).isEqualTo("Second Wind");
    }

    @Test
    void handlesConstantsWithLeadingOrRepeatedUnderscores() {
        assertThat(util.labelOf("PLAYER_FACING")).isEqualTo("Player Facing");
        assertThat(util.labelOf("A__B")).isEqualTo("A B");
    }
}
