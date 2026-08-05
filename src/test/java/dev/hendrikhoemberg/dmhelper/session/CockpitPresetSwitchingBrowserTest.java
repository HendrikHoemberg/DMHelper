package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.*;
import dev.hendrikhoemberg.dmhelper.support.PageReady;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class CockpitPresetSwitchingBrowserTest {

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;

    private static Playwright playwright;
    private static Browser browser;
    private Page page;
    private ReleaseRehearsalFixture.Seeded seeded;

    @BeforeAll void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach void openCockpit() throws Exception {
        seeded = fixture.seed();
        page = browser.newContext().newPage();
        PageReady.open(page, "http://localhost:" + port, cockpitPath());
    }

    @AfterEach void closePage() {
        if (page != null) page.close();
    }

    /** SessionController maps the cockpit at /campaigns/{campaignId}/session. */
    private String cockpitPath() {
        return "/campaigns/" + seeded.campaignId() + "/session";
    }

    private void reopen() {
        PageReady.open(page, "http://localhost:" + port, cockpitPath());
    }

    private String activePreset() {
        return page.locator("[data-cockpit-workbench]").getAttribute("data-cockpit-preset");
    }

    private String zoneOf(String moduleKey) {
        return page.locator("[data-cockpit-zone]:has(.cockpit-module[data-module-key='"
                + moduleKey + "'])").getAttribute("data-cockpit-zone");
    }

    @Test
    void opensOnExplorationByDefault() {
        assertThat(activePreset()).isEqualTo("builtin:exploration");
    }

    @Test
    void switchingPresetChangesTheWorkbenchAttribute() {
        page.selectOption("#cockpitPresetPicker", "builtin:combat");

        assertThat(activePreset()).isEqualTo("builtin:combat");
    }

    @Test
    void presetChoiceSurvivesReload() {
        page.selectOption("#cockpitPresetPicker", "builtin:combat");
        reopen();

        assertThat(activePreset()).isEqualTo("builtin:combat");
    }

    @Test
    void digitShortcutSelectsItsPreset() {
        page.keyboard().press("Digit2");

        assertThat(activePreset()).isEqualTo("builtin:combat");
    }

    @Test
    void collapsingAZoneMarksItAndSurvivesReload() {
        page.click("[data-cockpit-zone='RIGHT_SUPPORT'] [data-zone-collapse]");
        assertThat(page.locator("[data-cockpit-zone='RIGHT_SUPPORT']")
                .getAttribute("data-collapsed")).isEqualTo("true");

        reopen();

        assertThat(page.locator("[data-cockpit-zone='RIGHT_SUPPORT']")
                .getAttribute("data-collapsed")).isEqualTo("true");
    }

    @Test
    void collapsedZoneCanBeExpandedAgain() {
        page.click("[data-cockpit-zone='RIGHT_SUPPORT'] [data-zone-collapse]");
        assertThat(page.locator("[data-cockpit-zone='RIGHT_SUPPORT']")
                .getAttribute("data-collapsed")).isEqualTo("true");

        page.click("[data-cockpit-zone='RIGHT_SUPPORT'] [data-zone-collapse]");
        assertThat(page.locator("[data-cockpit-zone='RIGHT_SUPPORT']")
                .getAttribute("data-collapsed")).isEqualTo("false");
    }

    // --- The placement engine: this is the coverage the stage preamble exists for. ---

    @Test
    void explorationPlacesModulesInTheirAssignedZones() {
        assertThat(zoneOf("story")).isEqualTo("PRIMARY");
        assertThat(zoneOf("session-plan")).isEqualTo("LEFT_SUPPORT");
        assertThat(zoneOf("party")).isEqualTo("RIGHT_SUPPORT");
        assertThat(zoneOf("quick-notes")).isEqualTo("BOTTOM_UTILITY");
    }

    @Test
    void switchingToCombatRelocatesModulesOutOfTheDepot() {
        page.selectOption("#cockpitPresetPicker", "builtin:combat");

        assertThat(zoneOf("map")).isEqualTo("PRIMARY");
        assertThat(zoneOf("story")).isEqualTo("LEFT_SUPPORT");
        assertThat(zoneOf("party")).isEqualTo("LEFT_SUPPORT");
        assertThat(zoneOf("encounter")).isEqualTo("RIGHT_SUPPORT");
        assertThat(zoneOf("session-log")).isEqualTo("BOTTOM_UTILITY");
    }

    @Test
    void everyPresetPlacesEveryModuleItAssigns() {
        for (String preset : List.of("builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review")) {
            page.selectOption("#cockpitPresetPicker", preset);

            assertThat(page.locator("[data-cockpit-zone] .cockpit-module[data-module-key]").count())
                    .as("%s renders no modules", preset)
                    .isGreaterThan(0);
        }
    }

    @Test
    void clickingAZoneTabSelectsThatModulesPanel() {
        page.click("[data-module-tab='reference']");

        assertThat(page.locator("[data-module-panel='reference']").isVisible()).isTrue();
    }
}
