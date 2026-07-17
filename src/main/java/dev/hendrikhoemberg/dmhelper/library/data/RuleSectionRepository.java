package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleSectionRepository extends JpaRepository<RuleSection, UUID>,
        JpaSpecificationExecutor<RuleSection> {

    List<RuleSection> findByRulesetOrderBySortOrderAsc(String ruleset);

    List<RuleSection> findAllByOrderBySortOrderAsc();

    List<RuleSection> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    Optional<RuleSection> findBySourceAndSourceKey(ContentSource source, String sourceKey);

    List<RuleSection> findByCampaignIdOrderByNameAsc(UUID campaignId);

    List<RuleSection> findBySourceAndCampaignIsNullOrderByNameAsc(ContentSource source);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);
}
