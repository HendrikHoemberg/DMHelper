package dev.hendrikhoemberg.dmhelper.rollabletable.packagev2;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLink;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReference;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReferenceRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLink;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RollableTableExportClosureService {

    private static final int MAX_DEPTH = 10;

    private final RollableTableRepository tableRepository;
    private final WorldLocationTableLinkRepository locationLinkRepo;
    private final RollableTableEntryReferenceRepository entryRefRepo;
    private final SceneRepository sceneRepo;
    private final WorldLocationRepository locationRepo;

    public RollableTableExportClosureService(RollableTableRepository tableRepository,
                                              WorldLocationTableLinkRepository locationLinkRepo,
                                              RollableTableEntryReferenceRepository entryRefRepo,
                                              SceneRepository sceneRepo,
                                              WorldLocationRepository locationRepo) {
        this.tableRepository = tableRepository;
        this.locationLinkRepo = locationLinkRepo;
        this.entryRefRepo = entryRefRepo;
        this.sceneRepo = sceneRepo;
        this.locationRepo = locationRepo;
    }

    public ClosureResult forCampaign(UUID campaignId) {
        Set<UUID> tableIds = new HashSet<>();
        Set<UUID> globalCustomStatblockIds = new HashSet<>();
        Set<UUID> globalCustomMagicItemIds = new HashSet<>();
        Set<UUID> globalCustomEquipmentIds = new HashSet<>();
        Set<UUID> globalCustomSpellIds = new HashSet<>();

        List<RollableTable> campaignTables = tableRepository.findByCampaignIdOrderByNameAsc(campaignId);
        for (RollableTable table : campaignTables) {
            tableIds.add(table.getId());
        }

        List<Scene> scenes = sceneRepo.findByChapterAdventureCampaignId(campaignId);
        for (Scene scene : scenes) {
            for (SceneLink link : scene.getLinks()) {
                if (link.getRole() == SceneLinkRole.RANDOM_ENCOUNTERS
                        && link.getTargetScope() == dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope.PACKAGE
                        && link.getTargetId() != null) {
                    tableIds.add(link.getTargetId());
                }
            }
        }

        List<WorldLocation> locations = locationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId);
        for (var location : locations) {
            List<WorldLocationTableLink> links = locationLinkRepo.findByLocationIdWithTable(location.getId());
            for (var link : links) {
                tableIds.add(link.getTable().getId());
            }
        }

        Set<UUID> visited = new HashSet<>();
        List<UUID> sorted = new ArrayList<>();
        for (UUID id : tableIds) {
            walkTableClosure(id, visited, sorted, globalCustomStatblockIds,
                    globalCustomMagicItemIds, globalCustomEquipmentIds, globalCustomSpellIds, 0);
        }

        sorted.sort(Comparator.naturalOrder());
        Map<CampaignContentType, Set<UUID>> libraryRefs = new HashMap<>();
        if (!globalCustomStatblockIds.isEmpty()) {
            libraryRefs.put(CampaignContentType.STATBLOCK, globalCustomStatblockIds);
        }
        if (!globalCustomMagicItemIds.isEmpty()) {
            libraryRefs.put(CampaignContentType.MAGIC_ITEM, globalCustomMagicItemIds);
        }
        if (!globalCustomEquipmentIds.isEmpty()) {
            libraryRefs.put(CampaignContentType.EQUIPMENT_ITEM, globalCustomEquipmentIds);
        }
        if (!globalCustomSpellIds.isEmpty()) {
            libraryRefs.put(CampaignContentType.SPELL, globalCustomSpellIds);
        }
        return new ClosureResult(List.copyOf(sorted), Map.copyOf(libraryRefs));
    }

    private void walkTableClosure(UUID tableId, Set<UUID> visited, List<UUID> sorted,
                                  Set<UUID> statblockIds, Set<UUID> magicItemIds,
                                  Set<UUID> equipmentIds, Set<UUID> spellIds, int depth) {
        if (tableId == null || !visited.add(tableId)) return;
        if (depth > MAX_DEPTH) return;

        sorted.add(tableId);
        var tableOpt = tableRepository.findWithEntriesById(tableId);
        if (tableOpt.isEmpty()) return;

        var table = tableOpt.get();
        for (var entry : table.getEntries()) {
            for (var ref : entry.getReferences()) {
                if (ref.getTargetScope() == TableReferenceScope.CATALOG) {
                    continue;
                }
                if (ref.getTargetType() != null && ref.getTargetId() != null) {
                    try {
                        CampaignContentType type = CampaignContentType.valueOf(ref.getTargetType());
                        if (type == CampaignContentType.STATBLOCK) {
                            statblockIds.add(ref.getTargetId());
                        } else if (type == CampaignContentType.MAGIC_ITEM) {
                            magicItemIds.add(ref.getTargetId());
                        } else if (type == CampaignContentType.EQUIPMENT_ITEM) {
                            equipmentIds.add(ref.getTargetId());
                        } else if (type == CampaignContentType.SPELL) {
                            spellIds.add(ref.getTargetId());
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (ref.getTargetScope() == TableReferenceScope.ENTITY
                        && "ROLLABLE_TABLE".equals(ref.getTargetType())) {
                    if (ref.getTargetId() != null) {
                        walkTableClosure(ref.getTargetId(), visited, sorted, statblockIds,
                                magicItemIds, equipmentIds, spellIds, depth + 1);
                    }
                }
            }
        }
    }

    public record ClosureResult(
            List<UUID> tableIds,
            Map<CampaignContentType, Set<UUID>> libraryReferenceIds
    ) {}
}
