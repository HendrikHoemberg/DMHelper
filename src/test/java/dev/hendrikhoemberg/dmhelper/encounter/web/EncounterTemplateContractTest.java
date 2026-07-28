package dev.hendrikhoemberg.dmhelper.encounter.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class EncounterTemplateContractTest {

    @MockitoBean
    private CampaignRepository campaignRepository;

    @Test
    void setupIncludesLibraryAddWavePrepRewardsSummary() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/setup.html"));
        assertThat(html).contains("encounter/_library-add :: library-add");
        assertThat(html).contains("encounter/_threat-add :: threat-add");
        assertThat(html).contains("encounter/_waves :: waves");
        assertThat(html).contains("encounter/_prep :: prep");
        assertThat(html).contains("encounter/_rewards :: rewards");
        assertThat(html).contains("encounter/_summary-modal :: summary-modal");
        assertThat(html).contains("encounter/_placement-board :: placement-board");
    }

    @Test
    void placementBoardRendersCorrectAttributes() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_placement-board.html"));
        assertThat(html).contains("data-encounter-placement-board");
        assertThat(html).contains("data-unplaced-combatants");
        assertThat(html).contains("data-readiness-summary");
        assertThat(html).contains("data-place-party");
        assertThat(html).contains("data-auto-place");
        assertThat(html).contains("placement-canvas");
    }

    @Test
    void setupDoesNotContainPrefillButtons() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/setup.html"));
        assertThat(html).doesNotContain("Prefill from Map");
        assertThat(html).doesNotContain("Prefill from Party");
        assertThat(html).doesNotContain("Open battle map");
    }

    @Test
    void threatAddSearchesVisibleDefinitionsAndPostsFromThreat() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_threat-add.html"));
        assertThat(html).contains("threatAdd");
        assertThat(html).contains("@input.debounce.300ms");
        assertThat(html).contains("/api/v1/traps/search");
        assertThat(html).contains("/api/v1/hazards/search");
        assertThat(html).contains("/api/v1/encounters/${this.encounterId}/combatants/from-threat");
        assertThat(html).contains("window.dmRequest");
        assertThat(html).contains("threatKind");
        assertThat(html).contains("threatId");
    }

    @Test
    void trackerRendersActiveThreatCardOnly() throws IOException {
        // Tracker behavior is split across the template (markup) and combat-tracker.js (logic).
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_tracker.html"))
                + Files.readString(Path.of("src/main/resources/static/js/combat-tracker.js"));
        assertThat(html).contains("activeThreatCard");
        assertThat(html).contains("data-active-threat-card");
        assertThat(html).contains("x-show=\"activeThreatCard && !tableSafe\"");
        assertThat(html).contains("dice-roller-prefill");
        assertThat(html).contains("Prefills only");
        assertThat(html).doesNotContain("/api/v1/combatants/${activeCombatantId}/damage");
    }

    @Test
    void libraryAddHasSearchAndAddFromLibrary() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_library-add.html"));
        assertThat(html).contains("libraryAdd");
        assertThat(html).contains("@input.debounce.300ms");
        assertThat(html).contains("addFromLibrary");
        assertThat(html).contains("/api/v1/library/statblocks/search");
        assertThat(html).contains("/api/v1/encounters/${this.encounterId}/combatants/from-library");
        assertThat(html).contains("window.dmRequest");
        assertThat(html).contains("window.showToast");
        assertThat(html).contains("window.reportActionFailure");
    }

    @Test
    void wavesListAndFormRendered() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_waves.html"));
        assertThat(html).contains("hx-delete");
        assertThat(html).contains("hx-post");
        assertThat(html).contains("wave.status", "wave.triggerKind");
        assertThat(html).contains("triggerKind");
        assertThat(html).contains("waveKey");
    }

    @Test
    void prepFormContainsAllFields() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_prep.html"));
        assertThat(html).contains("name=\"tactics\"", "name=\"morale\"", "name=\"surrender\"",
                "name=\"environment\"", "name=\"sourceLocator\"", "name=\"scalingNotes\"",
                "name=\"sceneKey\"");
        assertThat(html).contains("hx-put");
    }

    @Test
    void rewardsFormContainsFields() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_rewards.html"));
        assertThat(html).contains("rewards-xp-total", "rewards-xp-per-pc", "rewards-notes");
        assertThat(html).contains("encounterRewardsForm");
        assertThat(html).contains("/api/v1/encounters/${this.encounterId}/rewards");
        assertThat(html).contains("window.dmRequest");
    }

    @Test
    void summaryModalWiresApplyRewardsEndpoint() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_summary-modal.html"));
        assertThat(html).contains("/rewards/apply");
        assertThat(html).contains("createLedger");
        assertThat(html).contains("applyItems");
        assertThat(html).contains("applyQuestObjectives");
    }

    @Test
    void summaryModalDispatchesCockpitEventInsteadOfReload() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_summary-modal.html"));
        assertThat(html).doesNotContain("window.location.reload()")
                .as("summary-modal should dispatch events instead of reloading the page");
    }

    @Test
    void trackerEndEncounterDispatchesEncounterEndEvent() throws IOException {
        // endEncounter's event dispatch lives in combat-tracker.js after the script extraction.
        String html = Files.readString(Path.of("src/main/resources/static/js/combat-tracker.js"));
        assertThat(html).contains("dispatchEvent(new CustomEvent('cockpit-encounter-ended'")
                .as("tracker endEncounter should dispatch a cockpit-encounter-ended event");
    }

    @Test
    void summaryModalHasEndEncounterAlpine() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_summary-modal.html"));
        assertThat(html).contains("endEncounterModal");
        assertThat(html).contains("showModal");
        assertThat(html).contains("confirmRewards");
        assertThat(html).contains("summary.rounds");
        assertThat(html).contains("summary.defeatedCount");
        assertThat(html).contains("summary.totalDamageDealt");
        assertThat(html).contains("summary.wavesSpawned");
    }

    @Test
    void controllerAddsWavePrepRewardsModelAttrs() throws IOException {
        String java = Files.readString(Path.of(
                "src/main/java/dev/hendrikhoemberg/dmhelper/encounter/web/EncounterController.java"));
        assertThat(java).contains("model.addAttribute(\"waves\"");
        assertThat(java).contains("model.addAttribute(\"prep\"");
        assertThat(java).contains("model.addAttribute(\"rewards\"");
    }

    @Test
    void trackerExposesAccessibleInitiativeSetupActions() throws IOException {
        // Setup markup lives in the template; its endpoints/handlers in combat-tracker.js.
        String html = Files.readString(
                Path.of("src/main/resources/templates/encounter/_tracker.html"))
                + Files.readString(Path.of("src/main/resources/static/js/combat-tracker.js"));

        assertThat(html).contains(
                "data-initiative-setup",
                "encounter?.combatPhase === 'SETUP'",
                "Roll unset NPCs",
                "Start combat",
                "acceptUnset",
                "/start-combat",
                "/auto-roll",
                "c.initiative ?? '\u2014'",
                "type=\"number\"",
                "aria-label");
        assertThat(html).doesNotContain("c.initiative || '\u2014'");
    }

    @Test
    void runningTurnControlsAreHiddenDuringSetup() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/encounter/_tracker.html"));

        assertThat(html).contains(
                "data-running-turn-controls",
                "x-show=\"encounter?.combatPhase === 'RUNNING'\"");
    }

    @Test
    void trackerPrefillButtonHasOneMergedClassAttribute() throws IOException {
        String html = Files.readString(
                Path.of("src/main/resources/templates/encounter/_tracker.html"));
        Pattern duplicateClassAttributes = Pattern.compile(
                "(?s)<[^>]*(?<![:\\w-])class=\"[^\"]*\"[^>]*(?<![:\\w-])class=\"[^\"]*\"[^>]*>");

        assertThat(duplicateClassAttributes.matcher(html).find())
                .as("tracker markup must not contain duplicate class attributes")
                .isFalse();
        assertThat(html).contains("class=\"btn btn-sm btn-ghost tracker-statblock-badge\"");
    }
}
