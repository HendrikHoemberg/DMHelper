package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionKind;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TrapService.class, HazardService.class, ThreatValidator.class, ThreatReferenceResolver.class,
        ThreatDependencyService.class, CustomContentSupport.class, LibraryReferenceCleaner.class})
class TrapServiceTest {

    @MockitoBean
    private CampaignPackageKeyService packageKeyService;

    @Autowired private TrapService service;
    @Autowired private TrapRepository repository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ConditionRepository conditionRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private AdventureRepository adventureRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private SceneSectionRepository sceneSectionRepository;
    @Autowired private EntityManager em;

    private UUID campaignId;
    private UUID srdTrapId;
    private UUID conditionId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        conditionRepository.deleteAll();
        statBlockRepository.deleteAll();
        sceneSectionRepository.deleteAll();
        sceneRepository.deleteAll();
        chapterRepository.deleteAll();
        adventureRepository.deleteAll();
        campaignRepository.deleteAll();

        Campaign campaign = new Campaign();
        campaign.setName("Trap Campaign");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();

        Condition condition = new Condition();
        condition.setSource(ContentSource.SRD);
        condition.setSourceKey("poisoned");
        condition.setName("Poisoned");
        condition.setDescription("Poisoned");
        condition = conditionRepository.save(condition);
        conditionId = condition.getId();

