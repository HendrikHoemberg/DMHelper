package dev.hendrikhoemberg.dmhelper.world.data;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "faction_clock")
public class FactionClock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faction_id", nullable = false)
    private Faction faction;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private int segments;

    @Column(nullable = false)
    private int filled = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "objective_id")
    private QuestObjective objective;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id")
    private Scene scene;

    @Column(columnDefinition = "CLOB")
    private String notes;

    @Column(length = 500)
    private String sourceLocator;

    @Column(nullable = false)
    private int sortOrder = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public Faction getFaction() { return faction; }
    public void setFaction(Faction faction) { this.faction = faction; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getSegments() { return segments; }
    public void setSegments(int segments) { this.segments = segments; }

    public int getFilled() { return filled; }
    public void setFilled(int filled) { this.filled = filled; }

    public QuestObjective getObjective() { return objective; }
    public void setObjective(QuestObjective objective) { this.objective = objective; }

    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
