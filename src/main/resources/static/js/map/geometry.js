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

function shapeBounds(shape) {
    const pts = shape.points || [];
    switch (shape.type) {
        case 'rect':
            return { minX: pts[0], minY: pts[1], maxX: pts[0] + pts[2], maxY: pts[1] + pts[3] };
        case 'circle':
            return { minX: pts[0] - pts[2], minY: pts[1] - pts[2], maxX: pts[0] + pts[2], maxY: pts[1] + pts[2] };
        case 'line':
        case 'polygon':
        default: {
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

export function boundsImpact(document, gridWidth, gridHeight) {
    const outsideCells = [];
    const affectedShapes = [];

    for (const layer of (document.layers || [])) {
        for (const cell of (layer.cells || [])) {
            if (cell.col < 0 || cell.col >= gridWidth || cell.row < 0 || cell.row >= gridHeight) {
                outsideCells.push(cell);
            }
        }
        for (const shape of (layer.shapes || [])) {
            const bounds = shapeBounds(shape);
            if (bounds.minX < 0 || bounds.minY < 0 || bounds.maxX > gridWidth || bounds.maxY > gridHeight) {
                affectedShapes.push(shape);
            }
        }
    }

    return { outsideCells, affectedShapes };
}
