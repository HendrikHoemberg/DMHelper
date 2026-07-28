package dev.hendrikhoemberg.dmhelper.encounter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.support.LazyInitLogCapture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One encounter, start to finish, over the same HTTP surface the browser drives: map and party,
 * then the encounter, placement, readiness, initiative, damage, conditions, a defeat and the end.
 *
 * <p>This exists because the individual pieces were all covered while the walk-through was not,
 * and a mapped encounter's Setup screen shipped broken: nothing exercised an encounter that
 * actually had a map on it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EncounterRunThroughTest {

    private static final String MAP_NAME = "Triboar Trail Ambush";
    private static final List<String> HEROES =
            List.of("Sildar Vance", "Mira Coppervein", "Thorn Ashwood", "Kessa Dune");
    private static final int GOBLINS = 4;

    @LocalServerPort private int port;

    @Autowired private CampaignRepository campaigns;
    @Autowired private PartyMemberService party;
    @Autowired private GameMapService maps;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final JsonMapper json = JsonMapper.builder().build();

    private UUID campaignId;
    private UUID mapId;
    private UUID encounterId;
    private final List<UUID> goblinIds = new ArrayList<>();

    @BeforeAll
    void seedTheTable() {
        Campaign campaign = new Campaign();
        campaign.setName("Run-Through Campaign");
        campaign.setDescription("A campaign that exists to be run once, end to end");
        campaignId = campaigns.save(campaign).getId();

        party.create(campaignId, HEROES.get(0), "Player One", "Fighter 1", 16, 12, 2, 30, 12, 11, 10, null);
        party.create(campaignId, HEROES.get(1), "Player Two", "Rogue 1", 14, 9, 3, 30, 14, 13, 11, null);
        party.create(campaignId, HEROES.get(2), "Player Three", "Cleric 1", 15, 10, 1, 30, 11, 15, 12, null);
        party.create(campaignId, HEROES.get(3), "Player Four", "Wizard 1", 12, 8, 2, 30, 13, 12, 15, null);

        mapId = maps.create(campaignId, MAP_NAME, 24, 16, 48).getId();
    }

    @Test
    @Order(1)
    void thePartyRosterShowsEveryHero() throws Exception {
        HttpResponse<String> page = get("/campaigns/" + campaignId + "/party");

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains(HEROES);
    }

    @Test
    @Order(2)
    void theEncounterIsCreatedOnTheMap() throws Exception {
        JsonNode created = jsonBody(send("POST",
                "/api/v1/campaigns/" + campaignId + "/encounters",
                """
                {"name": "Goblin Ambush", "mapId": "%s"}""".formatted(mapId)), 201);

        encounterId = UUID.fromString(created.get("id").asString());
        assertThat(created.get("mapId").asString()).isEqualTo(mapId.toString());
        assertThat(created.get("status").asString()).isEqualTo("PLANNED");
    }

    @Test
    @Order(3)
    void theSetupScreenOpensAndNamesTheMap() throws Exception {
        HttpResponse<String> page = get("/campaigns/" + campaignId + "/encounters/" + encounterId + "/setup");

        assertThat(page.statusCode())
                .as("a mapped encounter's Setup screen must open")
                .isEqualTo(200);
        assertThat(page.body().strip())
                .as("a truncated document means the render died part-way through")
                .endsWith("</html>");
        assertThat(page.body()).contains(MAP_NAME);
        assertThat(page.body()).contains("data-encounter-placement-board");
    }

    @Test
    @Order(4)
    void assigningBattleMusicLeavesTheSetupScreenIntact() throws Exception {
        JsonNode cue = jsonBody(send("POST", "/api/v1/campaigns/" + campaignId + "/audio/cues",
                """
                {"cueKey": "goblin-ambush-combat", "name": "Ambush Drums",
                 "providerId": "youtube", "referenceKind": "VIDEO",
                 "providerReference": "dQw4w9WgXcQ",
                 "category": "COMBAT", "transitionPreference": "CROSSFADE"}"""), 200, 201);
        UUID cueId = UUID.fromString(cue.get("id").asString());

        send("PUT", "/api/v1/campaigns/" + campaignId + "/audio/assignments/encounters/"
                + encounterId + "?cueId=" + cueId + "&role=combat", null);

        // Both screens read the cue off the encounter entity, another lazy association that the
        // template only sees after the session has closed.
        for (String screen : List.of("/setup", "")) {
            HttpResponse<String> page =
                    get("/campaigns/" + campaignId + "/encounters/" + encounterId + screen);
            assertThat(page.statusCode()).as("status for encounter%s", screen).isEqualTo(200);
            assertThat(page.body().strip()).endsWith("</html>");
            assertThat(page.body())
                    .as("the assigned cue must be named on encounter%s", screen)
                    .contains("Ambush Drums");
        }
    }

    @Test
    @Order(5)
    void thePartyAndTheGoblinsJoinTheEncounter() throws Exception {
        send("POST", "/api/v1/encounters/" + encounterId + "/prefill/party", null);

        for (int i = 1; i <= GOBLINS; i++) {
            JsonNode goblin = jsonBody(send("POST",
                    "/api/v1/encounters/" + encounterId + "/combatants",
                    """
                    {"name": "Goblin %d", "maxHp": 7, "kind": "MONSTER"}""".formatted(i)), 200, 201);
            goblinIds.add(UUID.fromString(goblin.get("id").asString()));
        }

        JsonNode combatants = jsonBody(get("/api/v1/encounters/" + encounterId + "/combatants"), 200);
        assertThat(combatants).hasSize(HEROES.size() + GOBLINS);
    }

    @Test
    @Order(6)
    void everyCombatantCanBePlacedOnTheMap() throws Exception {
        // One placed by hand the way a drag onto the board does it, the rest auto-placed.
        UUID firstGoblin = goblinIds.get(0);
        JsonNode placement = jsonBody(send("PUT",
                "/api/v1/encounters/" + encounterId + "/combatants/" + firstGoblin + "/placement",
                """
                {"positionX": 9, "positionY": 4, "sizeCols": 1, "sizeRows": 1,
                 "color": "#8a3b2a", "icon": null}"""), 200);
        assertThat(placement.get("positionX").asInt()).isEqualTo(9);
        assertThat(placement.get("positionY").asInt()).isEqualTo(4);

        send("POST", "/api/v1/encounters/" + encounterId + "/placements/auto", null);

        JsonNode placements = jsonBody(get("/api/v1/encounters/" + encounterId + "/placements"), 200);
        assertThat(placements).hasSize(HEROES.size() + GOBLINS);
    }

    @Test
    @Order(7)
    void readinessReportsTheEncounterIsRunnable() throws Exception {
        JsonNode readiness = jsonBody(get("/api/v1/encounters/" + encounterId + "/readiness"), 200);

        assertThat(readiness.get("unplacedCombatantCount").asInt()).isZero();
        assertThat(readiness.get("placedCombatantCount").asInt()).isEqualTo(HEROES.size() + GOBLINS);
        assertThat(readiness.get("canRun").asBoolean())
                .as("a fully placed encounter on a map must be runnable, issues: %s", readiness.get("issues"))
                .isTrue();
    }

    @Test
    @Order(8)
    void initiativeIsEnteredAndCombatStarts() throws Exception {
        send("POST", "/api/v1/encounters/" + encounterId + "/activate", null);

        // The DM types the players' rolls; the NPCs get rolled for them.
        List<UUID> pcs = combatantIdsOfKind("PC");
        int roll = 20;
        for (UUID pc : pcs) {
            send("PUT", "/api/v1/combatants/" + pc + "/initiative",
                    """
                    {"initiative": %d}""".formatted(roll--));
        }
        send("POST", "/api/v1/encounters/" + encounterId + "/auto-roll", null);

        JsonNode encounter = jsonBody(send("POST",
                "/api/v1/encounters/" + encounterId + "/start-combat",
                """
                {"acceptUnset": false}"""), 200);

        assertThat(encounter.get("combatPhase").asString()).isEqualTo("RUNNING");
        assertThat(encounter.get("round").asInt()).isEqualTo(1);
        assertThat(encounter.get("activeTurnIndex").asInt()).isNotNegative();
    }

    @Test
    @Order(9)
    void damageAndConditionsLandOnACombatant() throws Exception {
        UUID goblin = goblinIds.get(0);

        // The tracker sends a signed delta on this endpoint: negative wounds, positive heals.
        JsonNode damaged = jsonBody(send("POST", "/api/v1/combatants/" + goblin + "/damage",
                """
                {"amount": -5}"""), 200);
        assertThat(damaged.get("currentHp").asInt()).isEqualTo(2);
        assertThat(damaged.get("bloodied").asBoolean()).isTrue();

        JsonNode conditioned = jsonBody(send("PUT", "/api/v1/combatants/" + goblin + "/conditions",
                """
                {"sourceKey": "prone", "durationRounds": 2}"""), 200);
        assertThat(conditioned.get("conditions")).anySatisfy(
                condition -> assertThat(condition.get("sourceKey").asString()).isEqualTo("prone"));
    }

    @Test
    @Order(10)
    void theTurnAdvancesAndCanBeTakenBack() throws Exception {
        int before = jsonBody(get("/api/v1/encounters/" + encounterId), 200).get("activeTurnIndex").asInt();

        JsonNode advanced = jsonBody(send("POST", "/api/v1/encounters/" + encounterId + "/next-turn", null), 200);
        assertThat(advanced.get("activeTurnIndex").asInt()).isNotEqualTo(before);

        JsonNode back = jsonBody(send("POST", "/api/v1/encounters/" + encounterId + "/previous-turn", null), 200);
        assertThat(back.get("activeTurnIndex").asInt()).isEqualTo(before);
    }

    @Test
    @Order(11)
    void aDefeatedGoblinDropsOut() throws Exception {
        UUID goblin = goblinIds.get(0);

        JsonNode defeated = jsonBody(send("PUT", "/api/v1/combatants/" + goblin + "/defeated",
                """
                {"defeated": true}"""), 200);

        assertThat(defeated.get("defeated").asBoolean()).isTrue();
    }

    @Test
    @Order(12)
    void aMistakeCanBeUndone() throws Exception {
        UUID goblin = goblinIds.get(1);
        int before = combatantById(goblin).get("currentHp").asInt();

        send("POST", "/api/v1/combatants/" + goblin + "/damage",
                """
                {"amount": -3}""");
        assertThat(combatantById(goblin).get("currentHp").asInt()).isEqualTo(before - 3);

        assertThat(send("POST", "/api/v1/encounters/" + encounterId + "/undo", null).statusCode())
                .as("the DM must be able to take back a wrong hit")
                .isEqualTo(200);
        assertThat(combatantById(goblin).get("currentHp").asInt()).isEqualTo(before);
    }

    @Test
    @Order(13)
    void theEncounterEndsWithASummary() throws Exception {
        JsonNode result = jsonBody(send("POST",
                "/api/v1/encounters/" + encounterId + "/end-with-summary", null), 200);

        assertThat(result.get("encounter").get("status").asString()).isEqualTo("DONE");
        assertThat(result.get("summary")).isNotNull();
    }

    @Test
    @Order(14)
    void everyScreenTouchedByTheRunStillRenders() throws Exception {
        List<String> screens = List.of(
                "/campaigns/" + campaignId,
                "/campaigns/" + campaignId + "/party",
                "/campaigns/" + campaignId + "/maps",
                "/campaigns/" + campaignId + "/encounters",
                "/campaigns/" + campaignId + "/encounters/" + encounterId,
                "/campaigns/" + campaignId + "/encounters/" + encounterId + "/setup",
                "/campaigns/" + campaignId + "/session");

        try (LazyInitLogCapture capture = new LazyInitLogCapture()) {
            for (String screen : screens) {
                HttpResponse<String> page = get(screen);
                assertThat(page.statusCode()).as("status for %s", screen).isEqualTo(200);
                assertThat(page.body().strip())
                        .as("%s must be a complete document", screen)
                        .endsWith("</html>");
            }
            assertThat(capture.lazyInitFailures())
                    .as("no screen may fail on a detached lazy association")
                    .isEmpty();
        }
    }

    private JsonNode combatantById(UUID combatantId) throws Exception {
        JsonNode combatants = jsonBody(get("/api/v1/encounters/" + encounterId + "/combatants"), 200);
        for (JsonNode combatant : combatants) {
            if (combatantId.toString().equals(combatant.get("id").asString())) {
                return combatant;
            }
        }
        throw new AssertionError("combatant " + combatantId + " is not in the encounter");
    }

    private List<UUID> combatantIdsOfKind(String kind) throws Exception {
        JsonNode combatants = jsonBody(get("/api/v1/encounters/" + encounterId + "/combatants"), 200);
        List<UUID> ids = new ArrayList<>();
        combatants.forEach(c -> {
            if (kind.equals(c.get("kind").asString())) {
                ids.add(UUID.fromString(c.get("id").asString()));
            }
        });
        return ids;
    }

    private JsonNode jsonBody(HttpResponse<String> response, int... acceptedStatuses) throws Exception {
        assertThat(acceptedStatuses)
                .as("unexpected status %d for %s: %s",
                        response.statusCode(), response.uri(), response.body())
                .contains(response.statusCode());
        return json.readTree(response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send("GET", path, null);
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher payload = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", path.startsWith("/api/") ? "application/json" : "text/html")
                .method(method, payload);
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
