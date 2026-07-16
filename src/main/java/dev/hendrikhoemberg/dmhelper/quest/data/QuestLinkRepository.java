package dev.hendrikhoemberg.dmhelper.quest.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface QuestLinkRepository extends JpaRepository<QuestLink, UUID> {
    List<QuestLink> findByQuestIdOrderBySortOrderAsc(UUID questId);
}
