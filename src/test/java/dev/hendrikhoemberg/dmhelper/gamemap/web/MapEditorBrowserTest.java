package dev.hendrikhoemberg.dmhelper.gamemap.web;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.LoadState;
import dev.hendrikhoemberg.dmhelper.BrowserFailureCollector;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("playwright")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("browser")
class MapEditorBrowserTest {

    @LocalServerPort
    private int port;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private GameMapService gameMapService;

    @Autowired
    private TokenRepository tokenRepository;

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

    @Test
    void onlySectionsRelevantToTheActiveToolArePromoted() {
        page.click("[data-tool='brush']");
        assertThat(page.getAttribute("[data-inspector-section='palette']", "data-relevance"))
                .isEqualTo("primary");
        assertThat(page.getAttribute("[data-inspector-section='selection']", "data-relevance"))
                .isEqualTo("secondary");

        page.click("[data-tool='select']");
        assertThat(page.getAttribute("[data-inspector-section='selection']", "data-relevance"))
                .isEqualTo("primary");
        assertThat(page.getAttribute("[data-inspector-section='palette']", "data-relevance"))
                .isEqualTo("secondary");
    }

    @Test
    void keyboardShortcutKeepsInspectorContextInSync() {
        page.click("[data-tool='brush']");
        assertThat(page.getAttribute("[data-inspector-section='palette']", "data-relevance"))
                .isEqualTo("primary");

        page.keyboard().press("v");
        assertThat(page.getAttribute("[data-inspector-section='selection']", "data-relevance"))
                .isEqualTo("primary");
        assertThat(page.getAttribute("[data-inspector-section='palette']", "data-relevance"))
                .isEqualTo("secondary");
    }

