package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorldLocationRepository extends JpaRepository<WorldLocation, UUID> {
    List<WorldLocation> findByCampaignIdOrderByNameAscIdAsc(UUID campaignId);
    Optional<WorldLocation> findByIdAndCampaignId(UUID id, UUID campaignId);
}
