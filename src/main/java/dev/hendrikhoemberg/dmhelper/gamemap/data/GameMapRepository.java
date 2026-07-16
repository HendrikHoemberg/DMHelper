package dev.hendrikhoemberg.dmhelper.gamemap.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GameMapRepository extends JpaRepository<GameMap, UUID> {

    List<GameMap> findByCampaignIdOrderBySortOrderAsc(UUID campaignId);

    List<GameMap> findByCampaignIdOrderBySortOrderAscIdAsc(UUID campaignId);

    long countByCampaignId(UUID campaignId);
}
