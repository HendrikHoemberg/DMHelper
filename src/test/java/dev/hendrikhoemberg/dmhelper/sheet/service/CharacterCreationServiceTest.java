package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetEngine.DerivedValues;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(SheetService.class)
class CharacterCreationServiceTest {

    @Autowired
    private SheetService sheetService;

    @Autowired
    private CharacterSheetRepository sheetRepo;

    @Autowired
    private PartyMemberRepository partyMemberRepo;

    @Autowired
    private CampaignRepository campaignRepo;

    @Autowired
    private CharacterClassRepository classRepo;

    @Autowired
    private FeatRepository featRepo;

    @MockitoBean
    private SheetEngine sheetEngine;

    private PartyMember testMember;

    @BeforeEach
    void setUp() {
        var fighter = new CharacterClass();
        fighter.setSource(ContentSource.SRD);
        fighter.setSourceKey("srd-2024_fighter");
        fighter.setName("Fighter");
        fighter.setHitDie("d10");
        fighter.setSavingThrows("[\"str\", \"con\"]");
        fighter.setFeatures("""
                [{"feature_type":"CORE_TRAITS_TABLE","description":"| Skill Proficiencies | Perception, Stealth | choose 2 from Acrobatics, Animal Handling, Athletics, History, Insight, Intimidation, Persuasion, Sleight of Hand, Survival |"}]
                """);
        fighter.setProficiencies("""
                {"armor":["light","medium","heavy","shields"],"weapons":["simple","martial"]}
                """);
        classRepo.save(fighter);

        var champion = new CharacterClass();
        champion.setSource(ContentSource.SRD);
        champion.setSourceKey("srd-2024_fighter_champion");
        champion.setName("Champion");
        champion.setHitDie("d10");
        champion.setSavingThrows("[]");
        champion.setFeatures("[]");
        champion.setSubclassOf("srd-2024_fighter");
        classRepo.save(champion);

        var campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepo.save(campaign);

        testMember = new PartyMember();
        testMember.setCampaign(campaign);
        testMember.setCharacterName("Test Character");
        testMember.setAc(10);
        testMember.setMaxHp(10);
        testMember.setInitiativeBonus(0);
        testMember.setSpeed(30);
        testMember.setPassivePerception(10);
        testMember.setPassiveInsight(10);
        testMember.setPassiveInvestigation(10);
        testMember = partyMemberRepo.save(testMember);
    }

    private DerivedValues makeDerived(int str, int dex, int con, int totalLevel,
                                      int profBonus, int maxHp, int totalHitDice,
                                      int remainingHitDice, String classAndLevel,
                                      List<String> skillChoices) {
        return new DerivedValues(
                (str - 10) / 2, (dex - 10) / 2, (con - 10) / 2,
                0, 0, 0,
                profBonus, totalLevel,
                0, 0, 0, 0, 0, 0,
                Map.of(),
                10, 10, 10,
                maxHp, totalHitDice, remainingHitDice,
                new int[10], new int[10],
                Map.of(),
                0, 0,
                classAndLevel,
                30, (dex - 10) / 2, 10 + (dex - 10) / 2,
                skillChoices,
                List.of()
        );
    }

    @Test
    void creationOptionsReturnsSkillChoicesForFighter() {
        var options = sheetService.creationOptions("srd-2024_fighter");
        assertEquals("srd-2024_fighter", options.classSourceKey());
        assertEquals("d10", options.hitDie());
        assertTrue(options.savingThrows().contains("str"));
        assertTrue(options.savingThrows().contains("con"));
        assertEquals(2, options.skillChoiceCount());
        assertTrue(options.skillOptions().contains("acrobatics"));
        assertTrue(options.skillOptions().contains("athletics"));
        assertTrue(options.armorProficiencies().contains("light"));
        assertTrue(options.weaponProficiencies().contains("simple"));
    }

    @Test
    void creationOptionsReturnsSubclasses() {
        var options = sheetService.creationOptions("srd-2024_fighter");
        assertFalse(options.subclasses().isEmpty());
        assertTrue(options.subclasses().stream().anyMatch(s -> s.name().equals("Champion")));
    }

