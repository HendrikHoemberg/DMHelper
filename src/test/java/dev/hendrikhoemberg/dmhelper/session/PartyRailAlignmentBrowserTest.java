package dev.hendrikhoemberg.dmhelper.session;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartyRailAlignmentBrowserTest {

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
        page.setViewportSize(1600, 1000);
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

    @Test
    void everyPartyChipSharesTheSameLeftEdgeInTheNarrowRail() {
        UUID campaignId = fixtures.campaignWithRunningSessionAndFourPartyMembers();

        page.navigate("http://127.0.0.1:" + port + "/campaigns/" + campaignId + "/session");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForSelector(".runtime-party .party-member-chip");

        var chips = page.locator(".runtime-party .party-member-chip").all();
        assertThat(chips).as("fixture must render four chips").hasSize(4);

        double firstLeft = chips.getFirst().boundingBox().x;
        for (int i = 1; i < chips.size(); i++) {
            BoundingBox box = chips.get(i).boundingBox();
            assertThat(box.x)
                    .as("party chip %d must align with the first chip", i)
                    .isCloseTo(firstLeft, org.assertj.core.data.Offset.offset(1.0));
        }
    }
}
