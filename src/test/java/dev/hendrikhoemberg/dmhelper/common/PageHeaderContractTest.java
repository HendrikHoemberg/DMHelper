package dev.hendrikhoemberg.dmhelper.common;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PageHeaderContractTest {

    private static final List<String> CAMPAIGN_LIST_PAGES = List.of(
            "src/main/resources/templates/encounter/list.html",
            "src/main/resources/templates/maps/list.html",
            "src/main/resources/templates/notes/list.html",
            "src/main/resources/templates/audio/list.html",
            "src/main/resources/templates/party/list.html",
            "src/main/resources/templates/sheet/overview.html",
            "src/main/resources/templates/treasury/list.html",
            "src/main/resources/templates/world/npcs-list.html",
            "src/main/resources/templates/world/locations-list.html",
            "src/main/resources/templates/world/factions-list.html",
            "src/main/resources/templates/quest/list.html");

    @Test
    void everyCampaignPageUsesCampaignNameEyebrowAndGoldRule() throws IOException {
        for (String page : CAMPAIGN_LIST_PAGES) {
            String html = Files.readString(Path.of(page));
            assertThat(html).as("%s eyebrow", page)
                    .contains("page-header-eyebrow\" th:text=\"${campaign.name}\"");
            assertThat(html).as("%s gold rule", page)
                    .contains("rule-taper rule-taper--gold");
        }
    }

    @Test
    void noAsciiDoubleHyphenPseudoDashes() throws IOException {
        for (String page : CAMPAIGN_LIST_PAGES) {
            assertThat(Files.readString(Path.of(page)))
                    .as("%s must use — not --", page)
                    .doesNotContain(" -- ");
        }
    }
}
