package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
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
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncounterService {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private final EncounterRepository encounterRepo;
    private final CampaignRepository campaignRepo;
    private final EntityManager em;
    private final GameMapRepository mapRepo;
    private final CombatantRepository combatantRepo;
    private final CombatLogEntryRepository combatLogRepo;
    private final TokenRepository tokenRepo;
    private final PartyMemberRepository partyRepo;
    private final StatBlockRepository statBlockRepo;
    private final EncounterWaveRepository waveRepo;
    private final CombatDifficultyCalculator calculator;
    private final DiceEngine diceEngine;
    private final TablePresentationService tablePresentationService;
    private final SceneRefCleaner sceneRefCleaner;

    public EncounterService(EncounterRepository encounterRepo, CampaignRepository campaignRepo,
                            EntityManager em, GameMapRepository mapRepo,
                            CombatantRepository combatantRepo, CombatLogEntryRepository combatLogRepo,
                            TokenRepository tokenRepo,
                            PartyMemberRepository partyRepo, StatBlockRepository statBlockRepo,
                            EncounterWaveRepository waveRepo,
                            CombatDifficultyCalculator calculator, DiceEngine diceEngine,
                            TablePresentationService tablePresentationService,
                            SceneRefCleaner sceneRefCleaner) {
        this.encounterRepo = encounterRepo;
        this.campaignRepo = campaignRepo;
        this.em = em;
        this.mapRepo = mapRepo;
        this.combatantRepo = combatantRepo;
        this.combatLogRepo = combatLogRepo;
        this.tokenRepo = tokenRepo;
        this.partyRepo = partyRepo;
        this.statBlockRepo = statBlockRepo;
        this.waveRepo = waveRepo;
        this.calculator = calculator;
        this.diceEngine = diceEngine;
        this.tablePresentationService = tablePresentationService;
        this.sceneRefCleaner = sceneRefCleaner;
    }

    public record CreateRequest(String name, UUID mapId) {}

    public record AddFromLibraryRequest(UUID statBlockId, int quantity, String groupName,
                                         UUID waveId, Integer startX, Integer startY, String placementRegionKey) {}

    public record UpdateRequest(String name, UUID mapId, String status, String lairActionName,
                                String lairActionDescription) {}

    public record ConditionStateDto(String sourceKey, String name, String description,
                                    int durationRounds, boolean tickOnSourceTurn,
                                    int appliedInRound, UUID appliedByCombatantId) {}

    public record CombatantDto(UUID id, UUID encounterId, String name, int initiative,
                               int sortOrder, int currentHp, int maxHp, int tempHp,
                               String kind, String groupId, boolean groupLeader,
                               UUID tokenId, UUID statBlockId, UUID partyMemberId,
                               boolean defeated, boolean hidden, boolean bloodied,
                               List<ConditionStateDto> conditions,
                               String concentratingOn, boolean concentrationCheckPending,
                               int legendaryActionsUsed, int legendaryActionsMax,
                               int legendaryResistancesUsed, int legendaryResistancesMax,
                               String notes,
                               UUID waveId, Integer startX, Integer startY, String placementRegionKey) {}

    public record CombatantCreateRequest(String name, int maxHp, String kind,
                                         UUID tokenId, UUID statBlockId, UUID partyMemberId) {}

    public record CombatantUpdateRequest(String name, Integer initiative, Integer sortOrder,
                                         Integer currentHp, Integer maxHp, Integer tempHp,
                                         String kind, String groupId, Boolean groupLeader,
                                         Boolean defeated, Boolean hidden,
                                         String concentratingOn, Boolean concentrationCheckPending,
                                         Integer legendaryActionsUsed, Integer legendaryActionsMax,
                                         Integer legendaryResistancesUsed, Integer legendaryResistancesMax,
                                         String notes,
                                         UUID waveId, Integer startX, Integer startY, String placementRegionKey) {}

    public record InitiativeRequest(int initiative) {}

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

    public record PrefillMapRequest(UUID mapId) {}

    public record EncounterDto(UUID id, UUID campaignId, UUID mapId, String name, String status,
                               int round, int activeTurnIndex, int combatantCount,
                               String lairActionName, String lairActionDescription,
                               boolean lairActionAvailable,
                               List<CombatantDto> combatants,
                               List<RechargePrompt> rechargePrompts) {}

    public record CombatLogEntryDto(UUID id, int round, long sequence, String type,
                                     String combatantId, String combatantName, String payload,
                                     Instant createdAt) {}

    static EncounterDto toDto(Encounter e) {
        return new EncounterDto(e.getId(), e.getCampaign().getId(),
                e.getMap() != null ? e.getMap().getId() : null,
                e.getName(), e.getStatus().name(), e.getRound(), e.getActiveTurnIndex(),
                0,
                e.getLairActionName(), e.getLairActionDescription(),
                e.getLairActionName() != null && !e.isLairActionTriggered(),
                List.of(), List.of());
    }

    static CombatantDto toDto(Combatant c) {
        List<ConditionStateDto> conditions;
        try {
            conditions = JSON_MAPPER.readValue(c.getConditionsJson(),
                    new TypeReference<List<ConditionStateDto>>() {});
        } catch (Exception e) {
            conditions = List.of();
        }
        boolean bloodied = c.getCurrentHp() <= c.getMaxHp() / 2
                && c.getCurrentHp() > 0 && !c.isDefeated();
        return new CombatantDto(c.getId(), c.getEncounter().getId(),
                c.getName(), c.getInitiative(), c.getSortOrder(),
                c.getCurrentHp(), c.getMaxHp(), c.getTempHp(),
                c.getKind(), c.getGroupId(), c.isGroupLeader(),
                c.getToken() != null ? c.getToken().getId() : null,
                c.getStatBlock() != null ? c.getStatBlock().getId() : null,
                c.getPartyMember() != null ? c.getPartyMember().getId() : null,
                c.isDefeated(), c.isHidden(), bloodied,
                conditions,
                c.getConcentratingOn(), c.isConcentrationCheckPending(),
                c.getLegendaryActionsUsed(), c.getLegendaryActionsMax(),
                c.getLegendaryResistancesUsed(), c.getLegendaryResistancesMax(),
                c.getNotes(),
                c.getWave() != null ? c.getWave().getId() : null,
                c.getStartX(), c.getStartY(), c.getPlacementRegionKey());
    }

    public EncounterDto create(UUID campaignId, CreateRequest req) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));
        Encounter e = new Encounter();
        e.setCampaign(campaign);
        e.setName(req.name());
        if (req.mapId() != null) {
            GameMap map = mapRepo.findById(req.mapId())
                    .orElseThrow(() -> new NotFoundException("Map not found: " + req.mapId()));
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
            e.setMap(map);
        } else {
            e.setMap(null);
        }
        if (req.status() != null) {
            e.setStatus(Encounter.Status.valueOf(req.status()));
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
        encounterRepo.findByCampaignIdAndStatus(e.getCampaign().getId(), Encounter.Status.ACTIVE)
                .ifPresent(active -> {
                    active.setStatus(Encounter.Status.DONE);
                    encounterRepo.save(active);
                });
        e.setStatus(Encounter.Status.ACTIVE);
        e.setRound(1);
        e.setActiveTurnIndex(-1);
        EncounterDto dto = toDto(encounterRepo.save(e));
        logEntry(id, CombatLogEntry.EntryType.ENCOUNTER_ACTIVATED, "", "");
        return dto;
    }

    public EncounterDto endEncounter(UUID id) {
        Encounter e = findEntityById(id);
        e.setStatus(Encounter.Status.DONE);
        EncounterDto dto = toDto(encounterRepo.save(e));
        logEntry(id, CombatLogEntry.EntryType.ENCOUNTER_ENDED, "", "");
        logEntry(id, CombatLogEntry.EntryType.SESSION_END, "",
                "{\"endedAt\":\"" + Instant.now().toString() + "\"}");
        tablePresentationService.updateAoEs(e.getCampaign().getId(), List.of());
        tablePresentationService.broadcastCurrentState(e.getCampaign().getId());
        return dto;
    }

    public CombatantDto addCombatant(UUID encounterId, CombatantCreateRequest req) {
        Encounter e = findEntityById(encounterId);
        Combatant c = new Combatant();
        c.setEncounter(e);

        String name = req.name();
        int maxHp = req.maxHp();
        String kind = req.kind() != null ? req.kind() : "NPC";
        int currentHp = maxHp;

        if (req.tokenId() != null) {
            if (combatantRepo.findByEncounterIdAndTokenId(encounterId, req.tokenId()).isPresent()) {
                throw new IllegalArgumentException("Token already has a combatant in this encounter");
            }
            Token token = tokenRepo.findById(req.tokenId())
                    .orElseThrow(() -> new NotFoundException("Token not found: " + req.tokenId()));
            name = token.getName();
            kind = token.getKind();
            maxHp = token.getMaxHp() != null ? token.getMaxHp() : 10;
            currentHp = token.getCurrentHp() != null ? token.getCurrentHp() : maxHp;
            c.setToken(token);
        } else if (req.statBlockId() != null) {
            StatBlock sb = statBlockRepo.findById(req.statBlockId())
                    .orElseThrow(() -> new NotFoundException("StatBlock not found: " + req.statBlockId()));
            name = sb.getName();
            kind = "MONSTER";
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

        c.setName(name);
        c.setKind(kind);
        c.setMaxHp(maxHp);
        c.setCurrentHp(currentHp);
        c.setSortOrder((int) combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).size());

        Combatant saved = combatantRepo.save(c);
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of(
                "name", saved.getName(),
                "initiative", saved.getInitiative(),
                "maxHp", saved.getMaxHp(),
                "kind", saved.getKind()));
            logEntry(encounterId, CombatLogEntry.EntryType.COMBATANT_ADDED,
                saved.getId().toString(), payload);
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
                    null,
                    sb.getId(),
                    null);
            CombatantDto dto = addCombatant(encounterId, one);
            dto = updateCombatant(dto.id(), new CombatantUpdateRequest(
                    null, null, null, null, null, null,
                    null, groupId, i == 0, null, null, null, null, null, null, null, null, null,
                    wave.getId(), req.startX(), req.startY(), req.placementRegionKey()));
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
        if (req.kind() != null) c.setKind(req.kind());
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
        return toDto(saved);
    }

    public List<CombatantDto> prefillFromMap(UUID encounterId, UUID mapId) {
        Encounter e = findEntityById(encounterId);
        List<Token> tokens = tokenRepo.findByMapIdOrderByNameAsc(mapId);
        for (Token token : tokens) {
            if (combatantRepo.findByEncounterIdAndTokenId(encounterId, token.getId()).isPresent()) {
                continue;
            }
            if (token.getPartyMember() != null
                    && combatantRepo.findByEncounterIdAndPartyMemberId(encounterId, token.getPartyMember().getId()).isPresent()) {
                continue;
            }
            Combatant c = new Combatant();
            c.setEncounter(e);
            c.setName(token.getName());
            c.setKind(token.getKind());
            if (token.getPartyMember() != null) {
                PartyMember linked = token.getPartyMember();
                c.setMaxHp(linked.getMaxHp());
                c.setCurrentHp(linked.getCurrentHp());
                c.setTempHp(linked.getTempHp());
                if (linked.getConditionsJson() != null && !linked.getConditionsJson().isBlank()) {
                    c.setConditionsJson(linked.getConditionsJson());
                }
                c.setConcentratingOn(linked.getConcentratingOn());
                c.setPartyMember(linked);
            } else {
                c.setMaxHp(token.getMaxHp() != null ? token.getMaxHp() : 10);
                c.setCurrentHp(token.getCurrentHp() != null ? token.getCurrentHp() : c.getMaxHp());
            }
            c.setToken(token);
            c.setSortOrder((int) combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).size());
            combatantRepo.save(c);
        }
        return getCombatants(encounterId);
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

    public CombatantDto splitGroupMember(UUID combatantId) {
        Combatant c = findCombatantById(combatantId);
        c.setGroupId(null);
        return toDto(combatantRepo.save(c));
    }

    @Transactional(readOnly = true)
    public List<CombatantDto> getCombatants(UUID encounterId) {
        return combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
                .map(EncounterService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CombatantDto getCombatant(UUID combatantId) {
        return toDto(findCombatantById(combatantId));
    }

    public CombatantDto setInitiative(UUID combatantId, int initiative) {
        Combatant c = findCombatantById(combatantId);
        int previous = c.getInitiative();
        c.setInitiative(initiative);
        Combatant saved = combatantRepo.save(c);

        try {
            String payload = JSON_MAPPER.writeValueAsString(
                    Map.of("initiative", initiative, "previousInitiative", previous));
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.INITIATIVE_SET,
                    combatantId.toString(), payload);
        } catch (Exception e) { /* log failure is non-fatal */ }

        resortCombatants(c.getEncounter().getId());

        return toDto(saved);
    }

    public List<CombatantDto> autoRollInitiative(UUID encounterId) {
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        for (Combatant c : combatants) {
            if (!"PC".equals(c.getKind())) {
                int dexMod = 0;
                if (c.getStatBlock() != null) {
                    dexMod = dexModifier(c.getStatBlock());
                }
                int roll = diceEngine.roll("d20").total();
                c.setInitiative(roll + dexMod);
                combatantRepo.save(c);
                try {
                    String payload = JSON_MAPPER.writeValueAsString(
                            Map.of("initiative", c.getInitiative(), "roll", roll, "dexMod", dexMod));
                    logEntry(encounterId, CombatLogEntry.EntryType.INITIATIVE_SET,
                            c.getId().toString(), payload);
                } catch (Exception e) { /* ignore */ }
            }
        }
        resortCombatants(encounterId);
        return getCombatants(encounterId);
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
                combatantId.toString(), "{}");
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
                combatantId.toString(), "{}");
        }
        Combatant saved = combatantRepo.save(c);
        syncCombatantToPartyMember(saved);
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
        CombatLogEntry.EntryType type = defeated ? CombatLogEntry.EntryType.DEFEATED : CombatLogEntry.EntryType.REVIVED;
        logEntry(c.getEncounter().getId(), type, combatantId.toString(), "{}");
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
        var encounterIds = combatants.stream().map(Combatant::getId).collect(Collectors.toSet());
        if (orderedIds.size() != encounterIds.size() || !encounterIds.containsAll(orderedIds)) {
            throw new IllegalArgumentException("orderedIds must contain exactly the encounter's combatants");
        }
        for (int i = 0; i < orderedIds.size(); i++) {
            Combatant c = findCombatantById(orderedIds.get(i));
            c.setSortOrder(i);
        }
        combatantRepo.saveAll(combatants);
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of("orderedIds", orderedIds));
            logEntry(encounterId, CombatLogEntry.EntryType.COMBATANT_REORDERED, "", payload);
        } catch (Exception e) { /* ignore */ }
        return getCombatants(encounterId);
    }

    private void resortCombatants(UUID encounterId) {
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);
        combatants.sort(Comparator
                .comparing(Combatant::getInitiative).reversed()
                .thenComparing(Comparator.comparing(Combatant::getTieBreaker).reversed())
                .thenComparing(Combatant::getName));
        for (int i = 0; i < combatants.size(); i++) {
            combatants.get(i).setSortOrder(i);
            combatantRepo.save(combatants.get(i));
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

    public EncounterDto nextTurn(UUID encounterId) {
        Encounter encounter = findEntityById(encounterId);
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);

        if (combatants.isEmpty()) {
            throw new IllegalStateException("No combatants in encounter");
        }

        int oldIdx = encounter.getActiveTurnIndex();
        int idx = oldIdx;
        int checked = 0;

        while (checked < combatants.size()) {
            idx = (idx + 1) % combatants.size();
            Combatant candidate = combatants.get(idx);
            boolean stoppable = !candidate.isDefeated()
                    && (candidate.getGroupId() == null || candidate.isGroupLeader());
            if (stoppable) {
                break;
            }
            checked++;
        }

        if (checked >= combatants.size()) {
            throw new IllegalStateException("All combatants defeated");
        }

        if (oldIdx >= 0 && encounter.getLairActionName() != null) {
            Combatant oldCombatant = combatants.get(oldIdx);
            Combatant newCombatant = combatants.get(idx);
            if (oldCombatant.getInitiative() >= 20 && newCombatant.getInitiative() < 20) {
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
        tablePresentationService.broadcastCurrentState(encounter.getCampaign().getId());
        return new EncounterDto(encounter.getId(), encounter.getCampaign().getId(),
                encounter.getMap() != null ? encounter.getMap().getId() : null,
                encounter.getName(), encounter.getStatus().name(), encounter.getRound(),
                encounter.getActiveTurnIndex(), 0,
                encounter.getLairActionName(), encounter.getLairActionDescription(),
                encounter.getLairActionName() != null && !encounter.isLairActionTriggered(),
                List.of(), prompts);
    }

    public EncounterDto previousTurn(UUID encounterId) {
        Encounter encounter = findEntityById(encounterId);
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);

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
            if (!combatants.get(idx).isDefeated()
                    && (combatants.get(idx).getGroupId() == null || combatants.get(idx).isGroupLeader())) {
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
        tablePresentationService.broadcastCurrentState(encounter.getCampaign().getId());
        return toDto(encounter);
    }

    public EncounterDto setActiveTurn(UUID encounterId, UUID combatantId) {
        Encounter encounter = findEntityById(encounterId);
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);

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

        List<CombatLogEntry> entriesToKeep = log.subList(0, log.size() - 1);
        for (CombatLogEntry entry : entriesToKeep) {
            replayEntry(entry, combatants, encounter);
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

        encounter.setLogSequence(entriesToKeep.size());
        encounterRepo.save(encounter);

        combatLogRepo.delete(lastEntry);
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
        c.setInitiative(0);
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
                    c.setInitiative(node.get("initiative").asInt(0));
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case COMBATANT_REORDERED -> {
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
            }
            case SORT_ORDER -> {
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
                        restored.setInitiative(node.get("initiative").asInt(0));
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

    private void rebuildSortOrderForUndo(Map<UUID, Combatant> combatants, Encounter encounter) {
        List<Combatant> list = new ArrayList<>(combatants.values());
        list.sort(Comparator
                .comparingInt(Combatant::getInitiative).reversed()
                .thenComparing(Comparator.comparingInt(Combatant::getTieBreaker).reversed())
                .thenComparing(Combatant::getName));
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
                .comparing(Combatant::getInitiative).reversed()
                .thenComparing(Comparator.comparing(Combatant::getTieBreaker).reversed())
                .thenComparing(Combatant::getName));
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
