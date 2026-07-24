package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReadinessAcknowledgementRepository
        extends JpaRepository<ReadinessAcknowledgement, UUID> {

    List<ReadinessAcknowledgement> findByCampaignId(UUID campaignId);
    boolean existsByCampaignIdAndItemKey(UUID campaignId, String itemKey);
    void deleteByCampaignIdAndItemKey(UUID campaignId, String itemKey);
}
