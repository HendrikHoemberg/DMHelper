package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableRollGroupCodecTest {

    private final TableRollGroupCodec codec = new TableRollGroupCodec();

    @Test
    void roundTripsOutcomes() {
        List<TableRollOutcome> original = List.of(
                new TableRollOutcome(
                        "forest-enc", "Forest Encounters",
                        new DiceResult("1d2", List.of(new DiceResult.DieRoll("d2", List.of(2))), 0, 2, false, false),
                        "second", "2 wolves",
                        new DiceResult("2d4", List.of(new DiceResult.DieRoll("d4", List.of(3, 3))), 0, 6, false, false),
                        List.of(new TableResolvedReference(CampaignContentType.STATBLOCK, UUID.randomUUID(), "Wolf")),
                        List.of(new TableRollOutcome(
                                "wolves", "Wolves",
                                new DiceResult("1d6", List.of(new DiceResult.DieRoll("d6", List.of(3))), 0, 3, false, false),
                                "wolves", "Wolves attack!", null, List.of(), List.of()))
                ),
                new TableRollOutcome(
                        "forest-enc", "Forest Encounters",
                        new DiceResult("1d2", List.of(new DiceResult.DieRoll("d2", List.of(1))), 0, 1, false, false),
                        "first", "A deer", null, List.of(), List.of())
        );

        String json = codec.encode(original);
        List<TableRollOutcome> decoded = codec.decode(json, UUID.randomUUID());

        assertThat(decoded).hasSize(2);
        assertThat(decoded.getFirst().entryKey()).isEqualTo("second");
        assertThat(decoded.getFirst().rawRoll().total()).isEqualTo(2);
        assertThat(decoded.getFirst().quantityRoll().total()).isEqualTo(6);
        assertThat(decoded.getFirst().nestedRolls()).hasSize(1);
        assertThat(decoded.getFirst().nestedRolls().getFirst().entryKey()).isEqualTo("wolves");
        assertThat(decoded.get(1).entryKey()).isEqualTo("first");
        assertThat(decoded.get(1).rawRoll().total()).isEqualTo(1);
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        String badJson = "{\"schemaVersion\":99,\"outcomes\":[]}";
        assertThatThrownBy(() -> codec.decode(badJson, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unreadable table roll log");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> codec.decode("not-json", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unreadable table roll log");
    }
}
