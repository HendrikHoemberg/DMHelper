package dev.hendrikhoemberg.dmhelper.notes.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByCampaignIdOrderByCreatedAtDesc(UUID campaignId);

    List<Note> findByCampaignIdAndTypeOrderByCreatedAtDesc(UUID campaignId, NoteType type);

    @Query("SELECT n FROM Note n WHERE n.campaign.id = :campaignId " +
           "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(n.body) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY n.createdAt DESC")
    List<Note> searchByCampaignId(@Param("campaignId") UUID campaignId,
                                  @Param("search") String search);

    @Query("SELECT n FROM Note n WHERE n.campaign.id = :campaignId " +
           "AND LOWER(n.title) = LOWER(:title)")
    List<Note> findByCampaignIdAndTitle(@Param("campaignId") UUID campaignId,
                                        @Param("title") String title);
}
