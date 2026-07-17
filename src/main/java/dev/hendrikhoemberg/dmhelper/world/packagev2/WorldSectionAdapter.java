package dev.hendrikhoemberg.dmhelper.world.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.FactionClockDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.FactionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.WorldLocationDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.WorldNpcDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.WorldRelationshipDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class WorldSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private static final Logger log = LoggerFactory.getLogger(WorldSectionAdapter.class);

    private final WorldNpcRepository npcRepo;
    private final WorldLocationRepository locationRepo;
    private final FactionRepository factionRepo;
    private final WorldRelationshipRepository relationshipRepo;
    private final FactionClockRepository clockRepo;

    public WorldSectionAdapter(WorldNpcRepository npcRepo,
                                WorldLocationRepository locationRepo,
                                FactionRepository factionRepo,
                                WorldRelationshipRepository relationshipRepo,
                                FactionClockRepository clockRepo) {
        this.npcRepo = npcRepo;
        this.locationRepo = locationRepo;
        this.factionRepo = factionRepo;
        this.relationshipRepo = relationshipRepo;
        this.clockRepo = clockRepo;
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
        List<Faction> factions = factionRepo.findByCampaignIdOrderByNameAscIdAsc(context.campaignId());
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

        List<WorldLocation> locations = locationRepo.findByCampaignIdOrderByNameAscIdAsc(context.campaignId());
        List<WorldLocationDto> locationDtos = locations.stream().map(l -> {
            String key = context.key(CampaignContentType.WORLD_LOCATION, l.getId(), l.getName());
            ContentReference parentRef = l.getParentLocation() != null
                    ? context.packageRef(CampaignContentType.WORLD_LOCATION, l.getParentLocation().getId(), l.getParentLocation().getName())
                    : null;
            ContentReference mapRef = l.getMap() != null
                    ? context.packageRef(CampaignContentType.MAP, l.getMap().getId(), l.getMap().getName())
                    : null;
            ContentReference noteRef = l.getNote() != null
                    ? context.packageRef(CampaignContentType.NOTE, l.getNote().getId(), l.getNote().getTitle())
                    : null;
            return new WorldLocationDto(key, l.getName(), l.getKind().name(),
                    parentRef, mapRef, l.getMapRegionKey(), noteRef,
                    l.getSummary(), l.getServices(), l.getSecrets(),
                    List.of(), List.of(), List.of(),
                    parseTags(l.getTags()), l.getSourceLocator(), l.getCreatedAt());
        }).toList();
        target.worldLocations(locationDtos);

        List<WorldNpc> npcs = npcRepo.findByCampaignIdOrderByNameAscIdAsc(context.campaignId());
        List<WorldNpcDto> npcDtos = npcs.stream().map(n -> {
            String key = context.key(CampaignContentType.WORLD_NPC, n.getId(), n.getName());
            ContentReference factionRef = n.getFaction() != null
                    ? context.packageRef(CampaignContentType.FACTION, n.getFaction().getId(), n.getFaction().getName())
                    : null;
            ContentReference locationRef = n.getLocation() != null
                    ? context.packageRef(CampaignContentType.WORLD_LOCATION, n.getLocation().getId(), n.getLocation().getName())
                    : null;
            ContentReference noteRef = n.getNote() != null
                    ? context.packageRef(CampaignContentType.NOTE, n.getNote().getId(), n.getNote().getTitle())
                    : null;
            ContentReference statblockRef = n.getStatblock() != null
                    ? context.packageRef(CampaignContentType.STATBLOCK, n.getStatblock().getId(), n.getStatblock().getName())
                    : null;
            return new WorldNpcDto(key, n.getName(), n.getRole(),
                    n.getDisposition() != null ? n.getDisposition().name() : null,
                    factionRef, locationRef, noteRef, statblockRef,
                    n.getAppearance(), n.getVoice(), n.getMotivation(), n.getSecret(),
                    n.getInventoryText(), n.getStatus().name(),
                    parseTags(n.getTags()), n.getSourceLocator(), n.getCreatedAt());
        }).toList();
        target.worldNpcs(npcDtos);

        List<WorldRelationship> relationships = relationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(context.campaignId());
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

        List<FactionClock> clocks = clockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(context.campaignId());
        List<FactionClockDto> clockDtos = clocks.stream().map(c -> {
            String key = context.key(CampaignContentType.FACTION_CLOCK, c.getId(), c.getTitle());
            ContentReference factionRef = c.getFaction() != null
                    ? context.packageRef(CampaignContentType.FACTION, c.getFaction().getId(), c.getFaction().getName())
                    : null;
            ContentReference objectiveRef = c.getObjective() != null
                    ? context.packageRef(CampaignContentType.OBJECTIVE, c.getObjective().getId(), c.getObjective().getTitle())
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
                        dev.hendrikhoemberg.dmhelper.notes.data.Note note = context.require(
                                dto.noteRef(), CampaignContentType.NOTE, dev.hendrikhoemberg.dmhelper.notes.data.Note.class);
                        f.setNote(note);
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
                        try {
                            WorldLocation parent = context.require(dto.parentLocationRef(),
                                    CampaignContentType.WORLD_LOCATION, WorldLocation.class);
                            l.setParentLocation(parent);
                        } catch (IllegalStateException e) {
                            log.warn("Location parent not yet resolved for {} (cycle or ordering issue)", dto.key());
                        }
                    }
                    if (dto.mapRef() != null) {
                        dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap map = context.require(
                                dto.mapRef(), CampaignContentType.MAP, dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap.class);
                        l.setMap(map);
                    }
                    if (dto.noteRef() != null) {
                        dev.hendrikhoemberg.dmhelper.notes.data.Note note = context.require(
                                dto.noteRef(), CampaignContentType.NOTE, dev.hendrikhoemberg.dmhelper.notes.data.Note.class);
                        l.setNote(note);
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
                    entity.setDisposition(dev.hendrikhoemberg.dmhelper.world.data.WorldDisposition.valueOf(dto.disposition()));
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
                        Faction f = context.require(dto.factionRef(), CampaignContentType.FACTION, Faction.class);
                        n.setFaction(f);
                    }
                    if (dto.locationRef() != null) {
                        WorldLocation l = context.require(dto.locationRef(),
                                CampaignContentType.WORLD_LOCATION, WorldLocation.class);
                        n.setLocation(l);
                    }
                    if (dto.noteRef() != null) {
                        dev.hendrikhoemberg.dmhelper.notes.data.Note note = context.require(
                                dto.noteRef(), CampaignContentType.NOTE, dev.hendrikhoemberg.dmhelper.notes.data.Note.class);
                        n.setNote(note);
                    }
                    if (dto.statblockRef() != null) {
                        dev.hendrikhoemberg.dmhelper.library.data.StatBlock sb = context.require(
                                dto.statblockRef(), CampaignContentType.STATBLOCK, dev.hendrikhoemberg.dmhelper.library.data.StatBlock.class);
                        n.setStatblock(sb);
                    }
                });
            }
        }

        List<WorldRelationshipDto> relDtos = source.worldRelationships();
        if (relDtos != null) {
            for (WorldRelationshipDto dto : relDtos) {
                WorldRelationship entity = new WorldRelationship();
                entity.setCampaign(context.campaign());
                if (dto.kind() != null) {
                    entity.setKind(dev.hendrikhoemberg.dmhelper.world.data.RelationshipKind.valueOf(dto.kind()));
                }
                entity.setDirected(dto.directed());
                if (dto.knowledge() != null) {
                    entity.setKnowledge(dev.hendrikhoemberg.dmhelper.world.data.RelationshipKnowledge.valueOf(dto.knowledge()));
                }
                if (dto.status() != null) {
                    entity.setStatus(dev.hendrikhoemberg.dmhelper.world.data.RelationshipStatus.valueOf(dto.status()));
                }
                entity.setNotes(dto.notes());
                entity.setSourceLocator(dto.sourceLocator());
                entity.setSortOrder(dto.sortOrder());
                relationshipRepo.save(entity);
                context.register(CampaignContentType.WORLD_RELATIONSHIP, dto.key(), entity, entity.getId());
                context.defer("relationship-refs:" + dto.key(), () -> {
                    WorldRelationship r = context.require(
                            ContentReference.packageRef(CampaignContentType.WORLD_RELATIONSHIP, dto.key()),
                            CampaignContentType.WORLD_RELATIONSHIP, WorldRelationship.class);
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
                });
            }
        }

        List<FactionClockDto> clockDtos = source.factionClocks();
        if (clockDtos != null) {
            for (FactionClockDto dto : clockDtos) {
                FactionClock entity = new FactionClock();
                entity.setCampaign(context.campaign());
                entity.setTitle(dto.title());
                entity.setSegments(dto.segments());
                entity.setFilled(dto.filled());
                entity.setNotes(dto.notes());
                entity.setSourceLocator(dto.sourceLocator());
                entity.setSortOrder(dto.sortOrder());
                clockRepo.save(entity);
                context.register(CampaignContentType.FACTION_CLOCK, dto.key(), entity, entity.getId());
                context.defer("clock-refs:" + dto.key(), () -> {
                    FactionClock c = context.require(
                            ContentReference.packageRef(CampaignContentType.FACTION_CLOCK, dto.key()),
                            CampaignContentType.FACTION_CLOCK, FactionClock.class);
                    if (dto.factionRef() != null) {
                        Faction f = context.require(dto.factionRef(), CampaignContentType.FACTION, Faction.class);
                        c.setFaction(f);
                    }
                    if (dto.objectiveRef() != null) {
                        dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective obj = context.require(
                                dto.objectiveRef(), CampaignContentType.OBJECTIVE, dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective.class);
                        c.setObjective(obj);
                    }
                    if (dto.sceneRef() != null) {
                        dev.hendrikhoemberg.dmhelper.adventure.data.Scene scene = context.require(
                                dto.sceneRef(), CampaignContentType.SCENE, dev.hendrikhoemberg.dmhelper.adventure.data.Scene.class);
                        c.setScene(scene);
                    }
                });
            }
        }
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
