package dev.hendrikhoemberg.dmhelper.common.config;

import dev.hendrikhoemberg.dmhelper.library.service.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds SRD 5.2 reference data on first run. Runs after {@link SpellReseedMigration}
 * (order 1) so cleared stale data is reseeded in the same startup.
 */
@Component
public class DataSeeder {

    private final SrdSeedService srdSeedService;
    private final SpellSeedService spellSeedService;
    private final ConditionSeedService conditionSeedService;
    private final RuleSectionSeedService ruleSectionSeedService;
    private final EquipmentItemSeedService equipmentItemSeedService;
    private final MagicItemSeedService magicItemSeedService;
    private final CharacterClassSeedService characterClassSeedService;
    private final SpeciesSeedService speciesSeedService;
    private final BackgroundSeedService backgroundSeedService;
    private final FeatSeedService featSeedService;

    public DataSeeder(SrdSeedService srdSeedService,
                      SpellSeedService spellSeedService,
                      ConditionSeedService conditionSeedService,
                      RuleSectionSeedService ruleSectionSeedService,
                      EquipmentItemSeedService equipmentItemSeedService,
                      MagicItemSeedService magicItemSeedService,
                      CharacterClassSeedService characterClassSeedService,
                      SpeciesSeedService speciesSeedService,
                      BackgroundSeedService backgroundSeedService,
                      FeatSeedService featSeedService) {
        this.srdSeedService = srdSeedService;
        this.spellSeedService = spellSeedService;
        this.conditionSeedService = conditionSeedService;
        this.ruleSectionSeedService = ruleSectionSeedService;
        this.equipmentItemSeedService = equipmentItemSeedService;
        this.magicItemSeedService = magicItemSeedService;
        this.characterClassSeedService = characterClassSeedService;
        this.speciesSeedService = speciesSeedService;
        this.backgroundSeedService = backgroundSeedService;
        this.featSeedService = featSeedService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void seed() {
        srdSeedService.seedIfEmpty();
        spellSeedService.seedIfEmpty();
        conditionSeedService.seedIfEmpty();
        ruleSectionSeedService.seedIfEmpty();
        equipmentItemSeedService.seedIfEmpty();
        magicItemSeedService.seedIfEmpty();
        characterClassSeedService.seedIfEmpty();
        speciesSeedService.seedIfEmpty();
        backgroundSeedService.seedIfEmpty();
        featSeedService.seedIfEmpty();
    }
}
