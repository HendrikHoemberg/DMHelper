package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
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
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLog;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Service
@Transactional
public class RollableTableRollService {

    private static final int MAX_ATTEMPTS = 1000;
    private static final int MAX_DEPTH = 5;

    private final DiceEngine diceEngine;
    private final RollableTableRepository tableRepository;
    private final TableRollLogRepository logRepository;
    private final TableRollGroupCodec codec;
    private final CampaignRepository campaignRepository;

    public RollableTableRollService(DiceEngine diceEngine,
                                    RollableTableRepository tableRepository,
                                    TableRollLogRepository logRepository,
                                    TableRollGroupCodec codec,
                                    CampaignRepository campaignRepository) {
        this.diceEngine = diceEngine;
        this.tableRepository = tableRepository;
        this.logRepository = logRepository;
        this.codec = codec;
        this.campaignRepository = campaignRepository;
    }

    public TableRollGroup roll(UUID campaignId, UUID tableId, TableRollRequest request) {
        RollableTable table = tableRepository.findWithEntriesById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        if (!isVisibleToCampaign(table, campaignId)) {
            throw new IllegalArgumentException("Table is not visible to campaign: " + tableId);
        }

        if (request.rollCount() < 1) {
            throw new IllegalArgumentException("rollCount must be at least 1");
        }

        if (request.duplicatePolicy() == TableDuplicatePolicy.REROLL_DUPLICATES) {
            Predicate<RollableTableEntry> reachable = switch (table.getAddressMode()) {
                case RANGE -> e -> e.getRangeStart() != null && e.getRangeEnd() != null;
                case WEIGHTED -> e -> e.getWeight() != null && e.getWeight() > 0;
            };
            long uniqueCount = table.getEntries().stream()
                    .filter(reachable)
                    .count();
            if (request.rollCount() > uniqueCount) {
                throw new IllegalArgumentException(
                        "Cannot roll " + request.rollCount() + " unique outcomes from " + uniqueCount + " entries");
            }
        }

        List<TableRollOutcome> outcomes = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (int i = 0; i < request.rollCount(); i++) {
            TableRollOutcome outcome = rollOnce(table, request.manualValue(), 0, seenKeys, request.duplicatePolicy());
            outcomes.add(outcome);
        }

        TableConsequenceDraft draft = buildDraft(table.getCategory(), outcomes);
        TableDraftType draftType = draft != null ? toDraftType(table.getCategory()) : null;

        String resultJson = codec.encode(outcomes);

        TableRollLog log = new TableRollLog();
        log.setCampaign(campaignRepository.getReferenceById(campaignId));
        log.setTable(table);
        log.setTableKeySnapshot(table.getSourceKey());
        log.setTableNameSnapshot(table.getName());
        log.setResultJson(resultJson);
        log.setDraftType(draftType);
        log.setDraftStatus(TableDraftStatus.NONE);
        log.setCreatedAt(Instant.now());
        log = logRepository.save(log);

        return new TableRollGroup(
                log.getId(), campaignId, tableId,
                table.getSourceKey(), table.getName(),
                outcomes, draft, log.getCreatedAt());
    }

    private boolean isVisibleToCampaign(RollableTable table, UUID campaignId) {
        if (table.getCampaign() == null) return true;
        return table.getCampaign().getId().equals(campaignId);
    }

    private TableRollOutcome rollOnce(RollableTable table, Integer manualValue, int depth,
                                      Set<String> seenKeys, TableDuplicatePolicy policy) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            DiceResult rawRoll;
            if (manualValue != null && depth == 0) {
                rawRoll = diceEngine.roll(Integer.toString(manualValue));
            } else if (table.getRollExpression() != null) {
                rawRoll = diceEngine.roll(table.getRollExpression());
            } else {
                throw new IllegalStateException("Table has no roll expression: " + table.getId());
            }

            int total = rawRoll.total();
            RollableTableEntry entry = findEntry(table, total);
            if (entry == null) {
                throw new IllegalStateException("No entry matches roll total " + total + " in table " + table.getId());
            }

