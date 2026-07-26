package dev.hendrikhoemberg.dmhelper.support;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FixtureShapeCoverageTest {

    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private ReleaseRehearsalFixture releaseFixture;
    @Autowired private EntityManager em;

    private PopulatedCampaignFixture.Seeded seeded;
    private ReleaseRehearsalFixture.Seeded releaseSeeded;
    private PackageShapeProfile profile;

    @BeforeAll
    void setUp() throws IOException {
        seeded = fixture.seed();
        releaseSeeded = releaseFixture.seed();
        profile = PackageShapeProfileExtractor.load(PackageShapeProfileExtractor.COMMITTED_PROFILE);
    }

    private Map<String, String> coverageQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("scene.title", "select count(s) from Scene s where s.title is not null");
        q.put("scene.body", "select count(s) from Scene s where s.body is not null");
        q.put("scene.summary", "select count(s) from Scene s where s.summary is not null");
        q.put("scene.status", "select count(s) from Scene s where s.status is not null");
        q.put("scene.tags", "select count(s) from Scene s where s.tags is not null");
        q.put("scene.sourceLocator", "select count(s) from Scene s where s.sourceLocator is not null");

        q.put("sceneSection.kind", "select count(x) from SceneSection x where x.kind is not null");
        q.put("sceneSection.label", "select count(x) from SceneSection x where x.label is not null");
        q.put("sceneSection.body", "select count(x) from SceneSection x where x.body is not null");
        q.put("sceneSection.sourceLocator", "select count(x) from SceneSection x where x.sourceLocator is not null");
        q.put("sceneSection.threatRef", "select count(x) from SceneSection x where x.threatId is not null");

        q.put("sceneParticipant.displayName", "select count(x) from SceneParticipant x where x.displayName is not null");
        q.put("sceneParticipant.quantity", "select count(x) from SceneParticipant x where x.quantity > 0");
        q.put("sceneParticipant.disposition", "select count(x) from SceneParticipant x where x.disposition is not null");
        q.put("sceneParticipant.placementHint", "select count(x) from SceneParticipant x where x.placementHint is not null");
        q.put("sceneParticipant.sourceLocator", "select count(x) from SceneParticipant x where x.sourceLocator is not null");
        q.put("sceneParticipant.statblockRef", "select count(x) from SceneParticipant x where x.statBlock is not null");
        q.put("sceneParticipant.noteRef", "select count(x) from SceneParticipant x where x.note is not null");

        q.put("sceneTransition.kind", "select count(x) from SceneTransition x where x.kind is not null");
        q.put("sceneTransition.label", "select count(x) from SceneTransition x where x.label is not null");
        q.put("sceneTransition.targetSceneRef", "select count(x) from SceneTransition x where x.targetScene is not null");
        q.put("sceneTransition.externalDestination", "select count(x) from SceneTransition x where x.externalDestination is not null");
        q.put("sceneTransition.condition", "select count(x) from SceneTransition x where x.condition is not null");
        q.put("sceneTransition.dmNote", "select count(x) from SceneTransition x where x.dmNote is not null");

        q.put("sceneCheck.label", "select count(x) from SceneCheck x where x.label is not null");
        q.put("sceneCheck.ability", "select count(x) from SceneCheck x where x.ability is not null");
        q.put("sceneCheck.skill", "select count(x) from SceneCheck x where x.skill is not null");
        q.put("sceneCheck.dc", "select count(x) from SceneCheck x where x.dc is not null");
        q.put("sceneCheck.visibility", "select count(x) from SceneCheck x where x.visibility is not null");
        q.put("sceneCheck.success", "select count(x) from SceneCheck x where x.success is not null");
        q.put("sceneCheck.failure", "select count(x) from SceneCheck x where x.failure is not null");
        q.put("sceneCheck.partial", "select count(x) from SceneCheck x where x.partial is not null");
        q.put("sceneCheck.sourceLocator", "select count(x) from SceneCheck x where x.sourceLocator is not null");

        q.put("sceneLink.role", "select count(x) from SceneLink x where x.role is not null");
        q.put("sceneLink.displayText", "select count(x) from SceneLink x where x.displayText is not null");
        q.put("sceneLink.targetRef", "select count(x) from SceneLink x where x.targetId is not null");

        q.put("worldNpc.name", "select count(x) from WorldNpc x where x.name is not null");
        q.put("worldNpc.role", "select count(x) from WorldNpc x where x.role is not null");
        q.put("worldNpc.disposition", "select count(x) from WorldNpc x where x.disposition is not null");
        q.put("worldNpc.status", "select count(x) from WorldNpc x where x.status is not null");
        q.put("worldNpc.appearance", "select count(x) from WorldNpc x where x.appearance is not null");
        q.put("worldNpc.voice", "select count(x) from WorldNpc x where x.voice is not null");
        q.put("worldNpc.motivation", "select count(x) from WorldNpc x where x.motivation is not null");
        q.put("worldNpc.secret", "select count(x) from WorldNpc x where x.secret is not null");
        q.put("worldNpc.tags", "select count(x) from WorldNpc x where x.tags is not null");
        q.put("worldNpc.sourceLocator", "select count(x) from WorldNpc x where x.sourceLocator is not null");
        q.put("worldNpc.factionRef", "select count(x) from WorldNpc x where x.faction is not null");
        q.put("worldNpc.locationRef", "select count(x) from WorldNpc x where x.location is not null");
        q.put("worldNpc.statblockRef", "select count(x) from WorldNpc x where x.statblock is not null");
        q.put("worldNpc.noteRef", "select count(x) from WorldNpc x where x.note is not null");

        q.put("worldLocation.name", "select count(x) from WorldLocation x where x.name is not null");
        q.put("worldLocation.kind", "select count(x) from WorldLocation x where x.kind is not null");
        q.put("worldLocation.summary", "select count(x) from WorldLocation x where x.summary is not null");
        q.put("worldLocation.services", "select count(x) from WorldLocation x where x.services is not null");
        q.put("worldLocation.secrets", "select count(x) from WorldLocation x where x.secrets is not null");
        q.put("worldLocation.tags", "select count(x) from WorldLocation x where x.tags is not null");
        q.put("worldLocation.sourceLocator", "select count(x) from WorldLocation x where x.sourceLocator is not null");
        q.put("worldLocation.tableLinks", "select count(x) from WorldLocationTableLink x");

        q.put("faction.name", "select count(x) from Faction x where x.name is not null");
        q.put("faction.goals", "select count(x) from Faction x where x.goals is not null");
        q.put("faction.resources", "select count(x) from Faction x where x.resources is not null");
        q.put("faction.reputationNotes", "select count(x) from Faction x where x.reputationNotes is not null");
        q.put("faction.tags", "select count(x) from Faction x where x.tags is not null");
        q.put("faction.sourceLocator", "select count(x) from Faction x where x.sourceLocator is not null");

        q.put("quest.title", "select count(x) from Quest x where x.title is not null");
        q.put("quest.status", "select count(x) from Quest x where x.status is not null");
        q.put("quest.summary", "select count(x) from Quest x where x.summary is not null");
        q.put("quest.tags", "select count(x) from Quest x where x.tags is not null");
        q.put("quest.rewards", "select count(x) from Quest x where x.rewards is not null");
        q.put("quest.prerequisites", "select count(x) from Quest x where x.prerequisites is not null");
        q.put("quest.outcomeNotes", "select count(x) from Quest x where x.outcomeNotes is not null");
        q.put("quest.sourceLocator", "select count(x) from Quest x where x.sourceLocator is not null");

        q.put("handout.title", "select count(x) from Handout x where x.title is not null");
        q.put("handout.tags", "select count(x) from Handout x where x.tags is not null");
        q.put("handout.dmOnly", "select count(x) from Handout x where x.dmOnly = true");
        q.put("handout.presented", "select count(x) from Handout x where x.presented = true");

        q.put("trap.name", "select count(x) from Trap x where x.name is not null");
        q.put("trap.description", "select count(x) from Trap x where x.description is not null");
        q.put("trap.severity", "select count(x) from Trap x where x.severity is not null");
        q.put("trap.resetMode", "select count(x) from Trap x where x.resetMode is not null");
        q.put("trap.triggerDescription", "select count(x) from Trap x where x.triggerDescription is not null");
        q.put("trap.detectionPassiveThreshold", "select count(x) from Trap x where x.detectionPassiveThreshold is not null");
        q.put("trap.detectionCheck", "select count(x) from Trap x where x.detectionCheck.dc is not null");
        q.put("trap.save", "select count(x) from Trap x where x.save.dc is not null");
        q.put("trap.damage", "select count(x) from Trap x where x.damageExpression is not null");
        q.put("trap.additionalEffect", "select count(x) from Trap x where x.additionalEffect is not null");
        q.put("trap.countermeasureNotes", "select count(x) from Trap x where x.countermeasureNotes is not null");

        q.put("hazard.name", "select count(x) from Hazard x where x.name is not null");
        q.put("hazard.description", "select count(x) from Hazard x where x.description is not null");
        q.put("hazard.severity", "select count(x) from Hazard x where x.severity is not null");
        q.put("hazard.exposureMode", "select count(x) from Hazard x where x.exposureMode is not null");
        q.put("hazard.exposureText", "select count(x) from Hazard x where x.exposureText is not null");
        q.put("hazard.areaHint", "select count(x) from Hazard x where x.areaHint is not null");
        q.put("hazard.check", "select count(x) from Hazard x where x.check.dc is not null");
        q.put("hazard.damage", "select count(x) from Hazard x where x.damageExpression is not null");
        q.put("hazard.escalationText", "select count(x) from Hazard x where x.escalationText is not null");
        q.put("hazard.endingConditions", "select count(x) from Hazard x where x.endingConditions is not null");

        q.put("rollableTable.name", "select count(x) from RollableTable x where x.name is not null");
        q.put("rollableTable.description", "select count(x) from RollableTable x where x.description is not null");
        q.put("rollableTable.addressMode", "select count(x) from RollableTable x where x.addressMode is not null");
        q.put("rollableTable.rollExpression", "select count(x) from RollableTable x where x.rollExpression is not null");
        q.put("rollableTable.category", "select count(x) from RollableTable x where x.category is not null");
        q.put("rollableTable.tags", "select count(x) from RollableTable x where x.tags is not null");
        return q;
    }

    @Test
    void everyProfileFieldHasACoverageQuery() {
        assertThat(coverageQueries().keySet())
                .as("""
                    The profile was refreshed with fields nobody taught this test to check. Add a \
                    coverage query for each, or document why it is exempt -- do not let a new \
                    real-world field pass silently.""")
                .containsAll(profile.populatedFields().keySet());
    }

    @Test
    @Transactional
    void fixtureCoversEveryFieldTheRealPackagePopulates() {
        var uncovered = new TreeSet<String>();
        for (var entry : coverageQueries().entrySet()) {
            if (!profile.populatedFields().containsKey(entry.getKey())) {
                continue;
            }
            long count = (Long) em.createQuery(entry.getValue()).getSingleResult();
            if (count == 0) {
                uncovered.add(entry.getKey());
            }
        }

        assertThat(uncovered)
                .as("""
                    The real package populates these; PopulatedCampaignFixture does not, so no \
                    test can see a defect in how they render. Extend the fixture -- this is a \
                    floor, not a ceiling, and existing coverage the profile does not mention \
                    (nested locations) must stay.""")
                .isEmpty();
    }

    @Test
    void sectionAndTransitionKindsFromTheRealPackageAreExercised() {
        var missingSections = new TreeSet<>(profile.sectionKinds());
        var seededSections = em.createQuery(
                "select distinct x.kind from SceneSection x", Object.class).getResultList();
        seededSections.forEach(k -> missingSections.remove(k.toString()));
        assertThat(missingSections)
                .as("the real package has TRAP, HAZARD, SCALING and DEVELOPMENT sections; "
                    + "the fixture never rendered threat/_mechanics-card.html inside a scene")
                .isEmpty();

        var missingTransitions = new TreeSet<>(profile.transitionKinds());
        var seededTransitions = em.createQuery(
                "select distinct x.kind from SceneTransition x", Object.class).getResultList();
        seededTransitions.forEach(k -> missingTransitions.remove(k.toString()));
        assertThat(missingTransitions).isEmpty();
    }

    @Test
    void readinessShapeCountsArePresentInTheProfile() {
        assertThat(profile.counts())
                .as("""
                    The shape profile must track handout.assetKind adoption, scene.mapRequirement \
                    usage, and metadata.conversionOmissions presence. If the profile was regenerated \
                    without these entries, re-extract after extending the extractor.""")
                .containsKeys("handout.nonSourcePageAssetKind", "scene.mapRequirement",
                        "metadata.conversionOmissions");
    }

    @Test
    void nestedLocationCoverageIsNotSacrificedToMatchTheProfile() {
        assertThat(seeded.childLocationId()).isNotNull();
        long nested = (Long) em.createQuery(
                "select count(x) from WorldLocation x where x.parentLocation is not null")
                .getSingleResult();
        assertThat(nested)
                .as("nested locations exceed the profile on purpose -- keep them")
                .isGreaterThan(0);
    }

    @Test
    @Transactional
    void releaseFixtureShapeIsScopedToItsSeededCampaign() {
        UUID campaignId = releaseSeeded.campaignId();

        assertThat(count("select count(s) from Scene s join s.chapter c join c.adventure a "
                + "where a.campaign.id = :campaignId and s.title is not null")).isEqualTo(3);
        assertThat(count("select count(x) from SceneSection x join x.scene s join s.chapter c join c.adventure a "
                + "where a.campaign.id = :campaignId and x.kind is not null and x.label is not null "
                + "and x.body is not null and x.sourceLocator is not null")).isEqualTo(2);
        assertThat(count("select count(x) from SceneParticipant x join x.scene s join s.chapter c join c.adventure a "
                + "where a.campaign.id = :campaignId and x.displayName is not null and x.statBlock is not null "
                + "and x.placementHint is not null and x.sourceLocator is not null")).isEqualTo(4);
        assertThat(count("select count(x) from SceneTransition x join x.scene s join s.chapter c join c.adventure a "
                + "where a.campaign.id = :campaignId and x.kind is not null and x.label is not null "
                + "and x.targetScene is not null and x.condition is not null and x.dmNote is not null")).isEqualTo(2);
        assertThat(count("select count(x) from StatBlock x where x.campaign.id = :campaignId and x.source is not null "
                + "and x.name is not null and x.cr is not null and x.type is not null and x.hp is not null")).isEqualTo(4);
        assertThat(count("select count(x) from GameMap x where x.campaign.id = :campaignId and x.gridWidth = 20 "
                + "and x.gridHeight = 15 and x.cellSizePx = 64 and x.showGrid = true")).isEqualTo(1);
        assertThat(count("select count(x) from Handout x where x.campaign.id = :campaignId and x.title is not null "
                + "and x.tags is not null")).isEqualTo(3);
        assertThat(count("select count(x) from Quest x where x.campaign.id = :campaignId and x.title is not null "
                + "and x.summary is not null and x.rewards is not null and x.prerequisites is not null "
                + "and x.outcomeNotes is not null")).isEqualTo(1);
        assertThat(count("select count(x) from QuestObjective x where x.quest.campaign.id = :campaignId "
                + "and x.title is not null and x.description is not null and x.sourceLocator is not null")).isEqualTo(2);
        assertThat(count("select count(x) from PartyMember x where x.campaign.id = :campaignId "
                + "and x.characterName is not null and x.ac > 0 and x.maxHp > 0 and x.passivePerception > 0")).isEqualTo(4);
    }

    private long count(String jpql) {
        return (Long) em.createQuery(jpql).setParameter("campaignId", releaseSeeded.campaignId()).getSingleResult();
    }
}
