package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorldRelationshipRepository extends JpaRepository<WorldRelationship, UUID> {
    List<WorldRelationship> findByCampaignIdOrderBySortOrderAscIdAsc(UUID campaignId);
    Optional<WorldRelationship> findByIdAndCampaignId(UUID id, UUID campaignId);

    @Query("""
            SELECT r FROM WorldRelationship r
            WHERE r.campaign.id = :campaignId
              AND ((r.fromType = :type AND r.fromId = :entityId)
                OR (r.toType = :type AND r.toId = :entityId))
            """)
    List<WorldRelationship> findByCampaignIdAndEndpoint(
            @Param("campaignId") UUID campaignId,
            @Param("type") String type,
            @Param("entityId") UUID entityId);
}
