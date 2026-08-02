package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-31 section 12.4 (laptop behavior): every built-in preset fits on a laptop
 * without document scroll or clipped chrome, support modules become tabs when their zone is
 * constrained, and primary runtime actions stay visible without scrolling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class CockpitLaptopFitGateTest {

    private static final int[][] VIEWPORTS = {{1280, 720}, {1440, 900}, {1920, 1080}};

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;
    @Autowired private SessionLifecycleService lifecycleService;

    private static Playwright playwright;
    private static Browser browser;
    private ReleaseRehearsalFixture.Seeded seeded;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    @BeforeAll
    void launch() throws Exception {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        seeded = fixture.seed();
        lifecycleService.start(seeded.campaignId(), seeded.playableMapId());
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        failures = new BrowserFailureCollector();
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    private void openCockpitWithPreset(String presetKey, int width, int height) {
        page.setViewportSize(width, height);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
        page.locator("#cockpitPresetPicker").selectOption(presetKey);
        page.waitForFunction("key => window.cockpitLayout.currentPresetKey === key", presetKey);
    }

    @Test
    void everyBuiltInPresetFitsWithoutClippingOrDocumentScroll() {
        for (String preset : java.util.List.of("builtin:exploration", "builtin:combat",
                "builtin:theatre-of-mind", "builtin:session-review")) {
            for (int[] viewport : VIEWPORTS) {
                openCockpitWithPreset(preset, viewport[0], viewport[1]);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> report =
                        (java.util.Map<String, Object>) page.evaluate("""
                        () => ({
                          docOverflow: document.documentElement.scrollWidth
                                     - document.documentElement.clientWidth,
                          clipped: [...document.querySelectorAll('[data-cockpit-commandbar] button,'
                                   + ' [data-cockpit-commandbar] a')]
                            .filter(el => {
                              const r = el.getBoundingClientRect();
                              return r.width === 0 || r.right > innerWidth + 1;
                            }).length,
                          scrollingModules: [...document.querySelectorAll('.cockpit-module')]
                            .filter(m => getComputedStyle(m).overflow === 'visible').length
                        })
                        """);
                assertThat(((Number) report.get("docOverflow")).intValue())
                        .as("%s at %dx%d document overflow", preset, viewport[0], viewport[1])
                        .isLessThanOrEqualTo(1);
                assertThat(((Number) report.get("clipped")).intValue())
                        .as("%s at %dx%d clipped command bar controls", preset, viewport[0], viewport[1])
                        .isZero();
                assertThat(((Number) report.get("scrollingModules")).intValue())
                        .as("%s at %dx%d modules must own their scrolling", preset, viewport[0], viewport[1])
                        .isZero();
            }
        }
    }

    @Test
    void supportModulesBecomeTabsWhenTheZoneIsConstrained() {
        openCockpitWithPreset("builtin:combat", 1280, 720);
        assertThat(page.locator("[data-zone='LEFT_SUPPORT'] [role='tab']").count())
                .as("story and party share the constrained left zone as tabs")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void primaryRuntimeActionsStayVisibleWithoutScrolling() {
        openCockpitWithPreset("builtin:combat", 1280, 720);
        Object hidden = page.evaluate("""
                () => [...document.querySelectorAll('[data-runtime-action]')]
                        .filter(el => {
                          const r = el.getBoundingClientRect();
                          return r.bottom > innerHeight + 1 || r.width === 0;
                        }).length
                """);
        assertThat(((Number) hidden).intValue()).isZero();
    }
}
