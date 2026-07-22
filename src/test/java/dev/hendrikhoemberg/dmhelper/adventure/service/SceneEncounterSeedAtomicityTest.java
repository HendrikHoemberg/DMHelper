package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class SceneEncounterSeedAtomicityTest {

    @Autowired private SceneEncounterSeedService seeder;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CombatantRepository combatantRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SceneParticipantRepository participantRepository;
    @MockitoSpyBean private EncounterService encounterService;

    private PopulatedCampaignFixture.Seeded seeded;

    @BeforeEach
    void setUp() {
        seeded = fixture.seed();
    }

    @Test
    void participantFailureRollsBackEncounterCombatantsAndSceneLink() {
        long encountersBefore = encounterRepository.count();
        long combatantsBefore = combatantRepository.count();
        SceneParticipant template = participantRepository
                .findBySceneIdOrderBySortOrderAsc(seeded.richSceneId()).stream()
                .filter(participant -> participant.getStatBlock() != null)
                .findFirst()
                .orElseThrow();
        SceneParticipant secondResolved = new SceneParticipant();
        secondResolved.setScene(template.getScene());
        secondResolved.setDisplayName("Second resolved participant");
        secondResolved.setQuantity(1);
        secondResolved.setDisposition(template.getDisposition());
        secondResolved.setStatBlock(template.getStatBlock());
        secondResolved.setSortOrder(99);
        participantRepository.save(secondResolved);

        AtomicInteger additions = new AtomicInteger();
        doAnswer(invocation -> {
            if (additions.incrementAndGet() == 2) {
                throw new IllegalStateException("forced participant failure");
            }
            return invocation.callRealMethod();
        }).when(encounterService).addFromLibrary(any(UUID.class),
                any(EncounterService.AddFromLibraryRequest.class));

        assertThatThrownBy(() ->
                seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced participant failure");

        assertThat(encounterRepository.count()).isEqualTo(encountersBefore);
        assertThat(combatantRepository.count()).isEqualTo(combatantsBefore);
        assertThat(sceneRepository.findById(seeded.richSceneId()).orElseThrow().getEncounter())
                .isNull();
    }

    @Test
    void concurrentRequestsReturnOneEncounterWithoutDuplicateCombatants() throws Exception {
        long encountersBefore = encounterRepository.count();
        long combatantsBefore = combatantRepository.count();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch createCalls = new CountDownLatch(2);
        doAnswer(invocation -> {
            createCalls.countDown();
            createCalls.await(1, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(encounterService).create(eq(seeded.campaignId()),
                any(EncounterService.CreateRequest.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());
            });
            var second = executor.submit(() -> {
                start.await();
                return seeder.seedFromScene(seeded.campaignId(), seeded.richSceneId());
            });
            start.countDown();

            var results = java.util.List.of(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(results).extracting(SceneEncounterSeedService.SeedResult::encounterId)
                    .containsOnly(results.getFirst().encounterId());
            assertThat(results).extracting(SceneEncounterSeedService.SeedResult::alreadyExisted)
                    .containsExactlyInAnyOrder(false, true);
        }

        assertThat(encounterRepository.count()).isEqualTo(encountersBefore + 1);
        assertThat(combatantRepository.count()).isEqualTo(combatantsBefore + 2);
    }
}
