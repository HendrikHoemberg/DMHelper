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
    void encounterFragmentDeclaresModuleAttributes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_encounter.html"));
        assertThat(fragment).contains("data-cockpit-module-fragment");
        assertThat(fragment).contains("data-module-mode");
        assertThat(fragment).contains("data-module-empty");
        assertThat(fragment).contains("data-module-content-root");
        assertThat(fragment).contains("session/_encounter-rail");
        assertThat(fragment).contains("runtime-encounter");
    }

    @Test
    void encounterRailAcceptsViewAndMode() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_encounter-rail.html"));
        assertThat(rail).contains("th:fragment=\"encounters(view, mode)");
        assertThat(rail).doesNotContain("th:fragment=\"encounters(workspace)");
    }

    @Test
    void encounterRailUsesViewFieldsNotWorkspace() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_encounter-rail.html"));
        assertThat(rail).contains("view.planned");
        assertThat(rail).contains("enc.mapId");
        assertThat(rail).contains("enc.id");
        assertThat(rail).contains("enc.name");
        assertThat(rail).doesNotContain("workspace.activeEncounter");
        assertThat(rail).doesNotContain("workspace.plannedEncounters");
        assertThat(rail).doesNotContain("workspace.workspaceMap");
    }

    @Test
    void encounterFragmentEmptyStateShowsMessage() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_encounter.html"));
        assertThat(fragment).contains("Link or create an encounter");
    }

    @Test
    void encounterCompactModeShowsEssentials() throws IOException {
        // Essentials live in the tracker template, which is embedded in the rail
        String tracker = Files.readString(Path.of(
                "src/main/resources/templates/encounter/_tracker.html"));
        assertThat(tracker).contains("data-initiative-setup");
        assertThat(tracker).contains("encounter?.combatPhase === 'SETUP'");
        assertThat(tracker).contains("x-show=\"encounter?.combatPhase === 'RUNNING'\"");
        assertThat(tracker).contains("activeTurnIndex");
        assertThat(tracker).contains("round-counter");
        // Rail must embed the tracker
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_encounter-rail.html"));
        assertThat(rail).contains("encounter/_tracker :: tracker");
    }

    @Test
    void encounterStandardModeShowsFullTracker() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_encounter-rail.html"));
        assertThat(rail).contains("encounter/_tracker :: tracker");
        assertThat(rail).contains("Planned Encounters");
        assertThat(rail).contains("planned-encounter-row");
    }

    @Test
    void encounterFocusedModeShowsExpandedContent() throws IOException {
        // Expanded content lives in the tracker template
        String tracker = Files.readString(Path.of(
                "src/main/resources/templates/encounter/_tracker.html"));
        assertThat(tracker).contains("active-threat-card");
        assertThat(tracker).contains("combatant-detail");
        // Rail embeds the tracker
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_encounter-rail.html"));
        assertThat(rail).contains("encounter/_tracker :: tracker");
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

    @Test
    void partyFragmentDeclaresModuleAttributes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_party.html"));
        assertThat(fragment).contains("data-cockpit-module-fragment");
        assertThat(fragment).contains("data-module-mode");
        assertThat(fragment).contains("data-module-empty");
        assertThat(fragment).contains("data-module-content-root");
        assertThat(fragment).contains("party/_summary-bar");
        assertThat(fragment).contains("runtime-party");
    }

    @Test
    void partyFragmentUsesSummaryBarWithViewAndMode() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_party.html"));
        assertThat(fragment).contains("summary-bar(view=${view.members}, mode=${mode})");
        assertThat(fragment).doesNotContain("summary-bar(members=");
    }

    @Test
    void partyFragmentEmptyStateShowsMessage() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_party.html"));
        assertThat(fragment).contains("No party members yet");
    }

    @Test
    void partySummaryBarAcceptsViewAndMode() throws IOException {
        String bar = Files.readString(Path.of(
                "src/main/resources/templates/party/_summary-bar.html"));
        assertThat(bar).contains("th:fragment=\"summary-bar(view, mode)");
        assertThat(bar).doesNotContain("th:fragment=\"summary-bar(members");
    }

    @Test
    void partySummaryBarUsesViewFieldsNotPartyMemberEntities() throws IOException {
        String bar = Files.readString(Path.of(
                "src/main/resources/templates/party/_summary-bar.html"));
        assertThat(bar).contains("m.name");
        assertThat(bar).contains("m.ac");
        assertThat(bar).contains("m.currentHp");
        assertThat(bar).contains("m.maxHp");
        assertThat(bar).contains("m.passivePerception");
        assertThat(bar).contains("m.conditionsJson");
        assertThat(bar).contains("m.sheetUrl");
        assertThat(bar).doesNotContain("pm.characterName");
        assertThat(bar).doesNotContain("pm.ac");
    }

    @Test
    void partyCompactModeShowsEssentialsOnly() throws IOException {
        String bar = Files.readString(Path.of(
                "src/main/resources/templates/party/_summary-bar.html"));
        assertThat(bar).contains("mode.name() != 'COMPACT'");
    }

    @Test
    void partyModuleNoEditDeleteToggleControls() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_party.html"));
        assertThat(fragment).doesNotContain("Edit");
        assertThat(fragment).doesNotContain("toggle-active");
        assertThat(fragment).doesNotContain("Mark Inactive");
        assertThat(fragment).doesNotContain("Mark Active");
    }

    @Test
    void partySummaryBarParsesConditionsJson() throws IOException {
        String bar = Files.readString(Path.of(
                "src/main/resources/templates/party/_summary-bar.html"));
        assertThat(bar).contains("m.conditions");
        assertThat(bar).contains("conditionsJson");
    }

    @Test
    void partySummaryBarShowsIndividualConditionLabels() throws IOException {
        String bar = Files.readString(Path.of(
                "src/main/resources/templates/party/_summary-bar.html"));
        assertThat(bar).contains("th:each=\"cond : ${m.conditions}\"");
        assertThat(bar).contains("th:text=\"${cond}\"");
    }

    @Test
    void partyStandardModeShowsFullStats() throws IOException {
        String bar = Files.readString(Path.of(
                "src/main/resources/templates/party/_summary-bar.html"));
        assertThat(bar).contains("passiveInsight");
        assertThat(bar).contains("passiveInvestigation");
        assertThat(bar).contains("deathSaveSuccesses");
        assertThat(bar).contains("deathSaveFailures");
        assertThat(bar).contains("chip-status");
    }

    @Test
    void partyModuleUsesRuntimePartyClass() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_party.html"));
        assertThat(fragment).contains("runtime-party");
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
