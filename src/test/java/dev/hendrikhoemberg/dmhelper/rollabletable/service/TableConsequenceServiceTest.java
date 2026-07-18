package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableDraftStatus;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableDraftType;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLog;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableConsequenceServiceTest {

    @Mock private TableRollLogRepository logRepository;
    @Mock private TableRollGroupCodec codec;
    @Mock private EncounterService encounterService;
    @Mock private TreasuryService treasuryService;

    @InjectMocks private TableConsequenceService service;

    private final UUID campaignId = UUID.randomUUID();
    private final UUID rollId = UUID.randomUUID();

    private Campaign campaign() {
        var c = new Campaign();
        c.setId(campaignId);
        c.setName("Test");
        return c;
    }

    // ---- Step 1: Pure-draft tests ----

    @Test
    void encounterDraftPreviewAggregatesStatblockRefsWithQuantities() {
        UUID sbId = UUID.randomUUID();
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.PENDING);
        when(logRepository.findByIdAndCampaignId(rollId, campaignId)).thenReturn(Optional.of(log));
        when(codec.decode(log.getResultJson(), rollId)).thenReturn(outcomesWithStatblock(sbId, "Goblin", 4));

        TableConsequenceDraft draft = service.preview(rollId, campaignId);

        assertThat(draft).isInstanceOf(EncounterTableDraft.class);
        EncounterTableDraft enc = (EncounterTableDraft) draft;
        assertThat(enc.creatures()).hasSize(1);
        assertThat(enc.creatures().getFirst().statBlockId()).isEqualTo(sbId);
        assertThat(enc.creatures().getFirst().displayName()).isEqualTo("Goblin");
        assertThat(enc.creatures().getFirst().quantity()).isEqualTo(4);
    }

    @Test
    void treasureDraftPreviewReturnsItems() {
        UUID itemId = UUID.randomUUID();
        TableRollLog log = rollLog(TableDraftType.REWARD, TableDraftStatus.PENDING);
        when(logRepository.findByIdAndCampaignId(rollId, campaignId)).thenReturn(Optional.of(log));
        when(codec.decode(log.getResultJson(), rollId)).thenReturn(outcomesWithEquipment(itemId, "Longsword", 2));

        TableConsequenceDraft draft = service.preview(rollId, campaignId);

        assertThat(draft).isInstanceOf(RewardTableDraft.class);
        RewardTableDraft reward = (RewardTableDraft) draft;
        assertThat(reward.items()).hasSize(1);
        assertThat(reward.items().getFirst().targetId()).isEqualTo(itemId);
        assertThat(reward.items().getFirst().displayName()).isEqualTo("Longsword");
        assertThat(reward.items().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void previewOnlyIncludesStatblockAndEquipmentMagicItemRefs() {
        UUID sbId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        UUID handoutId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.PENDING);
        when(logRepository.findByIdAndCampaignId(rollId, campaignId)).thenReturn(Optional.of(log));
        when(codec.decode(log.getResultJson(), rollId)).thenReturn(outcomesWithMixedRefs(sbId, noteId, handoutId, encounterId));

        TableConsequenceDraft draft = service.preview(rollId, campaignId);

        assertThat(draft).isInstanceOf(EncounterTableDraft.class);
        EncounterTableDraft enc = (EncounterTableDraft) draft;
        assertThat(enc.creatures()).hasSize(1);
        assertThat(enc.creatures().getFirst().statBlockId()).isEqualTo(sbId);
    }

    @Test
    void previewThrowsNotFoundForMissingRoll() {
        when(logRepository.findByIdAndCampaignId(rollId, campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(rollId, campaignId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void previewThrowsForNonDraftRoll() {
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.NONE);
        when(logRepository.findByIdAndCampaignId(rollId, campaignId)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> service.preview(rollId, campaignId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    // ---- Step 2: State-transition tests ----

    @Test
    void confirmEncounterCreatesPlannedEncounterAndAddsCombatants() {
        UUID sbId = UUID.randomUUID();
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.PENDING);
        when(logRepository.findForResolution(rollId, campaignId)).thenReturn(Optional.of(log));
        when(codec.decode(log.getResultJson(), rollId)).thenReturn(outcomesWithStatblock(sbId, "Goblin", 4));

        UUID encId = UUID.randomUUID();
        when(encounterService.create(eq(campaignId), any()))
                .thenReturn(new EncounterService.EncounterDto(encId, campaignId, null, "4 goblins appear",
                        "PLANNED", 0, -1, 0, null, null, false, List.of(), List.of()));

        service.confirmEncounter(rollId, campaignId,
                new TableConsequenceService.ConfirmEncounterRequest("4 goblins appear", null,
                        List.of(new TableConsequenceService.CreatureEdit(sbId, 4))));

        verify(encounterService).create(eq(campaignId), any());
        verify(encounterService).addFromLibrary(eq(encId), any());
        verify(encounterService).updatePrep(eq(encId), any());
        assertThat(log.getDraftStatus()).isEqualTo(TableDraftStatus.CONFIRMED);
    }

    @Test
    void confirmRewardCreatesStashAssignments() {
        UUID itemId = UUID.randomUUID();
        TableRollLog log = rollLog(TableDraftType.REWARD, TableDraftStatus.PENDING);
        when(logRepository.findForResolution(rollId, campaignId)).thenReturn(Optional.of(log));
        when(codec.decode(log.getResultJson(), rollId)).thenReturn(outcomesWithEquipment(itemId, "Longsword", 2));

        service.confirmReward(rollId, campaignId,
                new TableConsequenceService.ConfirmRewardRequest(
                        List.of(new TableConsequenceService.ItemEdit(itemId, 2))));

        verify(treasuryService).create(argThat(req ->
                req.campaignId().equals(campaignId)
                        && req.partyMemberId() == null
                        && req.inventoryState() == dev.hendrikhoemberg.dmhelper.treasury.data.InventoryState.STASHED
        ));
        assertThat(log.getDraftStatus()).isEqualTo(TableDraftStatus.CONFIRMED);
    }

    @Test
    void discardSetsDiscardedStatusAndCreatesNothing() {
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.PENDING);
        when(logRepository.findForResolution(rollId, campaignId)).thenReturn(Optional.of(log));

        service.discard(rollId, campaignId);

        assertThat(log.getDraftStatus()).isEqualTo(TableDraftStatus.DISCARDED);
        verifyNoInteractions(encounterService, treasuryService);
    }

    @Test
    void confirmThrowsConflictWhenAlreadyConfirmed() {
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.CONFIRMED);
        when(logRepository.findForResolution(rollId, campaignId)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> service.confirmEncounter(rollId, campaignId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void confirmThrowsConflictWhenAlreadyDiscarded() {
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.DISCARDED);
        when(logRepository.findForResolution(rollId, campaignId)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> service.confirmEncounter(rollId, campaignId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISCARDED");
    }

    @Test
    void discardThrowsConflictWhenAlreadyConfirmedOrDiscarded() {
        for (var status : List.of(TableDraftStatus.CONFIRMED, TableDraftStatus.DISCARDED)) {
            TableRollLog log = rollLog(TableDraftType.ENCOUNTER, status);
            when(logRepository.findForResolution(rollId, campaignId)).thenReturn(Optional.of(log));

            assertThatThrownBy(() -> service.discard(rollId, campaignId))
                    .isInstanceOf(IllegalStateException.class);

            reset(logRepository);
        }
    }

    @Test
    void confirmEncounterThrows400ForInvalidQuantity() {
        UUID sbId = UUID.randomUUID();
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.PENDING);
        when(logRepository.findForResolution(rollId, campaignId)).thenReturn(Optional.of(log));
        when(codec.decode(log.getResultJson(), rollId)).thenReturn(outcomesWithStatblock(sbId, "Goblin", 4));

        assertThatThrownBy(() -> service.confirmEncounter(rollId, campaignId,
                new TableConsequenceService.ConfirmEncounterRequest("test", null,
                        List.of(new TableConsequenceService.CreatureEdit(sbId, 0)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");

        assertThatThrownBy(() -> service.confirmEncounter(rollId, campaignId,
                new TableConsequenceService.ConfirmEncounterRequest("test", null,
                        List.of(new TableConsequenceService.CreatureEdit(sbId, 51)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void confirmEncounterThrows400ForUnknownStatblockId() {
        UUID knownSbId = UUID.randomUUID();
        UUID unknownSbId = UUID.randomUUID();
        TableRollLog log = rollLog(TableDraftType.ENCOUNTER, TableDraftStatus.PENDING);
        when(logRepository.findForResolution(rollId, campaignId)).thenReturn(Optional.of(log));
        when(codec.decode(log.getResultJson(), rollId)).thenReturn(outcomesWithStatblock(knownSbId, "Goblin", 4));

        assertThatThrownBy(() -> service.confirmEncounter(rollId, campaignId,
                new TableConsequenceService.ConfirmEncounterRequest("test", null,
                        List.of(new TableConsequenceService.CreatureEdit(unknownSbId, 2)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference");
    }

    @Test
    void confirmThrowsNotFoundForCrossCampaign() {
        UUID otherCampaign = UUID.randomUUID();
        when(logRepository.findForResolution(rollId, otherCampaign)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmEncounter(rollId, otherCampaign, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ---- helpers ----

    private TableRollLog rollLog(TableDraftType type, TableDraftStatus status) {
        TableRollLog log = new TableRollLog();
        log.setId(rollId);
        log.setCampaign(campaign());
        log.setDraftType(type);
        log.setDraftStatus(status);
        log.setResultJson("{}");
        return log;
    }

    private List<TableRollOutcome> outcomesWithStatblock(UUID sbId, String name, int qty) {
        return List.of(new TableRollOutcome("test", "Test",
                new DiceResult("1d20", List.of(), 0, 10, false, false),
                "e1", "4 goblins appear",
                new DiceResult("1d4", List.of(), 0, qty, false, false),
                List.of(new TableResolvedReference(CampaignContentType.STATBLOCK, sbId, name)),
                List.of()));
    }

    private List<TableRollOutcome> outcomesWithEquipment(UUID itemId, String name, int qty) {
        return List.of(new TableRollOutcome("test", "Test",
                new DiceResult("1d20", List.of(), 0, 10, false, false),
                "e1", "treasure",
                new DiceResult("1d4", List.of(), 0, qty, false, false),
                List.of(new TableResolvedReference(CampaignContentType.EQUIPMENT_ITEM, itemId, name)),
                List.of()));
    }

    private List<TableRollOutcome> outcomesWithMixedRefs(UUID sbId, UUID noteId, UUID handoutId, UUID encId) {
        return List.of(new TableRollOutcome("test", "Test",
                new DiceResult("1d20", List.of(), 0, 10, false, false),
                "e1", "mixed",
                new DiceResult("1d4", List.of(), 0, 3, false, false),
                List.of(
                        new TableResolvedReference(CampaignContentType.STATBLOCK, sbId, "Orc"),
                        new TableResolvedReference(CampaignContentType.NOTE, noteId, "Note"),
                        new TableResolvedReference(CampaignContentType.HANDOUT, handoutId, "Handout"),
                        new TableResolvedReference(CampaignContentType.ENCOUNTER, encId, "Encounter")
                ),
                List.of()));
    }
}
