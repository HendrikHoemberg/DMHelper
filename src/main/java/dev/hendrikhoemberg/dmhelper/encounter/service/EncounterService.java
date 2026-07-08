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
import java.util.ArrayList;
import java.util.Comparator;
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

    public record InitiativeRequest(int initiative) {}

    public record ReorderRequest(List<UUID> orderedIds) {}

    public record ActiveTurnRequest(UUID combatantId) {}

    public record HpRequest(Integer currentHp, Integer tempHp) {}
    public record DamageRequest(int amount) {}
    public record DefeatedRequest(boolean defeated) {}
    public record ConditionToggleRequest(String sourceKey, int durationRounds) {}

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

    public CombatantDto setInitiative(UUID combatantId, int initiative) {
        Combatant c = findCombatantById(combatantId);
        int previous = c.getInitiative();
        c.setInitiative(initiative);
        Combatant saved = combatantRepo.save(c);
        resortCombatants(c.getEncounter().getId());

        try {
            String payload = JSON_MAPPER.writeValueAsString(
                    Map.of("initiative", initiative, "previousInitiative", previous));
            logEntry(c.getEncounter().getId(), CombatLogEntry.EntryType.INITIATIVE_SET,
                    combatantId.toString(), payload);
        } catch (Exception e) { /* log failure is non-fatal */ }

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
                int roll = new java.util.Random().nextInt(20) + 1;
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
        CombatLogEntry.EntryType type = amount < 0 ? CombatLogEntry.EntryType.DAMAGE : CombatLogEntry.EntryType.HEAL;
        try {
            String payload = JSON_MAPPER.writeValueAsString(Map.of("amount", amount));
            logEntry(c.getEncounter().getId(), type, combatantId.toString(), payload);
        } catch (Exception e) { /* ignore */ }
        return toDto(saved);
    }

    public CombatantDto setHp(UUID combatantId, Integer currentHp, Integer tempHp) {
        Combatant c = findCombatantById(combatantId);
        if (currentHp != null) c.setCurrentHp(currentHp);
        if (tempHp != null) c.setTempHp(tempHp);
        return toDto(combatantRepo.save(c));
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
            List<ConditionStateDto> updated = conditions.stream()
                .filter(cond -> {
                    if (cond.durationRounds() <= 0) return true;
                    int elapsed = encounter.getRound() - cond.appliedInRound();
                    return elapsed < cond.durationRounds();
                })
                .collect(Collectors.toList());
            if (updated.size() != conditions.size()) {
                try {
                    c.setConditionsJson(JSON_MAPPER.writeValueAsString(updated));
                } catch (Exception e) { /* ignore */ }
                combatantRepo.save(c);
                logEntry(encounterId, CombatLogEntry.EntryType.CONDITION_TICKED,
                    c.getId().toString(), "{\"remaining\":" + updated.size() + "}");
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
        logEntry(encounterId, CombatLogEntry.EntryType.COMBATANT_REORDERED, "", "{}");
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
    }

    public EncounterDto nextTurn(UUID encounterId) {
        Encounter encounter = findEntityById(encounterId);
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);

        if (combatants.isEmpty()) {
            throw new IllegalStateException("No combatants in encounter");
        }

        int idx = encounter.getActiveTurnIndex();
        int loopCount = 0;

        do {
            idx = (idx + 1) % combatants.size();
            loopCount++;
        } while (combatants.get(idx).isDefeated() && loopCount < combatants.size());

        if (loopCount >= combatants.size()) {
            throw new IllegalStateException("All combatants defeated");
        }

        if (idx == 0) {
            encounter.setRound(encounter.getRound() + 1);
            logEntry(encounterId, CombatLogEntry.EntryType.ROUND_ADVANCE, "",
                    "{\"round\":" + encounter.getRound() + "}");
            resetLegendaryActions(encounterId);
            tickConditionDurations(encounterId);
        }

        encounter.setActiveTurnIndex(idx);
        encounterRepo.save(encounter);

        logEntry(encounterId, CombatLogEntry.EntryType.TURN_START,
                combatants.get(idx).getId().toString(),
                "{\"activeTurnIndex\":" + idx + "}");

        return toDto(encounter);
    }

    public EncounterDto previousTurn(UUID encounterId) {
        Encounter encounter = findEntityById(encounterId);
        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounterId);

        if (combatants.isEmpty() || encounter.getActiveTurnIndex() < 0) {
            return toDto(encounter);
        }

        int idx = encounter.getActiveTurnIndex();
        if (idx >= combatants.size()) {
            idx = combatants.size() - 1;
        }

        int loopCount = 0;
        do {
            if (idx == 0) {
                idx = combatants.size() - 1;
                if (encounter.getRound() > 1) {
                    encounter.setRound(encounter.getRound() - 1);
                }
            } else {
                idx--;
            }
            loopCount++;
        } while (combatants.get(idx).isDefeated() && loopCount < combatants.size());

        if (loopCount >= combatants.size()) {
            return toDto(encounter);
        }

        encounter.setActiveTurnIndex(idx);
        encounterRepo.save(encounter);
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

    private void resetLegendaryActions(UUID encounterId) {
        // Will be implemented in Step 7
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
        Encounter encounter = findEntityById(encounterId);

        Map<UUID, Combatant> combatants = combatantRepo
                .findByEncounterIdOrderBySortOrderAsc(encounterId).stream()
                .collect(Collectors.toMap(Combatant::getId, Function.identity()));

        int reconstructedRound = encounter.getRound();
        int reconstructedTurnIndex = encounter.getActiveTurnIndex();

        List<CombatLogEntry> entriesToKeep = log.subList(0, log.size() - 1);
        for (CombatLogEntry entry : entriesToKeep) {
            switch (entry.getType()) {
                case ROUND_ADVANCE -> reconstructedRound = entry.getRound();
                case TURN_START -> {
                    try {
                        var node = JSON_MAPPER.readTree(entry.getPayload());
                        reconstructedTurnIndex = node.get("activeTurnIndex").asInt(-1);
                    } catch (Exception e) {
                        // ignore malformed turn payload
                    }
                }
                default -> {}
            }
        }

        reverseLogEntry(lastEntry, combatants);

        for (Combatant c : combatants.values()) {
            combatantRepo.save(c);
        }

        encounter.setRound(reconstructedRound);
        encounter.setActiveTurnIndex(reconstructedTurnIndex);
        encounter.setLogSequence(entriesToKeep.size());

        combatLogRepo.delete(lastEntry);
    }

    private void reverseLogEntry(CombatLogEntry entry, Map<UUID, Combatant> combatants) {
        UUID combatantId;
        try {
            combatantId = UUID.fromString(entry.getCombatantId());
        } catch (IllegalArgumentException e) {
            return;
        }
        Combatant combatant = combatants.get(combatantId);

        switch (entry.getType()) {
            case DAMAGE -> {
                if (combatant == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int amount = node.get("amount").asInt(0);
                    combatant.setCurrentHp(combatant.getCurrentHp() - amount);
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case HEAL -> {
                if (combatant == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int amount = node.get("amount").asInt(0);
                    combatant.setCurrentHp(combatant.getCurrentHp() - amount);
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case TEMP_HP -> {
                if (combatant == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int amount = node.get("amount").asInt(0);
                    combatant.setTempHp(Math.max(0, combatant.getTempHp() - amount));
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case DEFEATED -> {
                if (combatant != null) combatant.setDefeated(false);
            }
            case REVIVED -> {
                if (combatant != null) combatant.setDefeated(true);
            }
            case CONDITION_ADDED -> {
                if (combatant == null) return;
                try {
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
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case CONDITION_REMOVED -> {
                if (combatant == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    var conditions = JSON_MAPPER.readValue(combatant.getConditionsJson(),
                            new TypeReference<List<Map<String, Object>>>() {});
                    @SuppressWarnings("unchecked")
                    Map<String, Object> condition = JSON_MAPPER.convertValue(node, Map.class);
                    conditions.add(condition);
                    combatant.setConditionsJson(JSON_MAPPER.writeValueAsString(conditions));
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case INITIATIVE_SET -> {
                if (combatant == null) return;
                try {
                    var node = JSON_MAPPER.readTree(entry.getPayload());
                    int previousValue = node.has("previousInitiative") ? node.get("previousInitiative").asInt() : combatant.getInitiative();
                    combatant.setInitiative(previousValue);
                } catch (Exception e) {
                    // ignore malformed payload
                }
            }
            case COMBATANT_ADDED -> {
                if (combatant != null) {
                    combatants.remove(combatantId);
                    combatantRepo.delete(combatant);
                }
            }
            case COMBATANT_REMOVED, COMBATANT_REORDERED, CONCENTRATION_SET,
                 CONCENTRATION_LOST, CONCENTRATION_CHECK, RECHARGE,
                 LEGENDARY_ACTION, LEGENDARY_RESISTANCE, CONDITION_TICKED,
                 GROUP_SPLIT, LAIR_ACTION, NOTE,
                 ENCOUNTER_ACTIVATED, ENCOUNTER_ENDED,
                 INITIATIVE_SET, TURN_START, TURN_END, ROUND_ADVANCE -> {
                // Not reversible via simple mutation — skip
            }
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
