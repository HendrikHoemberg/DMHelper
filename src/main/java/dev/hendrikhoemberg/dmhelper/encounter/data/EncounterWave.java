package dev.hendrikhoemberg.dmhelper.encounter.data;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "encounter_wave", indexes = {
    @Index(name = "idx_wave_encounter", columnList = "encounter_id")
})
public class EncounterWave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(name = "wave_key", nullable = false, length = 100)
    private String waveKey;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WaveStatus status = WaveStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_kind", nullable = false, length = 24)
    private WaveTriggerKind triggerKind = WaveTriggerKind.MANUAL;

    @Column(name = "trigger_value", length = 255)
    private String triggerValue;

    @Column(columnDefinition = "CLOB")
    private String notes;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public String getWaveKey() { return waveKey; }
    public void setWaveKey(String waveKey) { this.waveKey = waveKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public WaveStatus getStatus() { return status; }
    public void setStatus(WaveStatus status) { this.status = status; }

    public WaveTriggerKind getTriggerKind() { return triggerKind; }
    public void setTriggerKind(WaveTriggerKind triggerKind) { this.triggerKind = triggerKind; }

    public String getTriggerValue() { return triggerValue; }
    public void setTriggerValue(String triggerValue) { this.triggerValue = triggerValue; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
