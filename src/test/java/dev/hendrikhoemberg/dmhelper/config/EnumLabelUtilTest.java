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

    /**
     * Several DTOs flatten an enum to a String before it reaches a template
     * (EncounterService.WaveDto.status is w.getStatus().name(); SheetResourceDto.resetRule and
     * LedgerEntryDto.kind do the same). label() cannot distinguish those from free-form text,
     * so it passes them through untouched -- wrapping such a field in #enums.label() looks like
     * a conversion but changes nothing. #enums.labelOf() is the one to use there.
     */
    @Test
    void labelDoesNotConvertEnumNamesThatArriveAsStrings() {
        assertThat(util.label("ACTIVE"))
                .as("label() is a no-op on a String; this is the trap labelOf() exists for")
                .isEqualTo("ACTIVE");
        assertThat(util.labelOf("ACTIVE")).isEqualTo("Active");
        assertThat(util.labelOf("LONG_REST")).isEqualTo("Long Rest");
        assertThat(util.labelOf("HP_THRESHOLD")).isEqualTo("Hp Threshold");
    }

    @Test
    void labelOfRendersNullAndBlankAsEmptyString() {
        assertThat(util.labelOf(null)).isEmpty();
        assertThat(util.labelOf("   ")).isEmpty();
    }
}
