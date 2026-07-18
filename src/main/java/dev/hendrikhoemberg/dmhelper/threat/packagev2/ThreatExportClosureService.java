package dev.hendrikhoemberg.dmhelper.threat.packagev2;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPin;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReference;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
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
public class ThreatExportClosureService {

    private final TrapRepository trapRepository;
    private final HazardRepository hazardRepository;
    private final SceneRepository sceneRepository;
    private final EncounterRepository encounterRepository;
    private final CombatantRepository combatantRepository;
    private final GameMapRepository gameMapRepository;
    private final MapThreatPinRepository mapThreatPinRepository;
    private final StatBlockRepository statBlockRepository;
    private final ConditionRepository conditionRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final MagicItemRepository magicItemRepository;

    public ThreatExportClosureService(TrapRepository trapRepository,
                                      HazardRepository hazardRepository,
                                      SceneRepository sceneRepository,
                                      EncounterRepository encounterRepository,
                                      CombatantRepository combatantRepository,
                                      GameMapRepository gameMapRepository,
                                      MapThreatPinRepository mapThreatPinRepository,
                                      StatBlockRepository statBlockRepository,
                                      ConditionRepository conditionRepository,
                                      EquipmentItemRepository equipmentItemRepository,
                                      MagicItemRepository magicItemRepository) {
        this.trapRepository = trapRepository;
        this.hazardRepository = hazardRepository;
        this.sceneRepository = sceneRepository;
        this.encounterRepository = encounterRepository;
        this.combatantRepository = combatantRepository;
        this.gameMapRepository = gameMapRepository;
        this.mapThreatPinRepository = mapThreatPinRepository;
        this.statBlockRepository = statBlockRepository;
        this.conditionRepository = conditionRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.magicItemRepository = magicItemRepository;
    }

    public ClosureResult forCampaign(UUID campaignId) {
        Set<UUID> trapIds = new HashSet<>();
        Set<UUID> hazardIds = new HashSet<>();

        for (Trap trap : trapRepository.findVisibleByCampaignId(campaignId)) {
            if (trap.getCampaign() != null && campaignId.equals(trap.getCampaign().getId())) {
                trapIds.add(trap.getId());
            }
        }
        for (Hazard hazard : hazardRepository.findVisibleByCampaignId(campaignId)) {
            if (hazard.getCampaign() != null && campaignId.equals(hazard.getCampaign().getId())) {
                hazardIds.add(hazard.getId());
            }
        }

        for (Scene scene : sceneRepository.findByChapterAdventureCampaignId(campaignId)) {
            for (SceneSection section : scene.getSections()) {
                if (section.getThreatId() == null || section.getThreatKind() == null) continue;
                if (section.getThreatKind() == ThreatKind.TRAP) {
                    trapIds.add(section.getThreatId());
                } else if (section.getThreatKind() == ThreatKind.HAZARD) {
                    hazardIds.add(section.getThreatId());
                }
            }
        }

        for (Encounter encounter : encounterRepository.findByCampaignIdOrderByNameAsc(campaignId)) {
            for (Combatant combatant : combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId())) {
                if (combatant.getThreatId() == null || combatant.getThreatKind() == null) continue;
                if (combatant.getThreatKind() == ThreatKind.TRAP) {
                    trapIds.add(combatant.getThreatId());
                } else if (combatant.getThreatKind() == ThreatKind.HAZARD) {
                    hazardIds.add(combatant.getThreatId());
                }
            }
        }

