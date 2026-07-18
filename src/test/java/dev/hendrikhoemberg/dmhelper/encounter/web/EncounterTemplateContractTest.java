package dev.hendrikhoemberg.dmhelper.encounter.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EncounterTemplateContractTest {

    @Test
    void detailIncludesLibraryAddWavePrepRewardsSummary() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/detail.html"));
        assertThat(html).contains("encounter/_library-add :: library-add");
        assertThat(html).contains("encounter/_threat-add :: threat-add");
        assertThat(html).contains("encounter/_waves :: waves");
        assertThat(html).contains("encounter/_prep :: prep");
        assertThat(html).contains("encounter/_rewards :: rewards");
        assertThat(html).contains("encounter/_summary-modal :: summary-modal");
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
        String html = Files.readString(Path.of("src/main/resources/templates/encounter/_tracker.html"));
        assertThat(html).contains("activeThreatCard");
        assertThat(html).contains("data-active-threat-card");
        assertThat(html).contains("x-show=\"activeThreatCard && dmMode\"");
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
}
