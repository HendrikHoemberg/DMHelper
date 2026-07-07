/**
 * Built-in terrain palette. Custom entries (name + color + walkable, SPEC §4.3)
 * are stored per map in the map document's `customTerrain` array and merged in
 * by the editor at load time.
 */
export const BUILTIN_TERRAIN = {
    floor:     { name: 'Floor',             fill: '#2a2a3e', stroke: '#3a3a5e', walkable: true },
    wall:      { name: 'Wall',              fill: '#4a4a5e', stroke: '#5a5a6e', walkable: false },
    water:     { name: 'Water',             fill: '#1a3a6e', stroke: '#2a4a7e', walkable: false },
    difficult: { name: 'Difficult Terrain', fill: '#3a4a1e', stroke: '#4a5a2e', walkable: true },
    lava:      { name: 'Lava',              fill: '#6e2a1a', stroke: '#7e3a2a', walkable: false },
    pit:       { name: 'Pit',               fill: '#1a1a1a', stroke: '#2a2a2a', walkable: false },
    door:      { name: 'Door',              fill: '#8a6a2e', stroke: '#9a7a3e', walkable: true },
};

export const DEFAULT_TERRAIN = 'floor';

export const SHAPE_COLORS = {
    fill: '#8B4513',
    stroke: '#654321',
};
