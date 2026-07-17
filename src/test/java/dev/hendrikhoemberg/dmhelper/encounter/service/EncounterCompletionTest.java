package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
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

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({EncounterService.class, CombatDifficultyCalculator.class, GameMapService.class, DiceEngine.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class, EncounterCompletionTest.MockConfig.class})
class EncounterCompletionTest {

    @MockitoBean
    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @TestConfiguration
    static class MockConfig {
        @Bean
        TablePresentationService tablePresentationService() {
            return Mockito.mock(TablePresentationService.class);
        }
    }

    @Autowired private EncounterService service;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void endEncounterBuildsSummaryWithDefeatedCount() {
        var enc = service.create(campaign.getId(), new CreateRequest("Test", null));
        service.activate(enc.id());
        var goblin = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin", 10, "MONSTER", null, null, null));
        service.applyDamage(goblin.id(), -10);
        var result = service.endEncounterWithSummary(enc.id());
        assertThat(result.summary().defeatedCount()).isEqualTo(1);
        assertThat(result.summary().rounds()).isGreaterThanOrEqualTo(1);
        assertThat(result.encounter().status()).isEqualTo("DONE");
    }

    @Test
    void updatePrepPersistsAndReadsBack() {
        var enc = service.create(campaign.getId(), new CreateRequest("Prep Test", null));
        var prep = new EncounterPrep("Flank", "Flee at half", "Offer info", "Dark cave", "LMOP p12", null, "goblin-ambush");
        service.updatePrep(enc.id(), prep);
        var reloaded = service.getPrep(enc.id());
        assertThat(reloaded.tactics()).isEqualTo("Flank");
        assertThat(reloaded.sceneKey()).isEqualTo("goblin-ambush");
    }

    @Test
    void updateRewardsPersistsAndReadsBack() {
        var enc = service.create(campaign.getId(), new CreateRequest("Rewards Test", null));
        var rewards = new EncounterRewards(500, 100, List.of(), List.of(), List.of(), "Test rewards");
        service.updateRewards(enc.id(), rewards);
        var reloaded = service.getRewards(enc.id());
        assertThat(reloaded.xpTotal()).isEqualTo(500);
        assertThat(reloaded.xpPerPc()).isEqualTo(100);
    }
}
