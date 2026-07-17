package dev.hendrikhoemberg.dmhelper.encounter.packagev2;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CombatLogEntryDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CombatantDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.EncounterDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.WaveDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWave;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWaveRepository;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EncounterSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final EncounterRepository encounterRepository;
    private final CombatantRepository combatantRepository;
    private final CombatLogEntryRepository combatLogEntryRepository;
    private final EncounterWaveRepository waveRepository;
    private final StatBlockReferenceResolver statBlockResolver;

    public EncounterSectionAdapter(EncounterRepository encounterRepository,
                                    CombatantRepository combatantRepository,
                                    CombatLogEntryRepository combatLogEntryRepository,
                                    EncounterWaveRepository waveRepository,
                                    StatBlockReferenceResolver statBlockResolver) {
        this.encounterRepository = encounterRepository;
        this.combatantRepository = combatantRepository;
        this.combatLogEntryRepository = combatLogEntryRepository;
        this.waveRepository = waveRepository;
        this.statBlockResolver = statBlockResolver;
    }

    @Override
    public String sectionName() {
        return "Encounter";
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var encounters = encounterRepository.findByCampaignIdOrderByNameAsc(context.campaignId());
        boolean includeCombatLog = context.options().includeCombatLog();

        List<EncounterDto> dtos = encounters.stream()
                .map(e -> exportEncounter(e, context, includeCombatLog))
                .toList();
        target.encounters(dtos);
    }

    private EncounterDto exportEncounter(Encounter encounter, CampaignExportContext context, boolean includeCombatLog) {
        String key = context.key(CampaignContentType.ENCOUNTER, encounter.getId(), encounter.getName());

        var combatants = combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId());
        List<CombatantDto> combatantDtos = combatants.stream()
                .map(c -> exportCombatant(c, context))
                .toList();

        Map<String, String> idToKey = new LinkedHashMap<>();
        for (Combatant c : combatants) {
            String combatantKey = context.key(CampaignContentType.COMBATANT, c.getId(), c.getName());
            idToKey.put(c.getId().toString(), combatantKey);
        }

        List<CombatLogEntryDto> combatLogDtos = List.of();
        if (includeCombatLog) {
            var logEntries = combatLogEntryRepository.findByEncounterIdOrderBySequenceAsc(encounter.getId());
            combatLogDtos = logEntries.stream()
                    .map(entry -> exportCombatLogEntry(entry, context, idToKey))
                    .toList();
        }

        ContentReference mapRef = encounter.getMap() != null
                ? context.packageRef(CampaignContentType.MAP, encounter.getMap().getId(), encounter.getMap().getName())
                : null;

        var waves = waveRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId());
        List<WaveDto> waveDtos = waves.stream()
                .map(w -> exportWave(w, context))
                .toList();

        CampaignManifestV2.EncounterPrep prep = parsePrep(encounter);
        CampaignManifestV2.EncounterRewards rewards = parseRewards(encounter);

        return new EncounterDto(
                key, encounter.getName(), combatantDtos,
                encounter.getStatus().name(), encounter.getRound(),
                encounter.getActiveTurnIndex(), encounter.getLogSequence(),
                encounter.getLairActionName(), encounter.getLairActionDescription(),
                mapRef, encounter.isLairActionTriggered(), combatLogDtos,
                prep, rewards, waveDtos
        );
    }

    private WaveDto exportWave(EncounterWave wave, CampaignExportContext context) {
        // Prefer the stable wave_key as the package-key seed so exports stay identity-stable.
        String keySeed = wave.getWaveKey() != null && !wave.getWaveKey().isBlank()
                ? wave.getWaveKey() : wave.getName();
        String key = context.key(CampaignContentType.ENCOUNTER_WAVE, wave.getId(), keySeed);
        return new WaveDto(
                key, wave.getName(), wave.getSortOrder(),
                wave.getStatus().name(), wave.getTriggerKind().name(),
                wave.getTriggerValue(), wave.getNotes()
        );
    }

    private CampaignManifestV2.EncounterPrep parsePrep(Encounter encounter) {
        if (encounter.getPrepJson() == null || encounter.getPrepJson().isBlank()) return null;
        try {
            return MAPPER.readValue(encounter.getPrepJson(), CampaignManifestV2.EncounterPrep.class);
        } catch (Exception e) {
            return null;
        }
    }

    private CampaignManifestV2.EncounterRewards parseRewards(Encounter encounter) {
        if (encounter.getRewardsJson() == null || encounter.getRewardsJson().isBlank()) return null;
        try {
            return MAPPER.readValue(encounter.getRewardsJson(), CampaignManifestV2.EncounterRewards.class);
        } catch (Exception e) {
            return null;
        }
    }

    private CombatantDto exportCombatant(Combatant combatant, CampaignExportContext context) {
        String key = context.key(CampaignContentType.COMBATANT, combatant.getId(), combatant.getName());

        ContentReference tokenRef = combatant.getToken() != null
                ? context.packageRef(CampaignContentType.TOKEN, combatant.getToken().getId(), combatant.getToken().getName())
                : null;
        ContentReference statBlockRef = combatant.getStatBlock() != null
                ? statBlockResolver.referenceFor(combatant.getStatBlock(), context)
                : null;
        ContentReference partyMemberRef = combatant.getPartyMember() != null
                ? context.packageRef(CampaignContentType.PARTY_MEMBER, combatant.getPartyMember().getId(), combatant.getPartyMember().getCharacterName())
                : null;

        String waveKey = null;
        if (combatant.getWave() != null) {
            String seed = combatant.getWave().getWaveKey() != null && !combatant.getWave().getWaveKey().isBlank()
                    ? combatant.getWave().getWaveKey() : combatant.getWave().getName();
            waveKey = context.key(CampaignContentType.ENCOUNTER_WAVE, combatant.getWave().getId(), seed);
        }

        return new CombatantDto(
                key, combatant.getName(), combatant.getInitiative(),
                combatant.getTieBreaker(), combatant.getSortOrder(),
                combatant.getMaxHp(), combatant.getCurrentHp(), combatant.getTempHp(),
                combatant.getKind(), combatant.getGroupId(), combatant.isGroupLeader(),
                tokenRef, statBlockRef, partyMemberRef,
                combatant.isDefeated(), combatant.isHidden(),
                combatant.getConditionsJson(), combatant.getConcentratingOn(),
                combatant.isConcentrationCheckPending(),
                combatant.getLegendaryActionsUsed(), combatant.getLegendaryResistancesUsed(),
                combatant.getLegendaryActionsMax(), combatant.getLegendaryResistancesMax(),
                combatant.getRechargedAbilities(), combatant.getNotes(),
                waveKey, combatant.getStartX(), combatant.getStartY(),
                combatant.getPlacementRegionKey()
        );
    }

    private CombatLogEntryDto exportCombatLogEntry(CombatLogEntry entry, CampaignExportContext context,
                                                    Map<String, String> idToKey) {
        String key = context.key(CampaignContentType.COMBAT_LOG_ENTRY, entry.getId(), entry.getType().name());

        JsonNode payload;
        if (entry.getPayload() != null && !entry.getPayload().isBlank()) {
            try {
                JsonNode raw = MAPPER.readTree(entry.getPayload());
                payload = CombatLogPayloadCodec.toPackage(idToKey, raw);
            } catch (Exception e) {
                payload = MAPPER.createObjectNode();
            }
        } else {
            payload = MAPPER.createObjectNode();
        }

        ContentReference combatantRef = null;
        if (entry.getCombatantId() != null && !entry.getCombatantId().isBlank()) {
            String combatantKey = idToKey.get(entry.getCombatantId());
            if (combatantKey != null) {
                combatantRef = ContentReference.packageRef(CampaignContentType.COMBATANT, combatantKey);
            }
        }

        return new CombatLogEntryDto(
                key, entry.getRound(), entry.getSequence(),
                entry.getType().name(), combatantRef, payload, entry.getCreatedAt()
        );
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<EncounterDto> dtos = source.encounters();
        if (dtos == null) return;

        var campaign = context.campaign();

        List<ImportedEncounter> imported = new ArrayList<>();

        for (EncounterDto dto : dtos) {
            var encounter = new Encounter();
            encounter.setCampaign(campaign);
            encounter.setName(dto.name());
            encounter.setEncounterKey(dto.key());
            encounter.setStatus(dto.status() != null ? Encounter.Status.valueOf(dto.status()) : Encounter.Status.PLANNED);
            encounter.setRound(dto.round());
            encounter.setActiveTurnIndex(dto.activeTurnIndex());
            encounter.setLogSequence(dto.logSequence());
            encounter.setLairActionName(dto.lairActionName());
                encounter.setLairActionDescription(dto.lairActionDescription());
            encounter.setLairActionTriggered(dto.lairActionTriggered());
            if (dto.prep() != null) {
                try {
                    encounter.setPrepJson(MAPPER.writeValueAsString(dto.prep()));
                } catch (Exception ignored) {}
            }
            if (dto.rewards() != null) {
                try {
                    encounter.setRewardsJson(MAPPER.writeValueAsString(dto.rewards()));
                } catch (Exception ignored) {}
            }
            if (dto.mapRef() != null) {
                context.defer("encounter map " + dto.key(), () -> {
                    var map = context.require(dto.mapRef(), CampaignContentType.MAP,
                            dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap.class);
                    encounter.setMap(map);
                });
            }
            encounterRepository.save(encounter);
            context.register(CampaignContentType.ENCOUNTER, dto.key(), encounter, encounter.getId());
            imported.add(new ImportedEncounter(encounter, dto));
        }

        for (var entry : imported) {
            var dto = entry.dto;
            if (dto.waves() != null) {
                for (WaveDto wDto : dto.waves()) {
                    var wave = new EncounterWave();
                    wave.setEncounter(entry.encounter);
                    wave.setWaveKey(wDto.key());
                    wave.setName(wDto.name());
                    wave.setSortOrder(wDto.sortOrder());
                    wave.setStatus(dev.hendrikhoemberg.dmhelper.encounter.data.WaveStatus.valueOf(wDto.status()));
                    wave.setTriggerKind(dev.hendrikhoemberg.dmhelper.encounter.data.WaveTriggerKind.valueOf(wDto.triggerKind()));
                    wave.setTriggerValue(wDto.triggerValue());
                    wave.setNotes(wDto.notes());
                    waveRepository.save(wave);
                    context.register(CampaignContentType.ENCOUNTER_WAVE, wDto.key(), wave, wave.getId());
                }
            }
        }

        for (var entry : imported) {
            var dto = entry.dto;
            if (dto.combatants() == null) continue;
            for (CombatantDto cDto : dto.combatants()) {
                var combatant = new Combatant();
                combatant.setEncounter(entry.encounter);
                combatant.setName(cDto.name());
                combatant.setInitiative(cDto.initiative());
                combatant.setTieBreaker(cDto.tieBreaker());
                combatant.setSortOrder(cDto.sortOrder());
                combatant.setMaxHp(cDto.maxHp());
                combatant.setCurrentHp(cDto.currentHp());
                combatant.setTempHp(cDto.tempHp());
                combatant.setKind(cDto.kind());
                combatant.setGroupId(cDto.groupId());
                combatant.setGroupLeader(cDto.groupLeader());
                combatant.setDefeated(cDto.defeated());
                combatant.setHidden(cDto.hidden());
                combatant.setConditionsJson(cDto.conditionsJson());
                combatant.setConcentratingOn(cDto.concentratingOn());
                combatant.setConcentrationCheckPending(cDto.concentrationCheckPending());
                combatant.setLegendaryActionsUsed(cDto.legendaryActionsUsed());
                combatant.setLegendaryResistancesUsed(cDto.legendaryResistancesUsed());
                combatant.setLegendaryActionsMax(cDto.legendaryActionsMax());
                combatant.setLegendaryResistancesMax(cDto.legendaryResistancesMax());
                combatant.setRechargedAbilities(cDto.rechargedAbilities());
                combatant.setNotes(cDto.notes());
                combatant.setStartX(cDto.startX());
                combatant.setStartY(cDto.startY());
                combatant.setPlacementRegionKey(cDto.placementRegionKey());
                if (cDto.waveKey() != null) {
                    context.defer("combatant wave " + cDto.key(), () -> {
                        var wave = context.require(
                                ContentReference.packageRef(CampaignContentType.ENCOUNTER_WAVE, cDto.waveKey()),
                                CampaignContentType.ENCOUNTER_WAVE, EncounterWave.class);
                        combatant.setWave(wave);
                    });
                }
                if (cDto.tokenRef() != null) {
                    context.defer("combatant token " + cDto.key(), () -> {
                        var token = context.require(cDto.tokenRef(), CampaignContentType.TOKEN,
                                dev.hendrikhoemberg.dmhelper.gamemap.data.Token.class);
                        combatant.setToken(token);
                    });
                }
                if (cDto.statBlockRef() != null) {
                    context.defer("combatant statblock " + cDto.key(), () -> {
                        combatant.setStatBlock(statBlockResolver.resolve(cDto.statBlockRef(), context));
                    });
                }
                if (cDto.partyMemberRef() != null) {
                    context.defer("combatant partyMember " + cDto.key(), () -> {
                        var pm = context.require(cDto.partyMemberRef(), CampaignContentType.PARTY_MEMBER,
                                dev.hendrikhoemberg.dmhelper.party.data.PartyMember.class);
                        combatant.setPartyMember(pm);
                    });
                }
                combatantRepository.save(combatant);
                context.register(CampaignContentType.COMBATANT, cDto.key(), combatant, combatant.getId());
                entry.combatants.put(cDto.key(), combatant);
            }
        }

        for (var entry : imported) {
            var dto = entry.dto;
            if (dto.combatLog() == null || dto.combatLog().isEmpty()) continue;

            Map<String, UUID> keyToId = new LinkedHashMap<>();
            entry.combatants.forEach((key, combatant) -> keyToId.put(key, combatant.getId()));

            for (CombatLogEntryDto logDto : dto.combatLog()) {
                var logEntry = new CombatLogEntry();
                logEntry.setEncounter(entry.encounter);
                logEntry.setRound(logDto.round());
                logEntry.setSequence(logDto.sequence());
                logEntry.setType(CombatLogEntry.EntryType.valueOf(logDto.type()));
                logEntry.setCreatedAt(logDto.createdAt());
                logEntry.setCombatantId("");

                if (logDto.combatantRef() != null) {
                    UUID combatantId = keyToId.get(logDto.combatantRef().key());
                    if (combatantId != null) {
                        logEntry.setCombatantId(combatantId.toString());
                    }
                }

                JsonNode payload = logDto.payload();
                if (payload != null && !payload.isEmpty()) {
                    JsonNode localPayload = CombatLogPayloadCodec.toLocal(keyToId, payload);
                    logEntry.setPayload(localPayload.toString());
                }

                var savedLogEntry = combatLogEntryRepository.save(logEntry);
                context.register(CampaignContentType.COMBAT_LOG_ENTRY, logDto.key(),
                        savedLogEntry, savedLogEntry.getId());
            }
        }
    }

    private static class ImportedEncounter {
        final Encounter encounter;
        final EncounterDto dto;
        final Map<String, Combatant> combatants = new LinkedHashMap<>();

        ImportedEncounter(Encounter encounter, EncounterDto dto) {
            this.encounter = encounter;
            this.dto = dto;
        }
    }
}
