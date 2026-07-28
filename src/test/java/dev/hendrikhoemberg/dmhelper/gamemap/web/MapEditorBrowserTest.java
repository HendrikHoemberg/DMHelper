package dev.hendrikhoemberg.dmhelper.gamemap.web;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MapEditorBrowserTest {

    @LocalServerPort
    private int port;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private GameMapService gameMapService;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private BrowserFailureCollector failures;

    private Campaign campaign;
    private GameMap map;

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
        failures = new BrowserFailureCollector();
        context = browser.newContext();
        page = context.newPage();
        failures.attach(page);

        campaign = new Campaign();
        campaign.setName("Geometry Test Campaign");
        campaign = campaignRepository.save(campaign);

        map = gameMapService.create(campaign.getId(), "Test Map", 30, 20, 48);

        page.navigate("http://localhost:" + port + "/campaigns/" + campaign.getId() + "/maps/" + map.getId() + "/edit");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        failures.clear();
    }

    @AfterEach
    void closePage() {
        try {
            failures.assertNoFailures();
        } finally {
            if (context != null) context.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void geometryHelpersUseTheAuthoritativeGrid() {
        Map<String, Object> result = (Map<String, Object>) page.evaluate("""
                async () => {
                    const g = await import('/js/map/geometry.js');
                    return {
                        fit: g.fitInsideGeometry(30, 20, 1600, 900),
                        fill: g.fillCoverGeometry(30, 20, 1600, 900),
                        calibration: g.calibratedImageGeometry(
                            {x: 0, y: 0, width: 30, height: 20},
                            {x: 2, y: 3}, {x: 7, y: 3}, 5, 48)
                    };
                }
                """);

        Map<String, Object> fit = (Map<String, Object>) result.get("fit");
        assertThat(((Number) fit.get("x")).doubleValue()).isCloseTo(0.0, within(0.001));
        assertThat(((Number) fit.get("y")).doubleValue()).isCloseTo(1.5625, within(0.001));
        assertThat(((Number) fit.get("width")).doubleValue()).isCloseTo(30.0, within(0.001));
        assertThat(((Number) fit.get("height")).doubleValue()).isCloseTo(16.875, within(0.001));

        Map<String, Object> fill = (Map<String, Object>) result.get("fill");
        assertThat(((Number) fill.get("x")).doubleValue()).isCloseTo(-2.7777777778, within(0.001));
        assertThat(((Number) fill.get("y")).doubleValue()).isCloseTo(0.0, within(0.001));
        assertThat(((Number) fill.get("width")).doubleValue()).isCloseTo(35.5555555556, within(0.001));
        assertThat(((Number) fill.get("height")).doubleValue()).isCloseTo(20.0, within(0.001));

        Map<String, Object> calibration = (Map<String, Object>) result.get("calibration");
        assertThat(((Number) calibration.get("x")).doubleValue()).isCloseTo(0.0, within(0.001));
        assertThat(((Number) calibration.get("y")).doubleValue()).isCloseTo(0.0, within(0.001));
        assertThat(((Number) calibration.get("width")).doubleValue()).isCloseTo(30.0, within(0.001));
        assertThat(((Number) calibration.get("height")).doubleValue()).isCloseTo(20.0, within(0.001));
        assertThat(((Number) calibration.get("scale")).doubleValue()).isCloseTo(1.0, within(0.001));
    }

    @SuppressWarnings("unchecked")
    @Test
    void boundsImpactDetectsOutsideCellsAndAffectedShapes() {
        Map<String, Object> result = (Map<String, Object>) page.evaluate("""
                async () => {
                    const g = await import('/js/map/geometry.js');

                    const emptyDoc = { layers: [] };
                    const emptyResult = g.boundsImpact(emptyDoc, 10, 10);

                    const doc = {
                        layers: [
                            {
                                id: 'terrain',
                                cells: [
                                    { col: 0, row: 0, terrain: 'floor' },
                                    { col: 5, row: 5, terrain: 'wall' },
                                    { col: 9, row: 9, terrain: 'floor' },
                                    { col: -1, row: 3, terrain: 'floor' },
                                    { col: 3, row: -2, terrain: 'wall' },
                                    { col: 10, row: 4, terrain: 'floor' },
                                    { col: 4, row: 10, terrain: 'floor' },
                                ],
                                shapes: [
                                    { type: 'rect', points: [1, 1, 3, 3] },
                                    { type: 'rect', points: [-1, 2, 4, 3] },
                                    { type: 'circle', points: [12, 12, 2] },
                                    { type: 'line', points: [] },
                                    { type: 'rect', points: [1] },
                                ],
                            },
                        ],
                    };

                    const impact = g.boundsImpact(doc, 10, 10);

                    return {
                        emptyOutside: emptyResult.outsideCells.length,
                        emptyShapes: emptyResult.affectedShapes.length,
                        outsideCount: impact.outsideCells.length,
                        affectedCount: impact.affectedShapes.length,
                    };
                }
                """);

        assertThat(((Number) result.get("emptyOutside")).intValue()).isEqualTo(0);
        assertThat(((Number) result.get("emptyShapes")).intValue()).isEqualTo(0);
        assertThat(((Number) result.get("outsideCount")).intValue()).isEqualTo(4);
        assertThat(((Number) result.get("affectedCount")).intValue()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void gridSettingsPreviewAndApplyAreAtomic() {
        page.evaluate("""
                () => {
                    const ed = window.mapEditor;
                    if (!ed || !ed.document) return;
                    const tl = ed.document.layers.find(l => l.id === 'terrain');
                    if (tl) tl.cells = [{col: 15, row: 15, terrain: 'floor'}];
                    const ol = ed.document.layers.find(l => l.id === 'objects');
                    if (ol) ol.shapes = [{type: 'rect', points: [20, 5, 5, 5], fill: 'rgba(255,0,0,0.5)', stroke: '#000', strokeWidth: 2}];
                    ed.renderDocument();
                }
                """);

        Map<String, Object> impact = (Map<String, Object>) page.evaluate("""
                () => window.mapEditor.previewGridResize(10, 10)
                """);

        assertThat(((List<?>) impact.get("outsideCells"))).hasSize(1);
        assertThat(((List<?>) impact.get("affectedShapes"))).hasSize(1);
        assertThat(((List<?>) impact.get("affectedTokens"))).isEmpty();

        page.waitForResponse(
                resp -> resp.url().contains("/api/v1/maps/" + map.getId() + "/settings") && "PUT".equals(resp.request().method()),
                () -> page.evaluate("""
                        () => window.mapEditor.applyGridSettings({
                            width: 40, height: 30, cellSizePx: 64,
                            resizeMode: 'PRESERVE',
                            tokenResolutions: []
                        })
                        """)
        );

        Map<String, Object> state = (Map<String, Object>) page.evaluate("""
                () => ({ gw: window.mapEditor.gridWidth, gh: window.mapEditor.gridHeight, cs: window.mapEditor.cellSizePx })
                """);

        assertThat(((Number) state.get("gw")).intValue()).isEqualTo(40);
        assertThat(((Number) state.get("gh")).intValue()).isEqualTo(30);
        assertThat(((Number) state.get("cs")).intValue()).isEqualTo(64);
    }
}
