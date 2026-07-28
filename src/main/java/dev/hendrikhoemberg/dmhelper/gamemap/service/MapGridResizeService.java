package dev.hendrikhoemberg.dmhelper.gamemap.service;

import java.util.ArrayList;
import java.util.List;

public class MapGridResizeService {

    public MapDocumentDto resize(MapDocumentDto document, int width, int height, MapSettingsCommand.ResizeMode mode) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive: " + width + "x" + height);
        }

        MapDocumentDto.GridDto oldGrid = document.grid();
        MapDocumentDto.GridDto newGrid = new MapDocumentDto.GridDto(
                width, height, oldGrid.cellSizePx(),
                oldGrid.gridType(), oldGrid.movementMode(), oldGrid.showGrid());

        List<MapLayerDto> newLayers = new ArrayList<>();

        for (int li = 0; li < document.layers().size(); li++) {
            MapLayerDto layer = document.layers().get(li);
            List<MapLayerDto.CellDto> newCells = new ArrayList<>();
            List<MapLayerDto.ShapeDto> newShapes = new ArrayList<>();

            if (mode == MapSettingsCommand.ResizeMode.CROP) {
                for (MapLayerDto.CellDto cell : layer.cells()) {
                    if (cell.col() < width && cell.row() < height) {
                        newCells.add(cell);
                    }
                }
                for (int si = 0; si < layer.shapes().size(); si++) {
                    MapLayerDto.ShapeDto shape = layer.shapes().get(si);
                    newShapes.add(clipShape(shape, width, height, li, si));
                }
            } else {
                for (MapLayerDto.CellDto cell : layer.cells()) {
                    if (cell.col() >= width || cell.row() >= height) {
                        throw new IllegalArgumentException(
                                "Content at (" + cell.col() + "," + cell.row() + ") is outside the requested "
                                + width + "x" + height + " grid boundary in layer '" + layer.name() + "'");
                    }
                    newCells.add(cell);
                }
                for (MapLayerDto.ShapeDto shape : layer.shapes()) {
                    validateShapeInBounds(shape, width, height, layer.name());
                    newShapes.add(shape);
                }
            }

            newLayers.add(new MapLayerDto(
                    layer.id(), layer.name(), layer.type(),
                    layer.visible(), layer.locked(),
                    newCells, newShapes, layer.image(), layer.playerVisible()));
        }

        return new MapDocumentDto(
                document.schemaVersion(), newGrid, newLayers,
                document.primitives(), document.customTerrain());
    }

    private void validateShapeInBounds(MapLayerDto.ShapeDto shape, int width, int height, String layerName) {
        List<Double> pts = shape.points();
        String type = shape.type();
        switch (type) {
            case "rect" -> {
                if (pts.size() >= 4) {
                    double x = pts.get(0), y = pts.get(1), w = pts.get(2), h = pts.get(3);
                    if (x + w > width || y + h > height) {
                        throw new IllegalArgumentException(
                                "Content is outside the requested " + width + "x" + height
                                + " grid boundary in layer '" + layerName + "'");
                    }
                }
            }
            case "circle" -> {
                if (pts.size() >= 3) {
                    double cx = pts.get(0), cy = pts.get(1);
                    if (cx < 0 || cx >= width || cy < 0 || cy >= height) {
                        throw new IllegalArgumentException(
                                "Content is outside the requested " + width + "x" + height
                                + " grid boundary in layer '" + layerName + "'");
                    }
                }
            }
            case "line", "polygon" -> {
                for (int i = 0; i < pts.size(); i += 2) {
                    double px = pts.get(i), py = pts.get(i + 1);
                    if (px < 0 || px >= width || py < 0 || py >= height) {
                        throw new IllegalArgumentException(
                                "Content is outside the requested " + width + "x" + height
                                + " grid boundary in layer '" + layerName + "'");
                    }
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported shape type for preserve: " + type + " in layer '" + layerName + "'");
        }
    }

    private MapLayerDto.ShapeDto clipShape(MapLayerDto.ShapeDto shape, int width, int height, int layerIndex, int shapeIndex) {
        List<Double> pts = shape.points();
        String type = shape.type();

        List<Double> newPts;
        switch (type) {
            case "rect" -> {
                if (pts.size() < 4) {
                    throw new IllegalArgumentException(
                            "Invalid rect shape at layer " + layerIndex + " index " + shapeIndex);
                }
                double x = clamp(pts.get(0), 0, width - 1);
                double y = clamp(pts.get(1), 0, height - 1);
                double w = Math.min(pts.get(2), width - x);
                double h = Math.min(pts.get(3), height - y);
                if (w <= 0 || h <= 0) {
                    throw new IllegalArgumentException(
                            "Shape collapsed to zero area at layer " + layerIndex + " index " + shapeIndex);
                }
                newPts = List.of(x, y, w, h);
            }
            case "line" -> {
                if (pts.size() < 4) {
                    throw new IllegalArgumentException(
                            "Invalid line shape at layer " + layerIndex + " index " + shapeIndex);
                }
                double x1 = clamp(pts.get(0), 0, width - 1);
                double y1 = clamp(pts.get(1), 0, height - 1);
                double x2 = clamp(pts.get(2), 0, width - 1);
                double y2 = clamp(pts.get(3), 0, height - 1);
                if (x1 == x2 && y1 == y2) {
                    throw new IllegalArgumentException(
                            "Shape collapsed to zero length at layer " + layerIndex + " index " + shapeIndex);
                }
                newPts = List.of(x1, y1, x2, y2);
            }
            case "circle" -> {
                if (pts.size() < 3) {
                    throw new IllegalArgumentException(
                            "Invalid circle shape at layer " + layerIndex + " index " + shapeIndex);
                }
                double cx = clamp(pts.get(0), 0, width - 1);
                double cy = clamp(pts.get(1), 0, height - 1);
                double r = pts.get(2);
                if (r <= 0) {
                    throw new IllegalArgumentException(
                            "Shape collapsed at layer " + layerIndex + " index " + shapeIndex);
                }
                newPts = List.of(cx, cy, r);
            }
            case "polygon" -> {
                newPts = new ArrayList<>();
                for (int i = 0; i < pts.size(); i += 2) {
                    newPts.add(clamp(pts.get(i), 0, width - 1));
                    newPts.add(clamp(pts.get(i + 1), 0, height - 1));
                }
                if (newPts.size() < 6) {
                    throw new IllegalArgumentException(
                            "Shape collapsed at layer " + layerIndex + " index " + shapeIndex);
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported shape type: " + type + " at layer " + layerIndex + " index " + shapeIndex);
        }

        return new MapLayerDto.ShapeDto(
                shape.type(), newPts, shape.fill(),
                shape.stroke(), shape.strokeWidth(), shape.label());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
