package dev.hendrikhoemberg.dmhelper.quest.service;

import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveDependency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestObjectiveDependencyValidatorTest {

    private final QuestObjectiveDependencyValidator validator = new QuestObjectiveDependencyValidator();

    @Test
    void rejectsSelfDependency() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> validator.validate(id, id, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateEdge() {
        UUID objId = UUID.randomUUID();
        UUID prereqId = UUID.randomUUID();
        QuestObjectiveDependency existing = dependency(objId, prereqId);
        assertThatThrownBy(() -> validator.validate(objId, prereqId, List.of(existing)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsCycle() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<QuestObjectiveDependency> existing = List.of(
                dependency(a, b),
                dependency(b, c)
        );
        assertThatThrownBy(() -> validator.validate(c, a, existing))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsValidDependency() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThatCode(() -> validator.validate(a, b, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsAllBranching() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        assertThatCode(() -> validator.validate(a, b, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(a, c, List.of(dependency(a, b))))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsAnyBranching() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        assertThatCode(() -> validator.validate(a, b, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(c, b, List.of(dependency(a, b))))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsDeepChain() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        assertThatCode(() -> validator.validate(b, a, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(c, b, List.of(dependency(b, a))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(d, c, List.of(dependency(b, a), dependency(c, b))))
                .doesNotThrowAnyException();
    }

    private QuestObjectiveDependency dependency(UUID objectiveId, UUID prereqId) {
        QuestObjectiveDependency dep = new QuestObjectiveDependency();
        dep.setObjective(objective(objectiveId));
        dep.setPrerequisiteObjective(objective(prereqId));
        return dep;
    }

    private QuestObjective objective(UUID id) {
        QuestObjective obj = new QuestObjective();
        obj.setId(id);
        return obj;
    }
}
