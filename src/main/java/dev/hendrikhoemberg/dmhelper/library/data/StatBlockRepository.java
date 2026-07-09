package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatBlockRepository extends JpaRepository<StatBlock, UUID>,
        JpaSpecificationExecutor<StatBlock> {

    List<StatBlock> findAllByOrderByNameAsc();

    List<StatBlock> findBySourceOrderByNameAsc(StatBlock.Source source);

    List<StatBlock> findByCampaignIdOrderByNameAsc(UUID campaignId);

    Optional<StatBlock> findBySourceAndSourceKey(StatBlock.Source source, String sourceKey);

    boolean existsBySource(StatBlock.Source source);

    List<StatBlock> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
