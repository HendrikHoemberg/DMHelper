package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorldNpcRepository extends JpaRepository<WorldNpc, UUID> {

    @EntityGraph(attributePaths = {"faction", "location"})
    List<WorldNpc> findByCampaignIdOrderByNameAscIdAsc(UUID campaignId);

    @EntityGraph(attributePaths = {"faction", "location"})
    Optional<WorldNpc> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<WorldNpc> findByLocationId(UUID locationId);
    List<WorldNpc> findByFactionId(UUID factionId);
}