        for (GameMap map : gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId)) {
            for (MapThreatPin pin : mapThreatPinRepository.findByMapIdOrderBySortOrderAsc(map.getId())) {
                if (pin.getThreatKind() == ThreatKind.TRAP) {
                    trapIds.add(pin.getThreatId());
                } else if (pin.getThreatKind() == ThreatKind.HAZARD) {
                    hazardIds.add(pin.getThreatId());
                }
            }
        }

        Set<UUID> conditionIds = new HashSet<>();
        Set<UUID> statblockIds = new HashSet<>();
        Set<UUID> equipmentIds = new HashSet<>();
        Set<UUID> magicItemIds = new HashSet<>();

        for (UUID id : trapIds) {
            trapRepository.findDetailedById(id).ifPresent(trap ->
                    collectLibraryRefs(trap.getReferences(), trap.getStatBlock() != null
                            ? trap.getStatBlock().getId() : null,
                            conditionIds, statblockIds, equipmentIds, magicItemIds));
        }
        for (UUID id : hazardIds) {
            hazardRepository.findDetailedById(id).ifPresent(hazard ->
                    collectLibraryRefs(hazard.getReferences(), null,
                            conditionIds, statblockIds, equipmentIds, magicItemIds));
        }

        List<UUID> sortedTraps = new ArrayList<>(trapIds);
        sortedTraps.sort(Comparator.naturalOrder());
        List<UUID> sortedHazards = new ArrayList<>(hazardIds);
        sortedHazards.sort(Comparator.naturalOrder());

        Map<CampaignContentType, Set<UUID>> libraryRefs = new HashMap<>();
        if (!conditionIds.isEmpty()) {
            libraryRefs.put(CampaignContentType.CONDITION, conditionIds);
        }
        if (!statblockIds.isEmpty()) {
            libraryRefs.put(CampaignContentType.STATBLOCK, statblockIds);
        }
        if (!equipmentIds.isEmpty()) {
            libraryRefs.put(CampaignContentType.EQUIPMENT_ITEM, equipmentIds);
        }
        if (!magicItemIds.isEmpty()) {
            libraryRefs.put(CampaignContentType.MAGIC_ITEM, magicItemIds);
        }
        return new ClosureResult(List.copyOf(sortedTraps), List.copyOf(sortedHazards), Map.copyOf(libraryRefs));
    }

    /**
     * Seeds only user-global CUSTOM library rows for embedding by {@code LibrarySectionAdapter}.
     * SRD/catalog targets stay as catalog content refs and must not be re-embedded as custom rows.
     * Campaign-scoped CUSTOM rows are already exported by campaign ownership.
     */
    private void collectLibraryRefs(List<ThreatReference> references, UUID statBlockId,
                                    Set<UUID> conditionIds, Set<UUID> statblockIds,
                                    Set<UUID> equipmentIds, Set<UUID> magicItemIds) {
        if (statBlockId != null && isGlobalCustomStatBlock(statBlockId)) {
            statblockIds.add(statBlockId);
        }
        if (references == null) return;
        for (ThreatReference ref : references) {
            if (ref.getTargetId() == null || ref.getTargetType() == null) continue;
            switch (ref.getTargetType()) {
                case CONDITION -> {
                    if (isGlobalCustomCondition(ref.getTargetId())) {
                        conditionIds.add(ref.getTargetId());
                    }
                }
                case STATBLOCK -> {
                    if (isGlobalCustomStatBlock(ref.getTargetId())) {
                        statblockIds.add(ref.getTargetId());
                    }
                }
                case EQUIPMENT_ITEM -> {
                    if (isGlobalCustomEquipment(ref.getTargetId())) {
                        equipmentIds.add(ref.getTargetId());
                    }
                }
                case MAGIC_ITEM -> {
                    if (isGlobalCustomMagicItem(ref.getTargetId())) {
                        magicItemIds.add(ref.getTargetId());
                    }
                }
                default -> { }
            }
        }
    }

    private boolean isGlobalCustomStatBlock(UUID id) {
        return statBlockRepository.findById(id)
                .filter(ThreatExportClosureService::isGlobalCustom)
                .isPresent();
    }

    private boolean isGlobalCustomCondition(UUID id) {
        return conditionRepository.findById(id)
                .filter(ThreatExportClosureService::isGlobalCustom)
                .isPresent();
    }

    private boolean isGlobalCustomEquipment(UUID id) {
        return equipmentItemRepository.findById(id)
                .filter(ThreatExportClosureService::isGlobalCustom)
                .isPresent();
    }

    private boolean isGlobalCustomMagicItem(UUID id) {
        return magicItemRepository.findById(id)
                .filter(ThreatExportClosureService::isGlobalCustom)
                .isPresent();
    }

    private static boolean isGlobalCustom(StatBlock entity) {
        return entity.getSource() == ContentSource.CUSTOM && entity.getCampaign() == null;
    }

    private static boolean isGlobalCustom(Condition entity) {
        return entity.getSource() == ContentSource.CUSTOM && entity.getCampaign() == null;
    }

    private static boolean isGlobalCustom(EquipmentItem entity) {
        return entity.getSource() == ContentSource.CUSTOM && entity.getCampaign() == null;
    }

    private static boolean isGlobalCustom(MagicItem entity) {
        return entity.getSource() == ContentSource.CUSTOM && entity.getCampaign() == null;
    }

    public record ClosureResult(
            List<UUID> trapIds,
            List<UUID> hazardIds,
            Map<CampaignContentType, Set<UUID>> libraryReferenceIds
    ) {}
}
