package dev.hendrikhoemberg.dmhelper.quest.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestRepository extends JpaRepository<Quest, UUID> {
    List<Quest> findByCampaignIdOrderByCreatedAtAscIdAsc(UUID campaignId);
    Optional<Quest> findByIdAndCampaignId(UUID id, UUID campaignId);
    long countByCampaignId(UUID campaignId);
}
