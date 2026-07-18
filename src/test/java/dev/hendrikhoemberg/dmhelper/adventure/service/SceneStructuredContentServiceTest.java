package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableReferenceResolver;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({SceneStructuredContentService.class, AdventureService.class, SceneTransitionService.class,
        SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class SceneStructuredContentServiceTest {

    @Autowired private SceneStructuredContentService structuredService;
    @Autowired private AdventureService adventureService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SceneSectionRepository sectionRepository;
    @Autowired private SceneCheckRepository checkRepository;
    @Autowired private SceneParticipantRepository participantRepository;
    @Autowired private SceneLinkRepository linkRepository;
    @Autowired private EntityManager em;
    @MockitoBean private SessionActivityRecorder sessionActivity;
    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;
    @MockitoBean private TableReferenceResolver referenceResolver;

    private Campaign campaign;
    private Campaign otherCampaign;
    private Scene scene;

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
    }

    @Test
    void updateMetadataUpdatesSummarySourceLocatorTagsAndMapRegion() {
        structuredService.updateMetadata(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneMetadataCommand(
                        "New summary", "SRD 5e p.42", "combat, dungeon", "region-1"));

        em.flush();
        em.clear();

        var reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getSummary()).isEqualTo("New summary");
        assertThat(reloaded.getSourceLocator()).isEqualTo("SRD 5e p.42");
        assertThat(reloaded.getTags()).isEqualTo("combat, dungeon");
        assertThat(reloaded.getMapRegionKey()).isEqualTo("region-1");
    }

    @Test
    void updateMetadataAcceptsNullSourceLocator() {
        structuredService.updateMetadata(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneMetadataCommand(
                        "Summary", null, "combat", null));

        em.flush();
        em.clear();

        var reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getSummary()).isEqualTo("Summary");
        assertThat(reloaded.getSourceLocator()).isNull();
        assertThat(reloaded.getMapRegionKey()).isNull();
    }

    @Test
    void updateMetadataRejectsSceneFromOtherCampaign() {
        assertThatThrownBy(() ->
                structuredService.updateMetadata(otherCampaign.getId(), scene.getId(),
                        new SceneStructuredContentService.SceneMetadataCommand(
                                "X", null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addAndUpdateAndDeleteSection() {
        var cmd = new SceneStructuredContentService.SceneSectionCommand(
                SceneSectionKind.READ_ALOUD, "Label", "Body text", "PHB p.50", 0);
        var section = structuredService.addSection(campaign.getId(), scene.getId(), cmd);
        assertThat(section.getId()).isNotNull();
        assertThat(section.getLabel()).isEqualTo("Label");
        assertThat(section.getBody()).isEqualTo("Body text");
        assertThat(section.getKind()).isEqualTo(SceneSectionKind.READ_ALOUD);
        assertThat(section.getSourceLocator()).isEqualTo("PHB p.50");
        assertThat(section.getSortOrder()).isEqualTo(0);

        var updateCmd = new SceneStructuredContentService.SceneSectionCommand(
                SceneSectionKind.DM_ADVICE, "Updated", "New body", null, 1);
        structuredService.updateSection(campaign.getId(), scene.getId(), section.getId(), updateCmd);

        em.flush();
        em.clear();

        var sections = structuredService.getSections(campaign.getId(), scene.getId());
        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).getKind()).isEqualTo(SceneSectionKind.DM_ADVICE);
        assertThat(sections.get(0).getBody()).isEqualTo("New body");
        assertThat(sections.get(0).getSourceLocator()).isNull();

        structuredService.deleteSection(campaign.getId(), scene.getId(), sections.get(0).getId());
        em.flush();
        assertThat(structuredService.getSections(campaign.getId(), scene.getId())).isEmpty();
    }

    @Test
    void sectionRequiresNonNullLabelAndBody() {
        assertThatThrownBy(() ->
                structuredService.addSection(campaign.getId(), scene.getId(),
                        new SceneStructuredContentService.SceneSectionCommand(
                                SceneSectionKind.READ_ALOUD, null, "body", null, 0)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                structuredService.addSection(campaign.getId(), scene.getId(),
                        new SceneStructuredContentService.SceneSectionCommand(
                                SceneSectionKind.READ_ALOUD, "label", null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renumberSectionsAfterDelete() {
        var s1 = structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.READ_ALOUD, "A", "body", null, 0));
        var s2 = structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.READ_ALOUD, "B", "body", null, 1));
        var s3 = structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.READ_ALOUD, "C", "body", null, 2));

        structuredService.deleteSection(campaign.getId(), scene.getId(), s2.getId());
        em.flush();
        em.clear();

        var remaining = structuredService.getSections(campaign.getId(), scene.getId());
        assertThat(remaining).extracting(SceneSection::getSortOrder).containsExactly(0, 1);
    }

    @Test
    void crudChecks() {
        var cmd = new SceneStructuredContentService.SceneCheckCommand(
                "Spot check", "wis", "perception", 15,
                SceneCheckVisibility.PLAYER_FACING, "Success", "Failure", null,
                null, null, null, "PHB p.178", 0);
        var check = structuredService.addCheck(campaign.getId(), scene.getId(), cmd);
        assertThat(check.getId()).isNotNull();

        var updateCmd = new SceneStructuredContentService.SceneCheckCommand(
                "Updated", "wis", "perception", 12,
                SceneCheckVisibility.DM_FACING, "S", "F", "P",
                null, null, null, null, 1);
        structuredService.updateCheck(campaign.getId(), scene.getId(), check.getId(), updateCmd);

        em.flush();
        em.clear();

        var checks = structuredService.getChecks(campaign.getId(), scene.getId());
        assertThat(checks).hasSize(1);
        assertThat(checks.get(0).getLabel()).isEqualTo("Updated");
        assertThat(checks.get(0).getDc()).isEqualTo(12);
        assertThat(checks.get(0).getPartial()).isEqualTo("P");

        structuredService.deleteCheck(campaign.getId(), scene.getId(), checks.get(0).getId());
        em.flush();
        assertThat(structuredService.getChecks(campaign.getId(), scene.getId())).isEmpty();
    }

    @Test
    void crudParticipants() {
        var cmd = new SceneStructuredContentService.SceneParticipantCommand(
                "Goblin", 3, SceneParticipantDisposition.HOSTILE,
                "behind door", null, null, "MM p.50", 0);
        var p = structuredService.addParticipant(campaign.getId(), scene.getId(), cmd);
        assertThat(p.getId()).isNotNull();
        assertThat(p.getQuantity()).isEqualTo(3);

        var updateCmd = new SceneStructuredContentService.SceneParticipantCommand(
                "Hobgoblin", 2, SceneParticipantDisposition.UNFRIENDLY,
                "on bridge", null, null, null, 1);
        structuredService.updateParticipant(campaign.getId(), scene.getId(), p.getId(), updateCmd);

        em.flush();
        em.clear();

        var participants = structuredService.getParticipants(campaign.getId(), scene.getId());
        assertThat(participants).hasSize(1);
        assertThat(participants.get(0).getDisplayName()).isEqualTo("Hobgoblin");

        structuredService.deleteParticipant(campaign.getId(), scene.getId(), participants.get(0).getId());
        em.flush();
        assertThat(structuredService.getParticipants(campaign.getId(), scene.getId())).isEmpty();
    }

    @Test
    void crudLinks() {
        var targetId = UUID.randomUUID();
        var cmd = new SceneStructuredContentService.SceneLinkCommand(
                SceneLinkRole.REFERENCE, SceneLinkTargetScope.PACKAGE, "HANDOUT", targetId,
                null, null, "The Letter", null, 0);
        var link = structuredService.addLink(campaign.getId(), scene.getId(), cmd);
        assertThat(link.getId()).isNotNull();

        var updateCmd = new SceneStructuredContentService.SceneLinkCommand(
                SceneLinkRole.REFERENCE, SceneLinkTargetScope.PACKAGE, "HANDOUT", targetId,
                null, null, "Updated Letter", "if found", 1);
        structuredService.updateLink(campaign.getId(), scene.getId(), link.getId(), updateCmd);

        em.flush();
        em.clear();

        var links = structuredService.getLinks(campaign.getId(), scene.getId());
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getDisplayText()).isEqualTo("Updated Letter");

        structuredService.deleteLink(campaign.getId(), scene.getId(), links.get(0).getId());
        em.flush();
        assertThat(structuredService.getLinks(campaign.getId(), scene.getId())).isEmpty();
    }

    @Test
    void addSectionRejectsSceneFromOtherCampaign() {
        var cmd = new SceneStructuredContentService.SceneSectionCommand(
                SceneSectionKind.READ_ALOUD, "L", "B", null, 0);
        assertThatThrownBy(() ->
                structuredService.addSection(otherCampaign.getId(), scene.getId(), cmd))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void tagsAreNormalizedThroughTagCodec() {
        structuredService.updateMetadata(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneMetadataCommand(
                        "Summary", null, "  combat , dungeon , trap  ", null));

        em.flush();
        em.clear();

        var reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getTags()).isEqualTo("combat, dungeon, trap");
    }

    @Test
    void acceptsEmptyTags() {
        structuredService.updateMetadata(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneMetadataCommand(
                        "Summary", null, "", null));
        em.flush();
        em.clear();
        assertThat(sceneRepository.findById(scene.getId()).orElseThrow().getTags()).isEqualTo("");
    }
}
