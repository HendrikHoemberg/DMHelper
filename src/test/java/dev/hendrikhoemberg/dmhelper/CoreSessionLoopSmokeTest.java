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
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

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
    @Autowired private CampaignSessionRepository sessionRepository;

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

    private void failOnce(Page page, String glob, String method, Pattern url, String correlationId) {
        browserFailures.expectHttpFailure(method, url, 503);
        AtomicBoolean failed = new AtomicBoolean();
        page.route(glob, route -> {
            if (failed.compareAndSet(false, true)) {
                route.fulfill(new Route.FulfillOptions()
                        .setStatus(503)
                        .setContentType("application/problem+json")
                        .setHeaders(Map.of("X-Correlation-ID", correlationId))
                        .setBody("{\"title\":\"Unavailable\",\"detail\":\"Try the action again.\","
                                + "\"correlationId\":\"" + correlationId + "\"}"));
            } else {
                route.resume();
            }
        });
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

    private void startSession() {
        var campaign = campaignRepo.findById(campaignId).orElseThrow();
        CampaignSession session = sessionRepository.findByCampaignId(campaignId)
                .orElseGet(() -> {
                    CampaignSession s = CampaignSession.idle(campaign);
                    s.setStatus(CampaignSession.Status.RUNNING);
                    return sessionRepository.save(s);
                });
        if (!session.isOpen()) {
            session.setStatus(CampaignSession.Status.RUNNING);
            sessionRepository.save(session);
        }
    }

    @Test
    @Order(7)
    void verifyPlayerSafeProjectionStripsDmOnly() {
        startSession();
        presentationService.presentMap(campaignId, mapId);

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
    void quickNotesWorkOnAFirstPartyMemberInsertedByHtmx() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/party");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        dmPage.getByText("+ Add Member").click();
        dmPage.locator("#party-form-modal .pm-form").waitFor();
        dmPage.locator("#party-form-modal [name='characterName']").fill("Dynamic Hero");
        dmPage.locator("#party-form-modal [name='ac']").fill("16");
        dmPage.locator("#party-form-modal [name='maxHp']").fill("32");
        dmPage.locator("#party-form-modal [name='initiativeBonus']").fill("3");
        dmPage.locator("#party-form-modal [name='speed']").fill("30");
        dmPage.locator("#party-form-modal [name='passivePerception']").fill("14");
        dmPage.locator("#party-form-modal [name='passiveInsight']").fill("12");
        dmPage.locator("#party-form-modal [name='passiveInvestigation']").fill("11");
        dmPage.locator("#party-form-modal button[type='submit']").click();

        Locator card = dmPage.locator(".party-member-card", new Page.LocatorOptions().setHasText("Dynamic Hero"));
        card.waitFor();
        card.locator(".quicknotes-form input").fill("Added after the card appeared.");
        card.locator(".quicknotes-form button[type='submit']").click();
        card.locator(".quicknote-row").waitFor();

        assertThat(card.locator(".quicknote-body").textContent())
                .isEqualTo("Added after the card appeared.");
    }

    @Test
    @Order(10)
    void libraryDeepLinkActivatesAndFiltersTheRequestedTab() {
        dmPage.navigate("http://localhost:" + port + "/library?tab=spells&search=Fireball");
        dmPage.locator("#tab-spells.active").waitFor();
        dmPage.locator("#spell-results").getByText("Fireball").first().waitFor();

        assertThat(dmPage.locator("#spellSearch").inputValue()).isEqualTo("Fireball");
        assertThat(dmPage.locator("#section-spells").getAttribute("class")).doesNotContain("hidden");
    }

    @Test
    @Order(11)
    void commandPaletteOpensTheRealMapPlayRoute() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/maps");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        dmPage.evaluate("window.dispatchEvent(new CustomEvent('command-palette-toggle'))");
        dmPage.locator(".command-palette-input").fill("Test Battle Map");
        dmPage.locator(".palette-result", new Page.LocatorOptions().setHasText("Test Battle Map")).waitFor();
        dmPage.locator(".palette-result", new Page.LocatorOptions().setHasText("Test Battle Map")).click();
        dmPage.waitForURL(url -> url.contains("/session"));

        assertThat(dmPage.url()).contains("/session");
    }

    @Test
    @Order(12)
    void exportAndReimportRoundTrip() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId);
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        // Verify export form has both history checkboxes checked by default
        assertThat(dmPage.locator("#includeCombatLog").inputValue()).isEqualTo("true");
        assertThat(dmPage.locator("#includeDiceHistory").inputValue()).isEqualTo("true");

        // Export the campaign, create preview, confirm import — all via fetch from the page
        // so both history options are transmitted as request params.
        String redirect = (String) dmPage.evaluate("""
            async ([baseUrl, cid]) => {
                const exp = await fetch(baseUrl + '/campaigns/' + cid + '/package?includeCombatLog=true&includeDiceHistory=true');
                if (!exp.ok) throw new Error('Export failed: ' + exp.status);
                const body = await exp.text();

                const prv = await fetch(baseUrl + '/campaigns/package-imports/previews', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'X-DMHelper-Filename': 'rt.dmcampaign.json' },
                    body: body
                });
                if (!prv.ok) throw new Error('Preview failed: ' + await prv.text());
                const preview = await prv.json();
                if (preview.status === 'BLOCKED') {
                    throw new Error('Import blocked: ' + JSON.stringify(preview.problems));
                }

                const conf = await fetch(baseUrl + '/campaigns/package-imports/' + preview.previewId + '/confirm?acceptWarnings=true', {
                    method: 'POST'
                });
                if (!conf.ok) throw new Error('Confirm failed: ' + await conf.text());
                return conf.headers.get('Location');
            }
        """, Arrays.asList("http://localhost:" + port, campaignId.toString()));

        assertThat(redirect).isNotNull();
        String restoredId = redirect.replaceAll("/campaigns/", "");

        // Campaign detail page
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + restoredId);
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Smoke Test Campaign");

        // Maps page
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + restoredId + "/maps");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Test Battle Map");

        // Encounters page
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + restoredId + "/encounters");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Smoke Encounter");

        // Adventures page (notes/quick notes)
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + restoredId + "/adventures");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Test Module");

        // Notes list page
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + restoredId + "/notes");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        // Handouts list page
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + restoredId + "/handouts");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        // Party page
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + restoredId + "/party");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Dynamic Hero");
    }

    @Test
    @Order(13)
    void failedTokenMoveRollsBackAndRetryPersists() {
        Token before = tokenRepo.findByMapIdOrderByNameAsc(mapId).getFirst();
        int oldX = before.getPositionX();
        int oldY = before.getPositionY();
        String corr = "move-failure-1234";
        failOnce(dmPage, "**/api/v1/tokens/*/move", "PATCH",
                Pattern.compile(".*/api/v1/tokens/.+/move"), corr);

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId
                + "/session?mapId=" + mapId);
        dmPage.waitForFunction("window.battleMap && window.battleMap.tokens.length > 0");
        dmPage.evaluate("([id]) => window.battleMap.saveTokenMove(id, 333, 222)",
                List.of(before.getId().toString()));

        dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();
        assertThat(tokenRepo.findById(before.getId()).orElseThrow().getPositionX()).isEqualTo(oldX);
        assertThat(tokenRepo.findById(before.getId()).orElseThrow().getPositionY()).isEqualTo(oldY);
        assertThat(((Number) dmPage.evaluate("([id]) => window.battleMap.tokens.find(t => t.id === id).positionX",
                List.of(before.getId().toString()))).intValue()).isEqualTo(oldX);

        dmPage.locator(".toast-error .toast-action").click();
        dmPage.waitForFunction("([id]) => window.battleMap.tokens.find(t => t.id === id).positionX === 333",
                List.of(before.getId().toString()));
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(tokenRepo.findById(before.getId()).orElseThrow().getPositionX()).isEqualTo(333);
    }

    @Test
    @Order(14)
    void failedNextTurnKeepsTrackerStateAndRetryAdvances() {
        String corr = "turn-failure-1234";
        failOnce(dmPage, "**/api/v1/encounters/*/next-turn", "POST",
                Pattern.compile(".*/api/v1/encounters/.+/next-turn"), corr);
        var before = encounterService.getById(encounterId);

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId
                + "/session?mapId=" + mapId);
        dmPage.waitForFunction("window.battleMap && document.querySelector(\"[data-action='next-turn']\")");

        // Exercise the real Alpine tracker action rather than the request helper in isolation.
        dmPage.locator("[data-action='next-turn']").click();

        dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();

        var unchanged = encounterService.getById(encounterId);
        assertThat(unchanged.round()).isEqualTo(before.round());
        assertThat(unchanged.activeTurnIndex()).isEqualTo(before.activeTurnIndex());

        dmPage.locator(".toast-error .toast-action").click();
        dmPage.waitForFunction("([eid, round, turn]) => fetch('/api/v1/encounters/' + eid)"
                + ".then(r => r.json())"
                + ".then(e => e.round !== round || e.activeTurnIndex !== turn)"
                + ".catch(() => false)",
                Arrays.asList(encounterId.toString(), before.round(), before.activeTurnIndex()));

        var advanced = encounterService.getById(encounterId);
        assertThat(advanced.round() != before.round()
                || advanced.activeTurnIndex() != before.activeTurnIndex()).isTrue();
    }

    @Test
    @Order(15)
    void failedPresentationKeepsCurtainAndRetryShowsMap() {
        startSession();
        presentationService.curtain(campaignId);
        String corr = "present-failure-1234";
        failOnce(dmPage, "**/api/v1/campaigns/*/table/presentation", "PUT",
                Pattern.compile(".*/api/v1/campaigns/.+/table/presentation"), corr);
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId
                + "/session?mapId=" + mapId);
        dmPage.waitForFunction("window.battleMap && window.battleMap.tokens.length > 0");

        dmPage.evaluate("([cid, mid, corr]) => { window.dmRequest(`/api/v1/campaigns/${cid}/table/presentation`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ mode: 'MAP', ref: mid }) }).catch(e => window.reportActionFailure('Could not show this map to the table.', e, () => window.dmRequest(`/api/v1/campaigns/${cid}/table/presentation`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ mode: 'MAP', ref: mid }) }))); }", Arrays.asList(campaignId.toString(), mapId.toString(), corr));
        dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();
        assertThat(presentationService.getCurrentState().mode()).isEqualTo("CURTAIN");

        dmPage.locator(".toast-error .toast-action").click();
        // Wait for the async retry request to complete
        try {
            for (int i = 0; i < 50; i++) {
                if ("MAP".equals(presentationService.getCurrentState().mode())) break;
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(presentationService.getCurrentState().mode()).isEqualTo("MAP");
    }

    @Test
    @Order(16)
    void failedDiceRollKeepsExpressionAndRetryCompletes() {
        String corr = "dice-failure-1234";
        failOnce(dmPage, "**/api/v1/roll", "POST",
                Pattern.compile(".*/api/v1/roll"), corr);
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId);
        dmPage.locator("#diceToggle").click();
        Locator expression = dmPage.locator(".dice-panel input[type='text']");
        expression.fill("2d6+4");
        dmPage.locator(".dice-panel .dice-input-row button").click();

        dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();
        assertThat(expression.inputValue()).isEqualTo("2d6+4");

        dmPage.locator(".toast-error .toast-action").click();
        dmPage.locator(".dice-result-total").waitFor();
        assertThat(expression.inputValue()).isEmpty();
    }

    @Test
    @Order(17)
    void failedDefeatedToggleRestoresThePersistedAndVisibleState() {
        Token before = tokenRepo.findByMapIdOrderByNameAsc(mapId).getFirst();
        boolean originalDead = before.isDead();
        String corr = "dead-failure-1234";
        failOnce(dmPage, "**/api/v1/tokens/*/dead", "PATCH",
                Pattern.compile(".*/api/v1/tokens/.+/dead"), corr);

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId
                + "/session?mapId=" + mapId);
        dmPage.waitForFunction("window.battleMap && window.battleMap.tokens.length > 0");
        dmPage.evaluate("([id]) => window.battleMap.selectToken(id)",
                List.of(before.getId().toString()));

        dmPage.evaluate("([id, dead]) => window.battleMap.markDead(id, dead)",
                Arrays.asList(before.getId().toString(), !originalDead));
        dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();

        assertThat(tokenRepo.findById(before.getId()).orElseThrow().isDead()).isEqualTo(originalDead);
        assertThat(dmPage.evaluate("([id]) => window.battleMap.tokens.find(t => t.id === id).dead",
                List.of(before.getId().toString()))).isEqualTo(originalDead);
    }

    @Test
    @Order(18)
    void failedStatblockTokenCreationRetainsTheSearchForRetry() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId
                + "/session?mapId=" + mapId);
        dmPage.waitForFunction("window.battleMap && window.battleMap.tokens.length > 0");

        String sbId = (String) dmPage.evaluate("""
            () => fetch('/api/v1/library/statblocks/search?q=Goblin')
                .then(r => r.json())
                .then(results => results.length > 0 ? results[0].id : null)
            """);
        assertThat(sbId).isNotNull();

        int tokenCountBefore = ((Number) dmPage.evaluate("window.battleMap.tokens.length")).intValue();

        String corr = "statblock-failure-1234";
        failOnce(dmPage, "**/api/v1/library/statblocks/*", "GET",
                Pattern.compile(".*/api/v1/library/statblocks/[^/?]+$"), corr);

        dmPage.evaluate("([id]) => window.battleMap.createTokenFromStatblock(id)", List.of(sbId));
        dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();

        assertThat(((Number) dmPage.evaluate("window.battleMap.tokens.length")).intValue())
                .isEqualTo(tokenCountBefore);

        dmPage.locator(".toast-error .toast-action").click();
        dmPage.waitForFunction("([expected]) => window.battleMap.tokens.length > expected",
                List.of(tokenCountBefore));
    }

    @Test
    @Order(19)
    void aRejectedRetryRemainsVisibleAndDoesNotBecomeAnUnhandledPageError() {
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId);
        dmPage.evaluate("() => window.showToast('Initial failure', 'error', 15000, {"
                + " label: 'Retry',"
                + " handler: () => Promise.reject(new window.DmRequestError("
                + "   'Still unavailable.', 503, 'retry-failure-1234'))"
                + "})");

        dmPage.locator(".toast-error .toast-action").click();

        dmPage.locator(".toast-error",
                new Page.LocatorOptions().setHasText("retry-failure-1234")).waitFor();
        assertThat(dmPage.locator(".toast-error .toast-action").count()).isEqualTo(1);
    }

    @Test
    @Order(20)
    void failedQuickNoteAddRetainsTextAndRetrySavesIt() {
        String corr = "quicknote-failure-1234";
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/adventures");
        failOnce(dmPage, "**/api/v1/campaigns/*/quicknotes", "POST",
                Pattern.compile(".*/api/v1/campaigns/.+/quicknotes"), corr);

        Locator input = dmPage.locator(".quicknotes-form input").first();
        input.fill("Keep this unsaved clue.");
        dmPage.locator(".quicknotes-form button[type='submit']").first().click();
        dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(corr)).waitFor();

        assertThat(input.inputValue()).isEqualTo("Keep this unsaved clue.");
        dmPage.locator(".toast-error .toast-action").click();
        dmPage.locator(".quicknote-row",
                new Page.LocatorOptions().setHasText("Keep this unsaved clue.")).waitFor();
        assertThat(input.inputValue()).isEmpty();
    }
}
