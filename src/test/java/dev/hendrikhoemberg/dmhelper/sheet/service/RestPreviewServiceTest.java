package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResourceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetEngine.DerivedValues;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.ClassLevelEntry;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.CreateSheetRequest;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.RestPreviewDto;
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
class RestPreviewServiceTest {

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
    private SheetResourceRepository resourceRepo;

    @MockitoBean
    private SheetEngine sheetEngine;

    private PartyMember testMember;
    private CharacterClass fighter;

    @BeforeEach
    void setUp() {
        fighter = new CharacterClass();
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

    private DerivedValues makeDerived(int totalLevel, int maxHp, int totalHitDice,
                                       int remainingHitDice, int[] spellSlots, int[] pactSlots) {
        return new DerivedValues(
                0, 0, 0, 0, 0, 0,
                2, totalLevel,
                0, 0, 0, 0, 0, 0,
                Map.of(),
                10, 10, 10,
                maxHp, totalHitDice, remainingHitDice,
                spellSlots, pactSlots,
                Map.of(),
                0, 0,
                "Fighter 5", 30, 0, 10,
                List.of(), List.of()
        );
    }

    private CreateSheetRequest makeCreateRequest(UUID memberId) {
        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 5, List.of(6, 5, 7, 6));
        Map<String, Object> prof = Map.of("skills", List.of(), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of());
        return new CreateSheetRequest(memberId, scores, List.of(entry), prof, null, null, List.of(), 0);
    }

