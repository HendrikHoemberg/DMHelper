package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneStatus;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
public class SceneController {

    private final AdventureService adventureService;
    private final CampaignRepository campaignRepository;
    private final GameMapRepository gameMapRepository;
    private final EncounterRepository encounterRepository;
    private final StatBlockRepository statBlockRepository;
    private final HandoutRepository handoutRepository;
    private final MarkdownUtil markdownUtil;

    public SceneController(AdventureService adventureService,
                           CampaignRepository campaignRepository,
                           GameMapRepository gameMapRepository,
                           EncounterRepository encounterRepository,
                           StatBlockRepository statBlockRepository,
                           HandoutRepository handoutRepository,
                           MarkdownUtil markdownUtil) {
        this.adventureService = adventureService;
        this.campaignRepository = campaignRepository;
        this.gameMapRepository = gameMapRepository;
        this.encounterRepository = encounterRepository;
        this.statBlockRepository = statBlockRepository;
        this.handoutRepository = handoutRepository;
        this.markdownUtil = markdownUtil;
    }

    @GetMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}")
    public String sceneDetail(@PathVariable UUID campaignId,
                              @PathVariable UUID adventureId,
                              @PathVariable UUID id,
                              Model model) {
        Scene scene = adventureService.findSceneById(id);
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("scene", scene);
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapters", adventureService.findChaptersByAdventure(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("renderedBody", markdownUtil.toHtml(scene.getBody()));
        model.addAttribute("maps", gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("encounters", encounterRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("statBlocks", statBlockRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("handouts", handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId));
        adventureService.getCurrentScene(campaignId).ifPresent(s -> model.addAttribute("currentScene", s));
        return "adventure/scene-detail";
    }

    @GetMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/edit")
    public String editForm(@PathVariable UUID campaignId,
                           @PathVariable UUID adventureId,
                           @PathVariable UUID id,
                           Model model) {
        Scene scene = adventureService.findSceneById(id);
        model.addAttribute("scene", scene);
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("maps", gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("encounters", encounterRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("statBlocks", statBlockRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("handouts", handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId));
        return "adventure/_scene-form :: form";
    }

    @PutMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}")
    public String updateScene(@PathVariable UUID campaignId,
                              @PathVariable UUID adventureId,
                              @PathVariable UUID id,
                              @RequestParam String title,
                              @RequestParam(required = false) String sceneKey,
                              @RequestParam(required = false) String body,
                              Model model) {
        Scene scene = adventureService.updateScene(id, title, sceneKey, body);
        model.addAttribute("scene", scene);
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("renderedBody", markdownUtil.toHtml(scene.getBody()));
        return "adventure/scene-detail :: sceneBody";
    }

    @DeleteMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}")
    public ResponseEntity<Void> deleteScene(@PathVariable UUID campaignId,
                                            @PathVariable UUID adventureId,
                                            @PathVariable UUID id) {
        adventureService.deleteScene(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/adventures/" + adventureId)
                .build();
    }

    @PutMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/move")
    public ResponseEntity<Void> moveScene(@PathVariable UUID campaignId,
                                          @PathVariable UUID adventureId,
                                          @PathVariable UUID id,
                                          @RequestParam int direction) {
        adventureService.moveScene(id, direction);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/adventures/" + adventureId)
                .build();
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/move-to-chapter")
    public ResponseEntity<Void> moveSceneToChapter(@PathVariable UUID campaignId,
                                                   @PathVariable UUID adventureId,
                                                   @PathVariable UUID id,
                                                   @RequestParam UUID chapterId) {
        adventureService.moveSceneToChapter(id, chapterId);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/adventures/" + adventureId)
                .build();
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/link-map")
    public String linkMap(@PathVariable UUID campaignId,
                          @PathVariable UUID adventureId,
                          @PathVariable UUID id,
                          @RequestParam UUID mapId,
                          @RequestParam(required = false) Integer pinX,
                          @RequestParam(required = false) Integer pinY,
                          Model model) {
        adventureService.linkMap(id, mapId, pinX, pinY);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/unlink-map")
    public String unlinkMap(@PathVariable UUID campaignId,
                            @PathVariable UUID adventureId,
                            @PathVariable UUID id,
                            Model model) {
        adventureService.unlinkMap(id);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/link-encounter")
    public String linkEncounter(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID id,
                                @RequestParam UUID encounterId,
                                Model model) {
        adventureService.linkEncounter(id, encounterId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/unlink-encounter")
    public String unlinkEncounter(@PathVariable UUID campaignId,
                                  @PathVariable UUID adventureId,
                                  @PathVariable UUID id,
                                  Model model) {
        adventureService.unlinkEncounter(id);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/add-statblock")
    public String addStatBlock(@PathVariable UUID campaignId,
                               @PathVariable UUID adventureId,
                               @PathVariable UUID id,
                               @RequestParam UUID statBlockId,
                               Model model) {
        adventureService.addStatBlock(id, statBlockId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/remove-statblock")
    public String removeStatBlock(@PathVariable UUID campaignId,
                                  @PathVariable UUID adventureId,
                                  @PathVariable UUID id,
                                  @RequestParam UUID statBlockId,
                                  Model model) {
        adventureService.removeStatBlock(id, statBlockId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/add-handout")
    public String addHandout(@PathVariable UUID campaignId,
                             @PathVariable UUID adventureId,
                             @PathVariable UUID id,
                             @RequestParam UUID handoutId,
                             Model model) {
        adventureService.addHandout(id, handoutId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/remove-handout")
    public String removeHandout(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID id,
                                @RequestParam UUID handoutId,
                                Model model) {
        adventureService.removeHandout(id, handoutId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PutMapping("/scenes/{id}/status")
    public String updateSceneStatus(@PathVariable UUID id,
                                    @RequestParam SceneStatus status,
                                    Model model) {
        Scene scene = adventureService.setStatus(id, status);
        model.addAttribute("scene", scene);
        return "adventure/_action-rail :: statusBadge";
    }

    @PostMapping("/campaigns/{campaignId}/current-scene")
    public String setCurrentScene(@PathVariable UUID campaignId,
                                  @RequestParam(defaultValue = "") String sceneId,
                                  Model model) {
        if (sceneId.isBlank()) {
            adventureService.clearCurrentScene(campaignId);
        } else {
            Scene s = adventureService.setCurrentScene(campaignId, UUID.fromString(sceneId));
            model.addAttribute("scene", s);
        }
        model.addAttribute("campaignId", campaignId);
        adventureService.getCurrentScene(campaignId).ifPresent(s -> model.addAttribute("currentScene", s));
        return "adventure/_scene-panel :: scenePanel";
    }

    @PutMapping("/campaigns/{campaignId}/current-scene/step")
    public String stepCurrentScene(@PathVariable UUID campaignId,
                                   @RequestParam int direction) {
        return adventureService.stepCurrentScene(campaignId, direction)
                .map(s -> "redirect:/campaigns/" + campaignId + "/adventures/"
                        + s.getChapter().getAdventure().getId() + "/scenes/" + s.getId())
                .orElse("redirect:/campaigns/" + campaignId + "/adventures");
    }

    private String loadActionRail(UUID campaignId, UUID adventureId, UUID sceneId, Model model) {
        Scene scene = adventureService.findSceneById(sceneId);
        model.addAttribute("scene", scene);
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("maps", gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("encounters", encounterRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("statBlocks", statBlockRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("handouts", handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId));
        return "adventure/_action-rail :: actionRail";
    }
}
