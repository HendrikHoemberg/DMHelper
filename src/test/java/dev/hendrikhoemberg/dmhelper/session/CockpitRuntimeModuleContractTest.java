package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitRuntimeModuleContractTest {

    @Test
    void moduleShellDeclaresEndpointDataAttributes() throws IOException {
        String shell = Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-module-shell.html"));
        assertThat(shell).contains(
                "data-module-endpoint",
                "data-module-loaded",
                "data-module-stale",
                "data-module-content");
        assertThat(count(shell, "data-module-content")).isEqualTo(1);
    }

    @Test
    void storyFragmentDeclaresModuleAttributes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_story.html"));
        assertThat(fragment).contains("data-cockpit-module-fragment");
        assertThat(fragment).contains("data-module-mode");
        assertThat(fragment).contains("data-module-empty");
        assertThat(fragment).contains("data-module-content-root");
        assertThat(fragment).contains("session/_story-rail");
        assertThat(fragment).contains("runtime-story");
    }

    @Test
    void storyRailAcceptsViewAndMode() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_story-rail.html"));
        assertThat(rail).contains("th:fragment=\"story(view, mode, campaignId)");
        assertThat(rail).doesNotContain("th:fragment=\"story(workspace)");
    }

    @Test
    void storyRailUsesViewFieldsNotWorkspace() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_story-rail.html"));
        assertThat(rail).contains("view.title");
        assertThat(rail).contains("view.readAloud");
        assertThat(rail).contains("view.sections");
        assertThat(rail).contains("view.checks");
        assertThat(rail).contains("view.participants");
        assertThat(rail).contains("view.transitions");
        assertThat(rail).contains("view.canSeedEncounter");
        assertThat(rail).doesNotContain("workspace.currentScene");
        assertThat(rail).doesNotContain("workspace.structuredSceneView");
    }

    @Test
    void storyRailReadAloudIsNeverScreenSensitive() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_story-rail.html"));
        assertThat(rail).contains("structured-read-aloud");
        int readAloudIdx = rail.indexOf("structured-read-aloud");
        int readAloudEnd = rail.indexOf("structured-block", readAloudIdx + 1);
        if (readAloudEnd < 0) readAloudEnd = rail.length();
        String readAloudBlock = rail.substring(readAloudIdx, readAloudEnd);
        assertThat(readAloudBlock)
                .as("read-aloud block must not contain data-screen-sensitive")
                .doesNotContain("data-screen-sensitive");
    }

    @Test
    void storyRailDmContentHasScreenSensitiveMarker() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_story-rail.html"));
        assertThat(rail).contains("data-screen-sensitive");
        assertThat(rail).contains("scene-checks");
        assertThat(rail).contains("scene-participants");
    }

    @Test
    void storyFragmentEmptyStateShowsMessage() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_story.html"));
        assertThat(fragment).contains("No current scene yet");
    }



    @Test
    void storyRailCompactModeExcludesDmContent() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_story-rail.html"));
        assertThat(rail).contains("scene-editorial");
        assertThat(rail).contains("seedCurrentScene");
        // Compact mode: look for th:if conditions that filter on mode
        assertThat(rail).contains("scene-participants");
        assertThat(rail).contains("scene-checks");
    }

    @Test
    void storyRailIncludesNavAndSeedInAllModes() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_story-rail.html"));
        assertThat(rail).contains("stepScene");
        assertThat(rail).contains("scene-actions");
        assertThat(rail).contains("Start encounter from this scene");
    }

    @Test
    void storyRailStandardModeHasAllContent() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_story-rail.html"));
        assertThat(rail).contains("scene-transitions");
        assertThat(rail).contains("transition-choice");
        assertThat(rail).contains("sectionThreatCards");
    }

    @Test
    void storyRailLinksAndThreatCardsRendered() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_story-rail.html"));
        assertThat(rail).contains("view.links");
        assertThat(rail).contains("view.sectionThreatCards");
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
