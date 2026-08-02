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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 15: every overlay level traps focus where blocking, closes on Escape, and
 * restores focus to its trigger. The session cockpit is the one full page that renders
 * mid-stage (the campaign pages still reference the pre-shell layout); ui-overlay.js is
 * not yet wired into any head fragment (Task 14 does that), so the gate loads the artifact
 * it is testing itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class OverlayBehaviorGateTest {

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
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720));
        page = context.newPage();
        failures.attach(page);
        page.navigate("http://localhost:" + port + "/campaigns/" + seeded.campaignId() + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        if (!(Boolean) page.evaluate("() => Boolean(window.dmOverlay)")) {
            page.addScriptTag(new Page.AddScriptTagOptions().setUrl("/js/ui-overlay.js"));
        }
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    @Test
    void escapeClosesADialogAndRestoresFocusToItsTrigger() {
        page.evaluate("""
                () => {
                  const t = document.createElement('button');
                  t.id = 'overlayTrigger';
                  t.textContent = 'Open';
                  document.querySelector('main').prepend(t);
                  const d = document.createElement('div');
                  d.className = 'dialog';
                  d.id = 'probeDialog';
                  d.setAttribute('role', 'dialog');
                  d.setAttribute('aria-modal', 'true');
                  d.hidden = true;
                  d.innerHTML = '<div class="dialog__panel"><button id="inside">Inside</button></div>';
                  document.body.appendChild(d);
                  t.addEventListener('click', () => window.dmOverlay.open(d));
                }
                """);
        page.click("#overlayTrigger");
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("inside");
        page.keyboard().press("Escape");
        assertThat(page.evaluate("() => document.getElementById('probeDialog').hidden")).isEqualTo(true);
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("overlayTrigger");
    }

    @Test
    void tabWrapsInsideABlockingDialog() {
        page.evaluate("""
                () => {
                  const d = document.createElement('div');
                  d.className = 'dialog';
                  d.setAttribute('role', 'dialog');
                  d.setAttribute('aria-modal', 'true');
                  d.hidden = true;
                  d.innerHTML = '<div class="dialog__panel">'
                    + '<button id="a">A</button><button id="b">B</button></div>';
                  document.body.appendChild(d);
                  window.dmOverlay.open(d);
                }
                """);
        page.keyboard().press("Tab");
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("b");
        page.keyboard().press("Tab");
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("a");
    }

    @Test
    void aComplementarySideSheetOpensInPlace() {
        page.evaluate("""
                () => {
                  const s = document.createElement('aside');
                  s.className = 'side-sheet';
                  s.setAttribute('role', 'complementary');
                  s.setAttribute('aria-labelledby', 'probeSheetTitle');
                  s.hidden = true;
                  s.innerHTML = '<div class="side-sheet__body"><button id="sheetItem">Item</button></div>';
                  document.body.appendChild(s);
                  window.dmOverlay.open(s);
                }
                """);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> result = (java.util.Map<String, Object>) page.evaluate("""
                () => {
                  const s = document.querySelector('.side-sheet[role="complementary"]');
                  const rect = s.getBoundingClientRect();
                  return {
                    hidden: s.hidden,
                    transform: getComputedStyle(s).transform,
                    left: Math.round(rect.left),
                    width: Math.round(rect.width),
                    viewport: window.innerWidth
                  };
                }
                """);
        assertThat(result.get("hidden")).isEqualTo(false);
        assertThat(result.get("transform"))
                .as("shared side-sheet must not inherit the legacy translateX(100%)")
                .isIn("none", "matrix(1, 0, 0, 1, 0, 0)");
        assertThat(((Number) result.get("width")).intValue()).isGreaterThan(0);
        assertThat(((Number) result.get("left")).intValue())
                .as("sheet must be on screen, not pushed off the right edge")
                .isGreaterThanOrEqualTo(0);
        assertThat(((Number) result.get("left")).intValue())
                .isLessThan(((Number) result.get("viewport")).intValue());
    }

    @Test
    void closingATopDialogRestoresTheTrapToTheDialogBeneath() {
        page.evaluate("""
                () => {
                  const a = document.createElement('div');
                  a.className = 'dialog'; a.id = 'a';
                  a.setAttribute('role', 'dialog'); a.setAttribute('aria-modal', 'true');
                  a.hidden = true;
                  a.innerHTML = '<div class="dialog__panel"><button id="aBtn">A</button></div>';
                  document.body.appendChild(a);
                  const b = document.createElement('div');
                  b.className = 'dialog'; b.id = 'b';
                  b.setAttribute('role', 'dialog'); b.setAttribute('aria-modal', 'true');
                  b.hidden = true;
                  b.innerHTML = '<div class="dialog__panel"><button id="bBtn">B</button></div>';
                  document.body.appendChild(b);
                  window.dmOverlay.open(a);
                  window.dmOverlay.open(b);
                }
                """);
        assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo("bBtn");
        page.keyboard().press("Escape");
        assertThat(page.evaluate("() => document.getElementById('b').hidden")).isEqualTo(true);
        assertThat(page.evaluate("() => document.getElementById('a').hidden")).isEqualTo(false);
        assertThat(page.evaluate("() => document.activeElement.id"))
                .as("focus returns to the top dialog's trigger, which lives inside the "
                        + "dialog beneath")
                .isEqualTo("aBtn");
        page.keyboard().press("Tab");
        assertThat(page.evaluate("() => document.activeElement.id"))
                .as("the trap follows the remaining dialog, not the page")
                .isEqualTo("aBtn");
    }
}
