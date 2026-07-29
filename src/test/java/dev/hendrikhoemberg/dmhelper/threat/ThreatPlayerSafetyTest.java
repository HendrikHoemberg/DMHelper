package dev.hendrikhoemberg.dmhelper.threat;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPin;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.threat.service.MapThreatPinService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Threat definitions, mechanics, provenance, and map pins must never leak into
 * player-visible surfaces. DM pin API remains the only channel for pin data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ThreatPlayerSafetyTest {

    private static final String DEF_MARKER = "THREAT_DEF_MARKER_a7f3c91e";
    private static final String MECH_MARKER = "THREAT_MECH_MARKER_b8e4d02f";
    private static final String PROV_MARKER = "THREAT_PROV_MARKER_c9f5e13a";
    private static final String PIN_KEY = "threat-pin-key-d0a6f24b";
    private static final String PIN_LABEL = "THREAT_PIN_LABEL_e1b7g35c";

    @LocalServerPort
    private int port;

    @Autowired private CampaignRepository campaignRepo;
    @Autowired private GameMapRepository mapRepo;
    @Autowired private TrapRepository trapRepo;
    @Autowired private MapThreatPinRepository pinRepo;
    @Autowired private CampaignSessionRepository sessionRepo;
    @Autowired private MapThreatPinService pinService;

    private Campaign campaign;
    private GameMap gameMap;
    private Trap trap;
    private UUID pinId;

    @Test
    void playerSurfacesNeverLeakThreatDefinitionMechanicsProvenanceOrPins() throws Exception {
        campaign = new Campaign();
        campaign.setName("Threat Safety Campaign");
        campaign.setDescription("Player safety for traps");
        campaign = campaignRepo.save(campaign);

        gameMap = new GameMap();
        gameMap.setCampaign(campaign);
        gameMap.setName("Safety Map");
        gameMap.setGridWidth(20);
        gameMap.setGridHeight(15);
        gameMap.setCellSizePx(48);
        gameMap.setDocument("{\"schemaVersion\":2,\"grid\":{\"width\":20,\"height\":15,\"cellSizePx\":48,\"gridType\":\"square\"},\"layers\":[],\"primitives\":[],\"customTerrain\":[]}");
        gameMap = mapRepo.save(gameMap);

        ContentProvenance provenance = new ContentProvenance();
        provenance.setSourceTitle(PROV_MARKER);
        provenance.setSourceLocator("page " + PROV_MARKER);
        provenance.setConverterId(PROV_MARKER);

        trap = new Trap();
        trap.setSourceKey("safety-secret-trap");
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(campaign);
        trap.setName("Secret Trap " + DEF_MARKER);
        trap.setDescription("Definition body " + DEF_MARKER);
        trap.setSeverity(ThreatSeverity.DEADLY);
        trap.setResetMode(ThreatResetMode.MANUAL);
        trap.setTriggerDescription("Trigger " + MECH_MARKER);
        trap.setDamageExpression("3d6");
        trap.getDamageTypes().add(DamageType.PIERCING);
        trap.setAdditionalEffect("Effect " + MECH_MARKER);
        trap.setCountermeasureNotes("Counter " + MECH_MARKER);
        trap.setProvenance(provenance);
        trap = trapRepo.save(trap);

        MapThreatPin pin = new MapThreatPin();
        pin.setMap(gameMap);
        pin.setPinKey(PIN_KEY);
        pin.setThreatKind(ThreatKind.TRAP);
        pin.setThreatId(trap.getId());
        pin.setXPx(96);
        pin.setYPx(144);
        pin.setLabel(PIN_LABEL);
        pin.setSortOrder(0);
        pin = pinRepo.save(pin);
        pinId = pin.getId();

        CampaignSession session = CampaignSession.idle(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setWorkspaceMap(gameMap);
        sessionRepo.save(session);

        var client = HttpClient.newHttpClient();
        String base = "http://localhost:" + port;

        String playerBody = client.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/player")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertNoLeak(playerBody, "player page");

        String tableState = client.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/api/v1/table/state")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertNoLeak(tableState, "table state");
        assertThat(tableState)
                .as("table state must not include threat pin identity")
                .doesNotContain(pinId.toString())
                .doesNotContain(trap.getId().toString())
                .doesNotContain("\"TRAP\"")
                .doesNotContain(PIN_KEY);

        // Player bootstrap is the initial WebSocket/table payload — same projection as table state.
        assertThat(tableState).contains("\"mode\"");

        String mapDocument = client.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/api/v1/maps/" + gameMap.getId() + "/document")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        // Document endpoint is DM-gated when pin is enabled; with test pin disabled it is reachable
        // but must still not embed threat pins (they live outside MapDocumentDto).
        if (!mapDocument.isBlank() && !mapDocument.contains("\"status\":403") && !mapDocument.contains("Forbidden")) {
            assertNoLeak(mapDocument, "map document");
            assertThat(mapDocument)
                    .doesNotContain(PIN_KEY)
                    .doesNotContain(PIN_LABEL)
                    .doesNotContain(pinId.toString());
        }

        // DM pins API must include the markers.
        var dmPins = pinService.listCombinedPins(gameMap.getId());
        assertThat(dmPins).anySatisfy(p -> {
            assertThat(p.key()).isEqualTo(PIN_KEY);
            assertThat(p.title()).isEqualTo(PIN_LABEL);
            assertThat(p.threatId()).isEqualTo(trap.getId());
            assertThat(p.threatKind()).isEqualTo(ThreatKind.TRAP);
        });
        assertThat(trap.getName()).contains(DEF_MARKER);
        assertThat(trap.getTriggerDescription()).contains(MECH_MARKER);
        assertThat(trap.getProvenance().getSourceTitle()).contains(PROV_MARKER);
    }

    private void assertNoLeak(String body, String surface) {
        assertThat(body)
                .as(surface + " must not leak threat markers")
                .doesNotContain(DEF_MARKER)
                .doesNotContain(MECH_MARKER)
                .doesNotContain(PROV_MARKER)
                .doesNotContain(PIN_KEY)
                .doesNotContain(PIN_LABEL);
    }
}
