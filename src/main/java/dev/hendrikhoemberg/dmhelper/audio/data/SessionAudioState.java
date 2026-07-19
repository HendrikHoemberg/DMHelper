package dev.hendrikhoemberg.dmhelper.audio.data;

import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_audio_state", uniqueConstraints = {
    @UniqueConstraint(name = "uq_session_audio_state_session", columnNames = "session_id")
})
public class SessionAudioState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CampaignSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_override_cue_id")
    private AudioCue manualOverrideCue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_automatic_cue_id")
    private AudioCue acceptedAutomaticCue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_cue_id")
    private AudioCue pendingCue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dismissed_candidate_cue_id")
    private AudioCue dismissedCandidateCue;

    @Column(nullable = false)
    private boolean muted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "temporary_victory_cue_id")
    private AudioCue temporaryVictoryCue;

    @Column(name = "victory_until")
    private Instant victoryUntil;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public CampaignSession getSession() { return session; }
    public void setSession(CampaignSession session) { this.session = session; }

    public AudioCue getManualOverrideCue() { return manualOverrideCue; }
    public void setManualOverrideCue(AudioCue manualOverrideCue) { this.manualOverrideCue = manualOverrideCue; }

    public AudioCue getAcceptedAutomaticCue() { return acceptedAutomaticCue; }
    public void setAcceptedAutomaticCue(AudioCue acceptedAutomaticCue) { this.acceptedAutomaticCue = acceptedAutomaticCue; }

    public AudioCue getPendingCue() { return pendingCue; }
    public void setPendingCue(AudioCue pendingCue) { this.pendingCue = pendingCue; }

    public AudioCue getDismissedCandidateCue() { return dismissedCandidateCue; }
    public void setDismissedCandidateCue(AudioCue dismissedCandidateCue) { this.dismissedCandidateCue = dismissedCandidateCue; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public AudioCue getTemporaryVictoryCue() { return temporaryVictoryCue; }
    public void setTemporaryVictoryCue(AudioCue temporaryVictoryCue) { this.temporaryVictoryCue = temporaryVictoryCue; }

    public Instant getVictoryUntil() { return victoryUntil; }
    public void setVictoryUntil(Instant victoryUntil) { this.victoryUntil = victoryUntil; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
