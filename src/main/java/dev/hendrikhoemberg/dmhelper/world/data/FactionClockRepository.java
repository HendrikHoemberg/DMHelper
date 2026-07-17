package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FactionClockRepository extends JpaRepository<FactionClock, UUID> {
    List<FactionClock> findByFactionIdOrderBySortOrderAscIdAsc(UUID factionId);
    List<FactionClock> findByCampaignIdOrderBySortOrderAscIdAsc(UUID campaignId);
}
