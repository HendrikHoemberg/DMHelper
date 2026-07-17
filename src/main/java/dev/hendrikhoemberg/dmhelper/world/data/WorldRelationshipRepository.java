package dev.hendrikhoemberg.dmhelper.world.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorldRelationshipRepository extends JpaRepository<WorldRelationship, UUID> {
    List<WorldRelationship> findByCampaignIdOrderBySortOrderAscIdAsc(UUID campaignId);
}
