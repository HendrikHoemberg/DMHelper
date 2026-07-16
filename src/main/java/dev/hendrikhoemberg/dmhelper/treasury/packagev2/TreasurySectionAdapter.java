package dev.hendrikhoemberg.dmhelper.treasury.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AssignmentDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TreasurySectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final ItemAssignmentRepository assignmentRepository;
    private final MagicItemRepository magicItemRepository;
    private final EquipmentItemRepository equipmentItemRepository;

    public TreasurySectionAdapter(ItemAssignmentRepository assignmentRepository,
                                  MagicItemRepository magicItemRepository,
                                  EquipmentItemRepository equipmentItemRepository) {
        this.assignmentRepository = assignmentRepository;
        this.magicItemRepository = magicItemRepository;
        this.equipmentItemRepository = equipmentItemRepository;
    }

    @Override
    public String sectionName() {
        return "Treasury";
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var assignments = assignmentRepository.findByCampaignIdOrderByPartyMemberAscIdAsc(context.campaignId());
        List<AssignmentDto> dtos = assignments.stream()
                .map(a -> exportAssignment(a, context))
                .toList();
        target.assignments(dtos);
    }

    private AssignmentDto exportAssignment(ItemAssignment assignment, CampaignExportContext context) {
        String key = context.key(CampaignContentType.ASSIGNMENT, assignment.getId(), assignment.getItemName());

        ContentReference holderRef = null;
        if (assignment.getPartyMember() != null) {
            holderRef = context.packageRef(CampaignContentType.PARTY_MEMBER,
                    assignment.getPartyMember().getId(),
                    assignment.getPartyMember().getCharacterName());
        }

        ContentReference magicItemRef = null;
        if (assignment.getMagicItem() != null) {
            magicItemRef = context.catalogRef(CampaignContentType.MAGIC_ITEM,
                    assignment.getMagicItem().getSourceKey());
        }

        ContentReference equipmentItemRef = null;
        if (assignment.getEquipmentItem() != null) {
            equipmentItemRef = context.catalogRef(CampaignContentType.EQUIPMENT_ITEM,
                    assignment.getEquipmentItem().getSourceKey());
        }

        return new AssignmentDto(key, holderRef, magicItemRef, equipmentItemRef,
                assignment.getCustomText(), assignment.getQuantity(), assignment.isAttuned());
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<AssignmentDto> dtos = source.assignments();
        if (dtos == null) return;

        var campaign = context.campaign();

        for (AssignmentDto dto : dtos) {
            var assignment = new ItemAssignment();
            assignment.setCampaign(campaign);
            assignment.setCustomText(dto.customText());
            assignment.setQuantity(dto.quantity());
            assignment.setAttuned(dto.attuned());

            if (dto.magicItemRef() != null) {
                assignment.setMagicItem(magicItemRepository.findBySourceKey(dto.magicItemRef().sourceKey())
                        .orElseThrow(() -> new IllegalStateException(
                                "No catalog magic item for " + dto.magicItemRef().sourceKey())));
            }
            if (dto.equipmentItemRef() != null) {
                assignment.setEquipmentItem(equipmentItemRepository.findBySourceKey(
                                dto.equipmentItemRef().sourceKey())
                        .orElseThrow(() -> new IllegalStateException(
                                "No catalog equipment item for " + dto.equipmentItemRef().sourceKey())));
            }

            if (dto.holderRef() != null) {
                ContentReference ref = dto.holderRef();
                if (ref.type() == CampaignContentType.PARTY_MEMBER) {
                    context.defer("assignment holder " + dto.key(), () -> {
                        var pm = context.require(ref, CampaignContentType.PARTY_MEMBER, PartyMember.class);
                        assignment.setPartyMember(pm);
                    });
                }
            }

            assignmentRepository.save(assignment);
            context.register(CampaignContentType.ASSIGNMENT, dto.key(), assignment, assignment.getId());
        }
    }
}
