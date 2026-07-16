package dev.hendrikhoemberg.dmhelper.quest.data;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "quest_link")
public class QuestLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestLinkRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SceneLinkTargetScope targetScope;

    @Column(nullable = false, length = 30)
    private String targetType;

    @Column(nullable = false)
    private UUID targetId;

    @Column(length = 100)
    private String catalogRuleset;

    @Column(length = 100)
    private String catalogSourceKey;

    @Column(length = 500)
    private String displayText;

    @Column(length = 2000)
    private String condition;

    @Column(nullable = false)
    private int sortOrder = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Quest getQuest() { return quest; }
    public void setQuest(Quest quest) { this.quest = quest; }

    public QuestLinkRole getRole() { return role; }
    public void setRole(QuestLinkRole role) { this.role = role; }

    public SceneLinkTargetScope getTargetScope() { return targetScope; }
    public void setTargetScope(SceneLinkTargetScope targetScope) { this.targetScope = targetScope; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }

    public String getCatalogRuleset() { return catalogRuleset; }
    public void setCatalogRuleset(String catalogRuleset) { this.catalogRuleset = catalogRuleset; }

    public String getCatalogSourceKey() { return catalogSourceKey; }
    public void setCatalogSourceKey(String catalogSourceKey) { this.catalogSourceKey = catalogSourceKey; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
