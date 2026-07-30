package dev.hendrikhoemberg.dmhelper.session.runtime;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneCheck;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLink;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransition;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPlacementService;
import dev.hendrikhoemberg.dmhelper.encounter.service.ReadinessVerdict;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionPlanService;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCardAssembler;
import dev.hendrikhoemberg.dmhelper.threat.web.ThreatCardView;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CockpitRuntimeModuleViewService {

    public record StoryView(UUID sceneId, String title, String summary, String body, String readAloud,
                            List<SectionView> sections, List<CheckView> checks,
                            List<ParticipantView> participants, List<TransitionView> transitions,
                            List<LinkView> links, Map<UUID, ThreatCardView> sectionThreatCards,
                            boolean canSeedEncounter, boolean hasPrevious, boolean hasNext,
                            UUID adventureId, UUID mapId, String mapName,
                            UUID linkedEncounterId) {}

    public record SectionView(UUID id, String label, String body, String kind) {}

    public record CheckView(String label, String ability, String skill, Integer dc,
                            String success, String failure) {}

    public record ParticipantView(String displayName, int quantity, String disposition,
                                  String statBlockName, Integer statBlockAc, String statBlockHp,
                                  String placementHint) {}

    public record TransitionView(UUID id, String kind, String label, String targetSceneTitle,
                                 String externalDestination, String condition) {}

    public record LinkView(String label, String role) {}

    public record MapView(UUID mapId, String name, int gridWidth, int gridHeight, int cellSizePx,
                          String movementMode, boolean showGrid, List<MapItemView> maps,
                          String selectionSource, String sessionStatus) {}

    public record MapItemView(UUID id, String name) {}

    public record EncounterView(UUID activeEncounterId, String activeEncounterName,
                                UUID activeEncounterMapId, String combatPhase,
                                List<CombatantView> combatants,
                                List<PlannedEncounterView> planned,
                                List<PlannedEncounterView> suspended,
                                List<PlannedEncounterView> finished) {}

    public record CombatantView(UUID id, String name, int initiative, int maxHp, int currentHp,
                                String statBlockName) {}

    public record PlannedEncounterView(UUID id, String name, UUID mapId, String mapName,
                                       ReadinessVerdict verdict, int combatantCount, int unplacedCount) {}

    public record SessionPlanView(String title, List<BeatView> beats,
                                  List<QuestProgressView> questProgress,
                                  List<BeatView> upcoming) {}

    public record BeatView(int position, String type, UUID targetId, String label,
                           String url, UUID mapId, boolean resolved,
                           boolean current, boolean upcoming) {}

    public record QuestProgressView(UUID questId, String title, String status,
                                    List<ObjectiveView> objectives) {}

    public record ObjectiveView(UUID id, String title, String status) {}

    public record PartyView(List<PartyMemberView> members) {}

    public record PartyMemberView(UUID id, String name, int ac, int speed, int maxHp, int currentHp,
                                  int tempHp, int passivePerception, int passiveInsight,
                                  int passiveInvestigation, int deathSaveSuccesses,
                                  int deathSaveFailures, String conditionsJson,
                                  String sheetUrl, boolean inspiration, int exhaustion, int xp,
                                  List<String> conditions) {}

    public record QuickNotesView(List<QuickNoteView> notes) {}

    public record QuickNoteView(UUID id, String body, String createdAt) {}

    public record ReferenceView() {}

    public record AudioView() {}

    public record SessionLogView() {}

    private final AdventureService adventures;
    private final EncounterRepository encounters;
    private final CombatantRepository combatants;
    private final GameMapRepository maps;
    private final PartyMemberRepository party;
    private final CampaignSessionRepository sessions;
    private final QuickNoteRepository quickNotes;
    private final SessionPlanService plans;
    private final ThreatCardAssembler threatCardAssembler;
    private final SessionLogModuleService sessionLogService;
    private final QuestRepository questRepository;

    public CockpitRuntimeModuleViewService(AdventureService adventures,
                                           EncounterRepository encounters,
                                           CombatantRepository combatants,
                                           GameMapRepository maps,
                                           PartyMemberRepository party,
                                           CampaignSessionRepository sessions,
                                           QuickNoteRepository quickNotes,
                                           SessionPlanService plans,
                                           ThreatCardAssembler threatCardAssembler,
                                           SessionLogModuleService sessionLogService,
                                           QuestRepository questRepository) {
        this.adventures = adventures;
        this.encounters = encounters;
        this.combatants = combatants;
        this.maps = maps;
        this.party = party;
        this.sessions = sessions;
        this.quickNotes = quickNotes;
        this.plans = plans;
        this.threatCardAssembler = threatCardAssembler;
        this.sessionLogService = sessionLogService;
        this.questRepository = questRepository;
    }

    public StoryView story(UUID campaignId) {
        Scene current = adventures.getCurrentScene(campaignId).orElse(null);
        if (current == null) return null;
        Hibernate.initialize(current.getSections());
        Hibernate.initialize(current.getChecks());
        Hibernate.initialize(current.getParticipants());
        Hibernate.initialize(current.getTransitions());
        Hibernate.initialize(current.getLinks());
        for (SceneParticipant p : current.getParticipants()) {
            Hibernate.initialize(p.getStatBlock());
        }
        for (SceneTransition t : current.getTransitions()) {
            Hibernate.initialize(t.getTargetScene());
        }
        String readAloud = current.getSections().stream()
                .filter(s -> s.getKind() == dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionKind.READ_ALOUD)
                .findFirst()
                .map(SceneSection::getBody)
                .orElse(null);

        Map<UUID, ThreatCardView> threatCards = threatCardAssembler.forScene(current);
        boolean canSeed = current.getEncounter() == null;

        List<Scene> flat = adventures.flattenedScenes(current.getChapter().getAdventure().getId());
        int idx = indexOf(flat, current.getId());
        boolean hasPrev = idx > 0;
        boolean hasNext = idx < flat.size() - 1;
        UUID adventureId = current.getChapter().getAdventure().getId();
        UUID mapId = current.getMap() != null ? current.getMap().getId() : null;
        String mapName = current.getMap() != null ? current.getMap().getName() : null;

        UUID linkedEncounterId = current.getEncounter() != null ? current.getEncounter().getId() : null;

        return new StoryView(
                current.getId(), current.getTitle(), current.getSummary(), current.getBody(), readAloud,
                List.copyOf(current.getSections().stream()
                        .map(s -> new SectionView(s.getId(), s.getLabel(), s.getBody(), s.getKind().name()))
                        .toList()),
                List.copyOf(current.getChecks().stream()
                        .map(c -> new CheckView(c.getLabel(),
                                c.getAbility(),
                                c.getSkill(),
                                c.getDc(),
                                c.getSuccess(),
                                c.getFailure()))
                        .toList()),
                List.copyOf(current.getParticipants().stream()
                        .map(p -> new ParticipantView(p.getDisplayName(), p.getQuantity(),
                                p.getDisposition() != null ? p.getDisposition().name() : null,
                                p.getStatBlock() != null ? p.getStatBlock().getName() : null,
                                p.getStatBlock() != null ? p.getStatBlock().getAc() : null,
                                p.getStatBlock() != null ? p.getStatBlock().getHp() : null,
                                p.getPlacementHint()))
                        .toList()),
                List.copyOf(current.getTransitions().stream()
                        .map(t -> new TransitionView(t.getId(), t.getKind().name(), t.getLabel(),
                                t.getTargetScene() != null ? t.getTargetScene().getTitle() : null,
                                t.getExternalDestination(), t.getCondition()))
                        .toList()),
                List.copyOf(current.getLinks().stream()
                        .map(l -> new LinkView(l.getDisplayText(),
                                l.getRole() != null ? l.getRole().name() : null))
                        .toList()),
                Map.copyOf(threatCards),
                canSeed, hasPrev, hasNext, adventureId, mapId, mapName,
                linkedEncounterId);
    }

    private static final Logger log = LoggerFactory.getLogger(CockpitRuntimeModuleViewService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> CONDITIONS_TYPE = new TypeReference<>() {};

    private static List<String> parseConditions(String conditionsJson) {
        if (conditionsJson == null || conditionsJson.isBlank() || "[]".equals(conditionsJson)) {
            return List.of();
        }
        try {
            return JSON.readValue(conditionsJson, CONDITIONS_TYPE);
        } catch (JacksonException e) {
            log.warn("Malformed conditionsJson, using empty list: {}", e.getMessage());
            return List.of();
        }
    }

    private static int indexOf(List<Scene> scenes, UUID id) {
        for (int i = 0; i < scenes.size(); i++) {
            if (scenes.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    public MapView map(UUID campaignId, UUID requestedMapId) {
        CampaignSession session = sessions.findByCampaignId(campaignId).orElse(null);
        if (session == null) {
            var campaign = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).stream()
                    .findFirst().map(GameMap::getCampaign).orElse(null);
            if (campaign != null) session = CampaignSession.idle(campaign);
        }
        var selection = CockpitMapSelection.resolve(
                session,
                encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE).orElse(null),
                adventures.getCurrentScene(campaignId).orElse(null),
                plans.latest(campaignId).orElse(null),
                requestedMapId, campaignId, maps);
        GameMap selected = selection.map();
        if (selected == null) return null;
        Hibernate.initialize(selected);
        List<GameMap> campaignMaps = maps.findByCampaignIdOrderBySortOrderAsc(campaignId);
        return new MapView(
                selected.getId(), selected.getName(),
                selected.getGridWidth(), selected.getGridHeight(), selected.getCellSizePx(),
                selected.getMovementMode(), selected.isShowGrid(),
                List.copyOf(campaignMaps.stream()
                        .map(m -> new MapItemView(m.getId(), m.getName()))
                        .toList()),
                selection.source().name(),
                session != null ? session.getStatus().name() : "IDLE");
    }

    public EncounterView encounter(UUID campaignId) {
        Encounter active = encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE).orElse(null);
        List<Encounter> planned = encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(e -> e.getStatus() == Encounter.Status.PLANNED)
                .toList();
        List<Encounter> suspended = encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(e -> e.getStatus() == Encounter.Status.SUSPENDED)
                .toList();
        planned.forEach(e -> Hibernate.initialize(e.getMap()));
        suspended.forEach(e -> Hibernate.initialize(e.getMap()));
        List<CombatantView> combatantViews = List.of();
        if (active != null) {
            List<Combatant> activeCombatants = combatants.findByEncounterIdOrderBySortOrderAsc(active.getId());
            combatantViews = List.copyOf(activeCombatants.stream()
                    .map(c -> {
                        Hibernate.initialize(c.getStatBlock());
                        return new CombatantView(c.getId(), c.getName(),
                                c.getInitiative() != null ? c.getInitiative() : 0,
                                c.getMaxHp(), c.getCurrentHp(),
                                c.getStatBlock() != null ? c.getStatBlock().getName() : null);
                    })
                    .toList());
        }

        List<Encounter> done = encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(e -> e.getStatus() == Encounter.Status.DONE)
                .sorted(java.util.Comparator.comparingLong(Encounter::getLogSequence).reversed())
                .limit(5)
                .toList();
        done.forEach(e -> Hibernate.initialize(e.getMap()));

        java.util.function.Function<Encounter, PlannedEncounterView> toView = e -> {
            var combatantList = combatants.findByEncounterIdOrderBySortOrderAsc(e.getId());
            int combatantCount = combatantList.size();
            long unplacedCount = combatantList.stream().filter(c -> c.getPlacement() == null).count();
            ReadinessVerdict verdict = EncounterPlacementService.verdictFor(
                    e.getMap() != null, false, combatantCount, (int) unplacedCount);
            return new PlannedEncounterView(e.getId(), e.getName(),
                    e.getMap() != null ? e.getMap().getId() : null,
                    e.getMap() != null ? e.getMap().getName() : null,
                    verdict, combatantCount, (int) unplacedCount);
        };

        return new EncounterView(
                active != null ? active.getId() : null,
                active != null ? active.getName() : null,
                active != null && active.getMap() != null ? active.getMap().getId() : null,
                active != null ? active.getCombatPhase().name() : null,
                combatantViews,
                List.copyOf(planned.stream().map(toView).toList()),
                List.copyOf(suspended.stream().map(toView).toList()),
                List.copyOf(done.stream().map(toView).toList()));
    }

    public SessionPlanView sessionPlan(UUID campaignId) {
        List<QuestProgressView> questProgress = List.copyOf(
                questRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId).stream()
                        .map(q -> {
                            Hibernate.initialize(q.getObjectives());
                            return new QuestProgressView(q.getId(), q.getTitle(), q.getStatus().name(),
                                    List.copyOf(q.getObjectives().stream()
                                            .map(o -> new ObjectiveView(o.getId(), o.getTitle(),
                                                    o.getStatus().name()))
                                            .toList()));
                        })
                        .toList());
        var plan = plans.latest(campaignId).orElse(null);
        if (plan == null) return new SessionPlanView(null, List.of(), questProgress, List.of());

        var planBeats = plan.beats();
        int firstUnresolved = -1;
        for (int i = 0; i < planBeats.size(); i++) {
            if (!planBeats.get(i).resolved()) { firstUnresolved = i; break; }
        }
        final int currentIdx = firstUnresolved;
        List<BeatView> beats = java.util.stream.IntStream.range(0, planBeats.size())
                .mapToObj(i -> {
                    var b = planBeats.get(i);
                    return new BeatView(b.position(), b.type(), b.targetId(), b.label(),
                            b.url(), b.mapId(), b.resolved(),
                            i == currentIdx, currentIdx >= 0 && i > currentIdx);
                })
                .toList();
        List<BeatView> upcoming = List.copyOf(beats.stream().filter(BeatView::upcoming).toList());
        return new SessionPlanView(plan.title(), List.copyOf(beats), questProgress, upcoming);
    }

    public PartyView party(UUID campaignId) {
        List<PartyMember> members = party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId);
        return new PartyView(List.copyOf(members.stream()
                .map(m -> new PartyMemberView(
                        m.getId(), m.getCharacterName(), m.getAc(), m.getSpeed(), m.getMaxHp(), m.getCurrentHp(),
                        m.getTempHp(), m.getPassivePerception(), m.getPassiveInsight(),
                        m.getPassiveInvestigation(), m.getDeathSaveSuccesses(), m.getDeathSaveFailures(),
                        m.getConditionsJson(),
                        "/campaigns/" + campaignId + "/party/" + m.getId() + "/sheet",
                        m.isInspiration(), m.getExhaustion(), m.getXp(),
                        parseConditions(m.getConditionsJson())))
                .toList()));
    }

    public QuickNotesView quickNotes(UUID campaignId) {
        List<QuickNote> notes = quickNotes.findByCampaignIdOrderByCreatedAtDesc(campaignId);
        return new QuickNotesView(List.copyOf(notes.stream()
                .map(n -> new QuickNoteView(n.getId(), n.getBody(),
                        n.getCreatedAt() != null ? n.getCreatedAt().toString() : null))
                .toList()));
    }

    public ReferenceView reference(UUID campaignId) {
        return new ReferenceView();
    }

    public AudioView audio(UUID campaignId) {
        return new AudioView();
    }

    public SessionLogModuleService.SessionLogView sessionLog(UUID campaignId, CockpitModuleMode mode) {
        return sessionLogService.sessionLog(campaignId, mode);
    }
}
