package dev.hendrikhoemberg.dmhelper.sheet.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/campaigns/{campaignId}")
public class SheetController {

    private final CampaignService campaignService;
    private final SheetService sheetService;
    private final PartyMemberRepository partyMemberRepo;

    public SheetController(CampaignService campaignService, SheetService sheetService,
                           PartyMemberRepository partyMemberRepo) {
        this.campaignService = campaignService;
        this.sheetService = sheetService;
        this.partyMemberRepo = partyMemberRepo;
    }

    @ModelAttribute("campaign")
    public Campaign addCampaign(@PathVariable UUID campaignId) {
        return campaignService.findById(campaignId);
    }

    @GetMapping("/sheets")
    public String overview(@PathVariable UUID campaignId, Model model) {
        List<PartyMember> members = partyMemberRepo.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId);
        model.addAttribute("members", members);
        model.addAttribute("campaignId", campaignId);
        return "sheet/overview";
    }

    @GetMapping("/party/{memberId}/sheet")
    public String detail(@PathVariable UUID campaignId, @PathVariable UUID memberId, Model model) {
        PartyMember member = partyMemberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Party member not found"));
        boolean hasSheet = sheetService.hasSheet(memberId);
        model.addAttribute("member", member);
        model.addAttribute("campaignId", campaignId);
        if (hasSheet) {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            model.addAttribute("sheet", dto);
        }
        model.addAttribute("hasSheet", hasSheet);
        return "sheet/detail";
    }

    @PostMapping("/party/{memberId}/sheet")
    public String create(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                         @RequestParam Map<String, String> params,
                         RedirectAttributes redirectAttributes) {
        try {
            Map<String, Integer> scores = new HashMap<>();
            scores.put("str", Integer.parseInt(params.getOrDefault("str", "10")));
            scores.put("dex", Integer.parseInt(params.getOrDefault("dex", "10")));
            scores.put("con", Integer.parseInt(params.getOrDefault("con", "10")));
            scores.put("int", Integer.parseInt(params.getOrDefault("int", "10")));
            scores.put("wis", Integer.parseInt(params.getOrDefault("wis", "10")));
            scores.put("cha", Integer.parseInt(params.getOrDefault("cha", "10")));

            ClassLevelEntry entry = new ClassLevelEntry("srd-2024_fighter", 1, List.of());
            CreateSheetRequest req = new CreateSheetRequest(memberId, scores,
                    List.of(entry), null, null, List.of(), 0);
            sheetService.createSheet(req);
            redirectAttributes.addFlashAttribute("message", "Character sheet created!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create sheet: " + e.getMessage());
            return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
        }
        return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
    }

    @DeleteMapping("/party/{memberId}/sheet")
    public String delete(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                         RedirectAttributes redirectAttributes) {
        try {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            sheetService.deleteSheet(dto.id());
            redirectAttributes.addFlashAttribute("message", "Character sheet deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete sheet: " + e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/party";
    }

    @PostMapping("/party/{memberId}/sheet/level-up")
    public String levelUp(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                          @RequestParam String classSourceKey,
                          @RequestParam(required = false) Integer hpRoll,
                          @RequestParam(defaultValue = "false") boolean isAverage,
                          RedirectAttributes redirectAttributes) {
        try {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            LevelUpRequest req = new LevelUpRequest(classSourceKey,
                    hpRoll != null ? hpRoll : 0, isAverage);
            sheetService.levelUp(dto.id(), req);
            redirectAttributes.addFlashAttribute("message", "Level up successful!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Level up failed: " + e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
    }

    @PostMapping("/party/{memberId}/sheet/rest/short")
    public String shortRest(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                            @RequestParam(defaultValue = "0") int hitDiceSpent,
                            RedirectAttributes redirectAttributes) {
        try {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            sheetService.shortRest(dto.id(), hitDiceSpent);
            redirectAttributes.addFlashAttribute("message", "Short rest completed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Short rest failed: " + e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
    }

    @PostMapping("/party/{memberId}/sheet/rest/long")
    public String longRest(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                           RedirectAttributes redirectAttributes) {
        try {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            sheetService.longRest(dto.id());
            redirectAttributes.addFlashAttribute("message", "Long rest completed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Long rest failed: " + e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
    }

    @PostMapping("/party/{memberId}/sheet/abilities")
    public String updateAbilities(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                                  @RequestParam Map<String, String> params,
                                  RedirectAttributes redirectAttributes) {
        try {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            Map<String, Integer> scores = new HashMap<>();
            scores.put("str", Integer.parseInt(params.getOrDefault("str", "10")));
            scores.put("dex", Integer.parseInt(params.getOrDefault("dex", "10")));
            scores.put("con", Integer.parseInt(params.getOrDefault("con", "10")));
            scores.put("int", Integer.parseInt(params.getOrDefault("int", "10")));
            scores.put("wis", Integer.parseInt(params.getOrDefault("wis", "10")));
            scores.put("cha", Integer.parseInt(params.getOrDefault("cha", "10")));
            UpdateSheetRequest req = new UpdateSheetRequest(scores, null, null, null, null, null, -1);
            sheetService.updateSheet(dto.id(), req);
            redirectAttributes.addFlashAttribute("message", "Ability scores updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Update failed: " + e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
    }
}
