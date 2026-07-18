package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReferenceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Transactional
public class LibraryReferenceCleaner {

    private final SheetSpellReferenceRepository sheetSpellRefRepository;
    private final CharacterSheetRepository sheetRepository;
    private final ItemAssignmentRepository itemAssignmentRepository;
    private final RollableTableEntryReferenceRepository rollableTableEntryRefRepository;
    private final ThreatReferenceRepository threatReferenceRepository;
    private final TrapRepository trapRepository;
    private final CampaignPackageKeyService packageKeyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LibraryReferenceCleaner(SheetSpellReferenceRepository sheetSpellRefRepository,
                                   CharacterSheetRepository sheetRepository,
                                   ItemAssignmentRepository itemAssignmentRepository,
                                   RollableTableEntryReferenceRepository rollableTableEntryRefRepository,
                                   ThreatReferenceRepository threatReferenceRepository,
                                   TrapRepository trapRepository,
                                   CampaignPackageKeyService packageKeyService) {
        this.sheetSpellRefRepository = sheetSpellRefRepository;
        this.sheetRepository = sheetRepository;
        this.itemAssignmentRepository = itemAssignmentRepository;
        this.rollableTableEntryRefRepository = rollableTableEntryRefRepository;
        this.threatReferenceRepository = threatReferenceRepository;
        this.trapRepository = trapRepository;
        this.packageKeyService = packageKeyService;
    }

    public int countSpellReferences(UUID spellId) {
        return sheetSpellRefRepository.findBySpellId(spellId).size();
    }

    public void deletePackageKey(UUID campaignId, CampaignContentType type, UUID entityId) {
        packageKeyService.deleteBindings(campaignId, type, List.of(entityId));
    }

    public int countMagicItemReferences(UUID magicItemId) {
        return (int) itemAssignmentRepository.findAll().stream()
                .filter(a -> a.getMagicItem() != null && a.getMagicItem().getId().equals(magicItemId))
                .count();
    }

    public int countEquipmentItemReferences(UUID equipmentItemId) {
        return (int) itemAssignmentRepository.findAll().stream()
                .filter(a -> a.getEquipmentItem() != null && a.getEquipmentItem().getId().equals(equipmentItemId))
                .count();
    }

    public int countSpeciesReferences(UUID speciesId) {
        return (int) sheetRepository.findAll().stream()
                .filter(s -> s.getSpecies() != null && s.getSpecies().getId().equals(speciesId))
                .count();
    }

    public int countBackgroundReferences(UUID backgroundId) {
        return (int) sheetRepository.findAll().stream()
                .filter(s -> s.getBackground() != null && s.getBackground().getId().equals(backgroundId))
                .count();
    }

    /** Counts sheets whose classLevels JSON references the given class sourceKey. */
    public int countClassSourceKeyReferences(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) return 0;
        int count = 0;
        for (CharacterSheet sheet : sheetRepository.findAll()) {
            if (classLevelsContain(sheet.getClassLevels(), sourceKey)) {
                count++;
            }
        }
        return count;
    }

    /** Counts sheets whose featRefs JSON list contains the given feat sourceKey. */
    public int countFeatSourceKeyReferences(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) return 0;
        int count = 0;
        for (CharacterSheet sheet : sheetRepository.findAll()) {
            if (featRefsContain(sheet.getFeatRefs(), sourceKey)) {
                count++;
            }
        }
        return count;
    }

    public int countRollableTableReferences(UUID entityId, CampaignContentType contentType) {
        return rollableTableEntryRefRepository
                .findByTargetTypeAndTargetId(contentType.name(), entityId).size();
    }

    /** Counts trap/hazard ThreatReference rows targeting the given library entity. */
    public int countThreatReferences(CampaignContentType contentType, UUID entityId) {
        return threatReferenceRepository.findByTargetTypeAndTargetId(contentType, entityId).size();
    }

    /** Counts traps that link a statblock directly via Trap.statBlock. */
    public int countTrapStatBlockReferences(UUID statBlockId) {
        return (int) trapRepository.countByStatBlockId(statBlockId);
    }

    private boolean classLevelsContain(String raw, String sourceKey) {
        if (raw == null || raw.isBlank()) return false;
        try {
            List<Map<String, Object>> list = objectMapper.readValue(raw, new TypeReference<>() {});
            for (Map<String, Object> entry : list) {
                if (sourceKey.equals(entry.get("classRef"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private boolean featRefsContain(String raw, String sourceKey) {
        if (raw == null || raw.isBlank()) return false;
        try {
            List<String> keys = objectMapper.readValue(raw, new TypeReference<>() {});
            return keys.contains(sourceKey);
        } catch (Exception ignored) {
            return false;
        }
    }
}