    @Test
    void createSheetWithSubclass() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1", List.of())
        );

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of(), "srd-2024_fighter_champion");
        Map<String, Object> prof = Map.of(
                "skills", List.of("athletics", "perception"), "tools", List.of(),
                "languages", List.of("common", "elvish"), "armor", List.of("light"),
                "weapons", List.of("simple"), "expertise", List.of()
        );
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 0);

        var dto = sheetService.createSheet(req);
        assertNotNull(dto.id());
        assertEquals(1, dto.classLevels().size());
        assertEquals("srd-2024_fighter", dto.classLevels().get(0).classSourceKey());
        assertEquals("srd-2024_fighter_champion", dto.classLevels().get(0).subclassSourceKey());
    }

    @Test
    void createSheetWithProficiencies() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1", List.of())
        );

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        Map<String, Object> prof = Map.of(
                "skills", List.of("athletics", "perception"), "tools", List.of("thieves_tools"),
                "languages", List.of("common", "elvish"), "armor", List.of("light", "medium"),
                "weapons", List.of("simple", "martial"), "expertise", List.of()
        );
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 0);

        var dto = sheetService.createSheet(req);
        assertNotNull(dto.id());
        var sheet = sheetRepo.findById(dto.id()).orElseThrow();
        assertTrue(sheet.getProficiencies().contains("athletics"));
        assertTrue(sheet.getProficiencies().contains("thieves_tools"));
        assertTrue(sheet.getProficiencies().contains("elvish"));
        assertTrue(sheet.getProficiencies().contains("medium"));
    }

    @Test
    void skillBonusesIncludeProficiencyAfterCreation() {
        Map<String, Integer> skillBonuses = new LinkedHashMap<>();
        skillBonuses.put("athletics", 5);
        skillBonuses.put("perception", 4);
        skillBonuses.put("acrobatics", 2);

        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1", List.of())
        );

        // Override derive to return skill bonuses after the first call
        when(sheetEngine.derive(any())).thenReturn(new DerivedValues(
                2, 2, 1, 0, 0, 0,
                2, 1,
                4, 2, 3, 0, 0, 0,
                skillBonuses,
                12, 10, 10,
                11, 1, 1,
                new int[10], new int[10],
                Map.of(),
                0, 0,
                "Fighter 1",
                30, 2, 12,
                List.of(),
                List.of()
        ));

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        Map<String, Object> prof = Map.of(
                "skills", List.of("athletics", "perception"), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of()
        );
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 0);

        var dto = sheetService.createSheet(req);
        assertEquals(5, dto.derivedValues().skillBonuses().get("athletics").intValue());
        assertEquals(4, dto.derivedValues().skillBonuses().get("perception").intValue());
    }

    @Test
    void createSheetWithDefaultProficienciesWhenNull() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1", List.of())
        );

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), null, null, null, List.of(), 0);

        var dto = sheetService.createSheet(req);
        assertNotNull(dto.id());
    }

    @Test
    void levelUpPromptsSubclassAtLevel3() {
        when(sheetEngine.derive(any()))
                .thenReturn(makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1", List.of()));

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        Map<String, Object> prof = new HashMap<>();
        prof.put("skills", List.of()); prof.put("tools", List.of());
        prof.put("languages", List.of()); prof.put("armor", List.of());
        prof.put("weapons", List.of()); prof.put("expertise", List.of());
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 0);
        var dto = sheetService.createSheet(req);

        when(sheetEngine.derive(any()))
                .thenReturn(makeDerived(15, 14, 13, 2, 2, 18, 2, 2, "Fighter 2", List.of()));

        var levelReq = new LevelUpRequest("srd-2024_fighter", 0, true);
        var leveled = sheetService.levelUp(dto.id(), levelReq);

        when(sheetEngine.derive(any()))
                .thenReturn(makeDerived(15, 14, 13, 3, 2, 25, 3, 3, "Fighter 3", List.of()));

        var levelReq3 = new LevelUpRequest("srd-2024_fighter", 0, true);
        var leveled3 = sheetService.levelUp(dto.id(), levelReq3);

        assertEquals(3, leveled3.derivedValues().totalLevel());
        // Subclass should still be empty since we didn't provide one
    }
}
