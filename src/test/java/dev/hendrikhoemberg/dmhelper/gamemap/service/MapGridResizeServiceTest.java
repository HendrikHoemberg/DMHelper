package dev.hendrikhoemberg.dmhelper.gamemap.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class MapGridResizeServiceTest {

    private final MapGridResizeService service = new MapGridResizeService();

    @Test
    void preserveResizeChangesOnlyTheEmbeddedGrid() {
        MapDocumentDto document = documentWithCellAndShape(20, 15);
        MapDocumentDto resized = service.resize(document, 40, 30, MapSettingsCommand.ResizeMode.PRESERVE);

        assertThat(resized.grid().width()).isEqualTo(40);
        assertThat(resized.grid().height()).isEqualTo(30);
        assertThat(resized.layers().get(0).cells()).hasSize(1);
        assertThat(resized.layers().get(1).shapes()).hasSize(1);
    }

    @Test
    void cropResizeRemovesOutOfBoundsCellsAndClipsShapes() {
        MapDocumentDto document = documentWithCellAndShape(20, 15);
        MapDocumentDto cropped = service.resize(document, 10, 10, MapSettingsCommand.ResizeMode.CROP);

        assertThat(cropped.layers().get(0).cells())
                .extracting(MapLayerDto.CellDto::col, MapLayerDto.CellDto::row)
                .containsExactly(tuple(2, 3));
        assertThat(cropped.layers().get(1).shapes().get(0).points())
                .containsExactly(8.0, 8.0, 2.0, 2.0);
    }

    @Test
    void cropResizeRejectsUnknownOrCollapsedGeometryInsteadOfSilentlyDroppingIt() {
        MapDocumentDto document = documentWithShape(
                "triangle", List.of(8.0, 8.0, 12.0, 8.0, 10.0, 12.0));

        assertThatThrownBy(() -> service.resize(document, 10, 10, MapSettingsCommand.ResizeMode.CROP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("triangle");
    }

    private static MapDocumentDto documentWithCellAndShape(int gridWidth, int gridHeight) {
        return new MapDocumentDto(
                MapDocumentDto.CURRENT_SCHEMA_VERSION,
                new MapDocumentDto.GridDto(gridWidth, gridHeight, 48, "square", "GRID", true),
                List.of(
                        new MapLayerDto("terrain", "Terrain", MapLayerDto.LayerType.TERRAIN,
                                true, false,
                                List.of(new MapLayerDto.CellDto(2, 3, "wall")),
                                List.of(), null, null),
                        new MapLayerDto("objects", "Objects", MapLayerDto.LayerType.OBJECTS,
                                true, false,
                                List.of(),
                                List.of(new MapLayerDto.ShapeDto("rect",
                                        List.of(8.0, 8.0, 5.0, 5.0), null, null, 0, null)),
                                null, null)
                ),
                List.of(), List.of()
        );
    }

    private static MapDocumentDto documentWithShape(String shapeType, List<Double> points) {
        return new MapDocumentDto(
                MapDocumentDto.CURRENT_SCHEMA_VERSION,
                new MapDocumentDto.GridDto(20, 15, 48, "square", "GRID", true),
                List.of(
                        new MapLayerDto("objects", "Objects", MapLayerDto.LayerType.OBJECTS,
                                true, false,
                                List.of(),
                                List.of(new MapLayerDto.ShapeDto(shapeType, points, null, null, 0, null)),
                                null, null)
                ),
                List.of(), List.of()
        );
    }
}
