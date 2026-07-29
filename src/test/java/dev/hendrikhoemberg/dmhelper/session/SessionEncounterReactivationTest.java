package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionEncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SessionEncounterReactivationTest {

    @Autowired private SessionEncounterService sessionEncounters;
    @Autowired private EncounterService encounters;
    @Autowired private EncounterRepository encounterRepo;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    @Test
    void reRunningAFinishedEncounterReactivatesItInsteadOfThrowing() {
        UUID campaignId = fixtures.campaignWithRunningSession();
        UUID encounterId = fixtures.plannedEncounterWithOneCombatant(campaignId);

        sessionEncounters.activate(campaignId, encounterId, null);
        Encounter running = encounterRepo.findById(encounterId).orElseThrow();
        running.setCombatPhase(Encounter.CombatPhase.RUNNING);
        running.setRound(4);
        running.setActiveTurnIndex(0);
        encounterRepo.saveAndFlush(running);
        encounters.endEncounter(encounterId);
        assertThat(encounterRepo.findById(encounterId).orElseThrow().getStatus())
                .isEqualTo(Encounter.Status.DONE);

        var result = sessionEncounters.activate(campaignId, encounterId, null);

        assertThat(result.status()).isEqualTo("ACTIVE");
        Encounter reopened = encounterRepo.findById(encounterId).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(Encounter.Status.ACTIVE);
        assertThat(reopened.getCombatPhase()).isEqualTo(Encounter.CombatPhase.SETUP);
        assertThat(reopened.getRound()).isZero();
        assertThat(reopened.getActiveTurnIndex()).isEqualTo(-1);
    }
}
