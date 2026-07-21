package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HazardRepository extends JpaRepository<Hazard, UUID> {

    @Query("SELECT h FROM Hazard h "
            + "WHERE h.campaign IS NULL OR (:campaignId IS NOT NULL AND h.campaign.id = :campaignId) "
            + "ORDER BY h.name ASC")
    List<Hazard> findVisibleByCampaignId(@Param("campaignId") UUID campaignIdOrNull);

    @Query("SELECT h FROM Hazard h "
            + "LEFT JOIN FETCH h.campaign "
            + "WHERE h.id = :id")
    Optional<Hazard> findDetailedById(@Param("id") UUID id);

    List<Hazard> findByNameContainingIgnoreCaseOrderByNameAsc(String query);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String key);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String key);

    boolean existsBySourceAndSourceKeyAndCampaignIsNullAndIdNot(ContentSource source, String key, UUID id);

    boolean existsByCampaignIdAndSourceKeyAndIdNot(UUID campaignId, String key, UUID id);
    long countByCampaignId(UUID campaignId);
}
