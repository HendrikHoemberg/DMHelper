package dev.hendrikhoemberg.dmhelper.gamemap.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapDocumentDto(
        @JsonProperty(required = true) int schemaVersion,
        @JsonProperty(required = true) GridDto grid,
        List<MapLayerDto> layers,
        List<PrimitiveDto> primitives,
        List<TerrainDefDto> customTerrain
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public MapDocumentDto {
        layers = layers != null ? layers : List.of();
        primitives = primitives != null ? primitives : List.of();
        customTerrain = customTerrain != null ? customTerrain : List.of();
    }

    public static MapDocumentDto createDefault(int gridWidth, int gridHeight, int cellSizePx) {
        return new MapDocumentDto(
                CURRENT_SCHEMA_VERSION,
                new GridDto(gridWidth, gridHeight, cellSizePx, "square", "GRID", true),
                List.of(
                        MapLayerDto.createTerrainLayer(),
                        MapLayerDto.createObjectsLayer(),
                        MapLayerDto.createAnnotationsLayer()
                ),
                List.of(),
                List.of()
        );
    }

    /** gridType is reserved for post-v1 hex support (SPEC §9); always "square" in v1. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GridDto(int width, int height, int cellSizePx, String gridType, String movementMode, boolean showGrid) {
        public GridDto(int width, int height, int cellSizePx, String gridType) {
            this(width, height, cellSizePx, gridType, "GRID", true);
        }

        @JsonCreator
        static GridDto create(
                @JsonProperty("width") int width,
                @JsonProperty("height") int height,
                @JsonProperty("cellSizePx") int cellSizePx,
                @JsonProperty("gridType") String gridType,
                @JsonProperty("movementMode") String movementMode,
                @JsonProperty("showGrid") Boolean showGrid) {
            return new GridDto(width, height, cellSizePx, gridType,
                    movementMode != null ? movementMode : "GRID",
                    showGrid != null ? showGrid : true);
        }
    }

    /** Semantic map primitive (SPEC §4.3), expanded to cells on render. The editor emits
     *  painted cells; primitives exist chiefly so generated maps can say
     *  "room from (2,2) to (10,8)" instead of hundreds of coordinates. Coordinates are
     *  grid cells, origin top-left, col before row. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrimitiveDto(
            @JsonProperty(required = true) String type,   // ROOM | CORRIDOR | DOOR | REGION
            int startCol, int startRow,
            int endCol, int endRow,
            String terrain   // REGION fill terrain key; ignored by other types
    ) {}

    /** Custom terrain palette entry (SPEC §4.3: palette extensible with
     *  name + color + walkable flag). Stored per map in its document. */
    public record TerrainDefDto(
            @JsonProperty(required = true) String key,
            @JsonProperty(required = true) String name,
            @JsonProperty(required = true) String fill,   // CSS hex color
            boolean walkable
    ) {}
}
