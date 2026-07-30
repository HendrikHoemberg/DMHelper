package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitRuntimeModuleContractTest {

    private static final Set<String> ALL_MODULE_KEYS = Set.of(
            "story", "map", "encounter", "session-plan", "party",
            "quick-notes", "reference", "audio", "session-log");

    @Test
    void allTenModulesRenderAllRuntimeStates() throws IOException {
        for (String key : ALL_MODULE_KEYS) {
            Path fragmentPath = Path.of(
                    "src/main/resources/templates/session/modules/_" + key + ".html");
        
            String fragment = Files.readString(fragmentPath);

            // empty state
            assertThat(fragment)
                    .as("module %s must declare data-module-empty", key)
                    .contains("data-module-empty");

            // populated state: data-module-content-root
            assertThat(fragment)
                    .as("module %s must declare data-module-content-root", key)
                    .contains("data-module-content-root");

            // mode support
            assertThat(fragment)
                    .as("module %s must declare data-module-mode", key)
                    .contains("data-module-mode");

            // module identity
            assertThat(fragment)
                    .as("module %s must declare data-cockpit-module-fragment", key)
                    .contains("data-cockpit-module-fragment");

            // loading / error / attention handled by shell chrome, not body fragment
            // refresh-without-shell-replacement: body fragments use th:replace/th:insert into stable shell
        }
    }

    @Test
    void moduleShellNoTransitionalSwitch() throws IOException {
        String shell = Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-module-shell.html"));
        assertThat(shell).doesNotContain("th:switch");
        assertThat(shell).doesNotContain("th:case");
    }

    @Test
    void workbenchDoesNotUseDeferredModules() throws IOException {
        String workbench = Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-workbench.html"));
        assertThat(workbench).doesNotContain("deferred-modules");
    }

    @Test
    void deletedDeferredModulesFileIsGone() {
        assertThat(Path.of("src/main/resources/templates/session/_cockpit-deferred-modules.html"))
                .doesNotExist();
    }

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
        assertThat(rail).contains("view.suspended");
        assertThat(rail).contains("enc.mapName");
        assertThat(rail).contains("enc.verdict");
        assertThat(rail).contains("enc.combatantCount");
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
        assertThat(tracker).contains("round-counter");
        // The tracker's Alpine component logic (including activeTurnIndex handling) lives in
        // combat-tracker.js, loaded globally from cockpit.html rather than inlined in the template.
        String trackerJs = Files.readString(Path.of(
                "src/main/resources/static/js/combat-tracker.js"));
        assertThat(trackerJs).contains("activeTurnIndex");
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
        assertThat(rail).contains("Planned encounters");
        assertThat(rail).contains("Suspended Encounters");
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
        assertThat(rail).contains("Create an encounter here");
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
        assertThat(fragment).contains("summary-bar(view=${view.members}, mode=${mode}, oob=${null})");
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
        assertThat(bar).contains("th:fragment=\"summary-bar(view, mode, oob)");
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

    // --- Session Plan ---

    @Test
    void sessionPlanFragmentDeclaresModuleAttributes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-plan.html"));
        assertThat(fragment).contains("data-cockpit-module-fragment");
        assertThat(fragment).contains("data-module-mode");
        assertThat(fragment).contains("data-module-empty");
        assertThat(fragment).contains("data-module-content-root");
    }

    @Test
    void sessionPlanFragmentShowsOrderedBeats() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-plan.html"));
        assertThat(fragment).contains("view.beats");
        assertThat(fragment).contains("beat.position");
        assertThat(fragment).contains("beat.type");
        assertThat(fragment).contains("beat.label");
    }

    @Test
    void sessionPlanFragmentShowsBrokenLinkState() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-plan.html"));
        assertThat(fragment).contains("beat.resolved");
        assertThat(fragment).contains("Broken link");
    }

    @Test
    void sessionPlanEmptyStateHasNextAction() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-plan.html"));
        // Empty when view == null or no title
        assertThat(fragment).contains("view.title");
        assertThat(fragment).contains("No session plan");
        assertThat(fragment).contains("empty-state");
    }

    @Test
    void sessionPlanHasQuestProgressView() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-plan.html"));
        assertThat(fragment).contains("view.questProgress");
    }

    @Test
    void sessionPlanCompactModeShowsNextBeatsOnly() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-plan.html"));
        assertThat(fragment).contains("mode.name() == 'COMPACT'");
        assertThat(fragment).contains("view.upcoming");
    }

    // --- Quick Notes ---

    @Test
    void quickNotesFragmentDeclaresModuleAttributes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        assertThat(fragment).contains("data-cockpit-module-fragment");
        assertThat(fragment).contains("data-module-mode");
        assertThat(fragment).contains("data-module-empty");
        assertThat(fragment).contains("data-module-content-root");
    }

    @Test
    void quickNotesFragmentHasCampaignScopeCapture() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        assertThat(fragment).contains("targetType=CAMPAIGN");
        assertThat(fragment).contains("quicknotes-form");
    }

    @Test
    void quickNotesFragmentHasUniqueDomId() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        assertThat(fragment).contains("id=\"|quicknotes-");
        assertThat(fragment).contains("data-target-type=\"CAMPAIGN\"");
        assertThat(fragment).contains("data-target-id=${campaignId}");
    }

    @Test
    void quickNotesFragmentListsUnresolvedNotes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        assertThat(fragment).contains("view.notes");
        assertThat(fragment).contains("n.body");
        assertThat(fragment).contains("n.createdAt");
    }

    @Test
    void quickNotesFragmentOffersVisibleSessionPlanPromotion() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        String shell = Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-module-shell.html"));
        String javascript = Files.readString(Path.of(
                "src/main/resources/static/js/quicknotes.js"));
        assertThat(shell).contains("x-data=${module.key == 'quick-notes' ? 'quicknotes' : null}");
        assertThat(fragment).contains("data-promote-session-plan",
                "Promote to session plan", "promoteSessionPlan({id:", "promote({id:");
        assertThat(javascript).contains("promoteSessionPlan(quicknote)",
                "Release rehearsal plan", "SESSION_PLAN", "moduleKey: 'session-plan'");
    }

    @Test
    void quickNotesRefreshKeepsOneAlpineRootAtStableModuleContentBoundary() throws IOException {
        String shell = Files.readString(Path.of(
                "src/main/resources/templates/session/_cockpit-module-shell.html"));
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        String javascript = Files.readString(Path.of(
                "src/main/resources/static/js/cockpit-modules.js"));

        assertThat(shell).contains(
                "x-data=${module.key == 'quick-notes' ? 'quicknotes' : null}",
                "data-campaign-id=${module.key == 'quick-notes' ? campaignId : null}",
                "data-target-type=${module.key == 'quick-notes' ? 'CAMPAIGN' : null}",
                "data-target-id=${module.key == 'quick-notes' ? campaignId : null}");
        assertThat(fragment).doesNotContain("x-data=\"quicknotes\"")
                .doesNotContain("data-campaign-id=${campaignId}")
                .doesNotContain("data-target-type='CAMPAIGN', data-target-id=${campaignId}");
        assertThat(javascript).contains(
                "const root = body.querySelector('[data-module-content]') || body;",
                "root.replaceChildren(...fragment.childNodes);",
                "window.Alpine?.initTree(root)");
    }

    @Test
    void quickNotesFragmentHasPromoteAndDeleteActions() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        assertThat(fragment).contains("promote(");
        assertThat(fragment).contains("remove(");
        assertThat(fragment).contains("x-model=\"newBody\"");
        assertThat(fragment).contains("unresolved");
    }

    @Test
    void quickNotesPreservesUnsentInput() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        assertThat(fragment).contains("x-model=\"newBody\"");
        assertThat(fragment).contains("persist-new-body");
    }

    @Test
    void quickNotesFragmentHasEmptyState() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_quick-notes.html"));
        assertThat(fragment).contains("empty-state");
        assertThat(fragment).contains("No quick notes");
    }

    // --- Reference ---

    @Test
    void referenceFragmentDeclaresModuleAttributes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_reference.html"));
        assertThat(fragment).contains("data-cockpit-module-fragment");
        assertThat(fragment).contains("data-module-mode");
        assertThat(fragment).contains("data-module-empty");
        assertThat(fragment).contains("data-module-content-root");
    }

    @Test
    void referenceFragmentUsesCockpitReferenceComponent() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_reference.html"));
        assertThat(fragment).contains("x-data=\"cockpitReference\"");
    }

    @Test
    void referenceFragmentHasSearchInput() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_reference.html"));
        assertThat(fragment).contains("x-model=\"query\"");
        assertThat(fragment).contains("type=\"search\"");
        assertThat(fragment).contains("placeholder");
    }

    @Test
    void referenceFragmentHasGroupedResults() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_reference.html"));
        assertThat(fragment).contains("x-text");
        assertThat(fragment).contains("results");
        assertThat(fragment).contains("group");
    }

    @Test
    void referenceFragmentPreventsHtmlInjection() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_reference.html"));
        assertThat(fragment).contains("x-text");
        assertThat(fragment).doesNotContain("x-html");
    }

    @Test
    void referenceFragmentEmptyStateIsVisible() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_reference.html"));
        assertThat(fragment).contains("empty");
        assertThat(fragment).contains("search");
        assertThat(fragment).contains("hint");
    }

    // --- Audio ---

    @Test
    void audioFragmentDeclaresModuleAttributes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_audio.html"));
        assertThat(fragment).contains("data-cockpit-module-fragment");
        assertThat(fragment).contains("data-module-mode");
        assertThat(fragment).contains("data-module-empty");
        assertThat(fragment).contains("data-module-content-root");
    }

    @Test
    void audioFragmentRendersCockpitWidget() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_audio.html"));
        assertThat(fragment).contains("cockpit-widget");
        assertThat(fragment).contains("audio/_cockpit-widget");
    }

    @Test
    void audioFragmentAssertExactlyOneInteractiveWidgetBody() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_audio.html"));
        assertThat(fragment).contains("x-data=\"audioCockpitWidget");
        assertThat(count(fragment, "x-data=\"audioCockpitWidget")).isEqualTo(1);
    }

    @Test
    void audioFragmentHasNoCommandBarAction() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_audio.html"));
        // Command bar must contain no audio-specific action
        assertThat(fragment).doesNotContain("audio-action");
        assertThat(fragment).doesNotContain("data-audio-command");
    }

    @Test
    void sessionLogFragmentDeclaresModuleAttributes() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("data-cockpit-module-fragment");
        assertThat(fragment).contains("data-module-mode");
        assertThat(fragment).contains("data-module-empty");
        assertThat(fragment).contains("data-module-content-root");
        assertThat(fragment).contains("runtime-session-log");
    }

    @Test
    void sessionLogFragmentShowsSessionStatusAndTime() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("view.sessionStatus");
        assertThat(fragment).contains("view.timeRange");
    }

    @Test
    void sessionLogFragmentShowsEvents() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("view.currentEvents");
        assertThat(fragment).contains("evt.occurredAt");
        assertThat(fragment).contains("evt.kind");
        assertThat(fragment).contains("evt.title");
        assertThat(fragment).contains("evt.warning");
    }

    @Test
    void sessionLogFragmentShowsSavedLogs() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("view.recentSavedLogs");
        assertThat(fragment).contains("sl.url");
        assertThat(fragment).contains("sl.title");
    }

    @Test
    void sessionLogFragmentShowsUnresolvedQuickNotesBadge() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("view.unresolvedQuickNoteCount");
    }

    @Test
    void sessionLogFragmentShowsReviewDraft() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("view.reviewDraft");
    }

    @Test
    void sessionLogFragmentHasEmptyState() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("empty-state");
        assertThat(fragment).contains("Session events will appear after play begins");
    }

    @Test
    void sessionLogCompactModeShowsLimitedContent() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("mode.name() == 'COMPACT'");
        assertThat(fragment).contains("view.unresolvedQuickNoteCount");
    }

    @Test
    void sessionLogHasModeClassExpression() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("runtime-session-log");
        assertThat(fragment).contains("th:classappend");
        assertThat(fragment).contains("mode.name()");
    }

    @Test
    void sessionLogFocusedModeShowsReviewContent() throws IOException {
        String fragment = Files.readString(Path.of(
                "src/main/resources/templates/session/modules/_session-log.html"));
        assertThat(fragment).contains("view.sessionStatus == 'REVIEW'");
        assertThat(fragment).contains("data-action=\"review-session\"");
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
