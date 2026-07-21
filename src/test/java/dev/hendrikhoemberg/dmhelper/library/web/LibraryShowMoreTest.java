package dev.hendrikhoemberg.dmhelper.library.web;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class LibraryShowMoreTest {

    @MockitoBean
    private CampaignRepository campaignRepository;

    @Test
    void cardListCapComesFromModelAndOffersShowMore() throws IOException {
        String tpl = Files.readString(Path.of("src/main/resources/templates/library/_card.html"));
        assertThat(tpl).doesNotContain("th:with=\"cap=60\"");
        assertThat(tpl).contains("id=\"statblock-results\"");
        assertThat(tpl).contains("Show ");
        assertThat(tpl).contains("hx-get");
    }

    @Test
    void titleWrapsInsteadOfOverlappingCrBadge() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/static/css/components.css"));
        assertThat(css).contains("library-card__title");
        assertThat(css).contains("overflow-wrap: anywhere");
    }
}
