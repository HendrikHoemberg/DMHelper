package dev.hendrikhoemberg.dmhelper.adventure.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StructuredAdventurePersistenceTest {

    @Autowired private EntityManager em;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SceneSectionRepository sceneSectionRepository;
    @Autowired private SceneCheckRepository sceneCheckRepository;
    @Autowired private SceneParticipantRepository sceneParticipantRepository;
    @Autowired private SceneTransitionRepository sceneTransitionRepository;
    @Autowired private SceneLinkRepository sceneLinkRepository;

    private Campaign campaign;
    private Scene scene;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Structured Adventure Test");
        em.persist(campaign);

        var adventure = new dev.hendrikhoemberg.dmhelper.adventure.data.Adventure();
        adventure.setCampaign(campaign);
        adventure.setName("Test Adventure");
        adventure.setSortOrder(0);
        em.persist(adventure);

        var chapter = new dev.hendrikhoemberg.dmhelper.adventure.data.Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Ch");
        chapter.setSortOrder(0);
        em.persist(chapter);

        scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Throne Room");
        scene.setSortOrder(0);
        scene.setSummary("A grand throne room");
        scene.setSourceLocator("src:curse-of-strahd/castle/throne-room");
        scene.setTags("boss, castle, strahd");
        scene.setMapRegionKey("region-castle-throne");
        em.persist(scene);
    }

    @Test
    void persistsAndReloadsNewSceneFields() {
        em.flush();
        em.clear();

        Scene reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getSummary()).isEqualTo("A grand throne room");
        assertThat(reloaded.getSourceLocator()).isEqualTo("src:curse-of-strahd/castle/throne-room");
        assertThat(reloaded.getTags()).isEqualTo("boss, castle, strahd");
        assertThat(reloaded.getMapRegionKey()).isEqualTo("region-castle-throne");
    }

    @Test
    void persistsAndReloadsSceneSection() {
        var section = new SceneSection();
        section.setScene(scene);
        section.setKind(SceneSectionKind.READ_ALOUD);
        section.setLabel("When you enter");
        section.setBody("The air is thick with dust.");
        section.setSourceLocator("src:curse-of-strahd/castle/throne-room/read-aloud");
        section.setSortOrder(0);
        em.persist(section);
        em.flush();
        em.clear();

        var sections = sceneSectionRepository.findBySceneIdOrderBySortOrderAsc(scene.getId());
        assertThat(sections).hasSize(1);
        var loaded = sections.get(0);
        assertThat(loaded.getKind()).isEqualTo(SceneSectionKind.READ_ALOUD);
        assertThat(loaded.getLabel()).isEqualTo("When you enter");
        assertThat(loaded.getBody()).isEqualTo("The air is thick with dust.");
        assertThat(loaded.getSourceLocator()).isEqualTo("src:curse-of-strahd/castle/throne-room/read-aloud");
        assertThat(loaded.getSortOrder()).isZero();
        assertThat(loaded.getScene().getId()).isEqualTo(scene.getId());
    }

    @Test
    void persistsAndReloadsSceneCheck() {
        var check = new SceneCheck();
        check.setScene(scene);
        check.setLabel("Spot the trap");
        check.setAbility("WIS");
        check.setSkill("Perception");
        check.setDc(15);
        check.setVisibility(SceneCheckVisibility.PLAYER_FACING);
        check.setSuccess("You spot the pressure plate.");
        check.setFailure("You step on it.");
        check.setPartial("You notice something off.");
        check.setRuleScope("dnd5e");
        check.setRuleRuleset("core");
        check.setRuleSourceKey("skill:perception");
        check.setSourceLocator("src:curse-of-strahd/castle/throne-room/trap");
        check.setSortOrder(0);
        em.persist(check);
        em.flush();
        em.clear();

        var checks = sceneCheckRepository.findBySceneIdOrderBySortOrderAsc(scene.getId());
        assertThat(checks).hasSize(1);
        var loaded = checks.get(0);
        assertThat(loaded.getLabel()).isEqualTo("Spot the trap");
        assertThat(loaded.getAbility()).isEqualTo("WIS");
        assertThat(loaded.getSkill()).isEqualTo("Perception");
        assertThat(loaded.getDc()).isEqualTo(15);
        assertThat(loaded.getVisibility()).isEqualTo(SceneCheckVisibility.PLAYER_FACING);
        assertThat(loaded.getSuccess()).isEqualTo("You spot the pressure plate.");
        assertThat(loaded.getFailure()).isEqualTo("You step on it.");
        assertThat(loaded.getPartial()).isEqualTo("You notice something off.");
        assertThat(loaded.getRuleScope()).isEqualTo("dnd5e");
        assertThat(loaded.getRuleRuleset()).isEqualTo("core");
        assertThat(loaded.getRuleSourceKey()).isEqualTo("skill:perception");
        assertThat(loaded.getSourceLocator()).isEqualTo("src:curse-of-strahd/castle/throne-room/trap");
        assertThat(loaded.getSortOrder()).isZero();
        assertThat(loaded.getScene().getId()).isEqualTo(scene.getId());
    }

    @Test
    void persistsAndReloadsSceneParticipant() {
        var participant = new SceneParticipant();
        participant.setScene(scene);
        participant.setDisplayName("Strahd");
        participant.setQuantity(1);
        participant.setDisposition(SceneParticipantDisposition.HOSTILE);
        participant.setPlacementHint("On the throne");
        participant.setSourceLocator("src:curse-of-strahd/castle/throne-room/strahd");
        participant.setSortOrder(0);
        em.persist(participant);
        em.flush();
        em.clear();

        var participants = sceneParticipantRepository.findBySceneIdOrderBySortOrderAsc(scene.getId());
        assertThat(participants).hasSize(1);
        var loaded = participants.get(0);
        assertThat(loaded.getDisplayName()).isEqualTo("Strahd");
        assertThat(loaded.getQuantity()).isEqualTo(1);
        assertThat(loaded.getDisposition()).isEqualTo(SceneParticipantDisposition.HOSTILE);
        assertThat(loaded.getPlacementHint()).isEqualTo("On the throne");
        assertThat(loaded.getSourceLocator()).isEqualTo("src:curse-of-strahd/castle/throne-room/strahd");
        assertThat(loaded.getSortOrder()).isZero();
        assertThat(loaded.getScene().getId()).isEqualTo(scene.getId());
    }

    @Test
    void persistsAndReloadsSceneTransition() {
        var targetScene = new Scene();
        targetScene.setChapter(scene.getChapter());
        targetScene.setTitle("Courtyard");
        targetScene.setSortOrder(1);
        em.persist(targetScene);

        var transition = new SceneTransition();
        transition.setScene(scene);
        transition.setKind(SceneTransitionKind.CHOICE);
        transition.setLabel("Go to courtyard");
        transition.setTargetScene(targetScene);
        transition.setCondition("If the party defeats Strahd");
        transition.setDmNote("They hear wolves outside.");
        transition.setSourceLocator("src:curse-of-strahd/castle/throne-room/to-courtyard");
        transition.setSortOrder(0);
        em.persist(transition);
        em.flush();
        em.clear();

        var transitions = sceneTransitionRepository.findBySceneIdOrderBySortOrderAsc(scene.getId());
        assertThat(transitions).hasSize(1);
        var loaded = transitions.get(0);
        assertThat(loaded.getKind()).isEqualTo(SceneTransitionKind.CHOICE);
        assertThat(loaded.getLabel()).isEqualTo("Go to courtyard");
        assertThat(loaded.getTargetScene().getId()).isEqualTo(targetScene.getId());
        assertThat(loaded.getCondition()).isEqualTo("If the party defeats Strahd");
        assertThat(loaded.getDmNote()).isEqualTo("They hear wolves outside.");
        assertThat(loaded.getSourceLocator()).isEqualTo("src:curse-of-strahd/castle/throne-room/to-courtyard");
        assertThat(loaded.getSortOrder()).isZero();
        assertThat(loaded.getScene().getId()).isEqualTo(scene.getId());
    }

    @Test
    void persistsAndReloadsSceneLink() {
        var link = new SceneLink();
        link.setScene(scene);
        link.setRole(SceneLinkRole.REFERENCE);
        link.setTargetScope(SceneLinkTargetScope.PACKAGE);
        link.setTargetType("HANDOUT");
        link.setTargetId(UUID.randomUUID());
        link.setCatalogRuleset("dnd5e");
        link.setCatalogSourceKey("handout:letter-of-invitation");
        link.setDisplayText("Letter of Invitation");
        link.setCondition("Only if the party has the letter");
        link.setSortOrder(0);
        em.persist(link);
        em.flush();
        em.clear();

        var links = sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(scene.getId());
        assertThat(links).hasSize(1);
        var loaded = links.get(0);
        assertThat(loaded.getRole()).isEqualTo(SceneLinkRole.REFERENCE);
        assertThat(loaded.getTargetScope()).isEqualTo(SceneLinkTargetScope.PACKAGE);
        assertThat(loaded.getTargetType()).isEqualTo("HANDOUT");
        assertThat(loaded.getTargetId()).isNotNull();
        assertThat(loaded.getCatalogRuleset()).isEqualTo("dnd5e");
        assertThat(loaded.getCatalogSourceKey()).isEqualTo("handout:letter-of-invitation");
        assertThat(loaded.getDisplayText()).isEqualTo("Letter of Invitation");
        assertThat(loaded.getCondition()).isEqualTo("Only if the party has the letter");
        assertThat(loaded.getSortOrder()).isZero();
        assertThat(loaded.getScene().getId()).isEqualTo(scene.getId());
    }

    @Test
    void sectionSortOrdersAreUniquePerScene() {
        var s1 = new SceneSection();
        s1.setScene(scene);
        s1.setKind(SceneSectionKind.READ_ALOUD);
        s1.setSortOrder(0);
        em.persist(s1);

        var s2 = new SceneSection();
        s2.setScene(scene);
        s2.setKind(SceneSectionKind.DM_ADVICE);
        s2.setSortOrder(1);
        em.persist(s2);
        em.flush();

        var s3 = new SceneSection();
        s3.setScene(scene);
        s3.setKind(SceneSectionKind.SECRET);
        s3.setSortOrder(0);
        em.persist(s3);

        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.persistence.PersistenceException.class,
                () -> { em.flush(); em.clear(); }
        );
    }
}
