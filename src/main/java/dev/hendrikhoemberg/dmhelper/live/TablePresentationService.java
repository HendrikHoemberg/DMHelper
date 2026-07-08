package dev.hendrikhoemberg.dmhelper.live;

import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private volatile LiveTableState currentState;
    private Runnable onStateChange;

    private volatile List<LiveTableState.AoeTemplateSnapshot> currentAoEs = List.of();

    public TablePresentationService(PlayerSafeProjectionService projectionService,
                                    GameMapRepository gameMapRepository,
                                    EncounterRepository encounterRepository,
                                    CombatantRepository combatantRepository,
                                    HandoutRepository handoutRepository) {
        this.projectionService = projectionService;
        this.gameMapRepository = gameMapRepository;
        this.encounterRepository = encounterRepository;
        this.combatantRepository = combatantRepository;
        this.handoutRepository = handoutRepository;
        this.currentState = LiveTableState.curtain();
    }

    public void setOnStateChange(Runnable callback) {
        this.onStateChange = callback;
    }

    public LiveTableState getCurrentState() {
        return currentState;
    }

    public void updateAoEs(List<LiveTableState.AoeTemplateSnapshot> aoes) {
        this.currentAoEs = List.copyOf(aoes);
    }

    @Transactional(readOnly = true)
    public LiveTableState presentMap(UUID mapId) {
        GameMap gameMap = gameMapRepository.findById(mapId).orElse(null);
        if (gameMap == null) {
            log.warn("Map not found for presentation: {}", mapId);
            return currentState;
        }

        var document = projectionService.projectMapDocument(gameMap);
        var tokens = projectionService.projectTokens(gameMap);
        UUID campaignId = gameMap.getCampaign().getId();
        var combatants = getActiveCombatants(campaignId);
        int activeTurnIndex = getActiveTurnIndex(campaignId);

        currentState = LiveTableState.full("MAP",
                new LiveTableState.MapSnapshot(
                        gameMap.getId().toString(), gameMap.getName(),
                        gameMap.getGridWidth(), gameMap.getGridHeight(), gameMap.getCellSizePx(),
                        gameMap.getMovementMode(), gameMap.isShowGrid(),
                        document, tokens, currentAoEs
                ),
                null,
                combatants,
                activeTurnIndex
        );
        broadcast();
        return currentState;
    }

    @Transactional(readOnly = true)
    public LiveTableState presentHandout(UUID handoutId) {
        Handout handout = handoutRepository.findById(handoutId).orElse(null);
        if (handout == null) {
            log.warn("Handout not found for presentation: {}", handoutId);
            return currentState;
        }

        currentState = LiveTableState.full("HANDOUT", null,
                new LiveTableState.HandoutRef(handoutId.toString(), handout.getTitle(), handout.getContentType()),
                null, null);
        broadcast();
        return currentState;
    }

    public LiveTableState curtain() {
        currentState = LiveTableState.curtain();
        broadcast();
        return currentState;
    }

    @Transactional(readOnly = true)
    public LiveTableState broadcastCurrentState() {
        if (currentState == null || "CURTAIN".equals(currentState.mode())) {
            return currentState;
        }
        if ("MAP".equals(currentState.mode()) && currentState.map() != null) {
            UUID mapId = UUID.fromString(currentState.map().mapId());
            GameMap gameMap = gameMapRepository.findById(mapId).orElse(null);
            if (gameMap != null) {
                UUID campaignId = gameMap.getCampaign().getId();
                var tokens = projectionService.projectTokens(gameMap);
                var combatants = getActiveCombatants(campaignId);
                int activeTurnIndex = getActiveTurnIndex(campaignId);
                currentState = LiveTableState.full("MAP",
                        new LiveTableState.MapSnapshot(
                                currentState.map().mapId(), currentState.map().mapName(),
                                currentState.map().gridWidth(), currentState.map().gridHeight(),
                                currentState.map().cellSizePx(), currentState.map().movementMode(),
                                currentState.map().showGrid(),
                                currentState.map().document(), tokens,
                                currentAoEs
                        ),
                        null,
                        combatants,
                        activeTurnIndex
                );
                broadcast();
            }
        }
        return currentState;
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
