package dev.hendrikhoemberg.dmhelper.gamemap.web;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.FilePayload;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

        page.evaluate("() => window.mapEditor.undo()");
        Map<String, Object> undone = (Map<String, Object>) page.evaluate("""
                () => ({ gw: window.mapEditor.gridWidth, gh: window.mapEditor.gridHeight, cs: window.mapEditor.cellSizePx })
                """);
        assertThat(((Number) undone.get("gw")).intValue()).isEqualTo(30);
        assertThat(((Number) undone.get("gh")).intValue()).isEqualTo(20);
        assertThat(((Number) undone.get("cs")).intValue()).isEqualTo(48);

        page.evaluate("() => window.mapEditor.redo()");
        Map<String, Object> redone = (Map<String, Object>) page.evaluate("""
                () => ({ gw: window.mapEditor.gridWidth, gh: window.mapEditor.gridHeight, cs: window.mapEditor.cellSizePx })
                """);
        assertThat(((Number) redone.get("gw")).intValue()).isEqualTo(40);
        assertThat(((Number) redone.get("gh")).intValue()).isEqualTo(30);
        assertThat(((Number) redone.get("cs")).intValue()).isEqualTo(64);
    }

    @SuppressWarnings("unchecked")
    @Test
    void settingsAndResizeConfirmationAreDiscoverable() {
        assertThat(page.locator("[data-map-control=\"map-section\"]").isVisible()).isTrue();
        assertThat(page.locator("[data-image-control=\"background-section\"]").isVisible()).isFalse();

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

        page.evaluate("() => Alpine.$data(document.querySelector('[x-data]')).gridWidth = 10");
        page.locator("[data-map-control=\"resize-canvas-btn\"]").click();
        page.waitForTimeout(100);

        assertThat(page.locator("[data-map-control=\"resize-dialog\"]").isVisible()).isTrue();
        assertThat(page.locator("[data-map-control=\"resize-affected-cells\"]").textContent()).contains("1");
        assertThat(page.locator("[data-map-control=\"resize-affected-shapes\"]").textContent()).contains("1");

        page.locator("[data-map-control=\"resize-cancel-btn\"]").click();
        page.waitForTimeout(100);
        assertThat(page.locator("[data-map-control=\"resize-dialog\"]").isVisible()).isFalse();
        assertThat(page.locator("[data-map-control=\"grid-width\"]").inputValue()).isEqualTo("30");

        page.evaluate("() => Alpine.$data(document.querySelector('[x-data]')).gridWidth = 40");
        page.evaluate("() => Alpine.$data(document.querySelector('[x-data]')).gridHeight = 30");
        page.evaluate("() => Alpine.$data(document.querySelector('[x-data]')).cellSizePx = 64");
        page.locator("[data-map-control=\"resize-canvas-btn\"]").click();

        page.waitForResponse(
                resp -> resp.url().contains("/api/v1/maps/" + map.getId() + "/settings") && "PUT".equals(resp.request().method()),
                () -> page.locator("[data-map-control=\"resize-confirm-btn\"]").click()
        );

        Map<String, Object> state = (Map<String, Object>) page.evaluate("""
                () => ({ w: window.mapEditor.gridWidth, h: window.mapEditor.gridHeight, cs: window.mapEditor.cellSizePx })
                """);
        assertThat(((Number) state.get("w")).intValue()).isEqualTo(40);
        assertThat(((Number) state.get("h")).intValue()).isEqualTo(30);
        assertThat(((Number) state.get("cs")).intValue()).isEqualTo(64);
    }

    @SuppressWarnings("unchecked")
    @Test
    void imageImportSelectAndResize() throws Exception {
        byte[] pngBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(img, "PNG", baos);
            pngBytes = baos.toByteArray();
        }

        page.setInputFiles("input[type=\"file\"]",
                new FilePayload("test.png", "image/png", pngBytes));

        page.waitForFunction("""
                () => window.mapEditor?.document?.layers?.some(
                    l => l.type === 'IMAGE' && l.image != null)
                """);

        page.evaluate("() => Alpine.$data(document.querySelector('[x-data]')).setLayer('image')");
        page.waitForTimeout(100);

        assertThat(page.locator("[data-image-control=\"background-section\"]").isVisible()).isTrue();

        page.waitForResponse(
                resp -> resp.url().contains("/api/v1/maps/" + map.getId() + "/document")
                        && "PUT".equals(resp.request().method()),
                () -> page.evaluate("""
                        () => {
                            const img = window.mapEditor.document.layers
                                .find(l => l.type === 'IMAGE').image;
                            img.width = 15.0;
                            img.height = 15.0;
                            window.mapEditor.markDirty();
                            window.mapEditor.save();
                        }
                        """)
        );

        Number docWidth = (Number) page.evaluate("""
                () => window.mapEditor.document.layers
                    .find(l => l.type === 'IMAGE').image.width
                """);
        assertThat(docWidth.doubleValue()).isCloseTo(15.0, within(0.01));
    }

    @SuppressWarnings("unchecked")
    @Test
    void importedImageHasCorrectFitGeometry() throws Exception {
        byte[] pngBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(img, "PNG", baos);
            pngBytes = baos.toByteArray();
        }

        page.setInputFiles("input[type=\"file\"]",
                new FilePayload("test.png", "image/png", pngBytes));

        page.waitForFunction("""
                () => window.mapEditor?.document?.layers?.some(
                    l => l.type === 'IMAGE' && l.image != null)
                """);

        Map<String, Object> imageState = (Map<String, Object>) page.evaluate("""
                () => {
                    const i = window.mapEditor.document.layers.find(l => l.type === 'IMAGE').image;
                    return {x: i.x, y: i.y, w: i.width, h: i.height,
                            gw: window.mapEditor.gridWidth, gh: window.mapEditor.gridHeight};
                }
                """);

        assertThat(((Number) imageState.get("x")).doubleValue()).isGreaterThanOrEqualTo(0);
        assertThat(((Number) imageState.get("y")).doubleValue()).isGreaterThanOrEqualTo(0);
        double right = ((Number) imageState.get("x")).doubleValue() + ((Number) imageState.get("w")).doubleValue();
        double bottom = ((Number) imageState.get("y")).doubleValue() + ((Number) imageState.get("h")).doubleValue();
        assertThat(right).isLessThanOrEqualTo(((Number) imageState.get("gw")).doubleValue() + 0.001);
        assertThat(bottom).isLessThanOrEqualTo(((Number) imageState.get("gh")).doubleValue() + 0.001);

        assertThat(page.locator("[data-image-control=\"background-section\"]").isVisible()).isTrue();

        page.evaluate("""
                () => {
                    const img = window.mapEditor.document.layers
                        .find(l => l.type === 'IMAGE').image;
                    img.rotationDeg = 45;
                    img.locked = true;
                }
                """);

        Map<String, Object> docState = (Map<String, Object>) page.evaluate("""
                () => {
                    const i = window.mapEditor.document.layers
                        .find(l => l.type === 'IMAGE').image;
                    return {r: i.rotationDeg, l: i.locked};
                }
                """);
        assertThat(((Number) docState.get("r")).doubleValue()).isCloseTo(45.0, within(0.01));
        assertThat((Boolean) docState.get("l")).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void persistenceAfterReload() throws Exception {
        byte[] pngBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(img, "PNG", baos);
            pngBytes = baos.toByteArray();
        }

        page.setInputFiles("input[type=\"file\"]",
                new FilePayload("test.png", "image/png", pngBytes));

        page.waitForFunction("""
                () => window.mapEditor?.document?.layers?.some(
                    l => l.type === 'IMAGE' && l.image != null)
                """);

        page.evaluate("""
                () => {
                    const i = window.mapEditor.document.layers.find(l => l.type === 'IMAGE').image;
                    i.rotationDeg = 45;
                    i.locked = true;
                    i.width = 18;
                }
                """);
        page.evaluate("() => window.mapEditor.markDirty()");

        page.waitForFunction("""
                () => document.getElementById('saveIndicator')
                    ?.textContent === 'Saved'
                """);

        page.navigate("http://localhost:" + port + "/campaigns/" + campaign.getId() + "/maps");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        String cardText = page.locator(".card-meta span").first().textContent();
        assertThat(cardText).contains("30×20");

        page.navigate("http://localhost:" + port + "/campaigns/" + campaign.getId() + "/maps/" + map.getId() + "/edit");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        failures.clear();

        page.waitForFunction("() => window.mapEditor?.document?.grid != null");

        Map<String, Object> gridState = (Map<String, Object>) page.evaluate("""
                () => ({w: window.mapEditor.gridWidth, h: window.mapEditor.gridHeight,
                        cs: window.mapEditor.cellSizePx})
                """);
        assertThat(((Number) gridState.get("w")).intValue()).isEqualTo(30);
        assertThat(((Number) gridState.get("h")).intValue()).isEqualTo(20);
        assertThat(((Number) gridState.get("cs")).intValue()).isEqualTo(48);

        Map<String, Object> imageState = (Map<String, Object>) page.evaluate("""
                () => {
                    const i = window.mapEditor.document.layers.find(l => l.type === 'IMAGE')?.image;
                    return i ? {w: i.width, r: i.rotationDeg, l: i.locked} : null;
                }
                """);
        assertThat(imageState).isNotNull();
        assertThat(((Number) imageState.get("w")).doubleValue()).isCloseTo(18.0, within(0.01));
        assertThat(((Number) imageState.get("r")).doubleValue()).isCloseTo(45.0, within(0.01));
        assertThat((Boolean) imageState.get("l")).isTrue();
    }

    @Test
    void safeFailureOnSettingsConflict() {
        failures.expectHttpFailure("PUT",
                java.util.regex.Pattern.compile(".*/api/v1/maps/" + map.getId() + "/settings"), 409);
        failures.expectConsoleError(
                java.util.regex.Pattern.compile(".*Settings save failed.*"));

        page.route("**/api/v1/maps/" + map.getId() + "/settings", route -> {
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(409)
                    .setContentType("application/json")
                    .setBody("{\"error\":\"Conflict\"}"));
        });

        page.evaluate("""
                () => {
                    const ed = window.mapEditor;
                    if (!ed || !ed.document) return;
                    const tl = ed.document.layers.find(l => l.id === 'terrain');
                    if (tl) tl.cells = [{col: 15, row: 15, terrain: 'floor'}];
                    ed.renderDocument();
                }
                """);

        page.evaluate("() => Alpine.$data(document.querySelector('[x-data]')).gridWidth = 10");
        page.locator("[data-map-control=\"resize-canvas-btn\"]").click();
        page.waitForTimeout(200);

        assertThat(page.locator("[data-map-control=\"resize-dialog\"]").isVisible()).isTrue();

        page.waitForResponse(
                resp -> resp.url().contains("/api/v1/maps/" + map.getId() + "/settings")
                        && "PUT".equals(resp.request().method()),
                () -> page.locator("[data-map-control=\"resize-confirm-btn\"]").click()
        );

        assertThat(page.locator("[data-map-control=\"resize-dialog\"]").isVisible()).isTrue();
        String dialogText = page.locator("[data-map-control=\"resize-dialog\"]").textContent();
        assertThat(dialogText).contains("409");
    }

    @Test
    void invalidImageFileDoesNotCreateImageLayer() {
        page.setInputFiles("input[type=\"file\"]",
                new FilePayload("test.txt", "text/plain", "not an image".getBytes()));

        page.waitForTimeout(500);

        Boolean hasImageLayer = (Boolean) page.evaluate("""
                () => window.mapEditor?.document?.layers?.some(
                    l => l.type === 'IMAGE' && l.image != null) === true
                """);
        assertThat(hasImageLayer).isFalse();
    }
}
