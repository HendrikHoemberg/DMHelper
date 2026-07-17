package dev.hendrikhoemberg.dmhelper.sheet.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.*;
import dev.hendrikhoemberg.dmhelper.treasury.data.InventoryState;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
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
    private final CharacterClassRepository classRepo;
    private final CampaignSettingsCodec settingsCodec;
    private final TreasuryService treasuryService;

    public SheetController(CampaignService campaignService, SheetService sheetService,
                           PartyMemberRepository partyMemberRepo,
                           SpeciesRepository speciesRepo,
                           BackgroundRepository backgroundRepo,
                           CharacterClassRepository classRepo,
                           CampaignSettingsCodec settingsCodec,
                           TreasuryService treasuryService) {
        this.campaignService = campaignService;
        this.sheetService = sheetService;
        this.partyMemberRepo = partyMemberRepo;
        this.speciesRepo = speciesRepo;
        this.backgroundRepo = backgroundRepo;
        this.classRepo = classRepo;
        this.settingsCodec = settingsCodec;
        this.treasuryService = treasuryService;
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

        var assignments = treasuryService.findByPartyMemberId(memberId);
        model.addAttribute("assignments", assignments);

        double totalWeight = assignments.stream()
            .filter(a -> a.inventoryState() == InventoryState.CARRIED || a.inventoryState() == InventoryState.EQUIPPED)
            .mapToDouble(a -> {
                try {
                    var eq = treasuryService.findEquipmentItemForAssignment(a.id());
                    if (eq != null && eq.getWeight() != null && !eq.getWeight().isBlank()) {
                        String w = eq.getWeight().replaceAll("[^0-9.]", "");
                        return Double.parseDouble(w) * a.quantity();
                    }
                } catch (Exception ignored) {}
                return 0;
            })
            .sum();
        model.addAttribute("totalWeight", totalWeight > 0 ? totalWeight : null);

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
        model.addAttribute("classList", classRepo.findBySubclassOfIsNullOrderByNameAsc());
        model.addAttribute("allClasses", classRepo.findAllByOrderByNameAsc());
        return "sheet/create";
    }

    @GetMapping("/party/{memberId}/sheet/creation-options")
    @ResponseBody
    public CreationOptionsDto creationOptions(@PathVariable UUID campaignId,
                                              @PathVariable UUID memberId,
                                              @RequestParam String classSourceKey) {
        return sheetService.creationOptions(classSourceKey);
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
                         @RequestParam(required = false) String subclassSourceKey,
                         @RequestParam(defaultValue = "") String skills,
                         @RequestParam(defaultValue = "") String tools,
                         @RequestParam(defaultValue = "") String languages,
                         @RequestParam(defaultValue = "") String expertise,
                         @RequestParam(defaultValue = "average") String hitDieChoice,
                         RedirectAttributes redirectAttributes) {
        try {
            Map<String, Integer> scores = new HashMap<>();
            scores.put("str", str);
            scores.put("dex", dex);
            scores.put("con", con);
            scores.put("int", intScore);
            scores.put("wis", wis);
            scores.put("cha", cha);

            ClassLevelEntry entry = new ClassLevelEntry(classSourceKey, classLevel,
                    classLevel > 1 ? generateHitDieRolls(classSourceKey, classLevel, hitDieChoice) : List.of(),
                    subclassSourceKey);
            Map<String, Object> proficiencies = new HashMap<>();
            proficiencies.put("skills", skills.isEmpty() ? List.of() : Arrays.asList(skills.split(",\\s*")));
            proficiencies.put("expertise", expertise.isEmpty() ? List.of() : Arrays.asList(expertise.split(",\\s*")));
            proficiencies.put("tools", tools.isEmpty() ? List.of() : Arrays.asList(tools.split(",\\s*")));
            proficiencies.put("languages", languages.isEmpty() ? List.of() : Arrays.asList(languages.split(",\\s*")));

            var creationOptions = sheetService.creationOptions(classSourceKey);
            proficiencies.put("armor", creationOptions.armorProficiencies());
            proficiencies.put("weapons", creationOptions.weaponProficiencies());

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

    private List<Integer> generateHitDieRolls(String classSourceKey, int level, String hitDieChoice) {
        List<Integer> rolls = new ArrayList<>();
        if (level <= 1) return rolls;
        int dieSize = 8;
        var clsOpt = classRepo.findBySourceKey(classSourceKey);
        if (clsOpt.isPresent() && clsOpt.get().getHitDie() != null) {
            try { dieSize = Integer.parseInt(clsOpt.get().getHitDie().substring(1)); } catch (Exception ignored) {}
        }
        int avg = (dieSize / 2) + 1;
        for (int i = 1; i < level; i++) {
            rolls.add("average".equals(hitDieChoice) ? avg : avg);
        }
        return rolls;
    }

    @PostMapping("/party/{memberId}/sheet/level-up")
    public String levelUp(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                          @RequestParam String classSourceKey,
                          @RequestParam(required = false) Integer hpRoll,
                          @RequestParam(defaultValue = "false") boolean isAverage,
                          @RequestParam(required = false) String subclassSourceKey,
                          @RequestParam(required = false) String asiType,
                          @RequestParam(required = false) String asiAbility1,
                          @RequestParam(required = false) String asiAbility2,
                          RedirectAttributes redirectAttributes) {
        try {
            SheetDto dto = sheetService.getSheetDtoByPartyMemberId(memberId);
            Map<String, Integer> asiScores = new HashMap<>();
            if ("single".equals(asiType) && asiAbility1 != null && !asiAbility1.isBlank()) {
                asiScores.put(asiAbility1, 2);
            } else if ("double".equals(asiType)) {
                if (asiAbility1 != null && !asiAbility1.isBlank()) asiScores.put(asiAbility1, 1);
                if (asiAbility2 != null && !asiAbility2.isBlank()) asiScores.put(asiAbility2, 1);
            }
            LevelUpRequest req = new LevelUpRequest(classSourceKey,
                    hpRoll != null ? hpRoll : 0, isAverage,
                    subclassSourceKey, asiType, asiScores);
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

    @GetMapping("/party/{memberId}/sheet/rest/preview")
    public String restPreview(@PathVariable UUID campaignId, @PathVariable UUID memberId,
                              @RequestParam String type,
                              @RequestParam(defaultValue = "0") int hitDiceSpent,
                              Model model) {
        UUID sheetId = sheetService.getSheetDtoByPartyMemberId(memberId).id();
        RestPreviewDto preview = sheetService.previewRest(sheetId, type, hitDiceSpent);
        model.addAttribute("preview", preview);
        model.addAttribute("restType", type.toUpperCase());
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("memberId", memberId);
        return "sheet/_rest-preview";
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
