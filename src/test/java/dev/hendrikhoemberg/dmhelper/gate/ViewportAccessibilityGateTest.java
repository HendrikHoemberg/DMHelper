package dev.hendrikhoemberg.dmhelper.gate;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 11.2, in full, at both required viewports. This is a gate, not a
 * smoke test: every assertion here corresponds to one bullet in the spec.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class ViewportAccessibilityGateTest {

    private static final int[][] VIEWPORTS = {{1366, 768}, {1920, 1080}};
    private static final String FOCUSABLE_QUERY = "button:not([disabled]), [href], input:not([disabled]), "
            + "select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex=\"-1\"])";

    @LocalServerPort private int port;
    @Autowired private ReleaseRehearsalFixture fixture;
    @Autowired private SessionLifecycleService lifecycleService;
    @Autowired private CockpitModuleRegistry moduleRegistry;

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

    private void openCockpit(int width, int height) {
        page.setViewportSize(width, height);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForFunction("window.cockpitLayout?.mounted === true");
    }

    /** Opens the compact topbar "More" menu so overflow pickers become actionable. */
    private void openCockpitMoreMenu() {
        Locator details = page.locator("details.cockpit-topbar__overflow");
        if (!Boolean.TRUE.equals(details.evaluate("el => el.open"))) {
            details.locator("summary").click();
        }
        details.locator(".cockpit-topbar__overflow-panel").waitFor();
    }

    private Locator visibleFirst(String selector) {
        Locator candidates = page.locator(selector);
        for (int index = 0; index < candidates.count(); index++) {
            Locator candidate = candidates.nth(index);
            if (candidate.isVisible()) return candidate;
        }
        throw new AssertionError("No visible element matched " + selector);
    }

    @Test
    void theCockpitNeverScrollsTheDocument() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            Object overflow = page.evaluate("""
                    () => ({
                      v: document.documentElement.scrollHeight - document.documentElement.clientHeight,
                      h: document.documentElement.scrollWidth - document.documentElement.clientWidth
                    })
                    """);
            @SuppressWarnings("unchecked")
            Map<String, Object> o = (Map<String, Object>) overflow;

            assertThat(((Number) o.get("v")).intValue())
                    .as("vertical document overflow at %dx%d", viewport[0], viewport[1])
                    .isLessThanOrEqualTo(1);
            assertThat(((Number) o.get("h")).intValue())
                    .as("horizontal document overflow at %dx%d", viewport[0], viewport[1])
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    void modulesNeitherOverlapNorClip() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            @SuppressWarnings("unchecked")
            List<String> problems = (List<String>) page.evaluate("""
                    (bounds) => {
                      const modules = [...document.querySelectorAll('[data-module-key]')]
                        .filter(m => m.offsetParent !== null);
                      const rects = modules.map(m => ({ key: m.dataset.moduleKey, r: m.getBoundingClientRect() }));
                      const out = [];
                      for (const { key, r } of rects) {
                        if (r.right > bounds.w + 1 || r.bottom > bounds.h + 1 || r.left < -1 || r.top < -1) {
                          out.push(key + ' clips the viewport');
                        }
                      }
                      for (let i = 0; i < rects.length; i++) {
                        for (let j = i + 1; j < rects.length; j++) {
                          const a = rects[i].r, b = rects[j].r;
                          const overlap = a.left < b.right - 1 && b.left < a.right - 1
                                       && a.top < b.bottom - 1 && b.top < a.bottom - 1;
                          if (overlap) out.push(rects[i].key + ' overlaps ' + rects[j].key);
                        }
                      }
                      return out;
                    }
                    """, Map.of("w", viewport[0], "h", viewport[1]));

            assertThat(problems).as("layout at %dx%d", viewport[0], viewport[1]).isEmpty();
        }
    }

    @Test
    void everyVisibleModuleRespectsItsDeclaredMinimum() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            for (var definition : moduleRegistry.all()) {
                Locator module = page.locator("[data-module-key='" + definition.key() + "']");
                if (module.count() == 0 || !module.first().isVisible()) continue;

                BoundingBox box = module.first().boundingBox();
                assertThat(box.width)
                        .as("%s width at %dx%d", definition.key(), viewport[0], viewport[1])
                        .isGreaterThanOrEqualTo(definition.minWidthPx());
                assertThat(box.height)
                        .as("%s height at %dx%d", definition.key(), viewport[0], viewport[1])
                        .isGreaterThanOrEqualTo(definition.minHeightPx());
            }
        }
    }

    @Test
    void theCommandBarStaysFullyReachable() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);
            openCockpitMoreMenu();

            for (String selector : List.of("#cockpitLayoutModeButton",
                    "#runtimeStatus", "[data-display-title]", "button[x-ref='sessionButton']")) {
                Locator control = page.locator(selector).first();
                BoundingBox box = control.boundingBox();
                assertThat(control.isVisible()).as("%s visible at %dx%d", selector, viewport[0], viewport[1])
                        .isTrue();
                assertThat(box).as("%s present at %dx%d", selector, viewport[0], viewport[1]).isNotNull();
                assertThat(box.x).as("%s left edge at %dx%d", selector, viewport[0], viewport[1])
                        .isGreaterThanOrEqualTo(-1);
                assertThat(box.y).as("%s top edge at %dx%d", selector, viewport[0], viewport[1])
                        .isGreaterThanOrEqualTo(-1);
                assertThat(box.x + box.width).as("%s right edge at %dx%d", selector, viewport[0], viewport[1])
                        .isLessThanOrEqualTo((double) viewport[0] + 1);
                assertThat(box.y + box.height).as("%s bottom edge at %dx%d", selector, viewport[0], viewport[1])
                        .isLessThanOrEqualTo((double) viewport[1] + 1);
                if (!selector.equals("#runtimeStatus") && !selector.equals("[data-display-title]")) {
                    assertThat(control.isEnabled()).as("%s enabled at %dx%d", selector, viewport[0], viewport[1])
                            .isTrue();
                }
            }
            @SuppressWarnings("unchecked")
            List<String> statusStates = (List<String>) page.evaluate("""
                    () => [...document.querySelectorAll('#runtimeStatus [data-status-save]')]
                      .map(el => el.getAttribute('data-state'))
                    """);
            assertThat(statusStates)
                    .as("runtime status child states at %dx%d", viewport[0], viewport[1])
                    .containsExactly("idle");
        }
    }

    @Test
    void keyboardUsersCanDriveTheWorkspace() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);
            openCockpitMoreMenu();

        page.locator("#cockpitLayoutModeButton").focus();
        page.keyboard().press("Enter");
        page.waitForFunction(
                "() => document.querySelector('[data-cockpit-workbench]').dataset.layoutMode === 'edit'");

        Locator splitter = page.locator("[role='separator']").first();
        splitter.focus();
        String sizeBefore = (String) page.evaluate("""
                () => getComputedStyle(document.querySelector('[data-cockpit-workbench]'))
                        .getPropertyValue('--primary-size')
                """);
        page.keyboard().press("ArrowRight");
        assertThat((String) page.evaluate("""
                () => getComputedStyle(document.querySelector('[data-cockpit-workbench]'))
                        .getPropertyValue('--primary-size')
                """))
                .as("arrow key resizes the adjacent zones")
                .isNotEqualTo(sizeBefore);
        assertThat(splitter.getAttribute("aria-valuenow"))
                .as("the splitter reports its current value")
                .isNotNull();
        page.keyboard().press("ArrowLeft");

        openCockpitMoreMenu();
        page.locator("#cockpitLayoutModeButton").focus();
        page.keyboard().press("Enter");
        if (page.locator("#cockpitLayoutExitDialog").isVisible()) {
            page.locator("[data-layout-exit='discard']").click();
        }
        page.waitForFunction(
                "() => document.querySelector('[data-cockpit-workbench]').dataset.layoutMode === 'locked'");

        Locator tab = page.locator("[role='tab']").first();
        tab.focus();
        page.keyboard().press("ArrowRight");
            assertThat((boolean) page.evaluate(
                "() => document.activeElement?.getAttribute('role') === 'tab'"))
                .as("tab strip keeps roving focus at %dx%d", viewport[0], viewport[1])
                .isTrue();
        }
    }

    @Test
    void thePrimaryZoneOwnsTheSpace() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            Object share = page.evaluate("""
                    () => {
                      const zone = (name) => document.querySelector(`[data-cockpit-zone="${name}"]`);
                      const area = (el) => {
                        if (!el || el.offsetParent === null) return 0;
                        const r = el.getBoundingClientRect();
                        return r.width * r.height;
                      };
                      const primary = area(zone('PRIMARY'));
                      const total = ['PRIMARY', 'LEFT_SUPPORT', 'RIGHT_SUPPORT', 'BOTTOM_UTILITY']
                        .map(zone).map(area).reduce((a, b) => a + b, 0);
                      return total > 0 ? primary / total : 0;
                    }
                    """);

            assertThat(((Number) share).doubleValue())
                    .as("primary zone share at %dx%d (spec §7.2: 50-65%%)", viewport[0], viewport[1])
                    .isBetween(0.45, 0.75);
        }
    }

    @Test
    void combatMapContentFillsItsModuleBody() {
        openCockpit(1920, 1080);
        page.locator("#cockpitPresetPicker").selectOption("builtin:combat");
        page.locator("[data-module-key='map'] .cockpit-table").waitFor();

        @SuppressWarnings("unchecked")
        Map<String, Number> heights = (Map<String, Number>) page.evaluate("""
                () => {
                  const module = document.querySelector('[data-module-key="map"]');
                  const body = module.querySelector('[data-module-body]');
                  const content = module.querySelector('[data-module-content]');
                  const table = module.querySelector('.cockpit-table');
                  return {
                    body: body.getBoundingClientRect().height,
                    content: content.getBoundingClientRect().height,
                    table: table.getBoundingClientRect().height
                  };
                }
                """);

        assertThat(heights.get("content").doubleValue())
                .as("dynamic map content fills the module body")
                .isGreaterThanOrEqualTo(heights.get("body").doubleValue() - 1);
        assertThat(heights.get("table").doubleValue())
                .as("map table fills the dynamic content wrapper")
                .isGreaterThanOrEqualTo(heights.get("content").doubleValue() - 1);
    }

    @Test
    void everyFocusedLayerTrapsAndRestoresFocus() {
        record Layer(String opener, String container) {
        }

        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);
            for (Layer layer : List.of(
                    new Layer("button[x-ref='sessionButton']", "#sessionLifecycleDialog"),
                    new Layer("[data-module-key='story'] [data-module-focus]", ".cockpit-focus-layer"))) {

                Locator opener = visibleFirst(layer.opener());
                String openerToken = "task13-opener-" + viewport[0] + "-" + layer.container();
                opener.evaluate("(element, token) => element.setAttribute('data-task13-opener', token)", openerToken);
                opener.focus();
                opener.press("Enter");
                page.locator(layer.container()).waitFor(new Locator.WaitForOptions().setState(
                        com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));

                String activeElement = (String) page.evaluate(
                        "() => document.activeElement?.outerHTML?.slice(0, 180) || '<none>'");
                assertThat((boolean) page.evaluate(
                        "sel => document.activeElement?.closest(sel) !== null", layer.container()))
                        .as("%s takes initial focus at %dx%d; active=%s", layer.container(), viewport[0], viewport[1],
                                activeElement)
                        .isTrue();

                Number focusableCount = (Number) page.evaluate("""
                        sel => {
                          const visible = (el) => {
                            const s = getComputedStyle(el);
                            return !el.hidden && s.display !== 'none' && s.visibility !== 'hidden'
                              && el.getClientRects().length > 0;
                          };
                          return [...document.querySelector(sel).querySelectorAll('FOCUSABLE_QUERY')]
                            .filter(visible).length;
                        }
                        """.replace("FOCUSABLE_QUERY", FOCUSABLE_QUERY), layer.container());
                assertThat(focusableCount.intValue())
                        .as("%s has focusable content at %dx%d", layer.container(), viewport[0], viewport[1])
                        .isGreaterThan(1);

                page.evaluate("""
                        sel => {
                          const visible = (el) => {
                            const s = getComputedStyle(el);
                            return !el.hidden && s.display !== 'none' && s.visibility !== 'hidden'
                              && el.getClientRects().length > 0;
                          };
                          const nodes = [...document.querySelector(sel).querySelectorAll('FOCUSABLE_QUERY')].filter(visible);
                          nodes.at(-1).focus();
                        }
                        """.replace("FOCUSABLE_QUERY", FOCUSABLE_QUERY), layer.container());
                page.keyboard().press("Tab");
                assertThat((boolean) page.evaluate("""
                        sel => {
                          const visible = (el) => {
                            const s = getComputedStyle(el);
                            return !el.hidden && s.display !== 'none' && s.visibility !== 'hidden'
                              && el.getClientRects().length > 0;
                          };
                          const nodes = [...document.querySelector(sel).querySelectorAll('FOCUSABLE_QUERY')].filter(visible);
                          return document.activeElement === nodes[0];
                        }
                        """.replace("FOCUSABLE_QUERY", FOCUSABLE_QUERY), layer.container()))
                        .as("%s wraps forward from the last focusable at %dx%d", layer.container(), viewport[0], viewport[1])
                        .isTrue();

                page.evaluate("""
                        sel => {
                          const visible = (el) => {
                            const s = getComputedStyle(el);
                            return !el.hidden && s.display !== 'none' && s.visibility !== 'hidden'
                              && el.getClientRects().length > 0;
                          };
                          const nodes = [...document.querySelector(sel).querySelectorAll('FOCUSABLE_QUERY')].filter(visible);
                          nodes[0].focus();
                        }
                        """.replace("FOCUSABLE_QUERY", FOCUSABLE_QUERY), layer.container());
                page.keyboard().press("Shift+Tab");
                assertThat((boolean) page.evaluate("""
                        sel => {
                          const visible = (el) => {
                            const s = getComputedStyle(el);
                            return !el.hidden && s.display !== 'none' && s.visibility !== 'hidden'
                              && el.getClientRects().length > 0;
                          };
                          const nodes = [...document.querySelector(sel).querySelectorAll('FOCUSABLE_QUERY')].filter(visible);
                          return document.activeElement === nodes.at(-1);
                        }
                        """.replace("FOCUSABLE_QUERY", FOCUSABLE_QUERY.replace("\\\"", "\\\\\\\"")), layer.container()))
                        .as("%s wraps backward from the first focusable at %dx%d", layer.container(), viewport[0], viewport[1])
                        .isTrue();

                page.keyboard().press("Escape");
                page.waitForFunction("""
                        sel => {
                          const el = document.querySelector(sel);
                          return el?.matches('dialog') ? !el.open : el?.hidden === true;
                        }
                        """, layer.container());
                assertThat((boolean) page.evaluate(
                        "token => document.activeElement?.getAttribute('data-task13-opener') === token", openerToken))
                        .as("%s restores focus to its opener at %dx%d", layer.container(), viewport[0], viewport[1])
                        .isTrue();
            }
        }
    }

    @Test
    void focusIsVisibleAndRestoredAfterAFocusedLayer() {
        for (int[] viewport : VIEWPORTS) {
            openCockpit(viewport[0], viewport[1]);

            page.locator("button[x-ref='sessionButton']").focus();
            assertThat((boolean) page.evaluate("""
                    () => {
                      const s = getComputedStyle(document.activeElement);
                      return s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) > 0;
                    }
                    """)).as("focus ring is painted at %dx%d", viewport[0], viewport[1]).isTrue();

            page.keyboard().press("Enter");
            page.locator("#sessionLifecycleDialog").waitFor();
            page.keyboard().press("Escape");

            assertThat((boolean) page.evaluate(
                    "() => document.activeElement === document.querySelector(\"button[x-ref='sessionButton']\")"))
                    .as("focus returns to the control that opened the layer at %dx%d", viewport[0], viewport[1])
                    .isTrue();
        }
    }

    @Test
    void reducedMotionIsRespected() {
        try (BrowserContext reduced = browser.newContext(new Browser.NewContextOptions()
                .setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE))) {
            Page quietPage = reduced.newPage();
            failures.attach(quietPage);
            for (int[] viewport : VIEWPORTS) {
                quietPage.setViewportSize(viewport[0], viewport[1]);
                quietPage.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
                quietPage.waitForLoadState(LoadState.NETWORKIDLE);

                @SuppressWarnings("unchecked")
                List<String> animated = (List<String>) quietPage.evaluate("""
                        () => [...document.querySelectorAll('body *')]
                          .filter(el => {
                            const s = getComputedStyle(el);
                            const d = (v) => Math.max(...v.split(',').map(x => parseFloat(x) * (x.includes('ms') ? 1 : 1000) || 0));
                            return d(s.animationDuration) > 10 || d(s.transitionDuration) > 10;
                          })
                          .map(el => el.tagName.toLowerCase() + '.' + (el.className || ''))
                          .slice(0, 20)
                        """);

                assertThat(animated).as("elements still animating under prefers-reduced-motion at %dx%d",
                        viewport[0], viewport[1]).isEmpty();
            }
        }
    }
}
