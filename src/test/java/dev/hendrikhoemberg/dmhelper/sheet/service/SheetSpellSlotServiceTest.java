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
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetEngine.DerivedValues;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(SheetService.class)
class SheetSpellSlotServiceTest {

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

    @Autowired
    private SheetSpellReferenceRepository spellRefRepo;

    @MockitoBean
    private SheetEngine sheetEngine;

    private final ObjectMapper mapper = new ObjectMapper();

    private PartyMember testMember;
    private UUID sheetId;

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
        testMember.setCharacterName("Test Caster");
        testMember.setAc(12);
        testMember.setMaxHp(20);
        testMember.setInitiativeBonus(1);
        testMember.setSpeed(30);
        testMember.setPassivePerception(10);
        testMember.setPassiveInsight(10);
        testMember.setPassiveInvestigation(10);
        testMember = partyMemberRepo.save(testMember);

        // Wizard class for spellcasting
        var wizard = new CharacterClass();
        wizard.setSource(ContentSource.SRD);
        wizard.setSourceKey("srd-2024_wizard");
        wizard.setName("Wizard");
        wizard.setHitDie("d6");
        wizard.setSavingThrows("[\"int\", \"wis\"]");
        wizard.setFeatures("[]");
        classRepo.save(wizard);

        when(sheetEngine.derive(any())).thenReturn(makeDerived(
                10, 10, 10, 3, 2, 20, 3, 3,
                new int[]{0, 4, 3, 2, 0, 0, 0, 0, 0, 0},
                new int[10],
                "Wizard 3"
        ));

        var scores = Map.of("str", 10, "dex", 10, "con", 10,
                "int", 16, "wis", 12, "cha", 8);
        Map<String, Object> prof = Map.of("skills", List.of(), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of());
        var entry = new ClassLevelEntry("srd-2024_wizard", 3, List.of());
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 0);

        var dto = sheetService.createSheet(req);
        sheetId = dto.id();
    }

    private DerivedValues makeDerived(int str, int dex, int con, int totalLevel,
                                      int profBonus, int maxHp, int totalHitDice,
                                      int remainingHitDice, int[] spellSlots,
                                      int[] pactSlots, String classAndLevel) {
        return new DerivedValues(
                (str - 10) / 2, (dex - 10) / 2, (con - 10) / 2,
                0, 0, 0,
                profBonus, totalLevel,
                0, 0, 0, 0, 0, 0,
                Map.of(),
                10, 10, 10,
                maxHp, totalHitDice, remainingHitDice,
                spellSlots, pactSlots,
                Map.of(),
                13, 5,
                classAndLevel,
                30, (dex - 10) / 2, 10 + (dex - 10) / 2,
                List.of(),
                List.of()
        );
    }

    @Test
    void spendSlotIncrementsUsed() {
        when(sheetEngine.derive(any())).thenReturn(makeDerived(
                10, 10, 10, 3, 2, 20, 3, 3,
                new int[]{0, 4, 3, 2, 0, 0, 0, 0, 0, 0},
                new int[10],
                "Wizard 3"
        ));

        var sheet = sheetRepo.findById(sheetId).orElseThrow();
        assertEquals("{}", sheet.getSpellSlotsUsed());

        sheetService.spendSpellSlot(sheetId, "1");

        sheet = sheetRepo.findById(sheetId).orElseThrow();
        var used = sheetService.getSpellSlotsUsed(sheet);
        assertEquals(1, used.get("1"));
    }

    @Test
    void recoverSlotDecrements() {
        when(sheetEngine.derive(any())).thenReturn(makeDerived(
                10, 10, 10, 3, 2, 20, 3, 3,
                new int[]{0, 4, 3, 2, 0, 0, 0, 0, 0, 0},
                new int[10],
                "Wizard 3"
        ));

        sheetService.spendSpellSlot(sheetId, "1");
        sheetService.spendSpellSlot(sheetId, "1");

        var sheet = sheetRepo.findById(sheetId).orElseThrow();
        var used = sheetService.getSpellSlotsUsed(sheet);
        assertEquals(2, used.get("1"));

        sheetService.recoverSpellSlot(sheetId, "1");

        sheet = sheetRepo.findById(sheetId).orElseThrow();
        used = sheetService.getSpellSlotsUsed(sheet);
        assertEquals(1, used.get("1"));
    }

    @Test
    void spendSlotCappedByMax() {
        when(sheetEngine.derive(any())).thenReturn(makeDerived(
                10, 10, 10, 3, 2, 20, 3, 3,
                new int[]{0, 4, 3, 2, 0, 0, 0, 0, 0, 0},
                new int[10],
                "Wizard 3"
        ));

        for (int i = 0; i < 10; i++) {
            sheetService.spendSpellSlot(sheetId, "1");
        }

        var sheet = sheetRepo.findById(sheetId).orElseThrow();
        var used = sheetService.getSpellSlotsUsed(sheet);
        assertEquals(4, used.get("1"), "Capped at max 4");
    }

    @Test
    void longRestClearsUsedSlots() {
        when(sheetEngine.derive(any())).thenReturn(makeDerived(
                10, 10, 10, 3, 2, 20, 3, 3,
                new int[]{0, 4, 3, 2, 0, 0, 0, 0, 0, 0},
                new int[10],
                "Wizard 3"
        ));

        sheetService.spendSpellSlot(sheetId, "1");
        sheetService.spendSpellSlot(sheetId, "2");

        var sheetBefore = sheetRepo.findById(sheetId).orElseThrow();
        var usedBefore = sheetService.getSpellSlotsUsed(sheetBefore);
        assertEquals(1, usedBefore.get("1"));
        assertEquals(1, usedBefore.get("2"));

        when(sheetEngine.derive(any())).thenReturn(makeDerived(
                10, 10, 10, 3, 2, 20, 3, 3,
                new int[]{0, 4, 3, 2, 0, 0, 0, 0, 0, 0},
                new int[10],
                "Wizard 3"
        ));

        sheetService.longRest(sheetId, 0);

        var sheetAfter = sheetRepo.findById(sheetId).orElseThrow();
        var usedAfter = sheetService.getSpellSlotsUsed(sheetAfter);
        assertTrue(usedAfter.isEmpty() || usedAfter.values().stream().allMatch(v -> v == 0));
    }

    @Test
    void togglePrepared() {
        when(sheetEngine.derive(any())).thenReturn(makeDerived(
                10, 10, 10, 3, 2, 20, 3, 3,
                new int[]{0, 4, 3, 2, 0, 0, 0, 0, 0, 0},
                new int[10],
                "Wizard 3"
        ));

        var spell = new Spell();
        spell.setSource(ContentSource.SRD);
        spell.setSourceKey("srd_burning_hands");
        spell.setName("Burning Hands");
        spell.setLevel(1);
        spell.setSchool("evocation");
        spell = spellRepo.save(spell);

        var spellDto = sheetService.addSpell(sheetId, spell.getId(), true, "wizard");
        assertTrue(spellDto.prepared());

        sheetService.togglePrepared(spellDto.id());

        var sheet = sheetRepo.findById(sheetId).orElseThrow();
        var spells = spellRefRepo.findBySheetId(sheetId);
        var ref = spells.stream().filter(s -> s.getId().equals(spellDto.id())).findFirst().orElseThrow();
        assertFalse(ref.isPrepared(), "Toggled prepared to false");
    }
}