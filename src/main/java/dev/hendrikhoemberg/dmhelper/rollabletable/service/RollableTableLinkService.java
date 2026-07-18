package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLink;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLink;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RollableTableLinkService {

    private final SceneRepository sceneRepository;
    private final SceneLinkRepository sceneLinkRepository;
    private final WorldLocationTableLinkRepository worldLocationTableLinkRepository;
    private final RollableTableRepository rollableTableRepository;
    private final WorldLocationRepository worldLocationRepository;

    public RollableTableLinkService(SceneRepository sceneRepository,
                                    SceneLinkRepository sceneLinkRepository,
                                    WorldLocationTableLinkRepository worldLocationTableLinkRepository,
                                    RollableTableRepository rollableTableRepository,
                                    WorldLocationRepository worldLocationRepository) {
        this.sceneRepository = sceneRepository;
        this.sceneLinkRepository = sceneLinkRepository;
        this.worldLocationTableLinkRepository = worldLocationTableLinkRepository;
        this.rollableTableRepository = rollableTableRepository;
        this.worldLocationRepository = worldLocationRepository;
    }

    public List<LinkedRollableTableView> forScene(UUID campaignId, UUID sceneId) {
        Scene scene = sceneRepository.findByIdAndCampaignId(campaignId, sceneId)
                .orElseThrow(() -> new NotFoundException("Scene not found in campaign"));

        List<SceneLink> sceneLinks = sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId);

        List<LinkedRollableTableView> result = new ArrayList<>();
        Set<UUID> seenTableIds = new LinkedHashSet<>();

        // Scene-level direct table links
        for (SceneLink link : sceneLinks) {
            if (link.getRole() == SceneLinkRole.RANDOM_ENCOUNTERS
                    && "ROLLABLE_TABLE".equals(link.getTargetType())
                    && link.getTargetId() != null) {
                rollableTableRepository.findById(link.getTargetId())
                        .filter(table -> isVisibleToCampaign(table, campaignId))
                        .ifPresent(table -> {
                            if (seenTableIds.add(table.getId())) {
                                result.add(new LinkedRollableTableView(
                                        table.getId(), table.getSourceKey(), table.getName(),
                                        table.getCategory(), "SCENE",
                                        link.getDisplayText() != null ? link.getDisplayText() : table.getName()));
                            }
                        });
            }
        }

        // Location-level table links (through LOCATION scene links)
        for (SceneLink link : sceneLinks) {
            if (link.getRole() == SceneLinkRole.LOCATION
                    && link.getTargetId() != null) {
                worldLocationRepository.findByIdAndCampaignId(link.getTargetId(), campaignId)
                        .ifPresent(location -> {
                            List<WorldLocationTableLink> locationLinks =
                                    worldLocationTableLinkRepository.findByLocationIdOrderBySortOrderAsc(location.getId());
                            for (WorldLocationTableLink locLink : locationLinks) {
                                RollableTable table = locLink.getTable();
                                if (isVisibleToCampaign(table, campaignId) && seenTableIds.add(table.getId())) {
                                    result.add(new LinkedRollableTableView(
                                            table.getId(), table.getSourceKey(), table.getName(),
                                            table.getCategory(), "LOCATION",
                                            location.getName() + ": " + (table.getName())));
                                }
                            }
                        });
            }
        }

        return result;
    }

    private boolean isVisibleToCampaign(RollableTable table, UUID campaignId) {
        if (table.getSource() == dev.hendrikhoemberg.dmhelper.library.data.ContentSource.SRD) return true;
        if (table.getCampaign() == null) return true;
        return table.getCampaign().getId().equals(campaignId);
    }
}
