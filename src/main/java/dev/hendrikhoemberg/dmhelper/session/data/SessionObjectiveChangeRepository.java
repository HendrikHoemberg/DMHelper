package dev.hendrikhoemberg.dmhelper.session.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SessionObjectiveChangeRepository extends JpaRepository<SessionObjectiveChange, UUID> {
    List<SessionObjectiveChange> findBySessionIdOrderByChangedAtAscIdAsc(UUID sessionId);
}
