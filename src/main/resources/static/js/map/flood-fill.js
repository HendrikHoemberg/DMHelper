/**
 * Terrain bucket-fill: finds every cell 4-directionally connected to (startCol, startRow)
 * that shares its terrain (absent cells count as the same "default floor" terrain).
 * Pure function — no DOM/Konva — so it's trivial to reason about and reuse (e.g. from a
 * future test runner) independently of the canvas.
 *
 * @param {{col:number, row:number, terrain:string}[]} cells — painted cells on one layer
 * @param {number} gridWidth
 * @param {number} gridHeight
 * @param {number} startCol
 * @param {number} startRow
 * @param {string} defaultTerrain — terrain key for cells absent from `cells`
 * @returns {{col:number, row:number}[]} the connected region (always includes the start cell)
 */
export function floodFillCells(cells, gridWidth, gridHeight, startCol, startRow, defaultTerrain) {
    if (startCol < 0 || startCol >= gridWidth || startRow < 0 || startRow >= gridHeight) return [];

    const terrainAt = new Map();
    for (const c of cells) terrainAt.set(`${c.col},${c.row}`, c.terrain);
    const targetTerrain = terrainAt.get(`${startCol},${startRow}`) ?? defaultTerrain;

    const visited = new Set();
    const region = [];
    const stack = [{ col: startCol, row: startRow }];

    while (stack.length) {
        const { col, row } = stack.pop();
        const key = `${col},${row}`;
        if (visited.has(key)) continue;
        if (col < 0 || col >= gridWidth || row < 0 || row >= gridHeight) continue;
        const terrain = terrainAt.get(key) ?? defaultTerrain;
        if (terrain !== targetTerrain) continue;

        visited.add(key);
        region.push({ col, row });
        stack.push({ col: col + 1, row }, { col: col - 1, row }, { col, row: row + 1 }, { col, row: row - 1 });
    }
    return region;
}
