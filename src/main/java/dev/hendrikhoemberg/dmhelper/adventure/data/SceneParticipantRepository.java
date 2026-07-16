package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SceneParticipantRepository extends JpaRepository<SceneParticipant, UUID> {
    List<SceneParticipant> findBySceneIdOrderBySortOrderAsc(UUID sceneId);
}
