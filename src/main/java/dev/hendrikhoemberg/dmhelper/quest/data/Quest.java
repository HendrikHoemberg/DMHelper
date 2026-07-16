package dev.hendrikhoemberg.dmhelper.quest.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quest")
public class Quest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestStatus status = QuestStatus.NOT_STARTED;

    @Column(columnDefinition = "CLOB")
    private String summary;

    @Column(length = 500)
    private String sourceLocator;

    @Column(length = 1000)
    private String tags;

    @Column(columnDefinition = "CLOB")
    private String rewards;

    @Column(columnDefinition = "CLOB")
    private String prerequisites;

    @Column(columnDefinition = "CLOB")
    private String outcomeNotes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<QuestObjective> objectives = new ArrayList<>();

    @OneToMany(mappedBy = "quest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<QuestLink> links = new ArrayList<>();

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

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public QuestStatus getStatus() { return status; }
    public void setStatus(QuestStatus status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getRewards() { return rewards; }
    public void setRewards(String rewards) { this.rewards = rewards; }

    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }

    public String getOutcomeNotes() { return outcomeNotes; }
    public void setOutcomeNotes(String outcomeNotes) { this.outcomeNotes = outcomeNotes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<QuestObjective> getObjectives() { return objectives; }
    public void setObjectives(List<QuestObjective> objectives) { this.objectives = objectives; }

    public List<QuestLink> getLinks() { return links; }
    public void setLinks(List<QuestLink> links) { this.links = links; }
}
