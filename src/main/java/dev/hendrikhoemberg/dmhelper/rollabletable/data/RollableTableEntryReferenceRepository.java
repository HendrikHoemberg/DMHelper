package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RollableTableEntryReferenceRepository extends JpaRepository<RollableTableEntryReference, UUID> {
    List<RollableTableEntryReference> findByTargetTypeAndTargetId(String targetType, UUID targetId);
}
