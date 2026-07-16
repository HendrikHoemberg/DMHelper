package dev.hendrikhoemberg.dmhelper.session.data;

import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_objective_change", indexes = {
    @Index(name = "idx_session_obj_change", columnList = "session_id, changed_at, id")
})
public class SessionObjectiveChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CampaignSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "objective_id", nullable = false)
    private QuestObjective objective;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QuestObjectiveStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestObjectiveStatus newStatus;

    @Column(nullable = false)
    private Instant changedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public CampaignSession getSession() { return session; }
    public void setSession(CampaignSession session) { this.session = session; }

    public QuestObjective getObjective() { return objective; }
    public void setObjective(QuestObjective objective) { this.objective = objective; }

    public QuestObjectiveStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(QuestObjectiveStatus previousStatus) { this.previousStatus = previousStatus; }

    public QuestObjectiveStatus getNewStatus() { return newStatus; }
    public void setNewStatus(QuestObjectiveStatus newStatus) { this.newStatus = newStatus; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
