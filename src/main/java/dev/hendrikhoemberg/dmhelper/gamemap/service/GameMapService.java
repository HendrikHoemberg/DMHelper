package dev.hendrikhoemberg.dmhelper.gamemap.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GameMapService {

    private final GameMapRepository repository;
    private final CampaignRepository campaignRepository;
    private final ObjectMapper objectMapper;

    public GameMapService(GameMapRepository repository, CampaignRepository campaignRepository) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
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
        if (doc.schemaVersion() != MapDocumentDto.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported schemaVersion: " + doc.schemaVersion()
                    + ". Expected: " + MapDocumentDto.CURRENT_SCHEMA_VERSION);
        }

        map.setDocument(documentJson);
        repository.saveAndFlush(map);
        return map.getVersion();
    }

    public GameMap update(UUID mapId, String name, int gridWidth, int gridHeight, int cellSizePx) {
        GameMap map = findById(mapId);
        map.setName(name);
        map.setGridWidth(gridWidth);
        map.setGridHeight(gridHeight);
        map.setCellSizePx(cellSizePx);
        return repository.save(map);
    }

    public void delete(UUID mapId) {
        GameMap map = findById(mapId);
        UUID campaignId = map.getCampaign().getId();
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
