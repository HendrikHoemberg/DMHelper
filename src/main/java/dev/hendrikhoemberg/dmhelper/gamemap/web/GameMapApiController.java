package dev.hendrikhoemberg.dmhelper.gamemap.web;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GameMapApiController {

    private final GameMapService service;

    public GameMapApiController(GameMapService service) {
        this.service = service;
    }

    record MapRequest(String name, Integer gridWidth, Integer gridHeight, Integer cellSizePx) {}

    record GameMapDto(UUID id, String name, int gridWidth, int gridHeight,
                      int cellSizePx, int sortOrder, String gridType, long version) {
        static GameMapDto from(GameMap m) {
            return new GameMapDto(m.getId(), m.getName(), m.getGridWidth(), m.getGridHeight(),
                    m.getCellSizePx(), m.getSortOrder(), m.getGridType(), m.getVersion());
        }
    }

    record MapDocumentResponse(long version, MapDocumentDto document) {}

    record SaveDocumentResponse(long version) {}

    @GetMapping("/campaigns/{campaignId}/maps")
    public List<GameMapDto> listMaps(@PathVariable UUID campaignId) {
        return service.findByCampaignId(campaignId).stream().map(GameMapDto::from).toList();
    }

    @PostMapping("/campaigns/{campaignId}/maps")
    public ResponseEntity<GameMapDto> createMap(@PathVariable UUID campaignId,
                                                @RequestBody MapRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Map name is required");
        }
        GameMap map = service.create(campaignId, request.name(),
                request.gridWidth() != null ? request.gridWidth() : 30,
                request.gridHeight() != null ? request.gridHeight() : 20,
                request.cellSizePx() != null ? request.cellSizePx() : 48);
        return ResponseEntity.status(HttpStatus.CREATED).body(GameMapDto.from(map));
    }

    @GetMapping("/maps/{id}")
    public GameMapDto getMap(@PathVariable UUID id) {
        return GameMapDto.from(service.findById(id));
    }

    @GetMapping("/maps/{id}/document")
    public MapDocumentResponse getDocument(@PathVariable UUID id) {
        GameMap map = service.findById(id);
        return new MapDocumentResponse(map.getVersion(), service.getDocument(id));
    }

    @PutMapping("/maps/{id}/document")
    public SaveDocumentResponse saveDocument(@PathVariable UUID id,
                                             @RequestParam long expectedVersion,
                                             @RequestBody String documentJson) {
        long newVersion = service.updateDocument(id, documentJson, expectedVersion);
        return new SaveDocumentResponse(newVersion);
    }

    @PutMapping("/maps/{id}")
    public GameMapDto updateMap(@PathVariable UUID id, @RequestBody MapRequest request) {
        GameMap existing = service.findById(id);
        GameMap updated = service.update(id,
                request.name() != null && !request.name().isBlank() ? request.name() : existing.getName(),
                request.gridWidth() != null ? request.gridWidth() : existing.getGridWidth(),
                request.gridHeight() != null ? request.gridHeight() : existing.getGridHeight(),
                request.cellSizePx() != null ? request.cellSizePx() : existing.getCellSizePx());
        return GameMapDto.from(updated);
    }

    @DeleteMapping("/maps/{id}")
    public ResponseEntity<Void> deleteMap(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
