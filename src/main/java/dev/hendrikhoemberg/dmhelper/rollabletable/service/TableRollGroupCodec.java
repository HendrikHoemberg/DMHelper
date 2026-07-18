package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TableRollGroupCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private record StoredTableRoll(int schemaVersion, List<TableRollOutcome> outcomes) {
    }

    public String encode(List<TableRollOutcome> outcomes) {
        try {
            return MAPPER.writeValueAsString(new StoredTableRoll(1, outcomes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode table roll outcomes", e);
        }
    }

    public List<TableRollOutcome> decode(String json, UUID logId) {
        try {
            StoredTableRoll stored = MAPPER.readValue(json, StoredTableRoll.class);
            if (stored.schemaVersion() != 1) {
                throw new IllegalStateException("Unreadable table roll log: " + logId);
            }
            return stored.outcomes();
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable table roll log: " + logId);
        }
    }
}
