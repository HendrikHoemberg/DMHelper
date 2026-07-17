package dev.hendrikhoemberg.dmhelper.campaign.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface SourceAnnotationRepository extends JpaRepository<SourceAnnotation, UUID> {
    List<SourceAnnotation> findByCampaignIdOrderByCreatedAtAscIdAsc(UUID campaignId);
    List<SourceAnnotation> findByCampaignIdAndOwnerTypeAndOwnerId(UUID campaignId, String ownerType, UUID ownerId);
    List<SourceAnnotation> findByOwnerTypeAndOwnerId(String ownerType, UUID ownerId);
    @Modifying
    @Query("DELETE FROM SourceAnnotation sa WHERE sa.ownerType = :ownerType AND sa.ownerId = :ownerId")
    void deleteByOwnerTypeAndOwnerId(@Param("ownerType") String ownerType, @Param("ownerId") UUID ownerId);
}
