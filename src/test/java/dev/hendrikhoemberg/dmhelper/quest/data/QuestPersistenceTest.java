package dev.hendrikhoemberg.dmhelper.quest.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class QuestPersistenceTest {

    @Autowired private EntityManager em;
    @Autowired private QuestRepository questRepository;
    @Autowired private QuestObjectiveRepository questObjectiveRepository;
    @Autowired private QuestObjectiveDependencyRepository questObjectiveDependencyRepository;
    @Autowired private QuestLinkRepository questLinkRepository;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Quest Test");
        em.persist(campaign);
    }

    @Test
    void persistsAndReloadsQuest() {
        var quest = new Quest();
        quest.setCampaign(campaign);
        quest.setTitle("Find the Artifact");
        quest.setStatus(QuestStatus.NOT_STARTED);
        quest.setSummary("The party must locate the lost artifact.");
        quest.setSourceLocator("src:curse-of-strahd/quests/artifact");
        quest.setTags("main, artifact, strahd");
        quest.setRewards("5000 XP, Legendary Item");
        quest.setPrerequisites("Must be level 5+");
        quest.setOutcomeNotes("Strahd will be weakened");
        em.persist(quest);
        em.flush();
        em.clear();

        var reloaded = questRepository.findById(quest.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Find the Artifact");
        assertThat(reloaded.getStatus()).isEqualTo(QuestStatus.NOT_STARTED);
        assertThat(reloaded.getSummary()).isEqualTo("The party must locate the lost artifact.");
        assertThat(reloaded.getSourceLocator()).isEqualTo("src:curse-of-strahd/quests/artifact");
        assertThat(reloaded.getTags()).isEqualTo("main, artifact, strahd");
        assertThat(reloaded.getRewards()).isEqualTo("5000 XP, Legendary Item");
        assertThat(reloaded.getPrerequisites()).isEqualTo("Must be level 5+");
        assertThat(reloaded.getOutcomeNotes()).isEqualTo("Strahd will be weakened");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void persistsQuestObjectivesInOrder() {
        var quest = new Quest();
        quest.setCampaign(campaign);
        quest.setTitle("Multi-Step Quest");
        em.persist(quest);

        var obj1 = new QuestObjective();
        obj1.setQuest(quest);
        obj1.setTitle("Find the clue");
        obj1.setDescription("Search the library");
        obj1.setStatus(QuestObjectiveStatus.COMPLETED);
        obj1.setCompletionMode(QuestObjectiveCompletionMode.ALL);
        obj1.setSortOrder(0);
        obj1.setSourceLocator("src:clue");
        em.persist(obj1);

        var obj2 = new QuestObjective();
        obj2.setQuest(quest);
        obj2.setTitle("Defeat the guardian");
        obj2.setStatus(QuestObjectiveStatus.NOT_STARTED);
        obj2.setCompletionMode(QuestObjectiveCompletionMode.ANY);
        obj2.setSortOrder(1);
        obj2.setSourceLocator("src:guardian");
        em.persist(obj2);
        em.flush();
        em.clear();

        var objectives = questObjectiveRepository.findByQuestIdOrderBySortOrderAsc(quest.getId());
        assertThat(objectives).hasSize(2);
        assertThat(objectives.get(0).getTitle()).isEqualTo("Find the clue");
        assertThat(objectives.get(0).getDescription()).isEqualTo("Search the library");
        assertThat(objectives.get(0).getStatus()).isEqualTo(QuestObjectiveStatus.COMPLETED);
        assertThat(objectives.get(0).getCompletionMode()).isEqualTo(QuestObjectiveCompletionMode.ALL);
        assertThat(objectives.get(0).getSourceLocator()).isEqualTo("src:clue");
        assertThat(objectives.get(0).getSortOrder()).isZero();
        assertThat(objectives.get(1).getTitle()).isEqualTo("Defeat the guardian");
        assertThat(objectives.get(1).getStatus()).isEqualTo(QuestObjectiveStatus.NOT_STARTED);
        assertThat(objectives.get(1).getCompletionMode()).isEqualTo(QuestObjectiveCompletionMode.ANY);
        assertThat(objectives.get(1).getSortOrder()).isOne();
    }

    @Test
    void persistsObjectiveDependencies() {
        var quest = new Quest();
        quest.setCampaign(campaign);
        quest.setTitle("Dependent Quest");
        em.persist(quest);

        var prereq = new QuestObjective();
        prereq.setQuest(quest);
        prereq.setTitle("Talk to the sage");
        prereq.setSortOrder(0);
        em.persist(prereq);

        var dependent = new QuestObjective();
        dependent.setQuest(quest);
        dependent.setTitle("Retrieve the tome");
        dependent.setSortOrder(1);
        em.persist(dependent);

        var dep = new QuestObjectiveDependency();
        dep.setQuest(quest);
        dep.setObjective(dependent);
        dep.setPrerequisiteObjective(prereq);
        em.persist(dep);
        em.flush();
        em.clear();

        var deps = questObjectiveDependencyRepository.findByQuestId(quest.getId());
        assertThat(deps).hasSize(1);
        var loaded = deps.get(0);
        assertThat(loaded.getObjective().getId()).isEqualTo(dependent.getId());
        assertThat(loaded.getPrerequisiteObjective().getId()).isEqualTo(prereq.getId());
        assertThat(loaded.getQuest().getId()).isEqualTo(quest.getId());
    }

    @Test
    void persistsQuestLinks() {
        var quest = new Quest();
        quest.setCampaign(campaign);
        quest.setTitle("Linked Quest");
        em.persist(quest);

        var link = new QuestLink();
        link.setQuest(quest);
        link.setRole(QuestLinkRole.GIVER);
        link.setTargetScope(SceneLinkTargetScope.PACKAGE);
        link.setTargetType("NPC");
        link.setTargetId(UUID.randomUUID());
        link.setCatalogRuleset("dnd5e");
        link.setCatalogSourceKey("npc:strahd");
        link.setDisplayText("Strahd");
        link.setCondition("If alive");
        link.setSortOrder(0);
        em.persist(link);
        em.flush();
        em.clear();

        var links = questLinkRepository.findByQuestIdOrderBySortOrderAsc(quest.getId());
        assertThat(links).hasSize(1);
        var loaded = links.get(0);
        assertThat(loaded.getRole()).isEqualTo(QuestLinkRole.GIVER);
        assertThat(loaded.getTargetScope()).isEqualTo(SceneLinkTargetScope.PACKAGE);
        assertThat(loaded.getTargetType()).isEqualTo("NPC");
        assertThat(loaded.getTargetId()).isNotNull();
        assertThat(loaded.getCatalogRuleset()).isEqualTo("dnd5e");
        assertThat(loaded.getCatalogSourceKey()).isEqualTo("npc:strahd");
        assertThat(loaded.getDisplayText()).isEqualTo("Strahd");
        assertThat(loaded.getCondition()).isEqualTo("If alive");
        assertThat(loaded.getSortOrder()).isZero();
    }

    @Test
    void hasTimestampsOnCreate() {
        var quest = new Quest();
        quest.setCampaign(campaign);
        quest.setTitle("Timed Quest");
        em.persist(quest);
        em.flush();

        assertThat(quest.getCreatedAt()).isNotNull();
    }

    @Test
    void nullableLegacyFieldsAreNullByDefault() {
        var quest = new Quest();
        quest.setCampaign(campaign);
        quest.setTitle("Legacy Fields");
        em.persist(quest);
        em.flush();
        em.clear();

        var reloaded = questRepository.findById(quest.getId()).orElseThrow();
        assertThat(reloaded.getSummary()).isNull();
        assertThat(reloaded.getSourceLocator()).isNull();
        assertThat(reloaded.getTags()).isNull();
        assertThat(reloaded.getRewards()).isNull();
        assertThat(reloaded.getPrerequisites()).isNull();
        assertThat(reloaded.getOutcomeNotes()).isNull();
    }
}
