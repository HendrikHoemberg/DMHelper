package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TableRollGroup(
        UUID logId,
        UUID campaignId,
        UUID tableId,
        String tableKey,
        String tableName,
        List<TableRollOutcome> outcomes,
        TableConsequenceDraft draft,
        Instant createdAt) {
}
