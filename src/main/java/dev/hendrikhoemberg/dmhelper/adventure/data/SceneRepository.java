package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, UUID> {
    List<Scene> findByChapterIdOrderBySortOrderAsc(UUID chapterId);
    List<Scene> findByChapterAdventureCampaignId(UUID campaignId);
    List<Scene> findByChapterAdventureCampaignIdAndTitleIgnoreCase(UUID campaignId, String title);
    List<Scene> findByMapIdAndPinXNotNull(UUID mapId);
    List<Scene> findByMapId(UUID mapId);
    List<Scene> findByEncounterId(UUID encounterId);
}
