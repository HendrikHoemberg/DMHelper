package dev.hendrikhoemberg.dmhelper.visual;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.hendrikhoemberg.dmhelper.support.PageReady;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.1, enforced against what the browser actually painted rather
 * than against what the stylesheet says. Cascade order, inline styles and fragment reuse
 * all get a vote here; the CSS-level test cannot see any of them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class TypographyRenderGateTest {

    @LocalServerPort private int port;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private PreparationSurfaceFixture prepFixture;

    private static Playwright playwright;
    private static Browser browser;
    private PopulatedCampaignFixture.Seeded seeded;
    private PreparationSurfaceFixture.Seeded prepared;

    @BeforeAll
    void seedAndLaunch() {
        seeded = fixture.seed();
        prepared = prepFixture.seed();
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void shutdown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    List<String> pages() {
        String c = "/campaigns/" + seeded.campaignId();
        String p = "/campaigns/" + prepared.campaignId();
        return List.of(
                c,
                c + "/adventures",
                c + "/party",
                c + "/encounters",
                c + "/maps",
                c + "/quests",
                c + "/session",
                p + "/encounters/" + prepared.encounterId());
    }

    @ParameterizedTest
    @MethodSource("pages")
    void everyCinzelElementIsADeclaredDisplayTitle(String path) {
        withGuardedPage(path, page -> {
            @SuppressWarnings("unchecked")
            List<String> undeclared = (List<String>) page.evaluate("""
                    () => Array.from(document.querySelectorAll('body *'))
                        .filter(el => getComputedStyle(el).fontFamily.includes('Cinzel'))
                        .filter(el => !el.hasAttribute('data-display-title'))
                        .map(el => el.tagName.toLowerCase() + '.' + (el.className || '(no class)'))
                    """);

            assertThat(undeclared).as("Cinzel without data-display-title on %s", path).isEmpty();
        });
    }

    @ParameterizedTest
    @MethodSource("pages")
    void atMostOneContentTitleUsesTheDisplayFace(String path) {
        withGuardedPage(path, page -> {
            Object count = page.evaluate("""
                    () => Array.from(document.querySelectorAll('[data-display-title]'))
                        .filter(el => !el.closest('.app-topbar, .rail, .book-cover'))
                        .length
                    """);

            assertThat(((Number) count).intValue())
                    .as("content titles in the display face on %s", path)
                    .isLessThanOrEqualTo(1);
        });
    }

    @ParameterizedTest
    @MethodSource("pages")
    void controlsAndChromeRenderInTheUiFace(String path) {
        withGuardedPage(path, page -> {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> serifControls = (List<Map<String, String>>) page.evaluate("""
                    () => Array.from(document.querySelectorAll('button, .btn, label, th, .badge'))
                        .filter(el => {
                          const f = getComputedStyle(el).fontFamily;
                          return f.includes('Cinzel') || f.includes('Alegreya');
                        })
                        .map(el => ({ tag: el.tagName.toLowerCase(), cls: el.className || '' }))
                    """);

            assertThat(serifControls).as("controls rendered in a book face on %s", path).isEmpty();
        });
    }

    private void withGuardedPage(String path, java.util.function.Consumer<Page> assertion) {
        try (Page page = browser.newPage()) {
            BrowserFailureCollector failures = new BrowserFailureCollector();
            failures.attach(page);
            PageReady.open(page, "http://localhost:" + port, path);
            assertion.accept(page);
            failures.assertNoFailures();
        }
    }
}
