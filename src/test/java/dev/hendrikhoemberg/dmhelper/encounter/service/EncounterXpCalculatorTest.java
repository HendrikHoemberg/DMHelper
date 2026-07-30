package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EncounterXpCalculatorTest {

    @Test
    void sumsXpOfDefeatedMonstersOnly() {
        List<Combatant> combatants = List.of(
                defeatedMonster("Goblin", 50),
                defeatedMonster("Hobgoblin", 100),
                defeatedMonster("Bugbear", 200),
                defeatedMonster("Goblin Boss", 50)
        );
        assertThat(EncounterXpCalculator.xpFromDefeated(combatants)).isEqualTo(400);
    }

    @Test
    void excludesSurvivorsAndPCs() {
        Combatant defeatedMonster = defeatedMonster("Goblin", 50);
        Combatant survivor = monster("Survivor", 100);
        survivor.setDefeated(false);
        Combatant pc = pc("Hero");
        pc.setDefeated(true);
        List<Combatant> combatants = List.of(defeatedMonster, survivor, pc);
        assertThat(EncounterXpCalculator.xpFromDefeated(combatants)).isEqualTo(50);
    }

    @Test
    void combatantWithoutStatblockYieldsZero() {
        Combatant c = new Combatant();
        c.setName("Mystery");
        c.setKind("MONSTER");
        c.setDefeated(true);
        c.setStatBlock(null);
        assertThat(EncounterXpCalculator.xpFromDefeated(List.of(c))).isZero();
    }

    @Test
    void nullListYieldsZero() {
        assertThat(EncounterXpCalculator.xpFromDefeated(null)).isZero();
    }

    @Test
    void xpPerPcDividesEvenly() {
        assertThat(EncounterXpCalculator.xpPerPc(200, 4)).isEqualTo(50);
    }

    @Test
    void xpPerPcWithZeroPcReturnsTotal() {
        assertThat(EncounterXpCalculator.xpPerPc(200, 0)).isEqualTo(200);
    }

    @Test
    void xpPerPcWithNegativePcReturnsTotal() {
        assertThat(EncounterXpCalculator.xpPerPc(200, -1)).isEqualTo(200);
    }

    private static Combatant defeatedMonster(String name, int xp) {
        Combatant c = monster(name, xp);
        c.setDefeated(true);
        return c;
    }

    private static Combatant monster(String name, int xp) {
        Combatant c = new Combatant();
        c.setName(name);
        c.setKind("MONSTER");
        c.setDefeated(false);
        StatBlock sb = new StatBlock();
        sb.setXp(xp);
        c.setStatBlock(sb);
        return c;
    }

    private static Combatant pc(String name) {
        Combatant c = new Combatant();
        c.setName(name);
        c.setKind("PC");
        return c;
    }
}
