package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorldLocationTableLinkRepository extends JpaRepository<WorldLocationTableLink, UUID> {
    List<WorldLocationTableLink> findByLocationIdOrderBySortOrderAsc(UUID locationId);
    List<WorldLocationTableLink> findByLocationId(UUID locationId);
    List<WorldLocationTableLink> findByTableId(UUID tableId);
    Optional<WorldLocationTableLink> findByIdAndLocationId(UUID id, UUID locationId);

    @Query("select l from WorldLocationTableLink l join fetch l.table where l.location.id = :locationId order by l.sortOrder asc")
    List<WorldLocationTableLink> findByLocationIdWithTable(UUID locationId);
}
