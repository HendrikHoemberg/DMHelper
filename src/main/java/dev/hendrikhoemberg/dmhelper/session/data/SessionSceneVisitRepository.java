package dev.hendrikhoemberg.dmhelper.session.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionSceneVisitRepository extends JpaRepository<SessionSceneVisit, UUID> {

    Optional<SessionSceneVisit> findBySessionIdAndSceneId(UUID sessionId, UUID sceneId);

    List<SessionSceneVisit> findBySessionIdOrderByVisitedAtAscIdAsc(UUID sessionId);

    List<SessionSceneVisit> findBySceneId(UUID sceneId);

    void deleteBySessionId(UUID sessionId);

    void deleteBySceneId(UUID sceneId);
}
