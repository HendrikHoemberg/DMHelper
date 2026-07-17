package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
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
class SheetServiceTest {

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
    private SpellRepository spellRepo;

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
        fighter.setFeatures("[]");
        classRepo.save(fighter);

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
                                      int remainingHitDice, String classAndLevel) {
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
                List.of(),
                List.of()
        );
    }

    @Test
    void createSheet() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        Map<String, Object> prof = Map.of("skills", List.of(), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of());
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 0);

        var dto = sheetService.createSheet(req);

        assertNotNull(dto.id());
        assertNotNull(dto.derivedValues());
        assertEquals(2, dto.derivedValues().strMod(), "STR 15 = +2");
        assertEquals("Fighter 1", dto.derivedValues().classAndLevel());
        assertEquals(11, dto.derivedValues().maxHp());
    }

    @Test
    void sheetlessMember() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(10, 10, 10, 1, 0, 10, 1, 1, "Fighter 1")
        );

        assertFalse(sheetService.hasSheet(testMember.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> sheetService.getSheetDtoByPartyMemberId(testMember.getId()));
    }

    @Test
    void levelUp() {
        when(sheetEngine.derive(any()))
                .thenReturn(makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1"))
                .thenReturn(makeDerived(15, 14, 13, 2, 2, 18, 2, 2, "Fighter 2"));

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

        var levelReq = new LevelUpRequest("srd-2024_fighter", 0, true);
        var leveled = sheetService.levelUp(dto.id(), levelReq);

        assertEquals("Fighter 2", leveled.derivedValues().classAndLevel());
        assertEquals(18, leveled.derivedValues().maxHp());
    }

    @Test
    void shortRest() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

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

        var rested = sheetService.shortRest(dto.id(), 1);

        assertTrue(rested.hitDiceUsed() > 0);
    }

    @Test
    void longRest() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

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

        sheetService.shortRest(dto.id(), 1);

        // 1 total HD, min(1, half(1/2)=1) recovered, so used goes from 1 to 0
        var rested = sheetService.longRest(dto.id(), 0);

        assertEquals(0, rested.hitDiceUsed());
    }

    @Test
    void awardXp() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

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

        var xpDto = sheetService.awardXp(dto.id(), 500);

        assertEquals(500, xpDto.xp());
    }

    @Test
    void xpLevel20Achievable() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        Map<String, Object> prof = new HashMap<>();
        prof.put("skills", List.of()); prof.put("tools", List.of());
        prof.put("languages", List.of()); prof.put("armor", List.of());
        prof.put("weapons", List.of()); prof.put("expertise", List.of());
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 355000);
        var dto = sheetService.createSheet(req);

        var sheet = sheetRepo.findById(dto.id()).orElseThrow();
        var result = sheetService.checkLevelUp(sheet);

        assertEquals(355000, result.xp());
        assertEquals(20, result.level(), "355000 XP should reach level 20");
        assertTrue(result.levelUp(), "Level-up should trigger at 355000 XP");
    }

    @Test
    void setLevelWithClassSourceKey() {
        var wizard = new CharacterClass();
        wizard.setSource(ContentSource.SRD);
        wizard.setSourceKey("srd-2024_wizard");
        wizard.setName("Wizard");
        wizard.setHitDie("d6");
        wizard.setSavingThrows("[\"int\", \"wis\"]");
        wizard.setFeatures("[]");
        classRepo.save(wizard);

        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 2, 2, 11, 2, 2, "Fighter 1, Wizard 1")
        );

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        Map<String, Object> prof = new HashMap<>();
        prof.put("skills", List.of()); prof.put("tools", List.of());
        prof.put("languages", List.of()); prof.put("armor", List.of());
        prof.put("weapons", List.of()); prof.put("expertise", List.of());
        var entries = List.of(
                new ClassLevelEntry("srd-2024_fighter", 1, List.of()),
                new ClassLevelEntry("srd-2024_wizard", 1, List.of())
        );
        var req = new CreateSheetRequest(testMember.getId(), scores,
                entries, prof, null, null, List.of(), 0);
        var dto = sheetService.createSheet(req);

        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 6, 3, 11, 6, 6, "Fighter 1, Wizard 5")
        );

        var updated = sheetService.setLevel(dto.id(), "srd-2024_wizard", 5);

        assertEquals(2, updated.classLevels().size());
        assertEquals(1, updated.classLevels().get(0).level(), "Fighter level unchanged");
        assertEquals(5, updated.classLevels().get(1).level(), "Wizard level updated to 5");
    }


    @Test
    void deleteSheet() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

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

        sheetService.deleteSheet(dto.id());

        assertFalse(sheetService.hasSheet(testMember.getId()));
    }

    @Test
    void sheetCanPrepareCampaignScopedCustomSpell() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

        var campaign = testMember.getCampaign();
        var customSpell = new Spell();
        customSpell.setSource(ContentSource.CUSTOM);
        customSpell.setSourceKey("campaign-custom-fireball");
        customSpell.setCampaign(campaign);
        customSpell.setName("Campaign Fireball");
        customSpell.setLevel(3);
        customSpell.setSchool("evocation");
        spellRepo.save(customSpell);

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        Map<String, Object> prof = Map.of("skills", List.of(), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of());
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 0);
        var dto = sheetService.createSheet(req);

        var spellDto = sheetService.addSpellBySourceKey(dto.id(), "campaign-custom-fireball", true, "Fighter");

        assertNotNull(spellDto);
        assertEquals("Campaign Fireball", spellDto.spellName());
        assertEquals(3, spellDto.spellLevel());
    }

    @Test
    void playerPayloadDoesNotIncludeContentProvenance() throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var provenance = new dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance();
        provenance.setSourceTitle("Test Source");
        provenance.setSourceHash("abc123");
        provenance.setConverterId("test-converter");

        var spell = new Spell();
        spell.setSource(ContentSource.CUSTOM);
        spell.setSourceKey("test-provenance-spell");
        spell.setName("Provenance Test Spell");
        spell.setLevel(1);
        spell.setProvenance(provenance);
        spell = spellRepo.save(spell);

        var json = mapper.writeValueAsString(spell);

        assertFalse(json.contains("\"provenance\""), "JSON should not contain provenance field");
        assertFalse(json.contains("sourceTitle"), "JSON should not contain sourceTitle");
        assertFalse(json.contains("sourceHash"), "JSON should not contain sourceHash");
        assertFalse(json.contains("converterId"), "JSON should not contain converterId");
        assertFalse(json.contains("sourceTitle"), "JSON should not contain sourceTitle");
        assertFalse(json.contains("sourceHash"), "JSON should not contain sourceHash");
        assertFalse(json.contains("converterId"), "JSON should not contain converterId");
    }
}
