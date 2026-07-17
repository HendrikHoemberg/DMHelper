package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatRepository extends JpaRepository<Feat, UUID>,
        JpaSpecificationExecutor<Feat> {

    List<Feat> findAllByOrderByNameAsc();

    List<Feat> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    List<Feat> findBySourceKeyIn(List<String> sourceKeys);

    Optional<Feat> findBySourceAndSourceKey(ContentSource source, String sourceKey);

    List<Feat> findByCampaignIdOrderByNameAsc(UUID campaignId);

    List<Feat> findBySourceAndCampaignIsNullOrderByNameAsc(ContentSource source);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);
}
