package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorldNpcRepository extends JpaRepository<WorldNpc, UUID> {
    List<WorldNpc> findByCampaignIdOrderByNameAscIdAsc(UUID campaignId);
    Optional<WorldNpc> findByIdAndCampaignId(UUID id, UUID campaignId);
}
