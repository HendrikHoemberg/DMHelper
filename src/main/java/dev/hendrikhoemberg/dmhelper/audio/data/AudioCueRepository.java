package dev.hendrikhoemberg.dmhelper.audio.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AudioCueRepository extends JpaRepository<AudioCue, UUID> {

    @Query("SELECT a FROM AudioCue a WHERE a.campaign.id = :campaignId ORDER BY a.name ASC")
    List<AudioCue> findByCampaignIdOrderByNameAsc(@Param("campaignId") UUID campaignId);

    @Query("SELECT a FROM AudioCue a WHERE a.campaign.id = :campaignId AND a.cueKey = :cueKey")
    Optional<AudioCue> findByCampaignIdAndCueKey(@Param("campaignId") UUID campaignId, @Param("cueKey") String cueKey);

    @Query("SELECT a FROM AudioCue a WHERE a.referenceKind = :kind AND a.providerReference = :reference AND a.campaign.id = :campaignId")
    List<AudioCue> findByReferenceKindAndProviderReferenceAndCampaignId(
            @Param("kind") AudioReferenceKind kind,
            @Param("reference") String providerReference,
            @Param("campaignId") UUID campaignId);

    @Query("SELECT a FROM AudioCue a WHERE a.campaign.id = :campaignId AND a.providerId = :providerId "
            + "AND a.referenceKind = :kind AND a.providerReference = :reference")
    List<AudioCue> findByProviderReference(
            @Param("campaignId") UUID campaignId,
            @Param("providerId") String providerId,
            @Param("kind") AudioReferenceKind kind,
            @Param("reference") String providerReference);

    @Query("SELECT a FROM AudioCue a LEFT JOIN FETCH a.campaign WHERE a.id = :id")
    Optional<AudioCue> findDetailedById(@Param("id") UUID id);
}
