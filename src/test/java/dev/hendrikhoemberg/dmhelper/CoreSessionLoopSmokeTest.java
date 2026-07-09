package dev.hendrikhoemberg.dmhelper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreSessionLoopSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired private CampaignRepository campaignRepo;
    @Autowired private GameMapRepository mapRepo;
    @Autowired private TokenRepository tokenRepo;
    @Autowired private EncounterService encounterService;
    @Autowired private TablePresentationService presentationService;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext dmContext;
    private Page dmPage;

    private UUID campaignId;
    private UUID mapId;
    private UUID encounterId;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void setUp() {
        dmContext = browser.newContext();
        dmPage = dmContext.newPage();
    }

    @AfterEach
    void tearDown() {
        if (dmContext != null) dmContext.close();
    }

    @Test
    @Order(1)
    void createCampaign() {
        dmPage.navigate("http://localhost:" + port + "/campaigns");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        dmPage.click("text=+ New Campaign");
        dmPage.waitForSelector("#campaignName");

        dmPage.fill("#campaignName", "Smoke Test Campaign");
        dmPage.fill("#campaignDescription", "Playwright smoke test");
        dmPage.click("button[type='submit']");

        dmPage.waitForTimeout(500);
        var campaigns = campaignRepo.findAllByOrderByNameAsc();
        assertThat(campaigns).isNotEmpty();
        campaignId = campaigns.getFirst().getId();
    }

    @Test
    @Order(2)
    void createMap() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/maps");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        dmPage.click("text=+ New Map");
        dmPage.waitForSelector("#mapName");

        dmPage.fill("#mapName", "Test Battle Map");
        dmPage.fill("#gridWidth", "20");
        dmPage.fill("#gridHeight", "15");
        dmPage.click("button[type='submit']");

        dmPage.waitForTimeout(500);
        var maps = mapRepo.findAll();
        assertThat(maps).isNotEmpty();
        mapId = maps.getFirst().getId();
    }

    @Test
    @Order(3)
    void placeTokenOnMap() {
        Token token = new Token();
        token.setMap(mapRepo.findById(mapId).orElseThrow());
        token.setName("Smoke Goblin");
        token.setKind("NPC");
        token.setMaxHp(20);
        token.setCurrentHp(20);
        token.setPositionX(200);
        token.setPositionY(150);
        token.setColor("#ff4444");
        tokenRepo.save(token);

        var tokens = tokenRepo.findByMapIdOrderByNameAsc(mapId);
        assertThat(tokens).isNotEmpty();
        assertThat(tokens.getFirst().getName()).isEqualTo("Smoke Goblin");
    }

    @Test
    @Order(4)
    void startEncounterAndAdvanceTurns() {
        encounterId = encounterService.create(campaignId,
                new EncounterService.CreateRequest("Smoke Encounter", mapId)).id();

        encounterService.prefillFromMap(encounterId, mapId);
        encounterService.activate(encounterId);

        var combatants = encounterService.getCombatants(encounterId);
        assertThat(combatants).isNotEmpty();

        encounterService.nextTurn(encounterId);

        var encounter = encounterService.getById(encounterId);
        assertThat(encounter.round()).isEqualTo(1);
        assertThat(encounter.activeTurnIndex()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(5)
    void verifyPlayerViewPageLoads() {
        Page playerPage = browser.newContext().newPage();
        playerPage.navigate("http://localhost:" + port + "/player");
        playerPage.waitForLoadState(LoadState.NETWORKIDLE);

        String title = playerPage.title();
        assertThat(title).isNotEmpty();

        playerPage.context().close();
    }

    @Test
    @Order(6)
    void verifyPlayerSafeProjectionStripsDmOnly() {
        presentationService.presentMap(mapId);

        Page playerPage = browser.newContext().newPage();
        playerPage.navigate("http://localhost:" + port + "/player");
        playerPage.waitForLoadState(LoadState.NETWORKIDLE);

        String pageContent = playerPage.content();
        assertThat(pageContent).doesNotContain("dm-only", "dmMode");

        playerPage.context().close();
    }
}
