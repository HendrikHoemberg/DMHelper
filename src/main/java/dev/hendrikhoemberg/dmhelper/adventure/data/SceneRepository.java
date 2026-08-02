package dev.hendrikhoemberg.dmhelper.adventure.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Scene s JOIN s.chapter c JOIN c.adventure a WHERE a.campaign.id = :campaignId AND s.id = :sceneId")
    Optional<Scene> findByIdAndCampaignIdForEncounterSeed(
            @Param("campaignId") UUID campaignId, @Param("sceneId") UUID sceneId);

    List<Scene> findBySceneAudioCueId(UUID cueId);

    @Query("SELECT s FROM Scene s JOIN s.chapter c JOIN c.adventure a WHERE a.campaign.id = :campaignId ORDER BY c.sortOrder ASC, s.sortOrder ASC")
    List<Scene> findByCampaignIdOrderByChapterAndSort(@Param("campaignId") UUID campaignId);

    /**
     * Every scene of many campaigns in one query, with the associations campaign readiness
     * reads already fetched. Assembling readiness for a campaign index one campaign at a time
     * costs two queries per card plus a lazy load per participant and statblock; on an index
     * of any size that is thousands of round trips.
     *
     * <p>{@code s.participants} is the only collection fetched — a second collection fetch
     * join would be a bag-fetch. The rest are to-one.
     */
    @Query("""
           SELECT DISTINCT s FROM Scene s
             JOIN FETCH s.chapter c
             JOIN FETCH c.adventure a
             LEFT JOIN FETCH s.participants p
             LEFT JOIN FETCH p.statBlock
             LEFT JOIN FETCH s.map
             LEFT JOIN FETCH s.encounter
           WHERE a.campaign.id IN :campaignIds
           ORDER BY c.sortOrder ASC, s.sortOrder ASC
           """)
    List<Scene> findForReadinessByCampaignIds(@Param("campaignIds") Collection<UUID> campaignIds);
}
