package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrapRepository extends JpaRepository<Trap, UUID> {

    @Query("SELECT t FROM Trap t "
            + "WHERE t.campaign IS NULL OR (:campaignId IS NOT NULL AND t.campaign.id = :campaignId) "
            + "ORDER BY t.name ASC")
    List<Trap> findVisibleByCampaignId(@Param("campaignId") UUID campaignIdOrNull);

    @Query("SELECT t FROM Trap t "
            + "LEFT JOIN FETCH t.campaign "
            + "LEFT JOIN FETCH t.statBlock "
            + "WHERE t.id = :id")
    Optional<Trap> findDetailedById(@Param("id") UUID id);

    List<Trap> findByNameContainingIgnoreCaseOrderByNameAsc(String query);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String key);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String key);

    boolean existsBySourceAndSourceKeyAndCampaignIsNullAndIdNot(ContentSource source, String key, UUID id);

    boolean existsByCampaignIdAndSourceKeyAndIdNot(UUID campaignId, String key, UUID id);

    long countByStatBlockId(UUID statBlockId);
}
