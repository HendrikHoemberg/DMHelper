package dev.hendrikhoemberg.dmhelper.treasury.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.treasury.data.InventoryState;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/treasury")
public class TreasuryController {

    private final TreasuryService treasuryService;
    private final LedgerService ledgerService;
    private final CampaignRepository campaignRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final MagicItemRepository magicItemRepository;
    private final EquipmentItemRepository equipmentItemRepository;

    public TreasuryController(TreasuryService treasuryService, LedgerService ledgerService,
                              CampaignRepository campaignRepository, PartyMemberRepository partyMemberRepository,
                              MagicItemRepository magicItemRepository, EquipmentItemRepository equipmentItemRepository) {
        this.treasuryService = treasuryService;
        this.ledgerService = ledgerService;
        this.campaignRepository = campaignRepository;
        this.partyMemberRepository = partyMemberRepository;
        this.magicItemRepository = magicItemRepository;
        this.equipmentItemRepository = equipmentItemRepository;
    }

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("partyMembers", partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId));
    }

    @GetMapping
    String overview(@PathVariable UUID campaignId, Model model) {
        var assignments = treasuryService.findByCampaignId(campaignId);
        model.addAttribute("assignments", assignments);
        // Group items per holder here: Thymeleaf's SpEL selections can't see the outer
        // loop variable, so the template can't filter assignments by party member itself.
        var byMember = new java.util.LinkedHashMap<UUID, java.util.List<TreasuryService.AssignmentDto>>();
        partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId)
                .forEach(pm -> byMember.put(pm.getId(), new java.util.ArrayList<>()));
        for (var a : assignments) {
            if (a.partyMemberId() != null && byMember.containsKey(a.partyMemberId())) {
                byMember.get(a.partyMemberId()).add(a);
            }
        }
        model.addAttribute("assignmentsByMember", byMember);
        model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
        model.addAttribute("magicItems", magicItemRepository.findAllByOrderByNameAsc());
        model.addAttribute("equipmentItems", equipmentItemRepository.findAllByOrderByNameAsc());
        return "treasury/list";
    }

    @GetMapping("/fragment/{memberId}")
    String partyMemberItemsFragment(@PathVariable UUID campaignId, @PathVariable UUID memberId, Model model) {
        model.addAttribute("assignments", treasuryService.findByPartyMemberId(memberId));
        var pm = partyMemberRepository.findById(memberId).orElseThrow();
        model.addAttribute("holderName", pm.getCharacterName());
        model.addAttribute("attunementCount", treasuryService.countAttunements(memberId));
        model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
        return "treasury/_holder-section :: holderSection";
    }

    @GetMapping("/new")
    String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("magicItems", magicItemRepository.findAllByOrderByNameAsc());
        model.addAttribute("equipmentItems", equipmentItemRepository.findAllByOrderByNameAsc());
        return "treasury/_form :: form";
    }

    @PostMapping
    String create(@PathVariable UUID campaignId,
                  @RequestParam(required = false) UUID partyMemberId,
                  @RequestParam(required = false) UUID magicItemId,
                  @RequestParam(required = false) UUID equipmentItemId,
                  @RequestParam(required = false) String customText,
                  @RequestParam(defaultValue = "1") int quantity,
                  @RequestParam(defaultValue = "false") boolean attuned,
                  Model model) {
        treasuryService.create(new TreasuryService.CreateAssignmentRequest(
                campaignId, partyMemberId, magicItemId, equipmentItemId, customText, quantity, attuned));
        return overview(campaignId, model);
    }

    @PutMapping("/{id}/attune")
    String toggleAttunement(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        var dto = treasuryService.toggleAttunement(id);
        if (dto.partyMemberId() != null) {
            model.addAttribute("assignments", treasuryService.findByPartyMemberId(dto.partyMemberId()));
            model.addAttribute("attunementCount", treasuryService.countAttunements(dto.partyMemberId()));
            var pm = partyMemberRepository.findById(dto.partyMemberId()).orElseThrow();
            model.addAttribute("holderName", pm.getCharacterName());
            model.addAttribute("balances", ledgerService.computeAllGoldBalances(campaignId));
            return "treasury/_holder-section :: holderSection";
        }
        var assignments = treasuryService.findByCampaignId(campaignId).stream()
                .filter(a -> a.partyMemberId() == null).toList();
        model.addAttribute("assignments", assignments);
        model.addAttribute("attunementCount", 0);
        model.addAttribute("holderName", "Party Stash");
        return "treasury/_holder-section :: holderSection";
    }

    @PutMapping("/{id}/quantity")
    String updateQuantity(@PathVariable UUID campaignId, @PathVariable UUID id,
                          @RequestParam int quantity, Model model) {
        var existing = treasuryService.findById(id);
        var dto = treasuryService.update(id, existing.partyMemberId(), quantity, existing.attuned());
        model.addAttribute("assignment", dto);
        return "treasury/_card :: card";
    }

    @PutMapping("/{id}/state")
    String setInventoryState(@PathVariable UUID campaignId, @PathVariable UUID id,
                             @RequestParam InventoryState inventoryState, Model model) {
        var dto = treasuryService.setInventoryState(id, inventoryState);
        model.addAttribute("assignment", dto);
        model.addAttribute("campaignId", campaignId);
        return "sheet/_inventory :: itemCard";
    }

    @PutMapping("/{id}/quantity-adjust")
    String adjustQuantity(@PathVariable UUID campaignId, @PathVariable UUID id,
                          @RequestParam int delta, Model model) {
        var dto = treasuryService.adjustQuantity(id, delta);
        model.addAttribute("assignment", dto);
        model.addAttribute("campaignId", campaignId);
        return "sheet/_inventory :: itemCard";
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        treasuryService.delete(id);
        return ResponseEntity.ok().build();
    }
}
