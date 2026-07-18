package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSectionRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SheetEngineStartupRefreshTest {

    @Mock private CharacterClassRepository classRepository;
    @Mock private RuleSectionRepository ruleSectionRepository;
    @Mock private FeatRepository featRepository;

    @Test
    void applicationReadyRefreshesCachesPopulatedAfterPostConstruct() {
        AtomicReference<List<CharacterClass>> classes = new AtomicReference<>(List.of());
        when(classRepository.findAllByOrderByNameAsc()).thenAnswer(ignored -> classes.get());
        when(classRepository.findBySourceKey("srd-2024_fighter"))
                .thenAnswer(ignored -> classes.get().stream().findFirst());
        when(ruleSectionRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        SheetEngine engine = new SheetEngine(classRepository, ruleSectionRepository, featRepository);
        engine.initialize();

        classes.set(List.of(fighterClass()));
        engine.onApplicationReady();

        var derived = engine.derive(levelOneFighterSheet());
        assertThat(derived.classAndLevel()).isEqualTo("Fighter 1");
        assertThat(derived.maxHp()).isEqualTo(10);
    }

    private CharacterClass fighterClass() {
        CharacterClass fighter = new CharacterClass();
        fighter.setSource(ContentSource.SRD);
        fighter.setSourceKey("srd-2024_fighter");
        fighter.setName("Fighter");
        fighter.setHitDie("d10");
        fighter.setSavingThrows("[]");
        fighter.setFeatures("[]");
        return fighter;
    }

    private CharacterSheet levelOneFighterSheet() {
        CharacterSheet sheet = new CharacterSheet();
        sheet.setAbilityScores("{\"str\":10,\"dex\":10,\"con\":10,\"int\":10,\"wis\":10,\"cha\":10}");
        sheet.setClassLevels("[{\"classSourceKey\":\"srd-2024_fighter\",\"level\":1,\"hitDieRolls\":[]}]");
        sheet.setProficiencies("{}");
        sheet.setFeatRefs("[]");
        sheet.setOverrides("{}");
        return sheet;
    }
}
