package dev.hendrikhoemberg.dmhelper.gamemap.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class MapGridResizeService {

    public MapDocumentDto resize(MapDocumentDto document, int width, int height, MapSettingsCommand.ResizeMode mode) {
        return resize(document, width, height, mode, List.of());
    }

    public MapDocumentDto resize(MapDocumentDto document, int width, int height,
                                 MapSettingsCommand.ResizeMode mode,
                                 List<MapSettingsCommand.ShapeRemoval> shapeRemovals) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive: " + width + "x" + height);
        }
        if (document == null || document.grid() == null || mode == null) {
            throw new IllegalArgumentException("Document, grid, and resize mode are required");
        }

        MapDocumentDto.GridDto oldGrid = document.grid();
        MapDocumentDto.GridDto newGrid = new MapDocumentDto.GridDto(
                width, height, oldGrid.cellSizePx(),
                oldGrid.gridType(), oldGrid.movementMode(), oldGrid.showGrid());

        Set<ShapeKey> removals = validateShapeRemovals(shapeRemovals);
        List<MapLayerDto> newLayers = new ArrayList<>();

        for (int li = 0; li < document.layers().size(); li++) {
            MapLayerDto layer = document.layers().get(li);
            List<MapLayerDto.CellDto> newCells = new ArrayList<>();
            List<MapLayerDto.ShapeDto> newShapes = new ArrayList<>();

            if (mode == MapSettingsCommand.ResizeMode.CROP) {
                for (MapLayerDto.CellDto cell : layer.cells()) {
                    if (cell.col() >= 0 && cell.col() < width && cell.row() >= 0 && cell.row() < height) {
                        newCells.add(cell);
                    }
                }
                for (int si = 0; si < layer.shapes().size(); si++) {
                    if (removals.remove(new ShapeKey(layer.id(), si))) {
                        continue;
                    }
                    MapLayerDto.ShapeDto shape = layer.shapes().get(si);
                    newShapes.add(clipShape(shape, width, height, li, si));
                }
            } else {
                for (MapLayerDto.CellDto cell : layer.cells()) {
                    if (cell.col() < 0 || cell.col() >= width || cell.row() < 0 || cell.row() >= height) {
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
        if (!removals.isEmpty()) {
            throw new IllegalArgumentException("Shape removals do not match document shapes: " + removals);
        }
        List<MapDocumentDto.PrimitiveDto> newPrimitives =
                resizePrimitives(document.primitives(), width, height, mode);

        return new MapDocumentDto(
                document.schemaVersion(), newGrid, newLayers,
                newPrimitives, document.customTerrain());
    }

    private static List<MapDocumentDto.PrimitiveDto> resizePrimitives(
            List<MapDocumentDto.PrimitiveDto> primitives, int width, int height,
            MapSettingsCommand.ResizeMode mode) {
        List<MapDocumentDto.PrimitiveDto> result = new ArrayList<>();
        for (int index = 0; index < primitives.size(); index++) {
            var primitive = primitives.get(index);
            if (primitive == null || primitive.type() == null
                    || !Set.of("ROOM", "CORRIDOR", "DOOR", "REGION").contains(primitive.type())) {
                throw new IllegalArgumentException("Unsupported map primitive at index " + index);
            }
            int minCol = Math.min(primitive.startCol(), primitive.endCol());
            int maxCol = Math.max(primitive.startCol(), primitive.endCol());
            int minRow = Math.min(primitive.startRow(), primitive.endRow());
            int maxRow = Math.max(primitive.startRow(), primitive.endRow());
            boolean outside = minCol < 0 || minRow < 0 || maxCol >= width || maxRow >= height;
            if (mode == MapSettingsCommand.ResizeMode.PRESERVE) {
                if (outside) {
                    throw new IllegalArgumentException(
                            "Map primitive at index " + index + " is outside the requested grid boundary");
                }
                result.add(primitive);
                continue;
            }
            if (maxCol < 0 || maxRow < 0 || minCol >= width || minRow >= height) {
                continue;
            }
            int startCol = Math.max(0, Math.min(width - 1, primitive.startCol()));
            int startRow = Math.max(0, Math.min(height - 1, primitive.startRow()));
            int endCol = Math.max(0, Math.min(width - 1, primitive.endCol()));
            int endRow = Math.max(0, Math.min(height - 1, primitive.endRow()));
            result.add(new MapDocumentDto.PrimitiveDto(
                    primitive.type(), startCol, startRow, endCol, endRow,
                    primitive.terrain(), primitive.key(), primitive.label(), primitive.playerVisible()));
        }
        return result;
    }

    private static Set<ShapeKey> validateShapeRemovals(
            List<MapSettingsCommand.ShapeRemoval> requested) {
        Set<ShapeKey> removals = new HashSet<>();
        for (var removal : requested != null ? requested : List.<MapSettingsCommand.ShapeRemoval>of()) {
            if (removal == null || removal.layerId() == null || removal.layerId().isBlank()
                    || removal.shapeIndex() < 0) {
                throw new IllegalArgumentException(
                        "Shape removals require layerId and a non-negative shapeIndex");
            }
            ShapeKey key = new ShapeKey(removal.layerId(), removal.shapeIndex());
            if (!removals.add(key)) {
                throw new IllegalArgumentException("Duplicate shape removal: " + key);
            }
        }
        return removals;
    }

    private void validateShapeInBounds(MapLayerDto.ShapeDto shape, int width, int height, String layerName) {
        List<Double> pts = shape.points();
        String type = shape.type();
        switch (type) {
            case "rect" -> {
                if (pts.size() >= 4) {
                    double x = pts.get(0), y = pts.get(1), w = pts.get(2), h = pts.get(3);
                    if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > width || y + h > height) {
                        throw new IllegalArgumentException(
                                "Content is outside the requested " + width + "x" + height
                                + " grid boundary in layer '" + layerName + "'");
                    }
                }
            }
            case "circle" -> {
                if (pts.size() >= 3) {
                    double cx = pts.get(0), cy = pts.get(1), radius = pts.get(2);
                    if (radius <= 0 || cx - radius < 0 || cy - radius < 0
                            || cx + radius > width || cy + radius > height) {
                        throw new IllegalArgumentException(
                                "Content is outside the requested " + width + "x" + height
                                + " grid boundary in layer '" + layerName + "'");
                    }
                }
            }
            case "line", "polygon" -> {
                for (int i = 0; i < pts.size(); i += 2) {
                    double px = pts.get(i), py = pts.get(i + 1);
                    if (px < 0 || px > width || py < 0 || py > height) {
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
                double sourceX = pts.get(0), sourceY = pts.get(1);
                double sourceW = pts.get(2), sourceH = pts.get(3);
                double x1 = Math.max(0, sourceX);
                double y1 = Math.max(0, sourceY);
                double x2 = Math.min(width, sourceX + sourceW);
                double y2 = Math.min(height, sourceY + sourceH);
                if (sourceW <= 0 || sourceH <= 0 || x2 - x1 <= 0 || y2 - y1 <= 0) {
                    throw new IllegalArgumentException(
                            "Shape collapsed to zero area at layer " + layerIndex + " index " + shapeIndex);
                }
                newPts = List.of(x1, y1, x2 - x1, y2 - y1);
            }
            case "line" -> {
                if (pts.size() < 4) {
                    throw new IllegalArgumentException(
                            "Invalid line shape at layer " + layerIndex + " index " + shapeIndex);
                }
                newPts = clipLine(pts.get(0), pts.get(1), pts.get(2), pts.get(3), width, height);
                if (newPts == null || (newPts.get(0).equals(newPts.get(2))
                        && newPts.get(1).equals(newPts.get(3)))) {
                    throw new IllegalArgumentException(
                            "Shape collapsed to zero length at layer " + layerIndex + " index " + shapeIndex);
                }
            }
            case "circle" -> {
                if (pts.size() < 3) {
                    throw new IllegalArgumentException(
                            "Invalid circle shape at layer " + layerIndex + " index " + shapeIndex);
                }
                double cx = pts.get(0), cy = pts.get(1), r = pts.get(2);
                if (r <= 0 || cx - r < 0 || cy - r < 0 || cx + r > width || cy + r > height) {
                    throw new IllegalArgumentException(
                            "Circle cannot be cropped without explicit removal at layer "
                                    + layerIndex + " index " + shapeIndex);
                }
                newPts = List.of(cx, cy, r);
            }
            case "polygon" -> {
                if (pts.size() < 6 || pts.size() % 2 != 0) {
                    throw new IllegalArgumentException(
                            "Invalid polygon shape at layer " + layerIndex + " index " + shapeIndex);
                }
                newPts = clipPolygon(pts, width, height);
                if (newPts.size() < 6 || polygonArea(newPts) <= 1.0e-9) {
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

    private static List<Double> clipLine(double x1, double y1, double x2, double y2,
                                         double width, double height) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1, width - x1, y1, height - y1};
        double enter = 0;
        double leave = 1;
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1.0e-12) {
                if (q[i] < 0) return null;
                continue;
            }
            double ratio = q[i] / p[i];
            if (p[i] < 0) enter = Math.max(enter, ratio);
            else leave = Math.min(leave, ratio);
            if (enter > leave) return null;
        }
        return List.of(x1 + enter * dx, y1 + enter * dy,
                x1 + leave * dx, y1 + leave * dy);
    }

    private static List<Double> clipPolygon(List<Double> points, double width, double height) {
        List<Point> polygon = new ArrayList<>();
        for (int i = 0; i < points.size(); i += 2) {
            polygon.add(new Point(points.get(i), points.get(i + 1)));
        }
        polygon = clipEdge(polygon, point -> point.x >= 0, (a, b) -> intersectVertical(a, b, 0));
        polygon = clipEdge(polygon, point -> point.x <= width, (a, b) -> intersectVertical(a, b, width));
        polygon = clipEdge(polygon, point -> point.y >= 0, (a, b) -> intersectHorizontal(a, b, 0));
        polygon = clipEdge(polygon, point -> point.y <= height, (a, b) -> intersectHorizontal(a, b, height));
        List<Double> result = new ArrayList<>();
        for (Point point : polygon) {
            if (!result.isEmpty() && result.get(result.size() - 2).equals(point.x)
                    && result.get(result.size() - 1).equals(point.y)) {
                continue;
            }
            result.add(point.x);
            result.add(point.y);
        }
        return result;
    }

    private interface Intersection {
        Point apply(Point from, Point to);
    }

    private static List<Point> clipEdge(List<Point> input, Predicate<Point> inside,
                                        Intersection intersection) {
        if (input.isEmpty()) return input;
        List<Point> output = new ArrayList<>();
        Point previous = input.getLast();
        boolean previousInside = inside.test(previous);
        for (Point current : input) {
            boolean currentInside = inside.test(current);
            if (currentInside) {
                if (!previousInside) output.add(intersection.apply(previous, current));
                output.add(current);
            } else if (previousInside) {
                output.add(intersection.apply(previous, current));
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static Point intersectVertical(Point a, Point b, double x) {
        double t = (x - a.x) / (b.x - a.x);
        return new Point(x, a.y + t * (b.y - a.y));
    }

    private static Point intersectHorizontal(Point a, Point b, double y) {
        double t = (y - a.y) / (b.y - a.y);
        return new Point(a.x + t * (b.x - a.x), y);
    }

    private static double polygonArea(List<Double> points) {
        double twiceArea = 0;
        int vertices = points.size() / 2;
        for (int i = 0; i < vertices; i++) {
            int next = (i + 1) % vertices;
            twiceArea += points.get(i * 2) * points.get(next * 2 + 1)
                    - points.get(next * 2) * points.get(i * 2 + 1);
        }
        return Math.abs(twiceArea) / 2;
    }

    private record Point(double x, double y) {}
    private record ShapeKey(String layerId, int shapeIndex) {}
}
