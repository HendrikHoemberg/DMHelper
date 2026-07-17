package dev.hendrikhoemberg.dmhelper.campaign.packagev2.key;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CampaignPackageKeyRepository extends JpaRepository<CampaignPackageKey, UUID> {

    Optional<CampaignPackageKey> findByCampaignIdAndEntityTypeAndEntityId(
            UUID campaignId, String entityType, UUID entityId);

    boolean existsByCampaignIdAndEntityTypeAndPackageKey(
            UUID campaignId, String entityType, String packageKey);

    Optional<CampaignPackageKey> findByCampaignIdAndEntityTypeAndPackageKey(
            UUID campaignId, String entityType, String packageKey);

    List<CampaignPackageKey> findByCampaignIdOrderByEntityTypeAscPackageKeyAsc(UUID campaignId);

    void deleteByCampaignIdAndEntityTypeAndEntityIdIn(UUID campaignId, String entityType, java.util.Collection<UUID> entityIds);
}
