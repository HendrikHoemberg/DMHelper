package dev.hendrikhoemberg.dmhelper.world.service;

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

    public WorldReferenceCleaner(WorldNpcRepository npcRepository,
                                 WorldLocationRepository locationRepository,
                                 FactionClockRepository clockRepository) {
        this.npcRepository = npcRepository;
        this.locationRepository = locationRepository;
        this.clockRepository = clockRepository;
    }

    public void onLocationDelete(UUID campaignId, UUID locationId) {
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
        }
        if (changed) {
            locationRepository.saveAll(siblings);
        }
    }

    public void onFactionDelete(UUID campaignId, UUID factionId) {
        List<FactionClock> clocks = clockRepository.findByFactionIdOrderBySortOrderAscIdAsc(factionId);
        if (!clocks.isEmpty()) {
            List<UUID> clockIds = clocks.stream().map(FactionClock::getId).toList();
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
}
