package dev.hendrikhoemberg.dmhelper.sheet.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.AttackDto;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.ClassLevelEntry;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.CreateSheetRequest;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.FeatureDto;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetEngine.DerivedValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(SheetService.class)
class SheetAttacksFeaturesTest {

    @Autowired
    private SheetService sheetService;

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

    private DerivedValues makeDerived() {
        return new DerivedValues(
                0, 0, 0, 0, 0, 0,
                2, 1,
                0, 0, 0, 0, 0, 0,
                Map.of(),
                10, 10, 10,
                10, 1, 1,
                new int[10], new int[10],
                Map.of(),
                0, 0,
                "Fighter 1",
                30, 0, 10,
                List.of(),
                List.of()
        );
    }

    private UUID createSheet() {
        when(sheetEngine.derive(any())).thenReturn(makeDerived());

        var scores = Map.of("str", 15, "dex", 14, "con", 13,
                "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        Map<String, Object> prof = Map.of("skills", List.of(), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of());
        var req = new CreateSheetRequest(testMember.getId(), scores,
                List.of(entry), prof, null, null, List.of(), 0);
        return sheetService.createSheet(req).id();
    }

    @Test
    void addAttack() {
        var sheetId = createSheet();
        var attack = new AttackDto(null, "Longsword", 5, "1d8+3", "slashing", "5 ft", "Versatile (1d10)", null, "");
        var result = sheetService.addAttack(sheetId, attack);

        assertEquals("longsword", result.key());
        assertEquals("Longsword", result.name());

        var dto = sheetService.getSheetDtoByPartyMemberId(testMember.getId());
        assertEquals(1, dto.attacks().size());
        assertEquals("Longsword", dto.attacks().get(0).name());
    }

    @Test
    void updateAttack() {
        var sheetId = createSheet();
        var attack = sheetService.addAttack(sheetId, new AttackDto(null, "Longsword", 5, "1d8+3", "slashing", "5 ft", null, null, ""));

        var updated = new AttackDto(attack.key(), "Longsword+1", 6, "1d8+4", "slashing", "5 ft", null, null, "");
        var result = sheetService.updateAttack(sheetId, updated);

        assertEquals("Longsword+1", result.name());
        assertEquals(6, result.attackBonus());
    }

    @Test
    void deleteAttack() {
        var sheetId = createSheet();
        var attack = sheetService.addAttack(sheetId, new AttackDto(null, "Longsword", 5, "1d8+3", "slashing", "5 ft", null, null, ""));

        sheetService.deleteAttack(sheetId, attack.key());

        var dto = sheetService.getSheetDtoByPartyMemberId(testMember.getId());
        assertTrue(dto.attacks().isEmpty());
    }

    @Test
    void addFeature() {
        var sheetId = createSheet();
        var feature = new FeatureDto(null, "Second Wind", "BONUS_ACTION", "Fighter 1", "Regain HP", "Second Wind");
        var result = sheetService.addFeature(sheetId, feature);

        assertEquals("second-wind", result.key());
        assertEquals("Second Wind", result.name());
        assertEquals("BONUS_ACTION", result.actionType());

        var dto = sheetService.getSheetDtoByPartyMemberId(testMember.getId());
        assertEquals(1, dto.features().size());
    }

    @Test
    void updateFeature() {
        var sheetId = createSheet();
        var feature = sheetService.addFeature(sheetId, new FeatureDto(null, "Second Wind", "BONUS_ACTION", "Fighter 1", "Regain HP", "Second Wind"));

        var updated = new FeatureDto(feature.key(), "Second Wind Revised", "ACTION", "Fighter 2", "Regain more HP", "Second Wind");
        var result = sheetService.updateFeature(sheetId, updated);

        assertEquals("Second Wind Revised", result.name());
        assertEquals("ACTION", result.actionType());
    }

    @Test
    void deleteFeature() {
        var sheetId = createSheet();
        var feature = sheetService.addFeature(sheetId, new FeatureDto(null, "Second Wind", "BONUS_ACTION", "Fighter 1", "Regain HP", "Second Wind"));

        sheetService.deleteFeature(sheetId, feature.key());

        var dto = sheetService.getSheetDtoByPartyMemberId(testMember.getId());
        assertTrue(dto.features().isEmpty());
    }

    @Test
    void roundTripJsonSerialization() throws Exception {
        var mapper = new ObjectMapper();
        var sheetId = createSheet();

        var attack = sheetService.addAttack(sheetId, new AttackDto(null, "Longsword", 5, "1d8+3", "slashing", "5 ft", "Versatile (1d10)", null, ""));
        var feature = sheetService.addFeature(sheetId, new FeatureDto(null, "Second Wind", "BONUS_ACTION", "Fighter 1", "Regain HP", "Second Wind"));

        var dto = sheetService.getSheetDtoByPartyMemberId(testMember.getId());

        var json = mapper.writeValueAsString(dto.attacks());
        assertTrue(json.contains("longsword"));
        assertTrue(json.contains("1d8+3"));

        var json2 = mapper.writeValueAsString(dto.features());
        assertTrue(json2.contains("second-wind"));
        assertTrue(json2.contains("BONUS_ACTION"));

        List<AttackDto> parsedAttacks = mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, AttackDto.class));
        assertEquals(1, parsedAttacks.size());
        assertEquals("Longsword", parsedAttacks.get(0).name());
    }

    @Test
    void attackKeyGeneratedWhenNull() {
        var sheetId = createSheet();
        var attack = sheetService.addAttack(sheetId, new AttackDto(null, "Magic Missile", 7, "1d4+1", "force", "120 ft", null, null, ""));
        var dto = sheetService.getSheetDtoByPartyMemberId(testMember.getId());

        var saved = dto.attacks().get(0);
        assertTrue(saved.key().matches("^[a-z0-9][a-z0-9._-]{0,99}$"));
    }

    @Test
    void featureKeyGeneratedWhenNull() {
        var sheetId = createSheet();
        var feature = sheetService.addFeature(sheetId, new FeatureDto(null, "Action Surge", "ACTION", "Fighter 2", "Take an extra action", null));
        var dto = sheetService.getSheetDtoByPartyMemberId(testMember.getId());

        var saved = dto.features().get(0);
        assertTrue(saved.key().matches("^[a-z0-9][a-z0-9._-]{0,99}$"));
    }

    @Test
    void packageExportIncludesAttacksAndFeatures() {
        var sheetId = createSheet();
        sheetService.addAttack(sheetId, new AttackDto(null, "Longsword", 5, "1d8+3", "slashing", "5 ft", null, null, ""));
        sheetService.addFeature(sheetId, new FeatureDto(null, "Second Wind", "BONUS_ACTION", "Fighter 1", "Regain HP", "Second Wind"));

        var dto = sheetService.getSheetDtoByPartyMemberId(testMember.getId());
        assertEquals(1, dto.attacks().size());
        assertEquals(1, dto.features().size());
        assertEquals("Longsword", dto.attacks().get(0).name());
        assertEquals("Second Wind", dto.features().get(0).name());
    }
}