    @Test
    void everySectionRemainsReachableEvenWhenSecondary() {
        page.click("[data-tool='brush']");
        for (String section : java.util.List.of("tool", "palette", "selection", "layers",
                "map", "pins")) {
            assertThat(page.isVisible("[data-inspector-section='" + section + "']"))
                    .as("section %s stays reachable", section).isTrue();
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
        expandInspectorSection("map");
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

        page.evaluate("() => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).gridWidth = 10");
        page.locator("[data-map-control=\"resize-canvas-btn\"]").click();
        page.waitForTimeout(100);

        assertThat(page.locator("[data-map-control=\"resize-dialog\"]").isVisible()).isTrue();
        assertThat(page.locator("[data-map-control=\"resize-affected-cells\"]").textContent()).contains("1");
        assertThat(page.locator("[data-map-control=\"resize-affected-shapes\"]").textContent()).contains("1");

        page.locator("[data-map-control=\"resize-cancel-btn\"]").click();
        page.waitForTimeout(100);
        assertThat(page.locator("[data-map-control=\"resize-dialog\"]").isVisible()).isFalse();
        assertThat(page.locator("[data-map-control=\"grid-width\"]").inputValue()).isEqualTo("30");

        page.evaluate("() => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).gridWidth = 40");
        page.evaluate("() => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).gridHeight = 30");
        page.evaluate("() => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).cellSizePx = 64");
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
        expandInspectorSection("map");
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

        page.evaluate("() => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).setLayer('image')");
        page.waitForTimeout(100);

        assertThat(page.locator("[data-image-control=\"background-section\"]").isVisible()).isTrue();
        assertThat((Number) page.evaluate(
                "() => window.mapEditor.transformer.nodes().length")).isEqualTo(1);

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
    void runtimeRendererIncludesBackgroundAndFiltersPrivatePlayerLayers() {
        Map<String, Object> result = (Map<String, Object>) page.evaluate("""
                async () => {
                    const { renderRuntimeDocument } = await import('/js/map/runtime-renderer.js');
                    const dataUrl = 'data:image/svg+xml;base64,' + btoa(
                        '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">'
                        + '<rect width="16" height="16" fill="red"/></svg>');
                    const document = {
                        customTerrain: [{ key: 'moss', name: 'Moss', fill: '#123456' }],
                        primitives: [
                            { type: 'DOOR', startCol: 2, startRow: 2, endCol: 2, endRow: 2,
                              playerVisible: false },
                            { type: 'DOOR', startCol: 3, startRow: 3, endCol: 3, endRow: 3,
                              playerVisible: true }
                        ],
                        layers: [
                            { id: 'image', type: 'IMAGE', visible: true, playerVisible: true,
                              image: { dataUrl, x: 0, y: 0, width: 4, height: 4, rotationDeg: 0 } },
                            { id: 'terrain', type: 'TERRAIN', visible: true, playerVisible: true,
                              cells: [{ col: 0, row: 0, terrain: 'moss' }], shapes: [] },
                            { id: 'objects', type: 'OBJECTS', visible: true, playerVisible: false,
                              cells: [], shapes: [{ type: 'rect', points: [0, 0, 1, 1],
                                                   fill: '#111111', stroke: '#ffffff' }] },
                            { id: 'annotations', type: 'ANNOTATIONS', visible: true, playerVisible: true,
                              cells: [], shapes: [{ type: 'line', points: [0, 0, 2, 2],
                                                   stroke: '#abcdef' }] }
                        ]
                    };
                    const dm = new Konva.Layer();
                    const player = new Konva.Layer();
                    window.mapEditor.stage.add(dm);
                    window.mapEditor.stage.add(player);
                    await renderRuntimeDocument({
                        Konva, document, targetLayer: dm, gridWidth: 4, gridHeight: 4,
                        cellSizePx: 32, playerView: false
                    });
                    await renderRuntimeDocument({
                        Konva, document, targetLayer: player, gridWidth: 4, gridHeight: 4,
                        cellSizePx: 32, playerView: true
                    });
                    const count = (layer, kind) => layer.find(
                        node => node.getAttr('_runtimeKind') === kind).length;
                    return {
                        dmImages: count(dm, 'image'),
                        dmShapes: count(dm, 'shape'),
                        playerImages: count(player, 'image'),
                        playerShapes: count(player, 'shape'),
                        dmTerrain: count(dm, 'terrain'),
                        playerTerrain: count(player, 'terrain'),
                        customTerrainRendered: dm.find(
                            node => node.getAttr('_runtimeKind') === 'terrain')
                            .some(node => node.fill() === '#123456')
                    };
                }
                """);

        assertThat(((Number) result.get("dmImages")).intValue()).isEqualTo(1);
        assertThat(((Number) result.get("dmShapes")).intValue()).isEqualTo(2);
        assertThat(((Number) result.get("playerImages")).intValue()).isEqualTo(1);
        assertThat(((Number) result.get("playerShapes")).intValue()).isEqualTo(1);
        assertThat(((Number) result.get("dmTerrain")).intValue()).isEqualTo(3);
        assertThat(((Number) result.get("playerTerrain")).intValue()).isEqualTo(2);
        assertThat(result.get("customTerrainRendered")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void importedImageHasCorrectFitGeometry() throws Exception {
        expandInspectorSection("map");
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

        page.evaluate("() => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).gridWidth = 10");
        expandInspectorSection("map");
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

    @SuppressWarnings("unchecked")
    @Test
    void imageCommandsMutateRealDocumentAndCalibrationMetadata() throws Exception {
        importSquarePng();

        Map<String, Object> state = (Map<String, Object>) page.evaluate("""
                async () => {
                    const ed = window.mapEditor;
                    ed.updateImageField('width', 10, true);
                    let image = ed.document.layers.find(l => l.type === 'IMAGE').image;
                    const numeric = {width: image.width, height: image.height};
                    ed.fitBackgroundImage('FILL_COVER');
                    image = ed.document.layers.find(l => l.type === 'IMAGE').image;
                    const fill = {x: image.x, y: image.y, width: image.width, height: image.height};
                    await ed.resetBackgroundImage();
                    ed.rotateImage(90);
                    ed.setImageLocked(true);
                    ed.calibrateBackgroundImage(5, 5, 10, 5, 10);
                    image = ed.document.layers.find(l => l.type === 'IMAGE').image;
                    await ed.save();
                    return {
                        numeric,
                        fill,
                        final: {
                            rotation: image.rotationDeg,
                            locked: image.locked,
                            width: image.width,
                            calibration: image.calibration
                        }
                    };
                }
                """);

        Map<String, Object> numeric = (Map<String, Object>) state.get("numeric");
        assertThat(((Number) numeric.get("width")).doubleValue()).isEqualTo(10);
        assertThat(((Number) numeric.get("height")).doubleValue()).isEqualTo(10);
        Map<String, Object> fill = (Map<String, Object>) state.get("fill");
        assertThat(((Number) fill.get("width")).doubleValue()).isEqualTo(30);
        assertThat(((Number) fill.get("height")).doubleValue()).isEqualTo(30);
        Map<String, Object> finalImage = (Map<String, Object>) state.get("final");
        assertThat(((Number) finalImage.get("rotation")).doubleValue()).isEqualTo(90);
        assertThat((Boolean) finalImage.get("locked")).isTrue();
        assertThat(((Number) finalImage.get("width")).doubleValue()).isEqualTo(40);
        Map<String, Object> calibration = (Map<String, Object>) finalImage.get("calibration");
        assertThat(((Number) calibration.get("cellsBetween")).doubleValue()).isEqualTo(10);
    }

    @Test
    void settingsSaveFlushesDirtyDocumentBeforeResizing() {
        page.evaluate("""
                async () => {
                    const ed = window.mapEditor;
                    const terrain = ed.document.layers.find(l => l.id === 'terrain');
                    terrain.cells = [{col: 2, row: 3, terrain: 'wall'}];
                    ed.renderDocument();
                    ed.markDirty();
                    await ed.applyGridSettings({
                        width: 40, height: 30, cellSizePx: 64,
                        resizeMode: 'PRESERVE', tokenResolutions: []
                    });
                }
                """);

        page.reload();
        page.waitForFunction("() => window.mapEditor?.document?.grid?.width === 40");

        Boolean preserved = (Boolean) page.evaluate("""
                () => window.mapEditor.document.layers.find(l => l.id === 'terrain')
                    .cells.some(c => c.col === 2 && c.row === 3 && c.terrain === 'wall')
                """);
        assertThat(preserved).isTrue();
    }

    @Test
    void settingsWaitsForDocumentSaveAlreadyInFlight() {
        page.route("**/api/v1/maps/" + map.getId() + "/document?expectedVersion=*", route -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(interrupted);
            }
            route.resume();
        });

        page.evaluate("""
                async () => {
                    const ed = window.mapEditor;
                    const terrain = ed.document.layers.find(l => l.id === 'terrain');
                    terrain.cells = [{col: 2, row: 3, terrain: 'wall'}];
                    ed.renderDocument();
                    ed.markDirty();
                    const saving = ed.save();
                    await ed.applyGridSettings({
                        width: 40, height: 30, cellSizePx: 64,
                        resizeMode: 'PRESERVE', tokenResolutions: []
                    });
                    await saving;
                }
                """);

        page.reload();
        page.waitForFunction("() => window.mapEditor?.document?.grid?.width === 40");
        Boolean preserved = (Boolean) page.evaluate("""
                () => window.mapEditor.document.layers.find(l => l.id === 'terrain')
                    .cells.some(c => c.col === 2 && c.row === 3)
                """);
        assertThat(preserved).isTrue();
    }

    @Test
    void settingsRequestLocksEditorAgainstConcurrentCanvasEdits() {
        page.evaluate("""
                () => {
                    const originalFetch = window.fetch.bind(window);
                    window.fetch = (...args) => String(args[0]).endsWith('/settings')
                        ? new Promise(resolve => setTimeout(
                            () => resolve(originalFetch(...args)), 300))
                        : originalFetch(...args);
                    window.pendingGridSettings = window.mapEditor.applyGridSettings({
                        width: 40, height: 30, cellSizePx: 48,
                        resizeMode: 'PRESERVE', tokenResolutions: []
                    });
                }
                """);
        page.waitForFunction("() => window.mapEditor.settingsInFlight");
        assertThat((Boolean) page.evaluate(
                "() => document.querySelector('.editor-container').inert")).isTrue();

        var canvas = page.locator(".konvajs-content").boundingBox();
        page.mouse().click(canvas.x + 100, canvas.y + 100);
        page.evaluate("() => window.pendingGridSettings");

        page.reload();
        page.waitForFunction("() => window.mapEditor?.document?.grid?.width === 40");
        Number cellCount = (Number) page.evaluate("""
                () => window.mapEditor.document.layers
                    .flatMap(layer => layer.cells || []).length
                """);
        assertThat(cellCount.intValue()).isZero();
    }

    @Test
    void unloadDoesNotStartCompetingDocumentSaveWhileSettingsAreInFlight() {
        Number documentSaves = (Number) page.evaluate("""
                () => {
                    const ed = window.mapEditor;
                    let saves = 0;
                    const originalFetch = window.fetch;
                    window.fetch = (...args) => {
                        if (String(args[0]).includes('/document?expectedVersion=')) saves++;
                        return originalFetch(...args);
                    };
                    ed.settingsInFlight = true;
                    ed.dirty = true;
                    ed.flushSave();
                    window.dispatchEvent(new Event('beforeunload', { cancelable: true }));
                    ed.settingsInFlight = false;
                    return saves;
                }
                """);
        assertThat(documentSaves.intValue()).isZero();
    }

    @Test
    void undoOfGridSettingsPersistsMetadataAndDocumentTogether() {
        page.evaluate("""
                async () => {
                    const ed = window.mapEditor;
                    await ed.applyGridSettings({
                        width: 40, height: 30, cellSizePx: 64,
                        resizeMode: 'PRESERVE', tokenResolutions: []
                    });
                    await ed.undo();
                }
                """);

        page.reload();
        page.waitForFunction("() => window.mapEditor?.document?.grid != null");

        Map<String, Object> grids = (Map<String, Object>) page.evaluate("""
                async () => {
                    const map = await (await fetch(`/api/v1/maps/${window.mapEditor.mapId}`)).json();
                    const document = window.mapEditor.document.grid;
                    return {
                        mapWidth: map.gridWidth, mapHeight: map.gridHeight, mapCell: map.cellSizePx,
                        docWidth: document.width, docHeight: document.height, docCell: document.cellSizePx
                    };
                }
                """);
        assertThat(grids).containsEntry("mapWidth", 30)
                .containsEntry("mapHeight", 20)
                .containsEntry("mapCell", 48)
                .containsEntry("docWidth", 30)
                .containsEntry("docHeight", 20)
                .containsEntry("docCell", 48);
    }

    @SuppressWarnings("unchecked")
    @Test
    void pngExportUsesAuthoritativeMapBounds() {
        Map<String, Object> options = (Map<String, Object>) page.evaluate("""
                () => {
                    const ed = window.mapEditor;
                    let captured;
                    ed.stage.toDataURL = options => {
                        captured = options;
                        return 'data:image/png;base64,AAAA';
                    };
                    ed.triggerDownload = () => {};
                    ed.exportPng();
                    return captured;
                }
                """);

        assertThat(options).containsEntry("x", 0)
                .containsEntry("y", 0)
                .containsEntry("width", 30 * 48)
                .containsEntry("height", 20 * 48)
                .containsEntry("pixelRatio", 2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shrinkingPreviewRequiresExplicitResolutionForAffectedToken() {
        Token token = new Token();
        token.setMap(map);
        token.setName("Outside");
        token.setPositionX(19 * 48);
        token.setPositionY(3 * 48);
        token.setSizeCols(1);
        token.setSizeRows(1);
        token = tokenRepository.saveAndFlush(token);

        page.evaluate("() => window.mapEditor.fetchTokens()");
        page.waitForFunction("() => window.mapEditor.tokenSnapshot.length === 1");
        page.evaluate("() => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).gridWidth = 10");
        page.evaluate("() => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).gridHeight = 10");
        expandInspectorSection("map");
        page.locator("[data-map-control=\"resize-canvas-btn\"]").click();

        Map<String, Object> resolution = (Map<String, Object>) page.evaluate("""
                () => Alpine.$data(document.querySelector('[x-data=\"toolbar()\"]')).tokenResolutions[0]
                """);
        assertThat(resolution.get("tokenId").toString()).isEqualTo(token.getId().toString());
        assertThat(resolution).containsEntry("action", "")
                .containsEntry("positionX", 9 * 48)
                .containsEntry("positionY", 3 * 48);
        assertThat(page.locator("[data-map-control=\"resize-confirm-btn\"]").isDisabled()).isTrue();

        page.locator("[data-map-control=\"resize-dialog\"] select")
                .last().selectOption("MOVE");
        assertThat(page.locator("[data-map-control=\"resize-confirm-btn\"]").isEnabled()).isTrue();
    }

    @Test
    void undoOfGridResizeRestoresRemovedToken() {
        Token token = new Token();
        token.setMap(map);
        token.setName("Undo token");
        token.setPositionX(19 * 48);
        token.setPositionY(3 * 48);
        token.setSizeCols(1);
        token.setSizeRows(1);
        token = tokenRepository.saveAndFlush(token);
        UUID tokenId = token.getId();

        page.evaluate("() => window.mapEditor.fetchTokens()");
        page.waitForFunction("() => window.mapEditor.tokenSnapshot.length === 1");
        page.evaluate("""
                async tokenId => {
                    const ed = window.mapEditor;
                    await ed.applyGridSettings({
                        width: 10, height: 10, cellSizePx: 48,
                        resizeMode: 'CROP',
                        tokenResolutions: [{
                            tokenId, action: 'REMOVE', positionX: 0, positionY: 0
                        }]
                    });
                    await ed.undo();
                }
                """, tokenId.toString());

        page.reload();
        page.waitForFunction("() => window.mapEditor?.tokenSnapshot?.length === 1");
        String restoredId = (String) page.evaluate(
                "() => window.mapEditor.tokenSnapshot[0].id");
        assertThat(restoredId).isEqualTo(tokenId.toString());
    }

    @Test
    void transparentSvgImportIsNormalizedToPngWithoutAWhiteBackground() {
        page.evaluate("""
                () => window.mapEditor.importBackgroundImage(
                    'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
                        '<svg xmlns="http://www.w3.org/2000/svg" width="8" height="8">'
                        + '<rect x="2" y="2" width="4" height="4" fill="red"/></svg>'))
                """);
        page.waitForFunction("""
                () => window.mapEditor.document.layers
                    .find(layer => layer.id === 'image')?.image?.dataUrl
                    ?.startsWith('data:image/png')
                """);

        Number alpha = (Number) page.evaluate("""
                async () => {
                    const dataUrl = window.mapEditor.document.layers
                        .find(layer => layer.id === 'image').image.dataUrl;
                    const image = new Image();
                    await new Promise((resolve, reject) => {
                        image.onload = resolve;
                        image.onerror = reject;
                        image.src = dataUrl;
                    });
                    const canvas = document.createElement('canvas');
                    canvas.width = image.width;
                    canvas.height = image.height;
                    const context = canvas.getContext('2d');
                    context.drawImage(image, 0, 0);
                    return context.getImageData(0, 0, 1, 1).data[3];
                }
                """);
        assertThat(alpha.intValue()).isZero();
    }

    @Test
    void resizePreviewMarksNonIntersectingDiagonalLineForExplicitRemoval() {
        Boolean requiresRemoval = (Boolean) page.evaluate("""
                () => {
                    const objects = window.mapEditor.document.layers.find(layer => layer.id === 'objects');
                    objects.shapes = [{
                        type: 'line', points: [-2, 1, 1, -2],
                        stroke: '#fff', strokeWidth: 1
                    }];
                    return window.mapEditor.previewGridResize(10, 10)
                        .affectedShapes[0].requiresRemoval;
                }
                """);
        assertThat(requiresRemoval).isTrue();
    }

    @Test
    void opaqueJpegImportRemainsCompactJpegData() throws Exception {
        byte[] jpegBytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "JPEG", output);
            jpegBytes = output.toByteArray();
        }
        page.setInputFiles("input[type=\"file\"]",
                new FilePayload("map.jpg", "image/jpeg", jpegBytes));
        page.waitForFunction("""
                () => window.mapEditor?.document?.layers
                    ?.find(layer => layer.id === 'image')?.image?.dataUrl
                    ?.startsWith('data:image/jpeg')
                """);
        Number length = (Number) page.evaluate("""
                () => window.mapEditor.document.layers
                    .find(layer => layer.id === 'image').image.dataUrl.length
                """);
        assertThat(length.intValue()).isLessThan(10_000);
    }

    @Test
    void theCanvasReceivesTheMajorityOfTheViewport() {
        page.setViewportSize(1280, 720);
        double canvas = ((Number) page.evaluate(
                "() => document.querySelector('.mapedit__canvas').getBoundingClientRect().width"))
                .doubleValue();
        assertThat(canvas / 1280.0).as("canvas share of width").isGreaterThan(0.6);
    }

    @SuppressWarnings("unchecked")
    @Test
    void gridAndSelectionStayLegibleOverImportedImagery() {
        Object contrastPair = page.evaluate("""
                () => {
                  const s = getComputedStyle(document.documentElement);
                  return [s.getPropertyValue('--map-grid-line').trim(),
                          s.getPropertyValue('--map-selection').trim()];
                }
                """);
        assertThat((java.util.List<String>) contrastPair).doesNotContain("");
    }

    @Test
    void autosaveStateIsVisibleButQuiet() {
        assertThat(page.isVisible("#saveIndicator")).isTrue();
        String color = (String) page.evaluate(
                "() => getComputedStyle(document.querySelector('#saveIndicator')).color");
        assertThat(color).as("save state must not use the primary text role")
                .isNotEqualTo("rgb(238, 232, 220)");
    }

    private void expandInspectorSection(String section) {
        page.click("[data-inspector-section='" + section + "'] > summary");
    }

    private void importSquarePng() throws Exception {
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
    }
}
