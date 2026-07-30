package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.audio.service.EncounterVictoryAudioRequested;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWave;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWaveRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveStatus;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveTriggerKind;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.service.CombatDifficultyCalculator.DifficultyResult;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapRuntimeChanged;

import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;

import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver;
import dev.hendrikhoemberg.dmhelper.threat.web.ThreatCardView;
import dev.hendrikhoemberg.dmhelper.threat.web.ThreatWebMapper;
import dev.hendrikhoemberg.dmhelper.treasury.data.InventoryState;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncounterService {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private static final Set<CombatLogEntry.EntryType> UNDO_BOUNDARIES = EnumSet.of(
            CombatLogEntry.EntryType.ENCOUNTER_ACTIVATED,
            CombatLogEntry.EntryType.ENCOUNTER_ENDED,
            CombatLogEntry.EntryType.SESSION_END,
            CombatLogEntry.EntryType.WAVE_SPAWNED,
            CombatLogEntry.EntryType.REWARD_APPLIED
    );

    private final EncounterRepository encounterRepo;
    private final CampaignRepository campaignRepo;
    private final EntityManager em;
    private final GameMapRepository mapRepo;
    private final CombatantRepository combatantRepo;
    private final CombatLogEntryRepository combatLogRepo;
    private final PartyMemberRepository partyRepo;
    private final StatBlockRepository statBlockRepo;
    private final EncounterWaveRepository waveRepo;
    private final CombatDifficultyCalculator calculator;
    private final DiceEngine diceEngine;
    private final SceneRefCleaner sceneRefCleaner;
    private final ObjectProvider<LedgerService> ledgerService;
    private final ObjectProvider<TreasuryService> treasuryService;
    private final ObjectProvider<CampaignPackageKeyService> packageKeyService;
    private final ObjectProvider<QuestObjectiveRepository> questObjectiveRepository;
    private final ThreatReferenceResolver threatReferenceResolver;
    private final TrapRepository trapRepository;
    private final HazardRepository hazardRepository;
    private final MarkdownUtil markdownUtil;
    private final ApplicationEventPublisher events;
    private final EncounterPlacementService placementService;

    public EncounterService(EncounterRepository encounterRepo, CampaignRepository campaignRepo,
                            EntityManager em, GameMapRepository mapRepo,
                            CombatantRepository combatantRepo, CombatLogEntryRepository combatLogRepo,
                            PartyMemberRepository partyRepo, StatBlockRepository statBlockRepo,
                            EncounterWaveRepository waveRepo,
                            CombatDifficultyCalculator calculator, DiceEngine diceEngine,
                            SceneRefCleaner sceneRefCleaner,
                            ObjectProvider<LedgerService> ledgerService,
                            ObjectProvider<TreasuryService> treasuryService,
                            ObjectProvider<CampaignPackageKeyService> packageKeyService,
                            ObjectProvider<QuestObjectiveRepository> questObjectiveRepository,
                            ThreatReferenceResolver threatReferenceResolver,
                            TrapRepository trapRepository,
                            HazardRepository hazardRepository,
                            MarkdownUtil markdownUtil,
                            ApplicationEventPublisher events,
                            EncounterPlacementService placementService) {
        this.encounterRepo = encounterRepo;
        this.campaignRepo = campaignRepo;
        this.em = em;
        this.mapRepo = mapRepo;
        this.combatantRepo = combatantRepo;
        this.combatLogRepo = combatLogRepo;
        this.partyRepo = partyRepo;
        this.statBlockRepo = statBlockRepo;
        this.waveRepo = waveRepo;
        this.calculator = calculator;
        this.diceEngine = diceEngine;
        this.sceneRefCleaner = sceneRefCleaner;
        this.ledgerService = ledgerService;
        this.treasuryService = treasuryService;
        this.packageKeyService = packageKeyService;
        this.questObjectiveRepository = questObjectiveRepository;
        this.threatReferenceResolver = threatReferenceResolver;
        this.trapRepository = trapRepository;
        this.hazardRepository = hazardRepository;
        this.markdownUtil = markdownUtil;
        this.events = events;
        this.placementService = placementService;
    }

    public record CreateRequest(String name, UUID mapId) {}

    public record AddFromLibraryRequest(UUID statBlockId, int quantity, String groupName,
                                         UUID waveId, Integer startX, Integer startY, String placementRegionKey) {}

    public record UpdateRequest(String name, UUID mapId, String status, String lairActionName,
                                String lairActionDescription) {}

    public record ConditionStateDto(String sourceKey, String name, String description,
                                    int durationRounds, boolean tickOnSourceTurn,
                                    int appliedInRound, UUID appliedByCombatantId) {}

    public record CombatantDto(UUID id, UUID encounterId, String name, Integer initiative,
                               int sortOrder, int currentHp, int maxHp, int tempHp,
                               String kind, String groupId, boolean groupLeader,
                               UUID tokenId, UUID placementId, UUID statBlockId, UUID partyMemberId,
                               boolean defeated, boolean hidden, boolean bloodied,
                               List<ConditionStateDto> conditions,
                               String concentratingOn, boolean concentrationCheckPending,
                               int legendaryActionsUsed, int legendaryActionsMax,
                               int legendaryResistancesUsed, int legendaryResistancesMax,
                               String notes,
                               UUID waveId, Integer startX, Integer startY, String placementRegionKey,
                               ThreatKind threatKind, UUID threatId, ThreatCardView threatCard,
                               boolean actsForGroup) {}

    public record StatblockRef(UUID statblockId, String name) {}

    public record CombatantCreateRequest(String name, int maxHp, String kind,
                                         UUID statBlockId, UUID partyMemberId) {}

    public record ThreatCombatantRequest(
            ThreatKind threatKind, UUID threatId, String name,
            Integer initiative, UUID waveId) {}

    public record CombatantUpdateRequest(String name, Integer initiative, Integer sortOrder,
                                         Integer currentHp, Integer maxHp, Integer tempHp,
                                         String kind, String groupId, Boolean groupLeader,
                                         Boolean defeated, Boolean hidden,
                                         String concentratingOn, Boolean concentrationCheckPending,
                                         Integer legendaryActionsUsed, Integer legendaryActionsMax,
                                         Integer legendaryResistancesUsed, Integer legendaryResistancesMax,
                                         String notes,
                                         UUID waveId, Integer startX, Integer startY, String placementRegionKey) {}

    public record WaveDto(UUID id, UUID encounterId, String waveKey, String name, int sortOrder,
            String status, String triggerKind, String triggerValue, String notes, int combatantCount) {}

    public record CreateWaveRequest(String waveKey, String name, WaveTriggerKind triggerKind,
            String triggerValue, String notes) {}

    public record UpdateWaveRequest(String name, WaveStatus status, WaveTriggerKind triggerKind,
            String triggerValue, String notes, Integer sortOrder) {}

    public record InitiativeRequest(Integer initiative) {}
    public record StartCombatRequest(boolean acceptUnset) {}

    public record ReorderRequest(List<UUID> orderedIds) {}

    public record ActiveTurnRequest(UUID combatantId) {}

    public record HpRequest(Integer currentHp, Integer tempHp) {}
    public record DamageRequest(int amount) {}
    public record DefeatedRequest(boolean defeated) {}
    public record ConditionToggleRequest(String sourceKey, int durationRounds) {}
    public record ConcentrationRequest(String spellName) {}
    public record ConcentrationCheckRequest(boolean passed) {}
    public record RechargePrompt(String abilityName, int minRoll, int maxRoll) {}
    public record RechargeCheckRequest(String abilityName, Integer rollResult) {}

    public record EncounterDto(UUID id, UUID campaignId, UUID mapId, String name, String status,
                               int round, int activeTurnIndex, String combatPhase, int combatantCount,
                               String lairActionName, String lairActionDescription,
                               boolean lairActionAvailable,
                               List<CombatantDto> combatants,
                               List<RechargePrompt> rechargePrompts) {}

    public record CombatLogEntryDto(UUID id, int round, long sequence, String type,
                                     String combatantId, String combatantName, String payload,
                                     Instant createdAt) {}

    public record EncounterSummaryDto(
            UUID encounterId,
            String name,
            int rounds,
            int combatantCount,
            int defeatedCount,
            int partyCasualtyCount,
            int totalDamageDealt,
            List<String> wavesSpawned,
            List<String> casualtyNames,
            EncounterRewards rewardsDraft,
            Instant endedAt
    ) {}

    public record EncounterEndResult(EncounterDto encounter, EncounterSummaryDto summary) {}

    public record ApplyRewardsRequest(
            boolean awardXp,
            List<UUID> partyMemberIds,
            boolean createLedger,
            boolean applyItems,
            boolean applyQuestObjectives
    ) {
        public ApplyRewardsRequest {
            if (partyMemberIds == null) partyMemberIds = List.of();
        }

        /** Back-compat: confirm-rewards UI historically only sent awardXp + partyMemberIds. */
        public ApplyRewardsRequest(boolean awardXp, List<UUID> partyMemberIds) {
            this(awardXp, partyMemberIds, true, true, true);
        }
    }

    static EncounterDto toDto(Encounter e) {
        return new EncounterDto(e.getId(), e.getCampaign().getId(),
                e.getMap() != null ? e.getMap().getId() : null,
                e.getName(), e.getStatus().name(), e.getRound(), e.getActiveTurnIndex(),
                e.getCombatPhase().name(),
                0,
                e.getLairActionName(), e.getLairActionDescription(),
                e.getLairActionName() != null && !e.isLairActionTriggered(),
                List.of(), List.of());
    }

    CombatantDto toDto(Combatant c) {
        return toDto(c, c.isGroupLeader());
    }

    CombatantDto toDto(Combatant c, boolean actsForGroup) {
        List<ConditionStateDto> conditions;
        try {
            conditions = JSON_MAPPER.readValue(c.getConditionsJson(),
                    new TypeReference<List<ConditionStateDto>>() {});
        } catch (Exception e) {
            conditions = List.of();
        }
        boolean bloodied = c.getCurrentHp() <= c.getMaxHp() / 2
                && c.getCurrentHp() > 0 && !c.isDefeated();
        UUID placementId = c.getPlacement() != null ? c.getPlacement().getId() : null;
        return new CombatantDto(c.getId(), c.getEncounter().getId(),
                c.getName(), c.getInitiative(), c.getSortOrder(),
                c.getCurrentHp(), c.getMaxHp(), c.getTempHp(),
                c.getKind(), c.getGroupId(), c.isGroupLeader(),
                null,
                placementId,
                c.getStatBlock() != null ? c.getStatBlock().getId() : null,
                c.getPartyMember() != null ? c.getPartyMember().getId() : null,
                c.isDefeated(), c.isHidden(), bloodied,
                conditions,
                c.getConcentratingOn(), c.isConcentrationCheckPending(),
                c.getLegendaryActionsUsed(), c.getLegendaryActionsMax(),
                c.getLegendaryResistancesUsed(), c.getLegendaryResistancesMax(),
                c.getNotes(),
                c.getWave() != null ? c.getWave().getId() : null,
                c.getStartX(), c.getStartY(), c.getPlacementRegionKey(),
                c.getThreatKind(), c.getThreatId(), resolveThreatCard(c),
                actsForGroup);
    }

    private ThreatCardView resolveThreatCard(Combatant c) {
        if (c.getThreatKind() == null || c.getThreatId() == null) {
            return null;
        }
        return switch (c.getThreatKind()) {
            case TRAP -> trapRepository.findDetailedById(c.getThreatId())
                    .map(t -> ThreatWebMapper.cardFromTrap(t, htmlDescription(t.getDescription())))
                    .orElse(null);
            case HAZARD -> hazardRepository.findDetailedById(c.getThreatId())
                    .map(h -> ThreatWebMapper.cardFromHazard(h, htmlDescription(h.getDescription())))
                    .orElse(null);
        };
    }

    private String htmlDescription(String markdown) {
        return markdown != null ? markdownUtil.toHtml(markdown) : "";
    }

    public EncounterDto create(UUID campaignId, CreateRequest req) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));
        Encounter e = new Encounter();
        e.setCampaign(campaign);
        e.setName(req.name());
        e.setCombatPhase(Encounter.CombatPhase.SETUP);
        if (req.mapId() != null) {
            GameMap map = mapRepo.findById(req.mapId())
                    .orElseThrow(() -> new NotFoundException("Map not found: " + req.mapId()));
            requireSameCampaign(campaign.getId(), map);
            e.setMap(map);
        }
        Encounter saved = encounterRepo.save(e);
        ensureMainWave(saved);
        return toDto(saved);
    }

    public EncounterDto update(UUID id, UpdateRequest req) {
        Encounter e = findEntityById(id);
        e.setName(req.name());
        if (req.mapId() != null) {
            GameMap map = mapRepo.findById(req.mapId())
                    .orElseThrow(() -> new NotFoundException("Map not found: " + req.mapId()));
            requireSameCampaign(e.getCampaign().getId(), map);
            e.setMap(map);
        } else {
            e.setMap(null);
        }
        if (req.status() != null) {
            Encounter.Status newStatus = Encounter.Status.valueOf(req.status());
            if (newStatus == Encounter.Status.ACTIVE && e.getStatus() != Encounter.Status.ACTIVE) {
                Optional<Encounter> active = encounterRepo.findByCampaignIdAndStatus(
                        e.getCampaign().getId(), Encounter.Status.ACTIVE);
                if (active.isPresent() && !active.get().getId().equals(id)) {
                    throw new IllegalStateException("Another encounter is already active");
                }
            }
            e.setStatus(newStatus);
        }
        e.setLairActionName(req.lairActionName());
        e.setLairActionDescription(req.lairActionDescription());
        return toDto(encounterRepo.save(e));
    }

    public void delete(UUID id) {
        sceneRefCleaner.detachEncounter(id);
        encounterRepo.delete(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public EncounterDto getById(UUID id) {
        return toDto(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<EncounterDto> list(UUID campaignId) {
        return encounterRepo.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .map(EncounterService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<EncounterDto> findActiveByCampaignId(UUID campaignId) {
        return encounterRepo.findByCampaignIdAndStatus(campaignId, Encounter.Status.ACTIVE)
                .map(EncounterService::toDto);
    }

    public EncounterDto activate(UUID id) {
        Encounter e = findEntityById(id);
        Optional<Encounter> active = encounterRepo.findByCampaignIdAndStatus(
                e.getCampaign().getId(), Encounter.Status.ACTIVE);
        if (active.isPresent() && !active.get().getId().equals(id)) {
            throw new IllegalStateException(
                    "Another encounter is already active; use the session activation workflow");
        }
        if (e.getStatus() == Encounter.Status.ACTIVE) {
            return toDto(e);
        }
        e.setStatus(Encounter.Status.ACTIVE);
        e.setCombatPhase(Encounter.CombatPhase.SETUP);
        e.setRound(0);
        e.setActiveTurnIndex(-1);
        Encounter saved = encounterRepo.save(e);
        placementService.autoPlaceUnplaced(id);
        EncounterDto dto = toDto(saved);
        logEntry(id, CombatLogEntry.EntryType.ENCOUNTER_ACTIVATED, "", "");
        return dto;
    }

    private static void requireSameCampaign(UUID campaignId, GameMap map) {
        if (!map.getCampaign().getId().equals(campaignId)) {
            throw new IllegalArgumentException("Map does not belong to encounter campaign");
        }
    }

    private void publishRuntimeChange(Combatant combatant) {
        GameMap map = combatant.getEncounter().getMap();
        if (map != null) {
            events.publishEvent(new MapRuntimeChanged(
                    combatant.getEncounter().getCampaign().getId(), map.getId()));
        }
    }

    @Transactional
    public EncounterDto resetEncounter(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        for (Combatant c : combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId)) {
            c.setCurrentHp(c.getMaxHp());
            c.setTempHp(0);
            c.setDefeated(false);
            c.setInitiative(null);
            c.setConditionsJson("[]");
            c.setConcentratingOn(null);
            combatantRepo.save(c);
        }
        e.setStatus(Encounter.Status.PLANNED);
        e.setCombatPhase(Encounter.CombatPhase.SETUP);
        e.setRound(0);
        e.setActiveTurnIndex(-1);
        return toDto(encounterRepo.save(e));
    }

    /**
     * Puts a finished encounter back into initiative setup. This deliberately mirrors the
     * reset performed by activate(UUID): old HP/initiative values remain editable, but stale
     * RUNNING/turn state cannot leak into the new run. The combat log is retained and a new
     * activation boundary separates the runs.
     */
    public Encounter reopen(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        if (e.getStatus() != Encounter.Status.DONE) {
            throw new IllegalStateException("Only finished encounters can be reopened");
        }
        e.setStatus(Encounter.Status.ACTIVE);
        e.setCombatPhase(Encounter.CombatPhase.SETUP);
        e.setRound(0);
        e.setActiveTurnIndex(-1);
        encounterRepo.save(e);
        placementService.autoPlaceUnplaced(encounterId);
        logEntry(encounterId, CombatLogEntry.EntryType.ENCOUNTER_ACTIVATED, "", "");
        return e;
    }

    public Encounter activateFresh(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        if (e.getStatus() != Encounter.Status.PLANNED) {
            throw new IllegalStateException("Only planned encounters can be activated fresh");
        }
        e.setStatus(Encounter.Status.ACTIVE);
        e.setCombatPhase(Encounter.CombatPhase.SETUP);
        e.setRound(0);
        e.setActiveTurnIndex(-1);
        encounterRepo.save(e);
        placementService.autoPlaceUnplaced(encounterId);
        return e;
    }

    public Encounter resume(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        if (e.getStatus() != Encounter.Status.SUSPENDED) {
            throw new IllegalStateException("Only suspended encounters can be resumed");
        }
        e.setStatus(Encounter.Status.ACTIVE);
        encounterRepo.save(e);
        return e;
    }

    public Encounter suspend(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        if (e.getStatus() != Encounter.Status.ACTIVE) {
            throw new IllegalStateException("Only active encounters can be suspended");
        }
        e.setStatus(Encounter.Status.SUSPENDED);
        encounterRepo.save(e);
        return e;
    }

    public EncounterDto endEncounter(UUID id) {
        Encounter e = findEntityById(id);
        if (e.getStatus() != Encounter.Status.ACTIVE && e.getStatus() != Encounter.Status.SUSPENDED) {
            throw new IllegalStateException("Only active or suspended encounters can be ended");
        }
        e.setStatus(Encounter.Status.DONE);
        EncounterDto dto = toDto(encounterRepo.save(e));
        logEntry(id, CombatLogEntry.EntryType.ENCOUNTER_ENDED, "", "");
        logEntry(id, CombatLogEntry.EntryType.SESSION_END, "",
                "{\"endedAt\":\"" + Instant.now().toString() + "\"}");
        if (e.getVictoryAudioCue() != null && e.getVictoryCueDurationSeconds() != null
                && e.getVictoryCueDurationSeconds() > 0) {
            events.publishEvent(new EncounterVictoryAudioRequested(
                    e.getCampaign().getId(), e.getId(), e.getName(),
                    e.getVictoryAudioCue().getId(), e.getVictoryCueDurationSeconds()));
        }
        return dto;
    }

    @Transactional
    public EncounterDto updatePrep(UUID id, EncounterPrep prep) {
        Encounter e = findEntityById(id);
        try {
            e.setPrepJson(JSON_MAPPER.writeValueAsString(prep));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid prep data", ex);
        }
        return toDto(e);
    }

    @Transactional
    public EncounterDto updateRewards(UUID id, EncounterRewards rewards) {
        Encounter e = findEntityById(id);
        try {
            e.setRewardsJson(JSON_MAPPER.writeValueAsString(rewards));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid rewards data", ex);
        }
        return toDto(e);
    }

    @Transactional(readOnly = true)
    public EncounterPrep getPrep(UUID id) {
        Encounter e = findEntityById(id);
        if (e.getPrepJson() == null || e.getPrepJson().isBlank()) return EncounterPrep.empty();
        try {
            return JSON_MAPPER.readValue(e.getPrepJson(), EncounterPrep.class);
        } catch (Exception ex) {
            return EncounterPrep.empty();
        }
    }

    @Transactional(readOnly = true)
    public EncounterRewards getRewards(UUID id) {
        Encounter e = findEntityById(id);
        if (e.getRewardsJson() == null || e.getRewardsJson().isBlank()) return EncounterRewards.empty();
        try {
            return JSON_MAPPER.readValue(e.getRewardsJson(), EncounterRewards.class);
        } catch (Exception ex) {
            return EncounterRewards.empty();
        }
    }

    @Transactional
    public EncounterEndResult endEncounterWithSummary(UUID encounterId) {
        EncounterDto enc = endEncounter(encounterId);
        EncounterSummaryDto summary = buildSummary(encounterId);
        return new EncounterEndResult(enc, summary);
    }

    public EncounterSummaryDto buildSummary(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        List<Combatant> all = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        List<CombatLogEntry> log = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId);

        int damage = log.stream()
                .filter(l -> l.getType() == CombatLogEntry.EntryType.DAMAGE)
                .mapToInt(l -> Math.abs(extractAmount(l.getPayload())))
                .sum();
        List<String> waves = log.stream()
                .filter(l -> l.getType() == CombatLogEntry.EntryType.WAVE_SPAWNED)
                .map(l -> extractWaveKey(l.getPayload()))
                .filter(Objects::nonNull)
                .toList();
        List<String> casualtyNames = all.stream()
                .filter(Combatant::isDefeated)
                .map(Combatant::getName)
                .toList();

        return new EncounterSummaryDto(
                e.getId(), e.getName(), e.getRound(),
                all.size(),
                (int) all.stream().filter(Combatant::isDefeated).count(),
                (int) all.stream().filter(c -> "PC".equals(c.getKind()) && c.isDefeated()).count(),
                damage,
                waves,
                casualtyNames,
                getRewards(encounterId),
                Instant.now()
        );
    }

    private String defeatedStatePayload(Combatant combatant) {
        try {
            return JSON_MAPPER.writeValueAsString(Map.of("name", combatant.getName()));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not record defeated-state evidence", failure);
        }
    }

    private int extractAmount(String payload) {
        try {
            return JSON_MAPPER.readTree(payload).get("amount").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractWaveKey(String payload) {
        try {
            return JSON_MAPPER.readTree(payload).get("waveKey").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void applyRewards(UUID encounterId, ApplyRewardsRequest req) {
        Encounter e = findEntityById(encounterId);
        List<CombatLogEntry> log = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId);
        boolean alreadyApplied = log.stream()
                .anyMatch(l -> l.getType() == CombatLogEntry.EntryType.REWARD_APPLIED);
        if (alreadyApplied) {
            throw new IllegalStateException("Rewards already applied");
        }

        EncounterRewards rewards = getRewards(encounterId);
        if (rewards == null) {
            rewards = EncounterRewards.empty();
        }

        UUID campaignId = e.getCampaign().getId();
        List<UUID> memberIds = req.partyMemberIds() != null && !req.partyMemberIds().isEmpty()
                ? req.partyMemberIds()
                : partyRepo.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId).stream()
                        .map(PartyMember::getId)
                        .toList();

        if (req.awardXp()) {
            Integer xpPool = rewards.xpTotal();
            Integer xpPerPc = rewards.xpPerPc();
            if ((xpPerPc != null && xpPerPc > 0) || (xpPool != null && xpPool > 0)) {
                if (memberIds.isEmpty()) {
                    throw new IllegalArgumentException("No party members selected to award XP");
                }
                int xpPerMember = xpPerPc != null && xpPerPc > 0
                        ? xpPerPc
                        : Math.max(1, xpPool / memberIds.size());
                for (UUID memberId : memberIds) {
                    PartyMember pm = partyRepo.findById(memberId).orElse(null);
                    if (pm != null) {
                        pm.setXp(pm.getXp() + xpPerMember);
                        partyRepo.save(pm);
                    }
                }
            }
        }

        if (req.createLedger() && rewards.currency() != null && !rewards.currency().isEmpty()) {
            LedgerService ledger = ledgerService.getIfAvailable();
            if (ledger != null) {
                for (EncounterRewards.CurrencyGrant grant : rewards.currency()) {
                    if (grant == null || grant.amount() <= 0) continue;
                    String currency = grant.currency() != null ? grant.currency() : "gp";
                    ledger.create(new LedgerService.CreateLedgerEntryRequest(
                            campaignId, "GOLD", "GAIN",
                            BigDecimal.valueOf(grant.amount()),
                            currency,
                            "Party",
                            "Encounter reward: " + e.getName(),
                            null, null, null));
                }
            }
        }

        if (req.applyItems() && rewards.items() != null && !rewards.items().isEmpty()) {
            TreasuryService treasury = treasuryService.getIfAvailable();
            if (treasury != null) {
                for (EncounterRewards.RewardItem item : rewards.items()) {
                    if (item == null || item.quantity() < 1) continue;
                    UUID magicId = resolveMagicItemId(treasury, campaignId, item.magicItemRef());
                    UUID equipId = resolveEquipmentItemId(treasury, campaignId, item.equipmentItemRef());
                    String custom = item.customText();
                    if (magicId == null && equipId == null && (custom == null || custom.isBlank())) {
                        continue;
                    }
                    treasury.create(new TreasuryService.CreateAssignmentRequest(
                            campaignId, null, magicId, equipId, custom, item.quantity(), false,
                            InventoryState.STASHED));
                }
            }
        }

        if (req.applyQuestObjectives() && rewards.questObjectiveRefs() != null
                && !rewards.questObjectiveRefs().isEmpty()) {
            applyQuestObjectiveRefs(campaignId, rewards.questObjectiveRefs());
        }

        try {
            logEntry(encounterId, CombatLogEntry.EntryType.REWARD_APPLIED, "",
                    JSON_MAPPER.writeValueAsString(Map.of(
                            "xpAwarded", req.awardXp(),
                            "ledger", req.createLedger(),
                            "items", req.applyItems(),
                            "quests", req.applyQuestObjectives())));
        } catch (Exception ex) {
            logEntry(encounterId, CombatLogEntry.EntryType.REWARD_APPLIED, "", "{}");
        }
    }

    private UUID resolveMagicItemId(TreasuryService treasury, UUID campaignId, ContentReference ref) {
        if (ref == null) return null;
        String sourceKey = ref.sourceKey() != null ? ref.sourceKey() : ref.key();
        if (sourceKey == null || sourceKey.isBlank()) return null;
        return treasury.resolveMagicItemForCampaign(campaignId, sourceKey)
                .map(MagicItem::getId)
                .orElse(null);
    }

    private UUID resolveEquipmentItemId(TreasuryService treasury, UUID campaignId, ContentReference ref) {
        if (ref == null) return null;
        String sourceKey = ref.sourceKey() != null ? ref.sourceKey() : ref.key();
        if (sourceKey == null || sourceKey.isBlank()) return null;
        return treasury.resolveEquipmentItemForCampaign(campaignId, sourceKey)
                .map(EquipmentItem::getId)
                .orElse(null);
    }

    private void applyQuestObjectiveRefs(UUID campaignId, List<ContentReference> refs) {
        CampaignPackageKeyService keys = packageKeyService.getIfAvailable();
        QuestObjectiveRepository objectives = questObjectiveRepository.getIfAvailable();
        if (keys == null || objectives == null) return;
        for (ContentReference ref : refs) {
            if (ref == null || ref.key() == null || ref.key().isBlank()) continue;
            Optional<UUID> entityId = keys.findEntityId(campaignId, CampaignContentType.OBJECTIVE, ref.key());
            if (entityId.isEmpty()) continue;
            objectives.findById(entityId.get()).ifPresent(objective -> {
                if (objective.getQuest() != null
                        && objective.getQuest().getCampaign() != null
                        && campaignId.equals(objective.getQuest().getCampaign().getId())
                        && objective.getStatus() != QuestObjectiveStatus.COMPLETED) {
                    objective.setStatus(QuestObjectiveStatus.COMPLETED);
                    objectives.save(objective);
                }
            });
        }
    }

    public CombatantDto addThreatCombatant(UUID encounterId, ThreatCombatantRequest req) {
        if (req == null || req.threatKind() == null || req.threatId() == null) {
            throw new IllegalArgumentException("threatKind and threatId are required");
        }
        Encounter e = findEntityById(encounterId);
        UUID campaignId = e.getCampaign().getId();
        Object resolved = threatReferenceResolver.requireVisible(
                req.threatKind(), req.threatId(), campaignId);

        String name = req.name();
        if (name == null || name.isBlank()) {
            name = switch (resolved) {
                case Trap t -> t.getName();
                case Hazard h -> h.getName();
                default -> req.threatKind().name();
            };
        }

        Combatant c = new Combatant();
        c.setEncounter(e);
        c.setName(name.trim());
        c.setKind(req.threatKind().name());
        c.setMaxHp(0);
        c.setCurrentHp(0);
        c.setTempHp(0);
        c.setThreatKind(req.threatKind());
        c.setThreatId(req.threatId());
        if (req.initiative() != null) {
            c.setInitiative(req.initiative());
        }
        if (req.waveId() != null) {
            EncounterWave wave = waveRepo.findById(req.waveId())
                    .orElseThrow(() -> new NotFoundException("Wave not found: " + req.waveId()));
            if (!wave.getEncounter().getId().equals(encounterId)) {
                throw new IllegalArgumentException("Wave does not belong to this encounter");
            }
            c.setWave(wave);
        }
        c.setSortOrder((int) combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).size());

        Combatant saved = combatantRepo.save(c);
        try {
            ObjectNode payload = JSON_MAPPER.createObjectNode();
            payload.put("name", saved.getName());
            if (saved.getInitiative() == null) payload.putNull("initiative");
            else payload.put("initiative", saved.getInitiative());
            payload.put("maxHp", saved.getMaxHp());
            payload.put("kind", saved.getKind());
            payload.put("threatKind", saved.getThreatKind().name());
            payload.put("threatId", saved.getThreatId().toString());
            logEntry(encounterId, CombatLogEntry.EntryType.COMBATANT_ADDED,
                    saved.getId().toString(), JSON_MAPPER.writeValueAsString(payload));
        } catch (Exception ex) { /* ignore */ }
        if (req.initiative() != null) {
            resortCombatants(encounterId);
        }
        return toDto(saved);
    }

    public CombatantDto addCombatant(UUID encounterId, CombatantCreateRequest req) {
        Encounter e = findEntityById(encounterId);
        Combatant c = new Combatant();
        c.setEncounter(e);

        String name = req.name();
        int maxHp = req.maxHp();
        String kind = req.kind() != null ? req.kind() : "NPC";
        int currentHp = maxHp;

        if (req.statBlockId() != null) {
            StatBlock sb = statBlockRepo.findById(req.statBlockId())
                    .orElseThrow(() -> new NotFoundException("StatBlock not found: " + req.statBlockId()));
            // Prefer caller-supplied display names (library multi-add uses "Goblin 1", "Goblin 2", …).
            if (name == null || name.isBlank()) {
                name = sb.getName();
            }
            if (kind == null || kind.isBlank() || "NPC".equals(kind)) {
                kind = "MONSTER";
            }
            if (maxHp <= 0) {
                maxHp = parseHpAsInt(sb);
                currentHp = maxHp;
            }
            c.setStatBlock(sb);
        } else if (req.partyMemberId() != null) {
            if (combatantRepo.findByEncounterIdAndPartyMemberId(encounterId, req.partyMemberId()).isPresent()) {
                throw new IllegalArgumentException("Party member already has a combatant in this encounter");
            }
            PartyMember pm = partyRepo.findById(req.partyMemberId())
                    .orElseThrow(() -> new NotFoundException("Party member not found: " + req.partyMemberId()));
            name = pm.getCharacterName();
            maxHp = pm.getMaxHp();
            currentHp = pm.getCurrentHp();
            kind = "PC";
            c.setPartyMember(pm);
        }

        if ("HAZARD".equals(kind) && maxHp == 10) {
            maxHp = 1;
            currentHp = 1;
        }

        c.setName(name);
        c.setKind(kind);
        c.setMaxHp(maxHp);
        c.setCurrentHp(currentHp);
        c.setSortOrder((int) combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).size());

        Combatant saved = combatantRepo.save(c);
        try {
            ObjectNode payload = JSON_MAPPER.createObjectNode();
            payload.put("name", saved.getName());
            if (saved.getInitiative() == null) payload.putNull("initiative");
            else payload.put("initiative", saved.getInitiative());
            payload.put("maxHp", saved.getMaxHp());
            payload.put("kind", saved.getKind());
            logEntry(encounterId, CombatLogEntry.EntryType.COMBATANT_ADDED,
                saved.getId().toString(), JSON_MAPPER.writeValueAsString(payload));
        } catch (Exception ex2) { /* ignore */ }
        return toDto(saved);
    }

    @Transactional
    public List<CombatantDto> addFromLibrary(UUID encounterId, AddFromLibraryRequest req) {
        if (req.quantity() < 1 || req.quantity() > 50) {
            throw new IllegalArgumentException("quantity must be 1..50");
        }
        Encounter e = findEntityById(encounterId);
        StatBlock sb = statBlockRepo.findById(req.statBlockId())
                .orElseThrow(() -> new NotFoundException("StatBlock not found: " + req.statBlockId()));
        EncounterWave wave = req.waveId() != null
                ? waveRepo.findById(req.waveId()).orElseThrow(() -> new NotFoundException("Wave not found: " + req.waveId()))
                : ensureMainWave(e);
        String groupId = UUID.randomUUID().toString();
        String baseName = req.groupName() != null && !req.groupName().isBlank()
                ? req.groupName() : sb.getName();
        List<CombatantDto> out = new ArrayList<>();
        int hp = parseHpAsInt(sb);
        for (int i = 0; i < req.quantity(); i++) {
            CombatantCreateRequest one = new CombatantCreateRequest(
                    req.quantity() == 1 ? baseName : baseName + " " + (i + 1),
                    hp > 0 ? hp : 10,
                    "MONSTER",
                    sb.getId(),
                    null);
            CombatantDto dto = addCombatant(encounterId, one);
            dto = updateCombatant(dto.id(), new CombatantUpdateRequest(
                    null, null, null, null, null, null,
                    null, groupId, i == 0, null, null, null, null, null, null, null, null, null,
                    wave.getId(), req.startX(), req.startY(), req.placementRegionKey()));
            if (req.startX() != null && req.startY() != null) {
                placementService.upsert(encounterId, dto.id(),
                        new EncounterPlacementService.PlacementUpsertRequest(
                                req.startX(), req.startY(), 1, 1,
                                EncounterPlacementService.defaultColor(dto.kind()), null));
            }
            out.add(dto);
        }
        return out;
    }

    public void removeCombatant(UUID combatantId) {
        Combatant c = findCombatantById(combatantId);
        try {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("name", c.getName());
            data.put("initiative", c.getInitiative());
            data.put("maxHp", c.getMaxHp());
            data.put("currentHp", c.getCurrentHp());
            data.put("tempHp", c.getTempHp());
            data.put("kind", c.getKind());
            data.put("sortOrder", c.getSortOrder());
            data.put("defeated", c.isDefeated());
            data.put("hidden", c.isHidden());
            data.put("conditionsJson", c.getConditionsJson());
            data.put("concentratingOn", c.getConcentratingOn() != null ? c.getConcentratingOn() : "");
            data.put("groupLeader", c.isGroupLeader());
            String payload = JSON_MAPPER.writeValueAsString(data);
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.COMBATANT_REMOVED,
                combatantId.toString(), payload);
        } catch (Exception ex) { /* ignore */ }
        c.setDefeated(true);
        c.setHidden(true);
        combatantRepo.save(c);
    }

    public CombatantDto updateCombatant(UUID id, CombatantUpdateRequest req) {
        Combatant c = findCombatantById(id);
        if (req.name() != null) c.setName(req.name());
        if (req.initiative() != null) c.setInitiative(req.initiative());
        if (req.sortOrder() != null) c.setSortOrder(req.sortOrder());
        if (req.currentHp() != null) c.setCurrentHp(req.currentHp());
        if (req.maxHp() != null) c.setMaxHp(req.maxHp());
        if (req.tempHp() != null) c.setTempHp(req.tempHp());
        if (req.kind() != null) {
            if (c.getThreatKind() != null && !c.getThreatKind().name().equals(req.kind())) {
                throw new IllegalArgumentException(
                        "Cannot change kind of threat combatant; expected " + c.getThreatKind().name());
            }
            c.setKind(req.kind());
        }
        if (req.groupId() != null) c.setGroupId(req.groupId());
        if (req.groupLeader() != null) c.setGroupLeader(req.groupLeader());
        if (req.defeated() != null) c.setDefeated(req.defeated());
        if (req.hidden() != null) c.setHidden(req.hidden());
        if (req.concentratingOn() != null) c.setConcentratingOn(req.concentratingOn());
        if (req.concentrationCheckPending() != null) c.setConcentrationCheckPending(req.concentrationCheckPending());
        if (req.legendaryActionsUsed() != null) c.setLegendaryActionsUsed(req.legendaryActionsUsed());
        if (req.legendaryActionsMax() != null) c.setLegendaryActionsMax(req.legendaryActionsMax());
        if (req.legendaryResistancesUsed() != null) c.setLegendaryResistancesUsed(req.legendaryResistancesUsed());
        if (req.legendaryResistancesMax() != null) c.setLegendaryResistancesMax(req.legendaryResistancesMax());
        if (req.notes() != null) c.setNotes(req.notes());
        if (req.waveId() != null) {
            EncounterWave wave = waveRepo.findById(req.waveId())
                    .orElseThrow(() -> new NotFoundException("Wave not found: " + req.waveId()));
            c.setWave(wave);
        }
        if (req.startX() != null) c.setStartX(req.startX());
        if (req.startY() != null) c.setStartY(req.startY());
        if (req.placementRegionKey() != null) c.setPlacementRegionKey(req.placementRegionKey());
        Combatant saved = combatantRepo.save(c);
        syncCombatantToPartyMember(saved);
        publishRuntimeChange(saved);
        return toDto(saved);
    }

    public List<CombatantDto> prefillFromParty(UUID encounterId, UUID campaignId) {
        Encounter e = findEntityById(encounterId);
        List<PartyMember> members = partyRepo.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId);
        for (PartyMember pm : members) {
            if (combatantRepo.findByEncounterIdAndPartyMemberId(encounterId, pm.getId()).isPresent()) {
                continue;
            }
            Combatant c = new Combatant();
            c.setEncounter(e);
            c.setName(pm.getCharacterName());
            c.setKind("PC");
            c.setMaxHp(pm.getMaxHp());
            c.setCurrentHp(pm.getCurrentHp());
            c.setTempHp(pm.getTempHp());
            if (pm.getConditionsJson() != null && !pm.getConditionsJson().isBlank()) {
                c.setConditionsJson(pm.getConditionsJson());
            }
            c.setConcentratingOn(pm.getConcentratingOn());
            c.setPartyMember(pm);
            c.setSortOrder((int) combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).size());
            combatantRepo.save(c);
        }
        return getCombatants(encounterId);
    }

    public List<EncounterPlacementService.PlacementDto> placeMissingParty(UUID encounterId) {
        Encounter encounter = findEntityById(encounterId);
        prefillFromParty(encounterId, encounter.getCampaign().getId());
        return placementService.placeUnplacedPartyCombatants(encounterId);
    }

    public CombatantDto splitGroupMember(UUID combatantId) {
        Combatant c = findCombatantById(combatantId);
        c.setGroupId(null);
        return toDto(combatantRepo.save(c));
    }

    @Transactional(readOnly = true)
    public List<CombatantDto> getCombatants(UUID encounterId) {
        List<Combatant> all = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
                .filter(this::isOnActiveWave)
                .toList();
        Map<UUID, Boolean> actsForGroup = all.stream()
                .collect(Collectors.toMap(Combatant::getId, c -> takesTurn(c, all)));
        return all.stream()
                .map(c -> toDto(c, actsForGroup.get(c.getId())))
                .toList();
    }

    public StatblockRef getCombatantStatblock(UUID combatantId) {
        Combatant c = combatantRepo.findById(combatantId)
                .orElseThrow(() -> new NotFoundException("Combatant not found: " + combatantId));
        StatBlock sb = c.getStatBlock();
        if (sb == null) return null;
        return new StatblockRef(sb.getId(), sb.getName());
    }

    private boolean isOnActiveWave(Combatant c) {
        if (c.getWave() == null) return true;
        return c.getWave().getStatus() == WaveStatus.ACTIVE;
    }

    private List<Combatant> getActiveCombatants(UUID encounterId) {
        return combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
                .filter(this::isOnActiveWave)
                .toList();
    }

    @Transactional(readOnly = true)
    public CombatantDto getCombatant(UUID combatantId) {
        return toDto(findCombatantById(combatantId));
    }

    public CombatantDto setInitiative(UUID combatantId, Integer initiative) {
        Combatant c = findCombatantById(combatantId);
        UUID encounterId = c.getEncounter().getId();
        lockEncounter(encounterId);
        em.refresh(c);
        Integer previous = c.getInitiative();
        c.setInitiative(initiative);
        Combatant saved = combatantRepo.save(c);

        try {
            ObjectNode payload = JSON_MAPPER.createObjectNode();
            if (initiative == null) payload.putNull("initiative");
            else payload.put("initiative", initiative);
            if (previous == null) payload.putNull("previousInitiative");
            else payload.put("previousInitiative", previous);
            logEntry(encounterId, CombatLogEntry.EntryType.INITIATIVE_SET,
                    combatantId.toString(), JSON_MAPPER.writeValueAsString(payload));
        } catch (Exception e) { /* log failure is non-fatal */ }

        resortCombatants(encounterId);

        return toDto(saved);
    }

    private Encounter lockEncounter(UUID encounterId) {
        return encounterRepo.findByIdForUpdate(encounterId)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
    }


    static int dexModifier(StatBlock sb) {
        return Math.floorDiv(sb.getDexScore() - 10, 2);
    }

    private EncounterWave ensureMainWave(Encounter e) {
        return waveRepo.findByEncounterIdAndWaveKey(e.getId(), "main")
                .orElseGet(() -> {
                    EncounterWave w = new EncounterWave();
                    w.setEncounter(e);
                    w.setWaveKey("main");
                    w.setName("Main");
                    w.setSortOrder(0);
                    w.setStatus(WaveStatus.ACTIVE);
                    w.setTriggerKind(WaveTriggerKind.MANUAL);
                    return waveRepo.save(w);
                });
    }

    private WaveDto waveToDto(EncounterWave w) {
        int count = (int) combatantRepo.countByWaveId(w.getId());
        return new WaveDto(w.getId(), w.getEncounter().getId(), w.getWaveKey(), w.getName(),
                w.getSortOrder(), w.getStatus().name(), w.getTriggerKind().name(),
                w.getTriggerValue(), w.getNotes(), count);
    }

    @Transactional(readOnly = true)
    public List<WaveDto> listWaves(UUID encounterId) {
        return waveRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
                .map(this::waveToDto)
                .toList();
    }

    @Transactional
    public WaveDto createWave(UUID encounterId, CreateWaveRequest req) {
        Encounter e = findEntityById(encounterId);
        if (waveRepo.findByEncounterIdAndWaveKey(e.getId(), req.waveKey()).isPresent()) {
            throw new IllegalArgumentException("Wave key '" + req.waveKey() + "' already exists in this encounter");
        }
        int maxSort = waveRepo.findByEncounterIdOrderBySortOrderAsc(e.getId()).stream()
                .mapToInt(EncounterWave::getSortOrder).max().orElse(-1);
        EncounterWave w = new EncounterWave();
        w.setEncounter(e);
        w.setWaveKey(req.waveKey());
        w.setName(req.name());
        w.setSortOrder(maxSort + 1);
        w.setStatus(WaveStatus.PENDING);
        w.setTriggerKind(req.triggerKind());
        w.setTriggerValue(req.triggerValue());
        w.setNotes(req.notes());
        w = waveRepo.save(w);
        return waveToDto(w);
    }

    @Transactional
    public WaveDto updateWave(UUID waveId, UpdateWaveRequest req) {
        EncounterWave w = waveRepo.findById(waveId)
                .orElseThrow(() -> new NotFoundException("Wave not found: " + waveId));
        if (req.name() != null) w.setName(req.name());
        if (req.status() != null) w.setStatus(req.status());
        if (req.triggerKind() != null) w.setTriggerKind(req.triggerKind());
        if (req.triggerValue() != null) w.setTriggerValue(req.triggerValue());
        if (req.notes() != null) w.setNotes(req.notes());
        if (req.sortOrder() != null) w.setSortOrder(req.sortOrder());
        w = waveRepo.save(w);
        return waveToDto(w);
    }

    @Transactional
    public void deleteWave(UUID waveId) {
        EncounterWave w = waveRepo.findById(waveId)
                .orElseThrow(() -> new NotFoundException("Wave not found: " + waveId));
        if ("main".equals(w.getWaveKey())) {
            throw new IllegalArgumentException("Cannot delete the main wave");
        }
        EncounterWave main = waveRepo.findByEncounterIdAndWaveKey(w.getEncounter().getId(), "main")
                .orElseThrow();
        List<Combatant> combatants = combatantRepo.findByWaveId(waveId);
        for (Combatant c : combatants) {
            c.setWave(main);
            combatantRepo.save(c);
        }
        waveRepo.delete(w);
    }

    @Transactional
    public EncounterDto spawnWave(UUID encounterId, UUID waveId) {
        Encounter e = findEntityById(encounterId);
        if (e.getStatus() != Encounter.Status.ACTIVE) {
            throw new IllegalStateException("Encounter must be ACTIVE to spawn a wave");
        }
        UUID activeCombatantId = activeCombatantId(e, getActiveCombatants(encounterId));
        EncounterWave wave = waveRepo.findById(waveId)
                .orElseThrow(() -> new NotFoundException("Wave not found: " + waveId));
        if (!wave.getEncounter().getId().equals(encounterId)) {
            throw new IllegalArgumentException("Wave does not belong to encounter");
        }
        if (wave.getStatus() == WaveStatus.ACTIVE || wave.getStatus() == WaveStatus.DEPLETED) {
            throw new IllegalStateException("Wave is already " + wave.getStatus());
        }
        wave.setStatus(WaveStatus.ACTIVE);
        waveRepo.save(wave);
        placementService.autoPlaceUnplaced(encounterId);
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of("waveKey", wave.getWaveKey()));
            logEntry(encounterId, CombatLogEntry.EntryType.WAVE_SPAWNED, "", payload);
        } catch (Exception ex) {
            logEntry(encounterId, CombatLogEntry.EntryType.WAVE_SPAWNED, "", "{}");
        }
        resortCombatants(encounterId, activeCombatantId);
        return toDto(e);
    }

    private static int parseHpAsInt(StatBlock sb) {
        String hp = sb.getHp();
        if (hp == null) return 10;
        String[] parts = hp.split(" ");
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public CombatantDto applyDamage(UUID combatantId, int amount) {
        Combatant c = findCombatantById(combatantId);
        if (amount < 0) {
            int damage = -amount;
            int remainingDamage = damage - c.getTempHp();
            c.setTempHp(Math.max(0, c.getTempHp() - damage));
            if (remainingDamage > 0) {
                c.setCurrentHp(c.getCurrentHp() - remainingDamage);
            }
        } else if (amount > 0) {
            c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + amount));
        }
        if (c.getCurrentHp() <= 0 && !"PC".equals(c.getKind()) && !c.isDefeated()) {
            c.setDefeated(true);
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.DEFEATED,
                combatantId.toString(), defeatedStatePayload(c));
        }
        if (amount < 0 && c.getConcentratingOn() != null && !c.getConcentratingOn().isEmpty()) {
            c.setConcentrationCheckPending(true);
            int dc = Math.max(10, (-amount) / 2);
            try {
                String payload = JSON_MAPPER.writeValueAsString(Map.of("dc", dc, "amount", -amount));
                logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.CONCENTRATION_CHECK,
                    combatantId.toString(), payload);
            } catch (Exception e) { /* ignore */ }
        }
        Combatant saved = combatantRepo.save(c);
        syncCombatantToPartyMember(saved);
        publishRuntimeChange(saved);
        CombatLogEntry.EntryType type = amount < 0 ? CombatLogEntry.EntryType.DAMAGE : CombatLogEntry.EntryType.HEAL;
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of("amount", amount));
            logEntry(c.getEncounter().getId(), type, combatantId.toString(), payload);
        } catch (Exception e) { /* ignore */ }
        return toDto(saved);
    }

    public CombatantDto setHp(UUID combatantId, Integer currentHp, Integer tempHp) {
        Combatant c = findCombatantById(combatantId);
        if (currentHp != null) c.setCurrentHp(Math.max(0, Math.min(c.getMaxHp(), currentHp)));
        if (tempHp != null) c.setTempHp(Math.max(0, tempHp));
        if (c.getCurrentHp() <= 0 && !"PC".equals(c.getKind()) && !c.isDefeated()) {
            c.setDefeated(true);
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.DEFEATED,
                combatantId.toString(), defeatedStatePayload(c));
        }
        Combatant saved = combatantRepo.save(c);
        syncCombatantToPartyMember(saved);
        publishRuntimeChange(saved);
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of(
                "currentHp", saved.getCurrentHp(),
                "tempHp", saved.getTempHp()));
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.SET_HP,
                combatantId.toString(), payload);
        } catch (Exception e) { /* ignore */ }
        return toDto(saved);
    }

    public CombatantDto markDefeated(UUID combatantId, boolean defeated) {
        Combatant c = findCombatantById(combatantId);
        c.setDefeated(defeated);
        Combatant saved = combatantRepo.save(c);
        publishRuntimeChange(saved);
        CombatLogEntry.EntryType type = defeated ? CombatLogEntry.EntryType.DEFEATED : CombatLogEntry.EntryType.REVIVED;
        logEntry(c.getEncounter().getId(), type, combatantId.toString(), defeatedStatePayload(c));
        return toDto(saved);
    }

    public CombatantDto toggleCondition(UUID combatantId, String sourceKey, int durationRounds) {
        Combatant c = findCombatantById(combatantId);
        List<ConditionStateDto> conditions = parseConditions(c);
        boolean removed = conditions.removeIf(cond -> cond.sourceKey().equals(sourceKey));
        if (!removed) {
            conditions.add(new ConditionStateDto(sourceKey, sourceKey, "", durationRounds, true,
                c.getEncounter().getRound(), null));
        }
        try {
            c.setConditionsJson(JSON_MAPPER.writeValueAsString(conditions));
        } catch (Exception e) { /* ignore */ }
        Combatant saved = combatantRepo.save(c);
        syncCombatantToPartyMember(saved);
        publishRuntimeChange(saved);
        CombatLogEntry.EntryType type = removed ? CombatLogEntry.EntryType.CONDITION_REMOVED : CombatLogEntry.EntryType.CONDITION_ADDED;
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of("sourceKey", sourceKey, "durationRounds", durationRounds));
            logEntry(c.getEncounter().getId(), type, combatantId.toString(), payload);
        } catch (Exception e) { /* ignore */ }
        return toDto(saved);
    }

    public void tickConditionDurations(UUID encounterId) {
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        Encounter encounter = findEntityById(encounterId);
        for (Combatant c : combatants) {
            List<ConditionStateDto> conditions = parseConditions(c);
            List<String> expiredKeys = new ArrayList<>();
            List<ConditionStateDto> updated = conditions.stream()
                .filter(cond -> {
                    if (cond.durationRounds() <= 0) return true;
                    int elapsed = encounter.getRound() - cond.appliedInRound();
                    boolean expired = elapsed >= cond.durationRounds();
                    if (expired) expiredKeys.add(cond.sourceKey());
                    return !expired;
                })
                .collect(Collectors.toList());
            if (updated.size() != conditions.size()) {
                try {
                    c.setConditionsJson(JSON_MAPPER.writeValueAsString(updated));
                } catch (Exception e) { /* ignore */ }
                combatantRepo.save(c);
                syncCombatantToPartyMember(c);
                publishRuntimeChange(c);
                try {
                    String payload = JSON_MAPPER.writeValueAsString(Map.of(
                        "expiredKeys", expiredKeys,
                        "remaining", updated.size()));
                    logEntry(encounterId, CombatLogEntry.EntryType.CONDITION_TICKED,
                        c.getId().toString(), payload);
                } catch (Exception e) { /* ignore */ }
            }
        }
    }

    public CombatantDto removeCondition(UUID combatantId, String sourceKey) {
        Combatant c = findCombatantById(combatantId);
        List<ConditionStateDto> conditions = parseConditions(c);
        conditions.removeIf(cond -> cond.sourceKey().equals(sourceKey));
        try {
            c.setConditionsJson(JSON_MAPPER.writeValueAsString(conditions));
        } catch (Exception e) { /* ignore */ }
        Combatant saved = combatantRepo.save(c);
        syncCombatantToPartyMember(saved);
        publishRuntimeChange(saved);
        logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.CONDITION_REMOVED,
            combatantId.toString(), "{\"sourceKey\":\"" + sourceKey + "\"}");
        return toDto(saved);
    }

    private List<ConditionStateDto> parseConditions(Combatant c) {
        try {
            return JSON_MAPPER.readValue(c.getConditionsJson(),
                new TypeReference<List<ConditionStateDto>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<CombatantDto> reorderCombatants(UUID encounterId, List<UUID> orderedIds) {
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        Encounter encounter = findEntityById(encounterId);
        UUID activeId = activeCombatantId(encounter, getActiveCombatants(encounterId));
        var encounterIds = combatants.stream().map(Combatant::getId).collect(Collectors.toSet());
        if (orderedIds.size() != encounterIds.size() || !encounterIds.containsAll(orderedIds)) {
            throw new IllegalArgumentException("orderedIds must contain exactly the encounter's combatants");
        }
        Map<UUID, Combatant> byId = combatants.stream()
                .collect(Collectors.toMap(Combatant::getId, combatant -> combatant));
        List<Combatant> reordered = new ArrayList<>(combatants.size());
        for (int i = 0; i < orderedIds.size(); i++) {
            Combatant c = byId.get(orderedIds.get(i));
            c.setSortOrder(i);
            reordered.add(c);
        }
        combatantRepo.saveAll(reordered);
        restoreActiveCombatantIndex(encounter, activeId, reordered);
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of("orderedIds", orderedIds));
            logEntry(encounterId, CombatLogEntry.EntryType.COMBATANT_REORDERED, "", payload);
        } catch (Exception e) { /* ignore */ }
        return getCombatants(encounterId);
    }

    private void resortCombatants(UUID encounterId) {
        Encounter encounter = findEntityById(encounterId);
        UUID activeId = activeCombatantId(encounter, getActiveCombatants(encounterId));
        resortCombatants(encounterId, activeId);
    }

    private void resortCombatants(UUID encounterId, UUID activeId) {
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        Encounter encounter = findEntityById(encounterId);
        boolean running = encounter.getCombatPhase() == Encounter.CombatPhase.RUNNING;
        combatants.sort(Comparator
                .comparing(Combatant::getInitiative, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparing(Combatant::getTieBreaker).reversed())
                .thenComparingInt(Combatant::getSortOrder)
                .thenComparing(Combatant::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < combatants.size(); i++) {
            combatants.get(i).setSortOrder(i);
            combatantRepo.save(combatants.get(i));
        }
        if (running) {
            restoreActiveCombatantIndex(encounter, activeId, combatants);
        }
        try {
            Map<String, Integer> orderMap = new HashMap<>();
            for (Combatant c : combatants) {
                orderMap.put(c.getId().toString(), c.getSortOrder());
            }
            logEntry(encounterId, CombatLogEntry.EntryType.SORT_ORDER, "",
                    JSON_MAPPER.writeValueAsString(orderMap));
        } catch (Exception e) { /* ignore */ }
    }

    private UUID activeCombatantId(Encounter encounter, List<Combatant> activeCombatants) {
        if (encounter.getCombatPhase() != Encounter.CombatPhase.RUNNING) {
            return null;
        }
        int activeTurnIndex = encounter.getActiveTurnIndex();
        if (activeTurnIndex < 0 || activeTurnIndex >= activeCombatants.size()) {
            return null;
        }
        return activeCombatants.get(activeTurnIndex).getId();
    }

    private void restoreActiveCombatantIndex(Encounter encounter, UUID activeId,
                                             List<Combatant> orderedCombatants) {
        if (encounter.getCombatPhase() != Encounter.CombatPhase.RUNNING || activeId == null) {
            return;
        }
        int activeIndex = 0;
        for (Combatant combatant : orderedCombatants) {
            if (!isOnActiveWave(combatant)) {
                continue;
            }
            if (combatant.getId().equals(activeId)) {
                encounter.setActiveTurnIndex(activeIndex);
                encounterRepo.save(encounter);
                return;
            }
            activeIndex++;
        }
    }

    public EncounterDto nextTurn(UUID encounterId) {
        requireRunning(encounterId);
        Encounter encounter = findEntityById(encounterId);
        List<Combatant> combatants = getActiveCombatants(encounterId);

        if (combatants.isEmpty()) {
            throw new IllegalStateException("No combatants in encounter");
        }

        int oldIdx = encounter.getActiveTurnIndex();
        int idx = oldIdx;
        int checked = 0;

        while (checked < combatants.size()) {
            idx = (idx + 1) % combatants.size();
            Combatant candidate = combatants.get(idx);
            if (takesTurn(candidate, combatants)) {
                break;
            }
            checked++;
        }

        if (checked >= combatants.size()) {
            return toDto(encounter);
        }

        if (oldIdx >= 0 && encounter.getLairActionName() != null) {
            Combatant oldCombatant = combatants.get(oldIdx);
            Combatant newCombatant = combatants.get(idx);
            if (initiativeForThreshold(oldCombatant) >= 20 && initiativeForThreshold(newCombatant) < 20) {
                encounter.setLairActionTriggered(true);
            }
        }

        if (idx <= oldIdx) {
            encounter.setRound(encounter.getRound() + 1);
            encounter.setLairActionTriggered(false);
            logEntry(encounterId, CombatLogEntry.EntryType.ROUND_ADVANCE, "",
                    "{\"round\":" + encounter.getRound() + "}");
            tickConditionDurations(encounterId);
        }

        encounter.setActiveTurnIndex(idx);
        resetLegendaryActions(combatants.get(idx));
        encounterRepo.save(encounter);

        logEntry(encounterId, CombatLogEntry.EntryType.TURN_START,
                combatants.get(idx).getId().toString(),
                "{\"activeTurnIndex\":" + idx + "}");

        List<RechargePrompt> prompts = checkRechargeAbilities(combatants.get(idx).getId());
        return new EncounterDto(encounter.getId(), encounter.getCampaign().getId(),
                encounter.getMap() != null ? encounter.getMap().getId() : null,
                encounter.getName(), encounter.getStatus().name(), encounter.getRound(),
                encounter.getActiveTurnIndex(), encounter.getCombatPhase().name(), 0,
                encounter.getLairActionName(), encounter.getLairActionDescription(),
                encounter.getLairActionName() != null && !encounter.isLairActionTriggered(),
                List.of(), prompts);
    }

    public EncounterDto previousTurn(UUID encounterId) {
        requireRunning(encounterId);
        Encounter encounter = findEntityById(encounterId);
        List<Combatant> combatants = getActiveCombatants(encounterId);

        if (combatants.isEmpty() || encounter.getActiveTurnIndex() < 0) {
            return toDto(encounter);
        }

        int oldIdx = encounter.getActiveTurnIndex();
        if (oldIdx >= combatants.size()) {
            oldIdx = combatants.size() - 1;
        }

        int idx = oldIdx;
        int checked = 0;
        boolean crossedBoundary = false;

        while (checked < combatants.size()) {
            if (idx == 0) {
                idx = combatants.size() - 1;
                if (encounter.getRound() > 1) {
                    crossedBoundary = true;
                }
            } else {
                idx--;
            }
            checked++;
            if (takesTurn(combatants.get(idx), combatants)) {
                break;
            }
        }

        if (checked >= combatants.size()) {
            return toDto(encounter);
        }

        if (crossedBoundary) {
            encounter.setRound(encounter.getRound() - 1);
            logEntry(encounterId, CombatLogEntry.EntryType.TURN_END, "", "");
        }

        encounter.setActiveTurnIndex(idx);
        encounterRepo.save(encounter);
        return toDto(encounter);
    }

    public EncounterDto setActiveTurn(UUID encounterId, UUID combatantId) {
        requireRunning(encounterId);
        Encounter encounter = findEntityById(encounterId);
        List<Combatant> combatants = getActiveCombatants(encounterId);

        for (int i = 0; i < combatants.size(); i++) {
            if (combatants.get(i).getId().equals(combatantId)) {
                encounter.setActiveTurnIndex(i);
                encounterRepo.save(encounter);
                return toDto(encounter);
            }
        }
        throw new NotFoundException("Combatant not in encounter: " + combatantId);
    }

    public void resetLegendaryActions(UUID encounterId) {
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        for (Combatant c : combatants) {
            c.setLegendaryActionsUsed(0);
            combatantRepo.save(c);
        }
        logEntry(encounterId, CombatLogEntry.EntryType.ROUND_ADVANCE, "", "{\"legendaryReset\":true}");
    }

    private void resetLegendaryActions(Combatant c) {
        c.setLegendaryActionsUsed(0);
    }

    public CombatantDto setConcentration(UUID combatantId, String spellName) {
        Combatant c = findCombatantById(combatantId);
        c.setConcentratingOn(spellName != null && !spellName.isEmpty() ? spellName : null);
        Combatant saved = combatantRepo.save(c);
        syncCombatantToPartyMember(saved);
        publishRuntimeChange(saved);
        logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.CONCENTRATION_SET,
            combatantId.toString(), "{\"spellName\":\"" + (spellName != null ? spellName : "") + "\"}");
        return toDto(saved);
    }

    public CombatantDto resolveConcentrationCheck(UUID combatantId, boolean passed) {
        Combatant c = findCombatantById(combatantId);
        c.setConcentrationCheckPending(false);
        if (!passed) {
            c.setConcentratingOn(null);
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.CONCENTRATION_LOST,
                combatantId.toString(), "{}");
        } else {
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.CONCENTRATION_CHECK,
                combatantId.toString(), "{\"passed\":true}");
        }
        Combatant saved = combatantRepo.save(c);
        syncCombatantToPartyMember(saved);
        publishRuntimeChange(saved);
        return toDto(saved);
    }

    public List<RechargePrompt> checkRechargeAbilities(UUID combatantId) {
        Combatant c = findCombatantById(combatantId);
        StatBlock sb = c.getStatBlock();
        if (sb == null) return List.of();

        List<String> recharged;
        try {
            recharged = JSON_MAPPER.readValue(c.getRechargedAbilities(), new TypeReference<List<String>>() {});
        } catch (Exception e) { recharged = List.of(); }

        List<RechargePrompt> prompts = new ArrayList<>();
        String actionsJson = sb.getActions();
        if (actionsJson != null && !actionsJson.isEmpty()) {
            prompts.addAll(parseRechargePatterns(actionsJson, recharged));
            prompts.addAll(parseRechargeFromNames(actionsJson, recharged));
        }
        String legendaryJson = sb.getLegendaryActions();
        if (legendaryJson != null && !legendaryJson.isEmpty()) {
            prompts.addAll(parseRechargePatterns(legendaryJson, recharged));
            prompts.addAll(parseRechargeFromNames(legendaryJson, recharged));
        }
        String bonusJson = sb.getBonusActions();
        if (bonusJson != null && !bonusJson.isEmpty()) {
            prompts.addAll(parseRechargePatterns(bonusJson, recharged));
            prompts.addAll(parseRechargeFromNames(bonusJson, recharged));
        }
        return prompts;
    }

    private List<RechargePrompt> parseRechargePatterns(String json, List<String> recharged) {
        List<RechargePrompt> prompts = new ArrayList<>();
        try {
            var node = JSON_MAPPER.readTree(json);
            if (node.isArray()) {
                for (var item : node) {
                    if (item.has("recharge")) {
                        String recharge = item.get("recharge").asText();
                        String[] parts = recharge.split("-");
                        if (parts.length == 2) {
                            int min = Integer.parseInt(parts[0].trim());
                            int max = Integer.parseInt(parts[1].trim());
                            String name = item.has("name") ? item.get("name").asText() : "Unknown";
                            if (!recharged.contains(name)) {
                                prompts.add(new RechargePrompt(name, min, max));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { /* ignore malformed JSON */ }
        return prompts;
    }

    private List<RechargePrompt> parseRechargeFromNames(String json, List<String> recharged) {
        List<RechargePrompt> prompts = new ArrayList<>();
        try {
            var node = JSON_MAPPER.readTree(json);
            if (node.isArray()) {
                for (var item : node) {
                    if (item.has("name")) {
                        String name = item.get("name").asText();
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                                "\\(Recharge\\s+(\\d+)-(\\d+)\\)", java.util.regex.Pattern.CASE_INSENSITIVE)
                                .matcher(name);
                        if (m.find() && !recharged.contains(name)) {
                            int min = Integer.parseInt(m.group(1));
                            int max = Integer.parseInt(m.group(2));
                            prompts.add(new RechargePrompt(name, min, max));
                        }
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return prompts;
    }

    public void resolveRecharge(UUID combatantId, String abilityName, Integer rollResult) {
        Combatant c = findCombatantById(combatantId);
        List<String> recharged;
        try {
            recharged = new ArrayList<>(JSON_MAPPER.readValue(c.getRechargedAbilities(), new TypeReference<List<String>>() {}));
        } catch (Exception e) { recharged = new ArrayList<>(); }

        if (rollResult != null && c.getStatBlock() != null) {
            var prompts = checkRechargeAbilities(combatantId);
            for (var prompt : prompts) {
                if (prompt.abilityName().equals(abilityName)) {
                    if (rollResult >= prompt.minRoll()) {
                        recharged.add(abilityName);
                    }
                    break;
                }
            }
        }

        try {
            c.setRechargedAbilities(JSON_MAPPER.writeValueAsString(recharged));
        } catch (Exception e) { /* ignore */ }
        combatantRepo.save(c);

        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of(
                "abilityName", abilityName, "rollResult", rollResult, "recharged", rollResult != null));
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.RECHARGE,
                combatantId.toString(), payload);
        } catch (Exception e) { /* ignore */ }
    }

    public CombatantDto useLegendaryAction(UUID combatantId) {
        Combatant c = findCombatantById(combatantId);
        if (c.getLegendaryActionsUsed() >= c.getLegendaryActionsMax()) {
            throw new IllegalStateException("No legendary actions remaining");
        }
        c.setLegendaryActionsUsed(c.getLegendaryActionsUsed() + 1);
        Combatant saved = combatantRepo.save(c);
        logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.LEGENDARY_ACTION,
            combatantId.toString(), "{\"remaining\":" + (c.getLegendaryActionsMax() - c.getLegendaryActionsUsed()) + "}");
        return toDto(saved);
    }

    public CombatantDto useLegendaryResistance(UUID combatantId) {
        Combatant c = findCombatantById(combatantId);
        if (c.getLegendaryResistancesUsed() >= c.getLegendaryResistancesMax()) {
            throw new IllegalStateException("No legendary resistances remaining");
        }
        c.setLegendaryResistancesUsed(c.getLegendaryResistancesUsed() + 1);
        Combatant saved = combatantRepo.save(c);
        logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.LEGENDARY_RESISTANCE,
            combatantId.toString(), "{\"remaining\":" + (c.getLegendaryResistancesMax() - c.getLegendaryResistancesUsed()) + "}");
        return toDto(saved);
    }

    public void activateLairAction(UUID encounterId) {
        Encounter encounter = findEntityById(encounterId);
        encounter.setLairActionTriggered(true);
        encounterRepo.save(encounter);
        logEntry(encounterId, CombatLogEntry.EntryType.LAIR_ACTION, "", "");
    }

    @Transactional(readOnly = true)
    public DifficultyResult calculateDifficulty(UUID campaignId, UUID encounterId) {
        findEntityById(encounterId);
        List<PartyMember> party = partyRepo.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId);
        List<CombatantDto> monsters = getCombatants(encounterId).stream()
            .filter(c -> !"PC".equals(c.kind()))
            .toList();
        return calculator.calculate(party, monsters);
    }

    CombatLogEntry logEntry(UUID encounterId, CombatLogEntry.EntryType type, String combatantId, String payload) {
        Encounter encounter = findEntityById(encounterId);
        CombatLogEntry entry = new CombatLogEntry();
        entry.setEncounter(encounter);
        entry.setRound(encounter.getRound());
        entry.setSequence(encounter.getLogSequence() + 1);
        entry.setType(type);
        entry.setCombatantId(combatantId);
        entry.setPayload(payload);
        encounter.setLogSequence(encounter.getLogSequence() + 1);
        return combatLogRepo.save(entry);
    }

    public void logDiceRoll(UUID encounterId, String expression, int total, String rollsJson) {
        String payload;
        try {
            JsonNode rollsNode;
            try {
                rollsNode = JSON_MAPPER.readTree(rollsJson);
            } catch (Exception e) {
                rollsNode = null;
            }
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("expression", expression);
            map.put("total", total);
            if (rollsNode != null) {
                map.put("rolls", rollsNode);
            }
            payload = JSON_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            payload = "{\"expression\":\"" + expression + "\",\"total\":" + total + "}";
        }
        logEntry(encounterId, CombatLogEntry.EntryType.DICE_ROLL, "", payload);
    }

    private CombatLogEntryDto toLogDto(CombatLogEntry entry) {
        String combatantName = "";
        try {
            UUID cid = UUID.fromString(entry.getCombatantId());
            combatantName = combatantRepo.findById(cid).map(Combatant::getName).orElse("");
        } catch (Exception e) {
            // ignore
        }
        return new CombatLogEntryDto(
                entry.getId(), entry.getRound(), entry.getSequence(),
                entry.getType().name(), entry.getCombatantId(), combatantName,
                entry.getPayload(), entry.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<CombatLogEntryDto> getLog(UUID encounterId) {
        return combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId).stream()
                .map(this::toLogDto).toList();
    }

    @Transactional
    public void undo(UUID encounterId) {
        List<CombatLogEntry> log = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId);
        if (log.isEmpty()) return;

        CombatLogEntry lastEntry = log.get(log.size() - 1);
        if (UNDO_BOUNDARIES.contains(lastEntry.getType())) {
            throw new IllegalStateException("Cannot undo past a session boundary: " + lastEntry.getType());
        }
        int removeCount = lastEntry.getType() == CombatLogEntry.EntryType.SORT_ORDER
                && log.size() >= 2
                && log.get(log.size() - 2).getType() == CombatLogEntry.EntryType.INITIATIVE_SET
                ? 2
                : 1;
        Encounter encounter = findEntityById(encounterId);

        Map<UUID, Combatant> combatants = combatantRepo
                .findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
                .collect(Collectors.toMap(Combatant::getId, Function.identity()));

        for (Combatant c : combatants.values()) {
            resetCombatantToBaseline(c);
        }
        encounter.setRound(1);
        encounter.setActiveTurnIndex(-1);
        encounter.setLairActionTriggered(false);

        List<CombatLogEntry> entriesToKeep = log.subList(0, log.size() - removeCount);
        for (CombatLogEntry entry : entriesToKeep) {
            replayEntry(entry, combatants, encounter);
        }

        boolean hasTurnEvidence = entriesToKeep.stream()
                .anyMatch(e -> e.getType() == CombatLogEntry.EntryType.TURN_START);
        encounter.setCombatPhase(hasTurnEvidence ? Encounter.CombatPhase.RUNNING : Encounter.CombatPhase.SETUP);
        if (!hasTurnEvidence) {
            encounter.setRound(0);
            encounter.setActiveTurnIndex(-1);
        }

        for (Combatant c : combatants.values()) {
            if (!em.contains(c)) {
                combatantRepo.save(c);
            }
        }

        if (lastEntry.getType() == CombatLogEntry.EntryType.COMBATANT_ADDED) {
            try {
                UUID addedId = UUID.fromString(lastEntry.getCombatantId());
                combatants.remove(addedId);
                combatantRepo.deleteById(addedId);
            } catch (Exception e) { /* ignore */ }
        }

        tickConditionDurations(encounterId);

        long retainedLogSequence = entriesToKeep.stream()
                .mapToLong(CombatLogEntry::getSequence)
                .max()
                .orElse(0);
        encounter.setLogSequence(retainedLogSequence);
        encounterRepo.save(encounter);

        combatLogRepo.deleteAll(log.subList(log.size() - removeCount, log.size()));
    }

    private void resetCombatantToBaseline(Combatant c) {
        c.setCurrentHp(c.getMaxHp());
        c.setTempHp(0);
        c.setDefeated(false);
        c.setHidden(false);
        c.setConditionsJson("[]");
        c.setConcentratingOn(null);
        c.setConcentrationCheckPending(false);
        c.setLegendaryActionsUsed(0);
        c.setLegendaryResistancesUsed(0);
        c.setInitiative(null);
        c.setRechargedAbilities("[]");
    }

    private void replayEntry(CombatLogEntry entry, Map<UUID, Combatant> combatants, Encounter encounter) {
        UUID combatantId;
        try {
            combatantId = UUID.fromString(entry.getCombatantId());
        } catch (IllegalArgumentException e) {
            combatantId = null;
        }
        Combatant c = combatantId != null ? combatants.get(combatantId) : null;

        switch (entry.getType()) {
            case TURN_START -> {
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    encounter.setActiveTurnIndex(node.get("activeTurnIndex").asInt(-1));
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case ROUND_ADVANCE -> {
                encounter.setRound(entry.getRound());
            }
            case INITIATIVE_SET -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    var initiativeNode = node.get("initiative");
                    c.setInitiative(initiativeNode == null || initiativeNode.isNull() ? null : initiativeNode.asInt());
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case COMBATANT_REORDERED -> {
                UUID activeId = replayActiveCombatantId(encounter, combatants.values());
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    var orderedNode = node.get("orderedIds");
                    if (orderedNode != null && orderedNode.isArray()) {
                        for (int i = 0; i < orderedNode.size(); i++) {
                            String idStr = orderedNode.get(i).asText();
                            Combatant rc = combatants.get(UUID.fromString(idStr));
                            if (rc != null) {
                                rc.setSortOrder(i);
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore malformed payload
                }
                restoreActiveCombatantIndex(encounter, activeId, replayOrder(combatants.values()));
            }
            case SORT_ORDER -> {
                UUID activeId = replayActiveCombatantId(encounter, combatants.values());
                try {
                    Map<String, Integer> orderMap = JSON_MAPPER.readValue(
                            entry.getPayload(), new TypeReference<Map<String, Integer>>() {});
                    for (var entry_ : orderMap.entrySet()) {
                        try {
                            UUID cid = UUID.fromString(entry_.getKey());
                            Combatant so = combatants.get(cid);
                            if (so != null) {
                                so.setSortOrder(entry_.getValue());
                            }
                        } catch (Exception e) { /* ignore */ }
                    }
                } catch (Exception e) { /* ignore */ }
                restoreActiveCombatantIndex(encounter, activeId, replayOrder(combatants.values()));
            }
            case DAMAGE -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int amount = node.get("amount").asInt(0);
                    if (amount < 0) {
                        int damage = -amount;
                        int remainingDamage = damage - c.getTempHp();
                        c.setTempHp(Math.max(0, c.getTempHp() - damage));
                        if (remainingDamage > 0) {
                            c.setCurrentHp(Math.max(0, c.getCurrentHp() - remainingDamage));
                        }
                    }
                    if (c.getCurrentHp() <= 0 && !"PC".equals(c.getKind())) {
                        c.setDefeated(true);
                    }
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case HEAL -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int amount = node.get("amount").asInt(0);
                    if (amount > 0) {
                        c.setCurrentHp(Math.min(c.getMaxHp(), c.getCurrentHp() + amount));
                    }
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case TEMP_HP -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int amount = node.get("amount").asInt(0);
                    c.setTempHp(c.getTempHp() + amount);
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case CONDITION_ADDED -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    var conditions = new ArrayList<>(JSON_MAPPER.readValue(c.getConditionsJson(),
                            new TypeReference<List<Map<String, Object>>>() {}));
                    Map<String, Object> condition = Map.of(
                            "sourceKey", node.has("sourceKey") ? node.get("sourceKey").asText() : "",
                            "durationRounds", node.has("durationRounds") ? node.get("durationRounds").asInt(0) : 0);
                    conditions.add(condition);
                    c.setConditionsJson(JSON_MAPPER.writeValueAsString(conditions));
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case CONDITION_REMOVED -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    String sourceKey = node.has("sourceKey") ? node.get("sourceKey").asText() : "";
                    var conditions = new ArrayList<>(JSON_MAPPER.readValue(c.getConditionsJson(),
                            new TypeReference<List<Map<String, Object>>>() {}));
                    conditions.removeIf(cond -> sourceKey.equals(cond.get("sourceKey")));
                    c.setConditionsJson(JSON_MAPPER.writeValueAsString(conditions));
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case DEFEATED -> {
                if (c != null && c.getCurrentHp() <= 0) c.setDefeated(true);
            }
            case REVIVED -> {
                if (c != null) c.setDefeated(false);
            }
            case CONCENTRATION_SET -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    String spellName = node.has("spellName") ? node.get("spellName").asText() : null;
                    c.setConcentratingOn(spellName != null && !spellName.isEmpty() ? spellName : null);
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case CONCENTRATION_CHECK -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    String passed = node.has("passed") ? node.get("passed").asText() : null;
                    if ("true".equals(passed)) {
                        c.setConcentrationCheckPending(false);
                    } else if ("false".equals(passed)) {
                        c.setConcentratingOn(null);
                        c.setConcentrationCheckPending(false);
                    } else {
                        c.setConcentrationCheckPending(true);
                    }
                } catch (Exception e) {
                    c.setConcentrationCheckPending(true);
                }
            }
            case CONCENTRATION_LOST -> {
                if (c != null) {
                    c.setConcentratingOn(null);
                    c.setConcentrationCheckPending(false);
                }
            }
            case RECHARGE -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    boolean recharged = node.has("recharged") && !node.get("recharged").isNull()
                            && node.get("recharged").asBoolean();
                    String abilityName = node.has("abilityName") ? node.get("abilityName").asText() : "";
                    if (recharged && !abilityName.isEmpty()) {
                        List<String> rechargedList = new ArrayList<>(JSON_MAPPER.readValue(
                                c.getRechargedAbilities(), new TypeReference<List<String>>() {}));
                        if (!rechargedList.contains(abilityName)) {
                            rechargedList.add(abilityName);
                            c.setRechargedAbilities(JSON_MAPPER.writeValueAsString(rechargedList));
                        }
                    }
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case LEGENDARY_ACTION -> {
                if (c != null) c.setLegendaryActionsUsed(c.getLegendaryActionsUsed() + 1);
            }
            case LEGENDARY_RESISTANCE -> {
                if (c != null) c.setLegendaryResistancesUsed(c.getLegendaryResistancesUsed() + 1);
            }
            case GROUP_SPLIT -> {
                if (c != null) c.setGroupId(null);
            }
            case SET_HP -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    c.setCurrentHp(node.get("currentHp").asInt());
                    c.setTempHp(node.get("tempHp").asInt(0));
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case CONDITION_TICKED -> {
                if (c == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    var expiredNode = node.get("expiredKeys");
                    if (expiredNode != null && expiredNode.isArray()) {
                        var conditions = new ArrayList<>(JSON_MAPPER.readValue(c.getConditionsJson(),
                                new TypeReference<List<Map<String, Object>>>() {}));
                        for (var keyNode : expiredNode) {
                            String expiredKey = keyNode.asText();
                            conditions.removeIf(cond -> expiredKey.equals(cond.get("sourceKey")));
                        }
                        c.setConditionsJson(JSON_MAPPER.writeValueAsString(conditions));
                    }
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case COMBATANT_ADDED -> {
                if (combatantId != null && c == null) {
                    try {
                        var node = JSON_MAPPER.readTree(entry.getPayload());
                        Combatant restored = new Combatant();
                        restored.setEncounter(encounter);
                        restored.setName(node.get("name").asText());
                        var initNode = node.get("initiative");
                        restored.setInitiative(initNode == null || initNode.isNull() ? null : initNode.asInt());
                        restored.setMaxHp(node.get("maxHp").asInt(10));
                        restored.setCurrentHp(node.get("maxHp").asInt(10));
                        restored.setKind(node.get("kind").asText("NPC"));
                        restored.setConditionsJson("[]");
                        em.persist(restored);
                        em.flush();
                        combatants.put(restored.getId(), restored);
                    } catch (Exception e) {
                        // ignore malformed payload
                    }
                }
            }
            case COMBATANT_REMOVED -> {
                if (c != null) {
                    c.setDefeated(false);
                    c.setHidden(false);
                }
            }
            case TURN_END, LAIR_ACTION, NOTE, DICE_ROLL,
                 ENCOUNTER_ACTIVATED, ENCOUNTER_ENDED -> {
                // No combatant state change to replay
            }
        }
    }

    private UUID replayActiveCombatantId(Encounter encounter, Collection<Combatant> combatants) {
        List<Combatant> activeCombatants = replayOrder(combatants).stream()
                .filter(this::isOnActiveWave)
                .toList();
        return activeCombatantId(encounter, activeCombatants);
    }

    private List<Combatant> replayOrder(Collection<Combatant> combatants) {
        return combatants.stream()
                .sorted(Comparator.comparingInt(Combatant::getSortOrder))
                .toList();
    }

    private void rebuildSortOrderForUndo(Map<UUID, Combatant> combatants, Encounter encounter) {
        List<Combatant> list = new ArrayList<>(combatants.values());
        list.sort(Comparator
                .comparing(Combatant::getInitiative, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparing(Combatant::getTieBreaker).reversed())
                .thenComparingInt(Combatant::getSortOrder)
                .thenComparing(Combatant::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i);
        }
        int turnIndex = encounter.getActiveTurnIndex();
        if (turnIndex >= 0 && turnIndex < list.size()) {
            UUID activeId = list.get(turnIndex).getId();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(activeId)) {
                    encounter.setActiveTurnIndex(i);
                    break;
                }
            }
        }
    }

    private void resortCombatantsInMemory(Map<UUID, Combatant> combatants, Encounter encounter) {
        List<Combatant> list = new ArrayList<>(combatants.values());
        list.sort(Comparator
                .comparing(Combatant::getInitiative, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparing(Combatant::getTieBreaker).reversed())
                .thenComparingInt(Combatant::getSortOrder)
                .thenComparing(Combatant::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortOrder(i);
        }
        if (!list.isEmpty()) {
            int turnIndex = encounter.getActiveTurnIndex();
            if (turnIndex >= 0 && turnIndex < list.size()) {
                UUID activeId = list.get(turnIndex).getId();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getId().equals(activeId)) {
                        encounter.setActiveTurnIndex(i);
                        break;
                    }
                }
            }
        }
    }

    private Encounter requireSetup(UUID encounterId) {
        Encounter e = lockEncounter(encounterId);
        if (e.getStatus() != Encounter.Status.ACTIVE || e.getCombatPhase() != Encounter.CombatPhase.SETUP) {
            throw new IllegalStateException("Encounter is not in SETUP phase");
        }
        return e;
    }

    private Encounter requireRunning(UUID encounterId) {
        Encounter e = findEntityById(encounterId);
        if (e.getStatus() != Encounter.Status.ACTIVE) {
            throw new IllegalStateException("Encounter is not ACTIVE");
        }
        if (e.getCombatPhase() != Encounter.CombatPhase.RUNNING) {
            throw new InitiativeSetupIncompleteException("Encounter is not in RUNNING phase", 0);
        }
        return e;
    }

    private static int initiativeForThreshold(Combatant combatant) {
        return combatant.getInitiative() == null ? Integer.MIN_VALUE : combatant.getInitiative();
    }

    private int firstEligibleTurnIndex(List<Combatant> combatants) {
        for (int i = 0; i < combatants.size(); i++) {
            if (takesTurn(combatants.get(i), combatants)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether the initiative order stops on this combatant. A group acts once, on its flagged
     * leader, but that flag is fixed when the group is built: a leader killed mid-fight used to
     * take the whole group out of the order while its surviving members were still standing on
     * the map, and an encounter whose only survivors were led that way could not be started at
     * all. When no living leader is left, the group's first surviving member represents it.
     */
    private static boolean takesTurn(Combatant candidate, List<Combatant> order) {
        if (candidate.isDefeated()) return false;
        if (candidate.getGroupId() == null || candidate.isGroupLeader()) return true;
        List<Combatant> survivors = order.stream()
                .filter(c -> candidate.getGroupId().equals(c.getGroupId()))
                .filter(c -> !c.isDefeated())
                .toList();
        return survivors.stream().noneMatch(Combatant::isGroupLeader)
                && survivors.get(0).getId().equals(candidate.getId());
    }

    @Transactional(readOnly = true)
    public List<CombatantDto> getInitiativeSetupCombatants(UUID encounterId) {
        List<Combatant> all = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        Map<UUID, Boolean> actsForGroup = all.stream()
                .collect(Collectors.toMap(Combatant::getId, c -> takesTurn(c, all)));
        return all.stream()
                .map(c -> toDto(c, actsForGroup.get(c.getId())))
                .toList();
    }

    public List<CombatantDto> rollUnsetNpcInitiatives(UUID encounterId) {
        Encounter encounter = requireSetup(encounterId);
        List<Combatant> all = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        Map<String, Integer> rolledByGroup = new java.util.HashMap<>();
        for (Combatant combatant : all) {
            if (combatant.getInitiative() != null || "PC".equals(combatant.getKind())) continue;
            int modifier = combatant.getStatBlock() == null ? 0 : dexModifier(combatant.getStatBlock());
            String groupId = combatant.getGroupId();
            int roll;
            if (groupId == null) {
                roll = diceEngine.roll("d20").total();
            } else {
                roll = rolledByGroup.computeIfAbsent(groupId, unused -> diceEngine.roll("d20").total());
            }
            combatant.setInitiative(roll + modifier);
            combatantRepo.save(combatant);
            logInitiativeRoll(encounter.getId(), combatant, roll, modifier);
        }
        resortCombatants(encounterId);
        return getCombatants(encounterId);
    }

    private void logInitiativeRoll(UUID encounterId, Combatant combatant, int roll, int dexMod) {
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of(
                    "initiative", combatant.getInitiative(), "roll", roll, "dexMod", dexMod));
            logEntry(encounterId, CombatLogEntry.EntryType.INITIATIVE_SET,
                    combatant.getId().toString(), payload);
        } catch (Exception e) { /* ignore */ }
    }

    public EncounterDto startCombat(UUID encounterId, boolean acceptUnset) {
        Encounter encounter = requireSetup(encounterId);
        List<Combatant> allCombatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        if (allCombatants.isEmpty()) {
            throw new InitiativeSetupIncompleteException("Combat cannot start without combatants.", 0);
        }
        long unset = allCombatants.stream().filter(c -> c.getInitiative() == null).count();
        if (unset > 0 && !acceptUnset) {
            throw new InitiativeSetupIncompleteException(unset + " combatant(s) still have unset initiative.", (int) unset);
        }
        resortCombatants(encounterId);
        List<Combatant> combatants = getActiveCombatants(encounterId);
        int first = firstEligibleTurnIndex(combatants);
        if (first < 0) {
            throw new InitiativeSetupIncompleteException("Combat cannot start without an eligible active-wave combatant.", (int) unset);
        }
        encounter.setCombatPhase(Encounter.CombatPhase.RUNNING);
        encounter.setRound(1);
        encounter.setActiveTurnIndex(first);
        encounterRepo.save(encounter);
        resetLegendaryActions(combatants.get(first));
        logEntry(encounterId, CombatLogEntry.EntryType.TURN_START, combatants.get(first).getId().toString(),
                "{\"activeTurnIndex\":" + first + "}");
        return toDto(encounter);
    }

    private void syncCombatantToPartyMember(Combatant combatant) {
        if (combatant.getPartyMember() == null) return;
        PartyMember pm = combatant.getPartyMember();
        pm.setCurrentHp(combatant.getCurrentHp());
        pm.setTempHp(combatant.getTempHp());
        pm.setConditionsJson(combatant.getConditionsJson());
        pm.setConcentratingOn(combatant.getConcentratingOn());
        partyRepo.save(pm);
    }

    Combatant findCombatantById(UUID id) {
        return combatantRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Combatant not found: " + id));
    }

    Encounter findEntityById(UUID id) {
        return encounterRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Encounter not found: " + id));
    }
}
