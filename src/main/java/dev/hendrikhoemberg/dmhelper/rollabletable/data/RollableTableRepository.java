package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RollableTableRepository extends JpaRepository<RollableTable, UUID> {
    List<RollableTable> findByCampaignIdOrderByNameAsc(UUID campaignId);

    @Query("SELECT t FROM RollableTable t "
            + "WHERE t.campaign IS NULL OR (:campaignId IS NOT NULL AND t.campaign.id = :campaignId) "
            + "ORDER BY t.name ASC")
    List<RollableTable> findVisibleByCampaignId(@Param("campaignId") UUID campaignIdOrNull);

    @Query("SELECT DISTINCT t FROM RollableTable t LEFT JOIN FETCH t.entries WHERE t.id = :id")
    Optional<RollableTable> findWithEntriesById(@Param("id") UUID id);

    List<RollableTable> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);

    boolean existsBySourceAndSourceKeyAndCampaignIsNullAndIdNot(
            ContentSource source, String sourceKey, UUID id);

    boolean existsByCampaignIdAndSourceKeyAndIdNot(UUID campaignId, String sourceKey, UUID id);
    long countByCampaignId(UUID campaignId);
}
