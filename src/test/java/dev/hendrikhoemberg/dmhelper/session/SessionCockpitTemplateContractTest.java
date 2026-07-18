package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCockpitTemplateContractTest {

    @Test
    void cockpitShowsRuntimeChoicesAndEditorialNavigationSeparately() throws IOException {
        String story = Files.readString(Path.of("src/main/resources/templates/session/_story-rail.html"));
        assertThat(story).contains("scene-editorial");
        assertThat(story).contains("transition-choice");
        assertThat(story).contains("stepScene");
        assertThat(story).contains("followTransition");
        assertThat(story).contains("scene-summary");
        assertThat(story).contains("scene-checks");
        assertThat(story).contains("structuredSceneView.checks");
    }

    @Test
    void questProgressAlwaysRenderedWithStatusControls() throws IOException {
        String plan = Files.readString(Path.of("src/main/resources/templates/session/_session-plan.html"));
        assertThat(plan).contains("data-quest-progress");
        assertThat(plan).contains("setObjectiveStatus");
        assertThat(plan).contains("objective-status-select");
        // Quest progress is not gated on sessionPlan == null
        assertThat(plan).doesNotContain("th:if=\"${workspace.sessionPlan == null}\">\n  <h3");
    }

    @Test
    void cockpitOwnsOneRuntimeIslandAndAccessibleRailControls() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        String rail = Files.readString(Path.of("src/main/resources/templates/session/_encounter-rail.html"));
        String story = Files.readString(Path.of("src/main/resources/templates/session/_story-rail.html"));
        String plan = Files.readString(Path.of("src/main/resources/templates/session/_session-plan.html"));
        String lifecycle = Files.readString(Path.of(
                "src/main/resources/templates/session/_lifecycle-dialog.html"));
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        String battleMap = Files.readString(Path.of(
                "src/main/resources/static/js/map/battle-map.js"));
        assertThat(count(html, "id=\"battleCanvasWrap\"")).isEqualTo(1);
        assertThat(count(rail, "encounter/_tracker :: tracker")).isEqualTo(1);
        assertThat(count(html, "session/_encounter-rail :: encounters")).isEqualTo(1);
        assertThat(js).contains("new BattleMap(");
        assertThat(js).doesNotContain("nextTurn(id)", "applyDamage(combatant", "projectTokens(");
        assertThat(html).contains("aria-label=\"Story rail\"", "aria-label=\"Encounter rail\"",
                "aria-label=\"Session plan\"", "aria-live=\"polite\"");
        assertThat(html).contains("@keydown.window=\"handleKeyboard($event)\"");
        assertThat(html.indexOf("session/_lifecycle-dialog :: lifecycle-dialog"))
                .isLessThan(html.indexOf("</main>"));
        assertThat(story).contains("scene-actions");
        assertThat(story).contains("notes/_quicknotes-strip :: strip");
        assertThat(plan).contains("Present");
        assertThat(html).contains(">Handouts<", ">Rules<", ">Calendar<");
        assertThat(html).contains("aria-label=\"Battle map controls\"", "aria-label=\"Workspace map\"",
                "Present current map", ">Curtain<", "data-presentation-mode");
        assertThat(html).contains("@click=\"addToken()\"", "@click=\"addParty()\"",
                "x-for=\"t in tokens\"");
        assertThat(js).contains("sessionStatus", "presentationMode", "handleKeyboard");
        assertThat(js).contains("startSession", "pauseSession", "resumeSession",
                "cancelReview", "beginReview", "completeSession");
        assertThat(js).contains("async presentHandout(", "async switchMap(");
        assertThat(js).contains("this.switchWorkspaceMap(mapId");
        assertThat(js).contains("startMapId: config.mapId || ''");
        assertThat(count(js, "async stepScene(direction)")).isEqualTo(1);
        assertThat(js).doesNotContain("new CustomEvent('scene-step'");
        assertThat(js).contains("document.querySelector('.quicknotes-form input')");
        assertThat(js).contains("getClientRects().length > 0");
        assertThat(js).contains("new CustomEvent('command-palette-toggle')",
                "new CustomEvent('dice-roller-toggle')");
        assertThat(js).doesNotContain("palette.__x", "dice.__x");
        assertThat(story).doesNotContain("@click=\"presentScene(");
        assertThat(lifecycle).contains("@keydown.tab=\"trapLifecycleFocus($event)\"",
                "@click.away=\"closeLifecycle()\"");
        assertThat(js).contains("trapLifecycleFocus(event)", "closeLifecycle()",
                "openLifecycle()", "lifecycleFocusable(container)");
        assertThat(js).contains("const previousMapId", "if (!switched) {",
                "await bm.switchToMap(previousMapId)");
        assertThat(battleMap).contains("const documentResponse", "const tokenResponse",
                "return true;", "return false;");
    }

    @Test
    void cockpitUsesSharedTablePanelForPickerAndDirectStoryRolls() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        String linked = Files.readString(Path.of("src/main/resources/templates/session/_linked-tables.html"));
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));

        assertThat(html).contains("rollable-table/_roll-panel :: roll-panel",
                "/js/rollable-table-roll.js", "openLinkedTable(");
        assertThat(linked).contains("rollLinkedTable(");
        assertThat(js).contains("new CustomEvent('table-roll-open'",
                "new CustomEvent('table-roll-direct'");
        assertThat(js).doesNotContain("/roll?campaignId=${encodeURIComponent(cid)}");
    }

    @Test
    void diceHistoryRefreshesAfterTableActionsAndMarksUnreadableRows() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        String navbar = Files.readString(Path.of("src/main/resources/templates/fragments/navbar.html"));
        String history = Files.readString(Path.of("src/main/resources/templates/fragments/_dice-roller.html"));
        String diceJs = Files.readString(Path.of("src/main/resources/static/js/dice-roller.js"));

        assertThat(html).contains("/js/dice-roller.js");
        assertThat(navbar).contains("/js/dice-roller.js");
        assertThat(diceJs).contains("table-history-refresh");
        assertThat(history).contains("Result unavailable");
    }

    @Test
    void storyRailRendersInlineThreatMechanicsCards() throws IOException {
        String story = Files.readString(Path.of("src/main/resources/templates/session/_story-rail.html"));
        assertThat(story).contains("threat/_mechanics-card");
        assertThat(story).contains("sectionThreatCards");
        assertThat(story).contains("section.threatId");
    }

    @Test
    void encounterRailUsesTrackerActiveThreatCard() throws IOException {
        String rail = Files.readString(Path.of("src/main/resources/templates/session/_encounter-rail.html"));
        String tracker = Files.readString(Path.of("src/main/resources/templates/encounter/_tracker.html"));
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(rail).contains("encounter/_tracker :: tracker");
        assertThat(rail).contains("threatCard");
        assertThat(tracker).contains("activeThreatCard");
        assertThat(tracker).contains("data-active-threat-card");
        assertThat(js).contains("threatCard");
        assertThat(js).contains("activeCombatants");
    }

    @Test
    void cockpitLoadsSharedDiceRollerScript() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        assertThat(html).contains("/js/dice-roller.js");
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
