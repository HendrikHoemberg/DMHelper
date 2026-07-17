package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MagicItemRepository extends JpaRepository<MagicItem, UUID>,
        JpaSpecificationExecutor<MagicItem> {

    List<MagicItem> findAllByOrderByNameAsc();

    List<MagicItem> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    Optional<MagicItem> findBySourceKey(String sourceKey);

    Optional<MagicItem> findBySourceAndSourceKey(ContentSource source, String sourceKey);

    List<MagicItem> findByCampaignIdOrderByNameAsc(UUID campaignId);

    List<MagicItem> findBySourceAndCampaignIsNullOrderByNameAsc(ContentSource source);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);

    Optional<MagicItem> findBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);

    Optional<MagicItem> findByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);
}
