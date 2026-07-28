package dev.hendrikhoemberg.dmhelper.gamemap.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterTokenPlacement;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterTokenPlacementRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class RuntimeTokenProjectionService {

    private final TokenRepository tokenRepo;
    private final EncounterTokenPlacementRepository placementRepo;
    private final EncounterRepository encounterRepo;
    private final GameMapRepository mapRepo;
    private final ObjectMapper objectMapper;

    public RuntimeTokenProjectionService(TokenRepository tokenRepo,
                                          EncounterTokenPlacementRepository placementRepo,
                                          EncounterRepository encounterRepo,
                                          GameMapRepository mapRepo) {
        this.tokenRepo = tokenRepo;
        this.placementRepo = placementRepo;
        this.encounterRepo = encounterRepo;
        this.mapRepo = mapRepo;
        this.objectMapper = new ObjectMapper();
    }

    public List<RuntimeTokenDto> project(UUID mapId, UUID encounterId) {
        List<RuntimeTokenDto> markers = tokenRepo.findByMapIdOrderByNameAsc(mapId).stream()
                .map(this::toMarkerDto)
                .toList();

        UUID resolvedEncounterId = resolveEncounter(mapId, encounterId);

        List<RuntimeTokenDto> combatants = resolvedEncounterId != null
                ? placementRepo.findByMapIdAndEncounterIdOrderByCombatant_SortOrderAsc(mapId, resolvedEncounterId)
                    .stream()
                    .map(this::toCombatantDto)
                    .toList()
                : List.of();

        return Stream.concat(markers.stream(), combatants.stream()).toList();
    }

    private UUID resolveEncounter(UUID mapId, UUID encounterId) {
        if (encounterId != null) {
            Encounter enc = encounterRepo.findById(encounterId).orElse(null);
            if (enc != null && enc.getMap() != null && enc.getMap().getId().equals(mapId)) {
                return encounterId;
            }
            return null;
        }
        GameMap map = mapRepo.findById(mapId).orElse(null);
        if (map == null) return null;
        return encounterRepo.findByCampaignIdAndStatus(map.getCampaign().getId(), Encounter.Status.ACTIVE)
                .filter(e -> e.getMap() != null && e.getMap().getId().equals(mapId))
                .map(Encounter::getId)
                .orElse(null);
    }

    private RuntimeTokenDto toMarkerDto(Token token) {
        return new RuntimeTokenDto(
            token.getId(), RuntimeTokenSource.MARKER, null,
            token.getName(), token.getKind(), token.getPositionX(), token.getPositionY(),
            token.getSizeCols(), token.getSizeRows(), token.getColor(), token.getIcon(),
            token.isHidden(), null, null, false, false, List.of());
    }

    private RuntimeTokenDto toCombatantDto(EncounterTokenPlacement placement) {
        Combatant c = placement.getCombatant();
        int maxHp = c.getMaxHp();
        int currentHp = c.getCurrentHp();
        boolean bloodied = maxHp > 0 && currentHp <= maxHp / 2;
        List<String> conditions = parseConditions(c.getConditionsJson());
        return new RuntimeTokenDto(
            placement.getId(), RuntimeTokenSource.COMBATANT, c.getId(),
            c.getName(), c.getKind(), placement.getPositionX(), placement.getPositionY(),
            placement.getSizeCols(), placement.getSizeRows(), placement.getColor(), placement.getIcon(),
            c.isHidden(), currentHp, maxHp,
            bloodied, c.isDefeated(), conditions);
    }

    private List<String> parseConditions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public enum RuntimeTokenSource { COMBATANT, MARKER }

    public record RuntimeTokenDto(
            UUID id,
            RuntimeTokenSource source,
            UUID combatantId,
            String name,
            String kind,
            int positionX,
            int positionY,
            int sizeCols,
            int sizeRows,
            String color,
            String icon,
            boolean hidden,
            Integer currentHp,
            Integer maxHp,
            boolean bloodied,
            boolean defeated,
            List<String> conditions) {}
}
