/**
 * Built-in terrain palette. Custom entries (name + color + walkable, SPEC §4.3)
 * are stored per map in the map document's `customTerrain` array and merged in
 * by the editor at load time.
 */
/* Hues stay recognizable (water = blue, difficult = olive) but saturation and
   temperature match the grimoire palette in tokens.css. */
export const BUILTIN_TERRAIN = {
    floor:     { name: 'Floor',             fill: '#2a2318', stroke: '#3a3125', walkable: true },
    wall:      { name: 'Wall',              fill: '#4d4438', stroke: '#5d5448', walkable: false },
    water:     { name: 'Water',             fill: '#23405a', stroke: '#33506a', walkable: false },
    difficult: { name: 'Difficult Terrain', fill: '#414a26', stroke: '#515a36', walkable: true },
    lava:      { name: 'Lava',              fill: '#7a2f1c', stroke: '#8a3f2c', walkable: false },
    pit:       { name: 'Pit',               fill: '#14100c', stroke: '#241f18', walkable: false },
    door:      { name: 'Door',              fill: '#8a6a2e', stroke: '#9a7a3e', walkable: true },
};

export const DEFAULT_TERRAIN = 'floor';
/** UI-only sentinel for the palette picker's "Erase" entry — not a real terrain key.
 *  Selecting it sets the active terrain to DEFAULT_TERRAIN, same as painting Floor. */
export const ERASE_KEY = '__erase__';

export const SHAPE_COLORS = {
    fill: '#8B4513',
    stroke: '#654321',
};
