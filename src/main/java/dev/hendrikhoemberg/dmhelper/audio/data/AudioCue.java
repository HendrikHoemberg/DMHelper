package dev.hendrikhoemberg.dmhelper.audio.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audio_cue", uniqueConstraints = {
    @UniqueConstraint(name = "uq_audio_cue_campaign_key", columnNames = {"campaign_id", "cue_key"})
}, indexes = {
    @Index(name = "idx_audio_cue_campaign", columnList = "campaign_id"),
    @Index(name = "idx_audio_cue_name", columnList = "name"),
    @Index(name = "idx_audio_cue_provider_reference", columnList = "reference_kind, provider_reference")
})
public class AudioCue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "cue_key", nullable = false, length = 100)
    private String cueKey;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_kind", nullable = false, length = 16)
    private AudioReferenceKind referenceKind;

    @Column(name = "provider_reference", nullable = false, length = 500)
    private String providerReference;

    @Column(name = "cached_title", length = 500)
    private String cachedTitle;

    @Column(name = "artist_or_owner", length = 500)
    private String artistOrOwner;

    @Column(name = "artwork_url", length = 2000)
    private String artworkUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AudioCategory category;

    @Column(name = "volume_hint")
    private Integer volumeHint;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_preference", nullable = false, length = 16)
    private AudioTransitionPreference transitionPreference;

    @Column(columnDefinition = "CLOB")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getCueKey() { return cueKey; }
    public void setCueKey(String cueKey) { this.cueKey = cueKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public AudioReferenceKind getReferenceKind() { return referenceKind; }
    public void setReferenceKind(AudioReferenceKind referenceKind) { this.referenceKind = referenceKind; }

    public String getProviderReference() { return providerReference; }
    public void setProviderReference(String providerReference) { this.providerReference = providerReference; }

    public String getCachedTitle() { return cachedTitle; }
    public void setCachedTitle(String cachedTitle) { this.cachedTitle = cachedTitle; }

    public String getArtistOrOwner() { return artistOrOwner; }
    public void setArtistOrOwner(String artistOrOwner) { this.artistOrOwner = artistOrOwner; }

    public String getArtworkUrl() { return artworkUrl; }
    public void setArtworkUrl(String artworkUrl) { this.artworkUrl = artworkUrl; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public AudioCategory getCategory() { return category; }
    public void setCategory(AudioCategory category) { this.category = category; }

    public Integer getVolumeHint() { return volumeHint; }
    public void setVolumeHint(Integer volumeHint) { this.volumeHint = volumeHint; }

    public AudioTransitionPreference getTransitionPreference() { return transitionPreference; }
    public void setTransitionPreference(AudioTransitionPreference transitionPreference) { this.transitionPreference = transitionPreference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
