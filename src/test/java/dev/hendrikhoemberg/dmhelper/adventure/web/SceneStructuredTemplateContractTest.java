package dev.hendrikhoemberg.dmhelper.adventure.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class SceneStructuredTemplateContractTest {

    @MockitoBean
    private CampaignRepository campaignRepository;

    @Test
    void structureEditorContainsSectionsChecksParticipantsTransitionsLinks() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("scene.sections", "scene.checks", "scene.participants",
                "scene.transitions", "scene.links");
    }

    @Test
    void structureEditorHasDeleteActionsForStructuredContent() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("hx-delete");
        assertThat(count(html, "hx-confirm=\"Delete this section?\"")).isGreaterThanOrEqualTo(1);
        assertThat(count(html, "hx-confirm=\"Delete this check?\"")).isGreaterThanOrEqualTo(1);
        assertThat(count(html, "hx-confirm=\"Delete this participant?\"")).isGreaterThanOrEqualTo(1);
        assertThat(count(html, "hx-confirm=\"Delete this transition?\"")).isGreaterThanOrEqualTo(1);
        assertThat(count(html, "hx-confirm=\"Delete this link?\"")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sceneSectionsShowsSourceLocatorForSections() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-sections.html"));
        assertThat(html).contains("section.sourceLocator");
    }

    @Test
    void structureEditorShowsCheckVisibilityAndOutcomes() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("check.visibility", "check.success", "check.failure");
    }

    @Test
    void structureEditorShowsParticipantDispositionQuantityPlacement() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("p.disposition", "p.quantity", "p.placementHint", "p.sourceLocator");
    }

    @Test
    void structureEditorShowsTransitionDetails() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("t.targetScene.title", "t.externalDestination", "t.condition", "t.dmNote");
    }

    @Test
    void structureEditorShowsLinkRoleAndCondition() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("#enums.label(link.role)", "link.condition");
    }

    @Test
    @Disabled("moves to the read rail in Task 6")
    void existingQuickNoteAndMapEncounterSelectorsPreserved() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("scene.statBlocks", "scene.handouts");
        assertThat(html).contains("hx-vals='{\"status\": \"VISITED\"}'");
        assertThat(html).contains("Set as Current Scene");
    }

    @Test
    void sceneDetailHasSceneBodyRendered() throws IOException {
        String detail = Files.readString(Path.of("src/main/resources/templates/adventure/scene-detail.html"));
        String body = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-body.html"));
        assertThat(body).contains("renderedBody");
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
    void structureEditorHasCreateFormsForStructuredChildren() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
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
    void structureEditorHasThreatSelectorForSections() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("name=\"threatId\"");
        assertThat(html).contains("visibleTraps");
        assertThat(html).contains("visibleHazards");
        assertThat(html).contains("dmHelperFilterSectionThreatOptions");
        assertThat(html).contains("data-threat-kind=\"TRAP\"");
        assertThat(html).contains("data-threat-kind=\"HAZARD\"");
    }

    @Test
    void sceneSectionsExposesThreatMechanicsCards() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-sections.html"));
        assertThat(html).contains("threat/_mechanics-card");
        assertThat(html).contains("sectionThreatCards");
    }

    @Test
    @Disabled("moves to the read rail in Task 6")
    void structureEditorOffersSeedEncounterOnlyWhenTheSceneHasResolvableParticipants() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("seed-encounter");
        assertThat(html).contains("canSeedEncounter");
        assertThat(html)
                .as("re-running must not be offered once the scene already has an encounter")
                .contains("scene.encounter == null");
    }

    @Test
    @Disabled("moves to the read rail in Task 6")
    void structureEditorReportsParticipantsTheSeedCouldNotResolve() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/adventure/_scene-structure-editor.html"));
        assertThat(html).contains("seedResult.skippedParticipants");
        assertThat(html).contains("seedResult.combatantsAdded");
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
