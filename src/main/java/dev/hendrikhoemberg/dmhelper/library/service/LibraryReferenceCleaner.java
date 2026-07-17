package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Transactional
public class LibraryReferenceCleaner {

    private final SheetSpellReferenceRepository sheetSpellRefRepository;
    private final CharacterSheetRepository sheetRepository;
    private final ItemAssignmentRepository itemAssignmentRepository;
    private final CampaignPackageKeyService packageKeyService;

    public LibraryReferenceCleaner(SheetSpellReferenceRepository sheetSpellRefRepository,
                                   CharacterSheetRepository sheetRepository,
                                   ItemAssignmentRepository itemAssignmentRepository,
                                   CampaignPackageKeyService packageKeyService) {
        this.sheetSpellRefRepository = sheetSpellRefRepository;
        this.sheetRepository = sheetRepository;
        this.itemAssignmentRepository = itemAssignmentRepository;
        this.packageKeyService = packageKeyService;
    }

    public int countSpellReferences(UUID spellId) {
        return sheetSpellRefRepository.findBySpellId(spellId).size();
    }

    public void deletePackageKey(UUID campaignId, CampaignContentType type, UUID entityId) {
        packageKeyService.deleteBindings(campaignId, type, List.of(entityId));
    }

    public int countMagicItemReferences(UUID magicItemId) {
        return itemAssignmentRepository.findAll().stream()
                .filter(a -> a.getMagicItem() != null && a.getMagicItem().getId().equals(magicItemId))
                .toList().size();
    }

    public int countEquipmentItemReferences(UUID equipmentItemId) {
        return itemAssignmentRepository.findAll().stream()
                .filter(a -> a.getEquipmentItem() != null && a.getEquipmentItem().getId().equals(equipmentItemId))
                .toList().size();
    }

    public int countSpeciesReferences(UUID speciesId) {
        return sheetRepository.findAll().stream()
                .filter(s -> s.getSpecies() != null && s.getSpecies().getId().equals(speciesId))
                .toList().size();
    }

    public int countBackgroundReferences(UUID backgroundId) {
        return sheetRepository.findAll().stream()
                .filter(s -> s.getBackground() != null && s.getBackground().getId().equals(backgroundId))
                .toList().size();
    }
}
