package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorldLocationTableLinkRepository extends JpaRepository<WorldLocationTableLink, UUID> {
    List<WorldLocationTableLink> findByLocationIdOrderBySortOrderAsc(UUID locationId);
    List<WorldLocationTableLink> findByLocationId(UUID locationId);
}
