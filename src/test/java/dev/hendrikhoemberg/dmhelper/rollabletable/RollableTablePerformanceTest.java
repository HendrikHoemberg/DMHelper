package dev.hendrikhoemberg.dmhelper.rollabletable;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntry;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReference;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableRollService;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableDuplicatePolicy;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollGroupCodec;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RollableTablePerformanceTest {

    @Mock private DiceEngine diceEngine;
    @Mock private RollableTableRepository tableRepository;
    @Mock private TableRollLogRepository logRepository;
    @Mock private TableRollGroupCodec codec;
    @Mock private CampaignRepository campaignRepository;

    private RollableTableRollService service;
    private UUID campaignId;
    private UUID depthFiveRootId;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        lenient().when(campaignRepository.getReferenceById(campaignId)).thenReturn(campaign);

        RollableTable level5 = table("level5");
        level5.setId(UUID.randomUUID());

        RollableTable level4 = table("level4");
        level4.setId(UUID.randomUUID());

        RollableTable level3 = table("level3");
        level3.setId(UUID.randomUUID());

        RollableTable level2 = table("level2");
        level2.setId(UUID.randomUUID());

        depthFiveRootId = UUID.randomUUID();
        RollableTable root = table("root");
        root.setId(depthFiveRootId);

        linkEntry(level5, null);
        linkEntry(level4, level5);
        linkEntry(level3, level4);
        linkEntry(level2, level3);
        linkEntry(root, level2);

        when(tableRepository.findWithEntriesById(level5.getId())).thenReturn(Optional.of(level5));
        when(tableRepository.findWithEntriesById(level4.getId())).thenReturn(Optional.of(level4));
        when(tableRepository.findWithEntriesById(level3.getId())).thenReturn(Optional.of(level3));
        when(tableRepository.findWithEntriesById(level2.getId())).thenReturn(Optional.of(level2));
        when(tableRepository.findWithEntriesById(depthFiveRootId)).thenReturn(Optional.of(root));

        var deterministicRoll = new DiceResult("1", List.of(new DiceResult.DieRoll("d1", List.of(1))), 0, 1, false, false);
        lenient().when(diceEngine.roll("1")).thenReturn(deterministicRoll);

        lenient().when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new RollableTableRollService(diceEngine, tableRepository, logRepository, codec, campaignRepository);
    }

    @Test
    void depthFiveNestedRollResolvesWithinLocalBudget() {
        warmUp();

        assertTimeout(Duration.ofMillis(100), () -> {
            var result = service.roll(campaignId, depthFiveRootId,
                    new TableRollRequest(null, 1, TableDuplicatePolicy.ALLOW_DUPLICATES));
            assertThat(result).isNotNull();
        });
    }

    private void warmUp() {
        service.roll(campaignId, depthFiveRootId,
                new TableRollRequest(null, 1, TableDuplicatePolicy.ALLOW_DUPLICATES));
    }

    private static RollableTable table(String name) {
        RollableTable t = new RollableTable();
        t.setName(name);
        t.setSourceKey("perf_" + name);
        t.setAddressMode(TableAddressMode.RANGE);
        t.setRollExpression("1");
        t.setCategory(TableCategory.GENERIC);
        return t;
    }

    private static void linkEntry(RollableTable parent, RollableTable child) {
        RollableTableEntry entry = new RollableTableEntry();
        entry.setEntryKey("entry-" + parent.getName());
        entry.setRangeStart(1);
        entry.setRangeEnd(1);
        entry.setResultText("Result: " + parent.getName());
        entry.setTable(parent);
        if (child != null) {
            RollableTableEntryReference ref = new RollableTableEntryReference();
            ref.setEntry(entry);
            ref.setTargetId(child.getId());
            ref.setTargetType("ROLLABLE_TABLE");
            ref.setDisplayText(child.getName());
            entry.getReferences().add(ref);
        }
        parent.getEntries().add(entry);
    }
}
