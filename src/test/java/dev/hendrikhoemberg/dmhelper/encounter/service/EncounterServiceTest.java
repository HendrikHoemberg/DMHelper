package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({EncounterService.class, EncounterPlacementService.class, CombatDifficultyCalculator.class, GameMapService.class, DiceEngine.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
        dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver.class,
        dev.hendrikhoemberg.dmhelper.config.MarkdownUtil.class,
        })
class EncounterServiceTest {

    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired private EncounterService service;
    @Autowired private GameMapService mapService;
    @Autowired private TokenRepository tokenRepo;
    @Autowired private PartyMemberRepository partyRepo;
    @Autowired private CombatLogEntryRepository combatLogRepo;

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
        assertThat(activated.combatPhase()).isEqualTo("SETUP");
        assertThat(activated.round()).isEqualTo(0);
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
                new CombatantCreateRequest("Goblin", 7, "MONSTER", null, null));
        assertThat(c.id()).isNotNull();
        assertThat(c.name()).isEqualTo("Goblin");
        assertThat(c.maxHp()).isEqualTo(7);
        assertThat(c.currentHp()).isEqualTo(7);
        assertThat(c.kind()).isEqualTo("MONSTER");
        assertThat(c.threatKind()).isNull();
        assertThat(c.threatId()).isNull();
        assertThat(c.threatCard()).isNull();
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
                new CombatantCreateRequest(null, 0, null, null, pm.getId()));
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
                new CombatantCreateRequest("Alice", 10, "NPC", null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Bob", 10, "NPC", null, null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Charlie", 10, "NPC", null, null));

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
                new CombatantCreateRequest("A", 10, "NPC", null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("B", 10, "NPC", null, null));
        service.setInitiative(a.id(), 10);
        service.setInitiative(b.id(), 5);

        service.startCombat(enc.id(), false);
        // startCombat sets round=1, activeTurnIndex=0 (A)

        // First nextTurn advances to index 1 (B)
        EncounterDto turn1 = service.nextTurn(enc.id());
        assertThat(turn1.activeTurnIndex()).isEqualTo(1);
        assertThat(turn1.round()).isEqualTo(1);

        // Wrap back from last combatant to index 0 increments round
        EncounterDto turn2 = service.nextTurn(enc.id());
        assertThat(turn2.activeTurnIndex()).isEqualTo(0);
        assertThat(turn2.round()).isEqualTo(2);

        // Advance again
        EncounterDto turn3 = service.nextTurn(enc.id());
        assertThat(turn3.activeTurnIndex()).isEqualTo(1);
        assertThat(turn3.round()).isEqualTo(2);
    }

    @Test
    void shouldSkipDefeatedCombatantsOnNextTurn() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto a = service.addCombatant(enc.id(),
                new CombatantCreateRequest("A", 10, "NPC", null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("B", 10, "NPC", null, null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("C", 10, "NPC", null, null));
        service.setInitiative(a.id(), 15);
        service.setInitiative(b.id(), 10);
        service.setInitiative(c.id(), 5);
        service.markDefeated(b.id(), true);

        service.startCombat(enc.id(), false);
        // startCombat sets activeTurnIndex=0 (A, initiative 15)

        // nextTurn skips B (defeated), goes to C (index 2)
        EncounterDto turn1 = service.nextTurn(enc.id());
        assertThat(turn1.activeTurnIndex()).isEqualTo(2);
    }

    @Test
    void nextTurnIsANoOpWhenEveryCombatantIsDefeated() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto combatant = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 7, "MONSTER", null, null));
        service.setInitiative(combatant.id(), 10);
        service.activate(enc.id());
        service.startCombat(enc.id(), false);
        service.markDefeated(combatant.id(), true);

        EncounterDto result = service.nextTurn(enc.id());

        assertThat(result.activeTurnIndex()).isZero();
        assertThat(result.round()).isEqualTo(1);
    }

    @Test
    void shouldApplyDamage() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Monster", 20, "MONSTER", null, null));

        CombatantDto damaged = service.applyDamage(c.id(), -5);
        assertThat(damaged.currentHp()).isEqualTo(15);
    }

    @Test
    void shouldApplyDamageTempHpFirst() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Monster", 20, "MONSTER", null, null));
        service.updateCombatant(c.id(),                 new EncounterService.CombatantUpdateRequest(null, null, null, null, null, 5,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        CombatantDto damaged = service.applyDamage(c.id(), -8);
        assertThat(damaged.tempHp()).isEqualTo(0);
        assertThat(damaged.currentHp()).isEqualTo(17);
    }

    @Test
    void shouldAutoDefeatNonPcOnZeroHp() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Monster", 5, "MONSTER", null, null));

        CombatantDto defeated = service.applyDamage(c.id(), -10);
        assertThat(defeated.defeated()).isTrue();
    }

    @Test
    void shouldApplyHeal() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Healee", 20, "NPC", null, null));
        service.applyDamage(c.id(), -10);

        CombatantDto healed = service.applyDamage(c.id(), 5);
        assertThat(healed.currentHp()).isEqualTo(15);
    }

    @Test
    void shouldNotHealAboveMaxHp() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Healee", 20, "NPC", null, null));

        CombatantDto healed = service.applyDamage(c.id(), 50);
        assertThat(healed.currentHp()).isEqualTo(20);
    }

    @Test
    void shouldToggleCondition() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Target", 10, "NPC", null, null));

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
                new CombatantCreateRequest("Wizard", 10, "NPC", null, null));

        CombatantDto conc = service.setConcentration(c.id(), "Haste");
        assertThat(conc.concentratingOn()).isEqualTo("Haste");

        CombatantDto failed = service.resolveConcentrationCheck(c.id(), false);
        assertThat(failed.concentratingOn()).isNull();
    }

    @Test
    void shouldUseLegendaryAction() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Dragon", 100, "MONSTER", null, null));
        service.updateCombatant(c.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, 2, null, null, null, null, null, null, null));

        CombatantDto after = service.useLegendaryAction(c.id());
        assertThat(after.legendaryActionsUsed()).isEqualTo(1);
    }

    @Test
    void shouldUseLegendaryResistance() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Dragon", 100, "MONSTER", null, null));
        service.updateCombatant(c.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, 2, null, null, null, null, null));

        CombatantDto after = service.useLegendaryResistance(c.id());
        assertThat(after.legendaryResistancesUsed()).isEqualTo(1);
    }

    @Test
    void shouldUndoLastAction() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Monster", 20, "MONSTER", null, null));
        int originalHp = c.currentHp();

        service.applyDamage(c.id(), -5);
        service.undo(enc.id());

        CombatantDto reverted = service.getCombatant(c.id());
        assertThat(reverted.currentHp()).isEqualTo(originalHp);
    }

    @Test
    void shouldNotResetLegendaryResistancesOnRoundAdvance() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Dragon", 100, "MONSTER", null, null));
        CombatantDto d = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Other", 50, "MONSTER", null, null));
        service.setInitiative(c.id(), 20);
        service.setInitiative(d.id(), 10);

        service.updateCombatant(c.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, null, null, null, null, null, null, 3, null, 2, null, null, null, null, null, null));

        CombatantDto afterUpdate = service.getCombatant(c.id());
        assertThat(afterUpdate.legendaryActionsUsed()).isEqualTo(3);
        assertThat(afterUpdate.legendaryResistancesUsed()).isEqualTo(2);

        service.startCombat(enc.id(), false);
        // startCombat resets legendary actions on the active combatant (c)
        afterUpdate = service.getCombatant(c.id());
        assertThat(afterUpdate.legendaryActionsUsed()).isEqualTo(0);

        service.updateCombatant(c.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, null, null, null, null, null, null, 3, null, 2, null, null, null, null, null, null));

        afterUpdate = service.getCombatant(c.id());
        assertThat(afterUpdate.legendaryActionsUsed()).isEqualTo(3);

        service.nextTurn(enc.id()); // advance to d (index 1)
        service.nextTurn(enc.id()); // wrap to c (index 0), round 2
        EncounterDto round2 = service.nextTurn(enc.id());
        assertThat(round2.round()).isEqualTo(2);

        CombatantDto dragon = service.getCombatant(c.id());
        assertThat(dragon.legendaryActionsUsed()).isEqualTo(0);
        assertThat(dragon.legendaryResistancesUsed()).isEqualTo(2);
    }

    @Test
    void shouldPreserveConcentrationOnUndoAfterPassedCheck() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Wizard", 20, "NPC", null, null));
        service.setConcentration(c.id(), "Haste");

        assertThat(service.getCombatant(c.id()).concentratingOn()).isEqualTo("Haste");

        service.applyDamage(c.id(), -5);
        CombatantDto afterDmg = service.getCombatant(c.id());
        assertThat(afterDmg.concentrationCheckPending()).isTrue();

        service.resolveConcentrationCheck(c.id(), true);
        CombatantDto afterResolve = service.getCombatant(c.id());
        assertThat(afterResolve.concentrationCheckPending()).isFalse();
        assertThat(afterResolve.concentratingOn()).isEqualTo("Haste");

        // Use heal to add an undoable action without triggering another concentration check
        service.applyDamage(c.id(), 5);

        service.undo(enc.id());

        CombatantDto reverted = service.getCombatant(c.id());
        assertThat(reverted.concentrationCheckPending()).isFalse();
        assertThat(reverted.concentratingOn()).isEqualTo("Haste");
    }

    @Test
    void shouldNotMarkDefeatedOnUndoWhenHpAboveZero() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 20, "MONSTER", null, null));

        service.markDefeated(c.id(), true);
        service.applyDamage(c.id(), -5);

        service.undo(enc.id());

        CombatantDto reverted = service.getCombatant(c.id());
        assertThat(reverted.currentHp()).isEqualTo(20);
        assertThat(reverted.defeated()).isFalse();
    }

    @Test
    void shouldUndoSetHp() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Target", 30, "MONSTER", null, null));

        service.setHp(c.id(), 15, 0);
        service.undo(enc.id());

        CombatantDto reverted = service.getCombatant(c.id());
        assertThat(reverted.currentHp()).isEqualTo(30);
    }

    @Test
    void shouldRemoveExpiredConditionOnUndo() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto a = service.addCombatant(enc.id(),
                new CombatantCreateRequest("A", 10, "NPC", null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("B", 10, "NPC", null, null));
        service.setInitiative(a.id(), 10);
        service.setInitiative(b.id(), 5);

        service.toggleCondition(a.id(), "poisoned", 1);
        // startCombat sets activeTurnIndex=0 (a, initiative 10)
        service.startCombat(enc.id(), false);
        // Advance past both combatants (idx 0 → 1)
        service.nextTurn(enc.id());
        // Wrap back to idx 0: increments round → triggers tickConditionDurations
        EncounterDto afterWrap = service.nextTurn(enc.id());
        assertThat(afterWrap.round()).isEqualTo(2);

        CombatantDto afterTick = service.getCombatant(a.id());
        assertThat(afterTick.conditions()).isEmpty();

        service.undo(enc.id());

        CombatantDto reverted = service.getCombatant(a.id());
        assertThat(reverted.conditions()).isEmpty();
    }

    @Test
    void shouldUndoAddCombatant() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Temporary", 10, "MONSTER", null, null));
        int countBefore = service.getCombatants(enc.id()).size();

        service.undo(enc.id());

        int countAfter = service.getCombatants(enc.id()).size();
        assertThat(countAfter).isLessThan(countBefore);
    }

    @Test
    void shouldUndoRemoveCombatant() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto c = service.addCombatant(enc.id(),
                new CombatantCreateRequest("ToBeRemoved", 10, "MONSTER", null, null));
        UUID removedId = c.id();

        service.removeCombatant(c.id());

        CombatantDto removed = service.getCombatant(removedId);
        assertThat(removed.hidden()).isTrue();

        service.undo(enc.id());

        CombatantDto restored = service.getCombatant(removedId);
        assertThat(restored).isNotNull();
        assertThat(restored.currentHp()).isEqualTo(10);
    }

    @Test
    void shouldRebuildSortOrderForUndoRespectTieBreaker() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto a = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Alice", 10, "NPC", null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Bob", 10, "NPC", null, null));

        // Set tieBreaker BEFORE initiative (so resortCombatants captures the right order)
        em.createQuery("update Combatant c set c.tieBreaker = :tb where c.id = :id")
                .setParameter("tb", 5).setParameter("id", a.id()).executeUpdate();
        em.createQuery("update Combatant c set c.tieBreaker = :tb where c.id = :id")
                .setParameter("tb", 10).setParameter("id", b.id()).executeUpdate();
        em.flush();
        em.clear();

        service.setInitiative(a.id(), 10);
        service.setInitiative(b.id(), 10);

        service.applyDamage(a.id(), -5);
        service.undo(enc.id());

        var combatants = service.getCombatants(enc.id());
        assertThat(combatants).hasSize(2);
        assertThat(combatants.get(0).name()).isEqualTo("Bob");
        assertThat(combatants.get(1).name()).isEqualTo("Alice");
    }

    @Test
    void undoDoesNotDiscardManualReordering() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto a = service.addCombatant(enc.id(),
                new CombatantCreateRequest("A", 10, "NPC", null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("B", 10, "NPC", null, null));
        service.setInitiative(a.id(), 20);
        service.setInitiative(b.id(), 10);

        // Manually reorder: B (index 0) before A (index 1)
        service.reorderCombatants(enc.id(), List.of(b.id(), a.id()));

        // Do some other action
        service.applyDamage(a.id(), -5);

        // Undo damage
        service.undo(enc.id());

        // Verify manual reorder preserved (B before A)
        var combatants = service.getCombatants(enc.id());
        assertThat(combatants).hasSize(2);
        assertThat(combatants.get(0).id()).isEqualTo(b.id());
        assertThat(combatants.get(1).id()).isEqualTo(a.id());
    }

    @Test
    void undoPreservesTheMaximumRetainedLogSequenceWhenHistoryHasGaps() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Gapped Log", null));
        service.activate(enc.id());
        CombatantDto combatant = service.addCombatant(enc.id(),
                new CombatantCreateRequest("A", 10, "NPC", null, null));
        service.applyDamage(combatant.id(), 1);

        var log = combatLogRepo.findByEncounterIdOrderBySequenceAsc(enc.id());
        log.get(1).setSequence(4);
        log.get(2).setSequence(5);
        combatLogRepo.saveAll(log);
        Encounter encounter = em.find(Encounter.class, enc.id());
        encounter.setLogSequence(5);
        em.flush();
        em.clear();

        service.undo(enc.id());
        service.setInitiative(combatant.id(), 12);

        var afterUndo = combatLogRepo.findByEncounterIdOrderBySequenceAsc(enc.id());
        assertThat(afterUndo).extracting(CombatLogEntry::getSequence)
                .doesNotHaveDuplicates()
                .isSorted();
        assertThat(em.find(Encounter.class, enc.id()).getLogSequence())
                .isEqualTo(afterUndo.getLast().getSequence());
    }

    @Test
    void undoDoesNotDiscardInitiativeBasedReordering() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto a = service.addCombatant(enc.id(),
                new CombatantCreateRequest("A", 10, "NPC", null, null));
        CombatantDto b = service.addCombatant(enc.id(),
                new CombatantCreateRequest("B", 10, "NPC", null, null));

        // A initiative 5, B initiative 10 — A will be before B after resort
        service.setInitiative(a.id(), 5);
        service.setInitiative(b.id(), 10);
        var before = service.getCombatants(enc.id());

        // Now change B's initiative to 15 so it goes first
        service.setInitiative(b.id(), 15);

        // Undo the last initiative change (B=15 → should revert to B=10)
        service.undo(enc.id());

        // B should be back to initiative 10, order should match
        var after = service.getCombatants(enc.id());
        assertThat(after).hasSize(2);
        assertThat(after.get(0).id()).isEqualTo(before.get(0).id());
        assertThat(after.get(1).id()).isEqualTo(before.get(1).id());
    }

    @Test
    void shouldLogCombatantAdded() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 7, "MONSTER", null, null));

        var logEntries = combatLogRepo.findByEncounterIdOrderBySequenceAsc(enc.id());
        assertThat(logEntries).anyMatch(e ->
                e.getType() == CombatLogEntry.EntryType.COMBATANT_ADDED
                        && e.getPayload().contains("\"name\":\"Goblin\""));
    }

    @Test
    void shouldPreviousTurnCrossRoundBoundary() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        service.addCombatant(enc.id(),
                new CombatantCreateRequest("A", 10, "NPC", null, null));
        service.addCombatant(enc.id(),
                new CombatantCreateRequest("B", 10, "NPC", null, null));
        service.setInitiative(service.getCombatants(enc.id()).get(0).id(), 10);
        service.setInitiative(service.getCombatants(enc.id()).get(1).id(), 5);

        service.startCombat(enc.id(), false);
        // startCombat sets activeTurnIndex=0 (A)
        service.nextTurn(enc.id()); // advance to B (index 1)
        EncounterDto round2 = service.nextTurn(enc.id()); // wrap to A (index 0), round 2
        assertThat(round2.round()).isEqualTo(2);

        EncounterDto prev = service.previousTurn(enc.id());
        assertThat(prev.round()).isEqualTo(1);

        var logEntries = combatLogRepo.findByEncounterIdOrderBySequenceAsc(enc.id());
        assertThat(logEntries).anyMatch(e -> e.getType() == CombatLogEntry.EntryType.TURN_END);
    }

    @Test
    void groupMembersShareTurnSlot() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto leader = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin Leader", 10, "MONSTER", null, null));
        CombatantDto goblin1 = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin 1", 10, "MONSTER", null, null));
        CombatantDto goblin2 = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin 2", 10, "MONSTER", null, null));
        CombatantDto fighter = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Fighter", 10, "PC", null, null));

        String groupId = "goblin-group";
        service.updateCombatant(leader.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, groupId, true, null, null, null, null, null, null, null, null, null, null, null, null, null));
        service.updateCombatant(goblin1.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, groupId, false, null, null, null, null, null, null, null, null, null, null, null, null, null));
        service.updateCombatant(goblin2.id(), new EncounterService.CombatantUpdateRequest(null, null, null, null, null, null,
                null, groupId, false, null, null, null, null, null, null, null, null, null, null, null, null, null));

        service.startCombat(enc.id(), true);
        // startCombat sets activeTurnIndex=0 (leader)
        service.setActiveTurn(enc.id(), leader.id());

        // Should skip goblin1 and goblin2, stop at fighter (index 3)
        EncounterDto turn1 = service.nextTurn(enc.id());
        assertThat(turn1.activeTurnIndex()).isEqualTo(3);
        assertThat(turn1.round()).isEqualTo(1);

        // Wraps back to leader (index 0) and advances round to 2
        EncounterDto turn2 = service.nextTurn(enc.id());
        assertThat(turn2.activeTurnIndex()).isEqualTo(0);
        assertThat(turn2.round()).isEqualTo(2);
    }

    @Test
    void cannotUndoEncounterActivated() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        assertThatThrownBy(() -> service.undo(enc.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boundary");
    }

    @Test
    void canUndoDamageAfterActivate() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        CombatantDto goblin = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 10, "MONSTER", null, null));
        service.activate(enc.id());
        service.applyDamage(goblin.id(), 2);
        service.undo(enc.id());
        assertThat(service.getCombatant(goblin.id()).currentHp()).isEqualTo(10);
    }
}
