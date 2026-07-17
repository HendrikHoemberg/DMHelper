package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpeciesRepository extends JpaRepository<Species, UUID>,
        JpaSpecificationExecutor<Species> {

    List<Species> findAllByOrderByNameAsc();
    Species findBySourceKey(String sourceKey);

    List<Species> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    Optional<Species> findBySourceAndSourceKey(ContentSource source, String sourceKey);

    List<Species> findByCampaignIdOrderByNameAsc(UUID campaignId);

    List<Species> findBySourceAndCampaignIsNullOrderByNameAsc(ContentSource source);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);
}
