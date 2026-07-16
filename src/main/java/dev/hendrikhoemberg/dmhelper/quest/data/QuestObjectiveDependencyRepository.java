package dev.hendrikhoemberg.dmhelper.quest.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface QuestObjectiveDependencyRepository extends JpaRepository<QuestObjectiveDependency, QuestObjectiveDependency.QuestObjectiveDependencyId> {
    List<QuestObjectiveDependency> findByQuestId(UUID questId);
    List<QuestObjectiveDependency> findByObjectiveId(UUID objectiveId);
    List<QuestObjectiveDependency> findByPrerequisiteObjectiveId(UUID prerequisiteObjectiveId);
}
