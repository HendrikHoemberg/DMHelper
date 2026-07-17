package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetEngine;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetEngine.DerivedValues;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.ClassLevelEntry;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.CreateSheetRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({SheetService.class, EncounterService.class, CombatDifficultyCalculator.class,
        GameMapService.class, DiceEngine.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
        EncounterPartyHpSyncTest.MockConfig.class})
class EncounterPartyHpSyncTest {

    @MockitoBean
    private SheetEngine sheetEngine;

    @MockitoBean
    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @TestConfiguration
    static class MockConfig {
        @Bean
        TablePresentationService tablePresentationService() {
            return Mockito.mock(TablePresentationService.class);
        }
    }

    @Autowired
    private SheetService sheetService;

    @Autowired
    private EncounterService encounterService;

    @Autowired
    private PartyMemberRepository partyRepo;

    @Autowired
    private CharacterSheetRepository sheetRepo;

    @Autowired
    private CombatantRepository combatantRepo;

    @Autowired
    private CharacterClassRepository classRepo;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);

        var fighter = new CharacterClass();
        fighter.setSource(ContentSource.SRD);
        fighter.setSourceKey("srd-2024_fighter");
        fighter.setName("Fighter");
        fighter.setHitDie("d10");
        fighter.setSavingThrows("[\"str\", \"con\"]");
        fighter.setFeatures("[]");
        classRepo.save(fighter);

        em.flush();
    }

    private PartyMember createPartyMember(int currentHp, int maxHp) {
        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName("Test Character");
        pm.setAc(10);
        pm.setMaxHp(maxHp);
        pm.setCurrentHp(currentHp);
        pm.setInitiativeBonus(0);
        pm.setSpeed(30);
        pm.setPassivePerception(10);
        pm.setPassiveInsight(10);
        pm.setPassiveInvestigation(10);
        return partyRepo.save(pm);
    }

    private DerivedValues makeDerived(int maxHp, String classAndLevel) {
        return new DerivedValues(0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0,
                Map.of(), 10, 10, 10, maxHp, 1, 1, new int[10], new int[10],
                Map.of(), 0, 0, classAndLevel, 30, 0, 10, List.of(), List.of());
    }

    @Test
    void syncToPartyMemberClampsCurrentHpWhenMaxHpDecreases() {
        PartyMember pm = createPartyMember(20, 20);
        var scores = Map.of("str", 15, "dex", 14, "con", 13, "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        Map<String, Object> prof = Map.of("skills", List.of(), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of());
        var req = new CreateSheetRequest(pm.getId(), scores, List.of(entry), prof,
                null, null, List.of(), 0);

        when(sheetEngine.derive(any())).thenReturn(makeDerived(10, "Fighter 1"));

        sheetService.createSheet(req);

        PartyMember updated = partyRepo.findById(pm.getId()).orElseThrow();
        assertThat(updated.getMaxHp()).isEqualTo(10);
        assertThat(updated.getCurrentHp()).isEqualTo(10);
    }

    @Test
    void syncToPartyMemberDoesNotAutoHealWhenMaxHpIncreases() {
        PartyMember pm = createPartyMember(5, 10);
        var scores = Map.of("str", 15, "dex", 14, "con", 13, "int", 12, "wis", 10, "cha", 8);
        var entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
        Map<String, Object> prof = Map.of("skills", List.of(), "tools", List.of(),
                "languages", List.of(), "armor", List.of(), "weapons", List.of(), "expertise", List.of());
        var req = new CreateSheetRequest(pm.getId(), scores, List.of(entry), prof,
                null, null, List.of(), 0);

        when(sheetEngine.derive(any())).thenReturn(makeDerived(15, "Fighter 1"));

        sheetService.createSheet(req);

        PartyMember updated = partyRepo.findById(pm.getId()).orElseThrow();
        assertThat(updated.getMaxHp()).isEqualTo(15);
        assertThat(updated.getCurrentHp()).isEqualTo(5);
    }

    @Test
    void addCombatantCopiesCurrentHpFromPartyMember() {
        PartyMember pm = createPartyMember(14, 20);
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));

        CombatantDto c = encounterService.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, null, null, pm.getId()));

        assertThat(c.maxHp()).isEqualTo(20);
        assertThat(c.currentHp()).isEqualTo(14);
    }

    @Test
    void prefilledPartyMemberCopiesCurrentHp() {
        PartyMember pm = createPartyMember(7, 20);
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));

        var combatants = encounterService.prefillFromParty(enc.id(), campaign.getId());

        assertThat(combatants).hasSize(1);
        assertThat(combatants.get(0).currentHp()).isEqualTo(7);
        assertThat(combatants.get(0).maxHp()).isEqualTo(20);
    }

    @Test
    void setCombatantHpSyncsToLinkedPartyMember() {
        PartyMember pm = createPartyMember(20, 20);
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));
        encounterService.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, null, null, pm.getId()));

        var combatants = encounterService.getCombatants(enc.id());
        CombatantDto combatant = combatants.getFirst();

        encounterService.setHp(combatant.id(), 8, 2);

        PartyMember updated = partyRepo.findById(pm.getId()).orElseThrow();
        assertThat(updated.getCurrentHp()).isEqualTo(8);
        assertThat(updated.getTempHp()).isEqualTo(2);
    }

    @Test
    void applyDamageSyncsToLinkedPartyMember() {
        PartyMember pm = createPartyMember(20, 20);
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));
        encounterService.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, null, null, pm.getId()));

        var combatants = encounterService.getCombatants(enc.id());
        CombatantDto combatant = combatants.getFirst();

        encounterService.applyDamage(combatant.id(), -6);

        PartyMember updated = partyRepo.findById(pm.getId()).orElseThrow();
        assertThat(updated.getCurrentHp()).isEqualTo(14);
        assertThat(updated.getTempHp()).isEqualTo(0);
    }

    @Test
    void toggleConditionSyncsToLinkedPartyMember() {
        PartyMember pm = createPartyMember(20, 20);
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));
        encounterService.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, null, null, pm.getId()));

        var combatants = encounterService.getCombatants(enc.id());
        CombatantDto combatant = combatants.getFirst();

        encounterService.toggleCondition(combatant.id(), "poisoned", 3);

        PartyMember updated = partyRepo.findById(pm.getId()).orElseThrow();
        assertThat(updated.getConditionsJson()).contains("poisoned");
    }

    @Test
    void removeConditionSyncsToLinkedPartyMember() {
        PartyMember pm = createPartyMember(20, 20);
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));
        encounterService.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, null, null, pm.getId()));

        var combatants = encounterService.getCombatants(enc.id());
        CombatantDto combatant = combatants.getFirst();

        encounterService.toggleCondition(combatant.id(), "poisoned", 3);
        encounterService.removeCondition(combatant.id(), "poisoned");

        PartyMember updated = partyRepo.findById(pm.getId()).orElseThrow();
        assertThat(updated.getConditionsJson()).doesNotContain("poisoned");
    }

    @Test
    void setConcentrationSyncsToLinkedPartyMember() {
        PartyMember pm = createPartyMember(20, 20);
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));
        encounterService.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, null, null, pm.getId()));

        var combatants = encounterService.getCombatants(enc.id());
        CombatantDto combatant = combatants.getFirst();

        encounterService.setConcentration(combatant.id(), "Haste");

        PartyMember updated = partyRepo.findById(pm.getId()).orElseThrow();
        assertThat(updated.getConcentratingOn()).isEqualTo("Haste");
    }

    @Test
    void updateCombatantHpSyncsToLinkedPartyMember() {
        PartyMember pm = createPartyMember(20, 20);
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));
        encounterService.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, null, null, pm.getId()));

        var combatants = encounterService.getCombatants(enc.id());
        CombatantDto combatant = combatants.getFirst();

        var updateReq = new EncounterService.CombatantUpdateRequest(null, null, null,
                15, null, 3, null, null, null, null, null,
                null, null, null, null, null, null, null);
        encounterService.updateCombatant(combatant.id(), updateReq);

        PartyMember updated = partyRepo.findById(pm.getId()).orElseThrow();
        assertThat(updated.getCurrentHp()).isEqualTo(15);
        assertThat(updated.getTempHp()).isEqualTo(3);
    }

    @Test
    void unlinkedCombatantDoesNotAffectPartyMember() {
        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Encounter", null));
        CombatantDto c = encounterService.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 10, "MONSTER", null, null, null));

        encounterService.setHp(c.id(), 3, 0);

        var allPartyMembers = partyRepo.findByCampaignIdOrderByCharacterNameAsc(campaign.getId());
        assertThat(allPartyMembers).isEmpty();
    }
}
