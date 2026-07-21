package dev.hendrikhoemberg.dmhelper.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullPageRenderSmokeTest {

    @LocalServerPort private int port;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private AdventureService adventureService;

    private final HttpClient http = HttpClient.newHttpClient();
    private UUID campaignId;
    private UUID adventureId;
    private UUID sceneId;

    @BeforeAll
    void seed() {
        Campaign campaign = new Campaign();
        campaign.setName("Smoke Campaign");
        campaign.setDescription("Render-smoke fixture");
        campaignId = campaignRepository.save(campaign).getId();

        Adventure adventure = adventureService.createAdventure(
                campaignId, "Smoke Adventure", "desc", null);
        adventureId = adventure.getId();
        Chapter chapter = adventureService.createChapter(adventureId, "Chapter One", "intro");
        Scene scene = adventureService.createScene(chapter.getId(), "Opening Scene", "S1", "The hallway is dark.");
        sceneId = scene.getId();
    }

    List<String> pages() {
        String c = "/campaigns/" + campaignId;
        return List.of(
                "/campaigns",
                c,
                c + "/adventures",
                c + "/adventures/" + adventureId,
                c + "/adventures/" + adventureId + "/scenes/" + sceneId,
                c + "/encounters",
                c + "/maps",
                c + "/handouts",
                c + "/audio/cues",
                c + "/notes",
                c + "/party",
                c + "/sheets",
                c + "/treasury",
                c + "/ledger",
                c + "/world/npcs",
                c + "/world/locations",
                c + "/world/factions",
                c + "/quests",
                c + "/calendar",
                c + "/session",
                "/library",
                "/library/tables",
                "/library/traps",
                "/library/hazards"
        );
    }

    @ParameterizedTest
    @MethodSource("pages")
    void pageRendersCompletely(String path) throws Exception {
        String url = "http://localhost:" + port + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/html")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("status for %s", path)
                .isEqualTo(200);
        assertThat(response.body())
                .as("body for %s must be a complete document", path)
                .isNotNull();
        assertThat(response.body().strip())
                .as("body for %s must end with </html> (truncation = lazy-init mid-render)", path)
                .endsWith("</html>");
    }
}
