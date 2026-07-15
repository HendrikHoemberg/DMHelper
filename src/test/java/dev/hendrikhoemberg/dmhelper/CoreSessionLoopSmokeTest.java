package dev.hendrikhoemberg.dmhelper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
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
    @Autowired private AdventureRepository adventureRepo;
    @Autowired private ChapterRepository chapterRepo;
    @Autowired private GameMapRepository mapRepo;
    @Autowired private TokenRepository tokenRepo;
    @Autowired private EncounterService encounterService;
    @Autowired private TablePresentationService presentationService;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext dmContext;
    private Page dmPage;
    private final BrowserFailureCollector browserFailures = new BrowserFailureCollector();

    private Page guardedPage(BrowserContext context) {
        Page page = context.newPage();
        browserFailures.attach(page);
        return page;
    }

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
        browserFailures.clear();
        dmContext = browser.newContext();
        dmPage = guardedPage(dmContext);
    }

    @AfterEach
    void tearDown() {
        try {
            browserFailures.assertNoFailures();
        } finally {
            if (dmContext != null) dmContext.close();
        }
    }

    @Test
    @Order(1)
    void createCampaign() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/new");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.evaluate("document.querySelector('form')")).isNotNull();

        dmPage.navigate("http://localhost:" + port + "/campaigns");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        dmPage.evaluate("fetch('/campaigns', { " +
                "method: 'POST', " +
                "headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, " +
                "body: 'name=Smoke+Test+Campaign&description=Playwright+smoke+test' " +
                "})");

        dmPage.waitForTimeout(500);
        var campaigns = campaignRepo.findAllByOrderByNameAsc();
        assertThat(campaigns).isNotEmpty();
        campaignId = campaigns.getFirst().getId();
    }

    @Test
    @Order(2)
    void createAdventureWithChapter() {
        var campaign = campaignRepo.findById(campaignId).orElseThrow();

        Adventure adv = new Adventure();
        adv.setCampaign(campaign);
        adv.setName("Test Module");
        adventureRepo.save(adv);

        Chapter ch = new Chapter();
        ch.setAdventure(adv);
        ch.setTitle("Chapter 1");
        chapterRepo.save(ch);

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/adventures");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(dmPage.textContent("body")).contains("Test Module");

        dmPage.click("text=Test Module");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        assertThat(dmPage.textContent("body")).contains("Chapter 1");
    }

    @Test
    @Order(3)
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
    @Order(4)
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
    @Order(5)
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
    @Order(6)
    void verifyPlayerViewPageLoads() {
        BrowserContext playerContext = browser.newContext();
        Page playerPage = guardedPage(playerContext);
        playerPage.navigate("http://localhost:" + port + "/player");
        playerPage.waitForLoadState(LoadState.NETWORKIDLE);

        String title = playerPage.title();
        assertThat(title).isNotEmpty();

        playerContext.close();
    }

    @Test
    @Order(7)
    void verifyPlayerSafeProjectionStripsDmOnly() {
        presentationService.presentMap(mapId);

        BrowserContext playerContext = browser.newContext();
        Page playerPage = guardedPage(playerContext);
        playerPage.navigate("http://localhost:" + port + "/player");
        playerPage.waitForLoadState(LoadState.NETWORKIDLE);

        String pageContent = playerPage.content();
        assertThat(pageContent).doesNotContain("dm-only", "dmMode");

        playerContext.close();
    }

    @Test
    @Order(8)
    void createQuickNoteWithoutTemplateOrRequestErrors() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/adventures");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        dmPage.locator(".quicknotes-form input").first().fill("Remember the hidden stair.");
        dmPage.locator(".quicknotes-form button[type='submit']").first().click();
        dmPage.locator(".quicknote-row").first().waitFor();

        assertThat(dmPage.locator(".quicknote-body").first().textContent())
                .isEqualTo("Remember the hidden stair.");
    }

    @Test
    @Order(9)
    void commandPaletteOpensTheRealMapPlayRoute() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/maps");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        dmPage.evaluate("window.dispatchEvent(new CustomEvent('command-palette-toggle'))");
        dmPage.locator(".command-palette-input").fill("Test Battle Map");
        dmPage.locator(".palette-result", new Page.LocatorOptions().setHasText("Test Battle Map")).waitFor();
        dmPage.locator(".palette-result", new Page.LocatorOptions().setHasText("Test Battle Map")).click();
        dmPage.waitForURL(url -> url.endsWith("/play"));

        assertThat(dmPage.url()).endsWith("/play");
    }
}
