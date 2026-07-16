package dev.hendrikhoemberg.dmhelper.campaign.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SourceAnnotationRepository extends JpaRepository<SourceAnnotation, UUID> {
    List<SourceAnnotation> findByCampaignIdAndOwnerTypeAndOwnerId(UUID campaignId, String ownerType, UUID ownerId);
}
