package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorldLocationRepository extends JpaRepository<WorldLocation, UUID> {

    @EntityGraph(attributePaths = {"parentLocation"})
    List<WorldLocation> findByCampaignIdOrderByNameAscIdAsc(UUID campaignId);

    @EntityGraph(attributePaths = {"parentLocation", "locationAudioCue"})
    Optional<WorldLocation> findByIdAndCampaignId(UUID id, UUID campaignId);

    List<WorldLocation> findByLocationAudioCueId(UUID cueId);
}
