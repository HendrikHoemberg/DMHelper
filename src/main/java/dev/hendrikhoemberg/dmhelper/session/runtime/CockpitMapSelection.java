package dev.hendrikhoemberg.dmhelper.session.runtime;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.service.SessionPlanService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CockpitMapSelection {

    private CockpitMapSelection() {}

    public static SessionWorkspaceService.Selection resolve(
            CampaignSession session, Encounter active, Scene current,
            SessionPlanService.SessionPlan plan, UUID requestedMapId, UUID campaignId,
            GameMapRepository maps) {
        if (requestedMapId != null) {
            GameMap requested = maps.findById(requestedMapId)
                    .filter(m -> m.getCampaign().getId().equals(campaignId))
                    .orElseThrow(() -> new NotFoundException("Map not found in campaign"));
            return new SessionWorkspaceService.Selection(requested, SessionWorkspaceService.SelectionSource.EXPLICIT_MAP);
        }
        if (session != null && session.isOpen() && session.getWorkspaceMap() != null)
            return new SessionWorkspaceService.Selection(session.getWorkspaceMap(), SessionWorkspaceService.SelectionSource.STORED_SESSION);
        if (active != null && active.getMap() != null)
            return new SessionWorkspaceService.Selection(active.getMap(), SessionWorkspaceService.SelectionSource.ACTIVE_ENCOUNTER);
        if (current != null && current.getMap() != null)
            return new SessionWorkspaceService.Selection(current.getMap(), SessionWorkspaceService.SelectionSource.CURRENT_SCENE);
        if (plan != null) {
            Optional<GameMap> firstPlanMap = plan.beats().stream()
                    .filter(SessionPlanService.SessionPlanBeat::resolved)
                    .map(SessionPlanService.SessionPlanBeat::mapId)
                    .filter(Objects::nonNull)
                    .map(maps::findById).flatMap(Optional::stream)
                    .filter(m -> m.getCampaign().getId().equals(campaignId)).findFirst();
            if (firstPlanMap.isPresent())
                return new SessionWorkspaceService.Selection(firstPlanMap.get(), SessionWorkspaceService.SelectionSource.SESSION_PLAN);
        }
        return new SessionWorkspaceService.Selection(null, SessionWorkspaceService.SelectionSource.NONE);
    }
}
