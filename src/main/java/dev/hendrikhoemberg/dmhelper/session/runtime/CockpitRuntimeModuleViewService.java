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
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionPlanService;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCardAssembler;
import dev.hendrikhoemberg.dmhelper.threat.web.ThreatCardView;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CockpitRuntimeModuleViewService {

    public record StoryView(UUID sceneId, String title, String readAloud,
                            List<SectionView> sections, List<CheckView> checks,
                            List<ParticipantView> participants, List<TransitionView> transitions,
                            List<LinkView> links, Map<UUID, ThreatCardView> sectionThreatCards,
                            boolean canSeedEncounter) {}

    public record SectionView(String label, String body, String kind) {}

    public record CheckView(String label, String ability, String skill, Integer dc) {}

    public record ParticipantView(String displayName, int quantity, String disposition,
                                  String statBlockName, Integer statBlockAc, String statBlockHp,
                                  String placementHint) {}

    public record TransitionView(String kind, String label, String targetSceneTitle,
                                 String externalDestination, String condition) {}

    public record LinkView(String label, String role) {}

    public record MapView(UUID mapId, String name, int gridWidth, int gridHeight, int cellSizePx,
                          String movementMode, boolean showGrid, List<MapItemView> maps,
                          String selectionSource, String sessionStatus, String presentationMode) {}

    public record MapItemView(UUID id, String name) {}

    public record EncounterView(UUID activeEncounterId, String activeEncounterName,
                                String combatPhase, List<CombatantView> combatants,
                                List<PlannedEncounterView> planned) {}

    public record CombatantView(UUID id, String name, int initiative, int maxHp, int currentHp,
                                String statBlockName) {}

    public record PlannedEncounterView(UUID id, String name, UUID mapId) {}

    public record SessionPlanView(String title, List<BeatView> beats,
                                  List<QuestProgressView> quests) {}

    public record BeatView(int position, String type, UUID targetId, String label,
                           String url, UUID mapId, boolean resolved) {}

    public record QuestProgressView(UUID questId, String title, String status,
                                    List<ObjectiveView> objectives) {}

    public record ObjectiveView(UUID id, String title, String status) {}

    public record PartyView(List<PartyMemberView> members) {}

    public record PartyMemberView(UUID id, String name, int ac, int maxHp, int currentHp,
                                  int tempHp, int passivePerception, int passiveInsight,
                                  int passiveInvestigation, int deathSaveSuccesses,
                                  int deathSaveFailures, String conditionsJson,
                                  String sheetUrl, boolean inspiration, int exhaustion, int xp) {}

    public record QuickNotesView(List<QuickNoteView> notes) {}

    public record QuickNoteView(UUID id, String body, String createdAt) {}

    public record PresentationView(String mode, UUID presentedMapId, String presentedHandoutTitle) {}

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

    public CockpitRuntimeModuleViewService(AdventureService adventures,
                                           EncounterRepository encounters,
                                           CombatantRepository combatants,
                                           GameMapRepository maps,
                                           PartyMemberRepository party,
                                           CampaignSessionRepository sessions,
                                           QuickNoteRepository quickNotes,
                                           SessionPlanService plans,
                                           ThreatCardAssembler threatCardAssembler) {
        this.adventures = adventures;
        this.encounters = encounters;
        this.combatants = combatants;
        this.maps = maps;
        this.party = party;
        this.sessions = sessions;
        this.quickNotes = quickNotes;
        this.plans = plans;
        this.threatCardAssembler = threatCardAssembler;
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

        return new StoryView(
                current.getId(), current.getTitle(), readAloud,
                List.copyOf(current.getSections().stream()
                        .map(s -> new SectionView(s.getLabel(), s.getBody(), s.getKind().name()))
                        .toList()),
                List.copyOf(current.getChecks().stream()
                        .map(c -> new CheckView(c.getLabel(),
                                c.getAbility(),
                                c.getSkill(),
                                c.getDc()))
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
                        .map(t -> new TransitionView(t.getKind().name(), t.getLabel(),
                                t.getTargetScene() != null ? t.getTargetScene().getTitle() : null,
                                t.getExternalDestination(), t.getCondition()))
                        .toList()),
                List.copyOf(current.getLinks().stream()
                        .map(l -> new LinkView(l.getDisplayText(),
                                l.getRole() != null ? l.getRole().name() : null))
                        .toList()),
                Map.copyOf(threatCards),
                canSeed);
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
                session != null ? session.getStatus().name() : "IDLE",
                session != null ? session.getPresentationMode().name() : "CURTAIN");
    }

    public EncounterView encounter(UUID campaignId) {
        Encounter active = encounters.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE).orElse(null);
        List<Encounter> planned = encounters.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(e -> e.getStatus() == Encounter.Status.PLANNED)
                .toList();
        planned.forEach(e -> Hibernate.initialize(e.getMap()));
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
        return new EncounterView(
                active != null ? active.getId() : null,
                active != null ? active.getName() : null,
                active != null ? active.getCombatPhase().name() : null,
                combatantViews,
                List.copyOf(planned.stream()
                        .map(e -> new PlannedEncounterView(e.getId(), e.getName(),
                                e.getMap() != null ? e.getMap().getId() : null))
                        .toList()));
    }

    public SessionPlanView sessionPlan(UUID campaignId) {
        var plan = plans.latest(campaignId).orElse(null);
        if (plan == null) return new SessionPlanView(null, List.of(), List.of());
        return new SessionPlanView(
                plan.title(),
                List.copyOf(plan.beats().stream()
                        .map(b -> new BeatView(b.position(), b.type(), b.targetId(),
                                b.label(), b.url(), b.mapId(), b.resolved()))
                        .toList()),
                List.of());
    }

    public PartyView party(UUID campaignId) {
        List<PartyMember> members = party.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId);
        return new PartyView(List.copyOf(members.stream()
                .map(m -> new PartyMemberView(
                        m.getId(), m.getCharacterName(), m.getAc(), m.getMaxHp(), m.getCurrentHp(),
                        m.getTempHp(), m.getPassivePerception(), m.getPassiveInsight(),
                        m.getPassiveInvestigation(), m.getDeathSaveSuccesses(), m.getDeathSaveFailures(),
                        m.getConditionsJson(),
                        "/campaigns/" + campaignId + "/party/" + m.getId() + "/sheet",
                        m.isInspiration(), m.getExhaustion(), m.getXp()))
                .toList()));
    }

    public QuickNotesView quickNotes(UUID campaignId) {
        List<QuickNote> notes = quickNotes.findByCampaignIdOrderByCreatedAtDesc(campaignId);
        return new QuickNotesView(List.copyOf(notes.stream()
                .map(n -> new QuickNoteView(n.getId(), n.getBody(),
                        n.getCreatedAt() != null ? n.getCreatedAt().toString() : null))
                .toList()));
    }

    public PresentationView presentation(UUID campaignId) {
        CampaignSession session = sessions.findByCampaignId(campaignId).orElse(null);
        if (session == null) return new PresentationView("CURTAIN", null, null);
        Hibernate.initialize(session.getPresentedMap());
        Hibernate.initialize(session.getPresentedHandout());
        return new PresentationView(
                session.getPresentationMode().name(),
                session.getPresentedMap() != null ? session.getPresentedMap().getId() : null,
                session.getPresentedHandout() != null ? session.getPresentedHandout().getTitle() : null);
    }

    public ReferenceView reference(UUID campaignId) {
        return new ReferenceView();
    }

    public AudioView audio(UUID campaignId) {
        return new AudioView();
    }

    public SessionLogView sessionLog(UUID campaignId) {
        return new SessionLogView();
    }
}
