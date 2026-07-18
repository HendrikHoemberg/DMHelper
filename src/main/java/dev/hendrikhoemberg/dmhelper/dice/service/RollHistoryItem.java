package dev.hendrikhoemberg.dmhelper.dice.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RollHistoryItem(
        UUID id,
        RollHistoryKind kind,
        Instant createdAt,
        String expression,
        int total,
        String tableName,
        List<RollHistoryOutcome> outcomes,
        boolean available) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RollHistoryOutcome(
            String entryKey,
            String resultText) {
    }
}
