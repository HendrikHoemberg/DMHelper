package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RollableTableRepository extends JpaRepository<RollableTable, UUID> {
    List<RollableTable> findByCampaignIdOrderByNameAsc(UUID campaignId);

    @Query("SELECT DISTINCT t FROM RollableTable t LEFT JOIN FETCH t.entries WHERE t.id = :id")
    Optional<RollableTable> findWithEntriesById(@Param("id") UUID id);

    List<RollableTable> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
