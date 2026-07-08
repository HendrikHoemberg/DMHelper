package dev.hendrikhoemberg.dmhelper.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbc;

    public SchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void migrate() {
        addColumnIfNotExists("game_map", "movement_mode", "VARCHAR(16) NOT NULL DEFAULT 'GRID'");
        addColumnIfNotExists("game_map", "show_grid", "BOOLEAN NOT NULL DEFAULT TRUE");
        addColumnIfNotExists("token", "dead", "BOOLEAN NOT NULL DEFAULT FALSE");
    }

    private void addColumnIfNotExists(String table, String column, String definition) {
        try {
            int count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                    Integer.class, table.toUpperCase(), column.toUpperCase());
            if (count == 0) {
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                log.info("Added missing column {}.{} ({})", table, column, definition);
            }
        } catch (Exception e) {
            log.warn("Schema migration for {}.{} skipped: {}", table, column, e.getMessage());
        }
    }
}
