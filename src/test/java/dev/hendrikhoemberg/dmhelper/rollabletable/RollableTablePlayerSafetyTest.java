package dev.hendrikhoemberg.dmhelper.rollabletable;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntry;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RollableTablePlayerSafetyTest {

    private static final String SECRET_MARKER = "PLAYER_SAFETY_MARKER_c4f8e2a1";

    @LocalServerPort
    private int port;

    @Autowired
    private CampaignRepository campaignRepo;

    @Autowired
    private RollableTableRepository tableRepo;

    private Campaign campaign;

    @AfterEach
    void tearDown() {
        if (campaign != null && campaign.getId() != null) {
            campaignRepo.delete(campaign);
        }
    }

    @Test
    void playerPageDoesNotLeakRollableTableContent() throws Exception {
        campaign = new Campaign();
        campaign.setName("Safety Campaign");
        campaign.setDescription("Player safety test campaign");
        campaign = campaignRepo.save(campaign);

        RollableTable secretTable = new RollableTable();
        secretTable.setSourceKey("safety_secret");
        secretTable.setSource(dev.hendrikhoemberg.dmhelper.library.data.ContentSource.CUSTOM);
        secretTable.setName("DM Secrets - " + SECRET_MARKER);
        secretTable.setAddressMode(TableAddressMode.WEIGHTED);
        secretTable.setCategory(TableCategory.GENERIC);
        secretTable.setCampaign(campaign);
        RollableTableEntry entry = new RollableTableEntry();
        entry.setEntryKey("secret-entry");
        entry.setWeight(1);
        entry.setResultText("Secret result: " + SECRET_MARKER);
        entry.setTable(secretTable);
        secretTable.getEntries().add(entry);
        tableRepo.save(secretTable);

        var client = HttpClient.newHttpClient();

        var playerRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/player"))
                .build();
        var playerResponse = client.send(playerRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(playerResponse.statusCode()).isLessThan(500);
        assertThat(playerResponse.body()).doesNotContain(SECRET_MARKER);

        var stateRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/table/state"))
                .build();
        var stateResponse = client.send(stateRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(stateResponse.body()).doesNotContain(SECRET_MARKER);
    }
}
