package dev.hendrikhoemberg.dmhelper.world.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "world_relationship")
public class WorldRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RelationshipKind kind;

    @Column(nullable = false, length = 30)
    private String fromType;

    @Column(nullable = false)
    private UUID fromId;

    @Column(nullable = false, length = 30)
    private String toType;

    @Column(nullable = false)
    private UUID toId;

    @Column(nullable = false)
    private boolean directed = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RelationshipKnowledge knowledge = RelationshipKnowledge.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RelationshipStatus status = RelationshipStatus.ACTIVE;

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

    public RelationshipKind getKind() { return kind; }
    public void setKind(RelationshipKind kind) { this.kind = kind; }

    public String getFromType() { return fromType; }
    public void setFromType(String fromType) { this.fromType = fromType; }

    public UUID getFromId() { return fromId; }
    public void setFromId(UUID fromId) { this.fromId = fromId; }

    public String getToType() { return toType; }
    public void setToType(String toType) { this.toType = toType; }

    public UUID getToId() { return toId; }
    public void setToId(UUID toId) { this.toId = toId; }

    public boolean isDirected() { return directed; }
    public void setDirected(boolean directed) { this.directed = directed; }

    public RelationshipKnowledge getKnowledge() { return knowledge; }
    public void setKnowledge(RelationshipKnowledge knowledge) { this.knowledge = knowledge; }

    public RelationshipStatus getStatus() { return status; }
    public void setStatus(RelationshipStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
