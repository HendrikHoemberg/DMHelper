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
    void listTemplateContainsFilterControls() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/rollable-table/list.html"));
        assertThat(html).contains("campaignId");
        assertThat(html).contains("category");
        assertThat(html).contains("tag");
        assertThat(html).contains("text");
    }

    @Test
    void controllerAddsMarkdownDescriptionToModel() throws IOException {
        String java = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/rollabletable/web/RollableTableController.java"));
        assertThat(java).contains("model.addAttribute(\"markdownDescription\"");
        assertThat(java).contains("model.addAttribute(\"table\"");
    }
}
