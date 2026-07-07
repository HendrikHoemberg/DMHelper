package dev.hendrikhoemberg.dmhelper.common.config;

import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class SpellReseedMigration {

    private static final Logger log = LoggerFactory.getLogger(SpellReseedMigration.class);

    private final SpellRepository spellRepository;

    public SpellReseedMigration(SpellRepository spellRepository) {
        this.spellRepository = spellRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void reseedIfStale() {
        long total = spellRepository.count();
        if (total == 0) {
            return;
        }
        if (spellRepository.countWithSchool() == 0) {
            spellRepository.deleteAllInBatch();
            log.info("Cleared {} stale spells (no school populated) — will reseed", total);
        }
    }
}
