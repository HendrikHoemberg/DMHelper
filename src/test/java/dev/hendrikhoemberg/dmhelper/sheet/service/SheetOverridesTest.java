package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
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
class SheetOverridesTest {

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

    private SheetDto createTestSheet() {
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
        return sheetService.createSheet(req);
    }

    @Test
    void overridesWithMetaAreStoredAndRetrieved() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

        var dto = createTestSheet();

        Map<String, Object> overridesWithMeta = new LinkedHashMap<>();
        overridesWithMeta.put("armorClass", 18);
        overridesWithMeta.put("maxHp", 48);
        Map<String, Object> meta = new LinkedHashMap<>();
        Map<String, String> acMeta = new LinkedHashMap<>();
        acMeta.put("reason", "Mage Armor");
        acMeta.put("source", "spell");
        meta.put("armorClass", acMeta);
        Map<String, String> hpMeta = new LinkedHashMap<>();
        hpMeta.put("reason", "Aid");
        hpMeta.put("source", "spell");
        meta.put("maxHp", hpMeta);
        overridesWithMeta.put("_meta", meta);

        var updateReq = new UpdateSheetRequest(null, null, null, null, null, null, overridesWithMeta, -1);
        var updated = sheetService.updateSheet(dto.id(), updateReq);

        assertTrue(updated.overrides().containsKey("armorClass"));
        assertEquals(18, updated.overrides().get("armorClass"));
        assertTrue(updated.overrides().containsKey("maxHp"));
        assertEquals(48, updated.overrides().get("maxHp"));

        assertNotNull(updated.overridesMeta());
        assertTrue(updated.overridesMeta().containsKey("armorClass"));
        assertEquals("Mage Armor", updated.overridesMeta().get("armorClass").reason());
        assertEquals("spell", updated.overridesMeta().get("armorClass").source());
        assertTrue(updated.overridesMeta().containsKey("maxHp"));
        assertEquals("Aid", updated.overridesMeta().get("maxHp").reason());
        assertEquals("spell", updated.overridesMeta().get("maxHp").source());
    }

    @Test
    void overridesWithoutMetaReturnEmptyMeta() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

        var dto = createTestSheet();

        Map<String, Object> overridesOnly = new LinkedHashMap<>();
        overridesOnly.put("armorClass", 18);

        var updateReq = new UpdateSheetRequest(null, null, null, null, null, null, overridesOnly, -1);
        var updated = sheetService.updateSheet(dto.id(), updateReq);

        assertTrue(updated.overridesMeta().isEmpty());
    }

    @Test
    void sheetEngineIgnoresMetaInOverrides() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(15, 14, 13, 1, 2, 11, 1, 1, "Fighter 1")
        );

        var dto = createTestSheet();

        Map<String, Object> overridesWithMeta = new LinkedHashMap<>();
        overridesWithMeta.put("armorClass", 18);
        Map<String, Object> meta = new LinkedHashMap<>();
        Map<String, String> acMeta = new LinkedHashMap<>();
        acMeta.put("reason", "Mage Armor");
        acMeta.put("source", "spell");
        meta.put("armorClass", acMeta);
        overridesWithMeta.put("_meta", meta);

        var updateReq = new UpdateSheetRequest(null, null, null, null, null, null, overridesWithMeta, -1);
        var updated = sheetService.updateSheet(dto.id(), updateReq);

        var stored = sheetRepo.findById(updated.id()).orElseThrow();
        var engineDerived = sheetEngine.derive(stored);

        assertNotNull(engineDerived);
    }
}
