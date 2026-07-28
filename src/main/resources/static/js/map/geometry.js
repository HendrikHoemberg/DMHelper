/**
 * @param {number} gridWidth
 * @param {number} gridHeight
 * @param {number} imageWidth
 * @param {number} imageHeight
 * @returns {{x: number, y: number, width: number, height: number}}
 */
export function fitInsideGeometry(gridWidth, gridHeight, imageWidth, imageHeight) {
    const scale = Math.min(gridWidth / imageWidth, gridHeight / imageHeight);
    const width = imageWidth * scale;
    const height = imageHeight * scale;
    return {
        x: (gridWidth - width) / 2,
        y: (gridHeight - height) / 2,
        width,
        height,
    };
}

/**
 * @param {number} gridWidth
 * @param {number} gridHeight
 * @param {number} imageWidth
 * @param {number} imageHeight
 * @returns {{x: number, y: number, width: number, height: number}}
 */
export function fillCoverGeometry(gridWidth, gridHeight, imageWidth, imageHeight) {
    const scale = Math.max(gridWidth / imageWidth, gridHeight / imageHeight);
    const width = imageWidth * scale;
    const height = imageHeight * scale;
    return {
        x: (gridWidth - width) / 2,
        y: (gridHeight - height) / 2,
        width,
        height,
    };
}

/**
 * @param {{x: number, y: number, width: number, height: number}} image
 * @param {{x: number, y: number}} pointA
 * @param {{x: number, y: number}} pointB
 * @param {number} cellsBetween
 * @param {number} cellSizePx
 * @returns {{x: number, y: number, width: number, height: number, scale: number}}
 */
export function calibratedImageGeometry(image, pointA, pointB, cellsBetween, cellSizePx) {
    const dx = pointB.x - pointA.x;
    const dy = pointB.y - pointA.y;
    const currentDistancePx = Math.hypot(dx, dy) * cellSizePx;
    const targetDistancePx = cellsBetween * cellSizePx;
    const scale = currentDistancePx > 0 ? targetDistancePx / currentDistancePx : 1;
    const newWidth = image.width * scale;
    const newHeight = image.height * scale;
    const newX = pointA.x - (pointA.x - image.x) * scale;
    const newY = pointA.y - (pointA.y - image.y) * scale;
    return { x: newX, y: newY, width: newWidth, height: newHeight, scale };
}

export function shapeBounds(shape) {
    const pts = shape.points || [];
    switch (shape.type) {
        case 'rect':
            if (pts.length < 4) return null;
            return { minX: pts[0], minY: pts[1], maxX: pts[0] + pts[2], maxY: pts[1] + pts[3] };
        case 'circle':
            if (pts.length < 3) return null;
            return { minX: pts[0] - pts[2], minY: pts[1] - pts[2], maxX: pts[0] + pts[2], maxY: pts[1] + pts[2] };
        case 'line':
        case 'polygon':
        default: {
            if (pts.length < 2) return null;
            let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
            for (let i = 0; i + 1 < pts.length; i += 2) {
                if (pts[i] < minX) minX = pts[i];
                if (pts[i] > maxX) maxX = pts[i];
                if (pts[i + 1] < minY) minY = pts[i + 1];
                if (pts[i + 1] > maxY) maxY = pts[i + 1];
            }
            return { minX, minY, maxX, maxY };
        }
    }
}

function clippedLine(points, width, height) {
    if (points.length < 4) return null;
    const [x1, y1, x2, y2] = points;
    const dx = x2 - x1, dy = y2 - y1;
    const p = [-dx, dx, -dy, dy];
    const q = [x1, width - x1, y1, height - y1];
    let enter = 0, leave = 1;
    for (let i = 0; i < 4; i++) {
        if (Math.abs(p[i]) < 1e-12) {
            if (q[i] < 0) return null;
            continue;
        }
        const ratio = q[i] / p[i];
        if (p[i] < 0) enter = Math.max(enter, ratio);
        else leave = Math.min(leave, ratio);
        if (enter > leave) return null;
    }
    const clipped = [
        x1 + enter * dx, y1 + enter * dy,
        x1 + leave * dx, y1 + leave * dy,
    ];
    return clipped[0] === clipped[2] && clipped[1] === clipped[3] ? null : clipped;
}

