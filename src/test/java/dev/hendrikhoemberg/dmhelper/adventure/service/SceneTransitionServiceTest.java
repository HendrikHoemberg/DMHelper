package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({SceneStructuredContentService.class, AdventureService.class, SceneTransitionService.class,
        SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class SceneTransitionServiceTest {

    @Autowired private SceneStructuredContentService structuredService;
    @Autowired private SceneTransitionService transitionService;
    @Autowired private AdventureService adventureService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SceneTransitionRepository transitionRepository;
    @Autowired private EntityManager em;
    @MockitoBean private SessionActivityRecorder sessionActivity;
    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    private Campaign campaign;
    private Campaign otherCampaign;
    private Scene scene;
    private Scene targetScene;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        campaign = campaignRepository.save(campaign);

        otherCampaign = new Campaign();
        otherCampaign.setName("Other");
        otherCampaign = campaignRepository.save(otherCampaign);

        var a = adventureService.createAdventure(campaign.getId(), "A", null, null);
        var ch = adventureService.createChapter(a.getId(), "Ch", null);
        scene = adventureService.createScene(ch.getId(), "Scene", null, null);
        targetScene = adventureService.createScene(ch.getId(), "Target", null, null);
    }

    @Test
    void choiceTransitionRequiresSameCampaignTargetAndRejectsExternal() {
        var cmd = new SceneStructuredContentService.SceneTransitionCommand(
                SceneTransitionKind.CHOICE, "Go to target", targetScene.getId(),
                null, null, null, null, 0);
        var transition = structuredService.addTransition(campaign.getId(), scene.getId(), cmd);
        assertThat(transition.getId()).isNotNull();
        assertThat(transition.getTargetScene().getId()).isEqualTo(targetScene.getId());

        assertThatThrownBy(() ->
                structuredService.addTransition(campaign.getId(), scene.getId(),
                        new SceneStructuredContentService.SceneTransitionCommand(
                                SceneTransitionKind.CHOICE, "Bad", null,
                                "http://external.com", null, null, null, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entranceExitAcceptsExactlyOneTargetOrExternal() {
        var entrance = structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.ENTRANCE, "From town", targetScene.getId(),
                        null, null, null, null, 0));
        assertThat(entrance.getKind()).isEqualTo(SceneTransitionKind.ENTRANCE);

        var exitWithExternal = structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.EXIT, "To dungeon 5", null,
                        "Overworld tile 5,3", null, null, null, 1));
        assertThat(exitWithExternal.getExternalDestination()).isEqualTo("Overworld tile 5,3");
    }

    @Test
    void entranceExitRejectsBothTargetAndExternal() {
        assertThatThrownBy(() ->
                structuredService.addTransition(campaign.getId(), scene.getId(),
                        new SceneStructuredContentService.SceneTransitionCommand(
                                SceneTransitionKind.ENTRANCE, "Bad", targetScene.getId(),
                                "external", null, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entranceExitRejectsNeitherTargetNorExternal() {
        assertThatThrownBy(() ->
                structuredService.addTransition(campaign.getId(), scene.getId(),
                        new SceneStructuredContentService.SceneTransitionCommand(
                                SceneTransitionKind.EXIT, "Bad", null,
                                null, null, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTargetSceneFromOtherCampaign() {
        var otherA = adventureService.createAdventure(otherCampaign.getId(), "OtherA", null, null);
        var otherCh = adventureService.createChapter(otherA.getId(), "OtherCh", null);
        var otherScene = adventureService.createScene(otherCh.getId(), "OtherScene", null, null);

        assertThatThrownBy(() ->
                structuredService.addTransition(campaign.getId(), scene.getId(),
                        new SceneStructuredContentService.SceneTransitionCommand(
                                SceneTransitionKind.CHOICE, "Bad", otherScene.getId(),
                                null, null, null, null, 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void transitionOrderAndPackageKeyCleanupOnDeletion() {
        var t1 = structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.CHOICE, "First", targetScene.getId(),
                        null, null, null, null, 0));
        structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.CHOICE, "Second", targetScene.getId(),
                        null, null, null, null, 1));
        var t3 = structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.CHOICE, "Third", targetScene.getId(),
                        null, null, null, null, 2));

        structuredService.deleteTransition(campaign.getId(), scene.getId(), t1.getId());
        em.flush();
        em.clear();

        var remaining = structuredService.getTransitions(campaign.getId(), scene.getId());
        assertThat(remaining).extracting(SceneTransition::getSortOrder).containsExactly(0, 1);
    }

    @Test
    void followTransitionChangesCursorAndRecordsActivity() {
        var transition = structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.CHOICE, "Forward", targetScene.getId(),
                        null, null, null, null, 0));

        var result = transitionService.followTransition(campaign.getId(), transition.getId());
        assertThat(result.getId()).isEqualTo(targetScene.getId());

        var campaignReloaded = campaignRepository.findById(campaign.getId()).orElseThrow();
        assertThat(campaignReloaded.getCurrentSceneId()).isEqualTo(targetScene.getId());

        verify(sessionActivity).sceneSelected(eq(campaign.getId()), any());
    }

    @Test
    void followTransitionDoesNotMutateStatus() {
        var transition = structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.CHOICE, "Forward", targetScene.getId(),
                        null, null, null, null, 0));
        targetScene.setStatus(SceneStatus.UNVISITED);
        sceneRepository.save(targetScene);
        em.flush();

        transitionService.followTransition(campaign.getId(), transition.getId());
        em.clear();

        assertThat(sceneRepository.findById(targetScene.getId()).orElseThrow().getStatus())
                .isEqualTo(SceneStatus.UNVISITED);
    }

    @Test
    void followTransitionRefusesExternalOnlyTransition() {
        var transition = structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.EXIT, "Leave", null,
                        "Overworld 5,3", null, null, null, 0));

        assertThatThrownBy(() ->
                transitionService.followTransition(campaign.getId(), transition.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void followTransitionRejectsTransitionFromOtherCampaign() {
        var transition = structuredService.addTransition(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneTransitionCommand(
                        SceneTransitionKind.CHOICE, "Fwd", targetScene.getId(),
                        null, null, null, null, 0));

        assertThatThrownBy(() ->
                transitionService.followTransition(otherCampaign.getId(), transition.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void editorialStepCurrentSceneUnchanged() {
        adventureService.setCurrentScene(campaign.getId(), scene.getId());

        var stepped = adventureService.stepCurrentScene(campaign.getId(), 1);
        assertThat(stepped).isPresent();
        assertThat(stepped.get().getId()).isEqualTo(targetScene.getId());
    }
}
