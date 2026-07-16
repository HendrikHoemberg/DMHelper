package dev.hendrikhoemberg.dmhelper.quest.service;

import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveDependency;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class QuestObjectiveDependencyValidator {

    public void validate(UUID objectiveId, UUID prerequisiteObjectiveId,
                         List<QuestObjectiveDependency> existing) {
        if (objectiveId.equals(prerequisiteObjectiveId)) {
            throw new IllegalArgumentException("An objective cannot depend on itself");
        }

        for (QuestObjectiveDependency dep : existing) {
            if (dep.getObjective().getId().equals(objectiveId)
                    && dep.getPrerequisiteObjective().getId().equals(prerequisiteObjectiveId)) {
                throw new IllegalArgumentException("Duplicate dependency edge");
            }
        }

        if (wouldCreateCycle(objectiveId, prerequisiteObjectiveId, existing)) {
            throw new IllegalArgumentException("Adding this dependency would create a cycle");
        }
    }

    private boolean wouldCreateCycle(UUID objectiveId, UUID prerequisiteObjectiveId,
                                      List<QuestObjectiveDependency> existing) {
        Set<UUID> visited = new HashSet<>();
        return traversesTo(prerequisiteObjectiveId, objectiveId, existing, visited);
    }

    private boolean traversesTo(UUID current, UUID target,
                                 List<QuestObjectiveDependency> existing, Set<UUID> visited) {
        if (current.equals(target)) return true;
        if (!visited.add(current)) return false;
        for (QuestObjectiveDependency dep : existing) {
            if (dep.getObjective().getId().equals(current)) {
                if (traversesTo(dep.getPrerequisiteObjective().getId(), target, existing, visited)) {
                    return true;
                }
            }
        }
        return false;
    }
}
