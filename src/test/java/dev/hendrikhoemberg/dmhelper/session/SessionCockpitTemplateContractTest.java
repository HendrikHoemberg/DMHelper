package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCockpitTemplateContractTest {

    @Test
    void sessionActionsUseApplicationDialogsAndOfferSafeExit() throws IOException {
        String cockpit = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        String mapModule = Files.readString(Path.of("src/main/resources/templates/session/_map-module.html"));
        String tracker = Files.readString(Path.of("src/main/resources/templates/encounter/_tracker.html"));
        String cockpitJs = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        String battleMap = Files.readString(Path.of("src/main/resources/static/js/map/battle-map.js"));

        assertThat(cockpit).contains("Leave cockpit", "data-encounter-end-dialog");
        assertThat(mapModule).contains("data-token-dialog", "data-token-delete-dialog");
        assertThat(tracker).contains("request-encounter-end");
        assertThat(cockpitJs).contains("openTokenDialog()", "submitTokenDialog()",
                "requestTokenDelete(id)", "confirmTokenDelete()", "confirmEncounterEnd()");
        assertThat(cockpitJs).doesNotContain("prompt(", "confirm(");
        assertThat(battleMap).doesNotContain("prompt(");
    }

    @Test
    void cockpitShowsRuntimeChoicesAndEditorialNavigationSeparately() throws IOException {
        String story = Files.readString(Path.of("src/main/resources/templates/session/_story-rail.html"));
        assertThat(story).contains("scene-editorial");
        assertThat(story).contains("transition-choice");
        assertThat(story).contains("stepScene");
        assertThat(story).contains("followTransition");
        assertThat(story).contains("scene-checks");
        assertThat(story).contains("view.checks");
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
        String mapModule = Files.readString(Path.of("src/main/resources/templates/session/_map-module.html"));
        String shell = Files.readString(Path.of("src/main/resources/templates/session/_cockpit-module-shell.html"));
        String rail = Files.readString(Path.of("src/main/resources/templates/session/_encounter-rail.html"));
        String story = Files.readString(Path.of("src/main/resources/templates/session/_story-rail.html"));
        String plan = Files.readString(Path.of("src/main/resources/templates/session/_session-plan.html"));
        String lifecycle = Files.readString(Path.of(
                "src/main/resources/templates/session/_lifecycle-dialog.html"));
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        String battleMap = Files.readString(Path.of(
                "src/main/resources/static/js/map/battle-map.js"));
        assertThat(count(mapModule, "id=\"battleCanvasWrap\"")).isEqualTo(1);
        assertThat(count(rail, "encounter/_tracker :: tracker")).isEqualTo(1);
        String encounterModule = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_encounter.html"));
        assertThat(count(encounterModule, "session/_encounter-rail :: encounters")).isEqualTo(1);
        assertThat(js).contains("new BattleMap(");
        assertThat(js).doesNotContain("nextTurn(id)", "applyDamage(combatant", "projectTokens(");
        assertThat(shell).contains("aria-label=${module.title}", "aria-live=\"polite\"");
        assertThat(mapModule).contains("aria-live=\"polite\"");
        assertThat(html).contains("@keydown.window=\"handleKeyboard($event)\"");
        assertThat(html.indexOf("session/_lifecycle-dialog :: lifecycle-dialog"))
                .isLessThan(html.indexOf("</main>"));
        assertThat(story).contains("scene-actions");
        assertThat(Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_story.html")))
                .contains("scenePicker");
        assertThat(html).contains(">Rules<", ">Calendar<");
        assertThat(mapModule).contains("aria-label=\"Battle map controls\"", "aria-label=\"Workspace map\"");
        assertThat(mapModule).contains("@click=\"openTokenDialog()\"", "@click=\"addParty()\"",
                "tokens.filter(tk => tk.source === 'COMBATANT')",
                "tokens.filter(tk => tk.source === 'MARKER')");
        assertThat(html).contains("cockpitLayoutConfig",
                "cockpitPresetPicker", "cockpitLayoutModeButton",
                "session/_cockpit-workbench");
        assertThat(Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-workbench.html")))
                .contains("data-cockpit-workbench");
        assertThat(js).contains("sessionStatus", "handleKeyboard");
        assertThat(js).contains("startSession", "pauseSession", "resumeSession",
                "cancelReview", "beginReview", "completeSession");
        assertThat(js).contains("async switchMap(");
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
                "@click.self=\"closeLifecycle()\"");
        assertThat(js).contains("trapLifecycleFocus(event)", "closeLifecycle()",
                "openLifecycle()", "lifecycleFocusable(container)");
        assertThat(js).contains("showModal()", ".close()");
        assertThat(js).contains("const previousMapId", "if (!switched) {",
                "await bm.switchToMap(previousMapId)");
        assertThat(battleMap).contains("const documentResponse", "const tokenResponse",
                "return true;", "return false;");
    }

    @Test
    void lifecycleDialogIsNativeModal() throws IOException {
        String lifecycle = Files.readString(Path.of(
                "src/main/resources/templates/session/_lifecycle-dialog.html"));
        assertThat(lifecycle).contains("<dialog");
        assertThat(lifecycle).contains("@cancel.prevent=\"closeLifecycle()\"");
        assertThat(lifecycle).contains("@click.self=\"closeLifecycle()\"");
        assertThat(lifecycle).doesNotContain("x-show=\"lifecycleOpen\"");
        assertThat(lifecycle).doesNotContain(":hidden");
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
        assertThat(story).contains("section.id");
    }

    @Test
    void encounterRailUsesTrackerActiveThreatCard() throws IOException {
        String rail = Files.readString(Path.of("src/main/resources/templates/session/_encounter-rail.html"));
        String tracker = Files.readString(Path.of("src/main/resources/templates/encounter/_tracker.html"));
        // The tracker's Alpine component logic lives in combat-tracker.js (loaded globally
        // from cockpit.html) rather than inline, so lazily-inserted tracker markup can find it.
        String trackerJs = Files.readString(Path.of("src/main/resources/static/js/combat-tracker.js"));
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(rail).contains("encounter/_tracker :: tracker");
        assertThat(tracker).contains("activeThreatCard");
        assertThat(tracker).contains("data-active-threat-card");
        assertThat(trackerJs).contains("threatCard");
        assertThat(js).contains("threatCard");
        assertThat(js).contains("activeCombatants");
    }

    @Test
    void cockpitLoadsSharedDiceRollerScript() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        assertThat(html).contains("/js/dice-roller.js");
    }

    @Test
    void setCurrentSceneDoesNotReloadPage() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(extractFunction(js, "setCurrentScene"))
                .doesNotContain("window.location.reload()")
                .as("setCurrentScene should update rails in place, not reload");
    }

    @Test
    void stepSceneDoesNotReloadPage() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(extractFunction(js, "stepScene"))
                .doesNotContain("window.location.reload()")
                .as("stepScene should update rails in place, not reload");
    }

    @Test
    void followTransitionDoesNotReloadPage() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(extractFunction(js, "followTransition"))
                .doesNotContain("window.location.reload()")
                .as("followTransition should update rails in place, not reload");
    }

    @Test
    void activateEncounterDoesNotReloadPage() throws IOException {
        String rail = Files.readString(Path.of(
                "src/main/resources/templates/session/_encounter-rail.html"));
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));

        assertThat(rail)
                .contains("runEncounter($el.dataset.encounterId)")
                .doesNotContain("data-map-id=${enc.mapId}");
        assertThat(extractFunction(js, "activateEncounter"))
                .doesNotContain("window.location.reload()")
                .as("activateEncounter should update rails in place, not reload");
        assertThat(extractFunction(js, "runEncounter"))
                .contains("activateEncounter")
                .doesNotContain("window.location.reload()")
                .as("runEncounter should update rails in place, not reload");
    }

    @Test
    void storyCanSeedAnEncounterAndRefreshBothModules() throws IOException {
        String story = Files.readString(
                Path.of("src/main/resources/templates/session/_story-rail.html"));
        String script = Files.readString(
                Path.of("src/main/resources/static/js/session-cockpit.js"));

        assertThat(story)
                .contains("Start encounter from this scene")
                .contains("seedCurrentScene")
                .contains(":disabled=\"seedingSceneEncounter\"");
        assertThat(script)
                .contains("seedingSceneEncounter: false")
                .contains("async seedCurrentScene(sceneId)")
                .contains("if (this.seedingSceneEncounter) return")
                .contains("/session/scenes/${sceneId}/seed-encounter")
                .contains("refreshModules(['story', 'encounter']")
                .contains("this.seedingSceneEncounter = false")
                .contains("was created, but the cockpit modules could not refresh")
                .contains("Could not create the scene encounter");
    }

    @Test
    void cockpitHasRefreshModulesHelper() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(js).contains("refreshModules(")
                .as("cockpit JS should call refreshModules after scene mutations");
        assertThat(js)
                .contains("dispatchEvent(new CustomEvent('cockpit:module-refresh'")
                .doesNotContain("storyEl.innerHTML")
                .doesNotContain("encEl.innerHTML");
    }

    @Test
    void cockpitKeepsStableRailContainers() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/session/cockpit.html"));
        String workbench = Files.readString(
                Path.of("src/main/resources/templates/session/_cockpit-workbench.html"));
        String shell = Files.readString(
                Path.of("src/main/resources/templates/session/_cockpit-module-shell.html"));
        String layoutCss = Files.readString(
                Path.of("src/main/resources/static/css/cockpit-layout.css"));
        String mapModule = Files.readString(
                Path.of("src/main/resources/templates/session/_map-module.html"));
        assertThat(html)
                .contains("session/_cockpit-workbench")
                .contains("/css/cockpit-layout.css");
        assertThat(workbench)
                .contains("data-cockpit-workbench")
                .contains("data-cockpit-zone")
                .contains("cockpitModuleByKey['encounter']");
        assertThat(shell)
                .contains("class=\"cockpit-module\"", "data-module-body")
                .contains("data-module-content")
                .contains("data-module-loaded")
                .contains("data-module-endpoint");
        String encounterModule = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_encounter.html"));
        assertThat(encounterModule).contains("session/_encounter-rail");
        assertThat(Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_story.html")))
                .contains("session/_story-rail");
        assertThat(mapModule).contains("th:fragment=\"map-module-runtime\"", "battleCanvasWrap");
        assertThat(layoutCss).contains(".cockpit-workbench {", "overflow: hidden;");
    }

    @Test
    void transitionalModuleShellRemoved() throws IOException {
        String shell = Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-module-shell.html"));
        assertThat(shell).doesNotContain("th:switch");
        assertThat(shell).doesNotContain("workspace");
        assertThat(shell).doesNotContain("initiallyRendered");
        assertThat(shell).contains("shell(module, initialBody)");
        assertThat(shell).contains("th:insert=\"${initialBody}\"");
    }

    @Test
    void deferredModulesFileDeleted() {
        assertThat(Path.of("src/main/resources/templates/session/_cockpit-deferred-modules.html"))
                .doesNotExist();
    }

    @Test
    void workbenchNoLongerReferencesDeferredModules() throws IOException {
        String workbench = Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-workbench.html"));
        assertThat(workbench).doesNotContain("_cockpit-deferred-modules");
        assertThat(workbench).contains("module=${cockpitModuleByKey");
        assertThat(workbench).contains("initialBody=");
    }

    @Test
    void sessionControllerHasInitialModuleViews() throws IOException {
        String java = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java"));
        assertThat(java).contains("initialStoryView");
        assertThat(java).contains("initialSessionPlanView");
        assertThat(java).contains("initialPartyView");
        assertThat(java).contains("initialQuickNotesView");
    }

    @Test
    void railFragmentEndpointsExist() throws IOException {
        String java = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/session/web/SessionController.java"));
        assertThat(java).contains("/session/rails/story",
                "/session/rails/encounter")
                .as("SessionController should have story/encounter rail fragment endpoints");
    }

    private static String extractFunction(String js, String name) {
        int start = js.indexOf("async " + name + "(");
        if (start < 0) start = js.indexOf(name + ": async function(");
        if (start < 0) start = js.indexOf(name + "(");
        if (start < 0) return "";
        int brace = js.indexOf('{', start);
        if (brace < 0) return "";
        int depth = 1;
        int end = brace + 1;
        while (depth > 0 && end < js.length()) {
            char c = js.charAt(end);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            end++;
        }
        return js.substring(brace, end);
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
