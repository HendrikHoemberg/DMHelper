package dev.hendrikhoemberg.dmhelper.dice.service;

import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLog;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollGroupCodec;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollOutcome;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RollHistoryService {

    static final int HISTORY_QUERY_LIMIT = 20;

    private final DiceRollRepository diceRollRepository;
    private final TableRollLogRepository tableRollLogRepository;
    private final TableRollGroupCodec codec;

    public RollHistoryService(DiceRollRepository diceRollRepository,
                              TableRollLogRepository tableRollLogRepository,
                              TableRollGroupCodec codec) {
        this.diceRollRepository = diceRollRepository;
        this.tableRollLogRepository = tableRollLogRepository;
        this.codec = codec;
    }

    public List<RollHistoryItem> recent(UUID campaignId, int limit) {
        List<RollHistoryItem> items = new ArrayList<>();
        Limit queryLimit = Limit.of(HISTORY_QUERY_LIMIT);

        List<DiceRoll> diceRolls = diceRollRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId, queryLimit);
        for (DiceRoll roll : diceRolls) {
            items.add(new RollHistoryItem(
                    roll.getId(), RollHistoryKind.DICE, roll.getCreatedAt(),
                    roll.getExpression(), roll.getTotal(), null, null, true));
        }

        List<TableRollLog> tableRolls = tableRollLogRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId, queryLimit);
        for (TableRollLog log : tableRolls) {
            List<RollHistoryItem.RollHistoryOutcome> outcomes = null;
            boolean available = true;
            try {
                List<TableRollOutcome> decoded = codec.decode(log.getResultJson(), log.getId());
                outcomes = decoded.stream()
                        .map(o -> new RollHistoryItem.RollHistoryOutcome(o.entryKey(), o.resultText()))
                        .toList();
            } catch (IllegalStateException e) {
                outcomes = null;
                available = false;
            }
            items.add(new RollHistoryItem(
                    log.getId(), RollHistoryKind.TABLE, log.getCreatedAt(),
                    null, 0, log.getTableNameSnapshot(), outcomes, available));
        }

        items.sort(Comparator.comparing(RollHistoryItem::createdAt).reversed()
                .thenComparing(RollHistoryItem::id));

        if (items.size() > limit) {
            items = items.subList(0, limit);
        }

        return items;
    }
}
