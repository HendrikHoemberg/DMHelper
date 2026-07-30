package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterTokenPlacement;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterTokenPlacementRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapRuntimeChanged;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class EncounterPlacementService {
    private static final tools.jackson.databind.ObjectMapper JSON = JsonMapper.builder().build();

    private final EncounterTokenPlacementRepository placementRepo;
    private final EncounterRepository encounterRepo;
    private final CombatantRepository combatantRepo;
    private final PartyMemberRepository partyMemberRepo;
    private final GameMapRepository mapRepo;
    private final ApplicationEventPublisher events;

    public EncounterPlacementService(EncounterTokenPlacementRepository placementRepo,
                                     EncounterRepository encounterRepo,
                                     CombatantRepository combatantRepo,
                                     PartyMemberRepository partyMemberRepo,
                                     GameMapRepository mapRepo,
                                     ApplicationEventPublisher events) {
        this.placementRepo = placementRepo;
        this.encounterRepo = encounterRepo;
        this.combatantRepo = combatantRepo;
        this.partyMemberRepo = partyMemberRepo;
        this.mapRepo = mapRepo;
        this.events = events;
    }

    public record PlacementDto(UUID id, UUID encounterId, UUID combatantId, UUID mapId,
                               int positionX, int positionY, int sizeCols, int sizeRows,
                               String color, String icon) {}

    public record PlacementUpsertRequest(int positionX, int positionY, int sizeCols, int sizeRows,
                                         String color, String icon) {}

    public record PlacementMoveRequest(int positionX, int positionY) {}

    public record ChangeEncounterMapRequest(UUID mapId) {}

    public record EncounterReadinessDto(UUID encounterId, boolean canRun, ReadinessVerdict verdict, UUID mapId,
                                        int combatantCount, int placedCombatantCount, int unplacedCombatantCount,
                                        List<ReadinessIssueDto> issues) {}

    public record ReadinessIssueDto(String code, String message, String severity) {}

    public PlacementDto upsert(UUID encounterId, UUID combatantId, PlacementUpsertRequest request) {
        Encounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        Combatant combatant = combatantRepo.findById(combatantId)
                .orElseThrow(() -> new NotFoundException("Combatant not found: " + combatantId));

        if (!combatant.getEncounter().getId().equals(encounterId)) {
            throw new IllegalArgumentException("Combatant does not belong to encounter");
        }

        GameMap map = encounter.getMap();
        if (map == null) {
            throw new IllegalStateException("Encounter has no map assigned");
        }

        int sizeCols = Math.max(1, request.sizeCols());
        int sizeRows = Math.max(1, request.sizeRows());

        int maxX = Math.max(0, (map.getGridWidth() - sizeCols) * map.getCellSizePx());
        int maxY = Math.max(0, (map.getGridHeight() - sizeRows) * map.getCellSizePx());
        int x = clamp(request.positionX(), maxX);
        int y = clamp(request.positionY(), maxY);

        EncounterTokenPlacement placement = placementRepo.findByCombatantId(combatantId)
                .orElseGet(() -> {
                    EncounterTokenPlacement p = new EncounterTokenPlacement();
                    p.setEncounter(encounter);
                    p.setCombatant(combatant);
                    p.setMap(map);
                    return p;
                });

        placement.setPositionX(x);
        placement.setPositionY(y);
        placement.setSizeCols(sizeCols);
        placement.setSizeRows(sizeRows);
        if (request.color() != null) {
            placement.setColor(request.color());
        }
        if (request.icon() != null) {
            placement.setIcon(request.icon());
        }
        placement.setMap(map);

        EncounterTokenPlacement saved = placementRepo.save(placement);
        publish(map);
        return toDto(saved);
    }

    public PlacementDto move(UUID encounterId, UUID combatantId, int positionX, int positionY) {
        Encounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        EncounterTokenPlacement placement = placementRepo.findByCombatantId(combatantId)
                .orElseThrow(() -> new NotFoundException("Placement not found for combatant: " + combatantId));
        if (!placement.getEncounter().getId().equals(encounterId)
                || !placement.getCombatant().getId().equals(combatantId)) {
            throw new IllegalArgumentException("Placement does not belong to encounter");
        }

        GameMap map = encounter.getMap();
        if (map == null) {
            throw new IllegalStateException("Encounter has no map assigned");
        }

        int maxX = Math.max(0, (map.getGridWidth() - placement.getSizeCols()) * map.getCellSizePx());
        int maxY = Math.max(0, (map.getGridHeight() - placement.getSizeRows()) * map.getCellSizePx());
        int x = clamp(positionX, maxX);
        int y = clamp(positionY, maxY);

        placement.setPositionX(x);
        placement.setPositionY(y);
        EncounterTokenPlacement saved = placementRepo.save(placement);
        publish(map);
        return toDto(saved);
    }

    public void remove(UUID encounterId, UUID combatantId) {
        Optional<EncounterTokenPlacement> optPlacement = placementRepo.findByCombatantId(combatantId);
        if (optPlacement.isPresent()) {
            EncounterTokenPlacement placement = optPlacement.get();
            if (!placement.getEncounter().getId().equals(encounterId)) {
                throw new IllegalArgumentException("Placement does not belong to encounter");
            }
            placementRepo.delete(placement);
            publish(placement.getMap());
        }

        combatantRepo.findById(combatantId).ifPresent(combatant -> {
            combatant.setPlacement(null);
            combatantRepo.save(combatant);
        });
    }

    public List<PlacementDto> list(UUID encounterId) {
        return placementRepo.findByEncounterIdOrderByCombatant_SortOrderAsc(encounterId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<PlacementDto> autoPlaceUnplaced(UUID encounterId) {
        Encounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        GameMap map = encounter.getMap();
        if (map == null) return List.of();

        List<Combatant> allCombatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        List<PlacementDto> created = new ArrayList<>();

        Set<String> occupiedCells = buildOccupiedCells(encounterId, map.getCellSizePx());

        for (Combatant combatant : allCombatants) {
            if (placementRepo.findByCombatantId(combatant.getId()).isPresent()) continue;
            if (combatant.getWave() != null && combatant.getWave().getStatus() != dev.hendrikhoemberg.dmhelper.encounter.data.WaveStatus.ACTIVE) continue;

            int[] cell = preferredOrFreeCell(
                    combatant, map, occupiedCells);
            if (cell == null) continue;

            int px = cell[0] * map.getCellSizePx();
            int py = cell[1] * map.getCellSizePx();

            occupiedCells.add(cell[0] + "," + cell[1]);

            EncounterTokenPlacement placement = new EncounterTokenPlacement();
            placement.setEncounter(encounter);
            placement.setCombatant(combatant);
            placement.setMap(map);
            placement.setPositionX(px);
            placement.setPositionY(py);
            placement.setSizeCols(1);
            placement.setSizeRows(1);
            placement.setColor(defaultColor(combatant.getKind()));
            EncounterTokenPlacement saved = placementRepo.save(placement);
            created.add(toDto(saved));
        }
        if (!created.isEmpty()) publish(map);
        return created;
    }

    public List<PlacementDto> placeUnplacedPartyCombatants(UUID encounterId) {
        Encounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        GameMap map = encounter.getMap();
        if (map == null) return List.of();

        List<Combatant> allCombatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);

        Set<String> occupiedCells = buildOccupiedCells(encounterId, map.getCellSizePx());

        List<PlacementDto> created = new ArrayList<>();
        for (Combatant combatant : allCombatants) {
            if (combatant.getPartyMember() == null) continue;
            if (placementRepo.findByCombatantId(combatant.getId()).isPresent()) continue;

            int[] cell = findFreeCell(occupiedCells, map.getGridWidth(), map.getGridHeight(), 1, 1);
            if (cell == null) continue;

            int px = cell[0] * map.getCellSizePx();
            int py = cell[1] * map.getCellSizePx();

            occupiedCells.add(cell[0] + "," + cell[1]);

            EncounterTokenPlacement placement = new EncounterTokenPlacement();
            placement.setEncounter(encounter);
            placement.setCombatant(combatant);
            placement.setMap(map);
            placement.setPositionX(px);
            placement.setPositionY(py);
            placement.setSizeCols(1);
            placement.setSizeRows(1);
            placement.setColor(defaultColor(combatant.getKind()));
            EncounterTokenPlacement saved = placementRepo.save(placement);
            created.add(toDto(saved));
        }
        if (!created.isEmpty()) publish(map);
        return created;
    }

    public static ReadinessVerdict verdictFor(boolean hasMap, boolean placementMapMismatch,
                                              int combatantCount, int unplacedCount) {
        if (!hasMap || placementMapMismatch) return ReadinessVerdict.BLOCKED;
        if (combatantCount == 0 || unplacedCount > 0) return ReadinessVerdict.RUNNABLE_WITH_NOTES;
        return ReadinessVerdict.RUNNABLE;
    }

    public EncounterReadinessDto readiness(UUID encounterId) {
        Encounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        List<ReadinessIssueDto> issues = new ArrayList<>();
        boolean hasError = false;
        boolean mismatch = false;

        if (encounter.getMap() == null) {
            issues.add(new ReadinessIssueDto("MISSING_MAP", "Encounter has no map assigned", "ERROR"));
            hasError = true;
        }

        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        if (combatants.isEmpty()) {
            issues.add(new ReadinessIssueDto("EMPTY_ROSTER", "Encounter has no combatants", "WARNING"));
        }

        List<EncounterTokenPlacement> allPlacements = placementRepo
                .findByEncounterIdOrderByCombatant_SortOrderAsc(encounterId);
        int placedCount = allPlacements.size();
        int unplacedCount = combatants.size() - placedCount;

        if (unplacedCount > 0) {
            issues.add(new ReadinessIssueDto("UNPLACED_COMBATANTS",
                    placedCount + " placed, " + unplacedCount + " unplaced combatants", "WARNING"));
        }

        if (encounter.getMap() != null) {
            UUID mapId = encounter.getMap().getId();
            for (EncounterTokenPlacement p : allPlacements) {
                if (!p.getMap().getId().equals(mapId)) {
                    issues.add(new ReadinessIssueDto("PLACEMENT_MAP_MISMATCH",
                            "Placement " + p.getId() + " references a different map", "ERROR"));
                    hasError = true;
                    mismatch = true;
                    break;
                }
            }
        }

        ReadinessVerdict verdict = verdictFor(encounter.getMap() != null, mismatch,
                combatants.size(), unplacedCount);
        return new EncounterReadinessDto(encounterId, verdict != ReadinessVerdict.BLOCKED, verdict,
                encounter.getMap() != null ? encounter.getMap().getId() : null,
                combatants.size(), placedCount, unplacedCount, issues);
    }

    public EncounterReadinessDto changeMapAndResetPlacements(UUID encounterId, UUID mapId) {
        GameMap map = mapRepo.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Map not found: " + mapId));
        Encounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
        if (!map.getCampaign().getId().equals(encounter.getCampaign().getId())) {
            throw new IllegalArgumentException("Map does not belong to encounter campaign");
        }

        placementRepo.findByEncounterIdOrderByCombatant_SortOrderAsc(encounterId)
                .forEach(p -> placementRepo.delete(p));

        encounter.setMap(map);
        encounterRepo.save(encounter);
        publish(map);

        return readiness(encounterId);
    }

    private PlacementDto toDto(EncounterTokenPlacement p) {
        return new PlacementDto(p.getId(), p.getEncounter().getId(), p.getCombatant().getId(),
                p.getMap().getId(), p.getPositionX(), p.getPositionY(),
                p.getSizeCols(), p.getSizeRows(), p.getColor(), p.getIcon());
    }

    private void publish(GameMap map) {
        events.publishEvent(new MapRuntimeChanged(map.getCampaign().getId(), map.getId()));
    }

    static String defaultColor(String kind) {
        return switch (kind == null ? "NPC" : kind) {
            case "PC" -> "#4a9eff";
            case "MONSTER" -> "#d95c5c";
            case "OBJECT" -> "#8a8a8a";
            default -> "#7b68ee";
        };
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private Set<String> buildOccupiedCells(UUID encounterId, int cellSizePx) {
        Set<String> occupiedCells = new HashSet<>();
        for (EncounterTokenPlacement existing : placementRepo.findByEncounterIdOrderByCombatant_SortOrderAsc(encounterId)) {
            for (int dc = 0; dc < existing.getSizeCols(); dc++) {
                for (int dr = 0; dr < existing.getSizeRows(); dr++) {
                    int cellCol = (existing.getPositionX() / cellSizePx) + dc;
                    int cellRow = (existing.getPositionY() / cellSizePx) + dr;
                    occupiedCells.add(cellCol + "," + cellRow);
                }
            }
        }
        return occupiedCells;
    }

    static int[] findCenteredFreeCell(Set<String> occupiedCells, int gridWidth, int gridHeight,
                                       int sizeCols, int sizeRows) {
        int centerCol = (gridWidth - sizeCols) / 2;
        int centerRow = (gridHeight - sizeRows) / 2;
        List<int[]> freeCells = new ArrayList<>();
        for (int row = 0; row <= gridHeight - sizeRows; row++) {
            for (int col = 0; col <= gridWidth - sizeCols; col++) {
                if (isCellFree(occupiedCells, col, row, sizeCols, sizeRows, gridWidth, gridHeight)) {
                    freeCells.add(new int[]{col, row});
                }
            }
        }
        if (freeCells.isEmpty()) return null;
        freeCells.sort(java.util.Comparator.comparingInt(c -> {
            int dc = c[0] + sizeCols / 2 - centerCol;
            int dr = c[1] + sizeRows / 2 - centerRow;
            return dc * dc + dr * dr;
        }));
        return freeCells.getFirst();
    }

    private static int[] findFreeCell(Set<String> occupiedCells, int gridWidth, int gridHeight,
                                       int sizeCols, int sizeRows) {
        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                if (isCellFree(occupiedCells, col, row, sizeCols, sizeRows, gridWidth, gridHeight)) {
                    return new int[]{col, row};
                }
            }
        }
        return null;
    }

    private static int[] preferredOrFreeCell(Combatant combatant, GameMap map,
                                              Set<String> occupiedCells) {
        int cellSizePx = map.getCellSizePx();
        int gridWidth = map.getGridWidth();
        int gridHeight = map.getGridHeight();
        if (combatant.getStartX() != null && combatant.getStartY() != null) {
            int col = clamp(combatant.getStartX() / cellSizePx, Math.max(0, gridWidth - 1));
            int row = clamp(combatant.getStartY() / cellSizePx, Math.max(0, gridHeight - 1));
            if (isCellFree(occupiedCells, col, row, 1, 1, gridWidth, gridHeight)) {
                return new int[]{col, row};
            }
        }
        int[] regionCell = preferredRegionCell(combatant, map, occupiedCells);
        if (regionCell != null) return regionCell;
        return findCenteredFreeCell(occupiedCells, gridWidth, gridHeight, 1, 1);
    }

    private static int[] preferredRegionCell(Combatant combatant, GameMap map,
                                              Set<String> occupiedCells) {
        if (combatant.getPlacementRegionKey() == null || map.getDocument() == null) return null;
        try {
            MapDocumentDto document = JSON.readValue(map.getDocument(), MapDocumentDto.class);
            return document.primitives().stream()
                    .filter(p -> "REGION".equals(p.type()))
                    .filter(p -> combatant.getPlacementRegionKey().equals(p.key()))
                    .map(p -> {
                        int minCol = Math.max(0, Math.min(p.startCol(), p.endCol()));
                        int maxCol = Math.min(map.getGridWidth() - 1, Math.max(p.startCol(), p.endCol()));
                        int minRow = Math.max(0, Math.min(p.startRow(), p.endRow()));
                        int maxRow = Math.min(map.getGridHeight() - 1, Math.max(p.startRow(), p.endRow()));
                        int centerCol = (minCol + maxCol) / 2;
                        int centerRow = (minRow + maxRow) / 2;
                        if (isCellFree(occupiedCells, centerCol, centerRow, 1, 1,
                                map.getGridWidth(), map.getGridHeight())) {
                            return new int[]{centerCol, centerRow};
                        }
                        for (int row = minRow; row <= maxRow; row++) {
                            for (int col = minCol; col <= maxCol; col++) {
                                if (isCellFree(occupiedCells, col, row, 1, 1,
                                        map.getGridWidth(), map.getGridHeight())) {
                                    return new int[]{col, row};
                                }
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isCellFree(Set<String> occupiedCells, int col, int row,
                                       int sizeCols, int sizeRows, int gridWidth, int gridHeight) {
        if (col + sizeCols > gridWidth || row + sizeRows > gridHeight) return false;
        for (int dc = 0; dc < sizeCols; dc++) {
            for (int dr = 0; dr < sizeRows; dr++) {
                if (occupiedCells.contains((col + dc) + "," + (row + dr))) {
                    return false;
                }
            }
        }
        return true;
    }
}
