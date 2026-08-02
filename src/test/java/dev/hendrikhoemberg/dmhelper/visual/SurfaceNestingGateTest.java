package dev.hendrikhoemberg.dmhelper.visual;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import dev.hendrikhoemberg.dmhelper.support.PageReady;
import dev.hendrikhoemberg.dmhelper.support.CampaignFixtures;
import dev.hendrikhoemberg.dmhelper.support.PopulatedCampaignFixture;
import dev.hendrikhoemberg.dmhelper.support.PreparationSurfaceFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10.2: "Reduce nested same-color cards; use spacing, rules and
 * surface elevation to express grouping." A slab on an identical slab reads as noise, and
 * only the rendered tree knows it happened.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class SurfaceNestingGateTest {

    private static final String SURFACE_NESTING_DETECTOR = """
            () => {
              const painted = (el) => {
                const s = getComputedStyle(el);
                const isRendered = s.display !== 'none' && s.visibility !== 'hidden'
                  && s.visibility !== 'collapse';
                const hasFill = s.backgroundColor !== 'rgba(0, 0, 0, 0)'
                  && s.backgroundColor !== 'transparent';
                const hasEdge = [s.borderTopWidth, s.borderRightWidth,
                  s.borderBottomWidth, s.borderLeftWidth]
                  .some(width => parseFloat(width) > 0);
                const hasPaintedEdge = [s.borderTopColor, s.borderRightColor,
                  s.borderBottomColor, s.borderLeftColor]
                  .some(color => color !== 'rgba(0, 0, 0, 0)' && color !== 'transparent');
                return isRendered && hasFill && hasEdge && hasPaintedEdge ? s.backgroundColor : null;
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
            """;

    @LocalServerPort private int port;
    @Autowired private PopulatedCampaignFixture fixture;
    @Autowired private PreparationSurfaceFixture prepFixture;
    @Autowired private CampaignFixtures campaignFixtures;

    private static Playwright playwright;
    private static Browser browser;
    private PopulatedCampaignFixture.Seeded seeded;
    private PreparationSurfaceFixture.Seeded prepared;
    private UUID blockedCampaignId;

    @BeforeAll
    void seedAndLaunch() {
        seeded = fixture.seed();
        prepared = prepFixture.seed();
        blockedCampaignId = campaignFixtures.operationalFixtureNotReady();
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

    static Stream<Arguments> specialSurfaces() {
        return Stream.of(
                Arguments.of("campaign", ".readiness-panel"),
                Arguments.of("preparation", "[data-prep-summary]"));
    }

    @Test
    void detectorReportsSameFillWhenOnlySideBordersArePainted() {
        try (Page page = browser.newPage()) {
            page.setContent("""
                    <style>
                      .outer { background: rgb(20, 15, 10); border-left: 2px solid rgb(80, 60, 40); }
                      .inner { background: rgb(20, 15, 10); border-bottom: 2px solid rgb(80, 60, 40); }
                    </style>
                    <section class="outer"><div class="inner">fixture</div></section>
                    """);

            assertThat(findOffenders(page))
                    .as("side-only bordered same-fill fixture must be detected")
                    .containsExactly("section.outer > … > div.inner");
        }
    }

    @ParameterizedTest
    @MethodSource("specialSurfaces")
    void flatteningRuleTargetsTheEmittedSpecialSurface(String pageKind, String selector) {
        String path = pageKind.equals("campaign")
                ? "/campaigns/" + seeded.campaignId()
                : "/campaigns/" + prepared.campaignId() + "/encounters/" + prepared.encounterId();
        try (Page page = browser.newPage()) {
            PageReady.open(page, "http://localhost:" + port, path);

            @SuppressWarnings("unchecked")
            List<String> styles = (List<String>) page.evaluate("""
                    (selector) => Array.from(document.querySelectorAll(selector)).map(el => {
                      const s = getComputedStyle(el);
                      return [s.backgroundColor, s.borderTopWidth, s.borderRightWidth,
                        s.borderBottomWidth, s.borderLeftWidth, s.paddingBottom].join('|');
                    })
                    """, selector);

            assertThat(styles)
                    .as("emitted %s surface %s must use the flattening rule", pageKind, selector)
                    .containsExactly(pageKind.equals("campaign")
                            ? "rgba(0, 0, 0, 0)|1px|0px|0px|0px|8px"
                            : "rgba(0, 0, 0, 0)|0px|0px|0px|0px|8px");
        }
    }

    @ParameterizedTest
    @MethodSource("pages")
    void noBorderedSurfaceSitsOnAnIdenticalBorderedSurface(String path) {
        try (Page page = browser.newPage()) {
            page.setViewportSize(1366, 768);
            PageReady.open(page, "http://localhost:" + port, path);

            assertThat(findOffenders(page))
                    .as("same-fill bordered surfaces nested on %s — flatten the inner one", path)
                    .isEmpty();
        }
    }

    @Test
    void readinessActionsWrapWithoutInheritingCardFormStyling() {
        try (Page page = browser.newPage()) {
            page.setViewportSize(760, 900);
            PageReady.open(page, "http://localhost:" + port, "/campaigns/" + blockedCampaignId);

            @SuppressWarnings("unchecked")
            Map<String, Object> styles = (Map<String, Object>) page.evaluate("""
                    () => {
                      // Any blocker row will do; the ASSET category this used to name was
                      // retired with handout safety classification.
                      const actions = [...document.querySelectorAll('.readiness-item__actions')]
                        .find(el => el.querySelector('.inline-form'));
                      const form = actions.querySelector('.inline-form');
                      const formStyle = getComputedStyle(form);
                      return {
                        flexWrap: getComputedStyle(actions).flexWrap,
                        formBackground: formStyle.backgroundColor,
                        formBorder: formStyle.borderTopWidth,
                        formPadding: formStyle.paddingTop,
                        formMargin: formStyle.marginBottom,
                        overflows: actions.scrollWidth > actions.clientWidth,
                        summaryDisplay: getComputedStyle(
                          document.querySelector('.readiness-group__heading')).display
                      };
                    }
                    """);

            assertThat(styles).containsEntry("flexWrap", "wrap");
            assertThat(styles).containsEntry("formBackground", "rgba(0, 0, 0, 0)");
            assertThat(styles).containsEntry("formBorder", "0px");
            assertThat(styles).containsEntry("formPadding", "0px");
            assertThat(styles).containsEntry("formMargin", "0px");
            assertThat(styles).containsEntry("overflows", false);
            assertThat(styles).containsEntry("summaryDisplay", "list-item");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> findOffenders(Page page) {
        return (List<String>) page.evaluate(SURFACE_NESTING_DETECTOR);
    }
}
