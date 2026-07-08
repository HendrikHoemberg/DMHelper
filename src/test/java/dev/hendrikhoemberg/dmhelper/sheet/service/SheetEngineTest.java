package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSection;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSectionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetEngine.DerivedValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SheetEngineTest {

    @Mock
    private CharacterClassRepository classRepo;

    @Mock
    private RuleSectionRepository ruleSectionRepo;

    private SheetEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        var fighterClass = new CharacterClass();
        fighterClass.setSourceKey("srd-2024_fighter");
        fighterClass.setName("Fighter");
        fighterClass.setHitDie("d10");
        fighterClass.setSavingThrows("[\"str\", \"con\"]");
        fighterClass.setFeatures("""
                [
                    {"feature_type": "PROFICIENCY_BONUS", "data_for_class_table": [
                        {"level": 1, "column_value": "+2"},
                        {"level": 2, "column_value": "+2"},
                        {"level": 3, "column_value": "+2"},
                        {"level": 4, "column_value": "+2"},
                        {"level": 5, "column_value": "+3"},
                        {"level": 6, "column_value": "+3"},
                        {"level": 7, "column_value": "+3"},
                        {"level": 8, "column_value": "+3"},
                        {"level": 9, "column_value": "+4"},
                        {"level": 10, "column_value": "+4"},
                        {"level": 11, "column_value": "+4"},
                        {"level": 12, "column_value": "+4"},
                        {"level": 13, "column_value": "+5"},
                        {"level": 14, "column_value": "+5"},
                        {"level": 15, "column_value": "+5"},
                        {"level": 16, "column_value": "+5"},
                        {"level": 17, "column_value": "+6"},
                        {"level": 18, "column_value": "+6"},
                        {"level": 19, "column_value": "+6"},
                        {"level": 20, "column_value": "+6"}
                    ]},
                    {"feature_type": "CORE_TRAITS_TABLE", "description": "|Hit Die||d10 per fighter level|\\n|Primary Ability|Strength|"}
                ]
                """);

        var wizardClass = new CharacterClass();
        wizardClass.setSourceKey("srd-2024_wizard");
        wizardClass.setName("Wizard");
        wizardClass.setHitDie("d6");
        wizardClass.setSavingThrows("[\"int\", \"wis\"]");
        wizardClass.setFeatures("""
                [
                    {"feature_type": "CORE_TRAITS_TABLE", "description": "|Hit Die||d6 per wizard level|\\n|Primary Ability|Intelligence|"},
                    {"feature_type": "SPELL_SLOTS", "key": "srd-2024_wizard_slots-1st", "data_for_class_table": [
                        {"level": 1, "column_value": "2"},
                        {"level": 2, "column_value": "3"},
                        {"level": 3, "column_value": "4"},
                        {"level": 4, "column_value": "4"},
                        {"level": 5, "column_value": "4"}
                    ]},
                    {"feature_type": "SPELL_SLOTS", "key": "srd-2024_wizard_slots-2nd", "data_for_class_table": [
                        {"level": 1, "column_value": "0"},
                        {"level": 2, "column_value": "0"},
                        {"level": 3, "column_value": "2"},
                        {"level": 4, "column_value": "3"},
                        {"level": 5, "column_value": "3"}
                    ]},
                    {"feature_type": "SPELL_SLOTS", "key": "srd-2024_wizard_slots-3rd", "data_for_class_table": [
                        {"level": 1, "column_value": "0"},
                        {"level": 2, "column_value": "0"},
                        {"level": 3, "column_value": "0"},
                        {"level": 4, "column_value": "0"},
                        {"level": 5, "column_value": "2"}
                    ]}
                ]
                """);

        when(classRepo.findAllByOrderByNameAsc()).thenReturn(List.of(fighterClass, wizardClass));

        var multiclassRule = new RuleSection();
        multiclassRule.setSourceKey("srd-2024_multiclassing_spellcasting");
        multiclassRule.setDescription("""
                |Level|1|2|3|4|5|6|7|8|9|
                |---|---|---|---|---|---|---|---|---|---|
                |1|2|—|—|—|—|—|—|—|—|
                |2|3|—|—|—|—|—|—|—|—|
                |3|4|2|—|—|—|—|—|—|—|
                |4|4|3|—|—|—|—|—|—|—|
                |5|4|3|2|—|—|—|—|—|—|
                |6|4|3|3|—|—|—|—|—|—|
                |7|4|3|3|1|—|—|—|—|—|
                |8|4|3|3|2|—|—|—|—|—|
                |9|4|3|3|3|1|—|—|—|—|
                |10|4|3|3|3|2|—|—|—|—|
                |11|4|3|3|3|2|1|—|—|—|
                |12|4|3|3|3|2|1|—|—|—|
                |13|4|3|3|3|2|1|1|—|—|
                |14|4|3|3|3|2|1|1|—|—|
                |15|4|3|3|3|2|1|1|1|—|
                |16|4|3|3|3|2|1|1|1|—|
                |17|4|3|3|3|2|1|1|1|1|
                |18|4|3|3|3|3|1|1|1|1|
                |19|4|3|3|3|3|2|1|1|1|
                |20|4|3|3|3|3|2|2|1|1|
                """);
        when(ruleSectionRepo.findAllByOrderBySortOrderAsc()).thenReturn(List.of(multiclassRule));

        engine = new SheetEngine(classRepo, ruleSectionRepo);
        engine.initialize();
    }

    private CharacterSheet createSheet(Map<String, Integer> abilityScores,
                                       List<Map<String, Object>> classLevels,
                                       List<String> skillProficiencies,
                                       List<String> expertise,
                                       String speciesSpeed,
                                       int xp,
                                       int hitDiceUsed) throws Exception {
        var sheet = new CharacterSheet();
        var mapper = new ObjectMapper();

        sheet.setAbilityScores(mapper.writeValueAsString(abilityScores));
        sheet.setClassLevels(mapper.writeValueAsString(classLevels));

        var prof = new LinkedHashMap<String, Object>();
        prof.put("skills", skillProficiencies != null ? skillProficiencies : List.of());
        prof.put("tools", List.of());
        prof.put("languages", List.of());
        prof.put("armor", List.of());
        prof.put("weapons", List.of());
        prof.put("expertise", expertise != null ? expertise : List.of());
        sheet.setProficiencies(mapper.writeValueAsString(prof));

        sheet.setFeatRefs(mapper.writeValueAsString(List.of()));
        sheet.setOverrides(mapper.writeValueAsString(Map.of()));
        sheet.setSpellSlotsUsed(mapper.writeValueAsString(Map.of()));
        sheet.setXp(xp);
        sheet.setHitDiceUsed(hitDiceUsed);

        if (speciesSpeed != null) {
            var species = new Species();
            species.setSpeed(speciesSpeed);
            sheet.setSpecies(species);
        }

        return sheet;
    }

    @Test
    void level5Fighter() throws Exception {
        var scores = Map.of("str", 18, "dex", 14, "con", 16, "int", 8, "wis", 12, "cha", 10);
        var classLevels = List.<Map<String, Object>>of(
                Map.of("classSourceKey", "srd-2024_fighter", "level", 5,
                        "hitDieRolls", List.of(8, 5, 8, 6))
        );
        var sheet = createSheet(scores, classLevels,
                List.of("athletics", "perception"), List.of(), null, 6500, 2);

        var dv = engine.derive(sheet);

        assertEquals(3, dv.proficiencyBonus(), "Level 5 = +3 PB");
        assertEquals(5, dv.totalLevel());
        assertEquals(4, dv.strMod(), "STR 18 = +4");
        assertEquals(2, dv.dexMod(), "DEX 14 = +2");
        assertEquals(3, dv.conMod(), "CON 16 = +3");
        assertEquals(-1, dv.intMod(), "INT 8 = -1");
        assertEquals(1, dv.wisMod(), "WIS 12 = +1");
        assertEquals(0, dv.chaMod(), "CHA 10 = +0");

        assertEquals(52, dv.maxHp(), "HP: 10+3 + 8+3 + 5+3 + 8+3 + 6+3 = 52");
        assertEquals(5, dv.totalHitDice());
        assertEquals(3, dv.remainingHitDice(), "5 total - 2 used = 3");

        assertEquals(7, dv.saveStr(), "STR save: 4 + 3(PB) = 7");
        assertEquals(2, dv.saveDex(), "DEX save: 2 + 0 = 2");
        assertEquals(6, dv.saveCon(), "CON save: 3 + 3 = 6");

        assertEquals(7, dv.skillBonuses().get("athletics"));
        assertEquals(4, dv.skillBonuses().get("perception"));

        assertEquals(14, dv.passivePerception());
        assertEquals(30, dv.speed());
        assertEquals("Fighter 5", dv.classAndLevel());
    }

    @Test
    void level1Fighter() throws Exception {
        var scores = Map.of("str", 15, "dex", 12, "con", 14, "int", 10, "wis", 13, "cha", 8);
        var classLevels = List.<Map<String, Object>>of(
                Map.of("classSourceKey", "srd-2024_fighter", "level", 1, "hitDieRolls", List.of())
        );
        var sheet = createSheet(scores, classLevels, List.of(), List.of(), null, 0, 0);

        var dv = engine.derive(sheet);

        assertEquals(1, dv.totalLevel());
        assertEquals(2, dv.proficiencyBonus());
        assertEquals(2, dv.strMod(), "STR 15 = +2");
        assertEquals(1, dv.dexMod(), "DEX 12 = +1");
        assertEquals(2, dv.conMod(), "CON 14 = +2");
        assertEquals(-1, dv.chaMod(), "CHA 8 = -1");

        assertEquals(12, dv.maxHp(), "Level 1: 10 + 2 = 12");
        assertEquals(1, dv.totalHitDice());
        assertEquals(1, dv.remainingHitDice());

        assertEquals("Fighter 1", dv.classAndLevel());
        assertEquals(11, dv.armorClass(), "AC: 10 + 1(dex) = 11");
    }

    @Test
    void level3Wizard() throws Exception {
        var scores = Map.of("str", 8, "dex", 14, "con", 12, "int", 16, "wis", 10, "cha", 10);
        var classLevels = List.<Map<String, Object>>of(
                Map.of("classSourceKey", "srd-2024_wizard", "level", 3, "hitDieRolls", List.of(5, 4))
        );
        var sheet = createSheet(scores, classLevels, List.of(), List.of(), null, 900, 0);

        var dv = engine.derive(sheet);

        assertEquals(3, dv.totalLevel());
        assertEquals(2, dv.proficiencyBonus());
        assertEquals(3, dv.intMod());

        assertEquals(18, dv.maxHp(), "6+1 + 5+1 + 4+1 = 18");

        assertEquals(4, dv.spellSlots()[1], "1st-level slots = 4");
        assertEquals(2, dv.spellSlots()[2], "2nd-level slots = 2");
        assertEquals(0, dv.spellSlots()[3], "3rd-level slots = 0");

        assertTrue(dv.classSpellcastingAbilities().containsKey("srd-2024_wizard"));
        assertEquals("int", dv.classSpellcastingAbilities().get("srd-2024_wizard"));

        assertEquals(13, dv.spellSaveDC(), "8 + 2 + 3 = 13");
        assertEquals(5, dv.spellAttackBonus(), "2 + 3 = 5");

        assertEquals("Wizard 3", dv.classAndLevel());
    }

    @Test
    void overrideAc() throws Exception {
        var scores = Map.of("str", 10, "dex", 14, "con", 10, "int", 10, "wis", 10, "cha", 10);
        var classLevels = List.<Map<String, Object>>of(
                Map.of("classSourceKey", "srd-2024_fighter", "level", 1, "hitDieRolls", List.of())
        );
        var sheet = createSheet(scores, classLevels, List.of(), List.of(), null, 0, 0);
        var mapper = new ObjectMapper();
        sheet.setOverrides(mapper.writeValueAsString(Map.of("ac", 19)));

        var dv = engine.derive(sheet);

        assertEquals(19, dv.armorClass(), "Override AC takes precedence");
    }

    @Test
    void speciesSpeed() throws Exception {
        var scores = Map.of("str", 10, "dex", 10, "con", 10, "int", 10, "wis", 10, "cha", 10);
        var classLevels = List.<Map<String, Object>>of(
                Map.of("classSourceKey", "srd-2024_fighter", "level", 1, "hitDieRolls", List.of())
        );
        var sheet = createSheet(scores, classLevels, List.of(), List.of(), "25 ft.", 0, 0);

        var dv = engine.derive(sheet);

        assertEquals(25, dv.speed(), "Dwarf speed = 25");
    }

    @Test
    void skillExpertise() throws Exception {
        var scores = Map.of("str", 10, "dex", 10, "con", 10, "int", 10, "wis", 14, "cha", 10);
        var classLevels = List.<Map<String, Object>>of(
                Map.of("classSourceKey", "srd-2024_fighter", "level", 1, "hitDieRolls", List.of())
        );
        var sheet = createSheet(scores, classLevels,
                List.of("perception"), List.of("perception"), null, 0, 0);

        var dv = engine.derive(sheet);

        assertEquals(6, dv.skillBonuses().get("perception"),
                "Expertise: 2(WIS) + 2*2(PB) = 6");
        assertEquals(16, dv.passivePerception(), "10 + 6 = 16");
    }
}
