package dev.hendrikhoemberg.dmhelper.campaign.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NarrativeSurfaceContractTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    private static Document parse(String template) throws Exception {
        return Jsoup.parse(read(template));
    }

    /** Spec 11.2 fixes this order */
    @Test
    void campaignHomeSectionsAppearInTheApprovedOrder() throws Exception {
        List<String> sections = parse("campaigns/detail.html").select("[data-home-section]")
                .stream()
                .map(section -> section.attr("data-home-section"))
                .toList();
        assertThat(sections)
                .as("spec 11.2 fixes the reading order of the campaign home")
                .containsExactly("current", "start", "readiness", "party",
                        "preparation", "plan", "admin");
    }

    /** Spec 11.2: the repeated Run Session controls are the specific defect being fixed */
    @Test
    void campaignHomeOffersExactlyOneStartOrResumeAction() throws Exception {
        assertThat(parse("campaigns/detail.html").select("[data-action=start-session]"))
                .as("the duplicated Run Session control is the defect spec 11.2 fixes")
                .hasSize(1);
    }

    /** Spec 11.7: graph mechanics are an implementation detail, never DM-facing copy */
    @Test
    void questObjectivesDoNotLeakGraphIdentifiers() throws Exception {
        assertThat(read("quest/_objective-list.html"))
                .doesNotContain("edgeType")
                .doesNotContain("nodeId");
    }

    /** Player-safe state is a safety semantic; it must not drift per feature */
    @Test
    void playerSafeNotesUseTheStableShieldTone() throws Exception {
        assertThat(read("notes/_card.html")).contains("tone='shield'");
    }
}
