package dev.hendrikhoemberg.dmhelper.notes.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuickNoteRepository extends JpaRepository<QuickNote, UUID> {

    List<QuickNote> findByCampaignIdAndTargetTypeAndTargetIdOrderByCreatedAtAsc(
            UUID campaignId, String targetType, UUID targetId);

    List<QuickNote> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    @Query("SELECT qn FROM QuickNote qn WHERE qn.campaign.id = :campaignId " +
           "AND LOWER(qn.body) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY qn.createdAt DESC")
    List<QuickNote> searchByCampaignId(@Param("campaignId") UUID campaignId,
                                       @Param("search") String search);

    void deleteByTargetTypeAndTargetId(String targetType, UUID targetId);
}
