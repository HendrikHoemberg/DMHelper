package dev.hendrikhoemberg.dmhelper.quest.data;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quest_objective", uniqueConstraints = {
    @UniqueConstraint(name = "uq_quest_objective_quest_sort", columnNames = {"quest_id", "sort_order"})
})
public class QuestObjective {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestObjectiveStatus status = QuestObjectiveStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestObjectiveCompletionMode completionMode = QuestObjectiveCompletionMode.ALL;

    @Column(nullable = false)
    private int sortOrder = 0;

    @Column(length = 500)
    private String sourceLocator;

    @OneToMany(mappedBy = "objective", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuestObjectiveDependency> dependencies = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Quest getQuest() { return quest; }
    public void setQuest(Quest quest) { this.quest = quest; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public QuestObjectiveStatus getStatus() { return status; }
    public void setStatus(QuestObjectiveStatus status) { this.status = status; }

    public QuestObjectiveCompletionMode getCompletionMode() { return completionMode; }
    public void setCompletionMode(QuestObjectiveCompletionMode completionMode) { this.completionMode = completionMode; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public List<QuestObjectiveDependency> getDependencies() { return dependencies; }
    public void setDependencies(List<QuestObjectiveDependency> dependencies) { this.dependencies = dependencies; }
}
