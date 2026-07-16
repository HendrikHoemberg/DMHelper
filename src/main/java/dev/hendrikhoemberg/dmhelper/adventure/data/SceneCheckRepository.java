package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SceneCheckRepository extends JpaRepository<SceneCheck, UUID> {
    List<SceneCheck> findBySceneIdOrderBySortOrderAsc(UUID sceneId);
}
