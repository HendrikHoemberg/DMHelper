package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatBlockRepository extends JpaRepository<StatBlock, UUID>,
        JpaSpecificationExecutor<StatBlock> {

    List<StatBlock> findAllByOrderByNameAsc();

    List<StatBlock> findBySourceOrderByNameAsc(StatBlock.Source source);

    @Query("SELECT sb FROM StatBlock sb WHERE sb.campaign.id = :campaignId ORDER BY sb.name ASC")
    List<StatBlock> findByCampaignIdOrderByNameAsc(@Param("campaignId") UUID campaignId);

    @Query("SELECT sb FROM StatBlock sb WHERE sb.campaign.id = :campaignId ORDER BY sb.name ASC, sb.id ASC")
    List<StatBlock> findByCampaignIdOrderByNameAscIdAsc(@Param("campaignId") UUID campaignId);

    Optional<StatBlock> findBySourceAndSourceKey(StatBlock.Source source, String sourceKey);

    boolean existsBySource(StatBlock.Source source);

    Optional<StatBlock> findBySourceKey(String sourceKey);

    @Query("SELECT sb FROM StatBlock sb WHERE sb.campaign.id = :campaignId AND sb.sourceKey = :sourceKey")
    Optional<StatBlock> findByCampaignIdAndSourceKey(@Param("campaignId") UUID campaignId, @Param("sourceKey") String sourceKey);

    List<StatBlock> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
