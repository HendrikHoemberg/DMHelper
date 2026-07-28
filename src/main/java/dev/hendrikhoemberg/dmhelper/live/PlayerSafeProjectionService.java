package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.RuntimeTokenProjectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PlayerSafeProjectionService {

    private final TokenRepository tokenRepository;
    private final RuntimeTokenProjectionService runtimeTokenService;
    private final ObjectMapper objectMapper;

    public PlayerSafeProjectionService(TokenRepository tokenRepository,
                                        RuntimeTokenProjectionService runtimeTokenService) {
        this.tokenRepository = tokenRepository;
        this.runtimeTokenService = runtimeTokenService;
        this.objectMapper = new ObjectMapper();
    }

    public List<LiveTableState.TokenSnapshot> projectTokens(GameMap gameMap, Encounter activeEncounter) {
        UUID encounterId = activeEncounter != null ? activeEncounter.getId() : null;
        var runtime = runtimeTokenService.project(gameMap.getId(), encounterId);

        return runtime.stream()
                .filter(t -> !t.hidden())
                .map(t -> new LiveTableState.TokenSnapshot(
                        t.id().toString(), t.name(), t.kind(), t.color(),
                        t.positionX(), t.positionY(), t.sizeCols(), t.sizeRows(),
                        t.defeated(), t.bloodied(),
                        t.source().name(), t.combatantId()))
                .toList();
    }

    public MapDocumentDto projectMapDocument(GameMap gameMap) {
        if (gameMap.getDocument() == null || gameMap.getDocument().isBlank()) {
            return null;
        }
        try {
            MapDocumentDto doc = objectMapper.readValue(gameMap.getDocument(), MapDocumentDto.class);

            var safeLayers = doc.layers().stream()
                    .filter(l -> l.type() != MapLayerDto.LayerType.ANNOTATIONS)
                    .filter(l -> l.playerVisible() == null || l.playerVisible())
                    .map(l -> l.visible() != null && l.visible() ? l : new MapLayerDto(
                            l.id(), l.name(), l.type(), false,
                            l.locked(), List.of(), List.of(), null, l.playerVisible()))
                    .toList();

            var safePrimitives = doc.primitives().stream()
                    .filter(p -> p.playerVisible() == null || p.playerVisible())
                    .toList();

            return new MapDocumentDto(
                    doc.schemaVersion(),
                    doc.grid(),
                    safeLayers,
                    safePrimitives,
                    doc.customTerrain()
            );
        } catch (Exception e) {
            return MapDocumentDto.createDefault(gameMap.getGridWidth(), gameMap.getGridHeight(), gameMap.getCellSizePx());
        }
    }

    public List<LiveTableState.CombatantSnapshot> projectCombatants(List<Combatant> combatants,
                                                                     int activeTurnIndex) {
        if (combatants == null) return List.of();
        var visible = combatants.stream()
                .filter(c -> !c.isHidden())
                .toList();
        return visible.stream()
                .map(c -> {
                    int unfilteredIdx = combatants.indexOf(c);
                    return new LiveTableState.CombatantSnapshot(
                            c.getId().toString(),
                            c.getName(),
                            c.getInitiative(),
                            c.isDefeated(),
                            unfilteredIdx == activeTurnIndex,
                            parseConditions(c.getConditionsJson())
                    );
                })
                .toList();
    }

    private List<String> parseConditions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<String> names = new ArrayList<>();
            for (Map<String, Object> cond : raw) {
                Object sourceKey = cond.get("sourceKey");
                if (sourceKey != null) names.add(sourceKey.toString());
            }
            return names;
        } catch (Exception e) {
            return List.of();
        }
    }
}
