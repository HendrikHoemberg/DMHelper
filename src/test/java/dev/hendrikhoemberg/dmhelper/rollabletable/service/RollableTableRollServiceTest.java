package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntry;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReference;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableDraftStatus;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableDraftType;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLog;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RollableTableRollServiceTest {

    @Mock private DiceEngine diceEngine;
    @Mock private RollableTableRepository tableRepository;
    @Mock private TableRollLogRepository logRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private TableRollGroupCodec codec;

    @InjectMocks private RollableTableRollService service;

    @Captor private ArgumentCaptor<TableRollLog> logCaptor;

    private UUID campaignId;
    private Campaign campaign;
    private RollableTable table;
    private RollableTableEntry firstEntry;
    private RollableTableEntry secondEntry;
    private RollableTableEntry secondEntryRefTarget;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        campaign = new Campaign();
        campaign.setId(campaignId);

        table = new RollableTable();
        table.setId(UUID.randomUUID());
        table.setSourceKey("forest-enc");
        table.setName("Forest Encounters");
        table.setAddressMode(TableAddressMode.RANGE);
        table.setRollExpression("1d2");
        table.setCategory(TableCategory.ENCOUNTER);

        lenient().when(campaignRepository.getReferenceById(campaignId)).thenReturn(campaign);
        lenient().when(logRepository.save(any(TableRollLog.class))).thenAnswer(invocation -> {
            TableRollLog saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        firstEntry = new RollableTableEntry();
        firstEntry.setEntryKey("first");
        firstEntry.setRangeStart(1);
        firstEntry.setRangeEnd(1);
        firstEntry.setResultText("A deer");
        firstEntry.setTable(table);

        secondEntry = new RollableTableEntry();
        secondEntry.setEntryKey("second");
        secondEntry.setRangeStart(2);
        secondEntry.setRangeEnd(2);
        secondEntry.setResultText("2 wolves");
        secondEntry.setQuantityExpression("2d4");
        secondEntry.setTable(table);

        secondEntryRefTarget = new RollableTableEntry();
        secondEntryRefTarget.setEntryKey("wolves");
        secondEntryRefTarget.setRangeStart(1);
        secondEntryRefTarget.setRangeEnd(6);
        secondEntryRefTarget.setResultText("Wolves attack!");
    }

    @Test
    void rerollsDuplicateEntries() {
        UUID parentId = UUID.randomUUID();

        RollableTableEntryReference ref = new RollableTableEntryReference();
        ref.setTargetScope(TableReferenceScope.ENTITY);
        ref.setTargetType("ROLLABLE_TABLE");
        ref.setTargetId(parentId);
        ref.setDisplayText("Wolves");
        secondEntry.getReferences().add(ref);

        RollableTable nestedTable = new RollableTable();
        nestedTable.setId(parentId);
        nestedTable.setSourceKey("wolves");
        nestedTable.setName("Wolves");
        nestedTable.setAddressMode(TableAddressMode.RANGE);
        nestedTable.setRollExpression("1d6");
        nestedTable.setCategory(TableCategory.GENERIC);
        secondEntryRefTarget.setTable(nestedTable);
        nestedTable.setEntries(List.of(secondEntryRefTarget));

        table.setEntries(List.of(firstEntry, secondEntry));

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));
        when(tableRepository.findWithEntriesById(parentId)).thenReturn(Optional.of(nestedTable));
        when(diceEngine.roll("1d2")).thenReturn(die("1d2", 2), die("1d2", 1));
        when(diceEngine.roll("2d4")).thenReturn(die("2d4", 6));
        when(diceEngine.roll("1d6")).thenReturn(die("1d6", 3));

        TableRollGroup group = service.roll(campaignId, table.getId(),
                new TableRollRequest(null, 2, TableDuplicatePolicy.REROLL_DUPLICATES));

        assertThat(group.outcomes()).extracting(TableRollOutcome::entryKey)
                .containsExactly("second", "first");
        assertThat(group.outcomes().getFirst().quantityRoll().total()).isEqualTo(6);
        assertThat(group.outcomes().getFirst().nestedRolls()).hasSize(1);
        assertThat(group.outcomes().getFirst().nestedRolls().getFirst().entryKey()).isEqualTo("wolves");
    }

    @Test
    void manualRootValue() {
        table.setRollExpression(null);
        firstEntry.setRangeStart(6);
        firstEntry.setRangeEnd(10);
        firstEntry.setEntryKey("manual-hit");
        secondEntry.setRangeStart(1);
        secondEntry.setRangeEnd(5);
        secondEntry.setEntryKey("low");
        table.setEntries(List.of(firstEntry, secondEntry));

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));
        when(diceEngine.roll("7")).thenReturn(die("7", 7));

        TableRollGroup group = service.roll(campaignId, table.getId(),
                new TableRollRequest(7, 1, TableDuplicatePolicy.ALLOW_DUPLICATES));

        assertThat(group.outcomes()).hasSize(1);
        assertThat(group.outcomes().getFirst().entryKey()).isEqualTo("manual-hit");
        assertThat(group.outcomes().getFirst().rawRoll().total()).isEqualTo(7);
    }

    @Test
    void allowedDuplicates() {
        firstEntry.setRangeStart(1);
        firstEntry.setRangeEnd(1);
        table.setEntries(List.of(firstEntry));

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));
        when(diceEngine.roll("1d2")).thenReturn(die("1d2", 1), die("1d2", 1));

        TableRollGroup group = service.roll(campaignId, table.getId(),
                new TableRollRequest(null, 2, TableDuplicatePolicy.ALLOW_DUPLICATES));

        assertThat(group.outcomes()).hasSize(2);
        assertThat(group.outcomes()).allMatch(o -> o.entryKey().equals("first"));
    }

    @Test
    void impossibleUniqueCountThrows() {
        table.setEntries(List.of(firstEntry));

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> service.roll(campaignId, table.getId(),
                new TableRollRequest(null, 2, TableDuplicatePolicy.REROLL_DUPLICATES)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void missingRangeMatchThrows() {
        firstEntry.setRangeStart(10);
        firstEntry.setRangeEnd(20);
        table.setEntries(List.of(firstEntry));

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));
        when(diceEngine.roll("1d2")).thenReturn(die("1d2", 1));

        assertThatThrownBy(() -> service.roll(campaignId, table.getId(),
                new TableRollRequest(null, 1, TableDuplicatePolicy.ALLOW_DUPLICATES)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No entry matches");
    }

    @Test
    void depth5Succeeds() {
        table.setRollExpression("1d1");
        firstEntry.setRangeStart(1);
        firstEntry.setRangeEnd(1);
        firstEntry.setEntryKey("root");
        firstEntry.setQuantityExpression(null);
        table.setEntries(List.of(firstEntry));

        RollableTable prevTable = table;
        RollableTableEntry prevEntry = firstEntry;

        for (int i = 0; i < 5; i++) {
            RollableTable inner = new RollableTable();
            inner.setId(UUID.randomUUID());
            inner.setSourceKey("depth-" + i);
            inner.setName("Depth " + i);
            inner.setAddressMode(TableAddressMode.RANGE);
            inner.setRollExpression("1d1");
            inner.setCategory(TableCategory.GENERIC);

            RollableTableEntry innerEntry = new RollableTableEntry();
            innerEntry.setEntryKey("d" + i);
            innerEntry.setRangeStart(1);
            innerEntry.setRangeEnd(1);
            innerEntry.setResultText("Depth " + i);
            innerEntry.setTable(inner);
            inner.setEntries(List.of(innerEntry));

            RollableTableEntryReference nestedRef = new RollableTableEntryReference();
            nestedRef.setTargetScope(TableReferenceScope.ENTITY);
            nestedRef.setTargetType("ROLLABLE_TABLE");
            nestedRef.setTargetId(inner.getId());
            nestedRef.setDisplayText("Depth " + i);
            prevEntry.getReferences().add(nestedRef);

            when(tableRepository.findWithEntriesById(inner.getId())).thenReturn(Optional.of(inner));

            prevTable = inner;
            prevEntry = innerEntry;
        }

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));
        when(diceEngine.roll("1d1")).thenReturn(die("1d1", 1));

        TableRollGroup group = service.roll(campaignId, table.getId(),
                new TableRollRequest(null, 1, TableDuplicatePolicy.ALLOW_DUPLICATES));

        assertThat(group.outcomes()).hasSize(1);
        TableRollOutcome current = group.outcomes().getFirst();
        int depth = 0;
        while (!current.nestedRolls().isEmpty()) {
            current = current.nestedRolls().getFirst();
            depth++;
        }
        assertThat(depth).isEqualTo(5);
    }

    @Test
    void depth6Throws() {
        table.setRollExpression("1d1");
        firstEntry.setRangeStart(1);
        firstEntry.setRangeEnd(1);
        firstEntry.setEntryKey("root");
        firstEntry.setQuantityExpression(null);
        table.setEntries(List.of(firstEntry));

        RollableTable prevTable = table;
        RollableTableEntry prevEntry = firstEntry;

        for (int i = 0; i < 6; i++) {
            RollableTable inner = new RollableTable();
            inner.setId(UUID.randomUUID());
            inner.setSourceKey("depth-" + i);
            inner.setName("Depth " + i);
            inner.setAddressMode(TableAddressMode.RANGE);
            inner.setRollExpression("1d1");
            inner.setCategory(TableCategory.GENERIC);

            RollableTableEntry innerEntry = new RollableTableEntry();
            innerEntry.setEntryKey("d" + i);
            innerEntry.setRangeStart(1);
            innerEntry.setRangeEnd(1);
            innerEntry.setResultText("Depth " + i);
            innerEntry.setTable(inner);
            inner.setEntries(List.of(innerEntry));

            RollableTableEntryReference nestedRef = new RollableTableEntryReference();
            nestedRef.setTargetScope(TableReferenceScope.ENTITY);
            nestedRef.setTargetType("ROLLABLE_TABLE");
            nestedRef.setTargetId(inner.getId());
            nestedRef.setDisplayText("Depth " + i);
            prevEntry.getReferences().add(nestedRef);

            // Only stub tables that will actually be looked up (first 5 nested levels succeed at depth 0-4)
            if (i < 5) {
                when(tableRepository.findWithEntriesById(inner.getId())).thenReturn(Optional.of(inner));
            }

            prevTable = inner;
            prevEntry = innerEntry;
        }

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));
        when(diceEngine.roll("1d1")).thenReturn(die("1d1", 1));

        assertThatThrownBy(() -> service.roll(campaignId, table.getId(),
                new TableRollRequest(null, 1, TableDuplicatePolicy.ALLOW_DUPLICATES)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nested roll depth");
    }

    @Test
    void savesOneLogForAllOutcomes() {
        table.setCategory(TableCategory.ENCOUNTER);
        firstEntry.setRangeStart(1);
        firstEntry.setRangeEnd(2);
        firstEntry.setEntryKey("only");
        secondEntry.setRangeStart(3);
        secondEntry.setRangeEnd(6);
        secondEntry.setEntryKey("rare");
        table.setEntries(List.of(firstEntry, secondEntry));

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));
        when(diceEngine.roll("1d2")).thenReturn(die("1d2", 1), die("1d2", 2), die("1d2", 1));

        service.roll(campaignId, table.getId(),
                new TableRollRequest(null, 3, TableDuplicatePolicy.ALLOW_DUPLICATES));

        verify(logRepository).save(any(TableRollLog.class));
    }

    @Test
    void invisibleTableThrows() {
        dev.hendrikhoemberg.dmhelper.campaign.data.Campaign cmp = new dev.hendrikhoemberg.dmhelper.campaign.data.Campaign();
        cmp.setId(campaignId);
        table.setCampaign(cmp);

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));

        UUID otherCampaign = UUID.randomUUID();
        assertThatThrownBy(() -> service.roll(otherCampaign, table.getId(),
                new TableRollRequest(null, 1, TableDuplicatePolicy.ALLOW_DUPLICATES)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visible");
    }

    @Test
    void weightedModeSelectsByCumulativeWeight() {
        table.setAddressMode(TableAddressMode.WEIGHTED);
        table.setRollExpression("1d100");
        firstEntry.setWeight(60);
        firstEntry.setRangeStart(null);
        firstEntry.setRangeEnd(null);
        secondEntry.setWeight(40);
        secondEntry.setRangeStart(null);
        secondEntry.setRangeEnd(null);
        table.setEntries(List.of(firstEntry, secondEntry));

        when(tableRepository.findWithEntriesById(table.getId())).thenReturn(Optional.of(table));
        when(diceEngine.roll("1d100")).thenReturn(die("1d100", 45));

        TableRollGroup group = service.roll(campaignId, table.getId(),
                new TableRollRequest(null, 1, TableDuplicatePolicy.ALLOW_DUPLICATES));

        assertThat(group.outcomes()).hasSize(1);
        assertThat(group.outcomes().getFirst().entryKey()).isEqualTo("first");
    }

    private static DiceResult die(String expression, int total) {
        return new DiceResult(expression, List.of(), 0, total, false, false);
    }
}
