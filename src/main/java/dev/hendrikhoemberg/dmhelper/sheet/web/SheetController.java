package dev.hendrikhoemberg.dmhelper.sheet.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.*;

@Controller
@RequestMapping("/campaigns/{campaignId}")
public class SheetController {

    private final CampaignService campaignService;
    private final SheetService sheetService;
    private final PartyMemberRepository partyMemberRepo;
    private final SpeciesRepository speciesRepo;
    private final BackgroundRepository backgroundRepo;
    private final CampaignSettingsCodec settingsCodec;

    public SheetController(CampaignService campaignService, SheetService sheetService,
                           PartyMemberRepository partyMemberRepo,
                           SpeciesRepository speciesRepo,
                           BackgroundRepository backgroundRepo,
                           CampaignSettingsCodec settingsCodec) {
        this.campaignService = campaignService;
        this.sheetService = sheetService;
        this.partyMemberRepo = partyMemberRepo;
        this.speciesRepo = speciesRepo;
        this.backgroundRepo = backgroundRepo;
        this.settingsCodec = settingsCodec;
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

        Campaign campaign = campaignService.findById(campaignId);
        model.addAttribute("levelingMode", settingsCodec.read(campaign).levelingMode().name());
        return "sheet/detail";
    }

    @GetMapping("/party/{memberId}/sheet/create")
    public String createForm(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                              Model model) {
        PartyMember member = partyMemberRepo.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Party member not found"));
        model.addAttribute("member", member);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("speciesList", speciesRepo.findAllByOrderByNameAsc());
        model.addAttribute("backgroundList", backgroundRepo.findAllByOrderByNameAsc());
        return "sheet/create";
    }

    @PostMapping("/party/{memberId}/sheet")
    public String create(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                         @RequestParam(defaultValue = "srd-2024_fighter") String classSourceKey,
                         @RequestParam(defaultValue = "1") int classLevel,
                         @RequestParam(defaultValue = "10") int str,
                         @RequestParam(defaultValue = "10") int dex,
                         @RequestParam(defaultValue = "10") int con,
                         @RequestParam(defaultValue = "10") int intScore,
                         @RequestParam(defaultValue = "10") int wis,
                         @RequestParam(defaultValue = "10") int cha,
                         @RequestParam(required = false) UUID speciesId,
                         @RequestParam(required = false) UUID backgroundId,
                         RedirectAttributes redirectAttributes) {
        try {
            Map<String, Integer> scores = new HashMap<>();
            scores.put("str", str);
            scores.put("dex", dex);
            scores.put("con", con);
            scores.put("int", intScore);
            scores.put("wis", wis);
            scores.put("cha", cha);

            ClassLevelEntry entry = new ClassLevelEntry(classSourceKey, classLevel, List.of());
            Map<String, Object> proficiencies = Map.of(
                    "skills", List.of(), "tools", List.of(),
                    "languages", List.of(), "armor", List.of(),
                    "weapons", List.of(), "expertise", List.of()
            );
            CreateSheetRequest req = new CreateSheetRequest(memberId, scores,
                    List.of(entry), proficiencies, speciesId, backgroundId, List.of(), 0);
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
            sheetService.longRest(dto.id(), 0);
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
            UpdateSheetRequest req = new UpdateSheetRequest(scores, null, null, null, null, null, null, -1);
            sheetService.updateSheet(dto.id(), req);
            redirectAttributes.addFlashAttribute("message", "Ability scores updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Update failed: " + e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
    }

    @PostMapping("/party/{memberId}/sheet/proficiencies")
    public String updateProficiencies(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                                      @RequestParam String skills,
                                      @RequestParam(defaultValue = "") String expertise,
                                      @RequestParam(defaultValue = "") String tools,
                                      @RequestParam(defaultValue = "") String languages,
                                      RedirectAttributes redirectAttributes) {
        try {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            Map<String, Object> prof = new HashMap<>();
            prof.put("skills", Arrays.asList(skills.split(",\\s*")));
            prof.put("expertise", expertise.isEmpty() ? List.of() : Arrays.asList(expertise.split(",\\s*")));
            prof.put("tools", tools.isEmpty() ? List.of() : Arrays.asList(tools.split(",\\s*")));
            prof.put("languages", languages.isEmpty() ? List.of() : Arrays.asList(languages.split(",\\s*")));
            prof.put("armor", List.of());
            prof.put("weapons", List.of());
            UpdateSheetRequest req = new UpdateSheetRequest(null, null, prof, null, null, null, null, -1);
            sheetService.updateSheet(dto.id(), req);
            redirectAttributes.addFlashAttribute("message", "Proficiencies updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Update failed: " + e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
    }

    @PostMapping("/party/{memberId}/sheet/overrides")
    public String updateOverrides(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                                  @RequestParam String overrideKey,
                                  @RequestParam String overrideValue,
                                  RedirectAttributes redirectAttributes) {
        try {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            Map<String, Object> overrides = new HashMap<>(dto.overrides());
            try {
                overrides.put(overrideKey, Integer.parseInt(overrideValue));
            } catch (NumberFormatException e) {
                overrides.put(overrideKey, overrideValue);
            }
            UpdateSheetRequest req = new UpdateSheetRequest(null, null, null, null, null, null, overrides, -1);
            sheetService.updateSheet(dto.id(), req);
            redirectAttributes.addFlashAttribute("message", "Override updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Update failed: " + e.getMessage());
        }
        return "redirect:/campaigns/" + campaignId + "/party/" + memberId + "/sheet";
    }
}
