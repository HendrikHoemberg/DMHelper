package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/adventures")
public class AdventureController {

    private final AdventureService adventureService;
    private final CampaignRepository campaignRepository;

    public AdventureController(AdventureService adventureService, CampaignRepository campaignRepository) {
        this.adventureService = adventureService;
        this.campaignRepository = campaignRepository;
    }

    @ModelAttribute
    public void addCampaign(@PathVariable UUID campaignId, Model model) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    // ---- Adventures ----

    @GetMapping
    public String list(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("adventures", adventureService.findAdventuresByCampaign(campaignId));
        adventureService.getCurrentScene(campaignId).ifPresent(s -> model.addAttribute("currentScene", s));
        return "adventure/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(id));
        model.addAttribute("chapters", adventureService.findChaptersByAdventure(id));
        adventureService.getCurrentScene(campaignId).ifPresent(s -> model.addAttribute("currentScene", s));
        return "adventure/detail";
    }

    @GetMapping("/new")
    public String newForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("adventure", null);
        return "adventure/_adventure-form :: form";
    }

    @PostMapping
    public String create(@PathVariable UUID campaignId,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String sourceAttribution) {
        Adventure a = adventureService.createAdventure(campaignId, name, description, sourceAttribution);
        return "redirect:/campaigns/" + campaignId + "/adventures/" + a.getId();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID campaignId, @PathVariable UUID id, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(id));
        return "adventure/_adventure-form :: form";
    }

    @PutMapping("/{id}")
    public String update(@PathVariable UUID campaignId, @PathVariable UUID id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String sourceAttribution) {
        adventureService.updateAdventure(id, name, description, sourceAttribution);
        return "redirect:/campaigns/" + campaignId + "/adventures/" + id;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID campaignId, @PathVariable UUID id) {
        adventureService.deleteAdventure(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/adventures")
                .build();
    }

    @PutMapping("/{id}/move")
    public String move(@PathVariable UUID campaignId, @PathVariable UUID id,
                       @RequestParam int direction, Model model) {
        adventureService.moveAdventure(id, direction);
        model.addAttribute("adventures", adventureService.findAdventuresByCampaign(campaignId));
        adventureService.getCurrentScene(campaignId).ifPresent(s -> model.addAttribute("currentScene", s));
        return "adventure/_adventure-list :: adventureList";
    }

    // ---- Chapters ----

    @GetMapping("/{adventureId}/chapters/new")
    public String newChapterForm(@PathVariable UUID campaignId, @PathVariable UUID adventureId, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapter", null);
        return "adventure/_chapter-form :: form";
    }

    @PostMapping("/{adventureId}/chapters")
    public String createChapter(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                                @RequestParam String title,
                                @RequestParam(required = false) String intro, Model model) {
        adventureService.createChapter(adventureId, title, intro);
        return chapterListView(adventureId, model);
    }

    @GetMapping("/{adventureId}/chapters/{chapterId}/edit")
    public String editChapterForm(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                                  @PathVariable UUID chapterId, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapter", adventureService.findChapterById(chapterId));
        return "adventure/_chapter-form :: form";
    }

    @PutMapping("/{adventureId}/chapters/{chapterId}")
    public String updateChapter(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                                @PathVariable UUID chapterId,
                                @RequestParam String title,
                                @RequestParam(required = false) String intro, Model model) {
        adventureService.updateChapter(chapterId, title, intro);
        return chapterListView(adventureId, model);
    }

    @DeleteMapping("/{adventureId}/chapters/{chapterId}")
    public String deleteChapter(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                                @PathVariable UUID chapterId, Model model) {
        adventureService.deleteChapter(chapterId);
        return chapterListView(adventureId, model);
    }

    @PutMapping("/{adventureId}/chapters/{chapterId}/move")
    public String moveChapter(@PathVariable UUID campaignId, @PathVariable UUID adventureId,
                              @PathVariable UUID chapterId,
                              @RequestParam int direction, Model model) {
        adventureService.moveChapter(chapterId, direction);
        return chapterListView(adventureId, model);
    }

    private String chapterListView(UUID adventureId, Model model) {
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapters", adventureService.findChaptersByAdventure(adventureId));
        return "adventure/_chapter-list :: chapterList";
    }
}
