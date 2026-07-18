package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TableRollLogRepository extends JpaRepository<TableRollLog, UUID> {
    List<TableRollLog> findTop20ByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    List<TableRollLog> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId, Limit limit);

    List<TableRollLog> findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(UUID campaignId, Instant from, Instant to);
}
