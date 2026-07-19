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

    @Column(name = "accepted_source_kind", length = 32)
    private String acceptedSourceKind;

    @Column(name = "accepted_source_id")
    private UUID acceptedSourceId;

    @Column(name = "accepted_source_label", length = 500)
    private String acceptedSourceLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_cue_id")
    private AudioCue pendingCue;

    @Column(name = "pending_source_kind", length = 32)
    private String pendingSourceKind;

    @Column(name = "pending_source_id")
    private UUID pendingSourceId;

    @Column(name = "pending_source_label", length = 500)
    private String pendingSourceLabel;

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

    @Column(name = "victory_source_id")
    private UUID victorySourceId;

    @Column(name = "victory_source_label", length = 500)
    private String victorySourceLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "switch_mode", nullable = false, length = 16)
    private AudioSwitchMode switchMode = AudioSwitchMode.AUTOMATIC;

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

    public String getAcceptedSourceKind() { return acceptedSourceKind; }
    public void setAcceptedSourceKind(String acceptedSourceKind) { this.acceptedSourceKind = acceptedSourceKind; }
    public UUID getAcceptedSourceId() { return acceptedSourceId; }
    public void setAcceptedSourceId(UUID acceptedSourceId) { this.acceptedSourceId = acceptedSourceId; }
    public String getAcceptedSourceLabel() { return acceptedSourceLabel; }
    public void setAcceptedSourceLabel(String acceptedSourceLabel) { this.acceptedSourceLabel = acceptedSourceLabel; }

    public AudioCue getPendingCue() { return pendingCue; }
    public void setPendingCue(AudioCue pendingCue) { this.pendingCue = pendingCue; }

    public String getPendingSourceKind() { return pendingSourceKind; }
    public void setPendingSourceKind(String pendingSourceKind) { this.pendingSourceKind = pendingSourceKind; }
    public UUID getPendingSourceId() { return pendingSourceId; }
    public void setPendingSourceId(UUID pendingSourceId) { this.pendingSourceId = pendingSourceId; }
    public String getPendingSourceLabel() { return pendingSourceLabel; }
    public void setPendingSourceLabel(String pendingSourceLabel) { this.pendingSourceLabel = pendingSourceLabel; }

    public AudioCue getDismissedCandidateCue() { return dismissedCandidateCue; }
    public void setDismissedCandidateCue(AudioCue dismissedCandidateCue) { this.dismissedCandidateCue = dismissedCandidateCue; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public AudioCue getTemporaryVictoryCue() { return temporaryVictoryCue; }
    public void setTemporaryVictoryCue(AudioCue temporaryVictoryCue) { this.temporaryVictoryCue = temporaryVictoryCue; }

    public Instant getVictoryUntil() { return victoryUntil; }
    public void setVictoryUntil(Instant victoryUntil) { this.victoryUntil = victoryUntil; }

    public UUID getVictorySourceId() { return victorySourceId; }
    public void setVictorySourceId(UUID victorySourceId) { this.victorySourceId = victorySourceId; }
    public String getVictorySourceLabel() { return victorySourceLabel; }
    public void setVictorySourceLabel(String victorySourceLabel) { this.victorySourceLabel = victorySourceLabel; }

    public AudioSwitchMode getSwitchMode() { return switchMode; }
    public void setSwitchMode(AudioSwitchMode switchMode) { this.switchMode = switchMode; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
