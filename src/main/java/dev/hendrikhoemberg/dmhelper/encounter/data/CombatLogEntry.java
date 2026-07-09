package dev.hendrikhoemberg.dmhelper.encounter.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "combat_log_entry", indexes = {
    @Index(name = "idx_log_encounter", columnList = "encounter_id"),
})
public class CombatLogEntry {

    public enum EntryType {
        INITIATIVE_SET, TURN_START, TURN_END, ROUND_ADVANCE,
        DAMAGE, HEAL, TEMP_HP,
        CONDITION_ADDED, CONDITION_REMOVED, CONDITION_TICKED,
        CONCENTRATION_SET, CONCENTRATION_LOST, CONCENTRATION_CHECK,
        RECHARGE,
        LEGENDARY_ACTION, LEGENDARY_RESISTANCE,
        DEFEATED, REVIVED, SET_HP,
        COMBATANT_ADDED, COMBATANT_REMOVED, COMBATANT_REORDERED,
        GROUP_SPLIT, LAIR_ACTION,
        ENCOUNTER_ACTIVATED, ENCOUNTER_ENDED, SESSION_END,
        DICE_ROLL, NOTE,
        SORT_ORDER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    private int round;

    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntryType type;

    @Column(nullable = false, length = 36)
    private String combatantId;

    @Column(columnDefinition = "CLOB")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Encounter getEncounter() { return encounter; }
    public void setEncounter(Encounter encounter) { this.encounter = encounter; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }

    public EntryType getType() { return type; }
    public void setType(EntryType type) { this.type = type; }

    public String getCombatantId() { return combatantId; }
    public void setCombatantId(String combatantId) { this.combatantId = combatantId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
