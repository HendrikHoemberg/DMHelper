package dev.hendrikhoemberg.dmhelper.encounter.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
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

    public EncounterService(EncounterRepository encounterRepo, CampaignRepository campaignRepo,
                            EntityManager em, GameMapRepository mapRepo,
                            CombatantRepository combatantRepo, CombatLogEntryRepository combatLogRepo,
                            TokenRepository tokenRepo,
                            PartyMemberRepository partyRepo, StatBlockRepository statBlockRepo) {
        this.encounterRepo = encounterRepo;
        this.campaignRepo = campaignRepo;
        this.em = em;
        this.mapRepo = mapRepo;
        this.combatantRepo = combatantRepo;
        this.combatLogRepo = combatLogRepo;
        this.tokenRepo = tokenRepo;
        this.partyRepo = partyRepo;
        this.statBlockRepo = statBlockRepo;
    }

    public record CreateRequest(String name, UUID mapId) {}

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
                               String notes) {}

    public record CombatantCreateRequest(String name, int maxHp, String kind,
                                         UUID tokenId, UUID statBlockId, UUID partyMemberId) {}

    public record CombatantUpdateRequest(String name, Integer initiative, Integer sortOrder,
                                         Integer currentHp, Integer maxHp, Integer tempHp,
                                         String kind, String groupId, Boolean groupLeader,
                                         Boolean defeated, Boolean hidden,
                                         String concentratingOn, Boolean concentrationCheckPending,
                                         Integer legendaryActionsUsed, Integer legendaryActionsMax,
                                         Integer legendaryResistancesUsed, Integer legendaryResistancesMax,
                                         String notes) {}

    public record PrefillMapRequest(UUID mapId) {}

    public record EncounterDto(UUID id, UUID campaignId, UUID mapId, String name, String status,
                               int round, int activeTurnIndex, int combatantCount,
                               String lairActionName, String lairActionDescription,
                               List<CombatantDto> combatants) {}

    public record CombatLogEntryDto(UUID id, int round, long sequence, String type,
                                     String combatantId, String combatantName, String payload,
                                     Instant createdAt) {}

    static EncounterDto toDto(Encounter e) {
        return new EncounterDto(e.getId(), e.getCampaign().getId(),
                e.getMap() != null ? e.getMap().getId() : null,
                e.getName(), e.getStatus().name(), e.getRound(), e.getActiveTurnIndex(),
                0,
                e.getLairActionName(), e.getLairActionDescription(),
                List.of());
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
                c.getNotes());
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
        return toDto(encounterRepo.save(e));
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
        EncounterDto dto = toDto(encounterRepo.save(e));
        logEntry(id, CombatLogEntry.EntryType.ENCOUNTER_ACTIVATED, "", "");
        return dto;
    }

    public EncounterDto endEncounter(UUID id) {
        Encounter e = findEntityById(id);
        e.setStatus(Encounter.Status.DONE);
        EncounterDto dto = toDto(encounterRepo.save(e));
        logEntry(id, CombatLogEntry.EntryType.ENCOUNTER_ENDED, "", "");
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
            kind = sb.getType();
            c.setStatBlock(sb);
        } else if (req.partyMemberId() != null) {
            if (combatantRepo.findByEncounterIdAndPartyMemberId(encounterId, req.partyMemberId()).isPresent()) {
                throw new IllegalArgumentException("Party member already has a combatant in this encounter");
            }
            PartyMember pm = partyRepo.findById(req.partyMemberId())
                    .orElseThrow(() -> new NotFoundException("Party member not found: " + req.partyMemberId()));
            name = pm.getCharacterName();
            maxHp = pm.getMaxHp();
            currentHp = maxHp;
            kind = "PC";
            c.setPartyMember(pm);
        }

        c.setName(name);
        c.setKind(kind);
        c.setMaxHp(maxHp);
        c.setCurrentHp(currentHp);
        c.setSortOrder((int) combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId).size());

        return toDto(combatantRepo.save(c));
    }

    public void removeCombatant(UUID combatantId) {
        combatantRepo.delete(findCombatantById(combatantId));
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
        return toDto(combatantRepo.save(c));
    }

    public List<CombatantDto> prefillFromMap(UUID encounterId, UUID mapId) {
        Encounter e = findEntityById(encounterId);
        List<Token> tokens = tokenRepo.findByMapIdOrderByNameAsc(mapId);
        for (Token token : tokens) {
            if (combatantRepo.findByEncounterIdAndTokenId(encounterId, token.getId()).isPresent()) {
                continue;
            }
            Combatant c = new Combatant();
            c.setEncounter(e);
            c.setName(token.getName());
            c.setKind(token.getKind());
            c.setMaxHp(token.getMaxHp() != null ? token.getMaxHp() : 10);
            c.setCurrentHp(token.getCurrentHp() != null ? token.getCurrentHp() : c.getMaxHp());
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
            c.setCurrentHp(pm.getMaxHp());
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
        Map<UUID, Combatant> combatants = combatantRepo
                .findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
                .collect(Collectors.toMap(Combatant::getId, Function.identity()));

        List<CombatLogEntry> entriesToKeep = log.subList(0, log.size() - 1);

        reverseLogEntry(lastEntry, combatants);

        for (Combatant c : combatants.values()) {
            combatantRepo.save(c);
        }

        int reconstructedRound = 0;
        int reconstructedTurnIndex = -1;
        for (CombatLogEntry entry : entriesToKeep) {
            switch (entry.getType()) {
                case ROUND_ADVANCE -> reconstructedRound = entry.getRound();
                case TURN_START -> {
                    try {
                        var node = JSON_MAPPER.readTree(entry.getPayload());
                        reconstructedTurnIndex = node.get("activeTurnIndex").asInt(-1);
                    } catch (Exception ignored) {}
                }
                default -> {}
            }
        }

        Encounter encounter = findEntityById(encounterId);
        encounter.setRound(reconstructedRound);
        encounter.setActiveTurnIndex(reconstructedTurnIndex);
        encounter.setLogSequence(entriesToKeep.size());

        combatLogRepo.delete(lastEntry);
    }

    private void reverseLogEntry(CombatLogEntry entry, Map<UUID, Combatant> combatants) {
        UUID combatantId;
        try {
            combatantId = UUID.fromString(entry.getCombatantId());
        } catch (Exception e) {
            return;
        }
        Combatant combatant = combatants.get(combatantId);
        if (combatant == null) return;

        try {
            switch (entry.getType()) {
                case DAMAGE, HEAL -> {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int amount = node.get("amount").asInt(0);
                    combatant.setCurrentHp(combatant.getCurrentHp() - amount);
                }
                case TEMP_HP -> {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int amount = node.get("amount").asInt(0);
                    combatant.setTempHp(combatant.getTempHp() - amount);
                }
                case DEFEATED -> combatant.setDefeated(false);
                case REVIVED -> combatant.setDefeated(true);
                case CONDITION_ADDED -> {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    var conditions = JSON_MAPPER.readValue(combatant.getConditionsJson(),
                            new TypeReference<List<Map<String, Object>>>() {});
                    String name = node.has("name") ? node.get("name").asText() : null;
                    String sourceKey = node.has("sourceKey") ? node.get("sourceKey").asText() : null;
                    conditions.removeIf(c -> {
                        if (name != null && name.equals(c.get("name"))) return true;
                        return sourceKey != null && sourceKey.equals(c.get("sourceKey"));
                    });
                    combatant.setConditionsJson(JSON_MAPPER.writeValueAsString(conditions));
                }
                case CONDITION_REMOVED -> {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    var conditions = JSON_MAPPER.readValue(combatant.getConditionsJson(),
                            new TypeReference<List<Map<String, Object>>>() {});
                    @SuppressWarnings("unchecked")
                    Map<String, Object> condition = JSON_MAPPER.convertValue(node, Map.class);
                    conditions.add(condition);
                    combatant.setConditionsJson(JSON_MAPPER.writeValueAsString(conditions));
                }
                case INITIATIVE_SET -> {
                    // Cannot reverse without knowing previous value
                }
                case COMBATANT_ADDED -> {
                    combatants.remove(combatantId);
                    combatantRepo.delete(combatant);
                }
                case COMBATANT_REMOVED -> {
                    // Cannot re-add without stored combatant data
                }
                default -> {}
            }
        } catch (Exception e) {
            // Ignore errors during undo
        }
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
