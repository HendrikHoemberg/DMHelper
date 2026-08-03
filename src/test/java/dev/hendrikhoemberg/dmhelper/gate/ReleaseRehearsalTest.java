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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Tag("browser")
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
            seeded = fixture.seedForRehearsal(shape());
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

        private void applyPreset(String presetKey) {
            page.evaluate("key => window.cockpitLayout.applyPreset(key, { skipDirtyCheck: true })",
                    presetKey);
            page.waitForFunction("key => document.querySelector('#cockpitPresetPicker')?.value === key",
                    presetKey);
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
            page.waitForResponse(response -> response.url().endsWith("/session/start") && response.status() == 200,
                    () -> lifecycle.locator("button").filter(new Locator.FilterOptions().setHasText("Start")).first().click());
            page.waitForFunction("() => performance.getEntriesByType('navigation')[0]?.type === 'reload'");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForFunction("() => document.readyState === 'complete' && window.cockpitLayout?.mounted === true");
            page.waitForFunction("() => document.querySelector('[data-session-status]')?.dataset.sessionStatus === 'RUNNING'");
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
        void step4_theEncounterIsPreparedAndRunThroughTheReadinessFlow() {
            openCockpit();
            int actions = 0;
            Locator seedButton = page.locator("[data-runtime-module='story'] [data-seed-scene-encounter]");
            if (seedButton.count() > 0) {
                seedButton.first().click();
                actions++;
            } else if (shape() == ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS) {
                applyPreset("builtin:combat");
                Locator activatePrepared = page.locator("[data-runtime-module='encounter'] [data-encounter-id='"
                        + seeded.branchedEncounterId() + "']").first();
                activatePrepared.waitFor();
                activatePrepared.click();
                actions++;
            } else {
                throw new AssertionError("The scene-to-encounter action must be available for every rehearsal shape.");
            }
            applyPreset("builtin:combat");
            page.waitForSelector("[data-runtime-module='encounter'] [data-initiative-setup]");
            // The seeded roster is unplaced, which readiness reports as a WARNING. That must not
            // interrupt the run — activation places the tokens itself — so the encounter goes
            // straight to initiative setup and the dialog stays shut for anything non-blocking.
            assertThat(page.locator("#encounterReadinessDialog[open]").count())
                    .as("a warning-only readiness does not stop the DM with a dialog").isZero();
            encounterName = page.textContent("[data-runtime-module='encounter'] [data-encounter-name]").trim();
            if (shape() == ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS) {
                assertThat(page.locator("[data-runtime-module='encounter'] [data-module-content-root]").getAttribute("data-encounter-id"))
                        .as("the branched rehearsal uses the fixture-prepared encounter")
                        .isEqualTo(seeded.branchedEncounterId().toString());
            }
            assertThat(actions).isLessThanOrEqualTo(3);
            assertVisibleWithoutScrolling("encounter identity", "[data-runtime-module='encounter'] [data-encounter-name]");
        }

        @Test @Order(5)
        void step5_initiativeDamageConditionsDefeatAndTurnsResolve() {
            openCockpit();
            applyPreset("builtin:combat");
            Locator setup = page.locator("[data-runtime-module='encounter'] [data-initiative-setup]");
            setup.waitFor();
            page.waitForFunction(
                    "() => document.querySelectorAll('[data-runtime-module=\"encounter\"] input[data-initiative-input]').length > 0");
            setup.locator("button[data-roll-unset-initiative]").click();
            page.waitForFunction("() => !document.querySelector('[data-initiative-setup] .initiative-setup__row--unset')");
            setup.locator("button[data-start-combat]").click();
            page.waitForSelector("[data-running-turn-controls]");
            assertVisibleWithoutScrolling("current turn", "[data-running-turn-controls]");
            java.util.Map<?, ?> waveState = (java.util.Map<?, ?>) page.evaluate("""
                    async ({campaignId, encounterName}) => {
                        const encounters = await (await fetch(`/api/v1/campaigns/${campaignId}/encounters`)).json();
                        const encounter = encounters.find(e => e.name === encounterName);
                        const waves = await (await fetch(`/api/v1/encounters/${encounter.id}/waves`)).json();
                        const combatants = await (await fetch(`/api/v1/encounters/${encounter.id}/combatants`)).json();
                        const main = waves.find(w => w.waveKey === 'main');
                        return {mainIds: combatants.filter(c => c.waveId === main.id).map(c => c.id)};
                    }
                    """, java.util.Map.of("campaignId", seeded.campaignId().toString(), "encounterName", encounterName));
            @SuppressWarnings("unchecked")
            List<String> mainCombatantIds = (List<String>) waveState.get("mainIds");
            page.locator(".group-count").all().forEach(button -> {
                try { button.click(); } catch (Exception ignored) { /* no groups to expand */ }
            });
            Locator firstRow = page.locator(".combatant-row").first();
            defeatedCombatantName = firstRow.locator(".combatant-name").textContent()
                    .lines().findFirst().orElseThrow().trim();
            for (String combatantId : mainCombatantIds) {
                Locator row = page.locator(".combatant-row[data-cid='" + combatantId + "']");
                row.locator(".hp-delta-input").fill("-999");
                row.locator(".hp-delta-input").press("Enter");
                page.waitForFunction("id => document.querySelector(`.combatant-row[data-cid='${id}']`)?.classList.contains('defeated')", combatantId);
            }
            page.waitForFunction("ids => ids.every(id => document.querySelector(`.combatant-row[data-cid='${id}']`)?.classList.contains('defeated'))", mainCombatantIds);
            if (shape() == ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS) {
                String pendingWaveId = seeded.branchedReserveWaveId().toString();
                page.waitForFunction("""
                        async ({campaignId, encounterName}) => {
                            const encounters = await (await fetch(`/api/v1/campaigns/${campaignId}/encounters`)).json();
                            const encounter = encounters.find(e => e.name === encounterName);
                            if (!encounter) return false;
                            const waves = await (await fetch(`/api/v1/encounters/${encounter.id}/waves`)).json();
                            return waves.find(w => w.waveKey === 'main')?.status === 'DEPLETED';
                        }
                        """, java.util.Map.of("campaignId", seeded.campaignId().toString(), "encounterName", encounterName));
                Locator nextWave = page.locator("[data-runtime-module='encounter'] .wave-banner button").first();
                nextWave.waitFor();
                Response spawnResponse = page.waitForResponse(
                        response -> response.url().endsWith("/waves/" + pendingWaveId + "/spawn")
                                && response.request().method().equals("POST"), nextWave::click);
                assertThat(spawnResponse.status()).as("the pending wave spawn is accepted").isBetween(200, 299);
                page.waitForFunction("waveId => !document.querySelector('.wave-banner button')", pendingWaveId);
                Boolean spawned = (Boolean) page.evaluate("""
                        async ({campaignId, encounterName, waveId}) => {
                            const encounters = await (await fetch(`/api/v1/campaigns/${campaignId}/encounters`)).json();
                            const encounter = encounters.find(e => e.name === encounterName);
                            const waves = await (await fetch(`/api/v1/encounters/${encounter.id}/waves`)).json();
                            const combatants = await (await fetch(`/api/v1/encounters/${encounter.id}/combatants`)).json();
                            const wave = waves.find(w => w.id === waveId);
                            return wave?.status === 'ACTIVE'
                                && wave.combatantCount === 1
                                && combatants.some(c => c.waveId === waveId && c.name === 'Vault reinforcements' && !c.hidden);
                        }
                        """, java.util.Map.of("campaignId", seeded.campaignId().toString(),
                                "encounterName", encounterName, "waveId", pendingWaveId));
                assertThat(spawned).as("the real named reserve wave is active in the scene-created encounter").isTrue();
                page.locator(".combatant-row").filter(new Locator.FilterOptions().setHasText("Vault reinforcements")).waitFor();
                assertThat(page.locator(".combatant-row").filter(new Locator.FilterOptions().setHasText("Vault reinforcements")).count())
                        .as("the prepared reserve combatant is visible after spawning").isEqualTo(1);
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
            applyPreset("builtin:combat");
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
            applyPreset("builtin:combat");
            page.locator(".combatant-row").first().click();
            // The Combat preset now ships the Reference module, so the rehearsal drives the
            // real affordance -- revealModule -- instead of hand-docking it into a zone.
            page.evaluate("() => window.cockpitLayout.revealModule('reference')");
            page.waitForFunction(
                    "() => document.querySelector(\"[data-runtime-module='reference'] input[type=search]\") !== null");
            page.locator("[data-runtime-module='reference'] input[type='search']").first().fill("grapple");
            page.waitForFunction("() => document.querySelectorAll('[data-runtime-module=\"reference\"] [data-reference-result]').length > 0");
            assertThat(page.locator("[data-runtime-module='reference'] [data-reference-result]").count()).isGreaterThan(0);
            assertThat(page.url()).endsWith("/session");
        }

        @Test @Order(7)
        void step7_notesAreCapturedAndThePlanIsUpdated() {
            openCockpit();
            applyPreset("builtin:session-review");
            page.locator("[data-runtime-module='quick-notes'] textarea, [data-runtime-module='quick-notes'] input[type='text']").first().fill("The warden fled through the sluice gate.");
            page.locator("[data-runtime-module='quick-notes'] button[aria-label='Save quick note']").first().click();
            page.waitForSelector("[data-runtime-module='quick-notes'] .quicknote-row");
            page.waitForFunction("() => document.querySelector('#runtimeStatus [data-status-save]')?.dataset.state === 'saved'");
            assertThat(page.locator("#runtimeStatus [data-status-save]").getAttribute("data-state"))
                    .as("the quick-note action reports a real save").isEqualTo("saved");
            planTitle = "Release rehearsal plan";
            Locator promotePlan = page.locator("[data-runtime-module='quick-notes'] [data-promote-session-plan]").last();
            promotePlan.waitFor();
            promotePlan.click();
            page.waitForFunction("title => document.querySelector('[data-runtime-module=\\\"session-plan\\\"] h3.u-text-sm')?.textContent.trim() === title", planTitle);
            assertThat(page.locator("[data-runtime-module='session-plan'] h3.u-text-sm").textContent().trim()).as("the session plan reflects the promoted rehearsal update").isEqualTo(planTitle);
            assertVisibleWithoutScrolling("save status", "#runtimeStatus [data-status-save]");
        }

        @Test @Order(9)
        void step9_theEncounterAndSessionAreCompleted() {
            openCockpit();
            applyPreset("builtin:combat");
            page.locator("[data-runtime-module='encounter'] [data-end-encounter]").click();
            Locator endDialog = page.locator("[data-encounter-end-dialog]");
            endDialog.waitFor();
            endDialog.locator("button",
                    new Locator.LocatorOptions().setHasText("End encounter")).click();
            // Concluding an encounter now reports what it was worth before the tracker lets
            // go of it: the summary owns the XP and the DM dismisses it deliberately.
            Locator summary = page.locator(".summary-stats");
            summary.waitFor();
            assertThat(summary.textContent()).as("the close-out states the XP").contains("Reward XP");
            page.locator("button", new Page.LocatorOptions().setHasText("Skip")).click();
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
            sessionLogCard.locator("a").first().click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.locator(".note-body").waitFor();
            String log = page.textContent(".note-body");
            assertThat(log).containsPattern("\\d{1,2}:\\d{2}").containsAnyOf("CET", "CEST", "Europe/Berlin");
            assertThat(log).contains(sceneTitleVisitedDuringRehearsal);
            assertThat(log).contains(defeatedCombatantName);
            assertThat(log).contains(encounterName);
        }
    }

    @Nested
    @Order(1)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Linear campaign, one map")
    class Linear extends RehearsalSteps {
        @Override protected ReleaseRehearsalFixture.Shape shape() {
            return ReleaseRehearsalFixture.Shape.LINEAR_ONE_MAP;
        }
    }

    @Nested
    @Order(2)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("Branched campaign, two map scales, theatre of mind")
    class Branched extends RehearsalSteps {
        @Override protected ReleaseRehearsalFixture.Shape shape() {
            return ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS;
        }
    }
}
