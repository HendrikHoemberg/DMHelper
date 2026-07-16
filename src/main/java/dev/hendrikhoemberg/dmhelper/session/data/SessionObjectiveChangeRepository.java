package dev.hendrikhoemberg.dmhelper.session.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SessionObjectiveChangeRepository extends JpaRepository<SessionObjectiveChange, UUID> {
    List<SessionObjectiveChange> findBySessionIdOrderByChangedAtAscIdAsc(UUID sessionId);
    List<SessionObjectiveChange> findByObjectiveId(UUID objectiveId);
    void deleteByObjectiveId(UUID objectiveId);
    void deleteBySessionId(UUID sessionId);
}
