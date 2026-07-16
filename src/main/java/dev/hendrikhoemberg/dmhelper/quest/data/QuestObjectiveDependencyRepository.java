package dev.hendrikhoemberg.dmhelper.quest.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface QuestObjectiveDependencyRepository extends JpaRepository<QuestObjectiveDependency, QuestObjectiveDependency.QuestObjectiveDependencyId> {
    List<QuestObjectiveDependency> findByQuestId(UUID questId);
    List<QuestObjectiveDependency> findByObjectiveId(UUID objectiveId);
    List<QuestObjectiveDependency> findByPrerequisiteObjectiveId(UUID prerequisiteObjectiveId);

    @Modifying
    @Query("DELETE FROM QuestObjectiveDependency d WHERE d.objective.id = :objectiveId")
    void deleteByObjectiveId(@Param("objectiveId") UUID objectiveId);

    @Modifying
    @Query("DELETE FROM QuestObjectiveDependency d WHERE d.prerequisiteObjective.id = :objectiveId")
    void deleteByPrerequisiteObjectiveId(@Param("objectiveId") UUID objectiveId);
}
