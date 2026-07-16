package dev.hendrikhoemberg.dmhelper.quest.data;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quest_objective_dependency")
@IdClass(QuestObjectiveDependency.QuestObjectiveDependencyId.class)
public class QuestObjectiveDependency {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quest_id", nullable = false)
    private Quest quest;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "objective_id", nullable = false)
    private QuestObjective objective;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_objective_id", nullable = false)
    private QuestObjective prerequisiteObjective;

    public Quest getQuest() { return quest; }
    public void setQuest(Quest quest) { this.quest = quest; }

    public QuestObjective getObjective() { return objective; }
    public void setObjective(QuestObjective objective) { this.objective = objective; }

    public QuestObjective getPrerequisiteObjective() { return prerequisiteObjective; }
    public void setPrerequisiteObjective(QuestObjective prerequisiteObjective) { this.prerequisiteObjective = prerequisiteObjective; }

    public static class QuestObjectiveDependencyId implements Serializable {
        public UUID quest;
        public UUID objective;
        public UUID prerequisiteObjective;

        public QuestObjectiveDependencyId() {}

        public QuestObjectiveDependencyId(UUID quest, UUID objective, UUID prerequisiteObjective) {
            this.quest = quest;
            this.objective = objective;
            this.prerequisiteObjective = prerequisiteObjective;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QuestObjectiveDependencyId that)) return false;
            return Objects.equals(quest, that.quest) && Objects.equals(objective, that.objective) && Objects.equals(prerequisiteObjective, that.prerequisiteObjective);
        }

        @Override
        public int hashCode() {
            return Objects.hash(quest, objective, prerequisiteObjective);
        }
    }
}