function clipPolygonEdge(input, inside, intersection) {
    if (!input.length) return input;
    const output = [];
    let previous = input[input.length - 1];
    let previousInside = inside(previous);
    for (const current of input) {
        const currentInside = inside(current);
        if (currentInside) {
            if (!previousInside) output.push(intersection(previous, current));
            output.push(current);
        } else if (previousInside) {
            output.push(intersection(previous, current));
        }
        previous = current;
        previousInside = currentInside;
    }
    return output;
}

function clippedPolygon(points, width, height) {
    if (points.length < 6 || points.length % 2) return [];
    let polygon = [];
    for (let i = 0; i < points.length; i += 2) polygon.push({ x: points[i], y: points[i + 1] });
    const vertical = x => (a, b) => {
        const t = (x - a.x) / (b.x - a.x);
        return { x, y: a.y + t * (b.y - a.y) };
    };
    const horizontal = y => (a, b) => {
        const t = (y - a.y) / (b.y - a.y);
        return { x: a.x + t * (b.x - a.x), y };
    };
    polygon = clipPolygonEdge(polygon, point => point.x >= 0, vertical(0));
    polygon = clipPolygonEdge(polygon, point => point.x <= width, vertical(width));
    polygon = clipPolygonEdge(polygon, point => point.y >= 0, horizontal(0));
    polygon = clipPolygonEdge(polygon, point => point.y <= height, horizontal(height));
    return polygon;
}

function shapeRequiresRemoval(shape, width, height) {
    const points = shape.points || [];
    if (shape.type === 'circle') return true;
    if (shape.type === 'line') return clippedLine(points, width, height) === null;
    if (shape.type === 'polygon') {
        const polygon = clippedPolygon(points, width, height);
        if (polygon.length < 3) return true;
        let twiceArea = 0;
        for (let i = 0; i < polygon.length; i++) {
            const next = polygon[(i + 1) % polygon.length];
            twiceArea += polygon[i].x * next.y - next.x * polygon[i].y;
        }
        return Math.abs(twiceArea) <= 1e-9;
    }
    const bounds = shapeBounds(shape);
    return !bounds || bounds.maxX <= 0 || bounds.maxY <= 0
        || bounds.minX >= width || bounds.minY >= height;
}

/**
 * @param {{layers: {cells?: {col:number, row:number, terrain:string}[], shapes?: {type:string, points:number[]}[]}[]}} document
 * @param {number} gridWidth
 * @param {number} gridHeight
 * @returns {{outsideCells: {col:number, row:number, terrain:string}[], affectedShapes: {type:string, points:number[]}[]}}
 */
export function boundsImpact(document, gridWidth, gridHeight) {
    const outsideCells = [];
    const affectedShapes = [];
    const affectedPrimitives = [];

    for (const layer of (document.layers || [])) {
        for (const cell of (layer.cells || [])) {
            if (cell.col < 0 || cell.col >= gridWidth || cell.row < 0 || cell.row >= gridHeight) {
                outsideCells.push(cell);
            }
        }
        for (const [shapeIndex, shape] of (layer.shapes || []).entries()) {
            const bounds = shapeBounds(shape);
            if (!bounds) continue;
            if (bounds.minX < 0 || bounds.minY < 0 || bounds.maxX > gridWidth || bounds.maxY > gridHeight) {
                affectedShapes.push({
                    ...shape,
                    layerId: layer.id,
                    shapeIndex,
                    requiresRemoval: shapeRequiresRemoval(shape, gridWidth, gridHeight),
                });
            }
        }
    }

    for (const [primitiveIndex, primitive] of (document.primitives || []).entries()) {
        const minCol = Math.min(primitive.startCol, primitive.endCol);
        const maxCol = Math.max(primitive.startCol, primitive.endCol);
        const minRow = Math.min(primitive.startRow, primitive.endRow);
        const maxRow = Math.max(primitive.startRow, primitive.endRow);
        if (minCol < 0 || minRow < 0 || maxCol >= gridWidth || maxRow >= gridHeight) {
            affectedPrimitives.push({ ...primitive, primitiveIndex });
        }
    }

    return { outsideCells, affectedShapes, affectedPrimitives };
}
