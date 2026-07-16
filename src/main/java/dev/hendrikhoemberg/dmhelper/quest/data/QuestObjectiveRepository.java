package dev.hendrikhoemberg.dmhelper.quest.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface QuestObjectiveRepository extends JpaRepository<QuestObjective, UUID> {
    List<QuestObjective> findByQuestIdOrderBySortOrderAsc(UUID questId);
}
