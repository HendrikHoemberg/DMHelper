package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 11.3 — the representative release rehearsal, executed end to end
 * without leaving DMHelper. Each @Order corresponds to one numbered rehearsal step; the
 * seven gate-fail conditions are asserted at the point where each becomes observable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReleaseRehearsalTest {

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private final BrowserFailureCollector failures = new BrowserFailureCollector();

    private ReleaseRehearsalFixture.Seeded seeded;
    private String base;
    private String sceneTitleVisitedDuringRehearsal;
    private String defeatedCombatantName;
    private String encounterName;

    @BeforeAll
    void launch() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        failures.clear();
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);
        page.setViewportSize(1366, 768);
        base = "http://localhost:" + port;
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private void openCockpit() {
        page.navigate(base + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
    }

    private void assertVisibleWithoutScrolling(String description, String selector) {
        Locator locator = page.locator(selector).first();
        locator.waitFor();
        BoundingBox box = locator.boundingBox();
        assertThat(box).as("%s is present", description).isNotNull();
        assertThat(box.y).as("%s starts inside the viewport", description).isGreaterThanOrEqualTo(0);
        assertThat(box.y + box.height).as("%s ends inside the viewport", description)
                .isLessThanOrEqualTo(768.0);
        assertThat((int) page.evaluate("() => window.scrollY"))
                .as("%s required no scrolling", description).isZero();
    }

    @Test @Order(1)
    void step1_readinessReportIsInspectedAndClear() {
        page.navigate(base + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator(".readiness-panel").count()).isEqualTo(1);
        assertThat(page.locator(".readiness-panel.is-ready").count()).isEqualTo(1);
        assertThat(page.locator(".readiness-item--blocker").count()).isZero();
    }

    @Test @Order(2)
    void step2_sessionStartsAtTheSelectedScene() {
        page.navigate(base + "/campaigns/" + seeded.campaignId());
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("a[href$='/session'], button[data-run-session]").first().click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
        assertThat(page.url()).endsWith("/session");
        assertVisibleWithoutScrolling("current scene", "[data-runtime-module='story'] [data-current-scene]");
    }

    @Test @Order(3)
    void step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit() {
        openCockpit();
        String before = page.textContent("[data-runtime-module='story'] [data-current-scene]");
        page.locator("[data-runtime-module='story'] [data-scene-transition]").first().click();
        page.waitForFunction("previous => document.querySelector(\"[data-runtime-module='story'] [data-current-scene]\")?.textContent !== previous", before);
        sceneTitleVisitedDuringRehearsal = page.textContent("[data-runtime-module='story'] [data-current-scene]").trim();
        assertThat(page.url()).endsWith("/session");
        assertThat(page.locator("main[data-surface='run']").count()).isEqualTo(1);
    }

    @Test @Order(4)
    void step4_theEncounterIsCreatedFromTheSceneInAtMostTwoActions() {
        openCockpit();
        int actions = 0;
        page.locator("[data-runtime-module='story'] [data-seed-scene-encounter]").first().click();
        actions++;
        page.waitForSelector("[data-runtime-module='encounter'] [data-initiative-setup]");
        encounterName = page.textContent("[data-runtime-module='encounter'] [data-encounter-name]").trim();
        assertThat(actions).isLessThanOrEqualTo(2);
        assertVisibleWithoutScrolling("encounter identity", "[data-runtime-module='encounter'] [data-encounter-name]");
    }

    @Test @Order(5)
    void step5_initiativeDamageConditionsDefeatAndTurnsResolve() {
        openCockpit();
        Locator setup = page.locator("[data-runtime-module='encounter'] [data-initiative-setup]");
        setup.waitFor();
        List<Locator> inputs = setup.locator("input[data-initiative-input]").all();
        for (int i = 0; i < inputs.size(); i++) inputs.get(i).fill(String.valueOf(20 - i));
        setup.locator("[data-roll-unset-initiative]").click();
        page.waitForFunction("() => !document.querySelector('[data-initiative-setup] .initiative-setup__row--unset')");
        setup.locator("button[data-start-combat]").click();
        page.waitForSelector("[data-running-turn-controls]");
        assertVisibleWithoutScrolling("current turn", "[data-running-turn-controls]");
        Locator firstRow = page.locator(".combatant-row").first();
        defeatedCombatantName = firstRow.locator(".combatant-name").textContent().trim();
        String hpBefore = firstRow.locator(".combatant-hp").textContent();
        firstRow.locator(".hp-delta-input").fill("-999");
        firstRow.locator(".hp-delta-input").press("Enter");
        page.waitForFunction("previous => document.querySelector('.combatant-row .combatant-hp')?.textContent !== previous", hpBefore);
        assertThat(page.locator(".combatant-row.defeated").count()).isGreaterThan(0);
        page.locator("[data-action='next-turn']").click();
        page.locator("[data-action='next-turn']").click();
    }

    @Test @Order(6)
    void step6_statblocksAndRulesAreConsultedInsideDmhelper() {
        openCockpit();
        page.locator(".combatant-row").first().click();
        assertThat(page.locator(".detail-focused-statblock, .statblock-render").first().isVisible()).isTrue();
        page.locator("[data-runtime-module='reference'] input[type='search']").first().fill("grapple");
        page.waitForSelector("[data-runtime-module='reference'] [data-reference-result]");
        assertThat(page.locator("[data-runtime-module='reference'] [data-reference-result]").count()).isGreaterThan(0);
        assertThat(page.url()).endsWith("/session");
    }

    @Test @Order(7)
    void step7_notesAreCapturedAndThePlanIsUpdated() {
        openCockpit();
        page.locator("[data-runtime-module='quick-notes'] textarea, [data-runtime-module='quick-notes'] input[type='text']").first().fill("The warden fled through the sluice gate.");
        page.locator("[data-runtime-module='quick-notes'] button[type='submit']").first().click();
        page.waitForSelector("[data-runtime-module='quick-notes'] .quicknote-row");
        assertVisibleWithoutScrolling("save status", "#runtimeStatus [data-status-save]");
        page.waitForFunction("() => ['saved', 'idle'].includes(document.querySelector('#runtimeStatus [data-status-save]').dataset.state)");
    }

    @Test @Order(8)
    void step8_aReviewedPlayerSafeAssetIsPresentedAndTheDisplayAgrees() {
        openCockpit();
        Locator presentation = page.locator("[data-runtime-module='presentation']");
        presentation.locator("[data-present-handout='" + seeded.playerSafeHandoutId() + "']").click();
        page.waitForFunction("() => document.querySelector('[data-presentation-mode]')?.dataset.presentationMode === 'HANDOUT'");
        assertVisibleWithoutScrolling("presentation state", "[data-presentation-mode]");
        Page playerPage = context.newPage();
        failures.attach(playerPage);
        playerPage.navigate(base + "/player");
        playerPage.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(playerPage.locator("[data-screen-sensitive]").count()).isZero();
        assertThat(playerPage.content()).doesNotContain(String.valueOf(seeded.dmSourceHandoutId()));
        playerPage.close();
    }

    @Test @Order(9)
    void step9_theEncounterAndSessionAreCompleted() {
        openCockpit();
        page.locator("[data-runtime-module='encounter'] [data-end-encounter]").click();
        page.waitForFunction("() => !document.querySelector('[data-running-turn-controls]') || document.querySelector('[data-running-turn-controls]').hidden");
        page.locator("button[x-ref='sessionButton']").click();
        Locator lifecycle = page.locator("#sessionLifecycleDialog");
        lifecycle.waitFor();
        BoundingBox box = lifecycle.boundingBox();
        assertThat(box.y + box.height).isLessThanOrEqualTo(768.0);
        lifecycle.locator("button[data-complete-session]").click();
        page.waitForFunction("() => !document.querySelector('#sessionLifecycleDialog[open]')");
    }

    @Test @Order(10)
    void step10_theGeneratedLogAgreesWithWhatHappened() {
        page.navigate(base + "/campaigns/" + seeded.campaignId() + "/notes");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator(".note-body, [data-session-log]").first().waitFor();
        String log = page.textContent("body");
        assertThat(log).containsPattern("\\d{1,2}:\\d{2}").containsAnyOf("CET", "CEST", "Europe/Berlin");
        assertThat(log).contains(sceneTitleVisitedDuringRehearsal);
        assertThat(log).contains(defeatedCombatantName);
        assertThat(log).contains("sluice gate");
        assertThat(log).contains(encounterName);
    }
}
