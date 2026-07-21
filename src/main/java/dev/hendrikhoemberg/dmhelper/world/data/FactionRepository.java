package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactionRepository extends JpaRepository<Faction, UUID> {
    List<Faction> findByCampaignIdOrderByNameAscIdAsc(UUID campaignId);
    Optional<Faction> findByIdAndCampaignId(UUID id, UUID campaignId);
    long countByCampaignId(UUID campaignId);
}
