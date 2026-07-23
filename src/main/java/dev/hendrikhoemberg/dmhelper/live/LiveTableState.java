package dev.hendrikhoemberg.dmhelper.live;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveTableState(
        @JsonProperty String type,
        String mode,
        MapSnapshot map,
        HandoutRef handout,
        List<CombatantSnapshot> initiative,
        Integer activeTurnIndex
) {
    public static LiveTableState curtain() {
        return new LiveTableState("TABLE_STATE", "CURTAIN", null, null, null, null);
    }

    public static LiveTableState full(String mode, MapSnapshot map, HandoutRef handout,
                                      List<CombatantSnapshot> initiative, Integer activeTurnIndex) {
        return new LiveTableState("TABLE_STATE", mode, map, handout, initiative, activeTurnIndex);
    }

    public static LiveTableState tokenMoved(MapSnapshot map) {
        return new LiveTableState("TOKEN_MOVED", "MAP", map, null, null, null);
    }

    public static LiveTableState turnChanged(List<CombatantSnapshot> initiative, int activeTurnIndex) {
        return new LiveTableState("TURN_CHANGED", "MAP", null, null, initiative, activeTurnIndex);
    }

    public record MapSnapshot(
            String mapId,
            String mapName,
            int gridWidth,
            int gridHeight,
            int cellSizePx,
            String movementMode,
            boolean showGrid,
            Object document,
            List<TokenSnapshot> tokens,
            List<AoeTemplateSnapshot> aoes
    ) {}

    public record TokenSnapshot(
            String id,
            String name,
            String kind,
            String color,
            int positionX,
            int positionY,
            int sizeCols,
            int sizeRows,
            boolean dead,
            Boolean bloodied
    ) {}

    public record HandoutRef(
            String id,
            String title,
            String contentType,
            String fileUrl
    ) {}

    public record CombatantSnapshot(
            String id,
            String name,
            Integer initiative,
            boolean defeated,
            boolean active,
            List<String> conditions
    ) {}

    public record AoeTemplateSnapshot(
            String type,
            int cells,
            double x,
            double y
    ) {}
}
