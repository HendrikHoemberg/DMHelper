package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.PresentationInvalidated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TablePresentationService {

    private static final Logger log = LoggerFactory.getLogger(TablePresentationService.class);

    private final PlayerSafeProjectionService projectionService;
    private final GameMapRepository gameMapRepository;
    private final EncounterRepository encounterRepository;
    private final CombatantRepository combatantRepository;
    private final HandoutRepository handoutRepository;
    private final CampaignSessionRepository sessionRepository;

    private volatile LiveTableState currentState;
    private volatile UUID currentCampaignId;
    private Runnable onStateChange;

    private volatile List<LiveTableState.AoeTemplateSnapshot> currentAoEs = List.of();

    public TablePresentationService(PlayerSafeProjectionService projectionService,
                                    GameMapRepository gameMapRepository,
                                    EncounterRepository encounterRepository,
                                    CombatantRepository combatantRepository,
                                    HandoutRepository handoutRepository,
                                    CampaignSessionRepository sessionRepository) {
        this.projectionService = projectionService;
        this.gameMapRepository = gameMapRepository;
        this.encounterRepository = encounterRepository;
        this.combatantRepository = combatantRepository;
        this.handoutRepository = handoutRepository;
        this.sessionRepository = sessionRepository;
        this.currentState = LiveTableState.curtain();
    }

    public void setOnStateChange(Runnable callback) {
        this.onStateChange = callback;
    }

    public LiveTableState getCurrentState() {
        return currentState;
    }

    public boolean isCurrentlyPresentedHandout(UUID handoutId) {
        return "HANDOUT".equals(currentState.mode()) && currentState.handout() != null
                && handoutId.toString().equals(currentState.handout().id());
    }

    public void curtainIfCurrentContent(UUID campaignId, UUID contentId) {
        if (!campaignId.equals(currentCampaignId)) return;
        boolean currentMap = "MAP".equals(currentState.mode()) && currentState.map() != null
                && contentId.toString().equals(currentState.map().mapId());
        boolean currentHandout = "HANDOUT".equals(currentState.mode()) && currentState.handout() != null
                && contentId.toString().equals(currentState.handout().id());
        if (currentMap || currentHandout) {
            currentAoEs = List.of();
            currentState = LiveTableState.curtain();
            broadcast();
        }
    }

    public void curtainIfCurrentCampaign(UUID campaignId) {
        if (!campaignId.equals(currentCampaignId)) return;
        currentAoEs = List.of();
        currentState = LiveTableState.curtain();
        broadcast();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPresentationInvalidated(PresentationInvalidated event) {
        if (event.wholeCampaign()) curtainIfCurrentCampaign(event.campaignId());
        else curtainIfCurrentContent(event.campaignId(), event.contentId());
    }

    public void updateAoEs(UUID campaignId, List<LiveTableState.AoeTemplateSnapshot> aoes) {
        if (campaignId.equals(currentCampaignId) && "MAP".equals(currentState.mode())) {
            this.currentAoEs = List.copyOf(aoes);
        }
    }

    @Transactional
    public LiveTableState presentMap(UUID campaignId, UUID mapId) {
        CampaignSession session = requireOpenSession(campaignId);
        GameMap map = gameMapRepository.findById(mapId)
                .filter(value -> value.getCampaign().getId().equals(campaignId))
                .orElseThrow(() -> new NotFoundException("Map not found in campaign"));
        session.setPresentationMode(CampaignSession.PresentationMode.MAP);
        session.setPresentedMap(map);
        session.setPresentedHandout(null);
        sessionRepository.saveAndFlush(session);
        if (!campaignId.equals(currentCampaignId) || currentState.map() == null
                || !mapId.toString().equals(currentState.map().mapId())) {
            currentAoEs = List.of();
        }
        currentCampaignId = campaignId;
        currentState = projectMap(map);
        broadcast();
        return currentState;
    }

    @Transactional
    public LiveTableState presentHandout(UUID campaignId, UUID handoutId) {
        CampaignSession session = requireOpenSession(campaignId);
        Handout handout = handoutRepository.findById(handoutId)
                .filter(value -> value.getCampaign().getId().equals(campaignId) && !value.isDmOnly())
                .orElseThrow(() -> new NotFoundException("Presentable handout not found in campaign"));
        session.setPresentationMode(CampaignSession.PresentationMode.HANDOUT);
        session.setPresentedMap(null);
        session.setPresentedHandout(handout);
        sessionRepository.saveAndFlush(session);
        currentCampaignId = campaignId;
        currentState = projectHandout(handout);
        broadcast();
        return currentState;
    }

    @Transactional
    public LiveTableState curtain(UUID campaignId) {
        CampaignSession session = requireOpenSession(campaignId);
        session.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
        session.setPresentedMap(null);
        session.setPresentedHandout(null);
        sessionRepository.saveAndFlush(session);
        currentCampaignId = campaignId;
        currentState = LiveTableState.curtain();
        broadcast();
        return currentState;
    }

    @Transactional
    public LiveTableState restorePresentation(UUID campaignId) {
        CampaignSession session = sessionRepository.findByCampaignId(campaignId).orElse(null);
        if (session == null || !session.isOpen()) {
            currentCampaignId = campaignId;
            currentState = LiveTableState.curtain();
            return currentState;
        }
        try {
            return switch (session.getPresentationMode()) {
                case MAP -> {
                    if (session.getPresentedMap() == null)
                        throw new IllegalStateException("Missing map");
                    currentCampaignId = campaignId;
                    currentState = projectMap(session.getPresentedMap());
                    broadcast();
                    yield currentState;
                }
                case HANDOUT -> {
                    if (session.getPresentedHandout() == null || session.getPresentedHandout().isDmOnly())
                        throw new IllegalStateException("Missing handout");
                    currentCampaignId = campaignId;
                    currentState = projectHandout(session.getPresentedHandout());
                    broadcast();
                    yield currentState;
                }
                case CURTAIN -> {
                    currentCampaignId = campaignId;
                    currentState = LiveTableState.curtain();
                    broadcast();
                    yield currentState;
                }
            };
        } catch (Exception e) {
            log.warn("Could not restore presentation for campaign {}", campaignId, e);
            session.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
            session.setPresentedMap(null);
            session.setPresentedHandout(null);
            sessionRepository.save(session);
            currentCampaignId = campaignId;
            currentState = LiveTableState.curtain();
            broadcast();
            return currentState;
        }
    }

    @Transactional
    public LiveTableState restoreLatestPresentation() {
        CampaignSession latest = sessionRepository
                .findFirstByStatusNotOrderByUpdatedAtDesc(CampaignSession.Status.IDLE)
                .orElse(null);
        if (latest == null) {
            currentState = LiveTableState.curtain();
            return currentState;
        }
        return restorePresentation(latest.getCampaign().getId());
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void restoreOnStartup() {
        restoreLatestPresentation();
    }

    @Transactional
    public LiveTableState broadcastCurrentState(UUID campaignId) {
        if (!campaignId.equals(currentCampaignId)) return currentState;
        return restorePresentation(campaignId);
    }

    private LiveTableState projectMap(GameMap gameMap) {
        var document = projectionService.projectMapDocument(gameMap);
        var tokens = projectionService.projectTokens(gameMap);
        UUID campaignId = gameMap.getCampaign().getId();
        var combatants = getActiveCombatants(campaignId);
        int activeTurnIndex = getActiveTurnIndex(campaignId);
        return LiveTableState.full("MAP",
                new LiveTableState.MapSnapshot(
                        gameMap.getId().toString(), gameMap.getName(),
                        gameMap.getGridWidth(), gameMap.getGridHeight(), gameMap.getCellSizePx(),
                        gameMap.getMovementMode(), gameMap.isShowGrid(),
                        document, tokens, currentAoEs
                ), null, combatants, activeTurnIndex);
    }

    private LiveTableState projectHandout(Handout handout) {
        return LiveTableState.full("HANDOUT", null,
                new LiveTableState.HandoutRef(handout.getId().toString(), handout.getTitle(), handout.getContentType()),
                null, null);
    }

    private CampaignSession requireOpenSession(UUID campaignId) {
        CampaignSession session = sessionRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalStateException("No session found for this campaign"));
        if (!session.isOpen())
            throw new IllegalStateException("The campaign session must be open to present content.");
        return session;
    }

    private List<LiveTableState.CombatantSnapshot> getActiveCombatants(UUID campaignId) {
        Optional<Encounter> active = encounterRepository.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE);
        if (active.isEmpty()) return List.of();
        Encounter enc = active.get();
        List<Combatant> combatants = combatantRepository.findByEncounterIdOrderBySortOrderAsc(enc.getId());
        return projectionService.projectCombatants(combatants, enc.getActiveTurnIndex());
    }

    private int getActiveTurnIndex(UUID campaignId) {
        Optional<Encounter> active = encounterRepository.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE);
        return active.map(Encounter::getActiveTurnIndex).orElse(-1);
    }

    private void broadcast() {
        if (onStateChange != null) {
            onStateChange.run();
        }
    }
}
