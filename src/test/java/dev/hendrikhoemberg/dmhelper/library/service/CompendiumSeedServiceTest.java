package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({SrdSeedService.class, SpellSeedService.class,
         ConditionSeedService.class, RuleSectionSeedService.class,
         EquipmentItemSeedService.class, MagicItemSeedService.class,
         CharacterClassSeedService.class, SpeciesSeedService.class,
         BackgroundSeedService.class, FeatSeedService.class})
class CompendiumSeedServiceTest {

    @Autowired private ConditionRepository conditionRepository;
    @Autowired private RuleSectionRepository ruleSectionRepository;
    @Autowired private EquipmentItemRepository equipmentItemRepository;
    @Autowired private MagicItemRepository magicItemRepository;
    @Autowired private CharacterClassRepository characterClassRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private BackgroundRepository backgroundRepository;
    @Autowired private FeatRepository featRepository;

    @Autowired private ConditionSeedService conditionSeedService;
    @Autowired private RuleSectionSeedService ruleSectionSeedService;
    @Autowired private EquipmentItemSeedService equipmentSeedService;
    @Autowired private MagicItemSeedService magicItemSeedService;
    @Autowired private CharacterClassSeedService classSeedService;
    @Autowired private SpeciesSeedService speciesSeedService;
    @Autowired private BackgroundSeedService backgroundSeedService;
    @Autowired private FeatSeedService featSeedService;

    @BeforeEach
    void seedAll() {
        conditionSeedService.seedIfEmpty();
        ruleSectionSeedService.seedIfEmpty();
        equipmentSeedService.seedIfEmpty();
        magicItemSeedService.seedIfEmpty();
        classSeedService.seedIfEmpty();
        speciesSeedService.seedIfEmpty();
        backgroundSeedService.seedIfEmpty();
        featSeedService.seedIfEmpty();
    }

    @Test
    void shouldSeedConditions() {
        assertThat(conditionRepository.count()).isGreaterThan(0);
        Condition grappled = conditionRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase("Grappled"))
                .findFirst().orElse(null);
        if (grappled != null) {
            assertThat(grappled.getDescription()).isNotBlank();
        }
    }

    @Test
    void shouldSeedRules() {
        assertThat(ruleSectionRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldSeedEquipment() {
        assertThat(equipmentItemRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldSeedMagicItems() {
        assertThat(magicItemRepository.count()).isGreaterThan(100);
    }

    @Test
    void shouldSeedClasses() {
        assertThat(characterClassRepository.count()).isGreaterThan(0);
        assertThat(characterClassRepository.findBySubclassOfIsNullOrderByNameAsc()).isNotEmpty();
    }

    @Test
    void shouldSeedSpecies() {
        assertThat(speciesRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldSeedBackgrounds() {
        assertThat(backgroundRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldSeedFeats() {
        assertThat(featRepository.count()).isGreaterThan(0);
    }

    @Test
    void allEntriesShouldHaveSourceKeys() {
        conditionRepository.findAll().forEach(c -> assertThat(c.getSourceKey()).isNotBlank());
        ruleSectionRepository.findAll().forEach(r -> assertThat(r.getSourceKey()).isNotBlank());
        equipmentItemRepository.findAll().forEach(e -> assertThat(e.getSourceKey()).isNotBlank());
        magicItemRepository.findAll().forEach(m -> assertThat(m.getSourceKey()).isNotBlank());
        characterClassRepository.findAll().forEach(c -> assertThat(c.getSourceKey()).isNotBlank());
        speciesRepository.findAll().forEach(s -> assertThat(s.getSourceKey()).isNotBlank());
        backgroundRepository.findAll().forEach(b -> assertThat(b.getSourceKey()).isNotBlank());
        featRepository.findAll().forEach(f -> assertThat(f.getSourceKey()).isNotBlank());
    }
}
