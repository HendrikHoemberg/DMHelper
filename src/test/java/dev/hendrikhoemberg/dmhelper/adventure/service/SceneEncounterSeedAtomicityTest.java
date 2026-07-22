package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class SceneEncounterSeedAtomicityTest {

    @Autowired private SceneEncounterSeedService seeder;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CombatantRepository combatantRepository;
    @Autowired private SceneRepository sceneRepository;
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
        doThrow(new IllegalStateException("forced participant failure"))
                .when(encounterService)
                .addFromLibrary(any(UUID.class),
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
}
