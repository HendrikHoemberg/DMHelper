package dev.hendrikhoemberg.dmhelper.rollabletable.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RollableTableRepository extends JpaRepository<RollableTable, UUID> {
}
