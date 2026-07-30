package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableReferenceResolver;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCardAssembler;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver;
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
        ThreatReferenceResolver.class,
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
    @Autowired private TrapRepository trapRepository;
    @Autowired private HazardRepository hazardRepository;
    @Autowired private EntityManager em;
    @MockitoBean private SessionActivityRecorder sessionActivity;
    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;
    @MockitoBean private TableReferenceResolver referenceResolver;
    @MockitoBean private ThreatCardAssembler threatCardAssembler;

    private Campaign campaign;
    private Campaign otherCampaign;
    private Scene scene;
    private Trap campaignTrap;
    private Trap otherCampaignTrap;
    private Hazard campaignHazard;

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

        campaignTrap = newTrap(campaign, "camp-spike", "Campaign Spike");
        otherCampaignTrap = newTrap(otherCampaign, "other-spike", "Other Spike");
        campaignHazard = newHazard(campaign, "camp-gas", "Campaign Gas");
    }

    private Trap newTrap(Campaign owner, String key, String name) {
        Trap trap = new Trap();
        trap.setSourceKey(key);
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(owner);
        trap.setName(name);
        trap.setDescription("A trap description long enough.");
        trap.setSeverity(ThreatSeverity.SETBACK);
        trap.setResetMode(ThreatResetMode.NONE);
        return trapRepository.save(trap);
    }

    private Hazard newHazard(Campaign owner, String key, String name) {
        Hazard hazard = new Hazard();
        hazard.setSourceKey(key);
        hazard.setSource(ContentSource.CUSTOM);
        hazard.setCampaign(owner);
        hazard.setName(name);
        hazard.setDescription("A hazard description long enough.");
        hazard.setSeverity(ThreatSeverity.SETBACK);
        hazard.setExposureMode(HazardExposureMode.ON_ENTER);
        return hazardRepository.save(hazard);
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
    void freeTextMapRegionKeyIsRejectedAtTheFormRatherThanAtExport() {
        assertThatThrownBy(() ->
                structuredService.updateMetadata(campaign.getId(), scene.getId(),
                        new SceneStructuredContentService.SceneMetadataCommand(
                                "Summary", null, null, "Cave, area 3")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("map region key");
    }

    @Test
    void slugMapRegionKeyIsAccepted() {
        structuredService.updateMetadata(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneMetadataCommand(
                        "Summary", null, null, "map-cragmaw-a3"));

        em.flush();
        em.clear();

        var reloaded = sceneRepository.findById(scene.getId()).orElseThrow();
        assertThat(reloaded.getMapRegionKey()).isEqualTo("map-cragmaw-a3");
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

    @Test
    void trapSectionAcceptsVisibleTrapButRejectsHazardAndCrossCampaign() {
        var accepted = structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.TRAP, "Spike", "Watch the floor", "PHB p.1", 0,
                        ThreatKind.TRAP, campaignTrap.getId()));
        assertThat(accepted.getThreatKind()).isEqualTo(ThreatKind.TRAP);
        assertThat(accepted.getThreatId()).isEqualTo(campaignTrap.getId());

        assertThatThrownBy(() -> structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.TRAP, "Bad", "body", null, 1,
                        ThreatKind.HAZARD, campaignHazard.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRAP");

        assertThatThrownBy(() -> structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.TRAP, "Foreign", "body", null, 1,
                        ThreatKind.TRAP, otherCampaignTrap.getId())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void hazardSectionAcceptsVisibleHazardButRejectsTrapAndCrossCampaign() {
        Hazard otherHazard = newHazard(otherCampaign, "other-gas", "Other Gas");

        var accepted = structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.HAZARD, "Gas", "Poison cloud", null, 0,
                        ThreatKind.HAZARD, campaignHazard.getId()));
        assertThat(accepted.getThreatKind()).isEqualTo(ThreatKind.HAZARD);
        assertThat(accepted.getThreatId()).isEqualTo(campaignHazard.getId());

        assertThatThrownBy(() -> structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.HAZARD, "Bad", "body", null, 1,
                        ThreatKind.TRAP, campaignTrap.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HAZARD");

        assertThatThrownBy(() -> structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.HAZARD, "Foreign", "body", null, 1,
                        ThreatKind.HAZARD, otherHazard.getId())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void otherSectionKindsRejectThreatReferences() {
        assertThatThrownBy(() -> structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.READ_ALOUD, "Label", "body", null, 0,
                        ThreatKind.TRAP, campaignTrap.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRAP/HAZARD");

        assertThatThrownBy(() -> structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.FEATURE, "Label", "body", null, 0,
                        ThreatKind.HAZARD, campaignHazard.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removingThreatReferencePreservesProseFields() {
        var section = structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.TRAP, "Spike", "Original body", "Loc p.9", 0,
                        ThreatKind.TRAP, campaignTrap.getId()));

        structuredService.updateSection(campaign.getId(), scene.getId(), section.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.TRAP, "Spike", "Original body", "Loc p.9", 0,
                        null, null));

        em.flush();
        em.clear();

        SceneSection reloaded = sectionRepository.findById(section.getId()).orElseThrow();
        assertThat(reloaded.getThreatKind()).isNull();
        assertThat(reloaded.getThreatId()).isNull();
        assertThat(reloaded.getLabel()).isEqualTo("Spike");
        assertThat(reloaded.getBody()).isEqualTo("Original body");
        assertThat(reloaded.getSourceLocator()).isEqualTo("Loc p.9");
    }

    @Test
    void nullThreatReferencePreservesProseOnlySections() {
        var section = structuredService.addSection(campaign.getId(), scene.getId(),
                new SceneStructuredContentService.SceneSectionCommand(
                        SceneSectionKind.TRAP, "Prose trap", "Just prose", null, 0));
        assertThat(section.getThreatKind()).isNull();
        assertThat(section.getThreatId()).isNull();
        assertThat(section.getBody()).isEqualTo("Just prose");
    }
}
