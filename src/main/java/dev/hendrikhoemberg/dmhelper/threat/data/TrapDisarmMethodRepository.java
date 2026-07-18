package dev.hendrikhoemberg.dmhelper.threat.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrapDisarmMethodRepository extends JpaRepository<TrapDisarmMethod, UUID> {

    List<TrapDisarmMethod> findByTrapIdOrderBySortOrderAsc(UUID trapId);
}
