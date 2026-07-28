package dev.hendrikhoemberg.dmhelper.gamemap.service;

import java.util.UUID;

/**
 * Published inside a transaction after map markers or encounter placements change.
 * Consumers should observe it after commit so player projections never expose
 * uncommitted state.
 */
public record MapRuntimeChanged(UUID campaignId, UUID mapId) {
}
