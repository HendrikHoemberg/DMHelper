package dev.hendrikhoemberg.dmhelper.world.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.world.data.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Transactional
public class WorldReferenceCleaner {

    private final WorldNpcRepository npcRepository;
    private final WorldLocationRepository locationRepository;
    private final FactionClockRepository clockRepository;
    private final WorldRelationshipRepository relationshipRepository;
    private final CampaignPackageKeyService packageKeys;

    public WorldReferenceCleaner(WorldNpcRepository npcRepository,
                                 WorldLocationRepository locationRepository,
                                 FactionClockRepository clockRepository,
                                 WorldRelationshipRepository relationshipRepository,
                                 CampaignPackageKeyService packageKeys) {
        this.npcRepository = npcRepository;
        this.locationRepository = locationRepository;
        this.clockRepository = clockRepository;
        this.relationshipRepository = relationshipRepository;
        this.packageKeys = packageKeys;
    }

    public void onNpcDelete(UUID campaignId, UUID npcId) {
        deleteRelationshipsForEndpoint(campaignId, "WORLD_NPC", npcId);
        // scene_participant.world_npc_id is ON DELETE SET NULL at the DB layer
    }

    public void onLocationDelete(UUID campaignId, UUID locationId) {
        deleteRelationshipsForEndpoint(campaignId, "WORLD_LOCATION", locationId);

        List<WorldNpc> npcs = npcRepository.findByLocationId(locationId);
        for (WorldNpc npc : npcs) {
            npc.setLocation(null);
        }
        if (!npcs.isEmpty()) {
            npcRepository.saveAll(npcs);
        }

        List<WorldLocation> siblings = locationRepository.findByCampaignIdOrderByNameAscIdAsc(campaignId);
        boolean changed = false;
        for (WorldLocation loc : siblings) {
            if (loc.getParentLocation() != null && locationId.equals(loc.getParentLocation().getId())) {
                loc.setParentLocation(null);
                changed = true;
            }
            if (loc.getTravelLocations() != null) {
                boolean removed = loc.getTravelLocations().removeIf(
                        t -> t != null && locationId.equals(t.getId()));
                changed = changed || removed;
            }
        }
        if (changed) {
            locationRepository.saveAll(siblings);
        }
    }

    public void onFactionDelete(UUID campaignId, UUID factionId) {
        deleteRelationshipsForEndpoint(campaignId, "FACTION", factionId);

        List<FactionClock> clocks = clockRepository.findByFactionIdOrderBySortOrderAscIdAsc(factionId);
        if (!clocks.isEmpty()) {
            List<UUID> clockIds = clocks.stream().map(FactionClock::getId).toList();
            packageKeys.deleteBindings(campaignId, CampaignContentType.FACTION_CLOCK, clockIds);
            clockRepository.deleteAllById(clockIds);
        }
        List<WorldNpc> npcs = npcRepository.findByFactionId(factionId);
        for (WorldNpc npc : npcs) {
            npc.setFaction(null);
        }
        if (!npcs.isEmpty()) {
            npcRepository.saveAll(npcs);
        }
    }

    private void deleteRelationshipsForEndpoint(UUID campaignId, String type, UUID entityId) {
        List<WorldRelationship> rels =
                relationshipRepository.findByCampaignIdAndEndpoint(campaignId, type, entityId);
        if (rels.isEmpty()) {
            return;
        }
        List<UUID> ids = rels.stream().map(WorldRelationship::getId).toList();
        packageKeys.deleteBindings(campaignId, CampaignContentType.WORLD_RELATIONSHIP, ids);
        relationshipRepository.deleteAll(rels);
    }
}
