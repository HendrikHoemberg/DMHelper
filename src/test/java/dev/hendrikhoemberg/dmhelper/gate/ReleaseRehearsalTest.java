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

/** Spec 2026-07-22 section 11.3 — the representative release rehearsal for both fixture shapes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
class ReleaseRehearsalTest {

    static abstract class RehearsalSteps {

        protected abstract ReleaseRehearsalFixture.Shape shape();

        @LocalServerPort protected int port;
        @Autowired protected ReleaseRehearsalFixture fixture;

        protected static Playwright playwright;
        protected static Browser browser;
        protected BrowserContext context;
        protected Page page;
        protected final BrowserFailureCollector failures = new BrowserFailureCollector();

        protected ReleaseRehearsalFixture.Seeded seeded;
        protected String base;
        protected String sceneTitleVisitedDuringRehearsal;
        protected String defeatedCombatantName;
        protected String encounterName;
        protected String planTitle;

        @BeforeAll
        void launch() throws Exception {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            seeded = fixture.seed(shape());
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
            page.locator("button[x-ref='sessionButton']").click();
            Locator lifecycle = page.locator("#sessionLifecycleDialog");
            lifecycle.waitFor();
            lifecycle.locator("button").filter(new Locator.FilterOptions().setHasText("Start")).first().click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForFunction("window.cockpitLayout?.mounted === true");
            assertThat(page.locator("[data-session-status]").getAttribute("data-session-status"))
                    .as("the cockpit reports an active session").isEqualTo("RUNNING");
            assertThat(page.url()).endsWith("/session");
            assertVisibleWithoutScrolling("current scene", "[data-runtime-module='story'] [data-current-scene]");
        }

        @Test @Order(3)
        void step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit() {
            openCockpit();
            String before = page.textContent("[data-runtime-module='story'] [data-current-scene]");
            Locator transitions = page.locator("[data-runtime-module='story'] [data-scene-transition]");
            transitions.nth(shape() == ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS ? 1 : 0).click();
            page.waitForFunction("previous => document.querySelector(\"[data-runtime-module='story'] [data-current-scene]\")?.textContent !== previous", before);
            sceneTitleVisitedDuringRehearsal = page.textContent("[data-runtime-module='story'] [data-current-scene]").trim();
            if (shape() == ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS) {
                String branch = sceneTitleVisitedDuringRehearsal;
                page.locator("[data-runtime-module='story'] [data-scene-transition]").first().click();
                page.waitForFunction("previous => document.querySelector(\"[data-runtime-module='story'] [data-current-scene]\")?.textContent !== previous", branch);
                assertThat(page.textContent("[data-runtime-module='story'] [data-current-scene]").trim())
                        .as("the second branch reaches a different scene").isNotEqualTo(branch);
            }
            assertThat(page.url()).endsWith("/session");
            assertThat(page.locator("main[data-surface='run']").count()).isEqualTo(1);
        }

        @Test @Order(4)
        void step4_theEncounterIsCreatedFromTheSceneInAtMostTwoActions() {
            openCockpit();
            int actions = 0;
            Locator seedButton = page.locator("[data-runtime-module='story'] [data-seed-scene-encounter]");
            if (seedButton.count() > 0) {
                seedButton.first().click();
                actions++;
                page.waitForSelector("[data-runtime-module='encounter'] [data-initiative-setup]");
            } else {
                page.locator("[data-runtime-module='encounter'] [data-encounter-name]").waitFor();
            }
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
            if (shape() == ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS) {
                assertThat(page.locator("[data-runtime-module='encounter'] [data-next-wave]").count())
                        .as("a wave-based encounter offers its next wave from the cockpit")
                        .isGreaterThan(0);
            }
            page.locator(".combatant-row").first().click();
            Locator condition = page.locator(".detail-conditions input[type='checkbox']").first();
            condition.waitFor();
            Response conditionResponse = page.waitForResponse(
                    response -> response.url().contains("/api/v1/combatants/")
                            && response.url().endsWith("/conditions")
                            && response.request().method().equals("PUT"), condition::check);
            assertThat(conditionResponse.status()).as("the condition update is accepted by the server").isBetween(200, 299);
            page.waitForFunction("() => document.querySelector('.detail-conditions input[type=checkbox]')?.checked === true");
            assertThat(condition.isChecked()).as("a condition action is reflected in the tracker").isTrue();
            page.reload();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForFunction("window.cockpitLayout?.mounted === true");
            page.locator(".combatant-row").first().click();
            Locator persistedCondition = page.locator(".detail-conditions input[type='checkbox']").first();
            persistedCondition.waitFor();
            page.waitForFunction("() => document.querySelector('.detail-conditions input[type=checkbox]')?.checked === true");
            assertThat(persistedCondition.isChecked()).as("the condition remains checked after reload").isTrue();
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
            planTitle = "Release rehearsal plan";
            page.evaluate("""
                    async ({campaignId, title}) => {
                        const list = await fetch(`/api/v1/campaigns/${campaignId}/quicknotes?targetType=CAMPAIGN&targetId=${campaignId}`);
                        const notes = await list.json();
                        const note = notes.at(-1);
                        if (!note) throw new Error('The rehearsal quick note was not returned by the runtime.');
                        const params = new URLSearchParams({title, type: 'SESSION_PLAN'});
                        const promoted = await fetch(`/api/v1/campaigns/${campaignId}/quicknotes/${note.id}/promote?${params}`, {method: 'POST'});
                        if (!promoted.ok) throw new Error(`Session plan promotion failed: ${promoted.status}`);
                        return await promoted.json();
                    }
                    """, java.util.Map.of("campaignId", seeded.campaignId().toString(), "title", planTitle));
            page.reload();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForFunction("window.cockpitLayout?.mounted === true");
            assertThat(page.locator("[data-runtime-module='session-plan'] h3").textContent().trim()).as("the session plan reflects the promoted rehearsal update").isEqualTo(planTitle);
            assertVisibleWithoutScrolling("save status", "#runtimeStatus [data-status-save]");
            page.waitForFunction("() => ['saved', 'idle'].includes(document.querySelector('#runtimeStatus [data-status-save]').dataset.state)");
        }

        @Test @Order(8)
        void step8_aReviewedPlayerSafeAssetIsPresentedAndTheDisplayAgrees() {
            openCockpit();
            Locator presentation = page.locator("[data-runtime-module='presentation']");
            presentation.locator("#presentationHandoutPicker").selectOption(seeded.playerSafeHandoutId().toString());
            page.locator("#presentationPreview").waitFor();
            page.locator("#previewContainer img").waitFor();
            page.locator("#presentationPreview button").filter(new Locator.FilterOptions().setHasText("Present to table")).click();
            page.waitForFunction("() => document.querySelector('[data-presentation-mode]')?.dataset.presentationMode === 'HANDOUT'");
            assertThat(page.locator("#presentationPreview").getAttribute("style")).doesNotContain("display: block");
            assertVisibleWithoutScrolling("presentation state", "[data-presentation-mode]");
            Page playerPage = context.newPage();
            failures.attach(playerPage);
            playerPage.navigate(base + "/player");
            playerPage.waitForLoadState(LoadState.NETWORKIDLE);
            playerPage.locator(".pv-handout img").waitFor();
            assertThat(playerPage.locator(".pv-handout img").getAttribute("src")).isEqualTo("/player/files/" + seeded.playerSafeHandoutId());
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
            page.locator("button").filter(new Locator.FilterOptions().setHasText("Review & Complete")).click();
            page.locator("#sessionDraftTitle").fill("Release rehearsal session");
            page.locator("#sessionDraftBody").waitFor();
            assertThat(page.locator("#sessionDraftBody").inputValue()).isNotBlank();
            BoundingBox box = lifecycle.boundingBox();
            assertThat(box.y + box.height).isLessThanOrEqualTo(768.0);
            lifecycle.locator("button[data-complete-session]").click();
            page.waitForFunction("() => !document.querySelector('#sessionLifecycleDialog[open]')");
        }

        @Test @Order(10)
        void step10_theGeneratedLogAgreesWithWhatHappened() {
            page.navigate(base + "/campaigns/" + seeded.campaignId() + "/notes");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Locator sessionLogCard = page.locator("#notes-list .card").filter(new Locator.FilterOptions().setHasText("Session Log")).first();
            sessionLogCard.waitFor();
            sessionLogCard.locator("a").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.locator(".note-body").waitFor();
            String log = page.textContent(".note-body");
            assertThat(log).containsPattern("\\d{1,2}:\\d{2}").containsAnyOf("CET", "CEST", "Europe/Berlin");
            assertThat(log).contains(sceneTitleVisitedDuringRehearsal);
            assertThat(log).contains(defeatedCombatantName);
            assertThat(log).contains("sluice gate");
            assertThat(log).contains(encounterName);
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Linear campaign, one map")
    class Linear extends RehearsalSteps {
        @Override protected ReleaseRehearsalFixture.Shape shape() {
            return ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP;
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Branched campaign, two map scales, theatre of mind")
    class Branched extends RehearsalSteps {
        @Override protected ReleaseRehearsalFixture.Shape shape() {
            return ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS;
        }
    }
}
