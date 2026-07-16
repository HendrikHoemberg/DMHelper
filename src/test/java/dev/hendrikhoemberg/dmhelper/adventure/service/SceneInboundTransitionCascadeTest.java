package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SceneInboundTransitionCascadeTest {

    @Autowired private EntityManager em;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SceneTransitionRepository sceneTransitionRepository;

    @Test
    void deletingSceneSetsNullOnInboundTransitionReferences() {
        Campaign campaign = new Campaign();
        campaign.setName("Test");
        em.persist(campaign);

        var adv = new dev.hendrikhoemberg.dmhelper.adventure.data.Adventure();
        adv.setCampaign(campaign);
        adv.setName("Adv");
        adv.setSortOrder(0);
        em.persist(adv);

        var ch = new Chapter();
        ch.setAdventure(adv);
        ch.setTitle("Ch");
        ch.setSortOrder(0);
        em.persist(ch);

        var source = new Scene();
        source.setChapter(ch);
        source.setTitle("Source");
        source.setSortOrder(0);
        em.persist(source);

        var target = new Scene();
        target.setChapter(ch);
        target.setTitle("Target");
        target.setSortOrder(1);
        em.persist(target);

        var transition = new SceneTransition();
        transition.setScene(source);
        transition.setKind(SceneTransitionKind.CHOICE);
        transition.setTargetScene(target);
        transition.setSortOrder(0);
        em.persist(transition);
        em.flush();

        em.clear();
        sceneRepository.deleteById(target.getId());
        sceneRepository.flush();

        var refs = sceneTransitionRepository.findBySceneIdOrderBySortOrderAsc(source.getId());
        assertThat(refs).isNotEmpty();
        assertThat(refs.get(0).getTargetScene()).isNull();
    }
}
