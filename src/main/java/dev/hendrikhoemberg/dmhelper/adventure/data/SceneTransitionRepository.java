package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SceneTransitionRepository extends JpaRepository<SceneTransition, UUID> {
    List<SceneTransition> findBySceneIdOrderBySortOrderAsc(UUID sceneId);
    List<SceneTransition> findByTargetSceneId(UUID targetSceneId);
}
