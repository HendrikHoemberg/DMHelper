package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SceneSectionRepository extends JpaRepository<SceneSection, UUID> {
    List<SceneSection> findBySceneIdOrderBySortOrderAsc(UUID sceneId);
}
