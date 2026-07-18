package dev.hendrikhoemberg.dmhelper.adventure.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SceneStructuredTemplateContractTest {

    @Test
    void actionRailContainsSectionsChecksParticipantsTransitionsLinks() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("scene.sections", "scene.checks", "scene.participants",
                "scene.transitions", "scene.links");
    }

    @Test
    void actionRailHasDeleteActionsForStructuredContent() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("hx-delete");
        assertThat(count(html, "hx-confirm=\"Delete this section?\"")).isGreaterThanOrEqualTo(1);
        assertThat(count(html, "hx-confirm=\"Delete this check?\"")).isGreaterThanOrEqualTo(1);
        assertThat(count(html, "hx-confirm=\"Delete this participant?\"")).isGreaterThanOrEqualTo(1);
        assertThat(count(html, "hx-confirm=\"Delete this transition?\"")).isGreaterThanOrEqualTo(1);
        assertThat(count(html, "hx-confirm=\"Delete this link?\"")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void actionRailShowsSourceLocatorForSections() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("section.sourceLocator");
    }

    @Test
    void actionRailShowsCheckVisibilityAndOutcomes() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("check.visibility", "check.success", "check.failure");
    }

    @Test
    void actionRailShowsParticipantDispositionQuantityPlacement() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("p.disposition", "p.quantity", "p.placementHint", "p.sourceLocator");
    }

    @Test
    void actionRailShowsTransitionDetails() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("t.targetScene.title", "t.externalDestination", "t.condition", "t.dmNote");
    }

    @Test
    void actionRailShowsLinkRoleAndCondition() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("link.role.name()", "link.condition");
    }

    @Test
    void existingQuickNoteAndMapEncounterSelectorsPreserved() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("scene.statBlocks", "scene.handouts");
        assertThat(html).contains("hx-vals='{\"status\": \"VISITED\"}'");
        assertThat(html).contains("Set as Current Scene");
    }

    @Test
    void sceneDetailHasSceneBodyRendered() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/adventure/scene-detail.html"));
        assertThat(detail).contains("renderedBody");
        assertThat(detail).contains("sceneBody");
        assertThat(detail).contains("notes/_quicknotes-strip");
    }

    @Test
    void sceneFormPreservesMapEncounterStatblockHandoutSelectors() throws IOException {
        String form = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-form.html"));
        assertThat(form).contains("mapId", "encounterId", "statBlockId", "handoutId");
    }

    @Test
    void sceneFormExposesMetadataAndSceneNotesLabel() throws IOException {
        String form = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-form.html"));
        assertThat(form).contains("Scene notes", "name=\"summary\"", "name=\"sourceLocator\"",
                "name=\"tags\"", "name=\"mapRegionKey\"");
    }

    @Test
    void actionRailHasCreateFormsForStructuredChildren() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("data-create=\"section\"", "data-create=\"check\"",
                "data-create=\"participant\"", "data-create=\"transition\"", "data-create=\"link\"");
        assertThat(html).contains("data-structured-metadata", "name=\"mapRegionKey\"");
    }

    @Test
    void scenePanelLabelsBodyAsSceneNotes() throws IOException {
        String panel = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-panel.html"));
        assertThat(panel).contains("Scene notes");
        assertThat(panel).doesNotContain(">Read Aloud</summary>");
    }

    @Test
    void actionRailExposesThreatSelectorAndMechanicsCardForTrapHazardSections() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_action-rail.html"));
        assertThat(html).contains("name=\"threatId\"");
        assertThat(html).contains("visibleTraps");
        assertThat(html).contains("visibleHazards");
        assertThat(html).contains("threat/_mechanics-card");
        assertThat(html).contains("sectionThreatCards");
    }

    private static int count(String s, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
