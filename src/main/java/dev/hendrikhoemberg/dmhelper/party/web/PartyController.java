package dev.hendrikhoemberg.dmhelper.party.web;

import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/party")
public class PartyController {

    private final CampaignService campaignService;
    private final PartyMemberService partyService;

    public PartyController(CampaignService campaignService, PartyMemberService partyService) {
        this.campaignService = campaignService;
        this.partyService = partyService;
    }

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignService.findById(campaignId);
        model.addAttribute("campaign", campaign);
        model.addAttribute("members", partyService.findByCampaignId(campaignId));
        model.addAttribute("activeMembers", partyService.findActiveByCampaignId(campaignId));
        return "party/list";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("pm", null);
        return "party/_form :: form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("pm", partyService.findById(id));
        return "party/_form :: form";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam String characterName,
                         @RequestParam(required = false) String playerName,
                         @RequestParam(required = false) String classAndLevel,
                         @RequestParam int ac, @RequestParam int maxHp,
                         @RequestParam int initiativeBonus, @RequestParam int speed,
                         @RequestParam int passivePerception, @RequestParam int passiveInsight,
                         @RequestParam int passiveInvestigation,
                         @RequestParam(required = false) String notes,
                         Model model) {
        PartyMember pm = partyService.create(campaignId, characterName, playerName,
                classAndLevel, ac, maxHp, initiativeBonus, speed,
                passivePerception, passiveInsight, passiveInvestigation, notes);
        model.addAttribute("pm", pm);
        return "party/_card :: card";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID campaignId, @PathVariable UUID id,
                         @RequestParam String characterName,
                         @RequestParam(required = false) String playerName,
                         @RequestParam(required = false) String classAndLevel,
                         @RequestParam int ac, @RequestParam int maxHp,
                         @RequestParam int initiativeBonus, @RequestParam int speed,
                         @RequestParam int passivePerception, @RequestParam int passiveInsight,
                         @RequestParam int passiveInvestigation,
                         @RequestParam(required = false) String notes) {
        partyService.update(id, characterName, playerName, classAndLevel,
                ac, maxHp, initiativeBonus, speed,
                passivePerception, passiveInsight, passiveInvestigation, notes);
        return "redirect:/campaigns/" + campaignId + "/party";
    }

    @PutMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        PartyMember pm = partyService.findById(id);
        partyService.setActive(id, !pm.isActive());
        model.addAttribute("pm", partyService.findById(id));
        return "party/_card :: card";
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        partyService.delete(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/party")
                .build();
    }
}
