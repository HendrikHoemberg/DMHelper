package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CombatDifficultyCalculatorTest {

    @Mock private StatBlockRepository statBlockRepo;
    @InjectMocks private CombatDifficultyCalculator calculator;

    private PartyMember pc(String classAndLevel) {
        PartyMember pm = new PartyMember();
        pm.setCharacterName("Hero");
        pm.setClassAndLevel(classAndLevel);
        pm.setMaxHp(10);
        pm.setAc(10);
        pm.setActive(true);
        return pm;
    }

    @Test
    void shouldReturnNaForEmptyParty() {
        var result = calculator.calculate(List.of(), List.of());
        assertThat(result.rating()).isEqualTo("N/A");
    }

    @Test
    void shouldReturnNaForEmptyMonsters() {
        var result = calculator.calculate(List.of(pc("Fighter 5")), List.of());
        assertThat(result.rating()).isEqualTo("N/A");
    }

    @Test
    void shouldCalculateDifficultyForPartyVsMonsters() {
        List<PartyMember> party = List.of(
                pc("Fighter 5"), pc("Wizard 5"), pc("Cleric 5"), pc("Rogue 5")
        );

        var monsters = List.of(
                new CombatantDto(UUID.randomUUID(), UUID.randomUUID(), "Goblin", 0, 0,
                        0, 0, 0, "MONSTER", null, false,
                        null, UUID.randomUUID(), null,
                        false, false, false, List.of(),
                        null, false, 0, 0, 0, 0, null, null, null, null, null),
                new CombatantDto(UUID.randomUUID(), UUID.randomUUID(), "Goblin", 0, 0,
                        0, 0, 0, "MONSTER", null, false,
                        null, UUID.randomUUID(), null,
                        false, false, false, List.of(),
                        null, false, 0, 0, 0, 0, null, null, null, null, null),
                new CombatantDto(UUID.randomUUID(), UUID.randomUUID(), "Bugbear", 0, 0,
                        0, 0, 0, "MONSTER", null, false,
                        null, UUID.randomUUID(), null,
                        false, false, false, List.of(),
                        null, false, 0, 0, 0, 0, null, null, null, null, null)
        );

        when(statBlockRepo.findById(monsters.get(0).statBlockId()))
                .thenReturn(Optional.of(statBlock("Goblin", "1/4", 50)));
        when(statBlockRepo.findById(monsters.get(1).statBlockId()))
                .thenReturn(Optional.of(statBlock("Goblin", "1/4", 50)));
        when(statBlockRepo.findById(monsters.get(2).statBlockId()))
                .thenReturn(Optional.of(statBlock("Bugbear", "3", 700)));

        var result = calculator.calculate(party, monsters);

        // Party threshold for 4 level-5 PCs using HARD array: 4 * 750 = 3000
        // Total XP: 50 + 50 + 700 = 800
        // 800 < 3000/2 = 1500 => LOW
        assertThat(result.rating()).isEqualTo("LOW");
        assertThat(result.adjustedXp()).isEqualTo(800);
    }

    @Test
    void shouldExtractLevelFromClassAndLevel() {
        List<PartyMember> party = List.of(pc("Rogue 5"));
        var monsters = List.of(
                new CombatantDto(UUID.randomUUID(), UUID.randomUUID(), "Rat", 0, 0,
                        0, 0, 0, "MONSTER", null, false,
                        null, UUID.randomUUID(), null,
                        false, false, false, List.of(),
                        null, false, 0, 0, 0, 0, null, null, null, null, null)
        );
        when(statBlockRepo.findById(monsters.get(0).statBlockId()))
                .thenReturn(Optional.of(statBlock("Rat", "0", 10)));

        var result = calculator.calculate(party, monsters);
        assertThat(result.rating()).isEqualTo("LOW");
    }

    @Test
    void shouldExtractLevelFromMultiClass() {
        List<PartyMember> party = List.of(pc("Fighter 3 / Wizard 2"));
        var monsters = List.of(
                new CombatantDto(UUID.randomUUID(), UUID.randomUUID(), "Rat", 0, 0,
                        0, 0, 0, "MONSTER", null, false,
                        null, UUID.randomUUID(), null,
                        false, false, false, List.of(),
                        null, false, 0, 0, 0, 0, null, null, null, null, null)
        );
        when(statBlockRepo.findById(monsters.get(0).statBlockId()))
                .thenReturn(Optional.of(statBlock("Rat", "0", 10)));

        var result = calculator.calculate(party, monsters);
        // Fighter 3 / Wizard 2 = total level 5 => threshold 750 (HARD[4])
        // 10 < 750/4 = 187 => LOW
        assertThat(result.rating()).isEqualTo("LOW");
    }

    @Test
    void labelsTheCurrentCalculationAsAnEstimateWithSourceAndAssumptions() {
        PartyMember hero = pc("Fighter 5");
        CombatantDto monster = new CombatantDto(
                UUID.randomUUID(), UUID.randomUUID(), "Ogre", 0, 0,
                0, 0, 0, "MONSTER", null, false,
                null, UUID.randomUUID(), null,
                false, false, false, List.of(),
                null, false, 0, 0, 0, 0, null, null, null, null, null);
        when(statBlockRepo.findById(monster.statBlockId()))
                .thenReturn(Optional.of(statBlock("Ogre", "2", 450)));

        var result = calculator.calculate(List.of(hero), List.of(monster));

        assertThat(result.estimate()).isTrue();
        assertThat(result.source()).isEqualTo("2014 DMG encounter XP thresholds");
        assertThat(result.assumptions()).containsExactly(
                "2014 Medium thresholds stand in for 2024 Moderate thresholds.",
                "High begins at twice the proxy Moderate threshold.",
                "Monster XP uses stored XP, then the CR table, then a 200 XP fallback.");
    }

    private dev.hendrikhoemberg.dmhelper.library.data.StatBlock statBlock(String name, String cr, int xp) {
        var sb = new dev.hendrikhoemberg.dmhelper.library.data.StatBlock();
        sb.setName(name);
        sb.setCr(cr);
        sb.setXp(xp);
        sb.setType("monstrosity");
        sb.setSource(dev.hendrikhoemberg.dmhelper.library.data.ContentSource.SRD);
        return sb;
    }
}
