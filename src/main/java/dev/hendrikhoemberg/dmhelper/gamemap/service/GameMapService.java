package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import jakarta.persistence.EntityManager;
import dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class GameMapService {

    private final GameMapRepository repository;
    private final CampaignRepository campaignRepository;
    private final SceneRefCleaner sceneRefCleaner;
    private final SessionReferenceCleaner sessionRefCleaner;
    private final EncounterRepository encounterRepository;
    private final CombatantRepository combatantRepository;
    private final TokenRepository tokenRepository;
    private final StatBlockRepository statBlockRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public GameMapService(GameMapRepository repository, CampaignRepository campaignRepository,
                          SceneRefCleaner sceneRefCleaner, SessionReferenceCleaner sessionRefCleaner,
                          EncounterRepository encounterRepository, CombatantRepository combatantRepository,
                          TokenRepository tokenRepository, StatBlockRepository statBlockRepository,
                          PartyMemberRepository partyMemberRepository, EntityManager entityManager) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.sceneRefCleaner = sceneRefCleaner;
        this.sessionRefCleaner = sessionRefCleaner;
        this.encounterRepository = encounterRepository;
        this.combatantRepository = combatantRepository;
        this.tokenRepository = tokenRepository;
        this.statBlockRepository = statBlockRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.entityManager = entityManager;
        this.objectMapper = JsonMapper.builder().build();
    }

    public GameMap create(UUID campaignId, String name, int gridWidth, int gridHeight, int cellSizePx) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName(name);
        map.setGridWidth(gridWidth);
        map.setGridHeight(gridHeight);
        map.setCellSizePx(cellSizePx);
        map.setSortOrder((int) repository.countByCampaignId(campaignId));
        map.setDocument(objectMapper.writeValueAsString(
                MapDocumentDto.createDefault(gridWidth, gridHeight, cellSizePx)));
        return repository.save(map);
    }

    @Transactional(readOnly = true)
    public GameMap findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Map not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<GameMap> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderBySortOrderAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public MapDocumentDto getDocument(UUID mapId) {
        GameMap map = findById(mapId);
        if (map.getDocument() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(map.getDocument(), MapDocumentDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse map document: " + mapId, e);
        }
    }

    /** Whole-document replace with optimistic version check (SPEC §5). Returns the new version. */
    public long updateDocument(UUID mapId, String documentJson, long expectedVersion) {
        GameMap map = findById(mapId);
        if (map.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Map " + mapId + " changed concurrently: expected version " + expectedVersion
                    + " but is " + map.getVersion());
        }

        MapDocumentDto doc;
        try {
            doc = objectMapper.readValue(documentJson, MapDocumentDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid map document JSON: " + e.getMessage(), e);
        }

        String jsonToSave = documentJson;
        if (doc.schemaVersion() == 1) {
            // v1→v2 migration: bump schema version
            var migrated = new MapDocumentDto(
                    MapDocumentDto.CURRENT_SCHEMA_VERSION,
                    doc.grid(), doc.layers(), doc.primitives(), doc.customTerrain()
            );
            try {
                jsonToSave = objectMapper.writeValueAsString(migrated);
            } catch (Exception e) {
                throw new RuntimeException("Failed to migrate map document v1→v2", e);
            }
        } else if (doc.schemaVersion() != MapDocumentDto.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported schemaVersion: " + doc.schemaVersion()
                    + ". Expected: " + MapDocumentDto.CURRENT_SCHEMA_VERSION);
        }

        validateDocumentGridMatchesMap(doc, map);
        map.setDocument(jsonToSave);
        repository.saveAndFlush(map);
        return map.getVersion();
    }

    public GameMap update(UUID mapId, String name, int gridWidth, int gridHeight, int cellSizePx) {
        GameMap map = findById(mapId);
        if (gridWidth != map.getGridWidth() || gridHeight != map.getGridHeight()
                || cellSizePx != map.getCellSizePx()) {
            throw new IllegalArgumentException(
                    "Grid dimensions must be changed through the versioned settings endpoint");
        }
        map.setName(name);
        return repository.save(map);
    }

    public record MapSettingsResult(long version, GameMap map, MapDocumentDto document) {}

    private final MapGridResizeService gridResizeService = new MapGridResizeService();

    public MapSettingsResult updateSettings(UUID mapId, MapSettingsCommand command) {
        validateSettings(command);
        GameMap map = findById(mapId);
        if (map.getVersion() != command.expectedVersion()) {
            throw new OptimisticLockingFailureException(
                    "Map " + mapId + " changed concurrently: expected version " + command.expectedVersion()
                    + " but is " + map.getVersion());
        }

        MapDocumentDto persistedDocument = getDocument(mapId);
        MapDocumentDto doc = command.document() != null ? command.document() : persistedDocument;
        if (doc == null) {
            throw new IllegalArgumentException("Map document not found for " + mapId);
        }
        validateSettingsSourceGrid(doc, map, command);

        MapDocumentDto resized = gridResizeService.resize(
                doc, command.gridWidth(), command.gridHeight(),
                gridMatches(doc.grid(), command.gridWidth(), command.gridHeight(), command.cellSizePx())
                        ? MapSettingsCommand.ResizeMode.PRESERVE
                        : command.resizeMode(),
                command.shapeRemovals());

        MapDocumentDto.GridDto gridWithCellSize = new MapDocumentDto.GridDto(
                resized.grid().width(), resized.grid().height(), command.cellSizePx(),
                resized.grid().gridType(), resized.grid().movementMode(), resized.grid().showGrid());

        MapDocumentDto docWithUpdatedGrid = new MapDocumentDto(
                resized.schemaVersion(), gridWithCellSize,
                resized.layers(), resized.primitives(), resized.customTerrain());

        List<Token> mapTokens = tokenRepository.findByMapIdOrderByNameAsc(mapId);
        if (command.tokenRestoreIds() != null
                || command.tokenSnapshot() != null
                || command.expectedTokenSnapshot() != null) {
            if (command.tokenRestoreIds() == null || command.tokenSnapshot() == null) {
                throw new IllegalArgumentException(
                        "Token history restoration requires restore ids and snapshots");
            }
            restoreTokenSnapshot(map, mapTokens, command.tokenRestoreIds(), command.tokenSnapshot(),
                    command.expectedTokenSnapshot(),
                    command.gridWidth(), command.gridHeight(), command.cellSizePx());
        } else {
            applyTokenResolutions(mapId, map, mapTokens, command);
        }

        try {
            map.setDocument(objectMapper.writeValueAsString(docWithUpdatedGrid));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize resized document", e);
        }
        map.setGridWidth(command.gridWidth());
        map.setGridHeight(command.gridHeight());
        map.setCellSizePx(command.cellSizePx());
        repository.saveAndFlush(map);

        return new MapSettingsResult(map.getVersion(), map, docWithUpdatedGrid);
    }

    private void validateDocumentGridMatchesMap(MapDocumentDto document, GameMap map) {
        if (document.grid() == null
                || document.grid().width() != map.getGridWidth()
                || document.grid().height() != map.getGridHeight()
                || document.grid().cellSizePx() != map.getCellSizePx()
                || document.grid().gridType() == null
                || !"square".equalsIgnoreCase(document.grid().gridType())) {
            throw new IllegalArgumentException(
                    "Map document grid must match map settings "
                            + map.getGridWidth() + "x" + map.getGridHeight() + "@" + map.getCellSizePx());
        }
    }

    private void validateSettings(MapSettingsCommand command) {
        if (command == null || command.resizeMode() == null) {
            throw new IllegalArgumentException("Settings and resizeMode are required");
        }
        if (command.gridWidth() < 5 || command.gridWidth() > 100
                || command.gridHeight() < 5 || command.gridHeight() > 100) {
            throw new IllegalArgumentException("Grid width and height must be between 5 and 100");
        }
        if (command.cellSizePx() < 16 || command.cellSizePx() > 128) {
            throw new IllegalArgumentException("cellSizePx must be between 16 and 128");
        }
        if ((command.cellSizePx() - 16) % 8 != 0) {
            throw new IllegalArgumentException("cellSizePx must use increments of 8 starting at 16");
        }
    }

    private void validateSettingsSourceGrid(MapDocumentDto document, GameMap map,
                                            MapSettingsCommand command) {
        if (document.schemaVersion() != MapDocumentDto.CURRENT_SCHEMA_VERSION || document.grid() == null
                || document.grid().gridType() == null
                || !"square".equalsIgnoreCase(document.grid().gridType())) {
            throw new IllegalArgumentException("Settings document must use the current square-grid schema");
        }
        boolean currentGrid = gridMatches(document.grid(),
                map.getGridWidth(), map.getGridHeight(), map.getCellSizePx());
        boolean restoredGrid = gridMatches(document.grid(),
                command.gridWidth(), command.gridHeight(), command.cellSizePx());
        if (!currentGrid && !restoredGrid) {
            throw new IllegalArgumentException(
                    "Settings document grid must match either current or requested map settings");
        }
    }

    private boolean gridMatches(MapDocumentDto.GridDto grid, int width, int height, int cellSizePx) {
        return grid.width() == width && grid.height() == height && grid.cellSizePx() == cellSizePx;
    }

    private Map<UUID, MapSettingsCommand.TokenResolution> resolutionsByToken(MapSettingsCommand command) {
        Map<UUID, MapSettingsCommand.TokenResolution> result = new HashMap<>();
        for (var resolution : command.tokenResolutions() != null ? command.tokenResolutions() : List.<MapSettingsCommand.TokenResolution>of()) {
            if (resolution == null || resolution.tokenId() == null || resolution.action() == null) {
                throw new IllegalArgumentException("Token resolutions require tokenId and action");
            }
            if (result.put(resolution.tokenId(), resolution) != null) {
                throw new IllegalArgumentException("Duplicate token resolution: " + resolution.tokenId());
            }
        }
        return result;
    }

    private void applyTokenResolutions(UUID mapId, GameMap map, List<Token> mapTokens,
                                       MapSettingsCommand command) {
        Map<UUID, MapSettingsCommand.TokenResolution> resolutions = resolutionsByToken(command);
        double tokenScale = (double) command.cellSizePx() / map.getCellSizePx();
        for (Token token : mapTokens) {
            int projectedX = (int) Math.round(token.getPositionX() * tokenScale);
            int projectedY = (int) Math.round(token.getPositionY() * tokenScale);
            boolean affected = tokenOutside(token, projectedX, projectedY,
                    command.gridWidth(), command.gridHeight(), command.cellSizePx());
            var resolution = resolutions.remove(token.getId());
            if (affected && resolution == null) {
                throw new IllegalArgumentException(
                        "Token " + token.getId() + " requires an explicit resolution for the new grid");
            }
            if (!affected && resolution != null) {
                throw new IllegalArgumentException(
                        "Token " + token.getId() + " is not affected by the new grid");
            }
            if (resolution != null) {
                switch (resolution.action()) {
                    case MOVE -> {
                        validateTokenPosition(token, resolution.positionX(), resolution.positionY(),
                                command.gridWidth(), command.gridHeight(), command.cellSizePx());
                        token.setPositionX(resolution.positionX());
                        token.setPositionY(resolution.positionY());
                        tokenRepository.save(token);
                    }
                    case REMOVE -> deleteToken(token);
                }
            } else if (projectedX != token.getPositionX() || projectedY != token.getPositionY()) {
                token.setPositionX(projectedX);
                token.setPositionY(projectedY);
                tokenRepository.save(token);
            }
        }
        if (!resolutions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Token resolutions do not belong to map " + mapId + ": " + resolutions.keySet());
        }
    }

    private void restoreTokenSnapshot(GameMap map, List<Token> current,
                                      List<UUID> tokenRestoreIds,
                                      List<MapSettingsCommand.TokenSnapshot> requested,
                                      List<MapSettingsCommand.TokenSnapshot> expected,
                                      int width, int height, int cellSizePx) {
        Map<UUID, Token> currentById = new HashMap<>();
        current.forEach(token -> currentById.put(token.getId(), token));
        var restoreIds = new java.util.HashSet<>(tokenRestoreIds);
        if (restoreIds.size() != tokenRestoreIds.size() || restoreIds.contains(null)) {
            throw new IllegalArgumentException("Token restore ids contain a missing or duplicate id");
        }
        Map<UUID, MapSettingsCommand.TokenSnapshot> desired = new HashMap<>();
        for (var snapshot : requested) {
            if (snapshot == null || snapshot.id() == null
                    || desired.put(snapshot.id(), snapshot) != null) {
                throw new IllegalArgumentException("Token snapshot contains a missing or duplicate id");
            }
        }
        if (!restoreIds.containsAll(desired.keySet())) {
            throw new IllegalArgumentException("Token snapshots must be limited to the requested restore ids");
        }
        validateExpectedTokenState(restoreIds, currentById, expected);
        for (UUID restoreId : restoreIds) {
            Token token = currentById.get(restoreId);
            if (token != null && !desired.containsKey(restoreId)) deleteToken(token);
        }
        for (var snapshot : desired.values()) {
            Token token = currentById.get(snapshot.id());
            boolean recreated = false;
            if (token == null) {
                token = tokenRepository.findById(snapshot.id()).orElse(null);
                if (token != null && !token.getMap().getId().equals(map.getId())) {
                    throw new IllegalArgumentException(
                            "Token snapshot id belongs to another map: " + snapshot.id());
                }
                if (token == null) {
                    validateTokenSnapshotReferences(snapshot);
                    validateTokenSnapshotPosition(snapshot, width, height, cellSizePx);
                    insertRestoredToken(map, snapshot);
                    entityManager.flush();
                    token = tokenRepository.findById(snapshot.id())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Restored token was not persisted: " + snapshot.id()));
                    recreated = true;
                }
            }
            if (!recreated) {
                applyTokenSnapshot(token, snapshot);
                validateTokenPosition(token, snapshot.positionX(), snapshot.positionY(),
                        width, height, cellSizePx);
                tokenRepository.save(token);
            }
            restoreCombatantLinks(token, snapshot.combatantIds());
        }
    }

    private void validateExpectedTokenState(
            java.util.Set<UUID> restoreIds, Map<UUID, Token> currentById,
            List<MapSettingsCommand.TokenSnapshot> expected) {
        if (expected == null) {
            throw new IllegalArgumentException("Token history restoration requires expected token state");
        }
        Map<UUID, MapSettingsCommand.TokenSnapshot> expectedById = new HashMap<>();
        for (var snapshot : expected) {
            if (snapshot == null || snapshot.id() == null
                    || expectedById.put(snapshot.id(), snapshot) != null
                    || !restoreIds.contains(snapshot.id())) {
                throw new IllegalArgumentException("Expected token state contains an invalid id");
            }
        }
        for (UUID restoreId : restoreIds) {
            Token current = currentById.get(restoreId);
            var expectedToken = expectedById.get(restoreId);
            if ((current == null) != (expectedToken == null)
                    || current != null && !tokenMatchesSnapshot(current, expectedToken)) {
                throw new OptimisticLockingFailureException(
                        "Token " + restoreId + " changed since the grid resize");
            }
        }
    }

    private boolean tokenMatchesSnapshot(Token token, MapSettingsCommand.TokenSnapshot snapshot) {
        return java.util.Objects.equals(token.getName(), snapshot.name())
                && java.util.Objects.equals(token.getKind(), snapshot.kind())
                && token.getPositionX() == snapshot.positionX()
                && token.getPositionY() == snapshot.positionY()
                && token.getSizeCols() == Math.max(1, snapshot.sizeCols())
                && token.getSizeRows() == Math.max(1, snapshot.sizeRows())
                && java.util.Objects.equals(token.getColor(), snapshot.color())
                && token.isHidden() == snapshot.hidden()
                && java.util.Objects.equals(token.getCurrentHp(), snapshot.currentHp())
                && java.util.Objects.equals(token.getMaxHp(), snapshot.maxHp())
                && token.isDead() == snapshot.dead()
                && java.util.Objects.equals(
                    token.getStatBlock() != null ? token.getStatBlock().getId() : null,
                    snapshot.statBlockId())
                && java.util.Objects.equals(
                    token.getPartyMember() != null ? token.getPartyMember().getId() : null,
                    snapshot.partyMemberId())
                && java.util.Objects.equals(token.getNotes(), snapshot.notes())
                && java.util.Objects.equals(token.getIcon(), snapshot.icon())
                && new java.util.HashSet<>(combatantRepository.findByTokenId(token.getId()).stream()
                    .map(combatant -> combatant.getId()).toList())
                    .equals(new java.util.HashSet<>(
                        snapshot.combatantIds() != null ? snapshot.combatantIds() : List.<UUID>of()));
    }

    private void restoreCombatantLinks(Token token, List<UUID> requestedCombatantIds) {
        var desiredIds = new java.util.HashSet<>(
                requestedCombatantIds != null ? requestedCombatantIds : List.<UUID>of());
        for (var combatant : combatantRepository.findByTokenId(token.getId())) {
            if (!desiredIds.remove(combatant.getId())) {
                combatant.setToken(null);
                combatantRepository.save(combatant);
            }
        }
        for (UUID combatantId : desiredIds) {
            var combatant = combatantRepository.findById(combatantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Combatant not found: " + combatantId));
            if (combatant.getToken() != null && !combatant.getToken().getId().equals(token.getId())) {
                throw new IllegalArgumentException(
                        "Combatant is linked to another token: " + combatantId);
            }
            combatant.setToken(token);
            combatantRepository.save(combatant);
        }
    }

    private void validateTokenSnapshotReferences(MapSettingsCommand.TokenSnapshot snapshot) {
        if (snapshot.statBlockId() != null && !statBlockRepository.existsById(snapshot.statBlockId())) {
            throw new IllegalArgumentException("Stat block not found: " + snapshot.statBlockId());
        }
        if (snapshot.partyMemberId() != null && !partyMemberRepository.existsById(snapshot.partyMemberId())) {
            throw new IllegalArgumentException("Party member not found: " + snapshot.partyMemberId());
        }
    }

    private void validateTokenSnapshotPosition(MapSettingsCommand.TokenSnapshot snapshot,
                                               int width, int height, int cellSizePx) {
        int sizeCols = Math.max(1, snapshot.sizeCols());
        int sizeRows = Math.max(1, snapshot.sizeRows());
        if (snapshot.positionX() < 0 || snapshot.positionY() < 0
                || snapshot.positionX() + sizeCols * cellSizePx > width * cellSizePx
                || snapshot.positionY() + sizeRows * cellSizePx > height * cellSizePx) {
            throw new IllegalArgumentException(
                    "Token snapshot position is outside the restored grid: " + snapshot.id());
        }
    }

    private void insertRestoredToken(GameMap map, MapSettingsCommand.TokenSnapshot snapshot) {
        entityManager.createNativeQuery("""
                insert into token (
                    id, map_id, name, kind, positionx, positiony, size_cols, size_rows,
                    color, hidden, current_hp, max_hp, dead, statblock_id, party_member_id,
                    notes, icon
                ) values (
                    :id, :mapId, :name, :kind, :positionX, :positionY, :sizeCols, :sizeRows,
                    :color, :hidden, :currentHp, :maxHp, :dead, :statBlockId, :partyMemberId,
                    :notes, :icon
                )
                """)
                .setParameter("id", snapshot.id())
                .setParameter("mapId", map.getId())
                .setParameter("name", snapshot.name() != null ? snapshot.name() : "Token")
                .setParameter("kind", snapshot.kind() != null ? snapshot.kind() : "NPC")
                .setParameter("positionX", snapshot.positionX())
                .setParameter("positionY", snapshot.positionY())
                .setParameter("sizeCols", Math.max(1, snapshot.sizeCols()))
                .setParameter("sizeRows", Math.max(1, snapshot.sizeRows()))
                .setParameter("color", snapshot.color() != null ? snapshot.color() : "#7b68ee")
                .setParameter("hidden", snapshot.hidden())
                .setParameter("currentHp", snapshot.currentHp())
                .setParameter("maxHp", snapshot.maxHp())
                .setParameter("dead", snapshot.dead())
                .setParameter("statBlockId", snapshot.statBlockId())
                .setParameter("partyMemberId", snapshot.partyMemberId())
                .setParameter("notes", snapshot.notes())
                .setParameter("icon", snapshot.icon())
                .executeUpdate();
    }

    private void applyTokenSnapshot(Token token, MapSettingsCommand.TokenSnapshot snapshot) {
        token.setName(snapshot.name() != null ? snapshot.name() : "Token");
        token.setKind(snapshot.kind() != null ? snapshot.kind() : "NPC");
        token.setPositionX(snapshot.positionX());
        token.setPositionY(snapshot.positionY());
        token.setSizeCols(Math.max(1, snapshot.sizeCols()));
        token.setSizeRows(Math.max(1, snapshot.sizeRows()));
        token.setColor(snapshot.color() != null ? snapshot.color() : "#7b68ee");
        token.setHidden(snapshot.hidden());
        token.setCurrentHp(snapshot.currentHp());
        token.setMaxHp(snapshot.maxHp());
        token.setDead(snapshot.dead());
        token.setNotes(snapshot.notes());
        token.setIcon(snapshot.icon());
        token.setStatBlock(snapshot.statBlockId() != null
                ? statBlockRepository.findById(snapshot.statBlockId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Stat block not found: " + snapshot.statBlockId()))
                : null);
        token.setPartyMember(snapshot.partyMemberId() != null
                ? partyMemberRepository.findById(snapshot.partyMemberId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Party member not found: " + snapshot.partyMemberId()))
                : null);
    }

    private void deleteToken(Token token) {
        for (var combatant : combatantRepository.findByTokenId(token.getId())) {
            combatant.setToken(null);
            combatantRepository.save(combatant);
        }
        tokenRepository.delete(token);
    }

    private boolean tokenOutside(Token token, int x, int y, int width, int height, int cellSizePx) {
        int maxX = width * cellSizePx;
        int maxY = height * cellSizePx;
        return x < 0 || y < 0
                || x + token.getSizeCols() * cellSizePx > maxX
                || y + token.getSizeRows() * cellSizePx > maxY;
    }

    private void validateTokenPosition(Token token, int x, int y, int width, int height, int cellSizePx) {
        int maxX = width * cellSizePx;
        int maxY = height * cellSizePx;
        if (x < 0 || y < 0
                || x + token.getSizeCols() * cellSizePx > maxX
                || y + token.getSizeRows() * cellSizePx > maxY) {
            throw new IllegalArgumentException(
                    "MOVE destination for token " + token.getId() + " is outside the new grid");
        }
    }

    public GameMap updateMode(UUID mapId, String movementMode, Boolean showGrid) {
        GameMap map = findById(mapId);
        if (movementMode != null) {
            if (!movementMode.equals("GRID") && !movementMode.equals("FREEFORM")) {
                throw new IllegalArgumentException("Invalid movementMode: " + movementMode);
            }
            map.setMovementMode(movementMode);
        }
        if (showGrid != null) {
            map.setShowGrid(showGrid);
        }
        return repository.save(map);
    }

    public void delete(UUID mapId) {
        sessionRefCleaner.detachMap(mapId);
        sceneRefCleaner.detachMap(mapId);
        GameMap map = findById(mapId);
        UUID campaignId = map.getCampaign().getId();

        for (var encounter : encounterRepository.findByMapIdOrderByNameAsc(mapId)) {
            encounter.setMap(null);
            encounterRepository.save(encounter);
        }
        for (var token : tokenRepository.findByMapIdOrderByNameAsc(mapId)) {
            for (var combatant : combatantRepository.findByTokenId(token.getId())) {
                combatant.setToken(null);
                combatantRepository.save(combatant);
            }
            tokenRepository.delete(token);
        }
        repository.delete(map);

        var remaining = repository.findByCampaignIdOrderBySortOrderAsc(campaignId);
        for (int i = 0; i < remaining.size(); i++) {
            var m = remaining.get(i);
            if (m.getSortOrder() != i) {
                m.setSortOrder(i);
                repository.save(m);
            }
        }
    }
}
