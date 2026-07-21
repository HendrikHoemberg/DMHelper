package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, UUID> {
    List<Scene> findByChapterIdOrderBySortOrderAsc(UUID chapterId);
    List<Scene> findByChapterIdOrderBySortOrderAscIdAsc(UUID chapterId);
    long countByChapterAdventureCampaignId(UUID campaignId);
    List<Scene> findByChapterAdventureCampaignId(UUID campaignId);
    List<Scene> findByChapterAdventureCampaignIdAndTitleIgnoreCase(UUID campaignId, String title);
    List<Scene> findByMapIdAndPinXNotNull(UUID mapId);
    List<Scene> findByMapId(UUID mapId);
    List<Scene> findByEncounterId(UUID encounterId);

    @Query("SELECT s FROM Scene s JOIN s.chapter c JOIN c.adventure a WHERE a.campaign.id = :campaignId AND s.id = :sceneId")
    Optional<Scene> findByIdAndCampaignId(@Param("campaignId") UUID campaignId, @Param("sceneId") UUID sceneId);

    List<Scene> findBySceneAudioCueId(UUID cueId);
}
