package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConditionRepository extends JpaRepository<Condition, UUID>,
        JpaSpecificationExecutor<Condition> {

    List<Condition> findAllByOrderByNameAsc();

    List<Condition> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    Optional<Condition> findBySourceAndSourceKey(ContentSource source, String sourceKey);

    List<Condition> findByCampaignIdOrderByNameAsc(UUID campaignId);

    List<Condition> findBySourceAndCampaignIsNullOrderByNameAsc(ContentSource source);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);
}
