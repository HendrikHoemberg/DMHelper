package dev.hendrikhoemberg.dmhelper.dice.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLog;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollGroupCodec;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollOutcome;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableResolvedReference;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RollHistoryServiceTest {

    @Mock private DiceRollRepository diceRollRepository;
    @Mock private TableRollLogRepository tableRollLogRepository;
    @Mock private TableRollGroupCodec codec;

    @InjectMocks private RollHistoryService service;

    @Test
    void returnsMergedHistoryNewestFirst() {
        UUID campaignId = UUID.randomUUID();

        DiceRoll dice = new DiceRoll();
        dice.setId(UUID.randomUUID());
        dice.setExpression("1d20");
        dice.setTotal(17);
        dice.setCreatedAt(Instant.parse("2026-07-18T12:00:00Z"));

        DiceRoll dice2 = new DiceRoll();
        dice2.setId(UUID.randomUUID());
        dice2.setExpression("1d8");
        dice2.setTotal(5);
        dice2.setCreatedAt(Instant.parse("2026-07-18T12:01:00Z"));

        TableRollLog tableLog = new TableRollLog();
        tableLog.setId(UUID.randomUUID());
        tableLog.setTableNameSnapshot("Forest Encounters");
        tableLog.setResultJson("{}");
        tableLog.setCreatedAt(Instant.parse("2026-07-18T12:02:00Z"));

        when(diceRollRepository.findByCampaignIdOrderByCreatedAtDesc(eq(campaignId), any(Limit.class)))
                .thenReturn(List.of(dice2, dice));
        when(tableRollLogRepository.findByCampaignIdOrderByCreatedAtDesc(eq(campaignId), any(Limit.class)))
                .thenReturn(List.of(tableLog));
        when(codec.decode("{}", tableLog.getId())).thenReturn(List.of(
                new TableRollOutcome("forest-enc", "Forest Encounters",
                        new DiceResult("1d20", List.of(), 0, 12, false, false),
                        "wolves", "2 wolves", null, List.of(), List.of())
        ));

        List<RollHistoryItem> history = service.recent(campaignId, 20);

        assertThat(history).hasSize(3);
        assertThat(history.get(0).kind()).isEqualTo(RollHistoryKind.TABLE);
        assertThat(history.get(0).tableName()).isEqualTo("Forest Encounters");
        assertThat(history.get(0).outcomes()).hasSize(1);
        assertThat(history.get(0).outcomes().getFirst().entryKey()).isEqualTo("wolves");
        assertThat(history.get(1).kind()).isEqualTo(RollHistoryKind.DICE);
        assertThat(history.get(1).expression()).isEqualTo("1d8");
        assertThat(history.get(1).total()).isEqualTo(5);
        assertThat(history.get(2).kind()).isEqualTo(RollHistoryKind.DICE);
        assertThat(history.get(2).expression()).isEqualTo("1d20");
        assertThat(history.get(2).total()).isEqualTo(17);
    }

    @Test
    void handlesCorruptedTableLog() {
        UUID campaignId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();

        TableRollLog corrupted = new TableRollLog();
        corrupted.setId(logId);
        corrupted.setTableNameSnapshot("Broken Table");
        corrupted.setResultJson("corrupted");
        corrupted.setCreatedAt(Instant.parse("2026-07-18T12:00:00Z"));

        when(diceRollRepository.findByCampaignIdOrderByCreatedAtDesc(eq(campaignId), any(Limit.class)))
                .thenReturn(List.of());
        when(tableRollLogRepository.findByCampaignIdOrderByCreatedAtDesc(eq(campaignId), any(Limit.class)))
                .thenReturn(List.of(corrupted));
        when(codec.decode("corrupted", logId))
                .thenThrow(new IllegalStateException("Unreadable table roll log: " + logId));

        List<RollHistoryItem> history = service.recent(campaignId, 20);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().kind()).isEqualTo(RollHistoryKind.TABLE);
        assertThat(history.getFirst().tableName()).isEqualTo("Broken Table");
        assertThat(history.getFirst().outcomes()).isNull();
    }
}
