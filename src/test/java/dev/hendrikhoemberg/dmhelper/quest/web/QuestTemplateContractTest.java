package dev.hendrikhoemberg.dmhelper.quest.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuestTemplateContractTest {

    @Test
    void listRendersQuestCards() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/list.html"));
        assertThat(html).contains("th:each=\"quest", "quest.title", "quest.status");
        assertThat(html).contains("card-grid");
    }

    @Test
    void detailRendersObjectivesWithStatus() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/detail.html"));
        assertThat(html).contains("quest/_objective-list :: objectiveList");
        assertThat(html).contains("quest/_dependency-list :: dependencyList");
        assertThat(html).contains("quest/_link-list :: linkList");
    }

    @Test
    void objectiveListIteratesObjectives() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/_objective-list.html"));
        assertThat(html).contains("obj.status");
        assertThat(html).contains("obj.title");
        assertThat(html).contains("obj.sortOrder");
    }

    @Test
    void formRendersQuestFields() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/_form.html"));
        assertThat(html).contains("name=\"title\"", "name=\"status\"", "name=\"summary\"",
                "name=\"sourceLocator\"", "name=\"tags\"", "name=\"prerequisites\"",
                "name=\"rewards\"", "name=\"outcomeNotes\"");
    }

    @Test
    void objectiveListHasStatusActions() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/_objective-list.html"));
        assertThat(html).contains("obj.status", "hx-post");
        assertThat(html).contains("COMPLETED", "ACTIVE", "NOT_STARTED");
        assertThat(html).contains("hx-confirm=\"Delete this objective?\"");
    }

    @Test
    void dependencyListShowsPrerequisites() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/_dependency-list.html"));
        assertThat(html).contains("dep.prerequisiteObjective.title", "dep.objective.id");
        assertThat(html).contains("hx-confirm=\"Remove this dependency?\"");
    }

    @Test
    void linkListShowsRoleAndCondition() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/_link-list.html"));
        assertThat(html).contains("link.role.name()", "link.displayText", "link.condition");
        assertThat(html).contains("hx-confirm=\"Delete this link?\"");
    }

    @Test
    void annotationListShowsSourceAnnotations() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/_annotation-list.html"));
        assertThat(html).contains("quest.sourceLocator", "obj.sourceLocator");
    }

    @Test
    void errorResponsesRendered() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/quest/list.html"));
        assertThat(html).contains("common/_error");
    }
}
