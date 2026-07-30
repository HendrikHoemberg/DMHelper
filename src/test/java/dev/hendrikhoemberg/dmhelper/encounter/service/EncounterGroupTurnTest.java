package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantUpdateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({EncounterService.class, EncounterPlacementService.class, CombatDifficultyCalculator.class,
        GameMapService.class, DiceEngine.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
        dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver.class,
        dev.hendrikhoemberg.dmhelper.config.MarkdownUtil.class})
class EncounterGroupTurnTest {

    @MockitoBean dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;
    @Autowired EncounterService service;
    @Autowired jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Group Turns");
        em.persist(campaign);
        em.flush();
    }

    private static CombatantUpdateRequest group(String groupId, boolean leader) {
        return new CombatantUpdateRequest(null, null, null, null, null, null, null,
                groupId, leader, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    @Test
    void groupRepresentationMovesToASurvivorWhenTheLeaderFalls() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        CombatantDto leader = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin 1", 7, "MONSTER", null, null));
        CombatantDto mook = service.addCombatant(enc.id(),
                new CombatantCreateRequest("Goblin 2", 7, "MONSTER", null, null));
        service.updateCombatant(leader.id(), group("goblins", true));
        service.updateCombatant(mook.id(), group("goblins", false));
        service.setInitiative(leader.id(), 20);
        service.setInitiative(mook.id(), 18);

        assertThat(service.getCombatants(enc.id()))
                .filteredOn(CombatantDto::actsForGroup)
                .extracting(CombatantDto::name)
                .containsExactly("Goblin 1");

        service.markDefeated(leader.id(), true);

        assertThat(service.getCombatants(enc.id()))
                .filteredOn(CombatantDto::actsForGroup)
                .extracting(CombatantDto::name)
                .containsExactly("Goblin 2");
    }

    @Test
    void everyMemberOfAGroupSharesOneInitiativeRoll() {
        EncounterDto enc = service.create(campaign.getId(), new CreateRequest("Enc", null));
        service.activate(enc.id());
        String groupId = "zombies";
        for (int i = 1; i <= 5; i++) {
            CombatantDto c = service.addCombatant(enc.id(),
                    new CombatantCreateRequest("Zombie " + i, 22, "MONSTER", null, null));
            service.updateCombatant(c.id(), group(groupId, i == 1));
        }

        service.rollUnsetNpcInitiatives(enc.id());

        assertThat(service.getCombatants(enc.id()))
                .extracting(CombatantDto::initiative)
                .as("one group, one initiative")
                .containsOnly(service.getCombatants(enc.id()).get(0).initiative());
    }
}
