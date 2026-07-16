package dev.hendrikhoemberg.dmhelper.gamemap.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.MapDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.MapDto.TokenDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Component
public class MapSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final GameMapRepository gameMapRepository;
    private final TokenRepository tokenRepository;
    private final StatBlockReferenceResolver statBlockResolver;
    private final ObjectMapper objectMapper;

    public MapSectionAdapter(GameMapRepository gameMapRepository,
                             TokenRepository tokenRepository,
                             StatBlockReferenceResolver statBlockResolver) {
        this.gameMapRepository = gameMapRepository;
        this.tokenRepository = tokenRepository;
        this.statBlockResolver = statBlockResolver;
        this.objectMapper = JsonMapper.builder().build();
    }

    @Override
    public String sectionName() {
        return "Map";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var unsorted = gameMapRepository.findByCampaignIdOrderBySortOrderAsc(context.campaignId());
        var maps = new ArrayList<>(unsorted);
        maps.sort(Comparator.comparingInt(GameMap::getSortOrder)
                .thenComparing(GameMap::getId));

        List<MapDto> dtos = maps.stream()
                .map(map -> exportMap(map, context))
                .toList();
        target.maps(dtos);
    }

    private MapDto exportMap(GameMap map, CampaignExportContext context) {
        String key = context.key(CampaignContentType.MAP, map.getId(), map.getName());

        CampaignManifestV2.MapDto.MapDocumentV2 docV2 = null;
        if (map.getDocument() != null) {
            try {
                MapDocumentDto doc = objectMapper.readValue(map.getDocument(), MapDocumentDto.class);
                docV2 = toManifestDocument(doc, key, context);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse map document for " + map.getId(), e);
            }
        }

        List<Token> tokens = tokenRepository.findByMapIdOrderByNameAsc(map.getId());
        List<TokenDto> tokenDtos = tokens.stream()
                .map(t -> exportToken(t, context))
                .toList();

        return new MapDto(
                key, map.getName(),
                new MapDto.GridDto(map.getGridWidth(), map.getGridHeight(), map.getCellSizePx(), map.getGridType()),
                map.getMovementMode(), map.isShowGrid(),
                docV2, tokenDtos, map.getSortOrder()
        );
    }

    private CampaignManifestV2.MapDto.MapDocumentV2 toManifestDocument(
            MapDocumentDto doc, String mapKey, CampaignExportContext context) {

        List<CampaignManifestV2.MapDto.LayerDto> layers = new ArrayList<>();
        for (int layerIndex = 0; layerIndex < doc.layers().size(); layerIndex++) {
            MapLayerDto layer = doc.layers().get(layerIndex);
            CampaignManifestV2.MapDto.ImageDto imageDto = null;
            if (layer.image() != null && layer.image().dataUrl() != null) {
                imageDto = exportImage(layer.image(), mapKey, layer.id(), layerIndex, context);
            }
            layers.add(new CampaignManifestV2.MapDto.LayerDto(
                    layer.id(), layer.name(), layer.type(),
                    layer.visible(), layer.locked(),
                    layer.cells(), layer.shapes(), imageDto
            ));
        }

        return new CampaignManifestV2.MapDto.MapDocumentV2(
                2, doc.grid(), layers,
                doc.primitives(), doc.customTerrain()
        );
    }

    private CampaignManifestV2.MapDto.ImageDto exportImage(
            MapLayerDto.ImageDto image, String mapKey, String layerId, int layerIndex,
            CampaignExportContext context) {

        String dataUrl = image.dataUrl();
        if (!dataUrl.startsWith("data:")) {
            throw new IllegalArgumentException("Image data URL must start with 'data:'");
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Invalid data URL: missing comma separator");
        }
        String header = dataUrl.substring(5, comma);
        String encoded = dataUrl.substring(comma + 1);
        boolean isBase64 = header.contains(";base64");
        String mediaType = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;

        byte[] bytes = isBase64 ? Base64.getDecoder().decode(encoded)
                : encoded.getBytes(StandardCharsets.UTF_8);

        String sha256 = sha256(bytes);
        String identity = mapKey + "\u0000" + layerIndex + "\u0000" + layerId + "\u0000" + sha256;
        String assetKey = "map-img-" + sha256(identity.getBytes(StandardCharsets.UTF_8));

        String ext = switch (mediaType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "bin";
        };
        var descriptor = new AssetDescriptor(
                assetKey, "assets/maps/" + assetKey + "." + ext,
                mediaType, bytes.length, sha256, assetKey + "." + ext
        );
        context.assets().add(descriptor, bytes);

        return new CampaignManifestV2.MapDto.ImageDto(assetKey, image.x(), image.y(), image.width(), image.height());
    }

    private TokenDto exportToken(Token token, CampaignExportContext context) {
        String key = context.key(CampaignContentType.TOKEN, token.getId(), token.getName());

        ContentReference statBlockRef = null;
        if (token.getStatBlock() != null) {
            statBlockRef = statBlockResolver.referenceFor(token.getStatBlock(), context);
        }

        ContentReference partyMemberRef = null;
        if (token.getPartyMember() != null) {
            partyMemberRef = context.packageRef(CampaignContentType.PARTY_MEMBER,
                    token.getPartyMember().getId(), token.getPartyMember().getCharacterName());
        }

        return new TokenDto(
                key, token.getName(), token.getKind(), token.getColor(),
                token.getPositionX(), token.getPositionY(),
                token.getSizeCols(), token.getSizeRows(),
                token.isHidden(), statBlockRef, partyMemberRef,
                token.getCurrentHp(), token.getMaxHp(), token.isDead(),
                token.getNotes(), token.getIcon()
        );
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<MapDto> dtos = source.maps();
        if (dtos == null) return;

        var campaign = context.campaign();

        List<ImportedMap> imported = new ArrayList<>();
        for (MapDto dto : dtos) {
            var map = new GameMap();
            map.setCampaign(campaign);
            map.setName(dto.name());
            map.setGridWidth(dto.grid().w());
            map.setGridHeight(dto.grid().h());
            map.setCellSizePx(dto.grid().cellPx());
            map.setGridType(dto.grid().gridType());
            map.setMovementMode(dto.movementMode());
            map.setShowGrid(dto.showGrid());
            map.setSortOrder(dto.sortOrder());

            if (dto.document() != null) {
                MapDocumentDto storedDoc = fromManifestDocument(dto.document(), source, context);
                try {
                    map.setDocument(objectMapper.writeValueAsString(storedDoc));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize map document", e);
                }
            }

            gameMapRepository.save(map);
            context.register(CampaignContentType.MAP, dto.key(), map, map.getId());
            imported.add(new ImportedMap(map, dto));
        }

        for (var entry : imported) {
            for (TokenDto tokenDto : entry.dto.tokens()) {
                var token = new Token();
                token.setMap(entry.map);
                token.setName(tokenDto.name());
                token.setKind(tokenDto.kind());
                token.setColor(tokenDto.color());
                token.setPositionX(tokenDto.positionX());
                token.setPositionY(tokenDto.positionY());
                token.setSizeCols(tokenDto.sizeCols());
                token.setSizeRows(tokenDto.sizeRows());
                token.setHidden(tokenDto.hidden());
                token.setCurrentHp(tokenDto.currentHp());
                token.setMaxHp(tokenDto.maxHp());
                token.setDead(tokenDto.dead());
                token.setNotes(tokenDto.notes());
                token.setIcon(tokenDto.icon());

                if (tokenDto.statBlockRef() != null) {
                    token.setStatBlock(statBlockResolver.resolve(tokenDto.statBlockRef(), context));
                }
                if (tokenDto.partyMemberRef() != null) {
                    var pm = context.require(tokenDto.partyMemberRef(), CampaignContentType.PARTY_MEMBER, PartyMember.class);
                    token.setPartyMember(pm);
                }

                tokenRepository.save(token);
                context.register(CampaignContentType.TOKEN, tokenDto.key(), token, token.getId());
            }
        }
    }

    private MapDocumentDto fromManifestDocument(
            CampaignManifestV2.MapDto.MapDocumentV2 doc,
            CampaignManifestV2 manifest,
            CampaignImportContext context) {

        List<MapLayerDto> layers = new ArrayList<>();
        for (CampaignManifestV2.MapDto.LayerDto layer : doc.layers()) {
            MapLayerDto.ImageDto imageDto = null;
            if (layer.image() != null && layer.image().assetRef() != null) {
                imageDto = importImage(layer.image(), manifest, context);
            }
            layers.add(new MapLayerDto(
                    layer.id(), layer.name(), layer.type(),
                    layer.visible(), layer.locked(),
                    layer.cells(), layer.shapes(), imageDto
            ));
        }

        return new MapDocumentDto(
                doc.schemaVersion(), doc.grid(), layers,
                doc.primitives(), doc.customTerrain()
        );
    }

    private MapLayerDto.ImageDto importImage(
            CampaignManifestV2.MapDto.ImageDto image,
            CampaignManifestV2 manifest,
            CampaignImportContext context) {

        String assetRef = image.assetRef();
        String mediaType = resolveMediaType(assetRef, manifest);

        byte[] bytes;
        try {
            Path assetPath = context.requireAsset(assetRef);
            bytes = Files.readAllBytes(assetPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read asset: " + assetRef, e);
        }

        String base64 = Base64.getEncoder().encodeToString(bytes);
        String dataUrl = "data:" + mediaType + ";base64," + base64;
        return new MapLayerDto.ImageDto(dataUrl, image.x(), image.y(), image.width(), image.height());
    }

    private static String resolveMediaType(String assetRef, CampaignManifestV2 manifest) {
        if (manifest.assets() != null) {
            for (var asset : manifest.assets()) {
                if (asset.key().equals(assetRef)) {
                    return asset.mediaType();
                }
            }
        }
        return "image/png";
    }

    private static String sha256(byte[] bytes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record ImportedMap(GameMap map, MapDto dto) {}
}
