package dev.hendrikhoemberg.dmhelper;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportCoordinator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriteRequest;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableDraftStatus;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableDraftType;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreSessionLoopSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired private CampaignRepository campaignRepo;
    @Autowired private CampaignExportCoordinator exportCoordinator;
    @Autowired private AdventureRepository adventureRepo;
    @Autowired private ChapterRepository chapterRepo;
    @Autowired private SceneRepository sceneRepo;
    @Autowired private AdventureService adventureService;
    @Autowired private GameMapRepository mapRepo;
    @Autowired private GameMapService gameMapService;
    @Autowired private TokenRepository tokenRepo;
    @Autowired private EncounterService encounterService;
    @Autowired private TablePresentationService presentationService;
    @Autowired private CampaignSessionRepository sessionRepository;
    @Autowired private SessionLifecycleService sessionLifecycleService;
    @Autowired private HandoutService handoutService;
    @Autowired private NoteRepository noteRepository;
    @Autowired private PartyMemberRepository partyMemberRepository;
    @Autowired private dev.hendrikhoemberg.dmhelper.rollabletable.data.TableRollLogRepository rollLogRepository;
    @Autowired private EquipmentItemRepository equipmentItemRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private TreasuryService treasuryService;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CombatantRepository combatantRepository;
    @Autowired private dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository trapRepository;
    @Autowired private dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository hazardRepository;
    @Autowired private dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository mapThreatPinRepository;
    @Autowired private dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository sceneSectionRepository;
    @Autowired private dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository combatLogEntryRepository;
    @Autowired private dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository rollableTableRepository;

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
    private UUID secondMapId;
    private UUID encounterId;
    private UUID chapterId;
    private UUID handoutId;

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
        chapterId = chapterRepo.save(ch).getId();

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

        secondMapId = gameMapService.create(campaignId, "Fallback Map", 30, 20, 48).getId();

        Scene entry = new Scene();
        entry.setChapter(chapterRepo.findById(chapterId).orElseThrow());
        entry.setTitle("Upper Crypt");
        entry.setBody("The stair descends into cold stone.");
        entry.setSortOrder(1);
        entry.setMap(mapRepo.findById(mapId).orElseThrow());
        entry = sceneRepo.save(entry);
        Scene lower = new Scene();
        lower.setChapter(entry.getChapter());
        lower.setTitle("Lower Crypt");
        lower.setBody("Guardians wait beyond the western seal.");
        lower.setSortOrder(2);
        lower.setMap(entry.getMap());
        sceneRepo.save(lower);
        adventureService.setCurrentScene(campaignId, entry.getId());
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
        boolean sessionAlreadyOpen = sessionRepository.findByCampaignId(campaignId)
                .map(CampaignSession::isOpen)
                .orElse(false);
        if (!sessionAlreadyOpen) {
            sessionLifecycleService.start(campaignId, mapId);
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
        assertThat(dmPage.locator("#party-form-modal").isHidden())
                .as("closed party modal must leave layout and pointer hit testing")
                .isTrue();
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
    void runsTheCompleteCockpitFlowThroughVisibleControls() throws Exception {
        encounterService.endEncounter(encounterId);
        UUID plannedEncounterId = encounterService.create(campaignId,
                new EncounterService.CreateRequest("Crypt Guardians", mapId)).id();
        encounterService.prefillFromMap(plannedEncounterId, mapId);
        var handout = handoutService.createImported(campaignId,
                "<img src=x onerror=window.playerXss=true>", "",
                "seal.png", "image/png", Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
        handoutId = handout.getId();
        handoutService.setDmOnly(handoutId, false);

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/maps");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        dmPage.evaluate("window.dispatchEvent(new CustomEvent('command-palette-toggle'))");
        dmPage.locator(".command-palette-input").fill("Test Battle Map");
        dmPage.locator(".palette-result", new Page.LocatorOptions().setHasText("Test Battle Map")).waitFor();
        dmPage.locator(".palette-result", new Page.LocatorOptions().setHasText("Test Battle Map")).click();
        dmPage.waitForURL(url -> url.contains("/session"));

        assertThat(dmPage.url()).contains("/session");
        dmPage.locator("[aria-label='Battle map controls']").waitFor();
        Number domContentLoaded = (Number) dmPage.evaluate(
                "performance.getEntriesByType('navigation')[0].domContentLoadedEventEnd");
        assertThat(domContentLoaded.doubleValue()).isLessThan(2_000);
        assertThat(dmPage.locator("button", new Page.LocatorOptions().setHasText("Present current map")).count())
                .isEqualTo(1);
        assertThat(dmPage.locator("[data-presentation-mode]").count()).isEqualTo(1);

        dmPage.keyboard().press("]");
        dmPage.locator("[data-current-scene]",
                new Page.LocatorOptions().setHasText("Lower Crypt")).waitFor();
        Locator planned = dmPage.locator(".planned-encounter-row",
                new Page.LocatorOptions().setHasText("Crypt Guardians"));
        planned.locator("button", new Locator.LocatorOptions().setHasText("Activate")).click();
        dmPage.locator("[data-action='next-turn']").waitFor();
        encounterId = plannedEncounterId;
        var beforeTurn = encounterService.getById(encounterId);
        dmPage.keyboard().press("n");
        dmPage.waitForFunction("([eid, round, turn]) => fetch('/api/v1/encounters/' + eid)"
                        + ".then(r => r.json()).then(e => e.round !== round || e.activeTurnIndex !== turn)",
                Arrays.asList(encounterId.toString(), beforeTurn.round(), beforeTurn.activeTurnIndex()));

        String mapCorrelation = "map-switch-failure-1234";
        failOnce(dmPage, "**/api/v1/maps/*", "GET",
                Pattern.compile(".*/api/v1/maps/" + secondMapId), mapCorrelation);
        Locator mapPicker = dmPage.locator("#cockpitMapPicker");
        mapPicker.selectOption(secondMapId.toString());
        dmPage.locator(".toast-error", new Page.LocatorOptions().setHasText(mapCorrelation)).waitFor();
        assertThat(dmPage.evaluate("window.battleMap.mapId")).isEqualTo(mapId.toString());
        assertThat(mapPicker.inputValue()).isEqualTo(mapId.toString());
        assertThat(sessionRepository.findByCampaignId(campaignId).orElseThrow()
                .getWorkspaceMap().getId()).isEqualTo(mapId);
        dmPage.locator(".toast-error .toast-action").click();
        dmPage.waitForFunction("([id]) => window.battleMap.mapId === id", List.of(secondMapId.toString()));
        mapPicker.selectOption(mapId.toString());
        dmPage.waitForFunction("([id]) => window.battleMap.mapId === id", List.of(mapId.toString()));

        BrowserContext playerContext = browser.newContext();
        Page playerPage = guardedPage(playerContext);
        playerPage.navigate("http://localhost:" + port + "/player");
        playerPage.locator("#pvStatus", new Page.LocatorOptions().setHasText("Connected")).waitFor();
        dmPage.locator("button", new Page.LocatorOptions().setHasText("Present current map")).click();
        playerPage.locator("#pvCanvas").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        assertThat(presentationService.getCurrentState().mode()).isEqualTo("MAP");
        dmPage.locator("#cockpitHandoutPicker").selectOption(handoutId.toString());
        Locator playerHandout = playerPage.locator(".pv-handout img");
        playerHandout.waitFor();
        assertThat(playerHandout.getAttribute("alt"))
                .isEqualTo("<img src=x onerror=window.playerXss=true>");
        assertThat(playerPage.evaluate("window.playerXss")).isNull();
        dmPage.locator("button", new Page.LocatorOptions().setHasText("Curtain")).click();
        playerPage.locator(".pv-curtain").waitFor();
        playerContext.close();

        dmPage.locator("button", new Page.LocatorOptions().setHasText("Search")).click();
        dmPage.locator(".command-palette-overlay").waitFor();
        dmPage.keyboard().press("Escape");
        dmPage.locator(".command-palette-overlay").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

        dmPage.locator("button", new Page.LocatorOptions().setHasText("Dice")).click();
        dmPage.locator(".dice-panel").waitFor();
        dmPage.keyboard().press("Escape");
        dmPage.locator(".dice-panel").waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));

        dmPage.keyboard().press("q");
        Object shortcutDiagnostics = dmPage.evaluate("""
                ({active: document.activeElement?.outerHTML,
                  notes: document.querySelectorAll('.quicknotes-form input').length,
                  modals: Array.from(document.querySelectorAll('[aria-modal=true]')).map(e => ({
                    label: e.getAttribute('aria-label'), hidden: e.hidden,
                    rects: e.getClientRects().length, display: getComputedStyle(e).display
                  }))})
                """);
        assertThat(dmPage.evaluate("document.activeElement?.matches('.quicknotes-form input')"))
                .as("shortcut diagnostics: %s", shortcutDiagnostics)
                .isEqualTo(true);
        dmPage.locator("button[x-ref='sessionButton']").click();
        Locator lifecycle = dmPage.locator("[aria-label='Session lifecycle']");
        lifecycle.waitFor();
        dmPage.keyboard().press("Control+K");
        dmPage.keyboard().press("Control+R");
        dmPage.keyboard().press("?");
        assertThat(lifecycle.isVisible()).isTrue();
        assertThat(dmPage.locator(".command-palette-overlay").isVisible()).isFalse();
        assertThat(dmPage.locator(".dice-panel").isVisible()).isFalse();
        assertThat(dmPage.locator("#shortcut-overlay").isVisible()).isFalse();
        dmPage.keyboard().press("q");
        assertThat(dmPage.evaluate("document.activeElement?.matches('.quicknotes-form input')"))
                .isEqualTo(false);
        lifecycle.locator("button", new Locator.LocatorOptions().setHasText("Pause")).click();
        dmPage.waitForFunction("document.querySelector('[data-session-status]').textContent === 'PAUSED'");
        lifecycle.locator("button", new Locator.LocatorOptions().setHasText("Resume")).click();
        dmPage.waitForFunction("document.querySelector('[data-session-status]').textContent === 'RUNNING'");

        lifecycle.locator("button", new Locator.LocatorOptions().setHasText("Review & Complete")).click();
        Locator draftTitle = lifecycle.locator("#sessionDraftTitle");
        Locator draftBody = lifecycle.locator("#sessionDraftBody");
        draftTitle.waitFor();
        draftTitle.fill("Crypt session");
        draftBody.fill(draftBody.inputValue() + "\nThe western seal remains unresolved.\n");
        String completeCorrelation = "session-complete-failure-1234";
        failOnce(dmPage, "**/api/v1/campaigns/*/session/complete", "POST",
                Pattern.compile(".*/api/v1/campaigns/.+/session/complete"), completeCorrelation);
        lifecycle.locator("button[type='submit']").click();
        dmPage.locator(".toast-error",
                new Page.LocatorOptions().setHasText(completeCorrelation)).waitFor();
        assertThat(draftBody.inputValue()).contains("western seal remains unresolved");
        dmPage.locator(".toast-error .toast-action").click();
        dmPage.waitForURL(url -> url.contains("/notes"));
        assertThat(noteRepository.findByCampaignIdAndTypeOrderByCreatedAtDesc(
                campaignId, NoteType.SESSION_LOG)).singleElement()
                .satisfies(note -> assertThat(note.getBody()).contains("western seal remains unresolved"));

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/session");
        dmPage.locator("button[x-ref='sessionButton']").click();
        lifecycle = dmPage.locator("[aria-label='Session lifecycle']");
        lifecycle.locator("button", new Locator.LocatorOptions().setHasText("Start")).click();
        dmPage.waitForFunction("document.querySelector('[data-session-status]').textContent === 'RUNNING'");
        dmPage.reload();
        assertThat(dmPage.locator("[data-session-status]").textContent()).isEqualTo("RUNNING");

        dmPage.keyboard().press("?");
        Locator shortcutHelp = dmPage.locator("[aria-label='Keyboard shortcuts']");
        shortcutHelp.waitFor();
        assertThat(shortcutHelp.textContent()).contains("Focus quick note", "Present the current map");
        dmPage.keyboard().press("Escape");
    }

    @Test
    @Order(12)
    void exportAndReimportRoundTrip() throws Exception {
        var directArtifact = exportCoordinator.export(campaignId);
        new CampaignPackageWriter().write(directArtifact.writeRequest(), new ByteArrayOutputStream());
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
                if (!exp.ok) throw new Error('Export failed: ' + exp.status + ' ' + await exp.text());
                const contentType = exp.headers.get('Content-Type') || 'application/octet-stream';
                const zipped = contentType.includes('zip');
                const body = await exp.arrayBuffer();

                const prv = await fetch(baseUrl + '/campaigns/package-imports/previews', {
                    method: 'POST',
                    headers: {
                        'Content-Type': contentType,
                        'X-DMHelper-Filename': zipped ? 'rt.dmcampaign' : 'rt.dmcampaign.json'
                    },
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

        CampaignSession restoredSession = sessionRepository
                .findByCampaignId(UUID.fromString(restoredId)).orElseThrow();
        assertThat(restoredSession.getStatus()).isEqualTo(CampaignSession.Status.RUNNING);
        UUID restoredMapId = restoredSession.getWorkspaceMap().getId();
        assertThat(mapRepo.findById(restoredMapId).orElseThrow().getName())
                .isEqualTo("Test Battle Map");

        String referenceRoot = "campaigns/v2/published-adventure-shaped.dmcampaign/";
        CampaignManifestV2 referenceManifest = JsonMapper.builder().build().readValue(
                new ClassPathResource(referenceRoot + "manifest.json").getInputStream(),
                CampaignManifestV2.class);
        Map<String, InputStreamSource> referenceAssets = new HashMap<>();
        for (var asset : referenceManifest.assets()) {
            referenceAssets.put(asset.key(), new ClassPathResource(referenceRoot + asset.path()));
        }
        ByteArrayOutputStream referencePackage = new ByteArrayOutputStream();
        new CampaignPackageWriter().write(new CampaignPackageWriteRequest(
                "published-adventure-shaped.dmcampaign", referenceManifest, referenceAssets), referencePackage);
        String referencePackageBase64 = Base64.getEncoder().encodeToString(referencePackage.toByteArray());
        String referenceRedirect = (String) dmPage.evaluate("""
            async ([baseUrl, encodedPackage]) => {
                const raw = atob(encodedPackage);
                const packageBytes = Uint8Array.from(raw, ch => ch.charCodeAt(0));
                const previewResponse = await fetch(baseUrl + '/campaigns/package-imports/previews', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/zip',
                               'X-DMHelper-Filename': 'published-adventure-shaped.dmcampaign' },
                    body: packageBytes
                });
                if (!previewResponse.ok) throw new Error('Preview failed: ' + await previewResponse.text());
                const preview = await previewResponse.json();
                if (preview.status === 'BLOCKED') throw new Error(JSON.stringify(preview.problems));
                const confirm = await fetch(baseUrl + '/campaigns/package-imports/' + preview.previewId
                        + '/confirm?acceptWarnings=true', { method: 'POST' });
                if (!confirm.ok) throw new Error('Confirm failed: ' + await confirm.text());
                return confirm.headers.get('Location');
            }
            """, Arrays.asList("http://localhost:" + port, referencePackageBase64));
        dmPage.navigate("http://localhost:" + port + referenceRedirect + "/session");
        dmPage.locator("[data-session-status]").waitFor();
        Number referenceRender = (Number) dmPage.evaluate(
                "performance.getEntriesByType('navigation')[0].domContentLoadedEventEnd");
        assertThat(referenceRender.doubleValue()).isLessThan(2_000);
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

    @Test
    @Order(21)
    void sheetDetailAndLiveStateEditing() {
        PartyMember member = partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId)
                .stream().filter(m -> "Dynamic Hero".equals(m.getCharacterName()))
                .findFirst().orElseThrow();
        UUID memberId = member.getId();

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/party");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        dmPage.evaluate("""
            async ([cid, mid]) => {
                const resp = await fetch('/api/v1/campaigns/' + cid + '/party/' + mid + '/sheet', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        partyMemberId: mid,
                        abilityScores: {str:15,dex:14,con:13,int:10,wis:12,cha:8},
                        classLevels: [{classSourceKey:'srd-2024_fighter',level:1,hitDieRolls:[]}],
                        proficiencies: {skills:[],expertise:[],tools:[],languages:[]},
                        xp: 0
                    })
                });
                if (!resp.ok) throw new Error('Sheet creation failed: ' + await resp.text());
            }
        """, Arrays.asList(campaignId.toString(), memberId.toString()));

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/party/" + memberId + "/sheet");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Character Sheet");
        assertThat(dmPage.textContent("body")).contains("Fighter 1");
        assertThat(dmPage.textContent("body")).contains("Temp HP");
        assertThat(dmPage.textContent("body")).contains("Short Rest");

        dmPage.evaluate("""
            async ([cid, mid]) => {
                const resp = await fetch('/api/v1/campaigns/' + cid + '/party/' + mid + '/live-state', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        tempHp: 7, inspiration: false, exhaustion: 0,
                        deathSaveSuccesses: 0, deathSaveFailures: 0,
                        concentratingOn: null, conditionsJson: '[]'
                    })
                });
                if (!resp.ok) throw new Error('Live state update failed: ' + await resp.text());
            }
        """, Arrays.asList(campaignId.toString(), memberId.toString()));

        dmPage.locator("button:has-text('Short Rest')").first().click();
        dmPage.locator("#rest-preview-dialog h3").waitFor();
        assertThat(dmPage.textContent("body")).contains("Short Rest Preview");
        dmPage.locator("#rest-preview-dialog button:has-text('Cancel')").click();
    }

    @Test
    @Order(22)
    void rollableTableCreatesTreasureRollAndConfirmAddsToPartyStash() {
        startSession();
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/session");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        var equipment = equipmentItemRepository.findByNameContainingIgnoreCaseOrderByNameAsc("Backpack").getFirst();
        long initialStashRows = treasuryService.findPartyStash(campaignId).stream()
                .filter(item -> equipment.getId().equals(item.equipmentItemId()))
                .count();

        UUID tableId = createRollableTableThroughEditorApi(
                "browser_treasure", "Browser Treasure", "TREASURE", "Found a backpack", "1",
                "EQUIPMENT_ITEM", equipment.getId(), equipment.getName());
        linkTableToCurrentSceneThroughHttp(tableId, "Browser Treasure");

        dmPage.reload();
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        Locator linkedTable = dmPage.locator(".linked-table-row")
                .filter(new Locator.FilterOptions().setHasText("Browser Treasure"));
        linkedTable.waitFor();

        linkedTable.locator("button:has-text('Roll')").click();
        Locator rewardDraft = dmPage.locator(".draft-panel")
                .filter(new Locator.FilterOptions().setHasText("Review Reward Draft"));
        rewardDraft.waitFor();
        assertThat(dmPage.locator(".roll-result-text").filter(
                new Locator.FilterOptions().setHasText("Found a backpack")).isVisible()).isTrue();
        rewardDraft.locator("input[type='number']").fill("2");
        rewardDraft.locator("button:has-text('Discard')").click();
        dmPage.getByText("Draft discarded.", new Page.GetByTextOptions().setExact(true)).waitFor();

        var discardedLog = rollLogRepository.findTop20ByCampaignIdOrderByCreatedAtDesc(campaignId).getFirst();
        assertThat(discardedLog.getDraftType()).isEqualTo(TableDraftType.REWARD);
        assertThat(discardedLog.getDraftStatus()).isEqualTo(TableDraftStatus.DISCARDED);
        assertThat(treasuryService.findPartyStash(campaignId).stream()
                .filter(item -> equipment.getId().equals(item.equipmentItemId())).count())
                .isEqualTo(initialStashRows);

        dmPage.locator("#cockpitTablePicker").selectOption(tableId.toString());
        dmPage.locator(".roll-panel button:has-text('Roll')").click();
        rewardDraft.waitFor();
        rewardDraft.locator("input[type='number']").fill("3");
        rewardDraft.locator("button:has-text('Confirm')").click();
        dmPage.getByText("Draft confirmed.", new Page.GetByTextOptions().setExact(true)).waitFor();

        var confirmedLog = rollLogRepository.findTop20ByCampaignIdOrderByCreatedAtDesc(campaignId).getFirst();
        assertThat(confirmedLog.getDraftStatus()).isEqualTo(TableDraftStatus.CONFIRMED);
        assertThat(treasuryService.findPartyStash(campaignId))
                .anySatisfy(item -> {
                    assertThat(item.equipmentItemId()).isEqualTo(equipment.getId());
                    assertThat(item.quantity()).isEqualTo(3);
                });
    }

    @Test
    @Order(23)
    void rollableTableCreatesEncounterRollAndConfirmProducesPlannedEncounter() {
        startSession();

        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/session");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        var statBlock = statBlockRepository.findByNameContainingIgnoreCaseOrderByNameAsc("Goblin").getFirst();
        UUID tableId = createRollableTableThroughEditorApi(
                "browser_encounter", "Browser Encounter", "ENCOUNTER", "Goblins attack", "1",
                "STATBLOCK", statBlock.getId(), statBlock.getName());
        linkTableToCurrentSceneThroughHttp(tableId, "Browser Encounter");

        dmPage.reload();
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        dmPage.locator("#cockpitTablePicker").selectOption(tableId.toString());
        Locator panel = dmPage.locator(".roll-panel");
        panel.waitFor();
        panel.locator("input[type='number']").first().fill("1");
        panel.locator("button:has-text('Roll')").click();

        Locator encounterDraft = panel.locator(".draft-panel")
                .filter(new Locator.FilterOptions().setHasText("Review Encounter Draft"));
        encounterDraft.waitFor();
        encounterDraft.locator("input[type='text']").first().fill("Browser Encounter Encounter");
        encounterDraft.locator("input[type='number']").fill("2");
        encounterDraft.locator("button:has-text('Confirm')").click();
        dmPage.getByText("Draft confirmed.", new Page.GetByTextOptions().setExact(true)).waitFor();

        var confirmedLog = rollLogRepository.findTop20ByCampaignIdOrderByCreatedAtDesc(campaignId).getFirst();
        assertThat(confirmedLog.getDraftType()).isEqualTo(TableDraftType.ENCOUNTER);
        assertThat(confirmedLog.getDraftStatus()).isEqualTo(TableDraftStatus.CONFIRMED);

        var encounter = encounterRepository.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(candidate -> candidate.getName().equals("Browser Encounter Encounter"))
                .findFirst().orElseThrow();
        assertThat(encounter.getStatus()).isEqualTo(dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.PLANNED);
        assertThat(combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()))
                .hasSize(2)
                .allSatisfy(combatant -> assertThat(combatant.getStatBlock().getId()).isEqualTo(statBlock.getId()));
    }

    @Test
    @Order(24)
    void threatWorkflowProvesDmSurfacesAndPackageFidelity() throws Exception {
        startSession();
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/session");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);

        // 1. Create trap/hazard through editor request paths
        UUID trapId = createTrapThroughEditorApi("browser_spike", "Browser Spike Pit",
                "A pressure plate opens a pit of spikes.", 6, "2d10");
        UUID hazardId = createHazardThroughEditorApi("browser_gas", "Browser Poison Gas",
                "Green vapor seeps from vents.", "2d6");

        // 2. Verify detail / provenance / mechanics
        dmPage.navigate("http://localhost:" + port + "/library/traps/" + trapId);
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Browser Spike Pit");
        assertThat(dmPage.textContent("body")).contains("2d10");
        assertThat(dmPage.locator(".threat-mechanics-card").count()).isGreaterThan(0);
        assertThat(dmPage.locator(".provenance-panel, .provenance-summary").count())
                .as("custom threats show provenance")
                .isGreaterThan(0);

        dmPage.navigate("http://localhost:" + port + "/library/hazards/" + hazardId);
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Browser Poison Gas");

        // 3. Attach one trap to two scenes without duplication
        List<Scene> scenes = sceneRepo.findByChapterIdOrderBySortOrderAsc(chapterId);
        assertThat(scenes.size()).isGreaterThanOrEqualTo(2);
        Scene first = scenes.get(0);
        Scene second = scenes.get(1);
        attachThreatSection(first, trapId, "Browser Spike Pit", 0);
        attachThreatSection(second, trapId, "Browser Spike Pit (shared)", 0);
        assertThat(trapRepository.findAll()).filteredOn(t -> trapId.equals(t.getId())).hasSize(1);

        adventureService.setCurrentScene(campaignId, first.getId());

        // 4. Open story card in cockpit (mechanics card shows trigger/damage, not definition name)
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + campaignId + "/session");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Browser Spike Pit");
        Locator storyCard = dmPage.locator(".threat-mechanics-card")
                .filter(new Locator.FilterOptions().setHasText("2d10"));
        storyCard.first().waitFor();
        assertThat(storyCard.first().textContent()).contains("Pressure plate");

        // 5. Click detection/attack/damage Prefill and assert dice input from the button click alone.
        // Cockpit chrome (quest panel) can intercept Playwright hit-testing, so we fire the button's
        // own DOM click() — which runs its onclick dispatcher — and never re-dispatch dice-roller-prefill
        // as a fallback. A broken/missing button must fail the value assertion.
        dmPage.evaluate("""
                () => {
                  const card = Array.from(document.querySelectorAll('.threat-mechanics-card'))
                    .find(c => (c.textContent || '').includes('2d10'));
                  if (!card) throw new Error('Missing threat mechanics card');
                  const detectionStrong = Array.from(card.querySelectorAll('strong'))
                    .find(s => (s.textContent || '').trim() === 'Detection Check');
                  const detectionBtn = detectionStrong
                    && detectionStrong.parentElement
                    && detectionStrong.parentElement.querySelector('button');
                  if (!detectionBtn || !(detectionBtn.textContent || '').includes('Prefill 1d20')) {
                    throw new Error('Missing detection Prefill 1d20 button');
                  }
                  detectionBtn.click();
                }
                """);
        dmPage.locator(".dice-panel").waitFor();
        dmPage.waitForFunction("""
                () => {
                  const input = document.querySelector('.dice-panel input[type=text], .dice-panel input');
                  return input && input.value === '1d20';
                }
                """);
        assertThat((String) dmPage.evaluate(
                "document.querySelector('.dice-panel input[type=text], .dice-panel input')?.value || ''"))
                .isEqualTo("1d20");
        // Prefill must not auto-submit a roll result
        assertThat(dmPage.locator(".dice-panel").textContent()).doesNotContain("Total:");

        String attackExpr = (String) dmPage.evaluate("""
                () => {
                  const card = Array.from(document.querySelectorAll('.threat-mechanics-card'))
                    .find(c => (c.textContent || '').includes('2d10'));
                  const btn = card && Array.from(card.querySelectorAll('button'))
                    .find(b => (b.textContent || '').includes('Prefill attack'));
                  if (!btn) throw new Error('Missing Prefill attack button');
                  const expr = btn.dataset.prefillExpr;
                  btn.click();
                  return expr;
                }
                """);
        assertThat(attackExpr).isEqualTo("1d20+6");
        dmPage.waitForFunction("""
                () => {
                  const input = document.querySelector('.dice-panel input[type=text], .dice-panel input');
                  return input && input.value === '1d20+6';
                }
                """);
        assertThat((String) dmPage.evaluate(
                "document.querySelector('.dice-panel input[type=text], .dice-panel input')?.value || ''"))
                .isEqualTo("1d20+6");
        assertThat(dmPage.locator(".dice-panel").textContent()).doesNotContain("Total:");

        String damageExpr = (String) dmPage.evaluate("""
                () => {
                  const card = Array.from(document.querySelectorAll('.threat-mechanics-card'))
                    .find(c => (c.textContent || '').includes('2d10'));
                  const btn = card && Array.from(card.querySelectorAll('button'))
                    .find(b => (b.textContent || '').includes('Prefill damage'));
                  if (!btn) throw new Error('Missing Prefill damage button');
                  const expr = btn.dataset.prefillExpr;
                  btn.click();
                  return expr;
                }
                """);
        assertThat(damageExpr).isEqualTo("2d10");
        dmPage.waitForFunction("""
                () => {
                  const input = document.querySelector('.dice-panel input[type=text], .dice-panel input');
                  return input && input.value === '2d10';
                }
                """);
        assertThat((String) dmPage.evaluate(
                "document.querySelector('.dice-panel input[type=text], .dice-panel input')?.value || ''"))
                .isEqualTo("2d10");
        dmPage.keyboard().press("Escape");

        // 6. Add/activate encounter threat and see tracker card
        // End any leftover active encounters from earlier ordered smoke steps.
        encounterRepository.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(e -> e.getStatus() == dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.ACTIVE)
                .forEach(e -> encounterService.endEncounter(e.getId()));

        UUID threatEncounterId = encounterService.create(campaignId,
                new EncounterService.CreateRequest("Browser Threat Encounter", mapId)).id();
        encounterService.addCombatant(threatEncounterId,
                new EncounterService.CombatantCreateRequest(
                        "Browser Fighter", 30, "PC", null, null, null));
        var threatCombatant = encounterService.addThreatCombatant(threatEncounterId,
                new EncounterService.ThreatCombatantRequest(
                        dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind.TRAP,
                        trapId, null, 25, null));
        // Ensure PC goes after the trap so the trap is active on activation.
        UUID fighterIdEarly = encounterService.getCombatants(threatEncounterId).stream()
                .filter(c -> "Browser Fighter".equals(c.name()))
                .findFirst().orElseThrow().id();
        encounterService.setInitiative(fighterIdEarly, 5);
        encounterService.activate(threatEncounterId);
        // Activation leaves activeTurnIndex at -1 until the first next-turn.
        encounterService.nextTurn(threatEncounterId);

        dmPage.reload();
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        dmPage.waitForFunction("""
                () => {
                  const name = document.querySelector('[data-active-threat-card] strong, .active-threat-card strong');
                  const el = document.querySelector('[data-active-threat-card]');
                  if (!el || !name) return false;
                  const visible = getComputedStyle(el).display !== 'none'
                    && getComputedStyle(el).visibility !== 'hidden';
                  return visible && (name.textContent || '').includes('Browser Spike Pit');
                }
                """);
        assertThat(dmPage.locator("[data-active-threat-card]").textContent())
                .contains("Browser Spike Pit");

        // 7. Use existing damage/condition controls and verify log
        UUID fighterId = fighterIdEarly;
        // Select fighter via API damage path used by tracker, then verify log
        dmPage.evaluate("""
            async ([combatantId]) => {
              const response = await fetch('/api/v1/combatants/' + combatantId + '/damage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: -5 })
              });
              if (!response.ok) throw new Error('Damage failed: ' + await response.text());
              const cond = await fetch('/api/v1/combatants/' + combatantId + '/conditions', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ sourceKey: 'poisoned', durationRounds: 1 })
              });
              if (!cond.ok) throw new Error('Condition failed: ' + await cond.text());
            }
        """, List.of(fighterId.toString()));
        assertThat(combatLogEntryRepository.findByEncounterIdOrderBySequenceAsc(threatEncounterId))
                .isNotEmpty();
        assertThat(encounterService.getCombatant(fighterId).currentHp()).isEqualTo(25);

        // 8. Create DM pin and verify player marker absence
        Object pinId = dmPage.evaluate("""
            async ([mapId, trapId]) => {
              const response = await fetch('/api/v1/maps/' + mapId + '/pins', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                  key: 'browser-spike-pin',
                  threatKind: 'TRAP',
                  threatId: trapId,
                  x: 96,
                  y: 144,
                  label: 'Browser Spike Pin',
                  sortOrder: 0
                })
              });
              if (!response.ok) throw new Error('Pin create failed: ' + await response.text());
              return (await response.json()).id;
            }
        """, Arrays.asList(mapId.toString(), trapId.toString()));
        assertThat(pinId).isNotNull();
        assertThat(mapThreatPinRepository.findByMapIdOrderBySortOrderAsc(mapId))
                .anySatisfy(p -> assertThat(p.getPinKey()).isEqualTo("browser-spike-pin"));

        BrowserContext playerContext = browser.newContext();
        Page playerPage = guardedPage(playerContext);
        playerPage.navigate("http://localhost:" + port + "/player");
        playerPage.waitForLoadState(LoadState.NETWORKIDLE);
        String playerHtml = playerPage.content();
        // DM pin markers/labels must never reach the player page or table state.
        assertThat(playerHtml)
                .doesNotContain("browser-spike-pin")
                .doesNotContain("Browser Spike Pin");
        String tableState = (String) dmPage.evaluate("""
            async () => {
              const r = await fetch('/api/v1/table/state');
              return await r.text();
            }
        """);
        assertThat(tableState)
                .doesNotContain("browser-spike-pin")
                .doesNotContain("Browser Spike Pin")
                .doesNotContain(String.valueOf(pinId))
                .doesNotContain("\"TRAP\"");
        playerContext.close();

        // 9. Export/import and reopen refs/cards
        // Exclude combat log (planned-encounter log rows can carry round 0). Prior smoke steps
        // may leave table treasure refs that re-embed SRD equipment under a conflicting
        // sourceKey; strip rollable tables and custom equipment rows before packaging.
        var artifact = exportCoordinator.export(campaignId,
                new CampaignExportOptions(false, false));
        CampaignManifestV2 exportedManifest = stripTableAndConflictingEquipment(artifact.manifest());
        assertThat(exportedManifest.traps())
                .anySatisfy(t -> assertThat(t.name()).isEqualTo("Browser Spike Pit"));
        assertThat(exportedManifest.hazards())
                .anySatisfy(h -> assertThat(h.name()).isEqualTo("Browser Poison Gas"));
        assertThat(exportedManifest.maps())
                .anySatisfy(m -> assertThat(m.threatPins()).isNotEmpty());

        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        var originalWrite = artifact.writeRequest();
        new CampaignPackageWriter().write(
                new CampaignPackageWriteRequest(
                        "threat-rt.dmcampaign", exportedManifest, originalWrite.assetSources()),
                exported);
        byte[] packageBytes = exported.toByteArray();

        String redirect = (String) dmPage.evaluate("""
            async ([baseUrl, bodyB64, zipped]) => {
                const binary = Uint8Array.from(atob(bodyB64), c => c.charCodeAt(0));
                const prv = await fetch(baseUrl + '/campaigns/package-imports/previews', {
                    method: 'POST',
                    headers: {
                        'Content-Type': zipped
                            ? 'application/vnd.dmhelper.campaign+zip'
                            : 'application/json',
                        'X-DMHelper-Filename': zipped
                            ? 'threat-rt.dmcampaign'
                            : 'threat-rt.dmcampaign.json'
                    },
                    body: binary
                });
                if (!prv.ok) throw new Error('Preview failed: ' + await prv.text());
                const preview = await prv.json();
                if (preview.status === 'BLOCKED') {
                    throw new Error('Import blocked: ' + JSON.stringify(preview.problems));
                }
                const conf = await fetch(baseUrl + '/campaigns/package-imports/' + preview.previewId
                    + '/confirm?acceptWarnings=true', { method: 'POST' });
                if (!conf.ok) throw new Error('Confirm failed: ' + await conf.text());
                return conf.headers.get('Location');
            }
        """, Arrays.asList("http://localhost:" + port,
                Base64.getEncoder().encodeToString(packageBytes),
                !originalWrite.assetSources().isEmpty()));
        assertThat(redirect).isNotNull();
        UUID restoredId = UUID.fromString(redirect.replaceAll("/campaigns/", ""));

        var restoredTraps = trapRepository.findVisibleByCampaignId(restoredId).stream()
                .filter(t -> t.getCampaign() != null && restoredId.equals(t.getCampaign().getId()))
                .toList();
        assertThat(restoredTraps).anySatisfy(t -> assertThat(t.getName()).isEqualTo("Browser Spike Pit"));
        UUID restoredTrapId = restoredTraps.stream()
                .filter(t -> "Browser Spike Pit".equals(t.getName()))
                .findFirst().orElseThrow().getId();

        dmPage.navigate("http://localhost:" + port + "/library/traps/" + restoredTrapId);
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Browser Spike Pit");
        assertThat(dmPage.locator(".threat-mechanics-card").count()).isGreaterThan(0);

        // Restored scene sections still reference the re-mapped trap (dual-scene attach)
        var restoredSections = sceneSectionRepository.findByThreatKindAndThreatId(
                dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind.TRAP, restoredTrapId);
        assertThat(restoredSections)
                .as("import remaps scene threat sections onto restored trap")
                .hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy(s -> assertThat(s.getThreatId()).isEqualTo(restoredTrapId));
        assertThat(restoredSections.stream().map(s -> s.getLabel()).toList())
                .anyMatch(l -> l != null && l.contains("Browser Spike Pit"));

        // Restored map pin still points at the re-mapped trap
        var restoredPins = mapThreatPinRepository.findByThreatKindAndThreatId(
                dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind.TRAP, restoredTrapId);
        assertThat(restoredPins)
                .as("import remaps DM map pins onto restored trap")
                .isNotEmpty()
                .anySatisfy(p -> {
                    assertThat(p.getPinKey()).isEqualTo("browser-spike-pin");
                    assertThat(p.getLabel()).isEqualTo("Browser Spike Pin");
                    assertThat(p.getThreatId()).isEqualTo(restoredTrapId);
                });
        UUID restoredMapId = mapRepo.findByCampaignIdOrderBySortOrderAsc(restoredId).stream()
                .map(m -> m.getId())
                .filter(id -> mapThreatPinRepository.findByMapIdOrderBySortOrderAsc(id).stream()
                        .anyMatch(p -> "browser-spike-pin".equals(p.getPinKey())))
                .findFirst()
                .orElseThrow();
        Object pinApiBody = dmPage.evaluate("""
            async ([mapId]) => {
              const r = await fetch('/api/v1/maps/' + mapId + '/pins');
              if (!r.ok) throw new Error('Pin list failed: ' + await r.text());
              return await r.text();
            }
        """, List.of(restoredMapId.toString()));
        assertThat((String) pinApiBody)
                .contains("browser-spike-pin")
                .contains(restoredTrapId.toString());

        // Restored encounter combatant keeps threatRef → remapped trap
        var restoredThreatCombatants = combatantRepository.findByThreatKindAndThreatId(
                dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind.TRAP, restoredTrapId);
        assertThat(restoredThreatCombatants)
                .as("import remaps encounter threat combatants onto restored trap")
                .isNotEmpty()
                .allSatisfy(c -> {
                    assertThat(c.getThreatKind())
                            .isEqualTo(dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind.TRAP);
                    assertThat(c.getThreatId()).isEqualTo(restoredTrapId);
                });
        assertThat(encounterRepository.findByCampaignIdOrderByNameAsc(restoredId))
                .anySatisfy(e -> assertThat(e.getName()).isEqualTo("Browser Threat Encounter"));

        // Reopen cockpit story card on restored campaign (scene sections + mechanics card)
        var restoredAdventure = adventureRepo.findByCampaignIdOrderBySortOrderAsc(restoredId).getFirst();
        var restoredChapter = chapterRepo.findByAdventureIdOrderBySortOrderAsc(restoredAdventure.getId()).getFirst();
        var restoredScenes = sceneRepo.findByChapterIdOrderBySortOrderAsc(restoredChapter.getId());
        assertThat(restoredScenes).isNotEmpty();
        adventureService.setCurrentScene(restoredId, restoredScenes.getFirst().getId());
        dmPage.navigate("http://localhost:" + port + "/campaigns/" + restoredId + "/session");
        dmPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(dmPage.textContent("body")).contains("Browser Spike Pit");
        Locator restoredStoryCard = dmPage.locator(".threat-mechanics-card")
                .filter(new Locator.FilterOptions().setHasText("2d10"));
        restoredStoryCard.first().waitFor();
        assertThat(restoredStoryCard.first().textContent()).contains("Pressure plate");

        assertThat(threatCombatant.threatId()).isEqualTo(trapId);
    }

    private static CampaignManifestV2 stripTableAndConflictingEquipment(CampaignManifestV2 source) {
        // Drop scene links that targeted rollable tables so table removal stays coherent.
        var adventures = source.adventures().stream()
                .map(adv -> new CampaignManifestV2.AdventureDto(
                        adv.key(), adv.name(), adv.description(), adv.sourceAttribution(),
                        adv.sortOrder(),
                        adv.chapters().stream().map(ch -> new CampaignManifestV2.ChapterDto(
                                ch.key(), ch.title(), ch.intro(), ch.sortOrder(),
                                ch.scenes().stream().map(sc -> new CampaignManifestV2.SceneDto(
                                        sc.key(), sc.title(), sc.body(), sc.status(), sc.sortOrder(),
                                        sc.mapRef(), sc.pin(), sc.encounterRef(),
                                        sc.statblockRefs(), sc.handoutRefs(),
                                        sc.summary(), sc.sourceLocator(), sc.tags(), sc.mapRegionKey(),
                                        sc.sections(), sc.checks(), sc.participants(),
                                        sc.transitions(),
                                        sc.links() == null ? List.of() : sc.links().stream()
                                                .filter(link -> link.targetRef() == null
                                                        || link.targetRef().type() == null
                                                        || !"ROLLABLE_TABLE".equals(link.targetRef().type().name()))
                                                .toList()
                                )).toList()
                        )).toList(),
                        adv.createdAt()))
                .toList();
        // Also drop assignments that referenced stripped equipment.
        var assignments = source.assignments() == null ? List.<CampaignManifestV2.AssignmentDto>of()
                : source.assignments().stream()
                .filter(a -> a.equipmentItemRef() == null)
                .toList();
        return new CampaignManifestV2(
                source.formatVersion(), source.metadata(), source.campaign(), source.assets(),
                source.party(), source.customStatBlocks(), source.customSpells(),
                source.customConditions(), source.customRules(),
                List.of(), // customEquipment — avoid sourceKey collisions with SRD seed
                source.customMagicItems(), source.customClasses(), source.customSpecies(),
                source.customBackgrounds(), source.customFeats(),
                source.handouts(), source.maps(), source.encounters(), source.notes(),
                source.quickNotes(), assignments, source.ledgerEntries(),
                source.timelineEvents(), adventures, source.session(), source.diceRolls(),
                source.quests(), source.annotations(), source.worldNpcs(), source.worldLocations(),
                source.factions(), source.worldRelationships(), source.factionClocks(),
                List.of(), // rollableTables
                source.traps(), source.hazards());
    }

    private UUID createTrapThroughEditorApi(String sourceKey, String name,
                                            String description, int attackBonus,
                                            String damageExpression) {
        Object id = dmPage.evaluate("""
            async ([campaignId, sourceKey, name, description, attackBonus, damageExpression]) => {
                const response = await fetch('/api/v1/traps?campaignId=' + campaignId, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        sourceKey,
                        name,
                        description,
                        severity: 'DANGEROUS',
                        minLevel: 1,
                        maxLevel: 5,
                        triggerDescription: 'Pressure plate',
                        triggerAreaHint: '10-ft square',
                        detectionPassiveThreshold: 15,
                        detectionCheck: { mode: 'CHECK', ability: 'WIS', skill: 'Perception', dc: 15 },
                        disarmMethods: [{
                            key: 'jam-cover',
                            label: 'Jam cover',
                            ability: 'DEX',
                            skill: null,
                            tool: "thieves' tools",
                            dc: 14,
                            failureConsequence: 'Triggers',
                            sortOrder: 0
                        }],
                        attackBonus,
                        save: null,
                        damageExpression,
                        damageTypes: ['PIERCING'],
                        additionalEffect: 'Knocked prone',
                        resetMode: 'MANUAL',
                        resetTiming: null,
                        statBlockId: null,
                        countermeasureNotes: 'Wedge the cover',
                        references: []
                    })
                });
                if (!response.ok) throw new Error('Trap creation failed: ' + await response.text());
                return (await response.json()).id;
            }
        """, Arrays.asList(campaignId.toString(), sourceKey, name, description,
                attackBonus, damageExpression));
        return UUID.fromString((String) id);
    }

    private UUID createHazardThroughEditorApi(String sourceKey, String name,
                                              String description, String damageExpression) {
        Object id = dmPage.evaluate("""
            async ([campaignId, sourceKey, name, description, damageExpression]) => {
                const response = await fetch('/api/v1/hazards?campaignId=' + campaignId, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        sourceKey,
                        name,
                        description,
                        severity: 'SETBACK',
                        minLevel: 1,
                        maxLevel: 8,
                        exposureMode: 'ON_ENTER',
                        exposureText: 'Entering the cloud exposes you',
                        areaHint: '15-ft radius',
                        check: { mode: 'SAVE', ability: 'CON', skill: null, dc: 13 },
                        damageExpression,
                        damageTypes: ['POISON'],
                        escalationText: 'Spreads 5 feet',
                        endingConditions: 'Disperses after 1 minute',
                        references: []
                    })
                });
                if (!response.ok) throw new Error('Hazard creation failed: ' + await response.text());
                return (await response.json()).id;
            }
        """, Arrays.asList(campaignId.toString(), sourceKey, name, description, damageExpression));
        return UUID.fromString((String) id);
    }

    private void attachThreatSection(Scene scene, UUID trapId, String label, int sortOrder) {
        UUID adventureId = adventureRepo.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst().getId();
        String path = "/campaigns/" + campaignId
                + "/adventures/" + adventureId
                + "/chapters/" + chapterId
                + "/scenes/" + scene.getId() + "/sections";
        dmPage.evaluate("""
            async ([path, trapId, label, sortOrder]) => {
                const body = new URLSearchParams({
                    kind: 'TRAP',
                    label,
                    body: 'Attached by browser acceptance',
                    sortOrder: String(sortOrder),
                    threatId: trapId
                });
                const response = await fetch(path, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body
                });
                if (!response.ok) throw new Error('Scene threat section failed: ' + await response.text());
            }
        """, Arrays.asList(path, trapId.toString(), label, sortOrder));
    }

    private UUID createRollableTableThroughEditorApi(
            String sourceKey, String name, String category, String resultText,
            String quantityExpression, String referenceType, UUID referenceId, String referenceLabel) {
        Object id = dmPage.evaluate("""
            async ([campaignId, sourceKey, name, category, resultText, quantityExpression,
                    referenceType, referenceId, referenceLabel]) => {
                const response = await fetch('/api/v1/rollable-tables?campaignId=' + campaignId, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        sourceKey,
                        name,
                        description: 'Created by the real browser acceptance flow',
                        addressMode: 'RANGE',
                        rollExpression: '1d1',
                        category,
                        tags: ['browser-acceptance'],
                        entries: [{
                            key: sourceKey + '-result',
                            rangeStart: 1,
                            rangeEnd: 1,
                            weight: null,
                            resultText,
                            quantityExpression,
                            references: [{
                                scope: 'ENTITY',
                                targetType: referenceType,
                                targetId: referenceId,
                                catalogRuleset: null,
                                catalogSourceKey: null,
                                displayText: referenceLabel
                            }]
                        }]
                    })
                });
                if (!response.ok) throw new Error('Table creation failed: ' + await response.text());
                return (await response.json()).id;
            }
        """, Arrays.asList(campaignId.toString(), sourceKey, name, category, resultText,
                quantityExpression, referenceType, referenceId.toString(), referenceLabel));
        return UUID.fromString((String) id);
    }

    private void linkTableToCurrentSceneThroughHttp(UUID tableId, String displayText) {
        Scene scene = adventureService.getCurrentScene(campaignId).orElseThrow();
        UUID adventureId = adventureRepo.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst().getId();
        String path = "/campaigns/" + campaignId
                + "/adventures/" + adventureId
                + "/chapters/" + chapterId
                + "/scenes/" + scene.getId() + "/links";
        dmPage.evaluate("""
            async ([path, tableId, displayText]) => {
                const body = new URLSearchParams({
                    role: 'RANDOM_ENCOUNTERS',
                    targetScope: 'PACKAGE',
                    targetType: 'ROLLABLE_TABLE',
                    targetId: tableId,
                    displayText,
                    sortOrder: '1'
                });
                const response = await fetch(path, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body
                });
                if (!response.ok) throw new Error('Scene table link failed: ' + await response.text());
            }
        """, Arrays.asList(path, tableId.toString(), displayText));
    }
}
