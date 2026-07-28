package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWave;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWaveRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveTriggerKind;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({EncounterService.class, EncounterPlacementService.class, CombatDifficultyCalculator.class,
        SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
        dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver.class,
        dev.hendrikhoemberg.dmhelper.config.MarkdownUtil.class,
        EncounterInitiativeSetupServiceTest.MockConfig.class})
class EncounterInitiativeSetupServiceTest {

    @MockitoBean
    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @MockitoBean
    private DiceEngine diceEngine;

    @TestConfiguration
    static class MockConfig {
        @Bean
        TablePresentationService tablePresentationService() {
            return Mockito.mock(TablePresentationService.class);
        }
    }

    @Autowired private EncounterService service;
    @Autowired private EncounterWaveRepository waveRepo;
    @Autowired private jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Initiative Setup Campaign");
        em.persist(campaign);
        em.flush();
    }

    private CombatantDto add(UUID encounterId, String name, String kind) {
        return service.addCombatant(encounterId,
                new CombatantCreateRequest(name, 10, kind, null, null));
    }

    private EncounterDto activeEncounter(String name) {
        EncounterDto created = service.create(campaign.getId(), new CreateRequest(name, null));
        return service.activate(created.id());
    }

    @Test
    void activationEntersSetupWithoutStartingRoundOne() {
        EncounterDto activated = activeEncounter("Setup Test");
        assertThat(activated.status()).isEqualTo("ACTIVE");
        assertThat(activated.combatPhase()).isEqualTo("SETUP");
        assertThat(activated.round()).isEqualTo(0);
        assertThat(activated.activeTurnIndex()).isEqualTo(-1);
    }

    @Test
    void manualInitiativeDistinguishesUnsetZeroAndNegative() {
        EncounterDto enc = activeEncounter("Initiative Types");
        CombatantDto zero = add(enc.id(), "Zero", "PC");
        CombatantDto negative = add(enc.id(), "Negative", "PC");

        assertThat(service.getCombatant(zero.id()).initiative()).isNull();
        assertThat(service.getCombatant(negative.id()).initiative()).isNull();

        service.setInitiative(zero.id(), 0);
        service.setInitiative(negative.id(), -2);

        assertThat(service.getCombatant(zero.id()).initiative()).isEqualTo(0);
        assertThat(service.getCombatant(negative.id()).initiative()).isEqualTo(-2);
    }

    @Test
    void clearingManualInitiativeReturnsCombatantToUnset() {
        EncounterDto enc = activeEncounter("Clear Init");
        CombatantDto pc = add(enc.id(), "Hero", "PC");

        service.setInitiative(pc.id(), 18);
        assertThat(service.getCombatant(pc.id()).initiative()).isEqualTo(18);

        service.setInitiative(pc.id(), null);
        assertThat(service.getCombatant(pc.id()).initiative()).isNull();
    }

    @Test
    void autoRollTouchesOnlyUnsetNonPcCombatants() {
        EncounterDto enc = activeEncounter("Auto Roll");
        CombatantDto manualNpc = add(enc.id(), "Manual NPC", "MONSTER");
        CombatantDto unsetNpc = add(enc.id(), "Unset NPC", "MONSTER");
        CombatantDto unsetPc = add(enc.id(), "Unset PC", "PC");

        service.setInitiative(manualNpc.id(), 7);

        when(diceEngine.roll("d20")).thenReturn(new DiceResult("d20", List.of(), 0, 14, false, false));

        service.rollUnsetNpcInitiatives(enc.id());

        assertThat(service.getCombatant(manualNpc.id()).initiative()).isEqualTo(7);
        assertThat(service.getCombatant(unsetNpc.id()).initiative()).isEqualTo(14);
        assertThat(service.getCombatant(unsetPc.id()).initiative()).isNull();
        Mockito.verify(diceEngine, Mockito.times(1)).roll(anyString());
    }

    @Test
    void setupIncludesAndPreRollsPendingWaveCombatants() {
        EncounterDto enc = activeEncounter("Wave Pre-roll");
        var wave = service.createWave(enc.id(), new EncounterService.CreateWaveRequest(
                "reserve", "Reserve", WaveTriggerKind.MANUAL, null, null));
        CombatantDto reserveNpc = add(enc.id(), "Reserve Goblin", "MONSTER");
        service.updateCombatant(reserveNpc.id(), new EncounterService.CombatantUpdateRequest(
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                wave.id(), null, null, null));

        when(diceEngine.roll("d20")).thenReturn(new DiceResult("d20", List.of(), 0, 11, false, false));

        service.rollUnsetNpcInitiatives(enc.id());

        assertThat(service.getCombatants(enc.id())).isEmpty();
        var setupCombatants = service.getInitiativeSetupCombatants(enc.id());
        assertThat(setupCombatants).hasSize(1);
        assertThat(setupCombatants.getFirst().initiative()).isEqualTo(11);
    }

    @Test
    void startCombatRejectsUnacceptedUnsetValuesWithoutMutation() {
        EncounterDto enc = activeEncounter("Blocked");
        add(enc.id(), "Unset PC", "PC");

        assertThatThrownBy(() -> service.startCombat(enc.id(), false))
                .isInstanceOf(InitiativeSetupIncompleteException.class)
                .hasMessageContaining("1");

        EncounterDto unchanged = service.getById(enc.id());
        assertThat(unchanged.combatPhase()).isEqualTo("SETUP");
        assertThat(unchanged.round()).isEqualTo(0);
        assertThat(unchanged.activeTurnIndex()).isEqualTo(-1);
    }

    @Test
    void explicitAcceptanceStartsInDisplayedOrder() {
        EncounterDto enc = activeEncounter("Accepted");
        CombatantDto resolved = add(enc.id(), "Resolved", "MONSTER");
        add(enc.id(), "Unset PC", "PC");

        service.setInitiative(resolved.id(), 0);

        EncounterDto started = service.startCombat(enc.id(), true);
        assertThat(started.combatPhase()).isEqualTo("RUNNING");
        assertThat(started.round()).isEqualTo(1);
        assertThat(started.activeTurnIndex()).isEqualTo(0);
    }

    @Test
    void turnEndpointsRejectSetupPhase() {
        EncounterDto enc = activeEncounter("Not started");
        CombatantDto pc = add(enc.id(), "Fighter", "PC");
        service.setInitiative(pc.id(), 10);

        assertThatThrownBy(() -> service.nextTurn(enc.id()))
                .isInstanceOf(InitiativeSetupIncompleteException.class);
        assertThatThrownBy(() -> service.previousTurn(enc.id()))
                .isInstanceOf(InitiativeSetupIncompleteException.class);
    }

    @Test
    void turnEndpointsKeepLifecycleErrorsDistinctFromSetupConflicts() {
        EncounterDto planned = service.create(campaign.getId(), new CreateRequest("Still planned", null));
        add(planned.id(), "Fighter", "PC");

        assertThatThrownBy(() -> service.nextTurn(planned.id()))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(InitiativeSetupIncompleteException.class);
    }

    @Test
    void orderingPlacesUnsetLastAndPreservesZeroAndNegative() {
        EncounterDto enc = activeEncounter("Ordering");
        CombatantDto unset = add(enc.id(), "Unset", "PC");
        CombatantDto zero = add(enc.id(), "Zero", "PC");
        CombatantDto negative = add(enc.id(), "Negative", "PC");

        service.setInitiative(zero.id(), 0);
        service.setInitiative(negative.id(), -1);

        service.startCombat(enc.id(), true);

        var combatants = service.getCombatants(enc.id());
        assertThat(combatants).extracting(CombatantDto::name)
                .containsExactly("Zero", "Negative", "Unset");
    }

    @Test
    void changingInitiativeDuringCombatPreservesActiveCombatantIdentity() {
        EncounterDto enc = activeEncounter("Reorder During Combat");
        CombatantDto a = add(enc.id(), "A", "PC");
        CombatantDto b = add(enc.id(), "B", "PC");

        service.setInitiative(a.id(), 20);
        service.setInitiative(b.id(), 10);

        EncounterDto started = service.startCombat(enc.id(), false);
        UUID activeId = service.getCombatants(enc.id()).get(started.activeTurnIndex()).id();

        service.setInitiative(b.id(), 25);

        EncounterDto afterChange = service.getById(enc.id());
        UUID newActiveId = service.getCombatants(enc.id()).get(afterChange.activeTurnIndex()).id();
        assertThat(newActiveId).isEqualTo(activeId);
    }

    @Test
    void manuallyReorderingDuringCombatPreservesActiveCombatantIdentity() {
        EncounterDto enc = activeEncounter("Manual Reorder During Combat");
        CombatantDto a = add(enc.id(), "A", "PC");
        CombatantDto b = add(enc.id(), "B", "PC");

        service.setInitiative(a.id(), 20);
        service.setInitiative(b.id(), 10);
        service.startCombat(enc.id(), false);

        service.reorderCombatants(enc.id(), List.of(b.id(), a.id()));

        EncounterDto reordered = service.getById(enc.id());
        UUID activeId = service.getCombatants(enc.id()).get(reordered.activeTurnIndex()).id();
        assertThat(activeId).isEqualTo(a.id());
    }

    @Test
    void undoAfterMidCombatInitiativeChangePreservesActiveCombatantIdentity() {
        EncounterDto enc = activeEncounter("Undo Reorder During Combat");
        CombatantDto a = add(enc.id(), "A", "PC");
        CombatantDto b = add(enc.id(), "B", "PC");
        service.setInitiative(a.id(), 20);
        service.setInitiative(b.id(), 10);
        service.startCombat(enc.id(), false);

        service.setInitiative(b.id(), 25);
        service.applyDamage(a.id(), 1);
        service.undo(enc.id());

        EncounterDto restored = service.getById(enc.id());
        UUID activeId = service.getCombatants(enc.id()).get(restored.activeTurnIndex()).id();
        assertThat(activeId).isEqualTo(a.id());
    }

    @Test
    void nullableInitiativeRoundTripsThroughUndoEvidence() {
        EncounterDto enc = activeEncounter("Undo Init");
        CombatantDto pc = add(enc.id(), "Hero", "PC");

        service.setInitiative(pc.id(), 0);
        service.setInitiative(pc.id(), null);
        service.undo(enc.id());

        assertThat(service.getCombatant(pc.id()).initiative()).isZero();
    }

    @Test
    void undoingCombatStartReturnsToRoundZeroSetup() {
        EncounterDto enc = activeEncounter("Undo Start");
        CombatantDto pc = add(enc.id(), "Hero", "PC");
        service.setInitiative(pc.id(), 12);
        service.startCombat(enc.id(), false);

        service.undo(enc.id());

        EncounterDto setup = service.getById(enc.id());
        assertThat(setup.combatPhase()).isEqualTo("SETUP");
        assertThat(setup.round()).isZero();
        assertThat(setup.activeTurnIndex()).isEqualTo(-1);
    }
}
