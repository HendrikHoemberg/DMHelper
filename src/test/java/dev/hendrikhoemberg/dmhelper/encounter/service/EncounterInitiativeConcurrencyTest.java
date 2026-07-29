package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({EncounterService.class, EncounterPlacementService.class, CombatDifficultyCalculator.class,
        SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
        dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver.class,
        dev.hendrikhoemberg.dmhelper.config.MarkdownUtil.class})
class EncounterInitiativeConcurrencyTest {

    @MockitoBean
    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @MockitoBean
    private DiceEngine diceEngine;

    @Autowired private EncounterService service;
    @Autowired private CombatLogEntryRepository combatLogRepo;
    @Autowired private EncounterRepository encounterRepo;
    @Autowired private jakarta.persistence.EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentInitiativeEditsKeepCombatLogSequencesStrictlyIncreasing() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        UUID[] encounterId = new UUID[1];
        List<UUID> combatantIds = transactions.execute(status -> {
            Campaign campaign = new Campaign();
            campaign.setName("Concurrent Initiative Campaign");
            em.persist(campaign);
            var encounter = service.create(campaign.getId(),
                    new EncounterService.CreateRequest("Concurrent Setup", null));
            service.activate(encounter.id());
            encounterId[0] = encounter.id();
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ids.add(service.addCombatant(encounter.id(),
                        new EncounterService.CombatantCreateRequest(
                                "Combatant " + i, 10, "PC", null, null)).id());
            }
            return ids;
        });

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(combatantIds.size());
        try {
            List<Future<?>> edits = new ArrayList<>();
            for (int i = 0; i < combatantIds.size(); i++) {
                int initiative = 10 + i;
                UUID combatantId = combatantIds.get(i);
                edits.add(executor.submit(() -> {
                    start.await();
                    service.setInitiative(combatantId, initiative);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> edit : edits) {
                edit.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        transactions.executeWithoutResult(status -> {
            var log = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId[0]);
            assertThat(log).extracting(entry -> entry.getSequence())
                    .doesNotHaveDuplicates()
                    .isSorted();
            assertThat(encounterRepo.findById(encounterId[0]).orElseThrow().getLogSequence())
                    .isEqualTo(log.getLast().getSequence());
        });
    }

    @Test
    void concurrentManualEditIsNeverOverwrittenByRollingUnsetNpcs() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        UUID[] encounterId = new UUID[1];
        UUID manualNpcId = transactions.execute(status -> {
            Campaign campaign = new Campaign();
            campaign.setName("Manual Versus Roll Campaign");
            em.persist(campaign);
            var encounter = service.create(campaign.getId(),
                    new EncounterService.CreateRequest("Concurrent Setup", null));
            service.activate(encounter.id());
            encounterId[0] = encounter.id();
            return service.addCombatant(encounter.id(),
                    new EncounterService.CombatantCreateRequest(
                            "Manual NPC", 10, "NPC", null, null)).id();
        });
        when(diceEngine.roll("d20"))
                .thenReturn(new DiceResult("d20", List.of(), 0, 3, false, false));

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> manual = executor.submit(() -> {
                start.await();
                service.setInitiative(manualNpcId, 17);
                return null;
            });
            Future<?> roll = executor.submit(() -> {
                start.await();
                service.rollUnsetNpcInitiatives(encounterId[0]);
                return null;
            });
            start.countDown();
            manual.get(10, TimeUnit.SECONDS);
            roll.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        transactions.executeWithoutResult(status ->
                assertThat(service.getCombatant(manualNpcId).initiative()).isEqualTo(17));
    }
}
