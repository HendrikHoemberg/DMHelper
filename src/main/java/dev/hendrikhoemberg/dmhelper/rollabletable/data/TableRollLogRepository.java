package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableRollLogRepository extends JpaRepository<TableRollLog, UUID> {
    List<TableRollLog> findTop20ByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    List<TableRollLog> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId, Limit limit);

    List<TableRollLog> findByCampaignIdAndCreatedAtBetweenOrderByCreatedAtAscIdAsc(UUID campaignId, Instant from, Instant to);

    Optional<TableRollLog> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<TableRollLog> findByTableId(UUID tableId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TableRollLog l where l.id = :id and l.campaign.id = :campaignId")
    Optional<TableRollLog> findForResolution(UUID id, UUID campaignId);
}
