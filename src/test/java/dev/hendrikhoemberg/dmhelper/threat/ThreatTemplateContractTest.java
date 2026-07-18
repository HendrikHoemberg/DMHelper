package dev.hendrikhoemberg.dmhelper.threat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatTemplateContractTest {

    @Test
    void listHasCreateSearchAndScope() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/threat/list.html"));
        assertThat(html).contains("campaignId");
        assertThat(html).contains("name=\"text\"");
        assertThat(html).contains("New");
        assertThat(html).contains("createHref");
        assertThat(html).contains("/library/");
    }

    @Test
    void detailHasClonePromoteDependencyAwareDelete() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/threat/detail.html"));
        assertThat(html).contains("Clone");
        assertThat(html).contains("Promote");
        assertThat(html).contains("Delete");
        assertThat(html).contains("threatManagement");
        assertThat(html).contains("deletion-impact");
    }

    @Test
    void detailUsesUtextForSanitizedDescriptionAndTextForMechanics() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/threat/detail.html"));
        String card = Files.readString(Path.of("src/main/resources/templates/threat/_mechanics-card.html"));
        assertThat(detail).contains("th:utext=\"${markdownDescription}\"");
        assertThat(card).contains("th:text");
        assertThat(card).doesNotContain("th:utext");
    }

    @Test
    void formHasTypedDynamicMechanicsAndRefs() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/threat/form.html"));
        assertThat(html).contains("threatEditor");
        assertThat(html).contains("addDisarmMethod");
        assertThat(html).contains("removeDisarmMethod");
        assertThat(html).contains("moveDisarmMethod");
        assertThat(html).contains("showReferencePicker");
        assertThat(html).contains("damageTypes");
        assertThat(html).contains("detectionCheck");
        assertThat(html).contains("exposureMode");
        assertThat(html).contains("statBlockId");
    }

    @Test
    void provenanceFragmentDisplaysSourceFields() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/threat/_provenance.html"));
        assertThat(html).contains("provenance");
        assertThat(html).contains("sourceTitle");
        assertThat(html).contains("sourceLocator");
        assertThat(html).contains("licenseClassification");
    }

    @Test
    void editorSupportsStableKeysPerPathErrorsAndRetryableDmRequest() throws IOException {
        String editor = Files.readString(Path.of("src/main/resources/static/js/threat-editor.js"));
        String form = Files.readString(Path.of("src/main/resources/templates/threat/form.html"));

        assertThat(editor).contains("threatEditor");
        assertThat(editor).contains("addDisarmMethod");
        assertThat(editor).contains("removeDisarmMethod");
        assertThat(editor).contains("moveDisarmMethod");
        assertThat(editor).contains("fetchReferenceOptions");
        assertThat(editor).contains("problemsFor");
        assertThat(editor).contains("error.problem?.problems");
        assertThat(editor).contains("reportActionFailure");
        assertThat(editor).contains("TrapWrite");
        assertThat(editor).contains("HazardWrite");
        assertThat(form).contains("data-path");
    }

    @Test
    void appnavExposesDmOnlyTrapAndHazardLinks() throws IOException {
        String nav = Files.readString(Path.of("src/main/resources/templates/fragments/_appnav.html"));
        assertThat(nav).contains("/library/traps");
        assertThat(nav).contains("/library/hazards");
        assertThat(nav).contains("Traps");
        assertThat(nav).contains("Hazards");
    }

    @Test
    void controllerAddsMarkdownDescriptionAndKind() throws IOException {
        String java = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/threat/web/ThreatController.java"));
        assertThat(java).contains("model.addAttribute(\"markdownDescription\"");
        assertThat(java).contains("model.addAttribute(\"kind\"");
        assertThat(java).contains("/library/traps");
        assertThat(java).contains("/library/hazards");
    }
}
