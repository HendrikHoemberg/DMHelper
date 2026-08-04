package dev.hendrikhoemberg.dmhelper.web;

import dev.hendrikhoemberg.dmhelper.support.LazyInitLogCapture;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullPageRenderSmokeTest {

    @LocalServerPort private int port;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private PreparationSurfaceFixture prepFixture;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private PopulatedCampaignFixture.Seeded seeded;
    private PreparationSurfaceFixture.Seeded prepared;

    @BeforeAll
    void seed() {
        seeded = fixture.seed();
        prepared = prepFixture.seed();
    }

    List<String> pages() {
        String c = "/campaigns/" + seeded.campaignId();
        String p = "/campaigns/" + prepared.campaignId();
        return List.of(
                "/campaigns",
                c,
                c + "/adventures",
                c + "/adventures/" + seeded.adventureId(),
                c + "/adventures/" + seeded.adventureId() + "/scenes/" + seeded.richSceneId(),
                c + "/adventures/" + seeded.adventureId() + "/scenes/" + seeded.richSceneId() + "/structure",
                c + "/encounters",
                c + "/encounters/new",
                c + "/maps",
                c + "/maps/new",
                c + "/handouts",
                c + "/audio/cues",
                c + "/notes",
                c + "/party",
                c + "/sheets",
                c + "/party/" + seeded.memberId() + "/sheet",
                c + "/treasury",
                c + "/ledger",
                c + "/world/npcs",
                c + "/world/npcs/" + seeded.npcId(),
                c + "/world/locations",
                c + "/world/locations/" + seeded.locationId(),
                c + "/world/locations/" + seeded.childLocationId(),
                c + "/world/factions",
                c + "/world/factions/" + seeded.factionId(),
                c + "/quests",
                c + "/quests/" + seeded.questId(),
                c + "/calendar",
                c + "/session",
                c + "/settings",
                "/library",
                "/library/tables",
                "/library/tables/" + seeded.tableId(),
                "/library/traps",
                "/library/traps/" + seeded.trapId(),
                "/library/hazards",
                "/library/hazards/" + seeded.hazardId(),
                p + "/party",
                p + "/encounters/" + prepared.encounterId(),
                p + "/encounters/" + prepared.encounterId() + "/setup",
                // The map editor ships today and Part 4 Stage 6 restructures it into four
                // regions — exactly the shape that invites a slot to render its own source
                // markup — so the duplicate-id guard covers it before that work, not after.
                // /maps/{id}/play needs no entry: it 302s to /session, already swept above.
                p + "/maps/" + prepared.mapId() + "/edit"
        );
    }

    @ParameterizedTest
    @MethodSource("pages")
    void pageRendersCompletely(String path) throws Exception {
        HttpResponse<String> response = get(path);

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

    /**
     * A repeated id means a slot rendered twice. The archetype slots are wired as
     * {@code content=~{::#page-content}}, {@code rail=~{::#page-rail}}, so any markup a slot
     * references must live <em>outside</em> that slot — the way {@code #primaryAction} does.
     * Put it inside and it renders once where the fragment pulls it in and again as the
     * slot's own content: every Part 3 detail page shipped its contextual rail two and three
     * times over, through three stage gates and 2500 green tests, because nothing looked.
     */
    /**
     * Routes that deliberately answer with a fragment rather than a document, so the
     * end-with-{@code </html>} assertion does not apply to them. The presentation overlay is
     * Part 4 Task 47's surface: spec 11.11's requirements land on a fragment overlaid on the
     * DM page, not on a standalone player document.
     */
    List<String> fragmentRoutes() {
        String c = "/campaigns/" + seeded.campaignId();
        return List.of(
                c + "/handouts/" + seeded.playerHandoutId() + "/present",
                c + "/handouts/" + seeded.dmOnlyHandoutId() + "/present");
    }

    List<String> everyRenderedRoute() {
        return java.util.stream.Stream.concat(pages().stream(), fragmentRoutes().stream())
                .toList();
    }

    @ParameterizedTest
    @MethodSource("everyRenderedRoute")
    void pageEmitsEachElementIdOnce(String path) throws Exception {
        var counts = new java.util.LinkedHashMap<String, Integer>();
        for (var element : org.jsoup.Jsoup.parse(get(path).body()).select("[id]")) {
            counts.merge(element.id(), 1, Integer::sum);
        }
        assertThat(counts).isNotEmpty();
        assertThat(counts.entrySet().stream().filter(e -> e.getValue() > 1).toList())
                .as("duplicate element ids on %s — a slot rendered its own source markup", path)
                .isEmpty();
    }

    @Test
    void fullSweepLogsNoLazyInitializationException() throws Exception {
        try (LazyInitLogCapture capture = new LazyInitLogCapture()) {
            for (String path : pages()) {
                get(path);
            }
            assertThat(capture.lazyInitFailures())
                    .as("no LazyInitializationException may be logged during a full page sweep")
                    .isEmpty();
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