        Trap srd = new Trap();
        srd.setSourceKey("srd-needle");
        srd.setSource(ContentSource.SRD);
        srd.setName("SRD Needle");
        srd.setDescription("Classic needle.");
        srd.setSeverity(ThreatSeverity.SETBACK);
        srd.setResetMode(ThreatResetMode.NONE);
        srd = repository.save(srd);
        srdTrapId = srd.getId();
        em.flush();
    }

    @Test
    void createsGlobalAndCampaignCustomTraps() {
        Trap global = service.create(null, validWrite("global-trap", "Global Trap"), null);
        assertThat(global.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(global.getCampaign()).isNull();

        Trap camp = service.create(campaignId, validWrite("camp-trap", "Campaign Trap"), null);
        assertThat(camp.getCampaign().getId()).isEqualTo(campaignId);
    }

    @Test
    void rejectsMissingCampaignAndSourceKeyCollision() {
        assertThatThrownBy(() -> service.create(
                UUID.randomUUID(), validWrite("x", "X"), null))
                .isInstanceOf(NotFoundException.class);

        service.create(campaignId, validWrite("same-key", "First"), null);
        assertThatThrownBy(() -> service.create(
                campaignId, validWrite("same-key", "Second"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceKey");
    }

    @Test
    void rejectsCrossCampaignReferenceAndGlobalToCampaignTarget() {
        Campaign other = new Campaign();
        other.setName("Other");
        other = campaignRepository.save(other);

        Condition foreign = new Condition();
        foreign.setSource(ContentSource.CUSTOM);
        foreign.setCampaign(other);
        foreign.setSourceKey("foreign-cond");
        foreign.setName("Foreign");
        foreign.setDescription("x");
        foreign = conditionRepository.save(foreign);
        em.flush();

        TrapWrite cross = withConditionRef(validWrite("cross", "Cross"), foreign.getId());
        assertThatThrownBy(() -> service.create(campaignId, cross, null))
                .isInstanceOf(ThreatValidationException.class)
                .satisfies(e -> assertThat(((ThreatValidationException) e).problems())
                        .anyMatch(p -> p.code().equals("UNRESOLVED_REFERENCE")));

        Condition campOnly = new Condition();
        campOnly.setSource(ContentSource.CUSTOM);
        campOnly.setCampaign(campaignRepository.findById(campaignId).orElseThrow());
        campOnly.setSourceKey("camp-cond");
        campOnly.setName("Camp Cond");
        campOnly.setDescription("x");
        campOnly = conditionRepository.save(campOnly);
        em.flush();

        Condition finalCampOnly = campOnly;
        TrapWrite globalWrite = withConditionRef(validWrite("global-ref", "Global Ref"), finalCampOnly.getId());
        assertThatThrownBy(() -> service.create(null, globalWrite, null))
                .isInstanceOf(ThreatValidationException.class)
                .satisfies(e -> assertThat(((ThreatValidationException) e).problems())
                        .anyMatch(p -> p.code().equals("UNRESOLVED_REFERENCE")));
    }

    @Test
    void clonesSrdAndPromotesWhenReferencesAreGlobal() {
        Trap cloned = service.cloneAsCustom(srdTrapId, campaignId, "Custom Needle");
        assertThat(cloned.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(cloned.getName()).isEqualTo("Custom Needle");

        Trap withRef = service.create(campaignId, withConditionRef(
                validWrite("promote-me", "Promote Me"), conditionId), null);
        Trap promoted = service.promoteToGlobal(withRef.getId());
        assertThat(promoted.getCampaign()).isNull();
    }

    @Test
    void rejectsPromotionWithCampaignScopedReferences() {
        Condition campCond = new Condition();
        campCond.setSource(ContentSource.CUSTOM);
        campCond.setCampaign(campaignRepository.findById(campaignId).orElseThrow());
        campCond.setSourceKey("local-cond");
        campCond.setName("Local");
        campCond.setDescription("x");
        campCond = conditionRepository.save(campCond);
        em.flush();

        Trap trap = service.create(campaignId, withConditionRef(
                validWrite("promo-block", "Promo Block"), campCond.getId()), null);
        UUID id = trap.getId();
        assertThatThrownBy(() -> service.promoteToGlobal(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promote");
    }

    @Test
    void rejectsUpdateOfSrd() {
        assertThatThrownBy(() -> service.updateCustom(srdTrapId, validWrite("x", "X"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom");
    }

    @Test
    void deletionImpactAndConfirmedDeleteNullsSceneRefs() {
        Trap trap = service.create(campaignId, validWrite("impact", "Impact"), null);
        createSceneSection(trap.getId());
        em.flush();

        ThreatDeletionImpact impact = service.deletionImpact(trap.getId());
        assertThat(impact.hasDependents()).isTrue();
        assertThat(impact.dependencies()).anyMatch(d ->
                ThreatDependencyService.DEP_KIND_SCENE.equals(d.kind()));

        UUID trapId = trap.getId();
        assertThatThrownBy(() -> service.deleteCustom(trapId, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dependent");

        service.deleteCustom(trapId, true);
        em.flush();
        em.clear();

        assertThat(repository.findById(trapId)).isEmpty();
        SceneSection section = sceneSectionRepository.findAll().getFirst();
        assertThat(section.getThreatKind()).isNull();
        assertThat(section.getThreatId()).isNull();
        assertThat(section.getBody()).isEqualTo("Keep this prose");
        assertThat(section.getLabel()).isEqualTo("Needle section");
    }

    @Test
    void deletesWithoutDependentsWithoutConfirmation() {
        Trap trap = service.create(null, validWrite("simple", "Simple"), null);
        UUID id = trap.getId();
        assertThatCode(() -> service.deleteCustom(id, false)).doesNotThrowAnyException();
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void requireVisibleEnforcesCampaignScope() {
        Trap campTrap = service.create(campaignId, validWrite("vis", "Visible"), null);
        assertThat(service.requireVisible(campTrap.getId(), campaignId).getId())
                .isEqualTo(campTrap.getId());

        Campaign other = new Campaign();
        other.setName("Other Vis");
        other = campaignRepository.save(other);
        UUID otherId = other.getId();
        UUID trapId = campTrap.getId();
        assertThatThrownBy(() -> service.requireVisible(trapId, otherId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesDisarmMethodsAndReferences() {
        Trap trap = service.create(campaignId, withConditionRef(
                validWrite("upd", "Update Me"), conditionId), null);
        TrapWrite update = new TrapWrite(
                "upd", "Updated", "Updated description for the trap.",
                ThreatSeverity.DEADLY, 5, 10, "New trigger", null, 18,
                new ThreatCheckWrite(ThreatCheckMode.CHECK, "INT", "Investigation", 16),
                List.of(new TrapDisarmMethodWrite(
                        "arcana", "Arcana", "INT", "Arcana", null, 17, null, 0)),
                4, null, "2d6", List.of(DamageType.PIERCING), null,
                ThreatResetMode.NONE, null, null, null, List.of());

        Trap saved = service.updateCustom(trap.getId(), update, null);
        em.flush();
        em.clear();
        Trap loaded = service.findDetailedById(saved.getId());
        assertThat(loaded.getName()).isEqualTo("Updated");
        assertThat(loaded.getDisarmMethods()).hasSize(1);
        assertThat(loaded.getDisarmMethods().getFirst().getMethodKey()).isEqualTo("arcana");
        assertThat(loaded.getAttackBonus()).isEqualTo(4);
        assertThat(loaded.getReferences()).isEmpty();
    }

    private TrapWrite validWrite(String key, String name) {
        return new TrapWrite(
                key, name, "A solid trap description.",
                ThreatSeverity.SETBACK, null, null, null, null, null, null,
                List.of(), null, null, null, List.of(), null,
                ThreatResetMode.NONE, null, null, null, List.of());
    }

    private TrapWrite withConditionRef(TrapWrite base, UUID conditionTargetId) {
        return new TrapWrite(
                base.sourceKey(), base.name(), base.description(), base.severity(),
                base.minLevel(), base.maxLevel(), base.triggerDescription(), base.triggerAreaHint(),
                base.detectionPassiveThreshold(), base.detectionCheck(), base.disarmMethods(),
                base.attackBonus(), base.save(), base.damageExpression(), base.damageTypes(),
                base.additionalEffect(), base.resetMode(), base.resetTiming(), base.statBlockId(),
                base.countermeasureNotes(),
                List.of(new ThreatReferenceWrite(
                        ThreatReferenceRole.CONDITION, CampaignContentType.CONDITION,
                        conditionTargetId, "Poisoned")));
    }

    private void createSceneSection(UUID trapId) {
        Adventure adventure = new Adventure();
        adventure.setCampaign(campaignRepository.findById(campaignId).orElseThrow());
        adventure.setName("Adv");
        adventure = adventureRepository.save(adventure);

        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Ch");
        chapter = chapterRepository.save(chapter);

        Scene scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Scene");
        scene.setSortOrder(0);
        scene = sceneRepository.save(scene);

        SceneSection section = new SceneSection();
        section.setScene(scene);
        section.setKind(SceneSectionKind.TRAP);
        section.setLabel("Needle section");
        section.setBody("Keep this prose");
        section.setSortOrder(0);
        section.setThreatKind(ThreatKind.TRAP);
        section.setThreatId(trapId);
        sceneSectionRepository.save(section);
    }
}
