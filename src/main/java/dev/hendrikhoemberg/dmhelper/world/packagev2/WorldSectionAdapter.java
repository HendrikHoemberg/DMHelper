package dev.hendrikhoemberg.dmhelper.world.packagev2;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.FactionClockDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.FactionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.WorldLocationDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.WorldLocationTableLinkDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.WorldNpcDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.WorldRelationshipDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableLinkRole;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLink;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.world.data.Faction;
import dev.hendrikhoemberg.dmhelper.world.data.FactionClock;
import dev.hendrikhoemberg.dmhelper.world.data.FactionClockRepository;
import dev.hendrikhoemberg.dmhelper.world.data.FactionRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldNpc;
import dev.hendrikhoemberg.dmhelper.world.data.WorldNpcRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldRelationship;
import dev.hendrikhoemberg.dmhelper.world.data.WorldRelationshipRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class WorldSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final WorldNpcRepository npcRepo;
    private final WorldLocationRepository locationRepo;
    private final FactionRepository factionRepo;
    private final WorldRelationshipRepository relationshipRepo;
    private final FactionClockRepository clockRepo;
    private final StatBlockReferenceResolver statBlockResolver;
    private final WorldLocationTableLinkRepository tableLinkRepo;

    public WorldSectionAdapter(WorldNpcRepository npcRepo,
                                WorldLocationRepository locationRepo,
                                FactionRepository factionRepo,
                                WorldRelationshipRepository relationshipRepo,
                                FactionClockRepository clockRepo,
                                StatBlockReferenceResolver statBlockResolver,
                                WorldLocationTableLinkRepository tableLinkRepo) {
        this.npcRepo = npcRepo;
        this.locationRepo = locationRepo;
        this.factionRepo = factionRepo;
        this.relationshipRepo = relationshipRepo;
        this.clockRepo = clockRepo;
        this.statBlockResolver = statBlockResolver;
        this.tableLinkRepo = tableLinkRepo;
    }

    @Override
    public String sectionName() {
        return "World";
    }

    @Override
    public int order() {
        return 850;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        List<Faction> factions = orEmpty(factionRepo.findByCampaignIdOrderByNameAscIdAsc(context.campaignId()));
        List<FactionDto> factionDtos = factions.stream().map(f -> {
            String key = context.key(CampaignContentType.FACTION, f.getId(), f.getName());
            ContentReference noteRef = f.getNote() != null
                    ? context.packageRef(CampaignContentType.NOTE, f.getNote().getId(), f.getNote().getTitle())
                    : null;
            return new FactionDto(key, f.getName(), f.getGoals(), f.getResources(),
                    f.getReputationNotes(), noteRef, parseTags(f.getTags()),
                    f.getSourceLocator(), f.getCreatedAt());
        }).toList();
        target.factions(factionDtos);

        List<WorldNpc> npcs = orEmpty(npcRepo.findByCampaignIdOrderByNameAscIdAsc(context.campaignId()));
        List<WorldLocation> locations = orEmpty(locationRepo.findByCampaignIdOrderByNameAscIdAsc(context.campaignId()));
        List<WorldLocationDto> locationDtos = locations.stream().map(l -> {
            String key = context.key(CampaignContentType.WORLD_LOCATION, l.getId(), l.getName());
            ContentReference parentRef = l.getParentLocation() != null
                    ? context.packageRef(CampaignContentType.WORLD_LOCATION,
                    l.getParentLocation().getId(), l.getParentLocation().getName())
                    : null;
            ContentReference mapRef = l.getMap() != null
                    ? context.packageRef(CampaignContentType.MAP, l.getMap().getId(), l.getMap().getName())
                    : null;
            ContentReference noteRef = l.getNote() != null
                    ? context.packageRef(CampaignContentType.NOTE, l.getNote().getId(), l.getNote().getTitle())
                    : null;
            List<ContentReference> occupants = npcs.stream()
                    .filter(n -> n.getLocation() != null && l.getId().equals(n.getLocation().getId()))
                    .map(n -> context.packageRef(CampaignContentType.WORLD_NPC, n.getId(), n.getName()))
                    .toList();
            List<ContentReference> encounterRefs = l.getEncounters() == null ? List.of()
                    : l.getEncounters().stream()
                    .map(e -> context.packageRef(CampaignContentType.ENCOUNTER, e.getId(), e.getName()))
                    .toList();
            List<ContentReference> travelRefs = l.getTravelLocations() == null ? List.of()
                    : l.getTravelLocations().stream()
                    .map(t -> context.packageRef(CampaignContentType.WORLD_LOCATION, t.getId(), t.getName()))
                    .toList();
            List<WorldLocationTableLinkDto> tableLinkDtos = tableLinkRepo.findByLocationIdWithTable(l.getId())
                    .stream()
                    .map(link -> new WorldLocationTableLinkDto(
                            link.getRole() != null ? link.getRole().name() : null,
                            context.packageRef(CampaignContentType.ROLLABLE_TABLE,
                                    link.getTable().getId(), link.getTable().getName()),
                            link.getSortOrder()))
                    .toList();
            ContentReference locationCueRef = l.getLocationAudioCue() != null
                    ? context.packageRef(CampaignContentType.AUDIO_CUE,
                            l.getLocationAudioCue().getId(), l.getLocationAudioCue().getName())
                    : null;
            return new WorldLocationDto(key, l.getName(), l.getKind().name(),
                    parentRef, mapRef, l.getMapRegionKey(), noteRef,
                    l.getSummary(), l.getServices(), l.getSecrets(),
                    occupants, encounterRefs, travelRefs,
                    parseTags(l.getTags()), l.getSourceLocator(), l.getCreatedAt(),
                    tableLinkDtos, locationCueRef);
        }).toList();
        target.worldLocations(locationDtos);

        List<WorldNpcDto> npcDtos = npcs.stream().map(n -> {
            String key = context.key(CampaignContentType.WORLD_NPC, n.getId(), n.getName());
            ContentReference factionRef = n.getFaction() != null
                    ? context.packageRef(CampaignContentType.FACTION, n.getFaction().getId(), n.getFaction().getName())
                    : null;
            ContentReference locationRef = n.getLocation() != null
                    ? context.packageRef(CampaignContentType.WORLD_LOCATION,
                    n.getLocation().getId(), n.getLocation().getName())
                    : null;
            ContentReference noteRef = n.getNote() != null
                    ? context.packageRef(CampaignContentType.NOTE, n.getNote().getId(), n.getNote().getTitle())
                    : null;
            ContentReference statblockRef = n.getStatblock() != null
                    ? statBlockResolver.referenceFor(n.getStatblock(), context)
                    : null;
            return new WorldNpcDto(key, n.getName(), n.getRole(),
                    n.getDisposition() != null ? n.getDisposition().name() : null,
                    factionRef, locationRef, noteRef, statblockRef,
                    n.getAppearance(), n.getVoice(), n.getMotivation(), n.getSecret(),
                    n.getInventoryText(), n.getStatus().name(),
                    parseTags(n.getTags()), n.getSourceLocator(), n.getCreatedAt());
        }).toList();
        target.worldNpcs(npcDtos);

        List<WorldRelationship> relationships =
                orEmpty(relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(context.campaignId()));
        List<WorldRelationshipDto> relDtos = relationships.stream().map(r -> {
            String key = context.key(CampaignContentType.WORLD_RELATIONSHIP, r.getId(),
                    r.getKind().name() + ":" + r.getFromType() + "-" + r.getToType());
            ContentReference fromRef = resolveEntityRef(context, r.getFromType(), r.getFromId());
            ContentReference toRef = resolveEntityRef(context, r.getToType(), r.getToId());
            return new WorldRelationshipDto(key, r.getKind().name(),
                    fromRef, toRef, r.isDirected(),
                    r.getKnowledge().name(), r.getStatus().name(),
                    r.getNotes(), r.getSourceLocator(), r.getSortOrder());
        }).toList();
        target.worldRelationships(relDtos);

        List<FactionClock> clocks = orEmpty(clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(context.campaignId()));
        List<FactionClockDto> clockDtos = clocks.stream().map(c -> {
            String key = context.key(CampaignContentType.FACTION_CLOCK, c.getId(), c.getTitle());
            ContentReference factionRef = c.getFaction() != null
                    ? context.packageRef(CampaignContentType.FACTION, c.getFaction().getId(), c.getFaction().getName())
                    : null;
            ContentReference objectiveRef = c.getObjective() != null
                    ? context.packageRef(CampaignContentType.OBJECTIVE,
                    c.getObjective().getId(), c.getObjective().getTitle())
                    : null;
            ContentReference sceneRef = c.getScene() != null
                    ? context.packageRef(CampaignContentType.SCENE, c.getScene().getId(), c.getScene().getTitle())
                    : null;
            return new FactionClockDto(key, factionRef, c.getTitle(),
                    c.getSegments(), c.getFilled(), objectiveRef, sceneRef,
                    c.getNotes(), c.getSourceLocator(), c.getSortOrder());
        }).toList();
        target.factionClocks(clockDtos);
    }

    private static ContentReference resolveEntityRef(CampaignExportContext context, String typeName, UUID entityId) {
        CampaignContentType type = CampaignContentType.valueOf(typeName);
        return context.packageRef(type, entityId, typeName);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<FactionDto> factionDtos = source.factions();
        if (factionDtos != null) {
            for (FactionDto dto : factionDtos) {
                Faction entity = new Faction();
                entity.setCampaign(context.campaign());
                entity.setName(dto.name());
                entity.setGoals(dto.goals());
                entity.setResources(dto.resources());
                entity.setReputationNotes(dto.reputationNotes());
                if (dto.tags() != null) entity.setTags(String.join(",", dto.tags()));
                entity.setSourceLocator(dto.sourceLocator());
                if (dto.createdAt() != null) entity.setCreatedAt(dto.createdAt());
                factionRepo.save(entity);
                context.register(CampaignContentType.FACTION, dto.key(), entity, entity.getId());
                context.defer("faction-note:" + dto.key(), () -> {
                    Faction f = context.require(
                            ContentReference.packageRef(CampaignContentType.FACTION, dto.key()),
                            CampaignContentType.FACTION, Faction.class);
                    if (dto.noteRef() != null) {
                        f.setNote(context.require(dto.noteRef(), CampaignContentType.NOTE,
                                dev.hendrikhoemberg.dmhelper.notes.data.Note.class));
                    }
                });
            }
        }

        List<WorldLocationDto> locationDtos = source.worldLocations();
        if (locationDtos != null) {
            for (WorldLocationDto dto : locationDtos) {
                WorldLocation entity = new WorldLocation();
                entity.setCampaign(context.campaign());
                entity.setName(dto.name());
                entity.setKind(dto.kind() != null
                        ? dev.hendrikhoemberg.dmhelper.world.data.LocationKind.valueOf(dto.kind())
                        : dev.hendrikhoemberg.dmhelper.world.data.LocationKind.SITE);
                entity.setMapRegionKey(dto.mapRegionKey());
                entity.setSummary(dto.summary());
                entity.setServices(dto.services());
                entity.setSecrets(dto.secrets());
                if (dto.tags() != null) entity.setTags(String.join(",", dto.tags()));
                entity.setSourceLocator(dto.sourceLocator());
                if (dto.createdAt() != null) entity.setCreatedAt(dto.createdAt());
                locationRepo.save(entity);
                context.register(CampaignContentType.WORLD_LOCATION, dto.key(), entity, entity.getId());
                context.defer("location-refs:" + dto.key(), () -> {
                    WorldLocation l = context.require(
                            ContentReference.packageRef(CampaignContentType.WORLD_LOCATION, dto.key()),
                            CampaignContentType.WORLD_LOCATION, WorldLocation.class);
                    if (dto.parentLocationRef() != null) {
                        l.setParentLocation(context.require(dto.parentLocationRef(),
                                CampaignContentType.WORLD_LOCATION, WorldLocation.class));
                    }
                    if (dto.mapRef() != null) {
                        l.setMap(context.require(dto.mapRef(), CampaignContentType.MAP,
                                dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap.class));
                    }
                    if (dto.noteRef() != null) {
                        l.setNote(context.require(dto.noteRef(), CampaignContentType.NOTE,
                                dev.hendrikhoemberg.dmhelper.notes.data.Note.class));
                    }
                    if (dto.encounterRefs() != null) {
                        l.getEncounters().clear();
                        for (ContentReference ref : dto.encounterRefs()) {
                            l.getEncounters().add(context.require(ref, CampaignContentType.ENCOUNTER, Encounter.class));
                        }
                    }
                    if (dto.travelLocationRefs() != null) {
                        l.getTravelLocations().clear();
                        for (ContentReference ref : dto.travelLocationRefs()) {
                            l.getTravelLocations().add(context.require(
                                    ref, CampaignContentType.WORLD_LOCATION, WorldLocation.class));
                        }
                    }
                    if (dto.occupantNpcRefs() != null) {
                        for (ContentReference ref : dto.occupantNpcRefs()) {
                            WorldNpc occupant = context.require(
                                    ref, CampaignContentType.WORLD_NPC, WorldNpc.class);
                            if (occupant.getLocation() == null) {
                                occupant.setLocation(l);
                            }
                        }
                    }
                if (dto.tableLinks() != null) {
                    for (WorldLocationTableLinkDto linkDto : dto.tableLinks()) {
                        WorldLocationTableLink link = new WorldLocationTableLink();
                        link.setLocation(l);
                        if (linkDto.tableRef() != null) {
                            RollableTable table = context.require(linkDto.tableRef(),
                                    CampaignContentType.ROLLABLE_TABLE, RollableTable.class);
                            link.setTable(table);
                        }
                        if (linkDto.role() != null) {
                            link.setRole(RollableTableLinkRole.valueOf(linkDto.role()));
                        }
                        link.setSortOrder(linkDto.sortOrder());
                        tableLinkRepo.save(link);
                    }
                }
                if (dto.locationCueRef() != null) {
                    AudioCue cue = context.require(dto.locationCueRef(), CampaignContentType.AUDIO_CUE, AudioCue.class);
                    l.setLocationAudioCue(cue);
                }
                });
            }
        }

        List<WorldNpcDto> npcDtos = source.worldNpcs();
        if (npcDtos != null) {
            for (WorldNpcDto dto : npcDtos) {
                WorldNpc entity = new WorldNpc();
                entity.setCampaign(context.campaign());
                entity.setName(dto.name());
                entity.setRole(dto.role());
                if (dto.disposition() != null) {
                    entity.setDisposition(
                            dev.hendrikhoemberg.dmhelper.world.data.WorldDisposition.valueOf(dto.disposition()));
                }
                entity.setAppearance(dto.appearance());
                entity.setVoice(dto.voice());
                entity.setMotivation(dto.motivation());
                entity.setSecret(dto.secret());
                entity.setInventoryText(dto.inventoryText());
                if (dto.status() != null) {
                    entity.setStatus(dev.hendrikhoemberg.dmhelper.world.data.WorldNpcStatus.valueOf(dto.status()));
                }
                if (dto.tags() != null) entity.setTags(String.join(",", dto.tags()));
                entity.setSourceLocator(dto.sourceLocator());
                if (dto.createdAt() != null) entity.setCreatedAt(dto.createdAt());
                npcRepo.save(entity);
                context.register(CampaignContentType.WORLD_NPC, dto.key(), entity, entity.getId());
                context.defer("npc-refs:" + dto.key(), () -> {
                    WorldNpc n = context.require(
                            ContentReference.packageRef(CampaignContentType.WORLD_NPC, dto.key()),
                            CampaignContentType.WORLD_NPC, WorldNpc.class);
                    if (dto.factionRef() != null) {
                        n.setFaction(context.require(dto.factionRef(), CampaignContentType.FACTION, Faction.class));
                    }
                    if (dto.locationRef() != null) {
                        n.setLocation(context.require(dto.locationRef(),
                                CampaignContentType.WORLD_LOCATION, WorldLocation.class));
                    }
                    if (dto.noteRef() != null) {
                        n.setNote(context.require(dto.noteRef(), CampaignContentType.NOTE,
                                dev.hendrikhoemberg.dmhelper.notes.data.Note.class));
                    }
                    if (dto.statblockRef() != null) {
                        n.setStatblock(statBlockResolver.resolve(dto.statblockRef(), context));
                    }
                });
            }
        }

        List<WorldRelationshipDto> relDtos = source.worldRelationships();
        if (relDtos != null) {
            for (WorldRelationshipDto dto : relDtos) {
                context.defer("relationship-create:" + dto.key(), () -> {
                    WorldRelationship r = new WorldRelationship();
                    r.setCampaign(context.campaign());
                    if (dto.kind() != null) {
                        r.setKind(dev.hendrikhoemberg.dmhelper.world.data.RelationshipKind.valueOf(dto.kind()));
                    }
                    r.setDirected(dto.directed());
                    if (dto.knowledge() != null) {
                        r.setKnowledge(
                                dev.hendrikhoemberg.dmhelper.world.data.RelationshipKnowledge.valueOf(dto.knowledge()));
                    }
                    if (dto.status() != null) {
                        r.setStatus(dev.hendrikhoemberg.dmhelper.world.data.RelationshipStatus.valueOf(dto.status()));
                    }
                    r.setNotes(dto.notes());
                    r.setSourceLocator(dto.sourceLocator());
                    r.setSortOrder(dto.sortOrder());
                    if (dto.fromRef() != null) {
                        Object from = context.require(dto.fromRef(), dto.fromRef().type(), Object.class);
                        r.setFromType(dto.fromRef().type().name());
                        r.setFromId(entityId(from));
                    }
                    if (dto.toRef() != null) {
                        Object to = context.require(dto.toRef(), dto.toRef().type(), Object.class);
                        r.setToType(dto.toRef().type().name());
                        r.setToId(entityId(to));
                    }
                    relationshipRepo.save(r);
                    context.register(CampaignContentType.WORLD_RELATIONSHIP, dto.key(), r, r.getId());
                });
            }
        }

        List<FactionClockDto> clockDtos = source.factionClocks();
        if (clockDtos != null) {
            for (FactionClockDto dto : clockDtos) {
                context.defer("clock-create:" + dto.key(), () -> {
                    FactionClock c = new FactionClock();
                    c.setCampaign(context.campaign());
                    c.setTitle(dto.title());
                    c.setSegments(dto.segments());
                    c.setFilled(dto.filled());
                    c.setNotes(dto.notes());
                    c.setSourceLocator(dto.sourceLocator());
                    c.setSortOrder(dto.sortOrder());
                    if (dto.factionRef() != null) {
                        c.setFaction(context.require(dto.factionRef(), CampaignContentType.FACTION, Faction.class));
                    }
                    if (dto.objectiveRef() != null) {
                        c.setObjective(context.require(dto.objectiveRef(), CampaignContentType.OBJECTIVE,
                                dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective.class));
                    }
                    if (dto.sceneRef() != null) {
                        c.setScene(context.require(dto.sceneRef(), CampaignContentType.SCENE,
                                dev.hendrikhoemberg.dmhelper.adventure.data.Scene.class));
                    }
                    clockRepo.save(c);
                    context.register(CampaignContentType.FACTION_CLOCK, dto.key(), c, c.getId());
                });
            }
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (var part : tags.split(",")) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static UUID entityId(Object entity) {
        if (entity instanceof WorldNpc n) return n.getId();
        if (entity instanceof Faction f) return f.getId();
        if (entity instanceof WorldLocation l) return l.getId();
        throw new IllegalStateException(
                "Cannot resolve package entity id for " + entity.getClass().getName());
    }
}
