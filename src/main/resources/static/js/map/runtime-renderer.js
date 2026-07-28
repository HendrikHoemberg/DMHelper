import { expandPrimitives } from './shared.js';
import { BUILTIN_TERRAIN, DEFAULT_TERRAIN } from './terrain-palette.js';

const DEFAULT_SHAPE_FILL = 'rgba(74, 158, 255, 0.25)';
const DEFAULT_SHAPE_STROKE = '#4a9eff';

function includedLayers(document, playerView) {
    return (document?.layers || []).filter(layer =>
        layer?.visible !== false && (!playerView || layer.playerVisible !== false));
}

function mark(node, kind, layerId) {
    node.setAttr('_runtimeKind', kind);
    node.setAttr('_runtimeLayerId', layerId);
    return node;
}

function terrainFill(document, terrainKey) {
    const custom = (document?.customTerrain || []).find(item => item.key === terrainKey);
    return custom?.fill || BUILTIN_TERRAIN[terrainKey]?.fill
        || BUILTIN_TERRAIN[DEFAULT_TERRAIN].fill;
}

function addCell(Konva, group, document, layerId, cell, cellSizePx) {
    if (!cell || !Number.isFinite(cell.col) || !Number.isFinite(cell.row)) return;
    group.add(mark(new Konva.Rect({
        x: cell.col * cellSizePx,
        y: cell.row * cellSizePx,
        width: cellSizePx,
        height: cellSizePx,
        fill: terrainFill(document, cell.terrain),
        stroke: 'rgba(255,255,255,0.05)',
        strokeWidth: 0.5,
        listening: false,
    }), 'terrain', layerId));
}

function addShape(Konva, group, layerId, shape, cellSizePx) {
    if (!shape || !Array.isArray(shape.points)) return;
    const px = value => Number(value) * cellSizePx;
    const points = shape.points;
    const common = {
        fill: shape.fill || DEFAULT_SHAPE_FILL,
        stroke: shape.stroke || DEFAULT_SHAPE_STROKE,
        strokeWidth: shape.strokeWidth || 2,
        listening: false,
    };
    let node;
    switch (shape.type) {
        case 'rect':
            if (points.length < 4) return;
            node = new Konva.Rect({
                ...common, x: px(points[0]), y: px(points[1]),
                width: px(points[2]), height: px(points[3]),
            });
            break;
        case 'circle':
            if (points.length < 3) return;
            node = new Konva.Circle({
                ...common, x: px(points[0]), y: px(points[1]), radius: px(points[2]),
            });
            break;
        case 'line':
            if (points.length < 4) return;
            node = new Konva.Line({
                ...common, fillEnabled: false, points: points.map(px), lineCap: 'round',
            });
            break;
        case 'polygon':
            if (points.length < 6) return;
            node = new Konva.Line({
                ...common, points: points.map(px), closed: true, lineJoin: 'round',
            });
            break;
        default:
            return;
    }
    group.add(mark(node, 'shape', layerId));
    if (shape.label) {
        group.add(mark(new Konva.Text({
            x: px(points[0]) + 2,
            y: px(points[1]) + 2,
            text: shape.label,
            fontSize: cellSizePx * 0.3,
            fill: '#fff',
            listening: false,
        }), 'label', layerId));
    }
}

function loadImage(dataUrl) {
    return new Promise(resolve => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = () => {
            console.warn('Skipping background image that could not be decoded');
            resolve(null);
        };
        image.src = dataUrl;
    });
}

async function addBackground(Konva, group, layerId, imageDto, cellSizePx) {
    if (!imageDto?.dataUrl) return;
    const image = await loadImage(imageDto.dataUrl);
    if (!image) return;
    group.add(mark(new Konva.Image({
        image,
        x: imageDto.x * cellSizePx,
        y: imageDto.y * cellSizePx,
        width: imageDto.width * cellSizePx,
        height: imageDto.height * cellSizePx,
        rotation: imageDto.rotationDeg || 0,
        listening: false,
    }), 'image', layerId));
}

/**
 * Render an immutable map document into one clipped Konva layer.
 * Player rendering excludes layers explicitly marked playerVisible=false.
 */
export async function renderRuntimeDocument({
    Konva, document, targetLayer, gridWidth, gridHeight, cellSizePx, playerView = false,
}) {
    if (!document || !targetLayer) return;
    targetLayer.destroyChildren();

    const root = new Konva.Group({
        clip: {
            x: 0,
            y: 0,
            width: gridWidth * cellSizePx,
            height: gridHeight * cellSizePx,
        },
        listening: false,
    });
    targetLayer.add(root);
    const layers = includedLayers(document, playerView);

    for (const layer of layers.filter(item => item.type === 'IMAGE')) {
        await addBackground(Konva, root, layer.id, layer.image, cellSizePx);
    }

    const terrainLayers = layers.filter(item => item.type === 'TERRAIN');
    const explicitCells = terrainLayers.flatMap(layer => layer.cells || []);
    const explicitKeys = new Set(explicitCells.map(cell => `${cell.col},${cell.row}`));
    const primitiveDocument = playerView
        ? {
            ...document,
            primitives: (document.primitives || [])
                .filter(primitive => primitive.playerVisible !== false),
        }
        : document;
    const primitiveCells = expandPrimitives(primitiveDocument)
        .filter(cell => !explicitKeys.has(`${cell.col},${cell.row}`));
    for (const cell of primitiveCells) {
        addCell(Konva, root, document, 'primitives', cell, cellSizePx);
    }

    for (const layer of layers) {
        for (const cell of (layer.cells || [])) {
            addCell(Konva, root, document, layer.id, cell, cellSizePx);
        }
        for (const shape of (layer.shapes || [])) {
            addShape(Konva, root, layer.id, shape, cellSizePx);
        }
    }
    targetLayer.batchDraw();
}
