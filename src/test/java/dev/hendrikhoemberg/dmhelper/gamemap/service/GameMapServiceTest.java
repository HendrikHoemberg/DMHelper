package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignManifestV2SchemaValidator;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({GameMapService.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class GameMapServiceTest {

    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired private GameMapService service;

    @Autowired private jakarta.persistence.EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldCreateMapWithDefaultDocument() {
        GameMap map = service.create(campaign.getId(), "Tavern", 30, 20, 48);

        assertThat(map.getId()).isNotNull();
        assertThat(map.getName()).isEqualTo("Tavern");
        assertThat(map.getGridWidth()).isEqualTo(30);
        assertThat(map.getGridHeight()).isEqualTo(20);
        assertThat(map.getCellSizePx()).isEqualTo(48);
        assertThat(map.getSortOrder()).isEqualTo(0);
        assertThat(map.getGridType()).isEqualTo("SQUARE");
        assertThat(map.getDocument()).contains("\"schemaVersion\"");

        MapDocumentDto doc = service.getDocument(map.getId());
        assertThat(doc.schemaVersion()).isEqualTo(2);
        assertThat(doc.grid().width()).isEqualTo(30);
        assertThat(doc.grid().height()).isEqualTo(20);
        assertThat(doc.grid().gridType()).isEqualTo("square");
        assertThat(doc.layers()).hasSize(3);
        assertThat(doc.layers().get(0).id()).isEqualTo("terrain");
        assertThat(doc.layers().get(1).id()).isEqualTo("objects");
        assertThat(doc.layers().get(2).id()).isEqualTo("annotations");
        assertThat(doc.primitives()).isEmpty();
        assertThat(doc.customTerrain()).isEmpty();
    }

    @Test
    void shouldRoundTripImageLayer() {
        GameMap map = service.create(campaign.getId(), "Cave", 10, 10, 48);

        String documentJson = """
                {
                  "schemaVersion": 1,
                  "grid": {"width": 10, "height": 10, "cellSizePx": 48, "gridType": "square"},
                  "layers": [
                    {"id": "terrain", "name": "Terrain", "type": "TERRAIN", "cells": [], "shapes": []},
                    {"id": "objects", "name": "Objects", "type": "OBJECTS", "cells": [], "shapes": []},
                    {"id": "annotations", "name": "Annotations (DM only)", "type": "ANNOTATIONS", "cells": [], "shapes": []},
                    {"id": "image", "name": "Background", "type": "IMAGE", "cells": [], "shapes": [],
                     "image": {"dataUrl": "data:image/png;base64,AAAA", "x": 0, "y": 0, "width": 10, "height": 10}}
                  ],
                  "primitives": [],
                  "customTerrain": []
                }
                """;

        long initialVersion = map.getVersion();
        long version = service.updateDocument(map.getId(), documentJson, initialVersion);
        assertThat(version).isEqualTo(initialVersion + 1);

        MapDocumentDto doc = service.getDocument(map.getId());
        assertThat(doc.layers()).hasSize(4);
        MapLayerDto imageLayer = doc.layers().get(3);
        assertThat(imageLayer.id()).isEqualTo("image");
        assertThat(imageLayer.type()).isEqualTo(MapLayerDto.LayerType.IMAGE);
        assertThat(imageLayer.image()).isNotNull();
        assertThat(imageLayer.image().dataUrl()).isEqualTo("data:image/png;base64,AAAA");
        assertThat(imageLayer.image().width()).isEqualTo(10);
    }

    @Test
    void shouldAssignIncrementingSortOrders() {
        service.create(campaign.getId(), "Map 1", 20, 15, 48);
        GameMap map2 = service.create(campaign.getId(), "Map 2", 20, 15, 48);
        assertThat(map2.getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldFindByCampaignOrdered() {
        service.create(campaign.getId(), "Temple", 20, 15, 48);
        service.create(campaign.getId(), "Cave", 20, 15, 48);

        var maps = service.findByCampaignId(campaign.getId());
        assertThat(maps).hasSize(2);
        assertThat(maps.get(0).getSortOrder()).isLessThan(maps.get(1).getSortOrder());
    }

    @Test
    void shouldUpdateDocumentAndBumpVersion() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        long initialVersion = map.getVersion();
        String newDoc = """
                {"schemaVersion":2,"grid":{"width":20,"height":15,"cellSizePx":48,"gridType":"square"},"layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","visible":true,"locked":false,"cells":[{"col":0,"row":0,"terrain":"wall"}],"shapes":[]}],"primitives":[],"customTerrain":[]}""";

        long newVersion = service.updateDocument(map.getId(), newDoc, initialVersion);

        assertThat(newVersion).isEqualTo(initialVersion + 1);
        assertThat(service.findById(map.getId()).getDocument()).isEqualTo(newDoc);
    }

    @Test
    void shouldRejectStaleVersion() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        String doc = """
                {"schemaVersion":1,"grid":{"width":20,"height":15,"cellSizePx":48},"layers":[]}""";

        assertThatThrownBy(() -> service.updateDocument(map.getId(), doc, map.getVersion() + 7))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldRejectInvalidDocumentSchema() {
        GameMap map = service.create(campaign.getId(), "Test Map", 20, 15, 48);
        String badDoc = """
                {"schemaVersion":99,"grid":{"width":20,"height":15,"cellSizePx":48},"layers":[]}""";

        assertThatThrownBy(() -> service.updateDocument(map.getId(), badDoc, map.getVersion()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void shouldPreservePrimitivesAndCustomTerrain() {
        GameMap map = service.create(campaign.getId(), "Dungeon", 20, 15, 48);
        String doc = """
                {"schemaVersion":1,
                 "grid":{"width":20,"height":15,"cellSizePx":48,"gridType":"square"},
                 "layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","cells":[],"shapes":[]}],
                 "primitives":[{"type":"ROOM","startCol":2,"startRow":2,"endCol":10,"endRow":8},
                               {"type":"DOOR","startCol":10,"startRow":5,"endCol":10,"endRow":5}],
                 "customTerrain":[{"key":"moss","name":"Moss","fill":"#2a6e3a","walkable":true}]}""";

        service.updateDocument(map.getId(), doc, map.getVersion());

        MapDocumentDto parsed = service.getDocument(map.getId());
        assertThat(parsed.primitives()).hasSize(2);
        assertThat(parsed.primitives().get(0).type()).isEqualTo("ROOM");
        assertThat(parsed.customTerrain()).hasSize(1);
        assertThat(parsed.customTerrain().get(0).key()).isEqualTo("moss");
        // omitted visible/locked flags default to visible & unlocked
        assertThat(parsed.layers().get(0).visible()).isTrue();
        assertThat(parsed.layers().get(0).locked()).isFalse();
    }

    @Test
    void shouldDeleteMapAndReorder() {
        service.create(campaign.getId(), "Map 1", 20, 15, 48);
        GameMap map2 = service.create(campaign.getId(), "Map 2", 20, 15, 48);
        service.create(campaign.getId(), "Map 3", 20, 15, 48);

        service.delete(map2.getId());

        var maps = service.findByCampaignId(campaign.getId());
        assertThat(maps).hasSize(2);
        assertThat(maps.get(0).getSortOrder()).isEqualTo(0);
        assertThat(maps.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldUpdateNameWithoutAllowingGridMetadataToDrift() {
        GameMap map = service.create(campaign.getId(), "Original", 20, 15, 48);
        GameMap updated = service.update(map.getId(), "Renamed", 20, 15, 48);

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThatThrownBy(() -> service.update(map.getId(), "Renamed", 40, 30, 64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versioned settings endpoint");
    }

    @Test
    void shouldThrowWhenMapNotFound() {
        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Map not found");
    }

    @Test
    void shouldDefaultMovementModeAndShowGrid() {
        GameMap map = service.create(campaign.getId(), "Battle", 30, 20, 48);
        assertThat(map.getMovementMode()).isEqualTo("GRID");
        assertThat(map.isShowGrid()).isTrue();
    }

    @Test
    void shouldUpdateMovementMode() {
        GameMap map = service.create(campaign.getId(), "ModeMap", 10, 10, 48);
        service.updateMode(map.getId(), "FREEFORM", false);
        GameMap updated = service.findById(map.getId());
        assertThat(updated.getMovementMode()).isEqualTo("FREEFORM");
        assertThat(updated.isShowGrid()).isFalse();
    }

    @Test
    void shouldRejectInvalidMovementMode() {
        GameMap map = service.create(campaign.getId(), "BadMode", 10, 10, 48);
        assertThatThrownBy(() -> service.updateMode(map.getId(), "HEX", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid movementMode");
    }

    @Test
    void dtoSerializationMatchesMapDocumentSchema() throws Exception {
        var mapper = JsonMapper.builder().build();
        CampaignManifestV2SchemaValidator val = new CampaignManifestV2SchemaValidator();

        MapDocumentDto defaultDoc = MapDocumentDto.createDefault(30, 20, 48);
        assertThat(val.validate(manifestWrapping(mapper, defaultDoc))).isEmpty();

        MapDocumentDto richDoc = new MapDocumentDto(
                2,
                new MapDocumentDto.GridDto(10, 10, 48, "square", "GRID", true),
                List.of(
                        MapLayerDto.createTerrainLayer(),
                        MapLayerDto.createObjectsLayer(),
                        MapLayerDto.createAnnotationsLayer(),
                        new MapLayerDto("bg", "Background", MapLayerDto.LayerType.OBJECTS, true, false, List.of(),
                                List.of(new MapLayerDto.ShapeDto("rect", List.of(0.0, 0.0, 5.0, 5.0), "#ff0000", "#000", 1.0, "box")),
                                null, null)
                ),
                List.of(
                        new MapDocumentDto.PrimitiveDto("ROOM", 2, 2, 10, 8, null)
                ),
                List.of(
                        new MapDocumentDto.TerrainDefDto("moss", "Moss", "#2a6e3a", true)
                )
        );
        assertThat(val.validate(manifestWrapping(mapper, richDoc))).isEmpty();
    }

    /**
     * Injects a serialized MapDocumentDto into the committed minimal v2 manifest. The
     * schema requires 25 top-level fields and forbids extras, so the envelope comes from
     * the fixture rather than being written out here.
     */
    private static String manifestWrapping(JsonMapper mapper, MapDocumentDto document)
            throws Exception {
        ObjectNode manifest = (ObjectNode) mapper.readTree(
                new ClassPathResource("campaigns/v2/minimal.dmcampaign.json").getInputStream());

        ObjectNode map = mapper.createObjectNode();
        map.put("key", "map-1");
        map.put("name", "DTO test map");
        map.put("movementMode", "GRID");
        map.put("showGrid", true);
        map.put("sortOrder", 0);
        ObjectNode grid = mapper.createObjectNode();
        grid.put("w", document.grid().width());
        grid.put("h", document.grid().height());
        grid.put("cellPx", document.grid().cellSizePx());
        grid.put("gridType", "SQUARE");
        map.set("grid", grid);
        map.set("document", mapper.valueToTree(document));
        map.set("tokens", mapper.createArrayNode());

        ((ArrayNode) manifest.get("maps")).add(map);
        return mapper.writeValueAsString(manifest);
    }

    @Test
    void shouldUpdateSettingsPreservingContent() {
        GameMap map = service.create(campaign.getId(), "Settings Map", 20, 15, 48);
        long initialVersion = map.getVersion();

        var command = new MapSettingsCommand(initialVersion, 40, 30, 64,
                MapSettingsCommand.ResizeMode.PRESERVE, List.of());
        GameMapService.MapSettingsResult result = service.updateSettings(map.getId(), command);

        assertThat(result.version()).isEqualTo(initialVersion + 1);
        assertThat(result.map().getGridWidth()).isEqualTo(40);
        assertThat(result.map().getGridHeight()).isEqualTo(30);
        assertThat(result.map().getCellSizePx()).isEqualTo(64);

        MapDocumentDto doc = service.getDocument(map.getId());
        assertThat(doc.grid().width()).isEqualTo(40);
        assertThat(doc.grid().height()).isEqualTo(30);
        assertThat(doc.grid().cellSizePx()).isEqualTo(64);
    }

    @Test
    void shouldRejectStaleVersionOnSettingsUpdate() {
        GameMap map = service.create(campaign.getId(), "Stale Map", 20, 15, 48);

        var command = new MapSettingsCommand(map.getVersion() + 7, 40, 30, 64,
                MapSettingsCommand.ResizeMode.PRESERVE, List.of());
        assertThatThrownBy(() -> service.updateSettings(map.getId(), command))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldPreserveMovementModeAndShowGridOnSettingsUpdate() throws Exception {
        GameMap map = service.create(campaign.getId(), "Preserve Test", 20, 15, 48);

        // Update the document's grid to have FREEFORM + showGrid=false
        var mapper = new tools.jackson.databind.json.JsonMapper();
        MapDocumentDto original = service.getDocument(map.getId());
        var customGrid = new MapDocumentDto.GridDto(
                original.grid().width(), original.grid().height(), original.grid().cellSizePx(),
                original.grid().gridType(), "FREEFORM", false);
        var docWithFreeform = new MapDocumentDto(
                original.schemaVersion(), customGrid,
                original.layers(), original.primitives(), original.customTerrain());
        String json = mapper.writeValueAsString(docWithFreeform);
        long v = service.updateDocument(map.getId(), json, map.getVersion());
        map = service.findById(map.getId());

        var command = new MapSettingsCommand(v, 30, 20, 64,
                MapSettingsCommand.ResizeMode.PRESERVE, List.of());
        service.updateSettings(map.getId(), command);

        MapDocumentDto doc = service.getDocument(map.getId());
        assertThat(doc.grid().movementMode()).isEqualTo("FREEFORM");
        assertThat(doc.grid().showGrid()).isFalse();
    }

    @Test
    void updateSettingsVersionIsAcceptedByNextDocumentSave() {
        GameMap map = service.create(campaign.getId(), "Version Chain", 20, 15, 48);

        var command = new MapSettingsCommand(map.getVersion(), 30, 20, 64,
                MapSettingsCommand.ResizeMode.PRESERVE, List.of());
        GameMapService.MapSettingsResult result = service.updateSettings(map.getId(), command);

        String docJson = """
                {"schemaVersion":2,"grid":{"width":30,"height":20,"cellSizePx":64,"gridType":"square","movementMode":"GRID","showGrid":true},"layers":[],"primitives":[],"customTerrain":[]}""";
        long savedVersion = service.updateDocument(map.getId(), docJson, result.version());

        assertThat(savedVersion).isEqualTo(result.version() + 1);
        assertThat(service.findById(map.getId()).getVersion()).isEqualTo(savedVersion);
    }

    @Test
    void updateDocumentRejectsGridThatDisagreesWithMapMetadata() {
        GameMap map = service.create(campaign.getId(), "Grid Guard", 20, 15, 48);
        String mismatched = """
                {"schemaVersion":2,"grid":{"width":19,"height":15,"cellSizePx":48,"gridType":"square"},"layers":[]}""";

        assertThatThrownBy(() -> service.updateDocument(map.getId(), mismatched, map.getVersion()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match map settings");
    }

    @Test
    void updateSettingsEnforcesSupportedGridLimits() {
        GameMap map = service.create(campaign.getId(), "Limits", 20, 15, 48);

        assertThatThrownBy(() -> service.updateSettings(map.getId(),
                new MapSettingsCommand(map.getVersion(), 101, 20, 48,
                        MapSettingsCommand.ResizeMode.PRESERVE, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 5 and 100");
        assertThatThrownBy(() -> service.updateSettings(map.getId(),
                new MapSettingsCommand(map.getVersion(), 20, 15, 49,
                        MapSettingsCommand.ResizeMode.PRESERVE, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("increments of 8");
    }

    @Test
    void shrinkingRequiresResolutionForEveryAffectedToken() {
        GameMap map = service.create(campaign.getId(), "Token Guard", 20, 15, 48);
        Token token = token(map, "Outside", 19 * 48, 3 * 48);
        em.persist(token);
        em.flush();

        var command = new MapSettingsCommand(map.getVersion(), 10, 10, 48,
                MapSettingsCommand.ResizeMode.CROP, List.of());

        assertThatThrownBy(() -> service.updateSettings(map.getId(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(token.getId().toString())
                .hasMessageContaining("requires an explicit resolution");
    }

    @Test
    void shrinkingMovesAffectedTokenToValidatedPixelPosition() {
        GameMap map = service.create(campaign.getId(), "Token Move", 20, 15, 48);
        Token token = token(map, "Outside", 19 * 48, 3 * 48);
        em.persist(token);
        em.flush();

        var resolution = new MapSettingsCommand.TokenResolution(
                token.getId(), MapSettingsCommand.TokenAction.MOVE, 9 * 48, 3 * 48);
        service.updateSettings(map.getId(), new MapSettingsCommand(
                map.getVersion(), 10, 10, 48, MapSettingsCommand.ResizeMode.CROP, List.of(resolution)));
        em.clear();

        Token moved = em.find(Token.class, token.getId());
        assertThat(moved.getPositionX()).isEqualTo(9 * 48);
        assertThat(moved.getPositionY()).isEqualTo(3 * 48);
    }

    @Test
    void updateSettingsAtomicallyUsesSubmittedDirtyDocument() {
        GameMap map = service.create(campaign.getId(), "Dirty Settings", 20, 15, 48);
        MapDocumentDto current = service.getDocument(map.getId());
        MapLayerDto terrain = current.layers().getFirst();
        MapLayerDto changedTerrain = new MapLayerDto(
                terrain.id(), terrain.name(), terrain.type(), terrain.visible(), terrain.locked(),
                List.of(new MapLayerDto.CellDto(2, 3, "wall")),
                terrain.shapes(), terrain.image(), terrain.playerVisible());
        MapDocumentDto dirty = new MapDocumentDto(
                current.schemaVersion(), current.grid(),
                List.of(changedTerrain, current.layers().get(1), current.layers().get(2)),
                current.primitives(), current.customTerrain());

        service.updateSettings(map.getId(), new MapSettingsCommand(
                map.getVersion(), 40, 30, 64, MapSettingsCommand.ResizeMode.PRESERVE, List.of(), dirty));

        assertThat(service.getDocument(map.getId()).layers().getFirst().cells())
                .extracting(MapLayerDto.CellDto::col, MapLayerDto.CellDto::row)
                .containsExactly(tuple(2, 3));
    }

    @Test
    void updateSettingsCanAtomicallyRestoreEarlierGridSnapshot() {
        GameMap map = service.create(campaign.getId(), "History Restore", 20, 15, 48);
        MapDocumentDto original = service.getDocument(map.getId());
        GameMapService.MapSettingsResult expanded = service.updateSettings(map.getId(),
                new MapSettingsCommand(map.getVersion(), 40, 30, 64,
                        MapSettingsCommand.ResizeMode.PRESERVE, List.of()));

        GameMapService.MapSettingsResult restored = service.updateSettings(map.getId(),
                new MapSettingsCommand(expanded.version(), 20, 15, 48,
                        MapSettingsCommand.ResizeMode.PRESERVE, List.of(), original));

        assertThat(restored.document().grid().width()).isEqualTo(20);
        assertThat(restored.document().grid().height()).isEqualTo(15);
        assertThat(restored.document().grid().cellSizePx()).isEqualTo(48);
        assertThat(restored.map().getGridWidth()).isEqualTo(20);
        assertThat(restored.map().getGridHeight()).isEqualTo(15);
        assertThat(restored.map().getCellSizePx()).isEqualTo(48);
    }

    @Test
    void cellSizeChangeKeepsTokenAnchoredToSameLogicalCell() {
        GameMap map = service.create(campaign.getId(), "Token Scale", 20, 15, 48);
        Token token = token(map, "Anchored", 2 * 48, 3 * 48);
        em.persist(token);
        em.flush();

        service.updateSettings(map.getId(), new MapSettingsCommand(
                map.getVersion(), 20, 15, 64,
                MapSettingsCommand.ResizeMode.PRESERVE, List.of()));
        em.clear();

        Token scaled = em.find(Token.class, token.getId());
        assertThat(scaled.getPositionX()).isEqualTo(2 * 64);
        assertThat(scaled.getPositionY()).isEqualTo(3 * 64);
    }

    @Test
    void settingsHistorySnapshotRestoresTokenRemovedByResize() {
        GameMap map = service.create(campaign.getId(), "Token Undo", 20, 15, 48);
        MapDocumentDto original = service.getDocument(map.getId());
        Token token = token(map, "Restorable", 19 * 48, 3 * 48);
        token.setNotes("keep me");
        em.persist(token);
        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setMap(map);
        encounter.setName("Linked encounter");
        em.persist(encounter);
        Combatant combatant = new Combatant();
        combatant.setEncounter(encounter);
        combatant.setName("Linked combatant");
        combatant.setMaxHp(10);
        combatant.setCurrentHp(10);
        em.persist(combatant);
        em.flush();
        UUID tokenId = token.getId();
        var snapshot = new MapSettingsCommand.TokenSnapshot(
                tokenId, token.getName(), token.getKind(), token.getPositionX(), token.getPositionY(),
                token.getSizeCols(), token.getSizeRows(), token.getColor(), token.isHidden(),
                null, null, token.getNotes(), token.getIcon());

        var removed = service.updateSettings(map.getId(), new MapSettingsCommand(
                map.getVersion(), 10, 10, 48, MapSettingsCommand.ResizeMode.CROP,
                List.of(new MapSettingsCommand.TokenResolution(
                        tokenId, MapSettingsCommand.TokenAction.REMOVE, 0, 0))));
        em.flush();
        em.clear();
        assertThat(em.find(Token.class, tokenId)).isNull();
        Token laterToken = token(service.findById(map.getId()), "Created later", 2 * 48, 2 * 48);
        em.persist(laterToken);
        em.flush();
        UUID laterTokenId = laterToken.getId();

        service.updateSettings(map.getId(), new MapSettingsCommand(
                removed.version(), 20, 15, 48, MapSettingsCommand.ResizeMode.PRESERVE,
                List.of(), List.of(), List.of(tokenId), List.of(snapshot), List.of(), original));
        em.flush();
        em.clear();

        Token restored = em.find(Token.class, tokenId);
        assertThat(restored).isNotNull();
        assertThat(restored.getPositionX()).isEqualTo(19 * 48);
        assertThat(restored.getNotes()).isEqualTo("keep me");
        assertThat(em.find(Token.class, laterTokenId)).isNotNull();
    }

    private static Token token(GameMap map, String name, int x, int y) {
        Token token = new Token();
        token.setMap(map);
        token.setName(name);
        token.setPositionX(x);
        token.setPositionY(y);
        token.setSizeCols(1);
        token.setSizeRows(1);
        return token;
    }

}
