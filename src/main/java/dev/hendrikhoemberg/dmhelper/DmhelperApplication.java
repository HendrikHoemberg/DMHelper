package dev.hendrikhoemberg.dmhelper;

import dev.hendrikhoemberg.dmhelper.library.service.SpellSeedService;
import dev.hendrikhoemberg.dmhelper.library.service.SrdSeedService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class DmhelperApplication {

    private final SrdSeedService srdSeedService;
    private final SpellSeedService spellSeedService;

    public DmhelperApplication(SrdSeedService srdSeedService, SpellSeedService spellSeedService) {
        this.srdSeedService = srdSeedService;
        this.spellSeedService = spellSeedService;
    }

    public static void main(String[] args) {
        SpringApplication.run(DmhelperApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        srdSeedService.seedIfEmpty();
        spellSeedService.seedIfEmpty();
    }
}
