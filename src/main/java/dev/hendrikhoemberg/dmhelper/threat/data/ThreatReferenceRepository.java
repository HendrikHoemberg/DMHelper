package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ThreatReferenceRepository extends JpaRepository<ThreatReference, UUID> {

    List<ThreatReference> findByTargetTypeAndTargetId(CampaignContentType targetType, UUID targetId);

    List<ThreatReference> findByTrapIdOrderBySortOrderAsc(UUID trapId);

    List<ThreatReference> findByHazardIdOrderBySortOrderAsc(UUID hazardId);
}
