package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.MapDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.packagev2.MapSectionAdapter;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapSectionAdapterTest {

    private GameMapRepository gameMapRepo;
    private TokenRepository tokenRepo;
    private StatBlockReferenceResolver statBlockResolver;
    private StatBlockRepository statBlockRepository;
    private MapSectionAdapter adapter;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        gameMapRepo = mock(GameMapRepository.class);
        tokenRepo = mock(TokenRepository.class);
        statBlockRepository = mock(StatBlockRepository.class);
        statBlockResolver = new StatBlockReferenceResolver(statBlockRepository);
        adapter = new MapSectionAdapter(gameMapRepo, tokenRepo, statBlockResolver);
        campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder400() {
        assertThat(adapter.order()).isEqualTo(400);
    }

    @Test
    void sectionNameIsMap() {
        assertThat(adapter.sectionName()).isEqualTo("Map");
    }

    @Test
    void exportsMapsBySortOrderThenUuid() {
        UUID idBig = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID idSmall = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID idMiddle = UUID.fromString("00000000-0000-0000-0000-000000000002");

        GameMap mapA = map(idSmall, "A", 1);
        GameMap mapB = map(idBig, "B", 0);
        GameMap mapC = map(idMiddle, "C", 1);

        when(gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaign.getId()))
                .thenReturn(List.of(mapB, mapA, mapC));
        when(tokenRepo.findByMapIdOrderByNameAsc(any())).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = exportContext(keyService);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var maps = manifest.maps();
        assertThat(maps).hasSize(3);
        // order: sortOrder=0 (B), then sortOrder=1 sorted by UUID (A < C)
        assertThat(maps.get(0).name()).isEqualTo("B");
        assertThat(maps.get(1).name()).isEqualTo("A");
        assertThat(maps.get(2).name()).isEqualTo("C");
    }

    @Test
    void exportsTokensWithKindsAndState() {
        GameMap map = map(UUID.randomUUID(), "Dungeon", 0);
        map.setDocument(documentJson(null));

        when(gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaign.getId()))
                .thenReturn(List.of(map));

        Token pc = token(UUID.randomUUID(), "Aragorn", "PC", map, 100, 50, 20, 20, false, false, "helmet");
        pc.setCurrentHp(45);
        pc.setMaxHp(50);
        Token npc = token(UUID.randomUUID(), "Merchant", "NPC", map, 200, 150, 20, 20, false, false, null);
        Token monster = token(UUID.randomUUID(), "Goblin", "MONSTER", map, 50, 75, 20, 20, true, true, "skull");
        Token object = token(UUID.randomUUID(), "Chest", "OBJECT", map, 300, 200, 20, 20, false, false, null);
        object.setCurrentHp(null);
        object.setMaxHp(null);

        when(tokenRepo.findByMapIdOrderByNameAsc(map.getId())).thenReturn(List.of(pc, npc, monster, object));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = exportContext(keyService);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var tokens = manifest.maps().get(0).tokens();
        assertThat(tokens).hasSize(4);

        var pcDto = tokens.get(0);
        assertThat(pcDto.kind()).isEqualTo("PC");
        assertThat(pcDto.currentHp()).isEqualTo(45);
        assertThat(pcDto.maxHp()).isEqualTo(50);
        assertThat(pcDto.hidden()).isFalse();
        assertThat(pcDto.dead()).isFalse();
        assertThat(pcDto.icon()).isEqualTo("helmet");
        assertThat(pcDto.positionX()).isEqualTo(100);
        assertThat(pcDto.positionY()).isEqualTo(50);

        var monsterDto = tokens.get(2);
        assertThat(monsterDto.kind()).isEqualTo("MONSTER");
        assertThat(monsterDto.hidden()).isTrue();
        assertThat(monsterDto.dead()).isTrue();
        assertThat(monsterDto.icon()).isEqualTo("skull");

        var objectDto = tokens.get(3);
        assertThat(objectDto.currentHp()).isNull();
        assertThat(objectDto.maxHp()).isNull();
    }

    @Test
    void exportsPixelCoordinates() {
        GameMap map = map(UUID.randomUUID(), "Town", 0);
        map.setDocument(documentJson(null));

        when(gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaign.getId()))
                .thenReturn(List.of(map));

        Token token = token(UUID.randomUUID(), "Guard", "NPC", map, 1440, 720, 48, 48, false, false, null);
        when(tokenRepo.findByMapIdOrderByNameAsc(map.getId())).thenReturn(List.of(token));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = exportContext(keyService);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var t = manifest.maps().get(0).tokens().get(0);
        assertThat(t.positionX()).isEqualTo(1440);
        assertThat(t.positionY()).isEqualTo(720);
    }

    @Test
    void exportsImageAssetRefs() {
        GameMap map = map(UUID.randomUUID(), "World", 0);
        String mockImageDataUrl = imageDataUrl();
        map.setDocument(documentJson(mockImageDataUrl));

        when(gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaign.getId()))
                .thenReturn(List.of(map));
        when(tokenRepo.findByMapIdOrderByNameAsc(map.getId())).thenReturn(List.of());

        var collector = new CampaignAssetCollector();
        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, collector);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var layers = manifest.maps().get(0).document().layers();
        var imageLayer = layers.stream()
                .filter(l -> l.image() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected an image layer"));

        assertThat(imageLayer.image().assetRef()).isNotNull();
        assertThat(imageLayer.image().x()).isEqualTo(0);
        assertThat(imageLayer.image().y()).isEqualTo(0);
        assertThat(imageLayer.image().width()).isEqualTo(30);
        assertThat(imageLayer.image().height()).isEqualTo(20);

        var descriptors = collector.assetDescriptors();
        assertThat(descriptors).hasSize(1);
        AssetDescriptor desc = descriptors.get(0);
        assertThat(desc.key()).isEqualTo(imageLayer.image().assetRef());
        assertThat(desc.mediaType()).isEqualTo("image/png");
        assertThat(desc.sizeBytes()).isEqualTo(4);
        assertThat(desc.sha256()).isNotNull();
    }

    @Test
    void imageAssetKeysRemainUniqueForLayersAtTheSameCoordinates() throws Exception {
        GameMap map = map(UUID.fromString("12345678-0000-0000-0000-000000000001"), "World", 0);
        var image = new MapLayerDto.ImageDto(imageDataUrl(), 0.5, 0.5, 30, 20);
        var document = new MapDocumentDto(
                1,
                new MapDocumentDto.GridDto(30, 20, 48, "square", "GRID", true),
                List.of(
                        new MapLayerDto("background-a", "A", MapLayerDto.LayerType.IMAGE,
                                true, false, List.of(), List.of(), image),
                        new MapLayerDto("background-b", "B", MapLayerDto.LayerType.IMAGE,
                                true, false, List.of(), List.of(), image)),
                List.of(), List.of());
        map.setDocument(new tools.jackson.databind.json.JsonMapper().writeValueAsString(document));

        when(gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaign.getId())).thenReturn(List.of(map));
        when(tokenRepo.findByMapIdOrderByNameAsc(map.getId())).thenReturn(List.of());

        var collector = new CampaignAssetCollector();
        var context = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                new CampaignSectionAdapterTest.FakeKeyService(), collector);
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());

        adapter.exportSection(context, assembler);

        assertThat(collector.assetDescriptors())
                .hasSize(2)
                .extracting(AssetDescriptor::key)
                .doesNotHaveDuplicates();
    }

    @Test
    void importsMapsWithTokens(@TempDir Path tempDir) throws IOException {
        UUID mapId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        var srdRef = ContentReference.catalogRef(
                CampaignContentType.STATBLOCK, "SRD_5_2", "srd-2024_goblin");
        var srd = new StatBlock();
        srd.setId(UUID.randomUUID());
        srd.setSource(StatBlock.Source.SRD);
        srd.setSourceKey("srd-2024_goblin");

        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null, null,
                List.of(new MapDto(
                        "map-dungeon", "Dungeon",
                        new MapDto.GridDto(30, 20, 48, "SQUARE"),
                        "GRID", true,
                        new CampaignManifestV2.MapDto.MapDocumentV2(
                                1,
                                new MapDocumentDto.GridDto(30, 20, 48, "square", "GRID", true),
                                List.of(new CampaignManifestV2.MapDto.LayerDto(
                                        "terrain", "Terrain", MapLayerDto.LayerType.TERRAIN,
                                        true, false, List.of(), List.of(), null)),
                                List.of(), List.of()
                        ),
                        List.of(new CampaignManifestV2.MapDto.TokenDto(
                                "token-goblin", "Goblin", "MONSTER", "#ff0000",
                                100, 200, 1, 1, true, srdRef, null,
                                12, 20, false, "sneaky", "dagger"
                        )),
                        0
                )),
                null, null, null, null, null, null, null, null, null, List.of(), List.of()
        );

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());

        when(gameMapRepo.save(any())).thenAnswer(inv -> {
            var gm = inv.getArgument(0, GameMap.class);
            if (gm.getId() == null) gm.setId(mapId);
            return gm;
        });
        when(tokenRepo.save(any())).thenAnswer(inv -> {
            var t = inv.getArgument(0, Token.class);
            if (t.getId() == null) t.setId(tokenId);
            return t;
        });
        when(statBlockRepository.findBySourceAndSourceKey(
                StatBlock.Source.SRD, "srd-2024_goblin")).thenReturn(java.util.Optional.of(srd));

        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport());
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, importContext);

        verify(gameMapRepo).save(argThat(gm ->
                gm.getName().equals("Dungeon") &&
                gm.getSortOrder() == 0 &&
                gm.getGridWidth() == 30 &&
                gm.getGridHeight() == 20 &&
                gm.getCellSizePx() == 48 &&
                gm.getMovementMode().equals("GRID") &&
                gm.isShowGrid()
        ));

        // Verify document is valid JSON with the right structure
        verify(gameMapRepo).save(argThat(gm -> {
            if (gm.getDocument() == null) return false;
            try {
                var mapper = new tools.jackson.databind.json.JsonMapper();
                var doc = mapper.readValue(gm.getDocument(), MapDocumentDto.class);
                return doc.schemaVersion() == 1
                        && doc.layers().size() == 1
                        && doc.layers().get(0).id().equals("terrain");
            } catch (Exception e) {
                return false;
            }
        }));

        verify(tokenRepo).save(argThat(t ->
                t.getName().equals("Goblin") &&
                t.getKind().equals("MONSTER") &&
                t.getPositionX() == 100 &&
                t.getPositionY() == 200 &&
                t.isHidden() &&
                !t.isDead() &&
                t.getCurrentHp() == 12 &&
                t.getMaxHp() == 20 &&
                t.getStatBlock() == srd &&
                t.getIcon().equals("dagger") &&
                t.getNotes().equals("sneaky")
        ));
    }

    @Test
    void importsImageAssetRefsAsDataUrls(@TempDir Path tempDir) throws IOException {
        byte[] imageBytes = {0, 1, 2, 3};
        String assetKey = "map-img-key";

        UUID mapId = UUID.randomUUID();

        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null, null,
                List.of(new MapDto(
                        "map-world", "World",
                        new MapDto.GridDto(30, 20, 48, "SQUARE"),
                        "GRID", true,
                        new CampaignManifestV2.MapDto.MapDocumentV2(
                                1,
                                new MapDocumentDto.GridDto(30, 20, 48, "square", "GRID", true),
                                List.of(new CampaignManifestV2.MapDto.LayerDto(
                                        "bg", "Background", MapLayerDto.LayerType.IMAGE,
                                        true, false, List.of(), List.of(),
                                        new CampaignManifestV2.MapDto.ImageDto(
                                                assetKey, 10, 20, 300, 200
                                        )
                                )),
                                List.of(), List.of()
                        ),
                        List.of(),
                        0
                )),
                null, null, null, null, null, null, null, null, null, List.of(), List.of()
        );

        // Stage asset file
        Path assetFile = tempDir.resolve(assetKey + ".png");
        Files.write(assetFile, imageBytes);

        var freshCampaign = new Campaign();
        freshCampaign.setId(UUID.randomUUID());

        var validationResult = new dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult(
                null, manifest, 2, Map.of(assetKey, assetFile), List.of(), List.of());

        var pendingImport = new PendingCampaignImport(
                UUID.randomUUID(), validationResult, null, null);

        when(gameMapRepo.save(any())).thenAnswer(inv -> {
            var gm = inv.getArgument(0, GameMap.class);
            if (gm.getId() == null) gm.setId(mapId);
            return gm;
        });
        when(tokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var importContext = new CampaignImportContext(
                freshCampaign.getId(), new CampaignSectionAdapterTest.FakeKeyService(), pendingImport);
        importContext.setCampaign(freshCampaign);
        importContext.register(CampaignContentType.CAMPAIGN, "campaign-key", freshCampaign, freshCampaign.getId());

        adapter.importSection(manifest, importContext);

        verify(gameMapRepo).save(argThat(gm -> {
            if (gm.getDocument() == null) return false;
            try {
                var mapper = new tools.jackson.databind.json.JsonMapper();
                var doc = mapper.readValue(gm.getDocument(), MapDocumentDto.class);
                var layer = doc.layers().stream()
                        .filter(l -> l.image() != null)
                        .findFirst()
                        .orElse(null);
                if (layer == null || layer.image().dataUrl() == null) return false;
                String expectedDataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
                return layer.image().dataUrl().equals(expectedDataUrl)
                        && layer.image().x() == 10
                        && layer.image().y() == 20
                        && layer.image().width() == 300
                        && layer.image().height() == 200;
            } catch (Exception e) {
                return false;
            }
        }));
    }

    @Test
    void resolvesStatBlockAndPartyMemberRefs() {
        GameMap map = map(UUID.randomUUID(), "Heroes", 0);
        map.setDocument(documentJson(null));

        when(gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaign.getId()))
                .thenReturn(List.of(map));

        Token token = token(UUID.randomUUID(), "Frodo", "PC", map, 0, 0, 1, 1, false, false, null);
        var srd = new StatBlock();
        srd.setId(UUID.randomUUID());
        srd.setSource(StatBlock.Source.SRD);
        srd.setSourceKey("srd-2024_goblin");
        srd.setName("Goblin");
        token.setStatBlock(srd);
        when(tokenRepo.findByMapIdOrderByNameAsc(map.getId())).thenReturn(List.of(token));

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        var ctx = exportContext(keyService);
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        var tokenDto = manifest.maps().get(0).tokens().get(0);
        assertThat(tokenDto.statBlockRef()).isEqualTo(
                ContentReference.catalogRef(CampaignContentType.STATBLOCK, "SRD_5_2", "srd-2024_goblin"));
        assertThat(tokenDto.partyMemberRef()).isNull();
    }

    private static String imageDataUrl() {
        byte[] raw = {0, 1, 2, 3};
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(raw);
    }

    private static String documentJson(String imageDataUrl) {
        try {
            var mapper = new tools.jackson.databind.json.JsonMapper();
            List<MapLayerDto> layers;
            if (imageDataUrl != null) {
                var imgLayer = new MapLayerDto("bg", "Background", MapLayerDto.LayerType.IMAGE,
                        true, false, List.of(), List.of(),
                        new MapLayerDto.ImageDto(imageDataUrl, 0, 0, 30, 20));
                var terrainLayer = MapLayerDto.createTerrainLayer();
                var objectsLayer = MapLayerDto.createObjectsLayer();
                var annotationsLayer = MapLayerDto.createAnnotationsLayer();
                layers = List.of(imgLayer, terrainLayer, objectsLayer, annotationsLayer);
            } else {
                layers = List.of(
                        MapLayerDto.createTerrainLayer(),
                        MapLayerDto.createObjectsLayer(),
                        MapLayerDto.createAnnotationsLayer()
                );
            }
            var doc = new MapDocumentDto(1,
                    new MapDocumentDto.GridDto(30, 20, 48, "square", "GRID", true),
                    layers, List.of(), List.of());
            return mapper.writeValueAsString(doc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private GameMap map(UUID id, String name, int sortOrder) {
        GameMap m = new GameMap();
        m.setId(id);
        m.setCampaign(campaign);
        m.setName(name);
        m.setSortOrder(sortOrder);
        m.setGridWidth(30);
        m.setGridHeight(20);
        m.setCellSizePx(48);
        m.setDocument(documentJson(null));
        return m;
    }

    private Token token(UUID id, String name, String kind, GameMap map,
                        int x, int y, int cols, int rows,
                        boolean hidden, boolean dead, String icon) {
        Token t = new Token();
        t.setId(id);
        t.setName(name);
        t.setKind(kind);
        t.setMap(map);
        t.setPositionX(x);
        t.setPositionY(y);
        t.setSizeCols(cols);
        t.setSizeRows(rows);
        t.setHidden(hidden);
        t.setDead(dead);
        t.setIcon(icon);
        return t;
    }

    private CampaignExportContext exportContext(CampaignSectionAdapterTest.FakeKeyService keyService) {
        return new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
    }

    private void fillRest(CampaignManifestAssembler a) {
        a.campaign(new CampaignManifestV2.CampaignDto("campaign-key", "test", null, null, null, null));
        a.party(List.of());
        a.customStatBlocks(List.of());
        a.handouts(List.of());
        a.encounters(List.of());
        a.notes(List.of());
        a.quickNotes(List.of());
        a.assignments(List.of());
        a.ledgerEntries(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.session(null);
        a.diceRolls(List.of());
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler a) {
        return a.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(UUID.randomUUID(), null, null, null);
    }
}
