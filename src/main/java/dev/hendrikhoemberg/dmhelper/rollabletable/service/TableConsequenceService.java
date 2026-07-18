package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPrep;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableDraftStatus;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableDraftType;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLog;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository;
import dev.hendrikhoemberg.dmhelper.treasury.data.InventoryState;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TableConsequenceService {

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 50;

    private final TableRollLogRepository logRepository;
    private final TableRollGroupCodec codec;
    private final EncounterService encounterService;
    private final TreasuryService treasuryService;

    public TableConsequenceService(TableRollLogRepository logRepository,
                                   TableRollGroupCodec codec,
                                   EncounterService encounterService,
                                   TreasuryService treasuryService) {
        this.logRepository = logRepository;
        this.codec = codec;
        this.encounterService = encounterService;
        this.treasuryService = treasuryService;
    }

    public record ConfirmEncounterRequest(String name, UUID mapId, List<CreatureEdit> creatures) {
        public ConfirmEncounterRequest {
            if (creatures == null) creatures = List.of();
        }
    }

    public record CreatureEdit(UUID statBlockId, int quantity) {}

    public record ConfirmRewardRequest(List<ItemEdit> items) {
        public ConfirmRewardRequest {
            if (items == null) items = List.of();
        }
    }

    public record ItemEdit(UUID targetId, int quantity) {}

    @Transactional(readOnly = true)
    public TableConsequenceDraft preview(UUID rollId, UUID campaignId) {
        TableRollLog log = logRepository.findByIdAndCampaignId(rollId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Roll log not found: " + rollId + " for campaign " + campaignId));
        if (log.getDraftType() == null || log.getDraftStatus() != TableDraftStatus.PENDING) {
            throw new IllegalStateException("Roll log " + rollId + " is not in PENDING draft status");
        }
        return buildDraftFromLog(log);
    }

    public void confirmEncounter(UUID rollId, UUID campaignId, ConfirmEncounterRequest request) {
        TableRollLog log = logRepository.findForResolution(rollId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Roll log not found: " + rollId + " for campaign " + campaignId));
        requirePending(log);

        List<TableRollOutcome> outcomes = codec.decode(log.getResultJson(), rollId);
        Set<UUID> allowedStatblockIds = outcomes.stream()
                .flatMap(o -> o.references().stream())
                .filter(ref -> ref.targetType() == CampaignContentType.STATBLOCK)
                .map(TableResolvedReference::targetId)
                .collect(Collectors.toSet());

        String name = request.name() != null ? request.name() : "Rolled Encounter";
        UUID mapId = request.mapId();

        for (CreatureEdit edit : request.creatures()) {
            if (edit.quantity() < MIN_QUANTITY || edit.quantity() > MAX_QUANTITY) {
                throw new IllegalArgumentException("Creature quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY);
            }
            if (!allowedStatblockIds.contains(edit.statBlockId())) {
                throw new IllegalArgumentException("StatBlock reference " + edit.statBlockId() + " not present in rolled result");
            }
        }

        var enc = encounterService.create(campaignId, new EncounterService.CreateRequest(name, mapId));
        UUID encId = enc.id();

        for (CreatureEdit edit : request.creatures()) {
            encounterService.addFromLibrary(encId, new EncounterService.AddFromLibraryRequest(
                    edit.statBlockId(), edit.quantity(), null, null, null, null, null));
        }

        String sourceText = outcomes.stream()
                .map(o -> o.resultText() != null ? o.resultText() : "")
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining(", "));
        encounterService.updatePrep(encId, new EncounterPrep(null, null, null, "table-roll", sourceText, null, null));

        log.setDraftStatus(TableDraftStatus.CONFIRMED);
        logRepository.save(log);
    }

    public void confirmReward(UUID rollId, UUID campaignId, ConfirmRewardRequest request) {
        TableRollLog log = logRepository.findForResolution(rollId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Roll log not found: " + rollId + " for campaign " + campaignId));
        requirePending(log);

        List<TableRollOutcome> outcomes = codec.decode(log.getResultJson(), rollId);
        Map<UUID, CampaignContentType> itemTypes = outcomes.stream()
                .flatMap(o -> o.references().stream())
                .filter(ref -> ref.targetType() == CampaignContentType.EQUIPMENT_ITEM
                        || ref.targetType() == CampaignContentType.MAGIC_ITEM)
                .collect(Collectors.toMap(
                        TableResolvedReference::targetId,
                        TableResolvedReference::targetType,
                        (a, b) -> a));

        for (ItemEdit edit : request.items()) {
            if (edit.quantity() < MIN_QUANTITY || edit.quantity() > MAX_QUANTITY) {
                throw new IllegalArgumentException("Item quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY);
            }
            CampaignContentType type = itemTypes.get(edit.targetId());
            if (type == null) {
                throw new IllegalArgumentException("Item reference " + edit.targetId() + " not present in rolled result");
            }
            UUID magicItemId = type == CampaignContentType.MAGIC_ITEM ? edit.targetId() : null;
            UUID equipmentItemId = type == CampaignContentType.EQUIPMENT_ITEM ? edit.targetId() : null;
            treasuryService.create(new TreasuryService.CreateAssignmentRequest(
                    campaignId, null, magicItemId, equipmentItemId, null, edit.quantity(), false,
                    InventoryState.STASHED));
        }

        log.setDraftStatus(TableDraftStatus.CONFIRMED);
        logRepository.save(log);
    }

    public void discard(UUID rollId, UUID campaignId) {
        TableRollLog log = logRepository.findForResolution(rollId, campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Roll log not found: " + rollId + " for campaign " + campaignId));
        requirePending(log);
        log.setDraftStatus(TableDraftStatus.DISCARDED);
        logRepository.save(log);
    }

    private static void requirePending(TableRollLog log) {
        if (log.getDraftStatus() != TableDraftStatus.PENDING) {
            throw new IllegalStateException("Roll log " + log.getId() + " is " + log.getDraftStatus()
                    + "; expected PENDING");
        }
    }

    private TableConsequenceDraft buildDraftFromLog(TableRollLog log) {
        List<TableRollOutcome> outcomes = codec.decode(log.getResultJson(), log.getId());

        if (log.getDraftType() == TableDraftType.ENCOUNTER) {
            return buildEncounterDraft(outcomes);
        } else if (log.getDraftType() == TableDraftType.REWARD) {
            return buildRewardDraft(outcomes);
        }
        throw new IllegalStateException("Unsupported draft type: " + log.getDraftType());
    }

    private static EncounterTableDraft buildEncounterDraft(List<TableRollOutcome> outcomes) {
        String sourceText = outcomes.stream()
                .map(o -> o.resultText() != null ? o.resultText() : "")
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining(", "));
        String suggestedName = outcomes.isEmpty() ? sourceText : outcomes.getFirst().resultText();

        Map<UUID, EncounterCreatureDraft> aggregated = new LinkedHashMap<>();
        for (TableRollOutcome outcome : outcomes) {
            int qty = outcome.quantityRoll() != null ? Math.max(1, outcome.quantityRoll().total()) : 1;
            for (TableResolvedReference ref : outcome.references()) {
                if (ref.targetType() != CampaignContentType.STATBLOCK) continue;
                aggregated.merge(ref.targetId(),
                        new EncounterCreatureDraft(ref.targetId(), ref.displayText(), qty),
                        (existing, incoming) -> new EncounterCreatureDraft(
                                existing.statBlockId(), existing.displayName(),
                                existing.quantity() + qty));
            }
        }

        return new EncounterTableDraft(suggestedName, sourceText, List.copyOf(aggregated.values()));
    }

    private static RewardTableDraft buildRewardDraft(List<TableRollOutcome> outcomes) {
        String sourceText = outcomes.stream()
                .map(o -> o.resultText() != null ? o.resultText() : "")
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining(", "));

        Map<UUID, RewardItemDraft> aggregated = new LinkedHashMap<>();
        for (TableRollOutcome outcome : outcomes) {
            int qty = outcome.quantityRoll() != null ? Math.max(1, outcome.quantityRoll().total()) : 1;
            for (TableResolvedReference ref : outcome.references()) {
                if (ref.targetType() != CampaignContentType.EQUIPMENT_ITEM
                        && ref.targetType() != CampaignContentType.MAGIC_ITEM) continue;
                aggregated.merge(ref.targetId(),
                        new RewardItemDraft(ref.targetType(), ref.targetId(), ref.displayText(), qty),
                        (existing, incoming) -> new RewardItemDraft(
                                existing.type(), existing.targetId(), existing.displayName(),
                                existing.quantity() + qty));
            }
        }

        return new RewardTableDraft(sourceText, List.copyOf(aggregated.values()));
    }
}