    @Test
    void shortRestPreviewListsShortResetResources() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(5, 50, 5, 5, new int[10], new int[10])
        );

        var dto = sheetService.createSheet(makeCreateRequest(testMember.getId()));

        var shortRestResource = new SheetResource();
        shortRestResource.setSheet(sheetRepo.findById(dto.id()).orElseThrow());
        shortRestResource.setName("Action Surge");
        shortRestResource.setMaxUses(1);
        shortRestResource.setCurrentUses(0);
        shortRestResource.setResetRule(SheetResource.ResetRule.SHORT_REST);
        resourceRepo.save(shortRestResource);

        var longRestResource = new SheetResource();
        longRestResource.setSheet(sheetRepo.findById(dto.id()).orElseThrow());
        longRestResource.setName("Second Wind");
        longRestResource.setMaxUses(1);
        longRestResource.setCurrentUses(0);
        longRestResource.setResetRule(SheetResource.ResetRule.LONG_REST);
        resourceRepo.save(longRestResource);

        RestPreviewDto preview = sheetService.previewRest(dto.id(), "SHORT", 0);

        assertTrue(preview.resourcesToReset().contains("Action Surge"),
                "Short rest preview should list SHORT_REST resources");
        assertFalse(preview.resourcesToReset().contains("Second Wind"),
                "Short rest preview should NOT list LONG_REST resources");
        assertEquals("SHORT", preview.restType());
    }

    @Test
    void shortRestPreviewListsHitDice() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(5, 50, 5, 3, new int[10], new int[10])
        );

        var dto = sheetService.createSheet(makeCreateRequest(testMember.getId()));

        RestPreviewDto preview = sheetService.previewRest(dto.id(), "SHORT", 2);

        assertEquals(3, preview.hitDiceAvailable(), "Should report available hit dice");
        assertEquals(2, preview.hitDiceToSpend(), "Should report requested hit dice to spend");
        // Fighter d10 avg = 10/2 + 1 = 6 per die, 2 dice = 12
        assertTrue(preview.estimatedHpRecovered() > 0, "Should estimate HP recovery");
    }

    @Test
    void longRestPreviewRecoversSlotsAndHalfHitDice() {
        int[] spellSlots = new int[10];
        spellSlots[1] = 4;
        spellSlots[2] = 3;
        spellSlots[3] = 2;

        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(5, 50, 5, 5, spellSlots, new int[10])
        );

        var dto = sheetService.createSheet(makeCreateRequest(testMember.getId()));

        // Simulate some spell slots used and hit dice used
        var sheet = sheetRepo.findById(dto.id()).orElseThrow();
        sheet.setHitDiceUsed(4);
        try {
            var mapper = new tools.jackson.databind.ObjectMapper();
            sheet.setSpellSlotsUsed(mapper.writeValueAsString(Map.of("1", 2, "2", 1)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        sheetRepo.save(sheet);

        RestPreviewDto preview = sheetService.previewRest(dto.id(), "LONG", 0);

        assertEquals(50, preview.estimatedHpRecovered(), "Long rest should recover full HP");
        assertTrue(preview.clearExhaustionOneLevel(), "Long rest should clear exhaustion");
        assertFalse(preview.spellSlotsToRecover().isEmpty(), "Should list spell slots to recover");
        assertTrue(preview.notes().stream().anyMatch(n -> n.contains("spell slots")),
                "Should mention spell slot recovery");
    }

    @Test
    void previewDoesNotMutate() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(5, 50, 5, 5, new int[10], new int[10])
        );

        var dto = sheetService.createSheet(makeCreateRequest(testMember.getId()));

        var sheet = sheetRepo.findById(dto.id()).orElseThrow();
        sheet.setHitDiceUsed(0);
        sheetRepo.save(sheet);

        sheetService.previewRest(dto.id(), "SHORT", 5);
        sheetService.previewRest(dto.id(), "LONG", 0);

        var after = sheetRepo.findById(dto.id()).orElseThrow();
        assertEquals(0, after.getHitDiceUsed(), "previewRest should NOT mutate hitDiceUsed");
    }

    @Test
    void exhaustionNoteIncludedForLongRest() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(5, 50, 5, 5, new int[10], new int[10])
        );

        var dto = sheetService.createSheet(makeCreateRequest(testMember.getId()));

        RestPreviewDto preview = sheetService.previewRest(dto.id(), "LONG", 0);

        assertTrue(preview.clearExhaustionOneLevel(),
                "Long rest preview should indicate exhaustion reduction");
    }

    @Test
    void shortRestApplyHealsCurrentHpFromHitDice() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(5, 50, 5, 5, new int[10], new int[10])
        );

        var dto = sheetService.createSheet(makeCreateRequest(testMember.getId()));
        testMember = partyMemberRepo.findById(testMember.getId()).orElseThrow();
        testMember.setCurrentHp(10);
        testMember.setMaxHp(50);
        partyMemberRepo.save(testMember);

        sheetService.shortRest(dto.id(), 2);

        PartyMember after = partyMemberRepo.findById(testMember.getId()).orElseThrow();
        assertTrue(after.getCurrentHp() > 10, "Short rest should heal from hit dice");
        assertTrue(after.getCurrentHp() <= 50, "Short rest should not exceed max HP");
    }

    @Test
    void longRestApplyRestoresFullHpAndReducesExhaustion() {
        when(sheetEngine.derive(any())).thenReturn(
                makeDerived(5, 50, 5, 5, new int[10], new int[10])
        );

        var dto = sheetService.createSheet(makeCreateRequest(testMember.getId()));
        testMember = partyMemberRepo.findById(testMember.getId()).orElseThrow();
        testMember.setCurrentHp(12);
        testMember.setMaxHp(40);
        testMember.setTempHp(5);
        testMember.setExhaustion(2);
        testMember.setDeathSaveSuccesses(1);
        testMember.setDeathSaveFailures(2);
        partyMemberRepo.save(testMember);

        sheetService.longRest(dto.id(), 0);

        PartyMember after = partyMemberRepo.findById(testMember.getId()).orElseThrow();
        assertEquals(50, after.getCurrentHp(), "Long rest should restore to derived max HP");
        assertEquals(50, after.getMaxHp());
        assertEquals(0, after.getTempHp(), "Long rest clears temp HP");
        assertEquals(1, after.getExhaustion(), "Long rest reduces exhaustion by 1");
        assertEquals(0, after.getDeathSaveSuccesses());
        assertEquals(0, after.getDeathSaveFailures());
    }
}
