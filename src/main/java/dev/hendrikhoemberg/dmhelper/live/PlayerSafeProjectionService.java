package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PlayerSafeProjectionService {

    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    public PlayerSafeProjectionService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = new ObjectMapper();
    }

    public List<LiveTableState.TokenSnapshot> projectTokens(GameMap gameMap) {
        return tokenRepository.findByMapIdOrderByNameAsc(gameMap.getId()).stream()
                .filter(t -> !t.isHidden())
                .map(this::toSnapshot)
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
                    .map(l -> l.visible() != null && l.visible() ? l : new MapLayerDto(
                            l.id(), l.name(), l.type(), false,
                            l.locked(), l.cells(), l.shapes(), l.image()))
                    .toList();

            return new MapDocumentDto(
                    doc.schemaVersion(),
                    doc.grid(),
                    safeLayers,
                    doc.primitives(),
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

    private LiveTableState.TokenSnapshot toSnapshot(Token token) {
        return new LiveTableState.TokenSnapshot(
                token.getId().toString(),
                token.getName(),
                token.getKind(),
                token.getColor(),
                token.getPositionX(),
                token.getPositionY(),
                token.getSizeCols(),
                token.getSizeRows(),
                token.isDead(),
                computeBloodied(token),
                null,
                null
        );
    }

    private Boolean computeBloodied(Token token) {
        if (token.getCurrentHp() == null || token.getMaxHp() == null || token.getMaxHp() <= 0) {
            return null;
        }
        return token.getCurrentHp() <= token.getMaxHp() / 2;
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