            if (policy == TableDuplicatePolicy.REROLL_DUPLICATES && seenKeys != null) {
                if (seenKeys.contains(entry.getEntryKey())) {
                    continue;
                }
                seenKeys.add(entry.getEntryKey());
            }

            DiceResult quantityRoll = null;
            if (entry.getQuantityExpression() != null && !entry.getQuantityExpression().isBlank()) {
                quantityRoll = diceEngine.roll(entry.getQuantityExpression());
            }

            List<TableResolvedReference> resolvedRefs = new ArrayList<>();
            List<TableRollOutcome> nestedRolls = new ArrayList<>();

            for (RollableTableEntryReference ref : entry.getReferences()) {
                CampaignContentType contentType = resolveContentType(ref.getTargetType());
                resolvedRefs.add(new TableResolvedReference(
                        contentType, ref.getTargetId(), ref.getDisplayText()));

                if ("ROLLABLE_TABLE".equals(ref.getTargetType()) && ref.getTargetId() != null) {
                    if (depth >= MAX_DEPTH) {
                        throw new IllegalStateException("Nested roll depth exceeds maximum of " + MAX_DEPTH);
                    }
                    RollableTable nestedTable = tableRepository.findWithEntriesById(ref.getTargetId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Referenced table not found: " + ref.getTargetId()));
                    TableRollOutcome nested = rollOnce(nestedTable, null, depth + 1, null, policy);
                    nestedRolls.add(nested);
                }
            }

            return new TableRollOutcome(
                    table.getSourceKey(), table.getName(), rawRoll,
                    entry.getEntryKey(), entry.getResultText(), quantityRoll,
                    resolvedRefs, nestedRolls);
        }

        throw new IllegalStateException("Failed to roll unique outcome after " + MAX_ATTEMPTS + " attempts");
    }

    private RollableTableEntry findEntry(RollableTable table, int total) {
        if (table.getAddressMode() == TableAddressMode.WEIGHTED) {
            int cumulative = 0;
            for (RollableTableEntry entry : table.getEntries()) {
                if (entry.getWeight() != null && entry.getWeight() > 0) {
                    cumulative += entry.getWeight();
                    if (total <= cumulative) {
                        return entry;
                    }
                }
            }
            return null;
        }

        return table.getEntries().stream()
                .filter(e -> e.getRangeStart() != null && e.getRangeEnd() != null
                        && total >= e.getRangeStart() && total <= e.getRangeEnd())
                .findFirst()
                .orElse(null);
    }

    private CampaignContentType resolveContentType(String targetType) {
        try {
            return CampaignContentType.valueOf(targetType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private TableConsequenceDraft buildDraft(TableCategory category, List<TableRollOutcome> outcomes) {
        String sourceText = outcomes.stream()
                .map(o -> o.resultText() != null ? o.resultText() : "")
                .filter(t -> !t.isEmpty())
                .reduce((a, b) -> a + ", " + b).orElse("");
        return switch (category) {
            case ENCOUNTER -> {
                List<EncounterCreatureDraft> creatures = outcomes.stream()
                        .flatMap(o -> o.references().stream())
                        .filter(ref -> ref.targetType() == CampaignContentType.STATBLOCK)
                        .map(ref -> new EncounterCreatureDraft(
                                ref.targetId(), ref.displayText(), 1))
                        .distinct()
                        .toList();
                yield new EncounterTableDraft(
                        outcomes.getFirst().resultText(),
                        sourceText,
                        creatures);
            }
            case TREASURE -> {
                List<RewardItemDraft> items = outcomes.stream()
                        .flatMap(o -> o.references().stream())
                        .filter(ref -> ref.targetType() == CampaignContentType.EQUIPMENT_ITEM
                                || ref.targetType() == CampaignContentType.MAGIC_ITEM)
                        .map(ref -> new RewardItemDraft(
                                ref.targetType(), ref.targetId(), ref.displayText(), 1))
                        .distinct()
                        .toList();
                yield new RewardTableDraft(sourceText, items);
            }
            default -> null;
        };
    }

    private TableDraftType toDraftType(TableCategory category) {
        return switch (category) {
            case ENCOUNTER -> TableDraftType.ENCOUNTER;
            case TREASURE -> TableDraftType.REWARD;
            default -> null;
        };
    }
}
