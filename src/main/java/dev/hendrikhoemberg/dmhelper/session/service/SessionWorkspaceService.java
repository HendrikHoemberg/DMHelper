package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.*;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.LinkedRollableTableView;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableLinkService;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCardAssembler;
import dev.hendrikhoemberg.dmhelper.threat.web.ThreatCardView;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SessionWorkspaceService {

    public enum SelectionSource {
        ACTIVE_ENCOUNTER, CURRENT_SCENE, SESSION_PLAN, EXPLICIT_MAP, STORED_SESSION, NONE
    }

    public record StructuredSceneView(
            Scene scene, List<SceneSection> sections, List<SceneCheck> checks,
            List<SceneParticipant> participants, List<SceneTransition> transitions,
            List<SceneLink> links, Map<UUID, ThreatCardView> sectionThreatCards) {}

    public record QuestProgressView(
            Quest quest, List<QuestObjective> objectives) {}

    public record SessionWorkspace(
            Campaign campaign,
            CampaignSession session,
            GameMap workspaceMap,
            SelectionSource selectionSource,
            Scene currentScene,
            Scene previousScene,
            Scene nextScene,
            Encounter activeEncounter,
            List<Encounter> plannedEncounters,
            SessionPlanService.SessionPlan sessionPlan,
            List<GameMap> maps,
            List<Handout> handouts,
            List<PartyMember> partyMembers,
            CalendarService.InGameDate currentDate,
            StructuredSceneView structuredSceneView,
            List<QuestProgressView> questProgressViews,
            List<LinkedRollableTableView> linkedTables) {}

    private final CampaignRepository campaigns;
    private final CampaignSessionRepository sessions;
    private final AdventureService adventures;
    private final EncounterRepository encounters;
    private final SessionPlanService plans;
    private final GameMapRepository maps;
    private final HandoutRepository handouts;
    private final PartyMemberRepository party;
    private final CalendarService calendar;
    private final QuestRepository questRepository;
    private final RollableTableLinkService rollableTableLinkService;
    private final ThreatCardAssembler threatCardAssembler;

    public SessionWorkspaceService(CampaignRepository campaigns,
                                    CampaignSessionRepository sessions,
                                    AdventureService adventures,
                                    EncounterRepository encounters,
                                    SessionPlanService plans,
                                    GameMapRepository maps,
                                    HandoutRepository handouts,
                                    PartyMemberRepository party,
                                    CalendarService calendar,
                                    QuestRepository questRepository,
                                    RollableTableLinkService rollableTableLinkService,
                                    ThreatCardAssembler threatCardAssembler) {
        this.campaigns = campaigns;
        this.sessions = sessions;
        this.adventures = adventures;
        this.encounters = encounters;
        this.plans = plans;
        this.maps = maps;
        this.handouts = handouts;
        this.party = party;
        this.calendar = calendar;
        this.questRepository = questRepository;
        this.rollableTableLinkService = rollableTableLinkService;
        this.threatCardAssembler = threatCardAssembler;
    }

    public SessionWorkspace load(UUID campaignId, UUID requestedMapId) {
        Campaign campaign = campaigns.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        CampaignSession session = sessions.findByCampaignId(campaignId)
                .orElseGet(() -> CampaignSession.idle(campaign));
        // open-in-view=false: controller/templates touch these after the TX ends
        initializeSessionForView(session);
        Scene current = adventures.getCurrentScene(campaignId).orElse(null);
        initializeSceneForView(current);
        Encounter active = encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE).orElse(null);
        initializeEncounterForView(active);
        SessionPlanService.SessionPlan plan = plans.latest(campaignId).orElse(null);
        Selection selection = select(session, active, current, plan, requestedMapId, campaignId);
        List<Scene> neighbors = editorialNeighbors(current);
        initializeSceneForView(neighbors.get(0));
        initializeSceneForView(neighbors.get(1));
        StructuredSceneView ssv = buildStructuredSceneView(current);
        List<QuestProgressView> qpvs = buildQuestProgressViews(campaignId);
        List<LinkedRollableTableView> linkedTables = current != null
                ? rollableTableLinkService.forScene(campaignId, current.getId())
                : List.of();
        List<Encounter> planned = encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(e -> e.getStatus() == Encounter.Status.PLANNED)
                .toList();
        planned.forEach(SessionWorkspaceService::initializeEncounterForView);
        return new SessionWorkspace(campaign, session, selection.map(), selection.source(), current,
                neighbors.get(0), neighbors.get(1), active,
                planned,
                plan, maps.findByCampaignIdOrderBySortOrderAsc(campaignId),
                handouts.findByCampaignIdOrderByTitleAsc(campaignId),
                party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId),
                calendar.getCurrentDate(campaignId), ssv, qpvs, linkedTables);
    }

    /**
     * Eagerly materialize associations the cockpit reads after this transactional method returns.
     * Transient idle placeholders (no id) already hold plain collections.
     */
    private static void initializeSessionForView(CampaignSession session) {
        if (session.getId() == null) {
            return;
        }
        Hibernate.initialize(session.getAttendees());
        Hibernate.initialize(session.getWorkspaceMap());
        Hibernate.initialize(session.getPresentedMap());
        Hibernate.initialize(session.getPresentedHandout());
        Hibernate.initialize(session.getPlanNote());
    }

    /**
     * Story rail touches statBlocks/handouts/map/encounter/chapter.adventure and structured
     * collections after the transaction ends.
     */
    private static void initializeSceneForView(Scene scene) {
        if (scene == null || scene.getId() == null) {
            return;
        }
        Hibernate.initialize(scene.getStatBlocks());
        Hibernate.initialize(scene.getHandouts());
        Hibernate.initialize(scene.getMap());
        Hibernate.initialize(scene.getEncounter());
        Hibernate.initialize(scene.getSceneAudioCue());
        Hibernate.initialize(scene.getSections());
        Hibernate.initialize(scene.getChecks());
        Hibernate.initialize(scene.getParticipants());
        Hibernate.initialize(scene.getTransitions());
        for (SceneTransition transition : scene.getTransitions()) {
            Hibernate.initialize(transition.getTargetScene());
        }
        Hibernate.initialize(scene.getLinks());
        if (scene.getChapter() != null) {
            Hibernate.initialize(scene.getChapter());
            if (scene.getChapter().getAdventure() != null) {
                Hibernate.initialize(scene.getChapter().getAdventure());
            }
        }
    }

    private static void initializeEncounterForView(Encounter encounter) {
        if (encounter == null || encounter.getId() == null) {
            return;
        }
        Hibernate.initialize(encounter.getMap());
    }

    private StructuredSceneView buildStructuredSceneView(Scene scene) {
        if (scene == null) return null;
        // Collections already initialized for view; copy to plain lists for the view record.
        return new StructuredSceneView(scene,
                List.copyOf(scene.getSections()), List.copyOf(scene.getChecks()),
                List.copyOf(scene.getParticipants()), List.copyOf(scene.getTransitions()),
                List.copyOf(scene.getLinks()), threatCardAssembler.forScene(scene));
    }

    private List<QuestProgressView> buildQuestProgressViews(UUID campaignId) {
        return questRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId).stream()
                .map(q -> {
                    Hibernate.initialize(q.getObjectives());
                    return new QuestProgressView(q, List.copyOf(q.getObjectives()));
                })
                .toList();
    }

    private record Selection(GameMap map, SelectionSource source) {}

    private Selection select(CampaignSession session, Encounter active, Scene current,
                             SessionPlanService.SessionPlan plan, UUID requestedMapId, UUID campaignId) {
        if (session.isOpen())
            return new Selection(session.getWorkspaceMap(), SelectionSource.STORED_SESSION);
        if (active != null && active.getMap() != null)
            return new Selection(active.getMap(), SelectionSource.ACTIVE_ENCOUNTER);
        if (current != null && current.getMap() != null)
            return new Selection(current.getMap(), SelectionSource.CURRENT_SCENE);
        if (plan != null) {
            Optional<GameMap> firstPlanMap = plan.beats().stream()
                    .filter(SessionPlanService.SessionPlanBeat::resolved)
                    .map(SessionPlanService.SessionPlanBeat::mapId)
                    .filter(Objects::nonNull)
                    .map(maps::findById).flatMap(Optional::stream)
                    .filter(m -> m.getCampaign().getId().equals(campaignId)).findFirst();
            if (firstPlanMap.isPresent()) return new Selection(firstPlanMap.get(), SelectionSource.SESSION_PLAN);
        }
        if (requestedMapId != null) {
            GameMap requested = maps.findById(requestedMapId)
                    .filter(m -> m.getCampaign().getId().equals(campaignId))
                    .orElseThrow(() -> new NotFoundException("Map not found in campaign"));
            return new Selection(requested, SelectionSource.EXPLICIT_MAP);
        }
        return new Selection(null, SelectionSource.NONE);
    }

    private List<Scene> editorialNeighbors(Scene current) {
        if (current == null) return java.util.Arrays.asList(null, null);
        List<Scene> flat = adventures.flattenedScenes(current.getChapter().getAdventure().getId());
        int idx = indexOf(flat, current.getId());
        Scene prev = idx > 0 ? flat.get(idx - 1) : null;
        Scene next = idx < flat.size() - 1 ? flat.get(idx + 1) : null;
        return java.util.Arrays.asList(prev, next);
    }

    private int indexOf(List<Scene> scenes, UUID id) {
        for (int i = 0; i < scenes.size(); i++) {
            if (scenes.get(i).getId().equals(id)) return i;
        }
        return -1;
    }
}
