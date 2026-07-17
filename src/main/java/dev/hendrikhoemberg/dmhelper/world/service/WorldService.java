package dev.hendrikhoemberg.dmhelper.world.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjective;
import dev.hendrikhoemberg.dmhelper.world.data.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class WorldService {

    public record NpcCommand(
            String name, String role, WorldDisposition disposition,
            UUID factionId, UUID locationId, UUID noteId, UUID statblockId,
            String appearance, String voice, String motivation, String secret,
            String inventoryText, WorldNpcStatus status, String tags, String sourceLocator) {}

    public record LocationCommand(
            String name, LocationKind kind, UUID parentLocationId, UUID mapId, String mapRegionKey,
            UUID noteId, String summary, String services, String secrets,
            List<UUID> encounterIds, List<UUID> travelLocationIds,
            String tags, String sourceLocator) {}

    public record FactionCommand(
            String name, String goals, String resources, String reputationNotes,
            UUID noteId, String tags, String sourceLocator) {}

    public record RelationshipCommand(
            RelationshipKind kind, String fromType, UUID fromId, String toType, UUID toId,
            boolean directed, RelationshipKnowledge knowledge, RelationshipStatus status,
            String notes, String sourceLocator, int sortOrder) {}

    public record ClockCommand(
            UUID factionId, String title, int segments, int filled, UUID objectiveId, UUID sceneId,
            String notes, String sourceLocator, int sortOrder) {}

    private final WorldNpcRepository npcRepository;
    private final WorldLocationRepository locationRepository;
    private final FactionRepository factionRepository;
    private final WorldRelationshipRepository relationshipRepository;
    private final FactionClockRepository clockRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignPackageKeyService packageKeys;
    private final WorldLocationCycleValidator cycleValidator;
    private final WorldReferenceCleaner refCleaner;
    private final NoteRepository noteRepository;
    private final GameMapRepository gameMapRepository;
    private final StatBlockRepository statBlockRepository;

    public WorldService(WorldNpcRepository npcRepository,
                        WorldLocationRepository locationRepository,
                        FactionRepository factionRepository,
                        WorldRelationshipRepository relationshipRepository,
                        FactionClockRepository clockRepository,
                        CampaignRepository campaignRepository,
                        CampaignPackageKeyService packageKeys,
                        WorldLocationCycleValidator cycleValidator,
                        WorldReferenceCleaner refCleaner,
                        NoteRepository noteRepository,
                        GameMapRepository gameMapRepository,
                        StatBlockRepository statBlockRepository) {
        this.npcRepository = npcRepository;
        this.locationRepository = locationRepository;
        this.factionRepository = factionRepository;
        this.relationshipRepository = relationshipRepository;
        this.clockRepository = clockRepository;
        this.campaignRepository = campaignRepository;
        this.packageKeys = packageKeys;
        this.cycleValidator = cycleValidator;
        this.refCleaner = refCleaner;
        this.noteRepository = noteRepository;
        this.gameMapRepository = gameMapRepository;
        this.statBlockRepository = statBlockRepository;
    }

    // ---- NPC CRUD ----

    public WorldNpc createNpc(UUID campaignId, NpcCommand cmd) {
        Campaign campaign = findCampaign(campaignId);
        validateFactionInCampaign(campaignId, cmd.factionId());
        validateLocationInCampaign(campaignId, cmd.locationId());
        validateNoteInCampaign(campaignId, cmd.noteId());
        validateStatblockInCampaign(campaignId, cmd.statblockId());
        WorldNpc npc = new WorldNpc();
        npc.setCampaign(campaign);
        applyNpcCommand(npc, cmd);
        npc = npcRepository.save(npc);
        packageKeys.getOrCreate(campaignId, CampaignContentType.WORLD_NPC, npc.getId(), npc.getName());
        return npc;
    }

    public WorldNpc updateNpc(UUID campaignId, UUID npcId, NpcCommand cmd) {
        WorldNpc npc = findNpcInCampaign(campaignId, npcId);
        validateFactionInCampaign(campaignId, cmd.factionId());
        validateLocationInCampaign(campaignId, cmd.locationId());
        validateNoteInCampaign(campaignId, cmd.noteId());
        validateStatblockInCampaign(campaignId, cmd.statblockId());
        applyNpcCommand(npc, cmd);
        return npcRepository.save(npc);
    }

    public void deleteNpc(UUID campaignId, UUID npcId) {
        WorldNpc npc = findNpcInCampaign(campaignId, npcId);
        npcRepository.delete(npc);
        packageKeys.deleteBindings(campaignId, CampaignContentType.WORLD_NPC, List.of(npcId));
    }

    @Transactional(readOnly = true)
    public WorldNpc getNpc(UUID campaignId, UUID npcId) {
        return findNpcInCampaign(campaignId, npcId);
    }

    @Transactional(readOnly = true)
    public List<WorldNpc> getNpcs(UUID campaignId) {
        return npcRepository.findByCampaignIdOrderByNameAscIdAsc(campaignId);
    }

    private void applyNpcCommand(WorldNpc npc, NpcCommand cmd) {
        if (cmd.name() != null) npc.setName(cmd.name());
        if (cmd.role() != null) npc.setRole(cmd.role());
        if (cmd.disposition() != null) npc.setDisposition(cmd.disposition());
        if (cmd.factionId() != null) {
            Faction f = new Faction();
            f.setId(cmd.factionId());
            npc.setFaction(f);
        } else {
            npc.setFaction(null);
        }
        if (cmd.locationId() != null) {
            WorldLocation l = new WorldLocation();
            l.setId(cmd.locationId());
            npc.setLocation(l);
        } else {
            npc.setLocation(null);
        }
        if (cmd.noteId() != null) {
            Note n = new Note();
            n.setId(cmd.noteId());
            npc.setNote(n);
        } else {
            npc.setNote(null);
        }
        if (cmd.statblockId() != null) {
            StatBlock s = new StatBlock();
            s.setId(cmd.statblockId());
            npc.setStatblock(s);
        } else {
            npc.setStatblock(null);
        }
        if (cmd.appearance() != null) npc.setAppearance(cmd.appearance());
        if (cmd.voice() != null) npc.setVoice(cmd.voice());
        if (cmd.motivation() != null) npc.setMotivation(cmd.motivation());
        if (cmd.secret() != null) npc.setSecret(cmd.secret());
        if (cmd.inventoryText() != null) npc.setInventoryText(cmd.inventoryText());
        if (cmd.status() != null) npc.setStatus(cmd.status());
        if (cmd.tags() != null) npc.setTags(cmd.tags());
        if (cmd.sourceLocator() != null) npc.setSourceLocator(cmd.sourceLocator());
    }

    private WorldNpc findNpcInCampaign(UUID campaignId, UUID npcId) {
        return npcRepository.findByIdAndCampaignId(npcId, campaignId)
                .orElseThrow(() -> new NotFoundException("NPC not found in campaign"));
    }

    // ---- Location CRUD ----

    public WorldLocation createLocation(UUID campaignId, LocationCommand cmd) {
        Campaign campaign = findCampaign(campaignId);
        cycleValidator.assertNoCycle(null, cmd.parentLocationId());
        validateLocationInCampaign(campaignId, cmd.parentLocationId());
        WorldLocation location = new WorldLocation();
        location.setCampaign(campaign);
        applyLocationCommand(location, cmd);
        location = locationRepository.save(location);
        packageKeys.getOrCreate(campaignId, CampaignContentType.WORLD_LOCATION, location.getId(), location.getName());
        return location;
    }

    public WorldLocation updateLocation(UUID campaignId, UUID locationId, LocationCommand cmd) {
        WorldLocation location = findLocationInCampaign(campaignId, locationId);
        cycleValidator.assertNoCycle(locationId, cmd.parentLocationId());
        validateLocationInCampaign(campaignId, cmd.parentLocationId());
        applyLocationCommand(location, cmd);
        return locationRepository.save(location);
    }

    public void deleteLocation(UUID campaignId, UUID locationId) {
        WorldLocation location = findLocationInCampaign(campaignId, locationId);
        refCleaner.onLocationDelete(campaignId, locationId);
        locationRepository.delete(location);
        packageKeys.deleteBindings(campaignId, CampaignContentType.WORLD_LOCATION, List.of(locationId));
    }

    @Transactional(readOnly = true)
    public WorldLocation getLocation(UUID campaignId, UUID locationId) {
        return findLocationInCampaign(campaignId, locationId);
    }

    @Transactional(readOnly = true)
    public List<WorldLocation> getLocations(UUID campaignId) {
        return locationRepository.findByCampaignIdOrderByNameAscIdAsc(campaignId);
    }

    private void applyLocationCommand(WorldLocation location, LocationCommand cmd) {
        if (cmd.name() != null) location.setName(cmd.name());
        if (cmd.kind() != null) location.setKind(cmd.kind());
        if (cmd.parentLocationId() != null) {
            WorldLocation p = new WorldLocation();
            p.setId(cmd.parentLocationId());
            location.setParentLocation(p);
        } else {
            location.setParentLocation(null);
        }
        if (cmd.mapId() != null) {
            GameMap m = new GameMap();
            m.setId(cmd.mapId());
            location.setMap(m);
        } else {
            location.setMap(null);
        }
        if (cmd.mapRegionKey() != null) location.setMapRegionKey(cmd.mapRegionKey());
        if (cmd.noteId() != null) {
            Note n = new Note();
            n.setId(cmd.noteId());
            location.setNote(n);
        } else {
            location.setNote(null);
        }
        if (cmd.summary() != null) location.setSummary(cmd.summary());
        if (cmd.services() != null) location.setServices(cmd.services());
        if (cmd.secrets() != null) location.setSecrets(cmd.secrets());
        if (cmd.tags() != null) location.setTags(cmd.tags());
        if (cmd.sourceLocator() != null) location.setSourceLocator(cmd.sourceLocator());
        // TODO: handle encounterIds and travelLocationIds when the location UI is built
    }

    private WorldLocation findLocationInCampaign(UUID campaignId, UUID locationId) {
        return locationRepository.findByIdAndCampaignId(locationId, campaignId)
                .orElseThrow(() -> new NotFoundException("Location not found in campaign"));
    }

    // ---- Faction CRUD ----

    public Faction createFaction(UUID campaignId, FactionCommand cmd) {
        Campaign campaign = findCampaign(campaignId);
        Faction faction = new Faction();
        faction.setCampaign(campaign);
        applyFactionCommand(faction, cmd);
        faction = factionRepository.save(faction);
        packageKeys.getOrCreate(campaignId, CampaignContentType.FACTION, faction.getId(), faction.getName());
        return faction;
    }

    public Faction updateFaction(UUID campaignId, UUID factionId, FactionCommand cmd) {
        Faction faction = findFactionInCampaign(campaignId, factionId);
        applyFactionCommand(faction, cmd);
        return factionRepository.save(faction);
    }

    public void deleteFaction(UUID campaignId, UUID factionId) {
        Faction faction = findFactionInCampaign(campaignId, factionId);
        refCleaner.onFactionDelete(campaignId, factionId);
        factionRepository.delete(faction);
        packageKeys.deleteBindings(campaignId, CampaignContentType.FACTION, List.of(factionId));
    }

    @Transactional(readOnly = true)
    public Faction getFaction(UUID campaignId, UUID factionId) {
        return findFactionInCampaign(campaignId, factionId);
    }

    @Transactional(readOnly = true)
    public List<Faction> getFactions(UUID campaignId) {
        return factionRepository.findByCampaignIdOrderByNameAscIdAsc(campaignId);
    }

    private void applyFactionCommand(Faction faction, FactionCommand cmd) {
        if (cmd.name() != null) faction.setName(cmd.name());
        if (cmd.goals() != null) faction.setGoals(cmd.goals());
        if (cmd.resources() != null) faction.setResources(cmd.resources());
        if (cmd.reputationNotes() != null) faction.setReputationNotes(cmd.reputationNotes());
        if (cmd.noteId() != null) {
            Note n = new Note();
            n.setId(cmd.noteId());
            faction.setNote(n);
        } else {
            faction.setNote(null);
        }
        if (cmd.tags() != null) faction.setTags(cmd.tags());
        if (cmd.sourceLocator() != null) faction.setSourceLocator(cmd.sourceLocator());
    }

    private Faction findFactionInCampaign(UUID campaignId, UUID factionId) {
        return factionRepository.findByIdAndCampaignId(factionId, campaignId)
                .orElseThrow(() -> new NotFoundException("Faction not found in campaign"));
    }

    // ---- Relationship CRUD ----

    public WorldRelationship createRelationship(UUID campaignId, RelationshipCommand cmd) {
        Campaign campaign = findCampaign(campaignId);
        if (cmd.fromType().equals(cmd.toType()) && cmd.fromId().equals(cmd.toId())) {
            throw new IllegalArgumentException("Relationship cannot reference itself");
        }
        WorldRelationship relationship = new WorldRelationship();
        relationship.setCampaign(campaign);
        applyRelationshipCommand(relationship, cmd);
        relationship = relationshipRepository.save(relationship);
        packageKeys.getOrCreate(campaignId, CampaignContentType.WORLD_RELATIONSHIP, relationship.getId(),
                cmd.kind().name() + ":" + cmd.fromType() + "-" + cmd.toType());
        return relationship;
    }

    public WorldRelationship updateRelationship(UUID campaignId, UUID relationshipId, RelationshipCommand cmd) {
        WorldRelationship relationship = findRelationshipInCampaign(campaignId, relationshipId);
        if (cmd.fromType().equals(cmd.toType()) && cmd.fromId().equals(cmd.toId())) {
            throw new IllegalArgumentException("Relationship cannot reference itself");
        }
        applyRelationshipCommand(relationship, cmd);
        return relationshipRepository.save(relationship);
    }

    public void deleteRelationship(UUID campaignId, UUID relationshipId) {
        WorldRelationship relationship = findRelationshipInCampaign(campaignId, relationshipId);
        relationshipRepository.delete(relationship);
        packageKeys.deleteBindings(campaignId, CampaignContentType.WORLD_RELATIONSHIP, List.of(relationshipId));
    }

    @Transactional(readOnly = true)
    public WorldRelationship getRelationship(UUID campaignId, UUID id) {
        return findRelationshipInCampaign(campaignId, id);
    }

    @Transactional(readOnly = true)
    public List<WorldRelationship> getRelationships(UUID campaignId) {
        return relationshipRepository.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId);
    }

    private void applyRelationshipCommand(WorldRelationship relationship, RelationshipCommand cmd) {
        if (cmd.kind() != null) relationship.setKind(cmd.kind());
        if (cmd.fromType() != null) relationship.setFromType(cmd.fromType());
        if (cmd.fromId() != null) relationship.setFromId(cmd.fromId());
        if (cmd.toType() != null) relationship.setToType(cmd.toType());
        if (cmd.toId() != null) relationship.setToId(cmd.toId());
        relationship.setDirected(cmd.directed());
        if (cmd.knowledge() != null) relationship.setKnowledge(cmd.knowledge());
        if (cmd.status() != null) relationship.setStatus(cmd.status());
        if (cmd.notes() != null) relationship.setNotes(cmd.notes());
        if (cmd.sourceLocator() != null) relationship.setSourceLocator(cmd.sourceLocator());
        relationship.setSortOrder(cmd.sortOrder());
    }

    private WorldRelationship findRelationshipInCampaign(UUID campaignId, UUID relationshipId) {
        return relationshipRepository.findByIdAndCampaignId(relationshipId, campaignId)
                .orElseThrow(() -> new NotFoundException("Relationship not found in campaign"));
    }

    // ---- Clock CRUD ----

    public FactionClock createClock(UUID campaignId, ClockCommand cmd) {
        Campaign campaign = findCampaign(campaignId);
        validateClockFilled(cmd.segments(), cmd.filled());
        Faction faction = findFactionInCampaign(campaignId, cmd.factionId());
        FactionClock clock = new FactionClock();
        clock.setCampaign(campaign);
        clock.setFaction(faction);
        applyClockCommand(clock, cmd);
        clock = clockRepository.save(clock);
        packageKeys.getOrCreate(campaignId, CampaignContentType.FACTION_CLOCK, clock.getId(), clock.getTitle());
        return clock;
    }

    public FactionClock updateClock(UUID campaignId, UUID clockId, ClockCommand cmd) {
        FactionClock clock = findClockInCampaign(campaignId, clockId);
        validateClockFilled(cmd.segments(), cmd.filled());
        applyClockCommand(clock, cmd);
        return clockRepository.save(clock);
    }

    public void deleteClock(UUID campaignId, UUID clockId) {
        FactionClock clock = findClockInCampaign(campaignId, clockId);
        clockRepository.delete(clock);
        packageKeys.deleteBindings(campaignId, CampaignContentType.FACTION_CLOCK, List.of(clockId));
    }

    @Transactional(readOnly = true)
    public FactionClock getClock(UUID campaignId, UUID id) {
        return findClockInCampaign(campaignId, id);
    }

    @Transactional(readOnly = true)
    public List<FactionClock> getClocks(UUID campaignId) {
        return clockRepository.findByCampaignIdOrderBySortOrderAscIdAsc(campaignId);
    }

    private void validateClockFilled(int segments, int filled) {
        if (filled < 0 || filled > segments) {
            throw new IllegalArgumentException("Clock filled must be in [0, " + segments + "], got " + filled);
        }
    }

    private void applyClockCommand(FactionClock clock, ClockCommand cmd) {
        if (cmd.title() != null) clock.setTitle(cmd.title());
        clock.setSegments(cmd.segments());
        clock.setFilled(cmd.filled());
        if (cmd.objectiveId() != null) {
            QuestObjective o = new QuestObjective();
            o.setId(cmd.objectiveId());
            clock.setObjective(o);
        } else {
            clock.setObjective(null);
        }
        if (cmd.sceneId() != null) {
            Scene s = new Scene();
            s.setId(cmd.sceneId());
            clock.setScene(s);
        } else {
            clock.setScene(null);
        }
        if (cmd.notes() != null) clock.setNotes(cmd.notes());
        if (cmd.sourceLocator() != null) clock.setSourceLocator(cmd.sourceLocator());
        clock.setSortOrder(cmd.sortOrder());
    }

    private FactionClock findClockInCampaign(UUID campaignId, UUID clockId) {
        return clockRepository.findByIdAndCampaignId(clockId, campaignId)
                .orElseThrow(() -> new NotFoundException("Clock not found in campaign"));
    }

    // ---- Helpers ----

    private Campaign findCampaign(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
    }

    private void validateFactionInCampaign(UUID campaignId, UUID factionId) {
        if (factionId == null) return;
        if (!factionRepository.findByIdAndCampaignId(factionId, campaignId).isPresent()) {
            throw new IllegalArgumentException("Faction not found in campaign");
        }
    }

    private void validateLocationInCampaign(UUID campaignId, UUID locationId) {
        if (locationId == null) return;
        if (!locationRepository.findByIdAndCampaignId(locationId, campaignId).isPresent()) {
            throw new IllegalArgumentException("Location not found in campaign");
        }
    }

    private void validateNoteInCampaign(UUID campaignId, UUID noteId) {
        if (noteId == null) return;
        if (noteRepository.findByIdAndCampaignId(noteId, campaignId).isEmpty()) {
            throw new IllegalArgumentException("Note not found in campaign");
        }
    }

    private void validateStatblockInCampaign(UUID campaignId, UUID statblockId) {
        if (statblockId == null) return;
        Optional<StatBlock> sb = statBlockRepository.findById(statblockId);
        if (sb.isEmpty()) {
            throw new IllegalArgumentException("StatBlock not found");
        }
        if (sb.get().getCampaign() != null && !sb.get().getCampaign().getId().equals(campaignId)) {
            throw new IllegalArgumentException("StatBlock not found in campaign");
        }
    }
}
