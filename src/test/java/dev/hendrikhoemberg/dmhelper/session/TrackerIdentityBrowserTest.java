package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackerIdentityBrowserTest {

    @LocalServerPort private int port;
    @Autowired private CockpitInitialLoadFixtures fixtures;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private final BrowserFailureCollector failures = new BrowserFailureCollector();

    @BeforeAll
    void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
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
        page = guardedPage(context);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private Page guardedPage(BrowserContext browserContext) {
        Page guarded = browserContext.newPage();
        failures.attach(guarded);
        return guarded;
    }

    private CockpitInitialLoadFixtures.GroupedEncounter openCombat() {
        CockpitInitialLoadFixtures.GroupedEncounter seeded = fixtures.campaignWithGroupedEncounter();
        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForFunction("() => window.cockpitLayout?.mounted === true");
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        page.waitForSelector(".combatant-row");
        return seeded;
    }

    private long visibleBranchCount() {
        Object result = page.evaluate(
            "() => Array.from(document.querySelectorAll('.combatant-row .group-branch'))"
            + ".filter(el => el.offsetParent !== null).length");
        return ((Number) result).longValue();
    }

    @Test
    void theGroupRowIsLabelledAndItsToggleIsInsideTheModule() {
        openCombat();

        var groupRow = page.locator(".combatant-row:has(.group-count)").first();
        String label = groupRow.locator(".combatant-name__base").innerText();
        assertThat(label).isEqualTo("Goblin");

        var module = page.locator("[data-runtime-module='encounter']").first();
        var moduleBox = module.boundingBox();
        var toggleBox = groupRow.locator(".group-count").boundingBox();

        assertThat(toggleBox).isNotNull();
        assertThat(moduleBox).isNotNull();
        assertThat(toggleBox.x).isGreaterThanOrEqualTo(moduleBox.x);
        assertThat(toggleBox.y).isGreaterThanOrEqualTo(moduleBox.y);
        assertThat(toggleBox.x + toggleBox.width).isLessThanOrEqualTo(moduleBox.x + moduleBox.width);
        assertThat(toggleBox.y + toggleBox.height).isLessThanOrEqualTo(moduleBox.y + moduleBox.height);
    }

    @Test
    void theGroupTogglesByPointerInBothDirections() {
        openCombat();

        var toggle = page.locator(".combatant-row .group-count").first();
        assertThat(toggle.getAttribute("aria-expanded")).isEqualTo("false");
        assertThat(visibleBranchCount()).isZero();

        toggle.click();
        page.waitForSelector(".combatant-row .group-branch");
        assertThat(toggle.getAttribute("aria-expanded")).isEqualTo("true");
        assertThat(page.locator(".combatant-row:has(.group-branch)").count()).isEqualTo(3);

        toggle.click();
        page.waitForFunction("() => Array.from(document.querySelectorAll('.combatant-row .group-branch')).filter(el => el.offsetParent !== null).length === 0");
        assertThat(toggle.getAttribute("aria-expanded")).isEqualTo("false");
    }

    @Test
    void expandedGroupMembersArePairwiseDistinct() {
        openCombat();

        page.locator(".combatant-row .group-count").first().click();
        page.waitForSelector(".combatant-row .group-branch");

        var names = page.locator(".combatant-row:has(.group-branch) .combatant-name").allInnerTexts();
        assertThat(names).hasSize(3);
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    void theGroupLabelSurvivesItsMembersDying() {
        var seeded = openCombat();

        String labelBefore = page.locator(".combatant-row:has(.group-count) .combatant-name__base").first().innerText();

        for (int i = 0; i < Math.min(2, seeded.memberIds().size()); i++) {
            page.evaluate("(id) => window.dmRequest('/api/v1/combatants/' + id + '/defeated', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ defeated: true }) })", seeded.memberIds().get(i).toString());
        }

        page.evaluate("() => window.Alpine.$data(document.querySelector('.combatant-row')?.closest('[x-data]') || document.querySelector('[x-data*=combatTracker]'))?.reloadCombatants()");
        page.waitForFunction("() => document.querySelectorAll('.combatant-row.defeated').length >= 2");

        String labelAfter = page.locator(".combatant-row:has(.group-count) .combatant-name__base").first().innerText();
        assertThat(labelAfter).isEqualTo(labelBefore);
    }

    @Test
    void theRowConditionControlAppliesToItsOwnRow() {
        openCombat();
        var rows = page.locator(".combatant-row");
        String targetId = rows.nth(1).getAttribute("data-cid");
        String otherId = rows.nth(0).getAttribute("data-cid");

        rows.nth(0).click();                       // bind the editor somewhere else first
        rows.nth(1).locator(".condition-add").click();
        page.locator(".detail-conditions .condition-quick button")
                .filter(new Locator.FilterOptions().setHasText("Prone"))
                .first().click();

        page.waitForFunction(
                "(id) => document.querySelector(`[data-cid='${id}'] .cond-icon`) !== null",
                targetId);
        assertThat(page.locator("[data-cid='" + targetId + "'] .cond-icon").count()).isEqualTo(1);
        assertThat(page.locator("[data-cid='" + otherId + "'] .cond-icon").count())
                .as("no other combatant may gain the condition")
                .isZero();
        assertThat(page.evaluate(
                "() => window.Alpine.$data(document.querySelector('.tracker-panel')).selectedCombatantId"))
                .as("the shared editor must have been retargeted to the row that was clicked")
                .isEqualTo(targetId);
    }

    @Test
    void theDetailPanelNamesTheCombatantItEdits() {
        var seeded = openCombat();
        var rows = page.locator(".combatant-row");
        String firstName = rows.nth(0).locator(".combatant-name").innerText();
        String secondName = rows.nth(1).locator(".combatant-name").innerText();

        rows.nth(0).click();
        page.waitForSelector("[data-selected-combatant-name]");
        assertThat(page.locator("[data-selected-combatant-name]").innerText()).isEqualTo(firstName);

        rows.nth(1).click();
        page.waitForFunction("() => document.querySelector('[data-selected-combatant-name]')?.textContent === '" + secondName + "'");
        assertThat(page.locator("[data-selected-combatant-name]").innerText()).isEqualTo(secondName);
    }

    @Test
    void selectionAndActiveTurnAreDifferentTreatments() {
        var seeded = openCombat();

        String activeId = (String) page.evaluate(
            "() => window.Alpine.$data(document.querySelector('.tracker-panel')).activeCombatantId");
        if (activeId == null) {
            page.evaluate("() => window.Alpine.$data(document.querySelector('.tracker-panel')).nextTurn()");
            page.waitForFunction(
                "() => window.Alpine.$data(document.querySelector('.tracker-panel')).activeCombatantId !== null");
            activeId = (String) page.evaluate(
                "() => window.Alpine.$data(document.querySelector('.tracker-panel')).activeCombatantId");
        }

        var rows = page.locator(".combatant-row");
        for (int i = 0; i < rows.count(); i++) {
            if (!rows.nth(i).getAttribute("data-cid").equals(activeId)) {
                rows.nth(i).click();
                break;
            }
        }
        page.waitForSelector(".combatant-row.selected");

        assertThat(page.locator(".combatant-row.active").count()).isEqualTo(1);
        assertThat(page.locator(".combatant-row.selected").count()).isEqualTo(1);
        assertThat(page.locator(".combatant-row.active.selected").count()).isZero();

        String activeShadow = (String) page.evaluate(
            "() => getComputedStyle(document.querySelector('.combatant-row.active')).boxShadow");
        String selectedShadow = (String) page.evaluate(
            "() => getComputedStyle(document.querySelector('.combatant-row.selected')).boxShadow");

        assertThat(selectedShadow).isNotEqualTo(activeShadow);
        assertThat(selectedShadow).isNotEqualTo("none");
    }

    @Test
    void selectionSurvivesTheTurnAdvancingAndClearsWhenTheCombatantLeaves() {
        var seeded = openCombat();
        var rows = page.locator(".combatant-row");
        String cid = rows.first().getAttribute("data-cid");

        rows.first().click();
        page.waitForSelector(".combatant-row.selected");

        page.evaluate("() => window.Alpine.$data(document.querySelector('.tracker-panel')).nextTurn()");
        page.waitForFunction("() => window.Alpine.$data(document.querySelector('.tracker-panel')).selectedCombatantId !== null");
        assertThat(page.evaluate(
            "() => window.Alpine.$data(document.querySelector('.tracker-panel')).selectedCombatantId"))
            .isEqualTo(cid);

        page.evaluate("(id) => window.Alpine.$data(document.querySelector('.tracker-panel')).removeCombatant(id)", cid);
        page.waitForFunction("() => window.Alpine.$data(document.querySelector('.tracker-panel')).selectedCombatantId === null");
        page.waitForFunction("() => document.querySelector('.combatant-detail')?.offsetParent === null");
        assertThat(page.locator(".combatant-detail").first().isVisible()).isFalse();
    }

}
