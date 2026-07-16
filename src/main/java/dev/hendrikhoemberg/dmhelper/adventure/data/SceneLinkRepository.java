package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SceneLinkRepository extends JpaRepository<SceneLink, UUID> {
    List<SceneLink> findBySceneIdOrderBySortOrderAsc(UUID sceneId);
}
