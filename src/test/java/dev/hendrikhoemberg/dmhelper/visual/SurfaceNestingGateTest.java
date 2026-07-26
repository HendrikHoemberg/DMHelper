package dev.hendrikhoemberg.dmhelper.visual;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2: "Reduce nested same-color cards; use spacing, rules and
 * surface elevation to express grouping." A slab on an identical slab reads as noise, and
 * only the rendered tree knows it happened.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurfaceNestingGateTest {

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
        return List.of(c, c + "/adventures", c + "/party", c + "/encounters",
                c + "/session", p + "/encounters/" + prepared.encounterId());
    }

    @ParameterizedTest
    @MethodSource("pages")
    void noBorderedSurfaceSitsOnAnIdenticalBorderedSurface(String path) {
        try (Page page = browser.newPage()) {
            page.setViewportSize(1366, 768);
            page.navigate("http://localhost:" + port + path);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            @SuppressWarnings("unchecked")
            List<String> offenders = (List<String>) page.evaluate("""
                    () => {
                      const painted = (el) => {
                        const s = getComputedStyle(el);
                        const hasFill = s.backgroundColor !== 'rgba(0, 0, 0, 0)'
                          && s.backgroundColor !== 'transparent';
                        const hasEdge = parseFloat(s.borderTopWidth) > 0
                          && s.borderTopColor !== 'rgba(0, 0, 0, 0)';
                        return hasFill && hasEdge ? s.backgroundColor : null;
                      };
                      const describe = (el) =>
                        el.tagName.toLowerCase() + (el.className ? '.' + String(el.className).trim().split(/\\s+/).join('.') : '');
                      const out = [];
                      for (const el of document.querySelectorAll('body *')) {
                        const fill = painted(el);
                        if (!fill) continue;
                        for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
                          const parentFill = painted(p);
                          if (!parentFill) continue;
                          if (parentFill === fill) out.push(describe(p) + ' > … > ' + describe(el));
                          break;
                        }
                      }
                      return [...new Set(out)];
                    }
                    """);

            assertThat(offenders)
                    .as("same-fill bordered surfaces nested on %s — flatten the inner one", path)
                    .isEmpty();
        }
    }
}
