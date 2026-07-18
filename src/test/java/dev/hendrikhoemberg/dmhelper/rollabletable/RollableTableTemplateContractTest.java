package dev.hendrikhoemberg.dmhelper.rollabletable;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RollableTableTemplateContractTest {

    @Test
    void detailContainsEntriesRollButtonAndSafeRendering() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/detail.html"));
        assertThat(html).contains("th:each=\"entry");
        assertThat(html).contains("Roll");
        assertThat(html).contains("th:text"); // safe rendering, no th:utext for entry result
    }

    @Test
    void detailUsesUtextForDescription() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/detail.html"));
        assertThat(html).contains("th:utext=\"${markdownDescription}\"");
    }

    @Test
    void formContainsAddRemoveReorderEntryControls() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/form.html"));
        assertThat(html).contains("addEntry");
        assertThat(html).contains("removeEntry");
        assertThat(html).contains("moveEntry");
    }

    @Test
    void formHasModeSpecificInputs() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/form.html"));
        assertThat(html).contains("addressMode");
        assertThat(html).contains("rangeStart");
        assertThat(html).contains("rangeEnd");
        assertThat(html).contains("weight");
    }

    @Test
    void formHasReferencePickerQuantityAndDuplicatePolicy() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/form.html"));
        assertThat(html).contains("showReferencePicker");
        assertThat(html).contains("quantityExpression");
    }

    @Test
    void rollPanelHasManualValueRollCountAndDuplicatePolicy() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/_roll-panel.html"));
        assertThat(html).contains("manualValue");
        assertThat(html).contains("rollCount");
        assertThat(html).contains("duplicatePolicy");
    }

    @Test
    void rollPanelRendersWithXTextNotXHtml() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/_roll-panel.html"));
        assertThat(html).contains("x-text");
        assertThat(html).doesNotContain("x-html");
    }

    @Test
    void rollPanelOwnsResultLogAndDraftStateAndRendersNestedOutcomes() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/_roll-panel.html"));
        String detail = Files.readString(Path.of("src/main/resources/templates/rollable-table/detail.html"));
        String javascript = Files.readString(Path.of("src/main/resources/static/js/rollable-table-roll.js"));

        assertThat(javascript).contains("rollId: null", "this.rollId = this.result.logId",
                "flattenOutcomes", "table-history-refresh", "referenceUrl");
        assertThat(javascript).doesNotContain("Alpine.data('tableDraftPanel'");
        assertThat(html).contains("nestedDepth", "encounter-draft", "reward-draft");
        assertThat(detail).doesNotContain("x-data=\"tableDraftPanel()\"");
    }

    @Test
    void listTemplateContainsFilterControls() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/list.html"));
        assertThat(html).contains("campaignId");
        assertThat(html).contains("category");
        assertThat(html).contains("tag");
        assertThat(html).contains("text");
    }

    @Test
    void managementControlsAreVisibleInListAndDetail() throws IOException {
        String list = Files.readString(Path.of("src/main/resources/templates/rollable-table/list.html"));
        String detail = Files.readString(Path.of("src/main/resources/templates/rollable-table/detail.html"));

        assertThat(list).contains("New Table", "/library/tables/new");
        assertThat(detail).contains("Clone", "Promote", "Delete", "tableManagement");
    }

    @Test
    void referencePickerFetchesAndAddsOptions() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/form.html"));
        String javascript = Files.readString(Path.of("src/main/resources/static/js/rollable-table-editor.js"));

        assertThat(html).contains("referencePicker", "referenceType", "referenceQuery");
        assertThat(javascript).contains("fetchReferenceOptions", "addReference", "reference-options");
        assertThat(javascript).doesNotContain("Reference picker to be implemented");
    }

    @Test
    void editorUsesStructuredProblemDetailsForInlineValidation() throws IOException {
        String request = Files.readString(Path.of("src/main/resources/static/js/dm-request.js"));
        String editor = Files.readString(Path.of("src/main/resources/static/js/rollable-table-editor.js"));

        assertThat(request).contains("error.problem = problem");
        assertThat(editor).contains("error.problem?.problems");
    }

    @Test
    void controllerAddsMarkdownDescriptionToModel() throws IOException {
        String java = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableController.java"));
        assertThat(java).contains("model.addAttribute(\"markdownDescription\"");
        assertThat(java).contains("model.addAttribute(\"table\"");
    }
}
