package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AdventureRepository extends JpaRepository<Adventure, UUID> {
    List<Adventure> findByCampaignIdOrderBySortOrderAsc(UUID campaignId);
    List<Adventure> findByCampaignIdOrderBySortOrderAscIdAsc(UUID campaignId);
    long countByCampaignId(UUID campaignId);
}
