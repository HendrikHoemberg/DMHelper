package dev.hendrikhoemberg.dmhelper.campaign.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 11.10, structural only. Whether campaign settings and About *read* well is a
 * review question; the ranking of an import preview and the wording of a destructive
 * confirmation are not — both change what a DM decides.
 */
class AdministrationContractTest {

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    private static Document parse(String template) throws Exception {
        return Jsoup.parse(read(template));
    }

    @Test
    void importPreviewRanksErrorsWarningsContentsAndReadiness() throws Exception {
        List<String> sections = parse("campaigns/_import-dialog.html")
                .select("[data-import-section]").stream()
                .map(section -> section.attr("data-import-section"))
                .toList();

        assertThat(sections)
                .as("an import preview must lead with what blocks the import")
                .containsExactly("errors", "warnings", "contents", "readiness");
    }

    @Test
    void destructiveCampaignActionsStateTheirConsequence() throws Exception {
        String settings = read("campaigns/settings.html");
        assertThat(settings).contains("data-settings-group=\"danger\"");
        assertThat(settings).contains("data-confirm-consequence");
    }

}
