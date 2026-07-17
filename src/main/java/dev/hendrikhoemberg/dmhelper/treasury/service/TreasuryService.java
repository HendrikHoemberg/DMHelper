package dev.hendrikhoemberg.dmhelper.treasury.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.treasury.data.InventoryState;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class TreasuryService {

    private final ItemAssignmentRepository repository;
    private final PartyMemberRepository partyMemberRepository;
    private final MagicItemRepository magicItemRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final CampaignRepository campaignRepository;

    public TreasuryService(ItemAssignmentRepository repository,
                           PartyMemberRepository partyMemberRepository,
                           MagicItemRepository magicItemRepository,
                           EquipmentItemRepository equipmentItemRepository,
                           CampaignRepository campaignRepository) {
        this.repository = repository;
        this.partyMemberRepository = partyMemberRepository;
        this.magicItemRepository = magicItemRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.campaignRepository = campaignRepository;
    }

    public record CreateAssignmentRequest(
        UUID campaignId, UUID partyMemberId, UUID magicItemId, UUID equipmentItemId,
        String customText, int quantity, boolean attuned,
        InventoryState inventoryState
    ) {
        public CreateAssignmentRequest {
            if (inventoryState == null) inventoryState = InventoryState.CARRIED;
        }

        public CreateAssignmentRequest(
            UUID campaignId, UUID partyMemberId, UUID magicItemId, UUID equipmentItemId,
            String customText, int quantity, boolean attuned
        ) {
            this(campaignId, partyMemberId, magicItemId, equipmentItemId,
                 customText, quantity, attuned,
                 partyMemberId == null ? InventoryState.STASHED : InventoryState.CARRIED);
        }
    }

    public record AssignmentDto(
        UUID id, UUID campaignId, UUID partyMemberId, String holderName,
        UUID magicItemId, String magicItemName,
        UUID equipmentItemId, String equipmentItemName,
        String customText, int quantity, boolean attuned, String itemName,
        InventoryState inventoryState
    ) {
        public static AssignmentDto from(ItemAssignment a) {
            return new AssignmentDto(
                a.getId(), a.getCampaign().getId(),
                a.getPartyMember() != null ? a.getPartyMember().getId() : null,
                a.getPartyMember() != null ? a.getPartyMember().getCharacterName() : "Party Stash",
                a.getMagicItem() != null ? a.getMagicItem().getId() : null,
                a.getMagicItem() != null ? a.getMagicItem().getName() : null,
                a.getEquipmentItem() != null ? a.getEquipmentItem().getId() : null,
                a.getEquipmentItem() != null ? a.getEquipmentItem().getName() : null,
                a.getCustomText(), a.getQuantity(), a.isAttuned(), a.getItemName(),
                a.getInventoryState()
            );
        }
    }

    public AssignmentDto create(CreateAssignmentRequest req) {
        Campaign campaign = campaignRepository.findById(req.campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        if (req.magicItemId == null && req.equipmentItemId == null
            && (req.customText == null || req.customText.isBlank())) {
            throw new IllegalArgumentException("At least one of magicItemId, equipmentItemId, or customText is required");
        }
        if (req.magicItemId != null && req.equipmentItemId != null) {
            throw new IllegalArgumentException("Cannot assign both a magic item and an equipment item to the same assignment");
        }
        ItemAssignment a = new ItemAssignment();
        a.setCampaign(campaign);
        if (req.partyMemberId != null) {
            a.setPartyMember(partyMemberRepository.findById(req.partyMemberId)
                    .orElseThrow(() -> new NotFoundException("Party member not found")));
        }
        if (req.magicItemId != null) {
            a.setMagicItem(magicItemRepository.findById(req.magicItemId)
                    .orElseThrow(() -> new NotFoundException("Magic item not found")));
        }
        if (req.equipmentItemId != null) {
            a.setEquipmentItem(equipmentItemRepository.findById(req.equipmentItemId)
                    .orElseThrow(() -> new NotFoundException("Equipment item not found")));
        }
        a.setCustomText(req.customText);
        a.setQuantity(req.quantity);
        a.setInventoryState(req.inventoryState());
        if (req.attuned) {
            requireAttunableState(req.inventoryState());
        }
        a.setAttuned(req.attuned);
        return AssignmentDto.from(repository.save(a));
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByPartyMemberAsc(campaignId).stream()
                .map(AssignmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> findByPartyMemberId(UUID partyMemberId) {
        return repository.findByPartyMemberId(partyMemberId).stream()
                .map(AssignmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> findPartyStash(UUID campaignId) {
        return repository.findByCampaignIdAndPartyMemberIsNull(campaignId).stream()
                .map(AssignmentDto::from).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentDto findById(UUID id) {
        return repository.findById(id)
                .map(AssignmentDto::from)
                .orElseThrow(() -> new NotFoundException("Item assignment not found: " + id));
    }

    public AssignmentDto update(UUID id, UUID partyMemberId, int quantity, boolean attuned) {
        ItemAssignment a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item assignment not found: " + id));
        if (partyMemberId != null) {
            a.setPartyMember(partyMemberRepository.findById(partyMemberId)
                    .orElseThrow(() -> new NotFoundException("Party member not found")));
        } else {
            a.setPartyMember(null);
        }
        a.setQuantity(Math.max(1, quantity));
        if (attuned) {
            requireAttunableState(a.getInventoryState());
        }
        a.setAttuned(attuned);
        return AssignmentDto.from(repository.save(a));
    }

    public AssignmentDto toggleAttunement(UUID id) {
        ItemAssignment a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item assignment not found: " + id));
        boolean next = !a.isAttuned();
        if (next) {
            requireAttunableState(a.getInventoryState());
        }
        a.setAttuned(next);
        return AssignmentDto.from(repository.save(a));
    }

    public AssignmentDto setInventoryState(UUID id, InventoryState state) {
        ItemAssignment a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item assignment not found: " + id));
        if (a.isAttuned()) {
            requireAttunableState(state);
        }
        a.setInventoryState(state);
        return AssignmentDto.from(repository.save(a));
    }

    private static void requireAttunableState(InventoryState state) {
        if (state != InventoryState.EQUIPPED && state != InventoryState.CARRIED) {
            throw new IllegalArgumentException(
                    "Attuned items must be EQUIPPED or CARRIED (got " + state + ")");
        }
    }

    public AssignmentDto adjustQuantity(UUID id, int delta) {
        ItemAssignment a = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item assignment not found: " + id));
        int newQty = Math.max(0, a.getQuantity() + delta);
        a.setQuantity(newQty);
        if (newQty == 0) {
            a.setInventoryState(InventoryState.CONSUMED);
        }
        return AssignmentDto.from(repository.save(a));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Item assignment not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public int countAttunements(UUID partyMemberId) {
        return repository.countByPartyMemberIdAndAttunedTrue(partyMemberId);
    }

    @Transactional(readOnly = true)
    public EquipmentItem findEquipmentItemForAssignment(UUID assignmentId) {
        return repository.findById(assignmentId)
                .map(ItemAssignment::getEquipmentItem)
                .orElse(null);
    }

    public Optional<MagicItem> resolveMagicItemForCampaign(UUID campaignId, String sourceKey) {
        return magicItemRepository.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> magicItemRepository.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> magicItemRepository.findBySourceAndSourceKey(ContentSource.SRD, sourceKey));
    }

    public Optional<EquipmentItem> resolveEquipmentItemForCampaign(UUID campaignId, String sourceKey) {
        return equipmentItemRepository.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> equipmentItemRepository.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> equipmentItemRepository.findBySourceAndSourceKey(ContentSource.SRD, sourceKey));
    }
}
