package dev.hendrikhoemberg.dmhelper.encounter.data;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "encounter", indexes = {
    @Index(name = "idx_encounter_campaign", columnList = "campaign_id"),
    @Index(name = "idx_encounter_map", columnList = "map_id"),
})
public class Encounter {

    public enum Status { PLANNED, ACTIVE, SUSPENDED, DONE }
    public enum CombatPhase { SETUP, RUNNING }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_id")
    private GameMap map;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PLANNED;

    @Column(nullable = false)
    private int round = 0;

    @Column(nullable = false)
    private int activeTurnIndex = -1;

    @Column(nullable = false)
    private long logSequence = 0;

    @Column(length = 255)
    private String lairActionName;

    @Column(columnDefinition = "CLOB")
    private String lairActionDescription;

    @Column(nullable = false)
    private boolean lairActionTriggered = false;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "combat_phase", nullable = false, length = 16)
    private CombatPhase combatPhase = CombatPhase.SETUP;

    @Column(name = "encounter_key", length = 100)
    private String encounterKey;

    @Column(name = "prep_json", columnDefinition = "CLOB")
    private String prepJson;

    @Column(name = "rewards_json", columnDefinition = "CLOB")
    private String rewardsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combat_audio_cue_id")
    private AudioCue combatAudioCue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "victory_audio_cue_id")
    private AudioCue victoryAudioCue;

    @Column(name = "victory_cue_duration_seconds")
    private Integer victoryCueDurationSeconds;

    public String getEncounterKey() { return encounterKey; }
    public void setEncounterKey(String encounterKey) { this.encounterKey = encounterKey; }

    public String getPrepJson() { return prepJson; }
    public void setPrepJson(String prepJson) { this.prepJson = prepJson; }

    public String getRewardsJson() { return rewardsJson; }
    public void setRewardsJson(String rewardsJson) { this.rewardsJson = rewardsJson; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public GameMap getMap() { return map; }
    public void setMap(GameMap map) { this.map = map; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public int getActiveTurnIndex() { return activeTurnIndex; }
    public void setActiveTurnIndex(int activeTurnIndex) { this.activeTurnIndex = activeTurnIndex; }

    public long getLogSequence() { return logSequence; }
    public void setLogSequence(long logSequence) { this.logSequence = logSequence; }

    public String getLairActionName() { return lairActionName; }
    public void setLairActionName(String lairActionName) { this.lairActionName = lairActionName; }

    public String getLairActionDescription() { return lairActionDescription; }
    public void setLairActionDescription(String lairActionDescription) { this.lairActionDescription = lairActionDescription; }

    public boolean isLairActionTriggered() { return lairActionTriggered; }
    public void setLairActionTriggered(boolean lairActionTriggered) { this.lairActionTriggered = lairActionTriggered; }

    public CombatPhase getCombatPhase() { return combatPhase; }
    public void setCombatPhase(CombatPhase combatPhase) {
        this.combatPhase = combatPhase == null ? CombatPhase.SETUP : combatPhase;
    }

    public AudioCue getCombatAudioCue() { return combatAudioCue; }
    public void setCombatAudioCue(AudioCue combatAudioCue) { this.combatAudioCue = combatAudioCue; }

    public AudioCue getVictoryAudioCue() { return victoryAudioCue; }
    public void setVictoryAudioCue(AudioCue victoryAudioCue) { this.victoryAudioCue = victoryAudioCue; }

    public Integer getVictoryCueDurationSeconds() { return victoryCueDurationSeconds; }
    public void setVictoryCueDurationSeconds(Integer victoryCueDurationSeconds) { this.victoryCueDurationSeconds = victoryCueDurationSeconds; }
}
