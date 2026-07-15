package dev.hendrikhoemberg.dmhelper.gamemap.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapLayerDto(
        @JsonProperty(required = true) String id,
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) LayerType type,
        Boolean visible,
        Boolean locked,
        List<CellDto> cells,
        List<ShapeDto> shapes,
        ImageDto image
) {
    public enum LayerType { TERRAIN, OBJECTS, ANNOTATIONS, IMAGE }

    public MapLayerDto {
        visible = visible != null ? visible : Boolean.TRUE;
        locked = locked != null ? locked : Boolean.FALSE;
        cells = cells != null ? cells : List.of();
        shapes = shapes != null ? shapes : List.of();
    }

    public static MapLayerDto createTerrainLayer() {
        return new MapLayerDto("terrain", "Terrain", LayerType.TERRAIN, true, false, List.of(), List.of(), null);
    }

    public static MapLayerDto createObjectsLayer() {
        return new MapLayerDto("objects", "Objects", LayerType.OBJECTS, true, false, List.of(), List.of(), null);
    }

    public static MapLayerDto createAnnotationsLayer() {
        return new MapLayerDto("annotations", "Annotations (DM only)", LayerType.ANNOTATIONS, true, false, List.of(), List.of(), null);
    }

    /** A single painted cell on a terrain layer. Cells not present in the array are "floor" / default. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CellDto(int col, int row, String terrain) {}

    /** A shape on a layer. Coordinates are in grid-cell units, origin top-left, col (x) before row (y).
     *  rect: [x, y, width, height] · circle: [cx, cy, radius] · line: [x1, y1, x2, y2] ·
     *  polygon: flat [x1, y1, x2, y2, x3, y3, …]. strokeWidth is in screen pixels. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShapeDto(
            @JsonProperty(required = true) String type,   // rect | circle | line | polygon
            List<Double> points,
            String fill,
            String stroke,
            double strokeWidth,
            String label
    ) {
        public ShapeDto {
            points = points != null ? points : List.of();
        }
    }

    /** Background reference image for a LayerType.IMAGE layer. The image is embedded as a
     *  base64 data URL — this app has no separate file-storage subsystem, so it lives in the
     *  document CLOB alongside everything else. x/y/width/height are in grid-cell units, the
     *  same coordinate space shapes use; no rotation, matching the shape schema's scope. */
    public record ImageDto(
            @JsonProperty(required = true) String dataUrl,
            double x, double y, double width, double height
    ) {}
}
