package dev.hendrikhoemberg.dmhelper.world.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "faction")
public class Faction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String goals;

    @Column(columnDefinition = "CLOB")
    private String resources;

    @Column(columnDefinition = "CLOB")
    private String reputationNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note note;

    @Column(length = 1000)
    private String tags;

    @Column(length = 500)
    private String sourceLocator;

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

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGoals() { return goals; }
    public void setGoals(String goals) { this.goals = goals; }

    public String getResources() { return resources; }
    public void setResources(String resources) { this.resources = resources; }

    public String getReputationNotes() { return reputationNotes; }
    public void setReputationNotes(String reputationNotes) { this.reputationNotes = reputationNotes; }

    public Note getNote() { return note; }
    public void setNote(Note note) { this.note = note; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
