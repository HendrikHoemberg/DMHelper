package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({EncounterService.class, CombatDifficultyCalculator.class, GameMapService.class, DiceEngine.class})
class EncounterServiceTest {

    @Autowired private EncounterService service;
    @Autowired private GameMapService mapService;
    @Autowired private TokenRepository tokenRepo;
    @Autowired private PartyMemberRepository partyRepo;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;
    private GameMap map;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
        em.flush();
        map = mapService.create(campaign.getId(), "Test Map", 30, 20, 48);
    }

    @Test
    void shouldCreateEncounter() {
        EncounterDto dto = service.create(campaign.getId(), new CreateRequest("Test Encounter", map.getId()));
        assertThat(dto.id()).isNotNull();
        assertThat(dto.name()).isEqualTo("Test Encounter");
        assertThat(dto.campaignId()).isEqualTo(campaign.getId());
        assertThat(dto.mapId()).isEqualTo(map.getId());
        assertThat(dto.status()).isEqualTo("PLANNED");
    }

    @Test
    void shouldActivateEncounter() {
        EncounterDto dto = service.create(campaign.getId(), new CreateRequest("Activate Me", null));
        EncounterDto activated = service.activate(dto.id());
        assertThat(activated.status()).isEqualTo("ACTIVE");
        assertThat(activated.round()).isEqualTo(1);
        assertThat(activated.activeTurnIndex()).isEqualTo(-1);
    }

    @Test
    void shouldEndEncounter() {
        EncounterDto dto = service.create(campaign.getId(), new CreateRequest("End Me", null));
        service.activate(dto.id());
        EncounterDto ended = service.endEncounter(dto.id());
        assertThat(ended.status()).isEqualTo("DONE");
    }

    @Test
    void shouldAddCombatantAdHoc() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 7, "MONSTER", null, null, null));
        assertThat(c.id()).isNotNull();
        assertThat(c.name()).isEqualTo("Goblin");
        assertThat(c.maxHp()).isEqualTo(7);
        assertThat(c.currentHp()).isEqualTo(7);
        assertThat(c.kind()).isEqualTo("MONSTER");
    }

    @Test
    void shouldAddCombatantFromToken() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        Token token = new Token();
        token.setMap(map);
        token.setName("Orc");
        token.setKind("MONSTER");
        token.setMaxHp(15);
        token.setCurrentHp(15);
        em.persist(token);
        em.flush();

        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, token.getId(), null, null));
        assertThat(c.name()).isEqualTo("Orc");
        assertThat(c.maxHp()).isEqualTo(15);
    }

    @Test
    void shouldAddCombatantFromPartyMember() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName("Thia");
        pm.setClassAndLevel("Rogue 5");
        pm.setAc(16);
        pm.setMaxHp(38);
        pm.setInitiativeBonus(4);
        pm.setSpeed(30);
        pm.setPassivePerception(17);
        pm.setPassiveInsight(12);
        pm.setPassiveInvestigation(14);
        pm.setActive(true);
        em.persist(pm);
        em.flush();

        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest(null, 0, null, null, null, pm.getId()));
        assertThat(c.name()).isEqualTo("Thia");
        assertThat(c.kind()).isEqualTo("PC");
        assertThat(c.maxHp()).isEqualTo(38);
    }

    @Test
    void shouldPrefillFromParty() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        PartyMember pm1 = new PartyMember();
        pm1.setCampaign(campaign);
        pm1.setCharacterName("Thia");
        pm1.setClassAndLevel("Rogue 5");
        pm1.setAc(16);
        pm1.setMaxHp(38);
        pm1.setInitiativeBonus(4);
        pm1.setSpeed(30);
        pm1.setPassivePerception(17);
        pm1.setPassiveInsight(12);
        pm1.setPassiveInvestigation(14);
        pm1.setActive(true);
        em.persist(pm1);
        PartyMember pm2 = new PartyMember();
        pm2.setCampaign(campaign);
        pm2.setCharacterName("Bruenor");
        pm2.setClassAndLevel("Fighter 5");
        pm2.setAc(18);
        pm2.setMaxHp(45);
        pm2.setInitiativeBonus(2);
        pm2.setSpeed(25);
        pm2.setPassivePerception(13);
        pm2.setPassiveInsight(10);
        pm2.setPassiveInvestigation(9);
        pm2.setActive(true);
        em.persist(pm2);
        em.flush();

        var combatants = service.prefillFromParty(enc.id(), campaign.getId());
        assertThat(combatants).hasSize(2);
        assertThat(combatants).extracting(CombatantDto::name).containsExactly("Bruenor", "Thia");
    }

    @Test
    void shouldSetInitiativeAndSort() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto a = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Alice", 10, "NPC", null, null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Bob", 10, "NPC", null, null, null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Charlie", 10, "NPC", null, null, null));

        service.setInitiative(a.id(), 20);
        service.setInitiative(b.id(), 5);
        service.setInitiative(c.id(), 15);

        var combatants = service.getCombatants(enc.id());
        assertThat(combatants.get(0).initiative()).isEqualTo(20);
        assertThat(combatants.get(1).initiative()).isEqualTo(15);
        assertThat(combatants.get(2).initiative()).isEqualTo(5);
    }

    @Test
    void shouldAdvanceNextTurn() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto a = service.addCombatant(enc.id(),
                new CombatantCreateRequest("A", 10, "NPC", null, null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("B", 10, "NPC", null, null, null));
        service.setInitiative(a.id(), 10);
        service.setInitiative(b.id(), 5);

        // Activate sets round=1, activeTurnIndex=-1
        // First nextTurn advances to index 0 (wraps around), incrementing round
        EncounterDto turn1 = service.nextTurn(enc.id());
        assertThat(turn1.activeTurnIndex()).isEqualTo(0);
        assertThat(turn1.round()).isEqualTo(2);

        EncounterDto turn2 = service.nextTurn(enc.id());
        assertThat(turn2.activeTurnIndex()).isEqualTo(1);
        assertThat(turn2.round()).isEqualTo(2);

        // Wrap back to index 0 increments round again
        EncounterDto turn3 = service.nextTurn(enc.id());
        assertThat(turn3.activeTurnIndex()).isEqualTo(0);
        assertThat(turn3.round()).isEqualTo(3);
    }

    @Test
    void shouldSkipDefeatedCombatantsOnNextTurn() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto a = service.addCombatant(enc.id(),
                new CombatantCreateRequest("A", 10, "NPC", null, null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("B", 10, "NPC", null, null, null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("C", 10, "NPC", null, null, null));
        service.setInitiative(a.id(), 15);
        service.setInitiative(b.id(), 10);
        service.setInitiative(c.id(), 5);
        service.markDefeated(b.id(), true);

        EncounterDto turn1 = service.nextTurn(enc.id());
        assertThat(turn1.activeTurnIndex()).isEqualTo(0);

        EncounterDto turn2 = service.nextTurn(enc.id());
        assertThat(turn2.activeTurnIndex()).isEqualTo(2);
    }

    @Test
    void shouldApplyDamage() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Monster", 20, "MONSTER", null, null, null));

        CombatantDto damaged = service.applyDamage(c.id(), -5);
        assertThat(damaged.currentHp()).isEqualTo(15);
    }

    @Test
    void shouldApplyDamageTempHpFirst() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Monster", 20, "MONSTER", null, null, null));
        service.updateCombatant(c.id(),                 new EncounterService.CombatantUpdateRequest(null, null, null, null, null, 5,
                null, null, null, null, null, null, null, null, null, null, null, null));

        CombatantDto damaged = service.applyDamage(c.id(), -8);
        assertThat(damaged.tempHp()).isEqualTo(0);
        assertThat(damaged.currentHp()).isEqualTo(17);
    }

    @Test
    void shouldAutoDefeatNonPcOnZeroHp() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Monster", 5, "MONSTER", null, null, null));

        CombatantDto defeated = service.applyDamage(c.id(), -10);
        assertThat(defeated.defeated()).isTrue();
    }

    @Test
    void shouldApplyHeal() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Healee", 20, "NPC", null, null, null));
        service.applyDamage(c.id(), -10);

        CombatantDto healed = service.applyDamage(c.id(), 5);
        assertThat(healed.currentHp()).isEqualTo(15);
    }

    @Test
    void shouldNotHealAboveMaxHp() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Healee", 20, "NPC", null, null, null));

        CombatantDto healed = service.applyDamage(c.id(), 50);
        assertThat(healed.currentHp()).isEqualTo(20);
    }

    @Test
    void shouldToggleCondition() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Target", 10, "NPC", null, null, null));

        CombatantDto withCond = service.toggleCondition(c.id(), "poisoned", 3);
        assertThat(withCond.conditions()).hasSize(1);
        assertThat(withCond.conditions().get(0).sourceKey()).isEqualTo("poisoned");

        CombatantDto withoutCond = service.toggleCondition(c.id(), "poisoned", 3);
        assertThat(withoutCond.conditions()).isEmpty();
    }

    @Test
    void shouldSetAndResolveConcentration() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Wizard", 10, "NPC", null, null, null));

        CombatantDto conc = service.setConcentration(c.id(), "Haste");
        assertThat(conc.concentratingOn()).isEqualTo("Haste");

        CombatantDto failed = service.resolveConcentrationCheck(c.id(), false);
        assertThat(failed.concentratingOn()).isNull();
    }

    @Test
    void shouldUseLegendaryAction() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Dragon", 100, "MONSTER", null, null, null));
        service.updateCombatant(c.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, 2, null, null, null));

        CombatantDto after = service.useLegendaryAction(c.id());
        assertThat(after.legendaryActionsUsed()).isEqualTo(1);
    }

    @Test
    void shouldUseLegendaryResistance() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Dragon", 100, "MONSTER", null, null, null));
        service.updateCombatant(c.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, 2, null));

        CombatantDto after = service.useLegendaryResistance(c.id());
        assertThat(after.legendaryResistancesUsed()).isEqualTo(1);
    }

    @Test
    void shouldUndoLastAction() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Monster", 20, "MONSTER", null, null, null));
        int originalHp = c.currentHp();

        service.applyDamage(c.id(), -5);
        service.undo(enc.id());

        CombatantDto reverted = service.getCombatant(c.id());
        assertThat(reverted.currentHp()).isEqualTo(originalHp);
    }
}
