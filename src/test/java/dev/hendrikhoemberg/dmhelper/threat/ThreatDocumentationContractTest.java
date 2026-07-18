package dev.hendrikhoemberg.dmhelper.threat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documentation contracts for traps/hazards. Prose must stay aligned with
 * ownership, scene/tracker/pin workflows, dice prefill, package closure,
 * conversion non-invention, and player-safety non-goals.
 */
class ThreatDocumentationContractTest {

    @Test
    void dmManualChapterExistsAndCoversOwnershipAndWorkflows() throws Exception {
        String body = Files.readString(Path.of("docs/dm-manual/08-traps-and-hazards.md"));
        assertThat(body).containsIgnoringCase("trap");
        assertThat(body).containsIgnoringCase("hazard");
        for (String needle : List.of(
                "campaign-scoped",
                "user-global",
                "provenance",
                "/library/traps",
                "/library/hazards",
                "/api/v1/traps",
                "/api/v1/hazards",
                "scene",
                "tracker",
                "pin",
                "prefill",
                "player",
                "DM-only",
                "export",
                "import")) {
            assertThat(body).as("manual must mention %s", needle).containsIgnoringCase(needle);
        }
        assertThat(body).containsIgnoringCase("does not roll");
        assertThat(body).containsIgnoringCase("does not apply");
    }

    @Test
    void sessionCockpitDocumentsThreatSurfaces() throws Exception {
        String body = Files.readString(Path.of("docs/dm-manual/03-session-cockpit.md"));
        assertThat(body).containsIgnoringCase("trap");
        assertThat(body).containsIgnoringCase("hazard");
        assertThat(body).containsIgnoringCase("story");
        assertThat(body).containsIgnoringCase("prefill");
        assertThat(body).containsIgnoringCase("pin");
    }

    @Test
    void mapsEncountersDocumentsThreatTrackerAndPins() throws Exception {
        String body = Files.readString(Path.of("docs/dm-manual/04-maps-encounters-party.md"));
        assertThat(body).containsIgnoringCase("trap");
        assertThat(body).containsIgnoringCase("hazard");
        assertThat(body).containsIgnoringCase("tracker");
        assertThat(body).containsIgnoringCase("pin");
    }

    @Test
    void campaignFormatDocumentsTrapHazardSchemaAndClosure() throws Exception {
        String body = Files.readString(Path.of("docs/campaign-format-v2.md"));
        assertThat(body).contains("traps");
        assertThat(body).contains("hazards");
        assertThat(body).contains("threatPins");
        assertThat(body).contains("threatRef");
        assertThat(body).containsIgnoringCase("closure");
        assertThat(body).contains("TRAP");
        assertThat(body).contains("HAZARD");
        assertThat(body).contains("resetMode");
        assertThat(body).contains("exposureMode");
    }

    @Test
    void capabilitiesAndManifestDeclareThreatSupport() throws Exception {
        String caps = Files.readString(Path.of("docs/campaign-capabilities.md"));
        assertThat(caps).containsIgnoringCase("trap");
        assertThat(caps).containsIgnoringCase("hazard");
        assertThat(caps).contains("SUPPORTED");

        String manifest = Files.readString(Path.of("src/main/resources/agent/capability-manifest.json"));
        assertThat(manifest).contains("threats.traps_hazards");
        assertThat(manifest).contains("\"TRAP\"");
        assertThat(manifest).contains("\"HAZARD\"");
    }

    @Test
    void conversionDocsForbidInventingThreatMechanics() throws Exception {
        String playbook = Files.readString(Path.of("docs/agent/conversion-playbook.md"));
        assertThat(playbook).containsIgnoringCase("trap");
        assertThat(playbook).containsIgnoringCase("hazard");
        assertThat(playbook).containsIgnoringCase("never invent");
        assertThat(playbook).containsIgnoringCase("DC");

        String mapping = Files.readString(Path.of("docs/agent/mapping-rules.md"));
        assertThat(mapping).containsIgnoringCase("trap");
        assertThat(mapping).containsIgnoringCase("hazard");
        assertThat(mapping).containsIgnoringCase("attack");
        assertThat(mapping).containsIgnoringCase("save");
        assertThat(mapping).containsIgnoringCase("SOURCE_ANNOTATION");
    }

    @Test
    void validationErrorsDocumentThreatCodes() throws Exception {
        String body = Files.readString(Path.of("docs/authoring/validation-errors.md"));
        for (String code : List.of(
                "TRAP_EFFECT_MODE_CONFLICT",
                "TRAP_RESET_TIMING_REQUIRED",
                "HAZARD_EXPOSURE_REQUIRED",
                "THREAT_PIN_OUT_OF_BOUNDS",
                "THREAT_SEVERITY_REQUIRED",
                "THREAT_DC_OUT_OF_BOUNDS",
                "INVALID_THREAT_REFERENCE_KIND",
                "INVALID_DAMAGE_EXPRESSION",
                "DUPLICATE_DISARM_KEY")) {
            assertThat(body).as("validation-errors must list %s", code).contains(code);
        }
    }

    @Test
    void releaseNotesMentionTrapsAndHazards() throws Exception {
        String body = Files.readString(Path.of("docs/product/release-notes.md"));
        assertThat(body).containsIgnoringCase("trap");
        assertThat(body).containsIgnoringCase("hazard");
    }
}
